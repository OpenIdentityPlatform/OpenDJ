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
 * Portions copyright 2016 ForgeRock AS.
 * Portions Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.opendj.config.dsconfig;

import org.forgerock.testng.ForgeRockTestCase;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Test(groups = { "precommit", "config" })
public class DSConfigParseTest extends ForgeRockTestCase {
    @DataProvider
    public Object[][] escapeSequences() {
        return new Object[][] {
            {"global-aci:\\(targetattr=\\\"userPassword\\|\\|authPassword\\\"\\)"
                    + "\\(version\\ 3.0\\;\\ acl\\ \\\"Self\\ entry\\ read\\'\\\"\\;"
                    + "\\ allow\\ \\(read,search,compare\\)\\ userdn=\\\"ldap:///self\\\"\\;\\)",
                "global-aci:(targetattr=\"userPassword||authPassword\")"
                    + "(version 3.0; acl \"Self entry read'\";"
                    + " allow (read,search,compare) userdn=\"ldap:///self\";)"
            },
            {"cn=\"admin data\"", "cn=admin data"},
            {"\"cn=admin data\"", "cn=admin data"},
            {"cn=\\\"admin", "cn=\"admin"}
        };
    }

    @Test(dataProvider = "escapeSequences")
    public void testEscapeSequenceInCommandArgument(String arg, String value) throws Exception {
        Collection<String> cmdLine = DSConfig.toCommandArgs(arg);
        Assert.assertEquals(cmdLine.iterator().next(), value);
    }

    /** Batch lines and the arguments a POSIX shell splits them into. */
    @DataProvider
    public Object[][] shellQuoting() {
        return new Object[][] {
            // Inside double quotes a backslash is kept unless it precedes ", \, $ or `.
            { "--set base-dn:\"cn=a\\,b,dc=x\"", args("--set", "base-dn:cn=a\\,b,dc=x") },
            { "--set \"base-dn:cn=a\\2Cb,dc=x\"", args("--set", "base-dn:cn=a\\2Cb,dc=x") },
            { "\"a\\\"b\\\\c\\$d\\`e\"", args("a\"b\\c$d`e") },
            // Single quotes keep everything literally, a backslash included.
            { "--set 'base-dn:o=My Company'", args("--set", "base-dn:o=My Company") },
            { "--set 'base-dn:cn=a\\,b,dc=x'", args("--set", "base-dn:cn=a\\,b,dc=x") },
            { "'say \"hi\"' \"it's\"", args("say \"hi\"", "it's") },
            // Quoted and unquoted parts of one word make a single argument.
            { "base-dn:\"o=My \"'Company'", args("base-dn:o=My Company") },
            { "\"a\"b c", args("ab", "c") },
            // An empty quoted word is an empty argument.
            { "--set description:\"\" \"\"", args("--set", "description:", "") },
            // Tabs separate arguments as spaces do.
            { "a\tb  c", args("a", "b", "c") },
            // A trailing backslash has nothing to escape and is kept.
            { "a b\\", args("a", "b\\") },
        };
    }

    @Test(dataProvider = "shellQuoting")
    public void testShellQuotingInCommandLine(String line, List<String> expected) throws Exception {
        Assert.assertEquals(new ArrayList<>(DSConfig.toCommandArgs(line)), expected);
    }

    private static List<String> args(String... args) {
        return Arrays.asList(args);
    }
}
