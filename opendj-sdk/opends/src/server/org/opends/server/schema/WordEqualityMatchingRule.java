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
 * This class implements the wordMatch matching rule defined in X.520.  That
 * document defines "word" as implementation-specific, but in this case we will
 * consider it a match if the assertion value is contained within the attribute
 * value and is bounded by the edge of the value or any of the following
 * characters:
 * <BR>
 * <UL>
 *   <LI>A space</LI>
 *   <LI>A period</LI>
 *   <LI>A comma</LI>
 *   <LI>A slash</LI>
 *   <LI>A dollar sign</LI>
 *   <LI>A plus sign</LI>
 *   <LI>A dash</LI>
 *   <LI>An underscore</LI>
 *   <LI>An octothorpe</LI>
 *   <LI>An equal sign</LI>
 * </UL>
 */
class WordEqualityMatchingRule
       extends EqualityMatchingRule
{
  /**
   * Creates a new instance of this wordMatch matching rule.
   */
  public WordEqualityMatchingRule()
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
    return EMR_WORD_NAME;
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
  public String getOID()
  {
    return EMR_WORD_OID;
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
    StringBuilder buffer = new StringBuilder();
    toLowerCase(value, buffer, true);

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
  @Override
  public boolean areEqual(ByteSequence value1, ByteSequence value2)
  {
    // For this purpose, the first value will be considered the attribute value,
    // and the second the assertion value.  See if the second value is contained
    // in the first.  If not, then it isn't a match.
    String valueStr1 = value1.toString();
    String valueStr2 = value2.toString();
    int pos = valueStr1.indexOf(valueStr2);
    if (pos < 0)
    {
      return false;
    }


    if (pos > 0)
    {
      char c = valueStr1.charAt(pos-1);
      switch (c)
      {
        case ' ':
        case '.':
        case ',':
        case '/':
        case '$':
        case '+':
        case '-':
        case '_':
        case '#':
        case '=':
          // These are all acceptable.
          break;

        default:
          // Anything else is not.
          return false;
      }
    }


    if (valueStr1.length() > (pos + valueStr2.length()))
    {
      char c = valueStr1.charAt(pos + valueStr2.length());
      switch (c)
      {
        case ' ':
        case '.':
        case ',':
        case '/':
        case '$':
        case '+':
        case '-':
        case '_':
        case '#':
        case '=':
          // These are all acceptable.
          break;

        default:
          // Anything else is not.
          return false;
      }
    }


    // If we've gotten here, then we can assume it is a match.
    return true;
  }



  /**
   * Generates a hash code for the provided attribute value.  This version of
   * the method will simply create a hash code from the normalized form of the
   * attribute value.  For matching rules explicitly designed to work in cases
   * where byte-for-byte comparisons of normalized values is not sufficient for
   * determining equality (e.g., if the associated attribute syntax is based on
   * hashed or encrypted values), then this method must be overridden to provide
   * an appropriate implementation for that case.
   *
   * @param  attributeValue  The attribute value for which to generate the hash
   *                         code.
   *
   * @return  The hash code generated for the provided attribute value.*/
  @Override
  public int generateHashCode(ByteSequence attributeValue)
  {
    // In this case, we'll always return the same value because the matching
    // isn't based on the entire value.
    return 1;
  }
}

