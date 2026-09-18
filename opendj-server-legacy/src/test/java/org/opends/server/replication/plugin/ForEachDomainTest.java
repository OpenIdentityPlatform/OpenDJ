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

import static org.assertj.core.api.Assertions.*;
import static org.opends.server.TestCaseUtils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.opendj.ldap.DN;
import org.opends.server.TestCaseUtils;
import org.opends.server.backends.MemoryBackend;
import org.opends.server.replication.ReplicationTestCase;
import org.opends.server.replication.server.ReplServerFakeConfiguration;
import org.opends.server.replication.server.ReplicationServer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests that {@link MultimasterReplication#forEachDomain} gives every domain its turn
 * whatever one of them threw, and reports the failures the way it says it does: the first
 * is thrown once the loop is over, the others are suppressed under it where it records
 * suppression, and an instance thrown by two domains - the error a JVM out of memory hands
 * out as often as it is asked for one - is thrown once and suppresses nothing (issue #986).
 * <p>
 * Two domains, on backends of their own, neither of them started: the action is what
 * throws, and it never touches the domain it is given.
 */
@SuppressWarnings("javadoc")
public class ForEachDomainTest extends ReplicationTestCase
{
  private static final int RS_ID = 613;
  private static final String SECOND_BACKEND_ID = "test2";
  private static final String SECOND_ROOT_DN_STRING = "o=" + SECOND_BACKEND_ID;

  private DN firstBaseDN;
  private DN secondBaseDN;
  private ReplicationServer replicationServer;
  private LDAPReplicationDomain firstDomain;
  private LDAPReplicationDomain secondDomain;

  @BeforeMethod
  public void setUpLocal() throws Exception
  {
    firstBaseDN = DN.valueOf(TEST_ROOT_DN_STRING);
    secondBaseDN = DN.valueOf(SECOND_ROOT_DN_STRING);
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.initializeMemoryBackend(SECOND_BACKEND_ID, SECOND_ROOT_DN_STRING, true);

    final int rsPort = TestCaseUtils.findFreePort();
    replicationServer = new ReplicationServer(new ReplServerFakeConfiguration(
        rsPort, "forEachDomainTestDb", 0, RS_ID, 0, 100, new TreeSet<String>()));
    final SortedSet<String> replServers = new TreeSet<>();
    replServers.add("localhost:" + rsPort);
    firstDomain = MultimasterReplication.createNewDomain(new DomainFakeCfg(firstBaseDN, 1, replServers));
    secondDomain = MultimasterReplication.createNewDomain(new DomainFakeCfg(secondBaseDN, 2, replServers));
  }

  @AfterMethod
  public void tearDown() throws Exception
  {
    try
    {
      MultimasterReplication.deleteDomain(firstBaseDN);
      MultimasterReplication.deleteDomain(secondBaseDN);
    }
    finally
    {
      try
      {
        remove(replicationServer);
      }
      finally
      {
        final MemoryBackend backend = (MemoryBackend) getServerContext().getBackendConfigManager()
            .getLocalBackendById(SECOND_BACKEND_ID);
        if (backend != null)
        {
          backend.clearMemoryBackend();
          backend.finalizeBackend();
          getServerContext().getBackendConfigManager().deregisterLocalBackend(backend);
        }
      }
    }
  }

  @Test
  public void theFirstFailureIsThrownOnceTheLoopIsOverWithTheOthersSuppressedUnderIt()
  {
    final RuntimeException first = new RuntimeException("first");
    final RuntimeException second = new RuntimeException("second");
    final List<LDAPReplicationDomain> visited = new ArrayList<>();

    final Throwable thrown = catchThrowable(() -> MultimasterReplication.forEachDomain(domain ->
    {
      visited.add(domain);
      throw visited.size() == 1 ? first : second;
    }));

    assertThat(thrown).as("the failure thrown once the loop is over must be the first one met")
        .isSameAs(first);
    assertThat(thrown.getSuppressed()).as("the failure met after the first must be suppressed under it")
        .containsExactly(second);
    assertThat(visited).as("every domain must get its turn whatever the one before it threw")
        .containsExactlyInAnyOrder(firstDomain, secondDomain);
  }

  @Test
  public void aFailureThrownByTwoDomainsIsThrownOnceAndSuppressesNothing()
  {
    // The error a JVM out of memory prepared beforehand is handed out to every domain alike.
    final OutOfMemoryError theOneError = new OutOfMemoryError("prepared beforehand");
    final List<LDAPReplicationDomain> visited = new ArrayList<>();

    final Throwable thrown = catchThrowable(() -> MultimasterReplication.forEachDomain(domain ->
    {
      visited.add(domain);
      throw theOneError;
    }));

    assertThat(thrown).as("the failure thrown once the loop is over must be the one both domains threw")
        .isSameAs(theOneError);
    assertThat(thrown.getSuppressed()).as("a failure must not be suppressed under itself").isEmpty();
    assertThat(visited).as("every domain must get its turn whatever the one before it threw")
        .containsExactlyInAnyOrder(firstDomain, secondDomain);
  }

  @Test
  public void aFirstFailureWhichRecordsNoSuppressionStillGivesEveryDomainItsTurn()
  {
    final Error first = new ErrorWhichRecordsNoSuppression();
    final RuntimeException second = new RuntimeException("second");
    final List<LDAPReplicationDomain> visited = new ArrayList<>();

    final Throwable thrown = catchThrowable(() -> MultimasterReplication.forEachDomain(domain ->
    {
      visited.add(domain);
      if (visited.size() == 1)
      {
        throw first;
      }
      throw second;
    }));

    assertThat(thrown).as("the failure thrown once the loop is over must be the first one met")
        .isSameAs(first);
    assertThat(thrown.getSuppressed()).as("a failure which records no suppression must have none recorded")
        .isEmpty();
    assertThat(visited).as("every domain must get its turn whatever the one before it threw")
        .containsExactlyInAnyOrder(firstDomain, secondDomain);
  }

  /**
   * An error which keeps no record of the failures suppressed under it: the shape of the
   * error a JVM out of memory prepared beforehand, which was made without its constructor
   * and so keeps no list to record them in.
   */
  private static final class ErrorWhichRecordsNoSuppression extends Error
  {
    private static final long serialVersionUID = 1L;

    private ErrorWhichRecordsNoSuppression()
    {
      super("records no suppression", null, false, false);
    }
  }
}
