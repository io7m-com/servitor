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

import java.util.Objects;

/**
 * A published port.
 *
 * @param host         The host/address on which the port is exposed
 * @param portExternal The port number as exposed on the host
 * @param portInternal The internal container port number
 * @param family       The port family
 * @param type         The port type
 */

public record SvPublishPort(
  String host,
  int portExternal,
  int portInternal,
  SvPortFamily family,
  SvPortType type)
{
  /**
   * A published port.
   *
   * @param host         The host/address on which the port is exposed
   * @param portExternal The port number as exposed on the host
   * @param portInternal The internal container port number
   * @param family       The port family
   * @param type         The port type
   */

  public SvPublishPort
  {
    Objects.requireNonNull(host, "host");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(family, "family");

    if (!(portExternal >= 1 && portExternal <= 65536)) {
      throw new IllegalArgumentException(
        "Port %d must be in the range [1, 65536]"
          .formatted(Integer.valueOf(portExternal))
      );
    }

    if (!(portInternal >= 1 && portInternal <= 65536)) {
      throw new IllegalArgumentException(
        "Port %d must be in the range [1, 65536]"
          .formatted(Integer.valueOf(portInternal))
      );
    }
  }
}
