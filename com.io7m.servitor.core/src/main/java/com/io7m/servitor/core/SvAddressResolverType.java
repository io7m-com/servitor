/*
 * Copyright © 2024 Mark Raynsford <code@io7m.com> https://www.io7m.com
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

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * A name resolver.
 */

public interface SvAddressResolverType
{
  /**
   * Resolve the given hostname to an IPv4 address.
   *
   * @param hostName The name
   *
   * @return The address
   *
   * @throws SvException On errors
   */

  Inet4Address resolveIPV4(
    String hostName)
    throws SvException;

  /**
   * Resolve the given hostname to an IPv6 address.
   *
   * @param hostName The name
   *
   * @return The address
   *
   * @throws SvException On errors
   */

  Inet6Address resolveIPV6(
    String hostName)
    throws SvException;

  /**
   * Resolve the given hostname to any addresses.
   *
   * @param hostName The name
   *
   * @return The addresses
   *
   * @throws SvException On errors
   */

  default InetAddress[] resolveAll(
    final String hostName)
  {
    Inet4Address ipv4 = null;
    Inet6Address ipv6 = null;

    try {
      ipv4 = this.resolveIPV4(hostName);
    } catch (final SvException e) {
      // Ignore
    }

    try {
      ipv6 = this.resolveIPV6(hostName);
    } catch (final SvException e) {
      // Ignore
    }

    if (ipv4 != null) {
      if (ipv6 != null) {
        return new InetAddress[]{ipv4, ipv6};
      } else {
        return new InetAddress[]{ipv4};
      }
    } else {
      if (ipv6 != null) {
        return new InetAddress[]{ipv6};
      } else {
        return new InetAddress[]{};
      }
    }
  }
}
