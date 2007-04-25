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
package org.opends.server.messages;



import java.util.concurrent.ConcurrentHashMap;
import java.util.IllegalFormatException;


/**
 * This class defines the set of structures and methods that should be used when
 * retrieving text strings for use in a localized and/or internationalized
 * environment.  Each message will have a unique message number, and each
 * message will have a default English representation that can be overridden
 * from another list.
 * <BR><BR>
 * Note that there should be a pattern used for the message ID list so that each
 * message ID is unique, and so that the message ID itself may be keyed upon for
 * certain purposes.  The pattern that will be used is based on the hexadecimal
 * representation of the 32-bit value.  The first 12 bits will be used for the
 * category (a maximum of 4096 values), the next four bits will be used for the
 * severity (a maximum of 16 values), and the last 16 bits will be used for the
 * unique ID (a maximum of 65536 values).  The last 16 bits may be further
 * broken up within each category if desired.
 * <BR><BR>
 * The categories that have been defined include:
 * <UL>
 *   <LI>000 -- Core server processing</LI>
 *   <LI>001 -- Server extensions</LI>
 *   <LI>002 -- Connection and protocol handling</LI>
 *   <LI>003 -- Configuration handling</LI>
 *   <LI>004 -- The server loggers</LI>
 *   <LI>005 -- General server utilities</LI>
 *   <LI>006 -- Schema elements</LI>
 *   <LI>007 -- Plugin processing</LI>
 *   <LI>008 -- JE backend processing</LI>
 *   <LI>009 -- Generic backend processing</LI>
 *   <LI>00A -- Directory Server tools</LI>
 *   <LI>00B -- Task processing</LI>
 *   <LI>00C -- Access Control</LI>
 *   <LI>00D -- Administration framework</LI>
 *   <LI>00E -- Synchronization</LI>
 *   <LI>800 through FFE -- Reserved for third-party modules</LI>
 *   <LI>FFF -- User-defined processing</LI>
 * </UL>
 * <BR><BR>
 * The severity levels that have been defined include:
 * <UL>
 *   <LI>0 -- Informational</LI>
 *   <LI>1 -- Mild warning</LI>
 *   <LI>2 -- Severe warning</LI>
 *   <LI>3 -- Mild error</LI>
 *   <LI>4 -- Severe error</LI>
 *   <LI>5 -- Fatal error</LI>
 *   <LI>6 -- Debug</LI>
 *   <LI>7 -- Notice</LI>
 * </UL>
 */
public class MessageHandler
{
  /**
   * The category bitmask that will be used for messages associated with the
   * core server.
   */
  public static final int CATEGORY_MASK_CORE = 0x00000000;



  /**
   * The category bitmask that will be used for messages associated with server
   * extensions (e.g., extended operations, SASL mechanisms, password storage
   * schemes, password validators, etc.).
   */
  public static final int CATEGORY_MASK_EXTENSIONS = 0x00100000;



  /**
   * The category bitmask that will be used for messages associated with
   * connection and protocol handling (e.g., ASN.1 and LDAP).
   */
  public static final int CATEGORY_MASK_PROTOCOL = 0x00200000;



  /**
   * The category bitmask that will be used for messages associated with
   * configuration handling.
   */
  public static final int CATEGORY_MASK_CONFIG = 0x00300000;



  /**
   * The category bitmask that will be used for messages associated with the
   * server loggers.
   */
  public static final int CATEGORY_MASK_LOG = 0x00400000;



  /**
   * The category bitmask that will be used for messages associated with the
   * general server utilities.
   */
  public static final int CATEGORY_MASK_UTIL = 0x00500000;



  /**
   * The category bitmask that will be used for messages associated with the
   * server schema elements.
   */
  public static final int CATEGORY_MASK_SCHEMA = 0x00600000;



  /**
   * The category bitmask that will be used for messages associated with plugin
   * processing.
   */
  public static final int CATEGORY_MASK_PLUGIN = 0x00700000;



  /**
   * The category bitmask used for messages associated with the JE backend.
   */
  public static final int CATEGORY_MASK_JEB = 0x00800000;



  /**
   * The category bitmask used for messages associated with generic backends.
   */
  public static final int CATEGORY_MASK_BACKEND = 0x00900000;



  /**
   * The category bitmask used for messages associated with tools.
   */
  public static final int CATEGORY_MASK_TOOLS = 0x00A00000;



  /**
   * The category bitmask used for messages associated with tasks.
   */
  public static final int CATEGORY_MASK_TASK = 0x00B00000;


  /**
   * The category bitmask used for messages associated with Access Control.
   */
  public static final int CATEGORY_MASK_ACCESS_CONTROL = 0x00C00000;


  /**
   * The category bitmask used for messages associated with the
   * administration framework.
   */
  public static final int CATEGORY_MASK_ADMIN = 0x00D00000;


  /**
   * The category bitmask used for messages associated with the Synchronization.
   */
  public static final int CATEGORY_MASK_SYNC = 0x0E000000;



  /**
   * The category bitmask that will be used for messages associated with
   * third-party (including user-defined) modules.
   */
  public static final int CATEGORY_MASK_THIRD_PARTY = 0x80000000;



