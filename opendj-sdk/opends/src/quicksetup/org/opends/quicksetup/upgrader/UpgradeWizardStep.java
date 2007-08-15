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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.upgrader;

import org.opends.messages.Message;
import static org.opends.messages.QuickSetupMessages.*;

import org.opends.quicksetup.WizardStep;

/**
 * Steps in the Upgrade wizard.
 */
public enum UpgradeWizardStep implements WizardStep {

  /**
   * The welcome step.
   */
  WELCOME(INFO_WELCOME_STEP.get()),
  /**
   * The welcome step.
   */
  REVIEW(INFO_REVIEW_STEP.get()),
  /**
   * The progress step.
   */
  PROGRESS(INFO_PROGRESS_STEP.get()),
  /**
   * The finished step.
   */
  FINISHED(INFO_FINISHED_STEP.get());

  private Message msg;

  private UpgradeWizardStep(Message msg) {
    this.msg = msg;
  }

  /**
   * {@inheritDoc}
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

  /**
   * Returns an String representation of this object.
   * @return an String representation of this object.  This method is
   * overwritten in order to be able to use this objects as keys in Maps and
   * make them different to the Steps defined in the Step class.
   */
  public String toString() {
    return "UpgradWizardStep"+super.toString();
  }
}
