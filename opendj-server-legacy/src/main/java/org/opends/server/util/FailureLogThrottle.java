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
package org.opends.server.util;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounds how often a repeating failure is reported, so that a failure which a retry loop
 * or a stream of unwanted connections reproduces many times a second is still visible in
 * the error log without filling it.
 * <p>
 * One failure is logged per interval and the failures in between are counted, so that the
 * next one logged reports how many it stands for and a single line cannot be mistaken for
 * a single failure. That count looks backwards only: the failures following the last one
 * logged of a burst are counted but never reported, as nothing flushes the count when the
 * failures stop.
 * <p>
 * The first failure is always logged, however long the throttle has existed before it.
 * <p>
 * Safe to record from several threads, and exact for one: a failure recorded while
 * another thread is opening a new interval may be counted in the interval which is
 * closing or in the one which opens, so the count a caller reports can be off by the
 * failures which raced with it. It is a count of failures, not a ledger.
 */
public final class FailureLogThrottle
{
  /** Minimum interval, in nanoseconds, between two failures logged. */
  private final long intervalNanos;

  /**
   * Value of {@link System#nanoTime()} at which the last failure was logged. It starts one
   * interval in the past so that the first failure is logged.
   */
  private final AtomicLong lastLogNanos;

  /** Number of failures suppressed since the last one logged. */
  private final AtomicLong suppressed = new AtomicLong();

  /**
   * Creates a throttle logging at most one failure per provided interval.
   *
   * @param interval
   *          The interval between two failures logged.
   * @param unit
   *          The unit the interval is expressed in.
   */
  public FailureLogThrottle(final long interval, final TimeUnit unit)
  {
    this.intervalNanos = unit.toNanos(interval);
    this.lastLogNanos = new AtomicLong(System.nanoTime() - intervalNanos);
  }

  /**
   * Records a failure which happened at the provided time and tells how it must be logged,
   * together with the number of failures suppressed since the previous one logged.
   *
   * @param nowNanos
   *          The value of {@link System#nanoTime()} at which the failure happened.
   * @return A number greater than or equal to zero if this failure is to be logged, which is
   *         then the number of failures suppressed since the previous one logged, or
   *         {@code -count - 1} if this failure is itself to be suppressed, where
   *         {@code count} is the number of failures suppressed since the previous one
   *         logged, this one included.
   */
  public long record(final long nowNanos)
  {
    final long lastLog = lastLogNanos.get();
    if (nowNanos - lastLog >= intervalNanos && lastLogNanos.compareAndSet(lastLog, nowNanos))
    {
      return suppressed.getAndSet(0);
    }
    return -suppressed.incrementAndGet() - 1;
  }
}
