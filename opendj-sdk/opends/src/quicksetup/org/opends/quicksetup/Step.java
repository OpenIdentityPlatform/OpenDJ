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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup;

import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;

/**
 * This enumeration just represents the different steps that we can have in
 * the installation and uninstallation wizards.
 *
 */
public enum Step implements WizardStep

{
  /**
   * Welcome step for the installation.
   */
  WELCOME(INFO_WELCOME_STEP.get()),

  /**
   * Confirmation panel for the uninstallation.
   */
  CONFIRM_UNINSTALL(INFO_CONFIRM_UNINSTALL_STEP.get()),

  /**
   * Server Settings step (path, port, etc.).
   */
  SERVER_SETTINGS(INFO_SERVER_SETTINGS_STEP.get()),

  /**
   * Data Replication panel (standalone or replicated).
   */
  REPLICATION_OPTIONS(INFO_DATA_REPLICATION_STEP.get()),
  /**
   * Global Administrator creation panel.
   */
  CREATE_GLOBAL_ADMINISTRATOR(INFO_CREATE_GLOBAL_ADMINISTRATOR_STEP.get()),
  /**
   * Suffixes to Replicate.
   */
  SUFFIXES_OPTIONS(INFO_SUFFIXES_STEP.get()),
  /**
   * Panel when the user specifies the replication ports of the remote servers
   * that have not defined it.
   */
  REMOTE_REPLICATION_PORTS(INFO_REMOTE_REPLICATION_PORTS_STEP.get()),
  /**
   * Data Options panel (suffix dn, LDIF path, etc.).
   */
  NEW_SUFFIX_OPTIONS(INFO_DATA_OPTIONS_STEP.get()),

  /**
   * Review panel for the install.
   */
  REVIEW(INFO_REVIEW_STEP.get()),

  /**
   * Progress panel.
   */
  PROGRESS(INFO_PROGRESS_STEP.get()),

  /**
   * Finished panel.
   */
  FINISHED(INFO_FINISHED_STEP.get());

  private Message msg;

  /**
   * Creates a step.
   * @param msg the message key used to access a message catalog to
   * retreive this step's display name
   */
  Step(Message msg) {
    this.msg = msg;
  }

  /**
   * Gets this steps message key.
   * @return String message key used to access a message catalog to
   * retreive this step's display name
   */
  public Message getDisplayMessage() {
    return msg;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isProgressStep() {
    return this == PROGRESS;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isFinishedStep() {
    return this == FINISHED;
  }

}
