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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.synchronization.plugin;

import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.opends.server.util.ServerConstants.
     TIME_UNIT_MILLISECONDS_ABBR;
import static org.opends.server.util.ServerConstants.
     TIME_UNIT_MILLISECONDS_FULL;
import static org.opends.server.util.ServerConstants.TIME_UNIT_SECONDS_ABBR;
import static org.opends.server.util.ServerConstants.TIME_UNIT_SECONDS_FULL;
import static org.opends.server.synchronization.common.LogMessages.*;
import static org.opends.server.synchronization.plugin.Historical.*;
import static org.opends.server.synchronization.protocol.OperationContext.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.MessageHandler.*;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DataFormatException;

import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.DirectoryThread;
import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.config.IntegerConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.config.IntegerWithUnitConfigAttribute;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.Operation;
import org.opends.server.messages.MessageHandler;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.ldap.LDAPException;
import org.opends.server.synchronization.common.ChangeNumber;
import org.opends.server.synchronization.common.ChangeNumberGenerator;
import org.opends.server.synchronization.common.ServerState;
import org.opends.server.synchronization.protocol.AckMessage;
import org.opends.server.synchronization.protocol.AddContext;
import org.opends.server.synchronization.protocol.DeleteContext;
import org.opends.server.synchronization.protocol.ModifyContext;
import org.opends.server.synchronization.protocol.ModifyDNMsg;
import org.opends.server.synchronization.protocol.ModifyDnContext;
import org.opends.server.synchronization.protocol.OperationContext;
import org.opends.server.synchronization.protocol.SynchronizationMessage;
import org.opends.server.synchronization.protocol.UpdateMessage;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.DereferencePolicy;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
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
       implements ConfigurableComponent
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

  private short serverId;

  private BooleanConfigAttribute receiveStatusStub;
  private int listenerThreadNumber = 10;
  private boolean receiveStatus = true;

  private List<String> changelogServers;

  private DN baseDN;

  private List<ConfigAttribute> configAttributes =
                                          new ArrayList<ConfigAttribute>();

  private boolean shutdown = false;

  private DN configDn;

  private InternalClientConnection conn =
      InternalClientConnection.getRootConnection();

  static String CHANGELOG_SERVER_ATTR = "ds-cfg-changelog-server";
  static String BASE_DN_ATTR = "ds-cfg-synchronization-dn";
  static String SERVER_ID_ATTR = "ds-cfg-directory-server-id";
  static String RECEIVE_STATUS = "ds-cfg-receive-status";
  static String MAX_RECEIVE_QUEUE = "ds-cfg-max-receive-queue";
  static String MAX_RECEIVE_DELAY = "ds-cfg-max-receive-delay";
  static String MAX_SEND_QUEUE = "ds-cfg-max-send-queue";
  static String MAX_SEND_DELAY = "ds-cfg-max-send-delay";
  static String WINDOW_SIZE = "ds-cfg-window-size";
  static String HEARTBEAT_INTERVAL = "ds-cfg-heartbeat-interval";

  private static final StringConfigAttribute changelogStub =
    new StringConfigAttribute(CHANGELOG_SERVER_ATTR,
        "changelog server information", true, true, false);

  private static final IntegerConfigAttribute serverIdStub =
    new IntegerConfigAttribute(SERVER_ID_ATTR, "server ID", true, false,
                               false, true, 0, true, 65535);

  private static final DNConfigAttribute baseDnStub =
    new DNConfigAttribute(BASE_DN_ATTR, "synchronization base DN",
                          true, false, false);

  /**
   * The set of time units that will be used for expressing the heartbeat
   * interval.
   */
  private static final LinkedHashMap<String,Double> timeUnits =
       new LinkedHashMap<String,Double>();



  static
  {
    timeUnits.put(TIME_UNIT_MILLISECONDS_ABBR, 1D);
    timeUnits.put(TIME_UNIT_MILLISECONDS_FULL, 1D);
    timeUnits.put(TIME_UNIT_SECONDS_ABBR, 1000D);
    timeUnits.put(TIME_UNIT_SECONDS_FULL, 1000D);
  }



  /**
   * Creates a new SynchronizationDomain using configuration from configEntry.
   *
   * @param configEntry The ConfigEntry to use to read the configuration of this
   *                    SynchronizationDomain.
   * @throws ConfigException In case of invalid configuration.
   */
  public SynchronizationDomain(ConfigEntry configEntry) throws ConfigException
  {
    super("Synchronization flush");
    /*
     * read the centralized changelog server configuration
     * this is a multivalued attribute
     */
    StringConfigAttribute changelogServer =
      (StringConfigAttribute) configEntry.getConfigAttribute(changelogStub);

    if (changelogServer == null)
    {
      throw new ConfigException(MSGID_NEED_CHANGELOG_SERVER,
          MessageHandler.getMessage(MSGID_NEED_CHANGELOG_SERVER,
              configEntry.getDN().toString()) );
    }
    changelogServers = changelogServer.activeValues();
    configAttributes.add(changelogServer);

    /*
     * read the server Id information
     * this is a single valued integer, its value must fit on a short integer
     */
    IntegerConfigAttribute serverIdAttr =
      (IntegerConfigAttribute) configEntry.getConfigAttribute(serverIdStub);
    if (serverIdAttr == null)
    {
      throw new ConfigException(MSGID_NEED_SERVER_ID,
          MessageHandler.getMessage(MSGID_NEED_SERVER_ID,
              configEntry.getDN().toString())  );
    }
    serverId = (short) serverIdAttr.activeIntValue();
    configAttributes.add(serverIdAttr);

    /*
     * read the base DN
     */
    DNConfigAttribute baseDn =
      (DNConfigAttribute) configEntry.getConfigAttribute(baseDnStub);
    if (baseDn == null)
      baseDN = null;  // Attribute is not present : don't set a limit
    else
      baseDN = baseDn.activeValue();
    configAttributes.add(baseDn);

    state = new PersistentServerState(baseDN);
    state.loadState();

    /*
     * Read the Receive Status.
     */
    receiveStatusStub = new BooleanConfigAttribute(RECEIVE_STATUS,
        "receive status", false);
    BooleanConfigAttribute receiveStatusAttr = (BooleanConfigAttribute)
          configEntry.getConfigAttribute(receiveStatusStub);
    if (receiveStatusAttr != null)
    {
      receiveStatus = receiveStatusAttr.activeValue();
      configAttributes.add(receiveStatusAttr);
    }

    /*
     * read the synchronization flow control configuration.
     */
    IntegerConfigAttribute maxReceiveQueueStub =
      new IntegerConfigAttribute(MAX_RECEIVE_QUEUE, "max receive queue",
                                 false, false, false, true, 0,false, 0);

    IntegerConfigAttribute maxReceiveQueueAttr = (IntegerConfigAttribute)
              configEntry.getConfigAttribute(maxReceiveQueueStub);
    if (maxReceiveQueueAttr == null)
      maxReceiveQueue = 0;  // Attribute is not present : don't set a limit
    else
    {
      maxReceiveQueue = maxReceiveQueueAttr.activeIntValue();
      configAttributes.add(maxReceiveQueueAttr);
    }

    IntegerConfigAttribute maxReceiveDelayStub =
      new IntegerConfigAttribute(MAX_RECEIVE_DELAY, "max receive delay",
                                 false, false, false, true, 0, false, 0);
    IntegerConfigAttribute maxReceiveDelayAttr = (IntegerConfigAttribute)
              configEntry.getConfigAttribute(maxReceiveDelayStub);
    if (maxReceiveDelayAttr == null)
      maxReceiveDelay = 0;  // Attribute is not present : don't set a limit
    else
    {
      maxReceiveDelay = maxReceiveDelayAttr.activeIntValue();
      configAttributes.add(maxReceiveDelayAttr);
    }

    IntegerConfigAttribute maxSendQueueStub =
      new IntegerConfigAttribute(MAX_SEND_QUEUE, "max send queue",
                                 false, false, false, true, 0, false, 0);
    IntegerConfigAttribute maxSendQueueAttr =
      (IntegerConfigAttribute) configEntry.getConfigAttribute(maxSendQueueStub);
    if (maxSendQueueAttr == null)
      maxSendQueue = 0;  // Attribute is not present : don't set a limit
    else
    {
      maxSendQueue = maxSendQueueAttr.activeIntValue();
      configAttributes.add(maxSendQueueAttr);
    }

    IntegerConfigAttribute maxSendDelayStub =
      new IntegerConfigAttribute(MAX_SEND_DELAY, "max send delay",
                                 false, false, false, true, 0, false, 0);
    IntegerConfigAttribute maxSendDelayAttr =
      (IntegerConfigAttribute) configEntry.getConfigAttribute(maxSendDelayStub);
    if (maxSendDelayAttr == null)
      maxSendDelay = 0;  // Attribute is not present : don't set a limit
    else
    {
      maxSendDelay = maxSendDelayAttr.activeIntValue();
      configAttributes.add(maxSendDelayAttr);
    }

    Integer window;
    IntegerConfigAttribute windowStub =
      new IntegerConfigAttribute(WINDOW_SIZE, "window size",
                                 false, false, false, true, 0, false, 0);
    IntegerConfigAttribute windowAttr =
      (IntegerConfigAttribute) configEntry.getConfigAttribute(windowStub);
    if (windowAttr == null)
      window = 100;  // Attribute is not present : use the default value
    else
    {
      window = windowAttr.activeIntValue();
      configAttributes.add(windowAttr);
    }

    IntegerWithUnitConfigAttribute heartbeatStub =
      new IntegerWithUnitConfigAttribute(HEARTBEAT_INTERVAL,
                                         "heartbeat interval",
                                         false, timeUnits, true, 0, false, 0);
    IntegerWithUnitConfigAttribute heartbeatAttr =
      (IntegerWithUnitConfigAttribute)
           configEntry.getConfigAttribute(heartbeatStub);
    if (heartbeatAttr == null)
    {
      // Attribute is not present : use the default value
      heartbeatInterval = 1000;
    }
    else
    {
      heartbeatInterval = heartbeatAttr.activeCalculatedValue();
      configAttributes.add(heartbeatAttr);
    }

    configDn = configEntry.getDN();
    DirectoryServer.registerConfigurableComponent(this);

    monitor = new SynchronizationMonitor(this);
    DirectoryServer.registerMonitorProvider(monitor);

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
    } catch (Exception e)
    {
     /* TODO should mark that changelog service is
      * not available, log an error and retry upon timeout
      * should we stop the modifications ?
      */
    }
  }

  /**
   * {@inheritDoc}
   */
  public DN getConfigurableComponentEntryDN()
  {
    return configDn;
  }

  /**
   * {@inheritDoc}
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {
    return configAttributes;
  }

  /**
   * {@inheritDoc}
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
      List<String> unacceptableReasons)
  {
    boolean acceptable = true;
    StringConfigAttribute changelog = null;
    try
    {
      changelog = (StringConfigAttribute)
                                  configEntry.getConfigAttribute(changelogStub);
    } catch (ConfigException e)
    {
      acceptable = false;
      unacceptableReasons.add("Need at least one changelog server.");
    }
    if (changelog == null)
    {
      acceptable = false;
      unacceptableReasons.add("Need at least one changelog server.");
    }
    return acceptable;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
      boolean detailedResults)
  {
    StringConfigAttribute changelog = null;
    List<String> newChangelogServers;
    boolean newReceiveStatus;

    try
    {
      /*
       *  check if changelog server list changed
       */
      changelog = (StringConfigAttribute)
                                  configEntry.getConfigAttribute(changelogStub);

      newChangelogServers = changelog.activeValues();

      boolean sameConf = true;
      for (String s :newChangelogServers)
        if (!changelogServers.contains(s))
          sameConf = false;
      for (String s : changelogServers)
        if (!newChangelogServers.contains(s))
          sameConf = false;

      if (!sameConf)
      {
        broker.stop();
        changelogServers = newChangelogServers;
        broker.start(changelogServers);
      }

      /*
       * check if reception should be disabled
       */
      newReceiveStatus = ((BooleanConfigAttribute)
               configEntry.getConfigAttribute(receiveStatusStub)).activeValue();
      if (newReceiveStatus != receiveStatus)
      {
        /*
         * was disabled and moved to enabled
         */
        if (newReceiveStatus)
        {
          broker.restartReceive();
          for (int i=0; i<listenerThreadNumber; i++)
          {
            ListenerThread myThread = new ListenerThread(this);
            myThread.start();
            synchroThreads.add(myThread);
          }
        }
        else
        {
          /* was enabled and moved to disabled */
          broker.suspendReceive();
          // FIXME Need a way to stop these threads.
          // Setting the shutdown flag does not stop them until they have
          // consumed and discarded one more message each.
//          for (ListenerThread thread : synchroThreads)
//          {
//            thread.shutdown();
//          }
          synchroThreads.clear();
        }
        receiveStatus = newReceiveStatus;
      }

    } catch (Exception e)
    {
      /* this should never happen because the parameters have been
       * validated by hasAcceptableConfiguration
       */
      return new ConfigChangeResult(ResultCode.OPERATIONS_ERROR, false);
    }

    return new ConfigChangeResult(ResultCode.SUCCESS, false);
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
      ctx = new ModifyContext(changeNumber, modifiedEntryUUID);
      modifyOperation.setAttachment(SYNCHROCONTEXT, ctx);
    }
    else
    {
      String modifiedEntryUUID = ctx.getEntryUid();
      String currentEntryUUID = Historical.getEntryUuid(modifiedEntry);
      if (!currentEntryUUID.equals(modifiedEntryUUID))
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
   * Receive an update message from the changelog.
   * also responsible for updating the list of pending changes
   * @return the received message
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

    /* synchroThreads
     * create the threads that will wait for incoming changes
     * TODO : should use a pool of threads shared between all the servers
     * TODO : need to make number of thread configurable
     * TODO : need to handle operation dependencies
     */
    for (int i=0; i<listenerThreadNumber; i++)
    {
      ListenerThread myThread = new ListenerThread(this);
      myThread.start();
      synchroThreads.add(myThread);
    }

    while (shutdown  == false)
    {
      try
      {
        synchronized (this)
        {
          this.wait(1000);
          // save the RUV
          state.save();
        }
      } catch (InterruptedException e)
      { }
    }
    state.save();
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

    // stop the ChangelogBroker
    broker.stop();
  }

  /**
   * Get the DN where the ServerState is stored.
   * @return The DN where the ServerState is stored.
   */
  public DN getServerStateDN()
  {
    return state.getServerStateDn();
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

    if (result == ResultCode.NO_SUCH_OBJECT)
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
   * Check if a ConfigEntry is valid.
   * @param configEntry The config entry that needs to be checked.
   * @param unacceptableReason A description of the reason why the config entry
   *                           is not acceptable (if return is false).
   * @return a boolean indicating if the configEntry is valid.
   */
  public static boolean checkConfigEntry(ConfigEntry configEntry,
      StringBuilder unacceptableReason)
  {
    try
    {
    StringConfigAttribute changelogServer =
      (StringConfigAttribute) configEntry.getConfigAttribute(changelogStub);

    if (changelogServer == null)
    {
      unacceptableReason.append(
          MessageHandler.getMessage(MSGID_NEED_CHANGELOG_SERVER,
          configEntry.getDN().toString()) );
      return false;
    }

    /*
     * read the server Id information
     * this is a single valued integer, its value must fit on a short integer
     */
    IntegerConfigAttribute serverIdAttr =
      (IntegerConfigAttribute) configEntry.getConfigAttribute(serverIdStub);
    if (serverIdAttr == null)
    {
      unacceptableReason.append(
          MessageHandler.getMessage(MSGID_NEED_SERVER_ID,
              configEntry.getDN().toString()) );
      return false;
    }
    }
    catch (ConfigException e)
    {
      unacceptableReason.append(e.getMessage());
      return false;
    }
    return true;
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
}
