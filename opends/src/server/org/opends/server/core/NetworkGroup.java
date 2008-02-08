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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */
package org.opends.server.core;
import org.opends.messages.Message;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.Validator.ensureNotNull;

import java.util.TreeMap;
import java.util.Collection;

import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;
import org.opends.server.workflowelement.WorkflowElement;


/**
 * This class defines the network group. A network group is used to categorize
 * client connections. A network group is defined by a set of criteria, a
 * set of policies and a set of workflow nodes. A client connection belongs to
 * a network group whenever it satisfies all the network group criteria. As
 * soon as a client connection belongs to a network group, it has to comply
 * with all the network group policies. Any cleared client operation can be
 * routed to one the network group workflow nodes.
 */
public class NetworkGroup
{
  // Workflow nodes registered with the current network group.
  // Keys are workflowIDs.
  private TreeMap<String, WorkflowTopologyNode> registeredWorkflowNodes =
      new TreeMap<String, WorkflowTopologyNode>();


  // A lock to protect concurrent access to the registered Workflow nodes.
  private Object registeredWorkflowNodesLock = new Object();


  // The workflow node for the rootDSE entry. The RootDSE workflow node
  // is not stored in the list of registered workflow nodes.
  private RootDseWorkflowTopology rootDSEWorkflowNode = null;


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


  // The list of all network groups that are registered with the server.
  // The defaultNetworkGroup is not in the list of registered network groups.
  private static TreeMap<String, NetworkGroup> registeredNetworkGroups =
      new TreeMap<String, NetworkGroup>();


  // A lock to protect concurrent access to the registeredNetworkGroups.
  private static Object registeredNetworkGroupsLock = new Object();


  // The network group internal identifier.
  private String networkGroupID = null;



  /**
   * Creates a new instance of the network group.
   *
   * @param networkGroupID  the network group internal identifier
   */
  public NetworkGroup(
      String networkGroupID
      )
  {
    this.networkGroupID = networkGroupID;
  }


  /**
   * Performs any finalization that might be required when this
   * network group is unloaded.  No action is taken in the
   * default implementation.
   */
  public void finalizeNetworkGroup()
  {
    // No action is required by default.
  }


  /**
   * Registers the current network group (this) with the server.
   *
   * @throws  DirectoryException  If the network group ID for the provided
   *                              network group conflicts with the network
   *                              group ID of an existing network group.
   */
  public void register()
      throws DirectoryException
  {
    ensureNotNull(networkGroupID);

    synchronized (registeredNetworkGroupsLock)
    {
      // The network group must not be already registered
      if (registeredNetworkGroups.containsKey(networkGroupID))
      {
        Message message = ERR_REGISTER_NETWORK_GROUP_ALREADY_EXISTS.get(
                          networkGroupID);
        throw new DirectoryException(
            ResultCode.UNWILLING_TO_PERFORM, message);
      }

      TreeMap<String, NetworkGroup> newRegisteredNetworkGroups =
        new TreeMap<String, NetworkGroup>(registeredNetworkGroups);
      newRegisteredNetworkGroups.put(networkGroupID, this);
      registeredNetworkGroups = newRegisteredNetworkGroups;
    }
  }


  /**
   * Deregisters the current network group (this) with the server.
   */
  public void deregister()
  {
    synchronized (registeredNetworkGroupsLock)
    {
      TreeMap<String, NetworkGroup> networkGroups =
        new TreeMap<String, NetworkGroup>(registeredNetworkGroups);
      networkGroups.remove(networkGroupID);
      registeredNetworkGroups = networkGroups;
    }
  }


  /**
   * Registers a workflow with the network group.
   *
   * @param workflow  the workflow to register
   *
   * @throws  DirectoryException  If the workflow ID for the provided
   *                              workflow conflicts with the workflow
   *                              ID of an existing workflow.
   */
  public void registerWorkflow(
      WorkflowImpl workflow
      ) throws DirectoryException
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
   *
   * @throws  DirectoryException  If the workflow ID for the provided
   *                              workflow conflicts with the workflow
   *                              ID of an existing workflow.
   */
  private void registerWorkflow(
      WorkflowImpl workflow,
      WorkflowElement[] preWorkflowElements,
      WorkflowElement[] postWorkflowElements
      ) throws DirectoryException
  {
    // Is it the rootDSE workflow?
    DN baseDN = workflow.getBaseDN();
    if (baseDN.isNullDN())
    {
      // NOTE - The rootDSE workflow is stored with the registeredWorkflows.
      rootDSEWorkflowNode =
        new RootDseWorkflowTopology(workflow, namingContexts);
    }
    else
    {
      // This workflow is not the rootDSE workflow. Try to insert it in the
      // workflow topology.
      WorkflowTopologyNode workflowNode = new WorkflowTopologyNode(
          workflow, preWorkflowElements, postWorkflowElements);

      // Register the workflow node with the network group. If the workflow
      // ID is already existing then an exception is raised.
      registerWorkflowNode(workflowNode);

      // Now add the workflow in the workflow topology...
      for (WorkflowTopologyNode curNode: registeredWorkflowNodes.values())
      {
        // Try to insert the new workflow under an existing workflow...
        if (curNode.insertSubordinate(workflowNode))
        {
          // new workflow has been inserted in the topology
          continue;
        }

        // ... or try to insert the existing workflow below the new
        // workflow
        if (workflowNode.insertSubordinate(curNode))
        {
          // new workflow has been inserted in the topology
          continue;
        }
      }

      // Rebuild the list of naming context handled by the network group
      rebuildNamingContextList();
    }
  }


