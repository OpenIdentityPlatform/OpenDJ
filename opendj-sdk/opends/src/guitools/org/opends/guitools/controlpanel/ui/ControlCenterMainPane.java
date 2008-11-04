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

import java.awt.Dimension;

import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.ConfigChangeListener;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.util.Utilities;

/**
 * The main panel of the control panel.  It contains a split pane.  On the left
 * we have some actions and on the right some global information about the
 * server.
 *
 */
public class ControlCenterMainPane extends JSplitPane
{
  private static final long serialVersionUID = -8939025523701408656L;
  private StatusPanel statusPane;
  /**
   * Constructor.
   * @param info the control panel info.
   */
  public ControlCenterMainPane(ControlPanelInfo info)
  {
    super(JSplitPane.HORIZONTAL_SPLIT);
    setOpaque(true); //content panes must be opaque

    //setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1,
    //    AccordionElementBorder.bottomColor));

    statusPane = new StatusPanel();
    statusPane.setBorder(new EmptyBorder(10, 10, 30, 10));
    statusPane.setInfo(info);
    //statusPane.setBorder(BorderFactory.createCompoundBorder(
    //    BorderFactory.createMatteBorder(0, 0, 0, 0,
    //        AccordionElementBorder.bottomColor),
    //        new EmptyBorder(10, 10, 30, 10)));

    MainActionsPane mainActionsPane = new MainActionsPane();
    mainActionsPane.setInfo(info);
    JScrollPane accordionScroll = Utilities.createScrollPane(mainActionsPane);
    accordionScroll.getViewport().setBackground(
        ColorAndFontConstants.greyBackground);
    JScrollPane statusScroll = Utilities.createScrollPane(statusPane);

//  Create a split pane with the two scroll panes in it.
    setLeftComponent(accordionScroll);

    setRightComponent(statusScroll);
    setResizeWeight(0.0);

    setDividerLocation(accordionScroll.getPreferredSize().width + 2);

    setPreferredSize(
        new Dimension(getPreferredSize().width + 4, getPreferredSize().height));
    info.addConfigChangeListener(new ConfigChangeListener()
    {
      private boolean lastStatusStopped;
      /**
       * {@inheritDoc}
       */
      public void configurationChanged(ConfigurationChangeEvent ev)
      {
        if (ev.getNewDescriptor().getStatus() !=
          ServerDescriptor.ServerStatus.STARTED)
        {
          lastStatusStopped = true;
        }
        else if (lastStatusStopped && !ev.getNewDescriptor().isAuthenticated())
        {
          lastStatusStopped = false;
          SwingUtilities.invokeLater(new Runnable()
          {
            /**
             * {@inheritDoc}
             */
            public void run()
            {
              getLoginDialog().setVisible(true);
              getLoginDialog().toFront();
            }
          });
        }
      }
    });
  }

  /**
   * Returns the login dialog used to ask authentication to the user.
   * @return the login dialog used to ask authentication to the user.
   */
  public GenericDialog getLoginDialog()
  {
    return statusPane.getLoginDialog();
  }
}
