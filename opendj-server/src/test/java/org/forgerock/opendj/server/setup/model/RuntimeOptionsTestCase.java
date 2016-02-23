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
 * Copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.server.setup.model;

import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.opendj.server.setup.model.RuntimeOptions.INITIAL_MEMORY;
import static org.forgerock.opendj.server.setup.model.RuntimeOptions.MAXIMUM_MEMORY;

import org.testng.annotations.Test;

/**
 * This class contains some tests to demonstrate the use of the runtime options in the setup.
 */
public class RuntimeOptionsTestCase  extends AbstractSetupTestCase {

    @Test
    public void testGetDefault() {
        final RuntimeOptions options = RuntimeOptions.getDefault();
        assertThat(options.getInitialMemory()).isEqualTo(INITIAL_MEMORY);
        assertThat(options.getMaximumMemory()).isEqualTo(MAXIMUM_MEMORY);
        assertThat(options.getAdditionalArguments()).contains("-client");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testRuntimeOptionsDoesNotAllowNullAdditionalArguments() {
        final RuntimeOptions options = new RuntimeOptions();
        options.setInitialMemory(INITIAL_MEMORY);
        options.setMaximumMemory(MAXIMUM_MEMORY);
        options.setAdditionalArguments((String[]) null);
    }

    @Test
    public void testEqualitySucceed() {
        final RuntimeOptions options = RuntimeOptions.getDefault();
        assertThat(options.getInitialMemory()).isEqualTo(INITIAL_MEMORY);
        assertThat(options.getMaximumMemory()).isEqualTo(MAXIMUM_MEMORY);
        assertThat(options.getAdditionalArguments()).contains("-client");

        final RuntimeOptions options2 = new RuntimeOptions();
        options2.setInitialMemory(INITIAL_MEMORY);
        options2.setMaximumMemory(MAXIMUM_MEMORY);
        options2.setAdditionalArguments(new String[] { "-client" });

        assertThat(options).isEqualTo(options2);
    }

    @Test
    public void testEqualityFails() {
        final RuntimeOptions options = RuntimeOptions.getDefault();
        assertThat(options.getInitialMemory()).isEqualTo(INITIAL_MEMORY);
        assertThat(options.getMaximumMemory()).isEqualTo(MAXIMUM_MEMORY);
        assertThat(options.getAdditionalArguments()).contains("-client");

        final RuntimeOptions options2 = new RuntimeOptions();
        options2.setInitialMemory(INITIAL_MEMORY);
        options2.setMaximumMemory(MAXIMUM_MEMORY);
        options2.setAdditionalArguments(new String[] { "" });

        assertThat(options).isNotEqualTo(options2);
    }

    @Test
    public void testRuntimeOptionsToString() {

        final RuntimeOptions options = new RuntimeOptions();
        options.setInitialMemory(INITIAL_MEMORY);
        options.setMaximumMemory(MAXIMUM_MEMORY);
        options.setAdditionalArguments(new String[] { "-client" });

        assertThat(options.toString()).contains(String.valueOf(INITIAL_MEMORY));
        assertThat(options.toString()).contains(String.valueOf(MAXIMUM_MEMORY));
        assertThat(options.toString()).contains("-client");
    }
}
