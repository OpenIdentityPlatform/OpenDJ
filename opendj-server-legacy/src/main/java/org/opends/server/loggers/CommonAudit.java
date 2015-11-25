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
 *      Copyright 2015 ForgeRock AS.
 */
package org.opends.server.loggers;

import static java.util.Arrays.asList;

import static org.opends.messages.LoggerMessages.*;
import static java.util.Collections.newSetFromMap;
import static org.forgerock.audit.AuditServiceBuilder.newAuditService;
import static org.forgerock.audit.events.EventTopicsMetaDataBuilder.coreTopicSchemas;
import static org.forgerock.audit.json.AuditJsonConfig.registerHandlerToService;
import static org.opends.server.util.StaticUtils.getFileForPath;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.forgerock.audit.AuditException;
import org.forgerock.audit.AuditService;
import org.forgerock.audit.AuditServiceBuilder;
import org.forgerock.audit.AuditServiceConfiguration;
import org.forgerock.audit.AuditServiceProxy;
import org.forgerock.audit.DependencyProvider;
import org.forgerock.audit.events.EventTopicsMetaData;
import org.forgerock.audit.events.handlers.FileBasedEventHandlerConfiguration.FileRetention;
import org.forgerock.audit.events.handlers.FileBasedEventHandlerConfiguration.FileRotation;
import org.forgerock.audit.filter.FilterPolicy;
import org.forgerock.audit.handlers.csv.CsvAuditEventHandler;
import org.forgerock.audit.handlers.csv.CsvAuditEventHandlerConfiguration;
import org.forgerock.audit.handlers.csv.CsvAuditEventHandlerConfiguration.CsvFormatting;
import org.forgerock.audit.handlers.csv.CsvAuditEventHandlerConfiguration.CsvSecurity;
import org.forgerock.audit.handlers.csv.CsvAuditEventHandlerConfiguration.EventBufferingConfiguration;
import org.forgerock.audit.json.AuditJsonConfig;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.opendj.config.ConfigurationFramework;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.admin.std.server.CsvFileAccessLogPublisherCfg;
import org.opends.server.admin.std.server.CsvFileHTTPAccessLogPublisherCfg;
import org.opends.server.admin.std.server.ExternalAccessLogPublisherCfg;
import org.opends.server.admin.std.server.ExternalHTTPAccessLogPublisherCfg;
import org.opends.server.admin.std.server.FileCountLogRetentionPolicyCfg;
import org.opends.server.admin.std.server.FixedTimeLogRotationPolicyCfg;
import org.opends.server.admin.std.server.FreeDiskSpaceLogRetentionPolicyCfg;
import org.opends.server.admin.std.server.LogPublisherCfg;
import org.opends.server.admin.std.server.LogRetentionPolicyCfg;
import org.opends.server.admin.std.server.LogRotationPolicyCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.admin.std.server.SizeLimitLogRetentionPolicyCfg;
import org.opends.server.admin.std.server.SizeLimitLogRotationPolicyCfg;
import org.opends.server.admin.std.server.TimeLimitLogRotationPolicyCfg;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.util.StaticUtils;

/**
 * Entry point for the common audit facility.
 * <p>
 * This class manages the AuditService instances and Audit Event Handlers that correspond to the
 * publishers defined in OpenDJ configuration.
 * <p>
 * In theory there should be only one instance of AuditService for all the event handlers but
 * defining one service per handler allow to perform filtering at the DJ server level.
 */
public class CommonAudit
{
  /** Transaction id used when the incoming request does not contain a transaction id. */
  public static final String DEFAULT_TRANSACTION_ID = "0";

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  private static final String AUDIT_SERVICE_JSON_CONFIGURATION_FILE = "audit-config.json";

  /** Dependency provider used to instantiate the handlers. */
  private final DependencyProvider dependencyProvider;

  /** Configuration framework is used to get an up-to-date class loader with any external library available. */
  private final ConfigurationFramework configurationFramework;

  /** Cache of audit services per configuration entry name. */
  private final Map<String, AuditServiceProxy> auditServiceCache = new ConcurrentHashMap<>(10);

