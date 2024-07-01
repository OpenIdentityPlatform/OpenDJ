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
 * Copyright 2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.ldap.tools.ToolLdapServer.DIRECTORY_MANAGER;
import static org.fest.assertions.Assertions.assertThat;
import static com.forgerock.opendj.ldap.tools.ToolsTestUtils.buildArgs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.forgerock.opendj.ldap.tools.ToolsTestUtils.Args;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.controls.Control;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@SuppressWarnings("javadocs")
@Test
abstract class LDAPToolsTestCase extends ToolsTestCase {

    List<Control> controls;
    ToolLdapServer server;

    @BeforeClass
    void setUp() throws IOException {
        server = createFakeServer();
        server.start();
    }

    @AfterClass
    void tearDown() throws IOException {
        server.stop();
    }

    @BeforeMethod
    void initializeTest() {
        controls = new ArrayList<>();
    }

    void assertThatControlsHaveBeenSentInRequest(final List<Control> requestControls) {
        if (!controls.isEmpty()) {
            int i = 0;
            assertThat(requestControls.size()).isEqualTo(controls.size());
            for (final Control requestControl : requestControls) {
                final Control clientControl = controls.get(i++);
                assertThat(requestControl.getOID()).isEqualTo(clientControl.getOID());
                assertThat(requestControl.isCritical()).isEqualTo(clientControl.isCritical());
                assertThat(requestControl.getValue()).isEqualTo(clientControl.getValue());
            }
        }
    }

    /**
     * Run this ldap* tool on this mocked server.
     * Use root user DN and root user password common to the tests.
     * Other constant arguments should be added by the tool implementation by overriding
     * {@link LDAPToolsTestCase#toolConstantArguments()}
     *
     * @param specificTestArgs
     *         Arguments specific for a test case.
     * @return An int which represents the code returned by the ldap* tool
     */
    int runToolOnMockedServer(final String... specificTestArgs) {
        return runToolOnMockedServer(ResultCode.SUCCESS, specificTestArgs);
    }

    int runToolOnMockedServer(final ResultCode expectedRC, final String... specificTestArgs) {
        final int res = runTool(buildArgs().add("-h", server.getHostName())
                                           .add("-p", server.getPort())
                                           .add("-D", DIRECTORY_MANAGER)
                                           .add("-w", "password")
                                           .addAll(toolConstantArguments())
                                           .addAll(specificTestArgs)
                                           .toArray());
        assertThat(res).isEqualTo(expectedRC.intValue());
        return res;
    }

    Args toolConstantArguments() {
        return buildArgs();
    }

    abstract ToolLdapServer createFakeServer();
}
