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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2013-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.schema;

import static org.fest.assertions.Assertions.*;
import static org.testng.Assert.assertEquals;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test the DistinguishedNameEqualityMatchingRule.
 */
@SuppressWarnings("javadoc")
public class DistinguishedNameEqualityMatchingRuleTest extends MatchingRuleTest {
    @DataProvider(name = "matchingRuleInvalidAttributeValues")
    public Object[][] createMatchingRuleInvalidAttributeValues() {
        return new Object[][] { { "manager" }, { "manager " },
            { "=Jim" },
            { " =Jim" },
            { "= Jim" },
            { " = Jim" },
            { "cn+Jim" },
            { "cn + Jim" },
            { "cn=Jim+" },
            { "cn=Jim+manager" },
            { "cn=Jim+manager " },
            { "cn=Jim+manager," }, // { "cn=Jim," }, { "cn=Jim,  " }, {
                                   // "c[n]=Jim" },
            { "_cn=Jim" }, { "c_n=Jim" }, { "cn\"=Jim" }, { "c\"n=Jim" }, { "1cn=Jim" },
            { "cn+uid=Jim" }, { "-cn=Jim" }, { "/tmp=a" }, { "\\tmp=a" }, { "cn;lang-en=Jim" },
            { "@cn=Jim" }, { "_name_=Jim" },
            { "\u03c0=pi" },
            { "v1.0=buggy" }, // { "1.=buggy" }, { ".1=buggy" },
            { "oid.1." }, { "1.3.6.1.4.1.1466..0=#04024869" }, { "cn=#a" }, { "cn=#ag" },
            { "cn=#ga" }, { "cn=#abcdefgh" },
            { "cn=a\\b" }, // { "cn=a\\bg" }, { "cn=\"hello" },
            { "cn=+mail=,dc=example,dc=com" }, { "cn=xyz+sn=,dc=example,dc=com" },
            { "cn=,dc=example,dc=com" } };
    }

    @DataProvider(name = "matchingrules")
    public Object[][] createMatchingRuleTest() {
        return new Object[][] {
            { "", "", ConditionResult.TRUE },
            { "   ", "", ConditionResult.TRUE },
            { "cn=", "cn=", ConditionResult.TRUE },
            { "cn= ", "cn=", ConditionResult.TRUE },
            { "cn =", "cn=", ConditionResult.TRUE },
            { "cn = ", "cn=", ConditionResult.TRUE },
            { "dc=com", "dc=com", ConditionResult.TRUE },
            { "dc=com+o=com", "dc=com+o=com", ConditionResult.TRUE },
            { "DC=COM", "dc=com", ConditionResult.TRUE },
            { "dc = com", "dc=com", ConditionResult.TRUE },
            { " dc = com ", "dc=com", ConditionResult.TRUE },
            { "dc=example,dc=com", "dc=example,dc=com", ConditionResult.TRUE },
            { "dc=example, dc=com", "dc=example,dc=com", ConditionResult.TRUE },
            { "dc=example ,dc=com", "dc=example,dc=com", ConditionResult.TRUE },
            { "dc =example , dc  =   com", "dc=example,dc=com", ConditionResult.TRUE },
            { "givenName=John+cn=Doe,ou=People,dc=example,dc=com",
                "cn=doe+givenname=john,ou=people,dc=example,dc=com", ConditionResult.TRUE },
            { "givenName=John\\+cn=Doe,ou=People,dc=example,dc=com",
                "givenname=john\\+cn\\=doe,ou=people,dc=example,dc=com", ConditionResult.TRUE },
            { "cn=Doe\\, John,ou=People,dc=example,dc=com",
                "cn=doe\\, john,ou=people,dc=example,dc=com", ConditionResult.TRUE },
            { "UID=jsmith,DC=example,DC=net", "uid=jsmith,dc=example,dc=net", ConditionResult.TRUE },
            { "OU=Sales+CN=J. Smith,DC=example,DC=net", "cn=j. smith+ou=sales,dc=example,dc=net",
                ConditionResult.TRUE },
            { "CN=James \\\"Jim\\\" Smith\\, III,DC=example,DC=net",
                "cn=james \\\"jim\\\" smith\\, iii,dc=example,dc=net", ConditionResult.TRUE },
            { "CN=John Smith\\2C III,DC=example,DC=net", "cn=john smith\\, iii,dc=example,dc=net",
                ConditionResult.TRUE },
            { "CN=\\23John Smith\\20,DC=example,DC=net", "cn=\\#john smith,dc=example,dc=net",
                ConditionResult.TRUE },
            { "CN=Before\\0dAfter,DC=example,DC=net",
                // \0d is a hex representation of Carriage return. It is mapped
                // to a SPACE as defined in the MAP ( RFC 4518)
                "cn=before after,dc=example,dc=net", ConditionResult.TRUE },
            { "2.5.4.3=#04024869",
                // Unicode codepoints from 0000-0008 are mapped to nothing.
                "cn=hi", ConditionResult.TRUE },
            { "1.1.1=", "1.1.1=", ConditionResult.TRUE },
            { "CN=Lu\\C4\\8Di\\C4\\87", "cn=lu\u010di\u0107", ConditionResult.TRUE },
            { "ou=\\e5\\96\\b6\\e6\\a5\\ad\\e9\\83\\a8,o=Airius", "ou=\u55b6\u696d\u90e8,o=airius",
                ConditionResult.TRUE },
            { "cn=\\ john \\ ,dc=com", "cn=\\ john \\ ,dc=com", ConditionResult.TRUE },
            { "caseexact=UPPER,dc=COM", "caseexact=UPPER,dc=com", ConditionResult.TRUE },
            { "caseexact=upper,dc=COM", "caseexact=UPPER,dc=com", ConditionResult.FALSE },
            { "caseexact=UPPER,dc=COM", "caseexact=upper,dc=com", ConditionResult.FALSE },
            { "caseexact=lower,dc=COM", "caseexact=lower,dc=com", ConditionResult.TRUE },
            { "AB-global=", "ab-global=", ConditionResult.TRUE },
            { "OU= Sales + CN = J. Smith ,DC=example,DC=net",
                "cn=j. smith+ou=sales,dc=example,dc=net", ConditionResult.TRUE },
            { "cn=John+a=Doe", "a=Doe+cn=john", ConditionResult.TRUE },
            { "O=\"Sue, Grabbit and Runn\",C=US", "o=sue\\, grabbit and runn,c=us",
                ConditionResult.TRUE }, };
    }

