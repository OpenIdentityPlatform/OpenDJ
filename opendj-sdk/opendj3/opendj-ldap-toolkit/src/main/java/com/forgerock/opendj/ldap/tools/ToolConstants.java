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
 *      Portions copyright 2012 ForgeRock AS.
 */
package com.forgerock.opendj.ldap.tools;

/**
 * This class defines a number of constants used in one or more Directory Server
 * tools.
 */
final class ToolConstants {
    /**
     * The name of the SASL property that can be used to provide the
     * authentication ID for the bind.
     */
    static final String SASL_PROPERTY_AUTHID = "authid";

    /**
     * The name of the SASL property that can be used to provide the
     * authorization ID for the bind.
     */
    static final String SASL_PROPERTY_AUTHZID = "authzid";

    /**
     * The name of the SASL property that can be used to provide the digest URI
     * for the bind.
     */
    static final String SASL_PROPERTY_DIGEST_URI = "digest-uri";

    /**
     * The name of the SASL property that can be used to provide the KDC for use
     * in Kerberos authentication.
     */
    static final String SASL_PROPERTY_KDC = "kdc";

    /**
     * The name of the SASL property that can be used to provide the quality of
     * protection for the bind.
     */
    static final String SASL_PROPERTY_QOP = "qop";

    /**
     * The name of the SASL property that can be used to provide the realm for
     * the bind.
     */
    static final String SASL_PROPERTY_REALM = "realm";

    /**
     * The name of the SASL property that can be used to provide trace
     * information for a SASL ANONYMOUS request.
     */
    static final String SASL_PROPERTY_TRACE = "trace";

    /**
     * The name of the SASL property that can be used to provide the SASL
     * mechanism to use.
     */
    static final String SASL_PROPERTY_MECH = "mech";

    /**
     * The name of the opendj configuration direction in the user home
     * directory.
     */
    static final String DEFAULT_OPENDJ_CONFIG_DIR = ".opendj";

    /**
     * The default properties file name.
     */
    static final String DEFAULT_OPENDJ_PROPERTIES_FILE_NAME = "tools";

    /**
     * The default properties file extension.
     */
    static final String DEFAULT_OPENDJ_PROPERTIES_FILE_EXTENSION = ".properties";

    /**
     * The value for the short option batchFilePath.
     */
    static final char OPTION_SHORT_BATCH_FILE_PATH = 'F';

    /**
     * The value for the long option batchFilePath .
     */
    static final String OPTION_LONG_BATCH_FILE_PATH = "batchFilePath";

    /**
     * The value for the short option hostname.
     */
    static final char OPTION_SHORT_HOST = 'h';

    /**
     * The value for the long option hostname.
     */
    static final String OPTION_LONG_HOST = "hostname";

    /**
     * The value for the short option port.
     */
    static final char OPTION_SHORT_PORT = 'p';

    /**
     * The value for the long option port.
     */
    static final String OPTION_LONG_PORT = "port";

    /**
     * The value for the short option useSSL.
     */
    static final char OPTION_SHORT_USE_SSL = 'Z';

    /**
     * The value for the long option useSSL.
     */
    static final String OPTION_LONG_USE_SSL = "useSSL";

    /**
     * The value for the short option baseDN.
     */
    static final char OPTION_SHORT_BASEDN = 'b';

    /**
     * The value for the long option baseDN.
     */
    static final String OPTION_LONG_BASEDN = "baseDN";

    /**
     * The value for the short option bindDN.
     */
    static final char OPTION_SHORT_BINDDN = 'D';

    /**
     * The value for the long option bindDN.
     */
    static final String OPTION_LONG_BINDDN = "bindDN";

    /**
     * The value for the short option bindPassword.
     */
    static final char OPTION_SHORT_BINDPWD = 'w';

    /**
     * The value for the long option bindPassword.
     */
    static final String OPTION_LONG_BINDPWD = "bindPassword";

    /**
     * The value for the short option bindPasswordFile.
     */
    static final char OPTION_SHORT_BINDPWD_FILE = 'j';

