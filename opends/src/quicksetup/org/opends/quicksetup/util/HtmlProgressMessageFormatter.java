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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.Constants;

import static org.opends.messages.QuickSetupMessages.*;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

/**
 * This is an implementation of the ProgressMessageFormatter class that
 * provides format in HTML.
 *
 */
public class HtmlProgressMessageFormatter implements ProgressMessageFormatter
{
  static private final Logger LOG =
          Logger.getLogger(HtmlProgressMessageFormatter.class.getName());

  private Message doneHtml;
  private Message errorHtml;

  /**
   * The constant used to separate parameters in an URL.
   */
  private String PARAM_SEPARATOR = "&&&&";

  /**
   * The space in HTML.
   */
  private static Message SPACE = Message.raw("&nbsp;");

  /**
   * Returns the HTML representation of the text without providing any style.
   * @param text the source text from which we want to get the HTML
   * representation
   * @return the HTML representation for the given text.
   */
  public Message getFormattedText(Message text)
  {
    return Message.raw(getHtml(String.valueOf(text)));
  }

  /**
   * Returns the HTML representation of the text that is the summary of the
   * installation process (the one that goes in the UI next to the progress
   * bar).
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the HTML representation of the summary for the given text.
   */
  public Message getFormattedSummary(Message text)
  {
    return new MessageBuilder("<html>")
            .append(UIFactory.applyFontToHtml(
                    String.valueOf(text), UIFactory.PROGRESS_FONT))
            .toMessage();
  }

  /**
   * Returns the HTML representation of an error for a given text.
   * @param text the source text from which we want to get the HTML
   * representation
   * @param applyMargin specifies whether we apply a margin or not to the
   * resulting HTML.
   * @return the HTML representation of an error for the given text.
   */
  public Message getFormattedError(Message text, boolean applyMargin)
  {
    String html;
    if (!Utils.containsHtml(String.valueOf(text))) {
      html = UIFactory.getIconHtml(UIFactory.IconType.ERROR_LARGE)
          + SPACE
          + SPACE
          + UIFactory.applyFontToHtml(getHtml(String.valueOf(text)),
              UIFactory.PROGRESS_ERROR_FONT);
    } else {
      html =
          UIFactory.getIconHtml(UIFactory.IconType.ERROR_LARGE) + SPACE
          + SPACE + UIFactory.applyFontToHtml(
                  String.valueOf(text), UIFactory.PROGRESS_FONT);
    }

    String result = UIFactory.applyErrorBackgroundToHtml(html);
    if (applyMargin)
    {
      result =
          UIFactory.applyMargin(result,
              UIFactory.TOP_INSET_ERROR_MESSAGE, 0, 0, 0);
    }
    return Message.raw(result);
  }

  /**
   * Returns the HTML representation of a warning for a given text.
   * @param text the source text from which we want to get the HTML
   * representation
   * @param applyMargin specifies whether we apply a margin or not to the
   * resulting HTML.
   * @return the HTML representation of a warning for the given text.
   */
  public Message getFormattedWarning(Message text, boolean applyMargin)
  {
    String html;
    if (!Utils.containsHtml(String.valueOf(text))) {
      html =
        UIFactory.getIconHtml(UIFactory.IconType.WARNING_LARGE)
            + SPACE
            + SPACE
            + UIFactory.applyFontToHtml(getHtml(String.valueOf(text)),
                UIFactory.PROGRESS_WARNING_FONT);
    } else {
      html =
          UIFactory.getIconHtml(UIFactory.IconType.WARNING_LARGE) + SPACE
          + SPACE + UIFactory.applyFontToHtml(
                  String.valueOf(text), UIFactory.PROGRESS_FONT);
    }

    String result = UIFactory.applyWarningBackgroundToHtml(html);
    if (applyMargin)
    {
      result =
          UIFactory.applyMargin(result,
              UIFactory.TOP_INSET_ERROR_MESSAGE, 0, 0, 0);
    }
    return Message.raw(result);
  }

