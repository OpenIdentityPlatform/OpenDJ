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

package org.opends.guitools.controlpanel.ui.border;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.border.Border;

import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;

/**
 * The border of the CategoryButton when is selected.
 *
 */
public class SelectedCategoryBorder implements Border
{
  private Insets insets = new Insets(5, 5, 6, 5);

  /**
   * Default constructor.
   *
   */
  public SelectedCategoryBorder() {
  }

  /**
   * {@inheritDoc}
   */
  public Insets getBorderInsets(Component c)
  {
    return insets;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isBorderOpaque()
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public void paintBorder(Component c, Graphics g, int x, int y, int width,
      int height)
  {
    // render shadow on bottom
    g.setColor(ColorAndFontConstants.defaultBorderColor);
    g.drawLine(x, y + height - 1, x + width - 1, y + height - 1);
  }
}