    /**
     * The value for the long option bindPasswordFile.
     */
    static final String OPTION_LONG_BINDPWD_FILE = "bindPasswordFile";

    /**
     * The value for the short option compress.
     */
    static final char OPTION_SHORT_COMPRESS = 'c';

    /**
     * The value for the long option compress.
     */
    static final String OPTION_LONG_COMPRESS = "compress";

    /**
     * The value for the short option filename.
     */
    static final char OPTION_SHORT_FILENAME = 'f';

    /**
     * The value for the long option filename.
     */
    static final String OPTION_LONG_FILENAME = "filename";

    /**
     * The value for the short option ldifFile.
     */
    static final char OPTION_SHORT_LDIF_FILE = 'l';

    /**
     * The value for the long option ldifFile.
     */
    static final String OPTION_LONG_LDIF_FILE = "ldifFile";

    /**
     * The value for the short option useStartTLS.
     */
    static final char OPTION_SHORT_START_TLS = 'q';

    /**
     * The value for the long option useStartTLS.
     */
    static final String OPTION_LONG_START_TLS = "useStartTLS";

    /**
     * The value for the short option randomSeed.
     */
    static final char OPTION_SHORT_RANDOM_SEED = 's';

    /**
     * The value for the long option randomSeed.
     */
    static final String OPTION_LONG_RANDOM_SEED = "randomSeed";

    /**
     * The value for the short option keyStorePath.
     */
    static final char OPTION_SHORT_KEYSTOREPATH = 'K';

    /**
     * The value for the long option keyStorePath.
     */
    static final String OPTION_LONG_KEYSTOREPATH = "keyStorePath";

    /**
     * The value for the short option trustStorePath.
     */
    static final char OPTION_SHORT_TRUSTSTOREPATH = 'P';

    /**
     * The value for the long option trustStorePath.
     */
    static final String OPTION_LONG_TRUSTSTOREPATH = "trustStorePath";

    /**
     * The value for the short option keyStorePassword.
     */
    static final char OPTION_SHORT_KEYSTORE_PWD = 'W';

    /**
     * The value for the long option keyStorePassword.
     */
    static final String OPTION_LONG_KEYSTORE_PWD = "keyStorePassword";

    /**
     * The value for the short option trustStorePassword.
     */
    static final char OPTION_SHORT_TRUSTSTORE_PWD = 'T';

    /**
     * The value for the long option trustStorePassword.
     */
    static final String OPTION_LONG_TRUSTSTORE_PWD = "trustStorePassword";

    /**
     * The value for the short option keyStorePasswordFile .
     */
    static final char OPTION_SHORT_KEYSTORE_PWD_FILE = 'u';

    /**
     * The value for the long option keyStorePasswordFile .
     */
    static final String OPTION_LONG_KEYSTORE_PWD_FILE = "keyStorePasswordFile";

    /**
     * The value for the short option keyStorePasswordFile .
     */
    static final char OPTION_SHORT_TRUSTSTORE_PWD_FILE = 'U';

    /**
     * The value for the long option keyStorePasswordFile .
     */
    static final String OPTION_LONG_TRUSTSTORE_PWD_FILE = "trustStorePasswordFile";

    /**
     * The value for the short option trustAll .
     */
    static final char OPTION_SHORT_TRUSTALL = 'X';

    /**
     * The value for the long option trustAll .
     */
    static final String OPTION_LONG_TRUSTALL = "trustAll";

    /**
     * The value for the short option certNickname .
     */
    static final char OPTION_SHORT_CERT_NICKNAME = 'N';

    /**
     * The value for the long option certNickname .
     */
    static final String OPTION_LONG_CERT_NICKNAME = "certNickname";

    /**
     * The value for the long option assertionFilter .
     */
    static final String OPTION_LONG_ASSERTION_FILE = "assertionFilter";

    /**
     * The value for the short option dry-run.
     */
    static final char OPTION_SHORT_DRYRUN = 'n';

    /**
     * The value for the long option dry-run.
     */
    static final String OPTION_LONG_DRYRUN = "dry-run";

