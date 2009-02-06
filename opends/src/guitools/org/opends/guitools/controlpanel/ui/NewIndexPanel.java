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

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.ldap.InitialLdapContext;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;

import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.CategorizedComboBoxElement;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.OfflineUpdateException;
import org.opends.guitools.controlpanel.task.OnlineUpdateException;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.util.ConfigReader;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn.IndexType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.OpenDsException;
import org.opends.server.types.Schema;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.cli.CommandBuilder;

/**
 * Panel that appears when the user defines a new index.
 *
 */
public class NewIndexPanel extends AbstractIndexPanel
{
  private static final long serialVersionUID = -3516011638125862137L;

  private Component relativeComponent;

  private Schema schema;

  private IndexDescriptor newIndex;

  /**
   * Constructor of the panel.
   * @param backendName the backend where the index will be created.
   * @param relativeComponent the component relative to which the dialog
   * containing this panel will be centered.
   */
  public NewIndexPanel(String backendName, Component relativeComponent)
  {
    super();
    this.backendName.setText(backendName);
    this.relativeComponent = relativeComponent;
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_NEW_INDEX_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return attributes;
  }

  /**
   * Updates the contents of the panel with the provided backend.
   * @param backend the backend where the index will be created.
   */
  public void update(BackendDescriptor backend)
  {
    backendName.setText(backend.getBackendID());
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    final ServerDescriptor desc = ev.getNewDescriptor();

    Schema s = desc.getSchema();
    final boolean[] repack = {false};
    final boolean[] error = {false};
    if (s != null)
    {
      schema = s;
      repack[0] = attributes.getItemCount() == 0;
      LinkedHashSet<CategorizedComboBoxElement> newElements =
        new LinkedHashSet<CategorizedComboBoxElement>();

//    Check that the index does not exist
      BackendDescriptor backend = null;
      for (BackendDescriptor b : getInfo().getServerDescriptor().getBackends())
      {
        if (b.getBackendID().equalsIgnoreCase(backendName.getText()))
        {
          backend = b;
          break;
        }
      }

      TreeSet<String> standardAttrNames = new TreeSet<String>();
      TreeSet<String> configurationAttrNames = new TreeSet<String>();
      TreeSet<String> customAttrNames = new TreeSet<String>();
      for (AttributeType attr : schema.getAttributeTypes().values())
      {
        String name = attr.getPrimaryName();
        boolean defined = false;
        if (backend != null)
        {
          for (IndexDescriptor index : backend.getIndexes())
          {
            if (index.getName().equalsIgnoreCase(name))
            {
              defined = true;
              break;
            }
          }
        }
        if (!defined)
        {
          if (Utilities.isStandard(attr))
          {
            standardAttrNames.add(name);
          }
          else if (Utilities.isConfiguration(attr))
          {
            configurationAttrNames.add(name);
          }
          else
          {
            customAttrNames.add(name);
          }
        }
      }
      if (customAttrNames.size() > 0)
      {
        newElements.add(new CategorizedComboBoxElement(
            CUSTOM_ATTRIBUTES,
            CategorizedComboBoxElement.Type.CATEGORY));
        for (String attrName : customAttrNames)
        {
          newElements.add(new CategorizedComboBoxElement(
              attrName,
              CategorizedComboBoxElement.Type.REGULAR));
        }
      }
      if (standardAttrNames.size() > 0)
      {
        newElements.add(new CategorizedComboBoxElement(
            STANDARD_ATTRIBUTES,
            CategorizedComboBoxElement.Type.CATEGORY));
        for (String attrName : standardAttrNames)
        {
          newElements.add(new CategorizedComboBoxElement(
              attrName,
              CategorizedComboBoxElement.Type.REGULAR));
        }
      }
      // Ignore configuration attr names
      /*
        if (configurationAttrNames.size() > 0)
        {
          newElements.add(new CategorizedComboBoxDescriptor(
              "Configuration Attributes",
              CategorizedComboBoxDescriptor.Type.CATEGORY));
          for (String attrName : configurationAttrNames)
          {
            newElements.add(new CategorizedComboBoxDescriptor(
                attrName,
                CategorizedComboBoxDescriptor.Type.REGULAR));
          }
        }
       */
      DefaultComboBoxModel model =
        (DefaultComboBoxModel)attributes.getModel();
      updateComboBoxModel(newElements, model);
    }
    else
    {
      updateErrorPane(errorPane,
          ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_SUMMARY.get(),
          ColorAndFontConstants.errorTitleFont,
          ERR_CTRL_PANEL_SCHEMA_NOT_FOUND_DETAILS.get(),
          ColorAndFontConstants.defaultFont);
      repack[0] = true;
      error[0] = true;
    }

    SwingUtilities.invokeLater(new Runnable()
    {
      public void run()
      {
        setEnabledOK(!error[0]);
        errorPane.setVisible(error[0]);
        if (repack[0])
        {
          packParentDialog();
          if (relativeComponent != null)
          {
            Utilities.centerGoldenMean(
                Utilities.getParentDialog(NewIndexPanel.this),
                relativeComponent);
          }
        }
      }
    });
    if (!error[0])
    {
      updateErrorPaneAndOKButtonIfAuthRequired(desc,
          INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_NEW_INDEX.get());
    }
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    setPrimaryValid(lAttribute);
    setPrimaryValid(lEntryLimit);
    setPrimaryValid(lType);
    ArrayList<Message> errors = new ArrayList<Message>();
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
      if ((n < MIN_ENTRY_LIMIT) || (n > MAX_ENTRY_LIMIT))
      {
        errors.add(ERR_INFO_CTRL_PANEL_ENTRY_LIMIT_NOT_VALID.get(
            MIN_ENTRY_LIMIT, MAX_ENTRY_LIMIT));
        setPrimaryInvalid(lEntryLimit);
      }
    }
    catch (Throwable t)
    {
      errors.add(ERR_INFO_CTRL_PANEL_ENTRY_LIMIT_NOT_VALID.get(
          MIN_ENTRY_LIMIT, MAX_ENTRY_LIMIT));
      setPrimaryInvalid(lEntryLimit);
    }

