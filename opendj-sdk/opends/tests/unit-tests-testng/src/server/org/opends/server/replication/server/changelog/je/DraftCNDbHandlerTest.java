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
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.CNIndexData;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexDB;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexDBCursor;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.je.DraftCNDB.DraftCNDBCursor;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.Test;

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
    File testRoot = null;
    ReplicationServer replicationServer = null;
    ReplicationDbEnv dbEnv = null;
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

      testRoot = createCleanDir();
      dbEnv = new ReplicationDbEnv(testRoot.getPath(), replicationServer);

      handler = new DraftCNDbHandler(replicationServer, dbEnv);
      handler.setPurgeDelay(0);

      // Prepare data to be stored in the db
      int cn1 = 3;
      int cn2 = 4;
      int cn3 = 5;

      String value1 = "value1";
      String value2 = "value2";
      String value3 = "value3";

      String baseDN1 = "baseDN1";
      String baseDN2 = "baseDN2";
      String baseDN3 = "baseDN3";

      CSNGenerator gen = new CSNGenerator(1, 0);
      CSN csn1 = gen.newCSN();
      CSN csn2 = gen.newCSN();
      CSN csn3 = gen.newCSN();

      // Add records
      handler.add(new CNIndexData(cn1, value1, baseDN1, csn1));
      handler.add(new CNIndexData(cn2, value2, baseDN2, csn2));
      handler.add(new CNIndexData(cn3, value3, baseDN3, csn3));

      // The ChangeNumber should not get purged
      final long firstChangeNumber = getFirstChangeNumber(handler);
      assertEquals(firstChangeNumber, cn1);
      assertEquals(getLastChangeNumber(handler), cn3);

      DraftCNDBCursor dbc = handler.getReadCursor(firstChangeNumber);
      try
      {
        assertEqualTo(dbc.currentData(), csn1, baseDN1, value1);
        assertTrue(dbc.toString().length() != 0);

        assertTrue(dbc.next());
        assertEqualTo(dbc.currentData(), csn2, baseDN2, value2);

        assertTrue(dbc.next());
        assertEqualTo(dbc.currentData(), csn3, baseDN3, value3);

        assertFalse(dbc.next());
      }
      finally
      {
        StaticUtils.close(dbc);
      }

      handler.setPurgeDelay(100);

      // Check the db is cleared.
      while (handler.count() != 0)
      {
        Thread.sleep(200);
      }
      assertEquals(getFirstChangeNumber(handler), 0);
      assertEquals(getLastChangeNumber(handler), 0);
    }
    finally
    {
      if (handler != null)
        handler.shutdown();
      if (dbEnv != null)
        dbEnv.shutdown();
      if (replicationServer != null)
        replicationServer.remove();
      TestCaseUtils.deleteDirectory(testRoot);
    }
  }

  private void assertEqualTo(CNIndexData data, CSN csn, String baseDN,
      String cookie)
  {
    assertEquals(data.getCSN(), csn);
    assertEquals(data.getBaseDN(), baseDN);
    assertEquals(data.getPreviousCookie(), cookie);
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
    File testRoot = null;
    ReplicationServer replicationServer = null;
    ReplicationDbEnv dbEnv = null;
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

      testRoot = createCleanDir();
      dbEnv = new ReplicationDbEnv(testRoot.getAbsolutePath(), replicationServer);

      handler = new DraftCNDbHandler(replicationServer, dbEnv);
      handler.setPurgeDelay(0);

      assertTrue(handler.isEmpty());

      // Prepare data to be stored in the db
      int cn1 = 3;
      int cn2 = 4;
      int cn3 = 5;

      String value1 = "value1";
      String value2 = "value2";
      String value3 = "value3";

      String baseDN1 = "baseDN1";
      String baseDN2 = "baseDN2";
      String baseDN3 = "baseDN3";

      CSNGenerator gen = new CSNGenerator(1, 0);
      CSN csn1 = gen.newCSN();
      CSN csn2 = gen.newCSN();
      CSN csn3 = gen.newCSN();

      // Add records
      handler.add(new CNIndexData(cn1, value1, baseDN1, csn1));
      handler.add(new CNIndexData(cn2, value2, baseDN2, csn2));
      handler.add(new CNIndexData(cn3, value3, baseDN3, csn3));
      Thread.sleep(500);

      // Checks
      assertEquals(getFirstChangeNumber(handler), cn1);
      assertEquals(getLastChangeNumber(handler), cn3);

      assertEquals(handler.count(), 3, "Db count");

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
      assertEquals(getFirstChangeNumber(handler), 0);
      assertEquals(getLastChangeNumber(handler), 0);
      assertEquals(handler.count(), 0);
    }
    finally
    {
      if (handler != null)
        handler.shutdown();
      if (dbEnv != null)
        dbEnv.shutdown();
      if (replicationServer != null)
        replicationServer.remove();
      TestCaseUtils.deleteDirectory(testRoot);
    }
  }

  private long getFirstChangeNumber(ChangeNumberIndexDB handler) throws Exception
  {
    return handler.getFirstCNIndexData().getChangeNumber();
  }

  private long getLastChangeNumber(ChangeNumberIndexDB handler) throws Exception
  {
    return handler.getLastCNIndexData().getChangeNumber();
  }

  private String getPreviousCookie(DraftCNDbHandler handler, long changeNumber) throws Exception
  {
    ChangeNumberIndexDBCursor cursor = handler.getCursorFrom(changeNumber);
    try
    {
      return cursor.getCNIndexData().getPreviousCookie();
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
        assertEquals(cursor.getCNIndexData().getChangeNumber(), sns[i]);
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
