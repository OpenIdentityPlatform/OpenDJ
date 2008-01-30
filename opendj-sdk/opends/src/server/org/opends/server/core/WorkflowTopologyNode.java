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
 *      Portions Copyright 2007-2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;


import java.util.ArrayList;

import org.opends.server.types.DN;
import org.opends.server.types.Operation;
import org.opends.server.types.OperationType;
import org.opends.server.types.SearchScope;
import org.opends.server.workflowelement.WorkflowElement;


/**
 * This class implements a workflow node. A workflow node is used
 * to build a tree of workflows (aka workflow topology). Each node
 * may have a parent node and/or subordinate nodes. A node with no
 * parent is a naming context.
 *
 * Each node in the workflow topology is linked to a WorkflowImpl
 * which contains the real processing. The base DN of the workflow
 * node is the base DN of the related WorkflowImpl.
 *
 * How the workflow topology is built?
 * A workflow node is a subordinate of another workflow node when
 * the base DN of the former workflow is an ancestor of the base DN
 * of the latter workflow.
 *
 * A subtree search on a workflow node is performed on the node itself as
 * well as on all the subordinate nodes.
 */
public class WorkflowTopologyNode extends WorkflowTopology
{
  // Parent node of the current workflow node.
  private WorkflowTopologyNode parent = null;


  // The list of subordinate nodes of the current workflow node.
  private ArrayList<WorkflowTopologyNode> subordinates =
    new ArrayList<WorkflowTopologyNode>();


  /**
   * Creates a new node for a workflow topology. The new node is initialized
   * with a WorkflowImpl which contains the real processing. Optionally,
   * the node may have tasks to be executed before and/or after the real
   * processing. In the current implementation, such pre and post workflow
   * elements are not used.
   *
   * @param workflowImpl          the real processing attached to the node
   * @param preWorkflowElements   the list of tasks to be executed before
   *                              the real processing
   * @param postWorkflowElements  the list of tasks to be executed after
   *                              the real processing
   */
  public WorkflowTopologyNode(
      WorkflowImpl workflowImpl,
      WorkflowElement[] preWorkflowElements,
      WorkflowElement[] postWorkflowElements
      )
  {
    super(workflowImpl);
  }


  /**
   * Executes an operation on a set of data being identified by the
   * workflow node base DN.
   *
   * @param operation the operation to execute
   */
  public void execute(
      Operation operation
      )
  {
    // Execute the operation
    getWorkflowImpl().execute(operation);

    // For subtree search operation we need to go through the subordinate
    // nodes.
    if (operation.getOperationType() == OperationType.SEARCH)
    {
      executeSearchOnSubordinates((SearchOperation) operation);
    }
  }


  /**
   * Executes a search operation on the subordinate workflows.
   *
   * @param searchOp the search operation to execute
   */
  private void executeSearchOnSubordinates(
      SearchOperation searchOp
      )
  {
    // If the scope of the search is 'base' then it's useless to search
    // in the subordinate workflows.
    SearchScope originalScope = searchOp.getScope();
    if (originalScope == SearchScope.BASE_OBJECT)
    {
      return;
    }

    // Elaborate the new search scope before executing the search operation
    // in the subordinate workflows.
    SearchScope newScope = elaborateScopeForSearchInSubordinates(originalScope);
    searchOp.setScope(newScope);

    // Let's search in the subordinate workflows.
    WorkflowResultCode workflowResultCode = new WorkflowResultCode(
        searchOp.getResultCode(), searchOp.getErrorMessage());
    DN originalBaseDN = searchOp.getBaseDN();
    for (WorkflowTopologyNode subordinate: getSubordinates())
    {
      // We have to change the operation request base DN to match the
      // subordinate workflow base DN. Otherwise the workflow will
      // return a no such entry result code as the operation request
      // base DN is a superior of the subordinate workflow base DN.
      DN subordinateDN = subordinate.getBaseDN();

      // If the new search scope is 'base' and the search base DN does not
      // map the subordinate workflow then skip the subordinate workflow.
      if ((newScope == SearchScope.BASE_OBJECT)
          && !subordinateDN.getParent().equals(originalBaseDN))
      {
        continue;
      }

      // If the request base DN is not a subordinate of the subordinate
      // worklfow base DN then don't search in the subordinate workflow.
      if (! originalBaseDN.isAncestorOf(subordinateDN))
      {
        continue;
      }

      // Set the new request base DN and do execute the
      // operation in the subordinate workflow.
      searchOp.setBaseDN(subordinateDN);
      subordinate.execute(searchOp);
      boolean sendReferenceEntry =
        workflowResultCode.elaborateGlobalResultCode(
          searchOp.getResultCode(), searchOp.getErrorMessage());
      if (sendReferenceEntry)
      {
        // TODO jdemendi - turn a referral result code into a reference entry
        // and send the reference entry to the client application
      }
    }

    // Now we are done with the operation, let's restore the original
    // base DN and search scope in the operation.
    searchOp.setBaseDN(originalBaseDN);
    searchOp.setScope(originalScope);

    // Update the operation result code and error message
    searchOp.setResultCode(workflowResultCode.resultCode());
    searchOp.setErrorMessage(workflowResultCode.errorMessage());
  }


