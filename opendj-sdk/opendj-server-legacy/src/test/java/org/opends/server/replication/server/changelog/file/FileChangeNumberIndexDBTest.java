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

import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.std.meta.ReplicationServerCfgDefn.ReplicationDBImplementation;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexRecord;
import org.opends.server.replication.server.changelog.api.ChangelogDB;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.types.DN;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.replication.server.changelog.file.FileReplicaDBTest.*;
import static org.testng.Assert.*;

@SuppressWarnings("javadoc")
public class FileChangeNumberIndexDBTest extends ReplicationTestCase
{

  @DataProvider(name = "messages")
  Object[][] createMessages() throws Exception
  {
    CSN[] csns = generateCSNs(1, 0, 3);
    DN dn1 = DN.valueOf("o=test1");
    return new Object[][] {
      { new ChangeNumberIndexRecord(0L, dn1, csns[1]) },
      { new ChangeNumberIndexRecord(999L, dn1, csns[2]) },
    };
  }

  @Test(dataProvider="messages")
  public void testRecordParser(ChangeNumberIndexRecord msg) throws Exception
  {
    RecordParser<Long, ChangeNumberIndexRecord> parser = FileChangeNumberIndexDB.RECORD_PARSER;

    ByteString data = parser.encodeRecord(Record.from(msg.getChangeNumber(), msg));
    Record<Long, ChangeNumberIndexRecord> record = parser.decodeRecord(data);

    assertThat(record).isNotNull();
    assertThat(record.getKey()).isEqualTo(msg.getChangeNumber());
    assertThat(record.getValue().getBaseDN()).isEqualTo(msg.getBaseDN());
    assertThat(record.getValue().getCSN()).isEqualTo(msg.getCSN());
  }

  @Test
  public void testAddAndReadRecords() throws Exception
  {
    ReplicationServer replicationServer = null;
    try
    {
      replicationServer = newReplicationServer();
      final ChangelogDB changelogDB = replicationServer.getChangelogDB();
      changelogDB.setPurgeDelay(0);
      final FileChangeNumberIndexDB cnIndexDB = getCNIndexDB(replicationServer);

      long[] changeNumbers = addThreeRecords(cnIndexDB);
      long cn1 = changeNumbers[0];
      long cn2 = changeNumbers[1];
      long cn3 = changeNumbers[2];

      // Checks
      assertEquals(cnIndexDB.getOldestRecord().getChangeNumber(), cn1);
      assertEquals(cnIndexDB.getNewestRecord().getChangeNumber(), cn3);

      assertEquals(cnIndexDB.count(), 3, "Db count");
      assertFalse(cnIndexDB.isEmpty());

      DBCursor<ChangeNumberIndexRecord> cursor = cnIndexDB.getCursorFrom(cn1);
      assertCursorReadsInOrder(cursor, cn1, cn2, cn3);

      cursor = cnIndexDB.getCursorFrom(cn2);
      assertCursorReadsInOrder(cursor, cn2, cn3);

      cursor = cnIndexDB.getCursorFrom(cn3);
      assertCursorReadsInOrder(cursor, cn3);
    }
    finally
    {
      remove(replicationServer);
    }
  }

  @Test
  public void testClear() throws Exception
  {
    ReplicationServer replicationServer = null;
    try
    {
      replicationServer = newReplicationServer();
      final ChangelogDB changelogDB = replicationServer.getChangelogDB();
      changelogDB.setPurgeDelay(0);
      final FileChangeNumberIndexDB cnIndexDB = getCNIndexDB(replicationServer);
      addThreeRecords(cnIndexDB);

      cnIndexDB.clear();

      assertNull(cnIndexDB.getOldestRecord());
      assertNull(cnIndexDB.getNewestRecord());
      assertEquals(cnIndexDB.count(), 0);
      assertTrue(cnIndexDB.isEmpty());
    }
    finally
    {
      remove(replicationServer);
    }
  }

