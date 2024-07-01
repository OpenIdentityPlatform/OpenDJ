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
 *  Copyright 2016 ForgeRock AS.
 */

package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.cli.CliMessages.ERR_ARGPARSER_NO_VALUE_FOR_ARGUMENT_WITH_LONG_ID;
import static com.forgerock.opendj.cli.CliMessages.ERR_ARGPARSER_NO_VALUE_FOR_ARGUMENT_WITH_SHORT_ID;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.forgerock.i18n.LocalizableMessage;

/** Useful set of methods to manage tools unit tests. **/
@SuppressWarnings("javadoc")
class ToolsTestUtils {
    static Args buildArgs() {
        return new Args();
    }

    /** Helper class to represent arguments for command-line programs. */
    static class Args {
        private final List<String> args = new ArrayList<>();

        Args add(String arg) {
            args.add(arg);
            return this;
        }

        Args add(String arg, Object value) {
            args.add(arg);
            args.add(value.toString());
            return this;
        }

        Args addAll(String... args) {
            this.args.addAll(Arrays.asList(args));
            return this;
        }

        Args addAll(final Args args) {
            this.args.addAll(Arrays.asList(args.toArray()));
            return this;
        }

        String[] toArray() {
            return args.toArray(new String[args.size()]);
        }

        @Override
        public String toString() {
            return args.toString();
        }
    }


    static List<String> args(final String... arguments) {
        return Arrays.asList(arguments);
    }

    static void addValueNeededShortArgs(
            final List<List<String>> argsList, final List<LocalizableMessage> reasonList, final String... args) {
        testValueNeededArg(argsList, reasonList, "-", args);
    }

    static void addValueNeededLongArgs(
            final List<List<String>> argsList, final List<LocalizableMessage> reasonList, final String... args) {
        testValueNeededArg(argsList, reasonList, "--", args);
    }

    private static void testValueNeededArg(final List<List<String>> argsList,
                                           final List<LocalizableMessage> reasonList,
                                           final String prefix,
                                           final String[] argIDs) {
        for (final String argID : argIDs) {
            argsList.add(args(prefix + argID));
            reasonList.add(prefix.equals("--")
                                           ? ERR_ARGPARSER_NO_VALUE_FOR_ARGUMENT_WITH_LONG_ID.get(argID)
                                           : ERR_ARGPARSER_NO_VALUE_FOR_ARGUMENT_WITH_SHORT_ID.get(argID));
        }
    }

    static Object[][] toDataProviderArray(final List<List<String>> argLists,
                                          final List<LocalizableMessage> reasonList) {
        final Object[][] array = new Object[argLists.size()][2];
        for (int i = 0; i < argLists.size(); i++) {
            final List<String> args = argLists.get(i);
            array[i][0] = args.toArray(new String[args.size()]);
            array[i][1] = reasonList.get(i).toString();
        }
        return array;
    }

    static String createTempFile(final String... lines) throws Exception {
        final File f = File.createTempFile("ToolsTestUtils", ".txt");
        f.deleteOnExit();

        if (lines.length > 0) {
            final StringBuilder builder = new StringBuilder();
            for (final String line : lines) {
                builder.append(line).append(System.lineSeparator());
            }

            try (final FileOutputStream fos = new FileOutputStream(f.getPath())) {
                fos.write(builder.toString().getBytes());
            }
        }
        return f.getAbsolutePath();
    }

    /** Calculates the checksum of a file. */
    static long calcChecksum(final String filePath) throws Exception {
        long checksum = 0L;
        try (final BufferedReader reader = new BufferedReader(new FileReader(new File(filePath)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                checksum += line.hashCode();
            }
        }
        return checksum;
    }
}
