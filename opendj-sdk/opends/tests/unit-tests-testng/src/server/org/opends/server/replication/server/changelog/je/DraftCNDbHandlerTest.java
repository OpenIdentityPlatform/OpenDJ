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

import static org.opends.server.replication.server.changelog.je.DbHandlerTest.*;
import static org.testng.Assert.*;

/**
 * Test the DraftCNDbHandler class with 2 kinds of cleaning of the db : -
 * periodic trim - call to clear method()
 */
@SuppressWarnings("javadoc")
public class DraftCNDbHandlerTest extends ReplicationTestCase
{
  /**
   * This test makes basic operations of a DraftCNDb : - create the db - add
   * records - read them with a cursor - set a very short trim period - wait for
   * the db to be trimmed / here since the changes are not stored in the
   * replication changelog, the draftCNDb will be cleared.
   */
  @Test()
  void testDraftCNDbHandlerTrim() throws Exception
  {
    ReplicationServer replicationServer = null;
    DraftCNDbHandler handler = null;
    try
    {
      TestCaseUtils.startServer();

      int changelogPort = TestCaseUtils.findFreePort();

      // configure a ReplicationServer.
      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(changelogPort, null, 0,
        2, 0, 100, null);
      replicationServer = new ReplicationServer(conf);

      handler = newDraftCNDbHandler(replicationServer);
      handler.setPurgeDelay(0);

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
      handler.addRecord(new CNIndexRecord(cn1, value1, baseDN1, csns[0]));
      handler.addRecord(new CNIndexRecord(cn2, value2, baseDN2, csns[1]));
      handler.addRecord(new CNIndexRecord(cn3, value3, baseDN3, csns[2]));

      // The ChangeNumber should not get purged
      final long firstChangeNumber = handler.getFirstRecord().getChangeNumber();
      assertEquals(firstChangeNumber, cn1);
      assertEquals(handler.getLastRecord().getChangeNumber(), cn3);

      DraftCNDBCursor dbc = handler.getReadCursor(firstChangeNumber);
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

      handler.setPurgeDelay(100);

      // Check the db is cleared.
      while (!handler.isEmpty())
      {
        Thread.sleep(200);
      }
      assertNull(handler.getFirstRecord());
      assertNull(handler.getLastRecord());
      assertEquals(handler.count(), 0);
    }
    finally
    {
      if (handler != null)
        handler.shutdown();
      remove(replicationServer);
    }
  }

  private void assertEqualTo(CNIndexRecord data, CSN csn, DN baseDN, String cookie)
  {
    assertEquals(data.getCSN(), csn);
    assertEquals(data.getBaseDN(), baseDN);
    assertEquals(data.getPreviousCookie(), cookie);
  }

  private DraftCNDbHandler newDraftCNDbHandler(ReplicationServer rs) throws Exception
  {
    File testRoot = createCleanDir();
    ReplicationDbEnv dbEnv = new ReplicationDbEnv(testRoot.getPath(), rs);
    return new DraftCNDbHandler(rs, dbEnv);
  }

  private File createCleanDir() throws IOException
  {
    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    String path = System.getProperty(TestCaseUtils.PROPERTY_BUILD_DIR, buildRoot
            + File.separator + "build");
    path = path + File.separator + "unit-tests" + File.separator + "DraftCNDbHandler";
    final File testRoot = new File(path);
    TestCaseUtils.deleteDirectory(testRoot);
    testRoot.mkdirs();
    return testRoot;
  }

  /**
   * This test makes basic operations of a DraftCNDb and explicitly calls
   * the clear() method instead of waiting for the periodic trim to clear
   * it.
   * - create the db
   * - add records
   * - read them with a cursor
   * - clear the db.
   * @throws Exception
   */
  @Test()
  void testDraftCNDbHandlerClear() throws Exception
  {
    ReplicationServer replicationServer = null;
    DraftCNDbHandler handler = null;
    try
    {
      TestCaseUtils.startServer();

      int changelogPort = TestCaseUtils.findFreePort();

      // configure a ReplicationServer.
      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(changelogPort, null, 0,
        2, 0, 100, null);
      replicationServer = new ReplicationServer(conf);

      handler = newDraftCNDbHandler(replicationServer);
      handler.setPurgeDelay(0);

      assertTrue(handler.isEmpty());

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
      handler.addRecord(new CNIndexRecord(cn1, value1, baseDN1, csns[0]));
      handler.addRecord(new CNIndexRecord(cn2, value2, baseDN2, csns[1]));
      handler.addRecord(new CNIndexRecord(cn3, value3, baseDN3, csns[2]));
      Thread.sleep(500);

      // Checks
      assertEquals(handler.getFirstRecord().getChangeNumber(), cn1);
      assertEquals(handler.getLastRecord().getChangeNumber(), cn3);

      assertEquals(handler.count(), 3, "Db count");
      assertFalse(handler.isEmpty());

      assertEquals(getPreviousCookie(handler, cn1), value1);
      assertEquals(getPreviousCookie(handler, cn2), value2);
      assertEquals(getPreviousCookie(handler, cn3), value3);

      ChangeNumberIndexDBCursor cursor = handler.getCursorFrom(cn1);
      assertCursorReadsInOrder(cursor, cn1, cn2, cn3);

      cursor = handler.getCursorFrom(cn2);
      assertCursorReadsInOrder(cursor, cn2, cn3);

      cursor = handler.getCursorFrom(cn3);
      assertCursorReadsInOrder(cursor, cn3);

      handler.clear();

      // Check the db is cleared.
      assertNull(handler.getFirstRecord());
      assertNull(handler.getLastRecord());
      assertEquals(handler.count(), 0);
      assertTrue(handler.isEmpty());
    }
    finally
    {
      if (handler != null)
        handler.shutdown();
      remove(replicationServer);
    }
  }

  private String getPreviousCookie(DraftCNDbHandler handler, long changeNumber) throws Exception
  {
    ChangeNumberIndexDBCursor cursor = handler.getCursorFrom(changeNumber);
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
