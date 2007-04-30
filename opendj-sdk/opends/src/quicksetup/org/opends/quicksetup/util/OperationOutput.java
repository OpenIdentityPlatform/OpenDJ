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

package org.opends.quicksetup.util;

import java.util.List;

/**
 * Contains information about an operation invoked by this class.
 */
public class OperationOutput {

  private Exception exception = null;

  private List<String> errors = null;

  /**
   * Gets a list of string representing error messages obtained
   * by invoking the operation.  Null if there were no errors.
   * @return List of Strings representing errors
   */
  public List<String> getErrors() {
    return errors;
  }

  /**
   * Gets an exception that occurred by invoking the operation.  Null
   * if there were no exceptions.
   * @return Exception error
   */
  public Exception getException() {
    return exception;
  }

  /**
   * Sets the exception that occurred during execution.  Can be null to
   * indicate no exception was encountered.
   * @param exception Exception that occurred during invocation of the operation
   */
  void setException(Exception exception) {
    this.exception = exception;
  }

  /**
   * Sets the list of error messages that occurred during execution.
   * Can be null to indicate no errors were encountered.
   * @param errors List of Strings representing error messages
   */
  void setErrors(List<String> errors) {
    this.errors = errors;
  }
}