    /**
     * DN test data provider.
     *
     * @return The array of test DN strings.
     */
    @DataProvider(name = "testDNs")
    public Object[][] createData() {
        return new Object[][] {
            { "", "" },
            { "   ", "" },
            { "cn=", "cn=" },
            { "cn= ", "cn=" },
            { "cn =", "cn=" },
            { "cn = ", "cn=" },
            { "dc=com", "dc=com" },
            { "dc=com+o=com", "dc=com\u0001o=com" },
            { "DC=COM", "dc=com" },
            { "dc = com", "dc=com" },
            { " dc = com ", "dc=com" },
            { "dc=example,dc=com", "dc=com\u0000dc=example" },
            { "dc=example, dc=com", "dc=com\u0000dc=example" },
            { "dc=example ,dc=com", "dc=com\u0000dc=example" },
            { "dc =example , dc  =   com", "dc=com\u0000dc=example" },
            { "givenName=John+cn=Doe,ou=People,dc=example,dc=com",
                "dc=com\u0000dc=example\u0000ou=people\u0000cn=doe\u0001givenname=john" },
            { "givenName=John\\+cn=Doe,ou=People,dc=example,dc=com",
                "dc=com\u0000dc=example\u0000ou=people\u0000givenname=john\u002Bcn\u003Ddoe" },
            { "cn=Doe\\, John,ou=People,dc=example,dc=com",
                "dc=com\u0000dc=example\u0000ou=people\u0000cn=doe, john" },
            { "UID=jsmith,DC=example,DC=net", "dc=net\u0000dc=example\u0000uid=jsmith" },
            { "OU=Sales+CN=J. Smith,DC=example,DC=net",
                "dc=net\u0000dc=example\u0000cn=j. smith\u0001ou=sales" },
            // commented due to checkstyle bug : https://github.com/checkstyle/checkstyle/issues/157
            // uncomment when it is fixed
            // { "CN=James \\\"Jim\\\" Smith\\, III,DC=example,DC=net",
            //    "dc=net\u0000dc=example\u0000cn=james " + "\u005C\u0022jim\u005C\u0022" + " smith\u002C iii" },
            { "CN=John Smith\\2C III,DC=example,DC=net",
                "dc=net\u0000dc=example\u0000cn=john smith\u002C iii" },
            { "CN=\\23John Smith\\20,DC=example,DC=net",
                "dc=net\u0000dc=example\u0000cn=\u0023john smith" },
            { "CN=Before\\0dAfter,DC=example,DC=net",
                // \0d is a hex representation of Carriage return. It is mapped
                // to a SPACE as defined in the MAP ( RFC 4518)
                "dc=net\u0000dc=example\u0000cn=before after" },
            { "2.5.4.3=#04024869",
                // Unicode codepoints from 0000-0008 are mapped to nothing.
                "cn=hi" },
            { "1.1.1=", "1.1.1=" },
            { "CN=Lu\\C4\\8Di\\C4\\87", "cn=luc\u030cic\u0301" },
            { "ou=\\e5\\96\\b6\\e6\\a5\\ad\\e9\\83\\a8,o=Airius",
                "o=airius\u0000ou=\u55b6\u696d\u90e8" },
            { "cn=\\ john \\ ,dc=com", "dc=com\u0000cn=john" },
            { "caseexact=UPPER,dc=COM", "dc=com\u0000caseexact=UPPER" },
            { "caseexact=mIxEd,dc=COM", "dc=com\u0000caseexact=mIxEd" },
            { "AB-global=", "ab-global=" },
            { "OU= Sales + CN = J. Smith ,DC=example,DC=net",
                "dc=net\u0000dc=example\u0000cn=j. smith\u0001ou=sales" },
            { "cn=John+a=", "a=\u0001cn=john" },
            { "O=\"Sue, Grabbit and Runn\",C=US", "c=us\u0000o=sue\u002C grabbit and runn" }, };
    }

    protected MatchingRule getRule() {
        return CoreSchema.getDistinguishedNameMatchingRule();
    }

    @Test
    public void testIsValidated() {
        assertThat(getRule().isValidated()).isTrue();
    }

    /**
     * Test the normalized values.
     */
    @Test(dataProvider = "testDNs")
    public void testNormalization(final String value1, final String value2) throws Exception {
        final MatchingRule rule = getRule();
        final ByteString normalizedValue1 =
                rule.normalizeAttributeValue(ByteString.valueOfUtf8(value1));
        final ByteString expectedValue = ByteString.valueOfUtf8(value2);
        assertEquals(normalizedValue1, expectedValue);
    }

}
