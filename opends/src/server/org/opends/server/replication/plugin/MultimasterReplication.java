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

import java.util.ArrayList;
import static org.opends.server.replication.plugin.
ReplicationRepairRequestControl.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.server.ReplicationDomainCfg;
import org.opends.server.admin.std.server.ReplicationSynchronizationProviderCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.BackupTaskListener;
import org.opends.server.api.ExportTaskListener;
import org.opends.server.api.ImportTaskListener;
import org.opends.server.api.RestoreTaskListener;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.Modification;
import org.opends.server.types.Operation;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.ResultCode;
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

/**
 * This class is used to load the Replication code inside the JVM
 * and to trigger initialization of the replication.
 *
 * It also extends the SynchronizationProvider class in order to have some
 * replication code running during the operation process
 * as pre-op, conflictRsolution, and post-op.
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
  private static Map<DN, ReplicationDomain> domains =
    new HashMap<DN, ReplicationDomain>() ;

  /**
   * The queue of received update messages, to be treated by the ReplayThread
   * threads.
   */
  private static LinkedBlockingQueue<UpdateToReplay> updateToReplayQueue =
    new LinkedBlockingQueue<UpdateToReplay>();

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
   * Finds the domain for a given DN.
   *
   * @param dn         The DN for which the domain must be returned.
   * @param pluginOp   An optional operation for which the check is done.
   *                   Can be null is the request has no associated operation.
   * @return           The domain for this DN.
   */
  public static ReplicationDomain findDomain(DN dn, PluginOperation pluginOp)
  {
    /*
     * Don't run the special replication code on Operation that are
     * specifically marked as don't synchronize.
     */
    if ((pluginOp != null) && (pluginOp instanceof Operation))
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
         * code running later do not generate ChnageNumber, solve conflicts
         * and forward the operation to the replication server.
         */
        for (Control c : op.getRequestControls())
        {
          if (c.getOID().equals(OID_REPLICATION_REPAIR_CONTROL))
          {
            op.setSynchronizationOperation(true);
            op.setDontSynchronize(true);
            // remove this control from the list of controls since
            // it has now been processed and the local backend will
            // fail if it finds a control that it does not know about and
            // that is marked as critical.
            List<Control> controls = op.getRequestControls();
            controls.remove(c);
            return null;
          }
        }
    }


    ReplicationDomain domain = null;
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
   * @param configuration The entry whith the configuration of this domain.
   * @return The domain created.
   * @throws ConfigException When the configuration is not valid.
   */
  public static ReplicationDomain createNewDomain(
      ReplicationDomainCfg configuration)
      throws ConfigException
  {
    ReplicationDomain domain;
    domain = new ReplicationDomain(configuration, updateToReplayQueue);

    if (domains.size() == 0)
    {
      /*
       * Create the threads that will process incoming update messages
       */
      createReplayThreads();
    }

    domains.put(domain.getBaseDN(), domain);
    return domain;
  }

  /**
   * Deletes a domain.
   * @param dn : the base DN of the domain to delete.
   */
  public static void deleteDomain(DN dn)
  {
    ReplicationDomain domain = domains.remove(dn);

    if (domain != null)
      domain.shutdown();

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
   * Stope the threads that are waiting for incoming update messages.
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
      replayThread.waitForShutdown();
    }
    replayThreads.clear();
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
      ReplicationDomainCfg configuration, List<Message> unacceptableReasons)
  {
    return ReplicationDomain.isConfigurationAcceptable(
      configuration, unacceptableReasons);
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
     ReplicationDomainCfg configuration)
  {
    try
    {
      ReplicationDomain rd = createNewDomain(configuration);
      if (isRegistered)
      {
        rd.start();
      }
      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    } catch (ConfigException e)
    {
      // we should never get to this point because the configEntry has
      // already been validated in configAddisAcceptable
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
    ReplicationDomain domain =
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
    ReplicationDomain domain =
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
    ReplicationDomain domain =
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
    ReplicationDomain domain =
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
    ReplicationDomain domain = findDomain(operationDN, modifyOperation);

    if ((domain == null) || (!domain.solveConflict()))
      return new SynchronizationProviderResult.ContinueProcessing();

    Historical historicalInformation = (Historical)
                            modifyOperation.getAttachment(
                                    Historical.HISTORICAL);
    if (historicalInformation == null)
    {
      Entry entry = modifyOperation.getModifiedEntry();
      historicalInformation = Historical.load(entry);
      modifyOperation.setAttachment(Historical.HISTORICAL,
              historicalInformation);
    }

    historicalInformation.generateState(modifyOperation);

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
    return new SynchronizationProviderResult.ContinueProcessing();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SynchronizationProviderResult doPreOperation(
         PreOperationAddOperation addOperation)
  {
    ReplicationDomain domain =
      findDomain(addOperation.getEntryDN(), addOperation);
    if (domain == null)
      return new SynchronizationProviderResult.ContinueProcessing();

    if (!addOperation.isSynchronizationOperation())
      domain.doPreOperation(addOperation);

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
    for (ReplicationDomain domain : domains.values())
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
    ReplicationDomain domain =
      findDomain(DirectoryServer.getSchemaDN(), null);
    if (domain != null)
      domain.synchronizeModifications(modifications);
  }

  /**
   * {@inheritDoc}
   */
  public void processBackupBegin(Backend backend, BackupConfig config)
  {
    for (DN dn : backend.getBaseDNs())
    {
      ReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
        domain.backupStart();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void processBackupEnd(Backend backend, BackupConfig config,
                               boolean successful)
  {
    for (DN dn : backend.getBaseDNs())
    {
      ReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
        domain.backupEnd();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void processRestoreBegin(Backend backend, RestoreConfig config)
  {
    for (DN dn : backend.getBaseDNs())
    {
      ReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
        domain.disable();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void processRestoreEnd(Backend backend, RestoreConfig config,
                                boolean successful)
  {
    for (DN dn : backend.getBaseDNs())
    {
      ReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
        domain.enable();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void processImportBegin(Backend backend, LDIFImportConfig config)
  {
    for (DN dn : backend.getBaseDNs())
    {
      ReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
        domain.disable();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void processImportEnd(Backend backend, LDIFImportConfig config,
                               boolean successful)
  {
    for (DN dn : backend.getBaseDNs())
    {
      ReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
        domain.enable();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void processExportBegin(Backend backend, LDIFExportConfig config)
  {
    for (DN dn : backend.getBaseDNs())
    {
      ReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
        domain.backupStart();
    }
  }

  /**
   * {@inheritDoc}
   */
  public void processExportEnd(Backend backend, LDIFExportConfig config,
                               boolean successful)
  {
    for (DN dn : backend.getBaseDNs())
    {
      ReplicationDomain domain = findDomain(dn, null);
      if (domain != null)
        domain.backupEnd();
    }
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationDelete(
      ReplicationDomainCfg configuration)
  {
    deleteDomain(configuration.getBaseDN());

    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }

  /**
   * {@inheritDoc}
   */
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
    ReplicationDomain domain = findDomain(dn, operation);
    if (domain == null)
      return;

    domain.synchronize(operation);

    return;
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
  public boolean
    isConfigurationChangeAcceptable(ReplicationSynchronizationProviderCfg
    configuration,
    List<Message> unacceptableReasons)
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult
    applyConfigurationChange
    (ReplicationSynchronizationProviderCfg configuration)
  {
    int numUpdateRepayThread = configuration.getNumUpdateReplayThreads();

    // Stop threads then restart new number of threads
    stopReplayThreads();
    replayThreadNumber = numUpdateRepayThread;
    if (domains.size() > 0)
    {
      createReplayThreads();
    }

    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }

  /**
   * {@inheritDoc}
   */
  public void completeSynchronizationProvider()
  {
    isRegistered = true;

    // start all the domains
    for (ReplicationDomain domain : domains.values())
    {
      domain.start();
    }
  }
}
