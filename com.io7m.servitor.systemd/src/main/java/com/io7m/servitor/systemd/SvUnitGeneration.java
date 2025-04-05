/*
 * Copyright © 2023 Mark Raynsford <code@io7m.com> https://www.io7m.com
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package com.io7m.servitor.systemd;

import com.io7m.jaffirm.core.Postconditions;
import com.io7m.servitor.core.SvAddressResolverType;
import com.io7m.servitor.core.SvConfiguration;
import com.io7m.servitor.core.SvDevicePassthrough;
import com.io7m.servitor.core.SvEntrypoint;
import com.io7m.servitor.core.SvException;
import com.io7m.servitor.core.SvNetworkBackendBridge;
import com.io7m.servitor.core.SvNetworkBackendPasta;
import com.io7m.servitor.core.SvNetworkBackendSlirp4NetNS;
import com.io7m.servitor.core.SvNetworking;
import com.io7m.servitor.core.SvOCIImage;
import com.io7m.servitor.core.SvOutboundAddress;
import com.io7m.servitor.core.SvPublishPort;
import com.io7m.servitor.core.SvRunAs;
import com.io7m.servitor.core.SvService;
import com.io7m.servitor.core.SvServiceElementType;
import com.io7m.servitor.core.SvServiceGroup;
import com.io7m.servitor.core.SvVolumeFlag;
import com.io7m.servitor.core.SvVolumeType;
import org.apache.commons.text.StringEscapeUtils;
import org.jgrapht.traverse.DepthFirstIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.io7m.servitor.core.SvContainerFlag.READ_ONLY_ROOT;
import static com.io7m.servitor.core.SvContainerFlag.REMAP_USER_TO_CONTAINER_ROOT;

/**
 * Functions to generate {@code systemd} units.
 */

public final class SvUnitGeneration
{
  private static final Logger LOG =
    LoggerFactory.getLogger(SvUnitGeneration.class);

  private SvUnitGeneration()
  {

  }

  /**
   * Generate a set of service files from the given configuration.
   *
   * @param resolver      The resolver
   * @param configuration The configuration
   *
   * @return The set of units
   *
   * @throws SvException On errors
   */

  public static List<SvUnit> generate(
    final SvAddressResolverType resolver,
    final SvConfiguration configuration)
    throws SvException
  {
    Objects.requireNonNull(resolver, "resolver");
    Objects.requireNonNull(configuration, "configuration");

    final var results =
      new ArrayList<SvUnit>();
    final var iterator =
      new DepthFirstIterator<>(configuration.graph());

    while (iterator.hasNext()) {
      results.addAll(generateOne(resolver, configuration, iterator.next()));
    }

    return List.copyOf(results);
  }

  private static List<SvUnit> generateOne(
    final SvAddressResolverType resolver,
    final SvConfiguration configuration,
    final SvServiceElementType element)
    throws SvException
  {
    return switch (element) {
      case final SvServiceGroup group -> {
        yield generateOneGroup(resolver, configuration, group);
      }
      case final SvService service -> {
        yield generateOneService(resolver, configuration, service);
      }
    };
  }

  private static Optional<SvServiceGroup> parentOf(
    final SvConfiguration configuration,
    final SvServiceElementType e)
  {
    final var incoming =
      List.copyOf(
        configuration.graph()
          .incomingEdgesOf(e)
      );

    if (incoming.isEmpty()) {
      return Optional.empty();
    }

    return Optional.of(incoming.get(0).group());
  }

