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
 * Copyright 2013-2016 ForgeRock AS.
 * Portions Copyright 2017 Rosie Applications, Inc.
 */
package org.forgerock.opendj.rest2ldap;

import static java.util.Arrays.asList;
import static org.fest.assertions.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.json.resource.PatchOperation.add;
import static org.forgerock.json.resource.PatchOperation.increment;
import static org.forgerock.json.resource.PatchOperation.remove;
import static org.forgerock.json.resource.PatchOperation.replace;
import static org.forgerock.json.resource.Requests.*;
import static org.forgerock.json.resource.Resources.newInternalConnection;
import static org.forgerock.opendj.ldap.Connections.newInternalConnectionFactory;
import static org.forgerock.opendj.ldap.Functions.byteStringToInteger;
import static org.forgerock.opendj.rest2ldap.Rest2Ldap.collectionOf;
import static org.forgerock.opendj.rest2ldap.Rest2Ldap.constant;
import static org.forgerock.opendj.rest2ldap.Rest2Ldap.rest2Ldap;
import static org.forgerock.opendj.rest2ldap.Rest2Ldap.object;
import static org.forgerock.opendj.rest2ldap.Rest2Ldap.resource;
import static org.forgerock.opendj.rest2ldap.Rest2Ldap.simple;
import static org.forgerock.opendj.rest2ldap.TestUtils.asResource;
import static org.forgerock.opendj.rest2ldap.TestUtils.content;
import static org.forgerock.opendj.rest2ldap.TestUtils.ctx;
import static org.forgerock.opendj.rest2ldap.WritabilityPolicy.CREATE_ONLY;
import static org.forgerock.opendj.rest2ldap.WritabilityPolicy.READ_ONLY;
import static org.forgerock.util.Options.defaultOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.Connection;
import org.forgerock.json.resource.NotFoundException;
import org.forgerock.json.resource.NotSupportedException;
import org.forgerock.json.resource.PreconditionFailedException;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.IntermediateResponseHandler;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.MemoryBackend;
import org.forgerock.opendj.ldap.RequestContext;
import org.forgerock.opendj.ldap.RequestHandler;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.BindRequest;
import org.forgerock.opendj.ldap.requests.CompareRequest;
import org.forgerock.opendj.ldap.requests.DeleteRequest;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;
import org.forgerock.opendj.ldap.requests.ModifyDNRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Request;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.BindResult;
import org.forgerock.opendj.ldap.responses.CompareResult;
import org.forgerock.opendj.ldap.responses.ExtendedResult;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.services.context.Context;
import org.forgerock.testng.ForgeRockTestCase;
import org.forgerock.util.query.QueryFilter;
import org.testng.annotations.Test;

/** Tests that CREST requests are correctly mapped to LDAP. */
@SuppressWarnings({ "javadoc" })
@Test
public final class BasicRequestsTest extends ForgeRockTestCase {
    // FIXME: we need to test the request handler, not internal connections,
    // so that we can check that the request handler is returning everything.
    // FIXME: factor out test for re-use as common test suite (e.g. for InMemoryBackend).

    private static final QueryFilter<JsonPointer> NO_FILTER = QueryFilter.alwaysTrue();

    @Test
    public void testQueryAllWithNoSubtreeFlatteningAndNoSearchFilter() throws Exception {
        final Connection connection = newConnection();
        final List<ResourceResponse> resources = new LinkedList<>();
        final QueryResponse result =
            connection.query(
                newAuthConnectionContext(),
                newQueryRequest("").setQueryFilter(NO_FILTER),
                resources);

        assertThat(resources).hasSize(7);
        assertThat(result.getPagedResultsCookie()).isNull();
        assertThat(result.getTotalPagedResults()).isEqualTo(-1);

        checkThatOrgUnitsExist(resources, "level1");

        checkThatUsersExist(resources, 1,
          "test1",
          "test2",
          "test3",
          "test4",
          "test5",
          "test6"
        );
    }

    @Test
    public void testQueryAllWithSearchFilterAndNoSubtreeFlattening() throws Exception {
        final Connection connection = newConnection();
        final List<ResourceResponse> resources = new LinkedList<>();
        final QueryResponse result =
            connection.query(
                newAuthConnectionContext(),
                newQueryRequest("top-level-users").setQueryFilter(NO_FILTER),
                resources);

        assertThat(resources).hasSize(6);
        assertThat(result.getPagedResultsCookie()).isNull();
        assertThat(result.getTotalPagedResults()).isEqualTo(-1);

        checkThatUsersExist(resources,
          "test1",
          "test2",
          "test3",
          "test4",
          "test5",
          "test6"
        );
    }

    @Test
    public void testQueryAllWithSubtreeFlatteningAndNoSearchFilter() throws Exception {
        final Connection connection = newConnection();
        final List<ResourceResponse> resources = new LinkedList<>();
        final QueryResponse result =
            connection.query(
                newAuthConnectionContext(),
                newQueryRequest("all-entries").setQueryFilter(NO_FILTER),
                resources);

        assertThat(resources).hasSize(10);
        assertThat(result.getPagedResultsCookie()).isNull();
        assertThat(result.getTotalPagedResults()).isEqualTo(-1);

        checkThatOrgUnitsExist(resources,
          "level1",
          "level2"
        );

        checkThatUsersExist(resources, 2,
          "sub2",
          "sub1",
          "test1",
          "test2",
          "test3",
          "test4",
          "test5",
          "test6"
        );
    }

