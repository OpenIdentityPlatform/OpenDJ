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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.replication.server;

import java.io.File;
import java.net.ServerSocket;

import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.protocol.DeleteMsg;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static org.opends.server.TestCaseUtils.*;

/**
 * Test the dbHandler class
 */
public class DbHandlerTest extends ReplicationTestCase
{
  @Test()
  void testDbHandlerTrim() throws Exception
  {
    File testRoot = null;
    ReplicationServer replicationServer = null;
    ReplicationDbEnv dbEnv = null;
    DbHandler handler = null;
    ReplicationIterator it = null;
    try
    {
      TestCaseUtils.startServer();

      //  find  a free port for the replicationServer
      ServerSocket socket = TestCaseUtils.bindFreePort();
      int changelogPort = socket.getLocalPort();
      socket.close();

      // configure a ReplicationServer.
      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(changelogPort, null, 0,
        2, 0, 100, null);
      replicationServer = new ReplicationServer(conf);

      // create or clean a directory for the dbHandler
      String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
      String path = buildRoot + File.separator + "build" + File.separator +
        "unit-tests" + File.separator + "dbHandler";
      testRoot = new File(path);
      if (testRoot.exists())
      {
        TestCaseUtils.deleteDirectory(testRoot);
      }
      testRoot.mkdirs();

      dbEnv = new ReplicationDbEnv(path, replicationServer);

      handler = new DbHandler(1, TEST_ROOT_DN_STRING,
        replicationServer, dbEnv, 5000);

      ChangeNumberGenerator gen = new ChangeNumberGenerator( 1, 0);
      ChangeNumber changeNumber1 = gen.newChangeNumber();
      ChangeNumber changeNumber2 = gen.newChangeNumber();
      ChangeNumber changeNumber3 = gen.newChangeNumber();
      ChangeNumber changeNumber4 = gen.newChangeNumber();
      ChangeNumber changeNumber5 = gen.newChangeNumber();

      DeleteMsg update1 = new DeleteMsg(TEST_ROOT_DN_STRING, changeNumber1,
        "uid");
      DeleteMsg update2 = new DeleteMsg(TEST_ROOT_DN_STRING, changeNumber2,
        "uid");
      DeleteMsg update3 = new DeleteMsg(TEST_ROOT_DN_STRING, changeNumber3,
      "uid");
      DeleteMsg update4 = new DeleteMsg(TEST_ROOT_DN_STRING, changeNumber4,
      "uid");

      handler.add(update1);
      handler.add(update2);
      handler.add(update3);

      //--
      // Iterator tests with memory queue only populated

      // verify that memory queue is populated
      assertEquals(handler.getQueueSize(),3);

      // Iterator from existing CN
      it = handler.generateIterator(changeNumber1);
      assertTrue(it.next());
      assertTrue(it.getChange().getChangeNumber().compareTo(changeNumber2)==0,
          " Actual change number=" + it.getChange().getChangeNumber() +
          " Expect change number=" + changeNumber2);
      assertTrue(it.next());
      assertTrue(it.getChange().getChangeNumber().compareTo(changeNumber3)==0,
          " Actual change number=" + it.getChange().getChangeNumber());
      assertFalse(it.next());
      it.releaseCursor();
      it=null;

      // Iterator from NON existing CN
      Exception ec = null;
      try
      {
        it = handler.generateIterator(changeNumber5);
      }
      catch(Exception e)
      {
        ec = e;
      }
      assertNotNull(ec);
      assert(ec.getLocalizedMessage().equals("ChangeNumber not available"));

      //--
      // Iterator tests with db only populated
      Thread.sleep(1000); // let the time for flush to happen

      // verify that memory queue is empty (all changes flushed in the db)
      assertEquals(handler.getQueueSize(),0);

      // Test iterator from existing CN
      it = handler.generateIterator(changeNumber1);
      assertTrue(it.next());
      assertTrue(it.getChange().getChangeNumber().compareTo(changeNumber2)==0,
          " Actual change number=" + it.getChange().getChangeNumber());
      assertTrue(it.next());
      assertTrue(it.getChange().getChangeNumber().compareTo(changeNumber3)==0,
          " Actual change number=" + it.getChange().getChangeNumber());
      assertFalse(it.next());
      it.releaseCursor();
      it=null;

      // Iterator from NON existing CN
      ec = null;
      try
      {
        it = handler.generateIterator(changeNumber5);
      }
      catch(Exception e)
      {
        ec = e;
      }
      assertNotNull(ec);
      assert(ec.getLocalizedMessage().equals("ChangeNumber not available"));

      // Test first and last
      assertEquals(changeNumber1, handler.getFirstChange());
      assertEquals(changeNumber3, handler.getLastChange());

      //--
      // Iterator tests with db and memory queue populated
      // all changes in the db - add one in the memory queue
      handler.add(update4);

      // verify memory queue contains this one
      assertEquals(handler.getQueueSize(),1);

      // Test iterator from existing CN
      it = handler.generateIterator(changeNumber1);
      assertTrue(it.next());
      assertTrue(it.getChange().getChangeNumber().compareTo(changeNumber2)==0,
          " Actual change number=" + it.getChange().getChangeNumber());
      assertTrue(it.next());
      assertTrue(it.getChange().getChangeNumber().compareTo(changeNumber3)==0,
          " Actual change number=" + it.getChange().getChangeNumber());
      assertTrue(it.next());
      assertTrue(it.getChange().getChangeNumber().compareTo(changeNumber4)==0,
          " Actual change number=" + it.getChange().getChangeNumber());
      assertFalse(it.next());
      assertTrue(it.getChange()==null);
      it.releaseCursor();
      it=null;

      // Test iterator from existing CN at the limit between queue and db
      it = handler.generateIterator(changeNumber3);
      assertTrue(it.next());
      assertTrue(it.getChange().getChangeNumber().compareTo(changeNumber4)==0,
          " Actual change number=" + it.getChange().getChangeNumber());
      assertFalse(it.next());
      assertTrue(it.getChange()==null);
      it.releaseCursor();
      it=null;

      // Test iterator from existing CN at the limit between queue and db
      it = handler.generateIterator(changeNumber4);
      assertFalse(it.next());
      assertTrue(it.getChange()==null,
          " Actual change number=" + it.getChange());
      it.releaseCursor();
      it=null;

      // Test iterator from NON existing CN
      ec = null;
      try
      {
        it = handler.generateIterator(changeNumber5);
      }
      catch(Exception e)
      {
        ec = e;
      }
      assertNotNull(ec);
      assert(ec.getLocalizedMessage().equals("ChangeNumber not available"));

      handler.setPurgeDelay(1);

      boolean purged = false;
      int count = 300;  // wait at most 60 seconds
      while (!purged && (count > 0))
      {
        ChangeNumber firstChange = handler.getFirstChange();
        ChangeNumber lastChange = handler.getLastChange();
        if ((!firstChange.equals(changeNumber4) ||
          (!lastChange.equals(changeNumber4))))
        {
          TestCaseUtils.sleep(100);
        } else
        {
          purged = true;
        }
      }
    } finally
    {
      if (it != null)
      {
        it.releaseCursor();
        it=null;
      }
      if (handler != null)
        handler.shutdown();
      if (dbEnv != null)
        dbEnv.shutdown();
      if (replicationServer != null)
      replicationServer.remove();
      if (testRoot != null)
        TestCaseUtils.deleteDirectory(testRoot);
    }
  }

