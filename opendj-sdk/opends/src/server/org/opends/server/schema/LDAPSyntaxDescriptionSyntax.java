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
 * by brackets "[]" replaced with your own identifying * information:
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

import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.Error.*;
import org.opends.server.types.DebugLogLevel;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the LDAP syntax description syntax, which is used to
 * hold attribute syntax definitions in the server schema.  The format of this
 * syntax is defined in RFC 2252.
 */
public class LDAPSyntaxDescriptionSyntax
       extends AttributeSyntax
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
  public LDAPSyntaxDescriptionSyntax()
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
         DirectoryServer.getEqualityMatchingRule(EMR_CASE_IGNORE_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE,
               EMR_CASE_IGNORE_OID, SYNTAX_LDAP_SYNTAX_NAME);
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getOrderingMatchingRule(OMR_CASE_IGNORE_OID);
    if (defaultOrderingMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE,
               OMR_CASE_IGNORE_OID, SYNTAX_LDAP_SYNTAX_NAME);
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getSubstringMatchingRule(SMR_CASE_IGNORE_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR,
               MSGID_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE,
               SMR_CASE_IGNORE_OID, SYNTAX_LDAP_SYNTAX_NAME);
    }
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getSyntaxName()
  {

    return SYNTAX_LDAP_SYNTAX_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {

    return SYNTAX_LDAP_SYNTAX_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {

    return SYNTAX_LDAP_SYNTAX_DESCRIPTION;
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


    // Get string representations of the provided value using the provided form
    // and with all lowercase characters.
    String valueStr = value.stringValue();
    String lowerStr = toLowerCase(valueStr);


    // We'll do this a character at a time.  First, skip over any leading
    // whitespace.
    int pos    = 0;
    int length = valueStr.length();
    while ((pos < length) && (valueStr.charAt(pos) == ' '))
    {
      pos++;
    }

    if (pos >= length)
    {
      // This means that the value was empty or contained only whitespace.  That
      // is illegal.
      int msgID = MSGID_ATTR_SYNTAX_ATTRSYNTAX_EMPTY_VALUE;
      invalidReason.append(getMessage(msgID));
      return false;
    }


    // The next character must be an open parenthesis.  If it is not, then that
    // is an error.
    char c = valueStr.charAt(pos++);
    if (c != '(')
    {
      int msgID = MSGID_ATTR_SYNTAX_ATTRSYNTAX_EXPECTED_OPEN_PARENTHESIS;
      invalidReason.append(getMessage(msgID, valueStr, (pos-1), c));
      return false;
    }


    // Skip over any spaces immediately following the opening parenthesis.
    while ((pos < length) && ((c = valueStr.charAt(pos)) == ' '))
    {
      pos++;
    }

    if (pos >= length)
    {
      // This means that the end of the value was reached before we could find
      // the OID.  Ths is illegal.
      int msgID = MSGID_ATTR_SYNTAX_ATTRSYNTAX_TRUNCATED_VALUE;
      invalidReason.append(getMessage(msgID, valueStr));
      return false;
    }


    // The next set of characters must be the OID.  Strictly speaking, this
    // should only be a numeric OID, but we'll also allow for the
    // "attrname-oid" case as well.  Look at the first character to figure out
    // which we will be using.
    int oidStartPos = pos;
    if (isDigit(c))
    {
      // This must be a numeric OID.  In that case, we will accept only digits
      // and periods, but not consecutive periods.
      boolean lastWasPeriod = false;
      while ((pos < length) && ((c = valueStr.charAt(pos++)) != ' '))
      {
        if (c == '.')
        {
          if (lastWasPeriod)
          {
            int msgID =
                 MSGID_ATTR_SYNTAX_ATTRSYNTAX_DOUBLE_PERIOD_IN_NUMERIC_OID;
            invalidReason.append(getMessage(msgID, valueStr, (pos-1)));
            return false;
          }
          else
          {
            lastWasPeriod = true;
          }
        }
        else if (! isDigit(c))
        {
          // This must have been an illegal character.
          int msgID = MSGID_ATTR_SYNTAX_ATTRSYNTAX_ILLEGAL_CHAR_IN_NUMERIC_OID;
          invalidReason.append(getMessage(msgID, valueStr, c, (pos-1)));
          return false;
        }
        else
        {
          lastWasPeriod = false;
        }
      }
    }
    else
    {
      // This must be a "fake" OID.  In this case, we will only accept
      // alphabetic characters, numeric digits, and the hyphen.
      while ((pos < length) && ((c = valueStr.charAt(pos++)) != ' '))
      {
        if (isAlpha(c) || isDigit(c) || (c == '-') ||
            ((c == '_') && DirectoryServer.allowAttributeNameExceptions()))
        {
          // This is fine.  It is an acceptable character.
        }
        else
        {
          // This must have been an illegal character.
          int msgID = MSGID_ATTR_SYNTAX_ATTRSYNTAX_ILLEGAL_CHAR_IN_STRING_OID;
          invalidReason.append(getMessage(msgID, valueStr, c, (pos-1)));
          return false;
        }
      }
    }


    // If we're at the end of the value, then it isn't a valid attribute type
    // description.  Otherwise, parse out the OID.
    String oid;
    if (pos >= length)
    {
      int msgID = MSGID_ATTR_SYNTAX_ATTRSYNTAX_TRUNCATED_VALUE;
      invalidReason.append(getMessage(msgID, valueStr));
      return false;
    }
    else
    {
      oid = lowerStr.substring(oidStartPos, pos);
    }


    // Skip over the space(s) after the OID.
    while ((pos < length) && ((c = valueStr.charAt(pos)) == ' '))
    {
      pos++;
    }

    if (pos >= length)
    {
      // This means that the end of the value was reached before we could find
      // the OID.  Ths is illegal.
      int msgID = MSGID_ATTR_SYNTAX_ATTRSYNTAX_TRUNCATED_VALUE;
      invalidReason.append(getMessage(msgID, valueStr));
      return false;
    }


    // If the next character is a closing parenthesis, then we must be at the
    // end of the value.
    if (c == ')')
    {
      if (pos < length)
      {
        int msgID = MSGID_ATTR_SYNTAX_ATTRSYNTAX_UNEXPECTED_CLOSE_PARENTHESIS;
        invalidReason.append(getMessage(msgID, valueStr, (pos-1)));
        return false;
      }

      return true;
    }


    // The next token must be "DESC" followed by a quoted string.
    String tokenName;
    try
    {
      StringBuilder tokenNameBuffer = new StringBuilder();
      pos = readTokenName(lowerStr, tokenNameBuffer, pos);
      tokenName = tokenNameBuffer.toString();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_ATTR_SYNTAX_ATTRSYNTAX_CANNOT_READ_DESC_TOKEN;
      invalidReason.append(getMessage(msgID, valueStr, pos,
                                      stackTraceToSingleLineString(e)));
      return false;
    }

    if (! tokenName.equals("desc"))
    {
      int msgID = MSGID_ATTR_SYNTAX_ATTRSYNTAX_TOKEN_NOT_DESC;
      invalidReason.append(getMessage(msgID, valueStr, tokenName));
      return false;
    }


    // The next component must be the quoted description.
    try
    {
      StringBuilder descriptionBuffer = new StringBuilder();
      pos = readQuotedString(valueStr, descriptionBuffer, pos);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      int msgID = MSGID_ATTR_SYNTAX_ATTRSYNTAX_CANNOT_READ_DESC_VALUE;
      invalidReason.append(getMessage(msgID, valueStr, pos,
                                      stackTraceToSingleLineString(e)));
      return false;
    }
    //Check if we have a RFC 4512 style extension.
    if ((c = valueStr.charAt(pos)) != ')')
    {
        try {
            pos=parseExtension(valueStr, pos);
        } catch (Exception e) {
          if (debugEnabled())
          {
            debugCought(DebugLogLevel.ERROR, e);
          }
            int msgID = MSGID_ATTR_SYNTAX_ATTRSYNTAX_INVALID_EXTENSION;
            invalidReason.append(getMessage(msgID,
                    stackTraceToSingleLineString(e)));
            return false;
        }
    }

    // The next character must be the closing parenthesis and there should not
    // be anything after it (except maybe some spaces).
    if ((c = valueStr.charAt(pos++)) != ')')
    {
      int msgID = MSGID_ATTR_SYNTAX_ATTRSYNTAX_EXPECTED_CLOSE_PARENTHESIS;
      invalidReason.append(getMessage(msgID, valueStr, pos, c));
      return false;
    }

    while (pos < length)
    {
      c = valueStr.charAt(pos++);
      if (c != ' ')
      {
        int msgID = MSGID_ATTR_SYNTAX_ATTRSYNTAX_ILLEGAL_CHAR_AFTER_CLOSE;
        invalidReason.append(getMessage(msgID, valueStr, c, pos));
        return false;
      }
    }


    // If we've gotten here, then the value is OK.
    return true;
  }



  /**
   * Reads the next token name from the attribute syntax definition, skipping
   * over any leading or trailing spaces, and appends it to the provided buffer.
   *
   * @param  valueStr   The string representation of the attribute syntax
   *                    definition.
   * @param  tokenName  The buffer into which the token name will be written.
   * @param  startPos   The position in the provided string at which to start
   *                    reading the token name.
   *
   * @return  The position of the first character that is not part of the token
   *          name or one of the trailing spaces after it.
   *
   * @throws  DirectoryException  If a problem is encountered while reading the
   *                              token name.
   */
  private static int readTokenName(String valueStr, StringBuilder tokenName,
                                   int startPos)
          throws DirectoryException
  {


    // Skip over any spaces at the beginning of the value.
    char c = '\u0000';
    int  length = valueStr.length();
    while ((startPos < length) && ((c = valueStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_ATTRSYNTAX_TRUNCATED_VALUE;
      String message = getMessage(msgID, valueStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // Read until we find the next space.
    while ((startPos < length) && ((c = valueStr.charAt(startPos++)) != ' '))
    {
      tokenName.append(c);
    }


    // Skip over any trailing spaces after the value.
    while ((startPos < length) && ((c = valueStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }


    // Return the position of the first non-space character after the token.
    return startPos;
  }



  /**
   * Reads the value of a string enclosed in single quotes, skipping over the
   * quotes and any leading or trailing spaces, and appending the string to the
   * provided buffer.
   *
   * @param  valueStr     The user-provided representation of the attribute type
   *                      definition.
   * @param  valueBuffer  The buffer into which the user-provided representation
   *                      of the value will be placed.
   * @param  startPos     The position in the provided string at which to start
   *                      reading the quoted string.
   *
   * @return  The position of the first character that is not part of the quoted
   *          string or one of the trailing spaces after it.
   *
   * @throws  DirectoryException  If a problem is encountered while reading the
   *                              quoted string.
   */
  private static int readQuotedString(String valueStr,
                                      StringBuilder valueBuffer, int startPos)
          throws DirectoryException
  {


    // Skip over any spaces at the beginning of the value.
    char c = '\u0000';
    int  length = valueStr.length();
    while ((startPos < length) && ((c = valueStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_ATTRSYNTAX_TRUNCATED_VALUE;
      String message = getMessage(msgID, valueStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // The next character must be a single quote.
    if (c != '\'')
    {
      int    msgID   = MSGID_ATTR_SYNTAX_ATTRSYNTAX_EXPECTED_QUOTE_AT_POS;
      String message = getMessage(msgID, valueStr, startPos, c);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // Read until we find the closing quote.
    startPos++;
    while ((startPos < length) && ((c = valueStr.charAt(startPos)) != '\''))
    {
      valueBuffer.append(c);
      startPos++;
    }


    // Skip over any trailing spaces after the value.
    startPos++;
    while ((startPos < length) && ((c = valueStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }


    // If we're at the end of the value, then that's illegal.
    if (startPos >= length)
    {
      int    msgID   = MSGID_ATTR_SYNTAX_ATTRSYNTAX_TRUNCATED_VALUE;
      String message = getMessage(msgID, valueStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message,
                                   msgID);
    }


    // Return the position of the first non-space character after the token.
    return startPos;
  }

  /** Parses a RFC 4512 extensions (see 4.1.5 and 4.1 of the RFC) definition.
   *
   * From 4.1.5 of the spec:
   *
   *  LDAP syntax definitions are written according to the ABNF:
   *
   *  SyntaxDescription = LPAREN WSP
   *      numericoid                 ; object identifier
   *      [ SP "DESC" SP qdstring ]  ; description
   *      extensions WSP RPAREN      ; extensions
   *
   * @param valueStr The user-provided representation of the extensions
   *                      definition.
   *
   * @param startPos The position in the provided string at which to start
   *                      reading the quoted string.
   *
   * @return The position of the first character that is not part of the quoted
   *          string or one of the trailing spaces after it.
   *
   * @throws DirectoryException If the extensions definition could not be
   *                            parsed.
   */
private static int parseExtension(String valueStr, int startPos)
  throws DirectoryException {

      int pos=startPos, len=valueStr.length();
      char c;
      while(true)
      {
          StringBuilder tokenNameBuffer = new StringBuilder();
          pos = readTokenName(valueStr, tokenNameBuffer, pos);
          String tokenName = tokenNameBuffer.toString();
          if((tokenName.length() <= 2) || (!tokenName.startsWith("X-")))
          {
              int    msgID  =
                  MSGID_ATTR_SYNTAX_ATTRSYNTAX_EXTENSION_INVALID_CHARACTER;
              String message = getMessage(msgID, valueStr);
              throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                      message, msgID);
          }
          String xstring = tokenName.substring(2);
          //Only allow a-z,A-Z,-,_ characters after X-
          if(xstring.split("^[A-Za-z_-]+").length > 0)
          {
              int    msgID   =
                  MSGID_ATTR_SYNTAX_ATTRSYNTAX_EXTENSION_INVALID_CHARACTER;
              String message = getMessage(msgID, valueStr);
              throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                      message, msgID);
          }
          if((c=valueStr.charAt(pos)) == '\'')
          {
              StringBuilder qdString = new StringBuilder();
              pos = readQuotedString(valueStr, qdString, pos);

          } else if(c == '(')
          {
              pos++;
              StringBuilder qdString = new StringBuilder();
              while ((c=valueStr.charAt(pos)) != ')')
                  pos = readQuotedString(valueStr, qdString, pos);
              pos++;
          } else
          {
              int    msgID   =
                  MSGID_ATTR_SYNTAX_ATTRSYNTAX_EXTENSION_INVALID_CHARACTER;
              String message = getMessage(msgID, valueStr);
              throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                      message, msgID);
          }
          if (pos >= len)
          {
            int    msgID   = MSGID_ATTR_SYNTAX_ATTRSYNTAX_TRUNCATED_VALUE;
            String message = getMessage(msgID, valueStr);
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message, msgID);
          }
          if(valueStr.charAt(pos) == ')')
              break;
      }
      return pos;
  }
}

