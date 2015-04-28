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
 *      Copyright 2014-2015 ForgeRock AS.
 */
package org.forgerock.opendj.server.setup.cli;

import static com.forgerock.opendj.cli.Utils.filterExitCode;
import static com.forgerock.opendj.cli.Utils.LINE_SEPARATOR;
import static com.forgerock.opendj.cli.Utils.checkJavaVersion;
import static com.forgerock.opendj.cli.CliMessages.*;
import static com.forgerock.opendj.cli.CliConstants.*;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;

import com.forgerock.opendj.cli.Argument;
import com.forgerock.opendj.cli.ArgumentException;
import com.forgerock.opendj.cli.BooleanArgument;
import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.CommonArguments;
import com.forgerock.opendj.cli.ConsoleApplication;
import com.forgerock.opendj.cli.FileBasedArgument;
import com.forgerock.opendj.cli.IntegerArgument;
import com.forgerock.opendj.cli.ReturnCode;
import com.forgerock.opendj.cli.StringArgument;
import com.forgerock.opendj.cli.SubCommand;
import com.forgerock.opendj.cli.SubCommandArgumentParser;
import com.forgerock.opendj.cli.Utils;

/**
 * This class implements the new CLI for OpenDJ3 setup.
 */
public final class SetupCli extends ConsoleApplication {

    /**
     * Setup's logger.
     */
    private static final LocalizedLogger LOG = LocalizedLogger.getLoggerForThisClass();

    /**
     * TODO remove that after implementation in config.
     *
     * @return The installation path.
     */
    static String getInstallationPath() {
        return "/home/violette/OpenDJ-3.0.0/";
    }

    /**
     * TODO remove that after implementation in config.
     *
     * @return The instance path.
     */
    static String getInstancePath() {
        return "/home/violette/OpenDJ-3.0.0/";
    }


    private SubCommandArgumentParser argParser;

    private BooleanArgument cli;
    private BooleanArgument addBaseEntry;
    private BooleanArgument skipPortCheck;
    private BooleanArgument enableWindowsService;
    private BooleanArgument doNotStart;
    private BooleanArgument enableStartTLS;
    private BooleanArgument generateSelfSignedCertificate;
    private StringArgument hostName;
    private BooleanArgument usePkcs11;
    private FileBasedArgument directoryManagerPwdFile;
    private FileBasedArgument keyStorePasswordFile;
    private IntegerArgument ldapPort;
    private IntegerArgument adminConnectorPort;
    private IntegerArgument ldapsPort;
    private IntegerArgument jmxPort;
    private IntegerArgument sampleData;
    private StringArgument baseDN;
    private StringArgument importLDIF;
    private StringArgument rejectedImportFile;
    private StringArgument skippedImportFile;
    private StringArgument directoryManagerDN;
    private StringArgument directoryManagerPwdString;
    private StringArgument useJavaKeyStore;
    private StringArgument useJCEKS;
    private StringArgument usePkcs12;
    private StringArgument keyStorePassword;
    private StringArgument certNickname;
    private IntegerArgument connectTimeout;
    private BooleanArgument acceptLicense;

    /** Sub-commands. */
    private SubCommand createDirectoryServer;
    private SubCommand createProxy;

    /** Register the global arguments. */
    private BooleanArgument noPrompt;
    private BooleanArgument quietMode;
    private BooleanArgument verbose;
    private StringArgument propertiesFile;
    private BooleanArgument noPropertiesFile;
    private BooleanArgument showUsage;

    private SetupCli() {
        // Nothing to do.
    }

    /** To allow tests. */
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
        // TODO Activate logger when the instance/installation path will be resolved.
        // SetupLog.initLogFileHandler();

        try {
            checkJavaVersion();
        } catch (ClientException e) {
            errPrintln(e.getMessageObject());
            return ReturnCode.JAVA_VERSION_INCOMPATIBLE.get();
        }

        try {
            argParser = new SubCommandArgumentParser("setup", INFO_SETUP_DESCRIPTION.get(), true);
            initializeArguments();
        } catch (ArgumentException e) {
            final LocalizableMessage message = ERR_CANNOT_INITIALIZE_ARGS.get(e.getMessage());
            errPrintln(message);
            return ReturnCode.CLIENT_SIDE_PARAM_ERROR.get();
        }

