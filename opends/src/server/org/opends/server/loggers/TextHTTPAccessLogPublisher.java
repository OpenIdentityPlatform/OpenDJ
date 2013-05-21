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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.loggers;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.FileBasedHTTPAccessLogPublisherCfg;
import org.opends.server.api.HTTPAccessLogPublisher;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.FilePermission;
import org.opends.server.types.InitializationException;
import org.opends.server.types.ResultCode;
import org.opends.server.util.TimeThread;

/**
 * This class provides the implementation of the HTTP access logger used by the
 * directory server.
 */
public final class TextHTTPAccessLogPublisher extends
    HTTPAccessLogPublisher<FileBasedHTTPAccessLogPublisherCfg>
    implements ConfigurationChangeListener<FileBasedHTTPAccessLogPublisherCfg>
{

  // Extended log format standard fields
  private static final String ELF_C_IP = "c-ip";
  private static final String ELF_C_PORT = "c-port";
  private static final String ELF_CS_HOST = "cs-host";
  private static final String ELF_CS_METHOD = "cs-method";
  private static final String ELF_CS_URI_QUERY = "cs-uri-query";
  private static final String ELF_CS_USER_AGENT = "cs(User-Agent)";
  private static final String ELF_CS_USERNAME = "cs-username";
  private static final String ELF_CS_VERSION = "cs-version";
  private static final String ELF_S_COMPUTERNAME = "s-computername";
  private static final String ELF_S_IP = "s-ip";
  private static final String ELF_S_PORT = "s-port";
  private static final String ELF_SC_STATUS = "sc-status";
  // Application specific fields (eXtensions)
  private static final String X_CONNECTION_ID = "x-connection-id";
  private static final String X_DATETIME = "x-datetime";
  private static final String X_ETIME = "x-etime";

  private static final Set<String> ALL_SUPPORTED_FIELDS = new HashSet<String>(
      Arrays.asList(ELF_C_IP, ELF_C_PORT, ELF_CS_HOST, ELF_CS_METHOD,
          ELF_CS_URI_QUERY, ELF_CS_USER_AGENT, ELF_CS_USERNAME, ELF_CS_VERSION,
          ELF_S_COMPUTERNAME, ELF_S_IP, ELF_S_PORT, ELF_SC_STATUS,
          X_CONNECTION_ID, X_DATETIME, X_ETIME));

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
    final TextHTTPAccessLogPublisher startupPublisher =
      new TextHTTPAccessLogPublisher();
    startupPublisher.writer = writer;
    return startupPublisher;
  }



  private TextWriter writer = null;
  private FileBasedHTTPAccessLogPublisherCfg cfg = null;
  private List<String> logFormatFields;
  private String timeStampFormat = "dd/MMM/yyyy:HH:mm:ss Z";


  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(
      final FileBasedHTTPAccessLogPublisherCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    final List<Message> messages = new ArrayList<Message>();

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
      if (writer instanceof AsynchronousTextWriter)
      {
        currentWriter = ((AsynchronousTextWriter) writer).getWrappedWriter();
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

        if (writer instanceof AsynchronousTextWriter
            && !config.isAsynchronous())
        {
          // The asynchronous setting is being turned off.
          final AsynchronousTextWriter asyncWriter =
              (AsynchronousTextWriter) writer;
          writer = mfWriter;
          asyncWriter.shutdown(false);
        }

        if (writer instanceof ParallelTextWriter && !config.isAsynchronous())
        {
          // The asynchronous setting is being turned off.
          final ParallelTextWriter asyncWriter = (ParallelTextWriter) writer;
          writer = mfWriter;
          asyncWriter.shutdown(false);
        }

        if (!(writer instanceof AsynchronousTextWriter)
            && config.isAsynchronous())
        {
          // The asynchronous setting is being turned on.
          final AsynchronousTextWriter asyncWriter =
              new AsynchronousTextWriter("Asynchronous Text Writer for "
                  + config.dn().toNormalizedString(), config.getQueueSize(),
                  config.isAutoFlush(), mfWriter);
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

        if (cfg.isAsynchronous() && config.isAsynchronous()
            && cfg.getQueueSize() != config.getQueueSize())
        {
          adminActionRequired = true;
        }

        if (!config.getLogRecordTimeFormat().equals(timeStampFormat))
        {
          TimeThread.removeUserDefinedFormatter(timeStampFormat);
          timeStampFormat = config.getLogRecordTimeFormat();
        }

        cfg = config;
        logFormatFields = extractFieldsOrder(cfg.getLogFormat());
        Message errorMessage = validateLogFormat(logFormatFields);
        if (errorMessage != null)
        {
          resultCode = DirectoryServer.getServerErrorResultCode();
          adminActionRequired = true;
          messages.add(errorMessage);
        }
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


  private List<String> extractFieldsOrder(String logFormat)
  {
    // there will always be at least one field value due to the regexp
    // validating the log format
    return Arrays.asList(logFormat.split(" "));
  }

  /**
   * Validates the provided fields for the log format.
   *
   * @param fields
   *          the fields comprising the log format.
   * @return an error message when validation fails, null otherwise
   */
  private Message validateLogFormat(List<String> fields)
  {
    final Collection<String> unsupportedFields =
        subtract(fields, ALL_SUPPORTED_FIELDS);
    if (!unsupportedFields.isEmpty())
    { // there are some unsupported fields. List them.
      return WARN_CONFIG_LOGGING_UNSUPPORTED_FIELDS_IN_LOG_FORMAT.get(cfg.dn()
          .toString(), collectionToString(unsupportedFields, ", "));
    }
    if (fields.size() == unsupportedFields.size())
    { // all fields are unsupported
      return ERR_CONFIG_LOGGING_EMPTY_LOG_FORMAT.get(cfg.dn().toString());
    }
    return null;
  }

  /**
   * Returns a new Collection containing a - b.
   *
   * @param <T>
   * @param a
   *          the collection to subtract from, must not be null
   * @param b
   *          the collection to subtract, must not be null
   * @return a new collection with the results
   */
  private <T> Collection<T> subtract(Collection<T> a, Collection<T> b)
  {
    final Collection<T> result = new ArrayList<T>();
    for (T elem : a)
    {
      if (!b.contains(elem))
      {
        result.add(elem);
      }
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public void initializeLogPublisher(
      final FileBasedHTTPAccessLogPublisherCfg cfg)
      throws ConfigException, InitializationException
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

      final MultifileTextWriter theWriter = new MultifileTextWriter(
          "Multifile Text Writer for " + cfg.dn().toNormalizedString(),
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
        if (cfg.getQueueSize() > 0)
        {
          this.writer = new AsynchronousTextWriter(
              "Asynchronous Text Writer for " + cfg.dn().toNormalizedString(),
              cfg.getQueueSize(), cfg.isAutoFlush(), theWriter);
        }
        else
        {
          this.writer = new ParallelTextWriter("Parallel Text Writer for "
              + cfg.dn().toNormalizedString(), cfg.isAutoFlush(), theWriter);
        }
      }
      else
      {
        this.writer = theWriter;
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

    this.cfg = cfg;
    logFormatFields = extractFieldsOrder(cfg.getLogFormat());
    Message error = validateLogFormat(logFormatFields);
    if (error != null)
    {
      throw new InitializationException(error);
    }
    timeStampFormat = cfg.getLogRecordTimeFormat();

    cfg.addFileBasedHTTPAccessChangeListener(this);
  }


  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(
      final FileBasedHTTPAccessLogPublisherCfg configuration,
      final List<Message> unacceptableReasons)
  {
    return isConfigurationChangeAcceptable(configuration, unacceptableReasons);
  }


  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationChangeAcceptable(
      final FileBasedHTTPAccessLogPublisherCfg config,
      final List<Message> unacceptableReasons)
  {
    // Validate the time-stamp formatter.
    final String formatString = config.getLogRecordTimeFormat();
    try
    {
       new SimpleDateFormat(formatString);
    }
    catch (final Exception e)
    {
      final Message message = ERR_CONFIG_LOGGING_INVALID_TIME_FORMAT.get(String
          .valueOf(formatString));
      unacceptableReasons.add(message);
      return false;
    }

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


  /** {@inheritDoc} */
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

  /** {@inheritDoc} */
  @Override
  public final DN getDN()
  {
    return cfg != null ? cfg.dn() : null;
  }

  /** {@inheritDoc} */
  @Override
  public void logRequestInfo(HTTPRequestInfo ri)
  {
    final Map<String, Object> fields = new HashMap<String, Object>();
    fields.put(ELF_C_IP, ri.getClientAddress());
    fields.put(ELF_C_PORT, ri.getClientPort());
    fields.put(ELF_CS_HOST, ri.getClientHost());
    fields.put(ELF_CS_METHOD, ri.getMethod());
    fields.put(ELF_CS_URI_QUERY, ri.getQuery());
    fields.put(ELF_CS_USER_AGENT, ri.getUserAgent());
    fields.put(ELF_CS_USERNAME, ri.getAuthUser());
    fields.put(ELF_CS_VERSION, ri.getProtocol());
    fields.put(ELF_S_IP, ri.getServerAddress());
    fields.put(ELF_S_COMPUTERNAME, ri.getServerHost());
    fields.put(ELF_S_PORT, ri.getServerPort());
    fields.put(ELF_SC_STATUS, ri.getStatusCode());
    fields.put(X_CONNECTION_ID, ri.getConnectionID());
    fields.put(X_DATETIME, TimeThread.getUserDefinedTime(timeStampFormat));
    fields.put(X_ETIME, ri.getTotalProcessingTime());

    writeLogRecord(fields, logFormatFields);
  }

  private void writeLogRecord(Map<String, Object> fields,
      List<String> fieldnames)
  {
    if (fieldnames == null)
    {
      return;
    }
    final StringBuilder sb = new StringBuilder(100);
    for (String fieldname : fieldnames)
    {
      append(sb, fields.get(fieldname));
    }
    writer.writeRecord(sb.toString());
  }

  /**
   * Appends the value to the string builder using the default separator if
   * needed.
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
