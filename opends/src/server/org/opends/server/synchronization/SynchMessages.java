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
package org.opends.server.synchronization;

import org.opends.server.messages.MessageHandler;
import static org.opends.server.messages.MessageHandler.*;

/**
 * This class defines the set of message IDs and default format strings for
 * messages associated with the Synchronization.
 */
public class SynchMessages {

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
   * Register the messages from this class in the core server.
   *
   */
  static void registerMessages()
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
  }
}
