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
import org.opends.messages.Message;



import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.AttributeValueDecoder;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;


import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.messages.SchemaMessages.*;
import org.opends.messages.MessageBuilder;
import static org.opends.server.schema.SchemaConstants.*;


/**
 * This class defines the integer attribute syntax, which holds an
 * arbitrarily-long integer value.  Equality, ordering, and substring matching
 * will be allowed by default.
 */
public class IntegerSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{
  // The default equality matching rule for this syntax.
  private EqualityMatchingRule defaultEqualityMatchingRule;

  // The default ordering matching rule for this syntax.
  private OrderingMatchingRule defaultOrderingMatchingRule;

  // The default substring matching rule for this syntax.
  private SubstringMatchingRule defaultSubstringMatchingRule;



  /**
   * An {@link Integer} attribute value decoder for this syntax.
   */
  public static final AttributeValueDecoder<Integer> DECODER =
    new AttributeValueDecoder<Integer>()
  {
    /**
     * {@inheritDoc}
     */
    public Integer decode(AttributeValue value) throws DirectoryException
    {
      ByteString nvalue = value.getNormalizedValue();
      try
      {
        return Integer.valueOf(nvalue.stringValue());
      }
      catch (NumberFormatException e)
      {
        Message message =
            WARN_ATTR_SYNTAX_ILLEGAL_INTEGER.get(nvalue.stringValue());
        throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
            message);
      }
    }
  };



  /**
   * Creates a new instance of this syntax. Note that the only thing
   * that should be done here is to invoke the default constructor for
   * the superclass. All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public IntegerSyntax()
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
         DirectoryServer.getEqualityMatchingRule(EMR_INTEGER_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE.get(
          EMR_INTEGER_OID, SYNTAX_INTEGER_NAME));
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getOrderingMatchingRule(OMR_INTEGER_OID);
    if (defaultOrderingMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE.get(
          OMR_INTEGER_OID, SYNTAX_INTEGER_NAME));
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getSubstringMatchingRule(SMR_CASE_EXACT_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE.get(
          SMR_CASE_EXACT_OID, SYNTAX_INTEGER_NAME));
    }
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getSyntaxName()
  {
    return SYNTAX_INTEGER_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    return SYNTAX_INTEGER_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    return SYNTAX_INTEGER_DESCRIPTION;
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
                                   MessageBuilder invalidReason)
  {
    String valueString = value.stringValue();
    int    length      = valueString.length();

    if (length == 0)
    {
      invalidReason.append(
              WARN_ATTR_SYNTAX_INTEGER_EMPTY_VALUE.get(valueString));
      return false;
    }
    else if (length == 1)
    {
      switch (valueString.charAt(0))
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
          return true;
        case '-':
          invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_DASH_NEEDS_VALUE.get(
                  valueString));
          return false;
        default:
          invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_INVALID_CHARACTER.get(
                  valueString,
                  valueString.charAt(0), 0));
          return false;
      }
    }
    else
    {
      boolean negative = false;

      switch (valueString.charAt(0))
      {
        case '0':
          invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_INITIAL_ZERO.get(
                  valueString));
          return false;
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
          // These are all fine.
          break;
        case '-':
          // This is fine too.
          negative = true;
          break;
        default:
          invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_INVALID_CHARACTER.get(
                  valueString,
                  valueString.charAt(0), 0));
          return false;
      }

      switch (valueString.charAt(1))
      {
        case '0':
          // This is fine as long as the value isn't negative.
          if (negative)
          {
            invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_INITIAL_ZERO.get(
                    valueString));
            return false;
          }
          break;
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
          // These are all fine.
          break;
        default:
          invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_INVALID_CHARACTER.get(
                  valueString,
                  valueString.charAt(0), 0));
          return false;
      }

      for (int i=2; i < length; i++)
      {
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
            // These are all fine.
            break;
          default:
            invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_INVALID_CHARACTER.get(
                    valueString,
                    valueString.charAt(0), 0));
            return false;
        }
      }

      return true;
    }
  }



  /**
   * {@inheritDoc}
   */
  public boolean isBinary()
  {
    return false;
  }
}

