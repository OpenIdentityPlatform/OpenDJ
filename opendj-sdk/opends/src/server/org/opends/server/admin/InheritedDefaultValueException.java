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

package org.opends.server.admin;



/**
 * Thrown to indicate that a property's inherited default values could not be
 * determined due to some underlying operations exception which occurred when
 * attempting to retrieve them.
 */
public class InheritedDefaultValueException extends PropertyException {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 7228186032995472371L;

  // The operations exception that caused default value look up to fail.
  private final OperationsException cause;



  /**
   * Create a new inherited default value exception.
   *
   * @param d
   *          The property definition.
   * @param cause
   *          The operations exception that caused default value look up to
   *          fail.
   */
  public InheritedDefaultValueException(PropertyDefinition d,
      OperationsException cause) {
    super(d);
    this.cause = cause;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Throwable getCause() {
    return cause;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getMessage() {
    return "The inherited default values could not be determined "
        + "for the property \"" + getPropertyDefinition().getName() + "\"";
  }



  /**
   * Get the operations exception that caused default value look up to fail.
   *
   * @return Returns the operations exception that caused default value look up
   *         to fail.
   */
  public final OperationsException getOperationsException() {
    return cause;
  }

}
