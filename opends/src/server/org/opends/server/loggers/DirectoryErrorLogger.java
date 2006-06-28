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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.loggers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.ErrorLogger;
import org.opends.server.core.DirectoryServer;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.MultiChoiceConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.LoggerMessages.*;
import static org.opends.server.messages.MessageHandler.*;



/**
 * This class provides an implementation of an error logger.
 * JDK Loggers are used for the implementation with multiple
 * handlers and formatters based on where the messages are
 * being logged (local file, DB, syslog etc)
 */
public class DirectoryErrorLogger extends ErrorLogger
       implements ConfigurableComponent
{
  private static final int DEFAULT_TIME_INTERVAL = 30000;
  private static final int DEFAULT_BUFFER_SIZE = 0;
  // The JDK logger instance
  private Logger errorLogger = null;
  private DirectoryFileHandler fileHandler = null;

  // The hash map that will be used to define specific log severities for the
  // various categories.
  private HashMap<ErrorLogCategory,HashSet<ErrorLogSeverity>> definedSeverities;

  // The set of default log severities that will be used if no custom severities
  // have been defined for the associated category.
  private HashSet<ErrorLogSeverity> defaultSeverities;

  // The DN of the config entry this component is associated with.
  private DN configDN;




  /**
   * Initializes this error logger based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this error logger.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   */
  public void initializeErrorLogger(ConfigEntry configEntry)
         throws ConfigException
  {

    configDN = configEntry.getDN();
    defaultSeverities = new HashSet<ErrorLogSeverity>();

    HashSet<String> allowedValues = new HashSet<String> ();
    for(ErrorLogSeverity sev : ErrorLogSeverity.values())
    {
      allowedValues.add(sev.toString().toLowerCase());
    }
    List<String> defSev = getSeverities(configEntry,
                                        ATTR_LOGGER_DEFAULT_SEVERITY,
                                        allowedValues);
    if(defSev.isEmpty())
    {
      defaultSeverities.add(ErrorLogSeverity.FATAL_ERROR);
      defaultSeverities.add(ErrorLogSeverity.SEVERE_ERROR);
      defaultSeverities.add(ErrorLogSeverity.SEVERE_WARNING);
    } else
    {
      for(String defStr : defSev)
      {
        ErrorLogSeverity errorSeverity = ErrorLogSeverity.getByName(defStr);
        if(errorSeverity != null)
        {
          defaultSeverities.add(errorSeverity);
        } else
        {
          System.err.println("Ignoring invalid severity name:" + defStr);
        }
      }
    }

    definedSeverities =
      new HashMap<ErrorLogCategory,HashSet<ErrorLogSeverity>>();
    HashSet<String> allowedSeverityValues = new HashSet<String>();
    for(ErrorLogCategory cat: ErrorLogCategory.values())
    {
      for(ErrorLogSeverity sev : ErrorLogSeverity.values())
      {
        String val = cat.toString().toLowerCase() + "=" +
                     sev.toString().toLowerCase();
        allowedSeverityValues.add(val);
      }
    }

    List<String> overrideSeverities = getSeverities(configEntry,
                                           ATTR_LOGGER_OVERRIDE_SEVERITY,
                                           allowedSeverityValues);
    for(String overrideSeverity: overrideSeverities)
    {
      if(overrideSeverity != null)
      {
        int equalPos = overrideSeverity.indexOf('=');
        if (equalPos < 0)
        {
          System.err.println("Invalid override of severity level. Ignoring...");
        } else
        {
          String categoryName = overrideSeverity.substring(0, equalPos);
          ErrorLogCategory category = ErrorLogCategory.getByName(categoryName);
          if (category == null)
          {
            System.err.println("Invalid error log category " + categoryName +
                               ". Ignoring ...");
          } else
          {
            HashSet<ErrorLogSeverity> severities =
              new HashSet<ErrorLogSeverity>();
            StringTokenizer sevTokenizer =
              new StringTokenizer(overrideSeverity.substring(equalPos+1), ",");
            while (sevTokenizer.hasMoreElements())
            {
              String severityName = sevTokenizer.nextToken();
              ErrorLogSeverity severity =
                ErrorLogSeverity.getByName(severityName);
              if (severity == null)
              {
                System.err.println("Invalid error log severity " +
                severityName + ". Ignoring ...");
              } else
              {
                severities.add(severity);
              }
            }
            definedSeverities.put(category, severities);
          }
        }
      }
    }

    StringConfigAttribute logFileStub =
         new StringConfigAttribute(ATTR_LOGGER_FILE,
                  getMessage(MSGID_CONFIG_LOGGER_DESCRIPTION_CLASS_NAME),
                  true, false, true);

    StringConfigAttribute logFileNameAttr =
      (StringConfigAttribute) configEntry.getConfigAttribute(logFileStub);

    if(logFileNameAttr == null)
    {
      int msgID = MSGID_CONFIG_LOGGER_NO_FILE_NAME;
      String message = getMessage(msgID, configEntry.getDN().toString());
      throw new ConfigException(msgID, message);
    }


    errorLogger =
      Logger.getLogger("org.opends.server.loggers.DirectoryErrorLogger");
    errorLogger.setLevel(Level.ALL);

    File logFile = new File(logFileNameAttr.activeValue());
    if(!logFile.isAbsolute())
    {
      logFile = new File(DirectoryServer.getServerRoot() + File.separator +
                         logFileNameAttr.activeValue());
    }

    // Add Handler (based on config entry)
    try
    {
      CopyOnWriteArrayList<RotationPolicy> rp =
        RotationConfigUtil.getRotationPolicies(configEntry);
      int bufferSize = RotationConfigUtil.getIntegerAttribute(configEntry,
                        ATTR_LOGGER_BUFFER_SIZE, MSGID_LOGGER_BUFFER_SIZE);
      if(bufferSize == -1)
      {
        bufferSize = DEFAULT_BUFFER_SIZE;
      }

      fileHandler = new DirectoryFileHandler(configEntry,
                                             logFile.getAbsolutePath(),
                                             bufferSize);
      fileHandler.setFormatter(new DirectoryFileFormatter(false));
      errorLogger.addHandler(fileHandler);
      if(rp != null)
      {
        ArrayList<ActionType> actions =
          RotationConfigUtil.getPostRotationActions(configEntry);

        fileHandler.setPostRotationActions(actions);

        for(RotationPolicy rotationPolicy : rp)
        {
          if(rotationPolicy instanceof SizeBasedRotationPolicy)
          {
            long fileSize =
              ((SizeBasedRotationPolicy) rotationPolicy).getMaxFileSize();
            fileHandler.setFileSize(fileSize);
            rp.remove(rotationPolicy);
          }
        }
      }

      CopyOnWriteArrayList<RetentionPolicy> retentionPolicies =
        RotationConfigUtil.getRetentionPolicies(configEntry);

      int threadTimeInterval = RotationConfigUtil.getIntegerAttribute(
                                configEntry, ATTR_LOGGER_THREAD_INTERVAL,
                                MSGID_LOGGER_THREAD_INTERVAL);
      if(threadTimeInterval == -1)
      {
        threadTimeInterval = DEFAULT_TIME_INTERVAL;
      }
      LoggerThread lt = new LoggerThread("ErrorLogger Thread",
                                         threadTimeInterval, fileHandler, rp,
                                         retentionPolicies);
      lt.start();

    } catch(IOException ioe) {
      int    msgID   = MSGID_LOG_ERROR_CANNOT_ADD_FILE_HANDLER;
      String message = getMessage(msgID, String.valueOf(ioe));
      throw new ConfigException(msgID, message, ioe);
    }
  }



  /**
   * Closes this error logger and releases any resources it might have held.
   */
  public void closeErrorLogger()
  {
    // FIXME -- Take any appropriate action here.
    fileHandler.close();
  }


  /**
   * Writes a message to the error log using the provided information.
   *
   * @param  category  The category that may be used to determine whether to
   *                   actually log this message.
   * @param  severity  The severity that may be used to determine whether to
   *                   actually log this message.
   * @param  message   The message to be logged.
   * @param  errorID   The error ID that uniquely identifies the format string
   *                   used to generate the provided message.
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
      sb.append("category=").append(category.getCategoryName()).
                append(" severity=").append(severity.getSeverityName()).
                append(" msgID=").append(String.valueOf(errorID)).
                append(" msg=").append(message);

      // FIXME - lookup the level based on the severity
      errorLogger.log(DirectoryLogLevel.FATAL_ERROR, sb.toString());
    }
  }

  /**
   * Indicates whether the provided object is equal to this error logger.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return <CODE>true</CODE> if the provided object is determined to be equal
   *          to this error logger, or <CODE>false</CODE> if not.
   */
  public boolean equals(Object o)
  {
    if(this == o)
    {
      return true;
    }
    if((o == null) || (o.getClass() != this.getClass()))
    {
      return false;
    }

    return errorLogger.equals(o);
  }


  /**
   * Retrieves the hash code for this error logger.
   *
   * @return  The hash code for this error logger.
   */
  public int hashCode()
  {
    return errorLogger.hashCode();
  }


  /**
   * Retrieves the DN of the configuration entry with which this component is
   * associated.
   *
   * @return  The DN of the configuration entry with which this component is
   *          associated.
   */
  public DN getConfigurableComponentEntryDN()
  {
    return configDN;
  }



  /**
   * Retrieves the set of configuration attributes that are associated with this
   * configurable component.
   *
   * @return  The set of configuration attributes that are associated with this
   *          configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {
    // NYI
    return null;
  }


  /**
   * Indicates whether the configuration entry that will result from a proposed
   * modification is acceptable to this change listener.
   *
   * @param  configEntry         The configuration entry that will result from
   *                             the requested update.
   * @param  unacceptableReasons  A buffer to which this method can append a
   *                             human-readable message explaining why the
   *                             proposed change is not acceptable.
   *
   * @return  <CODE>true</CODE> if the proposed entry contains an acceptable
   *          configuration, or <CODE>false</CODE> if it does not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                                          List<String> unacceptableReasons)
  {
    try
    {
      StringConfigAttribute logFileStub =
                  new StringConfigAttribute(ATTR_LOGGER_FILE,
                  getMessage(MSGID_CONFIG_LOGGER_DESCRIPTION_CLASS_NAME),
                  true, false, true);
      StringConfigAttribute logFileNameAttr = (StringConfigAttribute)
                            configEntry.getConfigAttribute(logFileStub);

      if(logFileNameAttr == null)
      {
        int msgID = MSGID_CONFIG_LOGGER_NO_FILE_NAME;
        String message = getMessage(msgID, configEntry.getDN().toString());
        unacceptableReasons.add(message);
        return false;
      }
    } catch (ConfigException ce)
    {
      int msgID   = MSGID_CONFIG_LOGGER_INVALID_ERROR_LOGGER_CLASS;
      String message = getMessage(msgID, this.getClass().getName(),
                                  configEntry.getDN().toString(),
                                  String.valueOf(ce));
      unacceptableReasons.add(message);
      return false;
    }
    return true;
  }



  /**
   * Attempts to apply a new configuration to this Directory Server component
   * based on the provided changed entry.
   *
   * @param  configEntry      The configuration entry that containing the
   *                          updated configuration for this component.
   * @param  detailedResults  Indicates whether to provide detailed information
   *                          about any configuration changes applied.
   *
   * @return  Information about the result of processing the configuration
   *          change.
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
                                                  boolean detailedResults)
  {
      fileHandler.close();
      try
      {
        initializeErrorLogger(configEntry);
      } catch(ConfigException ce)
      {
        // TODO - log the change failure.
        return new ConfigChangeResult(
                        DirectoryServer.getServerErrorResultCode(), false);

      }

      return new ConfigChangeResult(ResultCode.SUCCESS, false);
  }

  /**
   * Return the severities based on the attribute name from the specified
   * config entry.
   *
   * @param  configEntry    The configuration entry that containing the updated
   *                        configuration for this component.
   * @param  attrName       The attribute name for which to return the severity
   *                        values.
   * @param  allowedValues  The set of possible severity values that may be
   *                        used.
   *
   *
   * @return  The list of values for the severity attribute.
   */

  private List<String> getSeverities(ConfigEntry configEntry, String attrName,
                                     Set<String> allowedValues)
  {
    ArrayList<String> values = new ArrayList<String>();
    MultiChoiceConfigAttribute severityStub =
        new MultiChoiceConfigAttribute(attrName,
                  getMessage(MSGID_CONFIG_LOGGER_DESCRIPTION_CLASS_NAME),
                  false, true, true, allowedValues);

    MultiChoiceConfigAttribute severityAttr = null;
    try
    {
      severityAttr = (MultiChoiceConfigAttribute)
      configEntry.getConfigAttribute(severityStub);
    } catch(ConfigException ce)
    {
      ce.printStackTrace();
      // FIXME - handle exception
      System.err.println("Cannot retrieve the config value for:" + attrName);
      return values;
    }

    if(severityAttr == null)
    {
      return values;
    }

    return severityAttr.activeValues();
  }

}

