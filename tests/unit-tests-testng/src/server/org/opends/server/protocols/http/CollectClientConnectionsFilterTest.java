/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.protocols.http;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.opends.server.protocols.http.CollectClientConnectionsFilter.*;

import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.forgerock.json.resource.ResourceException;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.util.Base64;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class CollectClientConnectionsFilterTest extends DirectoryServerTestCase
{

  private static final String USERNAME = "Aladdin";
  private static final String PASSWORD = "open sesame";
  private static final String BASE64_USERPASS = Base64
      .encode((USERNAME + ":" + PASSWORD).getBytes());

  private HTTPAuthenticationConfig authConfig = new HTTPAuthenticationConfig();

  private CollectClientConnectionsFilter filter =
      new CollectClientConnectionsFilter(null, authConfig);

  @DataProvider(name = "Invalid HTTP basic auth strings")
  public Object[][] getInvalidHttpBasicAuthStrings()
  {
    return new Object[][] { { null }, { "bla" },
      { "basic " + Base64.encode("la:bli:blu".getBytes()) } };
  }

  @Test(dataProvider = "Invalid HTTP basic auth strings")
  public void parseUsernamePasswordFromInvalidAuthZHeader(String authZHeader)
      throws Exception
  {
    assertThat(filter.parseUsernamePassword(authZHeader)).isNull();
  }

  @DataProvider(name = "Valid HTTP basic auth strings")
  public Object[][] getValidHttpBasicAuthStrings()
  {
    return new Object[][] { { "basic " + BASE64_USERPASS },
      { "Basic " + BASE64_USERPASS } };
  }

  @Test(dataProvider = "Valid HTTP basic auth strings")
  public void parseUsernamePasswordFromValidAuthZHeader(String authZHeader)
      throws Exception
  {
    assertThat(filter.parseUsernamePassword(authZHeader)).containsExactly(
        USERNAME, PASSWORD);
  }

  @Test
  public void sendUnauthorizedResponseWithHttpBasicAuthWillChallengeUserAgent()
      throws Exception
  {
    authConfig.setBasicAuthenticationSupported(true);

    ServletOutputStream oStream = mock(ServletOutputStream.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getOutputStream()).thenReturn(oStream);
    sendUnauthorizedResponseWithHTTPBasicAuthChallenge(response);

    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(response).setHeader("WWW-Authenticate",
        "Basic realm=\"org.forgerock.opendj\"");
    verifyUnauthorizedOutputMessage(response, oStream);
  }

  @Test
  public void sendUnauthorizedResponseWithoutHttpBasicAuthWillNotChallengeUserAgent()
      throws Exception
  {
    authConfig.setBasicAuthenticationSupported(true);

    HttpServletResponse response = mock(HttpServletResponse.class);
    ServletOutputStream oStream = mock(ServletOutputStream.class);
    when(response.getOutputStream()).thenReturn(oStream);
    sendUnauthorizedResponseWithHTTPBasicAuthChallenge(response);

    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verifyUnauthorizedOutputMessage(response, oStream);
  }

  private void sendUnauthorizedResponseWithHTTPBasicAuthChallenge(
      HttpServletResponse response)
  {
    filter.sendErrorReponse(response, true, ResourceException.getException(
        HttpServletResponse.SC_UNAUTHORIZED, "Invalid Credentials"));
  }

  private void verifyUnauthorizedOutputMessage(HttpServletResponse response,
      ServletOutputStream oStream) throws IOException
  {
    verify(response).getOutputStream();
    verify(oStream).println(
        "{\n" + "    \"code\": 401,\n"
            + "    \"message\": \"Invalid Credentials\",\n"
            + "    \"reason\": \"Unauthorized\"\n" + "}");
  }

  @Test
  public void extractUsernamePasswordHttpBasicAuthWillAcceptUserAgent()
      throws Exception
  {
    authConfig.setBasicAuthenticationSupported(true);

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader(HTTP_BASIC_AUTH_HEADER)).thenReturn(
        "Basic " + BASE64_USERPASS);

    assertThat(filter.extractUsernamePassword(request)).containsExactly(
        USERNAME, PASSWORD);
  }

  @Test
  public void extractUsernamePasswordCustomHeaders() throws Exception
  {
    final String customHeaderUsername = "X-OpenIDM-Username";
    final String customHeaderPassword = "X-OpenIDM-Password";

    authConfig.setCustomHeadersAuthenticationSupported(true);
    authConfig.setCustomHeaderUsername(customHeaderUsername);
    authConfig.setCustomHeaderPassword(customHeaderPassword);

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader(customHeaderUsername)).thenReturn(USERNAME);
    when(request.getHeader(customHeaderPassword)).thenReturn(PASSWORD);

    assertThat(filter.extractUsernamePassword(request)).containsExactly(
        USERNAME, PASSWORD);
  }

}
