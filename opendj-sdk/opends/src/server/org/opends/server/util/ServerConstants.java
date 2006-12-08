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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006 Sun Microsystems, Inc.
 */
package org.opends.server.util;



/**
 * This class defines a set of constants that may be referenced throughout the
 * Directory Server source.
 */
public class ServerConstants
{
  /**
   * The end-of-line character for this platform.
   */
  public static final String EOL = System.getProperty("line.separator");



  /**
   * The value that will be used in the configuration for Boolean elements with
   * a value of "true".
   */
  public static final String CONFIG_VALUE_TRUE = "true";



  /**
   * The value that will be used in the configuration for Boolean elements with
   * a value of "false".
   */
  public static final String CONFIG_VALUE_FALSE = "false";



  /**
   * The date format string that will be used to construct and parse dates
   * represented in a form like UTC time, but using the local time zone.
   */
  public static final String DATE_FORMAT_COMPACT_LOCAL_TIME =
       "yyyyMMddHHmmss";



  /**
   * The date format string that will be used to construct and parse dates
   * represented using generalized time.  It is assumed that the provided date
   * formatter will be set to UTC.
   */
  public static final String DATE_FORMAT_GENERALIZED_TIME =
       "yyyyMMddHHmmss.SSS'Z'";



  /**
   * The date format string that will be used to construct and parse dates
   * represented using generalized time.  It is assumed that the provided date
   * formatter will be set to UTC.
   */
  public static final String DATE_FORMAT_LOCAL_TIME =
       "dd/MMM/yyyy:HH:mm:ss Z";



  /**
   * The date format string that will be used to construct and parse dates
   * represented using generalized time.  It is assumed that the provided date
   * formatter will be set to UTC.
   */
  public static final String DATE_FORMAT_UTC_TIME =
       "yyyyMMddHHmmss'Z'";



  /**
   * The name of the time zone for universal coordinated time (UTC).
   */
  public static final String TIME_ZONE_UTC = "UTC";



  /**
   * The name of the standard attribute that is used to specify the target DN in
   * an alias entry, formatted in all lowercase.
   */
  public static final String ATTR_ALIAS_DN = "aliasedobjectname";



  /**
   * The name of the standard attribute that is used to hold country names,
   * formatted in all lowercase.
   */
  public static final String ATTR_C = "c";



  /**
   * The name of the standard attribute that is used to hold common names,
   * formatted in all lowercase.
   */
  public static final String ATTR_COMMON_NAME = "cn";



  /**
   * The name of the attribute that is used to specify the number of connections
   * currently established, formatted in camel case.
   */
  public static final String ATTR_CURRENT_CONNS = "currentConnections";



  /**
   * The name of the attribute that is used to specify the number of connections
   * currently established, formatted in all lowercase.
   */
  public static final String ATTR_CURRENT_CONNS_LC = "currentconnections";



  /**
   * The name of the attribute that is used to specify the current time,
   * formatted in camel case.
   */
  public static final String ATTR_CURRENT_TIME = "currentTime";



  /**
   * The name of the attribute that is used to specify the current time,
   * formatted in all lowercase.
   */
  public static final String ATTR_CURRENT_TIME_LC = "currenttime";



  /**
   * The name of the standard attribute that is used to hold domain component
   * names, formatted in all lowercase.
   */
  public static final String ATTR_DC = "dc";



  /**
   * The name of the attribute that is used to specify the maximum number of
   * connections established at any time since startup, formatted in camel case.
   */
  public static final String ATTR_MAX_CONNS = "maxConnections";



  /**
   * The name of the attribute that is used to specify the maximum number of
   * connections established at any time since startup, formatted in all
   * lowercase.
   */
  public static final String ATTR_MAX_CONNS_LC = "maxconnections";



  /**
   * The name of the standard attribute that is used to specify the set of
   * public naming contexts (suffixes) for the Directory Server, formatted in
   * camel case.
   */
  public static final String ATTR_NAMING_CONTEXTS = "namingContexts";



  /**
   * The name of the standard attribute that is used to specify the set of
   * public naming contexts (suffixes) for the Directory Server, formatted in
   * all lowercase.
   */
  public static final String ATTR_NAMING_CONTEXTS_LC = "namingcontexts";



  /**
   * The name of the attribute used to hold the DNs that constitute the set of
   * "private" naming contexts registered with the server.
   */
  public static final String ATTR_PRIVATE_NAMING_CONTEXTS =
       "ds-private-naming-contexts";



  /**
   * The name of the standard attribute that is used to hold organization names,
   * formatted in all lowercase.
   */
  public static final String ATTR_O = "o";



  /**
   * The name of the standard attribute that is used to hold organizational unit
   * names, formatted in all lowercase.
   */
  public static final String ATTR_OU = "ou";



  /**
   * The name of the standard attribute that is used to specify the name of the
   * Directory Server product, formatted in camel case.
   */
  public static final String ATTR_PRODUCT_NAME = "productName";



  /**
   * The name of the standard attribute that is used to specify the name of the
   * Directory Server product, formatted in all lowercase.
   */
  public static final String ATTR_PRODUCT_NAME_LC = "productname";



  /**
   * The name of the standard attribute that is used to specify the set of
   * referral URLs in a smart referral entry, formatted in all lowercase.
   */
  public static final String ATTR_REFERRAL_URL = "ref";



  /**
   * The name of the standard attribute that is used to specify the location
   * for the Directory Server schema, formatted in camel case.
   */
  public static final String ATTR_SUBSCHEMA_SUBENTRY = "subschemaSubentry";



  /**
   * The name of the standard attribute that is used to specify the location
   * for the Directory Server schema, formatted in all lowercase.
   */
  public static final String ATTR_SUBSCHEMA_SUBENTRY_LC = "subschemasubentry";



  /**
   * The name of the standard attribute that is used to specify the names of the
   * authentication password schemes supported by the server, formatted in
   * camel case.
   */
  public static final String ATTR_SUPPORTED_AUTH_PW_SCHEMES =
       "supportedAuthPasswordSchemes";



  /**
   * The name of the standard attribute that is used to specify the names of the
   * authentication password schemes supported by the server, formatted in all
   * lowercase.
   */
  public static final String ATTR_SUPPORTED_AUTH_PW_SCHEMES_LC =
       "supportedauthpasswordschemes";



