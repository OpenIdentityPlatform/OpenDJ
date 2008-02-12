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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.protocol;


/**
 * The version utility class for the replication protocol.
 */
public class ProtocolVersion
{
  /**
   * Get the version included in the Start Message mean the replication
   * protocol version used by the server that created the message.
   *
   * @return The version used by the server that created the message.
   */
  static short CURRENT_VERSION = 1;

  /**
   * Specifies the current version of the replication protocol.
   *
   * @return The current version of the protocol.
   */
  public static short currentVersion()
  {
    return CURRENT_VERSION;
  }

  /**
   * For test purpose.
   * @param currentVersion The provided current version.
   */
  public static void setCurrentVersion(short currentVersion)
  {
    CURRENT_VERSION = currentVersion;
  }

  /**
   * Specifies the oldest version of the protocol from the provided one
   * and the current one.
   *
   * @param version The version to be compared to the current one.
   * @return The minimal protocol version.
   */
  public static short minWithCurrent(short version)
  {
    Short sVersion = Short.valueOf(version);
    Short newVersion = (sVersion<CURRENT_VERSION?sVersion:CURRENT_VERSION);
    return newVersion;
  }
}

