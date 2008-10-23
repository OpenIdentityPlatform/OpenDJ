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

import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;


/**
 * This abstract message class is the superclass for start messages used
 * by LDAP servers and Replication servers to initiate their communications.
 * This class specifies a message header that contains the Replication
 * Protocol version.
 */
public abstract class StartMsg extends ReplicationMsg
{
  /** Protocol version. */
  protected short protocolVersion;
  /** Generation id of data set we want to work with. */
  protected long  generationId;
  /** Group id of the replicated domain. */
  protected byte groupId = (byte)-1;

  /**
   * The length of the header of this message.
   */
  protected int headerLength;

  /**
   * Create a new StartMsg.
   */
  public StartMsg()
  {
  }

  /**
   * Create a new StartMsg.
   *
   * @param protocolVersion The Replication Protocol version of the server
   *                        for which the StartMsg is created.
   * @param generationId    The generationId for this server.
   *
   */
  public StartMsg(short protocolVersion, long generationId)
  {
    this.protocolVersion = protocolVersion;
    this.generationId = generationId;
  }

  /**
   * Encode the header for the start message.
   *
   * @param type The type of the message to create.
   * @param additionalLength additional length needed to encode the remaining
   *                         part of the UpdateMessage.
   * @return a byte array containing the common header and enough space to
   *         encode the remaining bytes of the UpdateMessage as was specified
   *         by the additionalLength.
   *         (byte array length = common header length + additionalLength)
   * @throws UnsupportedEncodingException if UTF-8 is not supported.
   */
  public byte[] encodeHeader(byte type, int additionalLength)
  throws UnsupportedEncodingException
  {
    byte[] byteGenerationID =
      String.valueOf(generationId).getBytes("UTF-8");

    /* The message header is stored in the form :
     * <message type><protocol version><generation id><group id>
     */
    int length = 1 + 1 + byteGenerationID.length + 1 + 1 +
                     additionalLength;

    byte[] encodedMsg = new byte[length];

    /* put the type of the operation */
    encodedMsg[0] = type;

    /* put the protocol version */
    encodedMsg[1] = (byte)ProtocolVersion.getCurrentVersion();

    /* put the generationId */
    int pos = 2;
    pos = addByteArray(byteGenerationID, encodedMsg, pos);

    /* put the group id */
    encodedMsg[pos] = groupId;

    pos++;
    headerLength = pos;

    return encodedMsg;
  }

  /**
   * Encode the header for the start message. This uses the version 1 of the
   * replication protocol (used for compatibility purpose).
   *
   * @param type The type of the message to create.
   * @param additionalLength additional length needed to encode the remaining
   *                         part of the UpdateMessage.
   * @return a byte array containing the common header and enough space to
   *         encode the remaining bytes of the UpdateMessage as was specified
   *         by the additionalLength.
   *         (byte array length = common header length + additionalLength)
   * @throws UnsupportedEncodingException if UTF-8 is not supported.
   */
  public byte[] encodeHeader_V1(byte type, int additionalLength)
  throws UnsupportedEncodingException
  {
    byte[] byteGenerationID =
      String.valueOf(generationId).getBytes("UTF-8");

    /* The message header is stored in the form :
     * <message type><protocol version><generation id>
     */
    int length = 1 + 1 + 1 +
                     byteGenerationID.length + 1 +
                     additionalLength;

    byte[] encodedMsg = new byte[length];

    /* put the type of the operation */
    encodedMsg[0] = type;

    /* put the protocol version */
    encodedMsg[1] = (byte)ProtocolVersion.REPLICATION_PROTOCOL_V1_REAL;
    encodedMsg[2] = (byte)0;

    /* put the generationId */
    int pos = 3;
    headerLength = addByteArray(byteGenerationID, encodedMsg, pos);

    return encodedMsg;
  }

