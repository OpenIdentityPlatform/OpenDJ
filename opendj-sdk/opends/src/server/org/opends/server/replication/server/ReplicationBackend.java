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
package org.opends.server.replication.server;
import static org.opends.messages.BackendMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;
import static org.opends.server.loggers.debug.DebugLogger.getTracer;
import static org.opends.server.util.StaticUtils.getExceptionMessage;

import java.util.HashMap;
import java.util.HashSet;

import org.opends.messages.Message;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.admin.std.server.JEBackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.backends.jeb.BackupManager;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.util.Validator;

/**
 * This class defines a backend that stores its information in an
 * associated replication server object.
 * This is primarily intended to take advantage of the backup/restore/
 * import/export of the backend API, and to provide an LDAP access
 * to the replication server database.
 * <BR><BR>
 * Entries stored in this backend are held in the DB associated with
 * the replication server.
 * <BR><BR>
 * Currently are only implemented the create and restore backup features.
 *
 */
public class ReplicationBackend
       extends Backend
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  // The base DNs for this backend.
  private DN[] baseDNs;

  // The mapping between parent DNs and their immediate children.
  private HashMap<DN,HashSet<DN>> childDNs;

  // The base DNs for this backend, in a hash set.
  private HashSet<DN> baseDNSet;

  // The set of supported controls for this backend.
  private HashSet<String> supportedControls;

  // The set of supported features for this backend.
  private HashSet<String> supportedFeatures;

  // The directory associated with this backend.
  private BackupDirectory backendDirectory;

  ReplicationServer server;

  /**
   * The configuration of this backend.
   */
  private JEBackendCfg cfg;

  /**
   * Creates a new backend with the provided information.  All backend
   * implementations must implement a default constructor that use
   * <CODE>super()</CODE> to invoke this constructor.
   */
  public ReplicationBackend()
  {
    super();
    // Perform all initialization in initializeBackend.
  }


  /**
   * Set the base DNs for this backend.  This is used by the unit tests
   * to set the base DNs without having to provide a configuration
   * object when initializing the backend.
   * @param baseDNs The set of base DNs to be served by this memory backend.
   */
  public void setBaseDNs(DN[] baseDNs)
  {
    this.baseDNs = baseDNs;
  }


  /**
   * {@inheritDoc}
   */
  public void configureBackend(Configuration config) throws ConfigException
  {
    if (config != null)
    {
      Validator.ensureTrue(config instanceof BackendCfg);
      cfg = (JEBackendCfg)config;
      DN[] baseDNs = new DN[cfg.getBackendBaseDN().size()];
      cfg.getBackendBaseDN().toArray(baseDNs);
      setBaseDNs(baseDNs);
      backendDirectory = new BackupDirectory(
          cfg.getBackendDirectory(), null);
    }
  }

  /**
   * {@inheritDoc}
   */
  public synchronized void initializeBackend()
       throws ConfigException, InitializationException
  {
    if ((baseDNs == null) || (baseDNs.length != 1))
    {
      Message message = ERR_MEMORYBACKEND_REQUIRE_EXACTLY_ONE_BASE.get();
      throw new ConfigException(message);
    }

    baseDNSet = new HashSet<DN>();
    for (DN dn : baseDNs)
    {
      baseDNSet.add(dn);
    }

    childDNs = new HashMap<DN,HashSet<DN>>();

    supportedControls = new HashSet<String>();
    supportedFeatures = new HashSet<String>();

    for (DN dn : baseDNs)
    {
      try
      {
        DirectoryServer.registerBaseDN(dn, this, false, false);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        Message message = ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(
            dn.toString(), getExceptionMessage(e));
        throw new InitializationException(message, e);
      }
    }
  }



  /**
   * Removes any data that may have been stored in this backend.
   */
  public synchronized void clearMemoryBackend()
  {
    childDNs.clear();
  }



  /**
   * {@inheritDoc}
   */
  public synchronized void finalizeBackend()
  {
    for (DN dn : baseDNs)
    {
      try
      {
        DirectoryServer.deregisterBaseDN(dn, false);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  public DN[] getBaseDNs()
  {
    return baseDNs;
  }

  /**
   * {@inheritDoc}
   */
  public synchronized long getEntryCount()
  {
    return -1;
  }

  /**
   * {@inheritDoc}
   */
  public boolean isLocal()
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public synchronized Entry getEntry(DN entryDN)
  {
    return null;
  }

  /**
   * {@inheritDoc}
   */
  public synchronized boolean entryExists(DN entryDN)
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public synchronized void addEntry(Entry entry, AddOperation addOperation)
         throws DirectoryException
  {
    Message message = ERR_BACKUP_ADD_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }

  /**
   * {@inheritDoc}
   */
  public synchronized void deleteEntry(DN entryDN,
                                       DeleteOperation deleteOperation)
         throws DirectoryException
  {
    Message message = ERR_BACKUP_DELETE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }

  /**
   * {@inheritDoc}
   */
  public synchronized void replaceEntry(Entry entry,
                                        ModifyOperation modifyOperation)
         throws DirectoryException
  {
    Message message = ERR_BACKUP_MODIFY_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }

  /**
   * {@inheritDoc}
   */
  public synchronized void renameEntry(DN currentDN, Entry entry,
                                       ModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    Message message = ERR_BACKUP_MODIFY_DN_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }

  /**
   * {@inheritDoc}
   */
  public synchronized void search(SearchOperation searchOperation)
         throws DirectoryException
  {
    DN matchedDN = baseDNs[0];
    DN baseDN = searchOperation.getBaseDN();
    // FIXME Remove this error message or replace when implementing
    //       the search.
    Message message =
      ERR_MEMORYBACKEND_ENTRY_DOESNT_EXIST.get(String.valueOf(baseDN));
    throw new DirectoryException(
          ResultCode.NO_SUCH_OBJECT, message, matchedDN, null);
  }

  /**
   * {@inheritDoc}
   */
  public HashSet<String> getSupportedControls()
  {
    return supportedControls;
  }

  /**
   * {@inheritDoc}
   */
  public HashSet<String> getSupportedFeatures()
  {
    return supportedFeatures;
  }

  /**
   * {@inheritDoc}
   */
  public boolean supportsLDIFExport()
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public synchronized void exportLDIF(LDIFExportConfig exportConfig)
         throws DirectoryException
  {
    // TODO
  }

  /**
   * {@inheritDoc}
   */
  public boolean supportsLDIFImport()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public synchronized LDIFImportResult importLDIF(LDIFImportConfig importConfig)
         throws DirectoryException
  {
      return new LDIFImportResult(0, 0, 0);
  }



  /**
   * {@inheritDoc}
   */
  public boolean supportsBackup()
  {
    // This backend does not provide a backup/restore mechanism.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public boolean supportsBackup(BackupConfig backupConfig,
                                StringBuilder unsupportedReason)
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public void createBackup(BackupConfig backupConfig)
         throws DirectoryException
  {
    BackupManager backupManager =
      new BackupManager(getBackendID());
    backupManager.createBackup(cfg, backupConfig);
  }



  /**
   * {@inheritDoc}
   */
  public void removeBackup(BackupDirectory backupDirectory,
                           String backupID)
         throws DirectoryException
  {
    BackupManager backupManager =
      new BackupManager(getBackendID());
    backupManager.removeBackup(this.backendDirectory, backupID);
  }

  /**
   * {@inheritDoc}
   */
  public boolean supportsRestore()
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public void restoreBackup(RestoreConfig restoreConfig)
         throws DirectoryException
  {
    BackupManager backupManager =
      new BackupManager(getBackendID());
    backupManager.restoreBackup(cfg, restoreConfig);
  }

  /**
   * Retrieves the number of subordinates for the requested entry.
   *
   * @param entryDN The distinguished name of the entry.
   *
   * @return The number of subordinate entries for the requested entry
   *         or -1 if it can not be determined.
   *
   * @throws DirectoryException  If a problem occurs while trying to
   *                              retrieve the entry.
   */
  public long numSubordinates(DN entryDN)
      throws DirectoryException
  {
    Message message = WARN_ROOTDSE_GET_ENTRY_NONROOT.
    get(entryDN.toNormalizedString());
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }

  /**
   * Indicates whether the requested entry has any subordinates.
   *
   * @param entryDN The distinguished name of the entry.
   *
   * @return {@code ConditionResult.TRUE} if the entry has one or more
   *         subordinates or {@code ConditionResult.FALSE} otherwise
   *         or {@code ConditionResult.UNDEFINED} if it can not be
   *         determined.
   *
   * @throws DirectoryException  If a problem occurs while trying to
   *                              retrieve the entry.
   */
  public ConditionResult hasSubordinates(DN entryDN)
        throws DirectoryException
  {
    Message message = WARN_ROOTDSE_GET_ENTRY_NONROOT.
      get(entryDN.toNormalizedString());
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }

  /**
   * Set the replication server associated with this backend.
   * @param server The replication server.
   */
  public void setServer(ReplicationServer server)
  {
    this.server = server;
  }
}
