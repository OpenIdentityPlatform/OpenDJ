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
 *      Copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.server.setup.model;

import java.net.InetSocketAddress;
import java.net.ServerSocket;

import static com.forgerock.opendj.cli.CliConstants.*;
import static org.fest.assertions.Assertions.assertThat;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.forgerock.opendj.ldap.TestCaseUtils;
import org.testng.annotations.Test;

public class ListenerSettingsTestCase extends AbstractSetupTestCase {

    /**
     * Retrieves default settings.
     */
    @Test
    public void testGetDefault() {
        final ListenerSettings dsSettings = new ListenerSettings();
        assertThat(dsSettings.getAdminPort()).isEqualTo(DEFAULT_ADMIN_PORT);
        assertThat(dsSettings.getHTTPPort()).isEqualTo(DEFAULT_HTTP_PORT);
        assertThat(dsSettings.getJMXPort()).isEqualTo(DEFAULT_JMX_PORT);
        assertThat(dsSettings.getLdapPort()).isEqualTo(DEFAULT_LDAP_PORT);
        assertThat(dsSettings.getLdapsPort()).isEqualTo(DEFAULT_LDAPS_PORT);
        assertThat(dsSettings.getSNMPPort()).isEqualTo(DEFAULT_SNMP_PORT);
        assertThat(dsSettings.getSSLPortNumber()).isEqualTo(DEFAULT_SSL_PORT);

        assertThat(dsSettings.getHostName()).isEmpty();
        assertThat(dsSettings.getRootUserDN()).isEqualTo(DEFAULT_ROOT_USER_DN);
        assertThat(dsSettings.getPassword()).isNull();
        assertFalse(dsSettings.isSSLEnabled());
        assertThat(dsSettings.getCertificate()).isNull();
        assertFalse(dsSettings.isTLSEnabled());
        assertFalse(dsSettings.isJMXConnectionHandlerEnabled());
        assertFalse(dsSettings.isSNMPConnectionHandlerEnabled());
        assertTrue(dsSettings.isHTTPConnectionHandlerEnabled());
    }

    /**
     * Tries to retrieve a free port number.
     *
     * @throws Exception
     */
    @Test
    public void testGetFreePort() throws Exception {
        // Finds a free socket
        final InetSocketAddress isa = TestCaseUtils.findFreeSocketAddress();
        // Bound the free socket
        final int port = isa.getPort();
        final ServerSocket boundSocket = new ServerSocket(port);
        // Verify the new port number is different from the free socket and verify it's free.
        final int newPort = ListenerSettings.getFreeSocketPort(port);
        assertThat(newPort).isNotEqualTo(port);
        assertTrue(Math.abs(newPort - port) % PORT_INCREMENT == 0);

        boundSocket.close();
    }

    /**
     * Port number is invalid when inferior to 0 or superior to 65535.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidPort() throws Exception {
        ListenerSettings.getFreeSocketPort(65536);
    }

    /**
     * Port number is invalid when inferior to 0 or superior to 65535.
     *
     * @throws Exception
     */
    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidNegativePort() throws Exception {
        ListenerSettings.getFreeSocketPort(-1);
    }
}
