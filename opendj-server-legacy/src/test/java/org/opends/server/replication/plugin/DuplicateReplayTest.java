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

import java.lang.reflect.Method;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.meta.ReplicationDomainCfgDefn.IsolationPolicy;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.service.ReplicationDomain;
import org.opends.server.types.Entry;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
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

  @BeforeMethod
  public void setUpLocal() throws Exception
  {
    baseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    initializeTestBackend(true);
    enqueueFailure = null;
    queue = new TestSynchronousReplayQueue()
    {
      @Override
      public boolean offer(UpdateToReplay update, long timeout, TimeUnit unit) throws InterruptedException
      {
        if (enqueueFailure != null)
        {
          throw enqueueFailure;
        }
        return super.offer(update, timeout, unit);
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
    // Pause replay until the duplicate has arrived: no timing or reconnect race is needed.
    assertFalse(domain.processUpdate(addMsg()));
    assertFalse(domain.getServerState().cover(addCSN), "discarding a duplicate must not advance state");
    assertFalse(domain.getServerState().cover(deleteCSN));

    replayQueuedMessages();

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
