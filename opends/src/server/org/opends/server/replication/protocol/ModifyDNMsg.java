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

import static org.opends.server.replication.protocol.OperationContext.*;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.zip.DataFormatException;

import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.LDAPException;
import org.opends.server.types.Modification;
import org.opends.server.types.Operation;
import org.opends.server.types.RDN;
import org.opends.server.types.operation.PostOperationModifyDNOperation;

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
   * construct a new Modify DN message.
   *
   * @param operation The operation to use for building the message
   */
  public ModifyDNMsg(PostOperationModifyDNOperation operation)
  {
    super((OperationContext) operation.getAttachment(SYNCHROCONTEXT),
        operation.getRawEntryDN().toString());

    encodedMods = encodeMods(operation.getModifications());

    ModifyDnContext ctx =
      (ModifyDnContext) operation.getAttachment(SYNCHROCONTEXT);
    newSuperiorEntryUUID = ctx.getNewSuperiorEntryUUID();

    deleteOldRdn = operation.deleteOldRDN();
    if (operation.getRawNewSuperior() != null)
      newSuperior = operation.getRawNewSuperior().toString();
    else
      newSuperior = null;
    newRDN = operation.getRawNewRDN().toString();
  }

  /**
   * Construct a new Modify DN message (no mods).
   * Note: Keep this constructor version to support already written tests, not
   * using mods.
   *
   * @param dn The dn to use for building the message.
   * @param changeNumber The changeNumberto use for building the message.
   * @param entryUUID          The unique id to use for building the message.
   * @param newSuperiorEntryUUID The new parent unique id to use for building
   *                     the message.
   * @param deleteOldRdn boolean indicating if old rdn must be deleted to use
   *                     for building the message.
   * @param newSuperior  The new Superior entry to use for building the message.
   * @param newRDN       The new Rdn to use for building the message.
   */
  public ModifyDNMsg(String dn, ChangeNumber changeNumber, String entryUUID,
                     String newSuperiorEntryUUID, boolean deleteOldRdn,
                     String newSuperior, String newRDN)
  {
    super(new ModifyDnContext(changeNumber, entryUUID, newSuperiorEntryUUID),
        dn);

    this.newSuperiorEntryUUID = newSuperiorEntryUUID;
    this.deleteOldRdn = deleteOldRdn;
    this.newSuperior = newSuperior;
    this.newRDN = newRDN;
  }

  /**
   * Construct a new Modify DN message (with mods).
   *
   * @param dn The dn to use for building the message.
   * @param changeNumber The changeNumberto use for building the message.
   * @param entryUUID The unique id to use for building the message.
   * @param newSuperiorEntryUUID The new parent unique id to use for building
   *                     the message.
   * @param deleteOldRdn boolean indicating if old rdn must be deleted to use
   *                     for building the message.
   * @param newSuperior  The new Superior entry to use for building the message.
   * @param newRDN       The new Rdn to use for building the message.
   * @param mods         The mod of the operation.
   */
  public ModifyDNMsg(String dn, ChangeNumber changeNumber, String entryUUID,
      String newSuperiorEntryUUID, boolean deleteOldRdn, String newSuperior,
      String newRDN, List<Modification> mods)
  {
    this(dn, changeNumber, entryUUID, newSuperiorEntryUUID, deleteOldRdn,
        newSuperior, newRDN);
    this.encodedMods = encodeMods(mods);
  }

  /**
   * Creates a new ModifyDN message from a byte[].
   *
   * @param in The byte[] from which the operation must be read.
   * @throws DataFormatException The input byte[] is not a valid ModifyDNMsg.
   * @throws UnsupportedEncodingException If UTF8 is not supported.
   */
  public ModifyDNMsg(byte[] in) throws DataFormatException,
                                       UnsupportedEncodingException
  {
    // Decode header
    byte[] allowedPduTypes = new byte[2];
    allowedPduTypes[0] = MSG_TYPE_MODIFYDN;
    allowedPduTypes[1] = MSG_TYPE_MODIFYDN_V1;
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
  public Operation createOperation(InternalClientConnection connection,
      String newDn) throws LDAPException, ASN1Exception
  {
    ModifyDNOperationBasis moddn =  new ModifyDNOperationBasis(connection,
        InternalClientConnection.nextOperationID(),
        InternalClientConnection.nextMessageID(), null,
        ByteString.valueOf(newDn), ByteString.valueOf(newRDN),
        deleteOldRdn,
        (newSuperior == null ? null : ByteString.valueOf(newSuperior)));

    for (Modification mod : decodeMods(encodedMods))
    {
      moddn.addModification(mod);
    }

    ModifyDnContext ctx = new ModifyDnContext(getChangeNumber(), getEntryUUID(),
        newSuperiorEntryUUID);
    moddn.setAttachment(SYNCHROCONTEXT, ctx);
    return moddn;
  }

  // ============
  // Msg Encoding
  // ============

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes_V1() throws UnsupportedEncodingException
  {
    byte[] byteNewRdn = newRDN.getBytes("UTF-8");
    byte[] byteNewSuperior = null;
    byte[] byteNewSuperiorId = null;

    // calculate the length necessary to encode the parameters
    int bodyLength = byteNewRdn.length + 1 + 1;
    if (newSuperior != null)
    {
      byteNewSuperior = newSuperior.getBytes("UTF-8");
      bodyLength += byteNewSuperior.length + 1;
    }
    else
      bodyLength += 1;

    if (newSuperiorEntryUUID != null)
    {
      byteNewSuperiorId = newSuperiorEntryUUID.getBytes("UTF-8");
      bodyLength += byteNewSuperiorId.length + 1;
    }
    else
      bodyLength += 1;

    byte[] encodedMsg = encodeHeader_V1(MSG_TYPE_MODIFYDN_V1, bodyLength);
    int pos = encodedMsg.length - bodyLength;

    /* put the new RDN and a terminating 0 */
    pos = addByteArray(byteNewRdn, encodedMsg, pos);

    /* put the newsuperior and a terminating 0 */
    if (newSuperior != null)
    {
      pos = addByteArray(byteNewSuperior, encodedMsg, pos);
    }
    else
      encodedMsg[pos++] = 0;

    /* put the newsuperiorId and a terminating 0 */
    if (newSuperiorEntryUUID != null)
    {
      pos = addByteArray(byteNewSuperiorId, encodedMsg, pos);
    }
    else
      encodedMsg[pos++] = 0;

    /* put the deleteoldrdn flag */
    if (deleteOldRdn)
      encodedMsg[pos++] = 1;
    else
      encodedMsg[pos++] = 0;

    return encodedMsg;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes_V23() throws UnsupportedEncodingException
  {
    // Encoding V2 / V3

    byte[] byteNewRdn = newRDN.getBytes("UTF-8");
    byte[] byteNewSuperior = null;
    byte[] byteNewSuperiorId = null;

    // calculate the length necessary to encode the parameters
    int length = byteNewRdn.length + 1 + 1;
    if (newSuperior != null)
    {
      byteNewSuperior = newSuperior.getBytes("UTF-8");
      length += byteNewSuperior.length + 1;
    }
    else
      length += 1;

    if (newSuperiorEntryUUID != null)
    {
      byteNewSuperiorId = newSuperiorEntryUUID.getBytes("UTF-8");
      length += byteNewSuperiorId.length + 1;
    }
    else
      length += 1;

    length += encodedMods.length + 1;

    /* encode the header in a byte[] large enough to also contain mods.. */
    byte[] encodedMsg = encodeHeader(MSG_TYPE_MODIFYDN, length,
        ProtocolVersion.REPLICATION_PROTOCOL_V3);
    int pos = encodedMsg.length - length;

    /* put the new RDN and a terminating 0 */
    pos = addByteArray(byteNewRdn, encodedMsg, pos);

    /* put the newsuperior and a terminating 0 */
    if (newSuperior != null)
    {
      pos = addByteArray(byteNewSuperior, encodedMsg, pos);
    }
    else
      encodedMsg[pos++] = 0;

    /* put the newsuperiorId and a terminating 0 */
    if (newSuperiorEntryUUID != null)
    {
      pos = addByteArray(byteNewSuperiorId, encodedMsg, pos);
    }
    else
      encodedMsg[pos++] = 0;

    /* put the deleteoldrdn flag */
    if (deleteOldRdn)
      encodedMsg[pos++] = 1;
    else
      encodedMsg[pos++] = 0;

    /* add the mods */
    if (encodedMods.length > 0)
    {
      pos = encodedMsg.length - (encodedMods.length + 1);
      addByteArray(encodedMods, encodedMsg, pos);
    }
    else
      encodedMsg[pos++] = 0;

    return encodedMsg;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes_V45(short reqProtocolVersion)
      throws UnsupportedEncodingException
  {
    byte[] byteNewSuperior = null;
    byte[] byteNewSuperiorId = null;

    // calculate the length necessary to encode the parameters

    byte[] byteNewRdn = newRDN.getBytes("UTF-8");
    int bodyLength = byteNewRdn.length + 1 + 1;

    if (newSuperior != null)
    {
      byteNewSuperior = newSuperior.getBytes("UTF-8");
      bodyLength += byteNewSuperior.length + 1;
    }
    else
      bodyLength += 1;

    if (newSuperiorEntryUUID != null)
    {
      byteNewSuperiorId = newSuperiorEntryUUID.getBytes("UTF-8");
      bodyLength += byteNewSuperiorId.length + 1;
    }
    else
      bodyLength += 1;

    byte[] byteModsLen =
      String.valueOf(encodedMods.length).getBytes("UTF-8");
    bodyLength += byteModsLen.length + 1;
    bodyLength += encodedMods.length + 1;

    byte[] byteEntryAttrLen =
      String.valueOf(encodedEclIncludes.length).getBytes("UTF-8");
    bodyLength += byteEntryAttrLen.length + 1;
    bodyLength += encodedEclIncludes.length + 1;

    /* encode the header in a byte[] large enough to also contain mods.. */
    byte[] encodedMsg = encodeHeader(MSG_TYPE_MODIFYDN, bodyLength,
        reqProtocolVersion);

    int pos = encodedMsg.length - bodyLength;

    /* put the new RDN and a terminating 0 */
    pos = addByteArray(byteNewRdn, encodedMsg, pos);
    /* put the newsuperior and a terminating 0 */
    if (newSuperior != null)
    {
      pos = addByteArray(byteNewSuperior, encodedMsg, pos);
    }
    else
      encodedMsg[pos++] = 0;
    /* put the newsuperiorId and a terminating 0 */
    if (newSuperiorEntryUUID != null)
    {
      pos = addByteArray(byteNewSuperiorId, encodedMsg, pos);
    }
    else
      encodedMsg[pos++] = 0;

    /* put the deleteoldrdn flag */
    if (deleteOldRdn)
      encodedMsg[pos++] = 1;
    else
      encodedMsg[pos++] = 0;

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
  throws DataFormatException, UnsupportedEncodingException
  {
    /* read the newRDN
     * first calculate the length then construct the string
     */
    int length = getNextLength(in, pos);
    newRDN = new String(in, pos, length, "UTF-8");
    pos += length + 1;

    /* read the newSuperior
     * first calculate the length then construct the string
     */
    length = getNextLength(in, pos);
    if (length != 0)
      newSuperior = new String(in, pos, length, "UTF-8");
    else
      newSuperior = null;
    pos += length + 1;

    /* read the new parent Id
     */
    length = getNextLength(in, pos);
    if (length != 0)
      newSuperiorEntryUUID = new String(in, pos, length, "UTF-8");
    else
      newSuperiorEntryUUID = null;
    pos += length + 1;

    /* get the deleteoldrdn flag */
    deleteOldRdn = in[pos] != 0;
    pos++;

    // For easiness (no additional method), simply compare PDU type to
    // know if we have to read the mods of V2
    if (in[0] == MSG_TYPE_MODIFYDN)
    {
      /* Read the mods : all the remaining bytes but the terminating 0 */
      length = in.length - pos - 1;
      if (length > 0) // Otherwise, there is only the trailing 0 byte which we
        // do not need to read
      {
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
    }
  }

  private void decodeBody_V4(byte[] in, int pos)
  throws DataFormatException, UnsupportedEncodingException
  {
    /* read the newRDN
     * first calculate the length then construct the string
     */
    int length = getNextLength(in, pos);
    newRDN = new String(in, pos, length, "UTF-8");
    pos += length + 1;

    /* read the newSuperior
     * first calculate the length then construct the string
     */
    length = getNextLength(in, pos);
    if (length != 0)
      newSuperior = new String(in, pos, length, "UTF-8");
    else
      newSuperior = null;
    pos += length + 1;

    /* read the new parent Id
     */
    length = getNextLength(in, pos);
    if (length != 0)
      newSuperiorEntryUUID = new String(in, pos, length, "UTF-8");
    else
      newSuperiorEntryUUID = null;
    pos += length + 1;

    /* get the deleteoldrdn flag */
    deleteOldRdn = in[pos] != 0;
    pos++;

    // Read mods len
    length = getNextLength(in, pos);
    int modsLen = Integer.valueOf(new String(in, pos, length,"UTF-8"));
    pos += length + 1;

    // Read/Don't decode attributes
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

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
     if (protocolVersion == ProtocolVersion.REPLICATION_PROTOCOL_V1)
    {
      return "ModifyDNMsg content: " +
        " protocolVersion: " + protocolVersion +
        " dn: " + dn +
        " changeNumber: " + changeNumber +
        " uniqueId: " + entryUUID +
        " assuredFlag: " + assuredFlag +
        " newRDN: " + newRDN +
        " newSuperior: " + newSuperior +
        " deleteOldRdn: " + deleteOldRdn;
    }
    if (protocolVersion >= ProtocolVersion.REPLICATION_PROTOCOL_V2)
    {
      return "ModifyDNMsg content: " +
        " protocolVersion: " + protocolVersion +
        " dn: " + dn +
        " changeNumber: " + changeNumber +
        " uniqueId: " + entryUUID +
        " newRDN: " + newRDN +
        " newSuperior: " + newSuperior +
        " deleteOldRdn: " + deleteOldRdn +
        " assuredFlag: " + assuredFlag +
        " assuredMode: " + assuredMode +
        " safeDataLevel: " + safeDataLevel;
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
    if (newSuperior == null)
    {
      DN parentDn = DN.decode(this.getDn()).getParent();
      return parentDn.concat(RDN.decode(newRDN));
    }
    else
    {
      String newStringDN = newRDN + "," + newSuperior;
      return DN.decode(newStringDN);
    }
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
      DN newSuperiorDN = DN.decode(newSuperior);
      return newSuperiorDN.equals(targetDN);
    } catch (DirectoryException e)
    {
      // The newsuperior was not a correct DN, and therefore does not match the
      // DN given as a parameter.
      return false;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int size()
  {
    return encodedMods.length + newRDN.length() +
      encodedEclIncludes.length + headerSize();
  }

}
