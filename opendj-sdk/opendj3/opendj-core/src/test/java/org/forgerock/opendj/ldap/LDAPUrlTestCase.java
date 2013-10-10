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
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.ldap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * This class defines a set of tests for the org.forgerock.opendj.ldap.LDAPUrl
 * class.
 */
public class LDAPUrlTestCase extends SdkTestCase {
    /**
     * LDAPUrl encoding test data provider.
     *
     * @return The array of test encoding of LDAP URL strings.
     */
    @DataProvider(name = "ldapurls")
    public Object[][] createEncodingData() {
        return new Object[][] {
            { "ldap://", "ldap://", true },
            { "ldap:///", "ldap:///", true },
            { "ldap://ldap.example.net", "ldap://ldap.example.net", true },
            { "ldap://ldap.example.net/", "ldap://ldap.example.net/", true },
            { "ldap://ldap.example.net/?", "ldap://ldap.example.net/?", true },
            { "ldap:///o=University of Michigan,c=US", "ldap:///o=University%20of%20Michigan,c=US",
                true },
            { "ldap://ldap1.example.net/o=University of Michigan,c=US",
                "ldap://ldap1.example.net/o=University%20of%20Michigan,c=US", true },
            { "ldap://ldap1.example.net/o=University of Michigan,c=US?postalAddress",
                "ldap://ldap1.example.net/o=University%20of%20Michigan,c=US?postalAddress", true },
            {
                "ldap://ldap1.example.net:6666/o=University of Michigan,c=US??sub?(cn=Babs Jensen)",
                "ldap://ldap1.example.net:6666/o=University%20of%20Michigan,c=US??sub?(cn=Babs%20Jensen)",
                true },
            { "LDAP://ldap1.example.com/c=GB?objectClass?ONE",
                "LDAP://ldap1.example.com/c=GB?objectClass?ONE", true },
            // { "ldap://ldap2.example.com/o=Question?,c=US?mail",
            // "ldap://ldap2.example.com/o=Question%3f,c=US?mail",true },
            { "ldap://ldap3.example.com/o=Babsco,c=US???(four-octet=\00\00\00\04)",
                "ldap://ldap3.example.com/o=Babsco,c=US???(four-octet=%5c00%5c00%5c00%5c04)", true },
            { "ldap://ldap.example.com/o=An Example\\2C Inc.,c=US",
                "ldap://ldap.example.com/o=An%20Example%5C2C%20Inc.,c=US", true },
            { "ldap:///", "ldap:///", true }, { "ldap:///", "ldap:///", true },
            { "ldap:///", "ldap:///", true }, };
    }

    /**
     * LDAPUrl construction test data provider.
     *
     * @return The array of test construction of LDAPUrl objects.
     */
    @DataProvider(name = "urlobjects1")
    public Object[][] createURLObjects1() {
        return new Object[][] {
            { new LDAPUrl(false, null, null, null, null, null), "ldap:///???" },
            { new LDAPUrl(true, null, null, null, null, null), "ldaps:///???" },
            { new LDAPUrl(true, "void.central.sun.com", null, null, null, null),
                "ldaps://void.central.sun.com/???" },
            { new LDAPUrl(true, null, 1245, null, null, null), "ldaps://:1245/???" },
            { new LDAPUrl(true, "void.central", 123, null, null, null),
                "ldaps://void.central:123/???" },
            { new LDAPUrl(true, null, null, null, null, null, "cn", "sn"), "ldaps:///?cn,sn??" },
            {
                new LDAPUrl(true, null, null, null, null, Filter.equality("uid",
                        "abc"), "cn"), "ldaps:///?cn??(uid=abc)" },
            {
                new LDAPUrl(true, null, null, null, SearchScope.WHOLE_SUBTREE, Filter
                        .equality("uid", "abc"), "cn"), "ldaps:///?cn?sub?(uid=abc)" },
            {
                new LDAPUrl(true, null, null, DN.valueOf("uid=abc,o=target"),
                        SearchScope.WHOLE_SUBTREE, Filter.equality("uid", "abc"),
                        "cn"), "ldaps:///uid=abc,o=target?cn?sub?(uid=abc)" },
            {
                new LDAPUrl(true, "localhost", 1345, DN.valueOf("uid=abc,o=target"),
                        SearchScope.WHOLE_SUBTREE, Filter.equality("uid", "abc"),
                        "cn"), "ldaps://localhost:1345/uid=abc,o=target?cn?sub?(uid=abc)" }, };
    }

