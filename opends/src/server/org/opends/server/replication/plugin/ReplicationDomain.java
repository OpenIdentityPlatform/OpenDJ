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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.replication.plugin;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

import static org.opends.server.config.ConfigConstants.DN_BACKEND_BASE;
import static org.opends.server.loggers.ErrorLogger.logError;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.messages.ToolMessages.*;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.replication.plugin.Historical.ENTRYUIDNAME;
import static org.opends.server.replication.protocol.OperationContext.*;
import static org.opends.server.util.StaticUtils.createEntry;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DataFormatException;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.MultimasterDomainCfgDefn.*;
import org.opends.server.admin.std.server.MultimasterDomainCfg;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.backends.jeb.BackendImpl;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.common.ChangeNumberGenerator;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.AckMessage;
import org.opends.server.replication.protocol.AddContext;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteContext;
import org.opends.server.replication.protocol.DoneMessage;
import org.opends.server.replication.protocol.EntryMessage;
import org.opends.server.replication.protocol.ErrorMessage;
import org.opends.server.replication.protocol.InitializeRequestMessage;
import org.opends.server.replication.protocol.InitializeTargetMessage;
import org.opends.server.replication.protocol.ModifyContext;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.ModifyDnContext;
import org.opends.server.replication.protocol.OperationContext;
import org.opends.server.replication.protocol.RoutableMessage;
import org.opends.server.replication.protocol.ReplicationMessage;
import org.opends.server.replication.protocol.UpdateMessage;
import org.opends.server.tasks.InitializeTargetTask;
import org.opends.server.tasks.InitializeTask;
import org.opends.server.tasks.TaskUtils;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.ConfigChangeResult;
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
       implements ConfigurationChangeListener<MultimasterDomainCfg>
{
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

  /**
   * on shutdown, the server will wait for existing threads to stop
   * during this timeout (in ms).
   */
  private static final int SHUTDOWN_JOIN_TIMEOUT = 30000;

  private ReplicationMonitor monitor;

  private ReplicationBroker broker;

  private List<ListenerThread> synchroThreads =
    new ArrayList<ListenerThread>();
  private SortedMap<ChangeNumber, UpdateMessage> waitingAckMsgs =
    new TreeMap<ChangeNumber, UpdateMessage>();
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

  /**
   * This object is used to store the list of update currently being
   * done on the local database.
   * Is is usefull to make sure that the local operations are sent in a
   * correct order to the replication server and that the ServerState
   * is not updated too early.
   */
  private PendingChanges pendingChanges;

  /**
   * It contain the updates that were done on other servers, transmitted
   * by the replication server and that are currently replayed.
   * It is usefull to make sure that dependencies between operations
   * are correctly fullfilled and to to make sure that the ServerState is
   * not updated too early.
   */
  private RemotePendingChanges remotePendingChanges;

  /**
   * The time in milliseconds between heartbeats from the replication
   * server.  Zero means heartbeats are off.
   */
  private long heartbeatInterval = 0;
  short serverId;

  // The context related to an import or export being processed
  // Null when none is being processed.
  private IEContext ieContext = null;

  // The backend information necessary to make an import or export.
  private Backend backend;

  private int listenerThreadNumber = 10;

  private Collection<String> replicationServers;

  private DN baseDN;

  private boolean shutdown = false;

  private InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

  private boolean solveConflictFlag = true;

  private boolean disabled = false;
  private boolean stateSavingDisabled = false;

  private int window = 100;

  /**
   * The isoalation policy that this domain is going to use.
   * This field describes the behavior of the domain when an update is
   * attempted and the domain could not connect to any Replication Server.
   * Possible values are accept-updates or deny-updates, but other values
   * may be added in the futur.
   */
  private IsolationPolicy isolationpolicy;

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
    short exportTarget = RoutableMessage.UNKNOWN_SERVER;
    // The source in the case of an import
    short importSource = RoutableMessage.UNKNOWN_SERVER;

    // The total entry count expected to be processed
    long entryCount = 0;
    // The count for the entry left to be processed
    long entryLeftCount = 0;

    // The exception raised when any
    DirectoryException exception = null;

    /**
     * Initializes the counters of the task with the provider value.
     * @param count The value with which to initialize the counters.
     */
    public void initTaskCounters(long count)
    {
      entryCount = count;
      entryLeftCount = count;

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
     */
    public void updateTaskCounters()
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
     * Update the state of the task.
     */
    protected TaskState updateTaskCompletionState()
    {
      if (exception == null)
        return TaskState.COMPLETED_SUCCESSFULLY;
      else
        return TaskState.STOPPED_BY_ERROR;
    }
  }

  /**
   * Creates a new ReplicationDomain using configuration from configEntry.
   *
   * @param configuration    The configuration of this ReplicationDomain.
   * @throws ConfigException In case of invalid configuration.
   */
  public ReplicationDomain(MultimasterDomainCfg configuration)
    throws ConfigException
  {
    super("replication flush");

    // Read the configuration parameters.
    replicationServers = configuration.getReplicationServer();
    serverId = (short) configuration.getServerId();
    baseDN = configuration.getReplicationDN();
    maxReceiveQueue = configuration.getMaxReceiveQueue();
    maxReceiveDelay = (int) configuration.getMaxReceiveDelay();
    maxSendQueue = configuration.getMaxSendQueue();
    maxSendDelay = (int) configuration.getMaxSendDelay();
    window  = configuration.getWindowSize();
    heartbeatInterval = configuration.getHeartbeatInterval();
    isolationpolicy = configuration.getIsolationPolicy();

    /*
     * Modify conflicts are solved for all suffixes but the schema suffix
     * because we don't want to store extra information in the schema
     * ldif files.
     * This has no negative impact because the changes on schema should
     * not produce conflicts.
     */
    if (baseDN.compareTo(DirectoryServer.getSchemaDN()) == 0)
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
    state = new PersistentServerState(baseDN);

    /*
     * Create a replication monitor object responsible for publishing
     * monitoring information below cn=monitor.
     */
    monitor = new ReplicationMonitor(this);
    DirectoryServer.registerMonitorProvider(monitor);

    /*
     * create the broker object used to publish and receive changes
     */
    broker = new ReplicationBroker(state, baseDN, serverId, maxReceiveQueue,
        maxReceiveDelay, maxSendQueue, maxSendDelay, window,
        heartbeatInterval);

    broker.start(replicationServers);

    // Retrieves the related backend and its config entry
    try
    {
      retrievesBackendInfos(baseDN);
    } catch (DirectoryException e)
    {
      // The backend associated to this suffix is not able to
      // perform export and import.
      // The replication can continue but this replicationDomain
      // won't be able to use total update.
    }

    /*
     * ChangeNumberGenerator is used to create new unique ChangeNumbers
     * for each operation done on the replication domain.
     */
    ChangeNumberGenerator generator =
      new ChangeNumberGenerator(serverId, state);

    pendingChanges =
      new PendingChanges(new ChangeNumberGenerator(serverId, state),
                         broker, state);

    remotePendingChanges = new RemotePendingChanges(generator, state);

    // listen for changes on the configuration
    configuration.addChangeListener(this);
  }


  /**
   * Returns the base DN of this ReplicationDomain.
   *
   * @return The base DN of this ReplicationDomain
   */
  public DN getBaseDN()
  {
    return baseDN;
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
      return new SynchronizationProviderResult(false);
    }

    DeleteContext ctx =
      (DeleteContext) deleteOperation.getAttachment(SYNCHROCONTEXT);
    Entry deletedEntry = deleteOperation.getEntryToDelete();

    if (ctx != null)
    {
      /*
       * This is a replication operation
       * Check that the modified entry has the same entryuuid
       * has was in the original message.
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
        deleteOperation.setResultCode(ResultCode.NO_SUCH_OBJECT);
        return new SynchronizationProviderResult(false);
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
    return new SynchronizationProviderResult(true);
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
      return new SynchronizationProviderResult(false);
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
        addOperation.setResultCode(ResultCode.CANCELED);
        return new SynchronizationProviderResult(false);
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
          addOperation.setResultCode(ResultCode.NO_SUCH_OBJECT);
          return new SynchronizationProviderResult(false);
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
            addOperation.setResultCode(ResultCode.NO_SUCH_OBJECT);
            return new SynchronizationProviderResult(false);
          }
        }
      }
    }
    return new SynchronizationProviderResult(true);
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
      if (broker.isConnected())
      {
        return true;
      }
      else
      {
        Message msg = ERR_REPLICATION_COULD_NOT_CONNECT.get(baseDN.toString());
        DirectoryException result =
          new DirectoryException(
              ResultCode.UNWILLING_TO_PERFORM, msg);

        op.setResponseData(result);

        return false;
      }
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
      return new SynchronizationProviderResult(false);
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
        modifyDNOperation.setResultCode(ResultCode.NO_SUCH_OBJECT);
        return new SynchronizationProviderResult(false);
      }
      if (modifyDNOperation.getNewSuperior() != null)
      {
        /*
         * Also check that the current id of the
         * parent is the same as when the operation was performed.
         */
        String newParentId = findEntryId(modifyDNOperation.getNewSuperior());
        if ((newParentId != null) &&
            (!newParentId.equals(ctx.getNewParentId())))
        {
          modifyDNOperation.setResultCode(ResultCode.NO_SUCH_OBJECT);
          return new SynchronizationProviderResult(false);
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
    return new SynchronizationProviderResult(true);
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
      return new SynchronizationProviderResult(false);
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
        modifyOperation.setResultCode(ResultCode.NO_SUCH_OBJECT);
        return new SynchronizationProviderResult(false);
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
        modifyOperation.setResultCode(ResultCode.SUCCESS);
        return new SynchronizationProviderResult(false);
      }
    }
    return new SynchronizationProviderResult(true);
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
  }

  /**
   * Receives an update message from the replicationServer.
   * also responsible for updating the list of pending changes
   * @return the received message - null if none
   */
  public UpdateMessage receive()
  {
    UpdateMessage update = remotePendingChanges.getNextUpdate();

    if (update == null)
    {
      synchronized (broker)
      {
        while (update == null)
        {
          ReplicationMessage msg;
          try
          {
            msg = broker.receive();
            if (msg == null)
            {
              // The server is in the shutdown process
              return null;
            }
            log(Message.raw("Broker received message :" + msg));
            if (msg instanceof AckMessage)
            {
              AckMessage ack = (AckMessage) msg;
              receiveAck(ack);
            }
            else if (msg instanceof InitializeRequestMessage)
            {
              // Another server requests us to provide entries
              // for a total update
              InitializeRequestMessage initMsg = (InitializeRequestMessage) msg;
              try
              {
                initializeTarget(initMsg.getsenderID(), initMsg.getsenderID(),
                                 null);
              }
              catch(DirectoryException de)
              {
                // An error message has been sent to the peer
                // Nothing more to do locally
              }
            }
            else if (msg instanceof InitializeTargetMessage)
            {
              // Another server is exporting its entries to us
              InitializeTargetMessage initMsg = (InitializeTargetMessage) msg;

              try
              {
                importBackend(initMsg);
              }
              catch(DirectoryException de)
              {
                // Return an error message to notify the sender
                ErrorMessage errorMsg =
                  new ErrorMessage(initMsg.getsenderID(),
                                   de.getMessageObject());
                MessageBuilder mb = new MessageBuilder();
                mb.append(de.getMessageObject());
                mb.append("Backend ID: ");
                mb.append(backend.getBackendID());
                log(mb.toMessage());
                broker.publish(errorMsg);
              }
            }
            else if (msg instanceof ErrorMessage)
            {
              if (ieContext != null)
              {
                // This is an error termination for the 2 following cases :
                // - either during an export
                // - or before an import really started
                //   For example, when we publish a request and the
                //  replicationServer did not find any import source.
                abandonImportExport((ErrorMessage)msg);
              }
            }
            else if (msg instanceof UpdateMessage)
            {
              update = (UpdateMessage) msg;
              receiveUpdate(update);
            }
          }
          catch (SocketTimeoutException e)
          {
            // just retry
          }
        }
      }
    }
    return update;
  }

  /**
   * Do the necessary processing when an UpdateMessage was received.
   *
   * @param update The received UpdateMessage.
   */
  public void receiveUpdate(UpdateMessage update)
  {
    remotePendingChanges.putRemoteUpdate(update);
    numRcvdUpdates.incrementAndGet();
  }

  /**
   * Do the necessary processing when an AckMessage is received.
   *
   * @param ack The AckMessage that was received.
   */
  public void receiveAck(AckMessage ack)
  {
    UpdateMessage update;
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
    UpdateMessage msg = null;

    // Note that a failed non-replication operation might not have a change
    // number.
    ChangeNumber curChangeNumber = OperationContext.getChangeNumber(op);

    boolean isAssured = isAssured(op);

    if ((result == ResultCode.SUCCESS) && (!op.isSynchronizationOperation()))
    {
      // Generate a replication message for a successful non-replication
      // operation.
      msg = UpdateMessage.generateMsg(op, isAssured);

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
    return numRcvdUpdates.get();
  }

  /**
   * Get the number of updates sent by the replication plugin.
   *
   * @return the number of updates sent
   */
  public int getNumSentUpdates()
  {
    return numSentUpdates.get();
  }

  /**
   * Get the number of updates in the pending list.
   *
   * @return The number of updates in the pending list
   */
  public int getPendingUpdatesCount()
  {
    return pendingChanges.size();
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
    return numProcessedUpdates.get();
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
    broker.publish(new AckMessage(changeNumber));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void run()
  {
    /*
     * create the threads that will wait for incoming changes.
     */
    createListeners();

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
  }

  /**
   * create the threads that will wait for incoming changes.
   * TODO : should use a pool of threads shared between all the servers
   * TODO : need to make number of thread configurable
   */
  private void createListeners()
  {
    synchroThreads.clear();
    for (int i=0; i<listenerThreadNumber; i++)
    {
      ListenerThread myThread = new ListenerThread(this);
      myThread.start();
      synchroThreads.add(myThread);
    }
  }

  /**
   * Shutdown this ReplicationDomain.
   */
  public void shutdown()
  {
    // stop the listener threads
    for (ListenerThread thread : synchroThreads)
    {
      thread.shutdown();
    }

    // stop the flush thread
    shutdown = true;
    synchronized (this)
    {
      this.notify();
    }

    DirectoryServer.deregisterMonitorProvider(monitor.getMonitorInstanceName());

    // stop the ReplicationBroker
    broker.stop();

    //  wait for the listener thread to stop
    for (ListenerThread thread : synchroThreads)
    {
      thread.shutdown();
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
    return broker.getReplicationServer();
  }

  /**
   * Create and replay a synchronized Operation from an UpdateMessage.
   *
   * @param msg The UpdateMessage to be replayed.
   */
  public void replay(UpdateMessage msg)
  {
    Operation op = null;
    boolean done = false;
    boolean dependency = false;
    ChangeNumber changeNumber = null;
    int retryCount = 10;
    boolean firstTry = true;

    try
    {
      while ((!dependency) && (!done) && (retryCount-- > 0))
      {
        op = msg.createOperation(conn);

        op.setInternalOperation(true);
        op.setSynchronizationOperation(true);
        changeNumber = OperationContext.getChangeNumber(op);
        ((AbstractOperation)op).run();

        ResultCode result = op.getResultCode();

        if (result != ResultCode.SUCCESS)
        {
          if (op instanceof ModifyOperation)
          {
            ModifyOperation newOp = (ModifyOperation) op;
            dependency = remotePendingChanges.checkDependencies(newOp);
            if (!dependency)
            {
              done = solveNamingConflict(newOp, msg);
            }
          }
          else if (op instanceof DeleteOperation)
          {
            DeleteOperation newOp = (DeleteOperation) op;
            dependency = remotePendingChanges.checkDependencies(newOp);
            if ((!dependency) && (!firstTry))
            {
              done = solveNamingConflict(newOp, msg);
            }
          }
          else if (op instanceof AddOperation)
          {
            AddOperation newOp = (AddOperation) op;
            AddMsg addMsg = (AddMsg) msg;
            dependency = remotePendingChanges.checkDependencies(newOp);
            if (!dependency)
            {
              done = solveNamingConflict(newOp, addMsg);
            }
          }
          else if (op instanceof ModifyDNOperationBasis)
          {
            ModifyDNMsg newMsg = (ModifyDNMsg) msg;
            dependency = remotePendingChanges.checkDependencies(newMsg);
            if (!dependency)
            {
              ModifyDNOperationBasis newOp = (ModifyDNOperationBasis) op;
              done = solveNamingConflict(newOp, msg);
            }
          }
          else
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
        }
        else
        {
          done = true;
        }
        firstTry = false;
      }

      if (!done && !dependency)
      {
        // Continue with the next change but the servers could now become
        // inconsistent.
        // TODO : REPAIR : Should let the repair tool know about this
        Message message = ERR_LOOP_REPLAYING_OPERATION.get(op.toString());
        logError(message);
        numUnresolvedNamingConflicts.incrementAndGet();

        updateError(changeNumber);
      }
    }
    catch (ASN1Exception e)
    {
      Message message = ERR_EXCEPTION_DECODING_OPERATION.get(
              String.valueOf(msg) + stackTraceToSingleLineString(e));
      logError(message);
    }
    catch (LDAPException e)
    {
      Message message = ERR_EXCEPTION_DECODING_OPERATION.get(
              String.valueOf(msg) + stackTraceToSingleLineString(e));
      logError(message);
    }
    catch (DataFormatException e)
    {
      Message message = ERR_EXCEPTION_DECODING_OPERATION.get(
              String.valueOf(msg) + stackTraceToSingleLineString(e));
      logError(message);
    }
    catch (Exception e)
    {
      if (changeNumber != null)
      {
        /*
         * An Exception happened during the replay process.
         * Continue with the next change but the servers will now start
         * to be inconsistent.
         * TODO : REPAIR : Should let the repair tool know about this
         */
        Message message = ERR_EXCEPTION_REPLAYING_OPERATION.get(
            stackTraceToSingleLineString(e), op.toString());
        logError(message);
        updateError(changeNumber);
      }
      else
      {
        Message message = ERR_EXCEPTION_DECODING_OPERATION.get(
                String.valueOf(msg) + stackTraceToSingleLineString(e));
        logError(message);
      }
    }
    finally
    {
      if (!dependency)
      {
        if (msg.isAssured())
          ack(msg.getChangeNumber());
        incProcessedUpdates();
      }
    }
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
   * @return The unique Id of the entry whith the provided DN.
   */
  private String findEntryId(DN dn)
  {
    if (dn == null)
      return null;
    try
    {
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
   * find the current dn of an entry from its entry uuid.
   *
   * @param uuid the Entry Unique ID.
   * @return The curernt dn of the entry or null if there is no entry with
   *         the specified uuid.
   */
  private DN findEntryDN(String uuid)
  {
    try
    {
      InternalSearchOperation search = conn.processSearch(baseDN,
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
      UpdateMessage msg)
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
    else
    {
      // The other type of errors can not be caused by naming conflicts.
      // TODO log a message for the repair tool.
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
     UpdateMessage msg)
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
     // TODO log a message for the repair tool.
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
    UpdateMessage msg) throws Exception
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

  // Construct the new DN to use for the entry.
  DN entryDN = op.getEntryDN();
  DN newSuperior = findEntryDN(newSuperiorID);
  RDN newRDN = op.getNewRDN();
  DN parentDN;

  if (newSuperior == null)
  {
    parentDN = entryDN.getParent();
  }
  else
  {
    parentDN = newSuperior;
  }

  if ((parentDN == null) || parentDN.isNullDN())
  {
    /* this should never happen
     * can't solve any conflict in this case.
     */
    throw new Exception("operation parameters are invalid");
  }

  DN newDN = parentDN.concat(newRDN);

  // get the current DN of this entry in the database.
  DN currentDN = findEntryDN(entryUid);

  if (currentDN == null)
  {
    // The entry targetted by the Modify DN is not in the database
    // anymore.
    // This is a conflict between a delete and this modify DN.
    // The entry has been deleted anymore so we can safely assume
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
    // TODO log a message for the repair tool.
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
                    + baseDN);
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
      // TODO log a message for the repair tool.
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
        MessageBuilder mb = new MessageBuilder();
        mb.append(ERR_CANNOT_RENAME_CONFLICT_ENTRY.get());
        mb.append(String.valueOf(entryDN));
        mb.append(" ");
        mb.append(String.valueOf(conflictOp));
        mb.append(" ");
        mb.append(String.valueOf(op.getResultCode()));
        logError(mb.toMessage());
        // TODO : log error and information for the REPAIR tool.
      }
    } catch (DirectoryException e)
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_EXCEPTION_RENAME_CONFLICT_ENTRY.get());
      mb.append(String.valueOf(entryDN));
      mb.append(" ");
      mb.append(String.valueOf(conflictOp));
      mb.append(" ");
      mb.append(e.getLocalizedMessage());
      logError(mb.toMessage());
      // TODO log errror and information for the REPAIR tool.
    }
  }


  /**
   * Rename an entry that was conflicting so that it stays below the
   * baseDN of the replicationDomain.
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
        dn, generateDeleteConflictDn(uid, dn),false, baseDN);

    if (newOp.getResultCode() != ResultCode.SUCCESS)
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_CANNOT_RENAME_CONFLICT_ENTRY.get());
      mb.append(String.valueOf(dn));
      mb.append(" ");
      mb.append(String.valueOf(conflictOp));
      mb.append(" ");
      mb.append(String.valueOf(newOp.getResultCode()));
      logError(mb.toMessage());
      /*
       * TODO : REPAIR should log information for the repair tool.
       */
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

    AttributeType attrType =
      DirectoryServer.getAttributeType(DS_SYNC_CONFLICT, true);
    LinkedHashSet<AttributeValue> values = new LinkedHashSet<AttributeValue>();
    values.add(new AttributeValue(attrType, conflictDN.toString()));
    Attribute attr = new Attribute(attrType, DS_SYNC_CONFLICT, values);
    List<Modification> mods = new ArrayList<Modification>();
    Modification mod = new Modification(ModificationType.REPLACE, attr);
    mods.add(mod);
    ModifyOperation newOp = conn.processModify(currentDN, mods);
    if (newOp.getResultCode() != ResultCode.SUCCESS)
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(ERR_CANNOT_ADD_CONFLICT_ATTRIBUTE.get());
      mb.append(String.valueOf(op));
      mb.append(" ");
      mb.append(String.valueOf(newOp.getResultCode()));
      logError(mb.toMessage());
      /*
       * TODO : REPAIR should log information for the repair tool.
       */
    }
  }

  /**
   * Add the conflict object class to an entry that could
   * not be added because it is conflicting with another entry.
   *
   * @param msg            The conflicting Add Operation.
   *
   * @throws ASN1Exception When an encoding error happenned manipulating the
   *                       msg.
   */
  private void addConflict(AddMsg msg) throws ASN1Exception
  {
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
    return broker.getMaxRcvWindow();
  }

  /**
   * Get the current receive window size.
   *
   * @return The current receive window size.
   */
  public int getCurrentRcvWindow()
  {
    return broker.getCurrentRcvWindow();
  }

  /**
   * Get the maximum send window size.
   *
   * @return The maximum send window size.
   */
  public int getMaxSendWindow()
  {
    return broker.getMaxSendWindow();
  }

  /**
   * Get the current send window size.
   *
   * @return The current send window size.
   */
  public int getCurrentSendWindow()
  {
    return broker.getCurrentSendWindow();
  }

  /**
   * Get the number of times the replication connection was lost.
   * @return The number of times the replication connection was lost.
   */
  public int getNumLostConnections()
  {
    return broker.getNumLostConnections();
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
   * The listener threads will be destroyed.
   * The monitor informations will still be accessible.
   */
  public void disable()
  {
    state.save();
    state.clear();
    disabled = true;
    //  stop the listener threads
    for (ListenerThread thread : synchroThreads)
    {
      thread.shutdown();
    }
    broker.stop(); // this will cut the session and wake-up the listeners

    for (ListenerThread thread : synchroThreads)
    {
      try
      {
        thread.join(SHUTDOWN_JOIN_TIMEOUT);
      } catch (InterruptedException e)
      {
        // ignore
      }
    }
  }

  /**
   * Enable back the domain after a previous disable.
   * The domain will connect back to a replication Server and
   * will recreate threads to listen for messages from the Sycnhronization
   * server.
   * The ServerState will also be read again from the local database.
   */
  public void enable()
  {
    state.clear();
    state.loadState();
    disabled = false;

    broker.start(replicationServers);

    createListeners();
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
    ReplicationMessage msg;
    while (true)
    {
      try
      {
        msg = broker.receive();

        if (msg == null)
        {
          // The server is in the shutdown process
          return null;
        }
        log(Message.raw("receiveEntryBytes: received " + msg));
        if (msg instanceof EntryMessage)
        {
          // FIXME
          EntryMessage entryMsg = (EntryMessage)msg;
          byte[] entryBytes = entryMsg.getEntryBytes().clone();
          ieContext.updateTaskCounters();
          return entryBytes;
        }
        else if (msg instanceof DoneMessage)
        {
          // This is the normal termination of the import
          // No error is stored and the import is ended
          // by returning null
          return null;
        }
        else if (msg instanceof ErrorMessage)
        {
          // This is an error termination during the import
          // The error is stored and the import is ended
          // by returning null
          ErrorMessage errorMsg = (ErrorMessage)msg;
          ieContext.exception = new DirectoryException(ResultCode.OTHER,
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
        ieContext.exception = new DirectoryException(ResultCode.OTHER,
            Message.raw("received an unexpected message type"), e);
      }
      return null;
    }
  }

  /**
   * Processes an error message received while an import/export is
   * on going.
   * @param errorMsg The error message received.
   */
  protected void abandonImportExport(ErrorMessage errorMsg)
  {
    // FIXME TBD Treat the case where the error happens while entries
    // are being exported

    if (ieContext != null)
    {
      ieContext.exception = new DirectoryException(ResultCode.OTHER,
          errorMsg.getDetails());

      if (ieContext.initializeTask instanceof InitializeTask)
      {
        // Update the task that initiated the import
        ((InitializeTask)ieContext.initializeTask).
        setState(ieContext.updateTaskCompletionState(),ieContext.exception);

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
   * Log debug message.
   * @param message The message to log.
   */
  private void log(Message message)
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("DebugInfo" + message);
    }
  }

  /**
   * Export the entries.
   * @throws DirectoryException when an error occurred
   */
  protected void exportBackend() throws DirectoryException
  {
    // FIXME Temporary workaround - will probably be fixed when implementing
    // dynamic config
    retrievesBackendInfos(this.baseDN);

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

    ReplLDIFOutputStream os = new ReplLDIFOutputStream(this);

    LDIFExportConfig exportConfig = new LDIFExportConfig(os);
    List<DN> includeBranches = new ArrayList<DN>(1);
    includeBranches.add(this.baseDN);
    exportConfig.setIncludeBranches(includeBranches);

    //  Launch the export.
    try
    {
      backend.exportLDIF(exportConfig);
    }
    catch (DirectoryException de)
    {
      Message message =
          ERR_LDIFEXPORT_ERROR_DURING_EXPORT.get(de.getMessageObject());
      logError(message);
      throw new DirectoryException(
          ResultCode.OTHER, message, null);
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
      //  Clean up after the export by closing the export config.
      exportConfig.close();

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
  }

  /**
   * Retrieves the backend object related to the domain and the backend's
   * config entry. They will be used for import and export.
   * TODO This should be in a shared package rather than here.
   *
   * @param baseDN The baseDN to retrieve the backend
   * @throws DirectoryException when an error occired
   */
  protected void retrievesBackendInfos(DN baseDN) throws DirectoryException
  {
    // Retrieves the backend related to this domain
    Backend domainBackend = DirectoryServer.getBackend(baseDN);
    if (domainBackend == null)
    {
      Message message = ERR_CANNOT_DECODE_BASE_DN.get(DN_BACKEND_BASE, "");
      throw new DirectoryException(
          ResultCode.OTHER, message, null);
    }

    // Retrieves its configuration
    BackendCfg backendCfg = TaskUtils.getConfigEntry(domainBackend);
    if (backendCfg == null)
    {
      Message message =
          ERR_LDIFIMPORT_NO_BACKENDS_FOR_ID.get();
      logError(message);
      throw new DirectoryException(
          ResultCode.OTHER, message, null);
    }

    this.backend = domainBackend;
    if (! domainBackend.supportsLDIFImport())
    {
      Message message = ERR_LDIFIMPORT_CANNOT_IMPORT.get(
              String.valueOf(baseDN));
      logError(message);
      throw new DirectoryException(
          ResultCode.OTHER, message, null);
    }
  }


  /**
   * Sends lDIFEntry entry lines to the export target currently set.
   *
   * @param lDIFEntry The lines for the LDIF entry.
   * @throws IOException when an error occurred.
   */
  public void sendEntryLines(String lDIFEntry) throws IOException
  {
    // If an error was raised - like receiving an ErrorMessage
    // we just let down the export.
    if (ieContext.exception != null)
    {
      IOException ioe = new IOException(ieContext.exception.getMessage());
      ieContext = null;
      throw ioe;
    }

    // new entry then send the current one
    EntryMessage entryMessage = new EntryMessage(
        serverId, ieContext.exportTarget, lDIFEntry.getBytes());
    broker.publish(entryMessage);

    ieContext.updateTaskCounters();
  }

  /**
   * Initializes this domain from another source server.
   *
   * @param source The source from which to initialize
   * @param initTask The task that launched the initialization
   *                 and should be updated of its progress.
   * @throws DirectoryException when an error occurs
   */
  public void initialize(short source, Task initTask)
  throws DirectoryException
  {
    acquireIEContext();
    ieContext.initializeTask = initTask;

    InitializeRequestMessage initializeMsg = new InitializeRequestMessage(
        baseDN, serverId, source);

    // Publish Init request msg
    broker.publish(initializeMsg);

    // .. we expect to receive entries or err after that
  }

  /**
   * Verifies that the given string represents a valid source
   * from which this server can be initialized.
   * @param sourceString The string representaing the source
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

        log(Message.raw("Source decoded for import:" + source));
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
      throw new DirectoryException(
          resultCode, message, cause);
    else
      throw new DirectoryException(
          resultCode, message);
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
      return RoutableMessage.ALL_SERVERS;
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
   * @exception DirectoryException When an error occurs.
   */
  public void initializeTarget(short target, Task initTask)
  throws DirectoryException
  {
    initializeTarget(target, serverId, initTask);
  }

  /**
   * Process the initialization of some other server or servers in the topology
   * specified by the target argument when this initialization has been
   * initiated by another server than this one.
   * @param target The target that should be initialized.
   * @param requestorID The server that initiated the export.
   * @param initTask The task that triggers this initialization and that should
   *  be updated with its progress.
   * @exception DirectoryException When an error occurs.
   */
  public void initializeTarget(short target, short requestorID, Task initTask)
  throws DirectoryException
  {
    // FIXME Temporary workaround - will probably be fixed when implementing
    // dynamic config
    retrievesBackendInfos(this.baseDN);

    acquireIEContext();

    ieContext.exportTarget = target;
    if (initTask != null)
    {
      ieContext.initializeTask = initTask;
      ieContext.initTaskCounters(backend.getEntryCount());
    }

    // Send start message to the peer
    InitializeTargetMessage initializeMessage = new InitializeTargetMessage(
        baseDN, serverId, ieContext.exportTarget, requestorID,
        backend.getEntryCount());

    log(Message.raw("SD : publishes " + initializeMessage +
        " for #entries=" + backend.getEntryCount() + ieContext.entryLeftCount));

    broker.publish(initializeMessage);

    try
    {
      exportBackend();

      // Notify the peer of the success
      DoneMessage doneMsg = new DoneMessage(serverId,
        initializeMessage.getDestination());
      broker.publish(doneMsg);

      releaseIEContext();
    }
    catch(DirectoryException de)
    {
      // Notify the peer of the failure
      ErrorMessage errorMsg = new ErrorMessage(target, de.getMessageObject());
      broker.publish(errorMsg);

      releaseIEContext();

      throw(de);
    }
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
      Message message = ERR_LDIFIMPORT_CANNOT_LOCK_BACKEND.get(
          backend.getBackendID(), String.valueOf(failureReason));
      logError(message);
      throw new DirectoryException(ResultCode.OTHER, message);
    }
  }

  /**
   * Initializes the domain's backend with received entries.
   * @param initializeMessage The message that initiated the import.
   * @exception DirectoryException Thrown when an error occurs.
   */
  protected void importBackend(InitializeTargetMessage initializeMessage)
  throws DirectoryException
  {
    LDIFImportConfig importConfig = null;
    try
    {
      log(Message.raw("startImport"));

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
      ieContext.initTaskCounters(initializeMessage.getEntryCount());

      preBackendImport(this.backend);

      ieContext.ldifImportInputStream = new ReplLDIFInputStream(this);
      importConfig =
        new LDIFImportConfig(ieContext.ldifImportInputStream);
      List<DN> includeBranches = new ArrayList<DN>();
      includeBranches.add(this.baseDN);
      importConfig.setIncludeBranches(includeBranches);
      importConfig.setAppendToExistingData(false);

      // TODO How to deal with rejected entries during the import
      // importConfig.writeRejectedEntries("rejectedImport",
      // ExistingFileBehavior.OVERWRITE);

      // Process import
      this.backend.importLDIF(importConfig);

      stateSavingDisabled = false;

      // Re-exchange state with SS
      broker.stop();
      broker.start(replicationServers);

    }
    catch(Exception e)
    {
      throw new DirectoryException(ResultCode.OTHER,
              Message.raw(e.getLocalizedMessage()));
    }
    finally
    {
      // Cleanup
      importConfig.close();

      // Re-enable backend
      closeBackendImport(this.backend);

      // Update the task that initiated the import
      if ((ieContext != null ) && (ieContext.initializeTask != null))
      {
        ((InitializeTask)ieContext.initializeTask).
        setState(ieContext.updateTaskCompletionState(),ieContext.exception);
      }

      releaseIEContext();

      log(Message.raw("End importBackend"));
    }
    // Success
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

    // FIXME setBackendEnabled should be part taskUtils ?
    TaskUtils.enableBackend(backend.getBackendID());
  }

  /**
   * Retrieves a replication domain based on the baseDN.
   *
   * @param baseDN The baseDN of the domain to retrieve
   * @return The domain retrieved
   * @throws DirectoryException When an error occurred or no domain
   * match the provided baseDN.
   */
  public static ReplicationDomain retrievesReplicationDomain(DN baseDN)
  throws DirectoryException
  {
    ReplicationDomain replicationDomain = null;

    // Retrieves the domain
    DirectoryServer.getSynchronizationProviders();
    for (SynchronizationProvider provider :
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
        MultimasterReplication.findDomain(baseDN, null);
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
      mb.append(String.valueOf(baseDN));
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
    return backend;
  }

  /**
   * Returns a boolean indiciating if an import or export is currently
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
   * Push the modifications contain the in given parameter has
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
   *                            configuration is not acceptbale.
   *
   * @return true if the configuration is acceptable, false other wise.
   */
  public static boolean isConfigurationAcceptable(
      MultimasterDomainCfg configuration, List<Message> unacceptableReasons)
  {
    // Check that there is not already a domain with the same DN
    DN dn = configuration.getReplicationDN();
    if (MultimasterReplication.findDomain(dn,null) != null)
    {
      Message message = ERR_SYNC_INVALID_DN.get();
      unacceptableReasons.add(message);
      return false;
    }
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
         MultimasterDomainCfg configuration)
  {
    // server id and base dn are readonly.
    // isolationPolicy can be set immediately and will apply
    // to the next updates.
    // The other parameters needs to be renegociated with the ReplicationServer.
    // so that requires restarting the session with the ReplicationServer.
    replicationServers = configuration.getReplicationServer();
    maxReceiveQueue = configuration.getMaxReceiveQueue();
    maxReceiveDelay = (int) configuration.getMaxReceiveDelay();
    maxSendQueue = configuration.getMaxSendQueue();
    maxSendDelay = (int) configuration.getMaxSendDelay();
    window = configuration.getWindowSize();
    heartbeatInterval = configuration.getHeartbeatInterval();
    broker.changeConfig(replicationServers, maxReceiveQueue, maxReceiveDelay,
                        maxSendQueue, maxSendDelay, window, heartbeatInterval);
    isolationpolicy = configuration.getIsolationPolicy();

    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
         MultimasterDomainCfg configuration, List<Message> unacceptableReasons)
  {
    return true;
  }
}
