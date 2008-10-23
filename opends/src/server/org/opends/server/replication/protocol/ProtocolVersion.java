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
   * The constant for the first historical version of the replication protocol.
   */
  public static final short REPLICATION_PROTOCOL_V1 = 1;
  /**
   * The constant for the real value of the first historical version of the
   * replication protocol (was used in start messages only).
   */
  public static final short REPLICATION_PROTOCOL_V1_REAL = 49;
  /**
   * The constant for the second version of the replication protocol.
   */
  public static final short REPLICATION_PROTOCOL_V2 = 2;

  /**
   * The replication protocol version used by the instance of RS/DS in this VM.
   */
  private static short currentVersion = -1;

  static
  {
    resetCurrentVersion();
  }

  /**
   * Gets the current version of the replication protocol.
   *
   * @return The current version of the protocol.
   */
  public static short getCurrentVersion()
  {
    return currentVersion;
  }

  /**
   * For test purpose.
   * @param curVersion The provided current version.
   */
  public static void setCurrentVersion(short curVersion)
  {
    currentVersion = curVersion;
  }

  /**
   * Resets the protocol version to the default value (the latest version).
   * For test purpose.
   */
  public static void resetCurrentVersion()
  {
    currentVersion = REPLICATION_PROTOCOL_V2;
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
    short newVersion = (version < currentVersion ? version : currentVersion);
    return newVersion;
  }
}

