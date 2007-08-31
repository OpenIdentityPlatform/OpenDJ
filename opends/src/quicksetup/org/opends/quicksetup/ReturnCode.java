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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup;

/**
 * This class defines enumeration of application return code.
 */
public class ReturnCode {

  /**
   * Return code: Application successful.
   */
  public static final ReturnCode SUCCESSFUL = new ReturnCode(0);

  /**
   * Return code: User Cancelled uninstall.
   */
  public static final ReturnCode CANCELLED = new ReturnCode(0);

  /**
   * Return code: User provided invalid data.
   */
  public static final ReturnCode USER_DATA_ERROR = new ReturnCode(2);

  /**
   * Return code: Error accessing file system (reading/writing).
   */
  public static final ReturnCode FILE_SYSTEM_ACCESS_ERROR = new ReturnCode(3);

  /**
   * Error downloading jar files from web start server.  This is specific
   * to the web start installation.
   */
  public static final ReturnCode DOWNLOAD_ERROR = new ReturnCode(4);

  /**
   * Error during the configuration of the Directory Server.
   */
  public static final ReturnCode CONFIGURATION_ERROR = new ReturnCode(5);

  /**
   * Error during the import of data (base entry, from LDIF file or
   * automatically generated data).
   */

  public static final ReturnCode IMPORT_ERROR = new ReturnCode(6);

  /**
   * Error starting the Open DS server.
   */
  public static final ReturnCode START_ERROR = new ReturnCode(7);

  /**
   * Error stopping the Open DS server.
   */
  public static final ReturnCode STOP_ERROR = new ReturnCode(8);

  /**
   * Error enabling the Windows service.
   */
  public static final ReturnCode WINDOWS_SERVICE_ERROR = new ReturnCode(9);

  /**
   * Application specific error.
   */
  public static final ReturnCode APPLICATION_ERROR = new ReturnCode(10);

  /**
   * Error invoking an OpenDS tool.
   */
  public static final ReturnCode TOOL_ERROR = new ReturnCode(11);

  /**
   * Return code: Bug.
   */
  public static final ReturnCode BUG = new ReturnCode(12);

  /**
   * Return code: Bug.
   */
  public static final ReturnCode PRINT_VERSION = new ReturnCode(50);

  /**
   * Return code: Bug.
   */
  public static final ReturnCode PRINT_USAGE = new ReturnCode(51);

  /**
   * Return code for errors that are non-specified.
   */
  public static final ReturnCode UNKNOWN = new ReturnCode(100);


  private int code;

  /**
   * Creates a new parameterized instance.
   *
   * @param code to return
   */
  public ReturnCode(int code) {
    this.code = code;
  }

  /**
   * Gets the return code to return to the console.
   *
   * @return int code
   */
  public int getReturnCode() {
    return code;
  }

}
