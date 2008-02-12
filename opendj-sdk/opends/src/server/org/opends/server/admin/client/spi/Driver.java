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

package org.opends.server.admin.client.spi;



import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import org.opends.messages.Message;
import org.opends.server.admin.AbsoluteInheritedDefaultBehaviorProvider;
import org.opends.server.admin.AbstractManagedObjectDefinition;
import org.opends.server.admin.AliasDefaultBehaviorProvider;
import org.opends.server.admin.Configuration;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.Constraint;
import org.opends.server.admin.DefaultBehaviorException;
import org.opends.server.admin.DefaultBehaviorProviderVisitor;
import org.opends.server.admin.DefinedDefaultBehaviorProvider;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.IllegalPropertyValueStringException;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyException;
import org.opends.server.admin.PropertyIsSingleValuedException;
import org.opends.server.admin.PropertyNotFoundException;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.RelationDefinition;
import org.opends.server.admin.RelativeInheritedDefaultBehaviorProvider;
import org.opends.server.admin.UndefinedDefaultBehaviorProvider;
import org.opends.server.admin.DefinitionDecodingException.Reason;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.ClientConstraintHandler;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.OperationRejectedException;
import org.opends.server.admin.client.OperationRejectedException.OperationType;
import org.opends.server.admin.std.client.RootCfgClient;



/**
 * An abstract management connection context driver which should form
 * the basis of driver implementations.
 */
public abstract class Driver {

