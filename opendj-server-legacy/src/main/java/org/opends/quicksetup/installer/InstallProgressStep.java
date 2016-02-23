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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions Copyright 2015 ForgeRock AS.
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

  /** {@inheritDoc} */
  public boolean isLast() {
    return this == FINISHED_SUCCESSFULLY ||
            this == FINISHED_CANCELED ||
    this == FINISHED_WITH_ERROR;
  }

  /** {@inheritDoc} */
  public boolean isError() {
    return this.equals(FINISHED_WITH_ERROR);
  }
}
