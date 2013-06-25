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

import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

/**
 * An implementation of Transferable used in the LDAP entry browser to use
 * drag and drop.  Currently drag and drop is used for instance to drag a
 * number of entries from a browser and drop them in the list of members of
 * a group.
 *
 */

public class DndBrowserNodes implements Transferable {
  /**
   * The data flavor managed by this transferable.
   */
  final public static DataFlavor INFO_FLAVOR =
    new DataFlavor(BrowserNodeInfo.class, "Browse Node Information");

  static DataFlavor[] FLAVORS = {INFO_FLAVOR };

  private BrowserNodeInfo[] nodes;

  private Component parent; // The component that contains the nodes

  /**
   * Transferable implementation
   * ============================================
   */

  /**
   * {@inheritDoc}
   */
  public boolean isDataFlavorSupported(DataFlavor df) {
    return df.equals(INFO_FLAVOR);
  }

  /**
   * {@inheritDoc}
   */
  public Object getTransferData(DataFlavor df)
  throws UnsupportedFlavorException, IOException {
    if (df.equals(INFO_FLAVOR)) {
      return this;
    }
    else throw new UnsupportedFlavorException(df);
  }

  /**
   * {@inheritDoc}
   */
  public DataFlavor[] getTransferDataFlavors() {
    return FLAVORS;
  }

  /**
   * Returns the nodes that are being dragged (and dropped).
   * @return the nodes that are being dragged (and dropped).
   */
  public BrowserNodeInfo[] getNodes()
  {
    return nodes;
  }

  /**
   * Sets the nodes that are being dragged (and dropped).
   * @param nodes the nodes that are being dragged (and dropped).
   */
  public void setNodes(BrowserNodeInfo[] nodes)
  {
    this.nodes = nodes;
  }

  /**
   * Returns the component that contains the nodes (for instance the tree in
   * the LDAP browser).
   * @return the component that contains the nodes (for instance the tree in
   * the LDAP browser).
   */
  public Component getParent()
  {
    return parent;
  }

  /**
   * Sets the component that contains the nodes (for instance the tree in
   * the LDAP browser).
   * @param parent the component that contains the nodes.
   */
  public void setParent(Component parent)
  {
    this.parent = parent;
  }
}

