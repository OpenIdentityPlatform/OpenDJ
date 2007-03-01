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

package org.opends.quicksetup.ui;

import java.awt.event.ComponentListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashSet;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.opends.quicksetup.ButtonName;
import org.opends.quicksetup.CurrentInstallStatus;
import org.opends.quicksetup.Step;
import org.opends.quicksetup.event.ButtonActionListener;
import org.opends.quicksetup.event.ButtonEvent;
import org.opends.quicksetup.event.MinimumSizeComponentListener;
import org.opends.quicksetup.i18n.ResourceProvider;
import org.opends.quicksetup.installer.FieldName;
import org.opends.quicksetup.installer.InstallProgressDescriptor;
import org.opends.quicksetup.installer.InstallProgressStep;
import org.opends.quicksetup.installer.UserInstallData;
import org.opends.quicksetup.uninstaller.UninstallProgressDescriptor;
import org.opends.quicksetup.uninstaller.UninstallProgressStep;
import org.opends.quicksetup.util.ProgressMessageFormatter;
import org.opends.quicksetup.util.Utils;

/**
 * This class represents the dialog used by installer and the uninstaller.
 *
 * In its constructor it gets as parameters an object describing the current
 * installation status and the default values to be proposed to the user
 * in the panels.
 *
 * If we are installing Open DS and the server has already been installed it
 * will display an error message.  In the other cases it will display a wizard
 * (that is different depending on whether we are installing or uninstalling).
 *
 */
public class QuickSetupDialog
{
  private JFrame frame;

  private QuickSetupErrorPanel installedPanel;

  private JPanel framePanel;

  private StepsPanel stepsPanel;

  private CurrentStepPanel currentStepPanel;

  private ButtonsPanel buttonsPanel;

  private Step displayedStep;

  private UserInstallData defaultUserData;

  private CurrentInstallStatus installStatus;

  private HashSet<ButtonActionListener> buttonListeners =
      new HashSet<ButtonActionListener>();

  private boolean forceToDisplaySetup;

  /**
   * Constructor of QuickSetupDialog.
   * @param defaultUserData the default values to be proposed to the user in
   * the wizard.
   * @param installStatus the current installation status.
   */
  public QuickSetupDialog(UserInstallData defaultUserData,
      CurrentInstallStatus installStatus)
  {
    this.defaultUserData = defaultUserData;
    this.installStatus = installStatus;

    if (isUninstall())
    {
      frame = new JFrame(getMsg("frame-uninstall-title"));
    } else
    {
      frame = new JFrame(getMsg("frame-install-title"));
    }

    frame.getContentPane().add(getFramePanel());
    frame.addWindowListener(new WindowAdapter()
    {
      public void windowClosing(WindowEvent e)
      {
        if (isUninstall())
        {
          if (getDisplayedStep() == Step.PROGRESS)
          {
            // Simulate a close button event
            notifyButtonEvent(ButtonName.CLOSE);
          } else
          {
            // Simulate a quit button event
            notifyButtonEvent(ButtonName.QUIT);
          }
        } else if (isInstalled() && !forceToDisplaySetup)
        {
          // Simulate a close button event
          notifyButtonEvent(ButtonName.QUIT);
        } else
        {
          if (getDisplayedStep() == Step.PROGRESS)
          {
            // Simulate a close button event
            notifyButtonEvent(ButtonName.CLOSE);
          } else
          {
            // Simulate a quit button event
            notifyButtonEvent(ButtonName.QUIT);
          }
        }
      }
    });
    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    UIFactory.IconType ic;
    if (Utils.isMacOS())
    {
      ic = UIFactory.IconType.MINIMIZED_MAC;
    } else
    {
      ic = UIFactory.IconType.MINIMIZED;
    }
    frame.setIconImage(UIFactory.getImageIcon(ic).getImage());
  }

