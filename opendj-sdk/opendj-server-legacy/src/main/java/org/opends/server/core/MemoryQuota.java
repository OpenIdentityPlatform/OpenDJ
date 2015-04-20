/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2015 ForgeRock AS.
 */
package org.opends.server.core;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;
import java.util.concurrent.Semaphore;

import static org.opends.server.util.ServerConstants.*;

/**
 * Estimates the amount of memory in the running JVM for use of long term caches
 * by looking at the Old Generation, where implemented, or at the Runtime
 * information as fallback. Allows for reserving memory to avoid over commitment.
 * There is a fudge factor involved, so it is not byte exact.
 */
public final class MemoryQuota
{
  private static final long ONE_MEGABYTE = 1024 * 1024;

  private Semaphore reservedMemory;
  private int reservableMemory;
  private boolean allowOvercommit;

  /**
   * Returns the memory quota reservation system for this server instance.
   */
  public MemoryQuota()
  {
    allowOvercommit = System.getProperty(ENABLE_MEMORY_OVERCOMMIT) != null;
    reservableMemory = (int)(Math.pow(Math.E / Math.PI, 2) * (getOldGenInfo().getMax() / ONE_MEGABYTE));
    reservedMemory = new Semaphore(reservableMemory, true);
  }

  /**
   * Returns the maximum amount of memory the server will use when giving quotas.
   * @return the maximum amount of memory the server will use when giving quotas
   */
  public long getMaxMemory()
  {
    return getOldGenInfo().getMax();
  }

  private MemoryUsage getOldGenInfo()
  {
    List<MemoryPoolMXBean> mpools = ManagementFactory.getMemoryPoolMXBeans();
    for (MemoryPoolMXBean mpool : mpools)
    {
      MemoryUsage usage = mpool.getUsage();
      if (usage != null && mpool.getName().endsWith("Old Gen"))
      {
        return usage;
      }
    }
    Runtime runtime = Runtime.getRuntime();
    return new MemoryUsage(0, runtime.totalMemory() - runtime.freeMemory(), runtime.totalMemory(), runtime.maxMemory());
  }

  /**
   * Check enough memory is available in the reservable pool.
   * @param size the amount of requested memory
   * @return true if enough memory is available in the reservable pool
  */
  public boolean isMemoryAvailable(long size)
  {
    if (allowOvercommit)
    {
      return true;
    }
    if (acquireMemory(size))
    {
      releaseMemory(size);
      return true;
    }
    return false;
  }

  /**
   * Reserves the requested amount of memory in OldGen.
   *
   * @param size the requested amount of memory in bytes
   * @return true if the requested amount of memory in OldGen could be reserved
   */
  public boolean acquireMemory(long size)
  {
    return allowOvercommit
        || reservedMemory.tryAcquire((int) (size / ONE_MEGABYTE));
  }

  /**
   * Returns how much memory is currently not reserved (free) in OldGen.
   * @return how much memory is currently not reserved (free) in OldGen
   */
  public long getAvailableMemory()
  {
    if (allowOvercommit)
    {
      return reservableMemory * ONE_MEGABYTE;
    }
    return reservedMemory.availablePermits() * ONE_MEGABYTE;
  }

  /**
   * Translates bytes to percent of reservable memory.
   * @param size the amount of memory in bytes
   * @return percent of reservable memory
   */
  public int memBytesToPercent(long size)
  {
    return (int)(((size / ONE_MEGABYTE) * 100) / reservableMemory);
  }

  /**
   * Translates a percentage of memory to the equivalent number of bytes.
   * @param percent a percentage of memory
   * @return the equivalent number of bytes
   */
  public long memPercentToBytes(int percent)
  {
    return (reservableMemory * percent / 100) * ONE_MEGABYTE;
  }

  /**
   * Declares OldGen memory is not needed anymore.
   * @param size the amount of memory to return
   */
  public void releaseMemory(long size)
  {
    if (allowOvercommit)
    {
      return;
    }
    reservedMemory.release((int)(size / ONE_MEGABYTE));
  }
}