  /**
   * Returns the HTML representation of a success message for a given text.
   * @param text the source text from which we want to get the HTML
   * representation
   * @return the HTML representation of a success message for the given text.
   */
  public Message getFormattedSuccess(Message text)
  {
    // Note: the text we get already is in HTML form
    String html =
        UIFactory.getIconHtml(UIFactory.IconType.INFORMATION_LARGE) + SPACE
        + SPACE + UIFactory.applyFontToHtml(String.valueOf(text),
                UIFactory.PROGRESS_FONT);

    return Message.raw(UIFactory.applySuccessfulBackgroundToHtml(html));
  }

  /**
   * Returns the HTML representation of a log error message for a given
   * text.
   * @param text the source text from which we want to get the HTML
   * representation
   * @return the HTML representation of a log error message for the given
   * text.
   */
  public Message getFormattedLogError(Message text)
  {
    String html = getHtml(String.valueOf(text));
    return Message.raw(UIFactory.applyFontToHtml(html,
        UIFactory.PROGRESS_LOG_ERROR_FONT));
  }


  /**
   * Returns the HTML representation of a log message for a given text.
   * @param text the source text from which we want to get the HTML
   * representation
   * @return the HTML representation of a log message for the given text.
   */
  public Message getFormattedLog(Message text)
  {
    String html = getHtml(String.valueOf(text));
    return Message.raw(UIFactory.applyFontToHtml(html,
            UIFactory.PROGRESS_LOG_FONT));
  }

  /**
   * Returns the HTML representation of the 'Done' text string.
   * @return the HTML representation of the 'Done' text string.
   */
  public Message getFormattedDone()
  {
    if (doneHtml == null)
    {
      String html = getHtml(INFO_PROGRESS_DONE.get().toString());
      doneHtml = Message.raw(UIFactory.applyFontToHtml(html,
          UIFactory.PROGRESS_DONE_FONT));
    }
    return Message.raw(doneHtml);
  }

  /**
   * Returns the HTML representation of the 'Error' text string.
   * @return the HTML representation of the 'Error' text string.
   */
  public Message getFormattedError() {
    if (errorHtml == null)
    {
      String html = getHtml(INFO_PROGRESS_ERROR.get().toString());
      errorHtml = Message.raw(UIFactory.applyFontToHtml(html,
          UIFactory.PROGRESS_ERROR_FONT));
    }
    return Message.raw(errorHtml);
  }

  /**
   * Returns the HTML representation of the argument text to which we add
   * points.  For instance if we pass as argument 'Configuring Server' the
   * return value will be 'Configuring Server <B>.....</B>'.
   * @param text the String to which add points.
   * @return the HTML representation of the '.....' text string.
   */
  public Message getFormattedWithPoints(Message text)
  {
    String html = getHtml(String.valueOf(text));
    String points = SPACE +
            getHtml(INFO_PROGRESS_POINTS.get().toString()) + SPACE;

    MessageBuilder buf = new MessageBuilder();
    buf.append(UIFactory.applyFontToHtml(html, UIFactory.PROGRESS_FONT))
        .append(
            UIFactory.applyFontToHtml(points, UIFactory.PROGRESS_POINTS_FONT));

    return buf.toMessage();
  }

  /**
   * Returns the formatted representation of a point.
   * @return the formatted representation of the '.' text string.
   */
  public Message getFormattedPoint()
  {
    return Message.raw(UIFactory.applyFontToHtml(".",
        UIFactory.PROGRESS_POINTS_FONT));
  }

  /**
   * Returns the formatted representation of a space.
   * @return the formatted representation of the ' ' text string.
   */
  public Message getSpace()
  {
    return Message.raw(SPACE);
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
    return Message.raw(UIFactory.applyFontToHtml(getHtml(String.valueOf(text)),
        UIFactory.PROGRESS_FONT));
  }

