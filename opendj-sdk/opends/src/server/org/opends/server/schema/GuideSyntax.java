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
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements the guide attribute syntax, which may be used to
 * provide criteria for generating search filters for entries, optionally tied
 * to a specified objectclass.
 */
public class GuideSyntax
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
  public GuideSyntax()
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
         DirectoryServer.getEqualityMatchingRule(EMR_OCTET_STRING_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE,
               EMR_OCTET_STRING_OID, SYNTAX_GUIDE_NAME);
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getOrderingMatchingRule(OMR_OCTET_STRING_OID);
    if (defaultOrderingMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE,
               OMR_OCTET_STRING_OID, SYNTAX_GUIDE_NAME);
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getSubstringMatchingRule(SMR_OCTET_STRING_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE,
               SMR_OCTET_STRING_OID, SYNTAX_GUIDE_NAME);
    }
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getSyntaxName()
  {
    return SYNTAX_GUIDE_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    return SYNTAX_GUIDE_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    return SYNTAX_GUIDE_DESCRIPTION;
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
    // Get a lowercase string version of the provided value.
    String valueStr = toLowerCase(value.stringValue());


    // Find the position of the octothorpe.  If there isn't one, then the entire
    // value should be the criteria.
    int sharpPos = valueStr.indexOf('#');
    if (sharpPos < 0)
    {
      return criteriaIsValid(valueStr, valueStr, invalidReason);
    }


    // Get the objectclass and see if it is a valid name or OID.
    String ocName   = valueStr.substring(0, sharpPos).trim();
    int    ocLength = ocName.length();
    if (ocLength == 0)
    {
      int msgID = MSGID_ATTR_SYNTAX_GUIDE_NO_OC;
      invalidReason.append(getMessage(msgID, valueStr));
      return false;
    }

    if (! isValidSchemaElement(ocName, 0, ocLength, invalidReason))
    {
      return false;
    }


    // The rest of the value must be the criteria.
    return criteriaIsValid(valueStr.substring(sharpPos+1), valueStr,
                           invalidReason);
  }



  /**
   * Determines whether the provided string represents a valid criteria
   * according to the guide syntax.
   *
   * @param  criteria       The portion of the criteria for which to make the
   *                        determination.
   * @param  valueStr       The complete guide value provided by the client.
   * @param  invalidReason  The buffer to which to append the reason that the
   *                        criteria is invalid if a problem is found.
   *
   * @return  <CODE>true</CODE> if the provided string does contain a valid
   *          criteria, or <CODE>false</CODE> if not.
   */
  public static boolean criteriaIsValid(String criteria, String valueStr,
                                        StringBuilder invalidReason)
  {
    // See if the criteria starts with a '!'.  If so, then just evaluate
    // everything after that as a criteria.
    char c = criteria.charAt(0);
    if (c == '!')
    {
      return criteriaIsValid(criteria.substring(1), valueStr, invalidReason);
    }


    // See if the criteria starts with a '('.  If so, then find the
    // corresponding ')' and parse what's in between as a criteria.
    if (c == '(')
    {
      int length = criteria.length();
      int depth  = 1;

      for (int i=1; i < length; i++)
      {
        c = criteria.charAt(i);
        if (c == ')')
        {
          depth--;
          if (depth == 0)
          {
            String subCriteria = criteria.substring(1, i);
            if (! criteriaIsValid(subCriteria, valueStr, invalidReason))
            {
              return false;
            }

            // If we are at the end of the value, then it was valid.  Otherwise,
            // the next character must be a pipe or an ampersand followed by
            // another set of criteria.
            if (i == (length-1))
            {
              return true;
            }
            else
            {
              c = criteria.charAt(i+1);
              if ((c == '|') || (c == '&'))
              {
                return criteriaIsValid(criteria.substring(i+2), valueStr,
                                       invalidReason);
              }
              else
              {
                int msgID = MSGID_ATTR_SYNTAX_GUIDE_ILLEGAL_CHAR;
                invalidReason.append(getMessage(msgID, valueStr, criteria, c,
                                                (i+1)));
                return false;
              }
            }
          }
        }
        else if (c == '(')
        {
          depth++;
        }
      }


      // If we've gotten here, then we went through the entire value without
      // finding the appropriate closing parenthesis.
      int msgID = MSGID_ATTR_SYNTAX_GUIDE_MISSING_CLOSE_PAREN;
      invalidReason.append(getMessage(msgID, valueStr, criteria));
      return false;
    }


    // See if the criteria starts with a '?'.  If so, then it must be either
    // "?true" or "?false".
    if (c == '?')
    {
      if (criteria.startsWith("?true"))
      {
        if (criteria.length() == 5)
        {
          return true;
        }
        else
        {
          // The only characters allowed next are a pipe or an ampersand.
          c = criteria.charAt(5);
          if ((c == '|') || (c == '&'))
          {
            return criteriaIsValid(criteria.substring(6), valueStr,
                                   invalidReason);
          }
          else
          {
            int msgID = MSGID_ATTR_SYNTAX_GUIDE_ILLEGAL_CHAR;
            invalidReason.append(getMessage(msgID, valueStr, criteria, c, 5));
            return false;
          }
        }
      }
      else if (criteria.startsWith("?false"))
      {
        if (criteria.length() == 6)
        {
          return true;
        }
        else
        {
          // The only characters allowed next are a pipe or an ampersand.
          c = criteria.charAt(6);
          if ((c == '|') || (c == '&'))
          {
            return criteriaIsValid(criteria.substring(7), valueStr,
                                   invalidReason);
          }
          else
          {
            int msgID = MSGID_ATTR_SYNTAX_GUIDE_ILLEGAL_CHAR;
            invalidReason.append(getMessage(msgID, valueStr, criteria, c, 6));
            return false;
          }
        }
      }
      else
      {
        int msgID = MSGID_ATTR_SYNTAX_GUIDE_INVALID_QUESTION_MARK;
        invalidReason.append(getMessage(msgID, valueStr, criteria));
        return false;
      }
    }


    // See if the criteria is either "true" or "false".  If so, then it is
    // valid.
    if (criteria.equals("true") || criteria.equals("false"))
    {
      return true;
    }


    // The only thing that will be allowed is an attribute type name or OID
    // followed by a dollar sign and a match type.  Find the dollar sign and
    // verify whether the value before it is a valid attribute type name or OID.
    int dollarPos = criteria.indexOf('$');
    if (dollarPos < 0)
    {
      int msgID = MSGID_ATTR_SYNTAX_GUIDE_NO_DOLLAR;
      invalidReason.append(getMessage(msgID, valueStr, criteria));
      return false;
    }
    else if (dollarPos == 0)
    {
      int msgID = MSGID_ATTR_SYNTAX_GUIDE_NO_ATTR;
      invalidReason.append(getMessage(msgID, valueStr, criteria));
      return false;
    }
    else if (dollarPos == (criteria.length()-1))
    {
      int msgID = MSGID_ATTR_SYNTAX_GUIDE_NO_MATCH_TYPE;
      invalidReason.append(getMessage(msgID, valueStr, criteria));
      return false;
    }
    else
    {
      if (! isValidSchemaElement(criteria, 0, dollarPos, invalidReason))
      {
        return false;
      }
    }


    // The substring immediately after the dollar sign must be one of "eq",
    // "substr", "ge", "le", or "approx".  It may be followed by the end of the
    // value, a pipe, or an ampersand.
    int endPos;
    c = criteria.charAt(dollarPos+1);
    switch (c)
    {
      case 'e':
        if (criteria.startsWith("eq", dollarPos+1))
        {
          endPos = dollarPos + 3;
          break;
        }
        else
        {
          int msgID = MSGID_ATTR_SYNTAX_GUIDE_INVALID_MATCH_TYPE;
          invalidReason.append(getMessage(msgID, valueStr, criteria,
                                          dollarPos+1));
          return false;
        }

      case 's':
        if (criteria.startsWith("substr", dollarPos+1))
        {
          endPos = dollarPos + 7;
          break;
        }
        else
        {
          int msgID = MSGID_ATTR_SYNTAX_GUIDE_INVALID_MATCH_TYPE;
          invalidReason.append(getMessage(msgID, valueStr, criteria,
                                          dollarPos+1));
          return false;
        }

      case 'g':
        if (criteria.startsWith("ge", dollarPos+1))
        {
          endPos = dollarPos + 3;
          break;
        }
        else
        {
          int msgID = MSGID_ATTR_SYNTAX_GUIDE_INVALID_MATCH_TYPE;
          invalidReason.append(getMessage(msgID, valueStr, criteria,
                                          dollarPos+1));
          return false;
        }

      case 'l':
        if (criteria.startsWith("le", dollarPos+1))
        {
          endPos = dollarPos + 3;
          break;
        }
        else
        {
          int msgID = MSGID_ATTR_SYNTAX_GUIDE_INVALID_MATCH_TYPE;
          invalidReason.append(getMessage(msgID, valueStr, criteria,
                                          dollarPos+1));
          return false;
        }

      case 'a':
        if (criteria.startsWith("approx", dollarPos+1))
        {
          endPos = dollarPos + 7;
          break;
        }
        else
        {
          int msgID = MSGID_ATTR_SYNTAX_GUIDE_INVALID_MATCH_TYPE;
          invalidReason.append(getMessage(msgID, valueStr, criteria,
                                          dollarPos+1));
          return false;
        }

      default:
        int msgID = MSGID_ATTR_SYNTAX_GUIDE_INVALID_MATCH_TYPE;
        invalidReason.append(getMessage(msgID, valueStr, criteria,
                                        dollarPos+1));
        return false;
    }


    // See if we are at the end of the value.  If so, then it is valid.
    // Otherwise, the next character must be a pipe or an ampersand.
    if (endPos >= criteria.length())
    {
      return true;
    }
    else
    {
      c = criteria.charAt(endPos);
      if ((c == '|') || (c == '&'))
      {
        return criteriaIsValid(criteria.substring(endPos+1), valueStr,
                               invalidReason);
      }
      else
      {
        int msgID = MSGID_ATTR_SYNTAX_GUIDE_ILLEGAL_CHAR;
        invalidReason.append(getMessage(msgID, valueStr, criteria, c, endPos));
        return false;
      }
    }
  }
}

