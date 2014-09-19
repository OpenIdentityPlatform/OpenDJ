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
 *      Copyright 2007-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.forgerock.opendj.config.dsconfig;

import static com.forgerock.opendj.dsconfig.DsconfigMessages.*;
import static com.forgerock.opendj.cli.CliMessages.*;
import static org.forgerock.util.Utils.closeSilently;

import javax.net.ssl.SSLException;

import org.forgerock.opendj.config.LDAPProfile;
import org.forgerock.opendj.config.client.ManagementContext;
import org.forgerock.opendj.config.client.ldap.LDAPManagementContext;
import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.CommandBuilder;
import com.forgerock.opendj.cli.ConnectionFactoryProvider;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.ReturnCode;

/**
 * An LDAP management context factory for the DSConfig tool.
 */
public final class LDAPManagementContextFactory {

    /** The management context. */
    private ManagementContext context;

    /** The connection parameters command builder. */
    private CommandBuilder contextCommandBuilder;

    /** The connection factory provider. */
    private final ConnectionFactoryProvider provider;

    /** The connection factory. */
    private final ConnectionFactory factory;

    /**
     * Creates a new LDAP management context factory based on an authenticated connection factory.
     *
     * @param cfp
     *            The connection factory provider which should be used in this context.
     * @throws ArgumentException
     *             If an exception occurs when creating the authenticated connection factory linked to this context.
     */
    public LDAPManagementContextFactory(ConnectionFactoryProvider cfp) throws ArgumentException {
        this.provider = cfp;
        factory = cfp.getAuthenticatedConnectionFactory();
    }

    /**
     * Closes this management context.
     */
    public void close() {
        closeSilently(context);
    }

    /**
     * Returns the command builder that provides the equivalent arguments in interactive mode to get the management
     * context.
     *
     * @return the command builder that provides the equivalent arguments in interactive mode to get the management
     *         context.
     */
    public CommandBuilder getContextCommandBuilder() {
        return contextCommandBuilder;
    }

    /**
     * Gets the management context which sub-commands should use in order to manage the directory server.
     *
     * @param app
     *            The console application instance.
     * @return Returns the management context which sub-commands should use in order to manage the directory server.
     * @throws ArgumentException
     *             If a management context related argument could not be parsed successfully.
     * @throws ClientException
     *             If the management context could not be created.
     */
    public ManagementContext getManagementContext(ConsoleApplication app) throws ArgumentException, ClientException {
        // Lazily create the LDAP management context.
        if (context == null) {
            Connection connection;
            final String hostName = provider.getHostname();
            final int port = provider.getPort();
            try {
                connection = factory.getConnection();
                BuildVersion.checkVersionMismatch(connection);
            } catch (LdapException e) {
                if (e.getCause() instanceof SSLException) {
                    throw new ClientException(ReturnCode.CLIENT_SIDE_CONNECT_ERROR,
                            ERR_FAILED_TO_CONNECT_NOT_TRUSTED.get(hostName, String.valueOf(port)));
                } else {
                    throw new ClientException(ReturnCode.CLIENT_SIDE_CONNECT_ERROR,
                            ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(hostName, String.valueOf(port)));
                }
            } catch (ConfigException e) {
                throw new ClientException(ReturnCode.ERROR_USER_DATA, e.getMessageObject());
            } catch (Exception ex) {
                throw new ClientException(ReturnCode.CLIENT_SIDE_CONNECT_ERROR,
                        ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(hostName, port));
            } finally {
                closeSilently(factory);
            }

            context = LDAPManagementContext.newManagementContext(connection, LDAPProfile.getInstance());
        }
        return context;
    }
}
