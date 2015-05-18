/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
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
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.event.IndexModifiedEvent;
import org.opends.guitools.controlpanel.event.IndexModifiedListener;
import org.opends.guitools.controlpanel.task.RebuildIndexTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.components.AddRemovePanel;
import org.opends.guitools.controlpanel.ui.renderer.CustomListCellRenderer;
import org.opends.guitools.controlpanel.ui.renderer.IndexCellRenderer;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.guitools.controlpanel.util.ViewPositions;

/**
 * The panel that appears when the user wants to rebuild indexes.
 */
public class RebuildIndexPanel extends StatusGenericPanel implements IndexModifiedListener
{
  private static final long serialVersionUID = -4805445967165643375L;
  private JComboBox baseDNs;
  private AddRemovePanel<AbstractIndexDescriptor> addRemove;

  private JLabel lBaseDN;
  private JLabel lIndexes;
  private JLabel lNoBaseDNsFound;

  private final Map<String, SortedSet<AbstractIndexDescriptor>> hmIndexes = new HashMap<>();

  /** Constructor of the panel. */
  public RebuildIndexPanel()
  {
    createLayout();
  }

  /** {@inheritDoc} */
  @Override
  public void indexModified(final IndexModifiedEvent ev)
  {
    refreshContents(getInfo().getServerDescriptor());
  }

