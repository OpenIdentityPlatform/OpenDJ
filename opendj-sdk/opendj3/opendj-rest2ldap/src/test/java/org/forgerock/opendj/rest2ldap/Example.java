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
 * information: "Portions Copyrighted [year] [name of copyright owner]".
 *
 * Copyright © 2012 ForgeRock AS. All rights reserved.
 */

package org.forgerock.opendj.rest2ldap;

import static org.forgerock.json.resource.Resources.newInternalConnectionFactory;
import static org.forgerock.opendj.ldap.Connections.newAuthenticatedConnectionFactory;

import java.util.logging.Logger;

import org.forgerock.json.resource.Router;
import org.forgerock.json.resource.RoutingMode;
import org.forgerock.json.resource.servlet.HttpServlet;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
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
        final ConnectionFactory ldapFactory = newAuthenticatedConnectionFactory(
                new LDAPConnectionFactory("localhost", 1389), Requests.newSimpleBindRequest(
                        "cn=directory manager", "password".toCharArray()));

        // Create two entry containers whose members reference each other.
        final EntryContainer userContainer = new EntryContainer(DN
                .valueOf("ou=people,dc=example,dc=com"), ldapFactory);
        final EntryContainer groupContainer = new EntryContainer(DN
                .valueOf("ou=groups,dc=example,dc=com"), ldapFactory);

        // Create user resource.
        final AttributeMapper userMapper = new CompositeAttributeMapper().addMapper(
                new SimpleAttributeMapper("id", "entryUUID").singleValued(true)).addMapper(
                new DefaultAttributeMapper().includeAttribute("uid", "isMemberOf",
                        "modifyTimestamp")).addMapper(
                new ComplexAttributeMapper("name", new DefaultAttributeMapper().includeAttribute(
                        "cn", "sn", "givenName"))).addMapper(
                new ComplexAttributeMapper("contactInformation", new CompositeAttributeMapper()
                        .addMapper(
                                new SimpleAttributeMapper("telephoneNumber").decoder(
                                        Functions.byteStringToString()).singleValued(true))
                        .addMapper(
                                new SimpleAttributeMapper("emailAddress", "mail")
                                        .singleValued(true))));
        final LDAPCollectionResourceProvider userResource = new LDAPCollectionResourceProvider(
                userContainer, userMapper);

        // Create group resource.
        final AttributeMapper groupMapper = new DefaultAttributeMapper().includeAttribute("cn",
                "ou", "description", "uniquemember");
        final LDAPCollectionResourceProvider groupResource = new LDAPCollectionResourceProvider(
                groupContainer, groupMapper);

        // Create the router.
        final Router router = new Router();
        router.addRoute(RoutingMode.EQUALS, "/users", userResource);
        router.addRoute(RoutingMode.EQUALS, "/groups", groupResource);

        final org.forgerock.json.resource.ConnectionFactory resourceFactory = newInternalConnectionFactory(router);
        final HttpServer httpServer = HttpServer.createSimpleServer("./", PORT);
        try {
            final WebappContext ctx = new WebappContext("example", "/example");
            final ServletRegistration reg = ctx.addServlet("managed", new HttpServlet(
                    resourceFactory));
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
