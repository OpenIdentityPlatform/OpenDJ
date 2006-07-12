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
import java.util.zip.DataFormatException;

/**
 * Message used to send Modify information.
 */
public class ModifyMsg extends UpdateMessage
{
  private static final long serialVersionUID = -4905520652801395185L;
  private String dn = null;
  private byte[] encodedMods = null;
  private byte[] encodedMsg = null;

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
   * @throws DataFormatException If the input byte[] is not a valid modifyMsg
   * @throws UnsupportedEncodingException If UTF8 is not supported by the JVM.
   */
  public ModifyMsg(byte[] in) throws DataFormatException,
                                     UnsupportedEncodingException
  {
    encodedMsg = in;
    decodeChangeNumber(in);
  }

  private void decodeChangeNumber(byte[] in) throws DataFormatException,
                                             UnsupportedEncodingException
  {
    /* read the changeNumber */
    int pos = 1;
    int length = getNextLength(encodedMsg, pos);
    String changenumberStr = new String(encodedMsg, pos, length, "UTF-8");
    pos += length +1;
    changeNumber = new ChangeNumber(changenumberStr);
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
  public Operation createOperation(InternalClientConnection connection)
                   throws LDAPException, ASN1Exception, DataFormatException
  {
    if (encodedMods == null)
    {
      decode();
    }

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
   * Encode the Msg information into a byte array.
   *
   * @throws UnsupportedEncodingException If utf8 is not suported.
   */
  private void encode() throws UnsupportedEncodingException
  {
    byte[] byteDn = dn.getBytes("UTF-8");
    byte[] changeNumberByte =
      this.getChangeNumber().toString().getBytes("UTF-8");

    /* The Modify message is stored in the form :
     * <operation type>changenumber><dn><<mods>
     * the length of result byte array is therefore :
     *   1 + dn length + 1 + 24 + mods length
     */
    int length = 1 + changeNumberByte.length + 1 + byteDn.length + 1
                 + encodedMods.length + 1;
    encodedMsg = new byte[length];

    /* put the type of the operation */
    encodedMsg[0] = MSG_TYPE_MODIFY_REQUEST;
    int pos = 1;

    /* put the ChangeNumber */
    pos = addByteArray(changeNumberByte, encodedMsg, pos);

    /* put the DN and a terminating 0 */
    pos = addByteArray(byteDn, encodedMsg, pos);

    /* put the mods */
    pos = addByteArray(encodedMods, encodedMsg, pos);
  }

  /**
   * Decode the encodedMsg into mods and dn.
   *
   * @throws DataFormatException when the encodedMsg is no a valid modify.
   */
  private void decode() throws DataFormatException
  {
    /* first byte is the type */
    if (encodedMsg[0] != MSG_TYPE_MODIFY_REQUEST)
      throw new DataFormatException("byte[] is not a valid modify msg");

    try
    {
      /* read the changeNumber */
      int pos = 1;
      int length = getNextLength(encodedMsg, pos);
      String changenumberStr = new String(encodedMsg, pos, length, "UTF-8");
      pos += length +1;
      changeNumber = new ChangeNumber(changenumberStr);

      /* read the dn */
      length = getNextLength(encodedMsg, pos);
      dn = new String(encodedMsg, pos, length, "UTF-8");
      pos += length +1;

      /* Read the mods : all the remaining bytes but the terminating 0 */
      encodedMods = new byte[encodedMsg.length-pos-1];
      int i =0;
      while (pos<encodedMsg.length-1)
      {
        encodedMods[i++] = encodedMsg[pos++];
      }
    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
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
    return("Mod " + dn + " " + getChangeNumber());
  }
}
