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
 *      Copyright 2013-2015 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import static org.fest.assertions.Assertions.assertThat;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests PASSWORDMODIFYEXTENDED requests.
 */
@SuppressWarnings("javadoc")
public class PasswordModifyExtendedRequestTestCase extends RequestsTestCase {
    @DataProvider(name = "passwordModifyExtendedRequests")
    private Object[][] getPasswordModifyExtendedRequests() throws Exception {
        return createModifiableInstance();
    }

    @Override
    protected PasswordModifyExtendedRequest[] newInstance() {
        return new PasswordModifyExtendedRequest[] { Requests.newPasswordModifyExtendedRequest() };
    }

    @Override
    protected Request copyOf(Request original) {
        return Requests.copyOfPasswordModifyExtendedRequest((PasswordModifyExtendedRequest) original);
    }

    @Override
    protected Request unmodifiableOf(Request original) {
        return Requests.unmodifiablePasswordModifyExtendedRequest((PasswordModifyExtendedRequest) original);
    }

    @Test(dataProvider = "passwordModifyExtendedRequests")
    public void testModifiableRequest(final PasswordModifyExtendedRequest original) {
        final String password = "password";
        final String oldPassword = "oldPassword";
        final String userIdentity = "user.0";

        final PasswordModifyExtendedRequest copy = (PasswordModifyExtendedRequest) copyOf(original);
        copy.setNewPassword(password.toCharArray());
        copy.setOldPassword(oldPassword.toCharArray());
        copy.setUserIdentity(ByteString.valueOfUtf8(userIdentity));

        assertThat(copy.getNewPassword()).isEqualTo(password.getBytes());
        assertThat(original.getNewPassword()).isNull();
        assertThat(copy.getOldPassword()).isEqualTo(oldPassword.getBytes());
        assertThat(original.getOldPassword()).isNull();
        assertThat(copy.getUserIdentityAsString()).isEqualTo(userIdentity);
        assertThat(original.getUserIdentityAsString()).isNull();
    }

    @Test(dataProvider = "passwordModifyExtendedRequests")
    public void testModifiableRequestDecode(final PasswordModifyExtendedRequest original) throws DecodeException {
        final String password = "";
        final String oldPassword = "old";
        final String userIdentity = "uid=scarter,ou=people,dc=example,dc=com";
        final GenericControl control = GenericControl.newControl("1.2.3".intern());

        final PasswordModifyExtendedRequest copy = (PasswordModifyExtendedRequest) copyOf(original);
        copy.setNewPassword(password.toCharArray());
        copy.setUserIdentity(userIdentity);
        copy.setOldPassword(oldPassword.getBytes());
        copy.addControl(control);

        try {
            PasswordModifyExtendedRequest decoded = PasswordModifyExtendedRequest.DECODER.decodeExtendedRequest(copy,
                    new DecodeOptions());
            assertThat(decoded.getNewPassword()).isEqualTo(password.getBytes());
            assertThat(decoded.getOldPassword()).isEqualTo(oldPassword.getBytes());
            assertThat(decoded.getUserIdentity()).isEqualTo(ByteString.valueOfUtf8(userIdentity));
            assertThat(decoded.getControls().contains(control)).isTrue();
        } catch (DecodeException e) {
            throw e;
        }
    }

    @Test(dataProvider = "passwordModifyExtendedRequests")
    public void testUnmodifiableRequest(final PasswordModifyExtendedRequest original) {
        final PasswordModifyExtendedRequest unmodifiable = (PasswordModifyExtendedRequest) unmodifiableOf(original);
        original.setUserIdentity("uid=scarter,ou=people,dc=example,dc=com");
        original.setOldPassword("old".getBytes());
        assertThat(unmodifiable.getOID()).isEqualTo(original.getOID());
        assertThat(unmodifiable.getUserIdentity()).isEqualTo(original.getUserIdentity());
        assertThat(unmodifiable.getUserIdentityAsString()).isEqualTo(original.getUserIdentityAsString());
        original.setNewPassword("carter".getBytes());
        assertThat(unmodifiable.getNewPassword()).isEqualTo(original.getNewPassword());
        assertThat(unmodifiable.getOldPassword()).isEqualTo(original.getOldPassword());

    }

    @Test(dataProvider = "passwordModifyExtendedRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetNewPassword(final PasswordModifyExtendedRequest original) {
        final PasswordModifyExtendedRequest unmodifiable = (PasswordModifyExtendedRequest) unmodifiableOf(original);
        unmodifiable.setNewPassword("password".toCharArray());
    }

    @Test(dataProvider = "passwordModifyExtendedRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetNewPassword2(final PasswordModifyExtendedRequest original) {
        final PasswordModifyExtendedRequest unmodifiable = (PasswordModifyExtendedRequest) unmodifiableOf(original);
        unmodifiable.setNewPassword("password".getBytes());
    }

    @Test(dataProvider = "passwordModifyExtendedRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetOldPassword(final PasswordModifyExtendedRequest original) {
        final PasswordModifyExtendedRequest unmodifiable = (PasswordModifyExtendedRequest) unmodifiableOf(original);
        unmodifiable.setOldPassword("password".toCharArray());
    }

    @Test(dataProvider = "passwordModifyExtendedRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetOldPassword2(final PasswordModifyExtendedRequest original) {
        final PasswordModifyExtendedRequest unmodifiable = (PasswordModifyExtendedRequest) unmodifiableOf(original);
        unmodifiable.setOldPassword("password".getBytes());
    }

    @Test(dataProvider = "passwordModifyExtendedRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetUserIdentity(final PasswordModifyExtendedRequest original) {
        final PasswordModifyExtendedRequest unmodifiable = (PasswordModifyExtendedRequest) unmodifiableOf(original);
        unmodifiable.setUserIdentity("uid=scarter,ou=people,dc=example,dc=com");
    }
}
