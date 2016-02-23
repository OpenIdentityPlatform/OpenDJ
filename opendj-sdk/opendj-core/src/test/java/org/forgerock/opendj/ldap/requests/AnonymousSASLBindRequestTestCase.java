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
 * Tests anonymous SASL bind requests.
 */
@SuppressWarnings("javadoc")
public class AnonymousSASLBindRequestTestCase extends BindRequestTestCase {
    private static final AnonymousSASLBindRequest NEW_ANONYMOUS_SASL_BIND_REQUEST2 = Requests
            .newAnonymousSASLBindRequest("test");
    private static final AnonymousSASLBindRequest NEW_ANONYMOUS_SASL_BIND_REQUEST = Requests
            .newAnonymousSASLBindRequest("");

    @DataProvider(name = "anonymousSASLBindRequests")
    private Object[][] getAnonymousSASLBindRequests() throws Exception {
        return createModifiableInstance();
    }

    @Override
    protected AnonymousSASLBindRequest[] newInstance() {
        return new AnonymousSASLBindRequest[] { NEW_ANONYMOUS_SASL_BIND_REQUEST, NEW_ANONYMOUS_SASL_BIND_REQUEST2 };
    }

    @Override
    protected Request copyOf(Request original) {
        return Requests.copyOfAnonymousSASLBindRequest((AnonymousSASLBindRequest) original);
    }

    @Override
    protected Request unmodifiableOf(Request original) {
        return Requests.unmodifiableAnonymousSASLBindRequest((AnonymousSASLBindRequest) original);
    }

    @Test(dataProvider = "anonymousSASLBindRequests")
    public void testModifiableRequest(final AnonymousSASLBindRequest original) {
        final String newValue = "MyNewValue";
        final AnonymousSASLBindRequest copy = (AnonymousSASLBindRequest) copyOf(original);
        copy.setTraceString(newValue);
        assertThat(copy.getTraceString()).isEqualTo(newValue);
        assertThat(copy.getAuthenticationType()).isEqualTo(original.getAuthenticationType());
        assertThat(copy.getName()).isEqualTo(original.getName());
        assertThat(copy.getSASLMechanism()).isEqualTo(original.getSASLMechanism());
        assertThat(copy.getTraceString()).isNotEqualTo(original.getTraceString());
    }

    @Test(dataProvider = "anonymousSASLBindRequests")
    public void testUnmodifiableRequest(final AnonymousSASLBindRequest original) {
        final AnonymousSASLBindRequest unmodifiable = (AnonymousSASLBindRequest) unmodifiableOf(original);
        assertThat(unmodifiable.getAuthenticationType()).isEqualTo(original.getAuthenticationType());
        assertThat(unmodifiable.getName()).isEqualTo(original.getName());
        assertThat(unmodifiable.getSASLMechanism()).isEqualTo(original.getSASLMechanism());
        assertThat(unmodifiable.getTraceString()).isEqualTo(original.getTraceString());
    }

    @Test(dataProvider = "anonymousSASLBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableRequestSetter(final AnonymousSASLBindRequest original) {
        final AnonymousSASLBindRequest unmodifiable = (AnonymousSASLBindRequest) unmodifiableOf(original);
        unmodifiable.setTraceString("the_new_trace_string");
    }
}
