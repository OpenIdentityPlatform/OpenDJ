/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.server;

import static org.opends.server.TestCaseUtils.*;
import static org.testng.Assert.*;

import java.net.ServerSocket;

import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.TestCaseUtils;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.service.ReplicationBroker;
import org.forgerock.opendj.ldap.DN;
import org.testng.annotations.Test;

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
      ReplServerFakeConfiguration conf = new ReplServerFakeConfiguration(ports[0], null, 0, 1, 0, 0, null);
      replicationServer = new ReplicationServer(conf);
      assertTrue(replicationServer.isListening(), "the replication server should listen on port " + ports[0]);

      // Most of the configuration change are trivial to apply.
      // The interesting change is the change of the replication server port.
      // build a new ReplServerFakeConfiguration with a new server port
      // apply this new configuration and check that it is now possible to
      // connect to this new portnumber.
      ReplServerFakeConfiguration newconf = new ReplServerFakeConfiguration(ports[1], null, 0, 1, 0, 0, null);
      replicationServer.applyConfigurationChange(newconf);

      ReplicationBroker broker = openReplicationSession(
          DN.valueOf(TEST_ROOT_DN_STRING), 1, 10, ports[1], 1000);

      // check that the sendWindow is not null to make sure that the
      // broker did connect successfully.
      assertTrue(broker.getCurrentSendWindow() != 0);
      assertTrue(replicationServer.isListening(), "the replication server should listen on port " + ports[1]);
    }
    finally
    {
      remove(replicationServer);
    }
  }

  /**
   * Tests that a replication server whose listen port cannot be bound fails fast instead
   * of silently starting without any listener, which used to surface much later, and in
   * an unrelated place, as a "connection refused".
   */
  @Test
  public void replServerFailsWhenListenPortIsInUse() throws Exception
  {
    TestCaseUtils.startServer();

    final int instancesBefore = ReplicationServer.getAllInstances().size();
    // Keep the port bound for the whole lifetime of the replication server creation.
    try (ServerSocket portHolder = TestCaseUtils.bindFreePort())
    {
      final ReplServerFakeConfiguration conf = new ReplServerFakeConfiguration(
          portHolder.getLocalPort(), "replServerFailsWhenListenPortIsInUseDb", 0, 1, 0, 0, null);
      try
      {
        final ReplicationServer replicationServer = new ReplicationServer(conf);
        remove(replicationServer);
        fail("Creating a replication server on a port already in use should have failed");
      }
      catch (ConfigException expected)
      {
        // The failed replication server must not be left registered anywhere.
        assertEquals(ReplicationServer.getAllInstances().size(), instancesBefore);
      }
    }
  }
}
