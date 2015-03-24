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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.replication.server;

import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.DN;
import org.testng.annotations.Test;

import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

/**
 * Tests that we can dynamically modify the configuration of replicationServer.
 */
@SuppressWarnings("javadoc")
public class ReplicationServerDynamicConfTest extends ReplicationTestCase
{
  /**
   * Tests the applyConfigurationChange method of the ReplicationServer
   * class.
   */
  @Test
  public void replServerApplyChangeTest() throws Exception
  {
    TestCaseUtils.startServer();

    ReplicationServer replicationServer = null;
    try {
      int[] ports = TestCaseUtils.findFreePorts(2);

      // instantiate a Replication server using the first port number.
      ReplServerFakeConfiguration conf =
        new ReplServerFakeConfiguration(
            ports[0], null, replicationDbImplementation, 0, 1, 0, 0, null);
      replicationServer = new ReplicationServer(conf);

      // Most of the configuration change are trivial to apply.
      // The interesting change is the change of the replication server port.
      // build a new ReplServerFakeConfiguration with a new server port
      // apply this new configuration and check that it is now possible to
      // connect to this new portnumber.
      ReplServerFakeConfiguration newconf =
        new ReplServerFakeConfiguration(
            ports[1], null, replicationDbImplementation, 0, 1, 0, 0, null);

      replicationServer.applyConfigurationChange(newconf);

      ReplicationBroker broker = openReplicationSession(
          DN.valueOf(TEST_ROOT_DN_STRING), 1, 10, ports[1], 1000);

      // check that the sendWindow is not null to make sure that the
      // broker did connect successfully.
      assertTrue(broker.getCurrentSendWindow() != 0);
    }
    finally
    {
      remove(replicationServer);
    }
  }
}
