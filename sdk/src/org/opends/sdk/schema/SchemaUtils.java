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



import static com.sun.opends.sdk.messages.Messages.*;
import static com.sun.opends.sdk.util.StaticUtils.*;

import java.util.*;

import org.opends.sdk.DecodeException;
import org.opends.sdk.LocalizableMessage;

import com.sun.opends.sdk.util.SubstringReader;



/**
 * Schema utility methods.
 */
final class SchemaUtils
{
  /**
   * Reads the value for an "extra" parameter. It will handle a single
   * unquoted word (which is technically illegal, but we'll allow it), a
   * single quoted string, or an open parenthesis followed by a
   * space-delimited set of quoted strings or unquoted words followed by
   * a close parenthesis.
   * 
   * @param reader
   *          The string representation of the definition.
   * @return The "extra" parameter value that was read.
   * @throws DecodeException
   *           If a problem occurs while attempting to read the value.
   */
  static List<String> readExtensions(SubstringReader reader)
      throws DecodeException
  {
    int length = 0;
    List<String> values;

    // Skip over any leading spaces.
    reader.skipWhitespaces();
    reader.mark();

    try
    {
      // Look at the next character. If it is a quote, then parse until
      // the next quote and end. If it is an open parenthesis, then
      // parse individual values until the close parenthesis and end.
      // Otherwise, parse until the next space and end.
      char c = reader.read();
      if (c == '\'')
      {
        reader.mark();
        // Parse until the closing quote.
        while (reader.read() != '\'')
        {
          length++;
        }

        reader.reset();
        values = Collections.singletonList(reader.read(length));
        reader.read();
      }
      else if (c == '(')
      {
        // Skip over any leading spaces;
        reader.skipWhitespaces();
        reader.mark();

        c = reader.read();
        if (c == ')')
        {
          values = Collections.emptyList();
        }
        else
        {
          values = new ArrayList<String>();
          do
          {
            reader.reset();
            values.add(readQuotedString(reader));
            reader.skipWhitespaces();
            reader.mark();
          }
          while (reader.read() != ')');
        }
      }
      else
      {
        // Parse until the next space.
        do
        {
          length++;
        }
        while (reader.read() != ' ');

        reader.reset();
        values = Collections.singletonList(reader.read(length));
      }

      return values;
    }
    catch (final StringIndexOutOfBoundsException e)
    {
      final LocalizableMessage message = ERR_ATTR_SYNTAX_TRUNCATED_VALUE.get();
      throw DecodeException.error(message);
    }
  }



  static List<String> readNameDescriptors(SubstringReader reader)
      throws DecodeException
  {
    int length = 0;
    List<String> values;

    // Skip over any spaces at the beginning of the value.
    reader.skipWhitespaces();

    try
    {
      char c = reader.read();
      if (c == '\'')
      {
        reader.mark();
        // Parse until the closing quote.
        while (reader.read() != '\'')
        {
          length++;
        }

        reader.reset();
        values = Collections.singletonList(reader.read(length));
        reader.read();
      }
      else if (c == '(')
      {
        // Skip over any leading spaces;
        reader.skipWhitespaces();
        reader.mark();

        c = reader.read();
        if (c == ')')
        {
          values = Collections.emptyList();
        }
        else
        {
          values = new LinkedList<String>();
          do
          {
            reader.reset();
            values.add(readQuotedDescriptor(reader));
            reader.skipWhitespaces();
            reader.mark();
          }
          while (reader.read() != ')');
        }
      }
      else
      {
        final LocalizableMessage message =
            ERR_ATTR_SYNTAX_ILLEGAL_CHAR_IN_STRING_OID.get(String
                .valueOf(c), reader.pos() - 1);
        throw DecodeException.error(message);
      }

      return values;
    }
    catch (final StringIndexOutOfBoundsException e)
    {
      final LocalizableMessage message = ERR_ATTR_SYNTAX_TRUNCATED_VALUE.get();
      throw DecodeException.error(message);
    }
  }



