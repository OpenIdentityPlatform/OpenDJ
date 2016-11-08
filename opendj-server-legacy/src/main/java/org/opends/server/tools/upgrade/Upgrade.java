/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.tools.upgrade;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.core.LockFileManager;
import org.opends.server.tools.upgrade.UpgradeTasks.UpgradeCondition;
import org.opends.server.util.BuildVersion;

import com.forgerock.opendj.cli.ClientException;
import com.forgerock.opendj.cli.ReturnCode;

import static com.forgerock.opendj.cli.Utils.*;
import static javax.security.auth.callback.ConfirmationCallback.*;
import static javax.security.auth.callback.TextOutputCallback.WARNING;

import static org.opends.messages.ToolMessages.*;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.tools.upgrade.FormattedNotificationCallback.*;
import static org.opends.server.tools.upgrade.LicenseFile.*;
import static org.opends.server.tools.upgrade.UpgradeTasks.*;
import static org.opends.server.tools.upgrade.UpgradeUtils.batDirectory;
import static org.opends.server.tools.upgrade.UpgradeUtils.binDirectory;
import static org.opends.server.tools.upgrade.UpgradeUtils.instanceContainsJeBackends;
import static org.opends.server.tools.upgrade.UpgradeUtils.libDirectory;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class contains the table of upgrade tasks that need performing when
 * upgrading from one version to another.
 */
public final class Upgrade
{
  /** Upgrade's logger. */
  private static LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** Minimum version for which upgrade is supported. */
  private static BuildVersion UPGRADE_SUPPORTS_VERSION_FROM = BuildVersion.valueOf("2.6.0");

  /** The success exit code value. */
  static final int EXIT_CODE_SUCCESS = 0;
  /** The error exit code value. */
  static final int EXIT_CODE_ERROR = 1;

  private static final String LOCAL_DB_BACKEND_OBJECT_CLASS = "ds-cfg-local-db-backend";
  private static final String JE_BACKEND_OBJECT_CLASS = "ds-cfg-je-backend";

  /** If the upgrade contains some post upgrade tasks to do. */
  private static boolean hasPostUpgradeTask;

  /** If the upgrade script should exit with error code (useful for warnings) */
  private static boolean exitWithErrorCode;

  /** Developers should register upgrade tasks below. */
  private static final NavigableMap<BuildVersion, List<UpgradeTask>> TASKS = new TreeMap<>();
  private static final List<UpgradeTask> MANDATORY_TASKS = new LinkedList<>();

