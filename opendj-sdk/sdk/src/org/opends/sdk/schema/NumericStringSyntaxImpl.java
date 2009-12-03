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



import static com.sun.opends.sdk.util.Messages.ERR_ATTR_SYNTAX_NUMERIC_STRING_EMPTY_VALUE;
import static com.sun.opends.sdk.util.Messages.WARN_ATTR_SYNTAX_NUMERIC_STRING_ILLEGAL_CHAR;
import static org.opends.sdk.schema.SchemaConstants.EMR_NUMERIC_STRING_OID;
import static org.opends.sdk.schema.SchemaConstants.OMR_NUMERIC_STRING_OID;
import static org.opends.sdk.schema.SchemaConstants.SMR_CASE_EXACT_OID;
import static org.opends.sdk.schema.SchemaConstants.SYNTAX_NUMERIC_STRING_NAME;
import static org.opends.sdk.util.StaticUtils.isDigit;

import com.sun.opends.sdk.util.MessageBuilder;
import org.opends.sdk.util.ByteSequence;



/**
 * This class implements the numeric string attribute syntax, which may
 * be hold one or more numeric digits and/or spaces. Equality, ordering,
 * and substring matching will be allowed by default.
 */
final class NumericStringSyntaxImpl extends AbstractSyntaxImpl
{

  @Override
  public String getEqualityMatchingRule()
  {
    return EMR_NUMERIC_STRING_OID;
  }



  public String getName()
  {
    return SYNTAX_NUMERIC_STRING_NAME;
  }



  @Override
  public String getOrderingMatchingRule()
  {
    return OMR_NUMERIC_STRING_OID;
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
      MessageBuilder invalidReason)
  {
    final String valueString = value.toString();
    final int length = valueString.length();

    // It must have at least one digit or space.
    if (length == 0)
    {

      invalidReason.append(ERR_ATTR_SYNTAX_NUMERIC_STRING_EMPTY_VALUE
          .get());
      return false;
    }

    // Iterate through the characters and make sure they are all digits
    // or spaces.
    for (int i = 0; i < length; i++)
    {
      final char c = valueString.charAt(i);
      if (!(isDigit(c) || c == ' '))
      {

        invalidReason
            .append(WARN_ATTR_SYNTAX_NUMERIC_STRING_ILLEGAL_CHAR.get(
                valueString, String.valueOf(c), i));
        return false;
      }
    }

    return true;
  }
}
