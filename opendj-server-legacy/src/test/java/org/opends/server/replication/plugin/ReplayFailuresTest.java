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

import static org.testng.Assert.*;

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.CSNGenerator;
import org.testng.annotations.Test;

/**
 * Tests how long, and how many times, the replay of each change a replica could not
 * apply is remembered as having failed.
 */
@SuppressWarnings("javadoc")
public class ReplayFailuresTest extends DirectoryServerTestCase
{
  private static final int SERVER_ID = 42;
  private static final int MAX_TRACKED = 3;

  /**
   * A backend which is failing fails every change in flight: the failures of one change
   * must not reset the ones of another, or the replica would never give up on any of
   * them.
   */
  @Test
  public void failuresAreCountedPerChange()
  {
    final ReplayFailures failures = new ReplayFailures(MAX_TRACKED);
    final CSNGenerator generator = new CSNGenerator(SERVER_ID, 0);
    final CSN first = generator.newCSN();
    final CSN second = generator.newCSN();

    assertEquals(failures.recordFailure(first, 1000).getAttempts(), 1);
    assertEquals(failures.recordFailure(second, 1100).getAttempts(), 1);
    assertEquals(failures.recordFailure(first, 1200).getAttempts(), 2);
    assertEquals(failures.recordFailure(second, 1300).getAttempts(), 2);

    assertEquals(failures.recordFailure(first, 1400).getFailingForMs(), 400);
    assertEquals(failures.recordFailure(second, 1500).getFailingForMs(), 400);
    assertEquals(failures.size(), 2);
  }

  /** A change which was replayed or given up on keeps none of its failures. */
  @Test
  public void aChangeWhichIsForgottenStartsOver()
  {
    final ReplayFailures failures = new ReplayFailures(MAX_TRACKED);
    final CSN csn = new CSNGenerator(SERVER_ID, 0).newCSN();

    failures.recordFailure(csn, 1000);
    assertEquals(failures.recordFailure(csn, 1100).getAttempts(), 2);

    failures.forget(csn);

    final ReplayFailures.Failure failure = failures.recordFailure(csn, 1200);
    assertEquals(failure.getAttempts(), 1);
    assertEquals(failure.getFailingForMs(), 0, "a change which was forgotten must not be given up on straight away");
  }

  /**
   * A single delivery of a change can take a long time to fail - the replay is attempted
   * in place several times and each attempt waits on the storage - so however far apart
   * two failures are, they belong to the same run: a change which stopped failing is
   * forgotten rather than left to go stale.
   */
  @Test
  public void failuresFarApartStillBelongToTheSameRun()
  {
    final ReplayFailures failures = new ReplayFailures(MAX_TRACKED);
    final CSN csn = new CSNGenerator(SERVER_ID, 0).newCSN();

    failures.recordFailure(csn, 1000);

    final ReplayFailures.Failure failure = failures.recordFailure(csn, 1000 + 600000);
    assertEquals(failure.getAttempts(), 2);
    assertEquals(failure.getFailingForMs(), 600000,
        "a change must be given up on however long its deliveries take to fail");
  }

  /**
   * Past the bound, the change which stopped failing the longest ago is the one which is
   * evicted: evicting the one which is still being retried would reset the budget it is
   * about to reach and the replica would restart its session without end.
   */
  @Test
  public void theChangeWhichStoppedFailingTheLongestAgoIsEvicted()
  {
    final ReplayFailures failures = new ReplayFailures(MAX_TRACKED);
    final CSNGenerator generator = new CSNGenerator(SERVER_ID, 0);
    final CSN retried = generator.newCSN();
    final CSN idle = generator.newCSN();
    final CSN other = generator.newCSN();

    // The oldest change is the one which keeps failing, the others failed once.
    failures.recordFailure(retried, 1000);
    failures.recordFailure(idle, 1100);
    failures.recordFailure(other, 1200);
    failures.recordFailure(retried, 1300);
    failures.recordFailure(other, 1400);

    // One change too many: the one which did not fail since is the one to go.
    failures.recordFailure(generator.newCSN(), 1500);

    assertEquals(failures.size(), MAX_TRACKED);
    assertEquals(failures.recordFailure(retried, 1600).getAttempts(), 3,
        "the change which keeps failing must keep its failures");
    assertEquals(failures.recordFailure(idle, 1700).getAttempts(), 1,
        "the change which stopped failing the longest ago must have been evicted");
  }

  /** A disabled domain forgets the failures which go with the ServerState it dropped. */
  @Test
  public void clearForgetsEveryChange()
  {
    final ReplayFailures failures = new ReplayFailures(MAX_TRACKED);
    final CSNGenerator generator = new CSNGenerator(SERVER_ID, 0);
    final CSN csn = generator.newCSN();

    failures.recordFailure(csn, 1000);
    failures.recordFailure(generator.newCSN(), 1000);
    assertEquals(failures.size(), 2);

    failures.clear();

    assertEquals(failures.size(), 0);
    assertEquals(failures.recordFailure(csn, 1100).getAttempts(), 1);
  }
}