  static
  {
    // @formatter:off
    /* See OPENDJ-1284 */
    register("2.8.0", // userCertificate OID / cACertificate OID
        newAttributeTypes(INFO_UPGRADE_TASK_10133_1_SUMMARY.get(),
        "00-core.ldif", "2.5.4.36", "2.5.4.37"),
        addConfigEntry(INFO_UPGRADE_TASK_10133_2_SUMMARY.get(),
        "dn: cn=Certificate Exact Matching Rule,cn=Matching Rules,cn=config",
        "changetype: add",
        "objectClass: top",
        "objectClass: ds-cfg-matching-rule",
        "objectClass: ds-cfg-equality-matching-rule",
        "cn: Certificate Exact Matching Rule",
        "ds-cfg-java-class: "
            + "org.opends.server.schema.CertificateExactMatchingRuleFactory",
        "ds-cfg-enabled: true"));


    /* See OPENDJ-1295 */
    register("2.8.0",
        copySchemaFile("03-pwpolicyextension.ldif"));

    /* See OPENDJ-1490 and OPENDJ-1454 */
    register("2.8.0",
        deleteConfigEntry(INFO_UPGRADE_TASK_10733_1_SUMMARY.get(),
        "dn: ds-cfg-backend-id=replicationChanges,cn=Backends,cn=config"),
        modifyConfigEntry(INFO_UPGRADE_TASK_10733_2_SUMMARY.get(),
        "(objectClass=ds-cfg-dsee-compat-access-control-handler)",
        "delete: ds-cfg-global-aci",
        "ds-cfg-global-aci: "
            + "(target=\"ldap:///dc=replicationchanges\")"
            + "(targetattr=\"*\")"
            + "(version 3.0; acl \"Replication backend access\"; "
            + "deny (all) userdn=\"ldap:///anyone\";)"));

    /* See OPENDJ-1351 */
    register("2.8.0",
        modifyConfigEntry(INFO_UPGRADE_TASK_10820_SUMMARY.get(),
        "(objectClass=ds-cfg-root-dn)",
        "add: ds-cfg-default-root-privilege-name",
        "ds-cfg-default-root-privilege-name: changelog-read"));

    /* See OPENDJ-1580 */
    register("2.8.0",
        addConfigEntry(INFO_UPGRADE_TASK_10908_SUMMARY.get(),
            "dn: cn=PKCS5S2,cn=Password Storage Schemes,cn=config",
            "changetype: add",
            "objectClass: top",
            "objectClass: ds-cfg-password-storage-scheme",
            "objectClass: ds-cfg-pkcs5s2-password-storage-scheme",
            "cn: PKCS5S2",
            "ds-cfg-java-class: org.opends.server.extensions.PKCS5S2PasswordStorageScheme",
            "ds-cfg-enabled: true"));

    register("2.8.0",
        modifyConfigEntry(INFO_UPGRADE_TASK_10214_SUMMARY.get(),
          "(ds-cfg-java-class=org.opends.server.loggers.debug.TextDebugLogPublisher)",
          "delete:ds-cfg-java-class",
          "-",
          "add:ds-cfg-java-class",
          "ds-cfg-java-class: org.opends.server.loggers.TextDebugLogPublisher"));

    register("2.8.0",
        modifyConfigEntry(INFO_UPGRADE_TASK_10232_SUMMARY.get(),
          "(objectclass=ds-cfg-file-based-debug-log-publisher)",
          "delete:ds-cfg-default-debug-level"));

    register("2.8.0",
        modifyConfigEntry(INFO_UPGRADE_TASK_10329_SUMMARY.get(),
            "&(objectclass=ds-cfg-file-based-error-log-publisher)(cn=File-Based Error Logger)",
            "delete:ds-cfg-default-severity",
            "ds-cfg-default-severity: severe-warning",
            "ds-cfg-default-severity: severe-error",
            "ds-cfg-default-severity: fatal-error",
            "-",
            "add:ds-cfg-default-severity",
            "ds-cfg-default-severity: error",
            "ds-cfg-default-severity: warning"
            ));

    register("2.8.0",
        modifyConfigEntry(INFO_UPGRADE_TASK_10339_SUMMARY.get(),
            "&(objectclass=ds-cfg-file-based-error-log-publisher)(cn=Replication Repair Logger)",
            "delete:ds-cfg-override-severity",
             "-",
             "add:ds-cfg-override-severity",
             "ds-cfg-override-severity: SYNC=INFO,ERROR,WARNING,NOTICE"));

    /* See OPENDJ-1545 */
    register("2.8.0",
        deleteConfigEntry(INFO_UPGRADE_TASK_11237_1_SUMMARY.get(),
            "dn: cn=Network Groups,cn=config"),
        deleteConfigEntry(INFO_UPGRADE_TASK_11237_2_SUMMARY.get(),
            "dn: cn=Workflows,cn=config"),
        deleteConfigEntry(INFO_UPGRADE_TASK_11237_3_SUMMARY.get(),
            "dn: cn=Workflow Elements,cn=config"));
    register("2.8.0",
        deleteConfigEntry(INFO_UPGRADE_TASK_11239_SUMMARY.get(),
            "dn: cn=Network Group,cn=Plugins,cn=config"));
    register("2.8.0",
        deleteConfigEntry(INFO_UPGRADE_TASK_11339_SUMMARY.get(),
            "dn: cn=Extensions,cn=config"));

    /* See OPENDJ-1701 */
    register("2.8.0",
        deleteConfigEntry(INFO_UPGRADE_TASK_11476_SUMMARY.get(),
            "dn: cn=File System,cn=Entry Caches,cn=config"));

    /* See OPENDJ-1869 */
    register("2.8.0",
        modifyConfigEntry(INFO_UPGRADE_TASK_12226_SUMMARY.get(),
            "(objectclass=ds-cfg-root-config)",
            "delete: ds-cfg-entry-cache-preload"));

    /* See OPENDJ-2054 */
    register("2.8.0",
        deleteFile(new File(binDirectory, "dsframework")),
        deleteFile(new File(batDirectory, "dsframework.bat")));

    /* If the upgraded version is a non OEM one, migrates local-db backends to JE Backend, see OPENDJ-2364 **/
    register("3.0.0",
        conditionalUpgradeTasks(
          new UpgradeCondition() {
              @Override
              public boolean shouldPerformUpgradeTasks(UpgradeContext context) throws ClientException {
                return !isOEMVersion();
              }

              @Override
              public String toString() {
                return "!isOEMVersion";
              }
          },
          migrateLocalDBBackendsToJEBackends(),
          modifyConfigEntry(INFO_UPGRADE_TASK_MIGRATE_JE_SUMMARY_2.get(),
              "(objectClass=ds-cfg-local-db-backend)",
              "replace: objectClass",
              "objectClass: top",
              "objectClass: ds-cfg-backend",
              "objectClass: ds-cfg-pluggable-backend",
              "objectClass: ds-cfg-je-backend",
              "-",
              "replace: ds-cfg-java-class",
              "ds-cfg-java-class: org.opends.server.backends.jeb.JEBackend",
              "-",
              "delete: ds-cfg-import-thread-count",
              "-",
              "delete: ds-cfg-import-queue-size",
              "-",
              "delete: ds-cfg-subordinate-indexes-enabled",
              "-"
          ),
          modifyConfigEntry(INFO_UPGRADE_TASK_MIGRATE_JE_SUMMARY_3.get(),
              "(objectClass=ds-cfg-local-db-index)",
              "replace: objectClass",
              "objectClass: top",
              "objectClass: ds-cfg-backend-index",
              "-"
          ),
          modifyConfigEntry(INFO_UPGRADE_TASK_MIGRATE_JE_SUMMARY_4.get(),
              "(objectClass=ds-cfg-local-db-vlv-index)",
              "replace: objectClass",
              "objectClass: top",
              "objectClass: ds-cfg-backend-vlv-index",
              "-",
              "delete: ds-cfg-max-block-size",
              "-"
          )
        )
    );

    /* If the upgraded version is OEM, migrates local-db backends to PDB, see OPENDJ-2364 **/
    register("3.0.0",
      conditionalUpgradeTasks(
        new UpgradeCondition() {
          @Override
          public boolean shouldPerformUpgradeTasks(UpgradeContext context) throws ClientException {
            return isOEMVersion();
          }

          @Override
          public String toString() {
            return "isOEMVersion";
          }
        },
        deleteFile(new File(libDirectory, "je.jar")),
        requireConfirmation(INFO_UPGRADE_TASK_LOCAL_DB_TO_PDB_1_SUMMARY.get("3.0.0"), NO,
                renameLocalDBBackendDirectories(LOCAL_DB_BACKEND_OBJECT_CLASS),
                convertJEBackendsToPDBBackends(LOCAL_DB_BACKEND_OBJECT_CLASS),
                // Convert JE backend indexes to PDB backend indexes.
                modifyConfigEntry(INFO_UPGRADE_TASK_LOCAL_DB_TO_PDB_3_SUMMARY.get(),
                        "(objectclass=ds-cfg-local-db-index)",
                        "delete: objectclass",
                        "objectclass: ds-cfg-local-db-index",
                        "-",
                        "add: objectclass",
                        "objectclass: ds-cfg-backend-index",
                        "-"
                ),
                // Convert JE backend VLV indexes to PDB backend VLV indexes.
                modifyConfigEntry(INFO_UPGRADE_TASK_LOCAL_DB_TO_PDB_4_SUMMARY.get(),
                        "(objectclass=ds-cfg-local-db-vlv-index)",
                        "delete: objectclass",
                        "objectclass: ds-cfg-local-db-vlv-index",
                        "-",
                        "add: objectclass",
                        "objectclass: ds-cfg-backend-vlv-index",
                        "-",
                        "delete: ds-cfg-max-block-size",
                        "-"
                )
        )
      )
    );

    /* Remove dbtest tool (replaced by backendstat in 3.0.0) - see OPENDJ-1791 **/
    register("3.0.0",
            deleteFile(new File(binDirectory, "dbtest")),
            deleteFile(new File(batDirectory, "dbtest.bat")));

    /*
     * Rebuild all indexes when upgrading to 3.0.0.
     *
     * 1) matching rules have changed in 2.8.0 and again in 3.0.0- see OPENDJ-1637
     * 2) JE backend has been migrated to pluggable architecture.
     */
    register("3.0.0",
            rebuildAllIndexes(INFO_UPGRADE_TASK_11260_SUMMARY.get()));

    /* See OPENDJ-1742 */
    register("3.0.0",
        clearReplicationDbDirectory());

    /* See OPENDJ-2435 */
    register("3.5.0",
        addConfigEntry(INFO_UPGRADE_TASK_BCRYPT_SCHEME_SUMMARY.get(),
            "dn: cn=Bcrypt,cn=Password Storage Schemes,cn=config",
            "changetype: add",
            "objectClass: top",
            "objectClass: ds-cfg-password-storage-scheme",
            "objectClass: ds-cfg-bcrypt-password-storage-scheme",
            "cn: Bcrypt",
            "ds-cfg-java-class: org.opends.server.extensions.BcryptPasswordStorageScheme",
            "ds-cfg-enabled: true"));

    /* See OPENDJ-2683 */
    register("3.5.0",
        deleteConfigEntry(INFO_UPGRADE_TASK_REMOVE_MATCHING_RULES.get(),
        "cn=Auth Password Exact Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=Bit String Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=Boolean Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=Case Exact Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=Case Exact Ordering Matching Rule,cn=Matching Rules,cn=config",
        "cn=Case Exact Substring Matching Rule,cn=Matching Rules,cn=config",
        "cn=Case Exact IA5 Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=Case Exact IA5 Substring Matching Rule,cn=Matching Rules,cn=config",
        "cn=Case Ignore Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=Case Ignore Ordering Matching Rule,cn=Matching Rules,cn=config",
        "cn=Case Ignore Substring Matching Rule,cn=Matching Rules,cn=config",
        "cn=Case Ignore IA5 Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=Case Ignore IA5 Substring Matching Rule,cn=Matching Rules,cn=config",
        "cn=Case Ignore List Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=Case Ignore List Substring Matching Rule,cn=Matching Rules,cn=config",
        "cn=Certificate Exact Matching Rule,cn=Matching Rules,cn=config",
        "cn=Directory String First Component Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=Distinguished Name Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=Double Metaphone Approximate Matching Rule,cn=Matching Rules,cn=config",
        "cn=Generalized Time Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=Generalized Time Ordering Matching Rule,cn=Matching Rules,cn=config",
        "cn=Integer Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=Integer Ordering Matching Rule,cn=Matching Rules,cn=config",
        "cn=Integer First Component Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=Keyword Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=Numeric String Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=Numeric String Ordering Matching Rule,cn=Matching Rules,cn=config",
        "cn=Numeric String Substring Matching Rule,cn=Matching Rules,cn=config",
        "cn=Object Identifier Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=Object Identifier First Component Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=Octet String Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=Octet String Ordering Matching Rule,cn=Matching Rules,cn=config",
        "cn=Octet String Substring Matching Rule,cn=Matching Rules,cn=config",
        "cn=Presentation Address Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=Protocol Information Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=Telephone Number Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=Telephone Number Substring Matching Rule,cn=Matching Rules,cn=config",
        "cn=Time Based Matching Rule,cn=Matching Rules,cn=config",
        "cn=Unique Member Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=User Password Exact Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=UUID Equality Matching Rule,cn=Matching Rules,cn=config",
        "cn=UUID Ordering Matching Rule,cn=Matching Rules,cn=config",
        "cn=Word Equality Matching Rule,cn=Matching Rules,cn=config"));

    /* see OPENDJ-2730 */
    register("3.5.0", removeOldJarFiles());

    register("3.5.0",
        rebuildIndexesNamed(INFO_UPGRADE_REBUILD_INDEXES_DISTINGUISHED_NAME.get(),
            "." + EMR_DN_NAME, "." + EMR_OID_NAME, "." + EMR_UNIQUE_MEMBER_NAME, "." + EMR_CERTIFICATE_EXACT_NAME));

    register("3.5.0",
        deleteConfigEntry(INFO_UPGRADE_TASK_CONFIGURATION_BACKEND_NOT_CONFIGURABLE.get(),
            "dn: ds-cfg-backend-id=config,cn=Backends,cn=config"));

    register("3.5.0",
        restoreCsvDelimiterAttributeTypeInConcatenatedSchemaFile());

    register("3.5.0",
        requireConfirmation(INFO_UPGRADE_TASK_CONFIRM_DISABLING_HTTP_CONNECTION_HANDLER.get(), YES,
            modifyConfigEntry(INFO_UPGRADE_TASK_DISABLING_HTTP_CONNECTION_HANDLER.get(),
                    "(objectclass=ds-cfg-http-connection-handler)",
                    "replace: ds-cfg-enabled",
                    "ds-cfg-enabled: false",
                    "-",
                    "delete: ds-cfg-authentication-required",
                    "-",
                    "delete: ds-cfg-config-file",
                    "-"
            )
        ),
        addConfigEntry(INFO_UPGRADE_TASK_ADDING_DEFAULT_HTTP_ENDPOINTS_AND_AUTH.get(),
                "dn: cn=HTTP Endpoints,cn=config",
                "objectClass: top",
                "objectClass: ds-cfg-branch",
                "cn: HTTP Endpoints"
        ),
        addConfigEntry(
                "dn: ds-cfg-base-path=/api,cn=HTTP Endpoints,cn=config",
                "objectClass: top",
                "objectClass: ds-cfg-http-endpoint",
                "objectClass: ds-cfg-rest2ldap-endpoint",
                "ds-cfg-enabled: true",
                "ds-cfg-java-class: org.opends.server.protocols.http.rest2ldap.Rest2LdapEndpoint",
                "ds-cfg-base-path: /api",
                "ds-cfg-config-directory: config/rest2ldap/endpoints/api",
                "ds-cfg-http-authorization-mechanism: cn=HTTP Basic,cn=HTTP Authorization Mechanisms,cn=config"
        ),
        addConfigEntry(
                "dn: ds-cfg-base-path=/admin,cn=HTTP Endpoints,cn=config",
                "objectClass: top",
                "objectClass: ds-cfg-http-endpoint",
                "objectClass: ds-cfg-admin-endpoint",
                "ds-cfg-enabled: true",
                "ds-cfg-base-path: /admin",
                "ds-cfg-java-class: org.opends.server.protocols.http.rest2ldap.AdminEndpoint",
                "ds-cfg-http-authorization-mechanism: cn=HTTP Basic,cn=HTTP Authorization Mechanisms,cn=config"
        ),
        addConfigEntry(
                "dn: cn=HTTP Authorization Mechanisms,cn=config",
                "objectClass: top",
                "objectClass: ds-cfg-branch",
                "cn: HTTP Authorizations"
        ),
        addConfigEntry(
                "dn: cn=HTTP Anonymous,cn=HTTP Authorization Mechanisms,cn=config",
                "objectClass: top",
                "objectClass: ds-cfg-http-authorization-mechanism",
                "objectClass: ds-cfg-http-anonymous-authorization-mechanism",
                "cn: HTTP Anonymous",
                "ds-cfg-enabled: true",
                "ds-cfg-java-class: org.opends.server.protocols.http.authz.HttpAnonymousAuthorizationMechanism"
        ),
        addConfigEntry(
                "dn: cn=HTTP Basic,cn=HTTP Authorization Mechanisms,cn=config",
                "objectClass: top",
                "objectClass: ds-cfg-http-authorization-mechanism",
                "objectClass: ds-cfg-http-basic-authorization-mechanism",
                "cn: HTTP Basic",
                "ds-cfg-java-class: org.opends.server.protocols.http.authz.HttpBasicAuthorizationMechanism",
                "ds-cfg-enabled: true",
                "ds-cfg-http-basic-alt-authentication-enabled: true",
                "ds-cfg-http-basic-alt-username-header: X-OpenIDM-Username",
                "ds-cfg-http-basic-alt-password-header: X-OpenIDM-Password",
                "ds-cfg-identity-mapper: cn=Exact Match,cn=Identity Mappers,cn=config"
        ),
        addConfigEntry(
                "dn: cn=HTTP OAuth2 CTS,cn=HTTP Authorization Mechanisms,cn=config",
                "objectClass: top",
                "objectClass: ds-cfg-http-authorization-mechanism",
                "objectClass: ds-cfg-http-oauth2-authorization-mechanism",
                "objectClass: ds-cfg-http-oauth2-cts-authorization-mechanism",
                "cn: HTTP OAuth2 CTS",
                "ds-cfg-java-class: org.opends.server.protocols.http.authz.HttpOAuth2CtsAuthorizationMechanism",
                "ds-cfg-enabled: false",
                "ds-cfg-cts-base-dn: ou=famrecords,ou=openam-session,ou=tokens,dc=example,dc=com",
                "ds-cfg-oauth2-authzid-json-pointer: userName/0",
                "ds-cfg-identity-mapper: cn=Exact Match,cn=Identity Mappers,cn=config",
                "ds-cfg-oauth2-required-scope: read",
                "ds-cfg-oauth2-required-scope: write",
                "ds-cfg-oauth2-required-scope: uid",
                "ds-cfg-oauth2-access-token-cache-enabled: false",
                "ds-cfg-oauth2-access-token-cache-expiration: 300s"
        ),
        addConfigEntry(
                "dn: cn=HTTP OAuth2 OpenAM,cn=HTTP Authorization Mechanisms,cn=config",
                "objectClass: top",
                "objectClass: ds-cfg-http-authorization-mechanism",
                "objectClass: ds-cfg-http-oauth2-authorization-mechanism",
                "objectClass: ds-cfg-http-oauth2-openam-authorization-mechanism",
                "cn: HTTP OAuth2 OpenAM",
                "ds-cfg-java-class: org.opends.server.protocols.http.authz.HttpOAuth2OpenAmAuthorizationMechanism",
                "ds-cfg-enabled: false",
                "ds-cfg-openam-token-info-url: http://openam.example.com:8080/openam/oauth2/tokeninfo",
                "ds-cfg-oauth2-authzid-json-pointer: uid",
                "ds-cfg-identity-mapper: cn=Exact Match,cn=Identity Mappers,cn=config",
                "ds-cfg-oauth2-required-scope: read",
                "ds-cfg-oauth2-required-scope: write",
                "ds-cfg-oauth2-required-scope: uid",
                "ds-cfg-oauth2-access-token-cache-enabled: false",
                "ds-cfg-oauth2-access-token-cache-expiration: 300s"
        ),
        addConfigEntry(
                "dn: cn=HTTP OAuth2 Token Introspection (RFC7662),cn=HTTP Authorization Mechanisms,cn=config",
                "objectClass: top",
                "objectClass: ds-cfg-http-authorization-mechanism",
                "objectClass: ds-cfg-http-oauth2-authorization-mechanism",
                "objectClass: ds-cfg-http-oauth2-token-introspection-authorization-mechanism",
                "cn: HTTP OAuth2 Token Introspection (RFC7662)",
                "ds-cfg-java-class: "
                        + "org.opends.server.protocols.http.authz.HttpOAuth2TokenIntrospectionAuthorizationMechanism",
                "ds-cfg-enabled: false",
                "ds-cfg-oauth2-token-introspection-url: "
                        + "http://openam.example.com:8080/openam/oauth2/myrealm/introspect",
                "ds-cfg-oauth2-token-introspection-client-id: directoryserver",
                "ds-cfg-oauth2-token-introspection-client-secret: secret",
                "ds-cfg-oauth2-authzid-json-pointer: sub",
                "ds-cfg-identity-mapper: cn=Exact Match,cn=Identity Mappers,cn=config",
                "ds-cfg-oauth2-required-scope: read",
                "ds-cfg-oauth2-required-scope: write",
                "ds-cfg-oauth2-required-scope: uid",
                "ds-cfg-oauth2-access-token-cache-enabled: false",
                "ds-cfg-oauth2-access-token-cache-expiration: 300s"
        ),
        addConfigEntry(
                "dn: cn=HTTP OAuth2 File,cn=HTTP Authorization Mechanisms,cn=config",
                "objectClass: top",
                "objectClass: ds-cfg-http-authorization-mechanism",
                "objectClass: ds-cfg-http-oauth2-authorization-mechanism",
                "objectClass: ds-cfg-http-oauth2-file-authorization-mechanism",
                "cn: HTTP OAuth2 File",
                "ds-cfg-java-class: org.opends.server.protocols.http.authz.HttpOAuth2FileAuthorizationMechanism",
                "ds-cfg-enabled: false",
                "ds-cfg-oauth2-access-token-directory: oauth2-demo/",
                "ds-cfg-oauth2-authzid-json-pointer: uid",
                "ds-cfg-identity-mapper: cn=Exact Match,cn=Identity Mappers,cn=config",
                "ds-cfg-oauth2-required-scope: read",
                "ds-cfg-oauth2-required-scope: write",
                "ds-cfg-oauth2-required-scope: uid",
                "ds-cfg-oauth2-access-token-cache-enabled: false",
                "ds-cfg-oauth2-access-token-cache-expiration: 300s"
        ),
        /* Recursively copies.*/
        addConfigFile("rest2ldap")
    );

    /* See OPENDJ-3089 */
    register("4.0.0",
        addConfigEntry(INFO_UPGRADE_TASK_ADD_SCHEMA_PROVIDERS.get(),
            "dn: cn=Schema Providers,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-branch",
            "cn: Schema Providers"),
        addConfigEntry(
            "dn: cn=Core Schema,cn=Schema Providers,cn=config",
            "objectClass: top",
            "objectClass: ds-cfg-schema-provider",
            "objectClass: ds-cfg-core-schema",
            "cn: Core Schema",
            "ds-cfg-java-class: org.opends.server.schema.CoreSchemaProvider",
            "ds-cfg-enabled: true"),
        deleteConfigEntry(INFO_UPGRADE_TASK_REMOVE_MATCHING_RULES_ENTRY.get(),
            "cn=Matching Rules,cn=config"),
        deleteConfigEntry(INFO_UPGRADE_TASK_REMOVE_SYNTAXES.get(),
            "cn=Sun-defined Access Control Information,cn=Syntaxes,cn=config",
            "cn=Attribute Type Description,cn=Syntaxes,cn=config",
            "cn=Authentication Password,cn=Syntaxes,cn=config",
            "cn=Binary,cn=Syntaxes,cn=config",
            "cn=Bit String,cn=Syntaxes,cn=config",
            "cn=Boolean,cn=Syntaxes,cn=config",
            "cn=Certificate,cn=Syntaxes,cn=config",
            "cn=Certificate Exact Assertion,cn=Syntaxes,cn=config",
            "cn=Certificate List,cn=Syntaxes,cn=config",
            "cn=Certificate Pair,cn=Syntaxes,cn=config",
            "cn=Country String,cn=Syntaxes,cn=config",
            "cn=Delivery Method,cn=Syntaxes,cn=config",
            "cn=Directory String,cn=Syntaxes,cn=config",
            "cn=Distinguished Name,cn=Syntaxes,cn=config",
            "cn=DIT Content Rule Description,cn=Syntaxes,cn=config",
            "cn=DIT Structure Rule Description,cn=Syntaxes,cn=config",
            "cn=Enhanced Guide,cn=Syntaxes,cn=config",
            "cn=Facsimile Telephone Number,cn=Syntaxes,cn=config",
            "cn=Fax,cn=Syntaxes,cn=config",
            "cn=Generalized Time,cn=Syntaxes,cn=config",
            "cn=Guide,cn=Syntaxes,cn=config",
            "cn=IA5 String,cn=Syntaxes,cn=config",
            "cn=Integer,cn=Syntaxes,cn=config",
            "cn=JPEG,cn=Syntaxes,cn=config",
            "cn=LDAP Syntax Description,cn=Syntaxes,cn=config",
            "cn=Matching Rule Description,cn=Syntaxes,cn=config",
            "cn=Matching Rule Use Description,cn=Syntaxes,cn=config",
            "cn=Name and Optional UID,cn=Syntaxes,cn=config",
            "cn=Name Form Description,cn=Syntaxes,cn=config",
            "cn=Numeric String,cn=Syntaxes,cn=config",
            "cn=Object Class Description,cn=Syntaxes,cn=config",
            "cn=Object Identifier,cn=Syntaxes,cn=config",
            "cn=Octet String,cn=Syntaxes,cn=config",
            "cn=Other Mailbox,cn=Syntaxes,cn=config",
            "cn=Postal Address,cn=Syntaxes,cn=config",
            "cn=Presentation Address,cn=Syntaxes,cn=config",
            "cn=Printable String,cn=Syntaxes,cn=config",
            "cn=Protocol Information,cn=Syntaxes,cn=config",
            "cn=Substring Assertion,cn=Syntaxes,cn=config",
            "cn=Subtree Specification,cn=Syntaxes,cn=config",
            "cn=Supported Algorithm,cn=Syntaxes,cn=config",
            "cn=Telephone Number,cn=Syntaxes,cn=config",
            "cn=Teletex Terminal Identifier,cn=Syntaxes,cn=config",
            "cn=Telex Number,cn=Syntaxes,cn=config",
            "cn=UTC Time,cn=Syntaxes,cn=config",
            "cn=User Password,cn=Syntaxes,cn=config",
            "cn=UUID,cn=Syntaxes,cn=config",
            "cn=Syntaxes,cn=config")
    );
    register("4.0.0",
        modifyConfigEntry(INFO_UPGRADE_TASK_ADD_LOCAL_BACKEND.get(),
            "(objectClass=ds-cfg-backend)",
            "add: objectClass",
            "objectClass: ds-cfg-local-backend")
    );
    register("4.0.0", moveSubordinateBaseDnToGlobalConfiguration());
    register("4.0.0", removeTools("ldif-diff", "make-ldif", "dsjavaproperties"));

    /* All upgrades will refresh the server configuration schema and generate a new upgrade folder. */
    registerLast(
        performOEMMigrationIfNeeded(),
        copySchemaFile("02-config.ldif"),
        updateConfigUpgradeFolder(),
        postUpgradeRebuildIndexes());

    // @formatter:on
  }

