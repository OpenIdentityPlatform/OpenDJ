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

import java.util.Iterator;

import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test {@code Entries}.
 */
public final class EntriesTestCase extends SdkTestCase {
    /**
     * Creates test data for {@link #testDiffEntries}.
     *
     * @return The test data.
     */
    @DataProvider(name = "createTestDiffEntriesData")
    public Object[][] createTestDiffEntriesData() {
        // @formatter:off
        Entry empty = new LinkedHashMapEntry(
            "dn: cn=test",
            "objectClass: top",
            "objectClass: test"
        );

        Entry from = new LinkedHashMapEntry(
            "dn: cn=test",
            "objectClass: top",
            "objectClass: test",
            "fromOnly: fromOnlyValue",
            "bothSame: one",
            "bothSame: two",
            "bothSame: three",
            "bothDifferentDeletes: common",
            "bothDifferentDeletes: fromOnly1",
            "bothDifferentDeletes: fromOnly2",
            "bothDifferentAdds: common",
            "bothDifferentAddsAndDeletes: common",
            "bothDifferentAddsAndDeletes: fromOnly",
            "bothDifferentReplace: fromOnly1",
            "bothDifferentReplace: fromOnly2"
        );

        Entry to = new LinkedHashMapEntry(
            "dn: cn=test",
            "objectClass: top",
            "objectClass: test",
            "toOnly: toOnlyValue",
            "bothSame: one",
            "bothSame: two",
            "bothSame: three",
            "bothDifferentDeletes: common",
            "bothDifferentAdds: common",
            "bothDifferentAdds: toOnly1",
            "bothDifferentAdds: toOnly2",
            "bothDifferentAddsAndDeletes: common",
            "bothDifferentAddsAndDeletes: toOnly",
            "bothDifferentReplace: toOnly1",
            "bothDifferentReplace: toOnly2"
        );

        ModifyRequest diffFromEmpty = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify",
            "delete: bothDifferentAdds",
            "bothDifferentAdds: common",
            "-",
            "delete: bothDifferentAddsAndDeletes",
            "bothDifferentAddsAndDeletes: common",
            "bothDifferentAddsAndDeletes: fromOnly",
            "-",
            "delete: bothDifferentDeletes",
            "bothDifferentDeletes: common",
            "bothDifferentDeletes: fromOnly1",
            "bothDifferentDeletes: fromOnly2",
            "-",
            "delete: bothDifferentReplace",
            "bothDifferentReplace: fromOnly1",
            "bothDifferentReplace: fromOnly2",
            "-",
            "delete: bothSame",
            "bothSame: one",
            "bothSame: two",
            "bothSame: three",
            "-",
            "delete: fromOnly",
            "fromOnly: fromOnlyValue"
        );

        ModifyRequest diffEmptyTo = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify",
            "add: bothDifferentAdds",
            "bothDifferentAdds: common",
            "bothDifferentAdds: toOnly1",
            "bothDifferentAdds: toOnly2",
            "-",
            "add: bothDifferentAddsAndDeletes",
            "bothDifferentAddsAndDeletes: common",
            "bothDifferentAddsAndDeletes: toOnly",
            "-",
            "add: bothDifferentDeletes",
            "bothDifferentDeletes: common",
            "-",
            "add: bothDifferentReplace",
            "bothDifferentReplace: toOnly1",
            "bothDifferentReplace: toOnly2",
            "-",
            "add: bothSame",
            "bothSame: one",
            "bothSame: two",
            "bothSame: three",
            "-",
            "add: toOnly",
            "toOnly: toOnlyValue"
        );

        ModifyRequest diffFromTo = Requests.newModifyRequest(
            "dn: cn=test",
            "changetype: modify",
            "add: bothDifferentAdds",
            "bothDifferentAdds: toOnly1",
            "bothDifferentAdds: toOnly2",
            "-",
            "add: bothDifferentAddsAndDeletes",
            "bothDifferentAddsAndDeletes: toOnly",
            "-",
            "delete: bothDifferentAddsAndDeletes",
            "bothDifferentAddsAndDeletes: fromOnly",
            "-",
            "delete: bothDifferentDeletes",
            "bothDifferentDeletes: fromOnly1",
            "bothDifferentDeletes: fromOnly2",
            "-",
            "add: bothDifferentReplace",
            "bothDifferentReplace: toOnly1",
            "bothDifferentReplace: toOnly2",
            "-",
            "delete: bothDifferentReplace",
            "bothDifferentReplace: fromOnly1",
            "bothDifferentReplace: fromOnly2",
            "-",
            "delete: fromOnly",
            "fromOnly: fromOnlyValue",
            "-",
            "add: toOnly",
            "toOnly: toOnlyValue"
        );

        // From, to, diff.
        return new Object[][] {
            { from,  empty, diffFromEmpty },
            { empty, to,    diffEmptyTo   },
            { from,  to,    diffFromTo    }
        };

        // @formatter:on
    }

    /**
     * Tests {@link Entries#diffEntries(Entry, Entry)}.
     *
     * @param from
     *            Source entry.
     * @param to
     *            Destination entry.
     * @param expected
     *            Expected modifications.
     */
    @Test(dataProvider = "createTestDiffEntriesData")
    public void testDiffEntries(final Entry from, final Entry to, final ModifyRequest expected) {
        ModifyRequest actual = Entries.diffEntries(from, to);

        Assert.assertEquals(from.getName(), actual.getName());
        Assert.assertEquals(actual.getModifications().size(), expected.getModifications().size());
        Iterator<Modification> i1 = actual.getModifications().iterator();
        Iterator<Modification> i2 = expected.getModifications().iterator();
        while (i1.hasNext()) {
            Modification m1 = i1.next();
            Modification m2 = i2.next();

            Assert.assertEquals(m1.getModificationType(), m2.getModificationType());
            Assert.assertEquals(m1.getAttribute(), m2.getAttribute());
        }
    }
}
