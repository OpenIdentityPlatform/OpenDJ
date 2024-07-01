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
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import static org.assertj.core.api.Assertions.*;
import static org.forgerock.http.util.Json.*;
import static org.forgerock.json.resource.Requests.*;
import static org.forgerock.json.resource.ResourcePath.*;
import static org.forgerock.opendj.rest2ldap.Rest2Ldap.*;
import static org.forgerock.util.Options.*;

import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.Collections;

import org.forgerock.api.CrestApiProducer;
import org.forgerock.api.models.ApiDescription;
import org.forgerock.http.HttpApplication;
import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.http.util.Json;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.FilterChain;
import org.forgerock.json.resource.Request;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.http.rest2ldap.AdminEndpoint;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

@SuppressWarnings("javadoc")
public class AdminEndpointTestCase extends DirectoryServerTestCase
{
  private static final String ID = "frapi:opendj:admin";
  private static final String VERSION = "4.0.0";

  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }

  @Test
  public void testApiDescriptionGeneration() throws Exception
  {
    FilterChain endpointHandler = configureEndpoint();
    final ApiDescription api = requestApi(endpointHandler, "admin/config");
    assertThat(api).isNotNull();

    // Ensure we can can pretty print and parse back the generated api description
    parseJson(prettyPrint(api));

    assertThat(api.getId()).isEqualTo(ID + ":1.0");
    assertThat(api.getVersion()).isEqualTo(VERSION);
    assertThat(api.getPaths().getNames().size()).isGreaterThan(20);
    assertThat(api.getDefinitions().getNames().size()).isGreaterThan(150);
  }

  private FilterChain configureEndpoint() throws Exception
  {
    AdminEndpoint adminEndpoint = new AdminEndpoint(null, DirectoryServer.getInstance().getServerContext());
    HttpApplication httpApp = adminEndpoint.newHttpApplication();
    FilterChain handler = startRequestHandler(httpApp);
    handler.api(new CrestApiProducer(ID, VERSION));
    return handler;
  }

  private FilterChain startRequestHandler(HttpApplication httpApp) throws Exception
  {
    Method m = httpApp.getClass().getDeclaredMethod("startRequestHandler");
    m.setAccessible(true);
    FilterChain handler = (FilterChain) m.invoke(httpApp);
    return handler;
  }

  private ApiDescription requestApi(final FilterChain filterChain, String uriPath)
  {
    Context context = newRouterContext(uriPath);
    Request request = newApiRequest(resourcePath(uriPath));
    return filterChain.handleApiRequest(context, request);
  }

  private Context newRouterContext(final String uriPath)
  {
    Context ctx = new RootContext();
    ctx = new Rest2LdapContext(ctx, rest2Ldap(defaultOptions()));
    ctx = new UriRouterContext(ctx, null, uriPath, Collections.<String, String> emptyMap());
    return ctx;
  }

  private String prettyPrint(Object o) throws Exception
  {
    final ObjectMapper objectMapper =
        new ObjectMapper().registerModules(new Json.LocalizableStringModule(), new Json.JsonValueModule());
    final ObjectWriter writer = objectMapper.writer().withDefaultPrettyPrinter();
    return writer.writeValueAsString(o);
  }

  static JsonValue parseJson(final String json) throws Exception
  {
    try (StringReader r = new StringReader(json))
    {
      return new JsonValue(readJsonLenient(r));
    }
  }
}
