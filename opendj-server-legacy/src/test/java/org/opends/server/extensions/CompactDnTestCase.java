/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.extensions.StaticGroup.CompactDn;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.fest.assertions.Assertions.*;

/**
 * This class defines a set of tests for the inner class StaticGroup.CompactDn.
 */
@SuppressWarnings("javadoc")
public class CompactDnTestCase extends DirectoryServerTestCase {

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
      assertThat(new CompactDn(DN.valueOf(dn))).isEqualTo(new CompactDn(DN.valueOf(otherDn)));
    }

    @Test(dataProvider = "equivalentDnRepresentations", singleThreaded = true)
    public void testCompareTo(String dn, String otherDn) throws Exception {
        assertThat(new CompactDn(DN.valueOf(dn)).compareTo(new CompactDn(DN.valueOf(otherDn)))).isEqualTo(0);
    }
}