  /**
   * Returns the HTML representation of an error message for a given throwable.
   * This method applies a margin if the applyMargin parameter is
   * <CODE>true</CODE>.
   * @param t the throwable.
   * @param applyMargin specifies whether we apply a margin or not to the
   * resulting HTML.
   * @return the HTML representation of an error message for the given
   * exception.
   */
  public Message getFormattedError(Throwable t, boolean applyMargin)
  {
    String openDiv = "<div style=\"margin-left:5px; margin-top:10px\">";
    String hideText =
        UIFactory.applyFontToHtml(INFO_HIDE_EXCEPTION_DETAILS.get().toString(),
            UIFactory.PROGRESS_FONT);
    String showText =
        UIFactory.applyFontToHtml(INFO_SHOW_EXCEPTION_DETAILS.get().toString(),
            UIFactory.PROGRESS_FONT);
    String closeDiv = "</div>";

    StringBuilder stackBuf = new StringBuilder();
    stackBuf.append(getHtmlStack(t));
    Throwable root = t.getCause();
    while (root != null)
    {
      stackBuf.append(getHtml(INFO_EXCEPTION_ROOT_CAUSE.get().toString()))
              .append(Constants.HTML_LINE_BREAK);
      stackBuf.append(getHtmlStack(root));
      root = root.getCause();
    }
    String stackText =
        UIFactory.applyFontToHtml(stackBuf.toString(), UIFactory.STACK_FONT);

    StringBuilder buf = new StringBuilder();

    String msg = t.getMessage();
    if (msg != null)
    {
      buf.append(UIFactory.applyFontToHtml(getHtml(t.getMessage()),
              UIFactory.PROGRESS_ERROR_FONT)).append(Constants.HTML_LINE_BREAK);
    } else
    {
      buf.append(t.toString()).append(Constants.HTML_LINE_BREAK);
    }
    buf.append(getErrorWithStackHtml(openDiv, hideText, showText, stackText,
        closeDiv, false));

    String html =
        UIFactory.getIconHtml(UIFactory.IconType.ERROR_LARGE) + SPACE + SPACE
            + buf.toString();

    String result;
    if (applyMargin)
    {
      result =
          UIFactory.applyMargin(UIFactory.applyErrorBackgroundToHtml(html),
              UIFactory.TOP_INSET_ERROR_MESSAGE, 0, 0, 0);
    } else
    {
      result = UIFactory.applyErrorBackgroundToHtml(html);
    }
    return Message.raw(result);
  }

  /**
   * Returns the line break in HTML.
   * @return the line break in HTML.
   */
  public Message getLineBreak()
  {
    return Message.raw(Constants.HTML_LINE_BREAK);
  }

  /**
   * Returns the tab in HTML.
   * @return the tab in HTML.
   */
  public Message getTab()
  {
    return new MessageBuilder(SPACE)
            .append(SPACE)
            .append(SPACE)
            .append(SPACE)
            .append(SPACE)
            .toMessage();
  }

  /**
   * Returns the task separator in HTML.
   * @return the task separator in HTML.
   */
  public Message getTaskSeparator()
  {
    return Message.raw(UIFactory.HTML_SEPARATOR);
  }

  /**
   * Returns the log HTML representation after the user has clicked on a url.
   *
   * @see HtmlProgressMessageFormatter#getErrorWithStackHtml
   * @param url that has been clicked
   * @param lastText the HTML representation of the log before clicking on the
   * url.
   * @return the log HTML representation after the user has clicked on a url.
   */
  public Message getFormattedAfterUrlClick(String url, Message lastText)
  {
    String urlText = getErrorWithStackHtml(url, false);
    String newUrlText = getErrorWithStackHtml(url, true);
    String lastTextStr = String.valueOf(lastText);

    int index = lastTextStr.indexOf(urlText);
    if (index == -1)
    {
      LOG.log(Level.FINE, "lastText: " + lastText +
              "does not contain: " + urlText);
    } else
    {
      lastTextStr =
          lastTextStr.substring(0, index) + newUrlText
              + lastTextStr.substring(index + urlText.length());
    }
    return Message.raw(lastTextStr);
  }

