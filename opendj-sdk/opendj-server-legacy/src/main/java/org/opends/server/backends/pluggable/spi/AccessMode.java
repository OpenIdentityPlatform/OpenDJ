/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable.spi;

/** Defines access modes of a Storage. */
public enum AccessMode {
  /** Constant used to open the Storage in read-only mode; implies missing trees will be ignored. */
  READ_ONLY(false),
  /** Constant used to open the Storage in read-write mode; implies trees will be created if not already present. */
  READ_WRITE(true);

  private boolean readWrite;

  AccessMode(boolean update)
  {
    this.readWrite = update;
  }

  /**
   * Returns if the storage is being opened READ_WRITE.
   *
   * @return true if the storage is being opened READ_WRITE
   */
  public boolean isWriteable()
  {
    return readWrite;
  }
}
