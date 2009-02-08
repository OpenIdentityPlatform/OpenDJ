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
import static org.opends.messages.BackendMessages.INFO_MONITOR_UPTIME;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.util.Date;

import javax.swing.Box;
import javax.swing.JLabel;

import org.opends.guitools.controlpanel.datamodel.CustomSearchResult;
import org.opends.guitools.controlpanel.datamodel.BasicMonitoringAttributes;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.util.ConfigFromDirContext;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;

/**
 * The panel displaying the root monitor panel.
 */
public class RootMonitoringPanel extends GeneralMonitoringPanel
{
  private static final long serialVersionUID = 9031734563746269830L;

  private JLabel openConnections = Utilities.createDefaultLabel();
  private JLabel maxConnections = Utilities.createDefaultLabel();
  private JLabel totalConnections = Utilities.createDefaultLabel();
  private JLabel startTime = Utilities.createDefaultLabel();
  private JLabel upTime = Utilities.createDefaultLabel();
  private JLabel version = Utilities.createDefaultLabel();

  /**
   * Default constructor.
   */
  public RootMonitoringPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return openConnections;
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridy ++;
    JLabel lTitle = Utilities.createTitleLabel(
        INFO_CTRL_PANEL_GENERAL_MONITORING_ROOT.get());
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridwidth = 2;
    gbc.gridy = 0;
    gbc.insets.top = 5;
    gbc.insets.bottom = 7;
    add(lTitle, gbc);

    gbc.insets.bottom = 0;
    gbc.insets.top = 10;
    Message[] labels = {
        INFO_CTRL_PANEL_OPEN_CONNECTIONS_LABEL.get(),
        INFO_CTRL_PANEL_MAX_CONNECTIONS_LABEL.get(),
        INFO_CTRL_PANEL_TOTAL_CONNECTIONS_LABEL.get(),
        INFO_CTRL_PANEL_START_TIME_LABEL.get(),
        INFO_CTRL_PANEL_UP_TIME_LABEL.get(),
        INFO_CTRL_PANEL_OPENDS_VERSION_LABEL.get()
    };
    JLabel[] values = {
        openConnections,
        maxConnections,
        totalConnections,
        startTime,
        upTime,
        version
        };
    gbc.gridy ++;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridwidth = 1;
    for (int i=0; i < labels.length; i++)
    {
      gbc.insets.left = 0;
      gbc.gridx = 0;
      JLabel l = Utilities.createPrimaryLabel(labels[i]);
      add(l, gbc);
      gbc.insets.left = 10;
      gbc.gridx = 1;
      add(values[i], gbc);
      gbc.gridy ++;
    }

    gbc.gridwidth = 2;
    gbc.gridx = 0;
    gbc.gridy ++;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    add(Box.createGlue(), gbc);

    setBorder(PANEL_BORDER);
  }

  /**
   * Updates the contents of the panel.
   *
   */
  public void updateContents()
  {
    ServerDescriptor server = null;
    if (getInfo() != null)
    {
      server = getInfo().getServerDescriptor();
    }
    CustomSearchResult csr = null;
    if (server != null)
    {
      csr = server.getRootMonitor();
    }
    if (csr != null)
    {
      JLabel[] ls =
      {
          openConnections,
          maxConnections,
          totalConnections,
          startTime
      };
      BasicMonitoringAttributes[] attrs =
      {
        BasicMonitoringAttributes.CURRENT_CONNECTIONS,
        BasicMonitoringAttributes.MAX_CONNECTIONS,
        BasicMonitoringAttributes.TOTAL_CONNECTIONS,
        BasicMonitoringAttributes.START_DATE
      };
      for (int i=0; i<ls.length; i++)
      {
        ls[i].setText(getMonitoringValue(attrs[i], csr));
      }
      version.setText(server.getOpenDSVersion());
      try
      {
        String start = (String)getFirstMonitoringValue(csr,
            BasicMonitoringAttributes.START_DATE.getAttributeName());
        String current = (String)getFirstMonitoringValue(csr,
            BasicMonitoringAttributes.CURRENT_DATE.getAttributeName());
        Date startTime = ConfigFromDirContext.utcParser.parse(start);
        Date currentTime = ConfigFromDirContext.utcParser.parse(current);

        long upSeconds = (currentTime.getTime() - startTime.getTime()) / 1000;
        long upDays = (upSeconds / 86400);
        upSeconds %= 86400;
        long upHours = (upSeconds / 3600);
        upSeconds %= 3600;
        long upMinutes = (upSeconds / 60);
        upSeconds %= 60;
        Message upTimeStr =
          INFO_MONITOR_UPTIME.get(upDays, upHours, upMinutes, upSeconds);

        upTime.setText(upTimeStr.toString());
      }
      catch (Throwable t)
      {
        upTime.setText(NO_VALUE_SET.toString());
      }
    }
    else
    {
      openConnections.setText(NO_VALUE_SET.toString());
      maxConnections.setText(NO_VALUE_SET.toString());
      totalConnections.setText(NO_VALUE_SET.toString());
      startTime.setText(NO_VALUE_SET.toString());
      upTime.setText(NO_VALUE_SET.toString());
      version.setText(NO_VALUE_SET.toString());
    }
  }
}
