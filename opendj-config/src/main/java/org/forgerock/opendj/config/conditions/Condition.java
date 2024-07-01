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
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.conditions;

import org.forgerock.opendj.config.AbstractManagedObjectDefinition;
import org.forgerock.opendj.config.client.ManagedObject;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.config.server.ServerManagedObject;
import org.forgerock.opendj.ldap.LdapException;

/** An interface for evaluating conditions. */
public interface Condition {

    /**
     * Initializes this condition.
     *
     * @param d
     *            The abstract managed object definition associated with this
     *            condition.
     * @throws Exception
     *             If this condition could not be initialized.
     */
    void initialize(AbstractManagedObjectDefinition<?, ?> d) throws Exception;

    /**
     * Evaluates this condition against the provided client managed object.
     *
     * @param context
     *            The client management context.
     * @param managedObject
     *            The client managed object.
     * @return Returns <code>true</code> if this condition is satisfied.
     * @throws LdapException
     *             If the condition could not be evaluated.
     */
    boolean evaluate(ManagementContext context, ManagedObject<?> managedObject) throws LdapException;

    /**
     * Evaluates this condition against the provided server managed object.
     *
     * @param managedObject
     *            The server managed object.
     * @return Returns <code>true</code> if this condition is satisfied.
     * @throws ConfigException
     *             If the condition could not be evaluated due to an unexpected
     *             configuration exception.
     */
    boolean evaluate(ServerManagedObject<?> managedObject) throws ConfigException;
}
