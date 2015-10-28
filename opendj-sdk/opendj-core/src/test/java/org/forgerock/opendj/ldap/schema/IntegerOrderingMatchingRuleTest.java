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
 *      Portions copyright 2014-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.opendj.ldap.schema.IntegerOrderingMatchingRuleImpl.SIGN_MASK_NEGATIVE;
import static org.forgerock.opendj.ldap.schema.IntegerOrderingMatchingRuleImpl.SIGN_MASK_POSITIVE;
import static org.forgerock.opendj.ldap.schema.IntegerOrderingMatchingRuleImpl.encodeHeader;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.OMR_INTEGER_OID;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test the IntegerOrderingMatchingRule.
 */
@SuppressWarnings("javadoc")
public class IntegerOrderingMatchingRuleTest extends OrderingMatchingRuleTest {

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "OrderingMatchingRuleInvalidValues")
    public Object[][] createOrderingMatchingRuleInvalidValues() {
        /*
         * The JDK8 BigInteger parser is quite tolerant and allows leading zeros
         * and + characters. It's ok if the matching rule is more tolerant than
         * the syntax itself (see commented data).
         */
        return new Object[][] {
            //{"01"},
            //{"00"},
            //{"-01"},
            { "1-2" },
            { "b2" },
            { "-" },
            { "" },
            {" 63 "},
            {"- 63"},
            //{"+63" },
            {"AB"  },
            {"0xAB"},
        };
    }

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "Orderingmatchingrules")
    public Object[][] createOrderingMatchingRuleTestData() {
        return new Object[][] {
            {"1",   "0",   1},
            {"1",   "1",   0},
            {"45",  "54", -1},
            {"-63", "63", -1},
            {"-63", "0",  -1},
            {"63",  "0",   1},
            {"0",   "-63", 1},
            // Values which are greater than 64 bits.
            { "-987654321987654321987654321", "-987654321987654321987654322", 1 },
            {"987654321987654321987654321", "987654321987654321987654322", -1},
            { "987654321987654321987654321", "987654321987654321987654321", 0 },
            // Values which have very different encoded lengths.
            { "-987654321987654321987654321", "-1", -1 },
            { "-987654321987654321987654321", "1", -1 },
            { "987654321987654321987654321", "-1", 1 },
            { "987654321987654321987654321", "1", 1 },
            { "-1", "-987654321987654321987654321", 1 },
            { "1", "-987654321987654321987654321", 1 },
            {"-1", "987654321987654321987654322", -1},
            {"1", "987654321987654321987654322", -1},
        };
    }

    /** {@inheritDoc} */
    @Override
    protected MatchingRule getRule() {
        return Schema.getCoreSchema().getMatchingRule(OMR_INTEGER_OID);
    }

    private enum Sign {
        POSITIVE(SIGN_MASK_POSITIVE), NEGATIVE(SIGN_MASK_NEGATIVE);
        private final byte mask;
        private Sign(byte mask) {
            this.mask = mask;
        }
    }

    private int length(int i) {
        return i;
    }

    private String expected(int... bytes) {
        ByteStringBuilder builder = new ByteStringBuilder();
        for (int b : bytes) {
            builder.appendByte(b);
        }
        return builder.toByteString().toHexString();
    }

    @DataProvider
    private Object[][] headerEncoding() {
        return new Object[][] {
            // @formatter:off
            { length(1 << 0),      Sign.POSITIVE, expected(0x81) },
            { length(1 << 4) - 1,  Sign.POSITIVE, expected(0x8f) },
            { length(1 << 4),      Sign.POSITIVE, expected(0x90, 0x10) },
            { length(1 << 12) - 1, Sign.POSITIVE, expected(0x9f, 0xff) },
            { length(1 << 12),     Sign.POSITIVE, expected(0xa0, 0x10, 0x00) },
            { length(1 << 20) - 1, Sign.POSITIVE, expected(0xaf, 0xff, 0xff) },
            { length(1 << 20),     Sign.POSITIVE, expected(0xb0, 0x10, 0x00, 0x00) },
            { length(1 << 28) - 1, Sign.POSITIVE, expected(0xbf, 0xff, 0xff, 0xff) },
            { length(1 << 28),     Sign.POSITIVE, expected(0xc1, 0x00, 0x00, 0x00, 0x00) },
            { length(1 << 31) - 1, Sign.POSITIVE, expected(0xc7, 0xff, 0xff, 0xff, 0xf0) },

            { length(1 << 0),      Sign.NEGATIVE, expected(0x7e) },
            { length(1 << 4) - 1,  Sign.NEGATIVE, expected(0x70) },
            { length(1 << 4),      Sign.NEGATIVE, expected(0x6f, 0xef) },
            { length(1 << 12) - 1, Sign.NEGATIVE, expected(0x60, 0x00) },
            { length(1 << 12),     Sign.NEGATIVE, expected(0x5f, 0xef, 0xff) },
            { length(1 << 20) - 1, Sign.NEGATIVE, expected(0x50, 0x00, 0x00) },
            { length(1 << 20),     Sign.NEGATIVE, expected(0x4f, 0xef, 0xff, 0xff) },
            { length(1 << 28) - 1, Sign.NEGATIVE, expected(0x40, 0x00, 0x00, 0x00) },
            { length(1 << 28),     Sign.NEGATIVE, expected(0x3e, 0xff, 0xff, 0xff, 0xff) },
            { length(1 << 31) - 1, Sign.NEGATIVE, expected(0x38, 0x00, 0x00, 0x00, 0x0f) },
            // @formatter:on
        };
    }

    @Test(dataProvider = "headerEncoding")
    public void testHeaderEncoding(int length, Sign sign, String expectedHexString) {
        ByteStringBuilder builder = new ByteStringBuilder();
        encodeHeader(builder, length, sign.mask);
        ByteString actual = builder.toByteString();
        assertThat(actual.toHexString()).isEqualTo(expectedHexString);
    }
}
