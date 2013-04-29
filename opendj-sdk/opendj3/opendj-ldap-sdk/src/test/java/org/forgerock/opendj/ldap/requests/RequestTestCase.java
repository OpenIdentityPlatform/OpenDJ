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
import static org.testng.Assert.assertTrue;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.TestCaseUtils;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests the Request class.
 */
@SuppressWarnings("javadoc")
public abstract class RequestTestCase extends RequestsTestCase {
    // Dummy decoder which does nothing.
    private static class MyDecoder implements ControlDecoder<Control> {
        public Control decodeControl(final Control control, final DecodeOptions options)
                throws DecodeException {
            // do nothing.
            return control;
        }

        public String getOID() {
            return "1.2.3".intern();
        }
    }

    // Connection used for sending requests.
    protected Connection con;

    /**
     * Request data to be validated.
     *
     * @return An array of requests.
     * @throws Exception
     */
    @DataProvider(name = "testRequests")
    public Object[][] getTestRequests() throws Exception {
        final Request[] requestArray = createTestRequests();
        final Object[][] objectArray = new Object[requestArray.length][1];

        for (int i = 0; i < requestArray.length; i++) {
            objectArray[i][0] = requestArray[i];
        }
        return objectArray;
    }

    /**
     * Ensures that the LDAP Server is running.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @BeforeClass()
    public void startServer() throws Exception {
        TestCaseUtils.startServer();
        con = TestCaseUtils.getInternalConnection();
    }

    @Test(dataProvider = "testRequests")
    public void testControls(final Request request) throws Exception {
        // Add an arbitrary control and see if it is present.
        Control control = GenericControl.newControl("1.2.3".intern());
        request.addControl(control);
        assertTrue(request.getControls().size() > 0);
        final MyDecoder decoder = new MyDecoder();
        control = request.getControl(decoder, new DecodeOptions());
        assertNotNull(control);
    }

    /**
     * Creates the test requests.
     *
     * @return
     * @throws Exception
     */
    protected abstract Request[] createTestRequests() throws Exception;
}
