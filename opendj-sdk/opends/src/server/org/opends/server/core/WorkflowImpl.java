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

import static org.opends.messages.CoreMessages.*;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

import static org.opends.server.util.Validator.ensureNotNull;

import java.util.Collection;
import java.util.TreeMap;

import org.opends.server.types.*;
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
  // The workflow identifier used by the configuration.
  private String workflowID = null;

  // The root of the workflow task tree.
  private WorkflowElement<?> rootWorkflowElement = null;

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

  // The set of workflows registered with the server.
  private static TreeMap<String, Workflow> registeredWorkflows =
    new TreeMap<String, Workflow>();

  // A lock to protect concurrent access to the registeredWorkflows.
  private static Object registeredWorkflowsLock = new Object();


  /**
   * Creates a new instance of a workflow implementation. To define a worfklow
   * one needs to provide a task tree root node (the rootWorkflowElement) and
   * a base DN to identify the data set upon which the tasks can be applied.
   *
   * The rootWorkflowElement must not be null.
   *
   * @param workflowId          workflow internal identifier
   * @param baseDN              identifies the data handled by the workflow
   * @param rootWorkflowElement the root node of the workflow task tree
   */
  public WorkflowImpl(
      String             workflowId,
      DN                 baseDN,
      WorkflowElement<?> rootWorkflowElement
      )
  {
    this.workflowID = workflowId;
    this.baseDN = baseDN;
    this.rootWorkflowElement = rootWorkflowElement;
    if (this.rootWorkflowElement != null)
    {
      this.isPrivate = rootWorkflowElement.isPrivate();
    }
  }


  /**
   * Performs any finalization that might be required when this
   * workflow is unloaded.  No action is taken in the default
   * implementation.
   */
  public void finalizeWorkflow()
  {
    // No action is required by default.
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
   * Gets the workflow internal identifier.
   *
   * @return the workflow internal identifier
   */
  public String getWorkflowId()
  {
    return workflowID;
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
   *
   * @throws CanceledOperationException if this operation should
   * be canceled.
   */
  public void execute(Operation operation)
    throws CanceledOperationException
  {
    if (rootWorkflowElement != null)
    {
      rootWorkflowElement.execute(operation);
    }
    else
    {
      // No root workflow element? It's a configuration error.
      operation.setResultCode(ResultCode.OPERATIONS_ERROR);
      MessageBuilder message = new MessageBuilder(
        ERR_ROOT_WORKFLOW_ELEMENT_NOT_DEFINED.get(workflowID));
      operation.setErrorMessage(message);
    }
  }


  /**
   * Registers the current workflow (this) with the server.
   *
   * @throws  DirectoryException  If the workflow ID for the provided workflow
   *                              conflicts with the workflow ID of an existing
   *                              workflow.
   */
  public void register()
      throws DirectoryException
  {
    ensureNotNull(workflowID);

    synchronized (registeredWorkflowsLock)
    {
      // The workflow must not be already registered
      if (registeredWorkflows.containsKey(workflowID))
      {
        Message message =
                ERR_REGISTER_WORKFLOW_ALREADY_EXISTS.get(workflowID);
        throw new DirectoryException(
            ResultCode.UNWILLING_TO_PERFORM, message);
      }

      TreeMap<String, Workflow> newRegisteredWorkflows =
        new TreeMap<String, Workflow>(registeredWorkflows);
      newRegisteredWorkflows.put(workflowID, this);
      registeredWorkflows = newRegisteredWorkflows;
    }
  }


  /**
   * Deregisters the current workflow (this) with the server.
   */
  public void deregister()
  {
    ensureNotNull(workflowID);

    synchronized (registeredWorkflowsLock)
    {
      TreeMap<String, Workflow> newWorkflows =
        new TreeMap<String, Workflow>(registeredWorkflows);
      newWorkflows.remove(workflowID);
      registeredWorkflows = newWorkflows;
    }
  }


  /**
   * Deregisters a workflow with the server. The workflow to deregister
   * is identified with its identifier.
   *
   * @param workflowID  the identifier of the workflow to deregister
   *
   * @return the workflow that has been deregistered,
   *         <code>null</code> if no workflow has been found.
   */
  public WorkflowImpl deregister(String workflowID)
  {
    WorkflowImpl workflowToDeregister = null;

    synchronized (registeredWorkflowsLock)
    {
      if (registeredWorkflows.containsKey(workflowID))
      {
        workflowToDeregister =
          (WorkflowImpl) registeredWorkflows.get(workflowID);
        workflowToDeregister.deregister();
      }
    }

    return workflowToDeregister;
  }


  /**
   * Deregisters all Workflows that have been registered.  This should be
   * called when the server is shutting down.
   */
  public static void deregisterAllOnShutdown()
  {
    synchronized (registeredWorkflowsLock)
    {
      registeredWorkflows =
        new TreeMap<String, Workflow>();
    }
  }


  /**
   * Gets a workflow that was registered with the server.
   *
   * @param workflowID  the ID of the workflow to get
   * @return the requested workflow
   */
  public static Workflow getWorkflow(
      String workflowID)
  {
    return registeredWorkflows.get(workflowID);
  }


  /**
   * Gets all the workflows that were registered with the server.
   *
   * @return the list of registered workflows
   */
  public static Collection<Workflow> getWorkflows()
  {
    return registeredWorkflows.values();
  }


  /**
   * Gets the root workflow element for test purpose only.
   *
   * @return the root workflow element.
   */
  WorkflowElement<?> getRootWorkflowElement()
  {
    return rootWorkflowElement;
  }


  /**
   * Resets all the registered workflows.
   */
  public static void resetConfig()
  {
    synchronized (registeredWorkflowsLock)
    {
      registeredWorkflows = new TreeMap<String, Workflow>();
    }
  }

}
