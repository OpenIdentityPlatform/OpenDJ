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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2011 ForgeRock AS
 */
package org.opends.server.loggers;



import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.getFileForPath;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.server.admin.server.ConfigurationAddListener;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.server.ConfigurationDeleteListener;
import org.opends.server.admin.std.meta.AccessLogFilteringCriteriaCfgDefn.*;
import org.opends.server.admin.std.meta.FileBasedAccessLogPublisherCfgDefn.*;
import org.opends.server.admin.std.server.AccessLogFilteringCriteriaCfg;
import org.opends.server.admin.std.server.AccessLogPublisherCfg;
import org.opends.server.admin.std.server.FileBasedAccessLogPublisherCfg;
import org.opends.server.api.AccessLogPublisher;
import org.opends.server.api.ClientConnection;
import org.opends.server.api.ExtendedOperationHandler;
import org.opends.server.config.ConfigException;
import org.opends.server.core.*;
import org.opends.server.types.*;
import org.opends.server.util.TimeThread;



/**
 * This class provides the implementation of the access logger used by the
 * directory server.
 */
public class TextAccessLogPublisher extends
    AccessLogPublisher<FileBasedAccessLogPublisherCfg> implements
    ConfigurationChangeListener<FileBasedAccessLogPublisherCfg>
{

  /**
   * Criteria based filter.
   */
  static final class CriteriaFilter implements Filter
  {
    private final AccessLogFilteringCriteriaCfg cfg;
    private final boolean logConnectRecords;
    private final boolean logDisconnectRecords;
    private final EnumSet<OperationType> logOperationRecords;



    /**
     * Creates a new criteria based filter.
     *
     * @param cfg
     *          The access log filter criteria.
     */
    CriteriaFilter(final AccessLogFilteringCriteriaCfg cfg)
    {
      this.cfg = cfg;

      // Pre-parse the log record types for more efficient queries.
      if (cfg.getLogRecordType().isEmpty())
      {
        logConnectRecords = true;
        logDisconnectRecords = true;

        logOperationRecords = EnumSet.allOf(OperationType.class);
      }
      else
      {
        logConnectRecords =
          cfg.getLogRecordType().contains(LogRecordType.CONNECT);
        logDisconnectRecords =
          cfg.getLogRecordType().contains(LogRecordType.DISCONNECT);

        logOperationRecords = EnumSet.noneOf(OperationType.class);
        for (final LogRecordType type : cfg.getLogRecordType())
        {
          switch (type)
          {
          case ABANDON:
            logOperationRecords.add(OperationType.ABANDON);
            break;
          case ADD:
            logOperationRecords.add(OperationType.ADD);
            break;
          case BIND:
            logOperationRecords.add(OperationType.BIND);
            break;
          case COMPARE:
            logOperationRecords.add(OperationType.COMPARE);
            break;
          case DELETE:
            logOperationRecords.add(OperationType.DELETE);
            break;
          case EXTENDED:
            logOperationRecords.add(OperationType.EXTENDED);
            break;
          case MODIFY:
            logOperationRecords.add(OperationType.MODIFY);
            break;
          case RENAME:
            logOperationRecords.add(OperationType.MODIFY_DN);
            break;
          case SEARCH:
            logOperationRecords.add(OperationType.SEARCH);
            break;
          case UNBIND:
            logOperationRecords.add(OperationType.UNBIND);
            break;
          default: // Ignore CONNECT/DISCONNECT
            break;
          }
        }
      }
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConnectLoggable(final ClientConnection connection)
    {
      if (!logConnectRecords)
      {
        return false;
      }

      // TODO: other checks.

      return true;
    }



    /**
     * {@inheritDoc}
     */
    public boolean isDisconnectLoggable(final ClientConnection connection)
    {
      if (!logDisconnectRecords)
      {
        return false;
      }

      // TODO: other checks.

      return true;
    }



    /**
     * {@inheritDoc}
     */
    public boolean isRequestLoggable(final Operation operation)
    {
      if (!logOperationRecords.contains(operation.getOperationType()))
      {
        return false;
      }

      // TODO: other checks.

      return true;
    }



    /**
     * {@inheritDoc}
     */
    public boolean isResponseLoggable(final Operation operation)
    {
      if (!logOperationRecords.contains(operation.getOperationType()))
      {
        return false;
      }

      // TODO: other checks.

      return true;
    }

  }



  /**
   * Log message filter predicate.
   */
  static interface Filter
  {
    /**
     * Returns {@code true} if the provided client connect should be logged.
     *
     * @param connection
     *          The client connection.
     * @return {@code true} if the provided client connect should be logged.
     */
    boolean isConnectLoggable(ClientConnection connection);



    /**
     * Returns {@code true} if the provided client disconnect should be logged.
     *
     * @param connection
     *          The client connection.
     * @return {@code true} if the provided client disconnect should be logged.
     */
    boolean isDisconnectLoggable(ClientConnection connection);



    /**
     * Returns {@code true} if the provided request should be logged.
     *
     * @param operation
     *          The request.
     * @return {@code true} if the provided request should be logged.
     */
    boolean isRequestLoggable(Operation operation);



    /**
     * Returns {@code true} if the provided response should be logged.
     *
     * @param operation
     *          The response.
     * @return {@code true} if the provided response should be logged.
     */
    boolean isResponseLoggable(Operation operation);
  }



  /**
   * A filter which performs a logical OR over a set of sub-filters.
   */
  static final class OrFilter implements Filter
  {
    private final Filter[] subFilters;



    /**
     * Creates a new OR filter.
     *
     * @param subFilters
     *          The sub-filters.
     */
    OrFilter(final Filter[] subFilters)
    {
      this.subFilters = subFilters;
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConnectLoggable(final ClientConnection connection)
    {
      for (final Filter filter : subFilters)
      {
        if (filter.isConnectLoggable(connection))
        {
          // Succeed fast.
          return true;
        }
      }
      return false;
    }



    /**
     * {@inheritDoc}
     */
    public boolean isDisconnectLoggable(final ClientConnection connection)
    {
      for (final Filter filter : subFilters)
      {
        if (filter.isDisconnectLoggable(connection))
        {
          // Succeed fast.
          return true;
        }
      }
      return false;
    }



    /**
     * {@inheritDoc}
     */
    public boolean isRequestLoggable(final Operation operation)
    {
      for (final Filter filter : subFilters)
      {
        if (filter.isRequestLoggable(operation))
        {
          // Succeed fast.
          return true;
        }
      }
      return false;
    }



    /**
     * {@inheritDoc}
     */
    public boolean isResponseLoggable(final Operation operation)
    {
      for (final Filter filter : subFilters)
      {
        if (filter.isResponseLoggable(operation))
        {
          // Succeed fast.
          return true;
        }
      }
      return false;
    }

  }



  /**
   * The root filter which first checks the logger configuration, delegating to
   * a sub-filter if needed.
   */
  static final class RootFilter implements Filter
  {
    private final Filter subFilter;
    private final boolean suppressInternalOperations;
    private final boolean suppressSynchronizationOperations;
    private final FilteringPolicy policy;



    /**
     * Creates a new root filter.
     *
     * @param suppressInternal
     *          Indicates whether internal operations should be suppressed.
     * @param suppressSynchronization
     *          Indicates whether sync operations should be suppressed.
     * @param policy
     *          The filtering policy.
     * @param subFilter
     *          The sub-filters.
     */
    RootFilter(final boolean suppressInternal,
        final boolean suppressSynchronization, final FilteringPolicy policy,
        final Filter subFilter)
    {
      this.suppressInternalOperations = suppressInternal;
      this.suppressSynchronizationOperations = suppressSynchronization;
      this.policy = policy;
      this.subFilter = subFilter;
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConnectLoggable(final ClientConnection connection)
    {
      final long connectionID = connection.getConnectionID();
      if (connectionID >= 0 || !suppressInternalOperations)
      {
        switch (policy)
        {
        case INCLUSIVE:
          return subFilter.isConnectLoggable(connection);
        case EXCLUSIVE:
          return !subFilter.isConnectLoggable(connection);
        default: // NO_FILTERING:
          return true;
        }
      }
      else
      {
        return false;
      }
    }



    /**
     * {@inheritDoc}
     */
    public boolean isDisconnectLoggable(final ClientConnection connection)
    {
      final long connectionID = connection.getConnectionID();
      if (connectionID >= 0 || !suppressInternalOperations)
      {
        switch (policy)
        {
        case INCLUSIVE:
          return subFilter.isDisconnectLoggable(connection);
        case EXCLUSIVE:
          return !subFilter.isDisconnectLoggable(connection);
        default: // NO_FILTERING:
          return true;
        }
      }
      else
      {
        return false;
      }
    }



    /**
     * {@inheritDoc}
     */
    public boolean isRequestLoggable(final Operation operation)
    {
      if (isLoggable(operation))
      {
        switch (policy)
        {
        case INCLUSIVE:
          return subFilter.isRequestLoggable(operation);
        case EXCLUSIVE:
          return !subFilter.isRequestLoggable(operation);
        default: // NO_FILTERING:
          return true;
        }
      }
      else
      {
        return false;
      }
    }



    /**
     * {@inheritDoc}
     */
    public boolean isResponseLoggable(final Operation operation)
    {
      if (isLoggable(operation))
      {
        switch (policy)
        {
        case INCLUSIVE:
          return subFilter.isResponseLoggable(operation);
        case EXCLUSIVE:
          return !subFilter.isResponseLoggable(operation);
        default: // NO_FILTERING:
          return true;
        }
      }
      else
      {
        return false;
      }
    }



    // Determines whether the provided operation should be logged.
    private boolean isLoggable(final Operation operation)
    {
      final long connectionID = operation.getConnectionID();
      if (connectionID < 0)
      {
        // This is an internal operation.
        if (operation.isSynchronizationOperation())
        {
          return !suppressSynchronizationOperations;
        }
        else
        {
          return !suppressInternalOperations;
        }
      }

      return true;
    }
  }



  /**
   * Filter criteria configuration listener.
   */
  private final class FilterChangeListener implements
      ConfigurationChangeListener<AccessLogFilteringCriteriaCfg>,
      ConfigurationAddListener<AccessLogFilteringCriteriaCfg>,
      ConfigurationDeleteListener<AccessLogFilteringCriteriaCfg>
  {

    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationAdd(
        final AccessLogFilteringCriteriaCfg configuration)
    {
      // Rebuild the filter using the new configuration and criteria.
      buildFilters();
      configuration.addChangeListener(this);
      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    }



    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationChange(
        final AccessLogFilteringCriteriaCfg configuration)
    {
      // Rebuild the filter using the new configuration and criteria.
      buildFilters();
      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    }



    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationDelete(
        final AccessLogFilteringCriteriaCfg configuration)
    {
      // Rebuild the filter using the new configuration and criteria.
      buildFilters();
      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationAddAcceptable(
        final AccessLogFilteringCriteriaCfg configuration,
        final List<Message> unacceptableReasons)
    {
      return true;
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationChangeAcceptable(
        final AccessLogFilteringCriteriaCfg configuration,
        final List<Message> unacceptableReasons)
    {
      return true;
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationDeleteAcceptable(
        final AccessLogFilteringCriteriaCfg configuration,
        final List<Message> unacceptableReasons)
    {
      return true;
    }
  }



  /**
   * The category to use when logging responses.
   */
  private static final String CATEGORY_RESPONSE = "RES";

  /**
   * The category to use when logging requests.
   */
  private static final String CATEGORY_REQUEST = "REQ";



  /**
   * Returns an instance of the text access log publisher that will print all
   * messages to the provided writer. This is used to print the messages to the
   * console when the server starts up.
   *
   * @param writer
   *          The text writer where the message will be written to.
   * @param suppressInternal
   *          Indicates whether to suppress internal operations.
   * @return The instance of the text error log publisher that will print all
   *         messages to standard out.
   */
  public static TextAccessLogPublisher getStartupTextAccessPublisher(
      final TextWriter writer, final boolean suppressInternal)
  {
    final TextAccessLogPublisher startupPublisher =
      new TextAccessLogPublisher();
    startupPublisher.writer = writer;
    startupPublisher.buildFilters(suppressInternal, false,
        FilteringPolicy.NO_FILTERING);
    return startupPublisher;
  }



  private FileBasedAccessLogPublisherCfg currentConfig = null;
  private TextWriter writer = null;
  private Filter filter = null;
  private FilterChangeListener filterChangeListener = null;



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      final FileBasedAccessLogPublisherCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    final ArrayList<Message> messages = new ArrayList<Message>();

    final File logFile = getFileForPath(config.getLogFile());
    final FileNamingPolicy fnPolicy = new TimeStampNaming(logFile);
    try
    {
      final FilePermission perm = FilePermission.decodeUNIXMode(config
          .getLogFilePermissions());

      final boolean writerAutoFlush = config.isAutoFlush()
          && !config.isAsynchronous();

      TextWriter currentWriter;
      // Determine the writer we are using. If we were writing
      // asynchronously, we need to modify the underlying writer.
      if (writer instanceof AsyncronousTextWriter)
      {
        currentWriter = ((AsyncronousTextWriter) writer).getWrappedWriter();
      }
      else if (writer instanceof ParallelTextWriter)
      {
        currentWriter = ((ParallelTextWriter) writer).getWrappedWriter();
      }
      else
      {
        currentWriter = writer;
      }

      if (currentWriter instanceof MultifileTextWriter)
      {
        final MultifileTextWriter mfWriter =
          (MultifileTextWriter) currentWriter;

        mfWriter.setNamingPolicy(fnPolicy);
        mfWriter.setFilePermissions(perm);
        mfWriter.setAppend(config.isAppend());
        mfWriter.setAutoFlush(writerAutoFlush);
        mfWriter.setBufferSize((int) config.getBufferSize());
        mfWriter.setInterval(config.getTimeInterval());

        mfWriter.removeAllRetentionPolicies();
        mfWriter.removeAllRotationPolicies();

        for (final DN dn : config.getRotationPolicyDNs())
        {
          mfWriter.addRotationPolicy(DirectoryServer.getRotationPolicy(dn));
        }

        for (final DN dn : config.getRetentionPolicyDNs())
        {
          mfWriter.addRetentionPolicy(DirectoryServer.getRetentionPolicy(dn));
        }

        if (writer instanceof AsyncronousTextWriter && !config.isAsynchronous())
        {
          // The asynchronous setting is being turned off.
          final AsyncronousTextWriter asyncWriter =
            ((AsyncronousTextWriter) writer);
          writer = mfWriter;
          asyncWriter.shutdown(false);
        }

        if (writer instanceof ParallelTextWriter && !config.isAsynchronous())
        {
          // The asynchronous setting is being turned off.
          final ParallelTextWriter asyncWriter = ((ParallelTextWriter) writer);
          writer = mfWriter;
          asyncWriter.shutdown(false);
        }

        if (!(writer instanceof AsyncronousTextWriter)
            && config.isAsynchronous())
        {
          // The asynchronous setting is being turned on.
          final AsyncronousTextWriter asyncWriter = new AsyncronousTextWriter(
              "Asyncronous Text Writer for " + config.dn().toNormalizedString(),
              config.getQueueSize(), config.isAutoFlush(), mfWriter);
          writer = asyncWriter;
        }

        if (!(writer instanceof ParallelTextWriter) && config.isAsynchronous())
        {
          // The asynchronous setting is being turned on.
          final ParallelTextWriter asyncWriter = new ParallelTextWriter(
              "Parallel Text Writer for " + config.dn().toNormalizedString(),
              config.isAutoFlush(), mfWriter);
          writer = asyncWriter;
        }

        if ((currentConfig.isAsynchronous() && config.isAsynchronous())
            && (currentConfig.getQueueSize() != config.getQueueSize()))
        {
          adminActionRequired = true;
        }

        currentConfig = config;

        // Rebuild the filter using the new configuration and criteria.
        buildFilters();
      }
    }
    catch (final Exception e)
    {
      final Message message = ERR_CONFIG_LOGGING_CANNOT_CREATE_WRITER.get(
          config.dn().toString(), stackTraceToSingleLineString(e));
      resultCode = DirectoryServer.getServerErrorResultCode();
      messages.add(message);

    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void close()
  {
    writer.shutdown();

    if (currentConfig != null)
    {
      currentConfig.removeFileBasedAccessChangeListener(this);

      for (final String criteriaName : currentConfig
          .listAccessLogFilteringCriteria())
      {
        try
        {
          currentConfig.getAccessLogFilteringCriteria(criteriaName)
              .removeChangeListener(filterChangeListener);
        }
        catch (final ConfigException e)
        {
          // Ignore.
        }
      }
      currentConfig
          .removeAccessLogFilteringCriteriaAddListener(filterChangeListener);
      currentConfig
          .removeAccessLogFilteringCriteriaDeleteListener(filterChangeListener);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public DN getDN()
  {
    if (currentConfig != null)
    {
      return currentConfig.dn();
    }
    else
    {
      return null;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeAccessLogPublisher(
      final FileBasedAccessLogPublisherCfg cfg) throws ConfigException,
      InitializationException
  {
    final File logFile = getFileForPath(cfg.getLogFile());
    final FileNamingPolicy fnPolicy = new TimeStampNaming(logFile);

    try
    {
      final FilePermission perm = FilePermission.decodeUNIXMode(cfg
          .getLogFilePermissions());

      final LogPublisherErrorHandler errorHandler =
        new LogPublisherErrorHandler(cfg.dn());

      final boolean writerAutoFlush = cfg.isAutoFlush()
          && !cfg.isAsynchronous();

      final MultifileTextWriter writer = new MultifileTextWriter(
          "Multifile Text Writer for " + cfg.dn().toNormalizedString(),
          cfg.getTimeInterval(), fnPolicy, perm, errorHandler, "UTF-8",
          writerAutoFlush, cfg.isAppend(), (int) cfg.getBufferSize());

      // Validate retention and rotation policies.
      for (final DN dn : cfg.getRotationPolicyDNs())
      {
        writer.addRotationPolicy(DirectoryServer.getRotationPolicy(dn));
      }

      for (final DN dn : cfg.getRetentionPolicyDNs())
      {
        writer.addRetentionPolicy(DirectoryServer.getRetentionPolicy(dn));
      }

      if (cfg.isAsynchronous())
      {
        if (cfg.getQueueSize() > 0)
        {
          this.writer = new AsyncronousTextWriter(
              "Asyncronous Text Writer for " + cfg.dn().toNormalizedString(),
              cfg.getQueueSize(), cfg.isAutoFlush(), writer);
        }
        else
        {
          this.writer = new ParallelTextWriter("Parallel Text Writer for "
              + cfg.dn().toNormalizedString(), cfg.isAutoFlush(), writer);
        }
      }
      else
      {
        this.writer = writer;
      }
    }
    catch (final DirectoryException e)
    {
      final Message message = ERR_CONFIG_LOGGING_CANNOT_CREATE_WRITER.get(cfg
          .dn().toString(), String.valueOf(e));
      throw new InitializationException(message, e);

    }
    catch (final IOException e)
    {
      final Message message = ERR_CONFIG_LOGGING_CANNOT_OPEN_FILE.get(
          logFile.toString(), cfg.dn().toString(), String.valueOf(e));
      throw new InitializationException(message, e);

    }

    currentConfig = cfg;

    // Rebuild the filter using the new configuration and criteria.
    buildFilters();

    // Add change listeners.
    filterChangeListener = new FilterChangeListener();
    for (final String criteriaName : currentConfig
        .listAccessLogFilteringCriteria())
    {
      try
      {
        currentConfig.getAccessLogFilteringCriteria(criteriaName)
            .addChangeListener(filterChangeListener);
      }
      catch (final ConfigException e)
      {
        // Ignore.
      }
    }
    currentConfig
        .addAccessLogFilteringCriteriaAddListener(filterChangeListener);
    currentConfig
        .addAccessLogFilteringCriteriaDeleteListener(filterChangeListener);

    currentConfig.addFileBasedAccessChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationAcceptable(
      final AccessLogPublisherCfg configuration,
      final List<Message> unacceptableReasons)
  {
    final FileBasedAccessLogPublisherCfg config =
      (FileBasedAccessLogPublisherCfg) configuration;
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      final FileBasedAccessLogPublisherCfg config,
      final List<Message> unacceptableReasons)
  {
    // Make sure the permission is valid.
    try
    {
      final FilePermission filePerm = FilePermission.decodeUNIXMode(config
          .getLogFilePermissions());
      if (!filePerm.isOwnerWritable())
      {
        final Message message = ERR_CONFIG_LOGGING_INSANE_MODE.get(config
            .getLogFilePermissions());
        unacceptableReasons.add(message);
        return false;
      }
    }
    catch (final DirectoryException e)
    {
      final Message message = ERR_CONFIG_LOGGING_MODE_INVALID.get(
          config.getLogFilePermissions(), String.valueOf(e));
      unacceptableReasons.add(message);
      return false;
    }

    return true;
  }



  /**
   * Writes a message to the access logger with information about the abandon
   * request associated with the provided abandon operation.
   *
   * @param abandonOperation
   *          The abandon operation containing the information to use to log the
   *          abandon request.
   */
  @Override
  public void logAbandonRequest(final AbandonOperation abandonOperation)
  {
    if (!filter.isRequestLoggable(abandonOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(abandonOperation, "ABANDON", CATEGORY_REQUEST, buffer);
    buffer.append(" idToAbandon=");
    buffer.append(abandonOperation.getIDToAbandon());
    if (abandonOperation.isSynchronizationOperation())
    {
      buffer.append(" type=synchronization");
    }

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the result of
   * the provided abandon operation.
   *
   * @param abandonOperation
   *          The abandon operation containing the information to use to log the
   *          abandon request.
   */
  @Override
  public void logAbandonResult(final AbandonOperation abandonOperation)
  {
    if (!filter.isResponseLoggable(abandonOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(abandonOperation, "ABANDON", CATEGORY_RESPONSE, buffer);
    buffer.append(" result=");
    buffer.append(abandonOperation.getResultCode().getIntValue());
    final MessageBuilder msg = abandonOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" message=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    logAdditionalLogItems(abandonOperation, buffer);

    buffer.append(" etime=");
    buffer.append(abandonOperation.getProcessingTime());

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the add
   * request associated with the provided add operation.
   *
   * @param addOperation
   *          The add operation containing the information to use to log the add
   *          request.
   */
  @Override
  public void logAddRequest(final AddOperation addOperation)
  {
    if (!filter.isRequestLoggable(addOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(addOperation, "ADD", CATEGORY_REQUEST, buffer);
    buffer.append(" dn=\"");
    buffer.append(addOperation.getRawEntryDN().toString());
    buffer.append("\"");
    if (addOperation.isSynchronizationOperation())
    {
      buffer.append(" type=synchronization");
    }

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the add
   * response associated with the provided add operation.
   *
   * @param addOperation
   *          The add operation containing the information to use to log the add
   *          response.
   */
  @Override
  public void logAddResponse(final AddOperation addOperation)
  {
    if (!filter.isResponseLoggable(addOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(addOperation, "ADD", CATEGORY_RESPONSE, buffer);
    buffer.append(" result=");
    buffer.append(addOperation.getResultCode().getIntValue());

    final MessageBuilder msg = addOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" message=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    logAdditionalLogItems(addOperation, buffer);

    final DN proxiedAuthDN = addOperation.getProxiedAuthorizationDN();
    if (proxiedAuthDN != null)
    {
      buffer.append(" authzDN=\"");
      proxiedAuthDN.toString(buffer);
      buffer.append('\"');
    }

    buffer.append(" etime=");
    long etime = addOperation.getProcessingNanoTime();
    if (etime <= -1)
    {
      etime = addOperation.getProcessingTime();
    }
    buffer.append(etime);

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the bind
   * request associated with the provided bind operation.
   *
   * @param bindOperation
   *          The bind operation with the information to use to log the bind
   *          request.
   */
  @Override
  public void logBindRequest(final BindOperation bindOperation)
  {
    if (!filter.isRequestLoggable(bindOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(bindOperation, "BIND", CATEGORY_REQUEST, buffer);

    final String protocolVersion = bindOperation.getProtocolVersion();
    if (protocolVersion != null)
    {
      buffer.append(" version=");
      buffer.append(protocolVersion);
    }

    switch (bindOperation.getAuthenticationType())
    {
    case SIMPLE:
      buffer.append(" type=SIMPLE");
      break;
    case SASL:
      buffer.append(" type=SASL mechanism=");
      buffer.append(bindOperation.getSASLMechanism());
      break;
    default:
      buffer.append(" type=");
      buffer.append(bindOperation.getAuthenticationType());
      break;
    }

    buffer.append(" dn=\"");
    buffer.append(bindOperation.getRawBindDN().toString());
    buffer.append("\"");
    if (bindOperation.isSynchronizationOperation())
    {
      buffer.append(" type=synchronization");
    }

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the bind
   * response associated with the provided bind operation.
   *
   * @param bindOperation
   *          The bind operation containing the information to use to log the
   *          bind response.
   */
  @Override
  public void logBindResponse(final BindOperation bindOperation)
  {
    if (!filter.isResponseLoggable(bindOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(bindOperation, "BIND", CATEGORY_RESPONSE, buffer);
    buffer.append(" result=");
    buffer.append(bindOperation.getResultCode().getIntValue());

    final MessageBuilder msg = bindOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" message=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    final Message failureMessage = bindOperation.getAuthFailureReason();
    if (failureMessage != null)
    {
      buffer.append(" authFailureID=");
      buffer.append(failureMessage.getDescriptor().getId());
      buffer.append(" authFailureReason=\"");
      buffer.append(failureMessage);
      buffer.append('\"');
    }

    logAdditionalLogItems(bindOperation, buffer);

    if (bindOperation.getResultCode() == ResultCode.SUCCESS)
    {
      final AuthenticationInfo authInfo = bindOperation.getAuthenticationInfo();
      if (authInfo != null)
      {
        final DN authDN = authInfo.getAuthenticationDN();
        if (authDN != null)
        {
          buffer.append(" authDN=\"");
          authDN.toString(buffer);
          buffer.append('\"');

          final DN authzDN = authInfo.getAuthorizationDN();
          if (!authDN.equals(authzDN))
          {
            buffer.append(" authzDN=\"");
            if (authzDN != null)
            {
              authzDN.toString(buffer);
            }
            buffer.append('\"');
          }
        }
        else
        {
          buffer.append(" authDN=\"\"");
        }
      }
    }

    buffer.append(" etime=");
    long etime = bindOperation.getProcessingNanoTime();
    if (etime <= -1)
    {
      etime = bindOperation.getProcessingTime();
    }
    buffer.append(etime);

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the compare
   * request associated with the provided compare operation.
   *
   * @param compareOperation
   *          The compare operation containing the information to use to log the
   *          compare request.
   */
  @Override
  public void logCompareRequest(final CompareOperation compareOperation)
  {
    if (!filter.isRequestLoggable(compareOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(compareOperation, "COMPARE", CATEGORY_REQUEST, buffer);
    buffer.append(" dn=\"");
    buffer.append(compareOperation.getRawEntryDN().toString());
    buffer.append("\" attr=");
    buffer.append(compareOperation.getAttributeType().getNameOrOID());
    if (compareOperation.isSynchronizationOperation())
    {
      buffer.append(" type=synchronization");
    }

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the compare
   * response associated with the provided compare operation.
   *
   * @param compareOperation
   *          The compare operation containing the information to use to log the
   *          compare response.
   */
  @Override
  public void logCompareResponse(final CompareOperation compareOperation)
  {
    if (!filter.isResponseLoggable(compareOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(compareOperation, "COMPARE", CATEGORY_RESPONSE, buffer);
    buffer.append(" result=");
    buffer.append(compareOperation.getResultCode().getIntValue());

    final MessageBuilder msg = compareOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" message=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    logAdditionalLogItems(compareOperation, buffer);

    final DN proxiedAuthDN = compareOperation.getProxiedAuthorizationDN();
    if (proxiedAuthDN != null)
    {
      buffer.append(" authzDN=\"");
      proxiedAuthDN.toString(buffer);
      buffer.append('\"');
    }

    buffer.append(" etime=");
    long etime = compareOperation.getProcessingNanoTime();
    if (etime <= -1)
    {
      etime = compareOperation.getProcessingTime();
    }
    buffer.append(etime);

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about a new client
   * connection that has been established, regardless of whether it will be
   * immediately terminated.
   *
   * @param clientConnection
   *          The client connection that has been established.
   */
  @Override
  public void logConnect(final ClientConnection clientConnection)
  {
    if (!filter.isConnectLoggable(clientConnection))
    {
      return;
    }

    final long connectionID = clientConnection.getConnectionID();
    final StringBuilder buffer = new StringBuilder(100);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" CONNECT conn=");
    buffer.append(connectionID);
    buffer.append(" from=");
    buffer.append(clientConnection.getClientHostPort());
    buffer.append(" to=");
    buffer.append(clientConnection.getServerHostPort());
    buffer.append(" protocol=");
    buffer.append(clientConnection.getProtocol());

    writer.writeRecord(buffer.toString());

  }



  /**
   * Writes a message to the access logger with information about the delete
   * request associated with the provided delete operation.
   *
   * @param deleteOperation
   *          The delete operation with the information to use to log the delete
   *          request.
   */
  @Override
  public void logDeleteRequest(final DeleteOperation deleteOperation)
  {
    if (!filter.isRequestLoggable(deleteOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(deleteOperation, "DELETE", CATEGORY_REQUEST, buffer);
    buffer.append(" dn=\"");
    buffer.append(deleteOperation.getRawEntryDN().toString());
    buffer.append("\"");
    if (deleteOperation.isSynchronizationOperation())
    {
      buffer.append(" type=synchronization");
    }

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the delete
   * response associated with the provided delete operation.
   *
   * @param deleteOperation
   *          The delete operation containing the information to use to log the
   *          delete response.
   */
  @Override
  public void logDeleteResponse(final DeleteOperation deleteOperation)
  {
    if (!filter.isResponseLoggable(deleteOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(deleteOperation, "DELETE", CATEGORY_RESPONSE, buffer);
    buffer.append(" result=");
    buffer.append(deleteOperation.getResultCode().getIntValue());

    final MessageBuilder msg = deleteOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" message=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    logAdditionalLogItems(deleteOperation, buffer);

    final DN proxiedAuthDN = deleteOperation.getProxiedAuthorizationDN();
    if (proxiedAuthDN != null)
    {
      buffer.append(" authzDN=\"");
      proxiedAuthDN.toString(buffer);
      buffer.append('\"');
    }

    buffer.append(" etime=");
    long etime = deleteOperation.getProcessingNanoTime();
    if (etime <= -1)
    {
      etime = deleteOperation.getProcessingTime();
    }
    buffer.append(etime);

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the
   * termination of an existing client connection.
   *
   * @param clientConnection
   *          The client connection that has been terminated.
   * @param disconnectReason
   *          A generic disconnect reason for the connection termination.
   * @param message
   *          A human-readable message that can provide additional information
   *          about the disconnect.
   */
  @Override
  public void logDisconnect(final ClientConnection clientConnection,
      final DisconnectReason disconnectReason, final Message message)
  {
    if (!filter.isDisconnectLoggable(clientConnection))
    {
      return;
    }

    final long connectionID = clientConnection.getConnectionID();
    final StringBuilder buffer = new StringBuilder(100);
    buffer.append("[");
    buffer.append(TimeThread.getLocalTime());
    buffer.append("]");
    buffer.append(" DISCONNECT conn=");
    buffer.append(connectionID);
    buffer.append(" reason=\"");
    buffer.append(disconnectReason);

    if (message != null)
    {
      buffer.append("\" msg=\"");
      buffer.append(message);
    }

    buffer.append("\"");

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the extended
   * request associated with the provided extended operation.
   *
   * @param extendedOperation
   *          The extended operation containing the information to use to log
   *          the extended request.
   */
  @Override
  public void logExtendedRequest(final ExtendedOperation extendedOperation)
  {
    if (!filter.isRequestLoggable(extendedOperation))
    {
      return;
    }

    String name = null;
    final String oid = extendedOperation.getRequestOID();
    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(extendedOperation, "EXTENDED", CATEGORY_REQUEST, buffer);
    final ExtendedOperationHandler<?> extOpHandler = DirectoryServer
        .getExtendedOperationHandler(oid);
    if (extOpHandler != null)
    {
      name = extOpHandler.getExtendedOperationName();
      if (name != null)
      {
        buffer.append(" name=\"");
        buffer.append(name);
        buffer.append("\"");
      }
    }
    buffer.append(" oid=\"");
    buffer.append(oid);
    buffer.append("\"");
    if (extendedOperation.isSynchronizationOperation())
    {
      buffer.append(" type=synchronization");
    }

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the extended
   * response associated with the provided extended operation.
   *
   * @param extendedOperation
   *          The extended operation containing the info to use to log the
   *          extended response.
   */
  @Override
  public void logExtendedResponse(final ExtendedOperation extendedOperation)
  {
    if (!filter.isResponseLoggable(extendedOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(extendedOperation, "EXTENDED", CATEGORY_RESPONSE, buffer);

    String name = null;
    final String oid = extendedOperation.getResponseOID();
    if (oid != null)
    {
      final ExtendedOperationHandler<?> extOpHandler = DirectoryServer
          .getExtendedOperationHandler(oid);
      if (extOpHandler != null)
      {
        name = extOpHandler.getExtendedOperationName();
        if (name != null)
        {
          buffer.append(" name=\"");
          buffer.append(name);
          buffer.append("\"");
        }
      }
      buffer.append(" oid=\"");
      buffer.append(oid);
      buffer.append('\"');
    }

    buffer.append(" result=");
    buffer.append(extendedOperation.getResultCode().getIntValue());

    final MessageBuilder msg = extendedOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" message=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    logAdditionalLogItems(extendedOperation, buffer);

    buffer.append(" etime=");
    long etime = extendedOperation.getProcessingNanoTime();
    if (etime <= -1)
    {
      etime = extendedOperation.getProcessingTime();
    }
    buffer.append(etime);

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the modify DN
   * request associated with the provided modify DN operation.
   *
   * @param modifyDNOperation
   *          The modify DN operation containing the info to use to log the
   *          modify DN request.
   */
  @Override
  public void logModifyDNRequest(final ModifyDNOperation modifyDNOperation)
  {
    if (!filter.isRequestLoggable(modifyDNOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(modifyDNOperation, "MODIFYDN", CATEGORY_REQUEST, buffer);
    buffer.append(" dn=\"");
    buffer.append(modifyDNOperation.getRawEntryDN().toString());
    buffer.append("\" newRDN=\"");
    buffer.append(modifyDNOperation.getRawNewRDN().toString());
    buffer.append("\" deleteOldRDN=");
    buffer.append(modifyDNOperation.deleteOldRDN());

    final ByteString newSuperior = modifyDNOperation.getRawNewSuperior();
    if (newSuperior != null)
    {
      buffer.append(" newSuperior=\"");
      buffer.append(newSuperior.toString());
    }
    if (modifyDNOperation.isSynchronizationOperation())
    {
      buffer.append(" type=synchronization");
    }

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the modify DN
   * response associated with the provided modify DN operation.
   *
   * @param modifyDNOperation
   *          The modify DN operation containing the information to use to log
   *          the modify DN response.
   */
  @Override
  public void logModifyDNResponse(final ModifyDNOperation modifyDNOperation)
  {
    if (!filter.isResponseLoggable(modifyDNOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(modifyDNOperation, "MODIFYDN", CATEGORY_RESPONSE, buffer);
    buffer.append(" result=");
    buffer.append(modifyDNOperation.getResultCode().getIntValue());

    final MessageBuilder msg = modifyDNOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" message=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    logAdditionalLogItems(modifyDNOperation, buffer);

    final DN proxiedAuthDN = modifyDNOperation.getProxiedAuthorizationDN();
    if (proxiedAuthDN != null)
    {
      buffer.append(" authzDN=\"");
      proxiedAuthDN.toString(buffer);
      buffer.append('\"');
    }

    buffer.append(" etime=");
    long etime = modifyDNOperation.getProcessingNanoTime();
    if (etime <= -1)
    {
      etime = modifyDNOperation.getProcessingTime();
    }
    buffer.append(etime);

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the modify
   * request associated with the provided modify operation.
   *
   * @param modifyOperation
   *          The modify operation containing the information to use to log the
   *          modify request.
   */
  @Override
  public void logModifyRequest(final ModifyOperation modifyOperation)
  {
    if (!filter.isRequestLoggable(modifyOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(modifyOperation, "MODIFY", CATEGORY_REQUEST, buffer);
    buffer.append(" dn=\"");
    buffer.append(modifyOperation.getRawEntryDN().toString());
    buffer.append("\"");
    if (modifyOperation.isSynchronizationOperation())
    {
      buffer.append(" type=synchronization");
    }

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the modify
   * response associated with the provided modify operation.
   *
   * @param modifyOperation
   *          The modify operation containing the information to use to log the
   *          modify response.
   */
  @Override
  public void logModifyResponse(final ModifyOperation modifyOperation)
  {
    if (!filter.isResponseLoggable(modifyOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(modifyOperation, "MODIFY", CATEGORY_RESPONSE, buffer);
    buffer.append(" result=");
    buffer.append(modifyOperation.getResultCode().getIntValue());

    final MessageBuilder msg = modifyOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" message=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    logAdditionalLogItems(modifyOperation, buffer);

    final DN proxiedAuthDN = modifyOperation.getProxiedAuthorizationDN();
    if (proxiedAuthDN != null)
    {
      buffer.append(" authzDN=\"");
      proxiedAuthDN.toString(buffer);
      buffer.append('\"');
    }

    buffer.append(" etime=");
    long etime = modifyOperation.getProcessingNanoTime();
    if (etime <= -1)
    {
      etime = modifyOperation.getProcessingTime();
    }
    buffer.append(etime);

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the search
   * request associated with the provided search operation.
   *
   * @param searchOperation
   *          The search operation containing the info to use to log the search
   *          request.
   */
  @Override
  public void logSearchRequest(final SearchOperation searchOperation)
  {
    if (!filter.isRequestLoggable(searchOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(192);
    appendHeader(searchOperation, "SEARCH", CATEGORY_REQUEST, buffer);
    buffer.append(" base=\"");
    buffer.append(searchOperation.getRawBaseDN().toString());
    buffer.append("\" scope=");
    buffer.append(searchOperation.getScope());
    buffer.append(" filter=\"");
    searchOperation.getRawFilter().toString(buffer);

    final LinkedHashSet<String> attrs = searchOperation.getAttributes();
    if ((attrs == null) || attrs.isEmpty())
    {
      buffer.append("\" attrs=\"ALL\"");
    }
    else
    {
      buffer.append("\" attrs=\"");

      final Iterator<String> iterator = attrs.iterator();
      buffer.append(iterator.next());
      while (iterator.hasNext())
      {
        buffer.append(",");
        buffer.append(iterator.next());
      }

      buffer.append("\"");
    }
    if (searchOperation.isSynchronizationOperation())
    {
      buffer.append(" type=synchronization");
    }

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the completion
   * of the provided search operation.
   *
   * @param searchOperation
   *          The search operation containing the information to use to log the
   *          search result done message.
   */
  @Override
  public void logSearchResultDone(final SearchOperation searchOperation)
  {
    if (!filter.isResponseLoggable(searchOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(128);
    appendHeader(searchOperation, "SEARCH", CATEGORY_RESPONSE, buffer);
    buffer.append(" result=");
    buffer.append(searchOperation.getResultCode().getIntValue());

    final MessageBuilder msg = searchOperation.getErrorMessage();
    if ((msg != null) && (msg.length() > 0))
    {
      buffer.append(" message=\"");
      buffer.append(msg);
      buffer.append('\"');
    }

    buffer.append(" nentries=");
    buffer.append(searchOperation.getEntriesSent());

    logAdditionalLogItems(searchOperation, buffer);

    final DN proxiedAuthDN = searchOperation.getProxiedAuthorizationDN();
    if (proxiedAuthDN != null)
    {
      buffer.append(" authzDN=\"");
      proxiedAuthDN.toString(buffer);
      buffer.append('\"');
    }

    buffer.append(" etime=");
    long etime = searchOperation.getProcessingNanoTime();
    if (etime <= -1)
    {
      etime = searchOperation.getProcessingTime();
    }
    buffer.append(etime);

    writer.writeRecord(buffer.toString());
  }



  /**
   * Writes a message to the access logger with information about the unbind
   * request associated with the provided unbind operation.
   *
   * @param unbindOperation
   *          The unbind operation containing the info to use to log the unbind
   *          request.
   */
  @Override
  public void logUnbind(final UnbindOperation unbindOperation)
  {
    // FIXME: ensure that these are logged in combined mode.
    if (!filter.isRequestLoggable(unbindOperation))
    {
      return;
    }

    final StringBuilder buffer = new StringBuilder(100);
    appendHeader(unbindOperation, "UNBIND", CATEGORY_REQUEST, buffer);
    if (unbindOperation.isSynchronizationOperation())
    {
      buffer.append(" type=synchronization");
    }

    writer.writeRecord(buffer.toString());
  }



  // Appends the common log header information to the provided buffer.
  private void appendHeader(final Operation operation, final String opType,
      final String category, final StringBuilder buffer)
  {
    buffer.append('[');
    buffer.append(TimeThread.getLocalTime());
    buffer.append("] ");
    buffer.append(opType);
    buffer.append(' ');
    buffer.append(category);
    buffer.append(" conn=");
    buffer.append(operation.getConnectionID());
    buffer.append(" op=");
    buffer.append(operation.getOperationID());
    buffer.append(" msgID=");
    buffer.append(operation.getMessageID());
  }



  // Build an appropriate set of filters based on the configuration.
  private void buildFilters()
  {
    buildFilters(currentConfig.isSuppressInternalOperations(),
        currentConfig.isSuppressSynchronizationOperations(),
        currentConfig.getFilteringPolicy());
  }



  private void buildFilters(final boolean suppressInternal,
      final boolean suppressSynchronization, final FilteringPolicy policy)
  {
    final ArrayList<Filter> subFilters = new ArrayList<Filter>();
    if (currentConfig != null)
    {
      for (final String criteriaName : currentConfig
          .listAccessLogFilteringCriteria())
      {
        try
        {
          final AccessLogFilteringCriteriaCfg cfg = currentConfig
              .getAccessLogFilteringCriteria(criteriaName);
          subFilters.add(new CriteriaFilter(cfg));
        }
        catch (final ConfigException e)
        {
          // TODO: Unable to decode this access log criteria, so log a warning
          // and continue.
        }
      }
    }
    final Filter orFilter = new OrFilter(subFilters.toArray(new Filter[0]));
    filter = new RootFilter(suppressInternal, suppressSynchronization, policy,
        orFilter);
  }



  // Appends additional log items to the provided builder.
  private void logAdditionalLogItems(final Operation operation,
      final StringBuilder builder)
  {
    for (final AdditionalLogItem item : operation.getAdditionalLogItems())
    {
      builder.append(' ');
      item.toString(builder);
    }
  }

}
