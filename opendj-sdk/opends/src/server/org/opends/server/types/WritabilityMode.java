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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements an enumeration that may be used to control
 * the writability mode for the entire server or for a specific
 * backend.  The writability mode may be "enabled", "disabled", or
 * "internal-only".
 */
public enum WritabilityMode
{
  /**
   * Indicates that all write operations should be allowed.
   */
  ENABLED("enabled"),



  /**
   * Indicates that all write operations should be rejected.
   */
  DISABLED("disabled"),



  /**
   * Indicates that write operations from clients will be rejected,
   * but internal operations and updates through synchronization will
   * be allowed.
   */
  INTERNAL_ONLY("internal-only");



  // The human-readable name for this writability mode.
  private String modeName;



  /**
   * Creates a new writability mode with the provided name.
   *
   * @param  modeName  The human-readable name for this writability
   *                   mode.
   */
  private WritabilityMode(String modeName)
  {
    this.modeName = modeName;
  }



  /**
   * Retrieves the writability mode for the specified name.
   *
   * @param  modeName  The name of the writability mode to retrieve.
   *
   * @return  The requested writability mode, or <CODE>null</CODE> if
   *          the provided value is not the name of a valid mode.
   */
  public static WritabilityMode modeForName(String modeName)
  {
    String lowerName = toLowerCase(modeName);
    if (lowerName.equals("enabled"))
    {
      return WritabilityMode.ENABLED;
    }
    else if (lowerName.equals("disabled"))
    {
      return WritabilityMode.DISABLED;
    }
    else if (lowerName.equals("internal-only"))
    {
      return WritabilityMode.INTERNAL_ONLY;
    }
    else
    {
      return null;
    }
  }



  /**
   * Retrieves a string representation of this writability mode.
   *
   * @return  A string representation of this writability mode.
   */
  public String toString()
  {
    return modeName;
  }
}

