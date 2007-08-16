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
package org.opends.server.types;
import org.opends.messages.Message;



import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.Collections;

import org.opends.server.api.MatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1OctetString;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.util.ServerConstants.*;



/**
 * This class defines a data structure for storing and interacting
 * with a search filter that may serve as criteria for locating
 * entries in the Directory Server.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public final class SearchFilter
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The attribute type for this filter.
  private final AttributeType attributeType;

  // The assertion value for this filter.
  private final AttributeValue assertionValue;

  // Indicates whether to match on DN attributes for extensible match
  // filters.
  private final boolean dnAttributes;

  // The subFinal element for substring filters.
  private final ByteString subFinalElement;

  // The subInitial element for substring filters.
  private final ByteString subInitialElement;

  // The search filter type for this filter.
  private final FilterType filterType;

  // The set of subAny components for substring filters.
  private final List<ByteString> subAnyElements;

  // The set of filter components for AND and OR filters.
  private final LinkedHashSet<SearchFilter> filterComponents;

  // The not filter component for this search filter.
  private final SearchFilter notComponent;

  // The set of options for the attribute type in this filter.
  private final Set<String> attributeOptions;

  // The matching rule ID for this search filter.
  private final String matchingRuleID;



  /**
   * Creates a new search filter with the provided information.
   *
   * @param  filterType         The filter type for this search
   *                            filter.
   * @param  filterComponents   The set of filter components for AND
   *                            and OR filters.
   * @param  notComponent       The filter component for NOT filters.
   * @param  attributeType      The attribute type for this filter.
   * @param  attributeOptions   The set of attribute options for the
   *                            associated attribute type.
   * @param  assertionValue     The assertion value for this filter.
   * @param  subInitialElement  The subInitial element for substring
   *                            filters.
   * @param  subAnyElements     The subAny elements for substring
   *                            filters.
   * @param  subFinalElement    The subFinal element for substring
   *                            filters.
   * @param  matchingRuleID     The matching rule ID for this search
   *                            filter.
   * @param  dnAttributes       Indicates whether to match on DN
   *                            attributes for extensible match
   *                            filters.
   *
   * FIXME: this should be private.
   */
  public SearchFilter(FilterType filterType,
                      Collection<SearchFilter> filterComponents,
                      SearchFilter notComponent,
                      AttributeType attributeType,
                      Set<String> attributeOptions,
                      AttributeValue assertionValue,
                      ByteString subInitialElement,
                      List<ByteString> subAnyElements,
                      ByteString subFinalElement,
                      String matchingRuleID, boolean dnAttributes)
  {
    // This used to happen in getSubAnyElements, but we do it here
    // so that we can make this.subAnyElements final.
    if (subAnyElements == null) {
      subAnyElements = new ArrayList<ByteString>(0);
    }

    // This used to happen in getFilterComponents, but we do it here
    // so that we can make this.filterComponents final.
    if (filterComponents == null) {
      filterComponents = Collections.emptyList();
    }

    this.filterType        = filterType;
    this.filterComponents  =
            new LinkedHashSet<SearchFilter>(filterComponents);
    this.notComponent      = notComponent;
    this.attributeType     = attributeType;
    this.attributeOptions  = attributeOptions;
    this.assertionValue    = assertionValue;
    this.subInitialElement = subInitialElement;
    this.subAnyElements    = subAnyElements;
    this.subFinalElement   = subFinalElement;
    this.matchingRuleID    = matchingRuleID;
    this.dnAttributes      = dnAttributes;
  }


  /**
   * Creates a new AND search filter with the provided information.
   *
   * @param  filterComponents  The set of filter components for the
   * AND filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createANDFilter(Collection<SearchFilter>
                                                  filterComponents)
  {
    return new SearchFilter(FilterType.AND, filterComponents, null,
                            null, null, null, null, null, null, null,
                            false);
  }



  /**
   * Creates a new OR search filter with the provided information.
   *
   * @param  filterComponents  The set of filter components for the OR
   *                           filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createORFilter(Collection<SearchFilter>
                                                 filterComponents)
  {
    return new SearchFilter(FilterType.OR, filterComponents, null,
                            null, null, null, null, null, null, null,
                            false);
  }



  /**
   * Creates a new NOT search filter with the provided information.
   *
   * @param  notComponent  The filter component for this NOT filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createNOTFilter(
                                  SearchFilter notComponent)
  {
    return new SearchFilter(FilterType.NOT, null, notComponent, null,
                            null, null, null, null, null, null,
                            false);
  }



  /**
   * Creates a new equality search filter with the provided
   * information.
   *
   * @param  attributeType   The attribute type for this equality
   *                         filter.
   * @param  assertionValue  The assertion value for this equality
   *                         filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createEqualityFilter(
                                  AttributeType attributeType,
                                  AttributeValue assertionValue)
  {
    return new SearchFilter(FilterType.EQUALITY, null, null,
                            attributeType, null, assertionValue, null,
                            null, null, null, false);
  }



  /**
   * Creates a new equality search filter with the provided
   * information.
   *
   * @param  attributeType     The attribute type for this equality
   *                           filter.
   * @param  attributeOptions  The set of attribute options for this
   *                           equality filter.
   * @param  assertionValue    The assertion value for this equality
   *                           filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createEqualityFilter(
                                  AttributeType attributeType,
                                  Set<String> attributeOptions,
                                  AttributeValue assertionValue)
  {
    return new SearchFilter(FilterType.EQUALITY, null, null,
                            attributeType, attributeOptions,
                            assertionValue, null, null, null, null,
                            false);
  }



  /**
   * Creates a new substring search filter with the provided
   * information.
   *
   * @param  attributeType      The attribute type for this filter.
   * @param  subInitialElement  The subInitial element for substring
   *                            filters.
   * @param  subAnyElements     The subAny elements for substring
   *                            filters.
   * @param  subFinalElement    The subFinal element for substring
   *                            filters.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter
       createSubstringFilter(AttributeType attributeType,
                             ByteString subInitialElement,
                             List<ByteString> subAnyElements,
                             ByteString subFinalElement)
  {
    return new SearchFilter(FilterType.SUBSTRING, null, null,
                            attributeType, null, null,
                            subInitialElement, subAnyElements,
                            subFinalElement, null, false);
  }



  /**
   * Creates a new substring search filter with the provided
   * information.
   *
   * @param  attributeType      The attribute type for this filter.
   * @param  attributeOptions   The set of attribute options for this
   *                            search filter.
   * @param  subInitialElement  The subInitial element for substring
   *                            filters.
   * @param  subAnyElements     The subAny elements for substring
   *                            filters.
   * @param  subFinalElement    The subFinal element for substring
   *                            filters.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter
       createSubstringFilter(AttributeType attributeType,
                             Set<String> attributeOptions,
                             ByteString subInitialElement,
                             List<ByteString> subAnyElements,
                             ByteString subFinalElement)
  {
    return new SearchFilter(FilterType.SUBSTRING, null, null,
                            attributeType, attributeOptions, null,
                            subInitialElement, subAnyElements,
                            subFinalElement, null, false);
  }



  /**
   * Creates a greater-or-equal search filter with the provided
   * information.
   *
   * @param  attributeType   The attribute type for this
   *                         greater-or-equal filter.
   * @param  assertionValue  The assertion value for this
   *                         greater-or-equal filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createGreaterOrEqualFilter(
                                  AttributeType attributeType,
                                  AttributeValue assertionValue)
  {
    return new SearchFilter(FilterType.GREATER_OR_EQUAL, null, null,
                            attributeType, null, assertionValue, null,
                            null, null, null, false);
  }



  /**
   * Creates a greater-or-equal search filter with the provided
   * information.
   *
   * @param  attributeType     The attribute type for this
   *                           greater-or-equal filter.
   * @param  attributeOptions  The set of attribute options for this
   *                           search filter.
   * @param  assertionValue    The assertion value for this
   *                           greater-or-equal filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createGreaterOrEqualFilter(
                                  AttributeType attributeType,
                                  Set<String> attributeOptions,
                                  AttributeValue assertionValue)
  {
    return new SearchFilter(FilterType.GREATER_OR_EQUAL, null, null,
                            attributeType, attributeOptions,
                            assertionValue, null, null, null, null,
                            false);
  }



  /**
   * Creates a less-or-equal search filter with the provided
   * information.
   *
   * @param  attributeType   The attribute type for this less-or-equal
   *                         filter.
   * @param  assertionValue  The assertion value for this
   *                         less-or-equal filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createLessOrEqualFilter(
                                  AttributeType attributeType,
                                  AttributeValue assertionValue)
  {
    return new SearchFilter(FilterType.LESS_OR_EQUAL, null, null,
                            attributeType, null, assertionValue, null,
                            null, null, null, false);
  }



  /**
   * Creates a less-or-equal search filter with the provided
   * information.
   *
   * @param  attributeType     The attribute type for this
   *                           less-or-equal filter.
   * @param  attributeOptions  The set of attribute options for this
   *                           search filter.
   * @param  assertionValue    The assertion value for this
   *                           less-or-equal filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createLessOrEqualFilter(
                                  AttributeType attributeType,
                                  Set<String> attributeOptions,
                                  AttributeValue assertionValue)
  {
    return new SearchFilter(FilterType.LESS_OR_EQUAL, null, null,
                            attributeType, attributeOptions,
                            assertionValue, null, null, null, null,
                            false);
  }



  /**
   * Creates a presence search filter with the provided information.
   *
   * @param  attributeType  The attribute type for this presence
   *                        filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createPresenceFilter(
                                  AttributeType attributeType)
  {
    return new SearchFilter(FilterType.PRESENT, null, null,
                            attributeType, null, null, null, null,
                            null, null, false);
  }



  /**
   * Creates a presence search filter with the provided information.
   *
   * @param  attributeType     The attribute type for this presence
   *                           filter.
   * @param  attributeOptions  The attribute options for this presence
   *                           filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createPresenceFilter(
                                  AttributeType attributeType,
                                  Set<String> attributeOptions)
  {
    return new SearchFilter(FilterType.PRESENT, null, null,
                            attributeType, attributeOptions, null,
                            null, null, null, null, false);
  }



  /**
   * Creates an approximate search filter with the provided
   * information.
   *
   * @param  attributeType   The attribute type for this approximate
   *                         filter.
   * @param  assertionValue  The assertion value for this approximate
   *                         filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createApproximateFilter(
                                  AttributeType attributeType,
                                  AttributeValue assertionValue)
  {
    return new SearchFilter(FilterType.APPROXIMATE_MATCH, null, null,
                            attributeType, null, assertionValue, null,
                            null, null, null, false);
  }



  /**
   * Creates an approximate search filter with the provided
   * information.
   *
   * @param  attributeType     The attribute type for this approximate
   *                           filter.
   * @param  attributeOptions  The attribute options for this
   *                           approximate filter.
   * @param  assertionValue    The assertion value for this
   *                           approximate filter.
   *
   * @return  The constructed search filter.
   */
  public static SearchFilter createApproximateFilter(
                                  AttributeType attributeType,
                                  Set<String> attributeOptions,
                                  AttributeValue assertionValue)
  {
    return new SearchFilter(FilterType.APPROXIMATE_MATCH, null, null,
                            attributeType, attributeOptions,
                            assertionValue, null, null, null, null,
                            false);
  }



  /**
   * Creates an extensible matching filter with the provided
   * information.
   *
   * @param  attributeType   The attribute type for this extensible
   *                         match filter.
   * @param  assertionValue  The assertion value for this extensible
   *                         match filter.
   * @param  matchingRuleID  The matching rule ID for this search
   *                         filter.
   * @param  dnAttributes    Indicates whether to match on DN
   *                         attributes for extensible match filters.
   *
   * @return  The constructed search filter.
   *
   * @throws  DirectoryException  If the provided information is not
   *                              sufficient to create an extensible
   *                              match filter.
   */
  public static SearchFilter createExtensibleMatchFilter(
                                  AttributeType attributeType,
                                  AttributeValue assertionValue,
                                  String matchingRuleID,
                                  boolean dnAttributes)
         throws DirectoryException
  {
    if ((attributeType == null) && (matchingRuleID == null))
    {
      Message message =
          ERR_SEARCH_FILTER_CREATE_EXTENSIBLE_MATCH_NO_AT_OR_MR.get();
      throw new DirectoryException(
              ResultCode.PROTOCOL_ERROR, message);
    }

    return new SearchFilter(FilterType.EXTENSIBLE_MATCH, null, null,
                            attributeType, null, assertionValue, null,
                            null, null, matchingRuleID, dnAttributes);
  }



  /**
   * Creates an extensible matching filter with the provided
   * information.
   *
   * @param  attributeType     The attribute type for this extensible
   *                           match filter.
   * @param  attributeOptions  The set of attribute options for this
   *                           extensible match filter.
   * @param  assertionValue    The assertion value for this extensible
   *                           match filter.
   * @param  matchingRuleID    The matching rule ID for this search
   *                           filter.
   * @param  dnAttributes      Indicates whether to match on DN
   *                           attributes for extensible match
   *                           filters.
   *
   * @return  The constructed search filter.
   *
   * @throws  DirectoryException  If the provided information is not
   *                              sufficient to create an extensible
   *                              match filter.
   */
  public static SearchFilter createExtensibleMatchFilter(
                                  AttributeType attributeType,
                                  Set<String> attributeOptions,
                                  AttributeValue assertionValue,
                                  String matchingRuleID,
                                  boolean dnAttributes)
         throws DirectoryException
  {
    if ((attributeType == null) && (matchingRuleID == null))
    {
      Message message =
          ERR_SEARCH_FILTER_CREATE_EXTENSIBLE_MATCH_NO_AT_OR_MR.get();
      throw new DirectoryException(
              ResultCode.PROTOCOL_ERROR, message);
    }

    return new SearchFilter(FilterType.EXTENSIBLE_MATCH, null, null,
                            attributeType, attributeOptions,
                            assertionValue, null, null, null,
                            matchingRuleID, dnAttributes);
  }



  /**
   * Decodes the provided filter string as a search filter.
   *
   * @param  filterString  The filter string to be decoded as a search
   *                       filter.
   *
   * @return  The search filter decoded from the provided string.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to decode the provided string as a
   *                              search filter.
   */
  public static SearchFilter createFilterFromString(
                                  String filterString)
         throws DirectoryException
  {
    if (filterString == null)
    {
      Message message = ERR_SEARCH_FILTER_NULL.get();
      throw new DirectoryException(
              ResultCode.PROTOCOL_ERROR, message);
    }


    try
    {
      return createFilterFromString(filterString, 0,
                                    filterString.length());
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      throw de;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_SEARCH_FILTER_UNCAUGHT_EXCEPTION.get(
          filterString, String.valueOf(e));
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR, message,
                                   e);
    }
  }



  /**
   * Creates a new search filter from the specified portion of the
   * provided string.
   *
   * @param  filterString  The string containing the filter
   *                       information to be decoded.
   * @param  startPos      The index of the first character in the
   *                       string that is part of the search filter.
   * @param  endPos        The index of the first character after the
   *                       start position that is not part of the
   *                       search filter.
   *
   * @return  The decoded search filter.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to decode the provided string as a
   *                              search filter.
   */
  private static SearchFilter createFilterFromString(
                                   String filterString, int startPos,
                                   int endPos)
          throws DirectoryException
  {
    // Make sure that the length is sufficient for a valid search
    // filter.
    int length = endPos - startPos;
    if (length <= 0)
    {
      Message message = ERR_SEARCH_FILTER_NULL.get();
      throw new DirectoryException(
              ResultCode.PROTOCOL_ERROR, message);
    }


    // If the filter is surrounded by parentheses (which it should
    // be), then strip them off.
    if (filterString.charAt(startPos) == '(')
    {
      if (filterString.charAt(endPos-1) == ')')
      {
        startPos++;
        endPos--;
      }
      else
      {
        Message message = ERR_SEARCH_FILTER_MISMATCHED_PARENTHESES.
            get(filterString, startPos, endPos);
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                     message);
      }
    }


    // Look at the first character.  If it is a '&' then it is an AND
    // search.  If it is a '|' then it is an OR search.  If it is a
    // '!' then it is a NOT search.
    char c = filterString.charAt(startPos);
    if (c == '&')
    {
      return decodeCompoundFilter(FilterType.AND, filterString,
                                  startPos+1, endPos);
    }
    else if (c == '|')
    {
      return decodeCompoundFilter(FilterType.OR, filterString,
                                  startPos+1, endPos);
    }
    else if (c == '!')
    {
      return decodeCompoundFilter(FilterType.NOT, filterString,
                                  startPos+1, endPos);
    }


    // If we've gotten here, then it must be a simple filter.  It must
    // have an equal sign at some point, so find it.
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
      Message message = ERR_SEARCH_FILTER_NO_EQUAL_SIGN.get(
          filterString, startPos, endPos);
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                   message);
    }


    // Look at the character immediately before the equal sign,
    // because it may help determine the filter type.
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
        return decodeExtensibleMatchFilter(filterString, startPos,
                                           equalPos, endPos);
      default:
        filterType = FilterType.EQUALITY;
        attrEndPos = equalPos;
        break;
    }


    // The part of the filter string before the equal sign should be
    // the attribute type (with or without options).  Decode it.
    String attrType = filterString.substring(startPos, attrEndPos);
    StringBuilder lowerType = new StringBuilder(attrType.length());
    Set<String> attributeOptions = new HashSet<String>();

    int semicolonPos = attrType.indexOf(';');
    if (semicolonPos < 0)
    {
      for (int i=0; i < attrType.length(); i++)
      {
        lowerType.append(Character.toLowerCase(attrType.charAt(i)));
      }
    }
    else
    {
      for (int i=0; i < semicolonPos; i++)
      {
        lowerType.append(Character.toLowerCase(attrType.charAt(i)));
      }

      int nextPos = attrType.indexOf(';', semicolonPos+1);
      while (nextPos > 0)
      {
        attributeOptions.add(attrType.substring(semicolonPos+1,
                                                nextPos));
        semicolonPos = nextPos;
        nextPos = attrType.indexOf(';', semicolonPos+1);
      }

      attributeOptions.add(attrType.substring(semicolonPos+1));
    }


    // Get the attribute type for the specified name.
    AttributeType attributeType =
         DirectoryServer.getAttributeType(lowerType.toString());
    if (attributeType == null)
    {
      String typeStr = attrType.substring(0, lowerType.length());
      attributeType =
           DirectoryServer.getDefaultAttributeType(typeStr);
    }


    // Get the attribute value.
    String valueStr = filterString.substring(equalPos+1, endPos);
    if (valueStr.length() == 0)
    {
      return new SearchFilter(filterType, null, null, attributeType,
                    attributeOptions,
                    new AttributeValue(new ASN1OctetString(),
                                       new ASN1OctetString()),
                    null, null, null, null, false);
    }
    else if (valueStr.equals("*"))
    {
      return new SearchFilter(FilterType.PRESENT, null, null,
                              attributeType, attributeOptions, null,
                              null, null, null, null, false);
    }
    else if (valueStr.indexOf('*') >= 0)
    {
      return decodeSubstringFilter(filterString, attributeType,
                                   attributeOptions, equalPos,
                                   endPos);
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

      ByteString userValue;
      if (hasEscape)
      {
        ByteBuffer valueBuffer =
             ByteBuffer.allocate(valueStr.length());
        for (int i=0; i < valueBytes.length; i++)
        {
          if (valueBytes[i] == 0x5C) // The backslash character
          {
            // The next two bytes must be the hex characters that
            // comprise the binary value.
            if ((i + 2) >= valueBytes.length)
            {
              Message message =
                  ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                    get(filterString, equalPos+i+1);
              throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
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
                Message message =
                    ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                      get(filterString, equalPos+i+1);
                throw new DirectoryException(
                               ResultCode.PROTOCOL_ERROR, message);
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
                Message message =
                    ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                      get(filterString, equalPos+i+1);
                throw new DirectoryException(
                               ResultCode.PROTOCOL_ERROR, message);
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
        userValue = new ASN1OctetString(valueBytes);
      }
      else
      {
        userValue = new ASN1OctetString(valueBytes);
      }

      AttributeValue value =
           new AttributeValue(attributeType, userValue);
      return new SearchFilter(filterType, null, null, attributeType,
                              attributeOptions, value, null, null,
                              null, null, false);
    }
  }



  /**
   * Decodes a set of filters from the provided filter string within
   * the indicated range.
   *
   * @param  filterType    The filter type for this compound filter.
   *                       It must be an AND, OR or NOT filter.
   * @param  filterString  The string containing the filter
   *                       information to decode.
   * @param  startPos      The position of the first character in the
   *                       set of filters to decode.
   * @param  endPos        The position of the first character after
   *                       the end of the set of filters to decode.
   *
   * @return  The decoded search filter.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to decode the compound filter.
   */
  private static SearchFilter decodeCompoundFilter(
                                   FilterType filterType,
                                   String filterString, int startPos,
                                   int endPos)
          throws DirectoryException
  {
    // Create a list to hold the returned components.
    List<SearchFilter> filterComponents =
         new ArrayList<SearchFilter>();


    // If the end pos is equal to the start pos, then there are no
    // components.
    if (startPos == endPos)
    {
      if (filterType == FilterType.NOT)
      {
        Message message = ERR_SEARCH_FILTER_NOT_EXACTLY_ONE.get(
            filterString, startPos, endPos);
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                     message);
      }
      else
      {
        // This is valid and will be treated as a TRUE/FALSE filter.
        return new SearchFilter(filterType, filterComponents, null,
                                null, null, null, null, null, null,
                                null, false);
      }
    }


    // The first and last characters must be parentheses.  If not,
    // then that's an error.
    if ((filterString.charAt(startPos) != '(') ||
        (filterString.charAt(endPos-1) != ')'))
    {
      Message message =
          ERR_SEARCH_FILTER_COMPOUND_MISSING_PARENTHESES.
            get(filterString, startPos, endPos);
      throw new DirectoryException(
              ResultCode.PROTOCOL_ERROR, message);
    }


    // Iterate through the characters in the value.  Whenever an open
    // parenthesis is found, locate the corresponding close
    // parenthesis by counting the number of intermediate open/close
    // parentheses.
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
          filterComponents.add(createFilterFromString(filterString,
                                                      openPos, i+1));
          openPos = -1;
        }
        else if (pendingOpens < 0)
        {
          Message message =
              ERR_SEARCH_FILTER_NO_CORRESPONDING_OPEN_PARENTHESIS.
                get(filterString, i);
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                       message);
        }
      }
      else if (pendingOpens <= 0)
      {
        Message message =
            ERR_SEARCH_FILTER_COMPOUND_MISSING_PARENTHESES.
              get(filterString, startPos, endPos);
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                     message);
      }
    }


    // At this point, we have parsed the entire set of filter
    // components.  The list of open parenthesis positions must be
    // empty.
    if (pendingOpens != 0)
    {
      Message message =
          ERR_SEARCH_FILTER_NO_CORRESPONDING_CLOSE_PARENTHESIS.
            get(filterString, openPos);
      throw new DirectoryException(
              ResultCode.PROTOCOL_ERROR, message);
    }


    // We should have everything we need, so return the list.
    if (filterType == FilterType.NOT)
    {
      if (filterComponents.size() != 1)
      {
        Message message = ERR_SEARCH_FILTER_NOT_EXACTLY_ONE.get(
            filterString, startPos, endPos);
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                     message);
      }
      SearchFilter notComponent = filterComponents.get(0);
      return new SearchFilter(filterType, null, notComponent, null,
                              null, null, null, null, null, null,
                              false);
    }
    else
    {
      return new SearchFilter(filterType, filterComponents, null,
                              null, null, null, null, null, null,
                              null, false);
    }
  }


  /**
   * Decodes a substring search filter component based on the provided
   * information.
   *
   * @param  filterString  The filter string containing the
   *                       information to decode.
   * @param  attrType      The attribute type for this substring
   *                       filter component.
   * @param  options       The set of attribute options for the
   *                       associated attribute type.
   * @param  equalPos      The location of the equal sign separating
   *                       the attribute type from the value.
   * @param  endPos        The position of the first character after
   *                       the end of the substring value.
   *
   * @return  The decoded search filter.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to decode the substring filter.
   */
  private static SearchFilter decodeSubstringFilter(
                                   String filterString,
                                   AttributeType attrType,
                                   Set<String> options, int equalPos,
                                   int endPos)
          throws DirectoryException
  {
    // Get a binary representation of the value.
    byte[] valueBytes =
         getBytes(filterString.substring(equalPos+1, endPos));


    // Find the locations of all the asterisks in the value.  Also,
    // check to see if there are any escaped values, since they will
    // need special treatment.
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
      Message message = ERR_SEARCH_FILTER_SUBSTRING_NO_ASTERISKS.get(
          filterString, equalPos+1, endPos);
      throw new DirectoryException(
              ResultCode.PROTOCOL_ERROR, message);
    }
    else
    {
      // The rest of the processing will be only on the value bytes,
      // so re-adjust the end position.
      endPos = valueBytes.length;
    }


    // If the value starts with an asterisk, then there is no
    // subInitial component.  Otherwise, parse out the subInitial.
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
            // The next two bytes must be the hex characters that
            // comprise the binary value.
            if ((i + 2) >= valueBytes.length)
            {
              Message message =
                  ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                    get(filterString, equalPos+i+1);
              throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
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
                Message message =
                    ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                      get(filterString, equalPos+i+1);
                throw new DirectoryException(
                               ResultCode.PROTOCOL_ERROR, message);
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
                Message message =
                    ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                      get(filterString, equalPos+i+1);
                throw new DirectoryException(
                               ResultCode.PROTOCOL_ERROR, message);
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


    // Next, process through the rest of the asterisks to get the
    // subAny values.
    List<ByteString> subAny = new ArrayList<ByteString>();
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
            // The next two bytes must be the hex characters that
            // comprise the binary value.
            if ((i + 2) >= valueBytes.length)
            {
              Message message =
                  ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                    get(filterString, equalPos+i+1);
              throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
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
                Message message =
                    ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                      get(filterString, equalPos+i+1);
                throw new DirectoryException(
                               ResultCode.PROTOCOL_ERROR, message);
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
                Message message =
                    ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                      get(filterString, equalPos+i+1);
                throw new DirectoryException(
                               ResultCode.PROTOCOL_ERROR, message);
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
        System.arraycopy(valueBytes, firstPos+1, subAnyBytes, 0,
                         length);
        subAny.add(new ASN1OctetString(subAnyBytes));
      }


      firstPos = asteriskPos;
    }


    // Finally, see if there is anything after the last asterisk,
    // which would be the subFinal value.
    ByteString subFinal;
    if (firstPos == (endPos-1))
    {
      subFinal = null;
    }
    else
    {
      int length = endPos - firstPos - 1;

      if (hasEscape)
      {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        for (int i=firstPos+1; i < endPos; i++)
        {
          if (valueBytes[i] == 0x5C)
          {
            // The next two bytes must be the hex characters that
            // comprise the binary value.
            if ((i + 2) >= valueBytes.length)
            {
              Message message =
                  ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                    get(filterString, equalPos+i+1);
              throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
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
                Message message =
                    ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                      get(filterString, equalPos+i+1);
                throw new DirectoryException(
                               ResultCode.PROTOCOL_ERROR, message);
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
                Message message =
                    ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                      get(filterString, equalPos+i+1);
                throw new DirectoryException(
                               ResultCode.PROTOCOL_ERROR, message);
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
        System.arraycopy(valueBytes, firstPos+1, subFinalBytes, 0,
                         length);
        subFinal = new ASN1OctetString(subFinalBytes);
      }
    }


    return new SearchFilter(FilterType.SUBSTRING, null, null,
                            attrType, options, null, subInitial,
                            subAny, subFinal, null, false);
  }



  /**
   * Decodes an extensible match filter component based on the
   * provided information.
   *
   * @param  filterString  The filter string containing the
   *                       information to decode.
   * @param  startPos      The position in the filter string of the
   *                       first character in the extensible match
   *                       filter.
   * @param  equalPos      The position of the equal sign in the
   *                       extensible match filter.
   * @param  endPos        The position of the first character after
   *                       the end of the extensible match filter.
   *
   * @return  The decoded search filter.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to decode the extensible match
   *                              filter.
   */
  private static SearchFilter decodeExtensibleMatchFilter(
                                   String filterString, int startPos,
                                   int equalPos, int endPos)
          throws DirectoryException
  {
    AttributeType attributeType    = null;
    Set<String>   attributeOptions = new HashSet<String>();
    boolean       dnAttributes     = false;
    String        matchingRuleID   = null;


    // Look at the first character.  If it is a colon, then it must be
    // followed by either the string "dn" or the matching rule ID.  If
    // it is not, then it must be the attribute type.
    String lowerLeftStr =
         toLowerCase(filterString.substring(startPos, equalPos));
    if (filterString.charAt(startPos) == ':')
    {
      // See if it starts with ":dn".  Otherwise, it much be the
      // matching rule
      // ID.
      if (lowerLeftStr.startsWith(":dn:"))
      {
        dnAttributes = true;

        matchingRuleID =
             filterString.substring(startPos+4, equalPos-1);
      }
      else
      {
        matchingRuleID =
             filterString.substring(startPos+1, equalPos-1);
      }
    }
    else
    {
      int colonPos = filterString.indexOf(':',startPos);
      if (colonPos < 0)
      {
        Message message = ERR_SEARCH_FILTER_EXTENSIBLE_MATCH_NO_COLON.
            get(filterString, startPos);
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                     message);
      }


      String attrType = filterString.substring(startPos, colonPos);
      StringBuilder lowerType = new StringBuilder(attrType.length());

      int semicolonPos = attrType.indexOf(';');
      if (semicolonPos <0)
      {
        for (int i=0; i < attrType.length(); i++)
        {
          lowerType.append(Character.toLowerCase(attrType.charAt(i)));
        }
      }
      else
      {
        for (int i=0; i < semicolonPos; i++)
        {
          lowerType.append(Character.toLowerCase(attrType.charAt(i)));
        }

        int nextPos = attrType.indexOf(';', semicolonPos+1);
        while (nextPos > 0)
        {
          attributeOptions.add(attrType.substring(semicolonPos+1,
                                                  nextPos));
          semicolonPos = nextPos;
          nextPos = attrType.indexOf(';', semicolonPos+1);
        }

        attributeOptions.add(attrType.substring(semicolonPos+1));
      }


      // Get the attribute type for the specified name.
      attributeType =
           DirectoryServer.getAttributeType(lowerType.toString());
      if (attributeType == null)
      {
        String typeStr = attrType.substring(0, lowerType.length());
        attributeType =
             DirectoryServer.getDefaultAttributeType(typeStr);
      }


      // If there is anything left, then it should be ":dn" and/or ":"
      // followed by the matching rule ID.
      if (colonPos < (equalPos-1))
      {
        if (lowerLeftStr.startsWith(":dn:", colonPos))
        {
          dnAttributes = true;

          if ((colonPos+4) < (equalPos-1))
          {
            matchingRuleID =
                 filterString.substring(colonPos+4, equalPos-1);
          }
        }
        else
        {
          matchingRuleID =
               filterString.substring(colonPos+1, equalPos-1);
        }
      }
    }


    // Parse out the attribute value.
    byte[] valueBytes = getBytes(filterString.substring(equalPos+1,
                                                        endPos));
    boolean hasEscape = false;
    for (int i=0; i < valueBytes.length; i++)
    {
      if (valueBytes[i] == 0x5C)
      {
        hasEscape = true;
        break;
      }
    }

    ByteString userValue;
    if (hasEscape)
    {
      ByteBuffer valueBuffer = ByteBuffer.allocate(valueBytes.length);
      for (int i=0; i < valueBytes.length; i++)
      {
        if (valueBytes[i] == 0x5C) // The backslash character
        {
          // The next two bytes must be the hex characters that
          // comprise the binary value.
          if ((i + 2) >= valueBytes.length)
          {
            Message message = ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                get(filterString, equalPos+i+1);
            throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
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
              Message message =
                  ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                    get(filterString, equalPos+i+1);
              throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
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
              Message message =
                  ERR_SEARCH_FILTER_INVALID_ESCAPED_BYTE.
                    get(filterString, equalPos+i+1);
              throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
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
      userValue = new ASN1OctetString(valueBytes);
    }
    else
    {
      userValue = new ASN1OctetString(valueBytes);
    }

    // Make sure that the filter contains at least one of an attribute
    // type or a matching rule ID.  Also, construct the appropriate
    // attribute  value.
    AttributeValue value;
    if (attributeType == null)
    {
      if (matchingRuleID == null)
      {
        Message message =
            ERR_SEARCH_FILTER_EXTENSIBLE_MATCH_NO_AD_OR_MR.
              get(filterString, startPos);
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                     message);
      }
      else
      {
        MatchingRule mr = DirectoryServer.getMatchingRule(
                               toLowerCase(matchingRuleID));
        if (mr == null)
        {
          Message message =
              ERR_SEARCH_FILTER_EXTENSIBLE_MATCH_NO_SUCH_MR.
                get(filterString, startPos, matchingRuleID);
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                       message);
        }
        else
        {
          value = new AttributeValue(userValue,
                                     mr.normalizeValue(userValue));
        }
      }
    }
    else
    {
      value = new AttributeValue(attributeType, userValue);
    }

    return new SearchFilter(FilterType.EXTENSIBLE_MATCH, null, null,
                            attributeType, attributeOptions, value,
                            null, null, null, matchingRuleID,
                            dnAttributes);
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
   * Retrieves the set of filter components for this AND or OR filter.
   * The returned list can be modified by the caller.
   *
   * @return  The set of filter components for this AND or OR filter.
   */
  public Set<SearchFilter> getFilterComponents()
  {
    return filterComponents;
  }



  /**
   * Retrieves the filter component for this NOT filter.
   *
   * @return  The filter component for this NOT filter, or
   *          <CODE>null</CODE> if this is not a NOT filter.
   */
  public SearchFilter getNotComponent()
  {
    return notComponent;
  }



  /**
   * Retrieves the attribute type for this filter.
   *
   * @return  The attribute type for this filter, or <CODE>null</CODE>
   *          if there is none.
   */
  public AttributeType getAttributeType()
  {
    return attributeType;
  }



  /**
   * Retrieves the assertion value for this filter.
   *
   * @return  The assertion value for this filter, or
   *          <CODE>null</CODE> if there is none.
   */
  public AttributeValue getAssertionValue()
  {
    return assertionValue;
  }



  /**
   * Retrieves the subInitial element for this substring filter.
   *
   * @return  The subInitial element for this substring filter, or
   *          <CODE>null</CODE> if there is none.
   */
  public ByteString getSubInitialElement()
  {
    return subInitialElement;
  }



  /**
   * Retrieves the set of subAny elements for this substring filter.
   * The returned list may be altered by the caller.
   *
   * @return  The set of subAny elements for this substring filter.
   */
  public List<ByteString> getSubAnyElements()
  {
    return subAnyElements;
  }



  /**
   * Retrieves the subFinal element for this substring filter.
   *
   * @return  The subFinal element for this substring filter.
   */
  public ByteString getSubFinalElement()
  {
    return subFinalElement;
  }



  /**
   * Retrieves the matching rule ID for this extensible matching
   * filter.
   *
   * @return  The matching rule ID for this extensible matching
   *          filter.
   */
  public String getMatchingRuleID()
  {
    return matchingRuleID;
  }



  /**
   * Retrieves the dnAttributes flag for this extensible matching
   * filter.
   *
   * @return  The dnAttributes flag for this extensible matching
   *          filter.
   */
  public boolean getDNAttributes()
  {
    return dnAttributes;
  }



  /**
   * Indicates whether this search filter matches the provided entry.
   *
   * @param  entry  The entry for which to make the determination.
   *
   * @return  <CODE>true</CODE> if this search filter matches the
   *          provided entry, or <CODE>false</CODE> if it does not.
   *
   * @throws  DirectoryException  If a problem is encountered during
   *                              processing.
   */
  public boolean matchesEntry(Entry entry)
         throws DirectoryException
  {
    ConditionResult result = matchesEntryInternal(this, entry, 0);
    switch (result)
    {
      case TRUE:
        return true;
      case FALSE:
      case UNDEFINED:
        return false;
      default:
        Message message = ERR_SEARCH_FILTER_INVALID_RESULT_TYPE.
            get(String.valueOf(entry.getDN()), toString(),
                String.valueOf(result));
        logError(message);
        return false;
    }
  }



  /**
   * Indicates whether the this filter matches the provided entry.
   *
   * @param  completeFilter  The complete filter being checked, of
   *                         which this filter may be a subset.
   * @param  entry           The entry for which to make the
   *                         determination.
   * @param  depth           The current depth of the evaluation,
   *                         which is used to prevent infinite
   *                         recursion due to highly nested filters
   *                         and eventually running out of stack
   *                         space.
   *
   * @return  <CODE>TRUE</CODE> if this filter matches the provided
   *          entry, <CODE>FALSE</CODE> if it does not, or
   *          <CODE>UNDEFINED</CODE> if the result is undefined.
   *
   * @throws  DirectoryException  If a problem is encountered during
   *                              processing.
   */
  private ConditionResult matchesEntryInternal(
                               SearchFilter completeFilter,
                               Entry entry, int depth)
          throws DirectoryException
  {
    switch (filterType)
    {
      case AND:
        if (filterComponents == null)
        {
          // The set of subcomponents was null.  This is not allowed.
          Message message =
              ERR_SEARCH_FILTER_COMPOUND_COMPONENTS_NULL.
                get(String.valueOf(entry.getDN()),
                    String.valueOf(completeFilter),
                    String.valueOf(filterType));
          throw new DirectoryException(
                         DirectoryServer.getServerErrorResultCode(),
                         message);
        }
        else if (filterComponents.isEmpty())
        {
          // An AND filter with no elements like "(&)" is specified as
          // "undefined" in RFC 2251, but is considered one of the
          // TRUE/FALSE filters in RFC 4526, in which case we should
          // always return true.
          if (debugEnabled())
          {
            TRACER.debugInfo("Returning TRUE for LDAP TRUE " +
                "filter (&)");
          }
          return ConditionResult.TRUE;
        }
        else
        {
          // We will have to evaluate one or more subcomponents.  In
          // this case, first check our depth to make sure we're not
          // nesting too deep.
          if (depth >= MAX_NESTED_FILTER_DEPTH)
          {
            Message message = ERR_SEARCH_FILTER_NESTED_TOO_DEEP.
                get(String.valueOf(entry.getDN()),
                    String.valueOf(completeFilter));
            throw new DirectoryException(
                           DirectoryServer.getServerErrorResultCode(),
                           message);
          }

          for (SearchFilter f : filterComponents)
          {
            ConditionResult result =
                 f.matchesEntryInternal(completeFilter, entry,
                                     depth+1);
            switch (result)
            {
              case TRUE:
                break;
              case FALSE:
                if (debugEnabled())
                {
                  TRACER.debugVerbose(
                      "Returning FALSE for AND component %s in " +
                      "filter %s for entry %s",
                               f, completeFilter, entry.getDN());
                }
                return result;
              case UNDEFINED:
                if (debugEnabled())
                {
                  TRACER.debugInfo(
                 "Undefined result for AND component %s in filter " +
                 "%s for entry %s", f, completeFilter, entry.getDN());
                }
                return result;
              default:
                Message message =
                    ERR_SEARCH_FILTER_INVALID_RESULT_TYPE.
                      get(String.valueOf(entry.getDN()),
                          String.valueOf(completeFilter),
                          String.valueOf(result));
                throw new
                     DirectoryException(
                          DirectoryServer.getServerErrorResultCode(),
                          message);
            }
          }

          // If we have gotten here, then all the components must have
          // matched.
          if (debugEnabled())
          {
            TRACER.debugVerbose(
                "Returning TRUE for AND component %s in filter %s " +
                "for entry %s", this, completeFilter, entry.getDN());
          }
          return ConditionResult.TRUE;
        }


      case OR:
        if (filterComponents == null)
        {
          // The set of subcomponents was null.  This is not allowed.
          Message message =
              ERR_SEARCH_FILTER_COMPOUND_COMPONENTS_NULL.
                get(String.valueOf(entry.getDN()),
                    String.valueOf(completeFilter),
                    String.valueOf(filterType));
          throw new DirectoryException(
                         DirectoryServer.getServerErrorResultCode(),
                         message);
        }
        else if (filterComponents.isEmpty())
        {
          // An OR filter with no elements like "(|)" is specified as
          // "undefined" in RFC 2251, but is considered one of the
          // TRUE/FALSE filters in RFC 4526, in which case we should
          // always return false.
          if (debugEnabled())
          {
            TRACER.debugInfo("Returning FALSE for LDAP FALSE " +
                "filter (|)");
          }
          return ConditionResult.FALSE;
        }
        else
        {
          // We will have to evaluate one or more subcomponents.  In
          // this case, first check our depth to make sure we're not
          // nesting too deep.
          if (depth >= MAX_NESTED_FILTER_DEPTH)
          {
            Message message = ERR_SEARCH_FILTER_NESTED_TOO_DEEP.
                get(String.valueOf(entry.getDN()),
                    String.valueOf(completeFilter));
            throw new DirectoryException(
                           DirectoryServer.getServerErrorResultCode(),
                           message);
          }

          ConditionResult result = ConditionResult.FALSE;
          for (SearchFilter f : filterComponents)
          {
            switch (f.matchesEntryInternal(completeFilter, entry,
                                   depth+1))
            {
              case TRUE:
                if (debugEnabled())
                {
                  TRACER.debugVerbose(
                    "Returning TRUE for OR component %s in filter " +
                    "%s for entry %s",
                    f, completeFilter, entry.getDN());
                }
                return ConditionResult.TRUE;
              case FALSE:
                break;
              case UNDEFINED:
                if (debugEnabled())
                {
                  TRACER.debugInfo(
                  "Undefined result for OR component %s in filter " +
                  "%s for entry %s",
                  f, completeFilter, entry.getDN());
                }
                result = ConditionResult.UNDEFINED;
                break;
              default:
                Message message =
                    ERR_SEARCH_FILTER_INVALID_RESULT_TYPE.
                      get(String.valueOf(entry.getDN()),
                          String.valueOf(completeFilter),
                          String.valueOf(result));
                throw new
                     DirectoryException(
                          DirectoryServer.getServerErrorResultCode(),
                          message);
            }
          }


          if (debugEnabled())
          {
            TRACER.debugVerbose(
                "Returning %s for OR component %s in filter %s for " +
                "entry %s", result, this, completeFilter,
                            entry.getDN());
          }
          return result;
        }


      case NOT:
        if (notComponent == null)
        {
          // The NOT subcomponent was null.  This is not allowed.
          Message message = ERR_SEARCH_FILTER_NOT_COMPONENT_NULL.
              get(String.valueOf(entry.getDN()),
                  String.valueOf(completeFilter));
          throw new DirectoryException(
                         DirectoryServer.getServerErrorResultCode(),
                         message);
        }
        else
        {
          // The subcomponent for the NOT filter can be an AND, OR, or
          // NOT filter that would require more nesting.  Make sure
          // that we don't go too deep.
          if (depth >= MAX_NESTED_FILTER_DEPTH)
          {
            Message message = ERR_SEARCH_FILTER_NESTED_TOO_DEEP.
                get(String.valueOf(entry.getDN()),
                    String.valueOf(completeFilter));
            throw new DirectoryException(
                           DirectoryServer.getServerErrorResultCode(),
                           message);
          }

          ConditionResult result =
               notComponent.matchesEntryInternal(completeFilter,
                                                 entry, depth+1);
          switch (result)
          {
            case TRUE:
              if (debugEnabled())
              {
                TRACER.debugVerbose(
                   "Returning FALSE for NOT component %s in filter " +
                   "%s for entry %s",
                   notComponent, completeFilter, entry.getDN());
              }
              return ConditionResult.FALSE;
            case FALSE:
              if (debugEnabled())
              {
                TRACER.debugVerbose(
                    "Returning TRUE for NOT component %s in filter " +
                    "%s for entry %s",
                    notComponent, completeFilter, entry.getDN());
              }
              return ConditionResult.TRUE;
            case UNDEFINED:
              if (debugEnabled())
              {
                TRACER.debugInfo(
                  "Undefined result for NOT component %s in filter " +
                  "%s for entry %s",
                  notComponent, completeFilter, entry.getDN());
              }
              return ConditionResult.UNDEFINED;
            default:
              Message message = ERR_SEARCH_FILTER_INVALID_RESULT_TYPE.
                  get(String.valueOf(entry.getDN()),
                      String.valueOf(completeFilter),
                      String.valueOf(result));
              throw new
                   DirectoryException(
                        DirectoryServer.getServerErrorResultCode(),
                        message);
          }
        }


      case EQUALITY:
        // Make sure that an attribute type has been defined.
        if (attributeType == null)
        {
          Message message =
              ERR_SEARCH_FILTER_EQUALITY_NO_ATTRIBUTE_TYPE.
                get(String.valueOf(entry.getDN()), toString());
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                       message);
        }

        // Make sure that an assertion value has been defined.
        if (assertionValue == null)
        {
          Message message =
              ERR_SEARCH_FILTER_EQUALITY_NO_ASSERTION_VALUE.
                get(String.valueOf(entry.getDN()), toString(),
                    attributeType.getNameOrOID());
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                       message);
        }

        // See if the entry has an attribute with the requested type.
        List<Attribute> attrs = entry.getAttribute(attributeType,
                                                   attributeOptions);
        if ((attrs == null) || (attrs.isEmpty()))
        {
          if (debugEnabled())
          {
            TRACER.debugVerbose(
                "Returning FALSE for equality component %s in " +
                "filter %s because entry %s didn't have attribute " +
                "type %s",
                         this, completeFilter, entry.getDN(),
                         attributeType.getNameOrOID());
          }
          return ConditionResult.FALSE;
        }

        // Iterate through all the attributes and see if we can find a
        // match.
        for (Attribute a : attrs)
        {
          if (a.hasValue(assertionValue))
          {
            if (debugEnabled())
            {
              TRACER.debugVerbose(
                  "Returning TRUE for equality component %s in " +
                  "filter %s for entry %s",
                           this, completeFilter, entry.getDN());
            }
            return ConditionResult.TRUE;
          }
        }

        if (debugEnabled())
        {
          TRACER.debugVerbose(
              "Returning FALSE for equality component %s in filter " +
              "%s because entry %s didn't have attribute type " +
              "%s with value %s",
                       this, completeFilter, entry.getDN(),
                       attributeType.getNameOrOID(),
                       assertionValue.getStringValue());
        }
        return ConditionResult.FALSE;


      case SUBSTRING:
        // Make sure that an attribute type has been defined.
        if (attributeType == null)
        {
          Message message =
              ERR_SEARCH_FILTER_SUBSTRING_NO_ATTRIBUTE_TYPE.
                get(String.valueOf(entry.getDN()), toString());
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                       message);
        }

        // Make sure that at least one substring element has been
        // defined.
        if ((subInitialElement == null) &&
            (subFinalElement == null) &&
            ((subAnyElements == null) || subAnyElements.isEmpty()))
        {
          Message message =
              ERR_SEARCH_FILTER_SUBSTRING_NO_SUBSTRING_COMPONENTS.
                get(String.valueOf(entry.getDN()), toString(),
                    attributeType.getNameOrOID());
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                       message);
        }

        // See if the entry has an attribute with the requested type.
        attrs = entry.getAttribute(attributeType, attributeOptions);
        if ((attrs == null) || (attrs.isEmpty()))
        {
          if (debugEnabled())
          {
            TRACER.debugVerbose(
                "Returning FALSE for substring component %s in " +
                "filter %s because entry %s didn't have attribute " +
                "type %s",
                         this, completeFilter, entry.getDN(),
                         attributeType.getNameOrOID());
          }
          return ConditionResult.FALSE;
        }

        // Iterate through all the attributes and see if we can find a
        // match.
        ConditionResult result = ConditionResult.FALSE;
        for (Attribute a : attrs)
        {
          switch (a.matchesSubstring(subInitialElement,
                                     subAnyElements,
                                     subFinalElement))
          {
            case TRUE:
              if (debugEnabled())
              {
                TRACER.debugVerbose(
                    "Returning TRUE for substring component %s in " +
                    "filter %s for entry %s",
                             this, completeFilter, entry.getDN());
              }
              return ConditionResult.TRUE;
            case FALSE:
              break;
            case UNDEFINED:
              if (debugEnabled())
              {
                TRACER.debugVerbose(
                    "Undefined result encountered for substring " +
                    "component %s in filter %s for entry %s",
                             this, completeFilter, entry.getDN());
              }
              result = ConditionResult.UNDEFINED;
              break;
            default:
          }
        }

        if (debugEnabled())
        {
          TRACER.debugVerbose(
              "Returning %s for substring component %s in filter " +
              "%s for entry %s",
              result, this, completeFilter, entry.getDN());
        }
        return result;


      case GREATER_OR_EQUAL:
        // Make sure that an attribute type has been defined.
        if (attributeType == null)
        {
          Message message =
              ERR_SEARCH_FILTER_GREATER_OR_EQUAL_NO_ATTRIBUTE_TYPE.
                get(String.valueOf(entry.getDN()), toString());
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                       message);
        }

        // Make sure that an assertion value has been defined.
        if (assertionValue == null)
        {
          Message message =
              ERR_SEARCH_FILTER_GREATER_OR_EQUAL_NO_VALUE.
                get(String.valueOf(entry.getDN()), toString(),
                    attributeType.getNameOrOID());
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                       message);
        }

        // See if the entry has an attribute with the requested type.
        attrs = entry.getAttribute(attributeType, attributeOptions);
        if ((attrs == null) || (attrs.isEmpty()))
        {
          if (debugEnabled())
          {
            TRACER.debugVerbose("Returning FALSE for " +
                "greater-or-equal component %s in filter %s " +
                "because entry %s didn't have attribute type %s",
                         this, completeFilter, entry.getDN(),
                         attributeType.getNameOrOID());
          }
          return ConditionResult.FALSE;
        }

        // Iterate through all the attributes and see if we can find a
        // match.
        result = ConditionResult.FALSE;
        for (Attribute a : attrs)
        {
          switch (a.greaterThanOrEqualTo(assertionValue))
          {
            case TRUE:
              if (debugEnabled())
              {
                TRACER.debugVerbose(
                    "Returning TRUE for greater-or-equal component " +
                    "%s in filter %s for entry %s",
                             this, completeFilter, entry.getDN());
              }
              return ConditionResult.TRUE;
            case FALSE:
              break;
            case UNDEFINED:
              if (debugEnabled())
              {
                TRACER.debugVerbose(
                    "Undefined result encountered for " +
                    "greater-or-equal component %s in filter %s " +
                    "for entry %s", this, completeFilter,
                    entry.getDN());
              }
              result = ConditionResult.UNDEFINED;
              break;
            default:
          }
        }

        if (debugEnabled())
        {
          TRACER.debugVerbose(
              "Returning %s for greater-or-equal component %s in " +
              "filter %s for entry %s",
                       result, this, completeFilter, entry.getDN());
        }
        return result;


      case LESS_OR_EQUAL:
        // Make sure that an attribute type has been defined.
        if (attributeType == null)
        {
          Message message =
              ERR_SEARCH_FILTER_LESS_OR_EQUAL_NO_ATTRIBUTE_TYPE.
                get(String.valueOf(entry.getDN()), toString());
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                       message);
        }

        // Make sure that an assertion value has been defined.
        if (assertionValue == null)
        {
          Message message =
              ERR_SEARCH_FILTER_LESS_OR_EQUAL_NO_ASSERTION_VALUE.
                get(String.valueOf(entry.getDN()), toString(),
                    attributeType.getNameOrOID());
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                       message);
        }

        // See if the entry has an attribute with the requested type.
        attrs = entry.getAttribute(attributeType, attributeOptions);
        if ((attrs == null) || (attrs.isEmpty()))
        {
          if (debugEnabled())
          {
            TRACER.debugVerbose(
                "Returning FALSE for less-or-equal component %s in " +
                "filter %s because entry %s didn't have attribute " +
                "type %s", this, completeFilter, entry.getDN(),
                           attributeType.getNameOrOID());
          }
          return ConditionResult.FALSE;
        }

        // Iterate through all the attributes and see if we can find a
        // match.
        result = ConditionResult.FALSE;
        for (Attribute a : attrs)
        {
          switch (a.lessThanOrEqualTo(assertionValue))
          {
            case TRUE:
              if (debugEnabled())
              {
                TRACER.debugVerbose(
                    "Returning TRUE for less-or-equal component %s " +
                    "in filter %s for entry %s",
                             this, completeFilter, entry.getDN());
              }
              return ConditionResult.TRUE;
            case FALSE:
              break;
            case UNDEFINED:
              if (debugEnabled())
              {
                TRACER.debugVerbose(
                    "Undefined result encountered for " +
                        "less-or-equal component %s in filter %s " +
                        "for entry %s",
                        this, completeFilter, entry.getDN());
              }
              result = ConditionResult.UNDEFINED;
              break;
            default:
          }
        }

        if (debugEnabled())
        {
          TRACER.debugVerbose(
              "Returning %s for less-or-equal component %s in " +
              "filter %s for entry %s",
                       result, this, completeFilter, entry.getDN());
        }
        return result;


      case PRESENT:
        // Make sure that an attribute type has been defined.
        if (attributeType == null)
        {
          Message message =
              ERR_SEARCH_FILTER_PRESENCE_NO_ATTRIBUTE_TYPE.
                get(String.valueOf(entry.getDN()), toString());
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                       message);
        }


        // See if the entry has an attribute with the requested type.
        // If so, then it's a match.  If not, then it's not a match.
        if (entry.hasAttribute(attributeType, attributeOptions))
        {
          if (debugEnabled())
          {
            TRACER.debugVerbose(
                "Returning TRUE for presence component %s in " +
                "filter %s for entry %s",
                this, completeFilter, entry.getDN());
          }
          return ConditionResult.TRUE;
        }
        else
        {
          if (debugEnabled())
          {
            TRACER.debugVerbose(
                "Returning FALSE for presence component %s in " +
                "filter %s for entry %s",
                this, completeFilter, entry.getDN());
          }
          return ConditionResult.FALSE;
        }


      case APPROXIMATE_MATCH:
        // Make sure that an attribute type has been defined.
        if (attributeType == null)
        {
          Message message =
              ERR_SEARCH_FILTER_APPROXIMATE_NO_ATTRIBUTE_TYPE.
                get(String.valueOf(entry.getDN()), toString());
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                       message);
        }

        // Make sure that an assertion value has been defined.
        if (assertionValue == null)
        {
          Message message =
              ERR_SEARCH_FILTER_APPROXIMATE_NO_ASSERTION_VALUE.
                get(String.valueOf(entry.getDN()), toString(),
                    attributeType.getNameOrOID());
          throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                       message);
        }

        // See if the entry has an attribute with the requested type.
        attrs = entry.getAttribute(attributeType, attributeOptions);
        if ((attrs == null) || (attrs.isEmpty()))
        {
          if (debugEnabled())
          {
            TRACER.debugVerbose(
                "Returning FALSE for approximate component %s in " +
                "filter %s because entry %s didn't have attribute " +
                "type %s", this, completeFilter, entry.getDN(),
                           attributeType.getNameOrOID());
          }
          return ConditionResult.FALSE;
        }

        // Iterate through all the attributes and see if we can find a
        // match.
        result = ConditionResult.FALSE;
        for (Attribute a : attrs)
        {
          switch (a.approximatelyEqualTo(assertionValue))
          {
            case TRUE:
              if (debugEnabled())
              {
                TRACER.debugVerbose(
                   "Returning TRUE for approximate component %s in " +
                   "filter %s for entry %s",
                   this, completeFilter, entry.getDN());
              }
              return ConditionResult.TRUE;
            case FALSE:
              break;
            case UNDEFINED:
              if (debugEnabled())
              {
                TRACER.debugVerbose(
                    "Undefined result encountered for approximate " +
                    "component %s in filter %s for entry %s",
                             this, completeFilter, entry.getDN());
              }
              result = ConditionResult.UNDEFINED;
              break;
            default:
          }
        }

        if (debugEnabled())
        {
          TRACER.debugVerbose(
              "Returning %s for approximate component %s in filter " +
              "%s for entry %s",
              result, this, completeFilter, entry.getDN());
        }
        return result;


      case EXTENSIBLE_MATCH:
        return processExtensibleMatch(completeFilter, entry);


      default:
        // This is an invalid filter type.
        Message message = ERR_SEARCH_FILTER_INVALID_FILTER_TYPE.
            get(String.valueOf(entry.getDN()), toString(),
                filterType.toString());
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                     message);
    }
  }



  /**
   * Indicates whether this extensibleMatch filter matches the
   * provided entry.
   *
   * @param  completeFilter  The complete filter in which this
   *                         extensibleMatch filter may be a
   *                         subcomponent.
   * @param  entry           The entry for which to make the
   *                         determination.
   *
   * @return <CODE>TRUE</CODE> if this extensibleMatch filter matches
   *         the provided entry, <CODE>FALSE</CODE> if it does not, or
   *         <CODE>UNDEFINED</CODE> if the result cannot be
   *         determined.
   *
   * @throws  DirectoryException  If a problem occurs while evaluating
   *                              this filter against the provided
   *                              entry.
   */
  private ConditionResult processExtensibleMatch(
                               SearchFilter completeFilter,
                               Entry entry)
          throws DirectoryException
  {
    // We must have an assertion value for which to make the
    // determination.
    if (assertionValue == null)
    {
      Message message =
          ERR_SEARCH_FILTER_EXTENSIBLE_MATCH_NO_ASSERTION_VALUE.
            get(String.valueOf(entry.getDN()),
                String.valueOf(completeFilter));
      throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                   message);
    }


    // We must have a matching rule to use in the determination.
    MatchingRule matchingRule = null;
    if (matchingRuleID != null)
    {
      matchingRule =
           DirectoryServer.getMatchingRule(
                toLowerCase(matchingRuleID));
      if (matchingRule == null)
      {
        if (debugEnabled())
        {
          TRACER.debugInfo(
              "Unknown matching rule %s defined in extensibleMatch " +
              "component of filter %s -- returning undefined.",
                    matchingRuleID, this);
        }
        return ConditionResult.UNDEFINED;
      }
    }
    else
    {
      if (attributeType == null)
      {
        Message message =
            ERR_SEARCH_FILTER_EXTENSIBLE_MATCH_NO_RULE_OR_TYPE.
              get(String.valueOf(entry.getDN()),
                  String.valueOf(completeFilter));
        throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                     message);
      }
      else
      {
        matchingRule = attributeType.getEqualityMatchingRule();
        if (matchingRule == null)
        {
          if (debugEnabled())
          {
            TRACER.debugInfo(
             "Attribute type %s does not have an equality matching " +
             "rule -- returning undefined.",
             attributeType.getNameOrOID());
          }
          return ConditionResult.UNDEFINED;
        }
      }
    }


    // If there is an attribute type, then check to see if there is a
    // corresponding matching rule use for the matching rule and
    // determine if it allows that attribute type.
    if (attributeType != null)
    {
      MatchingRuleUse mru =
           DirectoryServer.getMatchingRuleUse(matchingRule);
      if (mru != null)
      {
        if (! mru.appliesToAttribute(attributeType))
        {
          if (debugEnabled())
          {
            TRACER.debugInfo(
                "Attribute type %s is not allowed for use with " +
                "matching rule %s because of matching rule use " +
                "definition %s", attributeType.getNameOrOID(),
                matchingRule.getNameOrOID(), mru.getName());
          }
          return ConditionResult.UNDEFINED;
        }
      }
    }


    // Normalize the assertion value using the matching rule.
    ByteString normalizedValue;
    try
    {
      normalizedValue =
           matchingRule.normalizeValue(assertionValue.getValue());
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      // We can't normalize the assertion value, so the result must be
      // undefined.
      return ConditionResult.UNDEFINED;
    }


    // If there is an attribute type, then we should only check for
    // that attribute.  Otherwise, we should check against all
    // attributes in the entry.
    ConditionResult result = ConditionResult.FALSE;
    if (attributeType == null)
    {
      for (List<Attribute> attrList :
           entry.getUserAttributes().values())
      {
        for (Attribute a : attrList)
        {
          for (AttributeValue v : a.getValues())
          {
            try
            {
              ByteString nv =
                   matchingRule.normalizeValue(v.getValue());
              ConditionResult r =
                   matchingRule.valuesMatch(nv, normalizedValue);
              switch (r)
              {
                case TRUE:
                  return ConditionResult.TRUE;
                case FALSE:
                  break;
                case UNDEFINED:
                  result = ConditionResult.UNDEFINED;
                  break;
                default:
                  Message message =
                      ERR_SEARCH_FILTER_INVALID_RESULT_TYPE.
                        get(String.valueOf(entry.getDN()),
                            String.valueOf(completeFilter),
                            String.valueOf(r));
                  throw new DirectoryException(
                                 ResultCode.PROTOCOL_ERROR, message);
              }
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }

              // We couldn't normalize one of the values.  If we don't
              // find a definite match, then we should return
              // undefined.
              result = ConditionResult.UNDEFINED;
            }
          }
        }
      }

      for (List<Attribute> attrList :
           entry.getOperationalAttributes().values())
      {
        for (Attribute a : attrList)
        {
          for (AttributeValue v : a.getValues())
          {
            try
            {
              ByteString nv =
                   matchingRule.normalizeValue(v.getValue());
              ConditionResult r =
                   matchingRule.valuesMatch(nv, normalizedValue);
              switch (r)
              {
                case TRUE:
                  return ConditionResult.TRUE;
                case FALSE:
                  break;
                case UNDEFINED:
                  result = ConditionResult.UNDEFINED;
                  break;
                default:
                  Message message =
                      ERR_SEARCH_FILTER_INVALID_RESULT_TYPE.
                        get(String.valueOf(entry.getDN()),
                            String.valueOf(completeFilter),
                            String.valueOf(r));
                  throw new DirectoryException(
                                 ResultCode.PROTOCOL_ERROR, message);
              }
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }

              // We couldn't normalize one of the values.  If we don't
              // find a definite match, then we should return
              // undefined.
              result = ConditionResult.UNDEFINED;
            }
          }
        }
      }

      Attribute a = entry.getObjectClassAttribute();
      for (AttributeValue v : a.getValues())
      {
        try
        {
          ByteString nv = matchingRule.normalizeValue(v.getValue());
          ConditionResult r =
               matchingRule.valuesMatch(nv, normalizedValue);
          switch (r)
          {
            case TRUE:
              return ConditionResult.TRUE;
            case FALSE:
              break;
            case UNDEFINED:
              result = ConditionResult.UNDEFINED;
              break;
            default:
              Message message = ERR_SEARCH_FILTER_INVALID_RESULT_TYPE.
                  get(String.valueOf(entry.getDN()),
                      String.valueOf(completeFilter),
                      String.valueOf(r));
              throw new DirectoryException(ResultCode.PROTOCOL_ERROR,
                                           message);
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          // We couldn't normalize one of the values.  If we don't
          // find a definite match, then we should return undefined.
          result = ConditionResult.UNDEFINED;
        }
      }
    }
    else
    {
      List<Attribute> attrList = entry.getAttribute(attributeType,
                                                    attributeOptions);
      if (attrList != null)
      {
        for (Attribute a : attrList)
        {
          for (AttributeValue v : a.getValues())
          {
            try
            {
              ByteString nv =
                   matchingRule.normalizeValue(v.getValue());
              ConditionResult r =
                   matchingRule.valuesMatch(nv, normalizedValue);
              switch (r)
              {
                case TRUE:
                  return ConditionResult.TRUE;
                case FALSE:
                  break;
                case UNDEFINED:
                  result = ConditionResult.UNDEFINED;
                  break;
                default:
                  Message message =
                      ERR_SEARCH_FILTER_INVALID_RESULT_TYPE.
                        get(String.valueOf(entry.getDN()),
                            String.valueOf(completeFilter),
                            String.valueOf(r));
                  throw new DirectoryException(
                                 ResultCode.PROTOCOL_ERROR, message);
              }
            }
            catch (Exception e)
            {
              if (debugEnabled())
              {
                TRACER.debugCaught(DebugLogLevel.ERROR, e);
              }

              // We couldn't normalize one of the values.  If we don't
              // find a definite match, then we should return
              // undefined.
              result = ConditionResult.UNDEFINED;
            }
          }
        }
      }
    }


    // If we've gotten here, then we know that there is no definite
    // match in the set of attributes.  If we should check DN
    // attributes, then do so.
    if (dnAttributes)
    {
      DN entryDN = entry.getDN();
      int count = entryDN.getNumComponents();
      for (int rdnIndex = 0; rdnIndex < count; rdnIndex++)
      {
        RDN rdn = entryDN.getRDN(rdnIndex);
        int numAVAs = rdn.getNumValues();
        for (int i=0; i < numAVAs; i++)
        {
          try
          {
            if ((attributeType == null) ||
                attributeType.equals(rdn.getAttributeType(i)))
            {

              AttributeValue v = rdn.getAttributeValue(i);
              ByteString nv =
                   matchingRule.normalizeValue(v.getValue());
              ConditionResult r =
                   matchingRule.valuesMatch(nv, normalizedValue);
              switch (r)
              {
                case TRUE:
                  return ConditionResult.TRUE;
                case FALSE:
                  break;
                case UNDEFINED:
                  result = ConditionResult.UNDEFINED;
                  break;
                default:
                  Message message =
                      ERR_SEARCH_FILTER_INVALID_RESULT_TYPE.
                        get(String.valueOf(entry.getDN()),
                            String.valueOf(completeFilter),
                            String.valueOf(r));
                  throw new DirectoryException(
                                 ResultCode.PROTOCOL_ERROR, message);
              }
            }
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }

            // We couldn't normalize one of the values.  If we don't
            // find a definite match, then we should return undefined.
            result = ConditionResult.UNDEFINED;
          }
        }
      }
    }


    // If we've gotten here, then there is no definitive match, so
    // we'll either return FALSE or UNDEFINED.
    return result;
  }


  /**
   * Indicates whether this search filter is equal to the provided
   * object.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provide object is equal to this
   *          search filter, or <CODE>false</CODE> if it is not.
   */
  public boolean equals(Object o)
  {
    if (o == null)
    {
      return false;
    }

    if (o == this)
    {
      return true;
    }

    if (! (o instanceof SearchFilter))
    {
      return false;
    }


    SearchFilter f = (SearchFilter) o;
    if (filterType != f.filterType)
    {
      return false;
    }


    switch (filterType)
    {
      case AND:
      case OR:
        if (filterComponents.size() != f.filterComponents.size())
        {
          return false;
        }

outerComponentLoop:
        for (SearchFilter outerFilter : filterComponents)
        {
          for (SearchFilter innerFilter : f.filterComponents)
          {
            if (outerFilter.equals(innerFilter))
            {
              continue outerComponentLoop;
            }
          }

          return false;
        }

        return true;
      case NOT:
        return notComponent.equals(f.notComponent);
      case EQUALITY:
        return (attributeType.equals(f.attributeType) &&
                optionsEqual(attributeOptions, f.attributeOptions) &&
                assertionValue.equals(f.assertionValue));
      case SUBSTRING:
        if (! attributeType.equals(f.attributeType))
        {
          return false;
        }

        SubstringMatchingRule smr =
             attributeType.getSubstringMatchingRule();
        if (smr == null)
        {
          return false;
        }

        if (! optionsEqual(attributeOptions, f.attributeOptions))
        {
          return false;
        }

        if (subInitialElement == null)
        {
          if (f.subInitialElement != null)
          {
            return false;
          }
        }
        else
        {
          try
          {
            ByteString nSI1 =
                 smr.normalizeSubstring(subInitialElement);
            ByteString nSI2 =
                 smr.normalizeSubstring(f.subInitialElement);

            if (! Arrays.equals(nSI1.value(), nSI2.value()))
            {
              return false;
            }
          }
          catch (Exception e)
          {
            return false;
          }
        }

        if (subFinalElement == null)
        {
          if (f.subFinalElement != null)
          {
            return false;
          }
        }
        else
        {
          try
          {
            ByteString nSF1 =
                 smr.normalizeSubstring(subFinalElement);
            ByteString nSF2 =
                 smr.normalizeSubstring(f.subFinalElement);

            if (! Arrays.equals(nSF1.value(), nSF2.value()))
            {
              return false;
            }
          }
          catch (Exception e)
          {
            return false;
          }
        }

        if (subAnyElements.size() != f.subAnyElements.size())
        {
          return false;
        }

        for (int i = 0; i < subAnyElements.size(); i++)
        {
          try
          {
            ByteString nSA1 =
                 smr.normalizeSubstring(subAnyElements.get(i));
            ByteString nSA2 =
                 smr.normalizeSubstring(f.subAnyElements.get(i));

            if (! Arrays.equals(nSA1.value(), nSA2.value()))
            {
              return false;
            }
          }
          catch (Exception e)
          {
            return false;
          }
        }

        return true;
      case GREATER_OR_EQUAL:
        return (attributeType.equals(f.attributeType) &&
                optionsEqual(attributeOptions, f.attributeOptions) &&
                assertionValue.equals(f.assertionValue));
      case LESS_OR_EQUAL:
        return (attributeType.equals(f.attributeType) &&
                optionsEqual(attributeOptions, f.attributeOptions) &&
                assertionValue.equals(f.assertionValue));
      case PRESENT:
        return (attributeType.equals(f.attributeType) &&
                optionsEqual(attributeOptions, f.attributeOptions));
      case APPROXIMATE_MATCH:
        return (attributeType.equals(f.attributeType) &&
                optionsEqual(attributeOptions, f.attributeOptions) &&
                assertionValue.equals(f.assertionValue));
      case EXTENSIBLE_MATCH:
        if (attributeType == null)
        {
          if (f.attributeType != null)
          {
            return false;
          }
        }
        else
        {
          if (! attributeType.equals(f.attributeType))
          {
            return false;
          }

          if (! optionsEqual(attributeOptions, f.attributeOptions))
          {
            return false;
          }
        }

        if (dnAttributes != f.dnAttributes)
        {
          return false;
        }

        if (matchingRuleID == null)
        {
          if (f.matchingRuleID != null)
          {
            return false;
          }
        }
        else
        {
          if (! matchingRuleID.equals(f.matchingRuleID))
          {
            return false;
          }
        }

        if (assertionValue == null)
        {
          if (f.assertionValue != null)
          {
            return false;
          }
        }
        else
        {
          if (matchingRuleID == null)
          {
            if (! assertionValue.equals(f.assertionValue))
            {
              return false;
            }
          }
          else
          {
            MatchingRule mr =
                 DirectoryServer.getMatchingRule(
                      toLowerCase(matchingRuleID));
            if (mr == null)
            {
              return false;
            }
            else
            {
              try
              {
                ConditionResult cr = mr.valuesMatch(
                     mr.normalizeValue(assertionValue.getValue()),
                     mr.normalizeValue(f.assertionValue.getValue()));
                if (cr != ConditionResult.TRUE)
                {
                  return false;
                }
              }
              catch (Exception e)
              {
                return false;
              }
            }
          }
        }

        return true;
      default:
        return false;
    }
  }



  /**
   * Indicates whether the two provided sets of attribute options
   * should be considered equal.
   *
   * @param  options1  The first set of attribute options for which to
   *                   make the determination.
   * @param  options2  The second set of attribute options for which
   *                   to make the determination.
   *
   * @return  {@code true} if the sets of attribute options are equal,
   *          or {@code false} if not.
   */
  private static boolean optionsEqual(Set<String> options1,
                                      Set<String> options2)
  {
    if ((options1 == null) || options1.isEmpty())
    {
      return ((options2 == null) || options2.isEmpty());
    }
    else if ((options2 == null) || options2.isEmpty())
    {
      return false;
    }
    else
    {
      if (options1.size() != options2.size())
      {
        return false;
      }

      HashSet<String> lowerOptions =
           new HashSet<String>(options1.size());
      for (String option : options1)
      {
        lowerOptions.add(toLowerCase(option));
      }

      for (String option : options2)
      {
        if (! lowerOptions.remove(toLowerCase(option)))
        {
          return false;
        }
      }

      return lowerOptions.isEmpty();
    }
  }


  /**
   * Retrieves the hash code for this search filter.
   *
   * @return  The hash code for this search filter.
   */
  public int hashCode()
  {
    switch (filterType)
    {
      case AND:
      case OR:
        int hashCode = 0;

        for (SearchFilter filterComp : filterComponents)
        {
          hashCode += filterComp.hashCode();
        }

        return hashCode;
      case NOT:
        return notComponent.hashCode();
      case EQUALITY:
        return (attributeType.hashCode() + assertionValue.hashCode());
      case SUBSTRING:
        hashCode = attributeType.hashCode();

        SubstringMatchingRule smr =
             attributeType.getSubstringMatchingRule();

        if (subInitialElement != null)
        {
          if (smr == null)
          {
            hashCode += subInitialElement.hashCode();
          }
          else
          {
            try
            {
              hashCode += smr.normalizeSubstring(
                               subInitialElement).hashCode();
            }
            catch (Exception e) {}
          }
        }

        if (subAnyElements != null)
        {
          for (ByteString e : subAnyElements)
          {
            if (smr == null)
            {
              hashCode += e.hashCode();
            }
            else
            {
              try
              {
                hashCode += smr.normalizeSubstring(e).hashCode();
              }
              catch (Exception e2) {}
            }
          }
        }

        if (subFinalElement != null)
        {
          if (smr == null)
          {
            hashCode += subFinalElement.hashCode();
          }
          else
          {
            try
            {
              hashCode +=
                   smr.normalizeSubstring(subFinalElement).hashCode();
            }
            catch (Exception e) {}
          }
        }

        return hashCode;
      case GREATER_OR_EQUAL:
        return (attributeType.hashCode() + assertionValue.hashCode());
      case LESS_OR_EQUAL:
        return (attributeType.hashCode() + assertionValue.hashCode());
      case PRESENT:
        return attributeType.hashCode();
      case APPROXIMATE_MATCH:
        return (attributeType.hashCode() + assertionValue.hashCode());
      case EXTENSIBLE_MATCH:
        hashCode = 0;

        if (attributeType != null)
        {
          hashCode += attributeType.hashCode();
        }

        if (dnAttributes)
        {
          hashCode++;
        }

        if (matchingRuleID != null)
        {
          hashCode += matchingRuleID.hashCode();
        }

        if (assertionValue != null)
        {
          hashCode += assertionValue.hashCode();
        }

        return hashCode;
      default:
        return 1;
    }
  }



  /**
   * Retrieves a string representation of this search filter.
   *
   * @return  A string representation of this search filter.
   */
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this search filter to the
   * provided buffer.
   *
   * @param  buffer  The buffer to which the information should be
   *                 appended.
   */
  public void toString(StringBuilder buffer)
  {
    switch (filterType)
    {
      case AND:
        buffer.append("(&");
        for (SearchFilter f : filterComponents)
        {
          f.toString(buffer);
        }
        buffer.append(")");
        break;
      case OR:
        buffer.append("(|");
        for (SearchFilter f : filterComponents)
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
        buffer.append(attributeType.getNameOrOID());

        if ((attributeOptions != null) &&
            (! attributeOptions.isEmpty()))
        {
          for (String option : attributeOptions)
          {
            buffer.append(";");
            buffer.append(option);
          }
        }

        buffer.append("=");
        valueToFilterString(buffer, assertionValue.getValue());
        buffer.append(")");
        break;
      case SUBSTRING:
        buffer.append("(");
        buffer.append(attributeType.getNameOrOID());

        if ((attributeOptions != null) &&
            (! attributeOptions.isEmpty()))
        {
          for (String option : attributeOptions)
          {
            buffer.append(";");
            buffer.append(option);
          }
        }

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
        buffer.append(attributeType.getNameOrOID());

        if ((attributeOptions != null) &&
            (! attributeOptions.isEmpty()))
        {
          for (String option : attributeOptions)
          {
            buffer.append(";");
            buffer.append(option);
          }
        }

        buffer.append(">=");
        valueToFilterString(buffer, assertionValue.getValue());
        buffer.append(")");
        break;
      case LESS_OR_EQUAL:
        buffer.append("(");
        buffer.append(attributeType.getNameOrOID());

        if ((attributeOptions != null) &&
            (! attributeOptions.isEmpty()))
        {
          for (String option : attributeOptions)
          {
            buffer.append(";");
            buffer.append(option);
          }
        }

        buffer.append("<=");
        valueToFilterString(buffer, assertionValue.getValue());
        buffer.append(")");
        break;
      case PRESENT:
        buffer.append("(");
        buffer.append(attributeType.getNameOrOID());

        if ((attributeOptions != null) &&
            (! attributeOptions.isEmpty()))
        {
          for (String option : attributeOptions)
          {
            buffer.append(";");
            buffer.append(option);
          }
        }

        buffer.append("=*)");
        break;
      case APPROXIMATE_MATCH:
        buffer.append("(");
        buffer.append(attributeType.getNameOrOID());

        if ((attributeOptions != null) &&
            (! attributeOptions.isEmpty()))
        {
          for (String option : attributeOptions)
          {
            buffer.append(";");
            buffer.append(option);
          }
        }

        buffer.append("~=");
        valueToFilterString(buffer, assertionValue.getValue());
        buffer.append(")");
        break;
      case EXTENSIBLE_MATCH:
        buffer.append("(");

        if (attributeType != null)
        {
          buffer.append(attributeType.getNameOrOID());

          if ((attributeOptions != null) &&
              (! attributeOptions.isEmpty()))
          {
            for (String option : attributeOptions)
            {
              buffer.append(";");
              buffer.append(option);
            }
          }
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
        valueToFilterString(buffer, assertionValue.getValue());
        buffer.append(")");
        break;
    }
  }



  /**
   * Appends a properly-cleaned version of the provided value to the
   * given buffer so that it can be safely used in string
   * representations of this search filter.  The formatting changes
   * that may be performed will be in compliance with the
   * specification in RFC 2254.
   *
   * @param  buffer  The buffer to which the "safe" version of the
   *                 value will be appended.
   * @param  value   The value to be appended to the buffer.
   */
  private void valueToFilterString(StringBuilder buffer,
                                   ByteString value)
  {
    if (value == null)
    {
      return;
    }


    // Get the binary representation of the value and iterate through
    // it to see if there are any unsafe characters.  If there are,
    // then escape them and replace them with a two-digit hex
    // equivalent.
    byte[] valueBytes = value.value();
    buffer.ensureCapacity(buffer.length() + valueBytes.length);
    for (byte b : valueBytes)
    {
      if (((b & 0x7F) != b) ||  // Not 7-bit clean
          (b <= 0x1F) ||        // Below the printable character range
          (b == 0x28) ||        // Open parenthesis
          (b == 0x29) ||        // Close parenthesis
          (b == 0x2A) ||        // Asterisk
          (b == 0x5C) ||        // Backslash
          (b == 0x7F))          // Delete character
      {
        buffer.append("\\");
        buffer.append(byteToHex(b));
      }
      else
      {
        buffer.append((char) b);
      }
    }
  }
}

