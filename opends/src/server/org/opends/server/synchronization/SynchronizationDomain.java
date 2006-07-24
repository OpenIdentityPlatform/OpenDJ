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
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.synchronization;

import static org.opends.server.util.TimeThread.getTime;
import static org.opends.server.synchronization.SynchMessages.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.MessageHandler.*;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.DirectoryThread;
import org.opends.server.config.BooleanConfigAttribute;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.config.IntegerConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.Operation;
import org.opends.server.messages.MessageHandler;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;
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

  private List<ListenerThread> synchroThreads;
  private SortedMap<ChangeNumber, PendingChange> pendingChanges =
    new TreeMap<ChangeNumber, PendingChange>();
  private SortedMap<ChangeNumber, UpdateMessage> waitingAckMsgs =
    new TreeMap<ChangeNumber, UpdateMessage>();
  private int numRcvdUpdates = 0;
  private int numSentUpdates = 0;
  private int numProcessedUpdates = 0;
  private int debugCount = 0;
  private ServerState state;
  private int numReplayedPostOpCalled = 0;

  private boolean assuredFlag = false;


  private int maxReceiveQueue = 0;
  private int maxSendQueue = 0;
  private int maxReceiveDelay = 0;
  private int maxSendDelay = 0;

  private short serverId;

  private BooleanConfigAttribute receiveStatusStub;
  private int listenerThreadNumber = 10;
  private StringConfigAttribute changelogStub;
  private boolean receiveStatus = true;

  private List<String> changelogServers;

  private DN baseDN;

  private List<ConfigAttribute> configAttributes =
                                          new ArrayList<ConfigAttribute>();

  private boolean shutdown = false;

  private DN configDn;

  static String CHANGELOG_SERVER_ATTR = "ds-cfg-changelog-server";
  static String BASE_DN_ATTR = "ds-cfg-synchronization-dn";
  static String SERVER_ID_ATTR = "ds-cfg-directory-server-id";
  static String RECEIVE_STATUS = "ds-cfg-receive-status";
  static String MAX_RECEIVE_QUEUE = "ds-cfg-max-receive-queue";
  static String MAX_RECEIVE_DELAY = "ds-cfg-max-receive-delay";
  static String MAX_SEND_QUEUE = "ds-cfg-max-send-queue";
  static String MAX_SEND_DELAY = "ds-cfg-max-send-delay";


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
    changelogStub = new StringConfigAttribute(CHANGELOG_SERVER_ATTR,
        "changelog server information", true, true, false);
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
    IntegerConfigAttribute serverIdStub =
      new IntegerConfigAttribute(SERVER_ID_ATTR, "server ID", true, false,
                                 false, true, 0, true, 65535);
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
    DNConfigAttribute baseDnStub =
      new DNConfigAttribute(BASE_DN_ATTR, "synchronization base DN",
                            true, false, false);
    DNConfigAttribute baseDn =
      (DNConfigAttribute) configEntry.getConfigAttribute(baseDnStub);
    if (baseDn == null)
      baseDN = null;  // Attribute is not present : don't set a limit
    else
      baseDN = baseDn.activeValue();
    configAttributes.add(baseDn);

    state = new ServerState(baseDN);
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

    configDn = configEntry.getDN();
    DirectoryServer.registerConfigurableComponent(this);

    monitor = new SynchronizationMonitor(this);
    DirectoryServer.registerMonitorProvider(monitor);

    // TODO : read RUV from database an make sure we don't
    // generate changeNumber smaller than ChangeNumbers in the RUV
    long startingChangeNumber = getTime();
    changeNumberGenerator = new ChangeNumberGenerator(serverId,
                                                      startingChangeNumber);
    /*
     * create the broker object used to publish and receive changes
     */
    try
    {
      broker = new ChangelogBroker(this);
      synchronized (broker)
      {
        broker.start(serverId, changelogServers);
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
        broker.start(serverId, changelogServers);
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
            ListenerThread myThread = new ListenerThread(this,
                                                         changeNumberGenerator);
            myThread.start();
            synchroThreads.add(myThread);
          }
        }
        else
        {
          /* was enabled and moved to disabled */
          broker.suspendReceive();
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
   * Handle the conflict resolution.
   * Called by the core server after locking the entry and before
   * starting the actual modification.
   * @param modifyOperation the operation
   * @return code indicating is operation must proceed
   */
  public SynchronizationProviderResult handleConflictResolution(
                                                ModifyOperation modifyOperation)
  {
    //  If operation do not yet have a change number, generate it
    ChangeNumber changeNumber =
      (ChangeNumber) modifyOperation.getAttachment(SYNCHRONIZATION);
    if (changeNumber == null)
    {
      synchronized(pendingChanges)
      {
        changeNumber = changeNumberGenerator.NewChangeNumber();
        pendingChanges.put(changeNumber, new PendingChange(changeNumber,
                                                           modifyOperation,
                                                           null));
      }
      modifyOperation.setAttachment(SYNCHRONIZATION, changeNumber);
    }

    // if Operation is a synchronization operation, solve conflicts
    if (modifyOperation.isSynchronizationOperation())
    {
      Entry modifiedEntry = modifyOperation.getModifiedEntry();
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
        /*
         * TODO : check that post operation do get called and
         * that pendingChanges do get updated
         */
        return new SynchronizationProviderResult(false);
      }
    }
    return new SynchronizationProviderResult(true);
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
        SynchronizationMessage msg = broker.receive();
        if (msg == null)
        {
          // The server is in the shutdown process
          return null;
        }

        update = msg.processReceive(this);
      }
      return update;
    }
  }

  /**
   * Do the necessary processing when an UpdateMessage was received.
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
   * Do the necessary processing when and AckMessage was received.
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
    numReplayedPostOpCalled++;
    UpdateMessage msg = null;
    ChangeNumber curChangeNumber =
      (ChangeNumber) op.getAttachment(SYNCHRONIZATION);

    if (op.getResultCode() != ResultCode.SUCCESS)
    {
      if (curChangeNumber != null)
      {
        /*
         * This code can be executed by multiple threads
         * Since TreeMap is not synchronized, it is mandatory to synchronize
         * it now.
         */
        synchronized (pendingChanges)
        {
          pendingChanges.remove(curChangeNumber);
        }
      }
      return;
    }

    if (!op.isSynchronizationOperation())
    {
      switch (op.getOperationType())
      {
      case MODIFY :
        msg = new ModifyMsg((ModifyOperation) op);
        break;
      case ADD:
        msg = new AddMsg((AddOperation) op);
        break;
      case DELETE :
        msg = new DeleteMsg((DeleteOperation) op);
        break;
      case MODIFY_DN :
        msg = new ModifyDNMsg((ModifyDNOperation) op);
        break;
      default :
        /*
         * This is an operation type that we do not know about
         * It should never happen
         * This code can be executed by multiple threads
         * Since TreeMap is not synchronized, it is mandatory to synchronize
         * it now.
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
      if (isAssured(op))
      {
        msg.setAssured();
      }
    }

    synchronized(pendingChanges)
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

      ChangeNumber firstChangeNumber = pendingChanges.firstKey();
      PendingChange firstChange = pendingChanges.get(firstChangeNumber);
      ChangeNumber lastCommittedChangeNumber = null;

      if (!op.isSynchronizationOperation() && msg.isAssured())
      {
        waitingAckMsgs.put(curChangeNumber, msg);
      }

      while ((firstChange != null) && firstChange.isCommitted())
      {
        if (firstChange.getOp().isSynchronizationOperation() == false)
        {
          numSentUpdates++;
          broker.publish(firstChange.getMsg());
        }

        lastCommittedChangeNumber = firstChange.getChangeNumber();

        pendingChanges.remove(lastCommittedChangeNumber);
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
      if (lastCommittedChangeNumber != null)
        state.update(lastCommittedChangeNumber);
    }

    if (!op.isSynchronizationOperation() && msg.isAssured())
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
   * Check if an operation must be processed as an assured operation.
   *
   * @param op the operation to be checked.
   * @return true if the operations must be processed as an assured operation.
   */
  private boolean isAssured(Operation op)
  {
    // TODO : should have a filtering mechanism for checking
    // operation that are assured and operations that are not.
    return assuredFlag;
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
    synchronized (pendingChanges)
    {
      return pendingChanges.size();
    }
  }

  /**
   * Increment the number of processed updates.
   */
  public void incProcessedUpdates()
  {
    synchronized (this)
    {
      numProcessedUpdates++;
    }
  }

  /**
   * get the number of updates replayed by the synchronization.
   *
   * @return The number of updates replayed by the synchronization
   */
  public int getNumProcessedUpdates()
  {
    return numProcessedUpdates;
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
   * Generate and set the ChangeNumber of a given Operation.
   *
   * @param operation The Operation for which the ChangeNumber must be set.
   */
  public void setChangeNumber(Operation operation)
  {
    ChangeNumber changeNumber =
      (ChangeNumber) operation.getAttachment(SYNCHRONIZATION);
    if (changeNumber == null)
    {
      synchronized(pendingChanges)
      {
        changeNumber = changeNumberGenerator.NewChangeNumber();
        pendingChanges.put(changeNumber, new PendingChange(changeNumber,
            operation, null));
      }
      operation.setAttachment(SYNCHRONIZATION, changeNumber);
    }
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
    synchroThreads = new ArrayList<ListenerThread>();
    for (int i=0; i<10; i++)
    {
      ListenerThread myThread = new ListenerThread(this, changeNumberGenerator);
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
   * Get the largest ChangeNumber that has been processed locally.
   *
   * @return The largest ChangeNumber that has been processed locally.
   */
  public ChangeNumber getMaxChangeNumber()
  {
    return state.getMaxChangeNumber(serverId);
  }

  /**
   * Create a new serverStartMessage suitable for this SynchronizationDomain.
   *
   * @return A new serverStartMessage suitable for this SynchronizationDomain.
   */
  public ServerStartMessage newServerStartMessage()
  {
    return new ServerStartMessage(serverId, baseDN, maxReceiveDelay,
                                  maxReceiveQueue, maxSendDelay, maxSendQueue,
                                  state);
  }

  /**
   * This methods is called when an error happends while replaying
   * and operation.
   * It is necessary because the postOPeration does not always get
   * called when error or Exceptions happen during the operation replay.
   *
   * @param changeNumber the ChangeNumber of the operation with error.
   */
  public void updateError(ChangeNumber changeNumber)
  {
    synchronized (pendingChanges)
    {
      pendingChanges.remove(changeNumber);
    }
  }
}