  /**
   * Packs and displays this dialog.
   *
   */
  public void packAndShow()
  {
    frame.pack();
    int minWidth = (int) frame.getPreferredSize().getWidth();
    int minHeight = (int) frame.getPreferredSize().getHeight();
    Utils.centerOnScreen(frame);
    if (isUninstall())
    {
      setFocusOnButton(ButtonName.FINISH);
    } else if (!isInstalled() || forceToDisplaySetup)
    {
      setFocusOnButton(ButtonName.NEXT);
    } else
    {
      if (installStatus.canOverwriteCurrentInstall())
      {
        setFocusOnButton(ButtonName.CONTINUE_INSTALL);
      }
      else
      {
        setFocusOnButton(ButtonName.QUIT);
      }
    }

    frame.addComponentListener(new MinimumSizeComponentListener(frame,
        minWidth, minHeight));

    frame.setVisible(true);
  }

  /**
   * This method is called when we detected that there is something installed
   * we inform of this to the user and the user wants to proceed with the
   * installation destroying the contents of the data and the configuration
   * in the current installation.
   *
   */
  public void forceToDisplaySetup()
  {
    forceToDisplaySetup = true;
    frame.getContentPane().removeAll();
    frame.getContentPane().add(getFramePanel());
    frame.pack();
    Utils.centerOnScreen(frame);
    setFocusOnButton(ButtonName.NEXT);
    int minWidth = (int) frame.getPreferredSize().getWidth();
    int minHeight = (int) frame.getPreferredSize().getHeight();

    ComponentListener[] listeners = frame.getComponentListeners();
    for (int i=0; i<listeners.length; i++)
    {
      if (listeners[i] instanceof MinimumSizeComponentListener)
      {
        frame.removeComponentListener(listeners[i]);
      }
    }
    frame.addComponentListener(new MinimumSizeComponentListener(frame,
        minWidth, minHeight));
  }

  /**
   * Displays the panel corresponding to the provided step.  The panel contents
   * are updated with the contents of the UserInstallData object.
   * @param step the step that we want to display.
   * @param userData the UserInstallData object that must be used to populate
   * the panels.
   */
  public void setDisplayedStep(Step step, UserInstallData userData)
  {
    displayedStep = step;
    if (isUninstall())
    {
      // First call the panels to do the required updates on their layout
      getButtonsPanel().setDisplayedStep(step);
      getStepsPanel().setDisplayedStep(step);
      getCurrentStepPanel().setDisplayedStep(step, userData);

      // Set the default button for the frame
      switch (step)
      {
      case CONFIRM_UNINSTALL:
        setDefaultButton(ButtonName.FINISH);
        break;

      case PROGRESS:
        setDefaultButton(ButtonName.CLOSE);
        break;
      }

      // Set the focus for the current step
      switch (step)
      {
      case CONFIRM_UNINSTALL:
        setFocusOnButton(ButtonName.FINISH);
        break;

      case PROGRESS:
        setFocusOnButton(ButtonName.CLOSE);
        setButtonEnabled(ButtonName.CLOSE, false);
        break;
      }
    } else if (!isInstalled() || forceToDisplaySetup)
    {
      // First call the panels to do the required updates on their layout
      getButtonsPanel().setDisplayedStep(step);
      getStepsPanel().setDisplayedStep(step);
      getCurrentStepPanel().setDisplayedStep(step, userData);

      // Set the default button for the frame
      switch (step)
      {
      case REVIEW:
        setDefaultButton(ButtonName.FINISH);
        break;

      case PROGRESS:
        setDefaultButton(ButtonName.CLOSE);
        break;

      default:
        setDefaultButton(ButtonName.NEXT);
      }

      // Set the focus for the current step
      switch (step)
      {
      case WELCOME:
        setFocusOnButton(ButtonName.NEXT);
        break;

      case SERVER_SETTINGS:
        // The focus is set by the panel itself
        break;

      case DATA_OPTIONS:
        // The focus is set by the panel itself
        break;

      case REVIEW:
        setFocusOnButton(ButtonName.FINISH);
        break;

      case PROGRESS:
        setFocusOnButton(ButtonName.CLOSE);
        setButtonEnabled(ButtonName.CLOSE, false);
        break;
      }
    }
  }