  private final Set<PublisherConfig> httpAccessPublishers =
      newSetFromMap(new ConcurrentHashMap<PublisherConfig, Boolean>(10));

  /** Audit service shared by all HTTP access publishers. */
  private final AuditServiceProxy httpAccessAuditService;

  private final AtomicBoolean trustTransactionIds = new AtomicBoolean(false);

  /**
   * Creates the common audit.
   *
   * @throws ConfigException
   *           If an error occurs.
   */
  public CommonAudit() throws ConfigException
  {
    configurationFramework = ConfigurationFramework.getInstance();
    this.dependencyProvider = new CommonAuditDependencyProvider();
    this.httpAccessAuditService = createAuditServiceWithoutHandlers();
  }

  /**
   * Indicates if transactionIds received from requests should be trusted.
   *
   * @return {@code true} if transactionIds should be trusted, {@code false} otherwise
   */
  public boolean shouldTrustTransactionIds()
  {
    return trustTransactionIds.get();
  }

  /**
   * Sets the indicator for transactionIds trusting.
   *
   * @param shouldTrust
   *          {@code true} if transactionIds should be trusted, {@code false}
   *          otherwise
   */
  public void setTrustTransactionIds(boolean shouldTrust)
  {
    trustTransactionIds.set(shouldTrust);
  }

  private AuditServiceProxy createAuditServiceWithoutHandlers() throws ConfigException
  {
    try
    {
      return buildAuditService(new AuditServiceSetup()
      {
        @Override
        public void addHandlers(AuditServiceBuilder builder)
        {
          // no handler to add
        }
      });
    }
    catch (IOException | ConfigException | AuditException e)
    {
      throw new ConfigException(ERR_COMMON_AUDIT_CREATE.get(e), e);
    }
  }

  /**
   * Returns the Common Audit request handler for the provided configuration.
   *
   * @param config
   *            The log publisher configuration
   * @return the request handler associated to the log publisher
   * @throws ConfigException
   *            If an error occurs
   */
  public RequestHandler getRequestHandler(LogPublisherCfg config) throws ConfigException
  {
    if (new PublisherConfig(config).isHttpAccessLog())
    {
      return httpAccessAuditService;
    }
    return auditServiceCache.get(getNameFromConfig(config));
  }

  /**
   * Adds or updates the publisher corresponding to the provided configuration to common audit.
   *
   * @param newConfig
   *          Configuration of the publisher
   * @throws ConfigException
   *           If an error occurs.
   */
  public void addOrUpdatePublisher(final LogPublisherCfg newConfig) throws ConfigException
  {
    if (newConfig.isEnabled())
    {
      logger.trace("Setting up common audit for configuration entry: " + newConfig.dn());
      try
      {
        final PublisherConfig newPublisher = new PublisherConfig(newConfig);
        if (newPublisher.isHttpAccessLog())
        {
          if (httpAccessPublishers.contains(newPublisher))
          {
            // remove the old version before adding the new one
            httpAccessPublishers.remove(newPublisher);
          }
          httpAccessPublishers.add(newPublisher);
          buildAuditService(httpAccessAuditServiceSetup());
        }
        else // all other logs
        {
          String name = newPublisher.getName();
          final AuditServiceProxy existingService =
              auditServiceCache.containsKey(name) ? auditServiceCache.get(name): null;
          AuditServiceProxy auditService = buildAuditService(new AuditServiceSetup(existingService)
          {
            @Override
            public void addHandlers(AuditServiceBuilder builder) throws ConfigException
            {
              registerHandlerName(newPublisher.getName());
              addHandlerToBuilder(newPublisher, builder);
            }
          });
          auditServiceCache.put(name, auditService);
        }
      }
      catch (Exception e)
      {
        throw new ConfigException(ERR_COMMON_AUDIT_ADD_OR_UPDATE_LOG_PUBLISHER.get(newConfig.dn(), e), e);
      }
    }
  }