  /**
   * The name of the standard attribute that is used to specify the OIDs of the
   * controls supported by the server, formatted in camel case.
   */
  public static final String ATTR_SUPPORTED_CONTROL = "supportedControl";



  /**
   * The name of the standard attribute that is used to specify the OIDs of the
   * controls supported by the server, formatted in all lowercase.
   */
  public static final String ATTR_SUPPORTED_CONTROL_LC = "supportedcontrol";



  /**
   * The name of the standard attribute that is used to specify the OIDs of the
   * extended operations supported by the server, formatted in camel case.
   */
  public static final String ATTR_SUPPORTED_EXTENSION = "supportedExtension";



  /**
   * The name of the standard attribute that is used to specify the OIDs of the
   * extended operations supported by the server, formatted in all lowercase.
   */
  public static final String ATTR_SUPPORTED_EXTENSION_LC = "supportedextension";



  /**
   * The name of the standard attribute that is used to specify the OIDs of the
   * features supported by the server, formatted in camel case.
   */
  public static final String ATTR_SUPPORTED_FEATURE = "supportedFeatures";



  /**
   * The name of the standard attribute that is used to specify the OIDs of the
   * features supported by the server, formatted in all lowercase.
   */
  public static final String ATTR_SUPPORTED_FEATURE_LC = "supportedfeatures";



  /**
   * The name of the standard attribute that is used to specify the names of the
   * SASL mechanisms supported by the server, formatted in camel case.
   */
  public static final String ATTR_SUPPORTED_SASL_MECHANISMS =
       "supportedSASLMechanisms";



  /**
   * The name of the standard attribute that is used to specify the names of the
   * SASL mechanisms supported by the server, formatted in all lowercase.
   */
  public static final String ATTR_SUPPORTED_SASL_MECHANISMS_LC =
       "supportedsaslmechanisms";



  /**
   * The name of the attribute that is used to specify the time that the
   * Directory Server started, formatted in camel case.
   */
  public static final String ATTR_START_TIME = "startTime";



  /**
   * The name of the attribute that is used to specify the time that the
   * Directory Server started, formatted in all lowercase.
   */
  public static final String ATTR_START_TIME_LC = "starttime";



  /**
   * The name of the attribute that is used to specify the total number of
   * connections established since startup, formatted in camel case.
   */
  public static final String ATTR_TOTAL_CONNS = "totalConnections";



  /**
   * The name of the attribute that is used to specify the total number of
   * connections established since startup, formatted in all lowercase.
   */
  public static final String ATTR_TOTAL_CONNS_LC = "totalconnections";



  /**
   * The name of the attribute that is used to specify the length of time that
   * the server has been online, formatted in camel case.
   */
  public static final String ATTR_UP_TIME = "upTime";



  /**
   * The name of the attribute that is used to specify the length of time that
   * the server has been online, formatted in all lowercase.
   */
  public static final String ATTR_UP_TIME_LC = "uptime";



  /**
   * The name of the standard attribute that is used to specify the password for
   * a user, formatted in all lowercase.
   */
  public static final String ATTR_USER_PASSWORD = "userpassword";



  /**
   * The name of the standard attribute that is used to specify vendor name for
   * the Directory Server, formatted in camel case.
   */
  public static final String ATTR_VENDOR_NAME = "vendorName";



  /**
   * The name of the standard attribute that is used to specify vendor name for
   * the Directory Server, formatted in all lowercase.
   */
  public static final String ATTR_VENDOR_NAME_LC = "vendorname";



  /**
   * The name of the standard attribute that is used to specify vendor version
   * for the Directory Server, formatted in camel case.
   */
  public static final String ATTR_VENDOR_VERSION = "vendorVersion";



  /**
   * The name of the standard attribute that is used to specify vendor version
   * for the Directory Server, formatted in all lowercase.
   */
  public static final String ATTR_VENDOR_VERSION_LC = "vendorversion";



  /**
   * The name of the standard objectclass that is used to indicate that an entry
   * is an alias, formatted in all lowercase.
   */
  public static final String OC_ALIAS = "alias";



  /**
   * The name of the standard objectclass, formatted in all lowercase, that is
   * used to indicate that an entry describes a country.
   */
  public static final String OC_COUNTRY = "country";



  /**
   * The name of the standard objectclass, formatted in all lowercase, that is
   * used to indicate that an entry describes a domain.
   */
  public static final String OC_DOMAIN = "domain";


  /**
   * The name of the standard objectclass that is used to allow any attribute
   * type to be present in an entry, formatted in camel case.
   */
  public static final String OC_EXTENSIBLE_OBJECT = "extensibleObject";


  /**
   * The name of the standard objectclass that is used to allow any attribute
   * type to be present in an entry, formatted in all lowercase characters.
   */
  public static final String OC_EXTENSIBLE_OBJECT_LC = "extensibleobject";



  /**
   * The request OID for the cancel extended operation.
   */
  public static final String OID_CANCEL_REQUEST = "1.3.6.1.1.8";



  /**
   * The OID for the extensibleObject objectclass.
   */
  public static final String OID_EXTENSIBLE_OBJECT =
       "1.3.6.1.4.1.1466.101.120.111";



  /**
   * The request OID for the password modify extended operation.
   */
  public static final String OID_PASSWORD_MODIFY_REQUEST =
       "1.3.6.1.4.1.4203.1.11.1";



  /**
   * The request OID for the StartTLS extended operation.
   */
  public static final String OID_START_TLS_REQUEST = "1.3.6.1.4.1.1466.20037";



  /**
   * The request OID for the "Who Am I?" extended operation.
   */
  public static final String OID_WHO_AM_I_REQUEST =
       "1.3.6.1.4.1.4203.1.11.3";



  /**
   * The name of the standard "ldapSubentry" objectclass (which is a special
   * type of objectclass that makes a kind of "operational" entry), formatted
   * in camel case.
   */
  public static final String OC_LDAP_SUBENTRY = "ldapSubentry";



  /**
   * The name of the standard "ldapSubentry" objectclass (which is a special
   * type of objectclass that makes a kind of "operational" entry), formatted
   * in all lowercase.
   */
  public static final String OC_LDAP_SUBENTRY_LC = "ldapsubentry";



