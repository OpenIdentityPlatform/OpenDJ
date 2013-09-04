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

import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.types.DirectoryException;

/**
 * Container for the ECL information sent from the ReplicationServer
 * to the client part (either broker over the protocol, or ECLSession).
 */
public class ECLUpdateMsg extends ReplicationMsg
{
  /** The replication change returned. */
  private final LDAPUpdateMsg updateMsg;

  /** The baseDN of the domain to which applies the change. */
  private final String baseDN;

  /** The value of the cookie updated with the current change. */
  private MultiDomainServerState cookie;

  /** The changeNumber as specified by draft-good-ldap-changelog. */
  private long changeNumber;

  /**
   * Creates a new message.
   * @param updateMsg The provided update message.
   * @param cookie    The provided cookie value
   * @param baseDN    The provided baseDN.
   * @param changeNumber The provided change number.
   */
  public ECLUpdateMsg(LDAPUpdateMsg updateMsg, MultiDomainServerState cookie,
      String baseDN, int changeNumber)
  {
    this.cookie = cookie;
    this.baseDN = baseDN;
    this.updateMsg = updateMsg;
    this.changeNumber = changeNumber;
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
            getClass().getCanonicalName());
      }
      int pos = 1;

      // Decode the cookie
      int length = getNextLength(in, pos);
      String cookieStr = new String(in, pos, length, "UTF-8");
      this.cookie = new MultiDomainServerState(cookieStr);
      pos += length + 1;

      // Decode the baseDN
      length = getNextLength(in, pos);
      this.baseDN = new String(in, pos, length, "UTF-8");
      pos += length + 1;

      // Decode the changeNumber
      length = getNextLength(in, pos);
      this.changeNumber = Integer.valueOf(new String(in, pos, length, "UTF-8"));
      pos += length + 1;

      // Decode the msg
      /* Read the mods : all the remaining bytes but the terminating 0 */
      length = in.length - pos - 1;
      byte[] encodedMsg = new byte[length];
      System.arraycopy(in, pos, encodedMsg, 0, length);
      ReplicationMsg rmsg = ReplicationMsg.generateMsg(
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
   * Getter for the baseDN.
   *
   * @return The baseDN.
   */
  public String getBaseDN()
  {
    return baseDN;
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
    " changeNumber: " + changeNumber +
    " serviceId: " + baseDN + "]";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes(short protocolVersion)
      throws UnsupportedEncodingException
  {
    byte[] byteCookie = String.valueOf(cookie).getBytes("UTF-8");
    byte[] byteBaseDN = String.valueOf(baseDN).getBytes("UTF-8");
    // FIXME JNR Changing line below to use long would require a protocol
    // version change. Leave it like this for now until the need arises.
    byte[] byteChangeNumber =
        Integer.toString((int) changeNumber).getBytes("UTF-8");
    byte[] byteUpdateMsg = updateMsg.getBytes(protocolVersion);

    int length = 1 + byteCookie.length +
                 1 + byteBaseDN.length +
                 1 + byteChangeNumber.length +
                 1 + byteUpdateMsg.length + 1;

    byte[] resultByteArray = new byte[length];

    /* Encode type */
    resultByteArray[0] = MSG_TYPE_ECL_UPDATE;
    int pos = 1;

    // Encode all fields
    pos = addByteArray(byteCookie, resultByteArray, pos);
    pos = addByteArray(byteBaseDN, resultByteArray, pos);
    pos = addByteArray(byteChangeNumber, resultByteArray, pos);
    pos = addByteArray(byteUpdateMsg, resultByteArray, pos);

    return resultByteArray;
  }

  /**
   * Setter for the changeNumber of this change.
   * @param changeNumber the provided changeNumber for this change.
   */
  public void setChangeNumber(long changeNumber)
  {
    this.changeNumber = changeNumber;
  }

  /**
   * Getter for the changeNumber of this change.
   * @return the changeNumber of this change.
   */
  public long getChangeNumber()
  {
    return this.changeNumber;
  }

}
