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
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.io.File;
import java.io.IOException;

import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexRecord;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.types.DN;
import org.opends.server.util.StaticUtils;
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
  private static final String value1 = "value1";
  private static final String value2 = "value2";
  private static final String value3 = "value3";

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
    JEChangeNumberIndexDB cnIndexDB = null;
    try
    {
      replicationServer = newReplicationServer();
      cnIndexDB = newCNIndexDB(replicationServer);
      cnIndexDB.setPurgeDelay(0);

      // Prepare data to be stored in the db
      DN baseDN1 = DN.decode("o=baseDN1");
      DN baseDN2 = DN.decode("o=baseDN2");
      DN baseDN3 = DN.decode("o=baseDN3");

      CSN[] csns = newCSNs(1, 0, 3);

      // Add records
      long cn1 = addRecord(cnIndexDB, value1, baseDN1, csns[0]);
                 addRecord(cnIndexDB, value2, baseDN2, csns[1]);
      long cn3 = addRecord(cnIndexDB, value3, baseDN3, csns[2]);

      // The ChangeNumber should not get purged
      final long oldestCN = cnIndexDB.getOldestRecord().getChangeNumber();
      assertEquals(oldestCN, cn1);
      assertEquals(cnIndexDB.getNewestRecord().getChangeNumber(), cn3);

      DBCursor<ChangeNumberIndexRecord> cursor = cnIndexDB.getCursorFrom(oldestCN);
      try
      {
        assertEqualTo(cursor.getRecord(), csns[0], baseDN1, value1);
        assertTrue(cursor.next());
        assertEqualTo(cursor.getRecord(), csns[1], baseDN2, value2);
        assertTrue(cursor.next());
        assertEqualTo(cursor.getRecord(), csns[2], baseDN3, value3);
        assertFalse(cursor.next());
      }
      finally
      {
        StaticUtils.close(cursor);
      }

      // Now test that the trimming thread does its job => start it
      cnIndexDB.setPurgeDelay(100);
      cnIndexDB.startTrimmingThread();

      // Check the db is cleared.
      while (!cnIndexDB.isEmpty())
      {
        Thread.sleep(200);
      }
      assertNull(cnIndexDB.getOldestRecord());
      assertNull(cnIndexDB.getNewestRecord());
      assertEquals(cnIndexDB.count(), 0);
    }
    finally
    {
      if (cnIndexDB != null)
        cnIndexDB.shutdown();
      remove(replicationServer);
    }
  }

  private long addRecord(JEChangeNumberIndexDB cnIndexDB, String cookie, DN baseDN, CSN csn)
      throws ChangelogException
  {
    return cnIndexDB.addRecord(new ChangeNumberIndexRecord(cookie, baseDN, csn));
  }

  private void assertEqualTo(ChangeNumberIndexRecord record, CSN csn, DN baseDN, String cookie)
  {
    assertEquals(record.getCSN(), csn);
    assertEquals(record.getBaseDN(), baseDN);
    assertEquals(record.getPreviousCookie(), cookie);
  }

  private JEChangeNumberIndexDB newCNIndexDB(ReplicationServer rs) throws Exception
  {
    File testRoot = createCleanDir();
    ReplicationDbEnv dbEnv = new ReplicationDbEnv(testRoot.getPath(), rs);
    JEChangeNumberIndexDB result = new JEChangeNumberIndexDB(rs, dbEnv);
    assertTrue(result.isEmpty());
    return result;
  }

  private File createCleanDir() throws IOException
  {
    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    String path = System.getProperty(TestCaseUtils.PROPERTY_BUILD_DIR, buildRoot
            + File.separator + "build");
    path = path + File.separator + "unit-tests" + File.separator + "JEChangeNumberIndexDB";
    final File testRoot = new File(path);
    TestCaseUtils.deleteDirectory(testRoot);
    testRoot.mkdirs();
    return testRoot;
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
    JEChangeNumberIndexDB cnIndexDB = null;
    try
    {
      replicationServer = newReplicationServer();
      cnIndexDB = newCNIndexDB(replicationServer);
      cnIndexDB.setPurgeDelay(0);

      // Prepare data to be stored in the db

      DN baseDN1 = DN.decode("o=baseDN1");
      DN baseDN2 = DN.decode("o=baseDN2");
      DN baseDN3 = DN.decode("o=baseDN3");

      CSN[] csns = newCSNs(1, 0, 3);

      // Add records
      long cn1 = addRecord(cnIndexDB, value1, baseDN1, csns[0]);
      long cn2 = addRecord(cnIndexDB, value2, baseDN2, csns[1]);
      long cn3 = addRecord(cnIndexDB, value3, baseDN3, csns[2]);

      // Checks
      assertEquals(cnIndexDB.getOldestRecord().getChangeNumber(), cn1);
      assertEquals(cnIndexDB.getNewestRecord().getChangeNumber(), cn3);

      assertEquals(cnIndexDB.count(), 3, "Db count");
      assertFalse(cnIndexDB.isEmpty());

      assertEquals(getPreviousCookie(cnIndexDB, cn1), value1);
      assertEquals(getPreviousCookie(cnIndexDB, cn2), value2);
      assertEquals(getPreviousCookie(cnIndexDB, cn3), value3);

      DBCursor<ChangeNumberIndexRecord> cursor = cnIndexDB.getCursorFrom(cn1);
      assertCursorReadsInOrder(cursor, cn1, cn2, cn3);

      cursor = cnIndexDB.getCursorFrom(cn2);
      assertCursorReadsInOrder(cursor, cn2, cn3);

      cursor = cnIndexDB.getCursorFrom(cn3);
      assertCursorReadsInOrder(cursor, cn3);

      cnIndexDB.clear();

      // Check the db is cleared.
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
