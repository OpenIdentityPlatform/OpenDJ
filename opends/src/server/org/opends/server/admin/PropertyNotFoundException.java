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

import org.opends.messages.Message;


/**
 * Thrown when an attempt is made to retrieve a property using its name but the
 * name was not recognized.
 * <p>
 * This exception can occur when attempt is made to retrieve inherited default
 * values from a managed object.
 */
public class PropertyNotFoundException extends OperationsException {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -895548482881819610L;

  // The name of the property that could not be found.
  private final String propertyName;



  /**
   * Create a new property not found exception.
   *
   * @param propertyName
   *          The name of the property that could not be found.
   */
  public PropertyNotFoundException(String propertyName) {
    super(Message.raw("The property \"" + propertyName +
            "\" was not recognized")); // TODO: i18n
    this.propertyName = propertyName;
  }



  /**
   * Get the name of the property that could not be found.
   *
   * @return Returns the name of the property that could not be found.
   */
  public String getPropertyName() {
    return propertyName;
  }

}
