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

import static com.forgerock.opendj.util.StringPrepProfile.CASE_FOLD;
import static com.forgerock.opendj.util.StringPrepProfile.TRIM;
import static com.forgerock.opendj.util.StringPrepProfile.prepareUnicode;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;

/**
 * This class implements the wordMatch matching rule defined in X.520. That
 * document defines "word" as implementation-specific, but in this case we will
 * consider it a match if the assertion value is contained within the attribute
 * value and is bounded by the edge of the value or any of the following
 * characters: <BR>
 * <UL>
 * <LI>A space</LI>
 * <LI>A period</LI>
 * <LI>A comma</LI>
 * <LI>A slash</LI>
 * <LI>A dollar sign</LI>
 * <LI>A plus sign</LI>
 * <LI>A dash</LI>
 * <LI>An underscore</LI>
 * <LI>An octothorpe</LI>
 * <LI>An equal sign</LI>
 * </UL>
 */
final class WordEqualityMatchingRuleImpl extends AbstractMatchingRuleImpl {
    @Override
    public Assertion getAssertion(final Schema schema, final ByteSequence value)
            throws DecodeException {
        final String normalStr = normalize(value);

        return new Assertion() {
            public ConditionResult matches(final ByteSequence attributeValue) {
                // See if the assertion value is contained in the attribute
                // value. If not, then it isn't a match.
                final String valueStr1 = attributeValue.toString();

                final int pos = valueStr1.indexOf(normalStr);
                if (pos < 0) {
                    return ConditionResult.FALSE;
                }

                if (pos > 0) {
                    final char c = valueStr1.charAt(pos - 1);
                    switch (c) {
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
                        return ConditionResult.FALSE;
                    }
                }

                if (valueStr1.length() > pos + normalStr.length()) {
                    final char c = valueStr1.charAt(pos + normalStr.length());
                    switch (c) {
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
                        return ConditionResult.FALSE;
                    }
                }

                // If we've gotten here, then we can assume it is a match.
                return ConditionResult.TRUE;
            }
        };
    }

    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value) {
        return ByteString.valueOf(normalize(value));
    }

    private String normalize(final ByteSequence value) {
        final StringBuilder buffer = new StringBuilder();
        prepareUnicode(buffer, value, TRIM, CASE_FOLD);

        final int bufferLength = buffer.length();
        if (bufferLength == 0) {
            if (value.length() > 0) {
                // This should only happen if the value is composed entirely of
                // spaces. In that case, the normalized value is a single space.
                return " ".intern();
            } else {
                // The value is empty, so it is already normalized.
                return "".intern();
            }
        }

        // Replace any consecutive spaces with a single space.
        for (int pos = bufferLength - 1; pos > 0; pos--) {
            if (buffer.charAt(pos) == ' ') {
                if (buffer.charAt(pos - 1) == ' ') {
                    buffer.delete(pos, pos + 1);
                }
            }
        }

        return buffer.toString();
    }
}
