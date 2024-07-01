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
 * Portions copyright 2012 ForgeRock AS.
 */

package org.forgerock.opendj.ldap.controls;

import org.forgerock.opendj.ldap.TestCaseUtils;
import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * An abstract class that all controls unit tests should extend. A control
 * represents the classes found directly under the package
 * org.forgerock.opendj.ldap.controls.
 */

@Test(groups = { "precommit", "controls", "sdk" })
public abstract class ControlsTestCase extends ForgeRockTestCase {
    /**
     * Set up the environment for performing the tests in this suite.
     *
     * @throws Exception
     *             If the environment could not be set up.
     */
    @BeforeClass
    public void setUp() throws Exception {
        // This test suite depends on having the schema available.
        TestCaseUtils.startServer();
    }
}
