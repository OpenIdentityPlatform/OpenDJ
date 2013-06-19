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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock AS.
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
public class InitializeRcvAckMsg extends RoutableMsg
{
  private final int numAck;


  /**
   * Create a new message..
   *
   * @param sender The server ID of the server that send this message.
   * @param destination The destination server or servers of this message.
   * @param numAck The number of acknowledged messages.
   *               The window will be increase by this credit number.
   */
  public InitializeRcvAckMsg(int sender, int destination, int numAck)
  {
    super(sender, destination);
    this.numAck = numAck;
  }

  /**
   * Creates a new message from its encoded form.
   *
   * @param in The byte array containing the encoded form of the message.
   * @throws DataFormatException If the byte array does not contain a valid
   *                             encoded form of the message.
   */
  public InitializeRcvAckMsg(byte[] in) throws DataFormatException
  {
    super();
    try
    {
      // msg type
      if (in[0] != MSG_TYPE_INITIALIZE_RCV_ACK)
        throw new DataFormatException("input is not a valid "
            + this.getClass().getCanonicalName());
      int pos = 1;

      // sender
      int length = getNextLength(in, pos);
      String senderString = new String(in, pos, length, "UTF-8");
      senderID = Integer.valueOf(senderString);
      pos += length +1;

      // destination
      length = getNextLength(in, pos);
      String serverIdString = new String(in, pos, length, "UTF-8");
      destination = Integer.valueOf(serverIdString);
      pos += length +1;

      // value fo the ack
      length = getNextLength(in, pos);
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
  public byte[] getBytes(short protocolVersion)
  {
    try {
      byte[] byteSender = String.valueOf(senderID).getBytes("UTF-8");
      byte[] byteDestination = String.valueOf(destination).getBytes("UTF-8");
      byte[] byteNumAck = String.valueOf(numAck).getBytes("UTF-8");

      int length = 1 + byteSender.length + 1
                     + byteDestination.length + 1
                     + byteNumAck.length + 1;

      byte[] resultByteArray = new byte[length];

      /* put the type of the operation */
      resultByteArray[0] = MSG_TYPE_INITIALIZE_RCV_ACK;
      int pos = 1;

      // sender
      pos = addByteArray(byteSender, resultByteArray, pos);

      // destination
      pos = addByteArray(byteDestination, resultByteArray, pos);

      // ack value
      pos = addByteArray(byteNumAck, resultByteArray, pos);

      return resultByteArray;
    }
    catch (UnsupportedEncodingException e)
    {
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    return this.getClass().getSimpleName()  + "=["+
      " sender=" + this.senderID +
      " destination=" + this.destination +
      " msgID=" + this.numAck + "]";
  }

  /**
   * Get the number of message acknowledged by this message.
   *
   * @return the number of message acknowledged by this message.
   */
  public int getNumAck()
  {
    return numAck;
  }
}