  /** If the upgraded version is OEM, migrates local-db backends to PDB, see OPENDJ-3002 **/
  private static UpgradeTask performOEMMigrationIfNeeded() {
    return conditionalUpgradeTasks(
        isOemVersionAndNewerThan3dot0(),
        deleteFile(new File(libDirectory, "je.jar")),
        deleteFile(new File(libDirectory, "opendj-je-backend.jar")),
        conditionalUpgradeTasks(
            new UpgradeCondition() {
                @Override
                public boolean shouldPerformUpgradeTasks(final UpgradeContext context) throws ClientException {
                    return instanceContainsJeBackends();
                }
            },
            requireConfirmation(INFO_UPGRADE_TASK_LOCAL_DB_TO_PDB_1_SUMMARY.get("3.5.0"), NO,
                    renameLocalDBBackendDirectories(JE_BACKEND_OBJECT_CLASS),
                    convertJEBackendsToPDBBackends(JE_BACKEND_OBJECT_CLASS))
        )
    );
  }

  private static UpgradeCondition isOemVersionAndNewerThan3dot0() {
    return new UpgradeCondition() {
        @Override
        public boolean shouldPerformUpgradeTasks(UpgradeContext context) throws ClientException {
            return isOEMVersion()
                && context.getFromVersion().isNewerThan(BuildVersion.valueOf("3.0.0"));
        }

        @Override
        public String toString() {
            return "is OEM version and from version >= 3.0.0";
        }
    };
  }

