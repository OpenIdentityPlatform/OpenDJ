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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2015 ForgeRock AS
 */
package org.opends.server.schema;

import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.ldap.ByteSequence;


import static org.opends.messages.SchemaMessages.*;
import org.forgerock.i18n.LocalizableMessageBuilder;
import static org.opends.server.schema.SchemaConstants.*;


/**
 * This class implements the UUID syntax, which is defined in RFC 4530.
 * Equality and ordering matching will be allowed by default.
 */
public class UUIDSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The default equality matching rule for this syntax. */
  private MatchingRule defaultEqualityMatchingRule;

  /** The default ordering matching rule for this syntax. */
  private MatchingRule defaultOrderingMatchingRule;

  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public UUIDSyntax()
  {
    super();
  }

  /** {@inheritDoc} */
  public void initializeSyntax(AttributeSyntaxCfg configuration)
         throws ConfigException
  {
    defaultEqualityMatchingRule =
         DirectoryServer.getMatchingRule(EMR_UUID_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE, EMR_UUID_OID, SYNTAX_UUID_NAME);
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getMatchingRule(OMR_UUID_OID);
    if (defaultOrderingMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE, OMR_UUID_OID, SYNTAX_UUID_NAME);
    }
  }

  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getName()
  {
    return SYNTAX_UUID_NAME;
  }

  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    return SYNTAX_UUID_OID;
  }

  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    return SYNTAX_UUID_DESCRIPTION;
  }

  /**
   * Retrieves the default equality matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default equality matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if equality
   *          matches will not be allowed for this type by default.
   */
  public MatchingRule getEqualityMatchingRule()
  {
    return defaultEqualityMatchingRule;
  }

  /**
   * Retrieves the default ordering matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default ordering matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if ordering
   *          matches will not be allowed for this type by default.
   */
  public MatchingRule getOrderingMatchingRule()
  {
    return defaultOrderingMatchingRule;
  }

  /**
   * Retrieves the default substring matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default substring matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if substring
   *          matches will not be allowed for this type by default.
   */
  public MatchingRule getSubstringMatchingRule()
  {
    // There is no substring matching rule by default.
    return null;
  }

  /**
   * Retrieves the default approximate matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default approximate matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if approximate
   *          matches will not be allowed for this type by default.
   */
  public MatchingRule getApproximateMatchingRule()
  {
    // There is no approximate matching rule by default.
    return null;
  }

  /**
   * Indicates whether the provided value is acceptable for use in an attribute
   * with this syntax.  If it is not, then the reason may be appended to the
   * provided buffer.
   *
   * @param  value          The value for which to make the determination.
   * @param  invalidReason  The buffer to which the invalid reason should be
   *                        appended.
   *
   * @return  <CODE>true</CODE> if the provided value is acceptable for use with
   *          this syntax, or <CODE>false</CODE> if not.
   */
  public boolean valueIsAcceptable(ByteSequence value,
                                   LocalizableMessageBuilder invalidReason)
  {
    // We will only accept values that look like valid UUIDs.  This means that
    // all values must be in the form HHHHHHHH-HHHH-HHHH-HHHH-HHHHHHHHHHHH,
    // where "H" represents a hexadecimal digit.  First, make sure that the
    // value is exactly 36 bytes long.
    String valueString = value.toString();
    if (valueString.length() != 36)
    {
      invalidReason.append(WARN_ATTR_SYNTAX_UUID_INVALID_LENGTH.get(valueString,
                                      valueString.length()));
      return false;
    }

    // Next, iterate through each character.  Make sure that the 9th, 14th,
    // 19th, and 24th characters are dashes and the rest are hex digits.
    for (int i=0; i < 36; i++)
    {
      switch (i)
      {
        case 8:
        case 13:
        case 18:
        case 23:
          if (valueString.charAt(i) != '-')
          {
            invalidReason.append(WARN_ATTR_SYNTAX_UUID_EXPECTED_DASH.get(
                    valueString, i, valueString.charAt(i)));
            return false;
          }
          break;
        default:
          switch (valueString.charAt(i))
          {
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
            case 'a':
            case 'b':
            case 'c':
            case 'd':
            case 'e':
            case 'f':
            case 'A':
            case 'B':
            case 'C':
            case 'D':
            case 'E':
            case 'F':
              break;
            default:
              invalidReason.append(WARN_ATTR_SYNTAX_UUID_EXPECTED_HEX.get(
                      valueString, i, valueString.charAt(i)));
              return false;
          }
      }
    }


    // If we've gotten here, then the value is acceptable.
    return true;
  }

  /** {@inheritDoc} */
  public boolean isBEREncodingRequired()
  {
    return false;
  }

  /** {@inheritDoc} */
  public boolean isHumanReadable()
  {
    return true;
  }
}

