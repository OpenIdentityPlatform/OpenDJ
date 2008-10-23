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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.SortedSet;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;

import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.CategorizedComboBoxElement;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.datamodel.SortableListModel;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.event.IndexModifiedEvent;
import org.opends.guitools.controlpanel.event.IndexModifiedListener;
import org.opends.guitools.controlpanel.task.RebuildIndexTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.components.AddRemovePanel;
import org.opends.guitools.controlpanel.ui.renderer.CustomListCellRenderer;
import org.opends.guitools.controlpanel.ui.renderer.IndexCellRenderer;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;

/**
 * The panel that appears when the user wants to rebuild indexes.
 *
 */
public class RebuildIndexPanel extends StatusGenericPanel
implements IndexModifiedListener
{
  private static final long serialVersionUID = -4805445967165643375L;
  private JComboBox baseDNs;
  private AddRemovePanel<AbstractIndexDescriptor> addRemove;

  private JLabel lBaseDN;
  private JLabel lIndexes;
  private JLabel lNoBaseDNsFound;

  private HashMap<String, SortedSet<AbstractIndexDescriptor>> hmIndexes =
    new HashMap<String, SortedSet<AbstractIndexDescriptor>>();

  /**
   * Constructor of the panel.
   *
   */
  public RebuildIndexPanel()
  {
    super();
    createLayout();
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
    gbc.gridwidth = 3;
    addErrorPane(gbc);
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
      /**
       * {@inheritDoc}
       */
      public void itemStateChanged(ItemEvent ev)
      {
        comboBoxSelected(hmIndexes,
            (CategorizedComboBoxElement)baseDNs.getSelectedItem(),
            addRemove);
      }
    });
    listener.itemStateChanged(null);
    add(baseDNs, gbc);
    lNoBaseDNsFound = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_NO_BASE_DNS_FOUND_LABEL.get());
    add(lNoBaseDNsFound, gbc);
    lNoBaseDNsFound.setVisible(false);

    lIndexes =
      Utilities.createPrimaryLabel(INFO_CTRL_PANEL_INDEXES_LABEL.get());
    gbc.insets.top = 10;
    gbc.insets.left = 0;
    gbc.gridy ++;
    gbc.gridx = 0;
    gbc.gridwidth = 1;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    add(lIndexes, gbc);

    addRemove = new AddRemovePanel<AbstractIndexDescriptor>(
        AbstractIndexDescriptor.class);
    addRemove.getAvailableLabel().setText(
        INFO_CTRL_PANEL_AVAILABLE_INDEXES_LABEL.get().toString());
    addRemove.getSelectedLabel().setText(
        INFO_CTRL_PANEL_SELECTED_INDEXES_LABEL.get().toString());

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.weighty = 0.0;
    gbc.gridwidth = 1;
    gbc.insets.top = 10;
    gbc.insets.left = 10;
    gbc.fill = GridBagConstraints.BOTH;
    add(addRemove, gbc);

    gbc.gridy ++;
    gbc.insets.top = 3;
    JLabel explanation = Utilities.createInlineHelpLabel(
        INFO_CTRL_PANEL_REQUIRES_REBUILD_LEGEND.get());
    add(explanation, gbc);

    addBottomGlue(gbc);
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
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_REBUILD_INDEXES_TITLE.get();
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
    ServerDescriptor desc = ev.getNewDescriptor();
    refreshContents(desc);
  }

  /**
   * Refresh the contents of the panel with the provided server descriptor.
   * @param desc the server descriptor.
   */
  private void refreshContents(ServerDescriptor desc)
  {
    updateIndexMap(desc, hmIndexes);
    updateBaseDNComboBoxModel((DefaultComboBoxModel)baseDNs.getModel(), desc);

    // Check that all backends
    boolean allDisabled = false;
    for (BackendDescriptor backend : desc.getBackends())
    {
      if (displayBackend(backend))
      {
        if (backend.isEnabled())
        {
          allDisabled = false;
          break;
        }
      }
    }
    if (!allDisabled)
    {
      updateErrorPaneAndOKButtonIfAuthRequired(desc,
          INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_DISABLE_BACKEND.get());
    }
    SwingUtilities.invokeLater(new Runnable()
    {
      /**
       * {@inheritDoc}
       */
      public void run()
      {
        boolean comboVisible = baseDNs.getModel().getSize() > 0;
        baseDNs.setVisible(comboVisible);
        lNoBaseDNsFound.setVisible(!comboVisible);
        addRemove.getAvailableList().repaint();
        addRemove.getSelectedList().repaint();
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
    super.cancelClicked();
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    setPrimaryValid(lBaseDN);
    setSecondaryValid(addRemove.getSelectedLabel());

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

    SortableListModel<AbstractIndexDescriptor> model =
      addRemove.getSelectedListModel();
    if (model.getSize() == 0)
    {
      setSecondaryInvalid(addRemove.getSelectedLabel());
      errors.add(ERR_CTRL_PANEL_MUST_SELECT_INDEX_TO_REBUILD.get());
    }

    if (errors.isEmpty())
    {
      ProgressDialog progressDialog = new ProgressDialog(
          Utilities.getParentDialog(this), getTitle(), getInfo());
      HashSet<String> baseDNs = new HashSet<String>();
      baseDNs.add(getSelectedBaseDN());
      RebuildIndexTask newTask = new RebuildIndexTask(getInfo(), progressDialog,
          baseDNs, addRemove.getSelectedListModel().getData());
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newTask, errors);
      }
      boolean confirmed = true;

      if ((errors.isEmpty()) && isServerRunning())
      {
        String backendName = newTask.getBackends().iterator().next();
        confirmed = displayConfirmationDialog(
            INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
            INFO_CTRL_PANEL_CONFIRM_REBUILD_INDEX_DETAILS.get(backendName));
      }
      if ((errors.isEmpty()) && confirmed)
      {
        launchOperation(newTask,
            INFO_CTRL_PANEL_REBUILDING_INDEXES_SUMMARY.get(baseDN),
            INFO_CTRL_PANEL_REBUILDING_INDEXES_SUCCESSFUL_SUMMARY.get(),
            INFO_CTRL_PANEL_REBUILDING_INDEXES_SUCCESSFUL_DETAILS.get(),
            ERR_CTRL_PANEL_REBUILDING_INDEXES_ERROR_SUMMARY.get(),
            null,
            ERR_CTRL_PANEL_REBUILDING_INDEXES_ERROR_DETAILS,
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
}
