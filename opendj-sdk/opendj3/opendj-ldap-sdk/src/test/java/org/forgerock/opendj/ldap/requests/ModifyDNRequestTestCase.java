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
 * Tests the Modify DN requests.
 */
@SuppressWarnings("javadoc")
public class ModifyDNRequestTestCase extends RequestTestCase {
    @DataProvider(name = "ModifyDNRequests")
    public Object[][] getModifyDNRequests() throws Exception {
        final ModifyDNRequest[] requests = {
                Requests.newModifyDNRequest("uid=user.100,ou=people,o=test", "uid=100.user,ou=people,o=testl"),
                Requests.newModifyDNRequest("cn=ModifyDNrequesttestcase", "cn=xyz"),
        };
        final Object[][] objArray = new Object[requests.length][1];
        for (int i = 0; i < requests.length; i++) {
            objArray[i][0] = requests[i];
        }
        return objArray;
    }

    @Override
    protected ModifyDNRequest[] createTestRequests() throws Exception {
        final Object[][] objs = getModifyDNRequests();
        final ModifyDNRequest[] ops = new ModifyDNRequest[objs.length];
        for (int i = 0; i < objs.length; i++) {
            ops[i] = (ModifyDNRequest) objs[i][0];
        }
        return ops;
    }
}
