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
 *      Copyright 2009 Sun Microsystems, Inc.
 */
package org.opends.guitools.controlpanel.ui.nodes;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Abstract class with some common methods for all the nodes in the
 * 'General Information' tree.
 *
 */
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

  /**
   * {@inheritDoc}
   */
  public boolean isRoot()
  {
    return isRoot;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isLeaf()
  {
    return getChildCount() == 0;
  }
}
