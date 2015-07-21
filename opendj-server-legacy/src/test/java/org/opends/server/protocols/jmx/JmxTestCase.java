/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package org.opends.server.protocols.jmx;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

import java.util.List;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.types.Attributes;
import org.opends.server.types.DN;
import org.opends.server.types.Modification;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** An abstract class that all JMX unit test should extend. */
@Test(groups = { "precommit", "jmx" }, sequential = true)
public abstract class JmxTestCase extends DirectoryServerTestCase
{
  /**
   * Set up the environment for performing the tests in this suite.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass(alwaysRun = true)
  public void setUp() throws Exception
  {
    // Make sure that the server is up and running.
    TestCaseUtils.restartServer();
    TestCaseUtils.initializeTestBackend(true);

    JmxConnectionHandler jmxCtx = getJmxConnectionHandler();
    assertNotNull(jmxCtx, "Unable to get a JMX connector");
  }

  /**
   * Get a reference to the JMX connection handler.
   *
   * @throws Exception if something went wrong.
   */
  protected JmxConnectionHandler getJmxConnectionHandler() throws Exception
  {
    List<ConnectionHandler> handlers = DirectoryServer.getConnectionHandlers();
    assertNotNull(handlers);
    JmxConnectionHandler jmxConnectionHandler = getJmxConnectionHandler(handlers);
    if (jmxConnectionHandler == null)
    {
      enableJmx();
      jmxConnectionHandler = getJmxConnectionHandler(handlers);
    }
    assertNotNull(jmxConnectionHandler);
    int cnt = 0;
    while (cnt <= 30 && jmxConnectionHandler.getRMIConnector().jmxRmiConnectorNoClientCertificate == null)
    {
      Thread.sleep(100);
      cnt++;
    }
    return jmxConnectionHandler;
  }

  private JmxConnectionHandler getJmxConnectionHandler(List<ConnectionHandler> handlers)
  {
    for (ConnectionHandler<?> handler : handlers)
    {
      if (handler instanceof JmxConnectionHandler)
      {
        return (JmxConnectionHandler) handler;
      }
    }
    return null;
  }

  private void enableJmx() throws Exception
  {
    ModifyOperationBasis op = new ModifyOperationBasis(
        getRootConnection(), nextOperationID(), nextMessageID(), null,
        DN.valueOf("cn=JMX Connection Handler,cn=Connection Handlers,cn=config"),
        newArrayList(new Modification(REPLACE, Attributes.create("ds-cfg-enabled", "true"))));
    op.run();
  }
}