  /**
   * A default behavior visitor used for retrieving the default values
   * of a property.
   *
   * @param <T>
   *          The type of the property.
   */
  private class DefaultValueFinder<T> implements
      DefaultBehaviorProviderVisitor<T, Collection<T>, Void> {

    // Any exception that occurred whilst retrieving inherited default
    // values.
    private DefaultBehaviorException exception = null;

    // The path of the managed object containing the first property.
    private final ManagedObjectPath<?, ?> firstPath;

    // Indicates whether the managed object has been created yet.
    private final boolean isCreate;

    // The path of the managed object containing the next property.
    private ManagedObjectPath<?, ?> nextPath = null;

    // The next property whose default values were required.
    private PropertyDefinition<T> nextProperty = null;



    // Private constructor.
    private DefaultValueFinder(ManagedObjectPath<?, ?> p, boolean isCreate) {
      this.firstPath = p;
      this.isCreate = isCreate;
    }



    /**
     * {@inheritDoc}
     */
    public Collection<T> visitAbsoluteInherited(
        AbsoluteInheritedDefaultBehaviorProvider<T> d, Void p) {
      try {
        return getInheritedProperty(d.getManagedObjectPath(), d
            .getManagedObjectDefinition(), d.getPropertyName());
      } catch (DefaultBehaviorException e) {
        exception = e;
        return Collections.emptySet();
      }
    }



    /**
     * {@inheritDoc}
     */
    public Collection<T> visitAlias(AliasDefaultBehaviorProvider<T> d, Void p) {
      return Collections.emptySet();
    }



    /**
     * {@inheritDoc}
     */
    public Collection<T> visitDefined(DefinedDefaultBehaviorProvider<T> d,
        Void p) {
      Collection<String> stringValues = d.getDefaultValues();
      List<T> values = new ArrayList<T>(stringValues.size());

      for (String stringValue : stringValues) {
        try {
          values.add(nextProperty.decodeValue(stringValue));
        } catch (IllegalPropertyValueStringException e) {
          exception = new DefaultBehaviorException(nextProperty, e);
          break;
        }
      }

      return values;
    }



    /**
     * {@inheritDoc}
     */
    public Collection<T> visitRelativeInherited(
        RelativeInheritedDefaultBehaviorProvider<T> d, Void p) {
      try {
        return getInheritedProperty(d.getManagedObjectPath(nextPath), d
            .getManagedObjectDefinition(), d.getPropertyName());
      } catch (DefaultBehaviorException e) {
        exception = e;
        return Collections.emptySet();
      }
    }



    /**
     * {@inheritDoc}
     */
    public Collection<T> visitUndefined(UndefinedDefaultBehaviorProvider<T> d,
        Void p) {
      return Collections.emptySet();
    }



    // Find the default values for the next path/property.
    private Collection<T> find(ManagedObjectPath<?, ?> p,
        PropertyDefinition<T> pd) throws DefaultBehaviorException {
      this.nextPath = p;
      this.nextProperty = pd;

      Collection<T> values = nextProperty.getDefaultBehaviorProvider().accept(
          this, null);

      if (exception != null) {
        throw exception;
      }

      if (values.size() > 1 && !pd.hasOption(PropertyOption.MULTI_VALUED)) {
        throw new DefaultBehaviorException(pd,
            new PropertyIsSingleValuedException(pd));
      }

      return values;
    }



    // Get an inherited property value.
    @SuppressWarnings("unchecked")
    private Collection<T> getInheritedProperty(ManagedObjectPath target,
        AbstractManagedObjectDefinition<?, ?> d, String propertyName)
        throws DefaultBehaviorException {
      // First check that the requested type of managed object
      // corresponds to the path.
      AbstractManagedObjectDefinition<?, ?> supr = target
          .getManagedObjectDefinition();
      if (!supr.isParentOf(d)) {
        throw new DefaultBehaviorException(
            nextProperty, new DefinitionDecodingException(supr,
                Reason.WRONG_TYPE_INFORMATION));
      }

      // Save the current property in case of recursion.
      PropertyDefinition<T> pd1 = nextProperty;

      try {
        // Determine the requested property definition.
        PropertyDefinition<T> pd2;
        try {
          // FIXME: we use the definition taken from the default
          // behavior here when we should really use the exact
          // definition of the component being created.
          PropertyDefinition<?> pdTmp = d.getPropertyDefinition(propertyName);
          pd2 = pd1.getClass().cast(pdTmp);
        } catch (IllegalArgumentException e) {
          throw new PropertyNotFoundException(propertyName);
        } catch (ClassCastException e) {
          // FIXME: would be nice to throw a better exception here.
          throw new PropertyNotFoundException(propertyName);
        }

        // If the path relates to the current managed object and the
        // managed object is in the process of being created it won't
        // exist, so we should just use the default values of the
        // referenced property.
        if (isCreate && firstPath.equals(target)) {
          // Recursively retrieve this property's default values.
          Collection<T> tmp = find(target, pd2);
          Collection<T> values = new ArrayList<T>(tmp.size());
          for (T value : tmp) {
            pd1.validateValue(value);
            values.add(value);
          }
          return values;
        } else {
          // FIXME: issue 2481 - this is broken if the referenced property
          // inherits its defaults from the newly created managed object.
          return getPropertyValues(target, pd2);
        }
      } catch (DefaultBehaviorException e) {
        // Wrap any errors due to recursion.
        throw new DefaultBehaviorException(pd1, e);
      } catch (DefinitionDecodingException e) {
        throw new DefaultBehaviorException(pd1, e);
      } catch (PropertyNotFoundException e) {
        throw new DefaultBehaviorException(pd1, e);
      } catch (AuthorizationException e) {
        throw new DefaultBehaviorException(pd1, e);
      } catch (ManagedObjectNotFoundException e) {
        throw new DefaultBehaviorException(pd1, e);
      } catch (CommunicationException e) {
        throw new DefaultBehaviorException(pd1, e);
      } catch (PropertyException e) {
        throw new DefaultBehaviorException(pd1, e);
      }
    }
  };



  /**
   * Creates a new abstract management context.
   */
  protected Driver() {
    // No implementation required.
  }



  /**
   * Closes any context associated with this management context
   * driver.
   */
  public void close() {
    // do nothing by default
  }



