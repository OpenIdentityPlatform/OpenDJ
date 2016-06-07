/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.opends.server.replication.protocol.OperationContext.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.opends.server.core.AddOperation;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.plugin.EntryHistorical;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.LDAPException;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.operation.PostOperationAddOperation;

/**
 * This class is used to exchange Add operation between LDAP servers
 * and replication servers.
 */
public class AddMsg extends LDAPUpdateMsg
{
  /** Attributes are managed encoded. */
  private byte[] encodedAttributes;

  /** Parent is managed decoded. */
  private String parentEntryUUID;

  /**
   * Creates a new AddMessage.
   * @param op the operation to use when creating the message
   */
  AddMsg(PostOperationAddOperation op)
  {
    super((AddContext) op.getAttachment(SYNCHROCONTEXT), op.getEntryDN());

    AddContext ctx = (AddContext) op.getAttachment(SYNCHROCONTEXT);

    // Stores parentUniqueID not encoded
    this.parentEntryUUID = ctx.getParentEntryUUID();

    // Stores attributes encoded
    this.encodedAttributes = encodeAttributes(op.getObjectClasses(),
        op.getUserAttributes(), op.getOperationalAttributes());
  }

  /**
   * Creates a new AddMessage.
   *
   * @param csn                   CSN of the add.
   * @param dn                    DN of the added entry.
   * @param entryUUID             The Unique identifier of the added entry.
   * @param parentEntryUUID       The unique Id of the parent of the added
   *                              entry.
   * @param objectClasses           objectclass of the added entry.
   * @param userAttributes        user attributes of the added entry.
   * @param operationalAttributes operational attributes of the added entry.
   */
  public AddMsg(CSN csn,
                DN dn,
                String entryUUID,
                String parentEntryUUID,
                Map<ObjectClass, String> objectClasses,
                Map<AttributeType,List<Attribute>> userAttributes,
                Map<AttributeType,List<Attribute>> operationalAttributes)
  {
    super (csn, entryUUID, dn);

    // Stores parentUniqueID not encoded
    this.parentEntryUUID = parentEntryUUID;

    // Stores attributes encoded
    this.encodedAttributes = encodeAttributes(objectClasses, userAttributes,
        operationalAttributes);
  }


  /**
   * Creates a new AddMessage.
   *
   * @param csn CSN of the add.
   * @param dn DN of the added entry.
   * @param uniqueId The Unique identifier of the added entry.
   * @param parentId The unique Id of the parent of the added entry.
   * @param objectClass objectclass of the added entry.
   * @param userAttributes user attributes of the added entry.
   * @param operationalAttributes operational attributes of the added entry.
   */
  public AddMsg(CSN csn,
                DN dn,
                String uniqueId,
                String parentId,
                Attribute objectClass,
                Collection<Attribute> userAttributes,
                Collection<Attribute> operationalAttributes)
  {
    super (csn, uniqueId, dn);

    // Stores parentUniqueID not encoded
    this.parentEntryUUID = parentId;

    // Stores attributes encoded
    this.encodedAttributes = encodeAttributes(objectClass, userAttributes,
        operationalAttributes);
  }

  /**
   * Creates a new Add message from a byte[].
   *
   * @param in The byte[] from which the operation must be read.
   * @throws DataFormatException The input byte[] is not a valid AddMsg
   */
  public AddMsg(byte[] in) throws DataFormatException
  {
    final ByteArrayScanner scanner = new ByteArrayScanner(in);
    decodeHeader(scanner, MSG_TYPE_ADD, MSG_TYPE_ADD_V1);

    if (protocolVersion <= 3)
    {
      decodeBody_V123(scanner);
    }
    else
    {
      decodeBody_V4(scanner);
    }
    if (protocolVersion==ProtocolVersion.getCurrentVersion())
    {
      bytes = in;
    }
  }

  /** {@inheritDoc} */
  @Override
  public AddOperation createOperation(
      InternalClientConnection connection, DN newDN)
  throws LDAPException, DecodeException
  {
    List<RawAttribute> attr = decodeRawAttributes(encodedAttributes);

    AddOperation add =  new AddOperationBasis(connection,
        InternalClientConnection.nextOperationID(),
        InternalClientConnection.nextMessageID(), null,
        ByteString.valueOfUtf8(newDN.toString()), attr);
    AddContext ctx = new AddContext(getCSN(), getEntryUUID(), parentEntryUUID);
    add.setAttachment(SYNCHROCONTEXT, ctx);
    return add;
  }

