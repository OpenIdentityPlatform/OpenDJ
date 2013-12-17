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
package org.opends.server.admin.client.ldap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.forgerock.opendj.ldap.Attribute;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.testng.Assert;

import com.forgerock.opendj.util.Validator;

/**
 * A mock LDAP connection which is used to verify that a modify operation was
 * requested and that it has the correct parameters.
 */
public final class ModifyEntryMockLDAPConnection extends MockLDAPConnection {

    // Detect multiple calls.
    private boolean alreadyModified = false;

    private final DN expectedDN;

    // The expected set of modifications (attribute name -> list of
    // values).
    private final Map<String, List<String>> modifications = new HashMap<String, List<String>>();

    /**
     * Create a new mock ldap connection for detecting modify operations.
     *
     * @param dn
     *            The expected DN of the entry to be added.
     */
    public ModifyEntryMockLDAPConnection(String dn) {
        this.expectedDN = DN.valueOf(dn);
    }

    /**
     * Add a modification which should be part of the modify operation.
     *
     * @param expectedName
     *            The name of the expected attribute.
     * @param expectedValues
     *            The attribute's expected new values (possibly empty if
     *            deleted).
     */
    public void addExpectedModification(String expectedName, String... expectedValues) {
        Validator.ensureNotNull(expectedName);
        Validator.ensureNotNull(expectedValues);
        modifications.put(expectedName, Arrays.asList(expectedValues));
    }

    /**
     * Determines whether or not the entry was modified.
     *
     * @return Returns <code>true</code> if it was modified.
     */
    public boolean isEntryModified() {
        return alreadyModified;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void modifyEntry(ModifyRequest request) throws ErrorResultException {
        Assert.assertFalse(alreadyModified);
        Assert.assertEquals(request.getName(), expectedDN);

        Map<String, List<String>> expected = new HashMap<String, List<String>>(modifications);
        for (Modification modification : request.getModifications()) {
            Attribute attribute = modification.getAttribute();
            String attrName = attribute.getAttributeDescription().getAttributeType().getNameOrOID();
            List<String> values = expected.remove(attrName);
            if (values == null) {
                Assert.fail("Unexpected modification to attribute " + attrName);
            }
            assertAttributeEquals(attribute, values);
        }
        if (!expected.isEmpty()) {
            Assert.fail("Missing modifications to: " + expected.keySet());
        }

        alreadyModified = true;
    }
}