  /**
   * Sets the parent workflow.
   *
   * @param parent  the parent workflow of the current workflow
   */
  public void setParent(WorkflowTopologyNode parent)
  {
    this.parent = parent;
  }


  /**
   * Gets the parent workflow.
   *
   * @return the parent workflow.
   */
  public WorkflowTopologyNode getParent()
  {
    return parent;
  }


  /**
   * Indicates whether the root workflow element is encapsulating a private
   * local backend or not.
   *
   * @return <code>true</code> if the root workflow element encapsulates
   *         a private local backend
   */
  public boolean isPrivate()
  {
    return getWorkflowImpl().isPrivate();
  }


  /**
   * Gets the base DN of the workflow that handles a given dn. The elected
   * workflow may be the current workflow or one of its subordiante workflows.
   *
   * @param  dn  the DN for which we are looking a parent DN
   * @return the base DN which is the parent of the <code>dn</code>,
   *         <code>null</code> if no parent DN was found
   */
  public DN getParentBaseDN(DN dn)
  {
    if (dn == null)
    {
      return null;
    }

    // parent base DN to return
    DN parentBaseDN = null;

    // Is the dn a subordinate of the current base DN?
    DN curBaseDN = getBaseDN();
    if (curBaseDN != null)
    {
      if (dn.isDescendantOf(curBaseDN))
      {
        // The dn may be handled by the current workflow.
        // Now we have to check whether the dn is handled by
        // a subordinate.
        for (WorkflowTopologyNode subordinate: getSubordinates())
        {
          parentBaseDN = subordinate.getParentBaseDN(dn);
          if (parentBaseDN != null)
          {
            // the dn is handled by a subordinate
            break;
          }
        }

        // If the dn is not handled by any subordinate, then it is
        // handled by the current workflow.
        if (parentBaseDN == null)
        {
          parentBaseDN = curBaseDN;
        }
      }
    }

    return parentBaseDN;
  }


  /**
   * Adds a workflow to the list of workflow subordinates without
   * additional control.
   *
   * @param newWorkflow     the workflow to add to the subordinate list
   * @param parentWorkflow  the parent workflow of the new workflow
   */
  private void addSubordinateNoCheck(
      WorkflowTopologyNode newWorkflow,
      WorkflowTopologyNode parentWorkflow
      )
  {
    subordinates.add(newWorkflow);
    newWorkflow.setParent(parentWorkflow);
  }


  /**
   * Adds a workflow to the subordinate list of the current workflow.
   * Before we can add the new workflow, we have to check whether
   * the new workflow is a parent workflow of any of the current
   * subordinates (if so, then we have to add the subordinate in the
   * subordinate list of the new workflow).
   *
   * @param newWorkflow  the workflow to add in the subordinate list
   */
  private void addSubordinate(
      WorkflowTopologyNode newWorkflow
      )
  {
    // Dont try to add the workflow to itself.
    if (newWorkflow == this)
    {
      return;
    }

    // Check whether subordinates of current workflow should move to the
    // new workflow subordinate list.
    ArrayList<WorkflowTopologyNode> curSubordinateList =
        new ArrayList<WorkflowTopologyNode>(getSubordinates());

    for (WorkflowTopologyNode curSubordinate: curSubordinateList)
    {
      DN newDN = newWorkflow.getBaseDN();
      DN subordinateDN = curSubordinate.getBaseDN();

      // Dont try to add workflow when baseDNs are
      // the same on both workflows.
      if (newDN.equals(subordinateDN)) {
        return;
      }

      if (subordinateDN.isDescendantOf(newDN))
      {
        removeSubordinate(curSubordinate);
        newWorkflow.addSubordinate(curSubordinate);
      }
    }

    // add the new workflow in the current workflow subordinate list
    addSubordinateNoCheck(newWorkflow, this);
  }


  /**
   * Remove a workflow from the subordinate list.
   *
   * @param subordinate  the subordinate to remove from the subordinate list
   */
  public void removeSubordinate(
      WorkflowTopologyNode subordinate
      )
  {
    subordinates.remove(subordinate);
  }


