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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;

import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.
 DBEnvironmentMonitoringTableModel;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.util.ServerConstants;

/**
 * The panel displaying the database environment monitor panel.
 */
public class DBEnvironmentMonitoringPanel extends GeneralMonitoringPanel
{
  private static final long serialVersionUID = 9031734563723229830L;

  private JTable table;
  private DBEnvironmentMonitoringTableModel tableModel;
  private JScrollPane scroll;
  private JLabel noDBsFound;
  private JLabel noMonitoringFound;
  private JButton showOperations;

  private LinkedHashSet<String> attributes = new LinkedHashSet<String>();
  private LinkedHashSet<String> allAttributes = new LinkedHashSet<String>();

  private MonitoringAttributesViewPanel<String> operationViewPanel;
  private GenericDialog operationViewDlg;

  /**
   * Default constructor.
   */
  public DBEnvironmentMonitoringPanel()
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
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    JLabel lTitle = Utilities.createTitleLabel(
        INFO_CTRL_PANEL_DB_ENVIRONMENT.get());
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridwidth = 2;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.insets.top = 5;
    gbc.insets.bottom = 7;
    add(lTitle, gbc);

    gbc.insets.bottom = 0;
    gbc.insets.top = 10;
    gbc.gridy ++;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridwidth = 1;
    showOperations =
      Utilities.createButton(INFO_CTRL_PANEL_OPERATIONS_VIEW.get());
    showOperations.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        operationViewClicked();
      }
    });
    showOperations.setVisible(false);
    gbc.gridx = 0;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(Box.createHorizontalGlue(), gbc);
    gbc.gridx ++;
    gbc.weightx = 0.0;
    add(showOperations, gbc);

    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.gridwidth = 2;
    tableModel = new DBEnvironmentMonitoringTableModel();
    tableModel.setAttributes(attributes);
    table = Utilities.createSortableTable(tableModel,
        new DefaultTableCellRenderer());
    scroll = Utilities.createScrollPane(table);
    updateTableSize();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    add(scroll, gbc);
    noDBsFound = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_NO_DBS_FOUND.get());
    noDBsFound.setHorizontalAlignment(SwingConstants.CENTER);
    add(noDBsFound, gbc);
    noMonitoringFound = Utilities.createDefaultLabel(
        INFO_CTRL_PANEL_NO_DB_MONITORING_FOUND.get());
    noMonitoringFound.setHorizontalAlignment(SwingConstants.CENTER);
    add(noMonitoringFound, gbc);

    setBorder(PANEL_BORDER);
  }

  /**
   * Updates the contents of the panel.  The code assumes that this is being
   * called from the event thread.
   *
   */
  public void updateContents()
  {
    boolean backendsFound = false;
    ServerDescriptor server = null;
    if (getInfo() != null)
    {
      server = getInfo().getServerDescriptor();
    }
    Set<BackendDescriptor> dbBackends = new HashSet<BackendDescriptor>();
    boolean updateAttributes = allAttributes.isEmpty();
    SortedSet<String> sortedAttrNames = new TreeSet<String>();
    if (server != null)
    {
      for (BackendDescriptor backend : server.getBackends())
      {
        if (backend.getType() == BackendDescriptor.Type.LOCAL_DB)
        {
          dbBackends.add(backend);
          if (updateAttributes)
          {
            sortedAttrNames.addAll(getMonitoringAttributes(backend));
          }
        }
      }
      backendsFound = !dbBackends.isEmpty();
    }
    if (updateAttributes)
    {
      allAttributes.addAll(sortedAttrNames);
      for (String attrName : allAttributes)
      {
        attributes.add(attrName);
        if (attributes.size() == 5)
        {
          break;
        }
      }
      if (attributes.size() > 0)
      {
        setOperationsToDisplay(attributes);
        updateTableSize();
      }
    }
    tableModel.setData(dbBackends);
    showOperations.setVisible(backendsFound);
    scroll.setVisible(backendsFound && !allAttributes.isEmpty());
    noDBsFound.setVisible(!backendsFound);
    noMonitoringFound.setVisible(backendsFound && allAttributes.isEmpty());
    showOperations.setVisible(allAttributes.size() > 0);
  }


  private void updateTableSize()
  {
    Utilities.updateTableSizes(table, 8);
    Utilities.updateScrollMode(scroll, table);
  }

  /**
   * Displays a dialog allowing the user to select which operations to display.
   *
   */
  private void operationViewClicked()
  {
    if (operationViewDlg == null)
    {
      operationViewPanel = MonitoringAttributesViewPanel.createStringInstance(
          allAttributes);
      operationViewDlg = new GenericDialog(Utilities.getFrame(this),
          operationViewPanel);
      operationViewDlg.setModal(true);
      Utilities.centerGoldenMean(operationViewDlg,
          Utilities.getParentDialog(this));
    }
    operationViewPanel.setSelectedAttributes(attributes);
    operationViewDlg.setVisible(true);
    if (!operationViewPanel.isCancelled())
    {
      attributes = operationViewPanel.getAttributes();
      setOperationsToDisplay(attributes);
      updateTableSize();
    }
  }

  private void setOperationsToDisplay(
      LinkedHashSet<String> attributes)
  {
    this.attributes = attributes;
    tableModel.setAttributes(attributes);
    tableModel.forceDataStructureChange();
  }

  private Set<String> getMonitoringAttributes(BackendDescriptor backend)
  {
    Set<String> attrNames = new HashSet<String>();
    if (backend.getMonitoringEntry() != null)
    {
      Set<String> allNames = backend.getMonitoringEntry().getAttributeNames();
      for (String attrName : allNames)
      {
        if (!attrName.equalsIgnoreCase(
            ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME) &&
            !attrName.equalsIgnoreCase(ServerConstants.ATTR_COMMON_NAME))
        {
          attrNames.add(attrName);
        }
      }
    }
    return attrNames;
  }
}
