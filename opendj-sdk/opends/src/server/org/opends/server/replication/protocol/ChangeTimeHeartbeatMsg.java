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
 */
package org.opends.server.replication.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

import org.opends.server.replication.common.ChangeNumber;

/**
 * Class that define messages sent by a replication domain (DS)
 * to the replication server to let the RS know the DS current
 * change time.
 */
public class ChangeTimeHeartbeatMsg extends ReplicationMsg
{
  /**
   * The ChangeNumber containing the change time.
   */
  private final ChangeNumber changeNumber;

  /**
   * Constructor of a Change Time Heartbeat message.
   */
  public ChangeTimeHeartbeatMsg()
  {
    this.changeNumber = new ChangeNumber((long)0,0,0);
  }

  /**
   * Constructor of a Change Time Heartbeat message providing
   * the change time value in a change number.
   * @param cn The provided change number.
   */
  public ChangeTimeHeartbeatMsg(ChangeNumber cn)
  {
    this.changeNumber = cn;
  }

  /**
   * Get a change number with the transmitted change time.
   * @return the ChangeNumber
   */
  public ChangeNumber getChangeNumber()
  {
    return changeNumber;
  }

  /**
   * Encode a change time message.
   * @return The encoded message.
   * @throws UnsupportedEncodingException When an error occurs.
   */
  public byte[] encode() throws UnsupportedEncodingException
  {
    byte[] changeNumberByte =
      this.getChangeNumber().toString().getBytes("UTF-8");
    int length = changeNumberByte.length;
    byte[] encodedMsg = new byte[length];

    /* Put the ChangeNumber */
    addByteArray(changeNumberByte, encodedMsg, 0);

    return encodedMsg;
  }

  /**
   * Creates a message from a provided byte array.
   * @param in The provided byte array.
   * @throws DataFormatException When an error occurs.
   */
  public ChangeTimeHeartbeatMsg(byte[] in) throws DataFormatException
  {
    try
    {
      /* Read the changeNumber */
      /* First byte is the type */
      if (in[0] != MSG_TYPE_CT_HEARTBEAT)
      {
        throw new DataFormatException("byte[] is not a valid CT_HEARTBEAT msg");
      }
      int pos = 1;
      int length = getNextLength(in, pos);
      String changenumberStr = new String(in, pos, length, "UTF-8");
      changeNumber = new ChangeNumber(changenumberStr);
    }
    catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
    catch (IllegalArgumentException e)
    {
      throw new DataFormatException(e.getMessage());
    }
  }

  /**
   * Get a byte array from the message.
   * @return The byte array containing the PDU of the message.
   * @throws UnsupportedEncodingException When an error occurs.
   */
  public byte[] getBytes() throws UnsupportedEncodingException
  {
    try {
      ByteArrayOutputStream oStream = new ByteArrayOutputStream();

      /* Put the type of the operation */
      oStream.write(MSG_TYPE_CT_HEARTBEAT);

      /* Put the ChangeNumber */
      byte[] changeNumberByte = changeNumber.toString().getBytes("UTF-8");
      oStream.write(changeNumberByte);
      oStream.write(0);

      return oStream.toByteArray();
    } catch (IOException e)
    {
      // never happens
      return null;
    }
  }
}
