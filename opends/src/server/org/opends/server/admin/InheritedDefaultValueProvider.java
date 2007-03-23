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



import java.util.Collection;



/**
 * An interface for retrieving inherited default property values.
 */
public interface InheritedDefaultValueProvider {

  /**
   * Get the path of the managed object which should be used as the base for
   * determining parent managed objects.
   *
   * @return Returns the path of the managed object which should be used as the
   *         base for determining parent managed objects.
   */
  ManagedObjectPath getManagedObjectPath();



  /**
   * Retrieves the values of a property from a managed object at the specified
   * location.
   *
   * @param path
   *          The location of the managed object containing the property.
   * @param propertyName
   *          The name of the property containing the default values.
   * @return Returns the values of a property from a managed object at the
   *         specified location.
   * @throws OperationsException
   *           If the managed object could not be read due to some underlying
   *           communication problem.
   * @throws PropertyNotFoundException
   *           If the property name was not recognized.
   */
  Collection<?> getDefaultPropertyValues(ManagedObjectPath path,
      String propertyName) throws OperationsException,
      PropertyNotFoundException;
}
