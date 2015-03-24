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
import org.forgerock.i18n.LocalizableMessageBuilder;

import org.opends.quicksetup.Constants;

import static org.opends.messages.QuickSetupMessages.*;

/**
 * This is an implementation of the ProgressMessageFormatter class that
 * provides format in plain text.
 */
public class PlainTextProgressMessageFormatter
implements ProgressMessageFormatter
{
  private LocalizableMessage doneText;
  private LocalizableMessage errorText;

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
  public LocalizableMessage getFormattedText(LocalizableMessage text)
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
  public LocalizableMessage getFormattedSummary(LocalizableMessage text)
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
  public LocalizableMessage getFormattedError(LocalizableMessage text, boolean applyMargin)
  {
    LocalizableMessage result;
    if (applyMargin)
    {
      result = new LocalizableMessageBuilder().append(Constants.LINE_SEPARATOR)
              .append(text).toMessage();
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
  public LocalizableMessage getFormattedWarning(LocalizableMessage text, boolean applyMargin)
  {
    LocalizableMessage result;
    if (applyMargin)
    {
      result = new LocalizableMessageBuilder(Constants.LINE_SEPARATOR)
              .append(text).toMessage();
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
  public LocalizableMessage getFormattedSuccess(LocalizableMessage text)
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
  public LocalizableMessage getFormattedLogError(LocalizableMessage text)
  {
    return text;
  }


  /**
   * Returns the plain text representation of a log message for a given text.
   * @param text the source text from which we want to get the plain text
   * representation
   * @return the plain text representation of a log message for the given text.
   */
  public LocalizableMessage getFormattedLog(LocalizableMessage text)
  {
    return text;
  }

  /**
   * Returns the plain text representation of the 'Done' text string.
   * @return the plain text representation of the 'Done' text string.
   */
  public LocalizableMessage getFormattedDone()
  {
    if (doneText == null)
    {
      doneText = INFO_PROGRESS_DONE.get();
    }
    return doneText;
  }

  /**
   * Returns the plain text representation of the 'Error' text string.
   * @return the plain text representation of the 'Error' text string.
   */
  public LocalizableMessage getFormattedError()
  {
    if (errorText == null)
    {
      errorText = INFO_PROGRESS_ERROR.get();
    }
    return errorText;
  }

  /**
   * Returns the plain text representation of the argument text to which we add
   * points.  For instance if we pass as argument 'Configuring Server' the
   * return value will be 'Configuring Server .....'.
   * @param text the String to which add points.
   * @return the plain text representation of the '.....' text string.
   */
  public LocalizableMessage getFormattedWithPoints(LocalizableMessage text)
  {
    return new LocalizableMessageBuilder(text).append(SPACE)
            .append(INFO_PROGRESS_POINTS.get()).append(SPACE).toMessage();
  }

  /**
   * Returns the formatted representation of a point.
   * @return the formatted representation of the '.' text string.
   */
  public LocalizableMessage getFormattedPoint()
  {
    return LocalizableMessage.raw(".");
  }

  /**
   * Returns the formatted representation of a space.
   * @return the formatted representation of the ' ' text string.
   */
  public LocalizableMessage getSpace()
  {
    return LocalizableMessage.raw(SPACE);
  }

  /**
   * Returns the formatted representation of a progress message for a given
   * text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a progress message for the given
   * text.
   */
  public LocalizableMessage getFormattedProgress(LocalizableMessage text)
  {
    return text;
  }

  /**
   * Returns the plain text representation of an error message for a given
   * throwable.
   * This method applies a margin if the applyMargin parameter is
   * <CODE>true</CODE>.
   * @param t the exception.
   * @param applyMargin specifies whether we apply a margin or not to the
   * resulting plain text.
   * @return the plain text representation of an error message for the given
   * exception.
   */
  public LocalizableMessage getFormattedError(Throwable t, boolean applyMargin)
  {
    String msg = t.getMessage();
    if (msg == null)
    {
      msg = t.toString();
    }
    String result;
    if (applyMargin)
    {
      result = Constants.LINE_SEPARATOR+msg;
    } else
    {
      result = msg;
    }
    return LocalizableMessage.raw(result);
  }

  /**
   * Returns the line break in plain text.
   * @return the line break in plain text.
   */
  public LocalizableMessage getLineBreak()
  {
    return LocalizableMessage.raw(Constants.LINE_SEPARATOR);
  }

  /**
   * Returns the tab in plain text.
   * @return the tab in plain text.
   */
  public LocalizableMessage getTab()
  {
    return LocalizableMessage.raw("     ");
  }

  /**
   * Returns the task separator in plain text.
   * @return the task separator in plain text.
   */
  public LocalizableMessage getTaskSeparator()
  {
    return LocalizableMessage.raw(
        Constants.LINE_SEPARATOR+
        "-----------------------------------------------------------------"+
        Constants.LINE_SEPARATOR+Constants.LINE_SEPARATOR);
  }

  /** {@inheritDoc} */
  public LocalizableMessage getFormattedAfterUrlClick(String url, LocalizableMessage lastText)
  {
    throw new IllegalStateException(
        "PlainTextProgressMessageFormatter.getFormattedAfterUrlClick must not "+
        "be called");
  }

}
