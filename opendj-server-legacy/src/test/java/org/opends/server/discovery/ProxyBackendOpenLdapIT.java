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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.SimplePagedResultsControl;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
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
import static org.testng.Assert.assertTrue;

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
            if (Boolean.parseBoolean(System.getenv("CI"))) {
                throw new AssertionError("Docker is required for the OpenLDAP proxy integration tests in CI");
            }
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
    public void pagesThroughMappedEntries() throws Exception {
        try (Connection admin = remoteFactory.getConnection();
             LDAPConnectionFactory proxy = new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort());
             Connection client = proxy.getConnection()) {
            admin.bind(ADMIN, "password".toCharArray());
            final String first = "uid=rewrite-page-one," + REMOTE_BASE;
            final String second = "uid=rewrite-page-two," + REMOTE_BASE;
            admin.add(Requests.newAddRequest(first).addAttribute("objectClass", "top", "person", "organizationalPerson", "inetOrgPerson")
                    .addAttribute("uid", "rewrite-page-one").addAttribute("cn", "Page One").addAttribute("sn", "One")
                    .addAttribute("displayName", "Page One"));
            try {
                admin.add(Requests.newAddRequest(second).addAttribute("objectClass", "top", "person", "organizationalPerson", "inetOrgPerson")
                        .addAttribute("uid", "rewrite-page-two").addAttribute("cn", "Page Two").addAttribute("sn", "Two")
                        .addAttribute("displayName", "Page Two"));
                try {
                    client.bind("uid=rewrite-user," + PROXY_BASE, "password".toCharArray());
                    ByteString cookie = ByteString.empty();
                    final Set<DN> seen = new HashSet<>();
                    for (int page = 0; page < 3; page++) {
                        final SearchRequest request = Requests.newSearchRequest(PROXY_BASE, SearchScope.WHOLE_SUBTREE,
                                "(uid=rewrite-page-*)", "description");
                        request.addControl(SimplePagedResultsControl.newControl(true, 1, cookie));
                        final List<SearchResultEntry> entries = new ArrayList<>();
                        final Result result = client.search(request, entries);
                        assertTrue(entries.size() <= 1);
                        for (SearchResultEntry entry : entries) {
                            assertTrue(seen.add(entry.getName()), "A paged result was returned twice");
                            assertNotNull(entry.getAttribute("description"));
                            assertEquals(entry.getAttribute("displayName"), null);
                        }
                        final SimplePagedResultsControl response = result.getControl(SimplePagedResultsControl.DECODER,
                                new DecodeOptions());
                        assertNotNull(response);
                        cookie = response.getCookie();
                        if (page == 0) {
                            assertTrue(!cookie.isEmpty(), "The first page must contain a continuation cookie");
                        }
                        if (cookie.isEmpty()) {
                            break;
                        }
                    }
                    assertTrue(cookie.isEmpty(), "Paging must terminate");
                    final Set<DN> expected = new HashSet<>();
                    expected.add(DN.valueOf("uid=rewrite-page-one," + PROXY_BASE));
                    expected.add(DN.valueOf("uid=rewrite-page-two," + PROXY_BASE));
                    assertEquals(seen, expected);
                } finally {
                    admin.delete(second);
                }
            } finally {
                admin.delete(first);
            }
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
