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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.types;
import org.opends.messages.Message;



import java.util.ArrayList;
import java.io.IOException;

import org.opends.server.protocols.asn1.*;
import org.opends.server.protocols.ldap.LDAPFilter;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the data structures and methods to use when
 * interacting with a raw search filter, which defines a set of
 * criteria for locating entries in a search request.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=true,
     mayExtend=false,
     mayInvoke=true)
public abstract class RawFilter
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

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
   * Retrieves the subordinate filter component for NOT searches.
   *
   * @return  The subordinate filter component for NOT searches, or
   *          {@code null} if this is not a NOT search.
   */
  public abstract RawFilter getNOTComponent();



  /**
   * Retrieves the attribute type for this search filter.  This will
   * not be applicable for AND, OR, or NOT filters.
   *
   * @return  The attribute type for this search filter, or
   *          {@code null} if there is none.
   */
  public abstract String getAttributeType();



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
   * Retrieves the subInitial component for this substring filter.
   * This is only applicable for substring search filters, but even
   * substring filters might not have a value for this component.
   *
   * @return  The subInitial component for this substring filter, or
   *          {@code null} if there is none.
   */
  public abstract ByteString getSubInitialElement();



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
   * Retrieves the subFinal element for this substring filter.  This
   * is not applicable for any other filter type, and may not be
   * provided even for some substring filters.
   *
   * @return  The subFinal element for this substring filter, or
   *          {@code null} if there is none.
   */
  public abstract ByteString getSubFinalElement();



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
   * Writes this protocol op to an ASN.1 output stream.
   *
   * @param stream The ASN.1 output stream to write to.
   * @throws IOException If a problem occurs while writing to the
   *                     stream.
   */
  public void write(ASN1Writer stream) throws IOException
  {
      FilterType filterType = getFilterType();
    switch (filterType)
    {
      case AND:
      case OR:
        stream.writeStartSequence(filterType.getBERType());
        for(RawFilter f : getFilterComponents())
        {
          f.write(stream);
        }
        stream.writeEndSequence();
        return;
      case NOT:
        stream.writeStartSequence(filterType.getBERType());
        getNOTComponent().write(stream);
        stream.writeEndSequence();
        return;
      case EQUALITY:
      case GREATER_OR_EQUAL:
      case LESS_OR_EQUAL:
      case APPROXIMATE_MATCH:
        stream.writeStartSequence(filterType.getBERType());
        stream.writeOctetString(getAttributeType());
        stream.writeOctetString(getAssertionValue());
        stream.writeEndSequence();
        return;
      case SUBSTRING:
        stream.writeStartSequence(filterType.getBERType());
        stream.writeOctetString(getAttributeType());

        stream.writeStartSequence();
        ByteString subInitialElement = getSubInitialElement();
        if (subInitialElement != null)
        {
          stream.writeOctetString(TYPE_SUBINITIAL, subInitialElement);
        }

        ArrayList<ByteString> subAnyElements = getSubAnyElements();
        if ((subAnyElements != null) && (! subAnyElements.isEmpty()))
        {
          for (ByteString s : subAnyElements)
          {
            stream.writeOctetString(TYPE_SUBANY, s);
          }
        }

        ByteString subFinalElement = getSubFinalElement();
        if (subFinalElement != null)
        {
          stream.writeOctetString(TYPE_SUBFINAL, subFinalElement);
        }
        stream.writeEndSequence();

        stream.writeEndSequence();
        return;
      case PRESENT:
        stream.writeOctetString(filterType.getBERType(),
            getAttributeType());
        return;
      case EXTENSIBLE_MATCH:
        stream.writeStartSequence(filterType.getBERType());

        String matchingRuleID = getMatchingRuleID();
        if (matchingRuleID != null)
        {
          stream.writeOctetString(TYPE_MATCHING_RULE_ID,
              matchingRuleID);
        }

        String attributeType = getAttributeType();
        if (attributeType != null)
        {
          stream.writeOctetString(TYPE_MATCHING_RULE_TYPE,
              attributeType);
        }

        stream.writeOctetString(TYPE_MATCHING_RULE_VALUE,
            getAssertionValue());

        if (getDNAttributes())
        {
          stream.writeBoolean(TYPE_MATCHING_RULE_DN_ATTRIBUTES, true);
        }

        stream.writeEndSequence();
        return;
      default:
        if (debugEnabled())
        {
          TRACER.debugError("Invalid search filter type: %s",
                            filterType);
        }
    }
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as a
   * raw search filter.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded search filter.
   *
   * @throws  LDAPException  If the provided ASN.1 element cannot be
   *                         decoded as a raw search filter.
   */
  public static LDAPFilter decode(ASN1Reader reader)
         throws LDAPException
  {
    byte type;
    try
    {
      type = reader.peekType();
    }
    catch(Exception e)
    {
      Message message = ERR_LDAP_FILTER_DECODE_NULL.get();
      throw new LDAPException(PROTOCOL_ERROR, message);
    }

    switch (type)
    {
      case TYPE_FILTER_AND:
      case TYPE_FILTER_OR:
        return decodeCompoundFilter(reader);

      case TYPE_FILTER_NOT:
        return decodeNotFilter(reader);

      case TYPE_FILTER_EQUALITY:
      case TYPE_FILTER_GREATER_OR_EQUAL:
      case TYPE_FILTER_LESS_OR_EQUAL:
      case TYPE_FILTER_APPROXIMATE:
        return decodeAVAFilter(reader);

      case TYPE_FILTER_SUBSTRING:
        return decodeSubstringFilter(reader);

      case TYPE_FILTER_PRESENCE:
        return decodePresenceFilter(reader);

      case TYPE_FILTER_EXTENSIBLE_MATCH:
        return decodeExtensibleMatchFilter(reader);

      default:
        Message message =
            ERR_LDAP_FILTER_DECODE_INVALID_TYPE.get(type);
        throw new LDAPException(PROTOCOL_ERROR, message);
    }
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as a compound
   * filter (i.e. one that holds a set of subordinate filter
   * components, like AND  or OR filters).
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded LDAP search filter.
   *
   * @throws  LDAPException  If a problem occurs while trying to
   *                         decode the provided ASN.1 element as a
   *                         raw search filter.
   */
  private static LDAPFilter decodeCompoundFilter(ASN1Reader reader)
      throws LDAPException
  {
    byte type;
    try
    {
      type = reader.peekType();
    }
    catch(Exception e)
    {
      Message message = ERR_LDAP_FILTER_DECODE_NULL.get();
      throw new LDAPException(PROTOCOL_ERROR, message);
    }

    FilterType filterType;
    switch (type)
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
          TRACER.debugError("Invalid filter type %x for a " +
              "compound filter", type);
        }
        filterType = null;
    }

    ArrayList<RawFilter> filterComponents =
        new ArrayList<RawFilter>();
    try
    {
      reader.readStartSequence();
      // Should have atleast 1 filter.
      do
      {
        filterComponents.add(LDAPFilter.decode(reader));
      }
      while(reader.hasNextElement());
      reader.readEndSequence();
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

      Message message = ERR_LDAP_FILTER_DECODE_COMPOUND_COMPONENTS.
          get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    return new LDAPFilter(filterType, filterComponents, null, null,
        null, null, null, null, null, false);
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as a NOT
   * filter.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded LDAP search filter.
   *
   * @throws  LDAPException  If a problem occurs while trying to
   *                         decode the provided ASN.1 element as a
   *                         raw search filter.
   */
  private static LDAPFilter decodeNotFilter(ASN1Reader reader)
          throws LDAPException
  {
    RawFilter notComponent;
    try
    {
      reader.readStartSequence();
      notComponent = decode(reader);
      reader.readEndSequence();
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

      Message message =
          ERR_LDAP_FILTER_DECODE_NOT_COMPONENT.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    return new LDAPFilter(FilterType.NOT, null, notComponent, null,
                          null, null, null, null, null, false);
  }

  /**
   * Decodes the elements from the provided ASN.1 read as as a filter
   * containing an attribute type and an assertion value.  This
   * includes equality, greater or equal, less or equal, and
   * approximate search filters.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded LDAP search filter.
   *
   * @throws  LDAPException  If a problem occurs while trying to
   *                         decode the provided ASN.1 element as a
   *                         raw search filter.
   */
  private static LDAPFilter decodeAVAFilter(ASN1Reader reader)
          throws LDAPException
  {
    byte type;
    try
    {
      type = reader.peekType();
    }
    catch(Exception e)
    {
      Message message = ERR_LDAP_FILTER_DECODE_NULL.get();
      throw new LDAPException(PROTOCOL_ERROR, message);
    }

    FilterType filterType;
    switch (type)
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
          TRACER.debugError("Invalid filter type %x for a " +
              "type-and-value filter", type);
        }
        filterType = null;
    }

    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_FILTER_DECODE_TV_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    String attributeType;
    try
    {
      attributeType = reader.readOctetStringAsString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_FILTER_DECODE_TV_TYPE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    ByteString assertionValue;
    try
    {
      assertionValue = reader.readOctetString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_FILTER_DECODE_TV_VALUE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_FILTER_DECODE_TV_SEQUENCE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    return new LDAPFilter(filterType, null, null, attributeType,
                          assertionValue, null, null, null, null,
                          false);
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as a
   * substring filter.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded LDAP search filter.
   *
   * @throws  LDAPException  If a problem occurs while trying to
   *                         decode the provided ASN.1 element as a
   *                         raw search filter.
   */
  private static LDAPFilter decodeSubstringFilter(ASN1Reader reader)
          throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_FILTER_DECODE_SUBSTRING_SEQUENCE.get(
          String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    String attributeType;
    try
    {
      attributeType = reader.readOctetStringAsString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_FILTER_DECODE_SUBSTRING_TYPE.get(
          String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_FILTER_DECODE_SUBSTRING_ELEMENTS.get(
          String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    try
    {
      // Make sure we have at least 1 substring
      reader.peekType();
    }
    catch (Exception e)
    {
      Message message =
          ERR_LDAP_FILTER_DECODE_SUBSTRING_NO_SUBELEMENTS.get();
      throw new LDAPException(PROTOCOL_ERROR, message);
    }


    ByteString subInitialElement = null;
    ByteString subFinalElement   = null;
    ArrayList<ByteString> subAnyElements = null;
    try
    {
      if(reader.hasNextElement() &&
          reader.peekType() == TYPE_SUBINITIAL)
      {
        subInitialElement = reader.readOctetString();
      }
      while(reader.hasNextElement() &&
          reader.peekType() == TYPE_SUBANY)
      {
        if(subAnyElements == null)
        {
          subAnyElements = new ArrayList<ByteString>();
        }
        subAnyElements.add(reader.readOctetString());
      }
      if(reader.hasNextElement() &&
          reader.peekType() == TYPE_SUBFINAL)
      {
        subFinalElement = reader.readOctetString();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_FILTER_DECODE_SUBSTRING_VALUES.get(
          String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_FILTER_DECODE_SUBSTRING_ELEMENTS.get(
          String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_FILTER_DECODE_SUBSTRING_SEQUENCE.get(
          String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    return new LDAPFilter(FilterType.SUBSTRING, null, null,
                          attributeType, null, subInitialElement,
                          subAnyElements, subFinalElement, null,
                          false);
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as a
   * presence filter.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded LDAP search filter.
   *
   * @throws  LDAPException  If a problem occurs while trying to
   *                         decode the provided ASN.1 element as a
   *                         raw search filter.
   */
  private static LDAPFilter decodePresenceFilter(ASN1Reader reader)
          throws LDAPException
  {
    String attributeType;
    try
    {
      attributeType = reader.readOctetStringAsString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_LDAP_FILTER_DECODE_PRESENCE_TYPE.get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    return new LDAPFilter(FilterType.PRESENT, null, null,
                          attributeType, null, null, null, null, null,
                          false);
  }

  /**
   * Decodes the elements from the provided ASN.1 reader as an
   * extensible match filter.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded LDAP search filter.
   *
   * @throws  LDAPException  If a problem occurs while trying to
   *                         decode the provided ASN.1 element as a
   *                         raw search filter.
   */
  private static LDAPFilter decodeExtensibleMatchFilter(
      ASN1Reader reader) throws LDAPException
  {
    try
    {
      reader.readStartSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_FILTER_DECODE_EXTENSIBLE_SEQUENCE.
          get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }


    ByteString assertionValue;
    boolean    dnAttributes   = false;
    String     attributeType  = null;
    String     matchingRuleID = null;
    try
    {
      if(reader.peekType() == TYPE_MATCHING_RULE_ID)
      {
        matchingRuleID = reader.readOctetStringAsString();
      }
      if(matchingRuleID == null ||
          reader.peekType() == TYPE_MATCHING_RULE_TYPE)
      {
        attributeType = reader.readOctetStringAsString();
      }
      assertionValue = reader.readOctetString();
      if(reader.hasNextElement() &&
          reader.peekType() == TYPE_MATCHING_RULE_DN_ATTRIBUTES)
      {
        dnAttributes = reader.readBoolean();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_FILTER_DECODE_EXTENSIBLE_ELEMENTS.
          get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
    }

    try
    {
      reader.readEndSequence();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_LDAP_FILTER_DECODE_EXTENSIBLE_SEQUENCE.
          get(String.valueOf(e));
      throw new LDAPException(PROTOCOL_ERROR, message, e);
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
    buffer.ensureCapacity(buffer.length() + value.length());
    for (int i = 0; i < value.length(); i++)
    {
      byte b = value.byteAt(i);
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