  /**
   * Deregisters a workflow with the network group. The workflow to
   * deregister is identified by its baseDN.
   *
   * @param baseDN  the baseDN of the workflow to deregister, may be null
   *
   * @return the deregistered workflow
   */
  public Workflow deregisterWorkflow(
      DN baseDN
      )
  {
    Workflow workflow = null;

    if (baseDN == null)
    {
      return workflow;
    }

    if (baseDN.isNullDN())
    {
      // deregister the rootDSE
      deregisterWorkflow(rootDSEWorkflowNode);
      workflow = rootDSEWorkflowNode.getWorkflowImpl();
    }
    else
    {
      // deregister a workflow node
      synchronized (registeredWorkflowNodesLock)
      {
        for (WorkflowTopologyNode node: registeredWorkflowNodes.values())
        {
          DN curDN = node.getBaseDN();
          if (curDN.equals(baseDN))
          {
            // Call deregisterWorkflow() instead of deregisterWorkflowNode()
            // because we want the naming context list to be updated as well.
            deregisterWorkflow(node);
            workflow = node.getWorkflowImpl();

            // Only one workflow can match the baseDN, so we can break
            // the loop here.
            break;
          }
        }
      }
    }

    return workflow;
  }


  /**
   * Deregisters a workflow with the network group. The workflow to
   * deregister is identified by its workflow ID.
   *
   * @param workflowID the workflow identifier of the workflow to deregister
   */
  public void deregisterWorkflow(
      String workflowID
      )
  {
    String rootDSEWorkflowID = null;
    if (rootDSEWorkflowNode != null)
    {
      rootDSEWorkflowID = rootDSEWorkflowNode.getWorkflowImpl().getWorkflowId();
    }

    if (workflowID.equalsIgnoreCase(rootDSEWorkflowID))
    {
      // deregister the rootDSE
      deregisterWorkflow(rootDSEWorkflowNode);
    }
    else
    {
      // deregister a workflow node
      synchronized (registeredWorkflowNodesLock)
      {
        for (WorkflowTopologyNode node: registeredWorkflowNodes.values())
        {
          String curID = node.getWorkflowImpl().getWorkflowId();
          if (curID.equals(workflowID))
          {
            // Call deregisterWorkflow() instead of deregisterWorkflowNode()
            // because we want the naming context list to be updated as well.
            deregisterWorkflow(node);

            // Only one workflow can match the baseDN, so we can break
            // the loop here.
            break;
          }
        }
      }
    }
  }


  /**
   * Deregisters a workflow node with the network group.
   *
   * @param workflow  the workflow node to deregister
   */
  private void deregisterWorkflow(Workflow workflow)
  {
    // true as soon as the workflow has been deregistered
    boolean deregistered = false;

    // Is it the rootDSE workflow?
    if (workflow == rootDSEWorkflowNode)
    {
      rootDSEWorkflowNode = null;
      deregistered = true;
    }
    else
    {
      // Deregister the workflow with the network group.
      WorkflowTopologyNode workflowNode = (WorkflowTopologyNode) workflow;
      deregisterWorkflowNode(workflowNode);
      deregistered = true;

      // The workflow to deregister is not the root DSE workflow.
      // Remove it from the workflow topology.
      workflowNode.remove();

      // Rebuild the list of naming context handled by the network group
      rebuildNamingContextList();
    }

    // If the workflow has been deregistered then deregister it with
    // the default network group as well
    if (deregistered && (this != defaultNetworkGroup))
    {
      defaultNetworkGroup.deregisterWorkflow(workflow);
    }
  }


  /**
   * Registers a workflow node with the network group.
   *
   * @param workflowNode  the workflow node to register
   *
   * @throws  DirectoryException  If the workflow node ID for the provided
   *                              workflow node conflicts with the workflow
   *                              node ID of an existing workflow node.
   */
  private void registerWorkflowNode(
      WorkflowTopologyNode workflowNode
      ) throws DirectoryException
  {
    String workflowID = workflowNode.getWorkflowImpl().getWorkflowId();
    ensureNotNull(workflowID);

    synchronized (registeredWorkflowNodesLock)
    {
      // The workflow must not be already registered
      if (registeredWorkflowNodes.containsKey(workflowID))
      {
        Message message = ERR_REGISTER_WORKFLOW_NODE_ALREADY_EXISTS.get(
          workflowID, networkGroupID);
        throw new DirectoryException(
            ResultCode.UNWILLING_TO_PERFORM, message);
      }

      TreeMap<String, WorkflowTopologyNode> newRegisteredWorkflowNodes =
        new TreeMap<String, WorkflowTopologyNode>(registeredWorkflowNodes);
      newRegisteredWorkflowNodes.put(workflowID, workflowNode);
      registeredWorkflowNodes = newRegisteredWorkflowNodes;
    }
  }


