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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.task;

import static org.opends.messages.AdminToolMessages.*;

import java.util.ArrayList;

import javax.swing.SwingUtilities;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.util.Utilities;
import org.forgerock.i18n.LocalizableMessage;

/** The task called when we want to restart the server. */
public class RestartServerTask extends StartStopTask
{
  private boolean starting;

  private StartServerTask startTask;

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   */
  public RestartServerTask(ControlPanelInfo info, ProgressDialog dlg)
  {
    super(info, dlg);
    startTask = new StartServerTask(info, dlg);
  }

  @Override
  public Type getType()
  {
    if (starting)
    {
      return Type.START_SERVER;
    }
    else
    {
      return Type.STOP_SERVER;
    }
  }

  @Override
  public LocalizableMessage getTaskDescription()
  {
    return INFO_CTRL_PANEL_RESTART_SERVER_TASK_DESCRIPTION.get();
  }

  @Override
  protected String getCommandLinePath()
  {
    return null;
  }

  /**
   * Returns the full path of the start command-line.
   * @return the full path of the start command-line.
   */
  private String getStartCommandLineName()
  {
    return startTask.getCommandLinePath();
  }

  /**
   * Returns the arguments of the start command-line.
   * @return the arguments of the start command-line.
   */
  private ArrayList<String> getStartCommandLineArguments()
  {
    return startTask.getCommandLineArguments();
  }

  /**
   * Returns the full path of the stop command-line.
   * @return the full path of the stop command-line.
   */
  private String getStopCommandLineName()
  {
    return getCommandLinePath("stop-ds");
  }

  @Override
  public void runTask()
  {
    state = State.RUNNING;
    starting = false;
    lastException = null;
    final ProgressDialog dlg = getProgressDialog();
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        String cmdLine = getStopCommandLineName();
        printEquivalentCommandLine(cmdLine, getCommandLineArguments(),
            INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_STOP_SERVER.get());
        dlg.setSummary(LocalizableMessage.raw(
            Utilities.applyFont(
            INFO_CTRL_PANEL_STOPPING_SERVER_SUMMARY.get(),
            ColorAndFontConstants.defaultFont)));
      }
    });
    // To display new status
    getInfo().regenerateDescriptor();
    getInfo().stopPooling();
    try
    {
      ArrayList<String> arguments = getCommandLineArguments();

      String[] args = new String[arguments.size()];

      arguments.toArray(args);
      returnCode = executeCommandLine(getStopCommandLineName(), args);

      if (returnCode != 0)
      {
        state = State.FINISHED_WITH_ERROR;
      }
      else
      {
        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
          public void run()
          {
            getProgressDialog().getProgressBar().setIndeterminate(false);
            dlg.getProgressBar().setValue(30);
            dlg.appendProgressHtml(Utilities.applyFont(
                "<b>"+INFO_CTRL_PANEL_SERVER_STOPPED.get()+"</b><br><br>",
                ColorAndFontConstants.progressFont));
            String cmdLine = getStartCommandLineName();
            printEquivalentCommandLine(cmdLine, getStartCommandLineArguments(),
                INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_START_SERVER.get());

            dlg.setSummary(LocalizableMessage.raw(
                Utilities.applyFont(
                INFO_CTRL_PANEL_STARTING_SERVER_SUMMARY.get(),
                ColorAndFontConstants.defaultFont)));
          }
        });

        starting = true;
        // To display new status
        getInfo().regenerateDescriptor();
        arguments = getStartCommandLineArguments();
        args = new String[arguments.size()];
        arguments.toArray(args);

        returnCode = executeCommandLine(getStartCommandLineName(), args);
        if (returnCode != 0)
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
    getInfo().startPooling();
  }
}
