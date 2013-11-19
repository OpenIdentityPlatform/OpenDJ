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
 *      Portions Copyright 2013 ForgeRock AS
 */
package com.forgerock.opendj.ldap.tools;

import static com.forgerock.opendj.ldap.tools.ToolConstants.*;
import static com.forgerock.opendj.ldap.tools.ToolsMessages.*;
import static com.forgerock.opendj.ldap.tools.Utils.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldif.EntryGenerator;
import org.forgerock.opendj.ldif.LDIFEntryWriter;

/**
 * Program that generate LDIF content based on a template.
 */
public final class MakeLDIF extends ConsoleApplication {

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

        BooleanArgument showUsage;
        IntegerArgument randomSeed;
        StringArgument ldifFile;
        StringArgument resourcePath;
        StringArgument constants;
        try {
            resourcePath = new StringArgument("resourcepath", 'r', "resourcePath", false, false, true,
                    INFO_PATH_PLACEHOLDER.get(), null, null, INFO_MAKELDIF_DESCRIPTION_RESOURCE_PATH.get());
            resourcePath.setHidden(true);
            argParser.addArgument(resourcePath);

            ldifFile = new StringArgument("ldiffile", OPTION_SHORT_OUTPUT_LDIF_FILENAME,
                    OPTION_LONG_OUTPUT_LDIF_FILENAME, false, false, true, INFO_FILE_PLACEHOLDER.get(),
                    null, null, INFO_MAKELDIF_DESCRIPTION_LDIF.get());
            argParser.addArgument(ldifFile);

            randomSeed = new IntegerArgument("randomseed", OPTION_SHORT_RANDOM_SEED, OPTION_LONG_RANDOM_SEED, false,
                    false, true, INFO_SEED_PLACEHOLDER.get(), 0, null, INFO_MAKELDIF_DESCRIPTION_SEED.get());
            argParser.addArgument(randomSeed);

            constants = new StringArgument("constant", 'c', "constant", false, true, true, INFO_FILE_PLACEHOLDER.get(),
                    null, null, INFO_MAKELDIF_DESCRIPTION_LDIF.get());
            argParser.addArgument(constants);

            showUsage = new BooleanArgument("help", OPTION_SHORT_HELP, OPTION_LONG_HELP,
                    INFO_MAKELDIF_DESCRIPTION_HELP.get());
            argParser.addArgument(showUsage);
            argParser.setUsageArgument(showUsage, getOutputStream());
        } catch (ArgumentException ae) {
            println(ERR_CANNOT_INITIALIZE_ARGS.get(ae.getMessage()));
            return EXIT_CODE_FAILURE;
        }

        // Parse the command-line arguments provided to the program.
        try {
            argParser.parseArguments(args);
        } catch (ArgumentException ae) {
            println(ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
            println(argParser.getUsageMessage());
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
        EntryGenerator generator = null;
        LDIFEntryWriter writer = null;
        try {
            generator = createGenerator(templatePath, resourcePath, randomSeedArg, constants);
            if (generator == null) {
                return EXIT_CODE_FAILURE;
            }

            if (generator.hasWarning()) {
                for (LocalizableMessage warn : generator.getWarnings()) {
                    println(warn);
                }
            }

            if (ldifFile.isPresent()) {
                try {
                    writer = new LDIFEntryWriter(new BufferedWriter(new FileWriter(new File(ldifFile.getValue()))));
                } catch (IOException e) {
                    println(ERR_MAKELDIF_UNABLE_TO_CREATE_LDIF.get(ldifFile.getValue(), e.getMessage()));
                    return EXIT_CODE_FAILURE;
                }
            } else {
                writer = new LDIFEntryWriter(getOutputStream());
            }

            if (!generateEntries(generator, writer, ldifFile)) {
                return EXIT_CODE_FAILURE;
            }

            println(INFO_MAKELDIF_PROCESSING_COMPLETE.get(numberOfEntriesWritten));

        } finally {
            closeIfNotNull(generator, writer);
        }

        return EXIT_CODE_SUCCESS;
    }

    /**
     * Returns the initialized generator, or null if a problem occurs.
     */
    private EntryGenerator createGenerator(final String templatePath, final StringArgument resourcePath,
            final IntegerArgument randomSeedArg, final StringArgument constants) {
        final EntryGenerator generator = new EntryGenerator(templatePath);

        if (resourcePath.isPresent()) {
            final File resourceDir = new File(resourcePath.getValue());
            if (!resourceDir.exists()) {
                println(ERR_MAKELDIF_NO_SUCH_RESOURCE_DIRECTORY.get(resourcePath.getValue()));
                generator.close();
                return null;
            }
            generator.setResourcePath(resourcePath.getValue());
        }

        if (randomSeedArg.isPresent()) {
            try {
                generator.setRandomSeed(randomSeedArg.getIntValue());
            } catch (ArgumentException ae) {
                println(ERR_ERROR_PARSING_ARGS.get(ae.getMessage()));
                generator.close();
                return null;
            }
        }

        if (constants.isPresent()) {
            if (!addConstantsToGenerator(constants, generator)) {
                generator.close();
                return null;
            }
        }

        // Force initialization of generator
        try {
            generator.hasNext();
        } catch (IOException e) {
            println(ERR_MAKELDIF_EXCEPTION_DURING_PARSE.get(e.getMessage()));
            generator.close();
            return null;
        }

        return generator;
    }

    /**
     * Returns true if all constants are added to generator, false otherwise.
     */
    private boolean addConstantsToGenerator(StringArgument constants, EntryGenerator generator) {
        for (final String constant : constants.getValues()) {
            final String[] chunks = constant.split("=");
            if (chunks.length != 2) {
                println(ERR_CONSTANT_ARG_CANNOT_DECODE.get(constant));
                return false;
            }
            generator.setConstant(chunks[0], chunks[1]);
        }
        return true;
    }

    /**
     * Returns true if generation is successfull, false otherwise.
     */
    private boolean generateEntries(final EntryGenerator generator, final LDIFEntryWriter writer,
            final StringArgument ldifFile) {
        try {
            while (generator.hasNext()) {
                final Entry entry = generator.readEntry();
                try {
                    writer.writeEntry(entry);
                } catch (IOException e) {
                    println(ERR_MAKELDIF_ERROR_WRITING_LDIF.get(ldifFile.getValue(), e.getMessage()));
                    return false;
                }
                if ((++numberOfEntriesWritten % 1000) == 0) {
                    println(INFO_MAKELDIF_PROCESSED_N_ENTRIES.get(numberOfEntriesWritten));
                }
            }
        } catch (Exception e) {
            println(ERR_MAKELDIF_EXCEPTION_DURING_PROCESSING.get(e.getMessage()));
            return false;
        }
        return true;
    }

    private MakeLDIF() {
        // nothing to do
    }

    // To allow tests
    MakeLDIF(PrintStream out, PrintStream err) {
        super(out, err);
    }

}
