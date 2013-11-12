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
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.io.File;
import java.io.IOException;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.server.ReplicationServerCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.types.DN;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.testng.Assert.*;

/**
 * Test the JEReplicaDB class
 */
@SuppressWarnings("javadoc")
public class JEReplicaDBTest extends ReplicationTestCase
{
  /** The tracer object for the debug logger */
  private static final DebugTracer TRACER = getTracer();
  private DN TEST_ROOT_DN;

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

  @BeforeClass
  public void setup() throws Exception
  {
    TEST_ROOT_DN = DN.decode(TEST_ROOT_DN_STRING);
  }

  @Test(enabled=true)
  void testTrim() throws Exception
  {
    ReplicationServer replicationServer = null;
    try
    {
      TestCaseUtils.startServer();
      replicationServer = configureReplicationServer(100, 5000);
      JEReplicaDB replicaDB = newReplicaDB(replicationServer);

      CSN[] csns = newCSNs(1, 0, 5);

      replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[0], "uid"));
      replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[1], "uid"));
      replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[2], "uid"));
      DeleteMsg update4 = new DeleteMsg(TEST_ROOT_DN, csns[3], "uid");

      //--
      // Iterator tests with memory queue only populated

      // verify that memory queue is populated
      assertEquals(replicaDB.getQueueSize(), 3);

      assertFoundInOrder(replicaDB, csns[0], csns[1], csns[2]);
      assertNotFound(replicaDB, csns[4]);

      //--
      // Iterator tests with db only populated
      Thread.sleep(1000); // let the time for flush to happen

      // verify that memory queue is empty (all changes flushed in the db)
      assertEquals(replicaDB.getQueueSize(), 0);

      assertFoundInOrder(replicaDB, csns[0], csns[1], csns[2]);
      assertNotFound(replicaDB, csns[4]);

      assertEquals(replicaDB.getOldestCSN(), csns[0]);
      assertEquals(replicaDB.getNewestCSN(), csns[2]);

      //--
      // Cursor tests with db and memory queue populated
      // all changes in the db - add one in the memory queue
      replicaDB.add(update4);

      // verify memory queue contains this one
      assertEquals(replicaDB.getQueueSize(), 1);

      assertFoundInOrder(replicaDB, csns[0], csns[1], csns[2], csns[3]);
      // Test cursor from existing CSN at the limit between queue and db
      assertFoundInOrder(replicaDB, csns[2], csns[3]);
      assertFoundInOrder(replicaDB, csns[3]);
      assertNotFound(replicaDB, csns[4]);

      replicaDB.setPurgeDelay(1);

      boolean purged = false;
      int count = 300;  // wait at most 60 seconds
      while (!purged && (count > 0))
      {
        CSN oldestCSN = replicaDB.getOldestCSN();
        CSN newestCSN = replicaDB.getNewestCSN();
        if (!oldestCSN.equals(csns[3]) || !newestCSN.equals(csns[3]))
        {
          TestCaseUtils.sleep(100);
        } else
        {
          purged = true;
        }
      }
      // FIXME should add an assert here
    }
    finally
    {
      remove(replicationServer);
    }
  }

  static CSN[] newCSNs(int serverId, long timestamp, int number)
  {
    CSNGenerator gen = new CSNGenerator(serverId, timestamp);
    CSN[] csns = new CSN[number];
    for (int i = 0; i < csns.length; i++)
    {
      csns[i] = gen.newCSN();
    }
    return csns;
  }

  private ReplicationServer configureReplicationServer(int windowSize, int queueSize)
      throws IOException, ConfigException
  {
    final int changelogPort = findFreePort();
    final ReplicationServerCfg conf =
        new ReplServerFakeConfiguration(changelogPort, null, 0, 2, queueSize, windowSize, null);
    return new ReplicationServer(conf);
  }

  private JEReplicaDB newReplicaDB(ReplicationServer replicationServer) throws Exception
  {
    JEChangelogDB changelogDB = (JEChangelogDB) replicationServer.getChangelogDB();
    return changelogDB.getOrCreateReplicaDB(TEST_ROOT_DN, 1, replicationServer).getFirst();
  }

  private File createCleanDir() throws IOException
  {
    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    String path = System.getProperty(TestCaseUtils.PROPERTY_BUILD_DIR, buildRoot
            + File.separator + "build");
    path = path + File.separator + "unit-tests" + File.separator + "JEReplicaDB";
    final File testRoot = new File(path);
    TestCaseUtils.deleteDirectory(testRoot);
    testRoot.mkdirs();
    return testRoot;
  }

  private void assertFoundInOrder(JEReplicaDB replicaDB, CSN... csns) throws Exception
  {
    if (csns.length == 0)
    {
      return;
    }

    DBCursor<UpdateMsg> cursor = replicaDB.generateCursorFrom(csns[0]);
    try
    {
      assertNull(cursor.getRecord());
      for (int i = 1; i < csns.length; i++)
      {
        assertTrue(cursor.next());
        assertEquals(cursor.getRecord().getCSN(), csns[i]);
      }
      assertFalse(cursor.next());
      assertNull(cursor.getRecord(), "Actual change=" + cursor.getRecord()
          + ", Expected null");
    }
    finally
    {
      StaticUtils.close(cursor);
    }
  }

  private void assertNotFound(JEReplicaDB replicaDB, CSN csn)
  {
    DBCursor<UpdateMsg> cursor = null;
    try
    {
      cursor = replicaDB.generateCursorFrom(csn);
      fail("Expected exception");
    }
    catch (ChangelogException e)
    {
      assertEquals(e.getLocalizedMessage(), "CSN not available");
    }
    finally
    {
      StaticUtils.close(cursor);
    }
  }

  /**
   * Test the feature of clearing a JEReplicaDB used by a replication server.
   * The clear feature is used when a replication server receives a request to
   * reset the generationId of a given domain.
   */
  @Test(enabled=true)
  void testClear() throws Exception
  {
    ReplicationServer replicationServer = null;
    try
    {
      TestCaseUtils.startServer();
      replicationServer = configureReplicationServer(100, 5000);
      JEReplicaDB replicaDB = newReplicaDB(replicationServer);

      CSN[] csns = newCSNs(1, 0, 3);

      // Add the changes
      replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[0], "uid"));
      replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[1], "uid"));
      replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[2], "uid"));

      // Check they are here
      assertEquals(csns[0], replicaDB.getOldestCSN());
      assertEquals(csns[2], replicaDB.getNewestCSN());

      // Clear ...
      replicaDB.clear();

      // Check the db is cleared.
      assertEquals(null, replicaDB.getOldestCSN());
      assertEquals(null, replicaDB.getNewestCSN());

    }
    finally
    {
      remove(replicationServer);
    }
  }

  @Test
  public void testGenerateCursorFrom() throws Exception
  {
    ReplicationServer replicationServer = null;
    DBCursor<UpdateMsg> cursor = null;
    try
    {
      TestCaseUtils.startServer();
      replicationServer = configureReplicationServer(100000, 10);
      JEReplicaDB replicaDB = newReplicaDB(replicationServer);

      CSN[] csns = newCSNs(1, System.currentTimeMillis(), 6);
      for (int i = 0; i < 5; i++)
      {
        if (i != 3)
        {
          replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[i], "uid"));
        }
      }
      replicaDB.flush();

      cursor = replicaDB.generateCursorFrom(csns[0]);
      assertTrue(cursor.next());
      assertEquals(cursor.getRecord().getCSN(), csns[1]);
      StaticUtils.close(cursor);

      cursor = replicaDB.generateCursorFrom(csns[3]);
      assertTrue(cursor.next());
      assertEquals(cursor.getRecord().getCSN(), csns[4]);
      StaticUtils.close(cursor);

      cursor = replicaDB.generateCursorFrom(csns[4]);
      assertFalse(cursor.next());
      assertNull(cursor.getRecord());
    }
    finally
    {
      StaticUtils.close(cursor);
      remove(replicationServer);
    }
  }

  @Test
  public void testGetCountNoCounterRecords() throws Exception
  {
    ReplicationServer replicationServer = null;
    try
    {
      TestCaseUtils.startServer();
      replicationServer = configureReplicationServer(100000, 10);
      JEReplicaDB replicaDB = newReplicaDB(replicationServer);

      CSN[] csns = newCSNs(1, System.currentTimeMillis(), 5);
      for (CSN csn : csns)
      {
        replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csn, "uid"));
      }
      replicaDB.flush();

      assertEquals(replicaDB.getCount(csns[0], csns[0]), 1);
      assertEquals(replicaDB.getCount(csns[0], csns[1]), 2);
      assertEquals(replicaDB.getCount(csns[0], csns[4]), 5);
      assertEquals(replicaDB.getCount(null, csns[4]), 5);
      assertEquals(replicaDB.getCount(csns[0], null), 0);
      assertEquals(replicaDB.getCount(null, null), 5);
    }
    finally
    {
      remove(replicationServer);
    }
  }

  /**
   * Test the logic that manages counter records in the JEReplicaDB in order to
   * optimize the counting of record in the replication changelog db.
   */
  @Test(enabled=true, groups = { "opendj-256" })
  void testDbCounts() throws Exception
  {
    // FIXME: for some reason this test is always failing in Jenkins when run as
    // part of the unit tests. Here is the output (the failure is 100%
    // reproducible and always has the same value of 3004):
    //
    // Failed Test:
    // org.opends.server.replication.server.JEReplicaDBTest#testDbCounts
    // [testng] Failure Cause: java.lang.AssertionError: AFTER PURGE
    // expected:<8000> but was:<3004>
    // [testng] org.testng.Assert.fail(Assert.java:84)
    // [testng] org.testng.Assert.failNotEquals(Assert.java:438)
    // [testng] org.testng.Assert.assertEquals(Assert.java:108)
    // [testng] org.testng.Assert.assertEquals(Assert.java:323)
    // [testng]
    // org.opends.server.replication.server.JEReplicaDBTest.testDBCount(JEReplicaDBTest.java:594)
    // [testng]
    // org.opends.server.replication.server.JEReplicaDBTest.testDbCounts(JEReplicaDBTest.java:389)

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
    // FIXME next line is the one failing with the stacktrace above
    testDBCount(4000, 1000);
  }

  private void testDBCount(int max, int counterWindow) throws Exception
  {
    String tn = "testDBCount("+max+","+counterWindow+")";
    debugInfo(tn, "Starting test");

    File testRoot = null;
    ReplicationServer replicationServer = null;
    ReplicationDbEnv dbEnv = null;
    JEReplicaDB replicaDB = null;
    try
    {
      TestCaseUtils.startServer();
      replicationServer = configureReplicationServer(100000, 10);

      testRoot = createCleanDir();
      dbEnv = new ReplicationDbEnv(testRoot.getPath(), replicationServer);
      replicaDB = new JEReplicaDB(1, TEST_ROOT_DN, replicationServer, dbEnv);
      replicaDB.setCounterRecordWindowSize(counterWindow);

      // Populate the db with 'max' msg
      int mySeqnum = 1;
      CSN csns[] = new CSN[2 * (max + 1)];
      long now = System.currentTimeMillis();
      for (int i=1; i<=max; i++)
      {
        csns[i] = new CSN(now + i, mySeqnum, 1);
        replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[i], "uid"));
        mySeqnum+=2;
      }
      replicaDB.flush();

      assertEquals(replicaDB.getOldestCSN(), csns[1], "Wrong oldest CSN");
      assertEquals(replicaDB.getNewestCSN(), csns[max], "Wrong newest CSN");

      // Test count in different subcases trying to handle all special cases
      // regarding the 'counter' record and 'count' algorithm
      assertCount(tn, replicaDB, csns[1], csns[1], 1, "FROM change1 TO change1 ");
      assertCount(tn, replicaDB, csns[1], csns[2], 2, "FROM change1 TO change2 ");
      assertCount(tn, replicaDB, csns[1], csns[counterWindow], counterWindow,
          "FROM change1 TO counterWindow=" + counterWindow);

      final int j = counterWindow + 1;
      assertCount(tn, replicaDB, csns[1], csns[j], j,
          "FROM change1 TO counterWindow+1=" + j);
      final int k = 2 * counterWindow;
      assertCount(tn, replicaDB, csns[1], csns[k], k,
          "FROM change1 TO 2*counterWindow=" + k);
      final int l = k + 1;
      assertCount(tn, replicaDB, csns[1], csns[l], l,
          "FROM change1 TO 2*counterWindow+1=" + l);
      assertCount(tn, replicaDB, csns[2], csns[5], 4,
          "FROM change2 TO change5 ");
      assertCount(tn, replicaDB, csns[(counterWindow + 2)], csns[(counterWindow + 5)], 4,
          "FROM counterWindow+2 TO counterWindow+5 ");
      assertCount(tn, replicaDB, csns[2], csns[(counterWindow + 5)], counterWindow + 4,
          "FROM change2 TO counterWindow+5 ");
      assertCount(tn, replicaDB, csns[(counterWindow + 4)], csns[(counterWindow + 4)], 1,
          "FROM counterWindow+4 TO counterWindow+4 ");

      CSN olderThanOldest = null;
      CSN newerThanNewest = new CSN(System.currentTimeMillis() + (2*(max+1)), 100, 1);

      // Now we want to test with start and stop outside of the db

      assertCount(tn, replicaDB, csns[1], newerThanNewest, max,
          "FROM our first generated change TO now (> newest change in the db)");
      assertCount(tn, replicaDB, olderThanOldest, newerThanNewest, max,
          "FROM null (start of time) TO now (> newest change in the db)");

      // Now we want to test that after closing and reopening the db, the
      // counting algo is well reinitialized and when new messages are added
      // the new counter are correctly generated.
      debugInfo(tn, "SHUTDOWN replicaDB and recreate");
      replicaDB.shutdown();

      replicaDB = new JEReplicaDB(1, TEST_ROOT_DN, replicationServer, dbEnv);
      replicaDB.setCounterRecordWindowSize(counterWindow);

      assertEquals(replicaDB.getOldestCSN(), csns[1], "Wrong oldest CSN");
      assertEquals(replicaDB.getNewestCSN(), csns[max], "Wrong newest CSN");

      assertCount(tn, replicaDB, csns[1], newerThanNewest, max,
          "FROM our first generated change TO now (> newest change in the db)");

      // Populate the db with 'max' msg
      for (int i=max+1; i<=(2*max); i++)
      {
        csns[i] = new CSN(now + i, mySeqnum, 1);
        replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[i], "uid"));
        mySeqnum+=2;
      }
      replicaDB.flush();

      assertEquals(replicaDB.getOldestCSN(), csns[1], "Wrong oldest CSN");
      assertEquals(replicaDB.getNewestCSN(), csns[2 * max], "Wrong newest CSN");

      assertCount(tn, replicaDB, csns[1], newerThanNewest, 2 * max,
          "FROM our first generated change TO now (> newest change in the db)");

      //

      replicaDB.setPurgeDelay(100);
      sleep(4000);
      long totalCount = replicaDB.getCount(null, null);
      debugInfo(tn, "FROM our first generated change TO now (> newest change in the db)" + " After purge, total count=" + totalCount);

      String testcase = "AFTER PURGE (oldest, newest)=";
      debugInfo(tn, testcase + replicaDB.getOldestCSN() + replicaDB.getNewestCSN());
      assertEquals(replicaDB.getNewestCSN(), csns[2 * max], "Newest=");

      int expectedCnt;
      if (totalCount>1)
      {
        final int newestSeqnum = replicaDB.getNewestCSN().getSeqnum();
        final int oldestSeqnum = replicaDB.getOldestCSN().getSeqnum();
        expectedCnt = ((newestSeqnum - oldestSeqnum + 1)/2) + 1;
      }
      else
      {
        expectedCnt = 0;
      }
      assertCount(tn, replicaDB, csns[1], newerThanNewest, expectedCnt, "AFTER PURGE");

      // Clear ...
      debugInfo(tn,"clear:");
      replicaDB.clear();

      // Check the db is cleared.
      assertEquals(null, replicaDB.getOldestCSN());
      assertEquals(null, replicaDB.getNewestCSN());
      debugInfo(tn,"Success");
    }
    finally
    {
      if (replicaDB != null)
        replicaDB.shutdown();
      if (dbEnv != null)
        dbEnv.shutdown();
      remove(replicationServer);
      TestCaseUtils.deleteDirectory(testRoot);
    }
  }

  private void assertCount(String tn, JEReplicaDB replicaDB, CSN from, CSN to,
      int expectedCount, String testcase)
  {
    long actualCount = replicaDB.getCount(from, to);
    debugInfo(tn, testcase + " actualCount=" + actualCount);
    assertEquals(actualCount, expectedCount, testcase);
  }

}
