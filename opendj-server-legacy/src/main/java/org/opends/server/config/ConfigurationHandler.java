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
 * Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.config;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.adapter.server3x.Converters;
import org.forgerock.opendj.config.ConfigurationFramework;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.spi.ConfigAddListener;
import org.forgerock.opendj.config.server.spi.ConfigChangeListener;
import org.forgerock.opendj.config.server.spi.ConfigDeleteListener;
import org.forgerock.opendj.config.server.spi.ConfigurationRepository;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.CancelRequestListener;
import org.forgerock.opendj.ldap.CancelledResultException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Entries;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.LdapResultHandler;
import org.forgerock.opendj.ldap.MemoryBackend;
import org.forgerock.opendj.ldap.RequestContext;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchResultHandler;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.forgerock.opendj.ldap.responses.SearchResultReference;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldif.EntryReader;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.opendj.ldif.LDIFEntryWriter;
import org.forgerock.util.Utils;
import org.forgerock.util.annotations.VisibleForTesting;
import org.opends.server.api.AlertGenerator;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.opends.server.tools.LDIFModify;
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ExistingFileBehavior;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.util.ActivateOnceSDKSchemaIsUsed;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.LDIFWriter;
import org.opends.server.util.TimeThread;

/**
 * Responsible for managing configuration, including listeners on configuration entries.
 * <p>
 * Configuration is represented by configuration entries, persisted on the file system.
 * Configuration entries are initially read from configuration file ("config/config.ldif" by default), then stored
 * in a {@code MemoryBackend} during server uptime.
 * <p>
 * The handler allows to register and unregister some listeners on any configuration entry
 * (add, change or delete listener).
 * Configuration entries can be added, replaced or deleted to the handler.
 * Any change of a configuration entry will trigger the listeners registered for this entry, and will also
 * trigger an update of configuration file.
 * <p>
 * The handler also maintains an up-to-date archive of configuration files.
 */
