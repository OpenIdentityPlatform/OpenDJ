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
package org.opends.server.protocols.http;

import static org.testng.Assert.*;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.server.config.meta.HTTPConnectionHandlerCfgDefn;
import org.forgerock.opendj.server.config.server.HTTPConnectionHandlerCfg;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.InitializationUtils;
import org.opends.server.types.Entry;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "http" }, sequential = true)
public class HTTPConnectionHandlerTestCase extends DirectoryServerTestCase
{
  private static final LocalizableMessage STOP_REASON = LocalizableMessage.raw("Don't need a reason.");

  @BeforeClass
  public void setUp() throws Exception
  {
    // This test suite depends on having the schema available, so we'll start the server.
    TestCaseUtils.startServer();
  }

  /**
   * The start method must not return before the handler thread has attempted to start the embedded
   * HTTP server, otherwise a client connecting right after the handler has been enabled through
   * dsconfig can be refused.
   *
   * @throws Exception
   *           if the handler cannot be instantiated or started.
   */
  @Test
  public void testStartWaitsForListenPort() throws Exception
  {
    final int listenPort = TestCaseUtils.findFreePort();
    Entry handlerEntry = TestCaseUtils.makeEntry(
        "dn: cn=HTTP Connection Handler,cn=Connection Handlers,cn=config",
        "objectClass: top",
        "objectClass: ds-cfg-connection-handler",
        "objectClass: ds-cfg-http-connection-handler",
        "cn: HTTP Connection Handler",
        "ds-cfg-java-class: org.opends.server.protocols.http.HTTPConnectionHandler",
        "ds-cfg-enabled: true",
        "ds-cfg-listen-address: 127.0.0.1",
        "ds-cfg-listen-port: " + listenPort,
        "ds-cfg-accept-backlog: 128",
        "ds-cfg-keep-stats: false",
        "ds-cfg-use-tcp-keep-alive: true",
        "ds-cfg-use-tcp-no-delay: true",
        "ds-cfg-allow-tcp-reuse-address: true",
        "ds-cfg-max-request-size: 5 megabytes",
        "ds-cfg-buffer-size: 4096 bytes",
        "ds-cfg-max-blocked-write-time-limit: 2 minutes",
        "ds-cfg-use-ssl: false",
        "ds-cfg-ssl-client-auth-policy: optional",
        "ds-cfg-ssl-cert-nickname: server-cert");
    HTTPConnectionHandlerCfg config =
        InitializationUtils.getConfiguration(HTTPConnectionHandlerCfgDefn.getInstance(), handlerEntry);

    HTTPConnectionHandler handler = new HTTPConnectionHandler();
    handler.initializeConnectionHandler(DirectoryServer.getInstance().getServerContext(), config);
    try
    {
      handler.start();

      // No retry loop here on purpose: once start() has returned, the port must already be open.
      TestCaseUtils.assertPortIsAcceptingConnections(listenPort);
    }
    finally
    {
      handler.processServerShutdown(STOP_REASON);
      handler.finalizeConnectionHandler(STOP_REASON);
      handler.join(10000);
      assertFalse(handler.isAlive(), "the connection handler thread is still running");
    }
  }
}
