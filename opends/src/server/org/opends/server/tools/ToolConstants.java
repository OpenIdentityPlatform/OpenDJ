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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
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
   * The value for the short option configClass.
   */
  public static final char OPTION_SHORT_CONFIG_CLASS = 'C';

  /**
   * The value for the long option configClass.
   */
  public static final String OPTION_LONG_CONFIG_CLASS = "configClass";

  /**
   * The placeholder value of configClass that will be displayed in usage
   * information.
   */
  public static final String OPTION_VALUE_CONFIG_CLASS = "{configClass}";

  /**
   * The value for the short option hostname.
   */
  public static final char OPTION_SHORT_HOST = 'h';

  /**
   * The value for the long option hostname.
   */
  public static final String OPTION_LONG_HOST = "hostname";

  /**
   * The placeholder value of hostname that will be displayed in usage
   * information.
   */
  public static final String OPTION_VALUE_HOST = "{host}";

  /**
   * The value for the short option port.
   */
  public static final char OPTION_SHORT_PORT = 'p';

  /**
   * The value for the long option port.
   */
  public static final String OPTION_LONG_PORT = "port";

  /**
   * The placeholder value of port that will be displayed in usage
   * information.
   */
  public static final String OPTION_VALUE_PORT = "{port}";

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
   * The placeholder value of baseDN that will be displayed in usage
   * information.
   */
  public static final String OPTION_VALUE_BASEDN = "{baseDN}";

  /**
   * The value for the short option rootUserDN.
   */
  public static final char OPTION_SHORT_ROOT_USER_DN = 'D';

  /**
   * The value for the long option rootUserDN.
   */
  public static final String OPTION_LONG_ROOT_USER_DN = "rootUserDN";

  /**
   * The placeholder value of hostname that will be displayed in usage
   * information.
   */
  public static final String OPTION_VALUE_ROOT_USER_DN = "{rootUserDN}";

  /**
   * The value for the short option bindDN.
   */
  public static final char OPTION_SHORT_BINDDN = 'D';

  /**
   * The value for the long option bindDN.
   */
  public static final String OPTION_LONG_BINDDN = "bindDN";

  /**
   * The placeholder value of bindDN that will be displayed in usage
   * information.
   */
  public static final String OPTION_VALUE_BINDDN = "{bindDN}";

  /**
   * The value for the short option bindPassword.
   */
  public static final char OPTION_SHORT_BINDPWD = 'w';

  /**
   * The value for the long option bindPassword.
   */
  public static final String OPTION_LONG_BINDPWD = "bindPassword";

  /**
   * The placeholder value of bindPassword that will be displayed in usage
   * information.
   */
  public static final String OPTION_VALUE_BINDPWD = "{bindPassword}";

  /**
   * The value for the short option bindPasswordFile.
   */
  public static final char OPTION_SHORT_BINDPWD_FILE = 'j';

  /**
   * The value for the long option bindPasswordFile.
   */
  public static final String OPTION_LONG_BINDPWD_FILE = "bindPasswordFile";

  /**
   * The placeholder value of bindPasswordFile that will be displayed in usage
   * information.
   */
  public static final String OPTION_VALUE_BINDPWD_FILE = "{bindPasswordFile}";


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
   * The placeholder value of filename that will be displayed in usage
   * information.
   */
  public static final String OPTION_VALUE_FILENAME = "{filename}";

  /**
   * The value for the short option ldifFile.
   */
  public static final char OPTION_SHORT_LDIF_FILE = 'l';

  /**
   * The value for the long option ldifFile.
   */
  public static final String OPTION_LONG_LDIF_FILE = "ldifFile";

  /**
   * The placeholder value of ldifFile that will be displayed in usage
   * information.
   */
  public static final String OPTION_VALUE_LDIF_FILE = "{ldifFile}";

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
   * The placeholder value of randomSeed that will be displayed in usage
   * information.
   */
  public static final String OPTION_VALUE_RANDOM_SEED = "{seed}";

  /**
   * The value for the short option keyStorePath.
   */
  public static final char OPTION_SHORT_KEYSTOREPATH = 'K';

  /**
   * The value for the long option keyStorePath.
   */
  public static final String OPTION_LONG_KEYSTOREPATH = "keyStorePath";

  /**
   * The placeholder value of keyStorePath that will be displayed in usage
   * information.
   */
  public static final String OPTION_VALUE_KEYSTOREPATH = "{keyStorePath}";

  /**
   * The value for the short option trustStorePath.
   */
  public static final char OPTION_SHORT_TRUSTSTOREPATH = 'P';

  /**
   * The value for the long option trustStorePath.
   */
  public static final String OPTION_LONG_TRUSTSTOREPATH = "trustStorePath";

  /**
   * The placeholder value of trustStorePath that will be displayed in usage
   * information.
   */
  public static final String OPTION_VALUE_TRUSTSTOREPATH = "{trustStorePath}";

  /**
   * The value for the short option keyStorePassword.
   */
  public static final char OPTION_SHORT_KEYSTORE_PWD = 'W';

  /**
   * The value for the long option keyStorePassword.
   */
  public static final String OPTION_LONG_KEYSTORE_PWD = "keyStorePassword";

  /**
   * The placeholder value of keyStorePassword that will be displayed in usage
   * information.
   */
  public static final String OPTION_VALUE_KEYSTORE_PWD = "{keyStorePassword}";

  /**
   * The value for the short option trustStorePassword.
   */
  public static final char OPTION_SHORT_TRUSTSTORE_PWD = 'T';

  /**
   * The value for the long option trustStorePassword.
   */
  public static final String OPTION_LONG_TRUSTSTORE_PWD = "trustStorePassword";

  /**
   * The placeholder value of trustStorePassword that will be displayed in usage
   * information.
   */
  public static final String OPTION_VALUE_TRUSTSTORE_PWD =
    "{trustStorePassword}";

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
   * The placeholder value of keyStorePasswordFile that will be
   * displayed in usage information.
   */
  public static final String OPTION_VALUE_KEYSTORE_PWD_FILE = "{path}";

  /**
   * The value for the short option keyStorePasswordFile .
   */
  public static final char OPTION_SHORT_TRUSTSTORE_PWD_FILE = 'U';

  /**
   * The value for the long option keyStorePasswordFile .
   */
  public static final String OPTION_LONG_TRUSTSTORE_PWD_FILE =
    "TrustStorePasswordFile";

  /**
   * The placeholder value of keyStorePasswordFile that will be
   * displayed in usage information.
   */
  public static final String OPTION_VALUE_TRUSTSTORE_PWD_FILE = "{path}";

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
   * The placeholder value of certNickname that will be  displayed in usage
   * information.
   */
  public static final String OPTION_VALUE_CERT_NICKNAME = "{nickname}";

  /**
   * The value for the long option assertionFilter .
   */
  public static final String OPTION_LONG_ASSERTION_FILE = "assertionFilter";

  /**
   * The placeholder value of assertionFilter  that will be displayed in usage
   * information.
   */
  public static final String OPTION_VALUE_ASSERTION_FILE = "{filter}";

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
   * The placeholder value of proxyAs  that will be
   * displayed in usage information.
   */
  public static final String OPTION_VALUE_PROXYAUTHID = "{authzID}";

  /**
   * The value for the short option saslOption.
   */
  public static final char OPTION_SHORT_SASLOPTION = 'o';

  /**
   * The value for the long option saslOption.
   */
  public static final String OPTION_LONG_SASLOPTION = "saslOption";

  /**
   * The placeholder value of saslOption that will be
   * displayed in usage information.
   */
  public static final String OPTION_VALUE_SASLOPTION = "{name=value}";

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
     * The placeholder value of protocol version that will be
     * displayed in usage information.
     */
    public static final String OPTION_VALUE_PROTOCOL_VERSION = "{version}";

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
   * The placeholder value of description that will be
   * displayed in usage information.
   */
  public static final String OPTION_VALUE_DESCRIPTION = "{description}";

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
   * The placeholder value of groupName that will be
   * displayed in usage information.
   */
  public static final String OPTION_VALUE_GROUPNAME = "{group-name}";

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
   * The placeholder value of member-name that will be
   * displayed in usage information.
   */
  public static final String OPTION_VALUE_MEMBERNAME = "{member-name}";

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
   * The placeholder value of backendName that will be
   * displayed in usage information.
   */
  public static final String OPTION_VALUE_BACKENDNAME = "{backend-name}";

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
   * The placeholder value of serverID that will be
   * displayed in usage information.
   */
  public static final String OPTION_VALUE_SERVERID = "{serverID}";

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
   * The placeholder value of userID that will be
   * displayed in usage information.
   */
  public static final String OPTION_VALUE_USERID = "{userID}";

  /**
   * The value for the short option set.
   */
  public static final Character OPTION_SHORT_SET = null;

  /**
  * The value for the long option set.
  */
 public static final String OPTION_LONG_SET = "set";

 /**
  * The placeholder value for the long option set.
  */
 public static final String OPTION_VALUE_SET = "{PROP:VAL}";

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
   * Placeholder string for the usage statement.
   */
  public static final String OPTION_VALUE_START_DATETIME = "{startTime}";

}

