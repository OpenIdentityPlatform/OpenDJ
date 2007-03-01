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
package org.opends.server.controls;



import java.util.ArrayList;
import java.util.Set;

import org.opends.server.protocols.asn1.ASN1Boolean;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Integer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.Control;

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the persistent search control defined in
 * draft-ietf-ldapext-psearch.  It makes it possible for clients to be notified
 * of changes to information in the Directory Server as they occur.
 */
public class PersistentSearchControl
       extends Control
{



  // Indicates whether to only return entries that have been updated since the
  // beginning of the search.
  private boolean changesOnly;

  // Indicates whether entries returned as a result of changes to directory data
  // should include the entry change notification control.
  private boolean returnECs;

  // The set of change types associated with this control.
  private Set<PersistentSearchChangeType> changeTypes;



  /**
   * Creates a new persistent search control with the provided information.
   *
   * @param  changeTypes  The set of change types for which to provide
   *                      notification to the client.
   * @param  changesOnly  Indicates whether to only return changes that match
   *                      the associated search criteria, or to also return all
   *                      existing entries that match the filter.
   * @param  returnECs    Indicates whether to include the entry change
   *                      notification control in updated entries that match the
   *                      associated search criteria.
   */
  public PersistentSearchControl(Set<PersistentSearchChangeType> changeTypes,
                                 boolean changesOnly, boolean returnECs)
  {
    super(OID_PERSISTENT_SEARCH, true,
          encodeValue(changeTypes, changesOnly, returnECs));


    this.changeTypes = changeTypes;
    this.changesOnly = changesOnly;
    this.returnECs   = returnECs;
  }



  /**
   * Creates a new persistent search control with the provided information.
   *
   * @param  oid          The OID to use for the control.
   * @param  isCritical   Indicates whether the control should be considered
   *                      critical for the operation processing.
   * @param  changeTypes  The set of change types for which to provide
   *                      notification to the client.
   * @param  changesOnly  Indicates whether to only return changes that match
   *                      the associated search criteria, or to also return all
   *                      existing entries that match the filter.
   * @param  returnECs    Indicates whether to include the entry change
   *                      notification control in updated entries that match the
   *                      associated search criteria.
   */
  public PersistentSearchControl(String oid, boolean isCritical,
                                 Set<PersistentSearchChangeType> changeTypes,
                                 boolean changesOnly, boolean returnECs)
  {
    super(oid, isCritical, encodeValue(changeTypes, changesOnly, returnECs));


    this.changeTypes = changeTypes;
    this.changesOnly = changesOnly;
    this.returnECs   = returnECs;
  }



  /**
   * Creates a new persistent search control with the provided information.
   *
   * @param  oid           The OID to use for the control.
   * @param  isCritical    Indicates whether the control should be considered
   *                       critical for the operation processing.
   * @param  changeTypes   The set of change types for which to provide
   *                       notification to the client.
   * @param  changesOnly   Indicates whether to only return changes that match
   *                       the associated search criteria, or to also return all
   *                       existing entries that match the filter.
   * @param  returnECs     Indicates whether to include the entry change
   *                       notification control in updated entries that match
   *                       the associated search criteria.
   * @param  encodedValue  The pre-encoded value for the control.
   */
  private PersistentSearchControl(String oid, boolean isCritical,
                                  Set<PersistentSearchChangeType> changeTypes,
                                  boolean changesOnly, boolean returnECs,
                                  ASN1OctetString encodedValue)
  {
    super(oid, isCritical, encodedValue);


    this.changeTypes = changeTypes;
    this.changesOnly = changesOnly;
    this.returnECs   = returnECs;
  }



  /**
   * Encodes the provided information into an ASN.1 octet string suitable for
   * use as the control value.
   *
   * @param  changeTypes  The set of change types for which to provide
   *                      notification to the client.
   * @param  changesOnly  Indicates whether to only return changes that match
   *                      the associated search criteria, or to also return all
   *                      existing entries that match the filter.
   * @param  returnECs    Indicates whether to include the entry change
   *                      notification control in updated entries that match the
   *                      associated search criteria.
   *
   * @return  An ASN.1 octet string containing the encoded information.
   */
  private static ASN1OctetString encodeValue(Set<PersistentSearchChangeType>
                                                  changeTypes,
                                             boolean changesOnly,
                                             boolean returnECs)
  {
    ArrayList<ASN1Element> elements =
         new ArrayList<ASN1Element>(3);
    elements.add(new ASN1Integer(
         PersistentSearchChangeType.changeTypesToInt(changeTypes)));
    elements.add(new ASN1Boolean(changesOnly));
    elements.add(new ASN1Boolean(returnECs));


    return new ASN1OctetString(new ASN1Sequence(elements).encode());
  }



  /**
   * Creates a new persistent search control from the contents of the provided
   * control.
   *
   * @param  control  The generic control containing the information to use to
   *                  create this persistent search control.
   *
   * @return  The persistent search control decoded from the provided control.
   *
   * @throws  LDAPException  If this control cannot be decoded as a valid
   *                         persistent search control.
   */
  public static PersistentSearchControl decodeControl(Control control)
         throws LDAPException
  {
    if (! control.hasValue())
    {
      int    msgID   = MSGID_PSEARCH_NO_CONTROL_VALUE;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }


    boolean                         changesOnly;
    boolean                         returnECs;
    Set<PersistentSearchChangeType> changeTypes;
    try
    {
      ArrayList<ASN1Element> elements =
           ASN1Sequence.decodeAsSequence(control.getValue().value()).elements();
      if (elements.size() != 3)
      {
        int    msgID   = MSGID_PSEARCH_INVALID_ELEMENT_COUNT;
        String message = getMessage(msgID, elements.size());
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
      }

      int changeTypesValue = elements.get(0).decodeAsInteger().intValue();
      changeTypes = PersistentSearchChangeType.intToTypes(changeTypesValue);
      changesOnly = elements.get(1).decodeAsBoolean().booleanValue();
      returnECs   = elements.get(2).decodeAsBoolean().booleanValue();
    }
    catch (LDAPException le)
    {
      throw le;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_PSEARCH_CANNOT_DECODE_VALUE;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message, e);
    }


    return new PersistentSearchControl(control.getOID(), control.isCritical(),
                                       changeTypes, changesOnly, returnECs,
                                       control.getValue());
  }



  /**
   * Retrieves the set of change types for this persistent search control.
   *
   * @return  The set of change types for this persistent search control.
   */
  public Set<PersistentSearchChangeType> getChangeTypes()
  {
    return changeTypes;
  }



  /**
   * Specifies the set of change types for this persistent search control.
   *
   * @param  changeTypes  The set of change types for this persistent search
   *                      control.
   */
  public void setChangeTypes(Set<PersistentSearchChangeType> changeTypes)
  {
    this.changeTypes = changeTypes;

    setValue(encodeValue(changeTypes, changesOnly, returnECs));
  }



  /**
   * Indicates whether to only return changes that match the associated search
   * criteria, or to also return all existing entries that match the filter.
   *
   * @return  <CODE>true</CODE> if only changes to matching entries should be
   *          returned, or <CODE>false</CODE> if existing matches should also be
   *          included.
   */
  public boolean getChangesOnly()
  {
    return changesOnly;
  }



  /**
   * Specifies whether to only return changes that match teh associated search
   * criteria, or to also return all existing entries that match the filter.
   *
   * @param  changesOnly  Indicates whether to only return changes that match
   *                      the associated search criteria, or to also return all
   *                      existing entries that match the filter.
   */
  public void setChangesOnly(boolean changesOnly)
  {
    this.changesOnly = changesOnly;

    setValue(encodeValue(changeTypes, changesOnly, returnECs));
  }



  /**
   * Indicates whether to include the entry change notification control in
   * entries returned to the client as the result of a change in the Directory
   * Server data.
   *
   * @return  <CODE>true</CODE> if entry change notification controls should be
   *          included in applicable entries, or <CODE>false</CODE> if not.
   */
  public boolean getReturnECs()
  {
    return returnECs;
  }



  /**
   * Specifies whether to include the entry change notification control in
   * entries returned to the client as a result of a change in the Directory
   * Server data.
   *
   * @param  returnECs  Indicates whether to include the entry change
   *                    notification control in updated entries that match the
   *                    associated search criteria.
   */
  public void setReturnECs(boolean returnECs)
  {
    this.returnECs = returnECs;

    setValue(encodeValue(changeTypes, changesOnly, returnECs));
  }



  /**
   * Retrieves a string representation of this persistent search control.
   *
   * @return  A string representation of this persistent search control.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this persistent search control to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("PersistentSearchControl(changeTypes=\"");
    PersistentSearchChangeType.changeTypesToString(changeTypes, buffer);
    buffer.append("\",changesOnly=");
    buffer.append(changesOnly);
    buffer.append(",returnECs=");
    buffer.append(returnECs);
    buffer.append(")");
  }
}

