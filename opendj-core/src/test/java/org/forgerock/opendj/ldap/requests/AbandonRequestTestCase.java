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

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests Abandon requests.
 */
@SuppressWarnings("javadoc")
public class AbandonRequestTestCase extends RequestsTestCase {
    private static final AbandonRequest NEW_ABANDON_REQUEST = Requests.newAbandonRequest(-1);
    private static final AbandonRequest NEW_ABANDON_REQUEST2 = Requests.newAbandonRequest(0);
    private static final AbandonRequest NEW_ABANDON_REQUEST3 = Requests.newAbandonRequest(1);

    @DataProvider(name = "abandonRequests")
    private Object[][] getAbandonRequests() throws Exception {
        return createModifiableInstance();
    }

    @Override
    protected AbandonRequest[] newInstance() {
        return new AbandonRequest[] {
            NEW_ABANDON_REQUEST,
            NEW_ABANDON_REQUEST2,
            NEW_ABANDON_REQUEST3 };
    }

    @Override
    protected Request copyOf(final Request original) {
        return Requests.copyOfAbandonRequest((AbandonRequest) original);
    }

    @Override
    protected Request unmodifiableOf(final Request original) {
        return Requests.unmodifiableAbandonRequest((AbandonRequest) original);
    }

    @Test(dataProvider = "abandonRequests")
    public void testModifiableRequest(final AbandonRequest original) {
        final int newReqId = 9999;
        final AbandonRequest copy = (AbandonRequest) copyOf(original);
        copy.setRequestID(newReqId);
        assertThat(copy.getRequestID()).isEqualTo(newReqId);
        assertThat(original.getRequestID()).isNotEqualTo(newReqId);
    }

    @Test(dataProvider = "abandonRequests")
    public void testUnmodifiableRequest(final AbandonRequest original) {
        final AbandonRequest unmodifiable = (AbandonRequest) unmodifiableOf(original);
        assertThat(unmodifiable.getRequestID()).isEqualTo(original.getRequestID());
    }

    @Test(dataProvider = "abandonRequests", expectedExceptions = UnsupportedOperationException.class)
    public void testUnmodifiableSetters(final AbandonRequest original) {
        final AbandonRequest unmodifiable = (AbandonRequest) unmodifiableOf(original);
        unmodifiable.setRequestID(0);
    }
}
