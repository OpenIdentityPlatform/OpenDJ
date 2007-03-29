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
package org.opends.server.synchronization.common;

import org.opends.server.messages.MessageHandler;
import static org.opends.server.messages.MessageHandler.*;

/**
 * This class defines the set of message IDs and default format strings for
 * messages associated with the Synchronization.
 */
public class LogMessages {

  /**
   * Name used to store attachment of historical information in the
   * operation.
   */
  public static final String HISTORICAL = "ds-synch-historical";

  /**
   * Invalid DN.
   */
  public static final int MSGID_SYNC_INVALID_DN =
       CATEGORY_MASK_SYNC | SEVERITY_MASK_MILD_ERROR | 1;

  /**
   * Need at least one Changelog Server.
   */
  public static final int MSGID_NEED_CHANGELOG_SERVER =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_MILD_ERROR | 2;

  /**
   * Need to have a server ID.
   */
  public static final int MSGID_NEED_SERVER_ID =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_MILD_ERROR | 3;

  /**
   * Invalid Changelog Server.
   */
  public static final int MSGID_INVALID_CHANGELOG_SERVER =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_MILD_ERROR | 4;

  /**
   * Unknown hostname.
   */
  public static final int MSGID_UNKNOWN_HOSTNAME =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_MILD_ERROR | 5;

  /**
   * Could not connect to any changelog server.
   */
  public static final int MSGID_COULD_NOT_BIND_CHANGELOG =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_MILD_ERROR | 6;

  /**
   * Unknown Operation type.
   */
  public static final int MSGID_UNKNOWN_TYPE =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_MILD_ERROR | 7;

  /**
   * Error while replaying an operation.
   */
  public static final int MSGID_ERROR_REPLAYING_OPERATION =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_MILD_ERROR | 8;

  /**
   * Operation was not found in Pending List during Post-Operation processing.
   */
  public static final int MSGID_OPERATION_NOT_FOUND_IN_PENDING =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_MILD_ERROR | 9;

  /**
   * Unable to open changelog database.
   */
  public static final int MSGID_COULD_NOT_INITIALIZE_DB =
   CATEGORY_MASK_SYNC | SEVERITY_MASK_MILD_ERROR | 10;

  /**
   * Unable to read changelog database.
   */
  public static final int MSGID_COULD_NOT_READ_DB =
   CATEGORY_MASK_SYNC | SEVERITY_MASK_MILD_ERROR | 11;

  /**
   * Exception while replaying an operation.
   */
  public static final int MSGID_EXCEPTION_REPLAYING_OPERATION =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_MILD_ERROR | 12;

  /**
   * Need to have a Changelog port.
   */
  public static final int MSGID_NEED_CHANGELOG_PORT =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_MILD_ERROR | 13;

  /**
   * Error while updating the ruv.
   */
  public static final int MSGID_ERROR_UPDATING_RUV =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_MILD_ERROR | 14;

  /**
   * Error while searching the ruv.
   */
  public static final int MSGID_ERROR_SEARCHING_RUV =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_MILD_ERROR | 15;

  /**
   * A server disconnected from the changelog server.
   * (this is an informational message)
   */
  public static final int MSGID_SERVER_DISCONNECT =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_NOTICE | 16;

  /**
   * There is no server listening on this host:port.
   * (this is an informational message)
   */
  public static final int MSGID_NO_CHANGELOG_SERVER_LISTENING =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_NOTICE | 17;

  /**
   * Tried to connect to a changelog server that does not have
   * all the changes that we have.
   * Try another one.
   */
  public static final int MSGID_CHANGELOG_MISSING_CHANGES =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_NOTICE | 18;

  /**
   * Only one changelog server is configured.
   * If this server fails the LDAP server will not be able to
   * process updates anymore.
   */
  public static final int MSGID_NEED_MORE_THAN_ONE_CHANGELOG_SERVER =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_NOTICE | 19;

