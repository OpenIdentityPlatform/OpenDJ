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

import org.testng.annotations.DataProvider;

/**
 * Tests the external SASL Bind requests.
 */
@SuppressWarnings("javadoc")
public class ExternalSASLBindRequestTestCase extends BindRequestTestCase {
    @DataProvider(name = "ExternalSASLBindRequests")
    public Object[][] getExternalSASLBindRequests() throws Exception {
        final ExternalSASLBindRequest[] requests = { Requests.newExternalSASLBindRequest() };
        final Object[][] objArray = new Object[requests.length][1];
        for (int i = 0; i < requests.length; i++) {
            objArray[i][0] = requests[i];
        }
        return objArray;
    }

    @Override
    protected ExternalSASLBindRequest[] createTestRequests() throws Exception {
        final Object[][] objs = getExternalSASLBindRequests();
        final ExternalSASLBindRequest[] ops = new ExternalSASLBindRequest[objs.length];
        for (int i = 0; i < objs.length; i++) {
            ops[i] = (ExternalSASLBindRequest) objs[i][0];
        }
        return ops;
    }
}
