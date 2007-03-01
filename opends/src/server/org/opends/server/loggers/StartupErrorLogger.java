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



import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.StringTokenizer;

import org.opends.server.api.ErrorLogger;
import org.opends.server.config.ConfigEntry;
import org.opends.server.types.ErrorLogCategory;
import org.opends.server.types.ErrorLogSeverity;
import org.opends.server.util.TimeThread;



/**
 * This class defines a Directory Server error logger that will be used only
 * during startup.  It makes it possible for informational, warning, and error
 * messages to be generated during startup and made available to the user for
 * things that happen before the configuration can be parsed and all of the
 * actual error loggers instantiated and registered.  This error logger will be
 * taken out of service once the startup is complete.
 * <BR><BR>
 * By default, fatal errors, severe errors, severe warnings, and important
 * information will be logged.  This can be modified on a per-category basis
 * using a "DS_ERROR_LEVEL" environment variable that should be a
 * semicolon-delimited list in which each element in that list should contain
 * the name of the category, an equal sign, and a comma-delimited list of the
 * severity levels to use for that category.
 */
public class StartupErrorLogger
       extends ErrorLogger
{
  /**
   * The name of the environment variable that may be used to alter the kinds of
   * messages that get logged with this startup error logger.
   */
  public static final String ENV_VARIABLE_ERROR_LOG_LEVEL = "DS_ERROR_LEVEL";



  // The hash map that will be used to define specific log severities for the
  // various categories.
  private HashMap<ErrorLogCategory,HashSet<ErrorLogSeverity>> definedSeverities;

  // The set of default log severities that will be used if no custom severities
  // have been defined for the associated category.
  private HashSet<ErrorLogSeverity> defaultSeverities;

  // The writer that will be used to actually write the messages.
  private PrintWriter writer;



  /**
   * Creates a new instance of this startup error logger.  It does not actually
   * do anything, since all initialization is performed in the
   * <CODE>initializeErrorLogger</CODE> method.
   */
  public StartupErrorLogger()
  {
    super();
  }



  /**
   * Initializes this error logger based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this error logger.
   */
  public void initializeErrorLogger(ConfigEntry configEntry)
  {
    writer = new PrintWriter(System.err, true);

    defaultSeverities = new HashSet<ErrorLogSeverity>();
    defaultSeverities.add(ErrorLogSeverity.FATAL_ERROR);
    defaultSeverities.add(ErrorLogSeverity.SEVERE_ERROR);
    defaultSeverities.add(ErrorLogSeverity.SEVERE_WARNING);
    defaultSeverities.add(ErrorLogSeverity.NOTICE);

    definedSeverities =
         new HashMap<ErrorLogCategory,HashSet<ErrorLogSeverity>>();


    String logLevelInfo = System.getenv(ENV_VARIABLE_ERROR_LOG_LEVEL);
    if (logLevelInfo != null)
    {
      StringTokenizer tokenizer = new StringTokenizer(logLevelInfo, ";");
      while (tokenizer.hasMoreTokens())
      {
        String token = tokenizer.nextToken();
        int equalPos = token.indexOf('=');
        if (equalPos < 0)
        {
          writer.println("StartupErrorLogger:  Token \"" + token +
                         "\" read from environment variable " +
                         ENV_VARIABLE_ERROR_LOG_LEVEL + " does not contain " +
                         "an equal sign to separate the category from the " +
                         "severity list.  It will be ignored");
          continue;
        }

        String categoryName = token.substring(0, equalPos);
        ErrorLogCategory category = ErrorLogCategory.getByName(categoryName);
        if (category == null)
        {
          writer.println("StartupErrorLogger:  Unknown error log category \"" +
                         categoryName + "\" read from environment variable " +
                         ENV_VARIABLE_ERROR_LOG_LEVEL + " will be ignored.");
          continue;
        }

        HashSet<ErrorLogSeverity> severities = new HashSet<ErrorLogSeverity>();
        StringTokenizer sevTokenizer =
             new StringTokenizer(token.substring(equalPos+1), ",");
        while (sevTokenizer.hasMoreElements())
        {
          String severityName = sevTokenizer.nextToken();
          ErrorLogSeverity severity = ErrorLogSeverity.getByName(severityName);
          if (severity == null)
          {
            writer.println("StartupErrorLogger:  Unknown error log severity " +
                           "\"" + severityName + "\" read from environment " +
                           "variable " + ENV_VARIABLE_ERROR_LOG_LEVEL +
                           " will be ignored.");
            continue;
          }
          else
          {
            severities.add(severity);
          }
        }

        definedSeverities.put(category, severities);
      }
    }
  }



  /**
   * Closes this error logger and releases any resources it might have held.
   */
  public void closeErrorLogger()
  {
    // No action is required, and this logger will remain usable.
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
  public void logError(ErrorLogCategory category, ErrorLogSeverity severity,
                       String message, int errorID)
  {
    HashSet<ErrorLogSeverity> severities = definedSeverities.get(category);
    if (severities == null)
    {
      severities = defaultSeverities;
    }

    if (severities.contains(severity))
    {
      StringBuilder logMsg = new StringBuilder();

      logMsg.append('[');
      logMsg.append(TimeThread.getLocalTime());
      logMsg.append("] category=");
      logMsg.append(category.getCategoryName());
      logMsg.append(" severity=");
      logMsg.append(severity.getSeverityName());
      logMsg.append(" id=");
      logMsg.append(errorID);
      logMsg.append(" msg=\"");
      logMsg.append(message);
      logMsg.append("\"");

      writer.println(logMsg.toString());
    }
  }



  /**
   * Indicates whether the provided object is equal to this error logger.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided object is determined to be equal
   *          to this error logger, or <CODE>false</CODE> if not.
   */
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }

    if ((o == null) || (! (o instanceof StartupErrorLogger)))
    {
      return false;
    }

    return true;
  }



  /**
   * Retrieves the hash code for this error logger.
   *
   * @return  The hash code for this error logger.
   */
  public int hashCode()
  {
    // Just make one up, since there should never be a need to have more than
    // one instance of this error logger.
    return 12345;
  }
}

