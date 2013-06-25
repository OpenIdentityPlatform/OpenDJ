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

import org.opends.server.types.AttributeType;

/**
 * Class of the nodes that represent a configuration attribute in the 'Manage
 * Schema' tree.
 *
 */
public class ConfigurationAttributeTreeNode extends SchemaElementTreeNode
{
  private static final long serialVersionUID = 428949987639862400L;
  private AttributeType attr;

  /**
   * Constructor of the node.
   * @param name the name of the node.
   * @param attr the attribute definition.
   */
  public ConfigurationAttributeTreeNode(String name, AttributeType attr)
  {
    super(name, attr);
    this.attr = attr;
  }

  /**
   * Returns the definition of the attribute represented by this node.
   * @return the definition of the attribute represented by this node.
   */
  public AttributeType getAttribute()
  {
    return attr;
  }
}
