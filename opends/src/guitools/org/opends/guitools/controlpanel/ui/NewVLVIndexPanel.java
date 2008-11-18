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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.ui;

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

import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVSortOrder;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.OfflineUpdateException;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.util.ConfigReader;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.admin.std.client.LocalDBBackendCfgClient;
import org.opends.server.admin.std.client.LocalDBVLVIndexCfgClient;
import org.opends.server.admin.std.client.RootCfgClient;
import org.opends.server.admin.std.meta.LocalDBVLVIndexCfgDefn;
import org.opends.server.admin.std.meta.LocalDBVLVIndexCfgDefn.Scope;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.cli.CommandBuilder;

/**
 * Panel that appears when the user defines a new VLV index.
 *
 */
public class NewVLVIndexPanel extends AbstractVLVIndexPanel
{
  private static final long serialVersionUID = 1554866540747530939L;

  /**
   * Constructor of the panel.
   * @param backendName the backend where the index will be created.
   * @param relativeComponent the component relative to which the dialog
   * containing this panel will be centered.
   */
  public NewVLVIndexPanel(String backendName, Component relativeComponent)
  {
    super(backendName, relativeComponent);
    createBasicLayout(this, new GridBagConstraints(), false);
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_NEW_VLV_INDEX_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return name;
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    if (updateLayout(ev.getNewDescriptor()))
    {
      updateErrorPaneAndOKButtonIfAuthRequired(ev.getNewDescriptor(),
          INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_NEW_VLV.get());
    }
  }

