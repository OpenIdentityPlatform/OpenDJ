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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.util.zip.DataFormatException;

import org.opends.server.replication.common.ServerState;
import org.opends.server.types.DN;

/**
 * Message sent by a replication server to a directory server in reply to the
 * ServerStartMsg.
 */
public class ReplServerStartDSMsg extends StartMsg
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
   * Threshold value used by the RS to determine if a DS must be put in
   * degraded status because the number of pending changes for him has crossed
   * this value. This field is only used by a DS.
   */
  private int degradedStatusThreshold = -1;

  /**
   * The weight affected to the replication server.
   */
  private final int weight;

  /**
   * Number of currently connected DS to the replication server.
   */
  private final int connectedDSNumber;

  /**
   * Create a ReplServerStartDSMsg.
   *
   * @param serverId replication server id
   * @param serverURL replication server URL
   * @param baseDN base DN for which the ReplServerStartDSMsg is created.
   * @param windowSize The window size.
   * @param serverState our ServerState for this baseDN.
   * @param generationId The generationId for this server.
   * @param sslEncryption Whether to continue using SSL to encrypt messages
   *                      after the start messages have been exchanged.
   * @param groupId The group id of the RS
   * @param degradedStatusThreshold The degraded status threshold
   * @param weight The weight affected to the replication server.
   * @param connectedDSNumber Number of currently connected DS to the
   *        replication server.
   */
  public ReplServerStartDSMsg(int serverId, String serverURL, DN baseDN,
                               int windowSize,
                               ServerState serverState,
                               long generationId,
                               boolean sslEncryption,
                               byte groupId,
                               int degradedStatusThreshold,
                               int weight,
                               int connectedDSNumber)
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
    this.weight = weight;
    this.connectedDSNumber = connectedDSNumber;
  }

  /**
   * Creates a new ReplServerStartDSMsg by decoding the provided byte array.
   * @param in A byte array containing the encoded information for the
   *             ReplServerStartDSMsg
   * @throws DataFormatException If the in does not contain a properly
   *                             encoded ReplServerStartDSMsg.
   */
  ReplServerStartDSMsg(byte[] in) throws DataFormatException
  {
    final ByteArrayScanner scanner = new ByteArrayScanner(in);
    decodeHeader(scanner, MSG_TYPE_REPL_SERVER_START_DS);

    /* The ReplServerStartDSMsg payload is stored in the form :
     * <baseDN><serverId><serverURL><windowSize><sslEncryption>
     * <degradedStatusThreshold><weight><connectedDSNumber>
     * <serverState>
     */
    baseDN = scanner.nextDN();
    serverId = scanner.nextIntUTF8();
    serverURL = scanner.nextString();
    windowSize = scanner.nextIntUTF8();
    sslEncryption = Boolean.valueOf(scanner.nextString());//FIXME
    degradedStatusThreshold =scanner.nextIntUTF8();
    weight = scanner.nextIntUTF8();
    connectedDSNumber = scanner.nextIntUTF8();
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
   * Get the base DN from this ReplServerStartDSMsg.
   *
   * @return the base DN from this ReplServerStartDSMsg.
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
    /* The ReplServerStartDSMsg is stored in the form :
     * <operation type><baseDN><serverId><serverURL><windowSize><sslEncryption>
     * <degradedStatusThreshold><weight><connectedDSNumber>
     * <serverState>
     */
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    encodeHeader(MSG_TYPE_REPL_SERVER_START_DS, builder, protocolVersion);
    builder.appendDN(baseDN);
    builder.appendIntUTF8(serverId);
    builder.appendString(serverURL);
    builder.appendIntUTF8(windowSize);
    builder.appendString(Boolean.toString(sslEncryption));
    builder.appendIntUTF8(degradedStatusThreshold);
    builder.appendIntUTF8(weight);
    builder.appendIntUTF8(connectedDSNumber);
    builder.appendServerStateMustComeLast(serverState);
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
    return "ReplServerStartDSMsg content: " +
      "\nprotocolVersion: " + protocolVersion +
      "\ngenerationId: " + generationId +
      "\nbaseDN: " + baseDN +
      "\ngroupId: " + groupId +
      "\nserverId: " + serverId +
      "\nserverState: " + serverState +
      "\nserverURL: " + serverURL +
      "\nsslEncryption: " + sslEncryption +
      "\ndegradedStatusThreshold: " + degradedStatusThreshold +
      "\nwindowSize: " + windowSize +
      "\nweight: " + weight +
      "\nconnectedDSNumber: " + connectedDSNumber;
  }

  /**
   * Gets the weight of the replication server.
   * @return The weight of the replication server.
   */
  public int getWeight()
  {
    return weight;
  }

  /**
   * Gets the number of directory servers connected to the replication server.
   * @return The number of directory servers connected to the replication
   * server.
   */
  public int getConnectedDSNumber()
  {
    return connectedDSNumber;
  }

}