  private static UpgradeTask convertJEBackendsToPDBBackends(final String objectClass) {
    return modifyConfigEntry(INFO_UPGRADE_TASK_LOCAL_DB_TO_PDB_2_SUMMARY.get(),
        "(objectclass=" + objectClass + ")",
        "delete: objectclass",
        "objectclass: " + objectClass,
        "-",
        "add: objectclass",
        "objectclass: ds-cfg-pluggable-backend",
        "objectclass: ds-cfg-pdb-backend",
        "-",
        "replace: ds-cfg-java-class",
        "ds-cfg-java-class: org.opends.server.backends.pdb.PDBBackend",
        "-",
        "delete: ds-cfg-preload-time-limit",
        "-",
        "delete: ds-cfg-import-thread-count",
        "-",
        "delete: ds-cfg-import-queue-size",
        "-",
        "delete: ds-cfg-db-txn-write-no-sync",
        "-",
        "delete: ds-cfg-db-run-cleaner",
        "-",
        "delete: ds-cfg-db-cleaner-min-utilization",
        "-",
        "delete: ds-cfg-db-evictor-lru-only",
        "-",
        "delete: ds-cfg-db-evictor-core-threads",
        "-",
        "delete: ds-cfg-db-evictor-max-threads",
        "-",
        "delete: ds-cfg-db-evictor-keep-alive",
        "-",
        "delete: ds-cfg-db-evictor-nodes-per-scan",
        "-",
        "delete: ds-cfg-db-log-file-max",
        "-",
        "delete: ds-cfg-db-log-filecache-size",
        "-",
        "delete: ds-cfg-db-logging-file-handler-on",
        "-",
        "delete: ds-cfg-db-logging-level",
        "-",
        "delete: ds-cfg-db-checkpointer-bytes-interval",
        "-",
        "delete: ds-cfg-db-checkpointer-wakeup-interval",
        "-",
        "delete: ds-cfg-db-num-lock-tables",
        "-",
        "delete: ds-cfg-db-num-cleaner-threads",
        "-",
        "delete: ds-cfg-je-property",
        "-",
        "delete: ds-cfg-subordinate-indexes-enabled",
        "-"
    );
  }

