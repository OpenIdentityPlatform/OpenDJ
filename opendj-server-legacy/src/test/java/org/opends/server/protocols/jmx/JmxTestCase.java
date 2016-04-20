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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.protocols.jmx;

import static java.util.concurrent.TimeUnit.*;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

import java.util.List;
import java.util.concurrent.Callable;

import org.assertj.core.api.Assertions;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.types.Attributes;
import org.opends.server.types.Modification;
import org.opends.server.util.TestTimer;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** An abstract class that all JMX unit test should extend. */
@SuppressWarnings("javadoc")
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
    List<ConnectionHandler<?>> handlers = DirectoryServer.getConnectionHandlers();
    assertNotNull(handlers);
    JmxConnectionHandler jmxConnectionHandler = getJmxConnectionHandler(handlers);
    if (jmxConnectionHandler == null)
    {
      enableJmx();
      jmxConnectionHandler = getJmxConnectionHandler(handlers);
    }
    assertNotNull(jmxConnectionHandler);
    final RmiConnector rmiConnector = jmxConnectionHandler.getRMIConnector();

    TestTimer timer = new TestTimer.Builder()
      .maxSleep(20, SECONDS)
      .sleepTimes(200, MILLISECONDS)
      .toTimer();
    timer.repeatUntilSuccess(new Callable<Void>()
    {
      @Override
      public Void call() throws Exception
      {
        Assertions.assertThat(rmiConnector.jmxRmiConnectorNoClientCertificate).isNotNull();
        Assertions.assertThat(rmiConnector.jmxRmiConnectorNoClientCertificate.isActive()).isTrue();
        return null;
      }
    });
    return jmxConnectionHandler;
  }

  private JmxConnectionHandler getJmxConnectionHandler(List<ConnectionHandler<?>> handlers)
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