  /**
   * Returns the HTML representation for a given text. without adding any kind
   * of font or style elements.  Just escapes the problematic characters
   * (like '<') and transform the break lines into '\n' characters.
   *
   * @param text the source text from which we want to get the HTML
   * representation
   * @return the HTML representation for the given text.
   */
  private String getHtml(String text)
  {
    StringBuilder buffer = new StringBuilder();
    if (text != null) {
      text = text.replaceAll("\r\n", "\n");
      String[] lines = text.split("[\n\r\u0085\u2028\u2029]");
      for (int i = 0; i < lines.length; i++)
      {
        if (i != 0)
        {
          buffer.append(Constants.HTML_LINE_BREAK);
        }
        buffer.append(escape(lines[i]));
      }
    }
    return buffer.toString();
  }

  /**
   * Returns the HTML representation of a plain text string which is obtained
   * by converting some special characters (like '<') into its equivalent
   * escaped HTML representation.
   *
   * @param rawString the String from which we want to obtain the HTML
   * representation.
   * @return the HTML representation of the plain text string.
   */
  private String escape(String rawString)
  {
    StringBuilder buffer = new StringBuilder();
    for (int i = 0; i < rawString.length(); i++)
    {
      char c = rawString.charAt(i);
      switch (c)
      {
      case '<':
        buffer.append("&lt;");
        break;

      case '>':
        buffer.append("&gt;");
        break;

      case '&':
        buffer.append("&amp;");
        break;

      case '"':
        buffer.append("&quot;");
        break;

      default:
        buffer.append(c);
        break;
      }
    }

    return buffer.toString();
  }

  /**
   * Returns a HTML representation of the stack trace of a Throwable object.
   * @param ex the throwable object from which we want to obtain the stack
   * trace HTML representation.
   * @return a HTML representation of the stack trace of a Throwable object.
   */
  private String getHtmlStack(Throwable ex)
  {
    StringBuilder buf = new StringBuilder();
    buf.append(SPACE)
    .append(SPACE)
    .append(SPACE)
    .append(SPACE)
    .append(SPACE)
    .append(SPACE)
    .append(SPACE)
    .append(SPACE)
    .append(SPACE)
    .append(SPACE)
    .append(getHtml(ex.toString()))
    .append(Constants.HTML_LINE_BREAK);
    StackTraceElement[] stack = ex.getStackTrace();
    for (StackTraceElement aStack : stack) {
      buf.append(SPACE)
              .append(SPACE)
              .append(SPACE)
              .append(SPACE)
              .append(SPACE)
              .append(SPACE)
              .append(SPACE)
              .append(SPACE)
              .append(SPACE)
              .append(SPACE)
              .append(getHtml(aStack.toString()))
              .append(Constants.HTML_LINE_BREAK);
    }
    return buf.toString();
  }