  /**
   * An Exception happened during connection to a changelog server.
   */
  public static final int MSGID_EXCEPTION_STARTING_SESSION =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_NOTICE | 20;

  /**
   * The internal search operation used to find old changes
   * caused an error.
   */
  public static final int MSGID_CANNOT_RECOVER_CHANGES =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_NOTICE | 21;

  /**
   * When trying to find a changelog sever, it was detected that
   * none of the Changelog server has seen all the operations
   * that this server has already processed.
   */
  public static final int MSGID_COULD_NOT_FIND_CHANGELOG_WITH_MY_CHANGES =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_NOTICE | 22;

  /**
   * Could not find any working changelog server.
   */
  public static final int MSGID_COULD_NOT_FIND_CHANGELOG =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_NOTICE | 23;

  /**
   * Exception closing changelog database.
   */
  public static final int MSGID_EXCEPTION_CLOSING_DATABASE =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_NOTICE | 24;

  /**
   * Error Decoding message during operation replay.
   */
  public static final int MSGID_EXCEPTION_DECODING_OPERATION =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_SEVERE_ERROR | 25;

  /**
   * Database Exception in the Chanlog service causing the
   * changelog to shutdown.
   */
  public static final int MSGID_CHANGELOG_SHUTDOWN_DATABASE_ERROR =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_FATAL_ERROR | 26;

  /**
   * Database Exception in the Chanlog service causing the
   * changelog to shutdown.
   */
  public static final int MSGID_IGNORE_BAD_DN_IN_DATABASE_IDENTIFIER =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_SEVERE_ERROR | 27;

  /**
   * Database Exception closing the Changelog Environement.
   */
  public static final int MSGID_ERROR_CLOSING_CHANGELOG_ENV =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_SEVERE_ERROR | 28;

  /**
   * Exception during the database trimming or flush.
   */
  public static final int MSGID_EXCEPTION_CHANGELOG_TRIM_FLUSH =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_SEVERE_ERROR | 29;

  /**
   * Error processing changelog message.
   */
  public static final int MSGID_CHANGELOG_CONNECTION_ERROR =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_SEVERE_ERROR | 30;

  /**
   * Remote server has sent an unknown message.
   */
  public static final int MSGID_UNKNOWN_MESSAGE =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_SEVERE_ERROR | 31;

  /**
   * Remote server has sent an unknown message.
   */
  public static final int MSGID_WRITER_UNEXPECTED_EXCEPTION =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_SEVERE_ERROR | 32;

  /**
   * Remote server has sent an unknown message.
   */
  public static final int MSGID_CHANGELOG_ERROR_SENDING_ACK =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_SEVERE_ERROR | 33;

  /**
   * Exception while receiving a message.
   */
  public static final int MSGID_EXCEPTION_RECEIVING_SYNCHRONIZATION_MESSAGE =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_SEVERE_ERROR | 34;

  /**
   * Loop detected while replaying an operation.  This message takes one
   * string argument containing details of the operation that could not be
   * replayed.
   */
  public static final int MSGID_LOOP_REPLAYING_OPERATION =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_MILD_ERROR | 35;

  /**
   * Failure when test existence or try to create directory
   * for the changelog database.  This message takes one
   * string argument containing details of the exception
   * and path of the directory.
   */
  public static final int MSGID_FILE_CHECK_CREATE_FAILED =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_MILD_ERROR | 36;

  /**
   * The message ID for the description of the attribute used to specify the
   * list of other Changelog Servers in the Changelog Server
   * Configuration.
   */
  public static final int MSGID_CHANGELOG_SERVER_ATTR =
    CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 37;

  /**
   * The message ID for the description of the attribute used to specify
   * the identifier of the Changelog Server.
   */
  public static final int MSGID_SERVER_ID_ATTR =
    CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 38;