    @Test
    public void testQueryAllWithSubtreeFlatteningAndSearchFilter() throws Exception {
        final Connection connection = newConnection();
        final List<ResourceResponse> resources = new LinkedList<>();
        final QueryResponse result =
            connection.query(
                newAuthConnectionContext(),
                newQueryRequest("all-users").setQueryFilter(NO_FILTER),
                resources);

        assertThat(resources).hasSize(8);
        assertThat(result.getPagedResultsCookie()).isNull();
        assertThat(result.getTotalPagedResults()).isEqualTo(-1);

        checkThatUsersExist(resources,
          "sub2",
          "sub1",
          "test1",
          "test2",
          "test3",
          "test4",
          "test5",
          "test6"
        );
    }

    @Test
    public void testQueryNoneWithNoSubtreeFlatteningAndNoSearchFilter() throws Exception {
        final Connection connection = newConnection();
        final List<ResourceResponse> resources = new LinkedList<>();
        final QueryResponse result =
            connection.query(
                newAuthConnectionContext(),
                newQueryRequest("").setQueryFilter(QueryFilter.<JsonPointer> alwaysFalse()),
                resources);

        assertThat(resources).hasSize(0);
        assertThat(result.getPagedResultsCookie()).isNull();
        assertThat(result.getTotalPagedResults()).isEqualTo(-1);
    }

    @Test
    public void testQueryNoneWithSearchFilterAndNoSubtreeFlattening() throws Exception {
        final Connection connection = newConnection();
        final List<ResourceResponse> resources = new LinkedList<>();
        final QueryResponse result =
            connection.query(
                newAuthConnectionContext(),
                newQueryRequest("top-level-users")
                    .setQueryFilter(QueryFilter.<JsonPointer> alwaysFalse()),
                resources);

        assertThat(resources).hasSize(0);
        assertThat(result.getPagedResultsCookie()).isNull();
        assertThat(result.getTotalPagedResults()).isEqualTo(-1);
    }

    @Test
    public void testQueryNoneWithSubtreeFlatteningAndNoSearchFilter() throws Exception {
        final Connection connection = newConnection();
        final List<ResourceResponse> resources = new LinkedList<>();
        final QueryResponse result =
            connection.query(
                newAuthConnectionContext(),
                newQueryRequest("all-entries")
                    .setQueryFilter(QueryFilter.<JsonPointer> alwaysFalse()),
                resources);

        assertThat(resources).hasSize(0);
        assertThat(result.getPagedResultsCookie()).isNull();
        assertThat(result.getTotalPagedResults()).isEqualTo(-1);
    }

    @Test
    public void testQueryNoneWithSubtreeFlatteningAndSearchFilter() throws Exception {
        final Connection connection = newConnection();
        final List<ResourceResponse> resources = new LinkedList<>();
        final QueryResponse result = connection.query(newAuthConnectionContext(),
          newQueryRequest("all-users").setQueryFilter(QueryFilter.<JsonPointer> alwaysFalse()), resources);

        assertThat(resources).hasSize(0);
        assertThat(result.getPagedResultsCookie()).isNull();
        assertThat(result.getTotalPagedResults()).isEqualTo(-1);
    }

    @Test
    public void testQueryPageResultsCookieWithNoSubtreeFlatteningAndNoSearchFilter()
    throws Exception {
        final Connection connection = newConnection();
        final List<ResourceResponse> resources = new ArrayList<>();

        // Read first page.
        QueryResponse result =
            connection.query(
                newAuthConnectionContext(),
                newQueryRequest("")
                    .setQueryFilter(NO_FILTER)
                    .setPageSize(3),
                resources);

        assertThat(result.getPagedResultsCookie()).isNotNull();
        assertThat(resources).hasSize(3);

        checkThatOrgUnitsExist(resources, "level1");

        checkThatUsersExist(resources, 1,
          "test1",
          "test2");

        String cookie = result.getPagedResultsCookie();

        resources.clear();

        // Read second page.
        result =
            connection.query(
                newAuthConnectionContext(),
                newQueryRequest("")
                    .setQueryFilter(NO_FILTER)
                    .setPageSize(3)
                    .setPagedResultsCookie(cookie),
                resources);

        assertThat(result.getPagedResultsCookie()).isNotNull();
        assertThat(resources).hasSize(3);

        checkThatUsersExist(resources,
            "test3",
            "test4",
            "test5");

        cookie = result.getPagedResultsCookie();

        resources.clear();

        // Read third page.
        result =
            connection.query(
                newAuthConnectionContext(),
                newQueryRequest("")
                    .setQueryFilter(NO_FILTER)
                    .setPageSize(3)
                    .setPagedResultsCookie(cookie),
                resources);

        assertThat(result.getPagedResultsCookie()).isNull();
        assertThat(resources).hasSize(1);

        checkThatUsersExist(resources,
            "test6");
    }

