/*
 * Copyright © 2025 Mark Raynsford <code@io7m.com> https://www.io7m.com
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

package com.io7m.servitor.tests;

import com.io7m.servitor.core.SvAddressResolverDNSJ;
import com.io7m.servitor.core.SvException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class SvAddressResolverDNSJTest
{
  @Test
  public void testA()
    throws Exception
  {
    final var resolver =
      SvAddressResolverDNSJ.create(Optional.empty());

    final var r =
      resolver.resolveIPV4("www.io7m.com");
  }

  @Test
  public void testAAAA()
    throws Exception
  {
    final var resolver =
      SvAddressResolverDNSJ.create(Optional.empty());

    final var r =
      resolver.resolveIPV6("www.io7m.com");
  }

  @Test
  public void testANonexistent()
    throws Exception
  {
    final var resolver =
      SvAddressResolverDNSJ.create(Optional.empty());

    final var ex =
      assertThrows(SvException.class, () -> {
        resolver.resolveIPV4("nonexistent.io7m.com");
      });

    assertEquals("error-dns-resolution", ex.errorCode());
  }

  @Test
  public void testAAAANonexistent()
    throws Exception
  {
    final var resolver =
      SvAddressResolverDNSJ.create(Optional.empty());

    final var ex =
      assertThrows(SvException.class, () -> {
        resolver.resolveIPV6("nonexistent.io7m.com");
      });

    assertEquals("error-dns-resolution", ex.errorCode());
  }

  @Test
  public void testAll()
    throws Exception
  {
    final var resolver =
      SvAddressResolverDNSJ.create(Optional.empty());

    final var r =
      resolver.resolveAll("www.io7m.com");

    assertEquals(2, r.length);
  }

  @Test
  public void testAllNonexistent()
    throws Exception
  {
    final var resolver =
      SvAddressResolverDNSJ.create(Optional.empty());

    final var r =
      resolver.resolveAll("nonexistent.io7m.com");

    assertEquals(0, r.length);
  }
}