  /**
   * The name of the objectclass that will be used as the structural class for
   * monitor entries.
   */
  public static final String OC_MONITOR_ENTRY = "ds-monitor-entry";



  /**
   * The name of the standard objectclass, formatted in all lowercase, that is
   * used to indicate that an entry describes an organization.
   */
  public static final String OC_ORGANIZATION = "organization";



  /**
   * The name of the standard objectclass that is  used to indicate that an
   * entry describes an organizational unit.
   */
  public static final String OC_ORGANIZATIONAL_UNIT = "organizationalUnit";



  /**
   * The name of the organizationalUnit objectclass formatted in all lowercase
   * characters.
   */
  public static final String OC_ORGANIZATIONAL_UNIT_LC = "organizationalunit";



  /**
   * The name of the standard objectclass that is used to indicate that an entry
   * is a smart referral, formatted in all lowercase.
   */
  public static final String OC_REFERRAL = "referral";



  /**
   * The name of the structural objectclass that will be used for the Directory
   * Server root DSE entry.
   */
  public static final String OC_ROOT_DSE = "ds-rootDSE";



  /**
   * The name of the structural objectclass that will be used for the Directory
   * Server root DSE entry, formatted in all lowercase.
   */
  public static final String OC_ROOT_DSE_LC = "ds-rootdse";



  /**
   * The name of the standard "subschema" objectclass (which is used in entries
   * that publish schema information), formatted in all lowercase.
   */
  public static final String OC_SUBSCHEMA = "subschema";




  /**
   * The name of the standard "top" objectclass, which is the superclass for
   * virtually all other objectclasses, formatted in all lowercase.
   */
  public static final String OC_TOP= "top";



  /**
   * The name of the objectclass that can be used for generic entries for which
   * we don't have any other type of objectclass that is more appropriate.
   */
  public static final String OC_UNTYPED_OBJECT = "untypedObject";



  /**
   * The name of the untypedObject objectclass in all lowercase characters.
   */
  public static final String OC_UNTYPED_OBJECT_LC = "untypedobject";



  /**
   * The English name for the debug log category used for access control
   * debugging.
   */
  public static final String DEBUG_CATEGORY_ACCESS_CONTROL = "ACCESS_CONTROL";



  /**
   * The English name for the debug log category used for backend debugging.
   */
  public static final String DEBUG_CATEGORY_BACKEND = "BACKEND";



  /**
   * The English name for the debug log category used for configuration
   * debugging.
   */
  public static final String DEBUG_CATEGORY_CONFIG = "CONFIG";



  /**
   * The English name for the debug log category used for connection handling
   * debugging.
   */
  public static final String DEBUG_CATEGORY_CONNECTION_HANDLING = "CONNECTION";



  /**
   * The English name for the debug log category used for constructor debugging.
   */
  public static final String DEBUG_CATEGORY_CONSTRUCTOR = "CONSTRUCTOR";



  /**
   * The English name for the debug log category used for core server debugging.
   */
  public static final String DEBUG_CATEGORY_CORE_SERVER = "CORE";



  /**
   * The English name for the debug log category used for debugging raw data
   * read.
   */
  public static final String DEBUG_CATEGORY_DATA_READ = "DATA_READ";



  /**
   * The English name for the debug log category used for debugging raw data
   * written.
   */
  public static final String DEBUG_CATEGORY_DATA_WRITE = "DATA_WRITE";



  /**
   * The English name for the debug log category used for exception debugging.
   */
  public static final String DEBUG_CATEGORY_EXCEPTION = "EXCEPTION";



  /**
   * The English name for the debug log category used for extended operation
   * debugging.
   */
  public static final String DEBUG_CATEGORY_EXTENDED_OPERATION = "EXTENDED_OP";



  /**
   * The English name for the debug log category used for server extensions
   * debugging.
   */
  public static final String DEBUG_CATEGORY_EXTENSIONS = "EXTENSIONS";



  /**
   * The English name for the debug log category used for method entry
   * debugging.
   */
  public static final String DEBUG_CATEGORY_ENTER = "ENTER";



  /**
   * The English name for the debug log category used for password policy
   * debugging.
   */
  public static final String DEBUG_CATEGORY_PASSWORD_POLICY = "PWPOLICY";



  /**
   * The English name for the debug log category used for plugin debugging.
   */
  public static final String DEBUG_CATEGORY_PLUGIN = "PLUGIN";



  /**
   * The English name for the debug log category used for debugging protocol
   * elements read.
   */
  public static final String DEBUG_CATEGORY_PROTOCOL_READ = "PROTOCOL_READ";



  /**
   * The English name for the debug log category used for debugging protocol
   * elements written.
   */
  public static final String DEBUG_CATEGORY_PROTOCOL_WRITE = "PROTOCOL_WRITE";



  /**
   * The English name for the debug log category used for SASL debugging.
   */
  public static final String DEBUG_CATEGORY_SASL_MECHANISM = "SASL";



  /**
   * The English name for the debug log category used for schema debugging.
   */
  public static final String DEBUG_CATEGORY_SCHEMA = "SCHEMA";



  /**
   * The English name for the debug log category used for shutdown debugging.
   */
  public static final String DEBUG_CATEGORY_SHUTDOWN = "SHUTDOWN";



  /**
   * The English name for the debug log category used for startup debugging.
   */
  public static final String DEBUG_CATEGORY_STARTUP = "STARTUP";



  /**
   * The English name for the debug log category used for synchronization
   * debugging.
   */
  public static final String DEBUG_CATEGORY_SYNCHRONIZATION = "SYNCH";



  /**
   * The English name for the debug log category used for raw data read
   * from the database.
   */
  public static final String DEBUG_CATEGORY_DATABASE_READ = "DATABASE_READ";



  /**
   * The English name for the debug log category used for raw data written
   * to the database.
   */
  public static final String DEBUG_CATEGORY_DATABASE_WRITE = "DATABASE_WRITE";



  /**
   * The English name for the debug log category used for access to the
   * database.
   */
  public static final String DEBUG_CATEGORY_DATABASE_ACCESS = "DATABASE_ACCESS";



