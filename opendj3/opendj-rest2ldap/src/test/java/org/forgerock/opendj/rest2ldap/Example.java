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
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.map;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.mapAllOf;
import static org.forgerock.opendj.rest2ldap.Rest2LDAP.mapComplex;

import java.util.logging.Logger;

import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.Router;
import org.forgerock.json.resource.servlet.HttpServlet;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.Functions;
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
                builder().factory(ldapFactory).baseDN("ou=people,dc=example,dc=com").map(
                        map("id", "entryUUID").singleValued(true),
                        mapAllOf("uid", "isMemberOf", "modifyTimestamp"),
                        mapComplex("name", mapAllOf("cn", "sn", "givenName")),
                        mapComplex("contactInformation", map("telephoneNumber").decoder(
                                Functions.byteStringToString()).singleValued(true), map(
                                "emailAddress", "mail").singleValued(true))).build();
        router.addRoute("/users", users);

        // Create group resource.
        CollectionResourceProvider groups =
                builder().factory(ldapFactory).baseDN("ou=groups,dc=example,dc=com").map(
                        mapAllOf("cn", "ou", "description", "uniquemember")).build();
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
