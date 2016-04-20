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

import org.opends.guitools.controlpanel.datamodel.VLVIndexDescriptor;

/** A node representing a VLV index. It is used in the 'Manage Index' tree. */
public class VLVIndexTreeNode extends AbstractIndexTreeNode
{
  private static final long serialVersionUID = -4067198828465569689L;
  private VLVIndexDescriptor index;

  /**
   * Constructor of the node.
   * @param name the name of the node.
   * @param index the VLV index associated with the node.
   */
  public VLVIndexTreeNode(String name, VLVIndexDescriptor index)
  {
    super(name);
    this.index = index;
  }

  /**
   * Returns the VLV index descriptor associated with the node.
   * @return the VLV index descriptor associated with the node.
   */
  public VLVIndexDescriptor getIndex()
  {
    return index;
  }
}
