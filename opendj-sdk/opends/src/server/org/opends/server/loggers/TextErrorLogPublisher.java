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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.loggers;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.opends.server.api.*;
import org.opends.server.core.DirectoryServer;
import org.opends.server.config.ConfigException;
import org.opends.server.types.*;

import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.LoggerMessages.*;
import static org.opends.server.messages.MessageHandler.getMessage;
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

    startupPublisher.defaultSeverities.add(ErrorLogSeverity.FATAL_ERROR);
    startupPublisher.defaultSeverities.add(ErrorLogSeverity.SEVERE_ERROR);
    startupPublisher.defaultSeverities.add(ErrorLogSeverity.SEVERE_WARNING);
    startupPublisher.defaultSeverities.add(ErrorLogSeverity.NOTICE);

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
          FilePermission.decodeUNIXMode(config.getLogFileMode());

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
      for(DN dn : config.getRotationPolicyDN())
      {
        RotationPolicy policy = DirectoryServer.getRotationPolicy(dn);
        if(policy != null)
        {
          writer.addRotationPolicy(policy);
        }
        else
        {
          int msgID = MSGID_CONFIG_LOGGER_INVALID_ROTATION_POLICY;
          String message = getMessage(msgID, dn.toString(),
                                      config.dn().toString());
          throw new ConfigException(msgID, message);
        }
      }
      for(DN dn: config.getRetentionPolicyDN())
      {
        RetentionPolicy policy = DirectoryServer.getRetentionPolicy(dn);
        if(policy != null)
        {
          writer.addRetentionPolicy(policy);
        }
        else
        {
          int msgID = MSGID_CONFIG_LOGGER_INVALID_RETENTION_POLICY;
          String message = getMessage(msgID, dn.toString(),
                                      config.dn().toString());
          throw new ConfigException(msgID, message);
        }
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
      int msgID = MSGID_CONFIG_LOGGING_CANNOT_CREATE_WRITER;
      String message = getMessage(msgID, config.dn().toString(),
                                  String.valueOf(e));
      throw new InitializationException(msgID, message, e);

    }
    catch(IOException e)
    {
      int msgID = MSGID_CONFIG_LOGGING_CANNOT_CREATE_WRITER;
      String message = getMessage(msgID, config.dn().toString(),
                                  String.valueOf(e));
      throw new InitializationException(msgID, message, e);

    }

    Set<ErrorLogPublisherCfgDefn.DefaultSeverity> defSevs =
        config.getDefaultSeverity();
    if(defSevs.isEmpty())
    {
      defaultSeverities.add(ErrorLogSeverity.FATAL_ERROR);
      defaultSeverities.add(ErrorLogSeverity.SEVERE_ERROR);
      defaultSeverities.add(ErrorLogSeverity.SEVERE_WARNING);
    } else
    {
      for(ErrorLogPublisherCfgDefn.DefaultSeverity defSev : defSevs)
      {
        if(defSev.toString().equalsIgnoreCase(LOG_SEVERITY_ALL))
        {
          defaultSeverities.add(ErrorLogSeverity.FATAL_ERROR);
          defaultSeverities.add(ErrorLogSeverity.INFORMATIONAL);
          defaultSeverities.add(ErrorLogSeverity.MILD_ERROR);
          defaultSeverities.add(ErrorLogSeverity.MILD_WARNING);
          defaultSeverities.add(ErrorLogSeverity.NOTICE);
          defaultSeverities.add(ErrorLogSeverity.SEVERE_ERROR);
          defaultSeverities.add(ErrorLogSeverity.SEVERE_WARNING);
        }
        else
        {
          ErrorLogSeverity errorSeverity =
              ErrorLogSeverity.getByName(defSev.toString());
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
          int msgID = MSGID_ERROR_LOGGER_INVALID_OVERRIDE_SEVERITY;
          String msg = getMessage(msgID, overrideSeverity);
          throw new ConfigException(msgID, msg);

        } else
        {
          String categoryName = overrideSeverity.substring(0, equalPos);
          ErrorLogCategory category = ErrorLogCategory.getByName(categoryName);
          if (category == null)
          {
            int msgID = MSGID_ERROR_LOGGER_INVALID_CATEGORY;
            String msg = getMessage(msgID, categoryName);
            throw new ConfigException(msgID, msg);
          } else
          {
            HashSet<ErrorLogSeverity> severities =
                new HashSet<ErrorLogSeverity>();
            StringTokenizer sevTokenizer =
              new StringTokenizer(overrideSeverity.substring(equalPos+1), ",");
            while (sevTokenizer.hasMoreElements())
            {
              String severityName = sevTokenizer.nextToken();
              if(severityName.equalsIgnoreCase(LOG_SEVERITY_ALL))
              {
                severities.add(ErrorLogSeverity.FATAL_ERROR);
                severities.add(ErrorLogSeverity.INFORMATIONAL);
                severities.add(ErrorLogSeverity.MILD_ERROR);
                severities.add(ErrorLogSeverity.MILD_WARNING);
                severities.add(ErrorLogSeverity.NOTICE);
                severities.add(ErrorLogSeverity.SEVERE_ERROR);
                severities.add(ErrorLogSeverity.SEVERE_WARNING);
              }
              else
              {
                ErrorLogSeverity severity =
                    ErrorLogSeverity.getByName(severityName);
                if (severity == null)
                {
                  int msgID = MSGID_ERROR_LOGGER_INVALID_SEVERITY;
                  String msg = getMessage(msgID, severityName);
                  throw new ConfigException(msgID, msg);
                } else
                {
                  severities.add(severity);
                }
              }
            }
            definedSeverities.put(category, severities);
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
                                           List<String> unacceptableReasons)
  {
    FileBasedErrorLogPublisherCfg config =
         (FileBasedErrorLogPublisherCfg) configuration;

    // Validate retention and rotation policies.
    for(DN dn : config.getRotationPolicyDN())
    {
      RotationPolicy policy = DirectoryServer.getRotationPolicy(dn);
      if(policy == null)
      {
        int msgID = MSGID_CONFIG_LOGGER_INVALID_ROTATION_POLICY;
        String message = getMessage(msgID, dn.toString(),
                                    config.dn().toString());
        unacceptableReasons.add(message);
        return false;
      }
    }
    for(DN dn: config.getRetentionPolicyDN())
    {
      RetentionPolicy policy = DirectoryServer.getRetentionPolicy(dn);
      if(policy == null)
      {
        int msgID = MSGID_CONFIG_LOGGER_INVALID_RETENTION_POLICY;
        String message = getMessage(msgID, dn.toString(),
                                    config.dn().toString());
        unacceptableReasons.add(message);
        return false;
      }
    }

    for(String overrideSeverity : config.getOverrideSeverity())
    {
      if(overrideSeverity != null)
      {
        int equalPos = overrideSeverity.indexOf('=');
        if (equalPos < 0)
        {
          int msgID = MSGID_ERROR_LOGGER_INVALID_OVERRIDE_SEVERITY;
          String msg = getMessage(msgID, overrideSeverity);
          unacceptableReasons.add(msg);
          return false;

        } else
        {
          String categoryName = overrideSeverity.substring(0, equalPos);
          ErrorLogCategory category = ErrorLogCategory.getByName(categoryName);
          if (category == null)
          {
            int msgID = MSGID_ERROR_LOGGER_INVALID_CATEGORY;
            String msg = getMessage(msgID, categoryName);
            unacceptableReasons.add(msg);
            return false;
          } else
          {
            StringTokenizer sevTokenizer =
              new StringTokenizer(overrideSeverity.substring(equalPos+1), ",");
            while (sevTokenizer.hasMoreElements())
            {
              String severityName = sevTokenizer.nextToken();
              if(!severityName.equalsIgnoreCase(LOG_SEVERITY_ALL))
              {
                ErrorLogSeverity severity =
                    ErrorLogSeverity.getByName(severityName);
                if (severity == null)
                {
                  int msgID = MSGID_ERROR_LOGGER_INVALID_SEVERITY;
                  String msg = getMessage(msgID, severityName);
                  unacceptableReasons.add(msg);
                  return false;
                }
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
  public boolean isConfigurationChangeAcceptable(
      FileBasedErrorLogPublisherCfg config, List<String> unacceptableReasons)
  {
    // Make sure the permission is valid.
    try
    {
      if(!currentConfig.getLogFileMode().equalsIgnoreCase(
          config.getLogFileMode()))
      {
        FilePermission.decodeUNIXMode(config.getLogFileMode());
      }
      if(!currentConfig.getLogFile().equalsIgnoreCase(config.getLogFile()))
      {
        File logFile = getFileForPath(config.getLogFile());
        if(logFile.createNewFile())
        {
          logFile.delete();
        }
      }
    }
    catch(Exception e)
    {
      int msgID = MSGID_CONFIG_LOGGING_CANNOT_CREATE_WRITER;
      String message = getMessage(msgID, config.dn().toString(),
                                  stackTraceToSingleLineString(e));
      unacceptableReasons.add(message);
      return false;
    }

    return isConfigurationAcceptable(config, unacceptableReasons);
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
    ArrayList<String> messages = new ArrayList<String>();

    Set<ErrorLogPublisherCfgDefn.DefaultSeverity> defSevs =
        config.getDefaultSeverity();
    defaultSeverities.clear();
    if(defSevs.isEmpty())
    {
      defaultSeverities.add(ErrorLogSeverity.FATAL_ERROR);
      defaultSeverities.add(ErrorLogSeverity.SEVERE_ERROR);
      defaultSeverities.add(ErrorLogSeverity.SEVERE_WARNING);
    } else
    {
      for(ErrorLogPublisherCfgDefn.DefaultSeverity defSev : defSevs)
      {
        if(defSev.toString().equalsIgnoreCase(LOG_SEVERITY_ALL))
        {
          defaultSeverities.add(ErrorLogSeverity.FATAL_ERROR);
          defaultSeverities.add(ErrorLogSeverity.INFORMATIONAL);
          defaultSeverities.add(ErrorLogSeverity.MILD_ERROR);
          defaultSeverities.add(ErrorLogSeverity.MILD_WARNING);
          defaultSeverities.add(ErrorLogSeverity.NOTICE);
          defaultSeverities.add(ErrorLogSeverity.SEVERE_ERROR);
          defaultSeverities.add(ErrorLogSeverity.SEVERE_WARNING);
        }
        else
        {
          ErrorLogSeverity errorSeverity =
              ErrorLogSeverity.getByName(defSev.toString());
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
          int msgID = MSGID_ERROR_LOGGER_INVALID_OVERRIDE_SEVERITY;
          String msg = getMessage(msgID, overrideSeverity);
          resultCode = DirectoryServer.getServerErrorResultCode();
          messages.add(msg);
        } else
        {
          String categoryName = overrideSeverity.substring(0, equalPos);
          ErrorLogCategory category = ErrorLogCategory.getByName(categoryName);
          if (category == null)
          {
            int msgID = MSGID_ERROR_LOGGER_INVALID_CATEGORY;
            String msg = getMessage(msgID, categoryName);
            resultCode = DirectoryServer.getServerErrorResultCode();
            messages.add(msg);
          } else
          {
            HashSet<ErrorLogSeverity> severities =
                new HashSet<ErrorLogSeverity>();
            StringTokenizer sevTokenizer =
              new StringTokenizer(overrideSeverity.substring(equalPos+1), ",");
            while (sevTokenizer.hasMoreElements())
            {
              String severityName = sevTokenizer.nextToken();
              if(severityName.equalsIgnoreCase(LOG_SEVERITY_ALL))
              {
                severities.add(ErrorLogSeverity.FATAL_ERROR);
                severities.add(ErrorLogSeverity.INFORMATIONAL);
                severities.add(ErrorLogSeverity.MILD_ERROR);
                severities.add(ErrorLogSeverity.MILD_WARNING);
                severities.add(ErrorLogSeverity.NOTICE);
                severities.add(ErrorLogSeverity.SEVERE_ERROR);
                severities.add(ErrorLogSeverity.SEVERE_WARNING);
              }
              else
              {
                ErrorLogSeverity severity =
                    ErrorLogSeverity.getByName(severityName);
                if (severity == null)
                {
                  int msgID = MSGID_ERROR_LOGGER_INVALID_SEVERITY;
                  String msg = getMessage(msgID, severityName);
                  resultCode = DirectoryServer.getServerErrorResultCode();
                  messages.add(msg);
                } else
                {
                  severities.add(severity);
                }
              }
            }
            definedSeverities.put(category, severities);
          }
        }
      }
    }

    File logFile = getFileForPath(config.getLogFile());
    FileNamingPolicy fnPolicy = new TimeStampNaming(logFile);

    try
    {
      FilePermission perm =
          FilePermission.decodeUNIXMode(config.getLogFileMode());

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

        for(DN dn : config.getRotationPolicyDN())
        {
          RotationPolicy policy = DirectoryServer.getRotationPolicy(dn);
          if(policy != null)
          {
            mfWriter.addRotationPolicy(policy);
          }
          else
          {
            int msgID = MSGID_CONFIG_LOGGER_INVALID_ROTATION_POLICY;
            String message = getMessage(msgID, dn.toString(),
                                        config.dn().toString());
            resultCode = DirectoryServer.getServerErrorResultCode();
            messages.add(message);
          }
        }
        for(DN dn: config.getRetentionPolicyDN())
        {
          RetentionPolicy policy = DirectoryServer.getRetentionPolicy(dn);
          if(policy != null)
          {
            mfWriter.addRetentionPolicy(policy);
          }
          else
          {
            int msgID = MSGID_CONFIG_LOGGER_INVALID_RETENTION_POLICY;
            String message = getMessage(msgID, dn.toString(),
                                        config.dn().toString());
            resultCode = DirectoryServer.getServerErrorResultCode();
            messages.add(message);
          }
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
      int msgID = MSGID_CONFIG_LOGGING_CANNOT_CREATE_WRITER;
      String message = getMessage(msgID, config.dn().toString(),
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
  public void logError(ErrorLogCategory category,
                       ErrorLogSeverity severity, String message,
                       int errorID)
  {
    HashSet<ErrorLogSeverity> severities = definedSeverities.get(category);
    if(severities == null)
    {
      severities = defaultSeverities;
    }

    if(severities.contains(severity))
    {

      StringBuilder sb = new StringBuilder();
      sb.append("[");
      sb.append(TimeThread.getLocalTime());
      sb.append("] category=").append(category.getCategoryName()).
          append(" severity=").append(severity.getSeverityName()).
          append(" msgID=").append(String.valueOf(errorID)).
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

