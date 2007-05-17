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

import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.ui.UIFactory;

/**
 * This is an implementation of the ProgressMessageFormatter class that
 * provides format in HTML.
 *
 */
public class HtmlProgressMessageFormatter implements ProgressMessageFormatter
{
  private String doneHtml;
  private String errorHtml;

  /**
   * The line break in HTML.
   */
  private static String LINE_BREAK = "<br>";

  /**
   * The constant used to separate parameters in an URL.
   */
  private String PARAM_SEPARATOR = "&&&&";

  /**
   * The space in HTML.
   */
  private static String SPACE = "&nbsp;";

  /**
   * Returns the HTML representation of the text without providing any style.
   * @param text the source text from which we want to get the HTML
   * representation
   * @return the HTML representation for the given text.
   */
  public String getFormattedText(String text)
  {
    return getHtml(text);
  }

  /**
   * Returns the HTML representation of the text that is the summary of the
   * installation process (the one that goes in the UI next to the progress
   * bar).
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the HTML representation of the summary for the given text.
   */
  public String getFormattedSummary(String text)
  {
    return "<html>"+UIFactory.applyFontToHtml(text, UIFactory.PROGRESS_FONT);
  }

  /**
   * Returns the HTML representation of an error for a given text.
   * @param text the source text from which we want to get the HTML
   * representation
   * @param applyMargin specifies whether we apply a margin or not to the
   * resulting HTML.
   * @return the HTML representation of an error for the given text.
   */
  public String getFormattedError(String text, boolean applyMargin)
  {
    String html;
    if (!containsHtml(text)) {
      html = UIFactory.getIconHtml(UIFactory.IconType.ERROR_LARGE)
          + SPACE
          + SPACE
          + UIFactory.applyFontToHtml(getHtml(text),
              UIFactory.PROGRESS_ERROR_FONT);
    } else {
      html =
          UIFactory.getIconHtml(UIFactory.IconType.ERROR_LARGE) + SPACE
          + SPACE + UIFactory.applyFontToHtml(text, UIFactory.PROGRESS_FONT);
    }

    String result = UIFactory.applyErrorBackgroundToHtml(html);
    if (applyMargin)
    {
      result =
          UIFactory.applyMargin(result,
              UIFactory.TOP_INSET_ERROR_MESSAGE, 0, 0, 0);
    }
    return result;
  }

  /**
   * Returns the HTML representation of a warning for a given text.
   * @param text the source text from which we want to get the HTML
   * representation
   * @param applyMargin specifies whether we apply a margin or not to the
   * resulting HTML.
   * @return the HTML representation of a warning for the given text.
   */
  public String getFormattedWarning(String text, boolean applyMargin)
  {
    String html;
    if (!containsHtml(text)) {
      html =
        UIFactory.getIconHtml(UIFactory.IconType.WARNING_LARGE)
            + SPACE
            + SPACE
            + UIFactory.applyFontToHtml(getHtml(text),
                UIFactory.PROGRESS_WARNING_FONT);
    } else {
      html =
          UIFactory.getIconHtml(UIFactory.IconType.WARNING_LARGE) + SPACE
          + SPACE + UIFactory.applyFontToHtml(text, UIFactory.PROGRESS_FONT);
    }

    String result = UIFactory.applyWarningBackgroundToHtml(html);
    if (applyMargin)
    {
      result =
          UIFactory.applyMargin(result,
              UIFactory.TOP_INSET_ERROR_MESSAGE, 0, 0, 0);
    }
    return result;
  }

  /**
   * Returns the HTML representation of a success message for a given text.
   * @param text the source text from which we want to get the HTML
   * representation
   * @return the HTML representation of a success message for the given text.
   */
  public String getFormattedSuccess(String text)
  {
    // Note: the text we get already is in HTML form
    String html =
        UIFactory.getIconHtml(UIFactory.IconType.INFORMATION_LARGE) + SPACE
        + SPACE + UIFactory.applyFontToHtml(text, UIFactory.PROGRESS_FONT);

    String result = UIFactory.applySuccessfulBackgroundToHtml(html);
    return result;
  }

  /**
   * Returns the HTML representation of a log error message for a given
   * text.
   * @param text the source text from which we want to get the HTML
   * representation
   * @return the HTML representation of a log error message for the given
   * text.
   */
  public String getFormattedLogError(String text)
  {
    String html = getHtml(text);
    return UIFactory.applyFontToHtml(html,
        UIFactory.PROGRESS_LOG_ERROR_FONT);
  }


  /**
   * Returns the HTML representation of a log message for a given text.
   * @param text the source text from which we want to get the HTML
   * representation
   * @return the HTML representation of a log message for the given text.
   */
  public String getFormattedLog(String text)
  {
    String html = getHtml(text);
    return UIFactory.applyFontToHtml(html, UIFactory.PROGRESS_LOG_FONT);
  }

  /**
   * Returns the HTML representation of the 'Done' text string.
   * @return the HTML representation of the 'Done' text string.
   */
  public String getFormattedDone()
  {
    if (doneHtml == null)
    {
      String html = getHtml(getMsg("progress-done"));
      doneHtml = UIFactory.applyFontToHtml(html,
          UIFactory.PROGRESS_DONE_FONT);
    }
    return doneHtml;
  }

