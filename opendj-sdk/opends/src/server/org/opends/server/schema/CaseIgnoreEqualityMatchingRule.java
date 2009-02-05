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



import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.Collection;
import java.util.Collections;

import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.types.ByteSequence;
import org.opends.server.types.ByteString;
import org.opends.server.types.DirectoryException;
import org.opends.server.util.ServerConstants;



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
  @Override
  public Collection<String> getAllNames()
  {
    return Collections.singleton(getName());
  }



  /**
   * Retrieves the common name for this matching rule.
   *
   * @return  The common name for this matching rule, or <CODE>null</CODE> if
   * it does not have a name.
   */
  @Override
  public String getName()
  {
    return EMR_CASE_IGNORE_NAME;
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
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
  @Override
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
  @Override
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
  @Override
  public ByteString normalizeValue(ByteSequence value)
         throws DirectoryException
  {
    int           valueLength = value.length();

    // Find the first non-space character.
    int startPos = 0;
    while ((startPos < valueLength) && (value.byteAt(startPos) == ' '))
    {
      startPos++;
    }

    if (startPos == valueLength)
    {
      // This should only happen if the value is composed entirely of spaces.
      // In that case, the normalized value is a single space.
      return ServerConstants.SINGLE_SPACE_VALUE;
    }


    // Find the last non-space character;
    int endPos = (valueLength-1);
    while ((endPos > startPos) && (value.byteAt(endPos) == ' '))
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
      byte b = value.byteAt(i);
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


    return ByteString.valueOf(buffer.toString());
  }



  /**
   * Normalizes a value that contains a non-ASCII string.
   *
   * @param  value  The non-ASCII value to normalize.
   *
   * @return  The normalized form of the provided value.
   */
  private ByteString normalizeNonASCII(ByteSequence value)
  {
    StringBuilder buffer = new StringBuilder();
    toLowerCase(value.toString(), buffer);

    int bufferLength = buffer.length();
    if (bufferLength == 0)
    {
      if (value.length() > 0)
      {
        // This should only happen if the value is composed entirely of spaces.
        // In that case, the normalized value is a single space.
        return ServerConstants.SINGLE_SPACE_VALUE;
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

