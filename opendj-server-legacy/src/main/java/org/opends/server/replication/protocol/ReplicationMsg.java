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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.util.zip.DataFormatException;

/**
 * Abstract class that must be used when defining messages that can
 * be sent for replication purpose between servers.
 *
 * When extending this class one should also create a new MSG_TYPE
 * and should update the generateMsg() method.
 */
public abstract class ReplicationMsg
{
  /** Reserved type for uses other than protocol messages. */
  public static final byte MSG_TYPE_DISK_ENCODING = -1;

  // PDU type values kept for compatibility with replication protocol version 1
  static final byte MSG_TYPE_MODIFY_V1 = 1;
  static final byte MSG_TYPE_ADD_V1 = 2;
  static final byte MSG_TYPE_DELETE_V1 = 3;
  static final byte MSG_TYPE_MODIFYDN_V1 = 4;
  static final byte MSG_TYPE_SERVER_START_V1 = 6;
  static final byte MSG_TYPE_REPL_SERVER_START_V1 = 7;
  static final byte MSG_TYPE_REPL_SERVER_INFO_V1 = 16;

  // PDU type values for current protocol version (see ProtocolVersion)
  static final byte MSG_TYPE_ACK = 5;
  static final byte MSG_TYPE_WINDOW = 8;
  static final byte MSG_TYPE_HEARTBEAT = 9;
  static final byte MSG_TYPE_INITIALIZE_REQUEST = 10;
  static final byte MSG_TYPE_INITIALIZE_TARGET = 11;
  static final byte MSG_TYPE_ENTRY = 12;
  static final byte MSG_TYPE_DONE = 13;
  static final byte MSG_TYPE_ERROR = 14;
  static final byte MSG_TYPE_WINDOW_PROBE = 15;
  static final byte MSG_TYPE_RESET_GENERATION_ID = 17;
  static final byte MSG_TYPE_REPL_SERVER_MONITOR_REQUEST = 18;
  static final byte MSG_TYPE_REPL_SERVER_MONITOR = 19;
  static final byte MSG_TYPE_SERVER_START = 20;
  static final byte MSG_TYPE_REPL_SERVER_START = 21;
  static final byte MSG_TYPE_MODIFY = 22;
  static final byte MSG_TYPE_ADD = 23;
  static final byte MSG_TYPE_DELETE = 24;
  static final byte MSG_TYPE_MODIFYDN = 25;
  static final byte MSG_TYPE_TOPOLOGY = 26;
  static final byte MSG_TYPE_START_SESSION = 27;
  static final byte MSG_TYPE_CHANGE_STATUS = 28;
  static final byte MSG_TYPE_GENERIC_UPDATE = 29;

  // Added for protocol version 3
  @Deprecated
  static final byte MSG_TYPE_START_ECL = 30;
  @Deprecated
  static final byte MSG_TYPE_START_ECL_SESSION = 31;
  @Deprecated
  static final byte MSG_TYPE_ECL_UPDATE = 32;
  static final byte MSG_TYPE_CT_HEARTBEAT = 33;

  // Added for protocol version 4
  // - New msgs types
  static final byte MSG_TYPE_REPL_SERVER_START_DS = 34;
  static final byte MSG_TYPE_STOP = 35;
  static final byte MSG_TYPE_INITIALIZE_RCV_ACK = 36;
  // - Modified msgs types
  //   EntryMsg, InitializeRequestMsg, InitializeTargetMsg, ErrorMsg
  //   TopologyMsg

  /** @since {@link ProtocolVersion#REPLICATION_PROTOCOL_V8} */
  static final byte MSG_TYPE_REPLICA_OFFLINE = 37;

  // Adding a new type of message here probably requires to
  // change accordingly generateMsg method below

  /**
   * Protected constructor.
   */
  protected ReplicationMsg()
  {
    // Nothing to do.
  }

  /**
   * Serializes the PDU using the provided replication protocol version.
   * WARNING: should be overwritten by a PDU (sub class) we want to support
   * older protocol version serialization for.
   *
   * @param protocolVersion
   *          The protocol version to use for serialization. The version should
   *          normally be older than the current one.
   * @return The encoded PDU, or <code>null</code> if the message isn't supported
   *          in that protocol version.
   */
  public abstract byte[] getBytes(short protocolVersion);

