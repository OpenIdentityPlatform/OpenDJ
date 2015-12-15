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
 *      Copyright 2014-2015 ForgeRock AS.
 */
package com.forgerock.opendj.cli;

import static com.forgerock.opendj.cli.CliMessages.*;
import static com.forgerock.opendj.cli.ArgumentConstants.*;
import static com.forgerock.opendj.cli.CliConstants.DEFAULT_LDAP_CONNECT_TIMEOUT;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.SearchScope;

/**
 * This class regroup commons arguments used by the different CLI.
 */
public final class CommonArguments {

    /** Prevent instantiation. */
    private CommonArguments() {
        // Nothing to do.
    }

    /**
     * Returns the "show usage / help" boolean argument.
     *
     * @return The "show usage" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getShowUsage() throws ArgumentException {
        return new BooleanArgument(OPTION_LONG_HELP.toLowerCase(), OPTION_SHORT_HELP, OPTION_LONG_HELP,
                INFO_DESCRIPTION_SHOWUSAGE.get());
    }

    /**
     * Returns the "verbose" boolean argument.
     *
     * @return The "verbose" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getVerbose() throws ArgumentException {
        final BooleanArgument verbose = new BooleanArgument(OPTION_LONG_VERBOSE.toLowerCase(), OPTION_SHORT_VERBOSE,
                OPTION_LONG_VERBOSE, INFO_DESCRIPTION_VERBOSE.get());
        verbose.setPropertyName("verbose");
        return verbose;
    }

    /**
     * Returns the "port" integer argument.
     *
     * @param defaultPort
     *            The default port number.
     * @return The "port" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static IntegerArgument getPort(final int defaultPort) throws ArgumentException {
        return getPort(defaultPort, null);
    }

    /**
     * Returns the "port" integer argument. <br>
     * <i> N.B : the 'p' short option is also used by skipdecode(DBTest),
     * propertiesFile(JavaPropertiesToolArguments).</i>
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
        return new IntegerArgument(OPTION_LONG_PORT.toLowerCase(), OPTION_SHORT_PORT, OPTION_LONG_PORT, false, false,
                true, INFO_PORT_PLACEHOLDER.get(), defaultPort, OPTION_LONG_PORT, true, 1, true, 65535,
                description != null ? description : INFO_DESCRIPTION_ADMIN_PORT.get());
    }

    /**
     * Returns the "postreadattrs" string argument.
     *
     * @return The "postreadattrs" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getPostReadAttributes() throws ArgumentException {
        return new StringArgument("postreadattrs", null, "postReadAttributes", false, false, true,
                INFO_ATTRIBUTE_LIST_PLACEHOLDER.get(), null, "postReadAttributes",
                INFO_DESCRIPTION_POSTREAD_ATTRS.get());
    }

    /**
     * Returns the "prereadattrs" string argument.
     *
     * @return The "prereadattrs" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getPreReadAttributes() throws ArgumentException {
        return new StringArgument("prereadattrs", null, "preReadAttributes", false, false, true,
                INFO_ATTRIBUTE_LIST_PLACEHOLDER.get(), null, "preReadAttributes", INFO_DESCRIPTION_PREREAD_ATTRS.get());
    }

    /**
     * Returns the "propertiesFilePath" string argument.
     *
     * @return The "propertiesFilePath" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getPropertiesFile() throws ArgumentException {
        return new StringArgument(OPTION_LONG_PROP_FILE_PATH.toLowerCase(), null, OPTION_LONG_PROP_FILE_PATH, false,
                false, true, INFO_PROP_FILE_PATH_PLACEHOLDER.get(), null, null, INFO_DESCRIPTION_PROP_FILE_PATH.get());
    }

    /**
     * Returns the "proxyauthzid" string argument.
     *
     * @return The "proxyauthzid" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getProxyAuthId() throws ArgumentException {
        return new StringArgument("proxyauthzid", OPTION_SHORT_PROXYAUTHID, OPTION_LONG_PROXYAUTHID, false, false,
                true, INFO_PROXYAUTHID_PLACEHOLDER.get(), null, OPTION_LONG_PROXYAUTHID,
                INFO_DESCRIPTION_PROXYAUTHZID.get());
    }

    /**
     * Returns the "No properties file" boolean argument.
     *
     * @return The "noPropertiesFile" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getNoPropertiesFile() throws ArgumentException {
        return new BooleanArgument(OPTION_LONG_NO_PROP_FILE.toLowerCase(), null, OPTION_LONG_NO_PROP_FILE,
                INFO_DESCRIPTION_NO_PROP_FILE.get());
    }

    /**
     * Returns the "Continue On Error" boolean argument. <br>
     * <i> N.B : the 'c' short option is also used by cleanupservice, compress.</i>
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
     * Returns the "control" string argument.
     *
     * @return The "control" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getControl() throws ArgumentException {
        StringArgument controlStr =
                new StringArgument(OPTION_LONG_CONTROL.toLowerCase(), OPTION_SHORT_CONTROL, OPTION_LONG_CONTROL, false,
                true, true, INFO_LDAP_CONTROL_PLACEHOLDER.get(), null, OPTION_LONG_CONTROL,
                INFO_DESCRIPTION_CONTROLS.get());
        controlStr.setDocDescriptionSupplement(SUPPLEMENT_DESCRIPTION_CONTROLS.get());
        return controlStr;
    }

    /**
     * Returns the "ldapVersion" integer argument.
     *
     * @return The "ldapVersion" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static IntegerArgument getLdapVersion() throws ArgumentException {
        return new IntegerArgument(OPTION_LONG_PROTOCOL_VERSION.toLowerCase(), OPTION_SHORT_PROTOCOL_VERSION,
                OPTION_LONG_PROTOCOL_VERSION, false, false, true, INFO_PROTOCOL_VERSION_PLACEHOLDER.get(), 3,
                OPTION_LONG_PROTOCOL_VERSION, INFO_DESCRIPTION_VERSION.get());
    }

    /**
     * Returns the "windowsnetstop" boolean argument.
     *
     * @return The "windowsnetstop" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getWindowsNetStop() throws ArgumentException {
        final BooleanArgument netStop = new BooleanArgument("windowsnetstop", null, "windowsNetStop",
                INFO_DESCRIPTION_WINDOWS_NET_STOP.get());
        netStop.setHidden(true);
        return netStop;
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
     * Returns the "no-op" boolean argument. <br>
     * <i> N.B : the 'n' short option is also used by backendid, newGroupName, newPassword, no-prompt.</i>
     *
     * @return The "no-op" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getNoOp() throws ArgumentException {
        return new BooleanArgument("no-op", OPTION_SHORT_DRYRUN, OPTION_LONG_DRYRUN, INFO_DESCRIPTION_NOOP.get());
    }

    /**
     * Returns the "no-prompt" boolean argument. <br>
     * <i> N.B : the 'n' short option is also used by backendid, newGroupName, newPassword, no-prompt.</i>
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
     * Returns the "targetldif" string argument.
     * <br><i> N.B : the 't' short option is also used by timelimit,
     * testonly, trustmanagerproviderdn, stoptime, start(dateTime).</i>
     *
     * @param description
     *            The description of this argument.
     * @return The "targetldif" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getTargetLDIF(final LocalizableMessage description) throws ArgumentException {
        return new StringArgument("targetldif", 't', "targetLDIF", true, false, true, INFO_LDIFFILE_PLACEHOLDER.get(),
                null, null, description);
    }

    /**
     * Returns the "timelimit" boolean argument. <br>
     * <i> N.B : the 't' short option is also used by targetldif, testonly, trustmanagerproviderdn, stoptime,
     * start(dateTime).</i>
     *
     * @return The "timelimit" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static IntegerArgument getTimeLimit() throws ArgumentException {
        return new IntegerArgument("timelimit", 't', "timeLimit", false, false, true,
                INFO_TIME_LIMIT_PLACEHOLDER.get(), 0, null, true, 0, false, 0, INFO_DESCRIPTION_TIME_LIMIT.get());
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
     * Returns the "trustmanagerproviderdn" string argument.
     * <br><i> N.B : the 't' short option is also used by targetldif, timelimit,
     * testonly, stoptime, start(dateTime)</i>
     *
     * @return The "trustmanagerproviderdn" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getTrustManagerProviderDN() throws ArgumentException {
        return new StringArgument("trustmanagerproviderdn", 't', "trustManagerProviderDN", false, false, true,
                INFO_TRUST_MANAGER_PROVIDER_DN_PLACEHOLDER.get(), null, null,
                INFO_DESCRIPTION_TRUSTMANAGER_PROVIDER_DN.get());
    }

    /**
     * Returns the "trustStorePath" string argument.
     *
     * @return The "trustStorePath" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getTrustStorePath() throws ArgumentException {
        return new StringArgument("trustStorePath", OPTION_SHORT_TRUSTSTOREPATH, OPTION_LONG_TRUSTSTOREPATH, false,
                false, true, INFO_TRUSTSTOREPATH_PLACEHOLDER.get(), null, OPTION_LONG_TRUSTSTOREPATH,
                INFO_DESCRIPTION_TRUSTSTOREPATH.get());
    }

    /**
     * Returns the "typesOnly" boolean argument.
     *
     * @return The "typesOnly" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getTypesOnly() throws ArgumentException {
        final BooleanArgument typesOnly = new BooleanArgument("typesOnly", 'A', "typesOnly",
                INFO_DESCRIPTION_TYPES_ONLY.get());
        typesOnly.setPropertyName("typesOnly");
        return typesOnly;
    }

    /**
     * Returns the "truststorepw" string argument.
     *
     * @return The "truststorepw" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getTrustStorePassword() throws ArgumentException {
        return new StringArgument("truststorepw", OPTION_SHORT_TRUSTSTORE_PWD, OPTION_LONG_TRUSTSTORE_PWD, false,
                false, true, INFO_TRUSTSTORE_PWD_PLACEHOLDER.get(), null, OPTION_LONG_TRUSTSTORE_PWD,
                INFO_DESCRIPTION_TRUSTSTOREPASSWORD.get());
    }

    /**
     * Returns the "trustStorePasswordFile" file argument.
     *
     * @return The "trustStorePasswordFile" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static FileBasedArgument getTrustStorePasswordFile() throws ArgumentException {
        return new FileBasedArgument("trustStorePasswordFile", OPTION_SHORT_TRUSTSTORE_PWD_FILE,
                OPTION_LONG_TRUSTSTORE_PWD_FILE, false, false, INFO_TRUSTSTORE_PWD_FILE_PLACEHOLDER.get(), null,
                OPTION_LONG_TRUSTSTORE_PWD_FILE, INFO_DESCRIPTION_TRUSTSTOREPASSWORD_FILE.get());
    }

    /**
     * Returns the "connectTimeout" integer argument.
     *
     * @return The "connectTimeout" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static IntegerArgument getConnectTimeOut() throws ArgumentException {
        final IntegerArgument connectTimeout = new IntegerArgument(OPTION_LONG_CONNECT_TIMEOUT, null,
                OPTION_LONG_CONNECT_TIMEOUT, false, false, true, INFO_TIMEOUT_PLACEHOLDER.get(),
                DEFAULT_LDAP_CONNECT_TIMEOUT, null, true, 0, false, Integer.MAX_VALUE,
                INFO_DESCRIPTION_CONNECTION_TIMEOUT.get());
        connectTimeout.setPropertyName(OPTION_LONG_CONNECT_TIMEOUT);
        connectTimeout.setHidden(true);
        return connectTimeout;
    }

    /**
     * Returns the "cleanupservice" string argument. <br>
     * <i> N.B : the 'c' short option is also used by continueOnError, compress.</i>
     *
     * @return The "cleanupservice" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getCleanupService() throws ArgumentException {
        return new StringArgument("cleanupservice", 'c', "cleanupService", false, false, true,
                INFO_SERVICE_NAME_PLACEHOLDER.get(), null, null,
                INFO_CONFIGURE_WINDOWS_SERVICE_DESCRIPTION_CLEANUP.get());
    }

    /**
     * Returns the "CLI" boolean argument. <br>
     * <i> N.B : the 'i' short option is also used by encoding.</i>
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
     * Returns the "checkstoppability" boolean argument.
     *
     * @return The "checkstoppability" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getCheckStoppability() throws ArgumentException {
        final BooleanArgument cs = new BooleanArgument("checkstoppability", null, "checkStoppability",
                INFO_CHECK_STOPPABILITY.get());
        cs.setHidden(true);
        return cs;
    }

    /**
     * Returns the "configfile" string argument. <br>
     * <i> N.B : the 'f' short option is also used by filename</i>
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
     * Returns the "backendid" string argument. <br>
     * <i> N.B : the 'n' short option is also used by newGroupName, no-prompt.</i>
     *
     * @return The "backendid" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getBackendId() throws ArgumentException {
        return new StringArgument("backendid", 'n', "backendID", false, true, true, INFO_BACKENDNAME_PLACEHOLDER.get(),
                null, null, INFO_BACKUPDB_DESCRIPTION_BACKEND_ID.get());
    }

    /**
     * Returns the "backupdirectory" string argument. <br>
     * <i> N.B : the 'd' short option is also used by sampledata, disableservice.</i>
     *
     * @return The "backupdirectory" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getBackupDirectory() throws ArgumentException {
        return new StringArgument("backupdirectory", 'd', "backupDirectory", true, false, true,
                INFO_BACKUPDIR_PLACEHOLDER.get(), null, null, INFO_DESCRIPTION_BACKUP_DIR.get());
    }

    /**
     * Returns the "backupID" string argument.
     *
     * @return The "backupID" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getBackupId() throws ArgumentException {
        return new StringArgument("backupid", 'I', "backupID", false, false, true,
                INFO_BACKUPID_PLACEHOLDER.get(), null, null, INFO_DESCRIPTION_BACKUP_ID.get());
    }

    /**
     * Returns the "backupall" boolean argument. <br><i> N.B : the 'a' short option is also used by addbaseentry,
     * defaultAdd.</i>
     *
     * @return The "backupall" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getBackupAll() throws ArgumentException {
        return new BooleanArgument("backupall", 'a', "backUpAll", INFO_DESCRIPTION_BACKUP_ALL.get());
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
     * Returns the "bindDN" string argument. <br/>
     * <i> N.B : the 'D' short option is also used by rootUserDN.</i>
     *
     * @param defaultBindDN
     *            The default bind DN.
     * @return The "bindDN" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getBindDN(final String defaultBindDN) throws ArgumentException {
        return new StringArgument("bindDN", OPTION_SHORT_BINDDN, OPTION_LONG_BINDDN, false, false, true,
                INFO_BINDDN_PLACEHOLDER.get(), defaultBindDN, OPTION_LONG_BINDDN, INFO_DESCRIPTION_BINDDN.get());
    }

    /**
     * Returns the "bindPassword" string argument.
     *
     * @return The "bindPassword" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getBindPassword() throws ArgumentException {
        return new StringArgument("bindPassword", OPTION_SHORT_BINDPWD, OPTION_LONG_BINDPWD, false, false, true,
                INFO_BINDPWD_PLACEHOLDER.get(), null, OPTION_LONG_BINDPWD, INFO_DESCRIPTION_BINDPASSWORD.get());
    }

    /**
     * Returns the "bindPasswordFile" file argument.
     *
     * @return The "bindPasswordFile" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static FileBasedArgument getBindPasswordFile() throws ArgumentException {
        return new FileBasedArgument("bindPasswordFile", OPTION_SHORT_BINDPWD_FILE, OPTION_LONG_BINDPWD_FILE, false,
                false, INFO_BINDPWD_FILE_PLACEHOLDER.get(), null, OPTION_LONG_BINDPWD_FILE,
                INFO_DESCRIPTION_BINDPASSWORDFILE.get());
    }

    /**
     * Returns the "addbaseentry" boolean argument.
     * <br><i> N.B : the 'a' short option is also used by backupall, defaultAdd.</i>
     *
     * @return The "addbaseentry" argument.
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
     * Returns the "rejectfile" string argument. <br>
     * <i> N.B : the 'R' short option is also used by restart, serverRoot.</i>
     *
     * @return The "rejectfile" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getRejectedImportLdif() throws ArgumentException {
        return new StringArgument("rejectFile".toLowerCase(), 'R', "rejectFile", false, false, true,
                INFO_REJECT_FILE_PLACEHOLDER.get(), null, "rejectFile", INFO_GENERAL_DESCRIPTION_REJECTED_FILE.get());
    }

    /**
     * Returns the "remote" boolean argument. <br>
     * <i> N.B : the 'r' short option is also used by useSASLExternal, stopreason.</i>
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
     * Returns the "reportauthzid" boolean argument.
     *
     * @return The "reportauthzid" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getReportAuthzId() throws ArgumentException {
        final BooleanArgument report = new BooleanArgument(OPTION_LONG_REPORT_AUTHZ_ID.toLowerCase(), 'E',
                OPTION_LONG_REPORT_AUTHZ_ID, INFO_DESCRIPTION_REPORT_AUTHZID.get());
        report.setPropertyName("reportAuthzID");
        return report;
    }

    /**
     * Returns the "restart" boolean argument. <br>
     * <i> N.B : the 'R' short option is also used by rejectfile, serverRoot.</i>
     *
     * @return The "restart" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getRestart() throws ArgumentException {
        final BooleanArgument restart = new BooleanArgument(OPTION_LONG_RESTART.toLowerCase(), 'R',
                OPTION_LONG_RESTART, INFO_DESCRIPTION_RESTART.get());
        restart.setPropertyName(OPTION_LONG_RESTART);
        return restart;
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
     * Returns the "sample data" integer argument. <br>
     * <i> N.B : the 'd' short option is also used by backupdirectory, disableservice.</i>
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
     * Returns the "sasloption" string argument. <br>
     * <i> N.B : the 'o' short option is also used by outputLDIF.</i>
     *
     * @return The "sasloption" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getSASL() throws ArgumentException {
        return new StringArgument("sasloption", OPTION_SHORT_SASLOPTION, OPTION_LONG_SASLOPTION, false, true, true,
                INFO_SASL_OPTION_PLACEHOLDER.get(), null, OPTION_LONG_SASLOPTION,
                INFO_LDAP_CONN_DESCRIPTION_SASLOPTIONS.get());
    }

    /**
     * Returns the "searchScope" string argument.<br>
     * <i> N.B : the 's' short option is also used by servicestate, sourceldif, randomSeed, script-friendly.</i>
     *
     * @return The "searchScope" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static  MultiChoiceArgument<SearchScope> getSearchScope() throws ArgumentException {
        final MultiChoiceArgument<SearchScope> searchScope = new MultiChoiceArgument<>(
                OPTION_LONG_SEARCHSCOPE, OPTION_SHORT_SEARCHSCOPE, OPTION_LONG_SEARCHSCOPE, false, true,
                INFO_SEARCH_SCOPE_PLACEHOLDER.get(), SearchScope.values(), false,
                INFO_SEARCH_DESCRIPTION_SEARCH_SCOPE.get());
        searchScope.setPropertyName(OPTION_LONG_SEARCHSCOPE);
        searchScope.setDefaultValue(SearchScope.WHOLE_SUBTREE);
        return searchScope;
    }

    /**
     * Returns the "serverRoot" string argument. <br>
     * <i> N.B : the 'R' short option is also used by rejectfile, restart.</i>
     *
     * @return The "serverRoot" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getServerRoot() throws ArgumentException {
        final StringArgument serverRoot = new StringArgument("serverRoot", OPTION_SHORT_SERVER_ROOT,
                OPTION_LONG_SERVER_ROOT, false, false, true, INFO_SERVER_ROOT_DIR_PLACEHOLDER.get(), null, null, null);
        serverRoot.setHidden(true);
        return serverRoot;
    }

    /**
     * Returns the "servicestate" boolean argument. <br>
     * <i> N.B : the 's' short option is also used by searchScope, sourceldif, randomSeed, script-friendly.</i>
     *
     * @return The "servicestate" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getServiceState() throws ArgumentException {
        return new BooleanArgument("servicestate", 's', "serviceState",
                INFO_CONFIGURE_WINDOWS_SERVICE_DESCRIPTION_STATE.get());
    }

    /**
     * Returns the "script-friendly" boolean argument.<br>
     * <i> N.B : the 's' short option is also used by searchScope, servicestate, sourceldif, randomSeed.</i>
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
     * Returns the "assertionfilter" string argument.
     *
     * @return The "assertionfilter" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getAssertionFilter() throws ArgumentException {
        return new StringArgument("assertionfilter", null, OPTION_LONG_ASSERTION_FILE, false, false, true,
                INFO_ASSERTION_FILTER_PLACEHOLDER.get(), null, OPTION_LONG_ASSERTION_FILE,
                INFO_DESCRIPTION_ASSERTION_FILTER.get());
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
     * Returns the "sourceldif" string argument. <br>
     * <i> N.B : the 's' short option is also used by searchScope, servicestate, randomSeed, script-friendly.</i>
     *
     * @param description
     *            The description of this argument.
     * @return The "sourceldif" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getSourceLDIF(final LocalizableMessage description) throws ArgumentException {
        return new StringArgument("sourceldif", 's', "sourceLDIF", true, false, true, INFO_LDIFFILE_PLACEHOLDER.get(),
                null, null, description);
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
     * Returns the "stopreason" string argument. <br>
     * <i> N.B : the 'r' short option is also used by useSASLExternal, remote.</i>
     *
     * @return The "stopreason" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getStopReason() throws ArgumentException {
        return new StringArgument("stopreason", 'r', "stopReason", false, false, true,
                INFO_STOP_REASON_PLACEHOLDER.get(), null, "stopReason", INFO_DESCRIPTION_STOP_REASON.get());
    }

    /**
     * Returns the "stopTime" string argument. <br><i> N.B : the 't' short option is also used by targetldif, timelimit,
     * testonly, trustmanagerproviderdn, start(dateTime)</i>
     *
     * @return The "stopTime" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getStopTime() throws ArgumentException {
        return new StringArgument("stoptime", 't', "stopTime", false, false, true, INFO_STOP_TIME_PLACEHOLDER.get(),
                null, "stopTime", INFO_DESCRIPTION_STOP_TIME.get());
    }

    /**
     * Returns the "rootUserDN" string argument. <br>
     * <i> N.B : the 'D' short option is also used by bindDN.</i>
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
     * Returns the "encoding" string argument. <br>
     * <i> N.B : the 'i' short option is also used by cli</i>
     *
     * @return The "encoding" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getEncoding() throws ArgumentException {
        return new StringArgument("encoding", 'i', "encoding", false, false, true, INFO_ENCODING_PLACEHOLDER.get(),
                null, "encoding", INFO_DESCRIPTION_ENCODING.get());
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
     * Returns the "defaultAdd" boolean argument.
     * <br><i> N.B : the 'a' short option is also used by addbaseentry, defaultAdd.</i>
     *
     * @return The "defaultAdd" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getDefaultAdd() throws ArgumentException {
        return new BooleanArgument("defaultAdd", 'a', "defaultAdd", INFO_MODIFY_DESCRIPTION_DEFAULT_ADD.get());
    }

    /**
     * Returns the "disableservice" boolean argument. <br>
     * <i> N.B : the 'd' short option is also used by backupdirectory, sampledata.</i>
     *
     * @return The "disableservice" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getDisableService() throws ArgumentException {
        return new BooleanArgument("disableservice", 'd', "disableService",
                INFO_CONFIGURE_WINDOWS_SERVICE_DESCRIPTION_DISABLE.get());
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
     * Returns the "filename" string argument.
     * <i> N.B : the 'f' short option is also used by configfile</i>
     * @param description
     *            The description of this argument.
     * @return The "filename" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getFilename(final LocalizableMessage description) throws ArgumentException {
        return new StringArgument("filename", OPTION_SHORT_FILENAME, OPTION_LONG_FILENAME, false, false, true,
                INFO_FILE_PLACEHOLDER.get(), null, OPTION_LONG_FILENAME, description);
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
     * Returns the "ldaps port" integer argument. <br>
     * <i> N.B : the 'Z' short option is also used by useSSL.</i>
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
     * Returns the "ldiffile" string argument.
     *
     * @param description
     *            The description of this argument.
     * @return The "ldapsPort" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getLDIFFile(final LocalizableMessage description) throws ArgumentException {
        return new StringArgument(OPTION_LONG_LDIF_FILE.toLowerCase(), OPTION_SHORT_LDIF_FILE, OPTION_LONG_LDIF_FILE,
                false, true, true, INFO_LDIFFILE_PLACEHOLDER.get(), null, null, description);
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
     * Returns the "hostname" string argument.
     *
     * @param defaultHostName
     *            The default host name value.
     * @return The "hostname" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getHostName(final String defaultHostName) throws ArgumentException {
        return getHostName(defaultHostName, null);
    }

    /**
     * Returns the "hostname" string argument.
     *
     * @param defaultHostName
     *            The default host name value.
     * @param description
     *            The custom description.
     * @return The "hostname" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getHostName(final String defaultHostName, final LocalizableMessage description)
            throws ArgumentException {
        return new StringArgument(OPTION_LONG_HOST.toLowerCase(), OPTION_SHORT_HOST, OPTION_LONG_HOST, false, false,
                true, INFO_HOST_PLACEHOLDER.get(), defaultHostName, OPTION_LONG_HOST, description != null ? description
                        : INFO_ARGUMENT_DESCRIPTION_HOST_NAME.get());
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
     * Returns the "useSASLExternal" boolean argument. <br>
     * <i> N.B : the 'r' short option is also used by stopreason, remote.</i>
     *
     * @return The "useSASLExternal" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getUseSASLExternal() throws ArgumentException {
        final BooleanArgument useSASLExternal = new BooleanArgument("useSASLExternal", 'r', "useSASLExternal",
                INFO_DESCRIPTION_USE_SASL_EXTERNAL.get());
        useSASLExternal.setPropertyName("useSASLExternal");
        return useSASLExternal;
    }

    /**
     * Returns the "useSSL" boolean argument. <br>
     * <i> N.B : the 'Z' short option is also used by ldapsport.</i>
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
     * Returns the "keymanagerpath" string argument.
     *
     * @return The "keymanagerpath" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getKeyManagerPath() throws ArgumentException {
        return new StringArgument("keymanagerpath", 'm', "keyManagerPath", false, false, true,
                INFO_KEY_MANAGER_PATH_PLACEHOLDER.get(), null, null, INFO_DESCRIPTION_KEYMANAGER_PATH.get());
    }

    /**
     * Returns the "keymanagerproviderdn" string argument.
     *
     * @return The "keymanagerproviderdn" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getKeyManagerProviderDN() throws ArgumentException {
        return new StringArgument("keymanagerproviderdn", 'k', "keyManagerProviderDN", false, false, true,
                INFO_KEY_MANAGER_PROVIDER_DN_PLACEHOLDER.get(), null, null,
                INFO_DESCRIPTION_KEYMANAGER_PROVIDER_DN.get());
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
                OPTION_LONG_CERT_NICKNAME, false, true, true, INFO_NICKNAME_PLACEHOLDER.get(), null,
                OPTION_LONG_CERT_NICKNAME, INFO_ARGUMENT_DESCRIPTION_CERT_NICKNAME.get());
    }

}
