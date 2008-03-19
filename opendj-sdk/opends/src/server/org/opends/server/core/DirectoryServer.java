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
package org.opends.server.core;

import org.opends.server.admin.ClassLoaderProvider;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.meta.GlobalCfgDefn.WorkflowConfigurationMode;
import org.opends.server.admin.std.server.*;
import org.opends.server.api.AccountStatusNotificationHandler;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.AlertHandler;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.Backend;
import org.opends.server.api.BackendInitializationListener;
import org.opends.server.api.BackupTaskListener;
import org.opends.server.api.CertificateMapper;
import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.CompressedSchema;
import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigChangeListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.api.ConfigHandler;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.DirectoryServerMBean;
import org.opends.server.api.EntryCache;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.ExportTaskListener;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.api.IdentityMapper;
import org.opends.server.api.ImportTaskListener;
import org.opends.server.api.InvokableComponent;
import org.opends.server.api.KeyManagerProvider;
import org.opends.server.api.MatchingRule;
import org.opends.server.api.MonitorProvider;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.PasswordGenerator;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.api.PasswordValidator;
import org.opends.server.api.RestoreTaskListener;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.api.TrustManagerProvider;
import org.opends.server.api.WorkQueue;
import org.opends.server.api.AccessControlHandler;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.backends.RootDSEBackend;
import static org.opends.server.config.ConfigConstants.DN_MONITOR_ROOT;
import static org.opends.server.config.ConfigConstants.ENV_VAR_INSTANCE_ROOT;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.JMXMBean;
import org.opends.server.controls.PasswordPolicyErrorType;
import org.opends.server.controls.PasswordPolicyResponseControl;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.extensions.JMXAlertHandler;
import static org.opends.server.loggers.AccessLogger.*;
import static org.opends.server.loggers.ErrorLogger.*;
import org.opends.server.loggers.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.loggers.debug.DebugLogger;
import org.opends.server.loggers.debug.TextDebugLogPublisher;

import org.opends.messages.MessageDescriptor;
import org.opends.messages.Message;
import static org.opends.messages.CoreMessages.*;
import static org.opends.messages.ToolMessages.*;
import org.opends.server.monitors.BackendMonitor;
import org.opends.server.monitors.ConnectionHandlerMonitor;
import org.opends.server.schema.AttributeTypeSyntax;
import org.opends.server.schema.BinarySyntax;
import org.opends.server.schema.BooleanEqualityMatchingRule;
import org.opends.server.schema.BooleanSyntax;
import org.opends.server.schema.CaseExactEqualityMatchingRule;
import org.opends.server.schema.CaseExactIA5EqualityMatchingRule;
import org.opends.server.schema.CaseExactIA5SubstringMatchingRule;
import org.opends.server.schema.CaseExactOrderingMatchingRule;
import org.opends.server.schema.CaseExactSubstringMatchingRule;
import org.opends.server.schema.CaseIgnoreEqualityMatchingRule;
import org.opends.server.schema.CaseIgnoreIA5EqualityMatchingRule;
import org.opends.server.schema.CaseIgnoreIA5SubstringMatchingRule;
import org.opends.server.schema.CaseIgnoreOrderingMatchingRule;
import org.opends.server.schema.CaseIgnoreSubstringMatchingRule;
import org.opends.server.schema.DirectoryStringSyntax;
import org.opends.server.schema.DistinguishedNameEqualityMatchingRule;
import org.opends.server.schema.DistinguishedNameSyntax;
import org.opends.server.schema.DoubleMetaphoneApproximateMatchingRule;
import org.opends.server.schema.GeneralizedTimeEqualityMatchingRule;
import org.opends.server.schema.GeneralizedTimeOrderingMatchingRule;
import org.opends.server.schema.GeneralizedTimeSyntax;
import org.opends.server.schema.IA5StringSyntax;
import org.opends.server.schema.IntegerEqualityMatchingRule;
import org.opends.server.schema.IntegerOrderingMatchingRule;
import org.opends.server.schema.IntegerSyntax;
import org.opends.server.schema.OIDSyntax;
import org.opends.server.schema.ObjectClassSyntax;
import org.opends.server.schema.ObjectIdentifierEqualityMatchingRule;
import org.opends.server.schema.OctetStringEqualityMatchingRule;
import org.opends.server.schema.OctetStringOrderingMatchingRule;
import org.opends.server.schema.OctetStringSubstringMatchingRule;
import static org.opends.server.schema.SchemaConstants.*;
import org.opends.server.schema.TelephoneNumberEqualityMatchingRule;
import org.opends.server.schema.TelephoneNumberSubstringMatchingRule;
import org.opends.server.schema.TelephoneNumberSyntax;
import org.opends.server.tools.ConfigureWindowsService;
import org.opends.server.types.AbstractOperation;
import org.opends.server.types.AcceptRejectWarn;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeUsage;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.BackupConfig;
import org.opends.server.types.Control;
import org.opends.server.crypto.CryptoManagerImpl;
import org.opends.server.types.DITContentRule;
import org.opends.server.types.DITStructureRule;
import org.opends.server.types.DN;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.HostPort;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LDIFExportConfig;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.MatchingRuleUse;
import org.opends.server.types.Modification;
import org.opends.server.types.NameForm;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.ObjectClassType;
import org.opends.server.types.OperatingSystem;
import org.opends.server.types.OperationType;
import org.opends.server.types.Privilege;
import org.opends.server.types.RDN;
import org.opends.server.types.RestoreConfig;
import org.opends.server.types.ResultCode;
import org.opends.server.types.Schema;
import org.opends.server.types.VirtualAttributeRule;
import org.opends.server.types.WritabilityMode;
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.types.LockManager;
import static org.opends.server.util.DynamicConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.util.Validator.ensureNotNull;
import org.opends.server.util.*;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;
import org.opends.server.workflowelement.*;
import org.opends.server.workflowelement.localbackend.*;
import org.opends.server.protocols.internal.InternalConnectionHandler;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.crypto.CryptoManagerSync;
import static org.opends.messages.ConfigMessages.*;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;


/**
 * This class defines the core of the Directory Server.  It manages the startup
 * and shutdown processes and coordinates activities between all other
 * components.
 */
