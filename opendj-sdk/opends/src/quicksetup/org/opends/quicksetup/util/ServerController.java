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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.util;

import org.opends.quicksetup.*;
import org.opends.server.protocols.ldap.LDAPResultCode;

import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.Map;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

/**
 * Class used to manipulate an OpenDS server.
 */
public class ServerController {

  private Application application;

  private Installation installation;

  /**
   * Creates a new instance that will operate on <code>application</code>'s
   * installation.
   * @param application to use for notifications
   */
  public ServerController(Application application) {
    this(application, application.getInstallation());
  }

  /**
   * Creates a new instance that will operate on <code>installation</code>
   * and use <code>application</code> for notifications.
   * @param application to use for notifications
   * @param installation representing the server instance to control
   */
  public ServerController(Application application, Installation installation) {
    if (application == null) {
      throw new NullPointerException("application cannot be null");
    }
    if (installation == null) {
      throw new NullPointerException("installation cannot be null");
    }
    this.application = application;
    this.installation = installation;
  }

  /**
   * This methods stops the server.
   *
   * @throws org.opends.quicksetup.ApplicationException if something goes wrong.
   */
  public void stopServer() throws ApplicationException {
    application.notifyListeners(
            application.getFormattedProgress(
                    application.getMsg("progress-stopping")) +
                    application.getLineBreak());

    ArrayList<String> argList = new ArrayList<String>();
    argList.add(Utils.getPath(installation.getServerStopCommandFile()));
    String[] args = new String[argList.size()];
    argList.toArray(args);
    ProcessBuilder pb = new ProcessBuilder(args);
    Map<String, String> env = pb.environment();
    env.put("JAVA_HOME", System.getProperty("java.home"));
    /* Remove JAVA_BIN to be sure that we use the JVM running the uninstaller
     * JVM to stop the server.
     */
    env.remove("JAVA_BIN");

    try {
      Process process = pb.start();

      BufferedReader err =
              new BufferedReader(
                      new InputStreamReader(process.getErrorStream()));
      BufferedReader out =
              new BufferedReader(
                      new InputStreamReader(process.getInputStream()));

      /* Create these objects to resend the stop process output to the details
       * area.
       */
      new StopReader(err, true);
      new StopReader(out, false);

      int returnValue = process.waitFor();

      int clientSideError = LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR;
      if ((returnValue == clientSideError) || (returnValue == 0)) {
        if (Utils.isWindows()) {
          /*
           * Sometimes the server keeps some locks on the files.
           * TODO: remove this code once stop-ds returns properly when server
           * is stopped.
           */
          int nTries = 10;
          boolean stopped = false;

          for (int i = 0; i < nTries && !stopped; i++) {
            stopped = !CurrentInstallStatus.isServerRunning();
            if (!stopped) {
              String msg =
                      application.getFormattedLog(
                        application.getMsg("progress-server-waiting-to-stop")) +
                        application.getLineBreak();
              application.notifyListeners(msg);
              try {
                Thread.sleep(5000);
              }
              catch (Exception ex) {

              }
            }
          }
          if (!stopped) {
            returnValue = -1;
          }
        }
      }

      if (returnValue == clientSideError) {
        String msg = application.getLineBreak() +
                application.getFormattedLog(
                        application.getMsg("progress-server-already-stopped")) +
                    application.getLineBreak();
        application.notifyListeners(msg);

      } else if (returnValue != 0) {
        String[] arg = {String.valueOf(returnValue)};
        String msg = application.getMsg("error-stopping-server-code", arg);

        /*
         * The return code is not the one expected, assume the server could
         * not be stopped.
         */
        throw new ApplicationException(ApplicationException.Type.STOP_ERROR,
                msg,
                null);
      } else {
        String msg = application.getFormattedLog(
                application.getMsg("progress-server-stopped"));
        application.notifyListeners(msg);
      }

    } catch (IOException ioe) {
      throw new ApplicationException(ApplicationException.Type.STOP_ERROR,
              application.getThrowableMsg("error-stopping-server", ioe), ioe);
    }
    catch (InterruptedException ie) {
      throw new ApplicationException(ApplicationException.Type.BUG,
              application.getThrowableMsg("error-stopping-server", ie), ie);
    }
  }