  /**
   * Returns a list containing all the tasks which are required in order to upgrade
   * from {@code fromVersion} to {@code toVersion}.
   *
   * @param fromVersion
   *          The old version.
   * @param toVersion
   *          The new version.
   * @return A list containing all the tasks which are required in order to upgrade
   *         from {@code fromVersion} to {@code toVersion}.
   */
  private static List<UpgradeTask> getUpgradeTasks(final BuildVersion fromVersion, final BuildVersion toVersion)
  {
    final List<UpgradeTask> tasks = new LinkedList<>();
    for (final List<UpgradeTask> subList : TASKS.subMap(fromVersion, false,
        toVersion, true).values())
    {
      tasks.addAll(subList);
    }
    tasks.addAll(MANDATORY_TASKS);
    return tasks;
  }

  /**
   * Upgrades the server from {@code fromVersion} to {@code toVersion} located in the upgrade context.
   *
   * @param context
   *          The context of the upgrade.
   * @throws ClientException
   *           If an error occurred while performing the upgrade.
   */
  public static void upgrade(final UpgradeContext context)
      throws ClientException
  {
    // Checks and validates the version number.
    isVersionCanBeUpdated(context);

    // Server must be offline.
    checkIfServerIsRunning(context);

    context.notify(INFO_UPGRADE_TITLE.get(), TITLE_CALLBACK);
    context.notify(INFO_UPGRADE_SUMMARY.get(context.getFromVersion(), context.getToVersion()), NOTICE_CALLBACK);
    context.notify(INFO_UPGRADE_GENERAL_SEE_FOR_DETAILS.get(UpgradeLog.getLogFilePath()), NOTICE_CALLBACK);

    // Checks License.
    checkLicence(context);

    logWarnAboutPatchesFolder();

    // Get the list of required upgrade tasks.
    final List<UpgradeTask> tasks =
        getUpgradeTasks(context.getFromVersion(), context.getToVersion());
    if (tasks.isEmpty())
    {
      changeBuildInfoVersion(context);
      return;
    }

    try
    {
      // Let tasks interact with the user in order to obtain user's selection.
      context.notify(INFO_UPGRADE_REQUIREMENTS.get(), TITLE_CALLBACK);
      for (final UpgradeTask task : tasks)
      {
        task.prepare(context);
      }

      // Starts upgrade
      final int userResponse = context.confirmYN(INFO_UPGRADE_DISPLAY_CONFIRM_START.get(), YES);
      if (userResponse == NO)
      {
        final LocalizableMessage message = INFO_UPGRADE_ABORTED_BY_USER.get();
        context.notify(message, WARNING);
        throw new ClientException(ReturnCode.ERROR_UNEXPECTED, message);
      }

      // Perform the upgrade tasks.
      context.notify(INFO_UPGRADE_PERFORMING_TASKS.get(), TITLE_CALLBACK);
      for (final UpgradeTask task : tasks)
      {
        try
        {
          task.perform(context);
        }
        catch (ClientException e)
        {
          handleClientException(context, e);
        }
      }

      if (UpgradeTasks.countErrors == 0)
      {
        /*
         * The end of a successful upgrade is marked up with the build info file update and the license,
         * if present, requires the creation of an approval file.
         */
        changeBuildInfoVersion(context);

        createFileLicenseApproved();
      }
      else
      {
        context.notify(ERR_UPGRADE_FAILS.get(UpgradeTasks.countErrors), TITLE_CALLBACK);
      }

      // Performs the post upgrade tasks.
      if (hasPostUpgradeTask && UpgradeTasks.countErrors == 0)
      {
        context.notify(INFO_UPGRADE_PERFORMING_POST_TASKS.get(), TITLE_CALLBACK);
        performPostUpgradeTasks(context, tasks);
        context.notify(INFO_UPGRADE_POST_TASKS_COMPLETE.get(), TITLE_CALLBACK);
      }
    }
    catch (final ClientException e)
    {
      context.notify(e.getMessageObject(), ERROR_CALLBACK);
      throw e;
    }
    catch (final Exception e)
    {
      final LocalizableMessage message = ERR_UPGRADE_TASKS_FAIL.get(stackTraceToSingleLineString(e));
      context.notify(message, ERROR_CALLBACK);
      throw new ClientException(ReturnCode.ERROR_UNEXPECTED, message, e);
    }
    finally
    {
      context.notify(INFO_UPGRADE_GENERAL_SEE_FOR_DETAILS.get(UpgradeLog.getLogFilePath()), NOTICE_CALLBACK);
      logger.info(INFO_UPGRADE_PROCESS_END);
    }
  }

