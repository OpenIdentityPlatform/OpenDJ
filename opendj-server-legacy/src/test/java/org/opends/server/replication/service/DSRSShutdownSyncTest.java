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

import static org.assertj.core.api.Assertions.assertThat;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/** Test the {@link DSRSShutdownSync} class. */
@SuppressWarnings("javadoc")
public class DSRSShutdownSyncTest extends DirectoryServerTestCase
{
  /** Short grace period, so that the time dependent contracts are pinned in milliseconds. */
  private static final long GRACE_PERIOD = 200;
  private static final int SERVER_ID = 1;
  private static final int OTHER_SERVER_ID = 2;

  private static DN baseDN1;
  private static DN baseDN2;

  @BeforeClass
  public static void classSetup() throws Exception
  {
    TestCaseUtils.startServer();
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
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync(GRACE_PERIOD);

    shutdownSync.replicaOfflineMsgSent(baseDN1, SERVER_ID);

    assertThat(shutdownSync.canShutdown(baseDN1)).isFalse();
  }

  @Test
  public void canShutdownOnceTheReplicaOfflineMsgIsForwarded() throws Exception
  {
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync(GRACE_PERIOD);

    shutdownSync.replicaOfflineMsgSent(baseDN1, SERVER_ID);
    shutdownSync.replicaOfflineMsgForwarded(baseDN1, SERVER_ID);

    assertThat(shutdownSync.canShutdown(baseDN1)).isTrue();
  }

  @Test
  public void canShutdownOnceTheGracePeriodExpired() throws Exception
  {
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync(GRACE_PERIOD);

    shutdownSync.replicaOfflineMsgSent(baseDN1, SERVER_ID);
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
    shutdownSync.replicaOfflineMsgSent(baseDN1, SERVER_ID);
    shutdownSync.replicaOfflineMsgForwarded(baseDN1, SERVER_ID);
    Thread.sleep(GRACE_PERIOD + 50);

    // the shutdown of the process, much later
    shutdownSync.replicaOfflineMsgSent(baseDN1, SERVER_ID);

    assertThat(shutdownSync.canShutdown(baseDN1)).isFalse();
  }

  @Test
  public void gracePeriodIsCountedPerDomain() throws Exception
  {
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync(GRACE_PERIOD);

    shutdownSync.replicaOfflineMsgSent(baseDN1, SERVER_ID);
    Thread.sleep(GRACE_PERIOD + 50);
    shutdownSync.replicaOfflineMsgSent(baseDN2, SERVER_ID);

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
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync(GRACE_PERIOD);

    shutdownSync.replicaOfflineMsgSent(baseDN1, SERVER_ID);
    shutdownSync.replicaOfflineMsgForwarded(baseDN1, OTHER_SERVER_ID);

    assertThat(shutdownSync.canShutdown(baseDN1)).isFalse();
  }

  @Test
  public void aReplicaStillWaitingDoesNotHoldBackAnAlreadyForwardedOne() throws Exception
  {
    final DSRSShutdownSync shutdownSync = new DSRSShutdownSync(GRACE_PERIOD);

    shutdownSync.replicaOfflineMsgSent(baseDN1, SERVER_ID);
    shutdownSync.replicaOfflineMsgSent(baseDN1, OTHER_SERVER_ID);
    shutdownSync.replicaOfflineMsgForwarded(baseDN1, SERVER_ID);
    shutdownSync.replicaOfflineMsgForwarded(baseDN1, OTHER_SERVER_ID);

    assertThat(shutdownSync.canShutdown(baseDN1)).isTrue();
  }
}
