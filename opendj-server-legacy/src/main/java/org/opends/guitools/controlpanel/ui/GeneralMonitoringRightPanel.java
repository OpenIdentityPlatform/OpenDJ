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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.util.StaticUtils.isOEMVersion;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;

import javax.swing.JPanel;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.ui.BrowseGeneralMonitoringPanel.NodeType;
import org.opends.guitools.controlpanel.util.Utilities;


/** The panel on the right of the 'General Information' panel. */
class GeneralMonitoringRightPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = -4197460101279681042L;

  /** The panel with a CardLayout that contains all the panels. */
  private JPanel mainPanel;

  private final RootMonitoringPanel rootPanel = new RootMonitoringPanel();
  private final WorkQueueMonitoringPanel workQueuePanel = new WorkQueueMonitoringPanel();
  private final EntryCachesMonitoringPanel entryCachesPanel = new EntryCachesMonitoringPanel();
  private final DatabaseMonitoringPanel jeMonitoringPanel = new DatabaseMonitoringPanel(
      BackendDescriptor.PluggableType.JE);
  private final DatabaseMonitoringPanel pdbMonitoringPanel = new DatabaseMonitoringPanel(
      BackendDescriptor.PluggableType.PDB);
  private final SystemInformationMonitoringPanel systemInformationPanel = new SystemInformationMonitoringPanel();
  private final JavaInformationMonitoringPanel javaInformationPanel = new JavaInformationMonitoringPanel();

  private static final String rootPanelTitle = "RootMonitoringPanel";
  private static final String workQueuePanelTitle = "WorkQueueMonitoringPanel";
  private static final String entryCachesPanelTitle = "EntryCachesMonitoringPanel";
  private static final String jeMonitoringPanelTitle = "JEDatabaseMonitoringPanel";
  private static final String pdbMonitoringPanelTitle = "PDBDatabaseMonitoringPanel";
  private static final String systemInformationPanelTitle = "SystemInformationMonitoringPanel";
  private static final String javaInformationPanelTitle = "JavaInformationMonitoringPanel";

  /** The panel used to update messages. */
  private final NoItemSelectedPanel noEntryPanel = new NoItemSelectedPanel();
  private static final String noEntryPanelTitle = "JavaInformationMonitoringPanel";

  private final StatusGenericPanel[] panels =
  {
      rootPanel,
      workQueuePanel,
      entryCachesPanel,
      jeMonitoringPanel,
      pdbMonitoringPanel,
      systemInformationPanel,
      javaInformationPanel
  };

  /** Default constructor. */
  public GeneralMonitoringRightPanel()
  {
    super();
    createLayout();
  }

  /**
   * Displays a panel containing a message.
   * @param msg the message.
   */
  @Override
  public void displayMessage(LocalizableMessage msg)
  {
    noEntryPanel.setMessage(msg);
    ((CardLayout)mainPanel.getLayout()).show(mainPanel, noEntryPanelTitle);
  }

  @Override
  public void setInfo(ControlPanelInfo info)
  {
    super.setInfo(info);
    for (StatusGenericPanel panel : panels)
    {
      panel.setInfo(info);
    }
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    CardLayout cardLayout = new CardLayout();
    mainPanel = new JPanel(cardLayout);
    mainPanel.setOpaque(false);
    noEntryPanel.setMessage(INFO_CTRL_PANEL_GENERAL_MONITORING_NO_ITEM_SELECTED.get());
    // panels with scroll
    mainPanel.add(Utilities.createBorderLessScrollBar(noEntryPanel), noEntryPanelTitle);
    mainPanel.add(Utilities.createBorderLessScrollBar(rootPanel), rootPanelTitle);
    mainPanel.add(Utilities.createBorderLessScrollBar(workQueuePanel), workQueuePanelTitle);
    mainPanel.add(Utilities.createBorderLessScrollBar(entryCachesPanel), entryCachesPanelTitle);
    mainPanel.add(Utilities.createBorderLessScrollBar(systemInformationPanel), systemInformationPanelTitle);
    mainPanel.add(Utilities.createBorderLessScrollBar(javaInformationPanel), javaInformationPanelTitle);
    // panels with no scroll
    if (!isOEMVersion())
    {
      mainPanel.add(jeMonitoringPanel, jeMonitoringPanelTitle);
    }
    mainPanel.add(pdbMonitoringPanel, pdbMonitoringPanelTitle);
    cardLayout.show(mainPanel, noEntryPanelTitle);
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    add(mainPanel, gbc);
  }

  @Override
  public void okClicked()
  {
    // No ok button
  }

  @Override
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.NO_BUTTON;
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return LocalizableMessage.EMPTY;
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return null;
  }

  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    // no-op
  }

  void update(NodeType type)
  {
    switch (type)
    {
    case ROOT:
      update(rootPanel, rootPanelTitle);
      break;
    case SYSTEM_INFORMATION:
      update(systemInformationPanel, systemInformationPanelTitle);
      break;
    case WORK_QUEUE:
      update(workQueuePanel, workQueuePanelTitle);
      break;
    case ENTRY_CACHES:
      update(entryCachesPanel, entryCachesPanelTitle);
      break;
    case JE_DATABASES_INFORMATION:
      update(jeMonitoringPanel, jeMonitoringPanelTitle);
      break;
    case PDB_DATABASES_INFORMATION:
      update(pdbMonitoringPanel, pdbMonitoringPanelTitle);
      break;
    case JAVA_INFORMATION:
      update(javaInformationPanel, javaInformationPanelTitle);
      break;
    default:
      throw new RuntimeException("Unknown node type: " + type);
    }
  }

  private void update(GeneralMonitoringPanel panel, String panelTitle)
  {
    panel.updateContents();
    ((CardLayout) mainPanel.getLayout()).show(mainPanel, panelTitle);
  }
}