  private static void performPostUpgradeTasks(final UpgradeContext context, final List<UpgradeTask> tasks)
      throws ClientException
  {
    boolean isOk = true;
    for (final UpgradeTask task : tasks)
    {
      if (isOk)
      {
        try
        {
          task.postUpgrade(context);
        }
        catch (ClientException e)
        {
          context.notify(e.getMessageObject(), WARNING);
          needToExitWithErrorCode();
          isOk = false;
        }
      }
      else
      {
        task.postponePostUpgrade(context);
      }
    }
  }

  private static void register(final String versionString,
      final UpgradeTask... tasks)
  {
    final BuildVersion version = BuildVersion.valueOf(versionString);
    List<UpgradeTask> taskList = TASKS.get(version);
    if (taskList == null)
    {
      taskList = new LinkedList<>();
      TASKS.put(version, taskList);
    }
    taskList.addAll(Arrays.asList(tasks));
  }

  private static void registerLast(final UpgradeTask... tasks)
  {
    MANDATORY_TASKS.addAll(Arrays.asList(tasks));
  }

  /**
   * The server must be offline during the upgrade.
   *
   * @throws ClientException
   *           An exception is thrown if the server is currently running.
   */
  private static void checkIfServerIsRunning(final UpgradeContext context) throws ClientException
  {
    final String lockFile = LockFileManager.getServerLockFileName();

    final StringBuilder failureReason = new StringBuilder();
    try
    {
      // Assume that if we cannot acquire the lock file the server is running.
      if (!LockFileManager.acquireExclusiveLock(lockFile, failureReason))
      {
        final LocalizableMessage message = ERR_UPGRADE_REQUIRES_SERVER_OFFLINE.get();
        context.notify(message, NOTICE_CALLBACK);
        throw new ClientException(ReturnCode.ERROR_UNEXPECTED, message);
      }
    }
    finally
    {
      LockFileManager.releaseLock(lockFile, failureReason);
    }
  }

