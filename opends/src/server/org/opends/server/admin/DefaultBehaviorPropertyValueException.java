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
 * Thrown to indicate that a property's default values were invalid.
 */
public class DefaultBehaviorPropertyValueException extends PropertyException {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = 8653244240567431537L;

  // The underlying property exception that caused this exception
  private final PropertyException cause;



  /**
   * Create a new default behavior property value exception.
   *
   * @param d
   *          The property definition.
   * @param cause
   *          The property exception that caused this exception.
   */
  public DefaultBehaviorPropertyValueException(PropertyDefinition d,
      PropertyException cause) {
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
    return "The default values could not be determined "
        + "for the property \"" + getPropertyDefinition().getName() + "\"";
  }



  /**
   * Get the property exception that caused this exception.
   *
   * @return Returns the property exception that caused this exception.
   */
  public final PropertyException getPropertyException() {
    return cause;
  }

}
