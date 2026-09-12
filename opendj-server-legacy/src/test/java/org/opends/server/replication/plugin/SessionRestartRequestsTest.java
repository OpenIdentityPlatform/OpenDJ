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

import static org.opends.server.replication.plugin.SessionRestartRequests.SessionRestart.*;
import static org.testng.Assert.*;

import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.Test;

/**
 * Tests the session restarts a replication domain has been asked for and has not run yet:
 * a request is answered once, it is not lost by the thread which took it and could not
 * run it, and the backoff a failing backend is owed is not dropped by a request which is
 * not owed one.
 */
@SuppressWarnings("javadoc")
@Test(groups = { "precommit", "replication" }, sequential = true)
public class SessionRestartRequestsTest extends DirectoryServerTestCase
{
  @Test
  public void nothingIsAskedForBeforeAnythingAsks()
  {
    final SessionRestartRequests requests = new SessionRestartRequests();

    assertFalse(requests.isPending(), "a domain nobody asked anything of has nothing to run");
    assertEquals(requests.take(), NONE);
  }

  @Test
  public void aRequestIsAnsweredByOneRestart()
  {
    final SessionRestartRequests requests = new SessionRestartRequests();

    requests.request(NOW);

    assertTrue(requests.isPending());
    assertEquals(requests.take(), NOW);
    assertFalse(requests.isPending(), "the request has been taken by a thread which runs it");
    assertEquals(requests.take(), NONE);
  }

  @Test
  public void theRestartWhichIsOwedTheBackoffWinsWhicheverOrderTheyComeIn()
  {
    final SessionRestartRequests backoffFirst = new SessionRestartRequests();
    backoffFirst.request(AFTER_BACKOFF);
    backoffFirst.request(NOW);

    final SessionRestartRequests backoffLast = new SessionRestartRequests();
    backoffLast.request(NOW);
    backoffLast.request(AFTER_BACKOFF);

    assertEquals(backoffFirst.take(), AFTER_BACKOFF,
        "a thread which is not owed the backoff must not spend the one another thread is owed");
    assertEquals(backoffLast.take(), AFTER_BACKOFF);
  }

  @Test
  public void aRestartWhichCouldNotRunIsAskedForAgain()
  {
    final SessionRestartRequests requests = new SessionRestartRequests();
    requests.request(AFTER_BACKOFF);

    final SessionRestartRequests.SessionRestart taken = requests.take();
    requests.giveBack(taken);

    assertTrue(requests.isPending(), "a restart which did not run is still being asked for");
    assertEquals(requests.take(), AFTER_BACKOFF);
  }

  @Test
  public void aRestartGivenBackDoesNotUndoTheOneAskedForMeanwhile()
  {
    final SessionRestartRequests requests = new SessionRestartRequests();
    requests.request(NOW);

    final SessionRestartRequests.SessionRestart taken = requests.take();
    requests.request(AFTER_BACKOFF);
    requests.giveBack(taken);

    assertEquals(requests.take(), AFTER_BACKOFF,
        "the request which arrived while the restart was running keeps its backoff");
  }

  @Test
  public void aDomainWhichIsDisabledForgetsWhatItWasAskedFor()
  {
    final SessionRestartRequests requests = new SessionRestartRequests();
    requests.request(AFTER_BACKOFF);

    requests.clear();

    assertFalse(requests.isPending(),
        "the change the restart was asked for is gone with the pending changes");
    assertEquals(requests.take(), NONE);
  }
}
