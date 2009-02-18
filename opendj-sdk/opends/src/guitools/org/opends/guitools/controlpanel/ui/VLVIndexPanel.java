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
import java.util.TreeSet;

import javax.naming.ldap.InitialLdapContext;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.CategorizedComboBoxElement;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVSortOrder;
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
import org.opends.server.admin.std.client.LocalDBVLVIndexCfgClient;
import org.opends.server.admin.std.client.RootCfgClient;
import org.opends.server.admin.std.meta.LocalDBVLVIndexCfgDefn.Scope;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.OpenDsException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.cli.CommandBuilder;

/**
 * The panel that displays an existing VLV index (it appears on the right of the
 * 'Manage Indexes' dialog).
 *
 */
public class VLVIndexPanel extends AbstractVLVIndexPanel
{
  private static final long serialVersionUID = 6333337497315464283L;
  private JButton deleteIndex = Utilities.createButton(
      INFO_CTRL_PANEL_DELETE_INDEX_LABEL.get());
  private JButton saveChanges = Utilities.createButton(
      INFO_CTRL_PANEL_SAVE_CHANGES_LABEL.get());
  private JLabel warning = Utilities.createDefaultLabel();

  private ScrollPaneBorderListener scrollListener;

  private ModifyVLVIndexTask newModifyTask;

  private boolean ignoreCheckSave;

  private Message INDEX_MODIFIED =
    INFO_CTRL_PANEL_INDEX_MODIFIED_MESSAGE.get();


  private VLVIndexDescriptor index;

  /**
   * Default constructor.
   *
   */
  public VLVIndexPanel()
  {
    super(null, null);
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_VLV_INDEX_PANEL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return baseDN;
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(final ConfigurationChangeEvent ev)
  {
    if (updateLayout(ev.getNewDescriptor()))
    {
      updateErrorPaneIfAuthRequired(ev.getNewDescriptor(),
          INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_VLV_INDEX_EDITING.get());
      SwingUtilities.invokeLater(new Runnable()
      {
        /**
         * {@inheritDoc}
         */
        public void run()
        {
          checkSaveButton();
          deleteIndex.setEnabled(
              !authenticationRequired(ev.getNewDescriptor()));
        }
      });
    }
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

  private void checkSaveButton()
  {
    if (!ignoreCheckSave && (index != null))
    {
      saveChanges.setEnabled(
          !authenticationRequired(getInfo().getServerDescriptor()) &&
          isModified());
    }
  }

  /**
   * {@inheritDoc}
   */
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.NO_BUTTON;
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
    scrollListener =
      ScrollPaneBorderListener.createBottomBorderListener(scroll);
    add(scroll, gbc);

    gbc.gridy ++;
    gbc.gridx = 0;
    gbc.weightx = 1.0;
    gbc.weighty = 0.0;
    gbc.insets.left = 0;
    gbc.gridwidth = 2;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets.top = 10;

    gbc.gridwidth = 3;
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

    DocumentListener documentListener = new DocumentListener()
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
    };

    ActionListener actionListener = new ActionListener()
    {
      /**
       * {@inheritDoc}
       */
      public void actionPerformed(ActionEvent ev)
      {
        checkSaveButton();
      }
    };


    baseDNs.addActionListener(actionListener);
    baseObject.addActionListener(actionListener);
    singleLevel.addActionListener(actionListener);
    subordinateSubtree.addActionListener(actionListener);
    wholeSubtree.addActionListener(actionListener);
    attributes.addActionListener(actionListener);
    sortOrder.getModel().addListDataListener(new ListDataListener()
    {
      /**
       * {@inheritDoc}
       */
      public void contentsChanged(ListDataEvent e)
      {
        checkSaveButton();
      }

      /**
       * {@inheritDoc}
       */
      public void intervalAdded(ListDataEvent e)
      {
        checkSaveButton();
      }

      /**
       * {@inheritDoc}
       */
      public void intervalRemoved(ListDataEvent e)
      {
        checkSaveButton();
      }
    });

    baseDN.getDocument().addDocumentListener(documentListener);
    filter.getDocument().addDocumentListener(documentListener);
    maxBlockSize.getDocument().addDocumentListener(documentListener);
    baseDN.getDocument().addDocumentListener(documentListener);
  }

