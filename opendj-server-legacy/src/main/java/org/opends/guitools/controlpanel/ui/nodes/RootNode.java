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

/**
 * The root node of the tree in the 'Manage Entries...' tree.  It represents
 * the root entry of the directory.
 */
public class RootNode extends SuffixNode {

  private static final long serialVersionUID = 9030738910898224866L;

  /** Constructor of the node. */
  public RootNode() {
    super("");
    setLeaf(false);
    setRefreshNeededOnExpansion(false);
  }
}
