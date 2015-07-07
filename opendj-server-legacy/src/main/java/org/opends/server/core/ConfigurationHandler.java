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
 *      Copyright 2014-2015 ForgeRock AS
 */
package org.opends.server.core;

import static org.forgerock.util.Utils.*;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.config.ConfigConstants.*;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.adapter.server3x.Converters;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.spi.ConfigAddListener;
import org.forgerock.opendj.config.server.spi.ConfigChangeListener;
import org.forgerock.opendj.config.server.spi.ConfigDeleteListener;
import org.forgerock.opendj.config.server.spi.ConfigurationRepository;
import org.forgerock.opendj.ldap.CancelRequestListener;
import org.forgerock.opendj.ldap.CancelledResultException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.MemoryBackend;
import org.forgerock.opendj.ldap.RequestContext;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ResultHandler;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldif.EntryReader;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.util.Utils;
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;

/**
 * Responsible for managing configuration entries and listeners on these
 * entries.
 */
public class ConfigurationHandler implements ConfigurationRepository
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final String CONFIGURATION_FILE_NAME = "02-config.ldif";

  private final ServerContext serverContext;

  /** The complete path to the configuration file to use. */
  private File configFile;

  /** Indicates whether to start using the last known good configuration. */
  private boolean useLastKnownGoodConfig;

  /** Backend containing the configuration entries. */
  private MemoryBackend backend;

  /** The config root entry. */
  private Entry rootEntry;

  /** The add/delete/change listeners on configuration entries. */
  private final ConcurrentHashMap<DN, EntryListeners> listeners = new ConcurrentHashMap<>();

  /** Schema with configuration-related elements. */
  private Schema configEnabledSchema;

  /**
   * Creates a new instance.
   *
   * @param serverContext
   *          The server context.
   */
  public ConfigurationHandler(final ServerContext serverContext)
  {
    this.serverContext = serverContext;
  }

  /**
   * Initialize the configuration.
   *
   * @throws InitializationException
   *            If an error occurs during the initialization.
   */
  public void initialize() throws InitializationException
  {
    final DirectoryEnvironmentConfig environment = serverContext.getEnvironment();
    useLastKnownGoodConfig = environment.useLastKnownGoodConfiguration();
    configFile = findConfigFileToUse(environment.getConfigFile());

    configEnabledSchema = loadConfigEnabledSchema();
    loadConfiguration(configFile, configEnabledSchema);
  }

  /** Holds add, change and delete listeners for a given configuration entry. */
  private static class EntryListeners {

    /** The set of add listeners that have been registered with this entry. */
    private final CopyOnWriteArrayList<ConfigAddListener> addListeners = new CopyOnWriteArrayList<>();
    /** The set of change listeners that have been registered with this entry. */
    private final CopyOnWriteArrayList<ConfigChangeListener> changeListeners = new CopyOnWriteArrayList<>();
    /** The set of delete listeners that have been registered with this entry. */
    private final CopyOnWriteArrayList<ConfigDeleteListener> deleteListeners = new CopyOnWriteArrayList<>();

    CopyOnWriteArrayList<ConfigChangeListener> getChangeListeners()
    {
      return changeListeners;
    }

    void registerChangeListener(final ConfigChangeListener listener)
    {
      changeListeners.add(listener);
    }

    boolean deregisterChangeListener(final ConfigChangeListener listener)
    {
      return changeListeners.remove(listener);
    }

    CopyOnWriteArrayList<ConfigAddListener> getAddListeners()
    {
      return addListeners;
    }

    void registerAddListener(final ConfigAddListener listener)
    {
      addListeners.addIfAbsent(listener);
    }

    void deregisterAddListener(final ConfigAddListener listener)
    {
      addListeners.remove(listener);
    }

    CopyOnWriteArrayList<ConfigDeleteListener> getDeleteListeners()
    {
      return deleteListeners;
    }

    void registerDeleteListener(final ConfigDeleteListener listener)
    {
      deleteListeners.addIfAbsent(listener);
    }

    void deregisterDeleteListener(final ConfigDeleteListener listener)
    {
      deleteListeners.remove(listener);
    }

  }

  /** Request context to be used when requesting the internal backend. */
  private static final RequestContext UNCANCELLABLE_REQUEST_CONTEXT =
      new RequestContext()
      {
        /** {@inheritDoc} */
        @Override
        public void removeCancelRequestListener(final CancelRequestListener listener)
        {
          // nothing to do
        }

        /** {@inheritDoc} */
        @Override
        public int getMessageID()
        {
          return -1;
        }

        /** {@inheritDoc} */
        @Override
        public void checkIfCancelled(final boolean signalTooLate)
            throws CancelledResultException
        {
          // nothing to do
        }

        /** {@inheritDoc} */
        @Override
        public void addCancelRequestListener(final CancelRequestListener listener)
        {
          // nothing to do

        }
      };

  /** Handler for search results.  */
  private static final class ConfigSearchHandler implements SearchResultHandler
  {
    private final Set<Entry> entries = new HashSet<>();

    Set<Entry> getEntries()
    {
      return entries;
    }

    /** {@inheritDoc} */
    @Override
    public boolean handleReference(SearchResultReference reference)
    {
      throw new UnsupportedOperationException("Search references are not supported for configuration entries.");
    }

    /** {@inheritDoc} */
    @Override
    public boolean handleEntry(SearchResultEntry entry)
    {
      entries.add(entry);
      return true;
    }
  }

  /** Handler for LDAP operations. */
  private static final class ConfigResultHandler implements ResultHandler<Result> {

    private LdapException resultError;

    LdapException getResultError()
    {
      return resultError;
    }

    boolean hasCompletedSuccessfully() {
      return resultError == null;
    }

    /** {@inheritDoc} */
    @Override
    public void handleResult(Result result)
    {
      // nothing to do
    }

    /** {@inheritDoc} */
    @Override
    public void handleError(LdapException error)
    {
      resultError = error;
    }
  }

  /**
   * Returns the configuration root entry.
   *
   * @return the root entry
   */
  public Entry getRootEntry() {
    return rootEntry;
  }

  /** {@inheritDoc} */
  @Override
  public Entry getEntry(final DN dn) throws ConfigException {
    Entry entry = backend.get(dn);
    if (entry == null)
    {
      // TODO : fix message
      LocalizableMessage message = LocalizableMessage.raw("Unable to retrieve the configuration entry %s", dn);
      throw new ConfigException(message);
    }
    return entry;
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasEntry(final DN dn) throws ConfigException {
    return backend.get(dn) != null;
  }

  /** {@inheritDoc} */
  @Override
  public Set<DN> getChildren(DN dn) throws ConfigException {
    final ConfigResultHandler resultHandler = new ConfigResultHandler();
    final ConfigSearchHandler searchHandler = new ConfigSearchHandler();

    backend.handleSearch(
        UNCANCELLABLE_REQUEST_CONTEXT,
        Requests.newSearchRequest(dn, SearchScope.SINGLE_LEVEL, Filter.objectClassPresent()),
        null, searchHandler, resultHandler);

    if (resultHandler.hasCompletedSuccessfully())
    {
      final Set<DN> children = new HashSet<>();
      for (final Entry entry : searchHandler.getEntries())
      {
        children.add(entry.getName());
      }
      return children;
    }
    else {
      // TODO : fix message
      throw new ConfigException(
          LocalizableMessage.raw("Unable to retrieve children of configuration entry : %s", dn),
          resultHandler.getResultError());
    }
  }

  /**
   * Retrieves the number of subordinates for the requested entry.
   *
   * @param entryDN
   *          The distinguished name of the entry.
   * @param subtree
   *          {@code true} to include all entries from the requested entry
   *          to the lowest level in the tree or {@code false} to only
   *          include the entries immediately below the requested entry.
   * @return The number of subordinate entries
   * @throws ConfigException
   *           If a problem occurs while trying to retrieve the entry.
   */
  public long numSubordinates(final DN entryDN, final boolean subtree) throws ConfigException
  {
    final ConfigResultHandler resultHandler = new ConfigResultHandler();
    final ConfigSearchHandler searchHandler = new ConfigSearchHandler();
    final SearchScope scope = subtree ? SearchScope.SUBORDINATES : SearchScope.SINGLE_LEVEL;
    backend.handleSearch(
        UNCANCELLABLE_REQUEST_CONTEXT,
        Requests.newSearchRequest(entryDN, scope, Filter.objectClassPresent()),
        null, searchHandler, resultHandler);

    if (resultHandler.hasCompletedSuccessfully())
    {
      return searchHandler.getEntries().size();
    }
    else {
      // TODO : fix the message
      throw new ConfigException(
          LocalizableMessage.raw("Unable to retrieve children of configuration entry : %s", entryDN),
          resultHandler.getResultError());
    }
  }

  /**
   * Add a configuration entry
   * <p>
   * The add is performed only if all Add listeners on the parent entry accept
   * the changes. Once the change is accepted, entry is effectively added and
   * all Add listeners are called again to apply the change resulting from this
   * new entry.
   *
   * @param entry
   *          The configuration entry to add.
   * @throws DirectoryException
   *           If an error occurs.
   */
  public void addEntry(final Entry entry) throws DirectoryException
  {
    final DN entryDN = entry.getName();
    if (backend.contains(entryDN))
    {
      throw new DirectoryException(ResultCode.ENTRY_ALREADY_EXISTS, ERR_CONFIG_FILE_ADD_ALREADY_EXISTS.get(entryDN));
    }

    final DN parentDN = retrieveParentDN(entryDN);

    // Iterate through add listeners to make sure the new entry is acceptable.
    final List<ConfigAddListener> addListeners = getAddListeners(parentDN);
    final LocalizableMessageBuilder unacceptableReason = new LocalizableMessageBuilder();
    for (final ConfigAddListener listener : addListeners)
    {
      if (!listener.configAddIsAcceptable(entry, unacceptableReason))
      {
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
            ERR_CONFIG_FILE_ADD_REJECTED_BY_LISTENER.get(entryDN, parentDN, unacceptableReason));
      }
    }

    // Add the entry.
    final ConfigResultHandler resultHandler = new ConfigResultHandler();
    backend.handleAdd(UNCANCELLABLE_REQUEST_CONTEXT, Requests.newAddRequest(entry), null, resultHandler);

    if (!resultHandler.hasCompletedSuccessfully()) {
      // TODO fix the message : error when adding config entry
      // use resultHandler.getResultError() to get the error
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          ERR_CONFIG_FILE_ADD_REJECTED_BY_LISTENER.get(entryDN, parentDN, unacceptableReason));
    }

    // Notify all the add listeners to apply the new configuration entry.
    ResultCode resultCode = ResultCode.SUCCESS;
    final List<LocalizableMessage> messages = new LinkedList<>();
    for (final ConfigAddListener listener : addListeners)
    {
      final ConfigChangeResult result = listener.applyConfigurationAdd(entry);
      if (result.getResultCode() != ResultCode.SUCCESS)
      {
        resultCode = resultCode == ResultCode.SUCCESS ? result.getResultCode() : resultCode;
        messages.addAll(result.getMessages());
      }

      handleConfigChangeResult(result, entry.getName(), listener.getClass().getName(), "applyConfigurationAdd");
    }

    if (resultCode != ResultCode.SUCCESS)
    {
      final String reasons = Utils.joinAsString(".  ", messages);
      throw new DirectoryException(resultCode, ERR_CONFIG_FILE_ADD_APPLY_FAILED.get(reasons));
    }
  }

  /**
   * Delete a configuration entry.
   * <p>
   * The delete is performed only if all Delete listeners on the parent entry
   * accept the changes. Once the change is accepted, entry is effectively
   * deleted and all Delete listeners are called again to apply the change
   * resulting from this deletion.
   *
   * @param dn
   *          DN of entry to delete.
   * @throws DirectoryException
   *           If a problem occurs.
   */
  public void deleteEntry(final DN dn) throws DirectoryException
  {
    // Entry must exist.
    if (!backend.contains(dn))
    {
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
          ERR_CONFIG_FILE_DELETE_NO_SUCH_ENTRY.get(dn), Converters.to(getMatchedDN(dn)), null);
    }

    // Entry must not have children.
    try
    {
      if (!getChildren(dn).isEmpty())
      {
        throw new DirectoryException(ResultCode.NOT_ALLOWED_ON_NONLEAF,
            ERR_CONFIG_FILE_DELETE_HAS_CHILDREN.get(dn));
      }
    }
    catch (ConfigException e)
    {
      // TODO : fix message = ERROR BACKEND CONFIG
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          ERR_CONFIG_FILE_DELETE_HAS_CHILDREN.get(dn), e);
    }

    // TODO : pass in the localizable message (2)
    final DN parentDN = retrieveParentDN(dn);

    // Iterate through delete listeners to make sure the deletion is acceptable.
    final List<ConfigDeleteListener> deleteListeners = getDeleteListeners(parentDN);
    final LocalizableMessageBuilder unacceptableReason = new LocalizableMessageBuilder();
    final Entry entry = backend.get(dn);
    for (final ConfigDeleteListener listener : deleteListeners)
    {
      if (!listener.configDeleteIsAcceptable(entry, unacceptableReason))
      {
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
            ERR_CONFIG_FILE_ADD_REJECTED_BY_LISTENER.get(entry, parentDN, unacceptableReason));
      }
    }

    // Delete the entry
    final ConfigResultHandler resultHandler = new ConfigResultHandler();
    backend.handleDelete(UNCANCELLABLE_REQUEST_CONTEXT, Requests.newDeleteRequest(dn), null, resultHandler);

    if (!resultHandler.hasCompletedSuccessfully()) {
      // TODO fix message : error when deleting config entry
      // use resultHandler.getResultError() to get the error
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          ERR_CONFIG_FILE_DELETE_REJECTED.get(dn, parentDN, unacceptableReason));
    }

    // Notify all the delete listeners that the entry has been removed.
    ResultCode resultCode = ResultCode.SUCCESS;
    final List<LocalizableMessage> messages = new LinkedList<>();
    for (final ConfigDeleteListener listener : deleteListeners)
    {
      final ConfigChangeResult result = listener.applyConfigurationDelete(entry);
      if (result.getResultCode() != ResultCode.SUCCESS)
      {
        resultCode = resultCode == ResultCode.SUCCESS ? result.getResultCode() : resultCode;
        messages.addAll(result.getMessages());
      }

      handleConfigChangeResult(result, dn, listener.getClass().getName(), "applyConfigurationDelete");
    }

    if (resultCode != ResultCode.SUCCESS)
    {
      final String reasons = Utils.joinAsString(".  ", messages);
      throw new DirectoryException(resultCode, ERR_CONFIG_FILE_DELETE_APPLY_FAILED.get(reasons));
    }
  }

  /**
   * Replaces the old configuration entry with the new configuration entry
   * provided.
   * <p>
   * The replacement is performed only if all Change listeners on the entry
   * accept the changes. Once the change is accepted, entry is effectively
   * replaced and all Change listeners are called again to apply the change
   * resulting from the replacement.
   *
   * @param oldEntry
   *          The original entry that is being replaced.
   * @param newEntry
   *          The new entry to use in place of the existing entry with the same
   *          DN.
   * @throws DirectoryException
   *           If a problem occurs while trying to replace the entry.
   */
  public void replaceEntry(final Entry oldEntry, final Entry newEntry)
      throws DirectoryException
  {
    final DN entryDN = oldEntry.getName();
    if (!backend.contains(entryDN))
    {
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
          ERR_CONFIG_FILE_MODIFY_NO_SUCH_ENTRY.get(oldEntry), Converters.to(getMatchedDN(entryDN)), null);
    }

    //TODO : add objectclass and attribute to the config schema in order to get this code run
