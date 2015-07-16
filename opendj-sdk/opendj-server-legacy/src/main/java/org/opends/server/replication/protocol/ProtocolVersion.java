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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
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
   * <ul>
   * <li>Add fields in the header for assured replication.</li>
   * </ul>
   */
  public static final short REPLICATION_PROTOCOL_V2 = 2;

  /**
   * The constant for the 3rd version of the replication protocol.
   * <ul>
   * <li>Add messages for remote ECL : not used as of today.</li>
   * </ul>
   */
  public static final short REPLICATION_PROTOCOL_V3 = 3;

  /**
   * The constant for the 4th version of the replication protocol.
   * <ul>
   * <li>Add to the body of the ADD/MOD/MODDN/DEL msgs, a list of attribute for
   * ECL entry attributes.</li>
   * <li>Modified algorithm for choosing a RS to connect to: introduction of a
   * ReplicationServerDSMsg message.</li>
   * <li>also added of the server URL in RSInfo of TopologyMsg</li>
   * <li>Introduction of a StopMsg for proper connections ending.</li>
   * <li>Initialization failover/flow control</li>
   * </ul>
   */
  public static final short REPLICATION_PROTOCOL_V4 = 4;

  /**
   * The constant for the 5th version of the replication protocol.
   * <ul>
   * <li>Add support for wild-cards in change log included attributes</li>
   * <li>Add support for specifying additional included attributes for deletes</li>
   * <li>See OPENDJ-194.</li>
   * </ul>
   */
  public static final short REPLICATION_PROTOCOL_V5 = 5;

  /**
   * The constant for the 6th version of the replication protocol.
   * <ul>
   * <li>include DS local URL in the DSInfo of TopologyMsg.</li>
   * </ul>
   */
  public static final short REPLICATION_PROTOCOL_V6 = 6;

  /**
   * The constant for the 7th version of the replication protocol.
   * <ul>
   * <li>compact encoding for length, CSNs, and server IDs.</li>
   * </ul>
   */
  public static final short REPLICATION_PROTOCOL_V7 = 7;

  /**
   * The constant for the 8th version of the replication protocol.
   * <ul>
   * <li>New ReplicaOfflineMsg.</li>
   * </ul>
   */
  public static final short REPLICATION_PROTOCOL_V8 = 8;

  /**
   * The replication protocol version used by the instance of RS/DS in this VM.
   */
  private static final short CURRENT_VERSION = REPLICATION_PROTOCOL_V8;

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
    return version < CURRENT_VERSION ? version : CURRENT_VERSION;
  }
}
