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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.util;

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
  public String getFormattedText(String text);

  /**
   * Returns the formatted representation of the text that is the summary of the
   * installation process (the one that goes in the UI next to the progress
   * bar).
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a summary for the given text.
   */
  public String getFormattedSummary(String text);

  /**
   * Returns the formatted representation of an error for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @param applyMargin specifies whether we apply a margin or not to the
   * resulting formatted text.
   * @return the formatted representation of an error for the given text.
   */
  public String getFormattedError(String text, boolean applyMargin);

  /**
   * Returns the formatted representation of a warning for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @param applyMargin specifies whether we apply a margin or not to the
   * resulting formatted text.
   * @return the formatted representation of a warning for the given text.
   */
  public String getFormattedWarning(String text, boolean applyMargin);

  /**
   * Returns the formatted representation of a success message for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a success message for the given
   * text.
   */
  public String getFormattedSuccess(String text);

  /**
   * Returns the formatted representation of a log error message for a given
   * text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a log error message for the given
   * text.
   */
  public String getFormattedLogError(String text);

  /**
   * Returns the formatted representation of a log message for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a log message for the given text.
   */
  public String getFormattedLog(String text);

  /**
   * Returns the formatted representation of the 'Done' text string.
   * @return the formatted representation of the 'Done' text string.
   */
  public String getFormattedDone();

  /**
   * Returns the formatted representation of the argument text to which we add
   * points.  For instance if we pass as argument 'Configuring Server' the
   * return value will be 'Configuring Server .....'.
   * @param text the String to which add points.
   * @return the formatted representation of the '.....' text string.
   */
  public String getFormattedWithPoints(String text);

  /**
   * Returns the formatted representation of a progress message for a given
   * text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a progress message for the given
   * text.
   */
  public String getFormattedProgress(String text);

  /**
   * Returns the formatted representation of an error message for a given
   * exception.
   * This method applies a margin if the applyMargin parameter is
   * <CODE>true</CODE>.
   * @param ex the exception.
   * @param applyMargin specifies whether we apply a margin or not to the
   * resulting formatted text.
   * @return the formatted representation of an error message for the given
   * exception.
   */
  public String getFormattedError(Exception ex, boolean applyMargin);

  /**
   * Returns the line break formatted.
   * @return the line break formatted.
   */
  public String getLineBreak();

  /**
   * Returns the tab formatted.
   * @return the tab formatted.
   */
  public String getTab();

  /**
   * Returns the task separator formatted.
   * @return the task separator formatted.
   */
  public String getTaskSeparator();

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
  public String getFormattedAfterUrlClick(String url, String lastText);
}
