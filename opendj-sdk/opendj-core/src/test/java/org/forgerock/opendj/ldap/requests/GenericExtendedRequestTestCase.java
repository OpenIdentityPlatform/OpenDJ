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
 *      Copyright 2013-2015 ForgeRock AS
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
 * Tests GENERICEXTENDED requests.
 */
@SuppressWarnings("javadoc")
public class GenericExtendedRequestTestCase extends RequestsTestCase {
    private static final GenericExtendedRequest NEW_GENERICEXTENDED_REQUEST = Requests
            .newGenericExtendedRequest("Generic1");
    private static final GenericExtendedRequest NEW_GENERICEXTENDED_REQUEST2 = Requests
            .newGenericExtendedRequest("Generic2");
    private static final GenericExtendedRequest NEW_GENERICEXTENDED_REQUEST3 = Requests
            .newGenericExtendedRequest("Generic3");

    @DataProvider(name = "GenericExtendedRequests")
    private Object[][] getGenericExtendedRequests() throws Exception {
        return createModifiableInstance();
    }

    @Override
    protected GenericExtendedRequest[] newInstance() {
        return new GenericExtendedRequest[] {
            NEW_GENERICEXTENDED_REQUEST,
            NEW_GENERICEXTENDED_REQUEST2,
            NEW_GENERICEXTENDED_REQUEST3 };
    }

    @Override
    protected Request copyOf(Request original) {
        return Requests.copyOfGenericExtendedRequest((GenericExtendedRequest) original);
    }

    @Override
    protected Request unmodifiableOf(Request original) {
        return Requests.unmodifiableGenericExtendedRequest((GenericExtendedRequest) original);
    }

    @Test(dataProvider = "GenericExtendedRequests")
    public void testModifiableRequest(final GenericExtendedRequest original) {
        final String newOID = "1.2.3.99";
        final String newValue = "newValue";

        final GenericExtendedRequest copy = (GenericExtendedRequest) copyOf(original);
        copy.setOID(newOID);
        copy.setValue(newValue);
        assertThat(copy.getOID()).isEqualTo(newOID);
        assertThat(original.getOID()).isNotEqualTo(newOID);
    }

    @Test(dataProvider = "GenericExtendedRequests")
    public void testUnmodifiableRequest(final GenericExtendedRequest original) {
        final GenericExtendedRequest unmodifiable = (GenericExtendedRequest) unmodifiableOf(original);
        assertThat(unmodifiable.getOID()).isEqualTo(original.getOID());
        assertThat(unmodifiable.getValue()).isEqualTo(original.getValue());
        assertThat(unmodifiable.getResultDecoder()).isEqualTo(original.getResultDecoder());
    }

    @Test(dataProvider = "GenericExtendedRequests")
    public void testModifiableRequestDecode(final GenericExtendedRequest original) throws DecodeException {
        final String oid = "1.2.3.4";
        final String value = "myValue";
        final GenericControl control = GenericControl.newControl("1.2.3".intern());

        final GenericExtendedRequest copy = (GenericExtendedRequest) copyOf(original);
        copy.setOID(oid);
        copy.setValue(value);
        copy.addControl(control);

        try {
            GenericExtendedRequest decoded = GenericExtendedRequest.DECODER.decodeExtendedRequest(copy,
                    new DecodeOptions());
            assertThat(decoded.getOID()).isEqualTo(oid);
            assertThat(decoded.getValue()).isEqualTo(ByteString.valueOfUtf8(value));
            assertThat(decoded.getControls().contains(control)).isTrue();
        } catch (DecodeException e) {
            throw e;
        }
    }
}
