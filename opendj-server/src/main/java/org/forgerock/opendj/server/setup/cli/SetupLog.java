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

package org.forgerock.opendj.server.setup.cli;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.CliMessages;
import com.forgerock.opendj.cli.ReturnCode;

/**
 * Creates a historical log about the setup. If file does not exist an attempt will be made to create it.
 */
final class SetupLog {

    private static File logFile;
    private static FileHandler fileHandler;
    static final String LOGNAME = "setup.log";

    private SetupLog() {
        //  Nothing to do.
    }
    /**
     * Creates a new file handler for writing log messages into {@value #LOGNAME} file.
     */
    static void initLogFileHandler() {
        final Logger logger = Logger.getLogger(SetupCli.class.getName());

        final String space = " ";

        if (logFile == null) {
            logFile = new File(SetupCli.getInstallationPath() + File.separator + LOGNAME);
        }
        try {
            fileHandler = new FileHandler(logFile.getCanonicalPath(), true);
        } catch (IOException e) {
            logger.severe(e.getMessage());
        }
        fileHandler.setFormatter(new Formatter() {
            /** {@inheritDoc} */
            @Override
            public String format(LogRecord record) {
                // Format the log ~like the errors logger.
                StringBuffer sb = new StringBuffer();
                final SimpleDateFormat dateFormat = new SimpleDateFormat("[dd/MMM/yyyy:HH:mm:ss Z]");
                sb.append(dateFormat.format(record.getMillis())).append(space);
                sb.append("category=SETUP").append(space).append("sq=").append(record.getSequenceNumber())
                        .append(space).append("severity=").append(record.getLevel().toString().toUpperCase());
                sb.append(space).append("src=").append(record.getSourceClassName()).append(space)
                        .append(record.getSourceMethodName()).append("\n");
                sb.append(space).append("msg=").append(record.getMessage()).append("\n");
                return sb.toString();
            }
        });
        logger.setLevel(Level.CONFIG);
        logger.addHandler(fileHandler);

        logger.setUseParentHandlers(false);
        // Log Config info.
        logger.info("**** Setup of OpenDJ3 started ****");
        logger.info(CliMessages.INFO_INSTALLATION_DIRECTORY.get(SetupCli.getInstallationPath()).toString());
        logger.info(CliMessages.INFO_INSTANCE_DIRECTORY.get(SetupCli.getInstancePath()).toString());
    }

    /**
     * Returns the print stream of the current logger.
     *
     * @return the print stream of the current logger.
     * @throws ClientException
     *             If the file defined by the logger is not found or invalid.
     */
    static PrintStream getPrintStream() throws ClientException {
        try {
            return new PrintStream(new FileOutputStream(logFile, true));
        } catch (FileNotFoundException e) {
            throw new ClientException(ReturnCode.ERROR_UNEXPECTED,
                    CliMessages.ERR_INVALID_LOG_FILE.get(e.getMessage()));
        }
    }

    /**
     * Gets the name of the log file.
     *
     * @return File representing the log file
     */
    public static File getLogFile() {
        return logFile;
    }
}