  /**
   * Deletes the named instantiable child managed object from the
   * named parent managed object.
   *
   * @param <C>
   *          The type of client managed object configuration that the
   *          relation definition refers to.
   * @param <S>
   *          The type of server managed object configuration that the
   *          relation definition refers to.
   * @param parent
   *          The path of the parent managed object.
   * @param rd
   *          The instantiable relation definition.
   * @param name
   *          The name of the child managed object to be removed.
   * @return Returns <code>true</code> if the named instantiable
   *         child managed object was found, or <code>false</code>
   *         if it was not found.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with the
   *           parent managed object's definition.
   * @throws ManagedObjectNotFoundException
   *           If the parent managed object could not be found.
   * @throws OperationRejectedException
   *           If the managed object cannot be removed due to some
   *           client-side or server-side constraint which cannot be
   *           satisfied (for example, if it is referenced by another
   *           managed object).
   * @throws AuthorizationException
   *           If the server refuses to remove the managed objects
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  public final <C extends ConfigurationClient, S extends Configuration>
  boolean deleteManagedObject(
      ManagedObjectPath<?, ?> parent, InstantiableRelationDefinition<C, S> rd,
      String name) throws IllegalArgumentException,
      ManagedObjectNotFoundException, OperationRejectedException,
      AuthorizationException, CommunicationException {
    validateRelationDefinition(parent, rd);
    ManagedObjectPath<?, ?> child = parent.child(rd, name);
    return doDeleteManagedObject(child);
  }



  /**
   * Deletes the optional child managed object from the named parent
   * managed object.
   *
   * @param <C>
   *          The type of client managed object configuration that the
   *          relation definition refers to.
   * @param <S>
   *          The type of server managed object configuration that the
   *          relation definition refers to.
   * @param parent
   *          The path of the parent managed object.
   * @param rd
   *          The optional relation definition.
   * @return Returns <code>true</code> if the optional child managed
   *         object was found, or <code>false</code> if it was not
   *         found.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with the
   *           parent managed object's definition.
   * @throws ManagedObjectNotFoundException
   *           If the parent managed object could not be found.
   * @throws OperationRejectedException
   *           If the managed object cannot be removed due to some
   *           client-side or server-side constraint which cannot be
   *           satisfied (for example, if it is referenced by another
   *           managed object).
   * @throws AuthorizationException
   *           If the server refuses to remove the managed objects
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  public final <C extends ConfigurationClient, S extends Configuration>
  boolean deleteManagedObject(
      ManagedObjectPath<?, ?> parent, OptionalRelationDefinition<C, S> rd)
      throws IllegalArgumentException, ManagedObjectNotFoundException,
      OperationRejectedException, AuthorizationException,
      CommunicationException {
    validateRelationDefinition(parent, rd);
    ManagedObjectPath<?, ?> child = parent.child(rd);
    return doDeleteManagedObject(child);
  }



  /**
   * Gets the named managed object. The path is guaranteed to be
   * non-empty, so implementations do not need to worry about handling
   * this special case.
   *
   * @param <C>
   *          The type of client managed object configuration that the
   *          path definition refers to.
   * @param <S>
   *          The type of server managed object configuration that the
   *          path definition refers to.
   * @param path
   *          The non-empty path of the managed object.
   * @return Returns the named managed object.
   * @throws DefinitionDecodingException
   *           If the managed object was found but its type could not
   *           be determined.
   * @throws ManagedObjectDecodingException
   *           If the managed object was found but one or more of its
   *           properties could not be decoded.
   * @throws ManagedObjectNotFoundException
   *           If the requested managed object could not be found on
   *           the server.
   * @throws AuthorizationException
   *           If the server refuses to retrieve the managed object
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  public abstract <C extends ConfigurationClient, S extends Configuration>
  ManagedObject<? extends C> getManagedObject(
      ManagedObjectPath<C, S> path) throws DefinitionDecodingException,
      ManagedObjectDecodingException, ManagedObjectNotFoundException,
      AuthorizationException, CommunicationException;



  /**
   * Gets the effective value of a property in the named managed
   * object.
   *
   * @param <C>
   *          The type of client managed object configuration that the
   *          path definition refers to.
   * @param <S>
   *          The type of server managed object configuration that the
   *          path definition refers to.
   * @param <PD>
   *          The type of the property to be retrieved.
   * @param path
   *          The path of the managed object containing the property.
   * @param pd
   *          The property to be retrieved.
   * @return Returns the property's effective value, or
   *         <code>null</code> if there are no values defined.
   * @throws IllegalArgumentException
   *           If the property definition is not associated with the
   *           referenced managed object's definition.
   * @throws DefinitionDecodingException
   *           If the managed object was found but its type could not
   *           be determined.
   * @throws PropertyException
   *           If the managed object was found but the requested
   *           property could not be decoded.
   * @throws ManagedObjectNotFoundException
   *           If the requested managed object could not be found on
   *           the server.
   * @throws AuthorizationException
   *           If the server refuses to retrieve the managed object
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  public final <C extends ConfigurationClient, S extends Configuration, PD>
  PD getPropertyValue(ManagedObjectPath<C, S> path,
      PropertyDefinition<PD> pd) throws IllegalArgumentException,
      DefinitionDecodingException, AuthorizationException,
      ManagedObjectNotFoundException, CommunicationException,
      PropertyException {
    Set<PD> values = getPropertyValues(path, pd);
    if (values.isEmpty()) {
      return null;
    } else {
      return values.iterator().next();
    }
  }



  /**
   * Gets the effective values of a property in the named managed
   * object.
   * <p>
   * Implementations MUST NOT not use
   * {@link #getManagedObject(ManagedObjectPath)} to read the
   * referenced managed object in its entirety. Specifically,
   * implementations MUST only attempt to resolve the default values
   * for the requested property and its dependencies (if it uses
   * inherited defaults). This is to avoid infinite recursion where a
   * managed object contains a property which inherits default values
   * from another property in the same managed object.
   *
   * @param <C>
   *          The type of client managed object configuration that the
   *          path definition refers to.
   * @param <S>
   *          The type of server managed object configuration that the
   *          path definition refers to.
   * @param <PD>
   *          The type of the property to be retrieved.
   * @param path
   *          The path of the managed object containing the property.
   * @param pd
   *          The property to be retrieved.
   * @return Returns the property's effective values, or an empty set
   *         if there are no values defined.
   * @throws IllegalArgumentException
   *           If the property definition is not associated with the
   *           referenced managed object's definition.
   * @throws DefinitionDecodingException
   *           If the managed object was found but its type could not
   *           be determined.
   * @throws PropertyException
   *           If the managed object was found but the requested
   *           property could not be decoded.
   * @throws ManagedObjectNotFoundException
   *           If the requested managed object could not be found on
   *           the server.
   * @throws AuthorizationException
   *           If the server refuses to retrieve the managed object
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  public abstract <C extends ConfigurationClient, S extends Configuration, PD>
  SortedSet<PD> getPropertyValues(
      ManagedObjectPath<C, S> path, PropertyDefinition<PD> pd)
      throws IllegalArgumentException, DefinitionDecodingException,
      AuthorizationException, ManagedObjectNotFoundException,
      CommunicationException, PropertyException;



  /**
   * Gets the root configuration managed object associated with this
   * management context driver.
   *
   * @return Returns the root configuration managed object associated
   *         with this management context driver.
   */
  public abstract
  ManagedObject<RootCfgClient> getRootConfigurationManagedObject();



