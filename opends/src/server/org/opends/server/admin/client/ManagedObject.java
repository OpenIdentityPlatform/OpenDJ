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
import java.util.SortedSet;

import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.IllegalPropertyValueException;
import org.opends.server.admin.InstantiableRelationDefinition;
import org.opends.server.admin.ManagedObjectAlreadyExistsException;
import org.opends.server.admin.ManagedObjectDefinition;
import org.opends.server.admin.ConfigurationClient;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.ManagedObjectPath;
import org.opends.server.admin.OptionalRelationDefinition;
import org.opends.server.admin.PropertyDefinition;
import org.opends.server.admin.PropertyIsMandatoryException;
import org.opends.server.admin.PropertyIsReadOnlyException;
import org.opends.server.admin.PropertyIsSingleValuedException;
import org.opends.server.admin.PropertyProvider;
import org.opends.server.admin.SingletonRelationDefinition;



/**
 * A generic interface for accessing client-side managed objects.
 * <p>
 * A managed object comprises of zero or more properties. A property
 * has associated with it three sets of property value(s). These are:
 * <ul>
 * <li><i>default value(s)</i> - these value(s) represent the
 * default behavior for the property when it has no active values.
 * When a property inherits its default value(s) from elsewhere (i.e.
 * a property in another managed object), the default value(s)
 * represent the active value(s) of the inherited property at the time
 * the managed object was retrieved
 * <li><i>active value(s)</i> - these value(s) represent the state
 * of the property at the time the managed object was retrieved
 * <li><i>pending value(s)</i> - these value(s) represent any
 * modifications made to the property's value(s) since the managed
 * object object was retrieved and before the changes have been
 * committed using the {@link #commit()} method, the pending values
 * can be empty indicating that the property should be modified back
 * to its default values.
 * </ul>
 * In addition, a property has an <i>effective state</i> defined by
 * its <i>effective values</i> which are derived by evaluating the
 * following rules in the order presented:
 * <ul>
 * <li>the <i>pending values</i> if defined and non-empty
 * <li>or, the <i>default values</i> if the pending values are
 * defined but are empty
 * <li>or, the <i>active values</i> if defined and non-empty
 * <li>or, the <i>default values</i> if there are no active values
 * <li>or, an empty set of values, if there are no default values.
 * </ul>
 *
 * @param <C>
 *          The type of client configuration represented by the client
 *          managed object.
 */
