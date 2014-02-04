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
 *      Copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.server.setup.cli;

import static com.forgerock.opendj.cli.Utils.filterExitCode;
import static com.forgerock.opendj.cli.Utils.LINE_SEPARATOR;
import static com.forgerock.opendj.cli.CliMessages.*;

import java.io.PrintStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ResultCode;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.ArgumentParser;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.FileBasedArgument;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.StringArgument;

/**
 * This class implements the new CLI for OpenDJ3 setup.
 */
public final class SetupCli extends ConsoleApplication {

    ArgumentParser argParser;

    BooleanArgument testOnly;
    BooleanArgument cli;
    BooleanArgument addBaseEntry;
    BooleanArgument skipPortCheck;
    BooleanArgument enableWindowsService;
    BooleanArgument doNotStart;
    BooleanArgument enableStartTLS;
    BooleanArgument generateSelfSignedCertificate;
    StringArgument hostName;
    BooleanArgument usePkcs11;
    FileBasedArgument directoryManagerPwdFile;
    FileBasedArgument keyStorePasswordFile;
    IntegerArgument ldapPort;
    IntegerArgument adminConnectorPort;
    IntegerArgument ldapsPort;
    IntegerArgument jmxPort;
    IntegerArgument sampleData;
    StringArgument baseDN;
    StringArgument importLDIF;
    StringArgument rejectedImportFile;
    StringArgument skippedImportFile;
    StringArgument directoryManagerDN;
    StringArgument directoryManagerPwdString;
    StringArgument useJavaKeyStore;
    StringArgument useJCEKS;
    StringArgument usePkcs12;
    StringArgument keyStorePassword;
    StringArgument certNickname;
    StringArgument progName;
    IntegerArgument connectTimeout = null;
    BooleanArgument acceptLicense;

    // Register the global arguments.
    BooleanArgument noPrompt;
    BooleanArgument quietMode;
    BooleanArgument verbose;
    StringArgument propertiesFile;
    BooleanArgument noPropertiesFile;
    BooleanArgument showUsage;

    private SetupCli() {
        // Nothing to do.
    }

    // To allow tests
    SetupCli(PrintStream out, PrintStream err) {
        super(out, err);
    }

    /**
     * The main method for setup tool.
     *
     * @param args
     *            The command-line arguments provided to this program.
     */
    public static void main(final String[] args) {
        final int retCode = new SetupCli().run(args);
        System.exit(filterExitCode(retCode));
    }

