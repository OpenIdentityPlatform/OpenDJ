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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS.
 */
package org.opends.server.replication.protocol;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.DataFormatException;

import org.opends.server.replication.common.CSN;
import org.opends.server.util.StaticUtils;

/**
 * This class specifies the parameters of a search request on the ECL.
 * It is used as an interface between the requestor (plugin part)
 */
public class StartECLSessionMsg extends ReplicationMsg
{

  /**
   * This specifies that the ECL is requested from a provided cookie value
   * defined as a MultiDomainServerState.
   */
  public final static short REQUEST_TYPE_FROM_COOKIE = 0;

  /**
   * This specifies that the ECL is requested from a provided interval
   * of change numbers (as defined by draft-good-ldap-changelog [CHANGELOG]
   * and NOT replication CSNs).
   * TODO: not yet implemented
   */
  public final static short REQUEST_TYPE_FROM_DRAFT_CHANGE_NUMBER = 1;

  /**
   * This specifies that the ECL is requested only for the entry that have
   * a CSN matching the provided one.
   * TODO: not yet implemented
   */
  public final static short REQUEST_TYPE_EQUALS_REPL_CHANGE_NUMBER = 2;

  /**
   * This specifies that the request on the ECL is a PERSISTENT search
   * with changesOnly = false.
   */
  public final static short PERSISTENT = 0;

  /**
   * This specifies that the request on the ECL is a NOT a PERSISTENT search.
   */
  public final static short NON_PERSISTENT = 1;

  /**
   * This specifies that the request on the ECL is a PERSISTENT search
   * with changesOnly = true.
   */
  public final static short PERSISTENT_CHANGES_ONLY = 2;

  /** The type of request as defined by REQUEST_TYPE_... */
  private short  eclRequestType;

  /**
   * When eclRequestType = FROM_COOKIE, specifies the provided cookie value.
   */
  private String crossDomainServerState = "";

  /**
   * When eclRequestType = FROM_CHANGE_NUMBER, specifies the provided change
   * number first and last - [CHANGELOG].
   */
  private int    firstDraftChangeNumber = -1;
  private int    lastDraftChangeNumber = -1;

  /**
   * When eclRequestType = EQUALS_REPL_CHANGE_NUMBER, specifies the provided
   * replication CSN.
   */
  private CSN csn;

  /** Specifies whether the search is persistent and changesOnly. */
  private short  isPersistent = NON_PERSISTENT;

  /**
   * A string helping debugging and tracing the client operation related when
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
  public StartECLSessionMsg(byte[] in) throws DataFormatException
  {
    /*
     * The message is stored in the form:
     * <message type><status><assured flag><assured mode><safe data level>
     * <list of referrals urls>
     * (each referral url terminates with 0)
     */

