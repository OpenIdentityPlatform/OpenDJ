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
package org.opends.server.core;


import org.opends.server.types.*;


/**
 * This class implements the workflow node that handles the root DSE entry.
 * As opposed to the WorkflowTopologyNode class, the root DSE node has no
 * parent node nor subordinate nodes. Instead, the root DSE node has a set
 * of naming contexts, each of which is a WorkflowTopologyNode object with
 * no parent.
 */
public class RootDseWorkflowTopology extends WorkflowTopology
{

  // The naming contexts known by the root DSE. These naming contexts
  // are defined in the scope of a network group.
  private NetworkGroupNamingContexts namingContexts = null;


  /**
   * Creates a workflow node to handle the root DSE entry.
   *
   * @param workflowImpl    the workflow which contains the processing for
   *                        the root DSE backend
   * @param namingContexts  the list of naming contexts being registered
   *                        with the network group the root DSE belongs to
   */
  public RootDseWorkflowTopology(
      WorkflowImpl               workflowImpl,
      NetworkGroupNamingContexts namingContexts
      )
  {
    super(workflowImpl);
    this.namingContexts = namingContexts;
  }


  /**
   * Executes an operation on the root DSE entry.
   *
   * @param operation the operation to execute
   *
   * @throws CanceledOperationException if this operation should
   * be cancelled.
   */
  public void execute(Operation operation)
      throws CanceledOperationException {
    // Execute the operation.
    OperationType operationType = operation.getOperationType();
    if (operationType != OperationType.SEARCH)
    {
      // Execute the operation
      getWorkflowImpl().execute(operation);
    }
    else
    {
      // Execute the SEARCH operation
      executeSearch((SearchOperation) operation);
    }
  }


  /**
   * Executes a search operation on the the root DSE entry.
   *
   * @param searchOp the operation to execute
   *
   * @throws CanceledOperationException if this operation should
   * be cancelled.
   */
  private void executeSearch(SearchOperation searchOp)
      throws CanceledOperationException {
    // Keep a the original search scope because we will alter it in the
    // operation.
    SearchScope originalScope = searchOp.getScope();

    // Search base?
    // The root DSE entry itself is never returned unless the operation
    // is a search base on the null suffix.
    if (originalScope == SearchScope.BASE_OBJECT)
    {
      getWorkflowImpl().execute(searchOp);
      return;
    }

    // Create a workflow result code in case we need to perform search in
    // subordinate workflows.
    WorkflowResultCode workflowResultCode = new WorkflowResultCode(
        searchOp.getResultCode(), searchOp.getErrorMessage());

    // The search scope is not 'base', so let's do a search on all the public
    // naming contexts with appropriate new search scope and new base DN.
    SearchScope newScope = elaborateScopeForSearchInSubordinates(originalScope);
    searchOp.setScope(newScope);
    DN originalBaseDN = searchOp.getBaseDN();

    for (WorkflowTopologyNode namingContext:
         namingContexts.getPublicNamingContexts())
    {
      // We have to change the operation request base DN to match the
      // subordinate workflow base DN. Otherwise the workflow will
      // return a no such entry result code as the operation request
      // base DN is a superior of the workflow base DN!
      DN ncDN = namingContext.getBaseDN();

      // Set the new request base DN then do execute the operation
      // in the naming context workflow.
      searchOp.setBaseDN(ncDN);
      namingContext.execute(searchOp);
      boolean sendReferenceEntry =
        workflowResultCode.elaborateGlobalResultCode(
          searchOp.getResultCode(), searchOp.getErrorMessage());
      if (sendReferenceEntry)
      {
        // TODO jdemendi - turn a referral result code into a reference entry
        // and send the reference entry to the client application
      }
    }

    // Now restore the original request base DN and original search scope
    searchOp.setBaseDN(originalBaseDN);
    searchOp.setScope(originalScope);

    // Set the operation result code and error message
    searchOp.setResultCode(workflowResultCode.resultCode());
    searchOp.setErrorMessage(workflowResultCode.errorMessage());
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

    // display the identifier and baseDN
    String workflowID = this.getWorkflowImpl().getWorkflowId();
    sb.append(leftMargin + "Workflow ID = " + workflowID + "\n");
    sb.append(leftMargin + "         baseDN:[ \"\" ]\n");

    return sb;
  }

}
