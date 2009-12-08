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
import static org.opends.sdk.schema.SchemaConstants.*;

import org.opends.sdk.ByteSequence;
import org.opends.sdk.LocalizableMessageBuilder;




/**
 * This class defines the country string attribute syntax, which should
 * be a two-character ISO 3166 country code. However, for
 * maintainability, it will accept any value consisting entirely of two
 * printable characters. In most ways, it will behave like the directory
 * string attribute syntax.
 */
final class CountryStringSyntaxImpl extends AbstractSyntaxImpl
{

  @Override
  public String getApproximateMatchingRule()
  {
    return AMR_DOUBLE_METAPHONE_OID;
  }



  @Override
  public String getEqualityMatchingRule()
  {
    return EMR_CASE_IGNORE_OID;
  }



  public String getName()
  {
    return SYNTAX_COUNTRY_STRING_NAME;
  }



  @Override
  public String getOrderingMatchingRule()
  {
    return OMR_CASE_IGNORE_OID;
  }



  @Override
  public String getSubstringMatchingRule()
  {
    return SMR_CASE_IGNORE_OID;
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
    final String stringValue = toLowerCase(value.toString());
    if (stringValue.length() != 2)
    {
      invalidReason
          .append(ERR_ATTR_SYNTAX_COUNTRY_STRING_INVALID_LENGTH
              .get(stringValue));
      return false;
    }

    if (!PrintableStringSyntaxImpl.isPrintableCharacter(stringValue
        .charAt(0))
        || !PrintableStringSyntaxImpl.isPrintableCharacter(stringValue
            .charAt(1)))
    {
      invalidReason.append(ERR_ATTR_SYNTAX_COUNTRY_STRING_NOT_PRINTABLE
          .get(stringValue));
      return false;
    }

    return true;
  }
}
