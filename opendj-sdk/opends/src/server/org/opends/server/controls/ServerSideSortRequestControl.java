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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.controls;



import java.util.ArrayList;
import java.util.StringTokenizer;

import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1Boolean;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Control;
import org.opends.server.types.LDAPException;
import org.opends.server.types.SortKey;
import org.opends.server.types.SortOrder;

import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the server-side sort request control as defined in RFC
 * 2891 section 1.1.  The ASN.1 description for the control value is:
 * <BR><BR>
 * <PRE>
 * SortKeyList ::= SEQUENCE OF SEQUENCE {
 *            attributeType   AttributeDescription,
 *            orderingRule    [0] MatchingRuleId OPTIONAL,
 *            reverseOrder    [1] BOOLEAN DEFAULT FALSE }
 * </PRE>
 */
public class ServerSideSortRequestControl
       extends Control
{
  /**
   * The BER type to use when encoding the orderingRule element.
   */
  private static final byte TYPE_ORDERING_RULE_ID = (byte) 0x80;



  /**
   * The BER type to use when encoding the reverseOrder element.
   */
  private static final byte TYPE_REVERSE_ORDER = (byte) 0x81;



  // The sort order associated with this control.
  private SortOrder sortOrder;



  /**
   * Creates a new server-side sort request control based on the provided sort
   * order.
   *
   * @param  sortOrder  The sort order to use for this control.
   */
  public ServerSideSortRequestControl(SortOrder sortOrder)
  {
    super(OID_SERVER_SIDE_SORT_REQUEST_CONTROL, false,
          encodeControlValue(sortOrder));

    this.sortOrder = sortOrder;
  }



  /**
   * Creates a new server-side sort request control based on the definition in
   * the provided sort order string.  This is only intended for client-side use,
   * and controls created with this constructor should not attempt to use the
   * generated sort order for any purpose.
   *
   * @param  sortOrderString  The string representation of the sort order to use
   *                          for the control.
   *
   * @throws  LDAPException  If the provided sort order string could not be
   *                         decoded.
   */
  public ServerSideSortRequestControl(String sortOrderString)
         throws LDAPException
  {
    super(OID_SERVER_SIDE_SORT_REQUEST_CONTROL, false,
          encodeControlValue(sortOrderString));

    this.sortOrder = null;
  }



  /**
   * Creates a new server-side sort request control with the provided
   * information.
   *
   * @param  oid           The OID to use for this control.
   * @param  isCritical    Indicates whether support for this control should be
   *                       considered a critical part of the server processing.
   * @param  controlValue  The encoded value for this control.
   * @param  sortOrder     sort order associated with this server-side sort
   *                       control.
   */
  private ServerSideSortRequestControl(String oid, boolean isCritical,
                                       ASN1OctetString controlValue,
                                       SortOrder sortOrder)
  {
    super(oid, isCritical, controlValue);

    this.sortOrder = sortOrder;
  }



  /**
   * Retrieves the sort order for this server-side sort request control.
   *
   * @return  The sort order for this server-side sort request control.
   */
  public SortOrder getSortOrder()
  {
    return sortOrder;
  }



  /**
   * Encodes the provided sort order object in a manner suitable for use as the
   * value of this control.
   *
   * @param  sortOrder  The sort order to be encoded.
   *
   * @return  The ASN.1 octet string containing the encoded sort order.
   */
  private static ASN1OctetString encodeControlValue(SortOrder sortOrder)
  {
    SortKey[] sortKeys = sortOrder.getSortKeys();
    ArrayList<ASN1Element> keyList =
         new ArrayList<ASN1Element>(sortKeys.length);
    for (SortKey sortKey : sortKeys)
    {
      ArrayList<ASN1Element> elementList = new ArrayList<ASN1Element>(3);
      elementList.add(new ASN1OctetString(
                               sortKey.getAttributeType().getNameOrOID()));

      if (sortKey.getOrderingRule() != null)
      {
        elementList.add(new ASN1OctetString(TYPE_ORDERING_RULE_ID,
                                 sortKey.getOrderingRule().getNameOrOID()));
      }

      if (! sortKey.ascending())
      {
        elementList.add(new ASN1Boolean(TYPE_REVERSE_ORDER, false));
      }

      keyList.add(new ASN1Sequence(elementList));
    }

    return new ASN1OctetString(new ASN1Sequence(keyList).encode());
  }



  /**
   * Encodes the provided sort order string in a manner suitable for use as the
   * value of this control.
   *
   * @param  sortOrderString  The sort order string to be encoded.
   *
   * @return  The ASN.1 octet string containing the encoded sort order.
   *
   * @throws  LDAPException  If the provided sort order string cannot be decoded
   *                         to create the control value.
   */
  private static ASN1OctetString encodeControlValue(String sortOrderString)
          throws LDAPException
  {
    StringTokenizer tokenizer = new StringTokenizer(sortOrderString, ",");

    ArrayList<ASN1Element> keyList = new ArrayList<ASN1Element>();
    while (tokenizer.hasMoreTokens())
    {
      String token = tokenizer.nextToken().trim();
      boolean reverseOrder = false;
      if (token.startsWith("-"))
      {
        reverseOrder = true;
        token = token.substring(1);
      }
      else if (token.startsWith("+"))
      {
        token = token.substring(1);
      }

      int colonPos = token.indexOf(':');
      if (colonPos < 0)
      {
        if (token.length() == 0)
        {
          int    msgID   = MSGID_SORTREQ_CONTROL_NO_ATTR_NAME;
          String message = getMessage(msgID, sortOrderString);
          throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                  message);
        }

        if (reverseOrder)
        {
          ArrayList<ASN1Element> elementList = new ArrayList<ASN1Element>(2);
          elementList.add(new ASN1OctetString(token));
          elementList.add(new ASN1Boolean(TYPE_REVERSE_ORDER, reverseOrder));
          keyList.add(new ASN1Sequence(elementList));
        }
        else
        {
          ArrayList<ASN1Element> elementList = new ArrayList<ASN1Element>(1);
          elementList.add(new ASN1OctetString(token));
          keyList.add(new ASN1Sequence(elementList));
        }
      }
      else if (colonPos == 0)
      {
        int    msgID   = MSGID_SORTREQ_CONTROL_NO_ATTR_NAME;
        String message = getMessage(msgID, sortOrderString);
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                message);
      }
      else if (colonPos == (token.length() - 1))
      {
        int    msgID   = MSGID_SORTREQ_CONTROL_NO_MATCHING_RULE;
        String message = getMessage(msgID, sortOrderString);
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                message);
      }
      else
      {
        String attrName = token.substring(0, colonPos);
        String ruleID   = token.substring(colonPos+1);

        if (reverseOrder)
        {
          ArrayList<ASN1Element> elementList = new ArrayList<ASN1Element>(3);
          elementList.add(new ASN1OctetString(attrName));
          elementList.add(new ASN1OctetString(TYPE_ORDERING_RULE_ID, ruleID));
          elementList.add(new ASN1Boolean(TYPE_REVERSE_ORDER, reverseOrder));
          keyList.add(new ASN1Sequence(elementList));
        }
        else
        {
          ArrayList<ASN1Element> elementList = new ArrayList<ASN1Element>(2);
          elementList.add(new ASN1OctetString(attrName));
          elementList.add(new ASN1OctetString(TYPE_ORDERING_RULE_ID, ruleID));
          keyList.add(new ASN1Sequence(elementList));
        }
      }
    }

    if (keyList.isEmpty())
    {
      int    msgID   = MSGID_SORTREQ_CONTROL_NO_SORT_KEYS;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }

    return new ASN1OctetString(new ASN1Sequence(keyList).encode());
  }



  /**
   * Creates a new server-side sort request control from the contents of the
   * provided control.
   *
   * @param  control  The generic control containing the information to use to
   *                  create this server-side sort request control.  It must not
   *                  be {@code null}.
   *
   * @return  The server-side sort request control decoded from the provided
   *          control.
   *
   * @throws  LDAPException  If this control cannot be decoded as a valid
   *                         server-side sort request control.
   */
  public static ServerSideSortRequestControl decodeControl(Control control)
         throws LDAPException
  {
    ASN1OctetString controlValue = control.getValue();
    if (controlValue == null)
    {
      int    msgID   = MSGID_SORTREQ_CONTROL_NO_VALUE;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }

    try
    {
      ASN1Sequence orderSequence =
           ASN1Sequence.decodeAsSequence(controlValue.value());
      ArrayList<ASN1Element> orderElements = orderSequence.elements();
      SortKey[] sortKeys = new SortKey[orderElements.size()];
      if (sortKeys.length == 0)
      {
        int    msgID   = MSGID_SORTREQ_CONTROL_NO_SORT_KEYS;
        String message = getMessage(msgID);
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
      }

      for (int i=0; i < sortKeys.length; i++)
      {
        ASN1Sequence keySequence = orderElements.get(i).decodeAsSequence();
        ArrayList<ASN1Element> keyElements = keySequence.elements();

        String attrName =
             keyElements.get(0).decodeAsOctetString().stringValue().
                  toLowerCase();
        AttributeType attrType = DirectoryServer.getAttributeType(attrName,
                                                                  false);
        if (attrType == null)
        {
          int    msgID   = MSGID_SORTREQ_CONTROL_UNDEFINED_ATTR;
          String message = getMessage(msgID, attrName);
          throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                  message);
        }

        OrderingMatchingRule orderingRule = null;
        boolean ascending = true;

        for (int j=1; j < keyElements.size(); j++)
        {
          ASN1Element e = keyElements.get(j);
          switch (e.getType())
          {
            case TYPE_ORDERING_RULE_ID:
              String orderingRuleID =
                   e.decodeAsOctetString().stringValue().toLowerCase();
              orderingRule =
                   DirectoryServer.getOrderingMatchingRule(orderingRuleID);
              if (orderingRule == null)
              {
                int    msgID   = MSGID_SORTREQ_CONTROL_UNDEFINED_ORDERING_RULE;
                String message = getMessage(msgID, orderingRuleID);
                throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                        message);
              }
              break;

            case TYPE_REVERSE_ORDER:
              ascending = ! e.decodeAsBoolean().booleanValue();
              break;

            default:
              int    msgID   = MSGID_SORTREQ_CONTROL_INVALID_SEQ_ELEMENT_TYPE;
              String message = getMessage(msgID, byteToHex(e.getType()));
              throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                      message);
          }
        }

        if ((orderingRule == null) &&
            (attrType.getOrderingMatchingRule() == null))
        {
          int    msgID   = MSGID_SORTREQ_CONTROL_NO_ORDERING_RULE_FOR_ATTR;
          String message = getMessage(msgID, attrName);
          throw new LDAPException(LDAPResultCode.CONSTRAINT_VIOLATION, msgID,
                                  message);
        }

        sortKeys[i] = new SortKey(attrType, ascending, orderingRule);
      }

      return new ServerSideSortRequestControl(control.getOID(),
                                              control.isCritical(),
                                              controlValue,
                                              new SortOrder(sortKeys));
    }
    catch (LDAPException le)
    {
      throw le;
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_SORTREQ_CONTROL_CANNOT_DECODE_VALUE;
      String message = getMessage(msgID, getExceptionMessage(e));
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message, e);
    }
  }



  /**
   * Retrieves a string representation of this server-side sort request control.
   *
   * @return  A string representation of this server-side sort request control.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this server-side sort request control
   * to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    buffer.append("ServerSideSortRequestControl(");

    if (sortOrder != null)
    {
      buffer.append(sortOrder);
    }

    buffer.append(")");
  }
}

