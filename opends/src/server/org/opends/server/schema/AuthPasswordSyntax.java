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
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;


import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.messages.SchemaMessages.*;
import org.opends.messages.MessageBuilder;
import static org.opends.server.schema.SchemaConstants.*;


/**
 * This class defines the auth password attribute syntax, which is defined in
 * RFC 3112 and is used to hold authentication information.  Only equality
 * matching will be allowed by default.
 */
public class AuthPasswordSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{
  // The default equality matching rule for this syntax.
  private EqualityMatchingRule defaultEqualityMatchingRule;



  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public AuthPasswordSyntax()
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
         DirectoryServer.getEqualityMatchingRule(EMR_AUTH_PASSWORD_EXACT_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE.get(
          EMR_AUTH_PASSWORD_EXACT_NAME, SYNTAX_AUTH_PASSWORD_NAME));
    }
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getSyntaxName()
  {
    return SYNTAX_AUTH_PASSWORD_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    return SYNTAX_AUTH_PASSWORD_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    return SYNTAX_AUTH_PASSWORD_DESCRIPTION;
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
    // There is no ordering matching rule by default.
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
    try
    {
      decodeAuthPassword(value.stringValue());
      return true;
    }
    catch (DirectoryException de)
    {
      invalidReason.append(de.getMessageObject());
      return false;
    }
  }



  /**
   * Decodes the provided authentication password value into its component
   * parts.
   *
   * @param  authPasswordValue  The authentication password value to be decoded.
   *
   * @return  A three-element array, containing the scheme, authInfo, and
   *          authValue components of the given string, in that order.
   *
   * @throws  DirectoryException  If a problem is encountered while attempting
   *                              to decode the value.
   */
  public static StringBuilder[] decodeAuthPassword(String authPasswordValue)
         throws DirectoryException
  {
    // Create placeholders for the values to return.
    StringBuilder scheme    = new StringBuilder();
    StringBuilder authInfo  = new StringBuilder();
    StringBuilder authValue = new StringBuilder();


    // First, ignore any leading whitespace.
    int length = authPasswordValue.length();
    int  pos   = 0;
    while ((pos < length) && (authPasswordValue.charAt(pos) == ' '))
    {
      pos++;
    }


    // The next set of characters will be the scheme, which must consist only
    // of digits, uppercase alphabetic characters, dash, period, slash, and
    // underscore characters.  It must be immediately followed by one or more
    // spaces or a dollar sign.
readScheme:
    while (pos < length)
    {
      char c = authPasswordValue.charAt(pos);

      switch (c)
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
        case 'A':
        case 'B':
        case 'C':
        case 'D':
        case 'E':
        case 'F':
        case 'G':
        case 'H':
        case 'I':
        case 'J':
        case 'K':
        case 'L':
        case 'M':
        case 'N':
        case 'O':
        case 'P':
        case 'Q':
        case 'R':
        case 'S':
        case 'T':
        case 'U':
        case 'V':
        case 'W':
        case 'X':
        case 'Y':
        case 'Z':
        case '-':
        case '.':
        case '/':
        case '_':
          scheme.append(c);
          pos++;
          break;
        case ' ':
        case '$':
          break readScheme;
        default:
          Message message = ERR_ATTR_SYNTAX_AUTHPW_INVALID_SCHEME_CHAR.get(pos);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
      }
    }


    // The scheme must consist of at least one character.
    if (scheme.length() == 0)
    {
      Message message = ERR_ATTR_SYNTAX_AUTHPW_NO_SCHEME.get();
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
              message);
    }


    // Ignore any spaces before the dollar sign separator.  Then read the dollar
    // sign and ignore any trailing spaces.
    while ((pos < length) && (authPasswordValue.charAt(pos) == ' '))
    {
      pos++;
    }

    if ((pos < length) && (authPasswordValue.charAt(pos) == '$'))
    {
      pos++;
    }
    else
    {
      Message message = ERR_ATTR_SYNTAX_AUTHPW_NO_SCHEME_SEPARATOR.get();
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
              message);
    }

    while ((pos < length) && (authPasswordValue.charAt(pos) == ' '))
    {
      pos++;
    }


    // The next component must be the authInfo element, containing only
    // printable characters other than the dollar sign and space character.
readAuthInfo:
    while (pos < length)
    {
      char c = authPasswordValue.charAt(pos);
      if ((c == ' ') || (c == '$'))
      {
        break readAuthInfo;
      }
      else if (PrintableString.isPrintableCharacter(c))
      {
        authInfo.append(c);
        pos++;
      }
      else
      {
        Message message =
            ERR_ATTR_SYNTAX_AUTHPW_INVALID_AUTH_INFO_CHAR.get(pos);
        throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                     message);
      }
    }


    // The authInfo element must consist of at least one character.
    if (scheme.length() == 0)
    {
      Message message = ERR_ATTR_SYNTAX_AUTHPW_NO_AUTH_INFO.get();
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // Ignore any spaces before the dollar sign separator.  Then read the dollar
    // sign and ignore any trailing spaces.
    while ((pos < length) && (authPasswordValue.charAt(pos) == ' '))
    {
      pos++;
    }

    if ((pos < length) && (authPasswordValue.charAt(pos) == '$'))
    {
      pos++;
    }
    else
    {
      Message message = ERR_ATTR_SYNTAX_AUTHPW_NO_AUTH_INFO_SEPARATOR.get();
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }

    while ((pos < length) && (authPasswordValue.charAt(pos) == ' '))
    {
      pos++;
    }


    // The final component must be the authValue element, containing only
    // printable characters other than the dollar sign and space character.
readAuthValue:
    while (pos < length)
    {
      char c = authPasswordValue.charAt(pos);
      if ((c == ' ') || (c == '$'))
      {
        break ;
      }
      else if (PrintableString.isPrintableCharacter(c))
      {
        authValue.append(c);
        pos++;
      }
      else
      {
        Message message =
            ERR_ATTR_SYNTAX_AUTHPW_INVALID_AUTH_VALUE_CHAR.get(pos);
        throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                     message);
      }
    }


    // The authValue element must consist of at least one character.
    if (scheme.length() == 0)
    {
      Message message = ERR_ATTR_SYNTAX_AUTHPW_NO_AUTH_VALUE.get();
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // The only characters remaining must be whitespace.
    while (pos < length)
    {
      char c = authPasswordValue.charAt(pos);
      if (c == ' ')
      {
        pos++;
      }
      else
      {
        Message message = ERR_ATTR_SYNTAX_AUTHPW_INVALID_TRAILING_CHAR.get(pos);
        throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                     message);
      }
    }


    // If we've gotten here, then everything must be OK.
    return new StringBuilder[]
    {
      scheme,
      authInfo,
      authValue
    };
  }



  /**
   * Indicates whether the provided value is encoded using the auth password
   * syntax.
   *
   * @param  value  The value for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the value appears to be encoded using the
   *          auth password syntax, or <CODE>false</CODE> if not.
   */
  public static boolean isEncoded(ByteString value)
  {
    // FIXME -- Make this more efficient, and don't use exceptions for flow
    // control.


    try
    {
      decodeAuthPassword(value.stringValue());
      return true;
    }
    catch (Exception e)
    {
      return false;
    }
  }
}

