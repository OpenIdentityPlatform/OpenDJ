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
 * Portions Copyright 2017 Rosie Applications, Inc.
 */
package org.forgerock.opendj.rest2ldap;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.http.util.Json.*;
import static org.forgerock.json.resource.Requests.*;
import static org.forgerock.json.resource.ResourcePath.*;
import static org.forgerock.opendj.rest2ldap.Rest2Ldap.*;
import static org.forgerock.util.Options.*;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import java.util.List;
import java.util.Map;
import org.forgerock.api.CrestApiProducer;
import org.forgerock.api.models.ApiDescription;
import org.forgerock.api.models.Items;
import org.forgerock.api.models.Resource;
import org.forgerock.api.models.Services;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.http.util.Json;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.Request;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.testng.ForgeRockTestCase;
import org.forgerock.util.Options;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * This class tests that the {@link Rest2LdapJsonConfigurator} class can successfully create its
 * model and generate its API description from the json configuration files.
 */
@Test
@SuppressWarnings("javadoc")
public class Rest2LdapJsonConfiguratorTest extends ForgeRockTestCase {
    private static final String ID = "frapi:opendj:rest2ldap";
    private static final String VERSION = "4.0.0";

    private static final Path SERVLET_MODULE_PATH =
        getPathToMavenModule("opendj-rest2ldap-servlet");

    private static final Path CONFIG_DIR = Paths.get(
        SERVLET_MODULE_PATH.toString(), "src", "main", "webapp", "WEB-INF", "classes", "rest2ldap");

    @Test
    public void testConfigureEndpointsWithApiDescription() throws Exception {
        final File endpointsDir = CONFIG_DIR.resolve("endpoints").toFile();
        final DescribableRequestHandler handler = createDescribableHandler(endpointsDir);
        final ApiDescription api = requestApi(handler, "api/users/bjensen");

        assertThat(api).isNotNull();

        // Ensure we can can pretty print and parse back the generated api description
        parseJson(prettyPrint(api));

        assertThat(api.getId()).isEqualTo(ID);
        assertThat(api.getVersion()).isEqualTo(VERSION);

        assertThat(api.getPaths().getNames()).containsOnly(
            "/api/users",
            "/api/read-only-users",
            "/api/all-users",
            "/api/groups");

        assertThat(api.getDefinitions().getNames()).containsOnly(
            "frapi:opendj:rest2ldap:object:1.0",
            "frapi:opendj:rest2ldap:group:1.0",
            "frapi:opendj:rest2ldap:user:1.0",
            "frapi:opendj:rest2ldap:posixUser:1.0");

        final Services services = api.getServices();

        assertThat(services.getNames()).containsOnly(
            "frapi:opendj:rest2ldap:user:1.0:read-write",
            "frapi:opendj:rest2ldap:user:1.0:read-only",
            "frapi:opendj:rest2ldap:group:1.0:read-write");

        final String[] readOnlyServices = new String[] {
            "frapi:opendj:rest2ldap:user:1.0:read-only"
        };

        for (String serviceName : readOnlyServices) {
            final Resource service = services.get(serviceName);
            final Items items = service.getItems();

            assertThat(service.getCreate()).isNull();
            assertThat(items.getCreate()).isNull();
            assertThat(items.getUpdate()).isNull();
            assertThat(items.getDelete()).isNull();
            assertThat(items.getPatch()).isNull();

            assertThat(items.getRead()).isNotNull();
        }

        final String[] writableServices = new String[] {
            "frapi:opendj:rest2ldap:user:1.0:read-write",
            "frapi:opendj:rest2ldap:group:1.0:read-write"
        };

        for (String serviceName : writableServices) {
            final Resource service = services.get(serviceName);
            final Items items = service.getItems();

            assertThat(service.getCreate()).isNotNull();
            assertThat(items.getCreate()).isNotNull();
            assertThat(items.getUpdate()).isNotNull();
            assertThat(items.getDelete()).isNotNull();
            assertThat(items.getPatch()).isNotNull();
            assertThat(items.getRead()).isNotNull();
        }
    }

    @DataProvider
    public Object[][] invalidSubResourceSubtreeFlatteningConfigurations() {
        // @Checkstyle:off
        return new Object[][] {
            {
                "{"
                    + "'example-v1': {"
                        + "'subResources': {"
                            + "'writeable-collection': {"
                                + "'type': 'collection',"
                                + "'dnTemplate': 'ou=people,dc=example,dc=com',"
                                + "'resource': 'frapi:opendj:rest2ldap:user:1.0',"
                                + "'namingStrategy': {"
                                    + "'type': 'clientDnNaming',"
                                    + "'dnAttribute': 'uid'"
                                + "},"
                                + "'flattenSubtree': true"
                            + "}"
                        + "}"
                    + "}"
                + "}"
            },
            {
                "{"
                    + "'example-v1': {"
                        + "'subResources': {"
                            + "'writeable-collection': {"
                                + "'type': 'collection',"
                                + "'dnTemplate': 'ou=people,dc=example,dc=com',"
                                + "'resource': 'frapi:opendj:rest2ldap:user:1.0',"
                                + "'namingStrategy': {"
                                    + "'type': 'clientDnNaming',"
                                    + "'dnAttribute': 'uid'"
                                + "},"
                                + "'isReadOnly': false,"
                                + "'flattenSubtree': true"
                            + "}"
                        + "}"
                    + "}"
                + "}"
            }
        };
        // @Checkstyle:on
    }