  /**
   * Returns the currently displayed step.
   * @return the currently displayed step.
   */
  public Step getDisplayedStep()
  {
    return displayedStep;
  }

  /**
   * Forwards to the displayed panel the InstallProgressDescriptor so that they
   * can update their contents accordingly.
   * @param descriptor the descriptor of the Installation progress.
   */
  public void displayProgress(InstallProgressDescriptor descriptor)
  {
    getCurrentStepPanel().displayProgress(descriptor);
    InstallProgressStep status = descriptor.getProgressStep();
    if ((status == InstallProgressStep.FINISHED_SUCCESSFULLY)
        || (status == InstallProgressStep.FINISHED_WITH_ERROR))
    {
      setButtonEnabled(ButtonName.CLOSE, true);
    }
  }

  /**
   * Forwards to the displayed panel the UninstallProgressDescriptor so that
   * they can update their contents accordingly.
   * @param descriptor the descriptor of the Uninstallation progress.
   */
  public void displayProgress(UninstallProgressDescriptor descriptor)
  {
    getCurrentStepPanel().displayProgress(descriptor);
    UninstallProgressStep status = descriptor.getProgressStep();
    if ((status == UninstallProgressStep.FINISHED_SUCCESSFULLY)
        || (status == UninstallProgressStep.FINISHED_WITH_ERROR))
    {
      setButtonEnabled(ButtonName.CLOSE, true);
    }
  }

  /**
   * Displays an error message dialog.
   *
   * @param msg
   *          the error message.
   * @param title
   *          the title for the dialog.
   */
  public void displayError(String msg, String title)
  {
    Utils.displayError(getFrame(), msg, title);
  }

  /**
   * Displays a confirmation message dialog.
   *
   * @param msg
   *          the confirmation message.
   * @param title
   *          the title of the dialog.
   * @return <CODE>true</CODE> if the user confirms the message, or
   * <CODE>false</CODE> if not.
   */
  public boolean displayConfirmation(String msg, String title)
  {
    return Utils.displayConfirmation(getFrame(), msg, title);
  }

  /**
   * Returns the value corresponding to the provided FieldName.
   * @param fieldName the FieldName for which we want to obtain the value.
   * @return the value corresponding to the provided FieldName.
   */
  public Object getFieldValue(FieldName fieldName)
  {
    return getCurrentStepPanel().getFieldValue(fieldName);
  }

  /**
   * Return the progress message formatter that will be used in the dialog
   * to display the messages.
   * @return the progress message formatter that will be used in the dialog
   * to display the messages.
   */
  public ProgressMessageFormatter getFormatter()
  {
    return getCurrentStepPanel().getProgressPanel().getFormatter();
  }

  /**
   * Marks as invalid (or valid depending on the value of the invalid parameter)
   * a field corresponding to FieldName.  This basically implies udpating the
   * style of the JLabel associated with fieldName (the association is done
   * using the LabelFieldDescriptor class).
   * @param fieldName the FieldName to be marked as valid or invalid.
   * @param invalid whether to mark the field as valid or invalid.
   */
  public void displayFieldInvalid(FieldName fieldName, boolean invalid)
  {
    getCurrentStepPanel().displayFieldInvalid(fieldName, invalid);
  }

  /**
   * Adds a button listener.  All the button listeners will be notified when
   * the buttons are clicked (by the user or programatically).
   * @param l the ButtonActionListener to be added.
   */
  public void addButtonActionListener(ButtonActionListener l)
  {
    getButtonsPanel().addButtonActionListener(l);
    getInstalledPanel().addButtonActionListener(l);
    getButtonsPanel().addButtonActionListener(l);
    getCurrentStepPanel().addButtonActionListener(l);

    buttonListeners.add(l);
  }

