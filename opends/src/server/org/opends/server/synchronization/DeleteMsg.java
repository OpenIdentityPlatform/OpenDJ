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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.synchronization;

import static org.opends.server.synchronization.SynchMessages.SYNCHRONIZATION;

import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

import org.opends.server.core.DeleteOperation;
import org.opends.server.core.Operation;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;

/**
 * Object used when sending delete information to Changelogs.
 */
public class DeleteMsg extends UpdateMessage
{
  private String dn;
  private static final long serialVersionUID = -4905520652801395185L;

  /**
   * Creates a new delete message.
   * @param op the Operation from which the message must be created.
   */
  public DeleteMsg(DeleteOperation op)
  {
    dn = op.getRawEntryDN().stringValue();
    changeNumber = (ChangeNumber) op.getAttachment(SYNCHRONIZATION);
  }

  /**
   * Creates a new Add message from a byte[].
   *
   * @param in The byte[] from which the operation must be read.
   * @throws DataFormatException The input byte[] is not a valid AddMsg
   */
  public DeleteMsg(byte[] in) throws DataFormatException
  {
    /* first byte is the type */
    if (in[0] != MSG_TYPE_DELETE_REQUEST)
      throw new DataFormatException("byte[] is not a valid delete msg");
    int pos = 1;

    /* read the dn
     * first calculate the length then construct the string
     */
    int length = 0;
    int offset = pos;
    while (in[pos++] != 0)
    {
      if (pos > in.length)
        throw new DataFormatException("byte[] is not a valid delete msg");
      length++;
    }
    try
    {
      dn = new String(in, offset, length, "UTF-8");

      /* read the changeNumber
       * it is always 24 characters long
       */
      String changenumberStr = new String(in, pos, 24, "UTF-8");
      changeNumber = new ChangeNumber(changenumberStr);
    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }


  /**
   * Create an Operation from a delete Message.
   *
   * @param connection the connection
   * @return the Operation from which the message was received
   */
  @Override
  public Operation createOperation(InternalClientConnection connection)
  {
    DeleteOperation del =  new DeleteOperation(connection,
                               InternalClientConnection.nextOperationID(),
                               InternalClientConnection.nextMessageID(), null,
                               new ASN1OctetString(dn));
    del.setAttachment(SYNCHRONIZATION, getChangeNumber());
    return del;
  }

  /**
   * Get the byte array representation of this Message.
   *
   * @return The byte array representation of this Message.
   */
  @Override
  public byte[] getBytes()
  {
    byte[] byteDn;
    try
    {
      byteDn = dn.getBytes("UTF-8");

      /* The Delete message is stored in the form :
       * <operation type><dn><changenumber>
       * the length of result byte array is therefore :
       *   1 + dn length + 1 + 24
       */
      int length = 1 + byteDn.length + 1  + 24;
      byte[] resultByteArray = new byte[length];
      int pos = 1;

      /* put the type of the operation */
      resultByteArray[0] = MSG_TYPE_DELETE_REQUEST;

      /* put the DN and a terminating 0 */
      for (int i = 0; i< byteDn.length; i++,pos++)
      {
        resultByteArray[pos] = byteDn[i];
      }
      resultByteArray[pos++] = 0;

      /* put the ChangeNumber */
      byte[] changeNumberByte =
                      this.getChangeNumber().toString().getBytes("UTF-8");
      for (int i=0; i<24; i++,pos++)
      {
        resultByteArray[pos] = changeNumberByte[i];
      }

      return resultByteArray;
    } catch (UnsupportedEncodingException e)
    {
      // should never happen : TODO : log error properly
    }
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return ("DEL " + dn + " " + getChangeNumber());
  }
}
