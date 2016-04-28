/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.quicksetup.ui;

import static com.forgerock.opendj.cli.Utils.*;
import static com.forgerock.opendj.util.OperatingSystem.*;

import static org.opends.messages.QuickSetupMessages.*;
import static org.opends.quicksetup.util.Utils.*;

import java.awt.Cursor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;

import javax.swing.SwingUtilities;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.quicksetup.Application;
import org.opends.quicksetup.CurrentInstallStatus;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.ProgressDescriptor;
import org.opends.quicksetup.ProgressStep;
import org.opends.quicksetup.Step;
import org.opends.quicksetup.TempLogFile;
import org.opends.quicksetup.UserDataCertificateException;
import org.opends.quicksetup.UserDataConfirmationException;
import org.opends.quicksetup.UserDataException;
import org.opends.quicksetup.WizardStep;
import org.opends.quicksetup.event.ButtonActionListener;
import org.opends.quicksetup.event.ButtonEvent;
import org.opends.quicksetup.event.ProgressUpdateEvent;
import org.opends.quicksetup.event.ProgressUpdateListener;
import org.opends.quicksetup.util.BackgroundTask;
import org.opends.quicksetup.util.HtmlProgressMessageFormatter;
import org.opends.quicksetup.util.ProgressMessageFormatter;
import org.opends.server.util.SetupUtils;

/**
 * This class is responsible for doing the following:
 * <p>
 * <ul>
 * <li>Check whether we are installing or uninstalling.</li>
 * <li>Performs all the checks and validation of the data provided by the user
 * during the setup.</li>
 * <li>It will launch also the installation once the user clicks on 'Finish' if
 * we are installing the product.</li>
 * </ul>
 */
