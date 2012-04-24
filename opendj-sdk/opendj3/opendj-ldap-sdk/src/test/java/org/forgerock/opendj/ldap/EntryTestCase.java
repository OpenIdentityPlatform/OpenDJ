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

package org.forgerock.opendj.ldap;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test {@code Entry}.
 */
@SuppressWarnings("javadoc")
public final class EntryTestCase extends SdkTestCase {
    private static interface EntryFactory {
        Entry newEntry(String... ldifLines);
    }

    private static final class LinkedHashMapEntryFactory implements EntryFactory {
        public Entry newEntry(final String... ldifLines) {
            return new LinkedHashMapEntry(ldifLines);
        }
    }

    private static final class TreeMapEntryFactory implements EntryFactory {
        public Entry newEntry(final String... ldifLines) {
            return new TreeMapEntry(ldifLines);
        }
    }

    @DataProvider(name = "EntryFactory")
    public Object[][] entryFactory() {
        // Value, type, options, containsOptions("foo")
        return new Object[][] { { new TreeMapEntryFactory() }, { new LinkedHashMapEntryFactory() } };
    }

    @Test(dataProvider = "EntryFactory")
    public void smokeTest(final EntryFactory factory) throws Exception {
        final Entry entry1 =
                factory.newEntry("dn: cn=Joe Bloggs,dc=example,dc=com", "objectClass: top",
                        "objectClass: person", "cn: Joe Bloggs", "sn: Bloggs", "givenName: Joe",
                        "description: A description");

        final Entry entry2 =
                factory.newEntry("dn: cn=Joe Bloggs,dc=example,dc=com", "changetype: add",
                        "objectClass: top", "objectClass: person", "cn: Joe Bloggs", "sn: Bloggs",
                        "givenName: Joe", "description: A description");

        Assert.assertEquals(entry1, entry2);

        for (final Entry e : new Entry[] { entry1, entry2 }) {
            Assert.assertEquals(e.getName(), DN.valueOf("cn=Joe Bloggs,dc=example,dc=com"));
            Assert.assertEquals(e.getAttributeCount(), 5);

            Assert.assertEquals(e.getAttribute("objectClass").size(), 2);
            Assert.assertTrue(e.containsAttribute("objectClass", "top", "person"));
            Assert.assertFalse(e.containsAttribute("objectClass", "top", "person", "foo"));

            Assert.assertTrue(e.containsAttribute("objectClass"));
            Assert.assertTrue(e.containsAttribute("cn"));
            Assert.assertTrue(e.containsAttribute("cn", "Joe Bloggs"));
            Assert.assertFalse(e.containsAttribute("cn", "Jane Bloggs"));
            Assert.assertTrue(e.containsAttribute("sn"));
            Assert.assertTrue(e.containsAttribute("givenName"));
            Assert.assertTrue(e.containsAttribute("description"));

            Assert.assertEquals(e.getAttribute("cn").firstValueAsString(), "Joe Bloggs");
            Assert.assertEquals(e.getAttribute("sn").firstValueAsString(), "Bloggs");
        }
    }
}
