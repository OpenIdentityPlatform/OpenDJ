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
 *      Portions copyright 2011-2013 ForgeRock AS
 */

package org.forgerock.opendj.ldap.requests;

import static com.forgerock.opendj.util.StaticUtils.EMPTY_BYTES;
import static com.forgerock.opendj.util.StaticUtils.getBytes;
import static org.fest.assertions.Assertions.assertThat;

import org.forgerock.opendj.io.LDAP;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;


/**
 * Tests Generic Bind requests.
 */
@SuppressWarnings("javadoc")
public class GenericBindRequestTestCase extends BindRequestTestCase {

    private static final GenericBindRequest NEW_GENERIC_BIND_REQUEST = Requests.newGenericBindRequest(
            LDAP.TYPE_AUTHENTICATION_SASL, EMPTY_BYTES);
    private static final GenericBindRequest NEW_GENERIC_BIND_REQUEST2 = Requests.newGenericBindRequest(
            LDAP.TYPE_AUTHENTICATION_SIMPLE, getBytes("password"));
    private static final GenericBindRequest NEW_GENERIC_BIND_REQUEST3 = Requests.newGenericBindRequest("username",
            LDAP.TYPE_AUTHENTICATION_SIMPLE, getBytes("password"));

    @DataProvider(name = "GenericBindRequests")
    private Object[][] getGenericBindRequests() throws Exception {
        return createModifiableInstance();
    }

    @Override
    protected GenericBindRequest[] newInstance() {
        return new GenericBindRequest[] {
            NEW_GENERIC_BIND_REQUEST,
            NEW_GENERIC_BIND_REQUEST2,
            NEW_GENERIC_BIND_REQUEST3
        };
    }

    @Override
    protected Request copyOf(Request original) {
        return Requests.copyOfGenericBindRequest((GenericBindRequest) original);
    }

    @Override
    protected Request unmodifiableOf(Request original) {
        return Requests.unmodifiableGenericBindRequest((GenericBindRequest) original);
    }

    @Test(dataProvider = "GenericBindRequests")
    public void testModifiableRequest(final GenericBindRequest original) {
        final String password = "pass";
        final String newName = "uid:user.0";

        final GenericBindRequest copy = (GenericBindRequest) copyOf(original);
        copy.setAuthenticationType((byte) 0);
        copy.setAuthenticationValue(password.getBytes());
        copy.setName(newName);

        assertThat(copy.getAuthenticationType()).isEqualTo((byte) 0);
        assertThat(original.getAuthenticationType()).isNotEqualTo((byte) 0);
        assertThat(copy.getAuthenticationValue()).isEqualTo(password.getBytes());
    }

    @Test(dataProvider = "GenericBindRequests")
    public void testUnmodifiableRequest(final GenericBindRequest original) {
        final GenericBindRequest unmodifiable = (GenericBindRequest) unmodifiableOf(original);
        assertThat(unmodifiable.getAuthenticationType()).isEqualTo(original.getAuthenticationType());
        assertThat(unmodifiable.getAuthenticationValue()).isEqualTo(original.getAuthenticationValue());
    }

    @Test(dataProvider = "GenericBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetName(final GenericBindRequest original) {
        final GenericBindRequest unmodifiable = (GenericBindRequest) unmodifiableOf(original);
        unmodifiable.setName("");
    }

    @Test(dataProvider = "GenericBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetAuthenticationType(final GenericBindRequest original) {
        final GenericBindRequest unmodifiable = (GenericBindRequest) unmodifiableOf(original);
        unmodifiable.setAuthenticationType((byte) 0xA3);
    }

    @Test(dataProvider = "GenericBindRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetAuthenticationValue(final GenericBindRequest original) {
        final GenericBindRequest unmodifiable = (GenericBindRequest) unmodifiableOf(original);
        unmodifiable.setAuthenticationValue(null);
    }
}
