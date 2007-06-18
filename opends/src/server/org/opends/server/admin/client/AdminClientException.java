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

package org.opends.server.admin.client;



import org.opends.server.admin.AdminException;



/**
 * Administration client exceptions represent non-operational problems
 * which occur whilst interacting with the administration framework.
 * They provide clients with a transport independent interface for
 * handling transport related exceptions.
 * <p>
 * Client exceptions represent communications problems, security
 * problems, and service related problems.
 */
public class AdminClientException extends AdminException {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 4044747533980824456L;



  /**
   * Create an administration client exception.
   */
  public AdminClientException() {
    // No implementation required.
  }



  /**
   * Create an administration client exception with a cause.
   *
   * @param cause
   *          The cause.
   */
  public AdminClientException(Throwable cause) {
    super(cause.getMessage(), cause);
  }



  /**
   * Create an administration client exception with a message and
   * cause.
   *
   * @param message
   *          The message.
   * @param cause
   *          The cause.
   */
  public AdminClientException(String message, Throwable cause) {
    super(message, cause);
  }



  /**
   * Create an administration client exception with a message.
   *
   * @param message
   *          The message.
   */
  public AdminClientException(String message) {
    super(message);
  }
}
