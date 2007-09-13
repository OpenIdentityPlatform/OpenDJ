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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.server.tools.tasks;

import org.opends.messages.Message;

import org.opends.server.types.OpenDsException;

/**
 * Exception for problems related to interacting with the task backend.
 */
public class TaskClientException extends OpenDsException {

  private static final long serialVersionUID = 3800881643050096416L;

  /**
   * Constructs a default instance.
   */
  public TaskClientException() {
  }

  /**
   * Constructs a parameterized instance.
   *
   * @param cause of this exception
   */
  public TaskClientException(OpenDsException cause) {
    super(cause);
  }

  /**
   * Constructs a parameterized instance.
   *
   * @param message for this exception
   */
  public TaskClientException(Message message) {
    super(message);
  }

  /**
   * Constructs a parameterized instance.
   *
   * @param cause of this exception
   */
  public TaskClientException(Throwable cause) {
    super(cause);
  }

  /**
   * Constructs a parameterized instance.
   *
   * @param message for this exception
   * @param cause of this exception
   */
  public TaskClientException(Message message, Throwable cause) {
    super(message, cause);
  }

}
