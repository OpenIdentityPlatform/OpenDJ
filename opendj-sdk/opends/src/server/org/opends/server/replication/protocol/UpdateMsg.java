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

import java.util.zip.DataFormatException;

import java.io.UnsupportedEncodingException;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.ChangeNumber;

/**
 * Abstract class that must be extended to define a message
 * used for sending Updates between servers.
 */
public class UpdateMsg extends ReplicationMsg
                                    implements Comparable<UpdateMsg>
{
  /**
   * Protocol version.
   */
  protected short protocolVersion;

  /**
   * The ChangeNumber of this update.
   */
  protected ChangeNumber changeNumber;

  /**
   * True when the update must use assured replication.
   */
  protected boolean assuredFlag = false;

  /**
   * When assuredFlag is true, defines the requested assured mode.
   */
  protected AssuredMode assuredMode = AssuredMode.SAFE_DATA_MODE;

  /**
   * When assured mode is safe data, gives the requested level.
   */
  protected byte safeDataLevel = (byte)1;

  /**
   * The payload that must be encoded in this message.
   */
  private byte[] payload;


  /**
   * Creates a new empty UpdateMsg.
   */
  protected UpdateMsg()
  {}

  /**
   * Creates a new UpdateMsg with the given informations.
   *
   * @param bytes A Byte Array with the encoded form of the message.
   *
   * @throws DataFormatException If bytes is not valid.
   */
  UpdateMsg(byte[] bytes) throws DataFormatException
  {
    // Decode header
    int pos = decodeHeader(MSG_TYPE_GENERIC_UPDATE, bytes);

    /* Read the payload : all the remaining bytes but the terminating 0 */
    int length = bytes.length - pos;
    payload = new byte[length];
    try
    {
      System.arraycopy(bytes, pos, payload, 0, length);
    } catch (IndexOutOfBoundsException e)
    {
      throw new DataFormatException(e.getMessage());
    } catch (ArrayStoreException e)
    {
      throw new DataFormatException(e.getMessage());
    } catch (NullPointerException e)
    {
      throw new DataFormatException(e.getMessage());
    }
  }

  /**
   * Creates a new UpdateMsg with the given informations.
   *
   * @param changeNumber  The ChangeNumber associated with the change
   *                      encoded in this message.
   * @param payload       The payload that must be encoded in this message.
   */
  public UpdateMsg(ChangeNumber changeNumber, byte[] payload)
  {
    this.payload = payload;
    this.protocolVersion = ProtocolVersion.getCurrentVersion();
    this.changeNumber = changeNumber;
  }

  /**
   * Get the ChangeNumber from the message.
   * @return the ChangeNumber
   */
  public ChangeNumber getChangeNumber()
  {
    return changeNumber;
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

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object obj)
  {
    if (obj != null)
    {
      if (obj.getClass() != this.getClass())
        return false;
      return changeNumber.equals(((UpdateMsg) obj).changeNumber);
    }
    else
    {
      return false;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode()
  {
    return changeNumber.hashCode();
  }

  /**
   * {@inheritDoc}
   */
  public int compareTo(UpdateMsg msg)
  {
    return changeNumber.compareTo(msg.getChangeNumber());
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes(short reqProtocolVersion)
    throws UnsupportedEncodingException
  {
    // Of course, always support current protocol version
    if (reqProtocolVersion == ProtocolVersion.getCurrentVersion())
    {
      return getBytes();
    }
    else
    {
      throw new UnsupportedEncodingException(getClass().getSimpleName() +
          " PDU does not support requested protocol version serialization: " +
          reqProtocolVersion);
    }
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
   * @param type the type of UpdateMsg to encode.
   * @param additionalLength additional length needed to encode the remaining
   *                         part of the UpdateMsg.
   * @return a byte array containing the common header and enough space to
   *         encode the remaining bytes of the UpdateMsg as was specified
   *         by the additionalLength.
   *         (byte array length = common header length + additionalLength)
   * @throws UnsupportedEncodingException if UTF-8 is not supported.
   */
  protected byte[] encodeHeader(byte type, int additionalLength)
    throws UnsupportedEncodingException
  {
    byte[] changeNumberByte =
      this.getChangeNumber().toString().getBytes("UTF-8");

    /* The message header is stored in the form :
     * <operation type><protocol version><changenumber><assured>
     * <assured mode> <safe data level>
     * the length of result byte array is therefore :
     *   1 + 1 + change number length + 1 + 1
     *   + 1 + 1 + additional_length
     */
    int length = 6 + changeNumberByte.length + additionalLength;

    byte[] encodedMsg = new byte[length];

    /* put the type of the operation */
    encodedMsg[0] = type;

    /* put the protocol version */
    encodedMsg[1] = (byte)ProtocolVersion.getCurrentVersion();
    int pos = 2;

    /* Put the ChangeNumber */
    pos = addByteArray(changeNumberByte, encodedMsg, pos);

    /* Put the assured flag */
    encodedMsg[pos++] = (assuredFlag ? (byte) 1 : 0);

    /* Put the assured mode */
    encodedMsg[pos++] = assuredMode.getValue();

    /* Put the safe data level */
    encodedMsg[pos++] = safeDataLevel;

    return encodedMsg;
  }

  /**
   * Decode the Header part of this Update Message, and check its type.
   *
   * @param type The allowed type of this Update Message.
   * @param encodedMsg the encoded form of the UpdateMsg.
   * @return the position at which the remaining part of the message starts.
   * @throws DataFormatException if the encodedMsg does not contain a valid
   *         common header.
   */
  protected int decodeHeader(byte type, byte[] encodedMsg)
                          throws DataFormatException
  {
    /* The message header is stored in the form :
     * <operation type><protocol version><changenumber><assured>
     * <assured mode> <safe data level>
     */
    if (!(type == encodedMsg[0]))
      throw new DataFormatException("byte[] is not a valid update msg: "
        + encodedMsg[0]);

    /* read the protocol version */
    protocolVersion = (short)encodedMsg[1];

    try
    {
      /* Read the changeNumber */
      int pos = 2;
      int length = getNextLength(encodedMsg, pos);
      String changenumberStr = new String(encodedMsg, pos, length, "UTF-8");
      pos += length + 1;
      changeNumber = new ChangeNumber(changenumberStr);

      /* Read the assured information */
      if (encodedMsg[pos++] == 1)
        assuredFlag = true;
      else
        assuredFlag = false;

      /* Read the assured mode */
      assuredMode = AssuredMode.valueOf(encodedMsg[pos++]);

      /* Read the safe data level */
      safeDataLevel = encodedMsg[pos++];

      return pos;
    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    } catch (IllegalArgumentException e)
    {
      throw new DataFormatException(e.getMessage());
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes() throws UnsupportedEncodingException
  {
    /* Encode the header in a byte[] large enough to also contain the payload */
    byte [] resultByteArray =
      encodeHeader(MSG_TYPE_GENERIC_UPDATE, payload.length);

    int pos = resultByteArray.length - payload.length;

    /* Add the payload */
    for (int i=0; i<payload.length; i++,pos++)
    {
      resultByteArray[pos] = payload[i];
    }
    return resultByteArray;
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
}
