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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.loggers;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.DebugLogger;
import org.opends.server.api.ProtocolElement;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.MultiChoiceConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DebugLogCategory;
import org.opends.server.types.DebugLogSeverity;
import org.opends.server.types.DN;
import org.opends.server.types.ResultCode;
import org.opends.server.util.StaticUtils;
import org.opends.server.util.ServerConstants;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.LoggerMessages.*;
import static org.opends.server.messages.MessageHandler.*;



/**
 * This class defines the debug logger.
 */
public class DirectoryDebugLogger extends DebugLogger
       implements ConfigurableComponent
{
  private static final int DEFAULT_TIME_INTERVAL = 30000;
  private static final int DEFAULT_BUFFER_SIZE = 0;
  // The underlying JDK logger.
  private Logger debugLogger = null;
  private DirectoryFileHandler fileHandler = null;

  // The hash map that will be used to define specific log severities for the
  // various categories.
  private HashMap<DebugLogCategory,HashSet<DebugLogSeverity>> definedSeverities;

  // The set of default log severities that will be used if no custom severities
  // have been defined for the associated category.
  private HashSet<DebugLogSeverity> defaultSeverities;

  // The DN of the config entry this component is associated with.
  private DN configDN;

  /**
   * Initializes this debug logger based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this debug logger.
   *
   * @throws  ConfigException  If an unrecoverable problem arises in the
   *                           process of performing the initialization.
   */

  public void initializeDebugLogger(ConfigEntry configEntry)
        throws ConfigException
  {
    configDN = configEntry.getDN();
    defaultSeverities = new HashSet<DebugLogSeverity>();

    HashSet<String> allowedValues = new HashSet<String> ();
    for(DebugLogSeverity sev : DebugLogSeverity.values())
    {
      allowedValues.add(sev.toString().toLowerCase());
    }

    List<String> defSev = getSeverities(configEntry,
                                        ATTR_LOGGER_DEFAULT_SEVERITY,
                                        allowedValues);

    if(defSev.isEmpty())
    {
      defaultSeverities.add(DebugLogSeverity.ERROR);
      defaultSeverities.add(DebugLogSeverity.WARNING);
    } else
    {
      for(String defStr : defSev)
      {
        DebugLogSeverity debugSeverity = DebugLogSeverity.getByName(defStr);
        if(debugSeverity != null)
        {
          defaultSeverities.add(debugSeverity);
        } else
        {
          System.err.println("Ignoring invalid severity name:" + defStr);
        }
      }
    }


    definedSeverities =
      new HashMap<DebugLogCategory,HashSet<DebugLogSeverity>>();

    HashSet<String> allowedSeverityValues = new HashSet<String> ();
    for(DebugLogCategory cat : DebugLogCategory.values())
    {
      for(DebugLogSeverity sev : DebugLogSeverity.values())
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
          DebugLogCategory category = DebugLogCategory.getByName(categoryName);
          if (category == null)
          {
            System.err.println("Invalid debug log category. Ignoring ...");
          } else
          {
            HashSet<DebugLogSeverity> severities =
                 new HashSet<DebugLogSeverity>();
            StringTokenizer sevTokenizer =
              new StringTokenizer(overrideSeverity.substring(equalPos+1), ",");
            while (sevTokenizer.hasMoreElements())
            {
              String severityName = sevTokenizer.nextToken();
              DebugLogSeverity severity =
                   DebugLogSeverity.getByName(severityName);
              if (severity == null)
              {
                System.err.println("Invalid debug log severity. Ignoring ...");
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

    StringConfigAttribute logFileNameAttr = (StringConfigAttribute)
            configEntry.getConfigAttribute(logFileStub);

    if(logFileNameAttr == null)
    {
      int msgID = MSGID_CONFIG_LOGGER_NO_FILE_NAME;
      String message = getMessage(msgID, configEntry.getDN().toString());
      throw new ConfigException(msgID, message);
    }

    debugLogger =
      Logger.getLogger("org.opends.server.loggers.DirectoryDebugLogger");
    debugLogger.setLevel(Level.ALL);

    File logFile = new File(logFileNameAttr.activeValue());
    if(!logFile.isAbsolute())
    {
      logFile = new File(DirectoryServer.getServerRoot() + File.separator +
                         logFileNameAttr.activeValue());
    }

    try
    {
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
      debugLogger.addHandler(fileHandler);

      int threadTimeInterval = RotationConfigUtil.getIntegerAttribute(
                                configEntry, ATTR_LOGGER_THREAD_INTERVAL,
                                MSGID_LOGGER_THREAD_INTERVAL);
      if(threadTimeInterval == -1)
      {
        threadTimeInterval = DEFAULT_TIME_INTERVAL;
      }

      LoggerThread lt = new LoggerThread("DebugLogger Thread",
            threadTimeInterval, fileHandler, null, null);
      lt.start();
    } catch(IOException ioe)
    {
      int    msgID   = MSGID_LOG_DEBUG_CANNOT_ADD_FILE_HANDLER;
      String message = getMessage(msgID, String.valueOf(ioe));
      throw new ConfigException(msgID, message, ioe);
    }
  }



  /**
   * Closes this debug logger and releases any resources it might have held.
   */
  public void closeDebugLogger()
  {
    // FIXME -- Take any appropriate action here.
    fileHandler.close();
  }



  /**
   * Writes a message to the debug logger indicating that the specified raw
   * data was read.
   *
   * @param  className   The fully-qualified name of the Java class in which
   *                     the data was read.
   * @param  methodName  The name of the method in which the data was read.
   * @param  buffer      The byte buffer containing the data that has been
   *       read.
   *                     The byte buffer must be in the same state when this
   *                     method returns as when it was entered.
   */
  public void debugBytesRead(String className, String methodName,
                                       ByteBuffer buffer)
  {
      HashSet<DebugLogSeverity> severities =
           definedSeverities.get(DebugLogCategory.DATA_READ);

      if(severities == null)
      {
        severities = defaultSeverities;
      }

      if(severities.contains(DebugLogSeverity.COMMUNICATION))
      {
        StringBuilder sb = new StringBuilder();
        sb.append("Bytes Read:").
        append("classname=").append(className).
        append(" methodname=").append(methodName).
        append(" bytebuffer=").
        append(ServerConstants.EOL);
        StaticUtils.byteArrayToHexPlusAscii(sb, buffer, 4);

        debugLogger.log(DirectoryLogLevel.COMMUNICATION, sb.toString());
      }
  }



  /**
   * Writes a msg to the debug logger indicating that the specified raw data
   * was written.
   *
   * @param  className   The fully-qualified name of the class in which the
   *                     data was written.
   * @param  methodName  The name of the method in which the data was written.
   * @param  buffer      The byte buffer containing the data that has been
   *                     written.  The byte buffer must be in the same state
   *                     when this method returns as when it was entered.
   */
  public void debugBytesWritten(String className, String methodName,
                                  ByteBuffer buffer)
  {
    HashSet<DebugLogSeverity> severities =
         definedSeverities.get(DebugLogCategory.DATA_WRITE);

    if (severities == null)
    {
      severities = defaultSeverities;
    }

    if (severities.contains(DebugLogSeverity.COMMUNICATION))
    {
      StringBuilder sb = new StringBuilder();
      sb.append("Bytes Written:").
      append("classname=").append(className).
      append(" methodname=").append(methodName).
      append(" bytebuffer=").
      append(ServerConstants.EOL);
      StaticUtils.byteArrayToHexPlusAscii(sb, buffer, 4);

      debugLogger.log(DirectoryLogLevel.COMMUNICATION, sb.toString());
    }
  }


  /**
   * Writes a message to the debug logger indicating that the constructor for
   * the specified class has been invoked.
   *
   * @param  className  The fully-qualified name of the Java class whose
   *                    constructor has been invoked.
   * @param  args       The set of arguments provided for the constructor.
   */
  public void debugConstructor(String className, String... args)
  {
    HashSet<DebugLogSeverity> severities =
         definedSeverities.get(DebugLogCategory.CONSTRUCTOR);
    if (severities == null)
    {
      severities = defaultSeverities;
    }

    if (severities.contains(DebugLogSeverity.INFO))
    {
      if (args.length == 0)
      {
        debugLogger.log(DirectoryLogLevel.INFO, "CONSTRUCTOR " + className);
      }
      else
      {
        StringBuilder sb = new StringBuilder();
        sb.append(args[0]);
        for(int i = 1; i < args.length; i++)
        {
          sb.append(",").append(args[i]);
        }
        debugLogger.log(DirectoryLogLevel.INFO,
                        "CONSTRUCTOR " + className +
                        " (" + sb.toString() + ")");
      }
    }

  }



  /**
   * Writes a message to the debug logger indicating that the specified method
   * has been entered.
   *
   * @param  className   The fully-qualified name of the Java class in which the
   *                     specified method resides.
   * @param  methodName  The name of the method that has been entered.
   * @param  args        The set of arguments provided to the method.
   */
  public void debugEnter(String className, String methodName, String... args)
  {
    HashSet<DebugLogSeverity> severities =
         definedSeverities.get(DebugLogCategory.METHOD_ENTER);
    if (severities == null)
    {
      severities = defaultSeverities;
    }

    if (severities.contains(DebugLogSeverity.INFO))
    {
      if (args.length == 0)
      {
        debugLogger.log(DirectoryLogLevel.INFO,
                        "Entered method:" + className + ":" + methodName);
      }
      else
      {
        StringBuilder sb = new StringBuilder();
        sb.append(args[0]);
        for(int i = 1; i < args.length; i++)
        {
          sb.append(",").append(args[i]);
        }
        debugLogger.log(DirectoryLogLevel.INFO,
                        "Entered method:" + className + ":" + methodName
                        + ":" + sb.toString());
      }
    }
  }


  /**
   * Writes a generic message to the debug logger using the provided
   * information.
   *
   * @param  category    The category associated with this debug message.
   * @param  severity    The severity associated with this debug message.
   * @param  className   The fully-qualified name of the Java class in which the
   *                     debug message was generated.
   * @param  methodName  The name of the method in which the debug message was
   *                     generated.
   * @param  message     The actual contents of the debug message.
   */
  public void debugMessage(DebugLogCategory category,
                                     DebugLogSeverity severity,
                                     String className, String methodName,
                                     String message)
  {

    HashSet<DebugLogSeverity> severities = definedSeverities.get(category);
    if(severities == null)
    {
      severities = defaultSeverities;
    }

    if(severities.contains(severity))
    {
      // FIXME - lookup the level based on the severity.
      debugLogger.log(DirectoryLogLevel.INFO,
          "classname:" + className + " methodname:" + methodName
          + ":" + message);
    }
  }



  /**
   * Writes a message to the debug logger containing information from the
   * provided exception that was thrown.
   *
   * @param  className   The fully-qualified name of the Java class in which the
   *                     exception was thrown.
   * @param  methodName  The name of the method in which the exception was
   *                     thrown.
   * @param  exception   The exception that was thrown.
   */
  public void debugException(String className, String methodName,
                                       Throwable exception)
  {

    HashSet<DebugLogSeverity> severities =
         definedSeverities.get(DebugLogCategory.EXCEPTION);
    if (severities == null)
    {
      severities = defaultSeverities;
    }

    if (severities.contains(DebugLogSeverity.ERROR))
    {
      StringBuilder buffer = new StringBuilder();
      buffer.append("classname=").append(className).
        append(" methodname=").append(methodName).append("\n").
        append( StaticUtils.stackTraceToString(exception));

      debugLogger.log(DirectoryLogLevel.ERROR, buffer.toString());
    }
  }



  /**
   * Writes a message to the debug logger indicating that the provided protocol
   * element has been read.
   *
   * @param  className        The fully-qualified name of the Java class in
   *                          which the protocol element was read.
   * @param  methodName       The name of the method in which the protocol
   *                          element was read.
   * @param  protocolElement  The protocol element that was read.
   */
  public void debugProtocolElementRead(String className, String methodName,
                                       ProtocolElement protocolElement)
  {
    HashSet<DebugLogSeverity> severities =
         definedSeverities.get(DebugLogCategory.PROTOCOL_READ);
    if (severities == null)
    {
      severities = defaultSeverities;
    }

    if (severities.contains(DebugLogSeverity.COMMUNICATION))
    {
      StringBuilder buffer = new StringBuilder();
      buffer.append("classname=").append(className).
        append(" methodname=").append(methodName);
      protocolElement.toString(buffer, 4);
      debugLogger.log(DirectoryLogLevel.COMMUNICATION, buffer.toString());
    }
  }



  /**
   * Writes a message to the debug logger indicating that the provided protocol
   * element has been written.
   *
   * @param  className        The fully-qualified name of the Java class in
   *                          which the protocol element was written.
   * @param  methodName       The name of the method in which the protocol
   *                          element was written.
   * @param  protocolElement  The protocol element that was written.
   */
  public void debugProtocolElementWritten(String className, String methodName,
                                       ProtocolElement protocolElement)
  {
    HashSet<DebugLogSeverity> severities =
         definedSeverities.get(DebugLogCategory.PROTOCOL_WRITE);
    if (severities == null)
    {
      severities = defaultSeverities;
    }

    if (severities.contains(DebugLogSeverity.COMMUNICATION))
    {
      StringBuilder buffer = new StringBuilder();
      buffer.append("classname=").append(className).
        append(" methodname=").append(methodName);
      protocolElement.toString(buffer, 4);
      debugLogger.log(DirectoryLogLevel.COMMUNICATION, buffer.toString());
    }
  }


  /**
   * Retrieves the hash code for this access logger.
   *
   * @return  The hash code for this access logger.
   */
  public int hashCode()
  {
    return debugLogger.hashCode();
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
   * Indicates whether the provided object is equal to this access logger.
   *
   * @param  obj  The object for which to make the determination.
   *
   * @return <CODE>true</CODE> if the provided object is determined to be equal
   *          to this access logger, or <CODE>false</CODE> if not.
   */
  public boolean equals(Object obj)
  {
    if(this == obj)
    {
      return true;
    }

    if((obj == null) || (obj.getClass() != this.getClass()))
    {
      return false;
    }
    return debugLogger.equals(obj);
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
      int msgID   = MSGID_CONFIG_LOGGER_INVALID_DEBUG_LOGGER_CLASS;
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
   *                          about any changes applied.
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
      initializeDebugLogger(configEntry);
    } catch(ConfigException ce)
    {
      return new ConfigChangeResult(DirectoryServer.getServerErrorResultCode(),
                                    false);
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
   * @param  allowedValues  The possible set of severity values that may be
   *                        used.
   *
   * @return  The list of values for the severity attribute.
   */

  private List<String> getSeverities(ConfigEntry configEntry,
                                     String attrName, Set<String> allowedValues)
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

