/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.protocol;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.zip.DataFormatException;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.CSN;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ByteSequenceReader;
import org.opends.server.types.operation.*;

/**
 * Abstract class that must be extended to define a message
 * used for sending Updates between servers.
 */
public abstract class LDAPUpdateMsg extends UpdateMsg
{
  /**
   * The DN on which the update was originally done.
   */
  protected DN dn;

  /**
   * The entryUUID of the entry that was updated.
   */
  protected String entryUUID;

  /**
   * Encoded form of the LDAPUpdateMsg.
   */
  protected byte[] bytes;

  /**
   * Encoded form of entry attributes.
   */
  protected byte[] encodedEclIncludes = new byte[0];

  /**
   * Creates a new UpdateMsg.
   */
  protected LDAPUpdateMsg()
  {
  }

  /**
   * Creates a new UpdateMsg with the given information.
   *
   * @param ctx The replication Context of the operation for which the
   *            update message must be created,.
   * @param dn The DN of the entry on which the change
   *           that caused the creation of this object happened
   */
  LDAPUpdateMsg(OperationContext ctx, DN dn)
  {
    this.protocolVersion = ProtocolVersion.getCurrentVersion();
    this.csn = ctx.getCSN();
    this.entryUUID = ctx.getEntryUUID();
    this.dn = dn;
  }

  /**
   * Creates a new UpdateMessage with the given information.
   *
   * @param csn       The CSN of the operation for which the
   *                  UpdateMessage is created.
   * @param entryUUID The Unique identifier of the entry that is updated
   *                  by the operation for which the UpdateMessage is created.
   * @param dn        The DN of the entry on which the change
   *                  that caused the creation of this object happened
   */
  LDAPUpdateMsg(CSN csn, String entryUUID, DN dn)
  {
    this.protocolVersion = ProtocolVersion.getCurrentVersion();
    this.csn = csn;
    this.entryUUID = entryUUID;
    this.dn = dn;
  }

  /**
   * Generates an Update message with the provided information.
   *
   * @param op The operation for which the message must be created.
   * @return The generated message.
   */
  public static LDAPUpdateMsg generateMsg(PostOperationOperation op)
  {
    switch (op.getOperationType())
    {
    case MODIFY :
      return new ModifyMsg((PostOperationModifyOperation) op);
    case ADD:
      return new AddMsg((PostOperationAddOperation) op);
    case DELETE :
      return new DeleteMsg((PostOperationDeleteOperation) op);
    case MODIFY_DN :
      return new ModifyDNMsg( (PostOperationModifyDNOperation) op);
    default:
      return null;
    }
  }

  /**
   * Get the DN on which the operation happened.
   *
   * @return The DN on which the operations happened.
   */
  public DN getDN()
  {
    return dn;
  }

  /**
   * Set the DN.
   * @param dn The dn that must now be used for this message.
   */
  public void setDN(DN dn)
  {
    this.dn = dn;
  }

  /**
   * Get the entryUUID of the entry on which the operation happened.
   *
   * @return The entryUUID of the entry on which the operation happened.
   */
  public String getEntryUUID()
  {
    return entryUUID;
  }

  /**
   * Create and Operation from the message.
   *
   * @param   conn connection to use when creating the message
   * @return  the created Operation
   * @throws  LDAPException In case of LDAP decoding exception.
   * @throws  IOException In case of ASN1 decoding exception.
   * @throws DataFormatException In case of bad msg format.
   */
  public Operation createOperation(InternalClientConnection conn)
      throws LDAPException, IOException, DataFormatException
  {
    return createOperation(conn, dn);
  }


  /**
   * Create and Operation from the message using the provided DN.
   *
   * @param   conn connection to use when creating the message.
   * @param   newDN the DN to use when creating the operation.
   * @return  the created Operation.
   * @throws  LDAPException In case of LDAP decoding exception.
   * @throws  IOException In case of ASN1 decoding exception.
   * @throws DataFormatException In case of bad msg format.
   */
  public abstract Operation createOperation(InternalClientConnection conn,
      DN newDN) throws LDAPException, IOException, DataFormatException;


