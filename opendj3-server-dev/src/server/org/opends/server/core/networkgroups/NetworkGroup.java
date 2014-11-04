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
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.core.networkgroups;

import java.util.TreeMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.RootDseWorkflowTopology;
import org.opends.server.core.Workflow;
import org.opends.server.core.WorkflowImpl;
import org.opends.server.core.WorkflowTopologyNode;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;

import static org.forgerock.util.Reject.*;
import static org.opends.messages.CoreMessages.*;

/**
 * This class defines the network group. A network group is used to
 * categorize client connections. A network group is defined by a set of
 * criteria, a set of policies and a set of workflow nodes. A client
 * connection belongs to a network group whenever it satisfies all the
 * network group criteria. As soon as a client connection belongs to a
 * network group, it has to comply with all the network group policies.
 * Any cleared client operation can be routed to one the network group
 * workflow nodes.
 */
public class NetworkGroup
{

  /**
   * The default network group has no criterion, no policy, and gives
   * access to all the workflows. The purpose of the default network
   * group is to allow new clients to perform a first operation before
   * they can be attached to a specific network group.
   */
  private static final String DEFAULT_NETWORK_GROUP_NAME = "default";
  private static NetworkGroup defaultNetworkGroup =
      new NetworkGroup(DEFAULT_NETWORK_GROUP_NAME);

  /**
   * Deregisters all network groups that have been registered. This
   * should be called when the server is shutting down.
   */
  public static void deregisterAllOnShutdown()
  {
    // Invalidate all NetworkGroups so they cannot accidentally be
    // used after a restart.
    defaultNetworkGroup.invalidate();
    defaultNetworkGroup = new NetworkGroup("default");
  }

  /**
   * Returns the default network group. The default network group is
   * always defined and has no criterion, no policy and provide full
   * access to all the registered workflows.
   *
   * @return the default network group
   */
  public static NetworkGroup getDefaultNetworkGroup()
  {
    return defaultNetworkGroup;
  }

  /** List of naming contexts handled by the network group. */
  private NetworkGroupNamingContexts namingContexts =
      new NetworkGroupNamingContexts();

  /** The network group internal identifier. */
  private final String networkGroupID;

  /**
   * Workflow nodes registered with the current network group.
   * Keys are workflowIDs.
   */
  private TreeMap<String, WorkflowTopologyNode> registeredWorkflowNodes =
      new TreeMap<String, WorkflowTopologyNode>();

  /**
   * A lock to protect concurrent access to the registered Workflow
   * nodes.
   */
  private final Object registeredWorkflowNodesLock = new Object();

  /**
   * The workflow node for the rootDSE entry. The RootDSE workflow node
   * is not stored in the list of registered workflow nodes.
   */
  private RootDseWorkflowTopology rootDSEWorkflowNode;

  /**
   * Creates a new system network group using the provided ID.
   *
   * @param networkGroupID
   *          The network group internal identifier.
   */
  NetworkGroup(String networkGroupID)
  {
    this.networkGroupID = networkGroupID;
  }

