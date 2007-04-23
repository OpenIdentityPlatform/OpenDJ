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
package org.opends.server.synchronization.plugin;

import static org.opends.server.config.ConfigConstants.ATTR_BACKEND_BASE_DN;
import static org.opends.server.config.ConfigConstants.ATTR_BACKEND_CLASS;
import static org.opends.server.config.ConfigConstants.ATTR_BACKEND_ID;
import static org.opends.server.config.ConfigConstants.DN_BACKEND_BASE;
import static org.opends.server.loggers.Error.logError;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.debugInfo;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.synchronization.common.LogMessages.*;
import static org.opends.server.synchronization.plugin.Historical.ENTRYUIDNAME;
import static org.opends.server.synchronization.protocol.OperationContext.*;
import static org.opends.server.util.StaticUtils.createEntry;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DataFormatException;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.MultimasterDomainCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.DirectoryThread;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.backends.jeb.BackendImpl;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.LockFileManager;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.Operation;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.synchronization.common.ChangeNumber;
import org.opends.server.synchronization.common.ChangeNumberGenerator;
import org.opends.server.synchronization.common.LogMessages;
import org.opends.server.synchronization.common.ServerState;
import org.opends.server.synchronization.protocol.AckMessage;
import org.opends.server.synchronization.protocol.AddContext;
import org.opends.server.synchronization.protocol.DeleteContext;
import org.opends.server.synchronization.protocol.DoneMessage;
import org.opends.server.synchronization.protocol.EntryMessage;
import org.opends.server.synchronization.protocol.ErrorMessage;
import org.opends.server.synchronization.protocol.InitializeRequestMessage;
import org.opends.server.synchronization.protocol.InitializeTargetMessage;
import org.opends.server.synchronization.protocol.ModifyContext;
import org.opends.server.synchronization.protocol.ModifyDNMsg;
import org.opends.server.synchronization.protocol.ModifyDnContext;
import org.opends.server.synchronization.protocol.OperationContext;
import org.opends.server.synchronization.protocol.RoutableMessage;
import org.opends.server.synchronization.protocol.SynchronizationMessage;
import org.opends.server.synchronization.protocol.UpdateMessage;
import org.opends.server.tasks.InitializeTargetTask;
import org.opends.server.tasks.InitializeTask;
import org.opends.server.tasks.TaskUtils;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.RDN;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.types.SearchScope;
import org.opends.server.types.SynchronizationProviderResult;

/**
 *  This class implements the bulk part of the.of the Directory Server side
 *  of the synchronization code.
 *  It contains the root method for publishing a change,
 *  processing a change received from the changelog service,
 *  handle conflict resolution,
 *  handle protocol messages from the changelog server.
 */
