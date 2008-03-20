/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.tools;



/**
 * This class defines a number of constants used in one or more Directory Server
 * tools.
 */
public class ToolConstants
{
  /**
   * The name of the SASL property that can be used to provide the
   * authentication ID for the bind.
   */
  public static final String SASL_PROPERTY_AUTHID = "authid";



  /**
   * The name of the SASL property that can be used to provide the authorization
   * ID for the bind.
   */
  public static final String SASL_PROPERTY_AUTHZID = "authzid";



  /**
   * The name of the SASL property that can be used to provide the digest URI
   * for the bind.
   */
  public static final String SASL_PROPERTY_DIGEST_URI = "digest-uri";



  /**
   * The name of the SASL property that can be used to provide the KDC for use
   * in Kerberos authentication.
   */
  public static final String SASL_PROPERTY_KDC = "kdc";



  /**
   * The name of the SASL property that can be used to provide the quality of
   * protection for the bind.
   */
  public static final String SASL_PROPERTY_QOP = "qop";



  /**
   * The name of the SASL property that can be used to provide the realm for the
   * bind.
   */
  public static final String SASL_PROPERTY_REALM = "realm";



  /**
   * The name of the SASL property that can be used to provide trace information
   * for a SASL ANONYMOUS request.
   */
  public static final String SASL_PROPERTY_TRACE = "trace";


  /**
   * The name of the opends configuration direction in the user
   * home directory.
   */
  public static final String DEFAULT_OPENDS_CONFIG_DIR = ".opends";

  /**
   * The default properties file name.
   */
  public static final String DEFAULT_OPENDS_PROPERTIES_FILE_NAME =
    "tools";

  /**
   * The default properties file extension.
   */
  public static final String DEFAULT_OPENDS_PROPERTIES_FILE_EXTENSION =
    ".properties";


  /**
   * The value for the short option configClass.
   */
  public static final char OPTION_SHORT_CONFIG_CLASS = 'C';

  /**
   * The value for the long option configClass.
   */
  public static final String OPTION_LONG_CONFIG_CLASS = "configClass";

  /**
   * The value for the short option hostname.
   */
  public static final char OPTION_SHORT_HOST = 'h';

  /**
   * The value for the long option hostname.
   */
  public static final String OPTION_LONG_HOST = "hostname";

  /**
   * The value for the short option port.
   */
  public static final char OPTION_SHORT_PORT = 'p';

  /**
   * The value for the long option port.
   */
  public static final String OPTION_LONG_PORT = "port";

  /**
   * The value for the short option useSSL.
   */
  public static final char OPTION_SHORT_USE_SSL = 'Z';

  /**
   * The value for the long option useSSL.
   */
  public static final String OPTION_LONG_USE_SSL = "useSSL";

  /**
   * The value for the short option baseDN.
   */
  public static final char OPTION_SHORT_BASEDN = 'b';

  /**
   * The value for the long option baseDN.
   */
  public static final String OPTION_LONG_BASEDN = "baseDN";

  /**
   * The value for the short option rootUserDN.
   */
  public static final char OPTION_SHORT_ROOT_USER_DN = 'D';

  /**
   * The value for the long option rootUserDN.
   */
  public static final String OPTION_LONG_ROOT_USER_DN = "rootUserDN";

  /**
   * The value for the short option bindDN.
   */
  public static final char OPTION_SHORT_BINDDN = 'D';

  /**
   * The value for the long option bindDN.
   */
  public static final String OPTION_LONG_BINDDN = "bindDN";

  /**
   * The value for the short option bindPassword.
   */
  public static final char OPTION_SHORT_BINDPWD = 'w';

  /**
   * The value for the long option bindPassword.
   */
  public static final String OPTION_LONG_BINDPWD = "bindPassword";

  /**
   * The value for the short option bindPasswordFile.
   */
  public static final char OPTION_SHORT_BINDPWD_FILE = 'j';

  /**
   * The value for the long option bindPasswordFile.
   */
  public static final String OPTION_LONG_BINDPWD_FILE = "bindPasswordFile";

  /**
   * The value for the short option compress.
   */
  public static final char OPTION_SHORT_COMPRESS = 'c';

  /**
   * The value for the long option compress.
   */
  public static final String OPTION_LONG_COMPRESS = "compress";

  /**
   * The value for the short option filename.
   */
  public static final char OPTION_SHORT_FILENAME = 'f';

