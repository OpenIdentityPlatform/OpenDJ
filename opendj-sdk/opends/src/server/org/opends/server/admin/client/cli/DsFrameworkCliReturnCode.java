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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.admin.client.cli;
import org.opends.messages.Message;

import static org.opends.messages.AdminMessages.*;


import java.util.HashMap;

import org.opends.admin.ads.ADSContextException.ErrorType;

  /**
   *
   * The enumeration which defines the return code.
   *
   */
  public enum DsFrameworkCliReturnCode
  {
    /**
     * successful.
     */
    SUCCESSFUL(0, INFO_ADMIN_SUCCESSFUL.get()),

    /**
     * successful but no operation was performed.
     */
    SUCCESSFUL_NOP(SUCCESSFUL.getReturnCode(), INFO_ADMIN_SUCCESSFUL_NOP.get()),

    /**
     * Unable to initialize arguments.
     */
    CANNOT_INITIALIZE_ARGS(1, ERR_ADMIN_NO_MESSAGE.get()),

    /**
     * Cannot parse arguments.
     */
    ERROR_PARSING_ARGS(2, ERR_ADMIN_NO_MESSAGE.get()),
    /**
     * Return code: Cannot get the connection to the ADS.
     */
    CANNOT_CONNECT_TO_ADS(3, ERR_ADMIN_NO_MESSAGE.get()),

    /**
     * The host name is missing.
     */
    MISSING_HOSTNAME(4, ERR_ADMIN_MISSING_HOSTNAME.get()),

    /**
     * The host name is not valid.
     */
    NOVALID_HOSTNAME(5, ERR_ADMIN_NOVALID_HOSTNAME.get()),

    /**
     * The installation path is missing.
     */
    MISSING_IPATH(6, ERR_ADMIN_MISSING_IPATH.get()),

    /**
     * The installation path is not valid.
     */
    NOVALID_IPATH(7, ERR_ADMIN_NOVALID_IPATH.get()),

    /**
     * An access permission error.
     */
    ACCESS_PERMISSION(8, ERR_ADMIN_ACCESS_PERMISSION.get()),

    /**
     * The entity is already registered.
     */
    ALREADY_REGISTERED(9, ERR_ADMIN_ALREADY_REGISTERED.get()),

    /**
     * The installation is broken.
     */
    BROKEN_INSTALL(10, ERR_ADMIN_BROKEN_INSTALL.get()),

    /**
     * The entity is not yet registered.
     */
    NOT_YET_REGISTERED(11, ERR_ADMIN_NOT_YET_REGISTERED.get()),

    /**
     * The port is missing.
     */
    MISSING_PORT(12, ERR_ADMIN_MISSING_PORT.get()),

    /**
     * The port is not valid.
     */
    NOVALID_PORT(13, ERR_ADMIN_NOVALID_PORT.get()),

    /**
     * The name is missing.
     */
    MISSING_NAME(14, ERR_ADMIN_MISSING_NAME.get()),

    /**
     * The administration UID is missing.
     */
    MISSING_ADMIN_UID(15, ERR_ADMIN_MISSING_ADMIN_UID.get()),

    /**
     * The administrator password is missing.
     */
    MISSING_ADMIN_PASSWORD(16, ERR_ADMIN_MISSING_ADMIN_PASSWORD.get()),

    /**
     * Unexpected error (potential bug).
     */
    ERROR_UNEXPECTED(17, ERR_ADMIN_ERROR_UNEXPECTED.get()),

    /**
     * Unexpected error (potential bug).
     */
    CONFLICTING_ARGS(18, ERR_ADMIN_NO_MESSAGE.get()),

    /**
     * The server entity is not yet registered.
     */
    SERVER_NOT_REGISTERED(19, ERR_ADMIN_SERVER_NOT_REGISTERED.get());

    // The retunCodevalue of the value.
    private final int returnCode;

    // The message id to be used of the value.
    private final Message message;

    // Private constructor.
    private DsFrameworkCliReturnCode(int returnCode, Message message)
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

  /**
   * Indicate whenever the association between ADS errors and return
   * has been done.
   */
  private static boolean initialized = false ;

  /**
   * The association map between ADS Error and Return code.
   */
  private static HashMap<ErrorType, DsFrameworkCliReturnCode>
    adsErrorToReturnCode = new HashMap<ErrorType, DsFrameworkCliReturnCode>();

  /**
   * Associates a set of ADS errors to return code.
   */
  private  static void registerAdsError()
  {
    adsErrorToReturnCode.put(ErrorType.MISSING_HOSTNAME,
        MISSING_HOSTNAME);
    adsErrorToReturnCode.put(ErrorType.NOVALID_HOSTNAME,
        NOVALID_HOSTNAME);
    adsErrorToReturnCode.put(ErrorType.MISSING_IPATH,
        MISSING_IPATH);
    adsErrorToReturnCode.put(ErrorType.NOVALID_IPATH,
        NOVALID_IPATH);
    adsErrorToReturnCode.put(ErrorType.ACCESS_PERMISSION,
        ACCESS_PERMISSION);
    adsErrorToReturnCode.put(ErrorType.ALREADY_REGISTERED,
        ALREADY_REGISTERED);
    adsErrorToReturnCode.put(ErrorType.BROKEN_INSTALL,
        BROKEN_INSTALL);
    adsErrorToReturnCode.put(ErrorType.UNEXPECTED_ADS_BACKEND_TYPE,
        BROKEN_INSTALL);
    adsErrorToReturnCode.put(ErrorType.NOT_YET_REGISTERED,
        NOT_YET_REGISTERED);
    adsErrorToReturnCode.put(ErrorType.MISSING_PORT,
        MISSING_PORT);
    adsErrorToReturnCode.put(ErrorType.NOVALID_PORT,
        NOVALID_PORT);
    adsErrorToReturnCode.put(ErrorType.MISSING_NAME,
        MISSING_NAME);
    adsErrorToReturnCode.put(ErrorType.MISSING_ADMIN_UID,
        MISSING_ADMIN_UID);
    adsErrorToReturnCode.put(ErrorType.MISSING_ADMIN_PASSWORD,
        MISSING_ADMIN_PASSWORD);
    adsErrorToReturnCode.put(ErrorType.ERROR_UNEXPECTED,
        ERROR_UNEXPECTED);
  }

  /**
   * Get ReturnCode from an ADS error.
   * @param error The ADS error
   * @return the ReturnCode associated to the ADS error.
   */
  public static DsFrameworkCliReturnCode
    getReturncodeFromAdsError(ErrorType error)
  {
    if (! initialized)
    {
      registerAdsError();
      initialized = true ;
    }
    return adsErrorToReturnCode.get(error);
  }
 }