  // ============
  // Msg encoding
  // ============

  /**
   * Do all the work necessary for the encoding.
   *
   * This is useful in case when one wants to perform this outside
   * of a synchronized portion of code.
   *
   * This method is not synchronized and therefore not MT safe.
   */
  public void encode()
  {
    bytes = getBytes(ProtocolVersion.getCurrentVersion());
  }

  /** {@inheritDoc} */
  @Override
  public ByteArrayBuilder encodeHeader(byte msgType, short protocolVersion)
  {
    /* The message header is stored in the form :
     * <operation type><protocol version><CSN><dn><entryuuid><assured>
     * <assured mode> <safe data level>
     */
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    builder.appendByte(msgType);
    builder.appendByte((byte) protocolVersion);
    builder.appendCSNUTF8(csn);
    builder.appendDN(dn);
    builder.appendString(entryUUID);
    builder.appendBoolean(assuredFlag);
    builder.appendByte(assuredMode.getValue());
    builder.appendByte(safeDataLevel);
    return builder;
  }

  /**
   * Encode the common header for all the UpdateMessage. This uses the version
   * 1 of the replication protocol (used for compatibility purpose).
   *
   * @param msgType the type of UpdateMessage to encode.
   * @return a byte array builder containing the common header
   */
  ByteArrayBuilder encodeHeader_V1(byte msgType)
  {
    /* The message header is stored in the form :
     * <operation type><CSN><dn><assured><entryuuid><change>
     */
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    builder.appendByte(msgType);
    builder.appendCSNUTF8(csn);
    builder.appendBoolean(assuredFlag);
    builder.appendDN(dn);
    builder.appendString(entryUUID);
    return builder;
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    if (protocolVersion == ProtocolVersion.REPLICATION_PROTOCOL_V1)
    {
      return getBytes_V1();
    }
    else if (protocolVersion <= ProtocolVersion.REPLICATION_PROTOCOL_V3)
    {
      return getBytes_V23();
    }
    else
    {
      // Encode in the current protocol version
      if (bytes == null)
      {
        // this is the current version of the protocol
        bytes = getBytes_V45(protocolVersion);
      }
      return bytes;
    }
  }

  /**
   * Get the byte array representation of this message. This uses the version
   * 1 of the replication protocol (used for compatibility purpose).
   *
   * @return The byte array representation of this message.
   */
  protected abstract byte[] getBytes_V1();

  /**
   * Get the byte array representation of this message. This uses the version
   * 2 of the replication protocol (used for compatibility purpose).
   *
   * @return The byte array representation of this message.
   */
  protected abstract byte[] getBytes_V23();

  /**
   * Get the byte array representation of this message. This uses the provided
   * version number which must be version 4 or newer.
   *
   * @param protocolVersion the actual protocol version to encode into
   * @return The byte array representation of this Message.
   */
  protected abstract byte[] getBytes_V45(short protocolVersion);

  /**
   * Encode a list of attributes.
   */
   private static byte[] encodeAttributes(Collection<Attribute> attributes)
   {
     if (attributes==null)
     {
       return new byte[0];
     }
     try
     {
       ByteStringBuilder byteBuilder = new ByteStringBuilder();
       ASN1Writer writer = ASN1.getWriter(byteBuilder);
       for (Attribute a : attributes)
       {
         new LDAPAttribute(a).write(writer);
       }
       return byteBuilder.toByteArray();
     }
     catch (Exception e)
     {
       return null;
     }
   }

  // ============
  // Msg decoding
  // ============

