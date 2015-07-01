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
 *      Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.file;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.assertj.core.api.SoftAssertions;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.util.time.TimeService;
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
import org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy;
import org.opends.server.types.DN;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy.*;
import static org.testng.Assert.*;

/**
 * Test the FileReplicaDB class.
 */
@SuppressWarnings("javadoc")
public class FileReplicaDBTest extends ReplicationTestCase
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();
  private DN TEST_ROOT_DN;

  /**
   * Utility - log debug message - highlight it is from the test and not
   * from the server code. Makes easier to observe the test steps.
   */
  private void debugInfo(String tn, String s)
  {
    logger.trace("** TEST %s ** %s", tn, s);
  }

  @BeforeClass
  public void setup() throws Exception
  {
    TEST_ROOT_DN = DN.valueOf(TEST_ROOT_DN_STRING);
  }

  @DataProvider(name = "messages")
  Object[][] createMessages()
  {
    CSN[] csns = generateCSNs(1, 0, 2);
    return new Object[][] {
      { new DeleteMsg(TEST_ROOT_DN, csns[0], "uid") },
      { new DeleteMsg(TEST_ROOT_DN, csns[1], "uid") },
    };
  }

  @Test(dataProvider="messages")
  public void testRecordParser(UpdateMsg msg) throws Exception
  {
    RecordParser<CSN, UpdateMsg> parser = FileReplicaDB.RECORD_PARSER;

    ByteString data = parser.encodeRecord(Record.from(msg.getCSN(), msg));
    Record<CSN, UpdateMsg> record = parser.decodeRecord(data);

    assertThat(record).isNotNull();
    assertThat(record.getKey()).isEqualTo(msg.getCSN());
    assertThat(record.getValue()).isEqualTo(msg);
  }

  @Test
  public void testDomainDNWithForwardSlashes() throws Exception
  {
    ReplicationServer replicationServer = null;
    FileReplicaDB replicaDB = null;
    try
    {
      TestCaseUtils.startServer();
      replicationServer = configureReplicationServer(100, 5000);

      replicaDB = newReplicaDB(replicationServer);
      CSN[] csns = generateCSNs(1, 0, 1);
      replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[0], "uid"));

      waitChangesArePersisted(replicaDB, 1);
      assertFoundInOrder(replicaDB, csns[0]);
    }
    finally
    {
      shutdown(replicaDB);
      remove(replicationServer);
    }
  }

  @Test
  public void testAddAndReadRecords() throws Exception
  {
    ReplicationServer replicationServer = null;
    FileReplicaDB replicaDB = null;
    try
    {
      TestCaseUtils.startServer();
      replicationServer = configureReplicationServer(100, 5000);

      replicaDB = newReplicaDB(replicationServer);
      CSN[] csns = generateCSNs(1, 0, 5);
      replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[0], "uid"));
      replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[1], "uid"));
      replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[2], "uid"));

      waitChangesArePersisted(replicaDB, 3);

      assertFoundInOrder(replicaDB, csns[0], csns[1], csns[2]);
      assertNotFound(replicaDB, csns[4], AFTER_MATCHING_KEY);
      assertLimits(replicaDB, csns[0], csns[2]);

      DeleteMsg update4 = new DeleteMsg(TEST_ROOT_DN, csns[3], "uid");
      replicaDB.add(update4);
      waitChangesArePersisted(replicaDB, 4);

      assertFoundInOrder(replicaDB, csns[0], csns[1], csns[2], csns[3]);
      assertFoundInOrder(replicaDB, csns[2], csns[3]);
      assertFoundInOrder(replicaDB, csns[3]);
      assertNotFound(replicaDB, csns[4], AFTER_MATCHING_KEY);
    }
    finally
    {
      shutdown(replicaDB);
      remove(replicationServer);
    }
  }

  @Test
  public void testGenerateCursorFrom() throws Exception
  {
    ReplicationServer replicationServer = null;
    FileReplicaDB replicaDB = null;
    try
    {
      TestCaseUtils.startServer();
      replicationServer = configureReplicationServer(100000, 10);
      replicaDB = newReplicaDB(replicationServer);

      final CSN[] csns = generateCSNs(1, System.currentTimeMillis(), 5);
      final ArrayList<CSN> csns2 = new ArrayList<CSN>(Arrays.asList(csns));
      csns2.remove(csns[3]);

      for (CSN csn : csns2)
      {
        replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csn, "uid"));
      }
      waitChangesArePersisted(replicaDB, 4);

      for (CSN csn : csns2)
      {
        assertNextCSN(replicaDB, csn, ON_MATCHING_KEY, csn);
      }
      assertNextCSN(replicaDB, csns[3], ON_MATCHING_KEY, csns[4]);

      for (int i = 0; i < csns2.size() - 1; i++)
      {
        assertNextCSN(replicaDB, csns2.get(i), AFTER_MATCHING_KEY, csns2.get(i + 1));
      }
      assertNotFound(replicaDB, csns[4], AFTER_MATCHING_KEY);
    }
    finally
    {
      shutdown(replicaDB);
      remove(replicationServer);
    }
  }

  @DataProvider
  Object[][] dataForTestsWithCursorReinitialization()
  {
    return new Object[][] {
      // the index to use in CSN array for the start key of the cursor
      { 0 },
      { 1 },
      { 4 },
    };
  }

  @Test(dataProvider="dataForTestsWithCursorReinitialization")
  public void testGenerateCursorFromWithCursorReinitialization(int csnIndexForStartKey) throws Exception
  {
    ReplicationServer replicationServer = null;
    FileReplicaDB replicaDB = null;
    try
    {
      TestCaseUtils.startServer();
      replicationServer = configureReplicationServer(100000, 10);
      replicaDB = newReplicaDB(replicationServer);

      CSN[] csns = generateCSNs(1, System.currentTimeMillis(), 5);

      try (DBCursor<UpdateMsg> cursor = replicaDB.generateCursorFrom(
          csns[csnIndexForStartKey], GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY))
      {
        assertFalse(cursor.next());

        int[] indicesToAdd = { 0, 1, 2, 4 };
        for (int i : indicesToAdd)
        {
          replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[i], "uid"));
        }
        waitChangesArePersisted(replicaDB, 4);

        for (int i = csnIndexForStartKey+1; i < 5; i++)
        {
          if (i != 3)
          {
            final String indexNbMsg = "index i=" + i;
            assertTrue(cursor.next(), indexNbMsg);
            assertEquals(cursor.getRecord().getCSN(), csns[i], indexNbMsg);
          }
        }
        assertFalse(cursor.next());
      }
    }
    finally
    {
      shutdown(replicaDB);
      remove(replicationServer);
    }
  }

  /**
   * TODO : this works only if we ensure that there is a rotation of ahead log file
   * at right place. Each record takes 54 bytes, so it means : 108 < max file size < 162 to have
   * the last record alone in the ahead log file
   * Re-enable this test when max file size is customizable for log
   */
  @Test(enabled=false)
  public void testPurge() throws Exception
  {
    ReplicationServer replicationServer = null;
    FileReplicaDB replicaDB = null;
    try
    {
      TestCaseUtils.startServer();
      replicationServer = configureReplicationServer(100, 5000);
      replicaDB = newReplicaDB(replicationServer);

      CSN[] csns = generateCSNs(1, 0, 5);

      replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[0], "uid"));
      replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[1], "uid"));
      replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[2], "uid"));
      replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[3], "uid"));

      waitChangesArePersisted(replicaDB, 4);

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

  /**
   * Test the feature of clearing a FileReplicaDB used by a replication server.
   * The clear feature is used when a replication server receives a request to
   * reset the generationId of a given domain.
   */
  @Test
  public void testClear() throws Exception
  {
    ReplicationServer replicationServer = null;
    FileReplicaDB replicaDB = null;
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
      assertLimits(replicaDB, csns[0], csns[2]);

      // Clear DB and check it is cleared.
      replicaDB.clear();
      assertLimits(replicaDB, null, null);
    }
    finally
    {
      shutdown(replicaDB);
      remove(replicationServer);
    }
  }

  private void assertNextCSN(FileReplicaDB replicaDB, final CSN startCSN,
      final PositionStrategy positionStrategy, final CSN expectedCSN)
      throws ChangelogException
  {
    try (DBCursor<UpdateMsg> cursor = replicaDB.generateCursorFrom(
        startCSN, GREATER_THAN_OR_EQUAL_TO_KEY, positionStrategy))
    {
      final SoftAssertions softly = new SoftAssertions();
      softly.assertThat(cursor.next()).isTrue();
      softly.assertThat(cursor.getRecord().getCSN()).isEqualTo(expectedCSN);
      softly.assertAll();
    }
  }

  private void assertNotFound(FileReplicaDB replicaDB, final CSN startCSN,
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
   * Test the logic that manages counter records in the FileReplicaDB in order to
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

    // TODO : do we need the management of counter records ?
    // Use unreachable limits for now because it is not implemented
    testGetOldestNewestCSNs(40, 100);
    testGetOldestNewestCSNs(4000, 10000);
  }

  private void testGetOldestNewestCSNs(final int max, final int counterWindow) throws Exception
  {
    String tn = "testDBCount("+max+","+counterWindow+")";
    debugInfo(tn, "Starting test");

    File testRoot = null;
    ReplicationServer replicationServer = null;
    ReplicationEnvironment dbEnv = null;
    FileReplicaDB replicaDB = null;
    try
    {
      TestCaseUtils.startServer();
      replicationServer = configureReplicationServer(100000, 10);

      testRoot = createCleanDir();
      dbEnv = new ReplicationEnvironment(testRoot.getPath(), replicationServer, TimeService.SYSTEM);
      replicaDB = new FileReplicaDB(1, TEST_ROOT_DN, replicationServer, dbEnv);

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
      waitChangesArePersisted(replicaDB, max, counterWindow);
      assertLimits(replicaDB, csns[1], csns[max]);

      // Now we want to test that after closing and reopening the db, the
      // counting algo is well reinitialized and when new messages are added
      // the new counter are correctly generated.
      debugInfo(tn, "SHUTDOWN replicaDB and recreate");
      replicaDB.shutdown();

      replicaDB = new FileReplicaDB(1, TEST_ROOT_DN, replicationServer, dbEnv);
      assertLimits(replicaDB, csns[1], csns[max]);

      // Populate the db with 'max' msg
      for (int i=max+1; i<=2 * max; i++)
      {
        csns[i] = new CSN(now + i, mySeqnum, 1);
        replicaDB.add(new DeleteMsg(TEST_ROOT_DN, csns[i], "uid"));
        mySeqnum+=2;
      }
      waitChangesArePersisted(replicaDB, 2 * max, counterWindow);
      assertLimits(replicaDB, csns[1], csns[2 * max]);

      replicaDB.purgeUpTo(new CSN(Long.MAX_VALUE, 0, 0));

      String testcase = "AFTER PURGE (oldest, newest)=";
      debugInfo(tn, testcase + replicaDB.getOldestCSN() + replicaDB.getNewestCSN());
      assertEquals(replicaDB.getNewestCSN(), csns[2 * max], "Newest=");

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
      shutdown(replicaDB);
      if (dbEnv != null)
      {
        dbEnv.shutdown();
      }
      remove(replicationServer);
      TestCaseUtils.deleteDirectory(testRoot);
    }
  }

  private void assertLimits(FileReplicaDB replicaDB, CSN oldestCSN, CSN newestCSN)
  {
    final SoftAssertions softly = new SoftAssertions();
    softly.assertThat(replicaDB.getOldestCSN()).as("Wrong oldest CSN").isEqualTo(oldestCSN);
    softly.assertThat(replicaDB.getNewestCSN()).as("Wrong newest CSN").isEqualTo(newestCSN);
    softly.assertAll();
  }

  private void shutdown(FileReplicaDB replicaDB)
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

  private void waitChangesArePersisted(FileReplicaDB replicaDB,
      int nbRecordsInserted) throws Exception
  {
    waitChangesArePersisted(replicaDB, nbRecordsInserted, 1000);
  }

  private void waitChangesArePersisted(FileReplicaDB replicaDB,
      int nbRecordsInserted, int counterWindow) throws Exception
  {
    // one counter record is inserted every time "counterWindow"
    // records have been inserted
    int expectedNbRecords = nbRecordsInserted + (nbRecordsInserted - 1) / counterWindow;

    int count = 0;
    while (replicaDB.getNumberRecords() != expectedNbRecords && count < 100)
    {
      Thread.sleep(10);
      count++;
    }
    assertEquals(replicaDB.getNumberRecords(), expectedNbRecords);
  }

  private ReplicationServer configureReplicationServer(int windowSize, int queueSize)
      throws IOException, ConfigException
  {
    final int changelogPort = findFreePort();
    final ReplicationServerCfg conf = new ReplServerFakeConfiguration(
        changelogPort, null, ReplicationDBImplementation.LOG, 0, 2, queueSize, windowSize, null);
    return new ReplicationServer(conf);
  }

  private FileReplicaDB newReplicaDB(ReplicationServer rs) throws Exception
  {
    final FileChangelogDB changelogDB = (FileChangelogDB) rs.getChangelogDB();
    return changelogDB.getOrCreateReplicaDB(TEST_ROOT_DN, 1, rs).getFirst();
  }

  private File createCleanDir() throws IOException
  {
    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    String path = System.getProperty(TestCaseUtils.PROPERTY_BUILD_DIR, buildRoot
            + File.separator + "build");
    path = path + File.separator + "unit-tests" + File.separator + "FileReplicaDB";
    final File testRoot = new File(path);
    TestCaseUtils.deleteDirectory(testRoot);
    testRoot.mkdirs();
    return testRoot;
  }

  private void assertFoundInOrder(FileReplicaDB replicaDB, CSN... csns) throws Exception
  {
    if (csns.length == 0)
    {
      return;
    }

    assertFoundInOrder(replicaDB, AFTER_MATCHING_KEY, csns);
    assertFoundInOrder(replicaDB, ON_MATCHING_KEY, csns);
  }

  private void assertFoundInOrder(FileReplicaDB replicaDB,
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
