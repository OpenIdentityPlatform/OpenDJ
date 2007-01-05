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

import javax.naming.NamingException;

import org.opends.quicksetup.event.InstallProgressUpdateEvent;
import org.opends.quicksetup.event.InstallProgressUpdateListener;
import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.ui.UIFactory;
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
 * InstallProgressUpdateListener objects that have been added to it.  The
 * notification will send a InstallProgressUpdateEvent.
 *
 * This class is supposed to be fully independent of the graphical layout.
 *
 * Note that we can use freely the class org.opends.server.util.SetupUtils as
 * it is included in quicksetup.jar.
 *
 */
public abstract class Installer
{
  /**
   * The path to the Configuration LDIF file.
   */
  protected static final String CONFIG_PATH_RELATIVE =
      "config" + File.separator + "config.ldif";

  private HashSet<InstallProgressUpdateListener> listeners =
      new HashSet<InstallProgressUpdateListener>();

  private UserInstallData userData;

  private ProgressMessageFormatter formatter;

  /**
   * Constructor to be used by the subclasses.
   * @param userData the user data definining the parameters of the
   * installation.
   * @param formatter the message formatter to be used to generate the text of
   * the InstallProgressUpdateEvent
   */
  protected Installer(UserInstallData userData,
      ProgressMessageFormatter formatter)
  {
    this.userData = userData;
    this.formatter = formatter;
  }

  /**
   * Adds a InstallProgressUpdateListener that will be notified of updates in
   * the install progress.
   * @param l the InstallProgressUpdateListener to be added.
   */
  public void addProgressUpdateListener(InstallProgressUpdateListener l)
  {
    listeners.add(l);
  }

