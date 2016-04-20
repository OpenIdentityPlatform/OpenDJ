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

import org.forgerock.opendj.ldap.schema.MatchingRule;

/** Class of the nodes that represent a matching rule in the 'Manage Schema' tree. */
public class MatchingRuleTreeNode extends SchemaElementTreeNode
{
  private static final long serialVersionUID = 75800988643731136L;
  private MatchingRule rule;

  /**
   * Constructor of the node.
   * @param name the name of the node.
   * @param rule the matching rule associated with the node.
   */
  public MatchingRuleTreeNode(String name, MatchingRule rule)
  {
    super(name, rule);
    this.rule = rule;
  }

  /**
   * Returns the matching rule definition represented by this node.
   * @return the matching rule definition represented by this node.
   */
  public MatchingRule getMatchingRule()
  {
    return rule;
  }
}
