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
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.protocol;

import java.io.IOException;
import java.util.List;
import java.util.zip.DataFormatException;

import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.CSN;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.operation.PostOperationModifyDNOperation;

import static org.opends.server.replication.protocol.OperationContext.*;

/**
 * Message used to send Modify DN information.
 */
public class ModifyDNMsg extends ModifyCommonMsg
{
  private String newRDN;
  private String newSuperior;
  private boolean deleteOldRdn;
  private String newSuperiorEntryUUID;

  /**
   * Construct a new Modify DN message.
   *
   * @param operation The operation to use for building the message
   */
  public ModifyDNMsg(PostOperationModifyDNOperation operation)
  {
    super((OperationContext) operation.getAttachment(SYNCHROCONTEXT),
        operation.getEntryDN());

    encodedMods = encodeMods(operation.getModifications());

    ModifyDnContext ctx =
      (ModifyDnContext) operation.getAttachment(SYNCHROCONTEXT);
    newSuperiorEntryUUID = ctx.getNewSuperiorEntryUUID();

    deleteOldRdn = operation.deleteOldRDN();
    final ByteString rawNewSuperior = operation.getRawNewSuperior();
    newSuperior = rawNewSuperior != null ? rawNewSuperior.toString() : null;
    newRDN = operation.getRawNewRDN().toString();
  }

  /**
   * Construct a new Modify DN message (no mods).
   * Note: Keep this constructor version to support already written tests, not
   * using mods.
   *
   * @param dn The dn to use for building the message.
   * @param csn The CSN to use for building the message.
   * @param entryUUID          The unique id to use for building the message.
   * @param newSuperiorEntryUUID The new parent unique id to use for building
   *                     the message.
   * @param deleteOldRdn boolean indicating if old rdn must be deleted to use
   *                     for building the message.
   * @param newSuperior  The new Superior entry to use for building the message.
   * @param newRDN       The new Rdn to use for building the message.
   */
  public ModifyDNMsg(DN dn, CSN csn, String entryUUID,
                     String newSuperiorEntryUUID, boolean deleteOldRdn,
                     String newSuperior, String newRDN)
  {
    super(new ModifyDnContext(csn, entryUUID, newSuperiorEntryUUID), dn);

    this.newSuperiorEntryUUID = newSuperiorEntryUUID;
    this.deleteOldRdn = deleteOldRdn;
    this.newSuperior = newSuperior;
    this.newRDN = newRDN;
  }

  /**
   * Construct a new Modify DN message (with mods).
   *
   * @param dn The dn to use for building the message.
   * @param csn The CSNto use for building the message.
   * @param entryUUID The unique id to use for building the message.
   * @param newSuperiorEntryUUID The new parent unique id to use for building
   *                     the message.
   * @param deleteOldRdn boolean indicating if old rdn must be deleted to use
   *                     for building the message.
   * @param newSuperior  The new Superior entry to use for building the message.
   * @param newRDN       The new Rdn to use for building the message.
   * @param mods         The mod of the operation.
   */
  public ModifyDNMsg(DN dn, CSN csn, String entryUUID,
      String newSuperiorEntryUUID, boolean deleteOldRdn, String newSuperior,
      String newRDN, List<Modification> mods)
  {
    this(dn, csn, entryUUID, newSuperiorEntryUUID, deleteOldRdn,
        newSuperior, newRDN);
    this.encodedMods = encodeMods(mods);
  }

