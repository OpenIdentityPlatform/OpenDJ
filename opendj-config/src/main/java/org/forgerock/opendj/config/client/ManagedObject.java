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
 * Copyright 2007-2009 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */

package org.forgerock.opendj.config.client;

import java.util.Collection;
import java.util.SortedSet;

import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.Configuration;
import org.forgerock.opendj.config.ConfigurationClient;
import org.forgerock.opendj.config.PropertyException;
import org.forgerock.opendj.config.DefinitionDecodingException;
import org.forgerock.opendj.config.InstantiableRelationDefinition;
import org.forgerock.opendj.config.ManagedObjectAlreadyExistsException;
import org.forgerock.opendj.config.ManagedObjectDefinition;
import org.forgerock.opendj.config.ManagedObjectNotFoundException;
import org.forgerock.opendj.config.ManagedObjectPath;
import org.forgerock.opendj.config.OptionalRelationDefinition;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.PropertyProvider;
import org.forgerock.opendj.config.SetRelationDefinition;
import org.forgerock.opendj.config.SingletonRelationDefinition;
import org.forgerock.opendj.ldap.LdapException;

/**
 * A generic interface for accessing client-side managed objects.
 * <p>
 * A managed object comprises of zero or more properties. A property has
 * associated with it three sets of property value(s). These are:
 * <ul>
 * <li><i>default value(s)</i> - these value(s) represent the default behavior
 * for the property when it has no active values. When a property inherits its
 * default value(s) from elsewhere (i.e. a property in another managed object),
 * the default value(s) represent the active value(s) of the inherited property
 * at the time the managed object was retrieved
 * <li><i>active value(s)</i> - these value(s) represent the state of the
 * property at the time the managed object was retrieved
 * <li><i>pending value(s)</i> - these value(s) represent any modifications made
 * to the property's value(s) since the managed object object was retrieved and
 * before the changes have been committed using the {@link #commit()} method,
 * the pending values can be empty indicating that the property should be
 * modified back to its default values.
 * </ul>
 * In addition, a property has an <i>effective state</i> defined by its
 * <i>effective values</i> which are derived by evaluating the following rules
 * in the order presented:
 * <ul>
 * <li>the <i>pending values</i> if defined and non-empty
 * <li>or, the <i>default values</i> if the pending values are defined but are
 * empty
 * <li>or, the <i>active values</i> if defined and non-empty
 * <li>or, the <i>default values</i> if there are no active values
 * <li>or, an empty set of values, if there are no default values.
 * </ul>
 *
 * @param <T>
 *            The type of client configuration represented by the client managed
 *            object.
 */
public interface ManagedObject<T extends ConfigurationClient> extends PropertyProvider {

    /**
     * Adds this managed object to the server or commits any changes made to it
     * depending on whether the managed object already exists on the
     * server. Pending property values will be committed to the managed object.
     * If successful, the pending values will become active values.
     * <p>
     * See the class description for more information regarding pending and
     * active values.
     *
     * @throws ManagedObjectAlreadyExistsException
     *             If the managed object cannot be added to the server because
     *             it already exists.
     * @throws MissingMandatoryPropertiesException
     *             If the managed object contains some mandatory properties
     *             which have been left undefined.
     * @throws ConcurrentModificationException
     *             If the managed object is being added to the server but its
     *             parent has been removed by another client, or if this managed
     *             object is being modified but it has been removed from the
     *             server by another client.
     * @throws OperationRejectedException
     *             If this managed object cannot be added or modified due to
     *             some client-side or server-side constraint which cannot be
     *             satisfied.
     * @throws LdapException
     *             If any other error occurs.
     */
    void commit() throws ManagedObjectAlreadyExistsException, MissingMandatoryPropertiesException,
            ConcurrentModificationException, OperationRejectedException, LdapException;

    /**
     * Determines whether this managed object has been modified since it
     * was constructed. In other words, whether the set of pending values
     * differs from the set of active values.
     *
     * @return Returns <code>true</code> if this managed object has been
     *         modified since it was constructed.
     */
    boolean isModified();

