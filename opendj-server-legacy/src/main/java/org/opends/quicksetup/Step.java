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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.quicksetup;

import org.forgerock.i18n.LocalizableMessage;
import static org.opends.messages.QuickSetupMessages.*;

/**
 * This enumeration just represents the different steps that we can have in
 * the installation and uninstallation wizards.
 */
public enum Step implements WizardStep
{
  /** Welcome step for the installation. */
  WELCOME(INFO_WELCOME_STEP.get()),

  /** License approval step for the installation. */
  LICENSE(INFO_LICENSE_STEP.get()),

  /** Confirmation panel for the uninstallation. */
  CONFIRM_UNINSTALL(INFO_CONFIRM_UNINSTALL_STEP.get()),

  /** Server Settings step (path, port, etc.). */
  SERVER_SETTINGS(INFO_SERVER_SETTINGS_STEP.get()),

  /** Data Replication panel (standalone or replicated). */
  REPLICATION_OPTIONS(INFO_DATA_REPLICATION_STEP.get()),
  /** Global Administrator creation panel. */
  CREATE_GLOBAL_ADMINISTRATOR(INFO_CREATE_GLOBAL_ADMINISTRATOR_STEP.get()),
  /** Suffixes to Replicate. */
  SUFFIXES_OPTIONS(INFO_SUFFIXES_STEP.get()),
  /**
   * Panel when the user specifies the replication ports of the remote servers
   * that have not defined it.
   */
  REMOTE_REPLICATION_PORTS(INFO_REMOTE_REPLICATION_PORTS_STEP.get()),
  /** Data Options panel (suffix dn, LDIF path, etc.). */
  NEW_SUFFIX_OPTIONS(INFO_DATA_OPTIONS_STEP.get()),

  /** Runtime options panel for the install. */
  RUNTIME_OPTIONS(INFO_JAVA_RUNTIME_OPTIONS_PANEL_STEP.get()),

  /** Review panel for the install. */
  REVIEW(INFO_REVIEW_STEP.get()),

  /** Progress panel. */
  PROGRESS(INFO_PROGRESS_STEP.get()),

  /** Finished panel. */
  FINISHED(INFO_FINISHED_STEP.get());

  private LocalizableMessage msg;

  /**
   * Creates a step.
   * @param msg the message key used to access a message catalog to
   * retrieve this step's display name
   */
  Step(LocalizableMessage msg) {
    this.msg = msg;
  }

  /**
   * Gets this steps message key.
   * @return String message key used to access a message catalog to
   * retrieve this step's display name
   */
  @Override
  public LocalizableMessage getDisplayMessage() {
    return msg;
  }

  @Override
  public boolean isProgressStep() {
    return this == PROGRESS;
  }

  @Override
  public boolean isFinishedStep() {
    return this == FINISHED;
  }

  @Override
  public boolean isLicenseStep() {
    return this == LICENSE;
  }

}