  /**
   * The value for the long option filename.
   */
  public static final String OPTION_LONG_FILENAME = "filename";

  /**
   * The value for the short option ldifFile.
   */
  public static final char OPTION_SHORT_LDIF_FILE = 'l';

  /**
   * The value for the long option ldifFile.
   */
  public static final String OPTION_LONG_LDIF_FILE = "ldifFile";

  /**
   * The value for the short option useStartTLS.
   */
  public static final char OPTION_SHORT_START_TLS = 'q';

  /**
   * The value for the long option useStartTLS.
   */
  public static final String OPTION_LONG_START_TLS = "useStartTLS";

  /**
   * The value for the short option randomSeed.
   */
  public static final char OPTION_SHORT_RANDOM_SEED = 's';

  /**
   * The value for the long option randomSeed.
   */
  public static final String OPTION_LONG_RANDOM_SEED = "randomSeed";

  /**
   * The value for the short option keyStorePath.
   */
  public static final char OPTION_SHORT_KEYSTOREPATH = 'K';

  /**
   * The value for the long option keyStorePath.
   */
  public static final String OPTION_LONG_KEYSTOREPATH = "keyStorePath";

  /**
   * The value for the short option trustStorePath.
   */
  public static final char OPTION_SHORT_TRUSTSTOREPATH = 'P';

  /**
   * The value for the long option trustStorePath.
   */
  public static final String OPTION_LONG_TRUSTSTOREPATH = "trustStorePath";

  /**
   * The value for the short option keyStorePassword.
   */
  public static final char OPTION_SHORT_KEYSTORE_PWD = 'W';

  /**
   * The value for the long option keyStorePassword.
   */
  public static final String OPTION_LONG_KEYSTORE_PWD = "keyStorePassword";

  /**
   * The value for the short option trustStorePassword.
   */
  public static final char OPTION_SHORT_TRUSTSTORE_PWD = 'T';

  /**
   * The value for the long option trustStorePassword.
   */
  public static final String OPTION_LONG_TRUSTSTORE_PWD = "trustStorePassword";

  /**
   * The value for the short option keyStorePasswordFile .
   */
  public static final char OPTION_SHORT_KEYSTORE_PWD_FILE = 'u';

  /**
   * The value for the long option keyStorePasswordFile .
   */
  public static final String OPTION_LONG_KEYSTORE_PWD_FILE =
    "keyStorePasswordFile";

  /**
   * The value for the short option keyStorePasswordFile .
   */
  public static final char OPTION_SHORT_TRUSTSTORE_PWD_FILE = 'U';

  /**
   * The value for the long option keyStorePasswordFile .
   */
  public static final String OPTION_LONG_TRUSTSTORE_PWD_FILE =
    "trustStorePasswordFile";
  /**
   * The value for the short option trustAll .
   */
  public static final char OPTION_SHORT_TRUSTALL = 'X';

  /**
   * The value for the long option trustAll .
   */
  public static final String OPTION_LONG_TRUSTALL = "trustAll";

  /**
   * The value for the short option certNickname .
   */
  public static final char OPTION_SHORT_CERT_NICKNAME = 'N';

  /**
   * The value for the long option certNickname .
   */
  public static final String OPTION_LONG_CERT_NICKNAME = "certNickname";

  /**
   * The value for the long option assertionFilter .
   */
  public static final String OPTION_LONG_ASSERTION_FILE = "assertionFilter";

  /**
   * The value for the short option dry-run.
   */
  public static final char OPTION_SHORT_DRYRUN = 'n';

  /**
   * The value for the long option dry-run.
   */
  public static final String OPTION_LONG_DRYRUN = "dry-run";

  /**
   * The value for the short option help.
   */
  public static final char OPTION_SHORT_HELP = 'H';

  /**
   * The value for the long option help.
   */
  public static final String OPTION_LONG_HELP = "help";

  /**
   * The value for the long option cli.
   */
  public static final String OPTION_LONG_CLI = "cli";

  /**
   * The value for the short option cli.
   */
  public static final char OPTION_SHORT_CLI = 'i';

  /**
   * The value for the short option proxyAs.
   */
  public static final char OPTION_SHORT_PROXYAUTHID = 'Y';

  /**
   * The value for the long option proxyAs.
   */
  public static final String OPTION_LONG_PROXYAUTHID = "proxyAs";

