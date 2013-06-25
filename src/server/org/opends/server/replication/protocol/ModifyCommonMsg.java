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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 */

package org.opends.server.replication.protocol;

import java.util.ArrayList;
import java.util.List;

import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1Reader;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.plugin.EntryHistorical;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeUsage;
import org.opends.server.types.ByteStringBuilder;
import org.opends.server.types.LDAPException;
import org.opends.server.types.Modification;
import org.opends.server.types.RawModification;

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
  public ModifyCommonMsg(OperationContext ctx, String dn)
  {
   super(ctx, dn);
  }

  /**
   * Creates a new ModifyCommonMsg with the given informations.
   *
   * @param cn        The ChangeNumber of the operation for which the
   *                  UpdateMessage is created.
   * @param entryUUID The Unique identifier of the entry that is updated
   *                  by the operation for which the UpdateMessage is created.
   * @param dn        The DN of the entry on which the change
   *                  that caused the creation of this object happened
   */
  public ModifyCommonMsg(ChangeNumber cn, String entryUUID, String dn)
  {
    super(cn, entryUUID, dn);
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
   * @throws ASN1Exception In case of ASN1 decoding exception
   * @return the list of modifications
   */
  public List<Modification> getMods() throws ASN1Exception, LDAPException
  {
    List<Modification> mods = new ArrayList<Modification>();

    ASN1Reader reader = ASN1.getReader(encodedMods);

    while (reader.hasNextElement())
      mods.add((LDAPModification.decode(reader)).toModification());

    return mods;
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
      return new byte[0];

    ByteStringBuilder byteBuilder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(byteBuilder);

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
   * @throws ASN1Exception when occurs.
   * @throws LDAPException when occurs.
   * @return The decoded mods.
   */
  protected List<Modification> decodeMods(byte[] in)
  throws ASN1Exception, LDAPException
  {
    List<Modification> mods = new ArrayList<Modification>();
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
   * @throws ASN1Exception when occurs.
   * @throws LDAPException when occurs.
   */
  protected ArrayList<RawModification> decodeRawMods(byte[] in)
  throws LDAPException, ASN1Exception
  {
    ArrayList<RawModification> ldapmods = new ArrayList<RawModification>();
    ASN1Reader asn1Reader = ASN1.getReader(in);
    while(asn1Reader.hasNextElement())
    {
      ldapmods.add(LDAPModification.decode(asn1Reader));
    }
    return ldapmods;
  }

}
