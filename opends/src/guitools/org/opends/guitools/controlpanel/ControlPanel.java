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
import org.opends.guitools.controlpanel.ui.ControlCenterMainPane;
import org.opends.guitools.controlpanel.ui.GenericDialog;
import org.opends.guitools.controlpanel.ui.MainMenuBar;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.AdminToolMessages;
import org.opends.quicksetup.Installation;
import org.opends.messages.Message;
import org.opends.quicksetup.util.Utils;
import org.opends.server.util.DynamicConstants;

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
   * control panel.  Nothing is done here: the user must say whether the server
   * is local or remote.
   * @param args the arguments that are passed in the command line.
   */
  public void initialize(String[] args)
  {
    info = ControlPanelInfo.getInstance();
    // Call Installation because the LocalOrRemotePanel uses it to check
    // whether the server is running or not and to get the install path.
    Installation.getLocal();
  }

  /**
   * Creates the main Control Panel dialog and displays it.
   */
  public void createAndDisplayGUI()
  {
    GenericDialog localOrRemote =
      ControlCenterMainPane.getLocalOrRemoteDialog(info);
    Utilities.centerOnScreen(localOrRemote);
    localOrRemote.setVisible(true);

    if (info.getServerDescriptor() == null)
    {
      MainMenuBar menuBar = new MainMenuBar(info);
      // Assume that the user decided to quit the application
      menuBar.quitClicked();
    }
    // To be sure that the dlg receives the new configuration event before
    // calling pack.
    SwingUtilities.invokeLater(new Runnable()
    {
      public void run()
      {
        // Create and set up the content pane.
        controlCenterPane = new ControlCenterMainPane(info);
        //  Create and set up the window.
        dlg = Utilities.createFrame();
        dlg.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        final MainMenuBar menuBar = new MainMenuBar(info);
        dlg.addWindowListener(new WindowAdapter() {
          public void windowClosing(WindowEvent e) {
            menuBar.quitClicked();
          }
        });
        dlg.setJMenuBar(menuBar);
        String title = Utils.getCustomizedObject(
            "INFO_CONTROL_PANEL_TITLE",
            AdminToolMessages.INFO_CONTROL_PANEL_TITLE.get(
            DynamicConstants.PRODUCT_NAME),
            Message.class).toString();
        dlg.setTitle(title);
        dlg.setContentPane(controlCenterPane);
        dlg.pack();
        Utilities.centerOnScreen(dlg);

        dlg.setVisible(true);
      }
    });
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
