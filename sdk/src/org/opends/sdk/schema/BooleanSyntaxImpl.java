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

import com.sun.opends.sdk.util.MessageBuilder;



/**
 * This class defines the Boolean attribute syntax, which only allows
 * values of "TRUE" or "FALSE" (although this implementation is more
 * flexible and will also allow "YES", "ON", or "1" instead of "TRUE",
 * or "NO", "OFF", or "0" instead of "FALSE"). Only equality matching is
 * allowed by default for this syntax.
 */
final class BooleanSyntaxImpl extends AbstractSyntaxImpl
{
  @Override
  public String getEqualityMatchingRule()
  {
    return EMR_BOOLEAN_OID;
  }



  public String getName()
  {
    return SYNTAX_BOOLEAN_NAME;
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
    final String valueString = value.toString().toUpperCase();

    final boolean returnValue =
        valueString.equals("TRUE") || valueString.equals("YES")
            || valueString.equals("ON") || valueString.equals("1")
            || valueString.equals("FALSE") || valueString.equals("NO")
            || valueString.equals("OFF") || valueString.equals("0");

    if (!returnValue)
    {
      invalidReason.append(WARN_ATTR_SYNTAX_ILLEGAL_BOOLEAN.get(value
          .toString()));
    }

    return returnValue;
  }
}
