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

package org.opends.guitools.controlpanel.ui.renderer;

import java.awt.Component;
import java.awt.Font;

import javax.swing.JTree;

import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;

/**
 * The renderer used to render the nodes in the LDAP entry browser.
 *
 */
public class BrowserCellRenderer extends TreeCellRenderer {

  private static final long serialVersionUID = 6756291700611741513L;
  Font defaultFont = ColorAndFontConstants.treeFont;
  Font italicFont = defaultFont.deriveFont(Font.ITALIC);
  Font boldFont = defaultFont.deriveFont(Font.BOLD);
  Font italicBoldFont = defaultFont.deriveFont(Font.ITALIC|Font.BOLD);
  BasicNode inspectedNode;

  /**
   * Sets which is the inspected node.  This method simply marks the selected
   * node in the tree so that it can have a different rendering.  This is
   * useful for instance when the right panel has a list of entries to which
   * the menu action apply, to make a difference between the selected node in
   * the tree (to which the action in the main menu will not apply) and the
   * selected nodes in the right pane.
   * @param node the selected node.
   */
  public void setInspectedNode(BasicNode node) {
    inspectedNode = node;
  }

  /**
   * {@inheritDoc}
   */
  public Component getTreeCellRendererComponent(
      JTree tree,
      Object value,
      boolean isSelected,
    boolean isExpanded,
    boolean isLeaf,
    int row,
      boolean cellHasFocus)
  {
    BasicNode node = (BasicNode)value;
    super.getTreeCellRendererComponent(tree, node, isSelected,
        isExpanded, isLeaf,
        row, cellHasFocus);

    setIcon(node.getIcon());
    setText(node.getDisplayName());

    Font newFont = defaultFont;
    int style = node.getFontStyle();
    if (node == inspectedNode) {
      style |= Font.BOLD;
    }
    if ((style & Font.ITALIC & Font.BOLD) != 0) {
      newFont = italicBoldFont;
    }
    else if ((style & Font.ITALIC) != 0) {
      newFont = italicFont;
    }
    else if ((style & Font.BOLD) != 0) {
      newFont = boldFont;
    }
    else {
      newFont = defaultFont;
    }
    setFont(newFont);
    return this;
  }


  /**
   * Returns the row height for the provided browser controller.
   * @param controller the browser controller.
   * @return the row height for the provided browser controller.
   */
  public static int calculateRowHeight(BrowserController controller) {
    return 16;
  }
}
