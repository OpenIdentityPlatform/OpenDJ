/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.forgerock.opendj.ldap.schema;

import static com.forgerock.opendj.util.StringPrepProfile.NO_CASE_FOLD;
import static com.forgerock.opendj.util.StringPrepProfile.TRIM;
import static com.forgerock.opendj.util.StringPrepProfile.prepareUnicode;
import static org.forgerock.opendj.ldap.CoreMessages.WARN_ATTR_SYNTAX_IA5_ILLEGAL_CHARACTER;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;

/**
 * This class implements the caseExactIA5SubstringsMatch matching rule. This
 * matching rule actually isn't defined in any official specification, but some
 * directory vendors do provide an implementation using an OID from their own
 * private namespace.
 */
final class CaseExactIA5SubstringMatchingRuleImpl extends AbstractSubstringMatchingRuleImpl {
    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value)
            throws DecodeException {
        return normalize(TRIM, value);
    }

    @Override
    ByteString normalizeSubString(final Schema schema, final ByteSequence value)
            throws DecodeException {
        return normalize(false, value);
    }

    private ByteString normalize(final boolean trim, final ByteSequence value)
            throws DecodeException {
        final StringBuilder buffer = new StringBuilder();
        prepareUnicode(buffer, value, trim, NO_CASE_FOLD);

        final int bufferLength = buffer.length();
        if (bufferLength == 0) {
            if (value.length() > 0) {
                // This should only happen if the value is composed entirely of
                // spaces. In that case, the normalized value is a single space.
                return SchemaConstants.SINGLE_SPACE_VALUE;
            } else {
                // The value is empty, so it is already normalized.
                return ByteString.empty();
            }
        }

        // Replace any consecutive spaces with a single space and watch out
        // for non-ASCII characters.
        for (int pos = bufferLength - 1; pos > 0; pos--) {
            final char c = buffer.charAt(pos);
            if (c == ' ') {
                if (buffer.charAt(pos - 1) == ' ') {
                    buffer.delete(pos, pos + 1);
                }
            } else if ((c & 0x7F) != c) {
                // This is not a valid character for an IA5 string. If strict
                // syntax enforcement is enabled, then we'll throw an exception.
                // Otherwise, we'll get rid of the character.
                final LocalizableMessage message =
                        WARN_ATTR_SYNTAX_IA5_ILLEGAL_CHARACTER.get(value.toString(), String
                                .valueOf(c));
                throw DecodeException.error(message);
            }
        }

        return ByteString.valueOf(buffer.toString());
    }
}