  /**
   * Decode the Header part of this Update message, and check its type.
   *
   * @param scanner the encoded form of the UpdateMsg.
   * @param allowedTypes The allowed types of this Update Message.
   * @throws DataFormatException if the encodedMsg does not contain a valid
   *         common header.
   */
  void decodeHeader(ByteArrayScanner scanner, byte... allowedTypes)
      throws DataFormatException
  {
    final byte msgType = scanner.nextByte();
    if (!isTypeAllowed(msgType, allowedTypes))
    {
      throw new DataFormatException("byte[] is not a valid update msg: "
          + msgType);
    }

    if (msgType == MSG_TYPE_ADD_V1
        || msgType == MSG_TYPE_DELETE_V1
        || msgType == MSG_TYPE_MODIFYDN_V1
        || msgType == MSG_TYPE_MODIFY_V1)
    {
      /*
       * For older protocol versions, decode the matching version header instead
       */
      // Force version to V1 (other new parameters take their default values
      // (assured stuff...))
      protocolVersion = ProtocolVersion.REPLICATION_PROTOCOL_V1;
      csn = scanner.nextCSNUTF8();
      assuredFlag = scanner.nextBoolean();
      dn = scanner.nextDN();
      entryUUID = scanner.nextString();
    }
    else
    {
      protocolVersion = scanner.nextByte();
      csn = scanner.nextCSNUTF8();
      dn = scanner.nextDN();
      entryUUID = scanner.nextString();
      assuredFlag = scanner.nextBoolean();
      assuredMode = AssuredMode.valueOf(scanner.nextByte());
      safeDataLevel = scanner.nextByte();
    }
  }

  private boolean isTypeAllowed(final byte msgType, byte... allowedTypes)
  {
    for (byte allowedType : allowedTypes)
    {
      if (msgType == allowedType)
      {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public abstract int size();

  /**
   * Return the number of bytes used by the header.
   * @return The number of bytes used by the header.
   */
  protected int headerSize()
  {
    return 100;    // 100 let's assume header size is 100
  }

  /**
   * Set a provided list of entry attributes.
   * @param entryAttrs  The provided list of entry attributes.
   */
  public void setEclIncludes(Collection<Attribute> entryAttrs)
  {
    this.encodedEclIncludes = encodeAttributes(entryAttrs);
  }

  /**
   * Returns the list of entry attributes.
   * @return The list of entry attributes.
   */
  public ArrayList<RawAttribute> getEclIncludes()
  {
    try
    {
      return decodeRawAttributes(this.encodedEclIncludes);
    }
    catch(Exception e)
    {
      return null;
    }
  }

  /**
   * Decode a provided byte array as a list of RawAttribute.
   * @param in The provided byte array.
   * @return The list of RawAttribute objects.
   * @throws LDAPException when it occurs.
   * @throws DecodeException when it occurs.
   */
  ArrayList<RawAttribute> decodeRawAttributes(byte[] in)
  throws LDAPException, DecodeException
  {
    ArrayList<RawAttribute> rattr = new ArrayList<>();
    try
    {
      ByteSequenceReader reader =
        ByteString.wrap(in).asReader();
      ASN1Reader asn1Reader = ASN1.getReader(reader);
      // loop on attributes
      while(asn1Reader.hasNextElement())
      {
        rattr.add(LDAPAttribute.decode(asn1Reader));
      }
      return rattr;
    }
    catch(Exception e)
    {
      return null;
    }
  }

  /**
   * Decode a provided byte array as a list of Attribute.
   * @param in The provided byte array.
   * @return The list of Attribute objects.
   * @throws LDAPException when it occurs.
   * @throws DecodeException when it occurs.
   */
  ArrayList<Attribute> decodeAttributes(byte[] in)
  throws LDAPException, DecodeException
  {
    ArrayList<Attribute> lattr = new ArrayList<>();
    try
    {
      ByteSequenceReader reader =
        ByteString.wrap(in).asReader();
      ASN1Reader asn1Reader = ASN1.getReader(reader);
      // loop on attributes
      while(asn1Reader.hasNextElement())
      {
        lattr.add(LDAPAttribute.decode(asn1Reader).toAttribute());
      }
      return lattr;
    }
    catch(Exception e)
    {
      return null;
    }
  }
}
