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

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.PropertyException;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.client.BackendCfgClient;
import org.forgerock.opendj.server.config.client.BackendIndexCfgClient;
import org.forgerock.opendj.server.config.client.PluggableBackendCfgClient;
import org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn;
import org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.CategorizedComboBoxElement;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.datamodel.SomeSchemaElement;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.types.Schema;

/** Panel that appears when the user defines a new index. */
public class NewIndexPanel extends AbstractIndexPanel
{
  private static final long serialVersionUID = -3516011638125862137L;

  private final Component relativeComponent;
  private Schema schema;
  private IndexDescriptor newIndex;

  /**
   * Constructor of the panel.
   *
   * @param backendName
   *          the backend where the index will be created.
   * @param relativeComponent
   *          the component relative to which the dialog containing this panel
   *          will be centered.
   */
  public NewIndexPanel(final String backendName, final Component relativeComponent)
  {
    super();
    this.backendName.setText(backendName);
    this.relativeComponent = relativeComponent;
    createLayout();
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_NEW_INDEX_TITLE.get();
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return attributes;
  }

  /**
   * Updates the contents of the panel with the provided backend.
   *
   * @param backend
   *          the backend where the index will be created.
   */
  public void update(final BackendDescriptor backend)
  {
    backendName.setText(backend.getBackendID());
  }

