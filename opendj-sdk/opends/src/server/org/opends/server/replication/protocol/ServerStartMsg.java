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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

import org.opends.server.replication.common.ServerState;

/**
 * This message is used by LDAP server when they first connect.
 * to a replication server to let them know who they are and what is their state
 * (their RUV)
 */
public class ServerStartMsg extends StartMsg
{
  private int serverId; // Id of the LDAP server that sent this message
  private String serverURL;
  private String baseDn;
  private int maxReceiveQueue;
  private int maxSendQueue;
  private int maxReceiveDelay;
  private int maxSendDelay;
  private int windowSize;
  private ServerState serverState = null;

  /**
   * The time in milliseconds between heartbeats from the replication
   * server.  Zero means heartbeats are off.
   */
  private long heartbeatInterval = 0;

  /**
   * Whether to continue using SSL to encrypt messages after the start
   * messages have been exchanged.
   */
  private boolean sslEncryption;

  /**
   * Creates a new ServerStartMsg. This message is to be sent by an LDAP
   * Server after being connected to a replication server for a given
   * replication domain.
   *
   * @param serverId2 The serverId of the server for which the ServerStartMsg
   *                 is created.
   * @param serverURL directory server URL
   * @param baseDn   The base DN.
   * @param windowSize   The window size used by this server.
   * @param heartbeatInterval The requested heartbeat interval.
   * @param serverState  The state of this server.
   * @param generationId The generationId for this server.
   * @param sslEncryption Whether to continue using SSL to encrypt messages
   *                      after the start messages have been exchanged.
   * @param groupId The group id of the DS for this DN
   */
  public ServerStartMsg(int serverId2, String serverURL, String baseDn,
      int windowSize, long heartbeatInterval, ServerState serverState,
      long generationId, boolean sslEncryption,
      byte groupId)
  {
    super((short) -1 /* version set when sending */, generationId);

    this.serverId = serverId2;
    this.serverURL = serverURL;
    this.baseDn = baseDn;
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
  public ServerStartMsg(byte[] in) throws DataFormatException
  {
    byte[] allowedPduTypes = new byte[1];
    allowedPduTypes[0] = MSG_TYPE_SERVER_START;
    headerLength = decodeHeader(allowedPduTypes, in);

    try
    {
      /* first bytes are the header */
      int pos = headerLength;

      /*
       * read the dn
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
       * read the maxReceiveDelay
       */
      length = getNextLength(in, pos);
      maxReceiveDelay = Integer.valueOf(new String(in, pos, length, "UTF-8"));
      pos += length +1;

      /*
       * read the maxReceiveQueue
       */
      length = getNextLength(in, pos);
      maxReceiveQueue = Integer.valueOf(new String(in, pos, length, "UTF-8"));
      pos += length +1;

      /*
       * read the maxSendDelay
       */
      length = getNextLength(in, pos);
      maxSendDelay = Integer.valueOf(new String(in, pos, length, "UTF-8"));
      pos += length +1;

      /*
       * read the maxSendQueue
       */
      length = getNextLength(in, pos);
      maxSendQueue = Integer.valueOf(new String(in, pos, length, "UTF-8"));
      pos += length +1;

      /*
       * read the windowSize
       */
      length = getNextLength(in, pos);
      windowSize = Integer.valueOf(new String(in, pos, length, "UTF-8"));
      pos += length +1;

      /*
       * read the heartbeatInterval
       */
      length = getNextLength(in, pos);
      heartbeatInterval = Integer.valueOf(new String(in, pos, length, "UTF-8"));
      pos += length +1;

      /*
       * read the sslEncryption setting
       */
      length = getNextLength(in, pos);
      sslEncryption = Boolean.valueOf(new String(in, pos, length, "UTF-8"));
      pos += length +1;

      // Read the ServerState
      // Caution: ServerState MUST be the last field. Because ServerState can
      // contain null character (string termination of sererid string ..) it
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
   * Get the ServerID from the message.
   * @return the server ID
   */
  public int getServerId()
  {
    return serverId;
  }

  /**
   * get the Server URL from the message.
   * @return the server URL
   */
  public String getServerURL()
  {
    return serverURL;
  }

  /**
   * Get the baseDn.
   * @return Returns the baseDn.
   */
  public String getBaseDn()
  {
    return baseDn;
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

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes(short sessionProtocolVersion)
  {
    try {
      byte[] byteDn = baseDn.getBytes("UTF-8");
      byte[] byteServerId = String.valueOf(serverId).getBytes("UTF-8");
      byte[] byteServerUrl = serverURL.getBytes("UTF-8");
      byte[] byteMaxRecvDelay =
                     String.valueOf(maxReceiveDelay).getBytes("UTF-8");
      byte[] byteMaxRecvQueue =
                     String.valueOf(maxReceiveQueue).getBytes("UTF-8");
      byte[] byteMaxSendDelay =
                     String.valueOf(maxSendDelay).getBytes("UTF-8");
      byte[] byteMaxSendQueue =
                     String.valueOf(maxSendQueue).getBytes("UTF-8");
      byte[] byteWindowSize =
                     String.valueOf(windowSize).getBytes("UTF-8");
      byte[] byteHeartbeatInterval =
                     String.valueOf(heartbeatInterval).getBytes("UTF-8");
      byte[] byteSSLEncryption =
                     String.valueOf(sslEncryption).getBytes("UTF-8");
      byte[] byteServerState = serverState.getBytes();

      int length = byteDn.length + 1 + byteServerId.length + 1 +
                   byteServerUrl.length + 1 +
                   byteMaxRecvDelay.length + 1 +
                   byteMaxRecvQueue.length + 1 +
                   byteMaxSendDelay.length + 1 +
                   byteMaxSendQueue.length + 1 +
                   byteWindowSize.length + 1 +
                   byteHeartbeatInterval.length + 1 +
                   byteSSLEncryption.length + 1 +
                   byteServerState.length + 1;

      /* encode the header in a byte[] large enough to also contain the mods */
      byte resultByteArray[] = encodeHeader(MSG_TYPE_SERVER_START, length,
          sessionProtocolVersion);
      int pos = headerLength;

      pos = addByteArray(byteDn, resultByteArray, pos);

      pos = addByteArray(byteServerId, resultByteArray, pos);

      pos = addByteArray(byteServerUrl, resultByteArray, pos);

      pos = addByteArray(byteMaxRecvDelay, resultByteArray, pos);

      pos = addByteArray(byteMaxRecvQueue, resultByteArray, pos);

      pos = addByteArray(byteMaxSendDelay, resultByteArray, pos);

      pos = addByteArray(byteMaxSendQueue, resultByteArray, pos);

      pos = addByteArray(byteWindowSize, resultByteArray, pos);

      pos = addByteArray(byteHeartbeatInterval, resultByteArray, pos);

      pos = addByteArray(byteSSLEncryption, resultByteArray, pos);

      pos = addByteArray(byteServerState, resultByteArray, pos);

      return resultByteArray;
    }
    catch (UnsupportedEncodingException e)
    {
      return null;
    }
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

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return "ServerStartMsg content: " +
      "\nprotocolVersion: " + protocolVersion +
      "\ngenerationId: " + generationId +
      "\ngroupId: " + groupId +
      "\nbaseDn: " + baseDn +
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
