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
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

/**
 * A strategy for serializing managed object paths.
 * <p>
 * This interface provides a generic means for serializing managed object paths
 * into application specific forms. For example, a client would use this
 * interface to construct {@code DN} objects from a path. Similarly,
 * on the server side, a serialization strategy is used to construct
 * <code>DN</code> instances from a path.
 * <p>
 * During serialization the serializer is invoked for each element in the
 * managed object path in big-endian order, starting from the root and
 * proceeding down to the leaf element.
 */
public interface ManagedObjectPathSerializer {

    /**
     * Append a managed object path element identified by an instantiable
     * relation and an instance name.
     *
     * @param <C>
     *            The type of client managed object configuration that this path
     *            element references.
     * @param <S>
     *            The type of server managed object configuration that this path
     *            element references.
     * @param r
     *            The instantiable relation.
     * @param d
     *            The managed object definition.
     * @param name
     *            The instance name.
     */
    <C extends ConfigurationClient, S extends Configuration> void appendManagedObjectPathElement(
        InstantiableRelationDefinition<? super C, ? super S> r, AbstractManagedObjectDefinition<C, S> d, String name);

    /**
     * Append a managed object path element identified by an optional relation.
     *
     * @param <C>
     *            The type of client managed object configuration that this path
     *            element references.
     * @param <S>
     *            The type of server managed object configuration that this path
     *            element references.
     * @param r
     *            The optional relation.
     * @param d
     *            The managed object definition.
     */
    <C extends ConfigurationClient, S extends Configuration> void appendManagedObjectPathElement(
        OptionalRelationDefinition<? super C, ? super S> r, AbstractManagedObjectDefinition<C, S> d);

    /**
     * Append a managed object path element identified by a singleton relation.
     *
     * @param <C>
     *            The type of client managed object configuration that this path
     *            element references.
     * @param <S>
     *            The type of server managed object configuration that this path
     *            element references.
     * @param r
     *            The singleton relation.
     * @param d
     *            The managed object definition.
     */
    <C extends ConfigurationClient, S extends Configuration> void appendManagedObjectPathElement(
        SingletonRelationDefinition<? super C, ? super S> r, AbstractManagedObjectDefinition<C, S> d);

    /**
     * Append a managed object path element identified by a set relation.
     *
     * @param <C>
     *            The type of client managed object configuration that this path
     *            element references.
     * @param <S>
     *            The type of server managed object configuration that this path
     *            element references.
     * @param r
     *            The set relation.
     * @param d
     *            The managed object definition.
     */
    <C extends ConfigurationClient, S extends Configuration> void appendManagedObjectPathElement(
        SetRelationDefinition<? super C, ? super S> r, AbstractManagedObjectDefinition<C, S> d);

}
