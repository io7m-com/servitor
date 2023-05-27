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


package com.io7m.servitor.tests;

import com.io7m.servitor.core.SvException;
import com.io7m.servitor.core.SvServiceElementType;
import com.io7m.servitor.xml.SvConfigurationFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class SvConfigurationsTest
{
  private static final Logger LOG =
    LoggerFactory.getLogger(SvConfigurationsTest.class);

  private Path directory;

  @BeforeEach
  public void setup()
    throws IOException
  {
    this.directory = SvTestDirectories.createTempDirectory();
  }

  @AfterEach
  public void tearDown()
    throws IOException
  {
    SvTestDirectories.deleteDirectory(this.directory);
  }

  @Test
  public void testParseConfig()
    throws Exception
  {
    final var file =
      SvTestDirectories.resourceOf(
        SvConfigurationsTest.class, this.directory, "config.xml");

    final var configuration =
      SvConfigurationFiles.parse(file);
    final var services =
      configuration.services();

    {
      final var s =
        services.get(UUID.fromString("edbb08e5-7bc6-4f3b-9068-4c82df07ed34"));
    }

    final var g = configuration.graph();
    final var roots =
      g.vertexSet()
        .stream()
        .filter(v -> g.inDegreeOf(v) == 0)
        .toList();

    assertEquals(5, roots.size());
    assertTrue(
      roots.stream()
        .anyMatch(s -> Objects.equals(
          s.id(),
          UUID.fromString("edbb08e5-7bc6-4f3b-9068-4c82df07ed34")))
    );
    assertTrue(
      roots.stream()
        .anyMatch(s -> Objects.equals(
          s.id(),
          UUID.fromString("f24a5e58-fb20-445f-97fa-9b9763d454e6")))
    );
    assertTrue(
      roots.stream()
        .anyMatch(s -> Objects.equals(
          s.id(),
          UUID.fromString("37c6204e-6f70-49b2-bbd0-d2f4f642a91a")))
    );
    assertTrue(
      roots.stream()
        .anyMatch(s -> Objects.equals(
          s.id(),
          UUID.fromString("0490e76a-2b48-4533-883a-03c461d9a0d3")))
    );
    assertTrue(
      roots.stream()
        .anyMatch(s -> Objects.equals(
          s.id(),
          UUID.fromString("0292dda0-9052-4dc8-ba8a-fbd3ab2c78f7")))
    );
  }

  @Test
  public void testInvalid0()
    throws Exception
  {
    final var file =
      SvTestDirectories.resourceOf(
        SvConfigurationsTest.class, this.directory, "invalid-0.xml");

    final var ex =
      assertThrows(SvException.class, () -> {
        SvConfigurationFiles.parse(file);
      });

    LOG.debug("", ex);
  }
}
