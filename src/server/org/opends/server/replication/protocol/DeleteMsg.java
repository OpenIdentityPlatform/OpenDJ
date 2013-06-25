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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.protocol;

import static org.opends.server.replication.protocol.OperationContext.*;

import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

import org.opends.server.controls.SubtreeDeleteControl;
import org.opends.server.core.DeleteOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.ByteString;
import org.opends.server.types.Operation;
import org.opends.server.types.operation.PostOperationDeleteOperation;

/**
 * Object used when sending delete information to replication servers.
 */
public class DeleteMsg extends LDAPUpdateMsg
{
  private String initiatorsName;

  /** whether the DEL operation is a subtree DEL. */
  private boolean isSubtreeDelete = false;

  /**
   * Creates a new delete message.
   *
   * @param operation the Operation from which the message must be created.
   */
  public DeleteMsg(PostOperationDeleteOperation operation)
  {
    super((OperationContext) operation.getAttachment(SYNCHROCONTEXT),
           operation.getRawEntryDN().toString());
    try
    {
      if (operation.getRequestControl(SubtreeDeleteControl.DECODER) != null)
        isSubtreeDelete = true;
    }
    catch(Exception e)
    {/* do nothing */}
  }

  /**
   * Creates a new delete message.
   *
   * @param dn           The dn with which the message must be created.
   * @param changeNumber The change number with which the message must be
   *                     created.
   * @param entryUUID    The unique id with which the message must be created.
   */
  public DeleteMsg(String dn, ChangeNumber changeNumber, String entryUUID)
  {
    super(new DeleteContext(changeNumber, entryUUID), dn);
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
    int pos = decodeHeader(allowedPduTypes, in);

    // protocol version has been read as part of the header
    if (protocolVersion >= 4)
      decodeBody_V4(in, pos);
    else
    {
      // Keep the previous protocol version behavior - when we don't know the
      // truth, we assume 'subtree'
      isSubtreeDelete = true;
    }
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public Operation createOperation(InternalClientConnection connection,
      String newDn)
  {
    DeleteOperationBasis del =  new DeleteOperationBasis(connection,
        InternalClientConnection.nextOperationID(),
        InternalClientConnection.nextMessageID(), null,
        ByteString.valueOf(newDn));

    if (isSubtreeDelete)
      del.addRequestControl(new SubtreeDeleteControl(false));

    DeleteContext ctx = new DeleteContext(getChangeNumber(), getEntryUUID());
    del.setAttachment(SYNCHROCONTEXT, ctx);
    return del;
  }

  // ============
  // Msg encoding
  // ============

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes_V1() throws UnsupportedEncodingException
  {
    return encodeHeader_V1(MSG_TYPE_DELETE_V1, 0);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes_V23() throws UnsupportedEncodingException
  {
    return encodeHeader(MSG_TYPE_DELETE, 0,
        ProtocolVersion.REPLICATION_PROTOCOL_V3);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes_V45(short reqProtocolVersion)
      throws UnsupportedEncodingException
  {
    // Put together the different encoded pieces
    int bodyLength = 0;

    byte[] byteEntryAttrLen =
      String.valueOf(encodedEclIncludes.length).getBytes("UTF-8");

    bodyLength += byteEntryAttrLen.length + 1;
    bodyLength += encodedEclIncludes.length + 1;
    byte[] byteInitiatorsName = null;
    if (initiatorsName != null)
    {
      byteInitiatorsName = initiatorsName.getBytes("UTF-8");
      bodyLength += byteInitiatorsName.length + 1;
    }
    else
    {
      bodyLength++;
    }
    // subtree flag
    bodyLength++;

    /* encode the header in a byte[] large enough to also contain the mods */
    byte [] encodedMsg = encodeHeader(MSG_TYPE_DELETE, bodyLength,
        reqProtocolVersion);
    int pos = encodedMsg.length - bodyLength;
    if (byteInitiatorsName != null)
      pos = addByteArray(byteInitiatorsName, encodedMsg, pos);
    else
      encodedMsg[pos++] = 0;
    pos = addByteArray(byteEntryAttrLen, encodedMsg, pos);
    pos = addByteArray(encodedEclIncludes, encodedMsg, pos);

    encodedMsg[pos++] = (isSubtreeDelete ? (byte) 1 : (byte) 0);

    return encodedMsg;
  }

  // ============
  // Msg decoding
  // ============

  private void decodeBody_V4(byte[] in, int pos)
  throws DataFormatException, UnsupportedEncodingException
  {
    int length = getNextLength(in, pos);
    if (length != 0)
    {
      initiatorsName = new String(in, pos, length, "UTF-8");
      pos += length + 1;
    }
    else
    {
      initiatorsName = null;
      pos += 1;
    }

    // Read ecl attr len
    length = getNextLength(in, pos);
    int eclAttrLen = Integer.valueOf(new String(in, pos, length,"UTF-8"));
    // Skip the length
    pos += length + 1;

    // Read/Don't decode entry attributes
    encodedEclIncludes = new byte[eclAttrLen];
    try
    {
      // Copy ecl attr
      System.arraycopy(in, pos, encodedEclIncludes, 0, eclAttrLen);
      // Skip the attrs
      pos += eclAttrLen +1;
    } catch (IndexOutOfBoundsException e)
    {
      throw new DataFormatException(e.getMessage());
    } catch (ArrayStoreException e)
    {
      throw new DataFormatException(e.getMessage());
    } catch (NullPointerException e)
    {
      throw new DataFormatException(e.getMessage());
    }

    // subtree flag
    isSubtreeDelete = (in[pos] == 1);

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
        " protocolVersion: " + protocolVersion +
        " dn: " + dn +
        " changeNumber: " + changeNumber +
        " uniqueId: " + entryUUID +
        " assuredFlag: " + assuredFlag;
    }
    if (protocolVersion >= ProtocolVersion.REPLICATION_PROTOCOL_V2)
    {
      return "DeleteMsg content: " +
        " protocolVersion: " + protocolVersion +
        " dn: " + dn +
        " changeNumber: " + changeNumber +
        " uniqueId: " + entryUUID +
        " assuredFlag: " + assuredFlag +
        " assuredMode: " + assuredMode +
        " safeDataLevel: " + safeDataLevel;
    }
    return "!!! Unknown version: " + protocolVersion + "!!!";
  }

  /**
   * {@inheritDoc}
   */
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
