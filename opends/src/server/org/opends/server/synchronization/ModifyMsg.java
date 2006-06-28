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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.synchronization;

import static org.opends.server.synchronization.SynchMessages.SYNCHRONIZATION;
import static org.opends.server.protocols.ldap.LDAPConstants.*;

import org.opends.server.core.ModifyOperation;
import org.opends.server.core.Operation;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.Attribute;
import org.opends.server.types.DN;
import org.opends.server.types.Modification;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Message used to send Modify information.
 */
public class ModifyMsg extends UpdateMessage
{
  private static final long serialVersionUID = -4905520652801395185L;
  private String dn;
  private byte[] encodedMods;

  /**
   * Creates a new Modify message from a ModifyOperation.
   *
   * @param op The operation to use for building the message
   */
  public ModifyMsg(ModifyOperation op)
  {
    dn = op.getRawEntryDN().stringValue();
    encodedMods = modsToByte(op.getModifications());
    changeNumber = (ChangeNumber) op.getAttachment(SYNCHRONIZATION);
  }

  /**
   * Creates a new Modify message using the provided information.
   *
   * @param changeNumber The ChangeNumber for the operation.
   * @param dn           The baseDN of the operation.
   * @param mods         The mod of the operation.
   */
  public ModifyMsg(ChangeNumber changeNumber, DN dn, List<Modification> mods)
  {
    this.encodedMods = modsToByte(mods);
    this.dn = dn.toNormalizedString();
    this.changeNumber = changeNumber;
  }

  /**
   * Creates a new Modify message from a byte[].
   *
   * @param in The byte[] from which the operation must be read.
   * @throws Exception The input byte[] is not a valid modifyMsg
   */
  public ModifyMsg(byte[] in) throws Exception
  {
    /* first byte is the type */
    if (in[0] != OP_TYPE_MODIFY_REQUEST)
      throw new Exception("byte[] is not a valid modify msg");
    int pos = 1;

    /* read the dn
     * first calculate the length then construct the string
     */
    int length = 0;
    int offset = pos;
    while (in[pos++] != 0)
    {
      if (pos > in.length)
        throw new Exception("byte[] is not a valid modify msg");
      length++;
    }
    dn = new String(in, offset, length, "UTF-8");

    /* read the changeNumber
     * it is always 24 characters long
     */
    String changenumberStr = new  String(in, pos, 24, "UTF-8");
    changeNumber = new ChangeNumber(changenumberStr);
    pos +=24;

    /* Read the mods : all the remaining bytes */
    encodedMods = new byte[in.length-pos];
    int i =0;
    while (pos<in.length)
    {
      encodedMods[i++] = in[pos++];
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
   * Create an operation from this Modify message.
   *
   * @param connection The connection to use when creating the operation.
   * @return The created operation.
   * @throws LDAPException In case of ldap decoding exception.
   * @throws ASN1Exception In case of ASN1 decoding exception.
   */
  @Override
  public Operation createOperation(InternalClientConnection connection)
                   throws LDAPException, ASN1Exception
  {
    ArrayList<LDAPModification> ldapmods;

    ArrayList<ASN1Element> mods = null;

    mods = ASN1Element.decodeElements(encodedMods);

    ldapmods = new ArrayList<LDAPModification>(mods.size());
    for (ASN1Element elem : mods)
      ldapmods.add(LDAPModification.decode(elem));

    ModifyOperation mod = new ModifyOperation(connection,
                               InternalClientConnection.nextOperationID(),
                               InternalClientConnection.nextMessageID(), null,
                               new ASN1OctetString(dn), ldapmods);
    mod.setAttachment(SYNCHRONIZATION, getChangeNumber());
    return mod;
  }

  /**
   * Get the byte array representation of this Message.
   *
   * @return The byte array representation of this Message.
   */
  @Override
  public byte[] getByte()
  {
    byte[] byteDn;
    try
    {
      byteDn = dn.getBytes("UTF-8");

      /* The Modify message is stored in the form :
       * <operation type><dn><changenumber><mods>
       * the length of result byte array is therefore :
       *   1 + dn length + 1 + 24 + mods length
       */
      int length = 1 + byteDn.length + 1  + 24 + encodedMods.length;
      byte[] resultByteArray = new byte[length];
      int pos = 1;

      /* put the type of the operation */
      resultByteArray[0] = OP_TYPE_MODIFY_REQUEST;
      /* put the DN and a terminating 0 */
      for (int i = 0; i< byteDn.length; i++,pos++)
      {
        resultByteArray[pos] = byteDn[i];
      }
      resultByteArray[pos++] = 0;
      /* put the ChangeNumber */
      byte[] changeNumberByte =
                      this.getChangeNumber().toString().getBytes("UTF-8");
      for (int i=0; i<24; i++,pos++)
      {
        resultByteArray[pos] = changeNumberByte[i];
      }

      /* put the mods */
      for (int i=0; i<encodedMods.length; i++,pos++)
      {
        resultByteArray[pos] = encodedMods[i];
      }
      return resultByteArray;
    } catch (UnsupportedEncodingException e)
    {
      // should never happens : TODO : log some error
    }
    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return("Mod " + dn + " " + getChangeNumber());
  }
}
