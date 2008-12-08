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

import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

import org.opends.server.core.DeleteOperationBasis;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.operation.PostOperationDeleteOperation;

/**
 * Object used when sending delete information to replication servers.
 */
public class DeleteMsg extends LDAPUpdateMsg
{
  /**
   * Creates a new delete message.
   *
   * @param operation the Operation from which the message must be created.
   */
  public DeleteMsg(PostOperationDeleteOperation operation)
  {
    super((OperationContext) operation.getAttachment(SYNCHROCONTEXT),
           operation.getRawEntryDN().stringValue());
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
   * @throws DataFormatException The input byte[] is not a valid DeleteMsg
   * @throws UnsupportedEncodingException  If UTF8 is not supported by the jvm
   */
  public DeleteMsg(byte[] in) throws DataFormatException,
                                     UnsupportedEncodingException
  {
    byte[] allowedPduTypes = new byte[2];
    allowedPduTypes[0] = MSG_TYPE_DELETE;
    allowedPduTypes[1] = MSG_TYPE_DELETE_V1;
    decodeHeader(allowedPduTypes, in);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public AbstractOperation createOperation(
         InternalClientConnection connection, String newDn)
  {
    DeleteOperationBasis del =  new DeleteOperationBasis(connection,
                               InternalClientConnection.nextOperationID(),
                               InternalClientConnection.nextMessageID(), null,
                               new ASN1OctetString(newDn));
    DeleteContext ctx = new DeleteContext(getChangeNumber(), getUniqueId());
    del.setAttachment(SYNCHROCONTEXT, ctx);
    return del;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes() throws UnsupportedEncodingException
  {
    return encodeHeader(MSG_TYPE_DELETE, 0);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    if (protocolVersion == ProtocolVersion.REPLICATION_PROTOCOL_V1)
    {
      return "DeleteMsg content: " +
        "\nprotocolVersion: " + protocolVersion +
        "\ndn: " + dn +
        "\nchangeNumber: " + changeNumber +
        "\nuniqueId: " + uniqueId +
        "\nassuredFlag: " + assuredFlag;
    }
    if (protocolVersion == ProtocolVersion.REPLICATION_PROTOCOL_V2)
    {
      return "DeleteMsg content: " +
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
   * {@inheritDoc}
   */
  @Override
  public int size()
  {
    // The DeleteMsg size is mostly dependent on the DN and should never
    // grow very large. It is therefore safe to assume an average of 40 bytes.
    return 40;
  }

  /**
   * {@inheritDoc}
   */
  public byte[] getBytes_V1() throws UnsupportedEncodingException
  {
    return encodeHeader_V1(MSG_TYPE_DELETE_V1, 0);
  }
}
