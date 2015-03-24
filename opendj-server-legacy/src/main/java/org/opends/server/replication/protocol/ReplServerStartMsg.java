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
 * Message sent by a replication server to another replication server
 * at Startup.
 */
public class ReplServerStartMsg extends StartMsg
{
  private final int serverId;
  private final String serverURL;
  private final DN baseDN;
  private final int windowSize;
  private final ServerState serverState;

  /**
   * Whether to continue using SSL to encrypt messages after the start
   * messages have been exchanged.
   */
  private final boolean sslEncryption;

  /**
   * NOTE: Starting from protocol V4, we introduce a dedicated PDU for answering
   * to the DS ServerStartMsg. This is the ReplServerStartDSMsg. So the
   * degradedStatusThreshold value being used only by a DS, it could be removed
   * from the ReplServerStartMsg PDU. However for a smoothly transition to V4
   * protocol, we prefer to let this variable also in this PDU but the one
   * really used is in the ReplServerStartDSMsg PDU. This prevents from having
   * only RSv3 able to connect to RSv4 as connection initiator.
   *
   * Threshold value used by the RS to determine if a DS must be put in
   * degraded status because the number of pending changes for him has crossed
   * this value. This field is only used by a DS.
   */
  private int degradedStatusThreshold = -1;

  /**
   * Create a ReplServerStartMsg.
   *
   * @param serverId replication server id
   * @param serverURL replication server URL
   * @param baseDN base DN for which the ReplServerStartMsg is created.
   * @param windowSize The window size.
   * @param serverState our ServerState for this baseDN.
   * @param generationId The generationId for this server.
   * @param sslEncryption Whether to continue using SSL to encrypt messages
   *                      after the start messages have been exchanged.
   * @param groupId The group id of the RS
   * @param degradedStatusThreshold The degraded status threshold
   */
  public ReplServerStartMsg(int serverId, String serverURL, DN baseDN,
                               int windowSize,
                               ServerState serverState,
                               long generationId,
                               boolean sslEncryption,
                               byte groupId,
                               int degradedStatusThreshold)
  {
    super((short) -1 /* version set when sending */, generationId);
    this.serverId = serverId;
    this.serverURL = serverURL;
    this.baseDN = baseDN;
    this.windowSize = windowSize;
    this.serverState = serverState;
    this.sslEncryption = sslEncryption;
    this.groupId = groupId;
    this.degradedStatusThreshold = degradedStatusThreshold;
  }

  /**
   * Creates a new ReplServerStartMsg by decoding the provided byte array.
   * @param in A byte array containing the encoded information for the
   *             ReplServerStartMsg
   * @throws DataFormatException If the in does not contain a properly
   *                             encoded ReplServerStartMsg.
   */
  ReplServerStartMsg(byte[] in) throws DataFormatException
  {
    final ByteArrayScanner scanner = new ByteArrayScanner(in);
    decodeHeader(scanner,
        MSG_TYPE_REPL_SERVER_START, MSG_TYPE_REPL_SERVER_START_V1);

    /* The ReplServerStartMsg payload is stored in the form :
     * <baseDN><serverId><serverURL><windowSize><sslEncryption>
     * <degradedStatusThreshold><serverState>
     */
    baseDN = scanner.nextDN();
    serverId = scanner.nextIntUTF8();
    serverURL = scanner.nextString();
    windowSize = scanner.nextIntUTF8();
    sslEncryption = Boolean.valueOf(scanner.nextString());

    if (protocolVersion > ProtocolVersion.REPLICATION_PROTOCOL_V1)
    {
      degradedStatusThreshold = scanner.nextIntUTF8();
    }

    serverState = scanner.nextServerStateMustComeLast();
  }

  /**
   * Get the Server Id.
   * @return the server id
   */
  public int getServerId()
  {
    return this.serverId;
  }

  /**
   * Get the server URL.
   * @return the server URL
   */
  public String getServerURL()
  {
    return this.serverURL;
  }

  /**
   * Get the base DN from this ReplServerStartMsg.
   *
   * @return the base DN from this ReplServerStartMsg.
   */
  public DN getBaseDN()
  {
    return baseDN;
  }

  /**
   * Get the serverState.
   * @return Returns the serverState.
   */
  public ServerState getServerState()
  {
    return this.serverState;
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    if (protocolVersion == ProtocolVersion.REPLICATION_PROTOCOL_V1)
    {
      /*
       * The ReplServerStartMessage is stored in the form :
       * <operation type><basedn><serverid><serverURL><windowsize><serverState>
       */
      encodeHeader_V1(MSG_TYPE_REPL_SERVER_START_V1, builder);
      builder.appendDN(baseDN);
      builder.appendIntUTF8(serverId);
      builder.appendString(serverURL);
      builder.appendIntUTF8(windowSize);
      builder.appendString(Boolean.toString(sslEncryption));
      builder.appendServerStateMustComeLast(serverState);
    }
    else
    {
      /* The ReplServerStartMsg is stored in the form :
       * <operation type><baseDN><serverId><serverURL><windowSize><sslEncryption>
       * <degradedStatusThreshold><serverState>
       */
      encodeHeader(MSG_TYPE_REPL_SERVER_START, builder, protocolVersion);
      builder.appendDN(baseDN);
      builder.appendIntUTF8(serverId);
      builder.appendString(serverURL);
      builder.appendIntUTF8(windowSize);
      builder.appendString(Boolean.toString(sslEncryption));
      builder.appendIntUTF8(degradedStatusThreshold);
      builder.appendServerStateMustComeLast(serverState);
    }
    return builder.toByteArray();
  }

  /**
   * Get the window size for the server that created this message.
   *
   * @return The window size for the server that created this message.
   */
  public int getWindowSize()
  {
    return windowSize;
  }

  /**
   * Get the SSL encryption value for the server that created the
   * message.
   *
   * @return The SSL encryption value for the server that created the
   *         message.
   */
  public boolean getSSLEncryption()
  {
    return sslEncryption;
  }

  /**
   * Get the degraded status threshold value.
   * @return The degraded status threshold value.
   */
  public int getDegradedStatusThreshold()
  {
    return degradedStatusThreshold;
  }

  /**
   * Set the degraded status threshold (For test purpose).
   * @param degradedStatusThreshold The degraded status threshold to set.
   */
  public void setDegradedStatusThreshold(int degradedStatusThreshold)
  {
    this.degradedStatusThreshold = degradedStatusThreshold;
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return "ReplServerStartMsg content: " +
      "\nprotocolVersion: " + protocolVersion +
      "\ngenerationId: " + generationId +
      "\nbaseDN: " + baseDN +
      "\ngroupId: " + groupId +
      "\nserverId: " + serverId +
      "\nserverState: " + serverState +
      "\nserverURL: " + serverURL +
      "\nsslEncryption: " + sslEncryption +
      "\ndegradedStatusThreshold: " + degradedStatusThreshold +
      "\nwindowSize: " + windowSize;
  }
}