  /**
   * The category bitmask that will be used for messages associated with
   * user-defined modules.
   */
  public static final int CATEGORY_MASK_USER_DEFINED = 0xFFF00000;



  /**
   * The severity bitmask that will be used for informational messages.
   */
  public static final int SEVERITY_MASK_INFORMATIONAL = 0x00000000;



  /**
   * The severity bitmask that will be used for mild warning messages.
   */
  public static final int SEVERITY_MASK_MILD_WARNING = 0x00010000;



  /**
   * The severity bitmask that will be used for severe warning messages.
   */
  public static final int SEVERITY_MASK_SEVERE_WARNING = 0x00020000;



  /**
   * The severity bitmask that will be used for mild error messages.
   */
  public static final int SEVERITY_MASK_MILD_ERROR = 0x00030000;



  /**
   * The severity bitmask that will be used for severe error messages.
   */
  public static final int SEVERITY_MASK_SEVERE_ERROR = 0x00040000;



  /**
   * The severity bitmask that will be used for fatal error messages.
   */
  public static final int SEVERITY_MASK_FATAL_ERROR = 0x00050000;



  /**
   * The severity bitmask that will be used for debug messages.
   */
  public static final int SEVERITY_MASK_DEBUG = 0x00060000;



  /**
   * The severity bitmask that will be used for important informational
   * messages.
   */
  public static final int SEVERITY_MASK_NOTICE = 0x00070000;



  // The set of messages that have been registered with this message handler.
  private static ConcurrentHashMap<Integer,String> messageMap =
                      new ConcurrentHashMap<Integer,String>();



  // Performs the appropriate initialization for this message handler.  Whenever
  // a new message file is defined, then it must be placed here to ensure that
  // the messages get populated properly.
  static
  {
    CoreMessages.registerMessages();
    ExtensionsMessages.registerMessages();
    ProtocolMessages.registerMessages();
    ConfigMessages.registerMessages();
    LoggerMessages.registerMessages();
    UtilityMessages.registerMessages();
    SchemaMessages.registerMessages();
    PluginMessages.registerMessages();
    JebMessages.registerMessages();
    BackendMessages.registerMessages();
    ToolMessages.registerMessages();
    TaskMessages.registerMessages();
    AdminMessages.registerMessages();
    AciMessages.registerMessages();
    ReplicationMessages.registerMessages();
  }



  /**
   * Retrieves the message string associated with the provided message ID.  No
   * formatting or replacements will be made within the message string.  If no
   * message exists with the specified message ID, then a generic message will
   * be returned.
   *
   * @param  messageID  The unique ID assigned to the message to retrieve.
   *
   * @return  The message string associated with the provided message ID.
   */
  public static String getMessage(int messageID)
  {
    String message = messageMap.get(messageID);
    if (message == null)
    {
      message = "Unknown message for message ID " + messageID;
    }

    return message;
  }



  /**
   * Retrieves the message string associated with the provided message ID,
   * treating it as a format string and replacing any tokens with information
   * from the provided argument list.  If no message exists with the specified
   * message ID, then a generic message will be returned.
   *
   * @param  messageID  The unique ID assigned to the message to retrieve.
   * @param  arguments  The set of arguments to use to replace tokens in the
   *                    format string before it is returned.
   *
   * @return  The message string associated with the provided message ID.
   */
  public static String getMessage(int messageID, Object... arguments)
  {
    String formatString = messageMap.get(messageID);
    if (formatString == null)
    {
      StringBuilder buffer = new StringBuilder();
      buffer.append("Unknown message for message ID ");
      buffer.append(messageID);
      buffer.append(" -- provided arguments were:  ");
      if ((arguments != null) && (arguments.length > 0))
      {
        buffer.append(String.valueOf(arguments[0]));

        for (int i=1; i < arguments.length; i++)
        {
          buffer.append(", ");
          buffer.append(String.valueOf(arguments[i]));
        }
      }

      return buffer.toString();
    }

    try
    {
      return String.format(formatString, arguments);
    }
    catch (IllegalFormatException e)
    {
      // Make a more useful message than a stack trace.
      StringBuilder buffer = new StringBuilder();
      buffer.append(formatString);
      buffer.append(" -- mismatched arguments were:  ");
      if ((arguments != null) && (arguments.length > 0))
      {
        buffer.append(String.valueOf(arguments[0]));

        for (int i=1; i < arguments.length; i++)
        {
          buffer.append(", ");
          buffer.append(String.valueOf(arguments[i]));
        }
      }

      return buffer.toString();
    }
  }



  /**
   * Registers the provided message with this message handler.
   *
   * @param  messageID     The unique identifier assigned to this message.
   * @param  formatString  The format string to use for this message.
   */
  static void registerMessage(int messageID, String formatString)
  {
    messageMap.put(messageID, formatString);
  }



  /**
   * Retrieves the entire set of messages defined in the server, mapped between
   * their message ID and the format string for that message.  The resulting
   * mapping must not be modified.
   *
   * @return  The entire set of messages defined in the server.
   */
  public static ConcurrentHashMap<Integer,String> getMessages()
  {
    return messageMap;
  }
}

