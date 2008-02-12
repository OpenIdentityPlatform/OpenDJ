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
public abstract class StartMessage extends ReplicationMessage
{
  private short protocolVersion;
  private long  generationId;

  /**
   * The length of the header of this message.
   */
  protected int headerLength;

  /**
   * Create a new StartMessage.
   *
   * @param protocolVersion The Replication Protocol version of the server
   *                        for which the StartMessage is created.
   * @param generationId    The generationId for this server.
   *
   */
  public StartMessage(short protocolVersion, long generationId)
  {
    this.protocolVersion = protocolVersion;
    this.generationId = generationId;
  }

  /**
   * Creates a new ServerStartMessage from its encoded form.
   *
   * @param type The type of the message to create.
   * @param encodedMsg The byte array containing the encoded form of the
   *           StartMessage.
   * @throws DataFormatException If the byte array does not contain a valid
   *                             encoded form of the ServerStartMessage.
   */
  public StartMessage(byte type, byte [] encodedMsg) throws DataFormatException
  {
    headerLength = decodeHeader(type, encodedMsg);
  }

  /**
   * Encode the header for the start message.
   *
   * @param type The type of the message to create.
   * @param additionalLength additional length needed to encode the remaining
   *                         part of the UpdateMessage.
   * @return a byte array containing the common header and enough space to
   *         encode the reamining bytes of the UpdateMessage as was specified
   *         by the additionalLength.
   *         (byte array length = common header length + additionalLength)
   * @throws UnsupportedEncodingException if UTF-8 is not supported.
   */
  public byte[] encodeHeader(byte type, int additionalLength)
  throws UnsupportedEncodingException
  {
    byte[] versionByte = Short.toString(protocolVersion).getBytes("UTF-8");
    byte[] byteGenerationID =
      String.valueOf(generationId).getBytes("UTF-8");

    /* The message header is stored in the form :
     * <message type><protocol version>
     */
    int length = 1 + versionByte.length + 1 +
                     byteGenerationID.length + 1 +
                     additionalLength;

    byte[] encodedMsg = new byte[length];

    /* put the type of the operation */
    encodedMsg[0] = type;
    int pos = 1;

    /* put the protocol version */
    pos = addByteArray(versionByte, encodedMsg, pos);

    /* put the generationId */
    headerLength = addByteArray(byteGenerationID, encodedMsg, pos);

    return encodedMsg;
  }

  /**
   * Decode the Header part of this message, and check its type.
   *
   * @param type The type of this message.
   * @param encodedMsg the encoded form of the message.
   * @return the position at which the remaining part of the message starts.
   * @throws DataFormatException if the encodedMsg does not contain a valid
   *         common header.
   */
  public int decodeHeader(byte type, byte [] encodedMsg)
  throws DataFormatException
  {
    /* first byte is the type */
    if (encodedMsg[0] != type)
      throw new DataFormatException("byte[] is not a valid msg");

    try
    {
      /* then read the version */
      int pos = 1;
      int length = getNextLength(encodedMsg, pos);
      protocolVersion = Short.valueOf(
          new String(encodedMsg, pos, length, "UTF-8"));
      pos += length + 1;

      /* read the generationId */
      length = getNextLength(encodedMsg, pos);
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

}
