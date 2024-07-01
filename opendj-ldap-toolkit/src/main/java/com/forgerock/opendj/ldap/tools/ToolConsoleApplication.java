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
 * Copyright 2016 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;


import java.io.PrintStream;

import com.forgerock.opendj.cli.ConsoleApplication;

/** Represents an OpenDJ client console-based. */
abstract class ToolConsoleApplication extends ConsoleApplication {

    ToolConsoleApplication(final PrintStream out, final PrintStream err) {
        super(out, err);
    }

    /**
     * Run this {@link ToolConsoleApplication} tool with the provided arguments.
     * Output and errors will be written on the provided streams.
     * This method can be used to run the tool programmatically.
     *
     * @param args
     *      Arguments set to pass to the tool.
     * @return
     *      An integer which represents the result code of the tool.
     * @throws LDAPToolException
     *      If an error occurs with either client inputs or tool execution.
     */
    abstract int run(final String... args) throws LDAPToolException;
}