  /**
   * Deregisters the current worklow (this) with the server.
   *
   * @param workflowNode  the workflow node to deregister
   */
  private void deregisterWorkflowNode(
      WorkflowTopologyNode workflowNode
      )
  {
    synchronized (registeredWorkflowNodesLock)
    {
      TreeMap<String, WorkflowTopologyNode> newWorkflowNodes =
        new TreeMap<String, WorkflowTopologyNode>(registeredWorkflowNodes);
      newWorkflowNodes.remove(workflowNode.getWorkflowImpl().getWorkflowId());
      registeredWorkflowNodes = newWorkflowNodes;
    }
  }


  /**
   * Gets the highest workflow in the topology that can handle the baseDN.
   *
   * @param baseDN  the base DN of the request
   * @return the highest workflow in the topology that can handle the base DN,
   *         <code>null</code> if none was found
   */
  public Workflow getWorkflowCandidate(
      DN baseDN
      )
  {
    // the top workflow to return
    Workflow workflowCandidate = null;

    // get the list of workflow candidates
    if (baseDN.isNullDN())
    {
      // The rootDSE workflow is the candidate.
      workflowCandidate = rootDSEWorkflowNode;
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
    for (WorkflowTopologyNode workflowNode: registeredWorkflowNodes.values())
    {
      WorkflowTopologyNode parent = workflowNode.getParent();
      if (parent == null)
      {
        namingContexts.addNamingContext (workflowNode);
      }
    }
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
  public StringBuilder toString(String leftMargin)
  {
    StringBuilder sb = new StringBuilder();
    String newMargin = leftMargin + "   ";

    sb.append (leftMargin + "Networkgroup (" + networkGroupID+ "\n");
    sb.append (leftMargin + "List of registered workflows:\n");
    for (WorkflowTopologyNode node: registeredWorkflowNodes.values())
    {
      sb.append (node.toString (newMargin));
    }

    namingContexts.toString (leftMargin);

    sb.append (leftMargin + "rootDSEWorkflow:\n");
    if (rootDSEWorkflowNode == null)
    {
      sb.append (newMargin + "null\n");
    }
    else
    {
      sb.append (rootDSEWorkflowNode.toString (newMargin));
    }

    return sb;
  }


  /**
   * Deregisters all network groups that have been registered.  This should be
   * called when the server is shutting down.
   */
  public static void deregisterAllOnShutdown()
  {
    synchronized (registeredNetworkGroupsLock)
    {
      // Invalidate all NetworkGroups so they cannot accidentally be used
      // after a restart.
      Collection<NetworkGroup> networkGroups = registeredNetworkGroups.values();
      for (NetworkGroup networkGroup: networkGroups)
      {
        networkGroup.invalidate();
      }
      defaultNetworkGroup.invalidate();

      registeredNetworkGroups = new TreeMap<String,NetworkGroup>();
      defaultNetworkGroup = new NetworkGroup ("default");
    }
  }

  /**
   * We've seen parts of the server hold references to a NetworkGroup
   * during an in-core server restart.  To help detect when this happens,
   * we null out the member variables, so we will fail fast with an NPE if an
   * invalidate NetworkGroup is used.
   */
  private void invalidate()
  {
    namingContexts = null;
    networkGroupID = null;
    rootDSEWorkflowNode = null;
    registeredWorkflowNodes = null;
  }


  /**
   * Provides the list of network group registered with the server.
   *
   * @return the list of registered network groups
   */
  public static Collection<NetworkGroup> getRegisteredNetworkGroups()
  {
    return registeredNetworkGroups.values();
  }


  /**
   * Resets the configuration of all the registered network groups.
   */
  public static void resetConfig()
  {
    // Reset the default network group
    defaultNetworkGroup.reset();

    // Reset all the registered network group
    synchronized (registeredNetworkGroupsLock)
    {
      registeredNetworkGroups = new TreeMap<String, NetworkGroup>();
    }
  }


  /**
   * Resets the configuration of the current network group.
   */
  public void reset()
  {
    synchronized (registeredWorkflowNodesLock)
    {
      registeredWorkflowNodes = new TreeMap<String, WorkflowTopologyNode>();
      rootDSEWorkflowNode = null;
      namingContexts = new NetworkGroupNamingContexts();
    }
  }
}
