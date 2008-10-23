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

package org.opends.guitools.controlpanel.browser;

import org.opends.guitools.controlpanel.ui.nodes.BasicNode;

/**
 * This is an abstract class that is extended to search for nodes or
 * to refresh the contents of the nodes.
 */
public abstract class AbstractNodeTask implements Runnable {

  BasicNode node;
  boolean cancelled;

  /**
   * The constructor of the node searcher.
   * @param node the node to be searched/refreshed.
   */
  protected AbstractNodeTask(BasicNode node) {
    this.node = node;
    cancelled = false;
  }


  /**
   * Returns the node that is being searched/refreshed.
   * @return the node that is being searched/refreshed.
   */
  public BasicNode getNode() {
    return node;
  }


  /**
   * Cancels the searching/refreshing process.
   *
   */
  public void cancel() {
    cancelled = true;
  }

  /**
   * Tells whether the search/refresh operation is cancelled.
   * @return <CODE>true</CODE> if the operation is cancelled and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isCancelled() {
    return cancelled;
  }

  /**
   * The method that is called to refresh/search the node.
   */
  public abstract void run();
}
