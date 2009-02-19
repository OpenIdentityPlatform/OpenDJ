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
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.ConfigChangeListener;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;

/**
 * The main panel of the control panel.  It contains a split pane.  On the left
 * we have some actions and on the right some global information about the
 * server.
 *
 */
public class ControlCenterMainPane extends JPanel
{
  private static final long serialVersionUID = -8939025523701408656L;
  private StatusPanel statusPane;
  private JLabel lAuthenticatedAs =
    Utilities.createInlineHelpLabel(Message.EMPTY);

  /**
   * Constructor.
   * @param info the control panel info.
   */
  public ControlCenterMainPane(ControlPanelInfo info)
  {
    super(new GridBagLayout());
    setOpaque(true); //content panes must be opaque
    JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    split.setOpaque(true); //content panes must be opaque

    statusPane = new StatusPanel();
    statusPane.setInfo(info);

    MainActionsPane mainActionsPane = new MainActionsPane();
    mainActionsPane.setInfo(info);
    JScrollPane accordionScroll = Utilities.createScrollPane(mainActionsPane);
    accordionScroll.getViewport().setBackground(
        ColorAndFontConstants.greyBackground);

//  Create a split pane with the two scroll panes in it.
    split.setLeftComponent(accordionScroll);

    split.setRightComponent(statusPane);
    split.setResizeWeight(0.0);

    split.setDividerLocation(accordionScroll.getPreferredSize().width + 2);

    split.setPreferredSize(
        new Dimension(split.getPreferredSize().width + 4,
            split.getPreferredSize().height));
    info.addConfigChangeListener(new ConfigChangeListener()
    {
      private boolean lastStatusStopped;
      /**
       * {@inheritDoc}
       */
      public void configurationChanged(final ConfigurationChangeEvent ev)
      {
        final boolean displayLogin;
        if (ev.getNewDescriptor().getStatus() !=
          ServerDescriptor.ServerStatus.STARTED)
        {
          lastStatusStopped = true;
          displayLogin = false;
        }
        else if (lastStatusStopped && !ev.getNewDescriptor().isAuthenticated())
        {
          lastStatusStopped = false;
          displayLogin = true;
        }
        else
        {
          displayLogin = false;
        }
        SwingUtilities.invokeLater(new Runnable()
        {
          /**
           * {@inheritDoc}
           */
          public void run()
          {
            updateAuthenticationLabel(ev.getNewDescriptor());
            if (displayLogin)
            {
              getLoginDialog().setVisible(true);
              getLoginDialog().toFront();
            }
          }
        });
      }
    });

    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    add(split, gbc);

    JPanel infoPanel = new JPanel(new GridBagLayout());
    gbc.gridy = 1;
    gbc.weighty = 0.0;
    add(infoPanel, gbc);

    infoPanel.setOpaque(false);
    infoPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
        ColorAndFontConstants.defaultBorderColor));
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.insets = new Insets(5, 5, 5, 5);
    infoPanel.add(lAuthenticatedAs, gbc);
    gbc.gridx = 1;
    gbc.weightx = 1.0;
    gbc.insets.left = 0;
    gbc.insets.right = 0;
    lAuthenticatedAs.setText("Qjlabel");
    infoPanel.add(
        Box.createVerticalStrut(lAuthenticatedAs.getPreferredSize().height),
        gbc);
    if (info.getServerDescriptor() != null)
    {
      updateAuthenticationLabel(info.getServerDescriptor());
    }
    else
    {
      lAuthenticatedAs.setText("");
    }
  }

  /**
   * Returns the login dialog used to ask authentication to the user.
   * @return the login dialog used to ask authentication to the user.
   */
  public GenericDialog getLoginDialog()
  {
    return statusPane.getLoginDialog();
  }

  private void updateAuthenticationLabel(ServerDescriptor server)
  {
    if (server.getStatus() ==
      ServerDescriptor.ServerStatus.STARTED)
    {
      if (server.isAuthenticated())
      {
        try
        {
         String bindDN = ConnectionUtils.getBindDN(
             statusPane.getInfo().getDirContext());
         lAuthenticatedAs.setText(
             INFO_CTRL_PANEL_AUTHENTICATED_AS.get(bindDN).toString());
        }
        catch (Throwable t)
        {
        }
      }
      else
      {
        lAuthenticatedAs.setText(
            INFO_CTRL_PANEL_NOT_AUTHENTICATED.get().toString());
      }
    }
    else
    {
      lAuthenticatedAs.setText(
         INFO_CTRL_PANEL_NOT_AUTHENTICATED_SERVER_NOT_RUNNING.get().toString());
    }
  }
}
