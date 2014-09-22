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

import java.util.Set;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ConditionResult;
import org.opends.server.admin.Configuration;
import org.opends.server.api.Backend;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.changelog.je.ECLEnabledDomainPredicate;
import org.opends.server.types.AttributeType;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.RestoreConfig;

/**
 * Changelog backend.
 */
public class ChangelogBackend extends Backend<Configuration>
{

  /** Backend id. */
  public static final String BACKEND_ID = "changelog";

  /**
   * Creates.
   *
   * @param replicationServer
   *            The replication server.
   * @param domainPredicate
   *            The predicate.
   */
  public ChangelogBackend(ReplicationServer replicationServer,
      ECLEnabledDomainPredicate domainPredicate)
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void configureBackend(Configuration cfg) throws ConfigException
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void initializeBackend() throws ConfigException,
      InitializationException
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void finalizeBackend()
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public DN[] getBaseDNs()
  {
    throw new RuntimeException("Not implemented");
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
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
      throws DirectoryException, CanceledOperationException
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void replaceEntry(Entry oldEntry, Entry newEntry,
      ModifyOperation modifyOperation) throws DirectoryException,
      CanceledOperationException
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void renameEntry(DN currentDN, Entry entry,
      ModifyDNOperation modifyDNOperation) throws DirectoryException,
      CanceledOperationException
  {
    throw new RuntimeException("Not implemented");
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
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public Set<String> getSupportedFeatures()
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public boolean supportsLDIFExport()
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void exportLDIF(LDIFExportConfig exportConfig)
      throws DirectoryException
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public boolean supportsLDIFImport()
  {
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig)
      throws DirectoryException
  {
    throw new RuntimeException("Not implemented");
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
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public void removeBackup(BackupDirectory backupDirectory, String backupID)
      throws DirectoryException
  {
    throw new RuntimeException("Not implemented");
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
    throw new RuntimeException("Not implemented");
  }

  /** {@inheritDoc} */
  @Override
  public long getEntryCount()
  {
    throw new RuntimeException("Not implemented");
  }

}