        // Parse the command-line arguments provided to this program.
        try {
            argParser.parseArguments(args);

            if (argParser.usageOrVersionDisplayed()) {
                // If we should just display usage or version information, then print it and exit.
                return ReturnCode.SUCCESS.get();
            }
        } catch (final ArgumentException e) {
            final LocalizableMessage message = ERR_ERROR_PARSING_ARGS.get(e.getMessage());
            errPrintln(message);
            return ReturnCode.CLIENT_SIDE_PARAM_ERROR.get();
        }

        // Verifying provided informations.
        try {
            final LinkedHashSet<LocalizableMessage> errorMessages = new LinkedHashSet<>();
            checkServerPassword(errorMessages);
            checkProvidedPorts(errorMessages);
            checkImportDataArguments(errorMessages);
            checkSecurityArguments(errorMessages);
            if (errorMessages.size() > 0) {
                throw new ArgumentException(ERR_CANNOT_INITIALIZE_ARGS.get(
                        getMessageFromCollection(errorMessages, LINE_SEPARATOR)));
            }
        } catch (final ArgumentException e) {
            errPrintln(e.getMessageObject());
            return ReturnCode.CLIENT_SIDE_PARAM_ERROR.get();
        }

        // Starts setup process.
        try {
            fillSetupSettings();
            runSetupInstallation();
        } catch (ClientException ex) {
            return ex.getReturnCode();
        } catch (Exception ex) {
            // TODO
            //println(Style.ERROR, LocalizableMessage.raw("...?"));
            return ReturnCode.ERROR_UNEXPECTED.get();
        }
        return ReturnCode.SUCCESS.get();
    }

    /**
     * Initialize setup's arguments by default.
     *
     * @throws ArgumentException
     *             If an exception occurs during the initialization of the arguments.
     */
    private void initializeArguments() throws ArgumentException {
        // Options.
        acceptLicense = addGlobal(CommonArguments.getAcceptLicense());
        cli = addGlobal(CommonArguments.getCLI());
        baseDN = addGlobal(CommonArguments.getBaseDN());
        addBaseEntry = addGlobal(CommonArguments.getAddBaseEntry());
        importLDIF = addGlobal(CommonArguments.getLDIFFile(INFO_DESCRIPTION_IMPORTLDIF.get()));
        rejectedImportFile = addGlobal(CommonArguments.getRejectedImportLdif());
        skippedImportFile = addGlobal(CommonArguments.getSkippedImportFile());
        sampleData = addGlobal(CommonArguments.getSampleData());
        ldapPort = addGlobal(CommonArguments.getLDAPPort(DEFAULT_LDAP_PORT));
        ldapsPort = addGlobal(CommonArguments.getLDAPSPort(DEFAULT_LDAPS_PORT));
        adminConnectorPort = addGlobal(CommonArguments.getAdminLDAPPort(DEFAULT_ADMIN_PORT));
        jmxPort = addGlobal(CommonArguments.getJMXPort(DEFAULT_JMX_PORT));
        skipPortCheck = addGlobal(CommonArguments.getSkipPortCheck());
        directoryManagerDN = addGlobal(CommonArguments.getRootDN());
        directoryManagerPwdString = addGlobal(CommonArguments.getRootDNPwd());
        directoryManagerPwdFile = addGlobal(CommonArguments.getRootDNPwdFile());
        enableWindowsService = addGlobal(CommonArguments.getEnableWindowsService());
        doNotStart = addGlobal(CommonArguments.getDoNotStart());
        enableStartTLS = addGlobal(CommonArguments.getEnableTLS());
        generateSelfSignedCertificate = addGlobal(CommonArguments.getGenerateSelfSigned());
        hostName = addGlobal(CommonArguments.getHostName(Utils.getDefaultHostName()));
        usePkcs11 = addGlobal(CommonArguments.getUsePKCS11Keystore());
        useJavaKeyStore = addGlobal(CommonArguments.getUseJavaKeyStore());
        useJCEKS = addGlobal(CommonArguments.getUseJCEKS());
        usePkcs12 = addGlobal(CommonArguments.getUsePKCS12KeyStore());
        keyStorePassword = addGlobal(CommonArguments.getKeyStorePassword());
        keyStorePasswordFile = addGlobal(CommonArguments.getKeyStorePasswordFile());
        certNickname = addGlobal(CommonArguments.getCertNickName());
        connectTimeout = CommonArguments.getConnectTimeOut();

        // Utility Input Output Options.
        noPrompt = addGlobal(CommonArguments.getNoPrompt());
        quietMode = addGlobal(CommonArguments.getQuiet());
        verbose = addGlobal(CommonArguments.getVerbose());
        propertiesFile = addGlobal(CommonArguments.getPropertiesFile());
        noPropertiesFile = addGlobal(CommonArguments.getNoPropertiesFile());
        showUsage = addGlobal(CommonArguments.getShowUsage());

        //Sub-commands && their arguments
        final ArrayList<SubCommand> subCommandList = new ArrayList<>(2);
        createDirectoryServer = new SubCommand(argParser, "create-directory-server",
                INFO_SETUP_SUBCOMMAND_CREATE_DIRECTORY_SERVER.get());
        // TODO to complete.
        createProxy = new SubCommand(argParser, "create-proxy",
                INFO_SETUP_SUBCOMMAND_CREATE_PROXY.get());
        subCommandList.add(createDirectoryServer);
        subCommandList.add(createProxy);

        argParser.setUsageGroupArgument(showUsage, subCommandList);

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

    private <A extends Argument> A addGlobal(A arg) throws ArgumentException {
        argParser.addGlobalArgument(arg);
        return arg;
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
     * Returns whether the command was launched to setup proxy or not.
     *
     * @return <CODE>true</CODE> if the command was launched to setup a proxy and <CODE>false</CODE> otherwise.
     */
    public boolean isCreateProxy() {
        return argParser.getSubCommand("create-proxy") != null;
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
        // Check that the provided ports do not match.
        try {
            final Set<Integer> ports = new HashSet<>();
            ports.add(ldapPort.getIntValue());

            checkPortArgument(adminConnectorPort, ports, errorMessages);

            if (jmxPort.isPresent()) {
                checkPortArgument(jmxPort, ports, errorMessages);
            }
            if (ldapsPort.isPresent()) {
                checkPortArgument(ldapsPort, ports, errorMessages);
            }
        } catch (ArgumentException ae) {
            LOG.error(LocalizableMessage.raw("Unexpected error. "
                    + "Assuming that it is caused by a previous parsing issue: " + ae, ae));
        }
    }

    private void checkPortArgument(IntegerArgument portArg, final Set<Integer> ports,
            final Collection<LocalizableMessage> errorMessages) throws ArgumentException {
        if (ports.contains(portArg.getIntValue())) {
            errorMessages.add(ERR_PORT_ALREADY_SPECIFIED.get(portArg.getIntValue()));
        } else {
            ports.add(portArg.getIntValue());
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

        if (certificateRequired && noPrompt.isPresent() && certificateType == 0) {
            errorMessages.add(ERR_CERTIFICATE_REQUIRED_FOR_SSL_OR_STARTTLS.get());
        }

        if (certificateType == 1) {
            if (!generateSelfSignedCertificate.isPresent()) {
                // Check that we have only a password.
                if (keyStorePassword.isPresent() && keyStorePasswordFile.isPresent()) {
                    final LocalizableMessage message = ERR_TWO_CONFLICTING_ARGUMENTS.get(
                            keyStorePassword.getLongIdentifier(), keyStorePasswordFile.getLongIdentifier());
                    errorMessages.add(message);
                }

                // Check that we have one password in no prompt mode.
                if (noPrompt.isPresent() && !keyStorePassword.isPresent() && !keyStorePasswordFile.isPresent()) {
                    final LocalizableMessage message = ERR_NO_KEYSTORE_PASSWORD.get(
                            keyStorePassword.getLongIdentifier(), keyStorePasswordFile.getLongIdentifier());
                    errorMessages.add(message);
                }
            }
            if (noPrompt.isPresent() && !ldapsPort.isPresent() && !enableStartTLS.isPresent()) {
                final LocalizableMessage message = ERR_SSL_OR_STARTTLS_REQUIRED.get(ldapsPort.getLongIdentifier(),
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

    /**
     * Fills the setup components according to the arguments provided by the user.
     * @throws ArgumentException
     */
    private void fillSetupSettings() throws ArgumentException {
        // TODO ...
    }

    /**
     * Launches the setup process.
     * @throws ClientException
     */
    private void runSetupInstallation() throws ClientException {
        // TODO move that function to another class.
    }
}
