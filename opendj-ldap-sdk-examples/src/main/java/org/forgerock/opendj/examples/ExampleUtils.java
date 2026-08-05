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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.opendj.examples;

import org.forgerock.opendj.ldap.ResultCode;

/** Utility methods shared by the example client applications. */
final class ExampleUtils {

    /**
     * Returns the port number held by the provided command line argument.
     * <p>
     * Like the other command line argument checks of the examples, a value which is not a port
     * number is reported on standard error and stops the example, rather than failing it with a
     * {@code NumberFormatException} stack trace.
     *
     * @param arg
     *            The command line argument holding the port number.
     * @return The port number held by the provided argument.
     */
    static int parsePort(final String arg) {
        try {
            return Integer.parseInt(arg);
        } catch (final NumberFormatException e) {
            System.err.println("Invalid port number: " + arg);
            System.exit(ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue());
            return -1; // Never reached: System.exit() does not return.
        }
    }

    private ExampleUtils() {
        // Prevent instantiation.
    }
}
