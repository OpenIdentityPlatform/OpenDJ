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
 * Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.protocols.http;

import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.*;
import static org.opends.server.protocols.http.CollectClientConnectionsFilter.*;

import java.io.IOException;

import org.assertj.core.api.SoftAssertions;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.resource.ResourceException;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.core.ServerContext;
import org.opends.server.util.Base64;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class CollectClientConnectionsFilterTest extends DirectoryServerTestCase
{
  private static final String USERNAME = "Aladdin";
  private static final String PASSWORD = "open sesame";
  private static final String BASE64_USERPASS = Base64.encode((USERNAME + ":" + PASSWORD).getBytes());

  private HTTPAuthenticationConfig authConfig;
  private CollectClientConnectionsFilter filter;

  @BeforeMethod
  private void createConfigAndFilter()
  {
    authConfig = new HTTPAuthenticationConfig();
    filter = new CollectClientConnectionsFilter(mock(ServerContext.class), null, authConfig);
  }

  @DataProvider(name = "Invalid HTTP basic auth strings")
  public Object[][] getInvalidHttpBasicAuthStrings()
  {
    return new Object[][] { { null }, { "bla" }, { "basic " + Base64.encode("la:bli:blu".getBytes()) } };
  }

  @Test(dataProvider = "Invalid HTTP basic auth strings")
  public void parseUsernamePasswordFromInvalidAuthZHeader(String authZHeader) throws Exception
  {
    assertThat(filter.parseUsernamePassword(authZHeader)).isNull();
  }

  @DataProvider(name = "Valid HTTP basic auth strings")
  public Object[][] getValidHttpBasicAuthStrings()
  {
    return new Object[][] { { "basic " + BASE64_USERPASS }, { "Basic " + BASE64_USERPASS } };
  }

  @Test(dataProvider = "Valid HTTP basic auth strings")
  public void parseUsernamePasswordFromValidAuthZHeader(String authZHeader) throws Exception
  {
    assertThat(filter.parseUsernamePassword(authZHeader)).containsExactly(USERNAME, PASSWORD);
  }

  @Test
  public void sendUnauthorizedResponseWithHttpBasicAuthWillChallengeUserAgent() throws Exception
  {
    authConfig.setBasicAuthenticationSupported(true);
    final Response response = sendUnauthorizedResponseWithHTTPBasicAuthChallenge();

    assertThat(response.getHeaders().getFirst("WWW-Authenticate")).isEqualTo("Basic realm=\"org.forgerock.opendj\"");
    verifyUnauthorizedOutputMessage(response);
  }

  @Test
  public void sendUnauthorizedResponseWithoutHttpBasicAuthWillNotChallengeUserAgent() throws Exception
  {
    authConfig.setBasicAuthenticationSupported(false);
    final Response response = sendUnauthorizedResponseWithHTTPBasicAuthChallenge();

    assertThat(response.getHeaders().getFirst("WWW-Authenticate")).isNull();
    verifyUnauthorizedOutputMessage(response);
  }

  private Response sendUnauthorizedResponseWithHTTPBasicAuthChallenge() throws Exception
  {
    return filter.resourceExceptionToPromise(ResourceException.getException(401, "Invalid Credentials")).get();
  }

  private void verifyUnauthorizedOutputMessage(Response response) throws IOException
  {
    final SoftAssertions softly = new SoftAssertions();
    softly.assertThat(response.getStatus().getCode()).isEqualTo(401);
    softly.assertThat(response.getStatus().getReasonPhrase()).isEqualTo("Unauthorized");
    softly.assertThat(response.getEntity().getJson().toString()).isEqualTo(
            "{code=401, reason=Unauthorized, message=Invalid Credentials}");
    softly.assertAll();
  }

  @Test
  public void extractUsernamePasswordHttpBasicAuthWillAcceptUserAgent() throws Exception
  {
    authConfig.setBasicAuthenticationSupported(true);

    final Request request = new Request();
    request.getHeaders().add(HTTP_BASIC_AUTH_HEADER, "Basic " + BASE64_USERPASS);
    assertThat(filter.extractUsernamePassword(request)).containsExactly(USERNAME, PASSWORD);
  }

  @Test
  public void extractUsernamePasswordCustomHeaders() throws Exception
  {
    final String customHeaderUsername = "X-OpenIDM-Username";
    final String customHeaderPassword = "X-OpenIDM-Password";

    authConfig.setCustomHeadersAuthenticationSupported(true);
    authConfig.setCustomHeaderUsername(customHeaderUsername);
    authConfig.setCustomHeaderPassword(customHeaderPassword);

    final Request request = new Request();
    request.getHeaders().add(customHeaderUsername, USERNAME);
    request.getHeaders().add(customHeaderPassword, PASSWORD);

    assertThat(filter.extractUsernamePassword(request)).containsExactly(USERNAME, PASSWORD);
  }
}