  /**
   * Checks if the version can be updated.
   *
   * @param context
   *          The current context which running the upgrade.
   * @throws ClientException
   *           If an exception occurs - stops the process.
   */
  private static void isVersionCanBeUpdated(final UpgradeContext context)
      throws ClientException
  {
    if (context.getFromVersion().equals(context.getToVersion()))
    {
      // If the server is already up to date then treat it as a successful upgrade so that upgrade is idempotent.
      final LocalizableMessage message = ERR_UPGRADE_VERSION_UP_TO_DATE.get(context.getToVersion());
      context.notify(message, NOTICE_CALLBACK);
      throw new ClientException(ReturnCode.SUCCESS, message);
    }

    // Exclude upgrade from very old versions.
    if (context.getFromVersion().compareTo(UPGRADE_SUPPORTS_VERSION_FROM) < 0)
    {
      final LocalizableMessage message =
          INFO_UPGRADE_VERSION_IS_NOT_SUPPORTED.get(UPGRADE_SUPPORTS_VERSION_FROM, UPGRADE_SUPPORTS_VERSION_FROM);
      context.notify(message, NOTICE_CALLBACK);
      throw new ClientException(ReturnCode.ERROR_UNEXPECTED, message);
    }
  }

  /**
   * Writes the up to date's version number within the build info file.
   *
   * @param context
   *          The current context which running the upgrade.
   * @throws ClientException
   *           If an exception occurs when displaying the message.
   */
  private static void changeBuildInfoVersion(final UpgradeContext context)
      throws ClientException
  {
    File buildInfoFile = new File(UpgradeUtils.configDirectory, Installation.BUILDINFO_RELATIVE_PATH);
    try (FileWriter buildInfo = new FileWriter(buildInfoFile, false))
    {

      // Write the new version
      buildInfo.write(context.getToVersion().toString());

      context.notify(INFO_UPGRADE_SUCCESSFUL.get(context.getFromVersion(), context.getToVersion()), TITLE_CALLBACK);
    }
    catch (IOException e)
    {
      final LocalizableMessage message = LocalizableMessage.raw(e.getMessage());
      context.notify(message, ERROR_CALLBACK);
      throw new ClientException(ReturnCode.ERROR_UNEXPECTED, message);
    }
  }