  /**
   * Removes the publisher corresponding to the provided configuration from common audit.
   *
   * @param config
   *          Configuration of publisher to remove
   * @throws ConfigException
   *            If an error occurs.
   */
  public void removePublisher(LogPublisherCfg config) throws ConfigException
  {
    final PublisherConfig publisher = new PublisherConfig(config);
    logger.trace("Shutting down common audit for configuration entry:" + config.dn());
    try
    {
      if (publisher.isHttpAccessLog())
      {
        httpAccessPublishers.remove(publisher);
        buildAuditService(httpAccessAuditServiceSetup());
      }
      else // all other logs
      {
        AuditServiceProxy auditService = auditServiceCache.remove(publisher.getName());
        if (auditService != null)
        {
          auditService.shutdown();
        }
      }
    }
    catch (Exception e)
    {
      throw new ConfigException(ERR_COMMON_AUDIT_REMOVE_LOG_PUBLISHER.get(config.dn(), e), e);
    }
  }

  /** Shutdown common audit. */
  public void shutdown()
  {
    httpAccessAuditService.shutdown();
    for (AuditServiceProxy service : auditServiceCache.values())
    {
      service.shutdown();
    }
  }

  private AuditServiceSetup httpAccessAuditServiceSetup()
  {
    return new AuditServiceSetup(httpAccessAuditService)
    {
      @Override
      public void addHandlers(AuditServiceBuilder builder) throws ConfigException
      {
        for (PublisherConfig publisher : httpAccessPublishers)
        {
          registerHandlerName(publisher.getName());
          addHandlerToBuilder(publisher, builder);
        }
      }
    };
  }

  /**
   * Strategy for the setup of AuditService.
   * <p>
   * Unless no handler must be added, this class should be extended and
   * implementations should override the {@code addHandlers()} method.
   */
  static abstract class AuditServiceSetup
  {
    private final AuditServiceProxy existingAuditServiceProxy;
    private final List<String> names = new ArrayList<>();

    /** Creation with no existing audit service. */
    AuditServiceSetup()
    {
      this.existingAuditServiceProxy = null;
    }

    /** Creation with an existing audit service. */
    AuditServiceSetup(AuditServiceProxy existingAuditService)
    {
      this.existingAuditServiceProxy = existingAuditService;
    }

    abstract void addHandlers(AuditServiceBuilder builder) throws ConfigException;

    void registerHandlerName(String name)
    {
      names.add(name);
    }

    List<String> getHandlerNames()
    {
      return names;
    }

    boolean mustCreateAuditServiceProxy()
    {
      return existingAuditServiceProxy == null;
    }

    AuditServiceProxy getExistingAuditServiceProxy()
    {
      return existingAuditServiceProxy;
    }

  }

  private AuditServiceProxy buildAuditService(AuditServiceSetup setup)
      throws IOException, AuditException, ConfigException
  {
    final JsonValue jsonConfig;
    try (InputStream input = getClass().getResourceAsStream(AUDIT_SERVICE_JSON_CONFIGURATION_FILE))
    {
      jsonConfig = AuditJsonConfig.getJson(input);
    }

    EventTopicsMetaData eventTopicsMetaData = coreTopicSchemas()
        .withCoreTopicSchemaExtensions(jsonConfig.get("extensions"))
        .withAdditionalTopicSchemas(jsonConfig.get("additionalTopics"))
        .build();
    AuditServiceBuilder builder = newAuditService()
        .withEventTopicsMetaData(eventTopicsMetaData)
        .withDependencyProvider(dependencyProvider);

    setup.addHandlers(builder);

    AuditServiceConfiguration auditConfig = new AuditServiceConfiguration();
    auditConfig.setAvailableAuditEventHandlers(setup.getHandlerNames());
    auditConfig.setFilterPolicies(getFilterPoliciesToPreventHttpHeadersLogging());
    builder.withConfiguration(auditConfig);
    AuditService audit = builder.build();

    final AuditServiceProxy proxy;
    if (setup.mustCreateAuditServiceProxy())
    {
      proxy = new AuditServiceProxy(audit);
      logger.trace("Starting up new common audit service");
      proxy.startup();
    }
    else
    {
      proxy = setup.getExistingAuditServiceProxy();
      proxy.setDelegate(audit);
      logger.trace("Starting up existing updated common audit service");
    }
    return proxy;
  }

