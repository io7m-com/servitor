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


package com.io7m.servitor.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A service.
 *
 * @param name                 The service name
 * @param description          The description
 * @param id                   The unique ID
 * @param image                The image
 * @param limits               The limits
 * @param runAs                The user/group to run as
 * @param ports                The published ports
 * @param volumes              The volumes
 * @param containerFlags       The container flags
 * @param environmentVariables The environment variables
 * @param containerArguments   The command-line arguments passed to the container entrypoint
 * @param outboundAddress      The outbound address
 * @param devicePassthroughs   The device passthroughs
 */

public record SvService(
  SvServiceName name,
  String description,
  UUID id,
  SvOCIImage image,
  SvLimits limits,
  SvRunAs runAs,
  List<SvPublishPort> ports,
  List<SvVolumeType> volumes,
  Set<SvContainerFlag> containerFlags,
  Map<String, String> environmentVariables,
  List<String> containerArguments,
  SvOutboundAddress outboundAddress,
  List<SvDevicePassthrough> devicePassthroughs)
  implements SvServiceElementType
{
  /**
   * A service.
   *
   * @param name                 The service name
   * @param description          The description
   * @param id                   The unique ID
   * @param image                The image
   * @param limits               The limits
   * @param runAs                The user/group to run as
   * @param ports                The published ports
   * @param volumes              The volumes
   * @param containerFlags       The container flags
   * @param environmentVariables The environment variables
   * @param containerArguments   The command-line arguments passed to the container entrypoint
   * @param outboundAddress      The outbound address
   * @param devicePassthroughs   The device passthroughs
   */

  public SvService
  {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(image, "image");
    Objects.requireNonNull(outboundAddress, "outboundAddress");

    ports = List.copyOf(ports);
    volumes = List.copyOf(volumes);
    containerFlags = Set.copyOf(containerFlags);
    environmentVariables = Map.copyOf(environmentVariables);
    containerArguments = List.copyOf(containerArguments);
    devicePassthroughs = List.copyOf(devicePassthroughs);
  }
}
