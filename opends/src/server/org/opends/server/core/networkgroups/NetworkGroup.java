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
 *      Copyright 2007-2009 Sun Microsystems, Inc.
 */
package org.opends.server.core.networkgroups;



import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.CoreMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.util.Validator.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.opends.messages.Message;
import org.opends.server.admin.ClassPropertyDefinition;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.meta.QOSPolicyCfgDefn;
import org.opends.server.admin.std.server.NetworkGroupCfg;
import org.opends.server.admin.std.server.QOSPolicyCfg;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.QOSPolicy;
import org.opends.server.api.QOSPolicyFactory;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.RootDseWorkflowTopology;
import org.opends.server.core.Workflow;
import org.opends.server.core.WorkflowImpl;
import org.opends.server.core.WorkflowTopologyNode;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.types.AuthenticationType;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.types.operation.PreParseOperation;
import org.opends.server.workflowelement.WorkflowElement;



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
   * Configuration change listener for user network groups.
   */
  private final class ChangeListener implements
      ConfigurationChangeListener<NetworkGroupCfg>
  {

    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationChange(
        NetworkGroupCfg configuration)
    {
      ResultCode resultCode = ResultCode.SUCCESS;
      boolean adminActionRequired = false;
      List<Message> messages = new ArrayList<Message>();

      // Update the priority.
      setNetworkGroupPriority(configuration.getPriority());

      // Deregister any workflows that have been removed.
      SortedSet<String> configWorkflows = configuration.getWorkflow();
      for (String id : getRegisteredWorkflows())
      {
        if (!configWorkflows.contains(id))
        {
          deregisterWorkflow(id);
        }
      }

      // Register any workflows that have been added.
      List<String> ngWorkflows = getRegisteredWorkflows();
      for (String id : configuration.getWorkflow())
      {
        if (!ngWorkflows.contains(id))
        {
          WorkflowImpl workflowImpl =
              (WorkflowImpl) WorkflowImpl.getWorkflow(id);
          try
          {
            registerWorkflow(workflowImpl);
          }
          catch (DirectoryException e)
          {
            if (resultCode == ResultCode.SUCCESS)
            {
              resultCode = e.getResultCode();
            }
            messages.add(e.getMessageObject());
          }
        }
      }

      try
      {
        criteria = decodeConnectionCriteriaConfiguration(configuration);
      }
      catch (ConfigException e)
      {
        resultCode = DirectoryServer.getServerErrorResultCode();
        messages.add(e.getMessageObject());
      }

      // Update the configuration.
      NetworkGroup.this.configuration = configuration;

      return new ConfigChangeResult(resultCode, adminActionRequired,
          messages);
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationChangeAcceptable(
        NetworkGroupCfg configuration, List<Message> unacceptableReasons)
    {
      return isConfigurationAcceptable(configuration,
          unacceptableReasons);
    }

  }

  /**
   * Configuration change listener for user network group QOS policies.
   */
  private final class QOSPolicyListener implements
      ConfigurationAddListener<QOSPolicyCfg>,
      ConfigurationDeleteListener<QOSPolicyCfg>
  {

    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationAdd(
        QOSPolicyCfg configuration)
    {
      ResultCode resultCode = ResultCode.SUCCESS;
      boolean adminActionRequired = false;
      List<Message> messages = new ArrayList<Message>();

      try
      {
        createNetworkGroupQOSPolicy(configuration);
      }
      catch (ConfigException e)
      {
        messages.add(e.getMessageObject());
        resultCode = DirectoryServer.getServerErrorResultCode();
      }
      catch (InitializationException e)
      {
        messages.add(e.getMessageObject());
        resultCode = DirectoryServer.getServerErrorResultCode();
      }

      return new ConfigChangeResult(resultCode, adminActionRequired,
          messages);
    }



    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationDelete(
        QOSPolicyCfg configuration)
    {
      QOSPolicy policy = policies.remove(configuration.dn());

      if (policy != null)
      {
        if (requestFilteringPolicy == policy)
        {
          requestFilteringPolicy = null;
        }
        else if (resourceLimitsPolicy == policy)
        {
          resourceLimitsPolicy = null;
        }

        policy.finalizeQOSPolicy();
      }

      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationAddAcceptable(
        QOSPolicyCfg configuration, List<Message> unacceptableReasons)
    {
      return isNetworkGroupQOSPolicyConfigurationAcceptable(
          configuration, unacceptableReasons);
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationDeleteAcceptable(
        QOSPolicyCfg configuration, List<Message> unacceptableReasons)
    {
      // Always ok.
      return true;
    }

  }



  // The admin network group has no criterion, no policy,
  // and gives access to all the workflows.
  private static final String ADMIN_NETWORK_GROUP_NAME = "admin";

  private static NetworkGroup adminNetworkGroup =
      new NetworkGroup(ADMIN_NETWORK_GROUP_NAME);

  // The default network group has no criterion, no policy, and gives
  // access to all the workflows. The purpose of the default network
  // group is to allow new clients to perform a first operation before
  // they can be attached to a specific network group.
  private static final String DEFAULT_NETWORK_GROUP_NAME = "default";

  private static NetworkGroup defaultNetworkGroup =
      new NetworkGroup(DEFAULT_NETWORK_GROUP_NAME);

  // The internal network group has no criterion, no policy, and gives
  // access to all the workflows. The purpose of the internal network
  // group is to allow internal connections to perform operations.
  private static final String INTERNAL_NETWORK_GROUP_NAME = "internal";
  private static NetworkGroup internalNetworkGroup =
      new NetworkGroup(INTERNAL_NETWORK_GROUP_NAME);

  // The ordered list of network groups.
  private static List<NetworkGroup> orderedNetworkGroups =
      new ArrayList<NetworkGroup>();

  // The list of all network groups that are registered with the server.
  // The defaultNetworkGroup is not in the list of registered network
  // groups.
  private static TreeMap<String, NetworkGroup> registeredNetworkGroups =
      new TreeMap<String, NetworkGroup>();

  // A lock to protect concurrent access to the registeredNetworkGroups.
  private static Object registeredNetworkGroupsLock = new Object();

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  /**
   * Deregisters all network groups that have been registered. This
   * should be called when the server is shutting down.
   */
  public static void deregisterAllOnShutdown()
  {
    synchronized (registeredNetworkGroupsLock)
    {
      // Invalidate all NetworkGroups so they cannot accidentally be
      // used after a restart.
      Collection<NetworkGroup> networkGroups =
          registeredNetworkGroups.values();
      for (NetworkGroup networkGroup : networkGroups)
      {
        networkGroup.invalidate();
      }
      defaultNetworkGroup.invalidate();
      adminNetworkGroup.invalidate();
      internalNetworkGroup.invalidate();

      registeredNetworkGroups = new TreeMap<String, NetworkGroup>();
      orderedNetworkGroups = new ArrayList<NetworkGroup>();
      defaultNetworkGroup = new NetworkGroup("default");
      adminNetworkGroup = new NetworkGroup("admin");
      internalNetworkGroup = new NetworkGroup("internal");
    }
  }



  /**
   * Gets the highest priority matching network group for a BIND op.
   *
   * @param connection
   *          the client connection
   * @param dn
   *          the operation bindDN
   * @param authType
   *          the operation authentication type
   * @param isSecure
   *          a boolean indicating whether the operation is secured
   * @return matching network group
   */
  static NetworkGroup findBindMatchingNetworkGroup(
      ClientConnection connection, DN dn, AuthenticationType authType,
      boolean isSecure)
  {
    for (NetworkGroup ng : orderedNetworkGroups)
    {
      if (ng.matchAfterBind(connection, dn, authType, isSecure))
      {
        return ng;
      }
    }
    return defaultNetworkGroup;
  }



  /**
   * Gets the highest priority matching network group.
   *
   * @param connection
   *          the client connection
   * @return matching network group
   */
  static NetworkGroup findMatchingNetworkGroup(
      ClientConnection connection)
  {
    for (NetworkGroup ng : orderedNetworkGroups)
    {
      if (ng.match(connection))
      {
        return ng;
      }
    }
    return defaultNetworkGroup;
  }



  /**
   * Returns the admin network group.
   *
   * @return the admin network group
   */
  public static NetworkGroup getAdminNetworkGroup()
  {
    return adminNetworkGroup;
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



  /**
   * Returns the internal network group.
   *
   * @return the internal network group
   */
  public static NetworkGroup getInternalNetworkGroup()
  {
    return internalNetworkGroup;
  }



  /**
   * Gets the network group having the specified ID.
   * <p>
   * This method is for testing only.
   *
   * @param networkGroupID
   *          The network group ID.
   * @return The network group, of <code>null</code> if no match was found.
   */
  public static NetworkGroup getNetworkGroup(String networkGroupID)
  {
    return registeredNetworkGroups.get(networkGroupID);
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
   * Initializes this network group as a user network group using the
   * provided configuration. The network group will monitor the
   * configuration and update its configuration when necessary.
   *
   * @param configuration
   *          The network group configuration.
   * @return The new user network group.
   * @throws ConfigException
   *           If an unrecoverable problem arises during initialization
   *           of the user network group as a result of the server
   *           configuration.
   * @throws InitializationException
   *           If a problem occurs during initialization of the user
   *           network group that is not related to the server
   *           configuration.
   */
  static NetworkGroup createUserNetworkGroup(
      NetworkGroupCfg configuration) throws InitializationException,
      ConfigException
  {
    NetworkGroup networkGroup = new NetworkGroup(configuration);

    try
    {
      // Set the priority.
      networkGroup.priority = configuration.getPriority();

      // Initialize the network group criteria.
      networkGroup.criteria =
          decodeConnectionCriteriaConfiguration(configuration);

      // Initialize the network group policies.
      for (String policyName : configuration
          .listNetworkGroupQOSPolicies())
      {
        QOSPolicyCfg policyConfiguration =
            configuration.getNetworkGroupQOSPolicy(policyName);
        networkGroup.createNetworkGroupQOSPolicy(policyConfiguration);
      }

      // Register the root DSE workflow with the network group.
      WorkflowImpl rootDSEworkflow =
          (WorkflowImpl) WorkflowImpl.getWorkflow("__root.dse__#");
      networkGroup.registerWorkflow(rootDSEworkflow);

      // Register the workflows with the network group.
      for (String workflowID : configuration.getWorkflow())
      {
        WorkflowImpl workflowImpl =
            (WorkflowImpl) WorkflowImpl.getWorkflow(workflowID);

        if (workflowImpl == null)
        {
          // The workflow does not exist, log an error message
          // and skip the workflow.
          Message message =
              INFO_ERR_WORKFLOW_DOES_NOT_EXIST.get(workflowID,
                  networkGroup.getID());
          logError(message);
        }
        else
        {
          networkGroup.registerWorkflow(workflowImpl);
        }
      }

      // Register all configuration change listeners.
      configuration.addChangeListener(networkGroup.changeListener);
      configuration
          .addNetworkGroupQOSPolicyAddListener(networkGroup.policyListener);
      configuration
          .addNetworkGroupQOSPolicyDeleteListener(networkGroup.policyListener);

      // Register the network group with the server.
      networkGroup.register();
    }
    catch (DirectoryException e)
    {
      networkGroup.finalizeNetworkGroup();
      throw new InitializationException(e.getMessageObject());
    }
    catch (InitializationException e)
    {
      networkGroup.finalizeNetworkGroup();
      throw e;
    }
    catch (ConfigException e)
    {
      networkGroup.finalizeNetworkGroup();
      throw e;
    }

    return networkGroup;
  }



  /**
   * Indicates whether the provided network group configuration is
   * acceptable.
   *
   * @param configuration
   *          The network group configuration.
   * @param unacceptableReasons
   *          A list that can be used to hold messages about why the
   *          provided configuration is not acceptable.
   * @return Returns <code>true</code> if the provided network group
   *         configuration is acceptable, or <code>false</code> if it is
   *         not.
   */
  static boolean isConfigurationAcceptable(
      NetworkGroupCfg configuration, List<Message> unacceptableReasons)
  {
    // The configuration is always acceptable if disabled.
    if (!configuration.isEnabled())
    {
      return true;
    }

    // Check that all the workflows in the network group have a
    // different base DN.
    boolean isAcceptable = true;

    Set<String> allBaseDNs = new HashSet<String>();
    for (String workflowId : configuration.getWorkflow())
    {
      WorkflowImpl workflow =
          (WorkflowImpl) WorkflowImpl.getWorkflow(workflowId);
      String baseDN = workflow.getBaseDN().toNormalizedString();
      if (allBaseDNs.contains(baseDN))
      {
        // This baseDN is duplicated
        Message message =
            ERR_WORKFLOW_BASE_DN_DUPLICATED_IN_NG.get(baseDN,
                getNameFromConfiguration(configuration));
        unacceptableReasons.add(message);
        isAcceptable = false;
        break;
      }
      else
      {
        allBaseDNs.add(baseDN);
      }
    }

    // Validate any policy configurations.
    for (String policyName : configuration
        .listNetworkGroupQOSPolicies())
    {
      try
      {
        QOSPolicyCfg policyCfg =
            configuration.getNetworkGroupQOSPolicy(policyName);
        if (!isNetworkGroupQOSPolicyConfigurationAcceptable(policyCfg,
            unacceptableReasons))
        {
          isAcceptable = false;
        }
      }
      catch (ConfigException e)
      {
        // This is bad - give up immediately.
        unacceptableReasons.add(e.getMessageObject());
        return false;
      }
    }

    // The bind DN patterns may be malformed.
    if (!configuration.getAllowedBindDN().isEmpty())
    {
      try
      {
        BindDNConnectionCriteria.decode(configuration
            .getAllowedBindDN());
      }
      catch (DirectoryException e)
      {
        unacceptableReasons.add(e.getMessageObject());
        isAcceptable = false;
      }
    }

    return isAcceptable;
  }



  // Decodes connection criteria configuration.
  private static ConnectionCriteria decodeConnectionCriteriaConfiguration(
      NetworkGroupCfg configuration) throws ConfigException
  {
    List<ConnectionCriteria> filters =
        new LinkedList<ConnectionCriteria>();

    if (!configuration.getAllowedAuthMethod().isEmpty())
    {
      filters.add(new AuthMethodConnectionCriteria(configuration
          .getAllowedAuthMethod()));
    }

    if (!configuration.getAllowedBindDN().isEmpty())
    {
      try
      {
        filters.add(BindDNConnectionCriteria.decode(configuration
            .getAllowedBindDN()));
      }
      catch (DirectoryException e)
      {
        throw new ConfigException(e.getMessageObject());
      }
    }

    if (!configuration.getAllowedClient().isEmpty()
        || !configuration.getDeniedClient().isEmpty())
    {
      filters.add(new IPConnectionCriteria(configuration
          .getAllowedClient(), configuration.getDeniedClient()));
    }

    if (!configuration.getAllowedProtocol().isEmpty())
    {
      filters.add(new ProtocolConnectionCriteria(configuration
          .getAllowedProtocol()));
    }

    if (configuration.isIsSecurityMandatory())
    {
      filters.add(SecurityConnectionCriteria.SECURITY_REQUIRED);
    }

    if (filters.isEmpty())
    {
      return ConnectionCriteria.TRUE;
    }
    else
    {
      return new ANDConnectionCriteria(filters);
    }
  }



  /**
   * Gets the name of the network group configuration.
   *
   * @param configuration
   *          The configuration.
   * @return The network group name.
   */
  private static String getNameFromConfiguration(NetworkGroupCfg configuration)
  {
    DN dn = configuration.dn();
    return dn.getRDN().getAttributeValue(0).toString();
  }



  // Determines whether or not the new network group configuration's
  // implementation class is acceptable.
  private static boolean isNetworkGroupQOSPolicyConfigurationAcceptable(
      QOSPolicyCfg policyConfiguration,
      List<Message> unacceptableReasons)
  {
    String className = policyConfiguration.getJavaClass();
    QOSPolicyCfgDefn d = QOSPolicyCfgDefn.getInstance();
    ClassPropertyDefinition pd = d.getJavaClassPropertyDefinition();

    // Validate the configuration.
    try
    {
      // Load the class and cast it to a network group policy factory.
      Class<? extends QOSPolicyFactory> theClass;
      QOSPolicyFactory factory;

      theClass = pd.loadClass(className, QOSPolicyFactory.class);
      factory = theClass.newInstance();

      // Determine the initialization method to use: it must take a
      // single parameter which is the exact type of the configuration
      // object.
      Method method =
          theClass.getMethod("isConfigurationAcceptable",
              QOSPolicyCfg.class, List.class);
      Boolean acceptable =
          (Boolean) method.invoke(factory, policyConfiguration,
              unacceptableReasons);

      if (!acceptable)
      {
        return false;
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      unacceptableReasons
          .add(ERR_CONFIG_NETWORK_GROUP_POLICY_CANNOT_INITIALIZE.get(
              String.valueOf(className), String
                  .valueOf(policyConfiguration.dn()),
              stackTraceToSingleLineString(e)));
      return false;
    }

    // The configuration is valid as far as we can tell.
    return true;
  }



  // Change listener (active for user network groups).
  private final ChangeListener changeListener;

  // Current configuration (active for user network groups).
  private NetworkGroupCfg configuration = null;

  // The network group connection criteria.
  private ConnectionCriteria criteria = ConnectionCriteria.TRUE;

  private final boolean isAdminNetworkGroup;

  private final boolean isDefaultNetworkGroup;

  private final boolean isInternalNetworkGroup;

  // List of naming contexts handled by the network group.
  private NetworkGroupNamingContexts namingContexts =
      new NetworkGroupNamingContexts();

  // The network group internal identifier.
  private final String networkGroupID;

  // All network group policies mapping factory class name to policy.
  private final Map<DN, QOSPolicy> policies =
      new ConcurrentHashMap<DN, QOSPolicy>();

  // Add/delete policy listener (active for user network groups).
  private final QOSPolicyListener policyListener;

  // The network group priority.
  private int priority = 100;

  // Workflow nodes registered with the current network group.
  // Keys are workflowIDs.
  private TreeMap<String, WorkflowTopologyNode> registeredWorkflowNodes =
      new TreeMap<String, WorkflowTopologyNode>();

  // A lock to protect concurrent access to the registered Workflow
  // nodes.
  private final Object registeredWorkflowNodesLock = new Object();

  // The network group request filtering policy.
  private RequestFilteringPolicy requestFilteringPolicy = null;

  // The network group resource limits policy.
  private ResourceLimitsPolicy resourceLimitsPolicy = null;

  // The workflow node for the rootDSE entry. The RootDSE workflow node
  // is not stored in the list of registered workflow nodes.
  private RootDseWorkflowTopology rootDSEWorkflowNode = null;

  // The network group statistics.
  private final NetworkGroupStatistics statistics;



  /**
   * Creates a new system network group using the provided ID.
   *
   * @param networkGroupID
   *          The network group internal identifier.
   */
  public NetworkGroup(String networkGroupID)
  {
    this.networkGroupID = networkGroupID;
    this.isInternalNetworkGroup =
        INTERNAL_NETWORK_GROUP_NAME.equals(networkGroupID);
    this.isAdminNetworkGroup =
        ADMIN_NETWORK_GROUP_NAME.equals(networkGroupID);
    this.isDefaultNetworkGroup =
        DEFAULT_NETWORK_GROUP_NAME.equals(networkGroupID);
    this.statistics = new NetworkGroupStatistics(this);
    this.configuration = null;
    this.changeListener = null;
    this.policyListener = null;
  }



  /**
   * Creates a new user network group using the provided configuration.
   */
  private NetworkGroup(NetworkGroupCfg configuration)
  {
    this.networkGroupID = getNameFromConfiguration(configuration);
    this.isInternalNetworkGroup = false;
    this.isAdminNetworkGroup = false;
    this.isDefaultNetworkGroup = false;
    this.statistics = new NetworkGroupStatistics(this);
    this.configuration = configuration;
    this.changeListener = new ChangeListener();
    this.policyListener = new QOSPolicyListener();
  }



  /**
   * Adds a connection to the group.
   *
   * @param connection
   *          the ClientConnection
   */
  public void addConnection(ClientConnection connection)
  {
    if (resourceLimitsPolicy != null)
    {
      resourceLimitsPolicy.addConnection(connection);
    }
  }



  /**
   * Checks the request filtering policy.
   *
   * @param operation
   *          the operation to be checked
   * @param messages
   *          the error messages
   * @return boolean indicating whether the operation conforms to the
   *         network group request filtering policy
   */
  boolean checkRequestFilteringPolicy(
      PreParseOperation operation, List<Message> messages)
  {
    if (requestFilteringPolicy != null)
    {
      return requestFilteringPolicy.isAllowed(operation, messages);
    }
    else
    {
      return true;
    }
  }



  /**
   * Checks the resource limits policy.
   *
   * @param connection
   *          the client connection
   * @param operation
   *          the ongoing operation
   * @param fullCheck
   *          a boolean indicating the level of checking: full/partial
   * @param messages
   *          the messages indicating the cause of the failure.
   * @return a boolean indicating whether resource limits are exceeded
   */
  boolean checkResourceLimitsPolicy(ClientConnection connection,
      PreParseOperation operation, boolean fullCheck,
      List<Message> messages)
  {
    if (resourceLimitsPolicy != null)
    {
      return resourceLimitsPolicy.isAllowed(connection, operation,
          fullCheck, messages);
    }
    else
    {
      return true;
    }
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
        for (WorkflowTopologyNode node : registeredWorkflowNodes
            .values())
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
    if ((workflow != null) && !isAdminNetworkGroup
        && !isInternalNetworkGroup && !isDefaultNetworkGroup)
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
   * @param workflowID
   *          the workflow identifier of the workflow to deregister
   * @return the deregistered workflow
   */
  public Workflow deregisterWorkflow(String workflowID)
  {
    Workflow workflow = null;

    String rootDSEWorkflowID = null;
    if (rootDSEWorkflowNode != null)
    {
      rootDSEWorkflowID =
          rootDSEWorkflowNode.getWorkflowImpl().getWorkflowId();
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
        for (WorkflowTopologyNode node : registeredWorkflowNodes
            .values())
        {
          String curID = node.getWorkflowImpl().getWorkflowId();
          if (curID.equals(workflowID))
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
    if ((workflow != null) && !isAdminNetworkGroup
        && !isInternalNetworkGroup && !isDefaultNetworkGroup)
    {
      WorkflowImpl workflowImpl = (WorkflowImpl) workflow;
      workflowImpl.decrementReferenceCounter();
    }

    return workflow;
  }



  /**
   * Performs any finalization that might be required when this network
   * group is unloaded. No action is taken in the default
   * implementation.
   */
  public void finalizeNetworkGroup()
  {
    if (configuration != null)
    {
      // Finalization specific to user network groups.
      deregister();

      // Remove all change listeners.
      configuration.removeChangeListener(changeListener);
      configuration
          .removeNetworkGroupQOSPolicyAddListener(policyListener);
      configuration
          .removeNetworkGroupQOSPolicyDeleteListener(policyListener);

      configuration = null;
    }

    // Clean up policies.
    for (QOSPolicy policy : policies.values())
    {
      policy.finalizeQOSPolicy();
    }

    requestFilteringPolicy = null;
    resourceLimitsPolicy = null;
    criteria = ConnectionCriteria.TRUE;
    policies.clear();
    // Remove the stats
    statistics.finalizeStatistics();
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
    if (resourceLimitsPolicy != null)
    {
      return resourceLimitsPolicy.getMinSubstring();
    }
    else
    {
      return 0;
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
   * Returns the QOS policy associated with this network group having
   * the specified class.
   *
   * @param <T>
   *          The type of QOS policy.
   * @param clazz
   *          The class of QOS policy requested.
   * @return The QOS policy associated with this network group having
   *         the specified class, or <code>null</code> if none was
   *         found.
   */
  public <T extends QOSPolicy> T getNetworkGroupQOSPolicy(Class<T> clazz)
  {
    for (QOSPolicy policy : policies.values())
    {
      if (clazz.isAssignableFrom(policy.getClass()))
      {
        return clazz.cast(policy);
      }
    }

    return null;
  }



  /**
   * Gets the search size limit, i.e. the maximum number of entries
   * returned by a search.
   *
   * @return the maximum number of entries returned by a search
   */
  public int getSizeLimit()
  {
    if (resourceLimitsPolicy != null)
    {
      return resourceLimitsPolicy.getSizeLimit();
    }
    else
    {
      return DirectoryServer.getSizeLimit();
    }
  }



  /**
   * Gets the search duration limit, i.e. the maximum duration of a
   * search operation.
   *
   * @return the maximum duration in ms of a search operation
   */
  public int getTimeLimit()
  {
    if (resourceLimitsPolicy != null)
    {
      return resourceLimitsPolicy.getTimeLimit();
    }
    else
    {
      return DirectoryServer.getTimeLimit();
    }
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
    if (baseDN.isNullDN())
    {
      // The rootDSE workflow is the candidate.
      workflowCandidate = rootDSEWorkflowNode;
    }
    else
    {
      // Search the highest workflow in the topology that can handle
      // the baseDN.
      for (WorkflowTopologyNode curWorkflow : namingContexts
          .getNamingContexts())
      {
        workflowCandidate = curWorkflow.getWorkflowCandidate(baseDN);
        if (workflowCandidate != null)
        {
          break;
        }
      }
    }

    return workflowCandidate;
  }



  /**
   * Registers a workflow with the network group.
   *
   * @param workflow
   *          the workflow to register
   * @throws DirectoryException
   *           If the workflow ID for the provided workflow conflicts
   *           with the workflow ID of an existing workflow.
   */
  public void registerWorkflow(WorkflowImpl workflow)
      throws DirectoryException
  {
    // The workflow is registered with no pre/post workflow element.
    registerWorkflow(workflow, null, null);
  }



  /**
   * Removes a connection from the group.
   *
   * @param connection
   *          the ClientConnection
   */
  public void removeConnection(ClientConnection connection)
  {
    if (resourceLimitsPolicy != null)
    {
      resourceLimitsPolicy.removeConnection(connection);
    }
  }



  /**
   * Updates the operations statistics.
   *
   * @param message
   *          The LDAP message being processed
   */
  public void updateMessageRead(LDAPMessage message)
  {
    statistics.updateMessageRead(message);
  }



  /**
   * Deregisters the current network group (this) with the server. The
   * method also decrements the reference counter of the workflows so
   * that workflows can be disabled or deleted if needed.
   * <p>
   * This methods is package private for testing purposes.
   */
  void deregister()
  {
    // Finalization specific to user network groups.
    synchronized (registeredNetworkGroupsLock)
    {
      // Deregister this network group.
      TreeMap<String, NetworkGroup> networkGroups =
          new TreeMap<String, NetworkGroup>(registeredNetworkGroups);
      networkGroups.remove(networkGroupID);
      registeredNetworkGroups = networkGroups;
      orderedNetworkGroups.remove(this);

      // Decrement the reference counter of the workflows registered
      // with this network group.
      synchronized (registeredWorkflowNodesLock)
      {
        for (WorkflowTopologyNode workflowNode : registeredWorkflowNodes
            .values())
        {
          WorkflowImpl workflowImpl = workflowNode.getWorkflowImpl();
          workflowImpl.decrementReferenceCounter();
        }
      }
    }
  }



  /**
   * Returns the request filtering policy statistics associated with
   * this network group.
   *
   * @return The request filtering policy statistics associated with
   *         this network group.
   */
  RequestFilteringPolicyStatistics getRequestFilteringPolicyStatistics()
  {
    if (requestFilteringPolicy != null)
    {
      return requestFilteringPolicy.getStatistics();
    }
    else
    {
      return null;
    }
  }



  /**
   * Returns the resource limits policy statistics associated with this
   * network group.
   *
   * @return The resource limits policy statistics associated with this
   *         network group.
   */
  ResourceLimitsPolicyStatistics getResourceLimitsPolicyStatistics()
  {
    if (resourceLimitsPolicy != null)
    {
      return resourceLimitsPolicy.getStatistics();
    }
    else
    {
      return null;
    }
  }



  /**
   * Registers the current network group (this) with the server.
   * <p>
   * This methods is package private for testing purposes.
   *
   * @throws InitializationException
   *           If the network group ID for the provided network group
   *           conflicts with the network group ID of an existing
   *           network group.
   */
  void register() throws InitializationException
  {
    ensureNotNull(networkGroupID);

    synchronized (registeredNetworkGroupsLock)
    {
      // The network group must not be already registered
      if (registeredNetworkGroups.containsKey(networkGroupID))
      {
        Message message =
            ERR_REGISTER_NETWORK_GROUP_ALREADY_EXISTS
                .get(networkGroupID);
        throw new InitializationException(message);
      }

      TreeMap<String, NetworkGroup> newRegisteredNetworkGroups =
          new TreeMap<String, NetworkGroup>(registeredNetworkGroups);
      newRegisteredNetworkGroups.put(networkGroupID, this);
      registeredNetworkGroups = newRegisteredNetworkGroups;

      // Insert the network group at the right position in the ordered
      // list.
      int index = 0;
      for (NetworkGroup ng : registeredNetworkGroups.values())
      {
        if (ng.equals(this))
        {
          continue;
        }
        if (this.priority > ng.priority)
        {
          index++;
        }
      }
      orderedNetworkGroups.add(index, this);
    }
  }



  /**
   * Sets the network group connection criteria.
   * <p>
   * This method is intended for testing only.
   *
   * @param criteria
   *          The connection criteria.
   */
  void setConnectionCriteria(ConnectionCriteria criteria)
  {
    this.criteria = criteria;
  }



  /**
   * Sets the network group priority.
   * <p>
   * This methods is package private for testing purposes.
   *
   * @param prio
   *          the network group priority
   */
  void setNetworkGroupPriority(int prio)
  {
    // Check whether the priority has changed
    if (priority != prio)
    {
      synchronized (registeredNetworkGroupsLock)
      {
        priority = prio;

        // Nothing to do if the network group is not registered
        if (registeredNetworkGroups.containsKey(networkGroupID))
        {
          // If the network group was already registered, remove it from
          // the ordered list
          orderedNetworkGroups.remove(this);

          // Then insert it at the right position in the ordered list
          int index = 0;
          for (NetworkGroup ng : registeredNetworkGroups.values())
          {
            if (ng.equals(this))
            {
              continue;
            }
            if (this.priority > ng.priority)
            {
              index++;
            }
          }
          orderedNetworkGroups.add(index, this);
        }
      }
    }
  }



  /**
   * Dumps info from the current network group for debug purpose.
   * <p>
   * This method is intended for testing only.
   *
   * @param leftMargin
   *          white spaces used to indent traces
   * @return a string buffer that contains trace information
   */
  StringBuilder toString(String leftMargin)
  {
    StringBuilder sb = new StringBuilder();
    String newMargin = leftMargin + "   ";

    sb.append(leftMargin + "Networkgroup (" + networkGroupID + "\n");
    sb.append(leftMargin + "List of registered workflows:\n");
    for (WorkflowTopologyNode node : registeredWorkflowNodes.values())
    {
      sb.append(node.toString(newMargin));
    }

    namingContexts.toString(leftMargin);

    sb.append(leftMargin + "rootDSEWorkflow:\n");
    if (rootDSEWorkflowNode == null)
    {
      sb.append(newMargin + "null\n");
    }
    else
    {
      sb.append(rootDSEWorkflowNode.toString(newMargin));
    }

    return sb;
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
    ensureNotNull(workflowID);

    // If the network group is the "internal" network group then bypass
    // the check because the internal network group may contain
    // duplicates of base DNs.
    if (isInternalNetworkGroup)
    {
      return;
    }

    // If the network group is the "admin" network group then bypass
    // the check because the internal network group may contain
    // duplicates of base DNs.
    if (isAdminNetworkGroup)
    {
      return;
    }

    // The workflow base DN should not be already present in the
    // network group. Bypass the check for the private workflows...
    for (WorkflowTopologyNode node : registeredWorkflowNodes.values())
    {
      DN nodeBaseDN = node.getBaseDN();
      if (nodeBaseDN.equals(workflowNode.getBaseDN()))
      {
        // The base DN is already registered in the network group,
        // we must reject the registration request
        Message message =
            ERR_REGISTER_WORKFLOW_BASE_DN_ALREADY_EXISTS.get(
                workflowID, networkGroupID, node.getWorkflowImpl()
                    .getWorkflowId(), workflowNode.getWorkflowImpl()
                    .getBaseDN().toString());
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
            message);
      }
    }
  }



  // Creates and registers the provided network group policy
  // configuration.
  private void createNetworkGroupQOSPolicy(
      QOSPolicyCfg policyConfiguration) throws ConfigException,
      InitializationException
  {
    String className = policyConfiguration.getJavaClass();
    QOSPolicyCfgDefn d = QOSPolicyCfgDefn.getInstance();
    ClassPropertyDefinition pd = d.getJavaClassPropertyDefinition();

    // Load the class and cast it to a network group policy.
    Class<? extends QOSPolicyFactory> theClass;
    QOSPolicyFactory factory;

    try
    {
      theClass = pd.loadClass(className, QOSPolicyFactory.class);
      factory = theClass.newInstance();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_CONFIG_NETWORK_GROUP_POLICY_CANNOT_INITIALIZE.get(String
              .valueOf(className), String.valueOf(policyConfiguration
              .dn()), stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }

    // Perform the necessary initialization for the network group
    // policy.
    QOSPolicy policy;

    try
    {
      // Determine the initialization method to use: it must take a
      // single parameter which is the exact type of the configuration
      // object.
      Method method =
          theClass.getMethod("createQOSPolicy", policyConfiguration
              .configurationClass());

      policy = (QOSPolicy) method.invoke(factory, policyConfiguration);
    }
    catch (Exception e)
    {
      if (e instanceof InvocationTargetException)
      {
        Throwable t = e.getCause();

        if (t instanceof InitializationException)
        {
          throw (InitializationException) t;
        }
        else if (t instanceof ConfigException)
        {
          throw (ConfigException) t;
        }
      }

      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_CONFIG_NETWORK_GROUP_POLICY_CANNOT_INITIALIZE.get(String
              .valueOf(className), String.valueOf(policyConfiguration
              .dn()), stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }

    // The network group has been successfully initialized - so register
    // it.
    QOSPolicy oldPolicy =
        policies.put(policyConfiguration.dn(), policy);

    if (policy instanceof RequestFilteringPolicy)
    {
      requestFilteringPolicy = (RequestFilteringPolicy) policy;
    }
    else if (policy instanceof ResourceLimitsPolicy)
    {
      resourceLimitsPolicy = (ResourceLimitsPolicy) policy;
    }

    if (oldPolicy != null)
    {
      oldPolicy.finalizeQOSPolicy();
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
      WorkflowTopologyNode workflowNode =
          (WorkflowTopologyNode) workflow;
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
          new TreeMap<String, WorkflowTopologyNode>(
              registeredWorkflowNodes);
      newWorkflowNodes.remove(workflowNode.getWorkflowImpl()
          .getWorkflowId());
      registeredWorkflowNodes = newWorkflowNodes;
    }
  }



  /**
   * Retrieves the list of registered workflows.
   *
   * @return a list of workflow ids
   */
  private List<String> getRegisteredWorkflows()
  {
    List<String> workflowIDs = new ArrayList<String>();
    synchronized (registeredWorkflowNodesLock)
    {
      for (WorkflowTopologyNode node : registeredWorkflowNodes.values())
      {
        workflowIDs.add(node.getWorkflowImpl().getWorkflowId());
      }
    }
    return workflowIDs;
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
   * Checks whether the connection matches the network group criteria.
   *
   * @param connection
   *          the client connection
   * @return a boolean indicating the match
   */
  private boolean match(ClientConnection connection)
  {
    if (criteria != null)
    {
      return criteria.matches(connection);
    }
    else
    {
      return true;
    }
  }



  /**
   * Checks whether the client connection matches the criteria after
   * bind.
   *
   * @param connection
   *          the ClientConnection
   * @param bindDN
   *          the DN used to bind
   * @param authType
   *          the authentication type
   * @param isSecure
   *          a boolean indicating whether the connection is secure
   * @return a boolean indicating whether the connection matches the
   *         criteria
   */
  private boolean matchAfterBind(ClientConnection connection,
      DN bindDN, AuthenticationType authType, boolean isSecure)
  {
    if (criteria != null)
    {
      return criteria.willMatchAfterBind(connection, bindDN, authType,
          isSecure);
    }
    else
    {
      return true;
    }
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
    for (WorkflowTopologyNode workflowNode : registeredWorkflowNodes
        .values())
    {
      WorkflowTopologyNode parent = workflowNode.getParent();
      if (parent == null)
      {
        namingContexts.addNamingContext(workflowNode);
      }
    }
  }



  /**
   * Registers a workflow with the network group and the workflow may
   * have pre and post workflow element.
   *
   * @param workflow
   *          the workflow to register
   * @param preWorkflowElements
   *          the tasks to execute before the workflow
   * @param postWorkflowElements
   *          the tasks to execute after the workflow
   * @throws DirectoryException
   *           If the workflow ID for the provided workflow conflicts
   *           with the workflow ID of an existing workflow or if the
   *           base DN of the workflow is the same than the base DN of
   *           another workflow already registered
   */
  private void registerWorkflow(WorkflowImpl workflow,
      WorkflowElement<?>[] preWorkflowElements,
      WorkflowElement<?>[] postWorkflowElements)
      throws DirectoryException
  {
    // Is it the rootDSE workflow?
    DN baseDN = workflow.getBaseDN();
    if (baseDN.isNullDN())
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
      WorkflowTopologyNode workflowNode =
          new WorkflowTopologyNode(workflow, preWorkflowElements,
              postWorkflowElements);

      // Register the workflow node with the network group. If the
      // workflow ID is already existing then an exception is raised.
      registerWorkflowNode(workflowNode);

      // Now add the workflow in the workflow topology...
      for (WorkflowTopologyNode curNode : registeredWorkflowNodes
          .values())
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
      // the network group is either default, or administration, or
      // internal network group.
      if (!isAdminNetworkGroup && !isInternalNetworkGroup
          && !isDefaultNetworkGroup)
      {
        workflow.incrementReferenceCounter();
      }
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
    ensureNotNull(workflowID);

    synchronized (registeredWorkflowNodesLock)
    {
      // The workflow must not be already registered
      if (registeredWorkflowNodes.containsKey(workflowID))
      {
        Message message =
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



  /**
   * Resets the configuration of the current network group.
   */
  private void reset()
  {
    synchronized (registeredWorkflowNodesLock)
    {
      registeredWorkflowNodes =
          new TreeMap<String, WorkflowTopologyNode>();
      rootDSEWorkflowNode = null;
      namingContexts = new NetworkGroupNamingContexts();
    }
  }
}
