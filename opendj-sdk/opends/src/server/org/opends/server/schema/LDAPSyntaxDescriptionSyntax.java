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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.schema;




import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.regex.Pattern;

import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.core.DirectoryServer;
import org.opends.messages.Message;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.*;
import org.opends.messages.MessageBuilder;
import org.opends.server.api.AbstractMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ByteSequence;
import org.opends.server.types.ByteString;
import org.opends.server.types.CommonSchemaElements;
import org.opends.server.types.DirectoryException;

import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.schema.StringPrepProfile.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.messages.SchemaMessages.*;

/**
 * This class defines the LDAP syntax description syntax, which is used to
 * hold attribute syntax definitions in the server schema.  The format of this
 * syntax is defined in RFC 2252.
 */
public class LDAPSyntaxDescriptionSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




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
   * {@inheritDoc}
   */
  @Override
  public void initializeSyntax(AttributeSyntaxCfg configuration)
         throws ConfigException
  {
    defaultEqualityMatchingRule =
         DirectoryServer.getEqualityMatchingRule(EMR_CASE_IGNORE_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE.get(
          EMR_CASE_IGNORE_OID, SYNTAX_LDAP_SYNTAX_NAME));
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getOrderingMatchingRule(OMR_CASE_IGNORE_OID);
    if (defaultOrderingMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE.get(
          OMR_CASE_IGNORE_OID, SYNTAX_LDAP_SYNTAX_NAME));
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getSubstringMatchingRule(SMR_CASE_IGNORE_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE.get(
          SMR_CASE_IGNORE_OID, SYNTAX_LDAP_SYNTAX_NAME));
    }
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  @Override
  public String getSyntaxName()
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
  @Override
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
  @Override
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
  @Override
  public ApproximateMatchingRule getApproximateMatchingRule()
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
   * @param  schema                The schema to use to resolve references to
   *                               other schema elements.
   * @param  allowUnknownElements  Indicates whether to allow values that are
   *                               not defined in the server schema. This
   *                               should only be true when called by
   *                               {@code valueIsAcceptable}.
   *                               Not used for LDAP Syntaxes
   *
   * @return  The decoded ldapsyntax definition.
   *
   * @throws  DirectoryException  If the provided value cannot be decoded as an
   *                              ldapsyntax definition.
   */
  public static LDAPSyntaxDescription decodeLDAPSyntax(ByteSequence value,
          Schema schema,
          boolean allowUnknownElements) throws DirectoryException
  {
    // Get string representations of the provided value using the provided form.
    String valueStr = value.toString();

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

      Message message = ERR_ATTR_SYNTAX_LDAPSYNTAX_EMPTY_VALUE.get();
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
              message);
    }


    // The next character must be an open parenthesis.  If it is not, then that
    // is an error.
    char c = valueStr.charAt(pos++);
    if (c != '(')
    {

      Message message =
              ERR_ATTR_SYNTAX_LDAPSYNTAX_EXPECTED_OPEN_PARENTHESIS.get(
                      valueStr, (pos-1), String.valueOf(c));
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
              message);
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
      Message message = ERR_ATTR_SYNTAX_LDAPSYNTAX_TRUNCATED_VALUE.get(
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
      while ((pos < length) && ((c = valueStr.charAt(pos)) != ' ')
              && (c = valueStr.charAt(pos)) != ')')
      {
        if (c == '.')
        {
          if (lastWasPeriod)
          {
            Message message =
              ERR_ATTR_SYNTAX_LDAPSYNTAX_DOUBLE_PERIOD_IN_NUMERIC_OID.
                  get(valueStr, (pos-1));
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
          Message message =
              ERR_ATTR_SYNTAX_LDAPSYNTAX_ILLEGAL_CHAR_IN_NUMERIC_OID.
                get(valueStr, String.valueOf(c), (pos-1));
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
      while ((pos < length) && ((c = valueStr.charAt(pos)) != ' ')
              && (c=valueStr.charAt(pos))!=')')
      {
        if (isAlpha(c) || isDigit(c) || (c == '-') ||
            ((c == '_') && DirectoryServer.allowAttributeNameExceptions()))
        {
          // This is fine.  It is an acceptable character.
          pos++;
        }
        else
        {
          // This must have been an illegal character.
          Message message =
                  ERR_ATTR_SYNTAX_LDAPSYNTAX_ILLEGAL_CHAR_IN_STRING_OID.
              get(valueStr, String.valueOf(c), (pos-1));
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
      Message message = ERR_ATTR_SYNTAX_LDAPSYNTAX_TRUNCATED_VALUE.get(
              valueStr);
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
              message);
    }
    else
    {
      oid = toLowerCase(valueStr.substring(oidStartPos, pos));
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
      Message message = ERR_ATTR_SYNTAX_LDAPSYNTAX_TRUNCATED_VALUE.get(
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
    LDAPSyntaxDescriptionSyntax syntax = null;
    HashMap<String,List<String>> extraProperties =
         new LinkedHashMap<String,List<String>>();
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
          Message message =
            ERR_ATTR_SYNTAX_LDAPSYNTAX_UNEXPECTED_CLOSE_PARENTHESIS.
                get(valueStr, (pos-1));
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
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
      else if (lowerTokenName.equals("x-subst"))
      {
        if (hasXSyntaxToken)
        {
          // We've already seen syntax extension. More than 1 is not allowed
          Message message =
              ERR_ATTR_SYNTAX_LDAPSYNTAX_TOO_MANY_EXTENSIONS.get(valueStr);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }
        hasXSyntaxToken = true;
        StringBuilder woidBuffer = new StringBuilder();
        pos = readQuotedString(valueStr, woidBuffer, pos);
        String syntaxOID = toLowerCase(woidBuffer.toString());
        AttributeSyntax<?> subSyntax = schema.getSyntax(syntaxOID);
        if (subSyntax == null)
        {
          Message message = ERR_ATTR_SYNTAX_LDAPSYNTAX_UNKNOWN_SYNTAX.get(
              String.valueOf(oid), syntaxOID);
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                       message);
        }
        syntax = new SubstitutionSyntax(subSyntax,valueStr,description,oid);
      }

      else if(lowerTokenName.equals("x-pattern"))
      {
        if (hasXSyntaxToken)
        {
          // We've already seen syntax extension. More than 1 is not allowed
          Message message =
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
          Message message = WARN_ATTR_SYNTAX_LDAPSYNTAX_REGEX_NO_PATTERN.get(
               valueStr);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }

        try
        {
          Pattern pattern = Pattern.compile(regex);
          syntax = new RegexSyntax(pattern,valueStr,description,oid);
        }
        catch(Exception e)
        {
          Message message =
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
          Message message =
              ERR_ATTR_SYNTAX_LDAPSYNTAX_TOO_MANY_EXTENSIONS.get(valueStr);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }
        hasXSyntaxToken = true;
        LinkedList<String> values = new LinkedList<String>();
        pos = readExtraParameterValues(valueStr, values, pos);

        if (values.isEmpty())
        {
          Message message =
              ERR_ATTR_SYNTAX_LDAPSYNTAX_ENUM_NO_VALUES.get(valueStr);
          throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                       message);
        }
        // Parse all enum values, check for uniqueness
        LinkedList<ByteSequence> entries = new LinkedList<ByteSequence>();
        for (String v : values)
        {
          ByteString entry = ByteString.valueOf(v);
          if (entries.contains(entry))
          {
            Message message =
                  WARN_ATTR_SYNTAX_LDAPSYNTAX_ENUM_DUPLICATE_VALUE.get(
                          valueStr, entry.toString(),pos);
            throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                  message);
          }
          entries.add(entry);
        }
        syntax = new EnumSyntax(entries, valueStr,description, oid);
      }
      else if (tokenName.matches("X\\-[_\\p{Alpha}-]+"))
      {
        // This must be a non-standard property and it must be followed by
        // either a single value in single quotes or an open parenthesis
        // followed by one or more values in single quotes separated by spaces
        // followed by a close parenthesis.
        List<String> valueList = new ArrayList<String>();
        pos = readExtraParameterValues(valueStr, valueList, pos);
        extraProperties.put(tokenName, valueList);
      }
      else
      {
        // Unknown Token
        Message message = ERR_ATTR_SYNTAX_LDAPSYNTAX_UNKNOWN_EXT.get(
            valueStr, tokenName, pos);
        throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
              message);
      }
    }
    if (syntax == null)
    {
      // Create a plain Syntax. That seems to be required by export/import
      // Schema backend.
      syntax = new LDAPSyntaxDescriptionSyntax();
    }

    CommonSchemaElements.checkSafeProperties(extraProperties);

    //Since we reached here it means everything is OK.
    return new LDAPSyntaxDescription(valueStr,syntax,
                                     description,extraProperties);
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
  @Override
  public boolean valueIsAcceptable(ByteSequence value,
                                   MessageBuilder invalidReason)
  {
    // We'll use the decodeAttributeType method to determine if the value is
    // acceptable.
    try
    {
      decodeLDAPSyntax(value, DirectoryServer.getSchema(), true);
      return true;
    }
    catch (DirectoryException de)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }

      invalidReason.append(de.getMessageObject());
      return false;
    }
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
      Message message =
          ERR_ATTR_SYNTAX_LDAPSYNTAX_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
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
      Message message =
          ERR_ATTR_SYNTAX_LDAPSYNTAX_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }


    // The next character must be a single quote.
    if (c != '\'')
    {
      Message message = ERR_ATTR_SYNTAX_LDAPSYNTAX_EXPECTED_QUOTE_AT_POS.get(
          valueStr, startPos, String.valueOf(c));
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
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
      Message message =
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
    while ((startPos < length) && ((c = valueStr.charAt(startPos)) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      Message message =
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
      while ((startPos < length) && ((c = valueStr.charAt(startPos)) != '\''))
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
        while ((startPos < length) && ((c = valueStr.charAt(startPos)) == ' '))
        {
          startPos++;
        }

        if (startPos >= length)
        {
          Message message =
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
          Message message =
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
          while ((startPos < length) &&
              ((c = valueStr.charAt(startPos)) != '\''))
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
          while ((startPos < length) &&
              ((c = valueStr.charAt(startPos)) != ' '))
          {
            valueBuffer.append(c);
            startPos++;
          }

          valueList.add(valueBuffer.toString());
        }

        if (startPos >= length)
        {
          Message message =
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
      while ((startPos < length) && ((c = valueStr.charAt(startPos)) != ' '))
      {
        valueBuffer.append(c);
        startPos++;
      }

      valueList.add(valueBuffer.toString());
    }

    // Skip over any trailing spaces.
    while ((startPos < length) && (valueStr.charAt(startPos) == ' '))
    {
      startPos++;
    }

    if (startPos >= length)
    {
      Message message =
          ERR_ATTR_SYNTAX_LDAPSYNTAX_TRUNCATED_VALUE.get(valueStr);
      throw new DirectoryException(
              ResultCode.INVALID_ATTRIBUTE_SYNTAX, message);
    }

    return startPos;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isBinary()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isHumanReadable()
  {
    return true;
  }



  /**
   * This class provides a substitution mechanism where one unimplemented
   * syntax can be substituted by another defined syntax. A substitution syntax
   * is an LDAPSyntaxDescriptionSyntax with X-SUBST extension.
   */
  private static class SubstitutionSyntax extends
          LDAPSyntaxDescriptionSyntax
  {
    // The syntax that will substitute the unimplemented syntax.
    private AttributeSyntax<?> subSyntax;

    // The description of this syntax.
    private String description;

    // The definition of this syntax.
    private String definition;


    //The oid of this syntax.
    private String oid;



    //Creates a new instance of this syntax.
    private SubstitutionSyntax(AttributeSyntax<?> subSyntax,
            String definition,
            String description,
            String oid)
    {
      super();
      this.subSyntax = subSyntax;
      this.definition = definition;
      this.description = description;
      this.oid = oid;
    }



    /**
     * {@inheritDoc}
     */
     @Override
    public String getSyntaxName()
    {
      // There is no name for a substitution syntax.
      return null;
    }



    /**
     * {@inheritDoc}
     */
     @Override
    public String getOID()
    {
      return oid;
    }



    /**
     * {@inheritDoc}
     */
     @Override
    public String getDescription()
    {
      return description;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
      return definition;
    }



     /**
     * {@inheritDoc}
     */
    @Override
    public boolean valueIsAcceptable(ByteSequence value,
                                     MessageBuilder invalidReason)
    {
      return  subSyntax.valueIsAcceptable(value, invalidReason);
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
    public EqualityMatchingRule getEqualityMatchingRule()
    {
      return subSyntax.getEqualityMatchingRule();
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
    public OrderingMatchingRule getOrderingMatchingRule()
    {
      return subSyntax.getOrderingMatchingRule();
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
    public SubstringMatchingRule getSubstringMatchingRule()
    {
      return subSyntax.getSubstringMatchingRule();
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
    public ApproximateMatchingRule getApproximateMatchingRule()
    {
      return subSyntax.getApproximateMatchingRule();
    }
  }



  /**
   * This class provides a regex mechanism where a new syntax and its
   * corresponding matching rules can be created on-the-fly. A regex
   * syntax is an LDAPSyntaxDescriptionSyntax with X-PATTERN extension.
   */
  private static class RegexSyntax extends
          LDAPSyntaxDescriptionSyntax
  {
    // The Pattern associated with the regex.
    private Pattern pattern;

    // The description of this syntax.
    private String description;

    //The oid of this syntax.
    private String oid;

    //The definition of this syntax.
    private String definition;

    //The equality matching rule.
    private EqualityMatchingRule equalityMatchingRule;

    //The substring matching rule.
    private SubstringMatchingRule substringMatchingRule;

    //The ordering matching rule.
    private OrderingMatchingRule orderingMatchingRule;

    //The approximate matching rule.
    private ApproximateMatchingRule approximateMatchingRule;


    //Creates a new instance of this syntax.
    private RegexSyntax(Pattern pattern,
            String definition,
            String description,
            String oid)
    {
      super();
      this.definition = definition;
      this.pattern = pattern;
      this.description = description;
      this.oid = oid;
    }



     /**
     * {@inheritDoc}
     */
     @Override
    public String getSyntaxName()
    {
      // There is no name for a regex syntax.
      return null;
    }



    /**
     * {@inheritDoc}
     */
     @Override
    public String getOID()
    {
      return oid;
    }



    /**
     * {@inheritDoc}
     */
     @Override
    public String getDescription()
    {
      return description;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
      return definition;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean valueIsAcceptable(ByteSequence value,
                                     MessageBuilder invalidReason)
    {
      String strValue = value.toString();
      boolean matches = pattern.matcher(strValue).matches();
      if(!matches)
      {
        Message message = WARN_ATTR_SYNTAX_LDAPSYNTAX_REGEX_INVALID_VALUE.get(
                strValue,pattern.pattern());
        invalidReason.append(message);
      }
      return matches;
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
    public EqualityMatchingRule getEqualityMatchingRule()
    {
      if(equalityMatchingRule == null)
      {
        //This has already been verified.
        equalityMatchingRule =
                DirectoryServer.getEqualityMatchingRule(EMR_CASE_IGNORE_OID);
      }
      return equalityMatchingRule;
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
    public OrderingMatchingRule getOrderingMatchingRule()
    {
      if(orderingMatchingRule == null)
      {
        orderingMatchingRule =
                DirectoryServer.getOrderingMatchingRule(OMR_CASE_IGNORE_OID);
      }
      return orderingMatchingRule;
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
    public SubstringMatchingRule getSubstringMatchingRule()
    {
      if(substringMatchingRule == null)
      {
        substringMatchingRule =
                DirectoryServer.getSubstringMatchingRule(SMR_CASE_IGNORE_OID);
      }
      return substringMatchingRule;
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
    public ApproximateMatchingRule getApproximateMatchingRule()
    {
      if(approximateMatchingRule == null)
      {
        approximateMatchingRule =
                DirectoryServer.getApproximateMatchingRule(
                                    AMR_DOUBLE_METAPHONE_OID);
      }
      return approximateMatchingRule;
    }
  }



  /**
   * This class provides an enumeration-based mechanism where a new syntax
   * and its corresponding matching rules can be created on-the-fly. An enum
   * syntax is an LDAPSyntaxDescriptionSyntax with X-PATTERN extension.
   */
  private static class EnumSyntax extends
          LDAPSyntaxDescriptionSyntax
  {
    //Set of read-only enum entries.
    LinkedList<ByteSequence> entries;

    // The description of this syntax.
    private String description;

    //The oid of this syntax.
    private String oid;

    //The equality matching rule.
    private EqualityMatchingRule equalityMatchingRule;

    //The substring matching rule.
    private SubstringMatchingRule substringMatchingRule;

    //The ordering matching rule.
    private OrderingMatchingRule orderingMatchingRule;

    //The approximate matching rule.
    private ApproximateMatchingRule approximateMatchingRule;

    //The definition of this syntax.
    private String definition;


    //Creates a new instance of this syntax.
    private EnumSyntax(LinkedList<ByteSequence> entries,
            String definition,
            String description,
            String oid)
    {
      super();
      this.entries = entries;
      this.definition = definition;
      this.description = description;
      this.oid = oid;
    }



     /**
     * {@inheritDoc}
     */
     @Override
    public String getSyntaxName()
    {
      // There is no name for a enum syntax.
      return null;
    }



    /**
     * {@inheritDoc}
     */
     @Override
    public String getOID()
    {
      return oid;
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
      return definition;
    }



    /**
     * {@inheritDoc}
     */
     @Override
    public String getDescription()
    {
      return description;
    }



     /**
      * {@inheritDoc}
      */
    @Override
    public void finalizeSyntax()
    {
      DirectoryServer.deregisterMatchingRule(orderingMatchingRule);
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public boolean valueIsAcceptable(ByteSequence value,
                                     MessageBuilder invalidReason)
    {
      //The value is acceptable if it belongs to the set.
      boolean isAllowed = entries.contains(value);

      if(!isAllowed)
      {
        Message message = WARN_ATTR_SYNTAX_LDAPSYNTAX_ENUM_INVALID_VALUE.get(
                value.toString(),oid);
        invalidReason.append(message);
      }

      return isAllowed;
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
    public EqualityMatchingRule getEqualityMatchingRule()
    {
      if(equalityMatchingRule == null)
      {
        //This has already been verified.
        equalityMatchingRule =
                DirectoryServer.getEqualityMatchingRule(EMR_CASE_IGNORE_OID);
      }
      return equalityMatchingRule;
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
    public OrderingMatchingRule getOrderingMatchingRule()
    {
      if(orderingMatchingRule == null)
      {
        orderingMatchingRule = new EnumOrderingMatchingRule(this, oid);
        try
        {
          DirectoryServer.registerMatchingRule(orderingMatchingRule, false);
        }
        catch(DirectoryException de)
        {
          logError(de.getMessageObject());
        }
      }
      return orderingMatchingRule;
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
    public SubstringMatchingRule getSubstringMatchingRule()
    {
      if(substringMatchingRule == null)
      {
        substringMatchingRule =
                DirectoryServer.getSubstringMatchingRule(SMR_CASE_IGNORE_OID);
      }
      return substringMatchingRule;
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
    public ApproximateMatchingRule getApproximateMatchingRule()
    {
      if(approximateMatchingRule == null)
      {
        approximateMatchingRule =
                DirectoryServer.getApproximateMatchingRule(
                                    AMR_DOUBLE_METAPHONE_OID);
      }
      return approximateMatchingRule;
    }



    //Returns the associated data structure containing the enum
    //values.
    private LinkedList<ByteSequence> getEnumValues()
    {
      return entries;
    }



    /**
      * Implementation of an Enum Ordering matching rule.
      */
    private final class EnumOrderingMatchingRule
       extends AbstractMatchingRule
       implements OrderingMatchingRule
    {
      //The enumeration syntax instance.
      private EnumSyntax syntax;


      //The oid of the matching rule.
      private String oid;


      //The name of the matching rule.
      private String name;



      static final long serialVersionUID = -2624642267131703408L;


      /**
       * Creates a new instance.
       */
      private EnumOrderingMatchingRule(EnumSyntax syntax,String oid)
      {
        super();
        this.syntax = syntax;
        this.oid = OMR_OID_GENERIC_ENUM + "." + oid;
        this.name = OMR_GENERIC_ENUM_NAME + oid;
      }



      /**
      * {@inheritDoc}
      */
      public int compare(byte[] arg0, byte[] arg1)
      {
        return compareValues(ByteString.wrap(arg0),ByteString.wrap(arg1));
      }



      /**
      * {@inheritDoc}
      */
      public int compareValues(ByteSequence value1, ByteSequence value2)
      {
        LinkedList<ByteSequence> enumValues = syntax.getEnumValues();
        return enumValues.indexOf(value1) - enumValues.indexOf(value2);
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public String getName()
      {
        return name;
      }



       /**
       * {@inheritDoc}
       */
      @Override
      public Collection<String> getAllNames()
      {
        return Collections.singleton(getName());
      }



       /**
       * {@inheritDoc}
       */
      @Override
      public String getOID()
      {
        return oid;
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public String getDescription()
      {
        return null;
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public String getSyntaxOID()
      {
        return SYNTAX_DIRECTORY_STRING_OID;
      }



      /**
       * {@inheritDoc}
       */
      @Override
      public ByteString normalizeValue(ByteSequence value)
              throws DirectoryException
      {
        StringBuilder buffer = new StringBuilder();
        prepareUnicode(buffer, value, TRIM, CASE_FOLD);

        int bufferLength = buffer.length();
        if (bufferLength == 0)
        {
          if (value.length() > 0)
          {
            // This should only happen if the value is composed entirely
            // of spaces. In that case, the normalized value is a single space.
            return SINGLE_SPACE_VALUE;
          }
          else
          {
            // The value is empty, so it is already normalized.
            return ByteString.empty();
          }
        }


        // Replace any consecutive spaces with a single space.
        for (int pos = bufferLength-1; pos > 0; pos--)
        {
          if (buffer.charAt(pos) == ' ')
          {
            if (buffer.charAt(pos-1) == ' ')
            {
              buffer.delete(pos, pos+1);
            }
          }
        }

        return ByteString.valueOf(buffer.toString());
      }
    }
  }
}
