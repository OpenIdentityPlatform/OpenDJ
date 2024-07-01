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
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.browser;

/**
 * The exception used by the NodeRefresher when the refresh process is
 * cancelled, interrupted or something failed.
 */
class SearchAbandonException extends Exception {

  private static final long serialVersionUID = 7768798649278383859L;
  private final NodeRefresher.State state;
  private final Exception x;
  private final Object arg;

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
