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
 *      Portions copyright 2014-2015 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

import static com.forgerock.opendj.util.StringPrepProfile.CASE_FOLD;
import static com.forgerock.opendj.util.StringPrepProfile.TRIM;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;

import java.util.Collection;
import java.util.Collections;

import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.spi.IndexQueryFactory;
import org.forgerock.opendj.ldap.spi.Indexer;
import org.forgerock.opendj.ldap.spi.IndexingOptions;

/**
 * This class implements the keywordMatch matching rule defined in X.520. That
 * document defines "keyword" as implementation-specific, but in this case we
 * will consider it a match if the assertion value is contained within the
 * attribute value and is bounded by the start or the end of the attribute value
 * or any of the following characters: <BR>
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
final class KeywordEqualityMatchingRuleImpl extends AbstractEqualityMatchingRuleImpl {

    KeywordEqualityMatchingRuleImpl() {
        super(EMR_KEYWORD_NAME);
    }

    @Override
    public Assertion getAssertion(final Schema schema, final ByteSequence assertionValue)
            throws DecodeException {
        final String normalStr = normalize(assertionValue);

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
                    if (!isAcceptable(c)) {
                        return ConditionResult.FALSE;
                    }
                }

                if (valueStr1.length() > pos + normalStr.length()) {
                    final char c = valueStr1.charAt(pos + normalStr.length());
                    if (!isAcceptable(c)) {
                        return ConditionResult.FALSE;
                    }
                }

                // If we've gotten here, then we can assume it is a match.
                return ConditionResult.TRUE;
            }

            private boolean isAcceptable(final char c) {
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
                    return true;

                default:
                    return false;
                }
            }

            @Override
            public <T> T createIndexQuery(IndexQueryFactory<T> factory) throws DecodeException {
                return factory.createMatchAllQuery();
            }
        };
    }

    /** {@inheritDoc} */
    @Override
    public Collection<? extends Indexer> createIndexers(IndexingOptions options) {
        return Collections.emptySet();
    }

    public ByteString normalizeAttributeValue(final Schema schema, final ByteSequence value) {
        return ByteString.valueOfUtf8(normalize(value));
    }

    private String normalize(final ByteSequence value) {
        return SchemaUtils.normalizeStringAttributeValue(value, TRIM, CASE_FOLD).toString();
    }
}
