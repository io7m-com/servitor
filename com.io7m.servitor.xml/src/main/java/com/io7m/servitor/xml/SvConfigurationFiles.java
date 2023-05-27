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


package com.io7m.servitor.xml;

import com.io7m.servitor.core.SvConfiguration;
import com.io7m.servitor.core.SvContainerFlag;
import com.io7m.servitor.core.SvException;
import com.io7m.servitor.core.SvGroupMembership;
import com.io7m.servitor.core.SvLimits;
import com.io7m.servitor.core.SvOCIImage;
import com.io7m.servitor.core.SvPortFamily;
import com.io7m.servitor.core.SvPortType;
import com.io7m.servitor.core.SvPublishPort;
import com.io7m.servitor.core.SvRunAs;
import com.io7m.servitor.core.SvService;
import com.io7m.servitor.core.SvServiceElementType;
import com.io7m.servitor.core.SvServiceGroup;
import com.io7m.servitor.core.SvServiceName;
import com.io7m.servitor.core.SvVolumeFile;
import com.io7m.servitor.core.SvVolumeFlag;
import com.io7m.servitor.core.SvVolumeType;
import com.io7m.servitor.core.SvVolumeZFS;
import com.io7m.servitor.xml.jaxb_v1.Configuration;
import com.io7m.servitor.xml.jaxb_v1.ContainerArguments;
import com.io7m.servitor.xml.jaxb_v1.ContainerFlags;
import com.io7m.servitor.xml.jaxb_v1.EnvironmentVariables;
import com.io7m.servitor.xml.jaxb_v1.Image;
import com.io7m.servitor.xml.jaxb_v1.Limits;
import com.io7m.servitor.xml.jaxb_v1.PublishPort;
import com.io7m.servitor.xml.jaxb_v1.PublishPorts;
import com.io7m.servitor.xml.jaxb_v1.RunAs;
import com.io7m.servitor.xml.jaxb_v1.ServiceGroupType;
import com.io7m.servitor.xml.jaxb_v1.ServiceType;
import com.io7m.servitor.xml.jaxb_v1.VolumeFileType;
import com.io7m.servitor.xml.jaxb_v1.VolumeFlag;
import com.io7m.servitor.xml.jaxb_v1.VolumeType;
import com.io7m.servitor.xml.jaxb_v1.VolumeZFSType;
import com.io7m.servitor.xml.jaxb_v1.Volumes;
import jakarta.xml.bind.JAXBContext;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Functions to parse configurations.
 */

public final class SvConfigurationFiles
{
  private SvConfigurationFiles()
  {

  }

  /**
   * Parse a configuration from the given file.
   *
   * @param file The file
   *
   * @return A configuration
   *
   * @throws SvException On errors
   */

  public static SvConfiguration parse(
    final Path file)
    throws SvException
  {
    Objects.requireNonNull(file, "file");

    try (var stream = Files.newInputStream(file)) {
      return parse(file.toUri(), stream);
    } catch (final IOException e) {
      throw new SvException(
        "I/O error.",
        e,
        "error-io",
        Map.of("File", file.toString()),
        Optional.empty()
      );
    }
  }

  /**
   * Parse a configuration from the given stream.
   *
   * @param stream The stream
   * @param source The URI of the stream
   *
   * @return A configuration
   *
   * @throws SvException On errors
   */

  public static SvConfiguration parse(
    final URI source,
    final InputStream stream)
    throws SvException
  {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(stream, "stream");

    try {
      final var schemas =
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

      final var schema =
        schemas.newSchema(
          SvConfigurationFiles.class.getResource(
            "/com/io7m/servitor/xml/service-1.xsd")
        );

      final var context =
        JAXBContext.newInstance(
          "com.io7m.servitor.xml.jaxb_v1"
        );

      final var unmarshaller = context.createUnmarshaller();
      unmarshaller.setSchema(schema);

      final var spf = SAXParserFactory.newInstance();
      spf.setXIncludeAware(true);
      spf.setNamespaceAware(true);
      spf.setSchema(schema);

      final var reader =
        spf.newSAXParser()
          .getXMLReader();

      final var saxSource = new SAXSource(reader, new InputSource(stream));
      saxSource.setSystemId(source.toString());

      return processConfiguration(
        (Configuration) unmarshaller.unmarshal(saxSource)
      );
    } catch (final Exception e) {
      throw new SvException(
        "XML error.",
        e,
        "error-xml",
        Map.of("Source", source.toString()),
        Optional.empty()
      );
    }
  }

  private static SvConfiguration processConfiguration(
    final Configuration configuration)
  {
    final var services =
      configuration.getServiceOrServiceGroup();
    final var results =
      new HashMap<UUID, SvServiceElementType>();

    final var graph =
      new DirectedAcyclicGraph<SvServiceElementType, SvGroupMembership>(
        SvGroupMembership.class);

    final var groupStack =
      new LinkedList<SvServiceGroup>();

    for (final var object : services) {
      if (object instanceof final ServiceType service) {
        final var result =
          processService(graph, groupStack, results, service);
        results.put(result.id(), result);
        continue;
      }
      if (object instanceof final ServiceGroupType service) {
        final var result =
          processServiceGroup(graph, groupStack, results, service);
        graph.addVertex(result);
        results.put(result.id(), result);
        continue;
      }
    }

    return new SvConfiguration(Map.copyOf(results), graph);
  }

