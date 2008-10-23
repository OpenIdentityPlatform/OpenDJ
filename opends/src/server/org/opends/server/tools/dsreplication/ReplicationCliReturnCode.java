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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 */

package org.opends.server.tools.dsreplication;

import static org.opends.messages.AdminToolMessages.*;

import org.opends.messages.Message;

/**
 *
 * The enumeration which defines the return code.
 *
 */
public enum ReplicationCliReturnCode
{
  /**
   * successful.
   */
  SUCCESSFUL(0, INFO_REPLICATION_SUCCESSFUL.get()),

  /**
   * successful but no operation was performed.
   */
  SUCCESSFUL_NOP(SUCCESSFUL.getReturnCode(),
      INFO_REPLICATION_SUCCESSFUL_NOP.get()),

  /**
   * Unable to initialize arguments.
   */
  CANNOT_INITIALIZE_ARGS(1, ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * Cannot parse arguments because the user provided arguments are not valid
   * or there was an error checking the user data.
   */
  ERROR_USER_DATA(2, ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * The user cancelled the operation in interactive mode.
   */
  USER_CANCELLED(3, ERR_REPLICATION_USER_CANCELLED.get()),

  /**
   * Unexpected error (potential bug).
   */
  CONFLICTING_ARGS(4, ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * The provided base DNs cannot be used to enable replication.
   */
  REPLICATION_CANNOT_BE_ENABLED_ON_BASEDN(5, ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * The provided base DNs cannot be used to disable replication.
   */
  REPLICATION_CANNOT_BE_DISABLED_ON_BASEDN(6, ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * The provided base DNs cannot be used to initialize the contents of the
   * replicas.
   */
  REPLICATION_CANNOT_BE_INITIALIZED_ON_BASEDN(7,
      ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * Error connecting with the provided credentials.
   */
  ERROR_CONNECTING(8, ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * Could not find the replication ID of the domain to be used to initialize
   * the replica.
   */
  REPLICATIONID_NOT_FOUND(9, ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * The number of tries we perform to start the initialization are over.
   * We systematically receive a peer not found error.
   */
  INITIALIZING_TRIES_COMPLETED(10, ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * Error enabling replication on a base DN.
   */
  ERROR_ENABLING_REPLICATION_ON_BASEDN(11, ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * Error initializing base DN.
   */
  ERROR_INITIALIZING_BASEDN_GENERIC(12, ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * Error reading configuration.
   */
  ERROR_READING_CONFIGURATION(13, ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * Error updating ADS.
   */
  ERROR_UPDATING_ADS(14, ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * Error reading ADS.
   */
  ERROR_READING_ADS(15, ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * Error reading TopologyCache.
   */
  ERROR_READING_TOPOLOGY_CACHE(16, ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * Error configuring replication server.
   */
  ERROR_CONFIGURING_REPLICATIONSERVER(17, ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * Unsupported ADS scenario.
   */
  REPLICATION_ADS_MERGE_NOT_SUPPORTED(18, ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * Error disabling replication on base DN.
   */
  ERROR_DISABLING_REPLICATION_ON_BASEDN(19, ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * Error removing replication port reference on base DN.
   */
  ERROR_DISABLING_REPLICATION_REMOVE_REFERENCE_ON_BASEDN(20,
      ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * Error initializing Administration Framework.
   */
  ERROR_INITIALIZING_ADMINISTRATION_FRAMEWORK(21,
      ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * Error seeding trustore.
   */
  ERROR_SEEDING_TRUSTORE(22, ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * Error launching pre external initialization.
   */
  ERROR_LAUNCHING_PRE_EXTERNAL_INITIALIZATION(23,
      ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * Error launching pre external initialization.
   */
  ERROR_LAUNCHING_POST_EXTERNAL_INITIALIZATION(24,
      ERR_REPLICATION_NO_MESSAGE.get()),

  /**
   * Error disabling replication server.
   */
  ERROR_DISABLING_REPLICATION_SERVER(25, ERR_REPLICATION_NO_MESSAGE.get());


  private Message message;
  private int returnCode;

  // Private constructor.
  private ReplicationCliReturnCode(int returnCode, Message message)
  {
    this.returnCode = returnCode;
    this.message = message;
  }

  /**
   * Get the corresponding message.
   *
   * @return The corresponding message.
   */
  public Message getMessage()
  {
    return message;
  }

  /**
   * Get the corresponding return code value.
   *
   * @return The corresponding return code value.
   */
  public int getReturnCode()
  {
    return returnCode;
  }
}