  /**
   * Build filter policies at the AuditService level to prevent logging of the headers for HTTP requests.
   * <p>
   * HTTP Headers may contains authentication information.
   */
  private Map<String, FilterPolicy> getFilterPoliciesToPreventHttpHeadersLogging()
  {
    Map<String, FilterPolicy> filterPolicies = new HashMap<>();
    FilterPolicy policy = new FilterPolicy();
    policy.setExcludeIf(asList("/http-access/http/request/headers"));
    filterPolicies.put("field", policy);
    return filterPolicies;
  }

  private void addHandlerToBuilder(PublisherConfig publisher, AuditServiceBuilder builder) throws ConfigException
  {
    if (publisher.isCsv())
    {
      addCsvHandler(publisher, builder);
    }
    else if (publisher.isExternal())
    {
      addExternalHandler(publisher, builder);
    }
    else
    {
      throw new ConfigException(ERR_COMMON_AUDIT_UNSUPPORTED_HANDLER_TYPE.get(publisher.getDn()));
    }
  }

  /** Add a handler defined externally in a JSON configuration file. */
  private void addExternalHandler(PublisherConfig publisher, AuditServiceBuilder builder) throws ConfigException
  {
    ExternalConfigData config = publisher.getExternalConfig();
    File configFile = getFileForPath(config.getConfigurationFile());
    try (InputStream input = new BufferedInputStream(new FileInputStream(configFile)))
    {
      JsonValue jsonConfig = AuditJsonConfig.getJson(input);
      registerHandlerToService(jsonConfig, builder, configurationFramework.getClassLoader());
    }
    catch (IOException e)
    {
      throw new ConfigException(ERR_COMMON_AUDIT_EXTERNAL_HANDLER_JSON_FILE.get(configFile, publisher.getDn(), e), e);
    }
    catch (Exception e)
    {
      throw new ConfigException(ERR_COMMON_AUDIT_EXTERNAL_HANDLER_CREATION.get(publisher.getDn(), e), e);
    }
  }

  private void addCsvHandler(PublisherConfig publisher, AuditServiceBuilder builder) throws ConfigException
  {
    String name = publisher.getName();
    try
    {
      CsvConfigData config = publisher.getCsvConfig();
      CsvAuditEventHandlerConfiguration csvConfig = new CsvAuditEventHandlerConfiguration();
      File logDirectory = getFileForPath(config.getLogDirectory());
      csvConfig.setLogDirectory(logDirectory.getAbsolutePath());
      csvConfig.setName(name);
      csvConfig.setTopics(Collections.singleton(publisher.getCommonAuditTopic()));

      addCsvHandlerFormattingConfig(config, csvConfig);
      addCsvHandlerBufferingConfig(config, csvConfig);
      addCsvHandlerSecureConfig(config, csvConfig);
      addCsvHandlerRotationConfig(publisher, config, csvConfig);
      addCsvHandlerRetentionConfig(publisher, config, csvConfig);

      builder.withAuditEventHandler(CsvAuditEventHandler.class, csvConfig);
    }
    catch (Exception e)
    {
      throw new ConfigException(ERR_COMMON_AUDIT_CSV_HANDLER_CREATION.get(publisher.getDn(), e), e);
    }
  }

  private void addCsvHandlerFormattingConfig(CsvConfigData config, CsvAuditEventHandlerConfiguration auditConfig)
      throws ConfigException
  {
    CsvFormatting formatting = new CsvFormatting();
    formatting.setQuoteChar(config.getQuoteChar());
    formatting.setDelimiterChar(config.getDelimiterChar());
    String endOfLineSymbols = config.getEndOfLineSymbols();
    if (endOfLineSymbols != null && !endOfLineSymbols.isEmpty())
    {
      formatting.setEndOfLineSymbols(endOfLineSymbols);
    }
    auditConfig.setFormatting(formatting);
  }