  /**
   * Reads the next OID from the definition, skipping over any leading
   * spaces.
   * 
   * @param reader
   *          The string representation of the definition.
   * @return The OID read from the definition.
   * @throws DecodeException
   *           If a problem is encountered while reading the token name.
   */
  static String readNumericOID(SubstringReader reader)
      throws DecodeException
  {
    // This must be a numeric OID. In that case, we will accept
    // only digits and periods, but not consecutive periods.
    boolean lastWasPeriod = false;
    int length = 0;

    // Skip over any spaces at the beginning of the value.
    reader.skipWhitespaces();
    reader.mark();

    try
    {
      char c;
      while ((c = reader.read()) != ' ' && c != '\'')
      {
        if (c == '.')
        {
          if (lastWasPeriod)
          {
            final LocalizableMessage message =
                ERR_ATTR_SYNTAX_OID_CONSECUTIVE_PERIODS.get(reader
                    .getString(), reader.pos() - 1);
            throw DecodeException.error(message);
          }
          else
          {
            lastWasPeriod = true;
          }
        }
        else if (!isDigit(c))
        {
          // Technically, this must be an illegal character. However, it
          // is possible that someone just got sloppy and did not
          // include a space between the name/OID and a closing
          // parenthesis. In that case, we'll assume it's the end of the
          // value.
          if (c == ')')
          {
            break;
          }

          // This must have been an illegal character.
          final LocalizableMessage message =
              ERR_ATTR_SYNTAX_OID_ILLEGAL_CHARACTER.get(reader
                  .getString(), reader.pos() - 1);
          throw DecodeException.error(message);
        }
        else
        {
          lastWasPeriod = false;
        }
        length++;
      }

      if (length == 0)
      {
        final LocalizableMessage message = ERR_ATTR_SYNTAX_OID_NO_VALUE.get();
        throw DecodeException.error(message);
      }

      reader.reset();

      return reader.read(length);
    }
    catch (final StringIndexOutOfBoundsException e)
    {
      final LocalizableMessage message = ERR_ATTR_SYNTAX_TRUNCATED_VALUE.get();
      throw DecodeException.error(message);
    }
  }



  /**
   * Reads the attribute description or numeric OID, skipping over any
   * leading or trailing spaces.
   * 
   * @param reader
   *          The string representation of the definition.
   * @return The attribute description or numeric OID read from the
   *         definition.
   * @throws DecodeException
   *           If a problem is encountered while reading the name or
   *           OID.
   */
  static String readOID(SubstringReader reader) throws DecodeException
  {
    int length = 1;
    boolean enclosingQuote = false;
    String oid;

    // Skip over any spaces at the beginning of the value.
    reader.skipWhitespaces();
    reader.mark();

    try
    {
      // The next character must be either numeric (for an OID) or
      // alphabetic (for an attribute description).
      char c = reader.read();
      if (c == '\'')
      {
        enclosingQuote = true;
        reader.mark();
        c = reader.read();
      }
      if (isDigit(c))
      {
        reader.reset();
        oid = readNumericOID(reader);
      }

      else if (isAlpha(c))
      {
        // This must be an attribute description. In this case, we will
        // only accept alphabetic characters, numeric digits, and the
        // hyphen.
        while (reader.remaining() > 0 && (c = reader.read()) != ' '
            && c != ')' && !(c == '\'' && enclosingQuote))
        {
          if (length == 0 && !isAlpha(c))
          {
            // This is an illegal character.
            final LocalizableMessage message =
                ERR_ATTR_SYNTAX_ILLEGAL_CHAR_IN_STRING_OID.get(String
                    .valueOf(c), reader.pos() - 1);
            throw DecodeException.error(message);
          }

          if (!isAlpha(c) && !isDigit(c) && c != '-' && c != '.'
              && c != '_')
          {
            // This is an illegal character.
            final LocalizableMessage message =
                ERR_ATTR_SYNTAX_ILLEGAL_CHAR_IN_STRING_OID.get(String
                    .valueOf(c), reader.pos() - 1);
            throw DecodeException.error(message);
          }

          length++;
        }

        reader.reset();

        // Return the position of the first non-space character after
        // the token.
        oid = reader.read(length);
      }
      else
      {
        final LocalizableMessage message =
            ERR_ATTR_SYNTAX_ILLEGAL_CHAR_IN_STRING_OID.get(String
                .valueOf(c), reader.pos() - 1);
        throw DecodeException.error(message);
      }

      if (enclosingQuote)
      {
        reader.read();
      }
      return oid;
    }
    catch (final StringIndexOutOfBoundsException e)
    {
      final LocalizableMessage message = ERR_ATTR_SYNTAX_TRUNCATED_VALUE.get();
      throw DecodeException.error(message);
    }
  }