  /** {@inheritDoc} */
  @Override
  public void backendIndexesModified(final IndexModifiedEvent ev)
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
      @Override
      public void itemStateChanged(final ItemEvent ev)
      {
        comboBoxSelected(hmIndexes, (CategorizedComboBoxElement) baseDNs.getSelectedItem(), addRemove);
      }
    });
    listener.itemStateChanged(null);
    add(baseDNs, gbc);
    lNoBaseDNsFound = Utilities.createDefaultLabel(INFO_CTRL_PANEL_NO_BASE_DNS_FOUND_LABEL.get());
    add(lNoBaseDNsFound, gbc);
    lNoBaseDNsFound.setVisible(false);

    lIndexes = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_INDEXES_LABEL.get());
    gbc.insets.top = 10;
    gbc.insets.left = 0;
    gbc.gridy++;
    gbc.gridx = 0;
    gbc.gridwidth = 1;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    add(lIndexes, gbc);

    addRemove = new AddRemovePanel<>(AbstractIndexDescriptor.class);
    addRemove.getAvailableLabel().setText(INFO_CTRL_PANEL_AVAILABLE_INDEXES_LABEL.get().toString());
    addRemove.getSelectedLabel().setText(INFO_CTRL_PANEL_SELECTED_INDEXES_LABEL.get().toString());

    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.weighty = 0.0;
    gbc.gridwidth = 1;
    gbc.insets.top = 10;
    gbc.insets.left = 10;
    gbc.fill = GridBagConstraints.BOTH;
    add(addRemove, gbc);

    gbc.gridy++;
    gbc.insets.top = 3;
    JLabel explanation = Utilities.createInlineHelpLabel(INFO_CTRL_PANEL_REQUIRES_REBUILD_LEGEND.get());
    add(explanation, gbc);

    addBottomGlue(gbc);
  }

  /** {@inheritDoc} */
  @Override
  public void setInfo(final ControlPanelInfo info)
  {
    super.setInfo(info);
    ListCellRenderer indexCellRenderer = new IndexCellRenderer(addRemove.getAvailableList(), info);
    addRemove.getAvailableList().setCellRenderer(indexCellRenderer);
    addRemove.getSelectedList().setCellRenderer(indexCellRenderer);
    info.addIndexModifiedListener(this);
  }

  /** {@inheritDoc} */
  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_PANEL_REBUILD_INDEXES_TITLE.get();
  }

  /** {@inheritDoc} */
  @Override
  public Component getPreferredFocusComponent()
  {
    return baseDNs;
  }

  /** {@inheritDoc} */
  @Override
  public void configurationChanged(final ConfigurationChangeEvent ev)
  {
    refreshContents(ev.getNewDescriptor());
  }

  /**
   * Refresh the contents of the panel with the provided server descriptor.
   *
   * @param desc
   *          the server descriptor.
   */
  private void refreshContents(final ServerDescriptor desc)
  {
    super.updateIndexMap(desc, hmIndexes);
    filterIndexes(hmIndexes);

    updateBaseDNComboBoxModel((DefaultComboBoxModel) baseDNs.getModel(), desc);

    if (!allDisabled(desc.getBackends()))
    {
      updateErrorPaneAndOKButtonIfAuthRequired(desc,
              isLocal() ? INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_FOR_DISABLE_BACKEND.get()
                        : INFO_CTRL_PANEL_CANNOT_CONNECT_TO_REMOTE_DETAILS.get(desc.getHostname()));
    }
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        ViewPositions pos;
        JScrollPane scroll = Utilities.getContainingScroll(RebuildIndexPanel.this);
        if (scroll != null)
        {
          pos = Utilities.getViewPositions(scroll);
        }
        else
        {
          pos = Utilities.getViewPositions(RebuildIndexPanel.this);
        }

        boolean comboVisible = baseDNs.getModel().getSize() > 0;
        baseDNs.setVisible(comboVisible);
        lNoBaseDNsFound.setVisible(!comboVisible);
        addRemove.getAvailableList().repaint();
        addRemove.getSelectedList().repaint();

        Utilities.updateViewPositions(pos);
        if (!desc.isLocal())
        {
          displayErrorMessage(INFO_CTRL_PANEL_SERVER_REMOTE_SUMMARY.get(),
              INFO_CTRL_PANEL_SERVER_MUST_BE_LOCAL_REBUILD_INDEX_SUMMARY.get());
          setEnabledOK(false);
        }
        else
        {
          displayMainPanel();
          setEnabledOK(true);
        }
      }
    });
  }

  private boolean allDisabled(Set<BackendDescriptor> backends)
  {
    for (BackendDescriptor backend : backends)
    {
      if (displayBackend(backend) && backend.isEnabled())
      {
        return false;
      }
    }
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void cancelClicked()
  {
    setPrimaryValid(lBaseDN);
    setSecondaryValid(addRemove.getSelectedLabel());
    super.cancelClicked();
  }

  /** {@inheritDoc} */
  @Override
  public void okClicked()
  {
    setPrimaryValid(lBaseDN);
    setSecondaryValid(addRemove.getSelectedLabel());

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

    SortableListModel<AbstractIndexDescriptor> model = addRemove.getSelectedListModel();
    if (model.getSize() == 0)
    {
      setSecondaryInvalid(addRemove.getSelectedLabel());
      errors.add(ERR_CTRL_PANEL_MUST_SELECT_INDEX_TO_REBUILD.get());
    }

    if (errors.isEmpty())
    {
      ProgressDialog progressDialog =
          new ProgressDialog(Utilities.createFrame(), Utilities.getParentDialog(this), getTitle(), getInfo());
      Set<String> baseDNs = new HashSet<>();
      baseDNs.add(getSelectedBaseDN());
      RebuildIndexTask newTask =
          new RebuildIndexTask(getInfo(), progressDialog, baseDNs, addRemove.getSelectedListModel().getData());
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newTask, errors);
      }
      boolean confirmed = true;

      if (errors.isEmpty() && isServerRunning())
      {
        String backendName = newTask.getBackends().iterator().next();
        confirmed =
            displayConfirmationDialog(INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get(),
                INFO_CTRL_PANEL_CONFIRM_REBUILD_INDEX_DETAILS.get(backendName));
      }
      if (errors.isEmpty() && confirmed)
      {
        launchOperation(newTask, INFO_CTRL_PANEL_REBUILDING_INDEXES_SUMMARY.get(baseDN),
            INFO_CTRL_PANEL_REBUILDING_INDEXES_SUCCESSFUL_SUMMARY.get(),
            INFO_CTRL_PANEL_REBUILDING_INDEXES_SUCCESSFUL_DETAILS.get(),
            ERR_CTRL_PANEL_REBUILDING_INDEXES_ERROR_SUMMARY.get(), null,
            ERR_CTRL_PANEL_REBUILDING_INDEXES_ERROR_DETAILS, progressDialog);
        progressDialog.setVisible(true);
        Utilities.getParentDialog(this).setVisible(false);
      }
    }
    if (!errors.isEmpty())
    {
      displayErrorDialog(errors);
    }
  }

  /** {@inheritDoc} */
  @Override
  protected boolean displayBackend(final BackendDescriptor backend)
  {
    return !backend.isConfigBackend() && (backend.getType() == BackendDescriptor.Type.LOCAL_DB
                                           || backend.getType() == BackendDescriptor.Type.PLUGGABLE);
  }

  private String getSelectedBaseDN()
  {
    CategorizedComboBoxElement o = (CategorizedComboBoxElement) baseDNs.getSelectedItem();
    return o != null ? (String) o.getValue() : null;
  }

  private void filterIndexes(final Map<String, SortedSet<AbstractIndexDescriptor>> hmIndexes)
  {
    // Remove the indexes that are not to be added.
    for (SortedSet<AbstractIndexDescriptor> indexes : hmIndexes.values())
    {
      for (Iterator<AbstractIndexDescriptor> it = indexes.iterator(); it.hasNext();)
      {
        if (!mustBeDisplayed(it.next()))
        {
          it.remove();
        }
      }
    }
  }

  private boolean mustBeDisplayed(final AbstractIndexDescriptor index)
  {
    if (index instanceof IndexDescriptor)
    {
      for (String name : RebuildIndexTask.INDEXES_NOT_TO_SPECIFY)
      {
        if (name.equalsIgnoreCase(index.getName()))
        {
          return false;
        }
      }
    }
    return true;
  }
}
