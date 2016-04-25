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
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

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

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.client.BackendCfgClient;
import org.forgerock.opendj.server.config.client.BackendIndexCfgClient;
import org.forgerock.opendj.server.config.client.PluggableBackendCfgClient;
import org.forgerock.opendj.server.config.meta.BackendIndexCfgDefn.IndexType;
import org.opends.admin.ads.util.ConnectionWrapper;
import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.event.ScrollPaneBorderListener;
import org.opends.guitools.controlpanel.task.DeleteIndexTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.util.Utilities;

/**
 * The panel that displays an existing index (it appears on the right of the
 * 'Manage Indexes' dialog).
 */
class IndexPanel extends AbstractIndexPanel
{
  private static final long serialVersionUID = 1439500626486823366L;

  private IndexDescriptor index;
  private ScrollPaneBorderListener scrollListener;

  private boolean ignoreCheckSave;

  private ModifyIndexTask newModifyTask;

  /** Default constructor. */
  public IndexPanel()
  {
    super();
    createLayout();
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
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

    entryLimit.getDocument().addDocumentListener(new DocumentListener()
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
    });

    ActionListener listener = new ActionListener()
    {
      @Override
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

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_INDEX_PANEL_TITLE.get();
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return entryLimit;
  }

  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    final ServerDescriptor desc = ev.getNewDescriptor();
    updateErrorPaneIfAuthRequired(desc,
        isLocal() ?
            INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_INDEX_EDITING.get() :
      INFO_CTRL_PANEL_CANNOT_CONNECT_TO_REMOTE_DETAILS.get(desc.getHostname()));
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
  boolean mustCheckUnsavedChanges()
  {
    return index != null &&
        saveChanges.isVisible() && saveChanges.isEnabled();
  }

  /**
   * Tells whether the user chose to save the changes in the panel, to not save
   * them or simply cancelled the selection in the tree.
   *
   * @return the value telling whether the user chose to save the changes in the
   *         panel, to not save them or simply cancelled the selection change in
   *         the tree.
   */
  UnsavedChangesDialog.Result checkUnsavedChanges()
  {
    UnsavedChangesDialog.Result result;
    UnsavedChangesDialog unsavedChangesDlg = new UnsavedChangesDialog(Utilities.getParentDialog(this), getInfo());
    unsavedChangesDlg.setMessage(INFO_CTRL_PANEL_UNSAVED_CHANGES_SUMMARY.get(),
                                 INFO_CTRL_PANEL_UNSAVED_INDEX_CHANGES_DETAILS.get(index.getName()));
    Utilities.centerGoldenMean(unsavedChangesDlg, Utilities.getParentDialog(this));
    unsavedChangesDlg.setVisible(true);
    result = unsavedChangesDlg.getResult();
    if (result == UnsavedChangesDialog.Result.SAVE)
    {
      saveIndex(false);
      if (newModifyTask == null || // The user data is not valid
          newModifyTask.getState() != Task.State.FINISHED_SUCCESSFULLY)
      {
        result = UnsavedChangesDialog.Result.CANCEL;
      }
    }

    return result;
  }

  /** Checks the enabling state of the save button. */
  private void checkSaveButton()
  {
    if (!ignoreCheckSave && index != null)
    {
      saveChanges.setEnabled(
          !authenticationRequired(getInfo().getServerDescriptor()) &&
          isModified());
    }
  }

