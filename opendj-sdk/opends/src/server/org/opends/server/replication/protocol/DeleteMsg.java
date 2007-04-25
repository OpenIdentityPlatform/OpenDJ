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

import org.opends.server.core.DeleteOperation;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.Operation;

/**
 * Object used when sending delete information to Changelogs.
 */
public class DeleteMsg extends UpdateMessage
{
  private static final long serialVersionUID = -4905520652801395185L;

  /**
   * Creates a new delete message.
   *
   * @param op the Operation from which the message must be created.
   */
  public DeleteMsg(DeleteOperation op)
  {
    super((OperationContext) op.getAttachment(SYNCHROCONTEXT),
           op.getRawEntryDN().stringValue());
  }

  /**
   * Creates a new delete message.
   *
   * @param dn The dn with which the message must be created.
   * @param changeNumber The change number with which the message must be
   *                     created.
   * @param uid The unique id with which the message must be created.
   */
  public DeleteMsg(String dn, ChangeNumber changeNumber, String uid)
  {
    super(new DeleteContext(changeNumber, uid), dn);
  }

  /**
   * Creates a new Add message from a byte[].
   *
   * @param in The byte[] from which the operation must be read.
   * @throws DataFormatException The input byte[] is not a valid AddMsg
   * @throws UnsupportedEncodingException  If UTF8 is not supported by the jvm
   */
  public DeleteMsg(byte[] in) throws DataFormatException,
                                     UnsupportedEncodingException
  {
    super(in);
    decodeHeader(MSG_TYPE_DELETE_REQUEST, in);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public Operation createOperation(InternalClientConnection connection,
      String newDn)
  {
    DeleteOperation del =  new DeleteOperation(connection,
                               InternalClientConnection.nextOperationID(),
                               InternalClientConnection.nextMessageID(), null,
                               new ASN1OctetString(newDn));
    DeleteContext ctx = new DeleteContext(getChangeNumber(), getUniqueId());
    del.setAttachment(SYNCHROCONTEXT, ctx);
    return del;
  }

  /**
   * Get the byte array representation of this Message.
   *
   * @return The byte array representation of this Message.
   */
  @Override
  public byte[] getBytes()
  {
    try
    {
      return encodeHeader(MSG_TYPE_DELETE_REQUEST, 0);
    } catch (UnsupportedEncodingException e)
    {
      // should never happen : TODO : log error properly
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return ("DEL " + getDn() + " " + getChangeNumber());
  }
}
