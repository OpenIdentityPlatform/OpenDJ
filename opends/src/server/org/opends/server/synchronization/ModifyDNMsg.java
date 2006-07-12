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

import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.Operation;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;

/**
 * Message used to send Modify DN information.
 */
public class ModifyDNMsg extends UpdateMessage
{
  private String dn;
  private String newRDN;
  private String newSuperior;
  private boolean deleteOldRdn;
  private static final long serialVersionUID = -4905520652801395185L;

  /**
   * construct a new Modify DN message.
   *
   * @param op The operation to use for building the message
   */
  public ModifyDNMsg(ModifyDNOperation op)
  {
    dn = op.getRawEntryDN().stringValue();
    deleteOldRdn = op.deleteOldRDN();
    if (op.getRawNewSuperior() != null)
      newSuperior = op.getRawNewSuperior().stringValue();
    else
      newSuperior = null;
    newRDN = op.getRawNewRDN().stringValue();
    changeNumber = (ChangeNumber) op.getAttachment(SYNCHRONIZATION);
  }

  /**
   * Creates a new Add message from a byte[].
   *
   * @param in The byte[] from which the operation must be read.
   * @throws DataFormatException The input byte[] is not a valid AddMsg
   */
  public ModifyDNMsg(byte[] in) throws DataFormatException
  {
    /* first byte is the type */
    if (in[0] != MSG_TYPE_MODIFYDN_REQUEST)
      throw new DataFormatException("byte[] is not a valid add msg");
    int pos = 1;

    /* read the dn
     * first calculate the length then construct the string
     */
    int length = 0;
    int offset = pos;
    while (in[pos++] != 0)
    {
      if (pos > in.length)
        throw new DataFormatException("byte[] is not a valid add msg");
      length++;
    }
    try
    {
      dn = new String(in, offset, length, "UTF-8");

      /* read the changeNumber
       * it is always 24 characters long
       */
      String changenumberStr = new  String(in, pos, 24, "UTF-8");
      changeNumber = new ChangeNumber(changenumberStr);
      pos +=24;

      /* read the newRDN
       * first calculate the length then construct the string
       */
      length = 0;
      offset = pos;
      while (in[pos++] != 0)
      {
        if (pos > in.length)
          throw new DataFormatException("byte[] is not a valid add msg");
        length++;
      }
      newRDN = new String(in, offset, length, "UTF-8");

      /* read the newSuperior
       * first calculate the length then construct the string
       */
      length = 0;
      offset = pos;
      while (in[pos++] != 0)
      {
        if (pos > in.length)
          throw new DataFormatException("byte[] is not a valid add msg");
        length++;
      }
      if (length != 0)
        newSuperior = new String(in, offset, length, "UTF-8");
      else
        newSuperior = null;

      /* get the deleteoldrdn flag */
      if (in[pos] == 0)
        deleteOldRdn = false;
      else
        deleteOldRdn = true;
    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * Create an operation from this ModifyDN message.
   * @param connection the connection to use when creating the operation
   * @return the created operation
   */
  @Override
  public Operation createOperation(InternalClientConnection connection)
  {
    ModifyDNOperation moddn =  new ModifyDNOperation(connection,
               InternalClientConnection.nextOperationID(),
               InternalClientConnection.nextMessageID(), null,
               new ASN1OctetString(dn), new ASN1OctetString(newRDN),
               deleteOldRdn,
               (newSuperior == null ? null : new ASN1OctetString(newSuperior)));
    moddn.setAttachment(SYNCHRONIZATION, getChangeNumber());
    return moddn;
  }

  /**
   * Get the byte array representation of this Message.
   *
   * @return The byte array representation of this Message.
   */
  @Override
  public byte[] getBytes()
  {
    try
    {
      byte[] byteDn = dn.getBytes("UTF-8");
      byte[] byteNewRdn = newRDN.getBytes("UTF-8");
      byte[] byteNewSuperior = null;

      /* The Modify DN message is stored in the form :
       * <operation type><dn><changenumber><newrdn><newsuperior><deleteoldrdn>
       * the length of result byte array is therefore :
       *   1 + dn length+1 + 24 + newrdn length+1 + newsuperior length+1 +1
       */
      int length = 1 + byteDn.length + 1 + 24 + byteNewRdn.length + 1 + 1;
      if (newSuperior != null)
      {
        byteNewSuperior = newSuperior.getBytes("UTF-8");
        length += byteNewSuperior.length + 1;
      }
      else
        length += 1;

      byte[] resultByteArray = new byte[length];
      int pos = 1;

      /* put the type of the operation */
      resultByteArray[0] = MSG_TYPE_MODIFYDN_REQUEST;

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

      /* put the new RDN and a terminating 0 */
      for (int i = 0; i< byteNewRdn.length; i++,pos++)
      {
        resultByteArray[pos] = byteNewRdn[i];
      }
      resultByteArray[pos++] = 0;

      /* put the newsuperior and a terminating 0 */
      if (newSuperior != null)
      {
        for (int i = 0; i< byteNewSuperior.length; i++,pos++)
        {
          resultByteArray[pos] = byteNewSuperior[i];
        }
        resultByteArray[pos++] = 0;
      }
      else
        resultByteArray[pos++] = 0;

      /* put the deleteoldrdn flag */
      if (deleteOldRdn)
        resultByteArray[pos++] = 1;
      else
        resultByteArray[pos++] = 0;

      return resultByteArray;
    } catch (UnsupportedEncodingException e)
    {
      // should never happen : TODO : log error
    }
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return ("Modify DN " + dn + " " + newRDN + " " + newSuperior + " " +
            getChangeNumber());
  }
}
