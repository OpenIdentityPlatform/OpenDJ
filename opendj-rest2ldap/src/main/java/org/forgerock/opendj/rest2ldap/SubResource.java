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
 * Copyright 2016 ForgeRock AS.
 *
 */
package org.forgerock.opendj.rest2ldap;

import static org.forgerock.opendj.rest2ldap.Rest2Ldap.DECODE_OPTIONS;
import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.ERR_UNRECOGNIZED_SUB_RESOURCE_TYPE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizedIllegalArgumentException;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.NotFoundException;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.Router;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.services.context.Context;
import org.forgerock.util.Function;

/**
 * Defines a parent-child relationship between a parent resource and one or more child resource(s). Removal of the
 * parent resource implies that the children (the sub-resources) are also removed. There are two types of
 * sub-resource:
 * <ul>
 * <li>{@link SubResourceSingleton} represents a one-to-one relationship supporting read, update, patch, and action
 *     requests</li>
 * <li>{@link SubResourceCollection} represents a one-to-many relationship supporting all requests.</li>
 * </ul>
 */
public abstract class SubResource {
    private static final Pattern TEMPLATE_KEY_RE = Pattern.compile("\\{([^}]+)\\}");

    private final String resourceId;
    private final List<String> dnTemplateVariables = new ArrayList<>();
    private String dnTemplateFormatString;

    String urlTemplate = "";
    String dnTemplate = "";
    boolean isReadOnly = false;
    Rest2Ldap rest2Ldap;
    Resource resource;

    SubResource(final String resourceId) {
        this.resourceId = resourceId;
    }

    @Override
    public final boolean equals(final Object o) {
        return this == o || (o instanceof SubResource && urlTemplate.equals(((SubResource) o).urlTemplate));
    }

    @Override
    public final int hashCode() {
        return urlTemplate.hashCode();
    }

    @Override
    public final String toString() {
        return urlTemplate;
    }

    final Resource getResource() {
        return resource;
    }

    final void build(final Rest2Ldap rest2Ldap, final String parent) {
        this.rest2Ldap = rest2Ldap;
        this.resource = rest2Ldap.getResource(resourceId);
        if (resource == null) {
            throw new LocalizedIllegalArgumentException(ERR_UNRECOGNIZED_SUB_RESOURCE_TYPE.get(parent, resourceId));
        }
        this.dnTemplateFormatString = formatTemplate(dnTemplate, dnTemplateVariables);
    }

    // Parse the template keys and replace them with %s for formatting.
    private String formatTemplate(final String template, final List<String> templateVariables) {
        final Matcher matcher = TEMPLATE_KEY_RE.matcher(template);
        final StringBuffer buffer = new StringBuffer(template.length());
        while (matcher.find()) {
            matcher.appendReplacement(buffer, "%s");
            templateVariables.add(matcher.group(1));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    abstract Router addRoutes(Router router);

    /** A 404 indicates that this instance is not also a collection, so return a more helpful message. */
    static <T> Function<ResourceException, T, ResourceException> convert404To400(final LocalizableMessage msg) {
        return new Function<ResourceException, T, ResourceException>() {
            @Override
            public T apply(final ResourceException e) throws ResourceException {
                if (e instanceof NotFoundException) {
                    throw new BadRequestException(msg.toString());
                }
                throw e;
            }
        };
    }

    final RequestHandler readOnly(final RequestHandler handler) {
        return isReadOnly ? new ReadOnlyRequestHandler(handler) : handler;
    }

    final DN dnFrom(final Context context) {
        final DN baseDn = context.containsContext(RoutingContext.class)
                ? context.asContext(RoutingContext.class).getDn() : DN.rootDN();

        final Schema schema = rest2Ldap.getOptions().get(DECODE_OPTIONS).getSchemaResolver().resolveSchema(dnTemplate);
        if (dnTemplateVariables.isEmpty()) {
            final DN relativeDn = DN.valueOf(dnTemplate, schema);
            return baseDn.child(relativeDn);
        } else {
            final UriRouterContext uriRouterContext = context.asContext(UriRouterContext.class);
            final Map<String, String> uriTemplateVariables = uriRouterContext.getUriTemplateVariables();
            final String[] values = new String[dnTemplateVariables.size()];
            for (int i = 0; i < values.length; i++) {
                final String key = dnTemplateVariables.get(i);
                values[i] = uriTemplateVariables.get(key);
            }
            final DN relativeDn = DN.format(dnTemplateFormatString, schema, (Object[]) values);
            return baseDn.child(relativeDn);
        }
    }

    final RequestHandler subResourceRouterFrom(final RoutingContext context) {
        return context.getType().getSubResourceRouter();
    }
}
