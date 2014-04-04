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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.util.ArrayList;
import java.util.List;

import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexRecord;
import org.opends.server.replication.server.changelog.api.ChangelogDB;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.types.DN;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.opends.server.replication.server.changelog.je.JEReplicaDBTest.*;
import static org.testng.Assert.*;

/**
 * Test the JEChangeNumberIndexDB class with 2 kinds of cleaning of the db : -
 * periodic trim - call to clear method()
 */
@SuppressWarnings("javadoc")
public class JEChangeNumberIndexDBTest extends ReplicationTestCase
{
  private final MultiDomainServerState previousCookie =
      new MultiDomainServerState();
  private final List<String> cookies = new ArrayList<String>();

  @BeforeMethod
  public void clearCookie()
  {
    previousCookie.clear();
    cookies.clear();
  }

  /**
   * This test makes basic operations of a JEChangeNumberIndexDB:
   * <ol>
   * <li>create the db</li>
   * <li>add records</li>
   * <li>read them with a cursor</li>
   * <li>set a very short trim period</li>
   * <li>wait for the db to be trimmed / here since the changes are not stored
   * in the replication changelog, the ChangeNumberIndexDB will be cleared.</li>
   * </ol>
   */
  @Test()
  void testTrim() throws Exception
  {
    ReplicationServer replicationServer = null;
    try
    {
      replicationServer = newReplicationServer();
      final ChangelogDB changelogDB = replicationServer.getChangelogDB();
      changelogDB.setPurgeDelay(0); // disable purging

      // Prepare data to be stored in the db
      DN baseDN1 = DN.valueOf("o=baseDN1");
      DN baseDN2 = DN.valueOf("o=baseDN2");
      DN baseDN3 = DN.valueOf("o=baseDN3");

      CSN[] csns = newCSNs(1, 0, 3);

      // Add records
      final JEChangeNumberIndexDB cnIndexDB = getCNIndexDB(replicationServer);
      long cn1 = addRecord(cnIndexDB, baseDN1, csns[0]);
                 addRecord(cnIndexDB, baseDN2, csns[1]);
      long cn3 = addRecord(cnIndexDB, baseDN3, csns[2]);

      // The ChangeNumber should not get purged
      final long oldestCN = cnIndexDB.getOldestRecord().getChangeNumber();
      assertEquals(oldestCN, cn1);
      assertEquals(cnIndexDB.getNewestRecord().getChangeNumber(), cn3);

      DBCursor<ChangeNumberIndexRecord> cursor = cnIndexDB.getCursorFrom(oldestCN);
      try
      {
        assertEqualTo(cursor.getRecord(), csns[0], baseDN1, cookies.get(0));
        assertTrue(cursor.next());
        assertEqualTo(cursor.getRecord(), csns[1], baseDN2, cookies.get(1));
        assertTrue(cursor.next());
        assertEqualTo(cursor.getRecord(), csns[2], baseDN3, cookies.get(2));
        assertFalse(cursor.next());
      }
      finally
      {
        StaticUtils.close(cursor);
      }

      // Now test that purging removes all changes bar the last one
      changelogDB.setPurgeDelay(1);
      int count = 0;
      while (cnIndexDB.count() > 1 && count < 100)
      {
        Thread.sleep(10);
        count++;
      }
      assertOnlyNewestRecordIsLeft(cnIndexDB, 3);
    }
    finally
    {
      remove(replicationServer);
    }
  }

  private long addRecord(JEChangeNumberIndexDB cnIndexDB, DN baseDN, CSN csn) throws ChangelogException
  {
    final String cookie = previousCookie.toString();
    cookies.add(cookie);
    final long changeNumber = cnIndexDB.addRecord(
        new ChangeNumberIndexRecord(cookie, baseDN, csn));
    previousCookie.update(baseDN, csn);
    return changeNumber;
  }

  private void assertEqualTo(ChangeNumberIndexRecord record, CSN csn, DN baseDN, String cookie)
  {
    assertEquals(record.getCSN(), csn);
    assertEquals(record.getBaseDN(), baseDN);
    assertEquals(record.getPreviousCookie(), cookie);
  }

  private JEChangeNumberIndexDB getCNIndexDB(ReplicationServer rs) throws ChangelogException
  {
    final JEChangelogDB changelogDB = (JEChangelogDB) rs.getChangelogDB();
    final JEChangeNumberIndexDB cnIndexDB =
        (JEChangeNumberIndexDB) changelogDB.getChangeNumberIndexDB();
    assertTrue(cnIndexDB.isEmpty());
    return cnIndexDB;
  }

