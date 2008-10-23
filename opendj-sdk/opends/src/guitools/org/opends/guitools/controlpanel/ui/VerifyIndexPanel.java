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
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;

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
import org.opends.messages.Message;

/**
 * The panel that appears when the user wants to verify an index.
 *
 */
public class VerifyIndexPanel extends StatusGenericPanel
implements IndexModifiedListener
{
  private static final long serialVersionUID = 5252070109221657041L;
  private JComboBox baseDNs;
  private JRadioButton verifyIndexContents;
  private JRadioButton verifyKeyEntryIDs;
  private AddRemovePanel<AbstractIndexDescriptor> addRemove;
  private JComboBox keyEntryIDs;
  private HashMap<String, SortedSet<AbstractIndexDescriptor>> hmIndexes =
    new HashMap<String, SortedSet<AbstractIndexDescriptor>>();

  private JLabel lBaseDN;
  private JLabel lAction;
  private JLabel lIndex;
  private JLabel lNoBaseDNsFound;

  private final String DATABASE_INDEXES =
    INFO_CTRL_PANEL_DATABASE_INDEXES.get().toString();
  private final String ATTRIBUTE_INDEXES =
    INFO_CTRL_PANEL_ATTRIBUTE_INDEXES.get().toString();
  private final String VLV_INDEXES =
    INFO_CTRL_PANEL_VLV_INDEXES.get().toString();

  /**
   * Constructor of the panel.
   *
   */
  public VerifyIndexPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public void setInfo(ControlPanelInfo info)
  {
    super.setInfo(info);
    ListCellRenderer indexCellRenderer = new IndexCellRenderer(
        addRemove.getAvailableList(), info);
    addRemove.getAvailableList().setCellRenderer(indexCellRenderer);
    addRemove.getSelectedList().setCellRenderer(indexCellRenderer);
    info.addIndexModifiedListener(this);
  }

  /**
   * {@inheritDoc}
   */
  public void indexModified(IndexModifiedEvent ev)
  {
    refreshContents(getInfo().getServerDescriptor());
  }

  /**
   * {@inheritDoc}
   */
  public void backendIndexesModified(IndexModifiedEvent ev)
  {
    refreshContents(getInfo().getServerDescriptor());
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
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
      public void itemStateChanged(ItemEvent ev)
      {
        comboBoxSelected(hmIndexes,
            (CategorizedComboBoxElement)baseDNs.getSelectedItem(),
            addRemove);
        updateVerifyKeyEntriesComboBox();
      }
    });
    listener.itemStateChanged(null);
    gbc.gridwidth = 2;
    add(baseDNs, gbc);
    lNoBaseDNsFound = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_NO_BASE_DNS_FOUND_LABEL.get());
    add(lNoBaseDNsFound, gbc);
    lNoBaseDNsFound.setVisible(false);


    lAction = Utilities.createPrimaryLabel(
        INFO_CTRL_PANEL_ACTION_LABEL.get());
    gbc.insets.top = 10;
    gbc.gridy ++;
    gbc.gridx = 0;
    gbc.gridwidth = 1;
    add(lAction, gbc);

    verifyIndexContents = Utilities.createRadioButton(
        INFO_CTRL_PANEL_VERIFY_ENTRY_CONTEXT_ARE_INDEXES.get());
    verifyIndexContents.setSelected(true);
    gbc.insets.left = 10;
    gbc.gridx = 1;
    gbc.gridwidth = 2;
    add(verifyIndexContents, gbc);

    addRemove = new AddRemovePanel<AbstractIndexDescriptor>(
        AbstractIndexDescriptor.class);
    addRemove.getAvailableLabel().setText(
        INFO_CTRL_PANEL_AVAILABLE_INDEXES_LABEL.get().toString());
    addRemove.getSelectedLabel().setText(
        INFO_CTRL_PANEL_SELECTED_INDEXES_LABEL.get().toString());

    gbc.gridy ++;
    gbc.weightx = 1.0;
    gbc.weighty = 0.0;
    gbc.gridwidth = 2;
    gbc.insets.top = 5;
    gbc.insets.left = 30;
    gbc.fill = GridBagConstraints.BOTH;
    add(addRemove, gbc);

    gbc.gridy ++;
    JLabel explanation = Utilities.createInlineHelpLabel(
        INFO_CTRL_PANEL_REQUIRES_REBUILD_LEGEND.get());
    gbc.insets.top = 3;
    add(explanation, gbc);

    verifyKeyEntryIDs = Utilities.createRadioButton(
        INFO_CTRL_PANEL_VERIFY_ALL_KEYS.get());
    verifyKeyEntryIDs.setSelected(true);
    gbc.insets.left = 10;
    gbc.insets.top = 10;
    gbc.gridx = 1;
    gbc.gridwidth = 2;
    gbc.gridy ++;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.NONE;
    add(verifyKeyEntryIDs, gbc);

    gbc.gridy ++;
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
      /**
       * {@inheritDoc}
       */
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

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_VERIFY_INDEXES_PANEL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return baseDNs;
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    refreshContents(ev.getNewDescriptor());
  }

  private void refreshContents(ServerDescriptor desc)
  {
    updateIndexMap(desc, hmIndexes);
    updateBaseDNComboBoxModel((DefaultComboBoxModel)baseDNs.getModel(), desc);
    SwingUtilities.invokeLater(new Runnable()
    {
      /**
       * {@inheritDoc}
       */
      public void run()
      {
        comboBoxSelected(hmIndexes,
            (CategorizedComboBoxElement)baseDNs.getSelectedItem(),
            addRemove);
        updateVerifyKeyEntriesComboBox();
        addRemove.getAvailableList().repaint();
        addRemove.getSelectedList().repaint();
        boolean comboVisible = baseDNs.getModel().getSize() > 0;
        baseDNs.setVisible(comboVisible);
        lNoBaseDNsFound.setVisible(!comboVisible);
      }
    });
  }

  /**
   * {@inheritDoc}
   */
  public void cancelClicked()
  {
    setPrimaryValid(lBaseDN);
    setSecondaryValid(addRemove.getSelectedLabel());
    setSecondaryValid(lIndex);
    super.cancelClicked();
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    setPrimaryValid(lBaseDN);
    setSecondaryValid(addRemove.getSelectedLabel());
    setSecondaryValid(lIndex);

    final LinkedHashSet<Message> errors = new LinkedHashSet<Message>();

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
      SortableListModel<AbstractIndexDescriptor> model =
        addRemove.getSelectedListModel();
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
        if ((keyEntryIDs.getItemCount() == 0) && (baseDN != null))
        {
          errors.add(ERR_CTRL_PANEL_NO_INDEXES_FOR_BASEDN.get(baseDN));
        }
        else
        {
          errors.add(ERR_CTRL_PANEL_INDEX_MUST_BE_SELECTED.get());
        }
      }
    }

    if (errors.isEmpty())
    {
      ProgressDialog progressDialog = new ProgressDialog(
          Utilities.getParentDialog(this), getTitle(), getInfo());
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
            ERR_CTRL_PANEL_VERIFYING_INDEXES_ERROR_SUMMARY.get(),
            null,
            ERR_CTRL_PANEL_VERIFYING_INDEXES_ERROR_DETAILS,
            progressDialog);
        progressDialog.setVisible(true);
        Utilities.getParentDialog(this).setVisible(false);
      }
    }
    if (errors.size() > 0)
    {
      displayErrorDialog(errors);
    }
  }

  /**
   * {@inheritDoc}
   */
  protected boolean displayBackend(BackendDescriptor backend)
  {
    return !backend.isConfigBackend() &&
    (backend.getType() == BackendDescriptor.Type.LOCAL_DB);
  }

  private String getSelectedBaseDN()
  {
    String dn = null;
    CategorizedComboBoxElement o =
      (CategorizedComboBoxElement)baseDNs.getSelectedItem();
    if (o != null)
    {
      dn = (String)o.getValue();
    }
    return dn;
  }

  private AbstractIndexDescriptor getSelectedIndex()
  {
    AbstractIndexDescriptor index = null;
    CategorizedComboBoxElement o =
      (CategorizedComboBoxElement)keyEntryIDs.getSelectedItem();
    if (o != null)
    {
      index = (AbstractIndexDescriptor)o.getValue();
    }
    return index;
  }

  private void updateVerifyKeyEntriesComboBox()
  {
    String dn = getSelectedBaseDN();
    if (dn != null)
    {
      SortedSet<AbstractIndexDescriptor> indexes = hmIndexes.get(dn);
      if (indexes != null)
      {
        ArrayList<CategorizedComboBoxElement> newElements =
          new ArrayList<CategorizedComboBoxElement>();
        ArrayList<AbstractIndexDescriptor> databaseIndexes =
          new ArrayList<AbstractIndexDescriptor>();
        ArrayList<AbstractIndexDescriptor> attributeIndexes =
          new ArrayList<AbstractIndexDescriptor>();
        ArrayList<AbstractIndexDescriptor> vlvIndexes =
          new ArrayList<AbstractIndexDescriptor>();
        for (AbstractIndexDescriptor index : indexes)
        {
          if (index instanceof IndexDescriptor)
          {
            IndexDescriptor standardIndex = (IndexDescriptor)index;
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
            // VLV index
            vlvIndexes.add(index);
          }
        }
        if (databaseIndexes.size() > 0)
        {
          newElements.add(new CategorizedComboBoxElement(DATABASE_INDEXES,
              CategorizedComboBoxElement.Type.CATEGORY));
          for (AbstractIndexDescriptor index : databaseIndexes)
          {
            newElements.add(new CategorizedComboBoxElement(index,
                CategorizedComboBoxElement.Type.REGULAR));
          }
        }
        if (attributeIndexes.size() > 0)
        {
          newElements.add(new CategorizedComboBoxElement(ATTRIBUTE_INDEXES,
              CategorizedComboBoxElement.Type.CATEGORY));
          for (AbstractIndexDescriptor index : attributeIndexes)
          {
            newElements.add(new CategorizedComboBoxElement(index,
                CategorizedComboBoxElement.Type.REGULAR));
          }
        }
        if (vlvIndexes.size() > 0)
        {
          newElements.add(new CategorizedComboBoxElement(VLV_INDEXES,
              CategorizedComboBoxElement.Type.CATEGORY));
          for (AbstractIndexDescriptor index : vlvIndexes)
          {
            newElements.add(new CategorizedComboBoxElement(index,
                CategorizedComboBoxElement.Type.REGULAR));
          }
        }
        updateComboBoxModel(newElements,
            (DefaultComboBoxModel)keyEntryIDs.getModel());
      }
    }
  }

  /**
   * The task in charge of verifying the index.
   *
   */
  protected class VerifyIndexTask extends IndexTask
  {
    private String baseDN;

    /**
     * The constructor of the task.
     * @param info the control panel info.
     * @param dlg the progress dialog that shows the progress of the task.
     */
    public VerifyIndexTask(ControlPanelInfo info, ProgressDialog dlg)
    {
      super(info, dlg, getSelectedBaseDN());
      this.baseDN = getSelectedBaseDN();
    }

    /**
     * {@inheritDoc}
     */
    public Type getType()
    {
      return Type.VERIFY_INDEXES;
    }

    /**
     * {@inheritDoc}
     */
    public Message getTaskDescription()
    {
      return INFO_CTRL_PANEL_VERIFY_INDEX_TASK_DESCRIPTION.get(baseDN);
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
        // backend.
        Set<String> backends =
          new TreeSet<String>(taskToBeLaunched.getBackends());
        backends.retainAll(getBackends());
        Task.Type type = taskToBeLaunched.getType();
        if ((type != Task.Type.BACKUP) &&
            (type != Task.Type.EXPORT_LDIF) &&
            (type != Task.Type.ENABLE_WINDOWS_SERVICE) &&
            (type != Task.Type.DISABLE_WINDOWS_SERVICE) &&
            (backends.size() > 0))
        {
          incompatibilityReasons.add(getIncompatibilityMessage(this,
              taskToBeLaunched));
          canLaunch = false;
        }
      }
      return canLaunch;
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
        ArrayList<String> arguments = getCommandLineArguments();

        String[] args = new String[arguments.size()];

        arguments.toArray(args);
        returnCode = executeCommandLine(getCommandLinePath(), args);

        if (returnCode != 0)
        {
          state = State.FINISHED_WITH_ERROR;
        }
        else
        {
          state = State.FINISHED_SUCCESSFULLY;
        }
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
    protected ArrayList<String> getCommandLineArguments()
    {
      ArrayList<String> args = new ArrayList<String>();

      args.add("--baseDN");
      args.add(getSelectedBaseDN());

      if (verifyIndexContents.isSelected())
      {
        SortableListModel<AbstractIndexDescriptor> model =
          addRemove.getSelectedListModel();
        for (AbstractIndexDescriptor index : model.getData())
        {
          args.add("--index");
          if (index instanceof VLVIndexDescriptor)
          {
            args.add(
                Utilities.getVLVNameInCommandLine((VLVIndexDescriptor)index));
          }
          else
          {
            args.add(index.getName());
          }
        }
      }
      else
      {
        args.add("--index");
        AbstractIndexDescriptor index = getSelectedIndex();
        if (index instanceof VLVIndexDescriptor)
        {
          args.add(
              Utilities.getVLVNameInCommandLine((VLVIndexDescriptor)index));
        }
        else
        {
          args.add(index.getName());
        }
        args.add("--clean");
      }

      args.add("--countErrors");

      return args;
    }

    /**
     * {@inheritDoc}
     */
    protected String getCommandLinePath()
    {
      return getCommandLinePath("verify-index");
    }
  };
}
