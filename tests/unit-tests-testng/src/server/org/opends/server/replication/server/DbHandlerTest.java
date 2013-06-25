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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011 ForgeRock AS
 */
package org.opends.server.replication.server;

import java.io.File;
import java.net.ServerSocket;

import org.opends.server.TestCaseUtils;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.protocol.DeleteMsg;
import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;

/**
 * Test the dbHandler class
 */
public class DbHandlerTest extends ReplicationTestCase
{
  // The tracer object for the debug logger
  private static final DebugTracer TRACER = getTracer();
  /**
   * Utility - log debug message - highlight it is from the test and not
   * from the server code. Makes easier to observe the test steps.
   */
  private void debugInfo(String tn, String s)
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("** TEST " + tn + " ** " + s);
    }
  }

  @Test(enabled=true)
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
      String path = System.getProperty(TestCaseUtils.PROPERTY_BUILD_DIR,
              buildRoot + File.separator + "build");
      path = path + File.separator + "unit-tests" + File.separator + "dbHandler";
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
  @Test(enabled=true)
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
      String path = System.getProperty(TestCaseUtils.PROPERTY_BUILD_DIR,
              buildRoot + File.separator + "build");
      path = path + File.separator + "unit-tests" + File.separator + "dbHandler";
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
  /**
   * Test the logic that manages counter records in the DbHandler in order to
   * optimize the counting of record in the replication changelog db.
   * @throws Exception
   */
  @Test(enabled=true, groups = { "opendj-256" })
  void testDbCounts() throws Exception
  {
    // FIXME: for some reason this test is always failing in Jenkins when run as
    // part of the unit tests. Here is the output (the failure is 100%
    // reproducible and always has the same value of 3004):
    //
    // Failed Test:
    // org.opends.server.replication.server.DbHandlerTest#testDbCounts
    // [testng] Failure Cause: java.lang.AssertionError: AFTER PURGE
    // expected:<8000> but was:<3004>
    // [testng] org.testng.Assert.fail(Assert.java:84)
    // [testng] org.testng.Assert.failNotEquals(Assert.java:438)
    // [testng] org.testng.Assert.assertEquals(Assert.java:108)
    // [testng] org.testng.Assert.assertEquals(Assert.java:323)
    // [testng]
    // org.opends.server.replication.server.DbHandlerTest.testDBCount(DbHandlerTest.java:594)
    // [testng]
    // org.opends.server.replication.server.DbHandlerTest.testDbCounts(DbHandlerTest.java:389)

    // It's worth testing with 2 different setting for counterRecord
    // - a counter record is put every 10 Update msg in the db - just a unit
    //   setting.
    // - a counter record is put every 1000 Update msg in the db - something
    //   closer to real setting.
    // In both cases, we want to test the counting algorithm,
    // - when start and stop are before the first counter record,
    // - when start and stop are before and after the first counter record,
    // - when start and stop are after the first counter record,
    // - when start and stop are before and after more than one counter record,
    // After a purge.
    // After shutdowning/closing and reopening the db.
    testDBCount(40, 10);
    testDBCount(4000, 1000);
  }

  private void testDBCount(int max, int counterWindow) throws Exception
  {
    String tn = "testDBCount("+max+","+counterWindow+")";
    debugInfo(tn, "Starting test");

    File testRoot = null;
    ReplicationServer replicationServer = null;
    ReplicationDbEnv dbEnv = null;
    DbHandler handler = null;
    ReplicationIterator ri = null;
    int actualCnt = 0;
    String testcase;
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
            2, 0, 100000, null);
      replicationServer = new ReplicationServer(conf);

      // create or clean a directory for the dbHandler
      String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
      String path = System.getProperty(TestCaseUtils.PROPERTY_BUILD_DIR,
              buildRoot + File.separator + "build");
      path = path + File.separator + "unit-tests" + File.separator + "dbHandler";
      testRoot = new File(path);
      if (testRoot.exists())
      {
        TestCaseUtils.deleteDirectory(testRoot);
      }
      testRoot.mkdirs();

      dbEnv = new ReplicationDbEnv(path, replicationServer);

      // Create the handler
      handler =
        new DbHandler( 1, TEST_ROOT_DN_STRING,
            replicationServer, dbEnv, 10);
      handler.setCounterWindowSize(counterWindow);

      // Populate the db with 'max' msg
      int mySeqnum = 1;
      ChangeNumber cnarray[] = new ChangeNumber[2*(max+1)];
      long now = System.currentTimeMillis();
      for (int i=1; i<=max; i++)
      {
        cnarray[i] = new ChangeNumber(now+i, mySeqnum, 1);
        mySeqnum+=2;
        DeleteMsg update1 = new DeleteMsg(TEST_ROOT_DN_STRING, cnarray[i], "uid");
        handler.add(update1);
      }
      handler.flush();

      // Test first and last
      ChangeNumber cn1 = handler.getFirstChange();
      assertEquals(cn1, cnarray[1], "First change");
      ChangeNumber cnlast = handler.getLastChange();
      assertEquals(cnlast, cnarray[max], "Last change");

      // Test count in different subcases trying to handle all special cases
      // regarding the 'counter' record and 'count' algorithm
      testcase="FROM change1 TO change1 ";
      actualCnt = handler.getCount(cnarray[1], cnarray[1]);
      debugInfo(tn,testcase + " actualCnt=" + actualCnt);
      assertEquals(actualCnt, 1, testcase);

      testcase="FROM change1 TO change2 ";
      actualCnt = handler.getCount(cnarray[1], cnarray[2]);
      debugInfo(tn,testcase + " actualCnt=" + actualCnt);
      assertEquals(actualCnt, 2, testcase);

      testcase="FROM change1 TO counterWindow="+(counterWindow);
      actualCnt = handler.getCount(cnarray[1], cnarray[counterWindow]);
      debugInfo(tn,testcase + " actualCnt=" + actualCnt);
      assertEquals(actualCnt, counterWindow, testcase);

      testcase="FROM change1 TO counterWindow+1="+(counterWindow+1);
      actualCnt = handler.getCount(cnarray[1], cnarray[counterWindow+1]);
      debugInfo(tn,testcase + " actualCnt=" + actualCnt);
      assertEquals(actualCnt, counterWindow+1, testcase);

      testcase="FROM change1 TO 2*counterWindow="+(2*counterWindow);
      actualCnt = handler.getCount(cnarray[1], cnarray[2*counterWindow]);
      debugInfo(tn,testcase + " actualCnt=" + actualCnt);
      assertEquals(actualCnt, 2*counterWindow, testcase);

      testcase="FROM change1 TO 2*counterWindow+1="+((2*counterWindow)+1);
      actualCnt = handler.getCount(cnarray[1], cnarray[(2*counterWindow)+1]);
      debugInfo(tn,testcase + " actualCnt=" + actualCnt);
      assertEquals(actualCnt, (2*counterWindow)+1, testcase);

      testcase="FROM change2 TO change5 ";
      actualCnt = handler.getCount(cnarray[2], cnarray[5]);
      debugInfo(tn,testcase + " actualCnt=" + actualCnt);
      assertEquals(actualCnt, 4, testcase);

      testcase="FROM counterWindow+2 TO counterWindow+5 ";
      actualCnt = handler.getCount(cnarray[(counterWindow+2)], cnarray[(counterWindow+5)]);
      debugInfo(tn,testcase + " actualCnt=" + actualCnt);
      assertEquals(actualCnt, 4, testcase);

      testcase="FROM change2 TO counterWindow+5 ";
      actualCnt = handler.getCount(cnarray[2], cnarray[(counterWindow+5)]);
      debugInfo(tn,testcase + " actualCnt=" + actualCnt);
      assertEquals(actualCnt, counterWindow+4, testcase);

      testcase="FROM counterWindow+4 TO counterWindow+4 ";
      actualCnt = handler.getCount(cnarray[(counterWindow+4)], cnarray[(counterWindow+4)]);
      debugInfo(tn,testcase + " actualCnt=" + actualCnt);
      assertEquals(actualCnt, 1, testcase);

      // Now test with changes older than first or newer than last
      ChangeNumber olderThanFirst = null;
      ChangeNumber newerThanLast =
        new ChangeNumber(System.currentTimeMillis() + (2*(max+1)), 100, 1);

      // Now we want to test with start and stop outside of the db

      testcase="FROM our first generated change TO now (> newest change in the db)";
      actualCnt = handler.getCount(cnarray[1], newerThanLast);
      debugInfo(tn,testcase + " actualCnt=" + actualCnt);
      assertEquals(actualCnt, max, testcase);

      testcase="FROM null (start of time) TO now (> newest change in the db)";
      actualCnt = handler.getCount(olderThanFirst, newerThanLast);
      debugInfo(tn,testcase + " actualCnt=" + actualCnt);
      assertEquals(actualCnt, max, testcase);

      // Now we want to test that after closing and reopening the db, the
      // counting algo is well reinitialized and when new messages are added
      // the new counter are correctly generated.
      debugInfo(tn,"SHUTDOWN handler and recreate");
      handler.shutdown();

      handler =
        new DbHandler( 1, TEST_ROOT_DN_STRING,
            replicationServer, dbEnv, 10);
      handler.setCounterWindowSize(counterWindow);

      // Test first and last
      cn1 = handler.getFirstChange();
      assertEquals(cn1, cnarray[1], "First change");
      cnlast = handler.getLastChange();
      assertEquals(cnlast, cnarray[max], "Last change");

      testcase="FROM our first generated change TO now (> newest change in the db)";
      actualCnt = handler.getCount(cnarray[1], newerThanLast);
      debugInfo(tn,testcase + " actualCnt=" + actualCnt);
      assertEquals(actualCnt, max, testcase);

      // Populate the db with 'max' msg
      for (int i=max+1; i<=(2*max); i++)
      {
        cnarray[i] = new ChangeNumber(now+i, mySeqnum, 1);
        mySeqnum+=2;
        DeleteMsg update1 = new DeleteMsg(TEST_ROOT_DN_STRING, cnarray[i], "uid");
        handler.add(update1);
      }
      handler.flush();

      // Test first and last
      cn1 = handler.getFirstChange();
      assertEquals(cn1, cnarray[1], "First change");
      cnlast = handler.getLastChange();
      assertEquals(cnlast, cnarray[2*max], "Last change");

      testcase="FROM our first generated change TO now (> newest change in the db)";
      actualCnt = handler.getCount(cnarray[1], newerThanLast);
      debugInfo(tn,testcase + " actualCnt=" + actualCnt);
      assertEquals(actualCnt, (2*max), testcase);

      //

      handler.setPurgeDelay(100);
      sleep(4000);
      int totalCount = handler.getCount(null, null);
      debugInfo(tn,testcase + " After purge, total count=" + totalCount);

      testcase="AFTER PURGE (first, last)=";
      debugInfo(tn,testcase + handler.getFirstChange() + handler.getLastChange());
      assertEquals(handler.getLastChange(), cnarray[2*max], "Last=");

      testcase="AFTER PURGE ";
      actualCnt = handler.getCount(cnarray[1], newerThanLast);
      int expectedCnt;
      if (totalCount>1)
      {
        expectedCnt = ((handler.getLastChange().getSeqnum()
                    - handler.getFirstChange().getSeqnum() + 1)/2)+1;
      }
      else
      {
        expectedCnt = 0;
      }
      debugInfo(tn,testcase + " actualCnt=" + actualCnt);
      assertEquals(actualCnt, expectedCnt, testcase);

      // Clear ...
      debugInfo(tn,"clear:");
      handler.clear();

      // Check the db is cleared.
      assertEquals(null, handler.getFirstChange());
      assertEquals(null, handler.getLastChange());
      debugInfo(tn,"Success");

    }
    finally
    {
      if (ri!=null)
        ri.releaseCursor();
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
