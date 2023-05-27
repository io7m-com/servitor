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


package com.io7m.servitor.validation;

import com.io7m.servitor.core.SvConfiguration;
import com.io7m.servitor.validation.internal.SvValidator;
import com.io7m.servitor.validation.internal.SvValidatorCheckType;

import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The default factory of validators.
 */

public final class SvValidators implements SvValidatorFactoryType
{
  private final Supplier<List<SvValidatorCheckType>> checks;

  private SvValidators(
    final Supplier<List<SvValidatorCheckType>> inChecks)
  {
    this.checks = Objects.requireNonNull(inChecks, "checks");
  }

  /**
   * Create a new validator factory, loading checks from {@link ServiceLoader}.
   *
   * @return A new validator factory
   */

  public static SvValidatorFactoryType createFromServiceLoader()
  {
    return new SvValidators(() -> {
      return ServiceLoader.load(SvValidatorCheckType.class)
        .stream()
        .map(ServiceLoader.Provider::get)
        .collect(Collectors.toList());
    });
  }

  @Override
  public SvValidatorType create(
    final SvConfiguration configuration)
  {
    return new SvValidator(configuration, this.checks.get());
  }
}
