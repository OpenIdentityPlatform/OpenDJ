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

package org.opends.quicksetup.util;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;


import org.opends.quicksetup.Constants;

import static org.opends.messages.QuickSetupMessages.*;

/**
 * This is an implementation of the ProgressMessageFormatter class that
 * provides format in plain text.
 *
 */
public class PlainTextProgressMessageFormatter
implements ProgressMessageFormatter
{
  private Message doneText;
  private Message errorText;

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
  public Message getFormattedText(Message text)
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
  public Message getFormattedSummary(Message text)
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
  public Message getFormattedError(Message text, boolean applyMargin)
  {
    Message result;
    if (applyMargin)
    {
      result = new MessageBuilder().append(Constants.LINE_SEPARATOR)
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
  public Message getFormattedWarning(Message text, boolean applyMargin)
  {
    Message result;
    if (applyMargin)
    {
      result = new MessageBuilder(Constants.LINE_SEPARATOR)
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
  public Message getFormattedSuccess(Message text)
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
  public Message getFormattedLogError(Message text)
  {
    return text;
  }


  /**
   * Returns the plain text representation of a log message for a given text.
   * @param text the source text from which we want to get the plain text
   * representation
   * @return the plain text representation of a log message for the given text.
   */
  public Message getFormattedLog(Message text)
  {
    return text;
  }

  /**
   * Returns the plain text representation of the 'Done' text string.
   * @return the plain text representation of the 'Done' text string.
   */
  public Message getFormattedDone()
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
  public Message getFormattedError()
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
  public Message getFormattedWithPoints(Message text)
  {
    return new MessageBuilder(text).append(SPACE)
            .append(INFO_PROGRESS_POINTS.get()).toMessage();
  }

  /**
   * Returns the formatted representation of a progress message for a given
   * text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a progress message for the given
   * text.
   */
  public Message getFormattedProgress(Message text)
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
  public Message getFormattedError(Throwable t, boolean applyMargin)
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
    return Message.raw(result);
  }

  /**
   * Returns the line break in plain text.
   * @return the line break in plain text.
   */
  public Message getLineBreak()
  {
    return Message.raw(Constants.LINE_SEPARATOR);
  }

  /**
   * Returns the tab in plain text.
   * @return the tab in plain text.
   */
  public Message getTab()
  {
    return Message.raw("     ");
  }

  /**
   * Returns the task separator in plain text.
   * @return the task separator in plain text.
   */
  public Message getTaskSeparator()
  {
    return Message.raw(
    "\n\n-----------------------------------------------------------------\n\n"
            );
  }

  /**
   * {@inheritDoc}
   */
  public Message getFormattedAfterUrlClick(String url, Message lastText)
  {
    throw new IllegalStateException(
        "PlainTextProgressMessageFormatter.getFormattedAfterUrlClick must not "+
        "be called");
  }

}
