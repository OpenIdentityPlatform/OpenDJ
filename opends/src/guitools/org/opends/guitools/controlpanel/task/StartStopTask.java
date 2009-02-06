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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.messages.Message;

/**
 * An abstract class used to refactor some code between the start, stop and
 * restart tasks.
 *
 */
public abstract class StartStopTask extends Task
{
  Set<String> backendSet;

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param progressDialog the progress dialog where the task progress will be
   * displayed.
   */
  protected StartStopTask(ControlPanelInfo info, ProgressDialog progressDialog)
  {
    super(info, progressDialog);
    backendSet = new HashSet<String>();
    for (BackendDescriptor backend :
      info.getServerDescriptor().getBackends())
    {
      backendSet.add(backend.getBackendID());
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
  public boolean canLaunch(Task taskToBeLaunched,
      Collection<Message> incompatibilityReasons)
  {
    boolean canLaunch = true;
    if (state == State.RUNNING)
    {
      incompatibilityReasons.add(getIncompatibilityMessage(this,
          taskToBeLaunched));
      canLaunch = false;
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
    // To display new status
    try
    {
      getInfo().regenerateDescriptor();
      getInfo().stopPooling();

      ArrayList<String> arguments = getCommandLineArguments();

      String[] args = new String[arguments.size()];

      arguments.toArray(args);
      returnCode = executeCommandLine(getCommandLinePath(), args);

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
    getInfo().startPooling();
  }

  /**
   * {@inheritDoc}
   */
  protected ArrayList<String> getCommandLineArguments()
  {
    ArrayList<String> args = new ArrayList<String>();
    return args;
  }
}
