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

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import static org.opends.messages.QuickSetupMessages.*;

import org.opends.quicksetup.*;
import static org.opends.quicksetup.util.Utils.*;
import org.opends.quicksetup.installer.InstallerHelper;

import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

/**
 * Class used to manipulate an OpenDS server.
 */
public class ServerController {

  static private final Logger LOG =
          Logger.getLogger(ServerController.class.getName());

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
   * Creates a new instance that will operate on <code>application</code>'s
   * installation.
   * @param installation representing the server instance to control
   */
  public ServerController(Installation installation) {
    this(null, installation);
  }

  /**
   * Creates a new instance that will operate on <code>installation</code>
   * and use <code>application</code> for notifications.
   * @param application to use for notifications
   * @param installation representing the server instance to control
   */
  public ServerController(Application application, Installation installation) {
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
    stopServer(false);
  }

  /**
   * This methods stops the server.
   *
   * @param suppressOutput boolean indicating that ouput to standard output
   *                       streams from the server should be suppressed.
   * @throws org.opends.quicksetup.ApplicationException
   *          if something goes wrong.
   */
  public void stopServer(boolean suppressOutput) throws ApplicationException {

    if (suppressOutput && !StandardOutputSuppressor.isSuppressed()) {
      StandardOutputSuppressor.suppress();
    }

    try {
      if (application != null) {
        MessageBuilder mb = new MessageBuilder();
        mb.append(application.getFormattedProgress(
                        INFO_PROGRESS_STOPPING.get()));
        mb.append(application.getLineBreak());
        application.notifyListeners(mb.toMessage());
      }
      LOG.log(Level.INFO, "stopping server");

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

        int clientSideError =
                org.opends.server.protocols.ldap.
                        LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR;
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
              stopped = !installation.getStatus().isServerRunning();
              if (!stopped) {
                if (application != null) {
                  MessageBuilder mb = new MessageBuilder();
                  mb.append(application.getFormattedLog(
                          INFO_PROGRESS_SERVER_WAITING_TO_STOP.get()));
                  mb.append(application.getLineBreak());
                  application.notifyListeners(mb.toMessage());
                }
                LOG.log(Level.FINE, "waiting for server to stop");
                try {
                  Thread.sleep(5000);
                }
                catch (Exception ex) {

                }
              } else {
                break;
              }
            }
            if (!stopped) {
              returnValue = -1;
            }
          }
        }

        if (returnValue == clientSideError) {
          if (application != null) {
            MessageBuilder mb = new MessageBuilder();
            mb.append(application.getLineBreak());
            mb.append(application.getFormattedLog(
                            INFO_PROGRESS_SERVER_ALREADY_STOPPED.get()));
            mb.append(application.getLineBreak());
            application.notifyListeners(mb.toMessage());
          }
          LOG.log(Level.INFO, "server already stopped");

        } else if (returnValue != 0) {
          /*
          * The return code is not the one expected, assume the server could
          * not be stopped.
          */
          throw new ApplicationException(
              ApplicationReturnCode.ReturnCode.STOP_ERROR,
                  INFO_ERROR_STOPPING_SERVER_CODE.get(
                          String.valueOf(returnValue)),
                  null);
        } else {
          if (application != null) {
            application.notifyListeners(application.getFormattedLog(
                    INFO_PROGRESS_SERVER_STOPPED.get()));
          }
          LOG.log(Level.INFO, "server stopped");
        }

      } catch (Exception e) {
        throw new ApplicationException(
            ApplicationReturnCode.ReturnCode.STOP_ERROR, getThrowableMsg(
                INFO_ERROR_STOPPING_SERVER.get(), e), e);
      }
    } finally {
      if (suppressOutput && StandardOutputSuppressor.isSuppressed()) {
        StandardOutputSuppressor.unsuppress();
      }
    }
  }

  /**
   * This methods starts the server.
   *
   * @return OperationOutput object containing output from the start server
   * command invocation.
   * @throws org.opends.quicksetup.ApplicationException if something goes wrong.
   */
  public OperationOutput startServer() throws ApplicationException {
    return startServer(true, false);
  }

  /**
   * This methods starts the server.
   * @param suppressOutput boolean indicating that ouput to standard output
   * streams from the server should be suppressed.
   * @return OperationOutput object containing output from the start server
   * command invocation.
   * @throws org.opends.quicksetup.ApplicationException if something goes wrong.
   */
  public OperationOutput startServer(boolean suppressOutput)
          throws ApplicationException
  {
    return startServer(true, suppressOutput);
  }

  /**
   * This methods starts the server.
   * @param verify boolean indicating whether this method will attempt to
   * connect to the server after starting to verify that it is listening.
   * @return OperationOutput object containing output from the start server
   * command invocation.
   * @return boolean indicating that ouput to standard output streams
   * from the server should be suppressed.
   * @throws org.opends.quicksetup.ApplicationException if something goes wrong.
   */
  private OperationOutput startServer(boolean verify, boolean suppressOuput)
          throws ApplicationException
  {
    OperationOutput output = new OperationOutput();

    if (suppressOuput && !StandardOutputSuppressor.isSuppressed()) {
      StandardOutputSuppressor.suppress();
    }

    try {
    if (application != null) {
      MessageBuilder mb = new MessageBuilder();
      mb.append(application.getFormattedProgress(
                      INFO_PROGRESS_STARTING.get()));
      mb.append(application.getLineBreak());
      application.notifyListeners(mb.toMessage());
    }
    LOG.log(Level.INFO, "starting server");

    ArrayList<String> argList = new ArrayList<String>();
    argList.add(Utils.getPath(installation.getServerStartCommandFile()));
    String[] args = new String[argList.size()];
    argList.toArray(args);
    ProcessBuilder pb = new ProcessBuilder(args);
    pb.directory(installation.getBinariesDirectory());
    Map<String, String> env = pb.environment();
    env.put("JAVA_HOME", System.getProperty("java.home"));
    /* Remove JAVA_BIN to be sure that we use the JVM running the installer
     * JVM to start the server.
     */
    env.remove("JAVA_BIN");

    // Upgrader's classpath contains jars located in the temporary
    // directory that we don't want locked by the directory server
    // when it starts.  Since we're just calling the start-ds script
    // it will figure out the correct classpath for the server.
    env.remove("CLASSPATH");

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

      long finishedTime = 0;
      while (!errReader.isFinished() || !outputReader.isFinished())
      {
        try
        {
          Thread.sleep(100);
        } catch (InterruptedException ie)
        {
        }

        if (errReader.startedIdFound() || outputReader.startedIdFound())
        {
          /* When we start the server in windows and we are not running it
           * under a windows service, the readers are kept open forever.
           * Once we find that is finished, wait at most 7 seconds.
           */
          if (finishedTime == 0)
          {
            finishedTime = System.currentTimeMillis();
          }
          else
          {
            if (System.currentTimeMillis() - finishedTime > 7000)
            {
              break;
            }
          }
        }
      }

      // Collect any messages found in the output
      List<Message> errors = errReader.getMessages();
      if (errors != null) {
        for(Message error : errors) {
          output.addErrorMessage(error);
        }
      }
      List<Message> messages = outputReader.getMessages();
      if (messages != null) {
        for (Message msg : messages) {

          // NOTE:  this may not be the best place to drop these.
          // However upon startup the server seems to log all messages,
          // regardless of whether or not they signal an error condition,
          // to its error log.

          output.addErrorMessage(msg);
        }
      }

      // Check if something wrong occurred reading the starting of the server
      ApplicationException ex = errReader.getException();
      if (ex == null)
      {
        ex = outputReader.getException();
      }
      if (ex != null)
      {
        // This is meaningless right now since we throw
        // the exception below, but in case we change out
        // minds later or add the ability to return exceptions
        // in the output only instead of throwing...
        output.setException(ex);
        throw ex;

      } else if (verify)
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
        Configuration config = installation.getCurrentConfiguration();
        int port = config.getPort();
        String ldapUrl = "ldap://localhost:" + port;

        // See if the application has prompted for credentials.  If
        // not we'll just try to connect anonymously.
        String userDn = null;
        String userPw = null;
        if (application != null) {
          userDn = application.getUserData().getDirectoryManagerDn();
          userPw = application.getUserData().getDirectoryManagerPwd();
        }
        if (userDn == null || userPw == null) {
          userDn = null;
          userPw = null;
        }

        for (int i=0; i<5 && !connected; i++)
        {
          try
          {
            Utils.createLdapContext(
                ldapUrl,
                userDn, userPw, 3000, null);
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
            throw new ApplicationException(
                ApplicationReturnCode.ReturnCode.START_ERROR,
                    INFO_ERROR_STARTING_SERVER_IN_WINDOWS.get(
                            String.valueOf(port)),
                    null);
          }
          else
          {
            throw new ApplicationException(
                ApplicationReturnCode.ReturnCode.START_ERROR,
                    INFO_ERROR_STARTING_SERVER_IN_UNIX.get(
                            String.valueOf(port)),
                    null);
          }
        }
      }

    } catch (IOException ioe)
    {
      throw new ApplicationException(
            ApplicationReturnCode.ReturnCode.START_ERROR,
              getThrowableMsg(INFO_ERROR_STARTING_SERVER.get(), ioe), ioe);
    }
  } finally {
      if (suppressOuput && StandardOutputSuppressor.isSuppressed()) {
        StandardOutputSuppressor.unsuppress();
      }
  }
    return output;
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
    public StopReader(final BufferedReader reader,
                                      final boolean isError) {
      final Message errorTag =
              isError ?
                      INFO_ERROR_READING_ERROROUTPUT.get() :
                      INFO_ERROR_READING_OUTPUT.get();

      isFirstLine = true;
      Thread t = new Thread(new Runnable() {
        public void run() {
          try {
            String line = reader.readLine();
            while (line != null) {
              if (application != null) {
                MessageBuilder buf = new MessageBuilder();
                if (!isFirstLine) {
                  buf.append(application.getProgressMessageFormatter().
                          getLineBreak());
                }
                if (isError) {
                  buf.append(application.getFormattedLogError(
                          Message.raw(line)));
                } else {
                  buf.append(application.getFormattedLog(
                          Message.raw(line)));
                }
                application.notifyListeners(buf.toMessage());
                isFirstLine = false;
              }
              LOG.log(Level.INFO, "server: " + line);
              line = reader.readLine();
            }
          } catch (Throwable t) {
            if (application != null) {
              Message errorMsg = getThrowableMsg(errorTag, t);
              application.notifyListeners(errorMsg);
            }
            LOG.log(Level.INFO, "error reading server messages",t);
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
    private ApplicationException ex;

    private List<Message> messages = new ArrayList<Message>();

    private boolean isFinished;

    private boolean startedIdFound;

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
      final Message errorTag =
              isError ?
                      INFO_ERROR_READING_ERROROUTPUT.get() :
                      INFO_ERROR_READING_OUTPUT.get();

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
              if (application != null) {
                MessageBuilder buf = new MessageBuilder();
                if (!isFirstLine)
                {
                  buf.append(application.getProgressMessageFormatter().
                          getLineBreak());
                }
                if (isError)
                {
                  buf.append(application.getFormattedLogError(
                          Message.raw(line)));
                } else
                {
                  buf.append(application.getFormattedLog(
                          Message.raw(line)));
                }
                application.notifyListeners(buf.toMessage());
                isFirstLine = false;
              }
              LOG.log(Level.INFO, "server: " + line);
              if (line.toLowerCase().indexOf("=" + startedId) != -1)
              {
                isFinished = true;
                startedIdFound = true;
              }

              messages.add(Message.raw(line));

              line = reader.readLine();
            }
          } catch (Throwable t)
          {
            ex = new ApplicationException(
                ApplicationReturnCode.ReturnCode.START_ERROR,
                getThrowableMsg(errorTag, t), t);

          }
          isFinished = true;
        }
      });
      t.start();
    }

    /**
     * Returns the ApplicationException that occurred reading the Start error
     * and output or <CODE>null</CODE> if no exception occurred.
     * @return the exception that occurred reading or <CODE>null</CODE> if
     * no exception occurred.
     */
    public ApplicationException getException()
    {
      return ex;
    }

    public List<Message> getMessages() {
      return messages;
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

    /**
     * Returns <CODE>true</CODE> if the server start Id was found and
     * <CODE>false</CODE> otherwise.
     * @return <CODE>true</CODE> if the server start Id was found and
     * <CODE>false</CODE> otherwise.
     */
    public boolean startedIdFound()
    {
      return startedIdFound;
    }
  }

}
