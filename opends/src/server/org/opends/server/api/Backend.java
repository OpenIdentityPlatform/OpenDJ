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
package org.opends.server.api;
import org.opends.messages.Message;



import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import org.opends.server.admin.Configuration;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.monitors.BackendMonitor;
import org.opends.server.types.AttributeType;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.CancelledOperationException;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.LockManager;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.WritabilityMode;
import org.opends.server.types.ConditionResult;

import static org.opends.messages.BackendMessages.*;



/**
 * This class defines the set of methods and structures that must be
 * implemented for a Directory Server backend.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=true,
     mayInvoke=false)
public abstract class Backend
{
  // The backend that holds a portion of the DIT that is
  // hierarchically above the information in this backend.
  private Backend parentBackend;

  // The set of backends that hold portions of the DIT that are
  // hierarchically below the information in this backend.
  private Backend[] subordinateBackends;

  // The backend monitor associated with this backend.
  private BackendMonitor backendMonitor;

  // Indicates whether this is a private backend or one that holds
  // user data.
  private boolean isPrivateBackend;

  // The unique identifier for this backend.
  private String backendID;

  // The writability mode for this backend.
  private WritabilityMode writabilityMode;



  /**
   * Creates a new backend with the provided information.  All backend
   * implementations must implement a default constructor that use
   * {@code super} to invoke this constructor.
   */
  protected Backend()
  {
    backendID           = null;
    parentBackend       = null;
    subordinateBackends = new Backend[0];
    isPrivateBackend    = false;
    writabilityMode     = WritabilityMode.ENABLED;
    backendMonitor      = null;
  }



  /**
   * Configure this backend based on the information in the provided
   * configuration.
   *
   * @param  cfg          The configuration of this backend.
   *
   * @throws  ConfigException
   *                      If there is an error in the configuration.
   */
  public abstract void configureBackend(Configuration cfg)
         throws ConfigException;



  /**
   * Indicates whether the provided configuration is acceptable for
   * this backend.  It should be possible to call this method on an
   * uninitialized backend instance in order to determine whether the
   * backend would be able to use the provided configuration.
   * <BR><BR>
   * Note that implementations which use a subclass of the provided
   * configuration class will likely need to cast the configuration
   * to the appropriate subclass type.
   *
   * @param  configuration        The backend configuration for which
   *                              to make the determination.
   * @param  unacceptableReasons  A list that may be used to hold the
   *                              reasons that the provided
   *                              configuration is not acceptable.
   *
   * @return  {@code true} if the provided configuration is acceptable
   *          for this backend, or {@code false} if not.
   */
  public boolean isConfigurationAcceptable(
                      Configuration configuration,
                      List<Message> unacceptableReasons)
  {
    // This default implementation does not perform any special
    // validation.  It should be overridden by backend implementations
    // that wish to perform more detailed validation.
    return true;
  }



  /**
   * Initializes this backend based on the information provided
   * when the backend was configured.
   *
   * @see #configureBackend
   *
   * @throws  ConfigException  If an unrecoverable problem arises in
   *                           the process of performing the
   *                           initialization.
   *
   * @throws  InitializationException  If a problem occurs during
   *                                   initialization that is not
   *                                   related to the server
   *                                   configuration.
   */
  public abstract void initializeBackend()
         throws ConfigException, InitializationException;



  /**
   * Performs any necessary work to finalize this backend, including
   * closing any underlying databases or connections and deregistering
   * any suffixes that it manages with the Directory Server.  This may
   * be called during the Directory Server shutdown process or if a
   * backend is disabled with the server online.  It must not return
   * until the backend is closed.
   * <BR><BR>
   * This method may not throw any exceptions.  If any problems are
   * encountered, then they may be logged but the closure should
   * progress as completely as possible.
   */
  public abstract void finalizeBackend();



  /**
   * Retrieves the set of base-level DNs that may be used within this
   * backend.
   *
   * @return  The set of base-level DNs that may be used within this
   *          backend.
   */
  public abstract DN[] getBaseDNs();



  /**
   * Attempts to pre-load all the entries stored within this backend
   * into the entry cache. Note that the caller must ensure that the
   * backend stays in read-only state until this method returns as
   * no entry locking is performed during this operation. Also note
   * that any backend implementing this method should implement pre-
   * load progress reporting and error handling specific to its own
   * implementation.
   *
   * @throws  UnsupportedOperationException if backend does not
   *          support this operation.
   */
  public abstract void preloadEntryCache()
    throws UnsupportedOperationException;



  /**
   * Indicates whether the data associated with this backend may be
   * considered local (i.e., in a repository managed by the Directory
   * Server) rather than remote (i.e., in an external repository
   * accessed by the Directory Server but managed through some other
   * means).
   *
   * @return  {@code true} if the data associated with this backend
   *          may be considered local, or {@code false} if it is
   *          remote.
   */
  public abstract boolean isLocal();



  /**
   * Indicates whether search operations which target the specified
   * attribute in the indicated manner would be considered indexed
   * in this backend.  The operation should be considered indexed only
   * if the specified operation can be completed efficiently within
   * the backend.
   * <BR><BR>
   * Note that this method should return a general result that covers
   * all values of the specified attribute.  If a the specified
   * attribute is indexed in the indicated manner but some particular
   * values may still be treated as unindexed (e.g., if the number of
   * entries with that attribute value exceeds some threshold), then
   * this method should still return {@code true} for the specified
   * attribute and index type.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   * @param  indexType      The index type for which to make the
   *                        determination.
   *
   * @return  {@code true} if search operations targeting the
   *          specified attribute in the indicated manner should be
   *          considered indexed, or {@code false} if not.
   */
  public abstract boolean isIndexed(AttributeType attributeType,
                                    IndexType indexType);



  /**
   * Indicates whether extensible match search operations that target
   * the specified attribute with the given matching rule should be
   * considered indexed in this backend.
   *
   * @param  attributeType  The attribute type for which to make the
   *                        determination.
   * @param  matchingRule   The matching rule for which to make the
   *                        determination.
   *
   * @return  {@code true} if extensible match search operations
   *          targeting the specified attribute with the given
   *          matching rule should be considered indexed, or
   *          {@code false} if not.
   */
  public boolean isIndexed(AttributeType attributeType,
                           MatchingRule matchingRule)
  {
    return false;
  }



  /**
   * Indicates whether a subtree search using the provided filter
   * would be indexed in this backend.  This default implementation
   * uses a rough set of logic that makes a best-effort determination.
   * Subclasses that provide a more complete indexing mechanism may
   * wish to override this method and provide a more accurate result.
   *
   * @param  filter  The search filter for which to make the
   *                 determination.
   *
   * @return  {@code true} if it is believed that the provided filter
   *          would be indexed in this backend, or {@code false} if
   *          not.
   */
  public boolean isIndexed(SearchFilter filter)
  {
    switch (filter.getFilterType())
    {
      case AND:
        // At least one of the subordinate filter components must be
        // indexed.
        for (SearchFilter f : filter.getFilterComponents())
        {
          if (isIndexed(f))
          {
            return true;
          }
        }
        return false;


      case OR:
        for (SearchFilter f : filter.getFilterComponents())
        {
          if (! isIndexed(f))
          {
            return false;
          }
        }
        return (! filter.getFilterComponents().isEmpty());


      case NOT:
        // NOT filters are not considered indexed by default.
        return false;


      case EQUALITY:
        return isIndexed(filter.getAttributeType(),
                         IndexType.EQUALITY);


      case SUBSTRING:
        return isIndexed(filter.getAttributeType(),
                         IndexType.SUBSTRING);


      case GREATER_OR_EQUAL:
        return isIndexed(filter.getAttributeType(),
                         IndexType.GREATER_OR_EQUAL);


      case LESS_OR_EQUAL:
        return isIndexed(filter.getAttributeType(),
                         IndexType.LESS_OR_EQUAL);


      case PRESENT:
        return isIndexed(filter.getAttributeType(),
                         IndexType.PRESENCE);


      case APPROXIMATE_MATCH:
        return isIndexed(filter.getAttributeType(),
                         IndexType.APPROXIMATE);


      case EXTENSIBLE_MATCH:
        // The attribute type must be provided for us to make the
        // determination.  If a matching rule ID is provided, then
        // we'll use it as well, but if not then we'll use the
        // default equality matching rule for the attribute type.
        AttributeType attrType = filter.getAttributeType();
        if (attrType == null)
        {
          return false;
        }

        MatchingRule matchingRule;
        String matchingRuleID = filter.getMatchingRuleID();
        if (matchingRuleID == null)
        {
          matchingRule = DirectoryServer.getMatchingRule(
                              matchingRuleID.toLowerCase());
        }
        else
        {
          matchingRule = attrType.getEqualityMatchingRule();
        }

        if (matchingRule == null)
        {
          return false;
        }
        else
        {
          return isIndexed(attrType, matchingRule);
        }


      default:
        return false;
    }
  }



  /**
   * Retrieves the requested entry from this backend.  Note that the
   * caller must hold a read or write lock on the specified DN.
   *
   * @param  entryDN  The distinguished name of the entry to retrieve.
   *
   * @return  The requested entry, or {@code null} if the entry does
   *          not exist.
   *
   * @throws  DirectoryException  If a problem occurs while trying to
   *                              retrieve the entry.
   */
  public abstract Entry getEntry(DN entryDN)
         throws DirectoryException;



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
  public abstract ConditionResult hasSubordinates(DN entryDN)
        throws DirectoryException;



  /**
   * Retrieves the number of subordinates for the requested entry.
   *
   * @param entryDN The distinguished name of the entry.
   *
   * @param subtree <code>true</code> to include all entries from the
   *                      requested entry to the lowest level in the
   *                      tree or <code>false</code> to only include
   *                      the entries immediately below the requested
   *                      entry.
   *
   * @return The number of subordinate entries for the requested entry
   *         or -1 if it can not be determined.
   *
   * @throws DirectoryException  If a problem occurs while trying to
   *                              retrieve the entry.
   */
  public abstract long numSubordinates(DN entryDN, boolean subtree)
      throws DirectoryException;



  /**
   * Indicates whether an entry with the specified DN exists in the
   * backend. The default implementation obtains a read lock and calls
   * {@code getEntry}, but backend implementations may override this
   * with a more efficient version that does not require a lock.  The
   * caller is not required to hold any locks on the specified DN.
   *
   * @param  entryDN  The DN of the entry for which to determine
   *                  existence.
   *
   * @return  {@code true} if the specified entry exists in this
   *          backend, or {@code false} if it does not.
   *
   * @throws  DirectoryException  If a problem occurs while trying to
   *                              make the determination.
   */
  public boolean entryExists(DN entryDN)
         throws DirectoryException
  {
    Lock lock = null;
    for (int i=0; i < 3; i++)
    {
      lock = LockManager.lockRead(entryDN);
      if (lock != null)
      {
        break;
      }
    }

    if (lock == null)
    {
      Message message =
          ERR_BACKEND_CANNOT_LOCK_ENTRY.get(String.valueOf(entryDN));
      throw new DirectoryException(
                     DirectoryServer.getServerErrorResultCode(),
                     message);
    }

    try
    {
      return (getEntry(entryDN) != null);
    }
    finally
    {
      LockManager.unlock(entryDN, lock);
    }
  }



  /**
   * Adds the provided entry to this backend.  This method must ensure
   * that the entry is appropriate for the backend and that no entry
   * already exists with the same DN.  The caller must hold a write
   * lock on the DN of the provided entry.
   *
   * @param  entry         The entry to add to this backend.
   * @param  addOperation  The add operation with which the new entry
   *                       is associated.  This may be {@code null}
   *                       for adds performed internally.
   *
   * @throws  DirectoryException  If a problem occurs while trying to
   *                              add the entry.
   *
   * @throws  CancelledOperationException  If this backend noticed and
   *                                       reacted to a request to
   *                                       cancel or abandon the add
   *                                       operation.
   */
  public abstract void addEntry(Entry entry,
                                AddOperation addOperation)
         throws DirectoryException, CancelledOperationException;



  /**
   * Removes the specified entry from this backend.  This method must
   * ensure that the entry exists and that it does not have any
   * subordinate entries (unless the backend supports a subtree delete
   * operation and the client included the appropriate information in
   * the request).  The caller must hold a write lock on the provided
   * entry DN.
   *
   * @param  entryDN          The DN of the entry to remove from this
   *                          backend.
   * @param  deleteOperation  The delete operation with which this
   *                          action is associated.  This may be
   *                          {@code null} for deletes performed
   *                          internally.
   *
   * @throws  DirectoryException  If a problem occurs while trying to
   *                              remove the entry.
   *
   * @throws  CancelledOperationException  If this backend noticed and
   *                                       reacted to a request to
   *                                       cancel or abandon the
   *                                       delete operation.
   */
  public abstract void deleteEntry(DN entryDN,
                                   DeleteOperation deleteOperation)
         throws DirectoryException, CancelledOperationException;



  /**
   * Replaces the specified entry with the provided entry in this
   * backend.  The backend must ensure that an entry already exists
   * with the same DN as the provided entry.  The caller must hold a
   * write lock on the DN of the provided entry.
   *
   * @param  entry            The new entry to use in place of the
   *                          existing entry with the same DN.
   * @param  modifyOperation  The modify operation with which this
   *                          action is associated.  This may be
   *                          {@code null} for modifications performed
   *                          internally.
   *
   * @throws  DirectoryException  If a problem occurs while trying to
   *                              replace the entry.
   *
   * @throws  CancelledOperationException  If this backend noticed and
   *                                       reacted to a request to
   *                                       cancel or abandon the
   *                                       modify operation.
   */
  public abstract void replaceEntry(Entry entry,
                                    ModifyOperation modifyOperation)
         throws DirectoryException, CancelledOperationException;



  /**
   * Moves and/or renames the provided entry in this backend, altering
   * any subordinate entries as necessary.  This must ensure that an
   * entry already exists with the provided current DN, and that no
   * entry exists with the target DN of the provided entry.  The
   * caller must hold write locks on both the current DN and the new
   * DN for the entry.
   *
   * @param  currentDN          The current DN of the entry to be
   *                            replaced.
   * @param  entry              The new content to use for the entry.
   * @param  modifyDNOperation  The modify DN operation with which
   *                            this action is associated.  This may
   *                            be {@code null} for modify DN
   *                            operations performed internally.
   *
   * @throws  DirectoryException  If a problem occurs while trying to
   *                              perform the rename.
   *
   * @throws  CancelledOperationException  If this backend noticed and
   *                                       reacted to a request to
   *                                       cancel or abandon the
   *                                       modify DN operation.
   */
  public abstract void renameEntry(DN currentDN, Entry entry,
                            ModifyDNOperation modifyDNOperation)
         throws DirectoryException, CancelledOperationException;



  /**
   * Processes the specified search in this backend.  Matching entries
   * should be provided back to the core server using the
   * {@code SearchOperation.returnEntry} method.  The caller is not
   * required to have any locks when calling this operation.
   *
   * @param  searchOperation  The search operation to be processed.
   *
   * @throws  DirectoryException  If a problem occurs while processing
   *                              the search.
   *
   * @throws  CancelledOperationException  If this backend noticed and
   *                                       reacted to a request to
   *                                       cancel or abandon the
   *                                       search operation.
   */
  public abstract void search(SearchOperation searchOperation)
         throws DirectoryException, CancelledOperationException;



  /**
   * Retrieves the OIDs of the controls that may be supported by this
   * backend.
   *
   * @return  The OIDs of the controls that may be supported by this
   *          backend.
   */
  public abstract Set<String> getSupportedControls();



  /**
   * Indicates whether this backend supports the specified control.
   *
   * @param  controlOID  The OID of the control for which to make the
   *                     determination.
   *
   * @return  {@code true} if this backends supports the control with
   *          the specified OID, or {@code false} if it does not.
   */
  public final boolean supportsControl(String controlOID)
  {
    Set<String> supportedControls = getSupportedControls();
    return ((supportedControls != null) &&
            supportedControls.contains(controlOID));
  }



  /**
   * Retrieves the OIDs of the features that may be supported by this
   * backend.
   *
   * @return  The OIDs of the features that may be supported by this
   *          backend.
   */
  public abstract Set<String> getSupportedFeatures();



  /**
   * Indicates whether this backend supports the specified feature.
   *
   * @param  featureOID  The OID of the feature for which to make the
   *                     determination.
   *
   * @return  {@code true} if this backend supports the feature with
   *          the specified OID, or {@code false} if it does not.
   */
  public final boolean supportsFeature(String featureOID)
  {
    Set<String> supportedFeatures = getSupportedFeatures();
    return ((supportedFeatures != null) &&
            supportedFeatures.contains(featureOID));
  }



  /**
   * Indicates whether this backend provides a mechanism to export the
   * data it contains to an LDIF file.
   *
   * @return  {@code true} if this backend provides an LDIF export
   *          mechanism, or {@code false} if not.
   */
  public abstract boolean supportsLDIFExport();



  /**
   * Exports the contents of this backend to LDIF.  This method should
   * only be called if {@code supportsLDIFExport} returns
   * {@code true}.  Note that the server will not explicitly
   * initialize this backend before calling this method.
   *
   * @param  exportConfig  The configuration to use when performing
   *                       the export.
   *
   * @throws  DirectoryException  If a problem occurs while performing
   *                              the LDIF export.
   */
  public abstract void exportLDIF(LDIFExportConfig exportConfig)
         throws DirectoryException;



  /**
   * Indicates whether this backend provides a mechanism to import its
   * data from an LDIF file.
   *
   * @return  {@code true} if this backend provides an LDIF import
   *          mechanism, or {@code false} if not.
   */
  public abstract boolean supportsLDIFImport();



  /**
   * Imports information from an LDIF file into this backend.  This
   * method should only be called if {@code supportsLDIFImport}
   * returns {@code true}.  Note that the server will not explicitly
   * initialize this backend before calling this method.
   *
   * @param  importConfig  The configuration to use when performing
   *                       the import.
   *
   * @return  Information about the result of the import processing.
   *
   * @throws  DirectoryException  If a problem occurs while performing
   *                              the LDIF import.
   */
  public abstract LDIFImportResult importLDIF(
                                        LDIFImportConfig importConfig)
         throws DirectoryException;



  /**
   * Indicates whether this backend provides a backup mechanism of any
   * kind.  This method is used by the backup process when backing up
   * all backends to determine whether this backend is one that should
   * be skipped.  It should only return {@code true} for backends that
   * it is not possible to archive directly (e.g., those that don't
   * store their data locally, but rather pass through requests to
   * some other repository).
   *
   * @return  {@code true} if this backend provides any kind of backup
   *          mechanism, or {@code false} if it does not.
   */
  public abstract boolean supportsBackup();



  /**
   * Indicates whether this backend provides a mechanism to perform a
   * backup of its contents in a form that can be restored later,
   * based on the provided configuration.
   *
   * @param  backupConfig       The configuration of the backup for
   *                            which to make the determination.
   * @param  unsupportedReason  A buffer to which a message can be
   *                            appended
   *                            explaining why the requested backup is
   *                            not supported.
   *
   * @return  {@code true} if this backend provides a mechanism for
   *          performing backups with the provided configuration, or
   *          {@code false} if not.
   */
  public abstract boolean supportsBackup(BackupConfig backupConfig,
                               StringBuilder unsupportedReason);



  /**
   * Creates a backup of the contents of this backend in a form that
   * may be restored at a later date if necessary.  This method should
   * only be called if {@code supportsBackup} returns {@code true}.
   * Note that the server will not explicitly initialize this backend
   * before calling this method.
   *
   * @param  backupConfig  The configuration to use when performing
   *                       the backup.
   *
   * @throws  DirectoryException  If a problem occurs while performing
   *                              the backup.
   */
  public abstract void createBackup(BackupConfig backupConfig)
         throws DirectoryException;



  /**
   * Removes the specified backup if it is possible to do so.
   *
   * @param  backupDirectory  The backup directory structure with
   *                          which the specified backup is
   *                          associated.
   * @param  backupID         The backup ID for the backup to be
   *                          removed.
   *
   * @throws  DirectoryException  If it is not possible to remove the
   *                              specified backup for some reason
   *                              (e.g., no such backup exists or
   *                              there are other backups that are
   *                              dependent upon it).
   */
  public abstract void removeBackup(BackupDirectory backupDirectory,
                                    String backupID)
         throws DirectoryException;



  /**
   * Indicates whether this backend provides a mechanism to restore a
   * backup.
   *
   * @return  {@code true} if this backend provides a mechanism for
   *          restoring backups, or {@code false} if not.
   */
  public abstract boolean supportsRestore();



  /**
   * Restores a backup of the contents of this backend.  This method
   * should only be called if {@code supportsRestore} returns
   * {@code true}.  Note that the server will not explicitly
   * initialize this backend before calling this method.
   *
   * @param  restoreConfig  The configuration to use when performing
   *                        the restore.
   *
   * @throws  DirectoryException  If a problem occurs while performing
   *                              the restore.
   */
  public abstract void restoreBackup(RestoreConfig restoreConfig)
         throws DirectoryException;



  /**
   * Retrieves the unique identifier for this backend.
   *
   * @return  The unique identifier for this backend.
   */
  public final String getBackendID()
  {
    return backendID;
  }



  /**
   * Specifies the unique identifier for this backend.
   *
   * @param  backendID  The unique identifier for this backend.
   */
  public final void setBackendID(String backendID)
  {
    this.backendID = backendID;
  }



  /**
   * Indicates whether this backend holds private data or user data.
   *
   * @return  {@code true} if this backend holds private data, or
   *          {@code false} if it holds user data.
   */
  public final boolean isPrivateBackend()
  {
    return isPrivateBackend;
  }



  /**
   * Specifies whether this backend holds private data or user data.
   *
   * @param  isPrivateBackend  Specifies whether this backend holds
   *                           private data or user data.
   */
  public final void setPrivateBackend(boolean isPrivateBackend)
  {
    this.isPrivateBackend = isPrivateBackend;
  }



  /**
   * Retrieves the writability mode for this backend.
   *
   * @return  The writability mode for this backend.
   */
  public final WritabilityMode getWritabilityMode()
  {
    return writabilityMode;
  }



  /**
   * Specifies the writability mode for this backend.
   *
   * @param  writabilityMode  The writability mode for this backend.
   */
  public final void setWritabilityMode(
                         WritabilityMode writabilityMode)
  {
    if (writabilityMode == null)
    {
      this.writabilityMode = WritabilityMode.ENABLED;
    }
    else
    {
      this.writabilityMode = writabilityMode;
    }
  }



  /**
   * Retrieves the backend monitor that is associated with this
   * backend.
   *
   * @return  The backend monitor that is associated with this
   *          backend, or {@code null} if none has been assigned.
   */
  public final BackendMonitor getBackendMonitor()
  {
    return backendMonitor;
  }



  /**
   * Sets the backend monitor for this backend.
   *
   * @param  backendMonitor  The backend monitor for this backend.
   */
  public final void setBackendMonitor(BackendMonitor backendMonitor)
  {
    this.backendMonitor = backendMonitor;
  }



  /**
   * Retrieves the total number of entries contained in this backend,
   * if that information is available.
   *
   * @return  The total number of entries contained in this backend,
   *          or -1 if that information is not available.
   */
  public abstract long getEntryCount();



  /**
   * Retrieves the parent backend for this backend.
   *
   * @return  The parent backend for this backend, or {@code null} if
   *          there is none.
   */
  public final Backend getParentBackend()
  {
    return parentBackend;
  }



  /**
   * Specifies the parent backend for this backend.
   *
   * @param  parentBackend  The parent backend for this backend.
   */
  public final void setParentBackend(Backend parentBackend)
  {
    synchronized (this)
    {
      this.parentBackend = parentBackend;
    }
  }



  /**
   * Retrieves the set of subordinate backends for this backend.
   *
   * @return  The set of subordinate backends for this backend, or an
   *          empty array if none exist.
   */
  public final Backend[] getSubordinateBackends()
  {
    return subordinateBackends;
  }



  /**
   * Specifies the set of subordinate backends for this backend.
   *
   * @param  subordinateBackends  The set of subordinate backends for
   *                              this backend.
   */
  public final void setSubordinateBackends(
                         Backend[] subordinateBackends)
  {
    synchronized (this)
    {
      this.subordinateBackends = subordinateBackends;
    }
  }



  /**
   * Indicates whether this backend has a subordinate backend
   * registered with the provided base DN.  This may check recursively
   * if a subordinate backend has its own subordinate backends.
   *
   * @param  subSuffixDN  The DN of the sub-suffix for which to make
   *                      the determination.
   *
   * @return  {@code true} if this backend has a subordinate backend
   *          registered with the provided base DN, or {@code false}
   *          if it does not.
   */
  public final boolean hasSubSuffix(DN subSuffixDN)
  {
    Backend[] subBackends = subordinateBackends;
    for (Backend b : subBackends)
    {
      for (DN baseDN : b.getBaseDNs())
      {
        if (baseDN.equals(subSuffixDN))
        {
          return true;
        }
      }

      if (b.hasSubSuffix(subSuffixDN))
      {
        return true;
      }
    }

    return false;
  }



  /**
   * Removes the backend associated with the specified sub-suffix if
   * it is registered.  This may check recursively if a subordinate
   * backend has its own subordinate backends.
   *
   * @param  subSuffixDN  The DN of the sub-suffix to remove from this
   *                      backend.
   * @param  parentDN     The superior DN for the sub-suffix DN that
   *                      matches one of the subordinate base DNs for
   *                      this backend.
   *
   * @throws  ConfigException  If the sub-suffix exists but it is not
   *                           possible to remove it for some reason.
   */
  public final void removeSubSuffix(DN subSuffixDN, DN parentDN)
         throws ConfigException
  {
    synchronized (this)
    {
      boolean matchFound = false;
      ArrayList<Backend> subBackendList =
           new ArrayList<Backend>(subordinateBackends.length);
      for (Backend b : subordinateBackends)
      {
        boolean thisMatches = false;
        DN[] subBaseDNs = b.getBaseDNs();
        for (DN dn : subBaseDNs)
        {
          if (dn.equals(subSuffixDN))
          {
            if (subBaseDNs.length > 1)
            {
              Message message =
                      ERR_BACKEND_CANNOT_REMOVE_MULTIBASE_SUB_SUFFIX.
                              get(String.valueOf(subSuffixDN),
                                      String.valueOf(parentDN));
              throw new ConfigException(message);
            }

            thisMatches = true;
            matchFound  = true;
            break;
          }
        }

        if (! thisMatches)
        {
          if (b.hasSubSuffix(subSuffixDN))
          {
            b.removeSubSuffix(subSuffixDN, parentDN);
          }
          else
          {
            subBackendList.add(b);
          }
        }
      }

      if (matchFound)
      {
        Backend[] newSubordinateBackends =
             new Backend[subBackendList.size()];
        subBackendList.toArray(newSubordinateBackends);
        subordinateBackends = newSubordinateBackends;
      }
    }
  }



  /**
   * Adds the provided backend to the set of subordinate backends for
   * this backend.
   *
   * @param  subordinateBackend  The backend to add to the set of
   *                             subordinate backends for this
   *                             backend.
   */
  public final void addSubordinateBackend(Backend subordinateBackend)
  {
    synchronized (this)
    {
      LinkedHashSet<Backend> backendSet =
           new LinkedHashSet<Backend>();

      for (Backend b : subordinateBackends)
      {
        backendSet.add(b);
      }

      if (backendSet.add(subordinateBackend))
      {
        Backend[] newSubordinateBackends =
             new Backend[backendSet.size()];
        backendSet.toArray(newSubordinateBackends);
        subordinateBackends = newSubordinateBackends;
      }
    }
  }



  /**
   * Removes the provided backend from the set of subordinate backends
   * for this backend.
   *
   * @param  subordinateBackend  The backend to remove from the set of
   *                             subordinate backends for this
   *                             backend.
   */
  public final void removeSubordinateBackend(
                         Backend subordinateBackend)
  {
    synchronized (this)
    {
      ArrayList<Backend> backendList =
           new ArrayList<Backend>(subordinateBackends.length);

      boolean found = false;
      for (Backend b : subordinateBackends)
      {
        if (b.equals(subordinateBackend))
        {
          found = true;
        }
        else
        {
          backendList.add(b);
        }
      }

      if (found)
      {
        Backend[] newSubordinateBackends =
             new Backend[backendList.size()];
        backendList.toArray(newSubordinateBackends);
        subordinateBackends = newSubordinateBackends;
      }
    }
  }



  /**
   * Indicates whether this backend should be used to handle
   * operations for the provided entry.
   *
   * @param  entryDN  The DN of the entry for which to make the
   *                  determination.
   *
   * @return  {@code true} if this backend handles operations for the
   *          provided entry, or {@code false} if it does not.
   */
  public final boolean handlesEntry(DN entryDN)
  {
    DN[] baseDNs = getBaseDNs();
    for (DN dn : baseDNs)
    {
      if (entryDN.isDescendantOf(dn))
      {
        Backend[] subBackends = subordinateBackends;
        for (Backend b : subBackends)
        {
          if (b.handlesEntry(entryDN))
          {
            return false;
          }
        }
        return true;
      }
    }
    return false;
  }



  /**
   * Indicates whether a backend should be used to handle operations
   * for the provided entry given the set of base DNs and exclude DNs.
   *
   * @param  entryDN     The DN of the entry for which to make the
   *                     determination.
   * @param  baseDNs     The set of base DNs for the backend.
   * @param  excludeDNs  The set of DNs that should be excluded from
   *                     the backend.
   *
   * @return  {@code true} if the backend should handle operations for
   *          the provided entry, or {@code false} if it does not.
   */
  public static final boolean handlesEntry(DN entryDN,
                                           List<DN> baseDNs,
                                           List<DN> excludeDNs)
  {
    for (DN baseDN : baseDNs)
    {
      if (entryDN.isDescendantOf(baseDN))
      {
        if ((excludeDNs == null) || excludeDNs.isEmpty())
        {
          return true;
        }

        boolean isExcluded = false;
        for (DN excludeDN : excludeDNs)
        {
          if (entryDN.isDescendantOf(excludeDN))
          {
            isExcluded = true;
            break;
          }
        }

        if (! isExcluded)
        {
          return true;
        }
      }
    }

    return false;
  }
}
