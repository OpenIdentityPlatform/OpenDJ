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
 *      Copyright 2014-2016 ForgeRock AS.
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
        return BooleanArgument.builder(OPTION_LONG_HELP)
                .shortIdentifier(OPTION_SHORT_HELP)
                .description(INFO_DESCRIPTION_SHOWUSAGE.get())
                .buildArgument();
    }

    /**
     * Returns the "verbose" boolean argument.
     *
     * @return The "verbose" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getVerbose() throws ArgumentException {
        return BooleanArgument.builder(OPTION_LONG_VERBOSE)
                .shortIdentifier(OPTION_SHORT_VERBOSE)
                .description(INFO_DESCRIPTION_VERBOSE.get())
                .buildArgument();
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
        return IntegerArgument.builder(OPTION_LONG_PORT)
                .shortIdentifier(OPTION_SHORT_PORT)
                .description(description != null ? description : INFO_DESCRIPTION_ADMIN_PORT.get())
                .range(1, 65535)
                .defaultValue(defaultPort)
                .valuePlaceholder(INFO_PORT_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "propertiesFilePath" string argument.
     *
     * @return The "propertiesFilePath" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getPropertiesFile() throws ArgumentException {
        return StringArgument.builder(OPTION_LONG_PROP_FILE_PATH)
                .description(INFO_DESCRIPTION_PROP_FILE_PATH.get())
                .valuePlaceholder(INFO_PROP_FILE_PATH_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "proxyauthzid" string argument.
     *
     * @return The "proxyauthzid" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getProxyAuthId() throws ArgumentException {
        return StringArgument.builder(OPTION_LONG_PROXYAUTHID)
                .shortIdentifier(OPTION_SHORT_PROXYAUTHID)
                .description(INFO_DESCRIPTION_PROXYAUTHZID.get())
                .valuePlaceholder(INFO_PROXYAUTHID_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "No properties file" boolean argument.
     *
     * @return The "noPropertiesFile" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getNoPropertiesFile() throws ArgumentException {
        return BooleanArgument.builder(OPTION_LONG_NO_PROP_FILE)
                .description(INFO_DESCRIPTION_NO_PROP_FILE.get())
                .buildArgument();
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
        return BooleanArgument.builder("continueOnError")
                .shortIdentifier('c')
                .description(INFO_DESCRIPTION_CONTINUE_ON_ERROR.get())
                .buildArgument();
    }

    /**
     * Returns the "control" string argument.
     *
     * @return The "control" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getControl() throws ArgumentException {
        return StringArgument.builder(OPTION_LONG_CONTROL)
                .shortIdentifier(OPTION_SHORT_CONTROL)
                .description(INFO_DESCRIPTION_CONTROLS.get())
                .docDescriptionSupplement(SUPPLEMENT_DESCRIPTION_CONTROLS.get())
                .multiValued()
                .valuePlaceholder(INFO_LDAP_CONTROL_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "ldapVersion" integer argument.
     *
     * @return The "ldapVersion" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static IntegerArgument getLdapVersion() throws ArgumentException {
        return IntegerArgument.builder(OPTION_LONG_PROTOCOL_VERSION)
                .shortIdentifier(OPTION_SHORT_PROTOCOL_VERSION)
                .description(INFO_DESCRIPTION_VERSION.get())
                .defaultValue(3)
                .valuePlaceholder(INFO_PROTOCOL_VERSION_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "quiet" boolean argument.
     *
     * @return The "quiet" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getQuiet() throws ArgumentException {
        return BooleanArgument.builder(OPTION_LONG_QUIET)
                .shortIdentifier(OPTION_SHORT_QUIET)
                .description(INFO_DESCRIPTION_QUIET.get())
                .buildArgument();
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
        return BooleanArgument.builder(OPTION_LONG_DRYRUN)
                .shortIdentifier(OPTION_SHORT_DRYRUN)
                .description(INFO_DESCRIPTION_NOOP.get())
                .buildArgument();
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
        return BooleanArgument.builder(OPTION_LONG_NO_PROMPT)
                .shortIdentifier(OPTION_SHORT_NO_PROMPT)
                .description(INFO_DESCRIPTION_NO_PROMPT.get())
                .buildArgument();
    }

    /**
     * Returns the "acceptLicense" boolean argument.
     *
     * @return The "acceptLicense" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getAcceptLicense() throws ArgumentException {
        return BooleanArgument.builder(OPTION_LONG_ACCEPT_LICENSE)
                .description(INFO_OPTION_ACCEPT_LICENSE.get())
                .buildArgument();
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
        return IntegerArgument.builder("timeLimit")
                .shortIdentifier('t')
                .description(INFO_DESCRIPTION_TIME_LIMIT.get())
                .lowerBound(0)
                .defaultValue(0)
                .valuePlaceholder(INFO_TIME_LIMIT_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "trustAll" boolean argument.
     *
     * @return The "trustAll" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getTrustAll() throws ArgumentException {
        return BooleanArgument.builder(OPTION_LONG_TRUSTALL)
                .shortIdentifier(OPTION_SHORT_TRUSTALL)
                .description(INFO_DESCRIPTION_TRUSTALL.get())
                .buildArgument();
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
        return StringArgument.builder("trustManagerProviderDN")
                .shortIdentifier('t')
                .description(INFO_DESCRIPTION_TRUSTMANAGER_PROVIDER_DN.get())
                .valuePlaceholder(INFO_TRUST_MANAGER_PROVIDER_DN_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "trustStorePath" string argument.
     *
     * @return The "trustStorePath" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getTrustStorePath() throws ArgumentException {
        return getTrustStorePath(null);
    }

    /**
     * Returns the "trustStorePath" string argument initialized with the provided default value.
     *
     * @param defaultValue
     *          The "trustStorePath" argument default value
     * @return The "trustStorePath" string argument initialized with the provided default value.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getTrustStorePath(final String defaultValue) throws ArgumentException {
        return StringArgument.builder(OPTION_LONG_TRUSTSTOREPATH)
                .shortIdentifier(OPTION_SHORT_TRUSTSTOREPATH)
                .description(INFO_DESCRIPTION_TRUSTSTOREPATH.get())
                .defaultValue(defaultValue)
                .valuePlaceholder(INFO_TRUSTSTOREPATH_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "typesOnly" boolean argument.
     *
     * @return The "typesOnly" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getTypesOnly() throws ArgumentException {
        return BooleanArgument.builder("typesOnly")
                .shortIdentifier('A')
                .description(INFO_DESCRIPTION_TYPES_ONLY.get())
                .buildArgument();
    }

    /**
     * Returns the "truststorepw" string argument.
     *
     * @return The "truststorepw" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getTrustStorePassword() throws ArgumentException {
        return StringArgument.builder(OPTION_LONG_TRUSTSTORE_PWD)
                .shortIdentifier(OPTION_SHORT_TRUSTSTORE_PWD)
                .description(INFO_DESCRIPTION_TRUSTSTOREPASSWORD.get())
                .valuePlaceholder(INFO_TRUSTSTORE_PWD_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "trustStorePasswordFile" file argument.
     *
     * @return The "trustStorePasswordFile" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static FileBasedArgument getTrustStorePasswordFile() throws ArgumentException {
        return FileBasedArgument.builder(OPTION_LONG_TRUSTSTORE_PWD_FILE)
                .shortIdentifier(OPTION_SHORT_TRUSTSTORE_PWD_FILE)
                .description(INFO_DESCRIPTION_TRUSTSTOREPASSWORD_FILE.get())
                .valuePlaceholder(INFO_TRUSTSTORE_PWD_FILE_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns a "connectTimeout" hidden integer argument.
     *
     * @return A "connectTimeout" hidden integer argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static IntegerArgument getConnectTimeOutHidden() throws ArgumentException {
        return getConnectTimeOut(true);
    }

    /**
     * Returns a "connectTimeout" integer argument.
     *
     * @return A "connectTimeout" integer argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static IntegerArgument getConnectTimeOut() throws ArgumentException {
        return getConnectTimeOut(false);
    }

    private static IntegerArgument getConnectTimeOut(final boolean hidden) throws ArgumentException {
        final IntegerArgument.Builder builder = IntegerArgument.builder(OPTION_LONG_CONNECT_TIMEOUT)
                .description(INFO_DESCRIPTION_CONNECTION_TIMEOUT.get())
                .lowerBound(0)
                .defaultValue(DEFAULT_LDAP_CONNECT_TIMEOUT)
                .valuePlaceholder(INFO_TIMEOUT_PLACEHOLDER.get());
        if (hidden) {
            builder.hidden();
        }
        return builder.buildArgument();
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
        return StringArgument.builder("cleanupService")
                .shortIdentifier('c')
                .description(INFO_CONFIGURE_WINDOWS_SERVICE_DESCRIPTION_CLEANUP.get())
                .valuePlaceholder(INFO_SERVICE_NAME_PLACEHOLDER.get())
                .buildArgument();
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
        return BooleanArgument.builder(OPTION_LONG_CLI)
                .shortIdentifier(OPTION_SHORT_CLI)
                .description(INFO_ARGUMENT_DESCRIPTION_CLI.get())
                .buildArgument();
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
        final StringArgument configFile = StringArgument.builder("configFile")
                .shortIdentifier('f')
                .description(INFO_DESCRIPTION_CONFIG_FILE.get())
                .hidden()
                .required()
                .valuePlaceholder(INFO_CONFIGFILE_PLACEHOLDER.get())
                .buildArgument();
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
        return StringArgument.builder(OPTION_LONG_CONFIG_CLASS)
                .shortIdentifier(OPTION_SHORT_CONFIG_CLASS)
                .description(INFO_DESCRIPTION_CONFIG_CLASS.get())
                .hidden()
                .required()
                .defaultValue(configFileHandlerName)
                .valuePlaceholder(INFO_CONFIGCLASS_PLACEHOLDER.get())
                .buildArgument();
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
        return StringArgument.builder("backendID")
                .shortIdentifier('n')
                .description(INFO_BACKUPDB_DESCRIPTION_BACKEND_ID.get())
                .multiValued()
                .valuePlaceholder(INFO_BACKENDNAME_PLACEHOLDER.get())
                .buildArgument();
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
        return StringArgument.builder("backupDirectory")
                .shortIdentifier('d')
                .description(INFO_DESCRIPTION_BACKUP_DIR.get())
                .required()
                .valuePlaceholder(INFO_BACKUPDIR_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "baseDN" string argument.
     *
     * @return The "baseDN" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getBaseDN() throws ArgumentException {
        return StringArgument.builder(OPTION_LONG_BASEDN)
                .shortIdentifier(OPTION_SHORT_BASEDN)
                .description(INFO_ARGUMENT_DESCRIPTION_BASEDN.get())
                .multiValued()
                .valuePlaceholder(INFO_BASEDN_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "batchFilePath" string argument.
     *
     * @return The "batchFilePath" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getBatchFilePath() throws ArgumentException {
        return StringArgument.builder(OPTION_LONG_BATCH_FILE_PATH)
                .shortIdentifier(OPTION_SHORT_BATCH_FILE_PATH)
                .description(INFO_DESCRIPTION_BATCH_FILE_PATH.get())
                .valuePlaceholder(INFO_BATCH_FILE_PATH_PLACEHOLDER.get())
                .buildArgument();
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
        return StringArgument.builder(OPTION_LONG_BINDDN)
                .shortIdentifier(OPTION_SHORT_BINDDN)
                .description(INFO_DESCRIPTION_BINDDN.get())
                .defaultValue(defaultBindDN)
                .valuePlaceholder(INFO_BINDDN_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "bindPassword" string argument.
     *
     * @return The "bindPassword" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getBindPassword() throws ArgumentException {
        return StringArgument.builder(OPTION_LONG_BINDPWD)
                .shortIdentifier(OPTION_SHORT_BINDPWD)
                .description(INFO_DESCRIPTION_BINDPASSWORD.get())
                .valuePlaceholder(INFO_BINDPWD_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "bindPasswordFile" file argument.
     *
     * @return The "bindPasswordFile" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static FileBasedArgument getBindPasswordFile() throws ArgumentException {
        return FileBasedArgument.builder(OPTION_LONG_BINDPWD_FILE)
                .shortIdentifier(OPTION_SHORT_BINDPWD_FILE)
                .description(INFO_DESCRIPTION_BINDPASSWORDFILE.get())
                .valuePlaceholder(INFO_BINDPWD_FILE_PLACEHOLDER.get())
                .buildArgument();
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
        return BooleanArgument.builder("addBaseEntry")
                .shortIdentifier('a')
                .description(INFO_ARGUMENT_DESCRIPTION_ADDBASE.get())
                .buildArgument();
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
        return StringArgument.builder("rejectFile")
                .shortIdentifier('R')
                .description(INFO_GENERAL_DESCRIPTION_REJECTED_FILE.get())
                .valuePlaceholder(INFO_REJECT_FILE_PLACEHOLDER.get())
                .buildArgument();
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
        return BooleanArgument.builder(OPTION_LONG_REMOTE)
                .shortIdentifier(OPTION_SHORT_REMOTE)
                .description(INFO_DESCRIPTION_REMOTE.get())
                .buildArgument();
    }

    /**
     * Returns the "reportauthzid" boolean argument.
     *
     * @return The "reportauthzid" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getReportAuthzId() throws ArgumentException {
        return BooleanArgument.builder(OPTION_LONG_REPORT_AUTHZ_ID)
                .shortIdentifier('E')
                .description(INFO_DESCRIPTION_REPORT_AUTHZID.get())
                .buildArgument();
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
        return BooleanArgument.builder(OPTION_LONG_RESTART)
                .shortIdentifier('R')
                .description(INFO_DESCRIPTION_RESTART.get())
                .buildArgument();
    }

    /**
     * Returns the "skip file" string argument.
     *
     * @return The "skipFile" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getSkippedImportFile() throws ArgumentException {
        return StringArgument.builder("skipFile")
                .description(INFO_GENERAL_DESCRIPTION_SKIPPED_FILE.get())
                .valuePlaceholder(INFO_SKIP_FILE_PLACEHOLDER.get())
                .buildArgument();
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
        return IntegerArgument.builder("sampleData")
                .shortIdentifier('d')
                .description(INFO_SETUP_DESCRIPTION_SAMPLE_DATA.get())
                .lowerBound(0)
                .defaultValue(0)
                .valuePlaceholder(INFO_NUM_ENTRIES_PLACEHOLDER.get())
                .buildArgument();
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
        return StringArgument.builder(OPTION_LONG_SASLOPTION)
                .shortIdentifier(OPTION_SHORT_SASLOPTION)
                .description(INFO_LDAP_CONN_DESCRIPTION_SASLOPTIONS.get())
                .multiValued()
                .valuePlaceholder(INFO_SASL_OPTION_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "searchScope" string argument.<br>
     * <i> N.B : the 's' short option is also used by servicestate, sourceldif, randomSeed, script-friendly.</i>
     *
     * @return The "searchScope" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static MultiChoiceArgument<SearchScope> getSearchScope() throws ArgumentException {
        return MultiChoiceArgument.<SearchScope>builder(OPTION_LONG_SEARCHSCOPE)
                .shortIdentifier(OPTION_SHORT_SEARCHSCOPE)
                .description(INFO_SEARCH_DESCRIPTION_SEARCH_SCOPE.get())
                .allowedValues(SearchScope.values())
                .defaultValue(SearchScope.WHOLE_SUBTREE)
                .valuePlaceholder(INFO_SEARCH_SCOPE_PLACEHOLDER.get())
                .buildArgument();
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
        return StringArgument.builder(OPTION_LONG_SERVER_ROOT)
                .shortIdentifier(OPTION_SHORT_SERVER_ROOT)
                .hidden()
                .valuePlaceholder(INFO_SERVER_ROOT_DIR_PLACEHOLDER.get())
                .buildArgument();
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
        return BooleanArgument.builder("serviceState")
                .shortIdentifier('s')
                .description(INFO_CONFIGURE_WINDOWS_SERVICE_DESCRIPTION_STATE.get())
                .buildArgument();
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
        return BooleanArgument.builder(OPTION_LONG_SCRIPT_FRIENDLY)
                .shortIdentifier(OPTION_SHORT_SCRIPT_FRIENDLY)
                .description(INFO_DESCRIPTION_SCRIPT_FRIENDLY.get())
                .buildArgument();
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
        return IntegerArgument.builder("ldapPort")
                .shortIdentifier(OPTION_SHORT_PORT)
                .description(INFO_ARGUMENT_DESCRIPTION_LDAPPORT.get())
                .range(1, 65535)
                .defaultValue(defaultLdapPort)
                .valuePlaceholder(INFO_PORT_PLACEHOLDER.get())
                .buildArgument();
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
        return IntegerArgument.builder("adminConnectorPort")
                .description(INFO_ARGUMENT_DESCRIPTION_ADMINCONNECTORPORT.get())
                .range(1, 65535)
                .defaultValue(defaultAdminPort)
                .valuePlaceholder(INFO_PORT_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "advanced" boolean argument.
     *
     * @return The "advanced" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getAdvancedMode() throws ArgumentException {
        return BooleanArgument.builder(OPTION_LONG_ADVANCED)
                .description(INFO_DESCRIPTION_ADVANCED.get())
                .buildArgument();
    }

    /**
     * Returns the "assertionfilter" string argument.
     *
     * @return The "assertionfilter" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getAssertionFilter() throws ArgumentException {
        return StringArgument.builder(OPTION_LONG_ASSERTION_FILE)
                .description(INFO_DESCRIPTION_ASSERTION_FILTER.get())
                .valuePlaceholder(INFO_ASSERTION_FILTER_PLACEHOLDER.get())
                .buildArgument();
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
        return IntegerArgument.builder("jmxPort")
                .shortIdentifier('x')
                .description(INFO_ARGUMENT_DESCRIPTION_SKIPPORT.get())
                .range(1, 65535)
                .defaultValue(defaultJMXPort)
                .valuePlaceholder(INFO_JMXPORT_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "skip port check" boolean argument.
     *
     * @return The "getSkipPortCheck" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getSkipPortCheck() throws ArgumentException {
        return BooleanArgument.builder("skipPortCheck")
                .shortIdentifier('S')
                .description(INFO_ARGUMENT_DESCRIPTION_SKIPPORT.get())
                .buildArgument();
    }

    /**
     * Returns the "startTLS" boolean argument.
     *
     * @return The "startTLS" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getStartTLS() throws ArgumentException {
        return BooleanArgument.builder(OPTION_LONG_START_TLS)
                .shortIdentifier(OPTION_SHORT_START_TLS)
                .description(INFO_DESCRIPTION_START_TLS.get())
                .buildArgument();
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
        return StringArgument.builder("stopReason")
                .shortIdentifier('r')
                .description(INFO_DESCRIPTION_STOP_REASON.get())
                .valuePlaceholder(INFO_STOP_REASON_PLACEHOLDER.get())
                .buildArgument();
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
        return StringArgument.builder("stopTime")
                .shortIdentifier('t')
                .description(INFO_DESCRIPTION_STOP_TIME.get())
                .valuePlaceholder(INFO_STOP_TIME_PLACEHOLDER.get())
                .buildArgument();
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
        return StringArgument.builder(OPTION_LONG_ROOT_USER_DN)
                .shortIdentifier(OPTION_SHORT_ROOT_USER_DN)
                .description(INFO_ARGUMENT_DESCRIPTION_ROOTDN.get())
                .defaultValue("cn=Directory Manager")
                .valuePlaceholder(INFO_ROOT_USER_DN_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "directory manager DN password" string argument.
     *
     * @return The "rootUserPassword" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getRootDNPwd() throws ArgumentException {
        return StringArgument.builder("rootUserPassword")
                .shortIdentifier(OPTION_SHORT_BINDPWD)
                .description(INFO_ROOT_USER_PWD_PLACEHOLDER.get())
                .valuePlaceholder(INFO_ROOT_USER_PWD_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "directory manager DN password file" file argument.
     *
     * @return The "rootUserPasswordFile" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static FileBasedArgument getRootDNPwdFile() throws ArgumentException {
        return FileBasedArgument.builder("rootUserPasswordFile")
                .shortIdentifier(OPTION_SHORT_BINDPWD_FILE)
                .description(INFO_ARGUMENT_DESCRIPTION_ROOTPWFILE.get())
                .valuePlaceholder(INFO_ROOT_USER_PWD_FILE_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "enable window service" integer argument.
     *
     * @return The "enableWindowsService" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getEnableWindowsService() throws ArgumentException {
        return BooleanArgument.builder("enableWindowsService")
                .shortIdentifier('e')
                .description(INFO_ARGUMENT_DESCRIPTION_ENABLE_WINDOWS_SERVICE.get())
                .buildArgument();
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
        return StringArgument.builder("encoding")
                .shortIdentifier('i')
                .description(INFO_DESCRIPTION_ENCODING.get())
                .valuePlaceholder(INFO_ENCODING_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "do not start" boolean argument.
     *
     * @return The "doNotStart" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getDoNotStart() throws ArgumentException {
        return BooleanArgument.builder("doNotStart")
                .shortIdentifier('O')
                .description(INFO_SETUP_DESCRIPTION_DO_NOT_START.get())
                .buildArgument();
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
        return BooleanArgument.builder("disableService")
                .shortIdentifier('d')
                .description(INFO_CONFIGURE_WINDOWS_SERVICE_DESCRIPTION_DISABLE.get())
                .buildArgument();
    }

    /**
     * Returns the "displayCommand" boolean argument.
     *
     * @return The "displayCommand" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getDisplayEquivalentCommand() throws ArgumentException {
        return BooleanArgument.builder(OPTION_LONG_DISPLAY_EQUIVALENT)
                .description(INFO_DESCRIPTION_DISPLAY_EQUIVALENT.get())
                .buildArgument();
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
        return StringArgument.builder(OPTION_LONG_EQUIVALENT_COMMAND_FILE_PATH)
                .description(description)
                .valuePlaceholder(INFO_PATH_PLACEHOLDER.get())
                .buildArgument();
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
        return StringArgument.builder(OPTION_LONG_FILENAME)
                .shortIdentifier(OPTION_SHORT_FILENAME)
                .description(description)
                .valuePlaceholder(INFO_FILE_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "enable start TLS" boolean argument.
     *
     * @return The "enableStartTLS" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getEnableTLS() throws ArgumentException {
        return BooleanArgument.builder("enableStartTLS")
                .shortIdentifier(OPTION_SHORT_START_TLS)
                .description(INFO_SETUP_DESCRIPTION_ENABLE_STARTTLS.get())
                .buildArgument();
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
        return IntegerArgument.builder("ldapsPort")
                .shortIdentifier(OPTION_SHORT_USE_SSL)
                .description(INFO_ARGUMENT_DESCRIPTION_LDAPSPORT.get())
                .range(1, 65535)
                .defaultValue(defaultSecurePort)
                .valuePlaceholder(INFO_PORT_PLACEHOLDER.get())
                .buildArgument();
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
        return StringArgument.builder(OPTION_LONG_LDIF_FILE)
                .shortIdentifier(OPTION_SHORT_LDIF_FILE)
                .description(description)
                .multiValued()
                .valuePlaceholder(INFO_LDIFFILE_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "generate self certificate" boolean argument.
     *
     * @return The "generateSelfSignedCertificate" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getGenerateSelfSigned() throws ArgumentException {
        return BooleanArgument.builder("generateSelfSignedCertificate")
                .description(INFO_ARGUMENT_DESCRIPTION_USE_SELF_SIGNED_CERTIFICATE.get())
                .buildArgument();
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
        return StringArgument.builder(OPTION_LONG_HOST)
                .shortIdentifier(OPTION_SHORT_HOST)
                .description(description != null ? description : INFO_ARGUMENT_DESCRIPTION_HOST_NAME.get())
                .defaultValue(defaultHostName)
                .valuePlaceholder(INFO_HOST_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "use PKCS11 key store" boolean argument.
     *
     * @return The "usePkcs11Keystore" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static BooleanArgument getUsePKCS11Keystore() throws ArgumentException {
        return BooleanArgument.builder("usePkcs11Keystore")
                .description(INFO_ARGUMENT_DESCRIPTION_USE_PKCS11.get())
                .buildArgument();
    }

    /**
     * Returns the "use java key store" string argument.
     *
     * @return The "useJavaKeystore" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getUseJavaKeyStore() throws ArgumentException {
        return StringArgument.builder("useJavaKeystore")
                .description(INFO_ARGUMENT_DESCRIPTION_USE_JAVAKEYSTORE.get())
                .valuePlaceholder(INFO_KEYSTOREPATH_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "use JCEKS" string argument.
     *
     * @return The "useJCEKS" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getUseJCEKS() throws ArgumentException {
        return StringArgument.builder("useJCEKS")
                .description(INFO_ARGUMENT_DESCRIPTION_USE_JCEKS.get())
                .valuePlaceholder(INFO_KEYSTOREPATH_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "use PKCS12 key store" string argument.
     *
     * @return The "usePkcs12keyStore" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getUsePKCS12KeyStore() throws ArgumentException {
        return StringArgument.builder("usePkcs12keyStore")
                .description(INFO_ARGUMENT_DESCRIPTION_USE_PKCS12.get())
                .valuePlaceholder(INFO_KEYSTOREPATH_PLACEHOLDER.get())
                .buildArgument();
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
        return BooleanArgument.builder(OPTION_LONG_USE_SSL)
                .shortIdentifier(OPTION_SHORT_USE_SSL)
                .description(INFO_DESCRIPTION_USE_SSL.get())
                .buildArgument();
    }

    /**
     * Returns the "keymanagerpath" string argument.
     *
     * @return The "keymanagerpath" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getKeyManagerPath() throws ArgumentException {
        return StringArgument.builder("keyManagerPath")
                .shortIdentifier('m')
                .description(INFO_DESCRIPTION_KEYMANAGER_PATH.get())
                .valuePlaceholder(INFO_KEY_MANAGER_PATH_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "keymanagerproviderdn" string argument.
     *
     * @return The "keymanagerproviderdn" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getKeyManagerProviderDN() throws ArgumentException {
        return StringArgument.builder("keyManagerProviderDN")
                .shortIdentifier('k')
                .description(INFO_DESCRIPTION_KEYMANAGER_PROVIDER_DN.get())
                .valuePlaceholder(INFO_KEY_MANAGER_PROVIDER_DN_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "key store password" string argument.
     *
     * @return The "keyStorePassword" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getKeyStorePassword() throws ArgumentException {
        return StringArgument.builder(OPTION_LONG_KEYSTORE_PWD)
                .shortIdentifier(OPTION_SHORT_KEYSTORE_PWD)
                .description(INFO_ARGUMENT_DESCRIPTION_KEYSTOREPASSWORD.get())
                .valuePlaceholder(INFO_KEYSTORE_PWD_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "key store password file" file argument.
     *
     * @return The "keyStorePassword" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static FileBasedArgument getKeyStorePasswordFile() throws ArgumentException {
        return FileBasedArgument.builder(OPTION_LONG_KEYSTORE_PWD_FILE)
                .shortIdentifier(OPTION_SHORT_KEYSTORE_PWD_FILE)
                .description(INFO_ARGUMENT_DESCRIPTION_KEYSTOREPASSWORD_FILE.get())
                .valuePlaceholder(INFO_KEYSTORE_PWD_FILE_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "keyStorePath" string argument.
     *
     * @return The "keyStorePath" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getKeyStorePath() throws ArgumentException {
        return StringArgument.builder(OPTION_LONG_KEYSTOREPATH)
                .shortIdentifier(OPTION_SHORT_KEYSTOREPATH)
                .description(INFO_DESCRIPTION_KEYSTOREPATH.get())
                .valuePlaceholder(INFO_KEYSTOREPATH_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "key store password file" string argument.
     *
     * @return The "keyStorePassword" argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getCertNickName() throws ArgumentException {
        return StringArgument.builder(OPTION_LONG_CERT_NICKNAME)
                .shortIdentifier(OPTION_SHORT_CERT_NICKNAME)
                .description(INFO_ARGUMENT_DESCRIPTION_CERT_NICKNAME.get())
                .multiValued()
                .valuePlaceholder(INFO_NICKNAME_PLACEHOLDER.get())
                .buildArgument();
    }

    /**
     * Returns the "admin uid" string argument with the provided description.
     *
     * @param description
     *            The argument localizable description.
     * @return The "admin uid" string argument with the provided description.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getAdminUid(final LocalizableMessage description) throws ArgumentException {
        return getAdminUid(false, description);
    }

    /**
     * Returns the "admin uid" hidden string argument.
     *
     * @param description
     *            The argument localizable description.
     * @return The "admin uid" hidden string argument.
     * @throws ArgumentException
     *             If there is a problem with any of the parameters used to create this argument.
     */
    public static StringArgument getAdminUidHidden(final LocalizableMessage description) throws ArgumentException {
        return getAdminUid(true, description);
    }

    private static StringArgument getAdminUid(final boolean hidden, final LocalizableMessage description)
            throws ArgumentException {
        final StringArgument.Builder builder = StringArgument.builder(OPTION_LONG_ADMIN_UID)
                .shortIdentifier('I')
                .description(description)
                .defaultValue(CliConstants.GLOBAL_ADMIN_UID)
                .valuePlaceholder(INFO_ADMINUID_PLACEHOLDER.get());
        if (hidden) {
            builder.hidden();
        }
        return builder.buildArgument();
    }
}