  /**
   * Lists the child managed objects of the named parent managed
   * object.
   *
   * @param <C>
   *          The type of client managed object configuration that the
   *          relation definition refers to.
   * @param <S>
   *          The type of server managed object configuration that the
   *          relation definition refers to.
   * @param parent
   *          The path of the parent managed object.
   * @param rd
   *          The instantiable relation definition.
   * @return Returns the names of the child managed objects.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with the
   *           parent managed object's definition.
   * @throws ManagedObjectNotFoundException
   *           If the parent managed object could not be found.
   * @throws AuthorizationException
   *           If the server refuses to list the managed objects
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  public final <C extends ConfigurationClient, S extends Configuration>
  String[] listManagedObjects(
      ManagedObjectPath<?, ?> parent, InstantiableRelationDefinition<C, S> rd)
      throws IllegalArgumentException, ManagedObjectNotFoundException,
      AuthorizationException, CommunicationException {
    return listManagedObjects(parent, rd, rd.getChildDefinition());
  }



  /**
   * Lists the child managed objects of the named parent managed
   * object which are a sub-type of the specified managed object
   * definition.
   *
   * @param <C>
   *          The type of client managed object configuration that the
   *          relation definition refers to.
   * @param <S>
   *          The type of server managed object configuration that the
   *          relation definition refers to.
   * @param parent
   *          The path of the parent managed object.
   * @param rd
   *          The instantiable relation definition.
   * @param d
   *          The managed object definition.
   * @return Returns the names of the child managed objects which are
   *         a sub-type of the specified managed object definition.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with the
   *           parent managed object's definition.
   * @throws ManagedObjectNotFoundException
   *           If the parent managed object could not be found.
   * @throws AuthorizationException
   *           If the server refuses to list the managed objects
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  public abstract <C extends ConfigurationClient, S extends Configuration>
  String[] listManagedObjects(
      ManagedObjectPath<?, ?> parent, InstantiableRelationDefinition<C, S> rd,
      AbstractManagedObjectDefinition<? extends C, ? extends S> d)
      throws IllegalArgumentException, ManagedObjectNotFoundException,
      AuthorizationException, CommunicationException;



  /**
   * Determines whether or not the named managed object exists.
   * <p>
   * Implementations should always return <code>true</code> when the
   * provided path is empty.
   *
   * @param path
   *          The path of the named managed object.
   * @return Returns <code>true</code> if the named managed object
   *         exists, <code>false</code> otherwise.
   * @throws ManagedObjectNotFoundException
   *           If the parent managed object could not be found.
   * @throws AuthorizationException
   *           If the server refuses to make the determination because
   *           the client does not have the correct privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  public abstract boolean managedObjectExists(ManagedObjectPath<?, ?> path)
      throws ManagedObjectNotFoundException, AuthorizationException,
      CommunicationException;



  /**
   * Deletes the named managed object.
   * <p>
   * Implementations do not need check whether the named managed
   * object exists, nor do they need to enforce client constraints.
   *
   * @param <C>
   *          The type of client managed object configuration that the
   *          relation definition refers to.
   * @param <S>
   *          The type of server managed object configuration that the
   *          relation definition refers to.
   * @param path
   *          The path of the managed object to be deleted.
   * @throws OperationRejectedException
   *           If the managed object cannot be removed due to some
   *           server-side constraint which cannot be satisfied (for
   *           example, if it is referenced by another managed
   *           object).
   * @throws AuthorizationException
   *           If the server refuses to remove the managed objects
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  protected abstract <C extends ConfigurationClient, S extends Configuration>
  void deleteManagedObject(
      ManagedObjectPath<C, S> path) throws OperationRejectedException,
      AuthorizationException, CommunicationException;



  /**
   * Gets the default values for the specified property.
   *
   * @param <PD>
   *          The type of the property.
   * @param p
   *          The managed object path of the current managed object.
   * @param pd
   *          The property definition.
   * @param isCreate
   *          Indicates whether the managed object has been created
   *          yet.
   * @return Returns the default values for the specified property.
   * @throws DefaultBehaviorException
   *           If the default values could not be retrieved or decoded
   *           properly.
   */
  protected final <PD> Collection<PD> findDefaultValues(
      ManagedObjectPath<?, ?> p, PropertyDefinition<PD> pd, boolean isCreate)
      throws DefaultBehaviorException {
    DefaultValueFinder<PD> v = new DefaultValueFinder<PD>(p, isCreate);
    return v.find(p, pd);
  }



