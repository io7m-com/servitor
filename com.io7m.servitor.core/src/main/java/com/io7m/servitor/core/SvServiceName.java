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
import java.util.regex.Pattern;

/**
 * The type of service names.
 *
 * @param value The name value
 */

public record SvServiceName(String value)
{
  private static final Pattern VALID =
    Pattern.compile("[a-z][a-z0-9_\\\\-]{0,63}");

  /**
   * The type of service names.
   *
   * @param value The name value
   */

  public SvServiceName
  {
    Objects.requireNonNull(value, "value");

    if (!VALID.matcher(value).matches()) {
      throw new IllegalArgumentException(
        "Service name '%s' must match '%s'"
          .formatted(value, VALID)
      );
    }
  }

  @Override
  public String toString()
  {
    return this.value;
  }
}
