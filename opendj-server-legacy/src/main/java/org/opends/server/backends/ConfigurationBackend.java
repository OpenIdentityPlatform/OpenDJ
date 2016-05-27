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
package org.opends.server.backends;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.config.ConfigConstants.ATTR_DEFAULT_ROOT_PRIVILEGE_NAME;
import static org.opends.server.config.ConfigConstants.CONFIG_ARCHIVE_DIR_NAME;
import static org.opends.server.util.StaticUtils.getExceptionMessage;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.adapter.server3x.Converters;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.server.config.meta.BackendCfgDefn.WritabilityMode;
import org.forgerock.opendj.server.config.server.BackendCfg;
import org.opends.server.api.Backend;
import org.opends.server.api.Backupable;
import org.opends.server.api.ClientConnection;
import org.opends.server.backends.ConfigurationBackend.ConfigurationBackendCfg;
import org.opends.server.config.ConfigurationHandler;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.SearchOperation;
import org.opends.server.core.ServerContext;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.BackupDirectory;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.IndexType;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LDIFImportResult;
import org.opends.server.types.Modification;
import org.opends.server.types.Privilege;
import org.opends.server.types.RestoreConfig;
import org.opends.server.util.BackupManager;
import org.opends.server.util.StaticUtils;

/** Back-end responsible for management of configuration entries. */
public class ConfigurationBackend extends Backend<ConfigurationBackendCfg> implements Backupable
{
  /**
   * Dummy {@link BackendCfg} implementation for the {@link ConfigurationBackend}. No config is
   * needed for this specific backend, but this class is required to behave like other backends
   * during initialization.
   */
  public final class ConfigurationBackendCfg implements BackendCfg
  {
    private ConfigurationBackendCfg()
    {
      // let nobody instantiate it
    }

    @Override
    public DN dn()
    {
      return getBaseDNs().iterator().next();
    }

    @Override
    public Class<? extends BackendCfg> configurationClass()
    {
      return this.getClass();
    }

    @Override
    public String getBackendId()
    {
      return CONFIG_BACKEND_ID;
    }

    @Override
    public SortedSet<DN> getBaseDN()
    {
      return Collections.unmodifiableSortedSet(new TreeSet<DN>(getBaseDNs()));
    }

    @Override
    public boolean isEnabled()
    {
      return true;
    }

    @Override
    public String getJavaClass()
    {
      return ConfigurationBackend.class.getName();
    }

    @Override
    public WritabilityMode getWritabilityMode()
    {
      return WritabilityMode.ENABLED;
    }

    @Override
    public void addChangeListener(ConfigurationChangeListener<BackendCfg> listener)
    {
      // no-op
    }

    @Override
    public void removeChangeListener(ConfigurationChangeListener<BackendCfg> listener)
    {
      // no-op
    }
  }

  /**
   * The backend ID for the configuration backend.
   * <p>
   * Try to avoid potential conflict with user backend identifiers.
   */
  public static final String CONFIG_BACKEND_ID = "__config.ldif__";

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The set of supported control OIDs for this backend. */
  private static final Set<String> SUPPORTED_CONTROLS = new HashSet<>(0);
  /** The set of supported feature OIDs for this backend. */
  private static final Set<String> SUPPORTED_FEATURES = new HashSet<>(0);

  /** The privilege array containing both the CONFIG_READ and CONFIG_WRITE privileges. */
  private static final Privilege[] CONFIG_READ_AND_WRITE =
  {
    Privilege.CONFIG_READ,
    Privilege.CONFIG_WRITE
  };

  /** Handles the configuration entries and their storage in files. */
  private final ConfigurationHandler configurationHandler;

  /** The reference to the configuration root entry. */
  private final Entry configRootEntry;

  /** The set of base DNs for this config handler backend. */
  private Set<DN> baseDNs;

  /**
   * The write lock used to ensure that only one thread can apply a
   * configuration update at any given time.
   */
  private final Object configLock = new Object();

