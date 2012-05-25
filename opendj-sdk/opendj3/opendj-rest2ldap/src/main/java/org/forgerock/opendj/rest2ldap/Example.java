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

import org.forgerock.json.resource.restlet.JsonResourceRestlet;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Functions;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.resource.framework.JsonResourceProvider;
import org.forgerock.resource.framework.impl.ResourceInvoker;
import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Restlet;
import org.restlet.data.Protocol;
import org.restlet.routing.Router;
import org.restlet.routing.Template;

/**
 * Example
 */
public class Example {

    private Application application = new Application();
    private final Router router = new Router();

    private void bindJsonResource(JsonResourceProvider resource, String path) {
        Restlet restlet = new JsonResourceRestlet(resource);
        restlet.setContext(application.getContext());
        router.attach(path, restlet, Template.MODE_EQUALS);
        router.attach(path + (path.equals("/") ? "" : "/"), restlet, Template.MODE_STARTS_WITH);
    }

    public void start() throws Exception {
        // All LDAP resources will use this connection factory.
        ConnectionFactory factory =
                Connections.newAuthenticatedConnectionFactory(new LDAPConnectionFactory(
                        "localhost", 1389), Requests.newSimpleBindRequest("cn=directory manager",
                        "password".toCharArray()));

        // Create two entry containers whose members reference each other.
        EntryContainer userContainer =
                new EntryContainer(DN.valueOf("ou=people,dc=example,dc=com"), factory);
        EntryContainer groupContainer =
                new EntryContainer(DN.valueOf("ou=groups,dc=example,dc=com"), factory);

        // Create user resource.
        AttributeMapper userMapper =
                new CompositeAttributeMapper().addMapper(
                        new DefaultAttributeMapper().includeAttribute("uid", "isMemberOf",
                                "modifyTimestamp")).addMapper(
                        new ComplexAttributeMapper("name", new DefaultAttributeMapper()
                                .includeAttribute("cn", "sn", "givenName"))).addMapper(
                        new ComplexAttributeMapper("contactInformation",
                                new CompositeAttributeMapper().addMapper(
                                        new SimpleAttributeMapper("telephoneNumber").withDecoder(
                                                Functions.byteStringToString()).forceSingleValued(
                                                true)).addMapper(
                                        new SimpleAttributeMapper("emailAddress", "mail")
                                                .forceSingleValued(true))));
        LDAPResource userResource = new LDAPResource(userContainer, userMapper);
        ResourceInvoker userResourceInvoker = new ResourceInvoker();
        userResourceInvoker.resource = userResource; // FIXME: Yuk!
        bindJsonResource(userResourceInvoker, "/users");

        // Create group resource.
        AttributeMapper groupMapper =
                new DefaultAttributeMapper().includeAttribute("cn", "ou", "description",
                        "uniquemember");
        LDAPResource groupResource = new LDAPResource(groupContainer, groupMapper);
        ResourceInvoker groupResourceInvoker = new ResourceInvoker();
        groupResourceInvoker.resource = groupResource; // FIXME: Yuk!
        bindJsonResource(groupResourceInvoker, "/groups");

        // Configure and start the application.
        application.getTunnelService().setQueryTunnel(false);
        application.setInboundRoot(router);
        Component component = new Component();
        component.getServers().add(Protocol.HTTP, 8080);
        component.getDefaultHost().attach("", application);
        component.start();
    }

    public static void main(String[] args) throws Exception {
        Example instance = new Example();
        instance.start();
    }
}
