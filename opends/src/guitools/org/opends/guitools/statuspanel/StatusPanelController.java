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

package org.opends.guitools.statuspanel;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Map;

import javax.swing.SwingUtilities;

import org.opends.server.core.DirectoryServer;

import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.guitools.statuspanel.event.ServerStatusChangeEvent;
import org.opends.guitools.statuspanel.event.ServerStatusChangeListener;
import org.opends.guitools.statuspanel.event.StatusPanelButtonListener;
import org.opends.guitools.statuspanel.ui.LoginDialog;
import org.opends.guitools.statuspanel.ui.StatusPanelDialog;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.ui.ProgressDialog;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.ui.Utilities;
import org.opends.quicksetup.util.BackgroundTask;
import org.opends.quicksetup.util.HtmlProgressMessageFormatter;
import org.opends.quicksetup.util.Utils;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.messages.MessageDescriptor;
import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.QuickSetupMessages.*;

/**
 * This is the main class of the status panel.
 *
 * It is in charge of all the logic behind the displaying of the dialogs and
 * the one that initializes everything.  This is also the class that ultimately
 * listens to user events (notably button clicks) and executes the associated
 * operations with these user events.
 *
 */
public class StatusPanelController implements ServerStatusChangeListener,
  StatusPanelButtonListener
{
  private LoginDialog loginDialog;
  private StatusPanelDialog controlPanelDialog;
  private ProgressDialog progressDialog;
  private ServerStatusPooler serverStatusPooler;
  private HtmlProgressMessageFormatter formatter =
    new HtmlProgressMessageFormatter();

  private boolean isStarting;
  private boolean isStopping;
  private boolean isRestarting;

  private boolean mustDisplayAuthenticateDialog;

  private ServerStatusDescriptor desc;

  private Message lastDetail;
  private Message lastSummary;

  private Thread progressUpdater;

  private ApplicationTrustManager trustManager;

//Update period of the progress dialog.
  private static final int UPDATE_PERIOD = 500;

  private static final ConnectionProtocolPolicy CONNECTION_POLICY =
    ConnectionProtocolPolicy.USE_MOST_SECURE_AVAILABLE;

  /**
   * This method creates the control panel dialogs and to check the current
   * install status. This method must be called outside the event thread because
   * it can perform long operations which can make the user think that the UI is
   * blocked.
   *
   * Note that there is no synchronization code between threads.  The
   * synchronization code is not required as all the start/stop/restart actions
   * are launched in the event thread so just using booleans that are updated
   * in the event thread is enough to guarantee that there are no multiple
   * operations launched at the same time.
   *
   * @param args for the moment this parameter is not used but we keep it in
   * order to (in case of need) pass parameters through the command line.
   */
  public void initialize(String[] args)
  {
    DirectoryServer.bootstrapClient();
    initLookAndFeel();
    trustManager = new ApplicationTrustManager(null);
    /* Call this methods to create the dialogs (the control panel dialog
     * is generated when we call getLoginDialog()). */
    getLoginDialog();
    getProgressDialog();
    serverStatusPooler = new ServerStatusPooler(CONNECTION_POLICY);
    serverStatusPooler.addServerStatusChangeListener(this);
    serverStatusPooler.startPooling();
    desc = serverStatusPooler.getLastDescriptor();
    while (desc == null)
    {
      try
      {
        Thread.sleep(100);
      }
      catch (Exception ex)
      {
      }
      desc = serverStatusPooler.getLastDescriptor();
    }
  }

  /**
   * This method displays the required dialog. This method must be called from
   * the event thread.
   */
  public void display()
  {
    getStatusPanelDialog().packAndShow();
    if (!isAuthenticated())
    {
      if (desc.getStatus() == ServerStatusDescriptor.ServerStatus.STARTED)
      {
        authenticateClicked();
      }
    }
  }

  /**
   * ServerStatusChangeListener implementation.  This method is called when
   * a new ServerStatusDescriptor has been generated by the ServerStatusPooler.
   * @param ev the event we receive.
   */
  public void statusChanged(final ServerStatusChangeEvent ev)
  {
    /* Here we assume that this events are not very frequent (not frequent
     * at least to generate flickering).  This is acceptable since the
     * ServerStatusPooler does pooling every second.
     */
    if (SwingUtilities.isEventDispatchThread())
    {
      getStatusPanelDialog().updateContents(ev.getStatusDescriptor());
    }
    else
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          getStatusPanelDialog().updateContents(ev.getStatusDescriptor());
        }
      });
    }
  }

  /**
   * Method called when user clicks on Quit button.
   * StatusPanelButtonListener implementation.
   */
  public void quitClicked()
  {
    serverStatusPooler.stopPooling();
    System.exit(0);
  }

  /**
   * Method called when user clicks on Start button.
   * StatusPanelButtonListener implementation.
   */
  public void startClicked()
  {
    if (isStarting)
    {
      if (!getProgressDialog().isVisible())
      {
        getProgressDialog().setVisible(true);
      }
    }
    else if (isStopping)
    {
      /* Should not happen */
      Thread.dumpStack();
    }
    else if (isRestarting)
    {
      /* Should not happen */
      Thread.dumpStack();
    }
    else
    {
      isStarting = true;
      lastDetail = null;
      getProgressDialog().setSummary(
          getFormattedSummary(INFO_SUMMARY_STARTING.get()));
      getProgressDialog().setDetails(Message.EMPTY);
      serverStatusPooler.beginServerStart();
      getProgressDialog().setCloseButtonEnabled(false);
      getStatusPanelDialog().setStartButtonEnabled(false);
      getStatusPanelDialog().setStopButtonEnabled(false);
      getStatusPanelDialog().setRestartButtonEnabled(false);
      getStatusPanelDialog().setAuthenticateButtonEnabled(false);

      if (!getProgressDialog().isVisible())
      {
        getProgressDialog().pack();
        Utilities.centerOnComponent(getProgressDialog(),
                getStatusPanelDialog());
        getProgressDialog().setVisible(true);
      }

      BackgroundTask task = new BackgroundTask()
      {
        public Object processBackgroundTask()
        {
          if (progressUpdater == null)
          {
            runProgressUpdater();
          }
          mustDisplayAuthenticateDialog = startServer() && !isAuthenticated();
          serverStatusPooler.endServerStart();
          return null;
        }
        public void backgroundTaskCompleted(Object value, Throwable t)
        {
          if (t != null)
          {
            // Bug
            t.printStackTrace();
          }
          getStatusPanelDialog().setStartButtonEnabled(true);
          getStatusPanelDialog().setStopButtonEnabled(true);
          getStatusPanelDialog().setRestartButtonEnabled(true);
          getStatusPanelDialog().setAuthenticateButtonEnabled(
              !isAuthenticated());
          getProgressDialog().setCloseButtonEnabled(true);
          isStarting = false;
        }
      };
      task.startBackgroundTask();
    }
  }

  /**
   * Method called when user clicks on Stop button.
   * StatusPanelButtonListener implementation.
   */
  public void stopClicked()
  {
    if (isStopping)
    {
      if (!getProgressDialog().isVisible())
      {
        getProgressDialog().setVisible(true);
      }
    }
    else if (isStarting)
    {
      /* Should not happen */
      Thread.dumpStack();
    }
    else if (isRestarting)
    {
      /* Should not happen */
      Thread.dumpStack();
    }
    else
    {
      boolean stopServer = confirmStop();

      if (stopServer)
      {
        isStopping = true;
        lastDetail = null;
        getProgressDialog().setSummary(
            getFormattedSummary(INFO_SUMMARY_STOPPING.get()));
        getProgressDialog().setDetails(Message.EMPTY);
        serverStatusPooler.beginServerStop();
        getProgressDialog().setCloseButtonEnabled(false);
        getStatusPanelDialog().setStartButtonEnabled(false);
        getStatusPanelDialog().setStopButtonEnabled(false);
        getStatusPanelDialog().setRestartButtonEnabled(false);
        getStatusPanelDialog().setAuthenticateButtonEnabled(false);

        if (!getProgressDialog().isVisible())
        {
          getProgressDialog().pack();
          Utilities.centerOnComponent(getProgressDialog(),
                  getStatusPanelDialog());
          getProgressDialog().setVisible(true);
        }

        BackgroundTask task = new BackgroundTask()
        {
          public Object processBackgroundTask()
          {
            if (progressUpdater == null)
            {
              runProgressUpdater();
            }
            stopServer();
            serverStatusPooler.endServerStop();
            mustDisplayAuthenticateDialog = false;
            return null;
          }
          public void backgroundTaskCompleted(Object value, Throwable t)
          {
            if (t != null)
            {
              // Bug
              t.printStackTrace();
            }
            getStatusPanelDialog().setStartButtonEnabled(true);
            getStatusPanelDialog().setStopButtonEnabled(true);
            getStatusPanelDialog().setRestartButtonEnabled(true);
            getStatusPanelDialog().setAuthenticateButtonEnabled(false);
            getProgressDialog().setCloseButtonEnabled(true);
            isStopping = false;
          }
        };
        task.startBackgroundTask();
      }
    }
  }

  /**
   * Method called when user clicks on Restart button.
   * StatusPanelButtonListener implementation.
   */
  public void restartClicked()
  {
    if (isRestarting)
    {
      if (!getProgressDialog().isVisible())
      {
        getProgressDialog().setVisible(true);
      }
    }
    else if (isStopping)
    {
      /* Should not happen */
      Thread.dumpStack();
    }
    else if (isStarting)
    {
      /* Should not happen */
      Thread.dumpStack();
    }
    else
    {
      boolean restartServer = confirmRestart();

      if (restartServer)
      {
        isRestarting = true;
        lastDetail = null;
        getProgressDialog().setSummary(
            getFormattedSummary(INFO_SUMMARY_STOPPING.get()));
        getProgressDialog().setDetails(Message.EMPTY);
        serverStatusPooler.beginServerStop();
        getProgressDialog().setCloseButtonEnabled(false);
        getStatusPanelDialog().setStartButtonEnabled(false);
        getStatusPanelDialog().setStopButtonEnabled(false);
        getStatusPanelDialog().setRestartButtonEnabled(false);
        getStatusPanelDialog().setAuthenticateButtonEnabled(false);

        if (!getProgressDialog().isVisible())
        {
          getProgressDialog().pack();
          Utilities.centerOnComponent(getProgressDialog(),
                  getStatusPanelDialog());
          getProgressDialog().setVisible(true);
        }

        BackgroundTask task = new BackgroundTask()
        {
          public Object processBackgroundTask()
          {
            if (progressUpdater == null)
            {
              runProgressUpdater();
            }
            if (stopServer())
            {
              serverStatusPooler.endServerStop();
              serverStatusPooler.beginServerStart();
              mustDisplayAuthenticateDialog = startServer() &&
              !isAuthenticated();
              serverStatusPooler.endServerStart();
            }
            else
            {
              serverStatusPooler.endServerStop();
              mustDisplayAuthenticateDialog = false;
            }
            return null;
          }
          public void backgroundTaskCompleted(Object value, Throwable t)
          {
            if (t != null)
            {
              // Bug
              t.printStackTrace();
            }
            getStatusPanelDialog().setStartButtonEnabled(true);
            getStatusPanelDialog().setStopButtonEnabled(true);
            getStatusPanelDialog().setRestartButtonEnabled(true);
            getStatusPanelDialog().setAuthenticateButtonEnabled(
                !isAuthenticated());
            getProgressDialog().setCloseButtonEnabled(true);
            isRestarting = false;
          }
        };
        task.startBackgroundTask();
      }
    }
  }

  /**
   * Method called when user clicks on Authenticate button.
   * StatusPanelButtonListener implementation.
   */
  public void authenticateClicked()
  {
    getLoginDialog().pack();
    Utilities.centerOnComponent(getLoginDialog(), getStatusPanelDialog());
    getLoginDialog().setVisible(true);
    if (!getLoginDialog().isCancelled())
    {
      try
      {
        serverStatusPooler.setAuthentication(
            getLoginDialog().getDirectoryManagerDn(),
            getLoginDialog().getDirectoryManagerPwd(),
            trustManager);
      }
      catch (ConfigException ce)
      {
        Utilities.displayError(getLoginDialog(), ce.getMessageObject(),
            INFO_ERROR_TITLE.get());
        getLoginDialog().toFront();
      }
    }
  }

  /**
   * A method to initialize the look and feel.
   *
   */
  private void initLookAndFeel()
  {
    UIFactory.initialize();
  }

  /**
   * Returns the login dialog and creates it if it does not exist.
   * @return the login dialog.
   */
  private LoginDialog getLoginDialog()
  {
    if (loginDialog == null)
    {
      loginDialog = new LoginDialog(getStatusPanelDialog(), trustManager,
          CONNECTION_POLICY);
      loginDialog.setModal(true);
    }
    return loginDialog;
  }

  /**
   * Returns the progress dialog and creates it if it does not exist.
   * As we only can run an operation at a time (considering the nature of the
   * operations we provide today) having a single progress dialog is enough.
   * @return the progress dialog.
   */
  private ProgressDialog getProgressDialog()
  {
    if (progressDialog == null)
    {
      progressDialog = new ProgressDialog(getStatusPanelDialog());
      progressDialog.addWindowListener(new WindowAdapter()
      {
        public void windowClosed(WindowEvent ev)
        {
          if (mustDisplayAuthenticateDialog)
          {
            mustDisplayAuthenticateDialog = false;
            authenticateClicked();
          }
        }
      });
    }
    return progressDialog;
  }

  /**
   * Returns the status panel dialog and creates it if it does not exist.  This
   * is the dialog that displays the status information to the user.
   * @return the status panel dialog.
   */
  private StatusPanelDialog getStatusPanelDialog()
  {
    if (controlPanelDialog == null)
    {
      controlPanelDialog = new StatusPanelDialog();
      controlPanelDialog.addButtonListener(this);
    }
    return controlPanelDialog;
  }

  /**
   * This methods starts the server and updates the progress with the start
   * messages.
   * @return <CODE>true</CODE> if the server started successfully and
   * <CODE>false</CODE> otherwise.
   */
  protected boolean startServer()
  {
    boolean started = false;

    if (isRestarting)
    {
      updateProgress(
              getFormattedSummary(INFO_SUMMARY_STARTING.get()),
              getTaskSeparator());
    }
    updateProgress(
        getFormattedSummary(INFO_SUMMARY_STARTING.get()),
        getFormattedProgressWithLineBreak(INFO_PROGRESS_STARTING.get()));

    ArrayList<String> argList = new ArrayList<String>();
    Installation installation = Installation.getLocal();
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
      Process process = pb.start();

      BufferedReader err =
        new BufferedReader(new InputStreamReader(process.getErrorStream()));
      BufferedReader out =
        new BufferedReader(new InputStreamReader(process.getInputStream()));

      /* Create these objects to resend the stop process output to the details
       * area.
       */
      new ProgressReader(err, true, true);
      new ProgressReader(out, false, true);

      int returnValue = process.waitFor();

      if (returnValue == 0)
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
        boolean running = false;
        for (int i=0; i<5 && !running; i++)
        {
          running = Installation.getLocal().getStatus().isServerRunning();
        }

        if (!running)
        {
          try
          {
            Thread.sleep(1000);
          }
          catch (Throwable t)
          {
          }
        }
        if (!running)
        {
          updateProgress(getFormattedError(INFO_SUMMARY_START_ERROR.get()),
                getFormattedError(INFO_ERROR_STARTING_SERVER_GENERIC.get(),
                    true));
        }
        else
        {
          updateProgress(
              getFormattedSuccess(INFO_SUMMARY_START_SUCCESS.get()),
              Message.EMPTY);
          started = true;
        }
      }
      else
      {
        Message msg = INFO_ERROR_STARTING_SERVER_CODE
                .get(String.valueOf(returnValue));

        /*
         * The return code is not the one expected, assume the server could
         * not be started.
         */
        updateProgress(
            getFormattedError(INFO_SUMMARY_START_ERROR.get()),
            msg);
      }

    } catch (IOException ioe)
    {
      Message msg =
              Utils.getThrowableMsg(INFO_ERROR_STARTING_SERVER.get(), ioe);
      updateProgress(
          getFormattedError(INFO_SUMMARY_START_ERROR.get()),
          msg);
    }
    catch (InterruptedException ie)
    {
      Message msg = Utils.getThrowableMsg(INFO_ERROR_STARTING_SERVER.get(), ie);
      updateProgress(
          getFormattedError(INFO_SUMMARY_START_ERROR.get()),
          msg);
    }

    return started;
  }

  /**
   * This methods stops the server and updates the progress with the stop
   * messages.
   * @return <CODE>true</CODE> if the server stopped successfully and
   * <CODE>false</CODE> otherwise.
   */
  private boolean stopServer()
  {
    boolean stopped = false;
    updateProgress(
        getFormattedSummary(INFO_SUMMARY_STOPPING.get()),
        getFormattedProgressWithLineBreak(INFO_PROGRESS_STOPPING.get()));

    ArrayList<String> argList = new ArrayList<String>();
    Installation installation = Installation.getLocal();
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

    try
    {
      Process process = pb.start();

      BufferedReader err =
          new BufferedReader(new InputStreamReader(process.getErrorStream()));
      BufferedReader out =
          new BufferedReader(new InputStreamReader(process.getInputStream()));

      /* Create these objects to resend the stop process output to the details
       * area.
       */
      new ProgressReader(err, true, false);
      new ProgressReader(out, false, false);

      int returnValue = process.waitFor();

      int clientSideError =
      org.opends.server.protocols.ldap.LDAPResultCode.CLIENT_SIDE_CONNECT_ERROR;
      if ((returnValue == clientSideError) || (returnValue == 0))
      {
        if (Utils.isWindows())
        {
          /*
           * Sometimes the server keeps some locks on the files.
           * TODO: remove this code once stop-ds returns properly when server
           * is stopped.
           */
          int nTries = 10;
          for (int i=0; i<nTries && !stopped; i++)
          {
            stopped = !Installation.getLocal().getStatus()
                    .isServerRunning();
            if (!stopped)
            {
              Message msg = new MessageBuilder(
                getFormattedLog(INFO_PROGRESS_SERVER_WAITING_TO_STOP.get()))
                      .append(getLineBreak()).toMessage();
              updateProgress(
                  getFormattedSummary(INFO_SUMMARY_STOPPING.get()),
                  msg);
              try
              {
                Thread.sleep(5000);
              }
              catch (Exception ex)
              {

              }
            }
          }
          if (!stopped)
          {
            returnValue = -1;
          }
        }
      }

      if (returnValue == clientSideError)
      {
        Message msg = new MessageBuilder(getLineBreak()).append(
            getFormattedLog(INFO_PROGRESS_SERVER_ALREADY_STOPPED.get())).append(
            getLineBreak()).toMessage();
        if (!isRestarting)
        {
          updateProgress(
              getFormattedSuccess(INFO_SUMMARY_STOP_SUCCESS.get()),
              msg);
        }
        else
        {
          updateProgress(
              getFormattedSummary(INFO_SUMMARY_STOP_SUCCESS.get()),
              msg);
        }
        stopped = true;
      }
      else if (returnValue != 0)
      {
        Message msg = INFO_ERROR_STOPPING_SERVER_CODE
                .get(String.valueOf(returnValue));

        /*
         * The return code is not the one expected, assume the server could
         * not be stopped.
         */
        updateProgress(
            getFormattedError(INFO_SUMMARY_STOP_ERROR.get()),
            msg);
      }
      else
      {
        Message msg = getFormattedLog(INFO_PROGRESS_SERVER_STOPPED.get());
        if (!isRestarting)
        {
          updateProgress(
              getFormattedSuccess(INFO_SUMMARY_STOP_SUCCESS.get()),
              msg);
        }
        else
        {
          updateProgress(
              getFormattedSummary(INFO_SUMMARY_STOP_SUCCESS.get()),
              msg);
        }
        stopped = true;
      }

    } catch (IOException ioe)
    {
      Message msg = Utils.getThrowableMsg(
              INFO_ERROR_STOPPING_SERVER.get(), ioe);
      updateProgress(
          getFormattedError(INFO_SUMMARY_STOP_ERROR.get()),
          msg);
    }
    catch (InterruptedException ie)
    {
      Message msg = Utils.getThrowableMsg(INFO_ERROR_STOPPING_SERVER.get(), ie);
      updateProgress(
          getFormattedError(INFO_SUMMARY_STOP_ERROR.get()),
          msg);
    }
    return stopped;
  }


  /**
   * Updates the progress variables used to update the progress dialog during
   * start/stop/restart.
   *
   * @param summary the summary for the start/stop/restart operation.
   * @param newDetail the new detail for the start/stop/restart operation.
   */
  private synchronized void updateProgress(final Message summary,
      final Message newDetail)
  {
    if (lastDetail == null)
    {
      lastDetail = newDetail;
    }
    else
    {
      lastDetail = new MessageBuilder(lastDetail)
              .append(newDetail).toMessage();
    }
    lastSummary = summary;
  }

  /**
   * This method is used to update the progress dialog.
   *
   * We are receiving notifications from the installer and uninstaller (this
   * class is a ProgressListener). However if we send lots of notifications
   * updating the progress panel every time we get a progress update can result
   * of a lot of flickering. So the idea here is to have a minimal time between
   * 2 updates of the progress dialog (specified by UPDATE_PERIOD).
   */
  private void runProgressUpdater()
  {
    progressUpdater = new Thread()
    {
      public void run()
      {
        try
        {
        Message lastDisplayedSummary = null;
        Message lastDisplayedDetail = null;
        while (true)
        {
          if (lastSummary != null)
          {
            if (lastSummary != lastDisplayedSummary)
            {
              lastDisplayedSummary = lastSummary;
              SwingUtilities.invokeLater(new Runnable()
              {
                public void run()
                {
                  getProgressDialog().setSummary(lastSummary);
                }
              });
            }
          }

          if (lastDetail != null)
          {
            if (lastDetail != lastDisplayedDetail)
            {
              lastDisplayedDetail = lastDetail;
              SwingUtilities.invokeLater(new Runnable()
              {
                public void run()
                {
                  getProgressDialog().setDetails(lastDetail);
                }
              });
            }
          }

          try
          {
            Thread.sleep(UPDATE_PERIOD);
          } catch (Exception ex)
          {
          }
        }
        } catch (Throwable t)
        {
          // Bug
          t.printStackTrace();
        }
      }
    };
    progressUpdater.start();
  }

  /**
   * Returns the formatted representation of the text that is the summary of the
   * installation process (the one that goes in the UI next to the progress
   * bar).
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of an error for the given text.
   */
  private Message getFormattedSummary(Message text)
  {
    return formatter.getFormattedSummary(text);
  }

  /**
   * Returns the formatted representation of a success message for a given text.
   * @param text the source text from which we want to get the formatted
   * representation.
   * @return the formatted representation of an success message for the given
   * text.
   */
  private Message getFormattedSuccess(Message text)
  {
    return formatter.getFormattedSuccess(text);
  }

  /**
   * Returns the formatted representation of an error for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of an error for the given text.
   */
  private Message getFormattedError(Message text)
  {
    return formatter.getFormattedError(text, false);
  }

  /**
   * Returns the formatted representation of an error for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of an error for the given text.
   */
  private Message getFormattedError(Message text, boolean applyMargin)
  {
    return formatter.getFormattedError(text, applyMargin);
  }

  /**
   * Returns the formatted representation of a log error message for a given
   * text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a log error message for the given
   * text.
   */
  private Message getFormattedLogError(Message text)
  {
    return formatter.getFormattedLogError(text);
  }

  /**
   * Returns the formatted representation of a log message for a given text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a log message for the given text.
   */
  private Message getFormattedLog(Message text)
  {
    return formatter.getFormattedLog(text);
  }

  /**
   * Returns the line break formatted.
   * @return the line break formatted.
   */
  private Message getLineBreak()
  {
    return formatter.getLineBreak();
  }

  /**
   * Returns the task separator formatted.
   * @return the task separator formatted.
   */
  private Message getTaskSeparator()
  {
    return formatter.getTaskSeparator();
  }

  /**
   * Returns the formatted representation of a progress message for a given
   * text.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a progress message for the given
   * text.
   */
  private Message getFormattedProgress(Message text)
  {
    return formatter.getFormattedProgress(text);
  }

  /**
   * Returns the formatted representation of a progress message for a given
   * text with a line feed at the end.
   * @param text the source text from which we want to get the formatted
   * representation
   * @return the formatted representation of a progress message for the given
   * text.
   */
  private Message getFormattedProgressWithLineBreak(Message text) {
    return new MessageBuilder(text).append(getLineBreak()).toMessage();
  }

  /**
   * This class is used to read the standard error and standard output of the
   * Stop/Start process.
   *
   * When a new log message is found notifies the progress listeners of it. If
   * an error occurs it also notifies the listeners.
   *
   */
  private class ProgressReader
  {
    private boolean isFirstLine;
    private Message errorMsg;

    /**
     * The protected constructor.
     * @param reader the BufferedReader of the stop process.
     * @param isError a boolean indicating whether the BufferedReader
     * corresponds to the standard error or to the standard output.
     * @param isStart a boolean indicating whether we are starting or stopping
     * the server.
     */
    public ProgressReader(final BufferedReader reader, final boolean isError,
        final boolean isStart)
    {
      final MessageDescriptor.Arg0 errorTag =
          isError ? INFO_ERROR_READING_ERROROUTPUT :
                  INFO_ERROR_READING_OUTPUT;

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
              MessageBuilder buf = new MessageBuilder();
              if (!isFirstLine)
              {
                buf.append(formatter.getLineBreak());
              }
              if (isError)
              {
                buf.append(getFormattedLogError(Message.raw(line)));
              } else
              {
                buf.append(getFormattedLog(Message.raw(line)));
              }
              Message summary = isStart?
                  getFormattedSummary(INFO_SUMMARY_STARTING.get()):
                    getFormattedSummary(INFO_SUMMARY_STOPPING.get());
              updateProgress(summary, buf.toMessage());
              isFirstLine = false;

              line = reader.readLine();
            }
          } catch (IOException ioe)
          {
            errorMsg = Utils.getThrowableMsg(errorTag.get(), ioe);

          } catch (Throwable t)
          {
            errorMsg = Utils.getThrowableMsg(errorTag.get(), t);
          }
        }
      });
      t.start();
    }

    public Message getErrorMessage()
    {
      return errorMsg;
    }
  }

  /**
   * Displays a confirmation dialog asking the user whether to stop the server
   * or not.
   * @return <CODE>true</CODE> if the server confirms that (s)he wants to stop
   * the server and <CODE>false</CODE> otherwise.
   */
  private boolean confirmStop()
  {
    return Utilities.displayConfirmation(getStatusPanelDialog(),
        INFO_CONFIRM_STOP_MESSAGE.get(),
            INFO_CONFIRM_STOP_TITLE.get());
  }

  /**
   * Displays a confirmation dialog asking the user whether to restart the
   * server or not.
   * @return <CODE>true</CODE> if the server confirms that (s)he wants to
   * restart the server and <CODE>false</CODE> otherwise.
   */
  private boolean confirmRestart()
  {
    return Utilities.displayConfirmation(getStatusPanelDialog(),
        INFO_CONFIRM_RESTART_MESSAGE.get(),
            INFO_CONFIRM_RESTART_TITLE.get());
  }

  /**
   * Returns whether the user provided LDAP authentication or not.
   * @return <CODE>true</CODE> if the server provided LDAP authentication and
   * <CODE>false</CODE> otherwise.
   */
  private boolean isAuthenticated()
  {
    return serverStatusPooler.isAuthenticated();
  }
}
