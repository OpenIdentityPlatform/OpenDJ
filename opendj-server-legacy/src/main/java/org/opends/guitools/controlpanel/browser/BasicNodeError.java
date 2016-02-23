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
 * Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.guitools.controlpanel.browser;


/**
 * Error record.
 * We group all the error variables in one class to decrease the number of
 * variables in BasicNode.
 */
public class BasicNodeError {
  private NodeRefresher.State state;
  private Exception exception;
  private Object arg;

  /**
   * The constructor of the BasicNodeError.
   * @param state the state of the refresher when the exception occurred.
   * @param x the exception.
   * @param arg the argument of the exception.
   */
  public BasicNodeError(NodeRefresher.State state, Exception x, Object arg) {
    this.state = state;
    exception = x;
    this.arg = arg;
  }

  /**
   * Returns the state of the refresher when the exception occurred.
   * @return the state of the refresher when the exception occurred.
   */
  public NodeRefresher.State getState() {
    return state;
  }

  /**
   * Returns the exception.
   * @return the exception.
   */
  public Exception getException() {
    return exception;
  }

  /**
   * Returns the argument of the exception.
   * @return the argument of the exception.
   */
  public Object getArg() {
    return arg;
  }
}
