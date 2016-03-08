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

import static com.forgerock.opendj.ldap.tools.LDAPToolException.newToolExceptionAlreadyPrinted;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.ERR_ERROR_PARSING_ARGS;

import java.io.PrintStream;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.ConnectionFactoryProvider;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.BindRequest;

/**
 * Facility class to help ldap* tools to parse arguments and get {@link org.forgerock.opendj.ldap.ConnectionFactory}
 * and {@link org.forgerock.opendj.ldap.requests.BindRequest} objects back.
 */
final class LDAPToolArgumentParser extends ArgumentParser {

    private static final boolean WITH_BIND_REQUEST = true;
    private static final boolean WITHOUT_BIND_REQUEST = false;

    /**
     * Returns a builder which can be used for incrementally constructing a new
     * {@link LDAPToolArgumentParser}.
     *
     * @param mainClassName
     *         The tool main class name.
     * @return A builder to continue building the parser.
     */
    static Builder builder(final String mainClassName) {
        return new Builder(mainClassName);
    }

    ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    BindRequest getBindRequest() {
        return bindRequest;
    }

    /** A fluent API for incrementally constructing ldap argument parser. */
    static final class Builder {
        private final String mainClassName;
        private LocalizableMessage toolDescription;
        private boolean allowTrailingArgs;
        private int minTrailingArguments;
        private int maxTrailingArguments;
        private String trailingArgsDisplayName;
        private boolean needAuthenticatedConnectionFactory;

        Builder(final String mainClassName) {
            this.mainClassName = mainClassName;
        }

        Builder toolDescription(final LocalizableMessage toolDescription) {
            this.toolDescription = toolDescription;
            return this;
        }

        /** No trailing arguments number limitations. **/
        Builder trailingArguments(final String displayNames) {
            return trailingArguments(0, 0, displayNames);
        }

        /** Specify that the tool accept exactly the provided number of trailing argument(s). */
        Builder trailingArguments(final int nbTrailingArguments, final String displayNames) {
            return trailingArguments(nbTrailingArguments, nbTrailingArguments, displayNames);
        }

        /** Only specifying the minimum number of trailing arguments implies that this number has no upper bound. **/
        Builder trailingArgumentsUnbounded(final int min, final String displayNames) {
            return trailingArguments(min, 0, displayNames);
        }

        Builder trailingArguments(final int min, final int max, final String displayNames) {
            this.minTrailingArguments = min;
            this.maxTrailingArguments = max;
            this.trailingArgsDisplayName = displayNames;
            this.allowTrailingArgs = true;
            return this;
        }

        Builder needAuthenticatedConnectionFactory() {
            this.needAuthenticatedConnectionFactory = true;
            return this;
        }

        LDAPToolArgumentParser build() {
            return new LDAPToolArgumentParser(mainClassName, toolDescription, false,
                                              allowTrailingArgs, minTrailingArguments, maxTrailingArguments,
                                              trailingArgsDisplayName, needAuthenticatedConnectionFactory);
        }
    }

    private ConnectionFactory connectionFactory;
    private BindRequest bindRequest;
    private final boolean needAuthenticatedCF;

    private LDAPToolArgumentParser(final String mainClassName,
                                   final LocalizableMessage toolDescription,
                                   final boolean longArgumentsCaseSensitive,
                                   final boolean allowsTrailingArguments,
                                   final int minTrailingArguments,
                                   final int maxTrailingArguments,
                                   final String trailingArgsDisplayName,
                                   final boolean needAuthenticatedCF) {
        super(mainClassName, toolDescription, longArgumentsCaseSensitive, allowsTrailingArguments,
              minTrailingArguments, maxTrailingArguments, trailingArgsDisplayName);
        this.needAuthenticatedCF = needAuthenticatedCF;
    }

    void parseArguments(final String[] args, final PrintStream stream, final ConnectionFactoryProvider provider)
            throws LDAPToolException {
        parseLdapToolArgument(args, provider, stream, WITH_BIND_REQUEST);
    }

    void parseArgumentsNoBindRequest(final String[] args,
                                     final PrintStream stream,
                                     final ConnectionFactoryProvider provider) throws LDAPToolException {
        parseLdapToolArgument(args, provider, stream, WITHOUT_BIND_REQUEST);
    }

    private void parseLdapToolArgument(final String[] args,
                                       final ConnectionFactoryProvider provider,
                                       final PrintStream stream,
                                       final boolean withBindRequest) throws LDAPToolException {
        try {
            parseArguments(args);
            if (usageOrVersionDisplayed()) {
                return;
            }
            connectionFactory = needAuthenticatedCF ? provider.getAuthenticatedConnectionFactory()
                                                    : provider.getUnauthenticatedConnectionFactory();
            if (withBindRequest) {
                bindRequest = provider.getBindRequest();
            }
        } catch (final ArgumentException ae) {
            displayMessageAndUsageReference(stream, ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
            throw newToolExceptionAlreadyPrinted(ae, ResultCode.CLIENT_SIDE_PARAM_ERROR);
        }
    }
}
