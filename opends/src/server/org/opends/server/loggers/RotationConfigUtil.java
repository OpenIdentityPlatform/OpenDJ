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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.lang.reflect.Method;

import org.opends.server.core.DirectoryServer;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.IntegerConfigAttribute;
import org.opends.server.config.MultiChoiceConfigAttribute;
import org.opends.server.config.StringConfigAttribute;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.types.DebugLogLevel;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.messages.ConfigMessages.*;
import static org.opends.server.messages.LoggerMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.loggers.debug.DebugLogger.debugCought;
import static org.opends.server.loggers.debug.DebugLogger.debugEnabled;


/**
 * This utility class provides methods to retrieve logger
 * rotation policy related configuration information.
 */
public class RotationConfigUtil
{


  /**
   * Create rotation policies based on the logger configuration
   * information.
   *
   * @param configEntry The configuration entry for the logger.
   *
   * @return  The set of rotation policies defined in the provided configuration
   *          entry.
   *
   * @throws ConfigException If there is an invalid config entry.
   */

  public static CopyOnWriteArrayList<RotationPolicy>
                     getRotationPolicies(ConfigEntry configEntry)
         throws ConfigException
  {
    HashSet<String> allowedValues = new HashSet<String>();
    allowedValues.add("size");
    allowedValues.add("timeofday");
    allowedValues.add("fixedtime");

    MultiChoiceConfigAttribute rotationPolicyStub =
        new MultiChoiceConfigAttribute(ATTR_LOGGER_ROTATION_POLICY,
        getMessage(MSGID_CONFIG_LOGGER_DESCRIPTION_CLASS_NAME),
        false, true, true, allowedValues);

    MultiChoiceConfigAttribute rotationPolicyAttr = (MultiChoiceConfigAttribute)
            configEntry.getConfigAttribute(rotationPolicyStub);

    if(rotationPolicyAttr == null)
    {
      int msgID = MSGID_CONFIG_LOGGER_NO_ROTATION_POLICY;
      String message = getMessage(msgID, configEntry.getDN().toString());
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.SEVERE_WARNING,
               message, msgID);
      return null;
    }

    int msgID = 0;
    RotationPolicy rotationPolicy = null;

    CopyOnWriteArrayList<RotationPolicy> policies =
      new CopyOnWriteArrayList<RotationPolicy> ();
    List<String> strPolicies = rotationPolicyAttr.activeValues();

    for (String policy : strPolicies)
    {
      policy = policy.trim();
      if(policy.equalsIgnoreCase("Size"))
      {
        int sizeLimit = getIntegerAttribute(configEntry,
                                            ATTR_LOGGER_ROTATION_SIZE_LIMIT,
                                            MSGID_LOGGER_ROTATION_SIZE_LIMIT);
        if(sizeLimit == -1)
        {
          msgID = MSGID_CONFIG_LOGGER_NO_SIZE_LIMIT;
          String message = getMessage(msgID, configEntry.getDN().toString());
          throw new ConfigException(msgID, message);
        }
        rotationPolicy = new SizeBasedRotationPolicy(sizeLimit);
      } else if(policy.equalsIgnoreCase("FixedTime"))
      {
        int fixedTimeLimit = getIntegerAttribute(configEntry,
                                  ATTR_LOGGER_ROTATION_FIXED_TIME_LIMIT,
                                  MSGID_LOGGER_ROTATION_FIXED_TIME_LIMIT);
        if(fixedTimeLimit == -1)
        {
          msgID = MSGID_CONFIG_LOGGER_NO_TIME_LIMIT;
          String message = getMessage(msgID, configEntry.getDN().toString());
          throw new ConfigException(msgID, message);
        }

        rotationPolicy = new TimeLimitRotationPolicy(fixedTimeLimit);

      } else if(policy.equalsIgnoreCase("TimeOfDay"))
      {
        StringConfigAttribute timeLimitStub =
             new StringConfigAttribute(ATTR_LOGGER_ROTATION_TIME_OF_DAY,
                      getMessage(MSGID_CONFIG_LOGGER_DESCRIPTION_CLASS_NAME),
                      true, false, true);

        StringConfigAttribute timeLimitAttr = (StringConfigAttribute)
            configEntry.getConfigAttribute(timeLimitStub);

        if(timeLimitAttr == null)
        {
          msgID = MSGID_CONFIG_LOGGER_NO_TIME_LIMIT;
          String message = getMessage(msgID, configEntry.getDN().toString());
          throw new ConfigException(msgID, message);
        }

        String timeLimitStr = timeLimitAttr.activeValue().trim();
        StringTokenizer tokenizer = new StringTokenizer(timeLimitStr, ",");
        ArrayList<String> tokens = new ArrayList<String> ();
        while(tokenizer.hasMoreTokens())
        {
          String token = tokenizer.nextToken().trim();
          tokens.add(token);
        }
        int[] timeOfDays = new int[tokens.size()];
        for(int i = 0; i < timeOfDays.length; i++)
        {
          String str = tokens.get(i);
          try
          {
            timeOfDays[i] = Integer.parseInt(str);
          } catch(Exception e)
          {
            String msg = getMessage(msgID, configEntry.getDN().toString());
            throw new ConfigException(msgID, msg);
          }
        }

        rotationPolicy = new FixedTimeRotationPolicy(timeOfDays);
      } else
      {
        // Invalid policy - throw exception
        msgID = MSGID_CONFIG_LOGGER_INVALID_ROTATION_POLICY;
        String message = getMessage(msgID, policy,
                                    configEntry.getDN().toString());
        throw new ConfigException(msgID, message);
      }

      policies.add(rotationPolicy);
    }

