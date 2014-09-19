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
 *      Portions Copyright 2014 ForgeRock AS
 */

package org.forgerock.opendj.ldap.requests;

import org.forgerock.opendj.ldap.ConnectionSecurityLayer;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.responses.BindResult;

/**
 * An authentication client which can be used to bind to a server. Specifically,
 * a bind client manages the state associated with multi-stage authentication
 * attempts and responds to any challenges returned by the server.
 */
public interface BindClient {
    /**
     * Disposes of any system resources or security-sensitive information that
     * this bind client might be using. Invoking this method invalidates this
     * instance.
     */
    void dispose();

    /**
     * Evaluates the provided bind result and returns {@code true} if
     * authentication has completed successfully, or {@code false} if additional
     * authentication steps are required (for example during a multi-stage SASL
     * authentication attempt).
     * <p>
     * If additional steps are required then implementations must update their
     * internal state based on information contained in the bind result (for
     * example, using the server provided SASL credentials).
     *
     * @param result
     *            The bind result to be evaluated.
     * @return {@code true} if authentication has completed successfully, of
     *         {@code false} if additional steps are required.
     * @throws LdapException
     *             If the evaluation failed for some reason and authentication
     *             cannot continue.
     */
    boolean evaluateResult(BindResult result) throws LdapException;

    /**
     * Returns a connection security layer, but only if this bind client has
     * negotiated integrity and/or privacy protection for the underlying
     * connection. This method should only be called once authentication has
     * completed.
     *
     * @return A connection security layer, or {@code null} if none was
     *         negotiated.
     */
    ConnectionSecurityLayer getConnectionSecurityLayer();

    /**
     * Returns the next bind request which should be used for the next stage of
     * authentication. Initially, this will be a copy of the original bind
     * request used to create this bind client.
     *
     * @return The next bind request which should be used for the next stage of
     *         authentication.
     */
    GenericBindRequest nextBindRequest();

}
