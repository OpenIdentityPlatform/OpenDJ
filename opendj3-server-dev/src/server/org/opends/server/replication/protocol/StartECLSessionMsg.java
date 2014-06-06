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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2014 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.DataFormatException;

import org.forgerock.util.Utils;
import org.opends.server.replication.common.CSN;

/**
 * This class specifies the parameters of a search request on the ECL.
 * It is used as an interface between the requestor (plugin part)
 */
public class StartECLSessionMsg extends ReplicationMsg
{

  /**
   * Type of request made to the External Changelog.
   */
  public enum ECLRequestType
  {

    /**
     * This specifies that the ECL is requested from a provided cookie value
     * defined as a MultiDomainServerState.
     */
    REQUEST_TYPE_FROM_COOKIE,

    /**
     * This specifies that the ECL is requested from a provided interval
     * of change numbers (as defined by draft-good-ldap-changelog [CHANGELOG]
     * and NOT replication CSNs).
     * TODO: not yet implemented
     */
    REQUEST_TYPE_FROM_CHANGE_NUMBER,

    /**
     * This specifies that the ECL is requested only for the entry that have a
     * CSN matching the provided one.
     * TODO: not yet implemented
     */
    REQUEST_TYPE_EQUALS_REPL_CHANGE_NUMBER
  }

  /**
   * Whether the current External Changelog search is persistent and requires to
   * receive only new changes or already existing changes as well.
   */
  public enum Persistent
  {
    /**
     * This specifies that the request on the ECL is a PERSISTENT search with
     * changesOnly = false.
     * <p>
     * It will return the content of the changelog DB as it is now, plus any
     * subsequent changes.
     */
    PERSISTENT,

    /**
     * This specifies that the request on the ECL is a NOT a PERSISTENT search.
     * <p>
     * It will only return the content of the changelog DB as it is now, and
     * stop. It will NOT be turned into a persistent search that can return
     * subsequent changes.
     */
    NON_PERSISTENT,

    /**
     * This specifies that the request on the ECL is a PERSISTENT search with
     * changesOnly = true.
     * <p>
     * It will only return subsequent changes that do not exist yet in the
     * changelog DB.
     */
    PERSISTENT_CHANGES_ONLY
  }

  /** The type of request as defined by REQUEST_TYPE_... */
  private ECLRequestType eclRequestType;

  /**
   * When eclRequestType = FROM_COOKIE, specifies the provided cookie value.
   */
  private String crossDomainServerState = "";

  /**
   * When eclRequestType = FROM_CHANGE_NUMBER, specifies the provided change
   * number first and last - [CHANGELOG].
   */
  private long firstChangeNumber = -1;
  private long lastChangeNumber = -1;

  /**
   * When eclRequestType = EQUALS_REPL_CHANGE_NUMBER, specifies the provided
   * replication CSN.
   */
  private CSN csn;

  /**
   * Specifies whether the search is persistent and changesOnly.
   */
  private Persistent isPersistent = Persistent.NON_PERSISTENT;

  /**
   * This is a string identifying the operation, provided by the client part of
   * the ECL, used to help interpretation of messages logged.
   * <p>
   * It helps debugging and tracing the client operation related when
   * processing, on the RS side, a request on the ECL.
   */
  private String operationId = "";

  /** Excluded domains. */
  private Set<String> excludedBaseDNs = new HashSet<String>();

  /**
   * Creates a new StartSessionMsg message from its encoded form.
   *
   * @param in The byte array containing the encoded form of the message.
   * @throws java.util.zip.DataFormatException If the byte array does not
   * contain a valid encoded form of the message.
   */
  StartECLSessionMsg(byte[] in) throws DataFormatException
  {
    /*
     * The message is stored in the form:
     * <message type><status><assured flag><assured mode><safe data level>
     * <list of referrals urls>
     * (each referral url terminates with 0)
     */
    final ByteArrayScanner scanner = new ByteArrayScanner(in);
    final byte msgType = scanner.nextByte();
    if (msgType != MSG_TYPE_START_ECL_SESSION)
    {
      throw new DataFormatException("Input is not a valid "
          + getClass().getCanonicalName());
    }

    eclRequestType = ECLRequestType.values()[scanner.nextIntUTF8()];
    firstChangeNumber = scanner.nextIntUTF8();
    lastChangeNumber = scanner.nextIntUTF8();
    csn = scanner.nextCSNUTF8();
    isPersistent = Persistent.values()[scanner.nextIntUTF8()];
    crossDomainServerState = scanner.nextString();
    operationId = scanner.nextString();
    final String excludedDNsString = scanner.nextString();
    if (excludedDNsString != null && excludedDNsString.length() > 0)
    {
      Collections.addAll(excludedBaseDNs, excludedDNsString.split(";"));
    }
  }

