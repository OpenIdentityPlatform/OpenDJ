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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.replication.protocol;

import java.util.zip.DataFormatException;

import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.types.DN;
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
  private final DN baseDN;

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
      DN baseDN, long changeNumber)
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
   * @throws NotSupportedOldVersionPDUException when it occurs.
   */
  ECLUpdateMsg(byte[] in) throws DataFormatException,
      NotSupportedOldVersionPDUException
  {
    try
    {
      final ByteArrayScanner scanner = new ByteArrayScanner(in);
      if (scanner.nextByte() != MSG_TYPE_ECL_UPDATE)
      {
        throw new DataFormatException("byte[] is not a valid " +
            getClass().getCanonicalName());
      }

      this.cookie = new MultiDomainServerState(scanner.nextString());
      this.baseDN = scanner.nextDN();
      this.changeNumber = scanner.nextIntUTF8();

      // Decode the msg
      this.updateMsg = (LDAPUpdateMsg) ReplicationMsg.generateMsg(
          scanner.remainingBytesZeroTerminated(),
          ProtocolVersion.getCurrentVersion());
    }
    catch(DirectoryException de)
    {
      throw new DataFormatException(de.toString());
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
  public DN getBaseDN()
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

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return "ECLUpdateMsg:[" +
    " updateMsg: " + updateMsg +
    " cookie: " + cookie +
    " changeNumber: " + changeNumber +
    " serviceId: " + baseDN + "]";
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    builder.appendByte(MSG_TYPE_ECL_UPDATE);
    builder.appendString(String.valueOf(cookie));
    builder.appendDN(baseDN);
    // FIXME JNR Changing the line below to use long would require a protocol
    // version change. Leave it like this for now until the need arises.
    builder.appendIntUTF8((int) changeNumber);
    builder.appendZeroTerminatedByteArray(updateMsg.getBytes(protocolVersion));
    return builder.toByteArray();
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
