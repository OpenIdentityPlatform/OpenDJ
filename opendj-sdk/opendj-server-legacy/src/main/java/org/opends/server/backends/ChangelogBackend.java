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
 *      Copyright 2014-2015 ForgeRock AS.
 */
package org.opends.server.backends;

import static org.opends.messages.BackendMessages.*;
import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.replication.plugin.MultimasterReplication.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.KeyMatchingStrategy.*;
import static org.opends.server.replication.server.changelog.api.DBCursor.PositionStrategy.*;
import static org.opends.server.util.LDIFWriter.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.opends.server.admin.Configuration;
import org.opends.server.api.Backend;
import org.opends.server.config.ConfigConstants;
import org.opends.server.controls.EntryChangelogNotificationControl;
import org.opends.server.controls.ExternalChangelogRequestControl;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.PersistentSearch;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.replication.common.CSN;
import org.opends.server.replication.common.MultiDomainServerState;
import org.opends.server.replication.common.ServerState;
import org.opends.server.replication.protocol.AddMsg;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.LDAPUpdateMsg;
import org.opends.server.replication.protocol.ModifyCommonMsg;
import org.opends.server.replication.protocol.ModifyDNMsg;
import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.ReplicationServer;
import org.opends.server.replication.server.ReplicationServerDomain;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexDB;
import org.opends.server.replication.server.changelog.api.ChangeNumberIndexRecord;
import org.opends.server.replication.server.changelog.api.ChangelogDB;
import org.opends.server.replication.server.changelog.api.ChangelogException;
import org.opends.server.replication.server.changelog.api.DBCursor;
import org.opends.server.replication.server.changelog.api.ReplicaId;
import org.opends.server.replication.server.changelog.api.ReplicationDomainDB;
import org.opends.server.replication.server.changelog.file.ECLEnabledDomainPredicate;
import org.opends.server.replication.server.changelog.file.ECLMultiDomainDBCursor;
import org.opends.server.replication.server.changelog.file.MultiDomainDBCursor;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attributes;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.CanceledOperationException;
import org.opends.server.types.Control;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.FilterType;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.Modification;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.Privilege;
import org.opends.server.types.RDN;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.WritabilityMode;
import org.opends.server.util.StaticUtils;

/**
 * A backend that provides access to the changelog, i.e. the "cn=changelog"
 * suffix. It is a read-only backend that is created by a
 * {@code ReplicationServer} and is not configurable.
 * <p>
 * There are two modes to search the changelog:
 * <ul>
 * <li>Cookie mode: when a "ECL Cookie Exchange Control" is provided with the
 * request. The cookie provided in the control is used to retrieve entries from
 * the ReplicaDBs. The <code>changeNumber</code> attribute is not returned with
 * the entries.</li>
 * <li>Change number mode: when no "ECL Cookie Exchange Control" is provided
 * with the request. The entries are retrieved using the ChangeNumberIndexDB and
 * their attributes are set with the information from the ReplicasDBs. The
 * <code>changeNumber</code> attribute value is set from the content of
 * ChangeNumberIndexDB.</li>
 * </ul>
 * <h3>Searches flow</h3>
 * <p>
 * Here is the flow of searches within the changelog backend APIs:
 * <ul>
 * <li>Normal searches only go through:
 * <ol>
 * <li>{@link ChangelogBackend#search(SearchOperation)} (once, single threaded)</li>
 * </ol>
 * </li>
 * <li>Persistent searches with <code>changesOnly=false</code> go through:
 * <ol>
 * <li>{@link ChangelogBackend#registerPersistentSearch(PersistentSearch)}
 * (once, single threaded),</li>
 * <li>
 * {@link ChangelogBackend#search(SearchOperation)} (once, single threaded)</li>
 * <li>{@link ChangelogBackend#notify*EntryAdded()} (multiple times, multi
 * threaded)</li>
 * </ol>
 * </li>
 * <li>Persistent searches with <code>changesOnly=true</code> go through:
 * <ol>
 * <li>{@link ChangelogBackend#registerPersistentSearch(PersistentSearch)}
 * (once, single threaded)</li>
 * <li>
 * {@link ChangelogBackend#notify*EntryAdded()} (multiple times, multi
 * threaded)</li>
 * </ol>
 * </li>
 * </ul>
 *
 * @see ReplicationServer
 */
