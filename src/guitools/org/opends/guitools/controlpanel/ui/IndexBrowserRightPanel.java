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

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;

import javax.swing.JPanel;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.event.IndexSelectionListener;
import org.opends.messages.Message;

/**
 * The panel on the right of the 'Manage Indexes' panel.
 *
 */
public class IndexBrowserRightPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = -6904674789074101772L;
  private JPanel mainPanel;
  private IndexPanel standardIndexPanel = new IndexPanel();
  private VLVIndexPanel vlvIndexPanel = new VLVIndexPanel();
  private BackendIndexesPanel backendIndexesPanel = new BackendIndexesPanel();
  private BackendVLVIndexesPanel backendVLVIndexesPanel =
    new BackendVLVIndexesPanel();

  private final static String NOTHING_SELECTED = "Nothing Selected";
  private final static String MULTIPLE_SELECTED = "Multiple Selected";

  /**
   * Default constructor.
   *
   */
  public IndexBrowserRightPanel()
  {
    super();
    createLayout();
  }

  /**
   * Displays a panel informing that no item is selected.
   *
   */
  public void displayVoid()
  {
    ((CardLayout)mainPanel.getLayout()).show(mainPanel, NOTHING_SELECTED);
  }

  /**
   * Displays a panel informing that multiple items are selected.
   *
   */
  public void displayMultiple()
  {
    ((CardLayout)mainPanel.getLayout()).show(mainPanel, MULTIPLE_SELECTED);
  }

  /**
   * Adds an index selection listener.
   * @param listener the index selection listener.
   */
  public void addIndexSelectionListener(IndexSelectionListener listener)
  {
    backendIndexesPanel.addIndexSelectionListener(listener);
    backendVLVIndexesPanel.addIndexSelectionListener(listener);
  }

  /**
   * Removes an index selection listener.
   * @param listener the index selection listener.
   */
  public void removeIndexSelectionListener(IndexSelectionListener listener)
  {
    backendIndexesPanel.removeIndexSelectionListener(listener);
    backendVLVIndexesPanel.removeIndexSelectionListener(listener);
  }

  /**
   * {@inheritDoc}
   */
  public void setInfo(ControlPanelInfo info)
  {
    super.setInfo(info);
    standardIndexPanel.setInfo(info);
    vlvIndexPanel.setInfo(info);
    backendIndexesPanel.setInfo(info);
    backendVLVIndexesPanel.setInfo(info);
  }

  /**
   * Updates the contents of the panel with an standard index.
   * @param index the index to be used to update the contents of the panel.
   */
  public void updateStandardIndex(IndexDescriptor index)
  {
    standardIndexPanel.update(index);
    ((CardLayout)mainPanel.getLayout()).show(mainPanel,
        standardIndexPanel.getTitle().toString());
  }

  /**
   * Updates the contents of the panel with a VLV index.
   * @param index the index to be used to update the contents of the panel.
   */
  public void updateVLVIndex(VLVIndexDescriptor index)
  {
    vlvIndexPanel.update(index);
    ((CardLayout)mainPanel.getLayout()).show(mainPanel,
        vlvIndexPanel.getTitle().toString());
  }

  /**
   * Updates the contents of the panel with the indexes on the provided backend.
   * A table with all the indexes of the backend will be displayed.
   * @param backendName the name of the backend.
   */
  public void updateBackendIndexes(String backendName)
  {
    backendIndexesPanel.update(backendName);
    ((CardLayout)mainPanel.getLayout()).show(mainPanel,
        backendIndexesPanel.getTitle().toString());
  }

  /**
   * Updates the contents of the panel with the VLV indexes on the provided
   * backend.
   * A table with all the VLV indexes of the backend will be displayed.
   * @param backendName the name of the backend.
   */
  public void updateBackendVLVIndexes(String backendName)
  {
    backendVLVIndexesPanel.update(backendName);
    ((CardLayout)mainPanel.getLayout()).show(mainPanel,
        backendVLVIndexesPanel.getTitle().toString());
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    CardLayout cardLayout = new CardLayout();
    mainPanel = new JPanel(cardLayout);
    mainPanel.setOpaque(false);
    NoItemSelectedPanel noEntryPanel = new NoItemSelectedPanel();
    mainPanel.add(noEntryPanel, NOTHING_SELECTED);
    NoItemSelectedPanel multipleEntryPanel = new NoItemSelectedPanel();
    multipleEntryPanel.setMessage(
        INFO_CTRL_PANEL_MULTIPLE_ITEMS_SELECTED_LABEL.get());
    mainPanel.add(multipleEntryPanel, MULTIPLE_SELECTED);
    StatusGenericPanel[] panels =
    {
        standardIndexPanel,
        backendIndexesPanel,
        backendVLVIndexesPanel,
        vlvIndexPanel
    };
    for (StatusGenericPanel panel : panels)
    {
      mainPanel.add(panel, panel.getTitle().toString());
    }
    cardLayout.show(mainPanel, NOTHING_SELECTED);
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    add(mainPanel, gbc);
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    // No ok button
  }

  /**
   * {@inheritDoc}
   */
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.NO_BUTTON;
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_INDEX_BROWSER_RIGHT_PANEL_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    // TODO
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
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
    boolean mustCheckUnsavedChanges;
    if (vlvIndexPanel.isVisible())
    {
      mustCheckUnsavedChanges = vlvIndexPanel.mustCheckUnsavedChanges();
    }
    else if (standardIndexPanel.isVisible())
    {
      mustCheckUnsavedChanges = standardIndexPanel.mustCheckUnsavedChanges();
    }
    else
    {
      mustCheckUnsavedChanges = false;
    }
    return mustCheckUnsavedChanges;
  }

  /**
   * Tells whether the user chose to save the changes in the panel, to not save
   * them or simply cancelled the selection in the tree.
   * @return the value telling whether the user chose to save the changes in the
   * panel, to not save them or simply cancelled the selection in the tree.
   */
  public UnsavedChangesDialog.Result checkUnsavedChanges()
  {
    UnsavedChangesDialog.Result result;
    if (vlvIndexPanel.isVisible())
    {
      result = vlvIndexPanel.checkUnsavedChanges();
    }
    else if (standardIndexPanel.isVisible())
    {
      result = standardIndexPanel.checkUnsavedChanges();
    }
    else
    {
      result = UnsavedChangesDialog.Result.DO_NOT_SAVE;
    }
    return result;
  }
}
