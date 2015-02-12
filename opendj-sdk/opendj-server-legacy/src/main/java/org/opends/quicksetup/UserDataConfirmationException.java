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
 * This exception is used when there is an confirmation to be asked
 * concerning the data provided by the user.  It will be thrown by the class
 * that is in charge of validating the user data (the Application class).
 *
 */
public class UserDataConfirmationException extends UserDataException
{
  private static final long serialVersionUID = -2662491859817280513L;

  /**
   * Constructor for UserDataConfirmationException.
   * @param step the step in the wizard where the exception occurred.
   * @param message the localized message describing the error.
   */
  public UserDataConfirmationException(WizardStep step, LocalizableMessage message)
  {
    super(step, message);
  }
}

