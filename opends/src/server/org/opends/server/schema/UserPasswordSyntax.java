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



import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines an attribute syntax used for storing values that have been
 * encoded using a password storage scheme.  The format for attribute values
 * with this syntax is the concatenation of the following elements in the given
 * order:
 * <BR>
 * <UL>
 *   <LI>An opening curly brace ("{") character.</LI>
 *   <LI>The name of the storage scheme used to encode the value.</LI>
 *   <LI>A closing curly brace ("}") character.</LI>
 *   <LI>The encoded value.</LI>
 * </UL>
 */
public class UserPasswordSyntax
       extends AttributeSyntax
{



  // The default equality matching rule for this syntax.
  private EqualityMatchingRule defaultEqualityMatchingRule;



  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public UserPasswordSyntax()
  {
    super();

  }



  /**
   * Initializes this attribute syntax based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this attribute syntax.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   */
  public void initializeSyntax(ConfigEntry configEntry)
         throws ConfigException
  {
    defaultEqualityMatchingRule =
         DirectoryServer.getEqualityMatchingRule(EMR_USER_PASSWORD_EXACT_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE,
               EMR_USER_PASSWORD_EXACT_NAME, SYNTAX_USER_PASSWORD_NAME);
    }
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getSyntaxName()
  {
    return SYNTAX_USER_PASSWORD_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    return SYNTAX_USER_PASSWORD_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    return SYNTAX_USER_PASSWORD_DESCRIPTION;
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
                                   StringBuilder invalidReason)
  {
    // We have to accept any value here because in many cases the value will not
    // have been encoded by the time this method is called.
    return true;
  }



  /**
   * Decodes the provided user password value into its component parts.
   *
   * @param  userPasswordValue  The user password value to be decoded.
   *
   * @return  A two-element string array whose elements are the storage scheme
   *          name (in all lowercase characters) and the encoded value, in that
   *          order.
   *
   * @throws  DirectoryException  If a problem is encountered while attempting
   *                              to decode the value.
   */
  public static String[] decodeUserPassword(String userPasswordValue)
         throws DirectoryException
  {
    // Make sure that there actually is a value to decode.
    if ((userPasswordValue == null) || (userPasswordValue.length() == 0))
    {
      int    msgID   = MSGID_ATTR_SYNTAX_USERPW_NO_VALUE;
      String message = getMessage(msgID);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // The first character of an encoded value must be an opening curly brace.
    if (userPasswordValue.charAt(0) != '{')
    {
      int    msgID   = MSGID_ATTR_SYNTAX_USERPW_NO_OPENING_BRACE;
      String message = getMessage(msgID);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // There must be a corresponding closing brace.
    int closePos = userPasswordValue.indexOf('}');
    if (closePos < 0)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_USERPW_NO_CLOSING_BRACE;
      String message = getMessage(msgID);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // Get the storage scheme name and encoded value.
    String schemeName   = userPasswordValue.substring(1, closePos);
    String encodedValue = userPasswordValue.substring(closePos+1);

    if (schemeName.length() == 0)
    {
      int msgID = MSGID_ATTR_SYNTAX_USERPW_NO_SCHEME;
      String message = getMessage(msgID);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    return new String[] { toLowerCase(schemeName), encodedValue };
  }



  /**
   * Indicates whether the provided value is encoded using the user password
   * syntax.
   *
   * @param  value  The value for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the value appears to be encoded using the
   *          user password syntax, or <CODE>false</CODE> if not.
   */
  public static boolean isEncoded(ByteString value)
  {
    // If the value is null or empty, then it's not.
    byte[] valueBytes;
    if ((value == null) || ((valueBytes = value.value()).length == 0))
    {
      return false;
    }


    // If the value doesn't start with an opening curly brace, then it's not.
    if (valueBytes[0] != '{')
    {
      return false;
    }


    // There must be a corresponding closing curly brace, and there must be at
    // least one character inside the brace.
    int closingBracePos = -1;
    for (int i=1; i < valueBytes.length; i++)
    {
      if (valueBytes[i] == '}')
      {
        closingBracePos = i;
        break;
      }
    }

    if ((closingBracePos < 0) || (closingBracePos == 1))
    {
      return false;
    }


    // The closing curly brace must not be the last character of the password.
    if (closingBracePos == (valueBytes.length - 1))
    {
      return false;
    }


    // If we've gotten here, then it looks to be encoded.
    return true;
  }
}