  private static void checkLicence(final UpgradeContext context)
      throws ClientException
  {
    // Check license
    if (LicenseFile.exists() && !LicenseFile.isAlreadyApproved())
    {
      context.notify(LocalizableMessage.raw(LINE_SEPARATOR + LicenseFile.getText()));
      context.notify(INFO_LICENSE_DETAILS_CLI_LABEL.get());
      if (!context.isAcceptLicenseMode())
      {
        final int answer;

        // The force cannot answer yes to the license's question, which is not a task even if it requires a user
        // interaction OR -an accept license mode to continue the process.
        if (context.isForceUpgradeMode())
        {
          answer = NO;
          context.notify(
              LocalizableMessage.raw(INFO_LICENSE_ACCEPT.get() + " " + INFO_PROMPT_NO_COMPLETE_ANSWER.get()));
        }
        else
        {
          answer = context.confirmYN(INFO_LICENSE_ACCEPT.get(), NO);
        }

        if (answer == NO)
        {
          System.exit(EXIT_CODE_SUCCESS);
        }
        else if (answer == YES)
        {
          LicenseFile.setApproval(true);
        }
      }
      else
      {
        // We automatically accept the license with this option.
        context.notify(
            LocalizableMessage.raw(INFO_LICENSE_ACCEPT.get() + " " + INFO_PROMPT_YES_COMPLETE_ANSWER.get()));
        LicenseFile.setApproval(true);
      }
    }
  }

  /**
   * The classes folder is renamed by the script launcher to avoid
   * incompatibility between patches and upgrade process. If a folder
   * "classes.disabled" is found, this function just displays a warning in the
   * log file, meaning the "classes" folder has been renamed. See upgrade.sh /
   * upgrade.bat scripts which hold the renaming process. (OPENDJ-1098)
   */
  private static void logWarnAboutPatchesFolder()
  {
    try
    {
      final File backup = new File(UpgradeUtils.getInstancePath(), "classes.disabled");
      if (backup.exists()) {
        final File[] files = backup.listFiles();
        if (files != null && files.length > 0)
        {
          logger.warn(INFO_UPGRADE_CLASSES_FOLDER_RENAMED, backup.getAbsoluteFile());
        }
      }
    }
    catch (SecurityException e)
    {
      logger.debug(LocalizableMessage.raw(e.getMessage()), e);
    }
  }

  static void needToRunPostUpgradePhase()
  {
    Upgrade.hasPostUpgradeTask = true;
  }

  /** This method should be used when the upgrade tool has issued a warning. */
  static void needToExitWithErrorCode()
  {
    Upgrade.exitWithErrorCode = true;
  }

  /**
   * {@code true} if the upgrade succeeded.
   *
   * @return {@code true} if the upgrade succeeded.
   */
  static boolean isSuccess()
  {
    return !exitWithErrorCode;
  }

  /** Prevent instantiation. */
  private Upgrade()
  {
    // Nothing to do.
  }
}
