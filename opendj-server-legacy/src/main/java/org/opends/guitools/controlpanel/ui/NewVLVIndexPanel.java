/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.guitools.controlpanel.util.Utilities.*;
import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.naming.ldap.InitialLdapContext;
import javax.swing.SwingUtilities;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVSortOrder;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.OfflineUpdateException;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.util.ConfigReader;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.admin.std.client.LocalDBBackendCfgClient;
import org.opends.server.admin.std.client.LocalDBVLVIndexCfgClient;
import org.opends.server.admin.std.client.RootCfgClient;
import org.opends.server.admin.std.meta.LocalDBVLVIndexCfgDefn;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.LDIFReader;

/**
 * Panel that appears when the user defines a new VLV index.
 */
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

  /** {@inheritDoc} */
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
    private final String ldif;
    private final String sortOrderStringValue;
    private final int maxBlock;
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
      backendSet = new HashSet<String>();
      backendSet.add(backendName.getText());
      indexName = name.getText().trim();
      sortOrder = getSortOrder();
      baseDN = getBaseDN();
      filterValue = filter.getText().trim();
      searchScope = getScope();
      backendID = backendName.getText();
      ldif = getIndexLDIF(indexName);
      sortOrderStringValue = getSortOrderStringValue(sortOrder);
      maxBlock = Integer.parseInt(maxBlockSize.getText());
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
        final Set<String> backends = new TreeSet<String>(taskToBeLaunched.getBackends());
        backends.retainAll(getBackends());
        if (backends.size() > 0)
        {
          incompatibilityReasons.add(getIncompatibilityMessage(this, taskToBeLaunched));
          return false;
        }
      }
      return true;
    }

    private void updateConfiguration() throws OpenDsException
    {
      boolean configHandlerUpdated = false;
      try
      {
        if (!isServerRunning())
        {
          configHandlerUpdated = true;
          getInfo().stopPooling();
          if (getInfo().mustDeregisterConfig())
          {
            DirectoryServer.deregisterBaseDN(DN.valueOf("cn=config"));
          }
          DirectoryServer.getInstance().initializeConfiguration(
              org.opends.server.extensions.ConfigFileHandler.class.getName(), ConfigReader.configFile);
          getInfo().setMustDeregisterConfig(true);
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
          // Create additional indexes and display the equivalent command.
          // Everything is done in the method createAdditionalIndexes
          createIndex(getInfo().getDirContext());
        }
        else
        {
          createIndex();
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
          DirectoryServer.getInstance().initializeConfiguration(ConfigReader.configClassName, ConfigReader.configFile);
          getInfo().startPooling();
        }
      }
    }

    private void createIndex() throws OpenDsException
    {
      LDIFImportConfig ldifImportConfig = null;
      try
      {
        ldifImportConfig = new LDIFImportConfig(new StringReader(getLDIF()));

        final LDIFReader reader = new LDIFReader(ldifImportConfig);
        Entry backendConfigEntry;
        while ((backendConfigEntry = reader.readEntry()) != null)
        {
          DirectoryServer.getConfigHandler().addEntry(backendConfigEntry, null);
        }
        DirectoryServer.getConfigHandler().writeUpdatedConfig();
      }
      catch (final IOException ioe)
      {
        throw new OfflineUpdateException(ERR_CTRL_PANEL_ERROR_UPDATING_CONFIGURATION.get(ioe), ioe);
      }
      finally
      {
        if (ldifImportConfig != null)
        {
          ldifImportConfig.close();
        }
      }
    }

    private String getLDIF() throws DirectoryException
    {
      final String topEntryDN =
          "cn=VLV Index," + getRDNString("ds-cfg-backend-id", backendName.getText()) + ",cn=Backends,cn=config";
      final boolean topEntryExists = DirectoryServer.getConfigHandler().entryExists(DN.valueOf(topEntryDN));

      if (!topEntryExists)
      {
        return makeLdif(
            "dn: " + topEntryDN,
            "objectClass: top",
            "objectClass: ds-cfg-branch",
            "cn: VLV Index",
            "")
            + ldif;
      }
      return ldif;
    }

    private void createIndex(InitialLdapContext ctx) throws OpenDsException
    {
      final ManagementContext mCtx = LDAPManagementContext.createFromContext(JNDIDirContextAdaptor.adapt(ctx));
      final RootCfgClient root = mCtx.getRootConfiguration();
      final LocalDBBackendCfgClient backend = (LocalDBBackendCfgClient) root.getBackend(backendName.getText());
      final LocalDBVLVIndexCfgDefn provider = LocalDBVLVIndexCfgDefn.getInstance();
      final LocalDBVLVIndexCfgClient index = backend.createLocalDBVLVIndex(provider, name.getText(), null);

      index.setFilter(filter.getText().trim());
      index.setSortOrder(getSortOrderStringValue(getSortOrder()));
      index.setBaseDN(DN.valueOf(getBaseDN()));
      index.setScope(VLVIndexDescriptor.getLocalDBVLVIndexScope(getScope()));
      index.setMaxBlockSize(Integer.parseInt(maxBlockSize.getText().trim()));
      index.commit();
    }

    @Override
    protected String getCommandLinePath()
    {
      return null;
    }

    @Override
    protected List<String> getCommandLineArguments()
    {
      return new ArrayList<String>();
    }

    private String getConfigCommandLineName()
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
        for (final BackendDescriptor backend : getInfo().getServerDescriptor().getBackends())
        {
          if (backend.getBackendID().equalsIgnoreCase(backendID))
          {
            newIndex = new VLVIndexDescriptor(
                indexName, backend, DN.valueOf(baseDN), searchScope, filterValue, sortOrder, maxBlock);
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
      final List<String> args = new ArrayList<String>();
      args.add("create-local-db-vlv-index");
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
      args.add("scope:" + VLVIndexDescriptor.getLocalDBVLVIndexScope(searchScope));

      args.add("--set");
      args.add("sort-order:" + sortOrderStringValue);

      args.addAll(getConnectionCommandLineArguments());
      args.add(getNoPropertiesFileArgument());
      args.add("--no-prompt");
      return args;
    }
  }
}
