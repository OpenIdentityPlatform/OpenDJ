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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.guitools.controlpanel.util.Utilities.*;
import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.server.config.client.BackendVLVIndexCfgClient;
import org.forgerock.opendj.server.config.client.PluggableBackendCfgClient;
import org.forgerock.opendj.server.config.client.RootCfgClient;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.CategorizedComboBoxElement;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVSortOrder;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.event.ScrollPaneBorderListener;
import org.opends.guitools.controlpanel.task.DeleteIndexTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.util.Utilities;

/**
 * The panel that displays an existing VLV index (it appears on the right of the
 * 'Manage Indexes' dialog).
 */
public class VLVIndexPanel extends AbstractVLVIndexPanel
{
  private static final long serialVersionUID = 6333337497315464283L;
  private static final LocalizableMessage INDEX_MODIFIED = INFO_CTRL_PANEL_INDEX_MODIFIED_MESSAGE.get();

  private final JButton deleteIndex = Utilities.createButton(INFO_CTRL_PANEL_DELETE_INDEX_LABEL.get());
  private final JButton saveChanges = Utilities.createButton(INFO_CTRL_PANEL_SAVE_CHANGES_LABEL.get());
  private final JLabel warning = Utilities.createDefaultLabel();

  private ScrollPaneBorderListener scrollListener;

  private ModifyVLVIndexTask newModifyTask;

  private boolean ignoreCheckSave;

  private VLVIndexDescriptor index;

