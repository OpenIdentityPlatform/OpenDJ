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

package org.opends.guitools.controlpanel.ui.nodes;

import org.opends.server.api.AttributeSyntax;

/**
 * Class of the nodes that represent an attribute syntax in the 'Manage Schema'
 * tree.
 *
 */
public class AttributeSyntaxTreeNode extends SchemaElementTreeNode
{
  private static final long serialVersionUID = 2439971368723239776L;
  private AttributeSyntax syntax;

  /**
   * Constructor of the node.
   * @param name the name of the node.
   * @param syntax the attribute syntax.
   */
  public AttributeSyntaxTreeNode(String name, AttributeSyntax syntax)
  {
    super(name, syntax);
    this.syntax = syntax;
  }

  /**
   * Returns the attribute syntax represented by this node.
   * @return the attribute syntax represented by this node.
   */
  public AttributeSyntax getAttributeSyntax()
  {
    return syntax;
  }
}
