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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.CategorizedComboBoxElement;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.datamodel.SortableListModel;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.event.IndexModifiedEvent;
import org.opends.guitools.controlpanel.event.IndexModifiedListener;
import org.opends.guitools.controlpanel.task.IndexTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.components.AddRemovePanel;
import org.opends.guitools.controlpanel.ui.renderer.CustomListCellRenderer;
import org.opends.guitools.controlpanel.ui.renderer.IndexCellRenderer;
import org.opends.guitools.controlpanel.ui.renderer.IndexComboBoxCellRenderer;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.guitools.controlpanel.util.ViewPositions;

/** The panel that appears when the user wants to verify an index. */
public class VerifyIndexPanel extends StatusGenericPanel implements IndexModifiedListener
{
  private static final long serialVersionUID = 5252070109221657041L;
  private JComboBox<?> baseDNs;
  private JRadioButton verifyIndexContents;
  private JRadioButton verifyKeyEntryIDs;
  private AddRemovePanel<AbstractIndexDescriptor> addRemove;
  private JComboBox<?> keyEntryIDs;
  private Map<String, SortedSet<AbstractIndexDescriptor>> hmIndexes = new HashMap<>();

  private JLabel lBaseDN;
  private JLabel lAction;
  private JLabel lIndex;
  private JLabel lNoBaseDNsFound;

  /** Constructor of the panel. */
  public VerifyIndexPanel()
  {
    super();
    createLayout();
  }

  @Override
  public void setInfo(ControlPanelInfo info)
  {
    super.setInfo(info);
    ListCellRenderer indexCellRenderer = new IndexCellRenderer(addRemove.getAvailableList(), info);
    addRemove.getAvailableList().setCellRenderer(indexCellRenderer);
    addRemove.getSelectedList().setCellRenderer(indexCellRenderer);
    info.addIndexModifiedListener(this);
  }

  @Override
  public void indexModified(IndexModifiedEvent ev)
  {
    refreshContents(getInfo().getServerDescriptor());
  }

  @Override
  public void backendIndexesModified(IndexModifiedEvent ev)
  {
    refreshContents(getInfo().getServerDescriptor());
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.WEST;
    gbc.weightx = 0.0;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 1;
    gbc.fill = GridBagConstraints.NONE;
    lBaseDN = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_BASE_DN_LABEL.get());
    add(lBaseDN, gbc);
    gbc.insets.left = 10;
    gbc.gridx = 1;
    gbc.gridy = 0;
    baseDNs = Utilities.createComboBox();
    DefaultComboBoxModel model = new DefaultComboBoxModel();
    baseDNs.setModel(model);
    baseDNs.setRenderer(new CustomListCellRenderer(baseDNs));
    ItemListener listener = new IgnoreItemListener(baseDNs);
    baseDNs.addItemListener(listener);
    baseDNs.addItemListener(new ItemListener()
    {
      @Override
      public void itemStateChanged(ItemEvent ev)
      {
        comboBoxSelected(hmIndexes, (CategorizedComboBoxElement) baseDNs.getSelectedItem(), addRemove);
        updateVerifyKeyEntriesComboBox();
      }
    });
    listener.itemStateChanged(null);
    gbc.gridwidth = 2;
    add(baseDNs, gbc);
    lNoBaseDNsFound = Utilities.createDefaultLabel(INFO_CTRL_PANEL_NO_BASE_DNS_FOUND_LABEL.get());
    add(lNoBaseDNsFound, gbc);
    lNoBaseDNsFound.setVisible(false);

