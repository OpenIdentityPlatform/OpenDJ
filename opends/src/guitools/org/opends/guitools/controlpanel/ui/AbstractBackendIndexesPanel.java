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
 *
 */
public abstract class AbstractBackendIndexesPanel extends StatusGenericPanel
{
  private String backendName;
  /**
   * The table model.
   */
  protected AbstractIndexTableModel tableModel;
  /**
   * The table contained by this panel.
   */
  protected JTable table;
  /**
   * The scroll pane that contains the table.
   */
  protected JScrollPane tableScroll;
  private Set<IndexSelectionListener> indexListeners =
    new HashSet<IndexSelectionListener>();
  private int lastRowMouseOver = -1;

  /**
   * Default constructor.
   *
   */
  protected AbstractBackendIndexesPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return table;
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    update(backendName);
  }

  /**
   * The contents of the panel are updated with the indexes of the provided
   * backend.
   * @param backendName the backend name.
   */
  public void update(String backendName)
  {
    this.backendName = backendName;

    BackendDescriptor backend = null;
    for (BackendDescriptor b : getInfo().getServerDescriptor().getBackends())
    {
      if (b.getBackendID().equals(backendName))
      {
        backend = b;
        break;
      }
    }

    if (backend != null)
    {
      final BackendDescriptor b = backend;
      SwingUtilities.invokeLater(new Runnable()
      {
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

  /**
   * The method that is called to update the table model with the contents of
   * the specified backend.
   * @param backend the backend.
   */
  protected abstract void updateTableModel(BackendDescriptor backend);

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    // No OK button
  }

  /**
   * {@inheritDoc}
   */
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

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   *
   */
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
      public void mouseReleased(MouseEvent ev)
      {
        int selectedRow = table.getSelectedRow();
        if ((selectedRow != -1) && (lastRowMouseOver == selectedRow))
        {
          AbstractIndexDescriptor index = tableModel.getIndexAt(selectedRow);
          final IndexSelectionEvent ise = new IndexSelectionEvent(table, index);
          SwingUtilities.invokeLater(new Runnable()
          {
            // Call it this way to let the painting events to happen.
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
      public void mouseMoved(MouseEvent ev)
      {
        lastRowMouseOver = table.rowAtPoint(ev.getPoint());

      }

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