  /*
   * Test the feature of clearing a dbHandler used by a replication server.
   * The clear feature is used when a replication server receives a request
   * to reset the generationId of a given domain.
   */
  @Test()
  void testDbHandlerClear() throws Exception
  {
    File testRoot = null;
    ReplicationServer replicationServer = null;
    ReplicationDbEnv dbEnv = null;
    DbHandler handler = null;
    try
    {
      TestCaseUtils.startServer();

      //  find  a free port for the replicationServer
      ServerSocket socket = TestCaseUtils.bindFreePort();
      int changelogPort = socket.getLocalPort();
      socket.close();

      // configure a ReplicationServer.
      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(changelogPort, null, 0,
        2, 0, 100, null);
      replicationServer = new ReplicationServer(conf);

      // create or clean a directory for the dbHandler
      String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
      String path = buildRoot + File.separator + "build" + File.separator +
        "unit-tests" + File.separator + "dbHandler";
      testRoot = new File(path);
      if (testRoot.exists())
      {
        TestCaseUtils.deleteDirectory(testRoot);
      }
      testRoot.mkdirs();

      dbEnv = new ReplicationDbEnv(path, replicationServer);

      handler =
        new DbHandler( 1, TEST_ROOT_DN_STRING,
        replicationServer, dbEnv, 5000);

      // Creates changes added to the dbHandler
      ChangeNumberGenerator gen = new ChangeNumberGenerator( 1, 0);
      ChangeNumber changeNumber1 = gen.newChangeNumber();
      ChangeNumber changeNumber2 = gen.newChangeNumber();
      ChangeNumber changeNumber3 = gen.newChangeNumber();

      DeleteMsg update1 = new DeleteMsg(TEST_ROOT_DN_STRING, changeNumber1,
        "uid");
      DeleteMsg update2 = new DeleteMsg(TEST_ROOT_DN_STRING, changeNumber2,
        "uid");
      DeleteMsg update3 = new DeleteMsg(TEST_ROOT_DN_STRING, changeNumber3,
        "uid");

      // Add the changes
      handler.add(update1);
      handler.add(update2);
      handler.add(update3);

      // Check they are here
      assertEquals(changeNumber1, handler.getFirstChange());
      assertEquals(changeNumber3, handler.getLastChange());

      // Clear ...
      handler.clear();

      // Check the db is cleared.
      assertEquals(null, handler.getFirstChange());
      assertEquals(null, handler.getLastChange());

    } finally
    {
      if (handler != null)
        handler.shutdown();
      if (dbEnv != null)
        dbEnv.shutdown();
      if (replicationServer != null)
        replicationServer.remove();
      if (testRoot != null)
        TestCaseUtils.deleteDirectory(testRoot);
    }
  }
}
