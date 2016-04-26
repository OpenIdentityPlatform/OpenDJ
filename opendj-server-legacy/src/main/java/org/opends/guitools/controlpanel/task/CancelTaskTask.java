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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.task;

import static org.opends.messages.AdminToolMessages.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.server.tools.ManageTasks;
import org.opends.server.tools.tasks.TaskEntry;

/** Task used to cancel tasks in server. */
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
    backendSet = new HashSet<>();
    for (BackendDescriptor backend : info.getServerDescriptor().getBackends())
    {
      backendSet.add(backend.getBackendID());
    }
    this.tasks = new ArrayList<>(tasks);
  }

  @Override
  public Type getType()
  {
    // TODO: change this
    return Type.MODIFY_ENTRY;
  }

  @Override
  public Set<String> getBackends()
  {
    return backendSet;
  }

  @Override
  public LocalizableMessage getTaskDescription()
  {
    return INFO_CTRL_PANEL_CANCEL_TASK_DESCRIPTION.get();
  }

  @Override
  public boolean regenerateDescriptor()
  {
    return true;
  }

  @Override
  protected String getCommandLinePath()
  {
    return null;
  }

  @Override
  protected ArrayList<String> getCommandLineArguments()
  {
    return new ArrayList<>();
  }

  /**
   * Returns the command-line arguments to be used to cancel the task.
   * @param task the task to be canceled.
   * @return the command-line arguments to be used to cancel the task.
   */
  private ArrayList<String> getCommandLineArguments(TaskEntry task)
  {
    ArrayList<String> args = new ArrayList<>();
    args.add("--cancel");
    args.add(task.getId());
    args.addAll(getConnectionCommandLineArguments());
    args.add(getNoPropertiesFileArgument());
    return args;
  }

  @Override
  public boolean canLaunch(Task taskToBeLaunched,
      Collection<LocalizableMessage> incompatibilityReasons)
  {
    if (!isServerRunning() && state == State.RUNNING)
    {
      // All the operations are incompatible if they apply to this
      // backend for safety.  This is a short operation so the limitation
      // has not a lot of impact.
      Set<String> backends = new TreeSet<>(taskToBeLaunched.getBackends());
      backends.retainAll(getBackends());
      if (!backends.isEmpty())
      {
        incompatibilityReasons.add(getIncompatibilityMessage(this, taskToBeLaunched));
        return false;
      }
    }
    return true;
  }

  @Override
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
        @Override
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
          @Override
          public void run()
          {
            if (isFirst)
            {
              getProgressDialog().appendProgressHtml("<br><br>");
            }
            ArrayList<String> args = new ArrayList<>(getObfuscatedCommandLineArguments(arguments));
            printEquivalentCommandLine(getCommandLinePath("manage-tasks"),
                    args, INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_CANCEL_TASK.get(
                        task.getId()));
          }
        });

        String[] args = new String[arguments.size()];

        arguments.toArray(args);

        returnCode = ManageTasks.mainTaskInfo(args, outPrintStream, errorPrintStream, false);
        if (returnCode != 0)
        {
          break;
        }

        numberCanceled++;
        final int fNumberCanceled = numberCanceled;
        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
          public void run()
          {
            JProgressBar progressBar = getProgressDialog().getProgressBar();
            if (fNumberCanceled == 1)
            {
              progressBar.setIndeterminate(false);
            }
            progressBar.setValue((fNumberCanceled * 100) / totalNumber);
          }
        });
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