  /**
   * This methods starts the server.
   * @throws org.opends.quicksetup.QuickSetupException if something goes wrong.
   */
  public void startServer() throws QuickSetupException {
    application.notifyListeners(
            application.getFormattedProgress(
                    application.getMsg("progress-starting")) +
        application.getLineBreak());

    ArrayList<String> argList = new ArrayList<String>();
    argList.add(Utils.getPath(installation.getServerStartCommandFile()));
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
      QuickSetupException ex = errReader.getException();
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
          // TODO: get this information from the installation instead
          UserData userData = application.getUserData();
          String ldapUrl = "ldap://localhost:" + userData.getServerPort();
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
          String[] arg = {String.valueOf(application.getUserData().
                  getServerPort())};
          if (Utils.isWindows())
          {

            throw new QuickSetupException(QuickSetupException.Type.START_ERROR,
                application.getMsg("error-starting-server-in-windows", arg),
                    null);
          }
          else
          {
            throw new QuickSetupException(QuickSetupException.Type.START_ERROR,
                application.getMsg("error-starting-server-in-unix", arg), null);
          }
        }
      }

    } catch (IOException ioe)
    {
      throw new QuickSetupException(QuickSetupException.Type.START_ERROR,
          application.getThrowableMsg("error-starting-server", ioe), ioe);
    }
  }

  /**
   * This class is used to read the standard error and standard output of the
   * Stop process.
   * <p/>
   * When a new log message is found notifies the
   * UninstallProgressUpdateListeners of it. If an error occurs it also
   * notifies the listeners.
   */
  private class StopReader {
    private boolean isFirstLine;

    /**
     * The protected constructor.
     *
     * @param reader  the BufferedReader of the stop process.
     * @param isError a boolean indicating whether the BufferedReader
     *        corresponds to the standard error or to the standard output.
     */
    public StopReader(final BufferedReader reader, final boolean isError) {
      final String errorTag =
              isError ? "error-reading-erroroutput" : "error-reading-output";

      isFirstLine = true;

      Thread t = new Thread(new Runnable() {
        public void run() {
          try {
            String line = reader.readLine();
            while (line != null) {
              StringBuilder buf = new StringBuilder();
              if (!isFirstLine) {
                buf.append(application.getProgressMessageFormatter().
                        getLineBreak());
              }
              if (isError) {
                buf.append(application.getFormattedLogError(line));
              } else {
                buf.append(application.getFormattedLog(line));
              }
              application.notifyListeners(buf.toString());
              isFirstLine = false;

              line = reader.readLine();
            }
          } catch (IOException ioe) {
            String errorMsg = application.getThrowableMsg(errorTag, ioe);
            application.notifyListeners(errorMsg);

          } catch (Throwable t) {
            String errorMsg = application.getThrowableMsg(errorTag, t);
            application.notifyListeners(errorMsg);
          }
        }
      });
      t.start();
    }
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
   * This class is used to read the standard error and standard output of the
   * Start process.
   *
   * When a new log message is found notifies the ProgressUpdateListeners
   * of it. If an error occurs it also notifies the listeners.
   *
   */
  private class StartReader
  {
    private QuickSetupException ex;

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
                buf.append(application.getProgressMessageFormatter().
                        getLineBreak());
              }
              if (isError)
              {
                buf.append(application.getFormattedLogError(line));
              } else
              {
                buf.append(application.getFormattedLog(line));
              }
              application.notifyListeners(buf.toString());
              isFirstLine = false;

              if (line.indexOf("id=" + startedId) != -1)
              {
                isFinished = true;
              }
              line = reader.readLine();
            }
          } catch (IOException ioe)
          {
            String errorMsg = application.getThrowableMsg(errorTag, ioe);
            ex =
                new QuickSetupException(QuickSetupException.Type.START_ERROR,
                    errorMsg, ioe);

          } catch (Throwable t)
          {
            String errorMsg = application.getThrowableMsg(errorTag, t);
            ex =
                new QuickSetupException(QuickSetupException.Type.START_ERROR,
                    errorMsg, t);
          }
          isFinished = true;
        }
      });
      t.start();
    }

    /**
     * Returns the QuickSetupException that occurred reading the Start error and
     * output or <CODE>null</CODE> if no exception occurred.
     * @return the exception that occurred reading or <CODE>null</CODE> if
     * no exception occurred.
     */
    public QuickSetupException getException()
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

}
