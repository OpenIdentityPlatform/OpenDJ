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
package org.opends.server.messages;



import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.messages.MessageHandler.*;



/**
 * This class defines the set of message IDs and default format strings for
 * messages associated with Directory Server plugins that are provided with the
 * server itself.  Messages for third-party plugins should not be defined in
 * this class.
 */
public class PluginMessages
{
  /**
   * The message ID for the message that will be used if an attempt is made to
   * initialize a plugin with a null configuration entry.  This takes a single
   * argument, which is the class name of the plugin that could not be
   * initialized.
   */
  public static final int MSGID_PLUGIN_NULL_CONFIG_ENTRY =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 1;



  /**
   * The message ID for the message that will be used to retrieve the
   * description of the shutdown password configuration attribute.  This does
   * not take any arguments.
   */
  public static final int MSGID_PLUGIN_DESCRIPTION_SHUTDOWN_PASSWORD =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_INFORMATIONAL | 2;



  /**
   * The message ID for the message that will be used if the LDAP ADList plugin
   * is not configured with any plugin types.  This takes a single argument,
   * which is the DN of the configuration entry.
   */
  public static final int MSGID_PLUGIN_ADLIST_NO_PLUGIN_TYPES =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 3;



  /**
   * The message ID for the message that will be used if the LDAP ADList plugin
   * is configured with an invalid plugin type.  This takes two arguments,
   * which are the DN of the configuration entry, and the invalid plugin type.
   */
  public static final int MSGID_PLUGIN_ADLIST_INVALID_PLUGIN_TYPE =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 4;



  /**
   * The message ID for the message that will be used if the Directory Server
   * profiler plugin is not configured with any plugin types.  This takes a
   * single argument, which is the DN of the configuration entry.
   */
  public static final int MSGID_PLUGIN_PROFILER_NO_PLUGIN_TYPES =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 5;



  /**
   * The message ID for the message that will be used if the Directory Server
   * profiler plugin is configured with an invalid plugin type.  This takes two
   * arguments, which are the DN of the configuration entry, and the invalid
   * plugin type.
   */
  public static final int MSGID_PLUGIN_PROFILER_INVALID_PLUGIN_TYPE =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 6;



  /**
   * The message ID for the message that will be used as the description for
   * the profile directory configuration attribute.  This does not take any
   * arguments.
   */
  public static final int MSGID_PLUGIN_PROFILER_DESCRIPTION_PROFILE_DIR =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_INFORMATIONAL | 7;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the path to the profile directory.  This takes three
   * arguments, which are the DN of the configuration entry, a string
   * representation of the exception that was caught, and the path to the
   * default profile directory that will be used.
   */
  public static final int MSGID_PLUGIN_PROFILER_CANNOT_DETERMINE_PROFILE_DIR =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_WARNING | 8;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to write the profiled capture data.  This takes two arguments, which
   * are the path to the file that was to be written and a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_PLUGIN_PROFILER_CANNOT_WRITE_PROFILE_DATA =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 9;



  /**
   * The message ID for the message that will be used as the description for
   * the profile autostart attribute.  This does not take any arguments.
   */
  public static final int MSGID_PLUGIN_PROFILER_DESCRIPTION_AUTOSTART =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_INFORMATIONAL | 10;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine whether to automatically start profiling.  This takes
   * two arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_PLUGIN_PROFILER_CANNOT_DETERMINE_AUTOSTART =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_WARNING | 11;



  /**
   * The message ID for the message that will be used as the description for
   * the profile sample interval attribute.  This does not take any arguments.
   */
  public static final int MSGID_PLUGIN_PROFILER_DESCRIPTION_INTERVAL =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_INFORMATIONAL | 12;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the profiler sample interval.  This takes three
   * arguments, which are the DN of the configuration entry, and a string
   * representation of the exception that was caught, and the default sample
   * interval that will be used.
   */
  public static final int MSGID_PLUGIN_PROFILER_CANNOT_DETERMINE_INTERVAL =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_WARNING | 13;



  /**
   * The message ID for the message that will be used as the description for
   * the profiler state attribute.  This does not take any arguments.
   */
  public static final int MSGID_PLUGIN_PROFILER_DESCRIPTION_STATE =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_INFORMATIONAL | 14;



  /**
   * The message ID for the message that will be used as the description for
   * the profiler action attribute.  This does not take any arguments.
   */
  public static final int MSGID_PLUGIN_PROFILER_DESCRIPTION_ACTION =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_INFORMATIONAL | 15;



  /**
   * The message ID for the message that will be used if an invalid profile
   * directory is specified.  This takes two arguments, which are the specified
   * profile directory and the DN of the configuration entry.
   */
  public static final int MSGID_PLUGIN_PROFILER_INVALID_PROFILE_DIR =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_WARNING | 16;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to determine the profiler action that should be taken.  This takes
   * two arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_PLUGIN_PROFILER_CANNOT_DETERMINE_ACTION =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_WARNING | 17;