    /**
     * The value for the short option help.
     */
    static final char OPTION_SHORT_HELP = 'H';

    /**
     * The value for the long option help.
     */
    static final String OPTION_LONG_HELP = "help";

    /**
     * The value for the long option cli.
     */
    static final String OPTION_LONG_CLI = "cli";

    /**
     * The value for the short option cli.
     */
    static final char OPTION_SHORT_CLI = 'i';

    /**
     * The value for the short option proxyAs.
     */
    static final char OPTION_SHORT_PROXYAUTHID = 'Y';

    /**
     * The value for the long option proxyAs.
     */
    static final String OPTION_LONG_PROXYAUTHID = "proxyAs";

    /**
     * The value for the short option saslOption.
     */
    static final char OPTION_SHORT_SASLOPTION = 'o';

    /**
     * The value for the long option saslOption.
     */
    static final String OPTION_LONG_SASLOPTION = "saslOption";

    /**
     * The value for the short option geteffectiverights control authzid.
     */
    static final char OPTION_SHORT_EFFECTIVERIGHTSUSER = 'g';

    /**
     * The value for the long option geteffectiverights control authzid.
     */
    static final String OPTION_LONG_EFFECTIVERIGHTSUSER = "getEffectiveRightsAuthzid";

    /**
     * The value for the short option geteffectiveights control attributes.
     */
    static final char OPTION_SHORT_EFFECTIVERIGHTSATTR = 'e';

    /**
     * The value for the long option geteffectiverights control specific
     * attribute list.
     */
    static final String OPTION_LONG_EFFECTIVERIGHTSATTR = "getEffectiveRightsAttribute";

    /**
     * The value for the short option protocol version attributes.
     */
    static final char OPTION_SHORT_PROTOCOL_VERSION = 'V';

    /**
     * The value for the long option protocol version attribute.
     */
    static final String OPTION_LONG_PROTOCOL_VERSION = "ldapVersion";

    /**
     * The value for the long option version.
     */
    static final char OPTION_SHORT_PRODUCT_VERSION = 'V';

    /**
     * The value for the long option version.
     */
    static final String OPTION_LONG_PRODUCT_VERSION = "version";

    /**
     * The value for the short option description attributes.
     */
    static final char OPTION_SHORT_DESCRIPTION = 'd';

    /**
     * The value for the long option description attribute.
     */
    static final String OPTION_LONG_DESCRIPTION = "description";

    /**
     * The value for the short option groupName attributes.
     */
    static final char OPTION_SHORT_GROUPNAME = 'g';

    /**
     * The value for the long option groupName attribute.
     */
    static final String OPTION_LONG_GROUPNAME = "groupName";

    /**
     * The value for the short option newGroupName attribute.
     */
    static final char OPTION_SHORT_NEWGROUPNAME = 'n';

    /**
     * The value for the long option groupName attribute.
     */
    static final String OPTION_LONG_NEWGROUPNAME = "newGroupName";

    /**
     * The value for the short option member-name attributes.
     */
    static final char OPTION_SHORT_MEMBERNAME = 'm';

    /**
     * The value for the long member-name version attribute.
     */
    static final String OPTION_LONG_MEMBERNAME = "memberName";

    /**
     * The value for the short option serverID attributes.
     */
    static final String OPTION_SHORT_SERVERID = null;

    /**
     * The value for the long option serverID attribute.
     */
    static final String OPTION_LONG_SERVERID = "serverID";

    /**
     * The value for the short option userID attributes.
     */
    static final String OPTION_SHORT_USERID = null;

    /**
     * The value for the long option userID attribute.
     */
    static final String OPTION_LONG_USERID = "userID";

    /**
     * The value for the short option set.
     */
    static final Character OPTION_SHORT_SET = null;

    /**
     * The value for the long option set.
     */
    static final String OPTION_LONG_SET = "set";

    /**
     * Value for the quiet option short form.
     */
    static final Character OPTION_SHORT_QUIET = 'Q';

