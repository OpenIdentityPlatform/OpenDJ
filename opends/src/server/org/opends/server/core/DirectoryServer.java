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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.core;


import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;


import org.opends.server.api.AccountStatusNotificationHandler;
import org.opends.server.api.AlertGenerator;
import org.opends.server.api.AlertHandler;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.Backend;
import org.opends.server.api.CertificateMapper;
import org.opends.server.api.ChangeNotificationListener;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConfigHandler;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.ConnectionHandler;
import org.opends.server.api.DirectoryServerMBean;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.EntryCache;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.api.IdentityMapper;
import org.opends.server.api.InvokableComponent;
import org.opends.server.api.KeyManagerProvider;
import org.opends.server.api.MatchingRule;
import org.opends.server.api.MonitorProvider;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.PasswordGenerator;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.api.PasswordValidator;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.api.ServerShutdownListener;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.api.SynchronizationProvider;
import org.opends.server.api.TrustManagerProvider;
import org.opends.server.api.VirtualAttribute;
import org.opends.server.api.WorkQueue;
import org.opends.server.api.plugin.PluginType;
import org.opends.server.api.plugin.StartupPluginResult;
import org.opends.server.backends.RootDSEBackend;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.ConfigFileHandler;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.config.JMXMBean;
import org.opends.server.extensions.JMXAlertHandler;
import org.opends.server.loggers.StartupDebugLogger;
import org.opends.server.loggers.StartupErrorLogger;
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
import org.opends.server.schema.ObjectClassSyntax;
import org.opends.server.schema.OctetStringEqualityMatchingRule;
import org.opends.server.schema.OctetStringOrderingMatchingRule;
import org.opends.server.schema.OctetStringSubstringMatchingRule;
import org.opends.server.schema.ObjectIdentifierEqualityMatchingRule;
import org.opends.server.schema.OIDSyntax;
import org.opends.server.schema.TelephoneNumberEqualityMatchingRule;
import org.opends.server.schema.TelephoneNumberSubstringMatchingRule;
import org.opends.server.schema.TelephoneNumberSyntax;
import org.opends.server.types.AcceptRejectWarn;
import org.opends.server.types.AttributeType;
import org.opends.server.types.AttributeUsage;
import org.opends.server.types.AttributeValue;
import org.opends.server.types.DITContentRule;
import org.opends.server.types.DITStructureRule;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.MatchingRuleUse;
import org.opends.server.types.NameForm;
import org.opends.server.types.ObjectClass;
import org.opends.server.types.ObjectClassType;
import org.opends.server.types.OperatingSystem;
import org.opends.server.types.RDN;
import org.opends.server.types.ResultCode;
import org.opends.server.types.WritabilityMode;
import org.opends.server.util.TimeThread;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.loggers.Access.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.CoreMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.DynamicConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the core of the Directory Server.  It manages the startup
 * and shutdown processes and coordinates activities between all other
 * components.
 */