  /**
   * The message ID for the message that will be used if the profiler sample
   * interval is changed.  This takes two arguments, which are the DN of the
   * configuration entry and the new sample interval that has been specified.
   */
  public static final int MSGID_PLUGIN_PROFILER_UPDATED_INTERVAL =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_INFORMATIONAL | 18;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to update the profiler sample interval.  This takes two
   * arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_PLUGIN_PROFILER_CANNOT_UPDATE_INTERVAL =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 19;



  /**
   * The message ID for the message that will be used if the profiler capture
   * directory is changed.  This takes two arguments, which are the DN of the
   * configuration entry and the new profile directory that has been specified.
   */
  public static final int MSGID_PLUGIN_PROFILER_UPDATED_DIRECTORY =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_INFORMATIONAL | 20;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to update the profiler capture directory.  This takes two
   * arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_PLUGIN_PROFILER_CANNOT_UPDATE_DIRECTORY =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 21;



  /**
   * The message ID for the message that will be used when the profiler starts
   * to capture information.  This takes a single argument, which is the DN of
   * the configuration entry.
   */
  public static final int MSGID_PLUGIN_PROFILER_STARTED_PROFILING =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_INFORMATIONAL | 22;



  /**
   * The message ID for the message that will be used if the profiler is
   * requested to start but is already active.  This takes a single argument,
   * which is the DN of the configuration entry.
   */
  public static final int MSGID_PLUGIN_PROFILER_ALREADY_PROFILING =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_INFORMATIONAL | 23;



  /**
   * The message ID for the message that will be used if the profiler is
   * requested to stop but is not active.  This takes a single argument, which
   * is the DN of the configuration entry.
   */
  public static final int MSGID_PLUGIN_PROFILER_NOT_RUNNING =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_INFORMATIONAL | 24;



  /**
   * The message ID for the message that will be used when the profiler is
   * stopped.  This takes a single argument, which is the DN of the
   * configuration entry.
   */
  public static final int MSGID_PLUGIN_PROFILER_STOPPED_PROFILING =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_INFORMATIONAL | 25;



  /**
   * The message ID for the message that will be used when the profiler thread
   * writes the data it has captured to a file.  This takes two arguments, which
   * are the DN of the configuration entry and the file to which the information
   * has been written.
   */
  public static final int MSGID_PLUGIN_PROFILER_WROTE_PROFILE_DATA =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_INFORMATIONAL | 26;



  /**
   * The message ID for the message that will be used if the profiler is
   * requested to perform an unrecognized action.  This takes two arguments,
   * which are the DN of the configuration entry and the requested action.
   */
  public static final int MSGID_PLUGIN_PROFILER_UNKNOWN_ACTION =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_MILD_ERROR | 27;



  /**
   * The message ID for the message that will be used if the profiler is
   * going to skip processing on the requested action because an earlier problem
   * was encountered while processing changes to the configuration.  This takes
   * two arguments, which are the DN of the configuration entry and the
   * requested action.
   */
  public static final int MSGID_PLUGIN_PROFILER_SKIPPING_ACTION =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_INFORMATIONAL | 28;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to process the requested profiler action.  This takes two
   * arguments, which are the DN of the configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_PLUGIN_PROFILER_CANNOT_PERFORM_ACTION =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 29;



  /**
   * The message ID for the message that will be used if an exception is thrown
   * by one of the Directory Server startup plugins.  This takes two arguments,
   * which are the DN of the plugin configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_PLUGIN_STARTUP_PLUGIN_EXCEPTION =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_FATAL_ERROR | 30;



  /**
   * The message ID for the message that will be used if a startup plugin
   * returns <CODE>null</CODE> rather than a valid result.  This takes a single
   * argument, which is the DN of the plugin configuration entry.
   */
  public static final int MSGID_PLUGIN_STARTUP_PLUGIN_RETURNED_NULL =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_FATAL_ERROR | 31;



  /**
   * The message ID for the message that will be used if a startup plugin
   * indicates that it failed but that the server startup may continue.  This
   * takes three arguments, which are the DN of the plugin configuration entry,
   * the error message generated by the plugin, and the unique ID for that error
   * message.
   */
  public static final int MSGID_PLUGIN_STARTUP_PLUGIN_FAIL_CONTINUE =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 32;



  /**
   * The message ID for the message that will be used if a startup plugin
   * indicates that it failed and that the server should abort its startup.
   * This takes three arguments, which are the DN of the plugin configuration
   * entry, the error message generated by the plugin, and the unique ID for
   * that error message.
   */
  public static final int MSGID_PLUGIN_STARTUP_PLUGIN_FAIL_ABORT =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_FATAL_ERROR | 33;



  /**
   * The message ID for the message that will be used if an exception is thrown
   * by one of the Directory Server shutdown plugins.  This takes two arguments,
   * which are the DN of the plugin configuration entry and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_PLUGIN_SHUTDOWN_PLUGIN_EXCEPTION =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 34;



  /**
   * The message ID for the message that will be used if an exception is thrown
   * by one of the Directory Server post-connect plugins.  This takes four
   * arguments, which are the DN of the plugin configuration entry, the
   * connection ID for the client connection, the address of the client, and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_PLUGIN_POST_CONNECT_PLUGIN_EXCEPTION =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 35;



  /**
   * The message ID for the message that will be used if one of the Directory
   * Server post-connect plugins returns null rather than a valid result.  This
   * takes three arguments, which are the DN of the plugin configuration entry,
   * the connection ID for the client connection, and the address of that
   * client.
   */
  public static final int MSGID_PLUGIN_POST_CONNECT_PLUGIN_RETURNED_NULL =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 36;



