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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.protocols.jmx;

import static org.testng.Assert.*;

import java.util.HashMap;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.core.DeleteOperation;
import org.opends.server.plugins.InvocationCounterPlugin;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * This class check is the pos-connected and post-disconnected plugin are
 * called (see issue #728).
 */
public class PostConnectedDisconnectTest extends JmxTestCase
{
  @Override
  @BeforeClass
  public void setUp() throws Exception
  {
    super.setUp();

    TestCaseUtils.addEntries(
        "dn: cn=Privileged User,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "cn: Privileged User",
        "givenName: Privileged",
        "sn: User",
        "uid: privileged.user",
        "userPassword: password",
        "ds-privilege-name: config-read",
        "ds-privilege-name: config-write",
        "ds-privilege-name: password-reset",
        "ds-privilege-name: update-schema",
        "ds-privilege-name: ldif-import",
        "ds-privilege-name: ldif-export",
        "ds-privilege-name: backend-backup",
        "ds-privilege-name: backend-restore",
        "ds-privilege-name: proxied-auth",
        "ds-privilege-name: bypass-acl",
        "ds-privilege-name: unindexed-search",
        "ds-privilege-name: jmx-read",
        "ds-privilege-name: jmx-write",
        "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
             "cn=Password Policies,cn=config");
  }

  /**
   * Clean up the environment after performing the tests in this suite.
   *
   * @throws Exception
   *           If the environment could not be set up.
   */
  @AfterClass
  public void afterClass() throws Exception
  {
    InternalClientConnection conn = InternalClientConnection
        .getRootConnection();

    DeleteOperation deleteOperation = conn.processDelete(DN
        .valueOf("cn=Privileged User,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }

  /**
   * Perform a simple connect.
   * @throws Exception If something wrong occurs.
   */
  @Test(enabled = false, groups = "slow")
  public void checkPostConnectDisconnectPlugin() throws Exception
  {
    // Before the test, how many time postconnect and postdisconnect
    // have been called.
    int postConnectBefore = InvocationCounterPlugin.getPostConnectCount();
    int postDisconnectBefore = InvocationCounterPlugin.getPostDisconnectCount();

    // Create a new client connection
    HashMap<String, Object> env = new HashMap<>();
    String[] credentials = new String[] { "cn=Privileged User,o=test", "password" };
    env.put("jmx.remote.credentials", credentials);
    env.put("jmx.remote.x.client.connection.check.period",0);
    OpendsJmxConnector opendsConnector = new OpendsJmxConnector("localhost",
        TestCaseUtils.getServerJmxPort(), env);
    assertNotNull(opendsConnector);
    opendsConnector.connect();

    // Check that number of postconnect has been incremented.
    // Don't wait more than 5 seconds
    long endTime = System.currentTimeMillis() + 5000;
    int postConnectAfter = postConnectBefore;

    while (System.currentTimeMillis() < endTime
        && postConnectAfter == postConnectBefore)
    {
      Thread.sleep(10);
      postConnectAfter = InvocationCounterPlugin.getPostConnectCount();
    }
    assertEquals(postConnectBefore +1, postConnectAfter);

    // Check that postDisconnect is not incremented.
    int postDisconnectAfter = InvocationCounterPlugin.getPostDisconnectCount();
    assertEquals(postDisconnectBefore, postDisconnectAfter);

    // Close the client connection
    opendsConnector.close();

    // Check that number of postdisconnect has been incremented.
    // Don't wait more than 5 seconds
    endTime = System.currentTimeMillis() + 5000;
    postDisconnectAfter = postDisconnectBefore;
    while (System.currentTimeMillis() < endTime
        && postDisconnectAfter == postDisconnectBefore)
    {
      Thread.sleep(10);
      postDisconnectAfter = InvocationCounterPlugin.getPostDisconnectCount();
    }
    assertEquals(postDisconnectBefore +1 , postDisconnectAfter);

    // Check that postconnect is not incremented again.
    postConnectAfter = InvocationCounterPlugin.getPostConnectCount();
    assertEquals(postConnectBefore +1 , postConnectAfter);
  }
}
