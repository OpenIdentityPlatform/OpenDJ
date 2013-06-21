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

import static java.util.Arrays.asList;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;
import static org.forgerock.json.fluent.JsonValue.field;
import static org.forgerock.json.fluent.JsonValue.json;
import static org.forgerock.json.fluent.JsonValue.object;
import static org.forgerock.json.resource.PatchOperation.add;
import static org.forgerock.json.resource.PatchOperation.increment;
import static org.forgerock.json.resource.PatchOperation.operation;
import static org.forgerock.json.resource.PatchOperation.remove;
import static org.forgerock.json.resource.PatchOperation.replace;
import static org.forgerock.json.resource.Requests.newDeleteRequest;
import static org.forgerock.json.resource.Requests.newPatchRequest;
import static org.forgerock.json.resource.Requests.newReadRequest;
import static org.forgerock.json.resource.Requests.newUpdateRequest;
import static org.forgerock.json.resource.Resources.newCollection;
import static org.forgerock.json.resource.Resources.newInternalConnection;
import static org.forgerock.opendj.ldap.Connections.newInternalConnectionFactory;
import static org.forgerock.opendj.ldap.Functions.byteStringToInteger;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.constant;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.object;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.simple;
import static org.forgerock.opendj.rest2ldap.TestUtils.asResource;
import static org.forgerock.opendj.rest2ldap.TestUtils.content;
import static org.forgerock.opendj.rest2ldap.TestUtils.ctx;

