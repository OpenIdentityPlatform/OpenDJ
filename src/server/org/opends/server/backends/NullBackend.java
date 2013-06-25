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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.backends;



import java.util.HashMap;
import java.util.HashSet;

import java.util.Map;
import org.opends.messages.Category;
import org.opends.messages.Message;
import org.opends.messages.Severity;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.config.ConfigException;
import org.opends.server.controls.PagedResultsControl;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.AttributeType;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.ConditionResult;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.Validator;

import static org.opends.messages.BackendMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class implements /dev/null like backend for development and
 * testing. The following behaviors of this backend implementation
 * should be noted:
 * <ul>
 * <li>All read operations return success but no data.
 * <li>All write operations return success but do nothing.
 * <li>Bind operations fail with invalid credentials.
 * <li>Compare operations are only possible on objectclass and return
 * true for the following objeclasses only: top, nullbackendobject,
 * extensibleobject. Otherwise comparison result is false or comparison
 * fails altogether.
 * <li>Controls are supported although this implementation does not
 * provide any specific emulation for controls. Generally known request
 * controls are accepted and default response controls returned where
 * applicable.
 * <li>Searches within this backend are always considered indexed.
 * <li>Backend Import is supported by iterating over ldif reader on a
 * single thread and issuing add operations which essentially do nothing
 * at all.
 * <li>Backend Export is supported but does nothing producing an empty
 * ldif.
 * <li>Backend Backup and Restore are not supported.
 * </ul>
 * This backend implementation is for development and testing only, does
 * not represent a complete and stable API, should be considered private
 * and subject to change without notice.
 */
