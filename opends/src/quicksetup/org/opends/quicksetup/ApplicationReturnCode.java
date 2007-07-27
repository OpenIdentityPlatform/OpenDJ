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
public final class ApplicationReturnCode
{
  /**
   * Enumeration defining return code.
   */
  public enum ReturnCode
  {
    /**
     * Return code: Application successful.
     */
    SUCCESSFUL(0),

    /**
     * Return code: User Cancelled uninstall.
     */
    CANCELLED(0),

    /**
     * Return code: User provided invalid data.
     */
    USER_DATA_ERROR(2),

    /**
     * Return code: Error accessing file system (reading/writing).
     */
    FILE_SYSTEM_ACCESS_ERROR(3),

    /**
     * Error downloading jar files from web start server.  This is specific
     * to the web start installation.
     */
    DOWNLOAD_ERROR(4),
    /**
     * Error during the configuration of the Directory Server.
     */
    CONFIGURATION_ERROR(5),
    /**
     * Error during the import of data (base entry, from LDIF file or
     * automatically generated data).
     */
    IMPORT_ERROR(6),
    /**
     * Error starting the Open DS server.
     */
    START_ERROR(7),

    /**
     * Error stopping the Open DS server.
     */
    STOP_ERROR(8),

    /**
     * Error enabling the Windows service.
     */
    WINDOWS_SERVICE_ERROR(9),

    /**
     * Application specific error.
     */
    APPLICATION_ERROR(10),

    /**
     * Error invoking an OpenDS tool.
     */
    TOOL_ERROR(11),

    /**
     * Return code: Bug.
     */
    BUG(12),
    /**
     * Return code: Bug.
     */
    PRINT_VERSION(50),

    /**
     * Return code for errors that are non-specified.
     */
    UNKNOWN(100);

    private int returnCode;

    /**
     * Private constructor.
     *
     * @param returnCode
     *          the return code.
     */
    private ReturnCode(int returnCode)
    {
      this.returnCode = returnCode;
    }

    /**
     * Returns the return code.
     *
     * @return the return code.
     */
    public int getReturnCode()
    {
      return returnCode;
    }
  }

  /**
   * Private constructor.
   */
  private ApplicationReturnCode()
  {
  }
}
