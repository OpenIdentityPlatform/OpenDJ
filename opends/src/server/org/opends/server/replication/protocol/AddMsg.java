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

import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;

import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.LDAPException;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.operation.PostOperationAddOperation;

import static org.opends.server.replication.protocol.OperationContext.*;
import static org.opends.server.util.StaticUtils.toLowerCase;

/**
 * This class is used to exchange Add operation between LDAP servers
 * and replication servers.
 */
public class AddMsg extends LDAPUpdateMsg
{
  private byte[] encodedAttributes;
  private String parentUniqueId;

  /**
   * Creates a new AddMessage.
   * @param op the operation to use when creating the message
   */
  public AddMsg(PostOperationAddOperation op)
  {
    super((AddContext) op.getAttachment(SYNCHROCONTEXT),
          op.getRawEntryDN().stringValue());

    AddContext ctx = (AddContext) op.getAttachment(SYNCHROCONTEXT);
    this.parentUniqueId = ctx.getParentUid();

    this.encodedAttributes =
      encodeAttributes(op.getObjectClasses(),
          op.getUserAttributes(),
          op.getOperationalAttributes());
  }

  private byte[] encodeAttributes(
      Map<ObjectClass, String> objectClasses,
      Map<AttributeType, List<Attribute>> userAttributes,
      Map<AttributeType, List<Attribute>> operationalAttributes)
  {
    //  Encode the object classes (SET OF LDAPString).
    AttributeBuilder builder = new AttributeBuilder(
        DirectoryServer.getObjectClassAttributeType());
    builder.setInitialCapacity(objectClasses.size());
    for (String s : objectClasses.values())
    {
      builder.add(new AttributeValue(new ASN1OctetString(s),
                         new ASN1OctetString(toLowerCase(s))));
    }
    Attribute attr = builder.toAttribute();

    ArrayList<ASN1Element> elems = new ArrayList<ASN1Element>();

    elems.add(new LDAPAttribute(attr).encode());

    // Encode the user attributes (AttributeList).
    for (List<Attribute> list : userAttributes.values())
    {
      for (Attribute a : list)
      {
        if (!a.isVirtual())
          elems.add(new LDAPAttribute(a).encode());
      }
    }

    // Encode the operational attributes (AttributeList).
    for (List<Attribute> list : operationalAttributes.values())
    {
      for (Attribute a : list)
      {
        if (!a.isVirtual())
          elems.add(new LDAPAttribute(a).encode());
      }
    }

    // Encode the sequence.
    return ASN1Element.encodeValue(elems);
  }

  /**
   * Creates a new AddMessage.
   *
   * @param cn                    ChangeNumber of the add.
   * @param dn                    DN of the added entry.
   * @param uniqueId              The Unique identifier of the added entry.
   * @param parentId              The unique Id of the parent of the added
   *                              entry.
   * @param objectClasses           objectclass of the added entry.
   * @param userAttributes        user attributes of the added entry.
   * @param operationalAttributes operational attributes of the added entry.
   */
  public AddMsg(ChangeNumber cn,
                String dn,
                String uniqueId,
                String parentId,
                Map<ObjectClass, String> objectClasses,
                Map<AttributeType,List<Attribute>> userAttributes,
                Map<AttributeType,List<Attribute>> operationalAttributes)
  {
    super (cn, uniqueId, dn);
    this.parentUniqueId = parentId;

    encodedAttributes = encodeAttributes(objectClasses,
                                        userAttributes, operationalAttributes);
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
    super (cn, uniqueId, dn);
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
    byte[] allowedPduTypes = new byte[2];
    allowedPduTypes[0] = MSG_TYPE_ADD;
    allowedPduTypes[1] = MSG_TYPE_ADD_V1;
    int pos = decodeHeader(allowedPduTypes, in);

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
  public AddOperationBasis createOperation(
         InternalClientConnection connection, String newDn)
         throws LDAPException, ASN1Exception
  {
    ArrayList<RawAttribute> attr = new ArrayList<RawAttribute>();
    ArrayList<ASN1Element> elems;

    elems = ASN1Element.decodeElements(encodedAttributes);
    for (ASN1Element elem : elems)
    {
      attr.add(LDAPAttribute.decode(elem));
    }

    AddOperationBasis add =  new AddOperationBasis(connection,
                            InternalClientConnection.nextOperationID(),
                            InternalClientConnection.nextMessageID(), null,
                            new ASN1OctetString(newDn), attr);
    AddContext ctx = new AddContext(getChangeNumber(), getUniqueId(),
                                    parentUniqueId);
    add.setAttachment(SYNCHROCONTEXT, ctx);
    return add;
  }

  /**
   * {@inheritDoc}
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
    byte [] resultByteArray = encodeHeader(MSG_TYPE_ADD, length);

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
    if (protocolVersion == ProtocolVersion.REPLICATION_PROTOCOL_V1)
    {
      return "AddMsg content: " +
        "\nprotocolVersion: " + protocolVersion +
        "\ndn: " + dn +
        "\nchangeNumber: " + changeNumber +
        "\nuniqueId: " + uniqueId +
        "\nassuredFlag: " + assuredFlag;
    }
    if (protocolVersion == ProtocolVersion.REPLICATION_PROTOCOL_V2)
    {
      return "AddMsg content: " +
        "\nprotocolVersion: " + protocolVersion +
        "\ndn: " + dn +
        "\nchangeNumber: " + changeNumber +
        "\nuniqueId: " + uniqueId +
        "\nassuredFlag: " + assuredFlag +
        "\nassuredMode: " + assuredMode +
        "\nsafeDataLevel: " + safeDataLevel;
    }
    return "!!! Unknown version: " + protocolVersion + "!!!";
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

  /**
   * Get the parent unique id of this add msg.
   * @return the parent unique id.
   */
  public String getParentUid()
  {
    return parentUniqueId;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int size()
  {
    return encodedAttributes.length + 40;
  }

  /**
   * {@inheritDoc}
   */
  public byte[] getBytes_V1() throws UnsupportedEncodingException
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
    byte [] resultByteArray = encodeHeader_V1(MSG_TYPE_ADD_V1, length);

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
}