    /** Create the command-line argument parser for use with this program. */
    int run(final String[] args) {
        try {
            argParser = new ArgumentParser("Setup", INFO_SETUP_TITLE.get(), true, false, 0, 0, INFO_SETUP_DESCRIPTION
                    .get().toString());

            initializeArguments();

        } catch (ArgumentException e) {
            final LocalizableMessage message = ERR_CANNOT_INITIALIZE_ARGS.get(e.getMessage());
            println(message);
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        // Parse the command-line arguments provided to this program.
        try {
            argParser.parseArguments(args);

            if (argParser.usageOrVersionDisplayed()) {
                // If we should just display usage or version information, then print it and exit.
                return ResultCode.SUCCESS.intValue();
            }
        } catch (final ArgumentException e) {
            final LocalizableMessage message = ERR_ERROR_PARSING_ARGS.get(e.getMessage());
            println(message);
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        // Verifying provided informations.
        try {
            final LinkedHashSet<LocalizableMessage> errorMessages = new LinkedHashSet<LocalizableMessage>();
            checkServerPassword(errorMessages);
            checkProvidedPorts(errorMessages);
            checkImportDataArguments(errorMessages);
            checkSecurityArguments(errorMessages);
            if (errorMessages.size() > 0) {
                throw new ArgumentException(ERR_CANNOT_INITIALIZE_ARGS.get(getMessageFromCollection(errorMessages,
                        LINE_SEPARATOR)));
            }
        } catch (final ArgumentException e) {
            println(e.getMessageObject());
            return ResultCode.CLIENT_SIDE_PARAM_ERROR.intValue();
        }

        // Starts setup process.

        return ResultCode.SUCCESS.intValue();
    }

    private void initializeArguments() throws ArgumentException {

        // Options.
        acceptLicense = CommonArguments.getAcceptLicense();

        testOnly = CommonArguments.getTestOnly();
        cli = CommonArguments.getCLI();
        baseDN = CommonArguments.getBaseDN();
        addBaseEntry = CommonArguments.getAddBaseEntry();
        importLDIF = CommonArguments.getImportLDIF();
        rejectedImportFile = CommonArguments.getRejectedImportLdif();
        skippedImportFile = CommonArguments.getSkippedImportFile();
        sampleData = CommonArguments.getSampleData();
        ldapPort = CommonArguments.getLDAPPort(389); // TODO
        adminConnectorPort = CommonArguments.getAdminLDAPPort(1444); // TODO
        jmxPort = CommonArguments.getJMXPort(1444); // TODO
        skipPortCheck = CommonArguments.getSkipPortCheck();
        directoryManagerDN = CommonArguments.getRootDN();
        directoryManagerPwdString = CommonArguments.getRootDNPwd();
        directoryManagerPwdFile = CommonArguments.getRootDNPwdFile();
        enableWindowsService = CommonArguments.getEnableWindowsService();
        doNotStart = CommonArguments.getDoNotStart();
        enableStartTLS = CommonArguments.getEnableTLS();
        ldapsPort = CommonArguments.getLDAPSPort(1636); // TODO
        generateSelfSignedCertificate = CommonArguments.getGenerateSelfSigned();
        hostName = CommonArguments.getHostName("TODO"); // TODO
        usePkcs11 = CommonArguments.getUsePKCS11Keystore();
        useJavaKeyStore = CommonArguments.getUseJavaKeyStore();
        useJCEKS = CommonArguments.getUseJCEKS();
        usePkcs12 = CommonArguments.getUsePKCS12KeyStore();
        keyStorePassword = CommonArguments.getKeyStorePassword();
        keyStorePasswordFile = CommonArguments.getKeyStorePasswordFile();
        certNickname = CommonArguments.getCertNickName();

        // Utility Input Output Options.
        noPrompt = CommonArguments.getNoPrompt();
        quietMode = CommonArguments.getQuiet();
        verbose = CommonArguments.getVerbose();
        propertiesFile = CommonArguments.getPropertiesFileArgument();
        noPropertiesFile = CommonArguments.getNoPropertiesFileArgument();
        showUsage = CommonArguments.getShowUsage();

        // Register global arguments.
        argParser.addArgument(testOnly);
        argParser.addArgument(cli);
        argParser.addArgument(baseDN);
        argParser.addArgument(addBaseEntry);
        argParser.addArgument(importLDIF);
        argParser.addArgument(rejectedImportFile);
        argParser.addArgument(skippedImportFile);
        argParser.addArgument(sampleData);
        argParser.addArgument(ldapPort);
        argParser.addArgument(adminConnectorPort);
        argParser.addArgument(jmxPort);
        argParser.addArgument(skipPortCheck);
        argParser.addArgument(directoryManagerDN);
        argParser.addArgument(directoryManagerPwdString);
        argParser.addArgument(directoryManagerPwdFile);
        argParser.addArgument(enableWindowsService);
        argParser.addArgument(doNotStart);
        argParser.addArgument(enableStartTLS);
        argParser.addArgument(ldapsPort);
        argParser.addArgument(generateSelfSignedCertificate);
        argParser.addArgument(hostName);
        argParser.addArgument(usePkcs11);
        argParser.addArgument(useJavaKeyStore);
        argParser.addArgument(useJCEKS);
        argParser.addArgument(usePkcs12);
        argParser.addArgument(keyStorePassword);
        argParser.addArgument(keyStorePasswordFile);
        argParser.addArgument(certNickname);

        // Register the global arguments.
        argParser.addArgument(showUsage);
        argParser.setUsageArgument(showUsage, getOutputStream());
        argParser.addArgument(noPropertiesFile);
        argParser.setNoPropertiesFileArgument(noPropertiesFile);
        argParser.addArgument(propertiesFile);
        argParser.setFilePropertiesArgument(propertiesFile);
        argParser.addArgument(quietMode);
        argParser.addArgument(verbose);
        argParser.addArgument(noPrompt);
        argParser.addArgument(acceptLicense);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isInteractive() {
        return !noPrompt.isPresent();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isQuiet() {
        return quietMode.isPresent();
    }

    /**
     * Automatically accepts the license if it's present.
     *
     * @return {@code true} if license is accepted by default.
     */
    private boolean isAcceptLicense() {
        return acceptLicense.isPresent();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isVerbose() {
        return verbose.isPresent();
    }

    /**
     * Returns whether the command was launched in CLI mode or not.
     *
     * @return <CODE>true</CODE> if the command was launched to use CLI mode and <CODE>false</CODE> otherwise.
     */
    public boolean isCli() {
        return cli.isPresent();
    }

    /**
     * Checks that there are no conflicts with the provided ports (like if the user provided the same port for different
     * protocols).
     *
     * @param errorMessages
     *            the list of messages to which we add the error messages describing the problems encountered during the
     *            execution of the checking.
     */
    private void checkProvidedPorts(final Collection<LocalizableMessage> errorMessages) {
        /**
         * Check that the provided ports do not match.
         */
        try {
            final Set<Integer> ports = new HashSet<Integer>();
            ports.add(ldapPort.getIntValue());

            if (ports.contains(adminConnectorPort.getIntValue())) {
                errorMessages.add(ERR_PORT_ALREADY_SPECIFIED.get(adminConnectorPort.getIntValue()));
            } else {
                ports.add(adminConnectorPort.getIntValue());
            }

            if (jmxPort.isPresent()) {
                if (ports.contains(jmxPort.getIntValue())) {
                    errorMessages.add(ERR_PORT_ALREADY_SPECIFIED.get(jmxPort.getIntValue()));
                } else {
                    ports.add(jmxPort.getIntValue());
                }
            }
            if (ldapsPort.isPresent()) {
                if (ports.contains(ldapsPort.getIntValue())) {
                    errorMessages.add(ERR_PORT_ALREADY_SPECIFIED.get(ldapsPort.getIntValue()));
                } else {
                    ports.add(ldapsPort.getIntValue());
                }
            }
        } catch (ArgumentException ae) {
            // TODO log that
            /*
             * logger.error(LocalizableMessage.raw("Unexpected error.  " +
             * "Assuming that it is caused by a previous parsing issue: " + ae, ae));
             */
        }
    }

    /**
     * Checks that there are no conflicts with the import data arguments.
     *
     * @param errorMessages
     *            the list of messages to which we add the error messages describing the problems encountered during the
     *            execution of the checking.
     */
    private void checkImportDataArguments(final Collection<LocalizableMessage> errorMessages) {
        // Make sure that the user didn't provide conflicting arguments.
        if (addBaseEntry.isPresent()) {
            if (importLDIF.isPresent()) {
                errorMessages.add(ERR_TOOL_CONFLICTING_ARGS.get(addBaseEntry.getLongIdentifier(),
                        importLDIF.getLongIdentifier()));
            } else if (sampleData.isPresent()) {
                errorMessages.add(ERR_TOOL_CONFLICTING_ARGS.get(addBaseEntry.getLongIdentifier(),
                        sampleData.getLongIdentifier()));
            }
        } else if (importLDIF.isPresent() && sampleData.isPresent()) {
            errorMessages.add(ERR_TOOL_CONFLICTING_ARGS.get(importLDIF.getLongIdentifier(),
                    sampleData.getLongIdentifier()));
        }

        if (rejectedImportFile.isPresent() && addBaseEntry.isPresent()) {
            errorMessages.add(ERR_TOOL_CONFLICTING_ARGS.get(addBaseEntry.getLongIdentifier(),
                    rejectedImportFile.getLongIdentifier()));
        } else if (rejectedImportFile.isPresent() && sampleData.isPresent()) {
            errorMessages.add(ERR_TOOL_CONFLICTING_ARGS.get(rejectedImportFile.getLongIdentifier(),
                    sampleData.getLongIdentifier()));
        }

        if (skippedImportFile.isPresent() && addBaseEntry.isPresent()) {
            errorMessages.add(ERR_TOOL_CONFLICTING_ARGS.get(addBaseEntry.getLongIdentifier(),
                    skippedImportFile.getLongIdentifier()));
        } else if (skippedImportFile.isPresent() && sampleData.isPresent()) {
            errorMessages.add(ERR_TOOL_CONFLICTING_ARGS.get(skippedImportFile.getLongIdentifier(),
                    sampleData.getLongIdentifier()));
        }

        if (noPrompt.isPresent() && !baseDN.isPresent() && baseDN.getDefaultValue() == null) {
            final Argument[] args = { importLDIF, addBaseEntry, sampleData };
            for (final Argument arg : args) {
                if (arg.isPresent()) {
                    errorMessages.add(ERR_ARGUMENT_NO_BASE_DN_SPECIFIED.get("--" + arg.getLongIdentifier()));
                }
            }
        }
    }

    /**
     * Checks that there are no conflicts with the security arguments. If we are in no prompt mode, check that all the
     * information required has been provided (but not if this information is valid: we do not try to open the keystores
     * or to check that the LDAPS port is in use).
     *
     * @param errorMessages
     *            the list of messages to which we add the error messages describing the problems encountered during the
     *            execution of the checking.
     */
    private void checkSecurityArguments(final Collection<LocalizableMessage> errorMessages) {
        final boolean certificateRequired = ldapsPort.isPresent() || enableStartTLS.isPresent();

        int certificateType = 0;
        if (generateSelfSignedCertificate.isPresent()) {
            certificateType++;
        }
        if (useJavaKeyStore.isPresent()) {
            certificateType++;
        }
        if (useJCEKS.isPresent()) {
            certificateType++;
        }
        if (usePkcs11.isPresent()) {
            certificateType++;
        }
        if (usePkcs12.isPresent()) {
            certificateType++;
        }

        if (certificateType > 1) {
            errorMessages.add(ERR_SEVERAL_CERTIFICATE_TYPE_SPECIFIED.get());
        }

        if (certificateRequired && noPrompt.isPresent() && (certificateType == 0)) {
            errorMessages.add(ERR_CERTIFICATE_REQUIRED_FOR_SSL_OR_STARTTLS.get());
        }

        if (certificateType == 1) {
            if (!generateSelfSignedCertificate.isPresent()) {
                // Check that we have only a password.
                if (keyStorePassword.isPresent() && keyStorePasswordFile.isPresent()) {
                    LocalizableMessage message = ERR_TWO_CONFLICTING_ARGUMENTS.get(
                            keyStorePassword.getLongIdentifier(), keyStorePasswordFile.getLongIdentifier());
                    errorMessages.add(message);
                }

                // Check that we have one password in no prompt mode.
                if (noPrompt.isPresent() && !keyStorePassword.isPresent() && !keyStorePasswordFile.isPresent()) {
                    LocalizableMessage message = ERR_NO_KEYSTORE_PASSWORD.get(keyStorePassword.getLongIdentifier(),
                            keyStorePasswordFile.getLongIdentifier());
                    errorMessages.add(message);
                }
            }
            if (noPrompt.isPresent() && !ldapsPort.isPresent() && !enableStartTLS.isPresent()) {
                LocalizableMessage message = ERR_SSL_OR_STARTTLS_REQUIRED.get(ldapsPort.getLongIdentifier(),
                        enableStartTLS.getLongIdentifier());
                errorMessages.add(message);
            }
        }
    }

    /**
     * Checks that there are no conflicts with the directory manager passwords. If we are in no prompt mode, check that
     * the password was provided.
     *
     * @param errorMessages
     *            the list of messages to which we add the error messages describing the problems encountered during the
     *            execution of the checking.
     */
    private void checkServerPassword(Collection<LocalizableMessage> errorMessages) {
        if (directoryManagerPwdString.isPresent() && directoryManagerPwdFile.isPresent()) {
            errorMessages.add(ERR_TWO_CONFLICTING_ARGUMENTS.get(
                    directoryManagerPwdString.getLongIdentifier(), directoryManagerPwdFile.getLongIdentifier()));
        }

        if (noPrompt.isPresent() && !directoryManagerPwdString.isPresent()
                && !directoryManagerPwdFile.isPresent()) {
            errorMessages.add(ERR_NO_ROOT_PASSWORD.get(directoryManagerPwdString.getLongIdentifier(),
                    directoryManagerPwdFile.getLongIdentifier()));
        }
    }


    /**
     * This is a helper method that gets a LocalizableMessage representation of the elements in the Collection of
     * Messages. The LocalizableMessage will display the different elements separated by the separator String.
     * TODO move this function.
     * @param col
     *            the collection containing the messages.
     * @param separator
     *            the separator String to be used.
     * @return the message representation for the collection; LocalizableMessage.EMPTY if null.
     */
    private static LocalizableMessage getMessageFromCollection(final Collection<LocalizableMessage> col,
            final String separator) {
        if (col == null || col.isEmpty()) {
            return LocalizableMessage.EMPTY;
        } else {
            LocalizableMessageBuilder mb = null;
            for (final LocalizableMessage m : col) {
                if (mb == null) {
                    mb = new LocalizableMessageBuilder(m);
                } else {
                    mb.append(separator).append(m);
                }
            }
            return mb.toMessage();
        }
    }
}