  /** Default constructor. */
  public VLVIndexPanel()
  {
    super(null, null);
    createLayout();
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_VLV_INDEX_PANEL_TITLE.get();
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return baseDN;
  }

  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    final ServerDescriptor desc = ev.getNewDescriptor();
    if (updateLayout(desc))
    {
      LocalizableMessage msg = isLocal() ? INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_VLV_INDEX_EDITING.get()
                                         : INFO_CTRL_PANEL_CANNOT_CONNECT_TO_REMOTE_DETAILS.get(desc.getHostname());
      updateErrorPaneIfAuthRequired(desc, msg);
      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          checkSaveButton();
          deleteIndex.setEnabled(!authenticationRequired(desc));
        }
      });
    }
  }

  @Override
  public void okClicked()
  {
    // no-op
  }

  /**
   * Method used to know if there are unsaved changes or not. It is used by the
   * index selection listener when the user changes the selection.
   *
   * @return <CODE>true</CODE> if there are unsaved changes (and so the
   *         selection of the index should be canceled) and <CODE>false</CODE>
   *         otherwise.
   */
  public boolean mustCheckUnsavedChanges()
  {
    return index != null && saveChanges.isVisible() && saveChanges.isEnabled();
  }

  /**
   * Tells whether the user chose to save the changes in the panel, to not save
   * them or simply cancelled the selection in the tree.
   *
   * @return the value telling whether the user chose to save the changes in the
   *         panel, to not save them or simply cancelled the selection change in
   *         the tree.
   */
  public UnsavedChangesDialog.Result checkUnsavedChanges()
  {
    UnsavedChangesDialog.Result result;
    final UnsavedChangesDialog unsavedChangesDlg = new UnsavedChangesDialog(getParentDialog(this), getInfo());
    unsavedChangesDlg.setMessage(INFO_CTRL_PANEL_UNSAVED_CHANGES_SUMMARY.get(),
                                 INFO_CTRL_PANEL_UNSAVED_INDEX_CHANGES_DETAILS.get(index.getName()));
    centerGoldenMean(unsavedChangesDlg, getParentDialog(this));
    unsavedChangesDlg.setVisible(true);
    result = unsavedChangesDlg.getResult();
    if (result == UnsavedChangesDialog.Result.SAVE)
    {
      saveIndex(false);
      if (newModifyTask == null
       || newModifyTask.getState() != Task.State.FINISHED_SUCCESSFULLY) // The user data is not valid
      {
        result = UnsavedChangesDialog.Result.CANCEL;
      }
    }

    return result;
  }

  private void checkSaveButton()
  {
    if (!ignoreCheckSave && index != null)
    {
      saveChanges.setEnabled(!authenticationRequired(getInfo().getServerDescriptor()) && isModified());
    }
  }

  @Override
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.NO_BUTTON;
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    final JPanel p = new JPanel(new GridBagLayout());
    p.setOpaque(false);
    super.createBasicLayout(p, gbc, true);
    p.setBorder(new EmptyBorder(10, 10, 10, 10));
    gbc = new GridBagConstraints();
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridx = 0;
    gbc.gridy = 0;
    final JScrollPane scroll = Utilities.createBorderLessScrollBar(p);
    scrollListener = ScrollPaneBorderListener.createBottomBorderListener(scroll);
    add(scroll, gbc);

    gbc.gridy++;
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

    gbc.gridy++;
    final JPanel buttonPanel = new JPanel(new GridBagLayout());
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
      @Override
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
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        saveIndex(false);
      }
    });

    final DocumentListener documentListener = new DocumentListener()
    {
      @Override
      public void insertUpdate(DocumentEvent ev)
      {
        checkSaveButton();
      }

      @Override
      public void changedUpdate(DocumentEvent ev)
      {
        checkSaveButton();
      }

      @Override
      public void removeUpdate(DocumentEvent ev)
      {
        checkSaveButton();
      }
    };

    final ActionListener actionListener = new ActionListener()
    {
      @Override
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
      @Override
      public void contentsChanged(ListDataEvent e)
      {
        checkSaveButton();
      }

      @Override
      public void intervalAdded(ListDataEvent e)
      {
        checkSaveButton();
      }

      @Override
      public void intervalRemoved(ListDataEvent e)
      {
        checkSaveButton();
      }
    });

    baseDN.getDocument().addDocumentListener(documentListener);
    filter.getDocument().addDocumentListener(documentListener);
    baseDN.getDocument().addDocumentListener(documentListener);
  }

  private void deleteIndex()
  {
    final List<LocalizableMessage> errors = new ArrayList<>();
    final ProgressDialog dlg = new ProgressDialog(
        createFrame(), getParentDialog(this), INFO_CTRL_PANEL_DELETE_VLV_INDEX_TITLE.get(), getInfo());
    final List<AbstractIndexDescriptor> indexesToDelete = new ArrayList<>();
    indexesToDelete.add(index);
    final DeleteIndexTask newTask = new DeleteIndexTask(getInfo(), dlg, indexesToDelete);
    for (final Task task : getInfo().getTasks())
    {
      task.canLaunch(newTask, errors);
    }

    if (errors.isEmpty())
    {
      final String indexName = index.getName();
      final String backendName = index.getBackend().getBackendID();
      if (displayConfirmationDialog(INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
                                    INFO_CTRL_PANEL_CONFIRMATION_VLV_INDEX_DELETE_DETAILS.get(indexName, backendName)))
      {
        launchOperation(newTask,
                        INFO_CTRL_PANEL_DELETING_VLV_INDEX_SUMMARY.get(),
                        INFO_CTRL_PANEL_DELETING_VLV_INDEX_COMPLETE.get(),
                        INFO_CTRL_PANEL_DELETING_VLV_INDEX_SUCCESSFUL.get(indexName, backendName),
                        ERR_CTRL_PANEL_DELETING_VLV_INDEX_ERROR_SUMMARY.get(),
                        ERR_CTRL_PANEL_DELETING_VLV_INDEX_ERROR_DETAILS.get(indexName),
                        null, dlg);
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
    final List<LocalizableMessage> errors = checkErrors(false);

    if (errors.isEmpty())
    {
      final ProgressDialog dlg =
          new ProgressDialog(getFrame(this), getFrame(this), INFO_CTRL_PANEL_MODIFYING_INDEX_TITLE.get(), getInfo());
      dlg.setModal(modal);
      newModifyTask = new ModifyVLVIndexTask(getInfo(), dlg);
      for (final Task task : getInfo().getTasks())
      {
        task.canLaunch(newModifyTask, errors);
      }
      if (errors.isEmpty() && checkIndexRequired())
      {
        final String indexName = index.getName();
        final String backendName = index.getBackend().getBackendID();
        launchOperation(newModifyTask,
                        INFO_CTRL_PANEL_MODIFYING_VLV_INDEX_SUMMARY.get(indexName),
                        INFO_CTRL_PANEL_MODIFYING_VLV_INDEX_COMPLETE.get(),
                        INFO_CTRL_PANEL_MODIFYING_VLV_INDEX_SUCCESSFUL.get(indexName, backendName),
                        ERR_CTRL_PANEL_MODIFYING_VLV_INDEX_ERROR_SUMMARY.get(),
                        ERR_CTRL_PANEL_MODIFYING_VLV_INDEX_ERROR_DETAILS.get(indexName),
                        null, dlg);
        saveChanges.setEnabled(false);
        dlg.setVisible(true);
      }
    }

    if (!errors.isEmpty())
    {
      displayErrorDialog(errors);
    }
  }

  /**
   * Updates the contents of the panel with the provided VLV index.
   *
   * @param index
   *          the VLV index descriptor to be used to update the panel.
   */
  public void update(VLVIndexDescriptor index)
  {
    ignoreCheckSave = true;
    readOnlyName.setText(index.getName());
    titlePanel.setDetails(LocalizableMessage.raw(index.getName()));
    if (index.getBackend() != null)
    {
      updateBaseDNCombo(index.getBackend());
      backendName.setText(index.getBackend().getBackendID());
    }
    final String dn = Utilities.unescapeUtf8(index.getBaseDN().toString());
    if (((DefaultComboBoxModel) baseDNs.getModel()).getIndexOf(dn) != -1)
    {
      baseDN.setText("");
      baseDNs.setSelectedItem(dn);
    }
    else
    {
      baseDN.setText(dn);
      baseDNs.setSelectedItem(OTHER_BASE_DN);
    }

    selectScopeRadioButton(index.getScope());
    filter.setText(index.getFilter());

    // Simulate a remove to update the attribute combo box and add them again.
    final int indexes[] = new int[sortOrderModel.getSize()];
    for (int i = 0; i < indexes.length; i++)
    {
      indexes[i] = i;
    }
    sortOrder.setSelectedIndices(indexes);
    remove.doClick();

    // The list is now empty and the attribute combo properly updated.
    final DefaultComboBoxModel model = (DefaultComboBoxModel) attributes.getModel();
    for (final VLVSortOrder s : index.getSortOrder())
    {
      sortOrderModel.addElement(s);
      for (int i = 0; i < model.getSize(); i++)
      {
        final CategorizedComboBoxElement o = (CategorizedComboBoxElement) model.getElementAt(i);
        if (o.getType() == CategorizedComboBoxElement.Type.REGULAR && o.getValue().equals(s.getAttributeName()))
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
        setWarningLabel(warning, INDEX_MODIFIED);
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

  private void selectScopeRadioButton(final SearchScope indexScope)
  {
    switch (indexScope.asEnum())
    {
    case BASE_OBJECT:
      baseObject.setSelected(true);
      break;
    case SINGLE_LEVEL:
      singleLevel.setSelected(true);
      break;
    case SUBORDINATES:
      subordinateSubtree.setSelected(true);
      break;
    case WHOLE_SUBTREE:
      wholeSubtree.setSelected(true);
      break;
    default:
      break;
    }
  }

  private boolean isModified()
  {
    try
    {
      return !index.getBaseDN().equals(DN.valueOf(getBaseDN())) || !index.getScope().equals(getScope())
          || !index.getFilter().equals(filter.getText().trim()) || !index.getSortOrder().equals(getSortOrder());
    }
    catch (final LocalizedIllegalArgumentException unused)
    {
      // The base DN is not valid.  This means that the index has been modified.
      return true;
    }
  }

  /** The task in charge of modifying the VLV index. */
  private class ModifyVLVIndexTask extends Task
  {
    private final Set<String> backendSet;
    private final String indexName;
    private final String baseDN;
    private final String filterValue;
    private final SearchScope searchScope;
    private final List<VLVSortOrder> sortOrder;
    private final String backendID;
    private final String sortOrderStringValue;
    private final VLVIndexDescriptor indexToModify;
    private VLVIndexDescriptor modifiedIndex;

    /**
     * The constructor of the task.
     *
     * @param info
     *          the control panel info.
     * @param dlg
     *          the progress dialog that shows the progress of the task.
     */
    private ModifyVLVIndexTask(ControlPanelInfo info, ProgressDialog dlg)
    {
      super(info, dlg);
      backendID = index.getBackend().getBackendID();
      backendSet = new HashSet<>();
      backendSet.add(backendID);
      indexName = index.getName();
      sortOrder = getSortOrder();
      baseDN = getBaseDN();
      filterValue = filter.getText().trim();
      searchScope = getScope();
      sortOrderStringValue = getSortOrderStringValue(sortOrder);
      indexToModify = index;
    }

    @Override
    public Type getType()
    {
      return Type.MODIFY_INDEX;
    }

    @Override
    public Set<String> getBackends()
    {
      return backendSet;
    }

    @Override
    public LocalizableMessage getTaskDescription()
    {
      return INFO_CTRL_PANEL_MODIFY_VLV_INDEX_TASK_DESCRIPTION.get(indexName, backendID);
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
              printEquivalentCommandLine(getConfigCommandLineName(), args,
                  INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_MODIFY_VLV_INDEX.get());
            }
          });
        }
        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
          public void run()
          {
            getProgressDialog().appendProgressHtml(
                Utilities.getProgressWithPoints(INFO_CTRL_PANEL_MODIFYING_VLV_INDEX_PROGRESS.get(indexName),
                    ColorAndFontConstants.progressFont));
          }
        });

        if (isServerRunning())
        {
          modifyVLVIndexOnline(getInfo().getConnection());
        }
        else
        {
          modifyVLVIndexOffline(backendID, indexName, indexToModify, DN.valueOf(baseDN), filterValue,
              searchScope, sortOrder);
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

    /**
     * Modifies index using the provided connection.
     *
     * @param ctx
     *          the connection to be used to update the index configuration.
     * @throws Exception
     *           if there is an error updating the server.
     */
    private void modifyVLVIndexOnline(ConnectionWrapper connWrapper) throws Exception
    {
      final RootCfgClient root = connWrapper.getRootConfiguration();
      modifyBackendVLVIndexOnline((PluggableBackendCfgClient) root.getBackend(backendID));
    }

    private void modifyBackendVLVIndexOnline(final PluggableBackendCfgClient backend) throws Exception
    {
      final BackendVLVIndexCfgClient index = backend.getBackendVLVIndex(indexName);
      final DN b = DN.valueOf(baseDN);
      if (!indexToModify.getBaseDN().equals(b))
      {
        index.setBaseDN(b);
      }

      if (!indexToModify.getFilter().equals(filterValue))
      {
        index.setFilter(filterValue);
      }

      if (indexToModify.getScope() != searchScope)
      {
        index.setScope(VLVIndexDescriptor.getBackendVLVIndexScope(searchScope));
      }

      if (!indexToModify.getSortOrder().equals(sortOrder))
      {
        index.setSortOrder(sortOrderStringValue);
      }
      index.commit();
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

    private String getConfigCommandLineName()
    {
      if (isServerRunning() && isModified())
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
        modifiedIndex = new VLVIndexDescriptor(
            indexName, indexToModify.getBackend(), DN.valueOf(baseDN), searchScope, filterValue, sortOrder);
        getInfo().registerModifiedIndex(modifiedIndex);
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
      if (lastException == null && state == State.FINISHED_SUCCESSFULLY)
      {
        rebuildIndexIfNecessary(modifiedIndex, getProgressDialog());
      }
    }

    private List<String> getDSConfigCommandLineArguments()
    {
      final List<String> args = new ArrayList<>();
      args.add("set-backend-vlv-index-prop");
      args.add("--backend-name");
      args.add(backendID);

      args.add("--index-name");
      args.add(indexName);

      if (!indexToModify.getBaseDN().equals(DN.valueOf(baseDN)))
      {
        args.add("--set");
        args.add("base-dn:" + baseDN);
      }

      if (indexToModify.getScope() != searchScope)
      {
        args.add("--set");
        args.add("scope:" + VLVIndexDescriptor.getBackendVLVIndexScope(searchScope));
      }
      if (!indexToModify.getFilter().equals(filterValue))
      {
        args.add("--set");
        args.add("filter:" + filterValue);
      }

      if (!indexToModify.getSortOrder().equals(sortOrder))
      {
        args.add("--set");
        args.add("sort-order:" + sortOrderStringValue);
      }

      args.addAll(getConnectionCommandLineArguments());
      args.add(getNoPropertiesFileArgument());
      args.add("--no-prompt");

      return args;
    }
  }
}
