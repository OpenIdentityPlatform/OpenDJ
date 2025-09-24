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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2010-2016 ForgeRock AS.
 * Portions Copyright 2022-2025 3A Systems, LLC.
 * Portions Copyright 2025 Wren Security.
 */
package org.opends.server.core;

import static com.forgerock.opendj.cli.CommonArguments.*;

import static org.opends.messages.CoreMessages.*;
import static org.opends.messages.ToolMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.tools.ConfigureWindowsService.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.DynamicConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import static com.forgerock.opendj.util.StaticUtils.registerBcProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;

import org.forgerock.http.routing.Router;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.adapter.server3x.Converters;
import org.forgerock.opendj.config.ConfigurationFramework;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ServerManagementContext;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.server.config.server.AlertHandlerCfg;
import org.forgerock.opendj.server.config.server.ConnectionHandlerCfg;
import org.forgerock.opendj.server.config.server.CryptoManagerCfg;
import org.forgerock.opendj.server.config.server.MonitorProviderCfg;
import org.forgerock.opendj.server.config.server.PasswordValidatorCfg;
import org.forgerock.opendj.server.config.server.RootCfg;
import org.forgerock.opendj.server.config.server.SynchronizationProviderCfg;
import org.forgerock.util.Reject;
import org.opends.server.admin.AdministrationDataSync;
import org.opends.server.api.AccessControlHandler;
import org.opends.server.api.AccountStatusNotificationHandler;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.AlertHandler;
import org.opends.server.api.AuthenticationPolicy;
import org.opends.server.api.Backend;
import org.opends.server.api.BackupTaskListener;
import org.opends.server.api.CertificateMapper;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.CompressedSchema;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.DirectoryServerMBean;
import org.opends.server.api.EntryCache;
import org.opends.server.api.ExportTaskListener;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.api.IdentityMapper;
import org.opends.server.api.ImportTaskListener;
import org.opends.server.api.InitializationCompletedListener;
import org.opends.server.api.KeyManagerProvider;
import org.opends.server.api.LocalBackend;
import org.opends.server.api.MonitorProvider;
import org.opends.server.api.PasswordGenerator;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.api.PasswordValidator;
import org.opends.server.api.RestoreTaskListener;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.api.TrustManagerProvider;
import org.opends.server.api.WorkQueue;
import org.opends.server.api.plugin.InternalDirectoryServerPlugin;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.config.AdministrationConnector;
import org.opends.server.config.ConfigurationHandler;
import org.opends.server.config.JMXMBean;
import org.opends.server.controls.PasswordPolicyErrorType;
import org.opends.server.controls.PasswordPolicyResponseControl;
import org.opends.server.crypto.CryptoManagerImpl;
import org.opends.server.crypto.CryptoManagerSync;
import org.opends.server.discovery.ServiceDiscoveryMechanismConfigManager;
import org.opends.server.extensions.DiskSpaceMonitor;
import org.opends.server.extensions.JMXAlertHandler;
import org.opends.server.loggers.AccessLogger;
import org.opends.server.loggers.CommonAudit;
import org.opends.server.loggers.DebugLogPublisher;
import org.opends.server.loggers.DebugLogger;
import org.opends.server.loggers.ErrorLogPublisher;
import org.opends.server.loggers.ErrorLogger;
import org.opends.server.loggers.RetentionPolicy;
import org.opends.server.loggers.RotationPolicy;
import org.opends.server.loggers.TextErrorLogPublisher;
import org.opends.server.loggers.TextWriter;
import org.opends.server.monitors.ConnectionHandlerMonitor;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalConnectionHandler;
import org.opends.server.schema.SchemaHandler;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.Control;
import org.opends.server.types.CryptoManager;
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.HostPort;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.LockManager;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.VirtualAttributeRule;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.CronExecutorService;
import org.opends.server.util.MultiOutputStream;
import org.opends.server.util.RuntimeInformation;
import org.opends.server.util.SetupUtils;
import org.opends.server.util.TimeThread;

import com.forgerock.opendj.cli.ArgumentConstants;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.util.OperatingSystem;

/**
 * This class defines the core of the Directory Server.  It manages the startup
 * and shutdown processes and coordinates activities between all other
 * components.
 */