  /**
   * Gets the management context associated with this driver.
   *
   * @return Returns the management context associated with this
   *         driver.
   */
  protected abstract ManagementContext getManagementContext();



  /**
   * Validate that a relation definition belongs to the managed object
   * referenced by the provided path.
   *
   * @param path
   *          The parent managed object path.
   * @param rd
   *          The relation definition.
   * @throws IllegalArgumentException
   *           If the relation definition does not belong to the
   *           managed object definition.
   */
  protected final void validateRelationDefinition(ManagedObjectPath<?, ?> path,
      RelationDefinition<?, ?> rd) throws IllegalArgumentException {
    AbstractManagedObjectDefinition<?, ?> d = path.getManagedObjectDefinition();
    RelationDefinition<?, ?> tmp = d.getRelationDefinition(rd.getName());
    if (tmp != rd) {
      throw new IllegalArgumentException("The relation " + rd.getName()
          + " is not associated with a " + d.getName());
    }
  }



  // Remove a managed object, first ensuring that the parent exists,
  // then ensuring that the child exists, before ensuring that any
  // constraints are satisfied.
  private <C extends ConfigurationClient, S extends Configuration>
  boolean doDeleteManagedObject(
      ManagedObjectPath<C, S> path) throws ManagedObjectNotFoundException,
      OperationRejectedException, AuthorizationException,
      CommunicationException {
    // First make sure that the parent exists.
    if (!managedObjectExists(path.parent())) {
      throw new ManagedObjectNotFoundException();
    }

    // Make sure that the targeted managed object exists.
    if (!managedObjectExists(path)) {
      return false;
    }

    // The targeted managed object is guaranteed to exist, so enforce
    // any constraints.
    AbstractManagedObjectDefinition<?, ?> d = path.getManagedObjectDefinition();
    List<Message> messages = new LinkedList<Message>();
    boolean isAcceptable = true;

    for (Constraint constraint : d.getAllConstraints()) {
      for (ClientConstraintHandler handler : constraint
          .getClientConstraintHandlers()) {
        ManagementContext context = getManagementContext();
        if (!handler.isDeleteAcceptable(context, path, messages)) {
          isAcceptable = false;
        }
      }
    }

    if (!isAcceptable) {
      throw new OperationRejectedException(OperationType.DELETE, d
          .getUserFriendlyName(), messages);
    }

    deleteManagedObject(path);
    return true;
  }

}