  /**
   * This test makes basic operations of a JEChangeNumberIndexDB and explicitly
   * calls the clear() method instead of waiting for the periodic trim to clear
   * it.
   * <ol>
   * <li>create the db</li>
   * <li>add records</li>
   * <li>read them with a cursor</li>
   * <li>clear the db</li>
   * </ol>
   */
  @Test()
  void testClear() throws Exception
  {
    ReplicationServer replicationServer = null;
    try
    {
      replicationServer = newReplicationServer();
      final ChangelogDB changelogDB = replicationServer.getChangelogDB();
      changelogDB.setPurgeDelay(0);

      // Prepare data to be stored in the db

      DN baseDN1 = DN.valueOf("o=baseDN1");
      DN baseDN2 = DN.valueOf("o=baseDN2");
      DN baseDN3 = DN.valueOf("o=baseDN3");

      CSN[] csns = newCSNs(1, 0, 3);

      // Add records
      final JEChangeNumberIndexDB cnIndexDB = getCNIndexDB(replicationServer);
      long cn1 = addRecord(cnIndexDB, baseDN1, csns[0]);
      long cn2 = addRecord(cnIndexDB, baseDN2, csns[1]);
      long cn3 = addRecord(cnIndexDB, baseDN3, csns[2]);

      // Checks
      assertEquals(cnIndexDB.getOldestRecord().getChangeNumber(), cn1);
      assertEquals(cnIndexDB.getNewestRecord().getChangeNumber(), cn3);

      assertEquals(cnIndexDB.count(), 3, "Db count");
      assertFalse(cnIndexDB.isEmpty());

      assertEquals(getPreviousCookie(cnIndexDB, cn1), cookies.get(0));
      assertEquals(getPreviousCookie(cnIndexDB, cn2), cookies.get(1));
      assertEquals(getPreviousCookie(cnIndexDB, cn3), cookies.get(2));

      DBCursor<ChangeNumberIndexRecord> cursor = cnIndexDB.getCursorFrom(cn1);
      assertCursorReadsInOrder(cursor, cn1, cn2, cn3);

      cursor = cnIndexDB.getCursorFrom(cn2);
      assertCursorReadsInOrder(cursor, cn2, cn3);

      cursor = cnIndexDB.getCursorFrom(cn3);
      assertCursorReadsInOrder(cursor, cn3);

      cnIndexDB.removeDomain(null);
      assertOnlyNewestRecordIsLeft(cnIndexDB, 3);

      // Check the db is cleared.
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
   * The newest record is no longer cleared to ensure persistence to the last
   * generated change number across server restarts.
   */
  private void assertOnlyNewestRecordIsLeft(JEChangeNumberIndexDB cnIndexDB,
      int newestChangeNumber) throws ChangelogException
  {
    assertEquals(cnIndexDB.count(), 1);
    assertFalse(cnIndexDB.isEmpty());
    final ChangeNumberIndexRecord oldest = cnIndexDB.getOldestRecord();
    final ChangeNumberIndexRecord newest = cnIndexDB.getNewestRecord();
    assertEquals(oldest.getChangeNumber(), newestChangeNumber);
    assertEquals(oldest.getChangeNumber(), newest.getChangeNumber());
    assertEquals(oldest.getPreviousCookie(), newest.getPreviousCookie());
    assertEquals(oldest.getBaseDN(), newest.getBaseDN());
    assertEquals(oldest.getCSN(), newest.getCSN());
  }

  private ReplicationServer newReplicationServer() throws Exception
  {
    TestCaseUtils.startServer();
    final int port = TestCaseUtils.findFreePort();
    return new ReplicationServer(
        new ReplServerFakeConfiguration(port, null, 0, 2, 0, 100, null));
  }

  private String getPreviousCookie(JEChangeNumberIndexDB cnIndexDB,
      long changeNumber) throws Exception
  {
    DBCursor<ChangeNumberIndexRecord> cursor = cnIndexDB.getCursorFrom(changeNumber);
    try
    {
      return cursor.getRecord().getPreviousCookie();
    }
    finally
    {
      StaticUtils.close(cursor);
    }
  }

  private void assertCursorReadsInOrder(DBCursor<ChangeNumberIndexRecord> cursor,
      long... cns) throws ChangelogException
  {
    try
    {
      for (int i = 0; i < cns.length; i++)
      {
        assertEquals(cursor.getRecord().getChangeNumber(), cns[i]);
        final boolean isNotLast = i + 1 < cns.length;
        assertEquals(cursor.next(), isNotLast);
      }
    }
    finally
    {
      cursor.close();
    }
  }
}