  /**
   * Returns the HTML representation of the 'Error' text string.
   * @return the HTML representation of the 'Error' text string.
   */
  public String getFormattedError() {
    if (errorHtml == null)
    {
      String html = getHtml(getMsg("progress-error"));
      errorHtml = UIFactory.applyFontToHtml(html,
          UIFactory.PROGRESS_ERROR_FONT);
    }
    return errorHtml;
  }

  /**
   * Returns the HTML representation of the argument text to which we add
   * points.  For instance if we pass as argument 'Configuring Server' the
   * return value will be 'Configuring Server <B>.....</B>'.
   * @param text the String to which add points.
   * @return the HTML representation of the '.....' text string.
   */
  public String getFormattedWithPoints(String text)
  {
    String html = getHtml(text);
    String points = SPACE + getHtml(getMsg("progress-points")) + SPACE;

    StringBuilder buf = new StringBuilder();
    buf.append(UIFactory.applyFontToHtml(html, UIFactory.PROGRESS_FONT))
        .append(
            UIFactory.applyFontToHtml(points, UIFactory.PROGRESS_POINTS_FONT));

    return buf.toString();
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
    return UIFactory.applyFontToHtml(getHtml(text),
        UIFactory.PROGRESS_FONT);
  }

  /**
   * Returns the HTML representation of an error message for a given exception.
   * This method applies a margin if the applyMargin parameter is
   * <CODE>true</CODE>.
   * @param ex the exception.
   * @param applyMargin specifies whether we apply a margin or not to the
   * resulting HTML.
   * @return the HTML representation of an error message for the given
   * exception.
   */
  public String getFormattedError(Exception ex, boolean applyMargin)
  {
    String openDiv = "<div style=\"margin-left:5px; margin-top:10px\">";
    String hideText =
        UIFactory.applyFontToHtml(getMsg("hide-exception-details"),
            UIFactory.PROGRESS_FONT);
    String showText =
        UIFactory.applyFontToHtml(getMsg("show-exception-details"),
            UIFactory.PROGRESS_FONT);
    String closeDiv = "</div>";

    StringBuilder stackBuf = new StringBuilder();
    stackBuf.append(getHtmlStack(ex));
    Throwable root = ex.getCause();
    while (root != null)
    {
      stackBuf.append(getHtml(getMsg("exception-root-cause")) + LINE_BREAK);
      stackBuf.append(getHtmlStack(root));
      root = root.getCause();
    }
    String stackText =
        UIFactory.applyFontToHtml(stackBuf.toString(), UIFactory.STACK_FONT);

    StringBuilder buf = new StringBuilder();

    String msg = ex.getMessage();
    if (msg != null)
    {
      buf.append(UIFactory.applyFontToHtml(getHtml(ex.getMessage()),
          UIFactory.PROGRESS_ERROR_FONT)
          + LINE_BREAK);
    } else
    {
      buf.append(ex.toString() + LINE_BREAK);
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
    return result;
  }

  /**
   * Returns the line break in HTML.
   * @return the line break in HTML.
   */
  public String getLineBreak()
  {
    return LINE_BREAK;
  }

  /**
   * Returns the tab in HTML.
   * @return the tab in HTML.
   */
  public String getTab()
  {
    return SPACE+SPACE+SPACE+SPACE+SPACE;
  }

  /**
   * Returns the task separator in HTML.
   * @return the task separator in HTML.
   */
  public String getTaskSeparator()
  {
    return UIFactory.HTML_SEPARATOR;
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
  public String getFormattedAfterUrlClick(String url, String lastText)
  {
    String urlText = getErrorWithStackHtml(url, false);
    String newUrlText = getErrorWithStackHtml(url, true);

    int index = lastText.indexOf(urlText);
    if (index == -1)
    {
      System.out.println("lastText: " + lastText);
      System.out.println("does not contain: " + urlText);
    } else
    {
      lastText =
          lastText.substring(0, index) + newUrlText
              + lastText.substring(index + urlText.length());
    }
    return lastText;
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
          buffer.append("<br>");
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
   * Returns a localized message for a key value.  In  the properties file we
   * have something of type:
   * key=value
   *
   * @see ResourceProvider#getMsg(String)
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

  /**
   * Returns a HTML representation of the stack trace of a Throwable object.
   * @param ex the throwable object from which we want to obtain the stack
   * trace HTML representation.
   * @return a HTML representation of the stack trace of a Throwable object.
   */
  private String getHtmlStack(Throwable ex)
  {
    StringBuilder buf = new StringBuilder();
    StackTraceElement[] stack = ex.getStackTrace();
    for (int i = 0; i < stack.length; i++)
    {
      buf.append(SPACE + SPACE + SPACE + SPACE + SPACE + SPACE + SPACE +
          SPACE + SPACE + SPACE + getHtml(stack[i].toString()) + LINE_BREAK);
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
      buf.append(openDiv + "<a href=\"http://").append(
          URLEncoder.encode(params, "UTF-8") + "\">" + text + "</a>");
      if (hide)
      {
        buf.append(LINE_BREAK + stackText);
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
    buf.append(openDiv + PARAM_SEPARATOR);
    buf.append(hideText + PARAM_SEPARATOR);
    buf.append(showText + PARAM_SEPARATOR);
    buf.append(stackText + PARAM_SEPARATOR);
    buf.append(closeDiv + PARAM_SEPARATOR);
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
    boolean isHide = new Boolean(params[i]);

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

  private boolean containsHtml(String text) {
    return (text != null &&
            text.indexOf('<') != -1 &&
            text.indexOf('>') != -1);
  }
}

