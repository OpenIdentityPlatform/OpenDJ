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
 * Copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.forgerock.json.resource.Requests.newDeleteRequest;
import static org.forgerock.json.resource.Requests.newReadRequest;
import static org.forgerock.json.resource.Resources.newCollection;
import static org.forgerock.json.resource.Resources.newInternalConnection;
import static org.forgerock.opendj.ldap.Connections.newInternalConnectionFactory;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.object;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.simple;

import java.io.IOException;

import org.forgerock.json.resource.Connection;
import org.forgerock.json.resource.NotFoundException;
import org.forgerock.json.resource.PreconditionFailedException;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.Resource;
import org.forgerock.json.resource.RootContext;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.MemoryBackend;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.opendj.rest2ldap.Rest2LDAP.Builder;
import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.Test;

/**
 * Tests that CREST requests are correctly mapped to LDAP.
 */
@SuppressWarnings({ "javadoc" })
@Test
public final class BasicRequestsTest extends ForgeRockTestCase {
    // FIXME: we need to test the request handler, not internal connections,
    // so that we can check that the request handler is returning everything.
    // FIXME: factor out test for re-use as common test suite (e.g. for InMemoryBackend).

    @Test
    public void testDelete() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        final Resource resource = connection.delete(c(), newDeleteRequest("/test1"));
        checkTestUser1(resource);
        try {
            connection.read(c(), newReadRequest("/test1"));
            fail("Read succeeded unexpectedly");
        } catch (final NotFoundException e) {
            // Expected.
        }
    }

    @Test
    public void testDeleteMVCCMatch() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        final Resource resource =
                connection.delete(c(), newDeleteRequest("/test1").setRevision("12345"));
        checkTestUser1(resource);
        try {
            connection.read(c(), newReadRequest("/test1"));
            fail("Read succeeded unexpectedly");
        } catch (final NotFoundException e) {
            // Expected.
        }
    }

    @Test(expectedExceptions = PreconditionFailedException.class)
    public void testDeleteMVCCNoMatch() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.delete(c(), newDeleteRequest("/test1").setRevision("12346"));
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void testDeleteNotFound() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.delete(c(), newDeleteRequest("/missing"));
    }

    @Test
    public void testRead() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Resource resource =
                newInternalConnection(handler).read(c(), newReadRequest("/test1"));
        checkTestUser1(resource);
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void testReadNotFound() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        newInternalConnection(handler).read(c(), newReadRequest("/missing"));
    }

    @Test
    public void testReadSelectAllFields() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Resource resource =
                newInternalConnection(handler).read(c(), newReadRequest("/test1").addField("/"));
        checkTestUser1(resource);
    }

    @Test
    public void testReadSelectPartial() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Resource resource =
                newInternalConnection(handler).read(c(),
                        newReadRequest("/test1").addField("surname"));
        assertThat(resource.getId()).isEqualTo("test1");
        assertThat(resource.getRevision()).isEqualTo("12345");
        assertThat(resource.getContent().get("id").asString()).isNull();
        assertThat(resource.getContent().get("displayName").asString()).isNull();
        assertThat(resource.getContent().get("surname").asString()).isEqualTo("user 1");
        assertThat(resource.getContent().get("rev").asString()).isNull();
    }

    // Disabled - see CREST-86 (Should JSON resource fields be case insensitive?)
    @Test(enabled = false)
    public void testReadSelectPartialInsensitive() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Resource resource =
                newInternalConnection(handler).read(c(),
                        newReadRequest("/test1").addField("SURNAME"));
        assertThat(resource.getId()).isEqualTo("test1");
        assertThat(resource.getRevision()).isEqualTo("12345");
        assertThat(resource.getContent().get("id").asString()).isNull();
        assertThat(resource.getContent().get("displayName").asString()).isNull();
        assertThat(resource.getContent().get("surname").asString()).isEqualTo("user 1");
        assertThat(resource.getContent().get("rev").asString()).isNull();
    }

    private Builder builder() throws IOException {
        return Rest2LDAP.builder().ldapConnectionFactory(getConnectionFactory()).baseDN("dc=test")
                .useEtagAttribute().useClientDNNaming("uid").readOnUpdatePolicy(
                        ReadOnUpdatePolicy.CONTROLS).authorizationPolicy(AuthorizationPolicy.NONE)
                .additionalLDAPAttribute("objectClass", "top", "person").mapper(
                        object().attribute("id", simple("uid").isSingleValued().isRequired())
                                .attribute("displayName",
                                        simple("cn").isSingleValued().isRequired()).attribute(
                                        "surname", simple("sn").isSingleValued().isRequired())
                                .attribute("rev", simple("etag").isSingleValued().isRequired()));
    }

    private RootContext c() {
        return new RootContext();
    }

    private void checkTestUser1(final Resource resource) {
        assertThat(resource.getId()).isEqualTo("test1");
        assertThat(resource.getRevision()).isEqualTo("12345");
        assertThat(resource.getContent().get("id").asString()).isEqualTo("test1");
        assertThat(resource.getContent().get("displayName").asString()).isEqualTo("test user 1");
        assertThat(resource.getContent().get("surname").asString()).isEqualTo("user 1");
        assertThat(resource.getContent().get("rev").asString()).isEqualTo("12345");
    }

    private ConnectionFactory getConnectionFactory() throws IOException {
        // @formatter:off
        final MemoryBackend backend =
                new MemoryBackend(new LDIFEntryReader(
                        "dn: dc=test",
                        "objectClass: domain",
                        "objectClass: top",
                        "dc: com",
                        "",
                        "dn: uid=test1,dc=test",
                        "objectClass: top",
                        "objectClass: person",
                        "uid: test1",
                        "userpassword: password",
                        "cn: test user 1",
                        "sn: user 1",
                        "etag: 12345",
                        "",
                        "dn: uid=test2,dc=test",
                        "objectClass: top",
                        "objectClass: person",
                        "uid: test2",
                        "userpassword: password",
                        "cn: test user 2",
                        "sn: user 2",
                        "etag: 67890"
                ));
        // @formatter:on

        return newInternalConnectionFactory(backend);
    }
}
