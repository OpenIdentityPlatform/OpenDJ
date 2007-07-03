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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;


import org.opends.server.types.DN;
import org.opends.server.types.Operation;
import org.opends.server.workflowelement.WorkflowElement;


/**
 * This class implements the workflow interface. Each task in the workflow
 * is implemented by a WorkflowElement. All the tasks in the workflow are
 * structured in a tree of tasks and the root node of the task tree is
 * stored in the Workflow class itself. To execute a workflow, one just need
 * to call the execute method on the root node of the task tree. Then each
 * task in turn will execute its subordinate nodes and synchronizes them
 * as needed.
 */
public class WorkflowImpl implements Workflow
{

  // The root of the workflow task tree.
  private WorkflowElement rootWorkflowElement = null;

  // The base DN of the data handled by the workflow.
  private DN baseDN = null;

  // Flag indicating whether the workflow root node of the task tree is
  // handling a private local backend.
  //
  // A private local backend is used by the server to store "private data"
  // such as schemas, tasks, monitoring data, configuration data... Such
  // private data are not returned upon a subtree search on the root DSE.
  // Also it is not planned to have anything but a single node task tree
  // to handle private local backend. So workflows used for proxy and
  // virtual will always be made public (ie. not private). So, unless the
  // rootWorkflowElement is handling a private local backend, the isPrivate
  // flag will always return false.
  private boolean isPrivate = false;


  /**
   * Creates a new instance of a workflow implementation. To define a worfklow
   * one needs to provide a task tree root node (the rootWorkflowElement) and
   * a base DN to identify the data set upon which the tasks can be applied.
   *
   * The rootWorkflowElement must not be null.
   *
   * @param baseDN              identifies the data handled by the workflow
   * @param rootWorkflowElement the root node of the workflow task tree
   */
  public WorkflowImpl(
      DN              baseDN,
      WorkflowElement rootWorkflowElement
      )
  {
    this.baseDN = baseDN;
    this.rootWorkflowElement = rootWorkflowElement;
    if (this.rootWorkflowElement != null)
    {
      this.isPrivate = rootWorkflowElement.isPrivate();
    }
  }


  /**
   * Gets the base DN of the data set being handled by the workflow.
   *
   * @return the workflow base DN
   */
  public DN getBaseDN()
  {
    return baseDN;
  }


  /**
   * Indicates whether the root node of the workflow task tree is
   * handling a private local backend.
   *
   * @return <code>true</code> if the workflow encapsulates a private local
   *         backend
   */
  public boolean isPrivate()
  {
    return isPrivate;
  }


  /**
   * Executes all the tasks defined by the workflow task tree for a given
   * operation.
   *
   * @param operation  the operation to execute
   */
  public void execute(
      Operation operation
      )
  {
    rootWorkflowElement.execute(operation);
  }
}
