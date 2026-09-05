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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.replication.service;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.replication.common.CSN;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** Test the {@link DSRSShutdownSync} class. */
@SuppressWarnings("javadoc")
public class DSRSShutdownSyncTest extends DirectoryServerTestCase
{
  /** Short grace period, for the contracts a test has to wait out. */
  private static final long GRACE_PERIOD = 500;
  /**
   * Grace period for the contracts which only read the state: long enough that no scheduling
   * pause between announcing a message and reading the state can expire it.
   */
  private static final long LONG_GRACE_PERIOD = 60000;
  /** Time given to the forwarding thread before it forwards the message of one domain. */
  private static final long FORWARD_DELAY = 200;
  private static final int SERVER_ID = 1;
  private static final int OTHER_SERVER_ID = 2;

  private static DN baseDN1;
  private static DN baseDN2;

  @BeforeClass
  public static void classSetup() throws Exception
  {
    baseDN1 = DN.valueOf("dc=example,dc=com");
    baseDN2 = DN.valueOf("dc=world,dc=company");
  }

  @Test
  public void canShutdownWhenNoReplicaOfflineMsgWasSent() throws Exception
  {
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync(GRACE_PERIOD);

    assertThat(shutdownSync.canShutdown(baseDN1)).isTrue();
  }

  @Test
  public void cannotShutdownUntilTheReplicaOfflineMsgIsForwarded() throws Exception
  {
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync(LONG_GRACE_PERIOD);

    shutdownSync.replicaOfflineMsgSent(baseDN1, newCSN(SERVER_ID));

    assertThat(shutdownSync.canShutdown(baseDN1)).isFalse();
  }

  @Test
  public void canShutdownOnceTheReplicaOfflineMsgIsForwarded() throws Exception
  {
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync(LONG_GRACE_PERIOD);
    final CSN offlineCSN = newCSN(SERVER_ID);

    shutdownSync.replicaOfflineMsgSent(baseDN1, offlineCSN);
    shutdownSync.replicaOfflineMsgForwarded(baseDN1, offlineCSN);

    assertThat(shutdownSync.canShutdown(baseDN1)).isTrue();
  }

  @Test
  public void canShutdownOnceTheGracePeriodExpired() throws Exception
  {
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync(GRACE_PERIOD);

    shutdownSync.replicaOfflineMsgSent(baseDN1, newCSN(SERVER_ID));
    Thread.sleep(GRACE_PERIOD + 50);

    assertThat(shutdownSync.canShutdown(baseDN1)).isTrue();
  }

  /**
   * A message sent earlier in the life of the process - an online import, a restore, a
   * configuration change - must not consume the grace period of the message sent by the
   * shutdown this class exists for.
   */
  @Test
  public void gracePeriodOfAShutdownIsNotSpentByAnEarlierMessage() throws Exception
  {
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync(GRACE_PERIOD);

    // an import disables then re-enables the replication service
    final CSN sentByTheImport = newCSN(SERVER_ID, 1);
    shutdownSync.replicaOfflineMsgSent(baseDN1, sentByTheImport);
    shutdownSync.replicaOfflineMsgForwarded(baseDN1, sentByTheImport);
    Thread.sleep(GRACE_PERIOD + 50);

    // the shutdown of the process, much later
    shutdownSync.replicaOfflineMsgSent(baseDN1, newCSN(SERVER_ID, 2));

    assertThat(shutdownSync.canShutdown(baseDN1)).isFalse();
  }

  /**
   * The message of an earlier announcement may still be queued behind a backlog when the
   * shutdown announces the replica offline again. Forwarding that older message says nothing
   * about the one the shutdown is waiting for, so it must not end the wait.
   */
  @Test
  public void aStaleForwardDoesNotConsumeTheGracePeriodOfANewerMessage() throws Exception
  {
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync(LONG_GRACE_PERIOD);
    final CSN queuedByAnEarlierImport = newCSN(SERVER_ID, 1);
    final CSN sentByTheShutdown = newCSN(SERVER_ID, 2);

    shutdownSync.replicaOfflineMsgSent(baseDN1, queuedByAnEarlierImport);
    shutdownSync.replicaOfflineMsgSent(baseDN1, sentByTheShutdown);
    shutdownSync.replicaOfflineMsgForwarded(baseDN1, queuedByAnEarlierImport);

    assertThat(shutdownSync.canShutdown(baseDN1)).isFalse();

    shutdownSync.replicaOfflineMsgForwarded(baseDN1, sentByTheShutdown);

    assertThat(shutdownSync.canShutdown(baseDN1)).isTrue();
  }

  @Test
  public void gracePeriodIsCountedPerDomain() throws Exception
  {
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync(GRACE_PERIOD);

    shutdownSync.replicaOfflineMsgSent(baseDN1, newCSN(SERVER_ID));
    Thread.sleep(GRACE_PERIOD + 50);
    shutdownSync.replicaOfflineMsgSent(baseDN2, newCSN(SERVER_ID));

    assertThat(shutdownSync.canShutdown(baseDN1)).isTrue();
    assertThat(shutdownSync.canShutdown(baseDN2)).isFalse();
  }

