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
 * Tests the unbind requests.
 */
@SuppressWarnings("javadoc")
public class UnbindRequestTestCase extends RequestsTestCase {
    private static final UnbindRequest NEW_UNBIND_REQUEST = Requests.newUnbindRequest();

    @DataProvider(name = "UnbindRequests")
    private Object[][] getUnbindRequests() throws Exception {
        return createModifiableInstance();
    }

    @Override
    protected UnbindRequest[] newInstance() {
        return new UnbindRequest[] { NEW_UNBIND_REQUEST };
    }

    @Override
    protected Request copyOf(Request original) {
        return Requests.copyOfUnbindRequest((UnbindRequest) original);
    }

    @Override
    protected Request unmodifiableOf(Request original) {
        return Requests.unmodifiableUnbindRequest((UnbindRequest) original);
    }

    @Test
    public void testModifiableRequest() {
        final UnbindRequest copy = (UnbindRequest) copyOf(Requests.newUnbindRequest());
        assertThat(copy.toString()).isEqualTo("UnbindRequest(controls=[])");
    }
}