  private void addCsvHandlerBufferingConfig(CsvConfigData config, CsvAuditEventHandlerConfiguration auditConfig)
  {
    EventBufferingConfiguration bufferingConfig = new EventBufferingConfiguration();
    bufferingConfig.setEnabled(config.isAsynchronous());
    bufferingConfig.setAutoFlush(config.isAutoFlush());
    auditConfig.setBufferingConfiguration(bufferingConfig);
  }

  private void addCsvHandlerSecureConfig(CsvConfigData config, CsvAuditEventHandlerConfiguration auditConfig)
  {
    if (config.isTamperEvident())
    {
      CsvSecurity security = new CsvSecurity();
      security.setSignatureInterval(config.getSignatureTimeInterval() + "ms");
      security.setEnabled(true);
      String keyStoreFile = config.getKeystoreFile();
      security.setFilename(getFileForPath(keyStoreFile).getPath());
      security.setPassword(getSecurePassword(config));
      auditConfig.setSecurity(security);
    }
  }

  private void addCsvHandlerRotationConfig(PublisherConfig publisher, CsvConfigData config,
      CsvAuditEventHandlerConfiguration auditConfig) throws ConfigException
  {
    RootCfg root = ServerManagementContext.getInstance().getRootConfiguration();
    SortedSet<String> rotationPolicies = config.getRotationPolicies();
    if (rotationPolicies.isEmpty())
    {
      return;
    }

    FileRotation fileRotation = new FileRotation();
    fileRotation.setRotationEnabled(true);
    for (final String policy : rotationPolicies)
    {
      LogRotationPolicyCfg policyConfig = root.getLogRotationPolicy(policy);
      if (policyConfig instanceof FixedTimeLogRotationPolicyCfg)
      {
        List<String> times = convertTimesOfDay(publisher, (FixedTimeLogRotationPolicyCfg) policyConfig);
        fileRotation.setRotationTimes(times);
      }
      else if (policyConfig instanceof SizeLimitLogRotationPolicyCfg)
      {
        fileRotation.setMaxFileSize(((SizeLimitLogRotationPolicyCfg) policyConfig).getFileSizeLimit());
      }
      else if (policyConfig instanceof TimeLimitLogRotationPolicyCfg)
      {
        long rotationInterval = ((TimeLimitLogRotationPolicyCfg) policyConfig).getRotationInterval();
        fileRotation.setRotationInterval(String.valueOf(rotationInterval) + " ms");
      }
      else
      {
        throw new ConfigException(
            ERR_COMMON_AUDIT_UNSUPPORTED_LOG_ROTATION_POLICY.get(publisher.getDn(), policyConfig.dn()));
      }
    }
    auditConfig.setFileRotation(fileRotation);
  }

  private void addCsvHandlerRetentionConfig(PublisherConfig publisher, CsvConfigData config,
      CsvAuditEventHandlerConfiguration auditConfig) throws ConfigException
  {
    RootCfg root = ServerManagementContext.getInstance().getRootConfiguration();
    SortedSet<String> retentionPolicies = config.getRetentionPolicies();
    if (retentionPolicies.isEmpty())
    {
      return;
    }

    FileRetention fileRetention = new FileRetention();
    for (final String policy : retentionPolicies)
    {
      LogRetentionPolicyCfg policyConfig = root.getLogRetentionPolicy(policy);
      if (policyConfig instanceof FileCountLogRetentionPolicyCfg)
      {
        fileRetention.setMaxNumberOfHistoryFiles(((FileCountLogRetentionPolicyCfg) policyConfig).getNumberOfFiles());
      }
      else if (policyConfig instanceof FreeDiskSpaceLogRetentionPolicyCfg)
      {
        // TODO: remove the cast to int when fixed
        fileRetention.setMinFreeSpaceRequired(
            (int)((FreeDiskSpaceLogRetentionPolicyCfg) policyConfig).getFreeDiskSpace());
      }
      else if (policyConfig instanceof SizeLimitLogRetentionPolicyCfg)
      {
        fileRetention.setMaxDiskSpaceToUse(((SizeLimitLogRetentionPolicyCfg) policyConfig).getDiskSpaceUsed());
      }
      else
      {
        throw new ConfigException(
            ERR_COMMON_AUDIT_UNSUPPORTED_LOG_RETENTION_POLICY.get(publisher.getDn(), policyConfig.dn()));
      }
    }
    auditConfig.setFileRetention(fileRetention);
  }

