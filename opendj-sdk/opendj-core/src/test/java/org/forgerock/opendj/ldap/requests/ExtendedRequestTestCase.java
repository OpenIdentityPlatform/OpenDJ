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
 * Copyright 2010 Sun Microsystems, Inc.
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
