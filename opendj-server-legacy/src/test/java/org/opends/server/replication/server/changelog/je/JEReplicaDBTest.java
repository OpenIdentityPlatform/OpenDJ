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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.assertj.core.api.SoftAssertions;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.ReplicationServerCfgDefn.ReplicationDBImplementation;
import org.opends.server.admin.std.server.ReplicationServerCfg;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy;
import org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy;
import org.opends.server.types.DN;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy.*;
import static org.testng.Assert.*;

/** Test the JEReplicaDB class. */
@SuppressWarnings("javadoc")
public class JEReplicaDBTest extends ReplicationTestCase
{
  /** The tracer object for the debug logger. */
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  private DN TEST_ROOT_DN;

  private static final ReplicationDBImplementation previousDBImpl = replicationDbImplementation;
  private ReplicationServer replicationServer;
  private JEReplicaDB replicaDB;

  @BeforeClass
  public void setDBImpl()
  {
    setReplicationDBImplementation(ReplicationDBImplementation.JE);
  }

  @AfterClass
  public void resetDBImplToPrevious()
  {
    setReplicationDBImplementation(previousDBImpl);
  }

  /**
   * Utility - log debug message - highlight it is from the test and not
   * from the server code. Makes easier to observe the test steps.
   */
  private void debugInfo(String tn, String s)
  {
    if (logger.isTraceEnabled())
    {
      logger.trace("** TEST " + tn + " ** " + s);
    }
  }

  @BeforeClass
  public void setup() throws Exception
  {
    TEST_ROOT_DN = DN.valueOf(TEST_ROOT_DN_STRING);
  }

  @DataProvider
  Object[][] cursorData()
  {
    // create 7 csns
    final CSN[] sevenCsns = generateCSNs(1, System.currentTimeMillis(), 7);
    CSN beforeCsn = sevenCsns[0];
    CSN middleCsn = sevenCsns[3]; // will be between csns[1] and csns[2]
    CSN afterCsn = sevenCsns[6];

    // but use only 4 of them for update msg
    // beforeCsn, middleCsn and afterCsn are not used
    // in order to test cursor generation from a key not present in the log (before, in the middle, after)
    final List<CSN> usedCsns = new ArrayList<>(Arrays.asList(sevenCsns));
    usedCsns.remove(beforeCsn);
    usedCsns.remove(middleCsn);
    usedCsns.remove(afterCsn);
    final CSN[] csns = usedCsns.toArray(new CSN[4]);

    return new Object[][] {
      // equal matching
      { csns, beforeCsn, EQUAL_TO_KEY, ON_MATCHING_KEY, -1, -1 },
      { csns, csns[0], EQUAL_TO_KEY, ON_MATCHING_KEY, 0, 3 },
      { csns, csns[1], EQUAL_TO_KEY, ON_MATCHING_KEY, 1, 3 },
      { csns, middleCsn, EQUAL_TO_KEY, ON_MATCHING_KEY, -1, -1 },
      { csns, csns[2], EQUAL_TO_KEY, ON_MATCHING_KEY, 2, 3 },
      { csns, csns[3], EQUAL_TO_KEY, ON_MATCHING_KEY, 3, 3 },
      { csns, afterCsn, EQUAL_TO_KEY, ON_MATCHING_KEY, -1, -1 },

      { csns, beforeCsn, EQUAL_TO_KEY, AFTER_MATCHING_KEY, -1, -1 },
      { csns, csns[0], EQUAL_TO_KEY, AFTER_MATCHING_KEY, 1, 3 },
      { csns, csns[1], EQUAL_TO_KEY, AFTER_MATCHING_KEY, 2, 3 },
      { csns, middleCsn, EQUAL_TO_KEY, AFTER_MATCHING_KEY, -1, -1 },
      { csns, csns[2], EQUAL_TO_KEY, AFTER_MATCHING_KEY, 3, 3 },
      { csns, csns[3], EQUAL_TO_KEY, AFTER_MATCHING_KEY, -1, -1 },
      { csns, afterCsn, EQUAL_TO_KEY, AFTER_MATCHING_KEY, -1, -1 },

      // less than or equal matching
      { csns, beforeCsn, LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, -1, -1 },
      { csns, csns[0], LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 0, 3 },
      { csns, csns[1], LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 1, 3 },
      { csns, middleCsn, LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 1, 3 },
      { csns, csns[2], LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 2, 3 },
      { csns, csns[3], LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 3, 3 },
      { csns, afterCsn, LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 3, 3 },

      { csns, beforeCsn, LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, -1, -1 },
      { csns, csns[0], LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, 1, 3 },
      { csns, csns[1], LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, 2, 3 },
      { csns, middleCsn, LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, 2, 3 },
      { csns, csns[2], LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, 3, 3 },
      { csns, csns[3], LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, -1, -1 },
      { csns, afterCsn, LESS_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, -1, -1 },

      // greater than or equal matching
      { csns, beforeCsn, GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 0, 3 },
      { csns, csns[0], GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 0, 3 },
      { csns, csns[1], GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 1, 3 },
      { csns, middleCsn, GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 2, 3 },
      { csns, csns[2], GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 2, 3 },
      { csns, csns[3], GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, 3, 3 },
      { csns, afterCsn, GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, -1, -1 },

      { csns, beforeCsn, GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, 0, 3 },
      { csns, csns[0], GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, 1, 3 },
      { csns, csns[1], GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, 2, 3 },
      { csns, middleCsn, GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, 2, 3 },
      { csns, csns[2], GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, 3, 3 },
      { csns, csns[3], GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, -1, -1 },
      { csns, afterCsn, GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, -1, -1 },
      { null, null, null, null, -1, -1 } // stop line
    };
  }