  private static List<SvUnit> generateOneService(
    final SvAddressResolverType resolver,
    final SvConfiguration configuration,
    final SvService service)
    throws SvException
  {
    final var serviceName =
      nameFor(configuration, service);

    final var stringWriter = new StringWriter();
    try (var writer = new PrintWriter(stringWriter)) {
      writer.println("#");
      writer.println("#  Automatically generated; do not edit.");
      writer.printf("#  $ServiceID: %s%n", service.id());
      writer.println("#");
      writer.println();

      writeInstall(writer, configuration, service);

      writer.println("[Unit]");
      writer.printf(
        "Description=%s (%s)%n",
        service.description(),
        service.image().tag());
      writer.println("Wants=network-online.target");

      final var parentOpt = parentOf(configuration, service);
      if (parentOpt.isPresent()) {
        final var parent = parentOpt.get();
        final var parentName = nameFor(configuration, parent);
        writer.printf("PartOf=%s.service%n", parentName);
        writer.printf("After=%s.service%n", parentName);
      } else {
        writer.println("After=network-online.target");
      }
      writer.println();

      writer.println("[Service]");
      writer.printf("Slice=%s.slice%n", sliceNameOf(configuration, service));

      if (service.isOneShot()) {
        writer.println("Type=oneshot");
        writer.println();
      } else {
        writer.println("Type=exec");
        writer.println("Restart=on-failure");
        writer.println("RestartSec=10s");
        writer.println("TimeoutStopSec=70");
        writer.println("TimeoutStartSec=300");
        writer.println();
      }

      writeRunAs(writer, service.runAs());
      writeServiceResourceLimits(writer, service);
      writeExecStart(resolver, writer, service, serviceName);
      writeExecStop(writer, serviceName);
      writeExecStopPost(writer, serviceName);
    }

    return List.of(new SvUnit(
      service,
      "%s.service".formatted(serviceName),
      stringWriter.toString()
    ));
  }

  private static void writeServiceResourceLimits(
    final PrintWriter writer,
    final SvService service)
  {
    final var wrote = new AtomicBoolean(false);

    final var limits = service.limits();
    limits.cpuPercent().ifPresent(cpuPercent -> {
      writer.println("CPUAccounting=true");
      writer.printf("CPUQuota=%d%%%n", cpuPercent);
      wrote.set(true);
    });

    if (limits.memoryLimited()) {
      writer.println("MemoryAccounting=true");
      limits.memoryLimitSoft().ifPresent(mem -> {
        writer.printf(
          "MemoryHigh=%s%n",
          Long.toUnsignedString(mem.longValue()));
      });
      limits.memoryLimitHard().ifPresent(mem -> {
        writer.printf("MemoryMax=%s%n", Long.toUnsignedString(mem.longValue()));
      });
      wrote.set(true);
    }

    if (wrote.get()) {
      writer.println();
    }
  }

  private static String sliceNameOf(
    final SvConfiguration configuration,
    final SvServiceElementType service)
  {
    final var graph =
      configuration.graph();
    final var incoming =
      List.copyOf(graph.incomingEdgesOf(service));
    final var rawName =
      service.name().value();

    if (incoming.isEmpty()) {
      return "services-" + rawName;
    }

    final var result =
      "%s-%s".formatted(
        sliceNameOf(configuration, incoming.get(0).group()),
        rawName
      );

    Postconditions.checkPostcondition(
      result,
      result.startsWith("services-"),
      s -> "Result must start with 'services-'"
    );
    return result;
  }

  private static void writeExecStart(
    final SvAddressResolverType resolver,
    final PrintWriter writer,
    final SvService service,
    final String serviceName)
    throws SvException
  {
    writer.println("ExecStart=/usr/bin/podman \\");
    writer.println("  run \\");
    writer.printf("  --name %s \\%n", serviceName);

    if (service.containerFlags().contains(READ_ONLY_ROOT)) {
      writer.println("  --read-only \\");
    }
    if (service.containerFlags().contains(REMAP_USER_TO_CONTAINER_ROOT)) {
      writer.println("  --user 0:0 \\");
    }

    writer.println("  --rm \\");
    writer.println("  --replace \\");

    writeDevicePassthroughs(writer, service.devicePassthroughs());
    writeEntrypoint(writer, service.entrypoint());
    writeEnvironmentVariables(writer, service.environmentVariables());
    writeVolumes(writer, service.volumes());
    writeNetwork(resolver, writer, service, service.networking());
    writePorts(resolver, writer, service, service.ports());
    writeImage(writer, service.image());
    writeArguments(writer, service.containerArguments());
    writer.println();
  }

  private static void writeEntrypoint(
    final PrintWriter writer,
    final Optional<SvEntrypoint> entrypoint)
  {
    if (entrypoint.isPresent()) {
      writer.printf(
        "  --entrypoint='%s' \\%n",
        StringEscapeUtils.escapeJava(entrypoint.get().path())
      );
    }
  }

  private static void writeDevicePassthroughs(
    final PrintWriter writer,
    final List<SvDevicePassthrough> devices)
  {
    for (final var device : devices) {
      writeDevicePassthrough(writer, device);
    }
  }

