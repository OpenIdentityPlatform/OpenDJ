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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.jmx;

import static org.testng.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.Attributes;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * An abstract class that all JMX unit test should extend.
 */
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

    synchronized (this)
    {
      this.wait(500);
    }
    JmxConnectionHandler jmxCtx = getJmxConnectionHandler();
    if (jmxCtx == null)
    {
      throw new Exception("Unable to get a JMX connector");
    }
  }

  /**
   * Get a reference to the JMX connection handler.
   *
   * @throws an
   *           Exception is something went wrong.
   */
  protected JmxConnectionHandler getJmxConnectionHandler() throws Exception
  {
    List<ConnectionHandler> handlers = DirectoryServer
        .getConnectionHandlers();
    assertNotNull(handlers);
    JmxConnectionHandler jmxConnectionHandler = null;
    for (ConnectionHandler handler : handlers)
    {
      if (handler instanceof JmxConnectionHandler)
      {
        jmxConnectionHandler = (JmxConnectionHandler) handler;
        break;
      }
    }
    if (jmxConnectionHandler == null)
    {
      enableJmx();
      synchronized (this)
      {
        this.wait(500);
      }
      for (ConnectionHandler handler : handlers)
      {
        if (handler instanceof JmxConnectionHandler)
        {
          jmxConnectionHandler = (JmxConnectionHandler) handler;
          break;
        }
      }
    }

    return jmxConnectionHandler;
  }

  /**
   * Enable JMX with the port chosen in TestCaseUtils.
   *
   * @throws Exception
   *           if the handler cannot be enabled.
   */
  private void enableJmx() throws Exception
  {
    ArrayList<Modification> mods = new ArrayList<Modification>();

    InternalClientConnection conn = InternalClientConnection
        .getRootConnection();
    mods.add(new Modification(ModificationType.REPLACE,
        Attributes.create(
            "ds-cfg-enabled", "true")));
    ModifyOperationBasis op = new ModifyOperationBasis(
        conn,
        conn.nextOperationID(),
        conn.nextMessageID(),
        new ArrayList<Control>(),
        DN
            .decode("cn=JMX Connection Handler,cn=Connection Handlers,cn=config"),
        mods);
    op.run();
  }
}
