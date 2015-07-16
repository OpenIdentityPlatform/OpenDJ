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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2014 Manuel Gaupp
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.controls;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.io.ASN1Reader;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.util.Reject;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.AttributeType;
import org.opends.server.types.LDAPException;
import org.opends.server.types.RawFilter;

import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.protocols.ldap.LDAPConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a filter that may be used in conjunction with the matched
 * values control to indicate which particular values of a multivalued attribute
 * should be returned.  The matched values filter is essentially a subset of an
 * LDAP search filter, lacking support for AND, OR, and NOT components, and
 * lacking support for the dnAttributes component of extensible matching
 * filters.
 */
public class MatchedValuesFilter
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The BER type associated with the equalityMatch filter type. */
  public static final byte EQUALITY_MATCH_TYPE = (byte) 0xA3;
  /** The BER type associated with the substrings filter type. */
  public static final byte SUBSTRINGS_TYPE = (byte) 0xA4;
  /** The BER type associated with the greaterOrEqual filter type. */
  public static final byte GREATER_OR_EQUAL_TYPE = (byte) 0xA5;
  /** The BER type associated with the lessOrEqual filter type. */
  public static final byte LESS_OR_EQUAL_TYPE = (byte) 0xA6;
  /** The BER type associated with the present filter type. */
  public static final byte PRESENT_TYPE = (byte) 0x87;
  /** The BER type associated with the approxMatch filter type. */
  public static final byte APPROXIMATE_MATCH_TYPE = (byte) 0xA8;
  /** The BER type associated with the extensibleMatch filter type. */
  public static final byte EXTENSIBLE_MATCH_TYPE = (byte) 0xA9;

  /** The matching rule ID for this matched values filter. */
  private final String matchingRuleID;
  /** Indicates whether the elements of this matched values filter have been fully decoded. */
  private boolean decoded;
  /** The match type for this matched values filter. */
  private final byte matchType;

  /** The raw, unprocessed attribute type for this matched values filter. */
  private final String rawAttributeType;
  /** The processed attribute type for this matched values filter. */
  private AttributeType attributeType;

  /** The matching rule for this matched values filter. */
  private MatchingRule matchingRule;
  /** The equality matching rule for this matched values filter. */
  private MatchingRule equalityMatchingRule;
  /** The ordering matching rule for this matched values filter. */
  private MatchingRule orderingMatchingRule;
  /** The substring matching rule for this matched values filter. */
  private MatchingRule substringMatchingRule;
  /** The approximate matching rule for this matched values filter. */
  private MatchingRule approximateMatchingRule;

  /** The raw, unprocessed assertion value for this matched values filter. */
  private final ByteString rawAssertionValue;
  /** The processed assertion value for this matched values filter. */
  private ByteString assertionValue;
  /** The assertion created from substring matching rule using values of this filter. */
  private Assertion substringAssertion;
  /** The subInitial value for this matched values filter. */
  private final ByteString subInitial;
  /** The set of subAny values for this matched values filter. */
  private final List<ByteString> subAny;
  /** The subFinal value for this matched values filter. */
  private final ByteString subFinal;

  /**
   * Creates a new matched values filter with the provided information.
   *
   * @param  matchType          The match type for this matched values filter.
   * @param  rawAttributeType   The raw, unprocessed attribute type.
   * @param  rawAssertionValue  The raw, unprocessed assertion value.
   * @param  subInitial         The subInitial element.
   * @param  subAny             The set of subAny elements.
   * @param  subFinal           The subFinal element.
   * @param  matchingRuleID     The matching rule ID.
   */
  private MatchedValuesFilter(byte matchType, String rawAttributeType,
                              ByteString rawAssertionValue,
                              ByteString subInitial, List<ByteString> subAny,
                              ByteString subFinal, String matchingRuleID)
  {
    this.matchType         = matchType;
    this.rawAttributeType  = rawAttributeType;
    this.rawAssertionValue = rawAssertionValue;
    this.subInitial        = subInitial;
    this.subAny            = subAny;
    this.subFinal          = subFinal;
    this.matchingRuleID    = matchingRuleID;
  }



  /**
   * Creates a new equalityMatch filter with the provided information.
   *
   * @param  rawAttributeType   The raw, unprocessed attribute type.
   * @param  rawAssertionValue  The raw, unprocessed assertion value.
   *
   * @return  The created equalityMatch filter.
   */
  public static MatchedValuesFilter createEqualityFilter(
                                         String rawAttributeType,
                                         ByteString rawAssertionValue)
  {
    Reject.ifNull(rawAttributeType,rawAssertionValue);

    return new MatchedValuesFilter(EQUALITY_MATCH_TYPE, rawAttributeType,
                                   rawAssertionValue, null, null, null, null);
  }



  /**
   * Creates a new equalityMatch filter with the provided information.
   *
   * @param  attributeType   The attribute type.
   * @param  assertionValue  The assertion value.
   *
   * @return  The created equalityMatch filter.
   */
  public static MatchedValuesFilter createEqualityFilter(
                                         AttributeType attributeType,
                                         ByteString assertionValue)
  {
    Reject.ifNull(attributeType, assertionValue);
    String rawAttributeType = attributeType.getNameOrOID();

    MatchedValuesFilter filter =
         new MatchedValuesFilter(EQUALITY_MATCH_TYPE, rawAttributeType,
                                 assertionValue, null, null, null, null);
    filter.attributeType  = attributeType;
    filter.assertionValue = assertionValue;

    return filter;
  }



  /**
   * Creates a new substrings filter with the provided information.
   *
   * @param  rawAttributeType  The raw, unprocessed attribute type.
   * @param  subInitial        The subInitial element.
   * @param  subAny            The set of subAny elements.
   * @param  subFinal          The subFinal element.
   *
   * @return  The created substrings filter.
   */
  public static MatchedValuesFilter createSubstringsFilter(
                                         String rawAttributeType,
                                         ByteString subInitial,
                                         List<ByteString> subAny,
                                         ByteString subFinal)
  {
    Reject.ifNull(rawAttributeType);
    return new MatchedValuesFilter(SUBSTRINGS_TYPE, rawAttributeType, null,
                                   subInitial, subAny, subFinal, null);
  }



  /**
   * Creates a new substrings filter with the provided information.
   *
   * @param  attributeType  The raw, unprocessed attribute type.
   * @param  subInitial     The subInitial element.
   * @param  subAny         The set of subAny elements.
   * @param  subFinal       The subFinal element.
   *
   * @return  The created substrings filter.
   */
  public static MatchedValuesFilter createSubstringsFilter(
                                         AttributeType attributeType,
                                         ByteString subInitial,
                                         List<ByteString> subAny,
                                         ByteString subFinal)
  {
    Reject.ifNull(attributeType);
    String rawAttributeType = attributeType.getNameOrOID();

    MatchedValuesFilter filter =
         new MatchedValuesFilter(SUBSTRINGS_TYPE, rawAttributeType, null,
                                 subInitial, subAny, subFinal, null);
    filter.attributeType  = attributeType;

    return filter;
  }



  /**
   * Creates a new greaterOrEqual filter with the provided information.
   *
   * @param  rawAttributeType   The raw, unprocessed attribute type.
   * @param  rawAssertionValue  The raw, unprocessed assertion value.
   *
   * @return  The created greaterOrEqual filter.
   */
  public static MatchedValuesFilter createGreaterOrEqualFilter(
                                         String rawAttributeType,
                                         ByteString rawAssertionValue)
  {
   Reject.ifNull(rawAttributeType, rawAssertionValue);

    return new MatchedValuesFilter(GREATER_OR_EQUAL_TYPE, rawAttributeType,
                                   rawAssertionValue, null, null, null, null);
  }



  /**
   * Creates a new greaterOrEqual filter with the provided information.
   *
   * @param  attributeType   The attribute type.
   * @param  assertionValue  The assertion value.
   *
   * @return  The created greaterOrEqual filter.
   */
  public static MatchedValuesFilter createGreaterOrEqualFilter(
                                         AttributeType attributeType,
                                         ByteString assertionValue)
  {
    Reject.ifNull(attributeType, assertionValue);

    String          rawAttributeType  = attributeType.getNameOrOID();

    MatchedValuesFilter filter =
         new MatchedValuesFilter(GREATER_OR_EQUAL_TYPE, rawAttributeType,
                                 assertionValue, null, null, null, null);
    filter.attributeType  = attributeType;
    filter.assertionValue = assertionValue;

    return filter;
  }



  /**
   * Creates a new lessOrEqual filter with the provided information.
   *
   * @param  rawAttributeType   The raw, unprocessed attribute type.
   * @param  rawAssertionValue  The raw, unprocessed assertion value.
   *
   * @return  The created lessOrEqual filter.
   */
  public static MatchedValuesFilter createLessOrEqualFilter(
                                         String rawAttributeType,
                                         ByteString rawAssertionValue)
  {
    Reject.ifNull(rawAttributeType, rawAssertionValue);
    return new MatchedValuesFilter(LESS_OR_EQUAL_TYPE, rawAttributeType,
                                   rawAssertionValue, null, null, null, null);
  }



  /**
   * Creates a new lessOrEqual filter with the provided information.
   *
   * @param  attributeType   The attribute type.
   * @param  assertionValue  The assertion value.
   *
   * @return  The created lessOrEqual filter.
   */
  public static MatchedValuesFilter createLessOrEqualFilter(
                                         AttributeType attributeType,
                                         ByteString assertionValue)
  {
    Reject.ifNull(attributeType, assertionValue);

    String          rawAttributeType = attributeType.getNameOrOID();

    MatchedValuesFilter filter =
         new MatchedValuesFilter(LESS_OR_EQUAL_TYPE, rawAttributeType,
                                 assertionValue, null, null, null, null);
    filter.attributeType  = attributeType;
    filter.assertionValue = assertionValue;

    return filter;
  }



  /**
   * Creates a new present filter with the provided information.
   *
   * @param  rawAttributeType  The raw, unprocessed attribute type.
   *
   * @return  The created present filter.
   */
  public static MatchedValuesFilter createPresentFilter(String rawAttributeType)
  {
    Reject.ifNull(rawAttributeType) ;
    return new MatchedValuesFilter(PRESENT_TYPE, rawAttributeType, null, null,
                                   null, null, null);
  }



  /**
   * Creates a new present filter with the provided information.
   *
   * @param  attributeType  The attribute type.
   *
   * @return  The created present filter.
   */
  public static MatchedValuesFilter createPresentFilter(
                                         AttributeType attributeType)
  {
    Reject.ifNull(attributeType);
    String rawAttributeType = attributeType.getNameOrOID();

    MatchedValuesFilter filter =
         new MatchedValuesFilter(PRESENT_TYPE, rawAttributeType, null, null,
                                 null, null, null);
    filter.attributeType  = attributeType;

    return filter;
  }



  /**
   * Creates a new approxMatch filter with the provided information.
   *
   * @param  rawAttributeType   The raw, unprocessed attribute type.
   * @param  rawAssertionValue  The raw, unprocessed assertion value.
   *
   * @return  The created approxMatch filter.
   */
  public static MatchedValuesFilter createApproximateFilter(
                                         String rawAttributeType,
                                         ByteString rawAssertionValue)
  {
    Reject.ifNull(rawAttributeType,rawAssertionValue);

    return new MatchedValuesFilter(APPROXIMATE_MATCH_TYPE, rawAttributeType,
                                   rawAssertionValue, null, null, null, null);
  }



  /**
   * Creates a new approxMatch filter with the provided information.
   *
   * @param  attributeType   The attribute type.
   * @param  assertionValue  The assertion value.
   *
   * @return  The created approxMatch filter.
   */
  public static MatchedValuesFilter createApproximateFilter(
                                         AttributeType attributeType,
                                         ByteString assertionValue)
  {
    Reject.ifNull(attributeType,assertionValue);
    String          rawAttributeType  = attributeType.getNameOrOID();

    MatchedValuesFilter filter =
         new MatchedValuesFilter(APPROXIMATE_MATCH_TYPE, rawAttributeType,
                                 assertionValue, null, null, null, null);
    filter.attributeType  = attributeType;
    filter.assertionValue = assertionValue;

    return filter;
  }



  /**
   * Creates a new extensibleMatch filter with the provided information.
   *
   * @param  rawAttributeType   The raw, unprocessed attribute type.
   * @param  matchingRuleID     The matching rule ID.
   * @param  rawAssertionValue  The raw, unprocessed assertion value.
   *
   * @return  The created extensibleMatch filter.
   */
  public static MatchedValuesFilter createExtensibleMatchFilter(
                                         String rawAttributeType,
                                         String matchingRuleID,
                                         ByteString rawAssertionValue)
  {
    Reject.ifNull(rawAttributeType, matchingRuleID, rawAssertionValue);
    return new MatchedValuesFilter(EXTENSIBLE_MATCH_TYPE, rawAttributeType,
                                   rawAssertionValue, null, null, null,
                                   matchingRuleID);
  }



  /**
   * Creates a new extensibleMatch filter with the provided information.
   *
   * @param  attributeType   The attribute type.
   * @param  matchingRule    The matching rule.
   * @param  assertionValue  The assertion value.
   *
   * @return  The created extensibleMatch filter.
   */
  public static MatchedValuesFilter createExtensibleMatchFilter(
                                         AttributeType attributeType,
                                         MatchingRule matchingRule,
                                         ByteString assertionValue)
  {
    Reject.ifNull(attributeType, matchingRule, assertionValue);
    String rawAttributeType = attributeType.getNameOrOID();
    String matchingRuleID = matchingRule.getOID();

    MatchedValuesFilter filter =
         new MatchedValuesFilter(EXTENSIBLE_MATCH_TYPE, rawAttributeType,
                                 assertionValue, null, null, null,
                                 matchingRuleID);
    filter.attributeType  = attributeType;
    filter.assertionValue = assertionValue;
    filter.matchingRule   = matchingRule;

    return filter;
  }



  /**
   * Creates a new matched values filter from the provided LDAP filter.
   *
   * @param  filter  The LDAP filter to use for this matched values filter.
   *
   * @return  The corresponding matched values filter.
   *
   * @throws  LDAPException  If the provided LDAP filter cannot be treated as a
   *                         matched values filter.
   */
  public static MatchedValuesFilter createFromLDAPFilter(RawFilter filter)
         throws LDAPException
  {
    switch (filter.getFilterType())
    {
      case AND:
      case OR:
      case NOT:
        // These filter types cannot be used in a matched values filter.
        LocalizableMessage message = ERR_MVFILTER_INVALID_LDAP_FILTER_TYPE.get(
            filter, filter.getFilterType());
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message);


      case EQUALITY:
        return new MatchedValuesFilter(EQUALITY_MATCH_TYPE,
                                       filter.getAttributeType(),
                                       filter.getAssertionValue(), null, null,
                                       null, null);


      case SUBSTRING:
        return new MatchedValuesFilter(SUBSTRINGS_TYPE,
                                       filter.getAttributeType(), null,
                                       filter.getSubInitialElement(),
                                       filter.getSubAnyElements(),
                                       filter.getSubFinalElement(), null);


      case GREATER_OR_EQUAL:
        return new MatchedValuesFilter(GREATER_OR_EQUAL_TYPE,
                                       filter.getAttributeType(),
                                       filter.getAssertionValue(), null, null,
                                       null, null);


      case LESS_OR_EQUAL:
        return new MatchedValuesFilter(LESS_OR_EQUAL_TYPE,
                                       filter.getAttributeType(),
                                       filter.getAssertionValue(), null, null,
                                       null, null);


      case PRESENT:
        return new MatchedValuesFilter(PRESENT_TYPE, filter.getAttributeType(),
                                       null, null, null, null, null);


      case APPROXIMATE_MATCH:
        return new MatchedValuesFilter(APPROXIMATE_MATCH_TYPE,
                                       filter.getAttributeType(),
                                       filter.getAssertionValue(), null, null,
                                       null, null);


      case EXTENSIBLE_MATCH:
        if (filter.getDNAttributes())
        {
          // This cannot be represented in a matched values filter.
          message = ERR_MVFILTER_INVALID_DN_ATTRIBUTES_FLAG.get(filter);
          throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message);
        }
        else
        {
          return new MatchedValuesFilter(EXTENSIBLE_MATCH_TYPE,
                                         filter.getAttributeType(),
                                         filter.getAssertionValue(), null, null,
                                         null, filter.getMatchingRuleID());
        }


      default:
        message = ERR_MVFILTER_INVALID_LDAP_FILTER_TYPE.get(filter, filter.getFilterType());
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message);
    }
  }

  /**
   * Encodes this matched values filter as an ASN.1 element.
   *
   * @param writer The ASN1Writer to use to encode this matched values filter.
   * @throws IOException if an error occurs while encoding.
   */
  public void encode(ASN1Writer writer) throws IOException
  {
    switch (matchType)
    {
      case EQUALITY_MATCH_TYPE:
      case GREATER_OR_EQUAL_TYPE:
      case LESS_OR_EQUAL_TYPE:
      case APPROXIMATE_MATCH_TYPE:
        // These will all be encoded in the same way.
        writer.writeStartSequence(matchType);
        writer.writeOctetString(rawAttributeType);
        writer.writeOctetString(rawAssertionValue);
        writer.writeEndSequence();
        return;

      case SUBSTRINGS_TYPE:
        writer.writeStartSequence(matchType);
        writer.writeOctetString(rawAttributeType);

        writer.writeStartSequence();
        if (subInitial != null)
        {
          writer.writeOctetString(TYPE_SUBINITIAL, subInitial);
        }

        if (subAny != null)
        {
          for (ByteString s : subAny)
          {
            writer.writeOctetString(TYPE_SUBANY, s);
          }
        }

        if (subFinal != null)
        {
          writer.writeOctetString(TYPE_SUBFINAL, subFinal);
        }
        writer.writeEndSequence();

        writer.writeEndSequence();
        return;

      case PRESENT_TYPE:
        writer.writeOctetString(matchType, rawAttributeType);
        return;

      case EXTENSIBLE_MATCH_TYPE:
        writer.writeStartSequence(matchType);
        if (matchingRuleID != null)
        {
          writer.writeOctetString(TYPE_MATCHING_RULE_ID, matchingRuleID);
        }

        if (rawAttributeType != null)
        {
          writer.writeOctetString(TYPE_MATCHING_RULE_TYPE, rawAttributeType);
        }
        writer.writeOctetString(TYPE_MATCHING_RULE_VALUE, rawAssertionValue);
        writer.writeEndSequence();
        return;


      default:
    }
  }

    /**
   * Decodes the provided ASN.1 element as a matched values filter item.
   *
   * @param  reader The ASN.1 reader.
   *
   * @return  The decoded matched values filter.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         filter item.
   */
  public static MatchedValuesFilter decode(ASN1Reader reader)
         throws LDAPException
  {
    byte type;
    try
    {
      type = reader.peekType();
    }
    catch(Exception e)
    {
      // TODO: Need a better message.
      throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR,
          ERR_MVFILTER_INVALID_ELEMENT_TYPE.get(e));
    }

    switch (type)
    {
      case EQUALITY_MATCH_TYPE:
      case GREATER_OR_EQUAL_TYPE:
      case LESS_OR_EQUAL_TYPE:
      case APPROXIMATE_MATCH_TYPE:
        // These will all be decoded in the same manner.  The element must be a
        // sequence consisting of the attribute type and assertion value.
        try
        {
          reader.readStartSequence();
          String rawAttributeType = reader.readOctetStringAsString();
          ByteString rawAssertionValue = reader.readOctetString();
          reader.readEndSequence();
          return new MatchedValuesFilter(type, rawAttributeType,
              rawAssertionValue, null, null, null, null);
        }
        catch (Exception e)
        {
          logger.traceException(e);

          LocalizableMessage message =
              ERR_MVFILTER_CANNOT_DECODE_AVA.get(getExceptionMessage(e));
          throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message, e);
        }


      case SUBSTRINGS_TYPE:
        // This must be a sequence of two elements, where the second is a
        // sequence of substring types.
        try
        {
          reader.readStartSequence();
          String rawAttributeType = reader.readOctetStringAsString();

          reader.readStartSequence();
          if(!reader.hasNextElement())
          {
            LocalizableMessage message = ERR_MVFILTER_NO_SUBSTRING_ELEMENTS.get();
            throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message);
          }

          ByteString subInitial        = null;
          ArrayList<ByteString> subAny = null;
          ByteString subFinal          = null;

          if(reader.hasNextElement() &&
              reader.peekType() == TYPE_SUBINITIAL)
          {
            subInitial = reader.readOctetString();
          }
          while(reader.hasNextElement() &&
              reader.peekType() == TYPE_SUBANY)
          {
            if(subAny == null)
            {
              subAny = new ArrayList<>();
            }
            subAny.add(reader.readOctetString());
          }
          if(reader.hasNextElement() &&
              reader.peekType() == TYPE_SUBFINAL)
          {
            subFinal = reader.readOctetString();
          }
          reader.readEndSequence();

          reader.readEndSequence();

          return new MatchedValuesFilter(type, rawAttributeType,
                                         null, subInitial, subAny, subFinal, null);
        }
        catch (LDAPException le)
        {
          throw le;
        }
        catch (Exception e)
        {
          logger.traceException(e);

          LocalizableMessage message =
              ERR_MVFILTER_CANNOT_DECODE_SUBSTRINGS.get(getExceptionMessage(e));
          throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message, e);
        }


      case PRESENT_TYPE:
        // The element must be an ASN.1 octet string holding the attribute type.
        try
        {
          String rawAttributeType = reader.readOctetStringAsString();

          return new MatchedValuesFilter(type, rawAttributeType,
                                         null, null, null, null, null);
        }
        catch (Exception e)
        {
          logger.traceException(e);

          LocalizableMessage message = ERR_MVFILTER_CANNOT_DECODE_PRESENT_TYPE.get(
              getExceptionMessage(e));
          throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message, e);
        }


      case EXTENSIBLE_MATCH_TYPE:
        // This must be a two or three element sequence with an assertion value
        // as the last element and an attribute type and/or matching rule ID as
        // the first element(s).
        try
        {
          reader.readStartSequence();

          String     rawAttributeType  = null;
          String     matchingRuleID    = null;

          if(reader.peekType() == TYPE_MATCHING_RULE_ID)
          {
            matchingRuleID = reader.readOctetStringAsString();
          }
          if(matchingRuleID == null ||
              reader.peekType() == TYPE_MATCHING_RULE_TYPE)
          {
             rawAttributeType = reader.readOctetStringAsString();
          }
          ByteString rawAssertionValue = reader.readOctetString();
          reader.readEndSequence();

          return new MatchedValuesFilter(type, rawAttributeType,
                                         rawAssertionValue, null, null, null,
                                         matchingRuleID);
        }
        catch (Exception e)
        {
          logger.traceException(e);

          LocalizableMessage message = ERR_MVFILTER_CANNOT_DECODE_EXTENSIBLE_MATCH.get(
              getExceptionMessage(e));
          throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message, e);
        }


      default:
        LocalizableMessage message =
            ERR_MVFILTER_INVALID_ELEMENT_TYPE.get(byteToHex(type));
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, message);
    }
  }



  /**
   * Retrieves the match type for this matched values filter.
   *
   * @return  The match type for this matched values filter.
   */
  public byte getMatchType()
  {
    return matchType;
  }



  /**
   * Retrieves the raw, unprocessed attribute type for this matched values
   * filter.
   *
   * @return  The raw, unprocessed attribute type for this matched values
   *          filter, or <CODE>null</CODE> if there is none.
   */
  public String getRawAttributeType()
  {
    return rawAttributeType;
  }


  /**
   * Retrieves the attribute type for this matched values filter.
   *
   * @return  The attribute type for this matched values filter, or
   *          <CODE>null</CODE> if there is none.
   */
  public AttributeType getAttributeType()
  {
    if (attributeType == null && rawAttributeType != null)
    {
      String lowerName = toLowerCase(rawAttributeType);
      attributeType = DirectoryServer.getAttributeTypeOrDefault(lowerName, rawAttributeType);
    }
    return attributeType;
  }


  /**
   * Retrieves the raw, unprocessed assertion value for this matched values
   * filter.
   *
   * @return  The raw, unprocessed assertion value for this matched values
   *          filter, or <CODE>null</CODE> if there is none.
   */
  public ByteString getRawAssertionValue()
  {
    return rawAssertionValue;
  }



  /**
   * Retrieves the assertion value for this matched values filter.
   *
   * @return  The assertion value for this matched values filter, or
   *          <CODE>null</CODE> if there is none.
   */
  public ByteString getAssertionValue()
  {
    if (assertionValue == null && rawAssertionValue != null)
    {
      assertionValue = rawAssertionValue;
    }
    return assertionValue;
  }



  /**
   * Retrieves the subInitial element for this matched values filter.
   *
   * @return  The subInitial element for this matched values filter, or
   *          <CODE>null</CODE> if there is none.
   */
  public ByteString getSubInitialElement()
  {
    return subInitial;
  }

  private Assertion getSubstringAssertion() {
    if (substringAssertion == null)
    {
      try
      {
        MatchingRule rule = getSubstringMatchingRule();
        if (rule != null)
        {
          substringAssertion = rule.getSubstringAssertion(subInitial, subAny, subFinal);
        }
      }
      catch (DecodeException e)
      {
        logger.traceException(e);
      }
    }
    return substringAssertion;
  }



  /**
   * Retrieves the set of subAny elements for this matched values filter.
   *
   * @return  The set of subAny elements for this matched values filter.  If
   *          there are none, then the return value may be either
   *          <CODE>null</CODE> or an empty list.
   */
  public List<ByteString> getSubAnyElements()
  {
    return subAny;
  }

  /**
   * Retrieves the subFinal element for this matched values filter.
   *
   * @return  The subFinal element for this matched values filter, or
   *          <CODE>null</CODE> if there is none.
   */
  public ByteString getSubFinalElement()
  {
    return subFinal;
  }

  /**
   * Retrieves the matching rule ID for this matched values filter.
   *
   * @return  The matching rule ID for this matched values filter, or
   *          <CODE>null</CODE> if there is none.
   */
  public String getMatchingRuleID()
  {
    return matchingRuleID;
  }



  /**
   * Retrieves the matching rule for this matched values filter.
   *
   * @return  The matching rule for this matched values filter, or
   *          <CODE>null</CODE> if there is none.
   */
  public MatchingRule getMatchingRule()
  {
    if (matchingRule == null && matchingRuleID != null)
    {
      matchingRule = DirectoryServer.getMatchingRule(toLowerCase(matchingRuleID));
    }
    return matchingRule;
  }



  /**
   * Retrieves the approximate matching rule that should be used for this
   * matched values filter.
   *
   * @return  The approximate matching rule that should be used for this matched
   *          values filter, or <CODE>null</CODE> if there is none.
   */
  public MatchingRule getApproximateMatchingRule()
  {
    if (approximateMatchingRule == null)
    {
      AttributeType attrType = getAttributeType();
      if (attrType != null)
      {
        approximateMatchingRule = attrType.getApproximateMatchingRule();
      }
    }

    return approximateMatchingRule;
  }



  /**
   * Retrieves the equality matching rule that should be used for this matched
   * values filter.
   *
   * @return  The equality matching rule that should be used for this matched
   *          values filter, or <CODE>null</CODE> if there is none.
   */
  public MatchingRule getEqualityMatchingRule()
  {
    if (equalityMatchingRule == null)
    {
      AttributeType attrType = getAttributeType();
      if (attrType != null)
      {
        equalityMatchingRule = attrType.getEqualityMatchingRule();
      }
    }

    return equalityMatchingRule;
  }



  /**
   * Retrieves the ordering matching rule that should be used for this matched
   * values filter.
   *
   * @return  The ordering matching rule that should be used for this matched
   *          values filter, or <CODE>null</CODE> if there is none.
   */
  public MatchingRule getOrderingMatchingRule()
  {
    if (orderingMatchingRule == null)
    {
      AttributeType attrType = getAttributeType();
      if (attrType != null)
      {
        orderingMatchingRule = attrType.getOrderingMatchingRule();
      }
    }

    return orderingMatchingRule;
  }



  /**
   * Retrieves the substring matching rule that should be used for this matched
   * values filter.
   *
   * @return  The substring matching rule that should be used for this matched
   *          values filter, or <CODE>null</CODE> if there is none.
   */
  public MatchingRule getSubstringMatchingRule()
  {
    if (substringMatchingRule == null)
    {
      AttributeType attrType = getAttributeType();
      if (attrType != null)
      {
        substringMatchingRule = attrType.getSubstringMatchingRule();
      }
    }

    return substringMatchingRule;
  }



  /**
   * Decodes all components of the matched values filter so that they can be
   * referenced as member variables.
   */
  private void fullyDecode()
  {
    if (! decoded)
    {
      getAttributeType();
      getAssertionValue();
      getSubstringAssertion();
      getMatchingRule();
      getApproximateMatchingRule();
      getEqualityMatchingRule();
      getOrderingMatchingRule();
      getSubstringMatchingRule();
      decoded = true;
    }
  }



  /**
   * Indicates whether the specified attribute value matches the criteria
   * defined in this matched values filter.
   *
   * @param  type   The attribute type with which the provided value is
   *                associated.
   * @param  value  The attribute value for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the specified attribute value matches the
   *          criteria defined in this matched values filter, or
   *          <CODE>false</CODE> if not.
   */
  public boolean valueMatches(AttributeType type, ByteString value)
  {
    fullyDecode();

    switch (matchType)
    {
      case EQUALITY_MATCH_TYPE:
        if (attributeType != null
            && attributeType.equals(type)
            && rawAssertionValue != null
            && value != null
            && equalityMatchingRule != null)
        {
          return matches(equalityMatchingRule, value, rawAssertionValue);
        }
        return false;


      case SUBSTRINGS_TYPE:
        if (attributeType != null
            && attributeType.equals(type)
            && substringAssertion != null)
        {
          try
          {
            return substringAssertion.matches(substringMatchingRule.normalizeAttributeValue(value)).toBoolean();
          }
          catch (Exception e)
          {
            logger.traceException(e);
          }
        }
        return false;


      case GREATER_OR_EQUAL_TYPE:
        if (attributeType != null
            && attributeType.equals(type)
            && assertionValue != null
            && value != null
            && orderingMatchingRule != null)
        {
          try
          {
            ByteString normValue = orderingMatchingRule.normalizeAttributeValue(value);
            Assertion assertion = orderingMatchingRule.getGreaterOrEqualAssertion(assertionValue);
            return assertion.matches(normValue).toBoolean();
          }
          catch (DecodeException e)
          {
            logger.traceException(e);
          }
        }
        return false;


      case LESS_OR_EQUAL_TYPE:
        if (attributeType != null
            && attributeType.equals(type)
            && assertionValue != null
            && value != null
            && orderingMatchingRule != null)
        {
          try
          {
            ByteString normValue = orderingMatchingRule.normalizeAttributeValue(value);
            Assertion assertion = orderingMatchingRule.getLessOrEqualAssertion(assertionValue);
            return assertion.matches(normValue).toBoolean();
          }
          catch (DecodeException e)
          {
            logger.traceException(e);
          }
        }
        return false;


      case PRESENT_TYPE:
        return attributeType != null && attributeType.equals(type);


      case APPROXIMATE_MATCH_TYPE:
        if (attributeType != null
            && attributeType.equals(type)
            && assertionValue != null
            && value != null
            && approximateMatchingRule != null)
        {
          return matches(approximateMatchingRule, value, assertionValue);
        }
        return false;


      case EXTENSIBLE_MATCH_TYPE:
        if (attributeType == null)
        {
          return matches(matchingRule, value, assertionValue);
        }
        else if (!attributeType.equals(type))
        {
          return false;
        }
        return matches(equalityMatchingRule, value, rawAssertionValue);


      default:
        return false;
    }
  }

  private boolean matches(MatchingRule matchingRule, ByteString value, ByteString assertionValue)
  {
    if (matchingRule == null || value == null || assertionValue == null)
    {
      return false;
    }

    try
    {
      ByteString normValue = matchingRule.normalizeAttributeValue(value);
      Assertion assertion = matchingRule.getAssertion(assertionValue);
      return assertion.matches(normValue).toBoolean();
    }
    catch (DecodeException e)
    {
      logger.traceException(e);
      return false;
    }
  }

  /**
   * Retrieves a string representation of this matched values filter, as an RFC
   * 2254-compliant filter string.
   *
   * @return  A string representation of this matched values filter.
   */
  @Override
  public String toString()
  {
    StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * Appends a string representation of this matched values filter, as an RFC
   * 2254-compliant filter string, to the provided buffer.
   *
   * @param  buffer  The buffer to which the filter string should be appended.
   */
  public void toString(StringBuilder buffer)
  {
    switch (matchType)
    {
      case EQUALITY_MATCH_TYPE:
        appendAttributeTypeAndAssertion(buffer, "=");
        break;


      case SUBSTRINGS_TYPE:
        buffer.append("(");
        buffer.append(rawAttributeType);
        buffer.append("=");
        if (subInitial != null)
        {
          RawFilter.valueToFilterString(buffer, subInitial);
        }

        if (subAny != null)
        {
          for (ByteString s : subAny)
          {
            buffer.append("*");
            RawFilter.valueToFilterString(buffer, s);
          }
        }

        buffer.append("*");
        if (subFinal != null)
        {
          RawFilter.valueToFilterString(buffer, subFinal);
        }
        buffer.append(")");
        break;


      case GREATER_OR_EQUAL_TYPE:
        appendAttributeTypeAndAssertion(buffer, ">=");
        break;


      case LESS_OR_EQUAL_TYPE:
        appendAttributeTypeAndAssertion(buffer, "<=");
        break;


      case PRESENT_TYPE:
        buffer.append("(");
        buffer.append(rawAttributeType);
        buffer.append("=*)");
        break;


      case APPROXIMATE_MATCH_TYPE:
        appendAttributeTypeAndAssertion(buffer, "~=");
        break;


      case EXTENSIBLE_MATCH_TYPE:
        buffer.append("(");

        if (rawAttributeType != null)
        {
          buffer.append(rawAttributeType);
        }

        if (matchingRuleID != null)
        {
          buffer.append(":");
          buffer.append(matchingRuleID);
        }

        buffer.append(":=");
        RawFilter.valueToFilterString(buffer, rawAssertionValue);
        buffer.append(")");
        break;
    }
  }

  private void appendAttributeTypeAndAssertion(StringBuilder buffer, String operator)
  {
    buffer.append("(");
    buffer.append(rawAttributeType);
    buffer.append(operator);
    RawFilter.valueToFilterString(buffer, rawAssertionValue);
    buffer.append(")");
  }
}

