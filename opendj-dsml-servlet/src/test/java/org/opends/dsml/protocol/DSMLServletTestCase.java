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
package org.opends.dsml.protocol;

import static org.opends.server.protocols.ldap.LDAPConstants.OP_TYPE_ABANDON_REQUEST;
import static org.opends.server.protocols.ldap.LDAPConstants.OP_TYPE_BIND_REQUEST;
import static org.opends.server.protocols.ldap.LDAPConstants.OP_TYPE_UNBIND_REQUEST;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.forgerock.testng.ForgeRockTestCase;
import org.opends.server.protocols.ldap.BindResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.tools.LDAPReader;
import org.opends.server.tools.LDAPWriter;
import org.testng.annotations.Test;

/**
 * Tests the error handling of {@link DSMLServlet#doPost}: an abandon request
 * used to trigger a {@code NullPointerException} which leaked the LDAP
 * connection, a request without a usable Content-Type header used to trigger a
 * {@code NullPointerException} as well, and the second batch request of a SOAP
 * body used to be silently skipped.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "dsml" })
public class DSMLServletTestCase extends ForgeRockTestCase
{
  /** SOAP 1.1 content type. */
  private static final String SOAP_1_1_CONTENT_TYPE = "text/xml";
  /** SOAP 1.2 content type. */
  private static final String SOAP_1_2_CONTENT_TYPE = "application/soap+xml";
  /** SOAP 1.2 envelope namespace, as it appears in the reply. */
  private static final String SOAP_1_2_NAMESPACE = "http://www.w3.org/2003/05/soap-envelope";

  private static final String ABANDON_BATCH =
      soap11(abandonBatch("1", null));

  /** Two batch requests in a single SOAP body, each carrying an abandon request. */
  private static final String TWO_ABANDON_BATCHES =
      soap11(abandonBatch("1", null) + abandonBatch("2", null));

  /** Same, with an authRequest which turns into a SASL authzid on each bind. */
  private static final String TWO_AUTHZ_BATCHES =
      soap11(abandonBatch("1", "dn:cn=first") + abandonBatch("2", "dn:cn=second"));

  private static String abandonBatch(String requestID, String authzPrincipal)
  {
    return "<batchRequest xmlns=\"urn:oasis:names:tc:DSML:2:0:core\" requestID=\"" + requestID + "\">"
        + (authzPrincipal != null ? "<authRequest principal=\"" + authzPrincipal + "\"/>" : "")
        + "<abandonRequest abandonID=\"1\"/>"
        + "</batchRequest>";
  }

  private static String soap11(String body)
  {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<se:Envelope xmlns:se=\"http://schemas.xmlsoap.org/soap/envelope/\">"
        + "<se:Body>" + body + "</se:Body>"
        + "</se:Envelope>";
  }

  private static String soap12(String body)
  {
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<se:Envelope xmlns:se=\"" + SOAP_1_2_NAMESPACE + "\">"
        + "<se:Body>" + body + "</se:Body>"
        + "</se:Envelope>";
  }

  /**
   * An abandon request produces no response element: the servlet must neither
   * fail nor leave the connection to the directory server open.
   */
  @Test
  public void testAbandonRequestIsProcessedAndConnectionIsClosed() throws Exception
  {
    try (FakeLdapServer server = new FakeLdapServer())
    {
      Map<String, String> headers = new LinkedHashMap<>();
      headers.put("Content-Type", SOAP_1_1_CONTENT_TYPE);

      String response = doPost(server.getPort(), headers, ABANDON_BATCH);

      assertTrue(response.contains("batchResponse"), response);
      assertFalse(response.contains("errorResponse"), response);
      // no response element is defined for an abandon request
      assertFalse(response.contains("abandonResponse"), response);

      server.awaitDisconnect();
      assertEquals(server.getReceivedOpTypes(),
          list(OP_TYPE_BIND_REQUEST, OP_TYPE_ABANDON_REQUEST, OP_TYPE_UNBIND_REQUEST),
          "the abandon request was not forwarded, or the connection was leaked");
    }
  }

  /**
   * A request without any Content-Type header must be rejected as malformed,
   * keeping the requestID so that the client can correlate the reply.
   */
  @Test
  public void testMissingContentTypeIsRejectedAsMalformedRequest() throws Exception
  {
    try (FakeLdapServer server = new FakeLdapServer())
    {
      String response = doPost(server.getPort(), new LinkedHashMap<String, String>(), ABANDON_BATCH);

      assertTrue(response.contains("malformedRequest"), response);
      assertTrue(response.contains("requestID=\"1\""), response);
      assertTrue(server.getReceivedOpTypes().isEmpty(),
          "no connection to the directory server should have been opened");
    }
  }

  /** A Content-Type header matching neither SOAP 1.1 nor SOAP 1.2 is malformed too. */
  @Test
  public void testUnsupportedContentTypeIsRejectedAsMalformedRequest() throws Exception
  {
    try (FakeLdapServer server = new FakeLdapServer())
    {
      Map<String, String> headers = new LinkedHashMap<>();
      headers.put("Content-Type", "application/json");

      String response = doPost(server.getPort(), headers, ABANDON_BATCH);

      assertTrue(response.contains("malformedRequest"), response);
      assertTrue(server.getReceivedOpTypes().isEmpty(),
          "no connection to the directory server should have been opened");
    }
  }

  /**
   * An error detected before the request is parsed must still reach the client
   * when the Content-Type header is missing.
   */
  @Test
  public void testMissingContentTypeStillReportsCredentialsError() throws Exception
  {
    try (FakeLdapServer server = new FakeLdapServer())
    {
      Map<String, String> headers = new LinkedHashMap<>();
      // credentials without the ':' separator: the password cannot be retrieved
      headers.put("Authorization", "Basic " + Base64.getEncoder()
          .encodeToString("cn=directory manager".getBytes(StandardCharsets.UTF_8)));

      String response = doPost(server.getPort(), headers, ABANDON_BATCH);

      assertTrue(response.contains("authenticationFailed"), response);
      assertTrue(server.getReceivedOpTypes().isEmpty(),
          "no connection to the directory server should have been opened");
    }
  }

  /**
   * A malformed Authorization header must not stop the header scan: the
   * Content-Type still decides which SOAP version the error is reported with.
   */
  @Test
  public void testMalformedAuthorizationKeepsTheRequestSoapVersion() throws Exception
  {
    try (FakeLdapServer server = new FakeLdapServer())
    {
      Map<String, String> headers = new LinkedHashMap<>();
      // credentials which are not valid Base64, read before the Content-Type
      headers.put("Authorization", "Basic !!!");
      headers.put("Content-Type", SOAP_1_2_CONTENT_TYPE);

      String response = doPost(server.getPort(), headers, soap12(abandonBatch("1", null)));

      assertTrue(response.contains("authenticationFailed"), response);
      assertTrue(response.contains(SOAP_1_2_NAMESPACE), response);
    }
  }

  /** The SOAP 1.2 request path must work as the SOAP 1.1 one does. */
  @Test
  public void testSoap12RequestIsProcessed() throws Exception
  {
    try (FakeLdapServer server = new FakeLdapServer())
    {
      Map<String, String> headers = new LinkedHashMap<>();
      headers.put("Content-Type", SOAP_1_2_CONTENT_TYPE);

      String response = doPost(server.getPort(), headers, soap12(abandonBatch("1", null)));

      assertTrue(response.contains("batchResponse"), response);
      assertFalse(response.contains("errorResponse"), response);
      assertTrue(response.contains(SOAP_1_2_NAMESPACE), response);

      server.awaitDisconnect();
      assertEquals(server.getReceivedOpTypes(),
          list(OP_TYPE_BIND_REQUEST, OP_TYPE_ABANDON_REQUEST, OP_TYPE_UNBIND_REQUEST),
          "the abandon request was not forwarded, or the connection was leaked");
    }
  }

  /**
   * Every batch request of a SOAP body gets its own connection: the second one
   * used to be silently skipped because the first connection was left assigned.
   */
  @Test
  public void testEachBatchRequestGetsItsOwnConnection() throws Exception
  {
    try (FakeLdapServer server = new FakeLdapServer())
    {
      Map<String, String> headers = new LinkedHashMap<>();
      headers.put("Content-Type", SOAP_1_1_CONTENT_TYPE);

      String response = doPost(server.getPort(), headers, TWO_ABANDON_BATCHES);

      assertFalse(response.contains("errorResponse"), response);

      server.awaitDisconnect(2);
      assertEquals(server.getReceivedOpTypes(),
          list(OP_TYPE_BIND_REQUEST, OP_TYPE_ABANDON_REQUEST, OP_TYPE_UNBIND_REQUEST,
               OP_TYPE_BIND_REQUEST, OP_TYPE_ABANDON_REQUEST, OP_TYPE_UNBIND_REQUEST),
          "the second batch request was not processed on its own connection");
    }
  }

  /**
   * The connection options are shared by all the batch requests of a SOAP body,
   * and the SASL authzid they carry is single valued: the authzid of a batch
   * request must not survive into the bind of the next one.
   */
  @Test
  public void testAuthzIdIsNotAccumulatedAcrossBatchRequests() throws Exception
  {
    try (FakeLdapServer server = new FakeLdapServer())
    {
      Map<String, String> params = new LinkedHashMap<>();
      // turn the HTTP credentials into a SASL PLAIN authid, so that the
      // authRequest of each batch request becomes an authzid
      params.put("ldap.authzidtypeisid", "true");

      Map<String, String> headers = new LinkedHashMap<>();
      headers.put("Content-Type", SOAP_1_1_CONTENT_TYPE);
      headers.put("Authorization", "Basic " + Base64.getEncoder()
          .encodeToString("user:password".getBytes(StandardCharsets.UTF_8)));

      String response = doPost(server.getPort(), params, headers, TWO_AUTHZ_BATCHES);

      assertFalse(response.contains("errorResponse"), response);

      server.awaitDisconnect(2);
      assertEquals(server.getReceivedOpTypes(),
          list(OP_TYPE_BIND_REQUEST, OP_TYPE_ABANDON_REQUEST, OP_TYPE_UNBIND_REQUEST,
               OP_TYPE_BIND_REQUEST, OP_TYPE_ABANDON_REQUEST, OP_TYPE_UNBIND_REQUEST),
          "the bind of the second batch request did not happen");
    }
  }

  /** Runs {@code doPost} against a servlet configured to use the given LDAP port. */
  private String doPost(int ldapPort, Map<String, String> headers, String body) throws Exception
  {
    return doPost(ldapPort, Collections.<String, String> emptyMap(), headers, body);
  }

  private String doPost(int ldapPort, Map<String, String> extraParams,
      Map<String, String> headers, String body) throws Exception
  {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("ldap.host", InetAddress.getLoopbackAddress().getHostAddress());
    params.put("ldap.port", String.valueOf(ldapPort));
    params.putAll(extraParams);

    DSMLServlet servlet = new DSMLServlet();
    servlet.init(servletConfig(params));

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    servlet.doPost(httpRequest(headers, body.getBytes(StandardCharsets.UTF_8)), httpResponse(out));
    return new String(out.toByteArray(), StandardCharsets.UTF_8);
  }

  private static List<Byte> list(byte... opTypes)
  {
    List<Byte> result = new ArrayList<>(opTypes.length);
    for (byte opType : opTypes)
    {
      result.add(opType);
    }
    return result;
  }

  /**
   * A minimal LDAP endpoint which answers the bind request with a success
   * result and records the type of every message it receives. Connections are
   * served one after the other, so that a SOAP body holding several batch
   * requests can be exercised.
   */
  private static final class FakeLdapServer implements Closeable
  {
    private final ServerSocket serverSocket;
    private final List<Byte> receivedOpTypes = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();
    private int closedConnections;
    private volatile Exception failure;
    private volatile boolean stopped;

    FakeLdapServer() throws IOException
    {
      serverSocket = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
      Thread thread = new Thread(this::serve, "fake-ldap-server");
      thread.setDaemon(true);
      thread.start();
    }

    int getPort()
    {
      return serverSocket.getLocalPort();
    }

    List<Byte> getReceivedOpTypes()
    {
      return new ArrayList<>(receivedOpTypes);
    }

    void awaitDisconnect() throws InterruptedException
    {
      awaitDisconnect(1);
    }

    /** Waits for the given number of connections to have been served. */
    void awaitDisconnect(int expectedConnections) throws InterruptedException
    {
      final long deadline = System.currentTimeMillis()
          + TimeUnit.SECONDS.toMillis(30);
      synchronized (lock)
      {
        while (closedConnections < expectedConnections)
        {
          final long remaining = deadline - System.currentTimeMillis();
          assertTrue(remaining > 0, "the client did not disconnect: "
              + closedConnections + " connection(s) served out of " + expectedConnections);
          lock.wait(remaining);
        }
      }
      assertNull(failure, "the fake LDAP server failed: " + failure);
    }

    private void serve()
    {
      while (!stopped)
      {
        final Socket socket;
        try
        {
          socket = serverSocket.accept();
        }
        catch (IOException e)
        {
          if (!stopped)
          {
            recordFailure(e);
          }
          return;
        }
        try (Socket connection = socket)
        {
          serveConnection(connection);
        }
        catch (Exception e)
        {
          recordFailure(e);
        }
        finally
        {
          synchronized (lock)
          {
            closedConnections++;
            lock.notifyAll();
          }
        }
      }
    }

    private void serveConnection(Socket socket) throws Exception
    {
      LDAPReader reader = new LDAPReader(socket);
      LDAPWriter writer = new LDAPWriter(socket);
      LDAPMessage message;
      while ((message = reader.readMessage()) != null)
      {
        receivedOpTypes.add(message.getProtocolOpType());
        if (message.getProtocolOpType() == OP_TYPE_BIND_REQUEST)
        {
          writer.writeMessage(new LDAPMessage(message.getMessageID(),
              new BindResponseProtocolOp(LDAPResultCode.SUCCESS)));
        }
      }
    }

    private void recordFailure(Exception e)
    {
      if (!stopped && failure == null)
      {
        failure = e;
      }
    }

    @Override
    public void close() throws IOException
    {
      stopped = true;
      serverSocket.close();
    }
  }

  private static ServletConfig servletConfig(final Map<String, String> params)
  {
    final ServletContext context = stub(ServletContext.class, (proxy, method, args) -> {
      switch (method.getName())
      {
      case "getInitParameter":
        return params.get(args[0]);
      case "getInitParameterNames":
        return Collections.enumeration(params.keySet());
      default:
        return defaultValue(method);
      }
    });
    return stub(ServletConfig.class, (proxy, method, args) ->
        "getServletContext".equals(method.getName()) ? context : defaultValue(method));
  }

  private static HttpServletRequest httpRequest(final Map<String, String> headers, final byte[] body)
  {
    final ByteArrayInputStream content = new ByteArrayInputStream(body);
    final ServletInputStream in = new ServletInputStream()
    {
      @Override
      public int read()
      {
        return content.read();
      }

      @Override
      public boolean isFinished()
      {
        return content.available() == 0;
      }

      @Override
      public boolean isReady()
      {
        return true;
      }

      @Override
      public void setReadListener(ReadListener readListener)
      {
        // not used
      }
    };
    return stub(HttpServletRequest.class, (proxy, method, args) -> {
      switch (method.getName())
      {
      case "getInputStream":
        return in;
      case "getHeaderNames":
        return Collections.enumeration(headers.keySet());
      case "getHeader":
        return headers.get(args[0]);
      default:
        return defaultValue(method);
      }
    });
  }

  private static HttpServletResponse httpResponse(final ByteArrayOutputStream out)
  {
    final ServletOutputStream os = new ServletOutputStream()
    {
      @Override
      public void write(int b)
      {
        out.write(b);
      }

      @Override
      public boolean isReady()
      {
        return true;
      }

      @Override
      public void setWriteListener(WriteListener writeListener)
      {
        // not used
      }
    };
    return stub(HttpServletResponse.class, (proxy, method, args) ->
        "getOutputStream".equals(method.getName()) ? os : defaultValue(method));
  }

  private static <T> T stub(Class<T> type, InvocationHandler handler)
  {
    return type.cast(Proxy.newProxyInstance(
        DSMLServletTestCase.class.getClassLoader(), new Class<?>[] { type }, handler));
  }

  /**
   * A proxy must return a value assignable to the return type of the invoked
   * method: {@code null} is only acceptable for a reference or {@code void}
   * return type, so every primitive has to be covered here.
   */
  private static Object defaultValue(Method method)
  {
    Class<?> returnType = method.getReturnType();
    if (returnType == boolean.class)
    {
      return Boolean.FALSE;
    }
    else if (returnType == char.class)
    {
      return (char) 0;
    }
    else if (returnType == byte.class)
    {
      return (byte) 0;
    }
    else if (returnType == short.class)
    {
      return (short) 0;
    }
    else if (returnType == int.class)
    {
      return 0;
    }
    else if (returnType == long.class)
    {
      return 0L;
    }
    else if (returnType == float.class)
    {
      return 0f;
    }
    else if (returnType == double.class)
    {
      return 0d;
    }
    else if ("toString".equals(method.getName()))
    {
      return "stub";
    }
    return null;
  }
}
