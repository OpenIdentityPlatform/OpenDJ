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
 * This message is used by LDAP server or by Replication Servers to
 * update the send window of the remote entities.
 *
 * A receiving entity should create such a message with a given credit
 * when it wants to open the send window of the remote entity.
 * A LDAP or Replication Server should increase its send window when receiving
 * such a message.
 */
public class WindowMsg extends ReplicationMsg
{
  private final int numAck;


  /**
   * Create a new WindowMsg.
   *
   * @param numAck The number of acknowledged messages.
   *               The window will be increase by this credit number.
   */
  public WindowMsg(int numAck)
  {
    this.numAck = numAck;
  }

  /**
   * Creates a new WindowMsg from its encoded form.
   *
   * @param in The byte array containing the encoded form of the
   *           WindowMsg.
   * @throws DataFormatException If the byte array does not contain a valid
   *                             encoded form of the WindowMsg.
   */
  public WindowMsg(byte[] in) throws DataFormatException
  {
    /* The WindowMsg is encoded in the form :
     * <numAck>
     */
    try
    {
      /* first byte is the type */
      if (in[0] != MSG_TYPE_WINDOW)
        throw new DataFormatException("input is not a valid Window Message");
      int pos = 1;

      /*
       * read the number of acks contained in this message.
       * first calculate the length then construct the string
       */
      int length = getNextLength(in, pos);
      String numAckStr = new String(in, pos, length, "UTF-8");
      pos += length +1;
      numAck = Integer.parseInt(numAckStr);
    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes()
  {
    /*
     * WindowMsg contains.
     * <numAck>
     */
    try {
      byte[] byteNumAck = String.valueOf(numAck).getBytes("UTF-8");

      int length = 1 + byteNumAck.length + 1;

      byte[] resultByteArray = new byte[length];

      /* put the type of the operation */
      resultByteArray[0] = MSG_TYPE_WINDOW;
      int pos = 1;

      pos = addByteArray(byteNumAck, resultByteArray, pos);

      return resultByteArray;
    }
    catch (UnsupportedEncodingException e)
    {
      return null;
    }
  }


  /**
   * Get the number of message acknowledged by the Window Message.
   *
   * @return the number of message acknowledged by the Window Message.
   */
  public int getNumAck()
  {
    return numAck;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return "WindowMsg : " + "numAck: " + numAck;
  }
}
