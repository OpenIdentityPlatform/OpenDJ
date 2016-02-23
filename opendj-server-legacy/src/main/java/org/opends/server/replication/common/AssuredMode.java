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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2015 ForgeRock AS.
 */
package org.opends.server.replication.common;

/**
 * The various modes supported for assured replication.
 */
public enum AssuredMode
{
  /** Safe read assured mode. */
  SAFE_READ_MODE((byte) 1),
  /** Safe data assured mode. */
  SAFE_DATA_MODE((byte) 2);

  /** The mode value. */
  private byte value = -1;

  private AssuredMode(byte value)
  {
    this.value = value;
  }

  /**
   * Returns the AssuredMode matching the passed mode numeric representation.
   * @param value The numeric value for the mode to return
   * @return The matching AssuredMode
   * @throws java.lang.IllegalArgumentException If provided mode value is
   * wrong
   */
  public static AssuredMode valueOf(byte value) throws IllegalArgumentException
  {
    switch (value)
    {
      case 1:
        return SAFE_READ_MODE;
      case 2:
        return SAFE_DATA_MODE;
      default:
        throw new IllegalArgumentException("Wrong assured mode numeric value: "
          + value);
    }
  }

  /**
   * Get a numeric representation of the mode.
   * @return The numeric representation of the mode
   */
  public byte getValue()
  {
    return value;
  }

}
