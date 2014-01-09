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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.forgerock.opendj.config.client.ldap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.util.Reject;
import org.testng.Assert;

/**
 * A mock LDAP connection which is used to verify that an add operation was
 * requested and that it has the correct parameters.
 */
public final class CreateEntryMockLDAPConnection extends MockLDAPConnection {

    // Detect multiple calls.
    private boolean alreadyAdded = false;

    // The expected set of attributes (attribute name -> list of
    // values).
    private final Map<String, List<String>> attributes = new HashMap<String, List<String>>();

    // The expected DN.
    private final DN expectedDN;

    /**
     * Create a new mock ldap connection for detecting add operations.
     *
     * @param dn
     *            The expected DN of the entry to be added.
     */
    public CreateEntryMockLDAPConnection(String dn) {
        this.expectedDN = DN.valueOf(dn);
    }

    /**
     * Add an attribute which should be part of the add operation.
     *
     * @param expectedName
     *            The name of the expected attribute.
     * @param expectedValues
     *            The attribute's expected values (never empty).
     */
    public void addExpectedAttribute(String expectedName, String... expectedValues) {
        Reject.ifNull(expectedName);
        Reject.ifNull(expectedValues);
        Reject.ifFalse(expectedValues.length > 0, "should have at least one expected value");
        attributes.put(expectedName, Arrays.asList(expectedValues));
    }

    /**
     * Asserts that the entry was created.
     */
    public void assertEntryIsCreated() {
        Assert.assertTrue(alreadyAdded);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createEntry(Entry entry) throws ErrorResultException {
        Assert.assertFalse(alreadyAdded);
        Assert.assertEquals(entry.getName(), expectedDN);

        Map<String, List<String>> expected = new HashMap<String, List<String>>(this.attributes);
        for (Attribute attribute : entry.getAllAttributes()) {
            String attrName = attribute.getAttributeDescription().getAttributeType().getNameOrOID();
            List<String> values = expected.remove(attrName);
            if (values == null) {
                Assert.fail("Unexpected attribute " + attrName);
            }
            assertAttributeEquals(attribute, values);
        }
        if (!expected.isEmpty()) {
            Assert.fail("Missing expected attributes: " + expected.keySet());
        }

        alreadyAdded = true;
    }
}