    boolean somethingSelected = false;
    for (JCheckBox type : types)
    {
      somethingSelected = type.isSelected() && type.isVisible();
      if (somethingSelected)
      {
        break;
      }
    }
    if (!somethingSelected)
    {
      errors.add(ERR_INFO_ONE_INDEX_TYPE_MUST_BE_SELECTED.get());
      setPrimaryInvalid(lType);
    }
    ProgressDialog dlg = new ProgressDialog(
        Utilities.getParentDialog(this),
        INFO_CTRL_PANEL_NEW_INDEX_TITLE.get(), getInfo());
    NewIndexTask newTask = new NewIndexTask(getInfo(), dlg);
    for (Task task : getInfo().getTasks())
    {
      task.canLaunch(newTask, errors);
    }
    if (errors.size() == 0)
    {
      launchOperation(newTask,
          INFO_CTRL_PANEL_CREATING_NEW_INDEX_SUMMARY.get(attrName),
          INFO_CTRL_PANEL_CREATING_NEW_INDEX_SUCCESSFUL_SUMMARY.get(),
          INFO_CTRL_PANEL_CREATING_NEW_INDEX_SUCCESSFUL_DETAILS.get(attrName),
          ERR_CTRL_PANEL_CREATING_NEW_INDEX_ERROR_SUMMARY.get(),
          ERR_CTRL_PANEL_CREATING_NEW_INDEX_ERROR_DETAILS.get(),
          null,
          dlg);
      dlg.setVisible(true);
      Utilities.getParentDialog(this).setVisible(false);
    }
    else
    {
      displayErrorDialog(errors);
    }
  }


  private String getAttributeName()
  {
    String attrName;
    CategorizedComboBoxElement o =
      (CategorizedComboBoxElement)attributes.getSelectedItem();
    if (o != null)
    {
      attrName = o.getValue().toString();
    }
    else
    {
      attrName = null;
    }
    return attrName;
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();

    createBasicLayout(this, gbc, false);

    attributes.addItemListener(new ItemListener()
    {
      public void itemStateChanged(ItemEvent ev)
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

  /**
   * The task in charge of creating the index.
   *
   */
  protected class NewIndexTask extends Task
  {
    private Set<String> backendSet;
    private String attributeName;
    private int entryLimitValue;
    private SortedSet<IndexType> indexTypes;

    /**
     * The constructor of the task.
     * @param info the control panel info.
     * @param dlg the progress dialog that shows the progress of the task.
     */
    public NewIndexTask(ControlPanelInfo info, ProgressDialog dlg)
    {
      super(info, dlg);
      backendSet = new HashSet<String>();
      backendSet.add(backendName.getText());
      attributeName = getAttributeName();
      entryLimitValue = Integer.parseInt(entryLimit.getText());
      indexTypes = getTypes();
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
      return INFO_CTRL_PANEL_NEW_INDEX_TASK_DESCRIPTION.get(
          attributeName, backendName.getText());
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
            /**
             * {@inheritDoc}
             */
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
                  INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_CREATE_INDEX.get()+
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
                    INFO_CTRL_PANEL_CREATING_NEW_INDEX_PROGRESS.get(
                        attributeName),
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
          getInfo().startPooling();
        }
      }
    }

    /**
     * Returns the LDIF representation of the index to be created.
     * @return the LDIF representation of the index to be created.
     */
    private String getIndexLDIF()
    {
      String dn = Utilities.getRDNString(
          "ds-cfg-backend-id", backendName.getText())+",cn=Backends,cn=config";
      ArrayList<String> lines = new ArrayList<String>();
      lines.add("dn: "+Utilities.getRDNString("ds-cfg-attribute",
          attributeName)+
          ",cn=Index,"+dn);
      lines.add("objectClass: ds-cfg-local-db-index");
      lines.add("objectClass: top");
      lines.add("ds-cfg-attribute: "+attributeName);
      lines.add("ds-cfg-index-entry-limit: "+entryLimitValue);
      for (IndexType type : indexTypes)
      {
        lines.add("ds-cfg-index-type: "+type.toString());
      }
      StringBuilder sb = new StringBuilder();
      for (String line : lines)
      {
        sb.append(line+ServerConstants.EOL);
      }
      return sb.toString();
    }

    private void createIndex() throws OpenDsException
    {
      LDIFImportConfig ldifImportConfig = null;
      try
      {
        String ldif = getIndexLDIF();

        ldifImportConfig = new LDIFImportConfig(new StringReader(ldif));
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
      // Instead of adding indexes using management framework, use this approach
      // so that we have to define the additional indexes only in the method
      // getBackendLdif.
      String ldif = getIndexLDIF();
      LDIFImportConfig ldifImportConfig = null;
      try
      {
        ldifImportConfig = new LDIFImportConfig(new StringReader(ldif));
        LDIFReader reader = new LDIFReader(ldifImportConfig);
        Entry indexEntry = reader.readEntry();
        Attributes attrs = new BasicAttributes();

        BasicAttribute oc = new BasicAttribute("objectClass");
        Iterator<AttributeValue> it =
          indexEntry.getObjectClassAttribute().iterator();
        while (it.hasNext())
        {
          oc.add(it.next().getValue().toString());
        }
        attrs.put(oc);

        List<Attribute> odsAttrs = indexEntry.getAttributes();
        for (Attribute odsAttr : odsAttrs)
        {
          String attrName = odsAttr.getName();
          BasicAttribute attr = new BasicAttribute(attrName);
          it = odsAttr.iterator();
          while (it.hasNext())
          {
            attr.add(it.next().getValue().toString());
          }
          attrs.put(attr);
        }

        final StringBuilder sb = new StringBuilder();
        sb.append(getConfigCommandLineName());
        Collection<String> args =
          getObfuscatedCommandLineArguments(getDSConfigCommandLineArguments());
        for (String arg : args)
        {
          sb.append(" "+CommandBuilder.escapeValue(arg));
        }

        ctx.createSubcontext(indexEntry.getDN().toString(), attrs);
      }
      catch (Throwable t)
      {
        throw new OnlineUpdateException(
            ERR_CTRL_PANEL_ERROR_UPDATING_CONFIGURATION.get(t.toString()), t);
      }
      finally
      {
        if (ldifImportConfig != null)
        {
          ldifImportConfig.close();
        }
      }
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
          if (backend.getBackendID().equalsIgnoreCase(backendName.getText()))
          {
            newIndex = new IndexDescriptor(attributeName,
                schema.getAttributeType(attributeName.toLowerCase()), backend,
                indexTypes, entryLimitValue);
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
      args.add("create-local-db-index");
      args.add("--backend-name");
      args.add(backendName.getText());
      args.add("--type");
      args.add("generic");

      args.add("--index-name");
      args.add(attributeName);

      for (IndexType type : indexTypes)
      {
        args.add("--set");
        args.add("index-type:"+type.toString());
      }
      args.add("--set");
      args.add("index-entry-limit:"+entryLimitValue);
      args.addAll(getConnectionCommandLineArguments());
      args.add(getNoPropertiesFileArgument());
      args.add("--no-prompt");
      return args;
    }
  }
}
