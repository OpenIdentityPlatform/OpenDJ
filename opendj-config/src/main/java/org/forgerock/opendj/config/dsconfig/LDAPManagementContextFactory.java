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
 * Copyright 2007-2010 Sun Microsystems, Inc.
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.opendj.config.dsconfig;

import static com.forgerock.opendj.dsconfig.DsconfigMessages.*;
import static org.forgerock.util.Utils.closeSilently;

import javax.net.ssl.SSLException;

import org.forgerock.i18n.LocalizableMessage;
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
import com.forgerock.opendj.cli.ReturnCode;

/** An LDAP management context factory for the DSConfig tool. */
public final class LDAPManagementContextFactory {

    /** The management context. */
    private ManagementContext context;

    /** The connection parameters command builder. */
    private final CommandBuilder contextCommandBuilder;
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
        contextCommandBuilder = null;
    }

    /** Closes this management context. */
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
     * @return Returns the management context which sub-commands should use in order to manage the directory server.
     * @throws ArgumentException
     *             If a management context related argument could not be parsed successfully.
     * @throws ClientException
     *             If the management context could not be created.
     */
    public ManagementContext getManagementContext() throws ArgumentException, ClientException {
        // Lazily create the LDAP management context.
        if (context == null) {
            Connection connection;
            final String hostName = provider.getHostname();
            final int port = provider.getPort();
            try {
                connection = factory.getConnection();
                BuildVersion.checkVersionMismatch(connection);
            } catch (LdapException e) {
                LocalizableMessage msg = e.getCause() instanceof SSLException
                    ? ERR_FAILED_TO_CONNECT_NOT_TRUSTED.get(hostName, port)
                    : ERR_DSCFG_ERROR_LDAP_FAILED_TO_CONNECT.get(hostName, port);
                throw new ClientException(ReturnCode.CLIENT_SIDE_CONNECT_ERROR, msg);
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