    @Test
    public void testQueryPageResultsCookieWithSubtreeFlatteningAndSearchFilter() throws Exception {
        final Connection connection = newConnection();
        final List<ResourceResponse> resources = new ArrayList<>();

        // Read first page.
        QueryResponse result =
            connection.query(
                newAuthConnectionContext(),
                newQueryRequest("all-users")
                    .setQueryFilter(NO_FILTER)
                    .setPageSize(5),
                resources);

        assertThat(result.getPagedResultsCookie()).isNotNull();
        assertThat(resources).hasSize(5);

        checkThatUsersExist(resources,
            "sub2",
            "sub1",
            "test1",
            "test2",
            "test3");

        String cookie = result.getPagedResultsCookie();

        resources.clear();

        // Read second page.
        result =
            connection.query(
                newAuthConnectionContext(),
                newQueryRequest("all-users")
                    .setQueryFilter(NO_FILTER)
                    .setPageSize(5)
                    .setPagedResultsCookie(cookie),
                resources);

        assertThat(result.getPagedResultsCookie()).isNull();
        assertThat(resources).hasSize(3);

        checkThatUsersExist(resources,
            "test4",
            "test5",
            "test6");

        resources.clear();
    }

    @Test
    public void testQueryPageResultsIndexedWithNoSubtreeFlatteningAndNoSearchFilter()
    throws Exception {
        final Connection connection = newConnection();
        final List<ResourceResponse> resources = new ArrayList<>();

        QueryResponse result =
            connection.query(
                newAuthConnectionContext(),
                newQueryRequest("")
                    .setQueryFilter(NO_FILTER)
                    .setPageSize(2)
                    .setPagedResultsOffset(1),
                resources);

        assertThat(result.getPagedResultsCookie()).isNotNull();
        assertThat(resources).hasSize(2);

        checkThatUsersExist(resources,
            "test2",
            "test3");
    }

