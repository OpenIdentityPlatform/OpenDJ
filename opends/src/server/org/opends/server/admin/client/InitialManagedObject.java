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



import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;

import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.IllegalPropertyValueException;
import org.opends.server.admin.InheritedDefaultValueProvider;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OperationsException;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyException;
import org.opends.server.admin.PropertyIsMandatoryException;
import org.opends.server.admin.PropertyIsReadOnlyException;
import org.opends.server.admin.PropertyIsSingleValuedException;
import org.opends.server.admin.PropertyNotFoundException;
import org.opends.server.admin.PropertyProvider;
import org.opends.server.admin.SingletonRelationDefinition;



/**
 * A managed object which is not bound to any underlying management
 * context.
 * <p>
 * The purpose of this managed object implementation is to facilitate
 * the construction of new configuration objects. Once all appropriate
 * properties are set, the initial managed object can be used as a
 * property provider during an invocation of
 * <code>ManagedObject#createChild</code>.
 * <p>
 * Any attempt to commit, retrieve, list, remove, or add new child
 * managed objects will result in an
 * {@link UnsupportedOperationException}.
 *
 * @param <C>
 *          The type of client configuration represented by the client
 *          managed object.
 */
public final class InitialManagedObject<C extends ConfigurationClient>
    implements ManagedObject<C> {

  /**
   * Internal inherited default value provider implementation.
   * <p>
   * FIXME: very dumb implementation which always throws a
   * PropertyNotFoundException.
   */
  private static class MyInheritedDefaultValueProvider implements
      InheritedDefaultValueProvider {

    // Private constructor.
    private MyInheritedDefaultValueProvider() {
      // No implementation required.
    }



    /**
     * {@inheritDoc}
     */
    public Collection<?> getDefaultPropertyValues(
        ManagedObjectPath path, String propertyName)
        throws OperationsException, PropertyNotFoundException {
      throw new PropertyNotFoundException(propertyName);
    }



    /**
     * {@inheritDoc}
     */
    public ManagedObjectPath getManagedObjectPath() {
      return ManagedObjectPath.emptyPath();
    }
  }

  // The managed object definition associated with this managed
  // object.
  private final ManagedObjectDefinition<C, ?> definition;

  // The managed object's properties.
  private final PropertySet properties;



  /**
   * Creates a new initial managed object associated with the
   * specified definition.
   *
   * @param definition
   *          The managed object definition.
   */
  public InitialManagedObject(ManagedObjectDefinition<C, ?> definition) {
    this.definition = definition;

    // FIXME: how will we cope with inherited default values?
    List<PropertyException> exceptions = new LinkedList<PropertyException>();
    this.properties = PropertySet.create(definition,
        PropertyProvider.DEFAULT_PROVIDER,
        new MyInheritedDefaultValueProvider(), exceptions);
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation throws an
   * {@link UnsupportedOperationException}.
   */
  public void commit() throws OperationsException {
    throw new UnsupportedOperationException();
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation throws an
   * {@link UnsupportedOperationException}.
   */
  public <M extends ConfigurationClient, N extends M>
      ManagedObject<N> createChild(
      InstantiableRelationDefinition<M, ?> r,
      ManagedObjectDefinition<N, ?> d, String name, PropertyProvider p)
      throws IllegalArgumentException, OperationsException {
    throw new UnsupportedOperationException();
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation throws an
   * {@link UnsupportedOperationException}.
   */
  public <M extends ConfigurationClient, N extends M>
      ManagedObject<N> createChild(
      OptionalRelationDefinition<M, ?> r,
      ManagedObjectDefinition<N, ?> d, PropertyProvider p)
      throws IllegalArgumentException, OperationsException {
    throw new UnsupportedOperationException();
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation throws an
   * {@link UnsupportedOperationException}.
   */
  public <M extends ConfigurationClient> ManagedObject<? extends M> getChild(
      InstantiableRelationDefinition<M, ?> d, String name)
      throws IllegalArgumentException, OperationsException {
    throw new UnsupportedOperationException();
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation throws an
   * {@link UnsupportedOperationException}.
   */
  public <M extends ConfigurationClient> ManagedObject<? extends M> getChild(
      OptionalRelationDefinition<M, ?> d)
      throws IllegalArgumentException, OperationsException {
    throw new UnsupportedOperationException();
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation throws an
   * {@link UnsupportedOperationException}.
   */
  public <M extends ConfigurationClient> ManagedObject<? extends M> getChild(
      SingletonRelationDefinition<M, ?> d)
      throws IllegalArgumentException, OperationsException {
    throw new UnsupportedOperationException();
  }



  /**
   * {@inheritDoc}
   */
  public C getConfiguration() {
    return definition.createClientConfiguration(this);
  }



  /**
   * {@inheritDoc}
   */
  public ManagedObjectDefinition<C, ?> getManagedObjectDefinition() {
    return definition;
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation throws an
   * {@link UnsupportedOperationException}.
   */
  public ManagedObjectPath getManagedObjectPath() {
    throw new UnsupportedOperationException();
  }



  /**
   * {@inheritDoc}
   */
  public <T> T getPropertyValue(PropertyDefinition<T> d)
      throws IllegalArgumentException {
    return properties.getPropertyValue(d);
  }



  /**
   * {@inheritDoc}
   */
  public <T> SortedSet<T> getPropertyValues(PropertyDefinition<T> d)
      throws IllegalArgumentException {
    return properties.getPropertyValues(d);
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation throws an
   * {@link UnsupportedOperationException}.
   */
  public boolean hasChild(OptionalRelationDefinition<?, ?> d)
      throws IllegalArgumentException, OperationsException {
    throw new UnsupportedOperationException();
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation throws an
   * {@link UnsupportedOperationException}.
   */
  public String[] listChildren(InstantiableRelationDefinition<?, ?> d)
      throws IllegalArgumentException, OperationsException {
    throw new UnsupportedOperationException();
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation throws an
   * {@link UnsupportedOperationException}.
   */
  public <M extends ConfigurationClient> void removeChild(
      InstantiableRelationDefinition<M, ?> d, String name)
      throws IllegalArgumentException, OperationsException {
    throw new UnsupportedOperationException();
  }



  /**
   * {@inheritDoc}
   * <p>
   * This implementation throws an
   * {@link UnsupportedOperationException}.
   */
  public <M extends ConfigurationClient> void removeChild(
      OptionalRelationDefinition<M, ?> d)
      throws IllegalArgumentException, OperationsException {
    throw new UnsupportedOperationException();

  }



  /**
   * {@inheritDoc}
   */
  public <T> void setPropertyValue(PropertyDefinition<T> d, T value)
      throws IllegalPropertyValueException,
      PropertyIsReadOnlyException, PropertyIsMandatoryException,
      IllegalArgumentException {
    properties.setPropertyValue(d, value);
  }



  /**
   * {@inheritDoc}
   */
  public <T> void setPropertyValues(PropertyDefinition<T> d,
      Collection<T> values) throws IllegalPropertyValueException,
      PropertyIsSingleValuedException, PropertyIsReadOnlyException,
      PropertyIsMandatoryException, IllegalArgumentException {
    properties.setPropertyValues(d, values);
  }

}
