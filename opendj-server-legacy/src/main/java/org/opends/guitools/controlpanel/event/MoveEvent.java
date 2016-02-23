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
 */

package org.opends.guitools.controlpanel.event;

import org.opends.guitools.controlpanel.ui.nodes.BrowserNodeInfo;

/**
 * The event that is throw when an entry is moved in the LDAP
 * entry browser.  For the time being it is not used but it can be used in the
 * future when the move of the entries is implemented.
 *
 */
public class MoveEvent
{
  private BrowserNodeInfo newParent;
  private BrowserNodeInfo[] nodes;

  /**
   * The constructor of the move event.
   * @param newParent the new parent of the nodes that are being moved.
   * @param nodes the nodes that are being moved.
   */
  public MoveEvent(BrowserNodeInfo newParent, BrowserNodeInfo[] nodes) {
    this.newParent = newParent;
    this.nodes = nodes;
  }

  /**
   * Return the new parent of the nodes that are being moved.
   * @return the new parent of the nodes that are being moved.
   */
  public BrowserNodeInfo getNewParent() {
    return newParent;
  }

  /**
   * Return the nodes that are being moved.
   * @return the nodes that are being moved.
   */
  public BrowserNodeInfo[] getNodes() {
    return nodes;
  }
}
