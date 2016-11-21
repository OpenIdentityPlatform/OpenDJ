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
 * Copyright 2006-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ConfigurationAddListener;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.config.server.ConfigurationDeleteListener;
import org.forgerock.opendj.server.config.server.DebugTargetCfg;
import org.forgerock.opendj.server.config.server.FileBasedDebugLogPublisherCfg;
import org.opends.server.api.DirectoryThread;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.FilePermission;
import org.opends.server.types.InitializationException;
import org.opends.server.util.TimeThread;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * The debug log publisher implementation that writes debug messages to files
 * on disk. It also maintains the rotation and retention polices of the log
 * files.
 */
public class TextDebugLogPublisher
    extends DebugLogPublisher<FileBasedDebugLogPublisherCfg>
    implements ConfigurationChangeListener<FileBasedDebugLogPublisherCfg>,
               ConfigurationAddListener<DebugTargetCfg>,
               ConfigurationDeleteListener<DebugTargetCfg>
{
  private static long globalSequenceNumber;
  private TextWriter writer;
  private FileBasedDebugLogPublisherCfg currentConfig;

  /**
   * Returns an instance of the text debug log publisher that will print all
   * messages to the provided writer, based on the provided debug targets.
   *
   * @param debugTargets
   *          The targets defining which and how debug events are logged.
   * @param writer
   *          The text writer where the message will be written to.
   * @return The instance of the text error log publisher that will print all
   *         messages to standard out. May be {@code null} if no debug target is
   *         valid.
   */
  static TextDebugLogPublisher getStartupTextDebugPublisher(List<String> debugTargets, TextWriter writer)
  {
    TextDebugLogPublisher startupPublisher = null;
    for (String value : debugTargets)
    {
      int settingsStart = value.indexOf(":");

      //See if the scope and settings exists
      if (settingsStart > 0)
      {
        String scope = value.substring(0, settingsStart);
        TraceSettings settings = TraceSettings.parseTraceSettings(value.substring(settingsStart + 1));
        if (settings != null)
        {
          if (startupPublisher == null) {
            startupPublisher = new TextDebugLogPublisher();
            startupPublisher.writer = writer;
          }
          startupPublisher.addTraceSettings(scope, settings);
        }
      }
    }
    return startupPublisher;
  }

  @Override
  public boolean isConfigurationAcceptable(
      FileBasedDebugLogPublisherCfg config, List<LocalizableMessage> unacceptableReasons)
  {
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  @Override
  public void initializeLogPublisher(FileBasedDebugLogPublisherCfg config, ServerContext serverContext)
      throws ConfigException, InitializationException
  {
    File logFile = getFileForPath(config.getLogFile(), serverContext);
    FileNamingPolicy fnPolicy = new TimeStampNaming(logFile);

    try
    {
      FilePermission perm = FilePermission.decodeUNIXMode(config.getLogFilePermissions());
      LogPublisherErrorHandler errorHandler = new LogPublisherErrorHandler(config.dn());
      boolean writerAutoFlush = config.isAutoFlush() && !config.isAsynchronous();

      MultifileTextWriter writer = new MultifileTextWriter("Multifile Text Writer for " + config.dn(),
                                  config.getTimeInterval(),
                                  fnPolicy,
                                  perm,
                                  errorHandler,
                                  "UTF-8",
                                  writerAutoFlush,
                                  config.isAppend(),
                                  (int)config.getBufferSize());

      // Validate retention and rotation policies.
      for(DN dn : config.getRotationPolicyDNs())
      {
        writer.addRotationPolicy(DirectoryServer.getRotationPolicy(dn));
      }
      for(DN dn: config.getRetentionPolicyDNs())
      {
        writer.addRetentionPolicy(DirectoryServer.getRetentionPolicy(dn));
      }

      if(config.isAsynchronous())
      {
        this.writer = newAsyncWriter(writer, config);
      }
      else
      {
        this.writer = writer;
      }
    }
    catch(DirectoryException e)
    {
      throw new InitializationException(
          ERR_CONFIG_LOGGING_CANNOT_CREATE_WRITER.get(config.dn(), e), e);
    }
    catch(IOException e)
    {
      throw new InitializationException(
          ERR_CONFIG_LOGGING_CANNOT_OPEN_FILE.get(logFile, config.dn(), e), e);
    }

    config.addDebugTargetAddListener(this);
    config.addDebugTargetDeleteListener(this);

    addTraceSettings(null, getDefaultSettings(config));

    for(String name : config.listDebugTargets())
    {
      final DebugTargetCfg targetCfg = config.getDebugTarget(name);
      addTraceSettings(targetCfg.getDebugScope(), new TraceSettings(targetCfg));
    }

    currentConfig = config;

    config.addFileBasedDebugChangeListener(this);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      FileBasedDebugLogPublisherCfg config, List<LocalizableMessage> unacceptableReasons)
  {
    // Make sure the permission is valid.
    try
    {
      FilePermission filePerm = FilePermission.decodeUNIXMode(config.getLogFilePermissions());
      if (!filePerm.isOwnerWritable())
      {
        LocalizableMessage message = ERR_CONFIG_LOGGING_INSANE_MODE.get(config.getLogFilePermissions());
        unacceptableReasons.add(message);
        return false;
      }
    }
    catch (DirectoryException e)
    {
      unacceptableReasons.add(ERR_CONFIG_LOGGING_MODE_INVALID.get(config.getLogFilePermissions(), e));
      return false;
    }

    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(FileBasedDebugLogPublisherCfg config)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    addTraceSettings(null, getDefaultSettings(config));
    DebugLogger.updateTracerSettings();

    try
    {
      // Determine the writer we are using. If we were writing asynchronously,
      // we need to modify the underlying writer.
      TextWriter currentWriter;
      if(writer instanceof AsynchronousTextWriter)
      {
        currentWriter = ((AsynchronousTextWriter)writer).getWrappedWriter();
      }
      else
      {
        currentWriter = writer;
      }

      if(currentWriter instanceof MultifileTextWriter)
      {
        MultifileTextWriter mfWriter = (MultifileTextWriter)writer;
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

        if(currentConfig.isAsynchronous() && config.isAsynchronous() &&
            currentConfig.getQueueSize() != config.getQueueSize())
        {
          ccr.setAdminActionRequired(true);
        }

        currentConfig = config;
      }
    }
    catch(Exception e)
    {
      ccr.setResultCode(DirectoryServer.getCoreConfigManager().getServerErrorResultCode());
      ccr.addMessage(ERR_CONFIG_LOGGING_CANNOT_CREATE_WRITER.get(
          config.dn(), stackTraceToSingleLineString(e)));
    }

    return ccr;
  }

  private AsynchronousTextWriter newAsyncWriter(MultifileTextWriter writer, FileBasedDebugLogPublisherCfg config)
  {
    String name = "Asynchronous Text Writer for " + config.dn();
    return new AsynchronousTextWriter(name, config.getQueueSize(), config.isAutoFlush(), writer);
  }

  private void configure(MultifileTextWriter mfWriter, FileBasedDebugLogPublisherCfg config) throws DirectoryException
  {
    FilePermission perm = FilePermission.decodeUNIXMode(config.getLogFilePermissions());
    boolean writerAutoFlush = config.isAutoFlush() && !config.isAsynchronous();

    File logFile = getLogFile(config);
    FileNamingPolicy fnPolicy = new TimeStampNaming(logFile);

    mfWriter.setNamingPolicy(fnPolicy);
    mfWriter.setFilePermissions(perm);
    mfWriter.setAppend(config.isAppend());
    mfWriter.setAutoFlush(writerAutoFlush);
    mfWriter.setBufferSize((int)config.getBufferSize());
    mfWriter.setInterval(config.getTimeInterval());

    mfWriter.removeAllRetentionPolicies();
    mfWriter.removeAllRotationPolicies();
    for(DN dn : config.getRotationPolicyDNs())
    {
      mfWriter.addRotationPolicy(DirectoryServer.getRotationPolicy(dn));
    }
    for(DN dn: config.getRetentionPolicyDNs())
    {
      mfWriter.addRetentionPolicy(DirectoryServer.getRetentionPolicy(dn));
    }
  }

  private File getLogFile(FileBasedDebugLogPublisherCfg config)
  {
    return getFileForPath(config.getLogFile());
  }

  private boolean hasAsyncConfigChanged(FileBasedDebugLogPublisherCfg newConfig)
  {
    return !currentConfig.dn().equals(newConfig.dn())
        && currentConfig.isAutoFlush() != newConfig.isAutoFlush()
        && currentConfig.getQueueSize() != newConfig.getQueueSize();
  }

  private TraceSettings getDefaultSettings(FileBasedDebugLogPublisherCfg config)
  {
    return new TraceSettings(
        TraceSettings.Level.getLevel(true, config.isDefaultDebugExceptionsOnly()),
        config.isDefaultOmitMethodEntryArguments(),
        config.isDefaultOmitMethodReturnValue(),
        config.getDefaultThrowableStackFrames(),
        config.isDefaultIncludeThrowableCause());
  }

  @Override
  public boolean isConfigurationAddAcceptable(DebugTargetCfg config,
                                              List<LocalizableMessage> unacceptableReasons)
  {
    return !hasTraceSettings(config.getDebugScope());
  }

  @Override
  public boolean isConfigurationDeleteAcceptable(DebugTargetCfg config,
                                              List<LocalizableMessage> unacceptableReasons)
  {
    // A delete should always be acceptable.
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationAdd(DebugTargetCfg config)
  {
    addTraceSettings(config.getDebugScope(), new TraceSettings(config));

    DebugLogger.updateTracerSettings();

    return new ConfigChangeResult();
  }

  @Override
  public ConfigChangeResult applyConfigurationDelete(DebugTargetCfg config)
  {
    removeTraceSettings(config.getDebugScope());

    DebugLogger.updateTracerSettings();

    return new ConfigChangeResult();
  }

  @Override
  public void trace(TraceSettings settings, String signature,
      String sourceLocation, String msg, StackTraceElement[] stackTrace)
  {
    String stack = null;
    if (stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(stackTrace,
          settings.getStackDepth());
    }
    publish(signature, sourceLocation, msg, stack);
  }

  @Override
  public void traceException(TraceSettings settings, String signature,
      String sourceLocation, String msg, Throwable ex,
      StackTraceElement[] stackTrace)
  {
    String message = DebugMessageFormatter.format("%s caught={%s}", new Object[] { msg, ex });

    String stack = null;
    if (stackTrace != null)
    {
      stack = DebugStackTraceFormatter.formatStackTrace(ex, settings.getStackDepth(),
          settings.isIncludeCause());
    }
    publish(signature, sourceLocation, message, stack);
  }

  @Override
  public void close()
  {
    writer.shutdown();

    if(currentConfig != null)
    {
      currentConfig.removeFileBasedDebugChangeListener(this);
    }
  }

  /**
   * Publishes a record, optionally performing some "special" work:
   * - injecting a stack trace into the message
   * - format the message with argument values
   */
  private void publish(String signature, String sourceLocation, String msg,
                       String stack)
  {
    Thread thread = Thread.currentThread();

    StringBuilder buf = new StringBuilder();
    // Emit the timestamp.
    buf.append("[");
    buf.append(TimeThread.getLocalTime());
    buf.append("] ");

    // Emit the seq num
    buf.append(globalSequenceNumber++);
    buf.append(" ");

    // Emit the debug level.
    buf.append("trace ");

    // Emit thread info.
    buf.append("thread={");
    buf.append(thread.getName());
    buf.append("(");
    buf.append(thread.getId());
    buf.append(")} ");

    if(thread instanceof DirectoryThread)
    {
      buf.append("threadDetail={");
      for (Map.Entry<String, String> entry :
        ((DirectoryThread) thread).getDebugProperties().entrySet())
      {
        buf.append(entry.getKey());
        buf.append("=");
        buf.append(entry.getValue());
        buf.append(" ");
      }
      buf.append("} ");
    }

    // Emit method info.
    buf.append("method={");
    buf.append(signature);
    buf.append("(");
    buf.append(sourceLocation);
    buf.append(")} ");

    // Emit message.
    buf.append(msg);

    // Emit Stack Trace.
    if(stack != null)
    {
      buf.append("\nStack Trace:\n");
      buf.append(stack);
    }

    writer.writeRecord(buf.toString());
  }

  @Override
  public DN getDN()
  {
    if(currentConfig != null)
    {
      return currentConfig.dn();
    }
    return null;
  }
}
