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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.protocol;

import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.DirectoryServer;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;

import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.plugin.EntryHistorical;
import org.opends.server.types.*;
import org.opends.server.types.operation.PostOperationAddOperation;

import static org.opends.server.replication.protocol.OperationContext.*;
import static org.opends.server.util.StaticUtils.toLowerCase;

/**
 * This class is used to exchange Add operation between LDAP servers
 * and replication servers.
 */
public class AddMsg extends LDAPUpdateMsg
{
  // Attributes are managed encoded
  private byte[] encodedAttributes;

  // Parent is managed decoded
  private String parentEntryUUID;

  /**
   * Creates a new AddMessage.
   * @param op the operation to use when creating the message
   */
  public AddMsg(PostOperationAddOperation op)
  {
    super((AddContext) op.getAttachment(SYNCHROCONTEXT),
          op.getRawEntryDN().toString());

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
   * @param cn                    ChangeNumber of the add.
   * @param dn                    DN of the added entry.
   * @param entryUUID             The Unique identifier of the added entry.
   * @param parentEntryUUID       The unique Id of the parent of the added
   *                              entry.
   * @param objectClasses           objectclass of the added entry.
   * @param userAttributes        user attributes of the added entry.
   * @param operationalAttributes operational attributes of the added entry.
   */
  public AddMsg(ChangeNumber cn,
                String dn,
                String entryUUID,
                String parentEntryUUID,
                Map<ObjectClass, String> objectClasses,
                Map<AttributeType,List<Attribute>> userAttributes,
                Map<AttributeType,List<Attribute>> operationalAttributes)
  {
    super (cn, entryUUID, dn);

    // Stores parentUniqueID not encoded
    this.parentEntryUUID = parentEntryUUID;

    // Stores attributes encoded
    this.encodedAttributes = encodeAttributes(objectClasses, userAttributes,
        operationalAttributes);
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
   * @throws UnsupportedEncodingException If UTF8 is not supported by the jvm
   */
  public AddMsg(byte[] in) throws DataFormatException,
                                  UnsupportedEncodingException
  {
    byte[] allowedPduTypes = new byte[2];
    allowedPduTypes[0] = MSG_TYPE_ADD;
    allowedPduTypes[1] = MSG_TYPE_ADD_V1;
    int pos = decodeHeader(allowedPduTypes, in);

    // protocol version has been read as part of the header
    if (protocolVersion <= 3)
      decodeBody_V123(in, pos);
    else
    {
      decodeBody_V4(in, pos);
    }
    if (protocolVersion==ProtocolVersion.getCurrentVersion())
    {
      bytes = in;
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
    ArrayList<RawAttribute> attr = decodeRawAttributes(encodedAttributes);

    AddOperationBasis add =  new AddOperationBasis(connection,
        InternalClientConnection.nextOperationID(),
        InternalClientConnection.nextMessageID(), null,
        ByteString.valueOf(newDn), attr);
    AddContext ctx = new AddContext(getChangeNumber(), getEntryUUID(),
        parentEntryUUID);
    add.setAttachment(SYNCHROCONTEXT, ctx);
    return add;
  }

  // ============
  // Msg encoding
  // ============

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes_V1() throws UnsupportedEncodingException
  {
    int bodyLength = encodedAttributes.length;
    byte[] byteParentId = null;
    if (parentEntryUUID != null)
    {
      byteParentId = parentEntryUUID.getBytes("UTF-8");
      bodyLength += byteParentId.length + 1;
    }
    else
    {
      bodyLength += 1;
    }

    /* encode the header in a byte[] large enough to also contain the mods */
    byte [] resultByteArray = encodeHeader_V1(MSG_TYPE_ADD_V1, bodyLength);

    int pos = resultByteArray.length - bodyLength;

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
  public byte[] getBytes_V23() throws UnsupportedEncodingException
  {
    // Put together the different encoded pieces
    int bodyLength = encodedAttributes.length;

    // Compute the total length of the body
    byte[] byteParentId = null;
    if (parentEntryUUID != null)
    {
      // Encode parentID now to get the length of the encoded bytes
      byteParentId = parentEntryUUID.getBytes("UTF-8");
      bodyLength += byteParentId.length + 1;
    }
    else
    {
      bodyLength += 1;
    }

    /* encode the header in a byte[] large enough to also contain the mods */
    byte [] resultByteArray = encodeHeader(MSG_TYPE_ADD, bodyLength,
          ProtocolVersion.REPLICATION_PROTOCOL_V3);

    int pos = resultByteArray.length - bodyLength;

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
  public byte[] getBytes_V45(short reqProtocolVersion)
      throws UnsupportedEncodingException
  {
    // Put together the different encoded pieces
    int bodyLength = 0;

    // Compute the total length of the body
    byte[] byteParentId = null;
    if (parentEntryUUID != null)
    {
      // Encode parentID now to get the length of the encoded bytes
      byteParentId = parentEntryUUID.getBytes("UTF-8");
      bodyLength += byteParentId.length + 1;
    }
    else
    {
      bodyLength += 1;
    }

    byte[] byteAttrLen =
      String.valueOf(encodedAttributes.length).getBytes("UTF-8");
    bodyLength += byteAttrLen.length + 1;
    bodyLength += encodedAttributes.length + 1;

    byte[] byteEntryAttrLen =
      String.valueOf(encodedEclIncludes.length).getBytes("UTF-8");
    bodyLength += byteEntryAttrLen.length + 1;
    bodyLength += encodedEclIncludes.length + 1;

    /* encode the header in a byte[] large enough to also contain the mods */
    byte [] encodedMsg = encodeHeader(MSG_TYPE_ADD, bodyLength,
        reqProtocolVersion);

    int pos = encodedMsg.length - bodyLength;
    if (byteParentId != null)
      pos = addByteArray(byteParentId, encodedMsg, pos);
    else
      encodedMsg[pos++] = 0;
    pos = addByteArray(byteAttrLen, encodedMsg, pos);
    pos = addByteArray(encodedAttributes, encodedMsg, pos);
    pos = addByteArray(byteEntryAttrLen, encodedMsg, pos);
    pos = addByteArray(encodedEclIncludes, encodedMsg, pos);
    return encodedMsg;
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
      AttributeBuilder builder = new AttributeBuilder(
          DirectoryServer.getObjectClassAttributeType());
      builder.setInitialCapacity(objectClasses.size());
      for (String s : objectClasses.values())
      {
        builder.add(AttributeValues.create(ByteString.valueOf(s),
            ByteString.valueOf(toLowerCase(s))));
      }
      Attribute attr = builder.toAttribute();

      new LDAPAttribute(attr).write(writer);

      // Encode the user attributes (AttributeList).
      for (List<Attribute> list : userAttributes.values())
      {
        for (Attribute a : list)
        {
          if (!EntryHistorical.isHistoricalAttribute(a))
            if (!a.isVirtual())
              new LDAPAttribute(a).write(writer);
        }
      }

      // Encode the operational attributes (AttributeList).
      for (List<Attribute> list : operationalAttributes.values())
      {
        for (Attribute a : list)
        {
          if (!EntryHistorical.isHistoricalAttribute(a))
            if (!a.isVirtual())
              new LDAPAttribute(a).write(writer);
        }
      }
    }
    catch(Exception e)
    {
      // TODO: DO something
    }

    // Encode the sequence.
    return byteBuilder.toByteArray();
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
        new LDAPAttribute(a).write(writer);

      if (operationalAttributes != null)
        for (Attribute a : operationalAttributes)
          new LDAPAttribute(a).write(writer);
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

  private void decodeBody_V123(byte[] in, int pos)
  throws DataFormatException, UnsupportedEncodingException
  {
    // read the parent unique Id
    int length = getNextLength(in, pos);
    if (length != 0)
    {
      parentEntryUUID = new String(in, pos, length, "UTF-8");
      pos += length + 1;
    }
    else
    {
      parentEntryUUID = null;
      pos += 1;
    }

    // Read/Don't decode attributes : all the remaining bytes
    encodedAttributes = new byte[in.length-pos];
    int i =0;
    while (pos<in.length)
    {
      encodedAttributes[i++] = in[pos++];
    }
  }

  private void decodeBody_V4(byte[] in, int pos)
  throws DataFormatException, UnsupportedEncodingException
  {
    // read the parent unique Id
    int length = getNextLength(in, pos);
    if (length != 0)
    {
      parentEntryUUID = new String(in, pos, length, "UTF-8");
      pos += length + 1;
    }
    else
    {
      parentEntryUUID = null;
      pos += 1;
    }

    // Read attr len
    length = getNextLength(in, pos);
    int attrLen = Integer.valueOf(new String(in, pos, length,"UTF-8"));
    pos += length + 1;

    // Read/Don't decode attributes
    this.encodedAttributes = new byte[attrLen];
    try
    {
      System.arraycopy(in, pos, encodedAttributes, 0, attrLen);
    } catch (IndexOutOfBoundsException e)
    {
      throw new DataFormatException(e.getMessage());
    } catch (ArrayStoreException e)
    {
      throw new DataFormatException(e.getMessage());
    } catch (NullPointerException e)
    {
      throw new DataFormatException(e.getMessage());
    }
    pos += attrLen + 1;

    // Read ecl attr len
    length = getNextLength(in, pos);
    int eclAttrLen = Integer.valueOf(new String(in, pos, length,"UTF-8"));
    pos += length + 1;

    // Read/Don't decode entry attributes
    encodedEclIncludes = new byte[eclAttrLen];
    try
    {
      System.arraycopy(in, pos, encodedEclIncludes, 0, eclAttrLen);
    } catch (IndexOutOfBoundsException e)
    {
      throw new DataFormatException(e.getMessage());
    } catch (ArrayStoreException e)
    {
      throw new DataFormatException(e.getMessage());
    } catch (NullPointerException e)
    {
      throw new DataFormatException(e.getMessage());
    }
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
        " protocolVersion: " + protocolVersion +
        " dn: " + dn +
        " changeNumber: " + changeNumber +
        " uniqueId: " + entryUUID +
        " assuredFlag: " + assuredFlag;
    }
    if (protocolVersion >= ProtocolVersion.REPLICATION_PROTOCOL_V2)
    {
      return "AddMsg content: " +
        " protocolVersion: " + protocolVersion +
        " dn: " + dn +
        " changeNumber: " + changeNumber +
        " uniqueId: " + entryUUID +
        " assuredFlag: " + assuredFlag +
        " assuredMode: " + assuredMode +
        " safeDataLevel: " + safeDataLevel;
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
    ByteStringBuilder byteBuilder = new ByteStringBuilder();
    byteBuilder.append(encodedAttributes);

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
   * @throws ASN1Exception In case of ASN1 decoding exception
   * @return the list of attributes
   */
  public List<Attribute> getAttributes() throws LDAPException, ASN1Exception
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

  /**
   * {@inheritDoc}
   */
  @Override
  public int size()
  {
    return encodedAttributes.length + + encodedEclIncludes.length
      + headerSize();
  }

}
