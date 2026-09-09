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

import java.util.concurrent.atomic.AtomicReference;

/**
 * The session restart a replication domain has been asked for and has not run yet.
 * <p>
 * A change which was released without being replayed is delivered again only over a new
 * session, so the thread which released it asks this domain for one. The request is what
 * the restart is run from, and it outlives the thread which made it: several threads
 * releasing changes at once are answered by one restart, and a restart which could not
 * run gives the request back rather than take it away with it - a change nobody asks for
 * again holds the ServerState of this domain back for as long as the server is up.
 * <p>
 * The wait a failing backend is owed belongs to the request rather than to the thread
 * which runs it: a thread which is not owed one - a replay thread on its way out, where
 * the backend is not what is going away - must not spend the wait another thread's
 * request was made with.
 */
class SessionRestartRequests
{
  /**
   * What a domain has been asked to do with its session, from what is not being asked for
   * to what is asked for most insistently: {@link #merge(SessionRestart)} keeps the
   * furthest down this list, so that a request is never answered by less than it asked
   * for.
   */
  enum SessionRestart
  {
    /** Nothing is being asked for: every request made has been run. */
    NONE,
    /** The session is to be restarted as soon as a thread can run it. */
    NOW,
    /**
     * The session is to be restarted once the backend has been left the time to recover
     * which the restarts made in a row have climbed to.
     */
    AFTER_BACKOFF;
  }

  private final AtomicReference<SessionRestart> requested =
      new AtomicReference<>(SessionRestart.NONE);

  /**
   * Asks this domain to restart its session.
   *
   * @param restart what is being asked for, {@link SessionRestart#NONE} asking for
   *          nothing
   */
  void request(SessionRestart restart)
  {
    merge(restart);
  }

  /**
   * Takes the request which is standing, so that the caller runs it.
   * <p>
   * It is taken before the restart is run rather than once it has run: a change released
   * while the restart was under way is not one that restart asks for - its delivery would
   * have been turned down as a duplicate of a change a replay thread still owned - so the
   * request it makes must outlive the restart which was already running.
   *
   * @return what is being asked for, {@link SessionRestart#NONE} when nothing is
   */
  SessionRestart take()
  {
    return requested.getAndSet(SessionRestart.NONE);
  }

  /**
   * Asks again for a restart which was taken and could not be run.
   *
   * @param taken what {@link #take()} returned to the caller which could not run it
   */
  void giveBack(SessionRestart taken)
  {
    merge(taken);
  }

  /** Forgets what this domain was asked for, its pending changes being gone with it. */
  void clear()
  {
    requested.set(SessionRestart.NONE);
  }

  /**
   * Returns whether a restart is being asked for.
   *
   * @return {@code true} when a restart has been asked for and not run yet
   */
  boolean isPending()
  {
    return requested.get() != SessionRestart.NONE;
  }

  private void merge(SessionRestart restart)
  {
    requested.accumulateAndGet(restart,
        (standing, asked) -> standing.compareTo(asked) >= 0 ? standing : asked);
  }
}
