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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock AS
 */

package org.forgerock.opendj.ldap.requests;

import static org.fest.assertions.Assertions.assertThat;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.RDN;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests the Modify DN requests.
 */
@SuppressWarnings("javadoc")
public class ModifyDNRequestTestCase extends RequestsTestCase {

    private static final ModifyDNRequest NEW_MODIFY_DN_REQUEST = Requests.newModifyDNRequest(
            "uid=user.100,ou=people,o=test", "uid=100.user,ou=people,o=testl");
    private static final ModifyDNRequest NEW_MODIFY_DN_REQUEST2 = Requests.newModifyDNRequest(
            "cn=ModifyDNrequesttestcase", "cn=xyz");

    @DataProvider(name = "ModifyDNRequests")
    private Object[][] getModifyDNRequests() throws Exception {
        return createModifiableInstance();
    }

    @Override
    protected ModifyDNRequest[] newInstance() {
        return new ModifyDNRequest[] {
            NEW_MODIFY_DN_REQUEST,
            NEW_MODIFY_DN_REQUEST2,
        };
    }

    @Override
    protected Request copyOf(Request original) {
        return Requests.copyOfModifyDNRequest((ModifyDNRequest) original);
    }

    @Override
    protected Request unmodifiableOf(Request original) {
        return Requests.unmodifiableModifyDNRequest((ModifyDNRequest) original);
    }

    @Test(dataProvider = "ModifyDNRequests")
    public void testModifiableRequest(final ModifyDNRequest original) {
        final String newDN = "cn=Ted,ou=People,dc=example,dc=com";
        final String superior = "ou=People,dc=example,dc=org";

        final ModifyDNRequest copy = (ModifyDNRequest) copyOf(original);
        copy.setName(DN.valueOf(newDN));
        copy.setDeleteOldRDN(true);
        copy.setNewSuperior(superior);
        assertThat(copy.getName().toString()).isEqualTo(newDN);
        assertThat(copy.getNewSuperior().toString()).isEqualTo(superior);
        assertThat(original.getNewSuperior()).isNull();
        assertThat(copy.toString()).contains("deleteOldRDN=true");
    }

    @Test(dataProvider = "ModifyDNRequests")
    public void testUnmodifiableRequest(final ModifyDNRequest original) {
        final ModifyDNRequest unmodifiable = (ModifyDNRequest) unmodifiableOf(original);
        assertThat(unmodifiable.getName().toString()).isEqualTo(original.getName().toString());
        assertThat(original.getNewSuperior()).isNull();
        assertThat(unmodifiable.getNewSuperior()).isNull();
    }

    @Test(dataProvider = "ModifyDNRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetDeleteOldRDN(final ModifyDNRequest original) {
        final ModifyDNRequest unmodifiable = (ModifyDNRequest) unmodifiableOf(original);
        unmodifiable.setDeleteOldRDN(true);
    }

    @Test(dataProvider = "ModifyDNRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetName(final ModifyDNRequest original) {
        final ModifyDNRequest unmodifiable = (ModifyDNRequest) unmodifiableOf(original);
        unmodifiable.setName(DN.valueOf("uid=scarter,ou=people,dc=example,dc=com"));
    }

    @Test(dataProvider = "ModifyDNRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetName2(final ModifyDNRequest original) {
        final ModifyDNRequest unmodifiable = (ModifyDNRequest) unmodifiableOf(original);
        unmodifiable.setName("uid=scarter,ou=people,dc=example,dc=com");
    }

    @Test(dataProvider = "ModifyDNRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetNewRDN(final ModifyDNRequest original) {
        final ModifyDNRequest unmodifiable = (ModifyDNRequest) unmodifiableOf(original);
        unmodifiable.setNewRDN("dc=example,dc=org");
    }

    @Test(dataProvider = "ModifyDNRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetNewRDN2(final ModifyDNRequest original) {
        final ModifyDNRequest unmodifiable = (ModifyDNRequest) unmodifiableOf(original);
        unmodifiable.setNewRDN(RDN.valueOf("dc=example,dc=org"));
    }

    @Test(dataProvider = "ModifyDNRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetNewSuperior(final ModifyDNRequest original) {
        final ModifyDNRequest unmodifiable = (ModifyDNRequest) unmodifiableOf(original);
        unmodifiable.setNewSuperior("ou=people2,dc=example,dc=com");
    }

    @Test(dataProvider = "ModifyDNRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetNewSuperior2(final ModifyDNRequest original) {
        final ModifyDNRequest unmodifiable = (ModifyDNRequest) unmodifiableOf(original);
        unmodifiable.setNewSuperior(DN.valueOf("ou=people2,dc=example,dc=com"));
    }
}
