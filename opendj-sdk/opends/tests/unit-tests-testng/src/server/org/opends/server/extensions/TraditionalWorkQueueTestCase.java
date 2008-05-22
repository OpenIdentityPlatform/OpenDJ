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
package org.opends.server.extensions;



import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.api.WorkQueue;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.plugins.DelayPreOpPlugin;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.Control;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DN;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchScope;

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
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Tests to ensure that the work queue is configured and enabled within the
   * Directory Server.
   */
  @Test()
  public void testWorkQueueEnabled()
  {
    WorkQueue workQueue = DirectoryServer.getWorkQueue();
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
    DN     dn   = DN.decode("cn=Work Queue,cn=config");
    String attr = "ds-cfg-num-worker-threads";
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.REPLACE,
                              new Attribute(attr, "30")));

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    ModifyOperation modifyOperation = conn.processModify(dn, mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

    mods.clear();
    mods.add(new Modification(ModificationType.REPLACE,
                              new Attribute(attr, "24")));
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
      "1.1"
    };

    for (int i=0; i < 7; i++)
    {
      assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
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
    assertTrue((stopTime - startTime) <= 1000);
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
    assertTrue((stopTime - startTime) <= 1000);
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

    List<Control> requestControls =
         DelayPreOpPlugin.createDelayControlList(5000);
    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrs = new LinkedHashSet<String>();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                                     conn.nextMessageID(),requestControls,
                                     DN.decode("o=test"),
                                     SearchScope.BASE_OBJECT,
                                     DereferencePolicy.NEVER_DEREF_ALIASES, 0,
                                     0, false, filter, attrs, null);
    DirectoryServer.getWorkQueue().submitOperation(searchOperation);

    long startTime = System.currentTimeMillis();
    assertTrue(DirectoryServer.getWorkQueue().waitUntilIdle(10000));
    long stopTime = System.currentTimeMillis();
    assertTrue((stopTime - startTime) >= 4000);
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

    List<Control> requestControls =
         DelayPreOpPlugin.createDelayControlList(5000);
    SearchFilter filter =
         SearchFilter.createFilterFromString("(objectClass=*)");
    LinkedHashSet<String> attrs = new LinkedHashSet<String>();

    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                                     conn.nextMessageID(), requestControls,
                                     DN.decode("o=test"),
                                     SearchScope.BASE_OBJECT,
                                     DereferencePolicy.NEVER_DEREF_ALIASES, 0,
                                     0, false, filter, attrs, null);
    DirectoryServer.getWorkQueue().submitOperation(searchOperation);

    long startTime = System.currentTimeMillis();
    assertFalse(DirectoryServer.getWorkQueue().waitUntilIdle(1000));
    long stopTime = System.currentTimeMillis();
    assertTrue((stopTime - startTime) <= 2000);
  }
}

