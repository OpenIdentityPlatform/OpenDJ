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
import org.opends.quicksetup.util.WebBrowserException;
import org.opends.quicksetup.util.WebBrowserLauncher;

/**
 * An abstract class that the different menu bars in the Control Panel extend.
 *
 */

public abstract class GenericMenuBar extends JMenuBar
{
  private ControlPanelInfo info;

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
      public void actionPerformed(ActionEvent ev)
      {
        displayURL("https://www.opends.org/wiki/page/AdministrationGuide");
      }
    });
    menu.add(menuItem);
    menuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_DOCUMENTATION_WIKI_MENU.get());
    menuItem.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        displayURL("https://www.opends.org/wiki/page/Main");
      }
    });
    menu.add(menuItem);
    return menu;
  }

  /**
   * Tries to display a URL in the systems default WEB browser.
   * @param url the URL to be displayed.
   */
  protected void displayURL(final String url)
  {
    BackgroundTask<Void> worker = new BackgroundTask<Void>()
    {
      /**
       * {@inheritDoc}
       */
      public Void processBackgroundTask() throws WebBrowserException
      {
        try
        {
          WebBrowserLauncher.openURL(url);
        } catch (Throwable t)
        {
          throw new WebBrowserException(url,
              ERR_CTRL_PANEL_UNEXPECTED_DETAILS.get(t.toString()), t);
        }
        return null;
      }

      /**
       * {@inheritDoc}
       */
      public void backgroundTaskCompleted(Void returnValue,
        Throwable throwable)
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