public class DirectoryServer
       implements Thread.UncaughtExceptionHandler, AlertGenerator
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

    /**
    * The fully-qualified name of this class.
    */
    private static final String CLASS_NAME =
       "org.opends.server.core.DirectoryServer";


  /**
   * The singleton Directory Server instance.
   */
  private static DirectoryServer directoryServer = new DirectoryServer();



  /**
   * Indicates whether the server currently holds an exclusive lock on the
   * server lock fiie.
   */
  private static boolean serverLocked = false;


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
  private static int NOTHING_TO_DO = 0;
  /**
   * Returned when the user specified the --checkStartability option with
   * some incompatible arguments.
   */
  private static int CHECK_ERROR = 1;
  /**
   * The server is already started.
   */
  private static int SERVER_ALREADY_STARTED = 98;
  /**
   * The server must be started as detached process.
   */
  private static int START_AS_DETACH = 99;
  /**
   * The server must be started as a non-detached process.
   */
  private static int START_AS_NON_DETACH = 100;
  /**
   * The server must be started as a window service.
   */
  private static int START_AS_WINDOWS_SERVICE = 101;
  /**
   * The server must be started as detached and it is being called from the
   * Windows Service.
   */
  private static int START_AS_DETACH_CALLED_FROM_WINDOWS_SERVICE = 102;
  /**
   * The server must be started as detached process and should not produce any
   * output.
   */
  private static int START_AS_DETACH_QUIET = 103;

  // The policy to use regarding single structural objectclass enforcement.
  private AcceptRejectWarn singleStructuralClassPolicy;

  // The policy to use regarding syntax enforcement.
  private AcceptRejectWarn syntaxEnforcementPolicy;

  // The account status notification handler config manager for the server.
  private AccountStatusNotificationHandlerConfigManager
       accountStatusNotificationHandlerConfigManager;

  // The default syntax to use for binary attributes.
  private AttributeSyntax<AttributeSyntaxCfg> defaultBinarySyntax;

  // The default syntax to use for Boolean attributes.
  private AttributeSyntax<AttributeSyntaxCfg> defaultBooleanSyntax;

  // The default syntax to use for DN attributes.
  private AttributeSyntax<AttributeSyntaxCfg> defaultDNSyntax;

  // The default syntax to use for integer attributes.
  private AttributeSyntax<AttributeSyntaxCfg> defaultIntegerSyntax;

  // The default syntax to use for string attributes.
  private AttributeSyntax<DirectoryStringAttributeSyntaxCfg>
               defaultStringSyntax;

  // The default attribute syntax to use for attributes with no defined syntax.
  private AttributeSyntax<DirectoryStringAttributeSyntaxCfg> defaultSyntax;

  // The attribute type used to reference the "objectclass" attribute.
  private AttributeType objectClassAttributeType;

  // The authenticated users manager for the server.
  private AuthenticatedUsers authenticatedUsers;

  // The configuration manager that will handle the server backends.
  private BackendConfigManager backendConfigManager;

  // Indicates whether to automatically add missing RDN attributes to entries
  // during an add request.
  private boolean addMissingRDNAttributes;

  // Indicates whether to allow attribute name exceptions (i.e., attribute names
  // can contain underscores and may start with a digit).
  private boolean allowAttributeNameExceptions;

  // Indicates whether a simple bind request containing a DN must also provide a
  // password.
  private boolean bindWithDNRequiresPassword;

  // Indicates whether the Directory Server should perform schema checking for
  // update operations.
  private boolean checkSchema;

  // Indicates whether the server has been bootstrapped.
  private boolean isBootstrapped;

  // Indicates whether the server has been bootstrapped for client use.
  private boolean isClientBootstrapped;

  // Indicates whether the server is currently online.
  private boolean isRunning;

  // Indicates whether the server is currently in "lockdown mode".
  private boolean lockdownMode;

  // Indicates whether the server should send a response to operations that have
  // been abandoned.
  private boolean notifyAbandonedOperations;

  // Indicates whether to save a copy of the configuration on successful
  // startup.
  private boolean saveConfigOnSuccessfulStartup;

  // Indicates whether the server is currently in the process of shutting down.
  private boolean shuttingDown;

  // Indicates whether the server should reject unauthenticated requests.
  private boolean rejectUnauthenticatedRequests;

  // Indicates whether bind responses should include failure reason messages.
  private boolean returnBindErrorMessages;

  // The configuration manager that will handle the certificate mapper.
  private CertificateMapperConfigManager certificateMapperConfigManager;

  // The class used to provide the config handler implementation.
  private Class configClass;

  // The configuration handler for the Directory Server.
  private ConfigHandler configHandler;

  // The set of account status notification handlers defined in the server.
  private ConcurrentHashMap<DN,AccountStatusNotificationHandler>
               accountStatusNotificationHandlers;

  // The set of certificate mappers registered with the server.
  private ConcurrentHashMap<DN,CertificateMapper> certificateMappers;

  // The set of alternate bind DNs for the root users.
  private ConcurrentHashMap<DN,DN> alternateRootBindDNs;

  // The set of identity mappers registered with the server (mapped between
  // the configuration entry Dn and the mapper).
  private ConcurrentHashMap<DN,IdentityMapper> identityMappers;

  // The set of JMX MBeans that have been registered with the server (mapped
  // between the associated configuration entry DN and the MBean).
  private ConcurrentHashMap<DN,JMXMBean> mBeans;

  // The set of key manager providers registered with the server.
  private ConcurrentHashMap<DN,KeyManagerProvider> keyManagerProviders;

  // The set of password generators registered with the Directory Server, as a
  // mapping between the DN of the associated configuration entry and the
  // generator implementation.
  private ConcurrentHashMap<DN,PasswordGenerator> passwordGenerators;

  // The set of password policies registered with the Directory Server, as a
  // mapping between the DN of the associated configuration entry and the policy
  // implementation.
  private ConcurrentHashMap<DN,PasswordPolicyConfig> passwordPolicies;

  // The set of password validators registered with the Directory Server, as a
  // mapping between the DN of the associated configuration entry and the
  // validator implementation.
  private ConcurrentHashMap<DN,
               PasswordValidator<? extends PasswordValidatorCfg>>
               passwordValidators;

  // The set of trust manager providers registered with the server.
  private ConcurrentHashMap<DN,TrustManagerProvider> trustManagerProviders;

  // The set of log rotation policies registered with the Directory Server, as
  // a mapping between the DN of the associated configuration entry and the
  // policy implementation.
  private ConcurrentHashMap<DN, RotationPolicy> rotationPolicies;

  // The set of log retention policies registered with the Directory Server, as
  // a mapping between the DN of the associated configuration entry and the
  // policy implementation.
  private ConcurrentHashMap<DN, RetentionPolicy> retentionPolicies;

  // The set supported LDAP protocol versions.
  private ConcurrentHashMap<Integer,List<ConnectionHandler>>
               supportedLDAPVersions;

  // The set of extended operation handlers registered with the server (mapped
  // between the OID of the extended operation and the handler).
  private ConcurrentHashMap<String,ExtendedOperationHandler>
               extendedOperationHandlers;

  // The set of monitor providers registered with the Directory Server, as a
  // mapping between the monitor name and the corresponding implementation.
  private ConcurrentHashMap<String,
                            MonitorProvider<? extends MonitorProviderCfg>>
               monitorProviders;

  // The set of password storage schemes defined in the server (mapped between
  // the lowercase scheme name and the storage scheme) that support the
  // authentication password syntax.
  private ConcurrentHashMap<String,PasswordStorageScheme>
               authPasswordStorageSchemes;

  // The set of password storage schemes defined in the server (mapped between
  // the lowercase scheme name and the storage scheme).
  private ConcurrentHashMap<String,PasswordStorageScheme>
               passwordStorageSchemes;

  // The set of password storage schemes defined in the server (mapped between
  // the DN of the configuration entry and the storage scheme).
  private ConcurrentHashMap<DN,PasswordStorageScheme>
               passwordStorageSchemesByDN;

  // The set of SASL mechanism handlers registered with the server (mapped
  // between the mechanism name and the handler).
  private ConcurrentHashMap<String,SASLMechanismHandler> saslMechanismHandlers;

  // The connection handler configuration manager for the Directory Server.
  private ConnectionHandlerConfigManager connectionHandlerConfigManager;

  // The set of alert handlers registered with the Directory Server.
  private CopyOnWriteArrayList<AlertHandler> alertHandlers;

  // The set of backup task listeners registered with the Directory Server.
  private CopyOnWriteArrayList<BackupTaskListener> backupTaskListeners;

  // The set of change notification listeners registered with the Directory
  // Server.
  private CopyOnWriteArrayList<ChangeNotificationListener>
               changeNotificationListeners;

  // The set of connection handlers registered with the Directory Server.
  private CopyOnWriteArrayList<ConnectionHandler> connectionHandlers;

  // The set of export task listeners registered with the Directory Server.
  private CopyOnWriteArrayList<ExportTaskListener> exportTaskListeners;

  // The set of import task listeners registered with the Directory Server.
  private CopyOnWriteArrayList<ImportTaskListener> importTaskListeners;

  // The set of persistent searches registered with the Directory Server.
  private CopyOnWriteArrayList<PersistentSearch> persistentSearches;

  // The set of restore task listeners registered with the Directory Server.
  private CopyOnWriteArrayList<RestoreTaskListener> restoreTaskListeners;

  // The set of shutdown listeners that have been registered with the Directory
  // Server.
  private CopyOnWriteArrayList<ServerShutdownListener> shutdownListeners;

  // The set of synchronization providers that have been registered with the
  // Directory Server.
  private
    CopyOnWriteArrayList<SynchronizationProvider<SynchronizationProviderCfg>>
               synchronizationProviders;

  // The set of virtual attributes defined in the server.
  private CopyOnWriteArrayList<VirtualAttributeRule> virtualAttributes;

  // The set of backend initialization listeners registered with the Directory
  // Server.
  private CopyOnWriteArraySet<BackendInitializationListener>
               backendInitializationListeners;

  // The set of root DNs registered with the Directory Server.
  private CopyOnWriteArraySet<DN> rootDNs;

  // The core configuration manager for the Directory Server.
  private CoreConfigManager coreConfigManager;

  // The crypto manager for the Directory Server.
  private CryptoManagerImpl cryptoManager;

  // The default compressed schema manager.
  private DefaultCompressedSchema compressedSchema;

  // The environment configuration for the Directory Server.
  private DirectoryEnvironmentConfig environmentConfig;

  // The shutdown hook that has been registered with the server.
  private DirectoryServerShutdownHook shutdownHook;

  // The DN of the default password policy configuration entry.
  private DN defaultPasswordPolicyDN;

  // The DN of the identity mapper that will be used to resolve authorization
  // IDs contained in the proxied authorization V2 control.
  private DN proxiedAuthorizationIdentityMapperDN;

  // The DN of the entry containing the server schema definitions.
  private DN schemaDN;

  // The Directory Server entry cache.
  private EntryCache entryCache;

  // The configuration manager for the entry cache.
  private EntryCacheConfigManager entryCacheConfigManager;

  // The configuration manager for extended operation handlers.
  private ExtendedOperationConfigManager extendedOperationConfigManager;

  // The path to the file containing the Directory Server configuration, or the
  // information needed to bootstrap the configuration handler.
  private File configFile;

  // The group manager for the Directory Server.
  private GroupManager groupManager;

  // The configuration manager for identity mappers.
  private IdentityMapperConfigManager identityMapperConfigManager;

  // The maximum number of entries that should be returned for a search unless
  // overridden on a per-user basis.
  private int sizeLimit;

  // The maximum length of time in seconds that should be allowed for a search
  // unless overridden on a per-user basis.
  private int timeLimit;

  // The maxiumum number of candidates that should be check for matches during
  // a search.
  private int lookthroughLimit;

  // Whether to use collect operation processing times in nanosecond resolution
  private boolean useNanoTime;

  // The key manager provider configuration manager for the Directory Server.
  private KeyManagerProviderConfigManager keyManagerProviderConfigManager;

  // The set of connections that are currently established.
  private LinkedHashSet<ClientConnection> establishedConnections;

  // The sets of mail server properties
  private List<Properties> mailServerPropertySets;

  // The set of schema changes made by editing the schema configuration files
  // with the server offline.
  private List<Modification> offlineSchemaChanges;

  // The log rotation policy config manager for the Directory Server.
  private LogRotationPolicyConfigManager rotationPolicyConfigManager;

  // The log retention policy config manager for the Directory Server.
  private LogRetentionPolicyConfigManager retentionPolicyConfigManager;

  // The logger configuration manager for the Directory Server.
  private LoggerConfigManager loggerConfigManager;

  // The number of connections currently established to the server.
  private long currentConnections;

  // The idle time limit for the server.
  private long idleTimeLimit;

  // The maximum number of connections that will be allowed at any given time.
  private long maxAllowedConnections;

  // The maximum number of connections established at one time.
  private long maxConnections;

  // The time that this Directory Server instance was started.
  private long startUpTime;

  // The total number of connections established since startup.
  private long totalConnections;

  // The MBean server used to handle JMX interaction.
  private MBeanServer mBeanServer;

  // The monitor config manager for the Directory Server.
  private MonitorConfigManager monitorConfigManager;

  // The operating system on which the server is running.
  private OperatingSystem operatingSystem;

  // The configuration handler used to manage the password generators.
  private PasswordGeneratorConfigManager passwordGeneratorConfigManager;

  // The default password policy for the Directory Server.
  private PasswordPolicyConfig defaultPasswordPolicyConfig;

  // The configuration handler used to manage the password policies.
  private PasswordPolicyConfigManager passwordPolicyConfigManager;

  // The configuration handler used to manage the password storage schemes.
  private PasswordStorageSchemeConfigManager storageSchemeConfigManager;

  // The configuration handler used to manage the password validators.
  private PasswordValidatorConfigManager passwordValidatorConfigManager;

  // The plugin config manager for the Directory Server.
  private PluginConfigManager pluginConfigManager;

  // The result code that should be used for internal "server" errors.
  private ResultCode serverErrorResultCode;

  // The special backend used for the Directory Server root DSE.
  private RootDSEBackend rootDSEBackend;

  // The root DN config manager for the server.
  private RootDNConfigManager rootDNConfigManager;

  // The SASL mechanism config manager for the Directory Server.
  private SASLConfigManager saslConfigManager;

  // The schema for the Directory Server.
  private Schema schema;

  // The schema configuration manager for the Directory Server.
  private SchemaConfigManager schemaConfigManager;

  // The set of disabled privileges.
  private Set<Privilege> disabledPrivileges;

  // The set of allowed task classes.
  private Set<String> allowedTasks;

  // The time that the server was started, formatted in UTC time.
  private String startTimeUTC;

  // The synchronization provider configuration manager for the Directory
  // Server.
  private SynchronizationProviderConfigManager
               synchronizationProviderConfigManager;

  // The thread group for all threads associated with the Directory Server.
  private ThreadGroup directoryThreadGroup;


  // Registry for base DN and naming context information.
  private BaseDnRegistry baseDnRegistry;


  // The set of backends registered with the server.
  private TreeMap<String,Backend> backends;

  // The mapping between backends and their unique indentifiers for their
  // offline state, representing either checksum or other unique value to
  // be used for detecting any offline modifications to a given backend.
  private ConcurrentHashMap<String,Long> offlineBackendsStateIDs;

  // The set of supported controls registered with the Directory Server.
  private TreeSet<String> supportedControls;

  // The set of supported feature OIDs registered with the Directory Server.
  private TreeSet<String> supportedFeatures;

  // The trust manager provider configuration manager for the Directory Server.
  private TrustManagerProviderConfigManager trustManagerProviderConfigManager;

  // The virtual attribute provider configuration manager for the Directory
  // Server.
  private VirtualAttributeConfigManager virtualAttributeConfigManager;

  // The work queue that will be used to service client requests.
  private WorkQueue workQueue;

  // The writability mode for the Directory Server.
  private WritabilityMode writabilityMode;

  // The workflow configuration mode (auto or manual).
  private WorkflowConfigurationMode workflowConfigurationMode;

  // The network group config manager for the Directory Server.
  // This config manager is used when the workflow configuration
  // mode is 'manual'.
  private NetworkGroupConfigManager networkGroupConfigManager;

  // The workflow config manager for the Directory Server.
  // This config manager is used when the workflow configuration
  // mode is 'manual'.
  private WorkflowConfigManager workflowConfigManager;

  // The workflow element config manager for the Directory Server.
  // This config manager is used when the workflow configuration
  // mode is 'manual'.
  private WorkflowElementConfigManager workflowElementConfigManager;



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
    isClientBootstrapped     = false;
    isRunning                = false;
    shuttingDown             = false;
    lockdownMode             = false;
    serverErrorResultCode    = ResultCode.OTHER;

    operatingSystem = OperatingSystem.forName(System.getProperty("os.name"));
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
   * by client-side tools.  This is not intended for use in running the server
   * itself.
   */
  public static void bootstrapClient()
  {
    synchronized (directoryServer)
    {
      if (directoryServer.isClientBootstrapped)
      {
        return;
      }


      // Set default values for variables that may be needed during schema
      // processing.
      directoryServer.syntaxEnforcementPolicy = AcceptRejectWarn.REJECT;


      // Create the server schema and initialize and register a minimal set of
      // matching rules and attribute syntaxes.
      directoryServer.schema = new Schema();
      directoryServer.bootstrapMatchingRules();
      directoryServer.bootstrapAttributeSyntaxes();


      // Perform any additional initialization that might be necessary before
      // loading the configuration.
      directoryServer.alertHandlers = new CopyOnWriteArrayList<AlertHandler>();
      directoryServer.passwordStorageSchemes =
           new ConcurrentHashMap<String,PasswordStorageScheme>();
      directoryServer.passwordStorageSchemesByDN =
           new ConcurrentHashMap<DN,PasswordStorageScheme>();
      directoryServer.passwordGenerators =
           new ConcurrentHashMap<DN,PasswordGenerator>();
      directoryServer.authPasswordStorageSchemes =
           new ConcurrentHashMap<String,PasswordStorageScheme>();
      directoryServer.passwordValidators =
           new ConcurrentHashMap<DN,
                PasswordValidator<? extends PasswordValidatorCfg>>();
      directoryServer.accountStatusNotificationHandlers =
           new ConcurrentHashMap<DN,AccountStatusNotificationHandler>();
      directoryServer.rootDNs = new CopyOnWriteArraySet<DN>();
      directoryServer.alternateRootBindDNs = new ConcurrentHashMap<DN,DN>();
      directoryServer.keyManagerProviders =
           new ConcurrentHashMap<DN,KeyManagerProvider>();
      directoryServer.trustManagerProviders =
           new ConcurrentHashMap<DN,TrustManagerProvider>();
      directoryServer.rotationPolicies =
           new ConcurrentHashMap<DN, RotationPolicy>();
      directoryServer.retentionPolicies =
           new ConcurrentHashMap<DN, RetentionPolicy>();
      directoryServer.certificateMappers =
           new ConcurrentHashMap<DN,CertificateMapper>();
      directoryServer.passwordPolicies =
           new ConcurrentHashMap<DN,PasswordPolicyConfig>();
      directoryServer.defaultPasswordPolicyDN = null;
      directoryServer.defaultPasswordPolicyConfig = null;
      directoryServer.monitorProviders =
           new ConcurrentHashMap<String,
                    MonitorProvider<? extends MonitorProviderCfg>>();
      directoryServer.backends = new TreeMap<String,Backend>();
      directoryServer.offlineBackendsStateIDs =
           new ConcurrentHashMap<String,Long>();
      directoryServer.backendInitializationListeners =
           new CopyOnWriteArraySet<BackendInitializationListener>();
      directoryServer.baseDnRegistry = new BaseDnRegistry();
      directoryServer.changeNotificationListeners =
           new CopyOnWriteArrayList<ChangeNotificationListener>();
      directoryServer.persistentSearches =
           new CopyOnWriteArrayList<PersistentSearch>();
      directoryServer.shutdownListeners =
           new CopyOnWriteArrayList<ServerShutdownListener>();
      directoryServer.synchronizationProviders =
           new CopyOnWriteArrayList<SynchronizationProvider
                                   <SynchronizationProviderCfg>>();
      directoryServer.supportedControls = new TreeSet<String>();
      directoryServer.supportedFeatures = new TreeSet<String>();
      directoryServer.supportedLDAPVersions =
           new ConcurrentHashMap<Integer,List<ConnectionHandler>>();
      directoryServer.virtualAttributes =
           new CopyOnWriteArrayList<VirtualAttributeRule>();
      directoryServer.connectionHandlers =
           new CopyOnWriteArrayList<ConnectionHandler>();
      directoryServer.identityMappers =
           new ConcurrentHashMap<DN,IdentityMapper>();
      directoryServer.extendedOperationHandlers =
           new ConcurrentHashMap<String,ExtendedOperationHandler>();
      directoryServer.saslMechanismHandlers =
           new ConcurrentHashMap<String,SASLMechanismHandler>();
      directoryServer.authenticatedUsers = new AuthenticatedUsers();
      directoryServer.offlineSchemaChanges = new LinkedList<Modification>();
      directoryServer.backupTaskListeners =
           new CopyOnWriteArrayList<BackupTaskListener>();
      directoryServer.restoreTaskListeners =
           new CopyOnWriteArrayList<RestoreTaskListener>();
      directoryServer.exportTaskListeners =
           new CopyOnWriteArrayList<ExportTaskListener>();
      directoryServer.importTaskListeners =
           new CopyOnWriteArrayList<ImportTaskListener>();
      directoryServer.allowedTasks = new LinkedHashSet<String>(0);
      directoryServer.disabledPrivileges = new LinkedHashSet<Privilege>(0);
      directoryServer.returnBindErrorMessages = false;
      directoryServer.idleTimeLimit = 0L;
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
  public void bootstrapServer()
         throws InitializationException
  {
    // First, make sure that the server isn't currently running.  If it isn't,
    // then make sure that no other thread will try to start or bootstrap the
    // server before this thread is done.
    synchronized (directoryServer)
    {
      if (isRunning)
      {
        Message message = ERR_CANNOT_BOOTSTRAP_WHILE_RUNNING.get();
        throw new InitializationException(message);
      }

      isBootstrapped   = false;
      shuttingDown     = false;
    }


    // Create the thread group that should be used for all Directory Server
    // threads.
    directoryThreadGroup = new ThreadGroup("Directory Server Thread Group");


    // Add a shutdown hook so that the server can be notified when the JVM
    // starts shutting down.
    shutdownHook = new DirectoryServerShutdownHook();
    Runtime.getRuntime().addShutdownHook(shutdownHook);


    // Register this class as the default uncaught exception handler for the
    // JVM.  The uncaughtException method will be called if a thread dies
    // because it did not properly handle an exception.
    Thread.setDefaultUncaughtExceptionHandler(this);


    // Create the MBean server that we will use for JMX interaction.
    initializeJMX();


    logError(INFO_DIRECTORY_BOOTSTRAPPING.get());


    // Perform all the bootstrapping that is shared with the client-side
    // processing.
    bootstrapClient();


    // Initialize the variables that will be used for connection tracking.
    establishedConnections = new LinkedHashSet<ClientConnection>(1000);
    currentConnections     = 0;
    maxConnections         = 0;
    totalConnections       = 0;


    // Create the plugin config manager, but don't initialize it yet.  This will
    // make it possible to process internal operations before the plugins have
    // been loaded.
    pluginConfigManager = new PluginConfigManager();


    // If we have gotten here, then the configuration should be properly
    // bootstrapped.
    synchronized (directoryServer)
    {
      isBootstrapped = true;
    }
  }



  /**
   * Performs a minimal set of JMX initialization.  This may be used by the core
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
      // FIXME -- Should we use the plaform Mbean Server or
      // should we use a private one ?
      directoryServer.mBeanServer = MBeanServerFactory.newMBeanServer();
      // directoryServer.mBeanServer =
      //      ManagementFactory.getPlatformMBeanServer();

      directoryServer.mBeans = new ConcurrentHashMap<DN,JMXMBean>();
      registerAlertGenerator(directoryServer);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_CREATE_MBEAN_SERVER.get(String.valueOf(e));
      throw new InitializationException(message, e);
    }
  }



  /**
   * Instantiates the configuration handler and loads the Directory Server
   * configuration.
   *
   * @param  configClass  The fully-qualified name of the Java class that will
   *                      serve as the configuration handler for the Directory
   *                      Server.
   * @param  configFile   The path to the file that will hold either the entire
   *                      server configuration or enough information to allow
   *                      the server to access the configuration in some other
   *                      repository.
   *
   * @throws  InitializationException  If a problem occurs while trying to
   *                                   initialize the config handler.
   */
  public void initializeConfiguration(String configClass, String configFile)
         throws InitializationException
  {
    Class cfgClass;
    try
    {
      cfgClass = Class.forName(configClass);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_CANNOT_LOAD_CONFIG_HANDLER_CLASS.get(
                  configClass, stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }

    File cfgFile = new File(configFile);

    environmentConfig.setConfigClass(cfgClass);
    environmentConfig.setConfigFile(cfgFile);
    initializeConfiguration();
  }



  /**
   * Instantiates the configuration handler and loads the Directory Server
   * configuration.
   *
   * @throws  InitializationException  If a problem occurs while trying to
   *                                   initialize the config handler.
   */
  public void initializeConfiguration()
         throws InitializationException
  {
    this.configClass = environmentConfig.getConfigClass();
    this.configFile  = environmentConfig.getConfigFile();


    // Make sure that administration framework definition classes are loaded.
    ClassLoaderProvider provider = ClassLoaderProvider.getInstance();
    if (! provider.isEnabled())
    {
      provider.enable();
    }


    // Load and instantiate the configuration handler class.
    Class handlerClass = configClass;
    try
    {
      configHandler = (ConfigHandler) handlerClass.newInstance();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_CANNOT_INSTANTIATE_CONFIG_HANDLER.get(
                  String.valueOf(configClass),
                  e.getLocalizedMessage());
      throw new InitializationException(message, e);
    }


    // Perform the handler-specific initialization.
    try
    {
      configHandler.initializeConfigHandler(configFile.getAbsolutePath(),
                                            false);
    }
    catch (InitializationException ie)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ie);
      }

      throw ie;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_CANNOT_INITIALIZE_CONFIG_HANDLER.get(
                  String.valueOf(configClass),
                  String.valueOf(configFile),
                  e.getLocalizedMessage());
      throw new InitializationException(message);
    }

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
    synchronized (directoryServer)
    {
      if (! isBootstrapped)
      {
        Message message = ERR_CANNOT_START_BEFORE_BOOTSTRAP.get();
        throw new InitializationException(message);
      }

      if (isRunning)
      {
        Message message = ERR_CANNOT_START_WHILE_RUNNING.get();
        throw new InitializationException(message);
      }


      logError(NOTE_DIRECTORY_SERVER_STARTING.get(getVersionString(),
                                                  BUILD_ID, REVISION_NUMBER));

      RuntimeInformation.logInfo();
      // Acquire an exclusive lock for the Directory Server process.
      if (! serverLocked)
      {
        String lockFile = LockFileManager.getServerLockFileName();
        try
        {
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.acquireExclusiveLock(lockFile, failureReason))
          {
            Message message = ERR_CANNOT_ACQUIRE_EXCLUSIVE_SERVER_LOCK.get(
                lockFile, String.valueOf(failureReason));
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
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }

          Message message = ERR_CANNOT_ACQUIRE_EXCLUSIVE_SERVER_LOCK.get(
              lockFile, stackTraceToSingleLineString(e));
          throw new InitializationException(message, e);
        }
      }


      // Determine whether or not we should start the connection handlers.
      boolean startConnectionHandlers =
           (! environmentConfig.disableConnectionHandlers());


      // Initialize all the schema elements.
      initializeSchema();


      // Initialize the core Directory Server configuration.
      coreConfigManager = new CoreConfigManager();
      coreConfigManager.initializeCoreConfig();


      // Initialize the Directory Server crypto manager.
      initializeCryptoManager();


      // Initialize the log rotation policies.
      rotationPolicyConfigManager = new LogRotationPolicyConfigManager();
      rotationPolicyConfigManager.initializeLogRotationPolicyConfig();

      // Initialize the log retention policies.
      retentionPolicyConfigManager = new LogRetentionPolicyConfigManager();
      retentionPolicyConfigManager.initializeLogRetentionPolicyConfig();


      // Initialize the server loggers.
      loggerConfigManager = new LoggerConfigManager();
      loggerConfigManager.initializeLoggerConfig();


      // Initialize the server alert handlers.
      initializeAlertHandlers();


      // Initialize the default entry cache. We have to have one before
      // <CODE>initializeBackends()</CODE> method kicks in further down.
      entryCacheConfigManager = new EntryCacheConfigManager();
      entryCacheConfigManager.initializeDefaultEntryCache();


      // Initialize the key manager provider.
      keyManagerProviderConfigManager = new KeyManagerProviderConfigManager();
      keyManagerProviderConfigManager.initializeKeyManagerProviders();


      // Initialize the trust manager provider.
      trustManagerProviderConfigManager =
           new TrustManagerProviderConfigManager();
      trustManagerProviderConfigManager.initializeTrustManagerProviders();


      // Initialize the certificate mapper.
      certificateMapperConfigManager = new CertificateMapperConfigManager();
      certificateMapperConfigManager.initializeCertificateMappers();


      // Initialize the identity mappers.
      initializeIdentityMappers();


      // Initialize the root DNs.
      rootDNConfigManager = new RootDNConfigManager();
      rootDNConfigManager.initializeRootDNs();


      // Initialize the group manager.
      initializeGroupManager();

      // Initialize the access control handler.
      AccessControlConfigManager.getInstance().initializeAccessControl();

      // Initialize all the backends and their associated suffixes
      // and initialize the workflows when workflow configuration mode
      // is auto.
      initializeBackends();

      // When workflow configuration mode is manual, do configure the
      // workflows now, else just configure the remaining workflows
      // (rootDSE and config backend).
      if (workflowConfigurationModeIsAuto())
      {
        createAndRegisterRemainingWorkflows();
      }
      else
      {
        configureWorkflowsManual();
      }

      // Check for and initialize user configured entry cache if any,
      // if not stick with default entry cache initialized earlier.
      entryCacheConfigManager.initializeEntryCache();

      // Reset the map as we can no longer guarantee offline state.
      directoryServer.offlineBackendsStateIDs.clear();

      // Register the supported controls and supported features.
      initializeSupportedControls();
      initializeSupportedFeatures();


      // Initialize all the extended operation handlers.
      initializeExtendedOperations();


      // Initialize all the SASL mechanism handlers.
      initializeSASLMechanisms();


      // Initialize all the virtual attribute handlers.
      initializeVirtualAttributes();


      // Initialize all the connection handlers.
      if (startConnectionHandlers)
      {
        initializeConnectionHandlers();
      }


      // Initialize all the monitor providers.
      monitorConfigManager = new MonitorConfigManager();
      monitorConfigManager.initializeMonitorProviders();


      // Initialize all the password policy components.
      initializePasswordPolicyComponents();


      // Load and initialize all the plugins, and then call the registered
      // startup plugins.
      initializePlugins();


      // Initialize any synchronization providers that may be defined.
      synchronizationProviderConfigManager =
           new SynchronizationProviderConfigManager();
      synchronizationProviderConfigManager.initializeSynchronizationProviders();


      // Create and initialize the work queue.
      workQueue = new WorkQueueConfigManager().initializeWorkQueue();


      PluginResult.Startup startupPluginResult =
           pluginConfigManager.invokeStartupPlugins();
      if (! startupPluginResult.continueProcessing())
      {
        Message message = ERR_STARTUP_PLUGIN_ERROR.
            get(startupPluginResult.getErrorMessage(),
                startupPluginResult.getErrorMessage().getDescriptor().getId());
        throw new InitializationException(message);
      }


      if (startConnectionHandlers)
      {
        startConnectionHandlers();
        new IdleTimeLimitThread().start();
      }


      // Create an object to synchronize ADS with the crypto manager.
      new CryptoManagerSync();

      // If we should write a copy of the config on successful startup, then do
      // so now.
      if (saveConfigOnSuccessfulStartup)
      {
        configHandler.writeSuccessfulStartupConfig();
      }


      // Mark the current time as the start time and indicate that the server is
      // now running.
      startUpTime  = System.currentTimeMillis();
      startTimeUTC = TimeThread.getGMTTime();
      isRunning    = true;

      Message message = NOTE_DIRECTORY_SERVER_STARTED.get();
      logError(message);
      sendAlertNotification(this, ALERT_TYPE_SERVER_STARTED, message);

      // Force the root connection to be initialized.
      InternalClientConnection.getRootConnection();

      // If a server.starting file exists, then remove it.
      File serverStartingFile =
                new File(configHandler.getServerRoot() + File.separator +
                         "logs" + File.separator + "server.starting");
      if (serverStartingFile.exists())
      {
        serverStartingFile.delete();
      }
    }
  }



  /**
   * Registers a basic set of matching rules with the server that should always
   * be available regardless of the server configuration and may be needed for
   * configuration processing.
   */
  private void bootstrapMatchingRules()
  {
    try
    {
      ApproximateMatchingRule matchingRule =
           new DoubleMetaphoneApproximateMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerApproximateMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(DoubleMetaphoneApproximateMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      EqualityMatchingRule matchingRule = new BooleanEqualityMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerEqualityMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(BooleanEqualityMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      EqualityMatchingRule matchingRule = new CaseExactEqualityMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerEqualityMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(CaseExactEqualityMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      EqualityMatchingRule matchingRule =
           new CaseExactIA5EqualityMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerEqualityMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(CaseExactIA5EqualityMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      EqualityMatchingRule matchingRule = new CaseIgnoreEqualityMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerEqualityMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(CaseIgnoreEqualityMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      EqualityMatchingRule matchingRule =
           new CaseIgnoreIA5EqualityMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerEqualityMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(CaseIgnoreIA5EqualityMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      EqualityMatchingRule matchingRule =
           new DistinguishedNameEqualityMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerEqualityMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(DistinguishedNameEqualityMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      EqualityMatchingRule matchingRule =
           new GeneralizedTimeEqualityMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerEqualityMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(GeneralizedTimeEqualityMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      EqualityMatchingRule matchingRule = new IntegerEqualityMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerEqualityMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(IntegerEqualityMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      EqualityMatchingRule matchingRule = new OctetStringEqualityMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerEqualityMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(OctetStringEqualityMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      EqualityMatchingRule matchingRule =
           new ObjectIdentifierEqualityMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerEqualityMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(ObjectIdentifierEqualityMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      EqualityMatchingRule matchingRule =
           new TelephoneNumberEqualityMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerEqualityMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(TelephoneNumberEqualityMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      OrderingMatchingRule matchingRule = new CaseExactOrderingMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerOrderingMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(CaseExactOrderingMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      OrderingMatchingRule matchingRule = new CaseIgnoreOrderingMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerOrderingMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(CaseIgnoreOrderingMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      OrderingMatchingRule matchingRule =
           new GeneralizedTimeOrderingMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerOrderingMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(GeneralizedTimeOrderingMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      OrderingMatchingRule matchingRule = new IntegerOrderingMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerOrderingMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(IntegerOrderingMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      OrderingMatchingRule matchingRule = new OctetStringOrderingMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerOrderingMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(OctetStringOrderingMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      SubstringMatchingRule matchingRule = new CaseExactSubstringMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerSubstringMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(CaseExactSubstringMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      SubstringMatchingRule matchingRule =
           new CaseExactIA5SubstringMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerSubstringMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(CaseExactIA5SubstringMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      SubstringMatchingRule matchingRule =
           new CaseIgnoreSubstringMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerSubstringMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(CaseIgnoreSubstringMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      SubstringMatchingRule matchingRule =
           new CaseIgnoreIA5SubstringMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerSubstringMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(CaseIgnoreIA5SubstringMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      SubstringMatchingRule matchingRule =
           new OctetStringSubstringMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerSubstringMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(OctetStringSubstringMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      SubstringMatchingRule matchingRule =
           new TelephoneNumberSubstringMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerSubstringMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_MATCHING_RULE.
          get(TelephoneNumberSubstringMatchingRule.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }
  }



  /**
   * Registers a basic set of attribute syntaxes with the server that should
   * always be available regardless of the server configuration and may be
   * needed for configuration processing.
   */
  private void bootstrapAttributeSyntaxes()
  {
    try
    {
      AttributeTypeSyntax syntax = new AttributeTypeSyntax();
      syntax.initializeSyntax(null);
      registerAttributeSyntax(syntax, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_SYNTAX.get(
          AttributeTypeSyntax.class.getName(), stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      defaultBinarySyntax = new BinarySyntax();
      defaultBinarySyntax.initializeSyntax(null);
      registerAttributeSyntax(defaultBinarySyntax, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_SYNTAX.get(
          BinarySyntax.class.getName(), stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      defaultBooleanSyntax = new BooleanSyntax();
      defaultBooleanSyntax.initializeSyntax(null);
      registerAttributeSyntax(defaultBooleanSyntax, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_SYNTAX.get(
          BooleanSyntax.class.getName(), stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      defaultStringSyntax = new DirectoryStringSyntax();
      defaultStringSyntax.initializeSyntax(null);
      registerAttributeSyntax(defaultStringSyntax, true);
      defaultSyntax = defaultStringSyntax;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_SYNTAX.
          get(DirectoryStringSyntax.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      defaultDNSyntax = new DistinguishedNameSyntax();
      defaultDNSyntax.initializeSyntax(null);
      registerAttributeSyntax(defaultDNSyntax, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_SYNTAX.
          get(DistinguishedNameSyntax.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      IA5StringSyntax syntax = new IA5StringSyntax();
      syntax.initializeSyntax(null);
      registerAttributeSyntax(syntax, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_SYNTAX.get(
          IA5StringSyntax.class.getName(), stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      defaultIntegerSyntax = new IntegerSyntax();
      defaultIntegerSyntax.initializeSyntax(null);
      registerAttributeSyntax(defaultIntegerSyntax, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_SYNTAX.get(
          IntegerSyntax.class.getName(), stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      GeneralizedTimeSyntax syntax = new GeneralizedTimeSyntax();
      syntax.initializeSyntax(null);
      registerAttributeSyntax(syntax, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_SYNTAX.
          get(GeneralizedTimeSyntax.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      ObjectClassSyntax syntax = new ObjectClassSyntax();
      syntax.initializeSyntax(null);
      registerAttributeSyntax(syntax, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_SYNTAX.get(
          ObjectClassSyntax.class.getName(), stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      OIDSyntax syntax = new OIDSyntax();
      syntax.initializeSyntax(null);
      registerAttributeSyntax(syntax, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_SYNTAX.get(
          OIDSyntax.class.getName(), stackTraceToSingleLineString(e));
      logError(message);
    }


    try
    {
      TelephoneNumberSyntax syntax = new TelephoneNumberSyntax();
      syntax.initializeSyntax(null);
      registerAttributeSyntax(syntax, true);
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_BOOTSTRAP_SYNTAX.
          get(TelephoneNumberSyntax.class.getName(),
              stackTraceToSingleLineString(e));
      logError(message);
    }
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



  /**
   * Initializes the crypto manager for the Directory Server.
   *
   * @throws  ConfigException  If a configuration problem is identified while
   *                           initializing the crypto manager.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the crypto manager that is not related
   *                                   to the server configuration.
   */
  public void initializeCryptoManager()
         throws ConfigException, InitializationException
  {
    RootCfg root =
         ServerManagementContext.getInstance().getRootConfiguration();
    CryptoManagerCfg cryptoManagerCfg = root.getCryptoManager();
    cryptoManager = new CryptoManagerImpl(cryptoManagerCfg);
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
   * Indicates whether the Directory Server is configured with information about
   * one or more mail servers and may therefore be used to send e-mail messages.
   *
   * @return  {@code true} if the Directory Server is configured to be able to
   *          send e-mail messages, or {@code false} if not.
   */
  public static boolean mailServerConfigured()
  {
    return ((directoryServer.mailServerPropertySets != null) &&
            (! directoryServer.mailServerPropertySets.isEmpty()));
  }



  /**
   * Specifies the set of mail server properties that should be used for SMTP
   * communication.
   *
   * @param  mailServerPropertySets  A list of {@code Properties} objects that
   *                                 provide information that can be used to
   *                                 communicate with SMTP servers.
   */
  public static void setMailServerPropertySets(List<Properties>
                                                    mailServerPropertySets)
  {
    directoryServer.mailServerPropertySets = mailServerPropertySets;
  }



  /**
   * Retrieves the sets of information about the mail servers configured for use
   * by the Directory Server.
   *
   * @return  The sets of information about the mail servers configured for use
   *          by the Directory Server.
   */
  public static List<Properties> getMailServerPropertySets()
  {
    return directoryServer.mailServerPropertySets;
  }



  /**
   * Initializes the set of alert handlers defined in the Directory Server.
   *
   * @throws  ConfigException  If there is a configuration problem with any of
   *                           the alert handlers.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the alert handlers that is not related to
   *                                   the server configuration.
   */
  private void initializeAlertHandlers()
          throws ConfigException, InitializationException
  {
    new AlertHandlerConfigManager().initializeAlertHandlers();
  }




  /**
   * Initializes the schema elements for the Directory Server, including the
   * matching rules, attribute syntaxes, attribute types, and object classes.
   *
   * @throws  ConfigException  If there is a configuration problem with any of
   *                           the schema elements.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the schema elements that is not related
   *                                   to the server configuration.
   */
  public void initializeSchema()
         throws ConfigException, InitializationException
  {
    // Create the schema configuration manager, and initialize the schema from
    // the configuration.
    schemaConfigManager = new SchemaConfigManager();
    schema = schemaConfigManager.getSchema();

    schemaConfigManager.initializeMatchingRules();
    schemaConfigManager.initializeAttributeSyntaxes();
    schemaConfigManager.initializeSchemaFromFiles();

    // With server schema in place set compressed schema.
    compressedSchema = new DefaultCompressedSchema();

    // At this point we have a problem, because none of the configuration is
    // usable because it was all read before we had a schema (and therefore all
    // of the attribute types and objectclasses are bogus and won't let us find
    // anything).  So we have to re-read the configuration so that we can
    // continue the necessary startup process.  In the process, we want to
    // preserve any configuration add/delete/change listeners that might have
    // been registered with the old configuration (which will primarily be
    // schema elements) so they can be re-registered with the new configuration.
    LinkedHashMap<String,List<ConfigAddListener>> addListeners =
         new LinkedHashMap<String,List<ConfigAddListener>>();
    LinkedHashMap<String,List<ConfigDeleteListener>> deleteListeners =
         new LinkedHashMap<String,List<ConfigDeleteListener>>();
    LinkedHashMap<String,List<ConfigChangeListener>> changeListeners =
         new LinkedHashMap<String,List<ConfigChangeListener>>();
    getChangeListeners(configHandler.getConfigRootEntry(), addListeners,
                       deleteListeners, changeListeners);

    try
    {
      configHandler.finalizeConfigHandler();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }

    try
    {
      configHandler.initializeConfigHandler(configFile.getAbsolutePath(), true);
    }
    catch (InitializationException ie)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ie);
      }

      throw ie;
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message =
          ERR_CANNOT_INITIALIZE_CONFIG_HANDLER.get(
                  String.valueOf(configClass),
                  String.valueOf(configFile),
                  e.getLocalizedMessage());
      throw new InitializationException(message);
    }


    // Re-register all of the change listeners with the configuration.
    for (String dnStr : addListeners.keySet())
    {
      try
      {
        DN dn = DN.decode(dnStr);
        for (ConfigAddListener listener : addListeners.get(dnStr))
        {
          configHandler.getConfigEntry(dn).registerAddListener(listener);
        }
      }
      catch (DirectoryException de)
      {
        // This should never happen, so we'll just re-throw it.
        throw new InitializationException(de.getMessageObject());
      }
    }

    for (String dnStr : deleteListeners.keySet())
    {
      try
      {
        DN dn = DN.decode(dnStr);
        for (ConfigDeleteListener listener : deleteListeners.get(dnStr))
        {
          configHandler.getConfigEntry(dn).registerDeleteListener(listener);
        }
      }
      catch (DirectoryException de)
      {
        // This should never happen, so we'll just re-throw it.
        throw new InitializationException(de.getMessageObject());
      }
    }

    for (String dnStr : changeListeners.keySet())
    {
      try
      {
        DN dn = DN.decode(dnStr);
        for (ConfigChangeListener listener : changeListeners.get(dnStr))
        {
          configHandler.getConfigEntry(dn).registerChangeListener(listener);
        }
      }
      catch (DirectoryException de)
      {
        // This should never happen, so we'll just re-throw it.
        throw new InitializationException(de.getMessageObject());
      }
    }
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



  /**
   * Gets all of the add, delete, and change listeners from the provided
   * configuration entry and all of its descendants and puts them in the
   * appropriate lists.
   *
   * @param  configEntry      The configuration entry to be processed, along
   *                          with all of its descendants.
   * @param  addListeners     The set of add listeners mapped to the DN of the
   *                          corresponding configuration entry.
   * @param  deleteListeners  The set of delete listeners mapped to the DN of
   *                          the corresponding configuration entry.
   * @param  changeListeners  The set of change listeners mapped to the DN of
   *                          the corresponding configuration entry.
   */
  private void getChangeListeners(ConfigEntry configEntry,
       LinkedHashMap<String,List<ConfigAddListener>> addListeners,
       LinkedHashMap<String,List<ConfigDeleteListener>> deleteListeners,
       LinkedHashMap<String,List<ConfigChangeListener>> changeListeners)
  {
    CopyOnWriteArrayList<ConfigAddListener> cfgAddListeners =
         configEntry.getAddListeners();
    if ((cfgAddListeners != null) && (cfgAddListeners.size() > 0))
    {
      addListeners.put(configEntry.getDN().toString(), cfgAddListeners);
    }

    CopyOnWriteArrayList<ConfigDeleteListener> cfgDeleteListeners =
         configEntry.getDeleteListeners();
    if ((cfgDeleteListeners != null) && (cfgDeleteListeners.size() > 0))
    {
      deleteListeners.put(configEntry.getDN().toString(), cfgDeleteListeners);
    }

    CopyOnWriteArrayList<ConfigChangeListener> cfgChangeListeners =
         configEntry.getChangeListeners();
    if ((cfgChangeListeners != null) && (cfgChangeListeners.size() > 0))
    {
      changeListeners.put(configEntry.getDN().toString(), cfgChangeListeners);
    }

    for (ConfigEntry child : configEntry.getChildren().values())
    {
      getChangeListeners(child, addListeners, deleteListeners, changeListeners);
    }
  }



  /**
   * Retrieves the set of backend initialization listeners that have been
   * registered with the Directory Server.  The contents of the returned set
   * must not be altered.
   *
   * @return  The set of backend initialization listeners that have been
   *          registered with the Directory Server.
   */
  public static Set<BackendInitializationListener>
                     getBackendInitializationListeners()
  {
    return directoryServer.backendInitializationListeners;
  }



  /**
   * Registers the provided backend initialization listener with the Directory
   * Server.
   *
   * @param  listener  The backend initialization listener to register with the
   *                   Directory Server.
   */
  public static void registerBackendInitializationListener(
                          BackendInitializationListener listener)
  {
    directoryServer.backendInitializationListeners.add(listener);
  }



  /**
   * Deegisters the provided backend initialization listener with the Directory
   * Server.
   *
   * @param  listener  The backend initialization listener to deregister with
   *                   the Directory Server.
   */
  public static void deregisterBackendInitializationListener(
                          BackendInitializationListener listener)
  {
    directoryServer.backendInitializationListeners.remove(listener);
  }



  /**
   * Initializes the set of backends defined in the Directory Server.
   *
   * @throws  ConfigException  If there is a configuration problem with any of
   *                           the backends.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the backends that is not related to the
   *                                   server configuration.
   */
  private void initializeBackends()
          throws ConfigException, InitializationException
  {
    backendConfigManager = new BackendConfigManager();
    backendConfigManager.initializeBackendConfig();


    // Make sure to initialize the root DSE backend separately after all other
    // backends.
    RootDSEBackendCfg rootDSECfg;
    try
    {
      RootCfg root =
           ServerManagementContext.getInstance().getRootConfiguration();
      rootDSECfg = root.getRootDSEBackend();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_GET_ROOT_DSE_CONFIG_ENTRY.get(
          stackTraceToSingleLineString(e));
      throw new InitializationException(message, e);
    }

    rootDSEBackend = new RootDSEBackend();
    rootDSEBackend.configureBackend(rootDSECfg);
    rootDSEBackend.initializeBackend();
  }


  /**
   * Deregisters a workflow with the default network group and
   * deregisters the workflow with the server. This method is
   * intended to be called when workflow configuration mode is
   * auto.
   *
   * @param baseDN  the DN of the workflow to deregister
   */
  private static void deregisterWorkflowWithDefaultNetworkGroup(
      DN baseDN
      )
  {
    // Get the default network group and deregister all the workflows
    // being configured for the backend (there is one worklfow per
    // backend base DN).
    NetworkGroup defaultNetworkGroup = NetworkGroup.getDefaultNetworkGroup();
    Workflow workflow = defaultNetworkGroup.deregisterWorkflow(baseDN);
    WorkflowImpl workflowImpl = (WorkflowImpl) workflow;
    workflowImpl.deregister();
  }


  /**
   * Creates a set of workflows for a given backend and registers the
   * workflows with the default network group. There are as many workflows
   * as base DNs defined in the backend. This method is intended
   * to be called when workflow configuration mode is auto.
   *
   * @param backend  the backend handled by the workflow
   *
   * @throws  DirectoryException  If the workflow ID for the provided
   *                              workflow conflicts with the workflow
   *                              ID of an existing workflow.
   */
  public static void createAndRegisterWorkflowsWithDefaultNetworkGroup(
      Backend backend
      ) throws DirectoryException
  {
    // Create a worklfow for each backend base DN and register the workflow
    // with the default network group.
    for (DN curBaseDN: backend.getBaseDNs())
    {
      WorkflowImpl workflowImpl = createWorkflow(curBaseDN, backend);
      registerWorkflowWithDefaultNetworkGroup(workflowImpl);
    }
  }


  /**
   * Creates one workflow for a given base DN in a backend.
   *
   * @param baseDN   the base DN of the workflow to create
   * @param backend  the backend handled by the workflow
   *
   * @return the newly created workflow
   *
   * @throws  DirectoryException  If the workflow ID for the provided
   *                              workflow conflicts with the workflow
   *                              ID of an existing workflow.
   */
  public static WorkflowImpl createWorkflow(
      DN      baseDN,
      Backend backend
      ) throws DirectoryException
  {
    String backendID = backend.getBackendID();

    // Create a root workflow element to encapsulate the backend
    LocalBackendWorkflowElement rootWE =
        LocalBackendWorkflowElement.createAndRegister(backendID, backend);

    // The workflow ID is "backendID + baseDN".
    // We cannot use backendID as workflow identifier because a backend
    // may handle several base DNs. We cannot use baseDN either because
    // we might want to configure several workflows handling the same
    // baseDN through different network groups. So a mix of both
    // backendID and baseDN should be ok.
    String workflowID = backend.getBackendID() + "#" + baseDN.toString();

    // Create the worklfow for the base DN and register the workflow with
    // the server.
    WorkflowImpl workflowImpl = new WorkflowImpl(
        workflowID, baseDN, (WorkflowElement) rootWE);
    workflowImpl.register();

    return workflowImpl;
  }


  /**
   * Registers a workflow with the default network group. This method
   * is intended to be called when workflow configuration mode is auto.
   *
   * @param workflowImpl  The workflow to register with the
   *                      default network group
   *
   * @throws  DirectoryException  If the workflow is already registered with
   *                              the default network group
   */
  private static void registerWorkflowWithDefaultNetworkGroup(
      WorkflowImpl workflowImpl
      ) throws DirectoryException
  {
    NetworkGroup defaultNetworkGroup = NetworkGroup.getDefaultNetworkGroup();
    defaultNetworkGroup.registerWorkflow(workflowImpl);
  }


  /**
   * Creates the missing workflows, one for the config backend and one for
   * the rootDSE backend.
   *
   * This method should be invoked whatever may be the workflow
   * configuration mode because config backend and rootDSE backend
   * will not have any configuration section, ever.
   *
   * @throws  ConfigException  If there is a configuration problem with any of
   *                           the workflows.
   */
  private void createAndRegisterRemainingWorkflows()
      throws ConfigException
  {
    try
    {
      createAndRegisterWorkflowsWithDefaultNetworkGroup (configHandler);
      createAndRegisterWorkflowsWithDefaultNetworkGroup (rootDSEBackend);
    }
    catch (DirectoryException de)
    {
      throw new ConfigException(de.getMessageObject());
    }
  }


  /**
   * Reconfigures the workflows when configuration mode has changed.
   * This method is invoked when workflows need to be reconfigured
   * while the server is running. If the reconfiguration is valid
   * then the method update the workflow configuration mode.
   *
   * @param oldMode  the current workflow configuration mode
   * @param newMode  the new workflow configuration mode
   */
  public static void reconfigureWorkflows(
      WorkflowConfigurationMode oldMode,
      WorkflowConfigurationMode newMode)
  {
    if ((oldMode == WorkflowConfigurationMode.AUTO)
        && (newMode == WorkflowConfigurationMode.MANUAL))
    {
      // move to manual mode
      try
      {
        directoryServer.configureWorkflowsManual();
        setWorkflowConfigurationMode(newMode);
      }
      catch (Exception e)
      {
        // rollback to auto mode
        try
        {
           directoryServer.configureWorkflowsAuto();
        }
        catch (Exception ee)
        {
          // rollback to auto mode is failing too!!
          // well, just log an error message and suggest the admin
          // to restart the server with the last valid config...
          Message message = ERR_CONFIG_WORKFLOW_CANNOT_CONFIGURE_MANUAL.get();
          logError(message);
        }
      }
    }
    else if ((oldMode == WorkflowConfigurationMode.MANUAL)
        && (newMode == WorkflowConfigurationMode.AUTO))
    {
      // move to auto mode
      try
      {
        directoryServer.configureWorkflowsAuto();
        setWorkflowConfigurationMode(newMode);
      }
      catch (Exception e)
      {
        // rollback to manual mode
        try
        {
           directoryServer.configureWorkflowsManual();
        }
        catch (Exception ee)
        {
          // rollback to auto mode is failing too!!
          // well, just log an error message and suggest the admin
          // to restart the server with the last valid config...
          Message message = ERR_CONFIG_WORKFLOW_CANNOT_CONFIGURE_AUTO.get();
          logError(message);
        }
      }
    }
  }


  /**
   * Configures the workflows when configuration mode is manual.
   *
   * @throws  ConfigException  If there is a problem with the Directory Server
   *                           configuration that prevents a critical component
   *                           from being instantiated.
   *
   * @throws  InitializationException  If some other problem occurs while
   *                                   attempting to initialize and start the
   *                                   Directory Server.
   */
  private void configureWorkflowsManual()
      throws ConfigException, InitializationException
  {
    // First of all re-initialize the current workflow configuration
    NetworkGroup.resetConfig();
    WorkflowImpl.resetConfig();
    WorkflowElement.resetConfig();

    // Then configure the workflows
    workflowElementConfigManager = new WorkflowElementConfigManager();
    workflowElementConfigManager.initializeWorkflowElements();

    workflowConfigManager = new WorkflowConfigManager();
    workflowConfigManager.initializeWorkflows();

    networkGroupConfigManager = new NetworkGroupConfigManager();
    networkGroupConfigManager.initializeNetworkGroups();

    // We now need to complete the workflow creation for the
    // config backend and rootDSE backend.
    createAndRegisterRemainingWorkflows();
  }


  /**
   * Configures the workflows when configuration mode is auto.
   *
   * @throws  ConfigException  If there is a problem with the Directory Server
   *                           configuration that prevents a critical component
   *                           from being instantiated.
   */
  private void configureWorkflowsAuto() throws ConfigException
  {
    // First of all re-initialize the current workflow configuration
    NetworkGroup.resetConfig();
    WorkflowImpl.resetConfig();
    WorkflowElement.resetConfig();

    // For each base DN in a backend create a workflow and register
    // the workflow with the default network group
    Map<String, Backend> backends = getBackends();
    for (String backendID: backends.keySet())
    {
      Backend backend = backends.get(backendID);
      for (DN baseDN: backend.getBaseDNs())
      {
        WorkflowImpl workflowImpl;
        try
        {
          workflowImpl = createWorkflow(baseDN, backend);
          registerWorkflowWithDefaultNetworkGroup(workflowImpl);
        }
        catch (DirectoryException e)
        {
          // TODO Auto-generated catch block
          throw new ConfigException(e.getMessageObject());
        }
      }
    }

    // We now need to complete the workflow creation for the
    // config backend and rootDSE backend.
    createAndRegisterRemainingWorkflows();
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
  public void initializeGroupManager()
         throws ConfigException, InitializationException
  {
    groupManager = new GroupManager();
    groupManager.initializeGroupImplementations();

    // The configuration backend has already been registered by this point
    // so we need to handle it explicitly.
    groupManager.performBackendInitializationProcessing(configHandler);
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
   * Initializes the set of supported controls for the Directory Server.
   *
   * @throws  ConfigException  If there is a configuration problem with the
   *                           list of supported controls.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the set of supported controls that is not
   *                                   related to the server configuration.
   */
  private void initializeSupportedControls()
          throws ConfigException, InitializationException
  {
    supportedControls.add(OID_LDAP_ASSERTION);
    supportedControls.add(OID_LDAP_READENTRY_PREREAD);
    supportedControls.add(OID_LDAP_READENTRY_POSTREAD);
    supportedControls.add(OID_LDAP_NOOP_OPENLDAP_ASSIGNED);
    supportedControls.add(OID_PERSISTENT_SEARCH);
    supportedControls.add(OID_PROXIED_AUTH_V1);
    supportedControls.add(OID_PROXIED_AUTH_V2);
    supportedControls.add(OID_AUTHZID_REQUEST);
    supportedControls.add(OID_MATCHED_VALUES);
    supportedControls.add(OID_LDAP_SUBENTRIES);
    supportedControls.add(OID_PASSWORD_POLICY_CONTROL);
    supportedControls.add(OID_REAL_ATTRS_ONLY);
    supportedControls.add(OID_VIRTUAL_ATTRS_ONLY);
    supportedControls.add(OID_ACCOUNT_USABLE_CONTROL);
  }



  /**
   * Initializes the set of supported features for the Directory Server.
   *
   * @throws  ConfigException  If there is a configuration problem with the
   *                           list of supported features.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the set of supported features that is not
   *                                   related to the server configuration.
   */
  private void initializeSupportedFeatures()
          throws ConfigException, InitializationException
  {
    supportedFeatures.add(OID_ALL_OPERATIONAL_ATTRS_FEATURE);
    supportedFeatures.add(OID_MODIFY_INCREMENT_FEATURE);
    supportedFeatures.add(OID_TRUE_FALSE_FILTERS_FEATURE);
  }



  /**
   * Initializes the set of identity mappers for the Directory Server.
   *
   * @throws  ConfigException  If there is a configuration problem with any of
   *                           the extended operation handlers.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the extended operation handlers that is
   *                                   not related to the server configuration.
   */
  private void initializeIdentityMappers()
          throws ConfigException, InitializationException
  {
    identityMapperConfigManager = new IdentityMapperConfigManager();
    identityMapperConfigManager.initializeIdentityMappers();
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
    extendedOperationConfigManager = new ExtendedOperationConfigManager();
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
    saslConfigManager = new SASLConfigManager();
    saslConfigManager.initializeSASLMechanismHandlers();
  }



  /**
   * Initializes the set of virtual attributes that should be defined in the
   * Directory Server.
   *
   * @throws  ConfigException  If there is a configuration problem with any of
   *                           the virtual attribute handlers.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the virtual attribute handlers that is
   *                                   not related to the server configuration.
   */
  private void initializeVirtualAttributes()
          throws ConfigException, InitializationException
  {
    virtualAttributeConfigManager = new VirtualAttributeConfigManager();
    virtualAttributeConfigManager.initializeVirtualAttributes();
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
    connectionHandlerConfigManager = new ConnectionHandlerConfigManager();
    connectionHandlerConfigManager.initializeConnectionHandlerConfig();
  }



  /**
   * Initializes the set of password policy components for use by the Directory
   * Server.
   *
   * @throws  ConfigException  If there is a configuration problem with any of
   *                           the password policy components.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the password policy components that is
   *                                   not related to the server configuration.
   */
  public void initializePasswordPolicyComponents()
         throws ConfigException, InitializationException
  {
    // Initialize all the password storage schemes.
    storageSchemeConfigManager = new PasswordStorageSchemeConfigManager();
    storageSchemeConfigManager.initializePasswordStorageSchemes();


    // Initialize all the password validators.
    passwordValidatorConfigManager = new PasswordValidatorConfigManager();
    passwordValidatorConfigManager.initializePasswordValidators();


    // Initialize all the password generators.
    passwordGeneratorConfigManager = new PasswordGeneratorConfigManager();
    passwordGeneratorConfigManager.initializePasswordGenerators();


    // Initialize the account status notification handlers.
    accountStatusNotificationHandlerConfigManager =
         new AccountStatusNotificationHandlerConfigManager();
    accountStatusNotificationHandlerConfigManager.
         initializeNotificationHandlers();


    // Initialize all the password policies.
    passwordPolicyConfigManager = new PasswordPolicyConfigManager();
    passwordPolicyConfigManager.initializePasswordPolicies();
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



  /**
   * Retrieves the thread group that should be used by all threads associated
   * with the Directory Server.
   *
   * @return  The thread group that should be used by all threads associated
   *          with the Directory Server.
   */
  public static ThreadGroup getDirectoryThreadGroup()
  {
    return directoryServer.directoryThreadGroup;
  }



  /**
   * Retrieves a reference to the Directory Server configuration handler.
   *
   * @return  A reference to the Directory Server configuration handler.
   */
  public static ConfigHandler getConfigHandler()
  {
    return directoryServer.configHandler;
  }



  /**
   * Initializes the set of plugins defined in the Directory Server.
   *
   * @throws  ConfigException  If there is a configuration problem with any of
   *                           the Directory Server plugins.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the plugins that is not related to the
   *                                   server configuration.
   */
  public void initializePlugins()
         throws ConfigException, InitializationException
  {
    pluginConfigManager.initializePluginConfig(null);
  }



  /**
   * Initializes the set of plugins defined in the Directory Server.  Only the
   * specified types of plugins will be initialized.
   *
   * @param  pluginTypes  The set of plugin types for the plugins to
   *                      initialize.
   *
   * @throws  ConfigException  If there is a configuration problem with any of
   *                           the Directory Server plugins.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the plugins that is not related to the
   *                                   server configuration.
   */
  public void initializePlugins(Set<PluginType> pluginTypes)
         throws ConfigException, InitializationException
  {
    pluginConfigManager = new PluginConfigManager();
    pluginConfigManager.initializePluginConfig(pluginTypes);
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
   * Retrieves the requested entry from the Directory Server configuration.
   *
   * @param  entryDN  The DN of the configuration entry to retrieve.
   *
   * @return  The requested entry from the Directory Server configuration.
   *
   * @throws  ConfigException  If a problem occurs while trying to retrieve the
   *                           requested entry.
   */
  public static ConfigEntry getConfigEntry(DN entryDN)
         throws ConfigException
  {
    return directoryServer.configHandler.getConfigEntry(entryDN);
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
    if (directoryServer.configHandler == null)
    {
      File serverRoot = directoryServer.environmentConfig.getServerRoot();
      if (serverRoot != null)
      {
        return serverRoot.getAbsolutePath();
      }

      // We don't know where the server root is, so we'll have to assume it's
      // the current working directory.
      return System.getProperty("user.dir");
    }
    else
    {
      return directoryServer.configHandler.getServerRoot();
    }
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
   * Retrieves a reference to the Directory Server schema.
   *
   * @return  A reference to the Directory Server schema.
   */
  public static Schema getSchema()
  {
    return directoryServer.schema;
  }



  /**
   * Replaces the Directory Server schema with the provided schema.
   *
   * @param  schema  The new schema to use for the Directory Server.
   */
  public static void setSchema(Schema schema)
  {
    directoryServer.schema = schema;
  }



  /**
   * Retrieves a list of modifications detailing any schema changes that may
   * have been made with the server offline (e.g., by directly editing the
   * schema configuration files).  Note that this information will not be
   * available until the server backends (and in particular, the schema backend)
   * have been initialized.
   *
   * @return  A list of modifications detailing any schema changes that may have
   *          been made with the server offline, or an empty list if no offline
   *          schema changes have been detected.
   */
  public static List<Modification> getOfflineSchemaChanges()
  {
    return directoryServer.offlineSchemaChanges;
  }



  /**
   * Specifies a list of modifications detailing any schema changes that may
   * have been made with the server offline.
   *
   * @param  offlineSchemaChanges  A list of modifications detailing any schema
   *                               changes that may have been made with the
   *                               server offline.  It must not be {@code null}.
   */
  public static void setOfflineSchemaChanges(List<Modification>
                                                  offlineSchemaChanges)
  {
    ensureNotNull(offlineSchemaChanges);

    directoryServer.offlineSchemaChanges = offlineSchemaChanges;
  }



  /**
   * Retrieves the set of matching rules registered with the Directory Server.
   * The mapping will be between the lowercase name or OID for each matching
   * rule and the matching rule implementation.  The same matching rule instance
   * may be included multiple times with different keys.
   *
   * @return  The set of matching rules registered with the Directory Server.
   */
  public static ConcurrentHashMap<String,MatchingRule> getMatchingRules()
  {
    return directoryServer.schema.getMatchingRules();
  }



  /**
   * Retrieves the set of encoded matching rules that have been defined in the
   * Directory Server.
   *
   * @return  The set of encoded matching rules that have been defined in the
   *          Directory Server.
   */
  public static LinkedHashSet<AttributeValue> getMatchingRuleSet()
  {
    return directoryServer.schema.getMatchingRuleSet();
  }



  /**
   * Retrieves the matching rule with the specified name or OID.
   *
   * @param  lowerName  The lowercase name or OID for the matching rule to
   *                    retrieve.
   *
   * @return  The requested matching rule, or <CODE>null</CODE> if no such
   *          matching rule has been defined in the server.
   */
  public static MatchingRule getMatchingRule(String lowerName)
  {
    return directoryServer.schema.getMatchingRule(lowerName);
  }



  /**
   * Registers the provided matching rule with the Directory Server.
   *
   * @param  matchingRule       The matching rule to register with the server.
   * @param  overwriteExisting  Indicates whether to overwrite an existing
   *                            mapping if there are any conflicts (i.e.,
   *                            another matching rule with the same OID or
   *                            name).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag is set to
   *                              <CODE>false</CODE>
   */
  public static void registerMatchingRule(MatchingRule matchingRule,
                                          boolean overwriteExisting)
         throws DirectoryException
  {
    directoryServer.schema.registerMatchingRule(matchingRule,
                                                overwriteExisting);
  }



  /**
   * Deregisters the provided matching rule with the Directory Server.
   *
   * @param  matchingRule  The matching rule to deregister with the server.
   */
  public static void deregisterMatchingRule(MatchingRule matchingRule)
  {
    directoryServer.schema.deregisterMatchingRule(matchingRule);
  }



  /**
   * Retrieves the set of approximate matching rules registered with the
   * Directory Server.  The mapping will be between the lowercase name or OID
   * for each approximate matching rule and the matching rule implementation.
   * The same approximate matching rule instance may be included multiple times
   * with different keys.
   *
   * @return  The set of approximate matching rules registered with the
   *          Directory Server.
   */
  public static ConcurrentHashMap<String,ApproximateMatchingRule>
                     getApproximateMatchingRules()
  {
    return directoryServer.schema.getApproximateMatchingRules();
  }



  /**
   * Retrieves the approximate matching rule with the specified name or OID.
   *
   * @param  lowerName  The lowercase name or OID for the approximate matching
   *                    rule to retrieve.
   *
   * @return  The requested approximate matching rule, or <CODE>null</CODE> if
   *          no such matching rule has been defined in the server.
   */
  public static ApproximateMatchingRule
                     getApproximateMatchingRule(String lowerName)
  {
    return directoryServer.schema.getApproximateMatchingRule(lowerName);
  }



  /**
   * Registers the provided approximate matching rule with the Directory
   * Server.
   *
   * @param  matchingRule       The matching rule to register with the server.
   * @param  overwriteExisting  Indicates whether to overwrite an existing
   *                            mapping if there are any conflicts (i.e.,
   *                            another matching rule with the same OID or
   *                            name).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag is set to
   *                              <CODE>false</CODE>
   */
  public static void registerApproximateMatchingRule(ApproximateMatchingRule
                                                          matchingRule,
                                                     boolean overwriteExisting)
         throws DirectoryException
  {
    directoryServer.schema.registerApproximateMatchingRule(matchingRule,
                                                           overwriteExisting);
  }



  /**
   * Deregisters the provided approximate matching rule with the Directory
   * Server.
   *
   * @param  matchingRule  The matching rule to deregister with the server.
   */
  public static void deregisterApproximateMatchingRule(ApproximateMatchingRule
                                                            matchingRule)
  {
    directoryServer.schema.deregisterApproximateMatchingRule(matchingRule);
  }



  /**
   * Retrieves the set of equality matching rules registered with the Directory
   * Server.  The mapping will be between the lowercase name or OID for each
   * equality matching rule and the matching rule implementation. The same
   * equality matching rule instance may be included multiple times with
   * different keys.
   *
   * @return  The set of equality matching rules registered with the Directory
   *          Server.
   */
  public static ConcurrentHashMap<String,EqualityMatchingRule>
                     getEqualityMatchingRules()
  {
    return directoryServer.schema.getEqualityMatchingRules();
  }



  /**
   * Retrieves the equality matching rule with the specified name or OID.
   *
   * @param  lowerName  The lowercase name or OID for the equality matching rule
   *                    to retrieve.
   *
   * @return  The requested equality matching rule, or <CODE>null</CODE> if no
   *          such matching rule has been defined in the server.
   */
  public static EqualityMatchingRule getEqualityMatchingRule(String lowerName)
  {
    return directoryServer.schema.getEqualityMatchingRule(lowerName);
  }



  /**
   * Registers the provided equality matching rule with the Directory Server.
   *
   * @param  matchingRule       The matching rule to register with the server.
   * @param  overwriteExisting  Indicates whether to overwrite an existing
   *                            mapping if there are any conflicts (i.e.,
   *                            another matching rule with the same OID or
   *                            name).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag is set to
   *                              <CODE>false</CODE>
   */
  public static void registerEqualityMatchingRule(EqualityMatchingRule
                                                       matchingRule,
                                                  boolean overwriteExisting)
         throws DirectoryException
  {
    directoryServer.schema.registerEqualityMatchingRule(matchingRule,
                                                        overwriteExisting);
  }



  /**
   * Deregisters the provided equality matching rule with the Directory Server.
   *
   * @param  matchingRule  The matching rule to deregister with the server.
   */
  public static void deregisterEqualityMatchingRule(EqualityMatchingRule
                                                    matchingRule)
  {
    directoryServer.schema.deregisterEqualityMatchingRule(matchingRule);
  }



  /**
   * Retrieves the set of ordering matching rules registered with the Directory
   * Server.  The mapping will be between the lowercase name or OID for each
   * ordering matching rule and the matching rule implementation. The same
   * ordering matching rule instance may be included multiple times with
   * different keys.
   *
   * @return  The set of ordering matching rules registered with the Directory
   *          Server.
   */
  public static ConcurrentHashMap<String,OrderingMatchingRule>
                     getOrderingMatchingRules()
  {
    return directoryServer.schema.getOrderingMatchingRules();
  }



  /**
   * Retrieves the ordering matching rule with the specified name or OID.
   *
   * @param  lowerName  The lowercase name or OID for the ordering matching rule
   *                    to retrieve.
   *
   * @return  The requested ordering matching rule, or <CODE>null</CODE> if no
   *          such matching rule has been defined in the server.
   */
  public static OrderingMatchingRule getOrderingMatchingRule(String lowerName)
  {
    return directoryServer.schema.getOrderingMatchingRule(lowerName);
  }



  /**
   * Registers the provided ordering matching rule with the Directory Server.
   *
   * @param  matchingRule       The matching rule to register with the server.
   * @param  overwriteExisting  Indicates whether to overwrite an existing
   *                            mapping if there are any conflicts (i.e.,
   *                            another matching rule with the same OID or
   *                            name).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag is set to
   *                              <CODE>false</CODE>
   */
  public static void registerOrderingMatchingRule(OrderingMatchingRule
                                                       matchingRule,
                                                  boolean overwriteExisting)
         throws DirectoryException
  {
    directoryServer.schema.registerOrderingMatchingRule(matchingRule,
                                                        overwriteExisting);
  }



  /**
   * Deregisters the provided ordering matching rule with the Directory Server.
   *
   * @param  matchingRule  The matching rule to deregister with the server.
   */
  public static void deregisterOrderingMatchingRule(OrderingMatchingRule
                                                    matchingRule)
  {
    directoryServer.schema.deregisterOrderingMatchingRule(matchingRule);
  }



  /**
   * Retrieves the set of substring matching rules registered with the Directory
   * Server.  The mapping will be between the lowercase name or OID for each
   * substring matching rule and the matching rule implementation.  The same
   * substring matching rule instance may be included multiple times with
   * different keys.
   *
   * @return  The set of substring matching rules registered with the Directory
   *          Server.
   */
  public static ConcurrentHashMap<String,SubstringMatchingRule>
                     getSubstringMatchingRules()
  {
    return directoryServer.schema.getSubstringMatchingRules();
  }



  /**
   * Retrieves the substring matching rule with the specified name or OID.
   *
   * @param  lowerName  The lowercase name or OID for the substring matching
   *                    rule to retrieve.
   *
   * @return  The requested substring matching rule, or <CODE>null</CODE> if no
   *          such matching rule has been defined in the server.
   */
  public static SubstringMatchingRule getSubstringMatchingRule(String lowerName)
  {
    return directoryServer.schema.getSubstringMatchingRule(lowerName);
  }



  /**
   * Registers the provided substring matching rule with the Directory Server.
   *
   * @param  matchingRule       The matching rule to register with the server.
   * @param  overwriteExisting  Indicates whether to overwrite an existing
   *                            mapping if there are any conflicts (i.e.,
   *                            another matching rule with the same OID or
   *                            name).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag is set to
   *                              <CODE>false</CODE>
   */
  public static void registerSubstringMatchingRule(SubstringMatchingRule
                                                        matchingRule,
                                                   boolean overwriteExisting)
         throws DirectoryException
  {
    directoryServer.schema.registerSubstringMatchingRule(matchingRule,
                                                         overwriteExisting);
  }



  /**
   * Deregisters the provided substring matching rule with the Directory Server.
   *
   * @param  matchingRule  The matching rule to deregister with the server.
   */
  public static void deregisterSubstringMatchingRule(SubstringMatchingRule
                                                     matchingRule)
  {
    directoryServer.schema.deregisterSubstringMatchingRule(matchingRule);
  }



  /**
   * Retrieves the set of objectclasses defined in the Directory Server.
   *
   * @return  The set of objectclasses defined in the Directory Server.
   */
  public static ConcurrentHashMap<String,ObjectClass> getObjectClasses()
  {
    return directoryServer.schema.getObjectClasses();
  }



  /**
   * Retrieves the set of encoded objectclasses that have been defined in the
   * Directory Server.
   *
   * @return  The set of encoded objectclasses that have been defined in the
   *          Directory Server.
   */
  public static LinkedHashSet<AttributeValue> getObjectClassSet()
  {
    return directoryServer.schema.getObjectClassSet();
  }



  /**
   * Retrieves the objectclass for the provided lowercase name or OID.
   *
   * @param  lowerName  The lowercase name or OID for the objectclass to
   *                    retrieve.
   *
   * @return  The requested objectclass, or <CODE>null</CODE> if there is no
   *          such objectclass defined in the server schema.
   */
  public static ObjectClass getObjectClass(String lowerName)
  {
    return directoryServer.schema.getObjectClass(lowerName);
  }



  /**
   * Retrieves the objectclass for the provided lowercase name or OID.  It can
   * optionally return a generated "default" version if the requested
   * objectclass is not defined in the schema.
   *
   * @param  lowerName      The lowercase name or OID for the objectclass to
   *                        retrieve.
   * @param  returnDefault  Indicates whether to generate a default version if
   *                        the requested objectclass is not defined in the
   *                        server schema.
   *
   * @return  The objectclass type, or <CODE>null</CODE> if there is no
   *          objectclass with the specified name or OID defined in the server
   *          schema and a default class should not be returned.
   */
  public static ObjectClass getObjectClass(String lowerName,
                                           boolean returnDefault)
  {
    ObjectClass oc = directoryServer.schema.getObjectClass(lowerName);
    if (returnDefault && (oc == null))
    {
      oc = getDefaultObjectClass(lowerName);
    }

    return oc;
  }



  /**
   * Registers the provided objectclass with the Directory Server.
   *
   * @param  objectClass        The objectclass instance to register with the
   *                            server.
   * @param  overwriteExisting  Indicates whether to overwrite an existing
   *                            mapping if there are any conflicts (i.e.,
   *                            another objectclass with the same OID or
   *                            name).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag is set to
   *                              <CODE>false</CODE>
   */
  public static void registerObjectClass(ObjectClass objectClass,
                                         boolean overwriteExisting)
         throws DirectoryException
  {
    directoryServer.schema.registerObjectClass(objectClass, overwriteExisting);
  }



  /**
   * Deregisters the provided objectclass with the Directory Server.
   *
   * @param  objectClass  The objectclass instance to deregister with the
   *                      server.
   */
  public static void deregisterObjectClass(ObjectClass objectClass)
  {
    directoryServer.schema.deregisterObjectClass(objectClass);
  }



  /**
   * Retrieves the "top" objectClass, which should be the topmost objectclass in
   * the inheritance chain for most other objectclasses.  If no such objectclass
   * could be found, then one will be constructed.
   *
   * @return  The "top" objectClass.
   */
  public static ObjectClass getTopObjectClass()
  {
    ObjectClass objectClass =
         directoryServer.schema.getObjectClass(TOP_OBJECTCLASS_NAME);
    if (objectClass == null)
    {
      String definition =
           "( 2.5.6.0 NAME 'top' ABSTRACT MUST objectClass " +
           "X-ORIGIN 'RFC 2256' )";

      objectClass = new ObjectClass(definition, TOP_OBJECTCLASS_NAME,
                                    Collections.singleton(TOP_OBJECTCLASS_NAME),
                                    TOP_OBJECTCLASS_OID,
                                    TOP_OBJECTCLASS_DESCRIPTION, null, null,
                                    null, ObjectClassType.ABSTRACT, false,
                                    null);
    }

    return objectClass;
  }



  /**
   * Causes the Directory Server to construct a new objectclass
   * definition with the provided name and with no required or allowed
   * attributes. This should only be used if there is no objectclass
   * for the specified name. It will not register the created
   * objectclass with the Directory Server.
   *
   * @param name
   *          The name to use for the objectclass, as provided by the
   *          user.
   * @return The constructed objectclass definition.
   */
  public static ObjectClass getDefaultObjectClass(String name)
  {
    String lowerName = toLowerCase(name);
    ObjectClass objectClass = directoryServer.schema.getObjectClass(lowerName);
    if (objectClass == null)
    {
      String oid        = lowerName + "-oid";
      String definition = "( " + oid + " NAME '" + name + "' ABSTRACT )";

      objectClass = new ObjectClass(definition, name,
                                    Collections.singleton(name), oid, null,
                                    getTopObjectClass(), null, null,
                                    ObjectClassType.STRUCTURAL, false, null);
    }

    return objectClass;
  }



  /**
   * Causes the Directory Server to construct a new auxiliary objectclass
   * definition with the provided name and with no required or allowed
   * attributes. This should only be used if there is no objectclass for the
   * specified name. It will not register the created objectclass with the
   * Directory Server.
   *
   * @param  name  The name to use for the objectclass, as provided by the user.
   *
   * @return  The constructed objectclass definition.
   */
  public static ObjectClass getDefaultAuxiliaryObjectClass(String name)
  {
    String lowerName = toLowerCase(name);
    ObjectClass objectClass = directoryServer.schema.getObjectClass(lowerName);
    if (objectClass == null)
    {
      String oid        = lowerName + "-oid";
      String definition = "( " + oid + " NAME '" + name + "' ABSTRACT )";

      objectClass = new ObjectClass(definition, name,
                                    Collections.singleton(name), oid, null,
                                    getTopObjectClass(), null, null,
                                    ObjectClassType.AUXILIARY, false, null);
    }

    return objectClass;
  }



  /**
   * Retrieves the set of attribute type definitions that have been
   * defined in the Directory Server.
   *
   * @return The set of attribute type definitions that have been
   *         defined in the Directory Server.
   */
  public static ConcurrentHashMap<String,AttributeType> getAttributeTypes()
  {
    return directoryServer.schema.getAttributeTypes();
  }



  /**
   * Retrieves the set of encoded attribute types that have been defined in the
   * Directory Server.
   *
   * @return  The set of encoded attribute types that have been defined in the
   *          Directory Server.
   */
  public static LinkedHashSet<AttributeValue> getAttributeTypeSet()
  {
    return directoryServer.schema.getAttributeTypeSet();
  }



  /**
   * Retrieves the attribute type for the provided lowercase name or OID.
   *
   * @param  lowerName  The lowercase attribute name or OID for the attribute
   *                    type to retrieve.
   *
   * @return  The requested attribute type, or <CODE>null</CODE> if there is no
   *          attribute with the specified type defined in the server schema.
   */
  public static AttributeType getAttributeType(String lowerName)
  {
    return directoryServer.schema.getAttributeType(lowerName);
  }



  /**
   * Retrieves the attribute type for the provided lowercase name or OID.  It
   * can optionally return a generated "default" version if the requested
   * attribute type is not defined in the schema.
   *
   * @param  lowerName      The lowercase name or OID for the attribute type to
   *                        retrieve.
   * @param  returnDefault  Indicates whether to generate a default version if
   *                        the requested attribute type is not defined in the
   *                        server schema.
   *
   * @return  The requested attribute type, or <CODE>null</CODE> if there is no
   *          attribute with the specified type defined in the server schema and
   *          a default type should not be returned.
   */
  public static AttributeType getAttributeType(String lowerName,
                                               boolean returnDefault)
  {
    AttributeType type = directoryServer.schema.getAttributeType(lowerName);
    if (returnDefault && (type == null))
    {
      type = getDefaultAttributeType(lowerName);
    }

    return type;
  }



  /**
   * Registers the provided attribute type with the Directory Server.
   *
   * @param  attributeType      The attribute type to register with the
   *                            Directory Server.
   * @param  overwriteExisting  Indicates whether to overwrite an existing
   *                            mapping if there are any conflicts (i.e.,
   *                            another attribute type with the same OID or
   *                            name).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag is set to
   *                              <CODE>false</CODE>
   */
  public static void registerAttributeType(AttributeType attributeType,
                                           boolean overwriteExisting)
         throws DirectoryException
  {
    directoryServer.schema.registerAttributeType(attributeType,
                                                 overwriteExisting);
  }



  /**
   * Deregisters the provided attribute type with the Directory Server.
   *
   * @param  attributeType  The attribute type to deregister with the Directory
   *                        Server.
   */
  public static void deregisterAttributeType(AttributeType attributeType)
  {
    directoryServer.schema.deregisterAttributeType(attributeType);
  }



  /**
   * Retrieves the attribute type for the "objectClass" attribute.
   *
   * @return  The attribute type for the "objectClass" attribute.
   */
  public static AttributeType getObjectClassAttributeType()
  {
    if (directoryServer.objectClassAttributeType == null)
    {
      directoryServer.objectClassAttributeType =
           directoryServer.schema.getAttributeType(
                OBJECTCLASS_ATTRIBUTE_TYPE_NAME);

      if (directoryServer.objectClassAttributeType == null)
      {
        AttributeSyntax oidSyntax =
             directoryServer.schema.getSyntax(SYNTAX_OID_NAME);
        if (oidSyntax == null)
        {
          try
          {
            OIDSyntax newOIDSyntax = new OIDSyntax();
            newOIDSyntax.initializeSyntax(null);
            oidSyntax = newOIDSyntax;
            directoryServer.schema.registerSyntax(oidSyntax, true);
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
          }
        }

        String definition =
             "( 2.5.4.0 NAME 'objectClass' EQUALITY objectIdentifierMatch " +
             "SYNTAX 1.3.6.1.4.1.1466.115.121.1.38 X-ORIGIN 'RFC 2256' )";

        directoryServer.objectClassAttributeType =
             new AttributeType(definition, "objectClass",
                               Collections.singleton("objectClass"),
                               OBJECTCLASS_ATTRIBUTE_TYPE_OID, null, null,
                               oidSyntax, AttributeUsage.USER_APPLICATIONS,
                               false, false, false, false);
        try
        {
          directoryServer.schema.registerAttributeType(
                 directoryServer.objectClassAttributeType, true);
        }
        catch (Exception e)
        {
          // This should never happen.
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
    }

    return directoryServer.objectClassAttributeType;
  }



  /**
   * Causes the Directory Server to construct a new attribute type definition
   * with the provided name and using the default attribute syntax.  This should
   * only be used if there is no real attribute type for the specified name.
   *
   * @param  name  The name to use for the attribute type, as provided by the
   *               user.
   *
   * @return  The constructed attribute type definition.
   */
  public static AttributeType getDefaultAttributeType(String name)
  {
    return getDefaultAttributeType(name, getDefaultAttributeSyntax());
  }



  /**
   * Causes the Directory Server to construct a new attribute type definition
   * with the provided name and syntax.  This should only be used if there is no
   * real attribute type for the specified name.
   *
   * @param  name    The name to use for the attribute type, as provided by the
   *                 user.
   * @param  syntax  The syntax to use for the attribute type.
   *
   * @return  The constructed attribute type definition.
   */
  public static AttributeType getDefaultAttributeType(String name,
                                                      AttributeSyntax syntax)
  {
    String oid        = toLowerCase(name) + "-oid";
    String definition = "( " + oid + " NAME '" + name + "' SYNTAX " +
                        syntax.getOID() + " )";

    return new AttributeType(definition, name, Collections.singleton(name),
                             oid, null, null, syntax,
                             AttributeUsage.USER_APPLICATIONS, false, false,
                             false, false);
  }



  /**
   * Retrieves the set of attribute syntaxes defined in the Directory Server.
   *
   * @return  The set of attribute syntaxes defined in the Directory Server.
   */
  public static ConcurrentHashMap<String,AttributeSyntax> getAttributeSyntaxes()
  {
    return directoryServer.schema.getSyntaxes();
  }



  /**
   * Retrieves the set of encoded attribute syntaxes that have been defined in
   * the Directory Server.
   *
   * @return  The set of encoded attribute syntaxes that have been defined in
   *          the Directory Server.
   */
  public static LinkedHashSet<AttributeValue> getAttributeSyntaxSet()
  {
    return directoryServer.schema.getSyntaxSet();
  }



  /**
   * Retrieves the requested attribute syntax.
   *
   * @param  oid           The OID of the syntax to retrieve.
   * @param  allowDefault  Indicates whether to return the default attribute
   *                       syntax if the requested syntax is unknown.
   *
   * @return  The requested attribute syntax, the default syntax if the
   *          requested syntax is unknown and the caller has indicated that the
   *          default is acceptable, or <CODE>null</CODE> otherwise.
   */
  public static AttributeSyntax getAttributeSyntax(String oid,
                                                   boolean allowDefault)
  {
    AttributeSyntax syntax = directoryServer.schema.getSyntax(oid);
    if ((syntax == null) && allowDefault)
    {
      return getDefaultAttributeSyntax();
    }

    return syntax;
  }



  /**
   * Registers the provided attribute syntax with the Directory Server.
   *
   * @param  syntax             The attribute syntax to register with the
   *                            Directory Server.
   * @param  overwriteExisting  Indicates whether to overwrite an existing
   *                            mapping if there are any conflicts (i.e.,
   *                            another attribute syntax with the same OID or
   *                            name).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag is set to
   *                              <CODE>false</CODE>
   */
  public static void registerAttributeSyntax(AttributeSyntax syntax,
                                             boolean overwriteExisting)
         throws DirectoryException
  {
    directoryServer.schema.registerSyntax(syntax, overwriteExisting);
  }



  /**
   * Deregisters the provided attribute syntax with the Directory Server.
   *
   * @param  syntax  The attribute syntax to deregister with the Directory
   *                 Server.
   */
  public static void deregisterAttributeSyntax(AttributeSyntax syntax)
  {
    directoryServer.schema.deregisterSyntax(syntax);
  }



  /**
   * Retrieves the default attribute syntax that should be used for attributes
   * that are not defined in the server schema.
   *
   * @return  The default attribute syntax that should be used for attributes
   *          that are not defined in the server schema.
   */
  public static AttributeSyntax getDefaultAttributeSyntax()
  {
    return directoryServer.defaultSyntax;
  }



  /**
   * Retrieves the default attribute syntax that should be used for attributes
   * that are not defined in the server schema and are meant to store binary
   * values.
   *
   * @return  The default attribute syntax that should be used for attributes
   *          that are not defined in the server schema and are meant to store
   *          binary values.
   */
  public static AttributeSyntax getDefaultBinarySyntax()
  {
    return directoryServer.defaultBinarySyntax;
  }



  /**
   * Retrieves the default attribute syntax that should be used for attributes
   * that are not defined in the server schema and are meant to store Boolean
   * values.
   *
   * @return  The default attribute syntax that should be used for attributes
   *          that are not defined in the server schema and are meant to store
   *          Boolean values.
   */
  public static AttributeSyntax getDefaultBooleanSyntax()
  {
    return directoryServer.defaultBooleanSyntax;
  }



  /**
   * Retrieves the default attribute syntax that should be used for attributes
   * that are not defined in the server schema and are meant to store DN values.
   *
   * @return  The default attribute syntax that should be used for attributes
   *          that are not defined in the server schema and are meant to store
   *          DN values.
   */
  public static AttributeSyntax getDefaultDNSyntax()
  {
    return directoryServer.defaultDNSyntax;
  }



  /**
   * Retrieves the default attribute syntax that should be used for attributes
   * that are not defined in the server schema and are meant to store integer
   * values.
   *
   * @return  The default attribute syntax that should be used for attributes
   *          that are not defined in the server schema and are meant to store
   *          integer values.
   */
  public static AttributeSyntax getDefaultIntegerSyntax()
  {
    return directoryServer.defaultIntegerSyntax;
  }



  /**
   * Retrieves the default attribute syntax that should be used for attributes
   * that are not defined in the server schema and are meant to store string
   * values.
   *
   * @return  The default attribute syntax that should be used for attributes
   *          that are not defined in the server schema and are meant to store
   *          string values.
   */
  public static AttributeSyntax getDefaultStringSyntax()
  {
    return directoryServer.defaultStringSyntax;
  }



  /**
   * Retrieves the set of matching rule uses defined in the Directory Server.
   *
   * @return  The set of matching rule uses defined in the Directory Server.
   */
  public static ConcurrentHashMap<MatchingRule,MatchingRuleUse>
                     getMatchingRuleUses()
  {
    return directoryServer.schema.getMatchingRuleUses();
  }



  /**
   * Retrieves the set of encoded matching rule uses that have been defined in
   * the Directory Server.
   *
   * @return  The set of encoded matching rule uses that have been defined in
   *          the Directory Server.
   */
  public static LinkedHashSet<AttributeValue> getMatchingRuleUseSet()
  {
    return directoryServer.schema.getMatchingRuleUseSet();
  }



  /**
   * Retrieves the matching rule use associated with the provided matching rule.
   *
   * @param  matchingRule  The matching rule for which to retrieve the matching
   *                       rule use.
   *
   * @return  The matching rule use for the provided matching rule, or
   *          <CODE>null</CODE> if none is defined.
   */
  public static MatchingRuleUse getMatchingRuleUse(MatchingRule matchingRule)
  {
    return directoryServer.schema.getMatchingRuleUse(matchingRule);
  }



  /**
   * Registers the provided matching rule use with the Directory Server.
   *
   * @param  matchingRuleUse    The matching rule use to register with the
   *                            server.
   * @param  overwriteExisting  Indicates whether to overwrite an existing
   *                            mapping if there are any conflicts (i.e.,
   *                            another matching rule use with the same matching
   *                            rule).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag is set to
   *                              <CODE>false</CODE>
   */
  public static void registerMatchingRuleUse(MatchingRuleUse matchingRuleUse,
                                             boolean overwriteExisting)
         throws DirectoryException
  {
    directoryServer.schema.registerMatchingRuleUse(matchingRuleUse,
                                                   overwriteExisting);
  }



  /**
   * Deregisters the provided matching rule use with the Directory Server.
   *
   * @param  matchingRuleUse  The matching rule use to deregister with the
   *                          server.
   */
  public static void deregisterMatchingRuleUse(MatchingRuleUse matchingRuleUse)
  {
    directoryServer.schema.deregisterMatchingRuleUse(matchingRuleUse);
  }



  /**
   * Retrieves the set of DIT content rules defined in the Directory Server.
   *
   * @return  The set of DIT content rules defined in the Directory Server.
   */
  public static ConcurrentHashMap<ObjectClass,DITContentRule>
                     getDITContentRules()
  {
    return directoryServer.schema.getDITContentRules();
  }



  /**
   * Retrieves the set of encoded DIT content rules that have been defined in
   * the Directory Server.
   *
   * @return  The set of encoded DIT content rules that have been defined in the
   *          Directory Server.
   */
  public static LinkedHashSet<AttributeValue> getDITContentRuleSet()
  {
    return directoryServer.schema.getDITContentRuleSet();
  }



  /**
   * Retrieves the DIT content rule associated with the specified objectclass.
   *
   * @param  objectClass  The objectclass for which to retrieve the associated
   *                      DIT content rule.
   *
   * @return  The requested DIT content rule, or <CODE>null</CODE> if no such
   *          rule is defined in the schema.
   */
  public static DITContentRule getDITContentRule(ObjectClass objectClass)
  {
    return directoryServer.schema.getDITContentRule(objectClass);
  }



  /**
   * Registers the provided DIT content rule with the Directory Server.
   *
   * @param  ditContentRule     The DIT content rule to register with the
   *                            server.
   * @param  overwriteExisting  Indicates whether to overwrite an existing
   *                            mapping if there are any conflicts (i.e.,
   *                            another DIT content rule with the same
   *                            structural objectclass).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag is set to
   *                              <CODE>false</CODE>
   */
  public static void registerDITContentRule(DITContentRule ditContentRule,
                                            boolean overwriteExisting)
         throws DirectoryException
  {
    directoryServer.schema.registerDITContentRule(ditContentRule,
                                                  overwriteExisting);
  }



  /**
   * Deregisters the provided DIT content rule with the Directory Server.
   *
   * @param  ditContentRule  The DIT content rule to deregister with the server.
   */
  public static void deregisterDITContentRule(DITContentRule ditContentRule)
  {
    directoryServer.schema.deregisterDITContentRule(ditContentRule);
  }



  /**
   * Retrieves the set of DIT structure rules defined in the Directory Server.
   *
   * @return  The set of DIT structure rules defined in the Directory Server.
   */
  public static ConcurrentHashMap<NameForm,DITStructureRule>
                     getDITStructureRules()
  {
    return directoryServer.schema.getDITStructureRulesByNameForm();
  }



  /**
   * Retrieves the set of encoded DIT structure rules that have been defined in
   * the Directory Server.
   *
   * @return  The set of encoded DIT structure rules that have been defined in
   *          the Directory Server.
   */
  public static LinkedHashSet<AttributeValue> getDITStructureRuleSet()
  {
    return directoryServer.schema.getDITStructureRuleSet();
  }



  /**
   * Retrieves the DIT structure rule associated with the provided rule ID.
   *
   * @param  ruleID  The rule ID for which to retrieve the associated DIT
   *                 structure rule.
   *
   * @return  The requested DIT structure rule, or <CODE>null</CODE> if no such
   *          rule is defined.
   */
  public static DITStructureRule getDITStructureRule(int ruleID)
  {
    return directoryServer.schema.getDITStructureRule(ruleID);
  }



  /**
   * Retrieves the DIT structure rule associated with the provided name form.
   *
   * @param  nameForm  The name form for which to retrieve the associated DIT
   *                   structure rule.
   *
   * @return  The requested DIT structure rule, or <CODE>null</CODE> if no such
   *          rule is defined.
   */
  public static DITStructureRule getDITStructureRule(NameForm nameForm)
  {
    return directoryServer.schema.getDITStructureRule(nameForm);
  }



  /**
   * Registers the provided DIT structure rule with the Directory Server.
   *
   * @param  ditStructureRule   The DIT structure rule to register with the
   *                            server.
   * @param  overwriteExisting  Indicates whether to overwrite an existing
   *                            mapping if there are any conflicts (i.e.,
   *                            another DIT structure rule with the same name
   *                            form).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag is set to
   *                              <CODE>false</CODE>
   */
  public static void registerDITStructureRule(DITStructureRule ditStructureRule,
                                              boolean overwriteExisting)
         throws DirectoryException
  {
    directoryServer.schema.registerDITStructureRule(ditStructureRule,
                                                    overwriteExisting);
  }



  /**
   * Deregisters the provided DIT structure rule with the Directory Server.
   *
   * @param  ditStructureRule  The DIT structure rule to deregister with the
   *                           server.
   */
  public static void deregisterDITStructureRule(DITStructureRule
                                                     ditStructureRule)
  {
    directoryServer.schema.deregisterDITStructureRule(ditStructureRule);
  }



  /**
   * Retrieves the set of name forms defined in the Directory Server.
   *
   * @return  The set of name forms defined in the Directory Server.
   */
  public static ConcurrentHashMap<ObjectClass,NameForm> getNameForms()
  {
    return directoryServer.schema.getNameFormsByObjectClass();
  }



  /**
   * Retrieves the set of encoded name forms that have been defined in the
   * Directory Server.
   *
   * @return  The set of encoded name forms that have been defined in the
   *          Directory Server.
   */
  public static LinkedHashSet<AttributeValue> getNameFormSet()
  {
    return directoryServer.schema.getNameFormSet();
  }



  /**
   * Retrieves the name form associated with the specified objectclass.
   *
   * @param  objectClass  The objectclass for which to retrieve the associated
   *                      name form.
   *
   * @return  The requested name form, or <CODE>null</CODE> if no such name form
   *          is defined in the schema.
   */
  public static NameForm getNameForm(ObjectClass objectClass)
  {
    return directoryServer.schema.getNameForm(objectClass);
  }



  /**
   * Retrieves the name form associated with the specified name or OID.
   *
   * @param  lowerName  The name or OID of the name form to retrieve, formatted
   *                    in all lowercase characters.
   *
   * @return  The requested name form, or <CODE>null</CODE> if no such name form
   *          is defined in the schema.
   */
  public static NameForm getNameForm(String lowerName)
  {
    return directoryServer.schema.getNameForm(lowerName);
  }



  /**
   * Registers the provided name form with the Directory Server.
   *
   * @param  nameForm           The name form to register with the server.
   * @param  overwriteExisting  Indicates whether to overwrite an existing
   *                            mapping if there are any conflicts (i.e.,
   *                            another name form with the same structural
   *                            objectclass).
   *
   * @throws  DirectoryException  If a conflict is encountered and the
   *                              <CODE>overwriteExisting</CODE> flag is set to
   *                              <CODE>false</CODE>
   */
  public static void registerNameForm(NameForm nameForm,
                                      boolean overwriteExisting)
         throws DirectoryException
  {
    directoryServer.schema.registerNameForm(nameForm, overwriteExisting);
  }



  /**
   * Deregisters the provided name form with the Directory Server.
   *
   * @param  nameForm  The name form to deregister with the server.
   */
  public static void deregisterNameForm(NameForm nameForm)
  {
    directoryServer.schema.deregisterNameForm(nameForm);
  }



  /**
   * Retrieves the set of virtual attribute rules registered with the Directory
   * Server.
   *
   * @return  The set of virtual attribute rules registered with the Directory
   *          Server.
   */
  public static List<VirtualAttributeRule> getVirtualAttributes()
  {
    return directoryServer.virtualAttributes;
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
    LinkedList<VirtualAttributeRule> ruleList =
         new LinkedList<VirtualAttributeRule>();

    for (VirtualAttributeRule rule : directoryServer.virtualAttributes)
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
  public static void registerVirtualAttribute(VirtualAttributeRule rule)
  {
    synchronized (directoryServer.virtualAttributes)
    {
      directoryServer.virtualAttributes.add(rule);
    }
  }



  /**
   * Deregisters the provided virtual attribute rule with the Directory Server.
   *
   * @param  rule  The virutal attribute rule to be deregistered.
   */
  public static void deregisterVirtualAttribute(VirtualAttributeRule rule)
  {
    synchronized (directoryServer.virtualAttributes)
    {
      directoryServer.virtualAttributes.remove(rule);
    }
  }



  /**
   * Replaces the specified virtual attribute rule in the set of virtual
   * attributes registered with the Directory Server.  If the old rule cannot
   * be found in the list, then the set of registered virtual attributes is not
   * updated.
   *
   * @param  oldRule  The existing rule that should be replaced with the new
   *                  rule.
   * @param  newRule  The new rule that should be used in place of the existing
   *                  rule.
   *
   * @return  {@code true} if the old rule was found and replaced with the new
   *          version, or {@code false} if it was not.
   */
  public static boolean replaceVirtualAttribute(VirtualAttributeRule oldRule,
                                                VirtualAttributeRule newRule)
  {
    synchronized (directoryServer.virtualAttributes)
    {
      int pos = directoryServer.virtualAttributes.indexOf(oldRule);
      if (pos >= 0)
      {
        directoryServer.virtualAttributes.set(pos, newRule);
        return true;
      }
      else
      {
        return false;
      }
    }
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
  public static ConcurrentHashMap<DN,JMXMBean> getJMXMBeans()
  {
    return directoryServer.mBeans;
  }



  /**
   * Retrieves the JMX MBean associated with the specified entry in the
   * Directory Server configuration.
   *
   * @param  configEntryDN  The DN of the configuration entry for which to
   *                        retrieve the associated JMX MBean.
   *
   * @return  The JMX MBean associated with the specified entry in the Directory
   *          Server configuration, or <CODE>null</CODE> if there is no MBean
   *          for the specified entry.
   */
  public static JMXMBean getJMXMBean(DN configEntryDN)
  {
    return directoryServer.mBeans.get(configEntryDN);
  }



  /**
   * Registers the provided invokable component with the Directory Server.
   *
   * @param  component  The invokable component to register.
   */
  public static void registerInvokableComponent(InvokableComponent component)
  {
    DN componentDN = component.getInvokableComponentEntryDN();
    JMXMBean mBean = directoryServer.mBeans.get(componentDN);
    if (mBean == null)
    {
      mBean = new JMXMBean(componentDN);
      mBean.addInvokableComponent(component);
      directoryServer.mBeans.put(componentDN, mBean);
    }
    else
    {
      mBean.addInvokableComponent(component);
    }
  }



  /**
   * Deregisters the provided invokable component with the Directory Server.
   *
   * @param  component  The invokable component to deregister.
   */
  public static void deregisterInvokableComponent(InvokableComponent component)
  {
    DN componentDN = component.getInvokableComponentEntryDN();
    JMXMBean mBean = directoryServer.mBeans.get(componentDN);
    if (mBean != null)
    {
      mBean.removeInvokableComponent(component);
    }
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
  public static CopyOnWriteArrayList<AlertHandler> getAlertHandlers()
  {
    return directoryServer.alertHandlers;
  }



  /**
   * Registers the provided alert handler with the Directory Server.
   *
   * @param  alertHandler  The alert handler to register.
   */
  public static void registerAlertHandler(AlertHandler alertHandler)
  {
    directoryServer.alertHandlers.add(alertHandler);
  }



  /**
   * Deregisters the provided alert handler with the Directory Server.
   *
   * @param  alertHandler  The alert handler to deregister.
   */
  public static void deregisterAlertHandler(AlertHandler alertHandler)
  {
    directoryServer.alertHandlers.remove(alertHandler);
  }



  /**
   * Sends an alert notification with the provided information.
   *
   * @param  generator     The alert generator that created the alert.
   * @param  alertType     The alert type name for this alert.
   * @param  alertMessage  A message (possibly <CODE>null</CODE>) that can
   */
  public static void sendAlertNotification(AlertGenerator generator,
                                           String alertType,
                                           Message alertMessage)
  {
    if ((directoryServer.alertHandlers == null) ||
        directoryServer.alertHandlers.isEmpty())
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
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }
      }
    }
    else
    {
      for (AlertHandler alertHandler : directoryServer.alertHandlers)
      {
        AlertHandlerCfg config = alertHandler.getAlertHandlerConfiguration();
        Set<String> enabledAlerts = config.getEnabledAlertType();
        Set<String> disabledAlerts = config.getDisabledAlertType();
        if ((enabledAlerts == null) || enabledAlerts.isEmpty())
        {
          if ((disabledAlerts != null) && disabledAlerts.contains(alertType))
          {
            continue;
          }
        }
        else
        {
          if (enabledAlerts.contains(alertType))
          {
            if ((disabledAlerts != null) && disabledAlerts.contains(alertType))
            {
              continue;
            }
          }
          else
          {
            continue;
          }
        }

        alertHandler.sendAlertNotification(generator, alertType, alertMessage);
      }
    }


    Message message = NOTE_SENT_ALERT_NOTIFICATION.get(
        generator.getClassName(), alertType,
            alertMessage != null ?
                    String.valueOf(alertMessage.getDescriptor().getId()) :
                    String.valueOf(MessageDescriptor.NULL_ID),
            alertMessage);
    logError(message);
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
  public static PasswordStorageScheme getPasswordStorageScheme(DN configEntryDN)
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
  public static ConcurrentHashMap<String,PasswordStorageScheme>
                     getPasswordStorageSchemes()
  {
    return directoryServer.passwordStorageSchemes;
  }



  /**
   * Retrieves the specified password storage scheme.
   *
   * @param  lowerName  The name of the password storage scheme to retrieve,
   *                    formatted in all lowercase characters.
   *
   * @return  The requested password storage scheme, or <CODE>null</CODE> if no
   *          such scheme is defined.
   */
  public static PasswordStorageScheme getPasswordStorageScheme(String lowerName)
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
  public static ConcurrentHashMap<String,PasswordStorageScheme>
                     getAuthPasswordStorageSchemes()
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
   *          <CODE>null</CODE> if no such scheme is defined.
   */
  public static PasswordStorageScheme getAuthPasswordStorageScheme(String name)
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
  public static void registerPasswordStorageScheme(DN configEntryDN,
                                                   PasswordStorageScheme scheme)
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
    PasswordStorageScheme scheme =
         directoryServer.passwordStorageSchemesByDN.remove(configEntryDN);

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
   * Retrieves the set of password validators that have been registered for use
   * with the Directory Server as a mapping between the DN of the associated
   * validator configuration entry and the validator implementation.
   *
   * @return  The set of password validators that have been registered for use
   *          with the Directory Server.
   */
  public static
       ConcurrentHashMap<DN,
            PasswordValidator<? extends PasswordValidatorCfg>>
            getPasswordValidators()
  {
    return directoryServer.passwordValidators;
  }



  /**
   * Retrieves the password validator registered with the provided configuration
   * entry DN.
   *
   * @param  configEntryDN  The DN of the configuration entry for which to
   *                        retrieve the associated password validator.
   *
   * @return  The requested password validator, or <CODE>null</CODE> if no such
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
   * Retrieves the set of account status notification handlers defined in the
   * Directory Server, as a mapping between the DN of the configuration entry
   * and the notification handler implementation.
   *
   * @return  The set of account status notification handlers defined in the
   *          Directory Server.
   */
  public static ConcurrentHashMap<DN,AccountStatusNotificationHandler>
                     getAccountStatusNotificationHandlers()
  {
    return directoryServer.accountStatusNotificationHandlers;
  }



  /**
   * Retrieves the account status notification handler with the specified
   * configuration entry DN.
   *
   * @param  handlerDN  The DN of the configuration entry associated with the
   *                    account status notification handler to retrieve.
   *
   * @return  The requested account status notification handler, or
   *          <CODE>null</CODE> if no such handler is defined in the server.
   */
  public static AccountStatusNotificationHandler
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
                          AccountStatusNotificationHandler handler)
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
   * Retrieves the set of password generators that have been registered for use
   * with the Directory Server as a mapping between the DN of the associated
   * generator configuration entry and the generator implementation.
   *
   * @return  The set of password generators that have been registered for use
   *          with the Directory Server.
   */
  public static ConcurrentHashMap<DN,PasswordGenerator> getPasswordGenerators()
  {
    return directoryServer.passwordGenerators;
  }



  /**
   * Retrieves the password generator registered with the provided configuration
   * entry DN.
   *
   * @param  configEntryDN  The DN of the configuration entry for which to
   *                        retrieve the associated password generator.
   *
   * @return  The requested password generator, or <CODE>null</CODE> if no such
   *          generator is defined.
   */
  public static PasswordGenerator getPasswordGenerator(DN configEntryDN)
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
  public static void registerPasswordGenerator(DN configEntryDN,
                                               PasswordGenerator generator)
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
   * Retrieves the set of password policies registered with the Directory
   * Server. The references returned are to the actual password policy objects
   * currently in use by the directory server and the referenced objects must
   * not be modified.
   *
   * @return  The set of password policies registered with the Directory Server.
   */
  public static PasswordPolicy[] getPasswordPolicies()
  {
    // The password policy objects are returned in an array to prevent the
    // caller from modifying the map structure.
    PasswordPolicyConfig[] values = directoryServer.passwordPolicies.values()
                                          .toArray(new PasswordPolicyConfig[0]);
    PasswordPolicy[] policies = new PasswordPolicy[values.length];
    for( int i = 0 ; i < values.length; ++i)
    {
      policies[i] = values[i].getPolicy();
    }

    return policies;
  }



  /**
   * Retrieves the password policy registered for the provided configuration
   * entry.
   *
   * @param  configEntryDN  The DN of the configuration entry for which to
   *                        retrieve the associated password policy.
   *
   * @return  The password policy registered for the provided configuration
   *          entry, or <CODE>null</CODE> if there is no such policy.
   */
  public static PasswordPolicy getPasswordPolicy(DN configEntryDN)
  {
    Validator.ensureNotNull(configEntryDN);

    PasswordPolicyConfig config
            = directoryServer.passwordPolicies.get(configEntryDN);
    return (null == config) ? null : config.getPolicy();
  }


  /**
   * Retrieves the password policy registered for the provided configuration
   * entry.
   *
   * @param  configEntryDN  The DN of the configuration entry for which to
   *                        retrieve the associated password policy.
   *
   * @return  The password policy config registered for the provided
   *          configuration entry, or <CODE>null</CODE> if there is
   *          no such policy.
   */
  public static PasswordPolicyConfig getPasswordPolicyConfig(DN configEntryDN)
  {
    Validator.ensureNotNull(configEntryDN);

    return directoryServer.passwordPolicies.get(configEntryDN);
  }


  /**
   * Registers the provided password policy with the Directory Server.  If a
   * policy is already registered for the provided configuration entry DN, then
   * it will be replaced.
   *
   * @param  configEntryDN  The DN of the configuration entry that defines the
   *                        password policy.
   * @param  config         The password policy config to register with the
   *                        server.
   */
  public static void registerPasswordPolicy(DN configEntryDN,
                                            PasswordPolicyConfig config)
  {
    Validator.ensureNotNull(configEntryDN, config);

    directoryServer.passwordPolicies.put(configEntryDN, config);
  }



  /**
   * Deregisters the provided password policy with the Directory Server.  If no
   * such policy is registered, then no action will be taken.
   *
   * @param  configEntryDN  The DN of the configuration entry that defines the
   *                        password policy to deregister.
   */
  public static void deregisterPasswordPolicy(DN configEntryDN)
  {
    Validator.ensureNotNull(configEntryDN);

    if (directoryServer.defaultPasswordPolicyDN.equals(configEntryDN))
    {
      directoryServer.defaultPasswordPolicyConfig = null;
    }

    PasswordPolicyConfig config
            = directoryServer.passwordPolicies.remove(configEntryDN);
  }



  /**
   * Retrieves the DN of the configuration entry for the default password policy
   * for the Directory Server.
   *
   * @return  The DN of the configuration entry for the default password policy
   *          for the Directory Server.
   */
  public static DN getDefaultPasswordPolicyDN()
  {
    return directoryServer.defaultPasswordPolicyDN;
  }



  /**
   * Specifies the DN of the configuration entry for the default password policy
   * for the Directory Server. This routine does not check the registered
   * password policies for the specified DN, since in the case of server
   * initialization, the password policy entries will not yet have been loaded
   * from the configuration backend.
   *
   * @param  defaultPasswordPolicyDN  The DN of the configuration entry for the
   *                                  default password policy for the Directory
   *                                  Server.
   */
  public static void setDefaultPasswordPolicyDN(DN defaultPasswordPolicyDN)
  {
    directoryServer.defaultPasswordPolicyDN = defaultPasswordPolicyDN;
    directoryServer.defaultPasswordPolicyConfig = null;
  }



  /**
   * Retrieves the default password policy for the Directory Server. This method
   * is equivalent to invoking <CODE>getPasswordPolicy</CODE> on the DN returned
   * from <CODE>DirectoryServer.getDefaultPasswordPolicyDN()</CODE>.
   *
   * @return  The default password policy for the Directory Server.
   */
  public static PasswordPolicy getDefaultPasswordPolicy()
  {
    assert null != directoryServer.passwordPolicies.get(
                                       directoryServer.defaultPasswordPolicyDN)
            : "Internal Error: no default password policy defined." ;

    if ((directoryServer.defaultPasswordPolicyConfig == null) &&
        (directoryServer.defaultPasswordPolicyDN != null))
    {
      directoryServer.defaultPasswordPolicyConfig =
           directoryServer.passwordPolicies.get(
                                       directoryServer.defaultPasswordPolicyDN);
    }
    assert directoryServer.passwordPolicies.get(
                                       directoryServer.defaultPasswordPolicyDN)
                == directoryServer.defaultPasswordPolicyConfig
           : "Internal Error: inconsistency between defaultPasswordPolicyConfig"
             + " cache and value in passwordPolicies map.";
    return directoryServer.defaultPasswordPolicyConfig.getPolicy();
  }


  /**
   * Retrieves the log rotation policy registered for the provided configuration
   * entry.
   *
   * @param  configEntryDN  The DN of the configuration entry for which to
   *                        retrieve the associated rotation policy.
   *
   * @return  The rotation policy registered for the provided configuration
   *          entry, or <CODE>null</CODE> if there is no such policy.
   */
  public static RotationPolicy getRotationPolicy(DN configEntryDN)
  {
    Validator.ensureNotNull(configEntryDN);

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
  public static void registerRotationPolicy(DN configEntryDN,
                                            RotationPolicy policy)
  {
    Validator.ensureNotNull(configEntryDN, policy);

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
    Validator.ensureNotNull(configEntryDN);

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
   *          entry, or <CODE>null</CODE> if there is no such policy.
   */
  public static RetentionPolicy getRetentionPolicy(DN configEntryDN)
  {
    Validator.ensureNotNull(configEntryDN);

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
  public static void registerRetentionPolicy(DN configEntryDN,
                                            RetentionPolicy policy)
  {
    Validator.ensureNotNull(configEntryDN, policy);

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
    Validator.ensureNotNull(configEntryDN);

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
  public static ConcurrentHashMap<String,
                                  MonitorProvider<? extends MonitorProviderCfg>>
                     getMonitorProviders()
  {
    return directoryServer.monitorProviders;
  }



  /**
   * Retrieves the monitor provider with the specified name.
   *
   * @param  lowerName  The name of the monitor provider to retrieve, in all
   *                    lowercase characters.
   *
   * @return  The requested resource monitor, or <CODE>null</CODE> if none
   *          exists with the specified name.
   */
  public static MonitorProvider<? extends MonitorProviderCfg>
                     getMonitorProvider(String lowerName)
  {
    return directoryServer.monitorProviders.get(lowerName);
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
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
    }
  }



  /**
   * Deregisters the specified monitor provider from the Directory Server.  If
   * no such monitor provider is registered, no action will be taken.
   *
   * @param  lowerName  The name of the monitor provider to deregister, in all
   *                    lowercase characters.
   */
  public static void deregisterMonitorProvider(String lowerName)
  {
    MonitorProvider provider =
         directoryServer.monitorProviders.remove(toLowerCase(lowerName));


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
        }
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
   * Retrieves the entry cache for the Directory Server.
   *
   * @return  The entry cache for the Directory Server.
   */
  public static EntryCache getEntryCache()
  {
    return directoryServer.entryCache;
  }



  /**
   * Specifies the entry cache that should be used by the Directory Server.
   * This should only be called by the entry cache configuration manager.
   *
   * @param  entryCache  The entry cache for the Directory Server.
   */
  public static void setEntryCache(EntryCache entryCache)
  {
    synchronized (directoryServer)
    {
      directoryServer.entryCache = entryCache;
    }
  }



  /**
   * Retrieves the set of key manager providers registered with the Directory
   * Server.
   *
   * @return  The set of key manager providers registered with the Directory
   *          Server.
   */
  public static Map<DN,KeyManagerProvider> getKeyManagerProviders()
  {
    return directoryServer.keyManagerProviders;
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
  public static KeyManagerProvider getKeyManagerProvider(DN providerDN)
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
                                                KeyManagerProvider provider)
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
   * Retrieves the set of trust manager providers registered with the Directory
   * Server.
   *
   * @return  The set of trust manager providers registered with the Directory
   *          Server.
   */
  public static Map<DN,TrustManagerProvider> getTrustManagerProviders()
  {
    return directoryServer.trustManagerProviders;
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
  public static TrustManagerProvider getTrustManagerProvider(DN providerDN)
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
  public static void registerTrustManagerProvider(DN providerDN,
                                                  TrustManagerProvider provider)
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
   * Retrieves the set of certificate mappers registered with the Directory
   * Server.
   *
   * @return  The set of certificate mappers registered with the Directory
   *          Server.
   */
  public static Map<DN,CertificateMapper> getCertificateMappers()
  {
    return directoryServer.certificateMappers;
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
  public static CertificateMapper getCertificateMapper(DN mapperDN)
  {
    return directoryServer.certificateMappers.get(mapperDN);
  }



  /**
   * Registers the provided certificate mapper with the Directory Server.
   *
   * @param  mapperDN  The DN with which to register the certificate mapper.
   * @param  mapper    The certificate mapper to register with the server.
   */
  public static void registerCertificateMapper(DN mapperDN,
                                               CertificateMapper mapper)
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
  public static CopyOnWriteArraySet<DN> getRootDNs()
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
   * Retrieves the set of alternate bind DNs for root users, mapped between the
   * alternate DN and the real DN.  The contents of the returned map must not be
   * altered by the caller.
   *
   * @return  The set of alternate bind DNs for root users, mapped between the
   *          alternate DN and the real DN.
   */
  public static ConcurrentHashMap<DN,DN> getAlternateRootBindDNs()
  {
    return directoryServer.alternateRootBindDNs;
  }



  /**
   * Retrieves the real entry DN for the root user with the provided alternate
   * bind DN.
   *
   * @param  alternateRootBindDN  The alternate root bind DN for which to
   *                              retrieve the real entry DN.
   *
   * @return  The real entry DN for the root user with the provided alternate
   *          bind DN, or <CODE>null</CODE> if no such mapping has been defined.
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
    if ((existingRootEntryDN != null) &&
        (! existingRootEntryDN.equals(actualRootEntryDN)))
    {
      Message message = ERR_CANNOT_REGISTER_DUPLICATE_ALTERNATE_ROOT_BIND_DN.
          get(String.valueOf(alternateRootBindDN),
              String.valueOf(existingRootEntryDN));
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
   *          was mapped, or <CODE>null</CODE> if there was no mapping for the
   *          provided DN.
   */
  public static DN deregisterAlternateRootBindDN(DN alternateRootBindDN)
  {
    return directoryServer.alternateRootBindDNs.remove(alternateRootBindDN);
  }



  /**
   * Retrieves the result code that should be used when the Directory Server
   * encounters an internal server error.
   *
   * @return  The result code that should be used when the Directory Server
   *          encounters an internal server error.
   */
  public static ResultCode getServerErrorResultCode()
  {
    return directoryServer.serverErrorResultCode;
  }



  /**
   * Specifies the result code that should be used when the Directory Server
   * encounters an internal server error.
   *
   * @param  serverErrorResultCode  The result code that should be used when the
   *                                Directory Server encounters an internal
   *                                server error.
   */
  public static void setServerErrorResultCode(ResultCode serverErrorResultCode)
  {
    directoryServer.serverErrorResultCode = serverErrorResultCode;
  }



  /**
   * Indicates whether the Directory Server should automatically add missing RDN
   * attributes to an entry whenever it is added.
   *
   * @return  <CODE>true</CODE> if the Directory Server should automatically add
   *          missing RDN attributes to an entry, or <CODE>false</CODE> if it
   *          should return an error to the client.
   */
  public static boolean addMissingRDNAttributes()
  {
    return directoryServer.addMissingRDNAttributes;
  }



  /**
   * Specifies whether the Directory Server should automatically add missing RDN
   * attributes to an entry whenever it is added.
   *
   * @param  addMissingRDNAttributes  Specifies whether the Directory Server
   *                                  should automatically add missing RDN
   *                                  attributes to an entry whenever it is
   *                                  added.
   */
  public static void setAddMissingRDNAttributes(boolean addMissingRDNAttributes)
  {
    directoryServer.addMissingRDNAttributes = addMissingRDNAttributes;
  }



  /**
   * Indicates whether to be more flexible in the set of characters allowed for
   * attribute names.  The standard requires that only ASCII alphabetic letters,
   * numeric digits, and hyphens be allowed, and that the name start with a
   * letter.  If attribute name exceptions are enabled, then underscores will
   * also be allowed, and the name will be allowed to start with a digit.
   *
   * @return  <CODE>true</CODE> if the server should use a more flexible
   *          syntax for attribute names, or <CODE>false</CODE> if not.
   */
  public static boolean allowAttributeNameExceptions()
  {
    return directoryServer.allowAttributeNameExceptions;
  }



  /**
   * Specifies whether to be more flexible in the set of characters allowed for
   * attribute names.
   *
   * @param  allowAttributeNameExceptions  Specifies whether to be more flexible
   *                                       in the set of characters allowed for
   *                                       attribute names.
   */
  public static void setAllowAttributeNameExceptions(
                          boolean allowAttributeNameExceptions)
  {
    directoryServer.allowAttributeNameExceptions = allowAttributeNameExceptions;
  }



  /**
   * Indicates whether the Directory Server should perform schema checking.
   *
   * @return  <CODE>true</CODE> if the Directory Server should perform schema
   *          checking, or <CODE>false</CODE> if not.
   */
  public static boolean checkSchema()
  {
    return directoryServer.checkSchema;
  }



  /**
   * Specifies whether the Directory Server should perform schema checking.
   *
   * @param  checkSchema  Specifies whether the Directory Server should perform
   *                      schema checking.
   */
  public static void setCheckSchema(boolean checkSchema)
  {
    directoryServer.checkSchema = checkSchema;
  }



  /**
   * Retrieves the policy that should be used regarding enforcement of a single
   * structural objectclass per entry.
   *
   * @return  The policy that should be used regarding enforcement of a single
   *          structural objectclass per entry.
   */
  public static AcceptRejectWarn getSingleStructuralObjectClassPolicy()
  {
    return directoryServer.singleStructuralClassPolicy;
  }



  /**
   * Specifies the policy that should be used regarding enforcement of a single
   * structural objectclass per entry.
   *
   * @param  singleStructuralClassPolicy  The policy that should be used
   *                                      regarding enforcement of a single
   *                                      structural objectclass per entry.
   */
  public static void setSingleStructuralObjectClassPolicy(
                          AcceptRejectWarn singleStructuralClassPolicy)
  {
    directoryServer.singleStructuralClassPolicy = singleStructuralClassPolicy;
  }



  /**
   * Retrieves the policy that should be used when an attribute value is found
   * that is not valid according to the associated attribute syntax.
   *
   * @return  The policy that should be used when an attribute value is found
   *          that is not valid according to the associated attribute syntax.
   */
  public static AcceptRejectWarn getSyntaxEnforcementPolicy()
  {
    return directoryServer.syntaxEnforcementPolicy;
  }



  /**
   * Retrieves the policy that should be used when an attribute value is found
   * that is not valid according to the associated attribute syntax.
   *
   * @param  syntaxEnforcementPolicy  The policy that should be used when an
   *                                  attribute value is found that is not valid
   *                                  according to the associated attribute
   *                                  syntax.
   */
  public static void setSyntaxEnforcementPolicy(
                          AcceptRejectWarn syntaxEnforcementPolicy)
  {
    directoryServer.syntaxEnforcementPolicy = syntaxEnforcementPolicy;
  }



  /**
   * Indicates whether the Directory Server should send a response to an
   * operation that has been abandoned.  Sending such a response is technically
   * a violation of the LDAP protocol specification, but not doing so in that
   * case can cause problems with clients that are expecting a response and may
   * hang until they get one.
   *
   * @return  <CODE>true</CODE> if the Directory Server should send a response
   *          to an operation that has been abandoned, or <CODE>false</CODE> if
   *          not.
   */
  public static boolean notifyAbandonedOperations()
  {
    return directoryServer.notifyAbandonedOperations;
  }



  /**
   * Specifies whether the Directory Server should send a response to an
   * operation that has been abandoned.  Sending such a response is technically
   * a violation of the LDAP protocol specification, but not doing so in that
   * case can cause problems with clients that are expecting a response and may
   * hang until they get one.
   *
   * @param  notifyAbandonedOperations  Indicates whether the Directory Server
   *                                    should send a response to an operation
   *                                    that has been abandoned.
   */
  public static void setNotifyAbandonedOperations(
                          boolean notifyAbandonedOperations)
  {
    directoryServer.notifyAbandonedOperations = notifyAbandonedOperations;
  }



  /**
   * Retrieves the set of backends that have been registered with the Directory
   * Server, as a mapping between the backend ID and the corresponding backend.
   *
   * @return  The set of backends that have been registered with the Directory
   *          Server.
   */
  public static Map<String,Backend> getBackends()
  {
    return directoryServer.backends;
  }



  /**
   * Retrieves the backend with the specified backend ID.
   *
   * @param  backendID  The backend ID of the backend to retrieve.
   *
   * @return  The backend with the specified backend ID, or {@code null} if
   *          there is none.
   */
  public static Backend getBackend(String backendID)
  {
    return directoryServer.backends.get(backendID);
  }



  /**
   * Indicates whether the Directory Server has a backend with the specified
   * backend ID.
   *
   * @param  backendID  The backend ID for which to make the determination.
   *
   * @return  {@code true} if the Directory Server has a backend with the
   *          specified backend ID, or {@code false} if not.
   */
  public static boolean hasBackend(String backendID)
  {
    return directoryServer.backends.containsKey(backendID);
  }



  /**
   * Registers the provided backend with the Directory Server.  Note that this
   * will not register the set of configured suffixes with the server, as that
   * must be done by the backend itself.
   *
   * @param  backend  The backend to register with the server.  Neither the
   *                  backend nor its backend ID may be null.
   *
   * @throws  DirectoryException  If the backend ID for the provided backend
   *                              conflicts with the backend ID of an existing
   *                              backend.
   */
  public static void registerBackend(Backend backend)
         throws DirectoryException
  {
    ensureNotNull(backend);

    String backendID = backend.getBackendID();
    ensureNotNull(backendID);

    synchronized (directoryServer)
    {
      TreeMap<String, Backend> newBackends =
          new TreeMap<String, Backend>(directoryServer.backends);
      if (newBackends.containsKey(backendID))
      {
        Message message = ERR_REGISTER_BACKEND_ALREADY_EXISTS.get(backendID);
        throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
      }
      else
      {
        newBackends.put(backendID, backend);
        directoryServer.backends = newBackends;

        for (String oid : backend.getSupportedControls())
        {
          registerSupportedControl(oid);
        }

        for (String oid : backend.getSupportedFeatures())
        {
          registerSupportedFeature(oid);
        }

        BackendMonitor monitor = new BackendMonitor(backend);
        monitor.initializeMonitorProvider(null);
        backend.setBackendMonitor(monitor);
        registerMonitorProvider(monitor);
      }
    }
  }



  /**
   * Deregisters the provided backend with the Directory Server.  Note that this
   * will not deregister the set of configured suffixes with the server, as that
   * must be done by the backend itself.
   *
   * @param  backend  The backend to deregister with the server.  It must not be
   *                  {@code null}.
   */
  public static void deregisterBackend(Backend backend)
  {
    ensureNotNull(backend);

    synchronized (directoryServer)
    {
      TreeMap<String,Backend> newBackends =
           new TreeMap<String,Backend>(directoryServer.backends);
      newBackends.remove(backend.getBackendID());

      directoryServer.backends = newBackends;

      // Don't need anymore the local backend workflow element so we
      // can remove it. We do remove the workflow element only when
      // the workflow configuration mode is auto because in manual
      // mode the config manager is doing the job.
      if (workflowConfigurationModeIsAuto())
      {
        LocalBackendWorkflowElement.remove(backend.getBackendID());
      }


      BackendMonitor monitor = backend.getBackendMonitor();
      if (monitor != null)
      {
        String instanceName = toLowerCase(monitor.getMonitorInstanceName());
        deregisterMonitorProvider(instanceName);
        monitor.finalizeMonitorProvider();
        backend.setBackendMonitor(null);
      }
    }
  }



  /**
   * This method returns a map that contains a unique offline state id,
   * such as checksum, for every server backend that has registered one.
   *
   * @return  <CODE>Map</CODE> backend to checksum map for offline state.
   */
  public static Map<String,Long> getOfflineBackendsStateIDs() {
    return Collections.unmodifiableMap(directoryServer.offlineBackendsStateIDs);
  }



  /**
   * This method allows any server backend to register its unique offline
   * state, such as checksum, in a global map other server components can
   * access to determine if any changes were made to given backend while
   * offline.
   *
   * @param  backend  As returned by <CODE>getBackendID()</CODE> method.
   *
   * @param  id       Unique offline state identifier such as checksum.
   */
  public static void registerOfflineBackendStateID(String backend, long id) {
    // Zero means failed checksum so just skip it.
    if (id != 0) {
      directoryServer.offlineBackendsStateIDs.put(backend, id);
    }
  }



  /**
   * Retrieves the entire set of base DNs registered with the Directory Server,
   * mapped from the base DN to the backend responsible for that base DN.  The
   * same backend may be present multiple times, mapped from different base DNs.
   *
   * @return  The entire set of base DNs registered with the Directory Server.
   */
  public static Map<DN,Backend> getBaseDNs()
  {
    return directoryServer.baseDnRegistry.getBaseDnMap();
  }



  /**
   * Retrieves the backend with the specified base DN.
   *
   * @param  baseDN  The DN that is registered as one of the base DNs for the
   *                 backend to retrieve.
   *
   * @return  The backend with the specified base DN, or {@code null} if there
   *          is no backend registered with the specified base DN.
   */
  public static Backend getBackendWithBaseDN(DN baseDN)
  {
    return directoryServer.baseDnRegistry.getBaseDnMap().get(baseDN);
  }



  /**
   * Retrieves the backend that should be used to handle operations on the
   * specified entry.
   *
   * @param  entryDN  The DN of the entry for which to retrieve the
   *                  corresponding backend.
   *
   * @return  The backend that should be used to handle operations on the
   *          specified entry, or {@code null} if no appropriate backend is
   *          registered with the server.
   */
  public static Backend getBackend(DN entryDN)
  {
    if (entryDN.isNullDN())
    {
      return directoryServer.rootDSEBackend;
    }

    Map<DN,Backend> baseDNs = directoryServer.baseDnRegistry.getBaseDnMap();
    Backend b = baseDNs.get(entryDN);
    while (b == null)
    {
      entryDN = entryDN.getParent();
      if (entryDN == null)
      {
        return null;
      }

      b = baseDNs.get(entryDN);
    }

    return b;
  }


  /**
   * Obtains a copy of the server's base DN registry.  The copy can be used
   * to test registration/deregistration of base DNs but cannot be used to
   * modify the backends.  To modify the server's live base DN to backend
   * mappings use {@link #registerBaseDN(DN, Backend, boolean)} and
   * {@link #deregisterBaseDN(DN)}.
   *
   * @return copy of the base DN regsitry
   */
  public static BaseDnRegistry copyBaseDnRegistry()
  {
    return directoryServer.baseDnRegistry.copy();
  }


  /**
   * Registers the provided base DN with the server.
   *
   * @param  baseDN     The base DN to register with the server.  It must not be
   *                    {@code null}.
   * @param  backend    The backend responsible for the provided base DN.  It
   *                    must not be {@code null}.
   * @param  isPrivate  Indicates whether the base DN should be considered a
   *                    private base DN.  If the provided base DN is a naming
   *                    context, then this controls whether it is public or
   *                    private.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to
   *                              register the provided base DN.
   */
  public static void registerBaseDN(DN baseDN, Backend backend,
                                    boolean isPrivate)
         throws DirectoryException
  {
    ensureNotNull(baseDN, backend);

    synchronized (directoryServer)
    {
      List<Message> warnings =
              directoryServer.baseDnRegistry.registerBaseDN(
                      baseDN, backend, isPrivate);

      // Since we've committed the changes we need to log any issues
      // that this registration has caused
      if (warnings != null) {
        for (Message warning : warnings) {
          logError(warning);
        }
      }

      // When a new baseDN is registered with the server we have to create
      // a new workflow to handle the base DN. We do not need to create
      // the workflow in manual mode because in that case the workflows
      // are created explicitely.
      if (workflowConfigurationModeIsAuto())
      {
        // Now create a workflow for the registered baseDN and register
        // the workflow with the default network group, but don't register
        // the workflow if the backend happens to be the configuration
        // backend because it's too soon for the config backend.
        if (! baseDN.equals(DN.decode("cn=config")))
        {
          WorkflowImpl workflowImpl = createWorkflow(baseDN, backend);
          registerWorkflowWithDefaultNetworkGroup(workflowImpl);
        }
      }
    }
  }



  /**
   * Deregisters the provided base DN with the server.
   *
   * @param  baseDN     The base DN to deregister with the server.  It must not
   *                    be {@code null}.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to
   *                              deregister the provided base DN.
   */
  public static void deregisterBaseDN(DN baseDN)
         throws DirectoryException
  {
    ensureNotNull(baseDN);

    synchronized(directoryServer) {

      List<Message> warnings =
              directoryServer.baseDnRegistry.deregisterBaseDN(baseDN);

      // Since we've committed the changes we need to log any issues
      // that this registration has caused
      if (warnings != null) {
        for (Message error : warnings) {
          logError(error);
        }
      }

      // Now we need to deregister the workflow that was associated with
      // the base DN but we can do it only when the workflow configuration
      // mode is auto, because in manual mode the deregistration is done
      // by the workflow config manager.
      if (workflowConfigurationModeIsAuto())
      {
        deregisterWorkflowWithDefaultNetworkGroup(baseDN);
      }
    }
  }



  /**
   * Retrieves the set of public naming contexts defined in the Directory
   * Server, mapped from the naming context DN to the corresponding backend.
   *
   * @return  The set of public naming contexts defined in the Directory Server.
   */
  public static Map<DN,Backend> getPublicNamingContexts()
  {
    return directoryServer.baseDnRegistry.getPublicNamingContextsMap();
  }



  /**
   * Retrieves the set of private naming contexts defined in the Directory
   * Server, mapped from the naming context DN to the corresponding backend.
   *
   * @return  The set of private naming contexts defined in the Directory
   *          Server.
   */
  public static Map<DN,Backend> getPrivateNamingContexts()
  {
    return directoryServer.baseDnRegistry.getPrivateNamingContextsMap();
  }



  /**
   * Indicates whether the specified DN is one of the Directory Server naming
   * contexts.
   *
   * @param  dn  The DN for which to make the determination.
   *
   * @return  {@code true} if the specified DN is a naming context for the
   *          Directory Server, or {@code false} if it is not.
   */
  public static boolean isNamingContext(DN dn)
  {
    return directoryServer.baseDnRegistry.containsNamingContext(dn);
  }



  /**
   * Retrieves the root DSE entry for the Directory Server.
   *
   * @return  The root DSE entry for the Directory Server.
   */
  public static Entry getRootDSE()
  {
    return directoryServer.rootDSEBackend.getRootDSE();
  }



  /**
   * Retrieves the root DSE backend for the Directory Server.
   *
   * @return  The root DSE backend for the Directory Server.
   */
  public static RootDSEBackend getRootDSEBackend()
  {
    return directoryServer.rootDSEBackend;
  }



  /**
   * Retrieves the DN of the entry containing the server schema definitions.
   *
   * @return  The DN of the entry containing the server schema definitions, or
   *          <CODE>null</CODE> if none has been defined (e.g., if no schema
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
   * Retrieves the entry with the requested DN.  It will first determine which
   * backend should be used for this DN and will then use that backend to
   * retrieve the entry.  The caller must already hold the appropriate lock on
   * the specified entry.
   *
   * @param  entryDN  The DN of the entry to retrieve.
   *
   * @return  The requested entry, or <CODE>null</CODE> if it does not exist.
   *
   * @throws  DirectoryException  If a problem occurs while attempting to
   *                              retrieve the entry.
   */
  public static Entry getEntry(DN entryDN)
         throws DirectoryException
  {
    // If the entry is the root DSE, then get and return that.
    if (entryDN.isNullDN())
    {
      return directoryServer.rootDSEBackend.getRootDSE();
    }

    // Figure out which backend should be used for the entry.  If it isn't
    // appropriate for any backend, then return null.
    Backend backend = getBackend(entryDN);
    if (backend == null)
    {
      return null;
    }

    // Retrieve the requested entry from the backend.
    return backend.getEntry(entryDN);
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
    if (entryDN.isNullDN())
    {
      return true;
    }

    // Figure out which backend should be used for the entry.  If it isn't
    // appropriate for any backend, then return false.
    Backend backend = getBackend(entryDN);
    if (backend == null)
    {
      return false;
    }

    // Ask the appropriate backend if the entry exists.
    return backend.entryExists(entryDN);
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
    return directoryServer.supportedControls;
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
    return directoryServer.supportedControls.contains(controlOID);
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
    return directoryServer.supportedFeatures;
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
    return directoryServer.supportedFeatures.contains(featureOID);
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
  public static ConcurrentHashMap<String,ExtendedOperationHandler>
                     getSupportedExtensions()
  {
    return directoryServer.extendedOperationHandlers;
  }



  /**
   * Retrieves the handler for the extended operation for the provided OID.
   *
   * @param  oid  The OID of the extended operation to retrieve.
   *
   * @return  The handler for the specified extended operation, or
   *          <CODE>null</CODE> if there is none.
   */
  public static ExtendedOperationHandler getExtendedOperationHandler(String oid)
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
  public static void registerSupportedExtension(String oid,
                          ExtendedOperationHandler handler)
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
  public static ConcurrentHashMap<String,SASLMechanismHandler>
                     getSupportedSASLMechanisms()
  {
    return directoryServer.saslMechanismHandlers;
  }



  /**
   * Retrieves the handler for the specified SASL mechanism.
   *
   * @param  name  The name of the SASL mechanism to retrieve.
   *
   * @return  The handler for the specified SASL mechanism, or <CODE>null</CODE>
   *          if there is none.
   */
  public static SASLMechanismHandler getSASLMechanismHandler(String name)
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
  public static void registerSASLMechanismHandler(String name,
                                                  SASLMechanismHandler handler)
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
                                       ConnectionHandler connectionHandler)
  {
    List<ConnectionHandler> handlers =
         directoryServer.supportedLDAPVersions.get(supportedLDAPVersion);
    if (handlers == null)
    {
      handlers = new LinkedList<ConnectionHandler>();
      handlers.add(connectionHandler);
      directoryServer.supportedLDAPVersions.put(supportedLDAPVersion, handlers);
    }
    else
    {
      if (! handlers.contains(connectionHandler))
      {
        handlers.add(connectionHandler);
      }
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
                                       ConnectionHandler connectionHandler)
  {
    List<ConnectionHandler> handlers =
         directoryServer.supportedLDAPVersions.get(supportedLDAPVersion);
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
   * Retrieves the set of identity mappers defined in the Directory Server
   * configuration, as a mapping between the DN of the configuration entry and
   * the identity mapper.
   *
   * @return  The set of identity mappers defined in the Directory Server
   *          configuration.
   */
  public static ConcurrentHashMap<DN,IdentityMapper> getIdentityMappers()
  {
    return directoryServer.identityMappers;
  }



  /**
   * Retrieves the Directory Server identity mapper whose configuration resides
   * in the specified configuration entry.
   *
   * @param  configEntryDN  The DN of the configuration entry for the identity
   *                        mapper to retrieve.
   *
   * @return  The requested identity mapper, or <CODE>null</CODE> if the
   *          provided entry DN is not associated with an active identity
   *          mapper.
   */
  public static IdentityMapper getIdentityMapper(DN configEntryDN)
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
  public static void registerIdentityMapper(DN configEntryDN,
                                            IdentityMapper identityMapper)
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
   * Retrieves the DN of the configuration entry for the identity mapper that
   * should be used in conjunction with proxied authorization V2 controls.
   *
   * @return  The DN of the configuration entry for the identity mapper that
   *          should be used in conjunction with proxied authorization V2
   *          controls, or <CODE>null</CODE> if none is defined.
   */
  public static DN getProxiedAuthorizationIdentityMapperDN()
  {
    return directoryServer.proxiedAuthorizationIdentityMapperDN;
  }



  /**
   * Specifies the DN of the configuration entry for the identity mapper that
   * should be used in conjunction with proxied authorization V2 controls.
   *
   * @param  proxiedAuthorizationIdentityMapperDN  The DN of the configuration
   *                                               entry for the identity mapper
   *                                               that should be used in
   *                                               conjunction with proxied
   *                                               authorization V2 controls.
   */
  public static void setProxiedAuthorizationIdentityMapperDN(
                          DN proxiedAuthorizationIdentityMapperDN)
  {
    directoryServer.proxiedAuthorizationIdentityMapperDN =
         proxiedAuthorizationIdentityMapperDN;
  }



  /**
   * Retrieves the identity mapper that should be used to resolve authorization
   * IDs contained in proxied authorization V2 controls.
   *
   * @return  The identity mapper that should be used to resolve authorization
   *          IDs contained in proxied authorization V2 controls, or
   *          <CODE>null</CODE> if none is defined.
   */
  public static IdentityMapper getProxiedAuthorizationIdentityMapper()
  {
    if (directoryServer.proxiedAuthorizationIdentityMapperDN == null)
    {
      return null;
    }

    return directoryServer.identityMappers.get(
                directoryServer.proxiedAuthorizationIdentityMapperDN);
  }



  /**
   * Retrieves the set of connection handlers configured in the Directory
   * Server.  The returned list must not be altered.
   *
   * @return  The set of connection handlers configured in the Directory Server.
   */
  public static CopyOnWriteArrayList<ConnectionHandler> getConnectionHandlers()
  {
    return directoryServer.connectionHandlers;
  }



  /**
   * Registers the provided connection handler with the Directory Server.
   *
   * @param  handler  The connection handler to register with the Directory
   *                  Server.
   */
  public static void registerConnectionHandler(
                          ConnectionHandler<? extends ConnectionHandlerCfg>
                               handler)
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
  public static void deregisterConnectionHandler(ConnectionHandler handler)
  {
    synchronized (directoryServer.connectionHandlers)
    {
      directoryServer.connectionHandlers.remove(handler);

      ConnectionHandlerMonitor monitor = handler.getConnectionHandlerMonitor();
      if (monitor != null)
      {
        String instanceName = toLowerCase(monitor.getMonitorInstanceName());
        deregisterMonitorProvider(instanceName);
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
    LinkedHashSet<HostPort> usedListeners = new LinkedHashSet<HostPort>();
    LinkedHashSet<Message> errorMessages = new LinkedHashSet<Message>();
    // Check that the port specified in the connection handlers is
    // available.
    for (ConnectionHandler<?> c : connectionHandlers)
    {
      for (HostPort listener : c.getListeners())
      {
        if (usedListeners.contains(listener))
        {
          // The port was already specified: this is a configuration error,
          // log a message.
          Message message = ERR_HOST_PORT_ALREADY_SPECIFIED.get(
              c.getConnectionHandlerName(), listener.toString());
          logError(message);
          errorMessages.add(message);

        }
        else
        {
          usedListeners.add(listener);
        }
      }
    }

    if (errorMessages.size() > 0)
    {
      throw new ConfigException(ERR_ERROR_STARTING_CONNECTION_HANDLERS.get());
    }


    // If there are no connection handlers log a message.
    if (connectionHandlers.isEmpty())
    {
      Message message = ERR_NOT_AVAILABLE_CONNECTION_HANDLERS.get();
      logError(message);
      throw new ConfigException(ERR_ERROR_STARTING_CONNECTION_HANDLERS.get());
    }

    // At this point, we should be ready to go.  Start all the connection
    // handlers.
    for (ConnectionHandler c : connectionHandlers)
    {
      c.start();
    }
  }

  /**
   * Retrieves a reference to the Directory Server work queue.
   *
   * @return  A reference to the Directory Server work queue.
   */
  public static WorkQueue getWorkQueue()
  {
    return directoryServer.workQueue;
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
  public static void enqueueRequest(AbstractOperation operation)
         throws DirectoryException
  {
    // See if a bind is already in progress on the associated connection.  If so
    // then reject the operation.
    ClientConnection clientConnection = operation.getClientConnection();
    if (clientConnection.bindInProgress() &&
        (operation.getOperationType() != OperationType.BIND))
    {
      Message message = ERR_ENQUEUE_BIND_IN_PROGRESS.get();
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
    }


    //Reject or accept the unauthenticated requests based on the configuration
    // settings.
    if ((directoryServer.rejectUnauthenticatedRequests ||
         directoryServer.lockdownMode) &&
        !clientConnection.getAuthenticationInfo().isAuthenticated())
    {
      switch(operation.getOperationType())
      {
        case ADD:
        case COMPARE:
        case DELETE:
        case SEARCH:
        case MODIFY:
        case MODIFY_DN:
          if (directoryServer.lockdownMode)
          {
            Message message = NOTE_REJECT_OPERATION_IN_LOCKDOWN_MODE.get();
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         message);
          }
          else
          {
            Message message = ERR_REJECT_UNAUTHENTICATED_OPERATION.get();
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                         message);
          }

        case EXTENDED:
         ExtendedOperationBasis extOp = (ExtendedOperationBasis) operation;
         String   requestOID = extOp.getRequestOID();
         if (!((requestOID != null) &&
                 requestOID.equals(OID_START_TLS_REQUEST)))
         {
           if (directoryServer.lockdownMode)
           {
             Message message = NOTE_REJECT_OPERATION_IN_LOCKDOWN_MODE.get();
             throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                          message);
           }
           else
           {
             Message message = ERR_REJECT_UNAUTHENTICATED_OPERATION.get();
             throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                                          message);
           }
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
            if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
            {
              operation.addResponseControl(new PasswordPolicyResponseControl(
                   null, 0, PasswordPolicyErrorType.CHANGE_AFTER_RESET));
              break;
            }
          }

          Message message = ERR_ENQUEUE_MUST_CHANGE_PASSWORD.get();
          throw new DirectoryException(
                  ResultCode.CONSTRAINT_VIOLATION, message);

        case EXTENDED:
          // We will only allow the password modify and StartTLS extended
          // operations.
          ExtendedOperationBasis extOp = (ExtendedOperationBasis) operation;
          String            requestOID = extOp.getRequestOID();
          if ((requestOID == null) ||
              ((! requestOID.equals(OID_PASSWORD_MODIFY_REQUEST)) &&
               (! requestOID.equals(OID_START_TLS_REQUEST))))
          {
            // See if the request included the password policy request control.
            // If it did, then add a corresponding response control.
            for (Control c : operation.getRequestControls())
            {
              if (c.getOID().equals(OID_PASSWORD_POLICY_CONTROL))
              {
                operation.addResponseControl(new PasswordPolicyResponseControl(
                     null, 0, PasswordPolicyErrorType.CHANGE_AFTER_RESET));
                break;
              }
            }

            message = ERR_ENQUEUE_MUST_CHANGE_PASSWORD.get();
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         message);
          }

          break;

          // Bind, unbind, and abandon will always be allowed.

          // Modify may or may not be allowed, but we'll leave that
          // determination up to the modify operation itself.
      }
    }



    directoryServer.workQueue.submitOperation(operation);
  }



  /**
   * Retrieves the set of change notification listeners registered with the
   * Directory Server.
   *
   * @return  The set of change notification listeners registered with the
   *          Directory Server.
   */
  public static CopyOnWriteArrayList<ChangeNotificationListener>
                     getChangeNotificationListeners()
  {
    return directoryServer.changeNotificationListeners;
  }



  /**
   * Registers the provided change notification listener with the Directory
   * Server so that it will be notified of any add, delete, modify, or modify DN
   * operations that are performed.
   *
   * @param  changeListener  The change notification listener to register with
   *                         the Directory Server.
   */
  public static void registerChangeNotificationListener(
                          ChangeNotificationListener changeListener)
  {
    directoryServer.changeNotificationListeners.add(changeListener);
  }



  /**
   * Deregisters the provided change notification listener with the Directory
   * Server so that it will no longer be notified of any add, delete, modify, or
   * modify DN operations that are performed.
   *
   * @param  changeListener  The change notification listener to deregister with
   *                         the Directory Server.
   */
  public static void deregisterChangeNotificationListener(
                          ChangeNotificationListener changeListener)
  {
    directoryServer.changeNotificationListeners.remove(changeListener);
  }



  /**
   * Retrieves the set of persistent searches registered with the Directory
   * Server.
   *
   * @return  The set of persistent searches registered with the Directory
   *          Server.
   */
  public static CopyOnWriteArrayList<PersistentSearch> getPersistentSearches()
  {
    return directoryServer.persistentSearches;
  }



  /**
   * Registers the provided persistent search operation with the Directory
   * Server so that it will be notified of any add, delete, modify, or modify DN
   * operations that are performed.
   *
   * @param  persistentSearch  The persistent search operation to register with
   *                           the Directory Server.
   */
  public static void registerPersistentSearch(PersistentSearch persistentSearch)
  {
    directoryServer.persistentSearches.add(persistentSearch);
    persistentSearch.getSearchOperation().getClientConnection().
         registerPersistentSearch(persistentSearch);
  }



  /**
   * Deregisters the provided persistent search operation with the Directory
   * Server so that it will no longer be notified of any add, delete, modify, or
   * modify DN operations that are performed.
   *
   * @param  persistentSearch  The persistent search operation to deregister
   *                           with the Directory Server.
   */
  public static void deregisterPersistentSearch(PersistentSearch
                                                     persistentSearch)
  {
    directoryServer.persistentSearches.remove(persistentSearch);
    persistentSearch.getSearchOperation().getClientConnection().
         deregisterPersistentSearch(persistentSearch);
  }




  /**
   * Retrieves the set of synchronization providers that have been registered
   * with the Directory Server.
   *
   * @return  The set of synchronization providers that have been registered
   *          with the Directory Server.
   */
  public static
    CopyOnWriteArrayList<SynchronizationProvider<SynchronizationProviderCfg>>
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
   * Deregisters the provided synchronization provider with the Directory
   * Server.
   *
   * @param  provider  The synchronization provider to deregister.
   */
  public static void deregisterSynchronizationProvider(SynchronizationProvider
                                                            provider)
  {
    directoryServer.synchronizationProviders.remove(provider);
  }



  /**
   * Retrieves a set containing the names of the allowed tasks that may be
   * invoked in the server.
   *
   * @return  A set containing the names of the allowed tasks that may be
   *          invoked in the server.
   */
  public static Set<String> getAllowedTasks()
  {
    return directoryServer.allowedTasks;
  }



  /**
   * Specifies the set of allowed tasks that may be invoked in the server.
   *
   * @param  allowedTasks  A set containing the names of the allowed tasks that
   *                       may be invoked in the server.
   */
  public static void setAllowedTasks(Set<String> allowedTasks)
  {
    directoryServer.allowedTasks = allowedTasks;
  }



  /**
   * Retrieves the set of privileges that have been disabled.
   *
   * @return  The set of privileges that have been disabled.
   */
  public static Set<Privilege> getDisabledPrivileges()
  {
    return directoryServer.disabledPrivileges;
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
    return directoryServer.disabledPrivileges.contains(privilege);
  }



  /**
   * Specifies the set of privileges that should be disabled in the server.
   *
   * @param  disabledPrivileges  The set of privileges that should be disabled
   *                             in the server.
   */
  public static void setDisabledPrivileges(Set<Privilege> disabledPrivileges)
  {
    directoryServer.disabledPrivileges = disabledPrivileges;
  }



  /**
   * Indicates whether responses to failed bind operations should include a
   * message explaining the reason for the failure.
   *
   * @return  {@code true} if bind responses should include error messages, or
   *          {@code false} if not.
   */
  public static boolean returnBindErrorMessages()
  {
    return directoryServer.returnBindErrorMessages;
  }



  /**
   * Specifies whether responses to failed bind operations should include a
   * message explaining the reason for the failure.
   *
   * @param  returnBindErrorMessages  Specifies whether responses to failed bind
   *                                  operations should include a message
   *                                  explaining the reason for the failure.
   */
  public static void setReturnBindErrorMessages(boolean returnBindErrorMessages)
  {
    directoryServer.returnBindErrorMessages = returnBindErrorMessages;
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
   * Indicates whether the Directory Server should save a copy of its
   * configuration whenever it is started successfully.
   *
   * @return  {@code true} if the server should save a copy of its configuration
   *          whenever it is started successfully, or {@code false} if not.
   */
  public static boolean saveConfigOnSuccessfulStartup()
  {
    return directoryServer.saveConfigOnSuccessfulStartup;
  }



  /**
   * Specifies whether the Directory Server should save a copy of its
   * configuration whenever it is started successfully.
   *
   * @param  saveConfigOnSuccessfulStartup  Specifies whether the server should
   *                                        save a copy of its configuration
   *                                        whenever it is started successfully.
   */
  public static void setSaveConfigOnSuccessfulStartup(
                          boolean saveConfigOnSuccessfulStartup)
  {
    directoryServer.saveConfigOnSuccessfulStartup =
         saveConfigOnSuccessfulStartup;
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
  public static void notifyBackupBeginning(Backend backend, BackupConfig config)
  {
    for (BackupTaskListener listener : directoryServer.backupTaskListeners)
    {
      try
      {
        listener.processBackupBegin(backend, config);
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
   * Notifies the registered backup task listeners that the server has completed
   * processing on a backup task with the provided information.
   *
   * @param  backend     The backend in which the backup was performed.
   * @param  config      The configuration for the backup that was performed.
   * @param  successful  Indicates whether the backup completed successfully.
   */
  public static void notifyBackupEnded(Backend backend, BackupConfig config,
                                       boolean successful)
  {
    for (BackupTaskListener listener : directoryServer.backupTaskListeners)
    {
      try
      {
        listener.processBackupEnd(backend, config, successful);
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
  public static void notifyRestoreBeginning(Backend backend,
                                            RestoreConfig config)
  {
    for (RestoreTaskListener listener : directoryServer.restoreTaskListeners)
    {
      try
      {
        listener.processRestoreBegin(backend, config);
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
   * Notifies the registered restore task listeners that the server has
   * completed processing on a restore task with the provided information.
   *
   * @param  backend     The backend in which the restore was performed.
   * @param  config      The configuration for the restore that was performed.
   * @param  successful  Indicates whether the restore completed successfully.
   */
  public static void notifyRestoreEnded(Backend backend, RestoreConfig config,
                                        boolean successful)
  {
    for (RestoreTaskListener listener : directoryServer.restoreTaskListeners)
    {
      try
      {
        listener.processRestoreEnd(backend, config, successful);
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
  public static void notifyExportBeginning(Backend backend,
                                           LDIFExportConfig config)
  {
    for (ExportTaskListener listener : directoryServer.exportTaskListeners)
    {
      try
      {
        listener.processExportBegin(backend, config);
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
   * Notifies the registered LDIF export task listeners that the server has
   * completed processing on an export task with the provided information.
   *
   * @param  backend     The backend in which the export was performed.
   * @param  config      The configuration for the export that was performed.
   * @param  successful  Indicates whether the export completed successfully.
   */
  public static void notifyExportEnded(Backend backend, LDIFExportConfig config,
                                       boolean successful)
  {
    for (ExportTaskListener listener : directoryServer.exportTaskListeners)
    {
      try
      {
        listener.processExportEnd(backend, config, successful);
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
  public static void notifyImportBeginning(Backend backend,
                                           LDIFImportConfig config)
  {
    for (ImportTaskListener listener : directoryServer.importTaskListeners)
    {
      try
      {
        listener.processImportBegin(backend, config);
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
   * Notifies the registered LDIF import task listeners that the server has
   * completed processing on an import task with the provided information.
   *
   * @param  backend     The backend in which the import was performed.
   * @param  config      The configuration for the import that was performed.
   * @param  successful  Indicates whether the import completed successfully.
   */
  public static void notifyImportEnded(Backend backend, LDIFImportConfig config,
                                       boolean successful)
  {
    for (ImportTaskListener listener : directoryServer.importTaskListeners)
    {
      try
      {
        listener.processImportEnd(backend, config, successful);
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
  public static void shutDown(String className, Message reason)
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

    ConfigEntry rootConfigEntry = null;
    try {
      rootConfigEntry = directoryServer.configHandler.getConfigRootEntry();
    } catch (Exception e) {

    }

    // Send an alert notification that the server is shutting down.
    Message message = NOTE_SERVER_SHUTDOWN.get(className, reason);
    sendAlertNotification(directoryServer, ALERT_TYPE_SERVER_SHUTDOWN,
            message);


    // Create a shutdown monitor that will watch the rest of the shutdown
    // process to ensure that everything goes smoothly.
    ServerShutdownMonitor shutdownMonitor = new ServerShutdownMonitor();
    shutdownMonitor.start();


    // Shut down the connection handlers.
    for (ConnectionHandler handler : directoryServer.connectionHandlers)
    {
      try
      {

        handler.finalizeConnectionHandler(
                INFO_CONNHANDLER_CLOSED_BY_SHUTDOWN.get(), true);
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }
    directoryServer.connectionHandlers.clear();



    // Call the shutdown plugins, and then finalize all the plugins defined in
    // the server.
    if (directoryServer.pluginConfigManager != null)
    {
      directoryServer.pluginConfigManager.invokeShutdownPlugins(reason);
      directoryServer.pluginConfigManager.finalizePlugins();
    }


    // shutdown the Synchronization Providers
    for (SynchronizationProvider provider :
         directoryServer.synchronizationProviders)
    {
      provider.finalizeSynchronizationProvider();
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


    // Stop the work queue.
    if (directoryServer.workQueue != null)
    {
      directoryServer.workQueue.finalizeWorkQueue(reason);
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
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }


    // Shut down all of the alert handlers.
    for (AlertHandler alertHandler : directoryServer.alertHandlers)
    {
      alertHandler.finalizeAlertHandler();
    }


    // Deregister all of the JMX MBeans.
    if (directoryServer.mBeanServer != null)
    {
      Set mBeanSet = directoryServer.mBeanServer.queryMBeans(null, null);
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
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
          }
        }
      }
    }


    // Finalize all of the SASL mechanism handlers.
    for (SASLMechanismHandler handler :
         directoryServer.saslMechanismHandlers.values())
    {
      try
      {
        handler.finalizeSASLMechanismHandler();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }


    // Finalize all of the extended operation handlers.
    for (ExtendedOperationHandler handler :
         directoryServer.extendedOperationHandlers.values())
    {
      try
      {
        handler.finalizeExtendedOperationHandler();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }


    // Finalize the password policy map.
    for (DN configEntryDN : directoryServer.passwordPolicies.keySet())
    {
      DirectoryServer.deregisterPasswordPolicy(configEntryDN);
    }

    // Finalize the access control handler
    AccessControlHandler accessControlHandler =
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


    // Shut down all the other components that may need special handling.
    // NYI


    // Shut down the monitor providers.
    for (MonitorProvider monitor : directoryServer.monitorProviders.values())
    {
      try
      {
        monitor.finalizeMonitorProvider();
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }


    // Shut down the backends.
    for (Backend backend : directoryServer.backends.values())
    {
      try
      {
        // Deregister all the local backend workflow elements that have been
        // registered with the server.
        LocalBackendWorkflowElement.removeAll();

        for (BackendInitializationListener listener :
             directoryServer.backendInitializationListeners)
        {
          listener.performBackendFinalizationProcessing(backend);
        }

        backend.finalizeBackend();

        // Remove the shared lock for this backend.
        try
        {
          String lockFile = LockFileManager.getBackendLockFileName(backend);
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.releaseLock(lockFile, failureReason))
          {
            message = WARN_SHUTDOWN_CANNOT_RELEASE_SHARED_BACKEND_LOCK.
                get(backend.getBackendID(), String.valueOf(failureReason));
            logError(message);
            // FIXME -- Do we need to send an admin alert?
          }

          serverLocked = false;
        }
        catch (Exception e2)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e2);
          }

          message = WARN_SHUTDOWN_CANNOT_RELEASE_SHARED_BACKEND_LOCK.
              get(backend.getBackendID(), stackTraceToSingleLineString(e2));
          logError(message);
          // FIXME -- Do we need to send an admin alert?
        }
      }
      catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }
      }
    }

    // Finalize the entry cache.
    EntryCache ec = DirectoryServer.getEntryCache();
    if (ec != null)
    {
      ec.finalizeEntryCache();
    }

    // Release the exclusive lock for the Directory Server process.
    String lockFile = LockFileManager.getServerLockFileName();
    try
    {
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.releaseLock(lockFile, failureReason))
      {
        message = WARN_CANNOT_RELEASE_EXCLUSIVE_SERVER_LOCK.get(
            lockFile, String.valueOf(failureReason));
        logError(message);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      message = WARN_CANNOT_RELEASE_EXCLUSIVE_SERVER_LOCK.get(
          lockFile, stackTraceToSingleLineString(e));
      logError(message);
    }

    // Deregister all workflows.
    WorkflowImpl.deregisterAllOnShutdown();

    // Deregister all network group configuration.
    NetworkGroup.deregisterAllOnShutdown();

    // Force a new InternalClientConnection to be created on restart.
    InternalConnectionHandler.clearRootClientConnectionAtShutdown();

    // Log a final message indicating that the server is stopped (which should
    // be true for all practical purposes), and then shut down all the error
    // loggers.
    logError(NOTE_SERVER_STOPPED.get());

    removeAllAccessLogPublishers();
    removeAllErrorLogPublishers();
    removeAllDebugLogPublishers();

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
    checkSchema                   = true;
    isBootstrapped                = false;
    isClientBootstrapped          = false;
    isRunning                     = false;
    lockdownMode                  = true;
    rejectUnauthenticatedRequests = true;
    shuttingDown                  = true;

    configClass              = null;
    configFile               = null;
    configHandler            = null;
    coreConfigManager        = null;
    compressedSchema         = null;
    cryptoManager            = null;
    defaultBinarySyntax      = null;
    defaultBooleanSyntax     = null;
    defaultDNSyntax          = null;
    defaultIntegerSyntax     = null;
    defaultStringSyntax      = null;
    defaultSyntax            = null;
    entryCache               = null;
    environmentConfig        = null;
    objectClassAttributeType = null;
    schemaDN                 = null;
    shutdownHook             = null;
    workQueue                = null;

    if (baseDnRegistry != null)
    {
      baseDnRegistry.clear();
      baseDnRegistry = null;
    }

    if (backends != null)
    {
      backends.clear();
      backends = null;
    }

    if (schema != null)
    {
      schema.destroy();
      schema = null;
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
  public static void restart(String className, Message reason)
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
  public static void restart(String className, Message reason,
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
   * @return  The new Directory Server instance created during the
   *          reinitialization process.
   *
   * @throws  InitializationException  If a problem occurs while trying to
   *                                   initialize the config handler or
   *                                   bootstrap that server.
   */
  public static DirectoryServer reinitialize()
         throws InitializationException
  {
    return reinitialize(directoryServer.environmentConfig);
  }



  /**
   * Reinitializes the server following a shutdown, preparing it for a call to
   * {@code startServer}.
   *
   * @param  config  The environment configuration for the Directory Server.
   *
   * @return  The new Directory Server instance created during the
   *          reinitialization process.
   *
   * @throws  InitializationException  If a problem occurs while trying to
   *                                   initialize the config handler or
   *                                   bootstrap that server.
   */
  public static DirectoryServer reinitialize(DirectoryEnvironmentConfig config)
         throws InitializationException
  {
    getNewInstance(config);
    LockManager.reinitializeLockTable();
    directoryServer.bootstrapServer();
    directoryServer.initializeConfiguration();
    return directoryServer;
  }



  /**
   * Retrieves the maximum number of concurrent client connections that may be
   * established.
   *
   * @return  The maximum number of concurrent client connections that may be
   *          established, or -1 if there is no limit.
   */
  public static long getMaxAllowedConnections()
  {
    return directoryServer.maxAllowedConnections;
  }



  /**
   * Specifies the maximum number of concurrent client connections that may be
   * established.  A value that is less than or equal to zero will indicate that
   * no limit should be enforced.
   *
   * @param  maxAllowedConnections  The maximum number of concurrent client
   *                                connections that may be established.
   */
  public static void setMaxAllowedConnections(long maxAllowedConnections)
  {
    if (maxAllowedConnections > 0)
    {
      directoryServer.maxAllowedConnections = maxAllowedConnections;
    }
    else
    {
      directoryServer.maxAllowedConnections = -1;
    }
  }



  /**
   * Indicates that a new connection has been accepted and increments the
   * associated counters.
   *
   * @param  clientConnection  The client connection that has been established.
   *
   * @return  The connection ID that should be used for this connection, or -1
   *          if the connection has been rejected for some reason (e.g., the
   *          maximum numberof concurrent connections have already been
   *          established).
   */
  public static long newConnectionAccepted(ClientConnection clientConnection)
  {
    synchronized (directoryServer.establishedConnections)
    {
      if (directoryServer.lockdownMode)
      {
        InetAddress remoteAddress = clientConnection.getRemoteAddress();
        if ((remoteAddress != null) && (! remoteAddress.isLoopbackAddress()))
        {
          return -1;
        }
      }

      if ((directoryServer.maxAllowedConnections > 0) &&
          (directoryServer.currentConnections >=
               directoryServer.maxAllowedConnections))
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
  public static void printVersion(OutputStream outputStream)
  throws IOException
  {
    outputStream.write(getBytes(PRINTABLE_VERSION_STRING));
    return;
  }


  /**
   * Retrieves the default maximum number of entries that should be returned for
   * a search.
   *
   * @return  The default maximum number of entries that should be returned for
   *          a search.
   */
  public static int getSizeLimit()
  {
    return directoryServer.sizeLimit;
  }



  /**
   * Specifies the default maximum number of entries that should be returned for
   * a search.
   *
   * @param  sizeLimit  The default maximum number of entries that should be
   *                    returned for a search.
   */
  public static void setSizeLimit(int sizeLimit)
  {
    directoryServer.sizeLimit = sizeLimit;
  }



  /**
   * Retrieves the default maximum number of entries that should checked for
   * matches during a search.
   *
   * @return  The default maximum number of entries that should checked for
   *          matches during a search.
   */
  public static int getLookthroughLimit()
  {
    return directoryServer.lookthroughLimit;
  }



  /**
   * Specifies the default maximum number of entries that should be checked for
   * matches during a search.
   *
   * @param  lookthroughLimit  The default maximum number of entries that should
   *                           be check for matches during a search.
   */
  public static void setLookthroughLimit(int lookthroughLimit)
  {
    directoryServer.lookthroughLimit = lookthroughLimit;
  }



  /**
   * Retrieves the default maximum length of time in seconds that should be
   * allowed when processing a search.
   *
   * @return  The default maximum length of time in seconds that should be
   *          allowed when processing a search.
   */
  public static int getTimeLimit()
  {
    return directoryServer.timeLimit;
  }



  /**
   * Specifies the default maximum length of time in seconds that should be
   * allowed when processing a search.
   *
   * @param  timeLimit  The default maximum length of time in seconds that
   *                    should be allowed when processing a search.
   */
  public static void setTimeLimit(int timeLimit)
  {
    directoryServer.timeLimit = timeLimit;
  }



  /**
   * Specifies whether to collect nanosecond resolution processing times for
   * operations.
   *
   * @param useNanoTime  <code>true</code> if nanosecond resolution times
   *                     should be collected or <code>false</code> to
   *                     only collect in millisecond resolution.
   */
  public static void setUseNanoTime(boolean useNanoTime)
  {
    directoryServer.useNanoTime = useNanoTime;
  }



  /**
   * Retrieves whether operation processing times should be collected with
   * nanosecond resolution.
   *
   * @return  <code>true</code> if nanosecond resolution times are collected
   *          or <code>false</code> if only millisecond resolution times are
   *          being collected.
   */
  public static boolean getUseNanoTime()
  {
    return directoryServer.useNanoTime;
  }



  /**
   * Retrieves the writability mode for the Directory Server.  This will only
   * be applicable for user suffixes.
   *
   * @return  The writability mode for the Directory Server.
   */
  public static WritabilityMode getWritabilityMode()
  {
    return directoryServer.writabilityMode;
  }



  /**
   * Specifies the writability mode for the Directory Server.  This will only
   * be applicable for user suffixes.
   *
   * @param writabilityMode  Specifies the writability mode for the Directory
   *                         Server.
   */
  public static void setWritabilityMode(WritabilityMode writabilityMode)
  {
    directoryServer.writabilityMode = writabilityMode;
  }




  /**
   * Indicates whether simple bind requests that contain a bind DN will also be
   * required to have a password.
   *
   * @return  <CODE>true</CODE> if simple bind requests containing a bind DN
   *          will be required to have a password, or <CODE>false</CODE> if not
   *          (and therefore will be treated as anonymous binds).
   */
  public static boolean bindWithDNRequiresPassword()
  {
    return directoryServer.bindWithDNRequiresPassword;
  }



  /**
   * Specifies whether simple bind requests that contain a bind DN will also be
   * required to have a password.
   *
   * @param  bindWithDNRequiresPassword  Indicates whether simple bind requests
   *                                     that contain a bind DN will also be
   *                                     required to have a password.
   */
  public static void setBindWithDNRequiresPassword(boolean
                          bindWithDNRequiresPassword)
  {
    directoryServer.bindWithDNRequiresPassword = bindWithDNRequiresPassword;
  }



  /**
   * Indicates whether an unauthenticated request should be rejected.
   *
   * @return <CODE>true</CODE>if an unauthenticated request should be
   *         rejected, or <CODE>false</CODE>f if not.
   */
  public static boolean rejectUnauthenticatedRequests()
  {
     return directoryServer.rejectUnauthenticatedRequests;
  }

  /**
   * Specifies whether an unauthenticated request should be rejected.
   *
   * @param  rejectUnauthenticatedRequests   Indicates whether an
   *                                        unauthenticated request should
   *                                        be rejected.
   */
  public static void setRejectUnauthenticatedRequests(boolean
                          rejectUnauthenticatedRequests)
  {
        directoryServer.rejectUnauthenticatedRequests =
                                  rejectUnauthenticatedRequests;
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
      Message message = WARN_DIRECTORY_SERVER_ENTERING_LOCKDOWN_MODE.get();
      logError(message);

      sendAlertNotification(directoryServer, ALERT_TYPE_ENTERING_LOCKDOWN_MODE,
              message);
    }
    else
    {
      Message message = NOTE_DIRECTORY_SERVER_LEAVING_LOCKDOWN_MODE.get();
      logError(message);

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
  public DN getComponentEntryDN()
  {
    try
    {
      if (configHandler == null)
      {
        // The config handler hasn't been initialized yet.  Just return the DN
        // of the root DSE.
        return DN.nullDN();
      }

      return configHandler.getConfigRootEntry().getDN();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      // This could theoretically happen if an alert needs to be sent before the
      // configuration is initialized.  In that case, just return an empty DN.
      return DN.nullDN();
    }
  }



  /**
   * Retrieves the fully-qualified name of the Java class for this alert
   * generator implementation.
   *
   * @return  The fully-qualified name of the Java class for this alert
   *          generator implementation.
   */
  public String getClassName()
  {
    return CLASS_NAME;
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
  public LinkedHashMap<String,String> getAlerts()
  {
    LinkedHashMap<String,String> alerts = new LinkedHashMap<String,String>();

    alerts.put(ALERT_TYPE_SERVER_STARTED, ALERT_DESCRIPTION_SERVER_STARTED);
    alerts.put(ALERT_TYPE_SERVER_SHUTDOWN, ALERT_DESCRIPTION_SERVER_SHUTDOWN);
    alerts.put(ALERT_TYPE_UNCAUGHT_EXCEPTION,
               ALERT_DESCRIPTION_UNCAUGHT_EXCEPTION);
    alerts.put(ALERT_TYPE_ENTERING_LOCKDOWN_MODE,
               ALERT_DESCRIPTION_ENTERING_LOCKDOWN_MODE);
    alerts.put(ALERT_TYPE_LEAVING_LOCKDOWN_MODE,
               ALERT_DESCRIPTION_LEAVING_LOCKDOWN_MODE);

    return alerts;
  }



  /**
   * Provides a means of handling a case in which a thread is about to die
   * because of an unhandled exception.  This method does nothing to try to
   * prevent the death of that thread, but will at least log it so that it can
   * be available for debugging purposes.
   *
   * @param  thread     The thread that threw the exception.
   * @param  exception  The exception that was thrown but not properly handled.
   */
  public void uncaughtException(Thread thread, Throwable exception)
  {
    if (debugEnabled())
    {
      TRACER.debugCaught(DebugLogLevel.ERROR, exception);
    }

    Message message = ERR_UNCAUGHT_THREAD_EXCEPTION.get(
        thread.getName(), stackTraceToString(exception));
    logError(message);
    sendAlertNotification(this, ALERT_TYPE_UNCAUGHT_EXCEPTION, message);
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
    BooleanArgument checkStartability      = null;
    BooleanArgument quietMode              = null;
    BooleanArgument windowsNetStart        = null;
    BooleanArgument displayUsage           = null;
    BooleanArgument fullVersion            = null;
    BooleanArgument noDetach               = null;
    BooleanArgument systemInfo             = null;
    BooleanArgument useLastKnownGoodConfig = null;
    StringArgument  configClass            = null;
    StringArgument  configFile             = null;


    // Create the command-line argument parser for use with this program.
    Message toolDescription = INFO_DSCORE_TOOL_DESCRIPTION.get();
    ArgumentParser argParser =
         new ArgumentParser("org.opends.server.core.DirectoryServer",
                            toolDescription, false);


    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      configClass = new StringArgument("configclass", 'C', "configClass",
                                       true, false, true,
                                       INFO_CONFIGCLASS_PLACEHOLDER.get(),
                                       ConfigFileHandler.class.getName(), null,
                                       INFO_DSCORE_DESCRIPTION_CONFIG_CLASS
                                               .get());
      configClass.setHidden(true);
      argParser.addArgument(configClass);


      configFile = new StringArgument("configfile", 'f', "configFile",
                                      true, false, true,
                                      INFO_CONFIGFILE_PLACEHOLDER.get(), null,
                                      null,
                                      INFO_DSCORE_DESCRIPTION_CONFIG_FILE
                                              .get());
      configFile.setHidden(true);
      argParser.addArgument(configFile);


      checkStartability = new BooleanArgument("checkstartability", null,
                              "checkStartability",
                              INFO_DSCORE_DESCRIPTION_CHECK_STARTABILITY.get());
      checkStartability.setHidden(true);
      argParser.addArgument(checkStartability);

      windowsNetStart = new BooleanArgument("windowsnetstart", null,
                              "windowsNetStart",
                              INFO_DSCORE_DESCRIPTION_WINDOWS_NET_START.get());
      windowsNetStart.setHidden(true);
      argParser.addArgument(windowsNetStart);


      fullVersion = new BooleanArgument("fullversion", 'F', "fullVersion",
                                        INFO_DSCORE_DESCRIPTION_FULLVERSION
                                                .get());
      fullVersion.setHidden(true);
      argParser.addArgument(fullVersion);


      systemInfo = new BooleanArgument("systeminfo", 's', "systemInfo",
                                       INFO_DSCORE_DESCRIPTION_SYSINFO.get());
      argParser.addArgument(systemInfo);


      useLastKnownGoodConfig =
           new BooleanArgument("lastknowngoodconfig", 'L',
                               "useLastKnownGoodConfig",
                               INFO_DSCORE_DESCRIPTION_LASTKNOWNGOODCFG.get());
      argParser.addArgument(useLastKnownGoodConfig);


      noDetach = new BooleanArgument("nodetach", 'N', "nodetach",
                                     INFO_DSCORE_DESCRIPTION_NODETACH.get());
      argParser.addArgument(noDetach);


      quietMode = new BooleanArgument("quiet", 'Q', "quiet",
                                      INFO_DESCRIPTION_QUIET.get());
      argParser.addArgument(quietMode);


      displayUsage = new BooleanArgument("help", 'H', "help",
                                         INFO_DSCORE_DESCRIPTION_USAGE.get());
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage);
    }
    catch (ArgumentException ae)
    {
      Message message = ERR_DSCORE_CANNOT_INITIALIZE_ARGS.get(ae.getMessage());
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
      Message message = ERR_DSCORE_ERROR_PARSING_ARGS.get(ae.getMessage());
      System.err.println(message);
      System.err.println(argParser.getUsage());
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
      //   START_AS_DETACH, START_AS_NON_DETACH, START_AS_WINDOWS_SERVICE to
      //   indicate that a problem occurred.
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
        LinkedList<String> newArgList = new LinkedList<String>();
        for (String arg : args)
        {
          if (! arg.equalsIgnoreCase("--checkstartability"))
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


    // At this point, we know that we're going to try to start the server.
    // Attempt to grab an exclusive lock for the Directory Server process.
    String lockFile = LockFileManager.getServerLockFileName();
    try
    {
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.acquireExclusiveLock(lockFile, failureReason))
      {
        Message message = ERR_CANNOT_ACQUIRE_EXCLUSIVE_SERVER_LOCK.get(lockFile,
                                    String.valueOf(failureReason));
        System.err.println(message);
        System.exit(1);
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }

      Message message = ERR_CANNOT_ACQUIRE_EXCLUSIVE_SERVER_LOCK.get(lockFile,
                                  stackTraceToSingleLineString(e));
      System.err.println(message);
      System.exit(1);
    }
    serverLocked = true;


    // Configure the JVM to delete the PID file on exit, if it exists.
    boolean pidFileMarkedForDeletion      = false;
    boolean startingFileMarkedForDeletion = false;
    try
    {
      String pidFilePath;
      String startingFilePath;
      String serverRoot = System.getenv(ENV_VAR_INSTANCE_ROOT);
      if (serverRoot == null)
      {
        pidFilePath      = "logs/server.pid";
        startingFilePath = "logs/server.starting";
      }
      else
      {
        pidFilePath      = serverRoot + File.separator + "logs" +
                           File.separator + "server.pid";
        startingFilePath = serverRoot + File.separator + "logs" +
                           File.separator + "server.starting";
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
      // We need to figure out where to put the file.  See if the server root
      // is available as an environment variable and if so then use it.
      // Otherwise, try to figure it out from the location of the config file.
      String serverRoot = System.getenv(ENV_VAR_INSTANCE_ROOT);
      if (serverRoot == null)
      {
        serverRoot = new File(configFile.getValue()).getParentFile().
                              getParentFile().getAbsolutePath();
      }

      if (serverRoot == null)
      {
        System.err.println("WARNING:  Unable to determine server root in " +
                           "order to redirect standard output and standard " +
                           "error.");
      }
      else
      {
        File logDir = new File(serverRoot + File.separator + "logs");
        if (logDir.exists())
        {
          FileOutputStream fos =
               new FileOutputStream(new File(logDir, "server.out"), true);
          serverOutStream = new PrintStream(fos);

          if (noDetach.isPresent())
          {
            if (! quietMode.isPresent())
            {
              MultiOutputStream multiStream =
                   new MultiOutputStream(System.out, serverOutStream);
              serverOutStream = new PrintStream(multiStream);
            }
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
    TextErrorLogPublisher startupErrorLogPublisher = null;
    TextDebugLogPublisher startupDebugLogPublisher = null;

    startupErrorLogPublisher =
        TextErrorLogPublisher.getStartupTextErrorPublisher(
            new TextWriter.STDOUT());
    ErrorLogger.addErrorLogPublisher(startupErrorLogPublisher);

    startupDebugLogPublisher =
        TextDebugLogPublisher.getStartupTextDebugPublisher(
            new TextWriter.STDOUT());
    DebugLogger.addDebugLogPublisher(startupDebugLogPublisher);


    // Create an environment configuration for the server and populate a number
    // of appropriate properties.
    DirectoryEnvironmentConfig environmentConfig =
         new DirectoryEnvironmentConfig();
    try
    {
      environmentConfig.setProperty(PROPERTY_CONFIG_CLASS,
                                    configClass.getValue());
      environmentConfig.setProperty(PROPERTY_CONFIG_FILE,
                                    configFile.getValue());
      environmentConfig.setProperty(PROPERTY_USE_LAST_KNOWN_GOOD_CONFIG,
           String.valueOf(useLastKnownGoodConfig.isPresent()));
    }
    catch (Exception e)
    {
      // This shouldn't happen.  For the methods we are using, the exception is
      // just a guard against making changes with the server running.
    }


    // Bootstrap and start the Directory Server.
    DirectoryServer directoryServer = DirectoryServer.getInstance();
    try
    {
      directoryServer.setEnvironmentConfig(environmentConfig);
      directoryServer.bootstrapServer();
      directoryServer.initializeConfiguration(configClass.getValue(),
                                              configFile.getValue());
    }
    catch (InitializationException ie)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ie);
      }

      Message message = ERR_DSCORE_CANNOT_BOOTSTRAP.get(ie.getMessage());
      System.err.println(message);
      System.exit(1);
    }
    catch (Exception e)
    {
      Message message = ERR_DSCORE_CANNOT_BOOTSTRAP.get(
              stackTraceToSingleLineString(e));
      System.err.println(message);
      System.exit(1);
    }

    try
    {
      directoryServer.startServer();
    }
    catch (InitializationException ie)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ie);
      }

      Message message = ERR_DSCORE_CANNOT_START.get(ie.getMessage());
      shutDown(directoryServer.getClass().getName(), message);
    }
    catch (Exception e)
    {
      Message message = ERR_DSCORE_CANNOT_START.get(
              stackTraceToSingleLineString(e));
      shutDown(directoryServer.getClass().getName(), message);
    }

    ErrorLogger.removeErrorLogPublisher(startupErrorLogPublisher);
    DebugLogger.removeDebugLogPublisher(startupDebugLogPublisher);
  }

  /**
   * Construct the DN of a monitor provider entry.
   * @param provider The monitor provider for which a DN is desired.
   * @return The DN of the monitor provider entry.
   */
  public static DN getMonitorProviderDN(MonitorProvider provider)
  {
    String monitorName = provider.getMonitorInstanceName();
    AttributeType cnType = getAttributeType(ATTR_COMMON_NAME);
    DN monitorRootDN;
    try
    {
      monitorRootDN = DN.decode(DN_MONITOR_ROOT);
    }
    catch (DirectoryException e)
    {
      // Cannot reach this point.
      throw new RuntimeException();
    }

    RDN rdn = RDN.create(cnType, new AttributeValue(cnType, monitorName));
    return monitorRootDN.concat(rdn);
  }



  /**
   * Gets the class loader to be used with this directory server
   * application.
   * <p>
   * The class loader will automatically load classes from plugins
   * where required.
   *
   * @return Returns the class loader to be used with this directory
   *         server application.
   */
  public static ClassLoader getClassLoader()
  {
    return ClassLoaderProvider.getInstance().getClassLoader();
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
    int returnValue;
    boolean isServerRunning;

    BooleanArgument noDetach =
      (BooleanArgument)argParser.getArgumentForLongID("nodetach");
    BooleanArgument quietMode =
      (BooleanArgument)argParser.getArgumentForLongID("quiet");
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
        Message message = ERR_CANNOT_ACQUIRE_EXCLUSIVE_SERVER_LOCK.get(lockFile,
            String.valueOf(failureReason));
        System.err.println(message);
        isServerRunning = true;
      }
    }
    catch (Exception e)
    {
      // We'll treat this as if the server is running because we won't
      // be able to start it anyway.
      Message message = ERR_CANNOT_ACQUIRE_EXCLUSIVE_SERVER_LOCK.get(lockFile,
          getExceptionMessage(e));
      System.err.println(message);
      isServerRunning = true;
    }

    boolean configuredAsService = isRunningAsWindowsService();

    if (isServerRunning)
    {
      if (configuredAsService && !windowsNetStartPresent)
      {
        returnValue = START_AS_WINDOWS_SERVICE;
      }
      else
      {
        returnValue = SERVER_ALREADY_STARTED;
      }
    }
    else
    {
      if (configuredAsService)
      {
        if (noDetachPresent)
        {
          // Conflicting arguments
          returnValue = CHECK_ERROR;
          Message message = ERR_DSCORE_ERROR_NODETACH_AND_WINDOW_SERVICE.get();
          System.err.println(message);

        }
        else
        {
          if (windowsNetStartPresent)
          {
            // start-ds.bat is being called through net start, so return
            // START_AS_DETACH_CALLED_FROM_WINDOWS_SERVICE so that the batch
            // file actually starts the server.
            returnValue = START_AS_DETACH_CALLED_FROM_WINDOWS_SERVICE;
          }
          else
          {
            returnValue = START_AS_WINDOWS_SERVICE;
          }
        }
      }
      else
      {
        if (noDetachPresent)
        {
          returnValue = START_AS_NON_DETACH;
        }
        else if (quietMode.isPresent())
        {
          returnValue = START_AS_DETACH_QUIET;
        }
        else
        {
          returnValue = START_AS_DETACH;
        }
      }
    }
    return returnValue;
  }

  /**
   * Returns true if this server is configured to run as a windows service.
   * @return <CODE>true</CODE> if this server is configured to run as a windows
   * service and <CODE>false</CODE> otherwise.
   */
  public static boolean isRunningAsWindowsService()
  {
    boolean isRunningAsWindowsService;
    if (SetupUtils.isWindows())
    {
      isRunningAsWindowsService = ConfigureWindowsService.serviceState(null,
      null) == ConfigureWindowsService.SERVICE_STATE_ENABLED;
    }
    else
    {
      isRunningAsWindowsService = false;
    }
    return isRunningAsWindowsService;
  }


  /**
   * Specifies whether the workflows are configured automatically or manually.
   * In auto configuration mode one workflow is created for each and every
   * base DN in the local backends. In the auto configuration mode the
   * workflows are created according to their description in the configuration
   * file.
   *
   * @param  workflowConfigurationMode  Indicates whether the workflows are
   *                                    configured automatically or manually
   */
  public static void setWorkflowConfigurationMode(
      WorkflowConfigurationMode workflowConfigurationMode)
  {
    directoryServer.workflowConfigurationMode = workflowConfigurationMode;
  }


  /**
   * Indicates whether the workflow configuration mode is 'auto' or not.
   *
   * @return the workflow configuration mode
   */
  public static boolean workflowConfigurationModeIsAuto()
  {
    boolean isAuto =
      (directoryServer.workflowConfigurationMode
       == WorkflowConfigurationMode.AUTO);
    return isAuto;
  }



  /**
   * Retrieves the workflow configuration mode.
   *
   * @return the workflow configuration mode
   */
  public static WorkflowConfigurationMode getWorkflowConfigurationMode()
  {
    return directoryServer.workflowConfigurationMode;
  }



  /**
   * Print messages for start-ds "-F" option (full version information).
   */

  private static
  void printFullVersionInformation() {
    /**
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
    System.out.println(SetupUtils.REVISION_NUMBER+separator+REVISION_NUMBER);
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
    System.out.println(SetupUtils.INCOMPATIBILITY_EVENTS+separator+
        StaticUtils.listToString(
            VersionCompatibilityIssue.getAllEvents(), ","));
  }

}

