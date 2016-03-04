/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.replication.plugin.ReplicationRepairRequestControl.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.server.config.server.ReplicationDomainCfg;
import org.forgerock.opendj.server.config.server.ReplicationSynchronizationProviderCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.BackupTaskListener;
import org.opends.server.api.ExportTaskListener;
import org.opends.server.api.ImportTaskListener;
import org.opends.server.api.RestoreTaskListener;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.core.DirectoryServer;
import org.opends.server.replication.service.DSRSShutdownSync;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.Operation;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.SynchronizationProviderResult;
import org.opends.server.types.operation.PluginOperation;
import org.opends.server.types.operation.PostOperationAddOperation;
import org.opends.server.types.operation.PostOperationDeleteOperation;
import org.opends.server.types.operation.PostOperationModifyDNOperation;
import org.opends.server.types.operation.PostOperationModifyOperation;
import org.opends.server.types.operation.PostOperationOperation;
import org.opends.server.types.operation.PreOperationAddOperation;
import org.opends.server.types.operation.PreOperationDeleteOperation;
import org.opends.server.types.operation.PreOperationModifyDNOperation;
import org.opends.server.types.operation.PreOperationModifyOperation;
import org.opends.server.util.Platform;

/**
 * This class is used to load the Replication code inside the JVM
 * and to trigger initialization of the replication.
 *
 * It also extends the SynchronizationProvider class in order to have some
 * replication code running during the operation process
 * as pre-op, conflictResolution, and post-op.
 */
