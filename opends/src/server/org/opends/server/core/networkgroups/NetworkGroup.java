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
package org.opends.server.core.networkgroups;

import org.opends.messages.Message;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.util.Validator.ensureNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.Collection;

import org.opends.server.admin.std.meta.
        NetworkGroupResourceLimitsCfgDefn.ReferralBindPolicy;
import org.opends.server.admin.std.meta.
        NetworkGroupResourceLimitsCfgDefn.ReferralPolicy;
import org.opends.server.api.ClientConnection;
import org.opends.server.core.*;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;
import org.opends.server.types.operation.PreParseOperation;
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
  private static final String DEFAULT_NETWORK_GROUP_NAME = "default";
  private final boolean isDefaultNetworkGroup;
  private static NetworkGroup defaultNetworkGroup =
      new NetworkGroup (DEFAULT_NETWORK_GROUP_NAME);


  // The admin network group (singleton).
  // The admin network group has no criterion, no policy, and gives
  // access to all the workflows.
  private static final String ADMIN_NETWORK_GROUP_NAME = "admin";
  private final boolean isAdminNetworkGroup;
  private static NetworkGroup adminNetworkGroup =
      new NetworkGroup (ADMIN_NETWORK_GROUP_NAME);


  // The internal network group (singleton).
  // The internal network group has no criterion, no policy, and gives
  // access to all the workflows. The purpose of the internal network
  // group is to allow internal connections to perform operations.
  private static final String INTERNAL_NETWORK_GROUP_NAME = "internal";
  private boolean isInternalNetworkGroup;
  private static NetworkGroup internalNetworkGroup =
      new NetworkGroup(INTERNAL_NETWORK_GROUP_NAME);


  // The list of all network groups that are registered with the server.
  // The defaultNetworkGroup is not in the list of registered network groups.
  private static TreeMap<String, NetworkGroup> registeredNetworkGroups =
      new TreeMap<String, NetworkGroup>();

  // A lock to protect concurrent access to the registeredNetworkGroups.
  private static Object registeredNetworkGroupsLock = new Object();

  // The ordered list of network groups.
  private static List<NetworkGroup> orderedNetworkGroups =
      new ArrayList<NetworkGroup>();


  // The network group internal identifier.
  private String networkGroupID = null;

  // The network group priority
  private int priority = 100;

  // The network group criteria.
  private NetworkGroupCriteria criteria = null;

  // The network group resource limits
  private ResourceLimits resourceLimits = null;

  // The network group request filtering policy
  private RequestFilteringPolicy requestFilteringPolicy = null;

  // The statistics
  private NetworkGroupStatistics stats;

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

    isInternalNetworkGroup = INTERNAL_NETWORK_GROUP_NAME.equals(networkGroupID);
    isAdminNetworkGroup    = ADMIN_NETWORK_GROUP_NAME.equals(networkGroupID);
    isDefaultNetworkGroup  = DEFAULT_NETWORK_GROUP_NAME.equals(networkGroupID);

    stats = new NetworkGroupStatistics(this);
  }


  /**
   * Retrieves the network group ID.
   * @return a string indicating the network group ID
   */
  public String getID() {
    return networkGroupID;
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

      // Insert the network group at the right position in the ordered list
      int index = 0;
      for (NetworkGroup ng : registeredNetworkGroups.values()) {
        if (ng.equals(this)) {
          continue;
        }
        if (this.priority > ng.priority) {
          index++;
        }
      }
      orderedNetworkGroups.add(index, this);
    }
  }


  /**
   * Deregisters the current network group (this) with the server.
   * The method also decrements the reference counter of the workflows
   * so that workflows can be disabled or deleted if needed.
   */
  public void deregister()
  {
    synchronized (registeredNetworkGroupsLock)
    {
      TreeMap<String, NetworkGroup> networkGroups =
        new TreeMap<String, NetworkGroup>(registeredNetworkGroups);
      networkGroups.remove(networkGroupID);
      registeredNetworkGroups = networkGroups;
      orderedNetworkGroups.remove(this);

      // decrement the reference counter of the workflows registered with
      // this network group
      updateWorkflowReferenceCounters();
    }
  }


  /**
   * Decrements the workflow reference counters of all the workflows
   * registered with this network group.
   */
  private void updateWorkflowReferenceCounters()
  {
    synchronized (registeredWorkflowNodesLock)
    {
      for (WorkflowTopologyNode workflowNode: registeredWorkflowNodes.values())
      {
        WorkflowImpl workflowImpl = workflowNode.getWorkflowImpl();
        workflowImpl.decrementReferenceCounter();
      }
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
    // The workflow is registered with no pre/post workflow element.
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
   *          workflow conflicts with the workflow ID of an existing
   *          workflow or if the base DN of the workflow is the same
   *          than the base DN of another workflow already registered
   */
  private void registerWorkflow(
      WorkflowImpl workflow,
      WorkflowElement<?>[] preWorkflowElements,
      WorkflowElement<?>[] postWorkflowElements
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

      // Now that the workflow node has been registered with the network
      // group, update the reference counter of the workflow, unless
      // the network group is either default, or administration, or internal
      // network group.
      if (!isAdminNetworkGroup
          && !isInternalNetworkGroup
          && !isDefaultNetworkGroup)
      {
        workflow.incrementReferenceCounter();
      }
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

    // Now that the workflow node has been deregistered with the network
    // group, update the reference counter of the workflow.
    if ((workflow != null)
        && !isAdminNetworkGroup
        && !isInternalNetworkGroup
        && !isDefaultNetworkGroup)
    {
      WorkflowImpl workflowImpl = (WorkflowImpl) workflow;
      workflowImpl.decrementReferenceCounter();
    }

    return workflow;
  }


  /**
   * Deregisters a workflow with the network group. The workflow to
   * deregister is identified by its workflow ID.
   *
   * @param workflowID the workflow identifier of the workflow to deregister
   * @return the deregistered workflow
   */
  public Workflow deregisterWorkflow(
      String workflowID
      )
  {
    Workflow workflow = null;

    String rootDSEWorkflowID = null;
    if (rootDSEWorkflowNode != null)
    {
      rootDSEWorkflowID = rootDSEWorkflowNode.getWorkflowImpl().getWorkflowId();
    }

    if (workflowID.equalsIgnoreCase(rootDSEWorkflowID))
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
          String curID = node.getWorkflowImpl().getWorkflowId();
          if (curID.equals(workflowID))
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

    // Now that the workflow node has been deregistered with the network
    // group, update the reference counter of the workflow.
    if ((workflow != null)
        && !isAdminNetworkGroup
        && !isInternalNetworkGroup
        && !isDefaultNetworkGroup)
    {
      WorkflowImpl workflowImpl = (WorkflowImpl) workflow;
      workflowImpl.decrementReferenceCounter();
    }

    return workflow;
  }


  /**
   * Deregisters a workflow node with the network group.
   *
   * @param workflow  the workflow node to deregister
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
   * Retrieves the list of registered workflows.
   * @return a list of workflow ids
   */
  public List<String> getRegisteredWorkflows() {
    List<String> workflowIDs = new ArrayList<String>();
    synchronized (registeredWorkflowNodesLock) {
      for (WorkflowTopologyNode node : registeredWorkflowNodes.values()) {
        workflowIDs.add(node.getWorkflowImpl().getWorkflowId());
      }
    }
    return workflowIDs;
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

      // The workflow base DN should not be already present in the
      // network group. Bypass the check for the private workflows...
      checkWorkflowBaseDN(workflowNode);

      // All is fine, let's register the workflow
      TreeMap<String, WorkflowTopologyNode> newRegisteredWorkflowNodes =
        new TreeMap<String, WorkflowTopologyNode>(registeredWorkflowNodes);
      newRegisteredWorkflowNodes.put(workflowID, workflowNode);
      registeredWorkflowNodes = newRegisteredWorkflowNodes;
    }
  }


  /**
   * Checks whether the base DN of a new workflow to register is
   * present in a workflow already registered with the network group.
   *
   * @param workflowNode  the workflow to check
   *
   * @throws  DirectoryException  If the base DN of the workflow is already
   *                              present in the network group
   */
  private void checkWorkflowBaseDN(
      WorkflowTopologyNode workflowNode
      ) throws DirectoryException
  {
    String workflowID = workflowNode.getWorkflowImpl().getWorkflowId();
    ensureNotNull(workflowID);

    // If the network group is the "internal" network group then bypass
    // the check because the internal network group may contain duplicates
    // of base DNs.
    if (isInternalNetworkGroup)
    {
      return;
    }

    // If the network group is the "admin" network group then bypass
    // the check because the internal network group may contain duplicates
    // of base DNs.
    if (isAdminNetworkGroup)
    {
      return;
    }

    // The workflow base DN should not be already present in the
    // network group. Bypass the check for the private workflows...
    for (WorkflowTopologyNode node: registeredWorkflowNodes.values())
    {
      DN nodeBaseDN = node.getBaseDN();
      if (nodeBaseDN.equals(workflowNode.getBaseDN()))
      {
        // The base DN is already registered in the network group,
        // we must reject the registration request
        Message message = ERR_REGISTER_WORKFLOW_BASE_DN_ALREADY_EXISTS.get(
          workflowID,
          networkGroupID,
          node.getWorkflowImpl().getWorkflowId(),
          workflowNode.getWorkflowImpl().getBaseDN().toString());
        throw new DirectoryException(
            ResultCode.UNWILLING_TO_PERFORM, message);
      }
    }
  }


  /**
   * Deregisters the current workflow (this) with the server.
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
   * Adds a connection to the group.
   *
   * @param connection the ClientConnection
   */
  public void addConnection(ClientConnection connection) {
    if (resourceLimits != null) {
      resourceLimits.addConnection(connection);
    }
  }

  /**
   * Removes a connection from the group.
   *
   * @param connection the ClientConnection
   */
  public void removeConnection(ClientConnection connection) {
    if (resourceLimits != null) {
      resourceLimits.removeConnection(connection);
    }
  }

  /**
   *
   * Sets the network group priority.
   *
   * @param prio the network group priority
   */
  public void setNetworkGroupPriority(int prio) {
    // Check whether the priority has changed
    if (priority != prio) {
      synchronized (registeredNetworkGroupsLock)
      {
        priority = prio;

        // Nothing to do if the network group is not registered
        if (registeredNetworkGroups.containsKey(networkGroupID)) {
          // If the network group was already registered, remove it from the
          // ordered list
          orderedNetworkGroups.remove(this);

          // Then insert it at the right position in the ordered list
          int index = 0;
          for (NetworkGroup ng : registeredNetworkGroups.values()) {
            if (ng.equals(this)) {
              continue;
            }
            if (this.priority > ng.priority) {
              index++;
            }
          }
          orderedNetworkGroups.add(index, this);
        }
      }
    }
  }

  /**
   *
   * Sets the network group criteria.
   *
   * @param ngCriteria the criteria
   */
  public void setCriteria(NetworkGroupCriteria ngCriteria) {
    criteria = ngCriteria;
  }

  /**
   * Sets the Resource Limits.
   *
   * @param limits the new resource limits
   */
  public void setResourceLimits(ResourceLimits limits) {
    resourceLimits = limits;
  }


  /**
   * Sets the Request Filtering Policy.
   *
   * @param policy the new request filtering policy
   */
  public void setRequestFilteringPolicy(RequestFilteringPolicy policy) {
    requestFilteringPolicy = policy;
  }

  /**
   * Gets the highest priority matching network group.
   *
   * @param connection the client connection
   * @return matching network group
   */
  public static NetworkGroup findMatchingNetworkGroup(
          ClientConnection connection) {
    for (NetworkGroup ng : getOrderedNetworkGroups()) {
      if (ng.match(connection)) {
        return ng;
      }
    }
    return defaultNetworkGroup;
  }

  /**
   * Gets the highest priority matching network group for a BIND op.
   *
   * @param connection the client connection
   * @param dn the operation bindDN
   * @param authType the operation authentication type
   * @param isSecure a boolean indicating whether the operation is secured
   * @return matching network group
   */
  public static NetworkGroup findBindMatchingNetworkGroup(
          ClientConnection connection, DN dn, AuthenticationType authType,
          boolean isSecure) {
    for (NetworkGroup ng:getOrderedNetworkGroups()) {
      if (ng.matchAfterBind(connection, dn, authType, isSecure)) {
        return ng;
      }
    }
    return defaultNetworkGroup;
  }

  /**
   * Checks whether the connection matches the network group criteria.
   *
   * @param connection  the client connection
   * @return a boolean indicating the match
   */
  private boolean match(ClientConnection connection) {
    if (criteria != null) {
      return (criteria.match(connection));
    }
    return (true);
  }

  /**
   * Checks whether the client connection matches the criteria after bind.
   *
   * @param connection the ClientConnection
   * @param bindDN the DN used to bind
   * @param authType the authentication type
   * @param isSecure a boolean indicating whether the connection is secure
   * @return a boolean indicating whether the connection matches the criteria
   */
  private boolean matchAfterBind(ClientConnection connection, DN bindDN,
          AuthenticationType authType, boolean isSecure) {
    if (criteria != null) {
      return (criteria.matchAfterBind(connection, bindDN, authType, isSecure));
    }
    return (true);
  }


  /**
   * Checks the resource limits.
   *
   * @param connection the client connection
   * @param operation the ongoing operation
   * @param fullCheck a boolean indicating the level of checking: full/partial
   * @param messages the messages indicating the cause of the failure.
   * @return a boolean indicating whether resource limits are exceeded
   */
  public boolean checkResourceLimits(
          ClientConnection connection,
          PreParseOperation operation,
          boolean fullCheck,
          List<Message> messages)
  {
    if (resourceLimits != null) {
      return (resourceLimits.checkLimits(connection, operation,
              fullCheck, messages));
    }
    return (true);
  }

  /**
   * Gets the search size limit, i.e. the maximum number of entries returned
   * by a search.
   * @return the maximum number of entries returned by a search
   */
  public int getSearchSizeLimit() {
    if (resourceLimits != null) {
      return resourceLimits.getSizeLimit();
    }
    return -1;
  }

  /**
   * Gets the search duration limit, i.e. the maximum duration of a search
   * operation.
   * @return the maximum duration in ms of a search operation
   */
  public int getSearchDurationLimit() {
    if (resourceLimits != null) {
      return resourceLimits.getTimeLimit();
    }
    return -1;
  }

  /**
   * Gets the minimum string length of a substring filter in a search
   * operation.
   * @return the minimum substring length
   */
  public int getMinSubstring() {
    if (resourceLimits != null) {
      return resourceLimits.getMinSubstring();
    }
    return 0;
  }

  /**
   * Gets the referral policy. The referral policy defines the behavior
   * when a referral or a search continuation reference is received.
   * The referral can either be discarded (ie an error is returned to the
   * client), forwarded (ie the result is passed as-is to the client) or
   * followed (ie the server contacts the server targeted by the referral to
   * pursue the request).
   * @return the referral policy for this network group
   */
  public ReferralPolicy getReferralPolicy() {
    if (resourceLimits != null) {
      return resourceLimits.getReferralPolicy();
    }
    return ReferralPolicy.FORWARD;
  }

  /**
   * Gets the referral bind policy. The referral bind policy defines
   * the bind credentials used when the server tries to follow a referral. It
   * can either bind to the referred server anonymously, or using the same
   * credentials as in the original request.
   * @return the referral binf policy
   */
  public ReferralBindPolicy getReferralBindPolicy() {
    if (resourceLimits != null) {
      return resourceLimits.getReferralBindPolicy();
    }
    return ReferralBindPolicy.ANONYMOUS;
  }

  /**
   * Gets the referral hop limit. When configured to follow referrals,
   * the request to the referred server can also contain a referral. The hop
   * limit is the maximum number of subsequent operations.
   * @return the referral hop limit
   */
  public int getReferralHopLimit() {
    if (resourceLimits != null) {
      return resourceLimits.getReferralHopLimit();
    }
    return 0;
  }

  /**
   * Checks the request filtering policy.
   * @param operation the operation to be checked
   * @param messages the error messages
   * @return boolean indicating whether the operation conforms to the
   *         network group request filtering policy
   */
  public boolean checkRequestFilteringPolicy(
          PreParseOperation operation,
          List<Message> messages) {
    if (requestFilteringPolicy != null) {
      return requestFilteringPolicy.checkPolicy(operation, messages);
    }
    return true;
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
   * Returns the admin network group.
   * @return the admin network group
   */
  public static NetworkGroup getAdminNetworkGroup()
  {
    return adminNetworkGroup;
  }


  /**
   * Returns the internal network group.
   * @return the internal network group
   */
  public static NetworkGroup getInternalNetworkGroup()
  {
    return internalNetworkGroup;
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
      adminNetworkGroup.invalidate();
      internalNetworkGroup.invalidate();

      registeredNetworkGroups = new TreeMap<String,NetworkGroup>();
      orderedNetworkGroups = new ArrayList<NetworkGroup>();
      defaultNetworkGroup = new NetworkGroup ("default");
      adminNetworkGroup = new NetworkGroup ("admin");
      internalNetworkGroup = new NetworkGroup("internal");
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
   * Provides the ordered list of registered Network groups.
   *
   * @return the ordered list of registered network groups
   */
  private static List<NetworkGroup> getOrderedNetworkGroups()
  {
    return orderedNetworkGroups;
  }


  /**
   * Returns a specific NetworkGroup.
   *
   * @param networkGroupId  the identifier of the requested network group
   * @return the requested NetworkGroup
   */
  public static NetworkGroup getNetworkGroup(String networkGroupId)
  {
    return registeredNetworkGroups.get(networkGroupId);
  }


  /**
   * Resets the configuration of all the registered network groups.
   */
  public static void resetConfig()
  {
    // Reset the default network group
    defaultNetworkGroup.reset();
    adminNetworkGroup.reset();
    internalNetworkGroup.reset();

    // Reset all the registered network group
    synchronized (registeredNetworkGroupsLock)
    {
      registeredNetworkGroups = new TreeMap<String, NetworkGroup>();
      orderedNetworkGroups = new ArrayList<NetworkGroup>();
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

  /**
   * Retrieves the statistics associated to the request filtering policy.
   *
   * @return the statistics associated to the request filtering policy
   */
  public RequestFilteringPolicyStat getRequestFilteringPolicyStat() {
    if (requestFilteringPolicy != null) {
      return requestFilteringPolicy.getStat();
    }
    return null;
  }

  /**
   * Retrieves the statistics associated to the resource limits.
   *
   * @return the statistics associated to the resource limits
   */
  public ResourceLimitsStat getResourceLimitStat() {
    if (resourceLimits != null) {
      return resourceLimits.getStat();
    }
    return null;
  }

  /**
   * Updates the operations statistics.
   * @param message The LDAP message being processed
   */
  public void updateMessageRead(LDAPMessage message) {
    stats.updateMessageRead(message);
  }
}