  private void deleteIndex()
  {
    ArrayList<Message> errors = new ArrayList<Message>();
    ProgressDialog dlg = new ProgressDialog(
        Utilities.getParentDialog(this),
        INFO_CTRL_PANEL_DELETE_VLV_INDEX_TITLE.get(), getInfo());
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
          INFO_CTRL_PANEL_CONFIRMATION_VLV_INDEX_DELETE_DETAILS.get(indexName,
              backendName)))
      {
        launchOperation(newTask,
            INFO_CTRL_PANEL_DELETING_VLV_INDEX_SUMMARY.get(),
            INFO_CTRL_PANEL_DELETING_VLV_INDEX_COMPLETE.get(),
            INFO_CTRL_PANEL_DELETING_VLV_INDEX_SUCCESSFUL.get(indexName,
                backendName),
            ERR_CTRL_PANEL_DELETING_VLV_INDEX_ERROR_SUMMARY.get(),
            ERR_CTRL_PANEL_DELETING_VLV_INDEX_ERROR_DETAILS.get(indexName),
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

  private void saveIndex(boolean modal)
  {
    newModifyTask = null;
    if (!isModified())
    {
      return;
    }
    List<Message> errors = checkErrors(false);

    if (errors.isEmpty())
    {
      ProgressDialog dlg = new ProgressDialog(
          Utilities.getParentDialog(this),
          INFO_CTRL_PANEL_MODIFYING_INDEX_TITLE.get(), getInfo());
      dlg.setModal(modal);
      newModifyTask = new ModifyVLVIndexTask(getInfo(), dlg);
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newModifyTask, errors);
      }
      if (errors.isEmpty())
      {
        // Check filters
        if (checkIndexRequired())
        {
          String indexName = index.getName();
          String backendName = index.getBackend().getBackendID();
          launchOperation(newModifyTask,
              INFO_CTRL_PANEL_MODIFYING_VLV_INDEX_SUMMARY.get(indexName),
              INFO_CTRL_PANEL_MODIFYING_VLV_INDEX_COMPLETE.get(),
              INFO_CTRL_PANEL_MODIFYING_VLV_INDEX_SUCCESSFUL.get(indexName,
                  backendName),
              ERR_CTRL_PANEL_MODIFYING_VLV_INDEX_ERROR_SUMMARY.get(),
              ERR_CTRL_PANEL_MODIFYING_VLV_INDEX_ERROR_DETAILS.get(indexName),
              null,
              dlg);
          saveChanges.setEnabled(false);
          dlg.setVisible(true);
        }
      }
    }

    if (errors.size() > 0)
    {
      displayErrorDialog(errors);
    }
  }


  /**
   * Updates the contents of the panel with the provided VLV index.
   * @param index the VLV index descriptor to be used to update the panel.
   */
  public void update(VLVIndexDescriptor index)
  {
    ignoreCheckSave = true;
    readOnlyName.setText(index.getName());
    titlePanel.setDetails(Message.raw(index.getName()));
    if (index.getBackend() != null)
    {
      updateBaseDNCombo(index.getBackend());
      backendName.setText(index.getBackend().getBackendID());
    }
    String dn = Utilities.unescapeUtf8(index.getBaseDN().toString());
    if (((DefaultComboBoxModel)baseDNs.getModel()).getIndexOf(dn) != -1)
    {
      baseDN.setText("");
      baseDNs.setSelectedItem(dn);
    }
    else
    {
      baseDN.setText(dn);
      baseDNs.setSelectedItem(OTHER_BASE_DN);
    }
    switch (index.getScope())
    {
    case BASE_OBJECT:
      baseObject.setSelected(true);
      break;
    case SINGLE_LEVEL:
      singleLevel.setSelected(true);
      break;
    case SUBORDINATE_SUBTREE:
      subordinateSubtree.setSelected(true);
      break;
    case WHOLE_SUBTREE:
      wholeSubtree.setSelected(true);
      break;
    }
    filter.setText(index.getFilter());

    // Simulate a remove to update the attribute combo box and add them again.
    int indexes[] = new int[sortOrderModel.getSize()];

    for (int i=0; i<indexes.length; i++)
    {
      indexes[i] = i;
    }
    sortOrder.setSelectedIndices(indexes);
    remove.doClick();

    // The list is now empty and the attribute combo properly updated.
    DefaultComboBoxModel model =
      (DefaultComboBoxModel)attributes.getModel();
    for (VLVSortOrder s : index.getSortOrder())
    {
      sortOrderModel.addElement(s);
      for (int i=0; i<model.getSize(); i++)
      {
        CategorizedComboBoxElement o =
          (CategorizedComboBoxElement)model.getElementAt(i);
        if ((o.getType() == CategorizedComboBoxElement.Type.REGULAR) &&
            o.getValue().equals(s.getAttributeName()))
        {
          model.removeElementAt(i);
          break;
        }
      }
    }
    if (model.getSize() > 1)
    {
      attributes.setSelectedIndex(1);
    }

    if (getInfo() != null)
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

  private boolean isModified()
  {
    try
    {
      return !index.getBaseDN().equals(DN.decode(getBaseDN())) ||
      (getScope() != index.getScope()) ||
      !filter.getText().trim().equals(index.getFilter()) ||
      !getSortOrder().equals(index.getSortOrder()) ||
      !String.valueOf(index.getMaxBlockSize()).equals(
          maxBlockSize.getText().trim());
    }
    catch (OpenDsException odse)
    {
      // The base DN is not valid.  This means that the index has been modified.
      return true;
    }
  }

  /**
   * The task in charge of modifying the VLV index.
   *
   */
  protected class ModifyVLVIndexTask extends Task
  {
    private Set<String> backendSet;
    private String indexName;
    private String baseDN;
    private String filterValue;
    private Scope scope;
    private List<VLVSortOrder> sortOrder;
    private String backendID;
    private String sortOrderStringValue;
    private String ldif;
    private VLVIndexDescriptor indexToModify;
    private int maxBlock;
    private VLVIndexDescriptor modifiedIndex;

    /**
     * The constructor of the task.
     * @param info the control panel info.
     * @param dlg the progress dialog that shows the progress of the task.
     */
    public ModifyVLVIndexTask(ControlPanelInfo info, ProgressDialog dlg)
    {
      super(info, dlg);
      backendID = index.getBackend().getBackendID();
      backendSet = new HashSet<String>();
      backendSet.add(backendID);
      indexName = index.getName();
      sortOrder = getSortOrder();
      baseDN = getBaseDN();
      filterValue = filter.getText().trim();
      scope = getScope();
      sortOrderStringValue = getSortOrderStringValue(sortOrder);
      ldif = getIndexLDIF(indexName);
      maxBlock = Integer.parseInt(maxBlockSize.getText());
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
      return INFO_CTRL_PANEL_MODIFY_VLV_INDEX_TASK_DESCRIPTION.get(
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
                  INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_MODIFY_VLV_INDEX.get()+
                  "<br><b>"+sb.toString()+"</b><br><br>",
                  ColorAndFontConstants.progressFont));
            }
          });
        }
        SwingUtilities.invokeLater(new Runnable()
        {
          public void run()
          {
            getProgressDialog().appendProgressHtml(
                Utilities.getProgressWithPoints(
                    INFO_CTRL_PANEL_MODIFYING_VLV_INDEX_PROGRESS.get(indexName),
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

    private void modifyIndex() throws OpenDsException
    {
      LDIFImportConfig ldifImportConfig = null;
      try
      {
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
        (LocalDBBackendCfgClient)root.getBackend(backendID);
      LocalDBVLVIndexCfgClient index = backend.getLocalDBVLVIndex(indexName);
      DN b = DN.decode(baseDN);
      if (!indexToModify.getBaseDN().equals(b))
      {
        index.setBaseDN(b);
      }
      if (!indexToModify.getFilter().equals(filterValue))
      {
        index.setFilter(filterValue);
      }
      if (indexToModify.getScope() != scope)
      {
        index.setScope(scope);
      }
      if (!indexToModify.getScope().equals(sortOrder))
      {
        index.setSortOrder(sortOrderStringValue);
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
        modifiedIndex = new VLVIndexDescriptor(
            indexName, indexToModify.getBackend(), DN.decode(baseDN),
            scope, filterValue, sortOrder, maxBlock);
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
      args.add("set-local-db-vlv-index-prop");
      args.add("--backend-name");
      args.add(backendID);

      args.add("--index-name");
      args.add(indexName);

      try
      {
        DN b = DN.decode(baseDN);
        if (!indexToModify.getBaseDN().equals(b))
        {
          args.add("--set");
          args.add("base-dn:"+baseDN);
        }
      }
      catch (OpenDsException odse)
      {
        throw new IllegalStateException("Unexpected error parsing DN "+
            getBaseDN()+": "+odse, odse);
      }
      if (indexToModify.getScope() != scope)
      {
        args.add("--set");
        args.add("scope:"+scope.toString());
      }
      if (!indexToModify.getFilter().equals(filterValue))
      {
        args.add("--set");
        args.add("filter:"+filterValue);
      }

      if (!indexToModify.getScope().equals(sortOrder))
      {
        args.add("--set");
        args.add("sort-order:"+sortOrderStringValue);
      }

      args.addAll(getConnectionCommandLineArguments());
      args.add(getNoPropertiesFileArgument());
      args.add("--no-prompt");

      return args;
    }
  }
}