    @DataProvider
    public Object[][] validSubResourceConfigurations() {
        // @Checkstyle:off
        return new Object[][] {
            {
                false,
                false,
                null,
                "{"
                    + "'example-v1': {"
                        + "'subResources': {"
                            + "'all-users': {"
                                + "'type': 'collection',"
                                + "'dnTemplate': 'ou=people,dc=example,dc=com',"
                                + "'resource': 'frapi:opendj:rest2ldap:user:1.0',"
                                + "'namingStrategy': {"
                                    + "'type': 'clientDnNaming',"
                                    + "'dnAttribute': 'uid'"
                                + "}"
                            + "}"
                        + "}"
                    + "}"
                + "}"
            },
            {
                false,
                false,
                "(objectClass=person)",
                "{"
                    + "'example-v1': {"
                        + "'subResources': {"
                            + "'all-users': {"
                                + "'type': 'collection',"
                                + "'dnTemplate': 'ou=people,dc=example,dc=com',"
                                + "'resource': 'frapi:opendj:rest2ldap:user:1.0',"
                                + "'namingStrategy': {"
                                    + "'type': 'clientDnNaming',"
                                    + "'dnAttribute': 'uid'"
                                + "},"
                                + "'baseSearchFilter': '(objectClass=person)'"
                            + "}"
                        + "}"
                    + "}"
                + "}"
            },
            {
                false,
                false,
                null,
                "{"
                    + "'example-v1': {"
                        + "'subResources': {"
                            + "'all-users': {"
                                + "'type': 'collection',"
                                + "'dnTemplate': 'ou=people,dc=example,dc=com',"
                                + "'resource': 'frapi:opendj:rest2ldap:user:1.0',"
                                + "'namingStrategy': {"
                                    + "'type': 'clientDnNaming',"
                                    + "'dnAttribute': 'uid'"
                                + "},"
                                + "'flattenSubtree': false"
                            + "}"
                        + "}"
                    + "}"
                + "}"
            },
            {
                true,
                false,
                null,
                "{"
                    + "'example-v1': {"
                        + "'subResources': {"
                            + "'all-users': {"
                                + "'type': 'collection',"
                                + "'dnTemplate': 'ou=people,dc=example,dc=com',"
                                + "'resource': 'frapi:opendj:rest2ldap:user:1.0',"
                                + "'namingStrategy': {"
                                    + "'type': 'clientDnNaming',"
                                    + "'dnAttribute': 'uid'"
                                + "},"
                                + "'isReadOnly': true"
                            + "}"
                        + "}"
                    + "}"
                + "}"
            },
            {
                true,
                false,
                null,
                "{"
                    + "'example-v1': {"
                        + "'subResources': {"
                            + "'all-users': {"
                                + "'type': 'collection',"
                                + "'dnTemplate': 'ou=people,dc=example,dc=com',"
                                + "'resource': 'frapi:opendj:rest2ldap:user:1.0',"
                                + "'namingStrategy': {"
                                    + "'type': 'clientDnNaming',"
                                    + "'dnAttribute': 'uid'"
                                + "},"
                                + "'isReadOnly': true,"
                                + "'flattenSubtree': false"
                            + "}"
                        + "}"
                    + "}"
                + "}"
            },
            {
                false,
                false,
                null,
                "{"
                    + "'example-v1': {"
                        + "'subResources': {"
                            + "'all-users': {"
                                + "'type': 'collection',"
                                + "'dnTemplate': 'ou=people,dc=example,dc=com',"
                                + "'resource': 'frapi:opendj:rest2ldap:user:1.0',"
                                + "'namingStrategy': {"
                                    + "'type': 'clientDnNaming',"
                                    + "'dnAttribute': 'uid'"
                                + "},"
                                + "'isReadOnly': false,"
                                + "'flattenSubtree': false"
                            + "}"
                        + "}"
                    + "}"
                + "}"
            },
            {
                true,
                true,
                null,
                "{"
                    + "'example-v1': {"
                        + "'subResources': {"
                            + "'all-users': {"
                                + "'type': 'collection',"
                                + "'dnTemplate': 'ou=people,dc=example,dc=com',"
                                + "'resource': 'frapi:opendj:rest2ldap:user:1.0',"
                                + "'namingStrategy': {"
                                    + "'type': 'clientDnNaming',"
                                    + "'dnAttribute': 'uid'"
                                + "},"
                                + "'isReadOnly': true,"
                                + "'flattenSubtree': true"
                            + "}"
                        + "}"
                    + "}"
                + "}"
            }
        };
        // @Checkstyle:on
    }

