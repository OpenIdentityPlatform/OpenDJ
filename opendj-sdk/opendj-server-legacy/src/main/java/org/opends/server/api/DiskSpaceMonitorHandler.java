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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions Copyright 2015 ForgeRock AS
 */

package org.opends.server.api;

import java.io.File;


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
   * @param directory the directory for which the threshold has been triggered
   * @param thresholdInBytes the threshold value in bytes
   */
  void diskLowThresholdReached(File directory, long thresholdInBytes);

  /**
   * Notifies that the registered "full" threshold have been reached.
   *
   * @param directory the directory for which the threshold has been triggered
   * @param thresholdInBytes the threshold value in bytes
   */
  void diskFullThresholdReached(File directory, long thresholdInBytes);

  /**
   * Notifies that the free disk space is now above both "low" and "full" thresholds.
   *
   * @param directory the directory for which the threshold has been triggeredTODO
   *
   * @param lowThresholdInBytes the low threshold value in bytes
   * @param fullThresholdInBytes the full threshold value in bytes
   */
  void diskSpaceRestored(File directory, long lowThresholdInBytes, long fullThresholdInBytes);
}