  /**
   * Creates and initializes a new instance of this backend.
   *
   * @param serverContext
   *            The server context.
   * @param configurationHandler
   *            Contains the configuration entries.
   * @throws InitializationException
   *            If an errors occurs.
   */
  public ConfigurationBackend(ServerContext serverContext, ConfigurationHandler configurationHandler)
      throws InitializationException
  {
    this.configurationHandler = configurationHandler;
    this.configRootEntry = Converters.to(configurationHandler.getRootEntry());
    baseDNs = Collections.singleton(configRootEntry.getName());

    setBackendID(CONFIG_BACKEND_ID);
  }

  /**
   * Returns a new {@link ConfigurationBackendCfg} for this {@link ConfigurationBackend}.
   *
   * @return a new {@link ConfigurationBackendCfg} for this {@link ConfigurationBackend}
   */
  public ConfigurationBackendCfg getBackendCfg()
  {
    return new ConfigurationBackendCfg();
  }

  @Override
  public void closeBackend()
  {
    try
    {
      DirectoryServer.deregisterBaseDN(configRootEntry.getName());
    }
    catch (Exception e)
    {
      logger.traceException(e, "Error when deregistering base DN: " + configRootEntry.getName());
    }
  }

  @Override
  public void configureBackend(ConfigurationBackendCfg cfg, ServerContext serverContext) throws ConfigException
  {
    // No action is required.
  }

  @Override
  public void openBackend() throws InitializationException
  {
    DN baseDN = configRootEntry.getName();
    try
    {
      DirectoryServer.registerBaseDN(baseDN, this, true);
    }
    catch (DirectoryException e)
    {
      logger.traceException(e);
      throw new InitializationException(
          ERR_CONFIG_CANNOT_REGISTER_AS_PRIVATE_SUFFIX.get(baseDN, getExceptionMessage(e)), e);
    }
  }

  @Override
  public Set<DN> getBaseDNs()
  {
    return baseDNs;
  }

  @Override
  public Entry getEntry(DN entryDN)
  {
    try
    {
      org.forgerock.opendj.ldap.Entry entry = configurationHandler.getEntry(entryDN);
      if (entry != null)
      {
        Entry serverEntry = Converters.to(entry);
        serverEntry.processVirtualAttributes();
        return serverEntry;
      }
    }
    catch (ConfigException e)
    {
      // should never happen
    }
    return null;
  }

  @Override
  public long getEntryCount()
  {
    try
    {
      return getNumberOfEntriesInBaseDN(configRootEntry.getName());
    }
    catch (DirectoryException e)
    {
      logger.traceException(e, "Unable to count entries of configuration backend");
      return -1;
    }
  }

  @Override
  public File getDirectory()
  {
    return configurationHandler.getConfigurationFile().getParentFile();
  }

  @Override
  public long getNumberOfChildren(DN parentDN) throws DirectoryException
  {
    try {
      return configurationHandler.numSubordinates(parentDN, false);
    }
    catch (ConfigException e)
    {
      throw new DirectoryException(ResultCode.UNDEFINED, e.getMessageObject());
    }
  }

  @Override
  public long getNumberOfEntriesInBaseDN(DN baseDN) throws DirectoryException
  {
    try
    {
      return configurationHandler.numSubordinates(baseDN, true) + 1;
    }
    catch (ConfigException e)
    {
      throw new DirectoryException(ResultCode.UNDEFINED, e.getMessageObject());
    }
  }

  @Override
  public Set<String> getSupportedControls()
  {
    return SUPPORTED_CONTROLS;
  }

  @Override
  public Set<String> getSupportedFeatures()
  {
    return SUPPORTED_FEATURES;
  }

