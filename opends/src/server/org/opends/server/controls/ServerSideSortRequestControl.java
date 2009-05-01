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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */
package org.opends.server.controls;
import org.opends.messages.Message;



import java.util.ArrayList;
import java.util.StringTokenizer;
import java.io.IOException;

import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.*;
import static org.opends.server.protocols.asn1.ASN1Constants.
    UNIVERSAL_OCTET_STRING_TYPE;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.*;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the server-side sort request control as defined in RFC
 * 2891 section 1.1. The subclass ServerSideSortRequestControl.ClientRequest
 * should be used when encoding this control from a sort order string. This is
 * suitable for client tools that want to encode this control without a
 * SortOrder object. The ASN.1 description for the control value is:
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


  /**
   * ControlDecoder implentation to decode this control from a ByteString.
   */
  private final static class Decoder
      implements ControlDecoder<ServerSideSortRequestControl>
  {
    /**
     * {@inheritDoc}
     */
    public ServerSideSortRequestControl decode(boolean isCritical,
                                               ByteString value)
        throws DirectoryException
    {
      if (value == null)
      {
        Message message = INFO_SORTREQ_CONTROL_NO_VALUE.get();
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
      }

      ASN1Reader reader = ASN1.getReader(value);
      try
      {
        reader.readStartSequence();
        if (!reader.hasNextElement())
        {
          Message message = INFO_SORTREQ_CONTROL_NO_SORT_KEYS.get();
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message);
        }

        ArrayList<SortKey> sortKeys = new ArrayList<SortKey>();
        while(reader.hasNextElement())
        {
          reader.readStartSequence();
          String attrName = toLowerCase(reader.readOctetStringAsString());
          AttributeType attrType =
              DirectoryServer.getAttributeType(attrName, false);
          if (attrType == null)
          {
            //This attribute is not defined in the schema. There is no point
            //iterating over the next attribute and return a partially sorted
            //result.
            return new ServerSideSortRequestControl(isCritical,
            new SortOrder(sortKeys.toArray(new SortKey[0])));
          }

          OrderingMatchingRule orderingRule = null;
          boolean ascending = true;
          if(reader.hasNextElement() &&
              reader.peekType() == TYPE_ORDERING_RULE_ID)
          {
            String orderingRuleID =
                toLowerCase(reader.readOctetStringAsString());
            orderingRule =
                DirectoryServer.getOrderingMatchingRule(orderingRuleID);
            if (orderingRule == null)
            {
              Message message =
                  INFO_SORTREQ_CONTROL_UNDEFINED_ORDERING_RULE.
                      get(orderingRuleID);
              throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                  message);
            }
          }
          if(reader.hasNextElement() &&
              reader.peekType() == TYPE_REVERSE_ORDER)
          {
            ascending = ! reader.readBoolean();
          }
          reader.readEndSequence();

          if ((orderingRule == null) &&
              (attrType.getOrderingMatchingRule() == null))
          {
            Message message =
                INFO_SORTREQ_CONTROL_NO_ORDERING_RULE_FOR_ATTR.get(attrName);
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                message);
          }

          sortKeys.add(new SortKey(attrType, ascending, orderingRule));
        }
        reader.readEndSequence();

        return new ServerSideSortRequestControl(isCritical,
            new SortOrder(sortKeys.toArray(new SortKey[0])));
      }
      catch (DirectoryException de)
      {
        throw de;
      }
      catch (Exception e)
      {
        Message message =
            INFO_SORTREQ_CONTROL_CANNOT_DECODE_VALUE.get(
                getExceptionMessage(e));
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, e);
      }
    }

    public String getOID()
    {
      return OID_SERVER_SIDE_SORT_REQUEST_CONTROL;
    }

  }

  /**
   * The Control Decoder that can be used to decode this control.
   */
  public static final ControlDecoder<ServerSideSortRequestControl> DECODER =
      new Decoder();

  // The sort order associated with this control represented by strings.
  private ArrayList<String[]> decodedKeyList;

  // The sort order associated with this control.
  private SortOrder sortOrder;

  /**
   * Creates a new server-side sort request control based on the definition in
   * the provided sort order string.
   *
   * @param  sortOrderString  The string representation of the sort order to
   *                          use for the control.
   * @throws LDAPException If the provided sort order string could not be
   *                       decoded.
   */
  public ServerSideSortRequestControl(String sortOrderString)
      throws LDAPException
  {
    this(false, sortOrderString);
  }

  /**
   * Creates a new server-side sort request control based on the definition in
   * the provided sort order string.
   *
   * @param  isCritical    Indicates whether support for this control
   *                       should be considered a critical part of the
   *                       server processing.
   * @param  sortOrderString  The string representation of the sort order to
   *                          use for the control.
   * @throws LDAPException If the provided sort order string could not be
   *                       decoded.
   */
  public ServerSideSortRequestControl(boolean isCritical,
                                      String sortOrderString)
      throws LDAPException
  {
    super(OID_SERVER_SIDE_SORT_REQUEST_CONTROL, isCritical);

    StringTokenizer tokenizer = new StringTokenizer(sortOrderString, ",");

    decodedKeyList = new ArrayList<String[]>();
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
          Message message =
              INFO_SORTREQ_CONTROL_NO_ATTR_NAME.get(sortOrderString);
          throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message);
        }

        if (reverseOrder)
        {
          decodedKeyList.add(new String[]{token, null, "r"});
        }
        else
        {
          decodedKeyList.add(new String[]{token, null, null});
        }
      }
      else if (colonPos == 0)
      {
        Message message =
            INFO_SORTREQ_CONTROL_NO_ATTR_NAME.get(sortOrderString);
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message);
      }
      else if (colonPos == (token.length() - 1))
      {
        Message message =
            INFO_SORTREQ_CONTROL_NO_MATCHING_RULE.get(sortOrderString);
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message);
      }
      else
      {
        String attrName = token.substring(0, colonPos);
        String ruleID   = token.substring(colonPos+1);

        if (reverseOrder)
        {
          decodedKeyList.add(new String[]{attrName, ruleID, "r"});
        }
        else
        {
          decodedKeyList.add(new String[]{attrName, ruleID, null});
        }
      }
    }

    if (decodedKeyList.isEmpty())
    {
      Message message = INFO_SORTREQ_CONTROL_NO_SORT_KEYS.get();
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message);
    }
  }


  /**
   * Creates a new server-side sort request control based on the provided sort
   * order.
   *
   * @param  sortOrder  The sort order to use for this control.
   */
  public ServerSideSortRequestControl(SortOrder sortOrder)
  {
    this(false, sortOrder);
  }

  /**
   * Creates a new server-side sort request control with the provided
   * information.
   *
   * @param  isCritical    Indicates whether support for this control should be
   *                       considered a critical part of the server processing.
   * @param  sortOrder     sort order associated with this server-side sort
   *                       control.
   */
  public ServerSideSortRequestControl(boolean isCritical, SortOrder sortOrder)
  {
    super(OID_SERVER_SIDE_SORT_REQUEST_CONTROL, isCritical);

    this.sortOrder = sortOrder;
  }


  /**
   * Retrieves the sort order for this server-side sort request control.
   *
   * @return  The sort order for this server-side sort request control.
   * @throws  DirectoryException if an error occurs while retriving the
   *          sort order.
   */
  public SortOrder getSortOrder() throws DirectoryException
  {
    if(sortOrder == null)
    {
      sortOrder = decodeSortOrderFromString();
    }

    return sortOrder;
  }

  /**
   * Indicates whether the sort control contains Sort keys.
   *
   * <P> A Sort control may not contain sort keys if the attribute type
   * is not recognized by the server </P>
   *
   * @return  <CODE>true</CODE> if the control contains sort keys
   *          or <CODE>false</CODE> if it does not.
   *
   * @throws  DirectoryException  If a problem occurs while trying to make the
   *                              determination.
   */
  public boolean  containsSortKeys() throws DirectoryException
  {
    return getSortOrder().getSortKeys().length!=0;
  }

  /**
   * Writes this control's value to an ASN.1 writer. The value (if any) must
   * be written as an ASN1OctetString.
   *
   * @param writer The ASN.1 writer to use.
   * @throws IOException If a problem occurs while writing to the stream.

   */
  @Override
  protected void writeValue(ASN1Writer writer) throws IOException {
    if(decodedKeyList != null)
    {
      // This control was created with a sort order string so encode using
      // that.
      writeValueFromString(writer);
    }
    else
    {
      // This control must have been created with a typed sort order object
      // so encode using that.
      writeValueFromSortOrder(writer);
    }
  }

  /**
   * Appends a string representation of this server-side sort request control
   * to the provided buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  @Override
  public void toString(StringBuilder buffer)
  {
    buffer.append("ServerSideSortRequestControl(");
    if(sortOrder == null)
    {
      buffer.append("SortOrder(");

      if (decodedKeyList.size() > 0)
      {
        decodedKeyToString(decodedKeyList.get(0), buffer);

        for (int i=1; i < decodedKeyList.size(); i++)
        {
          buffer.append(",");
          decodedKeyToString(decodedKeyList.get(i), buffer);
        }
      }
      buffer.append(")");
    }
    else
    {
      buffer.append(sortOrder);
    }
    buffer.append(")");
  }

  private void decodedKeyToString(String[] decodedKey, StringBuilder buffer)
  {
    buffer.append("SortKey(");
    if (decodedKey[2] == null)
    {
      buffer.append("+");
    }
    else
    {
      buffer.append("-");
    }
    buffer.append(decodedKey[0]);

    if (decodedKey[1] != null)
    {
      buffer.append(":");
      buffer.append(decodedKey[1]);
    }

    buffer.append(")");
  }

  private SortOrder decodeSortOrderFromString() throws DirectoryException
  {
    ArrayList<SortKey> sortKeys = new ArrayList<SortKey>();
    for(String[] decodedKey : decodedKeyList)
    {
      AttributeType attrType =
          DirectoryServer.getAttributeType(decodedKey[0].toLowerCase(), false);
      if (attrType == null)
      {
        //This attribute is not defined in the schema. There is no point
        //iterating over the next attribute and return a partially sorted
        //result.
        return new SortOrder(sortKeys.toArray(new SortKey[0]));
      }

      OrderingMatchingRule orderingRule = null;
      if(decodedKey[1] != null)
      {
        orderingRule =
            DirectoryServer.getOrderingMatchingRule(
                decodedKey[1].toLowerCase());
        if (orderingRule == null)
        {
          Message message =
              INFO_SORTREQ_CONTROL_UNDEFINED_ORDERING_RULE.
                  get(decodedKey[1]);
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
              message);
        }
      }

      boolean ascending = true;
      if(decodedKey[2] != null && decodedKey[2].equals("r"))
      {
        ascending = false;
      }

      if ((orderingRule == null) &&
          (attrType.getOrderingMatchingRule() == null))
      {
        Message message =
            INFO_SORTREQ_CONTROL_NO_ORDERING_RULE_FOR_ATTR.get(
                decodedKey[0]);
        throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
            message);
      }

      sortKeys.add(new SortKey(attrType, ascending, orderingRule));
    }

    return new SortOrder(sortKeys.toArray(new SortKey[0]));
  }

  private void writeValueFromString(ASN1Writer writer) throws IOException
  {
    writer.writeStartSequence(UNIVERSAL_OCTET_STRING_TYPE);

    writer.writeStartSequence();
    for(String[] strs : decodedKeyList)
    {
      writer.writeStartSequence();
      // Attr name will always be present
      writer.writeOctetString(strs[0]);
      // Rule ID might not be present
      if(strs[1] != null)
      {
        writer.writeOctetString(TYPE_ORDERING_RULE_ID, strs[1]);
      }
      // Reverse if present
      if(strs[2] != null)
      {
        writer.writeBoolean(TYPE_REVERSE_ORDER, true);
      }
      writer.writeEndSequence();
    }
    writer.writeEndSequence();

    writer.writeEndSequence();
  }

  private void writeValueFromSortOrder(ASN1Writer writer) throws IOException
  {
    writer.writeStartSequence(UNIVERSAL_OCTET_STRING_TYPE);

    writer.writeStartSequence();
    for (SortKey sortKey : sortOrder.getSortKeys())
    {
      writer.writeStartSequence();
      writer.writeOctetString(sortKey.getAttributeType().getNameOrOID());

      if (sortKey.getOrderingRule() != null)
      {
        writer.writeOctetString(TYPE_ORDERING_RULE_ID,
            sortKey.getOrderingRule().getNameOrOID());
      }

      if (! sortKey.ascending())
      {
        writer.writeBoolean(TYPE_REVERSE_ORDER, true);
      }

      writer.writeEndSequence();
    }
    writer.writeEndSequence();

    writer.writeEndSequence();
  }
}