  /**
   * Deregisters a workflow with the network group. The workflow to
   * deregister is identified by its baseDN.
   *
   * @param baseDN
   *          the baseDN of the workflow to deregister, may be null
   * @return the deregistered workflow
   */
  public Workflow deregisterWorkflow(DN baseDN)
  {
    Workflow workflow = null;

    if (baseDN == null)
    {
      return workflow;
    }

    if (baseDN.isRootDN())
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
        for (WorkflowTopologyNode node : registeredWorkflowNodes.values())
        {
          DN curDN = node.getBaseDN();
          if (curDN.equals(baseDN))
          {
            // Call deregisterWorkflow() instead of
            // deregisterWorkflowNode() because we want the naming
            // context list to be updated as well.
            deregisterWorkflow(node);
            workflow = node.getWorkflowImpl();

            // Only one workflow can match the baseDN, so we can break
            // the loop here.
            break;
          }
        }
      }
    }

    // Now that the workflow node has been deregistered with the network
    // group, update the reference counter of the workflow.
    if (workflow != null)
    {
      WorkflowImpl workflowImpl = (WorkflowImpl) workflow;
      workflowImpl.decrementReferenceCounter();
    }

    return workflow;
  }

  /**
   * Retrieves the network group ID.
   *
   * @return a string indicating the network group ID
   */
  public String getID()
  {
    return networkGroupID;
  }



  /**
   * Gets the minimum string length of a substring filter in a search
   * operation.
   *
   * @return the minimum substring length
   */
  public int getMinSubstring()
  {
    return 0;
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
   * Gets the search size limit, i.e. the maximum number of entries
   * returned by a search.
   *
   * @return the maximum number of entries returned by a search
   */
  public int getSizeLimit()
  {
    return DirectoryServer.getSizeLimit();
  }



  /**
   * Gets the search duration limit, i.e. the maximum duration of a
   * search operation.
   *
   * @return the maximum duration in ms of a search operation
   */
  public int getTimeLimit()
  {
    return DirectoryServer.getTimeLimit();
  }



  /**
   * Gets the highest workflow in the topology that can handle the
   * baseDN.
   *
   * @param baseDN
   *          the base DN of the request
   * @return the highest workflow in the topology that can handle the
   *         base DN, <code>null</code> if none was found
   */
  public Workflow getWorkflowCandidate(DN baseDN)
  {
    // the top workflow to return
    Workflow workflowCandidate = null;

    // get the list of workflow candidates
    if (baseDN.isRootDN())
    {
      // The rootDSE workflow is the candidate.
      workflowCandidate = rootDSEWorkflowNode;
    }
    else
    {
      // Search the highest workflow in the topology that can handle
      // the baseDN.
      //First search the private workflows
      // The order is important to ensure that the admin network group
      // is not broken and can always find cn=config
      for (WorkflowTopologyNode curWorkflow : namingContexts
          .getPrivateNamingContexts())
      {
        workflowCandidate = curWorkflow.getWorkflowCandidate(baseDN);
        if (workflowCandidate != null)
        {
          break;
        }
      }
      // If not found, search the public
      if (workflowCandidate == null) {
        for (WorkflowTopologyNode curWorkflow : namingContexts
            .getPublicNamingContexts())
        {
          workflowCandidate = curWorkflow.getWorkflowCandidate(baseDN);
          if (workflowCandidate != null)
          {
            break;
          }
        }
      }

    }

    return workflowCandidate;
  }

  /**
   * Checks whether the base DN of a new workflow to register is present
   * in a workflow already registered with the network group.
   *
   * @param workflowNode
   *          the workflow to check
   * @throws DirectoryException
   *           If the base DN of the workflow is already present in the
   *           network group
   */
  private void checkWorkflowBaseDN(WorkflowTopologyNode workflowNode)
      throws DirectoryException
  {
    String workflowID = workflowNode.getWorkflowImpl().getWorkflowId();
    ifNull(workflowID);

    // The workflow base DN should not be already present in the
    // network group. Bypass the check for the private workflows...
    for (WorkflowTopologyNode node : registeredWorkflowNodes.values())
    {
      DN nodeBaseDN = node.getBaseDN();
      if (nodeBaseDN.equals(workflowNode.getBaseDN()))
      {
        // The base DN is already registered in the network group,
        // we must reject the registration request
        LocalizableMessage message =
            ERR_REGISTER_WORKFLOW_BASE_DN_ALREADY_EXISTS.get(
                workflowID, networkGroupID, node.getWorkflowImpl()
                    .getWorkflowId(), workflowNode.getWorkflowImpl().getBaseDN());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }
  }

  /**
   * Deregisters a workflow node with the network group.
   *
   * @param workflow
   *          the workflow node to deregister
   * @return <code>true</code> when the workflow has been successfully
   *         deregistered
   */
  private boolean deregisterWorkflow(Workflow workflow)
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

    return deregistered;
  }



  /**
   * Deregisters the current workflow (this) with the server.
   *
   * @param workflowNode
   *          the workflow node to deregister
   */
  private void deregisterWorkflowNode(WorkflowTopologyNode workflowNode)
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
   * We've seen parts of the server hold references to a NetworkGroup
   * during an in-core server restart. To help detect when this happens,
   * we null out the member variables, so we will fail fast with an NPE
   * if an invalidate NetworkGroup is used.
   */
  private void invalidate()
  {
    namingContexts = null;
    rootDSEWorkflowNode = null;
    registeredWorkflowNodes = null;
  }

  /**
   * Rebuilds the list of naming contexts handled by the network group.
   * This operation should be performed whenever a workflow topology has
   * been updated (workflow registration or de-registration).
   */
  private void rebuildNamingContextList()
  {
    // reset lists of naming contexts
    namingContexts.resetLists();

    // a registered workflow with no parent is a naming context
    for (WorkflowTopologyNode workflowNode : registeredWorkflowNodes.values())
    {
      WorkflowTopologyNode parent = workflowNode.getParent();
      if (parent == null)
      {
        namingContexts.addNamingContext(workflowNode);
      }
    }
  }



  /**
   * Registers a workflow with the network group.
   *
   * @param workflow
   *          the workflow to register
   * @throws DirectoryException
   *           If the workflow ID for the provided workflow conflicts
   *           with the workflow ID of an existing workflow or if the
   *           base DN of the workflow is the same than the base DN of
   *           another workflow already registered
   */
  public void registerWorkflow(WorkflowImpl workflow) throws DirectoryException
  {
    // Is it the rootDSE workflow?
    DN baseDN = workflow.getBaseDN();
    if (baseDN.isRootDN())
    {
      // NOTE - The rootDSE workflow is stored with the
      // registeredWorkflows.
      rootDSEWorkflowNode =
          new RootDseWorkflowTopology(workflow, namingContexts);
    }
    else
    {
      // This workflow is not the rootDSE workflow. Try to insert it in
      // the workflow topology.
      WorkflowTopologyNode workflowNode = new WorkflowTopologyNode(workflow);

      // Register the workflow node with the network group. If the
      // workflow ID is already existing then an exception is raised.
      registerWorkflowNode(workflowNode);

      // Now add the workflow in the workflow topology...
      for (WorkflowTopologyNode curNode : registeredWorkflowNodes
          .values())
      {
        if (
            // Try to insert the new workflow under an existing workflow...
            curNode.insertSubordinate(workflowNode)
            // ... or try to insert the existing workflow below the new workflow
            || workflowNode.insertSubordinate(curNode))
        {
          // new workflow has been inserted in the topology
          continue;
        }
      }

      // Rebuild the list of naming context handled by the network group
      rebuildNamingContextList();

      // Now that the workflow node has been registered with the network
      // group, update the reference counter of the workflow, unless
      // the network group is either default, or administration, or
      // internal network group.
      workflow.incrementReferenceCounter();
    }
  }



  /**
   * Registers a workflow node with the network group.
   *
   * @param workflowNode
   *          the workflow node to register
   * @throws DirectoryException
   *           If the workflow node ID for the provided workflow node
   *           conflicts with the workflow node ID of an existing
   *           workflow node.
   */
  private void registerWorkflowNode(WorkflowTopologyNode workflowNode)
      throws DirectoryException
  {
    String workflowID = workflowNode.getWorkflowImpl().getWorkflowId();
    ifNull(workflowID);

    synchronized (registeredWorkflowNodesLock)
    {
      // The workflow must not be already registered
      if (registeredWorkflowNodes.containsKey(workflowID))
      {
        LocalizableMessage message =
            ERR_REGISTER_WORKFLOW_NODE_ALREADY_EXISTS.get(workflowID,
                networkGroupID);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
            message);
      }

      // The workflow base DN should not be already present in the
      // network group. Bypass the check for the private workflows...
      checkWorkflowBaseDN(workflowNode);

      // All is fine, let's register the workflow
      TreeMap<String, WorkflowTopologyNode> newRegisteredWorkflowNodes =
          new TreeMap<String, WorkflowTopologyNode>(
              registeredWorkflowNodes);
      newRegisteredWorkflowNodes.put(workflowID, workflowNode);
      registeredWorkflowNodes = newRegisteredWorkflowNodes;
    }
  }

}
