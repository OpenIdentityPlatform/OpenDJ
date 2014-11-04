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
 *      Copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.fest.assertions.Assertions.*;

/**
 * This class defines a set of tests for the org.forgerock.opendj.ldap.DN.CompactDn class.
 */
@SuppressWarnings("javadoc")
public class CompactDnTestCase extends SdkTestCase {

    /**
     * DN test data provider.
     *
     * @return The array of test DN strings.
     */
    @DataProvider
    public Object[][] equivalentDnRepresentations() {
        return new Object[][] {
            { "" , "" },
            { "   " , "" },
            { "cn=" , "cn=" },
            { "cn= " , "cn=" },
            { "cn =" , "cn=" },
            { "cn = " , "cn=" },
            { "dc=com" , "dc=com" },
            { "dc=com+o=com" , "dc=com+o=com" },
            { "DC=COM" , "DC=COM" },
            { "dc = com" , "dc=com" },
            { " dc = com " , "dc=com" },
            { "dc=example,dc=com" , "dc=example,dc=com" },
            { "dc=example, dc=com" , "dc=example,dc=com" },
            { "dc=example ,dc=com" , "dc=example,dc=com" },
            { "dc =example , dc  =   com" , "dc=example,dc=com" },
            { "givenName=John+cn=Doe,ou=People,dc=example,dc=com",
                "givenName=John+cn=Doe,ou=People,dc=example,dc=com" },
            { "givenName=John\\+cn=Doe,ou=People,dc=example,dc=com",
                "givenName=John\\+cn=Doe,ou=People,dc=example,dc=com" },
            { "cn=Doe\\, John,ou=People,dc=example,dc=com", "cn=Doe\\, John,ou=People,dc=example,dc=com" },
            { "UID=jsmith,DC=example,DC=net", "UID=jsmith,DC=example,DC=net" },
            { "OU=Sales+CN=J. Smith,DC=example,DC=net",
                "OU=Sales+CN=J. Smith,DC=example,DC=net" },
            { "CN=James \\\"Jim\\\" Smith\\, III,DC=example,DC=net",
                "CN=James \\\"Jim\\\" Smith\\, III,DC=example,DC=net" },
            { "CN=John Smith\\2C III,DC=example,DC=net", "CN=John Smith\\, III,DC=example,DC=net" },
            { "CN=\\23John Smith\\20,DC=example,DC=net", "CN=\\#John Smith\\ ,DC=example,DC=net" },
            { "CN=Before\\0dAfter,DC=example,DC=net",
                // \0d is a hex representation of Carriage return. It is mapped
                // to a SPACE as defined in the MAP ( RFC 4518)
                "CN=Before\\0dAfter,DC=example,DC=net" },
            { "2.5.4.3=#04024869",
                // Unicode codepoints from 0000-0008 are mapped to nothing.
                "2.5.4.3=\\04\\02Hi" },
            { "1.1.1=" , "1.1.1=" },
            { "CN=Lu\\C4\\8Di\\C4\\87" , "CN=Lu\u010di\u0107" },
            { "ou=\\e5\\96\\b6\\e6\\a5\\ad\\e9\\83\\a8,o=Airius", "ou=\u55b6\u696d\u90e8,o=Airius" },
            { "photo=\\ john \\ ,dc=com" , "photo=\\ john \\ ,dc=com" },
            { "AB-global=" , "AB-global=" },
            { "OU= Sales + CN = J. Smith ,DC=example,DC=net", "OU=Sales+CN=J. Smith,DC=example,DC=net" },
            { "cn=John+a=b" , "cn=John+a=b" },
            { "O=\"Sue, Grabbit and Runn\",C=US", "O=Sue\\, Grabbit and Runn,C=US" }, };
    }

    @Test(dataProvider = "equivalentDnRepresentations")
    public void testEquals(String dn, String otherDn) throws Exception {
        assertThat(DN.valueOf(dn).compact()).isEqualTo(DN.valueOf(otherDn).compact());
    }

    @Test(dataProvider = "equivalentDnRepresentations")
    public void testCompareTo(String dn, String otherDn) throws Exception {
        assertThat(DN.valueOf(dn).compact().compareTo(DN.valueOf(otherDn).compact())).isEqualTo(0);
    }
}