  /**
   * The English name for the debug log severity used for verbose messages.
   */
  public static final String DEBUG_SEVERITY_VERBOSE = "VERBOSE";



  /**
   * The English name for the debug log severity used for informational
   * messages.
   */
  public static final String DEBUG_SEVERITY_INFO = "INFO";



  /**
   * The English name for the debug log severity used for warning messages.
   */
  public static final String DEBUG_SEVERITY_WARNING = "WARNING";



  /**
   * The English name for the debug log severity used for error messages.
   */
  public static final String DEBUG_SEVERITY_ERROR = "ERROR";



  /**
   * The English name for the debug log severity used for messages related to
   * reading or writing data.
   */
  public static final String DEBUG_SEVERITY_COMMUNICATION = "COMMUNICATION";



  /**
   * The English name for the error log category used for access control
   * processing.
   */
  public static final String ERROR_CATEGORY_ACCESS_CONTROL = "ACCESS_CONTROL";



  /**
   * The English name for the error log category used for backend processing.
   */
  public static final String ERROR_CATEGORY_BACKEND = "BACKEND";



  /**
   * The English name for the error log category used for configuration
   * processing.
   */
  public static final String ERROR_CATEGORY_CONFIG = "CONFIG";



  /**
   * The English name for the error log category used for client connection
   * handling.
   */
  public static final String ERROR_CATEGORY_CONNECTION_HANDLING = "CONNECTION";



  /**
   * The English name for the error log category used for core server
   * processing.
   */
  public static final String ERROR_CATEGORY_CORE_SERVER = "CORE";



  /**
   * The English name for the error log category used for exception handling.
   */
  public static final String ERROR_CATEGORY_EXCEPTION = "EXCEPTION";



  /**
   * The English name for the error log category used for extended operation
   * processing.
   */
  public static final String ERROR_CATEGORY_EXTENDED_OPERATION = "EXTENDED_OP";



  /**
   * The English name for the error log category used for server extension
   * processing.
   */
  public static final String ERROR_CATEGORY_EXTENSIONS = "EXTENSIONS";



  /**
   * The English name for the error log category used for password policy
   * processing.
   */
  public static final String ERROR_CATEGORY_PASSWORD_POLICY = "PW_POLICY";



  /**
   * The English name for the error log category used for plugin processing.
   */
  public static final String ERROR_CATEGORY_PLUGIN = "PLUGIN";



  /**
   * The English name for the error log category used for request handling.
   */
  public static final String ERROR_CATEGORY_REQUEST = "REQUEST";



  /**
   * The English name for the error log category used for SASL processing.
   */
  public static final String ERROR_CATEGORY_SASL_MECHANISM = "SASL";



  /**
   * The English name for the error log category used for schema processing.
   */
  public static final String ERROR_CATEGORY_SCHEMA = "SCHEMA";



  /**
   * The English name for the error log category used for shutdown processing.
   */
  public static final String ERROR_CATEGORY_SHUTDOWN = "SHUTDOWN";



  /**
   * The English name for the error log category used for startup processing.
   */
  public static final String ERROR_CATEGORY_STARTUP = "STARTUP";



  /**
   * The English name for the error log category used for synchronization
   * processing.
   */
  public static final String ERROR_CATEGORY_SYNCHRONIZATION = "SYNCH";



  /**
   * The English name for the error log category used for task processing.
   */
  public static final String ERROR_CATEGORY_TASK = "TASK";



  /**
   * The English name for the error log severity used for fatal error messages.
   */
  public static final String ERROR_SEVERITY_FATAL = "FATAL_ERROR";



  /**
   * The English name for the error log severity used for generic debugging
   * messages.
   */
  public static final String ERROR_SEVERITY_DEBUG = "DEBUG";



  /**
   * The English name for the error log severity used for informational
   * messages.
   */
  public static final String ERROR_SEVERITY_INFORMATIONAL = "INFO";



  /**
   * The English name for the error log severity used for mild error messages.
   */
  public static final String ERROR_SEVERITY_MILD_ERROR = "MILD_ERROR";



  /**
   * The English name for the error log severity used for mild warning messages.
   */
  public static final String ERROR_SEVERITY_MILD_WARNING = "MILD_WARNING";



  /**
   * The English name for the error log severity used for important
   * informational messages.
   */
  public static final String ERROR_SEVERITY_NOTICE = "NOTICE";



  /**
   * The English name for the error log severity used for severe error messages.
   */
  public static final String ERROR_SEVERITY_SEVERE_ERROR = "SEVERE_ERROR";



  /**
   * The English name for the error log severity used for severe warning
   * messages.
   */
  public static final String ERROR_SEVERITY_SEVERE_WARNING = "SEVERE_WARNING";



  /**
   * The English name for the error log severity used for shutdown debug
   * messages.
   */
  public static final String ERROR_SEVERITY_SHUTDOWN_DEBUG = "SHUTDOWN";



  /**
   * The English name for the error log severity used for startup debug
   * messages.
   */
  public static final String ERROR_SEVERITY_STARTUP_DEBUG = "STARTUP";



  /**
   * The domain that will be used for JMX MBeans defined within the Directory
   * Server.
   */
  public static final String MBEAN_BASE_DOMAIN = "org.opends.server";



  /**
   * The description for the alert type that will be used for the alert
   * notification generated if a recurring task cannot be found to schedule the
   * next iteration after the previous iteration has completed.
   */
  public static final String ALERT_DESCRIPTION_CANNOT_FIND_RECURRING_TASK =
      "This alert type will be used to notify administrators if the " +
      "Directory Server is unable to locate a recurring task definition in " +
      "order to schedule the next iteration once the previous iteration has " +
      "completed.";



  /**
   * The alert type string that will be used for the alert notification
   * generated if a recurring task cannot be found to schedule the next
   * iteration after the previous iteration has completed.
   */
  public static final String ALERT_TYPE_CANNOT_FIND_RECURRING_TASK =
       "org.opends.server.CannotFindRecurringTask";



  /**
   * The description for the alert type that will be used for the alert
   * notification generated if an error occurs while attempting to rename the
   * current tasks backing file.
   */
  public static final String ALERT_DESCRIPTION_CANNOT_RENAME_CURRENT_TASK_FILE =
           "This alert type will be used to notify administrators if the " +
           "Directory Server is unable to rename the current tasks backing " +
           "file in the process of trying to write an updated version.";



