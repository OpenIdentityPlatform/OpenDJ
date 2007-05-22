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
package org.opends.server.protocols.ldap;



import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Collection;

import org.opends.server.api.MatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.FilterType;
import org.opends.server.types.LDAPException;
import org.opends.server.types.RawFilter;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the data structures and methods to use when interacting
 * with an LDAP search filter, which defines a set of criteria for locating
 * entries in a search request.
 */
public class LDAPFilter
       extends RawFilter
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The set of subAny elements for substring filters.
  private ArrayList<ByteString> subAnyElements;

  // The set of filter components for AND and OR filters.
  private ArrayList<RawFilter> filterComponents;

  // Indicates whether to match on DN attributes for extensible match filters.
  private boolean dnAttributes;

  // The assertion value for several filter types.
  private ByteString assertionValue;

  // The subFinal element for substring filters.
  private ByteString subFinalElement;

  // The subInitial element for substring filters.
  private ByteString subInitialElement;

  // The filter type for this filter.
  private FilterType filterType;

  // The filter component for NOT filters.
  private RawFilter notComponent;

  // The attribute type for several filter types.
  private String attributeType;

  // The matching rule ID for extensible matching filters.
  private String matchingRuleID;



  /**
   * Creates a new LDAP filter with the provided information.  Note that this
   * constructor is only intended for use by the {@code RawFilter} class and any
   * use of this constructor outside of that class must be very careful to
   * ensure that all of the appropriate element types have been provided for the
   * associated filter type.
   *
   * @param  filterType         The filter type for this filter.
   * @param  filterComponents   The filter components for AND and OR filters.
   * @param  notComponent       The filter component for NOT filters.
   * @param  attributeType      The attribute type for this filter.
   * @param  assertionValue     The assertion value for this filter.
   * @param  subInitialElement  The subInitial element for substring filters.
   * @param  subAnyElements     The subAny elements for substring filters.
   * @param  subFinalElement    The subFinal element for substring filters.
   * @param  matchingRuleID     The matching rule ID for extensible filters.
   * @param  dnAttributes       The dnAttributes flag for extensible filters.
   */
  public LDAPFilter(FilterType filterType,
                    ArrayList<RawFilter> filterComponents,
                    RawFilter notComponent, String attributeType,
                    ByteString assertionValue, ByteString subInitialElement,
                    ArrayList<ByteString> subAnyElements,
                    ByteString subFinalElement, String matchingRuleID,
                    boolean dnAttributes)
  {
    this.filterType        = filterType;
    this.filterComponents  = filterComponents;
    this.notComponent      = notComponent;
    this.attributeType     = attributeType;
    this.assertionValue    = assertionValue;
    this.subInitialElement = subInitialElement;
    this.subAnyElements    = subAnyElements;
    this.subFinalElement   = subFinalElement;
    this.matchingRuleID    = matchingRuleID;
    this.dnAttributes      = dnAttributes;
  }



  /**
   * Creates a new LDAP filter from the provided search filter.
   *
   * @param  filter  The search filter to use to create this LDAP filter.
   */
  public LDAPFilter(SearchFilter filter)
  {
    this.filterType = filter.getFilterType();

    switch (filterType)
    {
      case AND:
      case OR:
        Collection<SearchFilter> comps = filter.getFilterComponents();
        filterComponents = new ArrayList<RawFilter>(comps.size());
        for (SearchFilter f : comps)
        {
          filterComponents.add(new LDAPFilter(f));
        }

        notComponent      = null;
        attributeType     = null;
        assertionValue    = null;
        subInitialElement = null;
        subAnyElements    = null;
        subFinalElement   = null;
        matchingRuleID    = null;
        dnAttributes      = false;
        break;
      case NOT:
        notComponent = new LDAPFilter(filter.getNotComponent());

        filterComponents  = null;
        attributeType     = null;
        assertionValue    = null;
        subInitialElement = null;
        subAnyElements    = null;
        subFinalElement   = null;
        matchingRuleID    = null;
        dnAttributes      = false;
        break;
      case EQUALITY:
      case GREATER_OR_EQUAL:
      case LESS_OR_EQUAL:
      case APPROXIMATE_MATCH:
        attributeType  = filter.getAttributeType().getNameOrOID();
        assertionValue =
             filter.getAssertionValue().getValue().toASN1OctetString();

        filterComponents  = null;
        notComponent      = null;
        subInitialElement = null;
        subAnyElements    = null;
        subFinalElement   = null;
        matchingRuleID    = null;
        dnAttributes      = false;
        break;
      case SUBSTRING:
        attributeType  = filter.getAttributeType().getNameOrOID();

        ByteString bs = filter.getSubInitialElement();
        if (bs == null)
        {
          subInitialElement = null;
        }
        else
        {
          subInitialElement = bs.toASN1OctetString();
        }

        bs = filter.getSubFinalElement();
        if (bs == null)
        {
          subFinalElement = null;
        }
        else
        {
          subFinalElement = bs.toASN1OctetString();
        }

        List<ByteString> subAnyStrings = filter.getSubAnyElements();
        if (subAnyStrings == null)
        {
          subAnyElements = null;
        }
        else
        {
          subAnyElements = new ArrayList<ByteString>(subAnyStrings);
        }

        filterComponents  = null;
        notComponent      = null;
        assertionValue    = null;
        matchingRuleID    = null;
        dnAttributes      = false;
        break;
      case PRESENT:
        attributeType  = filter.getAttributeType().getNameOrOID();

        filterComponents  = null;
        notComponent      = null;
        assertionValue    = null;
        subInitialElement = null;
        subAnyElements    = null;
        subFinalElement   = null;
        matchingRuleID    = null;
        dnAttributes      = false;
        break;
      case EXTENSIBLE_MATCH:
        dnAttributes   = filter.getDNAttributes();
        matchingRuleID = filter.getMatchingRuleID();

        AttributeType attrType = filter.getAttributeType();
        if (attrType == null)
        {
          attributeType = null;
        }
        else
        {
          attributeType = attrType.getNameOrOID();
        }

        AttributeValue av = filter.getAssertionValue();
        if (av == null)
        {
          assertionValue = null;
        }
        else
        {
          assertionValue = av.getValue().toASN1OctetString();
        }

        filterComponents  = null;
        notComponent      = null;
        subInitialElement = null;
        subAnyElements    = null;
        subFinalElement   = null;
        break;
    }
  }



  /**
   * Decodes the provided string into an LDAP search filter.
   *
   * @param  filterString  The string representation of the search filter to
   *                       decode.
   *
   * @return  The decoded LDAP search filter.
   *
   * @throws  LDAPException  If the provided string does not represent a valid
   *                         LDAP search filter.
   */
  public static LDAPFilter decode(String filterString)
         throws LDAPException
  {
    if (filterString == null)
    {
      int msgID = MSGID_LDAP_FILTER_STRING_NULL;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }


    try
    {
      return decode(filterString, 0, filterString.length());
    }
    catch (LDAPException le)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, le);
      }

      throw le;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_LDAP_FILTER_UNCAUGHT_EXCEPTION;
      String message = getMessage(msgID, filterString, String.valueOf(e));
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message, e);
    }
  }



  /**
   * Decodes the provided string into an LDAP search filter.
   *
   * @param  filterString  The string representation of the search filter to
   *                       decode.
   * @param  startPos      The position of the first character in the filter
   *                       to parse.
   * @param  endPos        The position of the first character after the end of
   *                       the filter to parse.
   *
   * @return  The decoded LDAP search filter.
   *
   * @throws  LDAPException  If the provided string does not represent a valid
   *                         LDAP search filter.
   */
  private static LDAPFilter decode(String filterString, int startPos,
                                   int endPos)
          throws LDAPException
  {
    // Make sure that the length is sufficient for a valid search filter.
    int length = endPos - startPos;
    if (length <= 0)
    {
      int msgID = MSGID_LDAP_FILTER_STRING_NULL;
      String message = getMessage(msgID);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }

    // If the filter is enclosed in a pair of apostrophes ("single-quotes") it
    // is invalid.
    // (FIXME -- This error is a workaround for
    //  https://opends.dev.java.net/issues/show_bug.cgi?id=1024. A correct fix
    // is to validate the characters used in the attribute type.)
    if (1 < filterString.length()
         && filterString.startsWith("'") && filterString.endsWith("'"))
    {
      int msgID = MSGID_LDAP_FILTER_ENCLOSED_IN_APOSTROPHES;
      String message = getMessage(msgID, filterString);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }

    // If the filter is surrounded by parentheses (which it should be), then
    // strip them off.
    if (filterString.charAt(startPos) == '(')
    {
      if (filterString.charAt(endPos-1) == ')')
      {
        startPos++;
        endPos--;
      }
      else
      {
        int    msgID   = MSGID_LDAP_FILTER_MISMATCHED_PARENTHESES;
        String message = getMessage(msgID, filterString, startPos, endPos);
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
      }
    }


    // Look at the first character.  If it is a '&' then it is an AND search.
    // If it is a '|' then it is an OR search.  If it is a '!' then it is a NOT
    // search.
    char c = filterString.charAt(startPos);
    if (c == '&')
    {
      return decodeCompoundFilter(FilterType.AND, filterString, startPos+1,
                                  endPos);
    }
    else if (c == '|')
    {
      return decodeCompoundFilter(FilterType.OR, filterString, startPos+1,
                                  endPos);
    }
    else if (c == '!')
    {
      return decodeCompoundFilter(FilterType.NOT, filterString, startPos+1,
                                  endPos);
    }


    // If we've gotten here, then it must be a simple filter.  It must have an
    // equal sign at some point, so find it.
    int equalPos = -1;
    for (int i=startPos; i < endPos; i++)
    {
      if (filterString.charAt(i) == '=')
      {
        equalPos = i;
        break;
      }
    }

    if (equalPos <= startPos)
    {
        int    msgID   = MSGID_LDAP_FILTER_NO_EQUAL_SIGN;
        String message = getMessage(msgID, filterString, startPos, endPos);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }


    // Look at the character immediately before the equal sign, because it may
    // help determine the filter type.
    int attrEndPos;
    FilterType filterType;
    switch (filterString.charAt(equalPos-1))
    {
      case '~':
        filterType = FilterType.APPROXIMATE_MATCH;
        attrEndPos = equalPos-1;
        break;
      case '>':
        filterType = FilterType.GREATER_OR_EQUAL;
        attrEndPos = equalPos-1;
        break;
      case '<':
        filterType = FilterType.LESS_OR_EQUAL;
        attrEndPos = equalPos-1;
        break;
      case ':':
        return decodeExtensibleMatchFilter(filterString, startPos, equalPos,
                                           endPos);
      default:
        filterType = FilterType.EQUALITY;
        attrEndPos = equalPos;
        break;
    }


    // The part of the filter string before the equal sign should be the
    // attribute type.
    String attrType = filterString.substring(startPos, attrEndPos);


    // Get the attribute value.
    String valueStr = filterString.substring(equalPos+1, endPos);
    if (valueStr.length() == 0)
    {
      return new LDAPFilter(filterType, null, null, attrType,
                            new ASN1OctetString(), null, null, null, null,
                            false);
    }
    else if (valueStr.equals("*"))
    {
      return new LDAPFilter(FilterType.PRESENT, null, null, attrType, null,
                            null, null, null, null, false);
    }
    else if (valueStr.indexOf('*') >= 0)
    {
      return decodeSubstringFilter(filterString, attrType, equalPos, endPos);
    }
    else
    {
      boolean hasEscape = false;
      byte[] valueBytes = getBytes(valueStr);
      for (int i=0; i < valueBytes.length; i++)
      {
        if (valueBytes[i] == 0x5C) // The backslash character
        {
          hasEscape = true;
          break;
        }
      }

      ASN1OctetString  value;
      if (hasEscape)
      {
        ByteBuffer valueBuffer = ByteBuffer.allocate(valueStr.length());
        for (int i=0; i < valueBytes.length; i++)
        {
          if (valueBytes[i] == 0x5C) // The backslash character
          {
            // The next two bytes must be the hex characters that comprise the
            // binary value.
            if ((i + 2) >= valueBytes.length)
            {
              int    msgID   = MSGID_LDAP_FILTER_INVALID_ESCAPED_BYTE;
              String message = getMessage(msgID, filterString, equalPos+i+1);
              throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                      message);
            }

            byte byteValue = 0;
            switch (valueBytes[++i])
            {
              case 0x30: // '0'
                break;
              case 0x31: // '1'
                byteValue = (byte) 0x10;
                break;
              case 0x32: // '2'
                byteValue = (byte) 0x20;
                break;
              case 0x33: // '3'
                byteValue = (byte) 0x30;
                break;
              case 0x34: // '4'
                byteValue = (byte) 0x40;
                break;
              case 0x35: // '5'
                byteValue = (byte) 0x50;
                break;
              case 0x36: // '6'
                byteValue = (byte) 0x60;
                break;
              case 0x37: // '7'
                byteValue = (byte) 0x70;
                break;
              case 0x38: // '8'
                byteValue = (byte) 0x80;
                break;
              case 0x39: // '9'
                byteValue = (byte) 0x90;
                break;
              case 0x41: // 'A'
              case 0x61: // 'a'
                byteValue = (byte) 0xA0;
                break;
              case 0x42: // 'B'
              case 0x62: // 'b'
                byteValue = (byte) 0xB0;
                break;
              case 0x43: // 'C'
              case 0x63: // 'c'
                byteValue = (byte) 0xC0;
                break;
              case 0x44: // 'D'
              case 0x64: // 'd'
                byteValue = (byte) 0xD0;
                break;
              case 0x45: // 'E'
              case 0x65: // 'e'
                byteValue = (byte) 0xE0;
                break;
              case 0x46: // 'F'
              case 0x66: // 'f'
                byteValue = (byte) 0xF0;
                break;
              default:
                int    msgID   = MSGID_LDAP_FILTER_INVALID_ESCAPED_BYTE;
                String message = getMessage(msgID, filterString, equalPos+i+1);
                throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                        message);
            }

            switch (valueBytes[++i])
            {
              case 0x30: // '0'
                break;
              case 0x31: // '1'
                byteValue |= (byte) 0x01;
                break;
              case 0x32: // '2'
                byteValue |= (byte) 0x02;
                break;
              case 0x33: // '3'
                byteValue |= (byte) 0x03;
                break;
              case 0x34: // '4'
                byteValue |= (byte) 0x04;
                break;
              case 0x35: // '5'
                byteValue |= (byte) 0x05;
                break;
              case 0x36: // '6'
                byteValue |= (byte) 0x06;
                break;
              case 0x37: // '7'
                byteValue |= (byte) 0x07;
                break;
              case 0x38: // '8'
                byteValue |= (byte) 0x08;
                break;
              case 0x39: // '9'
                byteValue |= (byte) 0x09;
                break;
              case 0x41: // 'A'
              case 0x61: // 'a'
                byteValue |= (byte) 0x0A;
                break;
              case 0x42: // 'B'
              case 0x62: // 'b'
                byteValue |= (byte) 0x0B;
                break;
              case 0x43: // 'C'
              case 0x63: // 'c'
                byteValue |= (byte) 0x0C;
                break;
              case 0x44: // 'D'
              case 0x64: // 'd'
                byteValue |= (byte) 0x0D;
                break;
              case 0x45: // 'E'
              case 0x65: // 'e'
                byteValue |= (byte) 0x0E;
                break;
              case 0x46: // 'F'
              case 0x66: // 'f'
                byteValue |= (byte) 0x0F;
                break;
              default:
                int    msgID   = MSGID_LDAP_FILTER_INVALID_ESCAPED_BYTE;
                String message = getMessage(msgID, filterString, equalPos+i+1);
                throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                        message);
            }

            valueBuffer.put(byteValue);
          }
          else
          {
            valueBuffer.put(valueBytes[i]);
          }
        }

        valueBytes = new byte[valueBuffer.position()];
        valueBuffer.flip();
        valueBuffer.get(valueBytes);
        value = new ASN1OctetString(valueBytes);
      }
      else
      {
        value = new ASN1OctetString(valueBytes);
      }

      return new LDAPFilter(filterType, null, null, attrType, value, null, null,
                            null, null, false);
    }
  }



  /**
   * Decodes a set of filters from the provided filter string within the
   * indicated range.
   *
   * @param  filterType    The filter type for this compound filter.  It must be
   *                       an AND, OR or NOT filter.
   * @param  filterString  The string containing the filter information to
   *                       decode.
   * @param  startPos      The position of the first character in the set of
   *                       filters to decode.
   * @param  endPos        The position of the first character after the end of
   *                       the set of filters to decode.
   *
   * @return  The decoded LDAP filter.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         compound filter.
   */
  private static LDAPFilter decodeCompoundFilter(FilterType filterType,
                                                 String filterString,
                                                 int startPos, int endPos)
          throws LDAPException
  {
    // Create a list to hold the returned components.
    ArrayList<RawFilter> filterComponents = new ArrayList<RawFilter>();


    // If the end pos is equal to the start pos, then there are no components.
    if (startPos == endPos)
    {
      if (filterType == FilterType.NOT)
      {
        int    msgID   = MSGID_LDAP_FILTER_NOT_EXACTLY_ONE;
        String message = getMessage(msgID, filterString, startPos, endPos);
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
      }
      else
      {
        // This is valid and will be treated as a TRUE/FALSE filter.
        return new LDAPFilter(filterType, filterComponents, null, null, null,
                              null, null, null, null, false);
      }
    }


    // The first and last characters must be parentheses.  If not, then that's
    // an error.
    if ((filterString.charAt(startPos) != '(') ||
        (filterString.charAt(endPos-1) != ')'))
    {
      int msgID = MSGID_LDAP_FILTER_COMPOUND_MISSING_PARENTHESES;
      String message = getMessage(msgID, filterString, startPos, endPos);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }


    // Iterate through the characters in the value.  Whenever an open
    // parenthesis is found, locate the corresponding close parenthesis by
    // counting the number of intermediate open/close parentheses.
    int pendingOpens = 0;
    int openPos = -1;
    for (int i=startPos; i < endPos; i++)
    {
      char c = filterString.charAt(i);
      if (c == '(')
      {
        if (openPos < 0)
        {
          openPos = i;
        }

        pendingOpens++;
      }
      else if (c == ')')
      {
        pendingOpens--;
        if (pendingOpens == 0)
        {
          filterComponents.add(decode(filterString, openPos, i+1));
          openPos = -1;
        }
        else if (pendingOpens < 0)
        {
          int    msgID   = MSGID_LDAP_FILTER_NO_CORRESPONDING_OPEN_PARENTHESIS;
          String message = getMessage(msgID, filterString, i);
          throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                  message);
        }
      }
      else if (pendingOpens <= 0)
      {
        int    msgID   = MSGID_LDAP_FILTER_COMPOUND_MISSING_PARENTHESES;
        String message = getMessage(msgID, filterString, startPos, endPos);
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
      }
    }


    // At this point, we have parsed the entire set of filter components.  The
    // list of open parenthesis positions must be empty.
    if (pendingOpens != 0)
    {
      int    msgID   = MSGID_LDAP_FILTER_NO_CORRESPONDING_CLOSE_PARENTHESIS;
      String message = getMessage(msgID, filterString, openPos);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }


    // We should have everything we need, so return the list.
    if (filterType == FilterType.NOT)
    {
      if (filterComponents.size() != 1)
      {
        int    msgID   = MSGID_LDAP_FILTER_NOT_EXACTLY_ONE;
        String message = getMessage(msgID, filterString, startPos, endPos);
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
      }
      RawFilter notComponent = filterComponents.get(0);
      return new LDAPFilter(filterType, null, notComponent, null, null,
                            null, null, null, null, false);
    }
    else
    {
      return new LDAPFilter(filterType, filterComponents, null, null, null,
                            null, null, null, null, false);
    }
  }



  /**
   * Decodes a substring search filter component based on the provided
   * information.
   *
   * @param  filterString  The filter string containing the information to
   *                       decode.
   * @param  attrType      The attribute type for this substring filter
   *                       component.
   * @param  equalPos      The location of the equal sign separating the
   *                       attribute type from the value.
   * @param  endPos        The position of the first character after the end of
   *                       the substring value.
   *
   * @return  The decoded LDAP filter.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         substring filter.
   */
  private static LDAPFilter decodeSubstringFilter(String filterString,
                                                  String attrType, int equalPos,
                                                  int endPos)
          throws LDAPException
  {
    // Get a binary representation of the value.
    byte[] valueBytes = getBytes(filterString.substring(equalPos+1, endPos));


    // Find the locations of all the asterisks in the value.  Also, check to
    // see if there are any escaped values, since they will need special
    // treatment.
    boolean hasEscape = false;
    LinkedList<Integer> asteriskPositions = new LinkedList<Integer>();
    for (int i=0; i < valueBytes.length; i++)
    {
      if (valueBytes[i] == 0x2A) // The asterisk.
      {
        asteriskPositions.add(i);
      }
      else if (valueBytes[i] == 0x5C) // The backslash.
      {
        hasEscape = true;
      }
    }


    // If there were no asterisks, then this isn't a substring filter.
    if (asteriskPositions.isEmpty())
    {
      int msgID = MSGID_LDAP_FILTER_SUBSTRING_NO_ASTERISKS;
      String message = getMessage(msgID, filterString, equalPos+1, endPos);
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }


    // If the value starts with an asterisk, then there is no subInitial
    // component.  Otherwise, parse out the subInitial.
    ByteString subInitial;
    int firstPos = asteriskPositions.removeFirst();
    if (firstPos == 0)
    {
      subInitial = null;
    }
    else
    {
      if (hasEscape)
      {
        ByteBuffer buffer = ByteBuffer.allocate(firstPos);
        for (int i=0; i < firstPos; i++)
        {
          if (valueBytes[i] == 0x5C)
          {
            // The next two bytes must be the hex characters that comprise the
            // binary value.
            if ((i + 2) >= valueBytes.length)
            {
              int    msgID   = MSGID_LDAP_FILTER_INVALID_ESCAPED_BYTE;
              String message = getMessage(msgID, filterString, equalPos+i+1);
              throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                      message);
            }

            byte byteValue = 0;
            switch (valueBytes[++i])
            {
              case 0x30: // '0'
                break;
              case 0x31: // '1'
                byteValue = (byte) 0x10;
                break;
              case 0x32: // '2'
                byteValue = (byte) 0x20;
                break;
              case 0x33: // '3'
                byteValue = (byte) 0x30;
                break;
              case 0x34: // '4'
                byteValue = (byte) 0x40;
                break;
              case 0x35: // '5'
                byteValue = (byte) 0x50;
                break;
              case 0x36: // '6'
                byteValue = (byte) 0x60;
                break;
              case 0x37: // '7'
                byteValue = (byte) 0x70;
                break;
              case 0x38: // '8'
                byteValue = (byte) 0x80;
                break;
              case 0x39: // '9'
                byteValue = (byte) 0x90;
                break;
              case 0x41: // 'A'
              case 0x61: // 'a'
                byteValue = (byte) 0xA0;
                break;
              case 0x42: // 'B'
              case 0x62: // 'b'
                byteValue = (byte) 0xB0;
                break;
              case 0x43: // 'C'
              case 0x63: // 'c'
                byteValue = (byte) 0xC0;
                break;
              case 0x44: // 'D'
              case 0x64: // 'd'
                byteValue = (byte) 0xD0;
                break;
              case 0x45: // 'E'
              case 0x65: // 'e'
                byteValue = (byte) 0xE0;
                break;
              case 0x46: // 'F'
              case 0x66: // 'f'
                byteValue = (byte) 0xF0;
                break;
              default:
                int    msgID   = MSGID_LDAP_FILTER_INVALID_ESCAPED_BYTE;
                String message = getMessage(msgID, filterString, equalPos+i+1);
                throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                        message);
            }

            switch (valueBytes[++i])
            {
              case 0x30: // '0'
                break;
              case 0x31: // '1'
                byteValue |= (byte) 0x01;
                break;
              case 0x32: // '2'
                byteValue |= (byte) 0x02;
                break;
              case 0x33: // '3'
                byteValue |= (byte) 0x03;
                break;
              case 0x34: // '4'
                byteValue |= (byte) 0x04;
                break;
              case 0x35: // '5'
                byteValue |= (byte) 0x05;
                break;
              case 0x36: // '6'
                byteValue |= (byte) 0x06;
                break;
              case 0x37: // '7'
                byteValue |= (byte) 0x07;
                break;
              case 0x38: // '8'
                byteValue |= (byte) 0x08;
                break;
              case 0x39: // '9'
                byteValue |= (byte) 0x09;
                break;
              case 0x41: // 'A'
              case 0x61: // 'a'
                byteValue |= (byte) 0x0A;
                break;
              case 0x42: // 'B'
              case 0x62: // 'b'
                byteValue |= (byte) 0x0B;
                break;
              case 0x43: // 'C'
              case 0x63: // 'c'
                byteValue |= (byte) 0x0C;
                break;
              case 0x44: // 'D'
              case 0x64: // 'd'
                byteValue |= (byte) 0x0D;
                break;
              case 0x45: // 'E'
              case 0x65: // 'e'
                byteValue |= (byte) 0x0E;
                break;
              case 0x46: // 'F'
              case 0x66: // 'f'
                byteValue |= (byte) 0x0F;
                break;
              default:
                int    msgID   = MSGID_LDAP_FILTER_INVALID_ESCAPED_BYTE;
                String message = getMessage(msgID, filterString, equalPos+i+1);
                throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                        message);
            }

            buffer.put(byteValue);
          }
          else
          {
            buffer.put(valueBytes[i]);
          }
        }

        byte[] subInitialBytes = new byte[buffer.position()];
        buffer.flip();
        buffer.get(subInitialBytes);
        subInitial = new ASN1OctetString(subInitialBytes);
      }
      else
      {
        byte[] subInitialBytes = new byte[firstPos];
        System.arraycopy(valueBytes, 0, subInitialBytes, 0, firstPos);
        subInitial = new ASN1OctetString(subInitialBytes);
      }
    }


    // Next, process through the rest of the asterisks to get the subAny values.
    ArrayList<ByteString> subAny = new ArrayList<ByteString>();
    for (int asteriskPos : asteriskPositions)
    {
      int length = asteriskPos - firstPos - 1;

      if (hasEscape)
      {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        for (int i=firstPos+1; i < asteriskPos; i++)
        {
          if (valueBytes[i] == 0x5C)
          {
            // The next two bytes must be the hex characters that comprise the
            // binary value.
            if ((i + 2) >= valueBytes.length)
            {
              int    msgID   = MSGID_LDAP_FILTER_INVALID_ESCAPED_BYTE;
              String message = getMessage(msgID, filterString, equalPos+i+1);
              throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                      message);
            }

            byte byteValue = 0;
            switch (valueBytes[++i])
            {
              case 0x30: // '0'
                break;
              case 0x31: // '1'
                byteValue = (byte) 0x10;
                break;
              case 0x32: // '2'
                byteValue = (byte) 0x20;
                break;
              case 0x33: // '3'
                byteValue = (byte) 0x30;
                break;
              case 0x34: // '4'
                byteValue = (byte) 0x40;
                break;
              case 0x35: // '5'
                byteValue = (byte) 0x50;
                break;
              case 0x36: // '6'
                byteValue = (byte) 0x60;
                break;
              case 0x37: // '7'
                byteValue = (byte) 0x70;
                break;
              case 0x38: // '8'
                byteValue = (byte) 0x80;
                break;
              case 0x39: // '9'
                byteValue = (byte) 0x90;
                break;
              case 0x41: // 'A'
              case 0x61: // 'a'
                byteValue = (byte) 0xA0;
                break;
              case 0x42: // 'B'
              case 0x62: // 'b'
                byteValue = (byte) 0xB0;
                break;
              case 0x43: // 'C'
              case 0x63: // 'c'
                byteValue = (byte) 0xC0;
                break;
              case 0x44: // 'D'
              case 0x64: // 'd'
                byteValue = (byte) 0xD0;
                break;
              case 0x45: // 'E'
              case 0x65: // 'e'
                byteValue = (byte) 0xE0;
                break;
              case 0x46: // 'F'
              case 0x66: // 'f'
                byteValue = (byte) 0xF0;
                break;
              default:
                int    msgID   = MSGID_LDAP_FILTER_INVALID_ESCAPED_BYTE;
                String message = getMessage(msgID, filterString, equalPos+i+1);
                throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                        message);
            }

            switch (valueBytes[++i])
            {
              case 0x30: // '0'
                break;
              case 0x31: // '1'
                byteValue |= (byte) 0x01;
                break;
              case 0x32: // '2'
                byteValue |= (byte) 0x02;
                break;
              case 0x33: // '3'
                byteValue |= (byte) 0x03;
                break;
              case 0x34: // '4'
                byteValue |= (byte) 0x04;
                break;
              case 0x35: // '5'
                byteValue |= (byte) 0x05;
                break;
              case 0x36: // '6'
                byteValue |= (byte) 0x06;
                break;
              case 0x37: // '7'
                byteValue |= (byte) 0x07;
                break;
              case 0x38: // '8'
                byteValue |= (byte) 0x08;
                break;
              case 0x39: // '9'
                byteValue |= (byte) 0x09;
                break;
              case 0x41: // 'A'
              case 0x61: // 'a'
                byteValue |= (byte) 0x0A;
                break;
              case 0x42: // 'B'
              case 0x62: // 'b'
                byteValue |= (byte) 0x0B;
                break;
              case 0x43: // 'C'
              case 0x63: // 'c'
                byteValue |= (byte) 0x0C;
                break;
              case 0x44: // 'D'
              case 0x64: // 'd'
                byteValue |= (byte) 0x0D;
                break;
              case 0x45: // 'E'
              case 0x65: // 'e'
                byteValue |= (byte) 0x0E;
                break;
              case 0x46: // 'F'
              case 0x66: // 'f'
                byteValue |= (byte) 0x0F;
                break;
              default:
                int    msgID   = MSGID_LDAP_FILTER_INVALID_ESCAPED_BYTE;
                String message = getMessage(msgID, filterString, equalPos+i+1);
                throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                        message);
            }

            buffer.put(byteValue);
          }
          else
          {
            buffer.put(valueBytes[i]);
          }
        }

        byte[] subAnyBytes = new byte[buffer.position()];
        buffer.flip();
        buffer.get(subAnyBytes);
        subAny.add(new ASN1OctetString(subAnyBytes));
      }
      else
      {
        byte[] subAnyBytes = new byte[length];
        System.arraycopy(valueBytes, firstPos+1, subAnyBytes, 0, length);
        subAny.add(new ASN1OctetString(subAnyBytes));
      }


      firstPos = asteriskPos;
    }


    // Finally, see if there is anything after the last asterisk, which would be
    // the subFinal value.
    ByteString subFinal;
    if (firstPos == (valueBytes.length-1))
    {
      subFinal = null;
    }
    else
    {
      int length = valueBytes.length - firstPos - 1;

      if (hasEscape)
      {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        for (int i=firstPos+1; i < valueBytes.length; i++)
        {
          if (valueBytes[i] == 0x5C)
          {
            // The next two bytes must be the hex characters that comprise the
            // binary value.
            if ((i + 2) >= valueBytes.length)
            {
              int    msgID   = MSGID_LDAP_FILTER_INVALID_ESCAPED_BYTE;
              String message = getMessage(msgID, filterString, equalPos+i+1);
              throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                      message);
            }

            byte byteValue = 0;
            switch (valueBytes[++i])
            {
              case 0x30: // '0'
                break;
              case 0x31: // '1'
                byteValue = (byte) 0x10;
                break;
              case 0x32: // '2'
                byteValue = (byte) 0x20;
                break;
              case 0x33: // '3'
                byteValue = (byte) 0x30;
                break;
              case 0x34: // '4'
                byteValue = (byte) 0x40;
                break;
              case 0x35: // '5'
                byteValue = (byte) 0x50;
                break;
              case 0x36: // '6'
                byteValue = (byte) 0x60;
                break;
              case 0x37: // '7'
                byteValue = (byte) 0x70;
                break;
              case 0x38: // '8'
                byteValue = (byte) 0x80;
                break;
              case 0x39: // '9'
                byteValue = (byte) 0x90;
                break;
              case 0x41: // 'A'
              case 0x61: // 'a'
                byteValue = (byte) 0xA0;
                break;
              case 0x42: // 'B'
              case 0x62: // 'b'
                byteValue = (byte) 0xB0;
                break;
              case 0x43: // 'C'
              case 0x63: // 'c'
                byteValue = (byte) 0xC0;
                break;
              case 0x44: // 'D'
              case 0x64: // 'd'
                byteValue = (byte) 0xD0;
                break;
              case 0x45: // 'E'
              case 0x65: // 'e'
                byteValue = (byte) 0xE0;
                break;
              case 0x46: // 'F'
              case 0x66: // 'f'
                byteValue = (byte) 0xF0;
                break;
              default:
                int    msgID   = MSGID_LDAP_FILTER_INVALID_ESCAPED_BYTE;
                String message = getMessage(msgID, filterString, equalPos+i+1);
                throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                        message);
            }

            switch (valueBytes[++i])
            {
              case 0x30: // '0'
                break;
              case 0x31: // '1'
                byteValue |= (byte) 0x01;
                break;
              case 0x32: // '2'
                byteValue |= (byte) 0x02;
                break;
              case 0x33: // '3'
                byteValue |= (byte) 0x03;
                break;
              case 0x34: // '4'
                byteValue |= (byte) 0x04;
                break;
              case 0x35: // '5'
                byteValue |= (byte) 0x05;
                break;
              case 0x36: // '6'
                byteValue |= (byte) 0x06;
                break;
              case 0x37: // '7'
                byteValue |= (byte) 0x07;
                break;
              case 0x38: // '8'
                byteValue |= (byte) 0x08;
                break;
              case 0x39: // '9'
                byteValue |= (byte) 0x09;
                break;
              case 0x41: // 'A'
              case 0x61: // 'a'
                byteValue |= (byte) 0x0A;
                break;
              case 0x42: // 'B'
              case 0x62: // 'b'
                byteValue |= (byte) 0x0B;
                break;
              case 0x43: // 'C'
              case 0x63: // 'c'
                byteValue |= (byte) 0x0C;
                break;
              case 0x44: // 'D'
              case 0x64: // 'd'
                byteValue |= (byte) 0x0D;
                break;
              case 0x45: // 'E'
              case 0x65: // 'e'
                byteValue |= (byte) 0x0E;
                break;
              case 0x46: // 'F'
              case 0x66: // 'f'
                byteValue |= (byte) 0x0F;
                break;
              default:
                int    msgID   = MSGID_LDAP_FILTER_INVALID_ESCAPED_BYTE;
                String message = getMessage(msgID, filterString, equalPos+i+1);
                throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                        message);
            }

            buffer.put(byteValue);
          }
          else
          {
            buffer.put(valueBytes[i]);
          }
        }

        byte[] subFinalBytes = new byte[buffer.position()];
        buffer.flip();
        buffer.get(subFinalBytes);
        subFinal = new ASN1OctetString(subFinalBytes);
      }
      else
      {
        byte[] subFinalBytes = new byte[length];
        System.arraycopy(valueBytes, firstPos+1, subFinalBytes, 0, length);
        subFinal = new ASN1OctetString(subFinalBytes);
      }
    }


    return new LDAPFilter(FilterType.SUBSTRING, null, null, attrType, null,
                          subInitial, subAny, subFinal, null, false);
  }



  /**
   * Decodes an extensible match filter component based on the provided
   * information.
   *
   * @param  filterString  The filter string containing the information to
   *                       decode.
   * @param  startPos      The position in the filter string of the first
   *                       character in the extensible match filter.
   * @param  equalPos      The position of the equal sign in the extensible
   *                       match filter.
   * @param  endPos        The position of the first character after the end of
   *                       the extensible match filter.
   *
   * @return  The decoded LDAP filter.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         extensible match filter.
   */
  private static LDAPFilter decodeExtensibleMatchFilter(String filterString,
                                                        int startPos,
                                                        int equalPos,
                                                        int endPos)
          throws LDAPException
  {
    String  attributeType  = null;
    boolean dnAttributes   = false;
    String  matchingRuleID = null;


    // Look at the first character.  If it is a colon, then it must be followed
    // by either the string "dn" or the matching rule ID.  If it is not, then
    // must be the attribute type.
    String lowerLeftStr =
         toLowerCase(filterString.substring(startPos, equalPos));
    if (filterString.charAt(startPos) == ':')
    {
      // See if it starts with ":dn".  Otherwise, it much be the matching rule
      // ID.
      if (lowerLeftStr.startsWith(":dn:"))
      {
        dnAttributes = true;

        if((startPos+4) < (equalPos-1))
        {
          matchingRuleID = filterString.substring(startPos+4, equalPos-1);
        }
      }
      else
      {
        matchingRuleID = filterString.substring(startPos+1, equalPos-1);
      }
    }
    else
    {
      int colonPos = filterString.indexOf(':',startPos);
      if (colonPos < 0)
      {
        int    msgID   = MSGID_LDAP_FILTER_EXTENSIBLE_MATCH_NO_COLON;
        String message = getMessage(msgID, filterString, startPos);
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
      }


      attributeType = filterString.substring(startPos, colonPos);


      // If there is anything left, then it should be ":dn" and/or ":" followed
      // by the matching rule ID.
      if (colonPos < (equalPos-1))
      {
        if (lowerLeftStr.startsWith(":dn:", colonPos - startPos))
        {
          dnAttributes = true;

          if ((colonPos+4) < (equalPos-1))
          {
            matchingRuleID = filterString.substring(colonPos+4, equalPos-1);
          }
        }
        else
        {
          matchingRuleID = filterString.substring(colonPos+1, equalPos-1);
        }
      }
    }


    // Parse out the attribute value.
    byte[] valueBytes = getBytes(filterString.substring(equalPos+1, endPos));
    boolean hasEscape = false;
    for (int i=0; i < valueBytes.length; i++)
    {
      if (valueBytes[i] == 0x5C)
      {
        hasEscape = true;
        break;
      }
    }

    ByteString value;
    if (hasEscape)
    {
      ByteBuffer valueBuffer = ByteBuffer.allocate(valueBytes.length);
      for (int i=0; i < valueBytes.length; i++)
      {
        if (valueBytes[i] == 0x5C) // The backslash character
        {
          // The next two bytes must be the hex characters that comprise the
          // binary value.
          if ((i + 2) >= valueBytes.length)
          {
            int    msgID   = MSGID_LDAP_FILTER_INVALID_ESCAPED_BYTE;
            String message = getMessage(msgID, filterString, equalPos+i+1);
            throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                    message);
          }

          byte byteValue = 0;
          switch (valueBytes[++i])
          {
            case 0x30: // '0'
              break;
            case 0x31: // '1'
              byteValue = (byte) 0x10;
              break;
            case 0x32: // '2'
              byteValue = (byte) 0x20;
              break;
            case 0x33: // '3'
              byteValue = (byte) 0x30;
              break;
            case 0x34: // '4'
              byteValue = (byte) 0x40;
              break;
            case 0x35: // '5'
              byteValue = (byte) 0x50;
              break;
            case 0x36: // '6'
              byteValue = (byte) 0x60;
              break;
            case 0x37: // '7'
              byteValue = (byte) 0x70;
              break;
            case 0x38: // '8'
              byteValue = (byte) 0x80;
              break;
            case 0x39: // '9'
              byteValue = (byte) 0x90;
              break;
            case 0x41: // 'A'
            case 0x61: // 'a'
              byteValue = (byte) 0xA0;
              break;
            case 0x42: // 'B'
            case 0x62: // 'b'
              byteValue = (byte) 0xB0;
              break;
            case 0x43: // 'C'
            case 0x63: // 'c'
              byteValue = (byte) 0xC0;
              break;
            case 0x44: // 'D'
            case 0x64: // 'd'
              byteValue = (byte) 0xD0;
              break;
            case 0x45: // 'E'
            case 0x65: // 'e'
              byteValue = (byte) 0xE0;
              break;
            case 0x46: // 'F'
            case 0x66: // 'f'
              byteValue = (byte) 0xF0;
              break;
            default:
              int    msgID   = MSGID_LDAP_FILTER_INVALID_ESCAPED_BYTE;
              String message = getMessage(msgID, filterString, equalPos+i+1);
              throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                      message);
          }

          switch (valueBytes[++i])
          {
            case 0x30: // '0'
              break;
            case 0x31: // '1'
              byteValue |= (byte) 0x01;
              break;
            case 0x32: // '2'
              byteValue |= (byte) 0x02;
              break;
            case 0x33: // '3'
              byteValue |= (byte) 0x03;
              break;
            case 0x34: // '4'
              byteValue |= (byte) 0x04;
              break;
            case 0x35: // '5'
              byteValue |= (byte) 0x05;
              break;
            case 0x36: // '6'
              byteValue |= (byte) 0x06;
              break;
            case 0x37: // '7'
              byteValue |= (byte) 0x07;
              break;
            case 0x38: // '8'
              byteValue |= (byte) 0x08;
              break;
            case 0x39: // '9'
              byteValue |= (byte) 0x09;
              break;
            case 0x41: // 'A'
            case 0x61: // 'a'
              byteValue |= (byte) 0x0A;
              break;
            case 0x42: // 'B'
            case 0x62: // 'b'
              byteValue |= (byte) 0x0B;
              break;
            case 0x43: // 'C'
            case 0x63: // 'c'
              byteValue |= (byte) 0x0C;
              break;
            case 0x44: // 'D'
            case 0x64: // 'd'
              byteValue |= (byte) 0x0D;
              break;
            case 0x45: // 'E'
            case 0x65: // 'e'
              byteValue |= (byte) 0x0E;
              break;
            case 0x46: // 'F'
            case 0x66: // 'f'
              byteValue |= (byte) 0x0F;
              break;
            default:
              int    msgID   = MSGID_LDAP_FILTER_INVALID_ESCAPED_BYTE;
              String message = getMessage(msgID, filterString, equalPos+i+1);
              throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                      message);
          }

          valueBuffer.put(byteValue);
        }
        else
        {
          valueBuffer.put(valueBytes[i]);
        }
      }

      valueBytes = new byte[valueBuffer.position()];
      valueBuffer.flip();
      valueBuffer.get(valueBytes);
      value = new ASN1OctetString(valueBytes);
    }
    else
    {
      value = new ASN1OctetString(valueBytes);
    }

    return new LDAPFilter(FilterType.EXTENSIBLE_MATCH, null, null,
                          attributeType, value, null, null, null,
                          matchingRuleID, dnAttributes);
  }



  /**
   * Retrieves the filter type for this search filter.
   *
   * @return  The filter type for this search filter.
   */
  public FilterType getFilterType()
  {
    return filterType;
  }



  /**
   * Retrieves the set of subordinate filter components for AND or OR searches.
   * The contents of the returned list may be altered by the caller.
   *
   * @return  The set of subordinate filter components for AND and OR searches,
   *          or <CODE>null</CODE> if this is not an AND or OR search.
   */
  public ArrayList<RawFilter> getFilterComponents()
  {
    return filterComponents;
  }



  /**
   * Specifies the set of subordinate filter components for AND or OR searches.
   * This will be ignored for all other filter types.
   *
   * @param  filterComponents  The set of subordinate filter components for AND
   *                           or OR searches.
   */
  public void setFilterComponents(ArrayList<RawFilter> filterComponents)
  {
    this.filterComponents = filterComponents;
  }



  /**
   * Retrieves the subordinate filter component for NOT searches.
   *
   * @return  The subordinate filter component for NOT searches, or
   *          <CODE>null</CODE> if this is not a NOT search.
   */
  public RawFilter getNOTComponent()
  {
    return notComponent;
  }



  /**
   * Specifies the subordinate filter component for NOT searches.  This will be
   * ignored for any other type of search.
   *
   * @param  notComponent  The subordinate filter component for NOT searches.
   */
  public void setNOTComponent(RawFilter notComponent)
  {
    this.notComponent = notComponent;
  }



  /**
   * Retrieves the attribute type for this search filter.  This will not be
   * applicable for AND, OR, or NOT filters.
   *
   * @return  The attribute type for this search filter, or <CODE>null</CODE> if
   *          there is none.
   */
  public String getAttributeType()
  {
    return attributeType;
  }



  /**
   * Specifies the attribute type for this search filter.  This will be ignored
   * for AND, OR, and NOT searches.
   *
   * @param  attributeType  The attribute type for this search filter.
   */
  public void setAttributeType(String attributeType)
  {
    this.attributeType = attributeType;
  }



  /**
   * Retrieves the assertion value for this search filter.  This will only be
   * applicable for equality, greater or equal, less or equal, approximate, or
   * extensible matching filters.
   *
   * @return  The assertion value for this search filter, or <CODE>null</CODE>
   *          if there is none.
   */
  public ByteString getAssertionValue()
  {
    return assertionValue;
  }



  /**
   * Specifies the assertion value for this search filter.  This will be ignored
   * for types of filters that do not have an assertion value.
   *
   * @param  assertionValue  The assertion value for this search filter.
   */
  public void setAssertionValue(ByteString assertionValue)
  {
    this.assertionValue = assertionValue;
  }



  /**
   * Retrieves the subInitial component for this substring filter.  This is only
   * applicable for substring search filters, but even substring filters might
   * not have a value for this component.
   *
   * @return  The subInitial component for this substring filter, or
   *          <CODE>null</CODE> if there is none.
   */
  public ByteString getSubInitialElement()
  {
    return subInitialElement;
  }



  /**
   * Specifies the subInitial element for this substring filter.  This will be
   * ignored for all other types of filters.
   *
   * @param  subInitialElement  The subInitial element for this substring
   *                            filter.
   */
  public void setSubInitialElement(ByteString subInitialElement)
  {
    this.subInitialElement = subInitialElement;
  }



  /**
   * Retrieves the set of subAny elements for this substring filter.  This is
   * only applicable for substring search filters, and even then may be null or
   * empty for some substring filters.
   *
   * @return  The set of subAny elements for this substring filter, or
   *          <CODE>null</CODE> if there are none.
   */
  public ArrayList<ByteString> getSubAnyElements()
  {
    return subAnyElements;
  }



  /**
   * Specifies the set of subAny values for this substring filter.  This will be
   * ignored for other filter types.
   *
   * @param  subAnyElements  The set of subAny elements for this substring
   *                         filter.
   */
  public void setSubAnyElements(ArrayList<ByteString> subAnyElements)
  {
    this.subAnyElements = subAnyElements;
  }



  /**
   * Retrieves the subFinal element for this substring filter.  This is not
   * applicable for any other filter type, and may not be provided even for some
   * substring filters.
   *
   * @return  The subFinal element for this substring filter, or
   *          <CODE>null</CODE> if there is none.
   */
  public ByteString getSubFinalElement()
  {
    return subFinalElement;
  }



  /**
   * Specifies the subFinal element for this substring filter.  This will be
   * ignored for all other filter types.
   *
   * @param  subFinalElement  The subFinal element for this substring filter.
   */
  public void setSubFinalElement(ByteString subFinalElement)
  {
    this.subFinalElement = subFinalElement;
  }



  /**
   * Retrieves the matching rule ID for this extensible match filter.  This is
   * not applicable for any other type of filter and may not be included in
   * some extensible matching filters.
   *
   * @return  The matching rule ID for this extensible match filter, or
   *          <CODE>null</CODE> if there is none.
   */
  public String getMatchingRuleID()
  {
    return matchingRuleID;
  }



  /**
   * Specifies the matching rule ID for this extensible match filter.  It will
   * be ignored for all other filter types.
   *
   * @param  matchingRuleID  The matching rule ID for this extensible match
   *                         filter.
   */
  public void setMatchingRuleID(String matchingRuleID)
  {
    this.matchingRuleID = matchingRuleID;
  }



  /**
   * Retrieves the value of the DN attributes flag for this extensible match
   * filter, which indicates whether to perform matching on the components of
   * the DN.  This does not apply for any other type of filter.
   *
   * @return  The value of the DN attributes flag for this extensibleMatch
   *          filter.
   */
  public boolean getDNAttributes()
  {
    return dnAttributes;
  }



  /**
   * Specifies the value of the DN attributes flag for this extensible match
   * filter.  It will be ignored for all other filter types.
   *
   * @param  dnAttributes  The value of the DN attributes flag for this
   *                       extensible match filter.
   */
  public void setDNAttributes(boolean dnAttributes)
  {
    this.dnAttributes = dnAttributes;
  }



  /**
   * Converts this LDAP filter to a search filter that may be used by the
   * Directory Server's core processing.
   *
   * @return  The generated search filter.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to
   *                              construct the search filter.
   */
  public SearchFilter toSearchFilter()
         throws DirectoryException
  {
    ArrayList<SearchFilter> subComps;
    if (filterComponents == null)
    {
      subComps = null;
    }
    else
    {
      subComps = new ArrayList<SearchFilter>(filterComponents.size());
      for (RawFilter f : filterComponents)
      {
        subComps.add(f.toSearchFilter());
      }
    }


    SearchFilter notComp;
    if (notComponent == null)
    {
      notComp = null;
    }
    else
    {
      notComp = notComponent.toSearchFilter();
    }


    AttributeType attrType;
    HashSet<String> options;
    if (attributeType == null)
    {
      attrType = null;
      options  = null;
    }
    else
    {
      int semicolonPos = attributeType.indexOf(';');
      if (semicolonPos > 0)
      {
        String baseName = attributeType.substring(0, semicolonPos);
        attrType = DirectoryServer.getAttributeType(toLowerCase(baseName));
        if (attrType == null)
        {
          attrType = DirectoryServer.getDefaultAttributeType(baseName);
        }

        options = new HashSet<String>();
        StringTokenizer tokenizer =
             new StringTokenizer(attributeType.substring(semicolonPos+1), ";");
        while (tokenizer.hasMoreTokens())
        {
          options.add(tokenizer.nextToken());
        }
      }
      else
      {
        options = null;
        attrType =
             DirectoryServer.getAttributeType(toLowerCase(attributeType));
        if (attrType == null)
        {
          attrType = DirectoryServer.getDefaultAttributeType(attributeType);
        }
      }
    }


    AttributeValue value;
    if (assertionValue == null)
    {
      value = null;
    }
    else if (attrType == null)
    {
      if (matchingRuleID == null)
      {
        int    msgID   = MSGID_LDAP_FILTER_VALUE_WITH_NO_ATTR_OR_MR;
        String message = getMessage(msgID);
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message, msgID);
      }
      else
      {
        MatchingRule mr =
             DirectoryServer.getMatchingRule(toLowerCase(matchingRuleID));
        if (mr == null)
        {
          int    msgID   = MSGID_LDAP_FILTER_UNKNOWN_MATCHING_RULE;
          String message = getMessage(msgID, matchingRuleID);
          throw new DirectoryException(ResultCode.INAPPROPRIATE_MATCHING,
                                       message, msgID);
        }
        else
        {
          ByteString normalizedValue = mr.normalizeValue(assertionValue);
          value = new AttributeValue(assertionValue, normalizedValue);
        }
      }
    }
    else
    {
      value = new AttributeValue(attrType, assertionValue);
    }


    ArrayList<ByteString> subAnyComps;
    if (subAnyElements == null)
    {
      subAnyComps = null;
    }
    else
    {
      subAnyComps = new ArrayList<ByteString>(subAnyElements);
    }


    return new SearchFilter(filterType, subComps, notComp, attrType,
                            options, value, subInitialElement, subAnyComps,
                            subFinalElement, matchingRuleID, dnAttributes);
  }



  /**
   * Appends a string representation of this search filter to the provided
   * buffer.
   *
   * @param  buffer  The buffer to which the information should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    switch (filterType)
    {
      case AND:
        buffer.append("(&");
        for (RawFilter f : filterComponents)
        {
          f.toString(buffer);
        }
        buffer.append(")");
        break;
      case OR:
        buffer.append("(|");
        for (RawFilter f : filterComponents)
        {
          f.toString(buffer);
        }
        buffer.append(")");
        break;
      case NOT:
        buffer.append("(!");
        notComponent.toString(buffer);
        buffer.append(")");
        break;
      case EQUALITY:
        buffer.append("(");
        buffer.append(attributeType);
        buffer.append("=");
        valueToFilterString(buffer, assertionValue);
        buffer.append(")");
        break;
      case SUBSTRING:
        buffer.append("(");
        buffer.append(attributeType);
        buffer.append("=");

        if (subInitialElement != null)
        {
          valueToFilterString(buffer, subInitialElement);
        }

        if ((subAnyElements != null) && (! subAnyElements.isEmpty()))
        {
          for (ByteString s : subAnyElements)
          {
            buffer.append("*");
            valueToFilterString(buffer, s);
          }
        }

        buffer.append("*");

        if (subFinalElement != null)
        {
          valueToFilterString(buffer, subFinalElement);
        }

        buffer.append(")");
        break;
      case GREATER_OR_EQUAL:
        buffer.append("(");
        buffer.append(attributeType);
        buffer.append(">=");
        valueToFilterString(buffer, assertionValue);
        buffer.append(")");
        break;
      case LESS_OR_EQUAL:
        buffer.append("(");
        buffer.append(attributeType);
        buffer.append("<=");
        valueToFilterString(buffer, assertionValue);
        buffer.append(")");
        break;
      case PRESENT:
        buffer.append("(");
        buffer.append(attributeType);
        buffer.append("=*)");
        break;
      case APPROXIMATE_MATCH:
        buffer.append("(");
        buffer.append(attributeType);
        buffer.append("~=");
        valueToFilterString(buffer, assertionValue);
        buffer.append(")");
        break;
      case EXTENSIBLE_MATCH:
        buffer.append("(");

        if (attributeType != null)
        {
          buffer.append(attributeType);
        }

        if (dnAttributes)
        {
          buffer.append(":dn");
        }

        if (matchingRuleID != null)
        {
          buffer.append(":");
          buffer.append(matchingRuleID);
        }

        buffer.append(":=");
        valueToFilterString(buffer, assertionValue);
        buffer.append(")");
        break;
    }
  }
}

