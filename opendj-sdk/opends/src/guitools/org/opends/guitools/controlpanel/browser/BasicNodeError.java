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
 *      Copyright 2008 Sun Microsystems, Inc.
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
