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
 * Copyright 2012-2013 ForgeRock AS.
 */

package org.forgerock.opendj.rest2ldap;

import static org.forgerock.json.resource.Resources.newInternalConnectionFactory;
import static org.forgerock.opendj.ldap.Connections.newAuthenticatedConnectionFactory;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.builder;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.constant;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.object;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.simple;
import static org.forgerock.opendj.rest2ldap.WritabilityPolicy.CREATE_ONLY;
import static org.forgerock.opendj.rest2ldap.WritabilityPolicy.READ_ONLY;

import java.util.Arrays;
import java.util.logging.Logger;

import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.Router;
import org.forgerock.json.resource.servlet.HttpServlet;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.requests.Requests;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.servlet.ServletRegistration;
import org.glassfish.grizzly.servlet.WebappContext;

/**
 * Example.
 */
public class Example {

    private static final Logger LOGGER = Logger.getLogger(Example.class.getName());
    private static final int PORT = 18890;

    /**
     * REST 2 LDAP example application.
     *
     * @param args
     *            Command line arguments.
     * @throws Exception
     *             If an unexpected error occurred.
     */
    public static void main(final String[] args) throws Exception {
        // All LDAP resources will use this connection factory.
        final ConnectionFactory ldapFactory =
                newAuthenticatedConnectionFactory(new LDAPConnectionFactory("localhost", 1389),
                        Requests.newSimpleBindRequest("cn=directory manager", "password"
                                .toCharArray()));

        // Create the router.
        final Router router = new Router();

        // Create user resource.
        CollectionResourceProvider users =
                builder().factory(ldapFactory).baseDN("ou=people,dc=example,dc=com")
                    .attribute("schemas", constant(Arrays.asList("urn:scim:schemas:core:1.0")))
                    .attribute("id", simple("uid").isSingleValued().isRequired().writability(CREATE_ONLY))
                    .attribute("rev", simple("etag").isSingleValued().writability(READ_ONLY))
                    .attribute("userName", simple("mail").isSingleValued().writability(READ_ONLY))
                    .attribute("displayName", simple("cn").isSingleValued().isRequired())
                    .attribute("name", object()
                            .attribute("givenName", simple("givenName").isSingleValued())
                            .attribute("familyName", simple("sn").isSingleValued().isRequired()))
                    .attribute("contactInformation", object()
                            .attribute("telephoneNumber", simple("telephoneNumber").isSingleValued())
                            .attribute("emailAddress", simple("mail").isSingleValued()))
                    .additionalLDAPAttribute("objectClass", "top", "person", "organizationalPerson", "inetOrgPerson")
                    .build();
        router.addRoute("/users", users);

        // Create group resource.
        CollectionResourceProvider groups =
                builder().factory(ldapFactory).baseDN("ou=groups,dc=example,dc=com")
                    .attribute("cn", simple("cn").isSingleValued())
                    .attribute("description", simple("description"))
                    .attribute("member", simple("uniquemember"))
                    .build();
        router.addRoute("/groups", groups);

        final org.forgerock.json.resource.ConnectionFactory resourceFactory =
                newInternalConnectionFactory(router);
        final HttpServer httpServer = HttpServer.createSimpleServer("./", PORT);
        try {
            final WebappContext ctx = new WebappContext("example", "/example");
            final ServletRegistration reg =
                    ctx.addServlet("managed", new HttpServlet(resourceFactory));
            reg.addMapping("/managed/*");
            ctx.deploy(httpServer);

            LOGGER.info("Starting server...");
            httpServer.start();
            LOGGER.info("Server started");
            LOGGER.info("Press any key to stop the server...");
            System.in.read();
        } finally {
            LOGGER.info("Stopping server...");
            httpServer.stop();
            LOGGER.info("Server stopped");
        }
    }

}