  /**
   * Updates the contents of the panel with the provided backend.
   * @param backend the backend where the index will be created.
   */
  public void update(BackendDescriptor backend)
  {
    updateBaseDNCombo(backend);
    backendName.setText(backend.getBackendID());
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    List<Message> errors = checkErrors(true);
    if (errors.isEmpty())
    {
      ProgressDialog dlg = new ProgressDialog(
          Utilities.getParentDialog(this),
          INFO_CTRL_PANEL_NEW_VLV_INDEX_TITLE.get(), getInfo());
      NewVLVIndexTask newTask = new NewVLVIndexTask(getInfo(), dlg);
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newTask, errors);
      }
      if (errors.isEmpty())
      {
        // Check filters
        if (checkIndexRequired())
        {
          String indexName = name.getText().trim();
          launchOperation(newTask,
              INFO_CTRL_PANEL_CREATING_NEW_VLV_INDEX_SUMMARY.get(indexName),
              INFO_CTRL_PANEL_CREATING_NEW_VLV_INDEX_SUCCESSFUL_SUMMARY.get(),
              INFO_CTRL_PANEL_CREATING_NEW_VLV_INDEX_SUCCESSFUL_DETAILS.get(
                  indexName),
              ERR_CTRL_PANEL_CREATING_NEW_VLV_INDEX_ERROR_SUMMARY.get(),
              ERR_CTRL_PANEL_CREATING_NEW_VLV_INDEX_ERROR_DETAILS.get(),
              null,
              dlg);
          dlg.setVisible(true);
          Utilities.getParentDialog(this).setVisible(false);
        }
      }
    }

    if (!errors.isEmpty())
    {
      displayErrorDialog(errors);
    }
  }

  /**
   * The task in charge of creating the VLV index.
   *
   */
  protected class NewVLVIndexTask extends Task
  {
    private Set<String> backendSet;
    private String indexName;
    private Scope scope;
    private List<VLVSortOrder> sortOrder;
    private String baseDN;
    private String filterValue;
    private String backendID;
    private String ldif;
    private String sortOrderStringValue;
    private int maxBlock;
    private VLVIndexDescriptor newIndex;

    /**
     * The constructor of the task.
     * @param info the control panel info.
     * @param dlg the progress dialog that shows the progress of the task.
     */
    public NewVLVIndexTask(ControlPanelInfo info, ProgressDialog dlg)
    {
      super(info, dlg);
      backendSet = new HashSet<String>();
      backendSet.add(backendName.getText());
      indexName = name.getText().trim();
      sortOrder = getSortOrder();
      baseDN = getBaseDN();
      filterValue = filter.getText().trim();
      scope = getScope();
      backendID = backendName.getText();
      ldif = getIndexLDIF(indexName);
      sortOrderStringValue = getSortOrderStringValue(sortOrder);
      maxBlock = Integer.parseInt(maxBlockSize.getText());
    }

    /**
     * {@inheritDoc}
     */
    public Type getType()
    {
      return Type.NEW_INDEX;
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
      return INFO_CTRL_PANEL_NEW_VLV_INDEX_TASK_DESCRIPTION.get(
          indexName, backendID);
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
      return canLaunch;
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
            DirectoryServer.deregisterBaseDN(DN.decode("cn=config"));
          }
          DirectoryServer.getInstance().initializeConfiguration(
              org.opends.server.extensions.ConfigFileHandler.class.getName(),
              ConfigReader.configFile);
          getInfo().setMustDeregisterConfig(true);
        }
        else
        {
          SwingUtilities.invokeLater(new Runnable()
          {
            public void run()
            {
              StringBuilder sb = new StringBuilder();
              sb.append(getConfigCommandLineName());
              Collection<String> args =
                getObfuscatedCommandLineArguments(
                    getDSConfigCommandLineArguments());
              args.removeAll(getConfigCommandLineArguments());
              for (String arg : args)
              {
                sb.append(" "+CommandBuilder.escapeValue(arg));
              }
              getProgressDialog().appendProgressHtml(Utilities.applyFont(
                  INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_CREATE_VLV_INDEX.get()+
                  "<br><b>"+sb.toString()+"</b><br><br>",
                  ColorAndFontConstants.progressFont));
            }
          });
        }
        SwingUtilities.invokeLater(new Runnable()
        {
          /**
           * {@inheritDoc}
           */
          public void run()
          {
            getProgressDialog().appendProgressHtml(
                Utilities.getProgressWithPoints(
                    INFO_CTRL_PANEL_CREATING_NEW_VLV_INDEX_PROGRESS.get(
                        indexName),
                    ColorAndFontConstants.progressFont));
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
          /**
           * {@inheritDoc}
           */
          public void run()
          {
            getProgressDialog().appendProgressHtml(
                Utilities.getProgressDone(ColorAndFontConstants.progressFont));
          }
        });
      }
      finally
      {
        if (configHandlerUpdated)
        {
          DirectoryServer.getInstance().initializeConfiguration(
              ConfigReader.configClassName, ConfigReader.configFile);
          getInfo().startPooling(ControlPanelInfo.DEFAULT_POOLING);
        }
      }
    }

    private void createIndex() throws OpenDsException
    {
      LDIFImportConfig ldifImportConfig = null;
      try
      {
        String topEntryDN =
          "cn=VLV Index,"+Utilities.getRDNString("ds-cfg-backend-id",
          backendName.getText())+",cn=Backends,cn=config";
        boolean topEntryExists =
          DirectoryServer.getConfigHandler().entryExists(
              DN.decode(topEntryDN));

        if (!topEntryExists)
        {
          String completeLDIF =
          Utilities.makeLdif(
          "dn: "+topEntryDN,
          "objectClass: top",
          "objectClass: ds-cfg-branch",
          "cn: VLV Index", "") + ldif;
          ldifImportConfig =
            new LDIFImportConfig(new StringReader(completeLDIF));
        }
        else
        {
          ldifImportConfig = new LDIFImportConfig(new StringReader(ldif));
        }


        LDIFReader reader = new LDIFReader(ldifImportConfig);
        Entry backendConfigEntry;
        while ((backendConfigEntry = reader.readEntry()) != null)
        {
          DirectoryServer.getConfigHandler().addEntry(backendConfigEntry, null);
        }
        DirectoryServer.getConfigHandler().writeUpdatedConfig();
      }
      catch (IOException ioe)
      {
         throw new OfflineUpdateException(
              ERR_CTRL_PANEL_ERROR_UPDATING_CONFIGURATION.get(ioe.toString()),
              ioe);
      }
      finally
      {
        if (ldifImportConfig != null)
        {
          ldifImportConfig.close();
        }
      }
    }

    private void createIndex(InitialLdapContext ctx) throws OpenDsException
    {
      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(ctx));
      RootCfgClient root = mCtx.getRootConfiguration();
      LocalDBBackendCfgClient backend =
        (LocalDBBackendCfgClient)root.getBackend(backendName.getText());
      LocalDBVLVIndexCfgDefn provider = LocalDBVLVIndexCfgDefn.getInstance();
      LocalDBVLVIndexCfgClient index =
        backend.createLocalDBVLVIndex(provider, name.getText(), null);

      index.setFilter(filter.getText().trim());
      index.setSortOrder(getSortOrderStringValue(getSortOrder()));
      index.setBaseDN(DN.decode(getBaseDN()));
      index.setScope(getScope());
      index.setMaxBlockSize(Integer.parseInt(maxBlockSize.getText().trim()));
      index.commit();
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

    /**
     * {@inheritDoc}
     */
    public void runTask()
    {
      state = State.RUNNING;
      lastException = null;

      try
      {
        updateConfiguration();
        for (BackendDescriptor backend :
          getInfo().getServerDescriptor().getBackends())
        {
          if (backend.getBackendID().equalsIgnoreCase(backendID))
          {
            newIndex = new VLVIndexDescriptor(
                indexName, backend, DN.decode(baseDN),
                scope, filterValue, sortOrder, maxBlock);
            getInfo().registerModifiedIndex(newIndex);
            notifyConfigurationElementCreated(newIndex);
            break;
          }
        }
        state = State.FINISHED_SUCCESSFULLY;
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
    public void postOperation()
    {
      if ((lastException == null) && (state == State.FINISHED_SUCCESSFULLY) &&
          (newIndex != null))
      {
        rebuildIndexIfNecessary(newIndex, getProgressDialog());
      }
    }

    private ArrayList<String> getDSConfigCommandLineArguments()
    {
      ArrayList<String> args = new ArrayList<String>();
      args.add("create-local-db-vlv-index");
      args.add("--backend-name");
      args.add(backendID);
      args.add("--type");
      args.add("generic");

      args.add("--index-name");
      args.add(indexName);

      args.add("--set");
      args.add("base-dn:"+baseDN);

      args.add("--set");
      args.add("filter:"+filterValue);

      args.add("--set");
      args.add("scope:"+scope.toString());

      args.add("--set");
      args.add("sort-order:"+sortOrderStringValue);

      args.addAll(getConnectionCommandLineArguments());
      args.add(getNoPropertiesFileArgument());
      args.add("--no-prompt");
      return args;
    }
  }
}
