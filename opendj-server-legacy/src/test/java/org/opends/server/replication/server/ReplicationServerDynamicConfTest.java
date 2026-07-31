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
import static org.opends.server.util.StaticUtils.*;
import static org.testng.Assert.*;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.ChangelogBackend;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.types.VirtualAttributeRule;
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
   * an unrelated place, as a "connection refused", and that aborting its initialization
   * leaves the external changelog of the replication server which is already running
   * untouched: the virtual attribute rules are registered globally, by attribute name.
   */
  @Test
  public void replServerFailsWhenListenPortIsInUse() throws Exception
  {
    TestCaseUtils.startServer();

    ReplicationServer runningServer = null;
    try
    {
      final int[] ports = TestCaseUtils.findFreePorts(1);
      runningServer = new ReplicationServer(new ReplServerFakeConfiguration(
          ports[0], "replServerFailsWhenListenPortIsInUseRunningDb", 0, 1, 0, 0, null));
      assertTrue(runningServer.isListening());

      final List<String> rulesBefore = changelogVirtualAttributeNames();
      assertFalse(rulesBefore.isEmpty(), "the running replication server should provide the external changelog");
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
          // The failed replication server must not be left registered anywhere,
          assertEquals(ReplicationServer.getAllInstances().size(), instancesBefore);
          // nor must it release what it never acquired.
          assertEquals(changelogVirtualAttributeNames(), rulesBefore,
              "aborting the initialization must not deregister the virtual attribute rules"
                  + " of the running replication server");
          assertTrue(DirectoryServer.getInstance().getServerContext().getBackendConfigManager()
              .hasLocalBackend(ChangelogBackend.BACKEND_ID), "the changelog backend should still be registered");
          assertTrue(runningServer.isListening(), "the running replication server should still listen");
        }
      }
    }
    finally
    {
      remove(runningServer);
    }
  }

  /**
   * Tests that a listen port which is only momentarily unavailable, as it happens when a
   * socket holding it is being closed, does not prevent the replication server from
   * starting: {@code bindListenPort()} retries the bind a few times.
   */
  @Test
  public void replServerRetriesToBindItsListenPort() throws Exception
  {
    TestCaseUtils.startServer();

    ReplicationServer replicationServer = null;
    final ServerSocket portHolder = TestCaseUtils.bindFreePort();
    try
    {
      // Release the port while the replication server is retrying to bind it. The retry
      // gives 800 ms in total, which leaves a wide margin for this thread to be scheduled.
      final Thread portReleaser = new Thread(() -> {
        try
        {
          Thread.sleep(300);
        }
        catch (InterruptedException e)
        {
          Thread.currentThread().interrupt();
        }
        close(portHolder);
      }, "port releaser of replServerRetriesToBindItsListenPort");
      portReleaser.start();

      replicationServer = new ReplicationServer(new ReplServerFakeConfiguration(
          portHolder.getLocalPort(), "replServerRetriesToBindItsListenPortDb", 0, 1, 0, 0, null));
      portReleaser.join();

      assertTrue(replicationServer.isListening(),
          "the replication server should have bound the port which was released while it was retrying");
    }
    finally
    {
      close(portHolder);
      remove(replicationServer);
    }
  }

  /**
   * Tests that a port change to a port which is not available is rejected, and that a
   * replication server which nevertheless goes through the change goes back to its
   * previous listen port instead of staying deaf with a configuration which advertises a
   * port it does not listen to.
   */
  @Test
  public void replServerRollsBackAFailedPortChange() throws Exception
  {
    TestCaseUtils.startServer();

    ReplicationServer replicationServer = null;
    try
    {
      final int[] ports = TestCaseUtils.findFreePorts(1);
      replicationServer = new ReplicationServer(new ReplServerFakeConfiguration(
          ports[0], "replServerRollsBackAFailedPortChangeDb", 0, 1, 0, 0, null));
      assertTrue(replicationServer.isListening());

      try (ServerSocket portHolder = TestCaseUtils.bindFreePort())
      {
        final ReplServerFakeConfiguration newConf = new ReplServerFakeConfiguration(
            portHolder.getLocalPort(), "replServerRollsBackAFailedPortChangeDb", 0, 1, 0, 0, null);

        final List<LocalizableMessage> unacceptableReasons = new ArrayList<>();
        assertFalse(replicationServer.isConfigurationChangeAcceptable(newConf, unacceptableReasons),
            "a change to a listen port which is in use should not be acceptable");
        assertFalse(unacceptableReasons.isEmpty(), "the rejected change should say why it was rejected");

        final ConfigChangeResult ccr = replicationServer.applyConfigurationChange(newConf);
        assertEquals(ccr.getResultCode(), ResultCode.OPERATIONS_ERROR);
        assertFalse(ccr.getMessages().isEmpty(), "the failed change should say why it failed");
        assertEquals(replicationServer.getReplicationPort(), ports[0],
            "the replication server should have gone back to its previous listen port");
        assertTrue(replicationServer.isListening(), "the replication server should still listen");
      }

      // and it must still be usable on its original port.
      ReplicationBroker broker = openReplicationSession(
          DN.valueOf(TEST_ROOT_DN_STRING), 1, 10, ports[0], 1000);
      assertTrue(broker.getCurrentSendWindow() != 0);
    }
    finally
    {
      remove(replicationServer);
    }
  }

  /** Returns the names of the virtual attributes provided by the external changelog. */
  private List<String> changelogVirtualAttributeNames()
  {
    final Collection<String> changelogAttributes = Arrays.asList(
        "lastexternalchangelogcookie", "firstchangenumber", "lastchangenumber", "changelog");
    final List<String> names = new ArrayList<>();
    for (VirtualAttributeRule rule : DirectoryServer.getVirtualAttributes())
    {
      final String name = rule.getAttributeType().getNameOrOID().toLowerCase();
      if (changelogAttributes.contains(name))
      {
        names.add(name);
      }
    }
    Collections.sort(names);
    return names;
  }
}