  @Override
  public void configurationChanged(final ConfigurationChangeEvent ev)
  {
    final ServerDescriptor desc = ev.getNewDescriptor();

    Schema s = desc.getSchema();
    final boolean[] repack = { false };
    final boolean[] error = { false };
    if (s != null)
    {
      schema = s;
      repack[0] = attributes.getItemCount() == 0;
      LinkedHashSet<CategorizedComboBoxElement> newElements = new LinkedHashSet<>();

      BackendDescriptor backend = getBackendByID(backendName.getText());

      TreeSet<String> standardAttrNames = new TreeSet<>();
      TreeSet<String> configurationAttrNames = new TreeSet<>();
      TreeSet<String> customAttrNames = new TreeSet<>();
      for (AttributeType attr : schema.getAttributeTypes())
      {
        SomeSchemaElement element = new SomeSchemaElement(attr);
        String name = attr.getNameOrOID();
        if (!indexExists(backend, name))
        {
          if (Utilities.isStandard(element))
          {
            standardAttrNames.add(name);
          }
          else if (Utilities.isConfiguration(element))
          {
            configurationAttrNames.add(name);
          }
          else
          {
            customAttrNames.add(name);
          }
        }
      }
      if (!customAttrNames.isEmpty())
      {
        newElements.add(new CategorizedComboBoxElement(CUSTOM_ATTRIBUTES, CategorizedComboBoxElement.Type.CATEGORY));
        for (String attrName : customAttrNames)
        {
          newElements.add(new CategorizedComboBoxElement(attrName, CategorizedComboBoxElement.Type.REGULAR));
        }
      }
      if (!standardAttrNames.isEmpty())
      {
        newElements.add(new CategorizedComboBoxElement(STANDARD_ATTRIBUTES, CategorizedComboBoxElement.Type.CATEGORY));
        for (String attrName : standardAttrNames)
        {
          newElements.add(new CategorizedComboBoxElement(attrName, CategorizedComboBoxElement.Type.REGULAR));
        }
      }
      DefaultComboBoxModel model = (DefaultComboBoxModel) attributes.getModel();
      updateComboBoxModel(newElements, model);
    }
    else
    {
      updateErrorPane(errorPane, ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_SUMMARY.get(), ColorAndFontConstants.errorTitleFont,
          ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_DETAILS.get(), ColorAndFontConstants.defaultFont);
      repack[0] = true;
      error[0] = true;
    }

    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        setEnabledOK(!error[0]);
        errorPane.setVisible(error[0]);
        if (repack[0])
        {
          packParentDialog();
          if (relativeComponent != null)
          {
            Utilities.centerGoldenMean(Utilities.getParentDialog(NewIndexPanel.this), relativeComponent);
          }
        }
      }
    });
    if (!error[0])
    {
      updateErrorPaneAndOKButtonIfAuthRequired(desc, isLocal()
          ? INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_NEW_INDEX.get()
          : INFO_CTRL_PANEL_CANNOT_CONNECT_TO_REMOTE_DETAILS.get(desc.getHostname()));
    }
  }

  private boolean indexExists(BackendDescriptor backend, String indexName)
  {
    if (backend != null)
    {
      for (IndexDescriptor index : backend.getIndexes())
      {
        if (index.getName().equalsIgnoreCase(indexName))
        {
          return true;
        }
      }
    }
    return false;
  }

  private BackendDescriptor getBackendByID(String backendID)
  {
    for (BackendDescriptor b : getInfo().getServerDescriptor().getBackends())
    {
      if (b.getBackendID().equalsIgnoreCase(backendID))
      {
        return b;
      }
    }
    return null;
  }

  @Override
  public void okClicked()
  {
    setPrimaryValid(lAttribute);
    setPrimaryValid(lEntryLimit);
    setPrimaryValid(lType);
    List<LocalizableMessage> errors = new ArrayList<>();
    String attrName = getAttributeName();
    if (attrName == null)
    {
      errors.add(ERR_INFO_CTRL_ATTRIBUTE_NAME_REQUIRED.get());
      setPrimaryInvalid(lAttribute);
    }

    String v = entryLimit.getText();
    try
    {
      int n = Integer.parseInt(v);
      if (n < MIN_ENTRY_LIMIT || MAX_ENTRY_LIMIT < n)
      {
        errors.add(ERR_INFO_CTRL_PANEL_ENTRY_LIMIT_NOT_VALID.get(MIN_ENTRY_LIMIT, MAX_ENTRY_LIMIT));
        setPrimaryInvalid(lEntryLimit);
      }
    }
    catch (Throwable t)
    {
      errors.add(ERR_INFO_CTRL_PANEL_ENTRY_LIMIT_NOT_VALID.get(MIN_ENTRY_LIMIT, MAX_ENTRY_LIMIT));
      setPrimaryInvalid(lEntryLimit);
    }

    if (!isSomethingSelected())
    {
      errors.add(ERR_INFO_ONE_INDEX_TYPE_MUST_BE_SELECTED.get());
      setPrimaryInvalid(lType);
    }
    ProgressDialog dlg = new ProgressDialog(
        Utilities.createFrame(), Utilities.getParentDialog(this), INFO_CTRL_PANEL_NEW_INDEX_TITLE.get(), getInfo());
    NewIndexTask newTask = new NewIndexTask(getInfo(), dlg);
    for (Task task : getInfo().getTasks())
    {
      task.canLaunch(newTask, errors);
    }
    if (errors.isEmpty())
    {
      launchOperation(newTask, INFO_CTRL_PANEL_CREATING_NEW_INDEX_SUMMARY.get(attrName),
          INFO_CTRL_PANEL_CREATING_NEW_INDEX_SUCCESSFUL_SUMMARY.get(),
          INFO_CTRL_PANEL_CREATING_NEW_INDEX_SUCCESSFUL_DETAILS.get(attrName),
          ERR_CTRL_PANEL_CREATING_NEW_INDEX_ERROR_SUMMARY.get(),
          ERR_CTRL_PANEL_CREATING_NEW_INDEX_ERROR_DETAILS.get(),
          null, dlg);
      dlg.setVisible(true);
      Utilities.getParentDialog(this).setVisible(false);
    }
    else
    {
      displayErrorDialog(errors);
    }
  }

  private boolean isSomethingSelected()
  {
    for (JCheckBox type : types)
    {
      boolean somethingSelected = type.isSelected() && type.isVisible();
      if (somethingSelected)
      {
        return true;
      }
    }
    return false;
  }

  private String getAttributeName()
  {
    CategorizedComboBoxElement o = (CategorizedComboBoxElement) attributes.getSelectedItem();
    return o != null ? o.getValue().toString() : null;
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    createBasicLayout(this, gbc, false);

    attributes.addItemListener(new ItemListener()
    {
      @Override
      public void itemStateChanged(final ItemEvent ev)
      {
        String n = getAttributeName();
        AttributeType attr = null;
        if (n != null)
        {
          attr = schema.getAttributeType(n.toLowerCase());
        }
        repopulateTypesPanel(attr);
      }
    });
    entryLimit.setText(String.valueOf(DEFAULT_ENTRY_LIMIT));
  }

  /** The task in charge of creating the index. */
  private class NewIndexTask extends Task
  {
    private final Set<String> backendSet = new HashSet<>();
    private final String attributeName;
    private final int entryLimitValue;
    private final SortedSet<IndexType> indexTypes;

    /**
     * The constructor of the task.
     *
     * @param info
     *          the control panel info.
     * @param dlg
     *          the progress dialog that shows the progress of the task.
     */
    public NewIndexTask(final ControlPanelInfo info, final ProgressDialog dlg)
    {
      super(info, dlg);
      backendSet.add(backendName.getText());
      attributeName = getAttributeName();
      entryLimitValue = Integer.parseInt(entryLimit.getText());
      indexTypes = getTypes();
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
      return INFO_CTRL_PANEL_NEW_INDEX_TASK_DESCRIPTION.get(attributeName, backendName.getText());
    }

    @Override
    public boolean canLaunch(final Task taskToBeLaunched, final Collection<LocalizableMessage> incompatibilityReasons)
    {
      boolean canLaunch = true;
      if (state == State.RUNNING && runningOnSameServer(taskToBeLaunched))
      {
        // All the operations are incompatible if they apply to this
        // backend for safety.  This is a short operation so the limitation
        // has not a lot of impact.
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
              List<String> args = getObfuscatedCommandLineArguments(getDSConfigCommandLineArguments());
              args.removeAll(getConfigCommandLineArguments());
              printEquivalentCommandLine(
                  getConfigCommandLineName(), args, INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_CREATE_INDEX.get());
            }
          });
        }
        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
          public void run()
          {
            getProgressDialog().appendProgressHtml(Utilities.getProgressWithPoints(
                INFO_CTRL_PANEL_CREATING_NEW_INDEX_PROGRESS.get(attributeName), ColorAndFontConstants.progressFont));
          }
        });

        if (isServerRunning())
        {
          createIndexOnline(getInfo().getConnection());
        }
        else
        {
          createIndexOffline(backendName.getText(), attributeName, indexTypes, entryLimitValue);
        }
        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
          public void run()
          {
            getProgressDialog().appendProgressHtml(Utilities.getProgressDone(ColorAndFontConstants.progressFont));
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

    private void createIndexOnline(final ConnectionWrapper connWrapper) throws Exception
    {
      final BackendCfgClient backend = connWrapper.getRootConfiguration().getBackend(backendName.getText());
      createBackendIndexOnline((PluggableBackendCfgClient) backend);
    }

    private void createBackendIndexOnline(final PluggableBackendCfgClient backend) throws Exception
    {
      final List<PropertyException> exceptions = new ArrayList<>();
      final BackendIndexCfgClient index = backend.createBackendIndex(
          BackendIndexCfgDefn.getInstance(), attributeName, exceptions);
      index.setIndexType(indexTypes);
      if (entryLimitValue != index.getIndexEntryLimit())
      {
        index.setIndexEntryLimit(entryLimitValue);
      }
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
      if (isServerRunning())
      {
        return getCommandLinePath("dsconfig");
      }
      return null;
    }

    @Override
    public void runTask()
    {
      state = State.RUNNING;
      lastException = null;

      try
      {
        updateConfiguration();
        for (BackendDescriptor backend : getInfo().getServerDescriptor().getBackends())
        {
          if (backend.getBackendID().equalsIgnoreCase(backendName.getText()))
          {
            newIndex = new IndexDescriptor(attributeName,
                schema.getAttributeType(attributeName.toLowerCase()), backend, indexTypes, entryLimitValue);
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

    @Override
    public void postOperation()
    {
      if (lastException == null && state == State.FINISHED_SUCCESSFULLY && newIndex != null)
      {
        rebuildIndexIfNecessary(newIndex, getProgressDialog());
      }
    }

    private ArrayList<String> getDSConfigCommandLineArguments()
    {
      ArrayList<String> args = new ArrayList<>();
      args.add("create-backend-index");
      args.add("--backend-name");
      args.add(backendName.getText());
      args.add("--type");
      args.add("generic");

      args.add("--index-name");
      args.add(attributeName);

      for (IndexType type : indexTypes)
      {
        args.add("--set");
        args.add("index-type:" + type);
      }
      args.add("--set");
      args.add("index-entry-limit:" + entryLimitValue);
      args.addAll(getConnectionCommandLineArguments());
      args.add(getNoPropertiesFileArgument());
      args.add("--no-prompt");
      return args;
    }
  }
}