  /**
   * Reads the next OID from the definition, skipping over any leading
   * spaces. The OID may be followed by a integer length in brackets.
   * 
   * @param reader
   *          The string representation of the definition.
   * @return The OID read from the definition.
   * @throws DecodeException
   *           If a problem is encountered while reading the token name.
   */
  static String readOIDLen(SubstringReader reader)
      throws DecodeException
  {
    int length = 1;
    boolean enclosingQuote = false;

    // Skip over any spaces at the beginning of the value.
    reader.skipWhitespaces();
    reader.mark();

    try
    {
      // The next character must be either numeric (for an OID) or
      // alphabetic (for an attribute description).
      char c = reader.read();
      if (c == '\'')
      {
        enclosingQuote = true;
        reader.mark();
        c = reader.read();
      }
      if (isDigit(c))
      {
        boolean lastWasPeriod = false;
        while ((c = reader.read()) != ' ' && c != '{'
            && !(c == '\'' && enclosingQuote))
        {
          if (c == '.')
          {
            if (lastWasPeriod)
            {
              final LocalizableMessage message =
                  ERR_ATTR_SYNTAX_OID_CONSECUTIVE_PERIODS.get(reader
                      .getString(), reader.pos() - 1);
              throw DecodeException.error(message);
            }
            else
            {
              lastWasPeriod = true;
            }
          }
          else if (!isDigit(c))
          {
            // Technically, this must be an illegal character. However,
            // it is possible that someone just got sloppy and did not
            // include a space between the name/OID and a closing
            // parenthesis. In that case, we'll assume it's the end of
            // the value.
            if (c == ')')
            {
              break;
            }

            // This must have been an illegal character.
            final LocalizableMessage message =
                ERR_ATTR_SYNTAX_OID_ILLEGAL_CHARACTER.get(reader
                    .getString(), reader.pos() - 1);
            throw DecodeException.error(message);
          }
          else
          {
            lastWasPeriod = false;
          }
          length++;
        }

        if (length == 0)
        {
          final LocalizableMessage message = ERR_ATTR_SYNTAX_OID_NO_VALUE.get();
          throw DecodeException.error(message);
        }
      }

      else if (isAlpha(c))
      {
        // This must be an attribute description. In this case, we will
        // only accept alphabetic characters, numeric digits, and the
        // hyphen.
        while ((c = reader.read()) != ' ' && c != ')' && c != '{'
            && !(c == '\'' && enclosingQuote))
        {
          if (length == 0 && !isAlpha(c))
          {
            // This is an illegal character.
            final LocalizableMessage message =
                ERR_ATTR_SYNTAX_ILLEGAL_CHAR_IN_STRING_OID.get(String
                    .valueOf(c), reader.pos() - 1);
            throw DecodeException.error(message);
          }

          if (!isAlpha(c) && !isDigit(c) && c != '-' && c != '.'
              && c != '_')
          {
            // This is an illegal character.
            final LocalizableMessage message =
                ERR_ATTR_SYNTAX_ILLEGAL_CHAR_IN_STRING_OID.get(String
                    .valueOf(c), reader.pos() - 1);
            throw DecodeException.error(message);
          }

          length++;
        }
      }
      else
      {
        final LocalizableMessage message =
            ERR_ATTR_SYNTAX_ILLEGAL_CHAR_IN_STRING_OID.get(String
                .valueOf(c), reader.pos() - 1);
        throw DecodeException.error(message);
      }

      reader.reset();

      // Return the position of the first non-space character after the
      // token.
      final String oid = reader.read(length);

      reader.mark();
      if ((c = reader.read()) == '{')
      {
        reader.mark();
        // The only thing we'll allow here will be numeric digits and
        // the closing curly brace.
        while ((c = reader.read()) != '}')
        {
          if (!isDigit(c))
          {
            final LocalizableMessage message =
                ERR_ATTR_SYNTAX_OID_ILLEGAL_CHARACTER.get(reader
                    .getString(), reader.pos() - 1);
            throw DecodeException.error(message);
          }
        }
      }
      else if (c == '\'')
      {
        reader.mark();
      }
      else
      {
        reader.reset();
      }

      return oid;
    }
    catch (final StringIndexOutOfBoundsException e)
    {
      final LocalizableMessage message = ERR_ATTR_SYNTAX_TRUNCATED_VALUE.get();
      throw DecodeException.error(message);
    }
  }



