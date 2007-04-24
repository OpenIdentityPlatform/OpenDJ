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



import java.util.ArrayList;

import org.opends.server.protocols.asn1.ASN1Boolean;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.asn1.ASN1Set;
import org.opends.server.protocols.ldap.LDAPFilter;

import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the data structures and methods to use when
 * interacting with a raw search filter, which defines a set of
 * criteria for locating entries in a search request.
 */
public abstract class RawFilter
{
  /**
   * Creates a new LDAP filter from the provided filter string.
   *
   * @param  filterString  The filter string to use to create this raw
   *                       filter.
   *
   * @return  The raw filter decoded from the provided filter string.
   *
   * @throws  LDAPException  If the provied filter string could not be
   *                         decoded as a raw filter.
   */
  public static RawFilter create(String filterString)
         throws LDAPException
  {
    return LDAPFilter.decode(filterString);
  }



  /**
   * Creates a new LDAP filter from the provided search filter.
   *
   * @param  filter  The search filter to use to create this raw
   *                 filter.
   *
   * @return  The constructed raw filter.
   */
  public static RawFilter create(SearchFilter filter)
  {
    return new LDAPFilter(filter);
  }



  /**
   * Creates a new AND search filter with the provided filter
   * components.
   *
   * @param  filterComponents  The filter components for this AND
   *                           filter.
   *
   * @return  The AND search filter with the provided filter
   *          components.
   */
  public static LDAPFilter createANDFilter(ArrayList<RawFilter>
                                                filterComponents)
  {
    return new LDAPFilter(FilterType.AND, filterComponents, null,
                          null, null, null, null, null, null, false);
  }



  /**
   * Creates a new OR search filter with the provided filter
   * components.
   *
   * @param  filterComponents  The filter components for this OR
   *                           filter.
   *
   * @return  The OR search filter with the provided filter
   *          components.
   */
  public static LDAPFilter createORFilter(ArrayList<RawFilter>
                                               filterComponents)
  {
    return new LDAPFilter(FilterType.OR, filterComponents, null, null,
                          null, null, null, null, null, false);
  }



