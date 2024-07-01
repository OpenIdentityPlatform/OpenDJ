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
 * Portions Copyright 2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.ui.nodes;

import org.forgerock.opendj.ldap.schema.ObjectClass;

/** Class of the nodes that represent a custom object class in the 'Manage Schema' tree. */
public class CustomObjectClassTreeNode extends SchemaElementTreeNode
{
  private static final long serialVersionUID = -3525236184507876077L;
  private ObjectClass oc;

  /**
   * Constructor of the node.
   * @param name the name of the node.
   * @param oc the object class definition.
   */
  public CustomObjectClassTreeNode(String name, ObjectClass oc)
  {
    super(name, oc);
    this.oc = oc;
  }

  /**
   * Returns the definition of the object class represented by this node.
   * @return the definition of the object class represented by this node.
   */
  public ObjectClass getObjectClass()
  {
    return oc;
  }
}
