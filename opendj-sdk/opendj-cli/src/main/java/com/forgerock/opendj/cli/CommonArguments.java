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

import static com.forgerock.opendj.cli.CliMessages.*;
import static com.forgerock.opendj.cli.ArgumentConstants.*;

import org.forgerock.i18n.LocalizableMessage;

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
    public static BooleanArgument getShowUsage() throws ArgumentException {
        return new BooleanArgument("showUsage", OPTION_SHORT_HELP, OPTION_LONG_HELP, INFO_DESCRIPTION_SHOWUSAGE.get());
    }

    /**
     * Returns the "verbose" boolean argument.
     *
     * @return The "verbose" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getVerbose() throws ArgumentException {
        final BooleanArgument verbose = new BooleanArgument("verbose", 'v', "verbose", INFO_DESCRIPTION_VERBOSE.get());
        verbose.setPropertyName("verbose");
        return verbose;
    }

    /**
     * Returns the "port" integer argument.
     *
     * @param defaultPort
     *            The default port number.
     * @param description
     *            Port number's description.
     * @return The "port" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static IntegerArgument getPort(final int defaultPort, final LocalizableMessage description)
            throws ArgumentException {
        final IntegerArgument port = new IntegerArgument("port", OPTION_SHORT_PORT, OPTION_LONG_PORT, false, false,
                true, INFO_PORT_PLACEHOLDER.get(), defaultPort, null, true, 1, true, 65535,
                description);
        port.setPropertyName(OPTION_LONG_PORT);
        return port;
    }

    /**
     * Returns the "get properties file" string argument.
     *
     * @return The "get properties file" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getPropertiesFile() throws ArgumentException {
        return new StringArgument("propertiesFilePath", null, OPTION_LONG_PROP_FILE_PATH, false, false, true,
                INFO_PROP_FILE_PATH_PLACEHOLDER.get(), null, null, INFO_DESCRIPTION_PROP_FILE_PATH.get());
    }

    /**
     * Returns the "No properties file" boolean argument.
     *
     * @return The "noPropertiesFile" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getNoPropertiesFile() throws ArgumentException {
        return new BooleanArgument("noPropertiesFile", null, OPTION_LONG_NO_PROP_FILE,
                INFO_DESCRIPTION_NO_PROP_FILE.get());
    }

    /**
     * Returns the "Continue On Error" boolean argument.
     *
     * @return The "continueOnError" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getContinueOnError() throws ArgumentException {
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
    public static IntegerArgument getVersion() throws ArgumentException {
        final IntegerArgument version = new IntegerArgument("version", OPTION_SHORT_PROTOCOL_VERSION,
                OPTION_LONG_PROTOCOL_VERSION, false, false, true, INFO_PROTOCOL_VERSION_PLACEHOLDER.get(), 3, null,
                INFO_DESCRIPTION_VERSION.get());
        version.setPropertyName(OPTION_LONG_PROTOCOL_VERSION);
        return version;
    }

    /**
     * Returns the "quiet" boolean argument.
     *
     * @return The "quiet" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getQuiet() throws ArgumentException {
        final BooleanArgument quiet = new BooleanArgument(OPTION_LONG_QUIET, OPTION_SHORT_QUIET, OPTION_LONG_QUIET,
                INFO_DESCRIPTION_QUIET.get());
        quiet.setPropertyName(OPTION_LONG_QUIET);
        return quiet;

    }

    /**
     * Returns the "no-prompt" boolean argument.
     *
     * @return The "no-prompt" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getNoPrompt() throws ArgumentException {
        return new BooleanArgument(OPTION_LONG_NO_PROMPT, OPTION_SHORT_NO_PROMPT, OPTION_LONG_NO_PROMPT,
                INFO_DESCRIPTION_NO_PROMPT.get());
    }

    /**
     * Returns the "acceptLicense" boolean argument.
     *
     * @return The "acceptLicense" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getAcceptLicense() throws ArgumentException {
        return new BooleanArgument(OPTION_LONG_ACCEPT_LICENSE, null, OPTION_LONG_ACCEPT_LICENSE,
                INFO_OPTION_ACCEPT_LICENSE.get());
    }

    /**
     * Returns the "test only" boolean argument.
     *
     * @return The "test only" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getTestOnly() throws ArgumentException {
        final BooleanArgument testOnly = new BooleanArgument("testOnly".toLowerCase(), 't', "testOnly",
                INFO_ARGUMENT_DESCRIPTION_TESTONLY.get());
        testOnly.setHidden(true);
        testOnly.setPropertyName("testOnly");
        return testOnly;
    }

    /**
     * Returns the "trustAll" boolean argument.
     *
     * @return The "trustAll" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getTrustAll() throws ArgumentException {
        final BooleanArgument trustAll = new BooleanArgument(OPTION_LONG_TRUSTALL, OPTION_SHORT_TRUSTALL,
                OPTION_LONG_TRUSTALL, INFO_DESCRIPTION_TRUSTALL.get());
        trustAll.setPropertyName(OPTION_LONG_TRUSTALL);
        return trustAll;
    }

    /**
     * Returns the "trustStorePath" string argument.
     *
     * @return The "trustStorePath" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getTrustStorePath() throws ArgumentException {
        final StringArgument tsPath = new StringArgument("trustStorePath", OPTION_SHORT_TRUSTSTOREPATH,
                OPTION_LONG_TRUSTSTOREPATH, false, false, true, INFO_TRUSTSTOREPATH_PLACEHOLDER.get(), null, null,
                INFO_DESCRIPTION_TRUSTSTOREPATH.get());
        tsPath.setPropertyName(OPTION_LONG_TRUSTSTOREPATH);
        return tsPath;
    }

    /**
     * Returns the "trustStorePassword" string argument.
     *
     * @return The "trustStorePassword" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getTrustStorePassword() throws ArgumentException {
        final StringArgument tsPwd = new StringArgument("trustStorePassword", OPTION_SHORT_TRUSTSTORE_PWD,
                OPTION_LONG_TRUSTSTORE_PWD, false, false, true, INFO_TRUSTSTORE_PWD_PLACEHOLDER.get(), null, null,
                INFO_DESCRIPTION_TRUSTSTOREPASSWORD.get());
        tsPwd.setPropertyName(OPTION_LONG_TRUSTSTORE_PWD);
        return tsPwd;
    }

    /**
     * Returns the "trustStorePasswordFile" string argument.
     *
     * @return The "trustStorePasswordFile" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static FileBasedArgument getTrustStorePasswordFile() throws ArgumentException {
        final FileBasedArgument tsPwdFile = new FileBasedArgument("trustStorePasswordFile",
                OPTION_SHORT_TRUSTSTORE_PWD_FILE, OPTION_LONG_TRUSTSTORE_PWD_FILE, false, false,
                INFO_TRUSTSTORE_PWD_FILE_PLACEHOLDER.get(), null, null, INFO_DESCRIPTION_TRUSTSTOREPASSWORD_FILE.get());
        tsPwdFile.setPropertyName(OPTION_LONG_TRUSTSTORE_PWD_FILE);
        return tsPwdFile;
    }

    /**
     * Returns the "connection timeout" boolean argument.
     *
     * @param defaultTimeout
     *            The default timeout.
     * @return The "connectTimeout" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static IntegerArgument getConnectTimeOut(final int defaultTimeout) throws ArgumentException {
        final IntegerArgument connectTimeout = new IntegerArgument(OPTION_LONG_CONNECT_TIMEOUT, null,
                OPTION_LONG_CONNECT_TIMEOUT, false, false, true, INFO_TIMEOUT_PLACEHOLDER.get(), defaultTimeout, null,
                true, 0, false, Integer.MAX_VALUE, INFO_DESCRIPTION_CONNECTION_TIMEOUT.get());
        connectTimeout.setPropertyName(OPTION_LONG_CONNECT_TIMEOUT);
        connectTimeout.setHidden(true);
        return connectTimeout;
    }

    /**
     * Returns the "CLI" boolean argument.
     *
     * @return The "CLI" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getCLI() throws ArgumentException {
        final BooleanArgument cli = new BooleanArgument(OPTION_LONG_CLI.toLowerCase(), OPTION_SHORT_CLI,
                OPTION_LONG_CLI, INFO_ARGUMENT_DESCRIPTION_CLI.get());
        cli.setPropertyName(OPTION_LONG_CLI);
        return cli;
    }

    /**
     * Returns the "configfile" string argument.
     *
     * @return The "configfile" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getConfigFile() throws ArgumentException {
        final StringArgument configFile = new StringArgument("configfile", 'f', "configFile", true, false, true,
                INFO_CONFIGFILE_PLACEHOLDER.get(), null, null, INFO_DESCRIPTION_CONFIG_FILE.get());
        configFile.setHidden(true);
        return configFile;
    }

    /**
     * Returns the "configclass" string argument.
     *
     * @param configFileHandlerName
     *            The config file handler name.
     * @return The "configclass" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getConfigClass(final String configFileHandlerName) throws ArgumentException {
        final StringArgument configClass = new StringArgument("configclass", OPTION_SHORT_CONFIG_CLASS,
                OPTION_LONG_CONFIG_CLASS, true, false, true, INFO_CONFIGCLASS_PLACEHOLDER.get(), configFileHandlerName,
                null, INFO_DESCRIPTION_CONFIG_CLASS.get());
        configClass.setHidden(true);
        return configClass;
    }

    /**
     * Returns the "baseDN" string argument.
     *
     * @return The "baseDN" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getBaseDN() throws ArgumentException {
        return new StringArgument(OPTION_LONG_BASEDN.toLowerCase(), OPTION_SHORT_BASEDN, OPTION_LONG_BASEDN, false,
                true, true, INFO_BASEDN_PLACEHOLDER.get(), null, OPTION_LONG_BASEDN,
                INFO_ARGUMENT_DESCRIPTION_BASEDN.get());
    }

    /**
     * Returns the "batchFilePath" string argument.
     *
     * @return The "batchFilePath" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getBatchFilePath() throws ArgumentException {
        return new StringArgument(OPTION_LONG_BATCH_FILE_PATH, OPTION_SHORT_BATCH_FILE_PATH,
                OPTION_LONG_BATCH_FILE_PATH, false, false, true, INFO_BATCH_FILE_PATH_PLACEHOLDER.get(), null, null,
                INFO_DESCRIPTION_BATCH_FILE_PATH.get());
    }

    /**
     * Returns the "bindDN" string argument.
     *
     * @param defaultBindDN
     *            The default bind DN.
     * @return The "bindDN" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getBindDN(final String defaultBindDN) throws ArgumentException {
        final StringArgument bindDN = new StringArgument("bindDN", OPTION_SHORT_BINDDN, OPTION_LONG_BINDDN, false,
                false, true, INFO_BINDDN_PLACEHOLDER.get(), defaultBindDN, null, INFO_DESCRIPTION_BINDDN.get());
        bindDN.setPropertyName(OPTION_LONG_BINDDN);
        return bindDN;
    }

    /**
     * Returns the "bindPassword" string argument.
     *
     * @return The "bindPassword" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getBindPassword() throws ArgumentException {
        final StringArgument bindPassword = new StringArgument("bindPassword", OPTION_SHORT_BINDPWD,
                OPTION_LONG_BINDPWD, false, false, true, INFO_BINDPWD_PLACEHOLDER.get(), null, null,
                INFO_DESCRIPTION_BINDPASSWORD.get());
        bindPassword.setPropertyName(OPTION_LONG_BINDPWD);
        return bindPassword;
    }

    /**
     * Returns the "bindPasswordFile" file argument.
     *
     * @return The "bindPasswordFile" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static FileBasedArgument getBindPasswordFile() throws ArgumentException {
        final FileBasedArgument bindPasswordFile = new FileBasedArgument("bindPasswordFile", OPTION_SHORT_BINDPWD_FILE,
                OPTION_LONG_BINDPWD_FILE, false, false, INFO_BINDPWD_FILE_PLACEHOLDER.get(), null, null,
                INFO_DESCRIPTION_BINDPASSWORDFILE.get());
        bindPasswordFile.setPropertyName(OPTION_LONG_BINDPWD_FILE);
        return bindPasswordFile;
    }

    /**
     * Returns the "add base entry" boolean argument.
     *
     * @return The "addBaseEntry" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getAddBaseEntry() throws ArgumentException {
        final BooleanArgument addBaseEntryArg = new BooleanArgument("addBaseEntry".toLowerCase(), 'a', "addBaseEntry",
                INFO_ARGUMENT_DESCRIPTION_ADDBASE.get());
        addBaseEntryArg.setPropertyName("addBaseEntry");
        return addBaseEntryArg;
    }

    /**
     * Returns the "import LDIF" string argument.
     *
     * @return The "import LDIF" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getImportLDIF() throws ArgumentException {
        return new StringArgument(OPTION_LONG_LDIF_FILE.toLowerCase(), OPTION_SHORT_LDIF_FILE, OPTION_LONG_LDIF_FILE,
                false, true, true, INFO_LDIFFILE_PLACEHOLDER.get(), null, OPTION_LONG_LDIF_FILE,
                INFO_ARGUMENT_DESCRIPTION_IMPORTLDIF.get());
    }

    /**
     * Returns the "rejected import ldif file" string argument.
     *
     * @return The "rejectFile" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getRejectedImportLdif() throws ArgumentException {
        return new StringArgument("rejectFile".toLowerCase(), 'R', "rejectFile", false, false, true,
                INFO_REJECT_FILE_PLACEHOLDER.get(), null, "rejectFile", INFO_GENERAL_DESCRIPTION_REJECTED_FILE.get());
    }

    /**
     * Returns the "remote" boolean argument.
     *
     * @return The "remote" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getRemote() throws ArgumentException {
        final BooleanArgument remote = new BooleanArgument(OPTION_LONG_REMOTE.toLowerCase(), OPTION_SHORT_REMOTE,
                OPTION_LONG_REMOTE, INFO_DESCRIPTION_REMOTE.get());
        remote.setPropertyName(OPTION_LONG_REMOTE);
        return remote;
    }

    /**
     * Returns the "skip file" string argument.
     *
     * @return The "skipFile" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getSkippedImportFile() throws ArgumentException {
        return new StringArgument("skipFile".toLowerCase(), null, "skipFile", false, false, true,
                INFO_SKIP_FILE_PLACEHOLDER.get(), null, "skipFile", INFO_GENERAL_DESCRIPTION_SKIPPED_FILE.get());
    }

    /**
     * Returns the "sample data" integer argument.
     *
     * @return The "sampleData" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static IntegerArgument getSampleData() throws ArgumentException {
        return new IntegerArgument("sampleData".toLowerCase(), 'd', "sampleData", false, false, true,
                INFO_NUM_ENTRIES_PLACEHOLDER.get(), 0, "sampleData", true, 0, false, 0,
                INFO_SETUP_DESCRIPTION_SAMPLE_DATA.get());
    }

    /**
     * Returns the "sasloption" string argument.
     *
     * @return The "sasloption" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getSASL() throws ArgumentException {
        final StringArgument sasl = new StringArgument("sasloption", OPTION_SHORT_SASLOPTION, OPTION_LONG_SASLOPTION,
                false, true, true, INFO_SASL_OPTION_PLACEHOLDER.get(), null, null,
                INFO_LDAP_CONN_DESCRIPTION_SASLOPTIONS.get());
        sasl.setPropertyName(OPTION_LONG_SASLOPTION);
        return sasl;
    }
    /**
     * Returns the "script-friendly" boolean argument.
     *
     * @return The "script-friendly" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getScriptFriendly() throws ArgumentException {
        final BooleanArgument sf = new BooleanArgument("script-friendly", OPTION_SHORT_SCRIPT_FRIENDLY,
                OPTION_LONG_SCRIPT_FRIENDLY, INFO_DESCRIPTION_SCRIPT_FRIENDLY.get());
        sf.setPropertyName(OPTION_LONG_SCRIPT_FRIENDLY);
        return sf;
    }

    /**
     * Returns the "LDAP port" integer argument.
     *
     * @param defaultLdapPort
     *            Default LDAP Connector port.
     * @return The "ldapPort" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static IntegerArgument getLDAPPort(final int defaultLdapPort) throws ArgumentException {
        return new IntegerArgument("ldapPort".toLowerCase(), OPTION_SHORT_PORT, "ldapPort", false, false, true,
                INFO_PORT_PLACEHOLDER.get(), defaultLdapPort, "ldapPort", true, 1, true, 65535,
                INFO_ARGUMENT_DESCRIPTION_LDAPPORT.get());
    }

    /**
     * Returns the "Admin port" integer argument.
     *
     * @param defaultAdminPort
     *            Default Administration Connector port.
     * @return The "adminConnectorPort" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static IntegerArgument getAdminLDAPPort(final int defaultAdminPort) throws ArgumentException {
        return new IntegerArgument("adminConnectorPort".toLowerCase(), null, "adminConnectorPort", false, false, true,
                INFO_PORT_PLACEHOLDER.get(), defaultAdminPort, "adminConnectorPort", true, 1, true, 65535,
                INFO_ARGUMENT_DESCRIPTION_ADMINCONNECTORPORT.get());
    }

    /**
     * Returns the "advanced" boolean argument.
     *
     * @return The "advanced" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getAdvancedMode() throws ArgumentException {
        final BooleanArgument advanced = new BooleanArgument(OPTION_LONG_ADVANCED, null, OPTION_LONG_ADVANCED,
                INFO_DESCRIPTION_ADVANCED.get());
        advanced.setPropertyName(OPTION_LONG_ADVANCED);
        return advanced;
    }

    /**
     * Returns the "JMX port" integer argument.
     *
     * @param defaultJMXPort
     *            Default JMX port.
     * @return The "jmxPort" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static IntegerArgument getJMXPort(final int defaultJMXPort) throws ArgumentException {
        return new IntegerArgument("jmxPort".toLowerCase(), 'x', "jmxPort", false, false, true,
                INFO_JMXPORT_PLACEHOLDER.get(), defaultJMXPort, "jmxPort", true, 1, true, 65535,
                INFO_ARGUMENT_DESCRIPTION_SKIPPORT.get());
    }

    /**
     * Returns the "skip port check" boolean argument.
     *
     * @return The "getSkipPortCheck" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getSkipPortCheck() throws ArgumentException {
        final BooleanArgument skipPortCheck = new BooleanArgument("skipPortCheck".toLowerCase(), 'S', "skipPortCheck",
                INFO_ARGUMENT_DESCRIPTION_SKIPPORT.get());
        skipPortCheck.setPropertyName("skipPortCheck");
        return skipPortCheck;
    }

    /**
     * Returns the "startTLS" boolean argument.
     *
     * @return The "startTLS" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getStartTLS() throws ArgumentException {
        final BooleanArgument useStartTLS = new BooleanArgument("startTLS", OPTION_SHORT_START_TLS,
                OPTION_LONG_START_TLS, INFO_DESCRIPTION_START_TLS.get());
        useStartTLS.setPropertyName(OPTION_LONG_START_TLS);
        return useStartTLS;
    }
    /**
     * Returns the "directory manager DN" string argument.
     *
     * @return The "rootUserDN" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getRootDN() throws ArgumentException {
        return new StringArgument(OPTION_LONG_ROOT_USER_DN.toLowerCase(), OPTION_SHORT_ROOT_USER_DN,
                OPTION_LONG_ROOT_USER_DN, false, false, true, INFO_ROOT_USER_DN_PLACEHOLDER.get(),
                "cn=Directory Manager", OPTION_LONG_ROOT_USER_DN, INFO_ARGUMENT_DESCRIPTION_ROOTDN.get());
    }

    /**
     * Returns the "directory manager DN password" string argument.
     *
     * @return The "rootUserPassword" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getRootDNPwd() throws ArgumentException {
        return new StringArgument("rootUserPassword".toLowerCase(), OPTION_SHORT_BINDPWD, "rootUserPassword", false,
                false, true, INFO_ROOT_USER_PWD_PLACEHOLDER.get(), null, "rootUserPassword",
                INFO_ROOT_USER_PWD_PLACEHOLDER.get());
    }

    /**
     * Returns the "directory manager DN password file" file argument.
     *
     * @return The "rootUserPasswordFile" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static FileBasedArgument getRootDNPwdFile() throws ArgumentException {
        return new FileBasedArgument("rootUserPasswordFile".toLowerCase(), OPTION_SHORT_BINDPWD_FILE,
                "rootUserPasswordFile", false, false, INFO_ROOT_USER_PWD_FILE_PLACEHOLDER.get(), null,
                "rootUserPasswordFile", INFO_ARGUMENT_DESCRIPTION_ROOTPWFILE.get());
    }

    /**
     * Returns the "enable window service" integer argument.
     *
     * @return The "enableWindowsService" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getEnableWindowsService() throws ArgumentException {
        final BooleanArgument enableWindowsServiceArg = new BooleanArgument("enableWindowsService".toLowerCase(), 'e',
                "enableWindowsService", INFO_ARGUMENT_DESCRIPTION_ENABLE_WINDOWS_SERVICE.get());
        enableWindowsServiceArg.setPropertyName("enableWindowsService");
        return enableWindowsServiceArg;
    }

    /**
     * Returns the "do not start" boolean argument.
     *
     * @return The "doNotStart" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getDoNotStart() throws ArgumentException {
        final BooleanArgument doNotStartArg = new BooleanArgument("doNotStart".toLowerCase(), 'O', "doNotStart",
                INFO_SETUP_DESCRIPTION_DO_NOT_START.get());
        doNotStartArg.setPropertyName("doNotStart");
        return doNotStartArg;
    }

    /**
     * Returns the "displayCommand" boolean argument.
     *
     * @return The "displayCommand" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getDisplayEquivalentCommand() throws ArgumentException {
        return new BooleanArgument(OPTION_LONG_DISPLAY_EQUIVALENT, null, OPTION_LONG_DISPLAY_EQUIVALENT,
                INFO_DESCRIPTION_DISPLAY_EQUIVALENT.get());
    }

    /**
     * Returns the "commandFilePath" string argument.
     *
     * @param description
     *            The description of this argument.
     * @return The "commandFilePath" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getEquivalentCommandFile(final LocalizableMessage description)
            throws ArgumentException {
        return new StringArgument(OPTION_LONG_EQUIVALENT_COMMAND_FILE_PATH, null,
                OPTION_LONG_EQUIVALENT_COMMAND_FILE_PATH, false, false, true, INFO_PATH_PLACEHOLDER.get(), null, null,
                description);
    }

    /**
     * Returns the "enable start TLS" boolean argument.
     *
     * @return The "enableStartTLS" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getEnableTLS() throws ArgumentException {
        final BooleanArgument enableStartTLS = new BooleanArgument("enableStartTLS".toLowerCase(),
                OPTION_SHORT_START_TLS, "enableStartTLS", INFO_SETUP_DESCRIPTION_ENABLE_STARTTLS.get());
        enableStartTLS.setPropertyName("enableStartTLS");
        return enableStartTLS;
    }

    /**
     * Returns the "ldaps port" integer argument.
     *
     * @param defaultSecurePort
     *            Default value for the LDAPS port.
     * @return The "ldapsPort" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static IntegerArgument getLDAPSPort(final int defaultSecurePort) throws ArgumentException {
        return new IntegerArgument("ldapsPort".toLowerCase(), OPTION_SHORT_USE_SSL, "ldapsPort", false, false, true,
                INFO_PORT_PLACEHOLDER.get(), defaultSecurePort, "ldapsPort", true, 1, true, 65535,
                INFO_ARGUMENT_DESCRIPTION_LDAPSPORT.get());
    }

    /**
     * Returns the "generate self certificate" boolean argument.
     *
     * @return The "generateSelfSignedCertificate" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getGenerateSelfSigned() throws ArgumentException {
        final BooleanArgument generateSelfSigned = new BooleanArgument("generateSelfSignedCertificate".toLowerCase(),
                null, "generateSelfSignedCertificate", INFO_ARGUMENT_DESCRIPTION_USE_SELF_SIGNED_CERTIFICATE.get());
        generateSelfSigned.setPropertyName("generateSelfSignedCertificate");
        return generateSelfSigned;
    }

    /**
     * Returns the "host name" string argument.
     *
     * @param defaultHostName
     *            The default host name value.
     * @return The "hostname" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getHostName(final String defaultHostName) throws ArgumentException {
        final StringArgument hostName = new StringArgument(OPTION_LONG_HOST.toLowerCase(), OPTION_SHORT_HOST,
                OPTION_LONG_HOST, false, false, true, INFO_HOST_PLACEHOLDER.get(), defaultHostName, null,
                INFO_ARGUMENT_DESCRIPTION_HOST_NAME.get());
        hostName.setPropertyName(OPTION_LONG_HOST);
        return hostName;
    }

    /**
     * Returns the "use PKCS11 key store" boolean argument.
     *
     * @return The "usePkcs11Keystore" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getUsePKCS11Keystore() throws ArgumentException {
        final BooleanArgument usePkcs11 = new BooleanArgument("usePkcs11Keystore".toLowerCase(), null,
                "usePkcs11Keystore", INFO_ARGUMENT_DESCRIPTION_USE_PKCS11.get());
        usePkcs11.setPropertyName("usePkcs11Keystore");
        return usePkcs11;
    }

    /**
     * Returns the "use java key store" string argument.
     *
     * @return The "useJavaKeystore" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getUseJavaKeyStore() throws ArgumentException {
        return new StringArgument("useJavaKeystore".toLowerCase(), null, "useJavaKeystore", false, false, true,
                INFO_KEYSTOREPATH_PLACEHOLDER.get(), null, "useJavaKeystore",
                INFO_ARGUMENT_DESCRIPTION_USE_JAVAKEYSTORE.get());
    }

    /**
     * Returns the "use JCEKS" string argument.
     *
     * @return The "useJCEKS" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getUseJCEKS() throws ArgumentException {
        return new StringArgument("useJCEKS".toLowerCase(), null, "useJCEKS", false, false, true,
                INFO_KEYSTOREPATH_PLACEHOLDER.get(), null, "useJCEKS", INFO_ARGUMENT_DESCRIPTION_USE_JCEKS.get());
    }

    /**
     * Returns the "use PKCS12 key store" string argument.
     *
     * @return The "usePkcs12keyStore" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getUsePKCS12KeyStore() throws ArgumentException {
        return new StringArgument("usePkcs12keyStore".toLowerCase(), null, "usePkcs12keyStore", false, false, true,
                INFO_KEYSTOREPATH_PLACEHOLDER.get(), null, "usePkcs12keyStore",
                INFO_ARGUMENT_DESCRIPTION_USE_PKCS12.get());
    }

    /**
     * Returns the "useSSL" boolean argument.
     *
     * @return The "useSSL" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getUseSSL() throws ArgumentException {
        final BooleanArgument useSSL = new BooleanArgument("useSSL", OPTION_SHORT_USE_SSL, OPTION_LONG_USE_SSL,
                INFO_DESCRIPTION_USE_SSL.get());
        useSSL.setPropertyName(OPTION_LONG_USE_SSL);
        return useSSL;
    }


    /**
     * Returns the "key store password" string argument.
     *
     * @return The "keyStorePassword" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getKeyStorePassword() throws ArgumentException {
        return new StringArgument(OPTION_LONG_KEYSTORE_PWD.toLowerCase(), OPTION_SHORT_KEYSTORE_PWD,
                OPTION_LONG_KEYSTORE_PWD, false, false, true, INFO_KEYSTORE_PWD_PLACEHOLDER.get(), null,
                OPTION_LONG_KEYSTORE_PWD, INFO_ARGUMENT_DESCRIPTION_KEYSTOREPASSWORD.get());
    }

    /**
     * Returns the "key store password file" file argument.
     *
     * @return The "keyStorePassword" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static FileBasedArgument getKeyStorePasswordFile() throws ArgumentException {
        return new FileBasedArgument(OPTION_LONG_KEYSTORE_PWD_FILE.toLowerCase(), OPTION_SHORT_KEYSTORE_PWD_FILE,
                OPTION_LONG_KEYSTORE_PWD_FILE, false, false, INFO_KEYSTORE_PWD_FILE_PLACEHOLDER.get(), null,
                OPTION_LONG_KEYSTORE_PWD_FILE, INFO_ARGUMENT_DESCRIPTION_KEYSTOREPASSWORD_FILE.get());
    }

    /**
     * Returns the "keyStorePath" string argument.
     *
     * @return The "keyStorePath" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getKeyStorePath() throws ArgumentException {
        final StringArgument ksPath = new StringArgument("keyStorePath", OPTION_SHORT_KEYSTOREPATH,
                OPTION_LONG_KEYSTOREPATH, false, false, true, INFO_KEYSTOREPATH_PLACEHOLDER.get(), null, null,
                INFO_DESCRIPTION_KEYSTOREPATH.get());
        ksPath.setPropertyName(OPTION_LONG_KEYSTOREPATH);
        return ksPath;
    }

    /**
     * Returns the "key store password file" string argument.
     *
     * @return The "keyStorePassword" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getCertNickName() throws ArgumentException {
        return new StringArgument(OPTION_LONG_CERT_NICKNAME.toLowerCase(), OPTION_SHORT_CERT_NICKNAME,
                OPTION_LONG_CERT_NICKNAME, false, false, true, INFO_NICKNAME_PLACEHOLDER.get(), null,
                OPTION_LONG_CERT_NICKNAME, INFO_ARGUMENT_DESCRIPTION_CERT_NICKNAME.get());
    }

}
