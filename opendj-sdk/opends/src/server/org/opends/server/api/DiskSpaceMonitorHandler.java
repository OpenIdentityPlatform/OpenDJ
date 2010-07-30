/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.opends.server.api;

/**
 * This interface defines the set of methods that must be implemented
 * for a DiskSpaceMonitorHandler.  Disk space monitor handlers are
 * used to receive notifications from the DiskSpaceMonitor service
 * when the registered thresholds have been reached.
 */
public interface DiskSpaceMonitorHandler {

  /**
   * Notifies that the registered "low" threshold have been reached.
   *
   * @param bytesFree The number of bytes free when threshold
   *                                 was reached.
   */
  public void diskLowThresholdReached(long bytesFree);

  /**
   * Notifies that the registered "full" threshold have been reached.
   *
   * @param bytesFree The number of bytes free when threshold
   *                                 was reached.
   */
  public void diskFullThresholdReached(long bytesFree);

  /**
   * Notifies that the free disk space is now above both "low" and
   * "full" thresholds.
   *
   * @param bytesFree The number of bytes free.
   */
  public void diskSpaceRestored(long bytesFree);
}
