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



import org.opends.server.admin.std.server.EqualityMatchingRuleCfg;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.config.ConfigException;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the caseIgnoreMatch matching rule defined in X.520 and
 * referenced in RFC 2252.
 */
public class CaseIgnoreEqualityMatchingRule
       extends EqualityMatchingRule
{
  /**
   * Creates a new instance of this caseIgnoreMatch matching rule.
   */
  public CaseIgnoreEqualityMatchingRule()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  public void initializeMatchingRule(EqualityMatchingRuleCfg configuration)
         throws ConfigException, InitializationException
  {
    // No initialization is required.
  }



  /**
   * Retrieves the common name for this matching rule.
   *
   * @return  The common name for this matching rule, or <CODE>null</CODE> if
   * it does not have a name.
   */
  public String getName()
  {
    return EMR_CASE_IGNORE_NAME;
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  public String getOID()
  {
    return EMR_CASE_IGNORE_OID;
  }



  /**
   * Retrieves the description for this matching rule.
   *
   * @return  The description for this matching rule, or <CODE>null</CODE> if
   *          there is none.
   */
  public String getDescription()
  {
    // There is no standard description for this matching rule.
    return null;
  }



  /**
   * Retrieves the OID of the syntax with which this matching rule is
   * associated.
   *
   * @return  The OID of the syntax with which this matching rule is associated.
   */
  public String getSyntaxOID()
  {
    return SYNTAX_DIRECTORY_STRING_OID;
  }



  /**
   * Retrieves the normalized form of the provided value, which is best suited
   * for efficiently performing matching operations on that value.
   *
   * @param  value  The value to be normalized.
   *
   * @return  The normalized version of the provided value.
   *
   * @throws  DirectoryException  If the provided value is invalid according to
   *                              the associated attribute syntax.
   */
  public ByteString normalizeValue(ByteString value)
         throws DirectoryException
  {
    byte[]        valueBytes  = value.value();
    int           valueLength = valueBytes.length;

    // Find the first non-space character.
    int startPos = 0;
    while ((startPos < valueLength) && (valueBytes[startPos] == ' '))
    {
      startPos++;
    }

    if (startPos == valueLength)
    {
      // This should only happen if the value is composed entirely of spaces.
      // In that case, the normalized value is a single space.
      return new ASN1OctetString(" ");
    }


    // Find the last non-space character;
    int endPos = (valueLength-1);
    while ((endPos > startPos) && (valueBytes[endPos] == ' '))
    {
      endPos--;
    }


    // Assume that the value contains only ASCII characters and iterate through
    // it a character at a time, converting uppercase letters to lowercase.  If
    // we find a non-ASCII character, then fall back on a more correct method.
    StringBuilder buffer = new StringBuilder(endPos-startPos+1);
    boolean lastWasSpace = false;
    for (int i=startPos; i <= endPos; i++)
    {
      byte b = valueBytes[i];
      if ((b & 0x7F) != b)
      {
        return normalizeNonASCII(value);
      }

      switch (b)
      {
        case ' ':
          if (! lastWasSpace)
          {
            buffer.append(' ');
            lastWasSpace = true;
          }
          break;
        case 'A':
          buffer.append('a');
          lastWasSpace = false;
          break;
        case 'B':
          buffer.append('b');
          lastWasSpace = false;
          break;
        case 'C':
          buffer.append('c');
          lastWasSpace = false;
          break;
        case 'D':
          buffer.append('d');
          lastWasSpace = false;
          break;
        case 'E':
          buffer.append('e');
          lastWasSpace = false;
          break;
        case 'F':
          buffer.append('f');
          lastWasSpace = false;
          break;
        case 'G':
          buffer.append('g');
          lastWasSpace = false;
          break;
        case 'H':
          buffer.append('h');
          lastWasSpace = false;
          break;
        case 'I':
          buffer.append('i');
          lastWasSpace = false;
          break;
        case 'J':
          buffer.append('j');
          lastWasSpace = false;
          break;
        case 'K':
          buffer.append('k');
          lastWasSpace = false;
          break;
        case 'L':
          buffer.append('l');
          lastWasSpace = false;
          break;
        case 'M':
          buffer.append('m');
          lastWasSpace = false;
          break;
        case 'N':
          buffer.append('n');
          lastWasSpace = false;
          break;
        case 'O':
          buffer.append('o');
          lastWasSpace = false;
          break;
        case 'P':
          buffer.append('p');
          lastWasSpace = false;
          break;
        case 'Q':
          buffer.append('q');
          lastWasSpace = false;
          break;
        case 'R':
          buffer.append('r');
          lastWasSpace = false;
          break;
        case 'S':
          buffer.append('s');
          lastWasSpace = false;
          break;
        case 'T':
          buffer.append('t');
          lastWasSpace = false;
          break;
        case 'U':
          buffer.append('u');
          lastWasSpace = false;
          break;
        case 'V':
          buffer.append('v');
          lastWasSpace = false;
          break;
        case 'W':
          buffer.append('w');
          lastWasSpace = false;
          break;
        case 'X':
          buffer.append('x');
          lastWasSpace = false;
          break;
        case 'Y':
          buffer.append('y');
          lastWasSpace = false;
          break;
        case 'Z':
          buffer.append('z');
          lastWasSpace = false;
          break;
        default:
          buffer.append((char) b);
          lastWasSpace = false;
          break;
      }
    }


    return new ASN1OctetString(buffer.toString());
  }



  /**
   * Normalizes a value that contains a non-ASCII string.
   *
   * @param  value  The non-ASCII value to normalize.
   *
   * @return  The normalized form of the provided value.
   */
  private ByteString normalizeNonASCII(ByteString value)
  {
    StringBuilder buffer = new StringBuilder();
    toLowerCase(value.value(), buffer, true);

    int bufferLength = buffer.length();
    if (bufferLength == 0)
    {
      if (value.value().length > 0)
      {
        // This should only happen if the value is composed entirely of spaces.
        // In that case, the normalized value is a single space.
        return new ASN1OctetString(" ");
      }
      else
      {
        // The value is empty, so it is already normalized.
        return new ASN1OctetString();
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

    return new ASN1OctetString(buffer.toString());
  }



  /**
   * Indicates whether the two provided normalized values are equal to each
   * other.
   *
   * @param  value1  The normalized form of the first value to compare.
   * @param  value2  The normalized form of the second value to compare.
   *
   * @return  <CODE>true</CODE> if the provided values are equal, or
   *          <CODE>false</CODE> if not.
   */
  public boolean areEqual(ByteString value1, ByteString value2)
  {
    byte[] b1 = value1.value();
    byte[] b2 = value2.value();

    int length = b1.length;
    if (b2.length != length)
    {
      return false;
    }

    for (int i=0; i < length; i++)
    {
      if (b1[i] != b2[i])
      {
        return false;
      }
    }

    return true;
  }
}

