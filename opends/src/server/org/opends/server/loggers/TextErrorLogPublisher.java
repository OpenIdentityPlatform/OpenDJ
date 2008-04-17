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
package org.opends.server.loggers;
import org.opends.messages.Message;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.opends.server.api.*;
import org.opends.server.core.DirectoryServer;
import org.opends.server.config.ConfigException;
import org.opends.server.types.*;

import static org.opends.messages.ConfigMessages.*;
import static org.opends.messages.LoggerMessages.*;
import org.opends.messages.Severity;
import org.opends.messages.Category;
import org.opends.server.admin.std.server.ErrorLogPublisherCfg;
import org.opends.server.admin.std.server.FileBasedErrorLogPublisherCfg;
import org.opends.server.admin.std.meta.ErrorLogPublisherCfgDefn;
import org.opends.server.admin.server.ConfigurationChangeListener;
import static org.opends.server.util.StaticUtils.getFileForPath;
import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;
import org.opends.server.util.TimeThread;
import static org.opends.server.util.ServerConstants.*;


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
   * Returns an instance of the text error log publisher that will print
   * all messages to the provided writer. This is used to print the messages
   * to the console when the server starts up.
   *
   * @param writer The text writer where the message will be written to.
   * @return The instance of the text error log publisher that will print
   * all messages to standard out.
   */
  public static TextErrorLogPublisher
  getStartupTextErrorPublisher(TextWriter writer)
  {
    TextErrorLogPublisher startupPublisher = new TextErrorLogPublisher();
    startupPublisher.writer = writer;

    startupPublisher.defaultSeverities.addAll(Arrays.asList(Severity.values()));

    return startupPublisher;
  }

  /**
   * {@inheritDoc}
   */
  public void initializeErrorLogPublisher(FileBasedErrorLogPublisherCfg config)
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

      MultifileTextWriter writer =
          new MultifileTextWriter("Multifile Text Writer for " +
              config.dn().toNormalizedString(),
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
        this.writer = new AsyncronousTextWriter("Asyncronous Text Writer for " +
            config.dn().toNormalizedString(), config.getQueueSize(),
                                              config.isAutoFlush(),
                                              writer);
      }
      else
      {
        this.writer = writer;
      }
    }
    catch(DirectoryException e)
    {
      Message message = ERR_CONFIG_LOGGING_CANNOT_CREATE_WRITER.get(
          config.dn().toString(), String.valueOf(e));
      throw new InitializationException(message, e);

    }
    catch(IOException e)
    {
      Message message = ERR_CONFIG_LOGGING_CANNOT_OPEN_FILE.get(
          logFile.toString(), config.dn().toString(), String.valueOf(e));
      throw new InitializationException(message, e);

    }

    Set<ErrorLogPublisherCfgDefn.DefaultSeverity> defSevs =
        config.getDefaultSeverity();
    if(defSevs.isEmpty())
    {
      defaultSeverities.add(Severity.FATAL_ERROR);
      defaultSeverities.add(Severity.SEVERE_ERROR);
      defaultSeverities.add(Severity.SEVERE_WARNING);
    } else
    {
      for(ErrorLogPublisherCfgDefn.DefaultSeverity defSev : defSevs)
      {
        if(defSev.toString().equalsIgnoreCase(LOG_SEVERITY_ALL))
        {
          defaultSeverities.add(Severity.FATAL_ERROR);
          defaultSeverities.add(Severity.INFORMATION);
          defaultSeverities.add(Severity.MILD_ERROR);
          defaultSeverities.add(Severity.MILD_WARNING);
          defaultSeverities.add(Severity.NOTICE);
          defaultSeverities.add(Severity.SEVERE_ERROR);
          defaultSeverities.add(Severity.SEVERE_WARNING);
        }
        else if (defSev.toString().equalsIgnoreCase(LOG_SEVERITY_NONE))
        {
          // don't add any severity
        }
        else
        {
          Severity errorSeverity =
              Severity.parseString(defSev.name());
          if(errorSeverity != null)
          {
            defaultSeverities.add(errorSeverity);
          }
        }
      }
    }

    for(String overrideSeverity : config.getOverrideSeverity())
    {
      if(overrideSeverity != null)
      {
        int equalPos = overrideSeverity.indexOf('=');
        if (equalPos < 0)
        {
          Message msg =
              WARN_ERROR_LOGGER_INVALID_OVERRIDE_SEVERITY.get(overrideSeverity);
          throw new ConfigException(msg);

        } else
        {
          String categoryName = overrideSeverity.substring(0, equalPos);
          categoryName = categoryName.replace("-", "_").toUpperCase();
          try
          {
            Category category = Category.valueOf(categoryName);

            HashSet<Severity> severities =
                new HashSet<Severity>();
            StringTokenizer sevTokenizer =
              new StringTokenizer(overrideSeverity.substring(equalPos+1), ",");
            while (sevTokenizer.hasMoreElements())
            {
              String severityName = sevTokenizer.nextToken();
              severityName = severityName.replace("-", "_").toUpperCase();
              if(severityName.equalsIgnoreCase(LOG_SEVERITY_ALL))
              {
                severities.add(Severity.FATAL_ERROR);
                severities.add(Severity.INFORMATION);
                severities.add(Severity.MILD_ERROR);
                severities.add(Severity.MILD_WARNING);
                severities.add(Severity.NOTICE);
                severities.add(Severity.SEVERE_ERROR);
                severities.add(Severity.SEVERE_WARNING);
              }
              else
              {
                try
                {
                  Severity severity =
                      Severity.parseString(severityName);

                  severities.add(severity);
                }
                catch(Exception e)
                {
                  Message msg =
                      WARN_ERROR_LOGGER_INVALID_SEVERITY.get(severityName);
                  throw new ConfigException(msg);
                }
              }
            }
            definedSeverities.put(category, severities);
          }
          catch(Exception e)
          {
            Message msg = WARN_ERROR_LOGGER_INVALID_CATEGORY.get(categoryName);
            throw new ConfigException(msg);
          }
        }
      }
    }

    currentConfig = config;

    config.addFileBasedErrorChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isConfigurationAcceptable(ErrorLogPublisherCfg configuration,
                                           List<Message> unacceptableReasons)
  {
    FileBasedErrorLogPublisherCfg config =
         (FileBasedErrorLogPublisherCfg) configuration;

    return isConfigurationChangeAcceptable(config, unacceptableReasons);
  }

  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
      FileBasedErrorLogPublisherCfg config, List<Message> unacceptableReasons)
  {
    // Make sure the permission is valid.
    try
    {
      FilePermission filePerm =
          FilePermission.decodeUNIXMode(config.getLogFilePermissions());
      if(!filePerm.isOwnerWritable())
      {
        Message message = ERR_CONFIG_LOGGING_INSANE_MODE.get(
            config.getLogFilePermissions());
        unacceptableReasons.add(message);
        return false;
      }
    }
    catch(DirectoryException e)
    {
      Message message = ERR_CONFIG_LOGGING_MODE_INVALID.get(
          config.getLogFilePermissions(), String.valueOf(e));
      unacceptableReasons.add(message);
      return false;
    }

    for(String overrideSeverity : config.getOverrideSeverity())
    {
      if(overrideSeverity != null)
      {
        int equalPos = overrideSeverity.indexOf('=');
        if (equalPos < 0)
        {
          Message msg = WARN_ERROR_LOGGER_INVALID_OVERRIDE_SEVERITY.get(
                  overrideSeverity);
          unacceptableReasons.add(msg);
          return false;

        } else
        {
          String categoryName = overrideSeverity.substring(0, equalPos);
          categoryName = categoryName.replace("-", "_").toUpperCase();
          try
          {
            Category.valueOf(categoryName);
          }
          catch(Exception e)
          {
            Message msg = WARN_ERROR_LOGGER_INVALID_CATEGORY.get(categoryName);
            unacceptableReasons.add(msg);
          }

          StringTokenizer sevTokenizer =
              new StringTokenizer(overrideSeverity.substring(equalPos+1), ",");
          while (sevTokenizer.hasMoreElements())
          {
            String severityName = sevTokenizer.nextToken();
            severityName = severityName.replace("-", "_").toUpperCase();
            if(!severityName.equalsIgnoreCase(LOG_SEVERITY_ALL))
            {
              try
              {
                Severity.parseString(severityName);
              }
              catch(Exception e)
              {
                Message msg =
                    WARN_ERROR_LOGGER_INVALID_SEVERITY.get(severityName);
                unacceptableReasons.add(msg);
                return false;
              }
            }
          }
        }
      }
    }
    return true;
  }

  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
      FileBasedErrorLogPublisherCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    Set<ErrorLogPublisherCfgDefn.DefaultSeverity> defSevs =
        config.getDefaultSeverity();
    defaultSeverities.clear();
    if(defSevs.isEmpty())
    {
      defaultSeverities.add(Severity.FATAL_ERROR);
      defaultSeverities.add(Severity.SEVERE_ERROR);
      defaultSeverities.add(Severity.SEVERE_WARNING);
    } else
    {
      for(ErrorLogPublisherCfgDefn.DefaultSeverity defSev : defSevs)
      {
        if(defSev.toString().equalsIgnoreCase(LOG_SEVERITY_ALL))
        {
          defaultSeverities.add(Severity.FATAL_ERROR);
          defaultSeverities.add(Severity.INFORMATION);
          defaultSeverities.add(Severity.MILD_ERROR);
          defaultSeverities.add(Severity.MILD_WARNING);
          defaultSeverities.add(Severity.NOTICE);
          defaultSeverities.add(Severity.SEVERE_ERROR);
          defaultSeverities.add(Severity.SEVERE_WARNING);
        }
        else if (defSev.toString().equalsIgnoreCase(LOG_SEVERITY_NONE))
        {
          // don't add any severity
        }
        else
        {
          Severity errorSeverity =
              Severity.parseString(defSev.name());
          if(errorSeverity != null)
          {
            defaultSeverities.add(errorSeverity);
          }
        }
      }
    }

    definedSeverities.clear();
    for(String overrideSeverity : config.getOverrideSeverity())
    {
      if(overrideSeverity != null)
      {
        int equalPos = overrideSeverity.indexOf('=');
        if (equalPos < 0)
        {
          Message msg = WARN_ERROR_LOGGER_INVALID_OVERRIDE_SEVERITY.get(
                  overrideSeverity);
          resultCode = DirectoryServer.getServerErrorResultCode();
          messages.add(msg);
        } else
        {
          String categoryName = overrideSeverity.substring(0, equalPos);
          categoryName = categoryName.replace("-", "_").toUpperCase();
          try
          {
            Category category = Category.valueOf(categoryName);

            HashSet<Severity> severities =
                new HashSet<Severity>();
            StringTokenizer sevTokenizer =
              new StringTokenizer(overrideSeverity.substring(equalPos+1), ",");
            while (sevTokenizer.hasMoreElements())
            {
              String severityName = sevTokenizer.nextToken();
              severityName = severityName.replace("-", "_").toUpperCase();
              if(severityName.equalsIgnoreCase(LOG_SEVERITY_ALL))
              {
                severities.add(Severity.FATAL_ERROR);
                severities.add(Severity.INFORMATION);
                severities.add(Severity.MILD_ERROR);
                severities.add(Severity.MILD_WARNING);
                severities.add(Severity.NOTICE);
                severities.add(Severity.SEVERE_ERROR);
                severities.add(Severity.SEVERE_WARNING);
              }
              else
              {
                try
                {
                  Severity severity =
                      Severity.parseString(severityName);

                  severities.add(severity);
                }
                catch(Exception e)
                {
                  Message msg =
                      WARN_ERROR_LOGGER_INVALID_SEVERITY.get(severityName);
                  throw new ConfigException(msg);
                }
              }
            }
            definedSeverities.put(category, severities);
          }
          catch(Exception e)
          {
            Message msg = WARN_ERROR_LOGGER_INVALID_CATEGORY.get(categoryName);
            resultCode = DirectoryServer.getServerErrorResultCode();
            messages.add(msg);
          }
        }
      }
    }

    File logFile = getFileForPath(config.getLogFile());
    FileNamingPolicy fnPolicy = new TimeStampNaming(logFile);

    try
    {
      FilePermission perm =
          FilePermission.decodeUNIXMode(config.getLogFilePermissions());

      boolean writerAutoFlush =
          config.isAutoFlush() && !config.isAsynchronous();

      TextWriter currentWriter;
      // Determine the writer we are using. If we were writing asyncronously,
      // we need to modify the underlaying writer.
      if(writer instanceof AsyncronousTextWriter)
      {
        currentWriter = ((AsyncronousTextWriter)writer).getWrappedWriter();
      }
      else
      {
        currentWriter = writer;
      }

      if(currentWriter instanceof MultifileTextWriter)
      {
        MultifileTextWriter mfWriter = (MultifileTextWriter)writer;

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

        if(writer instanceof AsyncronousTextWriter && !config.isAsynchronous())
        {
          // The asynronous setting is being turned off.
          AsyncronousTextWriter asyncWriter = ((AsyncronousTextWriter)writer);
          writer = mfWriter;
          asyncWriter.shutdown(false);
        }

        if(!(writer instanceof AsyncronousTextWriter) &&
            config.isAsynchronous())
        {
          // The asynronous setting is being turned on.
          AsyncronousTextWriter asyncWriter =
              new AsyncronousTextWriter("Asyncronous Text Writer for " +
                  config.dn().toNormalizedString(), config.getQueueSize(),
                                                    config.isAutoFlush(),
                                                    mfWriter);
          writer = asyncWriter;
        }

        if((currentConfig.isAsynchronous() && config.isAsynchronous()) &&
            (currentConfig.getQueueSize() != config.getQueueSize()))
        {
          adminActionRequired = true;
        }

        currentConfig = config;
      }
    }
    catch(Exception e)
    {
      Message message = ERR_CONFIG_LOGGING_CANNOT_CREATE_WRITER.get(
              config.dn().toString(),
              stackTraceToSingleLineString(e));
      resultCode = DirectoryServer.getServerErrorResultCode();
      messages.add(message);

    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  public void close()
  {
    writer.shutdown();

    if(currentConfig != null)
    {
      currentConfig.removeFileBasedErrorChangeListener(this);
    }
  }


  /**
   * {@inheritDoc}
   */
  public void logError(Message message)
  {
    Severity severity = message.getDescriptor().getSeverity();
    Category category = message.getDescriptor().getCategory();
    int msgId = message.getDescriptor().getId();
    HashSet<Severity> severities = definedSeverities.get(category);
    if(severities == null)
    {
      severities = defaultSeverities;
    }

    if(severities.contains(severity))
    {

      StringBuilder sb = new StringBuilder();
      sb.append("[");
      sb.append(TimeThread.getLocalTime());
      sb.append("] category=").append(category).
          append(" severity=").append(severity).
          append(" msgID=").append(msgId).
          append(" msg=").append(message);

      writer.writeRecord(sb.toString());
    }
  }

  /**
   * {@inheritDoc}
   */
  public DN getDN()
  {
    if(currentConfig != null)
    {
      return currentConfig.dn();
    }
    else
    {
      return null;
    }
  }
}

