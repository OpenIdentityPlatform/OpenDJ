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

import java.io.IOException;
import java.util.List;
import java.util.zip.DataFormatException;

import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.CSN;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.LDAPException;
import org.opends.server.types.Modification;
import org.opends.server.types.RawModification;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.operation.PostOperationModifyOperation;

import static org.opends.server.replication.protocol.OperationContext.*;

/**
 * Message used to send Modify information.
 */
public class ModifyMsg extends ModifyCommonMsg
{
  /**
   * Creates a new Modify message from a ModifyOperation.
   *
   * @param op The operation to use for building the message
   */
  ModifyMsg(PostOperationModifyOperation op)
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
   */
  ModifyMsg(byte[] in) throws DataFormatException
  {
    final ByteArrayScanner scanner = new ByteArrayScanner(in);
    decodeHeader(scanner, MSG_TYPE_MODIFY, MSG_TYPE_MODIFY_V1);

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

  /**
   * Creates a new Modify message from a V1 byte[].
   *
   * @param in The byte[] from which the operation must be read.
   * @return The created ModifyMsg.
   * @throws DataFormatException If the input byte[] is not a valid ModifyMsg
   */
  static ModifyMsg createV1(byte[] in) throws DataFormatException
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
    {
      newDN = getDN();
    }

    List<RawModification> ldapmods = decodeRawMods(encodedMods);

    ModifyOperation mod = new ModifyOperationBasis(connection,
        InternalClientConnection.nextOperationID(),
        InternalClientConnection.nextMessageID(), null,
        ByteString.valueOfUtf8(newDN.toString()), ldapmods);
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
  public byte[] getBytes_V1()
  {
    final ByteArrayBuilder builder = encodeHeader_V1(MSG_TYPE_MODIFY_V1);
    builder.appendByteArray(encodedMods);
    return builder.toByteArray();
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes_V23()
  {
    final ByteArrayBuilder builder =
        encodeHeader(MSG_TYPE_MODIFY, ProtocolVersion.REPLICATION_PROTOCOL_V3);
    builder.appendByteArray(encodedMods);
    return builder.toByteArray();
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes_V45(short protocolVersion)
  {
    final ByteArrayBuilder builder =
        encodeHeader(MSG_TYPE_MODIFY, protocolVersion);
    builder.appendIntUTF8(encodedMods.length);
    builder.appendZeroTerminatedByteArray(encodedMods);
    builder.appendIntUTF8(encodedEclIncludes.length);
    builder.appendZeroTerminatedByteArray(encodedEclIncludes);
    return builder.toByteArray();
  }

  // ============
  // Msg decoding
  // ============

  private void decodeBody_V123(ByteArrayScanner scanner)
      throws DataFormatException
  {
    encodedMods = scanner.remainingBytes();
  }

  private void decodeBody_V4(ByteArrayScanner scanner)
      throws DataFormatException
  {
    final int modsLen = scanner.nextIntUTF8();
    this.encodedMods = scanner.nextByteArray(modsLen);
    scanner.skipZeroSeparator();

    final int eclAttrLen = scanner.nextIntUTF8();
    encodedEclIncludes = scanner.nextByteArray(eclAttrLen);
  }
}