  /**
   * Convert the set of provided times of day using 24-hour format "HHmm" to a list of
   * times of day using duration in minutes, e.g "20 minutes".
   * <p>
   * Example: "0230" => "150 minutes"
   */
  private List<String> convertTimesOfDay(PublisherConfig publisher, FixedTimeLogRotationPolicyCfg policyConfig)
      throws ConfigException
  {
    SortedSet<String> timesOfDay = policyConfig.getTimeOfDay();
    List<String> times = new ArrayList<>();
    for (String timeOfDay : timesOfDay)
    {
      try
      {
        int time = Integer.valueOf(timeOfDay.substring(0, 2)) * 60 + Integer.valueOf(timeOfDay.substring(2, 4));
        times.add(String.valueOf(time) + " minutes");
      }
      catch (NumberFormatException | IndexOutOfBoundsException e)
      {
        throw new ConfigException(ERR_COMMON_AUDIT_INVALID_TIME_OF_DAY.get(publisher.getDn(), timeOfDay,
            StaticUtils.stackTraceToSingleLineString(e)));
      }
    }
    return times;
  }

  // TODO : this method will be deleted, because a keystore handler will be used instead
  private String getSecurePassword(CsvConfigData config)
  {
    String fileName = config.getKeystorePinFile();
    File   pinFile  = getFileForPath(fileName);

    if (!pinFile.exists())
    {
      // should log error "no pin file"
    }
    else
    {
      String pinStr = null;
      BufferedReader br = null;
      try {
        br = new BufferedReader(new FileReader(pinFile));
        pinStr = br.readLine();
      }
      catch (IOException ioe)
      {
        // should log error "unable to read pin file"
      }
      finally
      {
        StaticUtils.close(br);
      }

      if (pinStr == null)
      {
        // should log error pin file empty
      }
      else
      {
        return pinStr;
      }
    }
    return "";
  }

  private String getNameFromConfig(Configuration config)
  {
    return config.dn().getRDN(0).getAttributeValue(0).toString();
  }

  /**
   * Indicates if the provided log publisher configuration corresponds to a common audit publisher.
   *
   * @param config
   *            The log publisher configuration.
   * @return {@code true} if publisher is for common audit
   * @throws ConfigException
   *            If an error occurs
   */
  public static boolean isCommonAuditConfig(LogPublisherCfg config) throws ConfigException
  {
    return new PublisherConfig(config).isCommonAudit();
  }

  /**
   * Indicates if HTTP access logging is enabled for common audit.
   *
   * @return {@code true} if there is at least one HTTP access logger enabled for common audit.
   */
  public boolean isHttpAccessLogEnabled()
  {
    return !httpAccessPublishers.isEmpty();
  }

  /**
   * Returns the audit service that manages HTTP Access logging.
   *
   * @return the request handler that accepts audit events
   */
  public RequestHandler getAuditServiceForHttpAccessLog()
  {
    return httpAccessAuditService;
  }

  /**
   * This class hides all ugly code needed to determine which type of publisher and audit event handler is needed.
   * <p>
   * In particular, it allows to retrieve a common configuration that can be used for log publishers that
   * publish to the same kind of handler.
   * For example: for CSV handler, DJ configurations for the log publishers contain the same methods but
   * do not have a common interface (CsvFileAccessLogPublisherCfg vs CsvFileHTTPAccessLogPublisherCfg).
   */
  private static class PublisherConfig
  {
    private final LogPublisherCfg config;
    private final boolean isCommonAudit;
    private LogType logType;
    private AuditType auditType;

