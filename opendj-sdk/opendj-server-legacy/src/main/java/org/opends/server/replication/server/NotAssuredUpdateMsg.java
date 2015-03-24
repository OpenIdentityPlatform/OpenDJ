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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.replication.server;

import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.*;

/**
 * This is a facility class that is in fact an hack to optimize replication
 * server performances in case of assured replication usage:
 * When received from a server by a server reader, an update message is to be
 * posted in the queues of the server writers. Thus, they receive the same
 * reference of update message. As we want to transform an assured update
 * message to an equivalent not assured one for some servers but not for all,
 * instead of performing a painful clone of the message, we use this special
 * class to keep a reference to the real object, but that will overwrite the
 * assured flag value to false when serializing the message, and return false
 * when calling the isAssured() method.
 */
class NotAssuredUpdateMsg extends UpdateMsg
{
  /** The real update message this message represents. */
  private final UpdateMsg realUpdateMsg;

  /**
   * V1 serialized form of the real message with assured flag set to false.
   * Ready to be sent.
   */
  private final byte[] realUpdateMsgNotAssuredBytesV1;

  /**
   * VLatest serialized form of the real message with assured flag set to false.
   * Ready to be sent.
   */
  private final byte[] realUpdateMsgNotAssuredBytesVLatest;

  /**
   * Creates a new empty UpdateMsg.
   * This class is only used by replication server code so constructor is not
   * public by security.
   *
   * @param updateMsg The real underlying update message this object represents.
   * @throws UnsupportedEncodingException  When the pre-encoding of the message
   *         failed because the UTF-8 encoding is not supported or the
   *         requested protocol version to use is not supported by this PDU.
   */
  NotAssuredUpdateMsg(UpdateMsg updateMsg) throws UnsupportedEncodingException
  {
    realUpdateMsg = updateMsg;

    /**
     * Prepare serialized forms
     */
    if (realUpdateMsg instanceof LDAPUpdateMsg)
    {
      /**
       * Prepare V1 serialized form of the message:
       * Get the encoding form of the real message then overwrite the assured
       * flag to always be false.
       */
      byte[] bytes = getRealUpdateMsgBytes(ProtocolVersion.REPLICATION_PROTOCOL_V1);

      /* Look for assured flag position:
       * The message header is stored in the form :
       * <operation type><CSN><dn><assured><entryuuid><change>
       * the length of result byte array is therefore :
       *   1 + CSN length + 1 + dn length + 1  + 1 +
       *   uuid length + 1 + additional_length
       * See LDAPUpdateMsg.encodeHeader_V1() for more information
       */
      // Find end of CSN then end of dn
      int pos = findNthZeroByte(bytes, 1, 2);
      // Force assured flag to false
      bytes[pos] = 0;

      // Store computed V1 serialized form
      realUpdateMsgNotAssuredBytesV1 = bytes;

      /**
       * Prepare VLATEST serialized form of the message:
       * Get the encoding form of the real message then overwrite the assured
       * flag to always be false.
       */
      bytes = getRealUpdateMsgBytes(ProtocolVersion.getCurrentVersion());

      /* Look for assured flag position:
       * The message header is stored in the form :
       * <operation type><protocol version><CSN><dn><entryuuid>
       * <assured> <assured mode> <safe data level>
       * the length of result byte array is therefore :
       *   1 + 1 + CSN length + 1 + dn length + 1 + uuid length +
       *   1 + 1 + 1 + 1 + additional_length
       * See LDAPUpdateMsg.encodeHeader() for more information
       */
      // Find end of CSN then end of dn then end of uuid
      pos = findNthZeroByte(bytes, 2, 3);
      // Force assured flag to false
      bytes[pos] = 0;

      // Store computed VLATEST serialized form
      realUpdateMsgNotAssuredBytesVLatest = bytes;
    }
    else
    {
      realUpdateMsgNotAssuredBytesV1 = null;
      /**
       * Prepare VLATEST serialized form of the message:
       * Get the encoding form of the real message then overwrite the assured
       * flag to always be false.
       */
      byte[] bytes = getRealUpdateMsgBytes(ProtocolVersion.getCurrentVersion());

      // This is a generic update message
      /* Look for assured flag position:
       * The message header is stored in the form :
       * <operation type><protocol version><CSN><assured>
       * <assured mode> <safe data level>
       * the length of result byte array is therefore :
       *   1 + 1 + CSN length + 1 + 1
       *   + 1 + 1 + additional_length
       * See UpdateMsg.encodeHeader() for more  information
       */
      // Find end of CSN
      int pos = findNthZeroByte(bytes, 2, 1);
      // Force assured flag to false
      bytes[pos] = 0;

      // Store computed VLatest serialized form
      realUpdateMsgNotAssuredBytesVLatest = bytes;
    }
  }

