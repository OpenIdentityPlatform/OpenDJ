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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.Requests;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.ProxyBackendLdapAssertions;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/** End-to-end proxy compatibility test against an independently configured OpenLDAP server. */
@Test(sequential = true)
public class ProxyBackendOpenLdapIT extends DirectoryServerTestCase {
    private static final String SUFFIX = "dc=example,dc=com";
    private static final String ADMIN = "cn=admin," + SUFFIX;
    private static final String SERVICE = "uid=proxy," + SUFFIX;
    private static final String REMOTE_BASE = "ou=rewrite-remote," + SUFFIX;
    private static final String PROXY_BASE = "ou=rewrite-openldap,o=test";
    private static final String REMOTE_USER = "uid=rewrite-user," + REMOTE_BASE;

    private GenericContainer<?> openldap;
    private LDAPConnectionFactory remoteFactory;

    @BeforeClass
    public void setUp() throws Exception {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            throw new SkipException("Docker is unavailable; skipping OpenLDAP integration tests");
        }
        TestCaseUtils.startServer();
        TestCaseUtils.initializeTestBackend(true);
        openldap = new GenericContainer<>(DockerImageName.parse("vegardit/openldap:2.6.10"))
                .withEnv("LDAP_INIT_ORG_DN", SUFFIX)
                .withEnv("LDAP_INIT_ORG_NAME", "Example")
                .withEnv("LDAP_INIT_ROOT_USER_DN", ADMIN)
                .withEnv("LDAP_INIT_ROOT_USER_PW", "password")
                .withEnv("LDAP_INIT_PPOLICY_PW_MIN_LENGTH", "1")
                .withEnv("LDAP_INIT_PPOLICY_MAX_FAILURES", "0")
                .withEnv("LDAP_PPOLICY_PQCHECKER_RULE", "0|00000000")
                .withEnv("LDAP_TLS_ENABLED", "false")
                .withEnv("LDAP_LDAPS_ENABLED", "false")
                .withExposedPorts(389)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));
        openldap.start();
        remoteFactory = new LDAPConnectionFactory(openldap.getHost(), openldap.getMappedPort(389));

        final Container.ExecResult search = openldap.execInContainer("ldapsearch", "-Y", "EXTERNAL", "-H",
                "ldapi:///", "-LLL", "-b", "cn=config", "(olcSuffix=" + SUFFIX + ")", "dn");
        assertEquals(search.getExitCode(), 0, search.getStderr());
        String databaseDn = null;
        for (String line : search.getStdout().split("\\R")) {
            if (line.startsWith("dn: ")) {
                databaseDn = line.substring(4).trim();
                break;
            }
        }
        assertNotNull(databaseDn, "OpenLDAP database DN");
        configure("dn: cn=config\nchangetype: modify\nreplace: olcAuthzPolicy\nolcAuthzPolicy: to\n");
        configure("dn: " + databaseDn + "\nchangetype: modify\nadd: olcAccess\n"
                + "olcAccess: {0}to attrs=authzTo by dn.exact=\"" + SERVICE + "\" auth by * break\n"
                + "olcAccess: {1}to dn.subtree=\"" + REMOTE_BASE + "\" by dn.exact=\""
                + REMOTE_USER + "\" write by * break\n");

        try (Connection admin = remoteFactory.getConnection()) {
            admin.bind(ADMIN, "password".toCharArray());
            admin.add(Requests.newAddRequest(SERVICE)
                    .addAttribute("objectClass", "top", "person", "organizationalPerson", "inetOrgPerson", "extensibleObject")
                    .addAttribute("uid", "proxy").addAttribute("cn", "Proxy").addAttribute("sn", "Proxy")
                    .addAttribute("userPassword", "proxy-password")
                    .addAttribute("authzTo", "dn.subtree:" + REMOTE_BASE));
            admin.add(Requests.newAddRequest(REMOTE_BASE)
                    .addAttribute("objectClass", "top", "organizationalUnit").addAttribute("ou", "rewrite-remote"));
            admin.add(Requests.newAddRequest("ou=moved," + REMOTE_BASE)
                    .addAttribute("objectClass", "top", "organizationalUnit").addAttribute("ou", "moved"));
            admin.add(Requests.newAddRequest(REMOTE_USER)
                    .addAttribute("objectClass", "top", "person", "organizationalPerson", "inetOrgPerson")
                    .addAttribute("uid", "rewrite-user").addAttribute("cn", "Rewrite User").addAttribute("sn", "User")
                    .addAttribute("displayName", "Remote Value").addAttribute("manager", "uid=rewrite-manager," + REMOTE_BASE)
                    .addAttribute("userPassword", "password"));
        }

        TestCaseUtils.dsconfig("create-service-discovery-mechanism", "--mechanism-name", "rewrite-openldap",
                "--type", "static", "--set", "bind-dn:" + SERVICE, "--set", "bind-password:proxy-password",
                "--set", "primary-server:" + openldap.getHost() + ":" + openldap.getMappedPort(389),
                "--set", "use-ssl:false");
        TestCaseUtils.dsconfig("create-backend", "--type", "proxy", "--backend-name", "rewrite-openldap",
                "--set", "base-dn:" + PROXY_BASE, "--set", "remote-base-dn:" + REMOTE_BASE,
                "--set", "attribute-mapping:description=displayName", "--set", "enabled:true",
                "--set", "shard:rewrite-openldap");
        TestCaseUtils.dsconfig("create-backend", "--type", "proxy", "--backend-name", "rewrite-attributes-only",
                "--set", "base-dn:" + REMOTE_BASE, "--set", "attribute-mapping:description=displayName",
                "--set", "enabled:true", "--set", "shard:rewrite-openldap");
    }

    private void configure(String ldif) throws Exception {
        final Path file = Files.createTempFile("opendj-openldap-config-", ".ldif");
        try {
            Files.write(file, ldif.getBytes(StandardCharsets.UTF_8));
            openldap.copyFileToContainer(MountableFile.forHostPath(file), "/tmp/opendj-proxy-test-config.ldif");
            final Container.ExecResult result = openldap.execInContainer("ldapmodify", "-Y", "EXTERNAL", "-H",
                    "ldapi:///", "-f", "/tmp/opendj-proxy-test-config.ldif");
            assertEquals(result.getExitCode(), 0, result.getStdout() + result.getStderr());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rewritesAgainstOpenLdap() throws Exception {
        try (LDAPConnectionFactory proxy = new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort())) {
            ProxyBackendLdapAssertions.assertRewriteOperations(proxy, remoteFactory, PROXY_BASE, REMOTE_BASE,
                    ADMIN, "password");
        }
    }

    @Test
    public void rewritesAttributesWithoutRemoteBase() throws Exception {
        try (LDAPConnectionFactory proxy = new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort());
             Connection client = proxy.getConnection()) {
            client.bind(REMOTE_USER, "password".toCharArray());
            final Entry entry = client.searchSingleEntry(REMOTE_USER, SearchScope.BASE_OBJECT,
                    "(objectClass=*)", "description", "manager");
            assertEquals(entry.getName(), DN.valueOf(REMOTE_USER));
            assertNotNull(entry.getAttribute("description"));
            assertEquals(entry.getAttribute("displayName"), null);
            assertEquals(entry.getAttribute("manager").firstValueAsString(), "uid=rewrite-manager," + REMOTE_BASE);
        }
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        if (remoteFactory != null) {
            remoteFactory.close();
        }
        if (openldap != null) {
            openldap.stop();
        }
    }
}
