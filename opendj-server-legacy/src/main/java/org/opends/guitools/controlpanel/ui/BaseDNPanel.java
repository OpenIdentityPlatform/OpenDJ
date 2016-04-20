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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
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
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.opendj.ldap.DN;

/** A simple dialog where the user can provide a base DN. */
public class BaseDNPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 2742173517231794830L;
  private JTextField dn;
  private JLabel dnLabel;
  private String baseDn;

  /** Default constructor. */
  public BaseDNPanel()
  {
    super();
    createLayout();
  }

  @Override
  public LocalizableMessage getTitle()
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

  /** Creates the layout of the panel (but the contents are not populated here). */
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

  @Override
  public Component getPreferredFocusComponent()
  {
    return dn;
  }

  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  @Override
  public void okClicked()
  {
    setPrimaryValid(dnLabel);
    LinkedHashSet<LocalizableMessage> errors = new LinkedHashSet<>();

    if ("".equals(dn.getText().trim()))
    {
      errors.add(ERR_CTRL_PANEL_NO_BASE_DN_PROVIDED.get());
    }
    else
    {
      try
      {
        DN.valueOf(dn.getText());
      }
      catch (LocalizedIllegalArgumentException e)
      {
        errors.add(ERR_CTRL_PANEL_INVALID_BASE_DN_PROVIDED.get(e.getMessageObject()));
      }
    }

    if (errors.isEmpty())
    {
      baseDn = dn.getText().trim();
      Utilities.getParentDialog(this).setVisible(false);
    }
    else
    {
      setPrimaryInvalid(dnLabel);
      displayErrorDialog(errors);
      dn.setSelectionStart(0);
      dn.setSelectionEnd(dn.getText().length());
      dn.requestFocusInWindow();
    }
  }

  @Override
  public void cancelClicked()
  {
    setPrimaryValid(dnLabel);
    baseDn = null;
    super.cancelClicked();
  }

  @Override
  public void toBeDisplayed(boolean visible)
  {
    super.toBeDisplayed(visible);
    if (visible)
    {
      baseDn = null;
    }
  }
}

