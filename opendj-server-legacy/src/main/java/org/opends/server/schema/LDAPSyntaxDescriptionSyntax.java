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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.schema;

import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.types.CommonSchemaElements;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.LDAPSyntaxDescription;
import org.opends.server.types.Schema;

/**
 * This class defines the LDAP syntax description syntax, which is used to
 * hold attribute syntax definitions in the server schema.  The format of this
 * syntax is defined in RFC 2252.
 */
public class LDAPSyntaxDescriptionSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The default equality matching rule for this syntax. */
  private MatchingRule defaultEqualityMatchingRule;

  /** The default ordering matching rule for this syntax. */
  private MatchingRule defaultOrderingMatchingRule;

  /** The default substring matching rule for this syntax. */
  private MatchingRule defaultSubstringMatchingRule;

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

  /** {@inheritDoc} */
  @Override
  public Syntax getSDKSyntax(org.forgerock.opendj.ldap.schema.Schema schema)
  {
    return schema.getSyntax(SchemaConstants.SYNTAX_LDAP_SYNTAX_OID);
  }

  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  @Override
  public String getName()
  {
    return SYNTAX_LDAP_SYNTAX_NAME;
  }

  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  @Override
  public String getOID()
  {
    return SYNTAX_LDAP_SYNTAX_OID;
  }

  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  @Override
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
  @Override
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
  @Override
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
  @Override
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
  @Override
  public MatchingRule getApproximateMatchingRule()
  {
    // There is no approximate matching rule by default.
    return null;
  }

  /**
   * Decodes the contents of the provided byte sequence as an ldap syntax
   * definition according to the rules of this syntax.  Note that the provided
   * byte sequence value does not need to be normalized (and in fact, it should
   * not be in order to allow the desired capitalization to be preserved).
   *
   * @param  value                 The byte sequence containing the value
   *                               to decode (it does not need to be
   *                               normalized).
   * @param serverContext
   *            The server context.
   * @param  schema                The schema to use to resolve references to
   *                               other schema elements.
   * @param  allowUnknownElements  Indicates whether to allow values that are
   *                               not defined in the server schema. This
   *                               should only be true when called by
   *                               {@code valueIsAcceptable}.
   *                               Not used for LDAP Syntaxes
   * @param forDelete
   *            {@code true} if used for deletion.
   *
   * @return  The decoded ldapsyntax definition.
   *
   * @throws  DirectoryException  If the provided value cannot be decoded as an
   *                              ldapsyntax definition.
   */
  public static LDAPSyntaxDescription decodeLDAPSyntax(ByteSequence value, ServerContext serverContext,
          Schema schema, boolean allowUnknownElements, boolean forDelete) throws DirectoryException
  {
    // Get string representations of the provided value using the provided form.
    String valueStr = value.toString();

    // We'll do this a character at a time.  First, skip over any leading
    // whitespace.
    int pos    = 0;
    int length = valueStr.length();
    while (pos < length && valueStr.charAt(pos) == ' ')
    {
      pos++;
    }

    if (pos >= length)
    {
      // This means that the value was empty or contained only whitespace.  That
      // is illegal.

      LocalizableMessage message = ERR_ATTR_SYNTAX_LDAPSYNTAX_EMPTY_VALUE.get();
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
              message);
    }


    // The next character must be an open parenthesis.  If it is not, then that
    // is an error.
    char c = valueStr.charAt(pos++);
    if (c != '(')
    {
      LocalizableMessage message =
              ERR_ATTR_SYNTAX_LDAPSYNTAX_EXPECTED_OPEN_PARENTHESIS.get(valueStr, pos-1, c);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
              message);
    }


    // Skip over any spaces immediately following the opening parenthesis.
    while (pos < length && ((c = valueStr.charAt(pos)) == ' '))
    {
      pos++;
    }

    if (pos >= length)
    {
      // This means that the end of the value was reached before we could find
      // the OID.  Ths is illegal.
      LocalizableMessage message = ERR_ATTR_SYNTAX_LDAPSYNTAX_TRUNCATED_VALUE.get(
              valueStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
              message);
    }

    int oidStartPos = pos;
    if (isDigit(c))
    {
      // This must be a numeric OID.  In that case, we will accept only digits
      // and periods, but not consecutive periods.
      boolean lastWasPeriod = false;
      while (pos < length && ((c = valueStr.charAt(pos)) != ' ')
              && (c = valueStr.charAt(pos)) != ')')
      {
        if (c == '.')
        {
          if (lastWasPeriod)
          {
            LocalizableMessage message =
              ERR_ATTR_SYNTAX_LDAPSYNTAX_DOUBLE_PERIOD_IN_NUMERIC_OID.
                  get(valueStr, pos-1);
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message);
          }
          else
          {
            lastWasPeriod = true;
          }
        }
        else if (! isDigit(c))
        {
          // This must have been an illegal character.
          LocalizableMessage message =
              ERR_ATTR_SYNTAX_LDAPSYNTAX_ILLEGAL_CHAR_IN_NUMERIC_OID.
                get(valueStr, c, pos-1);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }
        else
        {
          lastWasPeriod = false;
        }
        pos++;
      }
    }
    else
    {
      // This must be a "fake" OID.  In this case, we will only accept
      // alphabetic characters, numeric digits, and the hyphen.
      while (pos < length && ((c = valueStr.charAt(pos)) != ' ')
              && (c=valueStr.charAt(pos))!=')')
      {
        if (isAlpha(c) || isDigit(c) || c == '-' ||
            (c == '_' && DirectoryServer.allowAttributeNameExceptions()))
        {
          // This is fine.  It is an acceptable character.
          pos++;
        }
        else
        {
          // This must have been an illegal character.
          LocalizableMessage message =
                  ERR_ATTR_SYNTAX_LDAPSYNTAX_ILLEGAL_CHAR_IN_STRING_OID.
              get(valueStr, c, pos-1);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }
      }
    }

    // If we're at the end of the value, then it isn't a valid attribute type
    // description.  Otherwise, parse out the OID.
    String oid;
    if (pos >= length)
    {
      LocalizableMessage message = ERR_ATTR_SYNTAX_LDAPSYNTAX_TRUNCATED_VALUE.get(
              valueStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
              message);
    }
    oid = toLowerCase(valueStr.substring(oidStartPos, pos));

    // Skip over the space(s) after the OID.
    while (pos < length && ((c = valueStr.charAt(pos)) == ' '))
    {
      pos++;
    }

    if (pos >= length)
    {
      // This means that the end of the value was reached before we could find
      // the OID.  Ths is illegal.
      LocalizableMessage message = ERR_ATTR_SYNTAX_LDAPSYNTAX_TRUNCATED_VALUE.get(
              valueStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
              message);
    }

    // At this point, we should have a pretty specific syntax that describes
    // what may come next, but some of the components are optional and it would
    // be pretty easy to put something in the wrong order, so we will be very
    // flexible about what we can accept.  Just look at the next token, figure
    // out what it is and how to treat what comes after it, then repeat until
    // we get to the end of the value.  But before we start, set default values
    // for everything else we might need to know.
    String description = null;
    Syntax syntax = null;
    HashMap<String,List<String>> extraProperties = new LinkedHashMap<>();
    boolean hasXSyntaxToken = false;

    while (true)
    {
      StringBuilder tokenNameBuffer = new StringBuilder();
      pos = readTokenName(valueStr, tokenNameBuffer, pos);
      String tokenName = tokenNameBuffer.toString();
      String lowerTokenName = toLowerCase(tokenName);
      if (tokenName.equals(")"))
      {
        // We must be at the end of the value.  If not, then that's a problem.
        if (pos < length)
        {
          LocalizableMessage message =
            ERR_ATTR_SYNTAX_LDAPSYNTAX_UNEXPECTED_CLOSE_PARENTHESIS.get(valueStr, pos-1);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
        }

        break;
      }
      else if (lowerTokenName.equals("desc"))
      {
        // This specifies the description for the attribute type.  It is an
        // arbitrary string of characters enclosed in single quotes.
        StringBuilder descriptionBuffer = new StringBuilder();
        pos = readQuotedString(valueStr, descriptionBuffer, pos);
        description = descriptionBuffer.toString();
      }
      else
      {
        SchemaBuilder schemaBuilder = serverContext != null ?
            serverContext.getSchemaUpdater().getSchemaBuilder() : new SchemaBuilder(CoreSchema.getInstance());

        if (lowerTokenName.equals("x-subst"))
        {
          if (hasXSyntaxToken)
          {
            // We've already seen syntax extension. More than 1 is not allowed
            LocalizableMessage message =
                ERR_ATTR_SYNTAX_LDAPSYNTAX_TOO_MANY_EXTENSIONS.get(valueStr);
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message);
          }
          hasXSyntaxToken = true;
          StringBuilder woidBuffer = new StringBuilder();
          pos = readQuotedString(valueStr, woidBuffer, pos);
          String syntaxOID = toLowerCase(woidBuffer.toString());
          Syntax subSyntax = schema.getSyntax(syntaxOID);
          if (subSyntax == null)
          {
            LocalizableMessage message = ERR_ATTR_SYNTAX_LDAPSYNTAX_UNKNOWN_SYNTAX.get(oid, syntaxOID);
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
          }
          syntax = forDelete ? schema.getSyntax(oid) : schemaBuilder.buildSyntax(oid)
              .extraProperties("x-subst", syntaxOID)
              .addToSchema().toSchema().getSyntax(oid);
        }

        else if(lowerTokenName.equals("x-pattern"))
        {
          if (hasXSyntaxToken)
          {
            // We've already seen syntax extension. More than 1 is not allowed
            LocalizableMessage message =
                ERR_ATTR_SYNTAX_LDAPSYNTAX_TOO_MANY_EXTENSIONS.get(valueStr);
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message);
          }
          hasXSyntaxToken = true;
          StringBuilder regexBuffer = new StringBuilder();
          pos = readQuotedString(valueStr, regexBuffer, pos);
          String regex = regexBuffer.toString().trim();
          if(regex.length() == 0)
          {
            LocalizableMessage message = WARN_ATTR_SYNTAX_LDAPSYNTAX_REGEX_NO_PATTERN.get(
                 valueStr);
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message);
          }

          try
          {
            syntax = forDelete ? schema.getSyntax(oid) : schemaBuilder.buildSyntax(oid)
                .extraProperties("x-pattern", regex)
                .addToSchema().toSchema().getSyntax(oid);
          }
          catch(Exception e)
          {
            LocalizableMessage message =
                WARN_ATTR_SYNTAX_LDAPSYNTAX_REGEX_INVALID_PATTERN.get
                    (valueStr,regex);
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message);
          }
        }
        else if(lowerTokenName.equals("x-enum"))
        {
          if (hasXSyntaxToken)
          {
            // We've already seen syntax extension. More than 1 is not allowed
            LocalizableMessage message =
                ERR_ATTR_SYNTAX_LDAPSYNTAX_TOO_MANY_EXTENSIONS.get(valueStr);
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message);
          }
          hasXSyntaxToken = true;
          LinkedList<String> values = new LinkedList<>();
          pos = readExtraParameterValues(valueStr, values, pos);

          if (values.isEmpty())
          {
            LocalizableMessage message =
                ERR_ATTR_SYNTAX_LDAPSYNTAX_ENUM_NO_VALUES.get(valueStr);
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                         message);
          }
          // Parse all enum values, check for uniqueness
          List<String> entries = new LinkedList<>();
          for (String v : values)
          {
            ByteString entry = ByteString.valueOf(v);
            if (entries.contains(entry))
            {
              throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                  WARN_ATTR_SYNTAX_LDAPSYNTAX_ENUM_DUPLICATE_VALUE.get(
                      valueStr, entry,pos));
            }
            entries.add(v);
          }

          syntax = forDelete ? schema.getSyntax(oid) : schemaBuilder
              .addEnumerationSyntax(oid, description, true, entries.toArray(new String[0]))
              .toSchema().getSyntax(oid);
        }
        else if (tokenName.matches("X\\-[_\\p{Alpha}-]+"))
        {
          // This must be a non-standard property and it must be followed by
          // either a single value in single quotes or an open parenthesis
          // followed by one or more values in single quotes separated by spaces
          // followed by a close parenthesis.
          List<String> valueList = new ArrayList<>();
          pos = readExtraParameterValues(valueStr, valueList, pos);
          extraProperties.put(tokenName, valueList);
        }
        else
        {
          // Unknown Token
          LocalizableMessage message = ERR_ATTR_SYNTAX_LDAPSYNTAX_UNKNOWN_EXT.get(
              valueStr, tokenName, pos);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                message);
        }
      }
    }
    if (syntax == null)
    {
      // TODO : add localized message
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          LocalizableMessage.raw("no LDAP syntax for:" + value));
    }

    CommonSchemaElements.checkSafeProperties(extraProperties);

    //Since we reached here it means everything is OK.
    return new LDAPSyntaxDescription(valueStr, syntax, extraProperties);
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
    while (startPos < length && ((c = valueStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      LocalizableMessage message =
          ERR_ATTR_SYNTAX_LDAPSYNTAX_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // Read until we find the next space.
    while (startPos < length && ((c = valueStr.charAt(startPos++)) != ' '))
    {
      tokenName.append(c);
    }


    // Skip over any trailing spaces after the value.
    while (startPos < length && ((c = valueStr.charAt(startPos)) == ' '))
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
    while (startPos < length && ((c = valueStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      LocalizableMessage message =
          ERR_ATTR_SYNTAX_LDAPSYNTAX_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // The next character must be a single quote.
    if (c != '\'')
    {
      LocalizableMessage message = ERR_ATTR_SYNTAX_LDAPSYNTAX_EXPECTED_QUOTE_AT_POS.get(valueStr, startPos, c);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // Read until we find the closing quote.
    startPos++;
    while (startPos < length && ((c = valueStr.charAt(startPos)) != '\''))
    {
      valueBuffer.append(c);
      startPos++;
    }


    // Skip over any trailing spaces after the value.
    startPos++;
    while (startPos < length && ((c = valueStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }


    // If we're at the end of the value, then that's illegal.
    if (startPos >= length)
    {
      LocalizableMessage message =
          ERR_ATTR_SYNTAX_LDAPSYNTAX_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // Return the position of the first non-space character after the token.
    return startPos;
  }


  /**
   * Reads the value for an "extra" parameter.  It will handle a single unquoted
   * word (which is technically illegal, but we'll allow it), a single quoted
   * string, or an open parenthesis followed by a space-delimited set of quoted
   * strings or unquoted words followed by a close parenthesis.
   *
   * @param  valueStr   The string containing the information to be read.
   * @param  valueList  The list of "extra" parameter values read so far.
   * @param  startPos   The position in the value string at which to start
   *                    reading.
   *
   * @return  The "extra" parameter value that was read.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to read
   *                              the value.
   */
  private static int readExtraParameterValues(String valueStr,
                          List<String> valueList, int startPos)
          throws DirectoryException
  {
    // Skip over any leading spaces.
    int length = valueStr.length();
    char c = '\u0000';
    while (startPos < length && ((c = valueStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      LocalizableMessage message =
          ERR_ATTR_SYNTAX_LDAPSYNTAX_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // Look at the next character.  If it is a quote, then parse until the next
    // quote and end.  If it is an open parenthesis, then parse individual
    // values until the close parenthesis and end.  Otherwise, parse until the
    // next space and end.
    if (c == '\'')
    {
      // Parse until the closing quote.
      StringBuilder valueBuffer = new StringBuilder();
      startPos++;
      while (startPos < length && ((c = valueStr.charAt(startPos)) != '\''))
      {
        valueBuffer.append(c);
        startPos++;
      }
      startPos++;
      valueList.add(valueBuffer.toString());
    }
    else if (c == '(')
    {
      startPos++;
      // We're expecting a list of values. Quoted, space separated.
      while (true)
      {
        // Skip over any leading spaces;
        while (startPos < length && ((c = valueStr.charAt(startPos)) == ' '))
        {
          startPos++;
        }

        if (startPos >= length)
        {
          LocalizableMessage message =
              ERR_ATTR_SYNTAX_LDAPSYNTAX_TRUNCATED_VALUE.get(valueStr);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }

        if (c == ')')
        {
          // This is the end of the list.
          startPos++;
          break;
        }
        else if (c == '(')
        {
          // This is an illegal character.
          LocalizableMessage message =
              ERR_ATTR_SYNTAX_LDAPSYNTAX_EXTENSION_INVALID_CHARACTER.get(
                      valueStr, startPos);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }
        else if (c == '\'')
        {
          // We have a quoted string
          StringBuilder valueBuffer = new StringBuilder();
          startPos++;
          while (startPos < length && ((c = valueStr.charAt(startPos)) != '\''))
          {
            valueBuffer.append(c);
            startPos++;
          }

          valueList.add(valueBuffer.toString());
          startPos++;
        }
        else
        {
          //Consider unquoted string
          StringBuilder valueBuffer = new StringBuilder();
          while (startPos < length && ((c = valueStr.charAt(startPos)) != ' '))
          {
            valueBuffer.append(c);
            startPos++;
          }

          valueList.add(valueBuffer.toString());
        }

        if (startPos >= length)
        {
          LocalizableMessage message =
              ERR_ATTR_SYNTAX_LDAPSYNTAX_TRUNCATED_VALUE.get(valueStr);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }
      }
    }
    else
    {
      // Parse until the next space.
      StringBuilder valueBuffer = new StringBuilder();
      while (startPos < length && ((c = valueStr.charAt(startPos)) != ' '))
      {
        valueBuffer.append(c);
        startPos++;
      }

      valueList.add(valueBuffer.toString());
    }

    // Skip over any trailing spaces.
    while (startPos < length && valueStr.charAt(startPos) == ' ')
    {
      startPos++;
    }

    if (startPos >= length)
    {
      LocalizableMessage message =
          ERR_ATTR_SYNTAX_LDAPSYNTAX_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }

    return startPos;
  }
}
