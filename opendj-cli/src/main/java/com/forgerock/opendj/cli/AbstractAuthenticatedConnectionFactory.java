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
 *      Copyright 2014 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.requests.BindRequest;

/**
 * Abstract authenticated connection factory implementation.
 */
abstract class AbstractAuthenticatedConnectionFactory {

    AbstractAuthenticatedConnectionFactory() {
        // No implementation required.
    }

    /**
     * Returns the new authenticated connection factory.
     *
     * @param connection
     *            The connection factory.
     * @param request
     *            The bind request.
     * @return A new authenticated connection factory.
     * @throws ArgumentException
     *             If an error occurs when parsing the arguments.
     */
    abstract ConnectionFactory newAuthenticatedConnectionFactory(final ConnectionFactory connection,
            final BindRequest request) throws ArgumentException;
}
