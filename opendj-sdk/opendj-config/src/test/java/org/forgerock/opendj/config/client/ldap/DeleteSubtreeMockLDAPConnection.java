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

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ErrorResultException;
import org.testng.Assert;

/**
 * A mock LDAP connection which is used to verify that a delete subtree takes
 * place.
 */
public final class DeleteSubtreeMockLDAPConnection extends MockLDAPConnection {

    // Detect multiple calls.
    private boolean alreadyDeleted = false;

    // The expected DN.
    private final DN expectedDN;

    /**
     * Create a new mock ldap connection for detecting subtree deletes.
     *
     * @param dn
     *            The expected subtree DN.
     */
    public DeleteSubtreeMockLDAPConnection(String dn) {
        this.expectedDN = DN.valueOf(dn);
    }

    /**
     * Asserts that the subtree was deleted.
     */
    public void assertSubtreeIsDeleted() {
        Assert.assertTrue(alreadyDeleted);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteSubtree(DN dn) throws ErrorResultException {
        Assert.assertFalse(alreadyDeleted);
        Assert.assertEquals(dn, expectedDN);
        alreadyDeleted = true;
    }
}
