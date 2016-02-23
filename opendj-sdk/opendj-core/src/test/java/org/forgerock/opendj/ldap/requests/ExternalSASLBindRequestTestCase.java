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
 * Tests the external SASL Bind requests.
 */
@SuppressWarnings("javadoc")
public class ExternalSASLBindRequestTestCase extends BindRequestTestCase {
    private static final ExternalSASLBindRequest NEW_EXTERNAL_SASL_BIND_REQUEST = Requests.newExternalSASLBindRequest();

    @DataProvider(name = "ExternalSASLBindRequests")
    private Object[][] getExternalSASLBindRequests() throws Exception {
        return createModifiableInstance();
    }

    @Override
    protected ExternalSASLBindRequest[] newInstance() {
        return new ExternalSASLBindRequest[] {
            NEW_EXTERNAL_SASL_BIND_REQUEST };
    }

    @Override
    protected Request copyOf(Request original) {
        return Requests.copyOfExternalSASLBindRequest((ExternalSASLBindRequest) original);
    }

    @Override
    protected Request unmodifiableOf(Request original) {
        return Requests.unmodifiableExternalSASLBindRequest((ExternalSASLBindRequest) original);
    }

    @Test(dataProvider = "ExternalSASLBindRequests")
    public void testModifiableRequest(final ExternalSASLBindRequest original) {
        final String authID = "u:user.0";
        final ExternalSASLBindRequest copy = (ExternalSASLBindRequest) copyOf(original);
        copy.setAuthorizationID(authID);
        assertThat(copy.getAuthorizationID()).isEqualTo(authID);
        assertThat(copy.getSASLMechanism()).isEqualTo(original.getSASLMechanism());
    }

    @Test(dataProvider = "ExternalSASLBindRequests")
    public void testUnmodifiableRequest(final ExternalSASLBindRequest original) {
        final ExternalSASLBindRequest unmodifiable = (ExternalSASLBindRequest) unmodifiableOf(original);
        assertThat(unmodifiable.getAuthorizationID()).isEqualTo(original.getAuthorizationID());
        assertThat(unmodifiable.getAuthenticationType()).isEqualTo(original.getAuthenticationType());
        assertThat(unmodifiable.getSASLMechanism()).isEqualTo(original.getSASLMechanism());
    }

    @Test(dataProvider = "ExternalSASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetAuthorizationID(final ExternalSASLBindRequest original) {
        final ExternalSASLBindRequest unmodifiable = (ExternalSASLBindRequest) unmodifiableOf(original);
        unmodifiable.setAuthorizationID("dn: uid=scarter,ou=people,dc=example,dc=com");
    }
}
