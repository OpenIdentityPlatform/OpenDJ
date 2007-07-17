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
package org.opends.server.replication.protocol;

import static org.opends.server.replication.protocol.OperationContext.*;

import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.operation.PostOperationModifyDNOperation;

/**
 * Message used to send Modify DN information.
 */
public class ModifyDNMsg extends UpdateMessage
{
  private String newRDN;
  private String newSuperior;
  private boolean deleteOldRdn;
  private String newSuperiorId;
  private static final long serialVersionUID = -4905520652801395185L;

  /**
   * construct a new Modify DN message.
   *
   * @param operation The operation to use for building the message
   */
  public ModifyDNMsg(PostOperationModifyDNOperation operation)
  {
    super((OperationContext) operation.getAttachment(SYNCHROCONTEXT),
        operation.getRawEntryDN().stringValue());

    ModifyDnContext ctx =
      (ModifyDnContext) operation.getAttachment(SYNCHROCONTEXT);
    newSuperiorId = ctx.getNewParentId();

    deleteOldRdn = operation.deleteOldRDN();
    if (operation.getRawNewSuperior() != null)
      newSuperior = operation.getRawNewSuperior().stringValue();
    else
      newSuperior = null;
    newRDN = operation.getRawNewRDN().stringValue();
  }

  /**
   * construct a new Modify DN message.
   *
   * @param dn The dn to use for building the message.
   * @param changeNumber The changeNumberto use for building the message.
   * @param uid The unique id to use for building the message.
   * @param newParentUid The new parent unique id to use for building
   *                     the message.
   * @param deleteOldRdn boolean indicating if old rdn must be deleted to use
   *                     for building the message.
   * @param newSuperior The new Superior entry to use for building the message.
   * @param newRDN The new Rdn to use for building the message.
   */
  public ModifyDNMsg(String dn, ChangeNumber changeNumber, String uid,
                     String newParentUid, boolean deleteOldRdn,
                     String newSuperior, String newRDN)
  {
    super(new ModifyDnContext(changeNumber, uid, newParentUid), dn);

    newSuperiorId = newParentUid;

    this.deleteOldRdn = deleteOldRdn;
    this.newSuperior = newSuperior;
    this.newRDN = newRDN;
  }

  /**
   * Creates a new ModifyDN message from a byte[].
   *
   * @param in The byte[] from which the operation must be read.
   * @throws DataFormatException The input byte[] is not a valid AddMsg.
   * @throws UnsupportedEncodingException If UTF8 is not supported.
   */
  public ModifyDNMsg(byte[] in) throws DataFormatException,
                                       UnsupportedEncodingException
  {
    super(in);

    int pos = decodeHeader(MSG_TYPE_MODIFYDN_REQUEST, in);

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
      newSuperiorId = new String(in, pos, length, "UTF-8");
    else
      newSuperiorId = null;
    pos += length + 1;

    /* get the deleteoldrdn flag */
    if (in[pos] == 0)
      deleteOldRdn = false;
    else
      deleteOldRdn = true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public AbstractOperation createOperation(
         InternalClientConnection connection, String newDn)
  {
    ModifyDNOperationBasis moddn =  new ModifyDNOperationBasis(connection,
               InternalClientConnection.nextOperationID(),
               InternalClientConnection.nextMessageID(), null,
               new ASN1OctetString(newDn), new ASN1OctetString(newRDN),
               deleteOldRdn,
               (newSuperior == null ? null : new ASN1OctetString(newSuperior)));
    ModifyDnContext ctx = new ModifyDnContext(getChangeNumber(), getUniqueId(),
                                              newSuperiorId);
    moddn.setAttachment(SYNCHROCONTEXT, ctx);
    return moddn;
  }

  /**
   * Get the byte array representation of this Message.
   *
   * @return The byte array representation of this Message.
   *
   * @throws UnsupportedEncodingException  When the encoding of the message
   *         failed because the UTF-8 encoding is not supported.
   */
  @Override
  public byte[] getBytes() throws UnsupportedEncodingException
  {
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

    if (newSuperiorId != null)
    {
      byteNewSuperiorId = newSuperiorId.getBytes("UTF-8");
      length += byteNewSuperiorId.length + 1;
    }
    else
      length += 1;

    byte[] resultByteArray = encodeHeader(MSG_TYPE_MODIFYDN_REQUEST, length);
    int pos = resultByteArray.length - length;

    /* put the new RDN and a terminating 0 */
    pos = addByteArray(byteNewRdn, resultByteArray, pos);

    /* put the newsuperior and a terminating 0 */
    if (newSuperior != null)
    {
      pos = addByteArray(byteNewSuperior, resultByteArray, pos);
    }
    else
      resultByteArray[pos++] = 0;

    /* put the newsuperiorId and a terminating 0 */
    if (newSuperiorId != null)
    {
      pos = addByteArray(byteNewSuperiorId, resultByteArray, pos);
    }
    else
      resultByteArray[pos++] = 0;

    /* put the deleteoldrdn flag */
    if (deleteOldRdn)
      resultByteArray[pos++] = 1;
    else
      resultByteArray[pos++] = 0;

    return resultByteArray;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return ("Modify DN " + getDn() + " " + newRDN + " " + newSuperior + " " +
            getChangeNumber());
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
      String newStringDN = newRDN + "," + newSuperior;
      DN newDN = DN.decode(newStringDN);

      if (newDN.isAncestorOf(targetDn))
        return true;
      else
        return false;
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
      String newStringDN = newRDN + "," + newSuperior;
      DN newDN = DN.decode(newStringDN);

      if (newDN.equals(targetDN))
        return true;
      else
        return false;
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

      if (newSuperiorDN.equals(targetDN))
        return true;
      else
        return false;
    } catch (DirectoryException e)
    {
      // The newsuperior was not a correct DN, and therefore does not match the
      // DN given as a parameter.
      return false;
    }
  }

}
