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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.server;

import static org.testng.Assert.assertTrue;

import java.net.ServerSocket;

import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.plugin.ReplicationBroker;
import org.opends.server.types.DN;
import org.testng.annotations.Test;

/**
 * Tests that we can dynamically modify the configuration of replicationServer 
 */

public class ReplicationServerDynamicConfTest extends ReplicationTestCase
{
  /**
   * That that the applyConfigurationChange methos of the ReplicationServer
   * class.
   */
  @Test()
  public void replServerApplyChangeTest() throws Exception
  {
    ReplicationServer replicationServer = null;
    
    TestCaseUtils.startServer();

    try {
      // find two free ports for the replication Server port
      ServerSocket socket1 = TestCaseUtils.bindFreePort();
      int replicationServerPort = socket1.getLocalPort();
      ServerSocket socket2 = TestCaseUtils.bindFreePort();
      int newReplicationServerPort = socket2.getLocalPort();
      socket1.close();
      socket2.close();

      // instantiate a Replication server using the first port number.
      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(
            replicationServerPort, null, 0, 1, 0, 0, null);
      replicationServer = new ReplicationServer(conf);

      // Most of the configuration change are trivial to apply.
      // The interesting change is the change of the replication server port.
      // build a new ReplServerFakeConfiguration with a new server port
      // apply this new configuration and check that it is now possible to 
      // connect to this new portnumber.
      ReplServerFakeConfiguration newconf =
        new ReplServerFakeConfiguration(
            newReplicationServerPort, null, 0, 1, 0, 0, null);

      replicationServer.applyConfigurationChange(newconf);

      ReplicationBroker broker = openReplicationSession(
          DN.decode("dc=example"), (short) 1, 10, newReplicationServerPort,
          1000, false);

      // check that the sendWindow is not null to make sure that the 
      // broker did connect successfully.
      assertTrue(broker.getCurrentSendWindow() != 0);
    }
    finally 
    {
      replicationServer.shutdown();
    }
  }
}
