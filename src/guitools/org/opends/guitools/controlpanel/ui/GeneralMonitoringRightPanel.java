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

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;

import javax.swing.JPanel;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;


/**
 * The panel on the right of the 'General Information' panel.
 *
 */
public class GeneralMonitoringRightPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = -4197460101279681042L;

  /**
   * The panel with a CardLayout that contains all the panels.
   */
  protected JPanel mainPanel;

  private RootMonitoringPanel rootPanel = new RootMonitoringPanel();
  private WorkQueueMonitoringPanel workQueuePanel =
    new WorkQueueMonitoringPanel();
  private EntryCachesMonitoringPanel entryCachesPanel =
    new EntryCachesMonitoringPanel();
  private DBEnvironmentMonitoringPanel dbEnvironmentPanel =
    new DBEnvironmentMonitoringPanel();
  private SystemInformationMonitoringPanel systemInformationPanel =
    new SystemInformationMonitoringPanel();
  private JavaInformationMonitoringPanel javaInformationPanel =
    new JavaInformationMonitoringPanel();

  /**
   * The panel used to update messages.
   */
  protected NoItemSelectedPanel noEntryPanel = new NoItemSelectedPanel();

  private final StatusGenericPanel[] panels =
  {
      rootPanel,
      workQueuePanel,
      entryCachesPanel,
      dbEnvironmentPanel,
      systemInformationPanel,
      javaInformationPanel
  };

  /**
   * Default constructor.
   *
   */
  public GeneralMonitoringRightPanel()
  {
    super();
    createLayout();
  }

  /**
   * Displays a panel containing a message.
   * @param msg the message.
   *
   */
  public void displayMessage(Message msg)
  {
    noEntryPanel.setMessage(msg);
    ((CardLayout)mainPanel.getLayout()).show(mainPanel, getTitle(noEntryPanel));
  }

  /**
   * {@inheritDoc}
   */
  public void setInfo(ControlPanelInfo info)
  {
    super.setInfo(info);
    for (StatusGenericPanel panel : panels)
    {
      panel.setInfo(info);
    }
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  protected void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    CardLayout cardLayout = new CardLayout();
    mainPanel = new JPanel(cardLayout);
    mainPanel.setOpaque(false);
    noEntryPanel.setMessage(
        INFO_CTRL_PANEL_GENERAL_MONITORING_NO_ITEM_SELECTED.get());
    JPanel[] panelsWithScroll =
    {
        noEntryPanel,
        rootPanel,
        workQueuePanel,
        entryCachesPanel,
        systemInformationPanel,
        javaInformationPanel
    };
    JPanel[] panelsWithNoScroll =
    {
        dbEnvironmentPanel
    };
    for (JPanel panel : panelsWithScroll)
    {
      mainPanel.add(Utilities.createBorderLessScrollBar(panel),
          getTitle(panel));
    }
    for (JPanel panel : panelsWithNoScroll)
    {
      mainPanel.add(panel, getTitle(panel));
    }
    cardLayout.show(mainPanel, getTitle(noEntryPanel));
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
    return Message.EMPTY;
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  /**
   * Updates the contents of the panel with the root monitoring information.
   *
   */
  public void updateRoot()
  {
    rootPanel.updateContents();
    ((CardLayout)mainPanel.getLayout()).show(mainPanel,
        getTitle(rootPanel));
  }

  /**
   * Updates the contents of the panel with the system information monitoring.
   *
   */
  public void updateSystemInformation()
  {
    systemInformationPanel.updateContents();
    ((CardLayout)mainPanel.getLayout()).show(mainPanel,
        getTitle(systemInformationPanel));
  }

  /**
   * Updates the contents of the panel with the work queue monitoring
   * information.
   *
   */
  public void updateWorkQueue()
  {
    workQueuePanel.updateContents();
    ((CardLayout)mainPanel.getLayout()).show(mainPanel,
        getTitle(workQueuePanel));
  }

  /**
   * Updates the contents of the panel with the entry caches monitoring
   * information.
   *
   */
  public void updateEntryCaches()
  {
    entryCachesPanel.updateContents();
    ((CardLayout)mainPanel.getLayout()).show(mainPanel,
        getTitle(entryCachesPanel));
  }

  /**
   * Updates the contents of the panel with the database environment monitoring
   * information.
   *
   */
  public void updateDBEnvironment()
  {
    dbEnvironmentPanel.updateContents();
    ((CardLayout)mainPanel.getLayout()).show(mainPanel,
        getTitle(dbEnvironmentPanel));
  }

  /**
   * Updates the contents of the panel with the JAVA information.
   *
   */
  public void updateJavaInformation()
  {
    javaInformationPanel.updateContents();
    ((CardLayout)mainPanel.getLayout()).show(mainPanel,
        getTitle(javaInformationPanel));
  }

  /**
   * Returns the title for a given panel. It will be used to update the
   * CardLayout.
   * @param panel the panel we want to get the title from.
   * @return the title for a given panel.
   */
  protected String getTitle(JPanel panel)
  {
    return panel.getClass().toString();
  }
}