  /**
   * Removes a InstallProgressUpdateListener.
   * @param l the InstallProgressUpdateListener to be removed.
   */
  public void removeProgressUpdateListener(InstallProgressUpdateListener l)
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
   * This method notifies the InstallProgressUpdateListeners that there was an
   * update in the installation progress.
   * @param ratio the integer that specifies which percentage of
   * the whole installation has been completed.
   * @param currentPhaseSummary the localized summary message for the
   * current installation progress in formatted form.
   * @param newLogDetail the new log messages that we have for the
   * installation in formatted form.
   */
  protected void notifyListeners(Integer ratio, String currentPhaseSummary,
      String newLogDetail)
  {
    InstallProgressUpdateEvent ev =
        new InstallProgressUpdateEvent(getStatus(), ratio, currentPhaseSummary,
            newLogDetail);
    for (InstallProgressUpdateListener l : listeners)
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
   * Returns a localized message for a given properties key and throwable.
   * @param key the key of the message in the properties file.
   * @param t the throwable for which we want to get a message.
   * @return a localized message for a given properties key and throwable.
   */
  protected String getThrowableMsg(String key, Throwable t)
  {
    return getThrowableMsg(key, null, t);
  }

  /**
   * Returns a localized message for a given properties key and throwable.
   * @param key the key of the message in the properties file.
   * @param args the arguments of the message in the properties file.
   * @param t the throwable for which we want to get a message.
   *
   * @return a localized message for a given properties key and throwable.
   */
  protected String getThrowableMsg(String key, String[] args, Throwable t)
  {
    return Utils.getThrowableMsg(getI18n(), key, args, t);
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
    return formatter.getFormattedError(text, false);
  }

  /**
   * Returns the formatted representation of an warning for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of an warning for the given text.
   */
  protected String getFormattedWarning(String text)
  {
    return formatter.getFormattedWarning(text, false);
  }

  /**
   * Returns the formatted representation of a success message for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of an success message for the given
   * text.
   */
  protected String getFormattedSuccess(String text)
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
   * Returns the formatted representation of a log message for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a log message for the given text.
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
   * Returns the formatted representation of a progress message for a given
   * text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a progress message for the given
   * text.
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
      String failedMsg = getThrowableMsg("error-creating-temp-file", null, ioe);
      throw new InstallException(InstallException.Type.FILE_SYSTEM_ERROR,
          failedMsg, ioe);
    }
  }

  /**
   * This method is called when a new log message has been received.  It will
   * notify the InstallProgressUpdateListeners of this fact.
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
   * Returns an formatted representation of the summary for the specified
   * InstallProgressStep.
   * @param step the InstallProgressStep for which we want to get the summary
   * @return an formatted representation of the summary for the specified
   * InstallProgressStep.
   */
  protected abstract String getSummary(InstallProgressStep step);

  /**
   * This class is used to read the standard error and standard output of the
   * Start process.
   *
   * When a new log message is found notifies the InstallProgressUpdateListeners
   * of it. If an error occurs it also notifies the listeners.
   *
   */
  private class StartReader
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
    public StartReader(final BufferedReader reader, final String startedId,
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
            String errorMsg = getThrowableMsg(errorTag, ioe);
            ex =
                new InstallException(InstallException.Type.START_ERROR,
                    errorMsg, ioe);

          } catch (Throwable t)
          {
            String errorMsg = getThrowableMsg(errorTag, t);
            ex =
                new InstallException(InstallException.Type.START_ERROR,
                    errorMsg, t);
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
   * This class is used to notify the InstallProgressUpdateListeners of events
   * that are written to the standard error.  It is used in WebStartInstaller
   * and in OfflineInstaller.  These classes just create a ErrorPrintStream and
   * then they do a call to System.err with it.
   *
   * The class just reads what is written to the standard error, obtains an
   * formatted representation of it and then notifies the
   * InstallProgressUpdateListeners with the formatted messages.
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
   * This class is used to notify the InstallProgressUpdateListeners of events
   * that are written to the standard output. It is used in WebStartInstaller
   * and in OfflineInstaller. These classes just create a OutputPrintStream and
   * then they do a call to System.out with it.
   *
   * The class just reads what is written to the standard output, obtains an
   * formatted representation of it and then notifies the
   * InstallProgressUpdateListeners with the formatted messages.
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
    } catch (Throwable t)
    {
      throw new InstallException(
          InstallException.Type.CONFIGURATION_ERROR,
          getThrowableMsg("error-configuring", null, t), t);
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
    } catch (Throwable t)
    {
      throw new InstallException(
          InstallException.Type.CONFIGURATION_ERROR,
          getThrowableMsg("error-creating-base-entry", null, t), t);
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
    } catch (Throwable t)
    {
      throw new InstallException(
          InstallException.Type.CONFIGURATION_ERROR,
          getThrowableMsg("error-importing-ldif", null, t), t);
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
    } catch (Throwable t)
    {
      throw new InstallException(
          InstallException.Type.CONFIGURATION_ERROR,
          getThrowableMsg("error-import-automatically-generated", null, t), t);
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

    String[] args = new String[argList.size()];
    argList.toArray(args);
    ProcessBuilder pb = new ProcessBuilder(args);
    Map<String, String> env = pb.environment();
    env.put("JAVA_HOME", System.getProperty("java.home"));
    /* Remove JAVA_BIN to be sure that we use the JVM running the installer
     * JVM to start the server.
     */
    env.remove("JAVA_BIN");

    try
    {
      String startedId = getStartedId();

      Process process = pb.start();

      BufferedReader err =
          new BufferedReader(new InputStreamReader(process.getErrorStream()));
      BufferedReader out =
          new BufferedReader(new InputStreamReader(process.getInputStream()));

      StartReader errReader = new StartReader(err, startedId, true);
      StartReader outputReader = new StartReader(out, startedId, false);

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
         *
         * However we can have issues with the firewalls or do not have rights
         * to connect.  Just check if we can connect to the server.
         * Try 5 times with an interval of 1 second between try.
         */
        boolean connected = false;
        for (int i=0; i<5 && !connected; i++)
        {
          String ldapUrl = "ldap://localhost:"+userData.getServerPort();
          try
          {
            Utils.createLdapContext(
                ldapUrl,
                userData.getDirectoryManagerDn(),
                userData.getDirectoryManagerPwd(), 3000, null);
            connected = true;
          }
          catch (NamingException ne)
          {
          }
          if (!connected)
          {
            try
            {
              Thread.sleep(1000);
            }
            catch (Throwable t)
            {
            }
          }
        }
        if (!connected)
        {
          if (Utils.isWindows())
          {
            String[] arg = {String.valueOf(userData.getServerPort())};
            throw new InstallException(InstallException.Type.START_ERROR,
                getMsg("error-starting-server-in-windows", arg), null);
          }
          else
          {
            String[] arg = {String.valueOf(userData.getServerPort())};
            throw new InstallException(InstallException.Type.START_ERROR,
                getMsg("error-starting-server-in-unix", arg), null);
          }
        }
      }

    } catch (IOException ioe)
    {
      throw new InstallException(InstallException.Type.START_ERROR,
          getThrowableMsg("error-starting-server", ioe), ioe);
    }
  }

  /**
   * Returns the class path (using the class path separator which is platform
   * dependent) required to run Open DS server.
   * @return the class path required to run Open DS server.
   */
  protected abstract String getOpenDSClassPath();

  /**
   * Returns the installation path.
   * @return the installation path.
   */
  protected abstract String getInstallationPath();

  /**
   * Returns the config file path.
   * @return the config file path.
   */
  protected String getConfigFilePath()
  {
    return Utils.getPath(getInstallationPath(), CONFIG_PATH_RELATIVE);
  }

  /**
   * Returns the path to the binaries.
   * @return the path to the binaries.
   */
  protected String getBinariesPath()
  {
    return Utils.getPath(getInstallationPath(),
        Utils.getBinariesRelativePath());
  }

  /**
   * Updates the contents of the provided map with the localized summary
   * strings.
   * @param hmSummary the Map to be updated.
   */
  protected void initSummaryMap(
      Map<InstallProgressStep, String> hmSummary)
  {
    hmSummary.put(InstallProgressStep.NOT_STARTED,
        getFormattedSummary(getMsg("summary-install-not-started")));
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

    String cmd;
    if (Utils.isWindows())
    {
      cmd = "bin"+File.separator+"statuspanel.bat";
    }
    else
    {
      cmd = "bin"+File.separator+"statuspanel";
    }
    cmd = UIFactory.applyFontToHtml(cmd, UIFactory.INSTRUCTIONS_MONOSPACE_FONT);
    String[] args = {formatter.getFormattedText(getInstallationPath()), cmd};
    hmSummary.put(InstallProgressStep.FINISHED_SUCCESSFULLY,
        getFormattedSuccess(
            getMsg("summary-install-finished-successfully", args)));
    hmSummary.put(InstallProgressStep.FINISHED_WITH_ERROR,
        getFormattedError(getMsg("summary-install-finished-with-error")));
  }

  /**
   * Writes the java home that we are using for the setup in a file.
   * This way we can use this java home even if the user has not set JAVA_HOME
   * when running the different scripts.
   *
   */
  protected void writeJavaHome()
  {
    try
    {
      // This isn't likely to happen, and it's not a serious problem even if
      // it does.
      SetupUtils.writeSetJavaHome(getInstallationPath());
    } catch (Exception e) {}
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