  static Set<String> readOIDs(SubstringReader reader)
      throws DecodeException
  {
    Set<String> values;

    // Skip over any spaces at the beginning of the value.
    reader.skipWhitespaces();
    reader.mark();

    try
    {
      final char c = reader.read();
      if (c == '(')
      {
        values = new HashSet<String>();

        do
        {
          values.add(readOID(reader));

          // Skip over any trailing spaces;
          reader.skipWhitespaces();
        }
        while (reader.read() != ')');
      }
      else
      {
        reader.reset();
        values = Collections.singleton(readOID(reader));
      }

      return values;
    }
    catch (final StringIndexOutOfBoundsException e)
    {
      final LocalizableMessage message = ERR_ATTR_SYNTAX_TRUNCATED_VALUE.get();
      throw DecodeException.error(message);
    }
  }



  /**
   * Reads the value of a string enclosed in single quotes, skipping
   * over the quotes and any leading spaces.
   * 
   * @param reader
   *          The string representation of the definition.
   * @return The string value read from the definition.
   * @throws DecodeException
   *           If a problem is encountered while reading the quoted
   *           string.
   */
  static String readQuotedDescriptor(SubstringReader reader)
      throws DecodeException
  {
    int length = 0;

    // Skip over any spaces at the beginning of the value.
    reader.skipWhitespaces();

    try
    {
      // The next character must be a single quote.
      char c = reader.read();
      if (c != '\'')
      {
        final LocalizableMessage message =
            ERR_ATTR_SYNTAX_EXPECTED_QUOTE_AT_POS.get(reader.pos() - 1,
                String.valueOf(c));
        throw DecodeException.error(message);
      }

      // Read until we find the closing quote.
      reader.mark();
      while ((c = reader.read()) != '\'')
      {
        if (length == 0 && !isAlpha(c))
        {
          // This is an illegal character.
          final LocalizableMessage message =
              ERR_ATTR_SYNTAX_ILLEGAL_CHAR_IN_STRING_OID.get(String
                  .valueOf(c), reader.pos() - 1);
          throw DecodeException.error(message);
        }

        if (!isAlpha(c) && !isDigit(c) && c != '-' && c != '_'
            && c != '.')
        {
          // This is an illegal character.
          final LocalizableMessage message =
              ERR_ATTR_SYNTAX_ILLEGAL_CHAR_IN_STRING_OID.get(String
                  .valueOf(c), reader.pos() - 1);
          throw DecodeException.error(message);
        }

        length++;
      }

      reader.reset();

      final String descr = reader.read(length);
      reader.read();
      return descr;
    }
    catch (final StringIndexOutOfBoundsException e)
    {
      final LocalizableMessage message = ERR_ATTR_SYNTAX_TRUNCATED_VALUE.get();
      throw DecodeException.error(message);
    }
  }



  /**
   * Reads the value of a string enclosed in single quotes, skipping
   * over the quotes and any leading spaces.
   * 
   * @param reader
   *          The string representation of the definition.
   * @return The string value read from the definition.
   * @throws DecodeException
   *           If a problem is encountered while reading the quoted
   *           string.
   */
  static String readQuotedString(SubstringReader reader)
      throws DecodeException
  {
    int length = 0;

    // Skip over any spaces at the beginning of the value.
    reader.skipWhitespaces();

    try
    {
      // The next character must be a single quote.
      final char c = reader.read();
      if (c != '\'')
      {
        final LocalizableMessage message =
            ERR_ATTR_SYNTAX_EXPECTED_QUOTE_AT_POS.get(reader.pos() - 1,
                String.valueOf(c));
        throw DecodeException.error(message);
      }

      // Read until we find the closing quote.
      reader.mark();
      while (reader.read() != '\'')
      {
        length++;
      }

      reader.reset();

      final String str = reader.read(length);
      reader.read();
      return str;
    }
    catch (final StringIndexOutOfBoundsException e)
    {
      final LocalizableMessage message = ERR_ATTR_SYNTAX_TRUNCATED_VALUE.get();
      throw DecodeException.error(message);
    }
  }