  /**
   * The message ID for the message that will be used if an exception is thrown
   * by one of the Directory Server post-disconnect plugins.  This takes four
   * arguments, which are the DN of the plugin configuration entry, the
   * connection ID for the client connection, the address of the client, and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_PLUGIN_POST_DISCONNECT_PLUGIN_EXCEPTION =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 37;



  /**
   * The message ID for the message that will be used if one of the Directory
   * Server post-disconnect plugins returns null rather than a valid result.
   * This takes three arguments, which are the DN of the plugin configuration
   * entry, the connection ID for the client connection, and the address of that
   * client.
   */
  public static final int MSGID_PLUGIN_POST_DISCONNECT_PLUGIN_RETURNED_NULL =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 38;



  /**
   * The message ID for the message that will be used if an exception is thrown
   * by one of the Directory Server pre-parse plugins.  This takes five
   * arguments, which are the name of the associated operation type, the
   * DN of the plugin configuration entry, the connection ID for the client,
   * the operation ID for the operation, and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_PLUGIN_PRE_PARSE_PLUGIN_EXCEPTION =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 39;



  /**
   * The message ID for the message that will be used if one of the Directory
   * Server pre-parse plugins returns null rather than a valid result.  This
   * takes four arguments, which are the name of the associated operation type,
   * the DN of the plugin configuration entry, the connection ID for the client
   * connection, and the operation ID for the operation.
   */
  public static final int MSGID_PLUGIN_PRE_PARSE_PLUGIN_RETURNED_NULL =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 40;



  /**
   * The message ID for the message that will be used if an exception is thrown
   * by one of the Directory Server pre-operation plugins.  This takes five
   * arguments, which are the name of the associated operation type, the
   * DN of the plugin configuration entry, the connection ID for the client,
   * the operation ID for the operation, and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_PLUGIN_PRE_OPERATION_PLUGIN_EXCEPTION =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 41;



  /**
   * The message ID for the message that will be used if one of the Directory
   * Server pre-operation plugins returns null rather than a valid result.  This
   * takes four arguments, which are the name of the associated operation type,
   * the DN of the plugin configuration entry, the connection ID for the client
   * connection, and the operation ID for the operation.
   */
  public static final int MSGID_PLUGIN_PRE_OPERATION_PLUGIN_RETURNED_NULL =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 42;



  /**
   * The message ID for the message that will be used if an exception is thrown
   * by one of the Directory Server post-operation plugins.  This takes five
   * arguments, which are the name of the associated operation type, the
   * DN of the plugin configuration entry, the connection ID for the client,
   * the operation ID for the operation, and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_PLUGIN_POST_OPERATION_PLUGIN_EXCEPTION =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 43;



  /**
   * The message ID for the message that will be used if one of the Directory
   * Server post-operation plugins returns null rather than a valid result.
   * This takes four arguments, which are the name of the associated operation
   * type, the DN of the plugin configuration entry, the connection ID for the
   * client connection, and the operation ID for the operation.
   */
  public static final int MSGID_PLUGIN_POST_OPERATION_PLUGIN_RETURNED_NULL =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 44;



  /**
   * The message ID for the message that will be used if an exception is thrown
   * by one of the Directory Server post-response plugins.  This takes five
   * arguments, which are the name of the associated operation type, the
   * DN of the plugin configuration entry, the connection ID for the client,
   * the operation ID for the operation, and a string representation of the
   * exception that was caught.
   */
  public static final int MSGID_PLUGIN_POST_RESPONSE_PLUGIN_EXCEPTION =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 45;



  /**
   * The message ID for the message that will be used if one of the Directory
   * Server post-response plugins returns null rather than a valid result.
   * This takes four arguments, which are the name of the associated operation
   * type, the DN of the plugin configuration entry, the connection ID for the
   * client connection, and the operation ID for the operation.
   */
  public static final int MSGID_PLUGIN_POST_RESPONSE_PLUGIN_RETURNED_NULL =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 46;



  /**
   * The message ID for the message that will be used if an exception is thrown
   * by one of the Directory Server search result entry plugins.  This takes
   * five arguments, which are the DN of the plugin configuration entry,
   * the connection ID for the client, the operation ID for the search
   * operation, the DN of the search result entry, and a string representation
   * of the exception that was caught.
   */
  public static final int MSGID_PLUGIN_SEARCH_ENTRY_PLUGIN_EXCEPTION =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 47;



  /**
   * The message ID for the message that will be used if one of the Directory
   * Server search result entry plugins returns null rather than a valid result.
   * This takes four arguments, which are the DN of the plugin configuration
   * entry, the connection ID for the client connection, the operation ID for
   * the search operation, and the DN of the search result entry.
   */
  public static final int MSGID_PLUGIN_SEARCH_ENTRY_PLUGIN_RETURNED_NULL =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 48;



