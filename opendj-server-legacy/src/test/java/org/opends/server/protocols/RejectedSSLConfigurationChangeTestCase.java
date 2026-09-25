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
package org.opends.server.protocols;

import static org.opends.server.protocols.internal.InternalClientConnection.getRootConnection;
import static org.opends.server.util.StaticUtils.*;
import static org.testng.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.Configuration;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.reactive.LDAPConnectionHandler2;
import org.forgerock.opendj.server.config.meta.HTTPConnectionHandlerCfgDefn;
import org.forgerock.opendj.server.config.meta.LDAPConnectionHandlerCfgDefn;
import org.forgerock.opendj.server.config.server.ConnectionHandlerCfg;
import org.forgerock.opendj.server.config.server.HTTPConnectionHandlerCfg;
import org.forgerock.opendj.server.config.server.LDAPConnectionHandlerCfg;
import org.opends.admin.ads.util.BlindTrustManager;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.extensions.InitializationUtils;
import org.opends.server.protocols.http.HTTPConnectionHandler;
import org.opends.server.protocols.ldap.LDAPConnectionHandler;
import org.opends.server.types.Entry;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * A change to an SSL connection handler that is rejected because its key store cannot be loaded
 * must leave the running handler as it was: listening, with the SSL context it had.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit" }, sequential = true)
public class RejectedSSLConfigurationChangeTestCase extends DirectoryServerTestCase
{
  private static final LocalizableMessage STOP_REASON = LocalizableMessage.raw("Don't need a reason.");
  private static final DN KEY_MANAGER_DN = DN.valueOf("cn=Rejected Change Keys,cn=Key Manager Providers,cn=config");

  /** How long the handler must keep serving TLS after the rejected change: its thread checks every second. */
  private static final long KEEPS_SERVING_MS = TimeUnit.SECONDS.toMillis(3);

  private enum Kind
  {
    LDAP2, LDAP_LEGACY, HTTP
  }

  private static final class NoChanges<C extends Configuration> implements ConfigurationChangeListener<C>
  {
    @Override
    public boolean isConfigurationChangeAcceptable(C configuration, List<LocalizableMessage> unacceptableReasons)
    {
      return true;
    }

    @Override
    public ConfigChangeResult applyConfigurationChange(C configuration)
    {
      return new ConfigChangeResult();
    }
  }

  private File keyStore;

  @BeforeClass
  public void setUp() throws Exception
  {
    TestCaseUtils.startServer();
    keyStore = File.createTempFile("rejected-change", ".keystore");
    keyStore.deleteOnExit();
    restoreKeyStore();
    TestCaseUtils.addEntry(
        "dn: " + KEY_MANAGER_DN,
        "objectClass: top",
        "objectClass: ds-cfg-key-manager-provider",
        "objectClass: ds-cfg-file-based-key-manager-provider",
        "cn: Rejected Change Keys",
        "ds-cfg-java-class: org.opends.server.extensions.FileBasedKeyManagerProvider",
        "ds-cfg-enabled: true",
        "ds-cfg-key-store-type: JKS",
        "ds-cfg-key-store-file: " + keyStore.getAbsolutePath(),
        "ds-cfg-key-store-pin: password");
  }

  @AfterClass
  public void tearDown() throws Exception
  {
    // A handler registering for changes to its configuration also registers a reference to its key
    // manager provider, which keeps the provider from being deleted and outlives the handler.
    // Registering for changes to the same entry without a key manager provider drops the reference.
    final LDAPConnectionHandlerCfg ldap =
        (LDAPConnectionHandlerCfg) configuration(Kind.LDAP2, 1, null, "5 megabytes", false);
    final NoChanges<LDAPConnectionHandlerCfg> ldapListener = new NoChanges<>();
    ldap.addLDAPChangeListener(ldapListener);
    ldap.removeLDAPChangeListener(ldapListener);
    final HTTPConnectionHandlerCfg http =
        (HTTPConnectionHandlerCfg) configuration(Kind.HTTP, 1, null, "5 megabytes", false);
    final NoChanges<HTTPConnectionHandlerCfg> httpListener = new NoChanges<>();
    http.addHTTPChangeListener(httpListener);
    http.removeHTTPChangeListener(httpListener);

    final DeleteOperation delete = getRootConnection().processDelete(KEY_MANAGER_DN);
    assertEquals(delete.getResultCode(), ResultCode.SUCCESS, String.valueOf(delete.getErrorMessage()));
    Files.deleteIfExists(keyStore.toPath());
  }

