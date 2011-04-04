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
 *      Portions Copyright 2011 ForgeRock AS
 */
package org.opends.server.replication.server;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.net.ServerSocket;

import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.server.DraftCNDB.DraftCNDBCursor;
import org.testng.annotations.Test;

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

      //  find  a free port for the replicationServer
      ServerSocket socket = TestCaseUtils.bindFreePort();
      int changelogPort = socket.getLocalPort();
      socket.close();

      // configure a ReplicationServer.
      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(changelogPort, null, 0,
        2, 0, 100, null);
      replicationServer = new ReplicationServer(conf);

      // create or clean a directory for the DraftCNDbHandler
      String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
      String path = System.getProperty(TestCaseUtils.PROPERTY_BUILD_DIR,
              buildRoot + File.separator + "build");
      path = path + File.separator + "unit-tests" + File.separator + "DraftCNDbHandler";
      testRoot = new File(path);
      if (testRoot.exists())
      {
        TestCaseUtils.deleteDirectory(testRoot);
      }
      testRoot.mkdirs();

      dbEnv = new ReplicationDbEnv(path, replicationServer);

      handler = new DraftCNDbHandler(replicationServer, dbEnv);
      handler.setPurgeDelay(0);

      // Prepare data to be stored in the db
      int sn1 = 3;
      int sn2 = 4;
      int sn3 = 5;

      String value1 = "value1";
      String value2 = "value2";
      String value3 = "value3";

      String serviceID1 = "serviceID1";
      String serviceID2 = "serviceID2";
      String serviceID3 = "serviceID3";

      ChangeNumberGenerator gen = new ChangeNumberGenerator( 1, 0);
      ChangeNumber changeNumber1 = gen.newChangeNumber();
      ChangeNumber changeNumber2 = gen.newChangeNumber();
      ChangeNumber changeNumber3 = gen.newChangeNumber();

      // Add records
      handler.add(sn1, value1, serviceID1, changeNumber1);
      handler.add(sn2, value2, serviceID2, changeNumber2);
      handler.add(sn3, value3, serviceID3, changeNumber3);

      // The ChangeNumber should not get purged
      int firstkey = handler.getFirstKey();
      assertEquals(firstkey, sn1);
      assertEquals(handler.getLastKey(), sn3);

      DraftCNDBCursor dbc = handler.getReadCursor(firstkey);
      try
      {
        assertEquals(dbc.currentChangeNumber(), changeNumber1);
        assertEquals(dbc.currentServiceID(), serviceID1);
        assertEquals(dbc.currentValue(), value1);
        assertTrue(dbc.toString().length() != 0);

        assertTrue(dbc.next());

        assertEquals(dbc.currentChangeNumber(), changeNumber2);
        assertEquals(dbc.currentServiceID(), serviceID2);
        assertEquals(dbc.currentValue(), value2);

        assertTrue(dbc.next());

        assertEquals(dbc.currentChangeNumber(), changeNumber3);
        assertEquals(dbc.currentServiceID(), serviceID3);
        assertEquals(dbc.currentValue(), value3);

        assertFalse(dbc.next());
      }
      finally
      {
        handler.releaseReadCursor(dbc);
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
      if (testRoot != null)
        TestCaseUtils.deleteDirectory(testRoot);
    }
  }

  /**
   * This test makes basic operations of a DraftCNDb and explicitely call
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

      //  find  a free port for the replicationServer
      ServerSocket socket = TestCaseUtils.bindFreePort();
      int changelogPort = socket.getLocalPort();
      socket.close();

      // configure a ReplicationServer.
      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(changelogPort, null, 0,
        2, 0, 100, null);
      replicationServer = new ReplicationServer(conf);

      // create or clean a directory for the DraftCNDbHandler
      String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
      String path = System.getProperty(TestCaseUtils.PROPERTY_BUILD_DIR,
              buildRoot + File.separator + "build");
      path = path + File.separator + "unit-tests" + File.separator + "DraftCNDbHandler";
      testRoot = new File(path);
      if (testRoot.exists())
      {
        TestCaseUtils.deleteDirectory(testRoot);
      }
      testRoot.mkdirs();

      dbEnv = new ReplicationDbEnv(path, replicationServer);

      handler = new DraftCNDbHandler(replicationServer, dbEnv);
      handler.setPurgeDelay(0);

      //
      assertTrue(handler.count()==0);

      // Prepare data to be stored in the db
      int sn1 = 3;
      int sn2 = 4;
      int sn3 = 5;

      String value1 = "value1";
      String value2 = "value2";
      String value3 = "value3";

      String serviceID1 = "serviceID1";
      String serviceID2 = "serviceID2";
      String serviceID3 = "serviceID3";

      ChangeNumberGenerator gen = new ChangeNumberGenerator( 1, 0);
      ChangeNumber changeNumber1 = gen.newChangeNumber();
      ChangeNumber changeNumber2 = gen.newChangeNumber();
      ChangeNumber changeNumber3 = gen.newChangeNumber();

      // Add records
      handler.add(sn1, value1, serviceID1, changeNumber1);
      handler.add(sn2, value2, serviceID2, changeNumber2);
      handler.add(sn3, value3, serviceID3, changeNumber3);
      Thread.sleep(500);

      // Checks
      assertEquals(handler.getFirstKey(), sn1);
      assertEquals(handler.getLastKey(), sn3);

      assertEquals(handler.count(), 3, "Db count");

      assertEquals(handler.getValue(sn1),value1);
      assertEquals(handler.getValue(sn2),value2);
      assertEquals(handler.getValue(sn3),value3);

      DraftCNDbIterator it = handler.generateIterator(sn1);
      try
      {
        assertEquals(it.getDraftCN(), sn1);
        assertTrue(it.next());
        assertEquals(it.getDraftCN(), sn2);
        assertTrue(it.next());
        assertEquals(it.getDraftCN(), sn3);
        assertFalse(it.next());
      }
      finally
      {
        it.releaseCursor();
      }

      it = handler.generateIterator(sn2);
      try
      {
        assertEquals(it.getDraftCN(), sn2);
        assertTrue(it.next());
        assertEquals(it.getDraftCN(), sn3);
        assertFalse(it.next());
      }
      finally
      {
        it.releaseCursor();
      }

      it = handler.generateIterator(sn3);
      try
      {
        assertEquals(it.getDraftCN(), sn3);
        assertFalse(it.next());
      }
      finally
      {
        it.releaseCursor();
      }

      // Clear ...
      handler.clear();

      // Check the db is cleared.
      assertEquals(handler.getFirstKey(), 0);
      assertEquals(handler.getLastKey(), 0);
      assertTrue(handler.count()==0);

    } finally
    {
      if (handler != null)
        handler.shutdown();
      if (dbEnv != null)
        dbEnv.shutdown();
      if (replicationServer != null)
        replicationServer.remove();
      if (testRoot != null)
        TestCaseUtils.deleteDirectory(testRoot);
    }
  }
}