  /**
   * The message id for the description of the attribute used to specify
   * the port number of the Changelog Server.
   */
  public static final int MSGID_CHANGELOG_PORT_ATTR =
    CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 39;

  /**
   * The message id for the description of the attribute used to specify
   * the receive Window Size used by a Changelog Server.
   */
  public static final int MSGID_WINDOW_SIZE_ATTR =
    CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 40;

  /**
   * The message id for thedescription of the  attribute used to specify
   * the maximum queue size used by a Changelog Server.
   */
  public static final int MSGID_QUEUE_SIZE_ATTR =
    CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 41;

  /**
   * The message id for the Attribute used to specify the directory where the
   * persistent storage of the Changelog server will be saved.
   */
  public static final int MSGID_CHANGELOG_DIR_PATH_ATTR =
    CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 42;

  /**
   * The message id for the description of the attribute used to configure
   * the purge delay of the Changelog Servers.
   */
  public static final int MSGID_PURGE_DELAY_ATTR =
    CATEGORY_MASK_CORE | SEVERITY_MASK_INFORMATIONAL | 43;

  /**
   * The message id for the error raised when export/import
   * is rejected due to an export/import already in progress.
   */
  public static final int MSGID_SIMULTANEOUS_IMPORT_EXPORT_REJECTED =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_SEVERE_ERROR | 44;

  /**
   * The message id for the error raised when import
   * is rejected due to an invalid source of data imported.
   */
  public static final int MSGID_INVALID_IMPORT_SOURCE =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_SEVERE_ERROR | 45;

  /**
   * The message id for the error raised when export
   * is rejected due to an invalid target to export datas.
   */
  public static final int MSGID_INVALID_EXPORT_TARGET =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_SEVERE_ERROR | 46;

  /**
   * The message id for the error raised when import/export message
   * cannot be routed to an up-and-running target in the domain.
   */
  public static final int MSGID_NO_REACHABLE_PEER_IN_THE_DOMAIN =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_SEVERE_ERROR | 47;

  /**
   * The message ID for the message that will be used when no domain
   * can be found matching the provided domain base DN.
   */
  public static final int  MSGID_NO_MATCHING_DOMAIN =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_SEVERE_ERROR | 48;

  /**
   * The message ID for the message that will be used when no domain
   * can be found matching the provided domain base DN.
   */
  public static final int  MSGID_MULTIPLE_MATCHING_DOMAIN
       = CATEGORY_MASK_SYNC | SEVERITY_MASK_SEVERE_ERROR | 49;


  /**
   * The message ID for the message that will be used when the domain
   * belongs to a provider class that does not allow the export.
   */
  public static final int  MSGID_INVALID_PROVIDER =
    CATEGORY_MASK_SYNC | SEVERITY_MASK_SEVERE_ERROR | 50;

