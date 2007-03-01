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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.protocols.jmx;

import java.util.HashMap;

import org.opends.server.TestCaseUtils;
import org.opends.server.plugins.InvocationCounterPlugin;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * This class check is the pos-connected and post-disconnected plugin are
 * called (see issue #728).
 */
public class postConnectedDisconnectTest extends JmxTestCase
{

  /**
   * Set up the environment for performing the tests in this suite.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @BeforeClass
  public void setUp() throws Exception
  {
    // Make sure that the server is up and running.
    TestCaseUtils.startServer();
    synchronized (this)
    {
        this.wait(500);
    }
  }

  /**
   * Perform a simple connect.
   * @throws Exception If something wrong occurs.
   */
  @Test(enabled = true)
  public void checkPostconnectDisconnectPlugin() throws Exception
  {
    // Before the test, how many time postconnect and postdisconnect
    // have been called.
    int postConnectBefore = InvocationCounterPlugin.getPostConnectCount();
    int postDisconnectBefore = InvocationCounterPlugin.getPostDisconnectCount();

    // Create a new client connection
    HashMap<String, Object> env = new HashMap<String, Object>();
    String[] credentials = new String[] { "cn=directory manager" , "password"};
    env.put("jmx.remote.credentials", credentials);
    env.put("jmx.remote.x.client.connection.check.period",0);
    OpendsJmxConnector opendsConnector = new OpendsJmxConnector("localhost",
        (int) TestCaseUtils.getServerJmxPort(), env);
    opendsConnector.connect();
    assertNotNull(opendsConnector);

    // Check that number of postconnect has been incremented.
    Thread.sleep(3000);
    int postConnectAfter = InvocationCounterPlugin.getPostConnectCount();
    int postDisconnectAfter = InvocationCounterPlugin.getPostDisconnectCount();
    assertEquals(postConnectBefore +1, postConnectAfter);
    assertEquals(postDisconnectBefore, postDisconnectAfter);

    // Close the client connection
    opendsConnector.close();
    Thread.sleep(3000);

    // Check that number of postdisconnect has been incremented.
    postConnectAfter = InvocationCounterPlugin.getPostConnectCount();
    postDisconnectAfter = InvocationCounterPlugin.getPostDisconnectCount();
    assertEquals(postConnectBefore +1 , postConnectAfter);
    assertEquals(postDisconnectBefore +1 , postDisconnectAfter);
  }
}