  /**
   * Removes a button listener.
   * @param l the ButtonActionListener to be removed.
   */
  public void removeButtonActionListener(ButtonActionListener l)
  {
    if (isUninstall())
    {
      getButtonsPanel().removeButtonActionListener(l);
    } else if (isInstalled() && !forceToDisplaySetup)
    {
      getInstalledPanel().removeButtonActionListener(l);
    } else
    {
      getButtonsPanel().removeButtonActionListener(l);
    }
    buttonListeners.remove(l);
  }

  /**
   * This method is called to inform that a worker has started (the QuickSetup
   * is doing some data validation).  The worker is doing its tasks outside
   * the event thread to avoid blocking of the painting and this class is
   * notified of this fact.  The method basically simply the Next and Previous
   * buttons.
   *
   * This method can be called from the event thread or outside the event
   * thread.
   *
   */
  public void workerStarted()
  {
    Runnable r = new Runnable()
    {
      public void run()
      {
        // TODO: here we could have an animated gif.
        setButtonEnabled(ButtonName.NEXT, false);
        setButtonEnabled(ButtonName.PREVIOUS, false);
        setButtonEnabled(ButtonName.FINISH, false);
      }
    };
    runOnEventThread(r);
  }

  /**
   * This method is called to inform that a worker has finished. The method just
   * enables the Next and Previous buttons.
   *
   * This method can be called from the event thread or outside the event
   * thread.
   *
   */
  public void workerFinished()
  {
    Runnable r = new Runnable()
    {
      public void run()
      {
        // TO COMPLETE: here we could have an animated gif.
        setButtonEnabled(ButtonName.NEXT, true);
        setButtonEnabled(ButtonName.PREVIOUS, true);
        setButtonEnabled(ButtonName.FINISH, true);
      }
    };
    runOnEventThread(r);
  }

  /**
   * Notification from the worker with a message.
   * @param msg the message sent by the worker.
   */
  public void workerMessage(String msg)
  {
    // TODO For the moment not used.
  }

  /**
   * Notification telling that the installation/uninstallation is finished.
   * @param successful a boolean telling whether the setup was successful or
   * not.
   */
  public void finished(boolean successful)
  {
    setButtonEnabled(ButtonName.CLOSE, true);
    if (!successful)
    {
      // Do nothing... all the error messages
    }
  }

  /**
   * Returns the frame containing the dialog.
   * @return the frame containing the dialog.
   */
  public JFrame getFrame()
  {
    return frame;
  }

  /**
   * Enables a button associated with the given Button Name.
   * @param buttonName the button name of the button.
   * @param enable boolean indicating to enable or to disable the button.
   */
  private void setButtonEnabled(ButtonName buttonName, boolean enable)
  {
    getButton(buttonName).setEnabled(enable);
  }

  /**
   * Returns the panel of the dialog.
   * @return the panel of the dialog.
   */
  private JPanel getFramePanel()
  {
    JPanel p;
    if (isUninstall())
    {
      if (framePanel == null)
      {
        framePanel =
            new FramePanel(getStepsPanel(), getCurrentStepPanel(),
                getButtonsPanel());
      }
      p = framePanel;
    } else if (isInstalled() && !forceToDisplaySetup)
    {
      p = getInstalledPanel();
    } else
    {
      if (framePanel == null)
      {
        framePanel =
            new FramePanel(getStepsPanel(), getCurrentStepPanel(),
                getButtonsPanel());
      }
      p = framePanel;
    }
    return p;
  }

  /**
   * Returns the steps panel.
   * @return the steps panel.
   */
  private StepsPanel getStepsPanel()
  {
    if (stepsPanel == null)
    {
      stepsPanel = new StepsPanel(isUninstall());
    }
    return stepsPanel;
  }

