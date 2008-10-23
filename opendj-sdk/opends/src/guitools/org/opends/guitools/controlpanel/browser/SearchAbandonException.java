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
 * The exception used by the NodeRefresher when the refresh process is
 * cancelled, interrupted or something failed.
 *
 */
class SearchAbandonException extends Exception {

  private static final long serialVersionUID = 7768798649278383859L;
  private NodeRefresher.State state;
  private Exception x;
  private Object arg;

  /**
   * The constructor for the class.
   * @param state the state in which the refresher is.
   * @param x the exception.
   * @param arg the argument for the exception.
   */
  SearchAbandonException(NodeRefresher.State state, Exception x, Object arg) {
    this.state = state;
    this.x = x;
    this.arg = arg;
  }

  /**
   * Returns the state the refresher was when the exception occurred.
   * @return the state the refresher was when the exception occurred.
   */
  NodeRefresher.State getState() {
    return state;
  }

  /**
   * Returns the exception.
   * @return the exception.
   */
  Exception getException() {
    return x;
  }

  /**
   * Returns the argument of the exception.
   * @return the argument of the exception.
   */
  Object getArg() {
    return arg;
  }
}
