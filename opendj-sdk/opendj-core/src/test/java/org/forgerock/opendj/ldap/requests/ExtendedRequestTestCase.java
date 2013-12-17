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

import org.forgerock.opendj.ldap.responses.ExtendedResultDecoder;
import org.testng.annotations.Test;

/**
 * Tests various extended requests.
 */
@SuppressWarnings("javadoc")
public abstract class ExtendedRequestTestCase extends RequestsTestCase {

    @Test(dataProvider = "ExtendedRequests")
    public void testDecoder(final ExtendedRequest<?> request) throws Exception {
        final ExtendedResultDecoder<?> decoder = request.getResultDecoder();
        assertNotNull(decoder);
    }
}
