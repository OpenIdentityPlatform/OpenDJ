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
 * Copyright 2010 Sun Microsystems, Inc.
 * Portions copyright 2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import static org.fest.assertions.Assertions.assertThat;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LinkedAttribute;
import org.forgerock.opendj.ldap.Modification;
import org.forgerock.opendj.ldap.ModificationType;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests the modify request.
 */
@SuppressWarnings("javadoc")
public class ModifyRequestTestCase extends RequestsTestCase {

    private static final ModifyRequest ADD_MODIFICATION = Requests.newModifyRequest(DN.valueOf("uid=Modifyrequest1"))
            .addModification(ModificationType.ADD, "userpassword", "password");
    private static final ModifyRequest ADD_MODIFICATION2 = Requests.newModifyRequest("cn=Modifyrequesttestcase")
            .addModification(ModificationType.ADD, "userpassword", "password");
    private static final ModifyRequest NEW_MODIFY_REQUEST = Requests.newModifyRequest("dn: ou=People,o=test",
            "changetype: modify", "add: userpassword", "userpassword: password");

    @DataProvider(name = "ModifyRequests")
    private Object[][] getModifyRequests() throws Exception {
        return createModifiableInstance();
    }

    @Override
    protected ModifyRequest[] newInstance() {
        return new ModifyRequest[] {
            ADD_MODIFICATION,
            ADD_MODIFICATION2,
            NEW_MODIFY_REQUEST
        };
    }

    @Override
    protected Request copyOf(Request original) {
        return Requests.copyOfModifyRequest((ModifyRequest) original);
    }

    @Override
    protected Request unmodifiableOf(Request original) {
        return Requests.unmodifiableModifyRequest((ModifyRequest) original);
    }

    @Test(dataProvider = "ModifyRequests")
    public void testModifiableRequest(final ModifyRequest original) {
        final String newDN = "cn=Ted,ou=People,dc=example,dc=com";

        final ModifyRequest copy = (ModifyRequest) copyOf(original);
        copy.setName(DN.valueOf(newDN));
        assertThat(copy.getName().toString()).isEqualTo(newDN);
        assertThat(copy.toString()).contains("dn=" + newDN);
    }

    @Test(dataProvider = "ModifyRequests")
    public void testUnmodifiableRequest(final ModifyRequest original) {
        final ModifyRequest unmodifiable = (ModifyRequest) unmodifiableOf(original);
        assertThat(unmodifiable.getName().toString()).isEqualTo(original.getName().toString());
        assertThat(unmodifiable.getModifications().size()).isEqualTo(original.getModifications().size());

    }

    @Test(dataProvider = "ModifyRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableAddModification(final ModifyRequest original) {
        final ModifyRequest unmodifiable = (ModifyRequest) unmodifiableOf(original);
        unmodifiable.addModification(ModificationType.ADD, "member",
                DN.valueOf("uid=scarter,ou=people,dc=example,dc=com"));
    }

    @Test(dataProvider = "ModifyRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableAddModification2(final ModifyRequest original) {
        final ModifyRequest unmodifiable = (ModifyRequest) unmodifiableOf(original);
        unmodifiable.addModification(new Modification(ModificationType.ADD,
                new LinkedAttribute("description", "value1")));
    }

    @Test(dataProvider = "ModifyRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetName(final ModifyRequest original) {
        final ModifyRequest unmodifiable = (ModifyRequest) unmodifiableOf(original);
        unmodifiable.setName(DN.valueOf("uid=scarter,ou=people,dc=example,dc=com"));
    }

    @Test(dataProvider = "ModifyRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetName2(final ModifyRequest original) {
        final ModifyRequest unmodifiable = (ModifyRequest) unmodifiableOf(original);
        unmodifiable.setName("uid=scarter,ou=people,dc=example,dc=com");
    }
}
