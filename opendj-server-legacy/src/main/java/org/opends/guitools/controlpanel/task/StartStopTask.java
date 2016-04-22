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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ProgressDialog;

/** An abstract class used to re-factor some code between the start, stop and restart tasks. */
abstract class StartStopTask extends Task
{
  private final Set<String> backendSet;

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param progressDialog the progress dialog where the task progress will be
   * displayed.
   */
  protected StartStopTask(ControlPanelInfo info, ProgressDialog progressDialog)
  {
    super(info, progressDialog);
    backendSet = new HashSet<>();
    for (BackendDescriptor backend :
      info.getServerDescriptor().getBackends())
    {
      backendSet.add(backend.getBackendID());
    }
  }

  @Override
  public Set<String> getBackends()
  {
    return backendSet;
  }

  @Override
  public boolean canLaunch(Task taskToBeLaunched,
      Collection<LocalizableMessage> incompatibilityReasons)
  {
    boolean canLaunch = true;
    if (state == State.RUNNING && runningOnSameServer(taskToBeLaunched))
    {
      incompatibilityReasons.add(getIncompatibilityMessage(this,
          taskToBeLaunched));
      canLaunch = false;
    }
    return canLaunch;
  }

  @Override
  public void runTask()
  {
    state = State.RUNNING;
    lastException = null;
    // To display new status
    try
    {
      getInfo().stopPooling();
      getInfo().regenerateDescriptor();

      List<String> arguments = getCommandLineArguments();
      String[] args = arguments.toArray(new String[arguments.size()]);
      returnCode = executeCommandLine(getCommandLinePath(), args);

      postCommandLine();
    }
    catch (Throwable t)
    {
      lastException = t;
      state = State.FINISHED_WITH_ERROR;
    }
    getInfo().startPooling();
  }

  @Override
  protected ArrayList<String> getCommandLineArguments()
  {
    return new ArrayList<>();
  }

  /**
   * Method called just after calling the command-line.  To be overwritten
   * by the inheriting classes.
   */
  protected void postCommandLine()
  {
    state = returnCode != 0 ? State.FINISHED_WITH_ERROR : State.FINISHED_SUCCESSFULLY;
  }
}
