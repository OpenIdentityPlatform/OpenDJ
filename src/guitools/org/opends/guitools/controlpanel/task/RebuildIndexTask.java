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
 *      Portions Copyright 2012 ForgeRock AS
 */

package org.opends.guitools.controlpanel.task;

import static org.opends.messages.AdminToolMessages.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.SwingUtilities;

import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.tools.RebuildIndex;

/**
 * The class that is used when a set of indexes must be rebuilt.
 *
 */
public class RebuildIndexTask extends IndexTask
{
  private SortedSet<AbstractIndexDescriptor> indexes =
    new TreeSet<AbstractIndexDescriptor>();

  /**
   * The indexes that must not be specified in the command-line.
   */
  public static final String[] INDEXES_NOT_TO_SPECIFY =
  {"id2children", "id2subtree"};

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   * @param baseDNs the baseDNs corresponding to the indexes.
   * @param indexes the indexes.
   */
  public RebuildIndexTask(ControlPanelInfo info, ProgressDialog dlg,
      Collection<String> baseDNs, SortedSet<AbstractIndexDescriptor> indexes)
  {
    super(info, dlg, baseDNs);
    this.indexes.addAll(indexes);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Type getType()
  {
    return Type.REBUILD_INDEXES;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Message getTaskDescription()
  {
    if (baseDNs.size() == 1)
    {
      return INFO_CTRL_PANEL_REBUILD_INDEX_TASK_DESCRIPTION.get(
          baseDNs.iterator().next());
    }
    else
    {
      // Assume is in a backend
      return INFO_CTRL_PANEL_REBUILD_INDEX_TASK_DESCRIPTION.get(
          backendSet.iterator().next());
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean canLaunch(Task taskToBeLaunched,
      Collection<Message> incompatibilityReasons)
  {
    boolean canLaunch = true;
    if (state == State.RUNNING && runningOnSameServer(taskToBeLaunched))
    {
      // All the operations are incompatible if they apply to this
      // backend.
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
    return canLaunch;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void runTask()
  {
    state = State.RUNNING;
    lastException = null;
    try
    {
      boolean isLocal = getInfo().getServerDescriptor().isLocal();

      for (final String baseDN : baseDNs)
      {
        ArrayList<String> arguments = getCommandLineArguments(baseDN);

        String[] args = new String[arguments.size()];

        arguments.toArray(args);

        final List<String> displayArgs = getObfuscatedCommandLineArguments(
            getCommandLineArguments(baseDN));
        displayArgs.removeAll(getConfigCommandLineArguments());

        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
          public void run()
          {
            printEquivalentCommandLine(getCommandLinePath("rebuild-index"),
                displayArgs,
                INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_REBUILD_INDEX.get(baseDN));
          }
        });

        if (isLocal && !isServerRunning())
        {
          returnCode = executeCommandLine(getCommandLinePath("rebuild-index"),
              args);
        }
        else
        {
          returnCode = RebuildIndex.mainRebuildIndex(args, false,
              outPrintStream, errorPrintStream);
        }

        if (returnCode != 0)
        {
          break;
        }
      }

      if (returnCode != 0)
      {
        state = State.FINISHED_WITH_ERROR;
      }
      else
      {
        for (AbstractIndexDescriptor index : indexes)
        {
          getInfo().unregisterModifiedIndex(index);
        }

        state = State.FINISHED_SUCCESSFULLY;
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
  @Override
  protected ArrayList<String> getCommandLineArguments()
  {
    return new ArrayList<String>();
  }

  /**
   * Returns the command line arguments required to rebuild the indexes
   * in the specified base DN.
   * @param baseDN the base DN.
   * @return the command line arguments required to rebuild the indexes
   * in the specified base DN.
   */
  protected ArrayList<String> getCommandLineArguments(String baseDN)
  {
    ArrayList<String> args = new ArrayList<String>();

    args.add("--baseDN");
    args.add(baseDN);

    if (rebuildAll())
    {
      args.add("--rebuildAll");
    }
    else
    {
      for (AbstractIndexDescriptor index : indexes)
      {
        args.add("--index");
        if (index instanceof VLVIndexDescriptor)
        {
          args.add(
              Utilities.getVLVNameInCommandLine((VLVIndexDescriptor)index));
        }
        else
        {
          args.add(index.getName());
        }
      }
    }

    boolean isLocal = getInfo().getServerDescriptor().isLocal();
    if (isLocal && isServerRunning())
    {
    args.addAll(getConnectionCommandLineArguments());
    args.addAll(getConfigCommandLineArguments());
    }

    return args;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected String getCommandLinePath()
  {
    return null;
  }

  private boolean rebuildAll()
  {
    boolean rebuildAll = true;
    Set<BackendDescriptor> backends = new HashSet<BackendDescriptor>();
    for (AbstractIndexDescriptor index : indexes)
    {
      backends.add(index.getBackend());
    }
    for (BackendDescriptor backend : backends)
    {
      Set<AbstractIndexDescriptor> allIndexes =
        new HashSet<AbstractIndexDescriptor>();
      allIndexes.addAll(backend.getIndexes());
      allIndexes.addAll(backend.getVLVIndexes());
      for (AbstractIndexDescriptor index : allIndexes)
      {
        if (!ignoreIndex(index))
        {
          boolean found = false;
          for (AbstractIndexDescriptor indexToRebuild : indexes)
          {
            if (indexToRebuild.equals(index))
            {
              found = true;
              break;
            }
          }
          if (!found)
          {
            rebuildAll = false;
            break;
          }
        }
      }
    }
    return rebuildAll;
  }

  private boolean ignoreIndex(AbstractIndexDescriptor index)
  {
    boolean ignoreIndex = false;
    if (index instanceof IndexDescriptor)
    {
      for (String name : INDEXES_NOT_TO_SPECIFY)
      {
        if (name.equalsIgnoreCase(index.getName()))
        {
          ignoreIndex = true;
          break;
        }
      }
    }
    return ignoreIndex;
  }
}
