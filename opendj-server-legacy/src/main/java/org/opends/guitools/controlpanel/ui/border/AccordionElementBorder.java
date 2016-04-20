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
 * Portions Copyright 2015-2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.ui.border;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.border.Border;

import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;

/** The border specific to the accordion element. */
public class AccordionElementBorder implements Border
{
  private Insets insets = new Insets(1, 1, 1, 1);

  /** Default constructor. */
  public AccordionElementBorder() {
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return insets;
  }

  @Override
  public boolean isBorderOpaque() {
    return true;
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width,
      int height) {
    g.setColor(ColorAndFontConstants.topAccordionBorderColor);
    // render highlight at top
    g.drawLine(x, y, x + width - 1, y);
    // render left
    g.drawLine(x, y, x, y + height - 1);
    // render right
    g.drawLine(x + width - 1, y, x + width - 1, y + height - 1);
    // render shadow on bottom
    g.setColor(ColorAndFontConstants.defaultBorderColor);
    g.drawLine(x, y + height - 1, x + width - 1, y + height - 1);
  }
}
