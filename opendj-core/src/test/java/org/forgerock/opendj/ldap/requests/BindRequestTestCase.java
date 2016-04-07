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
 */

package org.forgerock.opendj.ldap.requests;

import static org.fest.assertions.Assertions.assertThat;
import static org.testng.Assert.assertNotNull;

import org.forgerock.opendj.io.LDAP;
import org.testng.annotations.Test;


/**
 * Tests the BIND requests.
 */
@SuppressWarnings("javadoc")
public abstract class BindRequestTestCase extends RequestsTestCase {
    @Test(dataProvider = "createModifiableInstance")
    public void testAuthType(final BindRequest request) throws Exception {
        final byte b = request.getAuthenticationType();
        assertThat(b).isIn(LDAP.TYPE_AUTHENTICATION_SASL, LDAP.TYPE_AUTHENTICATION_SIMPLE);
    }

    @Test(dataProvider = "createModifiableInstance")
    public void testBindClient(final BindRequest request) throws Exception {
        final BindClient client = request.createBindClient("localhost");
        assertNotNull(client);
    }

    @Test(dataProvider = "createModifiableInstance")
    public void testName(final BindRequest request) throws Exception {
        assertNotNull(request.getName());
    }

    @Test(dataProvider = "createModifiableInstance")
    public void testModifiableRequest(final BindRequest original) {
        final BindRequest copy = (BindRequest) copyOf(original);
        assertThat(copy.getAuthenticationType()).isEqualTo(original.getAuthenticationType());
        assertThat(copy.getName()).isEqualTo(original.getName());
    }

    @Test(dataProvider = "createModifiableInstance")
    public void testUnmodifiableRequest(final BindRequest original) {
        final BindRequest unmodifiable = (BindRequest) unmodifiableOf(original);
        assertThat(unmodifiable.getAuthenticationType()).isEqualTo(original.getAuthenticationType());
        assertThat(unmodifiable.getName()).isEqualTo(original.getName());
    }
}