  /**
   * Test the cursor with all acceptable strategies combination.
   * Creation of a replication server is costly so it is created only once on first test and cleaned after the
   * last test using the stop line in data to do so.
   */
  @Test(dataProvider="cursorData")
  public void testGenerateCursor(CSN[] csns, CSN startCsn, KeyMatchingStrategy matchingStrategy,
      PositionStrategy positionStrategy, int startIndex, int endIndex) throws Exception
  {
    try
    {
      if (replicationServer == null)
      {
        // initialize only once
        TestCaseUtils.startServer();
        replicationServer = configureReplicationServer(100000, 10);
        replicaDB = newReplicaDB(replicationServer);
        for (CSN csn : csns)
        {
          replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csn, "uid"));
        }
      }
      if (csns == null)
      {
        return; // stop line, time to clean replication artifacts
      }

      try (DBCursor<UpdateMsg> cursor = replicaDB.generateCursorFrom(startCsn, matchingStrategy, positionStrategy))
      {
        if (startIndex != -1)
        {
          assertThatCursorCanBeFullyReadFromStart(cursor, csns, startIndex, endIndex);
        }
        else
        {
          assertThatCursorIsExhausted(cursor);
        }
      }
    }
    finally
    {
      if (csns == null)
      {
        // stop line, stop and remove replication
        shutdown(replicaDB);
        remove(replicationServer);
      }
    }
  }

  @Test
  public void testTrim() throws Exception
  {
    ReplicationServer replicationServer = null;
    JEReplicaDB replicaDB = null;
    try
    {
      TestCaseUtils.startServer();
      replicationServer = configureReplicationServer(100, 5000);
      replicaDB = newReplicaDB(replicationServer);

      CSN[] csns = generateCSNs(1, 0, 5);

      replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[0], "uid"));
      replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[1], "uid"));
      replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[2], "uid"));
      DeleteMsg update4 = new DeleteMsg(TEST_ROOT_DN, csns[3], "uid");

      //--
      // Iterator tests with changes persisted
      assertFoundInOrder(replicaDB, csns[0], csns[1], csns[2]);
      assertNotFound(replicaDB, csns[4], AFTER_MATCHING_KEY);

      assertEquals(replicaDB.getOldestCSN(), csns[0]);
      assertEquals(replicaDB.getNewestCSN(), csns[2]);

      //--
      // Cursor tests with changes persisted
      replicaDB.add(update4);

      assertFoundInOrder(replicaDB, csns[0], csns[1], csns[2], csns[3]);
      // Test cursor from existing CSN
      assertFoundInOrder(replicaDB, csns[2], csns[3]);
      assertFoundInOrder(replicaDB, csns[3]);
      assertNotFound(replicaDB, csns[4], AFTER_MATCHING_KEY);

      replicaDB.purgeUpTo(new CSN(Long.MAX_VALUE, 0, 0));

      int count = 0;
      boolean purgeSucceeded = false;
      final CSN expectedNewestCSN = csns[3];
      do
      {
        Thread.sleep(10);

        final CSN oldestCSN = replicaDB.getOldestCSN();
        final CSN newestCSN = replicaDB.getNewestCSN();
        purgeSucceeded =
            oldestCSN.equals(expectedNewestCSN)
                && newestCSN.equals(expectedNewestCSN);
        count++;
      }
      while (!purgeSucceeded && count < 100);
      assertTrue(purgeSucceeded);
    }
    finally
    {
      shutdown(replicaDB);
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
        new ReplServerFakeConfiguration(changelogPort, null, ReplicationDBImplementation.JE, 0, 2, queueSize, windowSize, null);
    return new ReplicationServer(conf);
  }

  private JEReplicaDB newReplicaDB(ReplicationServer rs) throws Exception
  {
    final JEChangelogDB changelogDB = (JEChangelogDB) rs.getChangelogDB();
    return changelogDB.getOrCreateReplicaDB(TEST_ROOT_DN, 1, rs).getFirst();
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

    assertFoundInOrder(replicaDB, AFTER_MATCHING_KEY, csns);
    assertFoundInOrder(replicaDB, ON_MATCHING_KEY, csns);
  }

  /**
   * Test the feature of clearing a JEReplicaDB used by a replication server.
   * The clear feature is used when a replication server receives a request to
   * reset the generationId of a given domain.
   */
  @Test
  public void testClear() throws Exception
  {
    ReplicationServer replicationServer = null;
    JEReplicaDB replicaDB = null;
    try
    {
      TestCaseUtils.startServer();
      replicationServer = configureReplicationServer(100, 5000);
      replicaDB = newReplicaDB(replicationServer);

      CSN[] csns = generateCSNs(1, 0, 3);

      // Add the changes and check they are here
      replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[0], "uid"));
      replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[1], "uid"));
      replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[2], "uid"));

      assertEquals(csns[0], replicaDB.getOldestCSN());
      assertEquals(csns[2], replicaDB.getNewestCSN());

      // Clear DB and check it is cleared.
      replicaDB.clear();

      assertNull(replicaDB.getOldestCSN());
      assertNull(replicaDB.getNewestCSN());
    }
    finally
    {
      shutdown(replicaDB);
      remove(replicationServer);
    }
  }

  private void advanceCursorUpTo(DBCursor<UpdateMsg> cursor, CSN[] csns, int startIndex, int endIndex)
      throws Exception
  {
    for (int i = startIndex; i <= endIndex; i++)
    {
      assertThat(cursor.next()).as("next() value when i=" + i).isTrue();
      assertThat(cursor.getRecord().getCSN()).isEqualTo(csns[i]);
    }
  }

  private void assertThatCursorIsExhausted(DBCursor<UpdateMsg> cursor) throws Exception
  {
    final SoftAssertions softly = new SoftAssertions();
    softly.assertThat(cursor.next()).isFalse();
    softly.assertThat(cursor.getRecord()).isNull();
    softly.assertAll();
  }

  private void assertThatCursorCanBeFullyRead(DBCursor<UpdateMsg> cursor, CSN[] csns, int startIndex, int endIndex)
      throws Exception
  {
    advanceCursorUpTo(cursor, csns, startIndex, endIndex);
    assertThatCursorIsExhausted(cursor);
  }

  private void assertThatCursorCanBeFullyReadFromStart(DBCursor<UpdateMsg> cursor, CSN[] csns, int startIndex,
      int endIndex) throws Exception
  {
    assertThat(cursor.getRecord()).isNull();
    assertThatCursorCanBeFullyRead(cursor, csns, startIndex, endIndex);
  }

  private void assertNotFound(JEReplicaDB replicaDB, final CSN startCSN,
      final PositionStrategy positionStrategy) throws ChangelogException
  {
    try (DBCursor<UpdateMsg> cursor = replicaDB.generateCursorFrom(
        startCSN, GREATER_THAN_OR_EQUAL_TO_KEY, positionStrategy))
    {
      final SoftAssertions softly = new SoftAssertions();
      softly.assertThat(cursor.next()).isFalse();
      softly.assertThat(cursor.getRecord()).isNull();
      softly.assertAll();
    }
  }

  /**
   * Test the logic that manages counter records in the JEReplicaDB in order to
   * optimize the oldest and newest records in the replication changelog db.
   */
  @Test(groups = { "opendj-256" })
  public void testGetOldestNewestCSNs() throws Exception
  {
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
    // After shutting down/closing and reopening the db.
    testGetOldestNewestCSNs(40, 10);
    testGetOldestNewestCSNs(4000, 1000);
  }

  private void testGetOldestNewestCSNs(final int max, final int counterWindow) throws Exception
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

      assertEquals(replicaDB.getOldestCSN(), csns[1], "Wrong oldest CSN");
      assertEquals(replicaDB.getNewestCSN(), csns[max], "Wrong newest CSN");

      // Now we want to test that after closing and reopening the db, the
      // counting algo is well reinitialized and when new messages are added
      // the new counter are correctly generated.
      debugInfo(tn, "SHUTDOWN replicaDB and recreate");
      replicaDB.shutdown();

      replicaDB = new JEReplicaDB(1, TEST_ROOT_DN, replicationServer, dbEnv);
      replicaDB.setCounterRecordWindowSize(counterWindow);

      assertEquals(replicaDB.getOldestCSN(), csns[1], "Wrong oldest CSN");
      assertEquals(replicaDB.getNewestCSN(), csns[max], "Wrong newest CSN");

      // Populate the db with 'max' msg
      for (int i=max+1; i<=2 * max; i++)
      {
        csns[i] = new CSN(now + i, mySeqnum, 1);
        replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[i], "uid"));
        mySeqnum+=2;
      }

      assertEquals(replicaDB.getOldestCSN(), csns[1], "Wrong oldest CSN");
      assertEquals(replicaDB.getNewestCSN(), csns[2 * max], "Wrong newest CSN");

      replicaDB.purgeUpTo(new CSN(Long.MAX_VALUE, 0, 0));

      String testcase = "AFTER PURGE (oldest, newest)=";
      debugInfo(tn, testcase + replicaDB.getOldestCSN() + replicaDB.getNewestCSN());
      assertEquals(replicaDB.getNewestCSN(), csns[2 * max], "Newest=");

      // Clear ...
      debugInfo(tn,"clear:");
      replicaDB.clear();

      // Check the db is cleared.
      assertNull(replicaDB.getOldestCSN());
      assertNull(replicaDB.getNewestCSN());
      debugInfo(tn,"Success");
    }
    finally
    {
      shutdown(replicaDB);
      if (dbEnv != null)
      {
        dbEnv.shutdown();
      }
      remove(replicationServer);
      TestCaseUtils.deleteDirectory(testRoot);
    }
  }

  private void shutdown(JEReplicaDB replicaDB)
  {
    if (replicaDB != null)
    {
      replicaDB.shutdown();
    }
  }

  static CSN[] generateCSNs(int serverId, long timestamp, int number)
  {
    CSNGenerator gen = new CSNGenerator(serverId, timestamp);
    CSN[] csns = new CSN[number];
    for (int i = 0; i < csns.length; i++)
    {
      csns[i] = gen.newCSN();
    }
    return csns;
  }

  private void assertFoundInOrder(JEReplicaDB replicaDB,
      final PositionStrategy positionStrategy, CSN... csns) throws ChangelogException
  {
    try (DBCursor<UpdateMsg> cursor = replicaDB.generateCursorFrom(
        csns[0], GREATER_THAN_OR_EQUAL_TO_KEY, positionStrategy))
    {
      assertNull(cursor.getRecord(), "Cursor should point to a null record initially");

      for (int i = positionStrategy == ON_MATCHING_KEY ? 0 : 1; i < csns.length; i++)
      {
        final String msg = "i=" + i + ", csns[i]=" + csns[i].toStringUI();
        final SoftAssertions softly = new SoftAssertions();
        softly.assertThat(cursor.next()).as(msg).isTrue();
        softly.assertThat(cursor.getRecord().getCSN()).as(msg).isEqualTo(csns[i]);
        softly.assertAll();
      }
      final SoftAssertions softly = new SoftAssertions();
      softly.assertThat(cursor.next()).isFalse();
      softly.assertThat(cursor.getRecord()).isNull();
      softly.assertAll();
    }
  }
}
