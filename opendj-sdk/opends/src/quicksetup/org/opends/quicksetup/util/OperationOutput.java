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
import java.util.ArrayList;
import java.util.Collections;

/**
 * Contains information about an operation invoked by this class.
 */
public class OperationOutput {

  private Exception exception = null;

  private List<String> errorMessages = new ArrayList<String>();
  private List<String> debugMessages = new ArrayList<String>();
  private List<String> accessMessages = new ArrayList<String>();

  /**
   * Gets a list of strings representing error messages obtained
   * by invoking the operation that match <code>regex</code>.
   * @param regex String used to find particular error messages
   * @return List of Strings representing errorMessages that contain
   * the provided <code>regex</code> string.
   */
  public List<String> getErrorMessages(String regex) {
    List<String> errorMessagesSubset = new ArrayList<String>();
    for (String msg : errorMessages) {
      if (msg.matches(regex)) {
        errorMessagesSubset.add(msg);
      }
    }
    return Collections.unmodifiableList(errorMessagesSubset);
  }

  /**
   * Gets a list of strings representing error messages obtained
   * by invoking the operation.
   * @return List of Strings representing errorMessages
   */
  public List<String> getErrorMessages() {
    return Collections.unmodifiableList(errorMessages);
  }

  /**
   * Gets a list of strings representing error messages obtained
   * by invoking the operation.
   * @return List of Strings representing errorMessages
   */
  public List<String> getDebugMessages() {
    return Collections.unmodifiableList(debugMessages);
  }

  /**
   * Gets a list of strings representing error messages obtained
   * by invoking the operation.
   * @return List of Strings representing errorMessages
   */
  public List<String> getAccessMessages() {
    return Collections.unmodifiableList(accessMessages);
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
   * Adds an error message.
   * @param errorMessage an error message
   */
  void addErrorMessage(String errorMessage) {
    this.errorMessages.add(errorMessage);
  }

  /**
   * Adds an access message.
   * @param accessMessage an error message
   */
  void addAccessMessage(String accessMessage) {
    this.accessMessages.add(accessMessage);
  }

  /**
   * Adds an error message.
   * @param debugMessage an error message
   */
  void addDebugMessage(String debugMessage) {
    this.debugMessages.add(debugMessage);
  }


  /**
   * Sets the list of error messages that occurred during execution.
   * @param accessMessages List of Strings representing error messages
   */
  void setAccessMessages(List<String> accessMessages) {
    this.accessMessages = accessMessages;
  }

  /**
   * Sets the list of error messages that occurred during execution.
   * @param debugMessages List of Strings representing error messages
   */
  void setDebugMessages(List<String> debugMessages) {
    this.debugMessages = debugMessages;
  }

}
