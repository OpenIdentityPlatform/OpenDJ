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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.protocol;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.zip.DataFormatException;

import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.Attribute;
import org.opends.server.types.ByteSequenceReader;
import org.opends.server.types.ByteString;
import org.opends.server.types.ByteStringBuilder;
import org.opends.server.types.LDAPException;
import org.opends.server.types.Operation;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.operation.PostOperationAddOperation;
import org.opends.server.types.operation.PostOperationDeleteOperation;
import org.opends.server.types.operation.PostOperationModifyDNOperation;
import org.opends.server.types.operation.PostOperationModifyOperation;
import org.opends.server.types.operation.PostOperationOperation;

/**
 * Abstract class that must be extended to define a message
 * used for sending Updates between servers.
 */
public abstract class LDAPUpdateMsg extends UpdateMsg
{
  /**
   * The DN on which the update was originally done.
   */
  protected String dn = null;

  /**
   * The entryUUID of the entry that was updated.
   */
  protected String entryUUID;

  /**
   * Encoded form of the LDAPUpdateMsg.
   */
  protected byte[] bytes = null;

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
  public LDAPUpdateMsg(OperationContext ctx, String dn)
  {
    this.protocolVersion = ProtocolVersion.getCurrentVersion();
    this.changeNumber = ctx.getChangeNumber();
    this.entryUUID = ctx.getEntryUUID();
    this.dn = dn;
  }

  /**
   * Creates a new UpdateMessage with the given information.
   *
   * @param cn        The ChangeNumber of the operation for which the
   *                  UpdateMessage is created.
   * @param entryUUID The Unique identifier of the entry that is updated
   *                  by the operation for which the UpdateMessage is created.
   * @param dn        The DN of the entry on which the change
   *                  that caused the creation of this object happened
   */
  public LDAPUpdateMsg(ChangeNumber cn, String entryUUID, String dn)
  {
    this.protocolVersion = ProtocolVersion.getCurrentVersion();
    this.changeNumber = cn;
    this.entryUUID = entryUUID;
    this.dn = dn;
  }

  /**
   * Generates an Update Message with the provided information.
   *
   * @param op The operation for which the message must be created.
   * @return The generated message.
   */
  public static LDAPUpdateMsg generateMsg(PostOperationOperation op)
  {
    LDAPUpdateMsg msg = null;
    switch (op.getOperationType())
    {
    case MODIFY :
      msg = new ModifyMsg((PostOperationModifyOperation) op);
      break;

    case ADD:
      msg = new AddMsg((PostOperationAddOperation) op);
      break;

    case DELETE :
      msg = new DeleteMsg((PostOperationDeleteOperation) op);
      break;

    case MODIFY_DN :
      msg = new ModifyDNMsg( (PostOperationModifyDNOperation) op);
      break;
    }

    return msg;
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
      String newDn) throws LDAPException, ASN1Exception, DataFormatException;


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
   *
   * @throws UnsupportedEncodingException when encoding fails.
   */
  public void encode() throws UnsupportedEncodingException
  {
    bytes = getBytes(ProtocolVersion.getCurrentVersion());
  }

  /**
   * Encode the common header for all the UpdateMsg. This uses the current
   * protocol version.
   *
   * @param type the type of UpdateMsg to encode.
   * @param additionalLength additional length needed to encode the remaining
   *                         part of the UpdateMsg.
   * @param version The ProtocolVersion to use when encoding.
   * @return a byte array containing the common header and enough space to
   *         encode the remaining bytes of the UpdateMsg as was specified
   *         by the additionalLength.
   *         (byte array length = common header length + additionalLength)
   * @throws UnsupportedEncodingException if UTF-8 is not supported.
   */
  @Override
  public byte[] encodeHeader(byte type, int additionalLength, short version)
    throws UnsupportedEncodingException
  {
    byte[] byteDn = dn.getBytes("UTF-8");
    byte[] changeNumberByte =
      this.getChangeNumber().toString().getBytes("UTF-8");
    byte[] byteEntryuuid = getEntryUUID().getBytes("UTF-8");

    /* The message header is stored in the form :
     * <operation type><protocol version><changenumber><dn><entryuuid><assured>
     * <assured mode> <safe data level>
     * the length of result byte array is therefore :
     *   1 + 1 + change number length + 1 + dn length + 1 + uuid length + 1 + 1
     *   + 1 + 1 + additional_length
     */
    int length = 8 + changeNumberByte.length + byteDn.length
                 + byteEntryuuid.length + additionalLength;

    byte[] encodedMsg = new byte[length];

    /* put the type of the operation */
    encodedMsg[0] = type;

    /* put the protocol version */
    encodedMsg[1] = (byte) version;
    int pos = 2;

    /* Put the ChangeNumber */
    pos = addByteArray(changeNumberByte, encodedMsg, pos);

    /* Put the DN and a terminating 0 */
    pos = addByteArray(byteDn, encodedMsg, pos);

    /* Put the entry uuid and a terminating 0 */
    pos = addByteArray(byteEntryuuid, encodedMsg, pos);

    /* Put the assured flag */
    encodedMsg[pos++] = (assuredFlag ? (byte) 1 : 0);

    /* Put the assured mode */
    encodedMsg[pos++] = assuredMode.getValue();

    /* Put the safe data level */
    encodedMsg[pos++] = safeDataLevel;

    return encodedMsg;
  }