    return policies;
  }

  /**
   * Create retention policies based on the logger configuration
   * information.
   *
   * @param configEntry The configuration entry for the logger.
   *
   * @return  The set of retention policies contained in the provided
   *          configuration entry.
   *
   * @throws ConfigException If there is an invalid config entry.
   */

  public static CopyOnWriteArrayList<RetentionPolicy>
                     getRetentionPolicies(ConfigEntry configEntry)
         throws ConfigException
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
      throw new ConfigException(msgID, message);
    }
    File logFile = new File(logFileNameAttr.activeValue().trim());
    if(!logFile.isAbsolute())
    {
      logFile = new File (DirectoryServer.getServerRoot() + File.separator +
                            logFileNameAttr.activeValue().trim());
    }
    String prefix = logFile.getName();
    String directory = logFile.getParent();

    HashSet<String> allowedValues = new HashSet<String>();
    allowedValues.add("numberoffiles");
    allowedValues.add("diskspaceused");
    allowedValues.add("freediskspace");

    MultiChoiceConfigAttribute retentionPolicyStub =
        new MultiChoiceConfigAttribute(ATTR_LOGGER_RETENTION_POLICY,
        getMessage(MSGID_CONFIG_LOGGER_DESCRIPTION_CLASS_NAME),
        false, true, true, allowedValues);

    MultiChoiceConfigAttribute retentionPolicyAttr =
         (MultiChoiceConfigAttribute)
         configEntry.getConfigAttribute(retentionPolicyStub);

    if(retentionPolicyAttr == null)
    {
      int msgID = MSGID_CONFIG_LOGGER_NO_RETENTION_POLICY;
      String message = getMessage(msgID, configEntry.getDN().toString());
      logError(ErrorLogCategory.CONFIGURATION, ErrorLogSeverity.MILD_WARNING,
               message, msgID);
      return null;
    }

    int msgID = 0;
    RetentionPolicy retentionPolicy = null;

    CopyOnWriteArrayList<RetentionPolicy> policies =
      new CopyOnWriteArrayList<RetentionPolicy> ();
    List<String> strPolicies = retentionPolicyAttr.activeValues();

    for (String policy : strPolicies)
    {
      policy = policy.trim();
      if(policy.equalsIgnoreCase("numberOfFiles"))
      {
        int numFiles = getIntegerAttribute(configEntry,
                            ATTR_LOGGER_RETENTION_NUMBER_OF_FILES,
                            MSGID_LOGGER_RETENTION_NUMBER_OF_FILES);
        if(numFiles == -1)
        {
          msgID = MSGID_CONFIG_LOGGER_NO_NUMBER_OF_FILES;
          String message = getMessage(msgID, configEntry.getDN().toString());
          throw new ConfigException(msgID, message);
        }
        retentionPolicy = new FileNumberRetentionPolicy(directory, prefix,
          numFiles);
      } else if(policy.equalsIgnoreCase("diskSpaceUsed"))
      {
        int size = getIntegerAttribute(configEntry,
                                       ATTR_LOGGER_RETENTION_DISK_SPACE_USED,
                                       MSGID_LOGGER_RETENTION_DISK_SPACE_USED);
        if(size == -1)
        {
          msgID = MSGID_CONFIG_LOGGER_NO_DISK_SPACE_USED;
          String message = getMessage(msgID, configEntry.getDN().toString());
          throw new ConfigException(msgID, message);
        }
        retentionPolicy = new SizeBasedRetentionPolicy(directory, prefix, size);
      } else if(policy.equalsIgnoreCase("freeDiskSpace"))
      {
        int size = getIntegerAttribute(configEntry,
                                       ATTR_LOGGER_RETENTION_FREE_DISK_SPACE,
                                       MSGID_LOGGER_RETENTION_FREE_DISK_SPACE);
        if(size == -1)
        {
          msgID = MSGID_CONFIG_LOGGER_NO_FREE_DISK_SPACE;
          String message = getMessage(msgID, configEntry.getDN().toString());
          throw new ConfigException(msgID, message);
        }
        // Check if we are running on Java 6. If not report error.
        try
        {
          Method meth = java.io.File.class.getMethod("getFreeSpace",
                                                     new Class[0]);
        } catch(Exception e)
        {
          msgID = MSGID_CONFIG_LOGGER_INVALID_JAVA5_POLICY;
          String message = getMessage(msgID, configEntry.getDN().toString());
          throw new ConfigException(msgID, message);
        }
        retentionPolicy =
          new FreeDiskSpaceRetentionPolicy(directory, prefix, size);
      } else
      {
        // Invalid policy - throw exception
        msgID = MSGID_CONFIG_LOGGER_INVALID_RETENTION_POLICY;
        String message = getMessage(msgID, policy,
                                    configEntry.getDN().toString());
        throw new ConfigException(msgID, message);
      }

      policies.add(retentionPolicy);
    }

    return policies;
  }

  /**
   * Return post rotation actions based on the logger configuration
   * information. The action types are values separated by ",".
   *
   * @param configEntry The configuration entry for the logger.
   *
   * @return  The set of rotation actions contained in the provided
   *          configuration entry.
   *
   * @exception ConfigException If there is an invalid config entry.
   */

  public static ArrayList<ActionType>
                     getPostRotationActions(ConfigEntry configEntry)
         throws ConfigException
  {
    ArrayList<ActionType> actions = new ArrayList<ActionType>();

    StringConfigAttribute rotationActionStub =
        new StringConfigAttribute(ATTR_LOGGER_ROTATION_ACTION,
        getMessage(MSGID_CONFIG_LOGGER_DESCRIPTION_CLASS_NAME),
        true, false, true);

    StringConfigAttribute rotationActionAttr = (StringConfigAttribute)
            configEntry.getConfigAttribute(rotationActionStub);

    if(rotationActionAttr == null)
    {
      return actions;
    }

    String actionStr = rotationActionAttr.activeValue().trim();
    StringTokenizer s = new StringTokenizer(actionStr, ",");
    while(s.hasMoreTokens())
    {
      String aToken = s.nextToken().trim();
      if(aToken.equalsIgnoreCase("GZIPCompress"))
      {
        actions.add(ActionType.GZIP_COMPRESS);
        // PostRotationAction action = new GZIPAction();
      } else if(aToken.equals("ZIPCompress"))
      {
        actions.add(ActionType.ZIP_COMPRESS);
      } else if(aToken.equals("Sign"))
      {
        actions.add(ActionType.SIGN);
      } else if(aToken.equals("Encrypt"))
      {
        actions.add(ActionType.ENCRYPT);
      } else
      {
        int msgID = 0;
        throw new ConfigException(msgID, getMessage(msgID));
      }
    }

    return actions;
  }

  /**
   * Create the SSL certificate alias from the configuration.
   *
   * @param  configEntry  The configuration entry containing the certificate
   *                      configuration alias.
   *
   * @return  The requested configuration alias, or <CODE>null</CODE> if none is
   *          configured.
   */
  public static String getCertificateAlias(ConfigEntry configEntry)
  {
    String sslServerCertNickname = null;
    int msgID = MSGID_LOG_DESCRIPTION_SSL_CERT_NICKNAME;
    StringConfigAttribute certNameStub =
         new StringConfigAttribute(ATTR_SSL_CERT_NICKNAME, getMessage(msgID),
                                   false, false, true);
    try
    {
      StringConfigAttribute certNameAttr =
           (StringConfigAttribute)
           configEntry.getConfigAttribute(certNameStub);
      if (certNameAttr == null)
      {
        sslServerCertNickname = DEFAULT_SSL_CERT_NICKNAME;
      }
      else
      {
        sslServerCertNickname = certNameAttr.activeValue();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCought(DebugLogLevel.ERROR, e);
      }

      return null;
    }
    return sslServerCertNickname;

  }


  /**
   * Convenience method for returning an attribute value that is an
   * integer from the config entry.
   *
   * @param configEntry The configuration entry for the logger.
   * @param attrName    The name of the attribute.
   * @param msgID       The message ID for the description of the configuration
   *                    attribute.
   *
   * @return  The integer value of the requested configuration attribute.
   *
   * @throws ConfigException If there is an invalid config entry.
   */
  public static int getIntegerAttribute(ConfigEntry configEntry,
      String attrName, int msgID) throws ConfigException
  {
    int value = -1;

    IntegerConfigAttribute attrStub =
         new IntegerConfigAttribute(attrName, getMessage(msgID),
                                    true, false, false, true, 1, false, 0, 0);
    IntegerConfigAttribute attrVal =
           (IntegerConfigAttribute) configEntry.getConfigAttribute(attrStub);
    if (attrVal != null)
    {
      value = attrVal.activeIntValue();
    }
    return value;
  }
}

