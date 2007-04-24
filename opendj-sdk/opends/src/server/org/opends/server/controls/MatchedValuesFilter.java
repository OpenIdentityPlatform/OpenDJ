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
import java.util.List;

import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.MatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.LDAPException;
import org.opends.server.types.RawFilter;
import org.opends.server.util.Validator;

import static org.opends.server.loggers.debug.DebugLogger.debugCaught;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ProtocolMessages.*;
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



  /**
   * The BER type associated with the equalityMatch filter type.
   */
  public static final byte EQUALITY_MATCH_TYPE = (byte) 0xA3;



  /**
   * The BER type associated with the substrings filter type.
   */
  public static final byte SUBSTRINGS_TYPE = (byte) 0xA4;



  /**
   * The BER type associated with the greaterOrEqual filter type.
   */
  public static final byte GREATER_OR_EQUAL_TYPE = (byte) 0xA5;



  /**
   * The BER type associated with the lessOrEqual filter type.
   */
  public static final byte LESS_OR_EQUAL_TYPE = (byte) 0xA6;



  /**
   * The BER type associated with the present filter type.
   */
  public static final byte PRESENT_TYPE = (byte) 0x87;



  /**
   * The BER type associated with the approxMatch filter type.
   */
  public static final byte APPROXIMATE_MATCH_TYPE = (byte) 0xA8;



  /**
   * The BER type associated with the extensibleMatch filter type.
   */
  public static final byte EXTENSIBLE_MATCH_TYPE = (byte) 0xA9;



  // The approximate matching rule for this matched values filter.
  private ApproximateMatchingRule approximateMatchingRule;

  // The normalized subFinal value for this matched values filter.
  private ASN1OctetString normalizedSubFinal;

  // The normalized subInitial value for this matched values filter.
  private ASN1OctetString normalizedSubInitial;

  // The raw, unprocessed assertion value for this matched values filter.
  private ByteString rawAssertionValue;

  // The subFinal value for this matched values filter.
  private ByteString subFinal;

  // The subInitial value for this matched values filter.
  private ByteString subInitial;

  // The processed attribute type for this matched values filter.
  private AttributeType attributeType;

  // The processed assertion value for this matched values filter.
  private AttributeValue assertionValue;

  // Indicates whether the elements of this matched values filter have been
  // fully decoded.
  private boolean decoded;

  // The match type for this matched values filter.
  private byte matchType;

  // The equality matching rule for this matched values filter.
  private EqualityMatchingRule equalityMatchingRule;

  // The set of normalized subAny values for this matched values filter.
  private List<ASN1OctetString> normalizedSubAny;

  // The set of subAny values for this matched values filter.
  private List<ByteString> subAny;

  // The matching rule for this matched values filter.
  private MatchingRule matchingRule;

  // The ordering matching rule for this matched values filter.
  private OrderingMatchingRule orderingMatchingRule;

  // The matching rule ID for this matched values filter.
  private String matchingRuleID;

  // The raw, unprocessed attribute type for this matched values filter.
  private String rawAttributeType;

  // The substring matching rule for this matched values filter.
  private SubstringMatchingRule substringMatchingRule;



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

    decoded                 = false;
    attributeType           = null;
    assertionValue          = null;
    matchingRule            = null;
    normalizedSubInitial    = null;
    normalizedSubAny        = null;
    normalizedSubFinal      = null;
    approximateMatchingRule = null;
    equalityMatchingRule    = null;
    orderingMatchingRule    = null;
    substringMatchingRule   = null;
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
    Validator.ensureNotNull(rawAttributeType,rawAssertionValue);

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
                                         AttributeValue assertionValue)
  {
    Validator.ensureNotNull(attributeType, assertionValue);
    String rawAttributeType = attributeType.getNameOrOID();
    ASN1OctetString rawAssertionValue = assertionValue.getValue()
        .toASN1OctetString();

    MatchedValuesFilter filter =
         new MatchedValuesFilter(EQUALITY_MATCH_TYPE, rawAttributeType,
                                 rawAssertionValue, null, null, null, null);
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
    Validator.ensureNotNull(rawAttributeType);
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
    Validator.ensureNotNull(attributeType);
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
   Validator.ensureNotNull(rawAttributeType, rawAssertionValue);

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
                                         AttributeValue assertionValue)
  {
    Validator.ensureNotNull(attributeType, assertionValue);

    String          rawAttributeType  = attributeType.getNameOrOID();
    ASN1OctetString rawAssertionValue =
         assertionValue.getValue().toASN1OctetString();

    MatchedValuesFilter filter =
         new MatchedValuesFilter(GREATER_OR_EQUAL_TYPE, rawAttributeType,
                                 rawAssertionValue, null, null, null, null);
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
    Validator.ensureNotNull(rawAttributeType, rawAssertionValue);
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
                                         AttributeValue assertionValue)
  {
    Validator.ensureNotNull(attributeType, assertionValue);

    String          rawAttributeType = attributeType.getNameOrOID();
    ASN1OctetString rawAssertionValue =
         assertionValue.getValue().toASN1OctetString();

    MatchedValuesFilter filter =
         new MatchedValuesFilter(LESS_OR_EQUAL_TYPE, rawAttributeType,
                                 rawAssertionValue, null, null, null, null);
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
    Validator.ensureNotNull(rawAttributeType) ;
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
    Validator.ensureNotNull(attributeType);
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
    Validator.ensureNotNull(rawAttributeType,rawAssertionValue);

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
                                         AttributeValue assertionValue)
  {
    Validator.ensureNotNull(attributeType,assertionValue);
    String          rawAttributeType  = attributeType.getNameOrOID();
    ASN1OctetString rawAssertionValue =
         assertionValue.getValue().toASN1OctetString();

    MatchedValuesFilter filter =
         new MatchedValuesFilter(APPROXIMATE_MATCH_TYPE, rawAttributeType,
                                 rawAssertionValue, null, null, null, null);
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
    Validator
        .ensureNotNull(rawAttributeType, matchingRuleID, rawAssertionValue);
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
                                         AttributeValue assertionValue)
  {
    Validator.ensureNotNull(attributeType, matchingRule, assertionValue);
    String rawAttributeType = attributeType.getNameOrOID();
    String matchingRuleID = matchingRule.getOID();
    ASN1OctetString rawAssertionValue =
         assertionValue.getValue().toASN1OctetString();

    MatchedValuesFilter filter =
         new MatchedValuesFilter(EXTENSIBLE_MATCH_TYPE, rawAttributeType,
                                 rawAssertionValue, null, null, null,
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
        int    msgID   = MSGID_MVFILTER_INVALID_LDAP_FILTER_TYPE;
        String message = getMessage(msgID, String.valueOf(filter),
                                    String.valueOf(filter.getFilterType()));
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);


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
          msgID = MSGID_MVFILTER_INVALID_DN_ATTRIBUTES_FLAG;
          message = getMessage(msgID, String.valueOf(filter));
          throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                  message);
        }
        else
        {
          return new MatchedValuesFilter(EXTENSIBLE_MATCH_TYPE,
                                         filter.getAttributeType(),
                                         filter.getAssertionValue(), null, null,
                                         null, filter.getMatchingRuleID());
        }


      default:
        msgID   = MSGID_MVFILTER_INVALID_LDAP_FILTER_TYPE;
        message = getMessage(msgID, String.valueOf(filter),
                             String.valueOf(filter.getFilterType()));
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
    }
  }



  /**
   * Encodes this matched values filter as an ASN.1 element.
   *
   * @return  The ASN.1 element containing the encoded matched values filter.
   */
  public ASN1Element encode()
  {
    switch (matchType)
    {
      case EQUALITY_MATCH_TYPE:
      case GREATER_OR_EQUAL_TYPE:
      case LESS_OR_EQUAL_TYPE:
      case APPROXIMATE_MATCH_TYPE:
        // These will all be encoded in the same way.
        ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(2);
        elements.add(new ASN1OctetString(rawAttributeType));
        elements.add(rawAssertionValue.toASN1OctetString());
        return new ASN1Sequence(matchType, elements);


      case SUBSTRINGS_TYPE:
        ArrayList<ASN1Element> subElements = new ArrayList<ASN1Element>();
        if (subInitial != null)
        {
          ASN1OctetString subInitialOS = subInitial.toASN1OctetString();
          subInitialOS.setType(TYPE_SUBINITIAL);
          subElements.add(subInitialOS);
        }

        if (subAny != null)
        {
          for (ByteString s : subAny)
          {
            ASN1OctetString os = s.toASN1OctetString();
            os.setType(TYPE_SUBANY);
            subElements.add(os);
          }
        }

        if (subFinal != null)
        {
          ASN1OctetString subFinalOS = subFinal.toASN1OctetString();
          subFinalOS.setType(TYPE_SUBFINAL);
          subElements.add(subFinalOS);
        }

        elements = new ArrayList<ASN1Element>(2);
        elements.add(new ASN1OctetString(rawAttributeType));
        elements.add(new ASN1Sequence(subElements));
        return new ASN1Sequence(matchType, elements);


      case PRESENT_TYPE:
        return new ASN1OctetString(matchType, rawAttributeType);


      case EXTENSIBLE_MATCH_TYPE:
        elements = new ArrayList<ASN1Element>(3);
        if (matchingRuleID != null)
        {
          elements.add(new ASN1OctetString(TYPE_MATCHING_RULE_ID,
                                           matchingRuleID));
        }

        if (rawAttributeType != null)
        {
          elements.add(new ASN1OctetString(TYPE_MATCHING_RULE_TYPE,
                                           rawAttributeType));
        }

        ASN1OctetString valueOS = rawAssertionValue.toASN1OctetString();
        valueOS.setType(TYPE_MATCHING_RULE_VALUE);
        elements.add(valueOS);
        return new ASN1Sequence(matchType, elements);


      default:
        return null;
    }
  }



  /**
   * Decodes the provided ASN.1 element as a matched values filter item.
   *
   * @param  element  The ASN.1 element to be decoded.
   *
   * @return  The decoded matched values filter.
   *
   * @throws  LDAPException  If a problem occurs while attempting to decode the
   *                         filter item.
   */
  public static MatchedValuesFilter decode(ASN1Element element)
         throws LDAPException
  {
    switch (element.getType())
    {
      case EQUALITY_MATCH_TYPE:
      case GREATER_OR_EQUAL_TYPE:
      case LESS_OR_EQUAL_TYPE:
      case APPROXIMATE_MATCH_TYPE:
        // These will all be decoded in the same manner.  The element must be a
        // sequence consisting of the attribute type and assertion value.
        try
        {
          ArrayList<ASN1Element> elements =
               element.decodeAsSequence().elements();
          if (elements.size() != 2)
          {
            int    msgID   = MSGID_MVFILTER_INVALID_AVA_SEQUENCE_SIZE;
            String message = getMessage(msgID, elements.size());
            throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                    message);
          }

          String rawAttributeType =
                      elements.get(0).decodeAsOctetString().stringValue();

          return new MatchedValuesFilter(element.getType(), rawAttributeType,
                                         elements.get(1).decodeAsOctetString(),
                                         null, null, null, null);
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

          int    msgID   = MSGID_MVFILTER_CANNOT_DECODE_AVA;
          String message = getMessage(msgID, getExceptionMessage(e));
          throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message,
                                  e);
        }


      case SUBSTRINGS_TYPE:
        // This must be a sequence of two elements, where the second is a
        // sequence of substring types.
        try
        {
          ArrayList<ASN1Element> elements =
               element.decodeAsSequence().elements();
          if (elements.size() != 2)
          {
            int    msgID   = MSGID_MVFILTER_INVALID_SUBSTRING_SEQUENCE_SIZE;
            String message = getMessage(msgID, elements.size());
            throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                    message);
          }

          ArrayList<ASN1Element> subElements =
               elements.get(1).decodeAsSequence().elements();
          if (subElements.isEmpty())
          {
            int    msgID   = MSGID_MVFILTER_NO_SUBSTRING_ELEMENTS;
            String message = getMessage(msgID);
            throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                    message);
          }

          String rawAttributeType =
                      elements.get(0).decodeAsOctetString().stringValue();

          ByteString subInitial        = null;
          ArrayList<ByteString> subAny = null;
          ByteString subFinal          = null;
          for (ASN1Element e : subElements)
          {
            switch (e.getType())
            {
              case TYPE_SUBINITIAL:
                if (subInitial == null)
                {
                  subInitial = e.decodeAsOctetString();
                }
                else
                {
                  int    msgID   = MSGID_MVFILTER_MULTIPLE_SUBINITIALS;
                  String message = getMessage(msgID);
                  throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                          message);
                }
                break;

              case TYPE_SUBANY:
                if (subAny == null)
                {
                  subAny = new ArrayList<ByteString>();
                }

                subAny.add(e.decodeAsOctetString());
                break;

              case TYPE_SUBFINAL:
                if (subFinal == null)
                {
                  subFinal = e.decodeAsOctetString();
                }
                else
                {
                  int    msgID   = MSGID_MVFILTER_MULTIPLE_SUBFINALS;
                  String message = getMessage(msgID);
                  throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                          message);
                }
                break;

              default:
                int    msgID   = MSGID_MVFILTER_INVALID_SUBSTRING_ELEMENT_TYPE;
                String message = getMessage(msgID, byteToHex(e.getType()));
                throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                        message);
            }
          }

          return new MatchedValuesFilter(element.getType(), rawAttributeType,
                                         null, subInitial, subAny, subFinal,
                                         null);
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

          int    msgID   = MSGID_MVFILTER_CANNOT_DECODE_SUBSTRINGS;
          String message = getMessage(msgID, getExceptionMessage(e));
          throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message,
                                  e);
        }


      case PRESENT_TYPE:
        // The element must be an ASN.1 octet string holding the attribute type.
        try
        {
          String rawAttributeType = element.decodeAsOctetString().stringValue();

          return new MatchedValuesFilter(element.getType(), rawAttributeType,
                                         null, null, null, null, null);
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          int    msgID   = MSGID_MVFILTER_CANNOT_DECODE_PRESENT_TYPE;
          String message = getMessage(msgID, getExceptionMessage(e));
          throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message,
                                  e);
        }


      case EXTENSIBLE_MATCH_TYPE:
        // This must be a two or three element sequence with an assertion value
        // as the last element and an attribute type and/or matching rule ID as
        // the first element(s).
        try
        {
          ArrayList<ASN1Element> elements =
               element.decodeAsSequence().elements();
          if ((elements.size() < 2) || (elements.size() > 3))
          {
            int    msgID   = MSGID_MVFILTER_INVALID_EXTENSIBLE_SEQUENCE_SIZE;
            String message = getMessage(msgID, elements.size());
            throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                    message);
          }


          String          rawAttributeType  = null;
          String          matchingRuleID    = null;
          ASN1OctetString rawAssertionValue = null;
          for (ASN1Element e : elements)
          {
            switch (e.getType())
            {
              case TYPE_MATCHING_RULE_ID:
                if (matchingRuleID == null)
                {
                  matchingRuleID = e.decodeAsOctetString().stringValue();
                }
                else
                {
                  int    msgID   = MSGID_MVFILTER_MULTIPLE_MATCHING_RULE_IDS;
                  String message = getMessage(msgID);
                  throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                          message);
                }
                break;

              case TYPE_MATCHING_RULE_TYPE:
                if (rawAttributeType == null)
                {
                  rawAttributeType = e.decodeAsOctetString().stringValue();
                }
                else
                {
                  int    msgID   = MSGID_MVFILTER_MULTIPLE_ATTRIBUTE_TYPES;
                  String message = getMessage(msgID);
                  throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                          message);
                }
                break;

              case TYPE_MATCHING_RULE_VALUE:
                if (rawAssertionValue == null)
                {
                  rawAssertionValue = e.decodeAsOctetString();
                }
                else
                {
                  int    msgID   = MSGID_MVFILTER_MULTIPLE_ASSERTION_VALUES;
                  String message = getMessage(msgID);
                  throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                          message);
                }
                break;

              default:
                int    msgID   = MSGID_MVFILTER_INVALID_EXTENSIBLE_ELEMENT_TYPE;
                String message = getMessage(msgID, byteToHex(e.getType()));
                throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID,
                                        message);
            }
          }


          return new MatchedValuesFilter(element.getType(), rawAttributeType,
                                         rawAssertionValue, null, null, null,
                                         matchingRuleID);
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

          int    msgID   = MSGID_MVFILTER_CANNOT_DECODE_EXTENSIBLE_MATCH;
          String message = getMessage(msgID, getExceptionMessage(e));
          throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message,
                                  e);
        }


      default:
        int    msgID   = MSGID_MVFILTER_INVALID_ELEMENT_TYPE;
        String message = getMessage(msgID, byteToHex(element.getType()));
        throw new LDAPException(LDAPResultCode.PROTOCOL_ERROR, msgID, message);
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
   * Specifies the raw, unprocessed attribute type for this matched values
   * filter.
   *
   * @param  rawAttributeType  The raw, unprocessed attribute type for this
   *                           matched values filter.
   */
  public void setRawAttributeType(String rawAttributeType)
  {
    this.rawAttributeType = rawAttributeType;

    decoded                 = false;
    attributeType           = null;
    approximateMatchingRule = null;
    equalityMatchingRule    = null;
    orderingMatchingRule    = null;
    substringMatchingRule   = null;
  }



  /**
   * Retrieves the attribute type for this matched values filter.
   *
   * @return  The attribute type for this matched values filter, or
   *          <CODE>null</CODE> if there is none.
   */
  public AttributeType getAttributeType()
  {
    if (attributeType == null)
    {
      if (rawAttributeType != null)
      {
        attributeType =
             DirectoryServer.getAttributeType(toLowerCase(rawAttributeType));
        if (attributeType == null)
        {
          attributeType =
               DirectoryServer.getDefaultAttributeType(rawAttributeType);
        }
      }
    }

    return attributeType;
  }



  /**
   * Specifies the attribute type for this matched values filter.
   *
   * @param  attributeType  The attribute type for this matched values filter.
   */
  public void setAttributeType(AttributeType attributeType)
  {
    this.attributeType = attributeType;

    if (attributeType == null)
    {
      rawAttributeType = null;
    }
    else
    {
      rawAttributeType = attributeType.getNameOrOID();
    }

    decoded                 = false;
    approximateMatchingRule = null;
    equalityMatchingRule    = null;
    orderingMatchingRule    = null;
    substringMatchingRule   = null;
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
   * Specifies the raw, unprocessed assertion value for this matched values
   * filter.
   *
   * @param  rawAssertionValue  The raw, unprocessed assertion value for this
   *                            matched values filter.
   */
  public void setRawAssertionValue(ByteString rawAssertionValue)
  {
    this.rawAssertionValue = rawAssertionValue;

    decoded        = false;
    assertionValue = null;
  }



  /**
   * Retrieves the assertion value for this matched values filter.
   *
   * @return  The assertion value for this matched values filter, or
   *          <CODE>null</CODE> if there is none.
   */
  public AttributeValue getAssertionValue()
  {
    if (assertionValue == null)
    {
      if (rawAssertionValue != null)
      {
        assertionValue = new AttributeValue(getAttributeType(),
                                            rawAssertionValue);
      }
    }

    return assertionValue;
  }



  /**
   * Specifies the assertion value for this matched values filter.
   *
   * @param  assertionValue  The assertion value for this matched values filter.
   */
  public void setAssertionValue(AttributeValue assertionValue)
  {
    this.assertionValue = assertionValue;

    if (assertionValue == null)
    {
      rawAssertionValue = null;
    }
    else
    {
      rawAssertionValue = assertionValue.getValue().toASN1OctetString();
    }

    decoded = false;
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



  /**
   * Specifies the subInitial element for this matched values filter.
   *
   * @param  subInitial  The subInitial element for this matched values filter.
   */
  public void setSubInitialElement(ByteString subInitial)
  {
    this.subInitial = subInitial;

    decoded              = false;
    normalizedSubInitial = null;
  }



  /**
   * Retrieves the normalized form of the subInitial element.
   *
   * @return  The normalized form of the subInitial element, or
   *          <CODE>null</CODE> if there is none.
   */
  public ASN1OctetString getNormalizedSubInitialElement()
  {
    if (normalizedSubInitial == null)
    {
      if ((subInitial != null) && (getSubstringMatchingRule() != null))
      {
        try
        {
          normalizedSubInitial =
               getSubstringMatchingRule().normalizeSubstring(subInitial).
                    toASN1OctetString();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
    }

    return normalizedSubInitial;
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
   * Specifies the set of subAny elements for this matched values filter.
   *
   * @param  subAny  The set of subAny elements for this matched values filter.
   */
  public void setSubAnyElements(List<ByteString> subAny)
  {
    this.subAny = subAny;

    decoded          = false;
    normalizedSubAny = null;
  }



  /**
   * Retrieves the set of normalized subAny elements for this matched values
   * filter.
   *
   * @return  The set of subAny elements for this matched values filter.  If
   *          there are none, then an empty list will be returned.  If a
   *          problem occurs while attempting to perform the normalization, then
   *          <CODE>null</CODE> will be returned.
   */
  public List<ASN1OctetString> getNormalizedSubAnyElements()
  {
    if (normalizedSubAny == null)
    {
      if ((subAny == null) || (subAny.isEmpty()))
      {
        normalizedSubAny = new ArrayList<ASN1OctetString>(0);
      }
      else
      {
        if (getSubstringMatchingRule() == null)
        {
          return null;
        }

        normalizedSubAny = new ArrayList<ASN1OctetString>();
        try
        {
          for (ByteString s : subAny)
          {
            normalizedSubAny.add(
                 substringMatchingRule.normalizeSubstring(s).
                      toASN1OctetString());
          }
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }

          normalizedSubAny = null;
        }
      }
    }

    return normalizedSubAny;
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
   * Specifies the subFinal element for this matched values filter.
   *
   * @param  subFinal  The subFinal element for this matched values filter.
   */
  public void setSubFinalElement(ByteString subFinal)
  {
    this.subFinal = subFinal;

    decoded            = false;
    normalizedSubFinal = null;
  }



  /**
   * Retrieves the normalized form of the subFinal element.
   *
   * @return  The normalized form of the subFinal element, or <CODE>null</CODE>
   *          if there is none.
   */
  public ASN1OctetString getNormalizedSubFinalElement()
  {
    if (normalizedSubFinal == null)
    {
      if ((subFinal != null) && (getSubstringMatchingRule() != null))
      {
        try
        {
          normalizedSubFinal =
               getSubstringMatchingRule().normalizeSubstring(subFinal).
                    toASN1OctetString();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
    }

    return normalizedSubFinal;
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
   * Specifies the matching rule ID for this matched values filter.
   *
   * @param  matchingRuleID  The matching rule ID for this matched values
   *                         filter.
   */
  public void setMatchingRuleID(String matchingRuleID)
  {
    this.matchingRuleID = matchingRuleID;

    decoded      = false;
    matchingRule = null;
  }



  /**
   * Retrieves the matching rule for this matched values filter.
   *
   * @return  The matching rule for this matched values filter, or
   *          <CODE>null</CODE> if there is none.
   */
  public MatchingRule getMatchingRule()
  {
    if (matchingRule == null)
    {
      if (matchingRuleID != null)
      {
        matchingRule =
             DirectoryServer.getMatchingRule(toLowerCase(matchingRuleID));
      }
    }

    return matchingRule;
  }



  /**
   * Specifies the matching rule for this matched values filter.
   *
   * @param  matchingRule  The matching rule for this matched values filter.
   */
  public void setMatchingRule(MatchingRule matchingRule)
  {
    this.matchingRule = matchingRule;

    if (matchingRule == null)
    {
      matchingRuleID = null;
    }
    else
    {
      matchingRuleID = matchingRule.getNameOrOID();
    }

    decoded = false;
  }



  /**
   * Retrieves the approximate matching rule that should be used for this
   * matched values filter.
   *
   * @return  The approximate matching rule that should be used for this matched
   *          values filter, or <CODE>null</CODE> if there is none.
   */
  public ApproximateMatchingRule getApproximateMatchingRule()
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
  public EqualityMatchingRule getEqualityMatchingRule()
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
  public OrderingMatchingRule getOrderingMatchingRule()
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
  public SubstringMatchingRule getSubstringMatchingRule()
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
      getNormalizedSubInitialElement();
      getNormalizedSubAnyElements();
      getNormalizedSubFinalElement();
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
  public boolean valueMatches(AttributeType type, AttributeValue value)
  {
    fullyDecode();

    switch (matchType)
    {
      case EQUALITY_MATCH_TYPE:
        if ((attributeType != null) && (type != null) &&
            attributeType.equals(type) && (assertionValue != null) &&
            (value != null) && (equalityMatchingRule != null))
        {
          try
          {
            return equalityMatchingRule.areEqual(
                        assertionValue.getNormalizedValue(),
                        value.getNormalizedValue());
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            return false;
          }
        }
        else
        {
          return false;
        }


      case SUBSTRINGS_TYPE:
        if ((attributeType != null) && (type != null) &&
            attributeType.equals(type) && (substringMatchingRule != null))
        {
          try
          {
            ArrayList<ByteString> normalizedSubAnyBS =
                 new ArrayList<ByteString>(normalizedSubAny);

            return substringMatchingRule.valueMatchesSubstring(
                 value.getNormalizedValue(), normalizedSubInitial,
                 normalizedSubAnyBS, normalizedSubFinal);
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            return false;
          }
        }
        else
        {
          return false;
        }


      case GREATER_OR_EQUAL_TYPE:
        if ((attributeType != null) && (type != null) &&
            attributeType.equals(type) && (assertionValue != null) &&
            (value != null) && (orderingMatchingRule != null))
        {
          try
          {
            return (orderingMatchingRule.compareValues(
                         assertionValue.getNormalizedValue(),
                         value.getNormalizedValue()) >= 0);
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            return false;
          }
        }
        else
        {
          return false;
        }


      case LESS_OR_EQUAL_TYPE:
        if ((attributeType != null) && (type != null) &&
            attributeType.equals(type) && (assertionValue != null) &&
            (value != null) && (orderingMatchingRule != null))
        {
          try
          {
            return (orderingMatchingRule.compareValues(
                         assertionValue.getNormalizedValue(),
                         value.getNormalizedValue()) <= 0);
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            return false;
          }
        }
        else
        {
          return false;
        }


      case PRESENT_TYPE:
        return ((attributeType != null) && (type != null) &&
                attributeType.equals(type));


      case APPROXIMATE_MATCH_TYPE:
        if ((attributeType != null) && (type != null) &&
            attributeType.equals(type) && (assertionValue != null) &&
            (value != null) && (approximateMatchingRule != null))
        {
          try
          {
            ByteString nv1 =  approximateMatchingRule.normalizeValue(
                    assertionValue.getNormalizedValue());
            ByteString nv2 =  approximateMatchingRule.normalizeValue(
                    value.getNormalizedValue());

            return approximateMatchingRule.approximatelyMatch(nv1, nv2);
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            return false;
          }
        }
        else
        {
          return false;
        }


      case EXTENSIBLE_MATCH_TYPE:
        if ((assertionValue == null) || (value == null))
        {
          return false;
        }

        if (attributeType == null)
        {
          if (matchingRule == null)
          {
            return false;
          }

          try
          {
            ASN1OctetString nv1 =
                 matchingRule.normalizeValue(value.getValue()).
                      toASN1OctetString();
            ASN1OctetString nv2 =
                 matchingRule.normalizeValue(assertionValue.getValue()).
                      toASN1OctetString();

            return (matchingRule.valuesMatch(nv1, nv2) == ConditionResult.TRUE);
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            return false;
          }
        }
        else
        {
          if ((! attributeType.equals(type)) || (equalityMatchingRule == null))
          {
            return false;
          }

          try
          {
            return equalityMatchingRule.areEqual(
                        assertionValue.getNormalizedValue(),
                        value.getNormalizedValue());
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              debugCaught(DebugLogLevel.ERROR, e);
            }

            return false;
          }
        }


      default:
        return false;
    }
  }



  /**
   * Retrieves a string representation of this matched values filter, as an RFC
   * 2254-compliant filter string.
   *
   * @return  A string representation of this matched values filter.
   */
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
        buffer.append("(");
        buffer.append(rawAttributeType);
        buffer.append("=");
        RawFilter.valueToFilterString(buffer, rawAssertionValue);
        buffer.append(")");
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
        buffer.append("(");
        buffer.append(rawAttributeType);
        buffer.append(">=");
        RawFilter.valueToFilterString(buffer, rawAssertionValue);
        buffer.append(")");
        break;


      case LESS_OR_EQUAL_TYPE:
        buffer.append("(");
        buffer.append(rawAttributeType);
        buffer.append("<=");
        RawFilter.valueToFilterString(buffer, rawAssertionValue);
        buffer.append(")");
        break;


      case PRESENT_TYPE:
        buffer.append("(");
        buffer.append(rawAttributeType);
        buffer.append("=*)");
        break;


      case APPROXIMATE_MATCH_TYPE:
        buffer.append("(");
        buffer.append(rawAttributeType);
        buffer.append("~=");
        RawFilter.valueToFilterString(buffer, rawAssertionValue);
        buffer.append(")");
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
}

