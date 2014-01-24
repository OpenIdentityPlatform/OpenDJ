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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */

package org.opends.quicksetup;

import org.forgerock.i18n.LocalizableMessage;

/**
 * Step in a QuickSetup wizard application.
 */
public interface WizardStep {

  /**
   * Gets the message key associated with the display name of this step.
   * @return String message key for accessing this step's display name
   * in a message bundle
   */
  LocalizableMessage getDisplayMessage();

  /**
   * Indicates that when this step is displayed the application will
   * be asked to launch itself.
   * @return true indicating that this is the progress step for the
   * application
   */
  boolean isProgressStep();

  /**
   * Indicates whether this is the finished step for the application or not.
   * @return <CODE>true</CODE> if this is the finished step for the application
   * and <CODE>false</CODE> otherwise.
   */
  boolean isFinishedStep();

  /**
   * Indicates whether this is the license approval step.
   * @return <CODE>true</CODE> if this is the license approval step
   * and <CODE>false</CODE> otherwise.
   */
  boolean isLicenseStep();

}
