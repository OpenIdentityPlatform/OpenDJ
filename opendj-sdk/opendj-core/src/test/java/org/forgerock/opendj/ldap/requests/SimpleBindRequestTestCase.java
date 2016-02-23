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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests Simple Bind requests.
 */
@SuppressWarnings("javadoc")
public class SimpleBindRequestTestCase extends BindRequestTestCase {
    private static final SimpleBindRequest NEW_SIMPLE_BIND_REQUEST = Requests.newSimpleBindRequest();
    private static final SimpleBindRequest NEW_SIMPLE_BIND_REQUEST2 = Requests.newSimpleBindRequest("username",
            "password".toCharArray());

    @DataProvider(name = "simpleBindRequests")
    private Object[][] getSimpleBindRequests() throws Exception {
        return createModifiableInstance();
    }

    @Override
    protected SimpleBindRequest[] newInstance() {
        return new SimpleBindRequest[] {
            NEW_SIMPLE_BIND_REQUEST, // anonymous;
            NEW_SIMPLE_BIND_REQUEST2 };
    }

    @Override
    protected Request copyOf(Request original) {
        return Requests.copyOfSimpleBindRequest((SimpleBindRequest) original);
    }

    @Override
    protected Request unmodifiableOf(Request original) {
        return Requests.unmodifiableSimpleBindRequest((SimpleBindRequest) original);
    }

    @Test(dataProvider = "simpleBindRequests")
    public void testModifiableRequest(final SimpleBindRequest original) {
        final String name = "user.0";
        final String password = "pass99";

        final SimpleBindRequest copy = (SimpleBindRequest) copyOf(original);

        copy.setName(name);
        copy.setPassword(password.getBytes());

        assertThat(copy.getName()).isEqualTo(name);
        assertThat(copy.getPassword()).isEqualTo(password.getBytes());
        assertThat(copy.getAuthenticationType()).isEqualTo(original.getAuthenticationType());
        assertThat(original.getName()).isNotEqualTo(copy.getName());
        assertThat(original.getPassword()).isNotEqualTo(copy.getPassword());
    }

    @Test(dataProvider = "simpleBindRequests")
    public void testUnmodifiableRequest(final SimpleBindRequest original) {
        final SimpleBindRequest unmodifiable = (SimpleBindRequest) unmodifiableOf(original);
        assertThat(unmodifiable.getAuthenticationType()).isEqualTo(original.getAuthenticationType());
        assertThat(unmodifiable.getName()).isEqualTo(original.getName());
        assertThat(unmodifiable.getPassword()).isEqualTo(original.getPassword());
    }

    @Test(dataProvider = "simpleBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetName2(final SimpleBindRequest original) {
        final SimpleBindRequest unmodifiable = (SimpleBindRequest) unmodifiableOf(original);
        unmodifiable.setName("uid=scarter,ou=people,dc=example,dc=com");
    }

    @Test(dataProvider = "simpleBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetPassword(final SimpleBindRequest original) {
        final SimpleBindRequest unmodifiable = (SimpleBindRequest) unmodifiableOf(original);
        unmodifiable.setPassword("password".toCharArray());
    }

    @Test(dataProvider = "simpleBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetPassword2(final SimpleBindRequest original) {
        final SimpleBindRequest unmodifiable = (SimpleBindRequest) unmodifiableOf(original);
        unmodifiable.setPassword("password".getBytes());
    }
}
