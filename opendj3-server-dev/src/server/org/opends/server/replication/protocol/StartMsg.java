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
 *      Portions Copyright 2013-2014 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

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
  protected byte groupId = -1;

  /**
   * Create a new StartMsg.
   */
  protected StartMsg()
  {
    // Nothing to do.
  }

  /**
   * Create a new StartMsg.
   *
   * @param protocolVersion The Replication Protocol version of the server
   *                        for which the StartMsg is created.
   * @param generationId    The generationId for this server.
   *
   */
  StartMsg(short protocolVersion, long generationId)
  {
    this.protocolVersion = protocolVersion;
    this.generationId = generationId;
  }

  /**
   * Encode the header for the start message.
   *
   * @param msgType The type of the message to create.
   * @param builder Additional length needed to encode the remaining
   *                         part of the UpdateMessage.
   * @param protocolVersion  The version to use when encoding the header.
   */
  void encodeHeader(byte msgType, ByteArrayBuilder builder, short protocolVersion)
  {
    /* The message header is stored in the form :
     * <message type><protocol version><generation id><group id>
     */
    builder.appendByte(msgType);
    builder.appendByte((byte) protocolVersion);
    builder.appendLongUTF8(generationId);
    builder.appendByte(groupId);
  }

  /**
   * Encode the header for the start message. This uses the version 1 of the
   * replication protocol (used for compatibility purpose).
   *
   * @param msgType The type of the message to create.
   * @param builder The builder where to append the remaining part of the
   *                UpdateMessage.
   */
  void encodeHeader_V1(byte msgType, ByteArrayBuilder builder)
  {
    /* The message header is stored in the form :
     * <message type><protocol version><generation id>
     */
    builder.appendByte(msgType);
    builder.appendByte((byte) ProtocolVersion.REPLICATION_PROTOCOL_V1_REAL);
    builder.appendByte((byte) 0);
    builder.appendLongUTF8(generationId);
  }

  /**
   * Decode the Header part of this message, and check its type.
   *
   * @param scanner where to read the message from.
   * @param allowedTypes The allowed types of this message.
   * @throws DataFormatException if the encodedMsg does not contain a valid
   *         common header.
   */
  void decodeHeader(final ByteArrayScanner scanner, byte... allowedTypes)
      throws DataFormatException
  {
    final byte msgType = scanner.nextByte();
    if (!isTypeAllowed(allowedTypes, msgType))
    {
      throw new DataFormatException("byte[] is not a valid start msg: "
          + msgType);
    }

    final byte version = scanner.nextByte();

    // Filter for supported old versions PDUs
    if (msgType == MSG_TYPE_REPL_SERVER_START_V1)
    {
      if (version != ProtocolVersion.REPLICATION_PROTOCOL_V1_REAL)
      {
        throw new DataFormatException("Not a valid message: type is " + msgType
            + " but protocol version byte is " + version + " instead of "
            + ProtocolVersion.REPLICATION_PROTOCOL_V1_REAL);
      }

      // Force version to V1
      // We need to translate the MSG_TYPE_REPL_SERVER_START_V1 version
      // into REPLICATION_PROTOCOL_V1 so that we only see V1 everywhere.
      protocolVersion = ProtocolVersion.REPLICATION_PROTOCOL_V1;

      // In V1, version was 1 (49) in string, so with a null
      // terminating string. Let's position the cursor at the next byte
      scanner.skipZeroSeparator();
      generationId = scanner.nextLongUTF8();
    }
    else
    {
      if (version < ProtocolVersion.REPLICATION_PROTOCOL_V2)
      {
        throw new DataFormatException("Not a valid message: type is " + msgType
            + " but protocol version byte is " + version + " instead of "
            + ProtocolVersion.getCurrentVersion());
      }
      protocolVersion = version;
      generationId = scanner.nextLongUTF8();
      groupId = scanner.nextByte();
    }
  }

  private boolean isTypeAllowed(byte[] allowedTypes, final byte msgType)
  {
    for (byte allowedType : allowedTypes)
    {
      if (msgType == allowedType)
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Get the version included in the Start message mean the replication
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
