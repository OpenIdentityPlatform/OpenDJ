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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2015 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.cli.Utils.filterExitCode;
import static org.forgerock.util.Utils.closeSilently;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;

import org.forgerock.i18n.LocalizableMessage;

import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;

import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldif.EntryGenerator;
import org.forgerock.opendj.ldif.LDIFEntryWriter;

/** Program that generate LDIF content based on a template. */
public final class MakeLDIF extends ConsoleApplication {
    /** The value for the constant option in LDIF generator tools. */
    public static final String OPTION_LONG_CONSTANT = "constant";

    /** The value for the path to look for LDIF resources (e.g data files). */
    public static final String OPTION_LONG_RESOURCE_PATH = "resourcePath";

    private static final int EXIT_CODE_SUCCESS = 0;
    private static final int EXIT_CODE_FAILURE = 1;

    /** The total number of entries that have been written. */
    private long numberOfEntriesWritten;

    /**
     * Main method for MakeLDIF tool.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */
    public static void main(final String[] args) {
        final int retCode = new MakeLDIF().run(args);
        System.exit(filterExitCode(retCode));
    }

    /** Run Make LDIF with provided command-line arguments. */
    int run(final String[] args) {
        final LocalizableMessage toolDescription = INFO_MAKELDIF_TOOL_DESCRIPTION.get();
        final ArgumentParser argParser = new ArgumentParser(MakeLDIF.class.getName(), toolDescription,
                false, true, 1, 1, "template-file-path");
        argParser.setVersionHandler(new SdkVersionHandler());
        argParser.setShortToolDescription(REF_SHORT_DESC_MAKELDIF.get());
        argParser.setDocToolDescriptionSupplement(SUPPLEMENT_DESCRIPTION_MAKELDIF.get());

        BooleanArgument showUsage;
        IntegerArgument randomSeed;
        StringArgument ldifFile;
        StringArgument resourcePath;
        StringArgument constants;
        try {
            resourcePath = new StringArgument("resourcepath", 'r', OPTION_LONG_RESOURCE_PATH, false, false, true,
                    INFO_PATH_PLACEHOLDER.get(), null, null, INFO_MAKELDIF_DESCRIPTION_RESOURCE_PATH.get());
            resourcePath.setDocDescriptionSupplement(SUPPLEMENT_DESCRIPTION_RESOURCE_PATH.get());
            argParser.addArgument(resourcePath);

            ldifFile = new StringArgument("ldiffile", OPTION_SHORT_OUTPUT_LDIF_FILENAME,
                    OPTION_LONG_OUTPUT_LDIF_FILENAME, false, false, true, INFO_FILE_PLACEHOLDER.get(),
                    null, null, INFO_MAKELDIF_DESCRIPTION_LDIF.get());
            argParser.addArgument(ldifFile);

            randomSeed = new IntegerArgument("randomseed", OPTION_SHORT_RANDOM_SEED, OPTION_LONG_RANDOM_SEED, false,
                    false, true, INFO_SEED_PLACEHOLDER.get(), 0, null, INFO_MAKELDIF_DESCRIPTION_SEED.get());
            argParser.addArgument(randomSeed);

            constants = new StringArgument("constant", 'c', OPTION_LONG_CONSTANT, false, true, true,
                    INFO_CONSTANT_PLACEHOLDER.get(),
                    null, null, INFO_MAKELDIF_DESCRIPTION_CONSTANT.get());
            argParser.addArgument(constants);

            showUsage = new BooleanArgument("help", OPTION_SHORT_HELP, OPTION_LONG_HELP,
                    INFO_MAKELDIF_DESCRIPTION_HELP.get());
            argParser.addArgument(showUsage);
            argParser.setUsageArgument(showUsage, getOutputStream());
        } catch (ArgumentException ae) {
            errPrintln(ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
            return EXIT_CODE_FAILURE;
        }

        // Parse the command-line arguments provided to the program.
        try {
            argParser.parseArguments(args);
        } catch (ArgumentException ae) {
            argParser.displayMessageAndUsageReference(getErrStream(), ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
            return EXIT_CODE_FAILURE;
        }

        if (argParser.usageOrVersionDisplayed()) {
            return 0;
        }
        final String templatePath = argParser.getTrailingArguments().get(0);
        return run(templatePath, resourcePath, ldifFile, randomSeed, constants);
    }

    /** Run Make LDIF with provided arguments. */
    private int run(final String templatePath, final StringArgument resourcePath,
            final StringArgument ldifFile, final IntegerArgument randomSeedArg, final StringArgument constants) {
        LDIFEntryWriter writer = null;
        try (EntryGenerator generator = createGenerator(templatePath, resourcePath, randomSeedArg, constants)) {
            if (generator == null) {
                return EXIT_CODE_FAILURE;
            }

            if (generator.hasWarnings()) {
                for (LocalizableMessage warn : generator.getWarnings()) {
                    errPrintln(warn);
                }
            }

            if (ldifFile.isPresent()) {
                try {
                    writer = new LDIFEntryWriter(new BufferedWriter(new FileWriter(new File(ldifFile.getValue()))));
                } catch (IOException e) {
                    errPrintln(ERR_MAKELDIF_UNABLE_TO_CREATE_LDIF.get(ldifFile.getValue(), e.getMessage()));
                    return EXIT_CODE_FAILURE;
                }
            } else {
                writer = new LDIFEntryWriter(getOutputStream());
            }

            if (!generateEntries(generator, writer, ldifFile)) {
                return EXIT_CODE_FAILURE;
            }

            errPrintln(INFO_MAKELDIF_PROCESSING_COMPLETE.get(numberOfEntriesWritten));

            return EXIT_CODE_SUCCESS;
        } finally {
            closeSilently(writer);
        }
    }

    static EntryGenerator createGenerator(final String templatePath, final StringArgument resourcePath,
                                            final IntegerArgument randomSeedArg, final StringArgument constants,
                                            final boolean generateBranches, final ConsoleApplication app) {
        final EntryGenerator generator = new EntryGenerator(templatePath).setGenerateBranches(generateBranches);

        if (resourcePath.isPresent()) {
            final File resourceDir = new File(resourcePath.getValue());
            if (!resourceDir.exists()) {
                app.errPrintln(ERR_LDIF_GEN_TOOL_NO_SUCH_RESOURCE_DIRECTORY.get(resourcePath.getValue()));
                generator.close();
                return null;
            }
            generator.setResourcePath(resourcePath.getValue());
        }

        if (randomSeedArg.isPresent()) {
            try {
                generator.setRandomSeed(randomSeedArg.getIntValue());
            } catch (ArgumentException ae) {
                app.errPrintln(ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
                generator.close();
                return null;
            }
        }

        if (constants.isPresent()
                && !addConstantsToGenerator(constants, generator, app)) {
            generator.close();
            return null;
        }

        // Force initialization of generator
        try {
            generator.hasNext();
        } catch (IOException e) {
            app.errPrintln(ERR_LDIF_GEN_TOOL_EXCEPTION_DURING_PARSE.get(e.getMessage()));
            generator.close();
            return null;
        }

        return generator;
    }

    /** Returns true if all constants are added to generator, false otherwise. */
    private static boolean addConstantsToGenerator(StringArgument constants, EntryGenerator generator,
                                                       final ConsoleApplication app) {
        for (final String constant : constants.getValues()) {
            final String[] chunks = constant.split("=");
            if (chunks.length != 2) {
                app.errPrintln(ERR_CONSTANT_ARG_CANNOT_DECODE.get(constant));
                return false;
            }
            generator.setConstant(chunks[0], chunks[1]);
        }
        return true;
    }

    private EntryGenerator createGenerator(final String templatePath, final StringArgument resourcePath,
            final IntegerArgument randomSeedArg, final StringArgument constants) {
        return createGenerator(templatePath, resourcePath, randomSeedArg, constants, true, this);
    }

    /** Returns true if generation is successful, false otherwise. */
    private boolean generateEntries(final EntryGenerator generator, final LDIFEntryWriter writer,
            final StringArgument ldifFile) {
        try {
            while (generator.hasNext()) {
                final Entry entry = generator.readEntry();
                try {
                    writer.writeEntry(entry);
                } catch (IOException e) {
                    errPrintln(ERR_MAKELDIF_ERROR_WRITING_LDIF.get(ldifFile.getValue(), e.getMessage()));
                    return false;
                }
                if ((++numberOfEntriesWritten % 1000) == 0) {
                    errPrintln(INFO_MAKELDIF_PROCESSED_N_ENTRIES.get(numberOfEntriesWritten));
                }
            }
        } catch (Exception e) {
            errPrintln(ERR_MAKELDIF_EXCEPTION_DURING_PROCESSING.get(e.getMessage()));
            return false;
        }
        return true;
    }

    private MakeLDIF() {
        // nothing to do
    }

    /** To allow tests. */
    MakeLDIF(PrintStream out, PrintStream err) {
        super(out, err);
    }
}
