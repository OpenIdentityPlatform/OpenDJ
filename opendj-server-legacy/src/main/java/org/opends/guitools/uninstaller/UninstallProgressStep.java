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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.guitools.uninstaller;

import org.opends.quicksetup.ProgressStep;

/** Enumeration of steps for an uninstall process. */
public enum UninstallProgressStep implements ProgressStep {

  /** Uninstall not started. */
  NOT_STARTED,
  /** Uninstall has started. */
  STARTED,
  /** Unconfiguring replication in remote servers. */
  UNCONFIGURING_REPLICATION,
  /** Stopping server. */
  STOPPING_SERVER,
  /** Disabling Windows Service. */
  DISABLING_WINDOWS_SERVICE,
  /** Removing External Database files. */
  DELETING_EXTERNAL_DATABASE_FILES,
  /** Removing External Log files. */
  DELETING_EXTERNAL_LOG_FILES,
  /** Removing external references. */
  REMOVING_EXTERNAL_REFERENCES,
  /** Removing installation files. */
  DELETING_INSTALLATION_FILES,
  /** Installation finished successfully. */
  FINISHED_SUCCESSFULLY,
  /** Installation finished with a non critical error updating remote servers. */
  FINISHED_WITH_ERROR_ON_REMOTE,
  /** Installation finished but not all the files could be deleted. */
  FINISHED_WITH_ERROR_DELETING,
  /** Installation finished with an error. */
  FINISHED_WITH_ERROR;

  @Override
  public boolean isLast() {
    return this == FINISHED_SUCCESSFULLY ||
    this == FINISHED_WITH_ERROR ||
    this == FINISHED_WITH_ERROR_ON_REMOTE ||
    this == FINISHED_WITH_ERROR_DELETING;
  }

  @Override
  public boolean isError() {
    return this.equals(FINISHED_WITH_ERROR);
  }
}
