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

import java.util.Collection;
import java.util.Collections;

import org.forgerock.opendj.config.client.ClientConstraintHandler;
import org.forgerock.opendj.config.server.ServerConstraintHandler;

/**
 * An interface for enforcing constraints and dependencies between managed
 * objects and their properties. Constraints express relationships between
 * managed objects and their properties, for example:
 * <ul>
 * <li>referential integrity: where one managed object references another a
 * constraint can enforce referential integrity. The constraint can prevent
 * creation of references to non-existent managed objects, and also prevent
 * deletion of referenced managed objects
 * <li>property dependencies: for example, when a boolean property is
 * <code>true</code>, one or more additional properties must be specified. This
 * is useful for features like SSL, which when enabled, requires that various
 * SSL related configuration options are specified
 * <li>property constraints: for example, when an upper limit property must not
 * have a value which is less than the lower limit property.
 * </ul>
 * On the client-side constraints are enforced immediately before a write
 * operation is performed. That is to say, immediately before a new managed
 * object is created, changes to a managed object are applied, or an existing
 * managed object is deleted.
 */
public abstract class Constraint {

    /** Creates a new constraint. */
    protected Constraint() {
        // No implementation required.
    }

    /**
     * Gets the client-side constraint handlers which will be used to enforce
     * this constraint in client applications. The default implementation is to
     * return an empty set of client constraint handlers.
     *
     * @return Returns the client-side constraint handlers which will be used to
     *         enforce this constraint in client applications. The returned
     *         collection must not be <code>null</code> but maybe empty
     *         (indicating that the constraint can only be enforced on the
     *         server-side).
     */
    public Collection<ClientConstraintHandler> getClientConstraintHandlers() {
        return Collections.emptySet();
    }

    /**
     * Gets the server-side constraint handlers which will be used to enforce
     * this constraint within the server. The default implementation is to
     * return an empty set of server constraint handlers.
     *
     * @return Returns the server-side constraint handlers which will be used to
     *         enforce this constraint within the server. The returned
     *         collection must not be <code>null</code> and must not be empty,
     *         since constraints must always be enforced on the server.
     */
    public Collection<ServerConstraintHandler> getServerConstraintHandlers() {
        return Collections.emptySet();
    }

    /**
     * Initializes this constraint. The default implementation is to do nothing.
     *
     * @throws Exception
     *             If this constraint could not be initialized.
     */
    protected void initialize() throws Exception {
        // Default implementation is to do nothing.
    }

}
