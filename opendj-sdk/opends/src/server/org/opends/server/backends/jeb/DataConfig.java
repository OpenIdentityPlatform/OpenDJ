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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.backends.jeb;

/**
 * Configuration class to indicate desired compression and cryptographic options
 * for the data stored in the database.
 */
public class DataConfig
{
  /**
   * Indicates whether data should be compressed before writing to the database.
   */
  private boolean compressed = false;



  /**
   * Determine whether data should be compressed before writing to the database.
   * @return true if data should be compressed, false if not.
   */
  public boolean isCompressed()
  {
    return compressed;
  }



  /**
   * Configure whether data should be compressed before writing to the database.
   * @param compressed true if data should be compressed, false if not.
   */
  public void setCompressed(boolean compressed)
  {
    this.compressed = compressed;
  }

  /**
   * Get a string representation of this object.
   * @return A string representation of this object.
   */
  public String toString()
  {
    StringBuilder builder = new StringBuilder();
    if (compressed)
    {
      builder.append("[compressed]");
    }
    return builder.toString();
  }
}
