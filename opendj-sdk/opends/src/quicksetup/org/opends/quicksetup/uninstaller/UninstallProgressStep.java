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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.uninstaller;

/**
 * This enumeration describes the different uninstallation steps in which we can
 * be.
 *
 */
public enum UninstallProgressStep
{
  /**
   * Uninstall not started.
   */
  NOT_STARTED,
  /**
   * Stopping server.
   */
  STOPPING_SERVER,
  /**
   * Removing External Database files.
   */
  DELETING_EXTERNAL_DATABASE_FILES,
  /**
   * Removing External Log files.
   */
  DELETING_EXTERNAL_LOG_FILES,
  /**
   * Removing external references.
   */
  REMOVING_EXTERNAL_REFERENCES,
  /**
   * Removing installation files.
   */
  DELETING_INSTALLATION_FILES,
  /**
   * Installation finished successfully.
   */
  FINISHED_SUCCESSFULLY,
  /**
   * Installation finished with an error.
   */
  FINISHED_WITH_ERROR
}
