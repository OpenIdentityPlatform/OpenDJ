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
import static org.opends.sdk.schema.SchemaConstants.*;

import org.opends.sdk.ByteSequence;
import org.opends.sdk.LocalizableMessageBuilder;




/**
 * This class defines the integer attribute syntax, which holds an
 * arbitrarily-long integer value. Equality, ordering, and substring
 * matching will be allowed by default.
 */
final class IntegerSyntaxImpl extends AbstractSyntaxImpl
{
  @Override
  public String getEqualityMatchingRule()
  {
    return EMR_INTEGER_OID;
  }



  public String getName()
  {
    return SYNTAX_INTEGER_NAME;
  }



  @Override
  public String getOrderingMatchingRule()
  {
    return OMR_INTEGER_OID;
  }



  @Override
  public String getSubstringMatchingRule()
  {
    return SMR_CASE_EXACT_OID;
  }



  public boolean isHumanReadable()
  {
    return true;
  }



  /**
   * Indicates whether the provided value is acceptable for use in an
   * attribute with this syntax. If it is not, then the reason may be
   * appended to the provided buffer.
   * 
   * @param schema
   *          The schema in which this syntax is defined.
   * @param value
   *          The value for which to make the determination.
   * @param invalidReason
   *          The buffer to which the invalid reason should be appended.
   * @return <CODE>true</CODE> if the provided value is acceptable for
   *         use with this syntax, or <CODE>false</CODE> if not.
   */
  public boolean valueIsAcceptable(Schema schema, ByteSequence value,
      LocalizableMessageBuilder invalidReason)
  {
    final String valueString = value.toString();
    final int length = valueString.length();

    if (length == 0)
    {
      invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_EMPTY_VALUE
          .get(valueString));
      return false;
    }
    else if (length == 1)
    {
      switch (valueString.charAt(0))
      {
      case '0':
      case '1':
      case '2':
      case '3':
      case '4':
      case '5':
      case '6':
      case '7':
      case '8':
      case '9':
        return true;
      case '-':
        invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_DASH_NEEDS_VALUE
            .get(valueString));
        return false;
      default:
        invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_INVALID_CHARACTER
            .get(valueString, valueString.charAt(0), 0));
        return false;
      }
    }
    else
    {
      boolean negative = false;

      switch (valueString.charAt(0))
      {
      case '0':
        invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_INITIAL_ZERO
            .get(valueString));
        return false;
      case '1':
      case '2':
      case '3':
      case '4':
      case '5':
      case '6':
      case '7':
      case '8':
      case '9':
        // These are all fine.
        break;
      case '-':
        // This is fine too.
        negative = true;
        break;
      default:
        invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_INVALID_CHARACTER
            .get(valueString, valueString.charAt(0), 0));
        return false;
      }

      switch (valueString.charAt(1))
      {
      case '0':
        // This is fine as long as the value isn't negative.
        if (negative)
        {
          invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_INITIAL_ZERO
              .get(valueString));
          return false;
        }
        break;
      case '1':
      case '2':
      case '3':
      case '4':
      case '5':
      case '6':
      case '7':
      case '8':
      case '9':
        // These are all fine.
        break;
      default:
        invalidReason.append(WARN_ATTR_SYNTAX_INTEGER_INVALID_CHARACTER
            .get(valueString, valueString.charAt(0), 0));
        return false;
      }

      for (int i = 2; i < length; i++)
      {
        switch (valueString.charAt(i))
        {
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
          // These are all fine.
          break;
        default:
          invalidReason
              .append(WARN_ATTR_SYNTAX_INTEGER_INVALID_CHARACTER.get(
                  valueString, valueString.charAt(0), 0));
          return false;
        }
      }

      return true;
    }
  }
}
