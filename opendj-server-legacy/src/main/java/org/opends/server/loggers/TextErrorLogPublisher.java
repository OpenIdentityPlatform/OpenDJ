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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2015 ForgeRock AS.
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
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.messages.Severity;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.meta.ErrorLogPublisherCfgDefn;
import org.opends.server.admin.std.server.FileBasedErrorLogPublisherCfg;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ServerContext;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.FilePermission;
import org.opends.server.types.InitializationException;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.TimeThread;

/**
 * This class provides an implementation of an error log publisher.
 */
public class TextErrorLogPublisher
    extends ErrorLogPublisher<FileBasedErrorLogPublisherCfg>
    implements ConfigurationChangeListener<FileBasedErrorLogPublisherCfg>
{
  private TextWriter writer;
  private FileBasedErrorLogPublisherCfg currentConfig;

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
  public static TextErrorLogPublisher getServerStartupTextErrorPublisher(
      TextWriter writer)
  {
    TextErrorLogPublisher startupPublisher = new TextErrorLogPublisher();
    startupPublisher.writer = writer;
    startupPublisher.defaultSeverities.addAll(Arrays.asList(
        Severity.ERROR, Severity.WARNING, Severity.NOTICE));
    return startupPublisher;
  }



  /** {@inheritDoc} */
  @Override
  public void initializeLogPublisher(FileBasedErrorLogPublisherCfg config, ServerContext serverContext)
      throws ConfigException, InitializationException
  {
    File logFile = getFileForPath(config.getLogFile());
    FileNamingPolicy fnPolicy = new TimeStampNaming(logFile);

    try
    {
      FilePermission perm =
          FilePermission.decodeUNIXMode(config.getLogFilePermissions());

      LogPublisherErrorHandler errorHandler =
          new LogPublisherErrorHandler(config.dn());

      boolean writerAutoFlush =
          config.isAutoFlush() && !config.isAsynchronous();

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
        this.writer = new AsynchronousTextWriter("Asynchronous Text Writer for " + config.dn(),
            config.getQueueSize(), config.isAutoFlush(), writer);
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

    for(String overrideSeverity : config.getOverrideSeverity())
    {
      if(overrideSeverity != null)
      {
        int equalPos = overrideSeverity.indexOf('=');
        if (equalPos < 0)
        {
          throw new ConfigException(WARN_ERROR_LOGGER_INVALID_OVERRIDE_SEVERITY.get(overrideSeverity));
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
              severities.add(Severity.ERROR);
              severities.add(Severity.WARNING);
              severities.add(Severity.NOTICE);
              severities.add(Severity.INFORMATION);
            }
            else
            {
              try
              {
                severities.add(Severity.parseString(severityName));
              }
              catch (Exception e)
              {
                throw new ConfigException(WARN_ERROR_LOGGER_INVALID_SEVERITY.get(severityName));
              }
            }
          }
          definedSeverities.put(category, severities);
        }
        catch (Exception e)
        {
          throw new ConfigException(WARN_ERROR_LOGGER_INVALID_CATEGORY.get(category));
        }
      }
    }

    currentConfig = config;

    config.addFileBasedErrorChangeListener(this);
  }



  /** {@inheritDoc} */
  @Override
  public boolean isConfigurationAcceptable(
      FileBasedErrorLogPublisherCfg config, List<LocalizableMessage> unacceptableReasons)
  {
    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  /** {@inheritDoc} */
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

  /** {@inheritDoc} */
  @Override
  public ConfigChangeResult applyConfigurationChange(FileBasedErrorLogPublisherCfg config)
  {
    final ConfigChangeResult ccr = new ConfigChangeResult();

    setDefaultSeverities(config.getDefaultSeverity());

    definedSeverities.clear();
    for(String overrideSeverity : config.getOverrideSeverity())
    {
      if(overrideSeverity != null)
      {
        int equalPos = overrideSeverity.indexOf('=');
        if (equalPos < 0)
        {
          ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
          ccr.addMessage(WARN_ERROR_LOGGER_INVALID_OVERRIDE_SEVERITY.get(overrideSeverity));
        } else
        {
          String category = overrideSeverity.substring(0, equalPos);
          category = category.replace("-", "_").toUpperCase();
          try
          {
            Set<Severity> severities = new HashSet<>();
            StringTokenizer sevTokenizer =
              new StringTokenizer(overrideSeverity.substring(equalPos+1), ",");
            while (sevTokenizer.hasMoreElements())
            {
              String severityName = sevTokenizer.nextToken();
              severityName = severityName.replace("-", "_").toUpperCase();
              if(LOG_SEVERITY_ALL.equalsIgnoreCase(severityName))
              {
                severities.add(Severity.ERROR);
                severities.add(Severity.INFORMATION);
                severities.add(Severity.WARNING);
                severities.add(Severity.NOTICE);
              }
              else
              {
                try
                {
                  severities.add(Severity.parseString(severityName));
                }
                catch(Exception e)
                {
                  throw new ConfigException(WARN_ERROR_LOGGER_INVALID_SEVERITY.get(severityName));
                }
              }
            }
            definedSeverities.put(category, severities);
          }
          catch(Exception e)
          {
            ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
            ccr.addMessage(WARN_ERROR_LOGGER_INVALID_CATEGORY.get(category));
          }
        }
      }
    }

    File logFile = getFileForPath(config.getLogFile());
    FileNamingPolicy fnPolicy = new TimeStampNaming(logFile);
    try
    {
      FilePermission perm = FilePermission.decodeUNIXMode(config.getLogFilePermissions());

      boolean writerAutoFlush =
          config.isAutoFlush() && !config.isAsynchronous();

      TextWriter currentWriter;
      // Determine the writer we are using. If we were writing asynchronously,
      // we need to modify the underlying writer.
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

        if(writer instanceof AsynchronousTextWriter && !config.isAsynchronous())
        {
          // The asynchronous setting is being turned off.
          AsynchronousTextWriter asyncWriter = (AsynchronousTextWriter)writer;
          writer = mfWriter;
          asyncWriter.shutdown(false);
        }

        if (!(writer instanceof AsynchronousTextWriter) && config.isAsynchronous())
        {
          // The asynchronous setting is being turned on.
          writer = new AsynchronousTextWriter("Asynchronous Text Writer for " + config.dn(),
              config.getQueueSize(), config.isAutoFlush(), mfWriter);
        }

        if (currentConfig.isAsynchronous()
            && config.isAsynchronous()
            && currentConfig.getQueueSize() != config.getQueueSize())
        {
          ccr.setAdminActionRequired(true);
        }

        currentConfig = config;
      }
    }
    catch(Exception e)
    {
      ccr.setResultCode(DirectoryServer.getServerErrorResultCode());
      ccr.addMessage(ERR_CONFIG_LOGGING_CANNOT_CREATE_WRITER.get(
          config.dn(), stackTraceToSingleLineString(e)));
    }

    return ccr;
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
          defaultSeverities.add(Severity.ERROR);
          defaultSeverities.add(Severity.WARNING);
          defaultSeverities.add(Severity.INFORMATION);
          defaultSeverities.add(Severity.NOTICE);
        }
        else if (LOG_SEVERITY_NONE.equalsIgnoreCase(defaultSeverity))
        {
          // don't add any severity
        }
        else
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

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    writer.shutdown();

    if(currentConfig != null)
    {
      currentConfig.removeFileBasedErrorChangeListener(this);
    }
  }

  /** {@inheritDoc} */
  @Override
  public void log(String category, Severity severity, LocalizableMessage message, Throwable exception)
  {
    if (isEnabledFor(category, severity))
    {
      StringBuilder sb = new StringBuilder();
      sb.append("[");
      sb.append(TimeThread.getLocalTime());
      sb.append("] category=").append(category).
      append(" severity=").append(severity).
      append(" msgID=").append(message.resourceName())
                       .append('.')
                       .append(message.ordinal()).
      append(" msg=").append(message);
      if (exception != null)
      {
        sb.append(" exception=").append(
            StaticUtils.stackTraceToSingleLineString(exception));
      }

      writer.writeRecord(sb.toString());
    }
  }

  /** {@inheritDoc} */
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

  /** {@inheritDoc} */
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

