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

/** The border of the CategoryButton when is selected. */
public class SelectedCategoryBorder implements Border
{
  private Insets insets = new Insets(5, 5, 6, 5);

  /** Default constructor. */
  public SelectedCategoryBorder() {
  }

  @Override
  public Insets getBorderInsets(Component c)
  {
    return insets;
  }

  @Override
  public boolean isBorderOpaque()
  {
    return true;
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height)
  {
    // render shadow on bottom
    g.setColor(ColorAndFontConstants.defaultBorderColor);
    g.drawLine(x, y + height - 1, x + width - 1, y + height - 1);
  }
}