    /**
     * Creates a new child managed object bound to the specified instantiable
     * relation. The new managed object will initially not contain any property
     * values (including mandatory properties). Once the managed object has been
     * configured it can be added to the server using the {@link #commit()}
     * method.
     *
     * @param <C>
     *            The expected type of the child managed object configuration
     *            client.
     * @param <S>
     *            The expected type of the child managed object server
     *            configuration.
     * @param <C1>
     *            The actual type of the added managed object configuration
     *            client.
     * @param r
     *            The instantiable relation definition.
     * @param d
     *            The definition of the managed object to be created.
     * @param name
     *            The name of the child managed object.
     * @param exceptions
     *            A collection in which to place any
     *            {@link PropertyException}s that occurred whilst
     *            attempting to determine the managed object's default values.
     * @return Returns a new child managed object bound to the specified
     *         instantiable relation.
     * @throws IllegalManagedObjectNameException
     *             If the name of the child managed object is invalid.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with this
     *             managed object's definition.
     */
    <C extends ConfigurationClient, S extends Configuration, C1 extends C> ManagedObject<C1> createChild(
            InstantiableRelationDefinition<C, S> r, ManagedObjectDefinition<C1, ? extends S> d, String name,
            Collection<PropertyException> exceptions) throws IllegalManagedObjectNameException;

    /**
     * Creates a new child managed object bound to the specified optional
     * relation. The new managed object will initially not contain any property
     * values (including mandatory properties). Once the managed object has been
     * configured it can be added to the server using the {@link #commit()}
     * method.
     *
     * @param <C>
     *            The expected type of the child managed object configuration
     *            client.
     * @param <S>
     *            The expected type of the child managed object server
     *            configuration.
     * @param <C1>
     *            The actual type of the added managed object configuration
     *            client.
     * @param r
     *            The optional relation definition.
     * @param d
     *            The definition of the managed object to be created.
     * @param exceptions
     *            A collection in which to place any
     *            {@link PropertyException}s that occurred whilst
     *            attempting to determine the managed object's default values.
     * @return Returns a new child managed object bound to the specified
     *         optional relation.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with this
     *             managed object's definition.
     */
    <C extends ConfigurationClient, S extends Configuration, C1 extends C> ManagedObject<C1> createChild(
            OptionalRelationDefinition<C, S> r, ManagedObjectDefinition<C1, ? extends S> d,
            Collection<PropertyException> exceptions);

    /**
     * Creates a new child managed object bound to the specified set relation.
     * The new managed object will initially not contain any property values
     * (including mandatory properties). Once the managed object has been
     * configured it can be added to the server using the {@link #commit()}
     * method.
     *
     * @param <C>
     *            The expected type of the child managed object configuration
     *            client.
     * @param <S>
     *            The expected type of the child managed object server
     *            configuration.
     * @param <C1>
     *            The actual type of the added managed object configuration
     *            client.
     * @param r
     *            The set relation definition.
     * @param d
     *            The definition of the managed object to be created.
     * @param exceptions
     *            A collection in which to place any
     *            {@link PropertyException}s that occurred whilst
     *            attempting to determine the managed object's default values.
     * @return Returns a new child managed object bound to the specified set
     *         relation.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with this
     *             managed object's definition.
     */
    <C extends ConfigurationClient, S extends Configuration, C1 extends C> ManagedObject<C1> createChild(
            SetRelationDefinition<C, S> r, ManagedObjectDefinition<C1, ? extends S> d,
            Collection<PropertyException> exceptions);

    /**
     * Retrieves an instantiable child managed object.
     *
     * @param <C>
     *            The requested type of the child managed object configuration
     *            client.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param r
     *            The instantiable relation definition.
     * @param name
     *            The name of the child managed object.
     * @return Returns the instantiable child managed object.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with this
     *             managed object's definition.
     * @throws DefinitionDecodingException
     *             If the managed object was found but its type could not be
     *             determined.
     * @throws ManagedObjectDecodingException
     *             If the managed object was found but one or more of its
     *             properties could not be decoded.
     * @throws ManagedObjectNotFoundException
     *             If the requested managed object could not be found on the
     *             server.
     * @throws ConcurrentModificationException
     *             If this managed object has been removed from the server by
     *             another client.
     * @throws LdapException
     *             If any other error occurs.
     */
    <C extends ConfigurationClient, S extends Configuration> ManagedObject<? extends C> getChild(
            InstantiableRelationDefinition<C, S> r, String name) throws
            DefinitionDecodingException, ManagedObjectDecodingException, ManagedObjectNotFoundException,
            ConcurrentModificationException, LdapException;

