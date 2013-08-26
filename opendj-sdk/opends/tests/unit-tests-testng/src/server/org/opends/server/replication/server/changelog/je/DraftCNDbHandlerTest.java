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
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.ChangelogDBIterator;
import org.opends.server.replication.server.changelog.je.DraftCNDB.DraftCNDBCursor;
import org.opends.server.util.StaticUtils;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Test the DraftCNDbHandler class with 2 kinds of cleaning of the db :
 * - periodic trim
 * - call to clear method()
 */
public class DraftCNDbHandlerTest extends ReplicationTestCase
{
  /**
   * This test makes basic operations of a DraftCNDb :
   * - create the db
   * - add records
   * - read them with a cursor
   * - set a very short trim period
   * - wait for the db to be trimmed / here since the changes are not stored in
   *   the replication changelog, the draftCNDb will be cleared.
   *
   * @throws Exception
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
      int sn1 = 3;
      int sn2 = 4;
      int sn3 = 5;

      String value1 = "value1";
      String value2 = "value2";
      String value3 = "value3";

      String baseDN1 = "baseDN1";
      String baseDN2 = "baseDN2";
      String baseDN3 = "baseDN3";

      ChangeNumberGenerator gen = new ChangeNumberGenerator( 1, 0);
      ChangeNumber changeNumber1 = gen.newChangeNumber();
      ChangeNumber changeNumber2 = gen.newChangeNumber();
      ChangeNumber changeNumber3 = gen.newChangeNumber();

      // Add records
      handler.add(sn1, value1, baseDN1, changeNumber1);
      handler.add(sn2, value2, baseDN2, changeNumber2);
      handler.add(sn3, value3, baseDN3, changeNumber3);

      // The ChangeNumber should not get purged
      int firstkey = handler.getFirstKey();
      assertEquals(firstkey, sn1);
      assertEquals(handler.getLastKey(), sn3);

      DraftCNDBCursor dbc = handler.getReadCursor(firstkey);
      try
      {
        assertEquals(dbc.currentChangeNumber(), changeNumber1);
        assertEquals(dbc.currentBaseDN(), baseDN1);
        assertEquals(dbc.currentValue(), value1);
        assertTrue(dbc.toString().length() != 0);

        assertTrue(dbc.next());

        assertEquals(dbc.currentChangeNumber(), changeNumber2);
        assertEquals(dbc.currentBaseDN(), baseDN2);
        assertEquals(dbc.currentValue(), value2);

        assertTrue(dbc.next());

        assertEquals(dbc.currentChangeNumber(), changeNumber3);
        assertEquals(dbc.currentBaseDN(), baseDN3);
        assertEquals(dbc.currentValue(), value3);

        assertFalse(dbc.next());
      }
      finally
      {
        StaticUtils.close(dbc);
      }

      handler.setPurgeDelay(100);

      // Check the db is cleared.
      while(handler.count()!=0)
      {
        Thread.sleep(200);
      }
      assertEquals(handler.getFirstKey(), 0);
      assertEquals(handler.getLastKey(), 0);


    } finally
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
      int sn1 = 3;
      int sn2 = 4;
      int sn3 = 5;

      String value1 = "value1";
      String value2 = "value2";
      String value3 = "value3";

      String baseDN1 = "baseDN1";
      String baseDN2 = "baseDN2";
      String baseDN3 = "baseDN3";

      ChangeNumberGenerator gen = new ChangeNumberGenerator( 1, 0);
      ChangeNumber changeNumber1 = gen.newChangeNumber();
      ChangeNumber changeNumber2 = gen.newChangeNumber();
      ChangeNumber changeNumber3 = gen.newChangeNumber();

      // Add records
      handler.add(sn1, value1, baseDN1, changeNumber1);
      handler.add(sn2, value2, baseDN2, changeNumber2);
      handler.add(sn3, value3, baseDN3, changeNumber3);
      Thread.sleep(500);

      // Checks
      assertEquals(handler.getFirstKey(), sn1);
      assertEquals(handler.getLastKey(), sn3);

      assertEquals(handler.count(), 3, "Db count");

      assertEquals(handler.getPreviousCookie(sn1),value1);
      assertEquals(handler.getPreviousCookie(sn2),value2);
      assertEquals(handler.getPreviousCookie(sn3),value3);

      ChangelogDBIterator it = handler.generateIterator(sn1);
      assertIteratorReadsInOrder(it, sn1, sn2, sn3);

      it = handler.generateIterator(sn2);
      assertIteratorReadsInOrder(it, sn2, sn3);

      it = handler.generateIterator(sn3);
      assertIteratorReadsInOrder(it, sn3);

      handler.clear();

      // Check the db is cleared.
      assertEquals(handler.getFirstKey(), 0);
      assertEquals(handler.getLastKey(), 0);
      assertEquals(handler.count(), 0);
    } finally
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

  private void assertIteratorReadsInOrder(ChangelogDBIterator it, int... sns)
      throws ChangelogException
  {
    try
    {
      for (int i = 0; i < sns.length; i++)
      {
        assertEquals(it.getDraftCN(), sns[i]);
        final boolean isNotLast = i + 1 < sns.length;
        assertEquals(it.next(), isNotLast);
      }
    }
    finally
    {
      it.close();
    }
  }
}
