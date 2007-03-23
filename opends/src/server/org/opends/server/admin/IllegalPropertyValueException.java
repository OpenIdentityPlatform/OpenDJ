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
 * Thrown to indicate that a property value was invalid according to its
 * associated property definition.
 */
public class IllegalPropertyValueException extends PropertyException {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -3145632074909281823L;

  // The illegal property value.
  private final Object value;



  /**
   * Create a new illegal property value exception.
   *
   * @param d
   *          The property definition.
   * @param value
   *          The illegal property value.
   */
  public IllegalPropertyValueException(PropertyDefinition d, Object value) {
    super(d);
    this.value = value;
  }



  /**
   * Get the illegal property value that caused the exception.
   *
   * @return Returns the illegal property value.
   */
  public final Object getIllegalValue() {
    return value;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String getMessage() {
    return "The value \"" + String.valueOf(value)
        + "\" is not a valid value for the property \""
        + getPropertyDefinition().getName() + "\"";
  }

}
