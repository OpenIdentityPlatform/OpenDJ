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

package org.opends.testqa.monitoringclient;

import java.util.Date;
import java.util.Vector;

/**
 * Buffer to store the errors who occured
 * while the monitoring client is running.
 */
public class ErrorsBuffer {

  /**
   * Main class of the client.
   */
  private MonitoringClient client;

  /**
   * Errors who occured when the client is running.
   */
  private Vector<Error> errors;

  /**
   * Number of consumers who have cloned the buffer.
   */
  private int consumers;

  /**
   * Construct a ErrorsBuffer object.
   *
   * @param client The main class of the client.
   */
  public ErrorsBuffer (MonitoringClient client) {
    this.client = client;
    errors = new Vector<Error>();
    consumers = 0;
  }

  /**
   * Add a general error in the buffer and notify the consumers.
   *
   * @param protocol  The name of the protocol.
   * @param message   The message of the error.
   * @param date      The date of the error.
   * @return  true if the error have been add in the buffer, false otherwise.
   */
  public synchronized boolean addError(String protocol, String message,
          Date date) {
    boolean result = errors.add(new Error(protocol, "", message,
            new Date(date.getTime())));
    if (result) notifyAll();
    return result;
  }

  /**
   * Add an error in the buffer and notify the consumers.
   *
   * @param protocol  The name of the protocol.
   * @param attribute The name of the attribute monitored.
   * @param message   The message of the error.
   * @return  true if the error have been add in the buffer, false otherwise.
   */
  public synchronized boolean addError(String protocol, String attribute,
          String message) {
    boolean result = errors.add(new Error(protocol, attribute, message));
    if (result) notifyAll();
    return result;
  }

  /**
   * Add an error in the buffer and notify the consumers.
   *
   * @param protocol  The name of the protocol.
   * @param attribute The name of the attribute monitored.
   * @param message   The message of the error.
   * @param date      The date of the error.
   * @return  true if the error have been add in the buffer, false otherwise.
   */
  public synchronized boolean addError(String protocol, String attribute,
          String message, Date date) {
    boolean result = errors.add(new Error(protocol, attribute, message,
            new Date(date.getTime())));
    if (result) notifyAll();
    return result;
  }

  /**
   * Add an error in the buffer.
   *
   * @param protocol  The name of the protocol.
   * @param attribute The name of the attribute monitored.
   * @param message   The message of the error.
   * @param date      The date of the error.
   * @return  true if the error have been add in the buffer, false otherwise.
   */
  private synchronized boolean addErrorWithoutNotify(String protocol,
          String attribute, String message, Date date) {
    return errors.add(new Error(protocol, attribute, message,
            new Date(date.getTime())));
  }

  /**
   * Test if there is at least one error in the buffer.
   *
   * @return true if there is at least one error in the buffer, false otherwise.
   */
  public synchronized boolean isEmpty(){
    return errors.isEmpty();
  }

  /**
   * Return the first error in the buffer and remove it.
   *
   * @return  The first error in the buffer.
   */
  public synchronized Error removeFirstError() {
    return errors.remove(0);
  }

  /**
   * Clone the ErrorsBuffer and clear it if all the consumers have cloned it.
   *
   * @return  A clone of the ErrorsBuffer.
   */
  @Override
  public synchronized ErrorsBuffer clone () {
    ErrorsBuffer result = new ErrorsBuffer(client);
    for (Error e : errors) {
      result.addErrorWithoutNotify(e.getProtocol(), e.getAttribute(),
              e.getMessage(), new Date(e.getDate().getTime()));
    }
    if (++consumers == client.getNbConsumers()) {
      errors.clear();
      consumers = 0;
    }
    return result;
  }

}