    lAction = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_ACTION_LABEL.get());
    gbc.insets.top = 10;
    gbc.gridy++;
    gbc.gridx = 0;
    gbc.gridwidth = 1;
    add(lAction, gbc);

    verifyIndexContents = Utilities.createRadioButton(INFO_CTRL_PANEL_VERIFY_ENTRY_CONTEXT_ARE_INDEXES.get());
    verifyIndexContents.setSelected(true);
    gbc.insets.left = 10;
    gbc.gridx = 1;
    gbc.gridwidth = 2;
    add(verifyIndexContents, gbc);

    addRemove = new AddRemovePanel<>(AbstractIndexDescriptor.class);
    addRemove.getAvailableLabel().setText(INFO_CTRL_PANEL_AVAILABLE_INDEXES_LABEL.get().toString());
    addRemove.getSelectedLabel().setText(INFO_CTRL_PANEL_SELECTED_INDEXES_LABEL.get().toString());

    gbc.gridy++;
    gbc.weightx = 1.0;
    gbc.weighty = 0.0;
    gbc.gridwidth = 2;
    gbc.insets.top = 5;
    gbc.insets.left = 30;
    gbc.fill = GridBagConstraints.BOTH;
    add(addRemove, gbc);

    gbc.gridy++;
    JLabel explanation = Utilities.createInlineHelpLabel(INFO_CTRL_PANEL_REQUIRES_REBUILD_LEGEND.get());
    gbc.insets.top = 3;
    add(explanation, gbc);

    verifyKeyEntryIDs = Utilities.createRadioButton(INFO_CTRL_PANEL_VERIFY_ALL_KEYS.get());
    verifyKeyEntryIDs.setSelected(true);
    gbc.insets.left = 10;
    gbc.insets.top = 10;
    gbc.gridx = 1;
    gbc.gridwidth = 2;
    gbc.gridy++;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.NONE;
    add(verifyKeyEntryIDs, gbc);

    gbc.gridy++;
    gbc.insets.left = 30;
    gbc.insets.top = 5;
    gbc.gridwidth = 1;
    gbc.weightx = 0.0;
    lIndex = Utilities.createDefaultLabel(INFO_CTRL_PANEL_INDEX_LABEL.get());
    add(lIndex, gbc);

    keyEntryIDs = Utilities.createComboBox();
    model = new DefaultComboBoxModel();
    keyEntryIDs.setModel(model);
    keyEntryIDs.setRenderer(new IndexComboBoxCellRenderer(keyEntryIDs));
    listener = new IgnoreItemListener(keyEntryIDs);
    keyEntryIDs.addItemListener(listener);
    listener.itemStateChanged(null);
    gbc.gridx = 2;
    gbc.insets.left = 5;
    add(keyEntryIDs, gbc);

    addBottomGlue(gbc);

    ButtonGroup group = new ButtonGroup();
    group.add(verifyIndexContents);
    group.add(verifyKeyEntryIDs);
    verifyIndexContents.setSelected(true);
    listener = new ItemListener()
    {
      @Override
      public void itemStateChanged(ItemEvent ev)
      {
        addRemove.setEnabled(verifyIndexContents.isSelected());
        keyEntryIDs.setEnabled(!verifyIndexContents.isSelected());
        lIndex.setEnabled(!verifyIndexContents.isSelected());
      }
    };
    verifyIndexContents.addItemListener(listener);
    listener.itemStateChanged(null);
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_VERIFY_INDEXES_PANEL_TITLE.get();
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return baseDNs;
  }

  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    refreshContents(ev.getNewDescriptor());
  }

  private void refreshContents(final ServerDescriptor desc)
  {
    updateIndexMap(desc, hmIndexes);
    updateBaseDNComboBoxModel((DefaultComboBoxModel) baseDNs.getModel(), desc);
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        ViewPositions pos;
        JScrollPane scroll = Utilities.getContainingScroll(VerifyIndexPanel.this);
        if (scroll != null)
        {
          pos = Utilities.getViewPositions(scroll);
        }
        else
        {
          pos = Utilities.getViewPositions(VerifyIndexPanel.this);
        }
        comboBoxSelected(hmIndexes, (CategorizedComboBoxElement) baseDNs.getSelectedItem(), addRemove);
        updateVerifyKeyEntriesComboBox();
        addRemove.getAvailableList().repaint();
        addRemove.getSelectedList().repaint();
        boolean comboVisible = baseDNs.getModel().getSize() > 0;
        baseDNs.setVisible(comboVisible);
        lNoBaseDNsFound.setVisible(!comboVisible);
        Utilities.updateViewPositions(pos);

        if (!desc.isLocal())
        {
          displayErrorMessage(INFO_CTRL_PANEL_SERVER_REMOTE_SUMMARY.get(),
                              INFO_CTRL_PANEL_SERVER_MUST_BE_LOCAL_VERIFY_INDEX_SUMMARY.get());
        }
        else
        {
          displayMainPanel();
        }
        setEnabledOK(desc.isLocal());
      }
    });
  }

  @Override
  public void cancelClicked()
  {
    setPrimaryValid(lBaseDN);
    setSecondaryValid(addRemove.getSelectedLabel());
    setSecondaryValid(lIndex);
    super.cancelClicked();
  }

  @Override
  public void okClicked()
  {
    setPrimaryValid(lBaseDN);
    setSecondaryValid(addRemove.getSelectedLabel());
    setSecondaryValid(lIndex);

    final Set<LocalizableMessage> errors = new LinkedHashSet<>();
    String baseDN = getSelectedBaseDN();

    if (baseDN == null)
    {
      setPrimaryInvalid(lBaseDN);
      if (baseDNs.getItemCount() == 0)
      {
        errors.add(ERR_CTRL_PANEL_NO_BASE_DNS_DEFINED_LABEL.get());
      }
      else
      {
        errors.add(ERR_CTRL_PANEL_MUST_SELECT_BASE_DN.get());
      }
    }

    if (verifyIndexContents.isSelected())
    {
      SortableListModel<AbstractIndexDescriptor> model = addRemove.getSelectedListModel();
      if (model.getSize() == 0)
      {
        setSecondaryInvalid(addRemove.getSelectedLabel());
        errors.add(ERR_CTRL_PANEL_INDEX_TO_BE_VERIFIED_REQUIRED.get());
      }
    }
    else
    {
      AbstractIndexDescriptor index = getSelectedIndex();

      if (index == null)
      {
        setPrimaryInvalid(lIndex);
        final boolean indexSelected = keyEntryIDs.getItemCount() == 0 && baseDN != null;
        errors.add(indexSelected ? ERR_CTRL_PANEL_NO_INDEXES_FOR_BASEDN.get(baseDN)
                                 : ERR_CTRL_PANEL_INDEX_MUST_BE_SELECTED.get());
      }
    }

    if (errors.isEmpty())
    {
      ProgressDialog progressDialog =
          new ProgressDialog(Utilities.createFrame(), Utilities.getParentDialog(this), getTitle(), getInfo());
      VerifyIndexTask newTask = new VerifyIndexTask(getInfo(), progressDialog);
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newTask, errors);
      }
      if (errors.isEmpty())
      {
        launchOperation(newTask,
            INFO_CTRL_PANEL_VERIFYING_INDEXES_SUMMARY.get(baseDN),
            INFO_CTRL_PANEL_VERIFYING_INDEXES_SUCCESSFUL_SUMMARY.get(),
            INFO_CTRL_PANEL_VERIFYING_INDEXES_SUCCESSFUL_DETAILS.get(),
            ERR_CTRL_PANEL_VERIFYING_INDEXES_ERROR_SUMMARY.get(), null,
            ERR_CTRL_PANEL_VERIFYING_INDEXES_ERROR_DETAILS, progressDialog);
        progressDialog.setVisible(true);
        Utilities.getParentDialog(this).setVisible(false);
      }
    }
    if (!errors.isEmpty())
    {
      displayErrorDialog(errors);
    }
  }

  @Override
  protected boolean displayBackend(BackendDescriptor backend)
  {
    return !backend.isConfigBackend() && backend.getType() == BackendDescriptor.Type.PLUGGABLE;
  }

  private String getSelectedBaseDN()
  {
    CategorizedComboBoxElement o = (CategorizedComboBoxElement) baseDNs.getSelectedItem();
    return o != null ? (String) o.getValue() : null;
  }

  private AbstractIndexDescriptor getSelectedIndex()
  {
    CategorizedComboBoxElement o = (CategorizedComboBoxElement) keyEntryIDs.getSelectedItem();
    return o != null ? (AbstractIndexDescriptor) o.getValue() : null;
  }

  private void updateVerifyKeyEntriesComboBox()
  {
    String dn = getSelectedBaseDN();
    if (dn != null)
    {
      SortedSet<AbstractIndexDescriptor> indexes = hmIndexes.get(dn);
      if (indexes != null)
      {
        List<CategorizedComboBoxElement> newElements = new ArrayList<>();
        List<AbstractIndexDescriptor> databaseIndexes = new ArrayList<>();
        List<AbstractIndexDescriptor> attributeIndexes = new ArrayList<>();
        List<AbstractIndexDescriptor> vlvIndexes = new ArrayList<>();
        for (AbstractIndexDescriptor index : indexes)
        {
          if (index instanceof IndexDescriptor)
          {
            IndexDescriptor standardIndex = (IndexDescriptor) index;
            if (standardIndex.isDatabaseIndex())
            {
              databaseIndexes.add(standardIndex);
            }
            else
            {
              attributeIndexes.add(standardIndex);
            }
          }
          else
          {
            vlvIndexes.add(index);
          }
        }
        addNewElements(databaseIndexes, INFO_CTRL_PANEL_DATABASE_INDEXES.get().toString(), newElements);
        addNewElements(attributeIndexes, INFO_CTRL_PANEL_ATTRIBUTE_INDEXES.get().toString(), newElements);
        addNewElements(vlvIndexes, INFO_CTRL_PANEL_VLV_INDEXES.get().toString(), newElements);
        updateComboBoxModel(newElements, (DefaultComboBoxModel) keyEntryIDs.getModel());
      }
    }
  }

  private void addNewElements(final List<AbstractIndexDescriptor> indexes, final String label,
      final List<CategorizedComboBoxElement> elements)
  {
    if (!indexes.isEmpty())
    {
      elements.add(new CategorizedComboBoxElement(label, CategorizedComboBoxElement.Type.CATEGORY));
      for (AbstractIndexDescriptor index : indexes)
      {
        elements.add(new CategorizedComboBoxElement(index, CategorizedComboBoxElement.Type.REGULAR));
      }
    }
  }

  /** The task in charge of verifying the index. */
  private class VerifyIndexTask extends IndexTask
  {
    private String baseDN;

    /**
     * The constructor of the task.
     *
     * @param info
     *          the control panel info.
     * @param dlg
     *          the progress dialog that shows the progress of the task.
     */
    private VerifyIndexTask(ControlPanelInfo info, ProgressDialog dlg)
    {
      super(info, dlg, getSelectedBaseDN());
      this.baseDN = getSelectedBaseDN();
    }

    @Override
    public Type getType()
    {
      return Type.VERIFY_INDEXES;
    }

    @Override
    public LocalizableMessage getTaskDescription()
    {
      return INFO_CTRL_PANEL_VERIFY_INDEX_TASK_DESCRIPTION.get(baseDN);
    }

    @Override
    public boolean canLaunch(Task taskToBeLaunched, Collection<LocalizableMessage> incompatibilityReasons)
    {
      boolean canLaunch = true;
      if (state == State.RUNNING && runningOnSameServer(taskToBeLaunched))
      {
        // All the operations are incompatible if they apply to this backend.
        Set<String> backends = new TreeSet<>(taskToBeLaunched.getBackends());
        backends.retainAll(getBackends());
        Task.Type type = taskToBeLaunched.getType();
        if (type != Task.Type.BACKUP
            && type != Task.Type.EXPORT_LDIF
            && type != Task.Type.ENABLE_WINDOWS_SERVICE
            && type != Task.Type.DISABLE_WINDOWS_SERVICE
            && !backends.isEmpty())
        {
          incompatibilityReasons.add(getIncompatibilityMessage(this, taskToBeLaunched));
          canLaunch = false;
        }
      }
      return canLaunch;
    }

    @Override
    public void runTask()
    {
      state = State.RUNNING;
      lastException = null;
      try
      {
        List<String> arguments = getCommandLineArguments();
        String[] args = arguments.toArray(new String[arguments.size()]);

        returnCode = executeCommandLine(getCommandLinePath(), args);
        state = returnCode == 0 ? State.FINISHED_SUCCESSFULLY : State.FINISHED_WITH_ERROR;
      }
      catch (Throwable t)
      {
        lastException = t;
        state = State.FINISHED_WITH_ERROR;
      }
    }

    @Override
    protected List<String> getCommandLineArguments()
    {
      List<String> args = new ArrayList<>();

      args.add("--baseDN");
      args.add(getSelectedBaseDN());

      if (verifyIndexContents.isSelected())
      {
        SortableListModel<AbstractIndexDescriptor> model = addRemove.getSelectedListModel();
        for (AbstractIndexDescriptor index : model.getData())
        {
          args.add("--index");
          args.add(getName(index));
        }
      }
      else
      {
        args.add("--index");
        getName(getSelectedIndex());
        args.add("--clean");
      }

      args.add("--countErrors");

      return args;
    }

    private String getName(AbstractIndexDescriptor index)
    {
      if (index instanceof VLVIndexDescriptor)
      {
        return Utilities.getVLVNameInCommandLine((VLVIndexDescriptor) index);
      }
      return index.getName();
    }

    @Override
    protected String getCommandLinePath()
    {
      return getCommandLinePath("verify-index");
    }
  }
}
