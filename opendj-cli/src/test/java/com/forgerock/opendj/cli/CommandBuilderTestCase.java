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
 * Copyright 2026 3A Systems, LLC.
 */
package com.forgerock.opendj.cli;

import static org.fest.assertions.Assertions.assertThat;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Quoting of the values in the equivalent command lines that the tools print. */
@SuppressWarnings("javadoc")
public final class CommandBuilderTestCase extends CliTestCase {

    /**
     * Values and their Windows form. A Windows program splits its command line by the rules of
     * the Microsoft C runtime: backslashes are literal unless they precede a double quote, where
     * each pair gives one backslash and an odd one escapes the quote.
     */
    @DataProvider
    public Object[][] windowsValues() {
        return new Object[][] {
            { "cn=a\\,b,dc=x", "\"cn=a\\,b,dc=x\"" },
            { "o=My Company", "\"o=My Company\"" },
            { "(targetattr=\"cn\")(version 3.0; acl \"x\"; allow (read) userdn=\"ldap:///self\";)",
                "\"(targetattr=\\\"cn\\\")(version 3.0; acl \\\"x\\\"; allow (read) userdn=\\\"ldap:///self\\\";)\"" },
            { "abc\\", "\"abc\\\\\"" },
            { "a\\\"b", "\"a\\\\\\\"b\"" },
            { "", "\"\"" },
        };
    }

    @Test(dataProvider = "windowsValues")
    public void testWindowsValueIsQuotedForTheCRuntime(String value, String expected) {
        assertThat(CommandBuilder.escapeValue(value, false)).isEqualTo(expected);
    }

    @Test
    public void testUnixValueIsBackslashEscaped() {
        assertThat(CommandBuilder.escapeValue("cn=a\\,b \"x\"", true)).isEqualTo("cn=a\\\\,b\\ \\\"x\\\"");
    }

    /** A dsconfig batch line takes an unescaped single quote as an opening quote. */
    @Test
    public void testUnixApostropheIsEscaped() {
        assertThat(CommandBuilder.escapeValue("description:O'Brien", true)).isEqualTo("description:O\\'Brien");
    }
}
