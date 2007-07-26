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
 * This exception is thrown when a property's default values cannot be
 * determined. This can occur in the following situations:
 * <ul>
 * <li>the property has a well-defined set of default values but they
 * are invalid according to the property's syntax
 * <li>the property inherits its default values from another managed
 * object but they could not be retrieved, perhaps because of a
 * communication problem.
 * </ul>
 */
public class DefaultBehaviorException extends PropertyException {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -2542117466747573053L;

  // The cause of this exception.
  private final Throwable cause;



  /**
   * Create a new default behavior exception with a cause.
   *
   * @param pd
   *          The property definition whose default values could not
   *          be determined.
   * @param cause
   *          The exception that prevented the default values from
   *          being determined.
   */
  public DefaultBehaviorException(PropertyDefinition<?> pd, Throwable cause) {
    super(pd);
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
}
