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
import com.io7m.servitor.core.SvConfiguration;
import com.io7m.servitor.core.SvDevicePassthrough;
import com.io7m.servitor.core.SvException;
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
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
  private SvUnitGeneration()
  {

  }

  /**
   * Generate a set of service files from the given configuration.
   *
   * @param configuration The configuration
   *
   * @return The set of units
   *
   * @throws SvException On errors
   */

  public static List<SvUnit> generate(
    final SvConfiguration configuration)
    throws SvException
  {
    Objects.requireNonNull(configuration, "configuration");

    final var results =
      new ArrayList<SvUnit>();
    final var iterator =
      new DepthFirstIterator<>(configuration.graph());

    while (iterator.hasNext()) {
      results.addAll(generateOne(configuration, iterator.next()));
    }

    return List.copyOf(results);
  }

  private static List<SvUnit> generateOne(
    final SvConfiguration configuration,
    final SvServiceElementType element)
    throws SvException
  {
    return switch (element) {
      case final SvServiceGroup group -> {
        yield generateOneGroup(configuration, group);
      }
      case final SvService service -> {
        yield generateOneService(configuration, service);
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
      writeExecStart(writer, service, serviceName);
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
        writer.printf("MemoryHigh=%s%n", Long.toUnsignedString(mem.longValue()));
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
    writeEnvironmentVariables(writer, service.environmentVariables());
    writeVolumes(writer, service.volumes());
    writeOutboundAddress(writer, service.outboundAddress());
    writePorts(writer, service.ports());
    writeImage(writer, service.image());
    writeArguments(writer, service.containerArguments());
    writer.println();
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

  private static void writeOutboundAddress(
    final PrintWriter writer,
    final SvOutboundAddress outbound)
    throws SvException
  {
    final var inet6 =
      lookupIPv6(outbound.ipv6Address());

    Optional<Inet4Address> inet4 = Optional.empty();
    final var text = outbound.ipv4Address();
    if (text.isPresent()) {
      inet4 = Optional.of(lookupIPv4(text.get()));
    }

    writer.printf(
      "  --network='slirp4netns:outbound_addr6=%s",
      inet6.getHostAddress()
    );
    inet4.ifPresent(i4 -> {
      writer.printf(
        ",outbound_addr=%s",
        i4.getHostAddress()
      );
    });
    outbound.mtu().ifPresent(mtuValue -> {
      writer.printf(
        ",mtu=%s",
        mtuValue
      );
    });
    writer.printf("' \\%n");
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
    final PrintWriter writer,
    final List<SvPublishPort> ports)
    throws SvException
  {
    for (final var port : ports) {
      writer.printf(
        "  --publish '%s:%s:%s/%s' \\%n",
        formatAddress(port),
        Integer.valueOf(port.portExternal()),
        Integer.valueOf(port.portInternal()),
        port.type().name().toLowerCase(Locale.ROOT)
      );
    }
  }

  private static Inet4Address lookupIPv4(
    final String name)
    throws SvException
  {
    return Arrays.stream(lookupAll(name))
      .filter(a -> a instanceof Inet4Address)
      .map(Inet4Address.class::cast)
      .findFirst()
      .orElseThrow(() -> {
        return new SvException(
          "No IPv4 address could be resolved for the host.",
          "error-dns",
          Map.ofEntries(
            Map.entry("Host", name)
          ),
          Optional.empty()
        );
      });
  }

  private static InetAddress[] lookupAll(
    final String name)
    throws SvException
  {
    final InetAddress[] addresses;
    try {
      addresses = InetAddress.getAllByName(name);
    } catch (final UnknownHostException e) {
      throw new SvException(
        "Unknown host.",
        e,
        "error-dns",
        Map.ofEntries(
          Map.entry("Host", name)
        ),
        Optional.empty()
      );
    }
    return addresses;
  }

  private static Inet6Address lookupIPv6(
    final String name)
    throws SvException
  {
    return Arrays.stream(lookupAll(name))
      .filter(a -> a instanceof Inet6Address)
      .map(Inet6Address.class::cast)
      .findFirst()
      .orElseThrow(() -> {
        return new SvException(
          "No IPv6 address could be resolved for the host.",
          "error-dns",
          Map.ofEntries(
            Map.entry("Host", name)
          ),
          Optional.empty()
        );
      });
  }

  private static String formatAddress(
    final SvPublishPort port)
    throws SvException
  {
    final var addresses = lookupAll(port.host());

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
                Map.entry("Host", port.host())
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
                  Map.entry("Host", port.host())
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

      writer.println("[Unit]");
      writer.printf("Description=%s (Control service)%n", group.description());
      writer.println();

      writer.println("[Service]");
      writer.printf("Slice=%s.slice%n", sliceNameOf(configuration, group));
      writer.println("Type=oneshot");
      writer.println("ExecStart=/bin/true");
      writer.println("RemainAfterExit=yes");
      writer.println();
    }

    return new SvUnit(
      group,
      "%s.service".formatted(serviceName),
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
