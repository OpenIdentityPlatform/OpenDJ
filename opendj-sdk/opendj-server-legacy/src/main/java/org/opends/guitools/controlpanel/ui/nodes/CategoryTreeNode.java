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

package org.opends.guitools.controlpanel.ui.nodes;

import javax.swing.tree.DefaultMutableTreeNode;

import org.forgerock.i18n.LocalizableMessage;

/**
 * The tree node that is used to represent a category.  It is used for instance
 * in the browse index tree to separate categories (index type) from the actual
 * index nodes.
 *
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

  /** {@inheritDoc} */
  public boolean isRoot()
  {
    return false;
  }

  /** {@inheritDoc} */
  public boolean isLeaf()
  {
    return getChildCount() == 0;
  }
}
