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
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.naming.ldap.InitialLdapContext;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.event.ScrollPaneBorderListener;
import org.opends.guitools.controlpanel.task.DeleteIndexTask;
import org.opends.guitools.controlpanel.task.OfflineUpdateException;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.util.ConfigReader;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.client.ldap.LDAPManagementContext;
import org.opends.server.admin.std.client.LocalDBBackendCfgClient;
import org.opends.server.admin.std.client.LocalDBIndexCfgClient;
import org.opends.server.admin.std.client.RootCfgClient;
import org.opends.server.admin.std.meta.LocalDBIndexCfgDefn.IndexType;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.ServerConstants;
import org.opends.server.util.cli.CommandBuilder;

/**
 * The panel that displays an existing index (it appears on the right of the
 * 'Manage Indexes' dialog).
 *
 */
public class IndexPanel extends AbstractIndexPanel
{
  private static final long serialVersionUID = 1439500626486823366L;
  private IndexDescriptor index;
  private ScrollPaneBorderListener scrollListener;

  private boolean ignoreCheckSave;

  private ModifyIndexTask newModifyTask;

  /**
   * Default constructor.
   *
   */
  public IndexPanel()
  {
    super();
    createLayout();
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   *
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    JPanel p = new JPanel(new GridBagLayout());
    p.setOpaque(false);
    super.createBasicLayout(p, gbc, true);
    p.setBorder(new EmptyBorder(10, 10, 10, 10));
    gbc = new GridBagConstraints();
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridx = 0;
    gbc.gridy = 0;
    JScrollPane scroll = Utilities.createBorderLessScrollBar(p);
    scrollListener = new ScrollPaneBorderListener(scroll);
    add(scroll, gbc);

    gbc.gridy ++;
    gbc.gridx = 0;
    gbc.weightx = 1.0;
    gbc.insets.left = 0;
    gbc.gridwidth = 2;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;

    gbc.insets = new Insets(10, 10, 0, 10);
    add(warning, gbc);
    Utilities.setWarningLabel(warning, INDEX_MODIFIED);

    gbc.gridy ++;
    JPanel buttonPanel = new JPanel(new GridBagLayout());
    buttonPanel.setOpaque(false);
    gbc.insets = new Insets(10, 10, 10, 10);
    add(buttonPanel, gbc);

    gbc.insets = new Insets(0, 0, 0, 0);
    gbc.gridy = 0;
    gbc.gridx = 0;
    gbc.weightx = 0.0;
    gbc.gridwidth = 1;
    deleteIndex.setOpaque(false);
    gbc.insets.left = 0;
    buttonPanel.add(deleteIndex, gbc);
    deleteIndex.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        deleteIndex();
      }
    });
    gbc.gridx = 2;
    gbc.weightx = 1.0;
    buttonPanel.add(Box.createHorizontalGlue(), gbc);
    gbc.weightx = 0.0;
    gbc.insets.left = 10;
    saveChanges.setOpaque(false);
    gbc.gridx = 3;
    buttonPanel.add(saveChanges, gbc);
    saveChanges.addActionListener(new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        saveIndex(false);
      }
    });

    entryLimit.getDocument().addDocumentListener(new DocumentListener()
    {
      /**
       * {@inheritDoc}
       */
      public void insertUpdate(DocumentEvent ev)
      {
        checkSaveButton();
      }

      /**
       * {@inheritDoc}
       */
      public void changedUpdate(DocumentEvent ev)
      {
        checkSaveButton();
      }

      /**
       * {@inheritDoc}
       */
      public void removeUpdate(DocumentEvent ev)
      {
        checkSaveButton();
      }
    });

    ActionListener listener = new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        checkSaveButton();
      }
    };
    for (JCheckBox cb : types)
    {
      cb.addActionListener(listener);
    }
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_INDEX_PANEL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return entryLimit;
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(final ConfigurationChangeEvent ev)
  {
    updateErrorPaneIfAuthRequired(ev.getNewDescriptor(),
        INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_INDEX_EDITING.get());
    SwingUtilities.invokeLater(new Runnable()
    {
      /**
       * {@inheritDoc}
       */
      public void run()
      {
        checkSaveButton();
        deleteIndex.setEnabled(!authenticationRequired(ev.getNewDescriptor()));
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
  }

  /**
   * Method used to know if there are unsaved changes or not.  It is used by
   * the index selection listener when the user changes the selection.
   * @return <CODE>true</CODE> if there are unsaved changes (and so the
   * selection of the index should be canceled) and <CODE>false</CODE>
   * otherwise.
   */
  public boolean mustCheckUnsavedChanges()
  {
    return (index != null) &&
        saveChanges.isVisible() && saveChanges.isEnabled();
  }

  /**
   * Tells whether the user chose to save the changes in the panel, to not save
   * them or simply cancelled the selection in the tree.
   * @return the value telling whether the user chose to save the changes in the
   * panel, to not save them or simply cancelled the selection change in the
   * tree.
   */
  public UnsavedChangesDialog.Result checkUnsavedChanges()
  {
    UnsavedChangesDialog.Result result;
    UnsavedChangesDialog unsavedChangesDlg = new UnsavedChangesDialog(
          Utilities.getParentDialog(this), getInfo());
    unsavedChangesDlg.setMessage(INFO_CTRL_PANEL_UNSAVED_CHANGES_SUMMARY.get(),
        INFO_CTRL_PANEL_UNSAVED_INDEX_CHANGES_DETAILS.get(index.getName()));
    Utilities.centerGoldenMean(unsavedChangesDlg,
          Utilities.getParentDialog(this));
    unsavedChangesDlg.setVisible(true);
    result = unsavedChangesDlg.getResult();
    if (result == UnsavedChangesDialog.Result.SAVE)
    {
      saveIndex(false);
      if ((newModifyTask == null) || // The user data is not valid
          (newModifyTask.getState() != Task.State.FINISHED_SUCCESSFULLY))
      {
        result = UnsavedChangesDialog.Result.CANCEL;
      }
    }

    return result;
  }

  /**
   * Checks the enabling state of the save button.
   *
   */
  private void checkSaveButton()
  {
    if (!ignoreCheckSave && (index != null))
    {
      saveChanges.setEnabled(
          !authenticationRequired(getInfo().getServerDescriptor()) &&
          isModified());
    }
  }

  private void deleteIndex()
  {
    ArrayList<Message> errors = new ArrayList<Message>();
    ProgressDialog dlg = new ProgressDialog(
        Utilities.getParentDialog(this),
        INFO_CTRL_PANEL_DELETE_INDEX_TITLE.get(), getInfo());
    ArrayList<AbstractIndexDescriptor> indexesToDelete =
      new ArrayList<AbstractIndexDescriptor>();
    indexesToDelete.add(index);
    DeleteIndexTask newTask = new DeleteIndexTask(getInfo(), dlg,
        indexesToDelete);
    for (Task task : getInfo().getTasks())
    {
      task.canLaunch(newTask, errors);
    }
    if (errors.isEmpty())
    {
      String indexName = index.getName();
      String backendName = index.getBackend().getBackendID();
      if (displayConfirmationDialog(
          INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
          INFO_CTRL_PANEL_CONFIRMATION_INDEX_DELETE_DETAILS.get(indexName,
              backendName)))
      {
        launchOperation(newTask,
            INFO_CTRL_PANEL_DELETING_INDEX_SUMMARY.get(),
            INFO_CTRL_PANEL_DELETING_INDEX_COMPLETE.get(),
            INFO_CTRL_PANEL_DELETING_INDEX_SUCCESSFUL.get(indexName,
                backendName),
            ERR_CTRL_PANEL_DELETING_INDEX_ERROR_SUMMARY.get(),
            ERR_CTRL_PANEL_DELETING_INDEX_ERROR_DETAILS.get(indexName),
            null,
            dlg);
        dlg.setVisible(true);
      }
    }
    else
    {
      displayErrorDialog(errors);
    }
  }

  /**
   * Saves the index modifications.
   * @param modal whether the progress dialog for the task must be modal or
   * not.
   */
  private void saveIndex(boolean modal)
  {
    newModifyTask = null;
    if (!isModified())
    {
      return;
    }

    List<Message> errors = getErrors();

    if (errors.isEmpty())
    {
      ProgressDialog dlg = new ProgressDialog(
          Utilities.getParentDialog(this),
          INFO_CTRL_PANEL_MODIFYING_INDEX_TITLE.get(), getInfo());
      dlg.setModal(modal);
      newModifyTask = new ModifyIndexTask(getInfo(), dlg);
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newModifyTask, errors);
      }
      if (errors.size() == 0)
      {
        String attributeName = index.getName();
        String backendName = index.getBackend().getBackendID();
        launchOperation(newModifyTask,
            INFO_CTRL_PANEL_MODIFYING_INDEX_SUMMARY.get(attributeName),
            INFO_CTRL_PANEL_MODIFYING_INDEX_COMPLETE.get(),
            INFO_CTRL_PANEL_MODIFYING_INDEX_SUCCESSFUL.get(attributeName,
                backendName),
            ERR_CTRL_PANEL_MODIFYING_INDEX_ERROR_SUMMARY.get(),
            ERR_CTRL_PANEL_MODIFYING_INDEX_ERROR_DETAILS.get(attributeName),
            null,
            dlg);
        saveChanges.setEnabled(false);
        dlg.setVisible(true);
      }
    }

    if (errors.size() > 0)
    {
      displayErrorDialog(errors);
    }
  }

  /**
   * Updates the contents of the panel with the provided index.
   * @param index the index descriptor to be used to update the panel.
   */
  public void update(IndexDescriptor index)
  {
    ignoreCheckSave = true;
    setPrimaryValid(lEntryLimit);
    setPrimaryValid(lType);
    name.setText(index.getName());
    backendName.setText(index.getBackend().getBackendID());
    titlePanel.setDetails(Message.raw(index.getName()));
    entryLimit.setText(String.valueOf(index.getEntryLimit()));
    approximate.setSelected(false);
    equality.setSelected(false);
    ordering.setSelected(false);
    substring.setSelected(false);
    presence.setSelected(false);
    for (IndexType type : index.getTypes())
    {
      switch(type)
      {
      case APPROXIMATE:
        approximate.setSelected(true);
        break;
      case PRESENCE:
        presence.setSelected(true);
        break;
      case EQUALITY:
        equality.setSelected(true);
        break;
      case ORDERING:
        ordering.setSelected(true);
        break;
      case SUBSTRING:
        substring.setSelected(true);
        break;
      }
    }

    JComponent[] comps = {entryLimit, lType, typesPanel, lEntryLimit};

    for (int i=0; i<comps.length; i++)
    {
      comps[i].setVisible(!index.isDatabaseIndex());
    }

    AttributeType attr = index.getAttributeType();
    repopulateTypesPanel(attr);

    if (index.isDatabaseIndex())
    {
      entryLimit.setText("");
    }
    saveChanges.setVisible(!index.isDatabaseIndex());
    deleteIndex.setVisible(!index.isDatabaseIndex());
    if (index.isDatabaseIndex())
    {
      Utilities.setWarningLabel(warning, NON_CONFIGURABLE_INDEX);
      warning.setVisible(true);
    }
    else if (getInfo() != null)
    {
      if (getInfo().mustReindex(index))
      {
        Utilities.setWarningLabel(warning, INDEX_MODIFIED);
        warning.setVisible(true);
        warning.setVerticalTextPosition(SwingConstants.TOP);
      }
      else
      {
        warning.setVisible(false);
      }
    }
    this.index = index;

    ignoreCheckSave = false;
    checkSaveButton();

    scrollListener.updateBorder();
  }

  /**
   * Returns <CODE>true</CODE> if the index has been modified and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the index has been modified and
   * <CODE>false</CODE> otherwise.
   */
  private boolean isModified()
  {
    return !getTypes().equals(index.getTypes()) ||
    !String.valueOf(index.getEntryLimit()).equals(entryLimit.getText());
  }

  /**
   * The task in charge of modifying the index.
   *
   */
  protected class ModifyIndexTask extends Task
  {
    private Set<String> backendSet;
    private String attributeName;
    private String backendName;
    private int entryLimitValue;
    private IndexDescriptor indexToModify;
    private SortedSet<IndexType> indexTypes = new TreeSet<IndexType>();
    private IndexDescriptor modifiedIndex;

    /**
     * The constructor of the task.
     * @param info the control panel info.
     * @param dlg the progress dialog that shows the progress of the task.
     */
    public ModifyIndexTask(ControlPanelInfo info, ProgressDialog dlg)
    {
      super(info, dlg);
      backendName = index.getBackend().getBackendID();
      backendSet = new HashSet<String>();
      backendSet.add(backendName);
      attributeName = index.getName();
      entryLimitValue = Integer.parseInt(entryLimit.getText());
      indexTypes = getTypes();

      indexToModify = index;
    }

    /**
     * {@inheritDoc}
     */
    public Type getType()
    {
      return Type.MODIFY_INDEX;
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
      return INFO_CTRL_PANEL_MODIFY_INDEX_TASK_DESCRIPTION.get(attributeName,
          backendName);
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

    /**
     * Updates the configuration of the modified index.
     * @throws OpenDsException if there is an error updating the configuration.
     */
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
                  INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_MODIFY_INDEX.get()+
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
                    INFO_CTRL_PANEL_MODIFYING_INDEX_PROGRESS.get(attributeName),
                    ColorAndFontConstants.progressFont));
          }
        });
        if (isServerRunning())
        {
          // Create additional indexes and display the equivalent command.
          // Everything is done in the method createAdditionalIndexes
          modifyIndex(getInfo().getDirContext());
        }
        else
        {
          modifyIndex();
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
     * Returns the LDIF representation of the modified index.
     * @return the LDIF representation of the modified index.
     */
    private String getIndexLDIF()
    {
      String dn = Utilities.getRDNString("ds-cfg-backend-id", backendName)+
      ",cn=Backends,cn=config";
      ArrayList<String> lines = new ArrayList<String>();
      lines.add("dn: "+
          Utilities.getRDNString("ds-cfg-attribute", attributeName)+
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

    private void modifyIndex() throws OpenDsException
    {
      LDIFImportConfig ldifImportConfig = null;
      try
      {
        String ldif = getIndexLDIF();

        ldifImportConfig = new LDIFImportConfig(new StringReader(ldif));
        LDIFReader reader = new LDIFReader(ldifImportConfig);
        Entry newConfigEntry = reader.readEntry();
        Entry oldEntry = DirectoryServer.getConfigEntry(
            newConfigEntry.getDN()).getEntry();
        DirectoryServer.getConfigHandler().replaceEntry(oldEntry,
            newConfigEntry,
            null);
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

    /**
     * Modifies index using the provided connection.
     * @param ctx the connection to be used to update the index configuration.
     * @throws OpenDsException if there is an error updating the server.
     */
    private void modifyIndex(InitialLdapContext ctx) throws OpenDsException
    {
      final StringBuilder sb = new StringBuilder();
      sb.append(getConfigCommandLineName());
      Collection<String> args =
        getObfuscatedCommandLineArguments(getDSConfigCommandLineArguments());
      for (String arg : args)
      {
        sb.append(" "+CommandBuilder.escapeValue(arg));
      }

      ManagementContext mCtx = LDAPManagementContext.createFromContext(
          JNDIDirContextAdaptor.adapt(ctx));
      RootCfgClient root = mCtx.getRootConfiguration();
      LocalDBBackendCfgClient backend =
        (LocalDBBackendCfgClient)root.getBackend(backendName);
      LocalDBIndexCfgClient index = backend.getLocalDBIndex(attributeName);
      if (!indexTypes.equals(indexToModify.getTypes()))
      {
        index.setIndexType(indexTypes);
      }
      if (entryLimitValue != index.getIndexEntryLimit())
      {
        index.setIndexEntryLimit(entryLimitValue);
      }
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

    /**
     * Returns the full command-line path of the dsconfig command-line if we
     * can provide an equivalent command-line (the server is running).
     * @return the full command-line path of the dsconfig command-line if we
     * can provide an equivalent command-line (the server is running).
     */
    private String getConfigCommandLineName()
    {
      if (isServerRunning() && isModified())
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
        modifiedIndex = new IndexDescriptor(attributeName,
            indexToModify.getAttributeType(),
            indexToModify.getBackend(),
            indexTypes,
            entryLimitValue);
        getInfo().registerModifiedIndex(modifiedIndex);
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
      if ((lastException == null) && (state == State.FINISHED_SUCCESSFULLY))
      {
        rebuildIndexIfNecessary(modifiedIndex, getProgressDialog());
      }
    }

    private ArrayList<String> getDSConfigCommandLineArguments()
    {
      ArrayList<String> args = new ArrayList<String>();
      args.add("set-local-db-index-prop");
      args.add("--backend-name");
      args.add(backendName);

      args.add("--index-name");
      args.add(attributeName);

      if (!indexTypes.equals(indexToModify.getTypes()))
      {
        for (IndexType newType : indexTypes)
        {
          args.add("--set");
          args.add("index-type:"+newType.toString());
        }
      }
      if (entryLimitValue != indexToModify.getEntryLimit())
      {
        args.add("--set");
        args.add("index-entry-limit:"+entryLimitValue);
      }
      args.addAll(getConnectionCommandLineArguments());
      args.add("--no-prompt");
      return args;
    }
  }
}