  private static void writeDevicePassthrough(
    final PrintWriter writer,
    final SvDevicePassthrough device)
  {
    writer.printf("  --device='%s:%s", device.hostPath(), device.mountedAt());
    if (!device.permissions().isEmpty()) {
      final var text =
        device.permissions()
          .stream()
          .map(x -> switch (x) {
            case READ -> "r";
            case WRITE -> "w";
            case MKNOD -> "m";
          })
          .collect(Collectors.joining());
      writer.printf(":%s'", text);
    } else {
      writer.print("'");
    }
    writer.printf(" \\%n");
  }

  private static void writeNetwork(
    final SvAddressResolverType resolver,
    final PrintWriter writer,
    final SvService service,
    final SvNetworking networking)
    throws SvException
  {
    switch (networking.backend()) {
      case final SvNetworkBackendBridge bridge -> {
        writeNetworkBridge(
          resolver,
          writer,
          service,
          bridge,
          networking.outboundAddress()
        );
      }
      case final SvNetworkBackendPasta pasta -> {
        writeNetworkPasta(
          resolver,
          writer,
          service,
          pasta,
          networking.outboundAddress()
        );
      }
      case final SvNetworkBackendSlirp4NetNS slirp4NetNS -> {
        writeNetworkSlirp4NetNS(
          resolver,
          writer,
          service,
          slirp4NetNS,
          networking.outboundAddress()
        );
      }
    }
  }

  private static void writeNetworkSlirp4NetNS(
    final SvAddressResolverType resolver,
    final PrintWriter writer,
    final SvService service,
    final SvNetworkBackendSlirp4NetNS slirp4NetNS,
    final Optional<SvOutboundAddress> outbound)
    throws SvException
  {
    final var parts = new ArrayList<String>();
    if (outbound.isPresent()) {
      final var out =
        outbound.get();

      final var text6 = out.ipv6Address();
      if (text6.isPresent()) {
        final var inet6 = lookupIPv6(resolver, service, text6.get());
        parts.add("outbound_addr6=%s".formatted(inet6.getHostAddress()));
      }

      final var text4 = out.ipv4Address();
      if (text4.isPresent()) {
        final var inet4 = lookupIPv4(resolver, service, text4.get());
        parts.add("outbound_addr=%s".formatted(inet4.getHostAddress()));
      }

      out.mtu().ifPresent(mtuValue -> {
        parts.add("mtu=%d".formatted(mtuValue));
      });
    }

    final var networkString = new StringBuilder(128);
    networkString.append("slirp4netns");
    if (parts.size() > 0) {
      networkString.append(":");
      networkString.append(String.join(",", parts));
    }

    writer.printf("  --network='%s'", networkString);
    writer.printf(" \\%n");
  }

  private static void writeNetworkPasta(
    final SvAddressResolverType resolver,
    final PrintWriter writer,
    final SvService service,
    final SvNetworkBackendPasta pasta,
    final Optional<SvOutboundAddress> outbound)
    throws SvException
  {
    final var parts = new ArrayList<String>();

    if (outbound.isPresent()) {
      final var out =
        outbound.get();

      final var text6 = out.ipv6Address();
      if (text6.isPresent()) {
        final var inet6 = lookupIPv6(resolver, service, text6.get());
        parts.add("--address");
        parts.add(inet6.getHostAddress());
      }

      final var text4 = out.ipv4Address();
      if (text4.isPresent()) {
        final var inet4 = lookupIPv4(resolver, service, text4.get());
        parts.add("--address");
        parts.add(inet4.getHostAddress());
      }

      out.mtu().ifPresent(mtuValue -> {
        parts.add("--mtu");
        parts.add(mtuValue.toString());
      });
    }

    parts.addAll(pasta.arguments());

    final var networkString = new StringBuilder(128);
    networkString.append("pasta");
    if (parts.size() > 0) {
      networkString.append(":");
      networkString.append(String.join(",", parts));
    }

    writer.printf("  --network='%s'", networkString);
    writer.printf(" \\%n");
  }

  private static void writeNetworkBridge(
    final SvAddressResolverType resolver,
    final PrintWriter writer,
    final SvService service,
    final SvNetworkBackendBridge bridge,
    final Optional<SvOutboundAddress> outbound)
    throws SvException
  {
    final var networkString = new StringBuilder(128);
    networkString.append("bridge");

    if (outbound.isPresent()) {
      LOG.warn(
        "Service {} ({}): Specifying outbound address info is not supported for bridged networking.",
        service.name(),
        service.id()
      );
    }

    writer.printf("  --network='%s'", networkString);
    writer.printf(" \\%n");
  }

