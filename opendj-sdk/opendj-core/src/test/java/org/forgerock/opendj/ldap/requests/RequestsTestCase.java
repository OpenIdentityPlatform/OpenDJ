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
 *      Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.requests;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.fest.assertions.Assertions.assertThat;

import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.TestCaseUtils;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.ControlDecoder;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * An abstract class that all requests unit tests should extend. Requests represents the classes found directly under
 * the package org.forgerock.opendj.ldap.requests.
 */
@Test(groups = { "precommit", "requests", "sdk" })
public abstract class RequestsTestCase extends ForgeRockTestCase {

    private static final GenericControl NEW_CONTROL = GenericControl.newControl("1.2.3".intern());
    private static final GenericControl NEW_CONTROL2 = GenericControl.newControl("3.4.5".intern());
    private static final GenericControl NEW_CONTROL3 = GenericControl.newControl("6.7.8".intern());
    private static final GenericControl NEW_CONTROL4 = GenericControl.newControl("8.9.0".intern());

    /** Dummy decoder which does nothing. */
    private static class MyDecoder implements ControlDecoder<Control> {
        public Control decodeControl(final Control control, final DecodeOptions options) throws DecodeException {
            // do nothing.
            return control;
        }

        public String getOID() {
            return "1.2.3".intern();
        }
    }

    /**
     * Ensures that the LDAP Server is running.
     *
     * @throws Exception
     *             If an unexpected problem occurs.
     */
    @BeforeClass
    public void startServer() throws Exception {
        TestCaseUtils.startServer();
    }

    /**
     * Creates the requests.
     *
     * @return An array of requests.
     */
    protected abstract Request[] newInstance();

    /**
     * Creates a copy of the request.
     *
     * @param original
     *            The original request to copy.
     * @return A copy of the request.
     */
    protected abstract Request copyOf(final Request original);

    /**
     * Creates an unmodifiable request.
     *
     * @param original
     *            The original request.
     * @return A unmodifiable request from the original.
     */
    protected abstract Request unmodifiableOf(final Request original);

    @DataProvider(name = "createModifiableInstance")
    final Object[][] createModifiableInstance() throws Exception {
        final Request[] requestArray = newInstance();
        final Object[][] objectArray = new Object[requestArray.length][1];

        for (int i = 0; i < requestArray.length; i++) {
            objectArray[i][0] = requestArray[i];
        }
        return objectArray;
    }

    @DataProvider(name = "createCopyOfInstance")
    final Object[][] createCopyOfInstance() throws Exception {
        final Request[] requestArray = newInstance();
        final Object[][] objectArray = new Object[requestArray.length][2];

        for (int i = 0; i < requestArray.length; i++) {
            objectArray[i][0] = requestArray[i];
            objectArray[i][1] = copyOf(requestArray[i]);
        }
        return objectArray;
    }

    @DataProvider(name = "createUnmodifiableInstance")
    final Object[][] createUnmodifiableInstance() throws Exception {
        final Request[] requestArray = newInstance();
        final Object[][] objectArray = new Object[requestArray.length][2];

        for (int i = 0; i < requestArray.length; i++) {
            objectArray[i][0] = requestArray[i];
            objectArray[i][1] = unmodifiableOf(requestArray[i]);
        }
        return objectArray;
    }


    /**
     * Adds a control to a request and make sure it is present.
     *
     * @param request
     *            The request to test.
     * @throws DecodeException
     *             If the control cannot be decode.
     */
    @Test(dataProvider = "createModifiableInstance")
    public void testAddControl(final Request request) throws DecodeException {
        assertThat(request.containsControl(NEW_CONTROL.getOID())).isFalse();
        request.addControl(NEW_CONTROL);
        assertThat(request.containsControl(NEW_CONTROL.getOID())).isTrue();
        assertTrue(request.getControls().size() > 0);
        final MyDecoder decoder = new MyDecoder();
        Control control = request.getControl(decoder, new DecodeOptions());
        assertNotNull(control);
    }

    /**
     * Adds a control to the original request and make sure the unmodifiable request is affected. Adding a control to
     * the unmodifiable throws an exception.
     *
     * @param original
     *            The original request.
     * @param unmodifiable
     *            The unmodifiable 'view' request.
     */
    @Test(dataProvider = "createUnmodifiableInstance", expectedExceptions = UnsupportedOperationException.class)
    public void testAddControlUnmodifiable(final Request original, final Request unmodifiable) {

        assertThat(unmodifiable.containsControl(NEW_CONTROL2.getOID())).isFalse();
        assertThat(original.containsControl(NEW_CONTROL2.getOID())).isFalse();
        original.addControl(NEW_CONTROL2);

        // Unmodifiable is a view of the original request.
        assertThat(original.containsControl(NEW_CONTROL2.getOID())).isTrue();
        assertThat(unmodifiable.containsControl(NEW_CONTROL2.getOID())).isTrue();

        unmodifiable.addControl(NEW_CONTROL3);
    }

    /**
     * Tests adding a control to the original request. The Copy should not be affected by the modification.
     *
     * @param original
     *            The original request.
     * @param copy
     *            Copy of the original request.
     */
    @Test(dataProvider = "createCopyOfInstance")
    public void testAddControlToCopy(final Request original, final Request copy) {
        assertThat(original.containsControl(NEW_CONTROL3.getOID())).isFalse();
        assertThat(copy.containsControl(NEW_CONTROL3.getOID())).isFalse();

        original.addControl(NEW_CONTROL3);
        assertThat(original.containsControl(NEW_CONTROL3.getOID())).isTrue();
        assertTrue(original.getControls().size() > 0);
        assertThat(copy.containsControl(NEW_CONTROL3.getOID())).isFalse();

        copy.addControl(NEW_CONTROL4);
        assertThat(original.containsControl(NEW_CONTROL4.getOID())).isFalse();
        assertThat(copy.containsControl(NEW_CONTROL4.getOID())).isTrue();
    }

    /**
     * The toString function from the copy should always starts with the class name.
     * <p>
     * eg. AbandonRequest(requestID=-1...)
     *
     * @param original
     *            The original request.
     * @param copy
     *            The copy request.
     */
    @Test(dataProvider = "createCopyOfInstance")
    public void testCopyToStringShouldContainClassName(final Request original, final Request copy) {
        final String className = copy.getClass().getSimpleName().replace("Impl", "");
        assertThat(copy.toString().startsWith(className)).isTrue();
    }

}
