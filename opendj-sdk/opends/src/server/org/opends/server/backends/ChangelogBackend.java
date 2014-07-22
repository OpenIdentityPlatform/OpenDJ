/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2014 ForgeRock AS.
 */
package org.opends.server.backends;

import java.util.Collections;
import java.util.Set;

import org.opends.server.admin.Configuration;
import org.opends.server.api.Backend;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.util.Validator;

import static org.opends.messages.BackendMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * A backend that provides access to the changelog, ie the "cn=changelog" suffix.
 * It is a read-only backend.
 */
public class ChangelogBackend extends Backend
{
  private static final DebugTracer TRACER = getTracer();

  /** The DN for the base changelog entry. */
  private DN baseChangelogDN;

  /** The set of base DNs for this backend. */
  private DN[] baseDNs;

  /** The set of supported controls for this backend. */
  private final Set<String> supportedControls =
      Collections.singleton(OID_ECL_COOKIE_EXCHANGE_CONTROL);

  /** {@inheritDoc} */
  @Override
  public void configureBackend(final Configuration cfg) throws ConfigException
  {
    Validator.ensureNotNull(cfg);

    final ConfigEntry configEntry = DirectoryServer.getConfigEntry(cfg.dn());

    // Make sure that a configuration entry was provided. If not, then we will
    // not be able to complete initialization.
    if (configEntry == null)
    {
      throw new ConfigException(ERR_BACKEND_CONFIG_ENTRY_NULL.get(getBackendID()));
    }

    // Create the set of base DNs that we will handle. In this case, it's just
    // the DN of the base changelog entry.
    try
    {
      baseChangelogDN = DN.decode(DN_EXTERNAL_CHANGELOG_ROOT);
    }
    catch (final Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      throw new ConfigException(
          ERR_BACKEND_CANNOT_DECODE_BACKEND_ROOT_DN.get(getBackendID(), getExceptionMessage(e)), e);
    }

    this.baseDNs = new DN[] { baseChangelogDN };
  }

  /** {@inheritDoc} */
  @Override
  public void initializeBackend() throws ConfigException, InitializationException
  {
    try
    {
      DirectoryServer.registerBaseDN(baseChangelogDN, this, true);
    }
    catch (final Exception e)
    {
      throw new InitializationException(
          ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(baseChangelogDN.toString(), getExceptionMessage(e)), e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void finalizeBackend()
  {
    try
    {
      DirectoryServer.deregisterBaseDN(baseChangelogDN);
    }
    catch (final Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }

  /** {@inheritDoc} */
  @Override
  public DN[] getBaseDNs()
  {
    return baseDNs;
  }

  /** {@inheritDoc} */
  @Override
  public void preloadEntryCache() throws UnsupportedOperationException
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public boolean isLocal()
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public boolean isIndexed(AttributeType attributeType, IndexType indexType)
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public Entry getEntry(DN entryDN) throws DirectoryException
  {
    if (entryDN == null)
    {
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          ERR_BACKEND_GET_ENTRY_NULL.get(getBackendID()));
    }
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public ConditionResult hasSubordinates(DN entryDN) throws DirectoryException
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public long numSubordinates(DN entryDN, boolean subtree)
      throws DirectoryException
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void addEntry(Entry entry, AddOperation addOperation)
      throws DirectoryException, CanceledOperationException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_ADD_NOT_SUPPORTED.get(String.valueOf(entry.getDN()), getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
      throws DirectoryException, CanceledOperationException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_DELETE_NOT_SUPPORTED.get(String.valueOf(entryDN), getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public void replaceEntry(Entry oldEntry, Entry newEntry,
      ModifyOperation modifyOperation) throws DirectoryException,
      CanceledOperationException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_MODIFY_NOT_SUPPORTED.get(String.valueOf(newEntry.getDN()), getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public void renameEntry(DN currentDN, Entry entry,
      ModifyDNOperation modifyDNOperation) throws DirectoryException,
      CanceledOperationException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_MODIFY_DN_NOT_SUPPORTED.get(String.valueOf(currentDN), getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public void search(SearchOperation searchOperation)
      throws DirectoryException, CanceledOperationException
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> getSupportedControls()
  {
    return supportedControls;
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> getSupportedFeatures()
  {
    return Collections.emptySet();
  }

  /** {@inheritDoc} */
  @Override
  public boolean supportsLDIFExport()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void exportLDIF(LDIFExportConfig exportConfig)
      throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_IMPORT_AND_EXPORT_NOT_SUPPORTED.get(getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public boolean supportsLDIFImport()
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig)
      throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_IMPORT_AND_EXPORT_NOT_SUPPORTED.get(getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public boolean supportsBackup()
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public boolean supportsBackup(BackupConfig backupConfig,
      StringBuilder unsupportedReason)
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void createBackup(BackupConfig backupConfig) throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_BACKUP_AND_RESTORE_NOT_SUPPORTED.get(getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public void removeBackup(BackupDirectory backupDirectory, String backupID)
      throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_BACKUP_AND_RESTORE_NOT_SUPPORTED.get(getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public boolean supportsRestore()
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void restoreBackup(RestoreConfig restoreConfig)
      throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_BACKUP_AND_RESTORE_NOT_SUPPORTED.get(getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public long getEntryCount()
  {
    throw new RuntimeException("Not implemented");
  }

}
