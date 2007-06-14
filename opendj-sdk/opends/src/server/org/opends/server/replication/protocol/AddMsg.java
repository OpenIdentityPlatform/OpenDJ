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

import org.opends.server.core.AddOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.zip.DataFormatException;

import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.LDAPException;
import org.opends.server.types.RawAttribute;

import static org.opends.server.replication.protocol.OperationContext.*;
import static org.opends.server.util.StaticUtils.toLowerCase;

/**
 * This class is used to exchange Add operation between LDAP servers
 * and replication servers.
 */
public class AddMsg extends UpdateMessage
{
  private static final long serialVersionUID = -4905520652801395185L;
  private byte[] encodedAttributes;
  private String parentUniqueId;

  /**
   * Creates a new AddMessage.
   * @param op the operation to use when creating the message
   */
  public AddMsg(AddOperation op)
  {
    super((AddContext) op.getAttachment(SYNCHROCONTEXT),
          op.getRawEntryDN().stringValue());

    AddContext ctx = (AddContext) op.getAttachment(SYNCHROCONTEXT);
    this.parentUniqueId = ctx.getParentUid();

    // Encode the object classes (SET OF LDAPString).
    LinkedHashSet<AttributeValue> ocValues =
      new LinkedHashSet<AttributeValue>(op.getObjectClasses().size());
    for (String s : op.getObjectClasses().values())
    {
      ocValues.add(new AttributeValue(new ASN1OctetString(s),
                         new ASN1OctetString(toLowerCase(s))));
    }

    Attribute attr = new Attribute(
                 DirectoryServer.getObjectClassAttributeType(),
                 "objectClass", ocValues);

    ArrayList<ASN1Element> elems = new ArrayList<ASN1Element>();

    elems.add(new LDAPAttribute(attr).encode());

    // Encode the user attributes (AttributeList).
    for (List<Attribute> list : op.getUserAttributes().values())
    {
      for (Attribute a : list)
      {
        elems.add(new LDAPAttribute(a).encode());
      }
    }

    // Encode the operational attributes (AttributeList).
    for (List<Attribute> list : op.getOperationalAttributes().values())
    {
      for (Attribute a : list)
      {
        elems.add(new LDAPAttribute(a).encode());
      }
    }

    // Encode the sequence.
    encodedAttributes = ASN1Element.encodeValue(elems);
  }

  /**
   * Creates a new AddMessage.
   *
   * @param cn ChangeNumber of the add.
   * @param dn DN of the added entry.
   * @param uniqueId The Unique identifier of the added entry.
   * @param parentId The unique Id of the parent of the added entry.
   * @param objectClass objectclass of the added entry.
   * @param userAttributes user attributes of the added entry.
   * @param operationalAttributes operational attributes of the added entry.
   */
  public AddMsg(ChangeNumber cn,
                String dn,
                String uniqueId,
                String parentId,
                Attribute objectClass,
                Collection<Attribute> userAttributes,
                Collection<Attribute> operationalAttributes)
  {
    super (new AddContext(cn, uniqueId, parentId), dn);
    this.parentUniqueId = parentId;

    ArrayList<ASN1Element> elems = new ArrayList<ASN1Element>();
    elems.add(new LDAPAttribute(objectClass).encode());

    for (Attribute a : userAttributes)
      elems.add(new LDAPAttribute(a).encode());

    if (operationalAttributes != null)
      for (Attribute a : operationalAttributes)
        elems.add(new LDAPAttribute(a).encode());

    encodedAttributes = ASN1Element.encodeValue(elems);
  }

  /**
   * Creates a new Add message from a byte[].
   *
   * @param in The byte[] from which the operation must be read.
   * @throws DataFormatException The input byte[] is not a valid AddMsg
   * @throws UnsupportedEncodingException If UTF8 is not supported by the jvm
   */
  public AddMsg(byte[] in) throws DataFormatException,
                                  UnsupportedEncodingException
  {
    super(in);

    int  pos = decodeHeader(MSG_TYPE_ADD_REQUEST, in);

    // read the parent unique Id
    int length = getNextLength(in, pos);
    if (length != 0)
    {
      parentUniqueId = new String(in, pos, length, "UTF-8");
      pos += length + 1;
    }
    else
    {
      parentUniqueId = null;
      pos += 1;
    }

    // Read the attributes : all the remaining bytes
    encodedAttributes = new byte[in.length-pos];
    int i =0;
    while (pos<in.length)
    {
      encodedAttributes[i++] = in[pos++];
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public AddOperation createOperation(InternalClientConnection connection,
                                      String newDn)
                      throws LDAPException, ASN1Exception
  {
    ArrayList<RawAttribute> attr = new ArrayList<RawAttribute>();
    ArrayList<ASN1Element> elems;

    elems = ASN1Element.decodeElements(encodedAttributes);
    for (ASN1Element elem : elems)
    {
      attr.add(LDAPAttribute.decode(elem));
    }

    AddOperation add =  new AddOperation(connection,
                            InternalClientConnection.nextOperationID(),
                            InternalClientConnection.nextMessageID(), null,
                            new ASN1OctetString(newDn), attr);
    AddContext ctx = new AddContext(getChangeNumber(), getUniqueId(),
                                    parentUniqueId);
    add.setAttachment(SYNCHROCONTEXT, ctx);
    return add;
  }

  /**
   * Get the byte[] representation of this Message.
   *
   * @return the byte array representation of this Message.
   *
   * @throws UnsupportedEncodingException When the encoding of the message
   *         failed because the UTF-8 encoding is not supported.
   */
  @Override
  public byte[] getBytes() throws UnsupportedEncodingException
  {
    int length = encodedAttributes.length;
    byte[] byteParentId = null;
    if (parentUniqueId != null)
    {
      byteParentId = parentUniqueId.getBytes("UTF-8");
      length += byteParentId.length + 1;
    }
    else
    {
      length += 1;
    }

    /* encode the header in a byte[] large enough to also contain the mods */
    byte [] resultByteArray = encodeHeader(MSG_TYPE_ADD_REQUEST, length);

    int pos = resultByteArray.length - length;

    if (byteParentId != null)
      pos = addByteArray(byteParentId, resultByteArray, pos);
    else
      resultByteArray[pos++] = 0;

    /* put the attributes */
    for (int i=0; i<encodedAttributes.length; i++,pos++)
    {
      resultByteArray[pos] = encodedAttributes[i];
    }
    return resultByteArray;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return ("ADD " + getDn() + " " + getChangeNumber());
  }

  /**
   * Add the specified attribute/attribute value in the entry contained
   * in this AddMsg.
   *
   * @param name  The name of the attribute to add.
   * @param value The value of the attribute to add.
   * @throws ASN1Exception When this Msg is not valid.
   */
  public void addAttribute(String name, String value)
         throws ASN1Exception
  {
    RawAttribute newAttr = new LDAPAttribute(name, value);
    ArrayList<ASN1Element> elems;
    elems = ASN1Element.decodeElements(encodedAttributes);
    elems.add(newAttr.encode());
    encodedAttributes = ASN1Element.encodeValue(elems);
  }

  /**
   * Set the parent unique id of this add msg.
   *
   * @param uid the parent unique id.
   */
  public void setParentUid(String uid)
  {
    parentUniqueId = uid;
  }
}