    /**
     * Retrieves an optional child managed object.
     *
     * @param <C>
     *            The requested type of the child managed object configuration
     *            client.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param r
     *            The optional relation definition.
     * @return Returns the optional child managed object.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with this
     *             managed object's definition.
     * @throws DefinitionDecodingException
     *             If the managed object was found but its type could not be
     *             determined.
     * @throws ManagedObjectDecodingException
     *             If the managed object was found but one or more of its
     *             properties could not be decoded.
     * @throws ManagedObjectNotFoundException
     *             If the requested managed object could not be found on the
     *             server.
     * @throws ConcurrentModificationException
     *             If this managed object has been removed from the server by
     *             another client.
     * @throws LdapException
     *             If any other error occurs.
     */
    <C extends ConfigurationClient, S extends Configuration> ManagedObject<? extends C> getChild(
            OptionalRelationDefinition<C, S> r) throws DefinitionDecodingException,
            ManagedObjectDecodingException, ManagedObjectNotFoundException, ConcurrentModificationException,
            LdapException;

    /**
     * Retrieves a singleton child managed object.
     *
     * @param <C>
     *            The requested type of the child managed object configuration
     *            client.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param r
     *            The singleton relation definition.
     * @return Returns the singleton child managed object.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with this
     *             managed object's definition.
     * @throws DefinitionDecodingException
     *             If the managed object was found but its type could not be
     *             determined.
     * @throws ManagedObjectDecodingException
     *             If the managed object was found but one or more of its
     *             properties could not be decoded.
     * @throws ManagedObjectNotFoundException
     *             If the requested managed object could not be found on the
     *             server.
     * @throws ConcurrentModificationException
     *             If this managed object has been removed from the server by
     *             another client.
     * @throws LdapException
     *             If any other error occurs.
     */
    <C extends ConfigurationClient, S extends Configuration> ManagedObject<? extends C> getChild(
            SingletonRelationDefinition<C, S> r) throws DefinitionDecodingException,
            ManagedObjectDecodingException, ManagedObjectNotFoundException, ConcurrentModificationException,
            LdapException;

    /**
     * Retrieves a set child managed object.
     *
     * @param <C>
     *            The requested type of the child managed object configuration
     *            client.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param r
     *            The set relation definition.
     * @param name
     *            The name of the child managed object.
     * @return Returns the set child managed object.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with this
     *             managed object's definition.
     * @throws DefinitionDecodingException
     *             If the managed object was found but its type could not be
     *             determined.
     * @throws ManagedObjectDecodingException
     *             If the managed object was found but one or more of its
     *             properties could not be decoded.
     * @throws ManagedObjectNotFoundException
     *             If the requested managed object could not be found on the
     *             server.
     * @throws ConcurrentModificationException
     *             If this managed object has been removed from the server by
     *             another client.
     * @throws LdapException
     *             If any other error occurs.
     */
    <C extends ConfigurationClient, S extends Configuration> ManagedObject<? extends C> getChild(
            SetRelationDefinition<C, S> r, String name) throws DefinitionDecodingException,
            ManagedObjectDecodingException, ManagedObjectNotFoundException, ConcurrentModificationException,
            LdapException;

    /**
     * Creates a client configuration view of this managed object. Modifications
     * made to this managed object will be reflected in the client configuration
     * view and vice versa.
     *
     * @return Returns a client configuration view of this managed object.
     */
    T getConfiguration();

    /**
     * Gets the definition associated with this managed object.
     *
     * @return Returns the definition associated with this managed object.
     */
    ManagedObjectDefinition<T, ? extends Configuration> getManagedObjectDefinition();

    /**
     * Gets the path of this managed object.
     *
     * @return Returns the path of this managed object.
     */
    ManagedObjectPath<T, ? extends Configuration> getManagedObjectPath();

    /**
     * Gets a mutable copy of the set of default values for the specified
     * property.
     *
     * @param <P>
     *            The type of the property to be retrieved.
     * @param pd
     *            The property to be retrieved.
     * @return Returns the property's default values, or an empty set if there
     *         are no default values defined.
     * @throws IllegalArgumentException
     *             If the property definition is not associated with this
     *             managed object's definition.
     */
    <P> SortedSet<P> getPropertyDefaultValues(PropertyDefinition<P> pd);

    /**
     * Gets the effective value of the specified property.
     * <p>
     * See the class description for more information about how the effective
     * property value is derived.
     *
     * @param <P>
     *            The type of the property to be retrieved.
     * @param pd
     *            The property to be retrieved.
     * @return Returns the property's effective value, or <code>null</code> if
     *         there is no effective value defined.
     * @throws IllegalArgumentException
     *             If the property definition is not associated with this
     *             managed object's definition.
     */
    <P> P getPropertyValue(PropertyDefinition<P> pd);

