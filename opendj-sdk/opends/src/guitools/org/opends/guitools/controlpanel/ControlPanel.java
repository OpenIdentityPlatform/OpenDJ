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

package org.opends.guitools.controlpanel;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.ui.ControlCenterMainPane;
import org.opends.guitools.controlpanel.ui.MainMenuBar;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.AdminToolMessages;

/**
 * The class that is in charge of creating the main dialog of the ControlPanel
 * and the ControlCenterInfo (the data structure that is used by all the GUI
 * components and that contains information about the server status and server
 * configuration).
 */
public class ControlPanel
{
  private JFrame dlg;
  private ControlPanelInfo info;
  private ControlCenterMainPane controlCenterPane;

  /**
   * Main method that is used for testing purposes.  The control-panel
   * command-line is launched through the ControlPanelLauncher (which displays
   * a splash screen and calls the <code>initialize</code> and
   * <code>createAndDisplayMethods</code>.
   * @param args the arguments that are passed.
   */
  public static void main(String[] args) {
    try
    {
      initLookAndFeel();
    }
    catch (Throwable t)
    {
      t.printStackTrace();
    }
    final ControlPanel test = new ControlPanel();
    test.initialize(args);
    javax.swing.SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        test.createAndDisplayGUI();
      }
    });
  }

  /**
   * Method that creates the ControlCenterInfo object that will be in all the
   * control panel.  Here it basically reads the configuration of the
   * configuration file.
   * @param args the arguments that are passed in the command line.
   */
  public void initialize(String[] args)
  {
    info = ControlPanelInfo.getInstance();
    info.regenerateDescriptor();
    info.startPooling();
  }

  /**
   * Creates the main Control Panel dialog and displays it.
   */
  public void createAndDisplayGUI()
  {
//  Create and set up the content pane.
    controlCenterPane = new ControlCenterMainPane(info);
    //  Create and set up the window.
    dlg = new JFrame();
    dlg.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    final MainMenuBar menuBar = new MainMenuBar(info);
    dlg.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        menuBar.quitClicked();
      }
    });
    dlg.setJMenuBar(menuBar);
    dlg.setTitle(AdminToolMessages.INFO_CONTROL_PANEL_TITLE.get().toString());
    dlg.setContentPane(controlCenterPane);

    dlg.pack();
    Utilities.centerOnScreen(dlg);
    dlg.setVisible(true);
    if (info.getServerDescriptor().getStatus() ==
      ServerDescriptor.ServerStatus.STARTED)
    {
      controlCenterPane.getLoginDialog().setVisible(true);
      controlCenterPane.getLoginDialog().toFront();
    }
  }

  private static void initLookAndFeel() throws Throwable
  {
    if (SwingUtilities.isEventDispatchThread())
    {
      UIManager.setLookAndFeel(
          UIManager.getSystemLookAndFeelClassName());
    }
    else
    {
      final Throwable[] ts = {null};
      SwingUtilities.invokeAndWait(new Runnable()
      {
        public void run()
        {
          try
          {
            UIManager.setLookAndFeel(
                UIManager.getSystemLookAndFeelClassName());
          }
          catch (Throwable t)
          {
            ts[0] = t;
          }
        }
      });
      if (ts[0] != null)
      {
        throw ts[0];
      }
    }
  }
}
