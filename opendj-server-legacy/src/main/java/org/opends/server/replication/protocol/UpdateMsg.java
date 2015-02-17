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

import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.CSN;

import static org.opends.server.replication.protocol.ByteArrayBuilder.*;

/**
 * Abstract class that must be extended to define a message
 * used for sending Updates between servers.
 */
public class UpdateMsg extends ReplicationMsg
                                    implements Comparable<UpdateMsg>
{
  /** Protocol version. */
  protected short protocolVersion;

  /** The CSN of this update. */
  protected CSN csn;

  /** True when the update must use assured replication. */
  protected boolean assuredFlag;

  /** When assuredFlag is true, defines the requested assured mode. */
  protected AssuredMode assuredMode = AssuredMode.SAFE_DATA_MODE;

  /** When assured mode is safe data, gives the requested level. */
  protected byte safeDataLevel = 1;

  /** The payload that must be encoded in this message. */
  private final byte[] payload;

  /**
   * Creates a new empty UpdateMsg.
   */
  protected UpdateMsg()
  {
    payload = null;
  }

  /**
   * Creates a new UpdateMsg with the given information.
   *
   * @param bytes A Byte Array with the encoded form of the message.
   *
   * @throws DataFormatException If bytes is not valid.
   */
  UpdateMsg(byte[] bytes) throws DataFormatException
  {
    final ByteArrayScanner scanner = new ByteArrayScanner(bytes);
    decodeHeader(MSG_TYPE_GENERIC_UPDATE, scanner);
    // Read the payload : all the remaining bytes but the terminating 0
    payload = scanner.remainingBytes();
  }

  /**
   * Creates a new UpdateMsg with the given informations.
   * <p>
   * This constructor is only used for testing.
   *
   * @param csn  The CSN associated with the change encoded in this message.
   * @param payload       The payload that must be encoded in this message.
   */
  public UpdateMsg(CSN csn, byte[] payload)
  {
    this.payload = payload;
    this.protocolVersion = ProtocolVersion.getCurrentVersion();
    this.csn = csn;
  }

  /**
   * Get the CSN from the message.
   * @return the CSN
   */
  public CSN getCSN()
  {
    return csn;
  }

  /**
   * Get a boolean indicating if the Update must be processed as an
   * Asynchronous or as an assured replication.
   *
   * @return Returns the assuredFlag.
   */
  public boolean isAssured()
  {
    return assuredFlag;
  }

  /**
   * Set the Update message as an assured message.
   *
   * @param assured If the message is assured or not. Using true implies
   * setAssuredMode method must be called.
   */
  public void setAssured(boolean assured)
  {
    assuredFlag = assured;
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj)
  {
    return obj != null
        && obj.getClass() == getClass()
        && csn.equals(((UpdateMsg) obj).csn);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode()
  {
    return csn.hashCode();
  }

  /** {@inheritDoc} */
  @Override
  public int compareTo(UpdateMsg msg)
  {
    return csn.compareTo(msg.getCSN());
  }

  /**
   * Get the assured mode in this message.
   * @return The assured mode in this message
   */
  public AssuredMode getAssuredMode()
  {
    return assuredMode;
  }

  /**
   * Get the safe data level in this message.
   * @return The safe data level in this message
   */
  public byte getSafeDataLevel()
  {
    return safeDataLevel;
  }

  /**
   * Set the assured mode. Assured boolean must be set to true for this field
   * to mean something.
   * @param assuredMode The chosen assured mode.
   */
  public void setAssuredMode(AssuredMode assuredMode)
  {
    this.assuredMode = assuredMode;
  }

  /**
   * Set the safe data level. Assured mode should be set to safe data for this
   * field to mean something.
   * @param safeDataLevel The chosen safe data level.
   */
  public void setSafeDataLevel(byte safeDataLevel)
  {
    this.safeDataLevel = safeDataLevel;
  }

  /**
   * Get the version included in the update message. Means the replication
   * protocol version with which this update message was instantiated.
   *
   * @return The version with which this update message was instantiated.
   */
  public short getVersion()
  {
    return protocolVersion;
  }

  /**
   * Return the number of bytes used by this message.
   *
   * @return The number of bytes used by this message.
   */
  public int size()
  {
    return 10 + payload.length;
  }

  /**
   * Encode the common header for all the UpdateMsg. This uses the current
   * protocol version.
   *
   * @param msgType The type of UpdateMsg to encode.
   * @param protocolVersion The ProtocolVersion to use when encoding.
   * @return a byte array builder containing the common header
   */
  protected ByteArrayBuilder encodeHeader(byte msgType, short protocolVersion)
  {
    final ByteArrayBuilder builder =
        new ByteArrayBuilder(bytes(6) + csnsUTF8(1));
    builder.appendByte(msgType);
    builder.appendByte((byte) ProtocolVersion.getCurrentVersion());
    builder.appendCSNUTF8(getCSN());
    builder.appendBoolean(assuredFlag);
    builder.appendByte(assuredMode.getValue());
    builder.appendByte(safeDataLevel);
    return builder;
  }

  /**
   * Decode the Header part of this Update message, and check its type.
   *
   * @param allowedType The allowed type of this Update Message.
   * @param scanner The encoded form of the UpdateMsg.
   * @throws DataFormatException
   *           if the scanner does not contain a valid common header.
   */
  protected void decodeHeader(byte allowedType, ByteArrayScanner scanner)
      throws DataFormatException
  {
    /* The message header is stored in the form :
     * <operation type><protocol version><CSN><assured>
     * <assured mode> <safe data level>
     */
    final byte msgType = scanner.nextByte();
    if (allowedType != msgType)
    {
      throw new DataFormatException("byte[] is not a valid update msg: "
          + msgType);
    }

    protocolVersion = scanner.nextByte();
    csn = scanner.nextCSNUTF8();
    assuredFlag = scanner.nextBoolean();
    assuredMode = AssuredMode.valueOf(scanner.nextByte());
    safeDataLevel = scanner.nextByte();
  }

  /**
   * Returns the encoded representation of this update message using the current
   * protocol version.
   *
   * @return The encoded representation of this update message.
   */
  public byte[] getBytes()
  {
    return getBytes(ProtocolVersion.getCurrentVersion());
  }

  /**
   * This implementation is only called during unit testing, so we are free to
   * force the protocol version. Underlying implementations override this method
   * in order to provide version specific encodings.
   *
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    final ByteArrayBuilder builder = encodeHeader(MSG_TYPE_GENERIC_UPDATE,
        ProtocolVersion.getCurrentVersion());
    builder.appendByteArray(payload);
    return builder.toByteArray();
  }

  /**
   * Get the payload of the UpdateMsg.
   *
   * @return The payload of the UpdateMsg.
   */
  public byte[] getPayload()
  {
    return payload;
  }

  /**
   * Whether the current message can update the "ds-sync-state" attribute.
   *
   * @return true if current message can update the "ds-sync-state" attribute, false otherwise.
   */
  public boolean contributesToDomainState()
  {
    return true;
  }
}
