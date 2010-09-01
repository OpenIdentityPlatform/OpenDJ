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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.sdk;



import static com.sun.opends.sdk.messages.Messages.*;
import static com.sun.opends.sdk.util.StaticUtils.*;

import org.opends.sdk.schema.*;

import com.sun.opends.sdk.util.StaticUtils;
import com.sun.opends.sdk.util.SubstringReader;
import com.sun.opends.sdk.util.Validator;



/**
 * An attribute value assertion (AVA) as defined in RFC 4512 section 2.3
 * consists of an attribute description with zero options and an attribute
 * value.
 * <p>
 * The following are examples of string representations of AVAs:
 *
 * <pre>
 * uid=12345
 * ou=Engineering
 * cn=Kurt Zeilenga
 * </pre>
 *
 * @see <a href="http://tools.ietf.org/html/rfc4512#section-2.3">RFC 4512 -
 *      Lightweight Directory Access Protocol (LDAP): Directory Information
 *      Models </a>
 */
public final class AVA implements Comparable<AVA>
{
  /**
   * Parses the provided LDAP string representation of an AVA using the default
   * schema.
   *
   * @param ava
   *          The LDAP string representation of an AVA.
   * @return The parsed RDN.
   * @throws LocalizedIllegalArgumentException
   *           If {@code ava} is not a valid LDAP string representation of a
   *           AVA.
   * @throws NullPointerException
   *           If {@code ava} was {@code null}.
   */
  public static AVA valueOf(final String ava)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    return valueOf(ava, Schema.getDefaultSchema());
  }



  /**
   * Parses the provided LDAP string representation of an AVA using the provided
   * schema.
   *
   * @param ava
   *          The LDAP string representation of a AVA.
   * @param schema
   *          The schema to use when parsing the AVA.
   * @return The parsed AVA.
   * @throws LocalizedIllegalArgumentException
   *           If {@code ava} is not a valid LDAP string representation of a
   *           AVA.
   * @throws NullPointerException
   *           If {@code ava} or {@code schema} was {@code null}.
   */
  public static AVA valueOf(final String ava, final Schema schema)
      throws LocalizedIllegalArgumentException, NullPointerException
  {
    final SubstringReader reader = new SubstringReader(ava);
    try
    {
      return decode(reader, schema);
    }
    catch (final UnknownSchemaElementException e)
    {
      final LocalizableMessage message = ERR_RDN_TYPE_NOT_FOUND.get(ava, e
          .getMessageObject());
      throw new LocalizedIllegalArgumentException(message);
    }
  }



  static AVA decode(final SubstringReader reader, final Schema schema)
      throws LocalizedIllegalArgumentException, UnknownSchemaElementException
  {
    // Skip over any spaces at the beginning.
    reader.skipWhitespaces();

    final AttributeType attribute = readAttributeName(reader, schema);

    // Make sure that we're not at the end of the DN string because
    // that would be invalid.
    if (reader.remaining() == 0)
    {
      final LocalizableMessage message = ERR_ATTR_SYNTAX_DN_END_WITH_ATTR_NAME
          .get(reader.getString(), attribute.getNameOrOID());
      throw new LocalizedIllegalArgumentException(message);
    }

    // Skip over any spaces if we have.
    reader.skipWhitespaces();

    // The next character must be an equal sign. If it is not, then
    // that's an error.
    char c;
    if ((c = reader.read()) != '=')
    {
      final LocalizableMessage message = ERR_ATTR_SYNTAX_DN_NO_EQUAL.get(reader
          .getString(), attribute.getNameOrOID(), c);
      throw new LocalizedIllegalArgumentException(message);
    }

    // Skip over any spaces after the equal sign.
    reader.skipWhitespaces();

    // Parse the value for this RDN component.
    final ByteString value = readAttributeValue(reader);

    return new AVA(attribute, value);
  }



  private static void appendHexChars(final SubstringReader reader,
      final StringBuilder valueBuffer, final StringBuilder hexBuffer)
      throws DecodeException
  {
    final int length = hexBuffer.length();
    if (length == 0)
    {
      return;
    }

    if ((length % 2) != 0)
    {
      final LocalizableMessage message = ERR_HEX_DECODE_INVALID_LENGTH
          .get(hexBuffer);
      DecodeException.error(message);
    }

    int pos = 0;
    final int arrayLength = (length / 2);
    final byte[] hexArray = new byte[arrayLength];
    for (int i = 0; i < arrayLength; i++)
    {
      switch (hexBuffer.charAt(pos++))
      {
      case '0':
        hexArray[i] = 0x00;
        break;
      case '1':
        hexArray[i] = 0x10;
        break;
      case '2':
        hexArray[i] = 0x20;
        break;
      case '3':
        hexArray[i] = 0x30;
        break;
      case '4':
        hexArray[i] = 0x40;
        break;
      case '5':
        hexArray[i] = 0x50;
        break;
      case '6':
        hexArray[i] = 0x60;
        break;
      case '7':
        hexArray[i] = 0x70;
        break;
      case '8':
        hexArray[i] = (byte) 0x80;
        break;
      case '9':
        hexArray[i] = (byte) 0x90;
        break;
      case 'A':
      case 'a':
        hexArray[i] = (byte) 0xA0;
        break;
      case 'B':
      case 'b':
        hexArray[i] = (byte) 0xB0;
        break;
      case 'C':
      case 'c':
        hexArray[i] = (byte) 0xC0;
        break;
      case 'D':
      case 'd':
        hexArray[i] = (byte) 0xD0;
        break;
      case 'E':
      case 'e':
        hexArray[i] = (byte) 0xE0;
        break;
      case 'F':
      case 'f':
        hexArray[i] = (byte) 0xF0;
        break;
      default:
        final LocalizableMessage message = ERR_HEX_DECODE_INVALID_CHARACTER
            .get(hexBuffer, hexBuffer.charAt(pos - 1));
        throw DecodeException.error(message);
      }

      switch (hexBuffer.charAt(pos++))
      {
      case '0':
        // No action required.
        break;
      case '1':
        hexArray[i] |= 0x01;
        break;
      case '2':
        hexArray[i] |= 0x02;
        break;
      case '3':
        hexArray[i] |= 0x03;
        break;
      case '4':
        hexArray[i] |= 0x04;
        break;
      case '5':
        hexArray[i] |= 0x05;
        break;
      case '6':
        hexArray[i] |= 0x06;
        break;
      case '7':
        hexArray[i] |= 0x07;
        break;
      case '8':
        hexArray[i] |= 0x08;
        break;
      case '9':
        hexArray[i] |= 0x09;
        break;
      case 'A':
      case 'a':
        hexArray[i] |= 0x0A;
        break;
      case 'B':
      case 'b':
        hexArray[i] |= 0x0B;
        break;
      case 'C':
      case 'c':
        hexArray[i] |= 0x0C;
        break;
      case 'D':
      case 'd':
        hexArray[i] |= 0x0D;
        break;
      case 'E':
      case 'e':
        hexArray[i] |= 0x0E;
        break;
      case 'F':
      case 'f':
        hexArray[i] |= 0x0F;
        break;
      default:
        final LocalizableMessage message = ERR_HEX_DECODE_INVALID_CHARACTER
            .get(hexBuffer, hexBuffer.charAt(pos - 1));
        throw DecodeException.error(message);
      }
    }
    try
    {
      valueBuffer.append(new String(hexArray, "UTF-8"));
    }
    catch (final Exception e)
    {
      final LocalizableMessage message = ERR_ATTR_SYNTAX_DN_ATTR_VALUE_DECODE_FAILURE
          .get(reader.getString(), String.valueOf(e));
      throw DecodeException.error(message);
    }
    // Clean up the hex buffer.
    hexBuffer.setLength(0);
  }



  private static ByteString delimitAndEvaluateEscape(
      final SubstringReader reader) throws DecodeException
  {
    char c = '\u0000';
    final StringBuilder valueBuffer = new StringBuilder();
    final StringBuilder hexBuffer = new StringBuilder();
    reader.skipWhitespaces();

    boolean escaped = false;
    while (reader.remaining() > 0)
    {
      c = reader.read();
      if (escaped)
      {
        // This character is escaped.
        if (isHexDigit(c))
        {
          // Unicode characters.
          if (!(reader.remaining() > 0))
          {
            final LocalizableMessage msg = ERR_ATTR_SYNTAX_DN_ESCAPED_HEX_VALUE_INVALID
                .get(reader.getString());
            DecodeException.error(msg);
          }
          // Check the next byte for hex.
          final char c2 = reader.read();
          if (isHexDigit(c2))
          {
            hexBuffer.append(c);
            hexBuffer.append(c2);
            // We may be at the end.
            if (reader.remaining() == 0)
            {
              appendHexChars(reader, valueBuffer, hexBuffer);
            }
          }
          else
          {
            final LocalizableMessage message = ERR_ATTR_SYNTAX_DN_ESCAPED_HEX_VALUE_INVALID
                .get(reader.getString());
            DecodeException.error(message);
          }
        }
        else
        {
          appendHexChars(reader, valueBuffer, hexBuffer);
          valueBuffer.append(c);
        }
        escaped = false;
      }
      else if (c == 0x5C) // The backslash character
      {
        // We found an escape.
        escaped = true;
      }
      else
      {
        // Check for delimited chars.
        if (c == '+' || c == ',' || c == ';')
        {
          reader.reset();
          // Return what we have got here so far.
          appendHexChars(reader, valueBuffer, hexBuffer);
          return ByteString.valueOf(valueBuffer.toString());
        }
        // It is definitely not a delimiter at this point.
        appendHexChars(reader, valueBuffer, hexBuffer);
        valueBuffer.append(c);
        // reader.mark();
      }
      reader.mark();
    }

    reader.reset();
    return ByteString.valueOf(valueBuffer.toString());
  }



  private static AttributeType readAttributeName(final SubstringReader reader,
      final Schema schema) throws LocalizedIllegalArgumentException,
      UnknownSchemaElementException
  {
    int length = 1;
    reader.mark();

    // The next character must be either numeric (for an OID) or
    // alphabetic (for an attribute description).
    char c = reader.read();
    if (isDigit(c))
    {
      boolean lastWasPeriod = false;
      while (reader.remaining() > 0 && (c = reader.read()) != '=')
      {
        if (c == '.')
        {
          if (lastWasPeriod)
          {
            final LocalizableMessage message = ERR_ATTR_SYNTAX_OID_CONSECUTIVE_PERIODS
                .get(reader.getString(), reader.pos() - 1);
            throw new LocalizedIllegalArgumentException(message);
          }
          else
          {
            lastWasPeriod = true;
          }
        }
        else if (!isDigit(c))
        {
          // This must have been an illegal character.
          final LocalizableMessage message = ERR_ATTR_SYNTAX_OID_ILLEGAL_CHARACTER
              .get(reader.getString(), reader.pos() - 1);
          throw new LocalizedIllegalArgumentException(message);
        }
        else
        {
          lastWasPeriod = false;
        }
        length++;
      }
    }
    else if (isAlpha(c))
    {
      // This must be an attribute description. In this case, we will
      // only accept alphabetic characters, numeric digits, and the hyphen.
      while (reader.remaining() > 0)
      {
        c = reader.read();
        if (length == 0 && !isAlpha(c))
        {
          // This is an illegal character.
          final LocalizableMessage message = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR
              .get(reader.getString(), c, reader.pos() - 1);
          throw new LocalizedIllegalArgumentException(message);
        }

        if (c == '=')
        {
          // End of the attribute.
          break;
        }
        else if (c == ' ')
        {
          // Got a whitespace.It MUST be the end of the attribute
          // Make sure that the next non-whitespace character is '='.
          reader.skipWhitespaces();
          // Read back the next char.
          c = reader.read();
          if (c == '=')
          {
            break;
          }
          else
          {
            // This is an illegal character.
            final LocalizableMessage message = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR
                .get(reader.getString(), c, reader.pos() - 1);
            throw new LocalizedIllegalArgumentException(message);
          }
        }
        else if (!isAlpha(c) && !isDigit(c) && c != '-')
        {
          // This is an illegal character.
          final LocalizableMessage message = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR
              .get(reader.getString(), c, reader.pos() - 1);
          throw new LocalizedIllegalArgumentException(message);
        }

        length++;
      }
    }
    else
    {
      final LocalizableMessage message = ERR_ATTR_SYNTAX_DN_ATTR_ILLEGAL_CHAR
          .get(reader.getString(), c, reader.pos() - 1);
      throw new LocalizedIllegalArgumentException(message);
    }

    reader.reset();

    // Return the position of the first non-space character after the
    // token.

    return schema.getAttributeType(reader.read(length));
  }



  private static ByteString readAttributeValue(final SubstringReader reader)
      throws LocalizedIllegalArgumentException
  {
    // All leading spaces have already been stripped so we can start
    // reading the value. However, it may be empty so check for that.
    if (reader.remaining() == 0)
    {
      return ByteString.empty();
    }

    reader.mark();

    // Look at the first character. If it is an octothorpe (#), then
    // that means that the value should be a hex string.
    char c = reader.read();
    int length = 0;
    if (c == '#')
    {
      // The first two characters must be hex characters.
      reader.mark();
      if (reader.remaining() < 2)
      {
        final LocalizableMessage message = ERR_ATTR_SYNTAX_DN_HEX_VALUE_TOO_SHORT
            .get(reader.getString());
        throw new LocalizedIllegalArgumentException(message);
      }

      for (int i = 0; i < 2; i++)
      {
        c = reader.read();
        if (isHexDigit(c))
        {
          length++;
        }
        else
        {
          final LocalizableMessage message = ERR_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT
              .get(reader.getString(), c);
          throw new LocalizedIllegalArgumentException(message);
        }
      }

      // The rest of the value must be a multiple of two hex
      // characters. The end of the value may be designated by the
      // end of the DN, a comma or semicolon, or a space.
      while (reader.remaining() > 0)
      {
        c = reader.read();
        if (isHexDigit(c))
        {
          length++;

          if (reader.remaining() > 0)
          {
            c = reader.read();
            if (isHexDigit(c))
            {
              length++;
            }
            else
            {
              final LocalizableMessage message = ERR_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT
                  .get(reader.getString(), c);
              throw new LocalizedIllegalArgumentException(message);
            }
          }
          else
          {
            final LocalizableMessage message = ERR_ATTR_SYNTAX_DN_HEX_VALUE_TOO_SHORT
                .get(reader.getString());
            throw new LocalizedIllegalArgumentException(message);
          }
        }
        else if ((c == ' ') || (c == ',') || (c == ';'))
        {
          // This denotes the end of the value.
          break;
        }
        else
        {
          final LocalizableMessage message = ERR_ATTR_SYNTAX_DN_INVALID_HEX_DIGIT
              .get(reader.getString(), c);
          throw new LocalizedIllegalArgumentException(message);
        }
      }

      // At this point, we should have a valid hex string. Convert it
      // to a byte array and set that as the value of the provided
      // octet string.
      try
      {
        reader.reset();
        return ByteString.wrap(hexStringToByteArray(reader.read(length)));
      }
      catch (final Exception e)
      {
        final LocalizableMessage message = ERR_ATTR_SYNTAX_DN_ATTR_VALUE_DECODE_FAILURE
            .get(reader.getString(), String.valueOf(e));
        throw new LocalizedIllegalArgumentException(message);
      }
    }

    // If the first character is a quotation mark, then the value
    // should continue until the corresponding closing quotation mark.
    else if (c == '"')
    {
      reader.mark();
      while (true)
      {
        if (reader.remaining() <= 0)
        {
          // We hit the end of the AVA before the closing quote.
          // That's an error.
          final LocalizableMessage message = ERR_ATTR_SYNTAX_DN_UNMATCHED_QUOTE
              .get(reader.getString());
          throw new LocalizedIllegalArgumentException(message);
        }

        if (reader.read() == '"')
        {
          // This is the end of the value.
          break;
        }
        length++;
      }
      reader.reset();
      final ByteString retString = ByteString.valueOf(reader.read(length));
      reader.read();
      return retString;
    }

    // Otherwise, use general parsing to find the end of the value.
    else
    {
      reader.reset();
      ByteString bytes;
      try
      {
        bytes = delimitAndEvaluateEscape(reader);
      }
      catch (final DecodeException e)
      {
        throw new LocalizedIllegalArgumentException(e.getMessageObject());
      }
      if (bytes.length() == 0)
      {
        // We don't allow an empty attribute value.
        final LocalizableMessage message = ERR_ATTR_SYNTAX_DN_INVALID_REQUIRES_ESCAPE_CHAR
            .get(reader.getString(), reader.pos());
        throw new LocalizedIllegalArgumentException(message);
      }
      return bytes;
    }
  }



  private final AttributeType attributeType;

  private final ByteString attributeValue;



  /**
   * Creates a new attribute value assertion (AVA) using the provided attribute
   * type and value.
   *
   * @param attributeType
   *          The attribute type.
   * @param attributeValue
   *          The attribute value.
   * @throws NullPointerException
   *           If {@code attributeType} or {@code attributeValue} was {@code
   *           null}.
   */
  public AVA(final AttributeType attributeType, final ByteString attributeValue)
      throws NullPointerException
  {
    Validator.ensureNotNull(attributeType, attributeValue);

    this.attributeType = attributeType;
    this.attributeValue = attributeValue;
  }



  /**
   * Creates a new attribute value assertion (AVA) using the provided attribute
   * type and value decoded using the default schema.
   * <p>
   * If {@code attributeValue} is not an instance of {@code ByteString} then it
   * will be converted using the {@link ByteString#valueOf(Object)} method.
   *
   * @param attributeType
   *          The attribute type.
   * @param attributeValue
   *          The attribute value.
   * @throws UnknownSchemaElementException
   *           If {@code attributeType} was not found in the default schema.
   * @throws NullPointerException
   *           If {@code attributeType} or {@code attributeValue} was {@code
   *           null}.
   */
  public AVA(final String attributeType, final Object attributeValue)
      throws UnknownSchemaElementException, NullPointerException
  {
    Validator.ensureNotNull(attributeType, attributeValue);

    this.attributeType = Schema.getDefaultSchema().getAttributeType(
        attributeType);
    this.attributeValue = ByteString.valueOf(attributeValue);
  }



  /**
   * {@inheritDoc}
   */
  public int compareTo(final AVA ava)
  {
    int result = attributeType.compareTo(ava.attributeType);
    if (result != 0)
    {
      return result > 0 ? 1 : -1;
    }
    final ByteString normalizedValue = getNormalizeValue();
    final MatchingRule rule = attributeType.getOrderingMatchingRule();
    try
    {
      if (rule != null)
      {
        // Check equality assertion first.
        final Assertion lteAssertion = rule
            .getLessOrEqualAssertion(ava.attributeValue);
        final ConditionResult lteResult = lteAssertion.matches(normalizedValue);
        final Assertion gteAssertion = rule
            .getGreaterOrEqualAssertion(ava.attributeValue);
        final ConditionResult gteResult = gteAssertion.matches(normalizedValue);

        if (lteResult.equals(gteResult))
        {
          // it is equal to the assertion value.
          return 0;
        }
        else if (lteResult == ConditionResult.TRUE)
        {
          return -1;
        }
        else
        {
          return 1;
        }
      }
    }
    catch (final DecodeException de)
    {
      // use the bytestring comparison as default.
    }

    if (result == 0)
    {
      final ByteString nv1 = normalizedValue;
      final ByteString nv2 = ava.getNormalizeValue();
      result = nv1.compareTo(nv2);
    }

    return result;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean equals(final Object obj)
  {
    if (this == obj)
    {
      return true;
    }
    else if (obj instanceof AVA)
    {
      return compareTo((AVA) obj) == 0;
    }
    else
    {
      return false;
    }
  }



  /**
   * Returns the attribute type associated with this AVA.
   *
   * @return The attribute type associated with this AVA.
   */
  public AttributeType getAttributeType()
  {
    return attributeType;
  }



  /**
   * Returns the attribute value associated with this AVA.
   *
   * @return The attribute value associated with this AVA.
   */
  public ByteString getAttributeValue()
  {
    return attributeValue;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode()
  {
    return attributeType.hashCode() * 31 + getNormalizeValue().hashCode();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    final StringBuilder builder = new StringBuilder();
    return toString(builder).toString();
  }



  private ByteString getNormalizeValue()
  {
    final MatchingRule matchingRule = attributeType.getEqualityMatchingRule();
    if (matchingRule != null)
    {
      try
      {
        return matchingRule.normalizeAttributeValue(attributeValue);
      }
      catch (final DecodeException de)
      {
        // Ignore - we'll drop back to the user provided value.
      }
    }
    return attributeValue;
  }



  StringBuilder toString(final StringBuilder builder)
  {
    if (!attributeType.getNames().iterator().hasNext())
    {
      builder.append(attributeType.getOID());
      builder.append("=#");
      StaticUtils.toHex(attributeValue, builder);
    }
    else
    {
      final String name = attributeType.getNameOrOID();
      builder.append(name);
      builder.append("=");

      final Syntax syntax = attributeType.getSyntax();
      if (!syntax.isHumanReadable())
      {
        builder.append("#");
        StaticUtils.toHex(attributeValue, builder);
      }
      else
      {
        final String str = attributeValue.toString();
        if (str.length() == 0)
        {
          return builder;
        }
        char c = str.charAt(0);
        int startPos = 0;
        if ((c == ' ') || (c == '#'))
        {
          builder.append('\\');
          builder.append(c);
          startPos = 1;
        }
        final int length = str.length();
        for (int si = startPos; si < length; si++)
        {
          c = str.charAt(si);
          if (c < ' ')
          {
            for (final byte b : getBytes(String.valueOf(c)))
            {
              builder.append('\\');
              builder.append(StaticUtils.byteToLowerHex(b));
            }
          }
          else
          {
            if ((c == ' ' && si == length - 1)
                || (c == '"' || c == '+' || c == ',' || c == ';' || c == '<'
                    || c == '=' || c == '>' || c == '\\' || c == '\u0000'))
            {
              builder.append('\\');
            }
            builder.append(c);
          }
        }
      }
    }
    return builder;
  }
}
