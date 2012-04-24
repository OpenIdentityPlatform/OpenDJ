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

import java.util.ArrayList;
import java.util.List;

import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * This class tests the LDIFEntryWriter functionality.
 */
public final class LDIFEntryWriterTestCase extends LDIFTestCase {

    /**
     * Tests writeEntry method of LDIFEntryWriter class.See
     * https://opends.dev.java.net/issues/show_bug.cgi?id=4545 for more details.
     *
     * @throws Exception
     *             If the test failed unexpectedly.
     */
    @Test()
    public void testWriteEntry() throws Exception {
        final Entry entry = new LinkedHashMapEntry("cn=John Doe,ou=people,dc=example,dc=com");
        entry.addAttribute("objectClass", "top", "person", "inetOrgPerson");
        entry.addAttribute("cn", "John Doe");
        entry.addAttribute("sn", "Doe");
        entry.addAttribute("givenName", "John");
        entry.addAttribute("description", "one two", "three four", "five six");
        entry.addAttribute("typeOnly");
        entry.addAttribute("localized;lang-fr", "\u00e7edilla");

        final List<String> actual = new ArrayList<String>();
        final LDIFEntryWriter writer = new LDIFEntryWriter(actual);
        writer.writeEntry(entry);
        writer.close();

        final String[] expected =
                new String[] { "dn: cn=John Doe,ou=people,dc=example,dc=com", "objectClass: top",
                    "objectClass: person", "objectClass: inetOrgPerson", "cn: John Doe", "sn: Doe",
                    "givenName: John", "description: one two", "description: three four",
                    "description: five six", "typeOnly: ", "localized;lang-fr:: w6dlZGlsbGE=", "", };

        Assert.assertEquals(actual.size(), expected.length);
        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals(actual.get(i), expected[i], "LDIF output was " + actual);
        }
    }
}
