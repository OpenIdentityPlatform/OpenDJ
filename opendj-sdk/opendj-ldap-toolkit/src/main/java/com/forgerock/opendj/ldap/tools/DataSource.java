/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2009-2010 Sun Microsystems, Inc.
 *      Portions copyright 2014-2015 ForgeRock AS
 */

package com.forgerock.opendj.ldap.tools;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.util.StaticUtils;

import org.forgerock.util.Reject;

/**
 * A source of data for performance tools.
 */
final class DataSource {
    private static interface IDataSource {
        IDataSource duplicate();

        Object getData();
    }

    private static class IncrementLineFileDataSource implements IDataSource {
        private final List<String> lines;
        private int next;

        public IncrementLineFileDataSource(final String file) throws IOException {
            lines = new ArrayList<>();
            final BufferedReader in = new BufferedReader(new FileReader(file));
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    lines.add(line);
                }
            } finally {
                in.close();
            }
        }

        private IncrementLineFileDataSource(final List<String> lines) {
            this.lines = lines;
        }

        public IDataSource duplicate() {
            return new IncrementLineFileDataSource(lines);
        }

        public Object getData() {
            if (next == lines.size()) {
                next = 0;
            }

            return lines.get(next++);
        }

        public static LocalizableMessage getUsage() {
            return LocalizableMessage
                    .raw("\"inc({filename})\" Consecutive, incremental line from file");
        }
    }

    private static class IncrementNumberDataSource implements IDataSource {
        private final int low;
        private int next;
        private final int high;

        public IncrementNumberDataSource(final int low, final int high) {
            this.low = this.next = low;
            this.high = high;
        }

        public IDataSource duplicate() {
            return new IncrementNumberDataSource(low, high);
        }

        public Object getData() {
            if (next == high) {
                next = low;
                return high;
            }

            return next++;
        }

        public static LocalizableMessage getUsage() {
            return LocalizableMessage.raw("\"inc({min},{max})\" Consecutive, incremental number");
        }
    }

    private static class RandomLineFileDataSource implements IDataSource {
        private final List<String> lines;
        private final Random random;

        public RandomLineFileDataSource(final long seed, final String file) throws IOException {
            lines = new ArrayList<>();
            random = new Random(seed);
            final BufferedReader in = new BufferedReader(new FileReader(file));
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    lines.add(line);
                }
            } finally {
                in.close();
            }
        }

        public IDataSource duplicate() {
            return this;
        }

        public Object getData() {
            return lines.get(random.nextInt(lines.size()));
        }

        public static LocalizableMessage getUsage() {
            return LocalizableMessage.raw("\"rand({filename})\" Random line from file");
        }
    }

    private static class RandomNumberDataSource implements IDataSource {
        private final Random random;
        private final int offset;
        private final int range;

        public RandomNumberDataSource(final long seed, final int low, final int high) {
            random = new Random(seed);
            offset = low;
            range = high - low;
        }

        public IDataSource duplicate() {
            // There is no state info so threads can just share one instance.
            return this;
        }

        public Object getData() {
            return random.nextInt(range) + offset;
        }

        public static LocalizableMessage getUsage() {
            return LocalizableMessage.raw("\"rand({min},{max})\" Random number");
        }
    }

    private static final class RandomStringDataSource implements IDataSource {
        private final Random random;
        private final int length;
        private final Character[] charSet;

        private RandomStringDataSource(final int seed, final int length, final String charSet) {
            this.length = length;
            final Set<Character> chars = new HashSet<>();
            for (int i = 0; i < charSet.length(); i++) {
                final char c = charSet.charAt(i);
                if (c == '[') {
                    i += 1;
                    final char start = charSet.charAt(i);
                    i += 2;
                    final char end = charSet.charAt(i);
                    i += 1;
                    for (int j = start; j <= end; j++) {
                        chars.add((char) j);
                    }
                } else {
                    chars.add(c);
                }
            }
            this.charSet = chars.toArray(new Character[chars.size()]);
            this.random = new Random(seed);
        }

        public IDataSource duplicate() {
            return this;
        }

        public Object getData() {
            final char[] str = new char[length];
            for (int i = 0; i < length; i++) {
                str[i] = charSet[random.nextInt(charSet.length)];
            }
            return new String(str);
        }

        public static LocalizableMessage getUsage() {
            return LocalizableMessage
                    .raw("\"randstr({length},_charSet_)\" Random string of specified "
                            + "length and optionally from characters in "
                            + "the charSet string. A range of character "
                            + "can be specified with [start-end] charSet notation. "
                            + "If no charSet is specified, the default charSet of "
                            + "[A-Z][a-z][0-9] will be used");
        }
    }

    private static final class StaticDataSource implements IDataSource {
        private final Object data;

        private StaticDataSource(final Object data) {
            this.data = data;
        }

        public IDataSource duplicate() {
            // There is no state info so threads can just share one instance.
            return this;
        }

        public Object getData() {
            return data;
        }
    }

    /**
     * Returns Generated data from the specified data sources. Generated data
     * will be placed in the specified data array. If the data array is null or
     * smaller than the number of data sources, one will be allocated.
     *
     * @param dataSources
     *            Data sources that will generate arguments referenced by the
     *            format specifiers in the format string.
     * @param data
     *            The array where generated data will be placed to format the
     *            string.
     * @return A formatted string
     */
    public static Object[] generateData(final DataSource[] dataSources, Object[] data) {
        if (data == null || data.length < dataSources.length) {
            data = new Object[dataSources.length];
        }
        for (int i = 0; i < dataSources.length; i++) {
            data[i] = dataSources[i].getData();
        }
        return data;
    }

    /**
     * Parses a list of source definitions into an array of data source objects.
     * A data source is defined as follows: - rand({min},{max}) generates a
     * random integer between the min and max. - rand({filename}) retrieves a
     * random line from a file. - inc({min},{max}) returns incremental integer
     * between the min and max. - inc({filename}) retrieves lines in order from
     * a file. - {number} always return the integer as given. - {string} always
     * return the string as given.
     *
     * @param sources
     *            The list of source definitions to parse.
     * @return The array of parsed data sources.
     * @throws ArgumentException
     *             If an exception occurs while parsing.
     */
    public static DataSource[] parse(final List<String> sources) throws ArgumentException {
        Reject.ifNull(sources);
        final DataSource[] dataSources = new DataSource[sources.size()];
        for (int i = 0; i < sources.size(); i++) {
            final String dataSourceDef = sources.get(i);
            if (dataSourceDef.startsWith("rand(") && dataSourceDef.endsWith(")")) {
                final int lparenPos = dataSourceDef.indexOf("(");
                final int commaPos = dataSourceDef.indexOf(",");
                final int rparenPos = dataSourceDef.indexOf(")");

                if (commaPos < 0) {
                    try {
                        // This is a file name
                        dataSources[i] =
                                new DataSource(new RandomLineFileDataSource(0, dataSourceDef
                                        .substring(lparenPos + 1, rparenPos)));
                    } catch (IOException ioe) {
                        throw new ArgumentException(LocalizableMessage.raw(
                                "Error opening file %s: %s", dataSourceDef.substring(lparenPos + 1,
                                        rparenPos), ioe.getMessage()), ioe);
                    } catch (Exception e) {
                        throw new ArgumentException(LocalizableMessage.raw(
                                "Error parsing value generator: %s", e.getMessage()), e);
                    }
                } else {
                    try {
                        // This range of integers
                        final int low =
                                Integer.parseInt(dataSourceDef.substring(lparenPos + 1, commaPos));
                        final int high =
                                Integer.parseInt(dataSourceDef.substring(commaPos + 1, rparenPos));
                        dataSources[i] =
                                new DataSource(new RandomNumberDataSource(Thread.currentThread()
                                        .getId(), low, high));
                    } catch (Exception e) {
                        throw new ArgumentException(LocalizableMessage.raw(
                                "Error parsing value generator: %s", e.getMessage()), e);
                    }
                }

            } else if (dataSourceDef.startsWith("randstr(") && dataSourceDef.endsWith(")")) {
                final int lparenPos = dataSourceDef.indexOf("(");
                final int commaPos = dataSourceDef.indexOf(",");
                final int rparenPos = dataSourceDef.indexOf(")");
                int length;
                String charSet;
                try {
                    if (commaPos < 0) {
                        length =
                                Integer.parseInt(dataSourceDef.substring(lparenPos + 1, rparenPos));
                        charSet = "[A-Z][a-z][0-9]";
                    } else {
                        // length and charSet
                        length = Integer.parseInt(dataSourceDef.substring(lparenPos + 1, commaPos));
                        charSet = dataSourceDef.substring(commaPos + 1, rparenPos);
                    }
                    dataSources[i] = new DataSource(new RandomStringDataSource(0, length, charSet));
                } catch (Exception e) {
                    throw new ArgumentException(LocalizableMessage.raw(
                            "Error parsing value generator: %s", e.getMessage()), e);
                }
            } else if (dataSourceDef.startsWith("inc(") && dataSourceDef.endsWith(")")) {
                final int lparenPos = dataSourceDef.indexOf("(");
                final int commaPos = dataSourceDef.indexOf(",");
                final int rparenPos = dataSourceDef.indexOf(")");
                if (commaPos < 0) {
                    try {
                        // This is a file name
                        dataSources[i] =
                                new DataSource(new IncrementLineFileDataSource(dataSourceDef
                                        .substring(lparenPos + 1, rparenPos)));
                    } catch (IOException ioe) {
                        throw new ArgumentException(LocalizableMessage.raw(
                                "Error opening file %s: %s", dataSourceDef.substring(lparenPos + 1,
                                        rparenPos), ioe.getMessage()), ioe);
                    } catch (Exception e) {
                        throw new ArgumentException(LocalizableMessage.raw(
                                "Error parsing value generator: %s", e.getMessage()), e);
                    }
                } else {
                    try {
                        final int low =
                                Integer.parseInt(dataSourceDef.substring(lparenPos + 1, commaPos));
                        final int high =
                                Integer.parseInt(dataSourceDef.substring(commaPos + 1, rparenPos));
                        dataSources[i] = new DataSource(new IncrementNumberDataSource(low, high));
                    } catch (Exception e) {
                        throw new ArgumentException(LocalizableMessage.raw(
                                "Error parsing value generator: %s", e.getMessage()), e);
                    }
                }
            } else {
                try {
                    dataSources[i] =
                            new DataSource(new StaticDataSource(Integer.parseInt(dataSourceDef)));
                } catch (final NumberFormatException nfe) {
                    dataSources[i] = new DataSource(new StaticDataSource(dataSourceDef));
                }
            }
        }

        return dataSources;
    }

    public static LocalizableMessage getUsage() {
        StringBuilder builder = new StringBuilder();
        builder.append(IncrementLineFileDataSource.getUsage());
        builder.append(StaticUtils.EOL);
        builder.append(IncrementNumberDataSource.getUsage());
        builder.append(StaticUtils.EOL);
        builder.append(RandomLineFileDataSource.getUsage());
        builder.append(StaticUtils.EOL);
        builder.append(RandomNumberDataSource.getUsage());
        builder.append(StaticUtils.EOL);
        builder.append(RandomStringDataSource.getUsage());
        return LocalizableMessage.raw(builder.toString());
    }

    private final IDataSource impl;

    private DataSource(final IDataSource impl) {
        this.impl = impl;
    }

    public DataSource duplicate() {
        final IDataSource dup = impl.duplicate();
        if (dup == impl) {
            return this;
        } else {
            return new DataSource(dup);
        }
    }

    public Object getData() {
        return impl.getData();
    }
}
