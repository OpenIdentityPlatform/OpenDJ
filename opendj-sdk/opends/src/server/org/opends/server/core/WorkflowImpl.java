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
import static org.opends.server.util.Validator.ensureNotNull;

import java.util.Collection;
import java.util.Observable;
import java.util.Observer;
import java.util.TreeMap;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.admin.std.server.WorkflowCfg;
import org.opends.server.types.*;
import org.opends.server.workflowelement.WorkflowElement;
import org.opends.server.workflowelement.ObservableWorkflowElementState;


/**
 * This class implements the workflow interface. Each task in the workflow
 * is implemented by a WorkflowElement. All the tasks in the workflow are
 * structured in a tree of tasks and the root node of the task tree is
 * stored in the Workflow class itself. To execute a workflow, one just need
 * to call the execute method on the root node of the task tree. Then each
 * task in turn will execute its subordinate nodes and synchronizes them
 * as needed.
 */
public class WorkflowImpl implements Workflow, Observer
{
  // The workflow identifier used by the configuration.
  private final String workflowID;

  // The root of the workflow task tree.
  private WorkflowElement<?> rootWorkflowElement = null;

  // The root workflow element identifier.
  private String rootWorkflowElementID = null;

  // The base DN of the data handled by the workflow.
  private final DN baseDN;

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
   * Creates a new instance of a workflow implementation. To define a workflow
   * one needs to provide a task tree root node (the rootWorkflowElement) and
   * a base DN to identify the data set upon which the tasks can be applied.
   *
   * The rootWorkflowElement must not be null.
   *
   * @param workflowId          workflow internal identifier
   * @param baseDN              identifies the data handled by the workflow
   * @param rootWorkflowElementID  the identifier of the root workflow element
   * @param rootWorkflowElement the root node of the workflow task tree
   */
  public WorkflowImpl(
      String             workflowId,
      DN                 baseDN,
      String             rootWorkflowElementID,
      WorkflowElement<?> rootWorkflowElement
      )
  {
    this.workflowID = workflowId;
    this.baseDN = baseDN;
    this.rootWorkflowElement = rootWorkflowElement;
    if (this.rootWorkflowElement != null)
    {
      this.isPrivate = rootWorkflowElement.isPrivate();
      this.rootWorkflowElementID = rootWorkflowElementID;

      // The workflow wants to be notified when the workflow element state
      // is changing from enabled to disabled and vice versa.
      WorkflowElement.registereForStateUpdate(
        rootWorkflowElement, null, this);
    }
    else
    {
      // The root workflow element has not been loaded, let's register
      // the workflow with the list of objects that want to be notify
      // when the workflow element is created.
      WorkflowElement.registereForStateUpdate(
        null, rootWorkflowElementID, this);
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

    // Deregister the workflow with the list of objects to notify when
    // a workflow element is created or deleted.
    WorkflowElement.deregistereForStateUpdate(
      null, rootWorkflowElementID, this);

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


  /**
   * Updates the workflow configuration. This method should be invoked
   * whenever an existing workflow is modified.
   *
   * @param configuration  the new workflow configuration
   */
  public void updateConfig(WorkflowCfg configuration)
  {
    // The only parameter that can be changed is the root workflow element.
    String rootWorkflowElementID = configuration.getWorkflowElement();
    WorkflowElement<?> rootWorkflowElement =
      DirectoryServer.getWorkflowElement(rootWorkflowElementID);

    // Update the ID of the new root workflow element
    // and deregister the workflow with the list of objects to notify
    // when the former root workflow element is created
    String previousRootWorkflowElement = this.rootWorkflowElementID;
    WorkflowElement.deregistereForStateUpdate(
      null, previousRootWorkflowElement, this);
    this.rootWorkflowElementID = rootWorkflowElementID;

    // Does the new root workflow element exist?
    if (rootWorkflowElement == null)
    {
      // The new root workflow element does not exist yet then do nothing
      // but register with the list of object to notify when the workflow
      // element is created (and deregister first in case the workflow
      // was already registered)
      WorkflowElement.registereForStateUpdate(
        null, rootWorkflowElementID, this);
      rootWorkflowElement = null;
    }
    else
    {
      // The new root workflow element exists, let's use it and don't forget
      // to register with the list of objects to notify when the workflow
      // element is deleted.
      this.rootWorkflowElement = rootWorkflowElement;
      WorkflowElement.registereForStateUpdate(
        rootWorkflowElement, null, this);
    }
  }


  /**
   * {@inheritDoc}
   */
  public void update(Observable observable, Object arg)
  {
    if (observable instanceof ObservableWorkflowElementState)
    {
      ObservableWorkflowElementState weState =
        (ObservableWorkflowElementState) observable;
      updateRootWorkflowElementState(weState);
    }
  }


  /**
   * Display the workflow object.
   * @return a string identifying the workflow.
   */
  public String toString()
  {
    String id = "Workflow " + workflowID;
    return id;
  }


  /**
   * Takes into account an update of the root workflow element.
   * <p>
   * This method is called when a workflow element is created or
   * deleted. If the workflow element is the root workflow element
   * then the workflow processes it as follow:
   * <br>
   * If the workflow element is disabled then the workflow reset
   * its root workflow element (ie. the workflow has no more workflow
   * element, hence the workflow cannot process requests anymore).
   * <br>
   * If the workflow element is enabled then the workflow adopts the
   * workflow element as its new root workflow element. The workflow
   * then can process requests again.
   *
   * @param weState  the new state of the root workflow element
   */
  private void updateRootWorkflowElementState(
      ObservableWorkflowElementState weState)
  {
    // Check that the workflow element maps the root workflow element.
    // If not then ignore the workflow element.
    WorkflowElement<?> we = weState.getObservedWorkflowElement();
    String newWorkflowElementID = we.getWorkflowElementID();
    if (! rootWorkflowElementID.equalsIgnoreCase(newWorkflowElementID))
    {
      return;
    }

    // The workflow element maps the root workflow element, let's process it.
    if (weState.workflowElementIsEnabled())
    {
      // The root workflow element is enabled, let's use it
      // and don't forget to register the workflow with the list
      // of objects to notify when the root workflow element
      // is disabled...
      rootWorkflowElement = weState.getObservedWorkflowElement();
      WorkflowElement.registereForStateUpdate(
        rootWorkflowElement, null, this);
      WorkflowElement.deregistereForStateUpdate(
        null, rootWorkflowElementID, this);
    }
    else
    {
      // The root workflow element has been disabled. Reset the current
      // reference to the root workflow element and register the workflow
      // with the list of objects to notify when new workflow elements
      // are created.
      WorkflowElement.registereForStateUpdate(
        null, rootWorkflowElement.getWorkflowElementID(), this);
      rootWorkflowElement = null;
    }
  }
}