    /**
     * Gets a mutable copy of the set of effective values for the specified
     * property.
     * <p>
     * See the class description for more information about how the effective
     * property values are derived.
     *
     * @param <P>
     *            The type of the property to be retrieved.
     * @param pd
     *            The property to be retrieved.
     * @return Returns the property's effective values, or an empty set if there
     *         are no effective values defined.
     * @throws IllegalArgumentException
     *             If the property definition is not associated with this
     *             managed object's definition.
     */
    @Override
    <P> SortedSet<P> getPropertyValues(PropertyDefinition<P> pd);

    /**
     * Determines whether the specified property is set. If the property
     * is unset, then any default behavior associated with the property applies.
     *
     * @param pd
     *            The property definition.
     * @return Returns <code>true</code> if the property has been set, or
     *         <code>false</code> if it is unset and any default behavior
     *         associated with the property applies.
     * @throws IllegalArgumentException
     *             If the property definition is not associated with this
     *             managed object's definition.
     */
    boolean isPropertyPresent(PropertyDefinition<?> pd);

    /**
     * Determines whether the optional managed object associated with the
     * specified optional relations exists.
     *
     * @param <C>
     *            The type of client managed object configuration that the
     *            relation definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param r
     *            The optional relation definition.
     * @return Returns <code>true</code> if the optional managed object exists,
     *         <code>false</code> otherwise.
     * @throws ConcurrentModificationException
     *             If this managed object has been removed from the server by
     *             another client.
     * @throws LdapException
     *             If there is any other error.
     */
    <C extends ConfigurationClient, S extends Configuration> boolean hasChild(OptionalRelationDefinition<C, S> r)
            throws ConcurrentModificationException, LdapException;

    /**
     * Lists the child managed objects associated with the specified
     * instantiable relation.
     *
     * @param <C>
     *            The type of client managed object configuration that the
     *            relation definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param r
     *            The instantiable relation definition.
     * @return Returns the names of the child managed objects.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with this
     *             managed object's definition.
     * @throws ConcurrentModificationException
     *             If this managed object has been removed from the server by
     *             another client.
     * @throws LdapException
     *             If any other error occurs.
     */
    <C extends ConfigurationClient, S extends Configuration> String[] listChildren(
            InstantiableRelationDefinition<C, S> r) throws ConcurrentModificationException,
            LdapException;

    /**
     * Lists the child managed objects associated with the specified
     * instantiable relation which are a sub-type of the specified managed
     * object definition.
     *
     * @param <C>
     *            The type of client managed object configuration that the
     *            relation definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param r
     *            The instantiable relation definition.
     * @param d
     *            The managed object definition.
     * @return Returns the names of the child managed objects which are a
     *         sub-type of the specified managed object definition.
     * @throws ConcurrentModificationException
     *             If this managed object has been removed from the server by
     *             another client.
     * @throws LdapException
     *             If any other error occurs.
     */
    <C extends ConfigurationClient, S extends Configuration> String[] listChildren(
            InstantiableRelationDefinition<C, S> r, AbstractManagedObjectDefinition<? extends C, ? extends S> d)
            throws ConcurrentModificationException, LdapException;

    /**
     * Lists the child managed objects associated with the specified set
     * relation.
     *
     * @param <C>
     *            The type of client managed object configuration that the
     *            relation definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param r
     *            The set relation definition.
     * @return Returns the names of the child managed objects which for set
     *         relations are the definition names of each managed object.
     * @throws ConcurrentModificationException
     *             If this managed object has been removed from the server by
     *             another client.
     * @throws LdapException
     *             If any other error occurs.
     */
    <C extends ConfigurationClient, S extends Configuration> String[] listChildren(SetRelationDefinition<C, S> r)
            throws ConcurrentModificationException, LdapException;

    /**
     * Lists the child managed objects associated with the specified set
     * relation which are a sub-type of the specified managed object definition.
     *
     * @param <C>
     *            The type of client managed object configuration that the
     *            relation definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param r
     *            The set relation definition.
     * @param d
     *            The managed object definition.
     * @return Returns the names of the child managed objects which for set
     *         relations are the definition names of each managed object.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with this
     *             managed object's definition.
     * @throws ConcurrentModificationException
     *             If this managed object has been removed from the server by
     *             another client.
     * @throws LdapException
     *             If any other error occurs.
     */
    <C extends ConfigurationClient, S extends Configuration> String[] listChildren(SetRelationDefinition<C, S> r,
            AbstractManagedObjectDefinition<? extends C, ? extends S> d) throws
            ConcurrentModificationException, LdapException;

