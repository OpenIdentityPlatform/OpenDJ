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
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.server.config;

import static java.nio.charset.Charset.defaultCharset;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.util.Properties;
import java.util.regex.Pattern;

import org.forgerock.util.encode.Base64;

/** Functions which can be invoked from within expressions. */
final class Functions {
    // URL scheme: alpha *( alpha | digit | "+" | "-" | "." )
    private static final Pattern URL_SCHEME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9+-.]*:.*");

    public static String trim(String value) {
        return value != null ? value.trim() : null;
    }

    public static String encodeBase64(final String value) {
        return value != null ? Base64.encode(value.getBytes()) : null;
    }

    public static String decodeBase64(final String value) {
        return value != null ? new String(Base64.decode(value)) : null;
    }

    public static String read(final String url) throws Exception {
        try (final BufferedReader reader = new BufferedReader(open(url))) {
            final StringBuilder builder = new StringBuilder();
            boolean isFirst = true;
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!isFirst) {
                    builder.append(System.lineSeparator());
                }
                builder.append(line);
                isFirst = false;
            }
            return builder.toString();
        }
    }

    public static Properties readProperties(final String url) throws Exception {
        try (final Reader reader = open(url)) {
            final Properties properties = new Properties();
            properties.load(reader);
            return properties;
        }
    }

    private static InputStreamReader open(final String url) throws Exception {
        // Check if the URL is actually just a relative path name without a scheme. If it is then URL parsing will
        // fail, so parse it as a file and open it directly, otherwise assume we have a valid URL with a scheme.
        if (URL_SCHEME_PATTERN.matcher(url).matches()) {
            return new InputStreamReader(new URI(url).toURL().openStream(), defaultCharset());
        }
        return new InputStreamReader(new FileInputStream(url), defaultCharset());
    }

    private Functions() { /* Utility class. */ }
}