  /**
   * Tries to insert a new workflow in the subordinate list of one of the
   * current workflow subordinate, or in the current workflow subordinate list.
   *
   * @param newWorkflow  the new workflow to insert
   *
   * @return <code>true</code> if the new workflow has been inserted
   *         in any subordinate list
   */
  public boolean insertSubordinate(
      WorkflowTopologyNode newWorkflow
      )
  {
    // don't try to insert the workflow in itself!
    if (newWorkflow == this)
    {
      return false;
    }

    // the returned status
    boolean insertDone = false;

    DN parentBaseDN = getBaseDN();
    DN newBaseDN    = newWorkflow.getBaseDN();

    // dont' try to insert workflows when baseDNs are the same on both
    // workflows
    if (parentBaseDN.equals(newBaseDN))
    {
      return false;
    }

    // try to insert the new workflow
    if (newBaseDN.isDescendantOf(parentBaseDN))
    {
      // the new workflow is a subordinate for this parent DN, let's
      // insert the new workflow in the list of subordinates
      for (WorkflowTopologyNode subordinate: getSubordinates())
      {
        insertDone = subordinate.insertSubordinate(newWorkflow);
        if (insertDone)
        {
          // the newBaseDN is handled by a subordinate
          break;
        }
      }

      // if the newBaseDN is not handled by a subordinate then the workflow
      // is inserted it in the current workflow subordinate list
      if (! insertDone)
      {
        addSubordinate(newWorkflow);
        insertDone = true;
      }
    }

    return insertDone;
  }


  /**
   * Removes the current workflow from the parent subordinate list
   * and attach the workflow subordinates to the parent workflow.
   *
   * Example: the workflow to remove is w2
   *
   *        w1             w1
   *        |             / \
   *        w2     ==>   w3  w4
   *       / \
   *     w3   w4
   *
   * - Subordinate list of w1 is updated with w3 and w4.
   * - Parent workflow of w3 and w4 is now w1.
   */
  public void remove()
  {
    // First of all, remove the workflow from the parent subordinate list
    WorkflowTopologyNode parent = getParent();
    if (parent != null)
    {
      parent.removeSubordinate(this);
    }

    // Then set the parent of each subordinate and attach the subordinate to
    // the parent.
    for (WorkflowTopologyNode subordinate: getSubordinates())
    {
      subordinate.setParent(parent);
      if (parent != null)
      {
        parent.addSubordinateNoCheck(subordinate, parent);
      }
    }
  }


  /**
   * Gets the list of workflow subordinates.
   *
   * @return the list of workflow subordinates
   */
  public ArrayList<WorkflowTopologyNode> getSubordinates()
  {
    return subordinates;
  }


  /**
   * Gets the highest workflow in the topology that can handle the requestDN.
   * The highest workflow is either the current workflow or one of its
   * subordinates.
   *
   * @param requestDN  The DN for which we search for a workflow
   * @return the highest workflow that can handle the requestDN
   *         <code>null</code> if none was found
   */
  public WorkflowTopologyNode getWorkflowCandidate(
      DN requestDN
      )
  {
    // the returned workflow
    WorkflowTopologyNode workflowCandidate = null;

    // does the current workflow handle the request baseDN?
    DN baseDN = getParentBaseDN(requestDN);
    if (baseDN == null)
    {
      // the current workflow does not handle the requestDN,
      // let's return null
    }
    else
    {
      // is there any subordinate that can handle the requestDN?
      for (WorkflowTopologyNode subordinate: getSubordinates())
      {
        workflowCandidate = subordinate.getWorkflowCandidate(requestDN);
        if (workflowCandidate != null)
        {
          break;
        }
      }

      // none of the subordinates can handle the requestDN, so the current
      // workflow is the best root workflow candidate
      if (workflowCandidate == null)
      {
        workflowCandidate = this;
      }
    }

    return workflowCandidate;
  }


  /**
   * Dumps info from the current workflow for debug purpose.
   *
   * @param leftMargin  white spaces used to indent the traces
   * @return a string buffer that contains trace information
   */
  public StringBuilder toString(String leftMargin)
  {
    StringBuilder sb = new StringBuilder();

    // display the baseDN
    DN baseDN = getBaseDN();
    String workflowID = this.getWorkflowImpl().getWorkflowId();
    sb.append(leftMargin + "Workflow ID = " + workflowID + "\n");
    sb.append(leftMargin + "         baseDN:[");
    if (baseDN.isNullDN())
    {
      sb.append(" \"\"");
    }
    else
    {
      sb.append(" \"" + baseDN.toString() + "\"");
    }
    sb.append(" ]\n");

    // display the root workflow element
    sb.append(leftMargin
        + "         Root Workflow Element: "
        + getWorkflowImpl().getRootWorkflowElement() + "\n");

    // display parent workflow
    sb.append(leftMargin + "         Parent: " + getParent() + "\n");

    // dump each subordinate
    sb.append(leftMargin + "         List of subordinates:\n");
    ArrayList<WorkflowTopologyNode> subordinates = getSubordinates();
    if (subordinates.isEmpty())
    {
      sb.append(leftMargin + "            NONE\n");
    }
    else
    {
      for (WorkflowTopologyNode subordinate: getSubordinates())
      {
        sb.append(subordinate.toString(leftMargin + "            "));
      }
    }

    return sb;
  }

}
