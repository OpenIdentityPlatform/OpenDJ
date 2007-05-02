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

package org.opends.quicksetup;

/**
 * This exception is used to encapsulate all the error that we might have
 * during the installation.
 *
 * @see org.opends.quicksetup.installer.Installer
 * @see org.opends.quicksetup.installer.webstart.WebStartInstaller
 * @see org.opends.quicksetup.installer.offline.OfflineInstaller
 *
 */
public class ApplicationException extends Exception
{
  private static final long serialVersionUID = -3527273444231560341L;

  private String formattedMsg = null;

  private Type type;

  /**
   * This enum contains the different type of ApplicationException that we can
   * have.
   *
   */
  public enum Type
  {
    /**
     * Error related to file system error: IOException writing files, permission
     * errors, etc.
     */
    FILE_SYSTEM_ERROR,
    /**
     * Error downloading jar files from web start server.  This is specific
     * to the web start installation.
     */
    DOWNLOAD_ERROR,
    /**
     * Error during the configuration of the Directory Server.
     */
    CONFIGURATION_ERROR,
    /**
     * Error during the import of data (base entry, from LDIF file or
     * automatically generated data).
     */
    IMPORT_ERROR,
    /**
     * Error starting the Open DS server.
     */
    START_ERROR,

    /**
     * Error stopping the Open DS server.
     */
    STOP_ERROR,

    /**
     * Error enabling the Windows service.
     */
    WINDOWS_SERVICE_ERROR,

    /**
     * Application specific error.
     */
    APPLICATION,

    /**
     * Error invoking an OpenDS tool.
     */
    TOOL_ERROR,

    /**
     * A bug (for instance when we throw an IllegalStateException).
     */
    BUG
  }

  /**
   * Creates a new ApplicationException of type FILE_SYSTEM_ERROR.
   * @param msg localized exception message
   * @param e Exception cause
   * @return ApplicationException with Type property being FILE_SYSTEM_ERROR
   */
  public static ApplicationException createFileSystemException(String msg,
                                                               Exception e) {
    return new ApplicationException(Type.FILE_SYSTEM_ERROR, msg, e);
  }

  /**
   * The constructor of the ApplicationException.
   * @param type the type of error we have.
   * @param localizedMsg a localized string describing the problem.
   * @param rootCause the root cause of this exception.
   */
  public ApplicationException(Type type, String localizedMsg,
                              Throwable rootCause)
  {
    super(localizedMsg, rootCause);
    this.type = type;
  }

  /**
   * The constructor of the ApplicationException.
   * @param type the type of error we have.
   * @param localizedMsg a localized string describing the problem.
   * @param formattedMsg a localized message with extra formatting
   * @param rootCause the root cause of this exception.
   */
  public ApplicationException(Type type, String localizedMsg,
                              String formattedMsg, Throwable rootCause)
  {
    super(localizedMsg, rootCause);
    this.formattedMsg = formattedMsg;
    this.type = type;
  }

  /**
   * Returns the Type of this exception.
   * @return the Type of this exception.
   */
  public Type getType()
  {
    return type;
  }

  /**
   * Gets the localized message with extra formatting markup.
   * @return String representing a formatted message.
   */
  public String getFormattedMessage() {
    return formattedMsg;
  }

  /**
   * {@inheritDoc}
   */
  public String toString()
  {
    return getMessage();
  }
}