public class ChangelogBackend extends Backend<Configuration>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The id of this backend. */
  public static final String BACKEND_ID = "changelog";

  private static final long CHANGE_NUMBER_FOR_EMPTY_CURSOR = 0L;

  private static final String CHANGE_NUMBER_ATTR = "changeNumber";
  private static final String CHANGE_NUMBER_ATTR_LC = CHANGE_NUMBER_ATTR.toLowerCase();
  private static final String ENTRY_SENDER_ATTACHMENT = OID_ECL_COOKIE_EXCHANGE_CONTROL + ".entrySender";

  /** The set of objectclasses that will be used in root entry. */
  private static final Map<ObjectClass, String>
    CHANGELOG_ROOT_OBJECT_CLASSES = new LinkedHashMap<ObjectClass, String>(2);

  static
  {
    CHANGELOG_ROOT_OBJECT_CLASSES.put(DirectoryServer.getObjectClass(OC_TOP, true), OC_TOP);
    CHANGELOG_ROOT_OBJECT_CLASSES.put(DirectoryServer.getObjectClass("container", true), "container");
  }

  /** The set of objectclasses that will be used in ECL entries. */
  private static final Map<ObjectClass, String>
    CHANGELOG_ENTRY_OBJECT_CLASSES = new LinkedHashMap<ObjectClass, String>(2);

  static
  {
    CHANGELOG_ENTRY_OBJECT_CLASSES.put(DirectoryServer.getObjectClass(OC_TOP, true), OC_TOP);
    CHANGELOG_ENTRY_OBJECT_CLASSES.put(DirectoryServer.getObjectClass(OC_CHANGELOG_ENTRY, true), OC_CHANGELOG_ENTRY);
  }

  /** The attribute type for the "creatorsName" attribute. */
  private static final AttributeType CREATORS_NAME_TYPE =
      DirectoryConfig.getAttributeType(OP_ATTR_CREATORS_NAME_LC, true);

  /** The attribute type for the "modifiersName" attribute. */
  private static final AttributeType MODIFIERS_NAME_TYPE =
      DirectoryConfig.getAttributeType(OP_ATTR_MODIFIERS_NAME_LC, true);

  /** The base DN for the external change log. */
  public static final DN CHANGELOG_BASE_DN;

  static
  {
    try
    {
      CHANGELOG_BASE_DN = DN.valueOf(DN_EXTERNAL_CHANGELOG_ROOT);
    }
    catch (DirectoryException e)
    {
      throw new RuntimeException(e);
    }
  }

  /** The set of base DNs for this backend. */
  private DN[] baseDNs;
  /** The set of supported controls for this backend. */
  private final Set<String> supportedControls = Collections.singleton(OID_ECL_COOKIE_EXCHANGE_CONTROL);
  /** Whether the base changelog entry has subordinates. */
  private Boolean baseEntryHasSubordinates;

  /** The replication server on which the changelog is read. */
  private final ReplicationServer replicationServer;
  private final ECLEnabledDomainPredicate domainPredicate;

  /** The set of cookie-based persistent searches registered with this backend. */
  private final ConcurrentLinkedQueue<PersistentSearch> cookieBasedPersistentSearches =
      new ConcurrentLinkedQueue<PersistentSearch>();
  /**
   * The set of change number-based persistent searches registered with this
   * backend.
   */
  private final ConcurrentLinkedQueue<PersistentSearch> changeNumberBasedPersistentSearches =
      new ConcurrentLinkedQueue<PersistentSearch>();

  /**
   * Creates a new backend with the provided replication server.
   *
   * @param replicationServer
   *          The replication server on which the changes are read.
   * @param domainPredicate
   *          Returns whether a domain is enabled for the external changelog.
   */
  public ChangelogBackend(final ReplicationServer replicationServer, final ECLEnabledDomainPredicate domainPredicate)
  {
    this.replicationServer = replicationServer;
    this.domainPredicate = domainPredicate;
    setBackendID(BACKEND_ID);
    setWritabilityMode(WritabilityMode.DISABLED);
    setPrivateBackend(true);
  }

  private ChangelogDB getChangelogDB()
  {
    return replicationServer.getChangelogDB();
  }

  /**
   * Returns the ChangelogBackend configured for "cn=changelog" in this directory server.
   *
   * @return the ChangelogBackend configured for "cn=changelog" in this directory server
   * @deprecated instead inject the required object where needed
   */
  @Deprecated
  public static ChangelogBackend getInstance()
  {
    return (ChangelogBackend) DirectoryServer.getBackend(CHANGELOG_BASE_DN);
  }

  /** {@inheritDoc} */
  @Override
  public void configureBackend(final Configuration config, ServerContext serverContext) throws ConfigException
  {
    throw new UnsupportedOperationException("The changelog backend is not configurable");
  }

  /** {@inheritDoc} */
  @Override
  public void openBackend() throws InitializationException
  {
    baseDNs = new DN[] { CHANGELOG_BASE_DN };

    try
    {
      DirectoryServer.registerBaseDN(CHANGELOG_BASE_DN, this, true);
    }
    catch (final DirectoryException e)
    {
      throw new InitializationException(
          ERR_BACKEND_CANNOT_REGISTER_BASEDN.get(DN_EXTERNAL_CHANGELOG_ROOT, getExceptionMessage(e)), e);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void closeBackend()
  {
    try
    {
      DirectoryServer.deregisterBaseDN(CHANGELOG_BASE_DN);
    }
    catch (final DirectoryException e)
    {
      logger.traceException(e);
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
  public boolean isIndexed(final AttributeType attributeType, final IndexType indexType)
  {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public Entry getEntry(final DN entryDN) throws DirectoryException
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
  public ConditionResult hasSubordinates(final DN entryDN) throws DirectoryException
  {
    if (CHANGELOG_BASE_DN.equals(entryDN))
    {
      final Boolean hasSubs = baseChangelogHasSubordinates();
      if (hasSubs == null)
      {
        return ConditionResult.UNDEFINED;
      }
      return ConditionResult.valueOf(hasSubs);
    }
    return ConditionResult.FALSE;
  }

  private Boolean baseChangelogHasSubordinates() throws DirectoryException
  {
    if (baseEntryHasSubordinates == null)
    {
      // compute its value
      try
      {
        final ReplicationDomainDB replicationDomainDB = getChangelogDB().getReplicationDomainDB();
        final MultiDomainDBCursor cursor = replicationDomainDB.getCursorFrom(
            new MultiDomainServerState(), GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY, getExcludedBaseDNs());
        try
        {
          baseEntryHasSubordinates = cursor.next();
        }
        finally
        {
          close(cursor);
        }
      }
      catch (ChangelogException e)
      {
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, ERR_CHANGELOG_BACKEND_ATTRIBUTE.get(
            "hasSubordinates", DN_EXTERNAL_CHANGELOG_ROOT, stackTraceToSingleLineString(e)));
      }
    }
    return baseEntryHasSubordinates;
  }

  /** {@inheritDoc} */
  @Override
  public long getNumberOfEntriesInBaseDN(final DN baseDN) throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, ERR_NUM_SUBORDINATES_NOT_SUPPORTED.get());
  }

  /** {@inheritDoc} */
  @Override
  public long getNumberOfChildren(final DN parentDN) throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, ERR_NUM_SUBORDINATES_NOT_SUPPORTED.get());
  }

  /**
   * Notifies persistent searches of this backend that a new cookie entry was added to it.
   * <p>
   * Note: This method correspond to the "persistent search" phase.
   * It is executed multiple times per persistent search, multi-threaded, until the persistent search is cancelled.
   * <p>
   * This method must only be called after the provided data have been persisted to disk.
   *
   * @param baseDN
   *          the baseDN of the newly added entry.
   * @param updateMsg
   *          the update message of the newly added entry
   * @throws ChangelogException
   *           If a problem occurs while notifying of the newly added entry.
   */
  public void notifyCookieEntryAdded(DN baseDN, UpdateMsg updateMsg) throws ChangelogException
  {
    if (!(updateMsg instanceof LDAPUpdateMsg))
    {
      return;
    }

    try
    {
      for (PersistentSearch pSearch : cookieBasedPersistentSearches)
      {
        final SearchOperation searchOp = pSearch.getSearchOperation();
        final CookieEntrySender entrySender = searchOp.getAttachment(ENTRY_SENDER_ATTACHMENT);
        entrySender.persistentSearchSendEntry(baseDN, updateMsg);
      }
    }
    catch (DirectoryException e)
    {
      throw new ChangelogException(e.getMessageObject(), e);
    }
  }

  /**
   * Notifies persistent searches of this backend that a new change number entry was added to it.
   * <p>
   * Note: This method correspond to the "persistent search" phase.
   * It is executed multiple times per persistent search, multi-threaded, until the persistent search is cancelled.
   * <p>
   * This method must only be called after the provided data have been persisted to disk.
   *
   * @param baseDN
   *          the baseDN of the newly added entry.
   * @param changeNumber
   *          the change number of the newly added entry. It will be greater
   *          than zero for entries added to the change number index and less
   *          than or equal to zero for entries added to any replica DB
   * @param cookieString
   *          a string representing the cookie of the newly added entry.
   *          This is only meaningful for entries added to the change number index
   * @param updateMsg
   *          the update message of the newly added entry
   * @throws ChangelogException
   *           If a problem occurs while notifying of the newly added entry.
   */
  public void notifyChangeNumberEntryAdded(DN baseDN, long changeNumber, String cookieString, UpdateMsg updateMsg)
      throws ChangelogException
  {
    if (!(updateMsg instanceof LDAPUpdateMsg))
    {
      return;
    }

    try
    {
      // changeNumber entry can be shared with multiple persistent searches
      final Entry changeNumberEntry = createEntryFromMsg(baseDN, changeNumber, cookieString, updateMsg);
      for (PersistentSearch pSearch : changeNumberBasedPersistentSearches)
      {
        final SearchOperation searchOp = pSearch.getSearchOperation();
        final ChangeNumberEntrySender entrySender = searchOp.getAttachment(ENTRY_SENDER_ATTACHMENT);
        entrySender.persistentSearchSendEntry(changeNumber, changeNumberEntry);
      }
    }
    catch (DirectoryException e)
    {
      throw new ChangelogException(e.getMessageObject(), e);
    }
  }

  private boolean isCookieBased(final SearchOperation searchOp)
  {
    for (Control c : searchOp.getRequestControls())
    {
      if (OID_ECL_COOKIE_EXCHANGE_CONTROL.equals(c.getOID()))
      {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void addEntry(Entry entry, AddOperation addOperation)
      throws DirectoryException, CanceledOperationException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_ADD_NOT_SUPPORTED.get(String.valueOf(entry.getName()), getBackendID()));
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
        ERR_BACKEND_MODIFY_NOT_SUPPORTED.get(String.valueOf(newEntry.getName()), getBackendID()));
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

  /**
   * {@inheritDoc}
   * <p>
   * Runs the "initial search" phase (as opposed to a "persistent search"
   * phase). The "initial search" phase is the only search run by normal
   * searches, but it is also run by persistent searches with
   * <code>changesOnly=false</code>. Persistent searches with
   * <code>changesOnly=true</code> never execute this code.
   * <p>
   * Note: this method is executed only once per persistent search, single
   * threaded.
   */
  @Override
  public void search(final SearchOperation searchOperation) throws DirectoryException
  {
    checkChangelogReadPrivilege(searchOperation);

    final Set<DN> excludedBaseDNs = getExcludedBaseDNs();
    final MultiDomainServerState cookie = getCookieFromControl(searchOperation, excludedBaseDNs);

    final ChangeNumberRange range = optimizeSearch(searchOperation.getBaseDN(), searchOperation.getFilter());
    try
    {
      final boolean isPersistentSearch = isPersistentSearch(searchOperation);
      if (cookie != null)
      {
        initialSearchFromCookie(
            getCookieEntrySender(SearchPhase.INITIAL, searchOperation, cookie, excludedBaseDNs, isPersistentSearch));
      }
      else
      {
        initialSearchFromChangeNumber(
            getChangeNumberEntrySender(SearchPhase.INITIAL, searchOperation, range, isPersistentSearch));
      }
    }
    catch (ChangelogException e)
    {
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, ERR_CHANGELOG_BACKEND_SEARCH.get(
          searchOperation.getBaseDN(), searchOperation.getFilter(), stackTraceToSingleLineString(e)));
    }
  }

  private MultiDomainServerState getCookieFromControl(final SearchOperation searchOperation, Set<DN> excludedBaseDNs)
      throws DirectoryException
  {
    final ExternalChangelogRequestControl eclRequestControl =
        searchOperation.getRequestControl(ExternalChangelogRequestControl.DECODER);
    if (eclRequestControl != null)
    {
      final MultiDomainServerState cookie = eclRequestControl.getCookie();
      validateProvidedCookie(cookie, excludedBaseDNs);
      return cookie;
    }
    return null;
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
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void exportLDIF(final LDIFExportConfig exportConfig)
      throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_IMPORT_AND_EXPORT_NOT_SUPPORTED.get(getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig, ServerContext serverContext)
      throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
        ERR_BACKEND_IMPORT_AND_EXPORT_NOT_SUPPORTED.get(getBackendID()));
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
  public void removeBackup(BackupDirectory backupDirectory, String backupID) throws DirectoryException
  {
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          ERR_BACKEND_BACKUP_AND_RESTORE_NOT_SUPPORTED.get(getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException
  {
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          ERR_BACKEND_BACKUP_AND_RESTORE_NOT_SUPPORTED.get(getBackendID()));
  }

  /** {@inheritDoc} */
  @Override
  public long getEntryCount()
  {
    try
    {
      return getNumberOfEntriesInBaseDN(CHANGELOG_BASE_DN) + 1;
    }
    catch (DirectoryException e)
    {
      logger.traceException(e);
      return -1;
    }
  }

  /**
   * Represent the change number range targeted by a search operation.
   * <p>
   * This class should be visible for tests.
   */
  static final class ChangeNumberRange
  {
    private long lowerBound = -1;
    private long upperBound = -1;

    /**
     * Returns the lowest change number to retrieve (inclusive).
     *
     * @return the lowest change number
     */
    long getLowerBound()
    {
      return lowerBound;
    }

    /**
     * Returns the highest change number to retrieve (inclusive).
     *
     * @return the highest change number
     */
    long getUpperBound()
    {
      return upperBound;
    }
  }

  /**
   * Returns the set of DNs to exclude from the search.
   *
   * @return the DNs corresponding to domains to exclude from the search.
   * @throws DirectoryException
   *           If a DN can't be decoded.
   */
  private static Set<DN> getExcludedBaseDNs() throws DirectoryException
  {
    return getExcludedChangelogDomains();
  }

  /**
   * Optimize the search parameters by analyzing the DN and filter.
   * It also performs validation on some search parameters
   * for both cookie and change number based changelogs.
   *
   * @param baseDN the provided search baseDN.
   * @param userFilter the provided search filter.
   * @return the optimized change number range
   * @throws DirectoryException when an exception occurs.
   */
  ChangeNumberRange optimizeSearch(final DN baseDN, final SearchFilter userFilter) throws DirectoryException
  {
    SearchFilter equalityFilter = null;
    switch (baseDN.size())
    {
    case 1:
      // "cn=changelog" : use user-provided search filter.
      break;
    case 2:
      // It is probably "changeNumber=xxx,cn=changelog", use equality filter
      // But it also could be "<service-id>,cn=changelog" so need to check on attribute
      equalityFilter = buildSearchFilterFrom(baseDN, CHANGE_NUMBER_ATTR_LC, CHANGE_NUMBER_ATTR);
      break;
    default:
      // "replicationCSN=xxx,<service-id>,cn=changelog" : use equality filter
      equalityFilter = buildSearchFilterFrom(baseDN, "replicationcsn", "replicationCSN");
      break;
    }

    return optimizeSearchUsingFilter(equalityFilter != null ? equalityFilter : userFilter);
  }

  /**
   * Build a search filter from given DN and attribute.
   *
   * @return the search filter or {@code null} if attribute is not present in
   *         the provided DN
   */
  private SearchFilter buildSearchFilterFrom(final DN baseDN, final String lowerCaseAttr, final String upperCaseAttr)
  {
    final RDN rdn = baseDN.rdn();
    AttributeType attrType = DirectoryServer.getAttributeType(lowerCaseAttr);
    if (attrType == null)
    {
      attrType = DirectoryServer.getDefaultAttributeType(upperCaseAttr);
    }
    final ByteString attrValue = rdn.getAttributeValue(attrType);
    if (attrValue != null)
    {
      return SearchFilter.createEqualityFilter(attrType, attrValue);
    }
    return null;
  }

  private ChangeNumberRange optimizeSearchUsingFilter(final SearchFilter filter) throws DirectoryException
  {
    final ChangeNumberRange range = new ChangeNumberRange();
    if (filter == null)
    {
      return range;
    }

    if (matches(filter, FilterType.GREATER_OR_EQUAL, CHANGE_NUMBER_ATTR))
    {
      range.lowerBound = decodeChangeNumber(filter.getAssertionValue());
    }
    else if (matches(filter, FilterType.LESS_OR_EQUAL, CHANGE_NUMBER_ATTR))
    {
      range.upperBound = decodeChangeNumber(filter.getAssertionValue());
    }
    else if (matches(filter, FilterType.EQUALITY, CHANGE_NUMBER_ATTR))
    {
      final long number = decodeChangeNumber(filter.getAssertionValue());
      range.lowerBound = number;
      range.upperBound = number;
    }
    else if (matches(filter, FilterType.EQUALITY, "replicationcsn"))
    {
      // == exact CSN
      // validate provided CSN is correct
      new CSN(filter.getAssertionValue().toString());
    }
    else if (filter.getFilterType() == FilterType.AND)
    {
      // TODO: it looks like it could be generalized to N components, not only two
      final Collection<SearchFilter> components = filter.getFilterComponents();
      final SearchFilter filters[] = components.toArray(new SearchFilter[0]);
      long upper1 = -1;
      long lower1 = -1;
      long upper2 = -1;
      long lower2 = -1;
      if (filters.length > 0)
      {
        ChangeNumberRange range1 = optimizeSearchUsingFilter(filters[0]);
        upper1 = range1.upperBound;
        lower1 = range1.lowerBound;
      }
      if (filters.length > 1)
      {
        ChangeNumberRange range2 = optimizeSearchUsingFilter(filters[1]);
        upper2 = range2.upperBound;
        lower2 = range2.lowerBound;
      }
      if (upper1 == -1)
      {
        range.upperBound = upper2;
      }
      else if (upper2 == -1)
      {
        range.upperBound = upper1;
      }
      else
      {
        range.upperBound = Math.min(upper1, upper2);
      }

      range.lowerBound = Math.max(lower1, lower2);
    }
    return range;
  }

  private static long decodeChangeNumber(final ByteString assertionValue)
      throws DirectoryException
  {
    try
    {
      return Long.decode(assertionValue.toString());
    }
    catch (NumberFormatException e)
    {
      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
          LocalizableMessage.raw("Could not convert value '%s' to long", assertionValue));
    }
  }

  private boolean matches(SearchFilter filter, FilterType filterType, String primaryName)
  {
    return filter.getFilterType() == filterType
           && filter.getAttributeType() != null
           && filter.getAttributeType().getPrimaryName().equalsIgnoreCase(primaryName);
  }

  /**
   * Search the changelog when a cookie control is provided.
   */
  private void initialSearchFromCookie(final CookieEntrySender entrySender)
      throws DirectoryException, ChangelogException
  {
    if (!sendBaseChangelogEntry(entrySender.searchOp))
    { // only return the base entry: stop here
      return;
    }

    ECLMultiDomainDBCursor replicaUpdatesCursor = null;
    try
    {
      final ReplicationDomainDB replicationDomainDB = getChangelogDB().getReplicationDomainDB();
      final MultiDomainDBCursor cursor = replicationDomainDB.getCursorFrom(
          entrySender.cookie, GREATER_THAN_OR_EQUAL_TO_KEY, AFTER_MATCHING_KEY, entrySender.excludedBaseDNs);
      replicaUpdatesCursor = new ECLMultiDomainDBCursor(domainPredicate, cursor);

      if (sendCookieEntriesFromCursor(entrySender, replicaUpdatesCursor))
      {
        entrySender.transitioningToPersistentSearchPhase();
        sendCookieEntriesFromCursor(entrySender, replicaUpdatesCursor);
      }
    }
    finally
    {
      entrySender.finalizeInitialSearch();
      StaticUtils.close(replicaUpdatesCursor);
    }
  }

  private CookieEntrySender getCookieEntrySender(SearchPhase startPhase, final SearchOperation searchOperation,
      MultiDomainServerState cookie, Set<DN> excludedBaseDNs, boolean isPersistentSearch)
  {
    if (isPersistentSearch && SearchPhase.INITIAL.equals(startPhase))
    {
      return searchOperation.getAttachment(ENTRY_SENDER_ATTACHMENT);
    }
    return new CookieEntrySender(searchOperation, startPhase, cookie, excludedBaseDNs);
  }

  private boolean sendCookieEntriesFromCursor(final CookieEntrySender entrySender,
      final ECLMultiDomainDBCursor replicaUpdatesCursor) throws ChangelogException, DirectoryException
  {
    boolean continueSearch = true;
    while (continueSearch && replicaUpdatesCursor.next())
    {
      final UpdateMsg updateMsg = replicaUpdatesCursor.getRecord();
      final DN domainBaseDN = replicaUpdatesCursor.getData();
      continueSearch = entrySender.initialSearchSendEntry(updateMsg, domainBaseDN);
    }
    return continueSearch;
  }

  private boolean isPersistentSearch(SearchOperation op)
  {
    for (PersistentSearch pSearch : getPersistentSearches())
    {
      if (op == pSearch.getSearchOperation())
      {
        return true;
      }
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void registerPersistentSearch(PersistentSearch pSearch) throws DirectoryException
  {
    initializePersistentSearch(pSearch);

    if (isCookieBased(pSearch.getSearchOperation()))
    {
      cookieBasedPersistentSearches.add(pSearch);
    }
    else
    {
      changeNumberBasedPersistentSearches.add(pSearch);
    }
    super.registerPersistentSearch(pSearch);
  }

  private void initializePersistentSearch(PersistentSearch pSearch) throws DirectoryException
  {
    final SearchOperation searchOp = pSearch.getSearchOperation();

    // Validation must be done during registration for changes only persistent searches.
    // Otherwise, when there is an initial search phase,
    // validation is performed by the search() method.
    if (pSearch.isChangesOnly())
    {
      checkChangelogReadPrivilege(searchOp);
    }
    final ChangeNumberRange range = optimizeSearch(searchOp.getBaseDN(), searchOp.getFilter());

    final SearchPhase startPhase = pSearch.isChangesOnly() ? SearchPhase.PERSISTENT : SearchPhase.INITIAL;
    if (isCookieBased(searchOp))
    {
      final Set<DN> excludedBaseDNs = getExcludedBaseDNs();
      final MultiDomainServerState cookie = getCookie(pSearch.isChangesOnly(), searchOp, excludedBaseDNs);
      searchOp.setAttachment(ENTRY_SENDER_ATTACHMENT,
          new CookieEntrySender(searchOp, startPhase, cookie, excludedBaseDNs));
    }
    else
    {
      searchOp.setAttachment(ENTRY_SENDER_ATTACHMENT,
          new ChangeNumberEntrySender(searchOp, startPhase, range));
    }
  }

  private MultiDomainServerState getCookie(boolean isChangesOnly, SearchOperation searchOp, Set<DN> excludedBaseDNs)
      throws DirectoryException
  {
    if (isChangesOnly)
    {
      // this changesOnly persistent search will not go through #initialSearch()
      // so we must initialize the cookie here
      return getNewestCookie(searchOp);
    }
    return getCookieFromControl(searchOp, excludedBaseDNs);
  }

  private MultiDomainServerState getNewestCookie(SearchOperation searchOp)
  {
    if (!isCookieBased(searchOp))
    {
      return null;
    }

    final MultiDomainServerState cookie = new MultiDomainServerState();
    for (final Iterator<ReplicationServerDomain> it =
        replicationServer.getDomainIterator(); it.hasNext();)
    {
      final DN baseDN = it.next().getBaseDN();
      final ServerState state = getChangelogDB().getReplicationDomainDB().getDomainNewestCSNs(baseDN);
      cookie.update(baseDN, state);
    }
    return cookie;
  }

  /**
   * Validates the cookie contained in search parameters by checking its content
   * with the actual replication server state.
   *
   * @throws DirectoryException
   *           If the state is not valid
   */
  private void validateProvidedCookie(final MultiDomainServerState cookie, Set<DN> excludedBaseDNs)
      throws DirectoryException
  {
    if (cookie != null && !cookie.isEmpty())
    {
      replicationServer.validateCookie(cookie, excludedBaseDNs);
    }
  }

  /**
   * Search the changelog using change number(s).
   */
  private void initialSearchFromChangeNumber(final ChangeNumberEntrySender entrySender)
      throws ChangelogException, DirectoryException
  {
    if (!sendBaseChangelogEntry(entrySender.searchOp))
    { // only return the base entry: stop here
      return;
    }

    DBCursor<ChangeNumberIndexRecord> cnIndexDBCursor = null;
    final AtomicReference<MultiDomainDBCursor> replicaUpdatesCursor = new AtomicReference<MultiDomainDBCursor>();
    try
    {
      cnIndexDBCursor = getCNIndexDBCursor(entrySender.lowestChangeNumber);
      final MultiDomainServerState cookie = new MultiDomainServerState();

      if (sendChangeNumberEntriesFromCursors(entrySender, cnIndexDBCursor, replicaUpdatesCursor, cookie))
      {
        entrySender.transitioningToPersistentSearchPhase();
        sendChangeNumberEntriesFromCursors(entrySender, cnIndexDBCursor, replicaUpdatesCursor, cookie);
      }
    }
    finally
    {
      entrySender.finalizeInitialSearch();
      StaticUtils.close(cnIndexDBCursor, replicaUpdatesCursor.get());
    }
  }

  private ChangeNumberEntrySender getChangeNumberEntrySender(SearchPhase startPhase,
      final SearchOperation searchOperation, ChangeNumberRange range, boolean isPersistentSearch)
  {
    if (isPersistentSearch && SearchPhase.INITIAL.equals(startPhase))
    {
      return searchOperation.getAttachment(ENTRY_SENDER_ATTACHMENT);
    }
    return new ChangeNumberEntrySender(searchOperation, SearchPhase.INITIAL, range);
  }

  private boolean sendChangeNumberEntriesFromCursors(final ChangeNumberEntrySender entrySender,
      DBCursor<ChangeNumberIndexRecord> cnIndexDBCursor, AtomicReference<MultiDomainDBCursor> replicaUpdatesCursor,
      MultiDomainServerState cookie) throws ChangelogException, DirectoryException
  {
    boolean continueSearch = true;
    while (continueSearch && cnIndexDBCursor.next())
    {
      // Handle the current cnIndex record
      final ChangeNumberIndexRecord cnIndexRecord = cnIndexDBCursor.getRecord();
      if (replicaUpdatesCursor.get() == null)
      {
        replicaUpdatesCursor.set(initializeReplicaUpdatesCursor(cnIndexRecord));
        initializeCookieForChangeNumberMode(cookie, cnIndexRecord);
      }
      else
      {
        cookie.update(cnIndexRecord.getBaseDN(), cnIndexRecord.getCSN());
      }
      continueSearch = entrySender.changeNumberIsInRange(cnIndexRecord.getChangeNumber());
      if (continueSearch)
      {
        final UpdateMsg updateMsg = findReplicaUpdateMessage(cnIndexRecord, replicaUpdatesCursor.get());
        if (updateMsg != null)
        {
          continueSearch = entrySender.initialSearchSendEntry(cnIndexRecord, updateMsg, cookie);
          replicaUpdatesCursor.get().next();
        }
      }
    }
    return continueSearch;
  }

  /** Initialize the provided cookie from the provided change number index record. */
  private void initializeCookieForChangeNumberMode(
      MultiDomainServerState cookie, final ChangeNumberIndexRecord cnIndexRecord) throws ChangelogException
  {
    ECLMultiDomainDBCursor eclCursor = null;
    try
    {
      cookie.update(cnIndexRecord.getBaseDN(), cnIndexRecord.getCSN());
      MultiDomainDBCursor cursor =
          getChangelogDB().getReplicationDomainDB().getCursorFrom(cookie,
              LESS_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY);
      eclCursor = new ECLMultiDomainDBCursor(domainPredicate, cursor);
      eclCursor.next();
      cookie.update(eclCursor.toCookie());
    }
    finally
    {
      close(eclCursor);
    }
  }

  private MultiDomainDBCursor initializeReplicaUpdatesCursor(
      final ChangeNumberIndexRecord cnIndexRecord) throws ChangelogException
  {
    final MultiDomainServerState state = new MultiDomainServerState();
    state.update(cnIndexRecord.getBaseDN(), cnIndexRecord.getCSN());

    // No need for ECLMultiDomainDBCursor in this case
    // as updateMsg will be matched with cnIndexRecord
    final MultiDomainDBCursor replicaUpdatesCursor =
        getChangelogDB().getReplicationDomainDB().getCursorFrom(state, GREATER_THAN_OR_EQUAL_TO_KEY, ON_MATCHING_KEY);
    replicaUpdatesCursor.next();
    return replicaUpdatesCursor;
  }

  /**
   * Returns the replica update message corresponding to the provided
   * cnIndexRecord.
   *
   * @return the update message, which may be {@code null} if the update message
   *         could not be found because it was purged or because corresponding
   *         baseDN was removed from the changelog
   * @throws DirectoryException
   *           If inconsistency is detected between the available update
   *           messages and the provided cnIndexRecord
   */
  private UpdateMsg findReplicaUpdateMessage(
      final ChangeNumberIndexRecord cnIndexRecord,
      final MultiDomainDBCursor replicaUpdatesCursor)
          throws DirectoryException, ChangelogException
  {
    while (true)
    {
      final UpdateMsg updateMsg = replicaUpdatesCursor.getRecord();
      final int compareIndexWithUpdateMsg = cnIndexRecord.getCSN().compareTo(updateMsg.getCSN());
      if (compareIndexWithUpdateMsg < 0) {
        // Either update message has been purged or baseDN has been removed from changelogDB,
        // ignore current index record and go to the next one
        return null;
      }
      else if (compareIndexWithUpdateMsg == 0)
      {
        // Found the matching update message
        return updateMsg;
      }
      // Case compareIndexWithUpdateMsg > 0 : the update message has not bean reached yet
      if (!replicaUpdatesCursor.next())
      {
        // Should never happen, as it means some messages have disappeared
        // TODO : put the correct I18N message
        throw new DirectoryException(ResultCode.OPERATIONS_ERROR,
            LocalizableMessage.raw("Could not find replica update message matching index record. " +
                "No more replica update messages with a csn newer than " + updateMsg.getCSN() + " exist."));
      }
    }
  }

  /** Returns a cursor on CNIndexDB for the provided first change number. */
  private DBCursor<ChangeNumberIndexRecord> getCNIndexDBCursor(
      final long firstChangeNumber) throws ChangelogException
  {
    final ChangeNumberIndexDB cnIndexDB = getChangelogDB().getChangeNumberIndexDB();
    long changeNumberToUse = firstChangeNumber;
    if (changeNumberToUse <= 1)
    {
      final ChangeNumberIndexRecord oldestRecord = cnIndexDB.getOldestRecord();
      changeNumberToUse = oldestRecord == null ? CHANGE_NUMBER_FOR_EMPTY_CURSOR : oldestRecord.getChangeNumber();
    }
    return cnIndexDB.getCursorFrom(changeNumberToUse);
  }

  /**
   * Creates a changelog entry.
   */
  private static Entry createEntryFromMsg(final DN baseDN, final long changeNumber, final String cookie,
      final UpdateMsg msg) throws DirectoryException
  {
    if (msg instanceof AddMsg)
    {
      return createAddMsg(baseDN, changeNumber, cookie, msg);
    }
    else if (msg instanceof ModifyCommonMsg)
    {
      return createModifyMsg(baseDN, changeNumber, cookie, msg);
    }
    else if (msg instanceof DeleteMsg)
    {
      final DeleteMsg delMsg = (DeleteMsg) msg;
      return createChangelogEntry(baseDN, changeNumber, cookie, delMsg, null, "delete", delMsg.getInitiatorsName());
    }
    throw new DirectoryException(ResultCode.OPERATIONS_ERROR,
        LocalizableMessage.raw("Unexpected message type when trying to create changelog entry for dn %s : %s", baseDN,
            msg.getClass()));
  }

  /**
   * Creates an entry from an add message.
   * <p>
   * Map addMsg to an LDIF string for the 'changes' attribute, and pull out
   * change initiators name if available which is contained in the creatorsName
   * attribute.
   */
  private static Entry createAddMsg(final DN baseDN, final long changeNumber, final String cookie, final UpdateMsg msg)
      throws DirectoryException
  {
    final AddMsg addMsg = (AddMsg) msg;
    String changeInitiatorsName = null;
    String ldifChanges = null;
    try
    {
      final StringBuilder builder = new StringBuilder(256);
      for (Attribute attr : addMsg.getAttributes())
      {
        if (attr.getAttributeType().equals(CREATORS_NAME_TYPE) && !attr.isEmpty())
        {
          // This attribute is not multi-valued.
          changeInitiatorsName = attr.iterator().next().toString();
        }
        final String attrName = attr.getNameWithOptions();
        for (ByteString value : attr)
        {
          builder.append(attrName);
          appendLDIFSeparatorAndValue(builder, value);
          builder.append('\n');
        }
      }
      ldifChanges = builder.toString();
    }
    catch (Exception e)
    {
      logEncodingMessageError("add", addMsg.getDN(), e);
    }

    return createChangelogEntry(baseDN, changeNumber, cookie, addMsg, ldifChanges, "add", changeInitiatorsName);
  }

  /**
   * Creates an entry from a modify message.
   * <p>
   * Map the modifyMsg to an LDIF string for the 'changes' attribute, and pull
   * out change initiators name if available which is contained in the
   * modifiersName attribute.
   */
  private static Entry createModifyMsg(final DN baseDN, final long changeNumber, final String cookie,
      final UpdateMsg msg) throws DirectoryException
  {
    final ModifyCommonMsg modifyMsg = (ModifyCommonMsg) msg;
    String changeInitiatorsName = null;
    String ldifChanges = null;
    try
    {
      final StringBuilder builder = new StringBuilder(128);
      for (Modification mod : modifyMsg.getMods())
      {
        final Attribute attr = mod.getAttribute();
        if (mod.getModificationType() == ModificationType.REPLACE
            && attr.getAttributeType().equals(MODIFIERS_NAME_TYPE)
            && !attr.isEmpty())
        {
          // This attribute is not multi-valued.
          changeInitiatorsName = attr.iterator().next().toString();
        }
        final String attrName = attr.getNameWithOptions();
        builder.append(mod.getModificationType());
        builder.append(": ");
        builder.append(attrName);
        builder.append('\n');

        for (ByteString value : attr)
        {
          builder.append(attrName);
          appendLDIFSeparatorAndValue(builder, value);
          builder.append('\n');
        }
        builder.append("-\n");
      }
      ldifChanges = builder.toString();
    }
    catch (Exception e)
    {
      logEncodingMessageError("modify", modifyMsg.getDN(), e);
    }

    final boolean isModifyDNMsg = modifyMsg instanceof ModifyDNMsg;
    final Entry entry = createChangelogEntry(baseDN, changeNumber, cookie, modifyMsg, ldifChanges,
        isModifyDNMsg ? "modrdn" : "modify", changeInitiatorsName);

    if (isModifyDNMsg)
    {
      final ModifyDNMsg modDNMsg = (ModifyDNMsg) modifyMsg;
      addAttribute(entry, "newrdn", modDNMsg.getNewRDN());
      if (modDNMsg.getNewSuperior() != null)
      {
        addAttribute(entry, "newsuperior", modDNMsg.getNewSuperior());
      }
      addAttribute(entry, "deleteoldrdn", String.valueOf(modDNMsg.deleteOldRdn()));
    }
    return entry;
  }

  /**
   * Log an encoding message error.
   *
   * @param messageType
   *            String identifying type of message. Should be "add" or "modify".
   * @param entryDN
   *            DN of original entry
   */
  private static void logEncodingMessageError(String messageType, DN entryDN, Exception exception)
  {
    logger.traceException(exception);
    logger.error(LocalizableMessage.raw(
        "An exception was encountered while trying to encode a replication " + messageType + " message for entry \""
        + entryDN + "\" into an External Change Log entry: " + exception.getMessage()));
  }

  private void checkChangelogReadPrivilege(SearchOperation searchOp) throws DirectoryException
  {
    if (!searchOp.getClientConnection().hasPrivilege(Privilege.CHANGELOG_READ, searchOp))
    {
      throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
          NOTE_SEARCH_CHANGELOG_INSUFFICIENT_PRIVILEGES.get());
    }
  }

  /**
   * Create a changelog entry from a set of provided information. This is the part of
   * entry creation common to all types of msgs (ADD, DEL, MOD, MODDN).
   */
  private static Entry createChangelogEntry(final DN baseDN, final long changeNumber, final String cookie,
      final LDAPUpdateMsg msg, final String ldifChanges, final String changeType,
      final String changeInitiatorsName) throws DirectoryException
  {
    final CSN csn = msg.getCSN();
    String dnString;
    if (changeNumber > 0)
    {
      // change number mode
      dnString = "changeNumber=" + changeNumber + "," + DN_EXTERNAL_CHANGELOG_ROOT;
    }
    else
    {
      // Cookie mode
      dnString = "replicationCSN=" + csn + "," + baseDN + "," + DN_EXTERNAL_CHANGELOG_ROOT;
    }

    final Map<AttributeType, List<Attribute>> userAttrs = new LinkedHashMap<AttributeType, List<Attribute>>();
    final Map<AttributeType, List<Attribute>> opAttrs = new LinkedHashMap<AttributeType, List<Attribute>>();

    // Operational standard attributes
    addAttributeByType(ATTR_SUBSCHEMA_SUBENTRY_LC, ATTR_SUBSCHEMA_SUBENTRY_LC,
        ConfigConstants.DN_DEFAULT_SCHEMA_ROOT, userAttrs, opAttrs);
    addAttributeByType("numsubordinates", "numSubordinates", "0", userAttrs, opAttrs);
    addAttributeByType("hassubordinates", "hasSubordinates", "false", userAttrs, opAttrs);
    addAttributeByType("entrydn", "entryDN", dnString, userAttrs, opAttrs);

    // REQUIRED attributes
    if (changeNumber > 0)
    {
      addAttributeByType("changenumber", "changeNumber", String.valueOf(changeNumber), userAttrs, opAttrs);
    }
    SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_GMT_TIME);
    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC")); // ??
    final String format = dateFormat.format(new Date(csn.getTime()));
    addAttributeByType("changetime", "changeTime", format, userAttrs, opAttrs);
    addAttributeByType("changetype", "changeType", changeType, userAttrs, opAttrs);
    addAttributeByType("targetdn", "targetDN", msg.getDN().toString(), userAttrs, opAttrs);

    // NON REQUESTED attributes
    addAttributeByType("replicationcsn", "replicationCSN", csn.toString(), userAttrs, opAttrs);
    addAttributeByType("replicaidentifier", "replicaIdentifier", Integer.toString(csn.getServerId()),
        userAttrs, opAttrs);

    if (ldifChanges != null)
    {
      addAttributeByType("changes", "changes", ldifChanges, userAttrs, opAttrs);
    }
    if (changeInitiatorsName != null)
    {
      addAttributeByType("changeinitiatorsname", "changeInitiatorsName", changeInitiatorsName, userAttrs, opAttrs);
    }

    final String targetUUID = msg.getEntryUUID();
    if (targetUUID != null)
    {
      addAttributeByType("targetentryuuid", "targetEntryUUID", targetUUID, userAttrs, opAttrs);
    }
    final String cookie2 = cookie != null ? cookie : "";
    addAttributeByType("changelogcookie", "changeLogCookie", cookie2, userAttrs, opAttrs);

    final List<RawAttribute> includedAttributes = msg.getEclIncludes();
    if (includedAttributes != null && !includedAttributes.isEmpty())
    {
      final StringBuilder builder = new StringBuilder(256);
      for (final RawAttribute includedAttribute : includedAttributes)
      {
        final String name = includedAttribute.getAttributeType();
        for (final ByteString value : includedAttribute.getValues())
        {
          builder.append(name);
          appendLDIFSeparatorAndValue(builder, value);
          builder.append('\n');
        }
      }
      final String includedAttributesLDIF = builder.toString();
      addAttributeByType("includedattributes", "includedAttributes", includedAttributesLDIF, userAttrs, opAttrs);
    }

    return new Entry(DN.valueOf(dnString), CHANGELOG_ENTRY_OBJECT_CLASSES, userAttrs, opAttrs);
  }

  /**
   * Sends the entry if it matches the base, scope and filter of the current search operation.
   * It will also send the base changelog entry if it needs to be sent and was not sent before.
   *
   * @return {@code true} if search should continue, {@code false} otherwise
   */
  private static boolean sendEntryIfMatches(SearchOperation searchOp, Entry entry, String cookie)
      throws DirectoryException
  {
    if (matchBaseAndScopeAndFilter(searchOp, entry))
    {
      return searchOp.returnEntry(entry, getControls(cookie));
    }
    // maybe the next entry will match?
    return true;
  }

  /** Indicates if the provided entry matches the filter, base and scope. */
  private static boolean matchBaseAndScopeAndFilter(SearchOperation searchOp, Entry entry) throws DirectoryException
  {
    return entry.matchesBaseAndScope(searchOp.getBaseDN(), searchOp.getScope())
        && searchOp.getFilter().matchesEntry(entry);
  }

  private static List<Control> getControls(String cookie)
  {
    if (cookie != null)
    {
      final Control c = new EntryChangelogNotificationControl(true, cookie);
      return Collections.singletonList(c);
    }
    return Collections.emptyList();
  }

  /**
   * Create and returns the base changelog entry to the underlying search operation.
   * <p>
   * "initial search" phase must return the base entry immediately.
   *
   * @return {@code true} if search should continue, {@code false} otherwise
   */
  private boolean sendBaseChangelogEntry(SearchOperation searchOp) throws DirectoryException
  {
    final DN baseDN = searchOp.getBaseDN();
    final SearchFilter filter = searchOp.getFilter();
    final SearchScope scope = searchOp.getScope();

    if (ChangelogBackend.CHANGELOG_BASE_DN.matchesBaseAndScope(baseDN, scope))
    {
      final Entry entry = buildBaseChangelogEntry();
      if (filter.matchesEntry(entry) && !searchOp.returnEntry(entry, null))
      {
        // Abandon, size limit reached.
        return false;
      }
    }
    return !baseDN.equals(ChangelogBackend.CHANGELOG_BASE_DN)
        || !scope.equals(SearchScope.BASE_OBJECT);
  }

  private Entry buildBaseChangelogEntry() throws DirectoryException
  {
    final String hasSubordinatesStr = Boolean.toString(baseChangelogHasSubordinates());

    final Map<AttributeType, List<Attribute>> userAttrs = new LinkedHashMap<AttributeType, List<Attribute>>();
    final Map<AttributeType, List<Attribute>> operationalAttrs = new LinkedHashMap<AttributeType, List<Attribute>>();

    // We never return the numSubordinates attribute for the base changelog entry
    // and there is a very good reason for that:
    // - Either we compute it before sending the entries,
    // -- then we risk returning more entries if new entries come in after we computed numSubordinates
    // --   or we risk returning less entries if purge kicks in      after we computed numSubordinates
    // - Or we accumulate all the entries that must be returned before sending them => OutOfMemoryError

    addAttributeByUppercaseName(ATTR_COMMON_NAME, ATTR_COMMON_NAME, BACKEND_ID, userAttrs, operationalAttrs);
    addAttributeByUppercaseName(ATTR_SUBSCHEMA_SUBENTRY_LC, ATTR_SUBSCHEMA_SUBENTRY,
        ConfigConstants.DN_DEFAULT_SCHEMA_ROOT, userAttrs, operationalAttrs);
    addAttributeByUppercaseName("hassubordinates", "hasSubordinates", hasSubordinatesStr, userAttrs, operationalAttrs);
    addAttributeByUppercaseName("entrydn", "entryDN", DN_EXTERNAL_CHANGELOG_ROOT, userAttrs, operationalAttrs);
    return new Entry(CHANGELOG_BASE_DN, CHANGELOG_ROOT_OBJECT_CLASSES, userAttrs, operationalAttrs);
  }

  private static void addAttribute(final Entry e, final String attrType, final String attrValue)
  {
    e.addAttribute(Attributes.create(attrType, attrValue), null);
  }

  private static void addAttributeByType(String attrNameLowercase,
      String attrNameUppercase, String attrValue,
      Map<AttributeType, List<Attribute>> userAttrs,
      Map<AttributeType, List<Attribute>> operationalAttrs)
  {
    addAttribute(attrNameLowercase, attrNameUppercase, attrValue, userAttrs, operationalAttrs, true);
  }

  private static void addAttributeByUppercaseName(String attrNameLowercase,
      String attrNameUppercase,  String attrValue,
      Map<AttributeType, List<Attribute>> userAttrs,
      Map<AttributeType, List<Attribute>> operationalAttrs)
  {
    addAttribute(attrNameLowercase, attrNameUppercase, attrValue, userAttrs, operationalAttrs, false);
  }

  private static void addAttribute(final String attrNameLowercase,
      final String attrNameUppercase, final String attrValue,
      final Map<AttributeType, List<Attribute>> userAttrs,
      final Map<AttributeType, List<Attribute>> operationalAttrs, final boolean addByType)
  {
    AttributeType attrType = DirectoryServer.getAttributeType(attrNameLowercase);
    if (attrType == null)
    {
      attrType = DirectoryServer.getDefaultAttributeType(attrNameUppercase);
    }
    final Attribute a = addByType
        ? Attributes.create(attrType, attrValue)
        : Attributes.create(attrNameUppercase, attrValue);
    final List<Attribute> attrList = Collections.singletonList(a);
    if (attrType.isOperational())
    {
      operationalAttrs.put(attrType, attrList);
    }
    else
    {
      userAttrs.put(attrType, attrList);
    }
  }

  /**
   * Describes the current search phase.
   */
  private enum SearchPhase
  {
    /**
     * "Initial search" phase. The "initial search" phase is running
     * concurrently. All update notifications are ignored.
     */
    INITIAL,
    /**
     * Transitioning from the "initial search" phase to the "persistent search"
     * phase. "Initial search" phase has finished reading from the DB. It now
     * verifies if any more updates have been persisted to the DB since stopping
     * and send them. All update notifications are blocked.
     */
    TRANSITIONING,
    /**
     * "Persistent search" phase. "Initial search" phase has completed. All
     * update notifications are published.
     */
    PERSISTENT;
  }

  /**
   * Contains data to ensure that the same change is not sent twice to clients
   * because of race conditions between the "initial search" phase and the
   * "persistent search" phase.
   */
  private static class SendEntryData<K extends Comparable<K>>
  {
    private final AtomicReference<SearchPhase> searchPhase = new AtomicReference<SearchPhase>(SearchPhase.INITIAL);
    private final Object transitioningLock = new Object();
    private volatile K lastKeySentByInitialSearch;

    private SendEntryData(SearchPhase startPhase)
    {
      searchPhase.set(startPhase);
    }

    private void finalizeInitialSearch()
    {
      searchPhase.set(SearchPhase.PERSISTENT);
      synchronized (transitioningLock)
      { // initial search phase has completed, release all persistent searches
        transitioningLock.notifyAll();
      }
    }

    public void transitioningToPersistentSearchPhase()
    {
      searchPhase.set(SearchPhase.TRANSITIONING);
    }

    private void initialSearchSendsEntry(final K key)
    {
      lastKeySentByInitialSearch = key;
    }

    private boolean persistentSearchCanSendEntry(K key)
    {
      final SearchPhase stateValue = searchPhase.get();
      switch (stateValue)
      {
      case INITIAL:
        return false;
      case TRANSITIONING:
        synchronized (transitioningLock)
        {
          while (SearchPhase.TRANSITIONING.equals(searchPhase.get()))
          {
            // "initial search" phase is over, and is now verifying whether new
            // changes have been published to the DB.
            // Wait for this check to complete
            try
            {
              transitioningLock.wait();
            }
            catch (InterruptedException e)
            {
              Thread.currentThread().interrupt();
              // Shutdown must have been called. Stop sending entries.
              return false;
            }
          }
        }
        return key.compareTo(lastKeySentByInitialSearch) > 0;
      case PERSISTENT:
        return true;
      default:
        throw new RuntimeException("Not implemented for " + stateValue);
      }
    }
  }

  /** Sends entries to clients for change number searches. */
  private static class ChangeNumberEntrySender
  {
    private final SearchOperation searchOp;
    private final long lowestChangeNumber;
    private final long highestChangeNumber;
    private final SendEntryData<Long> sendEntryData;

    private ChangeNumberEntrySender(SearchOperation searchOp, SearchPhase startPhase, ChangeNumberRange range)
    {
      this.searchOp = searchOp;
      this.sendEntryData = new SendEntryData<Long>(startPhase);
      this.lowestChangeNumber = range.lowerBound;
      this.highestChangeNumber = range.upperBound;
    }

    /**
     * Indicates if provided change number is compatible with last change
     * number.
     *
     * @param changeNumber
     *          The change number to test.
     * @return {@code true} if and only if the provided change number is in the
     *         range of the last change number.
     */
    boolean changeNumberIsInRange(long changeNumber)
    {
      return highestChangeNumber == -1 || changeNumber <= highestChangeNumber;
    }

    private void finalizeInitialSearch()
    {
      sendEntryData.finalizeInitialSearch();
    }

    private void transitioningToPersistentSearchPhase()
    {
      sendEntryData.transitioningToPersistentSearchPhase();
    }

    /**
     * @return {@code true} if search should continue, {@code false} otherwise
     */
    private boolean initialSearchSendEntry(ChangeNumberIndexRecord cnIndexRecord, UpdateMsg updateMsg,
        MultiDomainServerState cookie) throws DirectoryException
    {
      final DN baseDN = cnIndexRecord.getBaseDN();
      sendEntryData.initialSearchSendsEntry(cnIndexRecord.getChangeNumber());
      final Entry entry = createEntryFromMsg(baseDN, cnIndexRecord.getChangeNumber(), cookie.toString(), updateMsg);
      return sendEntryIfMatches(searchOp, entry, null);
    }

    private void persistentSearchSendEntry(long changeNumber, Entry entry) throws DirectoryException
    {
      if (sendEntryData.persistentSearchCanSendEntry(changeNumber))
      {
        sendEntryIfMatches(searchOp, entry, null);
      }
    }
  }

  /** Sends entries to clients for cookie-based searches. */
  private static class CookieEntrySender {
    private final SearchOperation searchOp;
    private final SearchPhase startPhase;
    private final Set<DN> excludedBaseDNs;
    private final MultiDomainServerState cookie;
    private final ConcurrentSkipListMap<ReplicaId, SendEntryData<CSN>> replicaIdToSendEntryData =
        new ConcurrentSkipListMap<ReplicaId, SendEntryData<CSN>>();

    private CookieEntrySender(SearchOperation searchOp, SearchPhase startPhase, MultiDomainServerState cookie,
        Set<DN> excludedBaseDNs)
    {
      this.searchOp = searchOp;
      this.startPhase = startPhase;
      this.cookie = cookie;
      this.excludedBaseDNs = excludedBaseDNs;
    }

    private void finalizeInitialSearch()
    {
      for (SendEntryData<CSN> sendEntryData : replicaIdToSendEntryData.values())
      {
        sendEntryData.finalizeInitialSearch();
      }
    }

    private void transitioningToPersistentSearchPhase()
    {
      for (SendEntryData<CSN> sendEntryData : replicaIdToSendEntryData.values())
      {
        sendEntryData.transitioningToPersistentSearchPhase();
      }
    }

    private SendEntryData<CSN> getSendEntryData(DN baseDN, CSN csn)
    {
      final ReplicaId replicaId = ReplicaId.of(baseDN, csn.getServerId());
      SendEntryData<CSN> data = replicaIdToSendEntryData.get(replicaId);
      if (data == null)
      {
        final SendEntryData<CSN> newData = new SendEntryData<CSN>(startPhase);
        data = replicaIdToSendEntryData.putIfAbsent(replicaId, newData);
        return data == null ? newData : data;
      }
      return data;
    }

    private boolean initialSearchSendEntry(final UpdateMsg updateMsg, final DN baseDN) throws DirectoryException
    {
      final CSN csn = updateMsg.getCSN();
      final SendEntryData<CSN> sendEntryData = getSendEntryData(baseDN, csn);
      sendEntryData.initialSearchSendsEntry(csn);
      final String cookieString = updateCookie(baseDN, updateMsg.getCSN());
      final Entry entry = createEntryFromMsg(baseDN, 0, cookieString, updateMsg);
      return sendEntryIfMatches(searchOp, entry, cookieString);
    }

    private void persistentSearchSendEntry(DN baseDN, UpdateMsg updateMsg)
        throws DirectoryException
    {
      final CSN csn = updateMsg.getCSN();
      final SendEntryData<CSN> sendEntryData = getSendEntryData(baseDN, csn);
      if (sendEntryData.persistentSearchCanSendEntry(csn))
      {
        // multi threaded case: wait for the "initial search" phase to set the cookie
        final String cookieString = updateCookie(baseDN, updateMsg.getCSN());
        final Entry cookieEntry = createEntryFromMsg(baseDN, 0, cookieString, updateMsg);
        // FIXME JNR use this instead of previous line:
        // entry.replaceAttribute(Attributes.create("changelogcookie", cookieString));
        sendEntryIfMatches(searchOp, cookieEntry, cookieString);
      }
    }

    private String updateCookie(DN baseDN, final CSN csn)
    {
      synchronized (cookie)
      { // forbid concurrent updates to the cookie
        cookie.update(baseDN, csn);
        return cookie.toString();
      }
    }
  }
}