  /**
   * This test verifies purge is working by relying on a very short purge delay.
   * The purge can be done only if there is at least one read-only log file, so
   * this test ensures the rotation happens, using the rotation based on time.
   */
  @Test
  public void testPurge() throws Exception
  {
    ReplicationServer replicationServer = null;
    try
    {
      replicationServer = newReplicationServer();
      final ChangelogDB changelogDB = replicationServer.getChangelogDB();
      changelogDB.setPurgeDelay(0); // disable purging

      // Add records
      DN[] baseDNs = { DN.valueOf("o=test1"), DN.valueOf("o=test2"), DN.valueOf("o=test3"), DN.valueOf("o=test4") };
      CSN[] csns = generateCSNs(1, 0, 4);
      final FileChangeNumberIndexDB cnIndexDB = getCNIndexDB(replicationServer);
      long cn0 = addRecord(cnIndexDB, baseDNs[0], csns[0]);
                 addRecord(cnIndexDB, baseDNs[1], csns[1]);
      long cn2 = addRecord(cnIndexDB, baseDNs[2], csns[2]);

      // The CN DB should not be purged at this point
      assertEquals(cnIndexDB.getOldestRecord().getChangeNumber(), cn0);
      assertEquals(cnIndexDB.getNewestRecord().getChangeNumber(), cn2);

      // change the purge delay to a very short time
      changelogDB.setPurgeDelay(5);
      Thread.sleep(50);
      // add a new record to force the rotation of the log
      addRecord(cnIndexDB, baseDNs[3], csns[3]);

      // Now all changes should have been purged but the last one
      int count = 0;
      while (cnIndexDB.count() > 1 && count < 100)
      {
        Thread.sleep(10);
        count++;
      }
      assertOnlyNewestRecordIsLeft(cnIndexDB, 4);
    }
    finally
    {
      remove(replicationServer);
    }
  }

  private long[] addThreeRecords(FileChangeNumberIndexDB cnIndexDB) throws Exception
  {
    // Prepare data to be stored in the db
    DN baseDN1 = DN.valueOf("o=test1");
    DN baseDN2 = DN.valueOf("o=test2");
    DN baseDN3 = DN.valueOf("o=test3");

    CSN[] csns = generateCSNs(1, 0, 3);

    // Add records
    long cn1 = addRecord(cnIndexDB, baseDN1, csns[0]);
    long cn2 = addRecord(cnIndexDB, baseDN2, csns[1]);
    long cn3 = addRecord(cnIndexDB, baseDN3, csns[2]);
    return new long[] { cn1, cn2, cn3 };
  }

  private long addRecord(FileChangeNumberIndexDB cnIndexDB, DN baseDN, CSN csn) throws ChangelogException
  {
    return cnIndexDB.addRecord(new ChangeNumberIndexRecord(baseDN, csn));
  }

  private void assertEqualTo(ChangeNumberIndexRecord record, CSN csn, DN baseDN)
  {
    assertEquals(record.getCSN(), csn);
    assertEquals(record.getBaseDN(), baseDN);
  }

  private FileChangeNumberIndexDB getCNIndexDB(ReplicationServer rs) throws ChangelogException
  {
    final FileChangelogDB changelogDB = (FileChangelogDB) rs.getChangelogDB();
    final FileChangeNumberIndexDB cnIndexDB =
        (FileChangeNumberIndexDB) changelogDB.getChangeNumberIndexDB();
    assertTrue(cnIndexDB.isEmpty());
    return cnIndexDB;
  }

  /**
   * The newest record is no longer cleared to ensure persistence to the last
   * generated change number across server restarts.
   */
  private void assertOnlyNewestRecordIsLeft(FileChangeNumberIndexDB cnIndexDB,
      int newestChangeNumber) throws ChangelogException
  {
    assertEquals(cnIndexDB.count(), 1);
    assertFalse(cnIndexDB.isEmpty());
    final ChangeNumberIndexRecord oldest = cnIndexDB.getOldestRecord();
    final ChangeNumberIndexRecord newest = cnIndexDB.getNewestRecord();
    assertEquals(oldest.getChangeNumber(), newestChangeNumber);
    assertEquals(oldest.getChangeNumber(), newest.getChangeNumber());
    assertEquals(oldest.getBaseDN(), newest.getBaseDN());
    assertEquals(oldest.getCSN(), newest.getCSN());
  }

  private ReplicationServer newReplicationServer() throws Exception
  {
    TestCaseUtils.startServer();
    final int port = TestCaseUtils.findFreePort();
    final ReplServerFakeConfiguration cfg =
        new ReplServerFakeConfiguration(port, null, ReplicationDBImplementation.LOG, 0, 2, 0, 100, null);
    cfg.setComputeChangeNumber(true);
    return new ReplicationServer(cfg);
  }

  private void assertCursorReadsInOrder(DBCursor<ChangeNumberIndexRecord> cursor,
      long... cns) throws ChangelogException
  {
    try
    {
      for (long cn : cns)
      {
        assertTrue(cursor.next());
        assertEquals(cursor.getRecord().getChangeNumber(), cn);
      }
      assertFalse(cursor.next());
    }
    finally
    {
      cursor.close();
    }
  }
}
