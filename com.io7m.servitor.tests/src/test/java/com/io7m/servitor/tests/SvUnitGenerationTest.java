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

import com.io7m.servitor.systemd.SvUnitGeneration;
import com.io7m.servitor.xml.SvConfigurationFiles;
import org.apache.commons.configuration2.INIConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;

public final class SvUnitGenerationTest
{
  private static final Logger LOG =
    LoggerFactory.getLogger(SvUnitGenerationTest.class);

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
  public void testGenerateConfig()
    throws Exception
  {
    final var file =
      SvTestDirectories.resourceOf(
        SvConfigurationsTest.class, this.directory, "config.xml");

    final var configuration =
      SvConfigurationFiles.parse(file);

    final var units =
      SvUnitGeneration.generate(configuration);

    for (final var unit : units) {
      try (var reader = new StringReader(unit.fileText())) {
        final var config =
          INIConfiguration.builder()
            .build();
        config.read(reader);
      }
    }
  }
}
