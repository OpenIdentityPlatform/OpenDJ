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
 *      Portions Copyright 2012-2014 ForgeRock AS
 */
package org.opends.server.schema;



import java.util.HashSet;
import org.forgerock.i18n.slf4j.LocalizedLogger;

import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.ldap.ByteSequence;


import static org.opends.messages.SchemaMessages.*;
import org.forgerock.i18n.LocalizableMessageBuilder;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the facsimile telephone number attribute syntax, which
 * contains a printable string (the number) followed by zero or more parameters.
 * Those parameters should start with a dollar sign may be any of the following
 * strings:
 * <UL>
 *   <LI>twoDimensional</LI>
 *   <LI>fineResolution</LI>
 *   <LI>unlimitedLength</LI>
 *   <LI>b4Length</LI>
 *   <LI>a3Width</LI>
 *   <LI>b4Width</LI>
 *   <LI>uncompressed</LI>
 * </UL>
 */
public class FaxNumberSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The set of allowed fax parameter values, formatted entirely in lowercase
   * characters.
   */
  public static final HashSet<String> ALLOWED_FAX_PARAMETERS =
       new HashSet<String>(7);

  static
  {
    ALLOWED_FAX_PARAMETERS.add("twodimensional");
    ALLOWED_FAX_PARAMETERS.add("fineresolution");
    ALLOWED_FAX_PARAMETERS.add("unlimitedlength");
    ALLOWED_FAX_PARAMETERS.add("b4length");
    ALLOWED_FAX_PARAMETERS.add("a3width");
    ALLOWED_FAX_PARAMETERS.add("b4width");
    ALLOWED_FAX_PARAMETERS.add("uncompressed");
  }



  // The default equality matching rule for this syntax.
  private MatchingRule defaultEqualityMatchingRule;

  // The default ordering matching rule for this syntax.
  private MatchingRule defaultOrderingMatchingRule;

  // The default substring matching rule for this syntax.
  private MatchingRule defaultSubstringMatchingRule;



  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public FaxNumberSyntax()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  public void initializeSyntax(AttributeSyntaxCfg configuration)
         throws ConfigException
  {
    defaultEqualityMatchingRule =
         DirectoryServer.getMatchingRule(EMR_CASE_IGNORE_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE, EMR_CASE_IGNORE_OID, SYNTAX_FAXNUMBER_NAME);
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getMatchingRule(OMR_CASE_IGNORE_OID);
    if (defaultOrderingMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE, OMR_CASE_IGNORE_OID, SYNTAX_FAXNUMBER_NAME);
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getMatchingRule(SMR_CASE_IGNORE_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE, SMR_CASE_IGNORE_OID, SYNTAX_FAXNUMBER_NAME);
    }
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getName()
  {
    return SYNTAX_FAXNUMBER_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    return SYNTAX_FAXNUMBER_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    return SYNTAX_FAXNUMBER_DESCRIPTION;
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
    return defaultSubstringMatchingRule;
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
    // Get a lowercase string representation of the value and find its length.
    String valueString = toLowerCase(value.toString());
    int    valueLength = valueString.length();


    // The value must contain at least one character.
    if (valueLength == 0)
    {
      invalidReason.append(ERR_ATTR_SYNTAX_FAXNUMBER_EMPTY.get());
      return false;
    }


    // The first character must be a printable string character.
    char c = valueString.charAt(0);
    if (! PrintableString.isPrintableCharacter(c))
    {
      invalidReason.append(ERR_ATTR_SYNTAX_FAXNUMBER_NOT_PRINTABLE.get(valueString, c, 0));
      return false;
    }


    // Continue reading until we find a dollar sign or the end of the string.
    // Every intermediate character must be a printable string character.
    int pos = 1;
    for ( ; pos < valueLength; pos++)
    {
      c = valueString.charAt(pos);
      if (c == '$')
      {
        pos++;
        break;
      }
      else
      {
        if (! PrintableString.isPrintableCharacter(c))
        {
          invalidReason.append(ERR_ATTR_SYNTAX_FAXNUMBER_NOT_PRINTABLE.get(valueString, c, pos));
        }
      }
    }

    if (pos >= valueLength)
    {
      // We're at the end of the value, so it must be valid unless the last
      // character was a dollar sign.
      if (c == '$')
      {
        invalidReason.append(ERR_ATTR_SYNTAX_FAXNUMBER_END_WITH_DOLLAR.get(
                valueString));
        return false;
      }
      else
      {
        return true;
      }
    }


    // Continue reading until we find the end of the string.  Each substring
    // must be a valid fax parameter.
    int paramStartPos = pos;
    while (pos < valueLength)
    {
      c = valueString.charAt(pos++);
      if (c == '$')
      {
        String paramStr = valueString.substring(paramStartPos, pos);
        if (! ALLOWED_FAX_PARAMETERS.contains(paramStr))
        {
          invalidReason.append(ERR_ATTR_SYNTAX_FAXNUMBER_ILLEGAL_PARAMETER.get(
                  valueString, paramStr, paramStartPos, (pos-1)));
          return false;
        }

        paramStartPos = pos;
      }
    }


    // We must be at the end of the value.  Read the last parameter and make
    // sure it is valid.
    String paramStr = valueString.substring(paramStartPos);
    if (! ALLOWED_FAX_PARAMETERS.contains(paramStr))
    {
      invalidReason.append(ERR_ATTR_SYNTAX_FAXNUMBER_ILLEGAL_PARAMETER.get(
              valueString, paramStr, paramStartPos, (pos-1)));
      return false;
    }


    // If we've gotten here, then the value must be valid.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isBEREncodingRequired()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isHumanReadable()
  {
    return true;
  }
}