public interface ManagedObject<C extends ConfigurationClient> extends
    PropertyProvider {

  /**
   * Commit any changes made to this managed object. Pending property
   * values will be committed to the managed object. If successful,
   * the pending values will become active values.
   * <p>
   * See the class description for more information regarding pending
   * and active values.
   *
   * @throws ConcurrentModificationException
   *           If this managed object has been removed from the server
   *           by another client.
   * @throws OperationRejectedException
   *           If the server refuses to apply the changes due to some
   *           server-side constraint which cannot be satisfied.
   * @throws AuthorizationException
   *           If the server refuses to apply the changes because the
   *           client does not have the correct privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  void commit() throws ConcurrentModificationException,
      OperationRejectedException, AuthorizationException,
      CommunicationException;



  /**
   * Creates a new child managed object bound to the specified
   * instantiable relation. The new managed object instance will be
   * created with values taken from a property provider. The caller
   * must make sure that the property provider provides values for
   * mandatory properties.
   *
   * @param <M>
   *          The expected type of the child managed object
   *          configuration client.
   * @param <N>
   *          The actual type of the added managed object
   *          configuration client.
   * @param r
   *          The instantiable relation definition.
   * @param d
   *          The definition of the managed object to be created.
   * @param name
   *          The name of the child managed object.
   * @param p
   *          A property provider which should be used to initialize
   *          property values of the new managed object.
   * @return Returns a new child managed object bound to the specified
   *         instantiable relation.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with this
   *           managed object's definition.
   * @throws ManagedObjectDecodingException
   *           If the managed object could not be create because one
   *           or more of its properties are invalid.
   * @throws ManagedObjectAlreadyExistsException
   *           If the managed object cannot be created because it
   *           already exists on the server.
   * @throws ConcurrentModificationException
   *           If this managed object has been removed from the server
   *           by another client.
   * @throws OperationRejectedException
   *           If the server refuses to create the managed object due
   *           to some server-side constraint which cannot be
   *           satisfied.
   * @throws AuthorizationException
   *           If the server refuses to create the managed object
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  <M extends ConfigurationClient, N extends M> ManagedObject<N> createChild(
      InstantiableRelationDefinition<M, ?> r, ManagedObjectDefinition<N, ?> d,
      String name, PropertyProvider p) throws IllegalArgumentException,
      ManagedObjectDecodingException, ManagedObjectAlreadyExistsException,
      ConcurrentModificationException, OperationRejectedException,
      AuthorizationException, CommunicationException;



  /**
   * Creates a new child managed object bound to the specified
   * optional relation. The new managed object instance will be
   * created with values taken from a property provider. The caller
   * must make sure that the property provider provides values for
   * mandatory properties.
   *
   * @param <M>
   *          The expected type of the child managed object
   *          configuration client.
   * @param <N>
   *          The actual type of the added managed object
   *          configuration client.
   * @param r
   *          The optional relation definition.
   * @param d
   *          The definition of the managed object to be created.
   * @param p
   *          A property provider which should be used to initialize
   *          property values of the new managed object.
   * @return Returns a new child managed object bound to the specified
   *         optional relation.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with this
   *           managed object's definition.
   * @throws ManagedObjectDecodingException
   *           If the managed object could not be created because one
   *           or more of its properties are invalid.
   * @throws ManagedObjectAlreadyExistsException
   *           If the managed object cannot be created because it
   *           already exists on the server.
   * @throws ConcurrentModificationException
   *           If this managed object has been removed from the server
   *           by another client.
   * @throws OperationRejectedException
   *           If the server refuses to create the managed object due
   *           to some server-side constraint which cannot be
   *           satisfied.
   * @throws AuthorizationException
   *           If the server refuses to create the managed object
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  <M extends ConfigurationClient, N extends M> ManagedObject<N> createChild(
      OptionalRelationDefinition<M, ?> r, ManagedObjectDefinition<N, ?> d,
      PropertyProvider p) throws IllegalArgumentException,
      ManagedObjectDecodingException, ManagedObjectAlreadyExistsException,
      ConcurrentModificationException, OperationRejectedException,
      AuthorizationException, CommunicationException;



  /**
   * Retrieve an instantiable child managed object.
   *
   * @param <M>
   *          The requested type of the child managed object
   *          configuration client.
   * @param d
   *          The instantiable relation definition.
   * @param name
   *          The name of the child managed object.
   * @return Returns the instantiable child managed object.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with this
   *           managed object's definition.
   * @throws DefinitionDecodingException
   *           If the managed object was found but its type could not
   *           be determined.
   * @throws ManagedObjectDecodingException
   *           If the managed object was found but one or more of its
   *           properties could not be decoded.
   * @throws ManagedObjectNotFoundException
   *           If the requested managed object could not be found on
   *           the server.
   * @throws ConcurrentModificationException
   *           If this managed object has been removed from the server
   *           by another client.
   * @throws AuthorizationException
   *           If the server refuses to retrieve the managed object
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  <M extends ConfigurationClient> ManagedObject<? extends M> getChild(
      InstantiableRelationDefinition<M, ?> d, String name)
      throws IllegalArgumentException, DefinitionDecodingException,
      ManagedObjectDecodingException, ManagedObjectNotFoundException,
      ConcurrentModificationException, AuthorizationException,
      CommunicationException;



  /**
   * Retrieve an optional child managed object.
   *
   * @param <M>
   *          The requested type of the child managed object
   *          configuration client.
   * @param d
   *          The optional relation definition.
   * @return Returns the optional child managed object.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with this
   *           managed object's definition.
   * @throws DefinitionDecodingException
   *           If the managed object was found but its type could not
   *           be determined.
   * @throws ManagedObjectDecodingException
   *           If the managed object was found but one or more of its
   *           properties could not be decoded.
   * @throws ManagedObjectNotFoundException
   *           If the requested managed object could not be found on
   *           the server.
   * @throws ConcurrentModificationException
   *           If this managed object has been removed from the server
   *           by another client.
   * @throws AuthorizationException
   *           If the server refuses to retrieve the managed object
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  <M extends ConfigurationClient> ManagedObject<? extends M> getChild(
      OptionalRelationDefinition<M, ?> d) throws IllegalArgumentException,
      DefinitionDecodingException, ManagedObjectDecodingException,
      ManagedObjectNotFoundException, ConcurrentModificationException,
      AuthorizationException, CommunicationException;



  /**
   * Retrieve a singleton child managed object.
   *
   * @param <M>
   *          The requested type of the child managed object
   *          configuration client.
   * @param d
   *          The singleton relation definition.
   * @return Returns the singleton child managed object.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with this
   *           managed object's definition.
   * @throws DefinitionDecodingException
   *           If the managed object was found but its type could not
   *           be determined.
   * @throws ManagedObjectDecodingException
   *           If the managed object was found but one or more of its
   *           properties could not be decoded.
   * @throws ManagedObjectNotFoundException
   *           If the requested managed object could not be found on
   *           the server.
   * @throws ConcurrentModificationException
   *           If this managed object has been removed from the server
   *           by another client.
   * @throws AuthorizationException
   *           If the server refuses to retrieve the managed object
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  <M extends ConfigurationClient> ManagedObject<? extends M> getChild(
      SingletonRelationDefinition<M, ?> d) throws IllegalArgumentException,
      DefinitionDecodingException, ManagedObjectDecodingException,
      ManagedObjectNotFoundException, ConcurrentModificationException,
      AuthorizationException, CommunicationException;



  /**
   * Creates a client configuration view of this managed object.
   * Modifications made to this managed object will be reflected in
   * the client configuration view and vice versa.
   *
   * @return Returns a client configuration view of this managed
   *         object.
   */
  C getConfiguration();



  /**
   * Get the definition associated with this managed object.
   *
   * @return Returns the definition associated with this managed
   *         object.
   */
  ManagedObjectDefinition<C, ?> getManagedObjectDefinition();



  /**
   * Get the path of this managed object.
   *
   * @return Returns the path of this managed object.
   */
  ManagedObjectPath getManagedObjectPath();



  /**
   * Get the effective value of the specified property.
   * <p>
   * See the class description for more information about how the
   * effective property value is derived.
   *
   * @param <T>
   *          The type of the property to be retrieved.
   * @param d
   *          The property to be retrieved.
   * @return Returns the property's effective value, or
   *         <code>null</code> if there is no effective value
   *         defined.
   * @throws IllegalArgumentException
   *           If the property definition is not associated with this
   *           managed object's definition.
   */
  <T> T getPropertyValue(PropertyDefinition<T> d)
      throws IllegalArgumentException;



  /**
   * Get the effective values of the specified property.
   * <p>
   * See the class description for more information about how the
   * effective property values are derived.
   *
   * @param <T>
   *          The type of the property to be retrieved.
   * @param d
   *          The property to be retrieved.
   * @return Returns the property's effective values, or an empty set
   *         if there are no effective values defined.
   * @throws IllegalArgumentException
   *           If the property definition is not associated with this
   *           managed object's definition.
   */
  <T> SortedSet<T> getPropertyValues(PropertyDefinition<T> d)
      throws IllegalArgumentException;



  /**
   * Determines whether or not the optional managed object associated
   * with the specified optional relations exists.
   *
   * @param d
   *          The optional relation definition.
   * @return Returns <code>true</code> if the optional managed
   *         object exists, <code>false</code> otherwise.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with this
   *           managed object's definition.
   * @throws ConcurrentModificationException
   *           If this managed object has been removed from the server
   *           by another client.
   * @throws AuthorizationException
   *           If the server refuses to make the determination because
   *           the client does not have the correct privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  boolean hasChild(OptionalRelationDefinition<?, ?> d)
      throws IllegalArgumentException, ConcurrentModificationException,
      AuthorizationException, CommunicationException;



  /**
   * Lists the child managed objects associated with the specified
   * instantiable relation.
   *
   * @param d
   *          The instantiable relation definition.
   * @return Returns the names of the child managed objects.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with this
   *           managed object's definition.
   * @throws ConcurrentModificationException
   *           If this managed object has been removed from the server
   *           by another client.
   * @throws AuthorizationException
   *           If the server refuses to list the managed objects
   *           because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  String[] listChildren(InstantiableRelationDefinition<?, ?> d)
      throws IllegalArgumentException, ConcurrentModificationException,
      AuthorizationException, CommunicationException;



  /**
   * Removes the named instantiable child managed object.
   *
   * @param <M>
   *          The type of the child managed object configuration
   *          client.
   * @param d
   *          The instantiable relation definition.
   * @param name
   *          The name of the child managed object to be removed.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with this
   *           managed object's definition.
   * @throws ManagedObjectNotFoundException
   *           If the managed object could not be removed because it
   *           could not found on the server.
   * @throws OperationRejectedException
   *           If the server refuses to remove the managed object due
   *           to some server-side constraint which cannot be
   *           satisfied (for example, if it is referenced by another
   *           managed object).
   * @throws ConcurrentModificationException
   *           If this managed object has been removed from the server
   *           by another client.
   * @throws AuthorizationException
   *           If the server refuses to make the list the managed
   *           objects because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  <M extends ConfigurationClient> void removeChild(
      InstantiableRelationDefinition<M, ?> d, String name)
      throws IllegalArgumentException, ManagedObjectNotFoundException,
      OperationRejectedException, ConcurrentModificationException,
      AuthorizationException, CommunicationException;



  /**
   * Removes an optional child managed object.
   *
   * @param <M>
   *          The type of the child managed object configuration
   *          client.
   * @param d
   *          The optional relation definition.
   * @throws IllegalArgumentException
   *           If the relation definition is not associated with this
   *           managed object's definition.
   * @throws ManagedObjectNotFoundException
   *           If the managed object could not be removed because it
   *           could not found on the server.
   * @throws OperationRejectedException
   *           If the server refuses to remove the managed object due
   *           to some server-side constraint which cannot be
   *           satisfied (for example, if it is referenced by another
   *           managed object).
   * @throws ConcurrentModificationException
   *           If this managed object has been removed from the server
   *           by another client.
   * @throws AuthorizationException
   *           If the server refuses to make the list the managed
   *           objects because the client does not have the correct
   *           privileges.
   * @throws CommunicationException
   *           If the client cannot contact the server due to an
   *           underlying communication problem.
   */
  <M extends ConfigurationClient> void removeChild(
      OptionalRelationDefinition<M, ?> d) throws IllegalArgumentException,
      ManagedObjectNotFoundException, OperationRejectedException,
      ConcurrentModificationException, AuthorizationException,
      CommunicationException;



  /**
   * Set a new pending value for the specified property.
   * <p>
   * See the class description for more information regarding pending
   * values.
   *
   * @param <T>
   *          The type of the property to be modified.
   * @param d
   *          The property to be modified.
   * @param value
   *          The new pending value for the property, or
   *          <code>null</code> if the property should be reset to
   *          its default behavior.
   * @throws IllegalPropertyValueException
   *           If the new pending value is deemed to be invalid
   *           according to the property definition.
   * @throws PropertyIsReadOnlyException
   *           If an attempt was made to modify a read-only property.
   * @throws PropertyIsMandatoryException
   *           If an attempt was made to remove a mandatory property.
   * @throws IllegalArgumentException
   *           If the specified property definition is not associated
   *           with this managed object.
   */
  <T> void setPropertyValue(PropertyDefinition<T> d, T value)
      throws IllegalPropertyValueException, PropertyIsReadOnlyException,
      PropertyIsMandatoryException, IllegalArgumentException;



  /**
   * Set a new pending values for the specified property.
   * <p>
   * See the class description for more information regarding pending
   * values.
   *
   * @param <T>
   *          The type of the property to be modified.
   * @param d
   *          The property to be modified.
   * @param values
   *          A non-<code>null</code> set of new pending values for
   *          the property (an empty set indicates that the property
   *          should be reset to its default behavior). The set will
   *          not be referenced by this managed object.
   * @throws IllegalPropertyValueException
   *           If a new pending value is deemed to be invalid
   *           according to the property definition.
   * @throws PropertyIsSingleValuedException
   *           If an attempt was made to add multiple pending values
   *           to a single-valued property.
   * @throws PropertyIsReadOnlyException
   *           If an attempt was made to modify a read-only property.
   * @throws PropertyIsMandatoryException
   *           If an attempt was made to remove a mandatory property.
   * @throws IllegalArgumentException
   *           If the specified property definition is not associated
   *           with this managed object.
   */
  <T> void setPropertyValues(PropertyDefinition<T> d, Collection<T> values)
      throws IllegalPropertyValueException, PropertyIsSingleValuedException,
      PropertyIsReadOnlyException, PropertyIsMandatoryException,
      IllegalArgumentException;

}