    /**
     * Value for the quiet option long form.
     */
    static final String OPTION_LONG_QUIET = "quiet";

    /**
     * Value for noninteractive session short form.
     */
    static final Character OPTION_SHORT_NO_PROMPT = 'n';

    /**
     * Value for noninteractive session long form.
     */
    static final String OPTION_LONG_NO_PROMPT = "no-prompt";

    /**
     * Long form of script friendly option.
     */
    static final String OPTION_LONG_SCRIPT_FRIENDLY = "script-friendly";

    /**
     * Short form of script friendly option.
     */
    static final Character OPTION_SHORT_SCRIPT_FRIENDLY = 's';

    /**
     * Value for verbose option short form.
     */
    static final Character OPTION_SHORT_VERBOSE = 'v';

    /**
     * Value for verbose option long form.
     */
    static final String OPTION_LONG_VERBOSE = "verbose";

    /**
     * The value for the long option propertiesFilePAth .
     */
    static final String OPTION_LONG_PROP_FILE_PATH = "propertiesFilePath";

    /**
     * The value for the long option propertiesFilePAth .
     */
    static final String OPTION_LONG_NO_PROP_FILE = "noPropertiesFile";

    /**
     * Long form of referenced host name.
     */
    static final String OPTION_LONG_REFERENCED_HOST_NAME = "referencedHostName";

    /**
     * Long form of admin UID.
     */
    static final String OPTION_LONG_ADMIN_UID = "adminUID";

    /**
     * Long form of report authorization ID connection option.
     */
    static final String OPTION_LONG_REPORT_AUTHZ_ID = "reportAuthzID";

    /**
     * Long form of use password policy control connection option.
     */
    static final String OPTION_LONG_USE_PW_POLICY_CTL = "usePasswordPolicyControl";

    /**
     * Long form of use SASL external connection option.
     */
    static final String OPTION_LONG_USE_SASL_EXTERNAL = "useSASLExternal";

    /**
     * Long form of option for the command-line encoding option.
     */
    static final String OPTION_LONG_ENCODING = "encoding";

    /**
     * Long form of option specifying no wrapping of the command-line.
     */
    static final String OPTION_LONG_DONT_WRAP = "dontWrap";

    /**
     * The value for the long option targetDN.
     */
    static final String OPTION_LONG_TARGETDN = "targetDN";

    /**
     * Long form of email notification upon completion option.
     */
    static final String OPTION_LONG_COMPLETION_NOTIFICATION_EMAIL = "completionNotify";

    /**
     * Short form of email notification upon completion option.
     */
    static final Character OPTION_SHORT_COMPLETION_NOTIFICATION_EMAIL = null;

    /**
     * Long form of email notification upon error option.
     */
    static final String OPTION_LONG_ERROR_NOTIFICATION_EMAIL = "errorNotify";

    /**
     * Short form of email notification upon error option.
     */
    static final Character OPTION_SHORT_ERROR_NOTIFICATION_EMAIL = null;

    /**
     * Long form of dependency option.
     */
    static final String OPTION_LONG_DEPENDENCY = "dependency";

    /**
     * Short form of dependency option.
     */
    static final Character OPTION_SHORT_DEPENDENCY = null;

    /**
     * Long form of failed dependency action option.
     */
    static final String OPTION_LONG_FAILED_DEPENDENCY_ACTION = "failedDependencyAction";

    /**
     * Short form of failed dependency action option.
     */
    static final Character OPTION_SHORT_FAILED_DEPENDENCY_ACTION = null;

    /**
     * The default separator to be used in tables.
     */
    static final String LIST_TABLE_SEPARATOR = ":";

    /**
     *
     * The value for the short option output LDIF filename.
     */
    static final char OPTION_SHORT_OUTPUT_LDIF_FILENAME = 'o';

    /**
     * The value for the long option output LDIF filename.
     */
    static final String OPTION_LONG_OUTPUT_LDIF_FILENAME = "outputLDIF";

    // Prevent instantiation.
    private ToolConstants() {

    }

}