  /**
   * The message ID for the message that will be used if an exception is thrown
   * by one of the Directory Server search result reference plugins.  This takes
   * five arguments, which are the DN of the plugin configuration entry,
   * the connection ID for the client, the operation ID for the search
   * operation, a string representation of the referral URLs, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_PLUGIN_SEARCH_REFERENCE_PLUGIN_EXCEPTION =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 49;



  /**
   * The message ID for the message that will be used if one of the Directory
   * Server search result reference plugins returns null rather than a valid
   * result.  This takes four arguments, which are the DN of the plugin
   * configuration  entry, the connection ID for the client connection, the
   * operation ID for the search operation, and a string representation of the
   * referral URLs.
   */
  public static final int MSGID_PLUGIN_SEARCH_REFERENCE_PLUGIN_RETURNED_NULL =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 50;



  /**
   * The message ID for the message that will be used if the lastmod plugin is
   * configured with one or more invalid types.  This takes a single argument,
   * which is the invalid type that was used.
   */
  public static final int MSGID_PLUGIN_LASTMOD_INVALID_PLUGIN_TYPE =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 51;



  /**
   * The message ID for the message that will be used for the description of the
   * "filename" argument to the profile viewer.  It does not take any arguments.
   */
  public static final int MSGID_PROFILEVIEWER_DESCRIPTION_FILENAMES =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_INFORMATIONAL | 52;



  /**
   * The message ID for the message that will be used for the description of the
   * "useGUI" argument to the profile viewer.  It does not take any arguments.
   */
  public static final int MSGID_PROFILEVIEWER_DESCRIPTION_USE_GUI =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_INFORMATIONAL | 53;



  /**
   * The message ID for the message that will be used for the description of the
   * "displayUsage" argument to the profile viewer.  It does not take any
   * arguments.
   */
  public static final int MSGID_PROFILEVIEWER_DESCRIPTION_USAGE =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_INFORMATIONAL | 54;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to initialize the command-line arguments.  This takes a single
   * argument, which is a string representation of the exception that was
   * caught.
   */
  public static final int MSGID_PROFILEVIEWER_CANNOT_INITIALIZE_ARGS =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 55;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse the command-line arguments.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_PROFILEVIEWER_ERROR_PARSING_ARGS =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 56;



  /**
   * The message ID for the message that will be used if an error occurs while
   * trying to parse the command-line arguments.  This takes a single argument,
   * which is a string representation of the exception that was caught.
   */
  public static final int MSGID_PROFILEVIEWER_CANNOT_PROCESS_DATA_FILE =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 57;



  /**
   * The message ID for the message that will be used if an exception is thrown
   * by one of the Directory Server LDIF import plugins.  This takes three
   * arguments, which are the DN of the plugin configuration entry, the DN
   * of the entry being imported, and a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_PLUGIN_LDIF_IMPORT_PLUGIN_EXCEPTION =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 58;



  /**
   * The message ID for the message that will be used if one of the Directory
   * Server LDIF import plugins returns null rather than a valid result.  This
   * This takes two arguments, which are the DN of the plugin configuration
   * entry and the DN of the entry being imported.
   */
  public static final int MSGID_PLUGIN_LDIF_IMPORT_PLUGIN_RETURNED_NULL =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 59;



  /**
   * The message ID for the message that will be used if an exception is thrown
   * by one of the Directory Server LDIF export plugins.  This takes three
   * arguments, which are the DN of the plugin configuration entry, the DN
   * of the entry being exported, and a string representation of the exception
   * that was caught.
   */
  public static final int MSGID_PLUGIN_LDIF_EXPORT_PLUGIN_EXCEPTION =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 60;



  /**
   * The message ID for the message that will be used if one of the Directory
   * Server LDIF export plugins returns null rather than a valid result.  This
   * This takes two arguments, which are the DN of the plugin configuration
   * entry and the DN of the entry being exported.
   */
  public static final int MSGID_PLUGIN_LDIF_EXPORT_PLUGIN_RETURNED_NULL =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 61;



  /**
   * The message ID for the message that will be used if the entryUUID plugin is
   * configured with one or more invalid types.  This takes a single argument,
   * which is the invalid type that was used.
   */
  public static final int MSGID_PLUGIN_ENTRYUUID_INVALID_PLUGIN_TYPE =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 62;



  /**
   * The message ID for the message that will be used if an exception is thrown
   * by one of the Directory Server intermediate response plugins.  This takes
   * four arguments, which are the DN of the plugin configuration entry,
   * the connection ID for the client, the operation ID for the operation, and a
   * string representation of the exception that was caught.
   */
  public static final int MSGID_PLUGIN_INTERMEDIATE_RESPONSE_PLUGIN_EXCEPTION =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 63;



  /**
   * The message ID for the message that will be used if one of the Directory
   * Server intermediate response plugins returns null rather than a valid
   * result.  This takes four arguments, which are the DN of the plugin
   * configuration  entry, the connection ID for the client connection, and the
   * operation ID for the associated operation.
   */
  public static final int
       MSGID_PLUGIN_INTERMEDIATE_RESPONSE_PLUGIN_RETURNED_NULL =
            CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 64;



