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
 * Portions Copyright 2026 3A Systems, LLC.
 */
package com.forgerock.opendj.cli;

import java.io.File;
import java.io.IOException;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertEquals;

@SuppressWarnings("javadoc")
public class UtilsTestCase extends CliTestCase {

    @Test(expectedExceptions = ClientException.class)
    public void testInvalidJavaVersion() throws ClientException {
        final String original = System.getProperty("java.specification.version");
        System.setProperty("java.specification.version", "1.6");
        try {
            Utils.checkJavaVersion();
        } finally {
            System.setProperty("java.specification.version", original);
        }
    }

    @Test
    public void testValidJavaVersion() throws ClientException {
        Utils.checkJavaVersion();
    }

    @Test
    public void testCanWriteOnNewFile() throws ClientException, IOException {
        final File f = File.createTempFile("tempFile", ".txt");
        f.deleteOnExit();
        assertTrue(f.exists());
        assertTrue(Utils.canWrite(f.getPath()));
    }

    @Test
    public void testCannotWriteOnNewFile() throws ClientException, IOException {
        final File f = File.createTempFile("tempFile", ".txt");
        f.setReadOnly();
        f.deleteOnExit();
        assertTrue(f.exists());
        if (!System.getProperty("user.name").equals("root")) {
            // Expected behaviour
            assertFalse(Utils.canWrite(f.getPath()));
        } else {
            // Workaround for running tests in Docker
            // side effect of https://bugs.openjdk.java.net/browse/JDK-6931128,
            // where file permissions are not enforced if user is root.
            assertTrue(Utils.canWrite(f.getPath()));
        }
    }

    @Test
    public void testGetHostNameForLdapUrl() {
        assertEquals(Utils.getHostNameForLdapUrl("2a01:e35:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx"),
                "[2a01:e35:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx]");
        assertEquals(Utils.getHostNameForLdapUrl("basicUrl"), "basicUrl");
        assertEquals(Utils.getHostNameForLdapUrl(null), null);
        // Left/right brackets.
        assertEquals(Utils.getHostNameForLdapUrl("[2a01:e35:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx"),
                "[2a01:e35:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx]");
        assertEquals(Utils.getHostNameForLdapUrl("2a01:e35:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx]"),
                "[2a01:e35:xxxx:xxxx:xxxx:xxxx:xxxx:xxxx]");
    }

    @Test
    public void isDN() {
        assertTrue(Utils.isDN("cn=Jensen,ou=people,dc=example,dc=com"));
        assertTrue(Utils.isDN("cn=John Doe,dc=example,dc=org"));
        assertFalse(Utils.isDN(null));
        assertFalse(Utils.isDN("babs@example.com"));
    }

    @DataProvider
    public Object[][] administratorUIDs() {
        return new Object[][] { { "admin" }, { "a,b" }, { "a+b" }, { "#1" }, { " lead" }, { "a\\b" }, { "a;b" },
            { "a=b" }, { "J\u00f6rg" } };
    }

    @Test(dataProvider = "administratorUIDs")
    public void getAdministratorDNKeepsTheWholeUID(final String uid) {
        final DN adminDN = Utils.getAdministratorDN(uid);
        assertEquals(adminDN.parent(), DN.valueOf("cn=Administrators,cn=admin data"));
        final RDN rdn = adminDN.rdn();
        assertEquals(rdn.size(), 1);
        assertEquals(rdn.getFirstAVA().getAttributeType().getNameOrOID(), "cn");
        assertEquals(rdn.getFirstAVA().getAttributeValue(), ByteString.valueOfUtf8(uid));
    }


}
