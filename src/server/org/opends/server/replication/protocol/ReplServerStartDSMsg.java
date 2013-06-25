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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

import org.opends.server.replication.common.ServerState;

/**
 * Message sent by a replication server to a directory server in reply to the
 * ServerStartMsg.
 */
public class ReplServerStartDSMsg extends StartMsg
{
  private int serverId;
  private String serverURL;
  private String baseDn = null;
  private int windowSize;
  private ServerState serverState;

  /**
   * Whether to continue using SSL to encrypt messages after the start
   * messages have been exchanged.
   */
  private boolean sslEncryption;

  /**
   * Threshold value used by the RS to determine if a DS must be put in
   * degraded status because the number of pending changes for him has crossed
   * this value. This field is only used by a DS.
   */
  private int degradedStatusThreshold = -1;

  /**
   * The weight affected to the replication server.
   */
  private int weight = -1;

  /**
   * Number of currently connected DS to the replication server.
   */
  private int connectedDSNumber = -1;

  /**
   * Create a ReplServerStartDSMsg.
   *
   * @param serverId replication server id
   * @param serverURL replication server URL
   * @param baseDn base DN for which the ReplServerStartDSMsg is created.
   * @param windowSize The window size.
   * @param serverState our ServerState for this baseDn.
   * @param generationId The generationId for this server.
   * @param sslEncryption Whether to continue using SSL to encrypt messages
   *                      after the start messages have been exchanged.
   * @param groupId The group id of the RS
   * @param degradedStatusThreshold The degraded status threshold
   * @param weight The weight affected to the replication server.
   * @param connectedDSNumber Number of currently connected DS to the
   *        replication server.
   */
  public ReplServerStartDSMsg(int serverId, String serverURL, String baseDn,
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
    if (baseDn != null)
      this.baseDn = baseDn;
    else
      this.baseDn = null;
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
  public ReplServerStartDSMsg(byte[] in) throws DataFormatException
  {
    byte[] allowedPduTypes = new byte[1];
    allowedPduTypes[0] = MSG_TYPE_REPL_SERVER_START_DS;
    headerLength = decodeHeader(allowedPduTypes, in);

    try
    {
      /* The ReplServerStartDSMsg payload is stored in the form :
       * <baseDn><serverId><serverURL><windowSize><sslEncryption>
       * <degradedStatusThreshold><weight><connectedDSNumber>
       * <serverState>
       */

      /* first bytes are the header */
      int pos = headerLength;

      /* read the dn
       * first calculate the length then construct the string
       */
      int length = getNextLength(in, pos);
      baseDn = new String(in, pos, length, "UTF-8");
      pos += length +1;

      /*
       * read the ServerId
       */
      length = getNextLength(in, pos);
      String serverIdString = new String(in, pos, length, "UTF-8");
      serverId = Integer.valueOf(serverIdString);
      pos += length +1;

      /*
       * read the ServerURL
       */
      length = getNextLength(in, pos);
      serverURL = new String(in, pos, length, "UTF-8");
      pos += length +1;

      /*
       * read the window size
       */
      length = getNextLength(in, pos);
      windowSize = Integer.valueOf(new String(in, pos, length, "UTF-8"));
      pos += length +1;

      /*
       * read the sslEncryption setting
       */
      length = getNextLength(in, pos);
      sslEncryption = Boolean.valueOf(new String(in, pos, length, "UTF-8"));
      pos += length +1;

      /**
       * read the degraded status threshold
       */
      length = getNextLength(in, pos);
      degradedStatusThreshold =
        Integer.valueOf(new String(in, pos, length, "UTF-8"));
      pos += length + 1;

      /*
       * read the weight
       */
      length = getNextLength(in, pos);
      String weightString = new String(in, pos, length, "UTF-8");
      weight = Integer.valueOf(weightString);
      pos += length +1;

      /*
       * read the connected DS number
       */
      length = getNextLength(in, pos);
      String connectedDSNumberString = new String(in, pos, length, "UTF-8");
      connectedDSNumber = Integer.valueOf(connectedDSNumberString);
      pos += length +1;

      // Read the ServerState
      // Caution: ServerState MUST be the last field. Because ServerState can
      // contain null character (string termination of serverid string ..) it
      // cannot be decoded using getNextLength() like the other fields. The
      // only way is to rely on the end of the input buffer : and that forces
      // the ServerState to be the last. This should be changed and we want to
      // have more than one ServerState field.
      serverState = new ServerState(in, pos, in.length - 1);
    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
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
  public String getBaseDn()
  {
    return baseDn;
  }

  /**
   * Get the serverState.
   * @return Returns the serverState.
   */
  public ServerState getServerState()
  {
    return this.serverState;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes(short sessionProtocolVersion)
     throws UnsupportedEncodingException
  {
    /* The ReplServerStartDSMsg is stored in the form :
     * <operation type><baseDn><serverId><serverURL><windowSize><sslEncryption>
     * <degradedStatusThreshold><weight><connectedDSNumber>
     * <serverState>
     */
    byte[] byteDn = baseDn.getBytes("UTF-8");
    byte[] byteServerId = String.valueOf(serverId).getBytes("UTF-8");
    byte[] byteServerUrl = serverURL.getBytes("UTF-8");
    byte[] byteServerState = serverState.getBytes();
    byte[] byteWindowSize = String.valueOf(windowSize).getBytes("UTF-8");
    byte[] byteSSLEncryption =
      String.valueOf(sslEncryption).getBytes("UTF-8");
    byte[] byteDegradedStatusThreshold =
      String.valueOf(degradedStatusThreshold).getBytes("UTF-8");
    byte[] byteWeight =
      String.valueOf(weight).getBytes("UTF-8");
    byte[] byteConnectedDSNumber =
      String.valueOf(connectedDSNumber).getBytes("UTF-8");

    int length = byteDn.length + 1 + byteServerId.length + 1 +
      byteServerUrl.length + 1 + byteWindowSize.length + 1 +
      byteSSLEncryption.length + 1 + byteDegradedStatusThreshold.length + 1 +
      byteWeight.length + 1 + byteConnectedDSNumber.length + 1 +
      byteServerState.length + 1;

    /* encode the header in a byte[] large enough */
    byte resultByteArray[] = encodeHeader(MSG_TYPE_REPL_SERVER_START_DS,
        length, sessionProtocolVersion);

    int pos = headerLength;

    /* put the baseDN and a terminating 0 */
    pos = addByteArray(byteDn, resultByteArray, pos);

    /* put the ServerId */
    pos = addByteArray(byteServerId, resultByteArray, pos);

    /* put the ServerURL */
    pos = addByteArray(byteServerUrl, resultByteArray, pos);

    /* put the window size */
    pos = addByteArray(byteWindowSize, resultByteArray, pos);

    /* put the SSL Encryption setting */
    pos = addByteArray(byteSSLEncryption, resultByteArray, pos);

    /* put the degraded status threshold */
    pos = addByteArray(byteDegradedStatusThreshold, resultByteArray, pos);

    /* put the weight */
    pos = addByteArray(byteWeight, resultByteArray, pos);

    /* put the connected DS number */
    pos = addByteArray(byteConnectedDSNumber, resultByteArray, pos);

    /* put the ServerState */
    pos = addByteArray(byteServerState, resultByteArray, pos);

    return resultByteArray;
  }

  /**
   * get the window size for the server that created this message.
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

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return "ReplServerStartDSMsg content: " +
      "\nprotocolVersion: " + protocolVersion +
      "\ngenerationId: " + generationId +
      "\nbaseDn: " + baseDn +
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
