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
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC
 */
package org.opends.server.backends.pluggable;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * A striped counter of in-flight accesses whose non-atomic {@link #sum()} scan
 * is safe for quiescence detection: it may transiently over-estimate, but can
 * never return zero while an access is still in flight.
 * <p>
 * The increment and the matching decrement of one logical access must be
 * performed by the same thread; the stripe is a pure function of the thread,
 * so both land in the same slot. A scan reads each slot once, i.e. observes a
 * prefix of each slot's modification history, and within one slot a decrement
 * can never be observed without the increment that preceded it, so every
 * per-slot subtotal is non-negative. {@link java.util.concurrent.atomic.LongAdder}
 * does not provide this: the two halves of a pair may land in different cells
 * (probe rehash after CAS contention, cell table growth), letting a scan
 * observe the decrement while missing the increment and under-count to a
 * false zero.
 */
final class StripedCounter
{
  /** 16 longs = 128 bytes between slots, to keep them on distinct cache lines. */
  private static final int SPACING = 16;
  private static final int STRIPES = nextPowerOfTwo(Runtime.getRuntime().availableProcessors());

  private final AtomicLongArray counts = new AtomicLongArray(STRIPES * SPACING);

  private static int nextPowerOfTwo(int n)
  {
    int p = 1;
    while (p < n)
    {
      p <<= 1;
    }
    return p;
  }

  private static int slot()
  {
    final long id = Thread.currentThread().getId();
    return (((int) ((id * 0x9E3779B97F4A7C15L) >>> 32)) & (STRIPES - 1)) * SPACING;
  }

  void increment()
  {
    counts.getAndIncrement(slot());
  }

  /** Must be called by the same thread that did the paired {@link #increment()}. */
  void decrement()
  {
    counts.getAndDecrement(slot());
  }

  /**
   * Returns the current count. Concurrent updates may cause over-estimation,
   * but a paired increment/decrement is never observed half-way in the
   * decrement-only direction, so the result is zero only if every access
   * whose increment is visible has completed.
   */
  long sum()
  {
    long s = 0;
    for (int i = 0; i < counts.length(); i += SPACING)
    {
      s += counts.get(i);
    }
    return s;
  }

  /** Resets the count to zero. Only safe when no accesses are in flight. */
  void reset()
  {
    for (int i = 0; i < counts.length(); i += SPACING)
    {
      counts.set(i, 0);
    }
  }
}