public final class DirectoryServer
       implements AlertGenerator
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The singleton Directory Server instance. */
  private static DirectoryServer directoryServer = new DirectoryServer();

  /** Indicates whether the server currently holds an exclusive lock on the server lock file. */
  private static boolean serverLocked;

  /** The message to be displayed on the command-line when the user asks for the usage. */
  private static final LocalizableMessage toolDescription = INFO_DSCORE_TOOL_DESCRIPTION.get();

  /**
   * Return codes used when the hidden option --checkStartability is used.
   * NOTE: when checkstartability is specified is recommended not to allocate
   * a lot of memory for the JVM (Using -Xms and -Xmx options) as there might
   * be calls to Runtime.exec.
   */
  /**
   * Returned when the user specified the --checkStartability option with other
   * options like printing the usage, dumping messages, displaying version, etc.
   */
  private static final int NOTHING_TO_DO = 0;
  /**
   * Returned when the user specified the --checkStartability option with
   * some incompatible arguments.
   */
  private static final int CHECK_ERROR = 1;
  /** The server is already started. */
  private static final int SERVER_ALREADY_STARTED = 98;
  /** The server must be started as detached process. */
  private static final int START_AS_DETACH = 99;
  /** The server must be started as a non-detached process. */
  private static final int START_AS_NON_DETACH = 100;
  /** The server must be started as a window service. */
  private static final int START_AS_WINDOWS_SERVICE = 101;
  /** The server must be started as detached and it is being called from the Windows Service. */
  private static final int START_AS_DETACH_CALLED_FROM_WINDOWS_SERVICE = 102;
  /** The server must be started as detached process and should not produce any output. */
  private static final int START_AS_DETACH_QUIET = 103;
  /** The server must be started as non-detached process and should not produce any output. */
  private static final int START_AS_NON_DETACH_QUIET = 104;

  /** Temporary context object, to provide instance methods instead of static methods. */
  private final DirectoryServerContext serverContext;

  /** The account status notification handler config manager for the server. */
  private AccountStatusNotificationHandlerConfigManager accountStatusNotificationHandlerConfigManager;

  /** The authenticated users manager for the server. */
  private AuthenticatedUsers authenticatedUsers;
  /** The configuration manager that will handle the server backends. */
  private BackendConfigManager backendConfigManager;

  /** Indicates whether the server has been bootstrapped. */
  private boolean isBootstrapped;
  /** Indicates whether the server is currently online. */
  private boolean isRunning;
  /** Indicates whether the server is currently in "lockdown mode". */
  private boolean lockdownMode;

  /** Indicates whether the server is currently in the process of shutting down. */
  private boolean shuttingDown;

  /** The configuration manager that will handle the certificate mapper. */
  private CertificateMapperConfigManager certificateMapperConfigManager;

  /** The configuration handler for the Directory Server. */
  private ConfigurationHandler configurationHandler;

  /** The configuration manager that will handle HTTP endpoints. */
  private HttpEndpointConfigManager httpEndpointConfigManager;

  /** The set of account status notification handlers defined in the server. */
  private ConcurrentMap<DN, AccountStatusNotificationHandler<?>>
               accountStatusNotificationHandlers;

  /** The set of certificate mappers registered with the server. */
  private ConcurrentMap<DN, CertificateMapper<?>> certificateMappers;

  /** The set of alternate bind DNs for the root users. */
  private ConcurrentMap<DN, DN> alternateRootBindDNs;

  /**
   * The set of identity mappers registered with the server (mapped between the
   * configuration entry Dn and the mapper).
   */
  private ConcurrentMap<DN, IdentityMapper<?>> identityMappers;

  /**
   * The set of JMX MBeans that have been registered with the server (mapped
   * between the associated configuration entry DN and the MBean).
   */
  private ConcurrentHashMap<DN, JMXMBean> mBeans;

  /** The set of key manager providers registered with the server. */
  private ConcurrentMap<DN, KeyManagerProvider<?>> keyManagerProviders;

  /**
   * The set of password generators registered with the Directory Server, as a
   * mapping between the DN of the associated configuration entry and the
   * generator implementation.
   */
  private ConcurrentMap<DN, PasswordGenerator<?>> passwordGenerators;

  /**
   * The set of authentication policies registered with the Directory Server, as
   * a mapping between the DN of the associated configuration entry and the
   * policy implementation.
   */
  private ConcurrentMap<DN, AuthenticationPolicy> authenticationPolicies;

  /**
   * The set of password validators registered with the Directory Server, as a
   * mapping between the DN of the associated configuration entry and the
   * validator implementation.
   */
  private ConcurrentMap<DN, PasswordValidator<? extends PasswordValidatorCfg>> passwordValidators;

  /** The set of trust manager providers registered with the server. */
  private ConcurrentMap<DN, TrustManagerProvider<?>> trustManagerProviders;

  /**
   * The set of log rotation policies registered with the Directory Server, as a
   * mapping between the DN of the associated configuration entry and the policy
   * implementation.
   */
  private ConcurrentMap<DN, RotationPolicy<?>> rotationPolicies;

  /**
   * The set of log retention policies registered with the Directory Server, as
   * a mapping between the DN of the associated configuration entry and the
   * policy implementation.
   */
  private ConcurrentMap<DN, RetentionPolicy<?>> retentionPolicies;

  /** The set supported LDAP protocol versions. */
  private ConcurrentMap<Integer, List<ConnectionHandler<?>>> supportedLDAPVersions;

  /**
   * The set of extended operation handlers registered with the server (mapped
   * between the OID of the extended operation and the handler).
   */
  private ConcurrentMap<String, ExtendedOperationHandler<?>> extendedOperationHandlers;

  /**
   * The set of monitor providers registered with the Directory Server, as a
   * mapping between the monitor name and the corresponding implementation.
   */
  private ConcurrentMap<String, MonitorProvider<? extends MonitorProviderCfg>> monitorProviders;

  /**
   * The set of password storage schemes defined in the server (mapped between
   * the lowercase scheme name and the storage scheme) that support the
   * authentication password syntax.
   */
  private ConcurrentHashMap<String, PasswordStorageScheme<?>> authPasswordStorageSchemes;

  /**
   * The set of password storage schemes defined in the server (mapped between
   * the lowercase scheme name and the storage scheme).
   */
  private ConcurrentHashMap<String, PasswordStorageScheme<?>> passwordStorageSchemes;

  /**
   * The set of password storage schemes defined in the server (mapped between
   * the DN of the configuration entry and the storage scheme).
   */
  private ConcurrentMap<DN, PasswordStorageScheme<?>> passwordStorageSchemesByDN;

  /**
   * The set of SASL mechanism handlers registered with the server (mapped
   * between the mechanism name and the handler).
   */
  private ConcurrentMap<String, SASLMechanismHandler<?>> saslMechanismHandlers;

  /** The connection handler configuration manager for the Directory Server. */
  private ConnectionHandlerConfigManager connectionHandlerConfigManager;

  /** The set of alert handlers registered with the Directory Server. */
  private List<AlertHandler<?>> alertHandlers;
  /** The set of connection handlers registered with the Directory Server. */
  private List<ConnectionHandler<?>> connectionHandlers;

  /** The set of backup task listeners registered with the Directory Server. */
  private CopyOnWriteArrayList<BackupTaskListener> backupTaskListeners;
  /** The set of export task listeners registered with the Directory Server. */
  private CopyOnWriteArrayList<ExportTaskListener> exportTaskListeners;
  /** The set of import task listeners registered with the Directory Server. */
  private CopyOnWriteArrayList<ImportTaskListener> importTaskListeners;
  /** The set of restore task listeners registered with the Directory Server. */
  private CopyOnWriteArrayList<RestoreTaskListener> restoreTaskListeners;

  /**
   * The set of initialization completed listeners that have been registered
   * with the Directory Server.
   */
  private List<InitializationCompletedListener> initializationCompletedListeners;

  /** The set of shutdown listeners that have been registered with the Directory Server. */
  private List<ServerShutdownListener> shutdownListeners;
  /** The set of synchronization providers that have been registered with the Directory Server. */
  private List<SynchronizationProvider<SynchronizationProviderCfg>> synchronizationProviders;

  /** The set of root DNs registered with the Directory Server. */
  private Set<DN> rootDNs;

  /** The core configuration manager for the Directory Server. */
  private CoreConfigManager coreConfigManager;

  /** The crypto manager for the Directory Server. */
  private CryptoManagerImpl cryptoManager;

  /** The default compressed schema manager. */
  private DefaultCompressedSchema compressedSchema;

  /** The environment configuration for the Directory Server. */
  private DirectoryEnvironmentConfig environmentConfig;

  /** The shutdown hook that has been registered with the server. */
  private DirectoryServerShutdownHook shutdownHook;

  /** The DN of the entry containing the server schema definitions. */
  private DN schemaDN;

  /** The Directory Server entry cache. */
  private EntryCache<?> entryCache;

  /** The configuration manager for the entry cache. */
  private EntryCacheConfigManager entryCacheConfigManager;

  /** The configuration manager for extended operation handlers. */
  private ExtendedOperationConfigManager extendedOperationConfigManager;

  /**
   * The path to the file containing the Directory Server configuration, or the
   * information needed to bootstrap the configuration handler.
   */
  private File configFile;

  /** The group manager for the Directory Server. */
  private GroupManager groupManager;

  /** The subentry manager for the Directory Server. */
  private SubentryManager subentryManager;

  /** The configuration manager for identity mappers. */
  private IdentityMapperConfigManager identityMapperConfigManager;

  /** The current active persistent searches. */
  private final AtomicInteger activePSearches = new AtomicInteger(0);

  /** The key manager provider configuration manager for the Directory Server. */
  private KeyManagerProviderConfigManager keyManagerProviderConfigManager;

  /** The set of connections that are currently established. */
  private Set<ClientConnection> establishedConnections;

  /** The log rotation policy config manager for the Directory Server. */
  private LogRotationPolicyConfigManager rotationPolicyConfigManager;

  /** The log retention policy config manager for the Directory Server. */
  private LogRetentionPolicyConfigManager retentionPolicyConfigManager;

  /** The logger configuration manager for the Directory Server. */
  private LoggerConfigManager loggerConfigManager;

  /** The number of connections currently established to the server. */
  private long currentConnections;
  /** The idle time limit for the server. */
  private long idleTimeLimit;

  /** The maximum number of connections established at one time. */
  private long maxConnections;

  /** The time that this Directory Server instance was started. */
  private long startUpTime;

  /** The total number of connections established since startup. */
  private long totalConnections;

  /** The MBean server used to handle JMX interaction. */
  private MBeanServer mBeanServer;

  /** The monitor config manager for the Directory Server. */
  private MonitorConfigManager monitorConfigManager;

  /** The operating system on which the server is running. */
  private final OperatingSystem operatingSystem;

  /** The configuration handler used to manage the password generators. */
  private PasswordGeneratorConfigManager passwordGeneratorConfigManager;
  /** The default password policy for the Directory Server. */
  private PasswordPolicy defaultPasswordPolicy;
  /** The configuration handler used to manage the authentication policies. */
  private PasswordPolicyConfigManager authenticationPolicyConfigManager;
  /** The configuration handler used to manage the password storage schemes. */
  private PasswordStorageSchemeConfigManager storageSchemeConfigManager;
  /** The configuration handler used to manage the password validators. */
  private PasswordValidatorConfigManager passwordValidatorConfigManager;

  /** The plugin config manager for the Directory Server. */
  private PluginConfigManager pluginConfigManager;

  /** The root DN config manager for the server. */
  private RootDNConfigManager rootDNConfigManager;

  /** The SASL mechanism config manager for the Directory Server. */
  private SASLConfigManager saslConfigManager;

  /** The schema handler provides management of the schema, including its memory and files representations. */
  private SchemaHandler schemaHandler;

  /** The time that the server was started, formatted in UTC time. */
  private String startTimeUTC;

  /** The synchronization provider configuration manager for the Directory Server. */
  private SynchronizationProviderConfigManager synchronizationProviderConfigManager;

  /** The set of supported controls registered with the Directory Server. */
  private final TreeSet<String> supportedControls = newTreeSet(
      OID_LDAP_ASSERTION,
      OID_LDAP_READENTRY_PREREAD,
      OID_LDAP_READENTRY_POSTREAD,
      OID_LDAP_NOOP_OPENLDAP_ASSIGNED,
      OID_PERSISTENT_SEARCH,
      OID_PROXIED_AUTH_V1,
      OID_PROXIED_AUTH_V2,
      OID_AUTHZID_REQUEST,
      OID_MATCHED_VALUES,
      OID_LDAP_SUBENTRIES,
      OID_LDUP_SUBENTRIES,
      OID_PASSWORD_POLICY_CONTROL,
      OID_PERMISSIVE_MODIFY_CONTROL,
      OID_REAL_ATTRS_ONLY,
      OID_VIRTUAL_ATTRS_ONLY,
      OID_ACCOUNT_USABLE_CONTROL,
      OID_NS_PASSWORD_EXPIRED,
      OID_NS_PASSWORD_EXPIRING);

  /** The set of supported feature OIDs registered with the Directory Server. */
  private final TreeSet<String> supportedFeatures = newTreeSet(
      OID_ALL_OPERATIONAL_ATTRS_FEATURE,
      OID_MODIFY_INCREMENT_FEATURE,
      OID_TRUE_FALSE_FILTERS_FEATURE);

  /** The trust manager provider configuration manager for the Directory Server. */
  private TrustManagerProviderConfigManager trustManagerProviderConfigManager;

  /** The virtual attribute provider configuration manager for the Directory Server. */
  private final VirtualAttributeConfigManager virtualAttributeConfigManager;

  /** The work queue that will be used to service client requests. */
  private WorkQueue<?> workQueue;

  /** The memory reservation system. */
  private final MemoryQuota memoryQuota;

  /** The Disk Space Monitor. */
  private final DiskSpaceMonitor diskSpaceMonitor;

  /** The lock manager which will be used for coordinating access to LDAP entries. */
  private final LockManager lockManager = new LockManager();

  /** The default timeout used to start the server in detach mode. */
  public static final int DEFAULT_TIMEOUT = 200;

  /** Entry point for server configuration. */
  private ServerManagementContext serverManagementContext;

  /** Entry point to common audit service, where all audit events must be published. */
  private CommonAudit commonAudit;

  private Router httpRouter;
  private CronExecutorService cronExecutorService;
  private ServiceDiscoveryMechanismConfigManager serviceDiscoveryMechanismConfigManager;

  /** Class that prints the version of OpenDJ server to System.out. */
  public static final class DirectoryServerVersionHandler implements com.forgerock.opendj.cli.VersionHandler
  {
    @Override
    public void printVersion()
    {
      try
      {
        DirectoryServer.printVersion(System.out);
      }
      catch (Exception e){}
    }
  }

  /** Initialize the client DirectoryServer singleton by using a fluent interface. */
  public static class InitializationBuilder
  {
    /** Keep track of how subSystemsToInitialize are sequenced. */
    private enum SubSystem
    {
      CLIENT_INIT,
      CORE_CONFIG,
      INIT_CRYPTO,
      ADMIN_BACKEND,
      ADMIN_USERS,
      START_CRYPTO,
      PASSWORD_STORAGE_SCHEME,
      USER_PLUGINS,
      ERROR_DEBUG_LOGGERS;
    }

    private final String configFile;
    private final Set<PluginType> pluginTypes = new HashSet<>();
    private final EnumSet<SubSystem> subSystemsToInitialize = EnumSet.noneOf(SubSystem.class);
    private PrintStream loggingOut;
    private PrintStream errConfiguringLogging;

    /**
     * Initialize the client side of DirectoryServer and the Core Configuration.
     *
     * @param configFile the configuration file
     */
    public InitializationBuilder(String configFile)
    {
      this.configFile = configFile;
      subSystemsToInitialize.add(SubSystem.CLIENT_INIT);
      subSystemsToInitialize.add(SubSystem.CORE_CONFIG);
    }

    /**
     * Require to setup and start everything necessary for Crypto Services.
     * Core config should already be initialized through the constructor.
     *
     * @return this initialization builder
     */
    public InitializationBuilder requireCryptoServices()
    {
      Collections.addAll(subSystemsToInitialize,
          SubSystem.INIT_CRYPTO,
          SubSystem.ADMIN_BACKEND,
          SubSystem.ADMIN_USERS,
          SubSystem.START_CRYPTO);
      return this;
    }

    /**
     * Requires to setup and start Password Storage Schemes.
     * Crypto services are needed for Password Storage, so it will also set them up if not already done.
     *
     * @return this initialization builder
     */
    public InitializationBuilder requirePasswordStorageSchemes()
    {
      requireCryptoServices();
      Collections.addAll(subSystemsToInitialize, SubSystem.PASSWORD_STORAGE_SCHEME);
      return this;
    }

    /**
     * Requires to start specified user plugins.
     *
     * @param plugins the plugins to start
     * @return this initialization builder
     */
    public InitializationBuilder requireUserPlugins(PluginType... plugins)
    {
      Collections.addAll(subSystemsToInitialize, SubSystem.USER_PLUGINS);
      this.pluginTypes.addAll(Arrays.asList(plugins));
      return this;
    }

    /**
     * Requires to start the error and debug log publishers for tools.
     *
     * @param loggingOut
     *          The output stream where to write error and debug logging.
     * @param errConfiguringLogging
     *          The output stream where to write errors occurring when configuring logging.
     * @return this initialization builder
     */
    public InitializationBuilder requireErrorAndDebugLogPublisher(
        final PrintStream loggingOut, final PrintStream errConfiguringLogging)
    {
      subSystemsToInitialize.add(SubSystem.ERROR_DEBUG_LOGGERS);
      this.loggingOut = loggingOut;
      this.errConfiguringLogging = errConfiguringLogging;
      return this;
    }

    /**
     * Run all Initialization blocks as configured.
     *
     * @throws InitializationException
     *           if one of the initialization steps fails
     */
    public void initialize() throws InitializationException
    {
      for (SubSystem subSystem : subSystemsToInitialize)
      {
        switch (subSystem)
        {
        case CLIENT_INIT:
          clientInit();
          break;
        case CORE_CONFIG:
          initCoreConfig(configFile);
          break;
        case ADMIN_BACKEND:
          setupAdminBackends();
          break;
        case ADMIN_USERS:
          setupAdminUsers();
          break;
        case INIT_CRYPTO:
          initCryptoServices();
          break;
        case PASSWORD_STORAGE_SCHEME:
          startPasswordStorageScheme();
          break;
        case START_CRYPTO:
          startCryptoServices();
          break;
        case USER_PLUGINS:
          startUserPlugin();
          break;
        case ERROR_DEBUG_LOGGERS:
          startErrorAndDebugLoggers();
          break;
        }
      }
    }

    private void checkSubsystemIsInitialized(SubSystem subsystem) throws InitializationException
    {
      if (!subSystemsToInitialize.contains(subsystem))
      {
        throw new InitializationException(ERR_CANNOT_SUBSYSTEM_NOT_INITIALIZED.get(subsystem));
      }
    }

    private void clientInit() throws InitializationException
    {
      try
      {
        bootstrapClient();
        initializeJMX();
      }
      catch (Exception e)
      {
        throw new InitializationException(ERR_SERVER_BOOTSTRAP_ERROR.get(e.getLocalizedMessage()));
      }
    }

    private void initCoreConfig(String configFile) throws InitializationException
    {
      try
      {
        directoryServer.initializeConfiguration(configFile);
      }
      catch (Exception e)
      {
        throw new InitializationException(ERR_CANNOT_LOAD_CONFIG.get(e.getLocalizedMessage()));
      }
      try
      {
        directoryServer.initializeSchema();
      }
      catch (Exception e)
      {
        throw new InitializationException(ERR_CANNOT_LOAD_SCHEMA.get(e.getLocalizedMessage()));
      }
      try
      {
        directoryServer.coreConfigManager.initializeCoreConfig();
      }
      catch (Exception e)
      {
        throw new InitializationException(ERR_CANNOT_INITIALIZE_CORE_CONFIG.get(e.getLocalizedMessage()));
      }
    }

    private void initCryptoServices() throws InitializationException
    {
      try
      {
        directoryServer.initializeCryptoManager();
      }
      catch (Exception e)
      {
        throw new InitializationException(ERR_CANNOT_INITIALIZE_CRYPTO_MANAGER.get(e.getLocalizedMessage()));
      }
    }

    private void startCryptoServices() throws InitializationException
    {
      checkSubsystemIsInitialized(SubSystem.INIT_CRYPTO);
      checkSubsystemIsInitialized(SubSystem.ADMIN_USERS);
      new CryptoManagerSync();
    }

    private void setupAdminBackends() throws InitializationException
    {
      checkSubsystemIsInitialized(SubSystem.CORE_CONFIG);

      try
      {
        directoryServer.initializePlugins(Collections.<PluginType> emptySet());
      }
      catch (Exception e)
      {
        throw new InitializationException(ERR_CANNOT_INITIALIZE_SERVER_PLUGINS.get(e.getLocalizedMessage()));
      }

      try
      {
        directoryServer.initializeBackendConfigManager();
        directoryServer.initializeRootAndAdminDataBackends();
      }
      catch (InitializationException | ConfigException e)
      {
        throw new InitializationException(ERR_CANNOT_INITIALIZE_BACKENDS.get(e.getLocalizedMessage()));
      }

      try
      {
        directoryServer.initializeSubentryManager();
      }
      catch (Exception e)
      {
        throw new InitializationException(ERR_CANNOT_INITIALIZE_SUBENTRY_MANAGER.get(e.getLocalizedMessage()));
      }
    }

    private void setupAdminUsers() throws InitializationException
    {
      checkSubsystemIsInitialized(SubSystem.ADMIN_BACKEND);

      try
      {
        directoryServer.initializeRootDNConfigManager();
      }
      catch (Exception e)
      {
        throw new InitializationException(ERR_CANNOT_INITIALIZE_ROOTDN_MANAGER.get(e.getLocalizedMessage()));
      }

      try
      {
        directoryServer.initializeAuthenticationPolicyComponents();
        directoryServer.initializeAuthenticatedUsers();
      }
      catch (Exception e)
      {
        throw new InitializationException(ERR_CANNOT_INITIALIZE_PWPOLICY.get(e.getLocalizedMessage()));
      }
    }

    private void startUserPlugin() throws InitializationException
    {
      checkSubsystemIsInitialized(SubSystem.ADMIN_USERS);

      try
      {
        directoryServer.pluginConfigManager.initializeUserPlugins(pluginTypes);
      }
      catch (Exception e)
      {
        throw new InitializationException(getExceptionMessage(e));
      }
    }

    private void startPasswordStorageScheme() throws InitializationException
    {
      checkSubsystemIsInitialized(SubSystem.START_CRYPTO);

      try
      {
        directoryServer.storageSchemeConfigManager =
            new PasswordStorageSchemeConfigManager(directoryServer.serverContext);
        directoryServer.storageSchemeConfigManager.initializePasswordStorageSchemes();
      }
      catch (Exception e)
      {
        throw new InitializationException(ERR_CANNOT_INITIALIZE_STORAGE_SCHEMES.get(getExceptionMessage(e)));
      }
    }

    private void startErrorAndDebugLoggers()
    {
      try
      {
        final ErrorLogPublisher errorLogPublisher =
            TextErrorLogPublisher.getToolStartupTextErrorPublisher(new TextWriter.STREAM(loggingOut));
        ErrorLogger.getInstance().addLogPublisher(errorLogPublisher);
        DebugLogger.getInstance().addPublisherIfRequired(new TextWriter.STREAM(loggingOut));
      }
      catch (Exception e)
      {
        errConfiguringLogging.println("Error installing the custom error logger: " + stackTraceToSingleLineString(e));
      }
    }
  }

  /**
   * Temporary class to provide instance methods instead of static methods for
   * server. Once all static methods related to context are removed from the
   * server then DirectoryServer class can be used directly as implementation of
   * ServerContext.
   */
  private class DirectoryServerContext implements ServerContext
  {
    @Override
    public String getInstanceRoot()
    {
      return directoryServer.environmentConfig.getInstanceRootAsString();
    }

    @Override
    public String getServerRoot()
    {
      return directoryServer.environmentConfig.getServerRootAsString();
    }

    @Override
    public Schema getSchema()
    {
      return directoryServer.schemaHandler.getSchema();
    }

    @Override
    public SchemaHandler getSchemaHandler()
    {
      return directoryServer.schemaHandler;
    }

    @Override
    public ConfigurationHandler getConfigurationHandler()
    {
      return directoryServer.configurationHandler;
    }

    @Override
    public DirectoryEnvironmentConfig getEnvironment()
    {
      return directoryServer.environmentConfig;
    }

    @Override
    public ServerManagementContext getServerManagementContext()
    {
      return serverManagementContext;
    }

    @Override
    public RootCfg getRootConfig()
    {
      return getServerManagementContext().getRootConfiguration();
    }

    @Override
    public MemoryQuota getMemoryQuota()
    {
      return directoryServer.memoryQuota;
    }

    @Override
    public DiskSpaceMonitor getDiskSpaceMonitor()
    {
      return directoryServer.diskSpaceMonitor;
    }

    @Override
    public Router getHTTPRouter() {
     return directoryServer.httpRouter;
    }

    @Override
    public CommonAudit getCommonAudit()
    {
      return directoryServer.commonAudit;
    }

    @Override
    public LoggerConfigManager getLoggerConfigManager()
    {
      return directoryServer.loggerConfigManager;
    }

    @Override
    public CryptoManager getCryptoManager()
    {
      return directoryServer.cryptoManager;
    }

    @Override
    public ScheduledExecutorService getCronExecutorService()
    {
      return directoryServer.cronExecutorService;
    }

    @Override
    public BackendConfigManager getBackendConfigManager()
    {
      return directoryServer.backendConfigManager;
    }

    @Override
    public CoreConfigManager getCoreConfigManager()
    {
      return directoryServer.coreConfigManager;
    }

    @Override
    public ServiceDiscoveryMechanismConfigManager getServiceDiscoveryMechanismConfigManager()
    {
      return directoryServer.serviceDiscoveryMechanismConfigManager;
    }

    @Override
    public KeyManagerProvider<?> getKeyManagerProvider(DN keyManagerProviderDN)
    {
      return DirectoryServer.getKeyManagerProvider(keyManagerProviderDN);
    }

    @Override
    public TrustManagerProvider<?> getTrustManagerProvider(DN trustManagerProviderDN)
    {
      return DirectoryServer.getTrustManagerProvider(trustManagerProviderDN);
    }
  }

  /**
   * Creates a new instance of the Directory Server.  This will allow only a
   * single instance of the server per JVM.
   */
  private DirectoryServer()
  {
    this(new DirectoryEnvironmentConfig());
  }

  /**
   * Creates a new instance of the Directory Server.  This will allow only a
   * single instance of the server per JVM.
   *
   * @param  config  The environment configuration to use for the Directory
   *                 Server instance.
   */
  private DirectoryServer(DirectoryEnvironmentConfig config)
  {
    environmentConfig        = config;
    isBootstrapped           = false;
    isRunning                = false;
    shuttingDown             = false;
    lockdownMode             = false;

    operatingSystem = OperatingSystem.forName(System.getProperty("os.name"));
    serverContext = new DirectoryServerContext();
    virtualAttributeConfigManager = new VirtualAttributeConfigManager(serverContext);
    coreConfigManager = new CoreConfigManager(serverContext);
    memoryQuota = new MemoryQuota();
    diskSpaceMonitor = new DiskSpaceMonitor();
  }

  /**
   * Retrieves the instance of the Directory Server that is associated with this
   * JVM.
   *
   * @return  The instance of the Directory Server that is associated with this
   *          JVM.
   */
  public static DirectoryServer getInstance()
  {
    return directoryServer;
  }

  /**
   * Creates a new instance of the Directory Server and replaces the static
   * reference to it.  This should only be used in the context of an in-core
   * restart after the existing server has been shut down.
   *
   * @param  config  The environment configuration for the Directory Server.
   *
   * @return  The new instance of the Directory Server that is associated with
   *          this JVM.
   */
  private static DirectoryServer
                      getNewInstance(DirectoryEnvironmentConfig config)
  {
    synchronized (directoryServer)
    {
      return directoryServer = new DirectoryServer(config);
    }
  }

  /**
   * Retrieves the environment configuration for the Directory Server.
   *
   * @return  The environment configuration for the Directory Server.
   */
  public static DirectoryEnvironmentConfig getEnvironmentConfig()
  {
    return directoryServer.environmentConfig;
  }

  /**
   * Sets the environment configuration for the Directory Server.  This method
   * may only be invoked when the server is not running.
   *
   * @param  config  The environment configuration for the Directory Server.
   *
   * @throws  InitializationException  If the Directory Server is currently
   *                                   running.
   */
  public void setEnvironmentConfig(DirectoryEnvironmentConfig config)
          throws InitializationException
  {
    if (isRunning)
    {
      throw new InitializationException(
              ERR_CANNOT_SET_ENVIRONMENT_CONFIG_WHILE_RUNNING.get());
    }

    environmentConfig = config;
  }

  /**
   * Returns the server context.
   *
   * @return the server context
   */
  public ServerContext getServerContext() {
    return serverContext;
  }

  /**
   * Indicates whether the Directory Server is currently running.
   *
   * @return  {@code true} if the server is currently running, or {@code false}
   *          if not.
   */
  public static boolean isRunning()
  {
    return directoryServer.isRunning;
  }

  /**
   * Bootstraps the appropriate Directory Server structures that may be needed
   * by both server and client-side tools.
   */
  public static void bootstrapClient()
  {
    synchronized (directoryServer)
    {
      // Schema handler contains a default schema to start with
      directoryServer.schemaHandler = new SchemaHandler();

      // Perform any additional initialization that might be necessary before
      // loading the configuration.
      directoryServer.alertHandlers = new CopyOnWriteArrayList<>();
      directoryServer.passwordStorageSchemes = new ConcurrentHashMap<>();
      directoryServer.passwordStorageSchemesByDN = new ConcurrentHashMap<>();
      directoryServer.passwordGenerators = new ConcurrentHashMap<>();
      directoryServer.authPasswordStorageSchemes = new ConcurrentHashMap<>();
      directoryServer.passwordValidators = new ConcurrentHashMap<>();
      directoryServer.accountStatusNotificationHandlers = new ConcurrentHashMap<>();
      directoryServer.rootDNs = new CopyOnWriteArraySet<>();
      directoryServer.alternateRootBindDNs = new ConcurrentHashMap<>();
      directoryServer.keyManagerProviders = new ConcurrentHashMap<>();
      directoryServer.trustManagerProviders = new ConcurrentHashMap<>();
      directoryServer.rotationPolicies = new ConcurrentHashMap<>();
      directoryServer.retentionPolicies = new ConcurrentHashMap<>();
      directoryServer.certificateMappers = new ConcurrentHashMap<>();
      directoryServer.authenticationPolicies = new ConcurrentHashMap<>();
      directoryServer.defaultPasswordPolicy = null;
      directoryServer.monitorProviders = new ConcurrentHashMap<>();
      directoryServer.initializationCompletedListeners = new CopyOnWriteArrayList<>();
      directoryServer.shutdownListeners = new CopyOnWriteArrayList<>();
      directoryServer.synchronizationProviders = new CopyOnWriteArrayList<>();
      directoryServer.supportedLDAPVersions = new ConcurrentHashMap<>();
      directoryServer.connectionHandlers = new CopyOnWriteArrayList<>();
      directoryServer.identityMappers = new ConcurrentHashMap<>();
      directoryServer.extendedOperationHandlers = new ConcurrentHashMap<>();
      directoryServer.saslMechanismHandlers = new ConcurrentHashMap<>();
      directoryServer.backupTaskListeners = new CopyOnWriteArrayList<>();
      directoryServer.restoreTaskListeners = new CopyOnWriteArrayList<>();
      directoryServer.exportTaskListeners = new CopyOnWriteArrayList<>();
      directoryServer.importTaskListeners = new CopyOnWriteArrayList<>();
      directoryServer.idleTimeLimit = 0L;

      // make sure the timer thread is started in case it was stopped before
      TimeThread.start();
    }
  }

  /**
   * Bootstraps the Directory Server by initializing all the necessary
   * structures that should be in place before the configuration may be read.
   * This step must be completed before the server may be started or the
   * configuration is loaded, but it will not be allowed while the server is
   * running.
   *
   * @throws  InitializationException  If a problem occurs while attempting to
   *                                   bootstrap the server.
   */
  private void bootstrapServer() throws InitializationException
  {
    // First, make sure that the server isn't currently running.  If it isn't,
    // then make sure that no other thread will try to start or bootstrap the
    // server before this thread is done.
    synchronized (directoryServer)
    {
      if (isRunning)
      {
        LocalizableMessage message = ERR_CANNOT_BOOTSTRAP_WHILE_RUNNING.get();
        throw new InitializationException(message);
      }

      isBootstrapped   = false;
      shuttingDown     = false;
    }

    // Add a shutdown hook so that the server can be notified when the JVM
    // starts shutting down.
    shutdownHook = new DirectoryServerShutdownHook();
    Runtime.getRuntime().addShutdownHook(shutdownHook);

    // Create the MBean server that we will use for JMX interaction.
    initializeJMX();

    logger.debug(INFO_DIRECTORY_BOOTSTRAPPING);

    bootstrapClient();

    // Initialize the variables that will be used for connection tracking.
    establishedConnections = new LinkedHashSet<>(1000);
    currentConnections     = 0;
    maxConnections         = 0;
    totalConnections       = 0;

    // Create the plugin config manager, but don't initialize it yet.  This will
    // make it possible to process internal operations before the plugins have
    // been loaded.
    pluginConfigManager = new PluginConfigManager(serverContext);

    // If we have gotten here, then the configuration should be properly bootstrapped.
    synchronized (directoryServer)
    {
      isBootstrapped = true;
    }
  }

  /**
   * Performs a minimal set of JMX initialization. This may be used by the core
   * Directory Server or by command-line tools.
   *
   * @throws  InitializationException  If a problem occurs while attempting to
   *                                   initialize the JMX subsystem.
   */
  public static void initializeJMX()
         throws InitializationException
  {
    try
    {
      // It is recommended by ManagementFactory javadoc that the platform
      // MBeanServer also be used to register other application managed
      // beans besides the platform MXBeans. Try platform MBeanServer
      // first. If it fails create a new, private, MBeanServer instance.
      try
      {
        directoryServer.mBeanServer =
          ManagementFactory.getPlatformMBeanServer();
      }
      catch (Exception e)
      {
        logger.traceException(e);

        directoryServer.mBeanServer = MBeanServerFactory.newMBeanServer();
      }
      directoryServer.mBeans = new ConcurrentHashMap<>();
      registerAlertGenerator(directoryServer);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      throw new InitializationException(ERR_CANNOT_CREATE_MBEAN_SERVER.get(e), e);
    }
  }

  /**
   * Instantiates the configuration handler and loads the Directory Server
   * configuration.
   *
   * @param  configFile   The path to the file that will hold either the entire
   *                      server configuration or enough information to allow
   *                      the server to access the configuration in some other
   *                      repository.
   *
   * @throws  InitializationException  If a problem occurs while trying to
   *                                   initialize the config handler.
   */
  public void initializeConfiguration(String configFile) throws InitializationException
  {
    environmentConfig.setConfigFile(new File(configFile));
    initializeConfiguration();
  }

  /**
   * Initializes the configuration.
   * <p>
   * Creates the configuration handler, the server management context and the configuration backend.
   *
   * @throws InitializationException
   *            If an error occurs.
   */
  public void initializeConfiguration() throws InitializationException
  {
    configFile = environmentConfig.getConfigFile();
    configurationHandler = ConfigurationHandler.bootstrapConfiguration(serverContext);
    serverManagementContext = new ServerManagementContext(configurationHandler);
  }

  /**
   * Retrieves the path to the configuration file used to initialize the
   * Directory Server.
   *
   * @return  The path to the configuration file used to initialize the
   *          Directory Server.
   */
  public static String getConfigFile()
  {
    return directoryServer.configFile.getAbsolutePath();
  }

  /**
   * Starts up the Directory Server.  It must have already been bootstrapped
   * and cannot be running.
   *
   * @throws  ConfigException  If there is a problem with the Directory Server
   *                           configuration that prevents a critical component
   *                           from being instantiated.
   *
   * @throws  InitializationException  If some other problem occurs while
   *                                   attempting to initialize and start the
   *                                   Directory Server.
   */
  public void startServer()
         throws ConfigException, InitializationException
  {
    // Checks the version - if upgrade required, cannot launch the server.
    try
    {
      BuildVersion.checkVersionMismatch();
    }
    catch (InitializationException e)
    {
      logger.traceException(e);
      throw new InitializationException(e.getMessageObject());
    }

    synchronized (directoryServer)
    {
      if (! isBootstrapped)
      {
        LocalizableMessage message = ERR_CANNOT_START_BEFORE_BOOTSTRAP.get();
        throw new InitializationException(message);
      }

      if (isRunning)
      {
        LocalizableMessage message = ERR_CANNOT_START_WHILE_RUNNING.get();
        throw new InitializationException(message);
      }

      logger.info(NOTE_DIRECTORY_SERVER_STARTING, getVersionString(), BUILD_ID, REVISION);

      // Acquire an exclusive lock for the Directory Server process.
      if (! serverLocked)
      {
        String lockFile = LockFileManager.getServerLockFileName();
        try
        {
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.acquireExclusiveLock(lockFile, failureReason))
          {
            LocalizableMessage message = ERR_CANNOT_ACQUIRE_EXCLUSIVE_SERVER_LOCK.get(
                lockFile, failureReason);
            throw new InitializationException(message);
          }

          serverLocked = true;
        }
        catch (InitializationException ie)
        {
          throw ie;
        }
        catch (Exception e)
        {
          logger.traceException(e);

          LocalizableMessage message = ERR_CANNOT_ACQUIRE_EXCLUSIVE_SERVER_LOCK.get(
              lockFile, stackTraceToSingleLineString(e));
          throw new InitializationException(message, e);
        }
      }

      // Mark the current time as the start time.
      startUpTime  = System.currentTimeMillis();
      startTimeUTC = TimeThread.getGMTTime();

      // Determine whether we should start the connection handlers.
      boolean startConnectionHandlers = !environmentConfig.disableConnectionHandlers();

      diskSpaceMonitor.startDiskSpaceMonitor();

      initializeSchema();

      // At this point, it is necessary to reload the configuration with a complete schema
      // because it was loaded with an incomplete schema (meaning some attributes types and
      // objectclasses were defined by default, using a non-strict schema).
      // Configuration add/delete/change listeners are preserved by calling this method,
      // so schema elements listeners already registered are not lost.
      configurationHandler.reinitializeWithFullSchema(schemaHandler.getSchema());

      commonAudit = new CommonAudit(serverContext);
      httpRouter = new Router();
      cronExecutorService = new CronExecutorService();

      // Allow internal plugins to be registered.
      pluginConfigManager.initializePluginConfigManager();

      virtualAttributeConfigManager.initializeVirtualAttributes();

      // The core Directory Server configuration.
      coreConfigManager.initializeCoreConfig();

      registerBcProvider();
      initializeCryptoManager();

      rotationPolicyConfigManager = new LogRotationPolicyConfigManager(serverContext);
      rotationPolicyConfigManager.initializeLogRotationPolicyConfig();

      retentionPolicyConfigManager = new LogRetentionPolicyConfigManager(serverContext);
      retentionPolicyConfigManager.initializeLogRetentionPolicyConfig();

      loggerConfigManager = new LoggerConfigManager(serverContext);
      loggerConfigManager.initializeLoggerConfig();

      RuntimeInformation.logInfo();

      new AlertHandlerConfigManager(serverContext).initializeAlertHandlers();

      initializeBackendConfigManager();

      // Initialize the default entry cache. We have to have one before
      // <CODE>initializeRootAndAdminDataBackends()</CODE> method kicks in further down.
      entryCacheConfigManager = new EntryCacheConfigManager(serverContext);
      entryCacheConfigManager.initializeDefaultEntryCache();

      // Initialize the administration connector self signed certificate if
      // needed and do this before initializing the key managers so that it is
      // picked up.
      if (startConnectionHandlers)
      {
        AdministrationConnector.createSelfSignedCertificateIfNeeded(serverContext);
      }

      keyManagerProviderConfigManager = new KeyManagerProviderConfigManager(serverContext);
      keyManagerProviderConfigManager.initializeKeyManagerProviders();

      trustManagerProviderConfigManager = new TrustManagerProviderConfigManager(serverContext);
      trustManagerProviderConfigManager.initializeTrustManagerProviders();

      certificateMapperConfigManager = new CertificateMapperConfigManager(serverContext);
      certificateMapperConfigManager.initializeCertificateMappers();

      identityMapperConfigManager = new IdentityMapperConfigManager(serverContext);
      identityMapperConfigManager.initializeIdentityMappers();

      initializeRootDNConfigManager();

      initializeAuthenticatedUsers();
      initializeSubentryManager();
      initializeGroupManager();
      AccessControlConfigManager.getInstance().initializeAccessControl(serverContext);

      // Initialize backends needed by CryptoManagerSync for accessing keys.
      // PreInitialization callbacks (for Groups, for example) may require these to be fully available
      // before starting data backends.
      initializeRootAndAdminDataBackends();

      initializeAuthenticationPolicyComponents();

      // Synchronization of ADS with the crypto manager.
      // Need access to ADS keys before confidential backends and synchronization start to be able to
      // decode encrypted data in the backend by reading them from the trust store.
      new CryptoManagerSync();

      // Proxy needs discovery services to be already initialized.
      serviceDiscoveryMechanismConfigManager = new ServiceDiscoveryMechanismConfigManager(serverContext);
      serviceDiscoveryMechanismConfigManager.initializeServiceDiscoveryMechanismConfigManager();

      initializeRemainingBackends();

      // Check for and initialize user configured entry cache if any.
      // If not then stick with default entry cache initialized earlier.
      entryCacheConfigManager.initializeEntryCache();

      initializeExtendedOperations();
      initializeSASLMechanisms();

      if (startConnectionHandlers)
      {
        // Includes the administration connector.
        initializeConnectionHandlers();
      }

      monitorConfigManager = new MonitorConfigManager(serverContext);
      monitorConfigManager.initializeMonitorProviders();

      pluginConfigManager.initializeUserPlugins(null);

      if (!environmentConfig.disableSynchronization())
      {
        synchronizationProviderConfigManager = new SynchronizationProviderConfigManager(serverContext);
        synchronizationProviderConfigManager.initializeSynchronizationProviders();
      }

      workQueue = new WorkQueueConfigManager(serverContext).initializeWorkQueue();

      PluginResult.Startup startupPluginResult = pluginConfigManager.invokeStartupPlugins();
      if (! startupPluginResult.continueProcessing())
      {
        throw new InitializationException(ERR_STARTUP_PLUGIN_ERROR.get(startupPluginResult.getErrorMessage(),
                startupPluginResult.getErrorMessage().ordinal()));
      }

      for (InitializationCompletedListener listener : initializationCompletedListeners)
      {
        try
        {
          listener.initializationCompleted();
        }
        catch (Exception e)
        {
          logger.traceException(e);
        }
      }

      if (startConnectionHandlers)
      {
        startConnectionHandlers();
        new IdleTimeLimitThread().start();
      }

      // Write a copy of the config if needed.
      if (coreConfigManager.isSaveConfigOnSuccessfulStartup())
      {
        configurationHandler.writeSuccessfulStartupConfig();
      }

      LocalizableMessage message = NOTE_DIRECTORY_SERVER_STARTED.get();
      logger.info(message);
      sendAlertNotification(this, ALERT_TYPE_SERVER_STARTED, message);

      // Force the root connection to be initialized.
      InternalClientConnection rootConnection = InternalClientConnection.getRootConnection();

      if (! environmentConfig.disableAdminDataSynchronization())
      {
        AdministrationDataSync admDataSync = new AdministrationDataSync(rootConnection);
        admDataSync.synchronize();
      }

      httpEndpointConfigManager = new HttpEndpointConfigManager(serverContext);
      httpEndpointConfigManager.registerTo(serverContext.getServerManagementContext().getRootConfiguration());

      deleteUnnecessaryFiles();

      isRunning = true;
    }
  }

  private void initializeBackendConfigManager()
  {
    backendConfigManager = new BackendConfigManager(serverContext);
  }

  /** Delete "server.starting" and "hostname" files if they are present. */
  private void deleteUnnecessaryFiles()
  {
    File serverStartingFile = new File(environmentConfig.getInstanceRoot() + File.separator + "logs"
        + File.separator + "server.starting");
    if (serverStartingFile.exists())
    {
      serverStartingFile.delete();
    }

    File hostNameFile = new File(environmentConfig.getInstanceRoot() + File.separator + SetupUtils.HOST_NAME_FILE);
    if (hostNameFile.exists())
    {
      hostNameFile.delete();
    }
  }

  private void initializeAuthenticatedUsers()
  {
    directoryServer.authenticatedUsers = new AuthenticatedUsers();
  }

  /**
   * Retrieves the authenticated users manager for the Directory Server.
   *
   * @return  The authenticated users manager for the Directory Server.
   */
  public static AuthenticatedUsers getAuthenticatedUsers()
  {
    return directoryServer.authenticatedUsers;
  }

  private void initializeCryptoManager()
         throws ConfigException, InitializationException
  {
    CryptoManagerCfg cryptoManagerCfg = serverContext.getRootConfig().getCryptoManager();
    cryptoManager = new CryptoManagerImpl(serverContext, cryptoManagerCfg);
  }

  /**
   * Retrieves a reference to the Directory Server crypto manager.
   *
   * @return  A reference to the Directory Server crypto manager.
   */
  public static CryptoManagerImpl getCryptoManager()
  {
    return directoryServer.cryptoManager;
  }

  /**
   * Initializes the schema handler, which is responsible for building the complete schema for the
   * server.
   *
   * @throws ConfigException
   *           If there is a configuration problem with any of the schema elements.
   * @throws InitializationException
   *           If a problem occurs while initializing the schema that is not related to the server
   *           configuration.
   */
  public void initializeSchema() throws InitializationException, ConfigException
  {
    schemaHandler.initialize(serverContext);
    schemaHandler.detectChangesOnInitialization();

    // With server schema in place set compressed schema.
    compressedSchema = new DefaultCompressedSchema(serverContext);
  }

  /**
   * Retrieves the default compressed schema manager for the Directory Server.
   *
   * @return  The default compressed schema manager for the Directory Server.
   */
  public static CompressedSchema getDefaultCompressedSchema()
  {
    return directoryServer.compressedSchema;
  }

  private void initializeRootAndAdminDataBackends() throws ConfigException, InitializationException
  {
    backendConfigManager.initializeBackendConfig(Arrays.asList("adminRoot", "ads-truststore"));
  }

  private void initializeRemainingBackends() throws ConfigException, InitializationException
  {
    if (backendConfigManager == null)
    {
      throw new InitializationException(ERR_MISSING_ADMIN_BACKENDS.get());
    }
    backendConfigManager.initializeBackends(Collections.<String>emptyList(), serverContext.getRootConfig());
  }

  /**
   * Initializes the Directory Server group manager.
   *
   * @throws  ConfigException  If there is a configuration problem with any of
   *                           the group implementations.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the group manager that is not related to
   *                                   the server configuration.
   */
  private void initializeGroupManager()
         throws ConfigException, InitializationException
  {
    try
    {
      groupManager = new GroupManager(serverContext);
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);

      throw new InitializationException(de.getMessageObject());
    }

    groupManager.initializeGroupImplementations();

    // The configuration backend has already been registered by this point
    // so we need to handle it explicitly.
    // Because subentryManager may depend on the groupManager, let's delay this.
    // groupManager.performBackendPreInitializationProcessing(configurationHandler);
  }

  /**
   * Retrieves the Directory Server group manager.
   *
   * @return  The Directory Server group manager.
   */
  public static GroupManager getGroupManager()
  {
    return directoryServer.groupManager;
  }

  /**
   * Retrieves the Directory Server subentry manager.
   *
   * @return  The Directory Server subentry manager.
   */
  public static SubentryManager getSubentryManager()
  {
    return directoryServer.subentryManager;
  }

  /**
   * Initializes the set of extended operation handlers for the Directory
   * Server.
   *
   * @throws  ConfigException  If there is a configuration problem with any of
   *                           the extended operation handlers.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the extended operation handlers that is
   *                                   not related to the server configuration.
   */
  private void initializeExtendedOperations()
          throws ConfigException, InitializationException
  {
    extendedOperationConfigManager = new ExtendedOperationConfigManager(serverContext);
    extendedOperationConfigManager.initializeExtendedOperationHandlers();
  }

  /**
   * Initializes the set of SASL mechanism handlers for the Directory Server.
   *
   * @throws  ConfigException  If there is a configuration problem with any of
   *                           the SASL mechanism handlers.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the SASL mechanism handlers that is not
   *                                   related to the server configuration.
   */
  private void initializeSASLMechanisms()
          throws ConfigException, InitializationException
  {
    saslConfigManager = new SASLConfigManager(serverContext);
    saslConfigManager.initializeSASLMechanismHandlers();
  }

  /**
   * Initializes the set of connection handlers that should be defined in the
   * Directory Server.
   *
   * @throws  ConfigException  If there is a configuration problem with any of
   *                           the connection handlers.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the connection handlers that is not
   *                                   related to the server configuration.
   */
  private void initializeConnectionHandlers()
          throws ConfigException, InitializationException
  {
    if (connectionHandlerConfigManager == null) {
      connectionHandlerConfigManager = new ConnectionHandlerConfigManager(serverContext);
    }
    connectionHandlerConfigManager.initializeConnectionHandlerConfig();
  }

  /**
   * Initializes the subentry manager for the Directory Server.
   * Note that the subentry manager initialization should be
   * done before any dependent components initialization and
   * before bringing any backends online. Configuration backend
   * is a special case and therefore is exception to this rule.
   *
   * @throws InitializationException If a problem occurs while
   *                                 initializing the subentry
   *                                 manager.
   */
  private void initializeSubentryManager() throws InitializationException
  {
    try
    {
      subentryManager = new SubentryManager();

      // The configuration backend should already be registered
      // at this point so we need to handle it explicitly here.
      // However, subentryManager may have dependencies on the
      // groupManager. So lets delay the backend initialization until then.
      // subentryManager.performBackendPreInitializationProcessing(configurationHandler);
    }
    catch (DirectoryException de)
    {
      throw new InitializationException(de.getMessageObject());
    }
  }

  /**
   * Initializes the set of authentication policy components for use by the
   * Directory Server.
   * For the time this is mostly PasswordPolicy but new Authentication Policy
   * extensions should be initialized at the same time.
   *
   * @throws ConfigException
   *           If there is a configuration problem with any of the
   *           authentication policy components.
   * @throws InitializationException
   *           If a problem occurs while initializing the authentication policy
   *           components that is not related to the server configuration.
   */
  private void initializeAuthenticationPolicyComponents() throws ConfigException, InitializationException
  {
    storageSchemeConfigManager = new PasswordStorageSchemeConfigManager(serverContext);
    storageSchemeConfigManager.initializePasswordStorageSchemes();

    passwordValidatorConfigManager = new PasswordValidatorConfigManager(serverContext);
    passwordValidatorConfigManager.initializePasswordValidators();

    passwordGeneratorConfigManager = new PasswordGeneratorConfigManager(serverContext);
    passwordGeneratorConfigManager.initializePasswordGenerators();

    accountStatusNotificationHandlerConfigManager = new AccountStatusNotificationHandlerConfigManager(serverContext);
    accountStatusNotificationHandlerConfigManager.initializeNotificationHandlers();

    authenticationPolicyConfigManager = new PasswordPolicyConfigManager(serverContext);
    authenticationPolicyConfigManager.initializeAuthenticationPolicies();
  }

  /**
   * Retrieves the operating system on which the Directory Server is running.
   *
   * @return  The operating system on which the Directory Server is running.
   */
  public static OperatingSystem getOperatingSystem()
  {
    return directoryServer.operatingSystem;
  }

  private void initializePlugins(Set<PluginType> pluginTypes)
         throws ConfigException, InitializationException
  {
    pluginConfigManager = new PluginConfigManager(serverContext);
    pluginConfigManager.initializePluginConfigManager();
    pluginConfigManager.initializeUserPlugins(pluginTypes);
  }

  /**
   *  Initializes the root DN Config Manager in the Directory Server.
   *
   * @throws ConfigException If a problem occurs registering a DN.
   * @throws InitializationException If a problem occurs initializing the root
   *                                 DN manager.
   */
  private void initializeRootDNConfigManager()
         throws ConfigException, InitializationException{
    rootDNConfigManager = new RootDNConfigManager(serverContext);
    rootDNConfigManager.initializeRootDNs();
  }

  /**
   * Retrieves a reference to the Directory Server plugin configuration manager.
   *
   * @return  A reference to the Directory Server plugin configuration manager.
   */
  public static PluginConfigManager getPluginConfigManager()
  {
    return directoryServer.pluginConfigManager;
  }

  /**
   * Registers the provided internal plugin with the Directory Server
   * and ensures that it will be invoked in the specified ways.
   *
   * @param plugin
   *          The internal plugin to register with the Directory Server.
   *          The plugin must specify a configuration entry which is
   *          guaranteed to be unique.
   */
  public static void registerInternalPlugin(
      InternalDirectoryServerPlugin plugin)
  {
    directoryServer.pluginConfigManager.registerInternalPlugin(plugin);
  }

  /**
   * Deregisters the provided internal plugin with the Directory Server.
   *
   * @param plugin
   *          The internal plugin to deregister from the Directory Server.
   */
  public static void deregisterInternalPlugin(
      InternalDirectoryServerPlugin plugin)
  {
    directoryServer.pluginConfigManager.deregisterInternalPlugin(plugin);
  }

  /**
   * Retrieves the requested entry from the Directory Server configuration.
   * <p>
   * The main difference with {@link #getEntry(DN)} is that virtual attributes are not processed.
   * This is important when the whole directory server is not initialized yet (when initializing all backends).
   *
   * @param  entryDN  The DN of the configuration entry to retrieve.
   * @return  The requested entry from the Directory Server configuration.
   * @throws  ConfigException  If a problem occurs while trying to retrieve the requested entry.
   * @deprecated use {@link #getEntry(DN)} when possible
   */
  @Deprecated
  public static Entry getConfigEntry(DN entryDN) throws ConfigException
  {
    org.forgerock.opendj.ldap.Entry entry = directoryServer.configurationHandler.getEntry(entryDN);
    return entry != null ? Converters.to(entry) : null;
  }

  /**
   * Retrieves the path to the root directory for this instance of the Directory
   * Server.
   *
   * @return  The path to the root directory for this instance of the Directory
   *          Server.
  */
  public static String getServerRoot()
  {
    return getInstance().getServerContext().getServerRoot();
  }

  /**
   * Retrieves the path to the instance directory for this instance of the
   * Directory Server.
   *
   * @return The path to the instance directory for this instance of
   * the Directory Server.
   */
  public static String getInstanceRoot()
  {
    return getInstance().getServerContext().getInstanceRoot();
  }

  /**
   * Retrieves the time that the Directory Server was started, in milliseconds
   * since the epoch.
   *
   * @return  The time that the Directory Server was started, in milliseconds
   *          since the epoch.
   */
  public static long getStartTime()
  {
    return directoryServer.startUpTime;
  }

  /**
   * Retrieves the time that the Directory Server was started, formatted in UTC.
   *
   * @return  The time that the Directory Server was started, formatted in UTC.
   */
  public static String getStartTimeUTC()
  {
    return directoryServer.startTimeUTC;
  }

  /**
   * Retrieves the set of virtual attribute rules registered with the Directory
   * Server.
   *
   * @return  The set of virtual attribute rules registered with the Directory
   *          Server.
   */
  public static Collection<VirtualAttributeRule> getVirtualAttributes()
  {
    return directoryServer.virtualAttributeConfigManager.getVirtualAttributes();
  }

  /**
   * Retrieves the set of virtual attribute rules registered with the Directory
   * Server that are applicable to the provided entry.
   *
   * @param  entry  The entry for which to retrieve the applicable virtual
   *                attribute rules.
   *
   * @return  The set of virtual attribute rules registered with the Directory
   *          Server that apply to the given entry.  It may be an empty list if
   *          there are no applicable virtual attribute rules.
   */
  public static List<VirtualAttributeRule> getVirtualAttributes(Entry entry)
  {
    List<VirtualAttributeRule> ruleList = new LinkedList<>();
    for (VirtualAttributeRule rule : getVirtualAttributes())
    {
      if (rule.appliesToEntry(entry))
      {
        ruleList.add(rule);
      }
    }
    return ruleList;
  }

  /**
   * Registers the provided virtual attribute rule with the Directory Server.
   *
   * @param  rule  The virtual attribute rule to be registered.
   */
  public static void registerVirtualAttribute(final VirtualAttributeRule rule)
  {
    getInstance().virtualAttributeConfigManager.register(rule);
  }

  /**
   * Deregisters the provided virtual attribute rule with the Directory Server.
   *
   * @param  rule  The virtual attribute rule to be deregistered.
   */
  public static void deregisterVirtualAttribute(VirtualAttributeRule rule)
  {
    getInstance().virtualAttributeConfigManager.deregister(rule);
  }

  /**
   * Retrieves a reference to the JMX MBean server that is associated with the
   * Directory Server.
   *
   * @return  The JMX MBean server that is associated with the Directory Server.
   */
  public static MBeanServer getJMXMBeanServer()
  {
    return directoryServer.mBeanServer;
  }

  /**
   * Retrieves the set of JMX MBeans that are associated with the server.
   *
   * @return  The set of JMX MBeans that are associated with the server.
   */
  public static Collection<JMXMBean> getJMXMBeans()
  {
    return directoryServer.mBeans.values();
  }

  /**
   * Retrieves the JMX MBean associated with the specified entry in the
   * Directory Server configuration.
   *
   * @param  configEntryDN  The DN of the configuration entry for which to
   *                        retrieve the associated JMX MBean.
   *
   * @return  The JMX MBean associated with the specified entry in the Directory
   *          Server configuration, or {@code null} if there is no MBean
   *          for the specified entry.
   */
  public static JMXMBean getJMXMBean(DN configEntryDN)
  {
    return directoryServer.mBeans.get(configEntryDN);
  }

  /**
   * Registers the provided alert generator with the Directory Server.
   *
   * @param  alertGenerator  The alert generator to register.
   */
  public static void registerAlertGenerator(AlertGenerator alertGenerator)
  {
    DN componentDN = alertGenerator.getComponentEntryDN();
    JMXMBean mBean = directoryServer.mBeans.get(componentDN);
    if (mBean == null)
    {
      mBean = new JMXMBean(componentDN);
      mBean.addAlertGenerator(alertGenerator);
      directoryServer.mBeans.put(componentDN, mBean);
    }
    else
    {
      mBean.addAlertGenerator(alertGenerator);
    }
  }

  /**
   * Deregisters the provided alert generator with the Directory Server.
   *
   * @param  alertGenerator  The alert generator to deregister.
   */
  public static void deregisterAlertGenerator(AlertGenerator alertGenerator)
  {
    DN componentDN = alertGenerator.getComponentEntryDN();
    JMXMBean mBean = directoryServer.mBeans.get(componentDN);
    if (mBean != null)
    {
      mBean.removeAlertGenerator(alertGenerator);
    }
  }

  /**
   * Retrieves the set of alert handlers that have been registered with the
   * Directory Server.
   *
   * @return  The set of alert handlers that have been registered with the
   *          Directory Server.
   */
  public static List<AlertHandler<?>> getAlertHandlers()
  {
    return directoryServer.alertHandlers;
  }

  /**
   * Registers the provided alert handler with the Directory Server.
   *
   * @param  alertHandler  The alert handler to register.
   */
  public static void registerAlertHandler(AlertHandler<?> alertHandler)
  {
    directoryServer.alertHandlers.add(alertHandler);
  }

  /**
   * Deregisters the provided alert handler with the Directory Server.
   *
   * @param  alertHandler  The alert handler to deregister.
   */
  public static void deregisterAlertHandler(AlertHandler<?> alertHandler)
  {
    directoryServer.alertHandlers.remove(alertHandler);
  }

  /**
   * Sends an alert notification with the provided information.
   *
   * @param  generator     The alert generator that created the alert.
   * @param  alertType     The alert type name for this alert.
   * @param  alertMessage  A message (possibly {@code null}) that can
   */
  public static void sendAlertNotification(AlertGenerator generator,
                                           String alertType,
                                           LocalizableMessage alertMessage)
  {
    if (directoryServer.alertHandlers == null
        || directoryServer.alertHandlers.isEmpty())
    {
      // If the Directory Server is still in the process of starting up, then
      // create a JMX alert handler to use for this notification.
      if (! directoryServer.isRunning)
      {
        try
        {
          JMXAlertHandler alertHandler = new JMXAlertHandler();
          alertHandler.initializeAlertHandler(null);
          alertHandler.sendAlertNotification(generator, alertType,
                                             alertMessage);
        }
        catch (Exception e)
        {
          logger.traceException(e);
        }
      }
    }
    else
    {
      for (AlertHandler<?> alertHandler : directoryServer.alertHandlers)
      {
        AlertHandlerCfg config = alertHandler.getAlertHandlerConfiguration();
        Set<String> enabledAlerts = config.getEnabledAlertType();
        Set<String> disabledAlerts = config.getDisabledAlertType();
        if (enabledAlerts == null
            || enabledAlerts.isEmpty()
            || enabledAlerts.contains(alertType))
        {
          if (disabledAlerts != null && disabledAlerts.contains(alertType))
          {
            continue;
          }
        }
        else
        {
          continue;
        }

        alertHandler.sendAlertNotification(generator, alertType, alertMessage);
      }
    }

    String alertID = alertMessage != null ? alertMessage.resourceName() + "-" + alertMessage.ordinal() : "-1";
    logger.info(NOTE_SENT_ALERT_NOTIFICATION, generator.getClassName(), alertType, alertID, alertMessage);
  }

  /**
   * Retrieves the password storage scheme defined in the specified
   * configuration entry.
   *
   * @param  configEntryDN  The DN of the configuration entry that defines the
   *                        password storage scheme to retrieve.
   *
   * @return  The requested password storage scheme, or {@code null} if no such
   *          scheme is defined.
   */
  public static PasswordStorageScheme<?> getPasswordStorageScheme(DN configEntryDN)
  {
    return directoryServer.passwordStorageSchemesByDN.get(configEntryDN);
  }

  /**
   * Retrieves the set of password storage schemes defined in the Directory
   * Server, as a mapping between the all-lowercase scheme name and the
   * corresponding implementation.
   *
   * @return  The set of password storage schemes defined in the Directory
   *          Server.
   */
  public static Collection<PasswordStorageScheme<?>> getPasswordStorageSchemes()
  {
    return directoryServer.passwordStorageSchemes.values();
  }

  /**
   * Retrieves the specified password storage scheme.
   *
   * @param  lowerName  The name of the password storage scheme to retrieve,
   *                    formatted in all lowercase characters.
   *
   * @return  The requested password storage scheme, or {@code null} if no
   *          such scheme is defined.
   */
  public static PasswordStorageScheme<?> getPasswordStorageScheme(String lowerName)
  {
    return directoryServer.passwordStorageSchemes.get(lowerName);
  }

  /**
   * Retrieves the set of authentication password storage schemes defined in the
   * Directory Server, as a mapping between the scheme name and the
   * corresponding implementation.
   *
   * @return  The set of authentication password storage schemes defined in the
   *          Directory Server.
   */
  public static ConcurrentHashMap<String, PasswordStorageScheme<?>> getAuthPasswordStorageSchemes()
  {
    return directoryServer.authPasswordStorageSchemes;
  }

  /**
   * Retrieves the specified authentication password storage scheme.
   *
   * @param  name  The case-sensitive name of the authentication password
   *               storage scheme to retrieve.
   *
   * @return  The requested authentication password storage scheme, or
   *          {@code null} if no such scheme is defined.
   */
  public static PasswordStorageScheme<?> getAuthPasswordStorageScheme(String name)
  {
    return directoryServer.authPasswordStorageSchemes.get(name);
  }

  /**
   * Registers the provided password storage scheme with the Directory Server.
   * If an existing password storage scheme is registered with the same name,
   * then it will be replaced with the provided scheme.
   *
   * @param  configEntryDN  The DN of the configuration entry that defines the
   *                        password storage scheme.
   * @param  scheme         The password storage scheme to register with the
   *                        Directory Server.
   */
  public static void registerPasswordStorageScheme(DN configEntryDN, PasswordStorageScheme<?> scheme)
  {
    directoryServer.passwordStorageSchemesByDN.put(configEntryDN, scheme);

    String name = toLowerCase(scheme.getStorageSchemeName());
    directoryServer.passwordStorageSchemes.put(name, scheme);

    if (scheme.supportsAuthPasswordSyntax())
    {
      directoryServer.authPasswordStorageSchemes.put(
           scheme.getAuthPasswordSchemeName(), scheme);
    }
  }

  /**
   * Deregisters the specified password storage scheme with the Directory
   * Server.  If no scheme is registered with the specified name, then no action
   * will be taken.
   *
   * @param  configEntryDN  The DN of the configuration entry that defines the
   *                        password storage scheme.
   */
  public static void deregisterPasswordStorageScheme(DN configEntryDN)
  {
    PasswordStorageScheme<?> scheme = directoryServer.passwordStorageSchemesByDN.remove(configEntryDN);

    if (scheme != null)
    {
      directoryServer.passwordStorageSchemes.remove(
           toLowerCase(scheme.getStorageSchemeName()));

      if (scheme.supportsAuthPasswordSyntax())
      {
        directoryServer.authPasswordStorageSchemes.remove(
             scheme.getAuthPasswordSchemeName());
      }
    }
  }

  /**
   * Retrieves the password validator registered with the provided configuration
   * entry DN.
   *
   * @param  configEntryDN  The DN of the configuration entry for which to
   *                        retrieve the associated password validator.
   *
   * @return  The requested password validator, or {@code null} if no such
   *          validator is defined.
   */
  public static PasswordValidator<? extends PasswordValidatorCfg>
                     getPasswordValidator(DN configEntryDN)
  {
    return directoryServer.passwordValidators.get(configEntryDN);
  }

  /**
   * Registers the provided password validator for use with the Directory
   * Server.
   *
   * @param  configEntryDN  The DN of the configuration entry that defines the
   *                        specified password validator.
   * @param  validator      The password validator to register with the
   *                        Directory Server.
   */
  public static void
       registerPasswordValidator(DN configEntryDN,
            PasswordValidator<? extends PasswordValidatorCfg>
            validator)
  {
    directoryServer.passwordValidators.put(configEntryDN, validator);
  }

  /**
   * Deregisters the provided password validator for use with the Directory
   * Server.
   *
   * @param  configEntryDN  The DN of the configuration entry that defines the
   *                        password validator to deregister.
   */
  public static void deregisterPasswordValidator(DN configEntryDN)
  {
    directoryServer.passwordValidators.remove(configEntryDN);
  }

  /**
   * Retrieves the account status notification handler with the specified
   * configuration entry DN.
   *
   * @param  handlerDN  The DN of the configuration entry associated with the
   *                    account status notification handler to retrieve.
   *
   * @return  The requested account status notification handler, or
   *          {@code null} if no such handler is defined in the server.
   */
  public static AccountStatusNotificationHandler<?>
                     getAccountStatusNotificationHandler(DN handlerDN)
  {
    return directoryServer.accountStatusNotificationHandlers.get(handlerDN);
  }

  /**
   * Registers the provided account status notification handler with the
   * Directory Server.
   *
   * @param  handlerDN  The DN of the configuration entry that defines the
   *                    provided account status notification handler.
   * @param  handler    The account status notification handler to register with
   *                    the Directory Server.
   */
  public static void registerAccountStatusNotificationHandler(DN handlerDN,
      AccountStatusNotificationHandler<?> handler)
  {
    directoryServer.accountStatusNotificationHandlers.put(handlerDN, handler);
  }

  /**
   * Deregisters the specified account status notification handler with the
   * Directory Server.
   *
   * @param  handlerDN  The DN of the configuration entry for the account status
   *                    notification handler to deregister.
   */
  public static void deregisterAccountStatusNotificationHandler(DN handlerDN)
  {
    directoryServer.accountStatusNotificationHandlers.remove(handlerDN);
  }

  /**
   * Retrieves the password generator registered with the provided configuration
   * entry DN.
   *
   * @param  configEntryDN  The DN of the configuration entry for which to
   *                        retrieve the associated password generator.
   *
   * @return  The requested password generator, or {@code null} if no such
   *          generator is defined.
   */
  public static PasswordGenerator<?> getPasswordGenerator(DN configEntryDN)
  {
    return directoryServer.passwordGenerators.get(configEntryDN);
  }

  /**
   * Registers the provided password generator for use with the Directory
   * Server.
   *
   * @param  configEntryDN  The DN of the configuration entry that defines the
   *                        specified password generator.
   * @param  generator      The password generator to register with the
   *                        Directory Server.
   */
  public static void registerPasswordGenerator(DN configEntryDN, PasswordGenerator<?> generator)
  {
    directoryServer.passwordGenerators.put(configEntryDN, generator);
  }

  /**
   * Deregisters the provided password generator for use with the Directory
   * Server.
   *
   * @param  configEntryDN  The DN of the configuration entry that defines the
   *                        password generator to deregister.
   */
  public static void deregisterPasswordGenerator(DN configEntryDN)
  {
    directoryServer.passwordGenerators.remove(configEntryDN);
  }

  /**
   * Returns an unmodifiable collection containing all of the authentication
   * policies registered with the Directory Server. The references returned are
   * to the actual authentication policy objects currently in use by the
   * directory server and the referenced objects must not be modified.
   *
   * @return The unmodifiable collection containing all of the authentication
   *         policies registered with the Directory Server.
   */
  public static Collection<AuthenticationPolicy> getAuthenticationPolicies()
  {
    return Collections
       .unmodifiableCollection(directoryServer.authenticationPolicies.values());
  }

  /**
   * Retrieves the authentication policy registered for the provided
   * configuration entry.
   *
   * @param configEntryDN
   *          The DN of the configuration entry for which to retrieve the
   *          associated authentication policy.
   * @return The authentication policy registered for the provided configuration
   *         entry, or {@code null} if there is no such policy.
   */
  public static AuthenticationPolicy getAuthenticationPolicy(DN configEntryDN)
  {
    Reject.ifNull(configEntryDN);
    return directoryServer.authenticationPolicies.get(configEntryDN);
  }

  /**
   * Registers the provided authentication policy with the Directory Server. If
   * a policy is already registered for the provided configuration entry DN,
   * then it will be replaced.
   *
   * @param configEntryDN
   *          The DN of the configuration entry that defines the authentication
   *          policy.
   * @param policy
   *          The authentication policy to register with the server.
   */
  public static void registerAuthenticationPolicy(DN configEntryDN,
      AuthenticationPolicy policy)
  {
    Reject.ifNull(configEntryDN, policy);

    // Ensure default policy is synchronized.
    synchronized (directoryServer.authenticationPolicies)
    {
      if (getCoreConfigManager().getDefaultPasswordPolicyDN().equals(configEntryDN))
      {
        // The correct policy type is enforced by the core config manager.
        directoryServer.defaultPasswordPolicy = (PasswordPolicy) policy;
      }

      AuthenticationPolicy oldPolicy = directoryServer.authenticationPolicies
          .put(configEntryDN, policy);

      if (oldPolicy != null)
      {
        oldPolicy.finalizeAuthenticationPolicy();
      }
    }
  }

  /**
   * Deregisters the provided authentication policy with the Directory Server.
   * If no such policy is registered, then no action will be taken.
   *
   * @param configEntryDN
   *          The DN of the configuration entry that defines the authentication
   *          policy to deregister.
   */
  public static void deregisterAuthenticationPolicy(DN configEntryDN)
  {
    Reject.ifNull(configEntryDN);

    // Ensure default policy is synchronized.
    synchronized (directoryServer.authenticationPolicies)
    {
      if (getCoreConfigManager().getDefaultPasswordPolicyDN().equals(configEntryDN))
      {
        directoryServer.defaultPasswordPolicy = null;
      }

      AuthenticationPolicy oldPolicy = directoryServer.authenticationPolicies
          .remove(configEntryDN);
      if (oldPolicy != null)
      {
        oldPolicy.finalizeAuthenticationPolicy();
      }
    }
  }

  /**
   * Resets the default password policy to null.
   */
  public static void resetDefaultPasswordPolicy()
  {
    // Ensure default policy is synchronized.
    synchronized (directoryServer.authenticationPolicies)
    {
      directoryServer.defaultPasswordPolicy = null;
    }
  }

  /**
   * Retrieves the default password policy for the Directory Server. This
   * method is equivalent to invoking <CODE>getAuthenticationPolicy</CODE> on
   * the DN returned from
   * <CODE>DirectoryServer.getDefaultPasswordPolicyDN()</CODE>.
   *
   * @return The default password policy for the Directory Server.
   */
  public static PasswordPolicy getDefaultPasswordPolicy()
  {
    // Ensure default policy is synchronized.
    synchronized (directoryServer.authenticationPolicies)
    {
      DN defaultPasswordPolicyDN = getCoreConfigManager().getDefaultPasswordPolicyDN();
      assert null != directoryServer.authenticationPolicies
          .get(defaultPasswordPolicyDN) : "Internal Error: no default password policy defined.";

      if (directoryServer.defaultPasswordPolicy == null
          && defaultPasswordPolicyDN != null)
      {
        // The correct policy type is enforced by the core config manager.
        directoryServer.defaultPasswordPolicy = (PasswordPolicy)
          directoryServer.authenticationPolicies.get(defaultPasswordPolicyDN);
      }
      assert directoryServer.authenticationPolicies.get(defaultPasswordPolicyDN) ==
          directoryServer.defaultPasswordPolicy : "Internal Error: inconsistency between defaultPasswordPolicy"
          + " cache and value in authenticationPolicies map.";
      return directoryServer.defaultPasswordPolicy;
    }
  }

  /**
   * Retrieves the log rotation policy registered for the provided configuration
   * entry.
   *
   * @param  configEntryDN  The DN of the configuration entry for which to
   *                        retrieve the associated rotation policy.
   *
   * @return  The rotation policy registered for the provided configuration
   *          entry, or {@code null} if there is no such policy.
   */
  public static RotationPolicy<?> getRotationPolicy(DN configEntryDN)
  {
    Reject.ifNull(configEntryDN);

    return directoryServer.rotationPolicies.get(configEntryDN);
  }

    /**
   * Registers the provided log rotation policy with the Directory Server.  If a
   * policy is already registered for the provided configuration entry DN, then
   * it will be replaced.
   *
   * @param  configEntryDN  The DN of the configuration entry that defines the
   *                        password policy.
   * @param  policy         The rotation policy to register with the server.
   */
  public static void registerRotationPolicy(DN configEntryDN, RotationPolicy<?> policy)
  {
    Reject.ifNull(configEntryDN, policy);

    directoryServer.rotationPolicies.put(configEntryDN, policy);
  }

  /**
   * Deregisters the provided log rotation policy with the Directory Server.
   * If no such policy is registered, then no action will be taken.
   *
   * @param  configEntryDN  The DN of the configuration entry that defines the
   *                        rotation policy to deregister.
   */
  public static void deregisterRotationPolicy(DN configEntryDN)
  {
    Reject.ifNull(configEntryDN);

    directoryServer.rotationPolicies.remove(configEntryDN);
  }

  /**
   * Retrieves the log retention policy registered for the provided
   * configuration entry.
   *
   * @param  configEntryDN  The DN of the configuration entry for which to
   *                        retrieve the associated retention policy.
   *
   * @return  The retention policy registered for the provided configuration
   *          entry, or {@code null} if there is no such policy.
   */
  public static RetentionPolicy<?> getRetentionPolicy(DN configEntryDN)
  {
    Reject.ifNull(configEntryDN);

    return directoryServer.retentionPolicies.get(configEntryDN);
  }

  /**
   * Registers the provided log retention policy with the Directory Server.
   * If a policy is already registered for the provided configuration entry DN,
   * then it will be replaced.
   *
   * @param  configEntryDN  The DN of the configuration entry that defines the
   *                        password policy.
   * @param  policy         The retention policy to register with the server.
   */
  public static void registerRetentionPolicy(DN configEntryDN, RetentionPolicy<?> policy)
  {
    Reject.ifNull(configEntryDN, policy);

    directoryServer.retentionPolicies.put(configEntryDN, policy);
  }

  /**
   * Deregisters the provided log retention policy with the Directory Server.
   * If no such policy is registered, then no action will be taken.
   *
   * @param  configEntryDN  The DN of the configuration entry that defines the
   *                        retention policy to deregister.
   */
  public static void deregisterRetentionPolicy(DN configEntryDN)
  {
    Reject.ifNull(configEntryDN);

    directoryServer.retentionPolicies.remove(configEntryDN);
  }

  /**
   * Retrieves the set of monitor providers that have been registered with the
   * Directory Server, as a mapping between the monitor name (in all lowercase
   * characters) and the monitor implementation.
   *
   * @return  The set of monitor providers that have been registered with the
   *          Directory Server.
   */
  public static ConcurrentMap<String,
                                  MonitorProvider<? extends MonitorProviderCfg>>
                     getMonitorProviders()
  {
    return directoryServer.monitorProviders;
  }

  /**
   * Registers the provided monitor provider with the Directory Server.  Note
   * that if a monitor provider is already registered with the specified name,
   * then it will be replaced with the provided implementation.
   *
   * @param  monitorProvider  The monitor provider to register with the
   *                          Directory Server.
   */
  public static void registerMonitorProvider(
                          MonitorProvider<? extends MonitorProviderCfg>
                               monitorProvider)
  {
    String lowerName = toLowerCase(monitorProvider.getMonitorInstanceName());
    directoryServer.monitorProviders.put(lowerName, monitorProvider);

    // Try to register this monitor provider with an appropriate JMX MBean.
    try
    {
      DN monitorDN = getMonitorProviderDN(monitorProvider);
      JMXMBean mBean = directoryServer.mBeans.get(monitorDN);
      if (mBean == null)
      {
        mBean = new JMXMBean(monitorDN);
        mBean.addMonitorProvider(monitorProvider);
        directoryServer.mBeans.put(monitorDN, mBean);
      }
      else
      {
        mBean.addMonitorProvider(monitorProvider);
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }
  }

  /**
   * Deregisters the specified monitor provider from the Directory Server. If no
   * such monitor provider is registered, no action will be taken.
   *
   * @param monitorProvider
   *          The monitor provider to deregister from the Directory Server.
   */
  public static void deregisterMonitorProvider(
      MonitorProvider<? extends MonitorProviderCfg> monitorProvider)
  {
    String monitorName = toLowerCase(monitorProvider.getMonitorInstanceName());
    MonitorProvider<?> provider = directoryServer.monitorProviders
        .remove(monitorName);

    // Try to deregister the monitor provider as an MBean.
    if (provider != null)
    {
      try
      {
        DN monitorDN = getMonitorProviderDN(provider);
        JMXMBean mBean = directoryServer.mBeans.get(monitorDN);
        if (mBean != null)
        {
          mBean.removeMonitorProvider(provider);
          if (mBean.getMonitorProviders().isEmpty() && mBean.getAlertGenerators().isEmpty())
          {
            directoryServer.mBeans.remove(monitorDN);
            directoryServer.mBeanServer.unregisterMBean(mBean.getObjectName());
          }
        }
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
  }

  /**
   * Retrieves the entry cache for the Directory Server.
   *
   * @return  The entry cache for the Directory Server.
   */
  public static EntryCache<?> getEntryCache()
  {
    return directoryServer.entryCache;
  }

  /**
   * Specifies the entry cache that should be used by the Directory Server.
   * This should only be called by the entry cache configuration manager.
   *
   * @param  entryCache  The entry cache for the Directory Server.
   */
  public static void setEntryCache(EntryCache<?> entryCache)
  {
    synchronized (directoryServer)
    {
      directoryServer.entryCache = entryCache;
    }
  }

  /**
   * Retrieves the key manager provider registered with the provided entry DN.
   *
   * @param  providerDN  The DN with which the key manager provider is
   *                     registered.
   *
   * @return  The key manager provider registered with the provided entry DN, or
   *          {@code null} if there is no such key manager provider registered
   *          with the server.
   */
  public static KeyManagerProvider<?> getKeyManagerProvider(DN providerDN)
  {
    return directoryServer.keyManagerProviders.get(providerDN);
  }

  /**
   * Registers the provided key manager provider with the Directory Server.
   *
   * @param  providerDN  The DN with which to register the key manager provider.
   * @param  provider    The key manager provider to register with the server.
   */
  public static void registerKeyManagerProvider(DN providerDN,
      KeyManagerProvider<?> provider)
  {
    directoryServer.keyManagerProviders.put(providerDN, provider);
  }

  /**
   * Deregisters the specified key manager provider with the Directory Server.
   *
   * @param  providerDN  The DN with which the key manager provider is
   *                     registered.
   */
  public static void deregisterKeyManagerProvider(DN providerDN)
  {
    directoryServer.keyManagerProviders.remove(providerDN);
  }

  /**
   * Retrieves the trust manager provider registered with the provided entry DN.
   *
   * @param  providerDN  The DN with which the trust manager provider is
   *                     registered.
   *
   * @return  The trust manager provider registered with the provided entry DN,
   *          or {@code null} if there is no such trust manager provider
   *          registered with the server.
   */
  public static TrustManagerProvider<?> getTrustManagerProvider(DN providerDN)
  {
    return directoryServer.trustManagerProviders.get(providerDN);
  }

  /**
   * Registers the provided trust manager provider with the Directory Server.
   *
   * @param  providerDN  The DN with which to register the trust manager
   *                     provider.
   * @param  provider    The trust manager provider to register with the server.
   */
  public static void registerTrustManagerProvider(DN providerDN, TrustManagerProvider<?> provider)
  {
    directoryServer.trustManagerProviders.put(providerDN, provider);
  }

  /**
   * Deregisters the specified trust manager provider with the Directory Server.
   *
   * @param  providerDN  The DN with which the trust manager provider is
   *                     registered.
   */
  public static void deregisterTrustManagerProvider(DN providerDN)
  {
    directoryServer.trustManagerProviders.remove(providerDN);
  }

  /**
   * Retrieves the certificate mapper registered with the provided entry DN.
   *
   * @param  mapperDN  The DN with which the certificate mapper is registered.
   *
   * @return  The certificate mapper registered with the provided entry DN, or
   *          {@code null} if there is no such certificate mapper registered
   *          with the server.
   */
  public static CertificateMapper<?> getCertificateMapper(DN mapperDN)
  {
    return directoryServer.certificateMappers.get(mapperDN);
  }

  /**
   * Registers the provided certificate mapper with the Directory Server.
   *
   * @param  mapperDN  The DN with which to register the certificate mapper.
   * @param  mapper    The certificate mapper to register with the server.
   */
  public static void registerCertificateMapper(DN mapperDN, CertificateMapper<?> mapper)
  {
    directoryServer.certificateMappers.put(mapperDN, mapper);
  }

  /**
   * Deregisters the specified certificate mapper with the Directory Server.
   *
   * @param  mapperDN  The DN with which the certificate mapper is registered.
   */
  public static void deregisterCertificateMapper(DN mapperDN)
  {
    directoryServer.certificateMappers.remove(mapperDN);
  }

  /**
   * Retrieves the set of privileges that should automatically be granted to
   * root users when they authenticate.
   *
   * @return  The set of privileges that should automatically be granted to root
   *          users when they authenticate.
   */
  public static Set<Privilege> getRootPrivileges()
  {
    return directoryServer.rootDNConfigManager.getRootPrivileges();
  }

  /**
   * Retrieves the DNs for the root users configured in the Directory Server.
   * Note that this set should only contain the actual DNs for the root users
   * and not any alternate DNs.  Also, the contents of the returned set must not
   * be altered by the caller.
   *
   * @return  The DNs for the root users configured in the Directory Server.
   */
  public static Set<DN> getRootDNs()
  {
    return directoryServer.rootDNs;
  }

  /**
   * Indicates whether the provided DN is the DN for one of the root users
   * configured in the Directory Server.
   *
   * @param  userDN  The user DN for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided user DN is a Directory Server
   *          root DN, or <CODE>false</CODE> if not.
   */
  public static boolean isRootDN(DN userDN)
  {
    return directoryServer.rootDNs.contains(userDN);
  }

  /**
   * Registers the provided root DN with the Directory Server.
   *
   * @param  rootDN  The root DN to register with the Directory Server.
   */
  public static void registerRootDN(DN rootDN)
  {
    directoryServer.rootDNs.add(rootDN);
  }

  /**
   * Deregisters the provided root DN with the Directory Server.  This will have
   * no effect if the provided DN is not registered as a root DN.
   *
   * @param  rootDN  The root DN to deregister.
   */
  public static void deregisterRootDN(DN rootDN)
  {
    directoryServer.rootDNs.remove(rootDN);
  }

  /**
   * Retrieves the real entry DN for the root user with the provided alternate
   * bind DN.
   *
   * @param  alternateRootBindDN  The alternate root bind DN for which to
   *                              retrieve the real entry DN.
   *
   * @return  The real entry DN for the root user with the provided alternate
   *          bind DN, or {@code null} if no such mapping has been defined.
   */
  public static DN getActualRootBindDN(DN alternateRootBindDN)
  {
    return directoryServer.alternateRootBindDNs.get(alternateRootBindDN);
  }

  /**
   * Registers an alternate root bind DN using the provided information.
   *
   * @param  actualRootEntryDN    The actual DN for the root user's entry.
   * @param  alternateRootBindDN  The alternate DN that should be interpreted as
   *                              if it were the provided actual root entry DN.
   *
   * @throws  DirectoryException  If the provided alternate bind DN is already
   *                              in use for another root user.
   */
  public static void registerAlternateRootDN(DN actualRootEntryDN,
                                             DN alternateRootBindDN)
         throws DirectoryException
  {
    DN existingRootEntryDN =
         directoryServer.alternateRootBindDNs.putIfAbsent(alternateRootBindDN,
                                                          actualRootEntryDN);
    if (existingRootEntryDN != null
        && !existingRootEntryDN.equals(actualRootEntryDN))
    {
      LocalizableMessage message = ERR_CANNOT_REGISTER_DUPLICATE_ALTERNATE_ROOT_BIND_DN.
          get(alternateRootBindDN, existingRootEntryDN);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }
  }

  /**
   * Deregisters the provided alternate root bind DN from the server.  This will
   * have no effect if there was no mapping defined for the provided alternate
   * root bind DN.
   *
   * @param  alternateRootBindDN  The alternate root bind DN to be deregistered.
   *
   * @return  The actual root entry DN to which the provided alternate bind DN
   *          was mapped, or {@code null} if there was no mapping for the
   *          provided DN.
   */
  public static DN deregisterAlternateRootBindDN(DN alternateRootBindDN)
  {
    return directoryServer.alternateRootBindDNs.remove(alternateRootBindDN);
  }

  /**
   * Retrieves the DN of the entry containing the server schema definitions.
   *
   * @return  The DN of the entry containing the server schema definitions, or
   *          {@code null} if none has been defined (e.g., if no schema
   *          backend has been configured).
   */
  public static DN getSchemaDN()
  {
    return directoryServer.schemaDN;
  }

  /**
   * Specifies the DN of the entry containing the server schema definitions.
   *
   * @param  schemaDN  The DN of the entry containing the server schema
   *                   definitions.
   */
  public static void setSchemaDN(DN schemaDN)
  {
    directoryServer.schemaDN = schemaDN;
  }

  /**
   * Retrieves the entry with the requested DN. It will first determine which backend should be used
   * for this DN and will then use that backend to retrieve the entry. The caller is not required to
   * hold any locks on the specified DN.
   *
   * @param entryDN
   *          The DN of the entry to retrieve.
   * @return The requested entry, or {@code null} if it does not exist.
   * @throws DirectoryException
   *           If a problem occurs while attempting to retrieve the entry.
   */
  public static Entry getEntry(DN entryDN) throws DirectoryException
  {
    if (entryDN.isRootDN())
    {
      return directoryServer.backendConfigManager.getRootDSEBackend().getRootDSE();
    }
    final LocalBackend<?> backend = directoryServer.backendConfigManager.findLocalBackendForEntry(entryDN);
    return backend != null ? backend.getEntry(entryDN) : null;
  }

  /**
   * Indicates whether the specified entry exists in the Directory Server.  The
   * caller is not required to hold any locks when invoking this method.
   *
   * @param  entryDN  The DN of the entry for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the specified entry exists in one of the
   *          backends, or <CODE>false</CODE> if it does not.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to
   *                              make the determination.
   */
  public static boolean entryExists(DN entryDN)
         throws DirectoryException
  {
    // If the entry is the root DSE, then it will always exist.
    if (entryDN.isRootDN())
    {
      return true;
    }

    // Ask the appropriate backend if the entry exists.
    // If it is not appropriate for any backend, then return false.
    LocalBackend<?> backend = directoryServer.backendConfigManager.findLocalBackendForEntry(entryDN);
    return backend != null && backend.entryExists(entryDN);
  }

  /**
   * Retrieves the set of supported controls registered with the Directory
   * Server.
   *
   * @return  The set of supported controls registered with the Directory
   *          Server.
   */
  public static TreeSet<String> getSupportedControls()
  {
    TreeSet<String> controls = new TreeSet<>(directoryServer.supportedControls);
    for (Backend<?> backend : directoryServer.backendConfigManager.getAllBackends())
    {
      controls.addAll(backend.getSupportedControls());
    }
    return controls;
  }


  /**
   * Indicates whether the specified OID is registered with the Directory Server
   * as a supported control.
   *
   * @param  controlOID  The OID of the control for which to make the
   *                     determination.
   *
   * @return  <CODE>true</CODE> if the specified OID is registered with the
   *          server as a supported control, or <CODE>false</CODE> if not.
   */
  public static boolean isSupportedControl(String controlOID)
  {
    return getSupportedControls().contains(controlOID);
  }

  /**
   * Registers the provided OID as a supported control for the Directory Server.
   * This will have no effect if the specified control OID is already present in
   * the list of supported controls.
   *
   * @param  controlOID  The OID of the control to register as a supported
   *                     control.
   */
  public static void registerSupportedControl(String controlOID)
  {
    synchronized (directoryServer.supportedControls)
    {
      directoryServer.supportedControls.add(controlOID);
    }
  }

  /**
   * Deregisters the provided OID as a supported control for the Directory
   * Server.  This will have no effect if the specified control OID is not
   * present in the list of supported controls.
   *
   * @param  controlOID  The OID of the control to deregister as a supported
   *                     control.
   */
  public static void deregisterSupportedControl(String controlOID)
  {
    synchronized (directoryServer.supportedControls)
    {
      directoryServer.supportedControls.remove(controlOID);
    }
  }

  /**
   * Retrieves the set of supported features registered with the Directory
   * Server.
   *
   * @return  The set of supported features registered with the Directory
   *          Server.
   */
  public static TreeSet<String> getSupportedFeatures()
  {
    TreeSet<String> features = new TreeSet<>(directoryServer.supportedFeatures);
    for (Backend<?> backend : directoryServer.backendConfigManager.getAllBackends())
    {
      features.addAll(backend.getSupportedFeatures());
    }
    return features;
  }

  /**
   * Indicates whether the specified OID is registered with the Directory Server
   * as a supported feature.
   *
   * @param  featureOID  The OID of the feature for which to make the
   *                     determination.
   *
   * @return  <CODE>true</CODE> if the specified OID is registered with the
   *          server as a supported feature, or <CODE>false</CODE> if not.
   */
  public static boolean isSupportedFeature(String featureOID)
  {
    return getSupportedFeatures().contains(featureOID);
  }

  /**
   * Registers the provided OID as a supported feature for the Directory Server.
   * This will have no effect if the specified feature OID is already present in
   * the list of supported features.
   *
   * @param  featureOID  The OID of the feature to register as a supported
   *                     feature.
   */
  public static void registerSupportedFeature(String featureOID)
  {
    synchronized (directoryServer.supportedFeatures)
    {
      directoryServer.supportedFeatures.add(featureOID);
    }
  }

  /**
   * Deregisters the provided OID as a supported feature for the Directory
   * Server.  This will have no effect if the specified feature OID is not
   * present in the list of supported features.
   *
   * @param  featureOID  The OID of the feature to deregister as a supported
   *                     feature.
   */
  public static void deregisterSupportedFeature(String featureOID)
  {
    synchronized (directoryServer.supportedFeatures)
    {
      directoryServer.supportedFeatures.remove(featureOID);
    }
  }

  /**
   * Retrieves the set of extended operations that may be processed by the
   * Directory Server.
   *
   * @return  The set of extended operations that may be processed by the
   *         Directory Server.
   */
  public static Set<String> getSupportedExtensions()
  {
    return directoryServer.extendedOperationHandlers.keySet();
  }

  /**
   * Retrieves the handler for the extended operation for the provided OID.
   *
   * @param  oid  The OID of the extended operation to retrieve.
   *
   * @return  The handler for the specified extended operation, or
   *          {@code null} if there is none.
   */
  public static ExtendedOperationHandler<?> getExtendedOperationHandler(String oid)
  {
    return directoryServer.extendedOperationHandlers.get(oid);
  }

  /**
   * Registers the provided extended operation handler with the Directory
   * Server.
   *
   * @param  oid      The OID for the extended operation to register.
   * @param  handler  The extended operation handler to register with the
   *                  Directory Server.
   */
  public static void registerSupportedExtension(String oid, ExtendedOperationHandler<?> handler)
  {
    directoryServer.extendedOperationHandlers.put(toLowerCase(oid), handler);
  }

  /**
   * Deregisters the provided extended operation handler with the Directory
   * Server.
   *
   * @param  oid  The OID for the extended operation to deregister.
   */
  public static void deregisterSupportedExtension(String oid)
  {
    directoryServer.extendedOperationHandlers.remove(toLowerCase(oid));
  }

  /**
   * Retrieves the set of SASL mechanisms that are supported by the Directory
   * Server.
   *
   * @return  The set of SASL mechanisms that are supported by the Directory
   *          Server.
   */
  public static Set<String> getSupportedSASLMechanisms()
  {
    return directoryServer.saslMechanismHandlers.keySet();
  }

  /**
   * Retrieves the handler for the specified SASL mechanism.
   *
   * @param  name  The name of the SASL mechanism to retrieve.
   *
   * @return  The handler for the specified SASL mechanism, or {@code null}
   *          if there is none.
   */
  public static SASLMechanismHandler<?> getSASLMechanismHandler(String name)
  {
    return directoryServer.saslMechanismHandlers.get(name);
  }

  /**
   * Registers the provided SASL mechanism handler with the Directory Server.
   *
   * @param  name     The name of the SASL mechanism to be registered.
   * @param  handler  The SASL mechanism handler to register with the Directory
   *                  Server.
   */
  public static void registerSASLMechanismHandler(String name, SASLMechanismHandler<?> handler)
  {
    // FIXME -- Should we force this name to be lowercase?  If so, then will
    // that cause the lower name to be used in the root DSE?
    directoryServer.saslMechanismHandlers.put(name, handler);
  }

  /**
   * Deregisters the provided SASL mechanism handler with the Directory Server.
   *
   * @param  name  The name of the SASL mechanism to be deregistered.
   */
  public static void deregisterSASLMechanismHandler(String name)
  {
    // FIXME -- Should we force this name to be lowercase?
    directoryServer.saslMechanismHandlers.remove(name);
  }

  /**
   * Retrieves the supported LDAP versions for the Directory Server.
   *
   * @return  The supported LDAP versions for the Directory Server.
   */
  public static Set<Integer> getSupportedLDAPVersions()
  {
    return directoryServer.supportedLDAPVersions.keySet();
  }

  /**
   * Registers the provided LDAP protocol version as supported within the
   * Directory Server.
   *
   * @param  supportedLDAPVersion  The LDAP protocol version to register as
   *                               supported.
   * @param  connectionHandler     The connection handler that supports the
   *                               provided LDAP version.  Note that multiple
   *                               connection handlers can provide support for
   *                               the same LDAP versions.
   */
  public static synchronized void registerSupportedLDAPVersion(
                                       int supportedLDAPVersion,
                                       ConnectionHandler<?> connectionHandler)
  {
    List<ConnectionHandler<?>> handlers = directoryServer.supportedLDAPVersions.get(supportedLDAPVersion);
    if (handlers == null)
    {
      handlers = new LinkedList<>();
      handlers.add(connectionHandler);
      directoryServer.supportedLDAPVersions.put(supportedLDAPVersion, handlers);
    }
    else if (!handlers.contains(connectionHandler))
    {
      handlers.add(connectionHandler);
    }
  }

  /**
   * Deregisters the provided LDAP protocol version as supported within the
   * Directory Server.
   *
   * @param  supportedLDAPVersion  The LDAP protocol version to deregister.
   * @param  connectionHandler     The connection handler that no longer
   *                               supports the provided LDAP version.
   */
  public static synchronized void deregisterSupportedLDAPVersion(
                                       int supportedLDAPVersion,
      ConnectionHandler<?> connectionHandler)
  {
    List<ConnectionHandler<?>> handlers = directoryServer.supportedLDAPVersions.get(supportedLDAPVersion);
    if (handlers != null)
    {
      handlers.remove(connectionHandler);
      if (handlers.isEmpty())
      {
        directoryServer.supportedLDAPVersions.remove(supportedLDAPVersion);
      }
    }
  }

  /**
   * Retrieves the Directory Server identity mapper whose configuration resides
   * in the specified configuration entry.
   *
   * @param  configEntryDN  The DN of the configuration entry for the identity
   *                        mapper to retrieve.
   *
   * @return  The requested identity mapper, or {@code null} if the
   *          provided entry DN is not associated with an active identity
   *          mapper.
   */
  public static IdentityMapper<?> getIdentityMapper(DN configEntryDN)
  {
    return directoryServer.identityMappers.get(configEntryDN);
  }

  /**
   * Registers the provided identity mapper for use with the Directory Server.
   *
   * @param  configEntryDN   The DN of the configuration entry in which the
   *                         identity mapper definition resides.
   * @param  identityMapper  The identity mapper to be registered.
   */
  public static void registerIdentityMapper(DN configEntryDN, IdentityMapper<?> identityMapper)
  {
    directoryServer.identityMappers.put(configEntryDN, identityMapper);
  }

  /**
   * Deregisters the provided identity mapper for use with the Directory Server.
   *
   * @param  configEntryDN  The DN of the configuration entry in which the
   *                        identity mapper definition resides.
   */
  public static void deregisterIdentityMapper(DN configEntryDN)
  {
    directoryServer.identityMappers.remove(configEntryDN);
  }

  /**
   * Retrieves the identity mapper that should be used to resolve authorization
   * IDs contained in proxied authorization V2 controls.
   *
   * @return  The identity mapper that should be used to resolve authorization
   *          IDs contained in proxied authorization V2 controls, or
   *          {@code null} if none is defined.
   */
  public static IdentityMapper<?> getProxiedAuthorizationIdentityMapper()
  {
    DN dnMapper = getCoreConfigManager().getProxiedAuthorizationIdentityMapperDN();
    return dnMapper != null ? directoryServer.identityMappers.get(dnMapper) : null;
  }

  /**
   * Retrieves the set of connection handlers configured in the Directory
   * Server.  The returned list must not be altered.
   *
   * @return  The set of connection handlers configured in the Directory Server.
   */
  public static List<ConnectionHandler<?>> getConnectionHandlers()
  {
    return directoryServer.connectionHandlers;
  }

  /**
   * Registers the provided connection handler with the Directory Server.
   *
   * @param  handler  The connection handler to register with the Directory
   *                  Server.
   */
  public static void registerConnectionHandler(ConnectionHandler<? extends ConnectionHandlerCfg> handler)
  {
    synchronized (directoryServer.connectionHandlers)
    {
      directoryServer.connectionHandlers.add(handler);

      ConnectionHandlerMonitor monitor = new ConnectionHandlerMonitor(handler);
      monitor.initializeMonitorProvider(null);
      handler.setConnectionHandlerMonitor(monitor);
      registerMonitorProvider(monitor);
    }
  }

  /**
   * Deregisters the provided connection handler with the Directory Server.
   *
   * @param  handler  The connection handler to deregister with the Directory
   *                  Server.
   */
  public static void deregisterConnectionHandler(ConnectionHandler<?> handler)
  {
    synchronized (directoryServer.connectionHandlers)
    {
      directoryServer.connectionHandlers.remove(handler);

      ConnectionHandlerMonitor monitor = handler.getConnectionHandlerMonitor();
      if (monitor != null)
      {
        deregisterMonitorProvider(monitor);
        monitor.finalizeMonitorProvider();
        handler.setConnectionHandlerMonitor(null);
      }
    }
  }

  /**
   * Starts the connection handlers defined in the Directory Server
   * Configuration.
   *
   * @throws  ConfigException If there are more than one connection handlers
   *                          using the same host port or no connection handler
   *                          are enabled or we could not bind to any of the
   *                          listeners.
   */
  private void startConnectionHandlers() throws ConfigException
  {
    Set<HostPort> usedListeners = new LinkedHashSet<>();
    Set<LocalizableMessage> errorMessages = new LinkedHashSet<>();
    // Check that the port specified in the connection handlers is available.
    for (ConnectionHandler<?> c : connectionHandlers)
    {
      for (HostPort listener : c.getListeners())
      {
        if (!usedListeners.add(listener))
        {
          // The port was already specified: this is a configuration error,
          // log a message.
          LocalizableMessage message = ERR_HOST_PORT_ALREADY_SPECIFIED.get(c.getConnectionHandlerName(), listener);
          logger.error(message);
          errorMessages.add(message);
        }
      }
    }

    if (!errorMessages.isEmpty())
    {
      throw new ConfigException(ERR_ERROR_STARTING_CONNECTION_HANDLERS.get());
    }

    // If there are no connection handlers log a message.
    if (connectionHandlers.isEmpty())
    {
      logger.error(ERR_NOT_AVAILABLE_CONNECTION_HANDLERS);
      throw new ConfigException(ERR_ERROR_STARTING_CONNECTION_HANDLERS.get());
    }

    // At this point, we should be ready to go.
    for (ConnectionHandler<?> handler : connectionHandlers)
    {
      handler.start();
    }
  }

  /**
   * Retrieves a reference to the Directory Server work queue.
   *
   * @return  A reference to the Directory Server work queue.
   */
  public static WorkQueue<?> getWorkQueue()
  {
    return directoryServer.workQueue;
  }

  /**
   * Runs all the necessary checks prior to adding an operation to the work
   * queue. It throws a DirectoryException if one of the check fails.
   *
   * @param operation
   *          The operation to be added to the work queue.
   * @param isAllowedInLockDownMode
   *          Flag to indicate if the request can be added to the work queue regardless
   *          of the server's lock down mode.
   * @throws DirectoryException
   *           If a check failed preventing the operation from being added to
   *           the queue
   */
  public static void checkCanEnqueueRequest(Operation operation, boolean isAllowedInLockDownMode)
         throws DirectoryException
  {
    ClientConnection clientConnection = operation.getClientConnection();
    //Reject or accept the unauthenticated requests based on the configuration settings.
    if (!clientConnection.getAuthenticationInfo().isAuthenticated() &&
        (getCoreConfigManager().isRejectUnauthenticatedRequests() ||
        (directoryServer.lockdownMode && !isAllowedInLockDownMode)))
    {
      switch(operation.getOperationType())
      {
        case ADD:
        case COMPARE:
        case DELETE:
        case SEARCH:
        case MODIFY:
        case MODIFY_DN:
          LocalizableMessage message = directoryServer.lockdownMode
              ? NOTE_REJECT_OPERATION_IN_LOCKDOWN_MODE.get()
              : ERR_REJECT_UNAUTHENTICATED_OPERATION.get();
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);

        case EXTENDED:
         ExtendedOperationBasis extOp = (ExtendedOperationBasis) operation;
         String requestOID = extOp.getRequestOID();
         if (!OID_START_TLS_REQUEST.equals(requestOID) 
             && !OID_GET_SYMMETRIC_KEY_EXTENDED_OP.equals(requestOID))
         {
           // Clients must be allowed to enable TLS before authenticating.

           // Authentication is not required for the get symmetric key request as it depends on out of band trust
           // negotiation. See OPENDJ-3445.

           message = directoryServer.lockdownMode
               ? NOTE_REJECT_OPERATION_IN_LOCKDOWN_MODE.get()
               : ERR_REJECT_UNAUTHENTICATED_OPERATION.get();
           throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
         }
         break;

      }
    }

    // If the associated user is required to change their password before
    // continuing, then make sure the associated operation is one that could
    // result in the password being changed.  If not, then reject it.
    if (clientConnection.mustChangePassword())
    {
      switch (operation.getOperationType())
      {
        case ADD:
        case COMPARE:
        case DELETE:
        case MODIFY_DN:
        case SEARCH:
          // See if the request included the password policy request control.
          // If it did, then add a corresponding response control.
          for (Control c : operation.getRequestControls())
          {
            if (OID_PASSWORD_POLICY_CONTROL.equals(c.getOID()))
            {
              operation.addResponseControl(new PasswordPolicyResponseControl(
                   null, 0, PasswordPolicyErrorType.CHANGE_AFTER_RESET));
              break;
            }
          }

          DN user = clientConnection.getAuthenticationInfo()
              .getAuthorizationDN();
          LocalizableMessage message = ERR_ENQUEUE_MUST_CHANGE_PASSWORD
              .get(user != null ? user : "anonymous");
          throw new DirectoryException(
                  ResultCode.CONSTRAINT_VIOLATION, message);

        case EXTENDED:
          // We will only allow the password modify and StartTLS extended
          // operations.
          ExtendedOperationBasis extOp = (ExtendedOperationBasis) operation;
          String            requestOID = extOp.getRequestOID();
          if (!OID_PASSWORD_MODIFY_REQUEST.equals(requestOID)
              && !OID_START_TLS_REQUEST.equals(requestOID))
          {
            // See if the request included the password policy request control.
            // If it did, then add a corresponding response control.
            for (Control c : operation.getRequestControls())
            {
              if (OID_PASSWORD_POLICY_CONTROL.equals(c.getOID()))
              {
                operation.addResponseControl(new PasswordPolicyResponseControl(
                     null, 0, PasswordPolicyErrorType.CHANGE_AFTER_RESET));
                break;
              }
            }

            user = clientConnection.getAuthenticationInfo().getAuthorizationDN();
            message = ERR_ENQUEUE_MUST_CHANGE_PASSWORD.get(user != null ? user : "anonymous");
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
          }

          break;

          // Bind, unbind, and abandon will always be allowed.

          // Modify may or may not be allowed, but we'll leave that
          // determination up to the modify operation itself.
      }
    }
  }

  /**
   * Adds the provided operation to the work queue so that it will be processed
   * by one of the worker threads.
   *
   * @param  operation  The operation to be added to the work queue.
   *
   * @throws  DirectoryException  If a problem prevents the operation from being
   *                              added to the queue (e.g., the queue is full).
   */
  public static void enqueueRequest(Operation operation)
      throws DirectoryException
  {
    checkCanEnqueueRequest(operation, false);
    directoryServer.workQueue.submitOperation(operation);
  }

  /**
   * Tries to add the provided operation to the work queue if not full so that
   * it will be processed by one of the worker threads.
   *
   * @param operation
   *          The operation to be added to the work queue.
   * @return true if the operation could be enqueued, false otherwise
   * @throws DirectoryException
   *           If a problem prevents the operation from being added to the queue
   *           (e.g., the queue is full).
   */
  public static boolean tryEnqueueRequest(Operation operation)
      throws DirectoryException
  {
    checkCanEnqueueRequest(operation, false);
    return directoryServer.workQueue.trySubmitOperation(operation);
  }

  /**
   * Retrieves the set of synchronization providers that have been registered
   * with the Directory Server.
   *
   * @return  The set of synchronization providers that have been registered
   *          with the Directory Server.
   */
  public static List<SynchronizationProvider<SynchronizationProviderCfg>>
      getSynchronizationProviders()
  {
    return directoryServer.synchronizationProviders;
  }

  /**
   * Registers the provided synchronization provider with the Directory Server.
   *
   * @param  provider  The synchronization provider to register.
   */
  public static void registerSynchronizationProvider(
      SynchronizationProvider<SynchronizationProviderCfg> provider)
  {
    directoryServer.synchronizationProviders.add(provider);

    provider.completeSynchronizationProvider();
  }

  /**
   * Deregisters the provided synchronization provider with the Directory Server.
   *
   * @param  provider  The synchronization provider to deregister.
   */
  public static void deregisterSynchronizationProvider(SynchronizationProvider<?> provider)
  {
    directoryServer.synchronizationProviders.remove(provider);
  }

  /**
   * Returns the core configuration manager.
   *
   * @return the core config manager
   */
  public static CoreConfigManager getCoreConfigManager()
  {
    return directoryServer.coreConfigManager;
  }

  /**
   * Indicates whether the specified privilege is disabled.
   *
   * @param  privilege  The privilege for which to make the determination.
   *
   * @return  {@code true} if the specified privilege is disabled, or
   *          {@code false} if not.
   */
  public static boolean isDisabled(Privilege privilege)
  {
    return getCoreConfigManager().getDisabledPrivileges().contains(privilege);
  }

  /**
   * Retrieves the maximum length of time in milliseconds that client
   * connections should be allowed to remain idle without being disconnected.
   *
   * @return  The maximum length of time in milliseconds that client connections
   *          should be allowed to remain idle without being disconnected.
   */
  public static long getIdleTimeLimit()
  {
    return directoryServer.idleTimeLimit;
  }

  /**
   * Specifies the maximum length of time in milliseconds that client
   * connections should be allowed to remain idle without being disconnected.
   *
   * @param  idleTimeLimit  The maximum length of time in milliseconds that
   *                        client connections should be allowed to remain idle
   *                        without being disconnected.
   */
  public static void setIdleTimeLimit(long idleTimeLimit)
  {
    directoryServer.idleTimeLimit = idleTimeLimit;
  }

  /**
   * Registers the provided backup task listener with the Directory Server.
   *
   * @param  listener  The backup task listener to register with the Directory
   *                   Server.
   */
  public static void registerBackupTaskListener(BackupTaskListener listener)
  {
    directoryServer.backupTaskListeners.addIfAbsent(listener);
  }

  /**
   * Deregisters the provided backup task listener with the Directory Server.
   *
   * @param  listener  The backup task listener to deregister with the Directory
   *                   Server.
   */
  public static void deregisterBackupTaskListener(BackupTaskListener listener)
  {
    directoryServer.backupTaskListeners.remove(listener);
  }

  /**
   * Notifies the registered backup task listeners that the server will be
   * beginning a backup task with the provided information.
   *
   * @param  backend  The backend in which the backup is to be performed.
   * @param  config   The configuration for the backup to be performed.
   */
  public static void notifyBackupBeginning(LocalBackend<?> backend, BackupConfig config)
  {
    for (BackupTaskListener listener : directoryServer.backupTaskListeners)
    {
      try
      {
        listener.processBackupBegin(backend, config);
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
  }

  /**
   * Notifies the registered backup task listeners that the server has completed
   * processing on a backup task with the provided information.
   *
   * @param  backend     The backend in which the backup was performed.
   * @param  config      The configuration for the backup that was performed.
   * @param  successful  Indicates whether the backup completed successfully.
   */
  public static void notifyBackupEnded(LocalBackend<?> backend, BackupConfig config, boolean successful)
  {
    for (BackupTaskListener listener : directoryServer.backupTaskListeners)
    {
      try
      {
        listener.processBackupEnd(backend, config, successful);
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
  }

  /**
   * Registers the provided restore task listener with the Directory Server.
   *
   * @param  listener  The restore task listener to register with the Directory
   *                   Server.
   */
  public static void registerRestoreTaskListener(RestoreTaskListener listener)
  {
    directoryServer.restoreTaskListeners.addIfAbsent(listener);
  }

  /**
   * Deregisters the provided restore task listener with the Directory Server.
   *
   * @param  listener  The restore task listener to deregister with the
   *                   Directory Server.
   */
  public static void deregisterRestoreTaskListener(RestoreTaskListener listener)
  {
    directoryServer.restoreTaskListeners.remove(listener);
  }

  /**
   * Notifies the registered restore task listeners that the server will be
   * beginning a restore task with the provided information.
   *
   * @param  backend  The backend in which the restore is to be performed.
   * @param  config   The configuration for the restore to be performed.
   */
  public static void notifyRestoreBeginning(LocalBackend<?> backend, RestoreConfig config)
  {
    for (RestoreTaskListener listener : directoryServer.restoreTaskListeners)
    {
      try
      {
        listener.processRestoreBegin(backend, config);
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
  }

  /**
   * Notifies the registered restore task listeners that the server has
   * completed processing on a restore task with the provided information.
   *
   * @param  backend     The backend in which the restore was performed.
   * @param  config      The configuration for the restore that was performed.
   * @param  successful  Indicates whether the restore completed successfully.
   */
  public static void notifyRestoreEnded(LocalBackend<?> backend, RestoreConfig config, boolean successful)
  {
    for (RestoreTaskListener listener : directoryServer.restoreTaskListeners)
    {
      try
      {
        listener.processRestoreEnd(backend, config, successful);
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
  }

  /**
   * Registers the provided LDIF export task listener with the Directory Server.
   *
   * @param  listener  The export task listener to register with the Directory
   *                   Server.
   */
  public static void registerExportTaskListener(ExportTaskListener listener)
  {
    directoryServer.exportTaskListeners.addIfAbsent(listener);
  }

  /**
   * Deregisters the provided LDIF export task listener with the Directory
   * Server.
   *
   * @param  listener  The export task listener to deregister with the Directory
   *                   Server.
   */
  public static void deregisterExportTaskListener(ExportTaskListener listener)
  {
    directoryServer.exportTaskListeners.remove(listener);
  }

  /**
   * Notifies the registered LDIF export task listeners that the server will be
   * beginning an export task with the provided information.
   *
   * @param  backend  The backend in which the export is to be performed.
   * @param  config   The configuration for the export to be performed.
   */
  public static void notifyExportBeginning(LocalBackend<?> backend, LDIFExportConfig config)
  {
    for (ExportTaskListener listener : directoryServer.exportTaskListeners)
    {
      try
      {
        listener.processExportBegin(backend, config);
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
  }

  /**
   * Notifies the registered LDIF export task listeners that the server has
   * completed processing on an export task with the provided information.
   *
   * @param  backend     The backend in which the export was performed.
   * @param  config      The configuration for the export that was performed.
   * @param  successful  Indicates whether the export completed successfully.
   */
  public static void notifyExportEnded(LocalBackend<?> backend, LDIFExportConfig config, boolean successful)
  {
    for (ExportTaskListener listener : directoryServer.exportTaskListeners)
    {
      try
      {
        listener.processExportEnd(backend, config, successful);
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
  }

  /**
   * Registers the provided LDIF import task listener with the Directory Server.
   *
   * @param  listener  The import task listener to register with the Directory
   *                   Server.
   */
  public static void registerImportTaskListener(ImportTaskListener listener)
  {
    directoryServer.importTaskListeners.addIfAbsent(listener);
  }

  /**
   * Deregisters the provided LDIF import task listener with the Directory
   * Server.
   *
   * @param  listener  The import task listener to deregister with the Directory
   *                   Server.
   */
  public static void deregisterImportTaskListener(ImportTaskListener listener)
  {
    directoryServer.importTaskListeners.remove(listener);
  }

  /**
   * Notifies the registered LDIF import task listeners that the server will be
   * beginning an import task with the provided information.
   *
   * @param  backend  The backend in which the import is to be performed.
   * @param  config   The configuration for the import to be performed.
   */
  public static void notifyImportBeginning(LocalBackend<?> backend, LDIFImportConfig config)
  {
    for (ImportTaskListener listener : directoryServer.importTaskListeners)
    {
      try
      {
        listener.processImportBegin(backend, config);
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
  }

  /**
   * Notifies the registered LDIF import task listeners that the server has
   * completed processing on an import task with the provided information.
   *
   * @param  backend     The backend in which the import was performed.
   * @param  config      The configuration for the import that was performed.
   * @param  successful  Indicates whether the import completed successfully.
   */
  public static void notifyImportEnded(LocalBackend<?> backend, LDIFImportConfig config, boolean successful)
  {
    for (ImportTaskListener listener : directoryServer.importTaskListeners)
    {
      try
      {
        listener.processImportEnd(backend, config, successful);
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
  }

  /**
   * Registers the provided initialization completed listener with the
   * Directory Server so that it will be notified when the server
   * initialization completes.
   *
   * @param  listener  The initialization competed listener to register with
   *                   the Directory Server.
   */
  public static void registerInitializationCompletedListener(
          InitializationCompletedListener listener) {
    directoryServer.initializationCompletedListeners.add(listener);
  }

  /**
   * Deregisters the provided initialization completed listener with the
   * Directory Server.
   *
   * @param  listener  The initialization completed listener to deregister with
   *                   the Directory Server.
   */
  public static void deregisterInitializationCompletedListener(
          InitializationCompletedListener listener) {
    directoryServer.initializationCompletedListeners.remove(listener);
  }

  /**
   * Registers the provided shutdown listener with the Directory Server so that
   * it will be notified when the server shuts down.
   *
   * @param  listener  The shutdown listener to register with the Directory
   *                   Server.
   */
  public static void registerShutdownListener(ServerShutdownListener listener)
  {
    directoryServer.shutdownListeners.add(listener);
  }

  /**
   * Deregisters the provided shutdown listener with the Directory Server.
   *
   * @param  listener  The shutdown listener to deregister with the Directory
   *                   Server.
   */
  public static void deregisterShutdownListener(ServerShutdownListener listener)
  {
    directoryServer.shutdownListeners.remove(listener);
  }

  /**
   * Initiates the Directory Server shutdown process.  Note that once this has
   * started, it should not be interrupted.
   *
   * @param  className  The fully-qualified name of the Java class that
   *                    initiated the shutdown.
   * @param  reason     The human-readable reason that the directory server is
   *                    shutting down.
   */
  public static void shutDown(String className, LocalizableMessage reason)
  {
    synchronized (directoryServer)
    {
      if (directoryServer.shuttingDown)
      {
        // We already know that the server is shutting down, so we don't need to
        // do anything.
        return;
      }

      directoryServer.shuttingDown = true;
    }

    // Send an alert notification that the server is shutting down.
    sendAlertNotification(directoryServer, ALERT_TYPE_SERVER_SHUTDOWN,
        NOTE_SERVER_SHUTDOWN.get(className, reason));

    // Create a shutdown monitor that will watch the rest of the shutdown
    // process to ensure that everything goes smoothly.
    ServerShutdownMonitor shutdownMonitor = new ServerShutdownMonitor();
    shutdownMonitor.start();

    // Shut down the connection handlers.
    for (ConnectionHandler<?> handler : directoryServer.connectionHandlers)
    {
      try
      {
        handler.finalizeConnectionHandler(INFO_CONNHANDLER_CLOSED_BY_SHUTDOWN.get());
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }
    directoryServer.connectionHandlers.clear();

    if (directoryServer.workQueue != null)
    {
      directoryServer.workQueue.finalizeWorkQueue(reason);
      directoryServer.workQueue.waitUntilIdle(ServerShutdownMonitor.WAIT_TIME);
    }

    // shutdown replication
    for (SynchronizationProvider<?> provider : directoryServer.synchronizationProviders)
    {
      provider.finalizeSynchronizationProvider();
    }

    // Call the shutdown plugins, and then finalize all the plugins defined in
    // the server.
    if (directoryServer.pluginConfigManager != null)
    {
      directoryServer.pluginConfigManager.invokeShutdownPlugins(reason);
      directoryServer.pluginConfigManager.finalizePlugins();
    }

    // Deregister the shutdown hook.
    if (directoryServer.shutdownHook != null)
    {
      try
      {
        Runtime.getRuntime().removeShutdownHook(directoryServer.shutdownHook);
      }
      catch (Exception e) {}
    }

    // Notify all the shutdown listeners.
    for (ServerShutdownListener shutdownListener :
         directoryServer.shutdownListeners)
    {
      try
      {
        shutdownListener.processServerShutdown(reason);
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }

    // Shut down all of the alert handlers.
    for (AlertHandler<?> alertHandler : directoryServer.alertHandlers)
    {
      alertHandler.finalizeAlertHandler();
    }

    // Deregister all of the JMX MBeans.
    if (directoryServer.mBeanServer != null)
    {
      Set<?> mBeanSet = directoryServer.mBeanServer.queryMBeans(null, null);
      for (Object o : mBeanSet)
      {
        if (o instanceof DirectoryServerMBean)
        {
          try
          {
            DirectoryServerMBean mBean = (DirectoryServerMBean) o;
            directoryServer.mBeanServer.unregisterMBean(mBean.getObjectName());
          }
          catch (Exception e)
          {
            logger.traceException(e);
          }
        }
      }
    }

    // Finalize all of the SASL mechanism handlers.
    for (SASLMechanismHandler<?> handler : directoryServer.saslMechanismHandlers.values())
    {
      try
      {
        handler.finalizeSASLMechanismHandler();
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }

    // Finalize all of the extended operation handlers.
    for (ExtendedOperationHandler<?> handler : directoryServer.extendedOperationHandlers.values())
    {
      try
      {
        handler.finalizeExtendedOperationHandler();
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }

    // Finalize the password policy map.
    for (DN configEntryDN : directoryServer.authenticationPolicies.keySet())
    {
      DirectoryServer.deregisterAuthenticationPolicy(configEntryDN);
    }

    // Finalize password policies and their config manager.
    if (directoryServer.authenticationPolicyConfigManager != null)
    {
      directoryServer.authenticationPolicyConfigManager
          .finalizeAuthenticationPolicies();
    }

    // Finalize the access control handler
    AccessControlHandler<?> accessControlHandler =
        AccessControlConfigManager.getInstance().getAccessControlHandler();
    if (accessControlHandler != null)
    {
      accessControlHandler.finalizeAccessControlHandler();
    }

    // Perform any necessary cleanup work for the group manager.
    if (directoryServer.groupManager != null)
    {
      directoryServer.groupManager.finalizeGroupManager();
    }

    // Finalize the subentry manager.
    if (directoryServer.subentryManager != null)
    {
      directoryServer.subentryManager.finalizeSubentryManager();
    }

    // Shut down all the other components that may need special handling.
    // NYI

    // Shut down the monitor providers.
    for (MonitorProvider<?> monitor : directoryServer.monitorProviders.values())
    {
      try
      {
        monitor.finalizeMonitorProvider();
      }
      catch (Exception e)
      {
        logger.traceException(e);
      }
    }

    if (directoryServer.backendConfigManager != null) {
    	directoryServer.backendConfigManager.shutdownLocalBackends();
    }

    if (directoryServer.configurationHandler != null) {
      directoryServer.configurationHandler.finalize();
    }

    EntryCache<?> ec = DirectoryServer.getEntryCache();
    if (ec != null)
    {
      ec.finalizeEntryCache();
    }

    if (directoryServer.serviceDiscoveryMechanismConfigManager != null) {
    	directoryServer.serviceDiscoveryMechanismConfigManager.finalize();
    }
    // Release exclusive lock held on server.lock file
    try {
        String serverLockFileName = LockFileManager.getServerLockFileName();
        StringBuilder failureReason = new StringBuilder();
        if (!LockFileManager.releaseLock(serverLockFileName, failureReason)) {
            logger.info(NOTE_SERVER_SHUTDOWN, className, failureReason);
        }
        serverLocked = false;
    } catch (Exception e) {
        logger.traceException(e);
    }

    // Force a new InternalClientConnection to be created on restart.
    InternalConnectionHandler.clearRootClientConnectionAtShutdown();

    // Log a final message indicating that the server is stopped (which should
    // be true for all practical purposes), and then shut down all the error
    // loggers.
    logger.info(NOTE_SERVER_STOPPED);

    AccessLogger.getInstance().removeAllLogPublishers();
    ErrorLogger.getInstance().removeAllLogPublishers();
    DebugLogger.getInstance().removeAllLogPublishers();

    // Now that the loggers are disabled we can shutdown the timer.
    TimeThread.stop();

    // Just in case there's something that isn't shut down properly, wait for
    // the monitor to give the OK to stop.
    shutdownMonitor.waitForMonitor();

    // At this point, the server is no longer running.  We should destroy the
    // handle to the previous instance, but we will want to get a new instance
    // in case the server is to be started again later in the same JVM.  Before
    // doing that, destroy the previous instance.
    DirectoryEnvironmentConfig envConfig = directoryServer.environmentConfig;
    directoryServer.destroy();
    directoryServer = getNewInstance(envConfig);
  }

  /**
   * Destroy key structures in the current Directory Server instance in a manner
   * that can help detect any inappropriate cached references to server
   * components.
   */
  private void destroy()
  {
    isBootstrapped                = false;
    isRunning                     = false;
    lockdownMode                  = true;
    shuttingDown                  = true;

    configFile               = null;
    configurationHandler     = null;
    coreConfigManager        = null;
    compressedSchema         = null;
    cryptoManager            = null;
    entryCache               = null;
    environmentConfig        = null;
    schemaDN                 = null;
    shutdownHook             = null;
    workQueue                = null;

    if (schemaHandler != null)
    {
      schemaHandler.destroy();
      schemaHandler = null;
    }
  }

  /**
   * Causes the Directory Server to perform an in-core restart.  This will
   * cause virtually all components of the Directory Server to shut down, and
   * once that has completed it will be restarted.
   *
   * @param  className  The fully-qualified name of the Java class that
   *                    initiated the shutdown.
   * @param  reason     The human-readable reason that the directory server is
   *                    shutting down.
   */
  public static void restart(String className, LocalizableMessage reason)
  {
    restart(className, reason, directoryServer.environmentConfig);
  }

  /**
   * Causes the Directory Server to perform an in-core restart.  This will
   * cause virtually all components of the Directory Server to shut down, and
   * once that has completed it will be restarted.
   *
   * @param  className  The fully-qualified name of the Java class that
   *                    initiated the shutdown.
   * @param  reason     The human-readable reason that the directory server is
   *                    shutting down.
   * @param  config     The environment configuration to use for the server.
   */
  public static void restart(String className, LocalizableMessage reason,
                             DirectoryEnvironmentConfig config)
  {
    try
    {
      shutDown(className, reason);
      reinitialize(config);
      directoryServer.startServer();
    }
    catch (Exception e)
    {
      System.err.println("ERROR:  Unable to perform an in-core restart:");
      e.printStackTrace();
      System.err.println("Halting the JVM so that it must be manually " +
                         "restarted.");

      Runtime.getRuntime().halt(1);
    }
  }

  /**
   * Reinitializes the server following a shutdown, preparing it for a call to
   * {@code startServer}.
   *
   * @param  config  The environment configuration for the Directory Server.
   *
   * @return  The new Directory Server instance created during the
   *          re-initialization process.
   *
   * @throws  InitializationException  If a problem occurs while trying to
   *                                   initialize the config handler or
   *                                   bootstrap that server.
   */
  public static DirectoryServer reinitialize(DirectoryEnvironmentConfig config)
         throws InitializationException
  {
    // Ensure that the timer thread has started.
    TimeThread.start();

    getNewInstance(config);
    directoryServer.bootstrapServer();
    directoryServer.initializeConfiguration();
    return directoryServer;
  }

  /**
   * Indicates that a new connection has been accepted and increments the
   * associated counters.
   *
   * @param  clientConnection  The client connection that has been established.
   *
   * @return  The connection ID that should be used for this connection, or -1
   *          if the connection has been rejected for some reason (e.g., the
   *          maximum number of concurrent connections have already been
   *          established).
   */
  public static long newConnectionAccepted(ClientConnection clientConnection)
  {
    synchronized (directoryServer.establishedConnections)
    {
      if (directoryServer.lockdownMode)
      {
        InetAddress remoteAddress = clientConnection.getRemoteAddress();
        if (remoteAddress != null && !remoteAddress.isLoopbackAddress())
        {
          return -1;
        }
      }

      final long maxAllowed = getCoreConfigManager().getMaxAllowedConnections();
      if (0 < maxAllowed && maxAllowed <= directoryServer.currentConnections)
      {
        return -1;
      }

      directoryServer.establishedConnections.add(clientConnection);
      directoryServer.currentConnections++;

      if (directoryServer.currentConnections > directoryServer.maxConnections)
      {
        directoryServer.maxConnections = directoryServer.currentConnections;
      }

      return directoryServer.totalConnections++;
    }
  }

  /**
   * Indicates that the specified client connection has been closed.
   *
   * @param  clientConnection  The client connection that has been closed.
   */
  public static void connectionClosed(ClientConnection clientConnection)
  {
    synchronized (directoryServer.establishedConnections)
    {
      directoryServer.establishedConnections.remove(clientConnection);
      directoryServer.currentConnections--;
    }
  }

  /**
   * Retrieves the number of client connections that are currently established.
   *
   * @return  The number of client connections that are currently established.
   */
  public static long getCurrentConnections()
  {
    return directoryServer.currentConnections;
  }

  /**
   * Retrieves the maximum number of client connections that have been
   * established concurrently.
   *
   * @return  The maximum number of client connections that have been
   *          established concurrently.
   */
  public static long getMaxConnections()
  {
    return directoryServer.maxConnections;
  }

  /**
   * Retrieves the total number of client connections that have been established
   * since the Directory Server started.
   *
   * @return  The total number of client connections that have been established
   *          since the Directory Server started.
   */
  public static long getTotalConnections()
  {
    return directoryServer.totalConnections;
  }

  /**
   * Retrieves the full version string for the Directory Server.
   *
   * @return  The full version string for the Directory Server.
   */
  public static String getVersionString()
  {
    return FULL_VERSION_STRING;
  }

  /**
   * Prints out the version string for the Directory Server.
   *
   *
   * @param  outputStream  The output stream to which the version information
   *                       should be written.
   *
   * @throws  IOException  If a problem occurs while attempting to write the
   *                       version information to the provided output stream.
   */
  private static void printVersion(OutputStream outputStream) throws IOException
  {
    outputStream.write(PRINTABLE_VERSION_STRING.getBytes());

    // Print extensions' extra information
    String extensionInformation =
            ConfigurationFramework.getPrintableExtensionInformation(getServerRoot(), getInstanceRoot());
    if ( extensionInformation != null ) {
      outputStream.write(extensionInformation.getBytes());
    }
  }

  /**
   *  Registers a new persistent search by increasing the count
   *  of active persistent searches. After receiving a persistent
   *  search request, a Local or Remote WFE must call this method to
   *  let the core server manage the count of concurrent persistent
   *  searches.
   */
  public static void registerPersistentSearch()
  {
    directoryServer.activePSearches.incrementAndGet();
  }

  /**
   * Deregisters a canceled persistent search.  After a persistent
   * search is canceled, the handler must call this method to let
   * the core server manage the count of concurrent persistent
   *  searches.
   */
  public static void deregisterPersistentSearch()
  {
    directoryServer.activePSearches.decrementAndGet();
  }

  /**
   * Indicates whether a new persistent search is allowed.
   *
   * @return <CODE>true</CODE>if a new persistent search is allowed
   *          or <CODE>false</CODE>f if not.
   */
  public static boolean allowNewPersistentSearch()
  {
    //-1 indicates that there is no limit.
    int max = getCoreConfigManager().getMaxPSearches();
    return max == -1 || directoryServer.activePSearches.get() < max;
  }

  /**
   * Indicates whether the Directory Server is currently configured to operate
   * in the lockdown mode, in which all non-root requests will be rejected and
   * all connection attempts from non-loopback clients will be rejected.
   *
   * @return  {@code true} if the Directory Server is currently configured to
   *          operate in the lockdown mode, or {@code false} if not.
   */
  public static boolean lockdownMode()
  {
    return directoryServer.lockdownMode;
  }

  /**
   * Specifies whether the server should operate in lockdown mode.
   *
   * @param  lockdownMode  Indicates whether the Directory Server should operate
   *                       in lockdown mode.
   */
  public static void setLockdownMode(boolean lockdownMode)
  {
    directoryServer.lockdownMode = lockdownMode;

    if (lockdownMode)
    {
      LocalizableMessage message = WARN_DIRECTORY_SERVER_ENTERING_LOCKDOWN_MODE.get();
      logger.warn(message);

      sendAlertNotification(directoryServer, ALERT_TYPE_ENTERING_LOCKDOWN_MODE,
              message);
    }
    else
    {
      LocalizableMessage message = NOTE_DIRECTORY_SERVER_LEAVING_LOCKDOWN_MODE.get();
      logger.info(message);

      sendAlertNotification(directoryServer, ALERT_TYPE_LEAVING_LOCKDOWN_MODE,
              message);
    }
  }

  /**
   * Retrieves the DN of the configuration entry with which this alert generator
   * is associated.
   *
   * @return  The DN of the configuration entry with which this alert generator
   *          is associated.
   */
  @Override
  public DN getComponentEntryDN()
  {
    try
    {
      if (configurationHandler == null)
      {
        // The config handler hasn't been initialized yet.  Just return the DN
        // of the root DSE.
        return DN.rootDN();
      }

      return configurationHandler.getRootEntry().getName();
    }
    catch (Exception e)
    {
      logger.traceException(e);

      // This could theoretically happen if an alert needs to be sent before the
      // configuration is initialized.  In that case, just return an empty DN.
      return DN.rootDN();
    }
  }

  /**
   * Retrieves the fully-qualified name of the Java class for this alert
   * generator implementation.
   *
   * @return  The fully-qualified name of the Java class for this alert
   *          generator implementation.
   */
  @Override
  public String getClassName()
  {
    return DirectoryServer.class.getName();
  }

  /**
   * Retrieves information about the set of alerts that this generator may
   * produce.  The map returned should be between the notification type for a
   * particular notification and the human-readable description for that
   * notification.  This alert generator must not generate any alerts with types
   * that are not contained in this list.
   *
   * @return  Information about the set of alerts that this generator may
   *          produce.
   */
  @Override
  public Map<String, String> getAlerts()
  {
    Map<String, String> alerts = new LinkedHashMap<>();

    alerts.put(ALERT_TYPE_SERVER_STARTED, ALERT_DESCRIPTION_SERVER_STARTED);
    alerts.put(ALERT_TYPE_SERVER_SHUTDOWN, ALERT_DESCRIPTION_SERVER_SHUTDOWN);
    alerts.put(ALERT_TYPE_ENTERING_LOCKDOWN_MODE,
               ALERT_DESCRIPTION_ENTERING_LOCKDOWN_MODE);
    alerts.put(ALERT_TYPE_LEAVING_LOCKDOWN_MODE,
               ALERT_DESCRIPTION_LEAVING_LOCKDOWN_MODE);

    return alerts;
  }

  /**
   * Indicates whether the server is currently in the process of shutting down.
   * @return <CODE>true</CODE> if this server is currently in the process of
   * shutting down and <CODE>false</CODE> otherwise.
   */
  public boolean isShuttingDown()
  {
    return shuttingDown;
  }

  /**
   * Parses the provided command-line arguments and uses that information to
   * bootstrap and start the Directory Server.
   *
   * @param  args  The command-line arguments provided to this program.
   */
  public static void main(String[] args)
  {
    // Define the arguments that may be provided to the server.
    final BooleanArgument displayUsage;
    BooleanArgument checkStartability      = null;
    BooleanArgument quietMode              = null;
    IntegerArgument timeout                = null;
    BooleanArgument fullVersion            = null;
    BooleanArgument noDetach               = null;
    BooleanArgument systemInfo             = null;
    BooleanArgument useLastKnownGoodConfig = null;
    StringArgument  configFile             = null;

    // Create the command-line argument parser for use with this program.
    ArgumentParser argParser =
         new ArgumentParser("org.opends.server.core.DirectoryServer",
                            DirectoryServer.toolDescription, false);
    argParser.setShortToolDescription(REF_SHORT_DESC_START_DS.get());

    // Initialize all the command-line argument types and register them with the parser.
    try
    {
      BooleanArgument.builder("windowsNetStart")
              .description(INFO_DSCORE_DESCRIPTION_WINDOWS_NET_START.get())
              .hidden()
              .buildAndAddToParser(argParser);
      configFile =
              StringArgument.builder("configFile")
                      .shortIdentifier('f')
                      .description(INFO_DSCORE_DESCRIPTION_CONFIG_FILE.get())
                      .hidden()
                      .required()
                      .valuePlaceholder(INFO_CONFIGFILE_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      checkStartability =
              BooleanArgument.builder("checkStartability")
                      .description(INFO_DSCORE_DESCRIPTION_CHECK_STARTABILITY.get())
                      .hidden()
                      .buildAndAddToParser(argParser);
      fullVersion =
              BooleanArgument.builder("fullVersion")
                      .shortIdentifier('F')
                      .description(INFO_DSCORE_DESCRIPTION_FULLVERSION.get())
                      .hidden()
                      .buildAndAddToParser(argParser);
      systemInfo =
              BooleanArgument.builder("systemInfo")
                      .shortIdentifier('s')
                      .description(INFO_DSCORE_DESCRIPTION_SYSINFO.get())
                      .buildAndAddToParser(argParser);
      useLastKnownGoodConfig =
              BooleanArgument.builder("useLastKnownGoodConfig")
                      .shortIdentifier('L')
                      .description(INFO_DSCORE_DESCRIPTION_LASTKNOWNGOODCFG.get())
                      .buildAndAddToParser(argParser);
      noDetach =
              BooleanArgument.builder("nodetach")
                      .shortIdentifier('N')
                      .description(INFO_DSCORE_DESCRIPTION_NODETACH.get())
                      .buildAndAddToParser(argParser);

      quietMode = quietArgument();
      argParser.addArgument(quietMode);

      // Not used in this class, but required by the start-ds script (see issue #3814)
      timeout =
              IntegerArgument.builder("timeout")
                      .shortIdentifier('t')
                      .description(INFO_DSCORE_DESCRIPTION_TIMEOUT.get())
                      .required()
                      .lowerBound(0)
                      .defaultValue(DEFAULT_TIMEOUT)
                      .valuePlaceholder(INFO_SECONDS_PLACEHOLDER.get())
                      .buildAndAddToParser(argParser);
      displayUsage = showUsageArgument();
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage);
      argParser.setVersionHandler(new DirectoryServerVersionHandler());
    }
    catch (ArgumentException ae)
    {
      LocalizableMessage message = ERR_DSCORE_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
      System.err.println(message);
      System.exit(1);
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      argParser.displayMessageAndUsageReference(System.err, ERR_DSCORE_ERROR_PARSING_ARGS.get(ae.getMessage()));
      System.exit(1);
    }

    // If we should just display usage information, then print it and exit.
    if (checkStartability.isPresent())
    {
      // This option should only be used if a PID file already exists in the
      // server logs directory, and we need to check which of the following
      // conditions best describes the current usage:
      // - We're trying to start the server, but it's already running.  The
      //   attempt to start the server should fail, and the server process will
      //   exit with a result code of 98.
      // - We're trying to start the server and it's not already running.  We
      //   won't start it in this invocation, but the script used to get to this
      //   point should go ahead and overwrite the PID file and retry the
      //   startup process.  The server process will exit with a result code of
      //   99.
      // - We're not trying to start the server, but instead are trying to do
      //   something else like display the version number.  In that case, we
      //   don't need to write the PID file at all and can just execute the
      //   intended command.  If that command was successful, then we'll have an
      //   exit code of NOTHING_TO_DO (0).  Otherwise, it will have an exit code
      //   that is something other than NOTHING_TO_DO, SERVER_ALREADY_STARTED,
      //   START_AS_DETACH, START_AS_NON_DETACH, START_AS_WINDOWS_SERVICE,
      //   START_AS_DETACH_QUIET, START_AS_NON_DETACH_QUIET to indicate that a
      //   problem occurred.
      if (argParser.usageOrVersionDisplayed())
      {
        // We're just trying to display usage, and that's already been done so
        // exit with a code of zero.
        System.exit(NOTHING_TO_DO);
      }
      else if (fullVersion.isPresent() || systemInfo.isPresent())
      {
        // We're not really trying to start, so rebuild the argument list
        // without the "--checkStartability" argument and try again.  Exit with
        // whatever that exits with.
        List<String> newArgList = new LinkedList<>();
        for (String arg : args)
        {
          if (!"--checkstartability".equalsIgnoreCase(arg))
          {
            newArgList.add(arg);
          }
        }
        String[] newArgs = new String[newArgList.size()];
        newArgList.toArray(newArgs);
        main(newArgs);
        System.exit(NOTHING_TO_DO);
      }
      else
      {
        System.exit(checkStartability(argParser));
      }
    }
    else if (argParser.usageOrVersionDisplayed())
    {
      System.exit(0);
    }
    else if (fullVersion.isPresent())
    {
      printFullVersionInformation();
      return;
    }
    else if (systemInfo.isPresent())
    {
      RuntimeInformation.printInfo();
      return;
    }
    else if (noDetach.isPresent() && timeout.isPresent()) {
      argParser.displayMessageAndUsageReference(System.err, ERR_DSCORE_ERROR_NODETACH_TIMEOUT.get());
      System.exit(1);
    }

    // At this point, we know that we're going to try to start the server.
    // Attempt to grab an exclusive lock for the Directory Server process.
    String lockFile = LockFileManager.getServerLockFileName();
    try
    {
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.acquireExclusiveLock(lockFile, failureReason))
      {
        System.err.println(ERR_CANNOT_ACQUIRE_EXCLUSIVE_SERVER_LOCK.get(lockFile, failureReason));
        System.exit(1);
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);

      System.err.println(ERR_CANNOT_ACQUIRE_EXCLUSIVE_SERVER_LOCK.get(
          lockFile, stackTraceToSingleLineString(e)));
      System.exit(1);
    }
    serverLocked = true;

    // Create an environment configuration for the server and populate a number
    // of appropriate properties.
    DirectoryEnvironmentConfig environmentConfig = new DirectoryEnvironmentConfig();
    try
    {
      environmentConfig.setProperty(PROPERTY_CONFIG_FILE, configFile.getValue());
      environmentConfig.setProperty(PROPERTY_USE_LAST_KNOWN_GOOD_CONFIG,
          String.valueOf(useLastKnownGoodConfig.isPresent()));
    }
    catch (Exception e)
    {
      // This shouldn't happen.  For the methods we are using, the exception is
      // just a guard against making changes with the server running.
      System.err.println("WARNING:  Unable to set environment properties in environment config : "
          + stackTraceToSingleLineString(e));
    }

    // Configure the JVM to delete the PID file on exit, if it exists.
    boolean pidFileMarkedForDeletion      = false;
    boolean startingFileMarkedForDeletion = false;
    try
    {
      String pidFilePath;
      String startingFilePath;
      File instanceRoot = environmentConfig.getInstanceRoot();
      if (instanceRoot == null)
      {
        pidFilePath      = "logs/server.pid";
        startingFilePath = "logs/server.starting";
      }
      else
      {
        pidFilePath = instanceRoot.getAbsolutePath() + File.separator + "logs"
            + File.separator + "server.pid";
        startingFilePath = instanceRoot.getAbsolutePath() + File.separator
            + "logs" + File.separator + "server.starting";
      }

      File pidFile = new File(pidFilePath);
      if (pidFile.exists())
      {
        pidFile.deleteOnExit();
        pidFileMarkedForDeletion = true;
      }

      File startingFile = new File(startingFilePath);
      if (startingFile.exists())
      {
        startingFile.deleteOnExit();
        startingFileMarkedForDeletion = true;
      }
    } catch (Exception e) {}

    // Redirect standard output and standard error to the server.out file.  If
    // the server hasn't detached from the terminal, then also continue writing
    // to the original standard output and standard error.  Also, configure the
    // JVM to delete the PID and server.starting files on exit, if they exist.
    PrintStream serverOutStream;
    try
    {
      File serverRoot = environmentConfig.getServerRoot();
      if (serverRoot == null)
      {
        System.err.println("WARNING:  Unable to determine server root in " +
            "order to redirect standard output and standard error.");
      }
      else
      {
        File instanceRoot = environmentConfig.getInstanceRoot();
        File logDir = new File(instanceRoot.getAbsolutePath() + File.separator
            + "logs");
        if (logDir.exists())
        {
          FileOutputStream fos =
               new FileOutputStream(new File(logDir, "server.out"), true);
          serverOutStream = new PrintStream(fos);

          if (noDetach.isPresent() && !quietMode.isPresent())
          {
            MultiOutputStream multiStream =
                new MultiOutputStream(System.out, serverOutStream);
            serverOutStream = new PrintStream(multiStream);
          }

          System.setOut(serverOutStream);
          System.setErr(serverOutStream);

          if (! pidFileMarkedForDeletion)
          {
            File f = new File(logDir, "server.pid");
            if (f.exists())
            {
              f.deleteOnExit();
            }
          }

          if (! startingFileMarkedForDeletion)
          {
            File f = new File(logDir, "server.starting");
            if (f.exists())
            {
              f.deleteOnExit();
            }
          }
        }
        else
        {
          System.err.println("WARNING:  Unable to redirect standard output " +
                             "and standard error because the logs directory " +
                             logDir.getAbsolutePath() + " does not exist.");
        }
      }
    }
    catch (Exception e)
    {
      System.err.println("WARNING:  Unable to redirect standard output and " +
                         "standard error:  " + stackTraceToSingleLineString(e));
    }

    // Install the default loggers so the startup messages
    // will be printed.
    ErrorLogPublisher startupErrorLogPublisher =
        TextErrorLogPublisher.getServerStartupTextErrorPublisher(new TextWriter.STDOUT());
    ErrorLogger.getInstance().addLogPublisher(startupErrorLogPublisher);

    DebugLogPublisher startupDebugLogPublisher =
        DebugLogger.getInstance().addPublisherIfRequired(new TextWriter.STDOUT());

    // Bootstrap and start the Directory Server.
    DirectoryServer theDirectoryServer = DirectoryServer.getInstance();
    try
    {
      theDirectoryServer.setEnvironmentConfig(environmentConfig);
      theDirectoryServer.bootstrapServer();
      theDirectoryServer.initializeConfiguration();
    }
    catch (InitializationException ie)
    {
      logger.traceException(ie);

      LocalizableMessage message = ERR_DSCORE_CANNOT_BOOTSTRAP.get(ie.getMessage());
      System.err.println(message);
      System.exit(1);
    }
    catch (Exception e)
    {
      LocalizableMessage message = ERR_DSCORE_CANNOT_BOOTSTRAP.get(
              stackTraceToSingleLineString(e));
      System.err.println(message);
      System.exit(1);
    }

    try
    {
      theDirectoryServer.startServer();
    }
    catch (Exception e)
    {
      logger.traceException(e);
      LocalizableMessage message = ERR_DSCORE_CANNOT_START.get(stackTraceToSingleLineString(e));
      shutDown(theDirectoryServer.getClass().getName(), message);
    }

    ErrorLogger.getInstance().removeLogPublisher(startupErrorLogPublisher);
    if (startupDebugLogPublisher != null)
    {
      DebugLogger.getInstance().removeLogPublisher(startupDebugLogPublisher);
    }
  }

  /**
   * Construct the DN of a monitor provider entry.
   * @param provider The monitor provider for which a DN is desired.
   * @return The DN of the monitor provider entry.
   */
  public static DN getMonitorProviderDN(MonitorProvider<?> provider)
  {
    // Get a complete DN which could be a tree naming schema
    return DN.valueOf("cn=" + provider.getMonitorInstanceName() + "," + DN_MONITOR_ROOT);
  }

  /**
   * Gets the class loader to be used with this directory server application.
   * <p>
   * The class loader will automatically load classes from plugins where required.
   * <p>
   * Note: {@code public} access is required for compiling the
   * {@code org.opends.server.snmp.SNMPConnectionHandler}.
   *
   * @return Returns the class loader to be used with this directory server application.
   */
  public static ClassLoader getClassLoader()
  {
    return ConfigurationFramework.getInstance().getClassLoader();
  }

  /**
   * Loads the named class using this directory server application's
   * class loader.
   * <p>
   * This method provided as a convenience and is equivalent to
   * calling:
   *
   * <pre>
   * Class.forName(name, true, DirectoryServer.getClassLoader());
   * </pre>
   *
   * @param name
   *          The fully qualified name of the desired class.
   * @return Returns the class object representing the desired class.
   * @throws LinkageError
   *           If the linkage fails.
   * @throws ExceptionInInitializerError
   *           If the initialization provoked by this method fails.
   * @throws ClassNotFoundException
   *           If the class cannot be located by the specified class
   *           loader.
   * @see Class#forName(String, boolean, ClassLoader)
   */
  public static Class<?> loadClass(String name) throws LinkageError,
          ExceptionInInitializerError, ClassNotFoundException
  {
    return Class.forName(name, true, DirectoryServer.getClassLoader());
  }

  /**
   * Returns the error code that we return when we are checking the startability
   * of the server.
   * If there are conflicting arguments (like asking to run the server in non
   * detach mode when the server is configured to run as a window service) it
   * returns CHECK_ERROR (1).
   * @param argParser the ArgumentParser with the arguments already parsed.
   * @return the error code that we return when we are checking the startability
   * of the server.
   */
  private static int checkStartability(ArgumentParser argParser)
  {
    boolean isServerRunning;

    BooleanArgument noDetach =
      (BooleanArgument)argParser.getArgumentForLongID("nodetach");
    BooleanArgument quietMode =
      (BooleanArgument)argParser.getArgumentForLongID(ArgumentConstants.OPTION_LONG_QUIET);
    BooleanArgument windowsNetStart =
      (BooleanArgument)argParser.getArgumentForLongID("windowsnetstart");

    boolean noDetachPresent = noDetach.isPresent();
    boolean windowsNetStartPresent = windowsNetStart.isPresent();

    // We're trying to start the server, so see if it's already running by
    // trying to grab an exclusive lock on the server lock file.  If it
    // succeeds, then the server isn't running and we can try to start.
    // Otherwise, the server is running and this attempt should fail.
    String lockFile = LockFileManager.getServerLockFileName();
    try
    {
      StringBuilder failureReason = new StringBuilder();
      if (LockFileManager.acquireExclusiveLock(lockFile, failureReason))
      {
        // The server isn't running, so it can be started.
        LockFileManager.releaseLock(lockFile, failureReason);
        isServerRunning = false;
      }
      else
      {
        // The server's already running.
        System.err.println(ERR_CANNOT_ACQUIRE_EXCLUSIVE_SERVER_LOCK.get(lockFile, failureReason));
        isServerRunning = true;
      }
    }
    catch (Exception e)
    {
      // We'll treat this as if the server is running because we won't
      // be able to start it anyway.
      LocalizableMessage message = ERR_CANNOT_ACQUIRE_EXCLUSIVE_SERVER_LOCK.get(lockFile,
          getExceptionMessage(e));
      System.err.println(message);
      isServerRunning = true;
    }

    final boolean configuredAsWindowsService = isRunningAsWindowsService();
    if (isServerRunning)
    {
      if (configuredAsWindowsService && !windowsNetStartPresent)
      {
        return START_AS_WINDOWS_SERVICE;
      }
      else
      {
        return SERVER_ALREADY_STARTED;
      }
    }
    else if (configuredAsWindowsService)
    {
      if (noDetachPresent)
      {
        // Conflicting arguments
        System.err.println(ERR_DSCORE_ERROR_NODETACH_AND_WINDOW_SERVICE.get());
        return CHECK_ERROR;
      }
      else if (windowsNetStartPresent)
      {
        // start-ds.bat is being called through net start, so return
        // START_AS_DETACH_CALLED_FROM_WINDOWS_SERVICE so that the batch
        // file actually starts the server.
        return START_AS_DETACH_CALLED_FROM_WINDOWS_SERVICE;
      }
      else
      {
        return START_AS_WINDOWS_SERVICE;
      }
    }
    else if (noDetachPresent)
    {
      if (quietMode.isPresent())
      {
        return START_AS_NON_DETACH_QUIET;
      }
      else
      {
        return START_AS_NON_DETACH;
      }
    }
    else if (quietMode.isPresent())
    {
      return START_AS_DETACH_QUIET;
    }
    else
    {
      return START_AS_DETACH;
    }
  }

  /**
   * Returns true if this server is configured to run as a windows service.
   * @return <CODE>true</CODE> if this server is configured to run as a windows
   * service and <CODE>false</CODE> otherwise.
   */
  public static boolean isRunningAsWindowsService()
  {
    return OperatingSystem.isWindows()
        && serviceState() == SERVICE_STATE_ENABLED;
  }

  // TODO JNR remove error CoreMessages.ERR_REGISTER_WORKFLOW_ELEMENT_ALREADY_EXISTS

  /** Print messages for start-ds "-F" option (full version information). */
  private static void printFullVersionInformation() {
    /*
     * This option is used by the upgrade to identify the server build and it
     * can eventually also be used to be sent to the support in case of an
     * issue.  Since this is not a public interface and since it is better
     * to always have it in English for the support team, the message is
     * not localized.
     */
    String separator = ": ";
    System.out.println(getVersionString());
    System.out.println(SetupUtils.BUILD_ID+separator+BUILD_ID);
    System.out.println(SetupUtils.MAJOR_VERSION+separator+MAJOR_VERSION);
    System.out.println(SetupUtils.MINOR_VERSION+separator+MINOR_VERSION);
    System.out.println(SetupUtils.POINT_VERSION+separator+POINT_VERSION);
    System.out.println(SetupUtils.VERSION_QUALIFIER+separator+
        VERSION_QUALIFIER);
    if (BUILD_NUMBER > 0)
    {
      System.out.println(SetupUtils.BUILD_NUMBER+separator+
                     new DecimalFormat("000").format(BUILD_NUMBER));
    }
    System.out.println(SetupUtils.REVISION+separator+REVISION);
    System.out.println(SetupUtils.URL_REPOSITORY+separator+URL_REPOSITORY);
    System.out.println(SetupUtils.FIX_IDS+separator+FIX_IDS);
    System.out.println(SetupUtils.DEBUG_BUILD+separator+DEBUG_BUILD);
    System.out.println(SetupUtils.BUILD_OS+separator+BUILD_OS);
    System.out.println(SetupUtils.BUILD_USER+separator+BUILD_USER);
    System.out.println(SetupUtils.BUILD_JAVA_VERSION+separator+
        BUILD_JAVA_VERSION);
    System.out.println(SetupUtils.BUILD_JAVA_VENDOR+separator+
        BUILD_JAVA_VENDOR);
    System.out.println(SetupUtils.BUILD_JVM_VERSION+separator+
        BUILD_JVM_VERSION);
    System.out.println(SetupUtils.BUILD_JVM_VENDOR+separator+BUILD_JVM_VENDOR);

    // Print extensions' extra information
    String extensionInformation =
            ConfigurationFramework.getPrintableExtensionInformation(getServerRoot(), getInstanceRoot());
    if ( extensionInformation != null ) {
      System.out.print(extensionInformation);
    }
  }

  /**
   * Returns the lock manager which will be used for coordinating access to LDAP entries.
   *
   * @return the lock manager which will be used for coordinating access to LDAP entries.
   */
  public static LockManager getLockManager()
  {
    return directoryServer.lockManager;
  }
}