  /**
   * Encode the common header for all the UpdateMessage. This uses the version
   * 1 of the replication protocol (used for compatibility purpose).
   *
   * @param type the type of UpdateMessage to encode.
   * @param additionalLength additional length needed to encode the remaining
   *                         part of the UpdateMessage.
   * @return a byte array containing the common header and enough space to
   *         encode the remaining bytes of the UpdateMessage as was specified
   *         by the additionalLength.
   *         (byte array length = common header length + additionalLength)
   * @throws UnsupportedEncodingException if UTF-8 is not supported.
   */
  public byte[] encodeHeader_V1(byte type, int additionalLength)
    throws UnsupportedEncodingException
  {
    byte[] byteDn = dn.getBytes("UTF-8");
    byte[] changeNumberByte =
      this.getChangeNumber().toString().getBytes("UTF-8");
    byte[] byteEntryuuid = getEntryUUID().getBytes("UTF-8");

    /* The message header is stored in the form :
     * <operation type><changenumber><dn><assured><entryuuid><change>
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
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes(short reqProtocolVersion)
    throws UnsupportedEncodingException
  {
    if (reqProtocolVersion == ProtocolVersion.REPLICATION_PROTOCOL_V1)
    {
      return getBytes_V1();
    }
    else if (reqProtocolVersion <= ProtocolVersion.REPLICATION_PROTOCOL_V3)
    {
      return getBytes_V23();
    }
    else
    {
      // Encode in the current protocol version
      if (bytes == null)
      {
        // this is the current version of the protocol
        bytes = getBytes_V45(reqProtocolVersion);
      }
      return bytes;
    }
  }

  /**
   * Get the byte array representation of this Message. This uses the version
   * 1 of the replication protocol (used for compatibility purpose).
   *
   * @return The byte array representation of this Message.
   *
   * @throws UnsupportedEncodingException  When the encoding of the message
   *         failed because the UTF-8 encoding is not supported.
   */
  public abstract byte[] getBytes_V1() throws UnsupportedEncodingException;

  /**
   * Get the byte array representation of this Message. This uses the version
   * 2 of the replication protocol (used for compatibility purpose).
   *
   * @return The byte array representation of this Message.
   *
   * @throws UnsupportedEncodingException  When the encoding of the message
   *         failed because the UTF-8 encoding is not supported.
   */
  public abstract byte[] getBytes_V23() throws UnsupportedEncodingException;


  /**
   * Get the byte array representation of this Message. This uses the provided
   * version number which must be version 4 or newer.
   * @param reqProtocolVersion TODO
   *
   * @return The byte array representation of this Message.
   *
   * @throws UnsupportedEncodingException  When the encoding of the message
   *         failed because the UTF-8 encoding is not supported.
   */
  public abstract byte[] getBytes_V45(short reqProtocolVersion)
      throws UnsupportedEncodingException;


  /**
   * Encode a list of attributes.
   */
   static private byte[] encodeAttributes(Collection<Attribute> attributes)
   {
     if (attributes==null)
       return new byte[0];
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
   * Decode the Header part of this Update Message, and check its type.
   *
   * @param types The allowed types of this Update Message.
   * @param encodedMsg the encoded form of the UpdateMsg.
   * @return the position at which the remaining part of the message starts.
   * @throws DataFormatException if the encodedMsg does not contain a valid
   *         common header.
   */
   public int decodeHeader(byte[] types, byte[] encodedMsg)
                          throws DataFormatException
   {
     /* first byte is the type */
     boolean foundMatchingType = false;
     for (byte type : types)
     {
       if (type == encodedMsg[0])
       {
         foundMatchingType = true;
         break;
       }
     }
     if (!foundMatchingType)
       throw new DataFormatException("byte[] is not a valid update msg: "
           + encodedMsg[0]);

     /*
      * For older protocol version PDUs, decode the matching version header
      * instead.
      */
     if ((encodedMsg[0] == MSG_TYPE_ADD_V1) ||
         (encodedMsg[0] == MSG_TYPE_DELETE_V1) ||
         (encodedMsg[0] == MSG_TYPE_MODIFYDN_V1) ||
         (encodedMsg[0] == MSG_TYPE_MODIFY_V1))
     {
       return decodeHeader_V1(encodedMsg);
     }

     /* read the protocol version */
     protocolVersion = encodedMsg[1];

     try
     {
       /* Read the changeNumber */
       int pos = 2;
       int length = getNextLength(encodedMsg, pos);
       String changeNumberStr = new String(encodedMsg, pos, length, "UTF-8");
       pos += length + 1;
       changeNumber = new ChangeNumber(changeNumberStr);

       /* Read the dn */
       length = getNextLength(encodedMsg, pos);
       dn = new String(encodedMsg, pos, length, "UTF-8");
       pos += length + 1;

       /* Read the entryuuid */
       length = getNextLength(encodedMsg, pos);
       entryUUID = new String(encodedMsg, pos, length, "UTF-8");
       pos += length + 1;

       /* Read the assured information */
       assuredFlag = encodedMsg[pos++] == 1;

       /* Read the assured mode */
       assuredMode = AssuredMode.valueOf(encodedMsg[pos++]);

       /* Read the safe data level */
       safeDataLevel = encodedMsg[pos++];

       return pos;
     } catch (UnsupportedEncodingException e)
     {
       throw new DataFormatException("UTF-8 is not supported by this jvm.");
     } catch (IllegalArgumentException e)
     {
       throw new DataFormatException(e.getMessage());
     }
  }

  /**
   * Decode the Header part of this Update Message, and check its type. This
   * uses the version 1 of the replication protocol (used for compatibility
   * purpose).
   *
   * @param encodedMsg the encoded form of the UpdateMessage.
   * @return the position at which the remaining part of the message starts.
   * @throws DataFormatException if the encodedMsg does not contain a valid
   *         common header.
   */
  public int decodeHeader_V1(byte[] encodedMsg)
                          throws DataFormatException
  {
    if ((encodedMsg[0] != MSG_TYPE_ADD_V1) &&
      (encodedMsg[0] != MSG_TYPE_DELETE_V1) &&
      (encodedMsg[0] != MSG_TYPE_MODIFYDN_V1) &&
      (encodedMsg[0] != MSG_TYPE_MODIFY_V1))
      throw new DataFormatException("byte[] is not a valid update msg: expected"
        + " a V1 PDU, received: " + encodedMsg[0]);

    // Force version to V1 (other new parameters take their default values
    // (assured stuff...))
    protocolVersion = ProtocolVersion.REPLICATION_PROTOCOL_V1;

    try
    {
      /* read the changeNumber */
      int pos = 1;
      int length = getNextLength(encodedMsg, pos);
      String changeNumberStr = new String(encodedMsg, pos, length, "UTF-8");
      pos += length + 1;
      changeNumber = new ChangeNumber(changeNumberStr);

      /* read the assured information */
      assuredFlag = encodedMsg[pos++] == 1;

      /* read the dn */
      length = getNextLength(encodedMsg, pos);
      dn = new String(encodedMsg, pos, length, "UTF-8");
      pos += length + 1;

      /* read the entryuuid */
      length = getNextLength(encodedMsg, pos);
      entryUUID = new String(encodedMsg, pos, length, "UTF-8");
      pos += length + 1;

      return pos;
    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * Return the number of bytes used by this message.
   *
   * @return The number of bytes used by this message.
   */
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
   * @throws ASN1Exception when it occurs.
   */
  public ArrayList<RawAttribute> decodeRawAttributes(byte[] in)
  throws LDAPException, ASN1Exception
  {
    ArrayList<RawAttribute> rattr = new ArrayList<RawAttribute>();
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
   * @throws ASN1Exception when it occurs.
   */
  public ArrayList<Attribute> decodeAttributes(byte[] in)
  throws LDAPException, ASN1Exception
  {
    ArrayList<Attribute> lattr = new ArrayList<Attribute>();
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
