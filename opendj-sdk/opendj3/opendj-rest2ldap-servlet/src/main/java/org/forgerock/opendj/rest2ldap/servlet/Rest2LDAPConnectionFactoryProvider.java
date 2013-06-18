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
 * Copyright 2013 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap.servlet;

import static org.forgerock.json.resource.Resources.*;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.*;

import java.io.InputStream;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.Connection;
import org.forgerock.json.resource.ConnectionFactory;
import org.forgerock.json.resource.FutureResult;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.json.resource.Router;
import org.forgerock.opendj.rest2ldap.AuthorizationPolicy;
import org.forgerock.opendj.rest2ldap.Rest2LDAP;
import org.forgerock.opendj.rest2ldap.Rest2LDAP.Builder;

import com.forgerock.opendj.util.StaticUtils;

/**
 * The connection factory provider which is used by the OpenDJ Commons REST LDAP
 * Gateway.
 */
public final class Rest2LDAPConnectionFactoryProvider {
    private static final String INIT_PARAM_CONFIG_FILE = "config-file";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper().configure(
            JsonParser.Feature.ALLOW_COMMENTS, true);

    /**
     * Returns a JSON resource connection factory configured using the
     * configuration file named in the {@code config-file} Servlet
     * initialization parameter. See the sample configuration file for a
     * detailed description of its content.
     *
     * @param config
     *            The Servlet configuration.
     * @return The configured JSON resource connection factory.
     * @throws ServletException
     *             If the connection factory could not be initialized.
     * @see Rest2LDAP#configureConnectionFactory(JsonValue, String)
     * @see Builder#configureMapping(JsonValue)
     */
    public static ConnectionFactory getConnectionFactory(final ServletConfig config)
            throws ServletException {
        final String configFileName = config.getInitParameter(INIT_PARAM_CONFIG_FILE);
        if (configFileName == null) {
            throw new ServletException("Servlet initialization parameter '"
                    + INIT_PARAM_CONFIG_FILE + "' not specified");
        }
        final InputStream configFile =
                config.getServletContext().getResourceAsStream(configFileName);
        if (configFile == null) {
            throw new ServletException("Servlet configuration file '" + configFileName
                    + "' not found");
        }
        try {
            // Parse the config file.
            final Object content = JSON_MAPPER.readValue(configFile, Object.class);
            if (!(content instanceof Map)) {
                throw new ServletException("Servlet configuration file '" + configFileName
                        + "' does not contain a valid JSON configuration");
            }
            final JsonValue configuration = new JsonValue(content);

            // Parse the authorization configuration.
            final AuthorizationPolicy authzPolicy =
                    configuration.get("servlet").get("authorizationPolicy").required().asEnum(
                            AuthorizationPolicy.class);
            final String proxyAuthzTemplate =
                    configuration.get("servlet").get("proxyAuthzIdTemplate").asString();

            // Parse the connection factory if present.
            final String ldapFactoryName =
                    configuration.get("servlet").get("ldapConnectionFactory").asString();
            final org.forgerock.opendj.ldap.ConnectionFactory ldapFactory;
            if (ldapFactoryName != null) {
                ldapFactory =
                        configureConnectionFactory(configuration.get("ldapConnectionFactories")
                                .required(), ldapFactoryName);
            } else {
                ldapFactory = null;
            }

            // Create the router.
            final Router router = new Router();
            final JsonValue mappings = configuration.get("servlet").get("mappings").required();
            for (final String mappingUrl : mappings.keys()) {
                final JsonValue mapping = mappings.get(mappingUrl);
                final CollectionResourceProvider provider =
                        Rest2LDAP.builder().ldapConnectionFactory(ldapFactory).authorizationPolicy(
                                authzPolicy).proxyAuthzIdTemplate(proxyAuthzTemplate)
                                .configureMapping(mapping).build();
                router.addRoute(mappingUrl, provider);
            }
            final ConnectionFactory factory = newInternalConnectionFactory(router);
            if (ldapFactory != null) {
                /*
                 * Return a wrapper which will release resources associated with
                 * the LDAP connection factory (pooled connections, transport,
                 * etc).
                 */
                return new ConnectionFactory() {
                    @Override
                    public FutureResult<Connection> getConnectionAsync(
                            ResultHandler<? super Connection> handler) {
                        return factory.getConnectionAsync(handler);
                    }

                    @Override
                    public Connection getConnection() throws ResourceException {
                        return factory.getConnection();
                    }

                    @Override
                    public void close() {
                        ldapFactory.close();
                    }
                };
            } else {
                return factory;
            }
        } catch (final ServletException e) {
            // Rethrow.
            throw e;
        } catch (final Exception e) {
            throw new ServletException("Servlet configuration file '" + configFileName
                    + "' could not be read: " + e.getMessage());
        } finally {
            StaticUtils.closeSilently(configFile);
        }
    }

    // Prevent instantiation.
    private Rest2LDAPConnectionFactoryProvider() {
        // Nothing to do.
    }

}
