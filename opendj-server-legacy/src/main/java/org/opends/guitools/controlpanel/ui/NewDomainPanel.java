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

import java.util.ArrayList;

import javax.swing.JLabel;
import javax.swing.JTextField;

import org.forgerock.i18n.LocalizableMessage;

/** The panel to create a domain. */
public class NewDomainPanel extends NewOrganizationPanel
{
  private static final long serialVersionUID = -595396547491445219L;

  @Override
  public LocalizableMessage getTitle()
  {
    return INFO_CTRL_NEW_DOMAIN_PANEL_TITLE.get();
  }

  @Override
  protected LocalizableMessage getProgressDialogTitle()
  {
    return INFO_CTRL_NEW_DOMAIN_PANEL_TITLE.get();
  }

  @Override
  protected void checkSyntax(ArrayList<LocalizableMessage> errors)
  {
    for (JLabel label : labels)
    {
      setPrimaryValid(label);
    }

    JTextField[] requiredFields = {name};
    LocalizableMessage[] msgs = {ERR_CTRL_PANEL_NAME_OF_DOMAIN_REQUIRED.get()};
    for (int i=0; i<requiredFields.length; i++)
    {
      String v = requiredFields[i].getText().trim();
      if (v.length() == 0)
      {
        errors.add(msgs[i]);
      }
    }
  }

  @Override
  protected void updateDNValue()
  {
    String value = name.getText().trim();
    if (value.length() > 0)
    {
      dn.setText("dc" + "=" + value + "," + parentNode.getDN());
    }
    else
    {
      dn.setText(","+parentNode.getDN());
    }
  }

  @Override
  protected String getLDIF()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("dn: ").append(dn.getText()).append("\n");
    String[] attrNames = {"dc", "description"};
    JTextField[] textFields = {name, description};
    sb.append("objectclass: top\n");
    sb.append("objectclass: domain\n");
    for (int i=0; i<attrNames.length; i++)
    {
      String value = textFields[i].getText().trim();
      if (value.length() > 0)
      {
        sb.append(attrNames[i]).append(": ").append(value).append("\n");
      }
    }
    return sb.toString();
  }
}