  /**
   * Returns the current step panel.
   * @return the current step panel.
   */
  private CurrentStepPanel getCurrentStepPanel()
  {
    if (currentStepPanel == null)
    {
      currentStepPanel = new CurrentStepPanel(defaultUserData, installStatus,
          isUninstall());
    }
    return currentStepPanel;
  }


  /**
   * Returns the buttons panel.
   * @return the buttons panel.
   */
  private ButtonsPanel getButtonsPanel()
  {
    if (buttonsPanel == null)
    {
      buttonsPanel = new ButtonsPanel();
    }
    return buttonsPanel;
  }

  /* Different commodity methods to retrieve localized messages */
  private String getMsg(String key)
  {
    return getI18n().getMsg(key);
  }

  private ResourceProvider getI18n()
  {
    return ResourceProvider.getInstance();
  }

  /**
   * Returns the button corresponding to the buttonName.
   * @param buttonName the ButtonName for which we want to get the button.
   * @return the button corresponding to the buttonName.
   */
  private JButton getButton(ButtonName buttonName)
  {
    JButton button;
    if (isUninstall())
    {
      button = getButtonsPanel().getButton(buttonName);
    } else if (isInstalled() && !forceToDisplaySetup)
    {
      if (buttonName == ButtonName.QUIT)
      {
        button = getInstalledPanel().getQuitButton();
      } else if (buttonName == ButtonName.CONTINUE_INSTALL)
      {
        button = getInstalledPanel().getContinueInstallButton();
      } else
      {
        throw new IllegalStateException("Invalid button name " + buttonName
            + " if server is installed");
      }
    } else
    {
      button = getButtonsPanel().getButton(buttonName);
    }
    return button;
  }

  /**
   * Sets the focus in the button associated with the ButtonName.
   * @param buttonName the ButtonName associated with the button.
   */
  private void setFocusOnButton(ButtonName buttonName)
  {
    getButton(buttonName).requestFocusInWindow();
  }

  /**
   * Sets the default button for the frame.
   * @param buttonName the ButtonName associated with the button.
   */
  private void setDefaultButton(ButtonName buttonName)
  {
    getFrame().getRootPane().setDefaultButton(getButton(buttonName));
  }

  /**
   * Method used to execute a Runnable in the event thread.  If we are in the
   * event thread it will be called synchronously and if we are not it will
   * be executed asynchronously.
   *
   * @param r the Runnable to be executed.
   */
  private void runOnEventThread(Runnable r)
  {
    if (SwingUtilities.isEventDispatchThread())
    {
      r.run();
    } else
    {
      SwingUtilities.invokeLater(r);
    }
  }

  /**
   * Returns <CODE>true</CODE> if this is an uninstallation and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if this is an uninstallation and
   * <CODE>false</CODE> otherwise.
   */
  private boolean isUninstall()
  {
    return Utils.isUninstall();
  }


  /**
   * Returns <CODE>true</CODE> if the server is already installed and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the server is already installed and
   * <CODE>false</CODE> otherwise.
   */
  private boolean isInstalled()
  {
    return installStatus.isInstalled();
  }

  /**
   * Returns (and creates if it is not already created) the panel that
   * informs the user that the server is already installed when the
   * installation has been launched.
   * @return the panel that is used
   * to inform the user that the server is already installed when the
   * installation has been launched.
   */
  private QuickSetupErrorPanel getInstalledPanel()
  {
    if (installedPanel == null)
    {
      installedPanel = new QuickSetupErrorPanel(installStatus);
    }
    return installedPanel;
  }

  /**
   * Notifies the ButtonActionListener objects that an ButtonEvent has occurred
   * in the button associated with buttonName.
   * @param buttonName the ButtonName associated with the button.
   */
  private void notifyButtonEvent(ButtonName buttonName)
  {
    ButtonEvent be = new ButtonEvent(this, buttonName);
    for (ButtonActionListener li : buttonListeners)
    {
      li.buttonActionPerformed(be);
    }
  }
}
