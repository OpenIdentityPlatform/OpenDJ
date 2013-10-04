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
import org.opends.server.replication.server.changelog.api.CNIndexRecord;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexDBCursor;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.je.DraftCNDB.DraftCNDBCursor;
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
      TestCaseUtils.startServer();

      int changelogPort = TestCaseUtils.findFreePort();

      // configure a ReplicationServer.
      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(changelogPort, null, 0,
        2, 0, 100, null);
      replicationServer = new ReplicationServer(conf);

      cnIndexDB = newCNIndexDB(replicationServer);
      cnIndexDB.setPurgeDelay(0);

      // Prepare data to be stored in the db
      int cn1 = 3;
      int cn2 = 4;
      int cn3 = 5;

      String value1 = "value1";
      String value2 = "value2";
      String value3 = "value3";

      DN baseDN1 = DN.decode("o=baseDN1");
      DN baseDN2 = DN.decode("o=baseDN2");
      DN baseDN3 = DN.decode("o=baseDN3");

      CSN[] csns = newCSNs(1, 0, 3);

      // Add records
      cnIndexDB.addRecord(new CNIndexRecord(cn1, value1, baseDN1, csns[0]));
      cnIndexDB.addRecord(new CNIndexRecord(cn2, value2, baseDN2, csns[1]));
      cnIndexDB.addRecord(new CNIndexRecord(cn3, value3, baseDN3, csns[2]));

      // The ChangeNumber should not get purged
      final long firstChangeNumber = cnIndexDB.getFirstRecord().getChangeNumber();
      assertEquals(firstChangeNumber, cn1);
      assertEquals(cnIndexDB.getLastRecord().getChangeNumber(), cn3);

      DraftCNDBCursor dbc = cnIndexDB.getReadCursor(firstChangeNumber);
      try
      {
        assertEqualTo(dbc.currentRecord(), csns[0], baseDN1, value1);
        assertTrue(dbc.toString().length() != 0);

        assertTrue(dbc.next());
        assertEqualTo(dbc.currentRecord(), csns[1], baseDN2, value2);

        assertTrue(dbc.next());
        assertEqualTo(dbc.currentRecord(), csns[2], baseDN3, value3);

        assertFalse(dbc.next());
      }
      finally
      {
        StaticUtils.close(dbc);
      }

      cnIndexDB.setPurgeDelay(100);

      // Check the db is cleared.
      while (!cnIndexDB.isEmpty())
      {
        Thread.sleep(200);
      }
      assertNull(cnIndexDB.getFirstRecord());
      assertNull(cnIndexDB.getLastRecord());
      assertEquals(cnIndexDB.count(), 0);
    }
    finally
    {
      if (cnIndexDB != null)
        cnIndexDB.shutdown();
      remove(replicationServer);
    }
  }

  private void assertEqualTo(CNIndexRecord data, CSN csn, DN baseDN, String cookie)
  {
    assertEquals(data.getCSN(), csn);
    assertEquals(data.getBaseDN(), baseDN);
    assertEquals(data.getPreviousCookie(), cookie);
  }

  private JEChangeNumberIndexDB newCNIndexDB(ReplicationServer rs) throws Exception
  {
    File testRoot = createCleanDir();
    ReplicationDbEnv dbEnv = new ReplicationDbEnv(testRoot.getPath(), rs);
    return new JEChangeNumberIndexDB(rs, dbEnv);
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
      TestCaseUtils.startServer();

      int changelogPort = TestCaseUtils.findFreePort();

      // configure a ReplicationServer.
      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(changelogPort, null, 0,
        2, 0, 100, null);
      replicationServer = new ReplicationServer(conf);

      cnIndexDB = newCNIndexDB(replicationServer);
      cnIndexDB.setPurgeDelay(0);

      assertTrue(cnIndexDB.isEmpty());

      // Prepare data to be stored in the db
      int cn1 = 3;
      int cn2 = 4;
      int cn3 = 5;

      String value1 = "value1";
      String value2 = "value2";
      String value3 = "value3";

      DN baseDN1 = DN.decode("o=baseDN1");
      DN baseDN2 = DN.decode("o=baseDN2");
      DN baseDN3 = DN.decode("o=baseDN3");

      CSN[] csns = newCSNs(1, 0, 3);

      // Add records
      cnIndexDB.addRecord(new CNIndexRecord(cn1, value1, baseDN1, csns[0]));
      cnIndexDB.addRecord(new CNIndexRecord(cn2, value2, baseDN2, csns[1]));
      cnIndexDB.addRecord(new CNIndexRecord(cn3, value3, baseDN3, csns[2]));
      Thread.sleep(500);

      // Checks
      assertEquals(cnIndexDB.getFirstRecord().getChangeNumber(), cn1);
      assertEquals(cnIndexDB.getLastRecord().getChangeNumber(), cn3);

      assertEquals(cnIndexDB.count(), 3, "Db count");
      assertFalse(cnIndexDB.isEmpty());

      assertEquals(getPreviousCookie(cnIndexDB, cn1), value1);
      assertEquals(getPreviousCookie(cnIndexDB, cn2), value2);
      assertEquals(getPreviousCookie(cnIndexDB, cn3), value3);

      ChangeNumberIndexDBCursor cursor = cnIndexDB.getCursorFrom(cn1);
      assertCursorReadsInOrder(cursor, cn1, cn2, cn3);

      cursor = cnIndexDB.getCursorFrom(cn2);
      assertCursorReadsInOrder(cursor, cn2, cn3);

      cursor = cnIndexDB.getCursorFrom(cn3);
      assertCursorReadsInOrder(cursor, cn3);

      cnIndexDB.clear();

      // Check the db is cleared.
      assertNull(cnIndexDB.getFirstRecord());
      assertNull(cnIndexDB.getLastRecord());
      assertEquals(cnIndexDB.count(), 0);
      assertTrue(cnIndexDB.isEmpty());
    }
    finally
    {
      if (cnIndexDB != null)
        cnIndexDB.shutdown();
      remove(replicationServer);
    }
  }

  private String getPreviousCookie(JEChangeNumberIndexDB cnIndexDB,
      long changeNumber) throws Exception
  {
    ChangeNumberIndexDBCursor cursor = cnIndexDB.getCursorFrom(changeNumber);
    try
    {
      return cursor.getRecord().getPreviousCookie();
    }
    finally
    {
      StaticUtils.close(cursor);
    }
  }

  private void assertCursorReadsInOrder(ChangeNumberIndexDBCursor cursor,
      int... sns) throws ChangelogException
  {
    try
    {
      for (int i = 0; i < sns.length; i++)
      {
        assertEquals(cursor.getRecord().getChangeNumber(), sns[i]);
        final boolean isNotLast = i + 1 < sns.length;
        assertEquals(cursor.next(), isNotLast);
      }
    }
    finally
    {
      cursor.close();
    }
  }
}
