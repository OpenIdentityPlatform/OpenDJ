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
import org.opends.server.admin.IllegalPropertyValueStringException;
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
 * property contains a particular value.
 */
public final class ContainsCondition implements Condition {

  /**
   * The strongly typed underlying implementation.
   *
   * @param <T>
   *          The type of the property value being tested.
   */
  private static final class Impl<T> implements Condition {

    // The property.
    private final PropertyDefinition<T> pd;

    // The required property value.
    private final T value;



    // Private constructor.
    private Impl(PropertyDefinition<T> pd, T value)
        throws IllegalPropertyValueStringException {
      this.pd = pd;
      this.value = value;
    }



    /**
     * {@inheritDoc}
     */
    public boolean evaluate(ManagementContext context,
        ManagedObject<?> managedObject) throws AuthorizationException,
        CommunicationException {
      SortedSet<T> values = managedObject.getPropertyValues(pd);
      return values.contains(value);
    }



    /**
     * {@inheritDoc}
     */
    public boolean evaluate(ServerManagedObject<?> managedObject)
        throws ConfigException {
      SortedSet<T> values = managedObject.getPropertyValues(pd);
      return values.contains(value);
    }



    /**
     * {@inheritDoc}
     */
    public void initialize(AbstractManagedObjectDefinition<?, ?> d)
        throws Exception {
      // Not used.
    }



    // Private implementation of fix() method.
    private void setPropertyValue(ManagedObject<?> managedObject) {
      managedObject.setPropertyValue(pd, value);
    }

  }

  // The strongly typed private implementation.
  private Impl<?> impl = null;

  // The property name.
  private final String propertyName;

  // The string representation of the required property value.
  private final String propertyStringValue;



  /**
   * Creates a new contains value condition.
   *
   * @param propertyName
   *          The property name.
   * @param stringValue
   *          The string representation of the required property
   *          value.
   */
  public ContainsCondition(String propertyName, String stringValue) {
    Validator.ensureNotNull(propertyName, stringValue);
    this.propertyName = propertyName;
    this.propertyStringValue = stringValue;
  }



  /**
   * {@inheritDoc}
   */
  public boolean evaluate(ManagementContext context,
      ManagedObject<?> managedObject) throws AuthorizationException,
      CommunicationException {
    return impl.evaluate(context, managedObject);
  }



  /**
   * {@inheritDoc}
   */
  public boolean evaluate(ServerManagedObject<?> managedObject)
      throws ConfigException {
    return impl.evaluate(managedObject);
  }



  /**
   * Modifies the provided managed object so that it has the property
   * value associated with this condition.
   *
   * @param managedObject
   *          The managed object.
   */
  public void setPropertyValue(ManagedObject<?> managedObject) {
    impl.setPropertyValue(managedObject);
  }



  /**
   * {@inheritDoc}
   */
  public void initialize(AbstractManagedObjectDefinition<?, ?> d)
      throws Exception {
    // Decode the property.
    buildImpl(d.getPropertyDefinition(propertyName));
  }



  // Creates the new private implementation.
  private <T> void buildImpl(PropertyDefinition<T> pd)
      throws IllegalPropertyValueStringException {
    T value = pd.decodeValue(propertyStringValue);
    this.impl = new Impl<T>(pd, value);
  }

}