public class ConfigurationHandler implements ConfigurationRepository, AlertGenerator
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final String CONFIGURATION_FILE_NAME = "02-config.ldif";
  private static final String CLASS_NAME = ConfigurationHandler.class.getName();

  private final ServerContext serverContext;

  /** The complete path to the default configuration file. */
  private File configFile;

  /** Indicates whether to start using the last known good configuration. */
  private boolean useLastKnownGoodConfig;

  /** Indicates whether to maintain a configuration archive. */
  private boolean maintainConfigArchive;

  /** The maximum config archive size to maintain. */
  private int maxConfigArchiveSize;

  /**
   * A SHA-1 digest of the last known configuration. This should only be incorrect if the server
   * configuration file has been manually edited with the server online, which is a bad thing.
   */
  private byte[] configurationDigest;

  /** Backend containing the configuration entries. */
  private MemoryBackend backend;

  /** The config root entry. */
  private Entry rootEntry;

  /** The add/delete/change listeners on configuration entries. */
  private final ConcurrentHashMap<DN, EntryListeners> listeners = new ConcurrentHashMap<>();

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
   * Bootstraps the server configuration.
   * <p>
   * The returned ConfigurationHandler is initialized with a partial schema and must be later
   * re-initialized with the full schema by calling {@link #reinitializeWithFullSchema(Schema)}
   * method once the schema has been fully loaded.
   *
   * @param serverContext
   *          The server context.
   * @return the configuration handler
   * @throws InitializationException
   *           If an error occurs during bootstrapping.
   */
  public static ConfigurationHandler bootstrapConfiguration(ServerContext serverContext)
      throws InitializationException {
    final ConfigurationFramework configFramework = ConfigurationFramework.getInstance();
    try
    {
      if (!configFramework.isInitialized())
      {
        configFramework.initialize();
      }
    }
    catch (ConfigException e)
    {
      LocalizableMessage msg = ERR_CANNOT_INITIALIZE_CONFIGURATION_FRAMEWORK.get(stackTraceToSingleLineString(e));
      throw new InitializationException(msg, e);
    }

    final ConfigurationHandler configHandler = new ConfigurationHandler(serverContext);
    configHandler.initializeWithPartialSchema();
    return configHandler;
  }

  /**
   * Initializes the configuration with an incomplete schema.
   * <p>
   * As configuration contains schema-related items, the initialization of the configuration can
   * only be performed with an incomplete schema before a complete schema is available. Once a
   * complete schema is available, the {@link #reinitializeWithFullSchema(Schema)} method should be
   * called to have a fully validated configuration.
   *
   * @throws InitializationException
   *           If an error occurs.
   */
  @VisibleForTesting
  void initializeWithPartialSchema() throws InitializationException
  {
    File configFileToUse = preInitialization();
    Schema configEnabledSchema = loadSchemaWithConfigurationEnabled();
    loadConfiguration(configFileToUse, configEnabledSchema);
  }

  /**
   * Re-initializes the configuration handler with a fully initialized schema.
   * <p>
   * Previously registered listeners are preserved.
   *
   * @param schema
   *            The server schema, fully initialized.
   * @throws InitializationException
   *            If an error occurs.
   */
  public void reinitializeWithFullSchema(Schema schema) throws InitializationException
  {
    final Map<String, EntryListeners> exportedListeners = exportListeners();
    finalize();
    File configFileToUse = preInitialization();
    loadConfiguration(configFileToUse, schema);
    importListeners(exportedListeners, schema);
  }

  /** Finalizes the configuration handler. */
  @Override
  public void finalize()
  {
    listeners.clear();
    backend.clear();
  }

  /**
   * Prepares the initialization of the handler, returning the up-to-date configuration file to use
   * to load the configuration.
   *
   * @return the file containing the configuration
   * @throws InitializationException
   *            If an error occurs.
   */
  private File preInitialization() throws InitializationException
  {
    final DirectoryEnvironmentConfig environment = serverContext.getEnvironment();
    useLastKnownGoodConfig = environment.useLastKnownGoodConfiguration();
    configFile = environment.getConfigFile();
    File configFileToUse = findConfigFileToUse(configFile);
    ensureArchiveExistsAndIsUpToDate(environment, configFileToUse);
    applyConfigChangesIfNeeded(configFileToUse);
    return configFileToUse;
  }

  /**
   * Returns a copy of the listeners with DN as strings.
   * Use strings to avoid holding copies on the old schema.
   */
  private Map<String, EntryListeners> exportListeners()
  {
    final Map<String, EntryListeners> listenersCopy = new HashMap<>();
    for (Map.Entry<DN, EntryListeners> entry : listeners.entrySet())
    {
      listenersCopy.put(entry.getKey().toString(), entry.getValue());
    }
    return listenersCopy;
  }

  /** Imports the provided listeners into the configuration handler. */
  private void importListeners(Map<String, EntryListeners> listenersCopy, Schema schema)
  {
    for (Map.Entry<String, EntryListeners> entry : listenersCopy.entrySet())
    {
      listeners.put(DN.valueOf(entry.getKey(), schema), entry.getValue());
    }
  }

  @Override
  public Map<String, String> getAlerts()
  {
    Map<String, String> alerts = new LinkedHashMap<>();

    alerts.put(ALERT_TYPE_CANNOT_WRITE_CONFIGURATION, ALERT_DESCRIPTION_CANNOT_WRITE_CONFIGURATION);
    alerts.put(ALERT_TYPE_MANUAL_CONFIG_EDIT_HANDLED, ALERT_DESCRIPTION_MANUAL_CONFIG_EDIT_HANDLED);
    alerts.put(ALERT_TYPE_MANUAL_CONFIG_EDIT_LOST, ALERT_DESCRIPTION_MANUAL_CONFIG_EDIT_LOST);

    return alerts;
  }

  @Override
  public Set<DN> getChildren(DN dn) throws ConfigException
  {
    final ConfigLdapResultHandler resultHandler = new ConfigLdapResultHandler();
    final CollectorSearchResultHandler searchHandler = new CollectorSearchResultHandler();

    SearchRequest searchRequest = Requests.newSearchRequest(dn, SearchScope.SINGLE_LEVEL, Filter.alwaysTrue());
    backend.handleSearch(UNCANCELLABLE_REQUEST_CONTEXT, searchRequest, null, searchHandler, resultHandler);

    if (resultHandler.hasCompletedSuccessfully())
    {
      final Set<DN> children = new HashSet<>();
      for (final Entry entry : searchHandler.getEntries())
      {
        children.add(entry.getName());
      }
      return children;
    }
    throw new ConfigException(ERR_UNABLE_TO_RETRIEVE_CHILDREN_OF_CONFIGURATION_ENTRY.get(dn),
        resultHandler.getResultError());
  }

  @Override
  public String getClassName()
  {
    return CLASS_NAME;
  }

  @Override
  public DN getComponentEntryDN()
  {
    return rootEntry.getName();
  }

  /**
   * Returns the configuration file containing all configuration entries.
   *
   * @return the configuration file
   */
  public File getConfigurationFile()
  {
    return configFile;
  }

  @Override
  public Entry getEntry(final DN dn) throws ConfigException
  {
    Entry entry = backend.get(dn);
    if (entry != null)
    {
      entry = Entries.unmodifiableEntry(entry);
    }
    return entry;
  }

  /**
   * Returns the configuration root entry.
   *
   * @return the root entry
   */
  public Entry getRootEntry()
  {
    return rootEntry;
  }

  @Override
  public List<ConfigAddListener> getAddListeners(final DN dn)
  {
    return getEntryListeners(dn).getAddListeners();
  }

  @Override
  public List<ConfigChangeListener> getChangeListeners(final DN dn)
  {
    return getEntryListeners(dn).getChangeListeners();
  }

  @Override
  public List<ConfigDeleteListener> getDeleteListeners(final DN dn)
  {
    return getEntryListeners(dn).getDeleteListeners();
  }

  @Override
  public boolean hasEntry(final DN dn) throws ConfigException
  {
    return backend.get(dn) != null;
  }

  /**
   * Search the configuration entries.
   *
   * @param searchOperation
   *          Defines the search to perform
   */
  public void search(SearchOperation searchOperation)
  {
    // Leave all filtering to the SearchResultHandlerAdapter
    SearchRequest request = Requests.newSearchRequest(
        searchOperation.getBaseDN(), searchOperation.getScope(), Filter.alwaysTrue(), "*", "+");

    LdapResultHandlerAdapter resultHandler = new LdapResultHandlerAdapter(searchOperation);
    SearchResultHandler entryHandler = new SearchResultHandlerAdapter(searchOperation, resultHandler);
    backend.handleSearch(UNCANCELLABLE_REQUEST_CONTEXT, request, null, entryHandler, resultHandler);
  }

  /**
   * Retrieves the number of subordinates for the requested entry.
   *
   * @param entryDN
   *          The distinguished name of the entry.
   * @param subtree
   *          {@code true} to include all entries from the requested entry to the lowest level in
   *          the tree or {@code false} to only include the entries immediately below the requested
   *          entry.
   * @return The number of subordinate entries
   * @throws ConfigException
   *           If a problem occurs while trying to retrieve the entry.
   */
  public long numSubordinates(final DN entryDN, final boolean subtree) throws ConfigException
  {
    final ConfigLdapResultHandler resultHandler = new ConfigLdapResultHandler();
    final CollectorSearchResultHandler searchHandler = new CollectorSearchResultHandler();
    final SearchScope scope = subtree ? SearchScope.SUBORDINATES : SearchScope.SINGLE_LEVEL;
    final SearchRequest searchRequest = Requests.newSearchRequest(entryDN, scope, Filter.alwaysTrue());
    backend.handleSearch(UNCANCELLABLE_REQUEST_CONTEXT, searchRequest, null, searchHandler, resultHandler);

    if (resultHandler.hasCompletedSuccessfully())
    {
      return searchHandler.getEntries().size();
    }
    throw new ConfigException(ERR_UNABLE_TO_RETRIEVE_CHILDREN_OF_CONFIGURATION_ENTRY.get(entryDN),
        resultHandler.getResultError());
  }

  /**
   * Add a configuration entry.
   * <p>
   * The add is performed only if all Add listeners on the parent entry accept the changes. Once the
   * change is accepted, entry is effectively added and all Add listeners are called again to apply
   * the change resulting from this new entry.
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

    final DN parentDN = retrieveParentDNForAdd(entryDN);

    // Iterate through add listeners to make sure the new entry is acceptable.
    final List<ConfigAddListener> addListeners = getAddListeners(parentDN);
    final LocalizableMessageBuilder unacceptableReason = new LocalizableMessageBuilder();
    for (final ConfigAddListener listener : addListeners)
    {
      if (!listener.configAddIsAcceptable(entry, unacceptableReason))
      {
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, ERR_CONFIG_FILE_ADD_REJECTED_BY_LISTENER.get(
            entryDN, parentDN, unacceptableReason));
      }
    }

    // Add the entry.
    final ConfigLdapResultHandler resultHandler = new ConfigLdapResultHandler();
    backend.handleAdd(UNCANCELLABLE_REQUEST_CONTEXT, Requests.newAddRequest(entry), null, resultHandler);

    if (!resultHandler.hasCompletedSuccessfully())
    {
      LdapException ex = resultHandler.getResultError();
      throw new DirectoryException(ex.getResult().getResultCode(),
          ERR_CONFIG_FILE_ADD_FAILED.get(entryDN, parentDN, ex.getLocalizedMessage()), ex);
    }
    writeUpdatedConfig();

    // Notify all the add listeners to apply the new configuration entry.
    final ConfigChangeResult ccr = new ConfigChangeResult();
    for (final ConfigAddListener listener : addListeners)
    {
      final ConfigChangeResult result = listener.applyConfigurationAdd(entry);
      ccr.aggregate(result);
      handleConfigChangeResult(result, entry.getName(), listener.getClass().getName(), "applyConfigurationAdd");
    }

    if (ccr.getResultCode() != ResultCode.SUCCESS)
    {
      final String reasons = Utils.joinAsString(".  ", ccr.getMessages());
      throw new DirectoryException(ccr.getResultCode(), ERR_CONFIG_FILE_ADD_APPLY_FAILED.get(reasons));
    }
  }

  /**
   * Delete a configuration entry.
   * <p>
   * The delete is performed only if all Delete listeners on the parent entry accept the changes.
   * Once the change is accepted, entry is effectively deleted and all Delete listeners are called
   * again to apply the change resulting from this deletion.
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
          ERR_CONFIG_FILE_DELETE_NO_SUCH_ENTRY.get(dn), getMatchedDN(dn), null);
    }

    // Entry must not have children.
    try
    {
      if (!getChildren(dn).isEmpty())
      {
        throw new DirectoryException(ResultCode.NOT_ALLOWED_ON_NONLEAF, ERR_CONFIG_FILE_DELETE_HAS_CHILDREN.get(dn));
      }
    }
    catch (ConfigException e)
    {
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          ERR_CONFIG_BACKEND_CANNOT_DELETE_ENTRY.get(stackTraceToSingleLineString(e)), e);
    }

    final DN parentDN = retrieveParentDNForDelete(dn);

    // Iterate through delete listeners to make sure the deletion is acceptable.
    final List<ConfigDeleteListener> deleteListeners = getDeleteListeners(parentDN);
    final LocalizableMessageBuilder unacceptableReason = new LocalizableMessageBuilder();
    final Entry entry = backend.get(dn);
    for (final ConfigDeleteListener listener : deleteListeners)
    {
      if (!listener.configDeleteIsAcceptable(entry, unacceptableReason))
      {
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
            ERR_CONFIG_FILE_DELETE_REJECTED_BY_LISTENER.get(entry, parentDN, unacceptableReason));
      }
    }

    // Delete the entry and all listeners on the entry
    final ConfigLdapResultHandler resultHandler = new ConfigLdapResultHandler();
    backend.handleDelete(UNCANCELLABLE_REQUEST_CONTEXT, Requests.newDeleteRequest(dn), null, resultHandler);
    listeners.remove(dn);

    if (!resultHandler.hasCompletedSuccessfully())
    {
      LdapException ex = resultHandler.getResultError();
      throw new DirectoryException(ex.getResult().getResultCode(),
          ERR_CONFIG_FILE_DELETE_FAILED.get(dn, parentDN, ex.getLocalizedMessage()), ex);
    }
    writeUpdatedConfig();

    // Notify all the delete listeners that the entry has been removed.
    final ConfigChangeResult ccr = new ConfigChangeResult();
    for (final ConfigDeleteListener listener : deleteListeners)
    {
      final ConfigChangeResult result = listener.applyConfigurationDelete(entry);
      ccr.aggregate(result);
      handleConfigChangeResult(result, dn, listener.getClass().getName(), "applyConfigurationDelete");
    }

    if (ccr.getResultCode() != ResultCode.SUCCESS)
    {
      final String reasons = Utils.joinAsString(".  ", ccr.getMessages());
      throw new DirectoryException(ccr.getResultCode(), ERR_CONFIG_FILE_DELETE_APPLY_FAILED.get(reasons));
    }
  }

  /**
   * Replaces the old configuration entry with the new configuration entry provided.
   * <p>
   * The replacement is performed only if all Change listeners on the entry accept the changes. Once
   * the change is accepted, entry is effectively replaced and all Change listeners are called again
   * to apply the change resulting from the replacement.
   *
   * @param oldEntry
   *          The original entry that is being replaced.
   * @param newEntry
   *          The new entry to use in place of the existing entry with the same DN.
   * @throws DirectoryException
   *           If a problem occurs while trying to replace the entry.
   */
  @ActivateOnceSDKSchemaIsUsed("uncomment code down below in this method")
  public void replaceEntry(final Entry oldEntry, final Entry newEntry) throws DirectoryException
  {
    final DN newEntryDN = newEntry.getName();
    if (!backend.contains(newEntryDN))
    {
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
          ERR_CONFIG_FILE_MODIFY_NO_SUCH_ENTRY.get(oldEntry), getMatchedDN(newEntryDN), null);
    }

    // TODO : add objectclass and attribute to the config schema in order to get this code run
    // if (!Entries.getStructuralObjectClass(oldEntry, configEnabledSchema)
    // .equals(Entries.getStructuralObjectClass(newEntry, configEnabledSchema)))
    // {
    // throw new DirectoryException(ResultCode.NO_SUCH_OBJECT,
    // ERR_CONFIG_FILE_MODIFY_STRUCTURAL_CHANGE_NOT_ALLOWED.get(entryDN));
    // }

    // Iterate through change listeners to make sure the change is acceptable.
    final List<ConfigChangeListener> changeListeners = getChangeListeners(newEntryDN);
    final LocalizableMessageBuilder unacceptableReason = new LocalizableMessageBuilder();
    for (ConfigChangeListener listeners : changeListeners)
    {
      if (!listeners.configChangeIsAcceptable(newEntry, unacceptableReason))
      {
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
            ERR_CONFIG_FILE_MODIFY_REJECTED_BY_CHANGE_LISTENER.get(newEntryDN, unacceptableReason));
      }
    }

    // Replace the old entry with new entry.
    ModifyRequest modifyRequest = Entries.diffEntries(oldEntry, newEntry, Entries.diffOptions().attributes("*", "+"));
    final ConfigLdapResultHandler resultHandler = new ConfigLdapResultHandler();
    backend.handleModify(UNCANCELLABLE_REQUEST_CONTEXT, modifyRequest, null, resultHandler);

    if (!resultHandler.hasCompletedSuccessfully())
    {
      LdapException ex = resultHandler.getResultError();
      throw new DirectoryException(ex.getResult().getResultCode(),
          ERR_CONFIG_FILE_MODIFY_FAILED.get(newEntryDN, newEntryDN, ex.getLocalizedMessage()), ex);
    }
    writeUpdatedConfig();

    // Notify all the change listeners of the update.
    final ConfigChangeResult ccr = new ConfigChangeResult();
    for (final ConfigChangeListener listener : changeListeners)
    {
      if (!changeListeners.contains(listener))
      {
        // some listeners may have de-registered themselves due to previous changes, ignore them
        continue;
      }
      final ConfigChangeResult result = listener.applyConfigurationChange(newEntry);
      ccr.aggregate(result);
      handleConfigChangeResult(result, newEntryDN, listener.getClass().getName(), "applyConfigurationChange");
    }

    if (ccr.getResultCode() != ResultCode.SUCCESS)
    {
      String reasons = Utils.joinAsString(".  ", ccr.getMessages());
      throw new DirectoryException(ccr.getResultCode(), ERR_CONFIG_FILE_MODIFY_APPLY_FAILED.get(reasons));
    }
  }

  @Override
  public void registerAddListener(final DN dn, final ConfigAddListener listener)
  {
    getEntryListeners(dn).registerAddListener(listener);
  }

  @Override
  public void registerDeleteListener(final DN dn, final ConfigDeleteListener listener)
  {
    getEntryListeners(dn).registerDeleteListener(listener);
  }

  @Override
  public void registerChangeListener(final DN dn, final ConfigChangeListener listener)
  {
    getEntryListeners(dn).registerChangeListener(listener);
  }

  @Override
  public void deregisterAddListener(final DN dn, final ConfigAddListener listener)
  {
    getEntryListeners(dn).deregisterAddListener(listener);
  }

  @Override
  public void deregisterDeleteListener(final DN dn, final ConfigDeleteListener listener)
  {
    getEntryListeners(dn).deregisterDeleteListener(listener);
  }

  @Override
  public boolean deregisterChangeListener(final DN dn, final ConfigChangeListener listener)
  {
    return getEntryListeners(dn).deregisterChangeListener(listener);
  }

  /**
   * Writes the current configuration to LDIF with the provided export configuration.
   *
   * @param exportConfig
   *          The configuration to use for the export.
   * @throws DirectoryException
   *           If a problem occurs while writing the LDIF.
   */
  public void writeLDIF(LDIFExportConfig exportConfig) throws DirectoryException
  {
    try (LDIFEntryWriter writer = new LDIFEntryWriter(exportConfig.getWriter()))
    {
      writer.writeComment(INFO_CONFIG_FILE_HEADER.get().toString());
      for (Entry entry : new ArrayList<Entry>(backend.getAll()))
      {
        try
        {
          writer.writeEntry(entry);
        }
        catch (IOException e)
        {
          logger.traceException(e);
          LocalizableMessage message = ERR_CONFIG_FILE_WRITE_ERROR.get(entry.getName(), e);
          throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
        }
      }
    }
    catch (IOException e)
    {
      logger.traceException(e);
      LocalizableMessage message = ERR_CONFIG_LDIF_WRITE_ERROR.get(e);
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
    }
  }

  /**
   * Generates a configuration file with the ".startok" suffix, representing a configuration
   * file that has a successful start.
   * <p>
   * This method must not be called if configuration can't be correctly initialized.
   * <p>
   * The actual generation is skipped if last known good configuration is used.
   */
  public void writeSuccessfulStartupConfig()
  {
    if (useLastKnownGoodConfig)
    {
      // The server was started with the "last known good" configuration, so we
      // shouldn't overwrite it with something that is probably bad.
      return;
    }

    String startOKFilePath = configFile + ".startok";
    String tempFilePath = startOKFilePath + ".tmp";
    String oldFilePath = startOKFilePath + ".old";

    // Copy the current config file to a temporary file.
    File tempFile = new File(tempFilePath);
    try (FileInputStream inputStream = new FileInputStream(configFile))
    {
      try (FileOutputStream outputStream = new FileOutputStream(tempFilePath, false))
      {
        try
        {
          byte[] buffer = new byte[8192];
          while (true)
          {
            int bytesRead = inputStream.read(buffer);
            if (bytesRead < 0)
            {
              break;
            }

            outputStream.write(buffer, 0, bytesRead);
          }
        }
        catch (IOException e)
        {
          logger.traceException(e);
          logger.error(ERR_STARTOK_CANNOT_WRITE, configFile, tempFilePath, getExceptionMessage(e));
          return;
        }
      }
      catch (FileNotFoundException e)
      {
        logger.traceException(e);
        logger.error(ERR_STARTOK_CANNOT_OPEN_FOR_WRITING, tempFilePath, getExceptionMessage(e));
        return;
      }
      catch (IOException e)
      {
        logger.traceException(e);
      }
    }
    catch (FileNotFoundException e)
    {
      logger.traceException(e);
      logger.error(ERR_STARTOK_CANNOT_OPEN_FOR_READING, configFile, getExceptionMessage(e));
      return;
    }
    catch (IOException e)
    {
      logger.traceException(e);
    }

    // If a ".startok" file already exists, then move it to an ".old" file.
    File oldFile = new File(oldFilePath);
    try
    {
      if (oldFile.exists())
      {
        oldFile.delete();
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }

    File startOKFile = new File(startOKFilePath);
    try
    {
      if (startOKFile.exists())
      {
        startOKFile.renameTo(oldFile);
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }

    // Rename the temp file to the ".startok" file.
    try
    {
      tempFile.renameTo(startOKFile);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      logger.error(ERR_STARTOK_CANNOT_RENAME, tempFilePath, startOKFilePath, getExceptionMessage(e));
      return;
    }

    // Remove the ".old" file if there is one.
    try
    {
      if (oldFile.exists())
      {
        oldFile.delete();
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }
  }

  private void writeUpdatedConfig() throws DirectoryException
  {
    // FIXME -- This needs support for encryption.

    // Calculate an archive for the current server configuration file and see if
    // it matches what we expect. If not, then the file has been manually
    // edited with the server online which is a bad thing. In that case, we'll
    // copy the current config off to the side before writing the new config
    // so that the manual changes don't get lost but also don't get applied.
    // Also, send an admin alert notifying administrators about the problem.
    if (maintainConfigArchive)
    {
      try
      {
        byte[] currentDigest = calculateConfigDigest();
        if (!Arrays.equals(configurationDigest, currentDigest))
        {
          File existingCfg = configFile;
          File newConfigFile =
              new File(existingCfg.getParent(), "config.manualedit-" + TimeThread.getGMTTime() + ".ldif");
          int counter = 2;
          while (newConfigFile.exists())
          {
            newConfigFile = new File(newConfigFile.getAbsolutePath() + "." + counter);
          }

          try (FileInputStream inputStream = new FileInputStream(existingCfg);
              FileOutputStream outputStream = new FileOutputStream(newConfigFile))
          {
            byte[] buffer = new byte[8192];
            while (true)
            {
              int bytesRead = inputStream.read(buffer);
              if (bytesRead < 0)
              {
                break;
              }
              outputStream.write(buffer, 0, bytesRead);
            }
          }

          LocalizableMessage message =
              WARN_CONFIG_MANUAL_CHANGES_DETECTED.get(configFile, newConfigFile.getAbsolutePath());
          logger.warn(message);

          DirectoryServer.sendAlertNotification(this, ALERT_TYPE_MANUAL_CONFIG_EDIT_HANDLED, message);
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);

        LocalizableMessage message = ERR_CONFIG_MANUAL_CHANGES_LOST.get(configFile, stackTraceToSingleLineString(e));
        logger.error(message);

        DirectoryServer.sendAlertNotification(this, ALERT_TYPE_MANUAL_CONFIG_EDIT_HANDLED, message);
      }
    }

    // Write the new configuration to a temporary file.
    String tempConfig = configFile + ".tmp";
    try
    {
      LDIFExportConfig exportConfig = new LDIFExportConfig(tempConfig, ExistingFileBehavior.OVERWRITE);

      // FIXME -- Add all the appropriate configuration options.
      writeLDIF(exportConfig);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message =
          ERR_CONFIG_FILE_WRITE_CANNOT_EXPORT_NEW_CONFIG.get(tempConfig, stackTraceToSingleLineString(e));
      logger.error(message);

      DirectoryServer.sendAlertNotification(this, ALERT_TYPE_CANNOT_WRITE_CONFIGURATION, message);
      return;
    }

    // Delete the previous version of the configuration and rename the new one.
    try
    {
      File actualConfig = configFile;
      File tmpConfig = new File(tempConfig);
      renameFile(tmpConfig, actualConfig);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message =
          ERR_CONFIG_FILE_WRITE_CANNOT_RENAME_NEW_CONFIG.get(tempConfig, configFile, stackTraceToSingleLineString(e));
      logger.error(message);

      DirectoryServer.sendAlertNotification(this, ALERT_TYPE_CANNOT_WRITE_CONFIGURATION, message);
      return;
    }

    configurationDigest = calculateConfigDigest();

    // Try to write the archive for the new configuration.
    if (maintainConfigArchive)
    {
      writeConfigArchive();
    }
  }

  /** Request context to be used when requesting the internal backend. */
  private static final RequestContext UNCANCELLABLE_REQUEST_CONTEXT = new RequestContext()
  {
    @Override
    public void removeCancelRequestListener(final CancelRequestListener listener)
    {
      // nothing to do
    }

    @Override
    public int getMessageID()
    {
      return -1;
    }

    @Override
    public void checkIfCancelled(final boolean signalTooLate) throws CancelledResultException
    {
      // nothing to do
    }

    @Override
    public void addCancelRequestListener(final CancelRequestListener listener)
    {
      // nothing to do
    }
  };

  /** Holds add, change and delete listeners for a given configuration entry. */
  private static class EntryListeners
  {
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

  /** Handler for search results collecting all received entries. */
  private static final class CollectorSearchResultHandler implements SearchResultHandler
  {
    private final Set<Entry> entries = new HashSet<>();

    Set<Entry> getEntries()
    {
      return entries;
    }

    @Override
    public boolean handleReference(SearchResultReference reference)
    {
      throw new UnsupportedOperationException("Search references are not supported for configuration entries.");
    }

    @Override
    public boolean handleEntry(SearchResultEntry entry)
    {
      entries.add(entry);
      return true;
    }
  }

  /** Handler for search results redirecting to a SearchOperation. */
  private static final class SearchResultHandlerAdapter implements SearchResultHandler
  {
    private final SearchOperation searchOperation;
    private final LdapResultHandlerAdapter resultHandler;

    private SearchResultHandlerAdapter(SearchOperation searchOperation, LdapResultHandlerAdapter resultHandler)
    {
      this.searchOperation = searchOperation;
      this.resultHandler = resultHandler;
    }

    @Override
    public boolean handleReference(SearchResultReference reference)
    {
      throw new UnsupportedOperationException("Search references are not supported for configuration entries.");
    }

    @Override
    public boolean handleEntry(SearchResultEntry entry)
    {
      org.opends.server.types.Entry serverEntry = Converters.to(entry);
      serverEntry.processVirtualAttributes();
      return !filterMatchesEntry(serverEntry) || searchOperation.returnEntry(serverEntry, null);
    }

    private boolean filterMatchesEntry(org.opends.server.types.Entry serverEntry)
    {
      try
      {
        return searchOperation.getFilter().matchesEntry(serverEntry);
      }
      catch (DirectoryException e)
      {
        resultHandler.handleException(LdapException.newLdapException(ResultCode.UNWILLING_TO_PERFORM, e));
        return false;
      }
    }
  }

  /** Handler for LDAP operations. */
  private static final class ConfigLdapResultHandler implements LdapResultHandler<Result>
  {
    private LdapException resultError;

    LdapException getResultError()
    {
      return resultError;
    }

    boolean hasCompletedSuccessfully()
    {
      return resultError == null;
    }

    @Override
    public void handleResult(Result result)
    {
      // nothing to do
    }

    @Override
    public void handleException(LdapException exception)
    {
      resultError = exception;
    }
  }

  /** Handler for LDAP operations redirecting to a SearchOperation. */
  private static final class LdapResultHandlerAdapter implements LdapResultHandler<Result>
  {
    private final SearchOperation searchOperation;

    LdapResultHandlerAdapter(SearchOperation searchOperation)
    {
      this.searchOperation = searchOperation;
    }

    @Override
    public void handleResult(Result result)
    {
      searchOperation.setResultCode(result.getResultCode());
    }

    @Override
    public void handleException(LdapException exception)
    {
      searchOperation.setResultCode(exception.getResult().getResultCode());
      searchOperation.setErrorMessage(
          new LocalizableMessageBuilder(LocalizableMessage.raw(exception.getLocalizedMessage())));
      String matchedDNString = exception.getResult().getMatchedDN();
      if (matchedDNString != null)
      {
        searchOperation.setMatchedDN(DN.valueOf(matchedDNString));
      }
    }
  }

  /**
   * Find the actual configuration file to use to load configuration, given the standard
   * configuration file.
   *
   * @param standardConfigFile
   *          "Standard" configuration file provided.
   * @return the actual configuration file to use, which is either the standard config file provided
   *         or the config file corresponding to the last known good configuration
   * @throws InitializationException
   *           If a problem occurs.
   */
  private File findConfigFileToUse(final File standardConfigFile) throws InitializationException
  {
    File fileToUse;
    if (useLastKnownGoodConfig)
    {
      fileToUse = new File(standardConfigFile.getPath() + ".startok");
      if (fileToUse.exists())
      {
        logger.info(NOTE_CONFIG_FILE_USING_STARTOK_FILE, fileToUse.getAbsolutePath(), standardConfigFile);
      }
      else
      {
        logger.warn(WARN_CONFIG_FILE_NO_STARTOK_FILE, fileToUse.getAbsolutePath(), standardConfigFile);
        useLastKnownGoodConfig = false;
        fileToUse = standardConfigFile;
      }
    }
    else
    {
      fileToUse = standardConfigFile;
    }

    boolean fileExists = false;
    try
    {
      fileExists = fileToUse.exists();
    }
    catch (Exception e)
    {
      logger.traceException(e);
      throw new InitializationException(ERR_CONFIG_FILE_CANNOT_VERIFY_EXISTENCE.get(fileToUse.getAbsolutePath(), e));
    }
    if (!fileExists)
    {
      throw new InitializationException(ERR_CONFIG_FILE_DOES_NOT_EXIST.get(fileToUse.getAbsolutePath()));
    }
    return fileToUse;
  }

  /** Load the configuration-enabled schema that will allow to read the configuration file. */
  private Schema loadSchemaWithConfigurationEnabled() throws InitializationException
  {
    final File schemaDir = serverContext.getEnvironment().getSchemaDirectory();
    try (LDIFEntryReader reader = new LDIFEntryReader(new FileReader(new File(schemaDir, CONFIGURATION_FILE_NAME))))
    {
      final Schema schema = Schema.getDefaultSchema();
      reader.setSchema(schema);
      final Entry entry = reader.readEntry();
      return new SchemaBuilder(schema).addSchema(entry, false).toSchema().asNonStrictSchema();
    }
    catch (Exception e)
    {
      throw new InitializationException(
          ERR_UNABLE_TO_LOAD_CONFIGURATION_ENABLED_SCHEMA.get(stackTraceToSingleLineString(e)), e);
    }
  }

  /**
   * Read configuration entries from provided configuration file.
   *
   * @param configFile
   *          LDIF file with configuration entries.
   * @param schema
   *          Schema to validate entries when reading the config file.
   * @throws InitializationException
   *           If an errors occurs.
   */
  private void loadConfiguration(final File configFile, final Schema schema) throws InitializationException
  {
    try (EntryReader reader = getLDIFReader(configFile, schema))
    {
      backend = new MemoryBackend(schema, reader);
    }
    catch (IOException e)
    {
      throw new InitializationException(
          ERR_CONFIG_FILE_GENERIC_ERROR.get(configFile.getAbsolutePath(), e.getCause()), e);
    }

    // Check that root entry is the expected one
    rootEntry = backend.get(DN_CONFIG_ROOT);
    if (rootEntry == null)
    {
      // fix message : we didn't find the expected root in the file
      throw new InitializationException(
          ERR_CONFIG_FILE_INVALID_BASE_DN.get(configFile.getAbsolutePath(), "", DN_CONFIG_ROOT));
    }
  }

  /**
   * Ensure there is an-up-to-date configuration archive.
   * <p>
   * Check to see if a configuration archive exists. If not, then create one.
   * If so, then check whether the current configuration matches the last
   * configuration in the archive. If it doesn't, then archive it.
   */
  private void ensureArchiveExistsAndIsUpToDate(DirectoryEnvironmentConfig environment, File configFileToUse)
      throws InitializationException
  {
    maintainConfigArchive = environment.maintainConfigArchive();
    maxConfigArchiveSize = environment.getMaxConfigArchiveSize();
    if (maintainConfigArchive && !useLastKnownGoodConfig)
    {
      try
      {
        configurationDigest = calculateConfigDigest();
      }
      catch (DirectoryException e)
      {
        throw new InitializationException(e.getMessageObject(), e.getCause());
      }

      File archiveDirectory = new File(configFileToUse.getParent(), CONFIG_ARCHIVE_DIR_NAME);
      if (archiveDirectory.exists())
      {
        try
        {
          byte[] lastDigest = getLastConfigDigest(archiveDirectory);
          if (!Arrays.equals(configurationDigest, lastDigest))
          {
            writeConfigArchive();
          }
        }
        catch (DirectoryException e)
        {
          throw new InitializationException(e.getMessageObject(), e.getCause());
        }
      }
      else
      {
        writeConfigArchive();
      }
    }
  }

  /** Writes the current configuration to the configuration archive. This will be a best-effort attempt. */
  private void writeConfigArchive()
  {
    if (!maintainConfigArchive)
    {
      return;
    }
    File archiveDirectory = new File(configFile.getParentFile(), CONFIG_ARCHIVE_DIR_NAME);
    try
    {
      createArchiveDirectoryIfNeeded(archiveDirectory);
      File archiveFile = getNewArchiveFile(archiveDirectory);
      copyCurrentConfigFileToArchiveFile(archiveFile);
      removeOldArchiveFilesIfNeeded(archiveDirectory);
    }
    catch (DirectoryException e)
    {
      LocalizableMessage message = e.getMessageObject();
      logger.error(message);
      DirectoryServer.sendAlertNotification(this, ALERT_TYPE_CANNOT_WRITE_CONFIGURATION, message);
    }
  }

  private void createArchiveDirectoryIfNeeded(File archiveDirectory) throws DirectoryException
  {
    if (!archiveDirectory.exists())
    {
      try
      {
        if (!archiveDirectory.mkdirs())
        {
          throw new DirectoryException(ResultCode.UNDEFINED,
              ERR_CONFIG_FILE_CANNOT_CREATE_ARCHIVE_DIR_NO_REASON.get(archiveDirectory.getAbsolutePath()));
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);
        throw new DirectoryException(ResultCode.UNDEFINED,
            ERR_CONFIG_FILE_CANNOT_CREATE_ARCHIVE_DIR.get(archiveDirectory.getAbsolutePath(),
            stackTraceToSingleLineString(e)), e);
      }
    }
  }

  private File getNewArchiveFile(File archiveDirectory) throws DirectoryException
  {
    try
    {
      String timestamp = TimeThread.getGMTTime();
      File archiveFile = new File(archiveDirectory, "config-" + timestamp + ".gz");
      if (archiveFile.exists())
      {
        int counter = 1;
        do
        {
          counter++;
          archiveFile = new File(archiveDirectory, "config-" + timestamp + "-" + counter + ".gz");
        }
        while (archiveFile.exists());
      }
      return archiveFile;
    }
    catch (Exception e)
    {
      logger.traceException(e);
      throw new DirectoryException(ResultCode.UNDEFINED,
          ERR_CONFIG_FILE_CANNOT_WRITE_CONFIG_ARCHIVE.get(stackTraceToSingleLineString(e)));
    }
  }

  /** Copy the current configuration file to the archive configuration file. */
  private void copyCurrentConfigFileToArchiveFile(File archiveFile) throws DirectoryException
  {
    byte[] buffer = new byte[8192];
    try(FileInputStream inputStream = new FileInputStream(configFile);
        GZIPOutputStream outputStream = new GZIPOutputStream(new FileOutputStream(archiveFile)))
    {
      int bytesRead = inputStream.read(buffer);
      while (bytesRead > 0)
      {
        outputStream.write(buffer, 0, bytesRead);
        bytesRead = inputStream.read(buffer);
      }
    }
    catch (IOException e)
    {
      logger.traceException(e);
      throw new DirectoryException(ResultCode.UNDEFINED,
          ERR_CONFIG_FILE_CANNOT_WRITE_CONFIG_ARCHIVE.get(stackTraceToSingleLineString(e)));
    }
  }

  /** Deletes old archives files if we should enforce a maximum number of archived configurations. */
  private void removeOldArchiveFilesIfNeeded(File archiveDirectory)
  {
    if (maxConfigArchiveSize > 0)
    {
      String[] archivedFileList = archiveDirectory.list();
      int numToDelete = archivedFileList.length - maxConfigArchiveSize;
      if (numToDelete > 0)
      {
        Set<String> archiveSet = new TreeSet<>();
        for (String name : archivedFileList)
        {
          if (!name.startsWith("config-"))
          {
            continue;
          }
          // Simply ordering by filename should work, even when there are
          // timestamp conflicts, because the dash comes before the period in
          // the ASCII character set.
          archiveSet.add(name);
        }
        Iterator<String> iterator = archiveSet.iterator();
        for (int i = 0; i < numToDelete && iterator.hasNext(); i++)
        {
          File archive = new File(archiveDirectory, iterator.next());
          try
          {
            archive.delete();
          }
          catch (Exception e)
          {
            // do nothing
          }
        }
      }
    }
  }

  /**
   * Looks at the existing archive directory, finds the latest archive file, and calculates a SHA-1
   * digest of that file.
   *
   * @return The calculated digest of the most recent archived configuration file.
   * @throws DirectoryException
   *           If a problem occurs while calculating the digest.
   */
  private byte[] getLastConfigDigest(File archiveDirectory) throws DirectoryException
  {
    int latestCounter = 0;
    long latestTimestamp = -1;
    String latestFileName = null;
    for (String name : archiveDirectory.list())
    {
      if (!name.startsWith("config-"))
      {
        continue;
      }
      int dotPos = name.indexOf('.', 7);
      if (dotPos < 0)
      {
        continue;
      }
      int dashPos = name.indexOf('-', 7);
      if (dashPos < 0)
      {
        try
        {
          ByteString ts = ByteString.valueOfUtf8(name.substring(7, dotPos));
          long timestamp = GeneralizedTimeSyntax.decodeGeneralizedTimeValue(ts);
          if (timestamp > latestTimestamp)
          {
            latestFileName = name;
            latestTimestamp = timestamp;
            latestCounter = 0;
            continue;
          }
        }
        catch (Exception e)
        {
          continue;
        }
      }
      else
      {
        try
        {
          ByteString ts = ByteString.valueOfUtf8(name.substring(7, dashPos));
          long timestamp = GeneralizedTimeSyntax.decodeGeneralizedTimeValue(ts);
          int counter = Integer.parseInt(name.substring(dashPos + 1, dotPos));

          if (timestamp > latestTimestamp
              || (timestamp == latestTimestamp && counter > latestCounter))
          {
            latestFileName = name;
            latestTimestamp = timestamp;
            latestCounter = counter;
            continue;
          }
        }
        catch (Exception e)
        {
          continue;
        }
      }
    }

    if (latestFileName == null)
    {
      return null;
    }

    File latestFile = new File(archiveDirectory, latestFileName);
    try (GZIPInputStream inputStream = new GZIPInputStream(new FileInputStream(latestFile)))
    {
      return calculateDigest(inputStream);
    }
    catch (Exception e)
    {
      LocalizableMessage message =
          ERR_CONFIG_CANNOT_CALCULATE_DIGEST.get(latestFile.getAbsolutePath(), stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
    }
  }

  /**
   * Calculates a SHA-1 digest of the current configuration file.
   *
   * @return The calculated configuration digest.
   * @throws DirectoryException
   *           If a problem occurs while calculating the digest.
   */
  private byte[] calculateConfigDigest() throws DirectoryException
  {
    try (InputStream inputStream = new FileInputStream(configFile))
    {
      return calculateDigest(inputStream);
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_CONFIG_CANNOT_CALCULATE_DIGEST.get(configFile, stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(), message, e);
    }
  }

  private byte[] calculateDigest(InputStream inputStream) throws NoSuchAlgorithmException, IOException
  {
    MessageDigest sha1Digest = MessageDigest.getInstance(MESSAGE_DIGEST_ALGORITHM_SHA_1);
    byte[] buffer = new byte[8192];
    while (true)
    {
      int bytesRead = inputStream.read(buffer);
      if (bytesRead < 0)
      {
        break;
      }
      sha1Digest.update(buffer, 0, bytesRead);
    }
    return sha1Digest.digest();
  }

  /**
   * Applies the updates in the provided changes file to the content in the specified source file.
   * The result will be written to a temporary file, the current source file will be moved out of
   * place, and then the updated file will be moved into the place of the original file. The changes
   * file will also be renamed so it won't be applied again. <BR>
   * <BR>
   * If any problems are encountered, then the config initialization process will be aborted.
   *
   * @param sourceFile
   *          The LDIF file containing the source data.
   * @param changesFile
   *          The LDIF file containing the changes to apply.
   * @throws IOException
   *           If a problem occurs while performing disk I/O.
   * @throws LDIFException
   *           If a problem occurs while trying to interpret the data.
   */
  private void applyChangesFile(File sourceFile, File changesFile) throws IOException, LDIFException
  {
    // Create the appropriate LDIF readers and writer.
    LDIFImportConfig sourceImportCfg = new LDIFImportConfig(sourceFile.getAbsolutePath());
    sourceImportCfg.setValidateSchema(false);

    LDIFImportConfig changesImportCfg = new LDIFImportConfig(changesFile.getAbsolutePath());
    changesImportCfg.setValidateSchema(false);

    String tempFile = changesFile.getAbsolutePath() + ".tmp";
    LDIFExportConfig exportConfig = new LDIFExportConfig(tempFile, ExistingFileBehavior.OVERWRITE);

    List<LocalizableMessage> errorList = new LinkedList<>();
    boolean successful;
    try (LDIFReader sourceReader = new LDIFReader(sourceImportCfg);
        LDIFReader changesReader = new LDIFReader(changesImportCfg);
        LDIFWriter targetWriter = new LDIFWriter(exportConfig))
    {
      // Apply the changes and make sure there were no errors.
      successful = LDIFModify.modifyLDIF(sourceReader, changesReader, targetWriter, errorList);
    }

    if (!successful)
    {
      for (LocalizableMessage s : errorList)
      {
        logger.error(ERR_CONFIG_ERROR_APPLYING_STARTUP_CHANGE, s);
      }
      throw new LDIFException(ERR_CONFIG_UNABLE_TO_APPLY_CHANGES_FILE.get(Utils.joinAsString("; ", errorList)));
    }

    // Move the current config file out of the way and replace it with the updated version.
    File oldSource = new File(sourceFile.getAbsolutePath() + ".prechanges");
    if (oldSource.exists())
    {
      oldSource.delete();
    }
    sourceFile.renameTo(oldSource);
    new File(tempFile).renameTo(sourceFile);

    // Move the changes file out of the way so it doesn't get applied again.
    File newChanges = new File(changesFile.getAbsolutePath() + ".applied");
    if (newChanges.exists())
    {
      newChanges.delete();
    }
    changesFile.renameTo(newChanges);
  }

  private void applyConfigChangesIfNeeded(File configFileToUse) throws InitializationException
  {
    // See if there is a config changes file. If there is, then try to apply
    // the changes contained in it.
    File changesFile = new File(configFileToUse.getParent(), CONFIG_CHANGES_NAME);
    try
    {
      if (changesFile.exists())
      {
        applyChangesFile(configFileToUse, changesFile);
        if (maintainConfigArchive)
        {
          configurationDigest = calculateConfigDigest();
          writeConfigArchive();
        }
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_CONFIG_UNABLE_TO_APPLY_STARTUP_CHANGES.get(changesFile.getAbsolutePath(), e);
      throw new InitializationException(message, e);
    }
  }

  /**
   * Returns the LDIF reader on configuration entries.
   * <p>
   * It is the responsibility of the caller to ensure that reader is closed after usage.
   *
   * @param configFile
   *          LDIF file containing the configuration entries.
   * @param schema
   *          Schema to validate entries when reading the config file.
   * @return the LDIF reader
   * @throws InitializationException
   *           If an error occurs.
   */
  private EntryReader getLDIFReader(final File configFile, final Schema schema) throws InitializationException
  {
    try
    {
      LDIFEntryReader reader = new LDIFEntryReader(new FileReader(configFile));
      reader.setSchema(schema);
      return reader;
    }
    catch (Exception e)
    {
      throw new InitializationException(
          ERR_CONFIG_FILE_CANNOT_OPEN_FOR_READ.get(configFile.getAbsolutePath(), e.getLocalizedMessage()), e);
    }
  }

  /**
   * Returns the entry listeners attached to the provided DN.
   * <p>
   * If no listener exist for the provided DN, then a new set of empty listeners is created and
   * returned.
   *
   * @param dn
   *          DN of a configuration entry.
   * @return the listeners attached to the corresponding configuration entry.
   */
  private EntryListeners getEntryListeners(final DN dn)
  {
    EntryListeners entryListeners = listeners.get(dn);
    if (entryListeners == null)
    {
      entryListeners = new EntryListeners();
      final EntryListeners previousListeners = listeners.putIfAbsent(dn, entryListeners);
      if (previousListeners != null)
      {
        entryListeners = previousListeners;
      }
    }
    return entryListeners;
  }

  /**
   * Returns the parent DN of the configuration entry corresponding to the provided DN.
   *
   * @param entryDN
   *          DN of entry to retrieve the parent from.
   * @return the parent DN
   * @throws DirectoryException
   *           If entry has no parent or parent entry does not exist.
   */
  private DN retrieveParentDNForAdd(final DN entryDN) throws DirectoryException
  {
    final DN parentDN = entryDN.parent();
    if (parentDN == null)
    {
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, ERR_CONFIG_FILE_ADD_NO_PARENT_DN.get(entryDN));
    }
    if (!backend.contains(parentDN))
    {
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, ERR_CONFIG_FILE_ADD_NO_PARENT.get(entryDN, parentDN),
          getMatchedDN(parentDN), null);
    }
    return parentDN;
  }

  /**
   * Returns the parent DN of the configuration entry corresponding to the provided DN.
   *
   * @param entryDN
   *          DN of entry to retrieve the parent from.
   * @return the parent DN
   * @throws DirectoryException
   *           If entry has no parent or parent entry does not exist.
   */
  private DN retrieveParentDNForDelete(final DN entryDN) throws DirectoryException
  {
    final DN parentDN = entryDN.parent();
    if (parentDN == null)
    {
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, ERR_CONFIG_FILE_DELETE_NO_PARENT_DN.get(entryDN));
    }
    if (!backend.contains(parentDN))
    {
      throw new DirectoryException(ResultCode.NO_SUCH_OBJECT, ERR_CONFIG_FILE_DELETE_NO_PARENT.get(entryDN),
          getMatchedDN(parentDN), null);
    }
    return parentDN;
  }

  /** Returns the matched DN that is available in the configuration for the provided DN. */
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
   * Examines the provided result and logs a message if appropriate.
   * <p>
   * <ul>
   * <li>If the result code is anything other than {@code SUCCESS}, then it will log an error message.</li>
   * <li>If the operation was successful but admin action is required, then it will log a warning message.</li>
   * <li>If no action is required but messages were generated, then it will log an informational message.</li>
   * </ul>
   *
   * @param result
   *          The config change result object that
   * @param entryDN
   *          The DN of the entry that was added, deleted, or modified.
   * @param className
   *          The name of the class for the object that generated the provided result.
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
    final String messages = Utils.joinAsString("  ", result.getMessages());

    if (resultCode != ResultCode.SUCCESS)
    {
      logger.error(ERR_CONFIG_CHANGE_RESULT_ERROR, className, methodName, entryDN, resultCode, adminActionRequired,
          messages);
    }
    else if (adminActionRequired)
    {
      logger.warn(WARN_CONFIG_CHANGE_RESULT_ACTION_REQUIRED, className, methodName, entryDN, messages);
    }
    else if (!messages.isEmpty())
    {
      logger.debug(INFO_CONFIG_CHANGE_RESULT_MESSAGES, className, methodName, entryDN, messages);
    }
  }
}