public class QuickSetup implements ButtonActionListener, ProgressUpdateListener
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private GuiApplication application;
  private CurrentInstallStatus installStatus;
  private WizardStep currentStep;
  private QuickSetupDialog dialog;

  private final LocalizableMessageBuilder progressDetails = new LocalizableMessageBuilder();
  private ProgressDescriptor lastDescriptor;
  private ProgressDescriptor lastDisplayedDescriptor;
  private ProgressDescriptor descriptorToDisplay;

  /** Update period of the dialogs. */
  private static final int UPDATE_PERIOD = 500;

  /** The full pathname of the MacOS X LaunchServices OPEN(1) helper. */
  private static final String MAC_APPLICATIONS_OPENER = "/usr/bin/open";

  /**
   * This method creates the install/uninstall dialogs and to check the current
   * install status. This method must be called outside the event thread because
   * it can perform long operations which can make the user think that the UI is
   * blocked.
   *
   * @param tempLogFile
   *          temporary log file where messages will be logged.
   * @param args
   *          for the moment this parameter is not used but we keep it in order
   *          to (in case of need) pass parameters through the command line.
   */
  public void initialize(final TempLogFile tempLogFile, String[] args)
  {
    ProgressMessageFormatter formatter = new HtmlProgressMessageFormatter();

    installStatus = new CurrentInstallStatus();

    application = Application.create();
    application.setProgressMessageFormatter(formatter);
    application.setCurrentInstallStatus(installStatus);
    application.setTempLogFile(tempLogFile);
    if (args != null)
    {
      application.setUserArguments(args);
    }
    else
    {
      application.setUserArguments(new String[] {});
    }
    try
    {
      initLookAndFeel();
    }
    catch (Throwable t)
    {
      // This is likely a bug.
      t.printStackTrace();
    }

    /* In the calls to setCurrentStep the dialog will be created */
    setCurrentStep(application.getFirstWizardStep());
  }

  /** This method displays the setup dialog. This method must be called from the event thread. */
  public void display()
  {
    getDialog().packAndShow();
  }

  /**
   * ButtonActionListener implementation. It assumes that we are called in the
   * event thread.
   *
   * @param ev
   *          the ButtonEvent we receive.
   */
  @Override
  public void buttonActionPerformed(ButtonEvent ev)
  {
    switch (ev.getButtonName())
    {
    case NEXT:
      nextClicked();
      break;
    case CLOSE:
      closeClicked();
      break;
    case FINISH:
      finishClicked();
      break;
    case QUIT:
      quitClicked();
      break;
    case CONTINUE_INSTALL:
      continueInstallClicked();
      break;
    case PREVIOUS:
      previousClicked();
      break;
    case LAUNCH_STATUS_PANEL:
      launchStatusPanelClicked();
      break;
    case INPUT_PANEL_BUTTON:
      inputPanelButtonClicked();
      break;
    default:
      throw new IllegalArgumentException("Unknown button name: " + ev.getButtonName());
    }
  }

  /**
   * ProgressUpdateListener implementation. Here we take the ProgressUpdateEvent
   * and create a ProgressDescriptor that will be used to update the progress
   * dialog.
   *
   * @param ev
   *          the ProgressUpdateEvent we receive.
   * @see #runDisplayUpdater()
   */
  @Override
  public void progressUpdate(ProgressUpdateEvent ev)
  {
    synchronized (this)
    {
      ProgressDescriptor desc = createProgressDescriptor(ev);
      boolean isLastDescriptor = desc.getProgressStep().isLast();
      if (isLastDescriptor)
      {
        lastDescriptor = desc;
      }

      descriptorToDisplay = desc;
    }
  }

  /**
   * This method is used to update the progress dialog.
   * <p>
   * We are receiving notifications from the installer and uninstaller (this
   * class is a ProgressListener). However if we lots of notifications updating
   * the progress panel every time we get a progress update can result of a lot
   * of flickering. So the idea here is to have a minimal time between 2 updates
   * of the progress dialog (specified by UPDATE_PERIOD).
   *
   * @see #progressUpdate(org.opends.quicksetup.event.ProgressUpdateEvent)
   */
  private void runDisplayUpdater()
  {
    boolean doPool = true;
    while (doPool)
    {
      try
      {
        Thread.sleep(UPDATE_PERIOD);
      }
      catch (Exception ex) {}

      synchronized (this)
      {
        final ProgressDescriptor desc = descriptorToDisplay;
        if (desc != null)
        {
          if (desc != lastDisplayedDescriptor)
          {
            lastDisplayedDescriptor = desc;

            SwingUtilities.invokeLater(new Runnable()
            {
              @Override
              public void run()
              {
                if (application.isFinished() && !getCurrentStep().isFinishedStep())
                {
                  setCurrentStep(application.getFinishedStep());
                }
                getDialog().displayProgress(desc);
              }
            });
          }
          doPool = desc != lastDescriptor;
        }
      }
    }
  }

  /** Method called when user clicks 'Next' button of the wizard. */
  private void nextClicked()
  {
    final WizardStep cStep = getCurrentStep();
    application.nextClicked(cStep, this);
    BackgroundTask<?> worker = new NextClickedBackgroundTask(cStep);
    getDialog().workerStarted();
    worker.startBackgroundTask();
  }

  private void updateUserData(final WizardStep cStep)
  {
    BackgroundTask<?> worker = new BackgroundTask<Object>()
    {
      @Override
      public Object processBackgroundTask() throws UserDataException
      {
        try
        {
          application.updateUserData(cStep, QuickSetup.this);
        }
        catch (UserDataException uide)
        {
          throw uide;
        }
        catch (Throwable t)
        {
          throw new UserDataException(cStep, getThrowableMsg(INFO_BUG_MSG.get(), t));
        }
        return null;
      }

      @Override
      public void backgroundTaskCompleted(Object returnValue, Throwable throwable)
      {
        getDialog().workerFinished();

        if (throwable != null)
        {
          UserDataException ude = (UserDataException) throwable;
          if (ude instanceof UserDataConfirmationException)
          {
            if (displayConfirmation(ude.getMessageObject(), INFO_CONFIRMATION_TITLE.get()))
            {
              try
              {
                setCurrentStep(application.getNextWizardStep(cStep));
              }
              catch (Throwable t)
              {
                t.printStackTrace();
              }
            }
          }
          else
          {
            displayError(ude.getMessageObject(), INFO_ERROR_TITLE.get());
          }
        }
        else
        {
          setCurrentStep(application.getNextWizardStep(cStep));
        }
        if (currentStep.isProgressStep())
        {
          launch();
        }
      }
    };
    getDialog().workerStarted();
    worker.startBackgroundTask();
  }

  /** Method called when user clicks 'Finish' button of the wizard. */
  private void finishClicked()
  {
    final WizardStep cStep = getCurrentStep();
    if (application.finishClicked(cStep, this))
    {
      updateUserData(cStep);
    }
  }

  /** Method called when user clicks 'Previous' button of the wizard. */
  private void previousClicked()
  {
    WizardStep cStep = getCurrentStep();
    application.previousClicked(cStep, this);
    setCurrentStep(application.getPreviousWizardStep(cStep));
  }

  /** Method called when user clicks 'Quit' button of the wizard. */
  private void quitClicked()
  {
    application.quitClicked(getCurrentStep(), this);
  }

  /**
   * Method called when user clicks 'Continue' button in the case where there is
   * something installed.
   */
  private void continueInstallClicked()
  {
    // TODO:  move this stuff to Installer?
    application.forceToDisplay();
    getDialog().forceToDisplay();
    setCurrentStep(Step.WELCOME);
  }

  /** Method called when user clicks 'Close' button of the wizard. */
  private void closeClicked()
  {
    application.closeClicked(getCurrentStep(), this);
  }

  private void launchStatusPanelClicked()
  {
    BackgroundTask<Object> worker = new BackgroundTask<Object>()
    {
      @Override
      public Object processBackgroundTask() throws UserDataException
      {
        try
        {
          final Installation installation = Installation.getLocal();
          final ProcessBuilder pb;

          if (isMacOS())
          {
            List<String> cmd = new ArrayList<>();
            cmd.add(MAC_APPLICATIONS_OPENER);
            cmd.add(getScriptPath(getPath(installation.getControlPanelCommandFile())));
            pb = new ProcessBuilder(cmd);
          }
          else
          {
            pb = new ProcessBuilder(getScriptPath(getPath(installation.getControlPanelCommandFile())));
          }

          Map<String, String> env = pb.environment();
          env.put(SetupUtils.OPENDJ_JAVA_HOME, System.getProperty("java.home"));
          final Process process = pb.start();
          // Wait for 3 seconds. Assume that if the process has not exited everything went fine.
          int returnValue = 0;
          try
          {
            Thread.sleep(3000);
          }
          catch (Throwable t) {}

          try
          {
            returnValue = process.exitValue();
          }
          catch (IllegalThreadStateException e)
          {
            // The process has not exited: assume that the status panel could be launched successfully.
          }

          if (returnValue != 0)
          {
            throw new Error(INFO_COULD_NOT_LAUNCH_CONTROL_PANEL_MSG.get().toString());
          }
        }
        catch (Throwable t)
        {
          // This looks like a bug
          t.printStackTrace();
          throw new Error(INFO_COULD_NOT_LAUNCH_CONTROL_PANEL_MSG.get().toString());
        }

        return null;
      }

      @Override
      public void backgroundTaskCompleted(Object returnValue, Throwable throwable)
      {
        getDialog().getFrame().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        if (throwable != null)
        {
          displayError(LocalizableMessage.raw(throwable.getMessage()), INFO_ERROR_TITLE.get());
        }
      }
    };
    getDialog().getFrame().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    worker.startBackgroundTask();
  }

  /**
   * This method tries to update the visibility of the steps panel. The contents
   * are updated because the user clicked in one of the buttons that could make
   * the steps panel to change.
   */
  private void inputPanelButtonClicked()
  {
    getDialog().getStepsPanel().updateStepVisibility(this);
  }

  /**
   * Method called when we want to quit the setup (for instance when the user
   * clicks on 'Close' or 'Quit' buttons and has confirmed that (s)he wants to
   * quit the program.
   */
  public void quit()
  {
    logger.info(LocalizableMessage.raw("quitting application"));
    flushLogs();
    System.exit(0);
  }

  private void flushLogs()
  {
    java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(logger.getName());
    Handler[] handlers = julLogger.getHandlers();
    if (handlers != null)
    {
      for (Handler h : handlers)
      {
        h.flush();
      }
    }
  }

  /** Launch the QuickSetup application Open DS. */
  public void launch()
  {
    application.addProgressUpdateListener(this);
    new Thread(application, "Application Thread").start();
    Thread t = new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        runDisplayUpdater();
        WizardStep ws = application.getCurrentWizardStep();
        getDialog().getButtonsPanel().updateButtons(ws);
      }
    });
    t.start();
  }

  /**
   * Get the current step.
   *
   * @return the currently displayed Step of the wizard.
   */
  private WizardStep getCurrentStep()
  {
    return currentStep;
  }

  /**
   * Set the current step. This will basically make the required calls in the
   * dialog to display the panel that corresponds to the step passed as
   * argument.
   *
   * @param step
   *          The step to be displayed.
   */
  public void setCurrentStep(WizardStep step)
  {
    if (step == null)
    {
      throw new NullPointerException("step is null");
    }
    currentStep = step;
    application.setDisplayedWizardStep(step, application.getUserData(), getDialog());
  }

  /**
   * Get the dialog that is displayed.
   *
   * @return the dialog.
   */
  public QuickSetupDialog getDialog()
  {
    if (dialog == null)
    {
      dialog = new QuickSetupDialog(application, installStatus, this);
      dialog.addButtonActionListener(this);
      application.setQuickSetupDialog(dialog);
    }
    return dialog;
  }

  /**
   * Displays an error message dialog.
   *
   * @param msg
   *          the error message.
   * @param title
   *          the title for the dialog.
   */
  public void displayError(LocalizableMessage msg, LocalizableMessage title)
  {
    if (isCli())
    {
      System.err.println(msg);
    }
    else
    {
      getDialog().displayError(msg, title);
    }
  }

  /**
   * Displays a confirmation message dialog.
   *
   * @param msg
   *          the confirmation message.
   * @param title
   *          the title of the dialog.
   * @return <CODE>true</CODE> if the user confirms the message, or
   *         <CODE>false</CODE> if not.
   */
  public boolean displayConfirmation(LocalizableMessage msg, LocalizableMessage title)
  {
    return getDialog().displayConfirmation(msg, title);
  }

  /**
   * Gets the string value for a given field name.
   *
   * @param fieldName
   *          the field name object.
   * @return the string value for the field name.
   */
  public String getFieldStringValue(FieldName fieldName)
  {
    final Object value = getFieldValue(fieldName);
    if (value != null)
    {
      return String.valueOf(value);
    }

    return null;
  }

  /**
   * Gets the value for a given field name.
   *
   * @param fieldName
   *          the field name object.
   * @return the value for the field name.
   */
  public Object getFieldValue(FieldName fieldName)
  {
    return getDialog().getFieldValue(fieldName);
  }

  /**
   * Marks the fieldName as valid or invalid depending on the value of the
   * invalid parameter. With the current implementation this implies basically
   * using a red color in the label associated with the fieldName object. The
   * color/style used to mark the label invalid is specified in UIFactory.
   *
   * @param fieldName
   *          the field name object.
   * @param invalid
   *          whether to mark the field valid or invalid.
   */
  public void displayFieldInvalid(FieldName fieldName, boolean invalid)
  {
    getDialog().displayFieldInvalid(fieldName, invalid);
  }

  /** A method to initialize the look and feel. */
  private void initLookAndFeel() throws Throwable
  {
    UIFactory.initialize();
  }

  /**
   * A methods that creates an ProgressDescriptor based on the value of a
   * ProgressUpdateEvent.
   *
   * @param ev
   *          the ProgressUpdateEvent used to generate the ProgressDescriptor.
   * @return the ProgressDescriptor.
   */
  private ProgressDescriptor createProgressDescriptor(ProgressUpdateEvent ev)
  {
    ProgressStep status = ev.getProgressStep();
    LocalizableMessage newProgressLabel = ev.getCurrentPhaseSummary();
    LocalizableMessage additionalDetails = ev.getNewLogs();
    Integer ratio = ev.getProgressRatio();

    if (additionalDetails != null)
    {
      progressDetails.append(additionalDetails);
    }
    /*
     * Note: progressDetails might have a certain number of characters that
     * break LocalizableMessage Formatter (for instance percentages).
     * When fix for issue 2142 was committed it broke this code.
     * So here we use LocalizableMessage.raw instead of calling directly progressDetails.toMessage
     */
    return new ProgressDescriptor(status, ratio, newProgressLabel, LocalizableMessage.raw(progressDetails.toString()));
  }

  /** This is a class used when the user clicks on next and that extends BackgroundTask. */
  private class NextClickedBackgroundTask extends BackgroundTask<Object>
  {
    private WizardStep cStep;

    public NextClickedBackgroundTask(WizardStep cStep)
    {
      this.cStep = cStep;
    }

    @Override
    public Object processBackgroundTask() throws UserDataException
    {
      try
      {
        application.updateUserData(cStep, QuickSetup.this);
        return null;
      }
      catch (UserDataException uide)
      {
        throw uide;
      }
      catch (Throwable t)
      {
        throw new UserDataException(cStep, getThrowableMsg(INFO_BUG_MSG.get(), t));
      }
    }

    @Override
    public void backgroundTaskCompleted(Object returnValue, Throwable throwable)
    {
      getDialog().workerFinished();

      if (throwable != null)
      {
        if (!(throwable instanceof UserDataException))
        {
          logger.warn(LocalizableMessage.raw("Unhandled exception.", throwable));
        }
        else
        {
          UserDataException ude = (UserDataException) throwable;
          if (ude instanceof UserDataConfirmationException)
          {
            if (displayConfirmation(ude.getMessageObject(), INFO_CONFIRMATION_TITLE.get()))
            {
              setCurrentStep(application.getNextWizardStep(cStep));
            }
          }
          else if (ude instanceof UserDataCertificateException)
          {
            final UserDataCertificateException ce = (UserDataCertificateException) ude;
            CertificateDialog dlg = new CertificateDialog(getDialog().getFrame(), ce);
            dlg.pack();
            dlg.setVisible(true);
            CertificateDialog.ReturnType answer = dlg.getUserAnswer();
            if (answer != CertificateDialog.ReturnType.NOT_ACCEPTED)
            {
              // Retry the click but now with the certificate accepted.
              final boolean acceptPermanently = answer == CertificateDialog.ReturnType.ACCEPTED_PERMANENTLY;
              application.acceptCertificateForException(ce, acceptPermanently);
              application.nextClicked(cStep, QuickSetup.this);
              BackgroundTask<Object> worker = new NextClickedBackgroundTask(cStep);
              getDialog().workerStarted();
              worker.startBackgroundTask();
            }
          }
          else
          {
            displayError(ude.getMessageObject(), INFO_ERROR_TITLE.get());
          }
        }
      }
      else
      {
        setCurrentStep(application.getNextWizardStep(cStep));
      }
    }
  }
}
