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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.synchronization.protocol;

import static org.opends.server.synchronization.protocol.OperationContext.*;

import org.opends.server.core.ModifyOperation;
import org.opends.server.core.Operation;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.synchronization.common.ChangeNumber;
import org.opends.server.synchronization.plugin.Historical;
import org.opends.server.types.Attribute;
import org.opends.server.types.DN;
import org.opends.server.types.Modification;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;

/**
 * Message used to send Modify information.
 */
public class ModifyMsg extends UpdateMessage
{
  private static final long serialVersionUID = -4905520652801395185L;
  private byte[] encodedMods = null;
  private byte[] encodedMsg = null;

  /**
   * Creates a new Modify message from a ModifyOperation.
   *
   * @param op The operation to use for building the message
   */
  public ModifyMsg(ModifyOperation op)
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
   * @throws DataFormatException If the input byte[] is not a valid modifyMsg
   * @throws UnsupportedEncodingException If UTF8 is not supported by the JVM.
   */
  public ModifyMsg(byte[] in) throws DataFormatException,
                                     UnsupportedEncodingException
  {
    super(in);
    encodedMsg = in;
  }

  /**
   * Get the byte array representation of this Message.
   *
   * @return The byte array representation of this Message.
   */
  @Override
  public byte[] getBytes()
  {
    if (encodedMsg == null)
    {
      try
      {
        encode();
      } catch (UnsupportedEncodingException e)
      {
        // should never happens : TODO : log some error
        return null;
      }
    }
    return encodedMsg;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Operation createOperation(InternalClientConnection connection,
                   String newDn)
                   throws LDAPException, ASN1Exception, DataFormatException
  {
    if (encodedMods == null)
    {
      decode();
    }

    if (newDn == null)
      newDn = getDn();

    ArrayList<LDAPModification> ldapmods;

    ArrayList<ASN1Element> mods = null;

    mods = ASN1Element.decodeElements(encodedMods);

    ldapmods = new ArrayList<LDAPModification>(mods.size());
    for (ASN1Element elem : mods)
      ldapmods.add(LDAPModification.decode(elem));

    ModifyOperation mod = new ModifyOperation(connection,
                               InternalClientConnection.nextOperationID(),
                               InternalClientConnection.nextMessageID(), null,
                               new ASN1OctetString(newDn), ldapmods);
    ModifyContext ctx = new ModifyContext(getChangeNumber(), getUniqueId());
    mod.setAttachment(SYNCHROCONTEXT, ctx);
    return mod;
  }

  /**
   * Encode the Msg information into a byte array.
   *
   * @throws UnsupportedEncodingException If utf8 is not suported.
   */
  private void encode() throws UnsupportedEncodingException
  {
    /* encode the header in a byte[] large enough to also contain the mods */
    encodedMsg = encodeHeader(MSG_TYPE_MODIFY_REQUEST, encodedMods.length + 1);
    int pos = encodedMsg.length - (encodedMods.length + 1);

    /* add the mods */
    pos = addByteArray(encodedMods, encodedMsg, pos);
  }

  /**
   * Decode the encodedMsg into mods and dn.
   *
   * @throws DataFormatException when the encodedMsg is no a valid modify.
   */
  private void decode() throws DataFormatException
  {
    int pos = decodeHeader(MSG_TYPE_MODIFY_REQUEST, encodedMsg);

    /* Read the mods : all the remaining bytes but the terminating 0 */
    encodedMods = new byte[encodedMsg.length-pos-1];
    int i =0;
    while (pos<encodedMsg.length-1)
    {
      encodedMods[i++] = encodedMsg[pos++];
    }
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
      if (!attr.getAttributeType().equals(Historical.historicalAttrType))
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
    return("Modify " + getDn() + " " + getChangeNumber());
  }
}
