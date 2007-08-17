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
import org.opends.server.types.OpenDsException;

/**
 * This exception is used when there is an error with the data provided by
 * the user.  It will be thrown by the class that is in charge of validating
 * the user data (the Application class).
 *
 */
public class UserDataException extends OpenDsException {

  private static final long serialVersionUID = 1798143194655443132L;

  private WizardStep step;

  /**
   * Constructor for UserDataException.
   * @param step the step in the wizard where the exception occurred.
   * @param message the localized message describing the error.
   */
  public UserDataException(WizardStep step, Message message)
  {
    super(message);
    this.step = step;
  }

  /**
   * Constructor for UserDataException.
   * @param step the step in the wizard where the exception occurred.
   * @param message the localized message describing the error.
   * @param t the Exception that generated this exception.
   */
  public UserDataException(WizardStep step, Message message, Throwable t)
  {
    super(message, t);
    this.step = step;
  }

  /**
   * Returns the step of the wizard in which this exception occurred.
   * @return the step of the wizard in which this exception occurred.
   */
  public WizardStep getStep()
  {
    return step;
  }
}
