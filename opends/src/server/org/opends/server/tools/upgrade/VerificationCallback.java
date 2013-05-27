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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.tools.upgrade;

import javax.security.auth.callback.ConfirmationCallback;

/**
 * <p>
 * Underlying security services instantiate and pass a
 * <code>VerificationCallback</code> to the <code>handle</code> method of a
 * <code>CallbackHandler</code> to verify user's options.
 */
public class VerificationCallback extends ConfirmationCallback
{
  /**
   * The serial version UID.
   */
  private static final long serialVersionUID = 1L;

  /**
   * An identifier of a task which need a user interaction.
   */
  public static final int NEED_USER_INTERACTION = 0;

  /**
   * An identifier of a task which require long time to complete.
   */
  public static final int TAKE_LONG_TIME_TO_COMPLETE = 1;

  /**
   * An identifier of a task which cannot be reverted once started.
   */
  public static final int CANNOT_BE_REVERTED = 2;

  /**
   * An identifier of the accept license mode.
   */
  public static final int ACCEPT_LICENSE_MODE = 3;

  /**
   * The identifier of ignore errors mode.
   */
  public static final int IGNORE_ERRORS_MODE = 5;

  /**
   * The identifier of mandatory user interaction.
   */
  public static final int MANDATORY_USER_INTERACTION = 6;


  // The required options for the verification callback.
  private int[] requiredOptions;

  /**
   * Construct a verification callback, which checks user options selected with
   * required options needed by the process.
   *
   * @param messageType
   *          The type of the message.
   * @param optionType
   *          The type of the option.
   * @param defaultOption
   *          The default selected option.
   * @param requiredOptions
   *          The required option.
   */
  public VerificationCallback(int messageType, int optionType,
      int defaultOption, int... requiredOptions)
  {
    super(messageType, optionType, defaultOption);
    this.requiredOptions = requiredOptions;
  }

  /**
   * Returns options required in user's options.
   *
   * @return The options required which need to be verified.
   */
  public int[] getRequiredOptions()
  {
    return requiredOptions;
  }

  /**
   * Sets the required options which must be present in the user's options.
   *
   * @param requiredOptions
   *          The options required which need to be verified.
   */
  public void setRequiredOptions(final int... requiredOptions)
  {
    this.requiredOptions = requiredOptions;
  }

}
