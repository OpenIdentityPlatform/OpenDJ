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

import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.testng.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.meta.ReplicationDomainCfgDefn.IsolationPolicy;
import org.opends.server.core.AddOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.protocol.AckMsg;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.service.ReplicationBroker;
import org.opends.server.replication.service.ReplicationDomain;
import org.opends.server.types.Entry;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Tests duplicate delivery across reconnects against the real replay and backend (issue #1269). */
@SuppressWarnings("javadoc")
public class DuplicateReplayTest extends ReplicationTestCase
{
  private static final String ENTRY_UUID = "c9cb8c3c-615a-4122-865d-50323aaaed48";

  private DN baseDN;
  private LDAPReplicationDomain domain;
  private TestSynchronousReplayQueue queue;
  private Entry entry;
  private String parentUUID;
  private CSN addCSN;
  private CSN deleteCSN;
  private OutOfMemoryError enqueueFailure;
  private OutOfMemoryError pollFailure;
  private boolean failBeforePoll;
  private Runnable beforeEnqueue;

  @BeforeMethod
  public void setUpLocal() throws Exception
  {
    baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    initializeTestBackend(true);
    enqueueFailure = null;
    pollFailure = null;
    failBeforePoll = false;
    beforeEnqueue = null;
    queue = new TestSynchronousReplayQueue()
    {
      @Override
      public boolean offer(UpdateToReplay update, long timeout, TimeUnit unit) throws InterruptedException
      {
        if (beforeEnqueue != null)
        {
          beforeEnqueue.run();
        }
        if (enqueueFailure != null)
        {
          throw enqueueFailure;
        }
        return super.offer(update, timeout, unit);
      }

      @Override
      public UpdateToReplay poll(long timeout, TimeUnit unit)
      {
        return poll();
      }

      @Override
      public UpdateToReplay poll()
      {
        if (pollFailure != null && failBeforePoll)
        {
          throw pollFailure;
        }
        final UpdateToReplay result = super.poll();
        if (pollFailure != null)
        {
          throw pollFailure;
        }
        return result;
      }
    };
    final DomainFakeCfg conf = new DomainFakeCfg(baseDN, 1, new TreeSet<String>());
    conf.setIsolationPolicy(IsolationPolicy.ACCEPT_ALL_UPDATES);
    domain = MultimasterReplication.createNewDomain(conf, queue);
    domain.start();

    entry = makeEntry("dn: cn=duplicate," + baseDN,
        "objectClass: top", "objectClass: person", "cn: duplicate", "sn: duplicate",
        "entryUUID: " + ENTRY_UUID);
    parentUUID = getEntryUUID(baseDN);
    final CSNGenerator generator = new CSNGenerator(201, 0);
    addCSN = generator.newCSN();
    deleteCSN = generator.newCSN();
  }

  @AfterMethod
  public void tearDown() throws Exception
  {
    MultimasterReplication.deleteDomain(baseDN);
  }

  @Test
  public void appliedAddDeliveredAgainAfterDeleteDoesNotResurrectEntry() throws Exception
  {
    domain.processUpdate(addMsg());
    replayQueuedMessages();
    assertTrue(entryExists(entry.getName()));
    assertEquals(getEntryUUID(entry.getName()), ENTRY_UUID);
    domain.processUpdate(deleteMsg());
    replayQueuedMessages();
    assertFalse(entryExists(entry.getName()));
    assertTrue(domain.getServerState().cover(deleteCSN));

    // A new session can deliver an old change after it has left the pending list.
    assertFalse(domain.processUpdate(addMsg()));
    replayQueuedMessages();

    assertFalse(entryExists(entry.getName()), "an applied ADD was replayed after its DELETE");
    assertEquals(processedUpdates(), 3, "each delivery must be counted, including the duplicate");
  }

  @Test
  public void queuedDuplicateAddDoesNotMoveTheOriginalBehindDelete() throws Exception
  {
    domain.processUpdate(addMsg());
    domain.processUpdate(deleteMsg());
    final int completions = completedUpdates();
    // Pause replay until the duplicate has arrived: no timing or reconnect race is needed.
    assertFalse(domain.processUpdate(addMsg()));
    assertEquals(completedUpdates(), completions, "a queued duplicate must not return credit before replay");
    assertFalse(domain.getServerState().cover(addCSN), "discarding a duplicate must not advance state");
    assertFalse(domain.getServerState().cover(deleteCSN));

    replayQueuedMessages();

    assertEquals(completedUpdates(), completions + 2, "one completion per replayed change");
    assertFalse(entryExists(entry.getName()), "the queued ADD moved behind its DELETE");
    assertTrue(domain.getServerState().cover(deleteCSN));
    assertEquals(processedUpdates(), 3, "each delivery must be counted, including the duplicate");
  }

  @Test
  public void deliveryAbandonedBeforeEnqueueCanBeRetried() throws Exception
  {
    domain.disableService();
    domain.processUpdate(addMsg());
    assertTrue(queue.isEmpty());
    assertFalse(domain.getServerState().cover(addCSN));

    domain.enableService();
    domain.processUpdate(addMsg());
    replayQueuedMessages();

    assertTrue(entryExists(entry.getName()), "a delivery abandoned before enqueue was refused on retry");
    assertTrue(domain.getServerState().cover(addCSN));
  }

  @Test
  public void failedEnqueueCanBeRetried() throws Exception
  {
    enqueueFailure = new OutOfMemoryError("enqueue allocation failed");
    try
    {
      domain.processUpdate(addMsg());
      fail("the injected allocation failure did not reach enqueue");
    }
    catch (OutOfMemoryError e)
    {
      assertSame(e, enqueueFailure);
    }
    finally
    {
      enqueueFailure = null;
    }
    assertTrue(queue.isEmpty());
    assertFalse(domain.getServerState().cover(addCSN));

    domain.processUpdate(addMsg());
    replayQueuedMessages();

    assertTrue(entryExists(entry.getName()), "a failed enqueue was refused on retry");
    assertTrue(domain.getServerState().cover(addCSN));
  }

  @Test
  public void ownChangeSentBeforeANewerLocalPublishIsStillReplayed() throws Exception
  {
    // Model the incoming backlog after restoring a backup that predates these changes.
    final CSNGenerator generator = new CSNGenerator(1, 0);
    addCSN = generator.newCSN();
    deleteCSN = generator.newCSN();
    domain.publish(deleteMsg());
    assertTrue(domain.getServerState().cover(addCSN));

    domain.processUpdate(addMsg());
    replayQueuedMessages();
    assertTrue(entryExists(entry.getName()), "local publication must not hide a missing own-origin change");
    domain.processUpdate(deleteMsg());
    replayQueuedMessages();
    assertFalse(entryExists(entry.getName()));
    domain.processUpdate(addMsg());
    replayQueuedMessages();
    assertFalse(entryExists(entry.getName()), "own-origin replay also needs duplicate suppression");
  }

  @Test
  public void backendReloadResetsTheOwnReplayWatermark() throws Exception
  {
    final CSNGenerator generator = new CSNGenerator(1, 0);
    addCSN = generator.newCSN();
    deleteCSN = generator.newCSN();
    domain.processUpdate(addMsg());
    domain.processUpdate(deleteMsg());
    replayQueuedMessages();
    assertFalse(entryExists(entry.getName()));
    domain.disable();
    // Restore the persisted state of a backup taken before the ADD. Its entry is absent,
    // as it is in this backend after DELETE; no history on that entry can cover the ADD.
    ((PersistentServerState) domainField("state")).clear();
    domain.enable();
    assertFalse(domain.getServerState().cover(addCSN));
    domain.processUpdate(addMsg());
    replayQueuedMessages();
    assertTrue(entryExists(entry.getName()), "backend reload retained the pre-restore replay watermark");
  }

  private int completedUpdates() throws Exception
  {
    final Field brokerField = ReplicationDomain.class.getDeclaredField("broker");
    brokerField.setAccessible(true);
    final Object broker = brokerField.get(domain);
    final Field counter = ReplicationBroker.class.getDeclaredField("updateDoneCount");
    counter.setAccessible(true);
    return counter.getInt(broker);
  }

  @DataProvider
  public Object[][] queueFailurePositions()
  {
    return new Object[][] { { false }, { true } };
  }

  @Test(dataProvider = "queueFailurePositions")
  public void failureAroundQueueRemovalDoesNotLoseTheChange(boolean beforeRemoval) throws Exception
  {
    final AtomicBoolean recovering = (AtomicBoolean) domainField("replayFailureRecovery");
    final SessionRestartRequests restarts = (SessionRestartRequests) domainField("sessionRestarts");
    recovering.set(true); // Keep the checkpointer from consuming the request under test.
    try
    {
      domain.processUpdate(addMsg());
      domain.processUpdate(deleteMsg());
      failBeforePoll = beforeRemoval;
      pollFailure = new OutOfMemoryError("dequeue failed");
      try
      {
        new ReplayThread(queue, new ReentrantLock()).run();
        fail("the injected dequeue failure did not escape");
      }
      catch (OutOfMemoryError e)
      {
        assertSame(e, pollFailure);
      }
      finally
      {
        pollFailure = null;
      }
      assertEquals(queue.size(), beforeRemoval ? 2 : 1);
      assertTrue(restarts.isPending(), "a lost queue delivery must be requested again");
      replayQueuedMessages(); // DELETE must wait for the cancelled ADD, not commit NO_SUCH_OBJECT.
      domain.processUpdate(addMsg());
      replayQueuedMessages();
      assertFalse(entryExists(entry.getName()), "DELETE overtook the retry of the removed ADD");
      assertTrue(domain.getServerState().cover(deleteCSN));
    }
    finally
    {
      restarts.clear();
      recovering.set(false);
    }
  }

  @Test
  public void cancellationAfterANewerDeliveryWasRefusedRequestsAnotherSession() throws Exception
  {
    final AtomicBoolean recovering = (AtomicBoolean) domainField("replayFailureRecovery");
    final SessionRestartRequests restarts = (SessionRestartRequests) domainField("sessionRestarts");
    recovering.set(true);
    try
    {
      enqueueFailure = new OutOfMemoryError("old listener cannot finish enqueue");
      beforeEnqueue = () ->
      {
        // A newer session redelivers while the old listener is still in offer().
        assertFalse(domain.processUpdate(addMsg()));
        assertTrue(queue.isEmpty());
        beforeEnqueue = null;
        final OutOfMemoryError oldFailure = enqueueFailure;
        enqueueFailure = null;
        try
        {
          domain.processUpdate(deleteMsg());
        }
        finally
        {
          enqueueFailure = oldFailure;
        }
      };
      try
      {
        domain.processUpdate(addMsg());
        fail("enqueue must fail");
      }
      catch (OutOfMemoryError e)
      {
        assertSame(e, enqueueFailure);
      }
      finally
      {
        beforeEnqueue = null;
        enqueueFailure = null;
      }
      assertTrue(restarts.isPending(), "the newer session already missed its chance to retry");
      replayQueuedMessages();
      domain.processUpdate(addMsg());
      replayQueuedMessages();
      assertFalse(entryExists(entry.getName()), "DELETE overtook the cancelled ADD's retry");
      assertTrue(domain.getServerState().cover(deleteCSN));
    }
    finally
    {
      restarts.clear();
      recovering.set(false);
    }
  }

  private Object domainField(String name) throws Exception
  {
    final Field field = LDAPReplicationDomain.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(domain);
  }

  @DataProvider
  public Object[][] assuredDeliveries()
  {
    return new Object[][] {
      { false, true, false }, { true, true, false }, { true, false, false },
      { false, true, true }, { true, true, true }, { true, false, true }
    };
  }

  @Test(dataProvider = "assuredDeliveries")
  public void queuedRedeliveryAcknowledgesOnlyTheReplayResult(
      boolean firstAssured, boolean lastAssured, boolean failReplay) throws Exception
  {
    final Field brokerField = ReplicationDomain.class.getDeclaredField("broker");
    brokerField.setAccessible(true);
    final ReplicationBroker originalBroker = (ReplicationBroker) brokerField.get(domain);
    final List<AckMsg> acks = new ArrayList<>();
    // A non-started broker retains real completion accounting but records the wire ack.
    // No domain is passed to its constructor, so it registers no second monitor.
    final ReplicationBroker recordingBroker = new ReplicationBroker(null, domain.getServerState(),
        new DomainFakeCfg(baseDN, 1, new TreeSet<String>()), new ReplSessionSecurity())
    {
      @Override
      public byte getRsGroupId()
      {
        return domain.getGroupId();
      }

      @Override
      public void publish(ReplicationMsg msg)
      {
        if (msg instanceof AckMsg)
        {
          acks.add((AckMsg) msg);
        }
      }
    };
    brokerField.set(domain, recordingBroker);
    final AtomicBoolean recovering = (AtomicBoolean) domainField("replayFailureRecovery");
    final SessionRestartRequests restarts = (SessionRestartRequests) domainField("sessionRestarts");
    recovering.set(true);
    final OutOfMemoryError failure = new OutOfMemoryError("cannot construct replay operation");
    try
    {
      final AddMsg original = failReplay ? new AddMsg(addCSN, entry.getName(), ENTRY_UUID, parentUUID,
          entry.getObjectClasses(), entry.getUserAttributes(), entry.getOperationalAttributes())
      {
        @Override
        public AddOperation createOperation(InternalClientConnection connection, DN newDN)
        {
          throw failure;
        }
      } : addMsg();
      original.setAssured(firstAssured);
      original.setAssuredMode(AssuredMode.SAFE_READ_MODE);
      domain.processUpdate(original);
      for (int i = 0; i < 2; i++)
      {
        final AddMsg duplicate = addMsg();
        duplicate.setAssured(lastAssured);
        duplicate.setAssuredMode(AssuredMode.SAFE_READ_MODE);
        domain.processUpdate(duplicate);
        assertTrue(acks.isEmpty(), "queued redelivery must not acknowledge unapplied data");
        assertEquals(completedUpdates(), 0);
      }
      try
      {
        replayQueuedMessages();
        assertFalse(failReplay, "the injected replay failure must escape");
      }
      catch (OutOfMemoryError e)
      {
        assertTrue(failReplay);
        assertSame(e, failure);
      }
      assertEquals(completedUpdates(), 1, "only the accepted replay returns window credit");
      assertEquals(acks.size(), lastAssured ? 1 : 0);
      if (lastAssured)
      {
        assertEquals(acks.get(0).getCSN(), addCSN);
        assertEquals(acks.get(0).hasReplayError(), failReplay);
      }
      assertEquals(entryExists(entry.getName()), !failReplay);
    }
    finally
    {
      brokerField.set(domain, originalBroker);
      restarts.clear();
      recovering.set(false);
    }
  }

  private AddMsg addMsg()
  {
    return new AddMsg(addCSN, entry.getName(), ENTRY_UUID, parentUUID,
        entry.getObjectClasses(), entry.getUserAttributes(), entry.getOperationalAttributes());
  }

  private DeleteMsg deleteMsg()
  {
    return new DeleteMsg(entry.getName(), deleteCSN, ENTRY_UUID);
  }

  private void replayQueuedMessages() throws Exception
  {
    while (!queue.isEmpty())
    {
      final LDAPUpdateMsg msg = queue.take().getUpdateMessage();
      if (domain.markInProgress(msg))
      {
        domain.replay(msg, new AtomicBoolean(false));
      }
    }
  }

  private int processedUpdates() throws Exception
  {
    // The monitor counter is package-private in service; this domain has no RS to register a monitor.
    final Method getter = ReplicationDomain.class.getDeclaredMethod("getNumProcessedUpdates");
    getter.setAccessible(true);
    return (Integer) getter.invoke(domain);
  }
}
