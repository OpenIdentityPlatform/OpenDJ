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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.util.zip.DataFormatException;

import org.opends.server.replication.common.ServerState;
import org.opends.server.types.DN;

/**
 * This message is used by LDAP server when they first connect.
 * to a replication server to let them know who they are and what is their state
 * (their RUV)
 */
public class ServerStartMsg extends StartMsg
{
  /** Id of the LDAP server that sent this message. */
  private final int serverId;
  private final String serverURL;
  private final DN baseDN;
  private final int maxReceiveQueue;
  private final int maxSendQueue;
  private final int maxReceiveDelay;
  private final int maxSendDelay;
  private final int windowSize;
  private final ServerState serverState;

  /**
   * The time in milliseconds between heartbeats from the replication
   * server.  Zero means heartbeats are off.
   */
  private final long heartbeatInterval;

  /**
   * Whether to continue using SSL to encrypt messages after the start
   * messages have been exchanged.
   */
  private final boolean sslEncryption;

  /**
   * Creates a new ServerStartMsg. This message is to be sent by an LDAP
   * Server after being connected to a replication server for a given
   * replication domain.
   *
   * @param serverId2 The serverId of the server for which the ServerStartMsg
   *                 is created.
   * @param serverURL directory server URL
   * @param baseDN   The base DN.
   * @param windowSize   The window size used by this server.
   * @param heartbeatInterval The requested heartbeat interval.
   * @param serverState  The state of this server.
   * @param generationId The generationId for this server.
   * @param sslEncryption Whether to continue using SSL to encrypt messages
   *                      after the start messages have been exchanged.
   * @param groupId The group id of the DS for this DN
   */
  public ServerStartMsg(int serverId2, String serverURL, DN baseDN,
      int windowSize, long heartbeatInterval, ServerState serverState,
      long generationId, boolean sslEncryption,
      byte groupId)
  {
    super((short) -1 /* version set when sending */, generationId);

    this.serverId = serverId2;
    this.serverURL = serverURL;
    this.baseDN = baseDN;
    this.maxReceiveDelay = 0;
    this.maxReceiveQueue = 0;
    this.maxSendDelay = 0;
    this.maxSendQueue = 0;
    this.windowSize = windowSize;
    this.heartbeatInterval = heartbeatInterval;
    this.sslEncryption = sslEncryption;
    this.serverState = serverState;
    this.groupId = groupId;
  }

  /**
   * Creates a new ServerStartMsg from its encoded form.
   *
   * @param in The byte array containing the encoded form of the
   *           ServerStartMsg.
   * @throws DataFormatException If the byte array does not contain a valid
   *                             encoded form of the ServerStartMsg.
   */
  ServerStartMsg(byte[] in) throws DataFormatException
  {
    final ByteArrayScanner scanner = new ByteArrayScanner(in);
    decodeHeader(scanner, MSG_TYPE_SERVER_START);

    baseDN = scanner.nextDN();
    serverId = scanner.nextIntUTF8();
    serverURL = scanner.nextString();
    maxReceiveDelay = scanner.nextIntUTF8();
    maxReceiveQueue = scanner.nextIntUTF8();
    maxSendDelay = scanner.nextIntUTF8();
    maxSendQueue = scanner.nextIntUTF8();
    windowSize = scanner.nextIntUTF8();
    heartbeatInterval = scanner.nextIntUTF8();
    sslEncryption = Boolean.valueOf(scanner.nextString());
    serverState = scanner.nextServerStateMustComeLast();
  }

  /**
   * Get the ServerID from the message.
   * @return the server ID
   */
  public int getServerId()
  {
    return serverId;
  }

  /**
   * Get the Server URL from the message.
   * @return the server URL
   */
  public String getServerURL()
  {
    return serverURL;
  }

  /**
   * Get the baseDN.
   *
   * @return Returns the baseDN.
   */
  public DN getBaseDN()
  {
    return baseDN;
  }

  /**
   * Get the maxReceiveDelay.
   * @return Returns the maxReceiveDelay.
   */
  public int getMaxReceiveDelay()
  {
    return maxReceiveDelay;
  }

  /**
   * Get the maxReceiveQueue.
   * @return Returns the maxReceiveQueue.
   */
  public int getMaxReceiveQueue()
  {
    return maxReceiveQueue;
  }

  /**
   * Get the maxSendDelay.
   * @return Returns the maxSendDelay.
   */
  public int getMaxSendDelay()
  {
    return maxSendDelay;
  }

  /**
   * Get the maxSendQueue.
   * @return Returns the maxSendQueue.
   */
  public int getMaxSendQueue()
  {
    return maxSendQueue;
  }

  /**
   * Get the ServerState.
   * @return The ServerState.
   */
  public ServerState getServerState()
  {
    return serverState;
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    encodeHeader(MSG_TYPE_SERVER_START, builder, protocolVersion);

    builder.appendDN(baseDN);
    builder.appendIntUTF8(serverId);
    builder.appendString(serverURL);
    builder.appendIntUTF8(maxReceiveDelay);
    builder.appendIntUTF8(maxReceiveQueue);
    builder.appendIntUTF8(maxSendDelay);
    builder.appendIntUTF8(maxSendQueue);
    builder.appendIntUTF8(windowSize);
    builder.appendLongUTF8(heartbeatInterval);
    builder.appendString(Boolean.toString(sslEncryption));
    builder.appendServerStateMustComeLast(serverState);
    return builder.toByteArray();
  }

  /**
   * Get the window size for the ldap server that created the message.
   *
   * @return The window size for the ldap server that created the message.
   */
  public int getWindowSize()
  {
    return windowSize;
  }

  /**
   * Get the heartbeat interval requested by the ldap server that created the
   * message.
   *
   * @return The heartbeat interval requested by the ldap server that created
   * the message.
   */
  public long getHeartbeatInterval()
  {
    return heartbeatInterval;
  }

  /**
   * Get the SSL encryption value for the ldap server that created the
   * message.
   *
   * @return The SSL encryption value for the ldap server that created the
   *         message.
   */
  public boolean getSSLEncryption()
  {
    return sslEncryption;
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return "ServerStartMsg content: " +
      "\nprotocolVersion: " + protocolVersion +
      "\ngenerationId: " + generationId +
      "\ngroupId: " + groupId +
      "\nbaseDN: " + baseDN +
      "\nheartbeatInterval: " + heartbeatInterval +
      "\nmaxReceiveDelay: " + maxReceiveDelay +
      "\nmaxReceiveQueue: " + maxReceiveQueue +
      "\nmaxSendDelay: " + maxSendDelay +
      "\nmaxSendQueue: " + maxSendQueue +
      "\nserverId: " + serverId +
      "\nserverState: " + serverState +
      "\nserverURL: " + serverURL +
      "\nsslEncryption: " + sslEncryption +
      "\nwindowSize: " + windowSize;
  }
}
