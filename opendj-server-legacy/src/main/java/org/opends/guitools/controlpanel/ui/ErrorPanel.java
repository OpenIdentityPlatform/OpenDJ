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

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.util.Collection;

import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.util.Utilities;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;

/** Class used to display an collection of error messages. */
public class ErrorPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = -4494826284037288552L;
  private LocalizableMessage title;
  /**
   * Constructor.
   * @param title the title to be displayed in the dialog.
   * @param errors the collection of errors to be displayed.
   */
  public ErrorPanel(LocalizableMessage title, Collection<LocalizableMessage> errors)
  {
    super();
    this.title = title;
    createLayout(errors);
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return title;
  }

  private void createLayout(Collection<LocalizableMessage> errors)
  {
    GridBagConstraints gbc = new GridBagConstraints();
    addErrorPane(gbc);

    errorPane.setVisible(true);

    LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
    for (LocalizableMessage error : errors)
    {
      if (mb.length() > 0)
      {
        mb.append("<br>");
      }
      mb.append(error);
    }

    updateErrorPane(errorPane, title, ColorAndFontConstants.errorTitleFont,
        mb.toMessage(), ColorAndFontConstants.defaultFont);

    gbc.weighty = 0.0;
    addBottomGlue(gbc);
  }

  @Override
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.OK;
  }

  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return null;
  }

  @Override
  public void okClicked()
  {
    Utilities.getParentDialog(this).setVisible(false);
  }
}