  private static void writeArguments(
    final PrintWriter writer,
    final List<String> arguments)
  {
    if (arguments.isEmpty()) {
      writer.println();
      return;
    }

    writer.print("  ");
    writer.println(
      arguments.stream()
        .map(StringEscapeUtils::escapeJava)
        .map("'%s'"::formatted)
        .collect(Collectors.joining(" "))
    );
  }

  private static void writeEnvironmentVariables(
    final PrintWriter writer,
    final Map<String, String> variables)
  {
    final var sorted =
      variables.keySet()
        .stream()
        .sorted()
        .toList();

    for (final var name : sorted) {
      writer.printf(
        "  --env '%s=%s' \\%n",
        name,
        variables.get(name)
      );
    }
  }

  private static void writeExecStop(
    final PrintWriter writer,
    final String serviceName)
  {
    writer.println("ExecStop=/usr/bin/podman \\");
    writer.println("  stop \\");
    writer.println("  --ignore \\");
    writer.println("  --time 60 \\");
    writer.println("  " + serviceName);
    writer.println();
  }

  private static void writeExecStopPost(
    final PrintWriter writer,
    final String serviceName)
  {
    writer.println("ExecStopPost=/usr/bin/podman \\");
    writer.println("  rm \\");
    writer.println("  --ignore \\");
    writer.println("  --force \\");
    writer.println("  --time 60 \\");
    writer.println("  " + serviceName);
    writer.println();
  }

  private static void writeImage(
    final PrintWriter writer,
    final SvOCIImage image)
  {
    writer.printf(
      "  %s/%s:%s@%s \\%n",
      image.registry(),
      image.name(),
      image.tag(),
      image.hash()
    );
  }

  private static void writePorts(
    final SvAddressResolverType resolver,
    final PrintWriter writer,
    final SvService service,
    final List<SvPublishPort> ports)
    throws SvException
  {
    for (final var port : ports) {
      writer.printf(
        "  --publish '%s:%s:%s/%s' \\%n",
        formatAddress(resolver, service, port),
        Integer.valueOf(port.portExternal()),
        Integer.valueOf(port.portInternal()),
        port.type().name().toLowerCase(Locale.ROOT)
      );
    }
  }

  private static Inet4Address lookupIPv4(
    final SvAddressResolverType resolver,
    final SvService service,
    final String name)
    throws SvException
  {
    try {
      return resolver.resolveIPV4(name);
    } catch (final SvException e) {
      throw augmentWithServiceInfo(service, e);
    }
  }

  private static InetAddress[] lookupAll(
    final SvAddressResolverType resolver,
    final SvService service,
    final String name)
  {
    return resolver.resolveAll(name);
  }

  private static Inet6Address lookupIPv6(
    final SvAddressResolverType resolver,
    final SvService service,
    final String name)
    throws SvException
  {
    try {
      return resolver.resolveIPV6(name);
    } catch (final SvException e) {
      throw augmentWithServiceInfo(service, e);
    }
  }

  private static SvException augmentWithServiceInfo(
    final SvService service,
    final SvException e)
  {
    final var a = new HashMap<>(e.attributes());
    a.put("Service", service.name().value());
    a.put("Service ID", service.id().toString());
    return new SvException(
      e.getMessage(),
      e,
      e.errorCode(),
      a,
      e.remediatingAction()
    );
  }

  private static String formatAddress(
    final SvAddressResolverType resolver,
    final SvService service,
    final SvPublishPort port)
    throws SvException
  {
    final var addresses =
      lookupAll(resolver, service, port.host());

    return switch (port.family()) {
      case IPV4 -> {
        yield Arrays.stream(addresses)
          .filter(a -> a instanceof Inet4Address)
          .map(Inet4Address.class::cast)
          .findFirst()
          .orElseThrow(() -> {
            return new SvException(
              "No IPv4 address could be resolved for the host.",
              "error-dns",
              Map.ofEntries(
                Map.entry("Host", port.host()),
                Map.entry("Service", service.name().value()),
                Map.entry("Service ID", service.id().toString())
              ),
              Optional.empty()
            );
          }).getHostAddress();
      }
      case IPV6 -> {
        final var v6 =
          Arrays.stream(addresses)
            .filter(a -> a instanceof Inet6Address)
            .map(Inet6Address.class::cast)
            .findFirst()
            .orElseThrow(() -> {
              return new SvException(
                "No IPv6 address could be resolved for the host.",
                "error-dns",
                Map.ofEntries(
                  Map.entry("Host", port.host()),
                  Map.entry("Service", service.name().value()),
                  Map.entry("Service ID", service.id().toString())
                ),
                Optional.empty()
              );
            }).getHostAddress();
        yield "[%s]".formatted(v6);
      }
    };
  }

