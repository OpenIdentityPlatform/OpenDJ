/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.schema;

import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.util.StaticUtils.toLowerCase;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.types.DirectoryException;


/**
 * This class defines an attribute syntax used for storing values that have been
 * encoded using a password storage scheme.  The format for attribute values
 * with this syntax is the concatenation of the following elements in the given
 * order:
 * <BR>
 * <UL>
 *   <LI>An opening curly brace ("{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
") character.</LI>
 *   <LI>The name of the storage scheme used to encode the value.</LI>
 *   <LI>A closing curly brace ("}") character.</LI>
 *   <LI>The encoded value.</LI>
 * </UL>
 */
public class UserPasswordSyntax

{

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
    if (userPasswordValue == null || userPasswordValue.length() == 0)
    {
      LocalizableMessage message = ERR_ATTR_SYNTAX_USERPW_NO_VALUE.get();
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // The first character of an encoded value must be an opening curly brace.
    if (userPasswordValue.charAt(0) != '{')
    {
      LocalizableMessage message = ERR_ATTR_SYNTAX_USERPW_NO_OPENING_BRACE.get();
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // There must be a corresponding closing brace.
    int closePos = userPasswordValue.indexOf('}');
    if (closePos < 0)
    {
      LocalizableMessage message = ERR_ATTR_SYNTAX_USERPW_NO_CLOSING_BRACE.get();
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // Get the storage scheme name and encoded value.
    String schemeName   = userPasswordValue.substring(1, closePos);
    String encodedValue = userPasswordValue.substring(closePos+1);

    if (schemeName.length() == 0)
    {
      LocalizableMessage message = ERR_ATTR_SYNTAX_USERPW_NO_SCHEME.get();
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
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
  public static boolean isEncoded(ByteSequence value)
  {
    // If the value is null or empty, then it's not.
    if (value == null || value.length() == 0)
    {
      return false;
    }


    // If the value doesn't start with an opening curly brace, then it's not.
    if (value.byteAt(0) != '{')
    {
      return false;
    }


    // There must be a corresponding closing curly brace, and there must be at
    // least one character inside the brace.
    int closingBracePos = -1;
    for (int i=1; i < value.length(); i++)
    {
      if (value.byteAt(i) == '}')
      {
        closingBracePos = i;
        break;
      }
    }

    return closingBracePos >= 0
        && closingBracePos != 1
        // The closing curly brace must not be the last character of the password.
        && closingBracePos != value.length() - 1;
  }

}

