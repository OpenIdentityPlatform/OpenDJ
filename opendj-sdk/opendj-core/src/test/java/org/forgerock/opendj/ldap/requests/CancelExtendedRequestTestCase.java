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
 * Copyright 2013 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import static org.fest.assertions.Assertions.assertThat;

import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests CANCELEXTENDED requests.
 */
@SuppressWarnings("javadoc")
public class CancelExtendedRequestTestCase extends RequestsTestCase {
    private static final CancelExtendedRequest NEW_CANCELEXTENDED_REQUEST = Requests.newCancelExtendedRequest(-1);
    private static final CancelExtendedRequest NEW_CANCELEXTENDED_REQUEST2 = Requests.newCancelExtendedRequest(0);
    private static final CancelExtendedRequest NEW_CANCELEXTENDED_REQUEST3 = Requests.newCancelExtendedRequest(1);

    @DataProvider(name = "cancelExtendedRequests")
    private Object[][] getCancelExtendedRequests() throws Exception {
        return createModifiableInstance();
    }

    @Override
    protected CancelExtendedRequest[] newInstance() {
        return new CancelExtendedRequest[] {
            NEW_CANCELEXTENDED_REQUEST,
            NEW_CANCELEXTENDED_REQUEST2,
            NEW_CANCELEXTENDED_REQUEST3 };
    }

    @Override
    protected Request copyOf(Request original) {
        return Requests.copyOfCancelExtendedRequest((CancelExtendedRequest) original);
    }

    @Override
    protected Request unmodifiableOf(Request original) {
        return Requests.unmodifiableCancelExtendedRequest((CancelExtendedRequest) original);
    }


    @Test(dataProvider = "cancelExtendedRequests")
    public void testModifiableRequest(final CancelExtendedRequest original) {
        final int newReqId = 9999;
        final CancelExtendedRequest copy = (CancelExtendedRequest) copyOf(original);
        copy.setRequestID(newReqId);
        assertThat(copy.getRequestID()).isEqualTo(newReqId);
        assertThat(copy.getOID()).isEqualTo(original.getOID());
        assertThat(original.getRequestID()).isNotEqualTo(newReqId);
    }

    @Test(dataProvider = "cancelExtendedRequests")
    public void testUnmodifiableRequest(final CancelExtendedRequest original) {
        final CancelExtendedRequest unmodifiable = (CancelExtendedRequest) unmodifiableOf(original);
        assertThat(unmodifiable.getRequestID()).isEqualTo(original.getRequestID());
        assertThat(unmodifiable.getOID()).isEqualTo(original.getOID());
        assertThat(unmodifiable.getResultDecoder()).isEqualTo(original.getResultDecoder());
        assertThat(unmodifiable.getValue()).isEqualTo(original.getValue());
    }

    @Test(dataProvider = "cancelExtendedRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetRequestId(final CancelExtendedRequest original) {
        final CancelExtendedRequest unmodifiable = (CancelExtendedRequest) unmodifiableOf(original);
        unmodifiable.setRequestID(99);
    }

    @Test(dataProvider = "cancelExtendedRequests")
    public void testModifiableRequestDecode(final CancelExtendedRequest original) throws DecodeException {
        final GenericControl control = GenericControl.newControl("1.2.3".intern());

        final CancelExtendedRequest copy = (CancelExtendedRequest) copyOf(original);
        copy.setRequestID(99);
        copy.addControl(control);
        assertThat(original.getControls().contains(control)).isFalse();

        try {
            final CancelExtendedRequest decoded = CancelExtendedRequest.DECODER.decodeExtendedRequest(copy,
                    new DecodeOptions());
            assertThat(decoded.getControls().contains(control)).isTrue();
        } catch (DecodeException e) {
            throw e;
        }
    }
}
