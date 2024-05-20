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

import java.util.Optional;

/**
 * Service resource limits.
 *
 * @param cpuPercent      The cpu percentage limit
 * @param memoryLimitSoft The soft memory limit in bytes; exceeding this
 *                        value will cause throttling of processes
 * @param memoryLimitHard The hard memory limit in bytes; exceeding this
 *                        value will kill the process
 *
 * @see "https://www.freedesktop.org/software/systemd/man/latest/systemd.resource-control.html"
 */

public record SvLimits(
  Optional<Integer> cpuPercent,
  Optional<Long> memoryLimitSoft,
  Optional<Long> memoryLimitHard)
{
  /**
   * @return {@code true} if any memory limits are defined
   */

  public boolean memoryLimited()
  {
    return this.memoryLimitSoft.isPresent() || this.memoryLimitHard.isPresent();
  }
}