public class SynchronizationDomain extends DirectoryThread
       implements ConfigurationChangeListener<MultimasterDomainCfg>
{
  private SynchronizationMonitor monitor;

  private ChangeNumberGenerator changeNumberGenerator;
  private ChangelogBroker broker;

  private List<ListenerThread> synchroThreads =
    new ArrayList<ListenerThread>();
  private final SortedMap<ChangeNumber, PendingChange> pendingChanges =
    new TreeMap<ChangeNumber, PendingChange>();
  private SortedMap<ChangeNumber, UpdateMessage> waitingAckMsgs =
    new TreeMap<ChangeNumber, UpdateMessage>();
  private int numRcvdUpdates = 0;
  private int numSentUpdates = 0;
  private AtomicInteger numProcessedUpdates = new AtomicInteger();
  private int debugCount = 0;
  private PersistentServerState state;
  private int numReplayedPostOpCalled = 0;

  private int maxReceiveQueue = 0;
  private int maxSendQueue = 0;
  private int maxReceiveDelay = 0;
  private int maxSendDelay = 0;

  /**
   * The time in milliseconds between heartbeats from the synchronization
   * server.  Zero means heartbeats are off.
   */
  private long heartbeatInterval = 0;
  short serverId;

  /**
   * This class contain the context related to an import or export
   * launched on the domain.
   */
  private class IEContext
  {
    // The task that initiated the operation.
    Task initializeTask;
    // The input stream for the import
    SynchroLDIFInputStream ldifImportInputStream = null;
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

  // The context related to an import or export being processed
  // Null when none is being processed.
  private IEContext ieContext = null;

  // The backend informations necessary to make an import or export.
  private Backend backend;
  private ConfigEntry backendConfigEntry;
  private List<DN> branches = new ArrayList<DN>(0);

  private int listenerThreadNumber = 10;
  private boolean receiveStatus = true;

  private Collection<String> changelogServers;

  private DN baseDN;

  private List<ConfigAttribute> configAttributes =
                                          new ArrayList<ConfigAttribute>();

  private boolean shutdown = false;

  private InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

  private boolean solveConflictFlag = true;

  private boolean disabled = false;
  private boolean stateSavingDisabled = false;

  private int window = 100;


  /**
   * Creates a new SynchronizationDomain using configuration from configEntry.
   *
   * @param configuration    The configuration of this SynchronizationDomain.
   * @throws ConfigException In case of invalid configuration.
   */
  public SynchronizationDomain(MultimasterDomainCfg configuration)
    throws ConfigException
  {
    super("Synchronization flush");

    // Read the configuration parameters.
    changelogServers = configuration.getChangelogServer();
    serverId = (short) configuration.getServerId();
    baseDN = configuration.getSynchronizationDN();
    maxReceiveQueue = configuration.getMaxReceiveQueue();
    maxReceiveDelay = (int) configuration.getMaxReceiveDelay();
    maxSendQueue = configuration.getMaxSendQueue();
    maxSendDelay = (int) configuration.getMaxSendDelay();
    window  = configuration.getWindowSize();
    heartbeatInterval = configuration.getHeartbeatInterval();

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
     * Create a Synchronization monitor object responsible for publishing
     * monitoring information below cn=monitor.
     */
    monitor = new SynchronizationMonitor(this);
    DirectoryServer.registerMonitorProvider(monitor);

    /*
     * ChangeNumberGenerator is used to create new unique ChangeNumbers
     * for each operation done on the synchronization domain.
     */
    changeNumberGenerator = new ChangeNumberGenerator(serverId, state);

    /*
     * create the broker object used to publish and receive changes
     */
    try
    {
      broker = new ChangelogBroker(state, baseDN, serverId, maxReceiveQueue,
          maxReceiveDelay, maxSendQueue, maxSendDelay, window,
          heartbeatInterval);
      synchronized (broker)
      {
        broker.start(changelogServers);
        if (!receiveStatus)
          broker.suspendReceive();
      }

      // Retrieves the related backend and its config entry
      retrievesBackendInfos(baseDN);

    } catch (Exception e)
    {
     /* TODO should mark that changelog service is
      * not available, log an error and retry upon timeout
      * should we stop the modifications ?
      */
    }

    // listen for changes on the configuration
    configuration.addChangeListener(this);
  }


  /**
   * Returns the base DN of this SynchronizationDomain.
   *
   * @return The base DN of this SynchronizationDomain
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
      DeleteOperation deleteOperation)
  {
    DeleteContext ctx =
      (DeleteContext) deleteOperation.getAttachment(SYNCHROCONTEXT);
    Entry deletedEntry = deleteOperation.getEntryToDelete();

    if (ctx != null)
    {
      /*
       * This is a synchronization operation
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
      // There is no Synchronization context attached to the operation
      // so this is not a synchronization operation.
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
      AddOperation addOperation)
  {
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

      // There is a potential of perfs improvement here
      // if we could avoid the following parent entry retrieval
      DN parentDnFromCtx = findEntryDN(ctx.getParentUid());

      if (parentDnFromCtx != null)
      {
        DN entryDN = addOperation.getEntryDN();
        DN parentDnFromEntryDn = entryDN.getParentDNInSuffix();
        if ((parentDnFromEntryDn != null)
            && (!parentDnFromCtx.equals(parentDnFromEntryDn)))
        {
          // parentEntry has been renamed
          // Synchronization name conflict resolution is expected to fix that
          // later in the flow
          addOperation.setResultCode(ResultCode.NO_SUCH_OBJECT);
          return new SynchronizationProviderResult(false);
        }
      }
    }
    return new SynchronizationProviderResult(true);
  }

  /**
   * Implement the  handleConflictResolution phase of the ModifyDNOperation.
   *
   * @param modifyDNOperation The ModifyDNOperation.
   * @return A SynchronizationProviderResult indicating if the operation
   *         can continue.
   */
  public SynchronizationProviderResult handleConflictResolution(
      ModifyDNOperation modifyDNOperation)
  {
    ModifyDnContext ctx =
      (ModifyDnContext) modifyDNOperation.getAttachment(SYNCHROCONTEXT);
    if (ctx != null)
    {
      /*
       * This is a synchronization operation
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
      // There is no Synchronization context attached to the operation
      // so this is not a synchronization operation.
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
                                                ModifyOperation modifyOperation)
  {
    ModifyContext ctx =
      (ModifyContext) modifyOperation.getAttachment(SYNCHROCONTEXT);

    Entry modifiedEntry = modifyOperation.getModifiedEntry();
    if (ctx == null)
    {
      // There is no Synchronization context attached to the operation
      // so this is not a synchronization operation.
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
      modifyOperation.setAttachment(HISTORICAL, historicalInformation);

      historicalInformation.replayOperation(modifyOperation, modifiedEntry);

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
   * Its job is to generate the Synchronization context associated to the
   * operation. It is necessary to do it in this phase because contrary to
   * the other operations, the entry uid is not set when the handleConflict
   * phase is called.
   *
   * @param addOperation The Add Operation.
   */
  public void doPreOperation(AddOperation addOperation)
  {
    AddContext ctx = new AddContext(generateChangeNumber(addOperation),
        Historical.getEntryUuid(addOperation),
        findEntryId(addOperation.getEntryDN().getParentDNInSuffix()));

    addOperation.setAttachment(SYNCHROCONTEXT, ctx);
  }

  /**
   * Receives an update message from the changelog.
   * also responsible for updating the list of pending changes
   * @return the received message - null if none
   */
  public UpdateMessage receive()
  {
    synchronized (broker)
    {
      UpdateMessage update = null;
      while (update == null)
      {
        SynchronizationMessage msg;
        try
        {
          msg = broker.receive();
          if (msg == null)
          {
            // The server is in the shutdown process
            return null;
          }
          log("Broker received message :" + msg);
          if (msg instanceof AckMessage)
          {
            AckMessage ack = (AckMessage) msg;
            receiveAck(ack);
          }
          else if (msg instanceof UpdateMessage)
          {
            update = (UpdateMessage) msg;
            receiveUpdate(update);
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
              // Returns an error message to notify the sender
              int msgID = de.getMessageID();
              ErrorMessage errorMsg = new ErrorMessage(initMsg.getsenderID(),
                  msgID, de.getMessage());
              broker.publish(errorMsg);
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
              int msgID = de.getMessageID();
              ErrorMessage errorMsg = new ErrorMessage(initMsg.getsenderID(),
                  msgID, de.getMessage());
              log(getMessage(msgID, backend.getBackendID()) + de.getMessage());
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
              //  changelog did not find any import source.
              abandonImportExport((ErrorMessage)msg);
            }
          }
        } catch (SocketTimeoutException e)
        {
          // just retry
        }

      }
      return update;
    }
  }

  /**
   * Do the necessary processing when an UpdateMessage was received.
   *
   * @param update The received UpdateMessage.
   */
  public void receiveUpdate(UpdateMessage update)
  {
    ChangeNumber changeNumber = update.getChangeNumber();

    synchronized (pendingChanges)
    {
      if (pendingChanges.containsKey(changeNumber))
      {
        /*
         * This should never happen,
         * TODO log error and throw exception
         */
      }
      pendingChanges.put(changeNumber,
          new PendingChange(changeNumber, null, update));
      numRcvdUpdates++;
    }
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

    synchronized (pendingChanges)
    {
      update = waitingAckMsgs.get(changeNumber);
      waitingAckMsgs.remove(changeNumber);
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
  public void synchronize(Operation op)
  {
    ResultCode result = op.getResultCode();
    if ((result == ResultCode.SUCCESS) && op.isSynchronizationOperation())
    {
      numReplayedPostOpCalled++;
    }
    UpdateMessage msg = null;

    // Note that a failed non-synchronization operation might not have a change
    // number.
    ChangeNumber curChangeNumber = OperationContext.getChangeNumber(op);

    boolean isAssured = isAssured(op);

    if ((result == ResultCode.SUCCESS) && (!op.isSynchronizationOperation()))
    {
      // Generate a synchronization message for a successful non-synchronization
      // operation.
      msg = UpdateMessage.generateMsg(op, isAssured);

      if (msg == null)
      {
        /*
         * This is an operation type that we do not know about
         * It should never happen.
         */
        synchronized (pendingChanges)
        {
          pendingChanges.remove(curChangeNumber);
          int    msgID   = MSGID_UNKNOWN_TYPE;
          String message = getMessage(msgID, op.getOperationType().toString());
          logError(ErrorLogCategory.SYNCHRONIZATION,
                   ErrorLogSeverity.SEVERE_ERROR,
                   message, msgID);
          return;
        }
      }
    }

    synchronized(pendingChanges)
    {
      if (result == ResultCode.SUCCESS)
      {
        PendingChange curChange = pendingChanges.get(curChangeNumber);
        if (curChange == null)
        {
          // This should never happen
          int msgID = MSGID_OPERATION_NOT_FOUND_IN_PENDING;
          String message = getMessage(msgID, curChangeNumber.toString(),
              op.toString());
          logError(ErrorLogCategory.SYNCHRONIZATION,
              ErrorLogSeverity.SEVERE_ERROR,
              message, msgID);
          return;
        }
        curChange.setCommitted(true);

        if (op.isSynchronizationOperation())
          curChange.setOp(op);
        else
          curChange.setMsg(msg);

        if (msg != null && isAssured)
        {
          // Add the assured message to the list of those whose acknowledgements
          // we are awaiting.
          waitingAckMsgs.put(curChangeNumber, msg);
        }
      }
      else if (!op.isSynchronizationOperation())
      {
        // Remove an unsuccessful non-synchronization operation from the pending
        // changes list.
        if (curChangeNumber != null)
        {
          pendingChanges.remove(curChangeNumber);
        }
      }

      pushCommittedChanges();
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
   * get the number of updates received by the synchronization plugin.
   *
   * @return the number of updates received
   */
  public int getNumRcvdUpdates()
  {
    return numRcvdUpdates;
  }

  /**
   * Get the number of updates sent by the synchronization plugin.
   *
   * @return the number of updates sent
   */
  public int getNumSentUpdates()
  {
    return numSentUpdates;
  }

  /**
   * get the number of updates in the pending list.
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
   * get the number of updates replayed by the synchronization.
   *
   * @return The number of updates replayed by the synchronization
   */
  public int getNumProcessedUpdates()
  {
    return numProcessedUpdates.get();
  }

  /**
   * get the number of updates replayed successfully by the synchronization.
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
   * Shutdown this SynchronizationDomain.
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

    // stop the ChangelogBroker
    broker.stop();
  }

  /**
   * Get the name of the changelog server to which this domain is currently
   * connected.
   *
   * @return the name of the changelog server to which this domain
   *         is currently connected.
   */
  public String getChangelogServer()
  {
    return broker.getChangelogServer();
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
    ChangeNumber changeNumber = null;
    int retryCount = 10;

    try
    {
      while (!done && retryCount-- > 0)
      {
        op = msg.createOperation(conn);

        op.setInternalOperation(true);
        op.setSynchronizationOperation(true);
        changeNumber = OperationContext.getChangeNumber(op);
        if (changeNumber != null)
          changeNumberGenerator.adjust(changeNumber);

        op.run();

        ResultCode result = op.getResultCode();
        if (result != ResultCode.SUCCESS)
        {
          if (op instanceof ModifyOperation)
          {
            ModifyOperation newOp = (ModifyOperation) op;
            done = solveNamingConflict(newOp, msg);
          }
          else if (op instanceof DeleteOperation)
          {
            DeleteOperation newOp = (DeleteOperation) op;
            done = solveNamingConflict(newOp, msg);
          }
          else if (op instanceof AddOperation)
          {
            AddOperation newOp = (AddOperation) op;
            done = solveNamingConflict(newOp, msg);

          } else if (op instanceof ModifyDNOperation)
          {
            ModifyDNOperation newOp = (ModifyDNOperation) op;
            done = solveNamingConflict(newOp, msg);
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
      }

      if (!done)
      {
        // Continue with the next change but the servers could now become
        // inconsistent.
        // TODO : REPAIR : Should let the repair tool know about this
        int msgID = MSGID_LOOP_REPLAYING_OPERATION;
        String message = getMessage(msgID, op.toString());
        logError(ErrorLogCategory.SYNCHRONIZATION,
            ErrorLogSeverity.SEVERE_ERROR, message, msgID);
        updateError(changeNumber);
      }
    }
    catch (ASN1Exception e)
    {
      int msgID = MSGID_EXCEPTION_DECODING_OPERATION;
      String message = getMessage(msgID, msg) +
      stackTraceToSingleLineString(e);
      logError(ErrorLogCategory.SYNCHRONIZATION,
          ErrorLogSeverity.SEVERE_ERROR,
          message, msgID);
    }
    catch (LDAPException e)
    {
      int msgID = MSGID_EXCEPTION_DECODING_OPERATION;
      String message = getMessage(msgID, msg) +
      stackTraceToSingleLineString(e);
      logError(ErrorLogCategory.SYNCHRONIZATION,
          ErrorLogSeverity.SEVERE_ERROR,
          message, msgID);
    }
    catch (DataFormatException e)
    {
      int msgID = MSGID_EXCEPTION_DECODING_OPERATION;
      String message = getMessage(msgID, msg) +
      stackTraceToSingleLineString(e);
      logError(ErrorLogCategory.SYNCHRONIZATION,
          ErrorLogSeverity.SEVERE_ERROR,
          message, msgID);
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
        int msgID = MSGID_EXCEPTION_REPLAYING_OPERATION;
        String message = getMessage(msgID, stackTraceToSingleLineString(e),
            op.toString());
        logError(ErrorLogCategory.SYNCHRONIZATION,
            ErrorLogSeverity.SEVERE_ERROR, message, msgID);
        updateError(changeNumber);
      }
      else
      {
        int msgID = MSGID_EXCEPTION_DECODING_OPERATION;
        String message = getMessage(msgID, stackTraceToSingleLineString(e),
            msg.toString());
        logError(ErrorLogCategory.SYNCHRONIZATION,
            ErrorLogSeverity.SEVERE_ERROR, message, msgID);
      }
    }
    finally
    {
      if (msg.isAssured())
        ack(msg.getChangeNumber());
      incProcessedUpdates();
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
    synchronized (pendingChanges)
    {
      PendingChange change = pendingChanges.get(changeNumber);
      change.setCommitted(true);
      pushCommittedChanges();
    }
  }

  /**
   * Generate a new change number and insert it in the pending list.
   *
   * @param operation The operation for which the change number must be
   *                  generated.
   * @return The new change number.
   */
  private ChangeNumber generateChangeNumber(Operation operation)
  {
    ChangeNumber changeNumber;

    changeNumber = changeNumberGenerator.NewChangeNumber();
    PendingChange change = new PendingChange(changeNumber, operation, null);
    synchronized(pendingChanges)
    {
      pendingChanges.put(changeNumber, change);
    }
    return changeNumber;
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
       * This error may happen the operation is a modification but
       * the entry had been renamed on a different master in the same time.
       * search if the entry has been renamed, and return the new dn
       * of the entry.
       */
      DN newdn = findEntryDN(entryUid);
      if (newdn != null)
      {
        msg.setDn(newdn.toString());
        return false;
      }
      else
        return true;
    }

    // TODO log a message for the repair tool.
    return true;
  }

 /** Solve a conflict detected when replaying a delete operation.
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
       return true;
     }
     else
     {
       /*
        * This entry has been renamed, replay the delete using its new DN.
        */
       msg.setDn(currentDn.toString());
       return false;
     }
   }
   else if (result == ResultCode.NOT_ALLOWED_ON_NONLEAF)
   {
     /*
      * This may happen when we replay a DELETE done on a master
      * but children of this entry have been added on another master.
      */

     /*
      * TODO : either delete all the childs or rename the child below
      * the top suffix by adding entryuuid in dn and delete this entry.
      */
   }
   return true;
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
      UpdateMessage msg) throws Exception
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
         * It is quite weird that the operation result be NO_SUCH_OBJECT.
         * There is nothing more we can do except TODO log a
         * message for the repair tool to look at this problem.
         */
        return true;
      }
      DN parentDn = findEntryDN(parentUniqueId);
      if (parentDn == null)
      {
        /*
         * The parent has been deleted, so this entry should not
         * exist don't do the ADD.
         */
        return true;
      }
      else
      {
        RDN entryRdn = DN.decode(msg.getDn()).getRDN();
        msg.setDn(entryRdn + "," + parentDn);
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
        addConflict(op);
        msg.setDn(generateConflictDn(entryUid, msg.getDn()));
        return false;
      }
    }
    return true;
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

    // if the newDN and the current DN match then the operation
    // is a no-op (this was probably a second replay)
    // don't do anything.
    if (newDN.equals(currentDN))
    {
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
      generateAddConflictOp(op);
      modifyDnMsg.setNewRDN(generateConflictDn(entryUid,
                            modifyDnMsg.getNewRDN()));
      modifyDnMsg.setNewSuperior(newSuperior.toString());
      return false;
    }
    return true;
  }

  /**
   * Generate a modification to add the conflict ObjectClass to an entry
   * whose Dn is now conflicting with another entry.
   *
   * @param op The operation causing the conflict.
   */
  private void generateAddConflictOp(ModifyDNOperation op)
  {
    // TODO
  }

  /**
   * Add the conflict object class to an entry that could
   * not be added because it is conflicting with another entry.
   *
   * @param addOp The conflicting Add Operation.
   */
  private void addConflict(AddOperation addOp)
  {
    /*
     * TODO
     */
  }

  /**
   * Generate the Dn to use for a conflicting entry.
   *
   * @param entryUid The unique identifier of the entry involved in the
   * conflict.
   * @param dn Original dn.
   * @return The generated Dn for a conflicting entry.
   */
  private String generateConflictDn(String entryUid, String dn)
  {
    return "entryuuid=" + entryUid + "+" + dn;
  }

  /**
   * Check if an operation must be processed as an assured operation.
   *
   * @param op the operation to be checked.
   * @return true if the operations must be processed as an assured operation.
   */
  private boolean isAssured(Operation op)
  {
    // TODO : should have a filtering mechanism for checking
    // operation that are assured and operations that are not.
    return false;
  }

  /**
   * Push all committed local changes to the changelog service.
   * PRECONDITION : The pendingChanges lock must be held before calling
   * this method.
   */
  private void pushCommittedChanges()
  {
    if (pendingChanges.isEmpty())
      return;

    ChangeNumber firstChangeNumber = pendingChanges.firstKey();
    PendingChange firstChange = pendingChanges.get(firstChangeNumber);

    while ((firstChange != null) && firstChange.isCommitted())
    {
      if ((firstChange.getOp() != null ) &&
          (firstChange.getOp().isSynchronizationOperation() == false))
      {
        numSentUpdates++;
        broker.publish(firstChange.getMsg());
      }
      state.update(firstChangeNumber);
      pendingChanges.remove(firstChangeNumber);

      if (pendingChanges.isEmpty())
      {
        firstChange = null;
      }
      else
      {
        firstChangeNumber = pendingChanges.firstKey();
        firstChange = pendingChanges.get(firstChangeNumber);
      }
    }
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
   * Get the number of times the synchronization connection was lost.
   * @return The number of times the synchronization connection was lost.
   */
  public int getNumLostConnections()
  {
    return broker.getNumLostConnections();
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
   * Disable the Synchronization on this domain.
   * The session to the Synchronization server will be stopped.
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
  }

  /**
   * Enable back the domain after a previous disable.
   * The domain will connect back to a Synchronization Server and
   * will recreate threads to listen for messages from the Sycnhronization
   * server.
   * The ServerState will also be read again from the local database.
   */
  public void enable()
  {
    state.clear();
    state.loadState();
    disabled = false;

    try
    {
      broker.start(changelogServers);
    } catch (Exception e)
    {
      /* TODO should mark that changelog service is
       * not available, log an error and retry upon timeout
       * should we stop the modifications ?
       */
      e.printStackTrace();
      return;
    }
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
   * initialize the domain (called by SynchronizationDomainLDIFInputStream).
   *
   * @return The bytes. Null when the Done or Err message has been received
   */
  public byte[] receiveEntryBytes()
  {
    SynchronizationMessage msg;
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
        log("receiveEntryBytes: received " + msg);
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
              errorMsg.getDetails() , errorMsg.getMsgID());
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
            "received an unexpected message type" , 1, e);
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
          errorMsg.getDetails() , errorMsg.getMsgID());

      if (ieContext.initializeTask instanceof InitializeTask)
      {
        // Update the task that initiated the import
        ((InitializeTask)ieContext.initializeTask).
        setState(ieContext.updateTaskCompletionState(),ieContext.exception);

        ieContext = null;
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
    DN[] baseDNs = backend.getBaseDNs();

    // FIXME Should getConfigEntry be part of TaskUtils ?
    ConfigEntry configEntry = TaskUtils.getConfigEntry(backend);

    // FIXME Should setBackendEnabled be part of TaskUtils ?
    TaskUtils.setBackendEnabled(configEntry, false);

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
        backend.clearBackend(configEntry, baseDNs);
      }
      finally
      {
        LockFileManager.releaseLock(lockFile, failureReason);
      }
    }
    finally
    {
      TaskUtils.setBackendEnabled(configEntry, true);
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
  private void log(String message)
  {
    if (debugEnabled())
    {
      debugInfo("DebugInfo" + message);
      int    msgID   = MSGID_UNKNOWN_TYPE;
      logError(ErrorLogCategory.SYNCHRONIZATION,
          ErrorLogSeverity.NOTICE,
          "SynchronizationDomain/ " + message, msgID);
    }
  }

  /**
   * Export the entries.
   * @throws DirectoryException when an error occured
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
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_LOCK_BACKEND;
        String message = getMessage(msgID, backend.getBackendID(),
            String.valueOf(failureReason));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
            message, msgID);
        throw new DirectoryException(
            ResultCode.OTHER, message, msgID, null);
      }
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDIFEXPORT_CANNOT_LOCK_BACKEND;
      String message = getMessage(msgID, backend.getBackendID());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
          message + " " + stackTraceToSingleLineString(e), msgID);
      throw new DirectoryException(
          ResultCode.OTHER, message, msgID, null);
    }

    SynchroLDIFOutputStream os = new SynchroLDIFOutputStream(this);

    LDIFExportConfig exportConfig = new LDIFExportConfig(os);

    //  Launch the export.
    try
    {
      DN[] baseDNs = {this.baseDN};
      backend.exportLDIF(backendConfigEntry, baseDNs, exportConfig);
    }
    catch (DirectoryException de)
    {
      int    msgID   = MSGID_LDIFEXPORT_ERROR_DURING_EXPORT;
      String message = getMessage(msgID, de.getErrorMessage());
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
          msgID);
      throw new DirectoryException(
          ResultCode.OTHER, message, msgID, null);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_LDIFEXPORT_ERROR_DURING_EXPORT;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR, message,
          msgID);
      throw new DirectoryException(
          ResultCode.OTHER, message, msgID, null);
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
          int    msgID   = MSGID_LDIFEXPORT_CANNOT_UNLOCK_BACKEND;
          String message = getMessage(msgID, backend.getBackendID(),
              String.valueOf(failureReason));
          logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_WARNING,
              message, msgID);
          throw new DirectoryException(
              ResultCode.OTHER, message, msgID, null);
        }
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_LDIFEXPORT_CANNOT_UNLOCK_BACKEND;
        String message = getMessage(msgID, backend.getBackendID(),
            stackTraceToSingleLineString(e));
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_WARNING,
            message, msgID);
        throw new DirectoryException(
            ResultCode.OTHER, message, msgID, null);
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
    ArrayList<Backend>     backendList = new ArrayList<Backend>();
    ArrayList<ConfigEntry> entryList   = new ArrayList<ConfigEntry>();
    ArrayList<List<DN>> dnList = new ArrayList<List<DN>>();

    Backend backend = null;
    ConfigEntry backendConfigEntry = null;
    List<DN> branches = new ArrayList<DN>(0);

    // Retrieves the backend related to this domain
    Backend domainBackend = DirectoryServer.getBackend(baseDN);
    if (domainBackend == null)
    {
      int    msgID   = MSGID_CANNOT_DECODE_BASE_DN;
      String message = getMessage(msgID, DN_BACKEND_BASE);
      throw new DirectoryException(
          ResultCode.OTHER, message, msgID, null);
    }

    // Retrieves its config entry and its DNs
    int code = getBackends(backendList, entryList, dnList);
    if (code != 0)
    {
      int    msgID   = MSGID_CANNOT_DECODE_BASE_DN;
      String message = getMessage(msgID, DN_BACKEND_BASE);
      throw new DirectoryException(
          ResultCode.OTHER, message, msgID, null);
    }

    int numBackends = backendList.size();
    for (int i=0; i < numBackends; i++)
    {
      Backend b = backendList.get(i);

      if (domainBackend.getBackendID() != b.getBackendID())
      {
        continue;
      }

      if (backend == null)
      {
        backend = domainBackend;
        backendConfigEntry = entryList.get(i).duplicate();
        branches = dnList.get(i);
      }
      else
      {
        int msgID = MSGID_LDIFIMPORT_MULTIPLE_BACKENDS_FOR_ID;
        String message = getMessage(msgID, domainBackend.getBackendID());
        logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
            message, msgID);
        throw new DirectoryException(
            ResultCode.OTHER, message, msgID, null);
      }
    }

    if (backend == null)
    {
      int    msgID   = MSGID_LDIFIMPORT_NO_BACKENDS_FOR_ID;
      String message = getMessage(msgID, domainBackend.getBackendID());
      logError(ErrorLogCategory.BACKEND,
          ErrorLogSeverity.SEVERE_ERROR, message, msgID);
      throw new DirectoryException(
          ResultCode.OTHER, message, msgID, null);
    }
    else if (! backend.supportsLDIFExport())
    {
      int    msgID   = MSGID_LDIFIMPORT_CANNOT_IMPORT;
      String message = getMessage(msgID, 0); // FIXME
      logError(ErrorLogCategory.BACKEND,
          ErrorLogSeverity.SEVERE_ERROR, message, msgID);
      throw new DirectoryException(
          ResultCode.OTHER, message, msgID, null);
    }


    this.backend = backend;
    this.backendConfigEntry = backendConfigEntry;
    this.branches = branches;
  }


  /**
   * Sends lDIFEntry entry lines to the export target currently set.
   *
   * @param lDIFEntry The lines for the LDIF entry.
   * @throws IOException when an error occured.
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
   * Retrieves information about the backends defined in the Directory Server
   * configuration.
   *
   * @param  backendList  A list into which instantiated (but not initialized)
   *                      backend instances will be placed.
   * @param  entryList    A list into which the config entries associated with
   *                      the backends will be placed.
   * @param  dnList       A list into which the set of base DNs for each backend
   *                      will be placed.
   */
  private static int getBackends(ArrayList<Backend> backendList,
                                 ArrayList<ConfigEntry> entryList,
                                 ArrayList<List<DN>> dnList)
  throws DirectoryException
  {
    //  Get the base entry for all backend configuration.
    DN backendBaseDN = null;
    try
    {
      backendBaseDN = DN.decode(DN_BACKEND_BASE);
    }
    catch (DirectoryException de)
    {
      int    msgID   = MSGID_CANNOT_DECODE_BASE_DN;
      String message = getMessage(msgID, DN_BACKEND_BASE, de.getErrorMessage());
      throw new DirectoryException(
          ResultCode.OTHER, message, msgID, null);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CANNOT_DECODE_BASE_DN;
      String message = getMessage(msgID, DN_BACKEND_BASE,
          stackTraceToSingleLineString(e));
      throw new DirectoryException(
          ResultCode.OTHER, message, msgID, null);
    }

    ConfigEntry baseEntry = null;
    try
    {
      baseEntry = DirectoryServer.getConfigEntry(backendBaseDN);
    }
    catch (ConfigException ce)
    {
      int    msgID   = MSGID_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY;
      String message = getMessage(msgID, DN_BACKEND_BASE, ce.getMessage());
      throw new DirectoryException(
          ResultCode.OTHER, message, msgID, null);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_CANNOT_RETRIEVE_BACKEND_BASE_ENTRY;
      String message = getMessage(msgID, DN_BACKEND_BASE,
          stackTraceToSingleLineString(e));
      throw new DirectoryException(
          ResultCode.OTHER, message, msgID, null);
    }


    //  Iterate through the immediate children, attempting to parse them as
    //  backends.
    for (ConfigEntry configEntry : baseEntry.getChildren().values())
    {
      // Get the backend ID attribute from the entry.  If there isn't one, then
      // skip the entry.
      String backendID = null;
      try
      {
        int msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BACKEND_ID;
        StringConfigAttribute idStub =
          new StringConfigAttribute(ATTR_BACKEND_ID, getMessage(msgID),
              true, false, true);
        StringConfigAttribute idAttr =
          (StringConfigAttribute) configEntry.getConfigAttribute(idStub);
        if (idAttr == null)
        {
          continue;
        }
        else
        {
          backendID = idAttr.activeValue();
        }
      }
      catch (ConfigException ce)
      {
        int    msgID   = MSGID_CANNOT_DETERMINE_BACKEND_ID;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
            ce.getMessage());
        throw new DirectoryException(
            ResultCode.OTHER, message, msgID, null);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_CANNOT_DETERMINE_BACKEND_ID;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
            stackTraceToSingleLineString(e));
        throw new DirectoryException(
            ResultCode.OTHER, message, msgID, null);
      }


      //    Get the backend class name attribute from the entry.  If there isn't
      //    one, then just skip the entry.
      String backendClassName = null;
      try
      {
        int msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_CLASS;
        StringConfigAttribute classStub =
          new StringConfigAttribute(ATTR_BACKEND_CLASS, getMessage(msgID),
              true, false, false);
        StringConfigAttribute classAttr =
          (StringConfigAttribute) configEntry.getConfigAttribute(classStub);
        if (classAttr == null)
        {
          continue;
        }
        else
        {
          backendClassName = classAttr.activeValue();
        }
      }
      catch (ConfigException ce)
      {
        int    msgID   = MSGID_CANNOT_DETERMINE_BACKEND_CLASS;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
            ce.getMessage());
        throw new DirectoryException(
            ResultCode.OTHER, message, msgID, null);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_CANNOT_DETERMINE_BACKEND_CLASS;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
            stackTraceToSingleLineString(e));
        throw new DirectoryException(
            ResultCode.OTHER, message, msgID, null);
      }

      Class backendClass = null;
      try
      {
        backendClass = Class.forName(backendClassName);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_CANNOT_LOAD_BACKEND_CLASS;
        String message = getMessage(msgID, backendClassName,
            String.valueOf(configEntry.getDN()),
            stackTraceToSingleLineString(e));
        throw new DirectoryException(
            ResultCode.OTHER, message, msgID, null);
      }

      Backend backend = null;
      try
      {
        backend = (Backend) backendClass.newInstance();
        backend.setBackendID(backendID);
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_CANNOT_INSTANTIATE_BACKEND_CLASS;
        String message = getMessage(msgID, backendClassName,
            String.valueOf(configEntry.getDN()),
            stackTraceToSingleLineString(e));
        throw new DirectoryException(
            ResultCode.OTHER, message, msgID, null);      }


      // Get the base DN attribute from the entry.  If there isn't one, then
      // just skip this entry.
      List<DN> baseDNs = null;
      try
      {
        int msgID = MSGID_CONFIG_BACKEND_ATTR_DESCRIPTION_BASE_DNS;
        DNConfigAttribute baseDNStub =
          new DNConfigAttribute(ATTR_BACKEND_BASE_DN, getMessage(msgID),
              true, true, true);
        DNConfigAttribute baseDNAttr =
          (DNConfigAttribute) configEntry.getConfigAttribute(baseDNStub);
        if (baseDNAttr == null)
        {
          msgID = MSGID_NO_BASES_FOR_BACKEND;
          String message = getMessage(msgID,
              String.valueOf(configEntry.getDN()));
          throw new DirectoryException(
              DirectoryServer.getServerErrorResultCode(), message,msgID, null);
        }
        else
        {
          baseDNs = baseDNAttr.activeValues();
        }
      }
      catch (Exception e)
      {
        int    msgID   = MSGID_CANNOT_DETERMINE_BASES_FOR_BACKEND;
        String message = getMessage(msgID, String.valueOf(configEntry.getDN()),
            stackTraceToSingleLineString(e));
        throw new DirectoryException(
            ResultCode.OTHER, message, msgID, null);      }


      backendList.add(backend);
      entryList.add(configEntry);
      dnList.add(baseDNs);
    }
    return 0;
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
      if (source >= -1)
      {
        // TODO Verifies serverID is in the domain
        // We shold check here that this is a server implied
        // in the current domain.

        log("Source decoded for import:" + source);
        return source;
      }
    }
    catch(Exception e)
    {
      cause = e;
    }

    ResultCode resultCode = ResultCode.OTHER;
    int errorMessageID = MSGID_INVALID_IMPORT_SOURCE;
    String message = getMessage(errorMessageID);

    if (cause != null)
      throw new DirectoryException(
          resultCode, message, errorMessageID, cause);
    else
      throw new DirectoryException(
          resultCode, message, errorMessageID);
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
    int errorMessageID = MSGID_INVALID_EXPORT_TARGET;
    String message = getMessage(errorMessageID);

    if (cause != null)
      throw new DirectoryException(
          resultCode, message, errorMessageID, cause);
    else
      throw new DirectoryException(
          resultCode, message, errorMessageID);

  }

  private synchronized void acquireIEContext()
  throws DirectoryException
  {
    if (ieContext != null)
    {
      // Rejects 2 simultaneous exports
      int msgID = MSGID_SIMULTANEOUS_IMPORT_EXPORT_REJECTED;
      String message = getMessage(msgID);
      throw new DirectoryException(ResultCode.OTHER,
          message, msgID);
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
    acquireIEContext();

    ieContext.exportTarget = target;
    ieContext.initializeTask = initTask;
    ieContext.initTaskCounters(backend.getEntryCount());

    // Send start message
    InitializeTargetMessage initializeMessage = new InitializeTargetMessage(
        baseDN, serverId, ieContext.exportTarget, requestorID,
        ieContext.entryLeftCount);

    log("SD : publishes " + initializeMessage +
        " for #entries=" + ieContext.entryCount);

    broker.publish(initializeMessage);

    // make an export and send entries
    exportBackend();

    // Successfull termnation
    DoneMessage doneMsg = new DoneMessage(serverId,
      initializeMessage.getDestination());
    broker.publish(doneMsg);

    if (ieContext != null)
    {
      ieContext.updateTaskCompletionState();
      ieContext = null;
    }
  }

  /**
   * Process backend before import.
   * @param backend The backend.
   * @param backendConfigEntry The config entry of the backend.
   * @throws Exception
   */
  private void preBackendImport(Backend backend,
      ConfigEntry backendConfigEntry)
  throws Exception
  {
    // Stop saving state
    stateSavingDisabled = true;

    // Clear the backend
    clearJEBackend(false,backend.getBackendID(),null);

    // FIXME setBackendEnabled should be part of TaskUtils ?
    TaskUtils.setBackendEnabled(backendConfigEntry, false);

    // Acquire an exclusive lock for the backend.
    String lockFile = LockFileManager.getBackendLockFileName(backend);
    StringBuilder failureReason = new StringBuilder();
    if (! LockFileManager.acquireExclusiveLock(lockFile, failureReason))
    {
      int    msgID   = MSGID_LDIFIMPORT_CANNOT_LOCK_BACKEND;
      String message = getMessage(msgID, backend.getBackendID(),
          String.valueOf(failureReason));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
          message, msgID);
      throw new DirectoryException(ResultCode.OTHER, message, msgID);
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
      log("startImport");

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

      preBackendImport(this.backend, this.backendConfigEntry);

      DN[] baseDNs = {baseDN};
      ieContext.ldifImportInputStream = new SynchroLDIFInputStream(this);
      importConfig =
        new LDIFImportConfig(ieContext.ldifImportInputStream);
      importConfig.setIncludeBranches(this.branches);

      // TODO How to deal with rejected entries during the import
      // importConfig.writeRejectedEntries("rejectedImport",
      // ExistingFileBehavior.OVERWRITE);

      // Process import
      this.backend.importLDIF(this.backendConfigEntry, baseDNs, importConfig);

      stateSavingDisabled = false;

      // Re-exchange state with SS
      broker.stop();
      broker.start(changelogServers);

    }
    catch(Exception e)
    {
      throw new DirectoryException(ResultCode.OTHER, e.getLocalizedMessage(),
          2);// FIXME
    }
    finally
    {
      // Cleanup
      importConfig.close();

      // Re-enable backend
      closeBackendImport(this.backend, this.backendConfigEntry);

      // Update the task that initiated the import
      if ((ieContext != null ) && (ieContext.initializeTask != null))
      {
        ((InitializeTask)ieContext.initializeTask).
        setState(ieContext.updateTaskCompletionState(),ieContext.exception);
      }

      releaseIEContext();

      log("End importBackend");
    }
    // Success
  }

  /**
   * Make post import operations.
   * @param backend The backend implied in the import.
   * @param backendConfigEntry The config entry of the backend.
   * @exception DirectoryException Thrown when an error occurs.
   */
  protected void closeBackendImport(Backend backend,
      ConfigEntry backendConfigEntry)
  throws DirectoryException
  {
    String lockFile = LockFileManager.getBackendLockFileName(backend);
    StringBuilder failureReason = new StringBuilder();

    // Release lock
    if (!LockFileManager.releaseLock(lockFile, failureReason))
    {
      int    msgID   = MSGID_LDIFIMPORT_CANNOT_UNLOCK_BACKEND;
      String message = getMessage(msgID, backend.getBackendID(),
          String.valueOf(failureReason));
      logError(ErrorLogCategory.BACKEND, ErrorLogSeverity.SEVERE_ERROR,
          message, msgID);
      new DirectoryException(ResultCode.OTHER, message, msgID);
    }

    // FIXME setBackendEnabled should be part taskUtils ?
    TaskUtils.setBackendEnabled(backendConfigEntry, true);
  }

  /**
   * Retrieves a synchronization domain based on the baseDN.
   *
   * @param baseDN The baseDN of the domain to retrieve
   * @return The domain retrieved
   * @throws DirectoryException When an error occured.
   */
  public static SynchronizationDomain retrievesSynchronizationDomain(DN baseDN)
  throws DirectoryException
  {
    SynchronizationDomain synchronizationDomain = null;

    // Retrieves the domain
    DirectoryServer.getSynchronizationProviders();
    for (SynchronizationProvider provider :
      DirectoryServer.getSynchronizationProviders())
    {
      if (!( provider instanceof MultimasterSynchronization))
      {
        int msgID = LogMessages.MSGID_INVALID_PROVIDER;
        String message = getMessage(msgID);
        throw new DirectoryException(ResultCode.OTHER,
            message, msgID);
      }

      // From the domainDN retrieves the synchronization domain
      SynchronizationDomain sdomain =
        MultimasterSynchronization.findDomain(baseDN, null);
      if (sdomain == null)
      {
        int msgID = LogMessages.MSGID_NO_MATCHING_DOMAIN;
        String message = getMessage(msgID) + " " + baseDN;
        throw new DirectoryException(ResultCode.OTHER,
            message, msgID);
      }

      if (synchronizationDomain != null)
      {
        // Should never happen
        int msgID = LogMessages.MSGID_MULTIPLE_MATCHING_DOMAIN;
        String message = getMessage(msgID);
        throw new DirectoryException(ResultCode.OTHER,
            message, msgID);
      }
      synchronizationDomain = sdomain;
    }
    return synchronizationDomain;
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
    Operation op =
      new ModifyOperation(InternalClientConnection.getRootConnection(),
                          InternalClientConnection.nextOperationID(),
                          InternalClientConnection.nextMessageID(),
                          null, DirectoryServer.getSchemaDN(),
                          modifications);

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
      MultimasterDomainCfg configuration, List<String> unacceptableReasons)
  {
    // Check that there is not already a domain with the same DN
    // TODO : Check that the server id is a short
    DN dn = configuration.getSynchronizationDN();
    if (MultimasterSynchronization.findDomain(dn,null) != null)
    {
      String message = getMessage(MSGID_SYNC_INVALID_DN, dn.toString());
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
    // The other parameters needs to be renegociated with the Changelog Server.
    // so that requires restarting the session with the Changelog Server.
    changelogServers = configuration.getChangelogServer();
    maxReceiveQueue = configuration.getMaxReceiveQueue();
    maxReceiveDelay = (int) configuration.getMaxReceiveDelay();
    maxSendQueue = configuration.getMaxSendQueue();
    maxSendDelay = (int) configuration.getMaxSendDelay();
    window = configuration.getWindowSize();
    heartbeatInterval = configuration.getHeartbeatInterval();
    broker.changeConfig(changelogServers, maxReceiveQueue, maxReceiveDelay,
                        maxSendQueue, maxSendDelay, window, heartbeatInterval);

    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
         MultimasterDomainCfg configuration, List<String> unacceptableReasons)
  {
    return true;
  }
}
