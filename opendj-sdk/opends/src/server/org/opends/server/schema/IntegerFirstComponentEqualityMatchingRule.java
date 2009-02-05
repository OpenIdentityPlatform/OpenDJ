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



import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.Collection;
import java.util.Collections;

import org.opends.messages.Message;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.ByteSequence;
import org.opends.server.types.ByteString;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;
import org.opends.server.util.ServerConstants;



/**
 * This class implements the integerFirstComponentMatch matching rule defined in
 * X.520 and referenced in RFC 2252.  This rule is intended for use with
 * attributes whose values contain a set of parentheses enclosing a
 * space-delimited set of names and/or name-value pairs (like attribute type or
 * objectclass descriptions) in which the "first component" is the first item
 * after the opening parenthesis.
 */
class IntegerFirstComponentEqualityMatchingRule
       extends EqualityMatchingRule
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();




  /**
   * Creates a new instance of this integerFirstComponentMatch matching rule.
   */
  public IntegerFirstComponentEqualityMatchingRule()
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
    return EMR_INTEGER_FIRST_COMPONENT_NAME;
  }



  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
  public String getOID()
  {
    return EMR_INTEGER_FIRST_COMPONENT_OID;
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
    return SYNTAX_INTEGER_OID;
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
    try
    {
      int intValue1 = extractIntValue(value1.toString());
      int intValue2 = extractIntValue(value2.toString());

      return (intValue1 == intValue2);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      return false;
    }
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
   * @return  The hash code generated for the provided attribute value.
   */
  @Override
  public int generateHashCode(ByteSequence attributeValue)
  {
    // In this case, we'll always return the same value because the matching
    // isn't based on the entire value.
    return 1;
  }



  /**
   * Extracts the integer portion from the provided value string.
   *
   * @param  valueString  The value string from which to extract the integer
   *                      portion.
   *
   * @return  The extracted integer portion from the value string,
   *
   * @throws  DirectoryException  If a problem occurs while trying to extract
   *                              the integer value.
   */
  private static int extractIntValue(String valueString)
          throws DirectoryException
  {
    int valueLength = valueString.length();

    if ((valueLength == 0) || (valueString.charAt(0) != '('))
    {
      // We'll check to see if the entire string is an integer.  If so, then
      // use that value.  If not, then fail.
      try
      {
        return Integer.parseInt(valueString);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_EMR_INTFIRSTCOMP_NO_INITIAL_PARENTHESIS.get(
            String.valueOf(valueString));
        throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                     message, e);
      }
    }

    int  pos = 1;
    while ((pos < valueLength) && ((valueString.charAt(pos)) == ' '))
    {
      pos++;
    }

    if (pos >= valueLength)
    {
      Message message =
          ERR_EMR_INTFIRSTCOMP_NO_NONSPACE.get(String.valueOf(valueString));
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                   message);
    }


    // The current position must be the start position for the value.  Keep
    // reading until we find the next space.
    int startPos = pos++;
    while ((pos < valueLength) && ((valueString.charAt(pos)) != ' '))
    {
      pos++;
    }

    if (pos >= valueLength)
    {
      Message message = ERR_EMR_INTFIRSTCOMP_NO_SPACE_AFTER_INT.get(
          String.valueOf(valueString));
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                   message);
    }


    // We should now have the position of the integer value.  Make sure it's an
    // integer and return it.
    try
    {
      return Integer.parseInt(valueString.substring(startPos, pos));
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_EMR_INTFIRSTCOMP_FIRST_COMPONENT_NOT_INT.get(
          String.valueOf(valueString));
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                                   message);
    }
  }
}