  /**
   * A replication server relays the ReplicaOfflineMsg of every replica connected to it, so the
   * forward of another replica's message must not release the shutdown of this one.
   */
  @Test
  public void theForwardOfAnotherReplicasMessageDoesNotEndTheWait() throws Exception
  {
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync(LONG_GRACE_PERIOD);

    shutdownSync.replicaOfflineMsgSent(baseDN1, newCSN(SERVER_ID));
    shutdownSync.replicaOfflineMsgForwarded(baseDN1, newCSN(OTHER_SERVER_ID));

    assertThat(shutdownSync.canShutdown(baseDN1)).isFalse();
  }

  /** The domain waits for the message of every one of its replicas, not for the first of them. */
  @Test
  public void aReplicaWhichIsStillWaitingHoldsBackTheShutdownOfItsDomain() throws Exception
  {
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync(LONG_GRACE_PERIOD);
    final CSN ofOneReplica = newCSN(SERVER_ID);

    shutdownSync.replicaOfflineMsgSent(baseDN1, ofOneReplica);
    shutdownSync.replicaOfflineMsgSent(baseDN1, newCSN(OTHER_SERVER_ID));
    shutdownSync.replicaOfflineMsgForwarded(baseDN1, ofOneReplica);

    assertThat(shutdownSync.canShutdown(baseDN1)).isFalse();
  }

  @Test
  public void canShutdownOnceEveryReplicaOfTheDomainIsForwarded() throws Exception
  {
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync(LONG_GRACE_PERIOD);
    final CSN ofOneReplica = newCSN(SERVER_ID);
    final CSN ofTheOtherReplica = newCSN(OTHER_SERVER_ID);

    shutdownSync.replicaOfflineMsgSent(baseDN1, ofOneReplica);
    shutdownSync.replicaOfflineMsgSent(baseDN1, ofTheOtherReplica);
    shutdownSync.replicaOfflineMsgForwarded(baseDN1, ofOneReplica);
    shutdownSync.replicaOfflineMsgForwarded(baseDN1, ofTheOtherReplica);

    assertThat(shutdownSync.canShutdown(baseDN1)).isTrue();
  }

  /**
   * The domains of a shutdown wait together: the wait ends when the message of every one of them
   * has been forwarded, not when the first one has. Waiting for them one after the other would
   * leave the domains which come later without a grace period at all, since the wait of the
   * first one spends the deadline they share.
   */
  @Test
  public void oneWaitCoversEveryDomainOfTheShutdown() throws Exception
  {
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync(LONG_GRACE_PERIOD);
    final CSN ofTheFirstDomain = newCSN(SERVER_ID, 1);
    final CSN ofTheSecondDomain = newCSN(SERVER_ID, 2);
    shutdownSync.replicaOfflineMsgSent(baseDN1, ofTheFirstDomain);
    shutdownSync.replicaOfflineMsgSent(baseDN2, ofTheSecondDomain);
    final Thread forwarder =
        newForwarderThread(shutdownSync, ofTheFirstDomain, ofTheSecondDomain);

    final long startTime = System.nanoTime();
    forwarder.start();
    shutdownSync.awaitReplicaOfflineMsgsForwarded(
        asList(baseDN1, baseDN2), shutdownSync.newShutdownDeadline());
    final long elapsed = millisSince(startTime);
    forwarder.join();

    assertThat(elapsed)
        .as("the wait ended on the first domain forwarded, leaving the second one nothing")
        .isGreaterThanOrEqualTo(2 * FORWARD_DELAY);
    assertThat(elapsed).isLessThan(LONG_GRACE_PERIOD);
  }

  /**
   * However long the messages of a shutdown may still hold it back, the deadline the shutdown
   * was given bounds the wait.
   */
  @Test
  public void theWaitIsBoundedByTheDeadlineOfTheShutdown() throws Exception
  {
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync(LONG_GRACE_PERIOD);
    shutdownSync.replicaOfflineMsgSent(baseDN1, newCSN(SERVER_ID));
    shutdownSync.replicaOfflineMsgSent(baseDN2, newCSN(SERVER_ID));
    final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(GRACE_PERIOD);

    final long startTime = System.nanoTime();
    shutdownSync.awaitReplicaOfflineMsgsForwarded(asList(baseDN1, baseDN2), deadline);
    final long elapsed = millisSince(startTime);

    assertThat(elapsed).isGreaterThanOrEqualTo(GRACE_PERIOD - 50);
    assertThat(elapsed)
        .as("the wait outlived the deadline of the shutdown")
        .isLessThan(2 * GRACE_PERIOD);
  }

  /** Forwards the message of the first domain, then, as long again later, of the second one. */
  private Thread newForwarderThread(final DSRSShutdownSync shutdownSync,
      final CSN ofTheFirstDomain, final CSN ofTheSecondDomain)
  {
    return new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        try
        {
          Thread.sleep(FORWARD_DELAY);
          shutdownSync.replicaOfflineMsgForwarded(baseDN1, ofTheFirstDomain);
          Thread.sleep(FORWARD_DELAY);
          shutdownSync.replicaOfflineMsgForwarded(baseDN2, ofTheSecondDomain);
        }
        catch (InterruptedException e)
        {
          Thread.currentThread().interrupt();
        }
      }
    });
  }

  private static long millisSince(long startTime)
  {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
  }

  /**
   * The CSN of a message a replica announced. They are built by hand rather than with a
   * CSNGenerator: this class has no state to share with the server, and a generator would tie
   * the test to the time service the server starts.
   */
  private static CSN newCSN(int serverId)
  {
    return newCSN(serverId, 1);
  }

  private static CSN newCSN(int serverId, int seqNum)
  {
    return new CSN(1, seqNum, serverId);
  }
}
