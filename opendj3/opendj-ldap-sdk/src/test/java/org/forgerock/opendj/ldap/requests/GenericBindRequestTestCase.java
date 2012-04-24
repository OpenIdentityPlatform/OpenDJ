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
 *      Portions copyright 2011 ForgeRock AS
 */

package org.forgerock.opendj.ldap.requests;

import static com.forgerock.opendj.util.StaticUtils.EMPTY_BYTES;
import static com.forgerock.opendj.util.StaticUtils.getBytes;

import org.testng.annotations.DataProvider;

import com.forgerock.opendj.ldap.LDAPConstants;

/**
 * Tests Generic Bind requests.
 */
@SuppressWarnings("javadoc")
public class GenericBindRequestTestCase extends BindRequestTestCase {
    @DataProvider(name = "GenericBindRequests")
    public Object[][] getGenericBindRequests() throws Exception {
        final GenericBindRequest[] requests = {
                Requests.newGenericBindRequest(LDAPConstants.TYPE_AUTHENTICATION_SASL, EMPTY_BYTES),
                Requests.newGenericBindRequest(LDAPConstants.TYPE_AUTHENTICATION_SIMPLE, getBytes("password")),
                Requests.newGenericBindRequest("username", LDAPConstants.TYPE_AUTHENTICATION_SIMPLE,
                        getBytes("password"))
        };
        final Object[][] objArray = new Object[requests.length][1];
        for (int i = 0; i < requests.length; i++) {
            objArray[i][0] = requests[i];
        }
        return objArray;
    }

    @Override
    protected GenericBindRequest[] createTestRequests() throws Exception {
        final Object[][] objs = getGenericBindRequests();
        final GenericBindRequest[] ops = new GenericBindRequest[objs.length];
        for (int i = 0; i < objs.length; i++) {
            ops[i] = (GenericBindRequest) objs[i][0];
        }
        return ops;
    }
}
