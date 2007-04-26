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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.replication.server;

import java.io.File;
import java.net.ServerSocket;

import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.ReplicationDbEnv;
import org.opends.server.replication.server.DbHandler;
import org.opends.server.types.DN;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Test the dbHandler class
 */
public class dbHandlerTest extends ReplicationTestCase
{
  @Test()
  void testDbHandlerTrim() throws Exception
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
    ReplicationServer replicationServer = new ReplicationServer(conf);

    // create or clean a directory for the dbHandler
    String buildRoot = System.getProperty(TestCaseUtils.PROPERTY_BUILD_ROOT);
    String path = buildRoot + File.separator + "build" + File.separator +
                  "unit-tests" + File.separator + "dbHandler";
    File testRoot = new File(path);
    if (testRoot.exists())
    {
      TestCaseUtils.deleteDirectory(testRoot);
    }
    testRoot.mkdirs();

    ReplicationDbEnv dbEnv = new ReplicationDbEnv(path, replicationServer);

    DbHandler handler =
      new DbHandler((short) 1, DN.decode("o=test"), replicationServer, dbEnv);

    ChangeNumberGenerator gen = new ChangeNumberGenerator((short)1, 0);
    ChangeNumber changeNumber1 = gen.NewChangeNumber();
    ChangeNumber changeNumber2 = gen.NewChangeNumber();
    ChangeNumber changeNumber3 = gen.NewChangeNumber();

    DeleteMsg update1 = new DeleteMsg("o=test", changeNumber1, "uid");
    DeleteMsg update2 = new DeleteMsg("o=test", changeNumber2, "uid");
    DeleteMsg update3 = new DeleteMsg("o=test", changeNumber3, "uid");

    handler.add(update1);
    handler.add(update2);
    handler.add(update3);

    // The ChangeNumber should not get purged
    assertEquals(changeNumber1, handler.getFirstChange());
    assertEquals(changeNumber3, handler.getLastChange());

    handler.setPurgeDelay(1);

    boolean purged = false;
    int count = 300;  // wait at most 60 seconds
    while (!purged && (count>0))
    {
      ChangeNumber firstChange = handler.getFirstChange();
      ChangeNumber lastChange = handler.getLastChange();
      if ((!firstChange.equals(changeNumber3) ||
          (!lastChange.equals(changeNumber3))))
      {
        TestCaseUtils.sleep(100);
      }
      else
      {
        purged = true;
      }
    }

    handler.shutdown();
    dbEnv.shutdown();
    replicationServer.shutdown();

    TestCaseUtils.deleteDirectory(testRoot);
  }

}