  /**
   * Decode the Header part of this message, and check its type.
   *
   * @param types The allowed types of this message.
   * @param encodedMsg the encoded form of the message.
   * @return the position at which the remaining part of the message starts.
   * @throws DataFormatException if the encodedMsg does not contain a valid
   *         common header.
   */
  public int decodeHeader(byte[] types, byte [] encodedMsg)
  throws DataFormatException
  {
    /* first byte is the type */
    boolean foundMatchingType = false;
    for (int i = 0; i < types.length; i++)
    {
      if (types[i] == encodedMsg[0])
      {
        foundMatchingType = true;
        break;
      }
    }
    if (!foundMatchingType)
      throw new DataFormatException("byte[] is not a valid start msg: " +
        encodedMsg[0]);

    // Filter for supported old versions PDUs
    if (encodedMsg[0] == MSG_TYPE_REPL_SERVER_START_V1)
      return decodeHeader_V1(MSG_TYPE_REPL_SERVER_START_V1, encodedMsg);

    try
    {
      /* then read the version */
      short readVersion = (short)encodedMsg[1];
      if (readVersion != ProtocolVersion.getCurrentVersion())
        throw new DataFormatException("Not a valid message: type is " +
          encodedMsg[0] + " but protocol version byte is " + readVersion +
          " instead of " + ProtocolVersion.getCurrentVersion());
      protocolVersion = ProtocolVersion.getCurrentVersion();

      /* read the generationId */
      int pos = 2;
      int length = getNextLength(encodedMsg, pos);
      generationId = Long.valueOf(new String(encodedMsg, pos, length,
          "UTF-8"));
      pos += length +1;

      /* read the group id */
      groupId = encodedMsg[pos];
      pos++;

      return pos;
    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * Decode the Header part of this message, and check its type. This uses the
   * version 1 of the replication protocol (used for compatibility purpose).
   *
   * @param type The type of this message.
   * @param encodedMsg the encoded form of the message.
   * @return the position at which the remaining part of the message starts.
   * @throws DataFormatException if the encodedMsg does not contain a valid
   *         common header.
   */
  public int decodeHeader_V1(byte type, byte [] encodedMsg)
  throws DataFormatException
  {
    if (encodedMsg[0] != type)
      throw new DataFormatException("byte[] is not a valid start msg: expected "
        + " a V1 PDU, received: " + encodedMsg[0]);

    if (encodedMsg[1] != ProtocolVersion.REPLICATION_PROTOCOL_V1_REAL)
    {
      throw new DataFormatException("Not a valid message: type is " +
        type + " but protocol version byte is " + encodedMsg[1] + " instead of "
        + ProtocolVersion.REPLICATION_PROTOCOL_V1_REAL);
    }

    // Force version to V1
    // We need to translate the MSG_TYPE_REPL_SERVER_START_V1 version
    // into REPLICATION_PROTOCOL_V1 so that we only see V1 everywhere.
    protocolVersion = ProtocolVersion.REPLICATION_PROTOCOL_V1;

    try
    {
      // In V1, version was 1 (49) in string, so with a null
      // terminating string. Let's position the cursor at the next byte
      int pos = 3;

      /* read the generationId */
      int length = getNextLength(encodedMsg, pos);
      generationId = Long.valueOf(new String(encodedMsg, pos, length,
          "UTF-8"));
      pos += length +1;

      return pos;
    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * Get the version included in the Start Message mean the replication
   * protocol version used by the server that created the message.
   *
   * @return The version used by the server that created the message.
   */
  public short getVersion()
  {
    return protocolVersion;
  }

  /**
   * Get the generationId from this message.
   * @return The generationId.
   */
  public long getGenerationId()
  {
    return generationId;
  }

  /**
   * Get the group id in this message.
   * @return The group id in this message
   */
  public byte getGroupId()
  {
    return groupId;
  }

  /**
   * Set the group id in this message (For test purpose).
   * @param groupId The group id to set.
   */
  public void setGroupId(byte groupId)
  {
    this.groupId = groupId;
  }
}
