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
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.protocol;

import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;
import org.opends.server.types.DirectoryException;
import org.opends.server.replication.common.MultiDomainServerState;

/**
 * Container for the ECL information sent from the ReplicationServer
 * to the client part (either broker over the protocol, or ECLSession).
 */
public class ECLUpdateMsg extends ReplicationMsg
{
  // The replication change returned.
  private final LDAPUpdateMsg updateMsg;

  // The serviceId (baseDN) of the domain to which applies the change.
  private final String serviceId;

  // The value of the cookie updated with the current change
  private MultiDomainServerState cookie;

  // The changenumber as specified by draft-good-ldap-changelog.
  private int draftChangeNumber;

  /**
   * Creates a new message.
   * @param update    The provided update.
   * @param cookie    The provided cookie value
   * @param serviceId The provided serviceId.
   * @param draftChangeNumber The provided draft change number.
   */
  public ECLUpdateMsg(LDAPUpdateMsg update, MultiDomainServerState cookie,
      String serviceId, int draftChangeNumber)
  {
    this.cookie = cookie;
    this.serviceId = serviceId;
    this.updateMsg = update;
    this.draftChangeNumber = draftChangeNumber;
  }

  /**
   * Creates a new message from its encoded form.
   *
   * @param in The byte array containing the encoded form of the message.
   * @throws DataFormatException If the byte array does not contain
   *         a valid encoded form of the message.
   * @throws UnsupportedEncodingException when it occurs.
   * @throws NotSupportedOldVersionPDUException when it occurs.
   */
  public ECLUpdateMsg(byte[] in)
   throws DataFormatException,
          UnsupportedEncodingException,
          NotSupportedOldVersionPDUException
  {
    try
    {
      if (in[0] != MSG_TYPE_ECL_UPDATE)
      {
        throw new DataFormatException("byte[] is not a valid " +
            this.getClass().getCanonicalName());
      }
      int pos = 1;

      // Decode the cookie
      int length = getNextLength(in, pos);
      String cookieStr = new String(in, pos, length, "UTF-8");
      this.cookie = new MultiDomainServerState(cookieStr);
      pos += length + 1;

      // Decode the serviceId
      length = getNextLength(in, pos);
      this.serviceId = new String(in, pos, length, "UTF-8");
      pos += length + 1;

      // Decode the draft changeNumber
      length = getNextLength(in, pos);
      this.draftChangeNumber = Integer.valueOf(
          new String(in, pos, length, "UTF-8"));
      pos += length + 1;

      // Decode the msg
      /* Read the mods : all the remaining bytes but the terminating 0 */
      length = in.length - pos - 1;
      byte[] encodedMsg = new byte[length];
      System.arraycopy(in, pos, encodedMsg, 0, length);
      ReplicationMsg rmsg =
        ReplicationMsg.generateMsg(
            encodedMsg, ProtocolVersion.getCurrentVersion());
      this.updateMsg = (LDAPUpdateMsg)rmsg;
    }
    catch(DirectoryException de)
    {
      throw new DataFormatException(de.toString());
    }
    catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * Getter for the cookie value.
   * @return The cookie value.
   */
  public MultiDomainServerState getCookie()
  {
    return cookie;
  }

  /**
   * Setter for the cookie value.
   * @param cookie The provided cookie value.
   */
  public void setCookie(MultiDomainServerState cookie)
  {
    this.cookie = cookie;
  }

  /**
   * Getter for the serviceId.
   * @return The serviceId.
   */
  public  String getServiceId()
  {
    return serviceId;
  }

  /**
   * Getter for the message.
   * @return The included replication message.
   */
  public  UpdateMsg getUpdateMsg()
  {
    return updateMsg;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return "ECLUpdateMsg:[" +
    " updateMsg: " + updateMsg +
    " cookie: " + cookie +
    " draftChangeNumber: " + draftChangeNumber +
    " serviceId: " + serviceId + "]";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes(short protocolVersion)
      throws UnsupportedEncodingException
  {
    byte[] byteCookie    = String.valueOf(cookie).getBytes("UTF-8");
    byte[] byteServiceId = String.valueOf(serviceId).getBytes("UTF-8");
    byte[] byteDraftChangeNumber =
      Integer.toString(draftChangeNumber).getBytes("UTF-8");
    byte[] byteUpdateMsg = updateMsg.getBytes(protocolVersion);

    int length = 1 + byteCookie.length +
                 1 + byteServiceId.length +
                 1 + byteDraftChangeNumber.length +
                 1 + byteUpdateMsg.length + 1;

    byte[] resultByteArray = new byte[length];

    /* Encode type */
    resultByteArray[0] = MSG_TYPE_ECL_UPDATE;
    int pos = 1;

    // Encode cookie
    pos = addByteArray(byteCookie, resultByteArray, pos);

    // Encode serviceid
    pos = addByteArray(byteServiceId, resultByteArray, pos);

    /* Put the draftChangeNumber */
    pos = addByteArray(byteDraftChangeNumber, resultByteArray, pos);

    // Encode msg
    pos = addByteArray(byteUpdateMsg, resultByteArray, pos);

    return resultByteArray;
  }

  /**
   * Setter for the draftChangeNumber of this change.
   * @param draftChangeNumber the provided draftChangeNumber for this change.
   */
  public void setDraftChangeNumber(int draftChangeNumber)
  {
    this.draftChangeNumber = draftChangeNumber;
  }

  /**
   * Getter for the draftChangeNumber of this change.
   * @return the draftChangeNumber of this change.
   */
  public int getDraftChangeNumber()
  {
    return this.draftChangeNumber;
  }

}
