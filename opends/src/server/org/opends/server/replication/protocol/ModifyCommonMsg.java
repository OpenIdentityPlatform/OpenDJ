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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.protocol;

import java.util.List;

import org.opends.server.protocols.asn1.ASN1;
import org.opends.server.protocols.asn1.ASN1Writer;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.plugin.Historical;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeUsage;
import org.opends.server.types.ByteStringBuilder;
import org.opends.server.types.Modification;

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
    encodedMods = modsToByte(mods);
  }

  /**
   * Encode an ArrayList of Modification into a byte[] suitable
   * for storage in a database or send on the network.
   *
   * @param mods the ArrayList of Modification to be encoded.
   * @return The encoded modifications.
   */
  protected byte[] modsToByte(List<Modification> mods)
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

      if (!Historical.isHistoricalAttribute(attr))
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

}