    try
    {
      /* first bytes are the header */
      int pos = 0;

      /* first byte is the type */
      if (in.length < 1 || in[pos++] != MSG_TYPE_START_ECL_SESSION)
      {
        throw new DataFormatException(
          "Input is not a valid " + this.getClass().getCanonicalName());
      }

      // start mode
      int length = getNextLength(in, pos);
      eclRequestType = Short.valueOf(new String(in, pos, length, "UTF-8"));
      pos += length +1;

      // sequenceNumber
      length = getNextLength(in, pos);
      firstDraftChangeNumber =
        Integer.valueOf(new String(in, pos, length, "UTF-8"));
      pos += length +1;

      // stopSequenceNumber
      length = getNextLength(in, pos);
      lastDraftChangeNumber =
        Integer.valueOf(new String(in, pos, length, "UTF-8"));
      pos += length +1;

      // replication CSN
      length = getNextLength(in, pos);
      String csnStr = new String(in, pos, length, "UTF-8");
      pos += length + 1;
      csn = new CSN(csnStr);

      // persistentSearch mode
      length = getNextLength(in, pos);
      isPersistent = Short.valueOf(new String(in, pos, length, "UTF-8"));
      pos += length + 1;

      // generalized state
      length = getNextLength(in, pos);
      crossDomainServerState = new String(in, pos, length, "UTF-8");
      pos += length + 1;

      // operation id
      length = getNextLength(in, pos);
      operationId = new String(in, pos, length, "UTF-8");
      pos += length + 1;

      // excluded DN
      length = getNextLength(in, pos);
      String excludedDNsString = new String(in, pos, length, "UTF-8");
      if (excludedDNsString.length()>0)
      {
        String[] excludedDNsStr = excludedDNsString.split(";");
        Collections.addAll(this.excludedBaseDNs, excludedDNsStr);
      }
      pos += length + 1;

    } catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    } catch (IllegalArgumentException e)
    {
      throw new DataFormatException(e.getMessage());
    }
  }

  /**
   * Creates a new StartSessionMsg message with the given required parameters.
   */
  public StartECLSessionMsg()
  {
    eclRequestType = REQUEST_TYPE_FROM_COOKIE;
    crossDomainServerState = "";
    firstDraftChangeNumber = -1;
    lastDraftChangeNumber = -1;
    csn = new CSN(0, 0, 0);
    isPersistent = NON_PERSISTENT;
    operationId = "-1";
    excludedBaseDNs = new HashSet<String>();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes(short protocolVersion)
  {
    String excludedBaseDNsString =
        StaticUtils.collectionToString(excludedBaseDNs, ";");

    try
    {
      byte[] byteMode = toBytes(eclRequestType);
      byte[] byteSequenceNumber = toBytes(firstDraftChangeNumber);
      byte[] byteStopSequenceNumber = toBytes(lastDraftChangeNumber);
      byte[] byteCSN = csn.toString().getBytes("UTF-8");
      byte[] bytePsearch = toBytes(isPersistent);
      byte[] byteGeneralizedState = toBytes(crossDomainServerState);
      byte[] byteOperationId = toBytes(operationId);
      byte[] byteExcludedDNs = toBytes(excludedBaseDNsString);

      int length =
        byteMode.length + 1 +
        byteSequenceNumber.length + 1 +
        byteStopSequenceNumber.length + 1 +
        byteCSN.length + 1 +
        bytePsearch.length + 1 +
        byteGeneralizedState.length + 1 +
        byteOperationId.length + 1 +
        byteExcludedDNs.length + 1 +
        1;

      byte[] resultByteArray = new byte[length];
      int pos = 0;
      resultByteArray[pos++] = MSG_TYPE_START_ECL_SESSION;
      pos = addByteArray(byteMode, resultByteArray, pos);
      pos = addByteArray(byteSequenceNumber, resultByteArray, pos);
      pos = addByteArray(byteStopSequenceNumber, resultByteArray, pos);
      pos = addByteArray(byteCSN, resultByteArray, pos);
      pos = addByteArray(bytePsearch, resultByteArray, pos);
      pos = addByteArray(byteGeneralizedState, resultByteArray, pos);
      pos = addByteArray(byteOperationId, resultByteArray, pos);
      pos = addByteArray(byteExcludedDNs, resultByteArray, pos);
      return resultByteArray;
    } catch (IOException e)
    {
      // never happens
      return null;
    }
  }

  private byte[] toBytes(int i) throws UnsupportedEncodingException
  {
    return toBytes(String.valueOf(i));
  }

  private byte[] toBytes(String s) throws UnsupportedEncodingException
  {
    return String.valueOf(s).getBytes("UTF-8");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return getClass().getCanonicalName() + " [" +
            " requestType="+ eclRequestType +
            " persistentSearch="       + isPersistent +
            " csn=" + csn +
            " firstDraftChangeNumber=" + firstDraftChangeNumber +
            " lastDraftChangeNumber="  + lastDraftChangeNumber +
            " generalizedState="       + crossDomainServerState +
            " operationId="            + operationId +
            " excludedDNs="            + excludedBaseDNs + "]";
  }

  /**
   * Getter on the changer number start.
   * @return the changer number start.
   */
  public int getFirstDraftChangeNumber()
  {
    return firstDraftChangeNumber;
  }

  /**
   * Getter on the changer number stop.
   * @return the change number stop.
   */
  public int getLastDraftChangeNumber()
  {
    return lastDraftChangeNumber;
  }

  /**
   * Setter on the first changer number (as defined by [CHANGELOG]).
   * @param firstDraftChangeNumber the provided first change number.
   */
  public void setFirstDraftChangeNumber(int firstDraftChangeNumber)
  {
    this.firstDraftChangeNumber = firstDraftChangeNumber;
  }

  /**
   * Setter on the last changer number (as defined by [CHANGELOG]).
   * @param lastDraftChangeNumber the provided last change number.
   */
  public void setLastDraftChangeNumber(int lastDraftChangeNumber)
  {
    this.lastDraftChangeNumber = lastDraftChangeNumber;
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
  public short getECLRequestType()
  {
    return eclRequestType;
  }

  /**
   * Setter on the type of request.
   * @param eclRequestType the provided type of request.
   */
  public void setECLRequestType(short eclRequestType)
  {
    this.eclRequestType = eclRequestType;
  }

  /**
   * Getter on the persistent property of the search request on the ECL.
   * @return the persistent property.
   */
  public short isPersistent()
  {
    return this.isPersistent;
  }

  /**
   * Setter on the persistent property of the search request on the ECL.
   * @param isPersistent the provided persistent property.
   */
  public void setPersistent(short isPersistent)
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
   * Getter on the list of excluded baseDNs.
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
