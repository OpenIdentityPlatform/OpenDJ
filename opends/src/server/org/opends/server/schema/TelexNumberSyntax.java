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
package org.opends.server.schema;



import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ByteString;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;

import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;



/**
 * This class implements the telex number attribute syntax, which contains three
 * printable strings separated by dollar sign characters.  Equality, ordering,
 * and substring matching will be allowed by default.
 */
public class TelexNumberSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{
  // The default equality matching rule for this syntax.
  private EqualityMatchingRule defaultEqualityMatchingRule;

  // The default ordering matching rule for this syntax.
  private OrderingMatchingRule defaultOrderingMatchingRule;

  // The default substring matching rule for this syntax.
  private SubstringMatchingRule defaultSubstringMatchingRule;



  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public TelexNumberSyntax()
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
         DirectoryServer.getEqualityMatchingRule(EMR_CASE_IGNORE_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE,
               EMR_CASE_IGNORE_OID, SYNTAX_TELEX_NAME);
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getOrderingMatchingRule(OMR_CASE_IGNORE_OID);
    if (defaultOrderingMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE,
               OMR_CASE_IGNORE_OID, SYNTAX_TELEX_NAME);
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getSubstringMatchingRule(SMR_CASE_IGNORE_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE,
               SMR_CASE_IGNORE_OID, SYNTAX_TELEX_NAME);
    }
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getSyntaxName()
  {
    return SYNTAX_TELEX_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    return SYNTAX_TELEX_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    return SYNTAX_TELEX_DESCRIPTION;
  }



  /**
   * Retrieves the default equality matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default equality matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if equality
   *          matches will not be allowed for this type by default.
   */
  public EqualityMatchingRule getEqualityMatchingRule()
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
  public OrderingMatchingRule getOrderingMatchingRule()
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
  public SubstringMatchingRule getSubstringMatchingRule()
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
  public ApproximateMatchingRule getApproximateMatchingRule()
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
  public boolean valueIsAcceptable(ByteString value,
                                   StringBuilder invalidReason)
  {
    // Get a string representation of the value and find its length.
    String valueString = value.stringValue();
    int    valueLength = valueString.length();

    if (valueLength < 5)
    {
      int msgID = MSGID_ATTR_SYNTAX_TELEX_TOO_SHORT;
      invalidReason.append(getMessage(msgID, valueString));
      return false;
    }


    // The first character must be a printable string character.
    char c = valueString.charAt(0);
    if (! PrintableString.isPrintableCharacter(c))
    {
      int msgID = MSGID_ATTR_SYNTAX_TELEX_NOT_PRINTABLE;
      invalidReason.append(getMessage(msgID, valueString, c, 0));
      return false;
    }


    // Continue reading until we find a dollar sign.  Every intermediate
    // character must be a printable string character.
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
          int msgID = MSGID_ATTR_SYNTAX_TELEX_ILLEGAL_CHAR;
          invalidReason.append(getMessage(msgID, valueString, c, pos));
        }
      }
    }

    if (pos >= valueLength)
    {
      int msgID = MSGID_ATTR_SYNTAX_TELEX_TRUNCATED;
      invalidReason.append(getMessage(msgID, valueString));
      return false;
    }


    // The next character must be a printable string character.
    c = valueString.charAt(pos++);
    if (! PrintableString.isPrintableCharacter(c))
    {
      int msgID = MSGID_ATTR_SYNTAX_TELEX_NOT_PRINTABLE;
      invalidReason.append(getMessage(msgID, valueString, c, (pos-1)));
      return false;
    }


    // Continue reading until we find another dollar sign.  Every intermediate
    // character must be a printable string character.
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
          int msgID = MSGID_ATTR_SYNTAX_TELEX_ILLEGAL_CHAR;
          invalidReason.append(getMessage(msgID, valueString, c, pos));
          return false;
        }
      }
    }

    if (pos >= valueLength)
    {
      int msgID = MSGID_ATTR_SYNTAX_TELEX_TRUNCATED;
      invalidReason.append(getMessage(msgID, valueString));
      return false;
    }


    // The next character must be a printable string character.
    c = valueString.charAt(pos++);
    if (! PrintableString.isPrintableCharacter(c))
    {
      int msgID = MSGID_ATTR_SYNTAX_TELEX_NOT_PRINTABLE;
      invalidReason.append(getMessage(msgID, valueString, c, (pos-1)));
      return false;
    }


    // Continue reading until the end of the value.  Every intermediate
    // character must be a printable string character.
    for ( ; pos < valueLength; pos++)
    {
      c = valueString.charAt(pos);
      if (! PrintableString.isPrintableCharacter(c))
      {
        int msgID = MSGID_ATTR_SYNTAX_TELEX_ILLEGAL_CHAR;
        invalidReason.append(getMessage(msgID, valueString, c, pos));
        return false;
      }
    }


    // If we've gotten here, then we're at the end of the value and it is
    // acceptable.
    return true;
  }
}

