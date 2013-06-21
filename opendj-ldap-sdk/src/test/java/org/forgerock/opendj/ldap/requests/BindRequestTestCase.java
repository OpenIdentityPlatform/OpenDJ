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
 */

package org.forgerock.opendj.ldap.requests;

import static org.testng.Assert.assertNotNull;

import org.testng.annotations.Test;

import com.forgerock.opendj.ldap.LDAPConstants;

/**
 * Tests the BIND requests.
 */
@SuppressWarnings("javadoc")
public abstract class BindRequestTestCase extends RequestTestCase {
    @Test(dataProvider = "testRequests")
    public void testAuthType(final BindRequest request) throws Exception {
        final byte b = request.getAuthenticationType();
        if (!(b == LDAPConstants.TYPE_AUTHENTICATION_SASL || b == LDAPConstants.TYPE_AUTHENTICATION_SIMPLE)) {
            throw new Exception("Invalid bind type");
        }
    }

    @Test(dataProvider = "testRequests")
    public void testBindClient(final BindRequest request) throws Exception {
        final BindClient client = request.createBindClient("localhost");
        assertNotNull(client);
    }

    @Test(dataProvider = "testRequests")
    public void testName(final BindRequest request) throws Exception {
        assertNotNull(request.getName());
    }
}
