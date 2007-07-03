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


import java.util.ArrayList;

import org.opends.server.types.DN;
import org.opends.server.workflowelement.WorkflowElement;


/**
 * This class defines the network group. A network group is used to categorize
 * client connections. A network group is defined by a set of criteria, a
 * set of policies and a set of workflows. A client connection belongs to a
 * network group whenever it satisfies all the network group criteria. As soon
 * as a client connection belongs to a network group, it has to comply with
 * all the network group policies and all the client operation can be routed
 * to one the network group workflows.
 */
public class NetworkGroup
{
  // Workflows registered with the current network group.
  private ArrayList<WorkflowTopologyNode> registeredWorkflows =
      new ArrayList<WorkflowTopologyNode>();


  // The workflow for the rootDSE entry. The RootDSE workflow
  // is not stored in the list of registered workflows.
  private RootDseWorkflowTopology rootDSEWorkflow = null;


  // List of naming contexts handled by the network group.
  private NetworkGroupNamingContexts namingContexts =
      new NetworkGroupNamingContexts();


  // The default network group (singleton).
  // The default network group has no criterion, no policy, and gives
  // access to all the workflows. The purpose of the default network
  // group is to allow new clients to perform a first operation before
  // they can be attached to a specific network group.
  private static NetworkGroup defaultNetworkGroup =
      new NetworkGroup ("default");


  // The list of all network groups that have been created.
  // The default network group is not stored in that pool.
  private static ArrayList<NetworkGroup> networkGroupPool =
      new ArrayList<NetworkGroup>();


  // Human readable network group name.
  private String networkGroupName = null;


  /**
   * Creates a new instance of the network group.
   *
   * @param networkGroupName  the name of the network group for debug purpose
   */
  public NetworkGroup (
      String networkGroupName
      )
  {
    this.networkGroupName = networkGroupName;
  }


  /**
   * Registers a network group with the pool of network groups.
   *
   * @param networkGroup  the network group to register
   */
  public static void registerNetworkGroup(
      NetworkGroup networkGroup
      )
  {
    networkGroupPool.add(networkGroup);
  }


  /**
   * Registers a workflow with the network group.
   *
   * @param workflow  the workflow to register
   */
  public void registerWorkflow(
      WorkflowImpl workflow
      )
  {
    // The workflow is rgistered with no pre/post workflow element.
    registerWorkflow(workflow, null, null);
  }


  /**
   * Registers a workflow with the network group and the workflow may have
   * pre and post workflow element.
   *
   * @param workflow              the workflow to register
   * @param preWorkflowElements   the tasks to execute before the workflow
   * @param postWorkflowElements  the tasks to execute after the workflow
   */
  private void registerWorkflow(
      WorkflowImpl workflow,
      WorkflowElement[] preWorkflowElements,
      WorkflowElement[] postWorkflowElements
      )
  {
    // true as soon as the workflow has been registered
    boolean registered = false;

    // Is it the rootDSE workflow?
    DN baseDN = workflow.getBaseDN();
    if (baseDN.isNullDN())
    {
      // NOTE - The rootDSE workflow is not stored in the pool.
      rootDSEWorkflow = new RootDseWorkflowTopology(workflow, namingContexts);
      registered = true;
    }
    else
    {
      // This workflow is not the rootDSE workflow. Try to insert it in the
      // workflow topology.
      if (! baseDNAlreadyRegistered(baseDN))
      {
        WorkflowTopologyNode workflowTopology = new WorkflowTopologyNode(
            workflow, preWorkflowElements, postWorkflowElements);

        // Add the workflow in the workflow topology...
        for (WorkflowTopologyNode curWorkflow: registeredWorkflows)
        {
          // Try to insert the new workflow under an existing workflow...
          if (curWorkflow.insertSubordinate(workflowTopology))
          {
            // new workflow has been inserted in the topology
            break;
          }

          // ... or try to insert the existing workflow below the new
          // workflow
          if (workflowTopology.insertSubordinate(curWorkflow))
          {
            // new workflow has been inserted in the topology
            break;
          }
        }

        // ... then register the workflow with the pool.
        registeredWorkflows.add(workflowTopology);
        registered = true;

        // Rebuild the list of naming context handled by the network group
        rebuildNamingContextList();
      }
    }

    // If the workflow has been registered successfully then register it
    // with the default network group
    if (registered)
    {
      if (this != defaultNetworkGroup)
      {
        defaultNetworkGroup.registerWorkflow(
            workflow, preWorkflowElements, postWorkflowElements);
      }
    }
  }


  /**
   * Deregisters a workflow with the network group.
   *
   * @param baseDN  the baseDN of the workflow to deregister
   */
  public void deregisterWorkflow (
      DN baseDN
      )
  {
    Workflow workflow = getWorkflowCandidate(baseDN);
    if (workflow != null)
    {
      deregisterWorkflow(workflow);
    }
  }


