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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.util;

import org.opends.quicksetup.i18n.ResourceProvider;

/**
 * This is an implementation of the ProgressMessageFormatter class that
 * provides format in plain text.
 *
 */
public class PlainTextProgressMessageFormatter
implements ProgressMessageFormatter
{
  private String doneText;

  /**
   * The line break in plain text.
   */
  private static String LINE_BREAK = System.getProperty("line.separator");

  /**
   * The space in plain text.
   */
  private static String SPACE = " ";

  /**
   * Returns the text representation of the text without providing any style.
   * @param text the source text from which we want to get the text
   * representation
   * @return the text representation for the given text.
   */
  public String getFormattedText(String text)
  {
    return text;
  }

  /**
   * Returns the plain text representation of the text that is the summary of
   * the installation process (the one that goes in the UI next to the progress
   * bar).
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the text representation of the summary for the given text.
   */
  public String getFormattedSummary(String text)
  {
    return text;
  }

  /**
   * Returns the plain text representation of an error for a given text.
   * @param text the source text from which we want to get the plain text
   * representation
   * @param applyMargin specifies whether we apply a margin or not to the
   * resulting formatted text.
   * @return the plain text representation of an error for the given text.
   */
  public String getFormattedError(String text, boolean applyMargin)
  {
    String result;
    if (applyMargin)
    {
      result = LINE_BREAK+text;
    } else
    {
      result = text;
    }
    return result;
  }

  /**
   * Returns the plain text representation of a warning for a given text.
   * @param text the source text from which we want to get the plain text
   * representation
   * @param applyMargin specifies whether we apply a margin or not to the
   * resulting formatted text.
   * @return the plain text representation of a warning for the given text.
   */
  public String getFormattedWarning(String text, boolean applyMargin)
  {
    String result;
    if (applyMargin)
    {
      result = LINE_BREAK+text;
    } else
    {
      result = text;
    }
    return result;
  }

  /**
   * Returns the plain text representation of a success message for a given
   * text.
   * @param text the source text from which we want to get the plain text
   * representation
   * @return the plain text representation of a success message for the given
   * text.
   */
  public String getFormattedSuccess(String text)
  {
    return text;
  }

  /**
   * Returns the plain text representation of a log error message for a given
   * text.
   * @param text the source text from which we want to get the plain text
   * representation
   * @return the plain text representation of a log error message for the given
   * text.
   */
  public String getFormattedLogError(String text)
  {
    return text;
  }


  /**
   * Returns the plain text representation of a log message for a given text.
   * @param text the source text from which we want to get the plain text
   * representation
   * @return the plain text representation of a log message for the given text.
   */
  public String getFormattedLog(String text)
  {
    return text;
  }

  /**
   * Returns the plain text representation of the 'Done' text string.
   * @return the plain text representation of the 'Done' text string.
   */
  public String getFormattedDone()
  {
    if (doneText == null)
    {
      doneText = getMsg("progress-done");
    }
    return doneText;
  }

  /**
   * Returns the plain text representation of the argument text to which we add
   * points.  For instance if we pass as argument 'Configuring Server' the
   * return value will be 'Configuring Server .....'.
   * @param text the String to which add points.
   * @return the plain text representation of the '.....' text string.
   */
  public String getFormattedWithPoints(String text)
  {
    return text + SPACE + getMsg("progress-points");
  }

  /**
   * Returns the formatted representation of a progress message for a given
   * text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a progress message for the given
   * text.
   */
  public String getFormattedProgress(String text)
  {
    return text;
  }

  /**
   * Returns the plain text representation of an error message for a given
   * exception.
   * This method applies a margin if the applyMargin parameter is
   * <CODE>true</CODE>.
   * @param ex the exception.
   * @param applyMargin specifies whether we apply a margin or not to the
   * resulting plain text.
   * @return the plain text representation of an error message for the given
   * exception.
   */
  public String getFormattedError(Exception ex, boolean applyMargin)
  {
    String msg = ex.getMessage();
    if (msg == null)
    {
      msg = ex.toString();
    }
    String result;
    if (applyMargin)
    {
      result = LINE_BREAK+msg;
    } else
    {
      result = msg;
    }
    return result;
  }

  /**
   * Returns the line break in plain text.
   * @return the line break in plain text.
   */
  public String getLineBreak()
  {
    return LINE_BREAK;
  }

  /**
   * Returns the tab in plain text.
   * @return the tab in plain text.
   */
  public String getTab()
  {
    return "     ";
  }

  /**
   * Returns the task separator in plain text.
   * @return the task separator in plain text.
   */
  public String getTaskSeparator()
  {
    return
    "\n\n-----------------------------------------------------------------\n\n";
  }

  /**
   * {@inheritDoc}
   */
  public String getFormattedAfterUrlClick(String url, String lastText)
  {
    throw new IllegalStateException(
        "PlainTextProgressMessageFormatter.getFormattedAfterUrlClick must not "+
        "be called");
  }

  /**
   * Returns a localized message for a key value.  In  the properties file we
   * have something of type:
   * key=value
   *
   * @see ResourceProvider.getMsg(String key)
   * @param key the key in the properties file.
   * @return the value associated to the key in the properties file.
   * properties file.
   */
  private String getMsg(String key)
  {
    return getI18n().getMsg(key);
  }

  /**
   * Returns a ResourceProvider instance.
   * @return a ResourceProvider instance.
   */
  private ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
  }
}
