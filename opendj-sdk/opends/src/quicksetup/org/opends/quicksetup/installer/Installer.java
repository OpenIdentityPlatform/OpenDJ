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
package org.opends.quicksetup.installer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

import org.opends.quicksetup.event.ProgressUpdateEvent;
import org.opends.quicksetup.event.ProgressUpdateListener;
import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.util.Utils;
import org.opends.server.util.CreateTemplate;

/**
 * This is an abstract class that is in charge of actually performing the
 * installation.
 *
 * It just takes a UserInstallData object and based on that installs OpenDS.
 *
 * When there is an update during the installation it will notify the
 * ProgressUpdateListener objects that have been added to it.  The notification
 * will send a ProgressUpdateEvent.
 *
 * This class is supposed to be fully independent of the graphical layout.
 * However it is the most appropriate part of the code to generate well
 * formatted messages.  So it generates HTML messages in the
 * ProgressUpdateEvent and to do so uses the UIFactory method.
 *
 * TODO pass an object in the constructor that would generate the messages.
 * The problem of this approach is that the resulting interface of this object
 * may be quite complex and could impact the lisibility of the code.
 *
 */
public abstract class Installer
{
  /**
   * The path to the Configuration LDIF file.
   */
  protected static final String CONFIG_PATH_RELATIVE =
      "config" + File.separator + "config.ldif";

  /**
   * The relative path where all the binaries (scripts) are.
   */
  protected static final String BINARIES_PATH_RELATIVE = "bin";

  /**
   * The relative paths to the jar files required by the install.
   */
  protected static final String[] OPEN_DS_JAR_RELATIVE_PATHS =
    { "lib/OpenDS.jar", "lib/je.jar" };

  private HashSet<ProgressUpdateListener> listeners =
      new HashSet<ProgressUpdateListener>();

  private UserInstallData userData;

  private String doneHtml;

  /**
   * The line break in HTML.
   */
  protected static String LINE_BREAK = "<br>";

  /**
   * The space in HTML.
   */
  protected static String SPACE = "&nbsp;";

  /**
   * Constructor to be used by the subclasses.
   * @param userData the user data definining the parameters of the
   * installation.
   */
  protected Installer(UserInstallData userData)
  {
    this.userData = userData;
  }

  /**
   * Adds a ProgressUpdateListener that will be notified of updates in the
   * install progress.
   * @param l the ProgressUpdateListener to be added.
   */
  public void addProgressUpdateListener(ProgressUpdateListener l)
  {
    listeners.add(l);
  }

  /**
   * Removes a ProgressUpdateListener.
   * @param l the ProgressUpdateListener to be removed.
   */
  public void removeProgressUpdateListener(ProgressUpdateListener l)
  {
    listeners.remove(l);
  }

  /**
   * Returns whether the installer has finished or not.
   * @return <CODE>true</CODE> if the install is finished or <CODE>false
   * </CODE> if not.
   */
  public boolean isFinished()
  {
    return getStatus() == InstallProgressStep.FINISHED_SUCCESSFULLY
        || getStatus() == InstallProgressStep.FINISHED_WITH_ERROR;
  }

  /**
   * Start the installation process.  This method will not block the thread on
   * which is invoked.
   */
  public abstract void start();

  /**
   * An static String that contains the class name of ConfigFileHandler.
   */
  protected static final String CONFIG_CLASS_NAME =
      "org.opends.server.extensions.ConfigFileHandler";


  /**
   * Returns the UserInstallData object representing the parameters provided by
   * the user to do the installation.
   *
   * @return the UserInstallData object representing the parameters provided
   * by the user to do the installation.
   */
  protected UserInstallData getUserData()
  {
    return userData;
  }

