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

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ReturnCode;

import org.forgerock.testng.ForgeRockTestCase;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
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

    /** Batch lines with a quote that is not closed: a POSIX shell rejects them. */
    @DataProvider
    public Object[][] unclosedQuotes() {
        return new Object[][] {
            // Before single quotes were supported, this line gave three arguments.
            { "--set description:O'Brien --advanced" },
            { "--set 'base-dn:o=My Company" },
            { "--set \"base-dn:o=My Company" },
            { "--set \"base-dn:o=My Company\\\"" },
        };
    }

    @Test(dataProvider = "unclosedQuotes", expectedExceptions = ArgumentException.class,
            expectedExceptionsMessageRegExp = "The following batch command has a quote that is not closed: .*")
    public void testUnclosedQuoteIsRejected(String line) throws Exception {
        DSConfig.toCommandArgs(line);
    }

    @Test
    public void testBatchCommandWithUnclosedQuoteFails() {
        final ByteArrayOutputStream err = new ByteArrayOutputStream();

        final int exitCode = DSConfig.runBatchCommand(args("--noPropertiesFile", "-n"), "set-x --set 'a",
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(err));

        Assert.assertEquals(exitCode, ReturnCode.ERROR_USER_DATA.get());
        Assert.assertTrue(text(err).contains("The following batch command has a quote that is not closed: "
                + "set-x --set 'a"), text(err));
    }

    @Test
    public void testBatchCommandKeepsEscapedTrailingBlank() {
        // dsconfig fails on the first argument, before it reads --no-prompt, so it still writes
        // the error to the output stream.
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final PrintStream stream = new PrintStream(output);

        // dsconfig has no such subcommand: the error quotes the argument it was given.
        final int exitCode = DSConfig.runBatchCommand(args("--noPropertiesFile", "-n"), "set-x\\ ", stream, stream);

        Assert.assertEquals(exitCode, ReturnCode.CONFLICTING_ARGS.get());
        Assert.assertTrue(text(output).contains("The provided argument \"set-x \" is not recognized"), text(output));
    }

    /** The output with every run of blanks and line breaks as a single space, as the console wraps lines. */
    private static String text(ByteArrayOutputStream out) {
        return new String(out.toByteArray()).replaceAll("\\s+", " ");
    }

    /** Batch files and the arguments of each command they hold. */
    @DataProvider
    public Object[][] batchFiles() {
        return new Object[][] {
            { "# comment\n\nset-x --foo bar\nset-y\n", commands(args("set-x", "--foo", "bar"), args("set-y")) },
            // A line of blanks is skipped as an empty line is.
            { "set-x\n   \nset-y\n", commands(args("set-x"), args("set-y")) },
            { "set-x\n \t\n", commands(args("set-x")) },
            // Empty lines and comments are skipped inside a continued command too, so a line of a
            // long command can be commented out.
            { "set-x \\\n#  --foo bar \\\n  --baz qux\n", commands(args("set-x", "--baz", "qux")) },
            { "set-x \\\n\n  --baz qux\n", commands(args("set-x", "--baz", "qux")) },
            // A line that ends in a backslash continues on the next line.
            { "set-x \\\n  --foo bar\n", commands(args("set-x", "--foo", "bar")) },
            { "set-x --foo ab\\\ncd\n", commands(args("set-x", "--foo", "abcd")) },
            // An escaped backslash at the end of a line does not continue it, as dsconfig
            // --commandFilePath writes a value that ends in a backslash on UNIX.
            { "set-x --bindPassword pa\\\\\nset-y --foo bar\n",
                commands(args("set-x", "--bindPassword", "pa\\"), args("set-y", "--foo", "bar")) },
            { "set-x --foo a\\\\\\\nb\n", commands(args("set-x", "--foo", "a\\b")) },
            // An escaped blank at the end of a line is kept.
            { "set-x --set description:value\\ \n", commands(args("set-x", "--set", "description:value ")) },
            // A command still runs when its last line continues at the end of the file.
            { "set-x --foo bar \\", commands(args("set-x", "--foo", "bar")) },
            { "set-x\n \\\n", commands(args("set-x")) },
        };
    }

    @Test(dataProvider = "batchFiles")
    public void testBatchFileCommands(String batch, List<List<String>> expected) throws Exception {
        final List<List<String>> actual = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(batch))) {
            String command;
            while ((command = DSConfig.nextBatchCommand(reader)) != null) {
                actual.add(new ArrayList<>(DSConfig.toCommandArgs(command)));
            }
        }
        Assert.assertEquals(actual, expected);
    }

    private static List<String> args(String... args) {
        return Arrays.asList(args);
    }

    @SafeVarargs
    private static List<List<String>> commands(List<String>... commands) {
        return Arrays.asList(commands);
    }
}
