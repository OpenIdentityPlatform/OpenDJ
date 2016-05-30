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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import static org.forgerock.util.Utils.joinAsString;
import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.getFileForPath;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import static org.opends.server.util.TimeThread.getUserDefinedTime;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.server.config.server.FileBasedHTTPAccessLogPublisherCfg;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.FilePermission;
import org.opends.server.types.InitializationException;
import org.opends.server.util.TimeThread;

/**
 * This class provides the implementation of the HTTP access logger used by the
 * directory server.
 */
public final class TextHTTPAccessLogPublisher extends
    HTTPAccessLogPublisher<FileBasedHTTPAccessLogPublisherCfg>
    implements ConfigurationChangeListener<FileBasedHTTPAccessLogPublisherCfg>
{
  /** Enumeration of supported HTTP access log fields. */
  private enum LogField
  {
    // @formatter:off
    // Extended log format standard fields
    ELF_C_IP("c-ip")
            { Object valueOf(HTTPRequestInfo i, String tsf) { return i.getClientAddress (); } },
    ELF_C_PORT("c-port")
            { Object valueOf(HTTPRequestInfo i, String tsf) { return i.getClientPort(); } },
    ELF_CS_HOST("cs-host")
            { Object valueOf(HTTPRequestInfo i, String tsf) { return i.getClientHost(); } },
    ELF_CS_METHOD("cs-method")
            { Object valueOf(HTTPRequestInfo i, String tsf) { return i.getMethod(); } },
    ELF_CS_URI("cs-uri")
            { Object valueOf(HTTPRequestInfo i, String tsf) { return i.getUri().toString(); } },
    ELF_CS_URI_STEM("cs-uri-stem")
            { Object valueOf(HTTPRequestInfo i, String tsf) { return i.getUri().getRawPath(); } },
    ELF_CS_URI_QUERY("cs-uri-query")
            { Object valueOf(HTTPRequestInfo i, String tsf) { return i.getUri().getRawQuery(); } },
    ELF_CS_USER_AGENT("cs(User-Agent)")
            { Object valueOf(HTTPRequestInfo i, String tsf) { return i.getUserAgent(); } },
    ELF_CS_USERNAME("cs-username")
            { Object valueOf(HTTPRequestInfo i, String tsf) { return i.getAuthUser(); } },
    ELF_CS_VERSION("cs-version")
            { Object valueOf(HTTPRequestInfo i, String tsf) { return i.getProtocol(); } },
    ELF_S_COMPUTERNAME("s-computername")
            { Object valueOf(HTTPRequestInfo i, String tsf) { return i.getServerHost(); } },
    ELF_S_IP("s-ip")
            { Object valueOf(HTTPRequestInfo i, String tsf) { return i.getServerAddress(); } },
    ELF_S_PORT("s-port")
            { Object valueOf(HTTPRequestInfo i, String tsf) { return i.getServerPort(); } },
    ELF_SC_STATUS("sc-status")
            { Object valueOf(HTTPRequestInfo i, String tsf) { return i.getStatusCode(); } },

    // Application specific fields (eXtensions)
    X_CONNECTION_ID("x-connection-id")
            { Object valueOf(HTTPRequestInfo i, String tsf) { return i.getConnectionID(); } },
    X_DATETIME("x-datetime")
            { Object valueOf(HTTPRequestInfo i, String tsf) { return getUserDefinedTime(tsf); } },
    X_ETIME("x-etime")
            { Object valueOf(HTTPRequestInfo i, String tsf) { return i.getTotalProcessingTime(); } },
    X_TRANSACTION_ID("x-transaction-id")
            { Object valueOf(HTTPRequestInfo i, String tsf) { return i.getTransactionId(); } };
    // @formatter:on

    private final String name;

    LogField(final String name)
    {
      this.name = name;
    }

    String getName() {
      return name;
    }

    abstract Object valueOf(HTTPRequestInfo info, String timeStampFormat);
  }

  /**
   * Returns an instance of the text HTTP access log publisher that will print
   * all messages to the provided writer. This is used to print the messages to
   * the console when the server starts up.
   *
   * @param writer
   *          The text writer where the message will be written to.
   * @return The instance of the text error log publisher that will print all
   *         messages to standard out.
   */
  public static TextHTTPAccessLogPublisher getStartupTextHTTPAccessPublisher(
      final TextWriter writer)
  {
    final TextHTTPAccessLogPublisher startupPublisher = new TextHTTPAccessLogPublisher();
    startupPublisher.writer = writer;
    return startupPublisher;
  }

  private static final Map<String, LogField> FIELD_NAMES_TO_FIELD = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  static
  {
    for (LogField field : LogField.values())
    {
      FIELD_NAMES_TO_FIELD.put(field.getName(), field);
    }
  }

  private TextWriter writer;
  private FileBasedHTTPAccessLogPublisherCfg cfg;
  private List<LogField> logFormatFields = Collections.emptyList();
  private String timeStampFormat = "dd/MMM/yyyy:HH:mm:ss Z";

  @Override
  public ConfigChangeResult applyConfigurationChange(final FileBasedHTTPAccessLogPublisherCfg config)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    try
    {
      // Determine the writer we are using. If we were writing asynchronously,
      // we need to modify the underlying writer.
      TextWriter currentWriter;
      if (writer instanceof AsynchronousTextWriter)
      {
        currentWriter = ((AsynchronousTextWriter) writer).getWrappedWriter();
      }
      else
      {
        currentWriter = writer;
      }

      if (currentWriter instanceof MultifileTextWriter)
      {
        final MultifileTextWriter mfWriter = (MultifileTextWriter) currentWriter;
        configure(mfWriter, config);

        if (config.isAsynchronous())
        {
          if (writer instanceof AsynchronousTextWriter)
          {
            if (hasAsyncConfigChanged(config))
            {
              // reinstantiate
              final AsynchronousTextWriter previousWriter = (AsynchronousTextWriter) writer;
              writer = newAsyncWriter(mfWriter, config);
              previousWriter.shutdown(false);
            }
          }
          else
          {
            // turn async text writer on
            writer = newAsyncWriter(mfWriter, config);
          }
        }
        else
        {
          if (writer instanceof AsynchronousTextWriter)
          {
            // asynchronous is being turned off, remove async text writers.
            final AsynchronousTextWriter previousWriter = (AsynchronousTextWriter) writer;
            writer = mfWriter;
            previousWriter.shutdown(false);
          }
        }

        if (cfg.isAsynchronous() && config.isAsynchronous()
            && cfg.getQueueSize() != config.getQueueSize())
        {
          ccr.setAdminActionRequired(true);
        }

        if (!config.getLogRecordTimeFormat().equals(timeStampFormat))
        {
          TimeThread.removeUserDefinedFormatter(timeStampFormat);
          timeStampFormat = config.getLogRecordTimeFormat();
        }

        cfg = config;
        LocalizableMessage errorMessage = setLogFormatFields(cfg.getLogFormat());
        if (errorMessage != null)
        {
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          ccr.setAdminActionRequired(true);
          ccr.addMessage(errorMessage);
        }
      }
    }
    catch (final Exception e)
    {
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ERR_CONFIG_LOGGING_CANNOT_CREATE_WRITER.get(
          config.dn(), stackTraceToSingleLineString(e)));
    }

    return ccr;
  }

  private void configure(MultifileTextWriter mfWriter, FileBasedHTTPAccessLogPublisherCfg config)
      throws DirectoryException
  {
    final FilePermission perm = FilePermission.decodeUNIXMode(config.getLogFilePermissions());
    final boolean writerAutoFlush = config.isAutoFlush() && !config.isAsynchronous();

    final File logFile = getLogFile(config);
    final FileNamingPolicy fnPolicy = new TimeStampNaming(logFile);

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
  }

  private File getLogFile(final FileBasedHTTPAccessLogPublisherCfg config)
  {
    return getFileForPath(config.getLogFile());
  }

  private boolean hasAsyncConfigChanged(FileBasedHTTPAccessLogPublisherCfg newConfig)
  {
    return hasParallelConfigChanged(newConfig) && cfg.getQueueSize() != newConfig.getQueueSize();
  }

  private boolean hasParallelConfigChanged(FileBasedHTTPAccessLogPublisherCfg newConfig)
  {
    return !cfg.dn().equals(newConfig.dn()) && cfg.isAutoFlush() != newConfig.isAutoFlush();
  }

  private AsynchronousTextWriter newAsyncWriter(MultifileTextWriter mfWriter, FileBasedHTTPAccessLogPublisherCfg config)
  {
    String name = "Asynchronous Text Writer for " + config.dn();
    return new AsynchronousTextWriter(name, config.getQueueSize(), config.isAutoFlush(), mfWriter);
  }

  private LocalizableMessage setLogFormatFields(String logFormat)
  {
    // there will always be at least one field value due to the regexp validating the log format
    final List<String> fieldNames = Arrays.asList(logFormat.split(" "));
    final List<LogField> fields = new ArrayList<>(fieldNames.size());
    final List<String> unsupportedFields = new LinkedList<>();

    for (String fieldName : fieldNames)
    {
      final LogField field = FIELD_NAMES_TO_FIELD.get(fieldName);
      if (field != null)
      {
        fields.add(field);
      }
      else
      {
        unsupportedFields.add(fieldName);
      }
    }

    if (!unsupportedFields.isEmpty())
    {
      return WARN_CONFIG_LOGGING_UNSUPPORTED_FIELDS_IN_LOG_FORMAT.get(cfg.dn(), joinAsString(", ", unsupportedFields));
    }

    logFormatFields = fields;
    return null;
  }

  @Override
  public void initializeLogPublisher(
      final FileBasedHTTPAccessLogPublisherCfg cfg, ServerContext serverContext)
      throws ConfigException, InitializationException
  {
    final File logFile = getLogFile(cfg);
    final FileNamingPolicy fnPolicy = new TimeStampNaming(logFile);

    try
    {
      final FilePermission perm = FilePermission.decodeUNIXMode(cfg.getLogFilePermissions());
      final LogPublisherErrorHandler errorHandler = new LogPublisherErrorHandler(cfg.dn());
      final boolean writerAutoFlush = cfg.isAutoFlush() && !cfg.isAsynchronous();

      final MultifileTextWriter theWriter = new MultifileTextWriter(
          "Multifile Text Writer for " + cfg.dn(),
          cfg.getTimeInterval(), fnPolicy, perm, errorHandler, "UTF-8",
          writerAutoFlush, cfg.isAppend(), (int) cfg.getBufferSize());

      // Validate retention and rotation policies.
      for (final DN dn : cfg.getRotationPolicyDNs())
      {
        theWriter.addRotationPolicy(DirectoryServer.getRotationPolicy(dn));
      }
      for (final DN dn : cfg.getRetentionPolicyDNs())
      {
        theWriter.addRetentionPolicy(DirectoryServer.getRetentionPolicy(dn));
      }

      if (cfg.isAsynchronous())
      {
        this.writer = newAsyncWriter(theWriter, cfg);
      }
      else
      {
        this.writer = theWriter;
      }
    }
    catch (final DirectoryException e)
    {
      throw new InitializationException(
          ERR_CONFIG_LOGGING_CANNOT_CREATE_WRITER.get(cfg.dn(), e), e);
    }
    catch (final IOException e)
    {
      throw new InitializationException(
          ERR_CONFIG_LOGGING_CANNOT_OPEN_FILE.get(logFile, cfg.dn(), e), e);
    }

    this.cfg = cfg;
    LocalizableMessage error = setLogFormatFields(cfg.getLogFormat());
    if (error != null)
    {
      throw new InitializationException(error);
    }
    timeStampFormat = cfg.getLogRecordTimeFormat();

    cfg.addFileBasedHTTPAccessChangeListener(this);
  }

  @Override
  public boolean isConfigurationAcceptable(
      final FileBasedHTTPAccessLogPublisherCfg configuration,
      final List<LocalizableMessage> unacceptableReasons)
  {
    return isConfigurationChangeAcceptable(configuration, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      final FileBasedHTTPAccessLogPublisherCfg config,
      final List<LocalizableMessage> unacceptableReasons)
  {
    // Validate the time-stamp formatter.
    final String formatString = config.getLogRecordTimeFormat();
    try
    {
       new SimpleDateFormat(formatString);
    }
    catch (final Exception e)
    {
      unacceptableReasons.add(ERR_CONFIG_LOGGING_INVALID_TIME_FORMAT.get(formatString));
      return false;
    }

    // Make sure the permission is valid.
    try
    {
      final FilePermission filePerm = FilePermission.decodeUNIXMode(config.getLogFilePermissions());
      if (!filePerm.isOwnerWritable())
      {
        final LocalizableMessage message = ERR_CONFIG_LOGGING_INSANE_MODE.get(config.getLogFilePermissions());
        unacceptableReasons.add(message);
        return false;
      }
    }
    catch (final DirectoryException e)
    {
      unacceptableReasons.add(ERR_CONFIG_LOGGING_MODE_INVALID.get(config.getLogFilePermissions(), e));
      return false;
    }

    return true;
  }

  @Override
  public final void close()
  {
    writer.shutdown();
    TimeThread.removeUserDefinedFormatter(timeStampFormat);
    if (cfg != null)
    {
      cfg.removeFileBasedHTTPAccessChangeListener(this);
    }
  }

  @Override
  public final DN getDN()
  {
    return cfg != null ? cfg.dn() : null;
  }

  @Override
  public void logRequestInfo(HTTPRequestInfo ri)
  {
    final StringBuilder sb = new StringBuilder(100);
    for (LogField field : logFormatFields)
    {
      append(sb, field.valueOf(ri, timeStampFormat));
    }
    writer.writeRecord(sb.toString());
  }

  /**
   * Appends the value to the string builder using the default separator if needed.
   *
   * @param sb
   *          the StringBuilder where to append.
   * @param value
   *          the value to append.
   */
  private void append(final StringBuilder sb, Object value)
  {
    final char separator = '\t'; // as encouraged by the W3C working draft
    if (sb.length() > 0)
    {
      sb.append(separator);
    }

    if (value != null)
    {
      String val = String.valueOf(value);
      boolean useQuotes = val.contains(Character.toString(separator));
      if (useQuotes)
      {
        sb.append('"').append(val.replaceAll("\"", "\"\"")).append('"');
      }
      else
      {
        sb.append(val);
      }
    }
    else
    {
      sb.append('-');
    }
  }
}
