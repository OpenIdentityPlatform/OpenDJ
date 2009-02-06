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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.task;

import static org.opends.messages.AdminToolMessages.*;

import java.util.ArrayList;

import javax.swing.SwingUtilities;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;

/**
 * The task called when we want to restart the server.
 *
 */
public class RestartServerTask extends StartStopTask
{
  private boolean starting;

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   */
  public RestartServerTask(ControlPanelInfo info, ProgressDialog dlg)
  {
    super(info, dlg);
  }

  /**
   * {@inheritDoc}
   */
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

  /**
   * {@inheritDoc}
   */
  public Message getTaskDescription()
  {
    return INFO_CTRL_PANEL_RESTART_SERVER_TASK_DESCRIPTION.get();
  }

  /**
   * {@inheritDoc}
   */
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
    return getCommandLinePath("start-ds");
  }

  /**
   * Returns the full path of the stop command-line.
   * @return the full path of the stop command-line.
   */
  private String getStopCommandLineName()
  {
    return getCommandLinePath("stop-ds");
  }

  /**
   * {@inheritDoc}
   */
  public void runTask()
  {
    state = State.RUNNING;
    starting = false;
    lastException = null;
    final ProgressDialog dlg = getProgressDialog();
    SwingUtilities.invokeLater(new Runnable()
    {
      public void run()
      {
        String cmdLine = getStopCommandLineName();
        dlg.setSummary(Message.raw(
            Utilities.applyFont(
            INFO_CTRL_PANEL_STOPPING_SERVER_SUMMARY.get().toString(),
            ColorAndFontConstants.defaultFont)));
        dlg.appendProgressHtml(Utilities.applyFont(
            INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_STOP_SERVER.get()+"<br><b>"+
              cmdLine+"</b><br><br>",
              ColorAndFontConstants.progressFont));
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
          public void run()
          {
            getProgressDialog().getProgressBar().setIndeterminate(false);
            dlg.getProgressBar().setValue(30);
            dlg.appendProgressHtml(Utilities.applyFont(
                "<b>"+INFO_CTRL_PANEL_SERVER_STOPPED.get()+"</b><br><br>",
                ColorAndFontConstants.progressFont));
            String cmdLine = getStartCommandLineName();

            dlg.setSummary(Message.raw(
                Utilities.applyFont(
                INFO_CTRL_PANEL_STARTING_SERVER_SUMMARY.get().toString(),
                ColorAndFontConstants.defaultFont)));
            dlg.appendProgressHtml(Utilities.applyFont(
                INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_START_SERVER.get()+"<br><b>"+
                  cmdLine+"</b><br><br>",
                  ColorAndFontConstants.progressFont));
          }
        });

        starting = true;
        // To display new status
        getInfo().regenerateDescriptor();
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
