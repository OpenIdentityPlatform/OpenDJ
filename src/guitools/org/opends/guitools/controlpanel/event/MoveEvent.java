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
