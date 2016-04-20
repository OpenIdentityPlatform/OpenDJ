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

import org.forgerock.opendj.ldap.schema.Syntax;


/** Class of the nodes that represent an attribute syntax in the 'Manage Schema' tree. */
public class AttributeSyntaxTreeNode extends SchemaElementTreeNode
{
  private static final long serialVersionUID = 2439971368723239776L;
  private Syntax syntax;

  /**
   * Constructor of the node.
   * @param name the name of the node.
   * @param syntax the attribute syntax.
   */
  public AttributeSyntaxTreeNode(String name, Syntax syntax)
  {
    super(name, syntax);
    this.syntax = syntax;
  }

  /**
   * Returns the attribute syntax represented by this node.
   * @return the attribute syntax represented by this node.
   */
  public Syntax getAttributeSyntax()
  {
    return syntax;
  }
}
