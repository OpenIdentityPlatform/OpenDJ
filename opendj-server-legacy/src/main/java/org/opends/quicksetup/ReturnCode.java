/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.quicksetup;

/** This class defines enumeration of application return code. */
public class ReturnCode {

  /** Return code: Application successful. */
  public static final ReturnCode SUCCESSFUL = new ReturnCode(0);
  /** Return code: User Cancelled operation. */
  public static final ReturnCode CANCELED = new ReturnCode(0);
  /** Return code: User provided invalid data. */
  public static final ReturnCode USER_DATA_ERROR = new ReturnCode(2);
  /** Return code: Error accessing file system (reading/writing). */
  public static final ReturnCode FILE_SYSTEM_ACCESS_ERROR = new ReturnCode(3);
  /** Error during the configuration of the Directory Server. */
  public static final ReturnCode CONFIGURATION_ERROR = new ReturnCode(5);
  /**
   * Error during the import of data (base entry, from LDIF file or
   * automatically generated data).
   */
  public static final ReturnCode IMPORT_ERROR = new ReturnCode(6);
  /** Error starting the Open DS server. */
  public static final ReturnCode START_ERROR = new ReturnCode(7);
  /** Error stopping the Open DS server. */
  public static final ReturnCode STOP_ERROR = new ReturnCode(8);
  /** Error enabling the Windows service. */
  public static final ReturnCode WINDOWS_SERVICE_ERROR = new ReturnCode(9);
  /** Application specific error. */
  public static final ReturnCode APPLICATION_ERROR = new ReturnCode(10);
  /** Error invoking an OpenDS tool. */
  public static final ReturnCode TOOL_ERROR = new ReturnCode(11);
  /** Return code: Bug. */
  public static final ReturnCode BUG = new ReturnCode(12);
  /** Return code: java version non-compatible. */
  public static final ReturnCode JAVA_VERSION_INCOMPATIBLE = new ReturnCode(13);
  /** Return code: user provided invalid input. */
  public static final ReturnCode USER_INPUT_ERROR = new ReturnCode(14);
  /** Return code: Print Version. */
  public static final ReturnCode PRINT_VERSION = new ReturnCode(50);
  /** Return code for errors that are non-specified. */
  public static final ReturnCode UNKNOWN = new ReturnCode(100);

  private final int code;

  /**
   * Creates a new parameterized instance.
   *
   * @param code to return
   */
  private ReturnCode(int code)
  {
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