  @Override
  public ConditionResult hasSubordinates(DN entryDN) throws DirectoryException
  {
    long ret = getNumberOfChildren(entryDN);
    if(ret < 0)
    {
      return ConditionResult.UNDEFINED;
    }
    return ConditionResult.valueOf(ret != 0);
  }

  @Override
  public boolean isIndexed(AttributeType attributeType, IndexType indexType)
  {
    // All searches in this backend will always be considered indexed.
    return true;
  }

  @Override
  public boolean entryExists(DN entryDN) throws DirectoryException
  {
    try
    {
      return configurationHandler.hasEntry(entryDN);
    }
    catch (ConfigException e)
    {
      throw new DirectoryException(ResultCode.UNDEFINED, e.getMessageObject(), e);
    }
  }

  @Override
  public boolean supports(BackendOperation backendOperation)
  {
    switch (backendOperation)
    {
    case BACKUP:
    case RESTORE:
    case LDIF_EXPORT:
      return true;
    default:
      return false;
    }
  }

  @Override
  public void search(SearchOperation searchOperation) throws DirectoryException
  {
    // Make sure that the associated user has the CONFIG_READ privilege.
    ClientConnection clientConnection = searchOperation.getClientConnection();
    if (! clientConnection.hasPrivilege(Privilege.CONFIG_READ, searchOperation))
    {
      LocalizableMessage message = ERR_CONFIG_FILE_SEARCH_INSUFFICIENT_PRIVILEGES.get();
      throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS, message);
    }

