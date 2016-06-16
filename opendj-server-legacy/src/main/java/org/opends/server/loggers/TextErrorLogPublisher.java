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
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.loggers;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.LoggerMessages.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.messages.Severity;
import org.forgerock.opendj.config.server.ConfigurationChangeListener;
import org.forgerock.opendj.server.config.meta.ErrorLogPublisherCfgDefn;
import org.forgerock.opendj.server.config.server.FileBasedErrorLogPublisherCfg;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.FilePermission;
import org.opends.server.types.InitializationException;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TimeThread;

/** This class provides an implementation of an error log publisher. */
public class TextErrorLogPublisher
    extends ErrorLogPublisher<FileBasedErrorLogPublisherCfg>
    implements ConfigurationChangeListener<FileBasedErrorLogPublisherCfg>
{
  private TextWriter writer;
  private FileBasedErrorLogPublisherCfg currentConfig;
  private ServerContext serverContext;

  /**
   * Returns a new text error log publisher which will print all messages to the
   * provided writer. This publisher should be used by tools.
   *
   * @param writer
   *          The text writer where the message will be written to.
   * @return A new text error log publisher which will print all messages to the
   *         provided writer.
   */
  public static TextErrorLogPublisher getToolStartupTextErrorPublisher(TextWriter writer)
  {
    TextErrorLogPublisher startupPublisher = new TextErrorLogPublisher();
    startupPublisher.writer = writer;
    startupPublisher.defaultSeverities.addAll(Arrays.asList(Severity.values()));
    return startupPublisher;
  }

  /**
   * Returns a new text error log publisher which will print only notices,
   * severe warnings and errors, and fatal errors messages to the provided
   * writer. This less verbose publisher should be used by the directory server
   * during startup.
   *
   * @param writer
   *          The text writer where the message will be written to.
   * @return A new text error log publisher which will print only notices,
   *         severe warnings and errors, and fatal errors messages to the
   *         provided writer.
   */
  public static TextErrorLogPublisher getServerStartupTextErrorPublisher(TextWriter writer)
  {
    TextErrorLogPublisher startupPublisher = new TextErrorLogPublisher();
    startupPublisher.writer = writer;
    startupPublisher.defaultSeverities.addAll(Arrays.asList(
        Severity.ERROR, Severity.WARNING, Severity.NOTICE));
    return startupPublisher;
  }

  @Override
  public void initializeLogPublisher(FileBasedErrorLogPublisherCfg config, ServerContext serverContext)
      throws ConfigException, InitializationException
  {
    this.serverContext = serverContext;
    File logFile = getLogFile(config);
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

    setDefaultSeverities(config.getDefaultSeverity());

    ConfigChangeResult ccr = new ConfigChangeResult();
    setDefinedSeverities(config, ccr);
    if (!ccr.getMessages().isEmpty())
    {
      throw new ConfigException(ccr.getMessages().iterator().next());
    }

    currentConfig = config;

    config.addFileBasedErrorChangeListener(this);
  }

  private void setDefinedSeverities(FileBasedErrorLogPublisherCfg config, final ConfigChangeResult ccr)
  {
    for (String overrideSeverity : config.getOverrideSeverity())
    {
      if (overrideSeverity != null)
      {
        int equalPos = overrideSeverity.indexOf('=');
        if (equalPos < 0)
        {
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          ccr.addMessage(WARN_ERROR_LOGGER_INVALID_OVERRIDE_SEVERITY.get(overrideSeverity));
          return;
        }

        String category = overrideSeverity.substring(0, equalPos);
        category = category.replace("-", "_").toUpperCase();
        try
        {
          Set<Severity> severities = new HashSet<>();
          StringTokenizer sevTokenizer = new StringTokenizer(overrideSeverity.substring(equalPos + 1), ",");
          while (sevTokenizer.hasMoreElements())
          {
            String severityName = sevTokenizer.nextToken();
            severityName = severityName.replace("-", "_").toUpperCase();
            if (LOG_SEVERITY_ALL.equalsIgnoreCase(severityName))
            {
              addAllSeverities(severities);
            }
            else
            {
              try
              {
                severities.add(Severity.parseString(severityName));
              }
              catch (Exception e)
              {
                ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
                ccr.addMessage(WARN_ERROR_LOGGER_INVALID_SEVERITY.get(severityName));
                return;
              }
            }
          }
          definedSeverities.put(category, severities);
        }
        catch (Exception e)
        {
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          ccr.addMessage(WARN_ERROR_LOGGER_INVALID_CATEGORY.get(category));
          return;
        }
      }
    }
  }

  @Override
  public boolean isConfigurationAcceptable(
      FileBasedErrorLogPublisherCfg config, List<LocalizableMessage> unacceptableReasons)
  {
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  @Override
  public boolean isConfigurationChangeAcceptable(
      FileBasedErrorLogPublisherCfg config, List<LocalizableMessage> unacceptableReasons)
  {
    // Make sure the permission is valid.
    try
    {
      FilePermission filePerm = FilePermission.decodeUNIXMode(config.getLogFilePermissions());
      if(!filePerm.isOwnerWritable())
      {
        unacceptableReasons.add(ERR_CONFIG_LOGGING_INSANE_MODE.get(config.getLogFilePermissions()));
        return false;
      }
    }
    catch(DirectoryException e)
    {
      unacceptableReasons.add(ERR_CONFIG_LOGGING_MODE_INVALID.get(config.getLogFilePermissions(), e));
      return false;
    }

    for(String overrideSeverity : config.getOverrideSeverity())
    {
      if(overrideSeverity != null)
      {
        int equalPos = overrideSeverity.indexOf('=');
        if (equalPos < 0)
        {
          unacceptableReasons.add(WARN_ERROR_LOGGER_INVALID_OVERRIDE_SEVERITY.get(overrideSeverity));
          return false;
        }

        // No check on category because it can be any value
        StringTokenizer sevTokenizer = new StringTokenizer(overrideSeverity.substring(equalPos + 1), ",");
        while (sevTokenizer.hasMoreElements())
        {
          String severityName = sevTokenizer.nextToken();
          severityName = severityName.replace("-", "_").toUpperCase();
          if (!LOG_SEVERITY_ALL.equalsIgnoreCase(severityName))
          {
            try
            {
              Severity.parseString(severityName);
            }
            catch (Exception e)
            {
              unacceptableReasons.add(WARN_ERROR_LOGGER_INVALID_SEVERITY.get(severityName));
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  @Override
  public ConfigChangeResult applyConfigurationChange(FileBasedErrorLogPublisherCfg config)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    setDefaultSeverities(config.getDefaultSeverity());

    definedSeverities.clear();
    setDefinedSeverities(config, ccr);

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
        MultifileTextWriter mfWriter = (MultifileTextWriter)currentWriter;
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

        if (currentConfig.isAsynchronous() && config.isAsynchronous()
            && currentConfig.getQueueSize() != config.getQueueSize())
        {
          ccr.setAdminActionRequired(true);
        }

        currentConfig = config;
      }
      serverContext.getLoggerConfigManager().adjustJulLevel();
    }
    catch(Exception e)
    {
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ERR_CONFIG_LOGGING_CANNOT_CREATE_WRITER.get(
          config.dn(), stackTraceToSingleLineString(e)));
    }

    return ccr;
  }

  private void configure(MultifileTextWriter mfWriter, FileBasedErrorLogPublisherCfg config) throws DirectoryException
  {
    FilePermission perm = FilePermission.decodeUNIXMode(config.getLogFilePermissions());
    boolean writerAutoFlush = config.isAutoFlush() && !config.isAsynchronous();

    File logFile = getLogFile(config);
    FileNamingPolicy fnPolicy = new TimeStampNaming(logFile);

    mfWriter.setNamingPolicy(fnPolicy);
    mfWriter.setFilePermissions(perm);
    mfWriter.setAppend(config.isAppend());
    mfWriter.setAutoFlush(writerAutoFlush);
    mfWriter.setBufferSize((int) config.getBufferSize());
    mfWriter.setInterval(config.getTimeInterval());

    mfWriter.removeAllRetentionPolicies();
    mfWriter.removeAllRotationPolicies();
    for (DN dn : config.getRotationPolicyDNs())
    {
      mfWriter.addRotationPolicy(DirectoryServer.getRotationPolicy(dn));
    }
    for (DN dn : config.getRetentionPolicyDNs())
    {
      mfWriter.addRetentionPolicy(DirectoryServer.getRetentionPolicy(dn));
    }
  }

  private File getLogFile(FileBasedErrorLogPublisherCfg config)
  {
    return getFileForPath(config.getLogFile());
  }

  private boolean hasAsyncConfigChanged(FileBasedErrorLogPublisherCfg newConfig)
  {
    return !currentConfig.dn().equals(newConfig.dn())
        && currentConfig.isAutoFlush() != newConfig.isAutoFlush()
        && currentConfig.getQueueSize() != newConfig.getQueueSize();
  }

  private AsynchronousTextWriter newAsyncWriter(MultifileTextWriter mfWriter, FileBasedErrorLogPublisherCfg config)
  {
    String name = "Asynchronous Text Writer for " + config.dn();
    return new AsynchronousTextWriter(name, config.getQueueSize(), config.isAutoFlush(), mfWriter);
  }

  private void setDefaultSeverities(Set<ErrorLogPublisherCfgDefn.DefaultSeverity> defSevs)
  {
    defaultSeverities.clear();
    if (defSevs.isEmpty())
    {
      defaultSeverities.add(Severity.ERROR);
      defaultSeverities.add(Severity.WARNING);
    }
    else
    {
      for (ErrorLogPublisherCfgDefn.DefaultSeverity defSev : defSevs)
      {
        String defaultSeverity = defSev.toString();
        if (LOG_SEVERITY_ALL.equalsIgnoreCase(defaultSeverity))
        {
          addAllSeverities(defaultSeverities);
        }
        else if (!LOG_SEVERITY_NONE.equalsIgnoreCase(defaultSeverity))
        {
          Severity errorSeverity = Severity.parseString(defSev.name());
          if (errorSeverity != null)
          {
            defaultSeverities.add(errorSeverity);
          }
        }
      }
    }
  }

  private void addAllSeverities(Set<Severity> severities)
  {
    severities.add(Severity.ERROR);
    severities.add(Severity.WARNING);
    severities.add(Severity.INFORMATION);
    severities.add(Severity.NOTICE);
  }

  @Override
  public void close()
  {
    writer.shutdown();

    if(currentConfig != null)
    {
      currentConfig.removeFileBasedErrorChangeListener(this);
    }
  }

  @Override
  public void log(String source, Severity severity, LocalizableMessage message, Throwable exception)
  {
    String category = LoggingCategoryNames.getCategoryName(message.resourceName(), source);
    if (isEnabledFor(category, severity))
    {
      StringBuilder sb = new StringBuilder()
          .append("[")
          .append(TimeThread.getLocalTime())
          .append("] category=")
          .append(category)
          .append(" severity=")
          .append(severity)
          .append(" msgID=")
          .append(message.ordinal())
          .append(" msg=")
          .append(message.toString());
      if (exception != null)
      {
        sb.append(" exception=").append(StaticUtils.stackTraceToSingleLineString(exception));
      }

      writer.writeRecord(sb.toString());
    }
  }

  @Override
  public boolean isEnabledFor(String category, Severity severity)
  {
    Set<Severity> severities = definedSeverities.get(category);
    if (severities == null)
    {
      severities = defaultSeverities;
    }
    return severities.contains(severity);
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