  /**
   * The message ID for the message that will be used if the password policy
   * import plugin is configured with one or more invalid types.  This takes a
   * single argument, which is the invalid type that was used.
   */
  public static final int MSGID_PLUGIN_PWPIMPORT_INVALID_PLUGIN_TYPE =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 65;



  /**
   * The message ID for the message that will be used if an error occurs while
   * attempting to encode a user password.  This takes three arguments, which
   * are the name of the password attribute, the user entry DN, and a string
   * representation of the exception that was caught.
   */
  public static final int MSGID_PLUGIN_PWPIMPORT_ERROR_ENCODING_PASSWORD =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 66;



  /**
   * The message ID for the message that will be used if an attempt is made to
   * invoke a plugin for a plugin type that it does not support.  This takes
   * two arguments, which are the DN of the plugin configuration entry and the
   * name of the unsupported plugin type.
   */
  public static final int MSGID_PLUGIN_TYPE_NOT_SUPPORTED =
       CATEGORY_MASK_PLUGIN | SEVERITY_MASK_SEVERE_ERROR | 67;



  /**
   * Associates a set of generic messages with the message IDs defined in this
   * class.
   */
  public static void registerMessages()
  {
    registerMessage(MSGID_PLUGIN_NULL_CONFIG_ENTRY,
                    "Unable to initialize an instance of the plugin defined " +
                    "in class %s because the provided configuration entry " +
                    "was null.");


    registerMessage(MSGID_PLUGIN_DESCRIPTION_SHUTDOWN_PASSWORD,
                    "Specifies an optional encoded password that will be " +
                    "required in order to be able to stop the Directory " +
                    "Server.  If this is not provided, then no password will " +
                    "be required (although it will still be necessary to " +
                    "authenticate to the server in order to be able to add " +
                    "necessary task entry).  Changes to this password will " +
                    "take effect immediately.");


    registerMessage(MSGID_PLUGIN_ADLIST_NO_PLUGIN_TYPES,
                    "The LDAP attribute description list plugin instance " +
                    "defined in configuration entry %s does not list any " +
                    "plugin types.  This plugin must be configured to " +
                    "operate as a pre-parse search plugin.");
    registerMessage(MSGID_PLUGIN_ADLIST_INVALID_PLUGIN_TYPE,
                    "The LDAP attribute description list plugin instance " +
                    "defined in configuration entry %s lists an invalid " +
                    "plugin type %s.  This plugin may only be used as a " +
                    "pre-parse search plugin.");


    registerMessage(MSGID_PLUGIN_PROFILER_NO_PLUGIN_TYPES,
                    "The Directory Server profiler plugin instance defined " +
                    "in configuration entry %s does not list any plugin " +
                    "types.  This plugin must be configured to operate as a " +
                    "startup plugin.");
    registerMessage(MSGID_PLUGIN_PROFILER_INVALID_PLUGIN_TYPE,
                    "The Directory Server profiler plugin instance defined " +
                    "in configuration entry %s lists an invalid plugin type " +
                    "%s.  This plugin may only be used as a startup plugin.");
    registerMessage(MSGID_PLUGIN_PROFILER_DESCRIPTION_PROFILE_DIR,
                    "Specifies the path to the directory into which profile " +
                    "information will be written.  The directory must exist " +
                    "and the Directory Server must have permission to create " +
                    "new files in it.  Changes to this configuration " +
                    "attribute will take effect immediately.");
    registerMessage(MSGID_PLUGIN_PROFILER_CANNOT_DETERMINE_PROFILE_DIR,
                    "An unexpected error occurred while attempting to " +
                    "determine the value of the " +  ATTR_PROFILE_DIR +
                    " attribute in the %s entry:  %s.  The default profile " +
                    "directory of %s will be used.");
    registerMessage(MSGID_PLUGIN_PROFILER_CANNOT_WRITE_PROFILE_DATA,
                    "An unexpected error occurred when the profiler plugin " +
                    "defined in configuration entry %s attempted to write " +
                    "the information captured to output file %s:  %s.");
    registerMessage(MSGID_PLUGIN_PROFILER_DESCRIPTION_AUTOSTART,
                    "Indicates whether the profiler plugin should start " +
                    "collecting data automatically when the Directory Server " +
                    "is started.  This will only be read when the server is " +
                    "started, and any changes will take effect on the next " +
                    "restart.");
    registerMessage(MSGID_PLUGIN_PROFILER_CANNOT_DETERMINE_AUTOSTART,
                    "An unexpected error occurred while attempting to " +
                    "determine the value of the " +  ATTR_PROFILE_AUTOSTART +
                    " attribute in the %s entry:  %s.  Profiling information " +
                    "will not automatically be captured on startup and must " +
                    "be manually enabled.");
    registerMessage(MSGID_PLUGIN_PROFILER_DESCRIPTION_INTERVAL,
                    "Specifies the sample interval that should be used when " +
                    "capturing profiling information in the server.  Changes " +
                    "to this configuration attribute will take effect the " +
                    "next time the profiler is started.");
    registerMessage(MSGID_PLUGIN_PROFILER_CANNOT_DETERMINE_INTERVAL,
                    "An unexpected error occurred while attempting to " +
                    "determine the value of the " + ATTR_PROFILE_INTERVAL +
                    "attribute in the %s entry:  %s.  The default sample " +
                    "interval of %d milliseconds will be used.");
    registerMessage(MSGID_PLUGIN_PROFILER_DESCRIPTION_STATE,
                    "Specifies the current state for the profiler.  It will " +
                    "be either \"enabled\" (which indicates that the " +
                    "profiler thread is actively collecting data) or " +
                    "\"disabled\".  This is a read-only attribute.");
    registerMessage(MSGID_PLUGIN_PROFILER_DESCRIPTION_ACTION,
                    "Specifies the action that should be taken by the " +
                    "profiler.  A value of \"start\" will cause the profiler " +
                    "thread to start collecting data if it is not already " +
                    "active.  A value of \"stop\" will cause the profiler " +
                    "thread to stop collecting data and write it do disk, " +
                    "and a value of \"cancel\" will cause the profiler " +
                    "thread to stop collecting data and discard anything " +
                    "that has been captured.  These operations will occur " +
                    "immediately.");
    registerMessage(MSGID_PLUGIN_PROFILER_INVALID_PROFILE_DIR,
                    "The profile directory %s specified in attribute " +
                    ATTR_PROFILE_DIR + " of configuration entry %s is " +
                    "invalid because the specified path does not exist or " +
                    "is not a directory.");
    registerMessage(MSGID_PLUGIN_PROFILER_CANNOT_DETERMINE_INTERVAL,
                    "An unexpected error occurred while attempting to " +
                    "determine the value of the " + ATTR_PROFILE_ACTION +
                    " attribute in the %s entry:  %s.  No action will be " +
                    "taken.");
    registerMessage(MSGID_PLUGIN_PROFILER_UPDATED_INTERVAL,
                    "The sample interval for the profiler plugin defined in " +
                    "configuration entry %s has been updated to %d " +
                    "milliseconds.");
    registerMessage(MSGID_PLUGIN_PROFILER_CANNOT_UPDATE_INTERVAL,
                    "An unexpected error occurred while attempting to update " +
                    "the sample interval for the profiler plugin defined in " +
                    "configuration entry %s:  %s.");
    registerMessage(MSGID_PLUGIN_PROFILER_UPDATED_DIRECTORY,
                    "The profile directory for the profiler plugin defined " +
                    "in configuration entry %s has been changed to %s.");
    registerMessage(MSGID_PLUGIN_PROFILER_CANNOT_UPDATE_DIRECTORY,
                    "An unexpected error occurred while attempting to update " +
                    "the profile directory for the profiler plugin defined " +
                    "in configuration entry %s:  %s.");
    registerMessage(MSGID_PLUGIN_PROFILER_STARTED_PROFILING,
                    "The profiler plugin defined in configuration entry %s " +
                    "has been activated and has started capturing data.");
    registerMessage(MSGID_PLUGIN_PROFILER_ALREADY_PROFILING,
                    "The profiler plugin defined in configuration entry %s " +
                    "is already active, and therefore the request to start " +
                    "profiling has been ignored.");
    registerMessage(MSGID_PLUGIN_PROFILER_NOT_RUNNING,
                    "The profiler plugin defined in configuration entry %s " +
                    "received a request to stop capturing data but it was " +
                    "not active so no action has been taken.");
    registerMessage(MSGID_PLUGIN_PROFILER_STOPPED_PROFILING,
                    "The profiler plugin defined in configuration entry %s " +
                    "has been stopped and is no longer capturing data.");
    registerMessage(MSGID_PLUGIN_PROFILER_WROTE_PROFILE_DATA,
                    "The data collected by the profiler plugin defined in " +
                    "configuration entry %s has been written to %s.");
    registerMessage(MSGID_PLUGIN_PROFILER_UNKNOWN_ACTION,
                    "The profiler plugin defined in configuration entry %s " +
                    "has been requested to perform an action %s that is " +
                    "not recognized by the server.  No action will be taken.");
    registerMessage(MSGID_PLUGIN_PROFILER_SKIPPING_ACTION,
                    "A profiler action %s was requested for the profiler " +
                    "plugin defined in configuration entry %s, but one or " +
                    "more problems were encountered with the plugin " +
                    "configuration and therefore the requested action will " +
                    "be skipped.");
    registerMessage(MSGID_PLUGIN_PROFILER_CANNOT_PERFORM_ACTION,
                    "An unexpected error occurred while attempting to " +
                    "process the requested action for the profiler plugin " +
                    "defined in configuration entry %s:  %s.");


    registerMessage(MSGID_PLUGIN_STARTUP_PLUGIN_EXCEPTION,
                    "The startup plugin defined in configuration entry %s " +
                    "threw an exception when it was invoked during the " +
                    "Directory Server startup process:  %s.  The server " +
                    "startup process has been aborted.");
    registerMessage(MSGID_PLUGIN_STARTUP_PLUGIN_RETURNED_NULL,
                    "The startup plugin defined in configuration entry %s " +
                    "returned a null value when it was invoked during the " +
                    "Directory Server startup process.  This is an illegal " +
                    "return value, and the server startup process has been " +
                    "aborted.");
    registerMessage(MSGID_PLUGIN_STARTUP_PLUGIN_FAIL_CONTINUE,
                    "The startup plugin defined in configuration entry %s " +
                    "encountered an error when it was invoked during the " +
                    "Directory Server startup process:  %s (error ID %d).  " +
                    "The startup process will continue, but this failure " +
                    "may impact the operation of the server.");
    registerMessage(MSGID_PLUGIN_STARTUP_PLUGIN_FAIL_ABORT,
                    "The startup plugin defined in configuration entry %s " +
                    "encountered an error when it was invoked during the " +
                    "Directory Server startup process:  %s (error ID %d).  " +
                    "The server startup process has been aborted.");


    registerMessage(MSGID_PLUGIN_SHUTDOWN_PLUGIN_EXCEPTION,
                    "The shutdown plugin defined in configuration entry %s " +
                    "threw an exception when it was invoked during the " +
                    "Directory Server shutdown process:  %s.");


    registerMessage(MSGID_PLUGIN_POST_CONNECT_PLUGIN_EXCEPTION,
                    "The post-connect plugin defined in configuration entry " +
                    "%s threw an exception when it was invoked for " +
                    "connection %d from %s:  %s.  The connection will be " +
                    "terminated.");
    registerMessage(MSGID_PLUGIN_POST_CONNECT_PLUGIN_RETURNED_NULL,
                    "The post-connect plugin defined in configuration entry " +
                    "%s returned null when invoked for connection %d from " +
                    "%s.  This is an illegal response, and the connection " +
                    "will be terminated.");


    registerMessage(MSGID_PLUGIN_POST_DISCONNECT_PLUGIN_EXCEPTION,
                    "The post-disconnect plugin defined in configuration " +
                    "entry %s threw an exception when it was invoked for " +
                    "connection %d from %s:  %s.");
    registerMessage(MSGID_PLUGIN_POST_DISCONNECT_PLUGIN_RETURNED_NULL,
                    "The post-disconnect plugin defined in configuration " +
                    "entry %s returned null when invoked for connection %d " +
                    "from %s.  This is an illegal response.");


    registerMessage(MSGID_PLUGIN_LDIF_IMPORT_PLUGIN_EXCEPTION,
                    "The LDIF import plugin defined in configuration entry " +
                    "%s threw an exception when it was invoked on entry " +
                    "%s:  %s.");
    registerMessage(MSGID_PLUGIN_LDIF_IMPORT_PLUGIN_RETURNED_NULL,
                    "The LDIF import plugin defined in configuration entry " +
                    "%s returned null when invoked on entry %s.  This is an " +
                    "illegal response.");
    registerMessage(MSGID_PLUGIN_LDIF_EXPORT_PLUGIN_EXCEPTION,
                    "The LDIF export plugin defined in configuration entry " +
                    "%s threw an exception when it was invoked on entry " +
                    "%s:  %s.");
    registerMessage(MSGID_PLUGIN_LDIF_EXPORT_PLUGIN_RETURNED_NULL,
                    "The LDIF export plugin defined in configuration entry " +
                    "%s returned null when invoked on entry %s.  This is an " +
                    "illegal response.");


    registerMessage(MSGID_PLUGIN_PRE_PARSE_PLUGIN_EXCEPTION,
                    "The pre-parse %s plugin defined in configuration " +
                    "entry %s threw an exception when it was invoked for " +
                    "connection %d operation %d:  %s.  Processing on this " +
                    "operation will be terminated.");
    registerMessage(MSGID_PLUGIN_PRE_PARSE_PLUGIN_RETURNED_NULL,
                    "The pre-parse %s plugin defined in configuration " +
                    "entry %s returned null when invoked for connection %d " +
                    "operation %s.  This is an illegal response, and " +
                    "processing on this operation will be terminated.");


    registerMessage(MSGID_PLUGIN_PRE_OPERATION_PLUGIN_EXCEPTION,
                    "The pre-operation %s plugin defined in configuration " +
                    "entry %s threw an exception when it was invoked for " +
                    "connection %d operation %d:  %s.  Processing on this " +
                    "operation will be terminated.");
    registerMessage(MSGID_PLUGIN_PRE_OPERATION_PLUGIN_RETURNED_NULL,
                    "The pre-operation %s plugin defined in configuration " +
                    "entry %s returned null when invoked for connection %d " +
                    "operation %s.  This is an illegal response, and " +
                    "processing on this operation will be terminated.");


    registerMessage(MSGID_PLUGIN_POST_OPERATION_PLUGIN_EXCEPTION,
                    "The post-operation %s plugin defined in configuration " +
                    "entry %s threw an exception when it was invoked for " +
                    "connection %d operation %d:  %s.  Processing on this " +
                    "operation will be terminated.");
    registerMessage(MSGID_PLUGIN_POST_OPERATION_PLUGIN_RETURNED_NULL,
                    "The post-operation %s plugin defined in configuration " +
                    "entry %s returned null when invoked for connection %d " +
                    "operation %s.  This is an illegal response, and " +
                    "processing on this operation will be terminated.");


    registerMessage(MSGID_PLUGIN_POST_RESPONSE_PLUGIN_EXCEPTION,
                    "The post-response %s plugin defined in configuration " +
                    "entry %s threw an exception when it was invoked for " +
                    "connection %d operation %d:  %s.  Processing on this " +
                    "operation will be terminated.");
    registerMessage(MSGID_PLUGIN_POST_RESPONSE_PLUGIN_RETURNED_NULL,
                    "The post-response %s plugin defined in configuration " +
                    "entry %s returned null when invoked for connection %d " +
                    "operation %s.  This is an illegal response, and " +
                    "processing on this operation will be terminated.");


    registerMessage(MSGID_PLUGIN_SEARCH_ENTRY_PLUGIN_EXCEPTION,
                    "The search result entry plugin defined in configuration " +
                    "entry %s threw an exception when it was invoked for " +
                    "connection %d operation %d with entry %s:  %s.  " +
                    "Processing on this search operation will be terminated.");
    registerMessage(MSGID_PLUGIN_SEARCH_ENTRY_PLUGIN_RETURNED_NULL,
                    "The search result entry plugin defined in configuration " +
                    "entry %s returned null when invoked for connection %d " +
                    "operation %s with entry %s.  This is an illegal " +
                    "response, and processing on this search operation will " +
                    "be terminated.");


    registerMessage(MSGID_PLUGIN_SEARCH_REFERENCE_PLUGIN_EXCEPTION,
                    "The search result reference plugin defined in " +
                    "configuration entry %s threw an exception when it was " +
                    "invoked for connection %d operation %d with referral " +
                    "URL(s) %s:  %s.  Processing on this search operation " +
                    "will be terminated.");
    registerMessage(MSGID_PLUGIN_SEARCH_REFERENCE_PLUGIN_RETURNED_NULL,
                    "The search result reference plugin defined in " +
                    "configuration entry %s returned null when invoked for " +
                    "connection %d operation %s with referral URL(s) %s.  " +
                    "This is an illegal response, and processing on this " +
                    "search operation will be terminated.");


    registerMessage(MSGID_PLUGIN_INTERMEDIATE_RESPONSE_PLUGIN_EXCEPTION,
                    "The intermediate response plugin defined in " +
                    "configuration entry %s threw an exception when it was " +
                    "invoked for connection %d operation %d:  %s.  " +
                    "Processing on this operation will be terminated.");
    registerMessage(MSGID_PLUGIN_INTERMEDIATE_RESPONSE_PLUGIN_RETURNED_NULL,
                    "The intermediate response plugin defined in " +
                    "configuration entry %s returned null when invoked for " +
                    "connection %d operation %s.  This is an illegal " +
                    "response, and processing on this operation will be " +
                    "terminated.");


    registerMessage(MSGID_PLUGIN_LASTMOD_INVALID_PLUGIN_TYPE,
                    "An attempt was made to register the LastMod plugin to " +
                    "be invoked as a %s plugin.  This plugin type is not " +
                    "allowed for this plugin.");


    registerMessage(MSGID_PROFILEVIEWER_DESCRIPTION_FILENAMES,
                    "Specifies the path to a profile data file.  This  " +
                    "argument may be provided more than once to analyze data " +
                    "from multiple data files.");
    registerMessage(MSGID_PROFILEVIEWER_DESCRIPTION_USE_GUI,
                    "Indicates whether to view the profile information in " +
                    "GUI mode or to write the resulting data to standard " +
                    "output.");
    registerMessage(MSGID_PROFILEVIEWER_DESCRIPTION_USAGE,
                    "Displays this usage information.");
    registerMessage(MSGID_PROFILEVIEWER_CANNOT_INITIALIZE_ARGS,
                    "An unexpected error occurred while attempting to " +
                    "initialize the command-line arguments:  %s.");
    registerMessage(MSGID_PROFILEVIEWER_ERROR_PARSING_ARGS,
                    "An error occurred while parsing the command-line " +
                    "arguments:  %s.");
    registerMessage(MSGID_PROFILEVIEWER_CANNOT_PROCESS_DATA_FILE,
                    "An error occurred while trying to process the profile " +
                    "data in file %s:  %s.");


    registerMessage(MSGID_PLUGIN_ENTRYUUID_INVALID_PLUGIN_TYPE,
                    "An attempt was made to register the EntryUUID plugin to " +
                    "be invoked as a %s plugin.  This plugin type is not " +
                    "allowed for this plugin.");


    registerMessage(MSGID_PLUGIN_PWPIMPORT_INVALID_PLUGIN_TYPE,
                    "An attempt was made to register the password policy " +
                    "import plugin to be invoked as a %s plugin.  This " +
                    "plugin type is not allowed for this plugin.");
    registerMessage(MSGID_PLUGIN_PWPIMPORT_ERROR_ENCODING_PASSWORD,
                    "An error occurred while attempting to encode a password " +
                    "value stored in attribute %s of user entry %s:  %s.  " +
                    "Password values for this user will not be encoded.");


    registerMessage(MSGID_PLUGIN_TYPE_NOT_SUPPORTED,
                    "The plugin defined in configuration entry %s does not " +
                    "support the %s plugin type.");
  }
}

