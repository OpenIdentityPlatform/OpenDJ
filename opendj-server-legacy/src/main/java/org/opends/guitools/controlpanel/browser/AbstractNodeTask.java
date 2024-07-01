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
 * Portions Copyright 2016 ForgeRock AS.
 */

package org.opends.guitools.controlpanel.browser;

import org.opends.guitools.controlpanel.ui.nodes.BasicNode;

/**
 * This is an abstract class that is extended to search for nodes or
 * to refresh the contents of the nodes.
 */
public abstract class AbstractNodeTask implements Runnable {

  private final BasicNode node;
  private boolean cancelled;

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


  /** Cancels the searching/refreshing process. */
  public void cancel() {
    cancelled = true;
  }

  /**
   * Tells whether the search/refresh operation is cancelled.
   * @return <CODE>true</CODE> if the operation is cancelled and
   * <CODE>false</CODE> otherwise.
   */
  public boolean isCanceled() {
    return cancelled;
  }

  /** The method that is called to refresh/search the node. */
  @Override
  public abstract void run();
}
