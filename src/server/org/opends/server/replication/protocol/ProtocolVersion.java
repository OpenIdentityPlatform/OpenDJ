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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
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
   * Add fields in the header for assured replication.
   */
  public static final short REPLICATION_PROTOCOL_V2 = 2;

  /**
   * The constant for the 3rd version of the replication protocol.
   * Add messages for remote ECL : not used as of today.
   */
  public static final short REPLICATION_PROTOCOL_V3 = 3;

  /**
   * The constant for the 4th version of the replication protocol.
   * - Add to the body of the ADD/MOD/MODDN/DEL msgs, a list of attribute for
   *   ECL entry attributes.
   * - Modified algorithm for choosing a RS to connect to: introduction of a
   *   ReplicationServerDSMsg message.
   *   -> also added of the server URL in RSInfo of TopologyMsg
   * - Introduction of a StopMsg for proper connections ending.
   * - Initialization failover/flow control
   */
  public static final short REPLICATION_PROTOCOL_V4 = 4;

  /**
   * The constant for the 5th version of the replication protocol.
   * - Add support for wild-cards in change log included attributes
   * - Add support for specifying additional included attributes for deletes
   * - See OPENDJ-194.
   */
  public static final short REPLICATION_PROTOCOL_V5 = 5;

  /**
   * The constant for the 6th version of the replication protocol.
   * - include DS local URL in the DSInfo of TopologyMsg.
   */
  public static final short REPLICATION_PROTOCOL_V6 = 6;

  /**
   * The constant for the 7th version of the replication protocol.
   * - compact encoding for length, CSNs, and server IDs.
   */
  public static final short REPLICATION_PROTOCOL_V7 = 7;

  /**
   * The replication protocol version used by the instance of RS/DS in this VM.
   */
  private static final short CURRENT_VERSION = REPLICATION_PROTOCOL_V7;

  /**
   * Gets the current version of the replication protocol.
   *
   * @return The current version of the protocol.
   */
  public static short getCurrentVersion()
  {
    return CURRENT_VERSION;
  }

  /**
   * Specifies the oldest version of the protocol from the provided one
   * and the current one.
   *
   * @param version The version to be compared to the current one.
   * @return The minimal protocol version.
   */
  public static short getCompatibleVersion(short version)
  {
    return (version < CURRENT_VERSION ? version : CURRENT_VERSION);
  }
}