  /**
   * Clones the byte array to be able to modify it without problems
   * (ModifyMsg messages for instance do not return a cloned version of
   * their byte array).
   */
  private byte[] getRealUpdateMsgBytes(final short protocolVersion)
  {
    byte[] origBytes = realUpdateMsg.getBytes(protocolVersion);
    byte[] bytes = new byte[origBytes.length];
    System.arraycopy(origBytes, 0, bytes, 0, origBytes.length);
    return bytes;
  }

  private int findNthZeroByte(byte[] bytes, int startPos, int nbToFind)
      throws UnsupportedEncodingException
  {
    final int maxLen = bytes.length;
    int nbZeroFound = 0; // Number of 0 values found
    for (int pos = startPos; pos < maxLen; pos++)
    {
      if (bytes[pos] == (byte) 0)
      {
        nbZeroFound++;
        if (nbZeroFound == nbToFind)
        {
          // nb of end of strings reached
          pos++;
          if (pos >= maxLen)
          {
            throw new UnsupportedEncodingException("Reached end of packet.");
          }
          return pos;
        }
      }
    }
    throw new UnsupportedEncodingException(
        "Could not find " + nbToFind + " zero bytes in byte array.");
  }

  /** {@inheritDoc} */
  @Override
  public CSN getCSN()
  {
    return realUpdateMsg.getCSN();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isAssured()
  {
    // Always return false as we represent a not assured message
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void setAssured(boolean assured)
  {
    // No impact for this method as semantic is that assured is always false
    // and we do not want to change the original real update message settings
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj)
  {
    return obj != null
        && obj.getClass() == realUpdateMsg.getClass()
        && realUpdateMsg.getCSN().equals(((UpdateMsg) obj).getCSN());
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode()
  {
    return realUpdateMsg.hashCode();
  }

  /** {@inheritDoc} */
  @Override
  public int compareTo(UpdateMsg msg)
  {
    return realUpdateMsg.compareTo(msg);
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    if (protocolVersion == ProtocolVersion.REPLICATION_PROTOCOL_V1)
    {
      return realUpdateMsgNotAssuredBytesV1;
    }
    return realUpdateMsgNotAssuredBytesVLatest;
  }

  /** {@inheritDoc} */
  @Override
  public AssuredMode getAssuredMode()
  {
    return realUpdateMsg.getAssuredMode();
  }

  /** {@inheritDoc} */
  @Override
  public byte getSafeDataLevel()
  {
    return realUpdateMsg.getSafeDataLevel();
  }

  /** {@inheritDoc} */
  @Override
  public void setAssuredMode(AssuredMode assuredMode)
  {
    // No impact for this method as semantic is that assured is always false
    // and we do not want to change the original real update message settings
  }

  /** {@inheritDoc} */
  @Override
  public void setSafeDataLevel(byte safeDataLevel)
  {
    // No impact for this method as semantic is that assured is always false
    // and we do not want to change the original real update message settings
  }

  /** {@inheritDoc} */
  @Override
  public short getVersion()
  {
    return realUpdateMsg.getVersion();
  }

  /** {@inheritDoc} */
  @Override
  public int size()
  {
    return realUpdateMsg.size();
  }

  /** {@inheritDoc} */
  @Override
  protected ByteArrayBuilder encodeHeader(byte allowedType,
      short protocolVersion)
  {
    // Not called as only used by constructors using bytes
    return null;
  }

  /** {@inheritDoc} */
  @Override
  protected void decodeHeader(byte msgType, ByteArrayScanner scanner)
      throws DataFormatException
  {
    // Not called as only used by getBytes methods
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getPayload()
  {
    return realUpdateMsg.getPayload();
  }
}