  /**
   * The alert type string that will be used for the alert notification
   * generated if an error occurs while attempting to rename the current tasks
   * backing file.
   */
  public static final String ALERT_TYPE_CANNOT_RENAME_CURRENT_TASK_FILE =
       "org.opends.server.CannotRenameCurrentTaskFile";



  /**
   * The description for the alert type that will be used for the alert
   * notification generated if an error occurs while attempting to rename the
   * new tasks backing file.
   */
  public static final String ALERT_DESCRIPTION_CANNOT_RENAME_NEW_TASK_FILE =
           "This alert type will be used to notify administrators if the " +
           "Directory Server is unable to rename the new tasks backing " +
           "file into place.";



  /**
   * The alert type string that will be used for the alert notification
   * generated if an error occurs while attempting to rename the new tasks
   * backing file.
   */
  public static final String ALERT_TYPE_CANNOT_RENAME_NEW_TASK_FILE =
       "org.opends.server.CannotRenameNewTaskFile";



  /**
   * The description for the alert type that will be used for the alert
   * notification generated if an error occurs while attempting to schedule an
   * iteration of a recurring task.
   */
  public static final String
       ALERT_DESCRIPTION_CANNOT_SCHEDULE_RECURRING_ITERATION =
           "This alert type will be used to notify administrators if the " +
           "Directory Server is unable to schedule an iteration of a " +
           "recurring task.";



  /**
   * The alert type string that will be used for the alert notification
   * generated if an error occurs while attempting to schedule an iteration of a
   * recurring task.
   */
  public static final String ALERT_TYPE_CANNOT_SCHEDULE_RECURRING_ITERATION =
       "org.opends.server.CannotScheduleRecurringIteration";



  /**
   * The description for the alert type that will be used for the alert
   * notification generated if a problem occurs while attempting to write the
   * Directory Server configuration to disk.
   */
  public static final String ALERT_DESCRIPTION_CANNOT_WRITE_CONFIGURATION =
      "This alert type will be used to notify administrators if the " +
      "Directory Server is unable to write its updated configuration for " +
      "some reason and therefore the server may not exhibit the new " +
      "configuration if it is restarted.";



  /**
   * The alert type string that will be used for the alert notification
   * generated if a problem occurs while attempting to write the Directory
   * Server configuration to disk.
   */
  public static final String ALERT_TYPE_CANNOT_WRITE_CONFIGURATION =
       "org.opends.server.CannotWriteConfig";



  /**
   * The description for the alert type that will be used for the alert
   * notification generated if an error occurs while attempting to write the
   * tasks backing file.
   */
  public static final String ALERT_DESCRIPTION_CANNOT_WRITE_TASK_FILE =
           "This alert type will be used to notify administrators if the " +
           "Directory Server is unable to write an updated tasks backing " +
           "file for some reason.";



  /**
   * The alert type string that will be used for the alert notification
   * generated if an error occurs while attempting to write the tasks backing
   * file.
   */
  public static final String ALERT_TYPE_CANNOT_WRITE_TASK_FILE =
       "org.opends.server.CannotWriteTaskFile";



  /**
   * The description for the alert type that will be used for the alert
   * notification generated if consecutive failures in the LDAP connection
   * handler have caused it to become disabled.
   */
  public static final String
       ALERT_DESCRIPTION_LDAP_CONNECTION_HANDLER_CONSECUTIVE_FAILURES =
            "This alert type will be used to notify administrators of " +
            "consecutive failures that have occurred in the LDAP connection " +
            "handler that have caused it to become disabled.";



  /**
   * The alert type string that will be used for the alert notification
   * generated if consecutive failures in the LDAP connection handler have
   * caused it to become disabled.
   */
  public static final String
       ALERT_TYPE_LDAP_CONNECTION_HANDLER_CONSECUTIVE_FAILURES =
            "org.opends.server.LDAPHandlerDisabledByConsecutiveFailures";



  /**
   * The description for the alert type that will be used for the alert
   * notification generated if the LDAP connection handler encountered an
   * unexpected error that has caused it to become disabled.
   */
  public static final String
       ALERT_DESCRIPTION_LDAP_CONNECTION_HANDLER_UNCAUGHT_ERROR =
            "This alert type will be used to notify administrators of " +
            "uncaught errors in the LDAP connection handler that have caused " +
            "it to become disabled.";



  /**
   * The alert type string that will be used for the alert notification
   * generated if the LDAP connection handler encountered an unexpected error
   * that has caused it to become disabled.
   */
  public static final String
       ALERT_TYPE_LDAP_CONNECTION_HANDLER_UNCAUGHT_ERROR =
            "org.opends.server.LDAPHandlerUncaughtError";



  /**
   * The description for the alert type that will be used for the alert
   * notification generated when the Directory Server has completed its startup
   * process.
   */
  public static final String ALERT_DESCRIPTION_SERVER_STARTED =
      "This alert type will be used to provide notification that the " +
      "Directory Server has completed its startup process.";



  /**
   * The alert type string that will be used for the alert notification
   * generated when the Directory Server has completed its startup process.
   */
  public static final String ALERT_TYPE_SERVER_STARTED =
       "org.opends.server.DirectoryServerStarted";



  /**
   * The description for the alert type that will be used for the alert
   * notification generated when the Directory Server has started the shutdown
   * process.
   */
  public static final String ALERT_DESCRIPTION_SERVER_SHUTDOWN =
      "This alert type will be used to provide notification that the " +
      "Directory Server has begun the process of shutting down.";



  /**
   * The alert type string that will be used for the alert notification
   * generated when the Directory Server has started the shutdown process.
   */
  public static final String ALERT_TYPE_SERVER_SHUTDOWN =
       "org.opends.server.DirectoryServerShutdown";



  /**
   * The description for the alert type that will be used for the alert
   * notification generated by a thread that has died because of an uncaught
   * exception.
   */
  public static final String ALERT_DESCRIPTION_UNCAUGHT_EXCEPTION =
       "This alert type will be used if a Directory Server thread has " +
       "encountered an uncaught exception that caused that thread to " +
       "terminate abnormally.  The impact that this problem has on the " +
       "server depends on which thread was impacted and the nature of the " +
       "exception.";



