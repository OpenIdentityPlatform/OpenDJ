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
 */

package org.forgerock.opendj.ldif;

import static org.testng.Assert.assertNotNull;

import java.io.FileInputStream;
import java.util.NoSuchElementException;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.TestCaseUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * This class tests the LDIFEntryReader functionality.
 */
public final class LDIFEntryReaderTestCase extends LDIFTestCase {
    /**
     * Tests readEntry method of LDIFEntryReader class.See
     * https://opends.dev.java.net/issues/show_bug.cgi?id=4545 for more details.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test()
    public void testEmpty() throws Exception {
        final String path = TestCaseUtils.createTempFile("");
        final FileInputStream in = new FileInputStream(path);
        final LDIFEntryReader reader = new LDIFEntryReader(in);
        try {
            Assert.assertFalse(reader.hasNext());
            Assert.assertFalse(reader.hasNext());
            try {
                reader.readEntry();
                Assert.fail("reader.readEntry() should have thrown NoSuchElementException");
            } catch (NoSuchElementException e) {
                // This is expected.
            }
            Assert.assertFalse(reader.hasNext());
        } finally {
            reader.close();
        }
    }

    /**
     * Tests readEntry method of LDIFEntryReader class.See
     * https://opends.dev.java.net/issues/show_bug.cgi?id=4545 for more details.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test()
    public void testReadEntry() throws Exception {
        final String path =
                TestCaseUtils.createTempFile("dn: uid=1,ou=people,dc=ucsf,dc=edu",
                        "objectClass: top", "objectClass: person",
                        "objectClass: organizationalperson", "objectClass: inetorgperson",
                        "givenName: Aaccf", "sn: Amar", "cn: Aaccf Amar", "initials: ASA",
                        "employeeNumber: 020000001", "uid: 1", "mail: Aaccf.Amar@ucsf.edu",
                        "userPassword: password", "telephoneNumber: +1 685 622 6202",
                        "homePhone: +1 225 216 5900", "pager: +1 779 041 6341",
                        "mobile: +1 010 154 3228", "street: 01251 Chestnut Street",
                        "l: Panama City", "st: DE", "postalCode: 50369",
                        "postalAddress: Aaccf Amar$01251 Chestnut Street$Panama City, DE  50369",
                        "description: This is the description for Aaccf Amar.");
        final FileInputStream in = new FileInputStream(path);
        final LDIFEntryReader reader = new LDIFEntryReader(in);
        try {
            Assert.assertTrue(reader.hasNext());
            final Entry entry = reader.readEntry();
            assertNotNull(entry);
            Assert.assertEquals(entry.getName(), DN.valueOf("uid=1,ou=people,dc=ucsf,dc=edu"));
            Assert.assertFalse(reader.hasNext());
            try {
                reader.readEntry();
                Assert.fail("reader.readEntry() should have thrown NoSuchElementException");
            } catch (NoSuchElementException e) {
                // This is expected.
            }
        } finally {
            reader.close();
        }
    }
}