  /**
   * Creates a new ModifyDN message from a byte[].
   *
   * @param in The byte[] from which the operation must be read.
   * @throws DataFormatException The input byte[] is not a valid ModifyDNMsg.
   */
  ModifyDNMsg(byte[] in) throws DataFormatException
  {
    final ByteArrayScanner scanner = new ByteArrayScanner(in);
    decodeHeader(scanner, MSG_TYPE_MODIFYDN, MSG_TYPE_MODIFYDN_V1);

    if (protocolVersion <= 3)
    {
      decodeBody_V123(scanner, in[0]);
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
  public ModifyDNOperation createOperation(InternalClientConnection connection,
      DN newDN) throws LDAPException, IOException
  {
    ModifyDNOperation moddn =  new ModifyDNOperationBasis(connection,
        InternalClientConnection.nextOperationID(),
        InternalClientConnection.nextMessageID(), null,
        ByteString.valueOf(newDN.toString()),
        ByteString.valueOf(newRDN),
        deleteOldRdn,
        (newSuperior == null ? null : ByteString.valueOf(newSuperior)));

    for (Modification mod : decodeMods(encodedMods))
    {
      moddn.addModification(mod);
    }

    ModifyDnContext ctx = new ModifyDnContext(getCSN(), getEntryUUID(),
        newSuperiorEntryUUID);
    moddn.setAttachment(SYNCHROCONTEXT, ctx);
    return moddn;
  }

  // ============
  // Msg Encoding
  // ============

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes_V1()
  {
    final ByteArrayBuilder builder = encodeHeader_V1(MSG_TYPE_MODIFYDN_V1);
    builder.appendString(newRDN);
    builder.appendString(newSuperior);
    builder.appendString(newSuperiorEntryUUID);
    builder.appendBoolean(deleteOldRdn);
    return builder.toByteArray();
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes_V23()
  {
    final ByteArrayBuilder builder =
        encodeHeader(MSG_TYPE_MODIFYDN,ProtocolVersion.REPLICATION_PROTOCOL_V3);
    builder.appendString(newRDN);
    builder.appendString(newSuperior);
    builder.appendString(newSuperiorEntryUUID);
    builder.appendBoolean(deleteOldRdn);
    builder.appendZeroTerminatedByteArray(encodedMods);
    return builder.toByteArray();
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes_V45(short protocolVersion)
  {
    final ByteArrayBuilder builder =
        encodeHeader(MSG_TYPE_MODIFYDN, protocolVersion);
    builder.appendString(newRDN);
    builder.appendString(newSuperior);
    builder.appendString(newSuperiorEntryUUID);
    builder.appendBoolean(deleteOldRdn);
    builder.appendIntUTF8(encodedMods.length);
    builder.appendZeroTerminatedByteArray(encodedMods);
    builder.appendIntUTF8(encodedEclIncludes.length);
    builder.appendZeroTerminatedByteArray(encodedEclIncludes);
    return builder.toByteArray();
  }

  // ============
  // Msg decoding
  // ============

  private void decodeBody_V123(ByteArrayScanner scanner, byte msgType)
      throws DataFormatException
  {
    newRDN = scanner.nextString();
    newSuperior = scanner.nextString();
    newSuperiorEntryUUID = scanner.nextString();
    deleteOldRdn = scanner.nextBoolean();

    // For easiness (no additional method), simply compare PDU type to
    // know if we have to read the mods of V2
    if (msgType == MSG_TYPE_MODIFYDN)
    {
      encodedMods = scanner.remainingBytesZeroTerminated();
    }
  }

  private void decodeBody_V4(ByteArrayScanner scanner)
      throws DataFormatException
  {
    newRDN = scanner.nextString();
    newSuperior = scanner.nextString();
    newSuperiorEntryUUID = scanner.nextString();
    deleteOldRdn = scanner.nextBoolean();

    final int modsLen = scanner.nextIntUTF8();
    encodedMods = scanner.nextByteArray(modsLen);
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
      return "ModifyDNMsg content: " +
        " protocolVersion: " + protocolVersion +
        " dn: " + dn +
        " csn: " + csn +
        " uniqueId: " + entryUUID +
        " newRDN: " + newRDN +
        " newSuperior: " + newSuperior +
        " deleteOldRdn: " + deleteOldRdn +
        " assuredFlag: " + assuredFlag +
        (protocolVersion >= ProtocolVersion.REPLICATION_PROTOCOL_V2 ?
          " assuredMode: " + assuredMode +
          " safeDataLevel: " + safeDataLevel
          : "");
    }
    return "!!! Unknown version: " + protocolVersion + "!!!";
  }

  /**
   * Set the new superior.
   * @param string the new superior.
   */
  public void setNewSuperior(String string)
  {
    newSuperior = string;
  }

  /**
   * Get the new superior.
   *
   * @return The new superior.
   */
  public String getNewSuperior()
  {
    return newSuperior;
  }

  /**
   * Get the new superior id.
   *
   * @return The new superior id.
   */
  public String getNewSuperiorEntryUUID()
  {
    return newSuperiorEntryUUID;
  }

  /**
   * Get the delete old rdn option.
   *
   * @return The delete old rdn option.
   */
  public boolean deleteOldRdn()
  {
    return deleteOldRdn;
  }

  /**
   * Set the new superior id.
   *
   * @param newSup The new superior id.
   */
  public void setNewSuperiorEntryUUID(String newSup)
  {
    newSuperiorEntryUUID = newSup;
  }

  /**
   * Set the delete old rdn option.
   *
   * @param delete The delete old rdn option.
   */
  public void  setDeleteOldRdn(boolean delete)
  {
    deleteOldRdn = delete;
  }

  /**
   * Get the delete old rdn option.
   * @return true if delete old rdn option
   */
  public boolean getDeleteOldRdn()
  {
    return deleteOldRdn;
  }

  /**
   * Get the new RDN of this operation.
   *
   * @return The new RDN of this operation.
   */
  public String getNewRDN()
  {
    return newRDN;
  }

  /**
   * Set the new RDN of this operation.
   * @param newRDN the new RDN of this operation.
   */
  public void setNewRDN(String newRDN)
  {
    this.newRDN = newRDN;
  }

  /**
   * Computes and return the new DN that the entry should
   * have after this operation.
   *
   * @return the newDN.
   * @throws DirectoryException in case of decoding problems.
   */
  private DN computeNewDN() throws DirectoryException
  {
    if (newSuperior != null)
    {
      return DN.valueOf(newRDN + "," + newSuperior);
    }
    final DN parentDn = getDN().parent();
    return parentDn.child(RDN.decode(newRDN));
  }

  /**
   * Check if this MSG will change the DN of the target entry to be
   * the same as the dn given as a parameter.
   * @param targetDn the DN to use when checking if this MSG will change
   *                 the DN of the entry to a given DN.
   * @return A boolean indicating if the modify DN MSG will change the DN of
   *         the target entry to be the same as the dn given as a parameter.
   */
  public boolean newDNIsParent(DN targetDn)
  {
    try
    {
      DN newDN = computeNewDN();
      return newDN.isAncestorOf(targetDn);
    } catch (DirectoryException e)
    {
      // The DN was not a correct DN, and therefore does not a parent of the
      // DN given as a parameter.
      return false;
    }
  }

  /**
   * Check if the new dn of this ModifyDNMsg is the same as the targetDN
   * given in parameter.
   *
   * @param targetDN The targetDN to use to check for equality.
   *
   * @return A boolean indicating if the targetDN if the same as the new DN of
   *         the ModifyDNMsg.
   */
  public boolean newDNIsEqual(DN targetDN)
  {
    try
    {
      DN newDN = computeNewDN();
      return newDN.equals(targetDN);
    } catch (DirectoryException e)
    {
      // The DN was not a correct DN, and therefore does not match the
      // DN given as a parameter.
      return false;
    }
  }

  /**
   * Check if the new parent of the modifyDNMsg is the same as the targetDN
   * given in parameter.
   *
   * @param targetDN the targetDN to use when checking equality.
   *
   * @return A boolean indicating if the new parent of the modifyDNMsg is the
   *         same as the targetDN.
   */
  public boolean newParentIsEqual(DN targetDN)
  {
    try
    {
      DN newSuperiorDN = DN.valueOf(newSuperior);
      return newSuperiorDN.equals(targetDN);
    } catch (DirectoryException e)
    {
      // The newsuperior was not a correct DN, and therefore does not match the
      // DN given as a parameter.
      return false;
    }
  }

  /** {@inheritDoc} */
  @Override
  public int size()
  {
    return encodedMods.length + newRDN.length() +
      encodedEclIncludes.length + headerSize();
  }

}
