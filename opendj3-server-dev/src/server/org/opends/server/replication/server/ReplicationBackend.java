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
 *      Copyright 2007-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.replication.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.*;
import org.opends.server.api.Backend;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.backends.jeb.BackupManager;
import org.opends.server.config.ConfigException;
import org.opends.server.core.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.plugin.MultimasterReplication;
import org.opends.server.replication.plugin.ReplicationServerListener;
import org.opends.server.replication.protocol.*;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.util.*;
import org.forgerock.util.Reject;

import static java.util.Collections.*;

import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.JebMessages.*;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.types.FilterType.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines a backend that stores its information in an associated
 * replication server object.
 * <p>
 * This is primarily intended to take advantage of the backup/restore/
 * import/export of the backend API, and to provide an LDAP access to the
 * replication server database.
 * <p>
 * Entries stored in this backend are held in the DB associated with the
 * replication server.
 * <p>
 * Currently are only implemented the create and restore backup features.
 */
public class ReplicationBackend extends Backend
{
  private static final String CHANGE_NUMBER = "replicationChangeNumber";

  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private static final String BASE_DN = "dc=replicationchanges";

  /** The base DNs for this backend. */
  private DN[] baseDNs;

  /** The base DNs for this backend, in a hash set. */
  private Set<DN> baseDNSet;

  /** The set of supported controls for this backend. */
  private Set<String> supportedControls;

  /** The set of supported features for this backend. */
  private Set<String> supportedFeatures;

  private ReplicationServer server;

  /**
   * The number of milliseconds between job progress reports.
   */
  private long progressInterval = 10000;

  /**
   * The current number of entries exported.
   */
  private long exportedCount = 0;

  /**
   * The current number of entries skipped.
   */
  private long skippedCount = 0;

  /** Objectclass for getEntry root entries. */
  private Map<ObjectClass, String> rootObjectclasses;

  /** Attributes used for getEntry root entries. */
  private Map<AttributeType, List<Attribute>> attributes;

