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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.replication.protocol;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.LDAPException;
import org.opends.server.types.Operation;

/**
 * Abstract class that must be extended to define a message
 * used for sending Updates between servers.
 */
public abstract class UpdateMessage extends SynchronizationMessage
                                    implements Serializable,
                                               Comparable<UpdateMessage>
{
  /**
   * The ChangeNumber of this update.
   */
  private ChangeNumber changeNumber;

  /**
   * The DN on which the update was originally done.
   */
  private String dn = null;

  /**
   * True when the update must use assured replication.
   */
  private boolean assuredFlag = false;

  /**
   * The UniqueId of the entry that was updated.
   */
  private String UniqueId;

  /**
   * Creates a new UpdateMessage with the given informations.
   *
   * @param ctx The Synchronization Context of the operation for which the
   *            update message must be created,.
   * @param dn The dn of the entry on which the change
   *           that caused the creation of this object happened
   */
  public UpdateMessage(OperationContext ctx, String dn)
  {
    this.changeNumber = ctx.getChangeNumber();
    this.UniqueId = ctx.getEntryUid();
    this.dn = dn;
  }

  /**
   * Creates a new UpdateMessage from an ecoded byte array.
   *
   * @param in The encoded byte array containind the UpdateMessage.
   * @throws DataFormatException if the encoded byte array is not valid.
   * @throws UnsupportedEncodingException if UTF-8 is not supprted.
   */
  public UpdateMessage(byte[] in) throws DataFormatException,
                                         UnsupportedEncodingException
  {
    /* read the changeNumber */
    int pos = 1;
    int length = getNextLength(in, pos);
    String changenumberStr = new String(in, pos, length, "UTF-8");
    this.changeNumber = new ChangeNumber(changenumberStr);
  }

  /**
   * Generates an Update Message which the provided information.
   *
   * @param op The operation for which the message must be created.
   * @param isAssured flag indicating if the operation is an assured operation.
   * @return The generated message.
   */
  public static UpdateMessage generateMsg(Operation op, boolean isAssured)
  {
    UpdateMessage msg = null;
    switch (op.getOperationType())
    {
    case MODIFY :
      msg = new ModifyMsg((ModifyOperation) op);
      if (isAssured)
        msg.setAssured();
      break;

    case ADD:
      msg = new AddMsg((AddOperation) op);
      if (isAssured)
        msg.setAssured();
      break;

    case DELETE :
      msg = new DeleteMsg((DeleteOperation) op);
      if (isAssured)
        msg.setAssured();
      break;

    case MODIFY_DN :
      msg = new ModifyDNMsg((ModifyDNOperation) op);
      if (isAssured)
        msg.setAssured();
      break;
    }

    return msg;
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
   * Get the DN on which the operation happened.
   *
   * @return The DN on which the operations happened.
   */
  public String getDn()
  {
    return dn;
  }

  /**
   * Set the DN.
   * @param dn The dn that must now be used for this message.
   */
  public void setDn(String dn)
  {
    this.dn = dn;
  }

  /**
   * Get the Unique Identifier of the entry on which the operation happened.
   *
   * @return The Unique Identifier of the entry on which the operation happened.
   */
  public String getUniqueId()
  {
    return UniqueId;
  }

  /**
   * Get a boolean indicating if the Update must be processed as an
   * Asynchronous or as an assured synchronization.
   *
   * @return Returns the assuredFlag.
   */
  public boolean isAssured()
  {
    return assuredFlag;
  }

  /**
   * Set the Update message as an assured message.
   */
  public void setAssured()
  {
    assuredFlag = true;
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
      return changeNumber.equals(((UpdateMessage) obj).changeNumber);
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
  public int compareTo(UpdateMessage msg)
  {
    return changeNumber.compareTo(msg.getChangeNumber());
  }

  /**
   * Create and Operation from the message.
   *
   * @param   conn connection to use when creating the message
   * @return  the created Operation
   * @throws  LDAPException In case of LDAP decoding exception.
   * @throws  ASN1Exception In case of ASN1 decoding exception.
   * @throws DataFormatException In case of bad msg format.
   */
  public Operation createOperation(InternalClientConnection conn)
         throws LDAPException, ASN1Exception, DataFormatException
  {
    return createOperation(conn, dn);
  }


  /**
   * Create and Operation from the message using the provided DN.
   *
   * @param   conn connection to use when creating the message.
   * @param   newDn the DN to use when creating the operation.
   * @return  the created Operation.
   * @throws  LDAPException In case of LDAP decoding exception.
   * @throws  ASN1Exception In case of ASN1 decoding exception.
   * @throws DataFormatException In case of bad msg format.
   */
  public abstract Operation createOperation(InternalClientConnection conn,
                                            String newDn)
         throws LDAPException, ASN1Exception, DataFormatException;

  /**
   * Encode the common header for all the UpdateMessage.
   *
   * @param type the type of UpdateMessage to encode.
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
    byte[] byteDn = dn.getBytes("UTF-8");
    byte[] changeNumberByte =
      this.getChangeNumber().toString().getBytes("UTF-8");
    byte[] byteEntryuuid = getUniqueId().getBytes("UTF-8");

    /* The message header is stored in the form :
     * <operation type>changenumber><dn><assured><entryuuid><change>
     * the length of result byte array is therefore :
     *   1 + change number length + 1 + dn length + 1  + 1 +
     *   uuid length + 1 + additional_length
     */
    int length = 5 + changeNumberByte.length + byteDn.length
                 + byteEntryuuid.length + additionalLength;

    byte[] encodedMsg = new byte[length];

    /* put the type of the operation */
    encodedMsg[0] = type;
    int pos = 1;

    /* put the ChangeNumber */
    pos = addByteArray(changeNumberByte, encodedMsg, pos);

    /* put the assured information */
    encodedMsg[pos++] = (assuredFlag ? (byte) 1 : 0);

    /* put the DN and a terminating 0 */
    pos = addByteArray(byteDn, encodedMsg, pos);

    /* put the entry uuid and a terminating 0 */
    pos = addByteArray(byteEntryuuid, encodedMsg, pos);

    return encodedMsg;
  }

  /**
   * Decode the Header part of this Update Message, and check its type.
   *
   * @param type The type of this Update Message.
   * @param encodedMsg the encoded form of the UpdateMessage.
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
      /* read the changeNumber */
      int pos = 1;
      int length = getNextLength(encodedMsg, pos);
      String changenumberStr = new String(encodedMsg, pos, length, "UTF-8");
      pos += length + 1;
      changeNumber = new ChangeNumber(changenumberStr);

      /* read the assured information */
      if (encodedMsg[pos++] == 1)
        assuredFlag = true;
      else
        assuredFlag = false;

      /* read the dn */
      length = getNextLength(encodedMsg, pos);
      dn = new String(encodedMsg, pos, length, "UTF-8");
      pos += length + 1;

      /* read the entryuuid */
      length = getNextLength(encodedMsg, pos);
      UniqueId = new String(encodedMsg, pos, length, "UTF-8");
      pos += length + 1;

      return pos;
    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }

  }
}
