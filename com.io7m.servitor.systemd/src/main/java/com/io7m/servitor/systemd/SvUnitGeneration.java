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

import com.io7m.servitor.core.SvConfiguration;
import com.io7m.servitor.core.SvException;
import com.io7m.servitor.core.SvLimits;
import com.io7m.servitor.core.SvOCIImage;
import com.io7m.servitor.core.SvPublishPort;
import com.io7m.servitor.core.SvRunAs;
import com.io7m.servitor.core.SvService;
import com.io7m.servitor.core.SvServiceElementType;
import com.io7m.servitor.core.SvServiceGroup;
import com.io7m.servitor.core.SvVolumeFlag;
import com.io7m.servitor.core.SvVolumeType;
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
      results.add(generateOne(configuration, iterator.next()));
    }

    return List.copyOf(results);
  }

  private static SvUnit generateOne(
    final SvConfiguration configuration,
    final SvServiceElementType element)
    throws SvException
  {
    if (element instanceof final SvServiceGroup group) {
      return generateOneGroup(configuration, group);
    }
    if (element instanceof final SvService service) {
      return generateOneService(configuration, service);
    }

    throw new IllegalStateException(
      "Unrecognized element type: %s".formatted(element.getClass())
    );
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

  private static SvUnit generateOneService(
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

      final var parentOpt = parentOf(configuration, service);
      if (parentOpt.isPresent()) {
        final var parent = parentOpt.get();
        final String parentName = nameFor(configuration, parent);
        writer.printf("PartOf=%s.service%n", parentName);
        writer.printf("After=%s.service%n", parentName);
      }

      writer.println();

      writer.println("[Service]");
      writer.println("Type=exec");
      writeRunAs(writer, service.runAs());
      writer.println("Restart=on-failure");
      writer.println("RestartSec=10s");
      writer.println("TimeoutStopSec=70");
      writer.println("TimeoutStartSec=300");
      writer.println();

      writeExecStart(writer, service, serviceName);
      writeExecStop(writer, serviceName);
      writeExecStopPost(writer, serviceName);
    }

    return new SvUnit(
      service,
      "%s.service".formatted(serviceName),
      stringWriter.toString()
    );
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

    writeEnvironmentVariables(writer, service.environmentVariables());
    writeLimits(writer, service.limits());
    writeVolumes(writer, service.volumes());
    writePorts(writer, service.ports());
    writeImage(writer, service.image());
    writer.println();
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
      "  %s/%s:%s@%s",
      image.registry(),
      image.name(),
      image.tag(),
      image.hash()
    );
    writer.println();
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

  private static String formatAddress(
    final SvPublishPort port)
    throws SvException
  {
    final InetAddress[] addresses;
    try {
      addresses = InetAddress.getAllByName(port.host());
    } catch (final UnknownHostException e) {
      throw new SvException(
        "Unknown host.",
        e,
        "error-dns",
        Map.ofEntries(
          Map.entry("Host", port.host())
        ),
        Optional.empty()
      );
    }

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

  private static void writeLimits(
    final PrintWriter writer,
    final SvLimits limits)
  {
    writer.printf("  --cpus %s \\%n", Double.valueOf(limits.cpus()));
    writer.printf("  --memory %s \\%n", Long.toUnsignedString(limits.memory()));
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
        "  --volume %s:%s:%s \\%n",
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
    final var segments =
      new LinkedList<String>();
    final var graph =
      configuration.graph();

    var serviceNow = service;
    while (true) {
      segments.addFirst(serviceNow.name().value());
      final var incoming = List.copyOf(graph.incomingEdgesOf(serviceNow));
      if (incoming.isEmpty()) {
        break;
      }
      serviceNow = incoming.get(0).group();
    }

    return String.join(".", segments);
  }

  private static SvUnit generateOneGroup(
    final SvConfiguration configuration,
    final SvServiceGroup group)
  {
    final var serviceName =
      nameFor(configuration, group);

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
