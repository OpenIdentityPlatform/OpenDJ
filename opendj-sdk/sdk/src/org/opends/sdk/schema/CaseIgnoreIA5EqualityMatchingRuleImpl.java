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



import static org.opends.messages.SchemaMessages.WARN_ATTR_SYNTAX_IA5_ILLEGAL_CHARACTER;
import static org.opends.sdk.util.StringPrepProfile.CASE_FOLD;
import static org.opends.sdk.util.StringPrepProfile.TRIM;
import static org.opends.sdk.util.StringPrepProfile.prepareUnicode;

import org.opends.messages.Message;
import org.opends.sdk.DecodeException;
import org.opends.sdk.util.ByteSequence;
import org.opends.sdk.util.ByteString;



/**
 * This class implements the caseIgnoreIA5Match matching rule defined in
 * RFC 2252.
 */
final class CaseIgnoreIA5EqualityMatchingRuleImpl extends
    AbstractMatchingRuleImpl
{
  public ByteString normalizeAttributeValue(Schema schema,
      ByteSequence value) throws DecodeException
  {
    final StringBuilder buffer = new StringBuilder();
    prepareUnicode(buffer, value, TRIM, CASE_FOLD);

    final int bufferLength = buffer.length();
    if (bufferLength == 0)
    {
      if (value.length() > 0)
      {
        // This should only happen if the value is composed entirely of
        // spaces. In that case, the normalized value is a single space.
        return SchemaConstants.SINGLE_SPACE_VALUE;
      }
      else
      {
        // The value is empty, so it is already normalized.
        return ByteString.empty();
      }
    }

    // Replace any consecutive spaces with a single space and watch out
    // for non-ASCII characters.
    for (int pos = bufferLength - 1; pos > 0; pos--)
    {
      final char c = buffer.charAt(pos);
      if (c == ' ')
      {
        if (buffer.charAt(pos - 1) == ' ')
        {
          buffer.delete(pos, pos + 1);
        }
      }
      else if ((c & 0x7F) != c)
      {
        // This is not a valid character for an IA5 string. If strict
        // syntax enforcement is enabled, then we'll throw an exception.
        // Otherwise, we'll get rid of the character.
        final Message message =
            WARN_ATTR_SYNTAX_IA5_ILLEGAL_CHARACTER.get(
                value.toString(), String.valueOf(c));
        throw DecodeException.error(message);
      }
    }

    return ByteString.valueOf(buffer.toString());
  }
}
