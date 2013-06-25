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
import java.util.LinkedHashSet;
import javax.swing.JLabel;
import javax.swing.JTextField;

import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.server.types.DN;
import org.opends.server.types.OpenDsException;

/**
 * A simple dialog where the user can provide a base DN.
 *
 */
public class BaseDNPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 2742173517231794830L;
  private JTextField dn;
  private JLabel dnLabel;
  private String baseDn;

  /**
   * Default constructor.
   *
   */
  public BaseDNPanel()
  {
    super();
    createLayout();
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return INFO_CTRL_PANEL_OTHER_BASE_DN_TITLE.get();
  }

  /**
   * Returns the base DN chosen by the user.
   * @return the base DN chosen by the user.
   */
  public String getBaseDn()
  {
    return baseDn;
  }

  /**
   * Creates the layout of the panel (but the contents are not populated here).
   */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridx = 0;
    gbc.gridy = 0;

    gbc.weightx = 0.0;
    gbc.gridwidth = 1;
    gbc.fill = GridBagConstraints.NONE;
    dnLabel = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_BASE_DN_LABEL.get());
    add(dnLabel, gbc);
    gbc.insets.left = 10;
    gbc.gridx = 1;
    dn = Utilities.createLongTextField();
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(dn, gbc);

    addBottomGlue(gbc);
  }

  /**
   * {@inheritDoc}
   */
  public Component getPreferredFocusComponent()
  {
    return dn;
  }

  /**
   * {@inheritDoc}
   */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    setPrimaryValid(dnLabel);
    LinkedHashSet<Message> errors = new LinkedHashSet<Message>();

    if ("".equals(dn.getText().trim()))
    {
      errors.add(ERR_CTRL_PANEL_NO_BASE_DN_PROVIDED.get());
    }
    else
    {
      try
      {
        DN.decode(dn.getText());
      }
      catch (OpenDsException ode)
      {
        errors.add(ERR_CTRL_PANEL_INVALID_BASE_DN_PROVIDED.get(
            ode.getMessageObject().toString()));
      }
    }

    if (errors.size() > 0)
    {
      setPrimaryInvalid(dnLabel);
    }
    if (errors.isEmpty())
    {
      baseDn = dn.getText().trim();
      Utilities.getParentDialog(this).setVisible(false);
    }
    else
    {
      displayErrorDialog(errors);
      dn.setSelectionStart(0);
      dn.setSelectionEnd(dn.getText().length());
      dn.requestFocusInWindow();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void cancelClicked()
  {
    setPrimaryValid(dnLabel);
    baseDn = null;
    super.cancelClicked();
  }

  /**
   * {@inheritDoc}
   */
  public void toBeDisplayed(boolean visible)
  {
    super.toBeDisplayed(visible);
    if (visible)
    {
      baseDn = null;
    }
  }
}