  /**
   * Reads the next ruleid from the definition, skipping over any
   * leading spaces.
   * 
   * @param reader
   *          The string representation of the definition.
   * @return The ruleid read from the definition.
   * @throws DecodeException
   *           If a problem is encountered while reading the token name.
   */
  static Integer readRuleID(SubstringReader reader)
      throws DecodeException
  {
    // This must be a ruleid. In that case, we will accept
    // only digits.
    int length = 0;

    // Skip over any spaces at the beginning of the value.
    reader.skipWhitespaces();
    reader.mark();

    try
    {
      while (reader.read() != ' ')
      {
        length++;
      }

      if (length == 0)
      {
        final LocalizableMessage message = ERR_ATTR_SYNTAX_RULE_ID_NO_VALUE.get();
        throw DecodeException.error(message);
      }

      reader.reset();
      final String ruleID = reader.read(length);

      try
      {
        return Integer.valueOf(ruleID);
      }
      catch (final NumberFormatException e)
      {
        final LocalizableMessage message =
            ERR_ATTR_SYNTAX_RULE_ID_INVALID.get(ruleID);
        throw DecodeException.error(message);
      }
    }
    catch (final StringIndexOutOfBoundsException e)
    {
      final LocalizableMessage message = ERR_ATTR_SYNTAX_TRUNCATED_VALUE.get();
      throw DecodeException.error(message);
    }
  }



  static Set<Integer> readRuleIDs(SubstringReader reader)
      throws DecodeException
  {
    Set<Integer> values;

    // Skip over any spaces at the beginning of the value.
    reader.skipWhitespaces();
    reader.mark();

    try
    {
      final char c = reader.read();
      if (c == '(')
      {
        values = new HashSet<Integer>();

        do
        {
          values.add(readRuleID(reader));

          // Skip over any trailing spaces;
          reader.skipWhitespaces();
        }
        while (reader.read() != ')');
      }
      else
      {
        reader.reset();
        values = Collections.singleton(readRuleID(reader));
      }

      return values;
    }
    catch (final StringIndexOutOfBoundsException e)
    {
      final LocalizableMessage message = ERR_ATTR_SYNTAX_TRUNCATED_VALUE.get();
      throw DecodeException.error(message);
    }
  }



  /**
   * Reads the next token name from the definition, skipping over any
   * leading or trailing spaces or <code>null</code> if there are no
   * moretokens to read.
   * 
   * @param reader
   *          The string representation of the definition.
   * @return The token name read from the definition or
   *         <code>null</code> .
   * @throws DecodeException
   *           If a problem is encountered while reading the token name.
   */
  static String readTokenName(SubstringReader reader)
      throws DecodeException
  {
    String token = null;
    int length = 0;
    // Skip over any spaces at the beginning of the value.
    reader.skipWhitespaces();
    reader.mark();

    try
    {
      // Read until we find the next space.
      char c;
      while ((c = reader.read()) != ' ' && c != ')')
      {
        length++;
      }

      if (length > 0)
      {
        reader.reset();
        token = reader.read(length);
      }

      // Skip over any trailing spaces after the value.
      reader.skipWhitespaces();

      if (token == null && reader.remaining() > 0)
      {
        reader.reset();
        final LocalizableMessage message =
            ERR_ATTR_SYNTAX_UNEXPECTED_CLOSE_PARENTHESIS.get(length);
        throw DecodeException.error(message);
      }

      return token;
    }
    catch (final StringIndexOutOfBoundsException e)
    {
      final LocalizableMessage message = ERR_ATTR_SYNTAX_TRUNCATED_VALUE.get();
      throw DecodeException.error(message);
    }
  }



  // Prevent instantiation.
  private SchemaUtils()
  {
    // Nothing to do.
  }
}