  @BeforeMethod
  public void restoreKeyStore() throws IOException
  {
    Files.copy(getFileForPath("config/server.keystore").toPath(), keyStore.toPath(),
        StandardCopyOption.REPLACE_EXISTING);
  }

  @DataProvider
  public Object[][] handlers()
  {
    return new Object[][] {
      { Kind.LDAP2, null }, { Kind.LDAP2, "server-cert" },
      { Kind.LDAP_LEGACY, null }, { Kind.LDAP_LEGACY, "server-cert" },
      { Kind.HTTP, null }, { Kind.HTTP, "server-cert" },
    };
  }

  @Test(dataProvider = "handlers")
  public void rejectedChangeKeepsTheHandlerListening(Kind kind, String certNickname) throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final ConnectionHandler<?> handler = start(kind, configuration(kind, port, certNickname, "5 megabytes", true));
    try
    {
      assertServesTLS(port);

      Files.write(keyStore.toPath(), new byte[] { 1, 2, 3, 4 });
      final List<LocalizableMessage> reasons = new ArrayList<>();
      assertFalse(handler.isConfigurationAcceptable(configuration(kind, port, certNickname, "6 megabytes", true), reasons),
          "a change needing a key store that cannot be loaded was accepted");
      assertFalse(reasons.isEmpty(), "the change was rejected without a reason");

      final long deadline = System.currentTimeMillis() + KEEPS_SERVING_MS;
      do
      {
        assertServesTLS(port);
        Thread.sleep(250);
      }
      while (System.currentTimeMillis() < deadline);
    }
    finally
    {
      ((ServerShutdownListener) handler).processServerShutdown(STOP_REASON);
      handler.finalizeConnectionHandler(STOP_REASON);
      handler.join(10000);
      assertFalse(handler.isAlive(), "the connection handler thread is still running");
    }
  }

  @DataProvider
  public Object[][] kinds()
  {
    return new Object[][] { { Kind.LDAP2 }, { Kind.LDAP_LEGACY }, { Kind.HTTP } };
  }

  /** The start of a handler still disables it when its key store holds no key it can present. */
  @Test(dataProvider = "kinds")
  public void handlerWithoutItsCertificateDoesNotListen(Kind kind) throws Exception
  {
    final int port = TestCaseUtils.findFreePort();
    final ConnectionHandler<?> handler = start(kind, configuration(kind, port, "no-such-cert", "5 megabytes", true));
    try (Socket socket = new Socket("127.0.0.1", port))
    {
      fail("the handler listens on port " + port + " without the certificate it is configured to present");
    }
    catch (ConnectException expected)
    {
      // disabled at its start
    }
    finally
    {
      ((ServerShutdownListener) handler).processServerShutdown(STOP_REASON);
      handler.finalizeConnectionHandler(STOP_REASON);
      handler.join(10000);
      assertFalse(handler.isAlive(), "the connection handler thread is still running");
    }
  }

  private static ConnectionHandlerCfg configuration(Kind kind, int port, String certNickname, String maxRequestSize,
      boolean useSSL) throws Exception
  {
    final List<String> lines = new ArrayList<>();
    lines.add("dn: cn=Rejected Change Handler,cn=Connection Handlers,cn=config");
    lines.add("objectClass: top");
    lines.add("objectClass: ds-cfg-connection-handler");
    lines.add("cn: Rejected Change Handler");
    lines.add("ds-cfg-enabled: true");
    lines.add("ds-cfg-listen-address: 127.0.0.1");
    lines.add("ds-cfg-listen-port: " + port);
    lines.add("ds-cfg-accept-backlog: 128");
    lines.add("ds-cfg-keep-stats: false");
    lines.add("ds-cfg-use-tcp-keep-alive: true");
    lines.add("ds-cfg-use-tcp-no-delay: true");
    lines.add("ds-cfg-allow-tcp-reuse-address: true");
    lines.add("ds-cfg-max-request-size: " + maxRequestSize);
    lines.add("ds-cfg-use-ssl: " + useSSL);
    lines.add("ds-cfg-ssl-client-auth-policy: disabled");
    if (useSSL)
    {
      lines.add("ds-cfg-key-manager-provider: " + KEY_MANAGER_DN);
    }
    if (certNickname != null)
    {
      lines.add("ds-cfg-ssl-cert-nickname: " + certNickname);
    }
    if (kind == Kind.HTTP)
    {
      lines.add("objectClass: ds-cfg-http-connection-handler");
      lines.add("ds-cfg-java-class: " + HTTPConnectionHandler.class.getName());
      lines.add("ds-cfg-buffer-size: 4096 bytes");
      lines.add("ds-cfg-max-blocked-write-time-limit: 2 minutes");
      final Entry entry = TestCaseUtils.makeEntry(lines.toArray(new String[0]));
      return InitializationUtils.getConfiguration(HTTPConnectionHandlerCfgDefn.getInstance(), entry);
    }
    lines.add("objectClass: ds-cfg-ldap-connection-handler");
    lines.add("ds-cfg-java-class: "
        + (kind == Kind.LDAP2 ? LDAPConnectionHandler2.class : LDAPConnectionHandler.class).getName());
    lines.add("ds-cfg-allow-ldap-v2: false");
    lines.add("ds-cfg-send-rejection-notice: true");
    lines.add("ds-cfg-num-request-handlers: 2");
    lines.add("ds-cfg-allow-start-tls: false");
    final Entry entry = TestCaseUtils.makeEntry(lines.toArray(new String[0]));
    return InitializationUtils.getConfiguration(LDAPConnectionHandlerCfgDefn.getInstance(), entry);
  }

  private static ConnectionHandler<?> start(Kind kind, ConnectionHandlerCfg config) throws Exception
  {
    final ServerContext serverContext = DirectoryServer.getInstance().getServerContext();
    final ConnectionHandler<?> handler;
    switch (kind)
    {
    case LDAP2:
      final LDAPConnectionHandler2 ldap2 = new LDAPConnectionHandler2();
      ldap2.initializeConnectionHandler(serverContext, (LDAPConnectionHandlerCfg) config);
      handler = ldap2;
      break;
    case LDAP_LEGACY:
      final LDAPConnectionHandler legacy = new LDAPConnectionHandler();
      legacy.initializeConnectionHandler(serverContext, (LDAPConnectionHandlerCfg) config);
      handler = legacy;
      break;
    default:
      final HTTPConnectionHandler http = new HTTPConnectionHandler();
      http.initializeConnectionHandler(serverContext, (HTTPConnectionHandlerCfg) config);
      handler = http;
      break;
    }
    handler.start();
    return handler;
  }

  private static void assertServesTLS(int port) throws Exception
  {
    final SSLContext client = SSLContext.getInstance("TLS");
    client.init(null, new TrustManager[] { new BlindTrustManager() }, null);
    try (SSLSocket socket = (SSLSocket) client.getSocketFactory().createSocket("127.0.0.1", port))
    {
      socket.setSoTimeout(10000);
      socket.startHandshake();
      assertTrue(socket.getSession().getPeerCertificates().length > 0);
    }
    catch (IOException e)
    {
      fail("the handler does not serve TLS on port " + port + ": " + e, e);
    }
  }
}