  private void deleteIndex()
  {
    List<LocalizableMessage> errors = new ArrayList<>();
    ProgressDialog dlg = new ProgressDialog(
        Utilities.createFrame(),
        Utilities.getParentDialog(this),
        INFO_CTRL_PANEL_DELETE_INDEX_TITLE.get(), getInfo());
    ArrayList<AbstractIndexDescriptor> indexesToDelete = new ArrayList<>();
    indexesToDelete.add(index);
    DeleteIndexTask newTask = new DeleteIndexTask(getInfo(), dlg, indexesToDelete);
    for (Task task : getInfo().getTasks())
    {
      task.canLaunch(newTask, errors);
    }

    if (errors.isEmpty())
    {
      String indexName = index.getName();
      String backendName = index.getBackend().getBackendID();
      if (displayConfirmationDialog(INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
                                    INFO_CTRL_PANEL_CONFIRMATION_INDEX_DELETE_DETAILS.get(indexName, backendName)))
      {
        launchOperation(newTask,
            INFO_CTRL_PANEL_DELETING_INDEX_SUMMARY.get(),
            INFO_CTRL_PANEL_DELETING_INDEX_COMPLETE.get(),
            INFO_CTRL_PANEL_DELETING_INDEX_SUCCESSFUL.get(indexName, backendName),
            ERR_CTRL_PANEL_DELETING_INDEX_ERROR_SUMMARY.get(),
            ERR_CTRL_PANEL_DELETING_INDEX_ERROR_DETAILS.get(indexName), null, dlg);
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
   *
   * @param modal
   *          whether the progress dialog for the task must be modal or not.
   */
  private void saveIndex(boolean modal)
  {
    newModifyTask = null;
    if (!isModified())
    {
      return;
    }

    List<LocalizableMessage> errors = getErrors();

    if (errors.isEmpty())
    {
      ProgressDialog dlg = new ProgressDialog(
          Utilities.getFrame(this),
          Utilities.getFrame(this),
          INFO_CTRL_PANEL_MODIFYING_INDEX_TITLE.get(), getInfo());
      dlg.setModal(modal);
      newModifyTask = new ModifyIndexTask(getInfo(), dlg);
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newModifyTask, errors);
      }
      if (errors.isEmpty())
      {
        String attributeName = index.getName();
        String backendName = index.getBackend().getBackendID();
        launchOperation(newModifyTask,
            INFO_CTRL_PANEL_MODIFYING_INDEX_SUMMARY.get(attributeName),
            INFO_CTRL_PANEL_MODIFYING_INDEX_COMPLETE.get(),
            INFO_CTRL_PANEL_MODIFYING_INDEX_SUCCESSFUL.get(attributeName, backendName),
            ERR_CTRL_PANEL_MODIFYING_INDEX_ERROR_SUMMARY.get(),
            ERR_CTRL_PANEL_MODIFYING_INDEX_ERROR_DETAILS.get(attributeName), null, dlg);
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
   * Updates the contents of the panel with the provided index.
   *
   * @param index
   *          the index descriptor to be used to update the panel.
   */
  void update(IndexDescriptor index)
  {
    ignoreCheckSave = true;
    setPrimaryValid(lEntryLimit);
    setPrimaryValid(lType);
    name.setText(index.getName());
    backendName.setText(index.getBackend().getBackendID());
    titlePanel.setDetails(LocalizableMessage.raw(index.getName()));
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

    for (JComponent comp : comps)
    {
      comp.setVisible(!index.isDatabaseIndex());
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
   *
   * @return <CODE>true</CODE> if the index has been modified and
   *         <CODE>false</CODE> otherwise.
   */
  private boolean isModified()
  {
    return !getTypes().equals(index.getTypes()) ||
    !String.valueOf(index.getEntryLimit()).equals(entryLimit.getText());
  }

  /** The task in charge of modifying the index. */
  private class ModifyIndexTask extends Task
  {
    private final Set<String> backendSet;
    private final String attributeName;
    private final String backendName;
    private final int entryLimitValue;
    private final IndexDescriptor indexToModify;
    private final SortedSet<IndexType> indexTypes;
    private IndexDescriptor modifiedIndex;

    /**
     * The constructor of the task.
     *
     * @param info
     *          the control panel info.
     * @param dlg
     *          the progress dialog that shows the progress of the task.
     */
    private ModifyIndexTask(ControlPanelInfo info, ProgressDialog dlg)
    {
      super(info, dlg);
      backendName = index.getBackend().getBackendID();
      backendSet = new HashSet<>();
      backendSet.add(backendName);
      attributeName = index.getName();
      entryLimitValue = Integer.parseInt(entryLimit.getText());
      indexTypes = getTypes();

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
      return INFO_CTRL_PANEL_MODIFY_INDEX_TASK_DESCRIPTION.get(attributeName,
          backendName);
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

    /**
     * Updates the configuration of the modified index.
     *
     * @throws Exception
     *           if there is an error updating the configuration.
     */
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
              StringBuilder sb = new StringBuilder();
              sb.append(getConfigCommandLineName());
              List<String> args = getObfuscatedCommandLineArguments(getDSConfigCommandLineArguments());
              args.removeAll(getConfigCommandLineArguments());

              printEquivalentCommandLine(
                      getConfigCommandLineName(), args, INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_MODIFY_INDEX.get());
            }
          });
        }

        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
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
          modifyIndexOnline(getInfo().getConnection());
        }
        else
        {
          modifyIndexOffline(backendName, attributeName, indexToModify, indexTypes, entryLimitValue);
        }

        SwingUtilities.invokeLater(new Runnable()
        {
          @Override
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
          startPoolingAndInitializeConfiguration();
        }
      }
    }

    /**
     * Modifies index using the provided connection.
     *
     * @param connWrapper
     *          the connection to be used to update the index configuration.
     * @throws Exception
     *           if there is an error updating the server.
     */
    private void modifyIndexOnline(final ConnectionWrapper connWrapper) throws Exception
    {
      final BackendCfgClient backend = connWrapper.getRootConfiguration().getBackend(backendName);
      modifyBackendIndexOnline((PluggableBackendCfgClient) backend);
    }

    private void modifyBackendIndexOnline(final PluggableBackendCfgClient backend) throws Exception
    {
      final BackendIndexCfgClient index = backend.getBackendIndex(attributeName);
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
     * Returns the full command-line path of the dsconfig command-line if we can
     * provide an equivalent command-line (the server is running).
     *
     * @return the full command-line path of the dsconfig command-line if we can
     *         provide an equivalent command-line (the server is running).
     */
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
      List<String> args = new ArrayList<>();
      args.add("set-backend-index-prop");
      args.add("--backend-name");
      args.add(backendName);

      args.add("--index-name");
      args.add(attributeName);

      if (!indexTypes.equals(indexToModify.getTypes()))
      {
        // To add
        Set<IndexType> toAdd = new TreeSet<>();
        for (IndexType newType : indexTypes)
        {
          if (!indexToModify.getTypes().contains(newType))
          {
            toAdd.add(newType);
          }
        }
        // To delete
        Set<IndexType> toDelete = new TreeSet<>();
        for (IndexType oldType : indexToModify.getTypes())
        {
          if (!indexTypes.contains(oldType))
          {
            toDelete.add(oldType);
          }
        }
        for (IndexType newType : toDelete)
        {
          args.add("--remove");
          args.add("index-type:" + newType);
        }
        for (IndexType newType : toAdd)
        {
          args.add("--add");
          args.add("index-type:" + newType);
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
