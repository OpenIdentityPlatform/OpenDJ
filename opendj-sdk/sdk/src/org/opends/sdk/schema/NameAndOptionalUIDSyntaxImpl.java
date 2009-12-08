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
import org.opends.sdk.DN;
import org.opends.sdk.LocalizedIllegalArgumentException;
import org.opends.sdk.LocalizableMessageBuilder;




/**
 * This class implements the name and optional UID attribute syntax,
 * which holds values consisting of a DN, optionally followed by an
 * octothorpe (#) and a bit string value.
 */
final class NameAndOptionalUIDSyntaxImpl extends AbstractSyntaxImpl
{

  @Override
  public String getEqualityMatchingRule()
  {
    return EMR_UNIQUE_MEMBER_OID;
  }



  public String getName()
  {
    return SYNTAX_NAME_AND_OPTIONAL_UID_NAME;
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
    final String valueString = value.toString().trim();
    final int valueLength = valueString.length();

    // See if the value contains the "optional uid" portion. If we think
    // it does, then mark its location.
    int dnEndPos = valueLength;
    int sharpPos = -1;
    if (valueString.endsWith("'B") || valueString.endsWith("'b"))
    {
      sharpPos = valueString.lastIndexOf("#'");
      if (sharpPos > 0)
      {
        dnEndPos = sharpPos;
      }
    }

    // Take the DN portion of the string and try to normalize it.
    try
    {
      DN.valueOf(valueString.substring(0, dnEndPos), schema);
    }
    catch (final LocalizedIllegalArgumentException e)
    {
      // We couldn't normalize the DN for some reason. The value cannot
      // be acceptable.
      invalidReason.append(ERR_ATTR_SYNTAX_NAMEANDUID_INVALID_DN.get(
          valueString, e.getMessageObject()));
      return false;
    }

    // If there is an "optional uid", then normalize it and make sure it
    // only contains valid binary digits.
    if (sharpPos > 0)
    {
      final int endPos = valueLength - 2;
      for (int i = sharpPos + 2; i < endPos; i++)
      {
        final char c = valueString.charAt(i);
        if (!(c == '0' || c == '1'))
        {

          invalidReason
              .append(ERR_ATTR_SYNTAX_NAMEANDUID_ILLEGAL_BINARY_DIGIT
                  .get(valueString, String.valueOf(c), i));
          return false;
        }
      }
    }

    // If we've gotten here, then the value is acceptable.
    return true;
  }
}
