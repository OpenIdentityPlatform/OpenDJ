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

import static org.opends.guitools.controlpanel.util.Utilities.*;
import static org.opends.messages.AdminToolMessages.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.SwingUtilities;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.client.BackendCfgClient;
import org.forgerock.opendj.server.config.client.PluggableBackendCfgClient;
import org.forgerock.opendj.server.config.client.RootCfgClient;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.OpenDsException;

/** The task that is launched when an index must be deleted. */
public class DeleteIndexTask extends Task
{
  private final Set<String> backendSet;
  private final List<AbstractIndexDescriptor> indexesToDelete = new ArrayList<>();
  private final List<AbstractIndexDescriptor> deletedIndexes = new ArrayList<>();

  /**
   * Constructor of the task.
   *
   * @param info
   *          the control panel information.
   * @param dlg
   *          the progress dialog where the task progress will be displayed.
   * @param indexesToDelete
   *          the indexes that must be deleted.
   */
  public DeleteIndexTask(ControlPanelInfo info, ProgressDialog dlg, List<AbstractIndexDescriptor> indexesToDelete)
  {
    super(info, dlg);
    backendSet = new HashSet<>();
    for (final AbstractIndexDescriptor index : indexesToDelete)
    {
      backendSet.add(index.getBackend().getBackendID());
    }
    this.indexesToDelete.addAll(indexesToDelete);
  }

  @Override
  public Type getType()
  {
    return Type.DELETE_INDEX;
  }

  @Override
  public Set<String> getBackends()
  {
    return backendSet;
  }

  @Override
  public LocalizableMessage getTaskDescription()
  {
    if (backendSet.size() == 1)
    {
      return INFO_CTRL_PANEL_DELETE_INDEX_TASK_DESCRIPTION.get(getStringFromCollection(backendSet, ", "));
    }
    else
    {
      return INFO_CTRL_PANEL_DELETE_INDEX_IN_BACKENDS_TASK_DESCRIPTION.get(getStringFromCollection(backendSet, ", "));
    }
  }

  @Override
  public boolean canLaunch(Task taskToBeLaunched, Collection<LocalizableMessage> incompatibilityReasons)
  {
    boolean canLaunch = true;
    if (state == State.RUNNING && runningOnSameServer(taskToBeLaunched))
    {
      // All the operations are incompatible if they apply to this
      // backend for safety.  This is a short operation so the limitation
      // has not a lot of impact.
      final Set<String> backends = new TreeSet<>(taskToBeLaunched.getBackends());
      backends.retainAll(getBackends());
      if (!backends.isEmpty())
      {
        incompatibilityReasons.add(getIncompatibilityMessage(this, taskToBeLaunched));
        canLaunch = false;
      }
    }
    return canLaunch;
  }