    PublisherConfig(LogPublisherCfg config) throws ConfigException
    {
      this.config = config;
      ConfigEntry configEntry = DirectoryServer.getConfigEntry(config.dn());
      if (configEntry.hasObjectClass("ds-cfg-csv-file-access-log-publisher"))
      {
        auditType = AuditType.CSV;
        logType = LogType.ACCESS;
      }
      else if (configEntry.hasObjectClass("ds-cfg-csv-file-http-access-log-publisher"))
      {
        auditType = AuditType.CSV;
        logType = LogType.HTTP_ACCESS;
      }
      else if (configEntry.hasObjectClass("ds-cfg-external-access-log-publisher"))
      {
        auditType = AuditType.EXTERNAL;
        logType = LogType.ACCESS;
      }
      else if (configEntry.hasObjectClass("ds-cfg-external-http-access-log-publisher"))
      {
        auditType = AuditType.EXTERNAL;
        logType = LogType.HTTP_ACCESS;
      }
      isCommonAudit = auditType != null;
    }

    DN getDn()
    {
      return config.dn();
    }

    String getName()
    {
      return config.dn().getRDN(0).getAttributeValue(0).toString();
    }

    String getCommonAuditTopic() throws ConfigException
    {
      if (isAccessLog())
      {
        return "ldap-access";
      }
      else if (isHttpAccessLog())
      {
        return "http-access";
      }
      throw new ConfigException(ERR_COMMON_AUDIT_UNSUPPORTED_LOG_PUBLISHER.get(config.dn()));
    }

    boolean isExternal()
    {
      return AuditType.EXTERNAL == auditType;
    }

    boolean isCsv()
    {
      return AuditType.CSV == auditType;
    }

    boolean isAccessLog()
    {
      return LogType.ACCESS == logType;
    }

    boolean isHttpAccessLog()
    {
      return LogType.HTTP_ACCESS == logType;
    }

    boolean isCommonAudit()
    {
      return isCommonAudit;
    }

    CsvConfigData getCsvConfig() throws ConfigException
    {
      if (isAccessLog())
      {
        CsvFileAccessLogPublisherCfg conf = (CsvFileAccessLogPublisherCfg) config;
        return new CsvConfigData(conf.getLogDirectory(), conf.getCsvQuoteChar(), conf.getCsvDelimiterChar(), conf
            .getCsvEolSymbols(), conf.isAsynchronous(), conf.isAutoFlush(), conf.isTamperEvident(), conf
            .getSignatureTimeInterval(), conf.getKeyStoreFile(), conf.getKeyStorePinFile(), conf.getRotationPolicy(),
            conf.getRetentionPolicy());
      }
      if (isHttpAccessLog())
      {
        CsvFileHTTPAccessLogPublisherCfg conf = (CsvFileHTTPAccessLogPublisherCfg) config;
        return new CsvConfigData(conf.getLogDirectory(), conf.getCsvQuoteChar(), conf.getCsvDelimiterChar(), conf
            .getCsvEolSymbols(), conf.isAsynchronous(), conf.isAutoFlush(), conf.isTamperEvident(), conf
            .getSignatureTimeInterval(), conf.getKeyStoreFile(), conf.getKeyStorePinFile(), conf.getRotationPolicy(),
            conf.getRetentionPolicy());
      }
      throw new ConfigException(ERR_COMMON_AUDIT_UNSUPPORTED_LOG_PUBLISHER.get(config.dn()));
    }

    ExternalConfigData getExternalConfig() throws ConfigException
    {
      if (isAccessLog())
      {
        ExternalAccessLogPublisherCfg conf = (ExternalAccessLogPublisherCfg) config;
        return new ExternalConfigData(conf.getConfigFile());
      }
      if (isHttpAccessLog())
      {
        ExternalHTTPAccessLogPublisherCfg conf = (ExternalHTTPAccessLogPublisherCfg) config;
        return new ExternalConfigData(conf.getConfigFile());
      }
      throw new ConfigException(ERR_COMMON_AUDIT_UNSUPPORTED_LOG_PUBLISHER.get(config.dn()));
    }

    @Override
    public boolean equals(Object obj)
    {
      if (this == obj)
      {
        return true;
      }
      if (!(obj instanceof PublisherConfig))
      {
        return false;
      }
      PublisherConfig other = (PublisherConfig) obj;
      return config.dn().equals(other.config.dn());
    }

