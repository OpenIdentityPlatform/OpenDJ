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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

import org.opends.quicksetup.event.ProgressUpdateEvent;
import org.opends.quicksetup.event.ProgressUpdateListener;
import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.util.ProgressMessageFormatter;
import org.opends.quicksetup.util.Utils;
import org.opends.server.util.SetupUtils;

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

  private ProgressMessageFormatter formatter;

  /**
   * Constructor to be used by the subclasses.
   * @param userData the user data definining the parameters of the
   * installation.
   * @param formatter the message formatter to be used to generate the text of
   * the ProgressUpdateEvent
   */
  protected Installer(UserInstallData userData,
      ProgressMessageFormatter formatter)
  {
    this.userData = userData;
    this.formatter = formatter;
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
   * Returns the formatted representation of the text that is the summary of the
   * installation process (the one that goes in the UI next to the progress
   * bar).
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of an error for the given text.
   */
  protected String getFormattedSummary(String text)
  {
    return formatter.getFormattedSummary(text);
  }

  /**
   * Returns the formatted representation of an error for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of an error for the given text.
   */
  protected String getFormattedError(String text)
  {
    return formatter.getFormattedError(text);
  }

  /**
   * Returns the formatted representation of an warning for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of an warning for the given text.
   */
  public String getFormattedWarning(String text)
  {
    return formatter.getFormattedWarning(text);
  }

  /**
   * Returns the formatted representation of a success message for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of an success message for the given
   * text.
   */
  public String getFormattedSuccess(String text)
  {
    return formatter.getFormattedSuccess(text);
  }

  /**
   * Returns the formatted representation of a log error message for a given
   * text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a log error message for the given
   * text.
   */
  protected String getFormattedLogError(String text)
  {
    return formatter.getFormattedLogError(text);
  }

  /**
   * Returns the HTML representation of a log message for a given text.
   * @param text the source text from which we want to get the HTML
   * representation
   * @return the HTML representation of a log message for the given text.
   */
  protected String getFormattedLog(String text)
  {
    return formatter.getFormattedLog(text);
  }

  /**
   * Returns the formatted representation of the 'Done' text string.
   * @return the formatted representation of the 'Done' text string.
   */
  protected String getFormattedDone()
  {
    return formatter.getFormattedDone();
  }

  /**
   * Returns the formatted representation of the argument text to which we add
   * points.  For instance if we pass as argument 'Configuring Server' the
   * return value will be 'Configuring Server .....'.
   * @param text the String to which add points.
   * @return the formatted representation of the '.....' text string.
   */
  protected String getFormattedWithPoints(String text)
  {
    return formatter.getFormattedWithPoints(text);
  }

  /**
   * Returns the HTML representation of a progress message for a given text.
   * @param text the source text from which we want to get the HTML
   * representation
   * @return the HTML representation of a progress message for the given text.
   */
  protected String getFormattedProgress(String text)
  {
    return formatter.getFormattedProgress(text);
  }

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
  protected String getFormattedError(Exception ex, boolean applyMargin)
  {
    return formatter.getFormattedError(ex, applyMargin);
  }

  /**
   * Returns the line break formatted.
   * @return the line break formatted.
   */
  protected String getLineBreak()
  {
    return formatter.getLineBreak();
  }

  /**
   * Returns the task separator formatted.
   * @return the task separator formatted.
   */
  protected String getTaskSeparator()
  {
    return formatter.getTaskSeparator();
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
      return SetupUtils.createTemplateFile(
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
                buf.append(formatter.getLineBreak());
              }
              if (isError)
              {
                buf.append(getFormattedLogError(line));
              } else
              {
                buf.append(getFormattedLog(line));
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
        notifyListeners(getFormattedLogError(msg));
      } else
      {
        notifyListeners(formatter.getLineBreak() + getFormattedLogError(msg));
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
        notifyListeners(getFormattedLog(msg));
      } else
      {
        notifyListeners(formatter.getLineBreak() + getFormattedLog(msg));
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
    notifyListeners(getFormattedWithPoints(getMsg("progress-configuring")));

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
    notifyListeners(getFormattedWithPoints(
        getMsg("progress-creating-base-entry", arg)));

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

    notifyListeners(getFormattedDone());
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
    notifyListeners(getFormattedProgress(getMsg("progress-importing-ldif", arg))
        + formatter.getLineBreak());

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
    notifyListeners(getFormattedProgress(getMsg(
        "progress-import-automatically-generated", arg))
        + formatter.getLineBreak());

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
    notifyListeners(getFormattedProgress(getMsg("progress-starting")) +
        getLineBreak());

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
        getFormattedSummary(getMsg("summary-not-started")));
    hmSummary.put(InstallProgressStep.DOWNLOADING,
        getFormattedSummary(getMsg("summary-downloading")));
    hmSummary.put(InstallProgressStep.EXTRACTING,
        getFormattedSummary(getMsg("summary-extracting")));
    hmSummary.put(InstallProgressStep.CONFIGURING_SERVER,
        getFormattedSummary(getMsg("summary-configuring")));
    hmSummary.put(InstallProgressStep.CREATING_BASE_ENTRY,
        getFormattedSummary(getMsg("summary-creating-base-entry")));
    hmSummary.put(InstallProgressStep.IMPORTING_LDIF,
        getFormattedSummary(getMsg("summary-importing-ldif")));
    hmSummary.put(
        InstallProgressStep.IMPORTING_AUTOMATICALLY_GENERATED,
        getFormattedSummary(
            getMsg("summary-importing-automatically-generated")));
    hmSummary.put(InstallProgressStep.STARTING_SERVER,
        getFormattedSummary(getMsg("summary-starting")));
    hmSummary.put(InstallProgressStep.FINISHED_SUCCESSFULLY, "<html>"
        + getFormattedSuccess(getMsg("summary-finished-successfully")));
    hmSummary.put(InstallProgressStep.FINISHED_WITH_ERROR, "<html>"
        + getFormattedError(getMsg("summary-finished-with-error")));
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