    /**
     * Removes the named instantiable child managed object.
     *
     * @param <C>
     *            The type of client managed object configuration that the
     *            relation definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param r
     *            The instantiable relation definition.
     * @param name
     *            The name of the child managed object to be removed.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with this
     *             managed object's definition.
     * @throws ManagedObjectNotFoundException
     *             If the managed object could not be removed because it could
     *             not found on the server.
     * @throws OperationRejectedException
     *             If the managed object cannot be removed due to some
     *             client-side or server-side constraint which cannot be
     *             satisfied (for example, if it is referenced by another
     *             managed object).
     * @throws ConcurrentModificationException
     *             If this managed object has been removed from the server by
     *             another client.
     * @throws LdapException
     *             If any other error occurs.
     */
    <C extends ConfigurationClient, S extends Configuration> void removeChild(InstantiableRelationDefinition<C, S> r,
            String name) throws ManagedObjectNotFoundException, OperationRejectedException,
            ConcurrentModificationException, LdapException;

    /**
     * Removes an optional child managed object.
     *
     * @param <C>
     *            The type of client managed object configuration that the
     *            relation definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param r
     *            The optional relation definition.
     * @throws ManagedObjectNotFoundException
     *             If the managed object could not be removed because it could
     *             not found on the server.
     * @throws OperationRejectedException
     *             If the managed object cannot be removed due to some
     *             client-side or server-side constraint which cannot be
     *             satisfied (for example, if it is referenced by another
     *             managed object).
     * @throws ConcurrentModificationException
     *             If this managed object has been removed from the server by
     *             another client.
     * @throws LdapException
     *             If any other error occurs.
     */
    <C extends ConfigurationClient, S extends Configuration> void removeChild(OptionalRelationDefinition<C, S> r)
            throws ManagedObjectNotFoundException, OperationRejectedException,
            ConcurrentModificationException, LdapException;

    /**
     * Removes s set child managed object.
     *
     * @param <C>
     *            The type of client managed object configuration that the
     *            relation definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param r
     *            The set relation definition.
     * @param name
     *            The name of the child managed object to be removed.
     * @throws ManagedObjectNotFoundException
     *             If the managed object could not be removed because it could
     *             not found on the server.
     * @throws OperationRejectedException
     *             If the managed object cannot be removed due to some
     *             client-side or server-side constraint which cannot be
     *             satisfied (for example, if it is referenced by another
     *             managed object).
     * @throws ConcurrentModificationException
     *             If this managed object has been removed from the server by
     *             another client.
     * @throws LdapException
     *             If any other error occurs.
     */
    <C extends ConfigurationClient, S extends Configuration> void removeChild(SetRelationDefinition<C, S> r,
        String name) throws ManagedObjectNotFoundException, OperationRejectedException,
            ConcurrentModificationException, LdapException;

    /**
     * Sets a new pending value for the specified property.
     * <p>
     * See the class description for more information regarding pending values.
     *
     * @param <P>
     *            The type of the property to be modified.
     * @param pd
     *            The property to be modified.
     * @param value
     *            The new pending value for the property, or <code>null</code>
     *            if the property should be reset to its default behavior.
     * @throws PropertyException
     *             If this is not a new managed object and the property is
     *             read-only or for monitoring purposes.
     * @throws PropertyException
     *             If an attempt was made to remove a mandatory property.
     */
    <P> void setPropertyValue(PropertyDefinition<P> pd, P value);

    /**
     * Sets a new pending values for the specified property.
     * <p>
     * See the class description for more information regarding pending values.
     *
     * @param <P>
     *            The type of the property to be modified.
     * @param pd
     *            The property to be modified.
     * @param values
     *            A non-<code>null</code> set of new pending values for the
     *            property (an empty set indicates that the property should be
     *            reset to its default behavior). The set will not be referenced
     *            by this managed object.
     * @throws PropertyException
     *             If an attempt was made to add multiple pending values to a
     *             single-valued property.
     * @throws PropertyException
     *             If this is not a new managed object and the property is
     *             read-only or for monitoring purposes.
     * @throws PropertyException
     *             If an attempt was made to remove a mandatory property.
     */
    <P> void setPropertyValues(PropertyDefinition<P> pd, Collection<P> values);

}
