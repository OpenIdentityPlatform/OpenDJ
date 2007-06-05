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
package org.opends.admin.ads;

import static org.opends.server.messages.AdminMessages.*;

/**
 * This class is handling server group CLI.
 */
public class DsServiceCliReturnCode
{
  /**
   *
   * The enumeration which defines the return code.
   *
   */
  public enum ReturnCode
  {
    /**
     * successful.
     */
    SUCCESSFUL(0, MSGID_ADMIN_SUCCESSFUL),

    /**
     * successful but no operation was performed.
     */
    SUCCESSFUL_NOP(SUCCESSFUL.getReturnCode(), MSGID_ADMIN_SUCCESSFUL_NOP),

    /**
     * Unable to initialze arguments.
     */
    CANNOT_INITIALIZE_ARGS(1, MSGID_ADMIN_NO_MESSAGE),

    /**
     * Cannot parse argument.
     */
    ERROR_PARSING_ARGS(2, MSGID_ADMIN_NO_MESSAGE),
    /**
     * Return code: Cannot get the connection to the ADS.
     */
    CANNOT_CONNECT_TO_ADS(3, MSGID_ADMIN_NO_MESSAGE),

    /**
     * The host name is missing.
     */
    MISSING_HOSTNAME(4, MSGID_ADMIN_MISSING_HOSTNAME),

    /**
     * The host name is not valid.
     */
    NOVALID_HOSTNAME(5, MSGID_ADMIN_NOVALID_HOSTNAME),

    /**
     * The installation path is missing.
     */
    MISSING_IPATH(6, MSGID_ADMIN_MISSING_IPATH),

    /**
     * The installation path is not valid.
     */
    NOVALID_IPATH(7, MSGID_ADMIN_NOVALID_IPATH),

    /**
     * An access permission error.
     */
    ACCESS_PERMISSION(8, MSGID_ADMIN_ACCESS_PERMISSION),

    /**
     * The entity is already registered.
     */
    ALREADY_REGISTERED(9, MSGID_ADMIN_ALREADY_REGISTERED),

    /**
     * The installation is broken.
     */
    BROKEN_INSTALL(10, MSGID_ADMIN_BROKEN_INSTALL),

    /**
     * The entity is not yet registered.
     */
    NOT_YET_REGISTERED(11, MSGID_ADMIN_NOT_YET_REGISTERED),

    /**
     * The port is missing.
     */
    MISSING_PORT(12, MSGID_ADMIN_MISSING_PORT),

    /**
     * The port is not valid.
     */
    NOVALID_PORT(13, MSGID_ADMIN_NOVALID_PORT),

    /**
     * The name is missing.
     */
    MISSING_NAME(14, MSGID_ADMIN_MISSING_NAME),

    /**
     * The administration UID is missing.
     */
    MISSING_ADMIN_UID(15, MSGID_ADMIN_MISSING_ADMIN_UID),

    /**
     * The administratior password is missing.
     */
    MISSING_ADMIN_PASSWORD(16, MSGID_ADMIN_MISSING_ADMIN_PASSWORD),

    /**
     * Unexpected error (potential bug).
     */
    ERROR_UNEXPECTED(17, MSGID_ADMIN_ERROR_UNEXPECTED);

    // The retunCodevalue of the value.
    private final int returnCode;

    // The message id to be used of the value.
    private final int messageId;

    // Private constructor.
    private ReturnCode(int returnCode, int messageId)
    {
      this.returnCode = returnCode;
      this.messageId = messageId;
    }

    /**
     * Get the corresponding message Id.
     *
     * @return The corresponding message Id.
     */
    public int getMessageId()
    {
      return messageId;
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
  };

}