  /**
   * The alert type string that will be used for the alert notification
   * generated by a thread that has died because of an uncaught exception.
   */
  public static final String ALERT_TYPE_UNCAUGHT_EXCEPTION =
       "org.opends.server.UncaughtException";



  /**
   * The name of the default password storage scheme that will be used for new
   * passwords.
   */
  public static final String DEFAULT_PASSWORD_STORAGE_SCHEME = "SSHA";



  /**
   * The maximum depth to which nested search filters will be processed.  This
   * can prevent stack overflow errors from filters that look like
   * "(&(&(&(&(&(&(&(&(&....".
   */
  public static final int MAX_NESTED_FILTER_DEPTH = 100;



  /**
   * The OID for the attribute type that represents the "objectclass" attribute.
   */
  public static final String OBJECTCLASS_ATTRIBUTE_TYPE_OID = "2.5.4.0";



  /**
   * The name of the attribute type that represents the "objectclass" attribute.
   */
  public static final String OBJECTCLASS_ATTRIBUTE_TYPE_NAME = "objectclass";



  /**
   * The value that will be used for the vendorName attribute in the root DSE.
   */
  public static final String SERVER_VENDOR_NAME = "Sun Microsystems, Inc.";



  /**
   * The name of the security mechanism that will be used for connections whose
   * communication is protected using the confidentiality features of
   * DIGEST-MD5.
   */
  public static final String SECURITY_MECHANISM_DIGEST_MD5_CONFIDENTIALITY =
       "DIGEST-MD5 Confidentiality";



  /**
   * The name of the security mechanism that will be used for connections whose
   * communication is protected using the confidentiality features of Kerberos.
   */
  public static final String SECURITY_MECHANISM_KERBEROS_CONFIDENTIALITY =
       "Kerberos Confidentiality";



  /**
   * The name of the security mechanism that will be used for connections
   * established using SSL.
   */
  public static final String SECURITY_MECHANISM_SSL = "SSL";



  /**
   * The name of the security mechanism that will be used for connections that
   * have established a secure session through StartTLS.
   */
  public static final String SECURITY_MECHANISM_START_TLS = "StartTLS";



  /**
   * The name of the SASL mechanism that does not provide any authentication but
   * rather uses anonymous access.
   */
  public static final String SASL_MECHANISM_ANONYMOUS = "ANONYMOUS";



  /**
   * The name of the SASL mechanism based on external authentication.
   */
  public static final String SASL_MECHANISM_EXTERNAL = "EXTERNAL";



  /**
   * The name of the SASL mechanism based on CRAM-MD5 authentication.
   */
  public static final String SASL_MECHANISM_CRAM_MD5 = "CRAM-MD5";



  /**
   * The name of the SASL mechanism based on DIGEST-MD5 authentication.
   */
  public static final String SASL_MECHANISM_DIGEST_MD5 = "DIGEST-MD5";



  /**
   * The name of the SASL mechanism based on GSS-API authentication.
   */
  public static final String SASL_MECHANISM_GSSAPI = "GSSAPI";



  /**
   * The name of the SASL mechanism based on PLAIN authentication.
   */
  public static final String SASL_MECHANISM_PLAIN = "PLAIN";



  /**
   * The OID for the account usable request and response controls.
   */
  public static final String OID_ACCOUNT_USABLE_CONTROL =
       "1.3.6.1.4.1.42.2.27.9.5.8";



  /**
   * The IANA-assigned OID for the feature allowing a user to request that all
   * operational attributes be returned.
   */
  public static final String OID_ALL_OPERATIONAL_ATTRS_FEATURE =
       "1.3.6.1.4.1.4203.1.5.1";



  /**
   * The OID for the authorization identity request control.
   */
  public static final String OID_AUTHZID_REQUEST = "2.16.840.1.113730.3.4.16";



  /**
   * The OID for the authorization identity response control.
   */
  public static final String OID_AUTHZID_RESPONSE = "2.16.840.1.113730.3.4.15";



  /**
   * The OID for the entry change notification control.
   */
  public static final String OID_ENTRY_CHANGE_NOTIFICATION =
       "2.16.840.1.113730.3.4.7";



  /**
   * The OID to include in the supportedFeatures list of the Directory Server
   * to indicate that it supports requesting attributes by objectclass.
   */
  public static final String OID_LDAP_ADLIST_FEATURE = "1.3.6.1.4.1.4203.1.5.2";



  /**
   * The IANA-assigned OID for the LDAP assertion control.
   */
  public static final String OID_LDAP_ASSERTION = "1.3.6.1.1.12";



  /**
   * The OID for the LDAP no-op control that was originally assigned in the
   * initial draft (draft-zeilenga-ldap-noop-00) from the OpenLDAP private
   * range.  Note that this reference has been removed in later drafts, but
   * given that at this time no official OID is assigned, we will use it for
   * now, and will continue to support it in the future (along with the real
   * OID).
   */
  public static final String OID_LDAP_NOOP_OPENLDAP_ASSIGNED =
       "1.3.6.1.4.1.4203.1.10.2";



  /**
   * The IANA-assigned OID for the LDAP readentry control used for retrieving an
   * entry in the state it had immediately before an update was applied.
   */
  public static final String OID_LDAP_READENTRY_PREREAD =
       "1.3.6.1.1.13.1";



  /**
   * The IANA-assigned OID for the LDAP readentry control used for retrieving an
   * entry in the state it had immediately after an update was applied.
   */
  public static final String OID_LDAP_READENTRY_POSTREAD =
       "1.3.6.1.1.13.2";



  /**
   * The OID for the LDAP subentries control used to indicate that matching
   * subentries should be returned.
   */
  public static final String OID_LDAP_SUBENTRIES = "1.3.6.1.4.1.7628.5.101.1";



  /**
   * The OID for the matched values control used to specify which particular
   * attribute values should be returned in a search result entry.
   */
  public static final String OID_MATCHED_VALUES = "1.2.826.0.1.3344810.2.3";



  /**
   * The IANA-assigned OID for the feature allowing the use of the increment
   * modification type.
   */
  public static final String OID_MODIFY_INCREMENT_FEATURE = "1.3.6.1.1.14";



