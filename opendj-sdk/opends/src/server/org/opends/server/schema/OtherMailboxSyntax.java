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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
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



import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.messages.SchemaMessages.*;
import org.opends.messages.MessageBuilder;
import static org.opends.server.schema.SchemaConstants.*;


/**
 * This class implements the other mailbox attribute syntax, which consists of a
 * printable string component (the mailbox type) followed by a dollar sign and
 * an IA5 string component (the mailbox).  Equality and substring matching will
 * be allowed by default.
 */
public class OtherMailboxSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{
  // The default equality matching rule for this syntax.
  private EqualityMatchingRule defaultEqualityMatchingRule;

  // The default substring matching rule for this syntax.
  private SubstringMatchingRule defaultSubstringMatchingRule;



  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public OtherMailboxSyntax()
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
         DirectoryServer.getEqualityMatchingRule(EMR_CASE_IGNORE_LIST_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE.get(
          EMR_CASE_IGNORE_LIST_OID, SYNTAX_OTHER_MAILBOX_NAME));
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getSubstringMatchingRule(SMR_CASE_IGNORE_LIST_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE.get(
          SMR_CASE_IGNORE_LIST_OID, SYNTAX_OTHER_MAILBOX_NAME));
    }
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getSyntaxName()
  {
    return SYNTAX_OTHER_MAILBOX_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    return SYNTAX_OTHER_MAILBOX_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    return SYNTAX_OTHER_MAILBOX_DESCRIPTION;
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
    // Ordering matching is not allowed by default.
    return null;
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
    // Approximate matching is not allowed by default.
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
                                   MessageBuilder invalidReason)
  {
    // Check to see if the provided value was null.  If so, then that's not
    // acceptable.
    if (value == null)
    {

      invalidReason.append(ERR_ATTR_SYNTAX_OTHER_MAILBOX_EMPTY_VALUE.get());
      return false;
    }


    // Get the value as a string and determine its length.  If it is empty, then
    // that's not acceptable.
    String valueString = value.stringValue();
    int    valueLength = valueString.length();
    if (valueLength == 0)
    {

      invalidReason.append(ERR_ATTR_SYNTAX_OTHER_MAILBOX_EMPTY_VALUE.get());
      return false;
    }


    // Iterate through the characters in the vale until we find a dollar sign.
    // Every character up to that point must be a printable string character.
    int pos = 0;
    for ( ; pos < valueLength; pos++)
    {
      char c = valueString.charAt(pos);
      if (c == '$')
      {
        if (pos == 0)
        {

          invalidReason.append(ERR_ATTR_SYNTAX_OTHER_MAILBOX_NO_MBTYPE.get(
                  valueString));
          return false;
        }

        pos++;
        break;
      }
      else if (! PrintableString.isPrintableCharacter(c))
      {

        invalidReason.append(
                ERR_ATTR_SYNTAX_OTHER_MAILBOX_ILLEGAL_MBTYPE_CHAR.get(
                        valueString, String.valueOf(c), pos));
        return false;
      }
    }


    // Make sure there is at least one character left for the mailbox.
    if (pos >= valueLength)
    {

      invalidReason.append(ERR_ATTR_SYNTAX_OTHER_MAILBOX_NO_MAILBOX.get(
              valueString));
      return false;
    }


    // The remaining characters in the value must be IA5 (ASCII) characters.
    for ( ; pos < valueLength; pos++)
    {
      char c = valueString.charAt(pos);
      if (c != (c & 0x7F))
      {

        invalidReason.append(ERR_ATTR_SYNTAX_OTHER_MAILBOX_ILLEGAL_MB_CHAR.get(
                valueString, String.valueOf(c), pos));
        return false;
      }
    }


    // If we've gotten here, then the value is OK.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isBinary()
  {
    return false;
  }
}

