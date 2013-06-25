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

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.util.Collection;

import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

/**
 * Class used to display an collection of error messages.
 *
 */
public class ErrorPanel extends StatusGenericPanel
{
  private static final long serialVersionUID = -4494826284037288552L;
  private Message title;
  /**
   * Constructor.
   * @param title the title to be displayed in the dialog.
   * @param errors the collection of errors to be displayed.
   */
  public ErrorPanel(Message title, Collection<Message> errors)
  {
    super();
    this.title = title;
    createLayout(errors);
  }

  /**
   * {@inheritDoc}
   */
  public Message getTitle()
  {
    return title;
  }

  private void createLayout(Collection<Message> errors)
  {
    GridBagConstraints gbc = new GridBagConstraints();
    addErrorPane(gbc);

    errorPane.setVisible(true);

    MessageBuilder mb = new MessageBuilder();
    for (Message error : errors)
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

  /**
   * {@inheritDoc}
   */
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.OK;
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
  public Component getPreferredFocusComponent()
  {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public void okClicked()
  {
    Utilities.getParentDialog(this).setVisible(false);
  }
}