  /**
   * Creates a new NOT search filter with the provided filter
   * component.
   *
   * @param  notComponent  The filter component for this NOT filter.
   *
   * @return  The NOT search filter with the provided filter
   *          component.
   */
  public static LDAPFilter createNOTFilter(RawFilter notComponent)
  {
    return new LDAPFilter(FilterType.NOT, null, notComponent, null,
                          null, null, null, null, null, false);
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
   * @return  The constructed equality search filter.
   */
  public static LDAPFilter createEqualityFilter(String attributeType,
                                ByteString assertionValue)
  {
    return new LDAPFilter(FilterType.EQUALITY, null, null,
                          attributeType, assertionValue, null, null,
                          null, null, false);
  }



  /**
   * Creates a new substring search filter with the provided
   * information.
   *
   * @param  attributeType      The attribute type for this substring
   *                            filter.
   * @param  subInitialElement  The subInitial element for this
   *                            substring filter.
   * @param  subAnyElements     The subAny elements for this substring
   *                            filter.
   * @param  subFinalElement    The subFinal element for this
   *                            substring filter.
   *
   * @return  The constructed substring search filter.
   */
  public static LDAPFilter createSubstringFilter(String attributeType,
                                ByteString subInitialElement,
                                ArrayList<ByteString> subAnyElements,
                                ByteString subFinalElement)
  {
    return new LDAPFilter(FilterType.SUBSTRING, null, null,
                          attributeType, null, subInitialElement,
                          subAnyElements, subFinalElement, null,
                          false);
  }



  /**
   * Creates a new greater or equal search filter with the provided
   * information.
   *
   * @param  attributeType   The attribute type for this greater or
   *                         equal filter.
   * @param  assertionValue  The assertion value for this greater or
   *                         equal filter.
   *
   * @return  The constructed greater or equal search filter.
   */
  public static LDAPFilter createGreaterOrEqualFilter(
                                String attributeType,
                                ByteString assertionValue)
  {
    return new LDAPFilter(FilterType.GREATER_OR_EQUAL, null, null,
                          attributeType, assertionValue, null, null,
                          null, null, false);
  }



  /**
   * Creates a new less or equal search filter with the provided
   * information.
   *
   * @param  attributeType   The attribute type for this less or equal
   *                         filter.
   * @param  assertionValue  The assertion value for this less or
   *                         equal filter.
   *
   * @return  The constructed less or equal search filter.
   */
  public static LDAPFilter createLessOrEqualFilter(
                                String attributeType,
                                ByteString assertionValue)
  {
    return new LDAPFilter(FilterType.LESS_OR_EQUAL, null, null,
                          attributeType, assertionValue, null, null,
                          null, null, false);
  }



  /**
   * Creates a new presence search filter with the provided attribute
   * type.
   *
   * @param  attributeType  The attribute type for this presence
   *                        filter.
   *
   * @return  The constructed presence search filter.
   */
  public static LDAPFilter createPresenceFilter(String attributeType)
  {
    return new LDAPFilter(FilterType.PRESENT, null, null,
                          attributeType, null, null, null, null, null,
                          false);
  }



  /**
   * Creates a new approximate search filter with the provided
   * information.
   *
   * @param  attributeType   The attribute type for this approximate
   *                         filter.
   * @param  assertionValue  The assertion value for this approximate
   *                         filter.
   *
   * @return  The constructed approximate search filter.
   */
  public static LDAPFilter createApproximateFilter(
                                String attributeType,
                                ByteString assertionValue)
  {
    return new LDAPFilter(FilterType.APPROXIMATE_MATCH, null, null,
                          attributeType, assertionValue, null, null,
                          null, null, false);
  }



  /**
   * Creates a new extensible matching search filter with the provided
   * information.
   *
   * @param  matchingRuleID  The matching rule ID for this extensible
   *                         filter.
   * @param  attributeType   The attribute type for this extensible
   *                         filter.
   * @param  assertionValue  The assertion value for this extensible
   *                         filter.
   * @param  dnAttributes    The dnAttributes flag for this extensible
   *                         filter.
   *
   * @return  The constructed extensible matching search filter.
   */
  public static LDAPFilter createExtensibleFilter(
                                String matchingRuleID,
                                String attributeType,
                                ByteString assertionValue,
                                boolean dnAttributes)
  {
    return new LDAPFilter(FilterType.EXTENSIBLE_MATCH, null, null,
                          attributeType, assertionValue, null, null,
                          null, matchingRuleID, dnAttributes);
  }



  /**
   * Retrieves the filter type for this search filter.
   *
   * @return  The filter type for this search filter.
   */
  public abstract FilterType getFilterType();



  /**
   * Retrieves the set of subordinate filter components for AND or OR
   * searches.  The contents of the returned list may be altered by
   * the caller.
   *
   * @return  The set of subordinate filter components for AND and OR
   *          searches, or {@code null} if this is not an AND or OR
   *          search.
   */
  public abstract ArrayList<RawFilter> getFilterComponents();



  /**
   * Specifies the set of subordinate filter components for AND or OR
   * searches.  This will be ignored for all other filter types.
   *
   * @param  filterComponents  The set of subordinate filter
   *                           components for AND or OR searches.
   */
  public abstract void setFilterComponents(
                            ArrayList<RawFilter> filterComponents);



  /**
   * Retrieves the subordinate filter component for NOT searches.
   *
   * @return  The subordinate filter component for NOT searches, or
   *          {@code null} if this is not a NOT search.
   */
  public abstract RawFilter getNOTComponent();



  /**
   * Specifies the subordinate filter component for NOT searches.
   * This will be ignored for any other type of search.
   *
   * @param  notComponent  The subordinate filter component for NOT
   *                       searches.
   */
  public abstract void setNOTComponent(RawFilter notComponent);



  /**
   * Retrieves the attribute type for this search filter.  This will
   * not be applicable for AND, OR, or NOT filters.
   *
   * @return  The attribute type for this search filter, or
   *          {@code null} if there is none.
   */
  public abstract String getAttributeType();



  /**
   * Specifies the attribute type for this search filter.  This will
   * be ignored for AND, OR, and NOT searches.
   *
   * @param  attributeType  The attribute type for this search filter.
   */
  public abstract void setAttributeType(String attributeType);



  /**
   * Retrieves the assertion value for this search filter.  This will
   * only be applicable for equality, greater or equal, less or equal,
   * approximate, or extensible matching filters.
   *
   * @return  The assertion value for this search filter, or
   *          {@code null} if there is none.
   */
  public abstract ByteString getAssertionValue();



  /**
   * Specifies the assertion value for this search filter.  This will
   * be ignored for types of filters that do not have an assertion
   * value.
   *
   * @param  assertionValue  The assertion value for this search
   *                         filter.
   */
  public abstract void setAssertionValue(ByteString assertionValue);



  /**
   * Retrieves the subInitial component for this substring filter.
   * This is only applicable for substring search filters, but even
   * substring filters might not have a value for this component.
   *
   * @return  The subInitial component for this substring filter, or
   *          {@code null} if there is none.
   */
  public abstract ByteString getSubInitialElement();



  /**
   * Specifies the subInitial element for this substring filter.
   * This will be ignored for all other types of filters.
   *
   * @param  subInitialElement  The subInitial element for this
   *                            substring filter.
   */
  public abstract void setSubInitialElement(
                            ByteString subInitialElement);



  /**
   * Retrieves the set of subAny elements for this substring filter.
   * This is only applicable for substring search filters, and even
   * then may be {@code null} or empty for some substring filters.
   *
   * @return  The set of subAny elements for this substring filter, or
   *          {@code null} if there are none.
   */
  public abstract ArrayList<ByteString> getSubAnyElements();



  /**
   * Specifies the set of subAny values for this substring filter.
   * This will be ignored for other filter types.
   *
   * @param  subAnyElements  The set of subAny elements for this
   *                         substring filter.
   */
  public abstract void setSubAnyElements(ArrayList<ByteString>
                                              subAnyElements);



  /**
   * Retrieves the subFinal element for this substring filter.  This
   * is not applicable for any other filter type, and may not be
   * provided even for some substring filters.
   *
   * @return  The subFinal element for this substring filter, or
   *          {@code null} if there is none.
   */
  public abstract ByteString getSubFinalElement();



  /**
   * Specifies the subFinal element for this substring filter.  This
   * will be ignored for all other filter types.
   *
   * @param  subFinalElement  The subFinal element for this substring
   *                          filter.
   */
  public abstract void setSubFinalElement(ByteString subFinalElement);



  /**
   * Retrieves the matching rule ID for this extensible match filter.
   * This is not applicable for any other type of filter and may not
   * be included in some extensible matching filters.
   *
   * @return  The matching rule ID for this extensible match filter,
   *          or {@code null} if there is none.
   */
  public abstract String getMatchingRuleID();



  /**
   * Specifies the matching rule ID for this extensible match filter.
   * It will be ignored for all other filter types.
   *
   * @param  matchingRuleID  The matching rule ID for this extensible
   *                         match filter.
   */
  public abstract void setMatchingRuleID(String matchingRuleID);



  /**
   * Retrieves the value of the DN attributes flag for this extensible
   * match filter, which indicates whether to perform matching on the
   * components of the DN.  This does not apply for any other type of
   * filter.
   *
   * @return  The value of the DN attributes flag for this
   *          extensibleMatch filter.
   */
  public abstract boolean getDNAttributes();



  /**
   * Specifies the value of the DN attributes flag for this extensible
   * match filter.  It will be ignored for all other filter types.
   *
   * @param  dnAttributes  The value of the DN attributes flag for
   *                       this extensible match filter.
   */
  public abstract void setDNAttributes(boolean dnAttributes);



  /**
   * Encodes this search filter to an ASN.1 element.
   *
   * @return  The ASN.1 element containing the encoded search filter.
   */
  public final ASN1Element encode()
  {
    FilterType filterType = getFilterType();
    switch (filterType)
    {
      case AND:
      case OR:
        ArrayList<RawFilter> filterComponents = getFilterComponents();
        ArrayList<ASN1Element> elements =
             new ArrayList<ASN1Element>(filterComponents.size());
        for (RawFilter f : filterComponents)
        {
          elements.add(f.encode());
        }
        return new ASN1Set(filterType.getBERType(), elements);
      case NOT:
        return new ASN1Element(filterType.getBERType(),
                               getNOTComponent().encode().encode());
      case EQUALITY:
      case GREATER_OR_EQUAL:
      case LESS_OR_EQUAL:
      case APPROXIMATE_MATCH:
        String attributeType = getAttributeType();
        ByteString assertionValue = getAssertionValue();
        elements = new ArrayList<ASN1Element>(2);
        elements.add(new ASN1OctetString(attributeType));
        elements.add(assertionValue.toASN1OctetString());
        return new ASN1Sequence(filterType.getBERType(), elements);
      case SUBSTRING:
        attributeType = getAttributeType();
        elements = new ArrayList<ASN1Element>(2);
        elements.add(new ASN1OctetString(attributeType));

        ByteString subInitialElement = getSubInitialElement();
        ArrayList<ASN1Element> subElements =
             new ArrayList<ASN1Element>();
        if (subInitialElement != null)
        {
          ASN1OctetString subInitialOS =
               subInitialElement.toASN1OctetString();
          subInitialOS.setType(TYPE_SUBINITIAL);
          subElements.add(subInitialOS);
        }

        ArrayList<ByteString> subAnyElements = getSubAnyElements();
        if ((subAnyElements != null) && (! subAnyElements.isEmpty()))
        {
          for (ByteString s : subAnyElements)
          {
            ASN1OctetString os = s.toASN1OctetString();
            os.setType(TYPE_SUBANY);
            subElements.add(os);
          }
        }

        ByteString subFinalElement = getSubFinalElement();
        if (subFinalElement != null)
        {
          ASN1OctetString subFinalOS =
               subFinalElement.toASN1OctetString();
          subFinalOS.setType(TYPE_SUBFINAL);
          subElements.add(subFinalOS);
        }

        elements.add(new ASN1Sequence(subElements));
        return new ASN1Sequence(filterType.getBERType(), elements);
      case PRESENT:
        return new ASN1OctetString(filterType.getBERType(),
                                   getAttributeType());
      case EXTENSIBLE_MATCH:
        elements = new ArrayList<ASN1Element>(4);

        String matchingRuleID = getMatchingRuleID();
        if (matchingRuleID != null)
        {
          elements.add(new ASN1OctetString(TYPE_MATCHING_RULE_ID,
                                           matchingRuleID));
        }

        attributeType = getAttributeType();
        if (attributeType != null)
        {
          elements.add(new ASN1OctetString(TYPE_MATCHING_RULE_TYPE,
                                           attributeType));
        }

        ASN1OctetString assertionValueOS =
             getAssertionValue().toASN1OctetString();
        assertionValueOS.setType(TYPE_MATCHING_RULE_VALUE);
        elements.add(assertionValueOS);

        if (getDNAttributes())
        {
          elements.add(new ASN1Boolean(
               TYPE_MATCHING_RULE_DN_ATTRIBUTES, true));
        }

        return new ASN1Sequence(filterType.getBERType(), elements);
      default:
        if (debugEnabled())
        {
          debugError("Invalid search filter type: %s", filterType);
        }
        return null;
    }
  }



  /**
   * Decodes the provided ASN.1 element as a raw search filter.
   *
   * @param  element  The ASN.1 element to decode.
   *
   * @return  The decoded search filter.
   *
   * @throws  LDAPException  If the provided ASN.1 element cannot be
   *                         decoded as a raw search filter.
   */
  public static LDAPFilter decode(ASN1Element element)
         throws LDAPException
  {
    if (element == null)
    {
      int    msgID   = MSGID_LDAP_FILTER_DECODE_NULL;
      String message = getMessage(msgID);
      throw new LDAPException(PROTOCOL_ERROR, msgID, message);
    }

    switch (element.getType())
    {
      case TYPE_FILTER_AND:
      case TYPE_FILTER_OR:
        return decodeCompoundFilter(element);

      case TYPE_FILTER_NOT:
        return decodeNotFilter(element);

      case TYPE_FILTER_EQUALITY:
      case TYPE_FILTER_GREATER_OR_EQUAL:
      case TYPE_FILTER_LESS_OR_EQUAL:
      case TYPE_FILTER_APPROXIMATE:
        return decodeTypeAndValueFilter(element);

      case TYPE_FILTER_SUBSTRING:
        return decodeSubstringFilter(element);

      case TYPE_FILTER_PRESENCE:
        return decodePresenceFilter(element);

      case TYPE_FILTER_EXTENSIBLE_MATCH:
        return decodeExtensibleMatchFilter(element);

      default:
        int    msgID   = MSGID_LDAP_FILTER_DECODE_INVALID_TYPE;
        String message = getMessage(msgID, element.getType());
        throw new LDAPException(PROTOCOL_ERROR, msgID, message);
    }
  }



  /**
   * Decodes the provided ASN.1 element as a compound filter (i.e.,
   * one that holds a set of subordinate filter components, like AND
   * or OR filters).
   *
   * @param  element  the ASN.1 element to decode.
   *
   * @return  The decoded LDAP search filter.
   *
   * @throws  LDAPException  If a problem occurs while trying to
   *                         decode the provided ASN.1 element as a
   *                         raw search filter.
   */
  private static LDAPFilter decodeCompoundFilter(ASN1Element element)
          throws LDAPException
  {
    FilterType filterType;
    switch (element.getType())
    {
      case TYPE_FILTER_AND:
        filterType = FilterType.AND;
        break;
      case TYPE_FILTER_OR:
        filterType = FilterType.OR;
        break;
      default:
        // This should never happen.
        if (debugEnabled())
        {
          debugError("Invalid filter type %x for a compound filter",
                     element.getType());
        }
        filterType = null;
    }


    ArrayList<ASN1Element> elements;
    try
    {
      elements = element.decodeAsSet().elements();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_FILTER_DECODE_COMPOUND_SET;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    ArrayList<RawFilter> filterComponents =
         new ArrayList<RawFilter>(elements.size());
    try
    {
      for (ASN1Element e : elements)
      {
        filterComponents.add(LDAPFilter.decode(e));
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
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_LDAP_FILTER_DECODE_COMPOUND_COMPONENTS;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    return new LDAPFilter(filterType, filterComponents, null, null,
                          null, null, null, null, null, false);
  }



  /**
   * Decodes the provided ASN.1 element as a NOT filter.
   *
   * @param  element  the ASN.1 element to decode.
   *
   * @return  The decoded LDAP search filter.
   *
   * @throws  LDAPException  If a problem occurs while trying to
   *                         decode the provided ASN.1 element as a
   *                         raw search filter.
   */
  private static LDAPFilter decodeNotFilter(ASN1Element element)
          throws LDAPException
  {
    ASN1Element notFilterElement;
    try
    {
      notFilterElement = ASN1Element.decode(element.value());
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_LDAP_FILTER_DECODE_NOT_ELEMENT;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    RawFilter notComponent;
    try
    {
      notComponent = decode(notFilterElement);
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

      int    msgID   = MSGID_LDAP_FILTER_DECODE_NOT_COMPONENT;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    return new LDAPFilter(FilterType.NOT, null, notComponent, null,
                          null, null, null, null, null, false);
  }



  /**
   * Decodes the provided ASN.1 element as a filter containing an
   * attribute type and an assertion value.  This includes equality,
   * greater or equal, less or equal, and approximate search filters.
   *
   * @param  element  the ASN.1 element to decode.
   *
   * @return  The decoded LDAP search filter.
   *
   * @throws  LDAPException  If a problem occurs while trying to
   *                         decode the provided ASN.1 element as a
   *                         raw search filter.
   */
  private static LDAPFilter decodeTypeAndValueFilter(
                                 ASN1Element element)
          throws LDAPException
  {
    FilterType filterType;
    switch (element.getType())
    {
      case TYPE_FILTER_EQUALITY:
        filterType = FilterType.EQUALITY;
        break;
      case TYPE_FILTER_GREATER_OR_EQUAL:
        filterType = FilterType.GREATER_OR_EQUAL;
        break;
      case TYPE_FILTER_LESS_OR_EQUAL:
        filterType = FilterType.LESS_OR_EQUAL;
        break;
      case TYPE_FILTER_APPROXIMATE:
        filterType = FilterType.APPROXIMATE_MATCH;
        break;
      default:
        // This should never happen.
        if (debugEnabled())
        {
          debugError("Invalid filter type %x for a type-and-value " +
                     "filter", element.getType());
        }
        filterType = null;
    }


    ArrayList<ASN1Element> elements;
    try
    {
      elements = element.decodeAsSequence().elements();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_LDAP_FILTER_DECODE_TV_SEQUENCE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    if (elements.size() != 2)
    {
      int msgID = MSGID_LDAP_FILTER_DECODE_TV_INVALID_ELEMENT_COUNT;
      String message = getMessage(msgID, elements.size());
      throw new LDAPException(PROTOCOL_ERROR, msgID, message);
    }


    String attributeType;
    try
    {
      attributeType =
           elements.get(0).decodeAsOctetString().stringValue();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_LDAP_FILTER_DECODE_TV_TYPE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    ByteString assertionValue;
    try
    {
      assertionValue = elements.get(1).decodeAsOctetString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_LDAP_FILTER_DECODE_TV_VALUE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    return new LDAPFilter(filterType, null, null, attributeType,
                          assertionValue, null, null, null, null,
                          false);
  }



  /**
   * Decodes the provided ASN.1 element as a substring filter.
   *
   * @param  element  the ASN.1 element to decode.
   *
   * @return  The decoded LDAP search filter.
   *
   * @throws  LDAPException  If a problem occurs while trying to
   *                         decode the provided ASN.1 element as a
   *                         raw search filter.
   */
  private static LDAPFilter decodeSubstringFilter(ASN1Element element)
          throws LDAPException
  {
    ArrayList<ASN1Element> elements;
    try
    {
      elements = element.decodeAsSequence().elements();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_LDAP_FILTER_DECODE_SUBSTRING_SEQUENCE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    if (elements.size() != 2)
    {
      int msgID =
           MSGID_LDAP_FILTER_DECODE_SUBSTRING_INVALID_ELEMENT_COUNT;
      String message = getMessage(msgID, elements.size());
      throw new LDAPException(PROTOCOL_ERROR, msgID, message);
    }


    String attributeType;
    try
    {
      attributeType =
           elements.get(0).decodeAsOctetString().stringValue();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_LDAP_FILTER_DECODE_SUBSTRING_TYPE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    ArrayList<ASN1Element> subElements;
    try
    {
      subElements = elements.get(1).decodeAsSequence().elements();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_LDAP_FILTER_DECODE_SUBSTRING_ELEMENTS;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    if (subElements.isEmpty())
    {
      int msgID = MSGID_LDAP_FILTER_DECODE_SUBSTRING_NO_SUBELEMENTS;
      String message = getMessage(msgID);
      throw new LDAPException(PROTOCOL_ERROR, msgID, message);
    }


    ByteString subInitialElement = null;
    ByteString subFinalElement   = null;
    ArrayList<ByteString> subAnyElements = null;
    try
    {
      for (ASN1Element e : subElements)
      {
        switch (e.getType())
        {
          case TYPE_SUBINITIAL:
            subInitialElement = e.decodeAsOctetString();
            break;
          case TYPE_SUBFINAL:
            subFinalElement = e.decodeAsOctetString();
            break;
          case TYPE_SUBANY:
            if (subAnyElements == null)
            {
              subAnyElements = new ArrayList<ByteString>();
            }

            subAnyElements.add(e.decodeAsOctetString());
            break;
          default:
            int msgID =
                 MSGID_LDAP_FILTER_DECODE_SUBSTRING_INVALID_SUBTYPE;
            String message = getMessage(msgID);
            throw new LDAPException(PROTOCOL_ERROR, msgID, message);
        }
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
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_LDAP_FILTER_DECODE_SUBSTRING_VALUES;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    return new LDAPFilter(FilterType.SUBSTRING, null, null,
                          attributeType, null, subInitialElement,
                          subAnyElements, subFinalElement, null,
                          false);
  }



  /**
   * Decodes the provided ASN.1 element as a presence filter.
   *
   * @param  element  the ASN.1 element to decode.
   *
   * @return  The decoded LDAP search filter.
   *
   * @throws  LDAPException  If a problem occurs while trying to
   *                         decode the provided ASN.1 element as a
   *                         raw search filter.
   */
  private static LDAPFilter decodePresenceFilter(ASN1Element element)
          throws LDAPException
  {
    String attributeType;
    try
    {
      attributeType = element.decodeAsOctetString().stringValue();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_LDAP_FILTER_DECODE_PRESENCE_TYPE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    return new LDAPFilter(FilterType.PRESENT, null, null,
                          attributeType, null, null, null, null, null,
                          false);
  }



  /**
   * Decodes the provided ASN.1 element as an extensible match filter.
   *
   * @param  element  the ASN.1 element to decode.
   *
   * @return  The decoded LDAP search filter.
   *
   * @throws  LDAPException  If a problem occurs while trying to
   *                         decode the provided ASN.1 element as a
   *                         raw search filter.
   */
  private static LDAPFilter decodeExtensibleMatchFilter(ASN1Element
                                                             element)
          throws LDAPException
  {
    ArrayList<ASN1Element> elements;
    try
    {
      elements = element.decodeAsSequence().elements();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_LDAP_FILTER_DECODE_EXTENSIBLE_SEQUENCE;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    ByteString assertionValue = null;
    boolean    dnAttributes   = false;
    String     attributeType  = null;
    String     matchingRuleID = null;
    try
    {
      for (ASN1Element e : elements)
      {
        switch (e.getType())
        {
          case TYPE_MATCHING_RULE_ID:
            matchingRuleID = e.decodeAsOctetString().stringValue();
            break;
          case TYPE_MATCHING_RULE_TYPE:
            attributeType = e.decodeAsOctetString().stringValue();
            break;
          case TYPE_MATCHING_RULE_VALUE:
            assertionValue = e.decodeAsOctetString();
            break;
          case TYPE_MATCHING_RULE_DN_ATTRIBUTES:
            dnAttributes = e.decodeAsBoolean().booleanValue();
            break;
          default:
            int msgID =
                 MSGID_LDAP_FILTER_DECODE_EXTENSIBLE_INVALID_TYPE;
            String message = getMessage(msgID, e.getType());
            throw new LDAPException(PROTOCOL_ERROR, msgID, message);
        }
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
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_LDAP_FILTER_DECODE_EXTENSIBLE_ELEMENTS;
      String message = getMessage(msgID, String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, msgID, message, e);
    }


    return new LDAPFilter(FilterType.EXTENSIBLE_MATCH, null, null,
                          attributeType, assertionValue, null, null,
                          null, matchingRuleID, dnAttributes);
  }



  /**
   * Converts this raw filter to a search filter that may be used by
   * the Directory Server's core processing.
   *
   * @return  The generated search filter.
   *
   * @throws  DirectoryException  If a problem occurs while attempting
   *                              to construct the search filter.
   */
  public abstract SearchFilter toSearchFilter()
         throws DirectoryException;



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
  public abstract void toString(StringBuilder buffer);



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
  public static void valueToFilterString(StringBuilder buffer,
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