public class NullBackend extends Backend
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();



  // The base DNs for this backend.
  private DN[] baseDNs;

  // The base DNs for this backend, in a hash set.
  private HashSet<DN> baseDNSet;

  // The set of supported controls for this backend.
  private HashSet<String> supportedControls;

  // The set of supported features for this backend.
  private HashSet<String> supportedFeatures;

  // The map of null entry object classes.
  private Map<ObjectClass,String> objectClasses;



  /**
   * Creates a new backend with the provided information.  All backend
   * implementations must implement a default constructor that use
   * <CODE>super()</CODE> to invoke this constructor.
   */
  public NullBackend()
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
  @Override()
  public void configureBackend(Configuration config)
         throws ConfigException
  {
    if (config != null)
    {
      Validator.ensureTrue(config instanceof BackendCfg);
      BackendCfg cfg = (BackendCfg)config;
      DN[] cfgBaseDNs = new DN[cfg.getBaseDN().size()];
      cfg.getBaseDN().toArray(cfgBaseDNs);
      setBaseDNs(cfgBaseDNs);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void initializeBackend()
       throws ConfigException, InitializationException
  {
    baseDNSet = new HashSet<DN>();
    for (DN dn : baseDNs)
    {
      baseDNSet.add(dn);
    }

    // Add supported controls.
    supportedControls = new HashSet<String>();
    supportedControls.add(OID_SUBTREE_DELETE_CONTROL);
    supportedControls.add(OID_PAGED_RESULTS_CONTROL);
    supportedControls.add(OID_MANAGE_DSAIT_CONTROL);
    supportedControls.add(OID_SERVER_SIDE_SORT_REQUEST_CONTROL);
    supportedControls.add(OID_VLV_REQUEST_CONTROL);

    // Add supported features.
    supportedFeatures = new HashSet<String>();

    // Register base DNs.
    for (DN dn : baseDNs)
    {
      try
      {
        DirectoryServer.registerBaseDN(dn, this, false);
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

    // Initialize null entry object classes.
    objectClasses = new HashMap<ObjectClass,String>();

    String topOCName = "top";
    ObjectClass topOC = DirectoryServer.getObjectClass(topOCName);
    if (topOC == null) {
      throw new InitializationException(Message.raw(
        Category.BACKEND, Severity.FATAL_ERROR,
        "Unable to locate " + topOCName +
        " objectclass in the current server schema"));
    }
    objectClasses.put(topOC, topOCName);

    String nulOCName = "nullbackendobject";
    ObjectClass nulOC = DirectoryServer.getDefaultObjectClass(nulOCName);
    try {
      DirectoryServer.registerObjectClass(nulOC, false);
    } catch (DirectoryException de) {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, de);
      }
      throw new InitializationException(de.getMessageObject());
    }
    objectClasses.put(nulOC, nulOCName);

    String extOCName = "extensibleobject";
    ObjectClass extOC = DirectoryServer.getObjectClass(extOCName);
    if (extOC == null) {
      throw new InitializationException(Message.raw(
        Category.BACKEND, Severity.FATAL_ERROR,
        "Unable to locate " + extOCName +
        " objectclass in the current server schema"));
    }
    objectClasses.put(extOC, extOCName);
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void finalizeBackend()
  {
    for (DN dn : baseDNs)
    {
      try
      {
        DirectoryServer.deregisterBaseDN(dn);
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
  @Override()
  public DN[] getBaseDNs()
  {
    return baseDNs;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public long getEntryCount()
  {
    return -1;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isLocal()
  {
    // For the purposes of this method, this is a local backend.
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isIndexed(AttributeType attributeType, IndexType indexType)
  {
    // All searches in this backend will always be considered indexed.
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConditionResult hasSubordinates(DN entryDN)
         throws DirectoryException
  {
    return ConditionResult.UNDEFINED;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public long numSubordinates(DN entryDN, boolean subtree)
         throws DirectoryException
  {
    return -1;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public Entry getEntry(DN entryDN)
  {
    return new Entry(null, objectClasses, null, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean entryExists(DN entryDN)
  {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public void addEntry(Entry entry, AddOperation addOperation)
         throws DirectoryException
  {
    return;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
         throws DirectoryException
  {
    return;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public void replaceEntry(Entry oldEntry, Entry newEntry,
      ModifyOperation modifyOperation) throws DirectoryException
  {
    return;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public void renameEntry(DN currentDN, Entry entry,
    ModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    return;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public void search(SearchOperation searchOperation)
         throws DirectoryException
  {
    PagedResultsControl pageRequest =
        searchOperation.getRequestControl(PagedResultsControl.DECODER);

    if (pageRequest != null) {
      // Indicate no more pages.
      PagedResultsControl control;
      control =
          new PagedResultsControl(pageRequest.isCritical(), 0, null);
      searchOperation.getResponseControls().add(control);
    }

    return;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public HashSet<String> getSupportedControls()
  {
    return supportedControls;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public HashSet<String> getSupportedFeatures()
  {
    return supportedFeatures;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsLDIFExport()
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public void exportLDIF(LDIFExportConfig exportConfig)
         throws DirectoryException
  {
    LDIFWriter ldifWriter;

    try {
      ldifWriter = new LDIFWriter(exportConfig);
    } catch (Exception e) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = Message.raw(
        Category.BACKEND, Severity.SEVERE_ERROR, e.getMessage());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
        message);
    }

    try {
      ldifWriter.close();
    } catch (Exception e) {
      if (debugEnabled()) {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsLDIFImport()
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig)
         throws DirectoryException
  {
    LDIFReader reader;
    try
    {
      reader = new LDIFReader(importConfig);
    }
    catch (Exception e)
    {
      Message message = Message.raw(
        Category.BACKEND, Severity.SEVERE_ERROR, e.getMessage());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }

    try
    {
      while (true)
      {
        Entry e = null;
        try
        {
          e = reader.readEntry();
          if (e == null)
          {
            break;
          }
        }
        catch (LDIFException le)
        {
          if (! le.canContinueReading())
          {
            Message message = Message.raw(
              Category.BACKEND, Severity.SEVERE_ERROR, le.getMessage());
            throw new DirectoryException(
              DirectoryServer.getServerErrorResultCode(),message);
          }
          else
          {
            continue;
          }
        }

        try
        {
          addEntry(e, null);
        }
        catch (DirectoryException de)
        {
          reader.rejectLastEntry(de.getMessageObject());
        }
      }

      return new LDIFImportResult(reader.getEntriesRead(),
                                  reader.getEntriesRejected(),
                                  reader.getEntriesIgnored());
    }
    catch (DirectoryException de)
    {
      throw de;
    }
    catch (Exception e)
    {
      Message message = Message.raw(
        Category.BACKEND, Severity.SEVERE_ERROR, e.getMessage());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }
    finally
    {
      reader.close();
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsBackup()
  {
    // This backend does not provide a backup/restore mechanism.
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsBackup(BackupConfig backupConfig,
                                StringBuilder unsupportedReason)
  {
    // This backend does not provide a backup/restore mechanism.
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public void createBackup(BackupConfig backupConfig)
         throws DirectoryException
  {
    Message message = Message.raw(
        Category.BACKEND, Severity.SEVERE_ERROR,
        "The null backend does not support backup operation");
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public void removeBackup(BackupDirectory backupDirectory,
                           String backupID)
         throws DirectoryException
  {
    Message message = Message.raw(
        Category.BACKEND, Severity.SEVERE_ERROR,
        "The null backend does not support remove backup operation");
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsRestore()
  {
    // This backend does not provide a backup/restore mechanism.
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public void restoreBackup(RestoreConfig restoreConfig)
         throws DirectoryException
  {
    Message message = Message.raw(
        Category.BACKEND, Severity.SEVERE_ERROR,
        "The null backend does not support restore operation");
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void preloadEntryCache() throws UnsupportedOperationException {
    throw new UnsupportedOperationException("Operation not supported.");
  }
}
