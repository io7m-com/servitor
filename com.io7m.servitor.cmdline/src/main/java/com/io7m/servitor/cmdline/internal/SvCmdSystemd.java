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


package com.io7m.servitor.cmdline.internal;

import com.io7m.quarrel.core.QCommandContextType;
import com.io7m.quarrel.core.QCommandMetadata;
import com.io7m.quarrel.core.QCommandStatus;
import com.io7m.quarrel.core.QCommandType;
import com.io7m.quarrel.core.QParameterNamed1;
import com.io7m.quarrel.core.QParameterNamedType;
import com.io7m.quarrel.core.QParametersPositionalNone;
import com.io7m.quarrel.core.QParametersPositionalType;
import com.io7m.quarrel.core.QStringType.QConstant;
import com.io7m.quarrel.ext.logback.QLogback;
import com.io7m.servitor.systemd.SvUnitGeneration;
import com.io7m.servitor.validation.SvValidators;
import com.io7m.servitor.xml.SvConfigurationFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.io7m.quarrel.core.QCommandStatus.SUCCESS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Generate systemd units.
 */

public final class SvCmdSystemd implements QCommandType
{
  private static final Logger LOG =
    LoggerFactory.getLogger(SvCmdSystemd.class);

  private static final QParameterNamed1<Path> CONFIGURATION =
    new QParameterNamed1<>(
      "--configuration",
      List.of(),
      new QConstant("The configuration file."),
      Optional.empty(),
      Path.class
    );

  private static final QParameterNamed1<Path> OUTPUT_DIRECTORY =
    new QParameterNamed1<>(
      "--output-directory",
      List.of(),
      new QConstant("The output directory."),
      Optional.empty(),
      Path.class
    );

  /**
   * Generate systemd units.
   */

  public SvCmdSystemd()
  {

  }

  @Override
  public List<QParameterNamedType<?>> onListNamedParameters()
  {
    return Stream.concat(
      Stream.of(CONFIGURATION, OUTPUT_DIRECTORY),
      QLogback.parameters().stream()
    ).toList();
  }

  @Override
  public QParametersPositionalType onListPositionalParameters()
  {
    return new QParametersPositionalNone();
  }

  @Override
  public QCommandStatus onExecute(
    final QCommandContextType context)
    throws Exception
  {
    QLogback.configure(context);

    final var configurationFile =
      context.parameterValue(CONFIGURATION);
    final var outputDirectory =
      context.parameterValue(OUTPUT_DIRECTORY);

    final var configuration =
      SvConfigurationFiles.parse(configurationFile);

    final var validators =
      SvValidators.createFromServiceLoader();
    final var validator =
      validators.create(configuration);
    final var errors =
      validator.validate();

    if (!errors.isEmpty()) {
      throw new IllegalStateException();
    }

    final var units =
      SvUnitGeneration.generate(configuration);

    Files.createDirectories(outputDirectory);

    for (final var unit : units) {
      final var outputFile =
        outputDirectory.resolve(unit.fileName());
      final var outputFileTmp =
        outputDirectory.resolve(unit.fileName() + ".tmp");

      Files.writeString(
        outputFileTmp,
        unit.fileText(),
        UTF_8,
        TRUNCATE_EXISTING,
        WRITE,
        CREATE
      );

      Files.move(
        outputFileTmp,
        outputFile,
        ATOMIC_MOVE,
        REPLACE_EXISTING
      );

      LOG.info("wrote {}", outputFile);
    }

    return SUCCESS;
  }

  @Override
  public QCommandMetadata metadata()
  {
    return new QCommandMetadata(
      "systemd",
      new QConstant("Generate systemd service files."),
      Optional.empty()
    );
  }
}
