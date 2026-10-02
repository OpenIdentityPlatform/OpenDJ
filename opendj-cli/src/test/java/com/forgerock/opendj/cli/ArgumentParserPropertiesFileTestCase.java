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

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.fest.assertions.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;

import org.forgerock.i18n.LocalizableMessage;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Reading argument defaults from a {@code tools.properties} file. */
@SuppressWarnings("javadoc")
public final class ArgumentParserPropertiesFileTestCase extends CliTestCase {

    /** Both ways a tool reads the file: through --propertiesFilePath and through an explicit path. */
    @DataProvider
    public Object[][] readers() {
        return new Object[][] { { true }, { false } };
    }

    @Test(dataProvider = "readers")
    public void testUtf8FileKeepsNonAsciiCharacters(boolean viaArgument) throws Exception {
        final File file = write("basedn=ou=Jörg Ж,dc=example,dc=com\n".getBytes(UTF_8));
        assertThat(baseDN(file, viaArgument)).isEqualTo("ou=Jörg Ж,dc=example,dc=com");
    }

    /** Some Windows editors start a UTF-8 file with a byte order mark. */
    @Test(dataProvider = "readers")
    public void testUtf8ByteOrderMarkIsSkipped(boolean viaArgument) throws Exception {
        final File file = write("\uFEFFbasedn=dc=example,dc=com\n".getBytes(UTF_8));
        assertThat(baseDN(file, viaArgument)).isEqualTo("dc=example,dc=com");
    }

    /** Files written for earlier releases, which read them as ISO-8859-1, keep working. */
    @Test(dataProvider = "readers")
    public void testIso88591FileIsStillAccepted(boolean viaArgument) throws Exception {
        final File file = write("basedn=ou=Jörg,dc=example,dc=com\n".getBytes(ISO_8859_1));
        assertThat(baseDN(file, viaArgument)).isEqualTo("ou=Jörg,dc=example,dc=com");
    }

    /** The file keeps the properties format: a backslash in a value must be doubled. */
    @Test(dataProvider = "readers")
    public void testDoubledBackslashIsOneBackslash(boolean viaArgument) throws Exception {
        final File file = write("basedn=cn=a\\\\,b,dc=example,dc=com\n".getBytes(UTF_8));
        assertThat(baseDN(file, viaArgument)).isEqualTo("cn=a\\,b,dc=example,dc=com");
    }

    private static String baseDN(File file, boolean viaArgument) throws Exception {
        final ArgumentParser parser =
                new ArgumentParser(ArgumentParserPropertiesFileTestCase.class.getName(),
                        LocalizableMessage.raw("test"), false);
        final StringArgument baseDN = StringArgument.builder("baseDN")
                .valuePlaceholder(LocalizableMessage.raw("{baseDN}"))
                .description(LocalizableMessage.raw("base DN"))
                .buildAndAddToParser(parser);
        if (viaArgument) {
            final StringArgument propertiesFile =
                    CommonArguments.propertiesFileArgument();
            parser.addArgument(propertiesFile);
            parser.setFilePropertiesArgument(propertiesFile);
            parser.parseArguments(new String[] { "--" + propertiesFile.getLongIdentifier(), file.getPath() });
        } else {
            parser.parseArguments(new String[0], file.getPath(), true);
        }
        return baseDN.getValue();
    }

    private static File write(byte[] content) throws Exception {
        final File file = File.createTempFile("tools", ".properties");
        file.deleteOnExit();
        Files.write(file.toPath(), content);
        return file;
    }
}
