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

package org.opends.guitools.controlpanel.ui.nodes;

import javax.swing.tree.DefaultMutableTreeNode;

import org.forgerock.i18n.LocalizableMessage;

/**
 * The tree node that is used to represent a category.  It is used for instance
 * in the browse index tree to separate categories (index type) from the actual
 * index nodes.
 */
public class CategoryTreeNode extends DefaultMutableTreeNode
{
  private static final long serialVersionUID = -2112887236893130192L;

  /**
   * Constructor.
   * @param name the name of the node (the one that be used to display).
   */
  public CategoryTreeNode(LocalizableMessage name)
  {
    super(name);
  }

  @Override
  public boolean isRoot()
  {
    return false;
  }

  @Override
  public boolean isLeaf()
  {
    return getChildCount() == 0;
  }
}