  /**
   * Creates a new StartSessionMsg message with the given required parameters.
   */
  public StartECLSessionMsg()
  {
    eclRequestType = ECLRequestType.REQUEST_TYPE_FROM_COOKIE;
    crossDomainServerState = "";
    firstChangeNumber = -1;
    lastChangeNumber = -1;
    csn = new CSN(0, 0, 0);
    isPersistent = Persistent.NON_PERSISTENT;
    operationId = "-1";
    excludedBaseDNs = new HashSet<String>();
  }

  /** {@inheritDoc} */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    final ByteArrayBuilder builder = new ByteArrayBuilder();
    builder.append(MSG_TYPE_START_ECL_SESSION);
    builder.appendUTF8(eclRequestType.ordinal());
    // FIXME JNR Changing the lines below to use long would require a protocol
    // version change. Leave it like this for now until the need arises.
    builder.appendUTF8((int) firstChangeNumber);
    builder.appendUTF8((int) lastChangeNumber);
    builder.appendUTF8(csn);
    builder.appendUTF8(isPersistent.ordinal());
    builder.append(crossDomainServerState);
    builder.append(operationId);
    builder.append(Utils.joinAsString(";", excludedBaseDNs));
    return builder.toByteArray();
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + " [" +
            " requestType="+ eclRequestType +
            " persistentSearch="       + isPersistent +
            " csn="                    + csn +
            " firstChangeNumber="      + firstChangeNumber +
            " lastChangeNumber="       + lastChangeNumber +
            " generalizedState="       + crossDomainServerState +
            " operationId="            + operationId +
            " excludedDNs="            + excludedBaseDNs + "]";
  }

  /**
   * Getter on the changer number start.
   * @return the changer number start.
   */
  public long getFirstChangeNumber()
  {
    return firstChangeNumber;
  }

  /**
   * Specifies the last changer number requested.
   *
   * @return the last change number requested.
   */
  public long getLastChangeNumber()
  {
    return lastChangeNumber;
  }

  /**
   * Setter on the first changer number (as defined by [CHANGELOG]).
   * @param firstChangeNumber the provided first change number.
   */
  public void setFirstChangeNumber(long firstChangeNumber)
  {
    this.firstChangeNumber = firstChangeNumber;
  }

  /**
   * Setter on the last changer number (as defined by [CHANGELOG]).
   * @param lastChangeNumber the provided last change number.
   */
  public void setLastChangeNumber(long lastChangeNumber)
  {
    this.lastChangeNumber = lastChangeNumber;
  }

  /**
   * Getter on the replication CSN.
   * @return the replication CSN.
   */
  public CSN getCSN()
  {
    return csn;
  }

  /**
   * Setter on the replication CSN.
   * @param csn the provided replication CSN.
   */
  public void setCSN(CSN csn)
  {
    this.csn = csn;
  }
  /**
   * Getter on the type of request.
   * @return the type of request.
   */
  public ECLRequestType getECLRequestType()
  {
    return eclRequestType;
  }

  /**
   * Setter on the type of request.
   * @param eclRequestType the provided type of request.
   */
  public void setECLRequestType(ECLRequestType eclRequestType)
  {
    this.eclRequestType = eclRequestType;
  }

  /**
   * Getter on the persistent property of the search request on the ECL.
   * @return the persistent property.
   */
  public Persistent getPersistent()
  {
    return this.isPersistent;
  }

  /**
   * Setter on the persistent property of the search request on the ECL.
   * @param isPersistent the provided persistent property.
   */
  public void setPersistent(Persistent isPersistent)
  {
    this.isPersistent = isPersistent;
  }

  /**
   * Getter of the cross domain server state.
   * @return the cross domain server state.
   */
  public String getCrossDomainServerState()
  {
    return this.crossDomainServerState;
  }

  /**
   * Setter of the cross domain server state.
   * @param crossDomainServerState the provided cross domain server state.
   */
  public void setCrossDomainServerState(String crossDomainServerState)
  {
    this.crossDomainServerState = crossDomainServerState;
  }

  /**
   * Setter of the operation id.
   * @param operationId The provided operation id.
   */
  public void setOperationId(String operationId)
  {
    this.operationId = operationId;
  }

  /**
   * Getter on the operation id.
   * @return the operation id.
   */
  public String getOperationId()
  {
    return this.operationId;
  }

  /**
   * Getter on the list of excluded baseDNs (like cn=admin, ...).
   *
   * @return the list of excluded baseDNs.
   */
  public Set<String> getExcludedBaseDNs()
  {
    return this.excludedBaseDNs;
  }

  /**
   * Setter on the list of excluded baseDNs.
   *
   * @param excludedBaseDNs
   *          the provided list of excluded baseDNs.
   */
  public void setExcludedDNs(Set<String> excludedBaseDNs)
  {
    this.excludedBaseDNs = excludedBaseDNs;
  }

}
