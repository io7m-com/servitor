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

/**
 * A volume flag.
 */

public enum SvVolumeFlag
{
  /**
   * The volume is mounted read-only.
   */

  READ_ONLY("ro"),

  /**
   * The volume is mounted read-write.
   */

  READ_WRITE("rw"),

  /**
   * The volume is automatically chowned by the container.
   */

  USE_CORRECT_UID_GID("U"),

  /**
   * The volume is relabelled with an SELinux label that makes the volume accessible by other containers.
   */

  RELABEL_SHARED("z"),

  /**
   * The volume is relabelled with an SELinux label that makes the volume inaccessible by other containers.
   */

  RELABEL_PRIVATE("Z");

  private final String flag;

  /**
   * @return The flag string (such as "Z", "rw", etc).
   */

  public String flag()
  {
    return this.flag;
  }

  SvVolumeFlag(
    final String inFlag)
  {
    this.flag = Objects.requireNonNull(inFlag, "flag");
  }
}
