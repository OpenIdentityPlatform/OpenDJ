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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011 ForgeRock AS
 */

package org.opends.guitools.controlpanel;

import static org.opends.messages.AdminToolMessages.
 INFO_CONTROL_PANEL_LAUNCHER_USAGE_DESCRIPTION;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ControlCenterMainPane;
import org.opends.guitools.controlpanel.ui.GenericFrame;
import org.opends.guitools.controlpanel.ui.LocalOrRemotePanel;
import org.opends.guitools.controlpanel.ui.MainMenuBar;
import org.opends.guitools.controlpanel.util.BlindApplicationTrustManager;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.AdminToolMessages;
import org.opends.quicksetup.Installation;
import org.opends.messages.Message;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.util.Utils;
import org.opends.server.util.DynamicConstants;
import org.opends.server.util.args.ArgumentException;

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
  private ControlPanelArgumentParser argParser;
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
   * @param args the arguments that are passed in the command line.  The code
   * assumes that the arguments have been already validated.
   */
  public void initialize(String[] args)
  {
    info = ControlPanelInfo.getInstance();
    // Call Installation because the LocalOrRemotePanel uses it to check
    // whether the server is running or not and to get the install path.
    Installation.getLocal();
    argParser = new ControlPanelArgumentParser(ControlPanel.class.getName(),
        INFO_CONTROL_PANEL_LAUNCHER_USAGE_DESCRIPTION.get());
    try
    {
      argParser.initializeArguments();
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      // Bug
      throw new IllegalStateException("Arguments not correctly parsed: "+ae,
          ae);
    }
    if (argParser.isTrustAll())
    {
      info.setTrustManager(new BlindApplicationTrustManager());
    }
    info.setConnectTimeout(argParser.getConnectTimeout());
  }

  /**
   * Creates the main Control Panel dialog and displays it.
   */
  public void createAndDisplayGUI()
  {
    LocalOrRemotePanel localOrRemotePanel = new LocalOrRemotePanel();
    localOrRemotePanel.setInfo(info);
    final GenericFrame localOrRemote = new GenericFrame(localOrRemotePanel);
    localOrRemote.pack();
    Utilities.centerOnScreen(localOrRemote);

    if (argParser.isRemote())
    {
      updateLocalOrRemotePanel(localOrRemote);
    }

    if (argParser.getBindPassword() != null)
    {
      updateLocalOrRemotePanel(localOrRemote);
      getLocalOrRemotePanel(localOrRemote.getContentPane()).
      setCallOKWhenVisible(true);
    }

    ComponentListener listener = new ComponentAdapter()
    {
      /**
       * {@inheritDoc}
       */
      public void componentHidden(ComponentEvent e)
      {
        handleWindowClosed(localOrRemote, info);
      }
    };
    localOrRemote.addComponentListener(listener);
    localOrRemote.setVisible(true);
  }

  private void handleWindowClosed(GenericFrame localOrRemote,
      final ControlPanelInfo info)
  {
    if (info.getServerDescriptor() == null)
    {
      MainMenuBar menuBar = new MainMenuBar(info);
      // Assume that the user decided to quit the application
      menuBar.quitClicked();
    }

    updateSharedLocalOrRemotePanel(localOrRemote, info);

    // To be sure that the dialog receives the new configuration event before
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
    UIFactory.initializeLookAndFeel();
  }

  private void updateLocalOrRemotePanel(RootPaneContainer localOrRemote)
  {
    LocalOrRemotePanel panel =
      getLocalOrRemotePanel(localOrRemote.getContentPane());
    if (panel != null)
    {
      if (argParser.isRemote())
      {
        panel.setRemote(true);
      }
      if (argParser.getExplicitHostName() != null)
      {
        panel.setHostName(argParser.getExplicitHostName());
        panel.setRemote(true);
      }
      if (argParser.getExplicitPort() != -1)
      {
        panel.setPort(argParser.getExplicitPort());
        panel.setRemote(true);
      }
      if (argParser.getExplicitBindDn() != null)
      {
        panel.setBindDN(argParser.getExplicitBindDn());
      }
      if (argParser.getBindPassword() != null)
      {
        panel.setBindPassword(argParser.getBindPassword().toCharArray());
      }
    }
  }

  /**
   * A method used to update the contents of the dialog displayed when the user
   * selects 'Server To Administer...'.  This is done because this class
   * displays a GenericFrame and in the rest of the UI a GenericDialog is
   * shown.
   * @param localOrRemote the frame displayed by this class.
   * @param info the generic info.
   */
  private void updateSharedLocalOrRemotePanel(RootPaneContainer localOrRemote,
      ControlPanelInfo info)
  {
    LocalOrRemotePanel panel =
      getLocalOrRemotePanel(localOrRemote.getContentPane());
    LocalOrRemotePanel panelToUpdate = getLocalOrRemotePanel(
        ControlCenterMainPane.getLocalOrRemoteDialog(info));
    if (panel != null && panelToUpdate != null)
    {
      panelToUpdate.setRemote(panel.isRemote());
      if (panel.getHostName() != null)
      {
        panelToUpdate.setHostName(panel.getHostName());
      }
      if (panel.getPort() != -1)
      {
        panelToUpdate.setPort(panel.getPort());
      }
      if (panel.getBindDN() != null)
      {
        panelToUpdate.setBindDN(panel.getBindDN());
      }
    }
  }

  private LocalOrRemotePanel getLocalOrRemotePanel(Container c)
  {
    LocalOrRemotePanel panel = null;
    if (c instanceof LocalOrRemotePanel)
    {
      panel = (LocalOrRemotePanel)c;
    }
    else
    {
      for (Component comp : c.getComponents())
      {
        if (comp instanceof Container)
        {
          panel = getLocalOrRemotePanel((Container)comp);
        }
        if (panel != null)
        {
          break;
        }
      }
    }
    return panel;
  }
}
