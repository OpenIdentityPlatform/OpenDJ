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
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.core;

import java.util.*;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.meta.GlobalCfgDefn;
import org.forgerock.opendj.server.config.meta.GlobalCfgDefn.DisabledPrivilege;
import org.forgerock.opendj.server.config.meta.GlobalCfgDefn.InvalidAttributeSyntaxBehavior;
import org.forgerock.opendj.server.config.meta.GlobalCfgDefn.SingleStructuralObjectclassBehavior;
import org.forgerock.opendj.server.config.server.GlobalCfg;
import org.opends.server.api.AuthenticationPolicy;
import org.opends.server.api.LocalBackend;
import org.opends.server.loggers.CommonAudit;
import org.opends.server.types.*;

import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;
import static org.opends.messages.BackendMessages.WARN_ROOTDSE_NO_BACKEND_FOR_SUBORDINATE_BASE;
import static org.opends.messages.BackendMessages.WARN_ROOTDSE_SUBORDINATE_BASE_EXCEPTION;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

/**
 * Manages the set of core configuration attributes of the Directory Server.
 * <p>
 * These configuration attributes appear in the "cn=config" configuration entry.
 */
public class CoreConfigManager implements ConfigurationChangeListener<GlobalCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The server context. */
  private final ServerContext serverContext;

  /** The core attributes. */
  private volatile CoreAttributes coreAttributes = new CoreAttributes();

  /**
   * Creates a new instance of this core config manager.
   *
   * @param serverContext
   *            The server context.
   */
  public CoreConfigManager(ServerContext serverContext)
  {
    this.serverContext = serverContext;
  }

  /** Holder for all core attributes, allowing to switch as a whole. */
  private static class CoreAttributes
  {
    /** Set of subordinates base DNs to restrict to when searching base DN "". */
    private Set<DN> subordinateBaseDNs = Collections.emptySet();
    /** Indicates whether the Directory Server should perform schema checking for update operations. */
    private boolean checkSchema;
    /** The DN of the default password policy configuration entry. */
    private DN defaultPasswordPolicyDN;
    /**
     * Indicates whether to automatically add missing RDN attributes to entries
     * during an add request.
     */
    private boolean addMissingRDNAttributes;
    /**
     * Indicates whether to allow attribute name exceptions (i.e., attribute names
     * can contain underscores and may start with a digit).
     */
    private boolean allowAttributeNameExceptions;
    /** The policy to use regarding syntax enforcement. */
    private AcceptRejectWarn syntaxEnforcementPolicy = AcceptRejectWarn.REJECT;
    /** The result code that should be used for internal "server" errors. */
    private ResultCode serverErrorResultCode = ResultCode.OTHER;
    /** The policy to use regarding single structural objectclass enforcement. */
    private AcceptRejectWarn singleStructuralClassPolicy = AcceptRejectWarn.REJECT;
    /** Indicates whether the server should send a response to operations that have been abandoned. */
    private boolean notifyAbandonedOperations;
    /**
     * The maximum number of entries that should be returned for a search unless
     * overridden on a per-user basis.
     */
    private int sizeLimit;
    /**
     * The maximum length of time in seconds that should be allowed for a search
     * unless overridden on a per-user basis.
     */
    private int timeLimit;
    /**
     * The DN of the identity mapper that will be used to resolve authorization
     * IDs contained in the proxied authorization V2 control.
     */
    private DN proxiedAuthorizationIdentityMapperDN;
    /** The writability mode for the Directory Server. */
    private WritabilityMode writabilityMode = WritabilityMode.ENABLED;
    /** Indicates whether the server should reject unauthenticated requests. */
    private boolean rejectUnauthenticatedRequests;
    /** Indicates whether a simple bind request containing a DN must also provide a password. */
    private boolean bindWithDNRequiresPassword;
    /** The maximum number of candidates that should be check for matches during a search. */
    private int lookthroughLimit;
    /** The sets of mail server properties. */
    private List<Properties> mailServerPropertySets = Collections.emptyList();

    /** The set of allowed task classes. */
    private Set<String> allowedTasks = Collections.emptySet();
    /** The set of disabled privileges. */
    private Set<Privilege> disabledPrivileges = Collections.emptySet();
    /** Indicates whether bind responses should include failure reason messages. */
    private boolean returnBindErrorMessages;
    /** The idle time limit for the server. */
    private long idleTimeLimit;
    /** Indicates whether to save a copy of the configuration on successful startup. */
    private boolean saveConfigOnSuccessfulStartup;
    /** Whether to use collect operation processing times in nanosecond resolution. */
    private boolean useNanoTime;
    /** The maximum number of connections that will be allowed at any given time. */
    private long maxAllowedConnections;
    /** The maximum number of concurrent persistent searches. */
    private int maxPSearches;
    /** The maximum size that internal buffers will be allowed to grow to until they are trimmed. */
    private int maxInternalBufferSize = DEFAULT_MAX_INTERNAL_BUFFER_SIZE;
  }

  /**
   * Initializes the Directory Server's core configuration. This should only be
   * called at server startup.
   *
   * @throws ConfigException
   *           If a configuration problem causes the identity mapper
   *           initialization process to fail.
   * @throws InitializationException
   *           If a problem occurs while initializing the identity mappers that
   *           is not related to the server configuration.
   */
  public void initializeCoreConfig()
         throws ConfigException, InitializationException
  {
    GlobalCfg globalConfig = serverContext.getRootConfig().getGlobalConfiguration();
    globalConfig.addChangeListener(this);


    // Validate any specified SMTP servers
    Set<String> smtpServers = globalConfig.getSMTPServer();
    if (smtpServers != null)
    {
      for (String server : smtpServers)
      {
        try
        {
          HostPort.valueOf(server, SMTP_DEFAULT_PORT);
        }
        catch (RuntimeException e)
        {
          LocalizableMessage message = ERR_CONFIG_CORE_INVALID_SMTP_SERVER.get(server);
          throw new ConfigException(message, e);
        }
      }
    }
    CoreAttributes coreAttrs = new CoreAttributes();
    applyGlobalConfiguration(globalConfig, coreAttrs);
    applySubordinateDNsChange(globalConfig, coreAttrs);
    coreAttributes = coreAttrs;
    DirectoryServer.resetDefaultPasswordPolicy();
  }

  /**
   * Applies the settings in the provided configuration to the Directory Server.
   *
   * @param  globalConfig  The configuration settings to be applied.
   */
  private void applyGlobalConfiguration(final GlobalCfg globalConfig, final CoreAttributes core)
      throws ConfigException
  {
    core.checkSchema = globalConfig.isCheckSchema();
    core.defaultPasswordPolicyDN = globalConfig.getDefaultPasswordPolicyDN();
    core.addMissingRDNAttributes = globalConfig.isAddMissingRDNAttributes();
    core.allowAttributeNameExceptions = globalConfig.isAllowAttributeNameExceptions();
    core.syntaxEnforcementPolicy = convert(globalConfig.getInvalidAttributeSyntaxBehavior());
    core.serverErrorResultCode = ResultCode.valueOf(globalConfig.getServerErrorResultCode());
    core.singleStructuralClassPolicy = convert(globalConfig.getSingleStructuralObjectclassBehavior());

    core.notifyAbandonedOperations = globalConfig.isNotifyAbandonedOperations();
    core.sizeLimit = globalConfig.getSizeLimit();
    core.timeLimit = (int) globalConfig.getTimeLimit();
    core.proxiedAuthorizationIdentityMapperDN = globalConfig.getProxiedAuthorizationIdentityMapperDN();
    core.writabilityMode = convert(globalConfig.getWritabilityMode());
    core.rejectUnauthenticatedRequests = globalConfig.isRejectUnauthenticatedRequests();
    core.bindWithDNRequiresPassword = globalConfig.isBindWithDNRequiresPassword();
    core.lookthroughLimit = globalConfig.getLookthroughLimit();

    core.mailServerPropertySets = getMailServerProperties(globalConfig.getSMTPServer());
    core.allowedTasks = globalConfig.getAllowedTask();
    core.disabledPrivileges = convert(globalConfig.getDisabledPrivilege());
    core.returnBindErrorMessages = globalConfig.isReturnBindErrorMessages();
    core.idleTimeLimit = globalConfig.getIdleTimeLimit();
    core.saveConfigOnSuccessfulStartup = globalConfig.isSaveConfigOnSuccessfulStartup();

    core.useNanoTime= globalConfig.getEtimeResolution() == GlobalCfgDefn.EtimeResolution.NANOSECONDS;
    long maxAllowedConnections = globalConfig.getMaxAllowedClientConnections();
    core.maxAllowedConnections = (maxAllowedConnections > 0) ? maxAllowedConnections : -1;
    core.maxPSearches = globalConfig.getMaxPsearches();
    core.maxInternalBufferSize = (int) globalConfig.getMaxInternalBufferSize();

    // For tools, common audit may not be available
    CommonAudit commonAudit = serverContext.getCommonAudit();
    if (commonAudit != null)
    {
      commonAudit.setTrustTransactionIds(globalConfig.isTrustTransactionIds());
    }

    // Update the schema with configuration changes if necessary
    try
    {
      final boolean allowMalformedNames = globalConfig.isAllowAttributeNameExceptions();
      serverContext.getSchemaHandler().updateSchemaOption(ALLOW_MALFORMED_NAMES_AND_OPTIONS, allowMalformedNames);
    }
    catch (DirectoryException e)
    {
      throw new ConfigException(e.getMessageObject(), e);
    }
  }

  private static AcceptRejectWarn convert(InvalidAttributeSyntaxBehavior invalidAttributeSyntaxBehavior)
  {
    switch (invalidAttributeSyntaxBehavior)
    {
    case ACCEPT:
      return AcceptRejectWarn.ACCEPT;
    case WARN:
      return AcceptRejectWarn.WARN;
    case REJECT:
    default:
      return AcceptRejectWarn.REJECT;
    }
  }

  private static AcceptRejectWarn convert(SingleStructuralObjectclassBehavior singleStructuralObjectclassBehavior)
  {
    switch (singleStructuralObjectclassBehavior)
    {
    case ACCEPT:
      return AcceptRejectWarn.ACCEPT;
    case WARN:
      return AcceptRejectWarn.WARN;
    case REJECT:
    default:
      return AcceptRejectWarn.REJECT;
    }
  }

  private static WritabilityMode convert(GlobalCfgDefn.WritabilityMode writabilityMode)
  {
    switch (writabilityMode)
    {
    case ENABLED:
      return WritabilityMode.ENABLED;
    case INTERNAL_ONLY:
      return WritabilityMode.INTERNAL_ONLY;
    case DISABLED:
    default:
      return WritabilityMode.DISABLED;
    }
  }

  private static List<Properties> getMailServerProperties(Set<String> smtpServers)
  {
    List<Properties> mailServerProperties = new ArrayList<>();
    if (smtpServers != null && !smtpServers.isEmpty())
    {
      for (String smtpServer : smtpServers)
      {
        final Properties properties = new Properties();
        try
        {
          final HostPort hp = HostPort.valueOf(smtpServer, SMTP_DEFAULT_PORT);

          properties.setProperty(SMTP_PROPERTY_HOST, hp.getHost());
          properties.setProperty(SMTP_PROPERTY_PORT,
              String.valueOf(hp.getPort()));
          properties.setProperty(SMTP_PROPERTY_CONNECTION_TIMEOUT,
              SMTP_DEFAULT_TIMEOUT_VALUE);
          properties.setProperty(SMTP_PROPERTY_IO_TIMEOUT,
              SMTP_DEFAULT_TIMEOUT_VALUE);
        }
        catch (RuntimeException e)
        {
          // no valid port provided
          properties.setProperty(SMTP_PROPERTY_HOST, smtpServer);
        }
        mailServerProperties.add(properties);
      }
    }
    return mailServerProperties;
  }

  private static HashSet<Privilege> convert(Set<DisabledPrivilege> configuredDisabledPrivs)
  {
    HashSet<Privilege> disabledPrivileges = new HashSet<>();
    if (configuredDisabledPrivs != null)
    {
      for (DisabledPrivilege p : configuredDisabledPrivs)
      {
        final Privilege privilege = convert(p);
        if (privilege != null)
        {
          disabledPrivileges.add(privilege);
        }
      }
    }
    return disabledPrivileges;
  }

  private static Privilege convert(DisabledPrivilege privilege)
  {
    switch (privilege)
    {
      case BACKEND_BACKUP:
        return Privilege.BACKEND_BACKUP;
      case BACKEND_RESTORE:
        return Privilege.BACKEND_RESTORE;
      case BYPASS_ACL:
        return Privilege.BYPASS_ACL;
      case CANCEL_REQUEST:
        return Privilege.CANCEL_REQUEST;
      case CONFIG_READ:
        return Privilege.CONFIG_READ;
      case CONFIG_WRITE:
        return Privilege.CONFIG_WRITE;
      case DATA_SYNC:
        return Privilege.DATA_SYNC;
      case DISCONNECT_CLIENT:
        return Privilege.DISCONNECT_CLIENT;
      case JMX_NOTIFY:
        return Privilege.JMX_NOTIFY;
      case JMX_READ:
        return Privilege.JMX_READ;
      case JMX_WRITE:
        return Privilege.JMX_WRITE;
      case LDIF_EXPORT:
        return Privilege.LDIF_EXPORT;
      case LDIF_IMPORT:
        return Privilege.LDIF_IMPORT;
      case MODIFY_ACL:
        return Privilege.MODIFY_ACL;
      case PASSWORD_RESET:
        return Privilege.PASSWORD_RESET;
      case PRIVILEGE_CHANGE:
        return Privilege.PRIVILEGE_CHANGE;
      case PROXIED_AUTH:
        return Privilege.PROXIED_AUTH;
      case SERVER_RESTART:
        return Privilege.SERVER_RESTART;
      case SERVER_SHUTDOWN:
        return Privilege.SERVER_SHUTDOWN;
      case UNINDEXED_SEARCH:
        return Privilege.UNINDEXED_SEARCH;
      case UPDATE_SCHEMA:
        return Privilege.UPDATE_SCHEMA;
      case SUBENTRY_WRITE:
        return Privilege.SUBENTRY_WRITE;
      default:
        return null;
    }
  }

  @Override
  public boolean isConfigurationChangeAcceptable(GlobalCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    boolean configAcceptable = true;
    Set<String> smtpServers = configuration.getSMTPServer();
    if (smtpServers != null)
    {
      for (String server : smtpServers)
      {
        try
        {
          // validate provided string
          HostPort.valueOf(server, SMTP_DEFAULT_PORT);
        }
        catch (RuntimeException e)
        {
          unacceptableReasons.add(ERR_CONFIG_CORE_INVALID_SMTP_SERVER.get(server));
          configAcceptable = false;
        }
      }
    }

    // Ensure that the default password policy always points to a password
    // policy and not another type of authentication policy.
    DN defaultPasswordPolicyDN = configuration.getDefaultPasswordPolicyDN();
    AuthenticationPolicy policy = DirectoryServer
        .getAuthenticationPolicy(defaultPasswordPolicyDN);
    if (!policy.isPasswordPolicy())
    {
      LocalizableMessage message =
        ERR_CONFIG_PWPOLICY_CANNOT_CHANGE_DEFAULT_POLICY_WRONG_TYPE
          .get(configuration.getDefaultPasswordPolicy());
      unacceptableReasons.add(message);
      configAcceptable = false;
    }

    if (!isSubordinateDNsAcceptable(configuration, unacceptableReasons))
    {
      configAcceptable = false;
    }

    return configAcceptable;
  }

  private boolean isSubordinateDNsAcceptable(GlobalCfg configuration, List<LocalizableMessage> unacceptableReasons)
  {
    try
    {
      Set<DN> subDNs = configuration.getSubordinateBaseDN();
      if (subDNs.isEmpty())
      {
        // This is fine -- we'll just use the set of user-defined suffixes.
        return true;
      }
      boolean hasError = false;
      for (DN baseDN : subDNs)
      {
        LocalBackend<?> backend = serverContext.getBackendConfigManager().findLocalBackendForEntry(baseDN);
        if (backend == null)
        {
          unacceptableReasons.add(WARN_ROOTDSE_NO_BACKEND_FOR_SUBORDINATE_BASE.get(baseDN));
          hasError = true;
        }
      }
      return !hasError;
    }
    catch (Exception e)
    {
      logger.traceException(e);
      unacceptableReasons.add(WARN_ROOTDSE_SUBORDINATE_BASE_EXCEPTION.get(stackTraceToSingleLineString(e)));
      return false;
    }
  }

  private void applySubordinateDNsChange(GlobalCfg cfg, CoreAttributes coreAttrs)
      throws ConfigException
  {
    try
    {
      Set<DN> subDNs = cfg.getSubordinateBaseDN();
      if (subDNs.equals(coreAttrs.subordinateBaseDNs))
      {
        // no change
        return;
      }
      if (subDNs.isEmpty())
      {
        // This is fine -- we'll just use the set of user-defined suffixes.
        coreAttrs.subordinateBaseDNs = Collections.emptySet();
      }
      else
      {
        Set<DN> newSubordinates = new HashSet<>();
        for (DN baseDN : subDNs)
        {
          LocalBackend<?> backend = serverContext.getBackendConfigManager().findLocalBackendForEntry(baseDN);
          if (backend == null)
          {
            throw new ConfigException(WARN_ROOTDSE_NO_BACKEND_FOR_SUBORDINATE_BASE.get(baseDN));
          }
          else
          {
            newSubordinates.add(baseDN);
          }
        }
        coreAttrs.subordinateBaseDNs = newSubordinates;
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
      throw new ConfigException(WARN_ROOTDSE_SUBORDINATE_BASE_EXCEPTION.get(stackTraceToSingleLineString(e)));
    }
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(GlobalCfg configuration)
  {
    final ConfigChangeResult changeResult = new ConfigChangeResult();
    final CoreAttributes coreAttrs = new CoreAttributes();
    try
    {
      applyGlobalConfiguration(configuration, coreAttrs);
      applySubordinateDNsChange(configuration, coreAttrs);
    }
    catch (ConfigException e)
    {
      changeResult.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
      changeResult.addMessage(e.getMessageObject());
    }

    if (changeResult.getResultCode() == ResultCode.SUCCESS)
    {
      coreAttributes = coreAttrs;
      DirectoryServer.resetDefaultPasswordPolicy();
    }
    return changeResult;
  }

  /**
   * Retrieves a set containing the names of the allowed tasks that may be
   * invoked in the server.
   *
   * @return  A set containing the names of the allowed tasks that may be
   *          invoked in the server.
   */
  public Set<String> getAllowedTasks()
  {
    return coreAttributes.allowedTasks;
  }

  /**
   * Retrieves the DN of the configuration entry for the default password policy
   * for the Directory Server.
   *
   * @return  The DN of the configuration entry for the default password policy
   *          for the Directory Server.
   */
  public DN getDefaultPasswordPolicyDN()
  {
    return coreAttributes.defaultPasswordPolicyDN;
  }

  /**
   * Retrieves the set of privileges that have been disabled.
   *
   * @return  The set of privileges that have been disabled.
   */
  public Set<Privilege> getDisabledPrivileges()
  {
    return coreAttributes.disabledPrivileges;
  }

  /**
   * Retrieves the idle time limit for the server.
   *
   * @return the idle time limit
   */
  public long getIdleTimeLimit()
  {
    return coreAttributes.idleTimeLimit;
  }

  /**
   * Retrieves the default maximum number of entries that should checked for
   * matches during a search.
   *
   * @return  The default maximum number of entries that should checked for
   *          matches during a search.
   */
  public int getLookthroughLimit()
  {
    return coreAttributes.lookthroughLimit;
  }

  /**
   * Retrieves the sets of information about the mail servers configured for use
   * by the Directory Server.
   *
   * @return  The sets of information about the mail servers configured for use
   *          by the Directory Server.
   */
  public List<Properties> getMailServerPropertySets()
  {
    return coreAttributes.mailServerPropertySets;
  }

  /**
   * Retrieves the maximum number of connections that will be allowed at any given time.
   *
   * @return the max number of connections allowed
   */
  public long getMaxAllowedConnections()
  {
    return coreAttributes.maxAllowedConnections;
  }

  /**
   * Returns the threshold capacity beyond which internal cached buffers used
   * for encoding and decoding entries and protocol messages will be trimmed
   * after use.
   *
   * @return The threshold capacity beyond which internal cached buffers used
   *         for encoding and decoding entries and protocol messages will be
   *         trimmed after use.
   */
  public int getMaxInternalBufferSize()
  {
    return coreAttributes.maxInternalBufferSize;
  }

  /**
   * Retrieves the maximum number of concurrent persistent searches that will be allowed.
   *
   * @return the max number of persistent searches
   */
  public int getMaxPSearches()
  {
    return coreAttributes.maxPSearches;
  }

  /**
   * Retrieves the DN of the configuration entry for the identity mapper that
   * should be used in conjunction with proxied authorization V2 controls.
   *
   * @return  The DN of the configuration entry for the identity mapper that
   *          should be used in conjunction with proxied authorization V2
   *          controls, or {@code null} if none is defined.
   */
  public DN getProxiedAuthorizationIdentityMapperDN()
  {
    return coreAttributes.proxiedAuthorizationIdentityMapperDN;
  }

  /**
   * Retrieves the result code that should be used when the Directory Server
   * encounters an internal server error.
   *
   * @return  The result code that should be used when the Directory Server
   *          encounters an internal server error.
   */
  public ResultCode getServerErrorResultCode()
  {
    return coreAttributes.serverErrorResultCode;
  }

  /**
   * Retrieves the policy that should be used regarding enforcement of a single
   * structural objectclass per entry.
   *
   * @return  The policy that should be used regarding enforcement of a single
   *          structural objectclass per entry.
   */
  public AcceptRejectWarn getSingleStructuralObjectClassPolicy()
  {
    return coreAttributes.singleStructuralClassPolicy;
  }

  /**
   * Retrieves the default maximum number of entries that should be returned for
   * a search.
   *
   * @return  The default maximum number of entries that should be returned for
   *          a search.
   */
  public int getSizeLimit()
  {
    return coreAttributes.sizeLimit;
  }

  /**
   * Retrieves the restricted set of subordinate base DNs to use when searching the root suffix "".
   *
   * @return the subordinate base DNs, which is empty if there is no restriction
   */
  public Set<DN> getSubordinateBaseDNs()
  {
    return Collections.unmodifiableSet(coreAttributes.subordinateBaseDNs);
  }

  /**
   * Retrieves the policy that should be used when an attribute value is found
   * that is not valid according to the associated attribute syntax.
   *
   * @return  The policy that should be used when an attribute value is found
   *          that is not valid according to the associated attribute syntax.
   */
  public AcceptRejectWarn getSyntaxEnforcementPolicy()
  {
    return coreAttributes.syntaxEnforcementPolicy;
  }

  /**
   * Retrieves the default maximum length of time in seconds that should be
   * allowed when processing a search.
   *
   * @return  The default maximum length of time in seconds that should be
   *          allowed when processing a search.
   */
  public int getTimeLimit()
  {
    return coreAttributes.timeLimit;
  }

  /**
   * Retrieves the writability mode for the Directory Server.  This will only
   * be applicable for user suffixes.
   *
   * @return  The writability mode for the Directory Server.
   */
  public WritabilityMode getWritabilityMode()
  {
    return coreAttributes.writabilityMode;
  }

  /**
   * Indicates whether the Directory Server should automatically add missing RDN
   * attributes to an entry whenever it is added.
   *
   * @return {@code true} if the Directory Server should automatically add
   *          missing RDN attributes to an entry, or {@code false} if it
   *          should return an error to the client.
   */
  public boolean isAddMissingRDNAttributes()
  {
    return coreAttributes.addMissingRDNAttributes;
  }

  /**
   * Indicates whether to be more flexible in the set of characters allowed for
   * attribute names.  The standard requires that only ASCII alphabetic letters,
   * numeric digits, and hyphens be allowed, and that the name start with a
   * letter.  If attribute name exceptions are enabled, then underscores will
   * also be allowed, and the name will be allowed to start with a digit.
   *
   * @return  {@code true} if the server should use a more flexible
   *          syntax for attribute names, or {@code false} if not.
   * @deprecated The schema option SchemaOptions.ALLOW_MALFORMED_NAMES_AND_OPTIONS from the
   *  schema should be used instead
   */
  @Deprecated
  public boolean isAllowAttributeNameExceptions()
  {
    return coreAttributes.allowAttributeNameExceptions;
  }

  /**
   * Indicates whether simple bind requests that contain a bind DN will also be
   * required to have a password.
   *
   * @return  <CODE>true</CODE> if simple bind requests containing a bind DN
   *          will be required to have a password, or <CODE>false</CODE> if not
   *          (and therefore will be treated as anonymous binds).
   */
  public boolean isBindWithDNRequiresPassword()
  {
    return coreAttributes.bindWithDNRequiresPassword;
  }

  /**
   * Indicates whether the Directory Server should perform schema checking.
   *
   * @return  {@code true} if the Directory Server should perform schema
   *          checking, or {@code false} if not.
   */
  public boolean isCheckSchema()
  {
    return coreAttributes.checkSchema;
  }

  /**
   * Indicates whether the specified privilege is disabled.
   *
   * @param  privilege  The privilege for which to make the determination.
   *
   * @return  {@code true} if the specified privilege is disabled, or
   *          {@code false} if not.
   */
  public boolean isDisabled(Privilege privilege)
  {
    return coreAttributes.disabledPrivileges.contains(privilege);
  }

  /**
   * Indicates whether the Directory Server is configured with information about
   * one or more mail servers and may therefore be used to send e-mail messages.
   *
   * @return  {@code true} if the Directory Server is configured to be able to
   *          send e-mail messages, or {@code false} if not.
   */
  public boolean isMailServerConfigured()
  {
    return coreAttributes.mailServerPropertySets != null && !coreAttributes.mailServerPropertySets.isEmpty();
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
  public boolean isNotifyAbandonedOperations()
  {
    return coreAttributes.notifyAbandonedOperations;
  }

  /**
   * Indicates whether unauthenticated requests should be rejected.
   *
   * @return {@code true} if unauthenticated request should be rejected,
   *          {@code false} otherwise.
   */
  public boolean isRejectUnauthenticatedRequests()
  {
    return coreAttributes.rejectUnauthenticatedRequests;
  }

  /**
   * Indicates whether responses to failed bind operations should include a
   * message explaining the reason for the failure.
   *
   * @return  {@code true} if bind responses should include error messages, or
   *          {@code false} if not.
   */
  public boolean isReturnBindErrorMessages()
  {
    return coreAttributes.returnBindErrorMessages;
  }

  /**
   * Retrieves whether operation processing times should be collected with
   * nanosecond resolution.
   *
   * @return  <code>true</code> if nanosecond resolution times are collected
   *          or <code>false</code> if only millisecond resolution times are
   *          being collected.
   */
  public boolean isUseNanoTime()
  {
    return coreAttributes.useNanoTime;
  }

  /**
   * Indicates whether configuration should be saved on successful startup of the server.
   *
   * @return {@code true} if configuration should be saved,
   *          {@code false} otherwise.
   */
  public boolean isSaveConfigOnSuccessfulStartup()
  {
    return coreAttributes.saveConfigOnSuccessfulStartup;
  }

}
