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



import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.StringTokenizer;

import org.opends.server.api.DebugLogger;
import org.opends.server.api.ProtocolElement;
import org.opends.server.config.ConfigEntry;
import org.opends.server.types.DebugLogCategory;
import org.opends.server.types.DebugLogSeverity;
import org.opends.server.util.TimeThread;

import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a Directory Server debug logger that will be used only
 * during startup.  It makes it possible for informational, warning, and error
 * messages to be generated during startup and made available to the user for
 * things that happen before the configuration can be parsed and all of the
 * actual debug loggers instantiated and registered.  This debug logger will be
 * taken out of service once the startup is complete.
 * <BR><BR>
 * By default, errors and warnings will be logged.  This can be modified on a
 * per-category basis using a "DS_DEBUG_LEVEL" environment variable that should
 * be a semicolon-delimited list in which each element in that list should
 * contain the name of the category, an equal sign, and a comma-delimited list
 * of the severity levels to use for that category.
 */
public class StartupDebugLogger
       extends DebugLogger
{
  /**
   * The name of the environment variable that may be used to alter the kinds of
   * messages that get logged with this startup debug logger.
   */
  public static final String ENV_VARIABLE_DEBUG_LOG_LEVEL = "DS_DEBUG_LEVEL";



  // The hash map that will be used to define specific log severities for the
  // various categories.
  private HashMap<DebugLogCategory,HashSet<DebugLogSeverity>> definedSeverities;

  // The set of default log severities that will be used if no custom severities
  // have been defined for the associated category.
  private HashSet<DebugLogSeverity> defaultSeverities;

  // The writer that will be used to actually write the messages.
  private PrintWriter writer;



  /**
   * Creates a new instance of this startup debug logger.  It does not actually
   * do anything, since all initialization is performed in the
   * <CODE>initializeDebugLogger</CODE> method.
   */
  public StartupDebugLogger()
  {
    super();
  }



  /**
   * Initializes this debug logger based on the information in the provided
   * configuration entry.
   *
   * @param  configEntry  The configuration entry that contains the information
   *                      to use to initialize this debug logger.
   */
  public void initializeDebugLogger(ConfigEntry configEntry)
  {
    writer = new PrintWriter(System.err, true);

    defaultSeverities = new HashSet<DebugLogSeverity>();
    defaultSeverities.add(DebugLogSeverity.ERROR);
    defaultSeverities.add(DebugLogSeverity.WARNING);

    definedSeverities =
         new HashMap<DebugLogCategory,HashSet<DebugLogSeverity>>();


    String logLevelInfo = System.getenv(ENV_VARIABLE_DEBUG_LOG_LEVEL);
    if (logLevelInfo != null)
    {
      StringTokenizer tokenizer = new StringTokenizer(logLevelInfo, ";");
      while (tokenizer.hasMoreTokens())
      {
        String token = tokenizer.nextToken();
        int equalPos = token.indexOf('=');
        if (equalPos < 0)
        {
          writer.println("StartupDebugLogger:  Token \"" + token +
                         "\" read from environment variable " +
                         ENV_VARIABLE_DEBUG_LOG_LEVEL + " does not contain " +
                         "an equal sign to separate the category from the " +
                         "severity list.  It will be ignored");
          continue;
        }

        String categoryName = token.substring(0, equalPos);
        DebugLogCategory category = DebugLogCategory.getByName(categoryName);
        if (category == null)
        {
          writer.println("StartupDebugLogger:  Unknown debug log category \"" +
                         categoryName + "\" read from environment variable " +
                         ENV_VARIABLE_DEBUG_LOG_LEVEL + " will be ignored.");
          continue;
        }

        HashSet<DebugLogSeverity> severities = new HashSet<DebugLogSeverity>();
        StringTokenizer sevTokenizer =
             new StringTokenizer(token.substring(equalPos+1), ",");
        while (sevTokenizer.hasMoreElements())
        {
          String severityName = sevTokenizer.nextToken();
          DebugLogSeverity severity = DebugLogSeverity.getByName(severityName);
          if (severity == null)
          {
            writer.println("StartupDebugLogger:  Unknown debug log severity " +
                           "\"" + severityName + "\" read from environment " +
                           "variable " + ENV_VARIABLE_DEBUG_LOG_LEVEL +
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
   * Closes this debug logger and releases any resources it might have held.
   */
  public void closeDebugLogger()
  {
    // No action is required, and this logger will remain usable.
  }



  /**
   * Writes a message to the debug logger indicating that the specified raw data
   * was read.
   *
   * @param  className   The fully-qualified name of the Java class in which the
   *                     data was read.
   * @param  methodName  The name of the method in which the data was read.
   * @param  buffer      The byte buffer containing the data that has been read.
   *                     The byte buffer must be in the same state when this
   *                     method returns as when it was entered.
   */
  public void debugBytesRead(String className, String methodName,
                             ByteBuffer buffer)
  {
    HashSet<DebugLogSeverity> severities =
         definedSeverities.get(DebugLogCategory.DATA_READ);
    if (severities == null)
    {
      severities = defaultSeverities;
    }

    if (severities.contains(DebugLogSeverity.COMMUNICATION))
    {
      StringBuilder msg = new StringBuilder();

      msg.append('[');
      msg.append(TimeThread.getLocalTime());
      msg.append("] category=" + DEBUG_CATEGORY_DATA_READ + ", data:" + EOL);

      byteArrayToHexPlusAscii(msg, buffer, 5);

      writer.println(msg.toString());
    }
  }



  /**
   * Writes a message to the debug logger indicating that the specified raw data
   * was written.
   *
   * @param  className   The fully-qualified name of the Java class in which the
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
      StringBuilder msg = new StringBuilder();

      msg.append('[');
      msg.append(TimeThread.getLocalTime());
      msg.append("] category=" + DEBUG_CATEGORY_DATA_WRITE + ", data:" + EOL);

      byteArrayToHexPlusAscii(msg, buffer, 5);

      writer.println(msg.toString());
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
      StringBuilder msg = new StringBuilder();

      msg.append('[');
      msg.append(TimeThread.getLocalTime());
      msg.append("] category=" + DEBUG_CATEGORY_CONSTRUCTOR + " - ");
      msg.append(className);
      msg.append("(");

      switch (args.length)
      {
        case 0:
          // No action required;
          break;
        case 1:
          msg.append(args[0]);
          break;
        default:
          msg.append(args[0]);

          for (int i=1; i < args.length; i++)
          {
            msg.append(",");
            msg.append(args[i]);
          }
          break;
      }

      msg.append(")");

      writer.println(msg.toString());
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
      StringBuilder msg = new StringBuilder();

      msg.append('[');
      msg.append(TimeThread.getLocalTime());
      msg.append("] category=" + DEBUG_CATEGORY_ENTER + " - ");
      msg.append(className);
      msg.append(".");
      msg.append(methodName);
      msg.append("(");

      switch (args.length)
      {
        case 0:
          // No action required;
          break;
        case 1:
          msg.append(args[0]);
          break;
        default:
          msg.append(args[0]);

          for (int i=1; i < args.length; i++)
          {
            msg.append(",");
            msg.append(args[i]);
          }
          break;
      }

      msg.append(")");

      writer.println(msg.toString());
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
  public void debugMessage(DebugLogCategory category, DebugLogSeverity severity,
                           String className, String methodName, String message)
  {
    HashSet<DebugLogSeverity> severities = definedSeverities.get(category);
    if (severities == null)
    {
      severities = defaultSeverities;
    }

    if (severities.contains(severity))
    {
      StringBuilder msg = new StringBuilder();

      msg.append('[');
      msg.append(TimeThread.getLocalTime());
      msg.append("] category=");
      msg.append(category.getCategoryName());
      msg.append(", severity=");
      msg.append(severity.getSeverityName());
      msg.append(", class=");
      msg.append(className);
      msg.append(", method=");
      msg.append(methodName);
      msg.append(", msg=\"");
      msg.append(message);
      msg.append("\"");

      writer.println(msg);
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
      StringBuilder msg = new StringBuilder();

      msg.append('[');
      msg.append(TimeThread.getLocalTime());
      msg.append("] category=" + DEBUG_CATEGORY_EXCEPTION + ", class=");
      msg.append(className);
      msg.append(", method=");
      msg.append(methodName);
      msg.append(", trace:" + EOL);

      stackTraceToString(msg, exception);

      writer.println(msg);
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
      StringBuilder msg = new StringBuilder();

      msg.append('[');
      msg.append(TimeThread.getLocalTime());
      msg.append("] category=" + DEBUG_CATEGORY_PROTOCOL_READ +
                 ", element:" + EOL);

      protocolElement.toString(msg, 5);

      writer.println(msg.toString());
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
      StringBuilder msg = new StringBuilder();

      msg.append('[');
      msg.append(TimeThread.getLocalTime());
      msg.append("] category=" + DEBUG_CATEGORY_PROTOCOL_WRITE+
                 ", element:" + EOL);

      protocolElement.toString(msg, 5);

      writer.println(msg.toString());
    }
  }



  /**
   * Indicates whether the provided object is equal to this debug logger.
   *
   * @param  o  The object for which to make the determination.
   *
   * @return  <CODE>true</CODE> if the provided object is determined to be equal
   *          to this debug logger, or <CODE>false</CODE> if not.
   */
  public boolean equals(Object o)
  {
    if (this == o)
    {
      return true;
    }

    if ((o == null) || (! (o instanceof StartupDebugLogger)))
    {
      return false;
    }

    return true;
  }



  /**
   * Retrieves the hash code for this debug logger.
   *
   * @return  The hash code for this debug logger.
   */
  public int hashCode()
  {
    // Just make one up, since there should never be a need to have more than
    // one instance of this debug logger.
    return 12345;
  }
}