    configurationHandler.search(searchOperation);
  }

  @Override
  public void addEntry(Entry entry, AddOperation addOperation)
         throws DirectoryException
  {
    // Make sure that the associated user has
    // both the CONFIG_READ and CONFIG_WRITE privileges.
    if (addOperation != null)
    {
      ClientConnection clientConnection = addOperation.getClientConnection();
      if (!clientConnection.hasAllPrivileges(CONFIG_READ_AND_WRITE, addOperation))
      {
        LocalizableMessage message = ERR_CONFIG_FILE_ADD_INSUFFICIENT_PRIVILEGES.get();
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS, message);
      }
    }

    // Only one configuration update may be in progress at any given time.
    synchronized (configLock)
    {
      configurationHandler.addEntry(Converters.from(copyWithoutVirtualAttributes(entry)));
    }
  }

  private Entry copyWithoutVirtualAttributes(Entry entry) {
    return entry.duplicate(false);
  }

  @Override
  public void deleteEntry(DN entryDN, DeleteOperation deleteOperation)
         throws DirectoryException
  {
    // Make sure that the associated user
    // has both the CONFIG_READ and CONFIG_WRITE privileges.
    if (deleteOperation != null)
    {
      ClientConnection clientConnection = deleteOperation.getClientConnection();
      if (!clientConnection.hasAllPrivileges(CONFIG_READ_AND_WRITE, deleteOperation))
      {
        LocalizableMessage message = ERR_CONFIG_FILE_DELETE_INSUFFICIENT_PRIVILEGES.get();
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS, message);
      }
    }

    // Only one configuration update may be in progress at any given time.
    synchronized (configLock)
    {
      if (configRootEntry.getName().equals(entryDN))
      {
        LocalizableMessage message = ERR_CONFIG_FILE_DELETE_NO_PARENT.get(entryDN);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      configurationHandler.deleteEntry(entryDN);
    }
  }

  @Override
  public void replaceEntry(Entry oldEntry, Entry newEntry, ModifyOperation modifyOperation) throws DirectoryException
  {
    // Make sure that the associated user has both the CONFIG_READ and CONFIG_WRITE privileges.
    // Also, if the operation targets the set of root privileges
    // then make sure the user has the PRIVILEGE_CHANGE privilege.
    if (modifyOperation != null)
    {
      ClientConnection clientConnection = modifyOperation.getClientConnection();
      if (!clientConnection.hasAllPrivileges(CONFIG_READ_AND_WRITE, modifyOperation))
      {
        LocalizableMessage message = ERR_CONFIG_FILE_MODIFY_INSUFFICIENT_PRIVILEGES.get();
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS, message);
      }

      for (Modification m : modifyOperation.getModifications())
      {
        if (m.getAttribute().getAttributeDescription().getAttributeType().hasName(ATTR_DEFAULT_ROOT_PRIVILEGE_NAME))
        {
          if (!clientConnection.hasPrivilege(Privilege.PRIVILEGE_CHANGE, modifyOperation))
          {
            LocalizableMessage message = ERR_CONFIG_FILE_MODIFY_PRIVS_INSUFFICIENT_PRIVILEGES.get();
            throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS, message);
          }

          break;
        }
      }
    }

    // Only one configuration update may be in progress at any given time.
    synchronized (configLock)
    {
      configurationHandler.replaceEntry(
          Converters.from(copyWithoutVirtualAttributes(oldEntry)),
          Converters.from(copyWithoutVirtualAttributes(newEntry)));
    }
  }

  @Override
  public void renameEntry(DN currentDN, Entry entry, ModifyDNOperation modifyDNOperation) throws DirectoryException
  {
    // Make sure that the associated
    // user has both the CONFIG_READ and CONFIG_WRITE privileges.
    if (modifyDNOperation != null)
    {
      ClientConnection clientConnection = modifyDNOperation.getClientConnection();
      if (!clientConnection.hasAllPrivileges(CONFIG_READ_AND_WRITE, modifyDNOperation))
      {
        LocalizableMessage message = ERR_CONFIG_FILE_MODDN_INSUFFICIENT_PRIVILEGES.get();
        throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS, message);
      }
    }

    // Modify DN operations will not be allowed in the configuration, so this
    // will always throw an exception.
    LocalizableMessage message = ERR_CONFIG_FILE_MODDN_NOT_ALLOWED.get();
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }

  @Override
  public void exportLDIF(LDIFExportConfig exportConfig) throws DirectoryException
  {
    configurationHandler.writeLDIF(exportConfig);
  }

  @Override
  public LDIFImportResult importLDIF(LDIFImportConfig importConfig, ServerContext serverContext)
         throws DirectoryException
  {
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, ERR_CONFIG_FILE_UNWILLING_TO_IMPORT.get());
  }

  @Override
  public void createBackup(BackupConfig backupConfig) throws DirectoryException
  {
    new BackupManager(getBackendID()).createBackup(this, backupConfig);
  }

  @Override
  public void removeBackup(BackupDirectory backupDirectory, String backupID) throws DirectoryException
  {
    new BackupManager(getBackendID()).removeBackup(backupDirectory, backupID);
  }

  @Override
  public void restoreBackup(RestoreConfig restoreConfig) throws DirectoryException
  {
    new BackupManager(getBackendID()).restoreBackup(this, restoreConfig);
  }

  @Override
  public ListIterator<Path> getFilesToBackup()
  {
    final List<Path> files = new ArrayList<>();

    File configFile = configurationHandler.getConfigurationFile();
    files.add(configFile.toPath());

    // the files in archive directory
    File archiveDirectory = new File(getDirectory(), CONFIG_ARCHIVE_DIR_NAME);
    if (archiveDirectory.exists())
    {
      for (File archiveFile : archiveDirectory.listFiles())
      {
        files.add(archiveFile.toPath());
      }
    }

    return files.listIterator();
  }

  @Override
  public boolean isDirectRestore()
  {
    return true;
  }

  @Override
  public Path beforeRestore() throws DirectoryException
  {
    // save current config files to a save directory
    return BackupManager.saveCurrentFilesToDirectory(this, getBackendID());
  }

  @Override
  public void afterRestore(Path restoreDirectory, Path saveDirectory) throws DirectoryException
  {
    // restore was successful, delete the save directory
    StaticUtils.recursiveDelete(saveDirectory.toFile());
  }
}
