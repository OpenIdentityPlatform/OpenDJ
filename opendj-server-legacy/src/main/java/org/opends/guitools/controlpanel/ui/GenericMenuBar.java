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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.ui;

import static org.opends.messages.AdminToolMessages.*;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.util.BackgroundTask;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.quicksetup.ui.WebBrowserErrorDialog;
import org.opends.quicksetup.util.Utils;
import org.opends.quicksetup.util.WebBrowserException;
import org.opends.quicksetup.util.WebBrowserLauncher;
import org.opends.server.util.DynamicConstants;

/** An abstract class that the different menu bars in the Control Panel extend. */

public abstract class GenericMenuBar extends JMenuBar
{
  private static final long serialVersionUID = -7289801307628271507L;

  private ControlPanelInfo info;

  /** The URL to the administration guide. */
  private final String ADMINISTRATION_GUIDE_URL =
    Utils.getCustomizedObject("ADMINISTRATION_GUIDE_URL",
        DynamicConstants.ADMINISTRATION_GUIDE_URL, String.class);

  /** The URL to the wiki main page. */
  private final String DOC_REFERENCE_WIKI =
    Utils.getCustomizedObject("DOC_REFERENCE_WIKI",
        DynamicConstants.DOC_REFERENCE_WIKI, String.class);

  /**
   * Constructor of the menu bar.
   * @param info the control panel information.
   */
  protected GenericMenuBar(ControlPanelInfo info)
  {
    this.info = info;
  }

  /**
   * Returns the control panel information.
   * @return the control panel information.
   */
  public ControlPanelInfo getInfo()
  {
    return info;
  }

  /**
   * Creates the Help menu bar.
   * @return the Help menu bar.
   */
  protected JMenu createHelpMenuBar()
  {
    JMenu menu = Utilities.createMenu(INFO_CTRL_PANEL_HELP_MENU.get(),
        INFO_CTRL_PANEL_HELP_MENU_DESCRIPTION.get());
    menu.setMnemonic(KeyEvent.VK_H);
    JMenuItem menuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_ADMINISTRATION_GUIDE_MENU.get());
    menuItem.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        displayURL(ADMINISTRATION_GUIDE_URL);
      }
    });
    menu.add(menuItem);
    menuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_DOCUMENTATION_WIKI_MENU.get());
    menuItem.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        displayURL(DOC_REFERENCE_WIKI);
      }
    });
    menu.add(menuItem);
    return menu;
  }

  /**
   * Tries to display a URL in the systems default WEB browser.
   * @param url the URL to be displayed.
   */
  private void displayURL(final String url)
  {
    BackgroundTask<Void> worker = new BackgroundTask<Void>()
    {
      @Override
      public Void processBackgroundTask() throws WebBrowserException
      {
        try
        {
          WebBrowserLauncher.openURL(url);
          return null;
        } catch (Throwable t)
        {
          throw new WebBrowserException(url,
              ERR_CTRL_PANEL_UNEXPECTED_DETAILS.get(t), t);
        }
      }

      @Override
      public void backgroundTaskCompleted(Void returnValue, Throwable throwable)
      {
        WebBrowserException ex = (WebBrowserException) throwable;
        if (ex != null)
        {
          WebBrowserErrorDialog dlg = new WebBrowserErrorDialog(
              Utilities.getFrame(GenericMenuBar.this), ex);
          Utilities.centerGoldenMean(dlg,
              Utilities.getParentDialog(GenericMenuBar.this));
          dlg.setModal(true);
          dlg.packAndShow();
        }
      }
    };
    worker.startBackgroundTask();
  }
}
