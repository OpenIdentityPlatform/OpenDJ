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

import java.util.TreeMap;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Operation;
import org.opends.server.workflowelement.localbackend.LocalBackendWorkflowElement;

import static org.forgerock.util.Reject.*;
import static org.opends.messages.CoreMessages.*;

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
  /** The workflow identifier used by the configuration. */
  private final String workflowID;

  /** The root of the workflow task tree. */
  private LocalBackendWorkflowElement rootWorkflowElement;

  /** The base DN of the data handled by the workflow. */
  private final DN baseDN;

  /**
   * Flag indicating whether the workflow root node of the task tree is
   * handling a private local backend.
   * A private local backend is used by the server to store "private data"
   * such as schemas, tasks, monitoring data, configuration data... Such
   * private data are not returned upon a subtree search on the root DSE.
   * Also it is not planned to have anything but a single node task tree
   * to handle private local backend. So workflows used for proxy and
   * virtual will always be made public (ie. not private). So, unless the
   * rootWorkflowElement is handling a private local backend, the isPrivate
   * flag will always return false.
   */
  private final boolean isPrivate;

  /** The set of workflows registered with the server. */
  private static TreeMap<String, Workflow> registeredWorkflows =
    new TreeMap<String, Workflow>();

  /** A lock to protect concurrent access to the registeredWorkflows. */
  private final static Object registeredWorkflowsLock = new Object();

  /**
   * A reference counter used to count the number of workflow nodes that
   * were registered with a network group. A workflow can be disabled or
   * deleted only when its reference counter value is 0.
   */
  private int referenceCounter;
  private final Object referenceCounterLock = new Object();


  /**
   * Creates a new instance of a workflow implementation. To define a workflow
   * one needs to provide a task tree root node (the rootWorkflowElement) and
   * a base DN to identify the data set upon which the tasks can be applied.
   *
   * The rootWorkflowElement must not be null.
   *
   * @param workflowId          workflow internal identifier
   * @param baseDN              identifies the data handled by the workflow
   * @param rootWorkflowElement the root node of the workflow task tree
   */
  public WorkflowImpl(String workflowId, DN baseDN, LocalBackendWorkflowElement rootWorkflowElement
      )
  {
    this.workflowID = workflowId;
    this.baseDN = baseDN;
    this.rootWorkflowElement = rootWorkflowElement;
    if (this.rootWorkflowElement != null)
    {
      this.isPrivate = rootWorkflowElement.isPrivate();
    }
    else
    {
      this.isPrivate = false;
    }
  }

  /**
   * Gets the base DN of the data set being handled by the workflow.
   *
   * @return the workflow base DN
   */
  @Override
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
  @Override
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
      LocalizableMessageBuilder message = new LocalizableMessageBuilder(
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
  void register() throws DirectoryException
  {
    ifNull(workflowID);

    synchronized (registeredWorkflowsLock)
    {
      // The workflow must not be already registered
      if (registeredWorkflows.containsKey(workflowID))
      {
        throw new DirectoryException(
            ResultCode.UNWILLING_TO_PERFORM,
            ERR_REGISTER_WORKFLOW_ALREADY_EXISTS.get(workflowID));
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
  void deregister()
  {
    ifNull(workflowID);

    // Deregister the workflow with the list of registered workflows.
    synchronized (registeredWorkflowsLock)
    {
      TreeMap<String, Workflow> newWorkflows =
        new TreeMap<String, Workflow>(registeredWorkflows);
      newWorkflows.remove(workflowID);
      registeredWorkflows = newWorkflows;
    }
  }


  /**
   * Deregisters all Workflows that have been registered.  This should be
   * called when the server is shutting down.
   */
  static void deregisterAllOnShutdown()
  {
    synchronized (registeredWorkflowsLock)
    {
      registeredWorkflows = new TreeMap<String, Workflow>();
    }
  }


  /**
   * Gets a workflow that was registered with the server.
   *
   * @param workflowID  the ID of the workflow to get
   * @return the requested workflow
   */
  static Workflow getWorkflow(String workflowID)
  {
    return registeredWorkflows.get(workflowID);
  }

  /**
   * Gets the root workflow element for test purpose only.
   *
   * @return the root workflow element.
   */
  LocalBackendWorkflowElement getRootWorkflowElement()
  {
    return rootWorkflowElement;
  }

  /**
   * Display the workflow object.
   * @return a string identifying the workflow.
   */
  @Override
  public String toString()
  {
    return "Workflow " + workflowID;
  }

  /**
   * Increments the workflow reference counter.
   * <p>
   * As long as the counter value is not 0 the workflow cannot be
   * disabled nor deleted.
   */
  public void incrementReferenceCounter()
  {
    synchronized (referenceCounterLock)
    {
      referenceCounter++;
    }
  }


  /**
   * Decrements the workflow reference counter.
   * <p>
   * As long as the counter value is not 0 the workflow cannot be
   * disabled nor deleted.
   */
  public void decrementReferenceCounter()
  {
    synchronized (referenceCounterLock)
    {
      if (referenceCounter == 0)
      {
        // the counter value is 0, we should not need to decrement anymore
        throw new AssertionError(
          "Reference counter of the workflow " + workflowID
          + " is already set to 0, cannot decrement it anymore"
          );
      }
      referenceCounter--;
    }
  }


  /**
   * Gets the value of the reference counter of the workflow.
   *
   * @return the reference counter of the workflow
   */
  public int getReferenceCounter()
  {
    return referenceCounter;
  }
}