  /**
   * The value for the short option saslOption.
   */
  public static final char OPTION_SHORT_SASLOPTION = 'o';

  /**
   * The value for the long option saslOption.
   */
  public static final String OPTION_LONG_SASLOPTION = "saslOption";

  /**
   * The value for the short option geteffectiverights control authzid.
   */
   public static final char OPTION_SHORT_EFFECTIVERIGHTSUSER = 'g';

  /**
   * The value for the long option geteffectiverights  control authzid.
   */
   public static final String OPTION_LONG_EFFECTIVERIGHTSUSER =
          "getEffectiveRightsAuthzid";

  /**
   * The value for the short option geteffectiveights control attributes.
   */
   public static final char OPTION_SHORT_EFFECTIVERIGHTSATTR = 'e';

  /**
   * The value for the long option geteffectiverights control specific
   * attribute list.
   */
   public static final String OPTION_LONG_EFFECTIVERIGHTSATTR =
          "getEffectiveRightsAttribute";

   /**
    * The value for the short option protocol version attributes.
    */
    public static final char OPTION_SHORT_PROTOCOL_VERSION = 'V';

   /**
    * The value for the long option protocol version
    * attribute.
    */
    public static final String OPTION_LONG_PROTOCOL_VERSION  =
           "ldapVersion";

    /**
     * The value for the long option version.
     */
     public static final char OPTION_SHORT_PRODUCT_VERSION = 'V';

    /**
     * The value for the long option version.
     */
     public static final String OPTION_LONG_PRODUCT_VERSION  = "version";

  /**
   * The value for the short option description attributes.
   */
  public static final char OPTION_SHORT_DESCRIPTION = 'd';

  /**
   * The value for the long option description attribute.
   */
  public static final String OPTION_LONG_DESCRIPTION = "description";

  /**
   * The value for the short option groupName attributes.
   */
  public static final char OPTION_SHORT_GROUPNAME = 'g';

  /**
   * The value for the long option groupName
   * attribute.
   */
  public static final String OPTION_LONG_GROUPNAME= "groupName";

  /**
   * The value for the short option newGroupName attribute.
   */
  public static final char OPTION_SHORT_NEWGROUPNAME = 'n';

  /**
   * The value for the long option groupName
   * attribute.
   */
  public static final String OPTION_LONG_NEWGROUPNAME= "newGroupName";

  /**
   * The value for the short option member-name attributes.
   */
  public static final char OPTION_SHORT_MEMBERNAME = 'm';

  /**
   * The value for the long member-name version
   * attribute.
   */
  public static final String OPTION_LONG_MEMBERNAME= "memberName";

  /**
   * The value for the short option backendName attributes.
   */
  public static final char OPTION_SHORT_BACKENDNAME = 'b';

  /**
   * The value for the long option backendName
   * attribute.
   */
  public static final String OPTION_LONG_BACKENDNAME= "backendName";

  /**
   * The value for the short option serverID attributes.
   */
  public static final String OPTION_SHORT_SERVERID = null;

  /**
   * The value for the long option serverID
   * attribute.
   */
  public static final String OPTION_LONG_SERVERID= "serverID";

  /**
   * The value for the short option userID attributes.
   */
  public static final String OPTION_SHORT_USERID = null;

  /**
   * The value for the long option userID
   * attribute.
   */
  public static final String OPTION_LONG_USERID= "userID";

  /**
   * The value for the short option set.
   */
  public static final Character OPTION_SHORT_SET = null;

  /**
  * The value for the long option set.
  */
 public static final String OPTION_LONG_SET = "set";

  /**
   * Value for the server root option short form.
   */
  public static final Character OPTION_SHORT_SERVER_ROOT = 'R';

  /**
   * Value for the server root option long form.
   */
  public static final String OPTION_LONG_SERVER_ROOT = "serverRoot";

  /**
   * Value for the quiet option short form.
   */
  public static final Character OPTION_SHORT_QUIET = 'Q';

  /**
   * Value for the quiet option long form.
   */
  public static final String OPTION_LONG_QUIET = "quiet";

  /**
   * Value for noninteractive session short form.
   */
  public static final Character OPTION_SHORT_NO_PROMPT = 'n';

  /**
   * Value for noninteractive session long form.
   */
  public static final String OPTION_LONG_NO_PROMPT = "no-prompt";

