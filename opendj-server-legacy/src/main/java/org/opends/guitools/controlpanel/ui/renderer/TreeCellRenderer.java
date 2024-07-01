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

package org.opends.guitools.controlpanel.ui.renderer;

import java.awt.Color;
import java.awt.Component;

import javax.swing.BorderFactory;
import javax.swing.JTree;
import javax.swing.border.Border;
import javax.swing.tree.DefaultTreeCellRenderer;

import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;

/**
 * An extension of the DefaultTreeCellRenderer that uses a customized border,
 * foreground and background.
 */
public class TreeCellRenderer extends DefaultTreeCellRenderer
{
  private static final long serialVersionUID = 4045260951231311206L;

  /** Background when the cell is not selected. */
  public static final Color nonselectionBackground =
    ColorAndFontConstants.background;
  private static final Color nonselectionForeground =
    ColorAndFontConstants.foreground;

  /** Background when the cell is selected. */
  public static final Color selectionBackground =
    ColorAndFontConstants.mouseOverBackground;

  private static final Color selectionForeground =
    ColorAndFontConstants.mouseOverForeground;


  private Border rootBorder = BorderFactory.createEmptyBorder(0, 5, 0, 0);
  private Border normalBorder = BorderFactory.createEmptyBorder(0, 0, 0, 0);

  /** Constructor of the renderer. */
  public TreeCellRenderer()
  {
    backgroundNonSelectionColor = nonselectionBackground;
    backgroundSelectionColor = selectionBackground;
    textNonSelectionColor = nonselectionForeground;
    textSelectionColor = selectionForeground;
    setFont(ColorAndFontConstants.treeFont);
  }

  @Override
  public Component getTreeCellRendererComponent(JTree tree, Object value,
      boolean isSelected, boolean isExpanded, boolean isLeaf, int row,
      boolean hasFocus)
  {
    super.getTreeCellRendererComponent(tree, value, isSelected, isExpanded,
        isLeaf, row, hasFocus);
    setIcon(null);

    if (row == 0 && tree.isRootVisible())
    {
      setBorder(rootBorder);
    }
    else
    {
      setBorder(normalBorder);
    }
    return this;
  }
}