  private static SvServiceGroup processServiceGroup(
    final DirectedAcyclicGraph<SvServiceElementType, SvGroupMembership> graph,
    final LinkedList<SvServiceGroup> groupStack,
    final HashMap<UUID, SvServiceElementType> results,
    final ServiceGroupType service)
  {
    final var group =
      new SvServiceGroup(
        new SvServiceName(service.getName()),
        service.getDescription(),
        UUID.fromString(service.getID())
      );

    graph.addVertex(group);
    groupStack.push(group);
    results.put(group.id(), group);

    try {
      final var services = service.getServiceOrServiceGroup();
      for (final var object : services) {
        final SvServiceElementType result;
        if (object instanceof final ServiceType subService) {
          result = processService(graph, groupStack, results, subService);
        } else if (object instanceof final ServiceGroupType subService) {
          result = processServiceGroup(graph, groupStack, results, subService);
        } else {
          throw new IllegalStateException();
        }
        graph.addEdge(group, result, new SvGroupMembership(group, result));
      }
    } finally {
      groupStack.pop();
    }
    return group;
  }

  private static SvService processService(
    final DirectedAcyclicGraph<SvServiceElementType, SvGroupMembership> graph,
    final LinkedList<SvServiceGroup> groupStack,
    final HashMap<UUID, SvServiceElementType> results,
    final ServiceType service)
  {
    final var result = new SvService(
      new SvServiceName(service.getName()),
      service.getDescription(),
      UUID.fromString(service.getID()),
      processImage(service.getImage()),
      processLimits(service.getLimits()),
      processRunAs(service.getRunAs()),
      processPorts(service.getPublishPorts()),
      processVolumes(service.getVolumes()),
      processContainerFlags(service.getContainerFlags()),
      processEnvironmentVariables(service.getEnvironmentVariables()),
      processContainerArguments(service.getContainerArguments())
    );

    graph.addVertex(result);
    results.put(result.id(), result);

    if (!groupStack.isEmpty()) {
      final var group = groupStack.peek();
      graph.addEdge(group, result, new SvGroupMembership(group, result));
    }
    return result;
  }

  private static List<String> processContainerArguments(
    final ContainerArguments containerArguments)
  {
    if (containerArguments == null) {
      return List.of();
    }

    final var results = new ArrayList<String>();
    for (final var argument : containerArguments.getContainerArgument()) {
      results.add(argument.getValue());
    }
    return List.copyOf(results);
  }

  private static Map<String, String> processEnvironmentVariables(
    final EnvironmentVariables environmentVariables)
  {
    if (environmentVariables == null) {
      return Map.of();
    }

    final var results = new HashMap<String, String>();
    for (final var env : environmentVariables.getEnvironmentVariable()) {
      results.put(env.getName(), env.getValue());
    }
    return Map.copyOf(results);
  }

  private static Set<SvContainerFlag> processContainerFlags(
    final ContainerFlags containerFlags)
  {
    if (containerFlags == null) {
      return Set.of();
    }

    final var results = new HashSet<SvContainerFlag>();
    for (final var flag : containerFlags.getContainerFlag()) {
      results.add(SvContainerFlag.valueOf(flag.getValue().value()));
    }
    return Set.copyOf(results);
  }

  private static List<SvVolumeType> processVolumes(
    final Volumes volumes)
  {
    if (volumes == null) {
      return List.of();
    }

    final var results = new ArrayList<SvVolumeType>();
    for (final var volume : volumes.getVolumeFileOrVolumeZFS()) {
      results.add(processVolume(volume));
    }
    return List.copyOf(results);
  }

  private static SvVolumeType processVolume(
    final VolumeType volume)
  {
    if (volume instanceof final VolumeZFSType zfs) {
      return processVolumeZFS(zfs);
    }
    if (volume instanceof final VolumeFileType file) {
      return processVolumeFile(file);
    }
    throw new IllegalStateException(
      "Unrecognized volume type: %s".formatted(volume.getClass())
    );
  }

  private static SvVolumeFile processVolumeFile(
    final VolumeFileType file)
  {
    return new SvVolumeFile(
      Paths.get(file.getHostPath()),
      Paths.get(file.getMountPath()),
      processVolumeFlags(file.getVolumeFlag())
    );
  }

  private static Set<SvVolumeFlag> processVolumeFlags(
    final List<VolumeFlag> flags)
  {
    final var results = new HashSet<SvVolumeFlag>();
    for (final var flag : flags) {
      results.add(SvVolumeFlag.valueOf(flag.getFlag().value()));
    }
    return Set.copyOf(results);
  }

  private static SvVolumeZFS processVolumeZFS(
    final VolumeZFSType zfs)
  {
    return new SvVolumeZFS(
      Paths.get(zfs.getHostPath()),
      Paths.get(zfs.getMountPath()),
      processVolumeFlags(zfs.getVolumeFlag())
    );
  }

  private static List<SvPublishPort> processPorts(
    final PublishPorts publishPorts)
  {
    if (publishPorts == null) {
      return List.of();
    }

    final var results = new ArrayList<SvPublishPort>();
    for (final var port : publishPorts.getPublishPort()) {
      results.add(processPort(port));
    }
    return List.copyOf(results);
  }

  private static SvPublishPort processPort(
    final PublishPort port)
  {
    return new SvPublishPort(
      port.getHost(),
      port.getPortExternal(),
      port.getPortInternal(),
      SvPortFamily.valueOf(port.getFamily().value().toUpperCase(Locale.ROOT)),
      SvPortType.valueOf(port.getType().value().toUpperCase(Locale.ROOT))
    );
  }

  private static SvRunAs processRunAs(
    final RunAs runAs)
  {
    return new SvRunAs(
      runAs.getUser(),
      runAs.getGroup()
    );
  }

  private static SvLimits processLimits(
    final Limits limits)
  {
    return new SvLimits(
      limits.getCPU(),
      limits.getMemory().longValueExact()
    );
  }

  private static SvOCIImage processImage(
    final Image image)
  {
    return new SvOCIImage(
      image.getRegistry(),
      image.getName(),
      image.getTag(),
      image.getHash()
    );
  }
}
