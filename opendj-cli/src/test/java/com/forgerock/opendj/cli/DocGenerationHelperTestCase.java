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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.forgerock.i18n.LocalizableMessage;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests that the generated AsciiDoc reference writes the value placeholders of the options
 * as text, not as AsciiDoc attribute references.
 */
@SuppressWarnings("javadoc")
public final class DocGenerationHelperTestCase extends CliTestCase {

    private static final String GENDOC = "org.forgerock.opendj.gendoc";

    /** An AsciiDoc attribute reference that no backslash escapes. */
    private static final Pattern ATTRIBUTE_REFERENCE = Pattern.compile("(?<!\\\\)\\{\\w[\\w-]*\\}");

    private String scriptName;
    private String gendoc;

    @BeforeClass
    public void enableDocGeneration() {
        scriptName = System.setProperty(ArgumentParser.PROPERTY_SCRIPT_NAME, "test-tool");
        gendoc = System.setProperty(GENDOC, "true");
    }

    @AfterClass(alwaysRun = true)
    public void restoreProperties() {
        restore(ArgumentParser.PROPERTY_SCRIPT_NAME, scriptName);
        restore(GENDOC, gendoc);
    }

    private static void restore(final String name, final String value) {
        if (value != null) {
            System.setProperty(name, value);
        } else {
            System.clearProperty(name);
        }
    }

    @Test
    public void toolReferenceEscapesPlaceholders() throws Exception {
        final ArgumentParser parser =
                new ArgumentParser(getClass().getName(), LocalizableMessage.raw("Reads the {path} you give."), false);
        nameArgument().buildAndAddToParser(parser);

        final String doc = parser.getUsage();

        assertThat(doc).contains("Reads the \\{path} you give.");
        assertThat(doc).contains("`--backend-name \\{name}`::");
        assertThat(doc).contains("Depends on the \\{name} you provide, as {PROP:VALUE} or {name=value} do not.");
        assertNoAttributeReference(doc);
    }

    @Test
    public void subcommandReferenceEscapesPlaceholdersOnce() throws Exception {
        final SubCommandArgumentParser parser =
                new SubCommandArgumentParser(getClass().getName(), LocalizableMessage.raw("A tool."), false);
        final SubCommand subCommand =
                new SubCommand(parser, "get-backend-prop", LocalizableMessage.raw("Shows the {name} backend."));
        nameArgument().buildAndAddToSubCommand(subCommand);

        final String doc = parser.getUsage();

        assertThat(doc).contains("Shows the \\{name} backend.");
        assertThat(doc).contains("`--backend-name \\{name}`::");
        assertThat(doc).contains("Depends on the \\{name} you provide, as {PROP:VALUE} or {name=value} do not.");
        assertThat(doc).doesNotContain("\\\\{");
        assertNoAttributeReference(doc);
    }

    private static StringArgument.Builder nameArgument() {
        return StringArgument.builder("backend-name")
                .description(LocalizableMessage.raw(
                        "Depends on the {name} you provide, as {PROP:VALUE} or {name=value} do not"))
                .valuePlaceholder(LocalizableMessage.raw("{name}"));
    }

    private static void assertNoAttributeReference(final String doc) {
        final Matcher matcher = ATTRIBUTE_REFERENCE.matcher(doc);
        if (matcher.find()) {
            throw new AssertionError("Unescaped attribute reference " + matcher.group() + " in:\n" + doc);
        }
    }
}