    @Override
    public int hashCode()
    {
      return config.dn().hashCode();
    }

  }

  /** Types of audit handlers managed. */
  private enum AuditType
  {
    CSV, EXTERNAL
  }

  /** Types of log managed. */
  private enum LogType
  {
    ACCESS, HTTP_ACCESS
  }

  /**
   * Contains the parameters for a CSV handler.
   * <p>
   * OpenDJ log publishers that logs to a CSV handler have the same parameters but do not share
   * a common ancestor with all the parameters (e.g Access Log, HTTP Access Log, ...), hence this class
   * is necessary to avoid duplicating code that setup the configuration of the CSV handler.
   */
  private static class CsvConfigData
  {
    private final String logDirectory;
    private final String eolSymbols;
    private final String delimiterChar;
    private final String quoteChar;
    private final boolean asynchronous;
    private final boolean autoFlush;
    private final boolean tamperEvident;
    private final long signatureTimeInterval;
    private final String keystoreFile;
    private final String keystorePinFile;
    private final SortedSet<String> rotationPolicies;
    private final SortedSet<String> retentionPolicies;

    CsvConfigData(String logDirectory, String quoteChar, String delimiterChar, String eolSymbols, boolean asynchronous,
        boolean autoFlush, boolean tamperEvident, long signatureTimeInterval, String keystoreFile,
        String keystorePinFile, SortedSet<String> rotationPolicies, SortedSet<String> retentionPolicies)
    {
      this.logDirectory = logDirectory;
      this.quoteChar = quoteChar;
      this.delimiterChar = delimiterChar;
      this.eolSymbols = eolSymbols;
      this.asynchronous = asynchronous;
      this.autoFlush = autoFlush;
      this.tamperEvident = tamperEvident;
      this.signatureTimeInterval = signatureTimeInterval;
      this.keystoreFile = keystoreFile;
      this.keystorePinFile = keystorePinFile;
      this.rotationPolicies = rotationPolicies;
      this.retentionPolicies = retentionPolicies;
    }

    String getEndOfLineSymbols()
    {
      return eolSymbols;
    }

    char getDelimiterChar() throws ConfigException
    {
      String filtered = delimiterChar.replaceAll(Pattern.quote("\\"), "");
      if (filtered.length() != 1)
      {
        throw new ConfigException(ERR_COMMON_AUDIT_CSV_HANDLER_DELIMITER_CHAR.get("", filtered));
      }
      return filtered.charAt(0);
    }

    public char getQuoteChar() throws ConfigException
    {
      String filtered = quoteChar.replaceAll(Pattern.quote("\\"), "");
      if (filtered.length() != 1)
      {
        throw new ConfigException(ERR_COMMON_AUDIT_CSV_HANDLER_QUOTE_CHAR.get("", filtered));
      }
      return filtered.charAt(0);
    }

    String getLogDirectory()
    {
      return logDirectory;
    }

    boolean isAsynchronous()
    {
      return asynchronous;
    }

    boolean isAutoFlush()
    {
      return autoFlush;
    }

    boolean isTamperEvident()
    {
      return tamperEvident;
    }

    long getSignatureTimeInterval()
    {
      return signatureTimeInterval;
    }

    String getKeystoreFile()
    {
      return keystoreFile;
    }

    String getKeystorePinFile()
    {
      return keystorePinFile;
    }

    SortedSet<String> getRotationPolicies()
    {
      return rotationPolicies;
    }

    SortedSet<String> getRetentionPolicies()
    {
      return retentionPolicies;
    }
  }

  /**
   * Contains the parameters for an external handler.
   * <p>
   * OpenDJ log publishers that logs to an external handler have the same parameters but do not share
   * a common ancestor with all the parameters (e.g Access Log, HTTP Access Log, ...), hence this class
   * is necessary to avoid duplicating code that setup the configuration of an external handler.
   */
  private static class ExternalConfigData
  {
    private final String configurationFile;

    ExternalConfigData(String configurationFile)
    {
      this.configurationFile = configurationFile;
    }

    String getConfigurationFile()
    {
      return configurationFile;
    }
  }

}
