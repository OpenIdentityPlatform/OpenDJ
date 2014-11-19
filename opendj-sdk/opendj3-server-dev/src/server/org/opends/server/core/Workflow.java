/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.core;

import org.opends.server.types.DN;
import org.opends.server.types.Operation;
import org.opends.server.types.CanceledOperationException;

/**
 * This class defines the workflow interface.
 *
 * Each workflow is a node in a workflow tree (aka workflow topology).
 * Each node in the tree is linked to a workflow object of the first implementation
 * and the base DN of the node is the base DN of the attached workflow object.
 * The relationship of the nodes in the tree is based on the base DNs of the nodes.
 * A workflow node is a subordinate of another workflow node when the base DN
 * of the former is a superior of the base DN of the latter.
 * Workflow topology are useful, for example, in subtree searches:
 * search is performed on a node as well as on all the subordinate nodes.
 */
public interface Workflow
{
  /**
   * Gets the base DN which identifies the set of data upon which the
   * workflow is to be executed.
   *
   * @return the base DN of the workflow
   */
  public DN getBaseDN();


  /**
   * Executes all the tasks defined by the workflow task tree for a given
   * operation.
   *
   * @param operation  the operation to execute
   *
   * @throws CanceledOperationException if this operation should
   * be cancelled.
   */
  public void execute(Operation operation)
      throws CanceledOperationException;
}
