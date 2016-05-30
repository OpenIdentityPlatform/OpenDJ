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

import static org.forgerock.opendj.rest2ldap.Rest2ldapMessages.*;
import static org.forgerock.opendj.rest2ldap.authz.Utils.newIllegalArgumentException;
import static org.forgerock.util.Utils.joinAsString;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.forgerock.json.JsonPointer;
import org.forgerock.json.JsonValue;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.services.context.SecurityContext;

/**
 * An authorization ID template used for mapping security context principals to
 * AuthzID templates of the form
 * <code>dn:uid={uid},ou={realm},dc=example,dc=com</code>, or
 * <code>u:{uid}@{realm}.example.com</code>.
 */
final class AuthzIdTemplate {

    private interface Impl {
        String formatAsAuthzId(AuthzIdTemplate t, Object[] templateVariables);
    }

    private static final Impl DN_TEMPLATE_IMPL = new Impl() {
        @Override
        public String formatAsAuthzId(final AuthzIdTemplate t, final Object[] templateVariables) {
            // We're not interested in matching and place-holder attribute types can be tolerated,
            // so we can just use the core schema.
            return DN.format(t.formatString, Schema.getCoreSchema(), templateVariables).toString();
        }
    };

    private static final Impl UID_TEMPLATE_IMPL = new Impl() {
        @Override
        public String formatAsAuthzId(final AuthzIdTemplate t, final Object[] templateVariables) {
            return String.format(Locale.ENGLISH, t.formatString, templateVariables);
        }
    };

    private static final Pattern TEMPLATE_KEY_RE = Pattern.compile("\\{([^}]+)\\}");

    private enum TemplateType {
        DN ("dn:", SecurityContext.AUTHZID_DN, DN_TEMPLATE_IMPL),
        UID ("u:", SecurityContext.AUTHZID_ID, UID_TEMPLATE_IMPL);

        private final String key;
        private final String securityContextId;
        private final Impl impl;

        TemplateType(final String key, final String securityContextId, final Impl impl) {
            this.key = key;
            this.securityContextId = securityContextId;
            this.impl = impl;
        }

        private String getSecurityContextId() {
            return securityContextId;
        }

        private Impl getImpl() {
            return impl;
        }

        private static TemplateType parseTemplateType(final String template) {
            for (final TemplateType type : TemplateType.values()) {
                if (template.startsWith(type.key)) {
                    return type;
                }
            }
            throw newIllegalArgumentException(
                    ERR_CONFIG_INVALID_AUTHZID_TEMPLATE.get(template, joinAsString(",", getSupportedStartKeys())));
        }

        private static List<String> getSupportedStartKeys() {
            final List<String> startKeys = new ArrayList<>();
            for (final TemplateType type : TemplateType.values()) {
                startKeys.add(type.key);
            }
            return startKeys;
        }

        private String removeTemplateKey(final String formattedString) {
            return formattedString.substring(key.length()).trim();
        }
    }

    private final TemplateType type;
    private final String formatString;
    private final List<String> keys = new ArrayList<>();
    private final String template;

    /**
     * Create a new authorization ID template.
     *
     * @param template
     *            Authorization ID template
     * @throws IllegalArgumentException
     *             if template doesn't start with "u:" or "dn:"
     */
    AuthzIdTemplate(final String template) {
        this.type = TemplateType.parseTemplateType(template);
        this.formatString = formatTemplate(template);
        this.template = template;
    }

    private String formatTemplate(final String template) {
        // Parse the template keys and replace them with %s for formatting.
        final Matcher matcher = TEMPLATE_KEY_RE.matcher(template);
        final StringBuffer buffer = new StringBuffer(template.length());
        while (matcher.find()) {
            matcher.appendReplacement(buffer, "%s");
            keys.add(matcher.group(1));
        }
        matcher.appendTail(buffer);
        return type.removeTemplateKey(buffer.toString());
    }

    @Override
    public String toString() {
        return template;
    }

    String getSecurityContextID() {
        return this.type.getSecurityContextId();
    }

    /**
     * Return the template with all the variable replaced.
     *
     * @param principals
     *            Value to use to replace the variables.
     * @return The template with all the variable replaced.
     */
    String formatAsAuthzId(final JsonValue principals) {
        final String[] templateVariables = getPrincipalsForFormatting(principals);
        return type.getImpl().formatAsAuthzId(this, templateVariables);
    }

    private String[] getPrincipalsForFormatting(final JsonValue principals) {
        final String[] values = new String[keys.size()];
        for (int i = 0; i < values.length; i++) {
            final String key = keys.get(i);
            final JsonValue value = principals.get(new JsonPointer(key));
            if (value == null) {
                throw newIllegalArgumentException(ERR_AUTHZID_DECODER_PRINCIPAL_CANNOT_BE_DETERMINED.get(key));
            }

            final Object object = value.getObject();
            if (!isJSONPrimitive(object)) {
                throw newIllegalArgumentException(ERR_AUTHZID_DECODER_PRINCIPAL_INVALID_DATA_TYPE.get(key));
            }
            values[i] = String.valueOf(object);
        }
        return values;
    }

    private boolean isJSONPrimitive(final Object value) {
        return value instanceof String || value instanceof Boolean || value instanceof Number;
    }
}
