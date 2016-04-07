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
 * Copyright 2006-2008 Sun Microsystems, Inc.
 * Portions copyright 2014-2016 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

import static com.forgerock.opendj.cli.CliMessages.*;
import static com.forgerock.opendj.util.StaticUtils.getExceptionMessage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.forgerock.i18n.LocalizableMessageBuilder;

/**
 * This class defines an argument whose value will be read from a file rather
 * than actually specified on the command-line. When a value is specified on the
 * command line, it will be treated as the path to the file containing the
 * actual value rather than the value itself. <BR>
 * <BR>
 * Note that if no filename is provided on the command line but a default
 * value is specified programmatically or if the default value is read from a
 * specified property, then that default value will be taken as the actual value
 * rather than a filename. <BR>
 * <BR>
 * Also note that this argument type assumes that the entire value for the
 * argument is on a single line in the specified file. If the file contains
 * multiple lines, then only the first line will be read.
 */
public final class FileBasedArgument extends Argument {

    /**
     * Returns a builder which can be used for incrementally constructing a new
     * {@link FileBasedArgument}.
     *
     * @param longIdentifier
     *         The generic long identifier that will be used to refer to this argument.
     * @return A builder to continue building the {@link FileBasedArgument}.
     */
    public static Builder builder(final String longIdentifier) {
        return new Builder(longIdentifier);
    }

    /** A fluent API for incrementally constructing {@link FileBasedArgument}. */
    public static final class Builder extends ArgumentBuilder<Builder, String, FileBasedArgument> {
        private Builder(final String longIdentifier) {
            super(longIdentifier);
        }

        @Override
        Builder getThis() {
            return this;
        }

        @Override
        public FileBasedArgument buildArgument() throws ArgumentException {
            return new FileBasedArgument(this);
        }
    }

    /** The mapping between filenames specified and the first lines read from those files. */
    private final Map<String, String> namesToValues = new LinkedHashMap<>();

    private FileBasedArgument(final Builder builder) throws ArgumentException {
        super(builder);
    }

    /**
     * Adds a value to the set of values for this argument. This should only be
     * called if the value is allowed by the <CODE>valueIsAcceptable</CODE>
     * method. Note that in this case, correct behavior depends on a previous
     * successful call to <CODE>valueIsAcceptable</CODE> so that the value read
     * from the file may be stored in the name-to-value hash and used in place
     * of the filename here.
     *
     * @param valueString
     *            The string representation of the value to add to this
     *            argument.
     */
    @Override
    public void addValue(final String valueString) {
        final String actualValue = namesToValues.get(valueString);
        if (actualValue != null) {
            super.addValue(actualValue);
        }
    }

    /**
     * Retrieves a map between the filenames specified on the command line and
     * the first lines read from those files.
     *
     * @return A map between the filenames specified on the command line and the
     *         first lines read from those files.
     */
    public Map<String, String> getNameToValueMap() {
        return namesToValues;
    }

    /**
     * Indicates whether the provided value is acceptable for use in this
     * argument.
     *
     * @param valueString
     *            The value for which to make the determination.
     * @param invalidReason
     *            A buffer into which the invalid reason may be written if the
     *            value is not acceptable.
     * @return <CODE>true</CODE> if the value is acceptable, or
     *         <CODE>false</CODE> if it is not.
     */
    @Override
    public boolean valueIsAcceptable(final String valueString,
            final LocalizableMessageBuilder invalidReason) {
        // First, make sure that the specified file exists.
        File valueFile;
        try {
            valueFile = new File(valueString);
            if (!valueFile.exists()) {
                invalidReason.append(ERR_FILEARG_NO_SUCH_FILE.get(valueString, longIdentifier));
                return false;
            }
        } catch (final Exception e) {
            invalidReason.append(ERR_FILEARG_CANNOT_VERIFY_FILE_EXISTENCE.get(
                    valueString, longIdentifier, getExceptionMessage(e)));
            return false;
        }

        // Open the file, read the first line and close the file.
        String line;
        try (BufferedReader reader = new BufferedReader(new FileReader(valueFile))) {
            line = reader.readLine();
        } catch (final FileNotFoundException e) {
            invalidReason.append(ERR_FILEARG_CANNOT_OPEN_FILE.get(valueString, longIdentifier, getExceptionMessage(e)));
            return false;
        } catch (final IOException e) {
            invalidReason.append(ERR_FILEARG_CANNOT_READ_FILE.get(valueString, longIdentifier, getExceptionMessage(e)));
            return false;
        }

        // If the line read is null, then that means the file was empty.
        if (line == null) {
            invalidReason.append(ERR_FILEARG_EMPTY_FILE.get(valueString, longIdentifier));
            return false;
        }

        // Store the value in the hash so it will be available for addValue.
        // We won't do any validation on the value itself, so anything that we
        // read will be considered acceptable.
        namesToValues.put(valueString, line);
        return true;
    }
}
