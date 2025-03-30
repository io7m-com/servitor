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

import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddressString;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Name;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.Type;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@code dnsjava} resolver.
 */

public final class SvAddressResolverDNSJ
  implements SvAddressResolverType
{
  private final SimpleResolver resolver;

  private SvAddressResolverDNSJ(
    final SimpleResolver inResolver)
  {
    this.resolver =
      Objects.requireNonNull(inResolver, "resolver");
  }

  /**
   * Create a new resolver. If a DNS server is provided, it is used. Otherwise,
   * the default system DNS server is used.
   *
   * @param dnsServer The server
   *
   * @return A resolver
   *
   * @throws SvException On errors
   */

  public static SvAddressResolverType create(
    final Optional<String> dnsServer)
    throws SvException
  {
    try {
      final var resolver =
        new SimpleResolver(dnsServer.orElse(null));

      return new SvAddressResolverDNSJ(resolver);
    } catch (final UnknownHostException e) {
      throw new SvException(
        e.getMessage(),
        e,
        "error-dns-server",
        Map.ofEntries(
          Map.entry("Server", dnsServer.orElse("<unspecified>"))
        ),
        Optional.empty()
      );
    }
  }

  @Override
  public Inet4Address resolveIPV4(
    final String hostName)
    throws SvException
  {
    try {
      try {
        final var ipv4Address =
          new IPAddressString(hostName).toAddress();
        return (Inet4Address) Inet4Address.getByAddress(ipv4Address.getBytes());
      } catch (final AddressStringException e) {
        // Ignore!
      }

      final var lookup =
        new Lookup(
          Name.fromString(hostName, Name.root),
          Type.A,
          DClass.IN
        );

      lookup.setResolver(this.resolver);

      final var records = lookup.run();
      if (records != null) {
        for (final var record : records) {
          if (record instanceof final ARecord aRecord) {
            return (Inet4Address) aRecord.getAddress();
          }
        }
      }

      throw new IOException("No A record for the given name.");
    } catch (final IOException e) {
      throw new SvException(
        e.getMessage(),
        e,
        "error-dns-resolution",
        Map.ofEntries(
          Map.entry("Address", hostName),
          Map.entry("Record Type", "A")
        ),
        Optional.empty()
      );
    }
  }

  @Override
  public Inet6Address resolveIPV6(
    final String hostName)
    throws SvException
  {
    try {
      try {
        final var ipv6Address =
          new IPAddressString(hostName).toAddress();
        return (Inet6Address) Inet6Address.getByAddress(ipv6Address.getBytes());
      } catch (final AddressStringException e) {
        // Ignore!
      }

      final var lookup =
        new Lookup(
          Name.fromString(hostName, Name.root),
          Type.AAAA,
          DClass.IN
        );

      lookup.setResolver(this.resolver);

      final var records = lookup.run();
      if (records != null) {
        for (final var record : records) {
          if (record instanceof final AAAARecord aRecord) {
            return (Inet6Address) aRecord.getAddress();
          }
        }
      }

      throw new IOException("No AAAA record for the given name.");
    } catch (final IOException e) {
      throw new SvException(
        e.getMessage(),
        e,
        "error-dns-resolution",
        Map.ofEntries(
          Map.entry("Address", hostName),
          Map.entry("Record Type", "AAAA")
        ),
        Optional.empty()
      );
    }
  }
}