  /** Operational attributes used for getEntry root entries. */
  private Map<AttributeType,List<Attribute>> operationalAttributes;


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
   * {@inheritDoc}
   */
  @Override()
  public void configureBackend(Configuration config) throws ConfigException
  {
    if (config != null)
    {
      Reject.ifFalse(config instanceof BackendCfg);
      BackendCfg cfg = (BackendCfg) config;
      DN[] newBaseDNs = new DN[cfg.getBaseDN().size()];
      cfg.getBaseDN().toArray(newBaseDNs);
      this.baseDNs = newBaseDNs;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void initializeBackend()
       throws ConfigException, InitializationException
  {
    if ((baseDNs == null) || (baseDNs.length != 1))
    {
      LocalizableMessage message = ERR_MEMORYBACKEND_REQUIRE_EXACTLY_ONE_BASE.get();
      throw new ConfigException(message);
    }

    baseDNSet = new HashSet<DN>(Arrays.asList(baseDNs));

    supportedControls = new HashSet<String>();
    supportedFeatures = new HashSet<String>();

    for (DN dn : baseDNs)
    {
      try
      {
        DirectoryServer.registerBaseDN(dn, this, true);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        LocalizableMessage message = ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(
            dn.toString(), getExceptionMessage(e));
        throw new InitializationException(message, e);
      }
    }
    rootObjectclasses = new LinkedHashMap<ObjectClass,String>(3);
    rootObjectclasses.put(DirectoryServer.getTopObjectClass(), OC_TOP);
    ObjectClass domainOC = DirectoryServer.getObjectClass("domain", true);
    rootObjectclasses.put(domainOC, "domain");
    ObjectClass objectclassOC =
                   DirectoryServer.getObjectClass(ATTR_OBJECTCLASSES_LC, true);
    rootObjectclasses.put(objectclassOC, ATTR_OBJECTCLASSES_LC);

    attributes = new LinkedHashMap<AttributeType,List<Attribute>>();
    Attribute a = Attributes.create("changetype", "add");
    List<Attribute> attrList = new ArrayList<Attribute>(1);
    attrList.add(a);
    attributes.put(a.getAttributeType(), attrList);
    operationalAttributes = new LinkedHashMap<AttributeType,List<Attribute>>();
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
  public synchronized long getEntryCount()
  {
    if (server==null)
    {
      try
      {
        server = getReplicationServer();
        if (server == null)
        {
          return 0;
        }
      }
      catch(Exception e)
      {
        return 0;
      }
    }

    //This method only returns the number of actual change entries, the
    //domain and any baseDN entries are not counted.
    long retNum=0;
    for (ReplicationServerDomain rsd : toIterable(server.getDomainIterator()))
    {
      retNum += rsd.getChangesCount();
    }
    return retNum;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isLocal()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isIndexed(AttributeType attributeType, IndexType indexType)
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized Entry getEntry(DN entryDN)
  {
    try {
      if (baseDNSet.contains(entryDN)) {
           return new Entry(entryDN, rootObjectclasses, attributes,
                            operationalAttributes);
      }

      InternalClientConnection conn =
          InternalClientConnection.getRootConnection();
      SearchFilter filter =
          SearchFilter.createFilterFromString("(changetype=*)");
      InternalSearchOperation searchOp = new InternalSearchOperation(conn,
              InternalClientConnection.nextOperationID(),
              InternalClientConnection.nextMessageID(),
              null, entryDN, SearchScope.BASE_OBJECT,
              DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
              filter, null, null);
      search(searchOp);
      List<SearchResultEntry> resultEntries = searchOp.getSearchEntries();
      if (resultEntries.size() != 0)
      {
        return resultEntries.get(0);
      }
    }
    catch (DirectoryException ignored)
    {
    }
    return null;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized boolean entryExists(DN entryDN)
  {
   return getEntry(entryDN) != null;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void addEntry(Entry entry, AddOperation addOperation)
         throws DirectoryException
  {
    LocalizableMessage message = ERR_BACKUP_ADD_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void deleteEntry(DN entryDN,
                                       DeleteOperation deleteOperation)
         throws DirectoryException
  {
    LocalizableMessage message = ERR_BACKUP_DELETE_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void replaceEntry(Entry oldEntry, Entry newEntry,
                                        ModifyOperation modifyOperation)
         throws DirectoryException
  {
    LocalizableMessage message = ERR_BACKUP_MODIFY_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void renameEntry(DN currentDN, Entry entry,
                                       ModifyDNOperation modifyDNOperation)
         throws DirectoryException
  {
    LocalizableMessage message = ERR_BACKUP_MODIFY_DN_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Set<String> getSupportedControls()
  {
    return supportedControls;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public Set<String> getSupportedFeatures()
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
  public synchronized void exportLDIF(LDIFExportConfig exportConfig)
  throws DirectoryException
  {
    if(server == null) {
       LocalizableMessage message = ERR_REPLICATONBACKEND_EXPORT_LDIF_FAILED.get();
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,message);
    }

    final List<ReplicationServerDomain> exportedDomains =
        selectReplicationDomains(exportConfig.getIncludeBranches());

    // Make a note of the time we started.
    long startTime = System.currentTimeMillis();

    // Start a timer for the progress report.
    Timer timer = new Timer();
    TimerTask progressTask = new ProgressTask();
    timer.scheduleAtFixedRate(progressTask, progressInterval, progressInterval);

    // Create the LDIF writer.
    LDIFWriter ldifWriter;
    try
    {
      ldifWriter = new LDIFWriter(exportConfig);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      LocalizableMessage message =
        ERR_BACKEND_CANNOT_CREATE_LDIF_WRITER.get(String.valueOf(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          message, e);
    }

    exportRootChanges(exportedDomains, exportConfig, ldifWriter);

    try
    {
      for (ReplicationServerDomain domain : exportedDomains)
      {
        if (exportConfig.isCancelled())
        {
          break;
        }
        writeChangesAfterCSN(domain, exportConfig, ldifWriter, null, null);
      }
    }
    finally
    {
      timer.cancel();

      close(ldifWriter);
    }

    long finishTime = System.currentTimeMillis();
    long totalTime = finishTime - startTime;

    float rate = 0;
    if (totalTime > 0)
    {
      rate = 1000f*exportedCount / totalTime;
    }

    LocalizableMessage message = NOTE_JEB_EXPORT_FINAL_STATUS.get(
        exportedCount, skippedCount, totalTime/1000, rate);
    logError(message);
  }

  private List<ReplicationServerDomain> selectReplicationDomains(
      List<DN> includeBranches) throws DirectoryException
  {
    final List<ReplicationServerDomain> results =
        new ArrayList<ReplicationServerDomain>();
    final Iterable<ReplicationServerDomain> domains =
        toIterable(server.getDomainIterator());
    if (includeBranches == null || includeBranches.isEmpty())
    {
      for (ReplicationServerDomain domain : domains)
      {
        results.add(domain);
      }
      return results;
    }

    for (ReplicationServerDomain domain : domains)
    {
      DN baseDN = DN.valueOf(domain.getBaseDN() + "," + BASE_DN);
      for (DN includeBranch : includeBranches)
      {
        if (includeBranch.isDescendantOf(baseDN)
            || includeBranch.isAncestorOf(baseDN))
        {
          results.add(domain);
          break;
        }
      }
    }
    return results;
  }

  /**
   * Exports the root changes of the export, and one entry by domain.
   */
  private void exportRootChanges(List<ReplicationServerDomain> exportedDomains,
      final LDIFExportConfig exportConfig, LDIFWriter ldifWriter)
  {
    AttributeType ocType = DirectoryServer.getObjectClassAttributeType();
    AttributeBuilder builder = new AttributeBuilder(ocType);
    builder.add("top");
    builder.add("domain");
    Attribute ocAttr = builder.toAttribute();

    Map<AttributeType, List<Attribute>> attrs =
        new HashMap<AttributeType, List<Attribute>>();
    attrs.put(ocType, singletonList(ocAttr));

    try
    {
      ChangeRecordEntry changeRecord =
        new AddChangeRecordEntry(DN.valueOf(BASE_DN), attrs);
      ldifWriter.writeChangeRecord(changeRecord);
    }
    catch (Exception e) { /* do nothing */ }

    if (exportConfig == null)
    {
      return;
    }

    for (ReplicationServerDomain domain : exportedDomains)
    {
      if (exportConfig.isCancelled())
      {
        break;
      }

      final ServerState serverState = domain.getLatestServerState();
      TRACER.debugInfo("State=" + serverState);
      Attribute stateAttr = Attributes.create("state", serverState.toString());
      Attribute genidAttr = Attributes.create("generation-id",
          "" + domain.getGenerationId() + domain.getBaseDN());

      attrs.clear();
      attrs.put(ocType, singletonList(ocAttr));
      attrs.put(stateAttr.getAttributeType(), singletonList(stateAttr));
      attrs.put(genidAttr.getAttributeType(), singletonList(genidAttr));

      final String dnString = domain.getBaseDN() + "," + BASE_DN;
      try
      {
        DN dn = DN.valueOf(dnString);
        ldifWriter.writeChangeRecord(new AddChangeRecordEntry(dn, attrs));
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
        logError(ERR_BACKEND_EXPORT_ENTRY.get(dnString, String.valueOf(e)));
      }
    }
  }

  /**
   * Exports or returns all the changes from a ReplicationServerDomain coming
   * after the CSN specified in the searchOperation.
   */
  private void writeChangesAfterCSN(ReplicationServerDomain rsDomain,
      final LDIFExportConfig exportConfig, LDIFWriter ldifWriter,
      SearchOperation searchOperation, final CSN previousCSN)
      throws DirectoryException
  {
    if (exportConfig != null && exportConfig.isCancelled())
    { // Abort if cancelled
      return;
    }

    DBCursor<UpdateMsg> cursor = null;
    try
    {
      cursor = rsDomain.getCursorFrom(previousCSN);
      int lookthroughCount = 0;

      // Walk through the changes
      cursor.next(); // first try to advance the cursor
      while (cursor.getRecord() != null)
      {
        if (exportConfig != null && exportConfig.isCancelled())
        { // abort if cancelled
          return;
        }
        if (!canContinue(searchOperation, lookthroughCount))
        {
          break;
        }
        lookthroughCount++;
        writeChange(cursor.getRecord(), ldifWriter, searchOperation,
            rsDomain.getBaseDN(), exportConfig != null);
        cursor.next();
      }
    }
    catch (ChangelogException e)
    {
      throw new DirectoryException(ResultCode.OPERATIONS_ERROR, e);
    }
    finally
    {
      close(cursor);
    }
  }

  private boolean canContinue(SearchOperation searchOperation,
      int lookthroughCount)
  {
    if (searchOperation == null)
    {
      return true;
    }

    int limit = searchOperation.getClientConnection().getLookthroughLimit();
    if (lookthroughCount > limit && limit > 0)
    {
      // lookthrough limit exceeded
      searchOperation.setResultCode(ResultCode.ADMIN_LIMIT_EXCEEDED);
      searchOperation.setErrorMessage(null);
      return false;
    }

    try
    {
      searchOperation.checkIfCanceled(false);
      return true;
    }
    catch (CanceledOperationException e)
    {
      searchOperation.setResultCode(ResultCode.CANCELED);
      searchOperation.setErrorMessage(null);
      return false;
    }
  }

  private CSN extractCSN(SearchOperation searchOperation)
  {
    if (searchOperation != null)
    {
      return extractCSN(searchOperation.getFilter());
    }
    return null;
  }

  /**
   * Attempt to extract a CSN from searchFilter like
   * ReplicationChangeNumber=xxxx or ReplicationChangeNumber>=xxxx.
   *
   * @param filter
   *          The filter to evaluate.
   * @return The extracted CSN or null if no CSN was found.
   */
  private CSN extractCSN(SearchFilter filter)
  {
    // Try to optimize for filters like replicationChangeNumber>=xxxxx
    // or replicationChangeNumber=xxxxx :
    // If the search filter is one of these 2 filters, move directly to
    // ChangeNumber=xxxx before starting the iteration.
    final FilterType filterType = filter.getFilterType();
    if (GREATER_OR_EQUAL.equals(filterType) || EQUALITY.equals(filterType))
    {
      AttributeType changeNumberAttrType =
          DirectoryServer.getDefaultAttributeType(CHANGE_NUMBER);
      if (filter.getAttributeType().equals(changeNumberAttrType))
      {
        try
        {
          CSN startingCSN =
             new CSN(filter.getAssertionValue().getValue().toString());
          return new CSN(startingCSN.getTime(),
              startingCSN.getSeqnum() - 1, startingCSN.getServerId());
        }
        catch (Exception e)
        {
          // don't try to optimize the search if the ChangeNumber is
          // not a valid replication CSN.
        }
      }
    }
    else if (AND.equals(filterType))
    {
      for (SearchFilter filterComponent : filter.getFilterComponents())
      {
        // This code does not expect more than one CSN in the search filter.
        // It is ok, since it is only used by developers/testers for debugging.
        final CSN previousCSN = extractCSN(filterComponent);
        if (previousCSN != null)
        {
          return previousCSN;
        }
      }
    }
    return null;
  }


  /**
   * Exports one change.
   */
  private void writeChange(UpdateMsg updateMsg, LDIFWriter ldifWriter,
      SearchOperation searchOperation, DN baseDN, boolean isExport)
  {
    InternalClientConnection conn =
      InternalClientConnection.getRootConnection();
    Entry entry = null;
    DN dn = null;

    ObjectClass extensibleObjectOC =
      DirectoryServer.getDefaultObjectClass("extensibleObject");

    try
    {
      if (updateMsg instanceof LDAPUpdateMsg)
      {
        LDAPUpdateMsg msg = (LDAPUpdateMsg) updateMsg;

        if (msg instanceof AddMsg)
        {
          AddMsg addMsg = (AddMsg)msg;
          AddOperation addOperation = (AddOperation)msg.createOperation(conn);

          dn = DN.valueOf("puid=" + addMsg.getParentEntryUUID() + "+" +
              CHANGE_NUMBER + "=" + msg.getCSN() + "+" +
              msg.getDN() + "," + BASE_DN);

          Map<AttributeType,List<Attribute>> attrs =
            new HashMap<AttributeType,List<Attribute>>();
          Map<ObjectClass, String> objectclasses =
            new HashMap<ObjectClass, String>();

          for (RawAttribute a : addOperation.getRawAttributes())
          {
            Attribute attr = a.toAttribute();
            if (attr.getAttributeType().isObjectClassType())
            {
              for (ByteString os : a.getValues())
              {
                String ocName = os.toString();
                ObjectClass oc =
                  DirectoryServer.getObjectClass(toLowerCase(ocName));
                if (oc == null)
                {
                  oc = DirectoryServer.getDefaultObjectClass(ocName);
                }

                objectclasses.put(oc,ocName);
              }
            }
            else
            {
              addAttribute(attrs, attr);
            }
          }
          addAttribute(attrs, "changetype", "add");

          if (isExport)
          {
            ldifWriter.writeChangeRecord(new AddChangeRecordEntry(dn, attrs));
          }
          else
          {
            entry = new Entry(dn, objectclasses, attrs, null);
          }
        }
        else if (msg instanceof DeleteMsg)
        {
          dn = computeDN(msg);
          ChangeRecordEntry changeRecord = new DeleteChangeRecordEntry(dn);
          entry = writeChangeRecord(ldifWriter, changeRecord, isExport);
        }
        else if (msg instanceof ModifyMsg)
        {
          ModifyOperation op = (ModifyOperation)msg.createOperation(conn);

          dn = computeDN(msg);
          ChangeRecordEntry changeRecord =
            new ModifyChangeRecordEntry(dn, op.getRawModifications());
          entry = writeChangeRecord(ldifWriter, changeRecord, isExport);
        }
        else if (msg instanceof ModifyDNMsg)
        {
          ModifyDNOperation op = (ModifyDNOperation)msg.createOperation(conn);

          dn = computeDN(msg);
          ChangeRecordEntry changeRecord = new ModifyDNChangeRecordEntry(
              dn, op.getNewRDN(), op.deleteOldRDN(), op.getNewSuperior());
          entry = writeChangeRecord(ldifWriter, changeRecord, isExport);
        }


        if (isExport)
        {
          this.exportedCount++;
        }
        else
        {
          // Add extensibleObject objectclass and the ChangeNumber in the entry.
          if (!entry.getObjectClasses().containsKey(extensibleObjectOC))
            entry.addObjectClass(extensibleObjectOC);

          addAttribute(entry.getUserAttributes(), CHANGE_NUMBER,
              msg.getCSN().toString());
          addAttribute(entry.getUserAttributes(), "replicationDomain",
              baseDN.toNormalizedString());

          // Get the base DN, scope, and filter for the search.
          DN     searchBaseDN = searchOperation.getBaseDN();
          SearchScope  scope  = searchOperation.getScope();
          SearchFilter filter = searchOperation.getFilter();

          if (entry.matchesBaseAndScope(searchBaseDN, scope)
              && filter.matchesEntry(entry))
          {
            searchOperation.returnEntry(entry, new LinkedList<Control>());
          }
        }
      }
    }
    catch (Exception e)
    {
      this.skippedCount++;
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      final String dnStr = (dn != null) ? dn.toNormalizedString() : "Unknown";

      LocalizableMessage message;
      if (isExport)
      {
        message = ERR_BACKEND_EXPORT_ENTRY.get(dnStr, String.valueOf(e));
      }
      else
      {
        message = ERR_BACKEND_SEARCH_ENTRY.get(dnStr, e.getLocalizedMessage());
      }
      logError(message);
    }
  }

  private DN computeDN(LDAPUpdateMsg msg) throws DirectoryException
  {
    return DN.valueOf("uuid=" + msg.getEntryUUID() + "," + CHANGE_NUMBER + "="
        + msg.getCSN() + "," + msg.getDN() + "," + BASE_DN);
  }

  private Entry writeChangeRecord(LDIFWriter ldifWriter,
      ChangeRecordEntry changeRecord, boolean isExport) throws IOException,
      LDIFException
  {
    if (isExport)
    {
      ldifWriter.writeChangeRecord(changeRecord);
      return null;
    }

    final Writer writer = new Writer();
    writer.getLDIFWriter().writeChangeRecord(changeRecord);
    return writer.getLDIFReader().readEntry();
  }

  private void addAttribute(Map<AttributeType, List<Attribute>> attributes,
      String attrName, String attrValue)
  {
    addAttribute(attributes, Attributes.create(attrName, attrValue));
  }

  /**
   * Add an attribute to a provided Map of attribute.
   *
   * @param attributes The Map that should be updated.
   * @param attribute  The attribute that should be added to the Map.
   */
  private void addAttribute(
      Map<AttributeType,List<Attribute>> attributes, Attribute attribute)
  {
    AttributeType attrType = attribute.getAttributeType();
    List<Attribute> attrs = attributes.get(attrType);
    if (attrs == null)
    {
      attrs = new ArrayList<Attribute>(1);
      attrs.add(attribute);
      attributes.put(attrType, attrs);
    }
    else
    {
      attrs.add(attribute);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsLDIFImport()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized LDIFImportResult importLDIF(LDIFImportConfig importConfig)
         throws DirectoryException
  {
    LocalizableMessage message = ERR_REPLICATONBACKEND_IMPORT_LDIF_NOT_SUPPORTED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsBackup()
  {
    // This backend does not provide a backup/restore mechanism.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsBackup(BackupConfig backupConfig,
                                StringBuilder unsupportedReason)
  {
    return true;
  }

  /** {@inheritDoc} */
  @Override()
  public void createBackup(BackupConfig backupConfig)
         throws DirectoryException
  {
    createBackupManager().createBackup(getBackendDir(), backupConfig);
  }

  /** {@inheritDoc} */
  @Override()
  public void restoreBackup(RestoreConfig restoreConfig)
         throws DirectoryException
  {
    createBackupManager().restoreBackup(getBackendDir(), restoreConfig);
  }

  /** {@inheritDoc} */
  @Override()
  public void removeBackup(BackupDirectory backupDirectory, String backupID)
      throws DirectoryException
  {
    createBackupManager().removeBackup(backupDirectory, backupID);
  }

  private BackupManager createBackupManager()
  {
    return new BackupManager(getBackendID());
  }

  private File getBackendDir() throws DirectoryException
  {
   return getFileForPath(getReplicationServerCfg().getReplicationDBDirectory());
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsRestore()
  {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public long numSubordinates(DN entryDN, boolean subtree)
      throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                 ERR_NUM_SUBORDINATES_NOT_SUPPORTED.get());
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ConditionResult hasSubordinates(DN entryDN)
        throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                 ERR_HAS_SUBORDINATES_NOT_SUPPORTED.get());
  }

  /**
   * Set the replication server associated with this backend.
   * @param server The replication server.
   */
  public void setServer(ReplicationServer server)
  {
    this.server = server;
  }

  /**
   * This class reports progress of the export job at fixed intervals.
   */
  private final class ProgressTask extends TimerTask
  {
    /**
     * The number of entries that had been exported at the time of the
     * previous progress report.
     */
    private long previousCount = 0;

    /**
     * The time in milliseconds of the previous progress report.
     */
    private long previousTime;

    /**
     * Create a new export progress task.
     */
    public ProgressTask()
    {
      previousTime = System.currentTimeMillis();
    }

    /**
     * The action to be performed by this timer task.
     */
    @Override
    public void run()
    {
      long latestCount = exportedCount;
      long deltaCount = latestCount - previousCount;
      long latestTime = System.currentTimeMillis();
      long deltaTime = latestTime - previousTime;

      if (deltaTime == 0)
      {
        return;
      }

      float rate = 1000f*deltaCount / deltaTime;

      LocalizableMessage message =
          NOTE_JEB_EXPORT_PROGRESS_REPORT.get(latestCount, skippedCount, rate);
      logError(message);

      previousCount = latestCount;
      previousTime = latestTime;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public synchronized void search(SearchOperation searchOperation)
         throws DirectoryException
  {
    //This check is for GroupManager initialization. It currently doesn't
    //come into play because the replication server variable is null in
    //the check above. But if the order of initialization of the server variable
    //is ever changed, the following check will keep replication change entries
    //from being added to the groupmanager cache erroneously.
    List<Control> requestControls = searchOperation.getRequestControls();
    if (requestControls != null)
    {
      for (Control c : requestControls)
      {
        if (OID_INTERNAL_GROUP_MEMBERSHIP_UPDATE.equals(c.getOID()))
        {
          return;
        }
      }
    }

    // don't do anything if the search is a base search on the backend suffix.
    try
    {
      DN backendBaseDN = DN.valueOf(BASE_DN);
      if ( searchOperation.getScope().equals(SearchScope.BASE_OBJECT) &&
           backendBaseDN.equals(searchOperation.getBaseDN()) )
      {
        return;
      }
    }
    catch (Exception e)
    {
      return;
    }

    // Make sure the base entry exists if it's supposed to be in this backend.
    final DN searchBaseDN = searchOperation.getBaseDN();
    if (!handlesEntry(searchBaseDN))
    {
      DN matchedDN = searchBaseDN.getParentDNInSuffix();
      while (matchedDN != null)
      {
        if (handlesEntry(matchedDN))
        {
          break;
        }
        matchedDN = matchedDN.getParentDNInSuffix();
      }

      LocalizableMessage message = ERR_REPLICATIONBACKEND_ENTRY_DOESNT_EXIST.
        get(String.valueOf(searchBaseDN));
      throw new DirectoryException(
          ResultCode.NO_SUCH_OBJECT, message, matchedDN, null);
    }

    if (server==null)
    {
      server = getReplicationServer();
      if (server == null)
      {
        if (!baseDNSet.contains(searchBaseDN))
        {
          LocalizableMessage message = ERR_REPLICATIONBACKEND_ENTRY_DOESNT_EXIST.get(
              String.valueOf(searchBaseDN));
          throw new DirectoryException(
              ResultCode.NO_SUCH_OBJECT, message, null, null);
        }
        return;
      }
    }

    // Walk through all entries and send the ones that match.
    final List<ReplicationServerDomain> searchedDomains =
        selectReplicationDomains(Collections.singletonList(searchBaseDN));
    for (ReplicationServerDomain domain : searchedDomains)
    {
      final CSN previousCSN = extractCSN(searchOperation);
      writeChangesAfterCSN(domain, null, null, searchOperation, previousCSN);
    }
  }

  /**
   * Retrieves the replication server associated to this backend.
   *
   * @return The server retrieved
   * @throws DirectoryException When it occurs.
   */
  private ReplicationServer getReplicationServer() throws DirectoryException
  {
    for (SynchronizationProvider<?> provider :
      DirectoryServer.getSynchronizationProviders())
    {
      if (provider instanceof MultimasterReplication)
      {
        MultimasterReplication mmp = (MultimasterReplication)provider;
        ReplicationServerListener list = mmp.getReplicationServerListener();
        if (list != null)
        {
          return list.getReplicationServer();
        }
      }
    }
    return null;
  }

  /**
   * Find the replication server configuration associated with this replication
   * backend.
   */
  private ReplicationServerCfg getReplicationServerCfg()
      throws DirectoryException {
    RootCfg root = ServerManagementContext.getInstance().getRootConfiguration();

    for (String name : root.listSynchronizationProviders()) {
      SynchronizationProviderCfg syncCfg;
      try {
        syncCfg = root.getSynchronizationProvider(name);
      } catch (ConfigException e) {
        throw new DirectoryException(ResultCode.OPERATIONS_ERROR,
            ERR_REPLICATION_SERVER_CONFIG_NOT_FOUND.get(), e);
      }
      if (syncCfg instanceof ReplicationSynchronizationProviderCfg) {
        ReplicationSynchronizationProviderCfg scfg =
          (ReplicationSynchronizationProviderCfg) syncCfg;
        try {
          return scfg.getReplicationServer();
        } catch (ConfigException e) {
          throw new DirectoryException(ResultCode.OPERATIONS_ERROR,
              ERR_REPLICATION_SERVER_CONFIG_NOT_FOUND.get(), e);
        }
      }
    }

    // No replication server found.
    throw new DirectoryException(ResultCode.OPERATIONS_ERROR,
        ERR_REPLICATION_SERVER_CONFIG_NOT_FOUND.get());
  }

  /**
   * Writer class to read/write from/to a bytearray.
   */
  private static final class Writer
  {
    /** The underlying output stream. */
    private final ByteArrayOutputStream stream;

    /** The underlying LDIF config. */
    private final LDIFExportConfig config;

    /** The LDIF writer. */
    private final LDIFWriter writer;

    /**
     * Create a new string writer.
     */
    public Writer() {
      this.stream = new ByteArrayOutputStream();
      this.config = new LDIFExportConfig(stream);
      try {
        this.writer = new LDIFWriter(config);
      } catch (IOException e) {
        // Should not happen.
        throw new RuntimeException(e);
      }
    }

    /**
     * Get the LDIF writer.
     *
     * @return Returns the LDIF writer.
     */
    public LDIFWriter getLDIFWriter() {
      return writer;
    }



    /**
     * Close the writer and get an LDIF reader for the LDIF content.
     *
     * @return Returns an LDIF Reader.
     * @throws IOException
     *           If an error occurred closing the writer.
     */
    public LDIFReader getLDIFReader() throws IOException {
      writer.close();
      String ldif = stream.toString("UTF-8");
      ldif = ldif.replace("\n-\n", "\n");
      ByteArrayInputStream istream = new ByteArrayInputStream(ldif.getBytes());
      LDIFImportConfig newConfig = new LDIFImportConfig(istream);
      // ReplicationBackend may contain entries that are not schema
      // compliant. Let's ignore them for now.
      newConfig.setValidateSchema(false);
      return new LDIFReader(newConfig);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void preloadEntryCache() throws UnsupportedOperationException {
    throw new UnsupportedOperationException("Operation not supported.");
  }
}
