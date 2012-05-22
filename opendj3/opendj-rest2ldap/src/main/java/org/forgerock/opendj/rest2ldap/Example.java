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

import java.util.HashMap;
import java.util.Map;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.restlet.JsonResourceRestlet;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.Connections;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.LDAPConnectionFactory;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.resource.framework.JsonResourceProvider;
import org.forgerock.resource.framework.impl.ResourceInvoker;
import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Request;
import org.restlet.Restlet;
import org.restlet.data.Protocol;
import org.restlet.routing.Router;
import org.restlet.routing.Template;

/**
 * Example
 */
public class Example {

    private static final String PATH_PROPERTY = "rest2ldap.restlet.path";

    private Application application = new Application();
    private final Router router = new Router();
    private HashMap<JsonResourceProvider, Restlet> restlets =
            new HashMap<JsonResourceProvider, Restlet>();

    protected synchronized void bindRestlet(Restlet restlet, Map<String, Object> properties) {
        Object path = properties.get(PATH_PROPERTY);
        if (path != null && path instanceof String) { // service is specified as
                                                      // internally routable
            attach((String) path, restlet);
        }
    }

    protected synchronized void unbindRestlet(Restlet restlet, Map<String, Object> properties) {
        Object path = properties.get(PATH_PROPERTY);
        if (path != null && path instanceof String) { // service is specified as
                                                      // internally routable
            detach(restlet);
        }
    }

    /**
     * Attaches a target Restlet to the Restlet router based on a given URI
     * prefix.
     *
     * @param path
     *            the path to attach it to.
     * @param restlet
     *            the restlet to route to if path matches.
     * @throws IllegalArgumentException
     *             if path does not begin with a '/' character.
     */
    public void attach(String path, Restlet restlet) {
        restlet.setContext(application.getContext());
        router.attach(path, restlet, Template.MODE_EQUALS);
        router.attach(path + (path.equals("/") ? "" : "/"), restlet, Template.MODE_STARTS_WITH);
    }

    /**
     * Remove a restlet from restlet router
     *
     * @param restlet
     *            the restlet to remove
     */
    public void detach(Restlet restlet) {
        router.detach(restlet); // all routes to restlet are removed
    }

    protected synchronized void bindJsonResource(JsonResourceProvider resource,
            Map<String, Object> properties) {
        Restlet restlet = new CustomRestlet(resource);
        restlets.put(resource, restlet);
        bindRestlet(restlet, properties);
    }

    protected synchronized void unbindJsonResource(JsonResourceProvider resource,
            Map<String, Object> properties) {
        Restlet restlet = restlets.get(resource);
        if (restlet != null) {
            unbindRestlet(restlet, properties);
            restlets.remove(resource);
        }
    }

    private class CustomRestlet extends JsonResourceRestlet {
        public CustomRestlet(JsonResourceProvider resource) {
            super(resource);
        }

        @Override
        public JsonValue newContext(Request request) {
            JsonValue result = super.newContext(request);
            JsonValue security = result.get("security");
            security.put("openidm-roles", request.getAttributes().get("openidm.roles"));
            return result;
        }
    }

    public void start() throws Exception {
        Component component = new Component();

        // Add http listener
        component.getServers().add(Protocol.HTTP, 8080);
        application.getTunnelService().setQueryTunnel(false); // query string
                                                              // purism
        application.setInboundRoot(router);

        // Attach the json resource at the root path
        Map<String, Object> props = new HashMap<String, Object>();
        props.put(PATH_PROPERTY, "");

        AttributeMapper mapper = new IdentityAttributeMapper().excludeAttribute("entryUUID", "etag");
        ConnectionFactory factory =
                Connections.newAuthenticatedConnectionFactory(new LDAPConnectionFactory(
                        "localhost", 1389), Requests.newSimpleBindRequest("cn=directory manager",
                        "password".toCharArray()));
        EntryContainer container =
                new EntryContainer(DN.valueOf("ou=people,dc=example,dc=com"), factory);
        LDAPResource resource = new LDAPResource(container, mapper);
        ResourceInvoker invoker = new ResourceInvoker();
        invoker.resource = resource;
        bindJsonResource(invoker, props);

        // Attach the sample application.
        component.getDefaultHost().attach("/example", application);

        // Start the component.
        component.start();
    }

    public static void main(String[] args) throws Exception {
        Example instance = new Example();
        instance.start();
    }
}