  /**
   * This method notifies the ProgressUpdateListeners that there was an update
   * in the installation progress.
   * @param ratio the integer that specifies which percentage of
   * the whole installation has been completed.
   * @param currentPhaseSummary the localized summary message for the
   * current installation progress in HTML form.
   * @param newLogDetail the new log messages that we have for the
   * installation in HTML form.
   */
  protected void notifyListeners(Integer ratio, String currentPhaseSummary,
      String newLogDetail)
  {
    ProgressUpdateEvent ev =
        new ProgressUpdateEvent(getStatus(), ratio, currentPhaseSummary,
            newLogDetail);
    for (ProgressUpdateListener l : listeners)
    {
      l.progressUpdate(ev);
    }
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
  protected String getMsg(String key)
  {
    return getI18n().getMsg(key);
  }

  /**
   * Returns a localized message for a key value.  In  the properties file we
   * have something of type:
   * key=value
   *
   * For instance if we pass as key "mykey" and as arguments {"value1"} and
   * in the properties file we have:
   * mykey=value with argument {0}.
   *
   * This method will return "value with argument value1".
   * @see ResourceProvider.getMsg(String key, String[] args)
   * @param key the key in the properties file.
   * @param args the arguments to be passed to generate the resulting value.
   * @return the value associated to the key in the properties file.
   */
  protected String getMsg(String key, String[] args)
  {
    return getI18n().getMsg(key, args);
  }

  /**
   * Returns a ResourceProvider instance.
   * @return a ResourceProvider instance.
   */
  protected ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
  }

  /**
   * Returns a localized message for a given properties key an exception.
   * @param key the key of the message in the properties file.
   * @param ex the exception for which we want to get a message.
   * @return a localized message for a given properties key an exception.
   */
  protected String getExceptionMsg(String key, Exception ex)
  {
    return getExceptionMsg(key, null, ex);
  }

  /**
   * Returns a localized message for a given properties key an exception.
   * @param key the key of the message in the properties file.
   * @param args the arguments of the message in the properties file.
   * @param ex the exception for which we want to get a message.
   *
   * @return a localized message for a given properties key an exception.
   */
  protected String getExceptionMsg(String key, String[] args, Exception ex)
  {
    return Utils.getExceptionMsg(getI18n(), key, args, ex);
  }

  /**
   * Returns the HTML representation of an error for a given text.
   * @param text the source text from which we want to get the HTML
   * representation
   * @return the HTML representation of an error for the given text.
   */
  protected String getHtmlError(String text)
  {
    String html =
        UIFactory.getIconHtml(UIFactory.IconType.ERROR)
            + SPACE
            + SPACE
            + UIFactory.applyFontToHtml(getHtml(text),
                UIFactory.PROGRESS_ERROR_FONT);

    String result = UIFactory.applyErrorBackgroundToHtml(html);
    return result;
  }

  /**
   * Returns the HTML representation of an warning for a given text.
   * @param text the source text from which we want to get the HTML
   * representation
   * @return the HTML representation of an warning for the given text.
   */
  protected String getHtmlWarning(String text)
  {
    String html =
        UIFactory.getIconHtml(UIFactory.IconType.WARNING)
            + SPACE
            + SPACE
            + UIFactory.applyFontToHtml(getHtml(text),
                UIFactory.PROGRESS_WARNING_FONT);

    String result = UIFactory.applyWarningBackgroundToHtml(html);
    return result;
  }

  /**
   * Returns the HTML representation of a success message for a given text.
   * @param text the source text from which we want to get the HTML
   * representation
   * @return the HTML representation of an success message for the given text.
   */
  protected String getHtmlSuccess(String text)
  {
    // Note: the text we get already is in HTML form
    String html =
        UIFactory.getIconHtml(UIFactory.IconType.INFORMATION) + SPACE
        + SPACE + UIFactory.applyFontToHtml(text, UIFactory.PROGRESS_FONT);

    String result = UIFactory.applySuccessfulBackgroundToHtml(html);
    return result;
  }

  /**
   * Returns the HTML representation of a log error message for a given text.
   * @param text the source text from which we want to get the HTML
   * representation
   * @return the HTML representation of a log error message for the given text.
   */
  protected String getHtmlLogError(String text)
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
  protected String getHtmlLog(String text)
  {
    String html = getHtml(text);
    return UIFactory.applyFontToHtml(html, UIFactory.PROGRESS_LOG_FONT);
  }

