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
package org.opends.server.extensions;

import java.util.ArrayList;

import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.TestCaseUtils;
import org.opends.server.api.WorkQueue;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.plugins.DelayPreOpPlugin;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.schema.SchemaConstants;
import com.forgerock.opendj.ldap.tools.LDAPSearch;
import org.opends.server.types.Attributes;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.Modification;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.types.NullOutputStream.nullPrintStream;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

/**
 * A set of test cases for the traditional work queue.
 */
public class TraditionalWorkQueueTestCase
       extends ExtensionsTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer() throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Tests to ensure that the work queue is configured and enabled within the
   * Directory Server.
   */
  @Test
  public void testWorkQueueEnabled()
  {
    WorkQueue<?> workQueue = DirectoryServer.getWorkQueue();
    assertNotNull(workQueue);
    assertTrue(workQueue instanceof TraditionalWorkQueue);
  }



  /**
   * Verifies that the number of worker threads can be altered on the fly.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testChangingNumWorkerThreads()
         throws Exception
  {
    DN     dn   = DN.valueOf("cn=Work Queue,cn=config");
    String attr = "ds-cfg-num-worker-threads";
    ArrayList<Modification> mods = newArrayList(new Modification(REPLACE, Attributes.create(attr, "30")));

    InternalClientConnection conn = getRootConnection();
    ModifyOperation modifyOperation = conn.processModify(dn, mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    mods = newArrayList(new Modification(REPLACE, Attributes.create(attr, "24")));
    modifyOperation = conn.processModify(dn, mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    // Perform seven external searches so that we can make sure that the
    // unneeded worker threads can die off.
    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-b", "",
      "-s", "base",
      "(objectClass=*)",
      SchemaConstants.NO_ATTRIBUTES
    };

    for (int i=0; i < 7; i++)
    {
      assertEquals(LDAPSearch.run(nullPrintStream(), System.err, args), 0);
    }
  }



  /**
   * Tests the {@code WorkQueue.waitUntilIdle()} method for a case in which the
   * work queue should already be idle.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(groups = { "slow" })
  public void testWaitUntilIdleNoOpsInProgress()
         throws Exception
  {
    Thread.sleep(5000);

    long startTime = System.currentTimeMillis();
    assertTrue(DirectoryServer.getWorkQueue().waitUntilIdle(10000));
    long stopTime = System.currentTimeMillis();
    assertTrue(stopTime - startTime <= 1000);
  }



  /**
   * Tests the {@code WorkQueue.waitUntilIdle()} method for a case in which the
   * work queue should already be idle and no timeout is given.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(groups = { "slow" }, timeOut=10000)
  public void testWaitUntilIdleNoOpsInProgressNoTimeout()
         throws Exception
  {
    Thread.sleep(5000);

    long startTime = System.currentTimeMillis();
    assertTrue(DirectoryServer.getWorkQueue().waitUntilIdle(0));
    long stopTime = System.currentTimeMillis();
    assertTrue(stopTime - startTime <= 1000);
  }



  /**
   * Tests the {@code WorkQueue.waitUntilIdle()} method for a case in which the
   * work queue should not be idle for several seconds.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(groups = { "slow" })
  public void testWaitUntilIdleSlowOpInProgress()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    SearchRequest request = newSearchRequest(DN.valueOf("o=test"), SearchScope.BASE_OBJECT)
        .addControl(DelayPreOpPlugin.createDelayControlList(5000));
    InternalSearchOperation searchOperation =
        new InternalSearchOperation(getRootConnection(), nextOperationID(), nextMessageID(), request);
    DirectoryServer.getWorkQueue().submitOperation(searchOperation);

    long startTime = System.currentTimeMillis();
    assertTrue(DirectoryServer.getWorkQueue().waitUntilIdle(10000));
    long stopTime = System.currentTimeMillis();
    assertTrue(stopTime - startTime >= 4000);
  }

  /**
   * Tests the {@code WorkQueue.waitUntilIdle()} method for a case in which the
   * work queue should not be idle for several seconds.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(groups = { "slow" })
  public void testWaitUntilTimeoutWithIdleSlowOpInProgress()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    SearchRequest request = newSearchRequest(DN.valueOf("o=test"), SearchScope.BASE_OBJECT)
        .addControl(DelayPreOpPlugin.createDelayControlList(5000));
    InternalSearchOperation searchOperation =
        new InternalSearchOperation(getRootConnection(), nextOperationID(), nextMessageID(), request);
    DirectoryServer.getWorkQueue().submitOperation(searchOperation);

    long startTime = System.currentTimeMillis();
    assertFalse(DirectoryServer.getWorkQueue().waitUntilIdle(1000));
    long stopTime = System.currentTimeMillis();
    assertTrue(stopTime - startTime <= 2000);
  }
}