  /**
   * Returns the HTML representation of an exception in the
   * progress log.<BR>
   * We can have something of type:<BR><BR>
   *
   * An error occurred.  java.io.IOException could not connect to server.<BR>
   * <A HREF="">Show Details</A>
   *
   * When the user clicks on 'Show Details' the whole stack will be displayed.
   *
   * An error occurred.  java.io.IOException could not connect to server.<BR>
   * <A HREF="">Hide Details</A><BR>
   * ... And here comes all the stack trace representation<BR>
   *
   *
   * As the object that listens to this hyperlink events is not here (it is
   * QuickSetupStepPanel) we must include all the information somewhere.  The
   * chosen solution is to include everything in the URL using parameters.
   * This everything consists of:
   * The open div tag for the text.
   * The text that we display when we do not display the exception.
   * The text that we display when we display the exception.
   * The stack trace text.
   * The closing div.
   * A boolean informing if we are hiding the exception or not (to know in the
   * next event what must be displayed).
   *
   * @param openDiv the open div tag for the text.
   * @param hideText the text that we display when we do not display the
   * exception.
   * @param showText the text that we display when we display the exception.
   * @param stackText the stack trace text.
   * @param closeDiv the closing div.
   * @param hide a boolean informing if we are hiding the exception or not.
   * @return the HTML representation of an error message with an stack trace.
   */
  private String getErrorWithStackHtml(String openDiv, String hideText,
      String showText, String stackText, String closeDiv, boolean hide)
  {
    StringBuilder buf = new StringBuilder();

    String params =
        getUrlParams(openDiv, hideText, showText, stackText, closeDiv, hide);
    try
    {
      String text = hide ? hideText : showText;
      buf.append(openDiv).append("<a href=\"http://")
              .append(URLEncoder.encode(params, "UTF-8"))
              .append("\">").append(text).append("</a>");
      if (hide)
      {
        buf.append(Constants.HTML_LINE_BREAK).append(stackText);
      }
      buf.append(closeDiv);

    } catch (UnsupportedEncodingException uee)
    {
      // Bug
      throw new IllegalStateException("UTF-8 is not supported ", uee);
    }

    return buf.toString();
  }

  /**
   * Gets the url parameters of the href we construct in getErrorWithStackHtml.
   * @see HtmlProgressMessageFormatter#getErrorWithStackHtml
   * @param openDiv the open div tag for the text.
   * @param hideText the text that we display when we do not display the
   * exception.
   * @param showText the text that we display when we display the exception.
   * @param stackText the stack trace text.
   * @param closeDiv the closing div.
   * @param hide a boolean informing if we are hiding the exception or not.
   * @return the url parameters of the href we construct in getHrefString.
   */
  private String getUrlParams(String openDiv, String hideText,
      String showText, String stackText, String closeDiv, boolean hide)
  {
    StringBuilder buf = new StringBuilder();
    buf.append(openDiv).append(PARAM_SEPARATOR);
    buf.append(hideText).append(PARAM_SEPARATOR);
    buf.append(showText).append(PARAM_SEPARATOR);
    buf.append(stackText).append(PARAM_SEPARATOR);
    buf.append(closeDiv).append(PARAM_SEPARATOR);
    buf.append(hide);
    return buf.toString();
  }

  /**
   * Returns the HTML representation of an exception in the
   * progress log for a given url.
   * @param url the url containing all the information required to retrieve
   * the HTML representation.
   * @param inverse indicates whether we want to 'inverse' the representation
   * or not.  For instance if the url specifies that the stack is being hidden
   * and this parameter is <CODE>true</CODE> the resulting HTML will display
   * the stack.
   * @return the HTML representation of an exception in the progress log for a
   * given url.
   */
  private String getErrorWithStackHtml(String url, boolean inverse)
  {
    String p = url.substring("http://".length());
    try
    {
      p = URLDecoder.decode(p, "UTF-8");
    } catch (UnsupportedEncodingException uee)
    {
      // Bug
      throw new IllegalStateException("UTF-8 is not supported ", uee);
    }
    String params[] = p.split(PARAM_SEPARATOR);
    int i = 0;
    String openDiv = params[i++];
    String hideText = params[i++];
    String showText = params[i++];
    String stackText = params[i++];
    String closeDiv = params[i++];
    boolean isHide = Boolean.valueOf(params[i]);

    if (isHide)
    {
      return getErrorWithStackHtml(openDiv, hideText, showText, stackText,
          closeDiv, !inverse);
    } else
    {
      return getErrorWithStackHtml(openDiv, hideText, showText, stackText,
          closeDiv, inverse);
    }
  }

}

