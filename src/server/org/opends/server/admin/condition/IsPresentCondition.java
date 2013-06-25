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
package org.opends.server.admin.condition;



import java.util.SortedSet;

import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.server.ServerManagedObject;
import org.opends.server.config.ConfigException;
import org.opends.server.util.Validator;



/**
 * A condition which evaluates to <code>true</code> if and only if a
 * particular property has any values specified.
 */
public final class IsPresentCondition implements Condition {

  // The property name.
  private final String propertyName;

  // The property definition.
  private PropertyDefinition<?> pd;



  /**
   * Creates a new is present condition.
   *
   * @param propertyName
   *          The property name.
   */
  public IsPresentCondition(String propertyName) {
    Validator.ensureNotNull(propertyName);
    this.propertyName = propertyName;
  }



  /**
   * {@inheritDoc}
   */
  public boolean evaluate(ManagementContext context,
      ManagedObject<?> managedObject) throws AuthorizationException,
      CommunicationException {
    SortedSet<?> values = managedObject.getPropertyValues(pd);
    return !values.isEmpty();
  }



  /**
   * {@inheritDoc}
   */
  public boolean evaluate(ServerManagedObject<?> managedObject)
      throws ConfigException {
    SortedSet<?> values = managedObject.getPropertyValues(pd);
    return !values.isEmpty();
  }



  /**
   * {@inheritDoc}
   */
  public void initialize(AbstractManagedObjectDefinition<?, ?> d)
      throws Exception {
    // Decode the property.
    this.pd = d.getPropertyDefinition(propertyName);
  }

}
