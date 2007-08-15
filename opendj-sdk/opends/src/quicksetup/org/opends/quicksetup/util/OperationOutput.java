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
import org.opends.messages.Message;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Contains information about an operation invoked by this class.
 */
public class OperationOutput {

  private int returnCode = -1;

  private Exception exception = null;

  private List<Message> outputMessages = new ArrayList<Message>();
  private List<Message> errorMessages = new ArrayList<Message>();
  private List<Message> debugMessages = new ArrayList<Message>();
  private List<Message> accessMessages = new ArrayList<Message>();

  /**
   * Gets a list of strings representing error messages obtained
   * by invoking the operation that match <code>regex</code>.
   * @param regex String used to find particular error messages
   * @return List of Strings representing errorMessages that contain
   * the provided <code>regex</code> string.
   */
  public List<Message> getErrorMessages(String regex) {
    List<Message> errorMessagesSubset = new ArrayList<Message>();
    for (Message msg : errorMessages) {
      if (msg.toString().matches(regex)) {
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
  public List<Message> getErrorMessages() {
    return Collections.unmodifiableList(errorMessages);
  }

  /**
   * Gets a list of strings representing output messages obtained
   * by invoking the operation.
   * @return List of Strings representing errorMessages
   */
  public List<Message> getOutputMessages() {
    return Collections.unmodifiableList(outputMessages);
  }

  /**
   * Gets a list of strings representing error messages obtained
   * by invoking the operation.
   * @return List of Strings representing errorMessages
   */
  public List<Message> getDebugMessages() {
    return Collections.unmodifiableList(debugMessages);
  }

  /**
   * Gets a list of strings representing error messages obtained
   * by invoking the operation.
   * @return List of Strings representing errorMessages
   */
  public List<Message> getAccessMessages() {
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
   * Gets the return code produced by the operation if any.
   * @return int representing any return code returned by the
   * operation.  -1 indicates no return code was set.
   */
  public int getReturnCode() {
    return this.returnCode;
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
  void addErrorMessage(Message errorMessage) {
    this.errorMessages.add(errorMessage);
  }

  /**
   * Adds an output message.
   * @param outputMessage an error message
   */
  void addOutputMessage(Message outputMessage) {
    this.outputMessages.add(outputMessage);
  }

  /**
   * Adds an access message.
   * @param accessMessage an error message
   */
  void addAccessMessage(Message accessMessage) {
    this.accessMessages.add(accessMessage);
  }

  /**
   * Adds an error message.
   * @param debugMessage an error message
   */
  void addDebugMessage(Message debugMessage) {
    this.debugMessages.add(debugMessage);
  }


  /**
   * Sets the list of error messages that occurred during execution.
   * @param accessMessages List of Strings representing error messages
   */
  void setAccessMessages(List<Message> accessMessages) {
    this.accessMessages = accessMessages;
  }

  /**
   * Sets the list of error messages that occurred during execution.
   * @param debugMessages List of Strings representing error messages
   */
  void setDebugMessages(List<Message> debugMessages) {
    this.debugMessages = debugMessages;
  }

  /**
   * Sets the return code of the operation.
   * @param i int representing the return code
   */
  void setReturnCode(int i) {
    this.returnCode = i;
  }
}
