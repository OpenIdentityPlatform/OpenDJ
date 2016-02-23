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
 * Portions Copyright 2014-2015 ForgeRock AS.
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
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.util.Reject;



/**
 * A condition which evaluates to <code>true</code> if and only if a
 * particular property has any values specified.
 */
public final class IsPresentCondition implements Condition {

  /** The property name. */
  private final String propertyName;

  /** The property definition. */
  private PropertyDefinition<?> pd;



  /**
   * Creates a new is present condition.
   *
   * @param propertyName
   *          The property name.
   */
  public IsPresentCondition(String propertyName) {
    Reject.ifNull(propertyName);
    this.propertyName = propertyName;
  }



  /** {@inheritDoc} */
  public boolean evaluate(ManagementContext context,
      ManagedObject<?> managedObject) throws AuthorizationException,
      CommunicationException {
    SortedSet<?> values = managedObject.getPropertyValues(pd);
    return !values.isEmpty();
  }



  /** {@inheritDoc} */
  public boolean evaluate(ServerManagedObject<?> managedObject)
      throws ConfigException {
    SortedSet<?> values = managedObject.getPropertyValues(pd);
    return !values.isEmpty();
  }



  /** {@inheritDoc} */
  public void initialize(AbstractManagedObjectDefinition<?, ?> d)
      throws Exception {
    // Decode the property.
    this.pd = d.getPropertyDefinition(propertyName);
  }

}