  /**
   * The OID for the Netscape password expired control.
   */
  public static final String OID_NS_PASSWORD_EXPIRED =
       "2.16.840.1.113730.3.4.4";



  /**
   * The OID for the Netscape password expiring control.
   */
  public static final String OID_NS_PASSWORD_EXPIRING =
       "2.16.840.1.113730.3.4.5";



  /**
   * The OID for the password policy control from
   * draft-behera-ldap-password-policy.
   */
  public static final String OID_PASSWORD_POLICY_CONTROL =
       "1.3.6.1.4.1.42.2.27.8.5.1";



  /**
   * The OID for the persistent search control.
   */
  public static final String OID_PERSISTENT_SEARCH = "2.16.840.1.113730.3.4.3";



  /**
   * The OID for the proxied authorization v1 control.
   */
  public static final String OID_PROXIED_AUTH_V1 = "2.16.840.1.113730.3.4.12";



  /**
   * The OID for the proxied authorization v2 control.
   */
  public static final String OID_PROXIED_AUTH_V2 = "2.16.840.1.113730.3.4.18";



  /**
   * The OID for the subtree delete control.
   */
  public static final String OID_SUBTREE_DELETE_CONTROL =
       "1.2.840.113556.1.4.805";



  /**
   * The OID for the paged results control defined in RFC 2696.
   */
  public static final String OID_PAGED_RESULTS_CONTROL =
       "1.2.840.113556.1.4.319";



  /**
   * The OID for the ManageDsaIT control defined in RFC 3296.
   */
  public static final String OID_MANAGE_DSAIT_CONTROL =
       "2.16.840.1.113730.3.4.2";



  /**
   * The IANA-assigned OID for the feature allowing the use of LDAP true and
   * false filters.
   */
  public static final String OID_TRUE_FALSE_FILTERS_FEATURE =
       "1.3.6.1.4.1.4203.1.5.3";




  /**
   * The block length in bytes used when generating an HMAC-MD5 digest.
   */
  public static final int HMAC_MD5_BLOCK_LENGTH = 64;



  /**
   * The number of bytes in a raw MD5 digest.
   */
  public static final int MD5_DIGEST_LENGTH = 16;



  /**
   * The inner pad byte, which will be XORed with the shared secret for the
   * first CRAM-MD5 digest.
   */
  public static final byte CRAMMD5_IPAD_BYTE = 0x36;



  /**
   * The outer pad byte, which will be XORed with the shared secret for the
   * second CRAM-MD5 digest.
   */
  public static final byte CRAMMD5_OPAD_BYTE = 0x5C;



  /**
   * The name of the JAAS login module for Kerberos V.
   */
  public static final String JAAS_MODULE_KRB5 =
       "com.sun.security.auth.module.Krb5LoginModule";



  /**
   * The name of the JAAS property that specifies the path to the login
   * configuration file.
   */
  public static final String JAAS_PROPERTY_CONFIG_FILE =
       "java.security.auth.login.config";



  /**
   * The name of the JAAS property that indicates whether to allow JAAS
   * credentials to come from somewhere other than a GSS mechanism.
   */
  public static final String JAAS_PROPERTY_SUBJECT_CREDS_ONLY =
       "javax.security.auth.useSubjectCredsOnly";



  /**
   * The name of the Kerberos V property that specifies the address of the KDC.
   */
  public static final String KRBV_PROPERTY_KDC = "java.security.krb5.kdc";



  /**
   * The name of the Kerberos V property that specifies the realm to use.
   */
  public static final String KRBV_PROPERTY_REALM = "java.security.krb5.realm";



  /**
   * The name of the file (without path information) that should be used to hold
   * information about the backups contained in that directory.
   */
  public static final String BACKUP_DIRECTORY_DESCRIPTOR_FILE = "backup.info";



  /**
   * The name of the backup property that holds the base name of the archive
   * file containing the contents of the backup.
   */
  public static final String BACKUP_PROPERTY_ARCHIVE_FILENAME = "archive_file";



  /**
   * The name of the backup property that holds the name of the cipher algorithm
   * used to perform the encryption for the backup.
   */
  public static final String BACKUP_PROPERTY_CIPHER_ALGORITHM =
       "cipher_algorithm";



  /**
   * The name of the backup property that holds the name of the digest algorithm
   * used to generate the hash of a backup.
   */
  public static final String BACKUP_PROPERTY_DIGEST_ALGORITHM =
       "digest_algorithm";



  /**
   * The name of the backup property that holds the name of the MAC algorithm
   * used to generate the signed hash of a backup.
   */
  public static final String BACKUP_PROPERTY_MAC_ALGORITHM = "mac_algorithm";



  /**
   * The base filename to use for the archive file containing a backup of the
   * server configuration.
   */
  public static final String CONFIG_BACKUP_BASE_FILENAME = "config-backup-";



  /**
   * The base filename to use for the archive file containing a backup of the
   * server schema.
   */
  public static final String SCHEMA_BACKUP_BASE_FILENAME = "schema-backup-";



  /**
   * The name of the directory in which lock files will be placed.
   */
  public static final String LOCKS_DIRECTORY = "locks";



  /**
   * The prefix that will be used for lock filenames used for Directory Server
   * backends.
   */
  public static final String BACKEND_LOCK_FILE_PREFIX = "backend-";



  /**
   * The name that will be used for the server-wide lock to prevent multiple
   * instances of the server from running concurrently.
   */
  public static final String SERVER_LOCK_FILE_NAME = "server";



  /**
   * The suffix that will be used for all lock files created by the Directory
   * Server.
   */
  public static final String LOCK_FILE_SUFFIX = ".lock";



  /**
   * The name of the schema property that will be used to specify the path to
   * the schema file from which the schema element was loaded.
   */
  public static final String SCHEMA_PROPERTY_FILENAME = "X-SCHEMA-FILE";



  /**
   * The abbreviated unit that should be used for a size specified in bytes.
   */
  public static final String SIZE_UNIT_BYTES_ABBR = "b";



  /**
   * The full unit that should be used for a size specified in bytes.
   */
  public static final String SIZE_UNIT_BYTES_FULL = "bytes";



