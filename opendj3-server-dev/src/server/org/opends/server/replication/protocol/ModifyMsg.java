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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.replication.protocol;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.zip.DataFormatException;

import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.CSN;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.operation.PostOperationModifyOperation;

import static org.opends.server.replication.protocol.OperationContext.*;

/**
 * LocalizableMessage used to send Modify information.
 */
public class ModifyMsg extends ModifyCommonMsg
{
  /**
   * Creates a new Modify message from a ModifyOperation.
   *
   * @param op The operation to use for building the message
   */
  public ModifyMsg(PostOperationModifyOperation op)
  {
    super((OperationContext) op.getAttachment(OperationContext.SYNCHROCONTEXT),
          op.getEntryDN());
    encodedMods = encodeMods(op.getModifications());
  }

  /**
   * Creates a new Modify message using the provided information.
   *
   * @param csn The CSN for the operation.
   * @param dn           The baseDN of the operation.
   * @param mods         The mod of the operation.
   * @param entryUUID    The unique id of the entry on which the modification
   *                     needs to apply.
   */
  public ModifyMsg(CSN csn, DN dn, List<Modification> mods, String entryUUID)
  {
    super(new ModifyContext(csn, entryUUID), dn);
    this.encodedMods = encodeMods(mods);
  }

  /**
   * Creates a new Modify message from a byte[].
   *
   * @param in The byte[] from which the operation must be read.
   * @throws DataFormatException If the input byte[] is not a valid ModifyMsg
   * @throws UnsupportedEncodingException If UTF8 is not supported by the JVM.
   */
  public ModifyMsg(byte[] in) throws DataFormatException,
                                     UnsupportedEncodingException
  {
    // Decode header
    byte[] allowedPduTypes = new byte[2];
    allowedPduTypes[0] = MSG_TYPE_MODIFY;
    allowedPduTypes[1] = MSG_TYPE_MODIFY_V1;
    int pos = decodeHeader(allowedPduTypes, in);

    // protocol version has been read as part of the header
    if (protocolVersion <= 3)
      decodeBody_V123(in, pos);
    else
      decodeBody_V4(in, pos);

    if (protocolVersion==ProtocolVersion.getCurrentVersion())
    {
      bytes = in;
    }

  }

  /**
   * Creates a new Modify message from a V1 byte[].
   *
   * @param in The byte[] from which the operation must be read.
   * @throws DataFormatException If the input byte[] is not a valid ModifyMsg
   * @throws UnsupportedEncodingException If UTF8 is not supported by the JVM.
   *
   * @return The created ModifyMsg.
   */
  public static ModifyMsg createV1(byte[] in) throws DataFormatException,
                                     UnsupportedEncodingException
  {
    ModifyMsg msg = new ModifyMsg(in);

    // bytes is only for current version (of the protocol) bytes !
    msg.bytes = null;

    return msg;
  }

  /** {@inheritDoc} */
  @Override
  public ModifyOperation createOperation(InternalClientConnection connection,
      DN newDN) throws LDAPException, IOException, DataFormatException
  {
    if (newDN == null)
      newDN = getDN();

    List<RawModification> ldapmods = decodeRawMods(encodedMods);

    ModifyOperation mod = new ModifyOperationBasis(connection,
        InternalClientConnection.nextOperationID(),
        InternalClientConnection.nextMessageID(), null,
        ByteString.valueOf(newDN.toString()), ldapmods);
    ModifyContext ctx = new ModifyContext(getCSN(), getEntryUUID());
    mod.setAttachment(SYNCHROCONTEXT, ctx);
    return mod;
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    if (protocolVersion >= ProtocolVersion.REPLICATION_PROTOCOL_V1)
    {
      return "ModifyMsg content: " +
        " protocolVersion: " + protocolVersion +
        " dn: " + dn +
        " csn: " + csn +
        " uniqueId: " + entryUUID +
        " assuredFlag: " + assuredFlag +
        (protocolVersion >= ProtocolVersion.REPLICATION_PROTOCOL_V2 ?
          " assuredMode: " + assuredMode +
          " safeDataLevel: " + safeDataLevel +
          " size: " + encodedMods.length
          : "");
      /* Do not append mods, they can be too long */
    }
    return "!!! Unknown version: " + protocolVersion + "!!!";
  }

  /** {@inheritDoc} */
  @Override
  public int size()
  {
    // The ModifyMsg can be very large when added or deleted attribute
    // values are very large.
    // We therefore need to count the whole encoded msg.
    return encodedMods.length + encodedEclIncludes.length + headerSize();
  }

  // ============
  // Msg Encoding
  // ============

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes_V1() throws UnsupportedEncodingException
  {
    /* encode the header in a byte[] large enough to also contain the mods */
    byte[] encodedMsg = encodeHeader_V1(MSG_TYPE_MODIFY_V1, encodedMods.length +
      1);

    /* add the mods */
    int pos = encodedMsg.length - (encodedMods.length + 1);
    addByteArray(encodedMods, encodedMsg, pos);

    return encodedMsg;
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes_V23() throws UnsupportedEncodingException
  {
    // Encoding V2 / V3

    /* encode the header in a byte[] large enough to also contain mods */
    byte[] encodedMsg = encodeHeader(MSG_TYPE_MODIFY, encodedMods.length + 1,
        ProtocolVersion.REPLICATION_PROTOCOL_V3);

    /* add the mods */
    int pos = encodedMsg.length - (encodedMods.length + 1);
    addByteArray(encodedMods, encodedMsg, pos);

    return encodedMsg;
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes_V45(short reqProtocolVersion)
      throws UnsupportedEncodingException
  {
    int bodyLength = 0;
    byte[] byteModsLen =
      String.valueOf(encodedMods.length).getBytes("UTF-8");
    bodyLength += byteModsLen.length + 1;
    bodyLength += encodedMods.length + 1;

    byte[] byteEntryAttrLen =
      String.valueOf(encodedEclIncludes.length).getBytes("UTF-8");
    bodyLength += byteEntryAttrLen.length + 1;
    bodyLength += encodedEclIncludes.length + 1;

    /* encode the header in a byte[] large enough to also contain the mods */
    byte [] encodedMsg = encodeHeader(MSG_TYPE_MODIFY, bodyLength,
        reqProtocolVersion);

    int pos = encodedMsg.length - bodyLength;
    pos = addByteArray(byteModsLen, encodedMsg, pos);
    pos = addByteArray(encodedMods, encodedMsg, pos);
    pos = addByteArray(byteEntryAttrLen, encodedMsg, pos);
    pos = addByteArray(encodedEclIncludes, encodedMsg, pos);
    return encodedMsg;
  }

  // ============
  // Msg decoding
  // ============

  private void decodeBody_V123(byte[] in, int pos)
  throws DataFormatException
  {
    // Read and store the mods, in encoded form
    // all the remaining bytes but the terminating 0 */
    int length = in.length - pos - 1;
    encodedMods = new byte[length];
    try
    {
      System.arraycopy(in, pos, encodedMods, 0, length);
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

  private void decodeBody_V4(byte[] in, int pos)
  throws DataFormatException, UnsupportedEncodingException
  {
    // Read mods len
    int length = getNextLength(in, pos);
    int modsLen = Integer.valueOf(new String(in, pos, length,"UTF-8"));
    pos += length + 1;

    // Read/Don't decode mods
    this.encodedMods = new byte[modsLen];
    try
    {
      System.arraycopy(in, pos, encodedMods, 0, modsLen);
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
    pos += modsLen + 1;

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
}