//    if (!Entries.getStructuralObjectClass(oldEntry, configEnabledSchema)
//        .equals(Entries.getStructuralObjectClass(newEntry, configEnabledSchema)))
//    {
//      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
//          ERR_CONFIG_FILE_MODIFY_STRUCTURAL_CHANGE_NOT_ALLOWED.get(entryDN));
//    }

    // Iterate through change listeners to make sure the change is acceptable.
    final List<ConfigChangeListener> changeListeners = getChangeListeners(entryDN);
    final LocalizableMessageBuilder unacceptableReason = new LocalizableMessageBuilder();
    for (ConfigChangeListener listeners : changeListeners)
    {
      if (!listeners.configChangeIsAcceptable(newEntry, unacceptableReason))
      {
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
            ERR_CONFIG_FILE_MODIFY_REJECTED_BY_CHANGE_LISTENER.get(entryDN, unacceptableReason));
      }
    }

    // Replace the old entry with new entry.
    final ConfigResultHandler resultHandler = new ConfigResultHandler();
    backend.handleModify(
        UNCANCELLABLE_REQUEST_CONTEXT,
        Requests.newModifyRequest(oldEntry, newEntry),
        null,
        resultHandler);

    if (!resultHandler.hasCompletedSuccessfully())
    {
      // TODO fix message : error when replacing config entry
      // use resultHandler.getResultError() to get the error
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          ERR_CONFIG_FILE_DELETE_REJECTED.get(entryDN, entryDN, unacceptableReason));
    }

    // Notify all the change listeners of the update.
    ResultCode resultCode = ResultCode.SUCCESS;
    final List<LocalizableMessage> messages = new LinkedList<>();
    for (final ConfigChangeListener listener : changeListeners)
    {
      final ConfigChangeResult result = listener.applyConfigurationChange(newEntry);
      if (result.getResultCode() != ResultCode.SUCCESS)
      {
        resultCode = resultCode == ResultCode.SUCCESS ? result.getResultCode() : resultCode;
        messages.addAll(result.getMessages());
      }

      handleConfigChangeResult(result, entryDN, listener.getClass().getName(), "applyConfigurationChange");
    }

    if (resultCode != ResultCode.SUCCESS)
    {
      throw new DirectoryException(resultCode,
          ERR_CONFIG_FILE_MODIFY_APPLY_FAILED.get(Utils.joinAsString(".  ", messages)));
    }
  }

  /** {@inheritDoc} */
  @Override
  public void registerAddListener(final DN dn, final ConfigAddListener listener)
  {
    getEntryListeners(dn).registerAddListener(listener);
  }

  /** {@inheritDoc} */
  @Override
  public void registerDeleteListener(final DN dn, final ConfigDeleteListener listener)
  {
    getEntryListeners(dn).registerDeleteListener(listener);
  }

  /** {@inheritDoc} */
  @Override
  public void registerChangeListener(final DN dn, final ConfigChangeListener listener)
  {
    getEntryListeners(dn).registerChangeListener(listener);
  }

  /** {@inheritDoc} */
  @Override
  public void deregisterAddListener(final DN dn, final ConfigAddListener listener)
  {
    getEntryListeners(dn).deregisterAddListener(listener);
  }

  /** {@inheritDoc} */
  @Override
  public void deregisterDeleteListener(final DN dn, final ConfigDeleteListener listener)
  {
    getEntryListeners(dn).deregisterDeleteListener(listener);
  }

  /** {@inheritDoc} */
  @Override
  public boolean deregisterChangeListener(final DN dn, final ConfigChangeListener listener)
  {
    return getEntryListeners(dn).deregisterChangeListener(listener);
  }

  /** {@inheritDoc} */
  @Override
  public List<ConfigAddListener> getAddListeners(final DN dn)
  {
    return getEntryListeners(dn).getAddListeners();
  }

  /** {@inheritDoc} */
  @Override
  public List<ConfigDeleteListener> getDeleteListeners(final DN dn)
  {
    return getEntryListeners(dn).getDeleteListeners();
  }

  /** {@inheritDoc} */
  @Override
  public List<ConfigChangeListener> getChangeListeners(final DN dn)
  {
    return getEntryListeners(dn).getChangeListeners();
  }

  /** Load the configuration-enabled schema that will allow to read configuration file. */
  private Schema loadConfigEnabledSchema() throws InitializationException {
    LDIFEntryReader reader = null;
    try
    {
      final File schemaDir = serverContext.getEnvironment().getSchemaDirectory();
      reader = new LDIFEntryReader(new FileReader(new File(schemaDir, CONFIGURATION_FILE_NAME)));
      reader.setSchema(Schema.getDefaultSchema());
      final Entry entry = reader.readEntry();
      return new SchemaBuilder(Schema.getDefaultSchema()).addSchema(entry, false).toSchema();
    }
    catch (Exception e)
    {
      // TODO : fix message
      throw new InitializationException(LocalizableMessage.raw("Unable to load config-enabled schema"), e);
    }
    finally {
      closeSilently(reader);
    }
  }

  /**
   * Read configuration entries from provided configuration file.
   *
   * @param configFile
   *            LDIF file with configuration entries.
   * @param schema
   *          Schema to validate entries when reading the config file.
   * @throws InitializationException
   *            If an errors occurs.
   */
  private void loadConfiguration(final File configFile, final Schema schema)
      throws InitializationException
  {
    EntryReader reader = null;
    try
    {
      reader = getLDIFReader(configFile, schema);
      backend = new MemoryBackend(schema, reader);
    }
    catch (IOException e)
    {
      throw new InitializationException(
          ERR_CONFIG_FILE_GENERIC_ERROR.get(configFile.getAbsolutePath(), e.getCause()), e);
    }
    finally
    {
      closeSilently(reader);
    }

    // Check that root entry is the expected one
    rootEntry = backend.get(DN_CONFIG_ROOT);
    if (rootEntry == null)
    {
      // fix message : we didn't find the expected root in the file
      throw new InitializationException(ERR_CONFIG_FILE_INVALID_BASE_DN.get(
          configFile.getAbsolutePath(), "", DN_CONFIG_ROOT));
    }
  }

  /**
   * Returns the LDIF reader on configuration entries.
   * <p>
   * It is the responsability of the caller to ensure that reader
   * is closed after usage.
   *
   * @param configFile
   *          LDIF file containing the configuration entries.
   * @param schema
   *          Schema to validate entries when reading the config file.
   * @return the LDIF reader
   * @throws InitializationException
   *           If an error occurs.
   */
  private EntryReader getLDIFReader(final File configFile, final Schema schema)
      throws InitializationException
  {
    LDIFEntryReader reader = null;
    try
    {
      reader = new LDIFEntryReader(new FileReader(configFile));
      reader.setSchema(schema);
    }
    catch (Exception e)
    {
      throw new InitializationException(
          ERR_CONFIG_FILE_CANNOT_OPEN_FOR_READ.get(configFile.getAbsolutePath(), e), e);
    }
    return reader;
  }

  /**
   * Returns the entry listeners attached to the provided DN.
   * <p>
   * If no listener exist for the provided DN, then a new set of empty listeners
   * is created and returned.
   *
   * @param dn
   *          DN of a configuration entry.
   * @return the listeners attached to the corresponding configuration entry.
   */
  private EntryListeners getEntryListeners(final DN dn) {
    EntryListeners entryListeners  = listeners.get(dn);
    if (entryListeners == null) {
      entryListeners = new EntryListeners();
      final EntryListeners previousListeners = listeners.putIfAbsent(dn, entryListeners);
      if (previousListeners != null) {
        entryListeners = previousListeners;
      }
    }
    return entryListeners;
  }

  /**
   * Returns the parent DN of the configuration entry corresponding to the
   * provided DN.
   *
   * @param entryDN
   *          DN of entry to retrieve the parent from.
   * @return the parent DN
   * @throws DirectoryException
   *           If entry has no parent or parent entry does not exist.
   */
  private DN retrieveParentDN(final DN entryDN) throws DirectoryException
  {
    final DN parentDN = entryDN.parent();
    // Entry must have a parent.
    if (parentDN == null)
    {
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, ERR_CONFIG_FILE_ADD_NO_PARENT_DN.get(entryDN));
    }

    // Parent entry must exist.
    if (!backend.contains(parentDN))
    {
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
          ERR_CONFIG_FILE_ADD_NO_PARENT.get(entryDN, parentDN), Converters.to(getMatchedDN(parentDN)), null);
    }
    return parentDN;
  }

  /**
   * Returns the matched DN that is available in the configuration for the
   * provided DN.
   */
  private DN getMatchedDN(final DN dn)
  {
    DN matchedDN = null;
    DN parentDN = dn.parent();
    while (parentDN != null)
    {
      if (backend.contains(parentDN))
      {
        matchedDN = parentDN;
        break;
      }
      parentDN = parentDN.parent();
    }
    return matchedDN;
  }

  /**
   * Find the actual configuration file to use to load configuration, given the
   * standard config file.
   *
   * @param standardConfigFile
   *          "Standard" configuration file provided.
   * @return the actual configuration file to use, which is either the standard
   *         config file provided or the config file corresponding to the last
   *         known good configuration
   * @throws InitializationException
   *           If a problem occurs.
   */
  private File findConfigFileToUse(final File standardConfigFile) throws InitializationException
  {
    File configFileToUse = null;
    if (useLastKnownGoodConfig)
    {
      configFileToUse = new File(standardConfigFile + ".startok");
      if (! configFileToUse.exists())
      {
        logger.warn(WARN_CONFIG_FILE_NO_STARTOK_FILE, configFileToUse.getAbsolutePath(), standardConfigFile);
        useLastKnownGoodConfig = false;
        configFileToUse = standardConfigFile;
      }
      else
      {
        logger.info(NOTE_CONFIG_FILE_USING_STARTOK_FILE, configFileToUse.getAbsolutePath(), standardConfigFile);
      }
    }
    else
    {
      configFileToUse = standardConfigFile;
    }

    try
    {
      if (! configFileToUse.exists())
      {
        throw new InitializationException(ERR_CONFIG_FILE_DOES_NOT_EXIST.get(configFileToUse.getAbsolutePath()));
      }
    }
    catch (Exception e)
    {
      throw new InitializationException(
          ERR_CONFIG_FILE_CANNOT_VERIFY_EXISTENCE.get(configFileToUse.getAbsolutePath(), e));
    }
    return configFileToUse;
  }

  /**
   * Examines the provided result and logs a message if appropriate. If the
   * result code is anything other than {@code SUCCESS}, then it will log an
   * error message. If the operation was successful but admin action is
   * required, then it will log a warning message. If no action is required but
   * messages were generated, then it will log an informational message.
   *
   * @param result
   *          The config change result object that
   * @param entryDN
   *          The DN of the entry that was added, deleted, or modified.
   * @param className
   *          The name of the class for the object that generated the provided
   *          result.
   * @param methodName
   *          The name of the method that generated the provided result.
   */
  private void handleConfigChangeResult(ConfigChangeResult result, DN entryDN, String className, String methodName)
  {
    if (result == null)
    {
      logger.error(ERR_CONFIG_CHANGE_NO_RESULT, className, methodName, entryDN);
      return;
    }

    final ResultCode resultCode = result.getResultCode();
    final boolean adminActionRequired = result.adminActionRequired();
    final List<LocalizableMessage> messages = result.getMessages();

    final String messageBuffer = Utils.joinAsString("  ", messages);
    if (resultCode != ResultCode.SUCCESS)
    {
      logger.error(ERR_CONFIG_CHANGE_RESULT_ERROR, className, methodName, entryDN, resultCode,
          adminActionRequired, messageBuffer);
    }
    else if (adminActionRequired)
    {
      logger.warn(WARN_CONFIG_CHANGE_RESULT_ACTION_REQUIRED, className, methodName, entryDN, messageBuffer);
    }
    else if (messageBuffer.length() > 0)
    {
      logger.debug(INFO_CONFIG_CHANGE_RESULT_MESSAGES, className, methodName, entryDN, messageBuffer);
    }
  }

}
