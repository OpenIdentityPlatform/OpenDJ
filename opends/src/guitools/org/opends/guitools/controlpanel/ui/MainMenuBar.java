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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;

/**
 * The menu bar that appears on the main panel.
 *
 */
public class MainMenuBar extends GenericMenuBar
{
  private static final long serialVersionUID = 6441273044772077947L;

  private GenericDialog dlg;
  private RefreshOptionsPanel panel;

  /**
   * Constructor.
   * @param info the control panel information.
   */
  public MainMenuBar(ControlPanelInfo info)
  {
    super(info);

    addMenus();

    if (Utilities.isMacOS())
    {
      setMacOSQuitHandler();
    }
  }

  /**
   * Method that can be overwritten to set specific menus.
   *
   */
  protected void addMenus()
  {
    JMenu menu;
    JMenuItem menuItem;

    if (!Utilities.isMacOS())
    {
      menu = Utilities.createMenu(INFO_CTRL_PANEL_FILE_MENU.get(),
          INFO_CTRL_PANEL_FILE_MENU_DESCRIPTION.get());
      menu.setMnemonic(KeyEvent.VK_F);
      menuItem = Utilities.createMenuItem(INFO_CTRL_PANEL_EXIT_MENU.get());
      menuItem.addActionListener(new ActionListener()
      {
        /**
         * {@inheritDoc}
         */
        public void actionPerformed(ActionEvent ev)
        {
          quitClicked();
        }
      });
      menu.add(menuItem);

      add(menu);
    }
    add(createViewMenuBar());
    add(createHelpMenuBar());
  }

  /**
   * The method called when the user clicks on quick.  It will check that there
   * are not ongoing tasks.  If there are tasks, it will ask the user for
   * confirmation to quit.
   *
   */
  public void quitClicked()
  {
    Set<String> runningTasks = new HashSet<String>();
    for (Task task : getInfo().getTasks())
    {
      if (task.getState() == Task.State.RUNNING)
      {
        runningTasks.add(task.getTaskDescription().toString());
      }
    }
    boolean confirmed = true;
    if (runningTasks.size() > 0)
    {
      String allTasks = Utilities.getStringFromCollection(runningTasks, "<br>");
      Message title = INFO_CTRL_PANEL_CONFIRMATION_REQUIRED_SUMMARY.get();
      Message msg =
        INFO_CTRL_PANEL_RUNNING_TASKS_CONFIRMATION_DETAILS.get(allTasks);
      confirmed = Utilities.displayConfirmationDialog(
          Utilities.getParentDialog(this), title, msg);
    }
    if (confirmed)
    {
      System.exit(0);
    }
  }



  /**
   * Creates the View menu bar.
   * @return the View menu bar.
   */
  protected JMenu createViewMenuBar()
  {
    JMenu menu = Utilities.createMenu(INFO_CTRL_PANEL_VIEW_MENU.get(),
        INFO_CTRL_PANEL_HELP_VIEW_DESCRIPTION.get());
    menu.setMnemonic(KeyEvent.VK_V);
    JMenuItem menuItem = Utilities.createMenuItem(
        INFO_CTRL_PANEL_REFRESH_MENU.get());
    menuItem.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent ev)
      {
        refreshOptionsClicked();
      }
    });
    menu.add(menuItem);
    return menu;
  }

  /**
   * Specific method to be able to handle the Quit events sent from the COCOA
   * menu of Mac OS.
   *
   */
  private void setMacOSQuitHandler()
  {
    try
    {
      Class<? extends Object> applicationClass =
        Class.forName("com.apple.eawt.Application");
      Class applicationListenerClass =
        Class.forName("com.apple.eawt.ApplicationListener");
      final Object  macApplication = applicationClass.getConstructor(
          (Class[])null).newInstance((Object[])null);
      InvocationHandler adapter = new InvocationHandler()
      {
        public Object invoke (Object proxy, Method method, Object[] args)
        throws Throwable
        {
          Object event = args[0];
          if (method.getName().equals("handleQuit"))
          {
            quitClicked();

            // quitClicked will exit if we must exit
            Method setHandledMethod = event.getClass().getDeclaredMethod(
                "setHandled", new Class[] { boolean.class });
            setHandledMethod.invoke(event, new Object[] { Boolean.FALSE });
          }
          return null;
        }
      };
      Method addListenerMethod =
        applicationClass.getDeclaredMethod("addApplicationListener",
            new Class[] { applicationListenerClass });
      Object proxy = Proxy.newProxyInstance(MainMenuBar.class.getClassLoader(),
          new Class[] { applicationListenerClass }, adapter);
      addListenerMethod.invoke(macApplication, new Object[] { proxy });
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  /**
   * The method called when the user clicks on 'Refresh Options'.
   *
   */
  protected void refreshOptionsClicked()
  {
    if (panel == null)
    {
      panel = new RefreshOptionsPanel();
      panel.setInfo(getInfo());
      dlg = new GenericDialog(
          Utilities.getFrame(MainMenuBar.this),
          panel);
      dlg.setModal(true);
      Utilities.centerGoldenMean(dlg,
          Utilities.getFrame(MainMenuBar.this));
    }
    dlg.setVisible(true);
    if (!panel.isCancelled())
    {
      getInfo().setPoolingPeriod(panel.getPoolingPeriod());
      getInfo().stopPooling();
      getInfo().startPooling();
    }
  }
}