    @Test(dataProvider = "invalidSubResourceSubtreeFlatteningConfigurations")
    public void testInvalidSubResourceSubtreeFlatteningConfigurations(final String rawJson)
    throws Exception {
        try {
            Rest2LdapJsonConfigurator.configureResources(parseJson(rawJson));

            fail("Expected an IllegalArgumentException");
        }
        catch (IllegalArgumentException ex) {
            assertThat(ex.getMessage())
                .isEqualTo("Sub-resources must be read-only to support sub-tree flattening.");
        }
    }

    @Test
    public void testInvalidSubResourceSearchFilterConfiguration()
    throws Exception {
        final String rawJson =
            "{"
                + "'example-v1': {"
                    + "'subResources': {"
                        + "'all-users': {"
                            + "'type': 'collection',"
                            + "'dnTemplate': 'ou=people,dc=example,dc=com',"
                            + "'resource': 'frapi:opendj:rest2ldap:user:1.0',"
                            + "'namingStrategy': {"
                                + "'type': 'clientDnNaming',"
                                + "'dnAttribute': 'uid'"
                            + "},"
                            + "'baseSearchFilter': 'badFilter'"
                        + "}"
                    + "}"
                + "}"
            + "}";

        try {
            Rest2LdapJsonConfigurator.configureResources(parseJson(rawJson));

            fail("Expected an IllegalArgumentException");
        }
        catch (IllegalArgumentException ex) {
            assertThat(ex.getMessage())
                .isEqualTo(
                    "The provided search filter \"badFilter\" was missing an equal sign in the " +
                    "suspected simple filter component between positions 0 and 9");
        }
    }

    @Test(dataProvider = "validSubResourceConfigurations")
    public void testValidSubResourceConfigurations(final boolean expectedReadOnly,
                                                   final boolean expectedSubtreeFlattened,
                                                   final String expectedSearchFilter,
                                                   final String rawJson) throws Exception {
        final List<org.forgerock.opendj.rest2ldap.Resource> resources =
            Rest2LdapJsonConfigurator.configureResources(parseJson(rawJson));
        final org.forgerock.opendj.rest2ldap.Resource firstResource;
        final Map<String, SubResource> subResources;
        final SubResourceCollection allUsersSubResource;

        assertThat(resources.size()).isEqualTo(1);

        firstResource = resources.get(0);

        assertThat(firstResource.getResourceId()).isEqualTo("example-v1");

        subResources = firstResource.getSubResourceMap();

        assertThat(subResources.size()).isEqualTo(1);

        allUsersSubResource = (SubResourceCollection)subResources.get("all-users");

        assertThat(allUsersSubResource.isReadOnly()).isEqualTo(expectedReadOnly);
        assertThat(allUsersSubResource.shouldFlattenSubtree()).isEqualTo(expectedSubtreeFlattened);

        if (expectedSearchFilter == null) {
            assertThat(allUsersSubResource.getBaseSearchFilter()).isNull();
        }
        else {
            assertThat(allUsersSubResource.getBaseSearchFilter().toString())
                .isEqualTo(expectedSearchFilter);
        }
    }

    private RequestHandler createRequestHandler(final File endpointsDir) throws IOException {
        return Rest2LdapJsonConfigurator.configureEndpoints(endpointsDir, Options.defaultOptions());
    }

    private DescribableRequestHandler createDescribableHandler(final File endpointsDir)
    throws Exception {
        final RequestHandler rh = createRequestHandler(endpointsDir);
        final DescribableRequestHandler handler = new DescribableRequestHandler(rh);

        handler.api(new CrestApiProducer(ID, VERSION));

        return handler;
    }

    private ApiDescription requestApi(final DescribableRequestHandler handler,
                                      final String uriPath) {
        final Context context = newRouterContext(uriPath);
        final Request request = newApiRequest(resourcePath(uriPath));

        return handler.handleApiRequest(context, request);
    }

    private Context newRouterContext(final String uriPath) {
        Context ctx = new RootContext();

        ctx = new Rest2LdapContext(ctx, rest2Ldap(defaultOptions()));
        ctx = new UriRouterContext(ctx, null, uriPath, Collections.<String, String> emptyMap());

        return ctx;
    }

    private String prettyPrint(Object o) throws Exception {
        final ObjectMapper objectMapper =
            new ObjectMapper().registerModules(
                new Json.LocalizableStringModule(),
                new Json.JsonValueModule());

        final ObjectWriter writer = objectMapper.writer().withDefaultPrettyPrinter();

        return writer.writeValueAsString(o);
    }

    private static JsonValue parseJson(final String json) throws Exception {
        try (StringReader r = new StringReader(json)) {
            return new JsonValue(readJsonLenient(r));
        }
    }

    private static Path getPathToClass(Class<?> clazz) {
        return Paths.get(clazz.getProtectionDomain().getCodeSource().getLocation().getPath());
    }

    private static Path getPathToMavenModule(String moduleName) {
        final Path testClassPath = getPathToClass(Rest2LdapJsonConfiguratorTest.class);

        return Paths.get(testClassPath.toString(), "..", "..", "..", moduleName);
    }
}
