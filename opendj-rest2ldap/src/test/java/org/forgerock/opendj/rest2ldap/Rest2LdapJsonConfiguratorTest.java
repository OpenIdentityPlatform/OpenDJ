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
package org.forgerock.opendj.rest2ldap;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.http.util.Json.*;
import static org.forgerock.json.resource.Requests.*;
import static org.forgerock.json.resource.ResourcePath.*;
import static org.forgerock.opendj.rest2ldap.Rest2Ldap.*;
import static org.forgerock.util.Options.*;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.forgerock.api.CrestApiProducer;
import org.forgerock.api.models.ApiDescription;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.http.util.Json;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.Request;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.testng.ForgeRockTestCase;
import org.forgerock.util.Options;
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
    private static final Path CONFIG_DIR = Paths.get(
        "../opendj-rest2ldap-servlet/src/main/webapp/WEB-INF/classes/rest2ldap");

    @Test
    public void testConfigureEndpointsWithApiDescription() throws Exception {
        final DescribableRequestHandler handler = configureEndpoints(CONFIG_DIR.resolve("endpoints").toFile());
        final ApiDescription api = requestApi(handler, "api/users/bjensen");
        assertThat(api).isNotNull();

        // Ensure we can can pretty print and parse back the generated api description
        parseJson(prettyPrint(api));

        assertThat(api.getId()).isEqualTo(ID);
        assertThat(api.getVersion()).isEqualTo(VERSION);
        assertThat(api.getPaths().getNames()).containsOnly("/api/users", "/api/groups");
        assertThat(api.getDefinitions().getNames()).containsOnly(
            "frapi:opendj:rest2ldap:group:1.0",
            "frapi:opendj:rest2ldap:user:1.0",
            "frapi:opendj:rest2ldap:posixUser:1.0");
    }

    private DescribableRequestHandler configureEndpoints(final File endpointsDir) throws Exception {
        final RequestHandler rh = Rest2LdapJsonConfigurator.configureEndpoints(endpointsDir, Options.defaultOptions());
        DescribableRequestHandler handler = new DescribableRequestHandler(rh);
        handler.api(new CrestApiProducer(ID, VERSION));
        return handler;
    }

    private ApiDescription requestApi(final DescribableRequestHandler handler, String uriPath) {
        Context context = newRouterContext(uriPath);
        Request request = newApiRequest(resourcePath(uriPath));
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
            new ObjectMapper().registerModules(new Json.LocalizableStringModule(), new Json.JsonValueModule());
        final ObjectWriter writer = objectMapper.writer().withDefaultPrettyPrinter();
        return writer.writeValueAsString(o);
    }

    static JsonValue parseJson(final String json) throws Exception {
        try (StringReader r = new StringReader(json)) {
            return new JsonValue(readJsonLenient(r));
        }
    }
}
