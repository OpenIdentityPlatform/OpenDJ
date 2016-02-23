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
 * Portions Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.server.util.StaticUtils.isOEMVersion;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;

import javax.swing.JPanel;

import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.util.Utilities;
import org.forgerock.i18n.LocalizableMessage;


/**
 * The panel on the right of the 'General Information' panel.
 *
 */
public class GeneralMonitoringRightPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = -4197460101279681042L;

  /** The panel with a CardLayout that contains all the panels. */
  protected JPanel mainPanel;

  private RootMonitoringPanel rootPanel = new RootMonitoringPanel();
  private WorkQueueMonitoringPanel workQueuePanel = new WorkQueueMonitoringPanel();
  private EntryCachesMonitoringPanel entryCachesPanel = new EntryCachesMonitoringPanel();
  private DatabaseMonitoringPanel jeMonitoringPanel = new DatabaseMonitoringPanel(BackendDescriptor.PluggableType.JE);
  private DatabaseMonitoringPanel pdbMonitoringPanel = new DatabaseMonitoringPanel(BackendDescriptor.PluggableType.PDB);
  private SystemInformationMonitoringPanel systemInformationPanel = new SystemInformationMonitoringPanel();
  private JavaInformationMonitoringPanel javaInformationPanel = new JavaInformationMonitoringPanel();

  private static final String rootPanelTitle = "RootMonitoringPanel";
  private static final String workQueuePanelTitle = "WorkQueueMonitoringPanel";
  private static final String entryCachesPanelTitle = "EntryCachesMonitoringPanel";
  private static final String jeMonitoringPanelTitle = "JEDatabaseMonitoringPanel";
  private static final String pdbMonitoringPanelTitle = "PDBDatabaseMonitoringPanel";
  private static final String systemInformationPanelTitle = "SystemInformationMonitoringPanel";
  private static final String javaInformationPanelTitle = "JavaInformationMonitoringPanel";

  /** The panel used to update messages. */
  protected NoItemSelectedPanel noEntryPanel = new NoItemSelectedPanel();
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
  public void displayMessage(LocalizableMessage msg)
  {
    noEntryPanel.setMessage(msg);
    ((CardLayout)mainPanel.getLayout()).show(mainPanel, noEntryPanelTitle);
  }

  /** {@inheritDoc} */
  public void setInfo(ControlPanelInfo info)
  {
    super.setInfo(info);
    for (StatusGenericPanel panel : panels)
    {
      panel.setInfo(info);
    }
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  protected void createLayout()
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

  /** {@inheritDoc} */
  public void okClicked()
  {
    // No ok button
  }

  /** {@inheritDoc} */
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.NO_BUTTON;
  }

  /** {@inheritDoc} */
  public LocalizableMessage getTitle()
  {
    return LocalizableMessage.EMPTY;
  }

  /** {@inheritDoc} */
  public Component getPreferredFocusComponent()
  {
    return null;
  }

  /** {@inheritDoc} */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  /** Updates the contents of the panel with the root monitoring information. */
  public void updateRoot()
  {
    rootPanel.updateContents();
    ((CardLayout)mainPanel.getLayout()).show(mainPanel, rootPanelTitle);
  }

  /** Updates the contents of the panel with the system information monitoring. */
  public void updateSystemInformation()
  {
    systemInformationPanel.updateContents();
    ((CardLayout)mainPanel.getLayout()).show(mainPanel, systemInformationPanelTitle);
  }

  /** Updates the contents of the panel with the work queue monitoring information. */
  public void updateWorkQueue()
  {
    workQueuePanel.updateContents();
    ((CardLayout)mainPanel.getLayout()).show(mainPanel, workQueuePanelTitle);
  }

  /** Updates the contents of the panel with the entry caches monitoring information. */
  public void updateEntryCaches()
  {
    entryCachesPanel.updateContents();
    ((CardLayout)mainPanel.getLayout()).show(mainPanel, entryCachesPanelTitle);
  }

  /** Updates the contents of the panel with the je database monitoring information. */
  public void updateJEDatabaseInformation()
  {
    jeMonitoringPanel.updateContents();
    ((CardLayout)mainPanel.getLayout()).show(mainPanel, jeMonitoringPanelTitle);
  }

  /** Updates the contents of the panel with the pdb database monitoring information. */
  public void updatePDBDatbaseInformation()
  {
    pdbMonitoringPanel.updateContents();
    ((CardLayout)mainPanel.getLayout()).show(mainPanel, pdbMonitoringPanelTitle);
  }

  /** Updates the contents of the panel with the JAVA information. */
  public void updateJavaInformation()
  {
    javaInformationPanel.updateContents();
    ((CardLayout)mainPanel.getLayout()).show(mainPanel, javaInformationPanelTitle);
  }

}
