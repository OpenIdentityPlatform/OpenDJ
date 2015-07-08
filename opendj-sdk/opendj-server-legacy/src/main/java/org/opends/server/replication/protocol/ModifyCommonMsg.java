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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.replication.protocol;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.schema.AttributeUsage;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.plugin.EntryHistorical;
import org.opends.server.types.*;

/**
 * This class holds every common code for the modify messages (mod, moddn).
 */
public abstract class ModifyCommonMsg extends LDAPUpdateMsg {

  /**
   * The modifications kept encoded in the message.
   */
  protected byte[] encodedMods = new byte[0];

  /**
   * Creates a new ModifyCommonMsg.
   */
  public ModifyCommonMsg()
  {
    super();
  }

  /**
   * Creates a new ModifyCommonMsg with the given informations.
   *
   * @param ctx The replication Context of the operation for which the
   *            update message must be created,.
   * @param dn The DN of the entry on which the change
   *           that caused the creation of this object happened
   */
  public ModifyCommonMsg(OperationContext ctx, DN dn)
  {
   super(ctx, dn);
  }

  /**
   * Creates a new ModifyCommonMsg with the given informations.
   *
   * @param csn       The CSN of the operation for which the
   *                  UpdateMessage is created.
   * @param entryUUID The Unique identifier of the entry that is updated
   *                  by the operation for which the UpdateMessage is created.
   * @param dn        The DN of the entry on which the change
   *                  that caused the creation of this object happened
   */
  public ModifyCommonMsg(CSN csn, String entryUUID, DN dn)
  {
    super(csn, entryUUID, dn);
  }

  /**
   * Set the Modification associated to the UpdateMsg to the provided value.
   *
   * @param mods The new Modification associated to this ModifyMsg.
   */
  public void setMods(List<Modification> mods)
  {
    encodedMods = encodeMods(mods);
  }

  /**
   * Get the Modifications associated to the UpdateMsg to the provided value.
   * @throws LDAPException In case of LDAP decoding exception
   * @throws IOException In case of ASN1 decoding exception
   * @return the list of modifications
   */
  public List<Modification> getMods() throws IOException, LDAPException
  {
    return decodeMods(encodedMods);
  }

  // ============
  // Msg encoding
  // ============

  /**
   * Encode an ArrayList of Modification into a byte[] suitable
   * for storage in a database or send on the network.
   *
   * @param mods the ArrayList of Modification to be encoded.
   * @return The encoded modifications.
   */
  protected byte[] encodeMods(List<Modification> mods)
  {
    if ((mods == null) || (mods.size() == 0))
    {
      return new byte[0];
    }

    ByteStringBuilder byteBuilder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(byteBuilder);

    for (Modification mod : mods)
    {
      Attribute attr = mod.getAttribute();
      AttributeType type = attr.getAttributeType();
      if (type != null
          && AttributeUsage.DSA_OPERATION.equals(type.getUsage()) )
      {
        // Attributes with a dsaOperation usage should not be synchronized.
        // skip them.
        continue;
      }

      if (!EntryHistorical.isHistoricalAttribute(attr))
      {
        LDAPModification ldapmod = new LDAPModification(
          mod.getModificationType(), new LDAPAttribute(mod.getAttribute()));
        try
        {
          ldapmod.write(writer);
        }
        catch(Exception e)
        {
          // DO SOMETHING
        }
      }
    }
    return byteBuilder.toByteArray();
  }

  // ============
  // Msg decoding
  // ============

  /**
   * Decode mods from the provided byte array.
   * @param in The provided byte array.
   * @throws IOException when occurs.
   * @throws LDAPException when occurs.
   * @return The decoded mods.
   */
  protected List<Modification> decodeMods(byte[] in) throws IOException,
      LDAPException
  {
    List<Modification> mods = new ArrayList<>();
    ASN1Reader reader = ASN1.getReader(in);
    while (reader.hasNextElement())
    {
      mods.add((LDAPModification.decode(reader)).toModification());
    }
    return mods;
  }

  /**
   * Decode raw mods from the provided byte array.
   * @param in The provided byte array.
   * @return The decoded mods.
   * @throws IOException when occurs.
   * @throws LDAPException when occurs.
   */
  protected List<RawModification> decodeRawMods(byte[] in)
      throws LDAPException, IOException
  {
    List<RawModification> ldapmods = new ArrayList<>();
    ASN1Reader asn1Reader = ASN1.getReader(in);
    while(asn1Reader.hasNextElement())
    {
      ldapmods.add(LDAPModification.decode(asn1Reader));
    }
    return ldapmods;
  }
}
