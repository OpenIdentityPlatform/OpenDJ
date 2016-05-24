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
 * Portions Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.config;

/**
 * This interface is used to determine the "best match" managed object
 * definition in a definition hierarchy.
 * <p>
 * Managed object definitions, like Java classes, are arranged in an inheritance
 * hierarchy. When managed objects are decoded (e.g. from LDAP entries), the
 * driver implementation is provided with an
 * "expected managed object definition". However, the actual decoded managed
 * object is often an instance of a sub-type of this definition. For example,
 * when decoding a connection handler managed object, the actual type can never
 * be a connection handler because it is an abstract managed object type.
 * Instead, the decoded managed object must be a "concrete" sub-type: an LDAP
 * connection handler or JMX connection handler.
 * <p>
 * This resolution process is coordinated by the
 * <code>resolveManagedObjectDefinition</code> method in managed object
 * definitions, where it is passed a <code>DefinitionResolver</code>
 * implementation. The <code>resolveManagedObjectDefinition</code> method takes
 * care of recursively descending through the definition hierarchy and invokes
 * the {@link #matches(AbstractManagedObjectDefinition)} method against each
 * potential sub-type. It is the job of the resolver to indicate whether the
 * provided managed object definition is a candidate definition. For example,
 * the LDAP driver provides a definition resolver which uses the decoded LDAP
 * entry's object classes to determine the final appropriate managed object
 * definition.
 */
public interface DefinitionResolver {

    /**
     * Determines whether the provided managed object definition matches
     * this resolver's criteria.
     *
     * @param d
     *            The managed object definition.
     * @return Returns <code>true</code> if the the provided managed object
     *         definition matches this resolver's criteria.
     */
    boolean matches(AbstractManagedObjectDefinition<?, ?> d);
}
