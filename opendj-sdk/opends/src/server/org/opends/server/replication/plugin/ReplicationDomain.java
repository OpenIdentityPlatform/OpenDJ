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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.replication.plugin.Historical.ENTRYUIDNAME;
import static org.opends.server.replication.protocol.OperationContext.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.createEntry;
import static org.opends.server.util.StaticUtils.getFileForPath;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.opends.server.replication.common.StatusMachine.*;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CheckedOutputStream;
import java.util.zip.DataFormatException;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.ReplicationDomainCfgDefn.*;
import org.opends.server.admin.std.server.ReplicationDomainCfg;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.Backend;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.backends.jeb.BackendImpl;
import org.opends.server.backends.task.Task;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPModification;
import org.opends.server.replication.common.AssuredMode;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.common.DSInfo;
import org.opends.server.replication.common.RSInfo;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.common.ServerStatus;
import org.opends.server.replication.common.StatusMachine;
import org.opends.server.replication.common.StatusMachineEvent;
import org.opends.server.replication.protocol.AckMsg;
import org.opends.server.replication.protocol.AddContext;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.ChangeStatusMsg;
import org.opends.server.replication.protocol.DeleteContext;
import org.opends.server.replication.protocol.DoneMsg;
import org.opends.server.replication.protocol.EntryMsg;
import org.opends.server.replication.protocol.ErrorMsg;
import org.opends.server.replication.protocol.HeartbeatMsg;
import org.opends.server.replication.protocol.InitializeRequestMsg;
import org.opends.server.replication.protocol.InitializeTargetMsg;
import org.opends.server.replication.protocol.ModifyContext;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.ModifyDnContext;
import org.opends.server.replication.protocol.ModifyMsg;
import org.opends.server.replication.protocol.OperationContext;
import org.opends.server.replication.protocol.ReplSessionSecurity;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.replication.protocol.ResetGenerationIdMsg;
import org.opends.server.replication.protocol.RoutableMsg;
import org.opends.server.replication.protocol.TopologyMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.tasks.InitializeTargetTask;
import org.opends.server.tasks.InitializeTask;
import org.opends.server.tasks.TaskUtils;
import org.opends.server.types.AttributeBuilder;
import org.opends.server.types.Attributes;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDAPException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.Operation;
import org.opends.server.types.RDN;
import org.opends.server.types.RawModification;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;
import org.opends.server.types.SynchronizationProviderResult;
import org.opends.server.types.operation.PluginOperation;
import org.opends.server.types.operation.PostOperationOperation;
import org.opends.server.types.operation.PreOperationAddOperation;
import org.opends.server.types.operation.PreOperationDeleteOperation;
import org.opends.server.types.operation.PreOperationModifyDNOperation;
import org.opends.server.types.operation.PreOperationModifyOperation;
import org.opends.server.types.operation.PreOperationOperation;
import org.opends.server.workflowelement.localbackend.*;

/**
 *  This class implements the bulk part of the.of the Directory Server side
 *  of the replication code.
 *  It contains the root method for publishing a change,
 *  processing a change received from the replicationServer service,
 *  handle conflict resolution,
 *  handle protocol messages from the replicationServer.
 */
