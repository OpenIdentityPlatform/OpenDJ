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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014 ForgeRock AS.
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
