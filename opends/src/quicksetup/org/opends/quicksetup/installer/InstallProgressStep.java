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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.installer;

import org.opends.quicksetup.ProgressStep;

/**
 * Enumeration of installation steps.
 */
public enum InstallProgressStep implements ProgressStep {

  /**
   * Install not started.
   */
  NOT_STARTED,

  /**
   * Downloading the remote jar files (this step is specific to the Web Start
   * installation).
   */
  DOWNLOADING,

  /**
   * Extracting the zip file (this step is specific to the Web Start
   * installation).
   */
  EXTRACTING,

  /**
   * Configuring server.
   */
  CONFIGURING_SERVER,

  /**
   * Creating base entry for the suffix.
   */
  CREATING_BASE_ENTRY,

  /**
   * Importing the contents of an LDIF file into the suffix.
   */
  IMPORTING_LDIF,

  /**
   * Importing generated data into the suffix.
   */
  IMPORTING_AUTOMATICALLY_GENERATED,

  /**
   * Configuring replication.
   */
  CONFIGURING_REPLICATION,

  /**
   * Starting Open DS server.
   */
  STARTING_SERVER,

  /**
   * Stopping Open DS server.
   */
  STOPPING_SERVER,

  /**
   * Initialize Replicated Suffixes.
   */
  INITIALIZE_REPLICATED_SUFFIXES,

  /**
   * Configuring ADS.
   */
  CONFIGURING_ADS,

  /**
   * Enabling Windows service.
   */
  ENABLING_WINDOWS_SERVICE,

  /**
   * User is waiting for current task to finish
   * so that the operation can be canceled.
   */
  WAITING_TO_CANCEL,

  /**
   * Canceling install.
   */
  CANCELING,

  /**
   * Installation finished successfully.
   */
  FINISHED_SUCCESSFULLY,

  /**
   * User canceled installation.
   */
  FINISHED_CANCELED,

  /**
   * Installation finished with an error.
   */
  FINISHED_WITH_ERROR;

  /**
   * {@inheritDoc}
   */
  public boolean isLast() {
    return this == FINISHED_SUCCESSFULLY ||
            this == FINISHED_CANCELED ||
    this == FINISHED_WITH_ERROR;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isError() {
    return this.equals(FINISHED_WITH_ERROR);
  }
}
