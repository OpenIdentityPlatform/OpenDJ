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
 * Copyright 2009-2010 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
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
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor.PluggableType;
import org.opends.guitools.controlpanel.datamodel.DatabaseMonitoringTableModel;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.server.util.ServerConstants;

/** The panel displaying the database monitoring filtered attributes. */
class DatabaseMonitoringPanel extends GeneralMonitoringPanel
{
  private static final long serialVersionUID = 9031734563723229830L;

  private JTable table;
  private DatabaseMonitoringTableModel tableModel;
  private JScrollPane scroll;
  private JLabel noDBsFound;
  private JLabel noMonitoringFound;
  private JButton showFields;
  private Set<String> attributes = new LinkedHashSet<>();
  private final LinkedHashSet<String> allAttributes = new LinkedHashSet<>();

  private MonitoringAttributesViewPanel<String> fieldsViewPanel;
  private GenericDialog fieldsViewDlg;
  private final BackendDescriptor.PluggableType pluggableType;

  /**
   * Default constructor.
   * @param type the type of pluggable backend.
   */
  DatabaseMonitoringPanel(BackendDescriptor.PluggableType type)
  {
    pluggableType = type;
    createLayout();
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return table;
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    final JLabel lTitle = Utilities.createTitleLabel(
        PluggableType.JE == pluggableType ? INFO_CTRL_PANEL_JE_DB_INFO.get() : INFO_CTRL_PANEL_PDB_DB_INFO.get());
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
    showFields = Utilities.createButton(INFO_CTRL_PANEL_OPERATIONS_VIEW.get());
    showFields.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        fieldsViewClicked();
      }
    });
    showFields.setVisible(false);
    gbc.gridx = 0;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(Box.createHorizontalGlue(), gbc);
    gbc.gridx ++;
    gbc.weightx = 0.0;
    add(showFields, gbc);

    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.gridwidth = 2;
    tableModel = new DatabaseMonitoringTableModel();
    tableModel.setAttributes(attributes);
    table = Utilities.createSortableTable(tableModel, new DefaultTableCellRenderer());
    scroll = Utilities.createScrollPane(table);
    updateTableSize();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    add(scroll, gbc);
    noDBsFound = Utilities.createDefaultLabel(INFO_CTRL_PANEL_NO_DBS_FOUND.get());
    noDBsFound.setHorizontalAlignment(SwingConstants.CENTER);
    add(noDBsFound, gbc);
    noMonitoringFound = Utilities.createDefaultLabel(INFO_CTRL_PANEL_NO_DB_MONITORING_FOUND.get());
    noMonitoringFound.setHorizontalAlignment(SwingConstants.CENTER);
    add(noMonitoringFound, gbc);

    setBorder(PANEL_BORDER);
  }

  @Override
  public void updateContents()
  {
    boolean backendsFound = false;
    ServerDescriptor server = null;
    if (getInfo() != null)
    {
      server = getInfo().getServerDescriptor();
    }
    Set<BackendDescriptor> dbBackends = new HashSet<>();
    boolean updateAttributes = allAttributes.isEmpty();
    SortedSet<String> sortedAttrNames = new TreeSet<>();
    if (server != null)
    {
      for (BackendDescriptor backend : server.getBackends())
      {
        if (BackendDescriptor.Type.PLUGGABLE == backend.getType()
            && pluggableType == backend.getPluggableType())
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
      if (!attributes.isEmpty())
      {
        setFieldsToDisplay(attributes);
        updateTableSize();
      }
    }
    tableModel.setData(dbBackends);
    showFields.setVisible(backendsFound);
    scroll.setVisible(backendsFound && !allAttributes.isEmpty());
    noDBsFound.setVisible(!backendsFound);
    noMonitoringFound.setVisible(backendsFound && allAttributes.isEmpty());
    showFields.setVisible(!allAttributes.isEmpty());
  }


  private void updateTableSize()
  {
    Utilities.updateTableSizes(table, 8);
    Utilities.updateScrollMode(scroll, table);
  }

  /** Displays a dialog allowing the user to select which fields to display. */
  private void fieldsViewClicked()
  {
    if (fieldsViewDlg == null)
    {
      fieldsViewPanel = MonitoringAttributesViewPanel.createStringInstance(allAttributes);
      fieldsViewDlg = new GenericDialog(Utilities.getFrame(this), fieldsViewPanel);
      fieldsViewDlg.setModal(true);
      Utilities.centerGoldenMean(fieldsViewDlg, Utilities.getParentDialog(this));
    }
    fieldsViewPanel.setSelectedAttributes(attributes);
    fieldsViewDlg.setVisible(true);
    if (!fieldsViewPanel.isCanceled())
    {
      attributes = fieldsViewPanel.getAttributes();
      setFieldsToDisplay(attributes);
      updateTableSize();
    }
  }

  private void setFieldsToDisplay(Set<String> attributes)
  {
    this.attributes = attributes;
    tableModel.setAttributes(attributes);
    tableModel.forceDataStructureChange();
  }

  private Set<String> getMonitoringAttributes(BackendDescriptor backend)
  {
    Set<String> attrNames = new HashSet<>();
    if (backend.getMonitoringEntry() != null)
    {
      Set<String> allNames = backend.getMonitoringEntry().getAttributeNames();
      for (String attrName : allNames)
      {
        if (!attrName.equalsIgnoreCase(ServerConstants.OBJECTCLASS_ATTRIBUTE_TYPE_NAME)
            && !attrName.equalsIgnoreCase(ServerConstants.ATTR_COMMON_NAME))
        {
          attrNames.add(attrName);
        }
      }
    }
    return attrNames;
  }
}
