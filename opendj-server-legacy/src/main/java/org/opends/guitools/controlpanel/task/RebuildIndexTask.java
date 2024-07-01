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
 * Portions Copyright 2012-2016 ForgeRock AS.
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

import org.forgerock.i18n.LocalizableMessage;
import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.backends.pluggable.SuffixContainer;
import org.opends.server.tools.RebuildIndex;

/**
 * The class that is used when a set of indexes must be rebuilt.
 */
public class RebuildIndexTask extends IndexTask
{
  private final SortedSet<AbstractIndexDescriptor> indexes = new TreeSet<>();

  /**
   * The indexes that must not be specified in the command-line.
   */
  public static final String[] INDEXES_NOT_TO_SPECIFY = { SuffixContainer.ID2CHILDREN_INDEX_NAME,
    SuffixContainer.ID2SUBTREE_INDEX_NAME, SuffixContainer.ID2CHILDREN_COUNT_NAME };

  /**
   * Constructor of the task.
   *
   * @param info
   *          the control panel information.
   * @param dlg
   *          the progress dialog where the task progress will be displayed.
   * @param baseDNs
   *          the baseDNs corresponding to the indexes.
   * @param indexes
   *          the indexes.
   */
  public RebuildIndexTask(ControlPanelInfo info, ProgressDialog dlg, Collection<String> baseDNs,
      SortedSet<AbstractIndexDescriptor> indexes)
  {
    super(info, dlg, baseDNs);
    this.indexes.addAll(indexes);
  }

  @Override
  public Type getType()
  {
    return Type.REBUILD_INDEXES;
  }

  @Override
  public LocalizableMessage getTaskDescription()
  {
    if (baseDNs.size() == 1)
    {
      return INFO_CTRL_PANEL_REBUILD_INDEX_TASK_DESCRIPTION.get(baseDNs.iterator().next());
    }
    else
    {
      // Assume is in a backend
      return INFO_CTRL_PANEL_REBUILD_INDEX_TASK_DESCRIPTION.get(backendSet.iterator().next());
    }
  }

  @Override
  public boolean canLaunch(Task taskToBeLaunched, Collection<LocalizableMessage> incompatibilityReasons)
  {
    boolean canLaunch = true;
    if (state == State.RUNNING && runningOnSameServer(taskToBeLaunched))
    {
      // All the operations are incompatible if they apply to this backend.
      Set<String> backends = new TreeSet<>(taskToBeLaunched.getBackends());
      backends.retainAll(getBackends());
      if (!backends.isEmpty())
      {
        incompatibilityReasons.add(getIncompatibilityMessage(this, taskToBeLaunched));
        canLaunch = false;
      }
    }
    return canLaunch;
  }

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
        List<String> arguments = getCommandLineArguments(baseDN);
        String[] args = arguments.toArray(new String[arguments.size()]);

        final List<String> displayArgs = getObfuscatedCommandLineArguments(getCommandLineArguments(baseDN));
        displayArgs.removeAll(getConfigCommandLineArguments());

        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
          public void run()
          {
            printEquivalentCommandLine(getCommandLinePath("rebuild-index"), displayArgs,
                INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_REBUILD_INDEX.get(baseDN));
          }
        });

        if (isLocal && !isServerRunning())
        {
          returnCode = executeCommandLine(getCommandLinePath("rebuild-index"), args);
        }
        else
        {
          returnCode = RebuildIndex.mainRebuildIndex(args, false, outPrintStream, errorPrintStream);
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

  @Override
  protected List<String> getCommandLineArguments()
  {
    return new ArrayList<>();
  }

  /**
   * Returns the command line arguments required to rebuild the indexes in the
   * specified base DN.
   *
   * @param baseDN
   *          the base DN.
   * @return the command line arguments required to rebuild the indexes in the
   *         specified base DN.
   */
  private List<String> getCommandLineArguments(String baseDN)
  {
    List<String> args = new ArrayList<>();

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
          args.add(Utilities.getVLVNameInCommandLine((VLVIndexDescriptor) index));
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

  @Override
  protected String getCommandLinePath()
  {
    return null;
  }

  private boolean rebuildAll()
  {
    Set<BackendDescriptor> backends = new HashSet<>();
    for (AbstractIndexDescriptor index : indexes)
    {
      backends.add(index.getBackend());
    }
    for (BackendDescriptor backend : backends)
    {
      Set<AbstractIndexDescriptor> allIndexes = new HashSet<>();
      allIndexes.addAll(backend.getIndexes());
      allIndexes.addAll(backend.getVLVIndexes());
      for (AbstractIndexDescriptor index : allIndexes)
      {
        if (!ignoreIndex(index) && !indexes.contains(index))
        {
          return false;
        }
      }
    }
    return true;
  }

  private boolean ignoreIndex(AbstractIndexDescriptor index)
  {
    if (index instanceof IndexDescriptor)
    {
      for (String name : INDEXES_NOT_TO_SPECIFY)
      {
        if (name.equalsIgnoreCase(index.getName()))
        {
          return true;
        }
      }
    }
    return false;
  }
}
