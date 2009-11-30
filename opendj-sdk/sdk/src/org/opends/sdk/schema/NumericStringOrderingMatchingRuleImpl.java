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



import static org.opends.sdk.util.StringPrepProfile.NO_CASE_FOLD;
import static org.opends.sdk.util.StringPrepProfile.TRIM;
import static org.opends.sdk.util.StringPrepProfile.prepareUnicode;

import org.opends.sdk.util.ByteSequence;
import org.opends.sdk.util.ByteString;



/**
 * This implements defines the numericStringOrderingMatch matching rule
 * defined in X.520 and referenced in RFC 2252.
 */
final class NumericStringOrderingMatchingRuleImpl extends
    AbstractOrderingMatchingRuleImpl
{
  public ByteString normalizeAttributeValue(Schema schema,
      ByteSequence value)
  {
    final StringBuilder buffer = new StringBuilder();
    prepareUnicode(buffer, value, TRIM, NO_CASE_FOLD);

    if (buffer.length() == 0)
    {
      return ByteString.empty();
    }
    return ByteString.valueOf(buffer.toString());
  }
}
