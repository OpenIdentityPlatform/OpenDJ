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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2013 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.replication.plugin.
ReplicationRepairRequestControl.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.server.ReplicationDomainCfg;
import org.opends.server.admin.std.server.ReplicationSynchronizationProviderCfg;
import org.opends.server.api.*;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.*;
import org.opends.server.types.operation.*;

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
  private ReplicationServerListener replicationServerListener = null;
  private static Map<DN, LDAPReplicationDomain> domains =
    new ConcurrentHashMap<DN, LDAPReplicationDomain>(4) ;

  /**
   * The queue of received update messages, to be treated by the ReplayThread
   * threads.
   */
  private static final BlockingQueue<UpdateToReplay> updateToReplayQueue =
      new LinkedBlockingQueue<UpdateToReplay>(10000);

  /**
   * The list of ReplayThread threads.
   */
  private static List<ReplayThread> replayThreads =
    new ArrayList<ReplayThread>();

  /**
   * The configurable number of replay threads.
   */
  private static int replayThreadNumber = 10;

  private boolean isRegistered = false;

  /**
   * The configurable connection/handshake timeout.
   */
  private static volatile int connectionTimeoutMS = 5000;

  /**
   * Finds the domain for a given DN.
   *
   * @param dn         The DN for which the domain must be returned.
   * @param pluginOp   An optional operation for which the check is done.
   *                   Can be null is the request has no associated operation.
   * @return           The domain for this DN.
   */
  public static LDAPReplicationDomain findDomain(
      DN dn, PluginOperation pluginOp)
  {
    /*
     * Don't run the special replication code on Operation that are
     * specifically marked as don't synchronize.
     */
    if (pluginOp != null && pluginOp instanceof Operation)
    {
        Operation op = ((Operation) pluginOp);

        if (op.dontSynchronize())
          return null;

        /*
         * Check if the provided operation is a repair operation and set
         * the synchronization flags if necessary.
         * The repair operations are tagged as synchronization operations
         * so that the core server let the operation modify the entryuuid
         * and ds-sync-hist attributes.
         * They are also tagged as dontSynchronize so that the replication
         * code running later do not generate ChangeNumber, solve conflicts
         * and forward the operation to the replication server.
         */
        for (Control c : op.getRequestControls())
        {
          if (c.getOID().equals(OID_REPLICATION_REPAIR_CONTROL))
          {
            op.setSynchronizationOperation(true);
            op.setDontSynchronize(true);
            /*
            remove this control from the list of controls since
            it has now been processed and the local backend will
            fail if it finds a control that it does not know about and
            that is marked as critical.
            */
            List<Control> controls = op.getRequestControls();
            controls.remove(c);
            return null;
          }
        }
    }


    LDAPReplicationDomain domain;
    DN temp = dn;
    do
    {
      domain = domains.get(temp);
      temp = temp.getParentDNInSuffix();
      if (temp == null)
      {
        break;
      }
    } while (domain == null);

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
    LDAPReplicationDomain domain = null;
    try
    {
      domain = new LDAPReplicationDomain(configuration, updateToReplayQueue);
      if (domains.size() == 0)
      {
        /*
         * Create the threads that will process incoming update messages
         */
        createReplayThreads();
      }

      domains.put(domain.getBaseDN(), domain);
    }
    catch (ConfigException e)
    {
      logError(ERR_COULD_NOT_START_REPLICATION.get(
          configuration.dn().toString(), e.getLocalizedMessage()
          + " " + stackTraceToSingleLineString(e)));
    }
    return domain;
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
    LDAPReplicationDomain domain =
        new LDAPReplicationDomain(configuration, queue);

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
      domain.delete();

    // No replay threads running if no replication need
    if (domains.size() == 0) {
      stopReplayThreads();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeSynchronizationProvider(
      ReplicationSynchronizationProviderCfg configuration)
  throws ConfigException
  {
    domains.clear();
    replicationServerListener = new ReplicationServerListener(configuration);

    // Register as an add and delete listener with the root configuration so we
    // can be notified if Multimaster domain entries are added or removed.
    configuration.addReplicationDomainAddListener(this);
    configuration.addReplicationDomainDeleteListener(this);

    // Register as a root configuration listener so that we can be notified if
    // number of replay threads is changed and apply changes.
    configuration.addReplicationChangeListener(this);

    replayThreadNumber = configuration.getNumUpdateReplayThreads();
    connectionTimeoutMS = (int) Math.min(configuration.getConnectionTimeout(),
        Integer.MAX_VALUE);

    //  Create the list of domains that are already defined.
    for (String name : configuration.listReplicationDomains())
    {
      ReplicationDomainCfg domain = configuration.getReplicationDomain(name);
      createNewDomain(domain);
    }

    /*
     * If any schema changes were made with the server offline, then handle them
     * now.
     */
    List<Modification> offlineSchemaChanges =
         DirectoryServer.getOfflineSchemaChanges();
    if ((offlineSchemaChanges != null) && (! offlineSchemaChanges.isEmpty()))
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

  /**
   * Create the threads that will wait for incoming update messages.
   */
  private synchronized static void createReplayThreads()
  {
    replayThreads.clear();

    for (int i = 0; i < replayThreadNumber; i++)
    {
      ReplayThread replayThread = new ReplayThread(updateToReplayQueue);
      replayThread.start();
      replayThreads.add(replayThread);
    }
  }

  /**
   * Stop the threads that are waiting for incoming update messages.
   */
  private synchronized static void stopReplayThreads()
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

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationAddAcceptable(
      ReplicationDomainCfg configuration, List<Message> unacceptableReasons)
  {
    return LDAPReplicationDomain.isConfigurationAcceptable(
      configuration, unacceptableReasons);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationAdd(
     ReplicationDomainCfg configuration)
  {
    try
    {
      LDAPReplicationDomain rd = createNewDomain(configuration);
      if (isRegistered)
      {
        rd.start();
      }
      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    } catch (ConfigException e)
    {
      // we should never get to this point because the configEntry has
      // already been validated in isConfigurationAddAcceptable()
      return new ConfigChangeResult(ResultCode.CONSTRAINT_VIOLATION, false);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void doPostOperation(PostOperationAddOperation addOperation)
  {
    DN dn = addOperation.getEntryDN();
    genericPostOperation(addOperation, dn);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void doPostOperation(PostOperationDeleteOperation deleteOperation)
  {
    DN dn = deleteOperation.getEntryDN();
    genericPostOperation(deleteOperation, dn);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void doPostOperation(PostOperationModifyDNOperation modifyDNOperation)
  {
    DN dn = modifyDNOperation.getEntryDN();
    genericPostOperation(modifyDNOperation, dn);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void doPostOperation(PostOperationModifyOperation modifyOperation)
  {
    DN dn = modifyOperation.getEntryDN();
    genericPostOperation(modifyOperation, dn);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SynchronizationProviderResult handleConflictResolution(
      PreOperationModifyOperation modifyOperation)
  {
    LDAPReplicationDomain domain =
      findDomain(modifyOperation.getEntryDN(), modifyOperation);
    if (domain == null)
      return new SynchronizationProviderResult.ContinueProcessing();

    return domain.handleConflictResolution(modifyOperation);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SynchronizationProviderResult handleConflictResolution(
      PreOperationAddOperation addOperation) throws DirectoryException
  {
    LDAPReplicationDomain domain =
      findDomain(addOperation.getEntryDN(), addOperation);
    if (domain == null)
      return new SynchronizationProviderResult.ContinueProcessing();

    return domain.handleConflictResolution(addOperation);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SynchronizationProviderResult handleConflictResolution(
      PreOperationDeleteOperation deleteOperation) throws DirectoryException
  {
    LDAPReplicationDomain domain =
      findDomain(deleteOperation.getEntryDN(), deleteOperation);
    if (domain == null)
      return new SynchronizationProviderResult.ContinueProcessing();

    return domain.handleConflictResolution(deleteOperation);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SynchronizationProviderResult handleConflictResolution(
      PreOperationModifyDNOperation modifyDNOperation) throws DirectoryException
  {
    LDAPReplicationDomain domain =
      findDomain(modifyDNOperation.getEntryDN(), modifyDNOperation);
    if (domain == null)
      return new SynchronizationProviderResult.ContinueProcessing();

    return domain.handleConflictResolution(modifyDNOperation);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SynchronizationProviderResult
         doPreOperation(PreOperationModifyOperation modifyOperation)
  {
    DN operationDN = modifyOperation.getEntryDN();
    LDAPReplicationDomain domain = findDomain(operationDN, modifyOperation);

    if ((domain == null) || (!domain.solveConflict()))
      return new SynchronizationProviderResult.ContinueProcessing();

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

  /**
   * {@inheritDoc}
   */
  @Override
  public SynchronizationProviderResult doPreOperation(
         PreOperationDeleteOperation deleteOperation) throws DirectoryException
  {
    return new SynchronizationProviderResult.ContinueProcessing();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SynchronizationProviderResult doPreOperation(
         PreOperationModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    DN operationDN = modifyDNOperation.getEntryDN();
    LDAPReplicationDomain domain = findDomain(operationDN, modifyDNOperation);

    if (domain == null || !domain.solveConflict())
      return new SynchronizationProviderResult.ContinueProcessing();

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

  /**
   * {@inheritDoc}
   */
  @Override
  public SynchronizationProviderResult doPreOperation(
         PreOperationAddOperation addOperation)
  {
    // Check replication domain
    LDAPReplicationDomain domain =
      findDomain(addOperation.getEntryDN(), addOperation);
    if (domain == null)
      return new SynchronizationProviderResult.ContinueProcessing();

    // For LOCAL op only, generate ChangeNumber and attach Context
    if (!addOperation.isSynchronizationOperation())
      domain.doPreOperation(addOperation);

    // Add to the operation the historical attribute : "dn:changeNumber:add"
    EntryHistorical.setHistoricalAttrToOperation(addOperation);

    return new SynchronizationProviderResult.ContinueProcessing();
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void finalizeSynchronizationProvider()
  {
    isRegistered = false;

    // shutdown all the domains
    for (LDAPReplicationDomain domain : domains.values())
    {
      domain.shutdown();
    }
    domains.clear();

    // Stop replay threads
    stopReplayThreads();

    // shutdown the ReplicationServer Service if necessary
    if (replicationServerListener != null)
      replicationServerListener.shutdown();

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
   *
   */
  @Override
  public void processSchemaChange(List<Modification> modifications)
  {
    LDAPReplicationDomain domain =
      findDomain(DirectoryServer.getSchemaDN(), null);
    if (domain != null)
      domain.synchronizeModifications(modifications);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void processBackupBegin(Backend backend, BackupConfig config)
  {
    for (DN dn : backend.getBaseDNs())
    {
      LDAPReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
        domain.backupStart();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void processBackupEnd(Backend backend, BackupConfig config,
                               boolean successful)
  {
    for (DN dn : backend.getBaseDNs())
    {
      LDAPReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
        domain.backupEnd();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void processRestoreBegin(Backend backend, RestoreConfig config)
  {
    for (DN dn : backend.getBaseDNs())
    {
      LDAPReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
        domain.disable();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void processRestoreEnd(Backend backend, RestoreConfig config,
                                boolean successful)
  {
    for (DN dn : backend.getBaseDNs())
    {
      LDAPReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
        domain.enable();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void processImportBegin(Backend backend, LDIFImportConfig config)
  {
    for (DN dn : backend.getBaseDNs())
    {
      LDAPReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
        domain.disable();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void processImportEnd(Backend backend, LDIFImportConfig config,
                               boolean successful)
  {
    for (DN dn : backend.getBaseDNs())
    {
      LDAPReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
        domain.enable();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void processExportBegin(Backend backend, LDIFExportConfig config)
  {
    for (DN dn : backend.getBaseDNs())
    {
      LDAPReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
        domain.backupStart();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void processExportEnd(Backend backend, LDIFExportConfig config,
                               boolean successful)
  {
    for (DN dn : backend.getBaseDNs())
    {
      LDAPReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
        domain.backupEnd();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationDelete(
      ReplicationDomainCfg configuration)
  {
    deleteDomain(configuration.getBaseDN());

    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationDeleteAcceptable(
      ReplicationDomainCfg configuration, List<Message> unacceptableReasons)
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

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationChangeAcceptable(
      ReplicationSynchronizationProviderCfg configuration,
      List<Message> unacceptableReasons)
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationChange(
      ReplicationSynchronizationProviderCfg configuration)
  {
    int numUpdateRepayThread = configuration.getNumUpdateReplayThreads();

    // Stop threads then restart new number of threads
    stopReplayThreads();
    replayThreadNumber = numUpdateRepayThread;
    if (domains.size() > 0)
    {
      createReplayThreads();
    }

    connectionTimeoutMS = (int) Math.min(configuration.getConnectionTimeout(),
        Integer.MAX_VALUE);

    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void completeSynchronizationProvider()
  {
    isRegistered = true;

    // start all the domains
    for (LDAPReplicationDomain domain : domains.values())
    {
      domain.start();
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
   * Gets the baseDn of the domains that have a private backend.
   * @return The private baseDN.
   */
  public static ArrayList<String> getECLDisabledDomains()
  {
    ArrayList<String> disabledServiceIDs = new ArrayList<String>();

    for (LDAPReplicationDomain domain : domains.values())
    {
      if (!domain.isECLEnabled())
        disabledServiceIDs.add(domain.getBaseDN().toNormalizedString());
    }
    return disabledServiceIDs;
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
