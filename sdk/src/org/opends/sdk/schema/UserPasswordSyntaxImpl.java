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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.sdk.schema;



import static org.opends.messages.SchemaMessages.ERR_ATTR_SYNTAX_USERPW_NO_CLOSING_BRACE;
import static org.opends.messages.SchemaMessages.ERR_ATTR_SYNTAX_USERPW_NO_OPENING_BRACE;
import static org.opends.messages.SchemaMessages.ERR_ATTR_SYNTAX_USERPW_NO_SCHEME;
import static org.opends.messages.SchemaMessages.ERR_ATTR_SYNTAX_USERPW_NO_VALUE;
import static org.opends.sdk.schema.SchemaConstants.EMR_USER_PASSWORD_EXACT_OID;
import static org.opends.sdk.schema.SchemaConstants.SYNTAX_USER_PASSWORD_NAME;
import static org.opends.sdk.util.StaticUtils.toLowerCase;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.sdk.DecodeException;
import org.opends.sdk.util.ByteSequence;



/**
 * This class defines an attribute syntax used for storing values that
 * have been encoded using a password storage scheme. The format for
 * attribute values with this syntax is the concatenation of the
 * following elements in the given order: <BR>
 * <UL>
 * <LI>An opening curly brace ("{") character.</LI>
 * <LI>The name of the storage scheme used to encode the value.</LI>
 * <LI>A closing curly brace ("}") character.</LI>
 * <LI>The encoded value.</LI>
 * </UL>
 */
final class UserPasswordSyntaxImpl extends AbstractSyntaxImpl
{
  /**
   * Decodes the provided user password value into its component parts.
   * 
   * @param userPasswordValue
   *          The user password value to be decoded.
   * @return A two-element string array whose elements are the storage
   *         scheme name (in all lowercase characters) and the encoded
   *         value, in that order.
   * @throws DecodeException
   *           If a problem is encountered while attempting to decode
   *           the value.
   */
  static String[] decodeUserPassword(String userPasswordValue)
      throws DecodeException
  {
    // Make sure that there actually is a value to decode.
    if (userPasswordValue == null || userPasswordValue.length() == 0)
    {
      final Message message = ERR_ATTR_SYNTAX_USERPW_NO_VALUE.get();
      throw DecodeException.error(message);
    }

    // The first character of an encoded value must be an opening curly
    // brace.
    if (userPasswordValue.charAt(0) != '{')
    {
      final Message message =
          ERR_ATTR_SYNTAX_USERPW_NO_OPENING_BRACE.get();
      throw DecodeException.error(message);
    }

    // There must be a corresponding closing brace.
    final int closePos = userPasswordValue.indexOf('}');
    if (closePos < 0)
    {
      final Message message =
          ERR_ATTR_SYNTAX_USERPW_NO_CLOSING_BRACE.get();
      throw DecodeException.error(message);
    }

    // Get the storage scheme name and encoded value.
    final String schemeName = userPasswordValue.substring(1, closePos);
    final String encodedValue =
        userPasswordValue.substring(closePos + 1);

    if (schemeName.length() == 0)
    {
      final Message message = ERR_ATTR_SYNTAX_USERPW_NO_SCHEME.get();
      throw DecodeException.error(message);
    }

    return new String[] { toLowerCase(schemeName), encodedValue };
  }



  /**
   * Indicates whether the provided value is encoded using the user
   * password syntax.
   * 
   * @param value
   *          The value for which to make the determination.
   * @return <CODE>true</CODE> if the value appears to be encoded using
   *         the user password syntax, or <CODE>false</CODE> if not.
   */
  static boolean isEncoded(ByteSequence value)
  {
    // If the value is null or empty, then it's not.
    if (value == null || value.length() == 0)
    {
      return false;
    }

    // If the value doesn't start with an opening curly brace, then it's
    // not.
    if (value.byteAt(0) != '{')
    {
      return false;
    }

    // There must be a corresponding closing curly brace, and there must
    // be at least one character inside the brace.
    int closingBracePos = -1;
    for (int i = 1; i < value.length(); i++)
    {
      if (value.byteAt(i) == '}')
      {
        closingBracePos = i;
        break;
      }
    }

    if (closingBracePos < 0 || closingBracePos == 1)
    {
      return false;
    }

    // The closing curly brace must not be the last character of the
    // password.
    if (closingBracePos == value.length() - 1)
    {
      return false;
    }

    // If we've gotten here, then it looks to be encoded.
    return true;
  }



  @Override
  public String getEqualityMatchingRule()
  {
    return EMR_USER_PASSWORD_EXACT_OID;
  }



  public String getName()
  {
    return SYNTAX_USER_PASSWORD_NAME;
  }



  public boolean isHumanReadable()
  {
    return true;
  }



  public boolean valueIsAcceptable(Schema schema, ByteSequence value,
      MessageBuilder invalidReason)
  {
    // We have to accept any value here because in many cases the value
    // will not have been encoded by the time this method is called.
    // TODO: Is this correct for client-side use?
    return true;
  }

}