    /**
     * LDAPUrl construction test data provider.
     *
     * @return The array of test construction of LDAPUrl objects.
     */
    @DataProvider(name = "urlobjects2")
    public Object[][] createURLObjects2() {
        return new Object[][] {
            { new LDAPUrl(false, null, null, null, null, null), LDAPUrl.valueOf("ldap:///") },
            { new LDAPUrl(true, null, null, null, null, null), LDAPUrl.valueOf("ldaps:///") },
            { new LDAPUrl(true, "void.central.sun.com", null, null, null, null),
                LDAPUrl.valueOf("ldaps://void.central.sun.com") },
            { new LDAPUrl(true, null, 1245, null, null, null), LDAPUrl.valueOf("ldaps://:1245") },
            { new LDAPUrl(true, "void.central", 123, null, null, null),
                LDAPUrl.valueOf("ldaps://void.central:123") },
            { new LDAPUrl(true, null, null, null, null, null, "cn", "sn"),
                LDAPUrl.valueOf("ldaps:///?cn,sn??") },
            {
                new LDAPUrl(true, null, null, null, null, Filter.equality("uid",
                        "abc"), "cn"), LDAPUrl.valueOf("ldaps:///?cn??(uid=abc)") },
            {
                new LDAPUrl(true, null, null, null, SearchScope.WHOLE_SUBTREE, Filter
                        .equality("uid", "abc"), "cn"),
                LDAPUrl.valueOf("ldaps:///?cn?sub?(uid=abc)") },
            {
                new LDAPUrl(true, null, null, DN.valueOf("uid=abc,o=target"),
                        SearchScope.WHOLE_SUBTREE, Filter.equality("uid", "abc"),
                        "cn"), LDAPUrl.valueOf("ldaps:///uid=abc,o=target?cn?sub?(uid=abc)") },
            {
                new LDAPUrl(true, "localhost", 1345, DN.valueOf("uid=abc,o=target"),
                        SearchScope.WHOLE_SUBTREE, Filter.equality("uid", "abc"),
                        "cn"),
                LDAPUrl.valueOf("ldaps://localhost:1345/uid=abc,o=target?cn?sub?(uid=abc)") }, };
    }

    /**
     * Tests equals method of the LDAP URL.
     *
     * @param urlObj1
     *            The LDAPUrl object.
     * @param urlObj2
     *            The LDAPUrl object.
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(dataProvider = "urlobjects2")
    public void testLDAPURLCtor(final LDAPUrl urlObj1, final LDAPUrl urlObj2) throws Exception {
        assertTrue(urlObj1.equals(urlObj2));
    }

    /**
     * Test Whether the LDAP URL (non-encoded) is constructed properly from the
     * arguments.
     *
     * @param urlObj
     *            The LDAPUrl object.
     * @param urlString
     *            The non-encoded ldap url.
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(dataProvider = "urlobjects1")
    public void testLDAPURLCtor(final LDAPUrl urlObj, final String urlString) throws Exception {
        assertEquals(urlString, urlObj.toString());
    }

    /**
     * Test the LDAP URL encoding.
     *
     * @param toEncode
     *            The URL that needs encoding.
     * @param encoded
     *            The encoded URL.
     * @param valid
     *            if the encoding is valid.
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test(dataProvider = "ldapurls")
    public void testURLEncoding(final String toEncode, final String encoded, final boolean valid)
            throws Exception {
        final LDAPUrl url1 = LDAPUrl.valueOf(toEncode);
        final LDAPUrl url2 = LDAPUrl.valueOf(encoded);
        if (valid) {
            assertTrue(url1.equals(url2));
        }
    }
}
