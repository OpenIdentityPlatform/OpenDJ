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

import java.io.File;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.server.changelog.api.ChangelogException;
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
      final String dbDirName = "replServerFailsWhenListenPortIsInUseDb";
      try (ServerSocket portHolder = TestCaseUtils.bindFreePort())
      {
        final ReplServerFakeConfiguration conf = new ReplServerFakeConfiguration(
            portHolder.getLocalPort(), dbDirName, 0, 1, 0, 0, null);
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
        finally
        {
          // The aborted instance is never handed to the test, so its changelog cannot be
          // removed through ReplicationTestCase.remove().
          recursiveDelete(getFileForPath(dbDirName));
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
   * <p>
   * The port is released as soon as the replication server has actually failed to bind it,
   * so the retry is the only thing which can make it start: a test releasing the port after
   * a delay would silently stop exercising the retry as soon as the replication server took
   * longer than that delay to reach its first attempt.
   */
  @Test
  public void replServerRetriesToBindItsListenPort() throws Exception
  {
    TestCaseUtils.startServer();

    ReplicationServer replicationServer = null;
    final ServerSocket portHolder = TestCaseUtils.bindFreePort();
    final int bindFailuresBefore = ReplicationServer.listenPortBindFailures.get();
    try
    {
      final Thread portReleaser = new Thread(() -> {
        try
        {
          final long deadline = System.currentTimeMillis() + 30000;
          while (ReplicationServer.listenPortBindFailures.get() == bindFailuresBefore
              && System.currentTimeMillis() < deadline)
          {
            Thread.sleep(10);
          }
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

      assertTrue(ReplicationServer.listenPortBindFailures.get() > bindFailuresBefore,
          "the replication server should have failed its first attempt to bind the port,"
              + " otherwise this test does not exercise the retry");
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
   * replication server which nevertheless goes through the change keeps its listen port
   * and its whole configuration: the new port is bound before the current one is released,
   * so a failure has nothing to roll back and leaves nothing half applied.
   */
  @Test
  public void replServerKeepsItsConfigurationWhenAPortChangeFails() throws Exception
  {
    TestCaseUtils.startServer();

    ReplicationServer replicationServer = null;
    try
    {
      final int[] ports = TestCaseUtils.findFreePorts(1);
      final String dbDirName = "replServerKeepsItsConfigurationWhenAPortChangeFailsDb";
      replicationServer = new ReplicationServer(new ReplServerFakeConfiguration(
          ports[0], dbDirName, 0, 1, 0, 0, null, 1, 2000, 5000, 1));
      assertTrue(replicationServer.isListening());

      try (ServerSocket portHolder = TestCaseUtils.bindFreePort())
      {
        // The weight changes too, so that a failed change can be seen not to have applied
        // the part of the new configuration which does not depend on the listen port.
        final ReplServerFakeConfiguration newConf = new ReplServerFakeConfiguration(
            portHolder.getLocalPort(), dbDirName, 0, 1, 0, 0, null, 1, 2000, 5000, 2);

        final List<LocalizableMessage> unacceptableReasons = new ArrayList<>();
        assertFalse(replicationServer.isConfigurationChangeAcceptable(newConf, unacceptableReasons),
            "a change to a listen port which is in use should not be acceptable");
        assertFalse(unacceptableReasons.isEmpty(), "the rejected change should say why it was rejected");

        final ConfigChangeResult ccr = replicationServer.applyConfigurationChange(newConf);
        assertEquals(ccr.getResultCode(), ResultCode.OPERATIONS_ERROR);
        assertFalse(ccr.getMessages().isEmpty(), "the failed change should say why it failed");
        assertEquals(replicationServer.getReplicationPort(), ports[0],
            "the replication server should have kept its previous listen port");
        assertTrue(replicationServer.isListening(), "the replication server should still listen");
        assertEquals(replicationServer.getWeight(), 1,
            "a failed port change must not apply the rest of the new configuration");
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

  /**
   * Tests that a replication server whose changelog cannot be read fails fast instead of
   * starting over a changelog it never opened: it used to log ERR_COULD_NOT_READ_DB, whose
   * text already says the replication server failed to start, then bind its listen port and
   * accept connections anyway, so the failure surfaced much later and somewhere else.
   */
  @Test
  public void replServerFailsWhenChangelogCannotBeRead() throws Exception
  {
    TestCaseUtils.startServer();

    final String dbDirName = "replServerFailsWhenChangelogCannotBeReadDb";
    final File dbDirectory = getFileForPath(dbDirName);
    try
    {
      // A domains.state whose second field is not a DN: what a corrupted changelog state file
      // looks like to ReplicationEnvironment, which then cannot be created at all.
      assertTrue(dbDirectory.isDirectory() || dbDirectory.mkdirs(), "could not create " + dbDirectory);
      Files.write(new File(dbDirectory, "domains.state").toPath(),
          Collections.singletonList("1:this is not a DN"), StandardCharsets.UTF_8);

      final int[] ports = TestCaseUtils.findFreePorts(1);
      final int instancesBefore = ReplicationServer.getAllInstances().size();
      try
      {
        final ReplicationServer replicationServer = new ReplicationServer(
            new ReplServerFakeConfiguration(ports[0], dbDirName, 0, 1, 0, 0, null));
        remove(replicationServer);
        fail("Creating a replication server over an unreadable changelog should have failed");
      }
      catch (ConfigException expected)
      {
        assertTrue(expected.getCause() instanceof ChangelogException,
            "the failure should be the one of the changelog, but was: " + expected.getCause());
        assertTrue(expected.getMessage().contains(dbDirectory.getAbsolutePath()),
            "the failure should name the changelog directory, but was: " + expected.getMessage());
        assertEquals(ReplicationServer.getAllInstances().size(), instancesBefore,
            "the failed replication server must not be left registered");
      }

      // The listen port is never bound when the changelog cannot be read, and the aborted
      // initialization leaves nothing holding it.
      try (ServerSocket socket = new ServerSocket())
      {
        socket.bind(new InetSocketAddress(ports[0]));
      }
    }
    finally
    {
      // The aborted instance is never handed to the test, so its changelog cannot be removed
      // through ReplicationTestCase.remove().
      recursiveDelete(dbDirectory);
    }
  }

  /**
   * Tests the failure shape where the changelog state is restored only partially: the
   * domains processed before the failure got their generation id and the ones after it did
   * not, so they would adopt the generation id of the first replica to connect, over
   * on-disk logs which belong to another generation.
   * <p>
   * It is also the shape which restores domains before it fails, so it is the one where the
   * aborted initialization has something to release.
   */
  @Test
  public void replServerFailsWhenAReplicaChangelogCannotBeRead() throws Exception
  {
    TestCaseUtils.startServer();

    final int rsServerId = 8021;
    final String dbDirName = "replServerFailsWhenAReplicaChangelogCannotBeReadDb";
    final File dbDirectory = createPopulatedChangelog(dbDirName, rsServerId);
    try
    {
      // The head log file of the replica is replaced by a directory: the changelog state is
      // then still readable, and the changes of the domain it names are not.
      final File headLogFile = findFile(dbDirectory, "head", ".log");
      assertNotNull(headLogFile, "no replica changelog was written under " + dbDirectory);
      assertTrue(headLogFile.delete() && headLogFile.mkdir(), "could not replace " + headLogFile);

      final int[] ports = TestCaseUtils.findFreePorts(1);
      final int instancesBefore = ReplicationServer.getAllInstances().size();
      try
      {
        final ReplicationServer replicationServer = new ReplicationServer(
            new ReplServerFakeConfiguration(ports[0], dbDirName, 0, rsServerId, 0, 0, null));
        remove(replicationServer);
        fail("Creating a replication server over an unreadable replica changelog should have failed");
      }
      catch (ConfigException expected)
      {
        assertTrue(expected.getCause() instanceof ChangelogException,
            "the failure should be the one of the changelog, but was: " + expected.getCause());
        assertTrue(expected.getMessage().contains(dbDirectory.getAbsolutePath()),
            "the failure should name the changelog directory, but was: " + expected.getMessage());
        assertEquals(ReplicationServer.getAllInstances().size(), instancesBefore,
            "the failed replication server must not be left registered");
        assertNothingLeftBehind(rsServerId);
      }
    }
    finally
    {
      recursiveDelete(dbDirectory);
    }
  }

  /**
   * Tests that a replication server which cannot bind its listen port over a changelog it
   * did read releases the domains that reading restored: each of them holds a timer thread
   * and registers monitor providers, and the aborted instance is never handed to anything
   * which could shut them down later.
   */
  @Test
  public void abortedStartReleasesTheRestoredDomains() throws Exception
  {
    TestCaseUtils.startServer();

    final int rsServerId = 8022;
    final String dbDirName = "abortedStartReleasesTheRestoredDomainsDb";
    final File dbDirectory = createPopulatedChangelog(dbDirName, rsServerId);
    try (ServerSocket portHolder = TestCaseUtils.bindFreePort())
    {
      try
      {
        final ReplicationServer replicationServer = new ReplicationServer(
            new ReplServerFakeConfiguration(portHolder.getLocalPort(), dbDirName, 0, rsServerId, 0, 0, null));
        remove(replicationServer);
        fail("Creating a replication server on a port already in use should have failed");
      }
      catch (ConfigException expected)
      {
        assertNothingLeftBehind(rsServerId);
      }
    }
    finally
    {
      recursiveDelete(dbDirectory);
    }
  }

  /**
   * Tests that a replication server which restarted over an existing changelog releases the
   * domains that reading it restored when it stops: their monitor instance name embeds the
   * URL of their replication server, which used to be assigned only after the changelog had
   * been read, so they were registered under a name holding a null URL and the name looked
   * up to deregister them, built from the assigned URL, could never match it again.
   */
  @Test
  public void restartedReplServerReleasesTheRestoredDomains() throws Exception
  {
    TestCaseUtils.startServer();

    final int rsServerId = 8023;
    final String dbDirName = "restartedReplServerReleasesTheRestoredDomainsDb";
    final File dbDirectory = createPopulatedChangelog(dbDirName, rsServerId);
    ReplicationServer replicationServer = null;
    try
    {
      final int[] ports = TestCaseUtils.findFreePorts(1);
      replicationServer = new ReplicationServer(
          new ReplServerFakeConfiguration(ports[0], dbDirName, 0, rsServerId, 0, 0, null));
      assertTrue(replicationServer.isListening());
      assertFalse(domainRegistrationsOf(rsServerId).isEmpty(),
          "the restored domain should hold a timer thread and monitor providers, otherwise"
              + " this test does not test that they are released");
    }
    finally
    {
      remove(replicationServer);
      recursiveDelete(dbDirectory);
    }
    assertNothingLeftBehind(rsServerId);
  }

  /**
   * Tests that a port change whose wait for the previous listen thread is interrupted still
   * leaves this replication server listening: the new port is served before the previous one
   * is released, so an interrupt can only cut the wait for a thread which is already stopping
   * short, never leave the replication server with no listener at all.
   */
  @Test
  public void replServerKeepsListeningWhenAPortChangeIsInterrupted() throws Exception
  {
    TestCaseUtils.startServer();

    ReplicationServer replicationServer = null;
    try
    {
      final int[] ports = TestCaseUtils.findFreePorts(2);
      final String dbDirName = "replServerKeepsListeningWhenAPortChangeIsInterruptedDb";
      replicationServer = new ReplicationServer(
          new ReplServerFakeConfiguration(ports[0], dbDirName, 0, 1, 0, 0, null));
      assertTrue(replicationServer.isListening());

      // Thread.join() throws InterruptedException at once when the interrupt status is
      // already set, i.e. this interrupts the port change in its wait for the listen thread.
      Thread.currentThread().interrupt();
      final ConfigChangeResult ccr = replicationServer.applyConfigurationChange(
          new ReplServerFakeConfiguration(ports[1], dbDirName, 0, 1, 0, 0, null));
      // Cleared for the rest of this test, and for whatever runs next in this thread.
      final boolean interrupted = Thread.interrupted();

      assertEquals(ccr.getResultCode(), ResultCode.SUCCESS);
      assertTrue(interrupted, "the interrupted port change should have restored the interrupt status");
      assertEquals(replicationServer.getReplicationPort(), ports[1],
          "the replication server should have switched to the new listen port");
      assertTrue(replicationServer.isListening(),
          "an interrupted port change must not leave the replication server without a listener");

      // and it must be usable on the new port.
      ReplicationBroker broker = openReplicationSession(
          DN.valueOf(TEST_ROOT_DN_STRING), 1, 10, ports[1], 1000);
      assertTrue(broker.getCurrentSendWindow() != 0);
    }
    finally
    {
      remove(replicationServer);
    }
  }

  /**
   * Creates the changelog of a replication server which ran and served one replica, and
   * returns its directory: a changelog whose reading restores a domain, i.e. one over which
   * an initialization has something to release when it fails.
   */
  private File createPopulatedChangelog(String dbDirName, int rsServerId) throws Exception
  {
    final File dbDirectory = getFileForPath(dbDirName);
    recursiveDelete(dbDirectory);

    final int[] ports = TestCaseUtils.findFreePorts(1);
    final ReplicationServer replicationServer = new ReplicationServer(
        new ReplServerFakeConfiguration(ports[0], dbDirName, 0, rsServerId, 0, 0, null));
    ReplicationBroker broker = null;
    try
    {
      final DN baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
      broker = openReplicationSession(baseDN, 42, 100, ports[0], 1000);
      broker.publish(new DeleteMsg(baseDN, new CSNGenerator(42, 0).newCSN(), "uid"));

      // The changelog is written by the replication server, so the publication above is only
      // over once the log of the replica exists.
      waitFor(dbDirectory, "head", ".log");
    }
    finally
    {
      stop(broker);
      replicationServer.shutdown();
    }

    assertNotNull(findFile(dbDirectory, "generation", ".id"),
        "the changelog should hold the generation id of the domain, otherwise reading it back"
            + " restores no domain at all and this test tests nothing");
    // A replication server which stopped normally must not leave anything behind either.
    assertNothingLeftBehind(rsServerId);
    return dbDirectory;
  }

  /**
   * Asserts that the replication server with the provided server id left neither a thread of
   * a domain nor a monitor provider of a domain or of its changelog behind.
   */
  private void assertNothingLeftBehind(int rsServerId) throws Exception
  {
    // Stopping a thread only asks it to stop, so give the ones being stopped the time to
    // actually stop before reporting them as left behind.
    final long deadline = System.currentTimeMillis() + 10000;
    List<String> leftBehind;
    while (!(leftBehind = domainRegistrationsOf(rsServerId)).isEmpty() && System.currentTimeMillis() < deadline)
    {
      Thread.sleep(10);
    }
    assertEquals(leftBehind, Collections.emptyList(),
        "the replication server RS(" + rsServerId + ") left the above behind");
  }

  /** The threads a {@link ReplicationServerDomain} starts, and which its shutdown stops. */
  private static final Collection<String> DOMAIN_THREADS =
      Arrays.asList("assured timer for domain", "status monitor for domain");

  /**
   * Returns the threads and the monitor providers which the domains of the replication server
   * with the provided server id have started and registered.
   */
  private List<String> domainRegistrationsOf(int rsServerId)
  {
    final String replicationServer = "replication server rs(" + rsServerId + ")";
    final List<String> registrations = new ArrayList<>();
    for (Thread thread : Thread.getAllStackTraces().keySet())
    {
      final String name = thread.getName().toLowerCase();
      if (name.startsWith(replicationServer) && containsAnyOf(name, DOMAIN_THREADS))
      {
        registrations.add(thread.getName());
      }
    }
    // The monitor instance names are registered in lowercase.
    for (String monitorName : DirectoryServer.getMonitorProviders().keySet())
    {
      if (monitorName.contains(replicationServer))
      {
        registrations.add(monitorName);
      }
    }
    Collections.sort(registrations);
    return registrations;
  }

  private boolean containsAnyOf(String name, Collection<String> candidates)
  {
    for (String candidate : candidates)
    {
      if (name.contains(candidate))
      {
        return true;
      }
    }
    return false;
  }

  /** Waits for a file whose name matches to appear anywhere under the provided directory. */
  private void waitFor(File directory, String prefix, String suffix) throws Exception
  {
    final long deadline = System.currentTimeMillis() + 10000;
    while (findFile(directory, prefix, suffix) == null && System.currentTimeMillis() < deadline)
    {
      Thread.sleep(10);
    }
    assertNotNull(findFile(directory, prefix, suffix),
        "no " + prefix + "*" + suffix + " was written under " + directory);
  }

  /** Returns the first file whose name matches, at any depth of the provided directory. */
  private File findFile(File directory, String prefix, String suffix)
  {
    final File[] files = directory.listFiles();
    if (files == null)
    {
      return null;
    }
    for (File file : files)
    {
      final String name = file.getName();
      if (name.startsWith(prefix) && name.endsWith(suffix))
      {
        return file;
      }
      final File found = file.isDirectory() ? findFile(file, prefix, suffix) : null;
      if (found != null)
      {
        return found;
      }
    }
    return null;
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
