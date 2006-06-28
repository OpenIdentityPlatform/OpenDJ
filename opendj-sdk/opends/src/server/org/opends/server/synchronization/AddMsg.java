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

import org.opends.server.core.AddOperation;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPException;

import static org.opends.server.synchronization.SynchMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;

/**
 * This class is used to exchange Add operation between LDAP servers
 * and changelog servers.
 */
public class AddMsg extends UpdateMessage
{
  private static final long serialVersionUID = -4905520652801395185L;
  private String dn;
  private byte[] encodedAttributes ;

  /**
   * Creates a new AddMessage.
   * @param op the operation to use when creating the message
   */
  public AddMsg(AddOperation op)
  {
    dn = op.getRawEntryDN().stringValue();
    List<LDAPAttribute> attrs = op.getRawAttributes();
    ArrayList<ASN1Element> elems = new ArrayList<ASN1Element>(attrs.size());

    /*
     * encode each LDAPAttribute into an ASN1Element
     * then add each ASN1Element into an ArrayList,
     * then encode the Arraylist of ASN1Element into a byte[]
     */
    for (LDAPAttribute attr : attrs)
      elems.add(attr.encode());
    encodedAttributes = ASN1Element.encodeValue(elems);

    changeNumber = (ChangeNumber) op.getAttachment(SYNCHRONIZATION);
  }

  /**
   * Creates a new Add message from a byte[].
   *
   * @param in The byte[] from which the operation must be read.
   * @throws Exception The input byte[] is not a valid AddMsg
   */
  public AddMsg(byte[] in) throws Exception
  {
    /* first byte is the type */
    if (in[0] != OP_TYPE_ADD_REQUEST)
      throw new Exception("byte[] is not a valid add msg");
    int pos = 1;

    /* read the dn
     * first calculate the length then construct the string
     */
    int length = 0;
    int offset = pos;
    while (in[pos++] != 0)
    {
      if (pos > in.length)
        throw new Exception("byte[] is not a valid add msg");
      length++;
    }
    dn = new String(in, offset, length, "UTF-8");

    /* read the changeNumber
     * it is always 24 characters long
     */
    String changenumberStr = new  String(in, pos, 24, "UTF-8");
    changeNumber = new ChangeNumber(changenumberStr);
    pos +=24;

    /* Read the attributes : all the remaining bytes */
    encodedAttributes = new byte[in.length-pos];
    int i =0;
    while (pos<in.length)
    {
      encodedAttributes[i++] = in[pos++];
    }
  }

  /**
   * Create and return an AddOperation from a received ADD message.
   * @param connection The connection where we received the message.
   * @return The created operation.
   * @throws LDAPException In case msg encoding was not valid.
   * @throws ASN1Exception In case msg encoding was not valid.
   */
  @Override
  public AddOperation createOperation(InternalClientConnection connection)
                      throws LDAPException, ASN1Exception
  {
    ArrayList<LDAPAttribute> attr = new ArrayList<LDAPAttribute>();
    ArrayList<ASN1Element> elems;

    elems = ASN1Element.decodeElements(encodedAttributes);
    for (ASN1Element elem : elems)
    {
      attr.add(LDAPAttribute.decode(elem));
    }

    AddOperation add =  new AddOperation(connection,
                            InternalClientConnection.nextOperationID(),
                            InternalClientConnection.nextMessageID(), null,
                            new ASN1OctetString(dn), attr);

    add.setAttachment(SYNCHRONIZATION, getChangeNumber());
    return add;
  }

  /**
   * Get the byte[] representation of this Message.
   * @return the byte array representation of this Message.
   */
  @Override
  public byte[] getByte()
  {
    byte[] byteDn;
    try
    {
      byteDn = dn.getBytes("UTF-8");

      /* The ad message is stored in the form :
       * <operation type><dn><changenumber><attributes>
       * the length of result byte array is therefore :
       *   1 + dn length + 1 + 24 + attribute length
       */
      int length = 1 + byteDn.length + 1  + 24 + encodedAttributes.length;
      byte[] resultByteArray = new byte[length];
      int pos = 1;

      /* put the type of the operation */
      resultByteArray[0] = OP_TYPE_ADD_REQUEST;
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

      /* put the attributes */
      for (int i=0; i<encodedAttributes.length; i++,pos++)
      {
        resultByteArray[pos] = encodedAttributes[i];
      }
      return resultByteArray;
    } catch (UnsupportedEncodingException e)
    {
      // this can not happen as only UTF-8 is used and it is always
      // going to be supported by the jvm
      // TODO : should log an error
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return ("ADD " + dn + " " + getChangeNumber());
  }
}
