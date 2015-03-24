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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.protocol;

import java.util.zip.DataFormatException;

import org.opends.server.controls.SubtreeDeleteControl;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DeleteOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.CSN;
import org.opends.server.types.DN;
import org.opends.server.types.operation.PostOperationDeleteOperation;

import static org.opends.server.replication.protocol.OperationContext.*;

/**
 * Object used when sending delete information to replication servers.
 */
public class DeleteMsg extends LDAPUpdateMsg
{
  private String initiatorsName;

  /** Whether the DEL operation is a subtree DEL. */
  private boolean isSubtreeDelete;

  /**
   * Creates a new delete message.
   *
   * @param operation the Operation from which the message must be created.
   */
  DeleteMsg(PostOperationDeleteOperation operation)
  {
    super((OperationContext) operation.getAttachment(SYNCHROCONTEXT),
           operation.getEntryDN());
    try
    {
      isSubtreeDelete =
          operation.getRequestControl(SubtreeDeleteControl.DECODER) != null;
    }
    catch(Exception e)
    {/* do nothing */}
  }

  /**
   * Creates a new delete message.
   *
   * @param dn           The dn with which the message must be created.
   * @param csn          The CSN with which the message must be created.
   * @param entryUUID    The unique id with which the message must be created.
   */
  public DeleteMsg(DN dn, CSN csn, String entryUUID)
  {
    super(new DeleteContext(csn, entryUUID), dn);
  }

  /**
   * Creates a new Add message from a byte[].
   *
   * @param in The byte[] from which the operation must be read.
   * @throws DataFormatException The input byte[] is not a valid DeleteMsg
   */
  DeleteMsg(byte[] in) throws DataFormatException
  {
    final ByteArrayScanner scanner = new ByteArrayScanner(in);
    decodeHeader(scanner, MSG_TYPE_DELETE, MSG_TYPE_DELETE_V1);

    if (protocolVersion >= 4)
    {
      decodeBody_V4(scanner);
    }
    else
    {
      // Keep the previous protocol version behavior - when we don't know the
      // truth, we assume 'subtree'
      isSubtreeDelete = true;
    }
  }

  /** {@inheritDoc} */
  @Override
  public DeleteOperation createOperation(InternalClientConnection connection,
      DN newDN)
  {
    DeleteOperation del =  new DeleteOperationBasis(connection,
        InternalClientConnection.nextOperationID(),
        InternalClientConnection.nextMessageID(), null, newDN);

    if (isSubtreeDelete)
    {
      del.addRequestControl(new SubtreeDeleteControl(false));
    }

    DeleteContext ctx = new DeleteContext(getCSN(), getEntryUUID());
    del.setAttachment(SYNCHROCONTEXT, ctx);
    return del;
  }

  // ============
  // Msg encoding
  // ============

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes_V1()
  {
    return encodeHeader_V1(MSG_TYPE_DELETE_V1)
        .toByteArray();
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes_V23()
  {
    return encodeHeader(MSG_TYPE_DELETE,ProtocolVersion.REPLICATION_PROTOCOL_V3)
        .toByteArray();
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes_V45(short protocolVersion)
  {
    final ByteArrayBuilder builder =
        encodeHeader(MSG_TYPE_DELETE, protocolVersion);
    builder.appendString(initiatorsName);
    builder.appendIntUTF8(encodedEclIncludes.length);
    builder.appendZeroTerminatedByteArray(encodedEclIncludes);
    builder.appendBoolean(isSubtreeDelete);
    return builder.toByteArray();
  }

  // ============
  // Msg decoding
  // ============

  private void decodeBody_V4(ByteArrayScanner scanner)
      throws DataFormatException
  {
    initiatorsName = scanner.nextString();

    final int eclAttrLen = scanner.nextIntUTF8();
    encodedEclIncludes = scanner.nextByteArray(eclAttrLen);
    scanner.skipZeroSeparator();

    isSubtreeDelete = scanner.nextBoolean();
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    if (protocolVersion >= ProtocolVersion.REPLICATION_PROTOCOL_V1)
    {
      return "DeleteMsg content: " +
        " protocolVersion: " + protocolVersion +
        " dn: " + dn +
        " csn: " + csn +
        " uniqueId: " + entryUUID +
        " assuredFlag: " + assuredFlag +
        (protocolVersion >= ProtocolVersion.REPLICATION_PROTOCOL_V2 ?
          " assuredMode: " + assuredMode +
          " safeDataLevel: " + safeDataLevel
          : "");
    }
    return "!!! Unknown version: " + protocolVersion + "!!!";
  }

  /** {@inheritDoc} */
  @Override
  public int size()
  {
    return encodedEclIncludes.length + headerSize();
  }

  /**
   * Set the initiator's name of this change.
   *
   * @param iname the initiator's name.
   */
  public void setInitiatorsName(String iname)
  {
    initiatorsName = iname;
  }

  /**
   * Get the initiator's name of this change.
   * @return the initiator's name.
   */
  public String getInitiatorsName()
  {
    return initiatorsName;
  }

  /**
   * Set the subtree flag.
   * @param subtreeDelete the subtree flag.
   */
  public void setSubtreeDelete(boolean subtreeDelete)
  {
    this.isSubtreeDelete = subtreeDelete;
  }

  /**
   * Get the subtree flag.
   * @return the subtree flag.
   */
  public boolean isSubtreeDelete()
  {
    return this.isSubtreeDelete;
  }
}