    @Test
    public void testQueryPageResultsIndexedWithSubtreeFlatteningAndSearchFilter() throws Exception {
        final Connection connection = newConnection();
        final List<ResourceResponse> resources = new ArrayList<>();

        QueryResponse result =
            connection.query(
                newAuthConnectionContext(),
                newQueryRequest("all-users")
                    .setQueryFilter(NO_FILTER)
                    .setPageSize(3)
                    .setPagedResultsOffset(1),
                resources);

        assertThat(result.getPagedResultsCookie()).isNotNull();
        assertThat(resources).hasSize(3);

        checkThatUsersExist(resources,
            "test2",
            "test3",
            "test4");
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void testDelete() throws Exception {
        final Context context = newAuthConnectionContext();
        final Connection connection = newConnection();
        final ResourceResponse resource = connection.delete(context, newDeleteRequest("/test1"));

        checkResourcesAreEqual(resource, getTestUser1(12345));
        connection.read(context, newReadRequest("/test1"));
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void testDeleteMVCCMatch() throws Exception {
        final Context context = newAuthConnectionContext();
        final Connection connection = newConnection();
        final ResourceResponse resource =
            connection.delete(context, newDeleteRequest("/test1").setRevision("12345"));

        checkResourcesAreEqual(resource, getTestUser1(12345));
        connection.read(context, newReadRequest("/test1"));
    }

    @Test(expectedExceptions = PreconditionFailedException.class)
    public void testDeleteMVCCNoMatch() throws Exception {
        final Context context = newAuthConnectionContext();
        final Connection connection = newConnection();

        connection.delete(context, newDeleteRequest("/test1").setRevision("12346"));
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void testDeleteNotFound() throws Exception {
        final Context context = newAuthConnectionContext();
        final Connection connection = newConnection();

        connection.delete(context, newDeleteRequest("/missing"));
    }

    @Test
    public void testPatch() throws Exception {
        final Context context = newAuthConnectionContext();
        final Connection connection = newConnection();
        final ResourceResponse resource1 = connection.patch(context,
                newPatchRequest("/test1", add("/name/displayName", "changed")));
        checkResourcesAreEqual(resource1, getTestUser1Updated(12345));

        final ResourceResponse resource2 = connection.read(context, newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, getTestUser1Updated(12345));
    }

    @Test
    public void testPatchEmpty() throws Exception {
        final List<Request> requests = new LinkedList<>();
        final Context context = newAuthConnectionContext(requests);
        final Connection connection = newConnection();
        final ResourceResponse resource1 = connection.patch(context, newPatchRequest("/test1"));

        checkResourcesAreEqual(resource1, getTestUser1(12345));

        /*
         * Check that no modify operation was sent (only a single search should
         * be sent in order to get the current resource).
         */
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0)).isInstanceOf(SearchRequest.class);

        final ResourceResponse resource2 = connection.read(context, newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, getTestUser1(12345));
    }

    @Test
    public void testPatchAddOptionalAttribute() throws Exception {
        final Context context = newAuthConnectionContext();
        final Connection connection = newConnection();
        final JsonValue newContent = getTestUser1(12345);

        newContent.put("description", asList("one", "two"));

        final ResourceResponse resource1 =
                connection.patch(context,
                                 newPatchRequest(
                                    "/test1",
                                    add("/description", asList("one", "two"))));
        checkResourcesAreEqual(resource1, newContent);

        final ResourceResponse resource2 = connection.read(context, newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, newContent);
    }

    @Test
    public void testPatchAddOptionalAttributeIndexAppend() throws Exception {
        final Context context = newAuthConnectionContext();
        final Connection connection = newConnection();
        final JsonValue newContent = getTestUser1(12345);

        newContent.put("description", asList("one", "two"));

        final ResourceResponse resource1 =
            connection.patch(
                context,
                newPatchRequest("/test1", add("/description/-", "one"),
                                          add("/description/-", "two")));
        checkResourcesAreEqual(resource1, newContent);

        final ResourceResponse resource2 = connection.read(context, newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, newContent);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchConstantAttribute() throws Exception {
        newConnection().patch(
            newAuthConnectionContext(),
            newPatchRequest("/test1", add("/schemas", asList("junk"))));
    }

    @Test
    public void testPatchDeleteOptionalAttribute() throws Exception {
        final Context context = newAuthConnectionContext();
        final Connection connection = newConnection();

        connection.patch(context, newPatchRequest("/test1", add("/description", asList("one", "two"))));

        final ResourceResponse resource1 = connection.patch(context, newPatchRequest("/test1", remove("/description")));
        checkResourcesAreEqual(resource1, getTestUser1(12345));

        final ResourceResponse resource2 = connection.read(context, newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, getTestUser1(12345));
    }

    @Test
    public void testPatchIncrement() throws Exception {
        final Context context = newAuthConnectionContext();
        final Connection connection = newConnection();
        final JsonValue newContent = getTestUser1(12345);

        newContent.put("singleNumber", 100);
        newContent.put("multiNumber", asList(200, 300));

        final ResourceResponse resource1 =
            connection.patch(
                context,
                newPatchRequest(
                    "/test1",
                    add("/singleNumber", 0),
                    add("/multiNumber", asList(100, 200)),
                    increment("/singleNumber", 100),
                    increment("/multiNumber", 100)));

        checkResourcesAreEqual(resource1, newContent);

        final ResourceResponse resource2 = connection.read(context, newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, newContent);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchMissingRequiredAttribute() throws Exception {
        newConnection().patch(
            newAuthConnectionContext(),
            newPatchRequest("/test1", remove("/name/surname")));
    }

    @Test
    public void testPatchModifyOptionalAttribute() throws Exception {
        final Connection connection = newConnection();
        final Context context = newAuthConnectionContext();

        connection.patch(
            context,
            newPatchRequest("/test1", add("/description", asList("one", "two"))));

        final ResourceResponse resource1 =
            connection.patch(
                context,
                newPatchRequest("/test1", add("/description", asList("three"))));

        final JsonValue newContent = getTestUser1(12345);

        newContent.put("description", asList("one", "two", "three"));
        checkResourcesAreEqual(resource1, newContent);

        final ResourceResponse resource2 = connection.read(context, newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, newContent);
    }

    @Test(expectedExceptions = NotSupportedException.class)
    public void testPatchMultiValuedAttributeIndexAppend() throws Exception {
        final Connection connection = newConnection();

        connection.patch(
            newAuthConnectionContext(),
            newPatchRequest("/test1", add("/description/0", "junk")));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchMultiValuedAttributeIndexAppendWithList() throws Exception {
        final Connection connection = newConnection();

        connection.patch(
            newAuthConnectionContext(),
            newPatchRequest("/test1", add("/description/-", asList("one", "two"))));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchMultiValuedAttributeWithSingleValue() throws Exception {
        final Connection connection = newConnection();

        connection.patch(
            newAuthConnectionContext(),
            newPatchRequest("/test1", add("/description", "one")));
    }

    @Test
    public void testPatchMVCCMatch() throws Exception {
        final Connection connection = newConnection();
        final Context context = newAuthConnectionContext();
        final ResourceResponse resource1 =
            connection.patch(
                context,
                newPatchRequest(
                    "/test1", add("/name/displayName", "changed")).setRevision("12345"));

        checkResourcesAreEqual(resource1, getTestUser1Updated(12345));

        final ResourceResponse resource2 = connection.read(context, newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, getTestUser1Updated(12345));
    }

    @Test(expectedExceptions = PreconditionFailedException.class)
    public void testPatchMVCCNoMatch() throws Exception {
        final Connection connection = newConnection();

        connection.patch(
            newAuthConnectionContext(),
            newPatchRequest(
                "/test1",
                add("/name/displayName", "changed")).setRevision("12346"));
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void testPatchNotFound() throws Exception {
        newConnection().patch(
            newAuthConnectionContext(),
            newPatchRequest("/missing", add("/name/displayName", "changed")));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchReadOnlyAttribute() throws Exception {
        // Etag is read-only.
        newConnection().patch(
            newAuthConnectionContext(),
            newPatchRequest("/test1", add("_rev", "99999")));
    }

    @Test
    public void testPatchReplacePartialObject() throws Exception {
        final Connection connection = newConnection();
        final Context context = newAuthConnectionContext();
        final JsonValue expected = json(object(
            field("schemas", asList("urn:scim:schemas:core:1.0")),
            field("_id", "test1"),
            field("_rev", "12345"),
            field("name", object(field("displayName", "Humpty"),
                                 field("surname", "Dumpty")))));

        final ResourceResponse resource1 =
            connection.patch(
                context,
                newPatchRequest("/test1",
                replace("/name", object(field("displayName", "Humpty"),
                                        field("surname",     "Dumpty")))));
        checkResourcesAreEqual(resource1, expected);

        final ResourceResponse resource2 = connection.read(context, newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, expected);
    }

    @Test
    public void testPatchReplaceWholeObject() throws Exception {
        final Connection connection = newConnection();
        final Context context = newAuthConnectionContext();
        final JsonValue newContent =
            json(object(
                field("name", object(field("displayName", "Humpty"),
                                     field("surname",     "Dumpty")))));

        final JsonValue expected =
            json(object(
                field("schemas", asList("urn:scim:schemas:core:1.0")),
                field("_id", "test1"),
                field("_rev", "12345"),
                field("name", object(field("displayName", "Humpty"),
                                     field("surname",     "Dumpty")))));

        final ResourceResponse resource1 =
            connection.patch(context, newPatchRequest("/test1", replace("/", newContent)));
        checkResourcesAreEqual(resource1, expected);

        final ResourceResponse resource2 = connection.read(context, newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, expected);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchSingleValuedAttributeIndexAppend() throws Exception {
        final Connection connection = newConnection();

        connection.patch(
            newAuthConnectionContext(),
            newPatchRequest("/test1", add("/name/surname/-", "junk")));
    }

    @Test(expectedExceptions = NotSupportedException.class)
    public void testPatchSingleValuedAttributeIndexNumber() throws Exception {
        final Connection connection = newConnection();

        connection.patch(
            newAuthConnectionContext(),
            newPatchRequest("/test1", add("/name/surname/0", "junk")));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchSingleValuedAttributeWithMultipleValues() throws Exception {
        final Connection connection = newConnection();

        connection.patch(
            newAuthConnectionContext(),
            newPatchRequest(
                "/test1",
                add("/name/surname", asList("black", "white"))));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchUnknownAttribute() throws Exception {
        final Connection connection = newConnection();

        connection.patch(
            newAuthConnectionContext(),
            newPatchRequest("/test1", add("/dummy", "junk")));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchUnknownSubAttribute() throws Exception {
        final Connection connection = newConnection();

        connection.patch(
            newAuthConnectionContext(),
            newPatchRequest("/test1", add("/description/dummy", "junk")));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testPatchUnknownSubSubAttribute() throws Exception {
        final Connection connection = newConnection();
        connection.patch(newAuthConnectionContext(),
                         newPatchRequest("/test1", add("/description/dummy/dummy", "junk")));
    }

    @Test
    public void testRead() throws Exception {
        final ResourceResponse resource =
            newConnection().read(newAuthConnectionContext(), newReadRequest("/test1"));

        checkResourcesAreEqual(resource, getTestUser1(12345));
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void testReadNotFound() throws Exception {
        newConnection().read(newAuthConnectionContext(), newReadRequest("/missing"));
    }

    @Test
    public void testReadSelectAllFields() throws Exception {
        final ResourceResponse resource =
            newConnection().read(
                newAuthConnectionContext(), newReadRequest("/test1").addField("/"));

        checkResourcesAreEqual(resource, getTestUser1(12345));
    }

    @Test
    public void testReadSelectPartial() throws Exception {
        final ResourceResponse resource =
            newConnection().read(
                newAuthConnectionContext(), newReadRequest("/test1").addField("/name/surname"));

        assertThat(resource.getId()).isEqualTo("test1");
        assertThat(resource.getRevision()).isEqualTo("12345");
        assertThat(resource.getContent().get("_id").asString()).isNull();
        assertThat(resource.getContent().get("name").asMap()).isNull();
        assertThat(resource.getContent().get("surname").asString()).isEqualTo("user 1");
        assertThat(resource.getContent().get("_rev").asString()).isNull();
    }

    /** Disabled - see CREST-86 (Should JSON resource fields be case insensitive?) */
    @Test(enabled = false)
    public void testReadSelectPartialInsensitive() throws Exception {
        final ResourceResponse resource =
            newConnection().read(
                newAuthConnectionContext(), newReadRequest("/test1").addField("/name/SURNAME"));

        assertThat(resource.getId()).isEqualTo("test1");
        assertThat(resource.getRevision()).isEqualTo("12345");
        assertThat(resource.getContent().get("_id").asString()).isNull();
        assertThat(resource.getContent().get("/name/displayName").asString()).isNull();
        assertThat(resource.getContent().get("/name/surname").asString()).isEqualTo("user 1");
        assertThat(resource.getContent().get("_rev").asString()).isNull();
    }

    @Test
    public void testUpdate() throws Exception {
        final Connection connection = newConnection();
        final Context context = newAuthConnectionContext();
        final ResourceResponse resource1 = connection.update(
            context, newUpdateRequest("/test1", getTestUser1Updated(12345)));
        checkResourcesAreEqual(resource1, getTestUser1Updated(12345));

        final ResourceResponse resource2 = connection.read(context, newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, getTestUser1Updated(12345));
    }

    @Test
    public void testUpdateNoChange() throws Exception {
        final List<Request> requests = new LinkedList<>();
        final Connection connection = newConnection();
        final Context context = newAuthConnectionContext(requests);
        final ResourceResponse resource1 =
            connection.update(context, newUpdateRequest("/test1", getTestUser1(12345)));

        // Check that no modify operation was sent
        // (only a single search should be sent in order to get the current resource).
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0)).isInstanceOf(SearchRequest.class);
        checkResourcesAreEqual(resource1, getTestUser1(12345));

        final ResourceResponse resource2 = connection.read(context, newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, getTestUser1(12345));
    }

    @Test
    public void testUpdateAddOptionalAttribute() throws Exception {
        final Connection connection = newConnection();
        final Context context = newAuthConnectionContext();
        final JsonValue newContent = getTestUser1Updated(12345);

        newContent.put("description", asList("one", "two"));

        final ResourceResponse resource1 =
            connection.update(context, newUpdateRequest("/test1", newContent));
        checkResourcesAreEqual(resource1, newContent);

        final ResourceResponse resource2 = connection.read(context, newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, newContent);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testUpdateConstantAttribute() throws Exception {
        final Connection connection = newConnection();
        final JsonValue newContent = getTestUser1Updated(12345);

        newContent.put("schemas", asList("junk"));
        connection.update(newAuthConnectionContext(), newUpdateRequest("/test1", newContent));
    }

    @Test
    public void testUpdateDeleteOptionalAttribute() throws Exception {
        final Connection connection = newConnection();
        final Context context = newAuthConnectionContext();
        final JsonValue newContent = getTestUser1Updated(12345);

        newContent.put("description", asList("one", "two"));
        connection.update(newAuthConnectionContext(), newUpdateRequest("/test1", newContent));
        newContent.remove("description");

        final ResourceResponse resource1 =
          connection.update(context, newUpdateRequest("/test1", newContent));
        checkResourcesAreEqual(resource1, newContent);

        final ResourceResponse resource2 = connection.read(context, newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, newContent);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testUpdateMissingRequiredAttribute() throws Exception {
        final Connection connection = newConnection();
        final JsonValue newContent = getTestUser1Updated(12345);

        newContent.get("name").remove("surname");
        connection.update(newAuthConnectionContext(), newUpdateRequest("/test1", newContent));
    }

    @Test
    public void testUpdateModifyOptionalAttribute() throws Exception {
        final Connection connection = newConnection();
        final Context context = newAuthConnectionContext();
        final JsonValue newContent = getTestUser1Updated(12345);

        newContent.put("description", asList("one", "two"));
        connection.update(newAuthConnectionContext(), newUpdateRequest("/test1", newContent));
        newContent.put("description", asList("three"));

        final ResourceResponse resource1 =
            connection.update(context, newUpdateRequest("/test1", newContent));
        checkResourcesAreEqual(resource1, newContent);

        final ResourceResponse resource2 = connection.read(context, newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, newContent);
    }

    @Test
    public void testUpdateMVCCMatch() throws Exception {
        final Connection connection = newConnection();
        final Context context = newAuthConnectionContext();
        final ResourceResponse resource1 =
            connection.update(
                context,
                newUpdateRequest("/test1", getTestUser1Updated(12345)).setRevision("12345"));
        checkResourcesAreEqual(resource1, getTestUser1Updated(12345));

        final ResourceResponse resource2 = connection.read(context, newReadRequest("/test1"));
        checkResourcesAreEqual(resource2, getTestUser1Updated(12345));
    }

    @Test(expectedExceptions = PreconditionFailedException.class)
    public void testUpdateMVCCNoMatch() throws Exception {
        final Connection connection = newConnection();

        connection.update(
            newAuthConnectionContext(),
            newUpdateRequest("/test1", getTestUser1Updated(12345)).setRevision("12346"));
    }

    @Test(expectedExceptions = NotFoundException.class)
    public void testUpdateNotFound() throws Exception {
        final Connection connection = newConnection();

        connection.update(
            newAuthConnectionContext(),
            newUpdateRequest("/missing", getTestUser1Updated(12345)));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testUpdateReadOnlyAttribute() throws Exception {
        final Connection connection = newConnection();

        // Etag is read-only.
        connection.update(
            newAuthConnectionContext(),
            newUpdateRequest("/test1", getTestUser1Updated(99999)));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testUpdateSingleValuedAttributeWithMultipleValues() throws Exception {
        final Connection connection = newConnection();
        final JsonValue newContent = getTestUser1Updated(12345);

        newContent.put("surname", asList("black", "white"));
        connection.update(newAuthConnectionContext(), newUpdateRequest("/test1", newContent));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void testUpdateUnknownAttribute() throws Exception {
        final Connection connection = newConnection();
        final JsonValue newContent = getTestUser1Updated(12345);

        newContent.add("dummy", "junk");
        connection.update(newAuthConnectionContext(), newUpdateRequest("/test1", newContent));
    }

    private Connection newConnection() throws IOException {
        return newInternalConnection(usersApi().newRequestHandlerFor("api"));
    }

    private Rest2Ldap usersApi() throws IOException {
        return rest2Ldap(
            defaultOptions(),
            resource("api")
                .subResource(
                    collectionOf("user")
                        .dnTemplate("dc=test")
                        .useClientDnNaming("uid"))
                .subResource(
                    collectionOf("user")
                        .urlTemplate("top-level-users")
                        .dnTemplate("dc=test")
                        .useClientDnNaming("uid")
                        .baseSearchFilter("(objectClass=person)"))
                .subResource(
                    collectionOf("user")
                        .urlTemplate("all-entries")
                        .dnTemplate("dc=test")
                        .useClientDnNaming("uid")
                        .isReadOnly(true)
                        .flattenSubtree(true))
                .subResource(
                    collectionOf("user")
                        .urlTemplate("all-users")
                        .dnTemplate("dc=test")
                        .useClientDnNaming("uid")
                        .isReadOnly(true)
                        .flattenSubtree(true)
                        .baseSearchFilter("(objectClass=person)")),
            resource("user")
                .objectClasses("top", "person")
                .property(
                    "schemas",
                    constant(asList("urn:scim:schemas:core:1.0")))
                .property(
                    "_id",
                    simple("uid").isRequired(true).writability(CREATE_ONLY))
                .property(
                    "_ou",
                    simple("ou").isRequired(false).writability(CREATE_ONLY))
                .property(
                    "name",
                    object()
                      .property("displayName", simple("cn").isRequired(true))
                      .property("surname",     simple("sn").isRequired(true)))
                .property(
                    "_rev",
                    simple("etag").isRequired(true).writability(READ_ONLY))
                .property(
                    "description",
                    simple("description").isMultiValued(true))
                .property(
                    "singleNumber",
                    simple("singleNumber").decoder(byteStringToInteger()))
                .property(
                    "multiNumber",
                    simple("multiNumber").isMultiValued(true).decoder(byteStringToInteger()))
        );
    }

    private void checkResourcesAreEqual(final ResourceResponse actual, final JsonValue expected) {
        final ResourceResponse expectedResource = asResource(expected);

        assertThat(actual.getId()).isEqualTo(expectedResource.getId());
        assertThat(actual.getRevision()).isEqualTo(expectedResource.getRevision());

        assertThat(actual.getContent().getObject())
            .isEqualTo(expectedResource.getContent().getObject());
    }

    private void checkThatOrgUnitsExist(final List<ResourceResponse> resources,
                                        final String... orgUnitIds) {
        checkThatOrgUnitsExist(resources, 0, orgUnitIds);
    }

    private void checkThatOrgUnitsExist(final List<ResourceResponse> resources,
                                        final int startingIndex,
                                        final String... expectedOrgUnitIds) {
        for (int orgUnitIndex = 0; orgUnitIndex < expectedOrgUnitIds.length; ++orgUnitIndex) {
            final ResourceResponse resource = resources.get(startingIndex + orgUnitIndex);
            final JsonValue orgUnitId = resource.getContent().get("_ou");

            assertThat(orgUnitId).isNotNull();
            assertThat(orgUnitId.asString()).isEqualTo(expectedOrgUnitIds[orgUnitIndex]);
        }
    }

    private void checkThatUsersExist(final List<ResourceResponse> resources,
                                     final String... expectedUserIds) {
        checkThatUsersExist(resources, 0, expectedUserIds);
    }

    private void checkThatUsersExist(final List<ResourceResponse> resources,
                                     final int startingIndex, final String... expectedUserIds) {
        for (int userIndex = 0; userIndex < expectedUserIds.length; ++userIndex) {
            final ResourceResponse resource = resources.get(startingIndex + userIndex);

            assertThat(resource.getContent().get("_ou").isNull());
            assertThat(resource.getId()).isEqualTo(expectedUserIds[userIndex]);
        }
    }

    private AuthenticatedConnectionContext newAuthConnectionContext() throws IOException {
        return newAuthConnectionContext(new ArrayList<Request>());
    }

    private AuthenticatedConnectionContext newAuthConnectionContext(List<Request> requests)
    throws IOException {
        return new AuthenticatedConnectionContext(
            ctx(),
            getConnectionFactory(requests).getConnection());
    }

    private ConnectionFactory getConnectionFactory(final List<Request> requests)
    throws IOException {
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
                        "etag: 67890",
                        "",
                        "dn: uid=test3,dc=test",
                        "objectClass: top",
                        "objectClass: person",
                        "uid: test3",
                        "userpassword: password",
                        "cn: test user 3",
                        "sn: user 3",
                        "etag: 33333",
                        "",
                        "dn: uid=test4,dc=test",
                        "objectClass: top",
                        "objectClass: person",
                        "uid: test4",
                        "userpassword: password",
                        "cn: test user 4",
                        "sn: user 4",
                        "etag: 44444",
                        "",
                        "dn: uid=test5,dc=test",
                        "objectClass: top",
                        "objectClass: person",
                        "uid: test5",
                        "userpassword: password",
                        "cn: test user 5",
                        "sn: user 5",
                        "etag: 55555",
                        "",
                        "dn: uid=test6,dc=test",
                        "objectClass: top",
                        "objectClass: person",
                        "uid: test6",
                        "userpassword: password",
                        "cn: test user 6",
                        "sn: user 6",
                        "etag: 66666",
                        "",
                        "dn: ou=level1,dc=test",
                        "objectClass: top",
                        "objectClass: organizationalUnit",
                        "ou: level1",
                        "etag: 77777",
                        "",
                        "dn: uid=sub1,ou=level1,dc=test",
                        "objectClass: top",
                        "objectClass: person",
                        "uid: sub1",
                        "userpassword: password",
                        "cn: test user level 1",
                        "sn: user 7",
                        "etag: 88888",
                        "",
                        "dn: ou=level2,ou=level1,dc=test",
                        "objectClass: top",
                        "objectClass: organizationalUnit",
                        "ou: level2",
                        "etag: 99999",
                        "",
                        "dn: uid=sub2,ou=level2,ou=level1,dc=test",
                        "objectClass: top",
                        "objectClass: person",
                        "uid: sub2",
                        "userpassword: password",
                        "cn: test user level 2",
                        "sn: user 8",
                        "etag: 86753"
                ));
        // @formatter:on

        return newInternalConnectionFactory(recordRequests(backend, requests));
    }

    private RequestHandler<RequestContext> recordRequests(
            final RequestHandler<RequestContext> handler, final List<Request> requests) {
        return new RequestHandler<RequestContext>() {
            @Override
            public void handleAdd(RequestContext requestContext, AddRequest request,
                    IntermediateResponseHandler intermediateResponseHandler,
                    LdapResultHandler<Result> resultHandler) {
                requests.add(request);
                handler.handleAdd(requestContext, request, intermediateResponseHandler,
                        resultHandler);
            }

            @Override
            public void handleBind(RequestContext requestContext, int version, BindRequest request,
                    IntermediateResponseHandler intermediateResponseHandler,
                    LdapResultHandler<BindResult> resultHandler) {
                requests.add(request);
                handler.handleBind(requestContext, version, request, intermediateResponseHandler,
                        resultHandler);
            }

            @Override
            public void handleCompare(RequestContext requestContext, CompareRequest request,
                    IntermediateResponseHandler intermediateResponseHandler,
                    LdapResultHandler<CompareResult> resultHandler) {
                requests.add(request);
                handler.handleCompare(requestContext, request, intermediateResponseHandler,
                        resultHandler);
            }

            @Override
            public void handleDelete(RequestContext requestContext, DeleteRequest request,
                    IntermediateResponseHandler intermediateResponseHandler,
                    LdapResultHandler<Result> resultHandler) {
                requests.add(request);
                handler.handleDelete(requestContext, request, intermediateResponseHandler,
                        resultHandler);
            }

            @Override
            public <R extends ExtendedResult> void handleExtendedRequest(
                    RequestContext requestContext, ExtendedRequest<R> request,
                    IntermediateResponseHandler intermediateResponseHandler,
                    LdapResultHandler<R> resultHandler) {
                requests.add(request);
                handler.handleExtendedRequest(requestContext, request, intermediateResponseHandler,
                        resultHandler);
            }

            @Override
            public void handleModify(RequestContext requestContext, ModifyRequest request,
                    IntermediateResponseHandler intermediateResponseHandler,
                    LdapResultHandler<Result> resultHandler) {
                requests.add(request);
                handler.handleModify(requestContext, request, intermediateResponseHandler,
                        resultHandler);
            }

            @Override
            public void handleModifyDN(RequestContext requestContext, ModifyDNRequest request,
                    IntermediateResponseHandler intermediateResponseHandler,
                    LdapResultHandler<Result> resultHandler) {
                requests.add(request);
                handler.handleModifyDN(requestContext, request, intermediateResponseHandler,
                        resultHandler);
            }

            @Override
            public void handleSearch(RequestContext requestContext, SearchRequest request,
                IntermediateResponseHandler intermediateResponseHandler,
                SearchResultHandler entryHandler,
                LdapResultHandler<Result> resultHandler) {
                requests.add(request);
                handler.handleSearch(
                    requestContext,
                    request,
                    intermediateResponseHandler,
                    entryHandler,
                    resultHandler);
            }

        };
    }

    private JsonValue getTestUser1(final int rev) {
        return content(object(
                field("schemas", asList("urn:scim:schemas:core:1.0")),
                field("_id", "test1"),
                field("_rev", String.valueOf(rev)),
                field("name", object(field("displayName", "test user 1"),
                                     field("surname", "user 1")))));
    }

    private JsonValue getTestUser1Updated(final int rev) {
        return content(object(
                field("schemas", asList("urn:scim:schemas:core:1.0")),
                field("_id", "test1"),
                field("_rev", String.valueOf(rev)),
                field("name", object(field("displayName", "changed"),
                                     field("surname", "user 1")))));
    }
}