public class DirectoryServer
       implements Thread.UncaughtExceptionHandler, AlertGenerator
{
    /**
    * The fully-qualified name of this class for debugging purposes.
    */
    private static final String CLASS_NAME =
       "org.opends.server.core.DirectoryServer";


  /**
   * The singleton Directory Server instance.
   */
  private static DirectoryServer directoryServer = new DirectoryServer();



  // The policy to use regarding single structural objectclass enforcement.
  private AcceptRejectWarn singleStructuralClassPolicy;

  // The policy to use regarding syntax enforcement.
  private AcceptRejectWarn syntaxEnforcementPolicy;

  // The account status notification handler config manager for the server.
  private AccountStatusNotificationHandlerConfigManager
       accountStatusNotificationHandlerConfigManager;

  // The default syntax to use for binary attributes.
  private AttributeSyntax defaultBinarySyntax;

  // The default syntax to use for Boolean attributes.
  private AttributeSyntax defaultBooleanSyntax;

  // The default syntax to use for DN attributes.
  private AttributeSyntax defaultDNSyntax;

  // The default syntax to use for integer attributes.
  private AttributeSyntax defaultIntegerSyntax;

  // The default syntax to use for string attributes.
  private AttributeSyntax defaultStringSyntax;

  // The default attribute syntax to use for attributes with no defined syntax.
  private AttributeSyntax defaultSyntax;

  // The attribute type used to reference the "objectclass" attribute.
  private AttributeType objectClassAttributeType;

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

  // Indicates whether the server is currently online.
  private boolean isRunning;

  // Indicates whether the server should send a response to operations that have
  // been abandoned.
  private boolean notifyAbandonedOperations;

  // Indicates whether the server is currently in the process of shutting down.
  private boolean shuttingDown;

  // The certificate mapper used to establish a mapping between client
  // certificates and user entries.
  private CertificateMapper certificateMapper;

  // The configuration manager that will handle the certificate mapper.
  private CertificateMapperConfigManager certificateMapperConfigManager;

  // The configuration handler for the Directory Server.
  private ConfigHandler configHandler;

  // The set of account status notification handlers defined in the server.
  private ConcurrentHashMap<DN,AccountStatusNotificationHandler>
               accountStatusNotificationHandlers;

  // The set of alternate bind DNs for the root users.
  private ConcurrentHashMap<DN,DN> alternateRootBindDNs;

  // The set of identity mappers registered with the server (mapped between
  // the configuration entry Dn and the mapper).
  private ConcurrentHashMap<DN,IdentityMapper> identityMappers;

  // The set of JMX MBeans that have been registered with the server (mapped
  // between the associated configuration entry DN and the MBean).
  private ConcurrentHashMap<DN,JMXMBean> mBeans;

  // The set of password generators registered with the Directory Server, as a
  // mapping between the DN of the associated configuration entry and the
  // generator implementation.
  private ConcurrentHashMap<DN,PasswordGenerator> passwordGenerators;

  // The set of password policies registered with the Directory Server, as a
  // mapping between the DN of the associated configuration entry and the policy
  // implementation.
  private ConcurrentHashMap<DN,PasswordPolicy> passwordPolicies;

  // The set of password validators registered with the Directory Server, as a
  // mapping between the DN of the associated configuration entry and the
  // validator implementation.
  private ConcurrentHashMap<DN,PasswordValidator> passwordValidators;

  // The set of extended operation handlers registered with the server (mapped
  // between the OID of the extended operation and the handler).
  private ConcurrentHashMap<String,ExtendedOperationHandler>
               extendedOperationHandlers;

  // The set of monitor providers registered with the Directory Server, as a
  // mapping between the monitor name and the corresponding implementation.
  private ConcurrentHashMap<String,MonitorProvider> monitorProviders;

  // The set of password storage schemes defined in the server (mapped between
  // the lowercase scheme name and the storage scheme) that support the
  // authentication password syntax.
  private ConcurrentHashMap<String,PasswordStorageScheme>
               authPasswordStorageSchemes;

  // The set of password storage schemes defined in the server (mapped between
  // the lowercase scheme name and the storage scheme).
  private ConcurrentHashMap<String,PasswordStorageScheme>
               passwordStorageSchemes;

  // The set of SASL mechanism handlers registered with the server (mapped
  // between the mechanism name and the handler).
  private ConcurrentHashMap<String,SASLMechanismHandler> saslMechanismHandlers;

  // The set of virtual attributes defined in the server (mapped between the
  // lowercase names and the virtual attributes).
  private ConcurrentHashMap<String,VirtualAttribute> virtualAttributes;

  // The connection handler configuration manager for the Directory Server.
  private ConnectionHandlerConfigManager connectionHandlerConfigManager;

  // The set of alert handlers registered with the Directory Server.
  private CopyOnWriteArrayList<AlertHandler> alertHandlers;

  // The set of change notification listeners registered with the Directory
  // Server.
  private CopyOnWriteArrayList<ChangeNotificationListener>
               changeNotificationListeners;

  // The set of connection handlers registered with the Directory Server.
  private CopyOnWriteArrayList<ConnectionHandler> connectionHandlers;

  // The sets of mail server properties
  private CopyOnWriteArrayList<Properties> mailServerPropertySets;

  // The set of persistent searches registered with the Directory Server.
  private CopyOnWriteArrayList<PersistentSearch> persistentSearches;

  // The set of shutdown listeners that have been registered with the Directory
  // Server.
  private CopyOnWriteArrayList<ServerShutdownListener> shutdownListeners;

  // The set of synchronization providers that have been registered with the
  // Directory Server.
  private CopyOnWriteArrayList<SynchronizationProvider>
               synchronizationProviders;

  // The set of root DNs registered with the Directory Server.
  private CopyOnWriteArraySet<DN> rootDNs;

  // The core configuration manager for the Directory Server.
  private CoreConfigManager coreConfigManager;

  // The crypto manager for the Directory Server.
  private CryptoManager cryptoManager;

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

  // The key manager provider for the Directory Server.
  private KeyManagerProvider keyManagerProvider;

  // The key manager provider configuration manager for the Directory Server.
  private KeyManagerProviderConfigManager keyManagerProviderConfigManager;

  // The set of "private" suffixes that will be used to provide server-generated
  // data (e.g., monitor information, schema, etc.) to clients but will not be
  // searchable by default.
  private LinkedHashMap<DN,Backend> privateSuffixes;

  // The set of backends that have been registered with the server (mapped
  // between the normalized suffix and the backend).
  private LinkedHashMap<DN,Backend> suffixes;

  // The set of backends that have been registered with the server (mapped
  // between their backend ID and the backend).
  private LinkedHashMap<String,Backend> backends;

  // The set of connections that are currently established.
  private LinkedHashSet<ClientConnection> establishedConnections;

  // The logger configuration manager for the Directory Server.
  private LoggerConfigManager loggerConfigManager;

  // The number of connections currently established to the server.
  private long currentConnections;

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
  private PasswordPolicy defaultPasswordPolicy;

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

  // The SASL mechanism config manager for the Directory Server.
  SASLConfigManager saslConfigManager;

  // The schema for the Directory Server.
  private Schema schema;

  // The schema configuration manager for the Directory Server.
  private SchemaConfigManager schemaConfigManager;

  // The debug logger that will be used during the Directory Server startup.
  private StartupDebugLogger startupDebugLogger;

  // The error logger that will be used during the Directory Server startup.
  private StartupErrorLogger startupErrorLogger;

  // The fully-qualified name of the configuration handler class.
  private String configClass;

  // The path to the file containing the Directory Server configuration, or the
  // information needed to bootstrap the configuration handler.
  private String configFile;

  // The time that the server was started, formatted in UTC time.
  private String startTimeUTC;

  // The synchronization provider configuration manager for the Directory
  // Server.
  private SynchronizationProviderConfigManager
               synchronizationProviderConfigManager;

  // The thread group for all threads associated with the Directory Server.
  private ThreadGroup directoryThreadGroup;

  // The set of supported controls registered with the Directory Server.
  private TreeSet<String> supportedControls;

  // The set of supported feature OIDs registered with the Directory Server.
  private TreeSet<String> supportedFeatures;

  // The trust manager provider for the Directory Server.
  private TrustManagerProvider trustManagerProvider;

  // The trust manager provider configuration manager for the Directory Server.
  private TrustManagerProviderConfigManager trustManagerProviderConfigManager;

  // The work queue that will be used to service client requests.
  private WorkQueue workQueue;

  // The writability mode for the Directory Server.
  private WritabilityMode writabilityMode;



  /**
   * Creates a new instance of the Directory Server.  This will allow only a
   * single instance of the server per JVM.
   */
  private DirectoryServer()
  {
    isBootstrapped        = false;
    isRunning             = false;
    shuttingDown          = false;
    serverErrorResultCode = ResultCode.OTHER;

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
   * @return  The new instance of the Directory Server that is associated with
   *          this JVM.
   */
  private static DirectoryServer getNewInstance()
  {
    synchronized (directoryServer)
    {
      return directoryServer = new DirectoryServer();
    }
  }



  /**
   * Bootstraps the appropriate Directory Server structures that may be needed
   * by client-side tools.  This is not intended for use in running the server
   * itself.
   */
  public static void bootstrapClient()
  {
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
    directoryServer.passwordGenerators =
         new ConcurrentHashMap<DN,PasswordGenerator>();
    directoryServer.authPasswordStorageSchemes =
         new ConcurrentHashMap<String,PasswordStorageScheme>();
    directoryServer.passwordValidators =
         new ConcurrentHashMap<DN,PasswordValidator>();
    directoryServer.accountStatusNotificationHandlers =
         new ConcurrentHashMap<DN,AccountStatusNotificationHandler>();
    directoryServer.rootDNs = new CopyOnWriteArraySet<DN>();
    directoryServer.alternateRootBindDNs = new ConcurrentHashMap<DN,DN>();
    directoryServer.passwordPolicies =
         new ConcurrentHashMap<DN,PasswordPolicy>();
    directoryServer.defaultPasswordPolicyDN = null;
    directoryServer.defaultPasswordPolicy = null;
    directoryServer.monitorProviders =
         new ConcurrentHashMap<String,MonitorProvider>();
    directoryServer.privateSuffixes = new LinkedHashMap<DN,Backend>();
    directoryServer.suffixes = new LinkedHashMap<DN,Backend>();
    directoryServer.backends = new LinkedHashMap<String,Backend>();
    directoryServer.changeNotificationListeners =
         new CopyOnWriteArrayList<ChangeNotificationListener>();
    directoryServer.persistentSearches =
         new CopyOnWriteArrayList<PersistentSearch>();
    directoryServer.shutdownListeners =
         new CopyOnWriteArrayList<ServerShutdownListener>();
    directoryServer.synchronizationProviders =
         new CopyOnWriteArrayList<SynchronizationProvider>();
    directoryServer.supportedControls = new TreeSet<String>();
    directoryServer.supportedFeatures = new TreeSet<String>();
    directoryServer.virtualAttributes =
         new ConcurrentHashMap<String,VirtualAttribute>();
    directoryServer.connectionHandlers =
         new CopyOnWriteArrayList<ConnectionHandler>();
    directoryServer.identityMappers =
         new ConcurrentHashMap<DN,IdentityMapper>();
    directoryServer.extendedOperationHandlers =
         new ConcurrentHashMap<String,ExtendedOperationHandler>();
    directoryServer.saslMechanismHandlers =
         new ConcurrentHashMap<String,SASLMechanismHandler>();
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
        int    msgID   = MSGID_CANNOT_BOOTSTRAP_WHILE_RUNNING;
        String message = getMessage(msgID);
        throw new InitializationException(msgID, message);
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


    // Install default debug and error loggers for use until enough of the
    // configuration has been read to allow the real loggers to be installed.
    removeAllDebugLoggers(true);
    startupDebugLogger = new StartupDebugLogger();
    startupDebugLogger.initializeDebugLogger(null);
    addDebugLogger(startupDebugLogger);

    removeAllErrorLoggers(true);
    startupErrorLogger = new StartupErrorLogger();
    startupErrorLogger.initializeErrorLogger(null);
    addErrorLogger(startupErrorLogger);


    // Create the MBean server that we will use for JMX interaction.
    initializeJMX();


    logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.STARTUP_DEBUG,
             MSGID_DIRECTORY_BOOTSTRAPPING, getVersionString());
    logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.INFORMATIONAL,
             MSGID_DIRECTORY_BOOTSTRAPPING, getVersionString());


    // Perform all the bootstrapping that is shared with the client-side
    // processing.
    bootstrapClient();


    // Initialize the variables that will be used for connection tracking.
    establishedConnections = new LinkedHashSet<ClientConnection>(1000);
    currentConnections     = 0;
    maxConnections         = 0;
    totalConnections       = 0;


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
    assert debugEnter(CLASS_NAME, "initializeJMX");

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
      assert debugException(CLASS_NAME, "bootstrapServer", e);

      int    msgID   = MSGID_CANNOT_CREATE_MBEAN_SERVER;
      String message = getMessage(msgID, String.valueOf(e));
      throw new InitializationException(msgID, message, e);
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
    this.configClass = configClass;
    this.configFile  = configFile;


    // Load and instantiate the configuration handler class.
    Class handlerClass;
    try
    {
      handlerClass = Class.forName(configClass);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapServer", e);

      int    msgID   = MSGID_CANNOT_LOAD_CONFIG_HANDLER_CLASS;
      String message = getMessage(msgID, configClass, e);
      throw new InitializationException(msgID, message, e);
    }

    try
    {
      configHandler = (ConfigHandler) handlerClass.newInstance();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapServer", e);

      int    msgID   = MSGID_CANNOT_INSTANTIATE_CONFIG_HANDLER;
      String message = getMessage(msgID, configClass, e);
      throw new InitializationException(msgID, message, e);
    }


    // Perform the handler-specific initialization.
    try
    {
      configHandler.initializeConfigHandler(configFile, false);
    }
    catch (InitializationException ie)
    {
      assert debugException(CLASS_NAME, "bootstrapServer", ie);

      throw ie;
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapServer", e);

      int    msgID   = MSGID_CANNOT_INITIALIZE_CONFIG_HANDLER;
      String message = getMessage(msgID, configClass, configFile, e);
      throw new InitializationException(msgID, message);
    }
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
        int    msgID   = MSGID_CANNOT_START_BEFORE_BOOTSTRAP;
        String message = getMessage(msgID);
        throw new InitializationException(msgID, message);
      }

      if (isRunning)
      {
        int    msgID   = MSGID_CANNOT_START_WHILE_RUNNING;
        String message = getMessage(msgID);
        throw new InitializationException(msgID, message);
      }


      logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.NOTICE,
               MSGID_DIRECTORY_SERVER_STARTING, getVersionString());


      // Acquire an exclusive lock for the Directory Server process.
      String lockFile = LockFileManager.getServerLockFileName();
      try
      {
        StringBuilder failureReason = new StringBuilder();
        if (! LockFileManager.acquireExclusiveLock(lockFile, failureReason))
        {
          int    msgID   = MSGID_CANNOT_ACQUIRE_EXCLUSIVE_SERVER_LOCK;
          String message = getMessage(msgID, lockFile,
                                      String.valueOf(failureReason));
          throw new InitializationException(msgID, message);
        }
      }
      catch (InitializationException ie)
      {
        throw ie;
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "startServer", e);

        int    msgID   = MSGID_CANNOT_ACQUIRE_EXCLUSIVE_SERVER_LOCK;
        String message = getMessage(msgID, lockFile,
                                    stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }


      // Initialize all the schema elements.
      initializeSchema();


      // Initialize the core Directory Server configuration.
      coreConfigManager = new CoreConfigManager();
      coreConfigManager.initializeCoreConfig();


      // Initialize the Directory Server crypto manager.
      initializeCryptoManager();


      // Initialize the server loggers.
      loggerConfigManager = new LoggerConfigManager();
      loggerConfigManager.initializeLoggers();



      // Initialize information about the mail servers for use by the Directory
      // Server.
      initializeMailServerPropertySets();


      // Initialize the server alert handlers.
      initializeAlertHandlers();


      // Initialize the entry cache.
      entryCacheConfigManager = new EntryCacheConfigManager();
      entryCacheConfigManager.initializeEntryCache();


      // Initialize the key manager provider.
      keyManagerProviderConfigManager = new KeyManagerProviderConfigManager();
      keyManagerProviderConfigManager.initializeKeyManagerProvider();


      // Initialize the trust manager provider.
      trustManagerProviderConfigManager =
           new TrustManagerProviderConfigManager();
      trustManagerProviderConfigManager.initializeTrustManagerProvider();


      // Initialize the certificate mapper.
      certificateMapperConfigManager = new CertificateMapperConfigManager();
      certificateMapperConfigManager.initializeCertificateMapper();


      // Initialize the identity mappers.
      initializeIdentityMappers();


      // Initialize the root DNs.
      new RootDNConfigManager().initializeRootDNs();


      // Initialize all the backends and their associated suffixes.
      initializeBackends();


      // Initialize the access control handler.
      AccessControlConfigManager.getInstance().initializeAccessControl();


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
      initializeConnectionHandlers();


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


      StartupPluginResult startupPluginResult =
           pluginConfigManager.invokeStartupPlugins();
      if (! startupPluginResult.continueStartup())
      {
        int    msgID   = MSGID_STARTUP_PLUGIN_ERROR;
        String message = getMessage(msgID,
                                    startupPluginResult.getErrorMessage(),
                                    startupPluginResult.getErrorID());
        throw new InitializationException(msgID, message);
      }


      // Create and initialize the work queue.
      initializeWorkQueue();


      // At this point, we should be ready to go.  Start all the connection
      // handlers.
      for (ConnectionHandler c : connectionHandlers)
      {
        c.start();
      }


      // Mark the current time as the start time and indicate that the server is
      // now running.
      startUpTime  = System.currentTimeMillis();
      startTimeUTC = TimeThread.getUTCTime();
      isRunning    = true;

      int    msgID   = MSGID_DIRECTORY_SERVER_STARTED;
      String message = getMessage(msgID);
      logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.NOTICE, message,
               msgID);
      sendAlertNotification(this, ALERT_TYPE_SERVER_STARTED, msgID, message);


      // Deregister the startup-specific debug and error loggers.
      removeDebugLogger(startupDebugLogger);
      removeErrorLogger(startupErrorLogger);
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
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message = getMessage(msgID,
                  DoubleMetaphoneApproximateMatchingRule.class.getName(),
                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }


    try
    {
      EqualityMatchingRule matchingRule = new BooleanEqualityMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerEqualityMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message = getMessage(msgID,
                                  BooleanEqualityMatchingRule.class.getName(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }


    try
    {
      EqualityMatchingRule matchingRule = new CaseExactEqualityMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerEqualityMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message = getMessage(msgID,
                                  CaseExactEqualityMatchingRule.class.getName(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
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
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message =
           getMessage(msgID, CaseExactIA5EqualityMatchingRule.class.getName(),
                      stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }


    try
    {
      EqualityMatchingRule matchingRule = new CaseIgnoreEqualityMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerEqualityMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message = getMessage(msgID,
                            CaseIgnoreEqualityMatchingRule.class.getName(),
                            stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
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
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message =
           getMessage(msgID, CaseIgnoreIA5EqualityMatchingRule.class.getName(),
                      stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
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
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message = getMessage(msgID,
                  DistinguishedNameEqualityMatchingRule.class.getName(),
                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
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
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message = getMessage(msgID,
                  GeneralizedTimeEqualityMatchingRule.class.getName(),
                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }


    try
    {
      EqualityMatchingRule matchingRule = new IntegerEqualityMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerEqualityMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message = getMessage(msgID,
                                  IntegerEqualityMatchingRule.class.getName(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }


    try
    {
      EqualityMatchingRule matchingRule = new OctetStringEqualityMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerEqualityMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message = getMessage(msgID,
                            OctetStringEqualityMatchingRule.class.getName(),
                            stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
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
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message =
           getMessage(msgID,
                      ObjectIdentifierEqualityMatchingRule.class.getName(),
                      stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
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
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message =
           getMessage(msgID,
                      TelephoneNumberEqualityMatchingRule.class.getName(),
                      stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }


    try
    {
      OrderingMatchingRule matchingRule = new CaseExactOrderingMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerOrderingMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message = getMessage(msgID,
                                  CaseExactOrderingMatchingRule.class.getName(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }


    try
    {
      OrderingMatchingRule matchingRule = new CaseIgnoreOrderingMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerOrderingMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message = getMessage(msgID,
                            CaseIgnoreOrderingMatchingRule.class.getName(),
                            stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
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
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message = getMessage(msgID,
                            GeneralizedTimeOrderingMatchingRule.class.getName(),
                            stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }


    try
    {
      OrderingMatchingRule matchingRule = new IntegerOrderingMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerOrderingMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message = getMessage(msgID,
                                  IntegerOrderingMatchingRule.class.getName(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }


    try
    {
      OrderingMatchingRule matchingRule = new OctetStringOrderingMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerOrderingMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message = getMessage(msgID,
                            OctetStringOrderingMatchingRule.class.getName(),
                            stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }


    try
    {
      SubstringMatchingRule matchingRule = new CaseExactSubstringMatchingRule();
      matchingRule.initializeMatchingRule(null);
      registerSubstringMatchingRule(matchingRule, true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message = getMessage(msgID,
                            CaseExactSubstringMatchingRule.class.getName(),
                            stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
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
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message =
           getMessage(msgID, CaseExactIA5SubstringMatchingRule.class.getName(),
                      stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
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
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message = getMessage(msgID,
                            CaseIgnoreSubstringMatchingRule.class.getName(),
                            stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
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
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message = getMessage(msgID,
                            CaseIgnoreIA5SubstringMatchingRule.class.getName(),
                            stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
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
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message = getMessage(msgID,
                            OctetStringSubstringMatchingRule.class.getName(),
                            stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
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
      assert debugException(CLASS_NAME, "bootstrapMatchingRules", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_MATCHING_RULE;
      String message =
           getMessage(msgID,
                      TelephoneNumberSubstringMatchingRule.class.getName(),
                      stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
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
      assert debugException(CLASS_NAME, "bootstrapAttributeSyntaxes", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_SYNTAX;
      String message = getMessage(msgID, AttributeTypeSyntax.class.getName(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }


    try
    {
      defaultBinarySyntax = new BinarySyntax();
      defaultBinarySyntax.initializeSyntax(null);
      registerAttributeSyntax(defaultBinarySyntax, true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapAttributeSyntaxes", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_SYNTAX;
      String message = getMessage(msgID, BinarySyntax.class.getName(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }


    try
    {
      defaultBooleanSyntax = new BooleanSyntax();
      defaultBooleanSyntax.initializeSyntax(null);
      registerAttributeSyntax(defaultBooleanSyntax, true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapAttributeSyntaxes", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_SYNTAX;
      String message = getMessage(msgID, BooleanSyntax.class.getName(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
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
      assert debugException(CLASS_NAME, "bootstrapAttributeSyntaxes", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_SYNTAX;
      String message = getMessage(msgID, DirectoryStringSyntax.class.getName(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }


    try
    {
      defaultDNSyntax = new DistinguishedNameSyntax();
      defaultDNSyntax.initializeSyntax(null);
      registerAttributeSyntax(defaultDNSyntax, true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapAttributeSyntaxes", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_SYNTAX;
      String message = getMessage(msgID,
                                  DistinguishedNameSyntax.class.getName(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }


    try
    {
      AttributeSyntax syntax = new IA5StringSyntax();
      syntax.initializeSyntax(null);
      registerAttributeSyntax(syntax, true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapAttributeSyntaxes", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_SYNTAX;
      String message = getMessage(msgID, IA5StringSyntax.class.getName(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }


    try
    {
      defaultIntegerSyntax = new IntegerSyntax();
      defaultIntegerSyntax.initializeSyntax(null);
      registerAttributeSyntax(defaultIntegerSyntax, true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapAttributeSyntaxes", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_SYNTAX;
      String message = getMessage(msgID, IntegerSyntax.class.getName(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }


    try
    {
      AttributeSyntax syntax = new GeneralizedTimeSyntax();
      syntax.initializeSyntax(null);
      registerAttributeSyntax(syntax, true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapAttributeSyntaxes", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_SYNTAX;
      String message = getMessage(msgID, GeneralizedTimeSyntax.class.getName(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }


    try
    {
      AttributeSyntax syntax = new ObjectClassSyntax();
      syntax.initializeSyntax(null);
      registerAttributeSyntax(syntax, true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapAttributeSyntaxes", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_SYNTAX;
      String message = getMessage(msgID, ObjectClassSyntax.class.getName(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }


    try
    {
      AttributeSyntax syntax = new OIDSyntax();
      syntax.initializeSyntax(null);
      registerAttributeSyntax(syntax, true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapAttributeSyntaxes", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_SYNTAX;
      String message = getMessage(msgID, OIDSyntax.class.getName(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }


    try
    {
      AttributeSyntax syntax = new TelephoneNumberSyntax();
      syntax.initializeSyntax(null);
      registerAttributeSyntax(syntax, true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "bootstrapAttributeSyntaxes", e);

      int    msgID   = MSGID_CANNOT_BOOTSTRAP_SYNTAX;
      String message = getMessage(msgID, TelephoneNumberSyntax.class.getName(),
                                  stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.SCHEMA, ErrorLogSeverity.SEVERE_ERROR, message,
               msgID);
    }
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
    cryptoManager = new CryptoManager();
  }



  /**
   * Retrieves a reference to the Directory Server crypto manager.
   *
   * @return  A reference to the Directory Server crypto manager.
   */
  public static CryptoManager getCryptoManager()
  {
    assert debugEnter(CLASS_NAME, "getCryptoManager");

    return directoryServer.cryptoManager;
  }



  /**
   * Initializes the set of properties to use to connect to mail servers for
   * sending messages.
   *
   * @throws  ConfigException  If there is a configuration problem with any of
   *                           the mail server entries.
   *
   * @throws  InitializationException  If a problem occurs while initializing
   *                                   the mail server property sets that is not
   *                                   related to the server configuration.
   */
  public void initializeMailServerPropertySets()
         throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializeMailServerPropertySets");

    mailServerPropertySets = new CopyOnWriteArrayList<Properties>();

    // FIXME -- Actually read the information from the config handler.
    Properties defaultProperties = new Properties();
    defaultProperties.setProperty("mail.smtp.host", "127.0.0.1");
    mailServerPropertySets.add(defaultProperties);
  }



  /**
   * Retrieves the sets of information about the mail servers configured for use
   * by the Directory Server.
   *
   * @return  The sets of information about the mail servers configured for use
   *          by the Directory Server.
   */
  public static CopyOnWriteArrayList<Properties> getMailServerPropertySets()
  {
    assert debugEnter(CLASS_NAME, "getMailServerPropertySets");

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
    // FIXME -- Replace this with the real implementation.
    JMXAlertHandler alertHandler = new JMXAlertHandler();
    alertHandler.initializeAlertHandler(null);
    alertHandlers.add(alertHandler);
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
    assert debugEnter(CLASS_NAME, "initializeSchema");


    // Create the schema configuration manager, and initialize the schema from
    // the configuration.
    schemaConfigManager = new SchemaConfigManager();
    schema = schemaConfigManager.getSchema();

    schemaConfigManager.initializeMatchingRules();
    schemaConfigManager.initializeAttributeSyntaxes();
    schemaConfigManager.initializeSchemaFromFiles();


    // At this point we have a problem, because none of the configuration is
    // usable because it was all read before we had a schema (and therefore all
    // of the attribute types and objectclasses are bogus and won't let us find
    // anything).  So we have to re-read the configuration so that we can
    // continue the necessary startup process.
    try
    {
      configHandler.finalizeConfigHandler();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeSchema", e);
    }

    try
    {
      configHandler.initializeConfigHandler(configFile, true);
    }
    catch (InitializationException ie)
    {
      assert debugException(CLASS_NAME, "initializeSchema", ie);

      throw ie;
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeSchema", e);

      int    msgID   = MSGID_CANNOT_INITIALIZE_CONFIG_HANDLER;
      String message = getMessage(msgID, configClass, configFile, e);
      throw new InitializationException(msgID, message);
    }
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
    assert debugEnter(CLASS_NAME, "initializeBackends");

    backendConfigManager = new BackendConfigManager();
    backendConfigManager.initializeBackendConfig();


    // Make sure to initialize the root DSE backend separately after all other
    // backends.
    ConfigEntry rootDSEConfigEntry;
    try
    {
      DN rootDSEConfigDN = DN.decode(DN_ROOT_DSE_CONFIG);
      rootDSEConfigEntry = configHandler.getConfigEntry(rootDSEConfigDN);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeBackends", e);

      int    msgID   = MSGID_CANNOT_GET_ROOT_DSE_CONFIG_ENTRY;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }

    DN[] baseDNs   = { new DN(new RDN[0]) };
    rootDSEBackend = new RootDSEBackend();
    rootDSEBackend.initializeBackend(rootDSEConfigEntry, baseDNs);
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
    assert debugEnter(CLASS_NAME, "initializeSupportedControls");

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
    assert debugEnter(CLASS_NAME, "initializeSupportedFeatures");

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
    assert debugEnter(CLASS_NAME, "initializeIdentityMappers");

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
    assert debugEnter(CLASS_NAME, "initializeExtendedOperations");

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
    assert debugEnter(CLASS_NAME, "initializeSASLMechanisms");

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
    // NYI
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
    assert debugEnter(CLASS_NAME, "initializeConnectionHandlers");

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
    assert debugEnter(CLASS_NAME, "initializePasswordPolicyComponents");


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
    assert debugEnter(CLASS_NAME, "getOperatingSystem");

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
    assert debugEnter(CLASS_NAME, "getDirectoryThreadGroup");

    return directoryServer.directoryThreadGroup;
  }



  /**
   * Retrieves a reference to the Directory Server configuration handler.
   *
   * @return  A reference to the Directory Server configuration handler.
   */
  public static ConfigHandler getConfigHandler()
  {
    assert debugEnter(CLASS_NAME, "getConfigHandler");

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
    assert debugEnter(CLASS_NAME, "initializePlugins");

    pluginConfigManager = new PluginConfigManager();
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
    assert debugEnter(CLASS_NAME, "initializePlugins");

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
    assert debugEnter(CLASS_NAME, "getPluginConfigManager");

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
    assert debugEnter(CLASS_NAME, "getConfigEntry", String.valueOf(entryDN));

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
    assert debugEnter(CLASS_NAME, "getServerRoot");

    return directoryServer.configHandler.getServerRoot();
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
    assert debugEnter(CLASS_NAME, "getStartTime");

    return directoryServer.startUpTime;
  }



  /**
   * Retrieves the time that the Directory Server was started, formatted in UTC.
   *
   * @return  The time that the Directory Server was started, formatted in UTC.
   */
  public static String getStartTimeUTC()
  {
    assert debugEnter(CLASS_NAME, "getStartTimeUTC");

    return directoryServer.startTimeUTC;
  }



  /**
   * Retrieves a reference to the Directory Server schema.
   *
   * @return  A reference to the Directory Server schema.
   */
  public static Schema getSchema()
  {
    assert debugEnter(CLASS_NAME, "getSchema");

    return directoryServer.schema;
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
    assert debugEnter(CLASS_NAME, "getMatchingRules");

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
    assert debugEnter(CLASS_NAME, "getMatchingRuleSet");

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
    assert debugEnter(CLASS_NAME, "getMatchingRule", String.valueOf(lowerName));

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
    assert debugEnter(CLASS_NAME, "registerMatchingRule",
                      String.valueOf(matchingRule),
                      String.valueOf(overwriteExisting));

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
    assert debugEnter(CLASS_NAME, "deregisterMatchingRule",
                      String.valueOf(matchingRule));

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
    assert debugEnter(CLASS_NAME, "getApproximateMatchingRules");

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
    assert debugEnter(CLASS_NAME, "getApproximateMatchingRule",
                      String.valueOf(lowerName));

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
    assert debugEnter(CLASS_NAME, "registerApproximateMatchingRule",
                      String.valueOf(matchingRule),
                      String.valueOf(overwriteExisting));

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
    assert debugEnter(CLASS_NAME, "deregisterApproximateMatchingRule",
                      String.valueOf(matchingRule));

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
    assert debugEnter(CLASS_NAME, "getEqualityMatchingRules");

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
    assert debugEnter(CLASS_NAME, "getEqualityMatchingRule",
                      String.valueOf(lowerName));

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
    assert debugEnter(CLASS_NAME, "registerEqualityMatchingRule",
                      String.valueOf(matchingRule),
                      String.valueOf(overwriteExisting));

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
    assert debugEnter(CLASS_NAME, "deregisterEqualityMatchingRule",
                      String.valueOf(matchingRule));

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
    assert debugEnter(CLASS_NAME, "getOrderingMatchingRules");

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
    assert debugEnter(CLASS_NAME, "getOrderingMatchingRule",
                      String.valueOf(lowerName));

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
    assert debugEnter(CLASS_NAME, "registerOrderingMatchingRule",
                      String.valueOf(matchingRule),
                      String.valueOf(overwriteExisting));

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
    assert debugEnter(CLASS_NAME, "deregisterOrderingMatchingRule",
                      String.valueOf(matchingRule));

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
    assert debugEnter(CLASS_NAME, "getSubstringMatchingRules");

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
    assert debugEnter(CLASS_NAME, "getSubstringMatchingRule",
                      String.valueOf(lowerName));

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
    assert debugEnter(CLASS_NAME, "registerSubstringMatchingRule",
                      String.valueOf(matchingRule),
                      String.valueOf(overwriteExisting));

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
    assert debugEnter(CLASS_NAME, "deregisterSubstringMatchingRule",
                      String.valueOf(matchingRule));

    directoryServer.schema.deregisterSubstringMatchingRule(matchingRule);
  }



  /**
   * Retrieves the set of objectclasses defined in the Directory Server.
   *
   * @return  The set of objectclasses defined in the Directory Server.
   */
  public static ConcurrentHashMap<String,ObjectClass> getObjectClasses()
  {
    assert debugEnter(CLASS_NAME, "getObjectClasses");

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
    assert debugEnter(CLASS_NAME, "getObjectClassSet");

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
    assert debugEnter(CLASS_NAME, "getObjectClass", String.valueOf(lowerName));

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
   *          schema a default class should not be returned.
   */
  public static ObjectClass getObjectClass(String lowerName,
                                           boolean returnDefault)
  {
    assert debugEnter(CLASS_NAME, "getObjectClass", String.valueOf(lowerName),
                      String.valueOf(returnDefault));

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
    assert debugEnter(CLASS_NAME, "registerObjectClass",
                      String.valueOf(objectClass),
                      String.valueOf(overwriteExisting));

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
    assert debugEnter(CLASS_NAME, "deregisterObjectClass",
                      String.valueOf(objectClass));

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
    assert debugEnter(CLASS_NAME, "getTopObjectClass");

    ObjectClass objectClass =
         directoryServer.schema.getObjectClass(TOP_OBJECTCLASS_NAME);
    if (objectClass == null)
    {
      ConcurrentHashMap<String,String> names =
           new ConcurrentHashMap<String,String>(1);
      names.put(TOP_OBJECTCLASS_NAME, TOP_OBJECTCLASS_NAME);

      CopyOnWriteArraySet<AttributeType> requiredAttrs =
           new CopyOnWriteArraySet<AttributeType>();
      CopyOnWriteArraySet<AttributeType> optionalAttrs =
           new CopyOnWriteArraySet<AttributeType>();

      ConcurrentHashMap<String,CopyOnWriteArrayList<String>> extraProperties =
           new ConcurrentHashMap<String,CopyOnWriteArrayList<String>>(0);

      objectClass = new ObjectClass(TOP_OBJECTCLASS_NAME, names,
                                    TOP_OBJECTCLASS_OID,
                                    TOP_OBJECTCLASS_DESCRIPTION, null,
                                    requiredAttrs, optionalAttrs,
                                    ObjectClassType.ABSTRACT, false,
                                    extraProperties);
    }

    return objectClass;
  }



  /**
   * Causes the Directory Server to construct a new objectclass definition with
   * the provided name and with no required or allowed attributes.  This should
   * only be used if there is no objectclass for the specified name.  It will
   * not register the created objectclass with the Directory Server.
   *
   * @param  name  The name to use for the objectclass, as provided by the user.
   *
   * @return  The constructed objectclass definition.
   */
  public static ObjectClass getDefaultObjectClass(String name)
  {
    assert debugEnter(CLASS_NAME, "getDefaultObjectClass",
                      String.valueOf(name));

    String lowerName = toLowerCase(name);
    ObjectClass objectClass = directoryServer.schema.getObjectClass(lowerName);
    if (objectClass == null)
    {
      ConcurrentHashMap<String,String> names =
           new ConcurrentHashMap<String,String>(1);
      names.put(lowerName, name);

      CopyOnWriteArraySet<AttributeType> requiredAttrs =
           new CopyOnWriteArraySet<AttributeType>();
      CopyOnWriteArraySet<AttributeType> optionalAttrs =
           new CopyOnWriteArraySet<AttributeType>();

      ConcurrentHashMap<String,CopyOnWriteArrayList<String>> extraProperties =
           new ConcurrentHashMap<String,CopyOnWriteArrayList<String>>(0);

      objectClass = new ObjectClass(name, names, lowerName, null,
                                    getTopObjectClass(), requiredAttrs,
                                    optionalAttrs, ObjectClassType.ABSTRACT,
                                    false, extraProperties);
    }

    return objectClass;
  }



  /**
   * Retrieves the set of attribute type definitions that have been defined in
   * the Directory Server.
   *
   * @return  The set of attribute type definitions that have been defined in
   *          the Directory Server.
   */
  public static ConcurrentHashMap<String,AttributeType> getAttributeTypes()
  {
    assert debugEnter(CLASS_NAME, "getAttributeTypes");

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
    assert debugEnter(CLASS_NAME, "getAttributeTypeSet");

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
    assert debugEnter(CLASS_NAME, "getAttributeType",
                      String.valueOf(lowerName));

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
    assert debugEnter(CLASS_NAME, "getAttributeType",
                      String.valueOf(lowerName),
                      String.valueOf(returnDefault));

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
    assert debugEnter(CLASS_NAME, "registerAttributeType",
                      String.valueOf(attributeType),
                      String.valueOf(overwriteExisting));

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
    assert debugEnter(CLASS_NAME, "deregisterAttributeType",
                      String.valueOf(attributeType));

    directoryServer.schema.deregisterAttributeType(attributeType);
  }



  /**
   * Retrieves the attribute type for the "objectClass" attribute.
   *
   * @return  The attribute type for the "objectClass" attribute.
   */
  public static AttributeType getObjectClassAttributeType()
  {
    assert debugEnter(CLASS_NAME, "getObjectClassAttributeType");

    if (directoryServer.objectClassAttributeType == null)
    {
      directoryServer.objectClassAttributeType =
           directoryServer.schema.getAttributeType(
                OBJECTCLASS_ATTRIBUTE_TYPE_NAME);

      if (directoryServer.objectClassAttributeType == null)
      {
        ConcurrentHashMap<String,String> typeNames =
             new ConcurrentHashMap<String,String>();
        typeNames.put(OBJECTCLASS_ATTRIBUTE_TYPE_NAME, "objectClass");

        AttributeSyntax oidSyntax =
             directoryServer.schema.getSyntax(SYNTAX_OID_NAME);
        if (oidSyntax == null)
        {
          try
          {
            oidSyntax = new OIDSyntax();
            oidSyntax.initializeSyntax(null);
            directoryServer.schema.registerSyntax(oidSyntax, true);
          }
          catch (Exception e)
          {
            assert debugException(CLASS_NAME, "getObjectClassAttributeType", e);
          }
        }

        directoryServer.objectClassAttributeType =
             new AttributeType("objectClass", typeNames,
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
          assert debugException(CLASS_NAME, "getObjectClassAttributeType", e);
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
    assert debugEnter(CLASS_NAME, "getDefaultAttributeType",
                      String.valueOf(name));


    String lowerName = toLowerCase(name);
    ConcurrentHashMap<String,String> names =
         new ConcurrentHashMap<String,String>(1);
    names.put(lowerName, name);

    return new AttributeType(name, names, lowerName, null, null,
                             getDefaultAttributeSyntax(),
                             AttributeUsage.USER_APPLICATIONS, false, false,
                             false, false);
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
    assert debugEnter(CLASS_NAME, "getDefaultAttributeType",
                      String.valueOf(name));


    String lowerName = toLowerCase(name);
    ConcurrentHashMap<String,String> names =
         new ConcurrentHashMap<String,String>(1);
    names.put(lowerName, name);

    return new AttributeType(name, names, lowerName, null, null, syntax,
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
    assert debugEnter(CLASS_NAME, "getAttributeSyntaxes");

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
    assert debugEnter(CLASS_NAME, "getAttributeSyntaxSet");

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
    assert debugEnter(CLASS_NAME, "getAttributeSyntax", String.valueOf(oid),
                      String.valueOf(allowDefault));

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
    assert debugEnter(CLASS_NAME, "registerAttributeSyntax",
                      String.valueOf(syntax),
                      String.valueOf(overwriteExisting));

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
    assert debugEnter(CLASS_NAME, "deregisterAttributeSyntax",
                      String.valueOf(syntax));

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
    assert debugEnter(CLASS_NAME, "getDefaultAttributeSyntax");

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
    assert debugEnter(CLASS_NAME, "getDefaultBinarySyntax");

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
    assert debugEnter(CLASS_NAME, "getDefaultBooleanSyntax");

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
    assert debugEnter(CLASS_NAME, "getDefaultDNSyntax");

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
    assert debugEnter(CLASS_NAME, "getDefaultIntegerSyntax");

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
    assert debugEnter(CLASS_NAME, "getDefaultStringSyntax");

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
    assert debugEnter(CLASS_NAME, "getMatchingRuleUses");

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
    assert debugEnter(CLASS_NAME, "getMatchingRuleUseSet");

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
    assert debugEnter(CLASS_NAME, "getMatchingRuleUse",
                      String.valueOf(matchingRule));

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
    assert debugEnter(CLASS_NAME, "registerMatchingRuleUse",
                      String.valueOf(matchingRuleUse),
                      String.valueOf(overwriteExisting));

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
    assert debugEnter(CLASS_NAME, "deregisterMatchingRuleUse",
                      String.valueOf(matchingRuleUse));

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
    assert debugEnter(CLASS_NAME, "getDITContentRules");

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
    assert debugEnter(CLASS_NAME, "getDITContentRuleSet");

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
    assert debugEnter(CLASS_NAME, "getDITContentRule",
                      String.valueOf(objectClass));

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
    assert debugEnter(CLASS_NAME, "registerDITContentRule",
                      String.valueOf(ditContentRule),
                      String.valueOf(overwriteExisting));

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
    assert debugEnter(CLASS_NAME, "deregisterDITContentRule",
                      String.valueOf(ditContentRule));

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
    assert debugEnter(CLASS_NAME, "getDITStructureRules");

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
    assert debugEnter(CLASS_NAME, "getDITStructureRuleSet");

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
    assert debugEnter(CLASS_NAME, "getDITStructureRule",
                      String.valueOf(ruleID));

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
    assert debugEnter(CLASS_NAME, "getDITStructureRule",
                      String.valueOf(nameForm));

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
    assert debugEnter(CLASS_NAME, "registerDITStructureRule",
                      String.valueOf(ditStructureRule),
                      String.valueOf(overwriteExisting));

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
    assert debugEnter(CLASS_NAME, "deregisterDITStructureRule",
                      String.valueOf(ditStructureRule));

    directoryServer.schema.deregisterDITStructureRule(ditStructureRule);
  }



  /**
   * Retrieves the set of name forms defined in the Directory Server.
   *
   * @return  The set of name forms defined in the Directory Server.
   */
  public static ConcurrentHashMap<ObjectClass,NameForm> getNameForms()
  {
    assert debugEnter(CLASS_NAME, "getNameForms");

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
    assert debugEnter(CLASS_NAME, "getNameFormSet");

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
    assert debugEnter(CLASS_NAME, "getNameForm", String.valueOf(objectClass));

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
    assert debugEnter(CLASS_NAME, "getNameForm", String.valueOf(lowerName));

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
    assert debugEnter(CLASS_NAME, "registerNameForm", String.valueOf(nameForm),
                      String.valueOf(overwriteExisting));

    directoryServer.schema.registerNameForm(nameForm, overwriteExisting);
  }



  /**
   * Deregisters the provided name form with the Directory Server.
   *
   * @param  nameForm  The name form to deregister with the server.
   */
  public static void deregisterNameForm(NameForm nameForm)
  {
    assert debugEnter(CLASS_NAME, "deregisterNameForm",
                      String.valueOf(nameForm));

    directoryServer.schema.deregisterNameForm(nameForm);
  }



  /**
   * Retrieves a reference to the JMX MBean server that is associated with the
   * Directory Server.
   *
   * @return  The JMX MBean server that is associated with the Directory Server.
   */
  public static MBeanServer getJMXMBeanServer()
  {
    assert debugEnter(CLASS_NAME, "getJMXMBeanServer");

    return directoryServer.mBeanServer;
  }



  /**
   * Retrieves the set of JMX MBeans that are associated with the server.
   *
   * @return  The set of JMX MBeans that are associated with the server.
   */
  public static ConcurrentHashMap<DN,JMXMBean> getJMXMBeans()
  {
    assert debugEnter(CLASS_NAME, "getJMXMBeans");

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
    assert debugEnter(CLASS_NAME, "getJMXMBean", String.valueOf(configEntryDN));

    return directoryServer.mBeans.get(configEntryDN);
  }



  /**
   * Registers the provided configurable component with the Directory Server.
   *
   * @param  component  The configurable component to register.
   */
  public static void registerConfigurableComponent(ConfigurableComponent
                                                        component)
  {
    assert debugEnter(CLASS_NAME, "registerConfigurableComponent",
                      String.valueOf(component));

    DN componentDN = component.getConfigurableComponentEntryDN();
    JMXMBean mBean = directoryServer.mBeans.get(componentDN);
    if (mBean == null)
    {
      mBean = new JMXMBean(componentDN);
      mBean.addConfigurableComponent(component);
      directoryServer.mBeans.put(componentDN, mBean);
    }
    else
    {
      mBean.addConfigurableComponent(component);
    }
  }



  /**
   * Deregisters the provided configurable component with the Directory Server.
   *
   * @param  component  The configurable component to deregister.
   */
  public static void deregisterConfigurableComponent(ConfigurableComponent
                                                          component)
  {
    assert debugEnter(CLASS_NAME, "deregisterConfigurableComponent",
                      String.valueOf(component));

    DN componentDN = component.getConfigurableComponentEntryDN();
    JMXMBean mBean = directoryServer.mBeans.get(componentDN);
    if (mBean != null)
    {
      mBean.removeConfigurableComponent(component);
    }
  }



  /**
   * Registers the provided invokable component with the Directory Server.
   *
   * @param  component  The invokable component to register.
   */
  public static void registerInvokableComponent(InvokableComponent component)
  {
    assert debugEnter(CLASS_NAME, "registerInvokableComponent",
                      String.valueOf(component));

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
    assert debugEnter(CLASS_NAME, "deregisterInvokableComponent",
                      String.valueOf(component));

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
    assert debugEnter(CLASS_NAME, "registerAlertGenerator");

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
    assert debugEnter(CLASS_NAME, "deregisterAlertGenerator");

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
    assert debugEnter(CLASS_NAME, "getAlertHandlers");

    return directoryServer.alertHandlers;
  }



  /**
   * Registers the provided alert handler with the Directory Server.
   *
   * @param  alertHandler  The alert handler to register.
   */
  public static void registerAlertHandler(AlertHandler alertHandler)
  {
    assert debugEnter(CLASS_NAME, "registerAlertHandler",
                      String.valueOf(alertHandler));

    directoryServer.alertHandlers.add(alertHandler);
  }



  /**
   * Deregisters the provided alert handler with the Directory Server.
   *
   * @param  alertHandler  The alert handler to deregister.
   */
  public static void deregisterAlertHandler(AlertHandler alertHandler)
  {
    assert debugEnter(CLASS_NAME, "deregisterAlertHandler",
                      String.valueOf(alertHandler));

    directoryServer.alertHandlers.remove(alertHandler);
  }



  /**
   * Sends an alert notification with the provided information.
   *
   * @param  generator     The alert generator that created the alert.
   * @param  alertType     The alert type name for this alert.
   * @param  alertID       The alert ID that uniquely identifies the type of
   *                       alert.
   * @param  alertMessage  A message (possibly <CODE>null</CODE>) that can
   *                       provide more information about this alert.
   */
  public static void sendAlertNotification(AlertGenerator generator,
                                           String alertType, int alertID,
                                           String alertMessage)
  {
    assert debugEnter(CLASS_NAME, "sendAlertNotification",
                      String.valueOf(generator), String.valueOf(alertType),
                      String.valueOf(alertID), String.valueOf(alertMessage));


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
          alertHandler.sendAlertNotification(generator, alertType, alertID,
                                             alertMessage);
        }
        catch (Exception e)
        {
          assert debugException(CLASS_NAME, "sendAlertNotification", e);
        }
      }
    }
    else
    {
      for (AlertHandler alertHandler : directoryServer.alertHandlers)
      {
        alertHandler.sendAlertNotification(generator, alertType, alertID,
                                           alertMessage);
      }
    }


    int msgID = MSGID_SENT_ALERT_NOTIFICATION;
    String message = getMessage(msgID, generator.getClassName(), alertType,
                                alertID, alertMessage);
    logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.NOTICE,
             message, msgID);
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
    assert debugEnter(CLASS_NAME, "getPasswordStorageSchemes");

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
    assert debugEnter(CLASS_NAME, "getPasswordStorageScheme",
                      String.valueOf(lowerName));

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
    assert debugEnter(CLASS_NAME, "getAuthPasswordStorageSchemes");

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
    assert debugEnter(CLASS_NAME, "getAuthPasswordStorageScheme",
                      String.valueOf(name));

    return directoryServer.authPasswordStorageSchemes.get(name);
  }



  /**
   * Registers the provided password storage scheme with the Directory Server.
   * If an existing password storage scheme is registered with the same name,
   * then it will be replaced with the provided scheme.
   *
   * @param  scheme  The password storage scheme to register with the Directory
   *                 Server.
   */
  public static void registerPasswordStorageScheme(PasswordStorageScheme scheme)
  {
    assert debugEnter(CLASS_NAME, "registerPasswordStorageScheme",
                      String.valueOf(scheme));

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
   * @param  lowerName  The name of the password storage scheme to deregister,
   *                    formatted in all lowercache characters.
   */
  public static void deregisterPasswordStorageScheme(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "deregisterPasswordStorageScheme",
                      String.valueOf(lowerName));

    PasswordStorageScheme scheme =
         directoryServer.passwordStorageSchemes.remove(lowerName);

    if ((scheme != null) && scheme.supportsAuthPasswordSyntax())
    {
      directoryServer.authPasswordStorageSchemes.remove(
           scheme.getAuthPasswordSchemeName());
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
  public static ConcurrentHashMap<DN,PasswordValidator> getPasswordValidators()
  {
    assert debugEnter(CLASS_NAME, "getPasswordValidators");

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
  public static PasswordValidator getPasswordValidator(DN configEntryDN)
  {
    assert debugEnter(CLASS_NAME, "getPasswordValidator",
                      String.valueOf(configEntryDN));

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
  public static void registerPasswordValidator(DN configEntryDN,
                                               PasswordValidator validator)
  {
    assert debugEnter(CLASS_NAME, "registerPasswordValidator",
                      String.valueOf(configEntryDN), String.valueOf(validator));

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
    assert debugEnter(CLASS_NAME, "deregisterPasswordValidator",
                      String.valueOf(configEntryDN));

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
    assert debugEnter(CLASS_NAME, "getAccountStatusNotificationHandlers");

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
    assert debugEnter(CLASS_NAME, "getAccountStatusNotificationHandler",
                      String.valueOf(handlerDN));

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
    assert debugEnter(CLASS_NAME, "registerAccountStatusNotificationHandler",
                      String.valueOf(handlerDN), String.valueOf(handler));

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
    assert debugEnter(CLASS_NAME, "deregisterAccountStatusNotificationHandler",
                      String.valueOf(handlerDN));

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
    assert debugEnter(CLASS_NAME, "getPasswordGenerators");

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
    assert debugEnter(CLASS_NAME, "getPasswordGenerator",
                      String.valueOf(configEntryDN));

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
    assert debugEnter(CLASS_NAME, "registerPasswordGenerator",
                      String.valueOf(configEntryDN), String.valueOf(generator));

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
    assert debugEnter(CLASS_NAME, "deregisterPasswordGenerator",
                      String.valueOf(configEntryDN));

    directoryServer.passwordGenerators.remove(configEntryDN);
  }



  /**
   * Retrieves the set of password policies defined in the Directory Server as a
   * mapping between the DN of the associated configuration entry and the
   * corresponding policy.
   *
   * @return  The set of password policies defined in the Directory Server.
   */
  public static ConcurrentHashMap<DN,PasswordPolicy> getPasswordPolicies()
  {
    assert debugEnter(CLASS_NAME, "getPasswordPolicies");

    return directoryServer.passwordPolicies;
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
    assert debugEnter(CLASS_NAME, "getPasswordPolicy",
                      String.valueOf(configEntryDN));

    return directoryServer.passwordPolicies.get(configEntryDN);
  }



  /**
   * Registers the provided password policy with the Directory Server.  If a
   * policy is already registered for the provided configuration entry DN, then
   * it will be replaced.
   *
   * @param  configEntryDN  The DN of the configuration entry that defines the
   *                        password policy.
   * @param  policy         The password policy to register with the server.
   */
  public static void registerPasswordPolicy(DN configEntryDN,
                                            PasswordPolicy policy)
  {
    assert debugEnter(CLASS_NAME, "registerPasswordPolicy",
                      String.valueOf(configEntryDN), String.valueOf(policy));

    directoryServer.passwordPolicies.put(configEntryDN, policy);
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
    assert debugEnter(CLASS_NAME, "deregisterPasswordPolicy",
                      String.valueOf(configEntryDN));

    directoryServer.passwordPolicies.remove(configEntryDN);
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
    assert debugEnter(CLASS_NAME, "getDefaultPasswordPolicyDN");

    return directoryServer.defaultPasswordPolicyDN;
  }



  /**
   * Specifies the DN of the configuration entry for the default password policy
   * for the Directory Server.
   *
   * @param  defaultPasswordPolicyDN  The DN of the configuration entry for the
   *                                  default password policy for the Directory
   *                                  Server.
   */
  public static void setDefaultPasswordPolicyDN(DN defaultPasswordPolicyDN)
  {
    assert debugEnter(CLASS_NAME, "setDefaultPasswordPolicyDN",
                      String.valueOf(defaultPasswordPolicyDN));

    directoryServer.defaultPasswordPolicyDN = defaultPasswordPolicyDN;
    directoryServer.defaultPasswordPolicy   = null;
  }



  /**
   * Retrieves the default password policy for the Directory Server.
   *
   * @return  The default password policy for the Directory Server.
   */
  public static PasswordPolicy getDefaultPasswordPolicy()
  {
    assert debugEnter(CLASS_NAME, "getDefaultPasswordPolicy");

    if ((directoryServer.defaultPasswordPolicy == null) &&
        (directoryServer.defaultPasswordPolicyDN != null))
    {
      directoryServer.defaultPasswordPolicy =
           directoryServer.passwordPolicies.get(
                directoryServer.defaultPasswordPolicyDN);
    }

    return directoryServer.defaultPasswordPolicy;
  }



  /**
   * Specifies the default password policy for the Directory Server.  It will
   * still be necessary to register this policy with the set of defined password
   * policies.
   *
   * @param  defaultPasswordPolicy  The default password policy for the
   *                                Directory Server.
   */
  public static void setDefaultPasswordPolicy(PasswordPolicy
                                                   defaultPasswordPolicy)
  {
    assert debugEnter(CLASS_NAME, "setDefaultPasswordPolicy",
                      String.valueOf(defaultPasswordPolicy));

    directoryServer.defaultPasswordPolicy = defaultPasswordPolicy;
    directoryServer.defaultPasswordPolicyDN =
         defaultPasswordPolicy.getConfigurableComponentEntryDN();
  }



  /**
   * Retrieves the set of monitor providers that have been registered with the
   * Directory Server, as a mapping between the monitor name (in all lowercase
   * characters) and the monitor implementation.
   *
   * @return  The set of monitor providers that have been registered with the
   *          Directory Server.
   */
  public static ConcurrentHashMap<String,MonitorProvider> getMonitorProviders()
  {
    assert debugEnter(CLASS_NAME, "getMonitorProviders");

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
  public static MonitorProvider getMonitorProvider(String lowerName)
  {
    assert debugEnter(CLASS_NAME, "getMonitorProvider",
                      String.valueOf(lowerName));

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
  public static void registerMonitorProvider(MonitorProvider monitorProvider)
  {
    assert debugEnter(CLASS_NAME, "registerMonitorProvider",
                      String.valueOf(monitorProvider));

    String lowerName = toLowerCase(monitorProvider.getMonitorInstanceName());
    directoryServer.monitorProviders.put(lowerName, monitorProvider);


    // Try to register this monitor provider with an appropriate JMX MBean.
    try
    {
      DN monitorDN =
           DN.decode("cn=" + monitorProvider.getMonitorInstanceName() +
                     ",cn=monitor");
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
      assert debugException(CLASS_NAME, "registerMonitorProvider", e);
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
    assert debugEnter(CLASS_NAME, "deregisterMonitorProvider",
                      String.valueOf(lowerName));

    MonitorProvider provider =
         directoryServer.monitorProviders.remove(toLowerCase(lowerName));


    // Try to deregister the monitor provider as an MBean.
    if (provider != null)
    {
      try
      {
        DN monitorDN =
             DN.decode("cn=" + provider.getMonitorInstanceName() +
                       ",cn=monitor");
        JMXMBean mBean = directoryServer.mBeans.get(monitorDN);
        if (mBean != null)
        {
          mBean.removeMonitorProvider(provider);
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "deregisterMonitorProvider", e);
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
    assert debugEnter(CLASS_NAME, "getEntryCache");

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
    assert debugEnter(CLASS_NAME, "setEntryCache");

    synchronized (directoryServer)
    {
      directoryServer.entryCache = entryCache;
    }
  }



  /**
   * Retrieves the key manager provider for the Directory Server.
   *
   * @return  The key manager provider for the Directory Server.
   */
  public static KeyManagerProvider getKeyManagerProvider()
  {
    assert debugEnter(CLASS_NAME, "getKeyManagerProvider");

    return directoryServer.keyManagerProvider;
  }



  /**
   * Specifies the key manager provider for the Directory Server.
   *
   * @param  keyManagerProvider  The key manager provider for the Directory
   *                             Server.
   */
  public static void setKeyManagerProvider(KeyManagerProvider
                                                keyManagerProvider)
  {
    assert debugEnter(CLASS_NAME, "setKeyManagerProvider",
                      String.valueOf(keyManagerProvider));

    directoryServer.keyManagerProvider = keyManagerProvider;
  }



  /**
   * Retrieves the trust manager provider for the Directory Server.
   *
   * @return  The trust manager provider for the Directory Server.
   */
  public static TrustManagerProvider getTrustManagerProvider()
  {
    assert debugEnter(CLASS_NAME, "getTrustManagerProvider");

    return directoryServer.trustManagerProvider;
  }



  /**
   * Specifies the trust manager provider for the Directory Server.
   *
   * @param  trustManagerProvider  The trust manager provider for the Directory
   *                               Server.
   */
  public static void setTrustManagerProvider(TrustManagerProvider
                                                  trustManagerProvider)
  {
    assert debugEnter(CLASS_NAME, "setTrustManagerProvider",
                      String.valueOf(trustManagerProvider));

    directoryServer.trustManagerProvider = trustManagerProvider;
  }



  /**
   * Retrieves the certificate mapper for the Directory Server.
   *
   * @return  The certificate mapper for the Directory Server.
   */
  public static CertificateMapper getCertificateMapper()
  {
    assert debugEnter(CLASS_NAME, "getCertificateMapper");

    return directoryServer.certificateMapper;
  }



  /**
   * Specifies the certificate mapper for the Directory Server.
   *
   * @param  certificateMapper  The certificate mapper for the Directory Server.
   */
  public static void setCertificateMapper(CertificateMapper certificateMapper)
  {
    assert debugEnter(CLASS_NAME, "setCertificateMapper",
                      String.valueOf(certificateMapper));

    directoryServer.certificateMapper = certificateMapper;
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
    assert debugEnter(CLASS_NAME, "getRootDNs");

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
    assert debugEnter(CLASS_NAME, "isRootDN", String.valueOf(userDN));

    return directoryServer.rootDNs.contains(userDN);
  }



  /**
   * Registers the provided root DN with the Directory Server.
   *
   * @param  rootDN  The root DN to register with the Directory Server.
   */
  public static void registerRootDN(DN rootDN)
  {
    assert debugEnter(CLASS_NAME, "registerRootDN", String.valueOf(rootDN));

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
    assert debugEnter(CLASS_NAME, "deregisterRootDN", String.valueOf(rootDN));

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
    assert debugEnter(CLASS_NAME, "getAlternateRootBindDNs");

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
    assert debugEnter(CLASS_NAME, "getActualRootBindDN",
                      String.valueOf(alternateRootBindDN));

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
    assert debugEnter(CLASS_NAME, "registerAlternateRootDN",
                      String.valueOf(actualRootEntryDN),
                      String.valueOf(alternateRootBindDN));

    DN existingRootEntryDN =
         directoryServer.alternateRootBindDNs.putIfAbsent(alternateRootBindDN,
                                                          actualRootEntryDN);
    if ((existingRootEntryDN != null) &&
        (! existingRootEntryDN.equals(actualRootEntryDN)))
    {
      int   msgID    = MSGID_CANNOT_REGISTER_DUPLICATE_ALTERNATE_ROOT_BIND_DN;
      String message = getMessage(msgID, String.valueOf(alternateRootBindDN),
                                  String.valueOf(existingRootEntryDN));
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
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
    assert debugEnter(CLASS_NAME, "deregisterAlternateRootBindDN",
                      String.valueOf(alternateRootBindDN));

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
    assert debugEnter(CLASS_NAME, "getServerErrorResultCode");

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
    assert debugEnter(CLASS_NAME, "setServerErrorResultCode",
                      String.valueOf(serverErrorResultCode));

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
    assert debugEnter(CLASS_NAME, "addMissingRDNAttributes");

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
    assert debugEnter(CLASS_NAME, "setAddMissingRDNAttributes",
                      String.valueOf(addMissingRDNAttributes));

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
    assert debugEnter(CLASS_NAME, "allowAttributeNameExceptions");

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
    assert debugEnter(CLASS_NAME, "setAllowAttributeNameExceptions",
                      String.valueOf(allowAttributeNameExceptions));

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
    assert debugEnter(CLASS_NAME, "checkSchema");

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
    assert debugEnter(CLASS_NAME, "setCheckSchema",
                      String.valueOf(checkSchema));

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
    assert debugEnter(CLASS_NAME, "getSingleStructuralObjectClassPolicy");

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
    assert debugEnter(CLASS_NAME, "getSingleStructuralObjectClassPolicy");

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
    assert debugEnter(CLASS_NAME, "getSyntaxEnforcementPolicy");

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
    assert debugEnter(CLASS_NAME, "getSyntaxEnforcementPolicy");

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
    assert debugEnter(CLASS_NAME, "notifyAbandonedOperations");

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
    assert debugEnter(CLASS_NAME, "setNotifyAbandonedOperations",
                      String.valueOf(notifyAbandonedOperations));

    directoryServer.notifyAbandonedOperations = notifyAbandonedOperations;
  }



  /**
   * Retrieves the set of backends that have been registered with the Directory
   * Server.
   *
   * @return  The set of backends that have been registered with the Directory
   *          Server.
   */
  public static LinkedHashMap<String,Backend> getBackends()
  {
    assert debugEnter(CLASS_NAME, "getBackends");

    return directoryServer.backends;
  }



  /**
   * Retrieves the backend with the specified backend ID.
   *
   * @param  backendID  The backend ID of the backend to retrieve.
   *
   * @return  The backend with the specified backend ID, or <CODE>null</CODE> if
   *          there is none.
   */
  public static Backend getBackend(String backendID)
  {
    assert debugEnter(CLASS_NAME, "getBackend", String.valueOf(backendID));

    return directoryServer.backends.get(backendID);
  }



  /**
   * Indicates whether the Directory Server has a backend with the specified
   * backend ID.
   *
   * @param  backendID  The backend ID for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the Directory Server has a backend with the
   *          specified backend ID, or <CODE>false</CODE> if not.
   */
  public static boolean hasBackend(String backendID)
  {
    assert debugEnter(CLASS_NAME, "hasBackend", String.valueOf(backendID));

    return directoryServer.backends.containsKey(backendID);
  }



  /**
   * Registers the provided backend with the Directory Server.  Note that this
   * will not register the set of configured suffixes with the server, as that
   * must be done by the backend itself.
   *
   * @param  backend  The backend to register with the server.
   */
  public static void registerBackend(Backend backend)
  {
    assert debugEnter(CLASS_NAME, "registerBackend", String.valueOf(backend));

    directoryServer.backends.put(backend.getBackendID(), backend);
  }



  /**
   * Deregisters the provided backend with the Directory Server.  Note that this
   * will not deregister the set of configured suffixes with the server, as that
   * must be done by the backend itself.
   *
   * @param  backend  the backend to deregister with the server.
   */
  public static void deregisterBackend(Backend backend)
  {
    assert debugEnter(CLASS_NAME, "deregisterBackend", String.valueOf(backend));

    directoryServer.backends.remove(backend.getBackendID());
  }



  /**
   * Retrieves the set of suffixes that have been registered with the Directory
   * Server.
   *
   * @return  The set of suffixes that have been registered with the Directory
   *          Server.
   */
  public static LinkedHashMap<DN,Backend> getSuffixes()
  {
    assert debugEnter(CLASS_NAME, "getSuffixes");

    return directoryServer.suffixes;
  }



  /**
   * Retrieves the set of "private" suffixes that have been registered with the
   * server that will provide server-specific data to clients (e.g., monitor
   * data, schema, etc.) but should not be considered for normal operations
   * that may target all "user" suffixes.
   *
   * @return  The set of "private" suffixes that have been registered with the
   *          server.
   */
  public static LinkedHashMap<DN,Backend> getPrivateSuffixes()
  {
    assert debugEnter(CLASS_NAME, "getPrivateSuffixes");

    return directoryServer.privateSuffixes;
  }



  /**
   * Indicates whether the provided DN is one of the suffixes defined in the
   * Directory Server.
   *
   * @param  dn  The DN for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided DN is one of the suffixes
   *          defined in the Directory Server, or <CODE>false</CODE> if not.
   */
  public static boolean isSuffix(DN dn)
  {
    assert debugEnter(CLASS_NAME, "isSuffix", String.valueOf(dn));

    return (directoryServer.suffixes.containsKey(dn) ||
            directoryServer.privateSuffixes.containsKey(dn));
  }



  /**
   * Retrieves the backend that should be used to handle operations for the
   * provided entry DN.
   *
   * @param  dn  The DN of the entry for which to retrieve the appropriate
   *             backend.
   *
   * @return  The backend that should be used to handle the provided DN, or
   *          <CODE>null</CODE> if there is no backend for the provided DN.
   */
  public static Backend getBackend(DN dn)
  {
    assert debugEnter(CLASS_NAME, "getBackend", String.valueOf(dn));

    if (dn.isNullDN())
    {
      return directoryServer.rootDSEBackend;
    }

    Backend backend = directoryServer.suffixes.get(dn);
    if (backend == null)
    {
      backend = directoryServer.privateSuffixes.get(dn);
    }

    while (backend == null)
    {
      dn = dn.getParent();
      if (dn == null)
      {
        break;
      }

      backend = directoryServer.suffixes.get(dn);
      if (backend == null)
      {
        backend = directoryServer.privateSuffixes.get(dn);
      }
    }

    return backend;
  }



  /**
   * Registers the specified suffix to be handled by the provided backend.
   *
   * @param  suffixDN  The base DN for this suffix.
   * @param  backend   The backend to handle operations for the provided base.
   *
   * @throws  ConfigException  If the specified suffix is already registered
   *                           with the Directory Server.
   */
  public static void registerSuffix(DN suffixDN, Backend backend)
         throws ConfigException
  {
    assert debugEnter(CLASS_NAME, "registerSuffix", String.valueOf(suffixDN),
                      String.valueOf(backend));

    backend.setPrivateBackend(false);

    synchronized (directoryServer.suffixes)
    {
      // Check to see if this suffix is already in use.  It may be a suffix, or
      // it may be a sub-suffix of an existing suffix.
      Backend b = directoryServer.suffixes.get(suffixDN);
      if (b != null)
      {
        int    msgID   = MSGID_CANNOT_REGISTER_DUPLICATE_SUFFIX;
        String message = getMessage(msgID, String.valueOf(suffixDN),
                                    b.getClass().getName());

        throw new ConfigException(msgID, message);
      }

      boolean found = false;
      DN parentDN = suffixDN.getParent();
      while (parentDN != null)
      {
        b = directoryServer.suffixes.get(suffixDN);
        if (b != null)
        {
          if (b.hasSubSuffix(suffixDN))
          {
            int    msgID   = MSGID_CANNOT_REGISTER_DUPLICATE_SUBSUFFIX;
            String message = getMessage(msgID, String.valueOf(suffixDN),
                                        String.valueOf(parentDN));

            throw new ConfigException(msgID, message);
          }
          else
          {
            b.addSubordinateBackend(backend);
            found = true;
            break;
          }
        }

        parentDN = parentDN.getParent();
      }


      if (! found)
      {
        // If we've gotten here, then it is not in use.  Register it.
        directoryServer.suffixes.put(suffixDN, backend);
      }


      // See if there are any supported controls or features that we want to
      // advertise.
      Set<String> supportedControls = backend.getSupportedControls();
      if (supportedControls != null)
      {
        for (String controlOID : supportedControls)
        {
          registerSupportedControl(controlOID);
        }
      }

      Set<String> supportedFeatures = backend.getSupportedFeatures();
      if (supportedFeatures != null)
      {
        for (String featureOID : supportedFeatures)
        {
          registerSupportedFeature(featureOID);
        }
      }
    }
  }



  /**
   * Registers the specified private suffix to be handled by the provided
   * backend.
   *
   * @param  suffixDN  The base DN for this suffix.
   * @param  backend   The backend to handle operations for the provided base.
   *
   * @throws  ConfigException  If the specified suffix is already registered
   *                           with the Directory Server.
   */
  public static void registerPrivateSuffix(DN suffixDN, Backend backend)
         throws ConfigException
  {
    assert debugEnter(CLASS_NAME, "registerPrivateSuffix",
                      String.valueOf(suffixDN), String.valueOf(backend));

    backend.setPrivateBackend(true);

    synchronized (directoryServer.privateSuffixes)
    {
      // Check to see if this suffix is already in use for a "user" suffix.  It
      // may be a suffix, or it may be a sub-suffix of an  existing suffix.
      synchronized (directoryServer.suffixes)
      {
        Backend b = directoryServer.suffixes.get(suffixDN);
        if (b != null)
        {
          int    msgID   = MSGID_CANNOT_REGISTER_DUPLICATE_SUFFIX;
          String message = getMessage(msgID, String.valueOf(suffixDN),
                                      b.getClass().getName());

          throw new ConfigException(msgID, message);
        }

        DN parentDN = suffixDN.getParent();
        while (parentDN != null)
        {
          b = directoryServer.suffixes.get(suffixDN);
          if (b != null)
          {
            int msgID = MSGID_CANNOT_REGISTER_PRIVATE_SUFFIX_BELOW_USER_PARENT;
            String message = getMessage(msgID, String.valueOf(suffixDN),
                                        String.valueOf(parentDN));
            throw new ConfigException(msgID, message);
          }

          parentDN = suffixDN.getParent();
        }
      }


      // Check to see if this suffix is already registered as a private suffix
      // or sub-suffix.
      Backend b = directoryServer.privateSuffixes.get(suffixDN);
      if (b != null)
      {
        int    msgID   = MSGID_CANNOT_REGISTER_DUPLICATE_SUFFIX;
        String message = getMessage(msgID, String.valueOf(suffixDN),
                                    b.getClass().getName());

        throw new ConfigException(msgID, message);
      }

      DN parentDN = suffixDN.getParent();
      while (parentDN != null)
      {
        b = directoryServer.privateSuffixes.get(suffixDN);
        if (b != null)
        {
          if (b.hasSubSuffix(suffixDN))
          {
            int    msgID   = MSGID_CANNOT_REGISTER_DUPLICATE_SUBSUFFIX;
            String message = getMessage(msgID, String.valueOf(suffixDN),
                                        String.valueOf(parentDN));

            throw new ConfigException(msgID, message);
          }
          else
          {
            b.addSubordinateBackend(backend);
            return;
          }
        }

        parentDN = suffixDN.getParent();
      }


      // If we've gotten here, then it is not in use.  Register it.
      directoryServer.privateSuffixes.put(suffixDN, backend);
    }
  }



  /**
   * Deregisters the specified suffix with the Directory Server.  This should
   * work regardless of whether the specified DN is a normal suffix or a private
   * suffix.
   *
   * @param  suffixDN  The suffix DN to deregister with the server.
   *
   * @throws  ConfigException  If a problem occurs while attempting to
   *                           deregister the specified suffix.
   */
  public static void deregisterSuffix(DN suffixDN)
         throws ConfigException
  {
    assert debugEnter(CLASS_NAME, "deregisterSuffix", String.valueOf(suffixDN));


    // First, check to see if it is a "user" suffix or sub-suffix.
    synchronized (directoryServer.suffixes)
    {
      Backend b = directoryServer.suffixes.remove(suffixDN);
      if (b != null)
      {
        return;
      }

      DN parentDN = suffixDN.getParent();
      while (parentDN != null)
      {
        b = directoryServer.suffixes.get(parentDN);
        if (b != null)
        {
          if (b.hasSubSuffix(suffixDN))
          {
            b.removeSubSuffix(suffixDN, parentDN);
          }

          return;
        }
      }
    }


    // Check the set of private suffixes and sub-suffixes.
    synchronized (directoryServer.privateSuffixes)
    {
      Backend b = directoryServer.privateSuffixes.remove(suffixDN);
      if (b != null)
      {
        return;
      }

      DN parentDN = suffixDN.getParent();
      while (parentDN != null)
      {
        b = directoryServer.privateSuffixes.get(parentDN);
        if (b != null)
        {
          if (b.hasSubSuffix(suffixDN))
          {
            b.removeSubSuffix(suffixDN, parentDN);
          }

          return;
        }
      }
    }
  }



  /**
   * Retrieves the root DSE entry for the Directory Server.
   *
   * @return  The root DSE entry for the Directory Server.
   */
  public static Entry getRootDSE()
  {
    assert debugEnter(CLASS_NAME, "getRootDSE");

    return directoryServer.rootDSEBackend.getRootDSE();
  }



  /**
   * Retrieves the root DSE backend for the Directory Server.
   *
   * @return  The root DSE backend for the Directory Server.
   */
  public static RootDSEBackend getRootDSEBackend()
  {
    assert debugEnter(CLASS_NAME, "getRootDSEBackend");

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
    assert debugEnter(CLASS_NAME, "getSchemaDN");

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
    assert debugEnter(CLASS_NAME, "setSchemaDN", String.valueOf(schemaDN));

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
    assert debugEnter(CLASS_NAME, "getEntry", String.valueOf(entryDN));


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
   *                              retrieve the entry.
   */
  public static boolean entryExists(DN entryDN)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "getEntry", String.valueOf(entryDN));


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
    assert debugEnter(CLASS_NAME, "getSupportedControls");

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
    assert debugEnter(CLASS_NAME, "isSupportedControl",
                      String.valueOf(controlOID));

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
    assert debugEnter(CLASS_NAME, "registerSupportedControl",
                      String.valueOf(controlOID));

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
    assert debugEnter(CLASS_NAME, "deregisterSupportedControl",
                      String.valueOf(controlOID));

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
    assert debugEnter(CLASS_NAME, "getSupportedFeatures");

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
    assert debugEnter(CLASS_NAME, "isSupportedFeature",
                      String.valueOf(featureOID));

    return directoryServer.supportedFeatures.contains(featureOID);
  }



  /**
   * Registers the provided OID as a supported feature for the Directory Server.
   * This will have no effect if the specified feature OID is already present in
   * the list of supported features.
   *
   * @param  featureOID  The OID of the feature to register as a supported
   *                     control.
   */
  public static void registerSupportedFeature(String featureOID)
  {
    assert debugEnter(CLASS_NAME, "registerSupportedFeature",
                      String.valueOf(featureOID));

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
    assert debugEnter(CLASS_NAME, "deregisterSupportedFeature",
                      String.valueOf(featureOID));

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
    assert debugEnter(CLASS_NAME, "getSupportedExtensions");

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
    assert debugEnter(CLASS_NAME, "getExtendedOperationHandler",
                      String.valueOf(oid));

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
    assert debugEnter(CLASS_NAME, "registerSupportedExtension",
                      String.valueOf(oid), String.valueOf(handler));

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
    assert debugEnter(CLASS_NAME, "deregisterSupportedExtension",
                      String.valueOf(oid));

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
    assert debugEnter(CLASS_NAME, "getSupportedSASLMechanisms");

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
    assert debugEnter(CLASS_NAME, "getSASLMechanismHandler",
                      String.valueOf(name));

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
    assert debugEnter(CLASS_NAME, "registerSASLMechanismHandler",
                      String.valueOf(name), String.valueOf(handler));

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
    assert debugEnter(CLASS_NAME, "deregisterSASLMechanismHandler",
                      String.valueOf(name));

    // FIXME -- Should we force this name to be lowercase?
    directoryServer.saslMechanismHandlers.remove(name);
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
    assert debugEnter(CLASS_NAME, "getIdentityMappers");

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
    assert debugEnter(CLASS_NAME, "getIdentityMapper",
                      String.valueOf(configEntryDN));

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
    assert debugEnter(CLASS_NAME, "registerIdentityMapper",
                      String.valueOf(configEntryDN),
                      String.valueOf(identityMapper));

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
    assert debugEnter(CLASS_NAME, "deregisterIdentityMapper",
                      String.valueOf(configEntryDN));

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
    assert debugEnter(CLASS_NAME, "getProxiedAuthorizationIdentityMapperDN");

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
    assert debugEnter(CLASS_NAME, "setProxiedAuthorizationIdentityMapperDN",
                      String.valueOf(proxiedAuthorizationIdentityMapperDN));

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
    assert debugEnter(CLASS_NAME, "getProxiedAuthorizationIdentityMapper");

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
    assert debugEnter(CLASS_NAME, "getConnectionHandlers");

    return directoryServer.connectionHandlers;
  }



  /**
   * Registers the provided connection handler with the Directory Server.
   *
   * @param  handler  The connection handler to register with the Directory
   *                  Server.
   */
  public static void registerConnectionHandler(ConnectionHandler handler)
  {
    assert debugEnter(CLASS_NAME, "registerConnectionHandler",
                      String.valueOf(handler));

    synchronized (directoryServer.connectionHandlers)
    {
      directoryServer.connectionHandlers.add(handler);
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
    assert debugEnter(CLASS_NAME, "deregisterConnectionHandler",
                      String.valueOf(handler));

    synchronized (directoryServer.connectionHandlers)
    {
      directoryServer.connectionHandlers.remove(handler);
    }
  }



  /**
   * Initializes the Directory Server work queue from the information in the
   * configuration.
   *
   * @throws  ConfigException  If the Directory Server configuration does not
   *                           have a valid work queue specification.
   *
   * @throws  InitializationException  If a problem occurs while attempting to
   *                                   initialize the work queue.
   */
  private void initializeWorkQueue()
          throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializeWorkQueue");

    DN configEntryDN;
    try
    {
      configEntryDN = DN.decode(DN_WORK_QUEUE_CONFIG);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeWorkQueue", e);

      int    msgID   = MSGID_WORKQ_CANNOT_PARSE_DN;
      String message = getMessage(msgID, DN_WORK_QUEUE_CONFIG,
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    ConfigEntry configEntry = configHandler.getConfigEntry(configEntryDN);
    if (configEntry == null)
    {
      int    msgID   = MSGID_WORKQ_NO_CONFIG;
      String message = getMessage(msgID, DN_WORK_QUEUE_CONFIG);
      throw new ConfigException(msgID, message);
    }


    int msgID = MSGID_WORKQ_DESCRIPTION_CLASS;
    StringConfigAttribute classStub =
         new StringConfigAttribute(ATTR_WORKQ_CLASS, getMessage(msgID), true,
                                   false, true);
    StringConfigAttribute classAttr =
         (StringConfigAttribute) configEntry.getConfigAttribute(classStub);
    if (classAttr == null)
    {
      msgID = MSGID_WORKQ_NO_CLASS_ATTR;
      String message = getMessage(msgID, DN_WORK_QUEUE_CONFIG,
                                  ATTR_WORKQ_CLASS);

      throw new ConfigException(msgID, message);
    }
    else
    {
      Class workQueueClass ;
      try
      {
        workQueueClass = Class.forName(classAttr.activeValue());
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializeWorkQueue", e);

        msgID = MSGID_WORKQ_CANNOT_LOAD;
        String message = getMessage(msgID, classAttr.activeValue(),
                                    stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }

      try
      {
        workQueue = (WorkQueue) workQueueClass.newInstance();
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "initializeWorkQueue", e);

        msgID = MSGID_WORKQ_CANNOT_INSTANTIATE;
        String message = getMessage(msgID, classAttr.activeValue(),
                                    stackTraceToSingleLineString(e));
        throw new InitializationException(msgID, message, e);
      }

      workQueue.initializeWorkQueue(configEntry);
    }
  }



  /**
   * Retrieves a reference to the Directory Server work queue.
   *
   * @return  A reference to the Directory Server work queue.
   */
  public static WorkQueue getWorkQueue()
  {
    assert debugEnter(CLASS_NAME, "getWorkQueue");

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
  public static void enqueueRequest(Operation operation)
         throws DirectoryException
  {
    assert debugEnter(CLASS_NAME, "enqueueRequest", String.valueOf(operation));


    // See if a bind is already in progress on the associated connection.  If so
    // then reject the operation.
    ClientConnection clientConnection = operation.getClientConnection();
    if (clientConnection.bindInProgress() &&
        (operation.getOperationType() != OperationType.BIND))
    {
      int    msgID   = MSGID_ENQUEUE_BIND_IN_PROGRESS;
      String message = getMessage(msgID);
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                   msgID);
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
          int    msgID   = MSGID_ENQUEUE_MUST_CHANGE_PASSWORD;
          String message = getMessage(msgID);
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message,
                                       msgID);

        case EXTENDED:
          // This will only be allowed if it's a password modify request.
          ExtendedOperation extOp      = (ExtendedOperation) operation;
          String            requestOID = extOp.getRequestOID();
          if ((requestOID == null) ||
              (! requestOID.equals(OID_PASSWORD_MODIFY_REQUEST)))
          {
            msgID   = MSGID_ENQUEUE_MUST_CHANGE_PASSWORD;
            message = getMessage(msgID);
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         message, msgID);
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
    assert debugEnter(CLASS_NAME, "getChangeNotificationListeners");

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
    assert debugEnter(CLASS_NAME, "registerChangeNotificationListener",
                      String.valueOf(changeListener));

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
    assert debugEnter(CLASS_NAME, "deregisterChangeNotificationListener",
                      String.valueOf(changeListener));

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
    assert debugEnter(CLASS_NAME, "getPersistentSearches");

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
    assert debugEnter(CLASS_NAME, "registerPersistentSearch",
                      String.valueOf(persistentSearch));

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
    assert debugEnter(CLASS_NAME, "deregisterPersistentSearch",
                      String.valueOf(persistentSearch));

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
  public static CopyOnWriteArrayList<SynchronizationProvider>
              getSynchronizationProviders()
  {
    assert debugEnter(CLASS_NAME, "getSynchronizationProviders");

    return directoryServer.synchronizationProviders;
  }



  /**
   * Registers the provided synchronization provider with the Directory Server.
   *
   * @param  provider  The synchronization provider to register.
   */
  public static void registerSynchronizationProvider(SynchronizationProvider
                                                          provider)
  {
    assert debugEnter(CLASS_NAME, "registerSynchronizationProvider",
                      String.valueOf(provider));

    directoryServer.synchronizationProviders.add(provider);
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
    assert debugEnter(CLASS_NAME, "deregisterSynchronizationProvider",
                      String.valueOf(provider));

    directoryServer.synchronizationProviders.remove(provider);
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
    assert debugEnter(CLASS_NAME, "registerShutdownListener",
                      String.valueOf(listener));

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
    assert debugEnter(CLASS_NAME, "deregisterShutdownListener",
                      String.valueOf(listener));

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
  public static void shutDown(String className, String reason)
  {
    assert debugEnter(CLASS_NAME, "shutDown", String.valueOf(className),
                      String.valueOf(reason));

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
    int    msgID   = MSGID_SERVER_SHUTDOWN;
    String message = getMessage(msgID, className, reason);
    sendAlertNotification(directoryServer, ALERT_TYPE_SERVER_SHUTDOWN, msgID,
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
        int id = MSGID_CONNHANDLER_CLOSED_BY_SHUTDOWN;
        handler.finalizeConnectionHandler(getMessage(id), true);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "shutDown", e);
      }
    }
    directoryServer.connectionHandlers.clear();

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
    directoryServer.workQueue.finalizeWorkQueue(reason);


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
        assert debugException(CLASS_NAME, "shutDown", e);
      }
    }



    // Call the shutdown plugins, and then finalize all the plugins defined in
    // the server.
    if (directoryServer.pluginConfigManager != null)
    {
      directoryServer.pluginConfigManager.invokeShutdownPlugins();
      directoryServer.pluginConfigManager.finalizePlugins();
    }


    // Shut down all of the alert handlers.
    for (AlertHandler alertHandler : directoryServer.alertHandlers)
    {
      alertHandler.finalizeAlertHandler();
    }


    // Deregister all of the JMX MBeans.
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
          assert debugException(CLASS_NAME, "shutDown", e);
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
        assert debugException(CLASS_NAME, "shutDown", e);
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
        assert debugException(CLASS_NAME, "shutDown", e);
      }
    }


    // Shut down all the other components that may need special handling.
    // NYI


    // Shut down the monitor providers.
    for (MonitorProvider monitor : directoryServer.monitorProviders.values())
    {
      try
      {
        monitor.processServerShutdown(reason);
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "shutDown", e);
      }
    }


    // Shut down the backends.
    for (Backend backend : directoryServer.backends.values())
    {
      try
      {
        backend.finalizeBackend();

        // Remove the shared lock for this backend.
        try
        {
          String lockFile = LockFileManager.getBackendLockFileName(backend);
          StringBuilder failureReason = new StringBuilder();
          if (! LockFileManager.releaseLock(lockFile, failureReason))
          {
            msgID   = MSGID_SHUTDOWN_CANNOT_RELEASE_SHARED_BACKEND_LOCK;
            message = getMessage(msgID, backend.getBackendID(),
                                 String.valueOf(failureReason));
            logError(ErrorLogCategory.CONFIGURATION,
                     ErrorLogSeverity.SEVERE_WARNING, message, msgID);
            // FIXME -- Do we need to send an admin alert?
          }
        }
        catch (Exception e2)
        {
          assert debugException(CLASS_NAME, "applyConfigurationChange", e2);

          msgID   = MSGID_SHUTDOWN_CANNOT_RELEASE_SHARED_BACKEND_LOCK;
          message = getMessage(msgID, backend.getBackendID(),
                               stackTraceToSingleLineString(e2));
          logError(ErrorLogCategory.CONFIGURATION,
                   ErrorLogSeverity.SEVERE_WARNING, message, msgID);
          // FIXME -- Do we need to send an admin alert?
        }
      }
      catch (Exception e)
      {
        assert debugException(CLASS_NAME, "shutDown", e);
      }
    }


    // Shut down all the access loggers.
    try
    {
      removeAllAccessLoggers(true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "shutDown", e);
    }


    // Release the exclusive lock for the Directory Server process.
    String lockFile = LockFileManager.getServerLockFileName();
    try
    {
      StringBuilder failureReason = new StringBuilder();
      if (! LockFileManager.releaseLock(lockFile, failureReason))
      {
        msgID   = MSGID_CANNOT_RELEASE_EXCLUSIVE_SERVER_LOCK;
        message = getMessage(msgID, lockFile, String.valueOf(failureReason));
        logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.SEVERE_WARNING,
                 message, msgID);
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "startServer", e);

      msgID   = MSGID_CANNOT_RELEASE_EXCLUSIVE_SERVER_LOCK;
      message = getMessage(msgID, lockFile, stackTraceToSingleLineString(e));
      logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.SEVERE_WARNING,
               message, msgID);
    }


    // Log a final message indicating that the server is stopped (which should
    // be true for all practical purposes), and then shut down all the error
    // loggers.
    logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.NOTICE,
             MSGID_SERVER_STOPPED);

    try
    {
      removeAllErrorLoggers(true);
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "shutDown", e);
    }


    // Shutdown all debug loggers.
    try
    {
      removeAllDebugLoggers(true);
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }


    // Just in case there's something that isn't shut down properly, wait for
    // the monitor to give the OK to stop.
    shutdownMonitor.waitForMonitor();


    // At this point, the server is no longer running.
    directoryServer.isRunning = false;
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
  public static void restart(String className, String reason)
  {
    assert debugEnter(CLASS_NAME, "restart", String.valueOf(className),
                      String.valueOf(reason));

    try
    {
      String configClass = directoryServer.configClass;
      String configFile  = directoryServer.configFile;

      shutDown(className, reason);
      getNewInstance();
      directoryServer.bootstrapServer();
      directoryServer.initializeConfiguration(configClass, configFile);
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
   * Retrieves the maximum number of concurrent client connections that may be
   * established.
   *
   * @return  The maximum number of concurrent client connections that may be
   *          established, or -1 if there is no limit.
   */
  public static long getMaxAllowedConnections()
  {
    assert debugEnter(CLASS_NAME, "getMaxAllowedConnections");

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
    assert debugEnter(CLASS_NAME, "setMaxAllowedConnections",
                      String.valueOf(maxAllowedConnections));

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
    assert debugEnter(CLASS_NAME, "newConnectionAccepted",
                      String.valueOf(clientConnection));

    synchronized (directoryServer.establishedConnections)
    {
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
    assert debugEnter(CLASS_NAME, "connectionClosed",
                      String.valueOf(clientConnection));

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
    assert debugEnter(CLASS_NAME, "getCurrentConnections");

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
    assert debugEnter(CLASS_NAME, "getMaxConnections");

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
    assert debugEnter(CLASS_NAME, "getTotalConnections");

    return directoryServer.totalConnections;
  }



  /**
   * Retrieves the full version string for the Directory Server.
   *
   * @return  The full version string for the Directory Server.
   */
  public static String getVersionString()
  {
    StringBuilder buffer = new StringBuilder();
    buffer.append(PRODUCT_NAME);
    buffer.append(" ");
    buffer.append(MAJOR_VERSION);
    buffer.append(".");
    buffer.append(MINOR_VERSION);
    buffer.append(VERSION_QUALIFIER);
    return buffer.toString();
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
    assert debugEnter(CLASS_NAME, "getSizeLimit");

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
    assert debugEnter(CLASS_NAME, "setSizeLimit", String.valueOf(sizeLimit));

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
    assert debugEnter(CLASS_NAME, "getLookthroughLimit");

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
    assert debugEnter(CLASS_NAME, "setLookthroughLimit",
      String.valueOf(lookthroughLimit));

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
    assert debugEnter(CLASS_NAME, "getTimeLimit");

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
    assert debugEnter(CLASS_NAME, "setTimeLimit", String.valueOf(timeLimit));

    directoryServer.timeLimit = timeLimit;
  }



  /**
   * Retrieves the writability mode for the Directory Server.  This will only
   * be applicable for user suffixes.
   *
   * @return  The writability mode for the Directory Server.
   */
  public static WritabilityMode getWritabilityMode()
  {
    assert debugEnter(CLASS_NAME, "getWritabilityMode");

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
    assert debugEnter(CLASS_NAME, "setWritabilityMode",
                      String.valueOf(writabilityMode));

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
    assert debugEnter(CLASS_NAME, "bindWithDNRequiresPassword");

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
    assert debugEnter(CLASS_NAME, "setBindWithDNRequiresPassword",
                      String.valueOf(bindWithDNRequiresPassword));

    directoryServer.bindWithDNRequiresPassword = bindWithDNRequiresPassword;
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
    assert debugEnter(CLASS_NAME, "getComponentEntryDN");

    try
    {
      if (configHandler == null)
      {
        // The config handler hasn't been initialized yet.  Just return the DN
        // of the root DSE.
        return new DN(new ArrayList<RDN>(0));
      }

      return configHandler.getConfigRootEntry().getDN();
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "getComponentEntryDN", e);

      // This could theoretically happen if an alert needs to be sent before the
      // configuration is initialized.  In that case, just return an empty DN.
      return new DN(new ArrayList<RDN>(0));
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
    assert debugEnter(CLASS_NAME, "getClassName");

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
    assert debugEnter(CLASS_NAME, "uncaughtException",
                      String.valueOf(thread), String.valueOf(exception));

    assert debugException(CLASS_NAME, "uncaughtException", exception);

    int    msgID   = MSGID_UNCAUGHT_THREAD_EXCEPTION;
    String message = getMessage(msgID, thread.getName(),
                                stackTraceToString(exception));
    logError(ErrorLogCategory.CORE_SERVER, ErrorLogSeverity.SEVERE_ERROR,
             message, msgID);
    sendAlertNotification(this, ALERT_TYPE_UNCAUGHT_EXCEPTION, msgID, message);
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
    BooleanArgument displayUsage = null;
    BooleanArgument dumpMessages = null;
    BooleanArgument fullVersion  = null;
    BooleanArgument systemInfo   = null;
    BooleanArgument version      = null;
    StringArgument  configClass  = null;
    StringArgument  configFile   = null;


    // Create the command-line argument parser for use with this program.
    ArgumentParser argParser =
         new ArgumentParser("org.opends.server.core.DirectoryServer", false);


    // Initialize all the command-line argument types and register them with the
    // parser.
    try
    {
      configClass = new StringArgument("configclass", 'C', "configClass",
                                       true, false, true, "{configClass}",
                                       ConfigFileHandler.class.getName(), null,
                                       MSGID_DSCORE_DESCRIPTION_CONFIG_CLASS);
      argParser.addArgument(configClass);


      configFile = new StringArgument("configfile", 'f', "configFile",
                                      true, false, true, "{configFile}", null,
                                      null,
                                      MSGID_DSCORE_DESCRIPTION_CONFIG_FILE);
      argParser.addArgument(configFile);


      version = new BooleanArgument("version", 'v', "version",
                                    MSGID_DSCORE_DESCRIPTION_VERSION);
      argParser.addArgument(version);


      fullVersion = new BooleanArgument("fullversion", 'V', "fullVersion",
                                        MSGID_DSCORE_DESCRIPTION_FULLVERSION);
      fullVersion.setHidden(true);
      argParser.addArgument(fullVersion);


      systemInfo = new BooleanArgument("systeminfo", 's', "systemInfo",
                                       MSGID_DSCORE_DESCRIPTION_SYSINFO);
      systemInfo.setHidden(true);
      argParser.addArgument(systemInfo);


      dumpMessages = new BooleanArgument("dumpmessages", 'm', "dumpMessages",
                                         MSGID_DSCORE_DESCRIPTION_DUMPMESSAGES);
      dumpMessages.setHidden(true);
      argParser.addArgument(dumpMessages);


      displayUsage = new BooleanArgument("help", 'H', "help",
                                         MSGID_DSCORE_DESCRIPTION_USAGE);
      argParser.addArgument(displayUsage);
      argParser.setUsageArgument(displayUsage);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_DSCORE_CANNOT_INITIALIZE_ARGS;
      String message = getMessage(msgID, ae.getMessage());

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
      int    msgID   = MSGID_DSCORE_ERROR_PARSING_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      System.err.println(message);
      System.err.println(argParser.getUsage());
      System.exit(1);
    }


    // If we should just display usage information, then print it and exit.
    if (displayUsage.isPresent())
    {
      System.exit(0);
    }
    else if (fullVersion.isPresent())
    {
      System.out.println(getVersionString());
      System.out.println("Build ID:            " + BUILD_ID);
      System.out.println("Major Version:       " + MAJOR_VERSION);
      System.out.println("Minor Version:       " + MINOR_VERSION);
      System.out.println("Point Version:       " + POINT_VERSION);
      System.out.println("Version Qualifier:   " + VERSION_QUALIFIER);
      System.out.println("Fix IDs:             " + FIX_IDS);
      System.out.println("Debug Build:         " + DEBUG_BUILD);
      System.out.println("Build OS:            " + BUILD_OS);
      System.out.println("Build User:          " + BUILD_USER);
      System.out.println("Build Java Version:  " + BUILD_JAVA_VERSION);
      System.out.println("Build Java Vendor:   " + BUILD_JAVA_VENDOR);
      System.out.println("Build JVM Version:   " + BUILD_JVM_VERSION);
      System.out.println("Build JVM Vendor:    " + BUILD_JVM_VENDOR);

      return;
    }
    else if (version.isPresent())
    {
      System.out.println(getVersionString());
      System.out.println("Build " + BUILD_ID);

      if ((FIX_IDS != null) && (FIX_IDS.length() > 0))
      {
        System.out.println("Fix IDs:  " + FIX_IDS);
      }

      return;
    }
    else if (systemInfo.isPresent())
    {
      System.out.println(getVersionString());
      System.out.println("Build ID:               " + BUILD_ID);
      System.out.println("Java Version:           " +
                         System.getProperty("java.version"));
      System.out.println("Java Vendor:            " +
                         System.getProperty("java.vendor"));
      System.out.println("JVM Version:            " +
                         System.getProperty("java.vm.version"));
      System.out.println("JVM Vendor:             " +
                         System.getProperty("java.vm.vendor"));
      System.out.println("Class Path:             " +
                         System.getProperty("java.class.path"));
      System.out.println("Current Directory:      " +
                         System.getProperty("user.dir"));
      System.out.println("Operating System:       " +
                         System.getProperty("os.name") + " " +
                         System.getProperty("os.version") + " " +
                         System.getProperty("os.arch"));

      try
      {
        System.out.println("System Name:            " +
                           InetAddress.getLocalHost().getCanonicalHostName());
      }
      catch (Exception e)
      {
        System.out.println("System Name:             Unknown (" + e + ")");
      }

      Runtime runtime = Runtime.getRuntime();
      System.out.println("Available Processors:   " +
                         runtime.availableProcessors());
      System.out.println("Max Available Memory:   " + runtime.maxMemory());
      System.out.println("Currently Used Memory:  " + runtime.totalMemory());
      System.out.println("Currently Free Memory:  " + runtime.freeMemory());

      return;
    }
    else if (dumpMessages.isPresent())
    {
      DirectoryServer.bootstrapClient();

      ConcurrentHashMap<Integer,String> messageMap = getMessages();
      for (int msgID : messageMap.keySet())
      {
        System.out.println(msgID + "\t" + messageMap.get(msgID));
      }

      return;
    }


    // Bootstrap and start the Directory Server.
    DirectoryServer directoryServer = DirectoryServer.getInstance();
    try
    {
      directoryServer.bootstrapServer();
      directoryServer.initializeConfiguration(configClass.getValue(),
                                              configFile.getValue());
    }
    catch (InitializationException ie)
    {
      assert debugException(CLASS_NAME, "main", ie);

      int    msgID   = MSGID_DSCORE_CANNOT_BOOTSTRAP;
      String message = getMessage(msgID, ie.getMessage());
      System.err.println(message);
      System.exit(1);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_DSCORE_CANNOT_BOOTSTRAP;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      System.err.println(message);
      System.exit(1);
    }

    try
    {
      directoryServer.startServer();
    }
    catch (InitializationException ie)
    {
      assert debugException(CLASS_NAME, "main", ie);

      int    msgID   = MSGID_DSCORE_CANNOT_START;
      String message = getMessage(msgID, ie.getMessage());
      System.err.println(message);
      System.exit(1);
    }
    catch (Exception e)
    {
      int    msgID   = MSGID_DSCORE_CANNOT_START;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      System.err.println(message);
      System.exit(1);
    }
  }
}

