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

package org.opends.quicksetup;

/**
 * This class describes methods for supporting interaction with the user.
 */
public interface UserInteraction {

  /**
   * Type of message displayed to the user.  The type of message
   * may affect the presentation of the interaction.
   */
  public enum MessageType {

    /** A message with no context. */
    PLAIN,

    /** A message displayed as a result of an error. */
    ERROR,

    /** A message displayed informing the user of something. */
    INFORMATION,

    /** A message displayed to warn the user. */
    WARNING,

    /** A message displayed to ask the user a question. */
    QUESTION
  }

  /**
   * Present a list of choices to the user and wait for them to select one
   * of them.
   * @param summary text to present to the user.  This is usually just a
   *        string bug For GUI applications can be a component that will appear
   *        inside a dialog
   * @param detail more details of the message
   * @param title of the prompt if any
   * @param type of message
   * @param options set of options to give the user
   * @param def the default option from <code>options</code>
   * @return Object that is the same value as the user selection from the
   *         <code>options</code> parameter.
   */
  Object confirm(String summary, String detail,
                 String title, MessageType type, String[] options, String def);

  /**
   * Present a list of choices to the user and wait for them to select one
   * of them.
   * @param summary text to present to the user.  This is usually just a
   *        string bug For GUI applications can be a component that will appear
   *        inside a dialog
   * @param detail more details of the message
   * @param fineDetails even finer details.  This text may be rendered in
   *        such a way that the user needs to take some sort of action to
   *        see this text
   * @param title of the prompt if any
   * @param type of message
   * @param options set of options to give the user
   * @param def the default option from <code>options</code>
   * @param viewDetailsOption name of the option to be used for showing the
   *        details.  If null a default will be used.
   * @return Object that is the same value as the user selection from the
   *         <code>options</code> parameter.
   */
  Object confirm(String summary, String detail, String fineDetails,
                 String title, MessageType type, String[] options, String def,
                 String viewDetailsOption);

}
