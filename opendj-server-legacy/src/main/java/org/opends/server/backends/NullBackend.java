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
 * Copyright 2008 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.backends;

import static org.forgerock.opendj.ldap.schema.CoreSchema.*;
import static org.opends.messages.BackendMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.ObjectClass;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.controls.PagedResultsControl;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.schema.ServerSchemaElement;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.RestoreConfig;
import org.opends.server.util.CollectionUtils;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;

/**
 * This class implements /dev/null like backend for development and testing.
 * The following behaviors of this backend implementation should be noted:
 * <ul>
 * <li>All read operations return success but no data.
 * <li>All write operations return success but do nothing.
 * <li>Bind operations fail with invalid credentials.
 * <li>Compare operations are only possible on objectclass and return
 * true for the following objectClasses only: top, nullbackendobject,
 * extensibleobject. Otherwise comparison result is false or comparison
 * fails altogether.
 * <li>Controls are supported although this implementation does not
 * provide any specific emulation for controls. Generally known request
 * controls are accepted and default response controls returned where applicable.
 * <li>Searches within this backend are always considered indexed.
 * <li>Backend Import is supported by iterating over ldif reader on a
 * single thread and issuing add operations which essentially do nothing at all.
 * <li>Backend Export is supported but does nothing producing an empty ldif.
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
  private Set<DN> baseDNs;

  /** The set of supported controls for this backend. */
  private final Set<String> supportedControls = CollectionUtils.newHashSet(
      OID_SUBTREE_DELETE_CONTROL,
      OID_PAGED_RESULTS_CONTROL,
      OID_MANAGE_DSAIT_CONTROL,
      OID_SERVER_SIDE_SORT_REQUEST_CONTROL,
      OID_VLV_REQUEST_CONTROL);

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

  @Override
  public void configureBackend(BackendCfg config, ServerContext serverContext) throws ConfigException
  {
    if (config != null)
    {
      this.baseDNs = config.getBaseDN();
    }
  }

  @Override
  public synchronized void openBackend() throws ConfigException, InitializationException
  {
    for (DN dn : baseDNs)
    {
      try
      {
        DirectoryServer.registerBaseDN(dn, this, false);
      }
      catch (Exception e)
      {
        logger.traceException(e);

        LocalizableMessage message = ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(dn, getExceptionMessage(e));
        throw new InitializationException(message, e);
      }
    }

    // Initialize null entry object classes.
    objectClasses = new HashMap<>();
    objectClasses.put(getTopObjectClass(), OC_TOP);
    objectClasses.put(getExtensibleObjectObjectClass(), "extensibleobject");

    String nulOCName = "nullbackendobject";
    ObjectClass nulOC = DirectoryServer.getSchema().getObjectClass(nulOCName);
    try {
      DirectoryServer.getSchema().registerObjectClass(nulOC, new ServerSchemaElement(nulOC).getSchemaFile(), false);
    } catch (DirectoryException de) {
      logger.traceException(de);
      throw new InitializationException(de.getMessageObject());
    }
    objectClasses.put(nulOC, nulOCName);
  }

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

  @Override
  public Set<DN> getBaseDNs()
  {
    return baseDNs;
  }

  @Override
  public long getEntryCount()
  {
    return -1;
  }

  @Override
  public boolean isIndexed(AttributeType attributeType, IndexType indexType)
  {
    // All searches in this backend will always be considered indexed.
    return true;
  }

  @Override
  public ConditionResult hasSubordinates(DN entryDN) throws DirectoryException
  {
    return ConditionResult.UNDEFINED;
  }

  @Override
  public long getNumberOfEntriesInBaseDN(DN baseDN) throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, ERR_NUM_SUBORDINATES_NOT_SUPPORTED.get());
  }

  @Override
  public long getNumberOfChildren(DN parentDN) throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, ERR_NUM_SUBORDINATES_NOT_SUPPORTED.get());
  }

  @Override
  public Entry getEntry(DN entryDN)
  {
    return new Entry(null, objectClasses, null, null);
  }

  @Override
  public boolean entryExists(DN entryDN)
  {
    return false;
  }

  @Override
  public void addEntry(Entry entry, AddOperation addOperation) throws DirectoryException
  {
    return;
  }

  @Override
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation) throws DirectoryException
  {
    return;
  }

  @Override
  public void replaceEntry(Entry oldEntry, Entry newEntry, ModifyOperation modifyOperation) throws DirectoryException
  {
    return;
  }

  @Override
  public void renameEntry(DN currentDN, Entry entry, ModifyDNOperation modifyDNOperation) throws DirectoryException
  {
    return;
  }

  @Override
  public void search(SearchOperation searchOperation) throws DirectoryException
  {
    PagedResultsControl pageRequest =
        searchOperation.getRequestControl(PagedResultsControl.DECODER);

    if (pageRequest != null) {
      // Indicate no more pages.
      PagedResultsControl control =
          new PagedResultsControl(pageRequest.isCritical(), 0, null);
      searchOperation.getResponseControls().add(control);
    }

    if (SearchScope.BASE_OBJECT.equals(searchOperation.getScope())
        && baseDNs.contains(searchOperation.getBaseDN()))
    {
      searchOperation.setResultCode(ResultCode.NO_SUCH_OBJECT);
    }
  }

  @Override
  public Set<String> getSupportedControls()
  {
    return supportedControls;
  }

  @Override
  public Set<String> getSupportedFeatures()
  {
    return Collections.emptySet();
  }

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

  @Override
  public void exportLDIF(LDIFExportConfig exportConfig) throws DirectoryException
  {
    try (LDIFWriter ldifWriter = new LDIFWriter(exportConfig))
    {
      // just create it to see if it fails
    } catch (Exception e) {
      logger.traceException(e);

      throw newDirectoryException(e);
    }
  }

  @Override
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig, ServerContext serverContext)
      throws DirectoryException
  {
    try (LDIFReader reader = getReader(importConfig))
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
          if (le.canContinueReading())
          {
            continue;
          }
          throw newDirectoryException(le);
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
      throw newDirectoryException(e);
    }
  }

  private DirectoryException newDirectoryException(Exception e)
  {
    LocalizableMessage message = LocalizableMessage.raw(e.getMessage());
    return new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
  }

  private LDIFReader getReader(LDIFImportConfig importConfig) throws DirectoryException
  {
    try
    {
      return new LDIFReader(importConfig);
    }
    catch (Exception e)
    {
      throw newDirectoryException(e);
    }
  }

  @Override
  public void createBackup(BackupConfig backupConfig) throws DirectoryException
  {
    throw unwillingToPerformOperation("backup");
  }

  @Override
  public void removeBackup(BackupDirectory backupDirectory, String backupID) throws DirectoryException
  {
    throw unwillingToPerformOperation("remove backup");
  }

  @Override
  public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException
  {
    throw unwillingToPerformOperation("restore");
  }

  private DirectoryException unwillingToPerformOperation(String operationName)
  {
    String msg = "The null backend does not support " + operationName + " operation";
    return new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, LocalizableMessage.raw(msg));
  }
}
