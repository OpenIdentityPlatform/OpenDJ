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
import org.forgerock.opendj.ldap.SearchScope;

/**
 * This class is the base class used to build the workflow topology.
 * A workflow topology is a tree of workflows. Each node in the tree
 * is attached to a WorkflowImpl which contains the task tree (ie. the
 * processing).
 *
 * There are two types of workflow nodes. The first one is used to build
 * nodes in the workflow topology (WorkflowTopologyNode) and the second
 * one is used to implement the root DSE node (RootDseWorkflowTopology).
 */
public abstract class WorkflowTopology implements Workflow
{
  /** The workflow implementation containing the task tree (ie. the processing). */
  private WorkflowImpl workflowImpl;

  /**
   * Create a new instance of the workflow topology base class.
   * The instance is initialized with the workflow implementation which
   * contains the task tree (ie. the processing).
   *
   * @param workflowImpl the workflow which contains the processing
   */
  protected WorkflowTopology(WorkflowImpl workflowImpl)
  {
    this.workflowImpl = workflowImpl;
  }


  /**
   * Returns the workflow implementation which contains the task tree
   * (ie. the processing).
   *
   * @return the workflow implementation which contains the processing
   */
  public WorkflowImpl getWorkflowImpl()
  {
    return workflowImpl;
  }


  /**
   * Gets the base DN of the workflow node. The base DN of the workflow
   * node is the base DN of the attached workflow implementation containing
   * the processing.
   *
   * @return the base DN of the workflow containing the processing.
   */
  @Override
  public DN getBaseDN()
  {
    return getWorkflowImpl().getBaseDN();
  }


  /**
   * Elaborates a new search scope according to the current search scope.
   * The new scope is intended to be used for searches on subordinate
   * workflows.
   *
   * @param currentScope  the current search scope
   * @return the new scope to use for searches on subordinate workflows,
   *         <code>null</code> when current scope is 'base'
   */
  protected SearchScope elaborateScopeForSearchInSubordinates(SearchScope currentScope)
  {
    switch (currentScope.asEnum())
    {
    case BASE_OBJECT:
      return null;
    case SINGLE_LEVEL:
      return SearchScope.BASE_OBJECT;
    case SUBORDINATES:
    case WHOLE_SUBTREE:
      return SearchScope.WHOLE_SUBTREE;
    default:
      return currentScope;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + " " + workflowImpl;
  }

}
