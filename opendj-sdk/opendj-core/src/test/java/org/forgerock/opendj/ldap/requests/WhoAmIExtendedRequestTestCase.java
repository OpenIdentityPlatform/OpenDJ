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
 * Tests WHOAMIEXTENDED requests.
 */
@SuppressWarnings("javadoc")
public class WhoAmIExtendedRequestTestCase extends RequestsTestCase {
    private static final WhoAmIExtendedRequest NEW_WHOAMIEXTENDED_REQUEST = Requests
            .newWhoAmIExtendedRequest();

    @DataProvider(name = "whoAmIExtendedRequests")
    private Object[][] getWhoAmIExtendedRequests() throws Exception {
        return createModifiableInstance();
    }

    @Override
    protected WhoAmIExtendedRequest[] newInstance() {
        return new WhoAmIExtendedRequest[] {
            NEW_WHOAMIEXTENDED_REQUEST
        };
    }

    @Override
    protected Request copyOf(Request original) {
        return Requests.copyOfWhoAmIExtendedRequest((WhoAmIExtendedRequest) original);
    }

    @Override
    protected Request unmodifiableOf(Request original) {
        return Requests.unmodifiableWhoAmIExtendedRequest((WhoAmIExtendedRequest) original);
    }

    @Test(dataProvider = "whoAmIExtendedRequests")
    public void testModifiableRequest(final WhoAmIExtendedRequest original) {
        final WhoAmIExtendedRequest copy = (WhoAmIExtendedRequest) copyOf(original);
        assertThat(copy.getOID()).isEqualTo(original.getOID());
        assertThat(copy.getResultDecoder()).isEqualTo(original.getResultDecoder());
        assertThat(copy.getValue()).isEqualTo(original.getValue());
    }

    @Test(dataProvider = "whoAmIExtendedRequests")
    public void testUnmodifiableRequest(final WhoAmIExtendedRequest original) {
        final WhoAmIExtendedRequest unmodifiable = (WhoAmIExtendedRequest) unmodifiableOf(original);
        assertThat(unmodifiable.getOID()).isEqualTo(original.getOID());
        assertThat(unmodifiable.getResultDecoder()).isEqualTo(original.getResultDecoder());
        assertThat(unmodifiable.getValue()).isEqualTo(original.getValue());
    }

    @Test(dataProvider = "whoAmIExtendedRequests")
    public void testUnmodifiableRequestHasResult(final WhoAmIExtendedRequest original) {
        final WhoAmIExtendedRequest unmodifiable = (WhoAmIExtendedRequest) unmodifiableOf(original);
        assertThat(unmodifiable.hasValue()).isFalse();
    }


    @Test(dataProvider = "whoAmIExtendedRequests")
    public void testModifiableRequestDecode(final WhoAmIExtendedRequest original) throws DecodeException {
        final GenericControl control = GenericControl.newControl("1.2.3".intern());

        final WhoAmIExtendedRequest copy = (WhoAmIExtendedRequest) copyOf(original);
        copy.addControl(control);
        assertThat(original.getControls().contains(control)).isFalse();

        try {
            final WhoAmIExtendedRequest decoded = WhoAmIExtendedRequest.DECODER.decodeExtendedRequest(copy,
                    new DecodeOptions());
            assertThat(decoded.getControls().contains(control)).isTrue();
        } catch (DecodeException e) {
            throw e;
        }
    }
}
