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
 *      Copyright 2008-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2015 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.ui.nodes;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * Abstract class with some common methods for all the nodes in the
 * 'Manage Schema' tree.
 *
 */
public abstract class SchemaElementTreeNode extends DefaultMutableTreeNode
{
  private static final long serialVersionUID = 5832209952457633471L;
  private String name;
  private Object schemaElement;

  /**
   * Constructor of the node.
   * @param name the name of the node.
   * @param schemaElement the schema element (attribute definition, object class
   * definition, etc.) associated with the node.
   */
  protected SchemaElementTreeNode(String name, Object schemaElement)
  {
    super(name);
    this.name = name;
    this.schemaElement = schemaElement;
  }

  /**
   * Returns the name of the node.
   * @return the name of the node.
   */
  public String getName()
  {
    return name;
  }

  /**
   * Returns the schema element associated with the node.
   * @return the schema element associated with the node.
   */
  public Object getSchemaElement()
  {
    return schemaElement;
  }

  /** {@inheritDoc} */
  public boolean isRoot()
  {
    return false;
  }

  /** {@inheritDoc} */
  public boolean isLeaf()
  {
    return true;
  }
}