public class MultimasterReplication
       extends SynchronizationProvider<ReplicationSynchronizationProviderCfg>
       implements ConfigurationAddListener<ReplicationDomainCfg>,
                  ConfigurationDeleteListener<ReplicationDomainCfg>,
                  ConfigurationChangeListener
                  <ReplicationSynchronizationProviderCfg>,
                  BackupTaskListener, RestoreTaskListener, ImportTaskListener,
                  ExportTaskListener
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private ReplicationServerListener replicationServerListener;
  private static final Map<DN, LDAPReplicationDomain> domains = new ConcurrentHashMap<>(4);
  private static final DSRSShutdownSync dsrsShutdownSync = new DSRSShutdownSync();
  /** The queue of received update messages, to be treated by the ReplayThread threads. */
  private static final BlockingQueue<UpdateToReplay> updateToReplayQueue = new LinkedBlockingQueue<>(10000);
  /** The list of ReplayThread threads. */
  private static final List<ReplayThread> replayThreads = new ArrayList<>();
  /** The configurable number of replay threads. */
  private static int replayThreadNumber = 10;

  /** Enum that symbolizes the state of the multimaster replication. */
  private enum State
  {
    STARTING, RUNNING, STOPPING
  }

  private static final AtomicReference<State> state = new AtomicReference<>(State.STARTING);

  /** The configurable connection/handshake timeout. */
  private static volatile int connectionTimeoutMS = 5000;

  /**
   * Finds the domain for a given DN.
   *
   * @param dn         The DN for which the domain must be returned.
   * @param pluginOp   An optional operation for which the check is done.
   *                   Can be null is the request has no associated operation.
   * @return           The domain for this DN.
   */
  public static LDAPReplicationDomain findDomain(DN dn, PluginOperation pluginOp)
  {
    /*
     * Don't run the special replication code on Operation that are
     * specifically marked as don't synchronize.
     */
    if (pluginOp instanceof Operation)
    {
        final Operation op = (Operation) pluginOp;
        if (op.dontSynchronize())
        {
          return null;
        }

        /*
         * Check if the provided operation is a repair operation and set the
         * synchronization flags if necessary.
         * The repair operations are tagged as synchronization operations so
         * that the core server let the operation modify the entryuuid and
         * ds-sync-hist attributes.
         * They are also tagged as dontSynchronize so that the replication code
         * running later do not generate CSN, solve conflicts and forward the
         * operation to the replication server.
         */
        for (Iterator<Control> it = op.getRequestControls().iterator(); it.hasNext();)
        {
          Control c = it.next();
          if (OID_REPLICATION_REPAIR_CONTROL.equals(c.getOID()))
          {
            op.setSynchronizationOperation(true);
            op.setDontSynchronize(true);
            /*
            remove this control from the list of controls since it has now been
            processed and the local backend will fail if it finds a control that
            it does not know about and that is marked as critical.
            */
            it.remove();
            return null;
          }
        }
    }

    LDAPReplicationDomain domain = null;
    DN temp = dn;
    while (domain == null && temp != null)
    {
      domain = domains.get(temp);
      temp = DirectoryServer.getParentDNInSuffix(temp);
    }

    return domain;
  }

  /**
   * Creates a new domain from its configEntry, do the
   * necessary initialization and starts it so that it is
   * fully operational when this method returns.
   * @param configuration The entry with the configuration of this domain.
   * @return The domain created.
   * @throws ConfigException When the configuration is not valid.
   */
  public static LDAPReplicationDomain createNewDomain(
      ReplicationDomainCfg configuration)
      throws ConfigException
  {
    try
    {
      final LDAPReplicationDomain domain = new LDAPReplicationDomain(
          configuration, updateToReplayQueue, dsrsShutdownSync);
      if (domains.isEmpty())
      {
        // Create the threads that will process incoming update messages
        createReplayThreads();
      }

      domains.put(domain.getBaseDN(), domain);
      return domain;
    }
    catch (ConfigException e)
    {
      logger.error(ERR_COULD_NOT_START_REPLICATION, configuration.dn(),
          e.getLocalizedMessage() + " " + stackTraceToSingleLineString(e));
    }
    return null;
  }

  /**
   * Creates a new domain from its configEntry, do the necessary initialization
   * and starts it so that it is fully operational when this method returns. It
   * is only used for tests so far.
   *
   * @param configuration The entry with the configuration of this domain.
   * @param queue         The BlockingQueue that this domain will use.
   *
   * @return              The domain created.
   *
   * @throws ConfigException When the configuration is not valid.
   */
  static LDAPReplicationDomain createNewDomain(
      ReplicationDomainCfg configuration,
      BlockingQueue<UpdateToReplay> queue)
      throws ConfigException
  {
    final LDAPReplicationDomain domain =
        new LDAPReplicationDomain(configuration, queue, dsrsShutdownSync);
    domains.put(domain.getBaseDN(), domain);
    return domain;
  }

  /**
   * Deletes a domain.
   * @param dn : the base DN of the domain to delete.
   */
  public static void deleteDomain(DN dn)
  {
    LDAPReplicationDomain domain = domains.remove(dn);
    if (domain != null)
    {
      domain.delete();
    }

    // No replay threads running if no replication need
    if (domains.isEmpty()) {
      stopReplayThreads();
    }
  }

  @Override
  public void initializeSynchronizationProvider(
      ReplicationSynchronizationProviderCfg cfg) throws ConfigException
  {
    domains.clear();
    replicationServerListener = new ReplicationServerListener(cfg, dsrsShutdownSync);

    // Register as an add and delete listener with the root configuration so we
    // can be notified if Multimaster domain entries are added or removed.
    cfg.addReplicationDomainAddListener(this);
    cfg.addReplicationDomainDeleteListener(this);

    // Register as a root configuration listener so that we can be notified if
    // number of replay threads is changed and apply changes.
    cfg.addReplicationChangeListener(this);

    replayThreadNumber = getNumberOfReplayThreadsOrDefault(cfg);
    connectionTimeoutMS = (int) Math.min(cfg.getConnectionTimeout(), Integer.MAX_VALUE);

    //  Create the list of domains that are already defined.
    for (String name : cfg.listReplicationDomains())
    {
      createNewDomain(cfg.getReplicationDomain(name));
    }

    // If any schema changes were made with the server offline, then handle them now.
    List<Modification> offlineSchemaChanges =
         DirectoryServer.getOfflineSchemaChanges();
    if (offlineSchemaChanges != null && !offlineSchemaChanges.isEmpty())
    {
      processSchemaChange(offlineSchemaChanges);
    }

    DirectoryServer.registerBackupTaskListener(this);
    DirectoryServer.registerRestoreTaskListener(this);
    DirectoryServer.registerExportTaskListener(this);
    DirectoryServer.registerImportTaskListener(this);

    DirectoryServer.registerSupportedControl(
        ReplicationRepairRequestControl.OID_REPLICATION_REPAIR_CONTROL);
  }

  private int getNumberOfReplayThreadsOrDefault(ReplicationSynchronizationProviderCfg cfg)
  {
    Integer value = cfg.getNumUpdateReplayThreads();
    return value == null ? Platform.computeNumberOfThreads(16, 2.0f) : value;
  }

  /** Create the threads that will wait for incoming update messages. */
  private static synchronized void createReplayThreads()
  {
    replayThreads.clear();

    ReentrantLock switchQueueLock = new ReentrantLock();
    for (int i = 0; i < replayThreadNumber; i++)
    {
      ReplayThread replayThread = new ReplayThread(updateToReplayQueue, switchQueueLock);
      replayThread.start();
      replayThreads.add(replayThread);
    }
  }

  /** Stop the threads that are waiting for incoming update messages. */
  private static synchronized void stopReplayThreads()
  {
    //  stop the replay threads
    for (ReplayThread replayThread : replayThreads)
    {
      replayThread.shutdown();
    }

    for (ReplayThread replayThread : replayThreads)
    {
      try
      {
        replayThread.join();
      }
      catch(InterruptedException e)
      {
        Thread.currentThread().interrupt();
      }
    }
    replayThreads.clear();
  }

  @Override
  public boolean isConfigurationAddAcceptable(
      ReplicationDomainCfg configuration, List<LocalizableMessage> unacceptableReasons)
  {
    return LDAPReplicationDomain.isConfigurationAcceptable(
      configuration, unacceptableReasons);
  }

  @Override
  public ConfigChangeResult applyConfigurationAdd(
     ReplicationDomainCfg configuration)
  {
    ConfigChangeResult ccr = new ConfigChangeResult();
    try
    {
      LDAPReplicationDomain rd = createNewDomain(configuration);
      if (State.RUNNING.equals(state.get()))
      {
        rd.start();
        if (State.STOPPING.equals(state.get())) {
          rd.shutdown();
        }
      }
    } catch (ConfigException e)
    {
      // we should never get to this point because the configEntry has
      // already been validated in isConfigurationAddAcceptable()
      ccr.setResultCode(ResultCode.CONSTRAINT_VIOLATION);
    }
    return ccr;
  }

  @Override
  public void doPostOperation(PostOperationAddOperation addOperation)
  {
    DN dn = addOperation.getEntryDN();
    genericPostOperation(addOperation, dn);
  }

  @Override
  public void doPostOperation(PostOperationDeleteOperation deleteOperation)
  {
    DN dn = deleteOperation.getEntryDN();
    genericPostOperation(deleteOperation, dn);
  }

  @Override
  public void doPostOperation(PostOperationModifyDNOperation modifyDNOperation)
  {
    DN dn = modifyDNOperation.getEntryDN();
    genericPostOperation(modifyDNOperation, dn);
  }

  @Override
  public void doPostOperation(PostOperationModifyOperation modifyOperation)
  {
    DN dn = modifyOperation.getEntryDN();
    genericPostOperation(modifyOperation, dn);
  }

  @Override
  public SynchronizationProviderResult handleConflictResolution(
      PreOperationModifyOperation modifyOperation)
  {
    LDAPReplicationDomain domain = findDomain(modifyOperation.getEntryDN(), modifyOperation);
    if (domain != null)
    {
      return domain.handleConflictResolution(modifyOperation);
    }
    return new SynchronizationProviderResult.ContinueProcessing();
  }

  @Override
  public SynchronizationProviderResult handleConflictResolution(
      PreOperationAddOperation addOperation) throws DirectoryException
  {
    LDAPReplicationDomain domain = findDomain(addOperation.getEntryDN(), addOperation);
    if (domain != null)
    {
      return domain.handleConflictResolution(addOperation);
    }
    return new SynchronizationProviderResult.ContinueProcessing();
  }

  @Override
  public SynchronizationProviderResult handleConflictResolution(
      PreOperationDeleteOperation deleteOperation) throws DirectoryException
  {
    LDAPReplicationDomain domain = findDomain(deleteOperation.getEntryDN(), deleteOperation);
    if (domain != null)
    {
      return domain.handleConflictResolution(deleteOperation);
    }
    return new SynchronizationProviderResult.ContinueProcessing();
  }

  @Override
  public SynchronizationProviderResult handleConflictResolution(
      PreOperationModifyDNOperation modifyDNOperation) throws DirectoryException
  {
    LDAPReplicationDomain domain = findDomain(modifyDNOperation.getEntryDN(), modifyDNOperation);
    if (domain != null)
    {
      return domain.handleConflictResolution(modifyDNOperation);
    }
    return new SynchronizationProviderResult.ContinueProcessing();
  }

  @Override
  public SynchronizationProviderResult
         doPreOperation(PreOperationModifyOperation modifyOperation)
  {
    DN operationDN = modifyOperation.getEntryDN();
    LDAPReplicationDomain domain = findDomain(operationDN, modifyOperation);

    if (domain == null || !domain.solveConflict())
    {
      return new SynchronizationProviderResult.ContinueProcessing();
    }

    EntryHistorical historicalInformation = (EntryHistorical)
      modifyOperation.getAttachment(EntryHistorical.HISTORICAL);
    if (historicalInformation == null)
    {
      Entry entry = modifyOperation.getModifiedEntry();
      historicalInformation = EntryHistorical.newInstanceFromEntry(entry);
      modifyOperation.setAttachment(EntryHistorical.HISTORICAL,
          historicalInformation);
    }
    historicalInformation.setPurgeDelay(domain.getHistoricalPurgeDelay());
    historicalInformation.setHistoricalAttrToOperation(modifyOperation);

    if (modifyOperation.getModifications().isEmpty())
    {
      /*
       * This operation becomes a no-op due to conflict resolution
       * stop the processing and send an OK result
       */
      return new SynchronizationProviderResult.StopProcessing(
          ResultCode.SUCCESS, null);
    }

    return new SynchronizationProviderResult.ContinueProcessing();
  }

  @Override
  public SynchronizationProviderResult doPreOperation(
         PreOperationDeleteOperation deleteOperation) throws DirectoryException
  {
    return new SynchronizationProviderResult.ContinueProcessing();
  }

  @Override
  public SynchronizationProviderResult doPreOperation(
         PreOperationModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    DN operationDN = modifyDNOperation.getEntryDN();
    LDAPReplicationDomain domain = findDomain(operationDN, modifyDNOperation);

    if (domain == null || !domain.solveConflict())
    {
      return new SynchronizationProviderResult.ContinueProcessing();
    }

    // The historical object is retrieved from the attachment created
    // in the HandleConflictResolution phase.
    EntryHistorical historicalInformation = (EntryHistorical)
    modifyDNOperation.getAttachment(EntryHistorical.HISTORICAL);
    if (historicalInformation == null)
    {
      // When no Historical attached, create once by loading from the entry
      // and attach it to the operation
      Entry entry = modifyDNOperation.getUpdatedEntry();
      historicalInformation = EntryHistorical.newInstanceFromEntry(entry);
      modifyDNOperation.setAttachment(EntryHistorical.HISTORICAL,
          historicalInformation);
    }
    historicalInformation.setPurgeDelay(domain.getHistoricalPurgeDelay());

    // Add to the operation the historical attribute : "dn:changeNumber:moddn"
    historicalInformation.setHistoricalAttrToOperation(modifyDNOperation);

    return new SynchronizationProviderResult.ContinueProcessing();
  }

  @Override
  public SynchronizationProviderResult doPreOperation(
         PreOperationAddOperation addOperation)
  {
    // Check replication domain
    LDAPReplicationDomain domain =
      findDomain(addOperation.getEntryDN(), addOperation);
    if (domain == null)
    {
      return new SynchronizationProviderResult.ContinueProcessing();
    }

    // For LOCAL op only, generate CSN and attach Context
    if (!addOperation.isSynchronizationOperation())
    {
      domain.doPreOperation(addOperation);
    }

    // Add to the operation the historical attribute : "dn:changeNumber:add"
    EntryHistorical.setHistoricalAttrToOperation(addOperation);

    return new SynchronizationProviderResult.ContinueProcessing();
  }

  @Override
  public void finalizeSynchronizationProvider()
  {
    setState(State.STOPPING);

    for (LDAPReplicationDomain domain : domains.values())
    {
      domain.shutdown();
    }
    domains.clear();

    stopReplayThreads();

    if (replicationServerListener != null)
    {
      replicationServerListener.shutdown();
    }

    DirectoryServer.deregisterBackupTaskListener(this);
    DirectoryServer.deregisterRestoreTaskListener(this);
    DirectoryServer.deregisterExportTaskListener(this);
    DirectoryServer.deregisterImportTaskListener(this);
  }

  /**
   * This method is called whenever the server detects a modification
   * of the schema done by directly modifying the backing files
   * of the schema backend.
   * Call the schema Domain if it exists.
   *
   * @param  modifications  The list of modifications that was
   *                                      applied to the schema.
   */
  @Override
  public void processSchemaChange(List<Modification> modifications)
  {
    LDAPReplicationDomain domain = findDomain(DirectoryServer.getSchemaDN(), null);
    if (domain != null)
    {
      domain.synchronizeSchemaModifications(modifications);
    }
  }

  @Override
  public void processBackupBegin(Backend<?> backend, BackupConfig config)
  {
    for (DN dn : backend.getBaseDNs())
    {
      LDAPReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
      {
        domain.backupStart();
      }
    }
  }

  @Override
  public void processBackupEnd(Backend<?> backend, BackupConfig config, boolean successful)
  {
    for (DN dn : backend.getBaseDNs())
    {
      LDAPReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
      {
        domain.backupEnd();
      }
    }
  }

  @Override
  public void processRestoreBegin(Backend<?> backend, RestoreConfig config)
  {
    for (DN dn : backend.getBaseDNs())
    {
      LDAPReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
      {
        domain.disable();
      }
    }
  }

  @Override
  public void processRestoreEnd(Backend<?> backend, RestoreConfig config, boolean successful)
  {
    for (DN dn : backend.getBaseDNs())
    {
      LDAPReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
      {
        domain.enable();
      }
    }
  }

  @Override
  public void processImportBegin(Backend<?> backend, LDIFImportConfig config)
  {
    for (DN dn : backend.getBaseDNs())
    {
      LDAPReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
      {
        domain.disable();
      }
    }
  }

  @Override
  public void processImportEnd(Backend<?> backend, LDIFImportConfig config, boolean successful)
  {
    for (DN dn : backend.getBaseDNs())
    {
      LDAPReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
      {
        domain.enable();
      }
    }
  }

  @Override
  public void processExportBegin(Backend<?> backend, LDIFExportConfig config)
  {
    for (DN dn : backend.getBaseDNs())
    {
      LDAPReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
      {
        domain.backupStart();
      }
    }
  }

  @Override
  public void processExportEnd(Backend<?> backend, LDIFExportConfig config, boolean successful)
  {
    for (DN dn : backend.getBaseDNs())
    {
      LDAPReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
      {
        domain.backupEnd();
      }
    }
  }

  @Override
  public ConfigChangeResult applyConfigurationDelete(
      ReplicationDomainCfg configuration)
  {
    deleteDomain(configuration.getBaseDN());

    return new ConfigChangeResult();
  }

  @Override
  public boolean isConfigurationDeleteAcceptable(
      ReplicationDomainCfg configuration, List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  /**
   * Generic code for all the postOperation entry point.
   *
   * @param operation The Operation for which the post-operation is called.
   * @param dn The Dn for which the post-operation is called.
   */
  private void genericPostOperation(PostOperationOperation operation, DN dn)
  {
    LDAPReplicationDomain domain = findDomain(dn, operation);
    if (domain != null) {
      domain.synchronize(operation);
    }
  }

  /**
   * Returns the replication server listener associated to that Multimaster
   * Replication.
   * @return the listener.
   */
  public ReplicationServerListener getReplicationServerListener()
  {
    return replicationServerListener;
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      ReplicationSynchronizationProviderCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(ReplicationSynchronizationProviderCfg configuration)
  {
    // Stop threads then restart new number of threads
    stopReplayThreads();
    replayThreadNumber = getNumberOfReplayThreadsOrDefault(configuration);
    if (!domains.isEmpty())
    {
      createReplayThreads();
    }

    connectionTimeoutMS = (int) Math.min(configuration.getConnectionTimeout(),
        Integer.MAX_VALUE);

    return new ConfigChangeResult();
  }

  @Override
  public void completeSynchronizationProvider()
  {
    for (LDAPReplicationDomain domain : domains.values())
    {
      domain.start();
    }
    setState(State.RUNNING);
  }

  private void setState(State newState)
  {
    state.set(newState);
    synchronized (state)
    {
      state.notifyAll();
    }
  }

  /**
   * Gets the number of handled domain objects.
   * @return The number of handled domain objects
   */
  public static int getNumberOfDomains()
  {
    return domains.size();
  }

  /**
   * Gets the Set of domain baseDN which are disabled for the external changelog.
   *
   * @return The Set of domain baseDNs which are disabled for the external changelog.
   * @throws DirectoryException
   *            if a problem occurs
   */
  public static Set<DN> getExcludedChangelogDomains() throws DirectoryException
  {
    final Set<DN> disabledBaseDNs = new HashSet<>(domains.size() + 1);
    disabledBaseDNs.add(DN.valueOf(DN_EXTERNAL_CHANGELOG_ROOT));
    for (LDAPReplicationDomain domain : domains.values())
    {
      if (!domain.isECLEnabled())
      {
        disabledBaseDNs.add(domain.getBaseDN());
      }
    }
    return disabledBaseDNs;
  }

  /**
   * Returns whether the provided baseDN represents a replication domain enabled
   * for the external changelog.
   *
   * @param baseDN
   *          the replication domain to check
   * @return true if the provided baseDN is enabled for the external changelog,
   *         false if the provided baseDN is disabled for the external changelog
   *         or unknown to multimaster replication.
   */
  public static boolean isECLEnabledDomain(DN baseDN)
  {
    waitForStartup();
    // if state is STOPPING, then we need to return from this method
    final LDAPReplicationDomain domain = domains.get(baseDN);
    return domain != null && domain.isECLEnabled();
  }

  /**
   * Returns whether the external change-log contains data from at least a domain.
   * @return whether the external change-log contains data from at least a domain
   */
  public static boolean isECLEnabled()
  {
    waitForStartup();
    for (LDAPReplicationDomain domain : domains.values())
    {
      if (domain.isECLEnabled())
      {
        return true;
      }
    }
    return false;
  }

  private static void waitForStartup()
  {
    if (State.STARTING.equals(state.get()))
    {
      synchronized (state)
      {
        while (State.STARTING.equals(state.get()))
        {
          try
          {
            state.wait();
          }
          catch (InterruptedException ignored)
          {
            // loop and check state again
          }
        }
      }
    }
  }

  /**
   * Returns the connection timeout in milli-seconds.
   *
   * @return The connection timeout in milli-seconds.
   */
  public static int getConnectionTimeoutMS()
  {
    return connectionTimeoutMS;
  }
}
