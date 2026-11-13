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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.discovery;

import java.time.Duration;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.Requests;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/** End-to-end user-authenticated proxy test against an independent 389 Directory Server. */
@Test(sequential = true)
public class ProxyBackend389IT extends DirectoryServerTestCase {
    private static final String REMOTE_BASE = "dc=example,dc=test";
    private static final String PROXY_BASE = "ou=ds389,o=test";
    private static final String REMOTE_USER = "uid=user1," + REMOTE_BASE;
    private static final String PROXY_USER = "uid=user1," + PROXY_BASE;
    private GenericContainer<?> directory;
    private LDAPConnectionFactory remoteFactory;
    private LDAPConnectionFactory proxyFactory;

    @BeforeClass
    public void setUp() throws Exception {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            if (Boolean.parseBoolean(System.getenv("CI"))) {
                throw new AssertionError("Docker is required for the 389 Directory Server proxy integration tests in CI");
            }
            throw new SkipException("Docker is unavailable; skipping 389 Directory Server integration tests");
        }
        TestCaseUtils.startServer();
        TestCaseUtils.initializeTestBackend(true);
        directory = new GenericContainer<>(DockerImageName.parse("389ds/dirsrv:3.1"))
                .withEnv("DS_DM_PASSWORD", "password")
                .withExposedPorts(3389)
                .waitingFor(Wait.forLogMessage(".*389-ds-container started\\..*\\n", 1)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        directory.start();
        final Container.ExecResult created = directory.execInContainer("dsconf", "localhost", "backend", "create",
                "--be-name", "proxytest", "--suffix", REMOTE_BASE);
        assertEquals(created.getExitCode(), 0, created.getStdout() + created.getStderr());
        remoteFactory = new LDAPConnectionFactory(directory.getHost(), directory.getMappedPort(3389));
        try (Connection admin = remoteFactory.getConnection()) {
            admin.bind("cn=Directory Manager", "password".toCharArray());
            admin.add(Requests.newAddRequest(REMOTE_BASE)
                    .addAttribute("objectClass", "top", "domain").addAttribute("dc", "example")
                    .addAttribute("aci", "(targetattr=\"*\")(version 3.0; acl \"proxy test user\"; "
                            + "allow (read,search,compare,write) userdn=\"ldap:///" + REMOTE_USER + "\";)"));
            admin.add(Requests.newAddRequest(REMOTE_USER)
                    .addAttribute("objectClass", "top", "person", "organizationalPerson", "inetOrgPerson")
                    .addAttribute("uid", "user1").addAttribute("cn", "User One").addAttribute("sn", "One")
                    .addAttribute("userPassword", "user-password"));
        }
        TestCaseUtils.dsconfig("create-service-discovery-mechanism", "--mechanism-name", "user-pool-389",
                "--type", "static", "--set", "primary-server:" + directory.getHost() + ":"
                        + directory.getMappedPort(3389), "--set", "use-ssl:false");
        TestCaseUtils.dsconfig("create-backend", "--type", "proxy", "--backend-name", "user-pool-389",
                "--set", "base-dn:" + PROXY_BASE, "--set", "remote-base-dn:" + REMOTE_BASE,
                "--set", "shard:user-pool-389", "--set", "enabled:true");
        proxyFactory = new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort());
    }

    @Test
    public void forwardsOperationsUsingUserAuthenticatedConnections() throws Exception {
        try (Connection first = proxyFactory.getConnection(); Connection second = proxyFactory.getConnection();
             Connection rejected = proxyFactory.getConnection()) {
            assertEquals(first.bind(PROXY_USER, "user-password".toCharArray()).getResultCode(), ResultCode.SUCCESS);
            assertEquals(second.bind(PROXY_USER, "user-password".toCharArray()).getResultCode(), ResultCode.SUCCESS);
            try {
                rejected.bind(PROXY_USER, "wrong-password".toCharArray());
                Assert.fail("A second client must not inherit another client's successful bind");
            } catch (LdapException e) {
                assertEquals(e.getResult().getResultCode(), ResultCode.INVALID_CREDENTIALS);
            }
            first.modify(Requests.newModifyRequest(PROXY_USER)
                    .addModification(ModificationType.REPLACE, "description", "updated through proxy"));
            first.close();
            assertNotNull(second.searchSingleEntry(PROXY_USER, SearchScope.BASE_OBJECT, "(objectClass=*)", "description")
                    .getAttribute("description"));
            try (Connection admin = remoteFactory.getConnection()) {
                admin.bind("cn=Directory Manager", "password".toCharArray());
                assertEquals(admin.searchSingleEntry(REMOTE_USER, SearchScope.BASE_OBJECT, "(objectClass=*)", "description")
                        .getAttribute("description").firstValueAsString(), "updated through proxy");
            }
        }
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        if (proxyFactory != null) proxyFactory.close();
        if (remoteFactory != null) remoteFactory.close();
        if (directory != null) directory.stop();
    }
}