  /**
   * The abbreviated unit that should be used for a size specified in kilobytes.
   */
  public static final String SIZE_UNIT_KILOBYTES_ABBR = "kb";



  /**
   * The full unit that should be used for a size specified in kilobytes.
   */
  public static final String SIZE_UNIT_KILOBYTES_FULL = "kilobytes";



  /**
   * The abbreviated unit that should be used for a size specified in kibibytes.
   */
  public static final String SIZE_UNIT_KIBIBYTES_ABBR = "kib";



  /**
   * The full unit that should be used for a size specified in kibibytes.
   */
  public static final String SIZE_UNIT_KIBIBYTES_FULL = "kibibytes";



  /**
   * The abbreviated unit that should be used for a size specified in megabytes.
   */
  public static final String SIZE_UNIT_MEGABYTES_ABBR = "mb";



  /**
   * The full unit that should be used for a size specified in megabytes.
   */
  public static final String SIZE_UNIT_MEGABYTES_FULL = "megabytes";



  /**
   * The abbreviated unit that should be used for a size specified in mebibytes.
   */
  public static final String SIZE_UNIT_MEBIBYTES_ABBR = "mib";



  /**
   * The full unit that should be used for a size specified in mebibytes.
   */
  public static final String SIZE_UNIT_MEBIBYTES_FULL = "mebibytes";



  /**
   * The abbreviated unit that should be used for a size specified in gigabytes.
   */
  public static final String SIZE_UNIT_GIGABYTES_ABBR = "gb";



  /**
   * The full unit that should be used for a size specified in gigabytes.
   */
  public static final String SIZE_UNIT_GIGABYTES_FULL = "gigabytes";



  /**
   * The abbreviated unit that should be used for a size specified in gibibytes.
   */
  public static final String SIZE_UNIT_GIBIBYTES_ABBR = "gib";



  /**
   * The full unit that should be used for a size specified in gibibytes.
   */
  public static final String SIZE_UNIT_GIBIBYTES_FULL = "gibibytes";



  /**
   * The abbreviated unit that should be used for a size specified in terabytes.
   */
  public static final String SIZE_UNIT_TERABYTES_ABBR = "tb";



  /**
   * The full unit that should be used for a size specified in terabytes.
   */
  public static final String SIZE_UNIT_TERABYTES_FULL = "terabytes";



  /**
   * The abbreviated unit that should be used for a size specified in tebibytes.
   */
  public static final String SIZE_UNIT_TEBIBYTES_ABBR = "tib";



  /**
   * The full unit that should be used for a size specified in tebibytes.
   */
  public static final String SIZE_UNIT_TEBIBYTES_FULL = "tebibytes";



  /**
   * The abbreviated unit that should be used for a time specified in
   * nanoseconds.
   */
  public static final String TIME_UNIT_NANOSECONDS_ABBR = "ns";



  /**
   * The full unit that should be used for a time specified in nanoseconds.
   */
  public static final String TIME_UNIT_NANOSECONDS_FULL = "nanoseconds";



  /**
   * The abbreviated unit that should be used for a time specified in
   * microseconds.
   */
  public static final String TIME_UNIT_MICROSECONDS_ABBR = "us";



  /**
   * The full unit that should be used for a time specified in microseconds.
   */
  public static final String TIME_UNIT_MICROSECONDS_FULL = "microseconds";



  /**
   * The abbreviated unit that should be used for a time specified in
   * milliseconds.
   */
  public static final String TIME_UNIT_MILLISECONDS_ABBR = "ms";



  /**
   * The full unit that should be used for a time specified in milliseconds.
   */
  public static final String TIME_UNIT_MILLISECONDS_FULL = "milliseconds";



  /**
   * The abbreviated unit that should be used for a time specified in seconds.
   */
  public static final String TIME_UNIT_SECONDS_ABBR = "s";



  /**
   * The full unit that should be used for a time specified in seconds.
   */
  public static final String TIME_UNIT_SECONDS_FULL = "seconds";



  /**
   * The abbreviated unit that should be used for a time specified in minutes.
   */
  public static final String TIME_UNIT_MINUTES_ABBR = "m";



  /**
   * The full unit that should be used for a time specified in minutes.
   */
  public static final String TIME_UNIT_MINUTES_FULL = "minutes";



  /**
   * The abbreviated unit that should be used for a time specified in hours.
   */
  public static final String TIME_UNIT_HOURS_ABBR = "h";



  /**
   * The full unit that should be used for a time specified in hours.
   */
  public static final String TIME_UNIT_HOURS_FULL = "hours";



  /**
   * The abbreviated unit that should be used for a time specified in days.
   */
  public static final String TIME_UNIT_DAYS_ABBR = "d";



  /**
   * The full unit that should be used for a time specified in days.
   */
  public static final String TIME_UNIT_DAYS_FULL = "days";



  /**
   * The abbreviated unit that should be used for a time specified in weeks.
   */
  public static final String TIME_UNIT_WEEKS_ABBR = "w";



  /**
   * The full unit that should be used for a time specified in weeks.
   */
  public static final String TIME_UNIT_WEEKS_FULL = "weeks";



  /**
   * The name of the system property that can be used to indicate whether
   * components should be allowed to use the <CODE>Runtime.exec</CODE> method.
   * If this property is set and the value is anything other than "false",
   * "off", "no", or "0", then components should not allow the use of the
   * <CODE>exec</CODE> method.
   */
  public static final String PROPERTY_DISABLE_EXEC =
       "org.opends.server.DisableExec";



  /**
   * The name of the system property that can be used to determine whether all
   * <CODE>DirectoryThread</CODE> instances should be created as daemon threads
   * regardless of whether they would otherwise be configured that way.
   */
  public static final String PROPERTY_FORCE_DAEMON_THREADS =
       "org.opends.server.ForceDaemonThreads";



  /**
   * The name of a command-line script used to launch an administrative tool.
   */
  public static final String PROPERTY_SCRIPT_NAME =
       "org.opends.server.scriptName";



  /**
   * The name of the system property that can be used to specify the path to the
   * server root.
   */
  public static final String PROPERTY_SERVER_ROOT =
       "org.opends.server.ServerRoot";



  /**
   * The column at which to wrap long lines of output in the command-line tools.
   */
  public static final int MAX_LINE_WIDTH = 79;
}

