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


package com.io7m.servitor.validation.internal;

import com.io7m.seltzer.api.SStructuredError;
import com.io7m.servitor.core.SvConfiguration;
import com.io7m.servitor.validation.SvValidatorType;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * The standard validator.
 */

public final class SvValidator implements SvValidatorType
{
  private final SvConfiguration configuration;
  private final List<SvValidatorCheckType> checks;

  /**
   * The standard validator.
   *
   * @param inConfiguration The configuration
   * @param inChecks        The checks to execute
   */

  public SvValidator(
    final SvConfiguration inConfiguration,
    final List<SvValidatorCheckType> inChecks)
  {
    this.configuration =
      Objects.requireNonNull(inConfiguration, "configuration");
    this.checks =
      Objects.requireNonNull(inChecks, "checks");
  }

  @Override
  public List<SStructuredError<String>> validate()
  {
    final var results = new LinkedList<SStructuredError<String>>();
    for (final var check : this.checks) {
      results.addAll(check.check(this.configuration));
    }
    return List.copyOf(results);
  }
}
