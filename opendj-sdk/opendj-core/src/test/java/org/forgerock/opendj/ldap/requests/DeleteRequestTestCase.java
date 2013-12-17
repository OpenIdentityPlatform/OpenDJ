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
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests the delete request.
 */
@SuppressWarnings("javadoc")
public class DeleteRequestTestCase extends RequestsTestCase {

    private static final DeleteRequest NEW_DELETE_REQUEST = Requests.newDeleteRequest(DN.valueOf("uid=Deleterequest1"));
    private static final DeleteRequest NEW_DELETE_REQUEST2 = Requests.newDeleteRequest("cn=Deleterequesttestcase");
    private static final DeleteRequest NEW_DELETE_REQUEST3 = Requests.newDeleteRequest("uid=user.999,ou=people,o=test");

    @DataProvider(name = "deleteRequests")
    private Object[][] getDeleteRequests() throws Exception {
        return createModifiableInstance();
    }

    @Override
    protected DeleteRequest[] newInstance() {
        return new DeleteRequest[] {
            NEW_DELETE_REQUEST,
            NEW_DELETE_REQUEST2,
            NEW_DELETE_REQUEST3
        };
    }

    @Override
    protected Request copyOf(Request original) {
        return Requests.copyOfDeleteRequest((DeleteRequest) original);
    }

    @Override
    protected Request unmodifiableOf(Request original) {
        return Requests.unmodifiableDeleteRequest((DeleteRequest) original);
    }

    @Test(dataProvider = "deleteRequests")
    public void testModifiableRequest(final DeleteRequest original) {
        final String newValue = "uid=newName";
        final DeleteRequest copy = (DeleteRequest) copyOf(original);

        copy.setName(newValue);
        assertThat(copy.getName().toString()).isEqualTo(newValue);
        assertThat(original.getName().toString()).isNotEqualTo(newValue);
    }

    @Test(dataProvider = "deleteRequests")
    public void testUnmodifiableRequest(final DeleteRequest original) {
        final DeleteRequest unmodifiable = (DeleteRequest) unmodifiableOf(original);
        assertThat(unmodifiable.getName().toString()).isEqualTo(original.getName().toString());
    }

    @Test(dataProvider = "deleteRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetName(final DeleteRequest original) {
        final DeleteRequest unmodifiable = (DeleteRequest) unmodifiableOf(original);
        unmodifiable.setName("uid=scarter,ou=people,dc=example,dc=com");
    }

    @Test(dataProvider = "deleteRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetName2(final DeleteRequest original) {
        final DeleteRequest unmodifiable = (DeleteRequest) unmodifiableOf(original);
        unmodifiable.setName(DN.valueOf("uid=scarter,ou=people,dc=example,dc=com"));
    }
}
