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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.backends;

import static org.opends.messages.BackendMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.admin.std.server.BackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.controls.PagedResultsControl;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.types.AttributeType;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.RestoreConfig;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;

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
public class NullBackend extends Backend<BackendCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();



  /** The base DNs for this backend. */
  private DN[] baseDNs;

  /** The base DNs for this backend, in a hash set. */
  private HashSet<DN> baseDNSet;

  /** The set of supported controls for this backend. */
  private final Set<String> supportedControls = new HashSet<String>(Arrays.asList(
      OID_SUBTREE_DELETE_CONTROL,
      OID_PAGED_RESULTS_CONTROL,
      OID_MANAGE_DSAIT_CONTROL,
      OID_SERVER_SIDE_SORT_REQUEST_CONTROL,
      OID_VLV_REQUEST_CONTROL));

  /** The map of null entry object classes. */
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

  /** {@inheritDoc} */
  @Override
  public void configureBackend(BackendCfg config, ServerContext serverContext) throws ConfigException
  {
    if (config != null)
    {
      BackendCfg cfg = config;
      DN[] cfgBaseDNs = new DN[cfg.getBaseDN().size()];
      cfg.getBaseDN().toArray(cfgBaseDNs);
      setBaseDNs(cfgBaseDNs);
    }
  }

  /** {@inheritDoc} */
  @Override
  public synchronized void openBackend() throws ConfigException, InitializationException
  {
    baseDNSet = new HashSet<DN>();
    for (DN dn : baseDNs)
    {
      baseDNSet.add(dn);
    }

    // Register base DNs.
    for (DN dn : baseDNs)
    {
      try
      {
        DirectoryServer.registerBaseDN(dn, this, false);
      }
      catch (Exception e)
      {
        logger.traceException(e);

        LocalizableMessage message = ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(
            dn, getExceptionMessage(e));
        throw new InitializationException(message, e);
      }
    }

    // Initialize null entry object classes.
    objectClasses = new HashMap<ObjectClass,String>();

    String topOCName = "top";
    ObjectClass topOC = DirectoryServer.getObjectClass(topOCName);
    if (topOC == null) {
      throw new InitializationException(LocalizableMessage.raw("Unable to locate " + topOCName +
        " objectclass in the current server schema"));
    }
    objectClasses.put(topOC, topOCName);

    String nulOCName = "nullbackendobject";
    ObjectClass nulOC = DirectoryServer.getDefaultObjectClass(nulOCName);
    try {
      DirectoryServer.registerObjectClass(nulOC, false);
    } catch (DirectoryException de) {
      logger.traceException(de);
      throw new InitializationException(de.getMessageObject());
    }
    objectClasses.put(nulOC, nulOCName);

    String extOCName = "extensibleobject";
    ObjectClass extOC = DirectoryServer.getObjectClass(extOCName);
    if (extOC == null) {
      throw new InitializationException(LocalizableMessage.raw("Unable to locate " + extOCName +
        " objectclass in the current server schema"));
    }
    objectClasses.put(extOC, extOCName);
  }

  /** {@inheritDoc} */
  @Override
  public synchronized void closeBackend()
  {
    for (DN dn : baseDNs)
    {
      try
      {
        DirectoryServer.deregisterBaseDN(dn);
      }
      catch (Exception e)
      {
        logger.traceException(e);
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
  public long getEntryCount()
  {
    return -1;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isIndexed(AttributeType attributeType, IndexType indexType)
  {
    // All searches in this backend will always be considered indexed.
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public ConditionResult hasSubordinates(DN entryDN)
         throws DirectoryException
  {
    return ConditionResult.UNDEFINED;
  }

  /** {@inheritDoc} */
  @Override
  public long getNumberOfEntriesInBaseDN(DN baseDN) throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, ERR_NUM_SUBORDINATES_NOT_SUPPORTED.get());
  }

  /** {@inheritDoc} */
  @Override
  public long getNumberOfChildren(DN parentDN) throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, ERR_NUM_SUBORDINATES_NOT_SUPPORTED.get());
  }

  /** {@inheritDoc} */
  @Override
  public Entry getEntry(DN entryDN)
  {
    return new Entry(null, objectClasses, null, null);
  }

  /** {@inheritDoc} */
  @Override
  public boolean entryExists(DN entryDN)
  {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void addEntry(Entry entry, AddOperation addOperation)
         throws DirectoryException
  {
    return;
  }

  /** {@inheritDoc} */
  @Override
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
         throws DirectoryException
  {
    return;
  }

  /** {@inheritDoc} */
  @Override
  public void replaceEntry(Entry oldEntry, Entry newEntry,
      ModifyOperation modifyOperation) throws DirectoryException
  {
    return;
  }

  /** {@inheritDoc} */
  @Override
  public void renameEntry(DN currentDN, Entry entry,
    ModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    return;
  }

  /** {@inheritDoc} */
  @Override
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
  public boolean supports(BackendOperation backendOperation)
  {
    switch (backendOperation)
    {
    case LDIF_EXPORT:
    case LDIF_IMPORT:
      return true;

    default:
      return false;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void exportLDIF(LDIFExportConfig exportConfig)
         throws DirectoryException
  {
    LDIFWriter ldifWriter;

    try {
      ldifWriter = new LDIFWriter(exportConfig);
    } catch (Exception e) {
      logger.traceException(e);

      LocalizableMessage message = LocalizableMessage.raw(e.getMessage());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
        message);
    }

    close(ldifWriter);
  }

  /** {@inheritDoc} */
  @Override
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig, ServerContext serverContext)
      throws DirectoryException
  {
    LDIFReader reader;
    try
    {
      reader = new LDIFReader(importConfig);
    }
    catch (Exception e)
    {
      LocalizableMessage message = LocalizableMessage.raw(e.getMessage());
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
            LocalizableMessage message = LocalizableMessage.raw(le.getMessage());
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
      LocalizableMessage message = LocalizableMessage.raw(e.getMessage());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }
    finally
    {
      reader.close();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void createBackup(BackupConfig backupConfig)
         throws DirectoryException
  {
    LocalizableMessage message = LocalizableMessage.raw("The null backend does not support backup operation");
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }

  /** {@inheritDoc} */
  @Override
  public void removeBackup(BackupDirectory backupDirectory,
                           String backupID)
         throws DirectoryException
  {
    LocalizableMessage message = LocalizableMessage.raw("The null backend does not support remove backup operation");
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }

  /** {@inheritDoc} */
  @Override
  public void restoreBackup(RestoreConfig restoreConfig)
         throws DirectoryException
  {
    LocalizableMessage message = LocalizableMessage.raw("The null backend does not support restore operation");
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }
}
