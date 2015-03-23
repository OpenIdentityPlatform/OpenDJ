/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */

package org.opends.guitools.controlpanel.ui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.util.Collection;

import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.util.Utilities;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;

/**
 * Class used to display an collection of error messages.
 *
 */
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

  /** {@inheritDoc} */
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

  /** {@inheritDoc} */
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.OK;
  }

  /** {@inheritDoc} */
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }

  /** {@inheritDoc} */
  public Component getPreferredFocusComponent()
  {
    return null;
  }

  /** {@inheritDoc} */
  public void okClicked()
  {
    Utilities.getParentDialog(this).setVisible(false);
  }
}
