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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2015-2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.ui.nodes;

import javax.swing.tree.DefaultMutableTreeNode;

/** Abstract class with some common methods for all the nodes in the 'Manage Schema' tree. */
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

  @Override
  public boolean isRoot()
  {
    return false;
  }

  @Override
  public boolean isLeaf()
  {
    return true;
  }
}
