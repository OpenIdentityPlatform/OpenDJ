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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui.nodes;

import javax.swing.tree.DefaultMutableTreeNode;

/** Abstract class with some common methods for all the nodes in the 'General Information' tree. */
public class GeneralMonitoringTreeNode extends DefaultMutableTreeNode
{
  private static final long serialVersionUID = 7896765876669863639L;
  private String displayName;
  private Object identifier;
  private boolean isRoot;

  /**
   * Constructor of the node.
   * @param displayName the name of the node.
   * @param identifier the identifier that is unique among all the nodes.
   * @param isRoot whether the node is the root or not.
   */
  public GeneralMonitoringTreeNode(String displayName,
      Object identifier,
      boolean isRoot)
  {
    super(displayName);
    this.displayName = displayName;
    this.identifier = identifier;
    this.isRoot = isRoot;
  }

  /**
   * Returns the name of the node.
   * @return the name of the node.
   */
  public String getDisplayName()
  {
    return displayName;
  }

  /**
   * Returns the identifier that is unique among all the nodes.
   * @return the identifier that is unique among all the nodes.
   */
  public Object getIdentifier()
  {
    return identifier;
  }

  @Override
  public boolean isRoot()
  {
    return isRoot;
  }

  @Override
  public boolean isLeaf()
  {
    return getChildCount() == 0;
  }
}