  /**
   * Deregisters a workflow with the network group.
   *
   * @param workflow  the workflow to deregister
   */
  private void deregisterWorkflow(
      Workflow workflow
      )
  {
    // true as soon as the workflow has been deregistered
    boolean deregistered = false;

    // Is it the rootDSE workflow?
    if (workflow == rootDSEWorkflow)
    {
      rootDSEWorkflow = null;
      deregistered = true;
    }
    else
    {
      // The workflow to deregister is not the root DSE workflow.
      // Remove it from the workflow topology.
      WorkflowTopologyNode workflowTopology = (WorkflowTopologyNode) workflow;
      workflowTopology.remove();

      // Then deregister the workflow with the network group.
      registeredWorkflows.remove(workflow);
      deregistered = true;

      // Rebuild the list of naming context handled by the network group
      rebuildNamingContextList();
    }

    // If the workflow has been deregistered then deregister it with
    // the default network group as well
    if (deregistered)
    {
      if (this != defaultNetworkGroup)
      {
        defaultNetworkGroup.deregisterWorkflow(workflow);
      }
    }
  }


  /**
   * Gets the highest workflow in the topology that can handle the baseDN.
   *
   * @param baseDN  the base DN of the request
   * @return the highest workflow in the topology that can handle the base DN,
   *         <code>null</code> if none was found
   */
  public Workflow getWorkflowCandidate (
      DN baseDN
      )
  {
    // the top workflow to return
    Workflow workflowCandidate = null;

    // get the list of workflow candidates
    if (baseDN.isNullDN())
    {
      // The rootDSE workflow is the candidate.
      workflowCandidate = rootDSEWorkflow;
    }
    else
    {
      // Search the highest workflow in the topology that can handle
      // the baseDN.
      for (WorkflowTopologyNode curWorkflow: namingContexts.getNamingContexts())
      {
        workflowCandidate = curWorkflow.getWorkflowCandidate (baseDN);
        if (workflowCandidate != null)
        {
          break;
        }
      }
    }

    return workflowCandidate;
  }


  /**
   * Returns the default network group. The default network group is always
   * defined and has no criterion, no policy and provide full access to
   * all the registered workflows.
   *
   * @return the default network group
   */
  public static NetworkGroup getDefaultNetworkGroup()
  {
    return defaultNetworkGroup;
  }


  /**
   * Rebuilds the list of naming contexts handled by the network group.
   * This operation should be performed whenever a workflow topology
   * has been updated (workflow registration or de-registration).
   */
  private void rebuildNamingContextList()
  {
    // reset lists of naming contexts
    namingContexts.resetLists();

    // a registered workflow with no parent is a naming context
    for (WorkflowTopologyNode curWorkflow: registeredWorkflows)
    {
      WorkflowTopologyNode parent = curWorkflow.getParent();
      if (parent == null)
      {
        namingContexts.addNamingContext (curWorkflow);
      }
    }
  }


  /**
   * Checks whether a base DN has been already registered with
   * the network group.
   *
   * @param baseDN  the base DN to check
   * @return <code>false</code> if the base DN is registered with the
   *         network group, <code>false</code> otherwise
   */
  private boolean baseDNAlreadyRegistered (
      DN baseDN
      )
  {
    // returned result
    boolean alreadyRegistered = false;

    // go through the list of registered workflow and check whether a base DN
    // has already been used in a registered workflow
    for (WorkflowTopologyNode curWorkflow: registeredWorkflows)
    {
      DN curDN = curWorkflow.getBaseDN();
      if (baseDN.equals (curDN))
      {
        alreadyRegistered = true;
        break;
      }
    }

    // check done
    return alreadyRegistered;
  }


  /**
   * Returns the list of naming contexts handled by the network group.
   *
   * @return the list of naming contexts
   */
  public NetworkGroupNamingContexts getNamingContexts()
  {
    return namingContexts;
  }


  /**
   * Dumps info from the current network group for debug purpose.
   *
   * @param  leftMargin  white spaces used to indent traces
   * @return a string buffer that contains trace information
   */
  public StringBuffer toString (String leftMargin)
  {
    StringBuffer sb = new StringBuffer();
    String newMargin = leftMargin + "   ";

    sb.append (leftMargin + "Networkgroup (" + networkGroupName+ "\n");
    sb.append (leftMargin + "List of registered workflows:\n");
    for (WorkflowTopologyNode w: registeredWorkflows)
    {
      sb.append (w.toString (newMargin));
    }

    namingContexts.toString (leftMargin);

    sb.append (leftMargin + "rootDSEWorkflow:\n");
    if (rootDSEWorkflow == null)
    {
      sb.append (newMargin + "null\n");
    }
    else
    {
      sb.append (rootDSEWorkflow.toString (newMargin));
    }

    return sb;
  }

}
