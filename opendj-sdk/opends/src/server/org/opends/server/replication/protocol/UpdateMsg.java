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

import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.LDAPException;
import org.opends.server.types.operation.PostOperationAddOperation;
import org.opends.server.types.operation.PostOperationDeleteOperation;
import org.opends.server.types.operation.PostOperationModifyDNOperation;
import org.opends.server.types.operation.PostOperationModifyOperation;
import org.opends.server.types.operation.PostOperationOperation;

/**
 * Abstract class that must be extended to define a message
 * used for sending Updates between servers.
 */
public abstract class UpdateMsg extends ReplicationMsg
                                    implements Comparable<UpdateMsg>
{
  /**
   * Protocol version.
   */
  protected short protocolVersion;

  /**
   * The ChangeNumber of this update.
   */
  protected ChangeNumber changeNumber;

  /**
   * The DN on which the update was originally done.
   */
  protected String dn = null;

  /**
   * True when the update must use assured replication.
   */
  protected boolean assuredFlag = false;

  /**
   * When assuredFlag is true, defines the requested assured mode.
   */
  protected AssuredMode assuredMode = AssuredMode.SAFE_DATA_MODE;

  /**
   * When assured mode is safe data, gives the requested level.
   */
  protected byte safeDataLevel = (byte)-1;

  /**
   * The uniqueId of the entry that was updated.
   */
  protected String uniqueId;

  /**
   * Creates a new UpdateMsg.
   */
  public UpdateMsg()
  {
  }

  /**
   * Creates a new UpdateMsg with the given informations.
   *
   * @param ctx The replication Context of the operation for which the
   *            update message must be created,.
   * @param dn The DN of the entry on which the change
   *           that caused the creation of this object happened
   */
  public UpdateMsg(OperationContext ctx, String dn)
  {
    this.protocolVersion = ProtocolVersion.getCurrentVersion();
    this.changeNumber = ctx.getChangeNumber();
    this.uniqueId = ctx.getEntryUid();
    this.dn = dn;
  }

  /**
   * Creates a new UpdateMessage with the given informations.
   *
   * @param cn        The ChangeNumber of the operation for which the
   *                  UpdateMessage is created.
   * @param entryUUID The Unique identifier of the entry that is updated
   *                  by the operation for which the UpdateMessage is created.
   * @param dn        The DN of the entry on which the change
   *                  that caused the creation of this object happened
   */
  public UpdateMsg(ChangeNumber cn, String entryUUID, String dn)
  {
    this.protocolVersion = ProtocolVersion.getCurrentVersion();
    this.changeNumber = cn;
    this.uniqueId = entryUUID;
    this.dn = dn;
  }

  /**
   * Generates an Update Message with the provided information.
   *
   * @param op The operation for which the message must be created.
   * @return The generated message.
   */
  public static UpdateMsg generateMsg(PostOperationOperation op)
  {
    UpdateMsg msg = null;
    switch (op.getOperationType())
    {
    case MODIFY :
      msg = new ModifyMsg((PostOperationModifyOperation) op);
      break;

    case ADD:
      msg = new AddMsg((PostOperationAddOperation) op);
      break;

    case DELETE :
      msg = new DeleteMsg((PostOperationDeleteOperation) op);
      break;

    case MODIFY_DN :
      msg = new ModifyDNMsg( (PostOperationModifyDNOperation) op);
      break;
    }

    return msg;
  }

  /**
   * Get the ChangeNumber from the message.
   * @return the ChangeNumber
   */
  public ChangeNumber getChangeNumber()
  {
    return changeNumber;
  }

  /**
   * Get the DN on which the operation happened.
   *
   * @return The DN on which the operations happened.
   */
  public String getDn()
  {
    return dn;
  }

  /**
   * Set the DN.
   * @param dn The dn that must now be used for this message.
   */
  public void setDn(String dn)
  {
    this.dn = dn;
  }

  /**
   * Get the Unique Identifier of the entry on which the operation happened.
   *
   * @return The Unique Identifier of the entry on which the operation happened.
   */
  public String getUniqueId()
  {
    return uniqueId;
  }

  /**
   * Get a boolean indicating if the Update must be processed as an
   * Asynchronous or as an assured replication.
   *
   * @return Returns the assuredFlag.
   */
  public boolean isAssured()
  {
    return assuredFlag;
  }

  /**
   * Set the Update message as an assured message.
   *
   * @param assured If the message is assured or not. Using true implies
   * setAssuredMode method must be called.
   */
  public void setAssured(boolean assured)
  {
    assuredFlag = assured;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object obj)
  {
    if (obj != null)
    {
      if (obj.getClass() != this.getClass())
        return false;
      return changeNumber.equals(((UpdateMsg) obj).changeNumber);
    }
    else
    {
      return false;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode()
  {
    return changeNumber.hashCode();
  }

  /**
   * {@inheritDoc}
   */
  public int compareTo(UpdateMsg msg)
  {
    return changeNumber.compareTo(msg.getChangeNumber());
  }

  /**
   * Create and Operation from the message.
   *
   * @param   conn connection to use when creating the message
   * @return  the created Operation
   * @throws  LDAPException In case of LDAP decoding exception.
   * @throws  ASN1Exception In case of ASN1 decoding exception.
   * @throws DataFormatException In case of bad msg format.
   */
  public AbstractOperation createOperation(InternalClientConnection conn)
         throws LDAPException, ASN1Exception, DataFormatException
  {
    return createOperation(conn, dn);
  }


  /**
   * Create and Operation from the message using the provided DN.
   *
   * @param   conn connection to use when creating the message.
   * @param   newDn the DN to use when creating the operation.
   * @return  the created Operation.
   * @throws  LDAPException In case of LDAP decoding exception.
   * @throws  ASN1Exception In case of ASN1 decoding exception.
   * @throws DataFormatException In case of bad msg format.
   */
  public abstract AbstractOperation createOperation(
         InternalClientConnection conn, String newDn)
         throws LDAPException, ASN1Exception, DataFormatException;

  /**
   * Encode the common header for all the UpdateMsg. This uses the current
   * protocol version.
   *
   * @param type the type of UpdateMsg to encode.
   * @param additionalLength additional length needed to encode the remaining
   *                         part of the UpdateMsg.
   * @return a byte array containing the common header and enough space to
   *         encode the remaining bytes of the UpdateMsg as was specified
   *         by the additionalLength.
   *         (byte array length = common header length + additionalLength)
   * @throws UnsupportedEncodingException if UTF-8 is not supported.
   */
  public byte[] encodeHeader(byte type, int additionalLength)
    throws UnsupportedEncodingException
  {
    byte[] byteDn = dn.getBytes("UTF-8");
    byte[] changeNumberByte =
      this.getChangeNumber().toString().getBytes("UTF-8");
    byte[] byteEntryuuid = getUniqueId().getBytes("UTF-8");

    /* The message header is stored in the form :
     * <operation type><protocol version><changenumber><dn><entryuuid><assured>
     * <assured mode> <safe data level>
     * the length of result byte array is therefore :
     *   1 + 1 + change number length + 1 + dn length + 1 + uuid length + 1 + 1
     *   + 1 + 1 + additional_length
     */
    int length = 8 + changeNumberByte.length + byteDn.length
                 + byteEntryuuid.length + additionalLength;

    byte[] encodedMsg = new byte[length];

    /* put the type of the operation */
    encodedMsg[0] = type;

    /* put the protocol version */
    encodedMsg[1] = (byte)ProtocolVersion.getCurrentVersion();
    int pos = 2;

    /* Put the ChangeNumber */
    pos = addByteArray(changeNumberByte, encodedMsg, pos);

    /* Put the DN and a terminating 0 */
    pos = addByteArray(byteDn, encodedMsg, pos);

    /* Put the entry uuid and a terminating 0 */
    pos = addByteArray(byteEntryuuid, encodedMsg, pos);

    /* Put the assured flag */
    encodedMsg[pos++] = (assuredFlag ? (byte) 1 : 0);

    /* Put the assured mode */
    encodedMsg[pos++] = assuredMode.getValue();

    /* Put the safe data level */
    encodedMsg[pos++] = safeDataLevel;

    return encodedMsg;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes(short reqProtocolVersion)
    throws UnsupportedEncodingException
  {

    // Using current protocol version should normally not be done as we would
    // normally call the getBytes() method instead for that. So this check
    // for security
    if (reqProtocolVersion == ProtocolVersion.getCurrentVersion())
    {
      return getBytes();
    }

    switch (reqProtocolVersion)
    {
      case ProtocolVersion.REPLICATION_PROTOCOL_V1:
        return getBytes_V1();
      default:
        // Unsupported requested version
        throw new UnsupportedEncodingException(getClass().getSimpleName() +
          " PDU does not support requested protocol version serialization: " +
          reqProtocolVersion);
    }
  }

  /**
   * Get the byte array representation of this Message. This uses the version
   * 1 of the replication protocol (used for compatibility purpose).
   *
   * @return The byte array representation of this Message.
   *
   * @throws UnsupportedEncodingException  When the encoding of the message
   *         failed because the UTF-8 encoding is not supported.
   */
  public abstract byte[] getBytes_V1() throws UnsupportedEncodingException;

  /**
   * Encode the common header for all the UpdateMessage. This uses the version
   * 1 of the replication protocol (used for compatibility purpose).
   *
   * @param type the type of UpdateMessage to encode.
   * @param additionalLength additional length needed to encode the remaining
   *                         part of the UpdateMessage.
   * @return a byte array containing the common header and enough space to
   *         encode the remaining bytes of the UpdateMessage as was specified
   *         by the additionalLength.
   *         (byte array length = common header length + additionalLength)
   * @throws UnsupportedEncodingException if UTF-8 is not supported.
   */
  public byte[] encodeHeader_V1(byte type, int additionalLength)
    throws UnsupportedEncodingException
  {
    byte[] byteDn = dn.getBytes("UTF-8");
    byte[] changeNumberByte =
      this.getChangeNumber().toString().getBytes("UTF-8");
    byte[] byteEntryuuid = getUniqueId().getBytes("UTF-8");

    /* The message header is stored in the form :
     * <operation type>changenumber><dn><assured><entryuuid><change>
     * the length of result byte array is therefore :
     *   1 + change number length + 1 + dn length + 1  + 1 +
     *   uuid length + 1 + additional_length
     */
    int length = 5 + changeNumberByte.length + byteDn.length
                 + byteEntryuuid.length + additionalLength;

    byte[] encodedMsg = new byte[length];

    /* put the type of the operation */
    encodedMsg[0] = type;
    int pos = 1;

    /* put the ChangeNumber */
    pos = addByteArray(changeNumberByte, encodedMsg, pos);

    /* put the assured information */
    encodedMsg[pos++] = (assuredFlag ? (byte) 1 : 0);

    /* put the DN and a terminating 0 */
    pos = addByteArray(byteDn, encodedMsg, pos);

    /* put the entry uuid and a terminating 0 */
    pos = addByteArray(byteEntryuuid, encodedMsg, pos);

    return encodedMsg;
  }

  /**
   * Decode the Header part of this Update Message, and check its type.
   *
   * @param types The allowed types of this Update Message.
   * @param encodedMsg the encoded form of the UpdateMsg.
   * @return the position at which the remaining part of the message starts.
   * @throws DataFormatException if the encodedMsg does not contain a valid
   *         common header.
   */
  public int decodeHeader(byte[] types, byte[] encodedMsg)
                          throws DataFormatException
  {
    /* The message header is stored in the form :
     * <operation type><protocol version><changenumber><dn><entryuuid><assured>
     * <assured mode> <safe data level>
     */

    /* first byte is the type */
    boolean foundMatchingType = false;
    for (int i = 0; i < types.length; i++)
    {
      if (types[i] == encodedMsg[0])
      {
        foundMatchingType = true;
        break;
      }
    }
    if (!foundMatchingType)
      throw new DataFormatException("byte[] is not a valid update msg: "
        + encodedMsg[0]);

    /*
     * For older protocol version PDUs, decode the matching version header
     * instead.
     */
    if ((encodedMsg[0] == MSG_TYPE_ADD_V1) ||
      (encodedMsg[0] == MSG_TYPE_DELETE_V1) ||
      (encodedMsg[0] == MSG_TYPE_MODIFYDN_V1) ||
      (encodedMsg[0] == MSG_TYPE_MODIFY_V1))
    {
      return decodeHeader_V1(encodedMsg);
    }

    /* read the protocol version */
    protocolVersion = (short)encodedMsg[1];

    try
    {
      /* Read the changeNumber */
      int pos = 2;
      int length = getNextLength(encodedMsg, pos);
      String changenumberStr = new String(encodedMsg, pos, length, "UTF-8");
      pos += length + 1;
      changeNumber = new ChangeNumber(changenumberStr);

      /* Read the dn */
      length = getNextLength(encodedMsg, pos);
      dn = new String(encodedMsg, pos, length, "UTF-8");
      pos += length + 1;

      /* Read the entryuuid */
      length = getNextLength(encodedMsg, pos);
      uniqueId = new String(encodedMsg, pos, length, "UTF-8");
      pos += length + 1;

      /* Read the assured information */
      if (encodedMsg[pos++] == 1)
        assuredFlag = true;
      else
        assuredFlag = false;

      /* Read the assured mode */
      assuredMode = AssuredMode.valueOf(encodedMsg[pos++]);

      /* Read the safe data level */
      safeDataLevel = encodedMsg[pos++];

      return pos;
    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    } catch (IllegalArgumentException e)
    {
      throw new DataFormatException(e.getMessage());
    }
  }

  /**
   * Decode the Header part of this Update Message, and check its type. This
   * uses the version 1 of the replication protocol (used for compatibility
   * purpose).
   *
   * @param encodedMsg the encoded form of the UpdateMessage.
   * @return the position at which the remaining part of the message starts.
   * @throws DataFormatException if the encodedMsg does not contain a valid
   *         common header.
   */
  public int decodeHeader_V1(byte[] encodedMsg)
                          throws DataFormatException
  {
    if ((encodedMsg[0] != MSG_TYPE_ADD_V1) &&
      (encodedMsg[0] != MSG_TYPE_DELETE_V1) &&
      (encodedMsg[0] != MSG_TYPE_MODIFYDN_V1) &&
      (encodedMsg[0] != MSG_TYPE_MODIFY_V1))
      throw new DataFormatException("byte[] is not a valid update msg: expected"
        + " a V1 PDU, received: " + encodedMsg[0]);

    // Force version to V1 (other new parameters take their default values
    // (assured stuff...))
    protocolVersion = ProtocolVersion.REPLICATION_PROTOCOL_V1;

    try
    {
      /* read the changeNumber */
      int pos = 1;
      int length = getNextLength(encodedMsg, pos);
      String changenumberStr = new String(encodedMsg, pos, length, "UTF-8");
      pos += length + 1;
      changeNumber = new ChangeNumber(changenumberStr);

      /* read the assured information */
      if (encodedMsg[pos++] == 1)
        assuredFlag = true;
      else
        assuredFlag = false;

      /* read the dn */
      length = getNextLength(encodedMsg, pos);
      dn = new String(encodedMsg, pos, length, "UTF-8");
      pos += length + 1;

      /* read the entryuuid */
      length = getNextLength(encodedMsg, pos);
      uniqueId = new String(encodedMsg, pos, length, "UTF-8");
      pos += length + 1;

      return pos;
    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * Get the assured mode in this message.
   * @return The assured mode in this message
   */
  public AssuredMode getAssuredMode()
  {
    return assuredMode;
  }

  /**
   * Get the safe data level in this message.
   * @return The safe data level in this message
   */
  public byte getSafeDataLevel()
  {
    return safeDataLevel;
  }

  /**
   * Set the assured mode. Assured boolean must be set to true for this field
   * to mean something.
   * @param assuredMode The chosen assured mode.
   */
  public void setAssuredMode(AssuredMode assuredMode)
  {
    this.assuredMode = assuredMode;
  }

  /**
   * Set the safe data level. Assured mode should be set to safe data for this
   * field to mean something.
   * @param safeDataLevel The chosen safe data level.
   */
  public void setSafeDataLevel(byte safeDataLevel)
  {
    this.safeDataLevel = safeDataLevel;
  }

  /**
   * Get the version included in the update message. Means the replication
   * protocol version with which this update message was instantiated.
   *
   * @return The version with which this update message was instantiated.
   */
  public short getVersion()
  {
    return protocolVersion;
  }

  /**
   * Return the number of bytes used by this message.
   *
   * @return The number of bytes used by this message.
   */
  public abstract int size();
}
