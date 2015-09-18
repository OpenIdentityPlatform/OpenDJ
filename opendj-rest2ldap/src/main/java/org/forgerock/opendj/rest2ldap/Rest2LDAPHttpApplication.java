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
 * Copyright 2015 ForgeRock AS.
 */

package org.forgerock.opendj.rest2ldap;

import static org.forgerock.http.util.Json.*;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.configureConnectionFactory;
import static org.forgerock.util.Utils.*;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.forgerock.http.Handler;
import org.forgerock.http.HttpApplication;
import org.forgerock.http.HttpApplicationException;
import org.forgerock.http.handler.Handlers;
import org.forgerock.http.io.Buffer;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.json.JsonValue;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.Router;
import org.forgerock.json.resource.http.CrestHttp;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.services.context.Context;
import org.forgerock.util.Factory;
import org.forgerock.util.Reject;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Rest2ldap HTTP application. */
public final class Rest2LDAPHttpApplication implements HttpApplication {
    private static final Logger LOG = LoggerFactory.getLogger(Rest2LDAPHttpApplication.class);

    private static final class HttpHandler implements Handler, Closeable {
        private final ConnectionFactory ldapConnectionFactory;
        private final Handler delegate;

        HttpHandler(final JsonValue configuration) {
            ldapConnectionFactory = createLdapConnectionFactory(configuration);
            try {
                delegate = CrestHttp.newHttpHandler(createRouter(configuration, ldapConnectionFactory));
            } catch (final RuntimeException e) {
                closeSilently(ldapConnectionFactory);
                throw e;
            }
        }

        private static RequestHandler createRouter(
                final JsonValue configuration, final ConnectionFactory ldapConnectionFactory) {
            final AuthorizationPolicy authzPolicy = configuration.get("servlet")
                    .get("authorizationPolicy")
                    .required()
                    .asEnum(AuthorizationPolicy.class);
            final String proxyAuthzTemplate = configuration.get("servlet").get("proxyAuthzIdTemplate").asString();
            final JsonValue mappings = configuration.get("servlet").get("mappings").required();

            final Router router = new Router();
            for (final String mappingUrl : mappings.keys()) {
                final JsonValue mapping = mappings.get(mappingUrl);
                final CollectionResourceProvider provider = Rest2LDAP.builder()
                        .ldapConnectionFactory(ldapConnectionFactory)
                        .authorizationPolicy(authzPolicy)
                        .proxyAuthzIdTemplate(proxyAuthzTemplate)
                        .configureMapping(mapping)
                        .build();
                router.addRoute(Router.uriTemplate(mappingUrl), provider);
            }
            return router;
        }

        private static ConnectionFactory createLdapConnectionFactory(final JsonValue configuration) {
            final String ldapFactoryName = configuration.get("servlet").get("ldapConnectionFactory").asString();
            if (ldapFactoryName != null) {
                return configureConnectionFactory(
                        configuration.get("ldapConnectionFactories").required(), ldapFactoryName);
            }
            return null;
        }

        @Override
        public void close() {
            closeSilently(ldapConnectionFactory);
        }

        @Override
        public Promise<Response, NeverThrowsException> handle(final Context context, final Request request) {
            return delegate.handle(context, request);
        }
    }

    private final URL configurationUrl;
    private HttpHandler handler;
    private HttpAuthenticationFilter filter;

    /**
     * Default constructor called by the HTTP Framework which will use the
     * default configuration file location.
     */
    public Rest2LDAPHttpApplication() {
        this.configurationUrl = getClass().getResource("/opendj-rest2ldap-config.json");
    }

    /**
     * Creates a new Rest2LDAP HTTP application using the provided configuration URL.
     *
     * @param configurationURL
     *            The URL to the JSON configuration file.
     */
    public Rest2LDAPHttpApplication(final URL configurationURL) {
        Reject.ifNull(configurationURL, "The configuration URL must not be null");
        this.configurationUrl = configurationURL;
    }

    private static JsonValue readJson(final URL resource) throws IOException {
        try (InputStream in = resource.openStream()) {
            return new JsonValue(readJsonLenient(in));
        }
    }

    @Override
    public Handler start() throws HttpApplicationException {
        try {
            final JsonValue configuration = readJson(configurationUrl);
            handler = new HttpHandler(configuration);
            filter = new HttpAuthenticationFilter(configuration);
            return Handlers.chainOf(handler, filter);
        } catch (final Exception e) {
            // TODO i18n, once supported in opendj-rest2ldap
            final String errorMsg = "Unable to start Rest2Ldap Http Application";
            LOG.error(errorMsg, e);
            stop();
            throw new HttpApplicationException(errorMsg, e);
        }
    }

    @Override
    public Factory<Buffer> getBufferFactory() {
        // Use container default buffer factory.
        return null;
    }

    @Override
    public void stop() {
        closeSilently(handler, filter);
        handler = null;
        filter = null;
    }
}
