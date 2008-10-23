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

import static org.opends.server.replication.protocol.OperationContext.*;

import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.plugin.Historical;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeUsage;
import org.opends.server.types.DN;
import org.opends.server.types.LDAPException;
import org.opends.server.types.Modification;
import org.opends.server.types.RawModification;
import org.opends.server.types.operation.PostOperationModifyOperation;


import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;

/**
 * Message used to send Modify information.
 */
public class ModifyMsg extends UpdateMsg
{
  private byte[] encodedMods = null;

  /**
   * Creates a new Modify message from a ModifyOperation.
   *
   * @param op The operation to use for building the message
   */
  public ModifyMsg(PostOperationModifyOperation op)
  {
    super((OperationContext) op.getAttachment(OperationContext.SYNCHROCONTEXT),
          op.getRawEntryDN().stringValue());
    encodedMods = modsToByte(op.getModifications());
  }

  /**
   * Creates a new Modify message using the provided information.
   *
   * @param changeNumber The ChangeNumber for the operation.
   * @param dn           The baseDN of the operation.
   * @param mods         The mod of the operation.
   * @param entryuuid    The unique id of the entry on which the modification
   *                     needs to apply.
   */
  public ModifyMsg(ChangeNumber changeNumber, DN dn, List<Modification> mods,
                   String entryuuid)
  {
    super(new ModifyContext(changeNumber, entryuuid),
          dn.toNormalizedString());
    this.encodedMods = modsToByte(mods);
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

    /* Read the mods : all the remaining bytes but the terminating 0 */
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

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes() throws UnsupportedEncodingException
  {
    /* encode the header in a byte[] large enough to also contain the mods */
    byte[] encodedMsg = encodeHeader(MSG_TYPE_MODIFY, encodedMods.length + 1);

    /* add the mods */
    int pos = encodedMsg.length - (encodedMods.length + 1);
    addByteArray(encodedMods, encodedMsg, pos);

    return encodedMsg;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public AbstractOperation createOperation(InternalClientConnection connection,
                   String newDn)
                   throws LDAPException, ASN1Exception, DataFormatException
  {
    if (newDn == null)
      newDn = getDn();

    ArrayList<RawModification> ldapmods;

    ArrayList<ASN1Element> mods = null;

    mods = ASN1Element.decodeElements(encodedMods);

    ldapmods = new ArrayList<RawModification>(mods.size());
    for (ASN1Element elem : mods)
      ldapmods.add(LDAPModification.decode(elem));

    ModifyOperationBasis mod = new ModifyOperationBasis(connection,
                               InternalClientConnection.nextOperationID(),
                               InternalClientConnection.nextMessageID(), null,
                               new ASN1OctetString(newDn), ldapmods);
    ModifyContext ctx = new ModifyContext(getChangeNumber(), getUniqueId());
    mod.setAttachment(SYNCHROCONTEXT, ctx);
    return mod;
  }

  /**
   * Encode an ArrayList of Modification into a byte[] suitable
   * for storage in a database or send on the network.
   *
   * @param mods the ArrayList of Modification to be encoded.
   */
  private byte[] modsToByte(List<Modification> mods)
  {
    ArrayList<ASN1Element> modsASN1;

    modsASN1 = new ArrayList<ASN1Element>(mods.size());
    for (Modification mod : mods)
    {
      Attribute attr = mod.getAttribute();
      AttributeType type = attr.getAttributeType();
      if (type != null )
      {
        if (AttributeUsage.DSA_OPERATION.equals(type.getUsage()))
        {
          // Attributes with a dsaOperation usage should not be synchronized.
          // skip them.
          continue;
        }
      }

      if (!Historical.isHistoricalAttribute(attr))
      {
        LDAPModification ldapmod = new LDAPModification(
          mod.getModificationType(), new LDAPAttribute(mod.getAttribute()));
        modsASN1.add(ldapmod.encode());
      }
    }

    return ASN1Element.encodeValue(modsASN1);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    if (protocolVersion == ProtocolVersion.REPLICATION_PROTOCOL_V1)
    {
      return "ModifyMsg content: " +
        "\nprotocolVersion: " + protocolVersion +
        "\ndn: " + dn +
        "\nchangeNumber: " + changeNumber +
        "\nuniqueId: " + uniqueId +
        "\nassuredFlag: " + assuredFlag;
    }
    if (protocolVersion == ProtocolVersion.REPLICATION_PROTOCOL_V2)
    {
      return "ModifyMsg content: " +
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
   * Set the Modification associated to the UpdateMsg to the provided value.
   *
   * @param mods The new Modification associated to this ModifyMsg.
   */
  public void setMods(List<Modification> mods)
  {
    encodedMods = modsToByte(mods);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int size()
  {
    // The ModifyMsg can be very large when added or deleted attribute
    // values are very large. We therefore need to count the
    // whole encoded msg.
    return encodedMods.length + 100; // 100 let's assume header size is 100
  }

  /**
   * {@inheritDoc}
   */
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
}
