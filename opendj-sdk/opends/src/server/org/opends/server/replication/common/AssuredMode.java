/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.common;

/**
 * The various modes supported for assured replication.
 */
public enum AssuredMode
{
  /**
   * Safe read assured mode.
   */
  SAFE_READ_MODE((byte) 1),
  /**
   * Safe data assured mode.
   */
  SAFE_DATA_MODE((byte) 2);

  // The mode value
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
