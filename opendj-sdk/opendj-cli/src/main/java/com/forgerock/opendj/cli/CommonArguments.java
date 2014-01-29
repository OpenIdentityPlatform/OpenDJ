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
 *      Copyright 2014 ForgeRock AS
 */
package com.forgerock.opendj.cli;

import static com.forgerock.opendj.cli.CliConstants.*;
import static com.forgerock.opendj.cli.CliMessages.*;

/**
 * This class regroup commons arguments used by the different CLI.
 */
public final class CommonArguments {

    // Prevent instantiation.
    private CommonArguments() {
        // Nothing to do.
    }

    /**
     * Returns the "show usage" boolean argument.
     *
     * @return The "show usage" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static final BooleanArgument getShowUsage() throws ArgumentException {
        return new BooleanArgument("showUsage", OPTION_SHORT_HELP, OPTION_LONG_HELP,
                INFO_DESCRIPTION_SHOWUSAGE.get());
    }

    /**
     * Returns the "verbose" boolean argument.
     *
     * @return The "verbose" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static final BooleanArgument getVerbose() throws ArgumentException {
        final BooleanArgument verbose = new BooleanArgument("verbose", 'v', "verbose", INFO_DESCRIPTION_VERBOSE.get());
        verbose.setPropertyName("verbose");
        return verbose;
    }

    /**
     * Returns the "get properties file" string argument.
     *
     * @return The "get properties file" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static final StringArgument getPropertiesFileArgument() throws ArgumentException {
        return new StringArgument("propertiesFilePath", null, OPTION_LONG_PROP_FILE_PATH,
                false, false, true, INFO_PROP_FILE_PATH_PLACEHOLDER.get(), null, null,
                INFO_DESCRIPTION_PROP_FILE_PATH.get());
    }

    /**
     * Returns the "No properties file" boolean argument.
     *
     * @return The "No properties file" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static final BooleanArgument getNoPropertiesFileArgument() throws ArgumentException {
        return new BooleanArgument("noPropertiesFileArgument", null, OPTION_LONG_NO_PROP_FILE,
                INFO_DESCRIPTION_NO_PROP_FILE.get());
    }

    /**
     * Returns the "Continue On Error" boolean argument.
     *
     * @return The "Continue On Error" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static final BooleanArgument getContinueOnErrorArgument() throws ArgumentException {
        final BooleanArgument continueOnError = new BooleanArgument("continueOnError", 'c', "continueOnError",
                INFO_DESCRIPTION_CONTINUE_ON_ERROR.get());
        continueOnError.setPropertyName("continueOnError");
        return continueOnError;
    }

    /**
     * Returns the "version" integer argument.
     *
     * @return The "version" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static final IntegerArgument getVersionArgument() throws ArgumentException {
        final IntegerArgument version = new IntegerArgument("version", OPTION_SHORT_PROTOCOL_VERSION,
                OPTION_LONG_PROTOCOL_VERSION, false, false, true, INFO_PROTOCOL_VERSION_PLACEHOLDER.get(), 3, null,
                INFO_DESCRIPTION_VERSION.get());
        version.setPropertyName(OPTION_LONG_PROTOCOL_VERSION);
        return version;
    }

}
