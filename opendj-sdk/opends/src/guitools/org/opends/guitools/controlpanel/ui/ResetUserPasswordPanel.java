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

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.util.ArrayList;

import javax.swing.JLabel;
import javax.swing.JPasswordField;

import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.task.ResetUserPasswordTask;
import org.opends.guitools.controlpanel.task.Task;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;

/**
 * Panel that appears when the user wants to change the password of a user.
 *
 */
public class ResetUserPasswordPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 8733172823605832626L;
  private JLabel dn = Utilities.createDefaultLabel();
  private JLabel name = Utilities.createDefaultLabel();
  private JLabel lPassword = Utilities.createPrimaryLabel();
  private JLabel lConfirmPassword = Utilities.createPrimaryLabel();
  private JPasswordField password = Utilities.createPasswordField(25);
  private JPasswordField confirmPassword = Utilities.createPasswordField(25);

  private BasicNode node;
  private BrowserController controller;

  /**
   * Constructor of the panel.
   *
   */
  public ResetUserPasswordPanel()
  {
    super();
    createLayout();
  }

  /**
   * Sets the node representing the entry that the user wants to modify the
   * password from.
   * @param node the node.
   * @param controller the browser controller.
   */
  public void setValue(BasicNode node, BrowserController controller)
  {
    this.node = node;
    this.controller = controller;
    setPrimaryValid(lPassword);
    setPrimaryValid(lConfirmPassword);

    dn.setText(node.getDN());
    name.setText(node.getDisplayName());

    password.setText("");
    confirmPassword.setText("");

    packParentDialog();
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return password;
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    final ArrayList<Message> errors = new ArrayList<Message>();

    setPrimaryValid(lPassword);
    setPrimaryValid(lConfirmPassword);

    String pwd1 = new String(password.getPassword());
    String pwd2 = new String(confirmPassword.getPassword());

    if (pwd1.length() == 0)
    {
      errors.add(ERR_CTRL_PANEL_NEW_PASSWORD_REQUIRED.get());
      setPrimaryInvalid(lPassword);
    }
    else if (!pwd1.equals(pwd2))
    {
      errors.add(ERR_CTRL_PANEL_PASSWORD_DO_NOT_MATCH.get());
      setPrimaryInvalid(lPassword);
      setPrimaryInvalid(lConfirmPassword);
    }
    if (errors.size() == 0)
    {
      ProgressDialog dlg = new ProgressDialog(
          Utilities.getParentDialog(this),
          INFO_CTRL_PANEL_RESET_USER_PASSWORD_TITLE.get(),
          getInfo());
      ResetUserPasswordTask newTask =
        new ResetUserPasswordTask(getInfo(), dlg, node, controller,
            password.getPassword());
      for (Task task : getInfo().getTasks())
      {
        task.canLaunch(newTask, errors);
      }
      if (errors.size() == 0)
      {
        launchOperation(newTask,
            INFO_CTRL_PANEL_RESETTING_USER_PASSWORD_SUMMARY.get(),
            INFO_CTRL_PANEL_RESETTING_USER_PASSWORD_SUCCESSFUL_SUMMARY.get(),
            INFO_CTRL_PANEL_RESETTING_USER_PASSWORD_SUCCESSFUL_DETAILS.get(),
            ERR_CTRL_PANEL_RESETTING_USER_PASSWORD_ERROR_SUMMARY.get(),
            ERR_CTRL_PANEL_RESETTING_USER_PASSWORD_ERROR_DETAILS.get(),
            null,
            dlg);
        Utilities.getParentDialog(this).setVisible(false);
        dlg.setVisible(true);
      }
    }
    if (errors.size() > 0)
    {
      displayErrorDialog(errors);
    }
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_RESET_USER_PASSWORD_TITLE.get();
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 0.0;
    gbc.weighty = 0.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;

    Message[] strings =
    {
        INFO_CTRL_PANEL_RESET_USER_PASSWORD_DN_LABEL.get(),
        INFO_CTRL_PANEL_RESET_USER_PASSWORD_NAME_LABEL.get(),
        INFO_CTRL_PANEL_RESET_USER_PASSWORD_PWD_LABEL.get(),
        INFO_CTRL_PANEL_RESET_USER_PASSWORD_CONFIRM_LABEL.get()
    };
    JLabel[] labels = {null, null, lPassword, lConfirmPassword};
    Component[] comps = {dn, name, password, confirmPassword};

    for (int i=0; i<strings.length; i++)
    {
      if (labels[i] == null)
      {
        labels[i] = Utilities.createPrimaryLabel(strings[i]);
      }
      else
      {
        labels[i].setText(strings[i].toString());
      }

      gbc.gridx = 0;
      gbc.insets.left = 0;
      gbc.weightx = 0.0;
      add(labels[i], gbc);
      gbc.insets.left = 10;
      gbc.gridx ++;
      gbc.weightx = 1.0;
      add(comps[i], gbc);

      gbc.insets.top = 10;
      gbc.gridy ++;
    }

    addBottomGlue(gbc);
  }
}