  /**
   * Long form of script friendly option.
   */
  public static final String OPTION_LONG_SCRIPT_FRIENDLY = "script-friendly";

  /**
   * Short form of script friendly option.
   */
  public static final Character OPTION_SHORT_SCRIPT_FRIENDLY = 's';

  /**
   * Value for verbose option short form.
   */
  public static final Character OPTION_SHORT_VERBOSE = 'v';

  /**
   * Value for verbose option long form.
   */
  public static final String OPTION_LONG_VERBOSE = "verbose";

  /**
   * Scheduled start date/time option long form.
   */
  public static final String OPTION_LONG_START_DATETIME = "start";

  /**
   * Scheduled start date/time option short form.
   */
  public static final Character OPTION_SHORT_START_DATETIME = 't';

  /**
   * The value for the long option propertiesFilePAth .
   */
  public static final String OPTION_LONG_PROP_FILE_PATH = "propertiesFilePath";

  /**
   * Argument that specifies that an operation be scheduled as a
   * task but the scheduling utility should not exit until the task
   * has completed.
   */
  public static final String OPTION_LONG_SYNCHRONOUS_TASK_MODE =
          "synchronousTaskMode";

  /**
   * Argument that specifies that an operation be scheduled as a
   * task but the scheduling utility should not exit until the task
   * has completed.
   */
  public static final Character OPTION_SHORT_SYNCHRONOUS_TASK_MODE = 'Y';


  /**
   * The value for the long option propertiesFilePAth .
   */
  public static final String OPTION_LONG_NO_PROP_FILE = "noPropertiesFile";


  /**
   * Long form of referenced host name.
   */
  public static final String OPTION_LONG_REFERENCED_HOST_NAME =
          "referencedHostName";


  /**
   * Long form of admin UID.
   */
  public static final String OPTION_LONG_ADMIN_UID = "adminUID";


  /**
   * Long form of report authorization ID connection option.
   */
  public static final String OPTION_LONG_REPORT_AUTHZ_ID = "reportAuthzID";


  /**
   * Long form of use password policy control connection option.
   */
  public static final String OPTION_LONG_USE_PW_POLICY_CTL =
          "usePasswordPolicyControl";


  /**
   * Long form of use SASL external connection option.
   */
  public static final String OPTION_LONG_USE_SASL_EXTERNAL = "useSASLExternal";


  /**
   * Long form of option for the command-line encoding option.
   */
  public static final String OPTION_LONG_ENCODING = "encoding";


  /**
   * Long form of option specifying no wrapping of the command-line.
   */
  public static final String OPTION_LONG_DONT_WRAP = "dontWrap";


  /**
   * Long form of email notification upon completion option.
   */
  public static final String OPTION_LONG_COMPLETION_NOTIFICATION_EMAIL =
          "completionNotify";

  /**
   * Short form of email notification upon completion option.
   */
  public static final Character OPTION_SHORT_COMPLETION_NOTIFICATION_EMAIL =
          null;


  /**
   * Long form of email notification upon error option.
   */
  public static final String OPTION_LONG_ERROR_NOTIFICATION_EMAIL =
          "errorNotify";


  /**
   * Short form of email notification upon error option.
   */
  public static final Character OPTION_SHORT_ERROR_NOTIFICATION_EMAIL =
          null;


  /**
   * Long form of dependency option.
   */
  public static final String OPTION_LONG_DEPENDENCY = "dependency";


  /**
   * Short form of dependency option.
   */
  public static final Character OPTION_SHORT_DEPENDENCY = null;


  /**
   * Long form of failed dependency action option.
   */
  public static final String OPTION_LONG_FAILED_DEPENDENCY_ACTION =
          "failedDependencyAction";


  /**
   * Short form of failed dependency action option.
   */
  public static final Character OPTION_SHORT_FAILED_DEPENDENCY_ACTION = null;


  /**
   * The default separator to be used in tables.
   */
  public static final String LIST_TABLE_SEPARATOR = ":";

  /**
   * Display the equivalent non-interactive command.
   */
  public static final String OPTION_DSCFG_LONG_DISPLAY_EQUIVALENT =
    "displayCommand";

  /**
   * The path where we write the equivalent non-interactive command.
   */
  public static final String OPTION_LONG_EQUIVALENT_COMMAND_FILE_PATH =
    "commandFilePath";
}

