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
 *      Copyright 2010 Sun Microsystems, Inc.
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