  /**
   * Returns the HTML representation of the 'Done' text string.
   * @return the HTML representation of the 'Done' text string.
   */
  protected String getHtmlDone()
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
   * Returns the HTML representation of the argument text to which we add
   * points.  For instance if we pass as argument 'Configuring Server' the
   * return value will be 'Configuring Server <B>.....</B>'.
   * @param text the String to which add points.
   * @return the HTML representation of the '.....' text string.
   */
  protected String getHtmlWithPoints(String text)
  {
    String html = getHtml(text);
    String points = SPACE + getHtml(getMsg("progress-points")) + SPACE;

    StringBuffer buf = new StringBuffer();
    buf.append(UIFactory.applyFontToHtml(html, UIFactory.PROGRESS_FONT))
        .append(
            UIFactory.applyFontToHtml(points, UIFactory.PROGRESS_POINTS_FONT));

    return buf.toString();
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
  protected String getHtml(String text)
  {
    StringBuffer buffer = new StringBuffer();
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

    return buffer.toString();
  }

  /**
   * Returns the HTML representation of a progress message for a given text.
   * @param text the source text from which we want to get the HTML
   * representation
   * @return the HTML representation of a progress message for the given text.
   */
  protected String getHtmlProgress(String text)
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
  protected String getHtmlError(Exception ex, boolean applyMargin)
  {
    String openDiv = "<div style=\"margin-left:5px; margin-top:10px\">";
    String hideText =
        UIFactory.applyFontToHtml(getMsg("hide-exception-details"),
            UIFactory.PROGRESS_FONT);
    String showText =
        UIFactory.applyFontToHtml(getMsg("show-exception-details"),
            UIFactory.PROGRESS_FONT);
    String closeDiv = "</div>";

    StringBuffer stackBuf = new StringBuffer();
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

    StringBuffer buf = new StringBuffer();

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
        UIFactory.getIconHtml(UIFactory.IconType.ERROR) + SPACE + SPACE
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
   * Returns a HTML representation of the stack trace of a Throwable object.
   * @param ex the throwable object from which we want to obtain the stack
   * trace HTML representation.
   * @return a HTML representation of the stack trace of a Throwable object.
   */
  private String getHtmlStack(Throwable ex)
  {
    StringBuffer buf = new StringBuffer();
    StackTraceElement[] stack = ex.getStackTrace();
    for (int i = 0; i < stack.length; i++)
    {
      buf.append(SPACE + SPACE + SPACE + SPACE + SPACE + SPACE + SPACE +
          SPACE + SPACE + SPACE + getHtml(stack[i].toString()) + LINE_BREAK);
    }
    return buf.toString();
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
    int toto;
    StringBuffer buffer = new StringBuffer();
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
  private static String getErrorWithStackHtml(String openDiv, String hideText,
      String showText, String stackText, String closeDiv, boolean hide)
  {
    StringBuffer buf = new StringBuffer();

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

  private static String PARAM_SEPARATOR = "&&&&";

  /**
   * Gets the url parameters of the href we construct in getErrorWithStackHtml.
   * @see getErrorWithStackHtml
   * @param openDiv the open div tag for the text.
   * @param hideText the text that we display when we do not display the
   * exception.
   * @param showText the text that we display when we display the exception.
   * @param stackText the stack trace text.
   * @param closeDiv the closing div.
   * @param hide a boolean informing if we are hiding the exception or not.
   * @return the url parameters of the href we construct in getHrefString.
   */
  private static String getUrlParams(String openDiv, String hideText,
      String showText, String stackText, String closeDiv, boolean hide)
  {
    StringBuffer buf = new StringBuffer();
    buf.append(openDiv + PARAM_SEPARATOR);
    buf.append(hideText + PARAM_SEPARATOR);
    buf.append(showText + PARAM_SEPARATOR);
    buf.append(stackText + PARAM_SEPARATOR);
    buf.append(closeDiv + PARAM_SEPARATOR);
    buf.append(hide);
    return buf.toString();
  }

  /**
   * Returns the log HTML representation after the user has clicked on a url.
   *
   * @see getErrorWithStackHtml
   * @param url that has been clicked
   * @param lastText the HTML representation of the log before clicking on the
   * url.
   * @return the log HTML representation after the user has clicked on a url.
   */
  public static String getHtmlAfterUrlClick(String url, String lastText)
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
   * Returns the HTML representation of an exception in the
   * progress log for a given url.
   * @see getHrefString
   * @param url the url containing all the information required to retrieve
   * the HTML representation.
   * @param inverse indicates whether we want to 'inverse' the representation
   * or not.  For instance if the url specifies that the stack is being hidden
   * and this parameter is <CODE>true</CODE> the resulting HTML will display
   * the stack.
   * @return the HTML representation of an exception in the progress log for a
   * given url.
   */
  private static String getErrorWithStackHtml(String url, boolean inverse)
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

  /**
   * Creates a template file based in the contents of the UserData object.
   * This template file is used to generate automatically data.  To generate
   * the template file the code will basically take into account the value of
   * the base dn and the number of entries to be generated.
   *
   * @return the file object pointing to the create template file.
   * @throws InstallException if an error occurs.
   */
  protected File createTemplateFile() throws InstallException
  {
    try
    {
      return CreateTemplate.createTemplateFile(
                  getUserData().getDataOptions().getBaseDn(),
                  getUserData().getDataOptions().getNumberEntries());
    }
    catch (IOException ioe)
    {
      String failedMsg = getExceptionMsg("error-creating-temp-file", null, ioe);
      throw new InstallException(InstallException.Type.FILE_SYSTEM_ERROR,
          failedMsg, ioe);
    }
  }

  /**
   * This method is called when a new log message has been received.  It will
   * notify the ProgressUpdateListeners of this fact.
   * @param newLogDetail the new log detail.
   */
  protected void notifyListeners(String newLogDetail)
  {
    Integer ratio = getRatio(getStatus());
    String currentPhaseSummary = getSummary(getStatus());
    notifyListeners(ratio, currentPhaseSummary, newLogDetail);
  }

  /**
   * Returns the current InstallProgressStep of the installation process.
   * @return the current InstallProgressStep of the installation process.
   */
  protected abstract InstallProgressStep getStatus();

  /**
   * Returns an integer that specifies which percentage of the whole
   * installation has been completed.
   * @param step the InstallProgressStep for which we want to get the ratio.
   * @return an integer that specifies which percentage of the whole
   * installation has been completed.
   */
  protected abstract Integer getRatio(InstallProgressStep step);

  /**
   * Returns an HTML representation of the summary for the specified
   * InstallProgressStep.
   * @param step the InstallProgressStep for which we want to get the summary
   * @return an HTML representation of the summary for the specified
   * InstallProgressStep.
   */
  protected abstract String getSummary(InstallProgressStep step);

  /**
   * This class is used to read the standard error and standard output of the
   * Start process.
   *
   * When a new log message is found notifies the ProgressUpdateListeners of
   * it. If an error occurs it also notifies the listeners.
   *
   */
  abstract class StartReader
  {
    private InstallException ex;

    private boolean isFinished;

    private boolean isFirstLine;

    /**
     * The protected constructor.
     * @param reader the BufferedReader of the start process.
     * @param startedId the message ID that this class can use to know whether
     * the start is over or not.
     * @param isError a boolean indicating whether the BufferedReader
     * corresponds to the standard error or to the standard output.
     */
    protected StartReader(final BufferedReader reader, final String startedId,
        final boolean isError)
    {
      final String errorTag =
          isError ? "error-reading-erroroutput" : "error-reading-output";

      isFirstLine = true;

      Thread t = new Thread(new Runnable()
      {
        public void run()
        {
          try
          {
            String line = reader.readLine();
            while (line != null)
            {
              StringBuffer buf = new StringBuffer();
              if (!isFirstLine)
              {
                buf.append(LINE_BREAK);
              }
              if (isError)
              {
                buf.append(getHtmlLogError(line));
              } else
              {
                buf.append(getHtmlLog(line));
              }
              notifyListeners(buf.toString());
              isFirstLine = false;

              if (line.indexOf("id=" + startedId) != -1)
              {
                isFinished = true;
              }
              line = reader.readLine();
            }
          } catch (IOException ioe)
          {
            String errorMsg = getExceptionMsg(errorTag, ioe);
            ex =
                new InstallException(InstallException.Type.START_ERROR,
                    errorMsg, ioe);

          } catch (RuntimeException re)
          {
            String errorMsg = getExceptionMsg(errorTag, re);
            ex =
                new InstallException(InstallException.Type.START_ERROR,
                    errorMsg, re);
          }
          isFinished = true;
        }
      });
      t.start();
    }

    /**
     * Returns the InstallException that occurred reading the Start error and
     * output or <CODE>null</CODE> if no exception occurred.
     * @return the exception that occurred reading or <CODE>null</CODE> if
     * no exception occurred.
     */
    public InstallException getException()
    {
      return ex;
    }

    /**
     * Returns <CODE>true</CODE> if the server starting process finished
     * (successfully or not) and <CODE>false</CODE> otherwise.
     * @return <CODE>true</CODE> if the server starting process finished
     * (successfully or not) and <CODE>false</CODE> otherwise.
     */
    public boolean isFinished()
    {
      return isFinished;
    }
  }

  /**
   * A subclass of the StartReader class used to read the standard error of the
   * server start.
   *
   */
  protected class StartErrorReader extends StartReader
  {
    /**
     * Constructor of the StartErrorReader.
     * @param reader the BufferedReader that reads the standard error of the
     * Start process.
     * @param startedId the Message ID that
     */
    public StartErrorReader(BufferedReader reader, String startedId)
    {
      super(reader, startedId, true);
    }
  }

  /**
   * A subclass of the StartReader class used to read the standard output of
   * the server start.
   *
   */
  protected class StartOutputReader extends StartReader
  {
    /**
     * Constructor of the StartOutputReader.
     * @param reader the BufferedReader that reads the standard output of the
     * Start process.
     * @param startedId the Message ID that
     */
    public StartOutputReader(BufferedReader reader, String startedId)
    {
      super(reader, startedId, false);
    }
  }

  /**
   * This class is used to notify the ProgressUpdateListeners of events that
   * are written to the standard error.  It is used in WebStartInstaller and in
   * OfflineInstaller.  These classes just create a ErrorPrintStream and then
   * they do a call to System.err with it.
   *
   * The class just reads what is written to the standard error, obtains an
   * HTML representation of it and then notifies the ProgressUpdateListeners
   * with the HTML messages.
   *
   */
  protected class ErrorPrintStream extends PrintStream
  {
    private boolean isFirstLine;

    /**
     * Default constructor.
     *
     */
    public ErrorPrintStream()
    {
      super(new ByteArrayOutputStream(), true);
      isFirstLine = true;
    }

    /**
     * {@inheritDoc}
     */
    public void println(String msg)
    {
      if (isFirstLine)
      {
        notifyListeners(getHtmlLogError(msg));
      } else
      {
        notifyListeners(LINE_BREAK + getHtmlLogError(msg));
      }
      isFirstLine = false;
    }

    /**
     * {@inheritDoc}
     */
    public void write(byte[] b, int off, int len)
    {
      if (b == null)
      {
        throw new NullPointerException("b is null");
      }

      if (off + len > b.length)
      {
        throw new IndexOutOfBoundsException(
            "len + off are bigger than the length of the byte array");
      }
      println(new String(b, off, len));
    }
  }

  /**
   * This class is used to notify the ProgressUpdateListeners of events that are
   * written to the standard output. It is used in WebStartInstaller and in
   * OfflineInstaller. These classes just create a OutputPrintStream and then
   * they do a call to System.err with it.
   *
   * The class just reads what is written to the standard output, obtains an
   * HTML representation of it and then notifies the ProgressUpdateListeners
   * with the HTML messages.
   *
   */
  protected class OutputPrintStream extends PrintStream
  {
    private boolean isFirstLine;

    /**
     * Default constructor.
     *
     */
    public OutputPrintStream()
    {
      super(new ByteArrayOutputStream(), true);
      isFirstLine = true;
    }

    /**
     * {@inheritDoc}
     */
    public void println(String msg)
    {
      if (isFirstLine)
      {
        notifyListeners(getHtmlLog(msg));
      } else
      {
        notifyListeners(LINE_BREAK + getHtmlLog(msg));
      }
      isFirstLine = false;
    }

    /**
     * {@inheritDoc}
     */
    public void write(byte[] b, int off, int len)
    {
      if (b == null)
      {
        throw new NullPointerException("b is null");
      }

      if (off + len > b.length)
      {
        throw new IndexOutOfBoundsException(
            "len + off are bigger than the length of the byte array");
      }

      println(new String(b, off, len));
    }
  }

  /**
   * This methods configures the server based on the contents of the UserData
   * object provided in the constructor.
   * @throws InstallException if something goes wrong.
   */
  protected void configureServer() throws InstallException
  {
    notifyListeners(getHtmlWithPoints(getMsg("progress-configuring")));

    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-C");
    argList.add(CONFIG_CLASS_NAME);

    argList.add("-c");
    argList.add(getConfigFilePath());
    argList.add("-p");
    argList.add(String.valueOf(getUserData().getServerPort()));

    argList.add("-D");
    argList.add(getUserData().getDirectoryManagerDn());

    argList.add("-w");
    argList.add(getUserData().getDirectoryManagerPwd());

    argList.add("-b");
    argList.add(getUserData().getDataOptions().getBaseDn());

    String[] args = new String[argList.size()];
    argList.toArray(args);
    try
    {
      InstallerHelper helper = new InstallerHelper();
      int result = helper.invokeConfigureServer(args);

      if (result != 0)
      {
        throw new InstallException(
            InstallException.Type.CONFIGURATION_ERROR,
            getMsg("error-configuring"), null);
      }
    } catch (RuntimeException re)
    {
      throw new InstallException(
          InstallException.Type.CONFIGURATION_ERROR,
          getExceptionMsg("error-configuring", null, re), re);
    }
  }

  /**
   * This methods creates the base entry for the suffix based on the contents of
   * the UserData object provided in the constructor.
   * @throws InstallException if something goes wrong.
   */
  protected void createBaseEntry() throws InstallException
  {
    String[] arg =
      { getUserData().getDataOptions().getBaseDn() };
    notifyListeners(getHtmlWithPoints(getMsg("progress-creating-base-entry",
        arg)));

    InstallerHelper helper = new InstallerHelper();
    String baseDn = getUserData().getDataOptions().getBaseDn();
    File tempFile = helper.createBaseEntryTempFile(baseDn);

    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-C");
    argList.add(CONFIG_CLASS_NAME);

    argList.add("-f");
    argList.add(getConfigFilePath());

    argList.add("-n");
    argList.add(getBackendName());

    argList.add("-l");
    argList.add(tempFile.getAbsolutePath());

    argList.add("-q");

    String[] args = new String[argList.size()];
    argList.toArray(args);

    try
    {
      int result = helper.invokeImportLDIF(args);

      if (result != 0)
      {
        throw new InstallException(
            InstallException.Type.CONFIGURATION_ERROR,
            getMsg("error-creating-base-entry"), null);
      }
    } catch (RuntimeException re)
    {
      throw new InstallException(
          InstallException.Type.CONFIGURATION_ERROR,
          getExceptionMsg("error-creating-base-entry", null, re), re);
    }

    notifyListeners(getHtmlDone());
  }

  /**
   * This methods imports the contents of an LDIF file based on the contents of
   * the UserData object provided in the constructor.
   * @throws InstallException if something goes wrong.
   */
  protected void importLDIF() throws InstallException
  {
    String[] arg =
      { getUserData().getDataOptions().getLDIFPath() };
    notifyListeners(getHtmlProgress(getMsg("progress-importing-ldif", arg))
        + LINE_BREAK);

    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-C");
    argList.add(CONFIG_CLASS_NAME);

    argList.add("-f");
    argList.add(getConfigFilePath());
    argList.add("-n");
    argList.add(getBackendName());
    argList.add("-l");
    argList.add(getUserData().getDataOptions().getLDIFPath());

    String[] args = new String[argList.size()];
    argList.toArray(args);

    try
    {
      InstallerHelper helper = new InstallerHelper();
      int result = helper.invokeImportLDIF(args);

      if (result != 0)
      {
        throw new InstallException(
            InstallException.Type.CONFIGURATION_ERROR,
            getMsg("error-importing-ldif"), null);
      }
    } catch (RuntimeException re)
    {
      throw new InstallException(
          InstallException.Type.CONFIGURATION_ERROR,
          getExceptionMsg("error-importing-ldif", null, re), re);
    }
  }

  /**
   * This methods imports automatically generated data based on the contents
   * of the UserData object provided in the constructor.
   * @throws InstallException if something goes wrong.
   */
  protected void importAutomaticallyGenerated() throws InstallException
  {
    File templatePath = createTemplateFile();
    int nEntries = getUserData().getDataOptions().getNumberEntries();
    String[] arg =
      { String.valueOf(nEntries) };
    notifyListeners(getHtmlProgress(getMsg(
        "progress-import-automatically-generated", arg))
        + LINE_BREAK);

    ArrayList<String> argList = new ArrayList<String>();
    argList.add("-C");
    argList.add(CONFIG_CLASS_NAME);

    argList.add("-f");
    argList.add(getConfigFilePath());
    argList.add("-n");
    argList.add(getBackendName());
    argList.add("-t");
    argList.add(templatePath.getAbsolutePath());
    argList.add("-S");
    argList.add("0");

    String[] args = new String[argList.size()];
    argList.toArray(args);

    try
    {
      InstallerHelper helper = new InstallerHelper();
      int result = helper.invokeImportLDIF(args);

      if (result != 0)
      {
        throw new InstallException(
            InstallException.Type.CONFIGURATION_ERROR,
            getMsg("error-import-automatically-generated"), null);
      }
    } catch (RuntimeException re)
    {
      throw new InstallException(
          InstallException.Type.CONFIGURATION_ERROR,
          getExceptionMsg("error-import-automatically-generated", null, re),
          re);
    }
  }

  /**
   * This methods starts the server.
   * @throws InstallException if something goes wrong.
   */
  protected void startServer() throws InstallException
  {
    notifyListeners(getHtmlProgress(getMsg("progress-starting")) + LINE_BREAK);

    ArrayList<String> argList = new ArrayList<String>();

    if (Utils.isWindows())
    {
      argList.add(Utils.getPath(getBinariesPath(), "start-ds.bat"));
    } else
    {
      argList.add(Utils.getPath(getBinariesPath(), "start-ds"));
    }
    String[] env =
      { "JAVA_HOME=" + System.getProperty("java.home") };

    try
    {
      String startedId = getStartedId();

      String[] args = new String[argList.size()];
      argList.toArray(args);
      // Process process = Runtime.getRuntime().exec(args);
      Process process = Runtime.getRuntime().exec(args, env);

      BufferedReader err =
          new BufferedReader(new InputStreamReader(process.getErrorStream()));
      BufferedReader out =
          new BufferedReader(new InputStreamReader(process.getInputStream()));

      StartErrorReader errReader = new StartErrorReader(err, startedId);
      StartOutputReader outputReader = new StartOutputReader(out, startedId);

      while (!errReader.isFinished() && !outputReader.isFinished())
      {
        try
        {
          Thread.sleep(100);
        } catch (InterruptedException ie)
        {
        }
      }
      // Check if something wrong occurred reading the starting of the server
      InstallException ex = errReader.getException();
      if (ex == null)
      {
        ex = outputReader.getException();
      }
      if (ex != null)
      {
        throw ex;

      } else
      {
        /*
         * There are no exceptions from the readers and they are marked as
         * finished. This means that the server has written in its output the
         * message id informing that it started. So it seems that everything
         * went fine.
         */
      }

    } catch (IOException ioe)
    {
      throw new InstallException(InstallException.Type.START_ERROR,
          getExceptionMsg("error-starting-server", ioe), ioe);
    }
  }

  /**
   * Returns the class path (using the class path separator which is platform
   * dependent) required to run Open DS server.
   * @return the class path required to run Open DS server.
   */
  protected abstract String getOpenDSClassPath();

  /**
   * Returns the config file path.
   * @return the config file path.
   */
  protected abstract String getConfigFilePath();

  /**
   * Returns the path to the binaries.
   * @return the path to the binaries.
   */
  protected abstract String getBinariesPath();

  /**
   * Updates the contents of the provided map with the localized summary
   * strings.
   * @param hmSummary the Map to be updated.
   */
  protected void initSummaryMap(
      Map<InstallProgressStep, String> hmSummary)
  {
    hmSummary.put(InstallProgressStep.NOT_STARTED,
        UIFactory.applyFontToHtml(
        getMsg("summary-not-started"), UIFactory.PROGRESS_FONT));
    hmSummary.put(InstallProgressStep.DOWNLOADING,
        UIFactory.applyFontToHtml(
        getMsg("summary-downloading"), UIFactory.PROGRESS_FONT));
    hmSummary.put(InstallProgressStep.EXTRACTING,
        UIFactory.applyFontToHtml(
        getMsg("summary-extracting"), UIFactory.PROGRESS_FONT));
    hmSummary.put(InstallProgressStep.CONFIGURING_SERVER,
        UIFactory.applyFontToHtml(getMsg("summary-configuring"),
            UIFactory.PROGRESS_FONT));
    hmSummary.put(InstallProgressStep.CREATING_BASE_ENTRY, UIFactory
        .applyFontToHtml(getMsg("summary-creating-base-entry"),
            UIFactory.PROGRESS_FONT));
    hmSummary.put(InstallProgressStep.IMPORTING_LDIF, UIFactory
        .applyFontToHtml(getMsg("summary-importing-ldif"),
            UIFactory.PROGRESS_FONT));
    hmSummary.put(
        InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED,
        UIFactory.applyFontToHtml(
            getMsg("summary-importing-automatically-generated"),
            UIFactory.PROGRESS_FONT));
    hmSummary.put(InstallProgressStep.STARTING_SERVER, UIFactory
        .applyFontToHtml(getMsg("summary-starting"),
            UIFactory.PROGRESS_FONT));
    hmSummary.put(InstallProgressStep.FINISHED_SUCCESSFULLY, "<html>"
        + getHtmlSuccess(getMsg("summary-finished-successfully")));
    hmSummary.put(InstallProgressStep.FINISHED_WITH_ERROR, "<html>"
        + getHtmlError(getMsg("summary-finished-with-error")));
  }

  /**
   * Returns the Message ID indicating that the server has started.
   * @return the Message ID indicating that the server has started.
   */
  private String getStartedId()
  {
    InstallerHelper helper = new InstallerHelper();
    return helper.getStartedId();
  }

  /**
   * Returns the default backend name (the one that will be created).
   * @return the default backend name (the one that will be created).
   */
  protected String getBackendName()
  {
    return "userRoot";
  }
}