import java.io.IOException;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.Connection;
import org.forgerock.json.resource.NotFoundException;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.PreconditionFailedException;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.Resource;
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
        final Resource resource = connection.delete(ctx(), newDeleteRequest("/test1"));
        checkResourcesAreEqual(resource, getTestUser1(12345));
        try {
            connection.read(ctx(), newReadRequest("/test1"));
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
                connection.delete(ctx(), newDeleteRequest("/test1").setRevision("12345"));
        checkResourcesAreEqual(resource, getTestUser1(12345));
        try {
            connection.read(ctx(), newReadRequest("/test1"));
            fail("Read succeeded unexpectedly");
        } catch (final NotFoundException e) {
            // Expected.
        }
    }

    @Test(expectedExceptions = PreconditionFailedException.class)
    public void testDeleteMVCCNoMatch() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.delete(ctx(), newDeleteRequest("/test1").setRevision("12346"));
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void testDeleteNotFound() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.delete(ctx(), newDeleteRequest("/missing"));
    }

    @Test
    public void testPatch() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        final Resource resource1 =
                connection.patch(ctx(), newPatchRequest("/test1", add("/name/displayName",
                        "changed")));
        checkResourcesAreEqual(resource1, getTestUser1Updated(12345));
        final Resource resource2 = connection.read(ctx(), newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, getTestUser1Updated(12345));
    }

    @Test
    public void testPatchAddOptionalAttribute() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        final JsonValue newContent = getTestUser1(12345);
        newContent.put("description", asList("one", "two"));
        final Resource resource1 =
                connection.patch(ctx(), newPatchRequest("/test1", add("/description", asList("one",
                        "two"))));
        checkResourcesAreEqual(resource1, newContent);
        final Resource resource2 = connection.read(ctx(), newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, newContent);
    }

    @Test
    public void testPatchAddOptionalAttributeIndexAppend() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        final JsonValue newContent = getTestUser1(12345);
        newContent.put("description", asList("one", "two"));
        final Resource resource1 =
                connection.patch(ctx(), newPatchRequest("/test1", add("/description/-", "one"),
                        add("/description/-", "two")));
        checkResourcesAreEqual(resource1, newContent);
        final Resource resource2 = connection.read(ctx(), newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, newContent);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchConstantAttribute() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.patch(ctx(), newPatchRequest("/test1", add("/schemas", asList("junk"))));
    }

    @Test
    public void testPatchDeleteOptionalAttribute() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.patch(ctx(),
                newPatchRequest("/test1", add("/description", asList("one", "two"))));
        final Resource resource1 =
                connection.patch(ctx(), newPatchRequest("/test1", remove("/description")));
        checkResourcesAreEqual(resource1, getTestUser1(12345));
        final Resource resource2 = connection.read(ctx(), newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, getTestUser1(12345));
    }

    @Test
    public void testPatchIncrement() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        final JsonValue newContent = getTestUser1(12345);
        newContent.put("singleNumber", 100);
        newContent.put("multiNumber", asList(200, 300));

        final Resource resource1 =
                connection.patch(ctx(), newPatchRequest("/test1", add("/singleNumber", 0), add(
                        "/multiNumber", asList(100, 200)), increment("/singleNumber", 100),
                        increment("/multiNumber", 100)));
        checkResourcesAreEqual(resource1, newContent);
        final Resource resource2 = connection.read(ctx(), newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, newContent);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchMissingRequiredAttribute() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.patch(ctx(), newPatchRequest("/test1", remove("/name/surname")));
    }

    @Test
    public void testPatchModifyOptionalAttribute() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.patch(ctx(),
                newPatchRequest("/test1", add("/description", asList("one", "two"))));
        final Resource resource1 =
                connection.patch(ctx(), newPatchRequest("/test1", add("/description",
                        asList("three"))));
        final JsonValue newContent = getTestUser1(12345);
        newContent.put("description", asList("one", "two", "three"));
        checkResourcesAreEqual(resource1, newContent);
        final Resource resource2 = connection.read(ctx(), newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, newContent);
    }

    @Test(expectedExceptions = NotSupportedException.class)
    public void testPatchMultiValuedAttributeIndexAppend() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.patch(ctx(), newPatchRequest("/test1", add("/description/0", "junk")));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchMultiValuedAttributeIndexAppendWithList() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.patch(ctx(), newPatchRequest("/test1", add("/description/-",
                asList("one", "two"))));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchMultiValuedAttributeWithSingleValue() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.patch(ctx(), newPatchRequest("/test1", add("/description", "one")));
    }

    @Test
    public void testPatchMVCCMatch() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        final Resource resource1 =
                connection.patch(ctx(), newPatchRequest("/test1",
                        add("/name/displayName", "changed")).setRevision("12345"));
        checkResourcesAreEqual(resource1, getTestUser1Updated(12345));
        final Resource resource2 = connection.read(ctx(), newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, getTestUser1Updated(12345));
    }

    @Test(expectedExceptions = PreconditionFailedException.class)
    public void testPatchMVCCNoMatch() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.patch(ctx(), newPatchRequest("/test1", add("/name/displayName", "changed"))
                .setRevision("12346"));
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void testPatchNotFound() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.patch(ctx(), newPatchRequest("/missing", add("/name/displayName", "changed")));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchReadOnlyAttribute() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        // Etag is read-only.
        connection.patch(ctx(), newPatchRequest("/test1", add("_rev", "99999")));
    }

    @Test
    public void testPatchReplacePartialObject() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        final JsonValue expected =
                json(object(field("schemas", asList("urn:scim:schemas:core:1.0")), field("_id",
                        "test1"), field("_rev", "12345"), field("name", object(field("displayName",
                        "Humpty"), field("surname", "Dumpty")))));
        final Resource resource1 =
                connection.patch(ctx(), newPatchRequest("/test1", replace("/name", object(field(
                        "displayName", "Humpty"), field("surname", "Dumpty")))));
        checkResourcesAreEqual(resource1, expected);
        final Resource resource2 = connection.read(ctx(), newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, expected);
    }

    @Test
    public void testPatchReplaceWholeObject() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        final JsonValue newContent =
                json(object(field("name", object(field("displayName", "Humpty"), field("surname",
                        "Dumpty")))));
        final JsonValue expected =
                json(object(field("schemas", asList("urn:scim:schemas:core:1.0")), field("_id",
                        "test1"), field("_rev", "12345"), field("name", object(field("displayName",
                        "Humpty"), field("surname", "Dumpty")))));
        final Resource resource1 =
                connection.patch(ctx(), newPatchRequest("/test1", replace("/", newContent)));
        checkResourcesAreEqual(resource1, expected);
        final Resource resource2 = connection.read(ctx(), newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, expected);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchSingleValuedAttributeIndexAppend() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.patch(ctx(), newPatchRequest("/test1", add("/name/surname/-", "junk")));
    }

    @Test(expectedExceptions = NotSupportedException.class)
    public void testPatchSingleValuedAttributeIndexNumber() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.patch(ctx(), newPatchRequest("/test1", add("/name/surname/0", "junk")));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchSingleValuedAttributeWithMultipleValues() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.patch(ctx(), newPatchRequest("/test1", add("/name/surname", asList("black",
                "white"))));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchUnknownAttribute() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.patch(ctx(), newPatchRequest("/test1", add("/dummy", "junk")));
    }

    @Test(expectedExceptions = NotSupportedException.class)
    public void testPatchUnknownOperation() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.patch(ctx(), newPatchRequest("/test1", operation("dummy", "/description",
                asList("one", "two"))));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchUnknownSubAttribute() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.patch(ctx(), newPatchRequest("/test1", add("/description/dummy", "junk")));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchUnknownSubSubAttribute() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.patch(ctx(), newPatchRequest("/test1", add("/description/dummy/dummy", "junk")));
    }

    @Test
    public void testRead() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Resource resource =
                newInternalConnection(handler).read(ctx(), newReadRequest("/test1"));
        checkResourcesAreEqual(resource, getTestUser1(12345));
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void testReadNotFound() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        newInternalConnection(handler).read(ctx(), newReadRequest("/missing"));
    }

    @Test
    public void testReadSelectAllFields() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Resource resource =
                newInternalConnection(handler).read(ctx(), newReadRequest("/test1").addField("/"));
        checkResourcesAreEqual(resource, getTestUser1(12345));
    }

    @Test
    public void testReadSelectPartial() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Resource resource =
                newInternalConnection(handler).read(ctx(),
                        newReadRequest("/test1").addField("/name/surname"));
        assertThat(resource.getId()).isEqualTo("test1");
        assertThat(resource.getRevision()).isEqualTo("12345");
        assertThat(resource.getContent().get("_id").asString()).isNull();
        assertThat(resource.getContent().get("name").asMap()).isNull();
        assertThat(resource.getContent().get("surname").asString()).isEqualTo("user 1");
        assertThat(resource.getContent().get("_rev").asString()).isNull();
    }

    // Disabled - see CREST-86 (Should JSON resource fields be case insensitive?)
    @Test(enabled = false)
    public void testReadSelectPartialInsensitive() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Resource resource =
                newInternalConnection(handler).read(ctx(),
                        newReadRequest("/test1").addField("/name/SURNAME"));
        assertThat(resource.getId()).isEqualTo("test1");
        assertThat(resource.getRevision()).isEqualTo("12345");
        assertThat(resource.getContent().get("_id").asString()).isNull();
        assertThat(resource.getContent().get("/name/displayName").asString()).isNull();
        assertThat(resource.getContent().get("/name/surname").asString()).isEqualTo("user 1");
        assertThat(resource.getContent().get("_rev").asString()).isNull();
    }

    @Test
    public void testUpdate() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        final Resource resource1 =
                connection.update(ctx(), newUpdateRequest("/test1", getTestUser1Updated(12345)));
        checkResourcesAreEqual(resource1, getTestUser1Updated(12345));
        final Resource resource2 = connection.read(ctx(), newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, getTestUser1Updated(12345));
    }

    @Test
    public void testUpdateAddOptionalAttribute() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        final JsonValue newContent = getTestUser1Updated(12345);
        newContent.put("description", asList("one", "two"));
        final Resource resource1 = connection.update(ctx(), newUpdateRequest("/test1", newContent));
        checkResourcesAreEqual(resource1, newContent);
        final Resource resource2 = connection.read(ctx(), newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, newContent);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testUpdateConstantAttribute() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        final JsonValue newContent = getTestUser1Updated(12345);
        newContent.put("schemas", asList("junk"));
        connection.update(ctx(), newUpdateRequest("/test1", newContent));
    }

    @Test
    public void testUpdateDeleteOptionalAttribute() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        final JsonValue newContent = getTestUser1Updated(12345);
        newContent.put("description", asList("one", "two"));
        connection.update(ctx(), newUpdateRequest("/test1", newContent));
        newContent.remove("description");
        final Resource resource1 = connection.update(ctx(), newUpdateRequest("/test1", newContent));
        checkResourcesAreEqual(resource1, newContent);
        final Resource resource2 = connection.read(ctx(), newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, newContent);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testUpdateMissingRequiredAttribute() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        final JsonValue newContent = getTestUser1Updated(12345);
        newContent.get("name").remove("surname");
        connection.update(ctx(), newUpdateRequest("/test1", newContent));
    }

    @Test
    public void testUpdateModifyOptionalAttribute() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        final JsonValue newContent = getTestUser1Updated(12345);
        newContent.put("description", asList("one", "two"));
        connection.update(ctx(), newUpdateRequest("/test1", newContent));
        newContent.put("description", asList("three"));
        final Resource resource1 = connection.update(ctx(), newUpdateRequest("/test1", newContent));
        checkResourcesAreEqual(resource1, newContent);
        final Resource resource2 = connection.read(ctx(), newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, newContent);
    }

    @Test
    public void testUpdateMVCCMatch() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        final Resource resource1 =
                connection.update(ctx(), newUpdateRequest("/test1", getTestUser1Updated(12345))
                        .setRevision("12345"));
        checkResourcesAreEqual(resource1, getTestUser1Updated(12345));
        final Resource resource2 = connection.read(ctx(), newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, getTestUser1Updated(12345));
    }

    @Test(expectedExceptions = PreconditionFailedException.class)
    public void testUpdateMVCCNoMatch() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.update(ctx(), newUpdateRequest("/test1", getTestUser1Updated(12345))
                .setRevision("12346"));
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void testUpdateNotFound() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        connection.update(ctx(), newUpdateRequest("/missing", getTestUser1Updated(12345)));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testUpdateReadOnlyAttribute() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        // Etag is read-only.
        connection.update(ctx(), newUpdateRequest("/test1", getTestUser1Updated(99999)));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testUpdateSingleValuedAttributeWithMultipleValues() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        final JsonValue newContent = getTestUser1Updated(12345);
        newContent.put("surname", asList("black", "white"));
        connection.update(ctx(), newUpdateRequest("/test1", newContent));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testUpdateUnknownAttribute() throws Exception {
        final RequestHandler handler = newCollection(builder().build());
        final Connection connection = newInternalConnection(handler);
        final JsonValue newContent = getTestUser1Updated(12345);
        newContent.add("dummy", "junk");
        connection.update(ctx(), newUpdateRequest("/test1", newContent));
    }

    private Builder builder() throws IOException {
        return Rest2LDAP.builder().ldapConnectionFactory(getConnectionFactory()).baseDN("dc=test")
                .useEtagAttribute().useClientDNNaming("uid").readOnUpdatePolicy(
                        ReadOnUpdatePolicy.CONTROLS).authorizationPolicy(AuthorizationPolicy.NONE)
                .additionalLDAPAttribute("objectClass", "top", "person")
                .mapper(object()
                        .attribute("schemas", constant(asList("urn:scim:schemas:core:1.0")))
                        .attribute(
                                "_id",
                                simple("uid").isSingleValued().isRequired().writability(
                                        WritabilityPolicy.CREATE_ONLY)).attribute(
                                "name",
                                object().attribute("displayName",
                                        simple("cn").isSingleValued().isRequired()).attribute(
                                        "surname", simple("sn").isSingleValued().isRequired()))
                        .attribute(
                                "_rev",
                                simple("etag").isSingleValued().isRequired().writability(
                                        WritabilityPolicy.READ_ONLY)).attribute("description",
                                simple("description")).attribute(
                                "singleNumber",
                                simple("singleNumber").decoder(byteStringToInteger())
                                        .isSingleValued()).attribute("multiNumber",
                                simple("multiNumber").decoder(byteStringToInteger())));
    }

    private void checkResourcesAreEqual(final Resource actual, final JsonValue expected) {
        final Resource expectedResource = asResource(expected);
        assertThat(actual.getId()).isEqualTo(expectedResource.getId());
        assertThat(actual.getRevision()).isEqualTo(expectedResource.getRevision());
        assertThat(actual.getContent().getObject()).isEqualTo(
                expectedResource.getContent().getObject());
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

    private JsonValue getTestUser1(final int rev) {
        return content(object(field("schemas", asList("urn:scim:schemas:core:1.0")), field("_id",
                "test1"), field("_rev", String.valueOf(rev)), field("name", object(field(
                "displayName", "test user 1"), field("surname", "user 1")))));
    }

    private JsonValue getTestUser1Updated(final int rev) {
        return content(object(field("schemas", asList("urn:scim:schemas:core:1.0")), field("_id",
                "test1"), field("_rev", String.valueOf(rev)), field("name", object(field(
                "displayName", "changed"), field("surname", "user 1")))));
    }
}