  // ============
  // Msg encoding
  // ============

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes_V1()
  {
    final ByteArrayBuilder builder = encodeHeader_V1(MSG_TYPE_ADD_V1);
    builder.appendString(parentEntryUUID);
    builder.appendByteArray(encodedAttributes);
    return builder.toByteArray();
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes_V23()
  {
    final ByteArrayBuilder builder =
        encodeHeader(MSG_TYPE_ADD, ProtocolVersion.REPLICATION_PROTOCOL_V3);
    builder.appendString(parentEntryUUID);
    builder.appendByteArray(encodedAttributes);
    return builder.toByteArray();
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes_V45(short protocolVersion)
  {
    final ByteArrayBuilder builder =
        encodeHeader(MSG_TYPE_ADD, protocolVersion);
    builder.appendString(parentEntryUUID);
    builder.appendIntUTF8(encodedAttributes.length);
    builder.appendZeroTerminatedByteArray(encodedAttributes);
    builder.appendIntUTF8(encodedEclIncludes.length);
    builder.appendZeroTerminatedByteArray(encodedEclIncludes);
    return builder.toByteArray();
  }

  private byte[] encodeAttributes(
      Map<ObjectClass, String> objectClasses,
      Map<AttributeType, List<Attribute>> userAttributes,
      Map<AttributeType, List<Attribute>> operationalAttributes)
  {
    ByteStringBuilder byteBuilder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(byteBuilder);

    try
    {
      //  Encode the object classes (SET OF LDAPString).
      AttributeBuilder builder = new AttributeBuilder(getObjectClassAttributeType());
      builder.addAllStrings(objectClasses.values());
      new LDAPAttribute(builder.toAttribute()).write(writer);

      // Encode the user and operational attributes (AttributeList).
      encodeAttributes(userAttributes, writer);
      encodeAttributes(operationalAttributes, writer);
    }
    catch(Exception e)
    {
      // TODO: DO something
    }

    // Encode the sequence.
    return byteBuilder.toByteArray();
  }

  private void encodeAttributes(Map<AttributeType, List<Attribute>> attributes,
      ASN1Writer writer) throws Exception
  {
    for (List<Attribute> list : attributes.values())
    {
      for (Attribute a : list)
      {
        if (!a.isVirtual() && !EntryHistorical.isHistoricalAttribute(a))
        {
          new LDAPAttribute(a).write(writer);
        }
      }
    }
  }

  private byte[] encodeAttributes(
      Attribute objectClass,
      Collection<Attribute> userAttributes,
      Collection<Attribute> operationalAttributes)
  {
    ByteStringBuilder byteBuilder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(byteBuilder);
    try
    {
      new LDAPAttribute(objectClass).write(writer);

      for (Attribute a : userAttributes)
      {
        new LDAPAttribute(a).write(writer);
      }

      if (operationalAttributes != null)
      {
        for (Attribute a : operationalAttributes)
        {
          new LDAPAttribute(a).write(writer);
        }
      }
    }
    catch(Exception e)
    {
      // Do something
    }
    return byteBuilder.toByteArray();
  }

  // ============
  // Msg decoding
  // ============

  private void decodeBody_V123(ByteArrayScanner scanner)
      throws DataFormatException
  {
    parentEntryUUID = scanner.nextString();
    encodedAttributes = scanner.remainingBytes();
  }

  private void decodeBody_V4(ByteArrayScanner scanner)
      throws DataFormatException
  {
    parentEntryUUID = scanner.nextString();

    final int attrLen = scanner.nextIntUTF8();
    encodedAttributes = scanner.nextByteArray(attrLen);
    scanner.skipZeroSeparator();

    final int eclAttrLen = scanner.nextIntUTF8();
    encodedEclIncludes = scanner.nextByteArray(eclAttrLen);
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    if (protocolVersion >= ProtocolVersion.REPLICATION_PROTOCOL_V1)
    {
      return "AddMsg content: " +
        " protocolVersion: " + protocolVersion +
        " dn: " + dn +
        " csn: " + csn +
        " uniqueId: " + entryUUID +
        " assuredFlag: " + assuredFlag +
        (protocolVersion >= ProtocolVersion.REPLICATION_PROTOCOL_V2 ?
          " assuredMode: " + assuredMode +
          " safeDataLevel: " + safeDataLevel
          : "");
    }
    return "!!! Unknown version: " + protocolVersion + "!!!";
  }

  /**
   * Add the specified attribute/attribute value in the entry contained
   * in this AddMsg.
   *
   * @param name  The name of the attribute to add.
   * @param value The value of the attribute to add.
   * @throws DecodeException When this Msg is not valid.
   */
  public void addAttribute(String name, String value) throws DecodeException
  {
    ByteStringBuilder byteBuilder = new ByteStringBuilder();
    byteBuilder.appendBytes(encodedAttributes);

    ASN1Writer writer = ASN1.getWriter(byteBuilder);

    try
    {
      new LDAPAttribute(name, value).write(writer);

      encodedAttributes = byteBuilder.toByteArray();
    }
    catch(Exception e)
    {
      // DO SOMETHING
    }
  }

  /**
   * Get the attributes of this add msg.
   * @throws LDAPException In case of LDAP decoding exception
   * @throws DecodeException In case of ASN1 decoding exception
   * @return the list of attributes
   */
  public List<Attribute> getAttributes() throws LDAPException, DecodeException
  {
    return decodeAttributes(encodedAttributes);
  }

  /**
   * Set the parent unique id of this add msg.
   *
   * @param entryUUID the parent unique id.
   */
  public void setParentEntryUUID(String entryUUID)
  {
    parentEntryUUID = entryUUID;
  }

  /**
   * Get the parent unique id of this add msg.
   * @return the parent unique id.
   */
  public String getParentEntryUUID()
  {
    return parentEntryUUID;
  }

  /** {@inheritDoc} */
  @Override
  public int size()
  {
    return encodedAttributes.length + encodedEclIncludes.length + headerSize();
  }

}
