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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */

package org.opends.quicksetup.util;

import org.forgerock.i18n.LocalizableMessage;

/**
 * This interface has been created in order to share the same formatting code
 * for both the Installer and the Uninstaller classes.   This way we have the
 * same look and feel for both.  In addition to that not having the format of
 * the message inside the Installer/Uninstaller classes is cleaner, as these
 * classes should no nothing about the classes that are in charge of displaying
 * the progress.
 *
 */
public interface ProgressMessageFormatter
{

  /**
   * Returns the formatted representation of the text without providing any
   * style.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation for the given text.
   */
  LocalizableMessage getFormattedText(LocalizableMessage text);

  /**
   * Returns the formatted representation of the text that is the summary of the
   * installation process (the one that goes in the UI next to the progress
   * bar).
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a summary for the given text.
   */
  LocalizableMessage getFormattedSummary(LocalizableMessage text);

  /**
   * Returns the formatted representation of an error for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @param applyMargin specifies whether we apply a margin or not to the
   * resulting formatted text.
   * @return the formatted representation of an error for the given text.
   */
  LocalizableMessage getFormattedError(LocalizableMessage text, boolean applyMargin);

  /**
   * Returns the formatted representation of a warning for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @param applyMargin specifies whether we apply a margin or not to the
   * resulting formatted text.
   * @return the formatted representation of a warning for the given text.
   */
  LocalizableMessage getFormattedWarning(LocalizableMessage text, boolean applyMargin);

  /**
   * Returns the formatted representation of a success message for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a success message for the given
   * text.
   */
  LocalizableMessage getFormattedSuccess(LocalizableMessage text);

  /**
   * Returns the formatted representation of a log error message for a given
   * text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a log error message for the given
   * text.
   */
  LocalizableMessage getFormattedLogError(LocalizableMessage text);

  /**
   * Returns the formatted representation of a log message for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a log message for the given text.
   */
  LocalizableMessage getFormattedLog(LocalizableMessage text);

  /**
   * Returns the formatted representation of the 'Done' text string.
   * @return the formatted representation of the 'Done' text string.
   */
  LocalizableMessage getFormattedDone();

  /**
   * Returns the formatted representation of the 'Error' text string.
   * @return the formatted representation of the 'Error' text string.
   */
  LocalizableMessage getFormattedError();

  /**
   * Returns the formatted representation of the argument text to which we add
   * points.  For instance if we pass as argument 'Configuring Server' the
   * return value will be 'Configuring Server .....'.
   * @param text the String to which add points.
   * @return the formatted representation of the '.....' text string.
   */
  LocalizableMessage getFormattedWithPoints(LocalizableMessage text);

  /**
   * Returns the formatted representation of a point.
   * @return the formatted representation of the '.' text string.
   */
  LocalizableMessage getFormattedPoint();

  /**
   * Returns the formatted representation of a space.
   * @return the formatted representation of the ' ' text string.
   */
  LocalizableMessage getSpace();

  /**
   * Returns the formatted representation of a progress message for a given
   * text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a progress message for the given
   * text.
   */
  LocalizableMessage getFormattedProgress(LocalizableMessage text);

  /**
   * Returns the formatted representation of an error message for a given
   * throwable.
   * This method applies a margin if the applyMargin parameter is
   * <CODE>true</CODE>.
   * @param t the throwable.
   * @param applyMargin specifies whether we apply a margin or not to the
   * resulting formatted text.
   * @return the formatted representation of an error message for the given
   * exception.
   */
  LocalizableMessage getFormattedError(Throwable t, boolean applyMargin);

  /**
   * Returns the line break formatted.
   * @return the line break formatted.
   */
  LocalizableMessage getLineBreak();

  /**
   * Returns the tab formatted.
   * @return the tab formatted.
   */
  LocalizableMessage getTab();

  /**
   * Returns the task separator formatted.
   * @return the task separator formatted.
   */
  LocalizableMessage getTaskSeparator();

  /**
   * Returns the log formatted representation after the user has clicked on a
   * url.
   *
   * @param url that has been clicked
   * @param lastText the formatted representation of the progress log before
   * clicking on the url.
   * @return the formatted progress log representation after the user has
   * clicked on a url.
   */
  LocalizableMessage getFormattedAfterUrlClick(String url, LocalizableMessage lastText);
}
