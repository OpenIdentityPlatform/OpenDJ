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
 * Copyright 2010 Sun Microsystems, Inc.
 */

package com.forgerock.opendj.ldap.extensions;

/**
 * Password policy state operation container.
 */
interface PasswordPolicyStateOperationContainer {
    /**
     * Returns the name of the user targeted by this password policy state
     * operation.
     *
     * @return The name of the user targeted by this password policy state
     *         operation.
     */
    String getTargetUser();

    /**
     * Sets the name of the user targeted by this password policy state
     * operation.
     *
     * @param targetUser
     *            The name of the user targeted by this password policy state
     *            operation.
     */
    void setTargetUser(String targetUser);

    /**
     * Adds an operation to this container.
     *
     * @param operation
     *            The operation to be added.
     */
    void addOperation(PasswordPolicyStateOperation operation);

    /**
     * Returns the operations in this container.
     *
     * @return The operations in this container.
     */
    Iterable<PasswordPolicyStateOperation> getOperations();
}