public class ReplicationDomain extends DirectoryThread
       implements ConfigurationChangeListener<ReplicationDomainCfg>,
                  AlertGenerator
{
  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
       "org.opends.server.replication.plugin.ReplicationDomain";

  /**
   * The attribute used to mark conflicting entries.
   * The value of this attribute should be the dn that this entry was
   * supposed to have when it was marked as conflicting.
   */
  public static final String DS_SYNC_CONFLICT = "ds-sync-conflict";

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private ReplicationMonitor monitor;

  private ReplicationBroker broker;
  // Thread waiting for incoming update messages for this domain and pushing
  // them to the global incoming update message queue for later processing by
  // replay threads.
  private ListenerThread listenerThread;
  // The update to replay message queue where the listener thread is going to
  // push incoming update messages.
  private LinkedBlockingQueue<UpdateToReplay> updateToReplayQueue;
  private SortedMap<ChangeNumber, UpdateMsg> waitingAckMsgs =
    new TreeMap<ChangeNumber, UpdateMsg>();
  private AtomicInteger numRcvdUpdates = new AtomicInteger(0);
  private AtomicInteger numSentUpdates = new AtomicInteger(0);
  private AtomicInteger numProcessedUpdates = new AtomicInteger();
  private AtomicInteger numResolvedNamingConflicts = new AtomicInteger();
  private AtomicInteger numResolvedModifyConflicts = new AtomicInteger();
  private AtomicInteger numUnresolvedNamingConflicts = new AtomicInteger();
  private int debugCount = 0;
  private PersistentServerState state;
  private int numReplayedPostOpCalled = 0;

  private int maxReceiveQueue = 0;
  private int maxSendQueue = 0;
  private int maxReceiveDelay = 0;
  private int maxSendDelay = 0;

  private long generationId = -1;
  private boolean generationIdSavedStatus = false;

  ChangeNumberGenerator generator;

  /**
   * This object is used to store the list of update currently being
   * done on the local database.
   * Is is useful to make sure that the local operations are sent in a
   * correct order to the replication server and that the ServerState
   * is not updated too early.
   */
  private PendingChanges pendingChanges;

  /**
   * It contain the updates that were done on other servers, transmitted
   * by the replication server and that are currently replayed.
   * It is useful to make sure that dependencies between operations
   * are correctly fulfilled and to to make sure that the ServerState is
   * not updated too early.
   */
  private RemotePendingChanges remotePendingChanges;

  /**
   * The time in milliseconds between heartbeats from the replication
   * server.  Zero means heartbeats are off.
   */
  private long heartbeatInterval = 0;
  private short serverId;

  // The context related to an import or export being processed
  // Null when none is being processed.
  private IEContext ieContext = null;

  private Collection<String> replicationServers;

  private DN baseDn;

  private boolean shutdown = false;

  private InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

  private boolean solveConflictFlag = true;

  private boolean disabled = false;
  private boolean stateSavingDisabled = false;

  private int window = 100;

  /*
   * Assured mode properties
   */
  // Is assured mode enabled or not for this domain ?
  private boolean assured = false;
  // Assured sub mode (used when assured is true)
  private AssuredMode assuredMode = AssuredMode.SAFE_DATA_MODE;
  // Safe Data level (used when assuredMode is SAFE_DATA)
  private byte assuredSdLevel = (byte)1;
  // Timeout (in milliseconds) when waiting for acknowledgments
  private long assuredTimeout = 1000;

  // Group id
  private byte groupId = (byte)1;
  // Referrals urls to be published to other servers of the topology
  // TODO: fill that with all currently opened urls if no urls configured
  private List<String> refUrls = new ArrayList<String>();

  // Current status for this replicated domain
  private ServerStatus status = ServerStatus.NOT_CONNECTED_STATUS;

  /*
   * Properties for the last topology info received from the network.
   */
  // Info for other DSs.
  // Warning: does not contain info for us (for our server id)
  private List<DSInfo> dsList = new ArrayList<DSInfo>();
  // Info for other RSs.
  private List<RSInfo> rsList = new ArrayList<RSInfo>();

  /**
   * The isolation policy that this domain is going to use.
   * This field describes the behavior of the domain when an update is
   * attempted and the domain could not connect to any Replication Server.
   * Possible values are accept-updates or deny-updates, but other values
   * may be added in the future.
   */
  private IsolationPolicy isolationpolicy;

  /**
   * The DN of the configuration entry of this domain.
   */
  private DN configDn;

  /**
   * A boolean indicating if the thread used to save the persistentServerState
   * is terminated.
   */
  private boolean done = true;

  /**
   * This class contain the context related to an import or export
   * launched on the domain.
   */
  private class IEContext
  {
    // The task that initiated the operation.
    Task initializeTask;
    // The input stream for the import
    ReplLDIFInputStream ldifImportInputStream = null;
    // The target in the case of an export
    short exportTarget = RoutableMsg.UNKNOWN_SERVER;
    // The source in the case of an import
    short importSource = RoutableMsg.UNKNOWN_SERVER;

    // The total entry count expected to be processed
    long entryCount = 0;
    // The count for the entry not yet processed
    long entryLeftCount = 0;

    // The exception raised when any
    DirectoryException exception = null;

    /**
     * Initializes the import/export counters with the provider value.
     * @param total
     * @param left
     * @throws DirectoryException
     */
    public void setCounters(long total, long left)
      throws DirectoryException
    {
      entryCount = total;
      entryLeftCount = left;

      if (initializeTask != null)
      {
        if (initializeTask instanceof InitializeTask)
        {
          ((InitializeTask)initializeTask).setTotal(entryCount);
          ((InitializeTask)initializeTask).setLeft(entryCount);
        }
        else if (initializeTask instanceof InitializeTargetTask)
        {
          ((InitializeTargetTask)initializeTask).setTotal(entryCount);
          ((InitializeTargetTask)initializeTask).setLeft(entryCount);
        }
      }
    }

    /**
     * Update the counters of the task for each entry processed during
     * an import or export.
     * @throws DirectoryException
     */
    public void updateCounters()
      throws DirectoryException
    {
      entryLeftCount--;

      if (initializeTask != null)
      {
        if (initializeTask instanceof InitializeTask)
        {
          ((InitializeTask)initializeTask).setLeft(entryLeftCount);
        }
        else if (initializeTask instanceof InitializeTargetTask)
        {
          ((InitializeTargetTask)initializeTask).setLeft(entryLeftCount);
        }
      }
    }

    /**
     * {@inheritDoc}
     */
    public String toString()
    {
      return new String("[ Entry count=" + this.entryCount +
                        ", Entry left count=" + this.entryLeftCount + "]");
    }
  }

  /**
   * This thread is launched when we want to export data to another server that
   * has requested to be initialized with the data of our backend.
   */
  private class ExportThread extends DirectoryThread
  {
    // Id of server that will receive updates
    private short target;

    /**
     * Constructor for the ExportThread.
     *
     * @param target Id of server that will receive updates
     */
    public ExportThread(short target)
    {
      super("Export thread " + serverId);
      this.target = target;
    }

    /**
     * Run method for this class.
     */
    public void run()
    {
      if (debugEnabled())
      {
        TRACER.debugInfo("Export thread starting.");
      }

      try
      {
        initializeRemote(target, target, null);
      } catch (DirectoryException de)
      {
      // An error message has been sent to the peer
      // Nothing more to do locally
      }
      if (debugEnabled())
      {
        TRACER.debugInfo("Export thread stopping.");
      }
    }
  }

  /**
   * Creates a new ReplicationDomain using configuration from configEntry.
   *
   * @param configuration    The configuration of this ReplicationDomain.
   * @param updateToReplayQueue The queue for update messages to replay.
   * @throws ConfigException In case of invalid configuration.
   */
  public ReplicationDomain(ReplicationDomainCfg configuration,
    LinkedBlockingQueue<UpdateToReplay> updateToReplayQueue)
    throws ConfigException
  {
    super("Replication State Saver for server id " + configuration.getServerId()
      + " and domain " + configuration.getBaseDN());

    // Read the configuration parameters.
    replicationServers = configuration.getReplicationServer();
    serverId = (short) configuration.getServerId();
    baseDn = configuration.getBaseDN();
    window  = configuration.getWindowSize();
    heartbeatInterval = configuration.getHeartbeatInterval();
    isolationpolicy = configuration.getIsolationPolicy();
    configDn = configuration.dn();
    this.updateToReplayQueue = updateToReplayQueue;

    /*
     * Fill assured configuration properties
     */
    AssuredType assuredType = configuration.getAssuredType();
    switch (assuredType)
    {
      case NOT_ASSURED:
        assured = false;
        break;
      case SAFE_DATA:
        assured = true;
        this.assuredMode = AssuredMode.SAFE_DATA_MODE;
        break;
      case SAFE_READ:
        assured = true;
        this.assuredMode = AssuredMode.SAFE_READ_MODE;
        break;
    }
    this.assuredSdLevel = (byte)configuration.getAssuredSdLevel();
    this.groupId = (byte)configuration.getGroupId();
    this.assuredTimeout = configuration.getAssuredTimeout();
    SortedSet<String> urls = configuration.getReferralsUrl();
    if (urls != null)
    {
      for (String url : urls)
      {
        this.refUrls.add(url);
      }
    }

    /*
     * Modify conflicts are solved for all suffixes but the schema suffix
     * because we don't want to store extra information in the schema
     * ldif files.
     * This has no negative impact because the changes on schema should
     * not produce conflicts.
     */
    if (baseDn.compareTo(DirectoryServer.getSchemaDN()) == 0)
    {
      solveConflictFlag = false;
    }
    else
    {
      solveConflictFlag = true;
    }

    /*
     * Create a new Persistent Server State that will be used to store
     * the last ChangeNmber seen from all LDAP servers in the topology.
     */
    state = new PersistentServerState(baseDn, serverId);

    /*
     * Create a replication monitor object responsible for publishing
     * monitoring information below cn=monitor.
     */
    monitor = new ReplicationMonitor(this);
    DirectoryServer.registerMonitorProvider(monitor);

    Backend backend = retrievesBackend(baseDn);
    if (backend == null)
    {
      throw new ConfigException(ERR_SEARCHING_DOMAIN_BACKEND.get(
                                  baseDn.toNormalizedString()));
    }

    try
    {
      generationId = loadGenerationId();
    }
    catch (DirectoryException e)
    {
      logError(ERR_LOADING_GENERATION_ID.get(
          baseDn.toNormalizedString(), e.getLocalizedMessage()));
    }

    /*
     * create the broker object used to publish and receive changes
     */
    broker = new ReplicationBroker(this, state, baseDn, serverId,
        maxReceiveQueue, maxReceiveDelay, maxSendQueue, maxSendDelay, window,
        heartbeatInterval, generationId,
        new ReplSessionSecurity(configuration),getGroupId());

    broker.start(replicationServers);

    /*
     * ChangeNumberGenerator is used to create new unique ChangeNumbers
     * for each operation done on this replication domain.
     *
     * The generator time is adjusted to the time of the last CN received from
     * remote other servers.
     */
    generator =
      new ChangeNumberGenerator(serverId, state);

    pendingChanges =
      new PendingChanges(generator,
                         broker, state);

    remotePendingChanges = new RemotePendingChanges(generator, state);

    // listen for changes on the configuration
    configuration.addChangeListener(this);

    // register as an AltertGenerator
    DirectoryServer.registerAlertGenerator(this);
  }


  /**
   * Returns the base DN of this ReplicationDomain.
   *
   * @return The base DN of this ReplicationDomain
   */
  public DN getBaseDN()
  {
    return baseDn;
  }

  /**
   * Implement the  handleConflictResolution phase of the deleteOperation.
   *
   * @param deleteOperation The deleteOperation.
   * @return A SynchronizationProviderResult indicating if the operation
   *         can continue.
   */
  public SynchronizationProviderResult handleConflictResolution(
         PreOperationDeleteOperation deleteOperation)
  {
    if ((!deleteOperation.isSynchronizationOperation())
        && (!brokerIsConnected(deleteOperation)))
    {
      Message msg = ERR_REPLICATION_COULD_NOT_CONNECT.get(baseDn.toString());
      return new SynchronizationProviderResult.StopProcessing(
          ResultCode.UNWILLING_TO_PERFORM, msg);
    }

    DeleteContext ctx =
      (DeleteContext) deleteOperation.getAttachment(SYNCHROCONTEXT);
    Entry deletedEntry = deleteOperation.getEntryToDelete();

    if (ctx != null)
    {
      /*
       * This is a replication operation
       * Check that the modified entry has the same entryuuid
       * as it was in the original message.
       */
      String operationEntryUUID = ctx.getEntryUid();
      String modifiedEntryUUID = Historical.getEntryUuid(deletedEntry);
      if (!operationEntryUUID.equals(modifiedEntryUUID))
      {
        /*
         * The changes entry is not the same entry as the one on
         * the original change was performed.
         * Probably the original entry was renamed and replaced with
         * another entry.
         * We must not let the change proceed, return a negative
         * result and set the result code to NO_SUCH_OBJET.
         * When the operation will return, the thread that started the
         * operation will try to find the correct entry and restart a new
         * operation.
         */
        return new SynchronizationProviderResult.StopProcessing(
            ResultCode.NO_SUCH_OBJECT, null);
      }
    }
    else
    {
      // There is no replication context attached to the operation
      // so this is not a replication operation.
      ChangeNumber changeNumber = generateChangeNumber(deleteOperation);
      String modifiedEntryUUID = Historical.getEntryUuid(deletedEntry);
      ctx = new DeleteContext(changeNumber, modifiedEntryUUID);
      deleteOperation.setAttachment(SYNCHROCONTEXT, ctx);
    }
    return new SynchronizationProviderResult.ContinueProcessing();
  }

  /**
   * Implement the  handleConflictResolution phase of the addOperation.
   *
   * @param addOperation The AddOperation.
   * @return A SynchronizationProviderResult indicating if the operation
   *         can continue.
   */
  public SynchronizationProviderResult handleConflictResolution(
      PreOperationAddOperation addOperation)
  {
    if ((!addOperation.isSynchronizationOperation())
        && (!brokerIsConnected(addOperation)))
    {
      Message msg = ERR_REPLICATION_COULD_NOT_CONNECT.get(baseDn.toString());
      return new SynchronizationProviderResult.StopProcessing(
          ResultCode.UNWILLING_TO_PERFORM, msg);
    }

    if (addOperation.isSynchronizationOperation())
    {
      AddContext ctx = (AddContext) addOperation.getAttachment(SYNCHROCONTEXT);
      /*
       * If an entry with the same entry uniqueID already exist then
       * this operation has already been replayed in the past.
       */
      String uuid = ctx.getEntryUid();
      if (findEntryDN(uuid) != null)
      {
        return new SynchronizationProviderResult.StopProcessing(
            ResultCode.CANCELED, null);
      }

      /* The parent entry may have been renamed here since the change was done
       * on the first server, and another entry have taken the former dn
       * of the parent entry
       */

      String parentUid = ctx.getParentUid();
      // root entry have no parent,
      // there is no need to check for it.
      if (parentUid != null)
      {
        // There is a potential of perfs improvement here
        // if we could avoid the following parent entry retrieval
        DN parentDnFromCtx = findEntryDN(ctx.getParentUid());

        if (parentDnFromCtx == null)
        {
          // The parent does not exist with the specified unique id
          // stop the operation with NO_SUCH_OBJECT and let the
          // conflict resolution or the dependency resolution solve this.
          return new SynchronizationProviderResult.StopProcessing(
              ResultCode.NO_SUCH_OBJECT, null);
        }
        else
        {
          DN entryDN = addOperation.getEntryDN();
          DN parentDnFromEntryDn = entryDN.getParentDNInSuffix();
          if ((parentDnFromEntryDn != null)
              && (!parentDnFromCtx.equals(parentDnFromEntryDn)))
          {
            // parentEntry has been renamed
            // replication name conflict resolution is expected to fix that
            // later in the flow
            return new SynchronizationProviderResult.StopProcessing(
                ResultCode.NO_SUCH_OBJECT, null);
          }
        }
      }
    }
    return new SynchronizationProviderResult.ContinueProcessing();
  }

  /**
   * Check that the broker associated to this ReplicationDomain has found
   * a Replication Server and that this LDAP server is therefore able to
   * process operations.
   * If not set the ResultCode and the response message,
   * interrupt the operation, and return false
   *
   * @param   op  The Operation that needs to be checked.
   *
   * @return  true when it OK to process the Operation, false otherwise.
   *          When false is returned the resultCode and the reponse message
   *          is also set in the Operation.
   */
  private boolean brokerIsConnected(PreOperationOperation op)
  {
    if (isolationpolicy.equals(IsolationPolicy.ACCEPT_ALL_UPDATES))
    {
      // this policy imply that we always accept updates.
      return true;
    }
    if (isolationpolicy.equals(IsolationPolicy.REJECT_ALL_UPDATES))
    {
      // this isolation policy specifies that the updates are denied
      // when the broker is not connected.
      return broker.isConnected();
    }
    // we should never get there as the only possible policies are
    // ACCEPT_ALL_UPDATES and REJECT_ALL_UPDATES
    return true;
  }


  /**
   * Implement the  handleConflictResolution phase of the ModifyDNOperation.
   *
   * @param modifyDNOperation The ModifyDNOperation.
   * @return A SynchronizationProviderResult indicating if the operation
   *         can continue.
   */
  public SynchronizationProviderResult handleConflictResolution(
      PreOperationModifyDNOperation modifyDNOperation)
  {
    if ((!modifyDNOperation.isSynchronizationOperation())
        && (!brokerIsConnected(modifyDNOperation)))
    {
      Message msg = ERR_REPLICATION_COULD_NOT_CONNECT.get(baseDn.toString());
      return new SynchronizationProviderResult.StopProcessing(
          ResultCode.UNWILLING_TO_PERFORM, msg);
    }

    ModifyDnContext ctx =
      (ModifyDnContext) modifyDNOperation.getAttachment(SYNCHROCONTEXT);
    if (ctx != null)
    {
      /*
       * This is a replication operation
       * Check that the modified entry has the same entryuuid
       * as was in the original message.
       */
      String modifiedEntryUUID =
        Historical.getEntryUuid(modifyDNOperation.getOriginalEntry());
      if (!modifiedEntryUUID.equals(ctx.getEntryUid()))
      {
        /*
         * The modified entry is not the same entry as the one on
         * the original change was performed.
         * Probably the original entry was renamed and replaced with
         * another entry.
         * We must not let the change proceed, return a negative
         * result and set the result code to NO_SUCH_OBJET.
         * When the operation will return, the thread that started the
         * operation will try to find the correct entry and restart a new
         * operation.
         */
        return new SynchronizationProviderResult.StopProcessing(
            ResultCode.NO_SUCH_OBJECT, null);
      }
      if (modifyDNOperation.getNewSuperior() != null)
      {
        /*
         * Also check that the current id of the
         * parent is the same as when the operation was performed.
         */
        String newParentId = findEntryId(modifyDNOperation.getNewSuperior());
        if ((newParentId != null) && (ctx.getNewParentId() != null) &&
            (!newParentId.equals(ctx.getNewParentId())))
        {
        return new SynchronizationProviderResult.StopProcessing(
            ResultCode.NO_SUCH_OBJECT, null);
        }
      }
    }
    else
    {
      // There is no replication context attached to the operation
      // so this is not a replication operation.
      ChangeNumber changeNumber = generateChangeNumber(modifyDNOperation);
      String newParentId = null;
      if (modifyDNOperation.getNewSuperior() != null)
      {
        newParentId = findEntryId(modifyDNOperation.getNewSuperior());
      }

      Entry modifiedEntry = modifyDNOperation.getOriginalEntry();
      String modifiedEntryUUID = Historical.getEntryUuid(modifiedEntry);
      ctx = new ModifyDnContext(changeNumber, modifiedEntryUUID, newParentId);
      modifyDNOperation.setAttachment(SYNCHROCONTEXT, ctx);
    }
    return new SynchronizationProviderResult.ContinueProcessing();
  }

  /**
   * Handle the conflict resolution.
   * Called by the core server after locking the entry and before
   * starting the actual modification.
   * @param modifyOperation the operation
   * @return code indicating is operation must proceed
   */
  public SynchronizationProviderResult handleConflictResolution(
         PreOperationModifyOperation modifyOperation)
  {
    if ((!modifyOperation.isSynchronizationOperation())
        && (!brokerIsConnected(modifyOperation)))
    {
      Message msg = ERR_REPLICATION_COULD_NOT_CONNECT.get(baseDn.toString());
      return new SynchronizationProviderResult.StopProcessing(
          ResultCode.UNWILLING_TO_PERFORM, msg);
    }

    ModifyContext ctx =
      (ModifyContext) modifyOperation.getAttachment(SYNCHROCONTEXT);

    Entry modifiedEntry = modifyOperation.getModifiedEntry();
    if (ctx == null)
    {
      // There is no replication context attached to the operation
      // so this is not a replication operation.
      ChangeNumber changeNumber = generateChangeNumber(modifyOperation);
      String modifiedEntryUUID = Historical.getEntryUuid(modifiedEntry);
      if (modifiedEntryUUID == null)
        modifiedEntryUUID = modifyOperation.getEntryDN().toString();
      ctx = new ModifyContext(changeNumber, modifiedEntryUUID);
      modifyOperation.setAttachment(SYNCHROCONTEXT, ctx);
    }
    else
    {
      // This is a replayed operation, it is necessary to
      // - check if the entry has been renamed
      // - check for conflicts
      String modifiedEntryUUID = ctx.getEntryUid();
      String currentEntryUUID = Historical.getEntryUuid(modifiedEntry);
      if ((currentEntryUUID != null) &&
          (!currentEntryUUID.equals(modifiedEntryUUID)))
      {
        /*
         * The current modified entry is not the same entry as the one on
         * the original modification was performed.
         * Probably the original entry was renamed and replaced with
         * another entry.
         * We must not let the modification proceed, return a negative
         * result and set the result code to NO_SUCH_OBJET.
         * When the operation will return, the thread that started the
         * operation will try to find the correct entry and restart a new
         * operation.
         */
         return new SynchronizationProviderResult.StopProcessing(
              ResultCode.NO_SUCH_OBJECT, null);
      }

      /*
       * Solve the conflicts between modify operations
       */
      Historical historicalInformation = Historical.load(modifiedEntry);
      modifyOperation.setAttachment(Historical.HISTORICAL,
                                    historicalInformation);

      if (historicalInformation.replayOperation(modifyOperation, modifiedEntry))
      {
        numResolvedModifyConflicts.incrementAndGet();
      }

      if (modifyOperation.getModifications().isEmpty())
      {
        /*
         * This operation becomes a no-op due to conflict resolution
         * stop the processing and send an OK result
         */
        return new SynchronizationProviderResult.StopProcessing(
            ResultCode.SUCCESS, null);
      }
    }
    return new SynchronizationProviderResult.ContinueProcessing();
  }

  /**
   * The preOperation phase for the add Operation.
   * Its job is to generate the replication context associated to the
   * operation. It is necessary to do it in this phase because contrary to
   * the other operations, the entry uid is not set when the handleConflict
   * phase is called.
   *
   * @param addOperation The Add Operation.
   */
  public void doPreOperation(PreOperationAddOperation addOperation)
  {
    AddContext ctx = new AddContext(generateChangeNumber(addOperation),
        Historical.getEntryUuid(addOperation),
        findEntryId(addOperation.getEntryDN().getParentDNInSuffix()));

    addOperation.setAttachment(SYNCHROCONTEXT, ctx);
    Historical.generateState(addOperation);
  }

  /**
   * Receives an update message from the replicationServer.
   * also responsible for updating the list of pending changes
   * @return the received message - null if none
   */
  public UpdateMsg receive()
  {
    UpdateMsg update = null;

    while ( (update == null) && (!shutdown) )
    {
      InitializeRequestMsg initMsg = null;
      ReplicationMsg msg;
      try
      {
        msg = broker.receive();
        if (msg == null)
        {
          // The server is in the shutdown process
          return null;
        }

        if (debugEnabled())
          if (!(msg instanceof HeartbeatMsg))
            TRACER.debugVerbose("Message received <" + msg + ">");

        if (msg instanceof AckMsg)
        {
          AckMsg ack = (AckMsg) msg;
          receiveAck(ack);
        }
        else if (msg instanceof InitializeRequestMsg)
        {
          // Another server requests us to provide entries
          // for a total update
          initMsg = (InitializeRequestMsg)msg;
        }
        else if (msg instanceof InitializeTargetMsg)
        {
          // Another server is exporting its entries to us
          InitializeTargetMsg importMsg = (InitializeTargetMsg) msg;

          try
          {
            // This must be done while we are still holding the
            // broker lock because we are now going to receive a
            // bunch of entries from the remote server and we
            // want the import thread to catch them and
            // not the ListenerThread.
            initialize(importMsg);
          }
          catch(DirectoryException de)
          {
            // Returns an error message to notify the sender
            ErrorMsg errorMsg =
              new ErrorMsg(importMsg.getsenderID(),
                  de.getMessageObject());
            MessageBuilder mb = new MessageBuilder();
            mb.append(de.getMessageObject());
            TRACER.debugInfo(Message.toString(mb.toMessage()));
            broker.publish(errorMsg);
          }
        }
        else if (msg instanceof ErrorMsg)
        {
          if (ieContext != null)
          {
            // This is an error termination for the 2 following cases :
            // - either during an export
            // - or before an import really started
            //   For example, when we publish a request and the
            //  replicationServer did not find any import source.
            abandonImportExport((ErrorMsg)msg);
          }
          else
          {
            /*
             * Log error message
             */
            ErrorMsg errorMsg = (ErrorMsg)msg;
            logError(ERR_ERROR_MSG_RECEIVED.get(
                errorMsg.getDetails()));
          }
        }
        if (msg instanceof TopologyMsg)
        {
          TopologyMsg topoMsg = (TopologyMsg)msg;
          receiveTopo(topoMsg);
        }
        if (msg instanceof ChangeStatusMsg)
        {
          ChangeStatusMsg csMsg = (ChangeStatusMsg)msg;
          receiveChangeStatus(csMsg);
        }
        else if (msg instanceof UpdateMsg)
        {
          update = (UpdateMsg) msg;
          receiveUpdate(update);
        }
      }
      catch (SocketTimeoutException e)
      {
        // just retry
      }
      // Test if we have received and export request message and
      // if that's the case handle it now.
      // This must be done outside of the portion of code protected
      // by the broker lock so that we keep receiveing update
      // when we are doing and export and so that a possible
      // closure of the socket happening when we are publishing the
      // entries to the remote can be handled by the other
      // replay thread when they call this method and therefore the
      // broker.receive() method.
      if (initMsg != null)
      {
        // Do this work in a thread to allow replay thread continue working
        ExportThread exportThread = new ExportThread(initMsg.getsenderID());
        exportThread.start();
      }
    }
    return update;
  }

  /**
   * Processes an incoming TopologyMsg.
   * Updates the structures for the local view of the topology.
   *
   * @param topoMsg The topology information received from RS.
   */
  public void receiveTopo(TopologyMsg topoMsg)
  {

    if (debugEnabled())
      TRACER.debugInfo("Replication domain " + baseDn
        + " received topology info update:\n" + topoMsg);

    // Store new lists
    synchronized(getDsList())
    {
      synchronized(getRsList())
      {
        dsList = topoMsg.getDsList();
        rsList = topoMsg.getRsList();
      }
    }
  }

  /**
   * Set the initial status of the domain, once he is connected to the topology.
   * @param initStatus The status to enter the state machine with
   */
  public void setInitialStatus(ServerStatus initStatus)
  {
    // Sanity check: is it a valid initial status?
    if (!isValidInitialStatus(initStatus))
    {
      Message msg = ERR_DS_INVALID_INIT_STATUS.get(initStatus.toString(),
        baseDn.toString(), Short.toString(serverId));
      logError(msg);
    } else
    {
      status = initStatus;
    }
  }

  /**
   * Processes an incoming ChangeStatusMsg. Compute new status according to
   * given order. Then update domain for being compliant with new status
   * definition.
   * @param csMsg The received status message
   */
  private void receiveChangeStatus(ChangeStatusMsg csMsg)
  {
    if (debugEnabled())
      TRACER.debugInfo("Replication domain " + baseDn +
        " received change status message:\n" + csMsg);

    ServerStatus reqStatus = csMsg.getRequestedStatus();

    // Translate requested status to a state machine event
    StatusMachineEvent event = StatusMachineEvent.statusToEvent(reqStatus);
    if (event == StatusMachineEvent.INVALID_EVENT)
    {
      Message msg = ERR_DS_INVALID_REQUESTED_STATUS.get(reqStatus.toString(),
        baseDn.toString(), Short.toString(serverId));
      logError(msg);
      return;
    }

    // Compute new status and do matching tasks
    // Use synchronized as admin task (thread) could order to go in admin status
    // for instance (concurrent with receive thread).
    synchronized (status)
    {
      ServerStatus newStatus =
        StatusMachine.computeNewStatus(status, event);

      if (newStatus == ServerStatus.INVALID_STATUS)
      {
        Message msg = ERR_DS_CANNOT_CHANGE_STATUS.get(baseDn.toString(),
          Short.toString(serverId), status.toString(), event.toString());
        logError(msg);
        return;
      }

      // Store new status
      status = newStatus;

      if (debugEnabled())
      TRACER.debugInfo("Replication domain " + baseDn +
        " new status is: " + status);

      // Perform whatever actions are needed to apply properties for being
      // compliant with new status
      updateDomainForNewStatus();
    }
  }

  /**
   * Called when first connection or disconnection detected.
   */
  public void toNotConnectedStatus()
  {
    // Go into not connected status
    // Use synchronized as somebody could ask another status change at the same
    // time
    synchronized (status)
    {
      StatusMachineEvent event =
        StatusMachineEvent.TO_NOT_CONNECTED_STATUS_EVENT;
      ServerStatus newStatus =
        StatusMachine.computeNewStatus(status, event);

      if (newStatus == ServerStatus.INVALID_STATUS)
      {
        Message msg = ERR_DS_CANNOT_CHANGE_STATUS.get(baseDn.toString(),
          Short.toString(serverId), status.toString(), event.toString());
        logError(msg);
        return;
      }

      // Store new status
      status = newStatus;

      if (debugEnabled())
        TRACER.debugInfo("Replication domain " + baseDn +
          " new status is: " + status);

      // Perform whatever actions are needed to apply properties for being
      // compliant with new status
      updateDomainForNewStatus();
    }
  }

  /**
   * Perform whatever actions are needed to apply properties for being
   * compliant with new status. Must be called in synchronized section for
   * status. The new status is already set in status variable.
   */
  private void updateDomainForNewStatus()
  {
    switch (status)
    {
      case NOT_CONNECTED_STATUS:
        break;
      case NORMAL_STATUS:
        break;
      case DEGRADED_STATUS:
        break;
      case FULL_UPDATE_STATUS:
        // Signal RS we just entered the full update status
        broker.signalStatusChange(status);
        break;
      case BAD_GEN_ID_STATUS:
        break;
      default:
        if (debugEnabled())
          TRACER.debugInfo("updateDomainForNewStatus: unexpected status: " +
            status);
    }
  }

  /**
   * Do the necessary processing when an UpdateMsg was received.
   *
   * @param update The received UpdateMsg.
   */
  public void receiveUpdate(UpdateMsg update)
  {
    remotePendingChanges.putRemoteUpdate(update);
    numRcvdUpdates.incrementAndGet();
  }

  /**
   * Do the necessary processing when an AckMsg is received.
   *
   * @param ack The AckMsg that was received.
   */
  public void receiveAck(AckMsg ack)
  {
    UpdateMsg update;
    ChangeNumber changeNumber = ack.getChangeNumber();

    synchronized (waitingAckMsgs)
    {
      update = waitingAckMsgs.remove(changeNumber);
    }
    if (update != null)
    {
      synchronized (update)
      {
        update.notify();
      }
    }
  }

  /**
   * Check if an operation must be synchronized.
   * Also update the list of pending changes and the server RUV
   * @param op the operation
   */
  public void synchronize(PostOperationOperation op)
  {
    ResultCode result = op.getResultCode();
    if ((result == ResultCode.SUCCESS) && op.isSynchronizationOperation())
    {
      numReplayedPostOpCalled++;
    }
    UpdateMsg msg = null;

    // Note that a failed non-replication operation might not have a change
    // number.
    ChangeNumber curChangeNumber = OperationContext.getChangeNumber(op);

    boolean isAssured = isAssured(op);

    if ((result == ResultCode.SUCCESS) && (!op.isSynchronizationOperation()))
    {
      // Generate a replication message for a successful non-replication
      // operation.
      msg = UpdateMsg.generateMsg(op);

      if (msg == null)
      {
        /*
         * This is an operation type that we do not know about
         * It should never happen.
         */
        pendingChanges.remove(curChangeNumber);
        Message message =
            ERR_UNKNOWN_TYPE.get(op.getOperationType().toString());
        logError(message);
        return;
      }
    }

    if (result == ResultCode.SUCCESS)
    {
      try
      {
        if (op.isSynchronizationOperation())
        {
          remotePendingChanges.commit(curChangeNumber);
        }
        else
        {
          pendingChanges.commit(curChangeNumber, msg);
        }
      }
      catch  (NoSuchElementException e)
      {
        Message message = ERR_OPERATION_NOT_FOUND_IN_PENDING.get(
            curChangeNumber.toString(), op.toString());
        logError(message);
        return;
      }

      if (msg != null && isAssured)
      {
        synchronized (waitingAckMsgs)
        {
          // Add the assured message to the list of update that are
          // waiting acknowledgements
          waitingAckMsgs.put(curChangeNumber, msg);
        }
      }

      if (generationIdSavedStatus != true)
      {
        this.saveGenerationId(generationId);
      }
    }
    else if (!op.isSynchronizationOperation())
    {
      // Remove an unsuccessful non-replication operation from the pending
      // changes list.
      if (curChangeNumber != null)
      {
        pendingChanges.remove(curChangeNumber);
      }
    }

    if (!op.isSynchronizationOperation())
    {
      int pushedChanges = pendingChanges.pushCommittedChanges();
      numSentUpdates.addAndGet(pushedChanges);
    }

    // Wait for acknowledgement of an assured message.
    if (msg != null && isAssured)
    {
      synchronized (msg)
      {
        while (waitingAckMsgs.containsKey(msg.getChangeNumber()))
        {
          // TODO : should have a configurable timeout to get
          // out of this loop
          try
          {
            msg.wait(1000);
          } catch (InterruptedException e)
          { }
        }
      }
    }
  }

  /**
   * get the number of updates received by the replication plugin.
   *
   * @return the number of updates received
   */
  public int getNumRcvdUpdates()
  {
    if (numRcvdUpdates != null)
      return numRcvdUpdates.get();
    else
      return 0;
  }

  /**
   * Get the number of updates sent by the replication plugin.
   *
   * @return the number of updates sent
   */
  public int getNumSentUpdates()
  {
    if (numSentUpdates != null)
      return numSentUpdates.get();
    else
      return 0;
  }

  /**
   * Get the number of updates in the pending list.
   *
   * @return The number of updates in the pending list
   */
  public int getPendingUpdatesCount()
  {
    if (pendingChanges != null)
      return pendingChanges.size();
    else
      return 0;
  }

  /**
   * Increment the number of processed updates.
   */
  public void incProcessedUpdates()
  {
    numProcessedUpdates.incrementAndGet();
  }

  /**
   * get the number of updates replayed by the replication.
   *
   * @return The number of updates replayed by the replication
   */
  public int getNumProcessedUpdates()
  {
    if (numProcessedUpdates != null)
      return numProcessedUpdates.get();
    else
      return 0;
  }

  /**
   * get the number of updates replayed successfully by the replication.
   *
   * @return The number of updates replayed successfully
   */
  public int getNumReplayedPostOpCalled()
  {
    return numReplayedPostOpCalled;
  }

  /**
   * get the ServerState.
   *
   * @return the ServerState
   */
  public ServerState getServerState()
  {
    return state;
  }

  /**
   * Get the debugCount.
   *
   * @return Returns the debugCount.
   */
  public int getDebugCount()
  {
    return debugCount;
  }

  /**
   * Send an Ack message.
   *
   * @param changeNumber The ChangeNumber for which the ack must be sent.
   */
  public void ack(ChangeNumber changeNumber)
  {
    broker.publish(new AckMsg(changeNumber));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void run()
  {
    done = false;

    // Create the listener thread
    listenerThread = new ListenerThread(this, updateToReplayQueue);
    listenerThread.start();

    while (shutdown  == false)
    {
      try
      {
        synchronized (this)
        {
          this.wait(1000);
          if (!disabled && !stateSavingDisabled )
          {
            // save the RUV
            state.save();
          }
        }
      } catch (InterruptedException e)
      { }
    }
    state.save();

    done = true;
  }

  /**
   * Shutdown this ReplicationDomain.
   */
  public void shutdown()
  {
    // stop the flush thread
    shutdown = true;

    // Stop the listener thread
    if (listenerThread != null)
    {
      listenerThread.shutdown();
    }

    synchronized (this)
    {
      this.notify();
    }

    DirectoryServer.deregisterMonitorProvider(monitor.getMonitorInstanceName());

    DirectoryServer.deregisterAlertGenerator(this);

    // stop the ReplicationBroker
    broker.stop();

    // Wait for the listener thread to stop
    if (listenerThread != null)
      listenerThread.waitForShutdown();

    // wait for completion of the persistentServerState thread.
    try
    {
      while (!done)
      {
        Thread.sleep(50);
      }
    } catch (InterruptedException e)
    {
      // stop waiting when interrupted.
    }
  }

  /**
   * Get the name of the replicationServer to which this domain is currently
   * connected.
   *
   * @return the name of the replicationServer to which this domain
   *         is currently connected.
   */
  public String getReplicationServer()
  {
    if (broker != null)
      return broker.getReplicationServer();
    else
      return "Not connected";
  }

  /**
   * Create and replay a synchronized Operation from an UpdateMsg.
   *
   * @param msg The UpdateMsg to be replayed.
   */
  public void replay(UpdateMsg msg)
  {
    Operation op = null;
    boolean done = false;
    boolean dependency = false;
    ChangeNumber changeNumber = null;
    int retryCount = 10;

    // Try replay the operation, then flush (replaying) any pending operation
    // whose dependency has been replayed until no more left.
    do
    {
      try
      {
        op = msg.createOperation(conn);
        dependency = remotePendingChanges.checkDependencies(op, msg);

        while ((!dependency) && (!done) && (retryCount-- > 0))
        {
          op.setInternalOperation(true);
          op.setSynchronizationOperation(true);
          changeNumber = OperationContext.getChangeNumber(op);
          ((AbstractOperation) op).run();

          // Try replay the operation
          ResultCode result = op.getResultCode();

          if (result != ResultCode.SUCCESS)
          {
            if (op instanceof ModifyOperation)
            {
              ModifyOperation newOp = (ModifyOperation) op;
              dependency = remotePendingChanges.checkDependencies(newOp);
              ModifyMsg modifyMsg = (ModifyMsg) msg;
              done = solveNamingConflict(newOp, modifyMsg);
            } else if (op instanceof DeleteOperation)
            {
              DeleteOperation newOp = (DeleteOperation) op;
              dependency = remotePendingChanges.checkDependencies(newOp);
              done = solveNamingConflict(newOp, msg);
            } else if (op instanceof AddOperation)
            {
              AddOperation newOp = (AddOperation) op;
              AddMsg addMsg = (AddMsg) msg;
              dependency = remotePendingChanges.checkDependencies(newOp);
              done = solveNamingConflict(newOp, addMsg);
            } else if (op instanceof ModifyDNOperationBasis)
            {
              ModifyDNOperationBasis newOp = (ModifyDNOperationBasis) op;
                  done = solveNamingConflict(newOp, msg);
            } else
            {
              done = true;  // unknown type of operation ?!
            }
            if (done)
            {
              // the update became a dummy update and the result
              // of the conflict resolution phase is to do nothing.
              // however we still need to push this change to the serverState
              updateError(changeNumber);
            }
            else
            {
              /*
               * Create a new operation as the ConflictResolution
               * different operation.
               */
              op = msg.createOperation(conn);
            }
          }
          else
          {
            done = true;
          }
        }

        if (!done && !dependency)
        {
          // Continue with the next change but the servers could now become
          // inconsistent.
          // Let the repair tool know about this.
          Message message = ERR_LOOP_REPLAYING_OPERATION.get(op.toString(),
            op.getErrorMessage().toString());
          logError(message);
          numUnresolvedNamingConflicts.incrementAndGet();

          updateError(changeNumber);
        }
      } catch (ASN1Exception e)
      {
        Message message = ERR_EXCEPTION_DECODING_OPERATION.get(
          String.valueOf(msg) + stackTraceToSingleLineString(e));
        logError(message);
      } catch (LDAPException e)
      {
        Message message = ERR_EXCEPTION_DECODING_OPERATION.get(
          String.valueOf(msg) + stackTraceToSingleLineString(e));
        logError(message);
      } catch (DataFormatException e)
      {
        Message message = ERR_EXCEPTION_DECODING_OPERATION.get(
          String.valueOf(msg) + stackTraceToSingleLineString(e));
        logError(message);
      } catch (Exception e)
      {
        if (changeNumber != null)
        {
          /*
           * An Exception happened during the replay process.
           * Continue with the next change but the servers will now start
           * to be inconsistent.
           * Let the repair tool know about this.
           */
          Message message = ERR_EXCEPTION_REPLAYING_OPERATION.get(
            stackTraceToSingleLineString(e), op.toString());
          logError(message);
          updateError(changeNumber);
        } else
        {
          Message message = ERR_EXCEPTION_DECODING_OPERATION.get(
            String.valueOf(msg) + stackTraceToSingleLineString(e));
          logError(message);
        }
      } finally
      {
        if (!dependency)
        {
          broker.updateWindowAfterReplay();
          if (msg.isAssured())
            ack(msg.getChangeNumber());
          incProcessedUpdates();
        }
      }

      // Now replay any pending update that had a dependency and whose
      // dependency has been replayed, do that until no more updates of that
      // type left...
      msg = remotePendingChanges.getNextUpdate();

      // Prepare restart of loop
      done = false;
      dependency = false;
      changeNumber = null;
      retryCount = 10;

    } while (msg != null);
  }

  /**
   * This method is called when an error happens while replaying
   * an operation.
   * It is necessary because the postOperation does not always get
   * called when error or Exceptions happen during the operation replay.
   *
   * @param changeNumber the ChangeNumber of the operation with error.
   */
  public void updateError(ChangeNumber changeNumber)
  {
    remotePendingChanges.commit(changeNumber);
  }

  /**
   * Generate a new change number and insert it in the pending list.
   *
   * @param operation The operation for which the change number must be
   *                  generated.
   * @return The new change number.
   */
  private ChangeNumber generateChangeNumber(PluginOperation operation)
  {
    return pendingChanges.putLocalOperation(operation);
  }


  /**
   * Find the Unique Id of the entry with the provided DN by doing a
   * search of the entry and extracting its uniqueID from its attributes.
   *
   * @param dn The dn of the entry for which the unique Id is searched.
   *
   * @return The unique Id of the entry with the provided DN.
   */
  static String findEntryId(DN dn)
  {
    if (dn == null)
      return null;
    try
    {
      InternalClientConnection conn =
                InternalClientConnection.getRootConnection();
      LinkedHashSet<String> attrs = new LinkedHashSet<String>(1);
      attrs.add(ENTRYUIDNAME);
      InternalSearchOperation search = conn.processSearch(dn,
            SearchScope.BASE_OBJECT, DereferencePolicy.NEVER_DEREF_ALIASES,
            0, 0, false,
            SearchFilter.createFilterFromString("objectclass=*"),
            attrs);

      if (search.getResultCode() == ResultCode.SUCCESS)
      {
        LinkedList<SearchResultEntry> result = search.getSearchEntries();
        if (!result.isEmpty())
        {
          SearchResultEntry resultEntry = result.getFirst();
          if (resultEntry != null)
          {
            return Historical.getEntryUuid(resultEntry);
          }
        }
      }
    } catch (DirectoryException e)
    {
      // never happens because the filter is always valid.
    }
    return null;
  }

  /**
   * find the current DN of an entry from its entry UUID.
   *
   * @param uuid the Entry Unique ID.
   * @return The current DN of the entry or null if there is no entry with
   *         the specified UUID.
   */
  private DN findEntryDN(String uuid)
  {
    try
    {
      InternalSearchOperation search = conn.processSearch(baseDn,
            SearchScope.WHOLE_SUBTREE,
            SearchFilter.createFilterFromString("entryuuid="+uuid));
      if (search.getResultCode() == ResultCode.SUCCESS)
      {
        LinkedList<SearchResultEntry> result = search.getSearchEntries();
        if (!result.isEmpty())
        {
          SearchResultEntry resultEntry = result.getFirst();
          if (resultEntry != null)
          {
            return resultEntry.getDN();
          }
        }
      }
    } catch (DirectoryException e)
    {
      // never happens because the filter is always valid.
    }
    return null;
  }

  /**
   * Solve a conflict detected when replaying a modify operation.
   *
   * @param op The operation that triggered the conflict detection.
   * @param msg The operation that triggered the conflict detection.
   * @return true if the process is completed, false if it must continue..
   */
  private boolean solveNamingConflict(ModifyOperation op,
      ModifyMsg msg)
  {
    ResultCode result = op.getResultCode();
    ModifyContext ctx = (ModifyContext) op.getAttachment(SYNCHROCONTEXT);
    String entryUid = ctx.getEntryUid();

    if (result == ResultCode.NO_SUCH_OBJECT)
    {
      /*
       * The operation is a modification but
       * the entry has been renamed on a different master in the same time.
       * search if the entry has been renamed, and return the new dn
       * of the entry.
       */
      DN newdn = findEntryDN(entryUid);
      if (newdn != null)
      {
        // There is an entry with the same unique id as this modify operation
        // replay the modify using the current dn of this entry.
        msg.setDn(newdn.toString());
        numResolvedNamingConflicts.incrementAndGet();
        return false;
      }
      else
      {
        // This entry does not exist anymore.
        // It has probably been deleted, stop the processing of this operation
        numResolvedNamingConflicts.incrementAndGet();
        return true;
      }
    }
    else if (result == ResultCode.NOT_ALLOWED_ON_RDN)
    {
      DN currentDN = findEntryDN(entryUid);
      RDN currentRDN = null;
      if (currentDN != null)
      {
        currentRDN = currentDN.getRDN();
      }
      else
      {
        // The entry does not exist anymore.
        numResolvedNamingConflicts.incrementAndGet();
        return true;
      }

      // The modify operation is trying to delete the value that is
      // currently used in the RDN. We need to alter the modify so that it does
      // not remove the current RDN value(s).

      List<Modification> mods = op.getModifications();
      for (Modification mod : mods)
      {
        AttributeType modAttrType = mod.getAttribute().getAttributeType();
        if ((mod.getModificationType() == ModificationType.DELETE) ||
            (mod.getModificationType() == ModificationType.REPLACE))
        {
          if (currentRDN.hasAttributeType(modAttrType))
          {
            // the attribute can't be deleted because it is used
            // in the RDN, turn this operation is a replace with the
            // current RDN value(s);
            mod.setModificationType(ModificationType.REPLACE);
            Attribute newAttribute = mod.getAttribute();
            AttributeBuilder attrBuilder;
            if (newAttribute == null)
            {
              attrBuilder = new AttributeBuilder(modAttrType);
            }
            else
            {
              attrBuilder = new AttributeBuilder(newAttribute);
            }
            attrBuilder.add(currentRDN.getAttributeValue(modAttrType));
            mod.setAttribute(attrBuilder.toAttribute());
          }
        }
      }
      msg.setMods(mods);
      numResolvedNamingConflicts.incrementAndGet();
      return false;
    }
    else
    {
      // The other type of errors can not be caused by naming conflicts.
      // Log a message for the repair tool.
      Message message = ERR_ERROR_REPLAYING_OPERATION.get(
          op.toString(), ctx.getChangeNumber().toString(),
          result.toString(), op.getErrorMessage().toString());
      logError(message);
      return true;
    }
  }

 /**
  * Solve a conflict detected when replaying a delete operation.
  *
  * @param op The operation that triggered the conflict detection.
  * @param msg The operation that triggered the conflict detection.
  * @return true if the process is completed, false if it must continue..
  */
 private boolean solveNamingConflict(DeleteOperation op,
     UpdateMsg msg)
 {
   ResultCode result = op.getResultCode();
   DeleteContext ctx = (DeleteContext) op.getAttachment(SYNCHROCONTEXT);
   String entryUid = ctx.getEntryUid();

   if (result == ResultCode.NO_SUCH_OBJECT)
   {
     /*
      * Find if the entry is still in the database.
      */
     DN currentDn = findEntryDN(entryUid);
     if (currentDn == null)
     {
       /*
        * The entry has already been deleted, either because this delete
        * has already been replayed or because another concurrent delete
        * has already done the job.
        * In any case, there is is nothing more to do.
        */
       numResolvedNamingConflicts.incrementAndGet();
       return true;
     }
     else
     {
       /*
        * This entry has been renamed, replay the delete using its new DN.
        */
       msg.setDn(currentDn.toString());
       numResolvedNamingConflicts.incrementAndGet();
       return false;
     }
   }
   else if (result == ResultCode.NOT_ALLOWED_ON_NONLEAF)
   {
     /*
      * This may happen when we replay a DELETE done on a master
      * but children of this entry have been added on another master.
      *
      * Rename all the children by adding entryuuid in dn and delete this entry.
      *
      * The action taken here must be consistent with the actions
      * done in the solveNamingConflict(AddOperation) method
      * when we are adding an entry whose parent entry has already been deleted.
      */
     findAndRenameChild(entryUid, op.getEntryDN(), op);
     numUnresolvedNamingConflicts.incrementAndGet();
     return false;
   }
   else
   {
     // The other type of errors can not be caused by naming conflicts.
     // Log a message for the repair tool.
     Message message = ERR_ERROR_REPLAYING_OPERATION.get(
         op.toString(), ctx.getChangeNumber().toString(),
         result.toString(), op.getErrorMessage().toString());
     logError(message);
     return true;
   }
 }

  /**
 * Solve a conflict detected when replaying a Modify DN operation.
 *
 * @param op The operation that triggered the conflict detection.
 * @param msg The operation that triggered the conflict detection.
 * @return true if the process is completed, false if it must continue.
 * @throws Exception When the operation is not valid.
 */
private boolean solveNamingConflict(ModifyDNOperation op,
    UpdateMsg msg) throws Exception
{
  ResultCode result = op.getResultCode();
  ModifyDnContext ctx = (ModifyDnContext) op.getAttachment(SYNCHROCONTEXT);
  String entryUid = ctx.getEntryUid();
  String newSuperiorID = ctx.getNewParentId();

  /*
   * four possible cases :
   * - the modified entry has been renamed
   * - the new parent has been renamed
   * - the operation is replayed for the second time.
   * - the entry has been deleted
   * action :
   *  - change the target dn and the new parent dn and
   *        restart the operation,
   *  - don't do anything if the operation is replayed.
   */

  // get the current DN of this entry in the database.
  DN currentDN = findEntryDN(entryUid);

  // Construct the new DN to use for the entry.
  DN entryDN = op.getEntryDN();
  DN newSuperior = null;
  RDN newRDN = op.getNewRDN();

  if (newSuperiorID != null)
  {
    newSuperior = findEntryDN(newSuperiorID);
  }
  else
  {
    newSuperior = entryDN.getParent();
  }

  //If we could not find the new parent entry, we missed this entry
  // earlier or it has disappeared from the database
  // Log this information for the repair tool and mark the entry
  // as conflicting.
  // stop the processing.
  if (newSuperior == null)
  {
    markConflictEntry(op, currentDN, currentDN.getParent().concat(newRDN));
    numUnresolvedNamingConflicts.incrementAndGet();
    return true;
  }

  DN newDN = newSuperior.concat(newRDN);

  if (currentDN == null)
  {
    // The entry targeted by the Modify DN is not in the database
    // anymore.
    // This is a conflict between a delete and this modify DN.
    // The entry has been deleted, we can safely assume
    // that the operation is completed.
    numResolvedNamingConflicts.incrementAndGet();
    return true;
  }

  // if the newDN and the current DN match then the operation
  // is a no-op (this was probably a second replay)
  // don't do anything.
  if (newDN.equals(currentDN))
  {
    numResolvedNamingConflicts.incrementAndGet();
    return true;
  }

  if ((result == ResultCode.NO_SUCH_OBJECT) ||
      (result == ResultCode.UNWILLING_TO_PERFORM) ||
      (result == ResultCode.OBJECTCLASS_VIOLATION))
  {
    /*
     * The entry or it's new parent has not been found
     * reconstruct the operation with the DN we just built
     */
    ModifyDNMsg modifyDnMsg = (ModifyDNMsg) msg;
    msg.setDn(currentDN.toString());
    modifyDnMsg.setNewSuperior(newSuperior.toString());
    numResolvedNamingConflicts.incrementAndGet();
    return false;
  }
  else if (result == ResultCode.ENTRY_ALREADY_EXISTS)
  {
    /*
     * This may happen when two modifyDn operation
     * are done on different servers but with the same target DN
     * add the conflict object class to the entry
     * and rename it using its entryuuid.
     */
    ModifyDNMsg modifyDnMsg = (ModifyDNMsg) msg;
    markConflictEntry(op, op.getEntryDN(), newDN);
    modifyDnMsg.setNewRDN(generateConflictRDN(entryUid,
                          modifyDnMsg.getNewRDN()));
    modifyDnMsg.setNewSuperior(newSuperior.toString());
    numUnresolvedNamingConflicts.incrementAndGet();
    return false;
  }
  else
  {
    // The other type of errors can not be caused by naming conflicts.
    // Log a message for the repair tool.
    Message message = ERR_ERROR_REPLAYING_OPERATION.get(
        op.toString(), ctx.getChangeNumber().toString(),
        result.toString(), op.getErrorMessage().toString());
    logError(message);
    return true;
  }
}


  /**
   * Solve a conflict detected when replaying a ADD operation.
   *
   * @param op The operation that triggered the conflict detection.
   * @param msg The message that triggered the conflict detection.
   * @return true if the process is completed, false if it must continue.
   * @throws Exception When the operation is not valid.
   */
  private boolean solveNamingConflict(AddOperation op,
      AddMsg msg) throws Exception
  {
    ResultCode result = op.getResultCode();
    AddContext ctx = (AddContext) op.getAttachment(SYNCHROCONTEXT);
    String entryUid = ctx.getEntryUid();
    String parentUniqueId = ctx.getParentUid();

    if (result == ResultCode.NO_SUCH_OBJECT)
    {
      /*
       * This can happen if the parent has been renamed or deleted
       * find the parent dn and calculate a new dn for the entry
       */
      if (parentUniqueId == null)
      {
        /*
         * This entry is the base dn of the backend.
         * It is quite surprising that the operation result be NO_SUCH_OBJECT.
         * There is nothing more we can do except TODO log a
         * message for the repair tool to look at this problem.
         */
        return true;
      }
      DN parentDn = findEntryDN(parentUniqueId);
      if (parentDn == null)
      {
        /*
         * The parent has been deleted
         * rename the entry as a conflicting entry.
         * The action taken here must be consistent with the actions
         * done when in the solveNamingConflict(DeleteOperation) method
         * when we are deleting an entry that have some child entries.
         */
        addConflict(msg);

        msg.setDn(generateConflictRDN(entryUid,
                    op.getEntryDN().getRDN().toString()) + ","
                    + baseDn);
        // reset the parent uid so that the check done is the handleConflict
        // phase does not fail.
        msg.setParentUid(null);
        numUnresolvedNamingConflicts.incrementAndGet();
        return false;
      }
      else
      {
        RDN entryRdn = DN.decode(msg.getDn()).getRDN();
        msg.setDn(entryRdn + "," + parentDn);
        numResolvedNamingConflicts.incrementAndGet();
        return false;
      }
    }
    else if (result == ResultCode.ENTRY_ALREADY_EXISTS)
    {
      /*
       * This can happen if
       *  - two adds are done on different servers but with the
       *    same target DN.
       *  - the same ADD is being replayed for the second time on this server.
       * if the nsunique ID already exist, assume this is a replay and
       *        don't do anything
       * if the entry unique id do not exist, generate conflict.
       */
      if (findEntryDN(entryUid) != null)
      {
        // entry already exist : this is a replay
        return true;
      }
      else
      {
        addConflict(msg);
        msg.setDn(generateConflictRDN(entryUid, msg.getDn()));
        numUnresolvedNamingConflicts.incrementAndGet();
        return false;
      }
    }
    else
    {
      // The other type of errors can not be caused by naming conflicts.
      // log a message for the repair tool.
      Message message = ERR_ERROR_REPLAYING_OPERATION.get(
          op.toString(), ctx.getChangeNumber().toString(),
          result.toString(), op.getErrorMessage().toString());
      logError(message);
      return true;
    }
  }

  /**
   * Find all the entries below the provided DN and rename them
   * so that they stay below the baseDn of this replicationDomain and
   * use the conflicting name and attribute.
   *
   * @param entryUid   The unique ID of the entry whose child must be renamed.
   * @param entryDN    The DN of the entry whose child must be renamed.
   * @param conflictOp The Operation that generated the conflict.
   */
  private void findAndRenameChild(
      String entryUid, DN entryDN, Operation conflictOp)
  {
    // Find an rename child entries.
    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

    try
    {
      LinkedHashSet<String> attrs = new LinkedHashSet<String>(1);
      attrs.add(ENTRYUIDNAME);

      SearchFilter ALLMATCH;
      ALLMATCH = SearchFilter.createFilterFromString("(objectClass=*)");
      InternalSearchOperation op =
          conn.processSearch(entryDN, SearchScope.SINGLE_LEVEL,
              DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false, ALLMATCH,
              attrs);

      if (op.getResultCode() == ResultCode.SUCCESS)
      {
        LinkedList<SearchResultEntry> entries = op.getSearchEntries();
        if (entries != null)
        {
          for (SearchResultEntry entry : entries)
          {
            markConflictEntry(conflictOp, entry.getDN(), entryDN);
            renameConflictEntry(conflictOp, entry.getDN(),
                                Historical.getEntryUuid(entry));
          }
        }
      }
      else
      {
        // log error and information for the REPAIR tool.
        MessageBuilder mb = new MessageBuilder();
        mb.append(ERR_CANNOT_RENAME_CONFLICT_ENTRY.get());
        mb.append(String.valueOf(entryDN));
        mb.append(" ");
        mb.append(String.valueOf(conflictOp));
        mb.append(" ");
        mb.append(String.valueOf(op.getResultCode()));
        logError(mb.toMessage());
      }
    } catch (DirectoryException e)
    {
      // log error and information for the REPAIR tool.
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_EXCEPTION_RENAME_CONFLICT_ENTRY.get());
      mb.append(String.valueOf(entryDN));
      mb.append(" ");
      mb.append(String.valueOf(conflictOp));
      mb.append(" ");
      mb.append(e.getLocalizedMessage());
      logError(mb.toMessage());
    }
  }


  /**
   * Rename an entry that was conflicting so that it stays below the
   * baseDn of the replicationDomain.
   *
   * @param conflictOp The Operation that caused the conflict.
   * @param dn         The DN of the entry to be renamed.
   * @param uid        The uniqueID of the entry to be renamed.
   */
  private void renameConflictEntry(Operation conflictOp, DN dn, String uid)
  {
    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

    ModifyDNOperation newOp = conn.processModifyDN(
        dn, generateDeleteConflictDn(uid, dn),false, baseDn);

    if (newOp.getResultCode() != ResultCode.SUCCESS)
    {
      // log information for the repair tool.
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_CANNOT_RENAME_CONFLICT_ENTRY.get());
      mb.append(String.valueOf(dn));
      mb.append(" ");
      mb.append(String.valueOf(conflictOp));
      mb.append(" ");
      mb.append(String.valueOf(newOp.getResultCode()));
      logError(mb.toMessage());
    }
  }


  /**
   * Generate a modification to add the conflict attribute to an entry
   * whose Dn is now conflicting with another entry.
   *
   * @param op        The operation causing the conflict.
   * @param currentDN The current DN of the operation to mark as conflicting.
   * @param conflictDN     The newDn on which the conflict happened.
   */
  private void markConflictEntry(Operation op, DN currentDN, DN conflictDN)
  {
    // create new internal modify operation and run it.
    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

    AttributeType attrType = DirectoryServer.getAttributeType(DS_SYNC_CONFLICT,
        true);
    Attribute attr = Attributes.create(attrType, new AttributeValue(
        attrType, conflictDN.toString()));
    List<Modification> mods = new ArrayList<Modification>();
    Modification mod = new Modification(ModificationType.REPLACE, attr);
    mods.add(mod);
    ModifyOperation newOp = conn.processModify(currentDN, mods);
    if (newOp.getResultCode() != ResultCode.SUCCESS)
    {
      // Log information for the repair tool.
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_CANNOT_ADD_CONFLICT_ATTRIBUTE.get());
      mb.append(String.valueOf(op));
      mb.append(" ");
      mb.append(String.valueOf(newOp.getResultCode()));
      logError(mb.toMessage());
    }

    // Generate an alert to let the administration know that some
    // conflict could not be solved.
    Message alertMessage = NOTE_UNRESOLVED_CONFLICT.get(conflictDN.toString());
    DirectoryServer.sendAlertNotification(this,
        ALERT_TYPE_REPLICATION_UNRESOLVED_CONFLICT, alertMessage);
  }

  /**
   * Add the conflict attribute to an entry that could
   * not be added because it is conflicting with another entry.
   *
   * @param msg            The conflicting Add Operation.
   *
   * @throws ASN1Exception When an encoding error happened manipulating the
   *                       msg.
   */
  private void addConflict(AddMsg msg) throws ASN1Exception
  {
    // Generate an alert to let the administrator know that some
    // conflict could not be solved.
    Message alertMessage = NOTE_UNRESOLVED_CONFLICT.get(msg.getDn());
    DirectoryServer.sendAlertNotification(this,
        ALERT_TYPE_REPLICATION_UNRESOLVED_CONFLICT, alertMessage);

    // Add the conflict attribute
    msg.addAttribute(DS_SYNC_CONFLICT, msg.getDn());
  }

  /**
   * Generate the Dn to use for a conflicting entry.
   *
   * @param entryUid The unique identifier of the entry involved in the
   * conflict.
   * @param rdn Original rdn.
   * @return The generated RDN for a conflicting entry.
   */
  private String generateConflictRDN(String entryUid, String rdn)
  {
    return "entryuuid=" + entryUid + "+" + rdn;
  }

  /**
   * Generate the RDN to use for a conflicting entry whose father was deleted.
   *
   * @param entryUid The unique identifier of the entry involved in the
   *                 conflict.
   * @param dn       The original DN of the entry.
   *
   * @return The generated RDN for a conflicting entry.
   * @throws DirectoryException
   */
  private RDN generateDeleteConflictDn(String entryUid, DN dn)
  {
    String newRDN =  "entryuuid=" + entryUid + "+" + dn.getRDN();
    RDN rdn = null;
    try
    {
      rdn = RDN.decode(newRDN);
    } catch (DirectoryException e)
    {
      // cannot happen
    }
    return rdn;
  }

  /**
   * Check if an operation must be processed as an assured operation.
   *
   * @param op the operation to be checked.
   * @return true if the operations must be processed as an assured operation.
   */
  private boolean isAssured(PostOperationOperation op)
  {
    // TODO : should have a filtering mechanism for checking
    // operation that are assured and operations that are not.
    return false;
  }

  /**
   * Get the maximum receive window size.
   *
   * @return The maximum receive window size.
   */
  public int getMaxRcvWindow()
  {
    if (broker != null)
      return broker.getMaxRcvWindow();
    else
      return 0;
  }

  /**
   * Get the current receive window size.
   *
   * @return The current receive window size.
   */
  public int getCurrentRcvWindow()
  {
    if (broker != null)
      return broker.getCurrentRcvWindow();
    else
      return 0;
  }

  /**
   * Get the maximum send window size.
   *
   * @return The maximum send window size.
   */
  public int getMaxSendWindow()
  {
    if (broker != null)
      return broker.getMaxSendWindow();
    else
      return 0;
  }

  /**
   * Get the current send window size.
   *
   * @return The current send window size.
   */
  public int getCurrentSendWindow()
  {
    if (broker != null)
      return broker.getCurrentSendWindow();
    else
      return 0;
  }

  /**
   * Get the number of times the replication connection was lost.
   * @return The number of times the replication connection was lost.
   */
  public int getNumLostConnections()
  {
    if (broker != null)
      return broker.getNumLostConnections();
    else
      return 0;
  }

  /**
   * Get the number of modify conflicts successfully resolved.
   * @return The number of modify conflicts successfully resolved.
   */
  public int getNumResolvedModifyConflicts()
  {
    return numResolvedModifyConflicts.get();
  }

  /**
   * Get the number of namign conflicts successfully resolved.
   * @return The number of naming conflicts successfully resolved.
   */
  public int getNumResolvedNamingConflicts()
  {
    return numResolvedNamingConflicts.get();
  }

  /**
   * Get the number of unresolved conflicts.
   * @return The number of unresolved conflicts.
   */
  public int getNumUnresolvedNamingConflicts()
  {
    return numUnresolvedNamingConflicts.get();
  }

  /**
   * Get the server ID.
   * @return The server ID.
   */
  public short getServerId()
  {
    return serverId;
  }

  /**
   * Check if the domain solve conflicts.
   *
   * @return a boolean indicating if the domain should sove conflicts.
   */
  public boolean solveConflict()
  {
    return solveConflictFlag;
  }

  /**
   * Disable the replication on this domain.
   * The session to the replication server will be stopped.
   * The domain will not be destroyed but call to the pre-operation
   * methods will result in failure.
   * The listener thread will be destroyed.
   * The monitor informations will still be accessible.
   */
  public void disable()
  {
    state.save();
    state.clearInMemory();
    disabled = true;

    // Stop the listener thread
    if (listenerThread != null)
      listenerThread.shutdown();

    broker.stop(); // This will cut the session and wake up the listener

    // Wait for the listener thread to stop
    if (listenerThread != null)
      listenerThread.waitForShutdown();
  }

  /**
   * Do what necessary when the data have changed : load state, load
   * generation Id.
   * @exception DirectoryException Thrown when an error occurs.
  */
  protected void loadDataState()
  throws DirectoryException
  {
    state.clearInMemory();
    state.loadState();
    generator.adjust(state.getMaxChangeNumber(serverId));
    // Retrieves the generation ID associated with the data imported
    generationId = loadGenerationId();
  }

  /**
   * Enable back the domain after a previous disable.
   * The domain will connect back to a replication Server and
   * will recreate threads to listen for messages from the Synchronization
   * server.
   * The generationId will be retrieved or computed if necessary.
   * The ServerState will also be read again from the local database.
   */
  public void enable()
  {
    try
    {
      loadDataState();
    }
    catch (Exception e)
    {
      /* TODO should mark that replicationServer service is
       * not available, log an error and retry upon timeout
       * should we stop the modifications ?
       */
      logError(ERR_LOADING_GENERATION_ID.get(
          baseDn.toNormalizedString(), e.getLocalizedMessage()));
      return;
    }

    // After an on-line import, the value of the generationId is new
    // and it is necessary for the broker to send this new value as part
    // of the serverStart message.
    broker.setGenerationId(generationId);

    broker.start(replicationServers);

    // Create the listener thread
    listenerThread = new ListenerThread(this, updateToReplayQueue);
    listenerThread.start();

    disabled = false;
  }

  /**
   * Compute the data generationId associated with the current data present
   * in the backend for this domain.
   * @return The computed generationId.
   * @throws DirectoryException When an error occurs.
   */
  public long computeGenerationId() throws DirectoryException
  {
    long genId = exportBackend(true);

    if (debugEnabled())
      TRACER.debugInfo("Computed generationId: generationId=" + genId);

    return genId;
  }

  /**
   * Returns the generationId set for this domain.
   *
   * @return The generationId.
   */
  public long getGenerationId()
  {
    return generationId;
  }

  /**
   * The attribute name used to store the state in the backend.
   */
  protected static final String REPLICATION_GENERATION_ID =
    "ds-sync-generation-id";

  /**
   * Stores the value of the generationId.
   * @param generationId The value of the generationId.
   * @return a ResultCode indicating if the method was successfull.
   */
  public ResultCode saveGenerationId(long generationId)
  {
    // The generationId is stored in the root entry of the domain.
    ASN1OctetString asn1BaseDn = new ASN1OctetString(baseDn.toString());

    ArrayList<ASN1OctetString> values = new ArrayList<ASN1OctetString>();
    ASN1OctetString value = new ASN1OctetString(Long.toString(generationId));
    values.add(value);

    LDAPAttribute attr =
      new LDAPAttribute(REPLICATION_GENERATION_ID, values);
    LDAPModification mod = new LDAPModification(ModificationType.REPLACE, attr);
    ArrayList<RawModification> mods = new ArrayList<RawModification>(1);
    mods.add(mod);

    ModifyOperationBasis op =
      new ModifyOperationBasis(conn, InternalClientConnection.nextOperationID(),
          InternalClientConnection.nextMessageID(),
          new ArrayList<Control>(0), asn1BaseDn,
          mods);
    op.setInternalOperation(true);
    op.setSynchronizationOperation(true);
    op.setDontSynchronize(true);

    op.run();

    ResultCode result = op.getResultCode();
    if (result != ResultCode.SUCCESS)
    {
      generationIdSavedStatus = false;
      if (result != ResultCode.NO_SUCH_OBJECT)
      {
        // The case where the backend is empty (NO_SUCH_OBJECT)
        // is not an error case.
        Message message = ERR_UPDATING_GENERATION_ID.get(
            op.getResultCode().getResultCodeName() + " " +
            op.getErrorMessage(),
            baseDn.toString());
        logError(message);
      }
    }
    else
    {
      generationIdSavedStatus = true;
    }
    return result;
  }


  /**
   * Load the GenerationId from the root entry of the domain
   * from the REPLICATION_GENERATION_ID attribute in database
   * to memory, or compute it if not found.
   *
   * @return generationId The retrieved value of generationId
   * @throws DirectoryException When an error occurs.
   */
  public long loadGenerationId()
  throws DirectoryException
  {
    long generationId=-1;

    if (debugEnabled())
      TRACER.debugInfo(
          "Attempt to read generation ID from DB " + baseDn.toString());

    ASN1OctetString asn1BaseDn = new ASN1OctetString(baseDn.toString());
    boolean found = false;
    LDAPFilter filter;
    try
    {
      filter = LDAPFilter.decode("objectclass=*");
    }
    catch (LDAPException e)
    {
      // can not happen
      return -1;
    }

    /*
     * Search the database entry that is used to periodically
     * save the ServerState
     */
    InternalSearchOperation search = null;
    LinkedHashSet<String> attributes = new LinkedHashSet<String>(1);
    attributes.add(REPLICATION_GENERATION_ID);
    search = conn.processSearch(asn1BaseDn,
        SearchScope.BASE_OBJECT,
        DereferencePolicy.DEREF_ALWAYS, 0, 0, false,
        filter,attributes);
    if (((search.getResultCode() != ResultCode.SUCCESS)) &&
        ((search.getResultCode() != ResultCode.NO_SUCH_OBJECT)))
    {
      Message message = ERR_SEARCHING_GENERATION_ID.get(
          search.getResultCode().getResultCodeName() + " " +
          search.getErrorMessage(),
          baseDn.toString());
      logError(message);
    }

    SearchResultEntry resultEntry = null;
    if (search.getResultCode() == ResultCode.SUCCESS)
    {
      LinkedList<SearchResultEntry> result = search.getSearchEntries();
      resultEntry = result.getFirst();
      if (resultEntry != null)
      {
        AttributeType synchronizationGenIDType =
          DirectoryServer.getAttributeType(REPLICATION_GENERATION_ID);
        List<Attribute> attrs =
          resultEntry.getAttribute(synchronizationGenIDType);
        if (attrs != null)
        {
          Attribute attr = attrs.get(0);
          if (attr.size()>1)
          {
            Message message = ERR_LOADING_GENERATION_ID.get(
                baseDn.toString(), "#Values=" + attr.size() +
                " Must be exactly 1 in entry " +
                resultEntry.toLDIFString());
            logError(message);
          }
          else if (attr.size() == 1)
          {
            found=true;
            try
            {
              generationId = Long.decode(attr.iterator().next().
                  getStringValue());
            }
            catch(Exception e)
            {
              Message message = ERR_LOADING_GENERATION_ID.get(
                baseDn.toString(), e.getLocalizedMessage());
              logError(message);
            }
          }
        }
      }
    }

    if (!found)
    {
      generationId = computeGenerationId();
      saveGenerationId(generationId);

      if (debugEnabled())
        TRACER.debugInfo("Generation ID created for domain base DN=" +
            baseDn.toString() +
            " generationId=" + generationId);
    }
    else
    {
      generationIdSavedStatus = true;
      if (debugEnabled())
        TRACER.debugInfo(
            "Generation ID successfully read from domain base DN=" + baseDn +
            " generationId=" + generationId);
    }
    return generationId;
  }

  /**
   * Reset the generationId of this domain in the whole topology.
   * A message is sent to the Replication Servers for them to reset
   * their change dbs.
   *
   * @param generationIdNewValue The new value of the generation Id.
   * @throws DirectoryException when an error occurs
   */
  public void resetGenerationId(Long generationIdNewValue)
  throws DirectoryException
  {
    if (debugEnabled())
      TRACER.debugInfo(
          this.getName() + "resetGenerationId" + generationIdNewValue);

    if (!isConnected())
    {
      ResultCode resultCode = ResultCode.OTHER;
      Message message = ERR_RESET_GENERATION_CONN_ERR_ID.get(
          baseDn.toNormalizedString());
      throw new DirectoryException(
         resultCode, message);
    }

    ResetGenerationIdMsg genIdMessage = null;

    if (generationIdNewValue == null)
    {
      genIdMessage = new ResetGenerationIdMsg(this.generationId);
    }
    else
    {
      genIdMessage = new ResetGenerationIdMsg(generationIdNewValue);
    }
    broker.publish(genIdMessage);
  }

  /**
   * Do whatever is needed when a backup is started.
   * We need to make sure that the serverState is correclty save.
   */
  public void backupStart()
  {
    state.save();
  }

  /**
   * Do whatever is needed when a backup is finished.
   */
  public void backupEnd()
  {
    // Nothing is needed at the moment
  }

  /*
   * Total Update >>
   */

  /**
   * Receives bytes related to an entry in the context of an import to
   * initialize the domain (called by ReplLDIFInputStream).
   *
   * @return The bytes. Null when the Done or Err message has been received
   */
  public byte[] receiveEntryBytes()
  {
    ReplicationMsg msg;
    while (true)
    {
      try
      {
        msg = broker.receive();

        if (debugEnabled())
          TRACER.debugVerbose(
              " sid:" + this.serverId +
              " base DN:" + this.baseDn +
              " Import EntryBytes received " + msg);
        if (msg == null)
        {
          // The server is in the shutdown process
          return null;
        }

        if (msg instanceof EntryMsg)
        {
          EntryMsg entryMsg = (EntryMsg)msg;
          byte[] entryBytes = entryMsg.getEntryBytes();
          ieContext.updateCounters();
          return entryBytes;
        }
        else if (msg instanceof DoneMsg)
        {
          // This is the normal termination of the import
          // No error is stored and the import is ended
          // by returning null
          return null;
        }
        else if (msg instanceof ErrorMsg)
        {
          // This is an error termination during the import
          // The error is stored and the import is ended
          // by returning null
          ErrorMsg errorMsg = (ErrorMsg)msg;
          ieContext.exception = new DirectoryException(
                                      ResultCode.OTHER,
                                      errorMsg.getDetails());
          return null;
        }
        else
        {
          // Other messages received during an import are trashed
        }
      }
      catch(Exception e)
      {
        // TODO: i18n
        ieContext.exception = new DirectoryException(ResultCode.OTHER,
            Message.raw("received an unexpected message type" +
                e.getLocalizedMessage()));
      }
    }
  }

  /**
   * Processes an error message received while an import/export is
   * on going.
   * @param errorMsg The error message received.
   */
  protected void abandonImportExport(ErrorMsg errorMsg)
  {
    // FIXME TBD Treat the case where the error happens while entries
    // are being exported

    if (debugEnabled())
      TRACER.debugVerbose(
          " abandonImportExport:" + this.serverId +
          " base DN:" + this.baseDn +
          " Error Msg received " + errorMsg);

    if (ieContext != null)
    {
      ieContext.exception = new DirectoryException(ResultCode.OTHER,
          errorMsg.getDetails());

      if (ieContext.initializeTask instanceof InitializeTask)
      {
        // Update the task that initiated the import
        ((InitializeTask)ieContext.initializeTask).
        updateTaskCompletionState(ieContext.exception);

        releaseIEContext();
      }
    }
  }

  /**
   * Clears all the entries from the JE backend determined by the
   * be id passed into the method.
   *
   * @param  createBaseEntry  Indicate whether to automatically create the base
   *                          entry and add it to the backend.
   * @param beID  The be id to clear.
   * @param dn   The suffix of the backend to create if the the createBaseEntry
   *             boolean is true.
   * @throws Exception  If an unexpected problem occurs.
   */
  public static void clearJEBackend(boolean createBaseEntry, String beID,
      String dn) throws Exception
  {
    BackendImpl backend = (BackendImpl)DirectoryServer.getBackend(beID);

    // FIXME Should setBackendEnabled be part of TaskUtils ?
    TaskUtils.disableBackend(beID);

    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();

      if (!LockFileManager.acquireExclusiveLock(lockFile, failureReason))
      {
        throw new RuntimeException(failureReason.toString());
      }

      try
      {
        backend.clearBackend();
      }
      finally
      {
        LockFileManager.releaseLock(lockFile, failureReason);
      }
    }
    finally
    {
      TaskUtils.enableBackend(beID);
    }

    if (createBaseEntry)
    {
      DN baseDN = DN.decode(dn);
      Entry e = createEntry(baseDN);
      backend = (BackendImpl)DirectoryServer.getBackend(beID);
      backend.addEntry(e, null);
    }
  }

  /**
   * Export the entries from the backend and/or compute the generation ID.
   * The ieContext must have been set before calling.
   * @param checksumOutput true is the exportBackend is called to compute
   *                       the generationID
   *
   * @return The computed  generationID.
   *
   * @throws DirectoryException when an error occurred
   */
  protected long exportBackend(boolean checksumOutput)
  throws DirectoryException
  {
    long genID = 0;
    Backend backend = retrievesBackend(this.baseDn);
    long bec = backend.numSubordinates(baseDn, true) + 1;
    long entryCount = (bec<1000?bec:1000);

    //  Acquire a shared lock for the backend.
    try
    {
      String lockFile = LockFileManager.getBackendLockFileName(backend);
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.acquireSharedLock(lockFile, failureReason))
      {
        Message message = ERR_LDIFEXPORT_CANNOT_LOCK_BACKEND.get(
            backend.getBackendID(), String.valueOf(failureReason));
        logError(message);
        throw new DirectoryException(
            ResultCode.OTHER, message, null);
      }
    }
    catch (Exception e)
    {
      Message message =
          ERR_LDIFEXPORT_CANNOT_LOCK_BACKEND.get(
                  backend.getBackendID(), e.getLocalizedMessage());
      logError(message);
      throw new DirectoryException(
          ResultCode.OTHER, message, null);
    }

    OutputStream os;
    ReplLDIFOutputStream ros;

    if (checksumOutput)
    {
      ros = new ReplLDIFOutputStream(this, entryCount);
      os = new CheckedOutputStream(ros, new GenerationIdChecksum());
      try
      {
        os.write((Long.toString(backend.numSubordinates(baseDn, true) + 1)).
            getBytes());
      }
      catch(Exception e)
      {
        // Should never happen
      }
    }
    else
    {
      ros = new ReplLDIFOutputStream(this, (short)-1);
      os = ros;
    }
    LDIFExportConfig exportConfig = new LDIFExportConfig(os);

    // baseDn branch is the only one included in the export
    List<DN> includeBranches = new ArrayList<DN>(1);
    includeBranches.add(this.baseDn);
    exportConfig.setIncludeBranches(includeBranches);

    // For the checksum computing mode, only consider the 'stable' attributes
    if (checksumOutput)
    {
      String includeAttributeStrings[] =
        {"objectclass", "sn", "cn", "entryuuid"};
      HashSet<AttributeType> includeAttributes;
      includeAttributes = new HashSet<AttributeType>();
      for (String attrName : includeAttributeStrings)
      {
        AttributeType attrType  = DirectoryServer.getAttributeType(attrName);
        if (attrType == null)
        {
          attrType = DirectoryServer.getDefaultAttributeType(attrName);
        }
        includeAttributes.add(attrType);
      }
      exportConfig.setIncludeAttributes(includeAttributes);
    }

    //  Launch the export.
    try
    {
      backend.exportLDIF(exportConfig);
    }
    catch (DirectoryException de)
    {
      if ((checksumOutput) &&
          (ros.getNumExportedEntries() >= entryCount))
      {
        // This is the normal end when computing the generationId
        // We can interrupt the export only by an IOException
      }
      else
      {
        Message message =
          ERR_LDIFEXPORT_ERROR_DURING_EXPORT.get(de.getMessageObject());
        logError(message);
        throw new DirectoryException(
            ResultCode.OTHER, message, null);
      }
    }
    catch (Exception e)
    {
      Message message = ERR_LDIFEXPORT_ERROR_DURING_EXPORT.get(
          stackTraceToSingleLineString(e));
      logError(message);
      throw new DirectoryException(
          ResultCode.OTHER, message, null);
    }
    finally
    {
      // Clean up after the export by closing the export config.
      // Will also flush the export and export the remaining entries.
      exportConfig.close();

      if (checksumOutput)
      {
        genID =
         ((CheckedOutputStream)os).getChecksum().getValue();
      }

      //  Release the shared lock on the backend.
      try
      {
        String lockFile = LockFileManager.getBackendLockFileName(backend);
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.releaseLock(lockFile, failureReason))
        {
          Message message = WARN_LDIFEXPORT_CANNOT_UNLOCK_BACKEND.get(
              backend.getBackendID(), String.valueOf(failureReason));
          logError(message);
          throw new DirectoryException(
              ResultCode.OTHER, message, null);
        }
      }
      catch (Exception e)
      {
        Message message = WARN_LDIFEXPORT_CANNOT_UNLOCK_BACKEND.get(
            backend.getBackendID(), stackTraceToSingleLineString(e));
        logError(message);
        throw new DirectoryException(
            ResultCode.OTHER, message, null);
      }
    }
    return genID;
  }

  /**
   * Retrieves the backend related to the domain.
   *
   * @return The backend of that domain.
   * @param baseDn The baseDn to retrieve the backend
   */
  protected static Backend retrievesBackend(DN baseDn)
  {
    // Retrieves the backend related to this domain
    return DirectoryServer.getBackend(baseDn);
  }

  /**
   * Get the internal broker to perform some operations on it.
   *
   * @return The broker for this domain.
   */
  ReplicationBroker getBroker()
  {
    return broker;
  }

  /**
   * Exports an entry in LDIF format.
   *
   * @param  lDIFEntry The entry to be exported..
   *
   * @throws IOException when an error occurred.
   */
  public void exportLDIFEntry(String lDIFEntry) throws IOException
  {
    // If an error was raised - like receiving an ErrorMsg
    // we just let down the export.
    if (ieContext.exception != null)
    {
      IOException ioe = new IOException(ieContext.exception.getMessage());
      ieContext = null;
      throw ioe;
    }

    EntryMsg entryMessage = new EntryMsg(
        serverId, ieContext.exportTarget, lDIFEntry.getBytes());
    broker.publish(entryMessage);

    try
    {
      ieContext.updateCounters();
    }
    catch (DirectoryException de)
    {
      throw new IOException(de.getMessage());
    }
  }

  /**
   * Initializes this domain from another source server.
   *
   * @param source The source from which to initialize
   * @param initTask The task that launched the initialization
   *                 and should be updated of its progress.
   * @throws DirectoryException when an error occurs
   */
  public void initializeFromRemote(short source, Task initTask)
  throws DirectoryException
  {
    if (debugEnabled())
      TRACER.debugInfo("Entering initializeFromRemote");

    acquireIEContext();
    ieContext.initializeTask = initTask;

    InitializeRequestMsg initializeMsg = new InitializeRequestMsg(
        baseDn, serverId, source);

    // Publish Init request msg
    broker.publish(initializeMsg);

    // .. we expect to receive entries or err after that
  }

  /**
   * Verifies that the given string represents a valid source
   * from which this server can be initialized.
   * @param sourceString The string representing the source
   * @return The source as a short value
   * @throws DirectoryException if the string is not valid
   */
  public short decodeSource(String sourceString)
  throws DirectoryException
  {
    short  source = 0;
    Throwable cause = null;
    try
    {
      source = Integer.decode(sourceString).shortValue();
      if ((source >= -1) && (source != serverId))
      {
        // TODO Verifies serverID is in the domain
        // We shold check here that this is a server implied
        // in the current domain.
        return source;
      }
    }
    catch(Exception e)
    {
      cause = e;
    }

    ResultCode resultCode = ResultCode.OTHER;
    Message message = ERR_INVALID_IMPORT_SOURCE.get();
    if (cause != null)
    {
      throw new DirectoryException(
          resultCode, message, cause);
    }
    else
    {
      throw new DirectoryException(
          resultCode, message);
    }
  }

  /**
   * Verifies that the given string represents a valid source
   * from which this server can be initialized.
   * @param targetString The string representing the source
   * @return The source as a short value
   * @throws DirectoryException if the string is not valid
   */
  public short decodeTarget(String targetString)
  throws DirectoryException
  {
    short  target = 0;
    Throwable cause;
    if (targetString.equalsIgnoreCase("all"))
    {
      return RoutableMsg.ALL_SERVERS;
    }

    // So should be a serverID
    try
    {
      target = Integer.decode(targetString).shortValue();
      if (target >= 0)
      {
        // FIXME Could we check now that it is a know server in the domain ?
      }
      return target;
    }
    catch(Exception e)
    {
      cause = e;
    }
    ResultCode resultCode = ResultCode.OTHER;
    Message message = ERR_INVALID_EXPORT_TARGET.get();

    if (cause != null)
      throw new DirectoryException(
          resultCode, message, cause);
    else
      throw new DirectoryException(
          resultCode, message);

  }

  private synchronized void acquireIEContext()
  throws DirectoryException
  {
    if (ieContext != null)
    {
      // Rejects 2 simultaneous exports
      Message message = ERR_SIMULTANEOUS_IMPORT_EXPORT_REJECTED.get();
      throw new DirectoryException(ResultCode.OTHER,
          message);
    }

    ieContext = new IEContext();
  }

  private synchronized void releaseIEContext()
  {
    ieContext = null;
  }

  /**
   * Process the initialization of some other server or servers in the topology
   * specified by the target argument.
   * @param target The target that should be initialized
   * @param initTask The task that triggers this initialization and that should
   *                 be updated with its progress.
   *
   * @exception DirectoryException When an error occurs.
   */
  public void initializeRemote(short target, Task initTask)
  throws DirectoryException
  {
    initializeRemote(target, serverId, initTask);
  }

  /**
   * Process the initialization of some other server or servers in the topology
   * specified by the target argument when this initialization specifying the
   * server that requests the initialization.
   *
   * @param target The target that should be initialized.
   * @param requestorID The server that initiated the export.
   * @param initTask The task that triggers this initialization and that should
   *  be updated with its progress.
   *
   * @exception DirectoryException When an error occurs.
   */
  public void initializeRemote(short target, short requestorID, Task initTask)
  throws DirectoryException
  {
    Message msg = NOTE_FULL_UPDATE_ENGAGED_FOR_REMOTE_START.get(
      Short.toString(serverId),
      baseDn.toString(),
      Short.toString(requestorID));
    logError(msg);

    boolean contextAcquired=false;

    try
    {
      Backend backend = retrievesBackend(this.baseDn);

      if (!backend.supportsLDIFExport())
      {
        Message message = ERR_INIT_EXPORT_NOT_SUPPORTED.get(
                            backend.getBackendID().toString());
        logError(message);
        throw new DirectoryException(ResultCode.OTHER, message);
      }

      acquireIEContext();
      contextAcquired = true;

      // The number of entries to be exported is the number of entries under
      // the base DN entry and the base entry itself.
      long entryCount = backend.numSubordinates(baseDn, true) + 1;
      ieContext.exportTarget = target;
      if (initTask != null)
      {
        ieContext.initializeTask = initTask;
      }
      ieContext.setCounters(entryCount, entryCount);

      // Send start message to the peer
      InitializeTargetMsg initializeMessage = new InitializeTargetMsg(
          baseDn, serverId, ieContext.exportTarget, requestorID, entryCount);

      broker.publish(initializeMessage);

      exportBackend(false);

      // Notify the peer of the success
      DoneMsg doneMsg = new DoneMsg(serverId,
          initializeMessage.getDestination());
      broker.publish(doneMsg);

      releaseIEContext();
    }
    catch(DirectoryException de)
    {
      // Notify the peer of the failure
      ErrorMsg errorMsg =
        new ErrorMsg(target,
                         de.getMessageObject());
      broker.publish(errorMsg);

      if (contextAcquired)
        releaseIEContext();

      throw(de);
    }

    msg = NOTE_FULL_UPDATE_ENGAGED_FOR_REMOTE_END.get(
      Short.toString(serverId),
      baseDn.toString(),
      Short.toString(requestorID));
    logError(msg);
  }

  /**
   * Process backend before import.
   * @param backend The backend.
   * @throws Exception
   */
  private void preBackendImport(Backend backend)
  throws Exception
  {
    // Stop saving state
    stateSavingDisabled = true;

    // FIXME setBackendEnabled should be part of TaskUtils ?
    TaskUtils.disableBackend(backend.getBackendID());

    // Acquire an exclusive lock for the backend.
    String lockFile = LockFileManager.getBackendLockFileName(backend);
    StringBuilder failureReason = new StringBuilder();
    if (! LockFileManager.acquireExclusiveLock(lockFile, failureReason))
    {
      Message message = ERR_INIT_CANNOT_LOCK_BACKEND.get(
                          backend.getBackendID(),
                          String.valueOf(failureReason));
      logError(message);
      throw new DirectoryException(ResultCode.OTHER, message);
    }
  }

  /**
   * Initializes the domain's backend with received entries.
   * @param initializeMessage The message that initiated the import.
   * @exception DirectoryException Thrown when an error occurs.
   */
  protected void initialize(InitializeTargetMsg initializeMessage)
  throws DirectoryException
  {
    LDIFImportConfig importConfig = null;
    DirectoryException de = null;

    Message msg = NOTE_FULL_UPDATE_ENGAGED_FROM_REMOTE_START.get(
      Short.toString(serverId),
      baseDn.toString(),
      Long.toString(initializeMessage.getRequestorID()));
    logError(msg);

    // Go into full update status
    // Use synchronized as somebody could ask another status change at the same
    // time
    synchronized (status)
    {
      StatusMachineEvent event = StatusMachineEvent.TO_FULL_UPDATE_STATUS_EVENT;
      ServerStatus newStatus =
        StatusMachine.computeNewStatus(status, event);

      if (newStatus == ServerStatus.INVALID_STATUS)
      {
        msg = ERR_DS_CANNOT_CHANGE_STATUS.get(baseDn.toString(),
          Short.toString(serverId), status.toString(), event.toString());
        logError(msg);
        return;
      }

      // Store new status
      status = newStatus;

      if (debugEnabled())
      TRACER.debugInfo("Replication domain " + baseDn +
        " new status is: " + status);

      // Perform whatever actions are needed to apply properties for being
      // compliant with new status
      updateDomainForNewStatus();
    }

    Backend backend = retrievesBackend(baseDn);

    try
    {
      if (!backend.supportsLDIFImport())
      {
        Message message = ERR_INIT_IMPORT_NOT_SUPPORTED.get(
            backend.getBackendID().toString());
        logError(message);
        de = new DirectoryException(ResultCode.OTHER, message);
      }
      else
      {
        if (initializeMessage.getRequestorID() == serverId)
        {
          // The import responds to a request we did so the IEContext
          // is already acquired
        }
        else
        {
          acquireIEContext();
        }

        ieContext.importSource = initializeMessage.getsenderID();
        ieContext.entryLeftCount = initializeMessage.getEntryCount();
        ieContext.setCounters(initializeMessage.getEntryCount(),
            initializeMessage.getEntryCount());

        preBackendImport(backend);

        ieContext.ldifImportInputStream = new ReplLDIFInputStream(this);
        importConfig =
          new LDIFImportConfig(ieContext.ldifImportInputStream);
        List<DN> includeBranches = new ArrayList<DN>();
        includeBranches.add(this.baseDn);
        importConfig.setIncludeBranches(includeBranches);
        importConfig.setAppendToExistingData(false);

        // TODO How to deal with rejected entries during the import
        importConfig.writeRejectedEntries(
          getFileForPath("logs" + File.separator +
              "replInitRejectedEntries").getAbsolutePath(),
          ExistingFileBehavior.OVERWRITE);

        // Process import
        backend.importLDIF(importConfig);

        stateSavingDisabled = false;
      }
    }
    catch(Exception e)
    {
      de = new DirectoryException(ResultCode.OTHER,
          Message.raw(e.getLocalizedMessage()));
    }
    finally
    {
      if ((ieContext != null)  && (ieContext.exception != null))
        de = ieContext.exception;

      // Cleanup
      if (importConfig != null)
      {
        importConfig.close();

        // Re-enable backend
        closeBackendImport(backend);

        backend = retrievesBackend(baseDn);
      }

      try
      {
        loadDataState();

        if (debugEnabled())
          TRACER.debugInfo(
              "After import, the replication plugin restarts connections" +
              " to all RSs to provide new generation ID=" + generationId);
        broker.setGenerationId(generationId);
      }
      catch (DirectoryException fe)
      {
        // If we already catch an Exception it's quite possible
        // that the loadDataState() and setGenerationId() fail
        // so we don't bother about the new Exception.
        // However if there was no Exception before we want
        // to return this Exception to the task creator.
        if (de == null)
          de = fe;
      }

      // Re-exchange generationID and state with RS
      broker.reStart();

      // Update the task that initiated the import
      if ((ieContext != null ) && (ieContext.initializeTask != null))
      {
        ((InitializeTask)ieContext.initializeTask).
        updateTaskCompletionState(de);
      }
      releaseIEContext();
    }
    // Sends up the root error.
    if (de != null)
    {
      throw de;
    }

    msg = NOTE_FULL_UPDATE_ENGAGED_FROM_REMOTE_END.get(
      Short.toString(serverId),
      baseDn.toString(),
      Long.toString(initializeMessage.getRequestorID()));
    logError(msg);
  }

  /**
   * Make post import operations.
   * @param backend The backend implied in the import.
   * @exception DirectoryException Thrown when an error occurs.
   */
  protected void closeBackendImport(Backend backend)
  throws DirectoryException
  {
    String lockFile = LockFileManager.getBackendLockFileName(backend);
    StringBuilder failureReason = new StringBuilder();

    // Release lock
    if (!LockFileManager.releaseLock(lockFile, failureReason))
    {
      Message message = WARN_LDIFIMPORT_CANNOT_UNLOCK_BACKEND.get(
          backend.getBackendID(), String.valueOf(failureReason));
      logError(message);
      throw new DirectoryException(ResultCode.OTHER, message);
    }

    TaskUtils.enableBackend(backend.getBackendID());
  }

  /**
   * Retrieves a replication domain based on the baseDn.
   *
   * @param baseDn The baseDn of the domain to retrieve
   * @return The domain retrieved
   * @throws DirectoryException When an error occurred or no domain
   * match the provided baseDn.
   */
  public static ReplicationDomain retrievesReplicationDomain(DN baseDn)
  throws DirectoryException
  {
    ReplicationDomain replicationDomain = null;

    // Retrieves the domain
    DirectoryServer.getSynchronizationProviders();
    for (SynchronizationProvider<?> provider :
      DirectoryServer.getSynchronizationProviders())
    {
      if (!( provider instanceof MultimasterReplication))
      {
        Message message = ERR_INVALID_PROVIDER.get();
        throw new DirectoryException(ResultCode.OTHER,
            message);
      }

      // From the domainDN retrieves the replication domain
      ReplicationDomain sdomain =
        MultimasterReplication.findDomain(baseDn, null);
      if (sdomain == null)
      {
        break;
      }
      if (replicationDomain != null)
      {
        // Should never happen
        Message message = ERR_MULTIPLE_MATCHING_DOMAIN.get();
        throw new DirectoryException(ResultCode.OTHER,
            message);
      }
      replicationDomain = sdomain;
    }

    if (replicationDomain == null)
    {
      MessageBuilder mb = new MessageBuilder(ERR_NO_MATCHING_DOMAIN.get());
      mb.append(" ");
      mb.append(String.valueOf(baseDn));
      throw new DirectoryException(ResultCode.OTHER,
         mb.toMessage());
    }
    return replicationDomain;
  }

  /**
   * Returns the backend associated to this domain.
   * @return The associated backend.
   */
  public Backend getBackend()
  {
    return retrievesBackend(baseDn);
  }

  /**
   * Returns a boolean indicating if an import or export is currently
   * processed.
   * @return The status
   */
  public boolean ieRunning()
  {
    return (ieContext != null);
  }
  /*
   * <<Total Update
   */


  /**
   * Push the modifications contained in the given parameter as
   * a modification that would happen on a local server.
   * The modifications are not applied to the local database,
   * historical information is not updated but a ChangeNumber
   * is generated and the ServerState associated to this domain is
   * updated.
   * @param modifications The modification to push
   */
  public void synchronizeModifications(List<Modification> modifications)
  {
    ModifyOperation opBasis =
      new ModifyOperationBasis(InternalClientConnection.getRootConnection(),
                          InternalClientConnection.nextOperationID(),
                          InternalClientConnection.nextMessageID(),
                          null, DirectoryServer.getSchemaDN(),
                          modifications);
    LocalBackendModifyOperation op = new LocalBackendModifyOperation(opBasis);

    ChangeNumber cn = generateChangeNumber(op);
    OperationContext ctx = new ModifyContext(cn, "schema");
    op.setAttachment(SYNCHROCONTEXT, ctx);
    op.setResultCode(ResultCode.SUCCESS);
    synchronize(op);
  }

  /**
   * Check if the provided configuration is acceptable for add.
   *
   * @param configuration The configuration to check.
   * @param unacceptableReasons When the configuration is not acceptable, this
   *                            table is use to return the reasons why this
   *                            configuration is not acceptable.
   *
   * @return true if the configuration is acceptable, false other wise.
   */
  public static boolean isConfigurationAcceptable(
      ReplicationDomainCfg configuration, List<Message> unacceptableReasons)
  {
    // Check that there is not already a domain with the same DN
    DN dn = configuration.getBaseDN();
    ReplicationDomain domain = MultimasterReplication.findDomain(dn, null);
    if ((domain != null) && (domain.baseDn.equals(dn)))
    {
      Message message = ERR_SYNC_INVALID_DN.get();
      unacceptableReasons.add(message);
      return false;
    }

    // Check that the base DN is configured as a base-dn of the directory server
    if (retrievesBackend(dn) == null)
    {
      Message message = ERR_UNKNOWN_DN.get(dn.toString());
      unacceptableReasons.add(message);
      return false;
    }
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
         ReplicationDomainCfg configuration)
  {
    // server id and base dn are readonly.
    // isolationPolicy can be set immediately and will apply
    // to the next updates.
    // The other parameters needs to be renegociated with the ReplicationServer
    // so that requires restarting the session with the ReplicationServer.
    Boolean needToRestartSession = false;
    Collection<String> newReplServers = configuration.getReplicationServer();

    // A new session is necessary only when information regarding
    // the connection is modified
    if ((!(replicationServers.size() == newReplServers.size()
        && replicationServers.containsAll(newReplServers))) ||
        window != configuration.getWindowSize() ||
        heartbeatInterval != configuration.getHeartbeatInterval())
      needToRestartSession = true;

    replicationServers = newReplServers;
    window = configuration.getWindowSize();
    heartbeatInterval = configuration.getHeartbeatInterval();
    broker.changeConfig(replicationServers, maxReceiveQueue, maxReceiveDelay,
        maxSendQueue, maxSendDelay, window, heartbeatInterval);
    isolationpolicy = configuration.getIsolationPolicy();

    // To be able to stop and restart the broker properly just
    // disable and enable the domain. That way a new session
    // with the new configuration is available.
    if (needToRestartSession)
    {
      this.disable();
      this.enable();
    }

    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
         ReplicationDomainCfg configuration, List<Message> unacceptableReasons)
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public LinkedHashMap<String, String> getAlerts()
  {
    LinkedHashMap<String,String> alerts = new LinkedHashMap<String,String>();

    alerts.put(ALERT_TYPE_REPLICATION_UNRESOLVED_CONFLICT,
               ALERT_DESCRIPTION_REPLICATION_UNRESOLVED_CONFLICT);
    return alerts;
  }

  /**
   * {@inheritDoc}
   */
  public String getClassName()
  {
    return CLASS_NAME;

  }

  /**
   * {@inheritDoc}
   */
  public DN getComponentEntryDN()
  {
    return configDn;
  }

  /**
   * Check if the domain is connected to a ReplicationServer.
   *
   * @return true if the server is connected, false if not.
   */
  public boolean isConnected()
  {
    if (broker != null)
      return broker.isConnected();
    else
      return false;
  }

  /**
   * Determine whether the connection to the replication server is encrypted.
   * @return true if the connection is encrypted, false otherwise.
   */
  public boolean isSessionEncrypted()
  {
    if (broker != null)
      return broker.isSessionEncrypted();
    else
      return false;
  }

  /**
   * Gets the info for DSs in the topology (except us).
   * @return The info for DSs in the topology (except us)
   */
  public List<DSInfo> getDsList()
  {
    return dsList;
  }

  /**
   * Gets the info for RSs in the topology (except the one we are connected
   * to).
   * @return The info for RSs in the topology (except the one we are connected
   * to)
   */
  public List<RSInfo> getRsList()
  {
    return rsList;
  }

  /**
   * Tells if assured replication is enabled for this domain.
   * @return True if assured replication is enabled for this domain.
   */
  public boolean isAssured()
  {
    return assured;
  }

  /**
   * Gives the mode for the assured replication of the domain.
   * @return The mode for the assured replication of the domain.
   */
  public AssuredMode getAssuredMode()
  {
    return assuredMode;
  }

  /**
   * Gives the assured level of the replication of the domain.
   * @return The assured level of the replication of the domain.
   */
  public byte getAssuredSdLevel()
  {
    return assuredSdLevel;
  }

  /**
   * Gets the group id for this domain.
   * @return The group id for this domain.
   */
  public byte getGroupId()
  {
    return groupId;
  }

  /**
   * Gets the referrals URLs this domain publishes.
   * @return The referrals URLs this domain publishes.
   */
  public List<String> getRefUrls()
  {
    return refUrls;
  }

  /**
   * Gets the status for this domain.
   * @return The status for this domain.
   */
  public ServerStatus getStatus()
  {
    return status;
  }
}