  /**
   * Register the messages from this class in the core server.
   *
   */
  public static void registerMessages()
  {
    MessageHandler.registerMessage(MSGID_SYNC_INVALID_DN,
       "The Synchronization configuration DN is invalid");
    MessageHandler.registerMessage(MSGID_NEED_CHANGELOG_SERVER,
        "At least one changelog server must be declared");
    MessageHandler.registerMessage(MSGID_NEED_SERVER_ID,
        "The Server Id must be defined");
    MessageHandler.registerMessage(MSGID_INVALID_CHANGELOG_SERVER,
        "Invalid changelog server configuration");
    MessageHandler.registerMessage(MSGID_UNKNOWN_HOSTNAME,
        "Changelog failed to start because the hostname is unknown.");
    MessageHandler.registerMessage(MSGID_COULD_NOT_BIND_CHANGELOG,
        "Changelog failed to start :" +
        " could not bind to the changelog listen port : %d. Error : %s");
    MessageHandler.registerMessage(MSGID_UNKNOWN_TYPE,
        "Unknown operation type : %s");
    MessageHandler.registerMessage(MSGID_ERROR_REPLAYING_OPERATION,
        "Error %s when replaying operation with changenumber %s %s : %s");
    MessageHandler.registerMessage(MSGID_OPERATION_NOT_FOUND_IN_PENDING,
        "Internal Error : Operation %s change number %s" +
        " was not found in pending list");
    MessageHandler.registerMessage(MSGID_COULD_NOT_INITIALIZE_DB,
        "Changelog failed to start " +
        "because the database %s could not be opened");
    MessageHandler.registerMessage(MSGID_COULD_NOT_READ_DB,
        "Changelog failed to start " +
        "because the database %s could not be read");
    MessageHandler.registerMessage(MSGID_EXCEPTION_REPLAYING_OPERATION,
         "An Exception was caught while replaying operation %s : %s");
    MessageHandler.registerMessage(MSGID_NEED_CHANGELOG_PORT,
         "The Changelog server port must be defined");
    MessageHandler.registerMessage(MSGID_ERROR_UPDATING_RUV,
         "Error %s when updating server state %s : %s base dn : %s");
    MessageHandler.registerMessage(MSGID_ERROR_SEARCHING_RUV,
         "Error %s when searching for server state %s : %s base dn : %s");
    MessageHandler.registerMessage(MSGID_SERVER_DISCONNECT,
         "%s has disconnected from this changelog server.");
    MessageHandler.registerMessage(MSGID_NO_CHANGELOG_SERVER_LISTENING,
         "There is no changelog server listening on %s.");
    MessageHandler.registerMessage(MSGID_CHANGELOG_MISSING_CHANGES,
        "The changelog server %s is missing some changes that this server" +
        " has already processed.");
    MessageHandler.registerMessage(MSGID_NEED_MORE_THAN_ONE_CHANGELOG_SERVER,
        "More than one changelog server should be configured.");
    MessageHandler.registerMessage(MSGID_EXCEPTION_STARTING_SESSION,
        "Caught Exception during initial communication with " +
        "changelog server : ");
    MessageHandler.registerMessage(MSGID_CANNOT_RECOVER_CHANGES,
        "Error when searching old changes from the database. ");
    MessageHandler.registerMessage(
        MSGID_COULD_NOT_FIND_CHANGELOG_WITH_MY_CHANGES,
        "Could not find a changelog server that has seen all the local" +
        " changes. Going to replay changes.");
    MessageHandler.registerMessage(MSGID_COULD_NOT_FIND_CHANGELOG,
        "Could not connect to any changelog server, retrying...");
    MessageHandler.registerMessage(MSGID_EXCEPTION_CLOSING_DATABASE,
        "Error closing changelog database %s : ");
    MessageHandler.registerMessage(MSGID_EXCEPTION_DECODING_OPERATION,
        "Error trying to replay %s, operation could not be decoded : ");
    MessageHandler.registerMessage(MSGID_CHANGELOG_SHUTDOWN_DATABASE_ERROR,
        "Error Trying to use the underlying database. " +
        "The Changelog Service is going to shut down. ");
    MessageHandler.registerMessage(MSGID_IGNORE_BAD_DN_IN_DATABASE_IDENTIFIER,
        "A badly formatted DN was found in the list of database known " +
        "By this changelog service :%s. This Identifier will be ignored. ");
    MessageHandler.registerMessage(MSGID_ERROR_CLOSING_CHANGELOG_ENV,
        "Error closing the changelog database : ");
    MessageHandler.registerMessage(MSGID_EXCEPTION_CHANGELOG_TRIM_FLUSH,
        "Error during the changelog database trimming or flush process." +
        " The Changelog service is going to shutdown. ");
    MessageHandler.registerMessage(MSGID_CHANGELOG_CONNECTION_ERROR,
        "Error during Changelog service message processing ." +
        " Connection from %s is rejected. ");
    MessageHandler.registerMessage(MSGID_UNKNOWN_MESSAGE,
        "%s has sent an unknown message. Closing the connection. ");
    MessageHandler.registerMessage(MSGID_WRITER_UNEXPECTED_EXCEPTION,
        "An unexpected error happened handling connection with %s." +
        "This connection is going to be closed. ");
    MessageHandler.registerMessage(MSGID_CHANGELOG_ERROR_SENDING_ACK,
        "An unexpected error happened sending an ack to %s." +
        "This connection is going to be closed. ");
    MessageHandler.registerMessage(
        MSGID_EXCEPTION_RECEIVING_SYNCHRONIZATION_MESSAGE,
        "An Exception was caught while receiving synchronization message : %s");
    MessageHandler.registerMessage(MSGID_LOOP_REPLAYING_OPERATION,
        "A loop was detected while replaying operation: %s");
    MessageHandler.registerMessage(MSGID_FILE_CHECK_CREATE_FAILED,
        "An Exception was caught while testing existence or trying " +
        " to create the directory for the changelog database : %s");
    MessageHandler.registerMessage(MSGID_CHANGELOG_SERVER_ATTR,
        "Specifies the list of Changelog Servers to which this" +
        " Changelog Server should connect. Each value of this attribute" +
        " should contain a values build with the hostname and the port" +
        " number of the remote server separated with a \":\"");
    MessageHandler.registerMessage(MSGID_SERVER_ID_ATTR,
        "Specifies the server ID. Each Changelog Server in the topology" +
        " Must be assigned a unique server ID in the topology.");
    MessageHandler.registerMessage(MSGID_CHANGELOG_PORT_ATTR,
        "Specifies the port number that the changelog server will use to" +
        " listen for connections from LDAP servers.");
    MessageHandler.registerMessage(MSGID_WINDOW_SIZE_ATTR,
        "Specifies the receive window size of the changelog server.");
    MessageHandler.registerMessage(MSGID_QUEUE_SIZE_ATTR,
        "Specifies the receive queue size of the changelog server." +
        " The Changelog servers will queue up to this number of messages" +
        " in its memory queue and save the older messages to persistent" +
        " storage. Using a larger size may improve performances when" +
        " The synchronization delay is larger than this size but at the cost" +
        " of using more memory.");
    MessageHandler.registerMessage(MSGID_CHANGELOG_DIR_PATH_ATTR,
        "Specifies the Changelog Server directory. The Changelog server" +
        " will create all persistent storage below this path.");
    MessageHandler.registerMessage(MSGID_PURGE_DELAY_ATTR,
        "Specifies the Changelog Purge Delay, The Changelog servers will" +
        " keep all changes up to this amount of time before deleting them." +
        " This values defines the maximum age of a backup that can be" +
        " restored because changelog servers would not be able to refresh" +
        " LDAP servers with older versions of the data. A zero value" +
        " can be used to specify an infinite delay (or never purge).");
    MessageHandler.registerMessage(MSGID_SIMULTANEOUS_IMPORT_EXPORT_REJECTED,
        "The current request is rejected due to an import or an export" +
        " already in progress for the same data.");
    MessageHandler.registerMessage(MSGID_INVALID_IMPORT_SOURCE,
        "Invalid source for the import.");
    MessageHandler.registerMessage(MSGID_INVALID_EXPORT_TARGET,
        "Invalid target for the export.");
    MessageHandler.registerMessage(MSGID_NO_REACHABLE_PEER_IN_THE_DOMAIN,
        "No reachable peer in the domain.");
    MessageHandler.registerMessage(MSGID_NO_MATCHING_DOMAIN,
        "No domain matches the base DN provided.");
    MessageHandler.registerMessage(MSGID_MULTIPLE_MATCHING_DOMAIN,
        "Multiple domains match the base DN provided.");
    MessageHandler.registerMessage(MSGID_INVALID_PROVIDER,
        "The provider class does not allow the operation requested.");
  }
}
