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
 */
package org.forgerock.opendj.rest2ldap;

import static org.forgerock.opendj.rest2ldap.Rest2Ldap.DECODE_OPTIONS;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.services.context.Context;
import org.forgerock.util.Options;

/**
 * Represents a DN template whose RDN values may be substituted with URL template parameters parsed during routing.
 * Two types of DN template are supported: {@link #compile(String) absolute/relative} or {@link #compileRelative(String)
 * relative}. The table below shows how DN templates will be resolved to DNs when the template parameter "subdomain"
 * has the value "www" and the current routing state references the DN "dc=example,dc=com":
 * <p>
 * <table>
 * <tr><th>DN Template</th><th>{@link #compile(String)}</th><th>{@link #compileRelative(String)}</th></tr>
 * <tr><td>dc=www</td><td>dc=www</td><td>dc=www,dc=example,dc=com</td></tr>
 * <tr><td>..</td><td>dc=com</td><td>dc=com</td></tr>
 * <tr><td>dc={subdomain}</td><td>dc=www</td><td>dc=www,dc=example,dc=com</td></tr>
 * <tr><td>dc={subdomain},..</td><td>dc=www,dc=com</td><td>dc=www,dc=com</td></tr>
 * </table>
 */
final class DnTemplate {
    private static final Pattern TEMPLATE_VARIABLE_RE = Pattern.compile("\\{([^}]+)\\}");
    private final String template;
    private final String formatString;
    private final List<String> variables;
    /** A value of -1 means that this DN template is absolute. */
    private final int relativeOffset;

    /**
     * Compiles a DN template which will resolve LDAP entries relative to the current routing state. The DN template may
     * contain trailing ".." RDNs in order to resolve entries which are relative to a parent of the current routing
     * state.
     *
     * @param template
     *         The string representation of the DN template.
     * @return The compiled DN template.
     */
    static DnTemplate compileRelative(String template) {
        return compile(template, true);
    }

    /**
     * Compiles a DN template which will resolve LDAP entries relative to the root DSE by default, but MAY include
     * relative RDNs indicating that the DN template will be resolved against current routing state instead.
     *
     * @param template
     *         The string representation of the DN template.
     * @return The compiled DN template.
     */
    static DnTemplate compile(String template) {
        return compile(template, false);
    }

    private static DnTemplate compile(String template, boolean isRelative) {
        // Parse any trailing relative RDNs.
        String trimmedTemplate;
        int relativeOffset;

        if (template.equals("..")) {
            trimmedTemplate = "";
            relativeOffset = 1;
        } else if (template.endsWith(",..")) {
            relativeOffset = 0;
            for (trimmedTemplate = template;
                 trimmedTemplate.endsWith(",..");
                 trimmedTemplate = trimmedTemplate.substring(0, trimmedTemplate.length() - 3)) {
                relativeOffset++;
            }
        } else if (isRelative) {
            trimmedTemplate = template;
            relativeOffset = 0;
        } else {
            trimmedTemplate = template;
            relativeOffset = -1;
        }

        final List<String> templateVariables = new ArrayList<>();
        final Matcher matcher = TEMPLATE_VARIABLE_RE.matcher(trimmedTemplate);
        final StringBuffer buffer = new StringBuffer(trimmedTemplate.length());
        while (matcher.find()) {
            matcher.appendReplacement(buffer, "%s");
            templateVariables.add(matcher.group(1));
        }
        matcher.appendTail(buffer);
        return new DnTemplate(trimmedTemplate, buffer.toString(), templateVariables, relativeOffset);
    }

    private DnTemplate(String template, String formatString, List<String> variables, int relativeOffset) {
        this.template = template;
        this.formatString = formatString;
        this.variables = variables;
        this.relativeOffset = relativeOffset;
    }

    DN format(final Context context) {
        // First determine the base DN based on the context DN and the relative offset.
        DN baseDn = null;
        if (relativeOffset >= 0 && context.containsContext(RoutingContext.class)) {
            final RoutingContext routingContext = context.asContext(RoutingContext.class);
            baseDn = routingContext.getDn().parent(routingContext.isCollection() ? relativeOffset - 1 : relativeOffset);
        }
        if (baseDn == null) {
            baseDn = DN.rootDN();
        }

        // Construct a DN using any routing template parameters.
        final Options options = context.asContext(Rest2LdapContext.class).getRest2ldap().getOptions();
        final Schema schema = options.get(DECODE_OPTIONS).getSchemaResolver().resolveSchema(template);
        if (variables.isEmpty()) {
            final DN relativeDn = DN.valueOf(template, schema);
            return baseDn.child(relativeDn);
        } else {
            final String[] values = new String[variables.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = getTemplateParameter(context, variables.get(i));
            }
            final DN relativeDn = DN.format(formatString, schema, (Object[]) values);
            return baseDn.child(relativeDn);
        }
    }

    private String getTemplateParameter(final Context context, final String parameter) {
        UriRouterContext uriRouterContext = context.asContext(UriRouterContext.class);
        for (;;) {
            final Map<String, String> uriTemplateVariables = uriRouterContext.getUriTemplateVariables();
            final String value = uriTemplateVariables.get(parameter);
            if (value != null) {
                return value;
            }
            if (!uriRouterContext.getParent().containsContext(UriRouterContext.class)) {
                throw new IllegalStateException("DN template parameter \"" + parameter + "\" cannot be resolved");
            }
            uriRouterContext = uriRouterContext.getParent().asContext(UriRouterContext.class);
        }
    }
}
