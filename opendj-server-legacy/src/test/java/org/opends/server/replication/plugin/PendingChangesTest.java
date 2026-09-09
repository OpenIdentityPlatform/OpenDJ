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
package org.opends.server.replication.plugin;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import org.mockito.ArgumentCaptor;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ReplicaOfflineMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.service.ReplicationDomain;
import org.opends.server.types.operation.PluginOperation;
import org.testng.annotations.Test;

/**
 * Tests the bookkeeping a replica does on its own changes: they are published in the order of
 * their CSNs, and the announcement that the replica goes offline is only reported as sent when
 * it really was published.
 * <p>
 * These tests need no server: the changes are built by a CSNGenerator, which reads the time
 * service, and the time service is up as soon as its class is loaded.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "replication" }, sequential = true)
public class PendingChangesTest extends DirectoryServerTestCase
{
  private static final int SERVER_ID = 42;

  @Test
  public void replicaOfflineMsgTheBrokerPublishedIsReportedAsSent() throws Exception
  {
    final ReplicationDomain domain = domainWhichPublishes(true);
    final PendingChanges pendingChanges = newPendingChanges(domain);

    final CSN offlineCSN = pendingChanges.putReplicaOfflineMsg();

    assertNotNull(offlineCSN, "the message was published and must be reported as sent");
    final UpdateMsg published = onlyMsgPublishedBy(domain);
    assertTrue(published instanceof ReplicaOfflineMsg, "published " + published);
    assertEquals(published.getCSN(), offlineCSN);
  }

  /**
   * The broker writes nothing when it has no usable session, when the changes which come before
   * this one still have to be republished by the recovery, or when it is stopped in between - and
   * what was not written must not be reported as sent: the shutdown of a collocated replication
   * server waits out the whole grace period of a message it was told about and which never
   * reached the wire.
   */
  @Test
  public void replicaOfflineMsgTheBrokerRefusedIsNotReportedAsSent() throws Exception
  {
    final ReplicationDomain domain = domainWhichPublishes(false);
    final PendingChanges pendingChanges = newPendingChanges(domain);

    assertNull(pendingChanges.putReplicaOfflineMsg(), "the broker refused the message");

    assertTrue(onlyMsgPublishedBy(domain) instanceof ReplicaOfflineMsg, "it was attempted");
  }

  /**
   * The message carries the newest CSN of the replica, so a change which is still in flight
   * holds it back, and the broker is never even asked to publish it.
   */
  @Test
  public void replicaOfflineMsgQueuedBehindAnUncommittedChangeIsNotReportedAsSent() throws Exception
  {
    final ReplicationDomain domain = domainWhichPublishes(true);
    final PendingChanges pendingChanges = newPendingChanges(domain);
    pendingChanges.putLocalOperation(newLocalOperation());

    assertNull(pendingChanges.putReplicaOfflineMsg(), "nothing was published");

    verify(domain, never()).publish(any(UpdateMsg.class));
  }

  /**
   * A message which could not be published is given up on rather than left queued: the replica
   * which could not announce itself offline is either shutting down, and the message dies with
   * the process, or it is being disabled for an import or a configuration change - and once it
   * comes back, announcing it offline on the session which follows would be a lie.
   */
  @Test
  public void replicaOfflineMsgWhichCouldNotBeSentIsNotPublishedLater() throws Exception
  {
    final ReplicationDomain domain = domainWhichPublishes(true);
    final PendingChanges pendingChanges = newPendingChanges(domain);
    final CSN changeCSN = pendingChanges.putLocalOperation(newLocalOperation());
    assertNull(pendingChanges.putReplicaOfflineMsg(), "nothing was published");

    // The change which held the message back completes.
    pendingChanges.commitAndPushCommittedChanges(changeCSN, mock(LDAPUpdateMsg.class));

    final UpdateMsg published = onlyMsgPublishedBy(domain);
    assertTrue(published instanceof LDAPUpdateMsg, "published " + published);
  }

  /**
   * A change the broker refused leaves the pending changes all the same: the replica has done
   * it, its ServerState says so, and it is by finding that state ahead of the one its
   * replication server reports that the next session republishes the change from the historical
   * information of its entry. Only the offline announcement, which is stored nowhere, needs the
   * answer of the broker.
   */
  @Test
  public void changeTheBrokerRefusedStillLeavesThePendingChanges() throws Exception
  {
    final ReplicationDomain domain = domainWhichPublishes(false);
    final PendingChanges pendingChanges = newPendingChanges(domain);
    final CSN changeCSN = pendingChanges.putLocalOperation(newLocalOperation());
    assertEquals(pendingChanges.size(), 1);

    pendingChanges.commitAndPushCommittedChanges(changeCSN, mock(LDAPUpdateMsg.class));

    assertTrue(onlyMsgPublishedBy(domain) instanceof LDAPUpdateMsg, "the change was published");
    assertEquals(pendingChanges.size(), 0, "and is not queued for a second attempt");
  }

  private PendingChanges newPendingChanges(ReplicationDomain domain)
  {
    return new PendingChanges(new CSNGenerator(SERVER_ID, 0), domain);
  }

  /** A domain whose broker accepts, or refuses, whatever it is given to publish. */
  private ReplicationDomain domainWhichPublishes(boolean accepted)
  {
    final ReplicationDomain domain = mock(ReplicationDomain.class);
    when(domain.publish(any(UpdateMsg.class))).thenReturn(accepted);
    return domain;
  }

  /** A local operation, i.e. one this replica must publish to the other replicas. */
  private PluginOperation newLocalOperation()
  {
    final PluginOperation operation = mock(PluginOperation.class);
    when(operation.isSynchronizationOperation()).thenReturn(false);
    return operation;
  }

  /**
   * The single message the domain was asked to publish, failing the test if it published
   * anything else: a message which is not sent and a message which is sent twice are both the
   * kind of mistake these tests are about.
   */
  private UpdateMsg onlyMsgPublishedBy(ReplicationDomain domain)
  {
    final ArgumentCaptor<UpdateMsg> published = ArgumentCaptor.forClass(UpdateMsg.class);
    verify(domain).publish(published.capture());
    return published.getValue();
  }
}