  /**
   * Generates a ReplicationMsg from its encoded form. This un-serialization is
   * done taking into account the various supported replication protocol
   * versions.
   *
   * @param buffer
   *          The encode form of the ReplicationMsg.
   * @param protocolVersion
   *          The version to use to decode the msg.
   * @return The generated SynchronizationMessage.
   * @throws DataFormatException
   *           If the encoded form was not a valid msg.
   * @throws NotSupportedOldVersionPDUException
   *           If the PDU is part of an old protocol version and we do not
   *           support it.
   */
  public static ReplicationMsg generateMsg(byte[] buffer, short protocolVersion)
      throws DataFormatException, NotSupportedOldVersionPDUException
  {
    switch (buffer[0])
    {
    case MSG_TYPE_SERVER_START_V1:
      throw new NotSupportedOldVersionPDUException("Server Start",
          ProtocolVersion.REPLICATION_PROTOCOL_V1, buffer[0]);
    case MSG_TYPE_REPL_SERVER_INFO_V1:
      throw new NotSupportedOldVersionPDUException("Replication Server Info",
          ProtocolVersion.REPLICATION_PROTOCOL_V1, buffer[0]);
    case MSG_TYPE_MODIFY:
      return new ModifyMsg(buffer);
    case MSG_TYPE_MODIFY_V1:
      return ModifyMsg.createV1(buffer);
    case MSG_TYPE_ADD:
    case MSG_TYPE_ADD_V1:
      return new AddMsg(buffer);
    case MSG_TYPE_DELETE:
    case MSG_TYPE_DELETE_V1:
      return new DeleteMsg(buffer);
    case MSG_TYPE_MODIFYDN:
    case MSG_TYPE_MODIFYDN_V1:
      return new ModifyDNMsg(buffer);
    case MSG_TYPE_ACK:
      return new AckMsg(buffer);
    case MSG_TYPE_SERVER_START:
      return new ServerStartMsg(buffer);
    case MSG_TYPE_REPL_SERVER_START:
    case MSG_TYPE_REPL_SERVER_START_V1:
      return new ReplServerStartMsg(buffer);
    case MSG_TYPE_WINDOW:
      return new WindowMsg(buffer);
    case MSG_TYPE_HEARTBEAT:
      return new HeartbeatMsg(buffer);
    case MSG_TYPE_INITIALIZE_REQUEST:
      return new InitializeRequestMsg(buffer, protocolVersion);
    case MSG_TYPE_INITIALIZE_TARGET:
      return new InitializeTargetMsg(buffer, protocolVersion);
    case MSG_TYPE_ENTRY:
      return new EntryMsg(buffer, protocolVersion);
    case MSG_TYPE_DONE:
      return new DoneMsg(buffer);
    case MSG_TYPE_ERROR:
      return new ErrorMsg(buffer, protocolVersion);
    case MSG_TYPE_RESET_GENERATION_ID:
      return new ResetGenerationIdMsg(buffer);
    case MSG_TYPE_WINDOW_PROBE:
      return new WindowProbeMsg(buffer);
    case MSG_TYPE_TOPOLOGY:
      return new TopologyMsg(buffer, protocolVersion);
    case MSG_TYPE_REPL_SERVER_MONITOR_REQUEST:
      return new MonitorRequestMsg(buffer);
    case MSG_TYPE_REPL_SERVER_MONITOR:
      return new MonitorMsg(buffer, protocolVersion);
    case MSG_TYPE_START_SESSION:
      return new StartSessionMsg(buffer, protocolVersion);
    case MSG_TYPE_CHANGE_STATUS:
      return new ChangeStatusMsg(buffer);
    case MSG_TYPE_GENERIC_UPDATE:
      return new UpdateMsg(buffer);
    case MSG_TYPE_START_ECL:
    case MSG_TYPE_START_ECL_SESSION:
    case MSG_TYPE_ECL_UPDATE:
      // Legacy versions never sent such messages to other instances (other JVMs).
      // They were only used in the combined DS-RS case.
      // It is safe to totally ignore these values since code now uses the ChangelogBackend.
      return null;
    case MSG_TYPE_CT_HEARTBEAT:
      return new ChangeTimeHeartbeatMsg(buffer, protocolVersion);
    case MSG_TYPE_REPL_SERVER_START_DS:
      return new ReplServerStartDSMsg(buffer);
    case MSG_TYPE_STOP:
      return new StopMsg(buffer);
    case MSG_TYPE_INITIALIZE_RCV_ACK:
      return new InitializeRcvAckMsg(buffer);
    case MSG_TYPE_REPLICA_OFFLINE:
      return new ReplicaOfflineMsg(buffer);
    default:
      throw new DataFormatException("received message with unknown type");
    }
  }
}
