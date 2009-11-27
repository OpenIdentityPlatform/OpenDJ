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
 *      Copyright 2009 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.task;

import static org.opends.messages.AdminToolMessages.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.SwingUtilities;

import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.messages.Message;
import org.opends.server.tools.ManageTasks;
import org.opends.server.tools.tasks.TaskEntry;

/**
 * Task used to cancel tasks in server.
 *
 */
public class CancelTaskTask extends Task
{
  private Set<String> backendSet;
  private List<TaskEntry> tasks;

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   * @param tasks the tasks to be canceled.
   */
  public CancelTaskTask(ControlPanelInfo info, ProgressDialog dlg,
      List<TaskEntry> tasks)
  {
    super(info, dlg);
    backendSet = new HashSet<String>();
    for (BackendDescriptor backend : info.getServerDescriptor().getBackends())
    {
      backendSet.add(backend.getBackendID());
    }
    this.tasks = new ArrayList<TaskEntry>(tasks);
  }

  /**
   * {@inheritDoc}
   */
  public Type getType()
  {
    // TODO: change this
    return Type.MODIFY_ENTRY;
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
  public Message getTaskDescription()
  {
    return INFO_CTRL_PANEL_CANCEL_TASK_DESCRIPTION.get();
  }

  /**
   * {@inheritDoc}
   */
  public boolean regenerateDescriptor()
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  protected String getCommandLinePath()
  {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  protected ArrayList<String> getCommandLineArguments()
  {
    return new ArrayList<String>();
  }

  /**
   * Returns the command-line arguments to be used to cancel the task.
   * @param task the task to be canceled.
   * @return the command-line arguments to be used to cancel the task.
   */
  private ArrayList<String> getCommandLineArguments(TaskEntry task)
  {
    ArrayList<String> args = new ArrayList<String>();
    args.add("--cancel");
    args.add(task.getId());
    args.addAll(getConnectionCommandLineArguments());
    args.add(getNoPropertiesFileArgument());
    return args;
  }

  /**
   * {@inheritDoc}
   */
  public boolean canLaunch(Task taskToBeLaunched,
      Collection<Message> incompatibilityReasons)
  {
    boolean canLaunch = true;
    if (!isServerRunning())
    {
      if (state == State.RUNNING)
      {
        // All the operations are incompatible if they apply to this
        // backend for safety.  This is a short operation so the limitation
        // has not a lot of impact.
        Set<String> backends =
          new TreeSet<String>(taskToBeLaunched.getBackends());
        backends.retainAll(getBackends());
        if (backends.size() > 0)
        {
          incompatibilityReasons.add(getIncompatibilityMessage(this,
              taskToBeLaunched));
          canLaunch = false;
        }
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
      final int totalNumber = tasks.size();
      int numberCanceled = 0;

      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          getProgressDialog().getProgressBar().setIndeterminate(true);
        }
      });
      for (final TaskEntry task : tasks)
      {
        final ArrayList<String> arguments = getCommandLineArguments(task);

        final boolean isFirst = numberCanceled == 0;
        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            if (isFirst)
            {
              getProgressDialog().appendProgressHtml("<br><br>");
            }
            ArrayList<String> args = new ArrayList<String>();
            args.addAll(getObfuscatedCommandLineArguments(arguments));
            printEquivalentCommandLine(getCommandLinePath("manage-tasks"),
                    args, INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_CANCEL_TASK.get(
                        task.getId()));
          }
        });

        String[] args = new String[arguments.size()];

        arguments.toArray(args);

        returnCode = ManageTasks.mainTaskInfo(args, System.in,
            outPrintStream, errorPrintStream, false);
        if (returnCode != 0)
        {
          break;
        }
        else
        {
          numberCanceled ++;
          final int fNumberCanceled = numberCanceled;
          SwingUtilities.invokeLater(new Runnable()
          {
            public void run()
            {
              if (fNumberCanceled == 1)
              {
                getProgressDialog().getProgressBar().setIndeterminate(false);
              }
              getProgressDialog().getProgressBar().setValue(
                  (fNumberCanceled * 100) / totalNumber);
            }
          });
        }
      }
      if (returnCode != 0)
      {
        state = State.FINISHED_WITH_ERROR;
      }
      else
      {
        state = State.FINISHED_SUCCESSFULLY;
      }
    }
    catch (Throwable t)
    {
      lastException = t;
      state = State.FINISHED_WITH_ERROR;
    }
  }
}
