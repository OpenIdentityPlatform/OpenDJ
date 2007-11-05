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
package org.opends.server.config;



import java.io.File;

import org.opends.server.types.SSLClientAuthPolicy;



/**
 * This class defines a number of constants used by the Directory Server
 * configuration, including configuration attribute and objectclass names,
 * and attribute options.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.VOLATILE,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public final class ConfigConstants
{
  /**
   * The prefix that will be applied to all custom attribute and objectclass
   * names used for holding information about a Directory Server backup.
   */
  public static final String NAME_PREFIX_BACKUP = "ds-backup-";



  /**
   * The prefix that will be applied to all custom attribute and objectclass
   * names in the Directory Server configuration.
   */
  public static final String NAME_PREFIX_CFG = "ds-cfg-";



  /**
   * The prefix that will be applied to all custom operational attributes used
   * for holding password policy state information.
   */
  public static final String NAME_PREFIX_PWP = "ds-pwp-";



  /**
   * The prefix that will be applied to all custom attributes and objectclasses
   * for holding recurring task information.
   */
  public static final String NAME_PREFIX_RECURRING_TASK = "ds-recurring-task-";



  /**
   * The prefix that will be applied to all custom operational attributes used
   * for holding resource limit information.
   */
  public static final String NAME_PREFIX_RLIM = "ds-rlim-";



  /**
   * The prefix that will be applied to all custom attributes and objectclasses
   * for holding task information.
   */
  public static final String NAME_PREFIX_TASK = "ds-task-";



  /**
   * The name of the configuration attribute that specifies the backlog to use
   * when accepting new connections.
   */
  public static final String ATTR_ACCEPT_BACKLOG =
       "ds-cfg-accept-backlog";



  /**
   * The default accept backlog to use if no value is given.
   */
  public static final int DEFAULT_ACCEPT_BACKLOG = 128;



  /**
   * The name of the configuration attribute that holds the fully-qualified name
   * for the account status notification handler class.
   */
  public static final String ATTR_ACCT_NOTIFICATION_HANDLER_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that indicates whether an account
   * status notification handler is enabled.
   */
  public static final String ATTR_ACCT_NOTIFICATION_HANDLER_ENABLED =
       "ds-cfg-enabled";



  /**
   * The name of the configuration attribute that specifies the set of account
   * status notification types that should trigger notifications.
   */
  public static final String ATTR_ACCT_NOTIFICATION_TYPE =
       "ds-cfg-account-status-notification-type";



  /**
   * The name of the configuration attribute that indicates whether to
   * automatically add missing RDN attributes or to return an error response to
   * the client.
   */
  public static final String ATTR_ADD_MISSING_RDN_ATTRS =
       "ds-cfg-add-missing-rdn-attributes";



  /**
   * The name of the configuration attribute that specifies the class that will
   * be used for an alert handler.
   */
  public static final String ATTR_ALERT_HANDLER_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that indicates whether an alert
   * handler is enabled.
   */
  public static final String ATTR_ALERT_HANDLER_ENABLED =
       "ds-cfg-enabled";



  /**
   * The name of the configuration attribute that indicates whether it will be
   * possible to allow exceptions to the strict attribute naming restrictions.
   */
  public static final String ATTR_ALLOW_ATTRIBUTE_NAME_EXCEPTIONS =
       "ds-cfg-allow-attribute-name-exceptions";



  /**
   * The name of the configuration attribute that indicates which clients
   * should be allowed to establish connections.
   */
  public static final String ATTR_ALLOWED_CLIENT =
       "ds-cfg-allowed-client";



  /**
   * The name of the configuration attribute that indicates whether LDAPv2
   * clients will be allowed to access the server.
   */
  public static final String ATTR_ALLOW_LDAPV2 =
       "ds-cfg-allow-ldap-v2";



  /**
   * The default policy that should be used for accepting LDAPv2 connections if
   * it is not defined in the configuration.
   */
  public static final boolean DEFAULT_ALLOW_LDAPV2 = true;



  /**
   * The name of the configuration attribute that indicates whether the server
   * socket should have the SO_REUSEADDR socket option set.
   */
  public static final String ATTR_ALLOW_REUSE_ADDRESS =
       "ds-cfg-allow-tcp-reuse-address";



  /**
   * The default policy for using the SO_REUSEADDR socket option if it is not
   * specified in the configuration.
   */
  public static final boolean DEFAULT_ALLOW_REUSE_ADDRESS = true;



  /**
   * The name of the configuration attribute that specifies one or more
   * alternate bind DNs for a root user.
   */
  public static final String ATTR_ROOTDN_ALTERNATE_BIND_DN =
       "ds-cfg-alternate-bind-dn";



  /**
   * The name of the configuration attribute that indicates whether the root DSE
   * should treat all attributes as user attributes or if it should treat them
   * as per their definition in the schema.
   */
  public static final String ATTR_ROOTDSE_SHOW_ALL_ATTRIBUTES =
       "ds-cfg-show-all-attributes";



  /**
   * The default value that will be used regarding treating all root DSE
   * attributes as user attributes if it is not defined in the configuration.
   */
  public static final boolean DEFAULT_ROOTDSE_SHOW_ALL_ATTRIBUTES = false;



  /**
   * The name of the configuration attribute that indicates whether the
   * subschema entry should treat all attributes as user attributes or if it
   * should treat them as per their definition in the schema.
   */
  public static final String ATTR_SCHEMA_SHOW_ALL_ATTRIBUTES =
       "ds-cfg-show-all-attributes";



  /**
   * The default value that will be used regarding treating all subschema entry
   * attributes as user attributes if it is not defined in the configuration.
   */
  public static final boolean DEFAULT_SCHEMA_SHOW_ALL_ATTRIBUTES = false;



  /**
   * The name of the configuration attribute that indicates whether to allow
   * clients to use the startTLS extended operation.
   */
  public static final String ATTR_ALLOW_STARTTLS =
       "ds-cfg-allow-start-tls";



  /**
   * The default configuration that specifies whether to allow startTLS
   * operations if it is not defined in the server configuration.
   */
  public static final boolean DEFAULT_ALLOW_STARTTLS = false;



  /**
   * The name of the configuration attribute that indicates whether to allow the
   * use of zero-length values in attributes with the directory string syntax.
   */
  public static final String ATTR_ALLOW_ZEROLENGTH_DIRECTORYSTRINGS =
       "ds-cfg-allow-zero-length-values";



  /**
   * The default configuration that specifies whether to allow zero-length
   * directory string values if it is not defined in the server configuration.
   */
  public static final boolean DEFAULT_ALLOW_ZEROLENGTH_DIRECTORYSTRINGS = false;



  /**
   * The name of the configuration attribute that holds the set of attribute
   * type definitions in the server schema, formatted in camelCase.
   */
  public static final String ATTR_ATTRIBUTE_TYPES = "attributeTypes";



  /**
   * The name of the configuration attribute that holds the set of attribute
   * type definitions in the server schema, formatted in all lowercase.
   */
  public static final String ATTR_ATTRIBUTE_TYPES_LC = "attributetypes";



  /**
   * The name of the configuration attribute that specifies the base DN(s) for a
   * backend.
   */
  public static final String ATTR_BACKEND_BASE_DN =
       "ds-cfg-base-dn";



  /**
   * The name of the configuration attribute that holds the fully-qualified name
   * of the Java class for a backend implementation.
   */
  public static final String ATTR_BACKEND_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that indicates whether a particular
   * backend is enabled.
   */
  public static final String ATTR_BACKEND_ENABLED =
       "ds-cfg-enabled";



  /**
   * The name of the configuration attribute that specifies the unique ID for a
   * backend.
   */
  public static final String ATTR_BACKEND_ID = "ds-cfg-backend-id";



  /**
   * The name of the configuration attribute that specifies the writability mode
   * for a backend.
   */
  public static final String ATTR_BACKEND_WRITABILITY_MODE =
       "ds-cfg-writability-mode";



  /**
   * The name of the configuration attribute that holds the DN of the backend
   * configuration entry with which a backup directory is associated.
   */
  public static final String ATTR_BACKUP_BACKEND_DN =
       NAME_PREFIX_BACKUP + "backend-dn";



  /**
   * The name of the configuration attribute that indicates whether a backup is
   * compressed.
   */
  public static final String ATTR_BACKUP_COMPRESSED =
       NAME_PREFIX_BACKUP + "compressed";



  /**
   * The name of the configuration attribute that holds the date that a backup
   * was made.
   */
  public static final String ATTR_BACKUP_DATE = NAME_PREFIX_BACKUP + "date";



  /**
   * The name of the configuration attribute that holds the set of dependencies
   * for a backup.
   */
  public static final String ATTR_BACKUP_DEPENDENCY =
       NAME_PREFIX_BACKUP + "dependency";



  /**
   * The name of the configuration attribute that holds the list of default
   * backup directories to search when using the backup backend.
   */
  public static final String ATTR_BACKUP_DIR_LIST =
       "ds-cfg-backup-directory";



  /**
   * The name of the configuration attribute that holds the path to a backup
   * directory.
   */
  public static final String ATTR_BACKUP_DIRECTORY_PATH =
       NAME_PREFIX_BACKUP + "directory-path";



  /**
   * The name of the configuration attribute that indicates whether a backup is
   * encrypted.
   */
  public static final String ATTR_BACKUP_ENCRYPTED =
       NAME_PREFIX_BACKUP + "encrypted";



  /**
   * The name of the configuration attribute that holds the backup ID.
   */
  public static final String ATTR_BACKUP_ID = NAME_PREFIX_BACKUP + "id";



  /**
   * The name of the configuration attribute that indicates whether a backup is
   * an incremental backup.
   */
  public static final String ATTR_BACKUP_INCREMENTAL =
       NAME_PREFIX_BACKUP + "incremental";



  /**
   * The name of the configuration attribute that holds the signed hash for a
   * backup.
   */
  public static final String ATTR_BACKUP_SIGNED_HASH =
       NAME_PREFIX_BACKUP + "signed-hash";



  /**
   * The name of the configuration attribute that holds the unsigned hash for a
   * backup.
   */
  public static final String ATTR_BACKUP_UNSIGNED_HASH =
       NAME_PREFIX_BACKUP + "unsigned-hash";



  /**
   * The name of the configuration attribute that indicates whether simple binds
   * containing a DN must also contain a password.
   */
  public static final String ATTR_BIND_WITH_DN_REQUIRES_PW =
       "ds-cfg-bind-with-dn-requires-password";



  /**
   * The default value for the bind with DN requires password configuration
   * attribute.
   */
  public static final boolean DEFAULT_BIND_WITH_DN_REQUIRES_PW = true;



  /**
   * The name of the configuration attribute that indicates whether an
   * unauthenticated request should be rejected.
   */
  public static final String ATTR_REJECT_UNAUTHENTICATED_REQ =
       "ds-cfg-reject-unauthenticated-requests";


  /**
   * The default value for the reject unauthenticated request attribute.
   */
  public static final boolean DEFAULT_REJECT_UNAUTHENTICATED_REQ = false;



  /**
   * The name of the configuration attribute that holds the name of the
   * attribute type that should be used when mapping a certificate fingerprint
   * to a user entry.
   */
  public static final String ATTR_CERTIFICATE_FINGERPRINT_ATTR =
       "ds-cfg-fingerprint-attribute";



  /**
   * The name of the configuration attribute that holds the name of the
   * algorithm that should be used to generate the certificate fingerprint.
   */
  public static final String ATTR_CERTIFICATE_FINGERPRINT_ALGORITHM =
       "ds-cfg-fingerprint-algorithm";



  /**
   * The name of the configuration attribute that holds the name of the
   * attribute type that should be used when mapping a certificate subject to a
   * user entry.
   */
  public static final String ATTR_CERTIFICATE_SUBJECT_ATTR =
       "ds-cfg-subject-attribute";



  /**
   * The name of the configuration attribute that holds the name of the
   * attribute type that should be used when mapping attributes in a certificate
   * subject to a user entry.
   */
  public static final String ATTR_CERTIFICATE_SUBJECT_ATTR_MAP =
       "ds-cfg-subject-attribute-mapping";



  /**
   * The name of the configuration attribute that holds the name of the
   * attribute type that should be used when mapping a certificate subject to a
   * user entry.
   */
  public static final String ATTR_CERTIFICATE_SUBJECT_BASEDN =
       "ds-cfg-user-base-dn";



  /**
   * The name of the configuration attribute that holds the fully-qualified name
   * of the Java class for the certificate mapper implementation.
   */
  public static final String ATTR_CERTMAPPER_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that holds the DN of a certificate
   * mapper configuration entry.
   */
  public static final String ATTR_CERTMAPPER_DN =
       "ds-cfg-certificate-mapper";



  /**
   * The name of the configuration attribute that indicates whether the
   * certificate mapper is enabled.
   */
  public static final String ATTR_CERTMAPPER_ENABLED =
       "ds-cfg-enabled";



  /**
   * The name of the configuration attribute that indicates whether schema
   * checking should be enabled in the server.
   */
  public static final String ATTR_CHECK_SCHEMA =
       "ds-cfg-check-schema";



  /**
   * The name of the configuration attribute that specifies the manner in which
   * SSL client certificates may be validated against certificates in the
   * corresponding user's entry during SASL EXTERNAL authentication.
   */
  public static final String ATTR_CLIENT_CERT_VALIDATION_POLICY =
       "ds-cfg-certificate-validation-policy";



  /**
   * The name of the configuration attribute that holds the fully-qualified name
   * of the Java class for the connection handler implementation.
   */
  public static final String ATTR_CONNECTION_HANDLER_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that indicates whether a particular
   * connection handler is enabled.
   */
  public static final String ATTR_CONNECTION_HANDLER_ENABLED =
       "ds-cfg-enabled";



  /**
   * The name of the configuration attribute that specifies the DN of the
   * default password policy for the Directory Server.
   */
  public static final String ATTR_DEFAULT_PWPOLICY_DN =
       "ds-cfg-default-password-policy";



  /**
   * The name of the configuration attribute that specifies the set of
   * privileges that root users should automatically be granted in the server.
   */
  public static final String ATTR_DEFAULT_ROOT_PRIVILEGE_NAME =
       "ds-cfg-default-root-privilege-name";



  /**
   * The name of the configuration attribute that indicates which clients
   * should not be allowed to establish connections.
   */
  public static final String ATTR_DENIED_CLIENT =
       "ds-cfg-denied-client";



  /**
   * The name of the configuration attribute that specifies the realm that
   * should be used for DIGEST-MD5 authentication.
   */
  public static final String ATTR_DIGESTMD5_REALM = "ds-cfg-realm";



  /**
   * The name of the attribute that is used to hold the DIT content rule
   * definitions in the server schema, formatted in camelCase.
   */
  public static final String ATTR_DIT_CONTENT_RULES = "dITContentRules";



  /**
   * The name of the attribute that is used to hold the DIT content rule
   * definitions in the server schema, formatted in all lowercase.
   */
  public static final String ATTR_DIT_CONTENT_RULES_LC = "ditcontentrules";



  /**
   * The name of the attribute that is used to hold the DIT structure rule
   * definitions in the server schema, formatted in camelCase.
   */
  public static final String ATTR_DIT_STRUCTURE_RULES = "dITStructureRules";



  /**
   * The name of the attribute that is used to hold the DIT structure rule
   * definitions in the server schema, formatted in all lowercase.
   */
  public static final String ATTR_DIT_STRUCTURE_RULES_LC = "ditstructurerules";



  /**
   * The name of the configuration attribute that holds the fully-qualified name
   * of the Java class for the entry cache implementation.
   */
  public static final String ATTR_ENTRYCACHE_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that indicates whether the entry
   * cache is enabled.
   */
  public static final String ATTR_ENTRYCACHE_ENABLED =
       "ds-cfg-enabled";



  /**
   * The name of the configuration attribute that holds the fully-qualified name
   * for the extended operation handler class.
   */
  public static final String ATTR_EXTOP_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that indicates whether an extended
   * operation handler should be enabled.
   */
  public static final String ATTR_EXTOP_ENABLED =
       "ds-cfg-enabled";



  /**
   * The name of the configuration attribute that contains a set of search
   * filters to use to determine which entries should be excluded from the
   * cache.
   */
  public static final String ATTR_FIFOCACHE_EXCLUDE_FILTER =
       "ds-cfg-exclude-filter";



  /**
   * The name of the configuration attribute that contains a set of search
   * filters to use to determine which entries should be included in the cache.
   */
  public static final String ATTR_FIFOCACHE_INCLUDE_FILTER =
       "ds-cfg-include-filter";



  /**
   * The name of the configuration attribute that indicates the maximum length
   * of time in milliseconds to spend trying to acquire a lock for an entry in
   * the cache.
   */
  public static final String ATTR_FIFOCACHE_LOCK_TIMEOUT =
       "ds-cfg-lock-timeout";



  /**
   * The default value for the entry cache lockout timeout that will be used if
   * no other value is specified.
   */
  public static final long DEFAULT_FIFOCACHE_LOCK_TIMEOUT = 2000L;



  /**
   * The name of the configuration attribute that indicates the maximum number
   * of entries that the FIFO entry cache will be allowed to hold.
   */
  public static final String ATTR_FIFOCACHE_MAX_ENTRIES =
       "ds-cfg-max-entries";



  /**
   * The default value for the entry cache max entries that will be used if no
   * other value is specified.
   */
  public static final long DEFAULT_FIFOCACHE_MAX_ENTRIES = Long.MAX_VALUE;



  /**
   * The name of the configuration attribute that indicates the maximum
   * percentage of available memory in the JVM that the FIFO entry cache will be
   * allowed to consume.
   */
  public static final String ATTR_FIFOCACHE_MAX_MEMORY_PCT =
       "ds-cfg-max-memory-percent";



  /**
   * The default value for the entry cache max memory percent that will be used
   * if no other value is specified.
   */
  public static final int DEFAULT_FIFOCACHE_MAX_MEMORY_PCT = 90;


  /**
   * The name of the configuration attribute that contains a set of search
   * filters to use to determine which entries should be excluded from the
   * cache.
   */
  public static final String ATTR_FSCACHE_EXCLUDE_FILTER =
       "ds-cfg-exclude-filter";

  /**
   * The name of the configuration attribute that contains a set of search
   * filters to use to determine which entries should be included in the cache.
   */
  public static final String ATTR_FSCACHE_INCLUDE_FILTER =
       "ds-cfg-include-filter";

  /**
   * The name of the configuration attribute that indicates the maximum length
   * of time in milliseconds to spend trying to acquire a lock for an entry in
   * the cache.
   */
  public static final String ATTR_FSCACHE_LOCK_TIMEOUT =
       "ds-cfg-lock-timeout";

  /**
   * The default value for the entry cache lockout timeout that will be used if
   * no other value is specified.
   */
  public static final long DEFAULT_FSCACHE_LOCK_TIMEOUT = 2000L;

  /**
   * The name of the configuration attribute that indicates the maximum number
   * of entries that the FIFO entry cache will be allowed to hold.
   */
  public static final String ATTR_FSCACHE_MAX_ENTRIES =
       "ds-cfg-max-entries";

  /**
   * The default value for the entry cache max entries that will be used if no
   * other value is specified.
   */
  public static final long DEFAULT_FSCACHE_MAX_ENTRIES = Long.MAX_VALUE;

  /**
   * The name of the configuration attribute that indicates the maximum
   * memory size of the FS entry cache.
   */
  public static final String ATTR_FSCACHE_MAX_MEMORY_SIZE =
       "ds-cfg-max-memory-size";

  /**
   * The name of the configuration attribute that specifies the entry cache JE
   * environment home.
   */
  public static final String ATTR_FSCACHE_HOME =
      "ds-cfg-cache-directory";

  /**
   * The default value for the entry cache JE environment home that will be used
   * if no other value is specified.
   */
  public static final String DEFAULT_FSCACHE_HOME = "/tmp/OpenDS.FSCache";

  /**
   * The name of the configuration attribute that indicates the maximum
   * available space in bytes in the file system that JE cache will be
   * allowed to consume.
   */
  public static final String ATTR_FSCACHE_JE_CACHE_SIZE =
       "ds-cfg-db-cache-size";

  /**
   * The default value for the JE cache size in bytes that will be used
   * if no other value is specified.
   */
  public static final long DEFAULT_FSCACHE_JE_CACHE_SIZE = 0;

  /**
   * The name of the configuration attribute that indicates the maximum
   * available memory percent that JE cache can consume.
   */
  public static final String ATTR_FSCACHE_JE_CACHE_PCT =
       "ds-cfg-db-cache-percent";

  /**
   * The default value for the JE cache size percent that will be used
   * if no other value is specified.
   */
  public static final int DEFAULT_FSCACHE_JE_CACHE_PCT = 0;

  /**
   * The name of the configuration attribute that indicates whether
   * file system entry cache is configured as persistent or not.
   */
  public static final String ATTR_FSCACHE_IS_PERSISTENT =
       "ds-cfg-persistent-cache";

  /**
   * The default value to indicate whether the cache is persistent or not.
   */
  public static final boolean DEFAULT_FSCACHE_IS_PERSISTENT = false;

  /**
   * The default value to indicate which cache type to use.
   */
  public static final String DEFAULT_FSCACHE_TYPE = "FIFO";

  /**
   * The name of the configuration attribute that indicates which
   * cache type will be used.
   */
  public static final String ATTR_FSCACHE_TYPE =
       "ds-cfg-cache-type";

  /**
   * The name of the configuration attribute that specifies the fully-qualified
   * class name for a group implementation.
   */
  public static final String ATTR_GROUP_IMPLEMENTATION_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that indicates whether a group
   * implementation should be enabled for use in the server.
   */
  public static final String ATTR_GROUP_IMPLEMENTATION_ENABLED =
       "ds-cfg-enabled";



  /**
   * The name of the configuration attribute that holds the address of the KDC
   * to use when processing SASL GSSAPI binds.
   */
  public static final String ATTR_GSSAPI_KDC = "ds-cfg-kdc-address";



  /**
   * The name of the configuration attribute that holds the path to the Kerberos
   * keytab file to use when processing SASL GSSAPI binds.
   */
  public static final String ATTR_GSSAPI_KEYTAB_FILE =
       "ds-cfg-keytab";



  /**
   * The name of the configuration attribute that holds the default Kerberos
   * realm to use when processing SASL GSSAPI binds.
   */
  public static final String ATTR_GSSAPI_REALM = "ds-cfg-realm";



  /**
   * The name of the configuration attribute that holds the fully-qualified name
   * of an identity mapper class.
   */
  public static final String ATTR_IDMAPPER_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that holds the DN of an identity
   * mapper configuration entry.
   */
  public static final String ATTR_IDMAPPER_DN =
       "ds-cfg-identity-mapper";



  /**
   * The name of the configuration attribute that indicates whether an identity
   * mapper is enabled.
   */
  public static final String ATTR_IDMAPPER_ENABLED =
       "ds-cfg-enabled";



  /**
   * The name of the configuration attribute that defines the behavior that the
   * server should use when a value is encountered that does not conform to the
   * associated attribute syntax.
   */
  public static final String ATTR_INVALID_SYNTAX_BEHAVIOR =
       "ds-cfg-invalid-attribute-syntax-behavior";



  /**
   * The name of the configuration attribute that defines the behavior that the
   * server should use when an entry is encountered that does not contain
   * exactly one structural objectclass.
   */
  public static final String ATTR_SINGLE_STRUCTURAL_CLASS_BEHAVIOR =
       "ds-cfg-single-structural-objectclass-behavior";



  /**
   * The name of the configuration attribute that holds the set of attribute
   * syntax definitions in the server schema, formatted in camelCase.
   */
  public static final String ATTR_LDAP_SYNTAXES = "ldapSyntaxes";



  /**
   * The name of the configuration attribute that holds the set of attribute
   * syntax definitions in the server schema, formatted in all lowercase.
   */
  public static final String ATTR_LDAP_SYNTAXES_LC = "ldapsyntaxes";



  /**
   * The name of the configuration attribute that indicates whether the LDAP
   * connection handler should keep statistical information.
   */
  public static final String ATTR_KEEP_LDAP_STATS =
       "ds-cfg-keep-stats";



  /**
   * Indicates whether the LDAP connection handler should keep statistical
   * information by default.
   */
  public static final boolean DEFAULT_KEEP_LDAP_STATS = true;



  /**
   * The name of the configuration attribute that specifies the fully-qualified
   * name of the class to use as the key manager provider.
   */
  public static final String ATTR_KEYMANAGER_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that specifies the DN of the
   * configuration entry for the key manager provider.
   */
  public static final String ATTR_KEYMANAGER_DN =
       "ds-cfg-key-manager-provider";



  /**
   * The name of the configuration attribute that indicates whether the key
   * manager provider should be enabled.
   */
  public static final String ATTR_KEYMANAGER_ENABLED =
       "ds-cfg-enabled";



  /**
   * The name of the configuration attribute that specifies the path to the
   * keystore file.
   */
  public static final String ATTR_KEYSTORE_FILE =
       "ds-cfg-key-store-file";



  /**
   * The name of the configuration attribute that specifies the PIN needed to
   * access the keystore.
   */
  public static final String ATTR_KEYSTORE_PIN =
       "ds-cfg-key-store-pin";



  /**
   * The name of the configuration attribute that specifies the name of the
   * environment variable containing the PIN needed to access the keystore.
   */
  public static final String ATTR_KEYSTORE_PIN_ENVAR =
       "ds-cfg-key-store-pin-environment-variable";



  /**
   * The name of the configuration attribute that specifies the path to the file
   * containing the PIN needed to access the keystore.
   */
  public static final String ATTR_KEYSTORE_PIN_FILE =
       "ds-cfg-key-store-pin-file";



  /**
   * The name of the configuration attribute that specifies the name of the Java
   * property containing the PIN needed to access the keystore.
   */
  public static final String ATTR_KEYSTORE_PIN_PROPERTY =
  "ds-cfg-key-store-pin-property";



  /**
   * The name of the configuration attribute that specifies the format of the
   * data in the keystore file.
   */
  public static final String ATTR_KEYSTORE_TYPE =
       "ds-cfg-key-store-type";



  /**
   * The name of the configuration attribute that specifies the fully-qualified
   * name of the class to use as the trust manager provider.
   */
  public static final String ATTR_TRUSTMANAGER_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that specifies the DN of the
   * configuration entry for the trust manager provider.
   */
  public static final String ATTR_TRUSTMANAGER_DN =
       "ds-cfg-trust-manager-provider";



  /**
   * The name of the configuration attribute that indicates whether the trust
   * manager provider should be enabled.
   */
  public static final String ATTR_TRUSTMANAGER_ENABLED =
       "ds-cfg-enabled";



  /**
   * The name of the configuration attribute that specifies the path to the
   * trust store file.
   */
  public static final String ATTR_TRUSTSTORE_FILE =
       "ds-cfg-trust-store-file";



  /**
   * The name of the configuration attribute that specifies the PIN needed to
   * access the trust store.
   */
  public static final String ATTR_TRUSTSTORE_PIN =
       "ds-cfg-trust-store-pin";



  /**
   * The name of the configuration attribute that specifies the name of the
   * environment variable containing the PIN needed to access the trust store.
   */
  public static final String ATTR_TRUSTSTORE_PIN_ENVAR =
       "ds-cfg-trust-store-pin-environment-variable";



  /**
   * The name of the configuration attribute that specifies the path to the file
   * containing the PIN needed to access the trust store.
   */
  public static final String ATTR_TRUSTSTORE_PIN_FILE =
       "ds-cfg-trust-store-pin-file";



  /**
   * The name of the configuration attribute that specifies the name of the Java
   * property containing the PIN needed to access the trust store.
   */
  public static final String ATTR_TRUSTSTORE_PIN_PROPERTY =
       "ds-cfg-trust-store-pin-property";



  /**
   * The name of the configuration attribute that specifies the format of the
   * data in the trust store file.
   */
  public static final String ATTR_TRUSTSTORE_TYPE =
       "ds-cfg-trust-store-type";



  /**
   * The name of the configuration attribute that specifies the address or set
   * of addresses on which a connection handler should listen.
   */
  public static final String ATTR_LISTEN_ADDRESS =
       "ds-cfg-listen-address";



  /**
   * The name of the configuration attribute that specifies the port or set of
   * ports on which a connection handler should listen.
   */
  public static final String ATTR_LISTEN_PORT = "ds-cfg-listen-port";

  /**
   * The attribute that specifies if internal operations should be logged
   * or not.
   */
  public static final String ATTR_LOGGER_SUPPRESS_INTERNAL_OPERATIONS =
       "ds-cfg-suppress-internal-operations";


  /**
   * The policy type for rotating log files.
   */
  public static final String ATTR_LOGGER_ROTATION_POLICY =
       "ds-cfg-rotation-policy";

  /**
   * The policy type for retaining log files.
   */
  public static final String ATTR_LOGGER_RETENTION_POLICY =
       "ds-cfg-retention-policy";

  /**
   * The number of files to retain attribute type.
   */
  public static final String ATTR_LOGGER_RETENTION_NUMBER_OF_FILES =
       "ds-cfg-number-of-files";

  /**
   * The disk space used attribute.
   */
  public static final String ATTR_LOGGER_RETENTION_DISK_SPACE_USED =
       "ds-cfg-disk-space-used";

  /**
   * The free disk space attribute.
   */
  public static final String ATTR_LOGGER_RETENTION_FREE_DISK_SPACE =
       "ds-cfg-free-disk-space";


  /**
   * The size limit for the size based rotation policy.
   */
  public static final String ATTR_LOGGER_ROTATION_SIZE_LIMIT =
       "ds-cfg-size-limit";


  /**
   * The time of day for the time of day based rotation policy.
   */
  public static final String ATTR_LOGGER_ROTATION_TIME_OF_DAY =
       "ds-cfg-time-of-day";



  /**
   * The action to be taken at the time of rotation.
   */
  public static final String ATTR_LOGGER_ROTATION_ACTION =
       "ds-cfg-rotation-action";


  /**
   * The time interval for the logger thread to sleep.
   */
  public static final String ATTR_LOGGER_THREAD_INTERVAL =
       "ds-cfg-time-interval";


  /**
   * The time interval for the logger thread to sleep.
   */
  public static final String ATTR_LOGGER_BUFFER_SIZE =
       "ds-cfg-buffer-size";



  /**
   * The name of the configuration attribute that holds the fully-qualified name
   * for the logger class.
   */
  public static final String ATTR_LOGGER_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that indicates whether a Directory
   * Server logger should be enabled.
   */
  public static final String ATTR_LOGGER_ENABLED =
       "ds-cfg-enabled";



  /**
   * The name of the configuration attribute that specifies the attribute or set
   * of attributes that should be used when attempting to map an ID string to
   * a user entry.
   */
  public static final String ATTR_MATCH_ATTRIBUTE =
       "ds-cfg-match-attribute";



  /**
   * The name of the configuration attribute that specifies the base DN(s) that
   * should be used when attempting to map an ID string to a user entry.
   */
  public static final String ATTR_MATCH_BASE =
       "ds-cfg-match-base-dn";



  /**
   * The name of the configuration attribute that holds the set of matching rule
   * definitions in the server schema, formatted in camelCase.
   */
  public static final String ATTR_MATCHING_RULES = "matchingRules";



  /**
   * The name of the configuration attribute that holds the set of matching rule
   * definitions in the server schema, formatted in all lowercase.
   */
  public static final String ATTR_MATCHING_RULES_LC = "matchingrules";



  /**
   * The name of the configuration attribute that holds the set of matching rule
   * use definitions in the server schema, formatted in camelCase.
   */
  public static final String ATTR_MATCHING_RULE_USE = "matchingRuleUse";



  /**
   * The name of the configuration attribute that holds the set of matching rule
   * use definitions in the server schema, formatted in all lowercase.
   */
  public static final String ATTR_MATCHING_RULE_USE_LC = "matchingruleuse";

  /**
   * The name of the attribute that holds the synchronization state,
   * formatted in lowercase.
   */
  public static final String ATTR_SYNCHRONIZATION_STATE_LC = "ds-sync-state";

  /**
   * The name of the attribute that holds the replication generationId,
   * formatted in lowercase.
   */
  public static final String ATTR_SYNCHRONIZATION_GENERATIONID_LC =
       "ds-sync-generation-id";

  /**
   * The default maximum request size that should be used if none is specified
   * in the configuration.
   */
  public static final int DEFAULT_MAX_REQUEST_SIZE = (5 * 1024 * 1024); // 5 MB



  /**
   * The name of the configuration attribute that specifies the fully-qualified
   * name of the Java class that defines a Directory Server matching rule.
   */
  public static final String ATTR_MATCHING_RULE_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that indicates whether a matching
   * rule should be enabled.
   */
  public static final String ATTR_MATCHING_RULE_ENABLED =
       "ds-cfg-enabled";



  /**
   * The name of the configuration attribute that indicates the maximum number
   * of client connections that may be established at any given time.
   */
  public static final String ATTR_MAX_ALLOWED_CONNS =
       "ds-cfg-max-allowed-client-connections";



  /**
   * The name of the configuration attribute that indicates the maximum allowed
   * size of a request in bytes.
   */
  public static final String ATTR_MAX_REQUEST_SIZE =
       "ds-cfg-max-request-size";



  /**
   * The name of the configuration attribute that indicates the maximum number
   * of pending operations that may be in the work queue at any given time.
   */
  public static final String ATTR_MAX_WORK_QUEUE_CAPACITY =
       "ds-cfg-max-work-queue-capacity";



  /**
   * The default maximum capacity that should be used for the work queue if none
   * is specified in the configuration.
   */
  public static final int DEFAULT_MAX_WORK_QUEUE_CAPACITY = 0;



  /**
   * The name of the configuration attribute that holds the fully-qualified name
   * for the monitor provider class.
   */
  public static final String ATTR_MONITOR_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that indicates whether a monitor
   * provider should be enabled.
   */
  public static final String ATTR_MONITOR_ENABLED =
       "ds-cfg-enabled";



  /**
   * The name of the attribute that is used to hold the name form definitions in
   * the server schema, formatted in camelCase.
   */
  public static final String ATTR_NAME_FORMS = "nameForms";



  /**
   * The name of the attribute that is used to hold the name form definitions in
   * the server schema, formatted in all lowercase.
   */
  public static final String ATTR_NAME_FORMS_LC = "nameforms";



  /**
   * The name of the configuration attribute that indicates whether to send a
   * response to operations that have been abandoned.
   */
  public static final String ATTR_NOTIFY_ABANDONED_OPS =
       "ds-cfg-notify-abandoned-operations";



  /**
   * The name of the configuration attribute that indicates the number of
   * request handlers that should be used to read requests from clients.
   */
  public static final String ATTR_NUM_REQUEST_HANDLERS =
       "ds-cfg-num-request-handlers";



  /**
   * The default number of request handler threads to use if it is not specified
   * in the configuration.
   */
  public static final int DEFAULT_NUM_REQUEST_HANDLERS = 1;



  /**
   * The name of the configuration attribute that indicates the number of worker
   * threads that should be used to process requests.
   */
  public static final String ATTR_NUM_WORKER_THREADS =
       "ds-cfg-num-worker-threads";



  /**
   * The default number of worker threads that should be used if no value is
   * specified in the configuration.
   */
  public static final int DEFAULT_NUM_WORKER_THREADS = 24;



  /**
   * The name of the standard attribute that holds the objectclass values for
   * the entry, formatted in camelCase.
   */
  public static final String ATTR_OBJECTCLASS = "objectClass";



  /**
   * The name of the configuration attribute that holds the set of objectclass
   * definitions in the server schema, formatted in camelCase.
   */
  public static final String ATTR_OBJECTCLASSES = "objectClasses";



  /**
   * The name of the configuration attribute that holds the set of objectclass
   * definitions in the server schema, formatted in all lowercase.
   */
  public static final String ATTR_OBJECTCLASSES_LC = "objectclasses";



  /**
   * The name of the configuration attribute that specifies a character set that
   * can be used with a password.
   */
  public static final String ATTR_PASSWORD_CHARSET =
       "ds-cfg-password-character-set";



  /**
   * The name of the configuration attribute that specifies the format that
   * should be used for generating a password.
   */
  public static final String ATTR_PASSWORD_FORMAT =
       "ds-cfg-password-format";



  /**
   * The name of the configuration attribute that specifies the maximum allowed
   * length for a password.
   */
  public static final String ATTR_PASSWORD_MAX_LENGTH =
       "ds-cfg-max-password-length";



  /**
   * The name of the configuration attribute that specifies the minimum allowed
   * length for a password.
   */
  public static final String ATTR_PASSWORD_MIN_LENGTH =
       "ds-cfg-min-password-length";

  /**
   * The name of the configuration attribute that specifies the minimum allowed
   * difference for a password.
   */
  public static final String ATTR_PASSWORD_MIN_DIFFERENCE =
       "ds-cfg-min-password-difference";


  /**
   * The name of the configuration attribute that holds the fully-qualified name
   * of the Java class for a plugin implementation.
   */
  public static final String ATTR_PLUGIN_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that indicates whether a particular
   * plugin is enabled.
   */
  public static final String ATTR_PLUGIN_ENABLED =
       "ds-cfg-enabled";



  /**
   * The name of the configuration attribute that indicates the ways in which a
   * plugin will be used.
   */
  public static final String ATTR_PLUGIN_TYPE =
       "ds-cfg-plugin-type";



  /**
   * The name of the configuration attribute that may be modified in order to
   * cause the profiler to take some action (e.g., starting or stopping
   * collection).
   */
  public static final String ATTR_PROFILE_ACTION =
      "ds-cfg-profile-action";



  /**
   * The name of the configuration attribute that indicates whether the
   * Directory Server profiler plugin should be automatically enabled when the
   * server is starting.
   */
  public static final String ATTR_PROFILE_AUTOSTART =
       "ds-cfg-enable-profiling-on-startup";



  /**
   * The name of the configuration attribute that holds the path to the
   * directory into which profile information will be written.
   */
  public static final String ATTR_PROFILE_DIR =
       "ds-cfg-profile-directory";



  /**
   * The name of the configuration attribute that holds the profile sample
   * interval in milliseconds.
   */
  public static final String ATTR_PROFILE_INTERVAL =
       "ds-cfg-profile-sample-interval";



  /**
   * The default sample interval in milliseconds to use when profiling if no
   * other value is specified.
   */
  public static final long DEFAULT_PROFILE_INTERVAL = 10;



  /**
   * The name of the read-only configuration attribute that holds the current
   * state of the profiler.
   */
  public static final String ATTR_PROFILE_STATE =
       "ds-cfg-profiler-state";



  /**
   * The name of the configuration attribute that holds the DN of the identity
   * mapper configuration entry for use with the proxied authorization V2
   * control.
   */
  public static final String ATTR_PROXY_MAPPER_DN =
       "ds-cfg-proxied-authorization-identity-mapper";



  /**
   * The name of the configuration attribute that holds the fully-qualified name
   * for the password generator class.
   */
  public static final String ATTR_PWGENERATOR_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that indicates whether a password
   * generator is enabled.
   */
  public static final String ATTR_PWGENERATOR_ENABLED =
       "ds-cfg-enabled";



  /**
   * The name of the configuration attribute that indicates whether a user will
   * be allowed to change their password even if it is expired.
   */
  public static final String ATTR_PWPOLICY_ALLOW_EXPIRED_CHANGES =
       "ds-cfg-allow-expired-password-changes";



  /**
   * The default value for the allowExpiredPasswordChanges configuration
   * attribute.
   */
  public static final boolean DEFAULT_PWPOLICY_ALLOW_EXPIRED_CHANGES = false;



  /**
   * The name of the configuration attribute that indicates whether a user may
   * provide a pre-encoded password.
   */
  public static final String ATTR_PWPOLICY_ALLOW_PRE_ENCODED_PASSWORDS =
       "ds-cfg-allow-pre-encoded-passwords";



  /**
   * The default value for the allowPreEncodedPasswords configuration attribute.
   */
  public static final boolean DEFAULT_PWPOLICY_ALLOW_PRE_ENCODED_PASSWORDS =
       false;



  /**
   * The name of the configuration attribute that indicates whether user entries
   * will be allowed to have multiple values for the password attribute.
   */
  public static final String ATTR_PWPOLICY_ALLOW_MULTIPLE_PW_VALUES =
       "ds-cfg-allow-multiple-password-values";



  /**
   * The default value for the allowMultiplePasswordValues configuration
   * attribute.
   */
  public static final boolean DEFAULT_PWPOLICY_ALLOW_MULTIPLE_PW_VALUES = false;



  /**
   * The name of the configuration attribute that indicates whether users will
   * be allowed to change their own passwords.
   */
  public static final String ATTR_PWPOLICY_ALLOW_USER_CHANGE =
       "ds-cfg-allow-user-password-changes";



  /**
   * The default value for the allowUserPasswordChanges configuration attribute.
   */
  public static final boolean DEFAULT_PWPOLICY_ALLOW_USER_CHANGE = true;



  /**
   * The name of the configuration attribute that specifies the default password
   * storage schemes for a password policy.
   */
  public static final String ATTR_PWPOLICY_DEFAULT_SCHEME =
       "ds-cfg-default-password-storage-scheme";



  /**
   * The name of the configuration attribute that indicates whether a user
   * password will be allowed to expire even if they have not yet seen a warning
   * notification.
   */
  public static final String ATTR_PWPOLICY_EXPIRE_WITHOUT_WARNING =
       "ds-cfg-expire-passwords-without-warning";



  /**
   * The default value for the expirePasswordsWithoutWarning configuration
   * attribute.
   */
  public static final boolean DEFAULT_PWPOLICY_EXPIRE_WITHOUT_WARNING = false;



  /**
   * The name of the configuration attribute that indicates whether a user must
   * change their password upon first authenticating after their account is
   * created.
   */
  public static final String ATTR_PWPOLICY_FORCE_CHANGE_ON_ADD =
       "ds-cfg-force-change-on-add";



  /**
   * The default value for the forceChangeOnAdd configuration attribute.
   */
  public static final boolean DEFAULT_PWPOLICY_FORCE_CHANGE_ON_ADD = false;



  /**
   * The name of the configuration attribute that indicates whether a user must
   * change their password after it is reset by an administrator.
   */
  public static final String ATTR_PWPOLICY_FORCE_CHANGE_ON_RESET =
       "ds-cfg-force-change-on-reset";



  /**
   * The default value for the forceChangeOnReset configuration attribute.
   */
  public static final boolean DEFAULT_PWPOLICY_FORCE_CHANGE_ON_RESET = false;



  /**
   * The name of the configuration attribute that specifies the number of fixed
   * grace login attempts that a user will have.
   */
  public static final String ATTR_PWPOLICY_GRACE_LOGIN_COUNT =
       "ds-cfg-grace-login-count";



  /**
   * The default value for the graceLoginCount configuration attribute.
   */
  public static final int DEFAULT_PWPOLICY_GRACE_LOGIN_COUNT = 0;



  /**
   * The default value for the password history count configuration attribute.
   */
  public static final int DEFAULT_PWPOLICY_HISTORY_COUNT = 0;



  /**
   * The default value for the password history duration configuration
   * attribute, in seconds.
   */
  public static final int DEFAULT_PWPOLICY_HISTORY_DURATION = 0;



  /**
   * The name of the configuration attribute that specifies the maximum length
   * of time an account may remain idle.
   */
  public static final String ATTR_PWPOLICY_IDLE_LOCKOUT_INTERVAL =
       "ds-cfg-idle-lockout-interval";



  /**
   * The default value for the idleLockoutInterval configuration attribute.
   */
  public static final int DEFAULT_PWPOLICY_IDLE_LOCKOUT_INTERVAL = 0;



  /**
   * The name of the configuration attribute that specifies the attribute used
   * to hold the last login time.
   */
  public static final String ATTR_PWPOLICY_LAST_LOGIN_TIME_ATTRIBUTE =
       "ds-cfg-last-login-time-attribute";



  /**
   * The name of the configuration attribute that specifies the format string
   * used to generate the last login time.
   */
  public static final String ATTR_PWPOLICY_LAST_LOGIN_TIME_FORMAT =
       "ds-cfg-last-login-time-format";



  /**
   * The name of the configuration attribute that specifies the length of time
   * that a user will remain locked out.
   */
  public static final String ATTR_PWPOLICY_LOCKOUT_DURATION =
       "ds-cfg-lockout-duration";



  /**
   * The default value for the lockoutDuration configuration attribute.
   */
  public static final int DEFAULT_PWPOLICY_LOCKOUT_DURATION = 0;



  /**
   * The name of the configuration attribute that specifies the number of
   * authentication failures required to lock out a user account.
   */
  public static final String ATTR_PWPOLICY_LOCKOUT_FAILURE_COUNT =
       "ds-cfg-lockout-failure-count";



  /**
   * The default value for the lockoutFailureCount configuration attribute.
   */
  public static final int DEFAULT_PWPOLICY_LOCKOUT_FAILURE_COUNT = 0;



  /**
   * The name of the configuration attribute that specifies the length of time
   * in seconds that an authentication failure will be counted against a user
   * for lockout purposes.
   */
  public static final String ATTR_PWPOLICY_LOCKOUT_FAILURE_EXPIRATION_INTERVAL =
       "ds-cfg-lockout-failure-expiration-interval";



  /**
   * The default value for the lockoutFailureExpirationInterval configuration
   * attribute.
   */
  public static final int DEFAULT_PWPOLICY_LOCKOUT_FAILURE_EXPIRATION_INTERVAL =
       0;



  /**
   * The name of the configuration attribute that specifies the maximum length
   * of time allowed between password changes.
   */
  public static final String ATTR_PWPOLICY_MAXIMUM_PASSWORD_AGE =
       "ds-cfg-max-password-age";



  /**
   * The default value for the maximumPasswordAge configuration attribute.
   */
  public static final int DEFAULT_PWPOLICY_MAXIMUM_PASSWORD_AGE = 0;



  /**
   * The name of the configuration attribute that specifies the maximum length
   * of time that a user has to change their password after it has been
   * administratively reset.
   */
  public static final String ATTR_PWPOLICY_MAXIMUM_PASSWORD_RESET_AGE =
       "ds-cfg-max-password-reset-age";



  /**
   * The default value for the maximumPasswordResetAge configuration attribute.
   */
  public static final int DEFAULT_PWPOLICY_MAXIMUM_PASSWORD_RESET_AGE = 0;



  /**
   * The name of the configuration attribute that specifies the minimum length
   * of time allowed between password changes.
   */
  public static final String ATTR_PWPOLICY_MINIMUM_PASSWORD_AGE =
       "ds-cfg-min-password-age";



  /**
   * The default value for the minimumPasswordAge configuration attribute.
   */
  public static final int DEFAULT_PWPOLICY_MINIMUM_PASSWORD_AGE = 0;



  /**
   * The name of the configuration attribute that specifies the DN(s) of the
   * configuration entries for the account status notification handlers for use
   * with the password policy.
   */
  public static final String ATTR_PWPOLICY_NOTIFICATION_HANDLER =
       "ds-cfg-account-status-notification-handler";



  /**
   * The name of the configuration attribute that specifies the attribute used
   * to hold user passwords.
   */
  public static final String ATTR_PWPOLICY_PASSWORD_ATTRIBUTE =
       "ds-cfg-password-attribute";



  /**
   * The name of the configuration attribute that specifies the DN of
   * configuration entry for the password generator to use with a password
   * policy.
   */
  public static final String ATTR_PWPOLICY_PASSWORD_GENERATOR =
       "ds-cfg-password-generator";



  /**
   * The name of the configuration attribute that specifies the DN(s) of the
   * configuration entries that will hold the password validators for use with
   * the password policy.
   */
  public static final String ATTR_PWPOLICY_PASSWORD_VALIDATOR =
       "ds-cfg-password-validator";



  /**
   * The name of the configuration attribute that specifies the format strings
   * that may have been used in the past to generate last login time values.
   */
  public static final String ATTR_PWPOLICY_PREVIOUS_LAST_LOGIN_TIME_FORMAT =
       "ds-cfg-previous-last-login-time-format";



  /**
   * The name of the configuration attribute that holds the time by which all
   * users must have changed their passwords.
   */
  public static final String ATTR_PWPOLICY_REQUIRE_CHANGE_BY_TIME =
       "ds-cfg-require-change-by-time";



  /**
   * The name of the configuration attribute that indicates whether users will
   * be required to provide their current password when they choose a new
   * password.
   */
  public static final String ATTR_PWPOLICY_REQUIRE_CURRENT_PASSWORD =
       "ds-cfg-password-change-requires-current-password";



  /**
   * The default value for the passwordChangeRequiresCurrentPassword
   * configuration attribute.
   */
  public static final boolean DEFAULT_PWPOLICY_REQUIRE_CURRENT_PASSWORD = false;



  /**
   * The name of the configuration attribute that indicates whether users will
   * be required to authenticate using a secure mechanism.
   */
  public static final String ATTR_PWPOLICY_REQUIRE_SECURE_AUTHENTICATION =
       "ds-cfg-require-secure-authentication";



  /**
   * The default value for the requireSecureAuthentication configuration
   * attribute.
   */
  public static final boolean DEFAULT_PWPOLICY_REQUIRE_SECURE_AUTHENTICATION =
       false;



  /**
   * The name of the configuration attribute that indicates whether users will
   * be required to change their passwords using a secure mechanism.
   */
  public static final String ATTR_PWPOLICY_REQUIRE_SECURE_PASSWORD_CHANGES =
       "ds-cfg-require-secure-password-changes";



  /**
   * The default value for the requireSecurePasswordChanges configuration
   * attribute.
   */
  public static final boolean DEFAULT_PWPOLICY_REQUIRE_SECURE_PASSWORD_CHANGES =
       false;



  /**
   * The name of the configuration attribute that indicates whether the server
   * should perform validation on passwords set by administrators.
   */
  public static final String ATTR_PWPOLICY_SKIP_ADMIN_VALIDATION =
       "ds-cfg-skip-validation-for-administrators";



  /**
   * The default value for the skipValidationForAdministrators configuration
   * attribute.
   */
  public static final boolean DEFAULT_PWPOLICY_SKIP_ADMIN_VALIDATION = false;



  /**
   * The name of the configuration attribute that specifies the maximum length
   * of time before expiration that a user should start to receive warning
   * notifications.
   */
  public static final String ATTR_PWPOLICY_WARNING_INTERVAL =
       "ds-cfg-password-expiration-warning-interval";



  /**
   * The default value for the passwordExpirationWarningInterval configuration
   * attribute.
   */
  public static final int DEFAULT_PWPOLICY_WARNING_INTERVAL = 604800;



  /**
   * The name of the configuration attribute that holds the fully-qualified name
   * for the password storage scheme class.
   */
  public static final String ATTR_PWSCHEME_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that indicates whether a password
   * storage scheme is enabled.
   */
  public static final String ATTR_PWSCHEME_ENABLED =
       "ds-cfg-enabled";



  /**
   * The name of the configuration attribute that holds the fully-qualified name
   * for the password validator class.
   */
  public static final String ATTR_PWVALIDATOR_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that indicates whether a password
   * validator is enabled.
   */
  public static final String ATTR_PWVALIDATOR_ENABLED =
       "ds-cfg-enabled";



  /**
   * The name of the configuration attribute that holds the name of the class
   * used to provide the implementation logic for a recurring task.
   */
  public static final String ATTR_RECURRING_TASK_CLASS_NAME =
       NAME_PREFIX_RECURRING_TASK + "class-name";



  /**
   * The name of the configuration attribute that holds the recurring task ID
   * for a recurring task that may be associated with a task.
   */
  public static final String ATTR_RECURRING_TASK_ID =
       NAME_PREFIX_RECURRING_TASK + "id";



  /**
   * The name of the configuration attribute that indicates whether the
   * Directory Server should be restarted instead of shut down.
   */
  public static final String ATTR_RESTART_SERVER =
       NAME_PREFIX_TASK + "restart-server";



  /**
   * The name of the configuration attribute that specifies the set of
   * subordinate base DNs that should be used for non-base-level searches
   * against the root DSE.
   */
  public static final String ATTR_ROOT_DSE_SUBORDINATE_BASE_DN =
       "ds-cfg-subordinate-base-dn";



  /**
   * The name of the configuration attribute that holds the fully-qualified name
   * for the SASL mechanism handler class.
   */
  public static final String ATTR_SASL_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that indicates whether a SASL
   * mechanism handler should be enabled.
   */
  public static final String ATTR_SASL_ENABLED =
       "ds-cfg-enabled";



  /**
   * The name of the configuration attribute that specifies the location(s) of
   * the entries used to publish the Directory Server schema information.
   */
  public static final String ATTR_SCHEMA_ENTRY_DN =
       "ds-cfg-schema-entry-dn";



  /**
   * The name of the configuration attribute that indicates whether to send
   * rejected client connections a notice of disconnection explaining why the
   * connection was not accepted.
   */
  public static final String ATTR_SEND_REJECTION_NOTICE =
       "ds-cfg-send-rejection-notice";



  /**
   * The default policy that will be used for deciding whether to send a
   * rejection notice to clients if it is not specified in the configuration.
   */
  public static final boolean DEFAULT_SEND_REJECTION_NOTICE = true;



  /**
   * The name of the configuration attribute that will be used to indicate the
   * result code that should be used for operations that fail because of an
   * internal server error.
   */
  public static final String ATTR_SERVER_ERROR_RESULT_CODE =
       "ds-cfg-server-error-result-code";



  /**
   * The name of the configuration attribute that holds the fully-qualified
   * domain name that should be used by the server when that information is
   * needed.
   */
  public static final String ATTR_SERVER_FQDN = "ds-cfg-server-fqdn";



  /**
   * The name of the configuration attribute that holds a message that may be
   * provided for the reason the Directory Server has been requested to shut
   * down.
   */
  public static final String ATTR_SHUTDOWN_MESSAGE =
       NAME_PREFIX_TASK + "shutdown-message";



  /**
   * The name of the configuration attribute that holds the password that must
   * be provided in order to shut down the server through the tasks interface.
   */
  public static final String ATTR_SHUTDOWN_PASSWORD =
       NAME_PREFIX_TASK + "shutdown-password";



  /**
   * The name of the configuration attribute that holds the server size limit.
   */
  public static final String ATTR_SIZE_LIMIT = "ds-cfg-size-limit";



  /**
   * The default value that will be used for the server size limit if no other
   * value is given.
   */
  public static final int DEFAULT_SIZE_LIMIT = 1000;

    /**
   * The name of the configuration attribute that holds the server lookthrough
   * limit.
   */
  public static final String ATTR_LOOKTHROUGH_LIMIT =
        "ds-cfg-lookthrough-limit";



  /**
   * The default value that will be used for the server lookthrough limit if
   * no other value is given.
   */
  public static final int DEFAULT_LOOKTHROUGH_LIMIT = 5000;



  /**
   * The name of the configuration attribute that contains a set of search
   * filters to use to determine which entries should be excluded from the
   * cache.
   */
  public static final String ATTR_SOFTREFCACHE_EXCLUDE_FILTER =
       "ds-cfg-exclude-filter";



  /**
   * The name of the configuration attribute that contains a set of search
   * filters to use to determine which entries should be included in the cache.
   */
  public static final String ATTR_SOFTREFCACHE_INCLUDE_FILTER =
       "ds-cfg-include-filter";



  /**
   * The name of the configuration attribute that indicates the maximum length
   * of time in milliseconds to spend trying to acquire a lock for an entry in
   * the cache.
   */
  public static final String ATTR_SOFTREFCACHE_LOCK_TIMEOUT =
       "ds-cfg-lock-timeout";



  /**
   * The name of the configuration attribute that holds information about the
   * policy that should be used when requesting/requiring SSL client
   * authentication.
   */
  public static final String ATTR_SSL_CLIENT_AUTH_POLICY =
       "ds-cfg-ssl-client-auth-policy";



  /**
   * The default SSL client authentication policy that should be used if it is
   * not defined in the configuration.
   */
  public static final SSLClientAuthPolicy DEFAULT_SSL_CLIENT_AUTH_POLICY =
       SSLClientAuthPolicy.OPTIONAL;



  /**
   * The name of the configuration attribute that holds the nickname of the
   * certificate that should be used for accepting SSL/TLS connections.
   */
  public static final String ATTR_SSL_CERT_NICKNAME =
       "ds-cfg-ssl-cert-nickname";



  /**
   * The default SSL server certificate nickname to use if it is not defined in
   * the configuration.
   */
  public static final String DEFAULT_SSL_CERT_NICKNAME = "server-cert";



  /**
   * The name of the configuration attribute that holds the nickname of the SSL
   * cipher suites that should be allowed for use in SSL/TLS sessions.
   */
  public static final String ATTR_SSL_CIPHERS =
       "ds-cfg-ssl-cipher-suite";



  /**
   * The name of the configuration attribute that holds the nickname of the SSL
   * protocols that should be allowed for use in SSL/TLS sessions.
   */
  public static final String ATTR_SSL_PROTOCOLS =
       "ds-cfg-ssl-protocol";



  /**
   * The name of the configuration attribute that specifies the fully-qualified
   * name of the Java class that defines a Directory Server synchronization
   * provider.
   */
  public static final String ATTR_SYNCHRONIZATION_PROVIDER_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that indicates whether a
   * synchronization provider should be enabled.
   */
  public static final String ATTR_SYNCHRONIZATION_PROVIDER_ENABLED =
       "ds-cfg-enabled";



  /**
   * The name of the configuration attribute that specifies the
   * fully-qualified name of the Java class that defines a Directory
   * Server access control handler.
   */
  public static final String ATTR_AUTHZ_HANDLER_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that indicates whether
   * access control should be enabled.
   */
  public static final String ATTR_AUTHZ_HANDLER_ENABLED =
       "ds-cfg-enabled";


    /**
     * The name of the configuration attribute that specifies a global
     * attribute access control instruction.
     */
    public static final String ATTR_AUTHZ_GLOBAL_ACI =
        "ds-cfg-global-aci";


  /**
   * The name of the configuration attribute that specifies the fully-qualified
   * name of the Java class that defines a Directory Server attribute syntax.
   */
  public static final String ATTR_SYNTAX_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that indicates whether an attribute
   * syntax should be enabled.
   */
  public static final String ATTR_SYNTAX_ENABLED =
       "ds-cfg-enabled";



  /**
   * The name of the configuration attribute that holds the actual start time
   * for a task.
   */
  public static final String ATTR_TASK_ACTUAL_START_TIME =
       NAME_PREFIX_TASK + "actual-start-time";



  /**
   * The name of the configuration attribute that holds the path to the backing
   * file for task information.
   */
  public static final String ATTR_TASK_BACKING_FILE =
       "ds-cfg-task-backing-file";



  /**
   * The name of the configuration attribute that holds the name of the class
   * providing the task logic.
   */
  public static final String ATTR_TASK_CLASS =
       NAME_PREFIX_TASK + "class-name";



  /**
   * The name of the configuration attribute that holds the completion time for
   * a task.
   */
  public static final String ATTR_TASK_COMPLETION_TIME =
       NAME_PREFIX_TASK + "completion-time";



  /**
   * The name of the configuration attribute that holds task IDs of any tasks on
   * which a given task is dependent.
   */
  public static final String ATTR_TASK_DEPENDENCY_IDS =
       NAME_PREFIX_TASK + "dependency-id";



  /**
   * The name of the configuration attribute that holds the indication of what
   * to do in the event that one of the dependencies for a task has failed.
   */
  public static final String ATTR_TASK_FAILED_DEPENDENCY_ACTION =
       NAME_PREFIX_TASK + "failed-dependency-action";



  /**
   * The name of the configuration attribute that holds the set of log messages
   * for a task.
   */
  public static final String ATTR_TASK_LOG_MESSAGES =
       NAME_PREFIX_TASK + "log-message";



  /**
   * The name of the configuration attribute that holds the set of e-mail
   * addresses of the users to notify when a task has completed.
   */
  public static final String ATTR_TASK_NOTIFY_ON_COMPLETION =
       NAME_PREFIX_TASK + "notify-on-completion";



  /**
   * The name of the configuration attribute that holds the set of e-mail
   * addresses of the users to notify if a task fails.
   */
  public static final String ATTR_TASK_NOTIFY_ON_ERROR =
       NAME_PREFIX_TASK + "notify-on-error";



  /**
   * The name of the configuration attribute that holds the length of time in
   * seconds that task information should be retained after processing on the
   * task has completed.
   */
  public static final String ATTR_TASK_RETENTION_TIME =
       "ds-cfg-task-retention-time";



  /**
   * The default task retention time that will be used if no value is provided.
   */
  public static final long DEFAULT_TASK_RETENTION_TIME = 86400;



  /**
   * The name of the configuration attribute that holds the scheduled start time
   * for a task.
   */
  public static final String ATTR_TASK_SCHEDULED_START_TIME =
       NAME_PREFIX_TASK + "scheduled-start-time";



  /**
   * The name of the configuration attribute that holds the task ID for a task.
   */
  public static final String ATTR_TASK_ID = NAME_PREFIX_TASK + "id";



  /**
   * The name of the configuration attribute that holds the current state for a
   * task.
   */
  public static final String ATTR_TASK_STATE = NAME_PREFIX_TASK + "state";



  /**
   * The name of the configuration attribute that indicates whether the
   * telephone number attribute syntax should use a strict compliance mode when
   * determining whether a value is acceptable.
   */
  public static final String ATTR_TELEPHONE_STRICT_MODE =
       "ds-cfg-strict-format";



  /**
   * The name of the configuration attribute that holds the server time limit.
   */
  public static final String ATTR_TIME_LIMIT = "ds-cfg-time-limit";



  /**
   * The default value that will be used for the server time limit if no other
   * value is given.
   */
  public static final int DEFAULT_TIME_LIMIT = 60;



  /**
   * The name of the configuration attribute that specifies the DN to use as the
   * search base when trying to find entries that match a provided username.
   */
  public static final String ATTR_USER_BASE_DN =
       "ds-cfg-user-base-dn";



  /**
   * The name of the configuration attribute that specifies which attribute
   * should be used to map usernames to their corresponding entries.
   */
  public static final String ATTR_USERNAME_ATTRIBUTE =
       "ds-cfg-user-name-attribute";



  /**
   * The default attribute type that will be used for username lookups if none
   * is provided.
   */
  public static final String DEFAULT_USERNAME_ATTRIBUTE = "uid";



  /**
   * The name of the configuration attribute that indicates whether to use SSL
   * when accepting client connections.
   */
  public static final String ATTR_USE_SSL = "ds-cfg-use-ssl";



  /**
   * The default configuration that specifies whether to use SSL if it is not
   * defined in the server configuration.
   */
  public static final boolean DEFAULT_USE_SSL = false;



  /**
   * The name of the configuration attribute that indicates whether connections
   * to clients should use the TCP_KEEPALIVE socket option.
   */
  public static final String ATTR_USE_TCP_KEEPALIVE =
       "ds-cfg-use-tcp-keep-alive";



  /**
   * The default policy for using the TCP_KEEPALIVE socket option if it is not
   * specified in the configuration.
   */
  public static final boolean DEFAULT_USE_TCP_KEEPALIVE = true;



  /**
   * The name of the configuration attribute that indicates whether connections
   * to clients should use the TCP_NODELAY socket option.
   */
  public static final String ATTR_USE_TCP_NODELAY =
       "ds-cfg-use-tcp-no-delay";



  /**
   * The default policy for using the TCP_NODELAY socket option if it is not
   * specified in the configuration.
   */
  public static final boolean DEFAULT_USE_TCP_NODELAY = true;



  /**
   * The name of the configuration attribute that is used to hold the name of
   * the user attribute that holds user certificates that can be used for
   * validation.
   */
  public static final String ATTR_VALIDATION_CERT_ATTRIBUTE =
       "ds-cfg-certificate-attribute";



  /**
   * The default attribute name for holding certificate information if no value
   * is specified.
   */
  public static final String DEFAULT_VALIDATION_CERT_ATTRIBUTE =
       "usercertificate";



  /**
   * The name of the configuration attribute that specifies the class providing
   * the logic for the work queue implementation.
   */
  public static final String ATTR_WORKQ_CLASS =
       "ds-cfg-java-class";



  /**
   * The name of the configuration attribute that specifies the writability mode
   * for the Directory Server.
   */
  public static final String ATTR_WRITABILITY_MODE =
       "ds-cfg-writability-mode";



  /**
   * The base name (with no path information) of the file that will be used to
   * hold schema tokens used for compressed schema elements.
   */
  public static final String COMPRESSED_SCHEMA_FILE_NAME =
       "schematokens.dat";



  /**
   * The base name (with no path information) of the directory that will hold
   * the archived versions of previous configurations.
   */
  public static final String CONFIG_ARCHIVE_DIR_NAME = "archived-configs";



  /**
   * The base name (with no path information) of the file that may contain
   * changes in LDIF form to apply to the configuration before the configuration
   * is loaded and initialized.
   */
  public static final String CONFIG_CHANGES_NAME = "config-changes.ldif";



  /**
   * The name of the directory that will hold the configuration file for the
   * Directory Server.
   */
  public static final String CONFIG_DIR_NAME = "config";



  /**
   * The default name of the file that holds the configuration for the Directory
   * Server.  It should exist below the directory specified by the
   * {@code CONFIG_DIR_NAME}.
   */
  public static final String CONFIG_FILE_NAME = "config.ldif";



  /**
   * The DN of the entry that will serve as the root for the Directory Server
   * configuration.
   */
  public static final String DN_CONFIG_ROOT = "cn=config";



  /**
   * The DN of the entry that will serve as the base for all Directory Server
   * account status notification handlers.
   */
  public static final String DN_ACCT_NOTIFICATION_HANDLER_CONFIG_BASE =
       "cn=Account Status Notification Handlers," + DN_CONFIG_ROOT;



  /**
   * The DN of the entry that will serve as the base for all Directory Server
   * backends.
   */
  public static final String DN_BACKEND_BASE = "cn=Backends," + DN_CONFIG_ROOT;



  /**
   * The DN of the entry that will serve as the base for all Directory Server
   * backup information.
   */
  public static final String DN_BACKUP_ROOT = "cn=backups";



  /**
   * The DN of the entry that will serve as the base for all Directory Server
   * connection handlers.
   */
  public static final String DN_CONNHANDLER_BASE =
       "cn=Connection Handlers," + DN_CONFIG_ROOT;



  /**
   * The DN of the entry that will serve as the default root for the Directory
   * Server schema information, unless an alternate location is defined in the
   * configuration.
   */
  public static final String DN_DEFAULT_SCHEMA_ROOT = "cn=schema";



  /**
   * The DN of the entry that will hold the configuration for the Directory
   * Server entry cache.
   */
  public static final String DN_ENTRY_CACHE_CONFIG =
       "cn=Entry Cache," + DN_CONFIG_ROOT;



  /**
   * The DN of the entry that will serve as the base for the configuration
   * for all Directory Server extended operation handlers.
   */
  public static final String DN_EXTENDED_OP_CONFIG_BASE =
       "cn=Extended Operations," + DN_CONFIG_ROOT;



  /**
   * The DN of the entry that will serve as the base for the configuration
   * for all Directory Server group implementations.
   */
  public static final String DN_GROUP_IMPLEMENTATION_CONFIG_BASE =
       "cn=Group Implementations," + DN_CONFIG_ROOT;



  /**
   * The DN of the entry that will serve as the base for the configuration
   * for all Directory Server identity mappers.
   */
  public static final String DN_IDMAPPER_CONFIG_BASE =
       "cn=Identity Mappers," + DN_CONFIG_ROOT;



  /**
   * The DN of the entry that will be the base of the configuration information
   * for the Directory Server certificate mappers.
   */
  public static final String DN_CERTMAPPER_CONFIG_BASE =
       "cn=Certificate Mappers," + DN_CONFIG_ROOT;



  /**
   * The DN of the entry that be the base of the configuration information for
   * the Directory Server key manager providers.
   */
  public static final String DN_KEYMANAGER_PROVIDER_CONFIG_BASE =
       "cn=Key Manager Providers," + DN_CONFIG_ROOT;



  /**
   * The DN of the entry that is the base of the configuration information for
   * the Directory Server trust manager providers.
   */
  public static final String DN_TRUSTMANAGER_PROVIDER_CONFIG_BASE =
       "cn=Trust Manager Providers," + DN_CONFIG_ROOT;



  /**
   * The ADS trust store backend id.
   */
  public static final String ID_ADS_TRUST_STORE_BACKEND = "ads-truststore";



  /**
   * The DN of the trust store backend configuration entry.
   */
  public static final String DN_TRUST_STORE_BACKEND =
       ATTR_BACKEND_ID + "=" + ID_ADS_TRUST_STORE_BACKEND +
            "," + DN_BACKEND_BASE;



  /**
   * Alias of the local instance certificate in the ADS keystore.
   */
  public static final String ADS_CERTIFICATE_ALIAS = "ads-certificate";



  /**
   * The DN of the entry that will serve as the base for local ADS trust store
   * information.
   */
  public static final String DN_TRUST_STORE_ROOT = "cn=ads-truststore";



  /**
   * The name of the attribute that holds a cryptographic cipher-key identifier.
   */
  public static final String ATTR_CRYPTO_KEY_ID = "ds-cfg-key-id";



  /**
   * The name of the objectclass that will be used for a server
   * certificate entry.
   */
  public static final String OC_CRYPTO_INSTANCE_KEY =
       "ds-cfg-instance-key";



  /**
   * The name of the objectclass that will be used for a self-signed
   * certificate request.
   */
  public static final String OC_SELF_SIGNED_CERT_REQUEST =
       "ds-cfg-self-signed-cert-request";



  /**
   * The name of the objectclass that will be used for a cipher key.
   */
  public static final String OC_CRYPTO_CIPHER_KEY = "ds-cfg-cipher-key";



  /**
   * The name of the objectclass that will be used for a mac key.
   */
  public static final String OC_CRYPTO_MAC_KEY = "ds-cfg-mac-key";



  /**
   * The name of the attribute that is used to hold a cryptographic
   * public key certificate.
   */
  public static final String ATTR_CRYPTO_PUBLIC_KEY_CERTIFICATE =
       "ds-cfg-public-key-certificate";


  /**
   * The name of the attribute that is used to hold the name of a
   * cryptographic cipher transformation.
   */
  public static final String ATTR_CRYPTO_CIPHER_TRANSFORMATION_NAME =
       "ds-cfg-cipher-transformation-name";


  /**
   * The name of the attribute that is used to hold the name of a
   * cryptographic message authentication code (MAC) algorithm.
   */
  public static final String ATTR_CRYPTO_MAC_ALGORITHM_NAME =
       "ds-cfg-mac-algorithm-name";


  /**
   * The name of the attribute that is used to hold the length of a
   * cryptographic secret key.
   */
  public static final String ATTR_CRYPTO_KEY_LENGTH_BITS =
       "ds-cfg-key-length-bits";


  /**
   * The name of the attribute that is used to hold the length of a
   * cryptographic cipher initialization vector.
   */
  public static final String ATTR_CRYPTO_INIT_VECTOR_LENGTH_BITS =
       "ds-cfg-initialization-vector-length-bits";


  /**
   * The name of the attribute that is used to hold a cryptographic
   * cipher-key wrapped by a public-key.
   */
  public static final String ATTR_CRYPTO_SYMMETRIC_KEY = "ds-cfg-symmetric-key";


  /**
   * The name of the attribute that is used to hold time a cryptographic key
   * was suspected to be compromised.
   */
  public static final String ATTR_CRYPTO_KEY_COMPROMISED_TIME =
       "ds-cfg-key-compromised-time";


  /**
   * The DN of the entry that will serve as the base for all Directory Server
   * loggers.
   */
  public static final String DN_LOGGER_BASE = "cn=Loggers," + DN_CONFIG_ROOT;



  /**
   * The DN of the entry that will serve as the base for all Directory Server
   * matching rules.
   */
  public static final String DN_MATCHING_RULE_CONFIG_BASE =
       "cn=Matching Rules," + DN_CONFIG_ROOT;



  /**
   * The DN of the entry that will serve as the base for the configuration
   * for all Directory Server monitors.
   */
  public static final String DN_MONITOR_CONFIG_BASE =
       "cn=Monitor Providers," + DN_CONFIG_ROOT;



  /**
   * The DN of the entry that will serve as the base for all Directory Server
   * monitor information.
   */
  public static final String DN_MONITOR_ROOT = "cn=monitor";



  /**
   * The DN of the entry that will serve as the base for all Directory Server
   * plugin information.
   */
  public static final String DN_PLUGIN_BASE = "cn=Plugins," + DN_CONFIG_ROOT;



  /**
   * The DN of the entry that will serve as the base for the configuration
   * for all Directory Server password generators.
   */
  public static final String DN_PWGENERATOR_CONFIG_BASE =
       "cn=Password Generators," + DN_CONFIG_ROOT;



  /**
   * The DN of the entry that will serve as the base for the configuration
   * for all Directory Server password policies.
   */
  public static final String DN_PWPOLICY_CONFIG_BASE =
       "cn=Password Policies," + DN_CONFIG_ROOT;



  /**
   * The DN of the entry that will serve as the base for the configuration
   * for all Directory Server password storage schemes.
   */
  public static final String DN_PWSCHEME_CONFIG_BASE =
       "cn=Password Storage Schemes," + DN_CONFIG_ROOT;



  /**
   * The DN of the entry that will serve as the base for the configuration
   * for all Directory Server password validators.
   */
  public static final String DN_PWVALIDATOR_CONFIG_BASE =
       "cn=Password Validators," + DN_CONFIG_ROOT;



  /**
   * The DN of the entry that will serve as the parent for all root DN
   * configuration entries.
   */
  public static final String DN_ROOT_DN_CONFIG_BASE =
       "cn=Root DNs," + DN_CONFIG_ROOT;



  /**
   * The DN of the entry that will hold the configuration information for the
   * Directory Server root DSE.
   */
  public static final String DN_ROOT_DSE_CONFIG =
       "cn=Root DSE," + DN_CONFIG_ROOT;



  /**
   * The DN of the entry that will serve as the base for the configuration
   * for all Directory Server SASL mechanism handlers.
   */
  public static final String DN_SASL_CONFIG_BASE =
       "cn=SASL Mechanisms," + DN_CONFIG_ROOT;



  /**
   * The DN of the entry that will serve as the base for the configuration for
   * all Directory Server synchronization providers.
   */
  public static final String DN_SYNCHRONIZATION_PROVIDER_BASE =
       "cn=Synchronization Providers," + DN_CONFIG_ROOT;


  /**
   * The DN of the entry containing the access control handler configuration.
   */
  public static final String DN_AUTHZ_HANDLER_CONFIG =
       "cn=Access Control Handler," + DN_CONFIG_ROOT;


  /**
   * The DN of the entry that will serve as the base for all Directory Server
   * attribute syntaxes.
   */
  public static final String DN_SYNTAX_CONFIG_BASE =
       "cn=Syntaxes," + DN_CONFIG_ROOT;



  /**
   * The DN of the entry that will serve as the base for all Directory Server
   * task information.
   */
  public static final String DN_TASK_ROOT = "cn=Tasks";



  /**
   * The DN of the entry that will hold information about the Directory Server
   * work queue configuration.
   */
  public static final String DN_WORK_QUEUE_CONFIG =
       "cn=Work Queue," + DN_CONFIG_ROOT;



  /**
   * The name of the environment variable that the Directory Server may check to
   * determine the installation root.
   */
  public static final String ENV_VAR_INSTANCE_ROOT = "INSTANCE_ROOT";



  /**
   * The class name string that should be used in JMX MBeanAttributeInfo objects
   * whose value is a Boolean array.
   */
  public static final String JMX_TYPE_BOOLEAN_ARRAY = "[Z";



  /**
   * The class name string that should be used in JMX MBeanAttributeInfo objects
   * whose value is a byte array.
   */
  public static final String JMX_TYPE_BYTE_ARRAY = "[B";



  /**
   * The class name string that should be used in JMX MBeanAttributeInfo objects
   * whose value is a character array.
   */
  public static final String JMX_TYPE_CHARACTER_ARRAY = "[C";



  /**
   * The class name string that should be used in JMX MBeanAttributeInfo objects
   * whose value is a double array.
   */
  public static final String JMX_TYPE_DOUBLE_ARRAY = "[D";



  /**
   * The class name string that should be used in JMX MBeanAttributeInfo objects
   * whose value is a float array.
   */
  public static final String JMX_TYPE_FLOAT_ARRAY = "[F";



  /**
   * The class name string that should be used in JMX MBeanAttributeInfo objects
   * whose value is an integer array.
   */
  public static final String JMX_TYPE_INT_ARRAY = "[I";



  /**
   * The class name string that should be used in JMX MBeanAttributeInfo objects
   * whose value is a long array.
   */
  public static final String JMX_TYPE_LONG_ARRAY = "[J";



  /**
   * The class name string that should be used in JMX MBeanAttributeInfo objects
   * whose value is a short array.
   */
  public static final String JMX_TYPE_SHORT_ARRAY = "[S";



  /**
   * The class name string that should be used in JMX MBeanAttributeInfo objects
   * whose value is a string array.  Note that this format is significantly
   * different from the format used for arrays of primitive types.
   */
  public static final String JMX_TYPE_STRING_ARRAY =
       "[L" + String.class.getName() + ";";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * access logger.
   */
  public static final String OC_ACCESS_LOGGER =
       "ds-cfg-access-log-publisher";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * account status notification handler.
   */
  public static final String OC_ACCT_NOTIFICATION_HANDLER =
       "ds-cfg-account-status-notification-handler";



  /**
   * The name of the objectclass that will be used for a Directory Server alert
   * handler.
   */
  public static final String OC_ALERT_HANDLER =
       "ds-cfg-alert-handler";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * attribute syntaxes.
   */
  public static final String OC_ATTRIBUTE_SYNTAX =
       "ds-cfg-attribute-syntax";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * backend.
   */
  public static final String OC_BACKEND = "ds-cfg-backend";



  /**
   * The name of the objectclass that will be used for a directory server backup
   * directory.
   */
  public static final String OC_BACKUP_DIRECTORY =
       NAME_PREFIX_BACKUP + "directory";



  /**
   * The name of the objectclass that will be used for a directory server backup
   * information entry.
   */
  public static final String OC_BACKUP_INFO = NAME_PREFIX_BACKUP + "info";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * certificate mapper.
   */
  public static final String OC_CERTIFICATE_MAPPER =
       "ds-cfg-certificate-mapper";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * connection handler.
   */
  public static final String OC_CONNECTION_HANDLER =
       "ds-cfg-connection-handler";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * debug logger.
   */
  public static final String OC_DEBUG_LOGGER = "ds-cfg-debug-log-publisher";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * error logger.
   */
  public static final String OC_ERROR_LOGGER = "ds-cfg-error-log-publisher";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * extended operation handler.
   */
  public static final String OC_EXTENDED_OPERATION_HANDLER =
       "ds-cfg-extended-operation-handler";



  /**
   * The name of the objectclass that will be used for a Directory Server group
   * implementation.
   */
  public static final String OC_GROUP_IMPLEMENTATION =
       "ds-cfg-group-implementation";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * identity mapper.
   */
  public static final String OC_IDENTITY_MAPPER =
       "ds-cfg-identity-mapper";



  /**
   * The name of the objectclass that will be used for a Directory Server key
   * manager provider.
   */
  public static final String OC_KEY_MANAGER_PROVIDER =
       "ds-cfg-key-manager-provider";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * matching rules.
   */
  public static final String OC_MATCHING_RULE =
       "ds-cfg-matching-rule";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * monitor provider.
   */
  public static final String OC_MONITOR_PROVIDER =
       "ds-cfg-monitor-provider";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * password generator.
   */
  public static final String OC_PASSWORD_GENERATOR =
       "ds-cfg-password-generator";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * password policy.
   */
  public static final String OC_PASSWORD_POLICY =
       "ds-cfg-password-policy";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * password storage scheme.
   */
  public static final String OC_PASSWORD_STORAGE_SCHEME =
       "ds-cfg-password-storage-scheme";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * password validator.
   */
  public static final String OC_PASSWORD_VALIDATOR =
       "ds-cfg-password-validator";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * plugin.
   */
  public static final String OC_PLUGIN = "ds-cfg-plugin";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * recurring task definition.
   */
  public static final String OC_RECURRING_TASK = "ds-recurring-task";



  /**
   * The name of the objectclass that will be used for a Directory Server root
   * DN configuration entry.
   */
  public static final String OC_ROOT_DN = "ds-cfg-root-dn-user";



  /**
   * The name of the objectclass that will be used for a Directory Server SASL
   * mechanism handler.
   */
  public static final String OC_SASL_MECHANISM_HANDLER =
       "ds-cfg-sasl-mechanism-handler";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * synchronization provider.
   */
  public static final String OC_SYNCHRONIZATION_PROVIDER =
       "ds-cfg-synchronization-provider";



  /**
   * The name of the objectclass that will be used for the Directory Server
   * access control configuration.
   */
  public static final String OC_AUTHZ_HANDLER_CONFIG =
       "ds-cfg-access-control-handler";



  /**
   * The name of the objectclass that will be used for a Directory Server task
   * definition.
   */
  public static final String OC_TASK = "ds-task";



  /**
   * The name of the objectclass that will be used for a Directory Server trust
   * manager provider.
   */
  public static final String OC_TRUST_MANAGER_PROVIDER =
       "ds-cfg-trust-manager-provider";



  /**
   * The name of the operational attribute that will appear in a user's entry to
   * indicate whether the account has been disabled.
   */
  public static final String OP_ATTR_ACCOUNT_DISABLED =
       NAME_PREFIX_PWP + "account-disabled";



  /**
   * The name of the operational attribute that may appear in a user's entry to
   * indicate when that account will expire (and therefore may no longer be used
   * to authenticate).
   */
  public static final String OP_ATTR_ACCOUNT_EXPIRATION_TIME =
       NAME_PREFIX_PWP + "account-expiration-time";



  /**
   * The name of the operational attribute that will appear in an entry to
   * indicate when it was created.
   */
  public static final String OP_ATTR_CREATE_TIMESTAMP = "createTimestamp";



  /**
   * The name of the create timestamp attribute, in all lowercase characters.
   */
  public static final String OP_ATTR_CREATE_TIMESTAMP_LC = "createtimestamp";



  /**
   * The name of the operational attribute that will appear in an entry to
   * indicate who created it.
   */
  public static final String OP_ATTR_CREATORS_NAME = "creatorsName";



  /**
   * The name of the creatorsName attribute, in all lowercase characters.
   */
  public static final String OP_ATTR_CREATORS_NAME_LC = "creatorsname";



  /**
   * The name of the operational attribute that will appear in a user's entry to
   * hold the last login time.
   */
  public static final String OP_ATTR_LAST_LOGIN_TIME =
       NAME_PREFIX_PWP + "last-login-time";



  /**
   * The name of the operational attribute that will appear in an entry to
   * indicate who last updated it.
   */
  public static final String OP_ATTR_MODIFIERS_NAME = "modifiersName";



  /**
   * The name of the modifiersName attribute, in all lowercase characters.
   */
  public static final String OP_ATTR_MODIFIERS_NAME_LC = "modifiersname";



  /**
   * The name of the operational attribute that will appear in an entry to
   * indicate when it was last updated.
   */
  public static final String OP_ATTR_MODIFY_TIMESTAMP = "modifyTimestamp";



  /**
   * The name of the modify timestamp attribute, in all lowercase characters.
   */
  public static final String OP_ATTR_MODIFY_TIMESTAMP_LC = "modifytimestamp";



  /**
   * The name of the operational attribute that will appear in a user's entry to
   * specify the set of privileges assigned to that user.
   */
  public static final String OP_ATTR_PRIVILEGE_NAME = "ds-privilege-name";



  /**
   * The name of the operational attribute that will appear in a user's entry
   * to indicate the time that the password was last changed.
   */
  public static final String OP_ATTR_PWPOLICY_CHANGED_TIME = "pwdChangedTime";



  /**
   * The name of the password changed time attribute, in all lowercase
   * characters.
   */
  public static final String OP_ATTR_PWPOLICY_CHANGED_TIME_LC =
       "pwdchangedtime";



  /**
   * The name of the operational attribute that will appear in a user's entry to
   * indicate the times of the grace logins by that user.
   */
  public static final String OP_ATTR_PWPOLICY_GRACE_LOGIN_TIME =
       "pwdGraceUseTime";



  /**
   * The name of the grace login time attribute, in all lowercase characters.
   */
  public static final String OP_ATTR_PWPOLICY_GRACE_LOGIN_TIME_LC =
       "pwdgraceusetime";



  /**
   * The name of the operational attribute that specifies the time that an
   * authentication attempt failed.
   */
  public static final String OP_ATTR_PWPOLICY_FAILURE_TIME = "pwdFailureTime";



  /**
   * The name of the failure time attribute, in all lowercase characters.
   */
  public static final String OP_ATTR_PWPOLICY_FAILURE_TIME_LC =
       "pwdfailuretime";



  /**
   * The name of the operational attribute that is used to maintain the password
   * history for the user.
   */
  public static final String OP_ATTR_PWPOLICY_HISTORY = "pwdHistory";



  /**
   * The name of the operational attribute that is used to maintain the password
   * history for the user, in all lowercase characters.
   */
  public static final String OP_ATTR_PWPOLICY_HISTORY_LC = "pwdhistory";



  /**
   * The name of the operational attribute that specifies the time that the
   * account was locked due to too many failed attempts.
   */
  public static final String OP_ATTR_PWPOLICY_LOCKED_TIME =
       "pwdAccountLockedTime";



  /**
   * The name of the locked time attribute, in all lowercase characters.
   */
  public static final String OP_ATTR_PWPOLICY_LOCKED_TIME_LC =
       "pwdaccountlockedtime";



  /**
   * The name of the operational attribute that will appear in a user's entry to
   * indicate the time that the user changed their password as a result of a
   * policy-wide required change.
   */
  public static final String OP_ATTR_PWPOLICY_CHANGED_BY_REQUIRED_TIME =
       NAME_PREFIX_PWP + "password-changed-by-required-time";



  /**
   * The name of the operational attribute that will appear in a user's entry
   * to indicate whether the password must be changed at the next
   * authentication.
   */
  public static final String OP_ATTR_PWPOLICY_RESET_REQUIRED = "pwdReset";



  /**
   * The name of the password reset attribute, in all lowercase characters.
   */
  public static final String OP_ATTR_PWPOLICY_RESET_REQUIRED_LC = "pwdreset";



  /**
   * The name of the operational attribute that will appear in a user's entry to
   * indicate which password policy should be used.
   */
  public static final String OP_ATTR_PWPOLICY_POLICY_DN =
       "ds-pwp-password-policy-dn";



  /**
   * The name of the operational attribute that indicates when the user was
   * first warned about an upcoming password expiration.
   */
  public static final String OP_ATTR_PWPOLICY_WARNED_TIME =
       NAME_PREFIX_PWP + "warned-time";



  /**
   * The name of the operational attribute that may be included in user entries
   * to specify an idle time limit to be applied for that user.
   */
  public static final String OP_ATTR_USER_IDLE_TIME_LIMIT =
      NAME_PREFIX_RLIM + "idle-time-limit";



  /**
   * The name of the operational attribute that may be included in user
   * entries to specify a size limit to be applied for that user.
   */
  public static final String OP_ATTR_USER_SIZE_LIMIT =
       NAME_PREFIX_RLIM + "size-limit";



  /**
   * The name of the operational attribute that may be included in user
   * entries to specify a time limit to be applied for that user.
   */
  public static final String OP_ATTR_USER_TIME_LIMIT =
       NAME_PREFIX_RLIM + "time-limit";



  /**
   * The name of the operational attribute that may be included in user
   * entries to specify a lookthrough limit for that user.
   */
  public static final String OP_ATTR_USER_LOOKTHROUGH_LIMIT =
      NAME_PREFIX_RLIM + "lookthrough-limit";



  /**
   * The name of the attribute option used to indicate that a configuration
   * attribute has one or more pending values.
   */
  public static final String OPTION_PENDING_VALUES = "pending";



  /**
   * The path to the directory that should serve as the MakeLDIF resource
   * directory.  It is relative to the server root.
   */
  public static final String PATH_MAKELDIF_RESOURCE_DIR =
       "config" + File.separator + "MakeLDIF";



  /**
   * The path to the directory containing the server schema definitions.  It is
   * relative to the server root.
   */
  public static final String PATH_SCHEMA_DIR =
       "config" + File.separator + "schema";



  /**
   * The name (with no path information) of the file in the schema directory
   * that will contain user-defined schema definitions.
   */
  public static final String FILE_USER_SCHEMA_ELEMENTS = "99-user.ldif";



  /**
   * The name of the configuration attribute that indicates the log file
   * where the loggers will log the information.
   */
  public static final String ATTR_LOGGER_FILE =
         "ds-cfg-log-file";



  /**
   * The name of the configuration attribute that indicates the default
   * severity levels for the logger.
   */
  public static final String ATTR_LOGGER_DEFAULT_SEVERITY =
        "ds-cfg-default-severity";



  /**
   * The name of the configuration attribute that indicates the override
   * severity levels for the logger.
   */
  public static final String ATTR_LOGGER_OVERRIDE_SEVERITY =
        "ds-cfg-override-severity";


  /**
   * The name of the configuration attribute that indicates the backend database
   * location on disk.
   */
  public static final String ATTR_BACKEND_DIRECTORY =
       "ds-cfg-db-directory";



  /**
   * The name of the attribute which configures the file permission mode
   * for the database direction.
   */
  public static final String ATTR_BACKEND_MODE =
      "ds-cfg-db-directory-permissions";



  /**
   * The name of the file (with no path information) that will be used as the
   * backing file for holding the tasks defined in the Directory Server.
   */
  public static final String TASK_FILE_NAME = "tasks.ldif";



  /**
   * The string representation of the RDN that should be used for the entry that
   * is the immediate parent of all recurring task definitions in the server.
   */
  public static final String RECURRING_TASK_BASE_RDN = "cn=Recurring Tasks";



  /**
   * The string representation of the RDN that should be used for the entry that
   * is the immediate parent of all scheduled task definitions in the server.
   */
  public static final String SCHEDULED_TASK_BASE_RDN = "cn=Scheduled Tasks";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * import task definition.
   */
  public static final String OC_IMPORT_TASK = NAME_PREFIX_TASK + "import";



  /**
   * The name of the attribute in an import task definition that specifies the
   * path to the file containing the LDIF data to import.
   */
  public static final String ATTR_IMPORT_LDIF_FILE =
       NAME_PREFIX_TASK + "import-ldif-file";



  /**
   * The name of the attribute in an import task definition that specifies
   * whether the import process should append to the existing database rather
   * than overwriting it.
   */
  public static final String ATTR_IMPORT_APPEND =
       NAME_PREFIX_TASK + "import-append";



  /**
   * The name of the attribute in an import task definition that specifies
   * whether an existing entry should be replaced when appending to an existing
   * database.
   */
  public static final String ATTR_IMPORT_REPLACE_EXISTING =
       NAME_PREFIX_TASK + "import-replace-existing";



  /**
   * The name of the attribute in an import task definition that specifies the
   * backend ID for the backend into which the date should be imported.
   */
  public static final String ATTR_IMPORT_BACKEND_ID =
       NAME_PREFIX_TASK + "import-backend-id";



  /**
   * The name of the attribute in an import task definition that specifies the
   * base DN of a branch that should be included in the LDIF import.
   */
  public static final String ATTR_IMPORT_INCLUDE_BRANCH =
       NAME_PREFIX_TASK + "import-include-branch";



  /**
   * The name of the attribute in an import task definition that specifies the
   * base DN of a branch that should be excluded from the LDIF import.
   */
  public static final String ATTR_IMPORT_EXCLUDE_BRANCH =
       NAME_PREFIX_TASK + "import-exclude-branch";



  /**
   * The name of the attribute in an import task definition that specifies an
   * attribute that should be included in the LDIF import.
   */
  public static final String ATTR_IMPORT_INCLUDE_ATTRIBUTE =
       NAME_PREFIX_TASK + "import-include-attribute";



  /**
   * The name of the attribute in an import task definition that specifies an
   * attribute that should be excluded from the LDIF import.
   */
  public static final String ATTR_IMPORT_EXCLUDE_ATTRIBUTE =
       NAME_PREFIX_TASK + "import-exclude-attribute";



  /**
   * The name of the attribute in an import task definition that specifies
   * a search filter that may be used to control which entries are included
   * in the import.
   */
  public static final String ATTR_IMPORT_INCLUDE_FILTER =
       NAME_PREFIX_TASK + "import-include-filter";



  /**
   * The name of the attribute in an import task definition that specifies
   * a search filter that may be used to control which entries are excluded
   * from the import.
   */
  public static final String ATTR_IMPORT_EXCLUDE_FILTER =
       NAME_PREFIX_TASK + "import-exclude-filter";



  /**
   * The name of the attribute in an import task definition that specifies
   * the path to a file into which rejected entries may be written if they
   * are not accepted during the import process.
   */
  public static final String ATTR_IMPORT_REJECT_FILE =
       NAME_PREFIX_TASK + "import-reject-file";


  /**
   * The name of the attribute in an import task definition that specifies
   * the path to a file into which skipped entries may be written if they
   * do not match criteria during the import process.
   */
  public static final String ATTR_IMPORT_SKIP_FILE =
       NAME_PREFIX_TASK + "import-skip-file";


  /**
   * The name of the attribute in an import task definition that specifies
   * whether to overwrite an existing rejects and/or skip file when performing
   * an LDIF import rather than appending to it.
   */
  public static final String ATTR_IMPORT_OVERWRITE =
       NAME_PREFIX_TASK + "import-overwrite-rejects";


  /**
   * The name of the attribute in an import task definition that specifies
   * whether to skip schema validation during the import.
   */
  public static final String ATTR_IMPORT_SKIP_SCHEMA_VALIDATION =
       NAME_PREFIX_TASK + "import-skip-schema-validation";



  /**
   * The name of the attribute in an import task definition that specifies
   * whether the LDIF file containing the data to import is compressed.
   */
  public static final String ATTR_IMPORT_IS_COMPRESSED =
       NAME_PREFIX_TASK + "import-is-compressed";



  /**
   * The name of the attribute in an import task definition that specifies
   * whether the LDIF file containing the data to import is encrypted.
   */
  public static final String ATTR_IMPORT_IS_ENCRYPTED =
       NAME_PREFIX_TASK + "import-is-encrypted";


  /**
   * The name of the objectclass that will be used for a Directory Server
   * initialize task definition.
   */
  public static final String OC_INITIALIZE_TASK =
    NAME_PREFIX_TASK + "initialize-from-remote-replica";

  /**
   * The name of the attribute in an initialize task definition that specifies
   * the base dn related to the synchonization domain to initialize.
   */
  public static final String ATTR_TASK_INITIALIZE_DOMAIN_DN =
       NAME_PREFIX_TASK + "initialize-domain-dn";

  /**
   * The name of the attribute in an initialize target task definition that
   * specifies the source in terms of source server from which to initialize.
   */
  public static final String ATTR_TASK_INITIALIZE_SOURCE =
       NAME_PREFIX_TASK + "initialize-replica-server-id";

  /**
   * The name of the objectclass that will be used for a Directory Server
   * initialize target task definition.
   */
  public static final String OC_INITIALIZE_TARGET_TASK =
    NAME_PREFIX_TASK + "initialize-remote-replica";

  /**
   * The name of the attribute in an initialize target task definition that
   * specifies the base dn related to the synchonization domain to initialize.
   */
  public static final String ATTR_TASK_INITIALIZE_TARGET_DOMAIN_DN =
       NAME_PREFIX_TASK + "initialize-domain-dn";

  /**
   * The name of the attribute in an initialize target task definition that
   * specifies the scope in terms of servers to initialize.
   */
  public static final String ATTR_TASK_INITIALIZE_TARGET_SCOPE =
       NAME_PREFIX_TASK + "initialize-replica-server-id";

  /**
   * The name of the attribute in an initialize target task definition that
   * specifies the scope in terms of servers to initialize.
   */
  public static final String ATTR_TASK_INITIALIZE_LEFT =
       NAME_PREFIX_TASK + "unprocessed-entry-count";

  /**
   * The name of the attribute in an initialize target task definition that
   * specifies the scope in terms of servers to initialize.
   */
  public static final String ATTR_TASK_INITIALIZE_DONE =
       NAME_PREFIX_TASK + "processed-entry-count";


  /**
   * The name of the objectclass that will be used for a Directory Server
   * export task definition.
   */
  public static final String OC_EXPORT_TASK = NAME_PREFIX_TASK + "export";



  /**
   * The name of the attribute in an export task definition that specifies the
   * path to the file to which the LDIF data should be written.
   */
  public static final String ATTR_TASK_EXPORT_LDIF_FILE =
       NAME_PREFIX_TASK + "export-ldif-file";



  /**
   * The name of the attribute in an export task definition that specifies
   * whether the export process should append to an existing LDIF file rather
   * than overwrite it.
   */
  public static final String ATTR_TASK_EXPORT_APPEND_TO_LDIF =
       NAME_PREFIX_TASK + "export-append-to-ldif";



  /**
   * The name of the attribute in an export task definition that specifies the
   * backend ID for the backend from which the data should be exported.
   */
  public static final String ATTR_TASK_EXPORT_BACKEND_ID =
       NAME_PREFIX_TASK + "export-backend-id";



  /**
   * The name of the attribute in an export task definition that specifies the
   * base DN of a branch that should be included in the LDIF export.
   */
  public static final String ATTR_TASK_EXPORT_INCLUDE_BRANCH =
       NAME_PREFIX_TASK + "export-include-branch";



  /**
   * The name of the attribute in an export task definition that specifies the
   * base DN of a branch that should be excluded from the LDIF export.
   */
  public static final String ATTR_TASK_EXPORT_EXCLUDE_BRANCH =
       NAME_PREFIX_TASK + "export-exclude-branch";



  /**
   * The name of the attribute in an export task definition that specifies an
   * attribute that should be included in the LDIF export.
   */
  public static final String ATTR_TASK_EXPORT_INCLUDE_ATTRIBUTE =
       NAME_PREFIX_TASK + "export-include-attribute";



  /**
   * The name of the attribute in an export task definition that specifies an
   * attribute that should be excluded from the LDIF export.
   */
  public static final String ATTR_TASK_EXPORT_EXCLUDE_ATTRIBUTE =
       NAME_PREFIX_TASK + "export-exclude-attribute";



  /**
   * The name of the attribute in an export task definition that specifies
   * a search filter that may be used to control which entries are included
   * in the export.
   */
  public static final String ATTR_TASK_EXPORT_INCLUDE_FILTER =
       NAME_PREFIX_TASK + "export-include-filter";



  /**
   * The name of the attribute in an export task definition that specifies
   * a search filter that may be used to control which entries are excluded
   * from the export.
   */
  public static final String ATTR_TASK_EXPORT_EXCLUDE_FILTER =
       NAME_PREFIX_TASK + "export-exclude-filter";



  /**
   * The name of the attribute in an export task definition that specifies
   * the column at which long lines should be wrapped.
   */
  public static final String ATTR_TASK_EXPORT_WRAP_COLUMN =
       NAME_PREFIX_TASK + "export-wrap-column";



  /**
   * The name of the attribute in an export task definition that specifies
   * whether the LDIF data should be compressed as it is exported.
   */
  public static final String ATTR_TASK_EXPORT_COMPRESS_LDIF =
       NAME_PREFIX_TASK + "export-compress-ldif";



  /**
   * The name of the attribute in an export task definition that specifies
   * whether the LDIF data should be encrypted as it is exported.
   */
  public static final String ATTR_TASK_EXPORT_ENCRYPT_LDIF =
       NAME_PREFIX_TASK + "export-encrypt-ldif";



  /**
   * The name of the attribute in an export task definition that specifies
   * whether a signed hash of the export data should be appended to the LDIF
   * file.
   */
  public static final String ATTR_TASK_EXPORT_SIGN_HASH =
       NAME_PREFIX_TASK + "export-sign-hash";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * restore task definition.
   */
  public static final String OC_RESTORE_TASK = NAME_PREFIX_TASK + "restore";



  /**
   * The name of the attribute in a restore task definition that specifies
   * whether the contents of the backup should be verified but not restored.
   */
  public static final String ATTR_TASK_RESTORE_VERIFY_ONLY =
       NAME_PREFIX_TASK + "restore-verify-only";



  /**
   * The name of the objectclass that will be used for a Directory Server
   * backup task definition.
   */
  public static final String OC_BACKUP_TASK = NAME_PREFIX_TASK + "backup";



  /**
   * The name of the attribute in a backup task definition that specifies
   * the backend ID for a backend that should be archived.
   */
  public static final String ATTR_TASK_BACKUP_BACKEND_ID =
       NAME_PREFIX_TASK + "backup-backend-id";



  /**
   * The name of the attribute in a backup task definition that specifies
   * whether all backends defined in the server should be backed up.
   */
  public static final String ATTR_TASK_BACKUP_ALL =
       NAME_PREFIX_TASK + "backup-all";



  /**
   * The name of the attribute in a backup task definition that specifies
   * whether to generate and incremental backup or a full backup.
   */
  public static final String ATTR_TASK_BACKUP_INCREMENTAL =
       NAME_PREFIX_TASK + "backup-incremental";



  /**
   * The name of the attribute in a backup task definition that specifies
   * the backup ID of the backup against which an incremental backup should
   * be taken.
   */
  public static final String ATTR_TASK_BACKUP_INCREMENTAL_BASE_ID =
       NAME_PREFIX_TASK + "backup-incremental-base-id";



  /**
   * The name of the attribute in a backup task definition that specifies
   * whether the backup file(s) should be compressed.
   */
  public static final String ATTR_TASK_BACKUP_COMPRESS =
       NAME_PREFIX_TASK + "backup-compress";



  /**
   * The name of the attribute in a backup task definition that specifies
   * whether the backup file(s) should be compressed.
   */
  public static final String ATTR_TASK_BACKUP_ENCRYPT =
       NAME_PREFIX_TASK + "backup-encrypt";



  /**
   * The name of the attribute in a backup task definition that specifies
   * whether to generate a hash of the backup file(s) for integrity
   * verification during restore.
   */
  public static final String ATTR_TASK_BACKUP_HASH =
       NAME_PREFIX_TASK + "backup-hash";



  /**
   * The name of the attribute in a backup task definition that specifies
   * whether the hash of the archive file(s) should be digitally signed to
   * provide tamper detection.
   */
  public static final String ATTR_TASK_BACKUP_SIGN_HASH =
       NAME_PREFIX_TASK + "backup-sign-hash";
  /**
   * The name of the attribute in the add schema file task definition that
   * specifies the name of the schema file to be added.
   */
  public static final String ATTR_TASK_ADDSCHEMAFILE_FILENAME =
       NAME_PREFIX_TASK + "schema-file-name";


  /**
   * The name of the attribute in a debug target configuration for a debug
   * logger that specifies the scope of the debug target.
   */
  public static final String ATTR_LOGGER_DEBUG_SCOPE =
      NAME_PREFIX_TASK + "debug-scope";

  /**
   * The name of the attribute in a logger configuration that spcifies the
   * log level.
   */
  public static final String ATTR_LOGGER_LEVEL =
      NAME_PREFIX_TASK + "log-level";

  /**
   * The name of the attribute in a logger configuration that specifies
   * whether to asyncornously writes log records to disk.
   */
  public static final String ATTR_LOGGER_ASYNC_WRITE =
      NAME_PREFIX_TASK + "async-write";


  /**
   * The name of the attribute in an rebuild task definition that specifies the
   * base DN of the indexes to do the rebuild in.
   */
  public static final String ATTR_REBUILD_BASE_DN =
       NAME_PREFIX_TASK + "rebuild-base-dn";


  /**
   * The name of the attribute in an rebuild task definition that specifies the
   * indexes to rebuild.
   */
  public static final String ATTR_REBUILD_INDEX =
       NAME_PREFIX_TASK + "rebuild-index";


  /**
   * The name of the attribute in an rebuild task definition that specifies the
   * maximum number of threads.
   */
  public static final String ATTR_REBUILD_MAX_THREADS =
       NAME_PREFIX_TASK + "rebuild-max-threads";

  /**
   * The name of the objectclass that will be used for a Directory Server
   * reset generationId task definition.
   */
  public static final String OC_RESET_GENERATION_ID_TASK =
       NAME_PREFIX_TASK + "reset-generation-id";


  /**
   * The name of the attribute containing the baseDn related to the replication
   * domain to which applies the task.
   */
  public static final String ATTR_TASK_SET_GENERATION_ID_DOMAIN_DN =
    OC_RESET_GENERATION_ID_TASK + "-domain-base-dn";

  /**
   * The name of the attribute in an import task definition that specifies
   * whether the backend should be cleared before the import.
   */
  public static final String ATTR_IMPORT_CLEAR_BACKEND =
       NAME_PREFIX_TASK + "import-clear-backend";
}

