/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.forgerock.opendj.config.client;

import java.io.Closeable;
import java.util.SortedSet;

import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.Configuration;
import org.forgerock.opendj.config.ConfigurationClient;
import org.forgerock.opendj.config.DefinitionDecodingException;
import org.forgerock.opendj.config.InstantiableRelationDefinition;
import org.forgerock.opendj.config.ManagedObjectNotFoundException;
import org.forgerock.opendj.config.ManagedObjectPath;
import org.forgerock.opendj.config.OptionalRelationDefinition;
import org.forgerock.opendj.config.PropertyDefinition;
import org.forgerock.opendj.config.PropertyException;
import org.forgerock.opendj.config.SetRelationDefinition;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.server.config.client.RootCfgClient;

/**
 * Client management connection context.
 */
public interface ManagementContext extends Closeable {

    /**
     * Deletes the named instantiable child managed object from the named parent
     * managed object.
     *
     * @param <C>
     *            The type of client managed object configuration that the
     *            relation definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param parent
     *            The path of the parent managed object.
     * @param rd
     *            The instantiable relation definition.
     * @param name
     *            The name of the child managed object to be removed.
     * @return Returns <code>true</code> if the named instantiable child managed
     *         object was found, or <code>false</code> if it was not found.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with the parent
     *             managed object's definition.
     * @throws ManagedObjectNotFoundException
     *             If the parent managed object could not be found.
     * @throws OperationRejectedException
     *             If the managed object cannot be removed due to some
     *             client-side or server-side constraint which cannot be
     *             satisfied (for example, if it is referenced by another
     *             managed object).
     * @throws LdapException
     *             If any other error occurs.
     */
    <C extends ConfigurationClient, S extends Configuration> boolean deleteManagedObject(
            ManagedObjectPath<?, ?> parent, InstantiableRelationDefinition<C, S> rd, String name)
            throws ManagedObjectNotFoundException, OperationRejectedException, LdapException;

    /**
     * Deletes the optional child managed object from the named parent managed
     * object.
     *
     * @param <C>
     *            The type of client managed object configuration that the
     *            relation definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param parent
     *            The path of the parent managed object.
     * @param rd
     *            The optional relation definition.
     * @return Returns <code>true</code> if the optional child managed object
     *         was found, or <code>false</code> if it was not found.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with the parent
     *             managed object's definition.
     * @throws ManagedObjectNotFoundException
     *             If the parent managed object could not be found.
     * @throws OperationRejectedException
     *             If the managed object cannot be removed due to some
     *             client-side or server-side constraint which cannot be
     *             satisfied (for example, if it is referenced by another
     *             managed object).
     * @throws LdapException
     *             If any other error occurs.
     */
    <C extends ConfigurationClient, S extends Configuration> boolean deleteManagedObject(
            ManagedObjectPath<?, ?> parent, OptionalRelationDefinition<C, S> rd) throws
            ManagedObjectNotFoundException, OperationRejectedException, LdapException;

    /**
     * Deletes s set child managed object from the named parent managed object.
     *
     * @param <C>
     *            The type of client managed object configuration that the
     *            relation definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param parent
     *            The path of the parent managed object.
     * @param rd
     *            The set relation definition.
     * @param name
     *            The name of the child managed object to be removed.
     * @return Returns <code>true</code> if the set child managed object was
     *         found, or <code>false</code> if it was not found.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with the parent
     *             managed object's definition.
     * @throws ManagedObjectNotFoundException
     *             If the parent managed object could not be found.
     * @throws OperationRejectedException
     *             If the managed object cannot be removed due to some
     *             client-side or server-side constraint which cannot be
     *             satisfied (for example, if it is referenced by another
     *             managed object).
     * @throws LdapException
     *             If any other error occurs.
     */
    <C extends ConfigurationClient, S extends Configuration> boolean deleteManagedObject(
            ManagedObjectPath<?, ?> parent, SetRelationDefinition<C, S> rd, String name)
            throws ManagedObjectNotFoundException, OperationRejectedException, LdapException;

    /**
     * Gets the named managed object.
     *
     * @param <C>
     *            The type of client managed object configuration that the path
     *            definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the path
     *            definition refers to.
     * @param path
     *            The path of the managed object.
     * @return Returns the named managed object.
     * @throws DefinitionDecodingException
     *             If the managed object was found but its type could not be
     *             determined.
     * @throws ManagedObjectDecodingException
     *             If the managed object was found but one or more of its
     *             properties could not be decoded.
     * @throws ManagedObjectNotFoundException
     *             If the requested managed object could not be found on the
     *             server.
     * @throws LdapException
     *             If any other error occurs.
     */
    <C extends ConfigurationClient, S extends Configuration> ManagedObject<? extends C> getManagedObject(
            ManagedObjectPath<C, S> path) throws DefinitionDecodingException, ManagedObjectDecodingException,
            ManagedObjectNotFoundException, LdapException;