  /**
   * Update the configuration in the server.
   *
   * @throws OpenDsException
   *           if an error occurs.
   */
  private void updateConfiguration() throws Exception
  {
    boolean configHandlerUpdated = false;
    final int totalNumber = indexesToDelete.size();
    int numberDeleted = 0;
    try
    {
      if (!isServerRunning())
      {
        configHandlerUpdated = true;
        stopPoolingAndInitializeConfiguration();
      }
      boolean isFirst = true;
      for (final AbstractIndexDescriptor index : indexesToDelete)
      {
        if (!isFirst)
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            @Override
            public void run()
            {
              getProgressDialog().appendProgressHtml("<br><br>");
            }
          });
        }
        isFirst = false;
        if (isServerRunning())
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            @Override
            public void run()
            {
              final List<String> args = getObfuscatedCommandLineArguments(getDSConfigCommandLineArguments(index));
              args.removeAll(getConfigCommandLineArguments());
              printEquivalentCommandLine(getConfigCommandLineName(index), args,
                  INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_DELETE_INDEX.get());
            }
          });
        }
        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
          public void run()
          {
            if (isVLVIndex(index))
            {
              getProgressDialog().appendProgressHtml(
                  Utilities.getProgressWithPoints(INFO_CTRL_PANEL_DELETING_VLV_INDEX.get(index.getName()),
                      ColorAndFontConstants.progressFont));
            }
            else
            {
              getProgressDialog().appendProgressHtml(
                  Utilities.getProgressWithPoints(INFO_CTRL_PANEL_DELETING_INDEX.get(index.getName()),
                      ColorAndFontConstants.progressFont));
            }
          }
        });
        if (isServerRunning())
        {
          deleteIndex(getInfo().getConnection(), index);
        }
        else
        {
          deleteIndex(index);
        }
        numberDeleted++;
        final int fNumberDeleted = numberDeleted;
        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
          public void run()
          {
            getProgressDialog().getProgressBar().setIndeterminate(false);
            getProgressDialog().getProgressBar().setValue((fNumberDeleted * 100) / totalNumber);
            getProgressDialog().appendProgressHtml(Utilities.getProgressDone(ColorAndFontConstants.progressFont));
          }
        });
        deletedIndexes.add(index);
      }
    }
    finally
    {
      if (configHandlerUpdated)
      {
        startPoolingAndInitializeConfiguration();
      }
    }
  }

  /**
   * Returns <CODE>true</CODE> if the index is a VLV index and
   * <CODE>false</CODE> otherwise.
   *
   * @param index
   *          the index.
   * @return <CODE>true</CODE> if the index is a VLV index and
   *         <CODE>false</CODE> otherwise.
   */
  private boolean isVLVIndex(AbstractIndexDescriptor index)
  {
    return index instanceof VLVIndexDescriptor;
  }

  /**
   * Deletes an index. The code assumes that the server is not running and that
   * the configuration file can be edited.
   *
   * @param index
   *          the index to be deleted.
   * @throws OpenDsException
   *           if an error occurs.
   */
  private void deleteIndex(AbstractIndexDescriptor index) throws OpenDsException
  {
    final String backendId = "ds-cfg-backend-id" + "=" + index.getBackend().getBackendID();
    String dn;
    if (isVLVIndex(index))
    {
      dn = "ds-cfg-name" + "=" + index.getName() + ",cn=VLV Index," + backendId + ",cn=Backends,cn=config";
    }
    else
    {
      dn = "ds-cfg-attribute" + "=" + index.getName() + ",cn=Index," + backendId + ",cn=Backends,cn=config";
    }
    DirectoryServer.getInstance().getServerContext().getConfigurationHandler().deleteEntry(DN.valueOf(dn));
  }

  /**
   * Deletes an index. The code assumes that the server is running and that the
   * provided connection is active.
   *
   * @param connWrapper
   *          the connection to the server.
   * @param index
   *          the index to be deleted.
   * @throws OpenDsException
   *           if an error occurs.
   */
  private void deleteIndex(final ConnectionWrapper connWrapper, final AbstractIndexDescriptor index) throws Exception
  {
    final RootCfgClient root = connWrapper.getRootConfiguration();
    final BackendCfgClient backend = root.getBackend(index.getBackend().getBackendID());

    removeBackendIndex((PluggableBackendCfgClient) backend, index);
    backend.commit();
  }

  private void removeBackendIndex(final PluggableBackendCfgClient backend, final AbstractIndexDescriptor index)
      throws Exception
  {
    final String indexName = index.getName();
    if (isVLVIndex(index))
    {
      backend.removeBackendVLVIndex(indexName);
    }
    else
    {
      backend.removeBackendIndex(indexName);
    }
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
   * Returns the path of the command line to be used to delete the specified
   * index.
   *
   * @param index
   *          the index to be deleted.
   * @return the path of the command line to be used to delete the specified
   *         index.
   */
  private String getConfigCommandLineName(AbstractIndexDescriptor index)
  {
    if (isServerRunning())
    {
      return getCommandLinePath("dsconfig");
    }
    else
    {
      return null;
    }
  }

  @Override
  public void runTask()
  {
    state = State.RUNNING;
    lastException = null;

    try
    {
      updateConfiguration();
      state = State.FINISHED_SUCCESSFULLY;
    }
    catch (final Throwable t)
    {
      lastException = t;
      state = State.FINISHED_WITH_ERROR;
    }
    finally
    {
      for (final AbstractIndexDescriptor index : deletedIndexes)
      {
        getInfo().unregisterModifiedIndex(index);
      }
    }
  }

  /**
   * Return the dsconfig arguments required to delete an index.
   *
   * @param index
   *          the index to be deleted.
   * @return the dsconfig arguments required to delete an index.
   */
  private List<String> getDSConfigCommandLineArguments(AbstractIndexDescriptor index)
  {
    final List<String> args = new ArrayList<>();
    if (isVLVIndex(index))
    {
      args.add("delete-backend-vlv-index");
    }
    else
    {
      args.add("delete-backend-index");
    }
    args.add("--backend-name");
    args.add(index.getBackend().getBackendID());

    args.add("--index-name");
    args.add(index.getName());

    args.addAll(getConnectionCommandLineArguments());
    args.add("--no-prompt");
    args.add(getNoPropertiesFileArgument());

    return args;
  }
}
