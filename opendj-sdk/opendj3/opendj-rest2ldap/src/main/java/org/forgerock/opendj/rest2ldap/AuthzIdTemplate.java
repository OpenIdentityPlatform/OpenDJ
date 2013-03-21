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
 * Copyright 2013 ForgeRock AS.
 */

package org.forgerock.opendj.rest2ldap;

import static org.forgerock.opendj.rest2ldap.Utils.isJSONPrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.forgerock.json.resource.ForbiddenException;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.Schema;

/**
 * An authorization ID template used for mapping security context principals to
 * AuthzID templates of the form
 * <code>dn:uid={uid},ou={realm},dc=example,dc=com</code>, or
 * <code>u:{uid}@{realm}.example.com</code>.
 */
final class AuthzIdTemplate {
    private static final Pattern KEY_RE = Pattern.compile("\\{([^}]+)\\}");

    private final String dnFormatString;
    private final String formatString;
    private final List<String> keys = new ArrayList<String>();
    private final String template;

    AuthzIdTemplate(final String template) {
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

        this.template = template;
        this.formatString = buffer.toString();
        this.dnFormatString = template.startsWith("dn:") ? formatString.substring(3) : null;
    }

    @Override
    public String toString() {
        return template;
    }

    String formatAsAuthzId(final Map<String, Object> principals, final Schema schema)
            throws ResourceException {
        if (isDNTemplate()) {
            final String dn = formatAsDN(principals, schema).toString();
            final StringBuilder builder = new StringBuilder(dn.length() + 3);
            builder.append("dn:");
            builder.append(dn);
            return builder.toString();
        } else {
            final String[] values = getPrincipalsForFormatting(principals);
            return String.format(Locale.ENGLISH, formatString, (Object[]) values);
        }
    }

    DN formatAsDN(final Map<String, Object> principals, final Schema schema)
            throws ResourceException {
        if (!isDNTemplate()) {
            throw new IllegalStateException();
        }
        final String[] values = getPrincipalsForFormatting(principals);
        return DN.format(dnFormatString, schema, (Object[]) values);
    }

    boolean isDNTemplate() {
        return dnFormatString != null;
    }

    private String[] getPrincipalsForFormatting(final Map<String, Object> principals)
            throws ForbiddenException {
        final String[] values = new String[keys.size()];
        for (int i = 0; i < values.length; i++) {
            final String key = keys.get(i);
            final Object value = principals.get(key);
            if (isJSONPrimitive(value)) {
                values[i] = String.valueOf(value);
            } else if (value == null) {
                // FIXME: i18n.
                throw new ForbiddenException(
                        "The request could not be authorized because the required security principal "
                                + key + " could not be determined");
            } else {
                // FIXME: i18n.
                throw new ForbiddenException(
                        "The request could not be authorized because the required security principal "
                                + key + " had an invalid data type");
            }
        }
        return values;
    }
}
