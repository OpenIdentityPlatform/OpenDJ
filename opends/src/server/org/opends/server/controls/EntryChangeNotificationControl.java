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
import org.opends.messages.Message;



import java.util.ArrayList;

import org.opends.server.protocols.asn1.ASN1Constants;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Enumerated;
import org.opends.server.protocols.asn1.ASN1Long;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.LDAPException;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the entry change notification control defined in
 * draft-ietf-ldapext-psearch.  It may be included in entries returned in
 * response to a persistent search operation.
 */
public class EntryChangeNotificationControl
       extends Control
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  // The previous DN for this change notification control.
  private DN previousDN;

  // The change number for this change notification control.
  private long changeNumber;

  // The change type for this change notification control.
  private PersistentSearchChangeType changeType;



  /**
   * Creates a new entry change notification control with the provided
   * information.
   *
   * @param  changeType    The change type for this change notification control.
   * @param  changeNumber  The change number for the associated change, or a
   *                       negative value if no change number is available.
   */
  public EntryChangeNotificationControl(PersistentSearchChangeType changeType,
                                        long changeNumber)
  {
    super(OID_ENTRY_CHANGE_NOTIFICATION, false,
          encodeValue(changeType, null, changeNumber));


    this.changeType   = changeType;
    this.changeNumber = changeNumber;

    previousDN = null;
  }



  /**
   * Creates a new entry change notification control with the provided
   * information.
   *
   * @param  changeType    The change type for this change notification control.
   * @param  previousDN    The DN that the entry had prior to a modify DN
   *                       operation, or <CODE>null</CODE> if the operation was
   *                       not a modify DN.
   * @param  changeNumber  The change number for the associated change, or a
   *                       negative value if no change number is available.
   */
  public EntryChangeNotificationControl(PersistentSearchChangeType changeType,
                                        DN previousDN, long changeNumber)
  {
    super(OID_ENTRY_CHANGE_NOTIFICATION, false,
          encodeValue(changeType, previousDN, changeNumber));


    this.changeType   = changeType;
    this.previousDN   = previousDN;
    this.changeNumber = changeNumber;
  }



  /**
   * Creates a new entry change notification control with the provided
   * information.
   *
   * @param  oid           The OID to use for this control.
   * @param  isCritical    Indicates whether this control should be considered
   *                       critical to the operation processing.
   * @param  changeType    The change type for this change notification control.
   * @param  previousDN    The DN that the entry had prior to a modify DN
   *                       operation, or <CODE>null</CODE> if the operation was
   *                       not a modify DN.
   * @param  changeNumber  The change number for the associated change, or a
   *                       negative value if no change number is available.
   */
  public EntryChangeNotificationControl(String oid, boolean isCritical,
                                        PersistentSearchChangeType changeType,
                                        DN previousDN, long changeNumber)
  {
    super(oid, isCritical, encodeValue(changeType, previousDN, changeNumber));


    this.changeType   = changeType;
    this.previousDN   = previousDN;
    this.changeNumber = changeNumber;
  }



  /**
   * Creates a new entry change notification control with the provided
   * information.
   *
   * @param  oid           The OID to use for this control.
   * @param  isCritical    Indicates whether this control should be considered
   *                       critical to the operation processing.
   * @param  changeType    The change type for this change notification control.
   * @param  previousDN    The DN that the entry had prior to a modify DN
   *                       operation, or <CODE>null</CODE> if the operation was
   *                       not a modify DN.
   * @param  changeNumber  The change number for the associated change, or a
   *                       negative value if no change number is available.
   * @param  encodedValue  The pre-encoded value for this change notification
   *                       control.
   */
  private EntryChangeNotificationControl(String oid, boolean isCritical,
                                         PersistentSearchChangeType changeType,
                                         DN previousDN, long changeNumber,
                                         ASN1OctetString encodedValue)
  {
    super(oid, isCritical, encodedValue);


    this.changeType   = changeType;
    this.previousDN   = previousDN;
    this.changeNumber = changeNumber;
  }



  /**
   * Encodes the provided information into an ASN.1 octet string suitable for
   * use as the control value.
   *
   * @param  changeType    The change type for this change notification control.
   * @param  previousDN    The DN that the entry had prior to a modify DN
   *                       operation, or <CODE>null</CODE> if the operation was
   *                       not a modify DN.
   * @param  changeNumber  The change number for the associated change, or a
   *                       negative value if no change number is available.
   *
   * @return  An ASN.1 octet string containing the encoded information.
   */
  private static ASN1OctetString encodeValue(PersistentSearchChangeType
                                                  changeType,
                                             DN previousDN, long changeNumber)
  {
    ArrayList<ASN1Element> elements =
         new ArrayList<ASN1Element>(3);
    elements.add(new ASN1Enumerated(changeType.intValue()));

    if (previousDN != null)
    {
      elements.add(new ASN1OctetString(previousDN.toString()));
    }

    if (changeNumber > 0)
    {
      elements.add(new ASN1Long(changeNumber));
    }


    return new ASN1OctetString(new ASN1Sequence(elements).encode());
  }



  /**
   * Creates a new entry change notification control from the contents of the
   * provided control.
   *
   * @param  control  The generic control containing the information to use to
   *                  create this entry change notification control.
   *
   * @return  The entry change notification control decoded from the provided
   *          control.
   *
   * @throws  LDAPException  If this control cannot be decoded as a valid
   *                         entry change notification control.
   */
  public static EntryChangeNotificationControl decodeControl(Control control)
         throws LDAPException
  {
    if (! control.hasValue())
    {
      Message message = ERR_ECN_NO_CONTROL_VALUE.get();
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message);
    }


    DN                         previousDN   = null;
    long                       changeNumber = -1;
    PersistentSearchChangeType changeType;
    try
    {
      ArrayList<ASN1Element> elements =
           ASN1Sequence.decodeAsSequence(control.getValue().value()).elements();
      if ((elements.size() < 1) || (elements.size() > 3))
      {
        Message message = ERR_ECN_INVALID_ELEMENT_COUNT.get(elements.size());
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message);
      }

      int changeTypeValue = elements.get(0).decodeAsEnumerated().intValue();
      changeType = PersistentSearchChangeType.valueOf(changeTypeValue);

      if (elements.size() == 2)
      {
        ASN1Element e = elements.get(1);
        if (e.getType() == ASN1Constants.UNIVERSAL_OCTET_STRING_TYPE)
        {
          if (changeType != PersistentSearchChangeType.MODIFY_DN)
          {
            Message message =
                ERR_ECN_ILLEGAL_PREVIOUS_DN.get(String.valueOf(changeType));
            throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message);
          }

          ASN1OctetString rawPreviousDN = e.decodeAsOctetString();
          previousDN = DN.decode(rawPreviousDN);
        }
        else if (e.getType() == ASN1Constants.UNIVERSAL_INTEGER_TYPE)
        {
          changeNumber = e.decodeAsLong().longValue();
        }
        else
        {
          Message message =
              ERR_ECN_INVALID_ELEMENT_TYPE.get(byteToHex(e.getType()));
          throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message);
        }
      }
      else if (elements.size() == 3)
      {
        if (changeType != PersistentSearchChangeType.MODIFY_DN)
        {
          Message message =
              ERR_ECN_ILLEGAL_PREVIOUS_DN.get(String.valueOf(changeType));
          throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message);
        }

        ASN1OctetString rawPreviousDN = elements.get(1).decodeAsOctetString();
        previousDN = DN.decode(rawPreviousDN);

        changeNumber = elements.get(2).decodeAsLong().longValue();
      }
    }
    catch (LDAPException le)
    {
      throw le;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_ECN_CANNOT_DECODE_VALUE.get(getExceptionMessage(e));
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message, e);
    }


    return new EntryChangeNotificationControl(control.getOID(),
                                              control.isCritical(), changeType,
                                              previousDN, changeNumber,
                                              control.getValue());
  }



  /**
   * Retrieves the change type for this entry change notification control.
   *
   * @return  The change type for this entry change notification control.
   */
  public PersistentSearchChangeType getChangeType()
  {
    return changeType;
  }



  /**
   * Sets the change type for this entry change notification control.
   *
   * @param  changeType  The change type for this entry change notification
   *                     control.
   */
  public void setChangeType(PersistentSearchChangeType changeType)
  {
    this.changeType = changeType;

    setValue(encodeValue(changeType, previousDN, changeNumber));
  }



  /**
   * Retrieves the previous DN for this entry change notification control.
   *
   * @return  The previous DN for this entry change notification control, or
   *          <CODE>null</CODE> if there is none.
   */
  public DN getPreviousDN()
  {
    return previousDN;
  }



  /**
   * Specifies the previous DN for this entry change notification control.
   *
   * @param  previousDN  The previous DN for this entry change notification
   *                     control.
   */
  public void setPreviousDN(DN previousDN)
  {
    this.previousDN = previousDN;

    setValue(encodeValue(changeType, previousDN, changeNumber));
  }



  /**
   * Retrieves the change number for this entry change notification control.
   *
   * @return  The change number for this entry change notification control, or a
   *          negative value if no change number is available.
   */
  public long getChangeNumber()
  {
    return changeNumber;
  }



  /**
   * Specifies the change number for this entry change notification control.
   *
   * @param  changeNumber  The change number for this entry change notification
   *                       control.
   */
  public void setChangeNumber(long changeNumber)
  {
    this.changeNumber = changeNumber;

    setValue(encodeValue(changeType, previousDN, changeNumber));
  }



  /**
   * Retrieves a string representation of this entry change notification
   * control.
   *
   * @return  A string representation of this entry change notification control.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this entry change notification control
   * to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("EntryChangeNotificationControl(changeType=");
    buffer.append(changeType.toString());

    if (previousDN != null)
    {
      buffer.append(",previousDN=\"");
      buffer.append(previousDN.toString());
      buffer.append("\"");
    }

    if (changeNumber > 0)
    {
      buffer.append(",changeNumber=");
      buffer.append(changeNumber);
    }

    buffer.append(")");
  }
}