  private static void writeRunAs(
    final PrintWriter writer,
    final SvRunAs runAs)
  {
    writer.printf("User=%s%n", runAs.user());
    writer.printf("Group=%s%n", runAs.group());
  }

  private static void writeVolumes(
    final PrintWriter writer,
    final List<SvVolumeType> volumes)
  {
    for (final var volume : volumes) {
      final var flagsWith = new HashSet<>(volume.flags());
      if (flagsWith.isEmpty()) {
        flagsWith.add(SvVolumeFlag.READ_ONLY);
      }

      final var flagText =
        String.join(
          ",",
          flagsWith
            .stream()
            .map(SvVolumeFlag::flag)
            .toList()
        );

      writer.printf(
        "  --volume '%s:%s:%s' \\%n",
        volume.hostPath().toAbsolutePath(),
        volume.mountedAt().toAbsolutePath(),
        flagText
      );
    }
  }

  private static String nameFor(
    final SvConfiguration configuration,
    final SvServiceElementType service)
  {
    final var graph =
      configuration.graph();
    final var incoming =
      List.copyOf(graph.incomingEdgesOf(service));
    final var rawName =
      service.name().value();

    if (incoming.isEmpty()) {
      return rawName;
    }

    return "%s.%s".formatted(
      nameFor(configuration, incoming.get(0).group()),
      rawName
    );
  }

  private static List<SvUnit> generateOneGroup(
    final SvAddressResolverType resolver,
    final SvConfiguration configuration,
    final SvServiceGroup group)
  {
    final var serviceName =
      nameFor(configuration, group);

    final var results = new LinkedList<SvUnit>();
    results.add(generateOneGroupServiceUnit(configuration, group, serviceName));
    results.add(generateOneGroupServiceSlice(group, serviceName));
    return results;
  }

  private static SvUnit generateOneGroupServiceSlice(
    final SvServiceGroup group,
    final String serviceName)
  {
    final var stringWriter = new StringWriter();
    try (var writer = new PrintWriter(stringWriter)) {
      writer.println("#");
      writer.println("#  Automatically generated; do not edit.");
      writer.printf("#  $ServiceID: %s%n", group.id());
      writer.println("#");
      writer.println();

      writer.println("[Unit]");
      writer.printf("Description=%s (Slice)%n", group.description());
      writer.println();
    }

    return new SvUnit(
      group,
      "%s.slice".formatted(serviceName),
      stringWriter.toString()
    );
  }

  private static SvUnit generateOneGroupServiceUnit(
    final SvConfiguration configuration,
    final SvServiceGroup group,
    final String serviceName)
  {
    final var stringWriter = new StringWriter();
    try (var writer = new PrintWriter(stringWriter)) {
      writer.println("#");
      writer.println("#  Automatically generated; do not edit.");
      writer.printf("#  $ServiceID: %s%n", group.id());
      writer.println("#");
      writer.println();

      writeInstall(writer, configuration, group);

      final var edges =
        configuration.graph().outgoingEdgesOf(group);
      final var wants =
        new ArrayList<String>();

      for (final var edge : edges) {
        final var targetServiceName =
          nameFor(configuration, edge.target());
        wants.add("%s.service".formatted(targetServiceName));
      }

      writer.println("[Unit]");
      writer.printf("Description=%s (Control service)%n", group.description());
      writer.printf("Wants=%s%n", String.join(" ", wants));
      writer.println();
    }

    return new SvUnit(
      group,
      "%s.target".formatted(serviceName),
      stringWriter.toString()
    );
  }

  private static void writeInstall(
    final PrintWriter writer,
    final SvConfiguration configuration,
    final SvServiceElementType service)
  {
    writer.println("[Install]");
    final var parentOpt = parentOf(configuration, service);
    if (parentOpt.isPresent()) {
      final var parent = parentOpt.get();
      writer.printf("WantedBy=%s.service%n", nameFor(configuration, parent));
    } else {
      writer.println("WantedBy=multi-user.target");
    }
    writer.println();
  }
}
