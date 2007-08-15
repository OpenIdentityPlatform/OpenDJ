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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.server.MultimasterDomainCfg;
import org.opends.server.admin.std.server.MultimasterSynchronizationProviderCfg;
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
import org.opends.messages.Message;


/**
 * This class is used to load the Replication code inside the JVM
 * and to trigger initialization of the replication.
 *
 * It also extends the SynchronizationProvider class in order to have some
 * replication code running during the operation process
 * as pre-op, conflictRsolution, and post-op.
 */
public class MultimasterReplication
       extends SynchronizationProvider<MultimasterSynchronizationProviderCfg>
       implements ConfigurationAddListener<MultimasterDomainCfg>,
                  ConfigurationDeleteListener<MultimasterDomainCfg>,
                  BackupTaskListener, RestoreTaskListener, ImportTaskListener,
                  ExportTaskListener
{
  private ReplicationServerListener replicationServer = null;
  private static Map<DN, ReplicationDomain> domains =
    new HashMap<DN, ReplicationDomain>() ;


  /**
   * Finds the domain for a given DN.
   *
   * @param dn   The DN for which the domain must be returned.
   * @param op   An optional operation for which the check is done.
   *             Can be null is the request has no associated operation.
   * @return     The domain for this DN.
   */
  public static ReplicationDomain findDomain(DN dn, PluginOperation op)
  {
    /*
     * Don't run the special replication code on Operation that are
     * specifically marked as don't synchronize.
     */
    if ((op != null) && (op instanceof Operation) &&
        (((Operation) op).dontSynchronize()))
      return null;

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
      MultimasterDomainCfg configuration)
      throws ConfigException
  {
    ReplicationDomain domain;
    domain = new ReplicationDomain(configuration);
    domains.put(domain.getBaseDN(), domain);
    domain.start();
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
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeSynchronizationProvider(
      MultimasterSynchronizationProviderCfg configuration)
  throws ConfigException
  {
    replicationServer = new ReplicationServerListener(configuration);

    // Register as an add and delete listener with the root configuration so we
    // can be notified if Multimaster domain entries are added or removed.
    configuration.addMultimasterDomainAddListener(this);
    configuration.addMultimasterDomainDeleteListener(this);

    //  Create the list of domains that are already defined.
    for (String name : configuration.listMultimasterDomains())
    {
      MultimasterDomainCfg domain = configuration.getMultimasterDomain(name);
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
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationAddAcceptable(
      MultimasterDomainCfg configuration, List<Message> unacceptableReasons)
  {
    return ReplicationDomain.isConfigurationAcceptable(
      configuration, unacceptableReasons);
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationAdd(
     MultimasterDomainCfg configuration)
  {
    try
    {
      createNewDomain(configuration);
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
      return new SynchronizationProviderResult(true);

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
      return new SynchronizationProviderResult(true);

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
      return new SynchronizationProviderResult(true);

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
      return new SynchronizationProviderResult(true);

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
      return new SynchronizationProviderResult(true);

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

    return new SynchronizationProviderResult(true);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SynchronizationProviderResult doPreOperation(
         PreOperationDeleteOperation deleteOperation) throws DirectoryException
  {
    return new SynchronizationProviderResult(true);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SynchronizationProviderResult doPreOperation(
         PreOperationModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    return new SynchronizationProviderResult(true);
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
      return new SynchronizationProviderResult(true);

    if (!addOperation.isSynchronizationOperation())
      domain.doPreOperation(addOperation);

    return new SynchronizationProviderResult(true);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public void finalizeSynchronizationProvider()
  {
    // shutdown all the domains
    for (ReplicationDomain domain : domains.values())
    {
      domain.shutdown();
    }

    // shutdown the ReplicationServer Service if necessary
    if (replicationServer != null)
      replicationServer.shutdown();

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
      MultimasterDomainCfg configuration)
  {
    deleteDomain(configuration.getReplicationDN());

    return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationDeleteAcceptable(
      MultimasterDomainCfg configuration, List<Message> unacceptableReasons)
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
}


