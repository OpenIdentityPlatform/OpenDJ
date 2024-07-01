/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2015 ForgeRock AS.
 */
package org.opends.server.backends.pluggable.spi;

/** Defines access modes of a Storage. */
public enum AccessMode {
  /** Constant used to open the Storage in read-only mode; implies missing trees will be ignored. */
  READ_ONLY(false),
  /** Constant used to open the Storage in read-write mode; implies trees will be created if not already present. */
  READ_WRITE(true);

  private final boolean readWrite;

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
