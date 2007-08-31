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
package org.opends.server.admin.client.spi;



import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.DefaultBehaviorException;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.IllegalPropertyValueException;
import org.opends.server.admin.IllegalPropertyValueStringException;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectAlreadyExistsException;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyIsMandatoryException;
import org.opends.server.admin.PropertyIsReadOnlyException;
import org.opends.server.admin.PropertyIsSingleValuedException;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.SingletonRelationDefinition;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.IllegalManagedObjectNameException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.MissingMandatoryPropertiesException;
import org.opends.server.admin.client.OperationRejectedException;



/**
 * An abstract managed object implementation.
 *
 * @param <T>
 *          The type of client configuration represented by the client
 *          managed object.
 */
public abstract class AbstractManagedObject<T extends ConfigurationClient>
    implements ManagedObject<T> {

  // The managed object definition associated with this managed
  // object.
  private final ManagedObjectDefinition<T, ? extends Configuration> definition;

  // Indicates whether or not this managed object exists on the server
  // (false means the managed object is new and has not been
  // committed).
  private boolean existsOnServer;

  // Optional naming property definition.
  private final PropertyDefinition<?> namingPropertyDefinition;

  // The path associated with this managed object.
  private ManagedObjectPath<T, ? extends Configuration> path;

  // The managed object's properties.
  private final PropertySet properties;



  /**
   * Creates a new abstract managed object.
   *
   * @param d
   *          The managed object's definition.
   * @param path
   *          The managed object's path.
   * @param properties
   *          The managed object's properties.
   * @param existsOnServer
   *          Indicates whether or not the managed object exists on
   *          the server (false means the managed object is new and
   *          has not been committed).
   * @param namingPropertyDefinition
   *          Optional naming property definition.
   */
  protected AbstractManagedObject(
      ManagedObjectDefinition<T, ? extends Configuration> d,
      ManagedObjectPath<T, ? extends Configuration> path,
      PropertySet properties, boolean existsOnServer,
      PropertyDefinition<?> namingPropertyDefinition) {
    this.definition = d;
    this.path = path;
    this.properties = properties;
    this.existsOnServer = existsOnServer;
    this.namingPropertyDefinition = namingPropertyDefinition;
  }



  /**
   * {@inheritDoc}
   */
  public final void commit() throws ManagedObjectAlreadyExistsException,
      MissingMandatoryPropertiesException, ConcurrentModificationException,
      OperationRejectedException, AuthorizationException,
      CommunicationException {
    // First make sure all mandatory properties are defined.
    List<PropertyIsMandatoryException> exceptions =
      new LinkedList<PropertyIsMandatoryException>();

    for (PropertyDefinition<?> pd : definition.getAllPropertyDefinitions()) {
      Property<?> p = getProperty(pd);
      if (pd.hasOption(PropertyOption.MANDATORY)
          && p.getEffectiveValues().isEmpty()) {
        exceptions.add(new PropertyIsMandatoryException(pd));
      }
    }

    if (!exceptions.isEmpty()) {
      throw new MissingMandatoryPropertiesException(exceptions);
    }

    // Commit the managed object.
    if (existsOnServer) {
      modifyExistingManagedObject();
    } else {
      addNewManagedObject();
      existsOnServer = true;
    }

    // Make all pending property values active.
    properties.commit();
  }



  /**
   * {@inheritDoc}
   */
  public final <C extends ConfigurationClient, S extends Configuration,
                CC extends C>
  ManagedObject<CC> createChild(
      InstantiableRelationDefinition<C, S> r,
      ManagedObjectDefinition<CC, ? extends S> d, String name,
      Collection<DefaultBehaviorException> exceptions)
      throws IllegalManagedObjectNameException, IllegalArgumentException {
    validateRelationDefinition(r);

    // Empty names are not allowed.
    if (name.trim().length() == 0) {
      throw new IllegalManagedObjectNameException(name);
    }

    // If the relation uses a naming property definition then it must
    // be a valid value.
    PropertyDefinition<?> pd = r.getNamingPropertyDefinition();
    if (pd != null) {
      try {
        pd.decodeValue(name);
      } catch (IllegalPropertyValueStringException e) {
        throw new IllegalManagedObjectNameException(name, pd);
      }
    }

    ManagedObjectPath<CC, ? extends S> childPath = path.child(r, d, name);
    return createNewManagedObject(d, childPath, pd, name, exceptions);
  }



  /**
   * {@inheritDoc}
   */
  public final <C extends ConfigurationClient,
                S extends Configuration, CC extends C>
  ManagedObject<CC> createChild(
      OptionalRelationDefinition<C, S> r,
      ManagedObjectDefinition<CC, ? extends S> d,
      Collection<DefaultBehaviorException> exceptions)
      throws IllegalArgumentException {
    validateRelationDefinition(r);
    ManagedObjectPath<CC, ? extends S> childPath = path.child(r, d);
    return createNewManagedObject(d, childPath, null, null, exceptions);
  }



  /**
   * {@inheritDoc}
   */
  public final <C extends ConfigurationClient, S extends Configuration>
  ManagedObject<? extends C> getChild(
      InstantiableRelationDefinition<C, S> r, String name)
      throws IllegalArgumentException, DefinitionDecodingException,
      ManagedObjectDecodingException, ManagedObjectNotFoundException,
      ConcurrentModificationException, AuthorizationException,
      CommunicationException {
    validateRelationDefinition(r);
    ensureThisManagedObjectExists();
    Driver ctx = getDriver();
    return ctx.getManagedObject(path.child(r, name));
  }



  /**
   * {@inheritDoc}
   */
  public final <C extends ConfigurationClient, S extends Configuration>
  ManagedObject<? extends C> getChild(
      OptionalRelationDefinition<C, S> r) throws IllegalArgumentException,
      DefinitionDecodingException, ManagedObjectDecodingException,
      ManagedObjectNotFoundException, ConcurrentModificationException,
      AuthorizationException, CommunicationException {
    validateRelationDefinition(r);
    ensureThisManagedObjectExists();
    Driver ctx = getDriver();
    return ctx.getManagedObject(path.child(r));
  }



  /**
   * {@inheritDoc}
   */
  public final <C extends ConfigurationClient, S extends Configuration>
  ManagedObject<? extends C> getChild(
      SingletonRelationDefinition<C, S> r) throws IllegalArgumentException,
      DefinitionDecodingException, ManagedObjectDecodingException,
      ManagedObjectNotFoundException, ConcurrentModificationException,
      AuthorizationException, CommunicationException {
    validateRelationDefinition(r);
    ensureThisManagedObjectExists();
    Driver ctx = getDriver();
    return ctx.getManagedObject(path.child(r));
  }



  /**
   * {@inheritDoc}
   */
  public final T getConfiguration() {
    return definition.createClientConfiguration(this);
  }



  /**
   * {@inheritDoc}
   */
  public final ManagedObjectDefinition<T, ? extends Configuration>
  getManagedObjectDefinition() {
    return definition;
  }



  /**
   * {@inheritDoc}
   */
  public final ManagedObjectPath<T, ? extends Configuration>
  getManagedObjectPath() {
    return path;
  }



  /**
   * {@inheritDoc}
   */
  public final <PD> SortedSet<PD> getPropertyDefaultValues(
      PropertyDefinition<PD> pd) throws IllegalArgumentException {
    return new TreeSet<PD>(getProperty(pd).getDefaultValues());
  }



  /**
   * {@inheritDoc}
   */
  public final <PD> PD getPropertyValue(PropertyDefinition<PD> pd)
      throws IllegalArgumentException {
    Set<PD> values = getProperty(pd).getEffectiveValues();
    if (values.isEmpty()) {
      return null;
    } else {
      return values.iterator().next();
    }
  }



  /**
   * {@inheritDoc}
   */
  public final <PD> SortedSet<PD> getPropertyValues(PropertyDefinition<PD> pd)
      throws IllegalArgumentException {
    return new TreeSet<PD>(getProperty(pd).getEffectiveValues());
  }



  /**
   * {@inheritDoc}
   */
  public final <C extends ConfigurationClient, S extends Configuration>
  boolean hasChild(
      OptionalRelationDefinition<C, S> r) throws IllegalArgumentException,
      ConcurrentModificationException, AuthorizationException,
      CommunicationException {
    validateRelationDefinition(r);
    Driver ctx = getDriver();
    try {
      return ctx.hasManagedObject(path, r);
    } catch (ManagedObjectNotFoundException e) {
      throw new ConcurrentModificationException();
    }
  }



  /**
   * {@inheritDoc}
   */
  public final boolean isPropertyPresent(PropertyDefinition<?> pd)
      throws IllegalArgumentException {
    return !getProperty(pd).isEmpty();
  }



  /**
   * {@inheritDoc}
   */
  public final <C extends ConfigurationClient, S extends Configuration>
  String[] listChildren(
      InstantiableRelationDefinition<C, S> r) throws IllegalArgumentException,
      ConcurrentModificationException, AuthorizationException,
      CommunicationException {
    return listChildren(r, r.getChildDefinition());
  }



  /**
   * {@inheritDoc}
   */
  public final <C extends ConfigurationClient, S extends Configuration>
  String[] listChildren(
      InstantiableRelationDefinition<C, S> r,
      AbstractManagedObjectDefinition<? extends C, ? extends S> d)
      throws IllegalArgumentException, ConcurrentModificationException,
      AuthorizationException, CommunicationException {
    validateRelationDefinition(r);
    Driver ctx = getDriver();
    try {
      return ctx.listManagedObjects(path, r, d);
    } catch (ManagedObjectNotFoundException e) {
      throw new ConcurrentModificationException();
    }
  }



  /**
   * {@inheritDoc}
   */
  public final <C extends ConfigurationClient, S extends Configuration>
  void removeChild(
      InstantiableRelationDefinition<C, S> r, String name)
      throws IllegalArgumentException, ManagedObjectNotFoundException,
      OperationRejectedException, ConcurrentModificationException,
      AuthorizationException, CommunicationException {
    validateRelationDefinition(r);
    Driver ctx = getDriver();
    boolean found;

    try {
      found = ctx.deleteManagedObject(path, r, name);
    } catch (ManagedObjectNotFoundException e) {
      throw new ConcurrentModificationException();
    }

    if (!found) {
      throw new ManagedObjectNotFoundException();
    }
  }



  /**
   * {@inheritDoc}
   */
  public final <C extends ConfigurationClient, S extends Configuration>
  void removeChild(
      OptionalRelationDefinition<C, S> r) throws IllegalArgumentException,
      ManagedObjectNotFoundException, OperationRejectedException,
      ConcurrentModificationException, AuthorizationException,
      CommunicationException {
    validateRelationDefinition(r);
    Driver ctx = getDriver();
    boolean found;

    try {
      found = ctx.deleteManagedObject(path, r);
    } catch (ManagedObjectNotFoundException e) {
      throw new ConcurrentModificationException();
    }

    if (!found) {
      throw new ManagedObjectNotFoundException();
    }
  }



  /**
   * {@inheritDoc}
   */
  public final <PD> void setPropertyValue(PropertyDefinition<PD> pd, PD value)
      throws IllegalPropertyValueException, PropertyIsReadOnlyException,
      PropertyIsMandatoryException, IllegalArgumentException {
    if (value == null) {
      setPropertyValues(pd, Collections.<PD> emptySet());
    } else {
      setPropertyValues(pd, Collections.singleton(value));
    }
  }



  /**
   * {@inheritDoc}
   */
  public final <PD> void setPropertyValues(PropertyDefinition<PD> pd,
      Collection<PD> values) throws IllegalPropertyValueException,
      PropertyIsSingleValuedException, PropertyIsReadOnlyException,
      PropertyIsMandatoryException, IllegalArgumentException {
    if (pd.hasOption(PropertyOption.MONITORING)) {
      throw new PropertyIsReadOnlyException(pd);
    }

    if (existsOnServer && pd.hasOption(PropertyOption.READ_ONLY)) {
      throw new PropertyIsReadOnlyException(pd);
    }

    properties.setPropertyValues(pd, values);

    // If this is a naming property then update the name.
    if (pd.equals(namingPropertyDefinition)) {
      // The property must be single-valued and mandatory.
      String newName = pd.encodeValue(values.iterator().next());
      path = path.rename(newName);
    }
  }



  /**
   * Adds this new managed object.
   *
   * @throws ManagedObjectAlreadyExistsException
   *           If the managed object cannot be added to the server
   *           because it already exists.
   * @throws ConcurrentModificationException
   *           If the managed object's parent has been removed by
   *           another client.
   * @throws OperationRejectedException
   *           If the server refuses to add this managed object due to
   *           some server-side constraint which cannot be satisfied.
   * @throws AuthorizationException
   *           If the server refuses to add this managed object
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  protected abstract void addNewManagedObject() throws AuthorizationException,
      CommunicationException, OperationRejectedException,
      ConcurrentModificationException, ManagedObjectAlreadyExistsException;



  /**
   * Gets the management context driver associated with this managed
   * object.
   *
   * @return Returns the management context driver associated with
   *         this managed object.
   */
  protected abstract Driver getDriver();



  /**
   * Gets the naming property definition associated with this managed
   * object.
   *
   * @return Returns the naming property definition associated with
   *         this managed object, or <code>null</code> if this
   *         managed object does not have a naming property.
   */
  protected final PropertyDefinition<?> getNamingPropertyDefinition() {
    return namingPropertyDefinition;
  }



  /**
   * Gets the property associated with the specified property
   * definition.
   *
   * @param <PD>
   *          The underlying type of the property.
   * @param pd
   *          The Property definition.
   * @return Returns the property associated with the specified
   *         property definition.
   * @throws IllegalArgumentException
   *           If this property provider does not recognize the
   *           requested property definition.
   */
  protected final <PD> Property<PD> getProperty(PropertyDefinition<PD> pd)
      throws IllegalArgumentException {
    return properties.getProperty(pd);
  }



  /**
   * Applies changes made to this managed object.
   *
   * @throws ConcurrentModificationException
   *           If this managed object has been removed from the server
   *           by another client.
   * @throws OperationRejectedException
   *           If the server refuses to modify this managed object due
   *           to some server-side constraint which cannot be
   *           satisfied.
   * @throws AuthorizationException
   *           If the server refuses to modify this managed object
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  protected abstract void modifyExistingManagedObject()
      throws ConcurrentModificationException, OperationRejectedException,
      AuthorizationException, CommunicationException;



  /**
   * Creates a new managed object.
   *
   * @param <M>
   *          The type of client configuration represented by the
   *          client managed object.
   * @param d
   *          The managed object's definition.
   * @param path
   *          The managed object's path.
   * @param properties
   *          The managed object's properties.
   * @param existsOnServer
   *          Indicates whether or not the managed object exists on
   *          the server (false means the managed object is new and
   *          has not been committed).
   * @param namingPropertyDefinition
   *          Optional naming property definition.
   * @return Returns the new managed object.
   */
  protected abstract <M extends ConfigurationClient>
  ManagedObject<M> newInstance(
      ManagedObjectDefinition<M, ?> d, ManagedObjectPath<M, ?> path,
      PropertySet properties, boolean existsOnServer,
      PropertyDefinition<?> namingPropertyDefinition);



  // Creates a new managed object with no active values, just default
  // values.
  private <M extends ConfigurationClient, PD> ManagedObject<M>
  createNewManagedObject(
      ManagedObjectDefinition<M, ?> d, ManagedObjectPath<M, ?> p,
      PropertyDefinition<PD> namingPropertyDefinition, String name,
      Collection<DefaultBehaviorException> exceptions) {
    PropertySet childProperties = new PropertySet();
    for (PropertyDefinition<?> pd : d.getAllPropertyDefinitions()) {
      try {
        createProperty(childProperties, p, pd);
      } catch (DefaultBehaviorException e) {
        // Add the exception if requested.
        if (exceptions != null) {
          exceptions.add(e);
        }
      }
    }

    // Set the naming property if there is one.
    if (namingPropertyDefinition != null) {
      PD value = namingPropertyDefinition.decodeValue(name);
      childProperties.setPropertyValues(namingPropertyDefinition, Collections
          .singleton(value));
    }

    return newInstance(d, p, childProperties, false, namingPropertyDefinition);
  }



  // Create an empty property.
  private <PD> void createProperty(PropertySet properties,
      ManagedObjectPath<?, ?> p, PropertyDefinition<PD> pd)
      throws DefaultBehaviorException {
    try {
      Driver context = getDriver();
      Collection<PD> defaultValues = context.findDefaultValues(p, pd, true);
      properties.addProperty(pd, defaultValues, Collections.<PD> emptySet());
    } catch (DefaultBehaviorException e) {
      // Make sure that we have still created the property.
      properties.addProperty(pd, Collections.<PD> emptySet(), Collections
          .<PD> emptySet());
      throw e;
    }
  }



  // Makes sure that this managed object exists.
  private void ensureThisManagedObjectExists()
      throws ConcurrentModificationException, CommunicationException,
      AuthorizationException {
    if (!path.isEmpty()) {
      Driver ctx = getDriver();

      try {
        ctx.getManagedObject(path);
      } catch (DefinitionDecodingException e) {
        // Ignore.
      } catch (ManagedObjectDecodingException e) {
        // Ignore.
      } catch (ManagedObjectNotFoundException e) {
        throw new ConcurrentModificationException();
      }
    }
  }



  // Validate that a relation definition belongs to this managed
  // object.
  private void validateRelationDefinition(RelationDefinition<?, ?> rd)
      throws IllegalArgumentException {
    ManagedObjectDefinition<T, ?> d = getManagedObjectDefinition();
    RelationDefinition<?, ?> tmp = d.getRelationDefinition(rd.getName());
    if (tmp != rd) {
      throw new IllegalArgumentException("The relation " + rd.getName()
          + " is not associated with a " + d.getName());
    }
  }

}
