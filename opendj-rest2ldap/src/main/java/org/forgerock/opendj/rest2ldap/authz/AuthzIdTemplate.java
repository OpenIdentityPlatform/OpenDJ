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
 * Copyright 2013-2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap.authz;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.Schema;

/**
 * An authorization ID template used for mapping security context principals to
 * AuthzID templates of the form
 * <code>dn:uid={uid},ou={realm},dc=example,dc=com</code>, or
 * <code>u:{uid}@{realm}.example.com</code>.
 */
final class AuthzIdTemplate {
    private static interface Impl {
        String formatAsAuthzId(AuthzIdTemplate t, Object[] templateVariables, Schema schema);
    }

    private static final Impl DN_IMPL = new Impl() {

        @Override
        public String formatAsAuthzId(final AuthzIdTemplate t, final Object[] templateVariables,
                final Schema schema) {
            final String authzId = String.format(Locale.ENGLISH, t.formatString, templateVariables);
            try {
                // Validate the DN.
                DN.valueOf(authzId.substring(3), schema);
            } catch (final IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "The request could not be authorized because the required security principal "
                        + "was not a valid LDAP DN");
            }
            return authzId;
        }
    };

    private static final Pattern DN_PATTERN = Pattern.compile("^dn:\\{[^}]+\\}$");

    private static final Impl DN_TEMPLATE_IMPL = new Impl() {

        @Override
        public String formatAsAuthzId(final AuthzIdTemplate t, final Object[] templateVariables,
                final Schema schema) {
            return "dn:" + DN.format(t.dnFormatString, schema, templateVariables);
        }

    };

    private static final Pattern KEY_RE = Pattern.compile("\\{([^}]+)\\}");

    private static final Impl UID_TEMPLATE_IMPL = new Impl() {

        @Override
        public String formatAsAuthzId(final AuthzIdTemplate t, final Object[] templateVariables,
                final Schema schema) {
            return String.format(Locale.ENGLISH, t.formatString, templateVariables);
        }

    };

    private final String dnFormatString;
    private final String formatString;
    private final List<String> keys = new ArrayList<>();
    private final Impl pimpl;
    private final String template;

    /**
     * Create a new authorization ID template.
     *
     * @param template
     *            Authorization ID template
     * @throws IllegalArgumentException
     *             if template doesn't start with "u:" or "dn:"
     */
    public AuthzIdTemplate(final String template) {
        if (!template.startsWith("u:") && !template.startsWith("dn:")) {
            throw new IllegalArgumentException("Invalid authorization ID template: " + template);
        }

        // Parse the template keys and replace them with %s for formatting.
        final Matcher matcher = KEY_RE.matcher(template);
        final StringBuffer buffer = new StringBuffer(template.length());
        while (matcher.find()) {
            matcher.appendReplacement(buffer, "%s");
            keys.add(matcher.group(1));
        }
        matcher.appendTail(buffer);
        this.formatString = buffer.toString();
        this.template = template;

        if (template.startsWith("dn:")) {
            this.pimpl = DN_PATTERN.matcher(template).matches() ? DN_IMPL : DN_TEMPLATE_IMPL;
            this.dnFormatString = formatString.substring(3);
        } else {
            this.pimpl = UID_TEMPLATE_IMPL;
            this.dnFormatString = null;
        }
    }

    @Override
    public String toString() {
        return template;
    }

    /**
     * Return the template with all the variable replaced.
     *
     * @param principals
     *            Value to use to replace the variables.
     * @param schema
     *            Schema to perform validation.
     * @return The template with all the variable replaced.
     */
    public String formatAsAuthzId(final Map<String, Object> principals, final Schema schema) {
        final String[] templateVariables = getPrincipalsForFormatting(principals);
        return pimpl.formatAsAuthzId(this, templateVariables, schema);
    }

    private String[] getPrincipalsForFormatting(final Map<String, Object> principals) {
        final String[] values = new String[keys.size()];
        for (int i = 0; i < values.length; i++) {
            final String key = keys.get(i);
            final Object value = principals.get(key);
            if (isJSONPrimitive(value)) {
                values[i] = String.valueOf(value);
            } else if (value != null) {
                throw new IllegalArgumentException(String.format(
                        "The request could not be authorized because the required "
                                + "security principal '%s' had an invalid data type", key));
            } else {
                throw new IllegalArgumentException(String.format(
                        "The request could not be authorized because the required "
                                + "security principal '%s' could not be determined", key));
            }
        }
        return values;
    }

    static boolean isJSONPrimitive(final Object value) {
        return value instanceof String || value instanceof Boolean || value instanceof Number;
    }
}
