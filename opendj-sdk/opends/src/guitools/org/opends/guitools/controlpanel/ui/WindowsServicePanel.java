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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.tools.ConfigureWindowsService;

/**
 * The panel that displays the Windows Service panel configuration for the
 * server.
 *
 */
public class WindowsServicePanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 6415350296295459469L;
  private JLabel lState;
  private JButton bEnable;
  private JButton bDisable;

  private boolean isWindowsServiceEnabled;

  /**
   * Default constructor.
   *
   */
  public WindowsServicePanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_WINDOWS_SERVICE_TITLE.get();
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.WEST;
    gbc.weightx = 0.0;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 4;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.BOTH;

    String text = INFO_CTRL_PANEL_WINDOWS_SERVICE_PANEL_TEXT.get().toString();

    JEditorPane pane = Utilities.makeHtmlPane(text,
        ColorAndFontConstants.defaultFont);

    Utilities.updatePreferredSize(pane, 100, text,
        ColorAndFontConstants.defaultFont, false);
    gbc.weighty = 0.0;
    add(pane, gbc);

    gbc.gridy = 1;
    gbc.gridwidth = 1;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    JLabel lWindowsService =Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_WINDOWS_SERVICE_INTEGRATION_LABEL.get());
    gbc.insets.top = 10;
    add(lWindowsService, gbc);
    lState = Utilities.createDefaultLabel();
    lState.setText(isWindowsServiceEnabled ?
        INFO_ENABLED_LABEL.get().toString() :
          INFO_DISABLED_LABEL.get().toString());
    gbc.insets.left = 10;
    gbc.gridx = 1;
    add(lState, gbc);

    bEnable = Utilities.createButton(
        INFO_CTRL_PANEL_ENABLE_WINDOWS_SERVICE_BUTTON.get());
    bDisable = Utilities.createButton(
        INFO_CTRL_PANEL_DISABLE_WINDOWS_SERVICE_BUTTON.get());
    bEnable.setOpaque(false);
    bDisable.setOpaque(false);
    int maxWidth = Math.max(bEnable.getPreferredSize().width,
        bDisable.getPreferredSize().width);
    int maxHeight = Math.max(bEnable.getPreferredSize().height,
        bDisable.getPreferredSize().height);
    bEnable.setPreferredSize(new Dimension(maxWidth, maxHeight));
    bDisable.setPreferredSize(new Dimension(maxWidth, maxHeight));

    ActionListener listener = new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        updateWindowsService();
      }
    };
    bEnable.addActionListener(listener);
    bDisable.addActionListener(listener);

    gbc.gridx = 2;
    add(bEnable, gbc);
    add(bDisable, gbc);

    gbc.weightx = 1.0;
    gbc.gridx = 3;
    add(Box.createHorizontalGlue(), gbc);

    bEnable.setVisible(!isWindowsServiceEnabled);
    bDisable.setVisible(isWindowsServiceEnabled);
    addBottomGlue(gbc);
  }

  /**
   * {@inheritDoc}
   */
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.CLOSE;
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    if (!isWindowsServiceEnabled)
    {
      return bEnable;
    }
    else
    {
      return bDisable;
    }
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    boolean previousValue = isWindowsServiceEnabled;
    isWindowsServiceEnabled = ev.getNewDescriptor().isWindowsServiceEnabled();
    if (isWindowsServiceEnabled != previousValue)
    {
      lState.setText(isWindowsServiceEnabled ?
          INFO_ENABLED_LABEL.get().toString() :
            INFO_DISABLED_LABEL.get().toString());
      bEnable.setVisible(!isWindowsServiceEnabled);
      bDisable.setVisible(isWindowsServiceEnabled);
    }
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    // NO ok button
  }

  private void updateWindowsService()
  {
    LinkedHashSet<Message> errors = new LinkedHashSet<Message>();
    ProgressDialog progressDialog = new ProgressDialog(
        Utilities.getParentDialog(this), getTitle(), getInfo());
    WindowsServiceTask newTask = new WindowsServiceTask(getInfo(),
        progressDialog, !isWindowsServiceEnabled);
    for (Task task : getInfo().getTasks())
    {
      task.canLaunch(newTask, errors);
    }
    if (errors.isEmpty())
    {
      if (isWindowsServiceEnabled)
      {
        launchOperation(newTask,
            INFO_CTRL_PANEL_DISABLING_WINDOWS_SERVICE_SUMMARY.get(),
            INFO_CTRL_PANEL_DISABLING_WINDOWS_SERVICE_SUCCESSFUL_SUMMARY.get(),
            INFO_CTRL_PANEL_DISABLING_WINDOWS_SERVICE_SUCCESSFUL_DETAILS.get(),
            ERR_CTRL_PANEL_DISABLING_WINDOWS_SERVICE_ERROR_SUMMARY.get(),
            null,
            ERR_CTRL_PANEL_DISABLING_WINDOWS_SERVICE_ERROR_DETAILS,
            progressDialog);
      }
      else
      {
        launchOperation(newTask,
            INFO_CTRL_PANEL_ENABLING_WINDOWS_SERVICE_SUMMARY.get(),
            INFO_CTRL_PANEL_ENABLING_WINDOWS_SERVICE_SUCCESSFUL_SUMMARY.get(),
            INFO_CTRL_PANEL_ENABLING_WINDOWS_SERVICE_SUCCESSFUL_DETAILS.get(),
            ERR_CTRL_PANEL_ENABLING_WINDOWS_SERVICE_ERROR_SUMMARY.get(),
            null,
            ERR_CTRL_PANEL_ENABLING_WINDOWS_SERVICE_ERROR_DETAILS,
            progressDialog);
      }
      progressDialog.setVisible(true);
    }
    else
    {
      displayErrorDialog(errors);
    }
  }

  /**
   * The task in charge of updating the windows service configuration.
   *
   */
  protected class WindowsServiceTask extends Task
  {
    Set<String> backendSet;
    private boolean enableService;
    /**
     * The constructor of the task.
     * @param info the control panel info.
     * @param dlg the progress dialog that shows the progress of the task.
     * @param enableService whether the windows service must be enabled or
     * disabled.
     */
    public WindowsServiceTask(ControlPanelInfo info, ProgressDialog dlg,
        boolean enableService)
    {
      super(info, dlg);
      this.enableService = enableService;
      backendSet = new HashSet<String>();
    }

    /**
     * {@inheritDoc}
     */
    public Type getType()
    {
      if (enableService)
      {
        return Type.ENABLE_WINDOWS_SERVICE;
      }
      else
      {
        return Type.DISABLE_WINDOWS_SERVICE;
      }
    }

    /**
     * {@inheritDoc}
     */
    public Message getTaskDescription()
    {
      if (enableService)
      {
        return INFO_CTRL_PANEL_ENABLE_WINDOWS_SERVICE_TASK_DESCRIPTION.get();
      }
      else
      {
        return INFO_CTRL_PANEL_DISABLE_WINDOWS_SERVICE_TASK_DESCRIPTION.get();
      }
    }

    /**
     * {@inheritDoc}
     */
    public boolean canLaunch(Task taskToBeLaunched,
        Collection<Message> incompatibilityReasons)
    {
      boolean canLaunch = true;
      if (state == State.RUNNING)
      {
        if ((taskToBeLaunched.getType() == Type.ENABLE_WINDOWS_SERVICE) ||
            (taskToBeLaunched.getType() == Type.DISABLE_WINDOWS_SERVICE))
        {
          incompatibilityReasons.add(getIncompatibilityMessage(this,
              taskToBeLaunched));
          canLaunch = false;
        }
      }
      return canLaunch;
    }

    /**
     * {@inheritDoc}
     */
    public void runTask()
    {
      state = State.RUNNING;
      lastException = null;
      try
      {
        if (enableService)
        {
          returnCode = ConfigureWindowsService.enableService(outPrintStream,
              errorPrintStream);
          if ((returnCode != ConfigureWindowsService.SERVICE_ALREADY_ENABLED) &&
              (returnCode != ConfigureWindowsService.SERVICE_ENABLE_SUCCESS))
          {
            state = State.FINISHED_WITH_ERROR;
          }
          else
          {
            state = State.FINISHED_SUCCESSFULLY;
          }
        }
        else
        {
          returnCode = ConfigureWindowsService.disableService(outPrintStream,
              errorPrintStream);
          if ((returnCode != ConfigureWindowsService.SERVICE_ALREADY_DISABLED)
              &&
              (returnCode != ConfigureWindowsService.SERVICE_DISABLE_SUCCESS))
          {
            state = State.FINISHED_WITH_ERROR;
          }
          else
          {
            state = State.FINISHED_SUCCESSFULLY;
          }
        }
      }
      catch (Throwable t)
      {
        lastException = t;
        state = State.FINISHED_WITH_ERROR;
      }
    }

    /**
     * {@inheritDoc}
     */
    public Set<String> getBackends()
    {
      return backendSet;
    }

    /**
     * {@inheritDoc}
     */
    protected ArrayList<String> getCommandLineArguments()
    {
      ArrayList<String> args = new ArrayList<String>();

      if (enableService)
      {
        args.add("--enableService");
      }
      else
      {
        args.add("--disableService");
      }

      return args;
    }

    /**
     * {@inheritDoc}
     */
    protected String getCommandLinePath()
    {
      return getCommandLinePath("windows-service");
    }
  };
}
