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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import org.opends.guitools.controlpanel.datamodel.AbstractIndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.AbstractIndexTableModel;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.event.IndexSelectionEvent;
import org.opends.guitools.controlpanel.event.IndexSelectionListener;
import org.opends.guitools.controlpanel.ui.renderer.SelectableTableCellRenderer;
import org.opends.guitools.controlpanel.util.Utilities;

/**
 * The abstract class used to refactor some code.  The classes that extend this
 * class are the two panels that appear on the right side of the
 * 'Manage Indexes...' dialog when the user clicks on 'Indexes' or
 * 'VLV Indexes'.
 */
abstract class AbstractBackendIndexesPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 2702054131388877743L;
  private String backendName;
  /** The table model. */
  protected AbstractIndexTableModel tableModel;
  /** The table contained by this panel. */
  private JTable table;
  /** The scroll pane that contains the table. */
  private JScrollPane tableScroll;
  private final Set<IndexSelectionListener> indexListeners = new HashSet<>();
  private int lastRowMouseOver = -1;

  /** Default constructor. */
  protected AbstractBackendIndexesPanel()
  {
    createLayout();
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return table;
  }

  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    update(backendName);
  }

  /**
   * The contents of the panel are updated with the indexes of the provided
   * backend.
   * @param backendName the backend name.
   */
  void update(String backendName)
  {
    this.backendName = backendName;

    BackendDescriptor backend = getBackend(backendName);
    if (backend != null)
    {
      final BackendDescriptor b = backend;
      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          updateTableModel(b);
          Utilities.updateTableSizes(table);
          Utilities.updateScrollMode(tableScroll, table);
        }
      });
    }
    else
    {
      updateErrorPane(errorPane,
          ERR_CTRL_PANEL_BACKEND_NOT_FOUND_SUMMARY.get(),
          ColorAndFontConstants.errorTitleFont,
          ERR_CTRL_PANEL_BACKEND_NOT_FOUND_DETAILS.get(backendName),
          ColorAndFontConstants.defaultFont);
    }
  }

  private BackendDescriptor getBackend(String backendID)
  {
    for (BackendDescriptor b : getInfo().getServerDescriptor().getBackends())
    {
      if (b.getBackendID().equals(backendID))
      {
        return b;
      }
    }
    return null;
  }

  /**
   * The method that is called to update the table model with the contents of
   * the specified backend.
   * @param backend the backend.
   */
  protected abstract void updateTableModel(BackendDescriptor backend);

  @Override
  public void okClicked()
  {
    // No OK button
  }

  @Override
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.NO_BUTTON;
  }

  /**
   * Adds an index selection listener.
   * @param listener the index selection listener.
   */
  public void addIndexSelectionListener(IndexSelectionListener listener)
  {
    indexListeners.add(listener);
  }

  /**
   * Removes an index selection listener.
   * @param listener the index selection listener.
   */
  public void removeIndexSelectionListener(IndexSelectionListener listener)
  {
    indexListeners.remove(listener);
  }

  /**
   * Returns the index table model used by this panel.
   * @return the index table model used by this panel.
   */
  protected abstract AbstractIndexTableModel getIndexTableModel();

  /** Creates the layout of the panel (but the contents are not populated here). */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 1;
    addErrorPane(gbc);
    gbc.gridy ++;
    tableModel = getIndexTableModel();
    SelectableTableCellRenderer renderer = new SelectableTableCellRenderer();
    table = Utilities.createSortableTable(tableModel, renderer);
    renderer.setTable(table);
    table.getSelectionModel().setSelectionMode(
        ListSelectionModel.SINGLE_SELECTION);
    table.setDragEnabled(false);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    table.addMouseListener(new MouseAdapter()
    {
      @Override
      public void mouseReleased(MouseEvent ev)
      {
        int selectedRow = table.getSelectedRow();
        if (selectedRow != -1 && lastRowMouseOver == selectedRow)
        {
          AbstractIndexDescriptor index = tableModel.getIndexAt(selectedRow);
          final IndexSelectionEvent ise = new IndexSelectionEvent(table, index);
          SwingUtilities.invokeLater(new Runnable()
          {
            /** Call it this way to let the painting events happen. */
            @Override
            public void run()
            {
              for (IndexSelectionListener listener : indexListeners)
              {
                listener.indexSelected(ise);
              }
            }
          });
        }
      }
    });
    table.addMouseMotionListener(new MouseMotionAdapter()
    {
      @Override
      public void mouseMoved(MouseEvent ev)
      {
        lastRowMouseOver = table.rowAtPoint(ev.getPoint());

      }

      @Override
      public void mouseDragged(MouseEvent ev)
      {
        lastRowMouseOver = -1;
      }
    });

    tableScroll = Utilities.createBorderLessScrollBar(table);
    tableScroll.getViewport().setOpaque(false);
    tableScroll.setOpaque(false);
    tableScroll.getViewport().setBackground(ColorAndFontConstants.background);
    tableScroll.setBackground(ColorAndFontConstants.background);
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    add(tableScroll, gbc);
  }
}
