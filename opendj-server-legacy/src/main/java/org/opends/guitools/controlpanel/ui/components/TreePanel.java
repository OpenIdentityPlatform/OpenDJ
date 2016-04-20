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

package org.opends.guitools.controlpanel.ui.components;

import java.awt.Component;
import java.awt.GridBagConstraints;

import javax.swing.JTree;
import javax.swing.tree.TreeSelectionModel;

import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.GenericDialog;
import org.opends.guitools.controlpanel.ui.StatusGenericPanel;
import org.opends.guitools.controlpanel.ui.renderer.TreeCellRenderer;
import org.forgerock.i18n.LocalizableMessage;

/** A basic panel containing a CustomTree. */
public class TreePanel extends StatusGenericPanel
{
  private static final long serialVersionUID = 5650902943430126109L;
  private JTree tree;

  /** Default constructor. */
  public TreePanel()
  {
    super();
    createLayout();
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  private void createLayout()
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;

    tree = new CustomTree();
    tree.getSelectionModel().setSelectionMode(
        TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    tree.setBackground(ColorAndFontConstants.background);
    tree.setCellRenderer(new TreeCellRenderer());
    tree.setShowsRootHandles(true);
    tree.setScrollsOnExpand(false);
    add(tree, gbc);
  }

  /**
   * Returns the tree contained in the panel.
   * @return the tree contained in the panel.
   */
  public JTree getTree()
  {
    return tree;
  }

  @Override
  public void okClicked()
  {
    // No ok button
  }

  @Override
  public GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.NO_BUTTON;
  }

  @Override
  public LocalizableMessage getTitle()
  {
    return null;
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return tree;
  }

  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
  }
}
