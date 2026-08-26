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

import java.util.LinkedHashMap;
import java.util.Map;

import net.jcip.annotations.GuardedBy;

import org.opends.server.replication.common.CSN;

/**
 * How long, and how many times, the replay of each change a replica could not apply has
 * been failing.
 * <p>
 * A backend which is failing fails every change in flight, so the failures are kept per
 * change: a single slot would be reset by each of them in turn and the replica would
 * never give up on any of them.
 * <p>
 * Only the changes which are failing are listed, and each of them is dropped as soon as
 * it is replayed or given up on, so the bound is not reached in practice. It is there so
 * that a replica failing every change it is sent can not grow this map without end. When
 * it is reached, the change which stopped failing the longest ago is the one which is
 * evicted: the ones which are still being retried are the ones whose failures must be
 * remembered.
 */
final class ReplayFailures
{
  /** The run of failures of one change: how many, and how long it has been going on. */
  static final class Failure
  {
    private final int attempts;
    private final long failingForMs;

    private Failure(int attempts, long failingForMs)
    {
      this.attempts = attempts;
      this.failingForMs = failingForMs;
    }

    /**
     * Returns how many times in a row the replay of the change failed.
     *
     * @return the number of failures, at least 1
     */
    int getAttempts()
    {
      return attempts;
    }

    /**
     * Returns how long the replay of the change has been failing, that is the time
     * between its first failure and the one which was just recorded.
     *
     * @return the duration in milliseconds, 0 for a first failure
     */
    long getFailingForMs()
    {
      return failingForMs;
    }
  }

  /** The failures of one change, as they are held in the map. */
  private static final class Run
  {
    private long firstFailureTimeMs;
    private int attempts;
  }

  @GuardedBy("this")
  private final Map<CSN, Run> runs;

  /**
   * Creates a new set of replay failures.
   *
   * @param maxTracked
   *          how many changes the failures are remembered for
   */
  ReplayFailures(final int maxTracked)
  {
    // Ordered on the last access, which is the last failure: the eldest entry is then
    // the change which stopped failing the longest ago.
    this.runs = new LinkedHashMap<CSN, Run>(16, 0.75f, true)
    {
      private static final long serialVersionUID = 1L;

      @Override
      protected boolean removeEldestEntry(Map.Entry<CSN, Run> eldest)
      {
        return size() > maxTracked;
      }
    };
  }

  /**
   * Records that the replay of the change with the provided CSN failed once more.
   *
   * @param csn
   *          the CSN of the change whose replay failed
   * @param nowMs
   *          when it failed
   * @return the run of failures this failure belongs to
   */
  synchronized Failure recordFailure(CSN csn, long nowMs)
  {
    Run run = runs.get(csn);
    if (run == null)
    {
      /*
       * The first failure of this change. A change which stops failing is forgotten -
       * it was replayed, it was given up on, or the domain it belongs to was disabled -
       * so a run which is listed here is one which is still going on, however long a
       * single delivery takes to fail.
       */
      run = new Run();
      run.firstFailureTimeMs = nowMs;
      runs.put(csn, run);
    }
    run.attempts++;
    return new Failure(run.attempts, nowMs - run.firstFailureTimeMs);
  }

  /**
   * Forgets the failures of the change with the provided CSN, which was replayed or
   * given up on.
   *
   * @param csn the CSN of the change
   */
  synchronized void forget(CSN csn)
  {
    runs.remove(csn);
  }

  /** Forgets the failures of every change, when the domain they belong to is disabled. */
  synchronized void clear()
  {
    runs.clear();
  }

  /**
   * Returns how many changes the failures are currently remembered for.
   *
   * @return the number of changes
   */
  synchronized int size()
  {
    return runs.size();
  }
}