    /**
     * Gets the effective value of a property in the named managed object.
     *
     * @param <P>
     *            The type of the property to be retrieved.
     * @param path
     *            The path of the managed object containing the property.
     * @param pd
     *            The property to be retrieved.
     * @return Returns the property's effective value, or <code>null</code> if
     *         there are no values defined.
     * @throws IllegalArgumentException
     *             If the property definition is not associated with the
     *             referenced managed object's definition.
     * @throws DefinitionDecodingException
     *             If the managed object was found but its type could not be
     *             determined.
     * @throws PropertyException
     *             If the managed object was found but the requested property
     *             could not be decoded.
     * @throws ManagedObjectNotFoundException
     *             If the requested managed object could not be found on the
     *             server.
     * @throws LdapException
     *             If any other error occurs.
     */
    <P> P getPropertyValue(ManagedObjectPath<?, ?> path, PropertyDefinition<P> pd)
            throws DefinitionDecodingException, LdapException, ManagedObjectNotFoundException;

    /**
     * Gets the effective values of a property in the named managed object.
     *
     * @param <P>
     *            The type of the property to be retrieved.
     * @param path
     *            The path of the managed object containing the property.
     * @param pd
     *            The property to be retrieved.
     * @return Returns the property's effective values, or an empty set if there
     *         are no values defined.
     * @throws IllegalArgumentException
     *             If the property definition is not associated with the
     *             referenced managed object's definition.
     * @throws DefinitionDecodingException
     *             If the managed object was found but its type could not be
     *             determined.
     * @throws PropertyException
     *             If the managed object was found but the requested property
     *             could not be decoded.
     * @throws ManagedObjectNotFoundException
     *             If the requested managed object could not be found on the
     *             server.
     * @throws LdapException
     *             If any other error occurs.
     */
    <P> SortedSet<P> getPropertyValues(ManagedObjectPath<?, ?> path, PropertyDefinition<P> pd)
            throws DefinitionDecodingException, LdapException, ManagedObjectNotFoundException;

    /**
     * Gets the root configuration client associated with this management
     * context.
     *
     * @return Returns the root configuration client associated with this
     *         management context.
     */
    RootCfgClient getRootConfiguration();

    /**
     * Gets the root configuration managed object associated with this
     * management context.
     *
     * @return Returns the root configuration managed object associated with
     *         this management context.
     */
    ManagedObject<RootCfgClient> getRootConfigurationManagedObject();

    /**
     * Lists the child managed objects of the named parent managed object.
     *
     * @param <C>
     *            The type of client managed object configuration that the
     *            relation definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param parent
     *            The path of the parent managed object.
     * @param rd
     *            The instantiable relation definition.
     * @return Returns the names of the child managed objects.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with the parent
     *             managed object's definition.
     * @throws ManagedObjectNotFoundException
     *             If the parent managed object could not be found.
     * @throws LdapException
     *             If any other error occurs.
     */
    <C extends ConfigurationClient, S extends Configuration> String[] listManagedObjects(
            ManagedObjectPath<?, ?> parent, InstantiableRelationDefinition<C, S> rd) throws
            ManagedObjectNotFoundException, LdapException;

    /**
     * Lists the child managed objects of the named parent managed object which
     * are a sub-type of the specified managed object definition.
     *
     * @param <C>
     *            The type of client managed object configuration that the
     *            relation definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param parent
     *            The path of the parent managed object.
     * @param rd
     *            The instantiable relation definition.
     * @param d
     *            The managed object definition.
     * @return Returns the names of the child managed objects which are a
     *         sub-type of the specified managed object definition.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with the parent
     *             managed object's definition.
     * @throws ManagedObjectNotFoundException
     *             If the parent managed object could not be found.
     * @throws LdapException
     *             If any other error occurs.
     */
    <C extends ConfigurationClient, S extends Configuration> String[] listManagedObjects(
            ManagedObjectPath<?, ?> parent, InstantiableRelationDefinition<C, S> rd,
            AbstractManagedObjectDefinition<? extends C, ? extends S> d) throws
            ManagedObjectNotFoundException, LdapException;

    /**
     * Lists the child managed objects of the named parent managed object.
     *
     * @param <C>
     *            The type of client managed object configuration that the
     *            relation definition refers to.
     * @param <S>
     *            The type of server managed object configuration that the
     *            relation definition refers to.
     * @param parent
     *            The path of the parent managed object.
     * @param rd
     *            The set relation definition.
     * @return Returns the names of the child managed objects.
     * @throws IllegalArgumentException
     *             If the relation definition is not associated with the parent
     *             managed object's definition.
     * @throws ManagedObjectNotFoundException
     *             If the parent managed object could not be found.
     * @throws LdapException
     *             If any other error occurs.
     */
    <C extends ConfigurationClient, S extends Configuration> String[] listManagedObjects(
            ManagedObjectPath<?, ?> parent, SetRelationDefinition<C, S> rd) throws
            ManagedObjectNotFoundException, LdapException;

    /**
     * Determines whether or not the named managed object exists.
     *
     * @param path
     *            The path of the named managed object.
     * @return Returns <code>true</code> if the named managed object exists,
     *         <code>false</code> otherwise.
     * @throws ManagedObjectNotFoundException
     *             If the parent managed object could not be found.
     * @throws LdapException
     *             If any other error occurs.
     */
    boolean managedObjectExists(ManagedObjectPath<?, ?> path) throws ManagedObjectNotFoundException, LdapException;
}
