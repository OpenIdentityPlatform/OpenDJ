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
package org.opends.guitools.controlpanel.ui;

import static org.opends.guitools.controlpanel.util.Utilities.*;
import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.SwingUtilities;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.PropertyException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.server.config.client.BackendCfgClient;
import org.forgerock.opendj.server.config.client.BackendVLVIndexCfgClient;
import org.forgerock.opendj.server.config.client.PluggableBackendCfgClient;
import org.forgerock.opendj.server.config.meta.BackendVLVIndexCfgDefn;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVSortOrder;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.util.Utilities;

/** Panel that appears when the user defines a new VLV index. */
class NewVLVIndexPanel extends AbstractVLVIndexPanel
{
  private static final long serialVersionUID = 1554866540747530939L;

  /**
   * Constructor of the panel.
   *
   * @param backendName
   *          the backend where the index will be created.
   * @param relativeComponent
   *          the component relative to which the dialog containing this panel
   *          will be centered.
   */
  NewVLVIndexPanel(String backendName, Component relativeComponent)
  {
    super(backendName, relativeComponent);
    createBasicLayout(this, new GridBagConstraints(), false);
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_NEW_VLV_INDEX_TITLE.get();
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return name;
  }

  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    final ServerDescriptor desc = ev.getNewDescriptor();
    if (updateLayout(desc))
    {
      LocalizableMessage msg = isLocal() ? INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_NEW_VLV.get()
                                         : INFO_CTRL_PANEL_CANNOT_CONNECT_TO_REMOTE_DETAILS.get(desc.getHostname());
      updateErrorPaneAndOKButtonIfAuthRequired(desc, msg);
    }
  }

  /**
   * Updates the contents of the panel with the provided backend.
   *
   * @param backend
   *          the backend where the index will be created.
   */
  void update(BackendDescriptor backend)
  {
    updateBaseDNCombo(backend);
    backendName.setText(backend.getBackendID());
  }

  @Override
  public void okClicked()
  {
    final List<LocalizableMessage> errors = checkErrors(true);
    if (errors.isEmpty())
    {
      final ProgressDialog dlg = new ProgressDialog(
          createFrame(), getParentDialog(this), INFO_CTRL_PANEL_NEW_VLV_INDEX_TITLE.get(), getInfo());
      final NewVLVIndexTask newTask = new NewVLVIndexTask(getInfo(), dlg);
      for (final Task task : getInfo().getTasks())
      {
        task.canLaunch(newTask, errors);
      }
      if (errors.isEmpty() && checkIndexRequired())
      {
        final String indexName = name.getText().trim();
        launchOperation(newTask, INFO_CTRL_PANEL_CREATING_NEW_VLV_INDEX_SUMMARY.get(indexName),
            INFO_CTRL_PANEL_CREATING_NEW_VLV_INDEX_SUCCESSFUL_SUMMARY.get(),
            INFO_CTRL_PANEL_CREATING_NEW_VLV_INDEX_SUCCESSFUL_DETAILS.get(indexName),
            ERR_CTRL_PANEL_CREATING_NEW_VLV_INDEX_ERROR_SUMMARY.get(),
            ERR_CTRL_PANEL_CREATING_NEW_VLV_INDEX_ERROR_DETAILS.get(), null, dlg);
        dlg.setVisible(true);
        getParentDialog(this).setVisible(false);
      }
    }

    if (!errors.isEmpty())
    {
      displayErrorDialog(errors);
    }
  }

  /** The task in charge of creating the VLV index. */
  private class NewVLVIndexTask extends Task
  {
    private final Set<String> backendSet;
    private final String indexName;
    private final SearchScope searchScope;
    private final List<VLVSortOrder> sortOrder;
    private final String baseDN;
    private final String filterValue;
    private final String backendID;
    private final String sortOrderStringValue;
    private VLVIndexDescriptor newIndex;

    /**
     * The constructor of the task.
     *
     * @param info
     *          the control panel info.
     * @param dlg
     *          the progress dialog that shows the progress of the task.
     */
    private NewVLVIndexTask(ControlPanelInfo info, ProgressDialog dlg)
    {
      super(info, dlg);
      backendSet = new HashSet<>();
      backendSet.add(backendName.getText());
      indexName = name.getText().trim();
      sortOrder = getSortOrder();
      baseDN = getBaseDN();
      filterValue = filter.getText().trim();
      searchScope = getScope();
      backendID = backendName.getText();
      sortOrderStringValue = getSortOrderStringValue(sortOrder);
    }

    @Override
    public Type getType()
    {
      return Type.NEW_INDEX;
    }

    @Override
    public Set<String> getBackends()
    {
      return backendSet;
    }

    @Override
    public LocalizableMessage getTaskDescription()
    {
      return INFO_CTRL_PANEL_NEW_VLV_INDEX_TASK_DESCRIPTION.get(indexName, backendID);
    }

    @Override
    public boolean canLaunch(Task taskToBeLaunched, Collection<LocalizableMessage> incompatibilityReasons)
    {
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
          return false;
        }
      }
      return true;
    }

    private void updateConfiguration() throws Exception
    {
      boolean configHandlerUpdated = false;
      try
      {
        if (!isServerRunning())
        {
          configHandlerUpdated = true;
          stopPoolingAndInitializeConfiguration();
        }
        else
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            @Override
            public void run()
            {
              final List<String> args = getObfuscatedCommandLineArguments(getDSConfigCommandLineArguments());
              args.removeAll(getConfigCommandLineArguments());
              printEquivalentCommandLine(
                  getConfigCommandLineName(), args, INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_CREATE_VLV_INDEX.get());
            }
          });
        }
        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
          public void run()
          {
            getProgressDialog().appendProgressHtml(getProgressWithPoints(
                  INFO_CTRL_PANEL_CREATING_NEW_VLV_INDEX_PROGRESS.get(indexName), ColorAndFontConstants.progressFont));
          }
        });

        if (isServerRunning())
        {
          createVLVIndexOnline(getInfo().getConnection());
        }
        else
        {
          createVLVIndexOffline(
              backendID, indexName, DN.valueOf(baseDN), filterValue, searchScope, sortOrder);
        }
        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
          public void run()
          {
            getProgressDialog().appendProgressHtml(getProgressDone(ColorAndFontConstants.progressFont));
          }
        });
      }
      finally
      {
        if (configHandlerUpdated)
        {
          startPoolingAndInitializeConfiguration();
        }
      }
    }

    private void createVLVIndexOnline(ConnectionWrapper conn) throws Exception
    {
      final BackendCfgClient backend = conn.getRootConfiguration().getBackend(backendName.getText());
      createBackendVLVIndexOnline((PluggableBackendCfgClient) backend);
    }

    private void createBackendVLVIndexOnline(final PluggableBackendCfgClient backend) throws Exception
    {
      final List<PropertyException> exceptions = new ArrayList<>();
      final BackendVLVIndexCfgClient index =
          backend.createBackendVLVIndex(BackendVLVIndexCfgDefn.getInstance(), name.getText(), exceptions);

      index.setFilter(filterValue);
      index.setSortOrder(sortOrderStringValue);
      index.setBaseDN(DN.valueOf(getBaseDN()));
      index.setScope(VLVIndexDescriptor.getBackendVLVIndexScope(getScope()));
      index.commit();
      Utilities.throwFirstFrom(exceptions);
    }

    @Override
    protected String getCommandLinePath()
    {
      return null;
    }

    @Override
    protected List<String> getCommandLineArguments()
    {
      return new ArrayList<>();
    }

    private String getConfigCommandLineName()
    {
      return isServerRunning() ? getCommandLinePath("dsconfig") : null;
    }

    @Override
    public void runTask()
    {
      state = State.RUNNING;
      lastException = null;

      try
      {
        updateConfiguration();
        for (final BackendDescriptor backend : getInfo().getServerDescriptor().getBackends())
        {
          if (backend.getBackendID().equalsIgnoreCase(backendID))
          {
            newIndex = new VLVIndexDescriptor(
                indexName, backend, DN.valueOf(baseDN), searchScope, filterValue, sortOrder);
            getInfo().registerModifiedIndex(newIndex);
            notifyConfigurationElementCreated(newIndex);
            break;
          }
        }
        state = State.FINISHED_SUCCESSFULLY;
      }
      catch (final Throwable t)
      {
        lastException = t;
        state = State.FINISHED_WITH_ERROR;
      }
    }

    @Override
    public void postOperation()
    {
      if (lastException == null && state == State.FINISHED_SUCCESSFULLY && newIndex != null)
      {
        rebuildIndexIfNecessary(newIndex, getProgressDialog());
      }
    }

    private List<String> getDSConfigCommandLineArguments()
    {
      final List<String> args = new ArrayList<>();
      args.add("create-backend-vlv-index");
      args.add("--backend-name");
      args.add(backendID);
      args.add("--type");
      args.add("generic");

      args.add("--index-name");
      args.add(indexName);

      args.add("--set");
      args.add("base-dn:" + baseDN);

      args.add("--set");
      args.add("filter:" + filterValue);

      args.add("--set");
      args.add("scope:" + VLVIndexDescriptor.getBackendVLVIndexScope(searchScope));

      args.add("--set");
      args.add("sort-order:" + sortOrderStringValue);

      args.addAll(getConnectionCommandLineArguments());
      args.add(getNoPropertiesFileArgument());
      args.add("--no-prompt");
      return args;
    }
  }
}
