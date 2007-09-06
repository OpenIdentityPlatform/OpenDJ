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
package org.opends.server.core;



import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.server.AdminTestCaseUtils;
import org.opends.server.admin.std.meta.PasswordPolicyCfgDefn;
import org.opends.server.admin.std.server.PasswordPolicyCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.tools.LDAPModify;
import org.opends.server.types.Attribute;
import org.opends.server.types.AttributeType;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ResultCode;
import org.opends.server.util.TimeThread;

import static org.testng.Assert.*;



/**
 * A set of generic test cases for the Directory Server password policy class.
 */
public class PasswordPolicyTestCase
       extends CoreTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.restartServer();
  }



  /**
   * Retrieves a set of invalid configurations that cannot be used to
   * initialize a password policy.
   *
   * @return  A set of invalid configurations that cannot be used to
   *          initialize a password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigurations()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=Default Password Policy 1,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 2,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: invalid",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 3,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: cn",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 4,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-last-login-time-attribute: invalid",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 5,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: invalid",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 6,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: invalid",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 7,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: invalid",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 8,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: invalid",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 9,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: invalid",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 10,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: invalid",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 11,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: invalid",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 12,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: invalid",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 13,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: invalid",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 14,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: invalid",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 15,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: invalid",
         "",
         "dn: cn=Default Password Policy 16,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: -1",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 17,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: notnumeric",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 18,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: -1 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 19,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: notnumeric seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 20,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 21,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 invalid",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 22,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: invalid",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 23,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: -1 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 24,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: notnumeric seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 25,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: -1 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 26,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 invalid",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 27,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: invalid",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 28,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: -1",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 29,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: notnumeric",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 30,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: -1 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 31,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: notnumeric seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 32,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 33,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 invalid",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 34,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: invalid",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 35,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: -1 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 36,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: invalid seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 37,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 38,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 invalid",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 39,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: invalid",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 40,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: -1 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 41,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: invalid seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 42,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 43,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 invalid",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 44,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: invalid",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 45,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: -1 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 46,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: invalid seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 47,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 48,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 invalid",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 49,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: invalid",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 50,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 0 seconds",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 51,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: -1 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 52,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: invalid days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 53,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 54,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 invalid",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 55,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: invalid",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 56,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "ds-cfg-require-change-by-time: invalid",
         "",
         "dn: cn=Default Password Policy 57,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "ds-cfg-last-login-time-format: invalid",
         "",
         "dn: cn=Default Password Policy 58,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: invalid",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 59,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=nonexistent," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 60,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "ds-cfg-account-status-notification-handler-dn: invalid",
         "",
         "dn: cn=Default Password Policy 61,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "ds-cfg-account-status-notification-handler-dn: cn=nonexistent," +
              "cn=Account Status Notification Handlers,cn=config",
         "",
         "dn: cn=Default Password Policy 62,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 63,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Undefined,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 64,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: invalid",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "",
         "dn: cn=Default Password Policy 65,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "ds-cfg-password-validator-dn: invalid",
         "",
         "dn: cn=Default Password Policy 66,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "ds-cfg-password-validator-dn: cn=nonexistent," +
              "cn=Password Validators,cn=config",
         "",
         "dn: cn=Default Password Policy 67,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: 0 seconds",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "ds-cfg-previous-last-login-time-format: invalid",
         "",
      // This is a catch-all invalid case to get coverage for attributes not
      // normally included in the default scheme.  It is based on the internal
      // knowledge that the idle lockout interval is the last attribute checked
      // during validation.
         "dn: cn=Default Password Policy 68,cn=Password Policies,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-password-policy",
         "cn: Default Password Policy",
         "ds-cfg-password-attribute: userPassword",
         "ds-cfg-default-password-storage-scheme-dn: " +
              "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config",
         "ds-cfg-deprecated-password-storage-scheme: " +
              "cn=BASE64,cn=Password Storage Schemes,cn=config",
         "ds-cfg-allow-expired-password-changes: false",
         "ds-cfg-allow-multiple-password-values: false",
         "ds-cfg-allow-pre-encoded-passwords: false",
         "ds-cfg-allow-user-password-changes: true",
         "ds-cfg-expire-passwords-without-warning: false",
         "ds-cfg-force-change-on-add: false",
         "ds-cfg-force-change-on-reset: false",
         "ds-cfg-grace-login-count: 0",
         "ds-cfg-idle-lockout-interval: invalid",
         "ds-cfg-lockout-failure-count: 0",
         "ds-cfg-lockout-duration: 0 seconds",
         "ds-cfg-lockout-failure-expiration-interval: 0 seconds",
         "ds-cfg-minimum-password-age: 0 seconds",
         "ds-cfg-maximum-password-age: 0 seconds",
         "ds-cfg-maximum-password-reset-age: 0 seconds",
         "ds-cfg-password-expiration-warning-interval: 5 days",
         "ds-cfg-password-generator-dn: cn=Random Password Generator," +
              "cn=Password Generators,cn=config",
         "ds-cfg-password-change-requires-current-password: false",
         "ds-cfg-require-secure-authentication: false",
         "ds-cfg-require-secure-password-changes: false",
         "ds-cfg-skip-validation-for-administrators: false",
         "ds-cfg-require-change-by-time: 20060101000000Z",
         "ds-cfg-last-login-time-attribute: ds-pwp-last-login-time",
         "ds-cfg-last-login-time-format: yyyyMMdd",
         "ds-cfg-previous-last-login-time-format: yyyyMMddHHmmss",
         "ds-cfg-account-status-notification-handler-dn: " +
              "cn=Error Log Handler,cn=Account Status Notification Handlers," +
              "cn=config");


    Object[][] configEntries = new Object[entries.size()][1];
    for (int i=0; i < configEntries.length; i++)
    {
      configEntries[i] = new Object[] { entries.get(i) };
    }

    return configEntries;
  }



  /**
   * Ensures that password policy creation will fail when given an invalid
   * configuration.
   *
   * @param  e  The entry containing an invalid password policy configuration.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidConfigs",
        expectedExceptions = { ConfigException.class,
                               InitializationException.class })
  public void testInvalidConstructor(Entry e)
         throws Exception
  {
    DN parentDN = DN.decode("cn=Password Policies,cn=config");
    ConfigEntry parentEntry = DirectoryServer.getConfigEntry(parentDN);
    ConfigEntry configEntry = new ConfigEntry(e, parentEntry);

    PasswordPolicyCfg configuration =
      AdminTestCaseUtils.getConfiguration(PasswordPolicyCfgDefn.getInstance(),
          configEntry.getEntry());

    new PasswordPolicy(configuration);
  }



  /**
   * Tests the <CODE>getPasswordAttribute</CODE> method for the default password
   * policy.
   */
  @Test()
  public void testGetPasswordAttributeDefault()
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    AttributeType  t = p.getPasswordAttribute();
    assertEquals(t, DirectoryServer.getAttributeType("userpassword"));
  }



  /**
   * Tests the <CODE>getPasswordAttribute</CODE> method for a password policy
   * using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetPasswordAttributeAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    AttributeType  t = p.getPasswordAttribute();
    assertEquals(t, DirectoryServer.getAttributeType("authpassword"));
  }



  /**
   * Tests the <CODE>usesAuthPasswordSyntax</CODE> method for the default
   * password policy.
   */
  @Test()
  public void testUsesAuthPasswordSyntaxDefault()
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertFalse(p.usesAuthPasswordSyntax());
  }



  /**
   * Tests the <CODE>usesAuthPasswordSyntax</CODE> method for a password policy
   * using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testUsesAuthPasswordSyntaxAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertTrue(p.usesAuthPasswordSyntax());
  }



  /**
   * Tests the <CODE>getDefaultStorageSchemes</CODE> method for the default
   * password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetDefaultStorageSchemesDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    CopyOnWriteArrayList<PasswordStorageScheme> defaultSchemes =
         p.getDefaultStorageSchemes();
    assertNotNull(defaultSchemes);
    assertFalse(defaultSchemes.isEmpty());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "default-password-storage-scheme-dn:" +
                    "cn=Base64,cn=Password Storage Schemes,cn=config");

    p = DirectoryServer.getDefaultPasswordPolicy();
    defaultSchemes = p.getDefaultStorageSchemes();
    assertNotNull(defaultSchemes);
    assertFalse(defaultSchemes.isEmpty());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "default-password-storage-scheme-dn:" +
                    "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config");
  }



  /**
   * Tests the <CODE>getDefaultStorageSchemes</CODE> method for a password
   * policy using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetDefaultStorageSchemesAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    CopyOnWriteArrayList<PasswordStorageScheme> defaultSchemes =
         p.getDefaultStorageSchemes();
    assertNotNull(defaultSchemes);
    assertFalse(defaultSchemes.isEmpty());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "default-password-storage-scheme-dn:" +
                    "cn=Salted MD5,cn=Password Storage Schemes,cn=config");

    p = DirectoryServer.getPasswordPolicy(dn);
    defaultSchemes = p.getDefaultStorageSchemes();
    assertNotNull(defaultSchemes);
    assertFalse(defaultSchemes.isEmpty());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "default-password-storage-scheme-dn:" +
                    "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config");
  }



  /**
   * Tests the <CODE>isDefaultStorageScheme</CODE> method for the default
   * password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIsDefaultStorageSchemeDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertTrue(p.isDefaultStorageScheme("SSHA"));
    assertFalse(p.isDefaultStorageScheme("CLEAR"));

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "default-password-storage-scheme-dn:" +
                    "cn=BASE64,cn=Password Storage Schemes,cn=config");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertTrue(p.isDefaultStorageScheme("BASE64"));
    assertFalse(p.isDefaultStorageScheme("SSHA"));
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "default-password-storage-scheme-dn:" +
                    "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config");
  }



  /**
   * Tests the <CODE>isDefaultStorageScheme</CODE> method for a password policy
   * using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIsDefaultStorageSchemeAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertTrue(p.isDefaultStorageScheme("SHA1"));
    assertFalse(p.isDefaultStorageScheme("MD5"));

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "default-password-storage-scheme-dn:" +
                    "cn=Salted MD5,cn=Password Storage Schemes,cn=config");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertTrue(p.isDefaultStorageScheme("MD5"));
    assertFalse(p.isDefaultStorageScheme("SHA1"));
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "default-password-storage-scheme-dn:" +
                    "cn=Salted SHA-1,cn=Password Storage Schemes,cn=config");
  }



  /**
   * Tests the <CODE>getDeprecatedStorageSchemes</CODE> method for the default
   * password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetDeprecatedStorageSchemesDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    CopyOnWriteArraySet<String> deprecatedSchemes =
         p.getDeprecatedStorageSchemes();
    assertNotNull(deprecatedSchemes);
    assertTrue(deprecatedSchemes.isEmpty());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "deprecated-password-storage-scheme-dn:" +
                    "cn=BASE64,cn=Password Storage Schemes,cn=config");

    p = DirectoryServer.getDefaultPasswordPolicy();
    deprecatedSchemes = p.getDeprecatedStorageSchemes();
    assertNotNull(deprecatedSchemes);
    assertFalse(deprecatedSchemes.isEmpty());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--remove", "deprecated-password-storage-scheme-dn:" +
                       "cn=BASE64,cn=Password Storage Schemes,cn=config");
  }



  /**
   * Tests the <CODE>getDeprecatedStorageSchemes</CODE> method for a password
   * policy using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetDeprecatedStorageSchemesAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    CopyOnWriteArraySet<String> deprecatedSchemes =
         p.getDeprecatedStorageSchemes();
    assertNotNull(deprecatedSchemes);
    assertTrue(deprecatedSchemes.isEmpty());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "deprecated-password-storage-scheme-dn:" +
                    "cn=Salted MD5,cn=Password Storage Schemes,cn=config");

    p = DirectoryServer.getPasswordPolicy(dn);
    deprecatedSchemes = p.getDeprecatedStorageSchemes();
    assertNotNull(deprecatedSchemes);
    assertFalse(deprecatedSchemes.isEmpty());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--remove", "deprecated-password-storage-scheme-dn:" +
                       "cn=Salted MD5,cn=Password Storage Schemes,cn=config");
  }



  /**
   * Tests the <CODE>isDeprecatedStorageScheme</CODE> method for the default
   * password storage scheme.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIsDeprecatedStorageSchemeDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertFalse(p.isDeprecatedStorageScheme("BASE64"));

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "deprecated-password-storage-scheme-dn:" +
                    "cn=BASE64,cn=Password Storage Schemes,cn=config");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertTrue(p.isDeprecatedStorageScheme("BASE64"));
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--remove", "deprecated-password-storage-scheme-dn:" +
                       "cn=BASE64,cn=Password Storage Schemes,cn=config");
  }



  /**
   * Tests the <CODE>isDeprecatedStorageScheme</CODE> method for a password
   * policy using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testIsDeprecatedStorageSchemeAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertFalse(p.isDeprecatedStorageScheme("MD5"));

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "deprecated-password-storage-scheme-dn:" +
                    "cn=Salted MD5,cn=Password Storage Schemes,cn=config");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertTrue(p.isDeprecatedStorageScheme("MD5"));
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--remove", "deprecated-password-storage-scheme-dn:" +
                       "cn=Salted MD5,cn=Password Storage Schemes,cn=config");
  }



  /**
   * Tests the <CODE>getPasswordValidators</CODE> method for the default
   * password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetPasswordValidatorsDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertNotNull(p.getPasswordValidators());
    assertFalse(p.getPasswordValidators().isEmpty());

    String valDN = "cn=Length-Based Password Validator," +
                   "cn=Password Validators,cn=config";
    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--add", "password-validator-dn:"+valDN);

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertNotNull(p.getPasswordValidators());
    assertFalse(p.getPasswordValidators().isEmpty());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--remove", "password-validator-dn:"+valDN);
  }



  /**
   * Tests the <CODE>getPasswordValidators</CODE> method for a password policy
   * using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetPasswordValidatorsAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertNotNull(p.getPasswordValidators());
    assertFalse(p.getPasswordValidators().isEmpty());

    String valDN = "cn=Length-Based Password Validator," +
                   "cn=Password Validators,cn=config";
    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--add", "password-validator-dn:"+valDN);

    p = DirectoryServer.getPasswordPolicy(dn);
    assertNotNull(p.getPasswordValidators());
    assertFalse(p.getPasswordValidators().isEmpty());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--remove", "password-validator-dn:"+valDN);
  }



  /**
   * Tests the <CODE>getAccountStatusNotificationHandlers</CODE> method for the
   * default password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetAccountStatusNotificationHandlersDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertNotNull(p.getAccountStatusNotificationHandlers());
    assertTrue(p.getAccountStatusNotificationHandlers().isEmpty());

    String notDN = "cn=Error Log Handler," +
                   "cn=Account Status Notification Handlers,cn=config";
    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--add", "account-status-notification-handler-dn:"+notDN);

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertNotNull(p.getAccountStatusNotificationHandlers());
    assertFalse(p.getAccountStatusNotificationHandlers().isEmpty());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--remove", "account-status-notification-handler-dn:"+notDN);
  }



  /**
   * Tests the <CODE>getAccountStatusNotificationHandlers</CODE> method for a
   * password policy using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetAccountStatusNotificationHandlersAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertNotNull(p.getAccountStatusNotificationHandlers());
    assertTrue(p.getAccountStatusNotificationHandlers().isEmpty());

    String notDN = "cn=Error Log Handler," +
                   "cn=Account Status Notification Handlers,cn=config";
    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--add", "account-status-notification-handler-dn:"+notDN);

    p = DirectoryServer.getPasswordPolicy(dn);
    assertNotNull(p.getAccountStatusNotificationHandlers());
    assertFalse(p.getAccountStatusNotificationHandlers().isEmpty());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--remove", "account-status-notification-handler-dn:"+notDN);
  }



  /**
   * Tests the <CODE>allowUserPasswordChanges</CODE> method for the default
   * password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAllowUserPasswordChangesDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertTrue(p.allowUserPasswordChanges());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "allow-user-password-changes:false");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertFalse(p.allowUserPasswordChanges());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "allow-user-password-changes:true");
  }



  /**
   * Tests the <CODE>allowUserPasswordChanges</CODE> method for a password
   * policy using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAllowUserPasswordChangesAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertTrue(p.allowUserPasswordChanges());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "allow-user-password-changes:false");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertFalse(p.allowUserPasswordChanges());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "allow-user-password-changes:true");
  }



  /**
   * Tests the <CODE>requireCurrentPassword</CODE> method for the default
   * password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequireCurrentPasswordDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertFalse(p.requireCurrentPassword());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "password-change-requires-current-password:true");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertTrue(p.requireCurrentPassword());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "password-change-requires-current-password:false");
  }



  /**
   * Tests the <CODE>requireCurrentPassword</CODE> method for a password policy
   * using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequireCurrentPasswordAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertFalse(p.requireCurrentPassword());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "password-change-requires-current-password:true");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertTrue(p.allowUserPasswordChanges());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "password-change-requires-current-password:false");
  }



  /**
   * Tests the <CODE>forceChangeOnAdd</CODE> method for the default password
   * policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testForceChangeOnAddDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertFalse(p.forceChangeOnAdd());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "force-change-on-add:true");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertTrue(p.forceChangeOnAdd());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "force-change-on-add:false");
  }



  /**
   * Tests the <CODE>forceChangeOnAdd</CODE> method for a password policy using
   * the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testForceChangeOnAddAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertFalse(p.requireCurrentPassword());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "force-change-on-add:true");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertTrue(p.forceChangeOnAdd());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "force-change-on-add:false");
  }



  /**
   * Tests the <CODE>forceChangeOnReset</CODE> method for the default password
   * policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testForceChangeOnResetDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertFalse(p.forceChangeOnReset());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "force-change-on-reset:true");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertTrue(p.forceChangeOnReset());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "force-change-on-reset:false");
  }



  /**
   * Tests the <CODE>forceChangeOnReset</CODE> method for a password policy
   * using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testForceChangeOnResetAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertFalse(p.requireCurrentPassword());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "force-change-on-reset:true");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertTrue(p.forceChangeOnReset());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "force-change-on-reset:false");
  }



  /**
   * Tests the <CODE>skipValidationForAdministrators</CODE> method for the
   * default password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSkipValidationForAdministratorsDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertFalse(p.skipValidationForAdministrators());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "skip-validation-for-administrators:true");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertTrue(p.skipValidationForAdministrators());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "skip-validation-for-administrators:false");
  }



  /**
   * Tests the <CODE>skipValidationForAdministrators</CODE> method for a
   * password policy using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testSkipValidationForAdministratorsAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertFalse(p.skipValidationForAdministrators());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "skip-validation-for-administrators:true");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertTrue(p.skipValidationForAdministrators());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "skip-validation-for-administrators:false");
  }



  /**
   * Tests the <CODE>getPasswordGeneratorDN</CODE> method for the default
   * password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetPasswordGeneratorDNDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertNotNull(p.getPasswordGeneratorDN());

    String genDN = "cn=Random Password Generator,cn=Password Generators," +
                   "cn=config";
    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--remove", "password-generator-dn:"+genDN);

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertNull(p.getPasswordGeneratorDN());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "password-generator-dn:"+genDN);
  }



  /**
   * Tests the <CODE>getPasswordGeneratorDN</CODE> method for a password policy
   * using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetPasswordGeneratorDNAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertNotNull(p.getPasswordGeneratorDN());

    String genDN = "cn=Random Password Generator,cn=Password Generators," +
                   "cn=config";
    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--remove", "password-generator-dn:"+genDN);

    p = DirectoryServer.getPasswordPolicy(dn);
    assertNull(p.getPasswordGeneratorDN());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "password-generator-dn:"+genDN);
  }



  /**
   * Tests the <CODE>getPasswordGenerator</CODE> method for the default password
   * policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetPasswordGeneratorDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertNotNull(p.getPasswordGenerator());

    String genDN = "cn=Random Password Generator,cn=Password Generators," +
                   "cn=config";
    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--remove", "password-generator-dn:"+genDN);

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertNull(p.getPasswordGenerator());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "password-generator-dn:"+genDN);
  }



  /**
   * Tests the <CODE>getPasswordGenerator</CODE> method for a password policy
   * using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetPasswordGeneratorAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertNotNull(p.getPasswordGenerator());

    String genDN = "cn=Random Password Generator,cn=Password Generators," +
                   "cn=config";
    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--remove", "password-generator-dn:"+genDN);

    p = DirectoryServer.getPasswordPolicy(dn);
    assertNull(p.getPasswordGenerator());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "password-generator-dn:"+genDN);
  }



  /**
   * Tests the <CODE>requireSecureAuthentication</CODE> method for the default
   * password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequireSecureAuthenticationDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertFalse(p.requireSecureAuthentication());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "require-secure-authentication:true");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertTrue(p.requireSecureAuthentication());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "require-secure-authentication:false");
  }



  /**
   * Tests the <CODE>requireSecureAuthentication</CODE> method for a password
   * policy using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequireSecureAuthenticationAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertFalse(p.requireSecureAuthentication());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "require-secure-authentication:true");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertTrue(p.requireSecureAuthentication());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "require-secure-authentication:false");
  }



  /**
   * Tests the <CODE>requireSecurePasswordChanges</CODE> method for the default
   * password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequireSecurePasswordChangesDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertFalse(p.requireSecurePasswordChanges());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "require-secure-password-changes:true");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertTrue(p.requireSecurePasswordChanges());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "require-secure-password-changes:false");
  }



  /**
   * Tests the <CODE>requireSecurePasswordChanges</CODE> method for a password
   * policy using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testRequireSecurePasswordChangesAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertFalse(p.requireSecurePasswordChanges());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "require-secure-password-changes:true");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertTrue(p.requireSecurePasswordChanges());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "require-secure-password-changes:false");
  }



  /**
   * Tests the <CODE>allowMultiplePasswordValues</CODE> method for the default
   * password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAllowMultiplePasswordValuesDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertFalse(p.allowMultiplePasswordValues());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "allow-multiple-password-values:true");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertTrue(p.allowMultiplePasswordValues());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "allow-multiple-password-values:false");
  }



  /**
   * Tests the <CODE>allowMultiplePasswordValues</CODE> method for a password
   * policy using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAllowMultiplePasswordValuesAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertFalse(p.allowMultiplePasswordValues());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "allow-multiple-password-values:true");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertTrue(p.allowMultiplePasswordValues());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "allow-multiple-password-values:false");
  }



  /**
   * Tests the <CODE>allowPreEncodedPasswords</CODE> method for the default
   * password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAllowPreEncodedPasswordsDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertFalse(p.allowPreEncodedPasswords());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "allow-pre-encoded-passwords:true");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertTrue(p.allowPreEncodedPasswords());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "allow-pre-encoded-passwords:false");
  }



  /**
   * Tests the <CODE>allowPreEncodedPasswords</CODE> method for a password
   * policy using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAllowPreEncodedPasswordsAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertFalse(p.allowPreEncodedPasswords());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "allow-pre-encoded-passwords:true");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertTrue(p.allowPreEncodedPasswords());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "allow-pre-encoded-passwords:false");
  }



  /**
   * Tests the <CODE>getMinimumPasswordAge</CODE> method for the default
   * password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetMinimumPasswordAgeDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertEquals(p.getMinimumPasswordAge(), 0);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "minimum-password-age:24 hours");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertEquals(p.getMinimumPasswordAge(), (24*60*60));
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "minimum-password-age:0 seconds");
  }



  /**
   * Tests the <CODE>getMinimumPasswordAge</CODE> method for a password policy
   * using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetMinimumPasswordAgeAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertEquals(p.getMinimumPasswordAge(), 0);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "minimum-password-age:24 hours");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertEquals(p.getMinimumPasswordAge(), (24*60*60));
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "minimum-password-age:0 seconds");
  }



  /**
   * Tests the <CODE>getMaximumPasswordAge</CODE> method for the default
   * password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetMaximumPasswordAgeDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertEquals(p.getMaximumPasswordAge(), 0);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "maximum-password-age:90 days");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertEquals(p.getMaximumPasswordAge(), (90*60*60*24));
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "maximum-password-age:0 seconds");
  }



  /**
   * Tests the <CODE>getMaximumPasswordAge</CODE> method for a password policy
   * using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetMaximumPasswordAgeAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertEquals(p.getMaximumPasswordAge(), 0);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "maximum-password-age:90 days");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertEquals(p.getMaximumPasswordAge(), (90*60*60*24));
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "maximum-password-age:0 seconds");
  }



  /**
   * Tests the <CODE>getMaximumPasswordResetAge</CODE> method for the default
   * password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetMaximumPasswordResetAgeDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertEquals(p.getMaximumPasswordResetAge(), 0);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "maximum-password-reset-age:24 hours");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertEquals(p.getMaximumPasswordResetAge(), (24*60*60));
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "maximum-password-reset-age:0 seconds");
  }



  /**
   * Tests the <CODE>getMaximumPasswordResetAge</CODE> method for a password
   * policy using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetMaximumPasswordResetAgeAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertEquals(p.getMaximumPasswordResetAge(), 0);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "maximum-password-reset-age:24 hours");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertEquals(p.getMaximumPasswordResetAge(), (24*60*60));
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "maximum-password-reset-age:0 seconds");
  }



  /**
   * Tests the <CODE>getWarningInterval</CODE> method for the default password
   * policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetWarningIntervalDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertEquals(p.getWarningInterval(), (5*60*60*24));

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "password-expiration-warning-interval:24 hours");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertEquals(p.getWarningInterval(), (24*60*60));
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "password-expiration-warning-interval:5 days");
  }



  /**
   * Tests the <CODE>getWarningInterval</CODE> method for a password
   * policy using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetWarningIntervalAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertEquals(p.getWarningInterval(), (5*60*60*24));

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "password-expiration-warning-interval:24 hours");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertEquals(p.getWarningInterval(), (24*60*60));
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "password-expiration-warning-interval:5 days");
  }



  /**
   * Tests the <CODE>expirePasswordsWithoutWarning</CODE> method for the default
   * password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testExpirePasswordsWithoutWarningDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertFalse(p.expirePasswordsWithoutWarning());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "expire-passwords-without-warning:true");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertTrue(p.expirePasswordsWithoutWarning());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "expire-passwords-without-warning:false");
  }



  /**
   * Tests the <CODE>expirePasswordsWithoutWarning</CODE> method for a password
   * policy using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testExpirePasswordsWithoutWarningAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertFalse(p.expirePasswordsWithoutWarning());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "expire-passwords-without-warning:true");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertTrue(p.expirePasswordsWithoutWarning());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "expire-passwords-without-warning:false");
  }



  /**
   * Tests the <CODE>allowExpiredPasswordChanges</CODE> method for the default
   * password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAllowExpiredPasswordChangesDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertFalse(p.allowExpiredPasswordChanges());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "allow-expired-password-changes:true");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertTrue(p.allowExpiredPasswordChanges());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "allow-expired-password-changes:false");
  }



  /**
   * Tests the <CODE>allowExpiredPasswordChanges</CODE> method for a password
   * policy using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAllowExpiredPasswordChangesAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertFalse(p.allowExpiredPasswordChanges());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "allow-expired-password-changes:true");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertTrue(p.allowExpiredPasswordChanges());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "allow-expired-password-changes:false");
  }



  /**
   * Tests the <CODE>getGraceLoginCount</CODE> method for the default password
   * policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetGraceLoginCountDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertEquals(p.getGraceLoginCount(), 0);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "grace-login-count:3");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertEquals(p.getGraceLoginCount(), 3);
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "grace-login-count:0");
  }



  /**
   * Tests the <CODE>getGraceLoginCount</CODE> method for a password policy
   * using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetGraceLoginCountAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertEquals(p.getGraceLoginCount(), 0);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "grace-login-count:3");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertEquals(p.getGraceLoginCount(), 3);
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "grace-login-count:0");
  }



  /**
   * Tests the <CODE>getLockoutFailureCount</CODE> method for the default
   * password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetLockoutFailureCountDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertEquals(p.getLockoutFailureCount(), 0);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "lockout-failure-count:3");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertEquals(p.getLockoutFailureCount(), 3);
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "lockout-failure-count:0");
  }



  /**
   * Tests the <CODE>getLockoutFailureCount</CODE> method for a password policy
   * using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetLockoutFailureCountAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertEquals(p.getLockoutFailureCount(), 0);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "lockout-failure-count:3");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertEquals(p.getLockoutFailureCount(), 3);
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "lockout-failure-count:0");
  }



  /**
   * Tests the <CODE>getLockoutDuration</CODE> method for the default password
   * policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetLockoutDurationDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertEquals(p.getLockoutDuration(), 0);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "lockout-duration:15 minutes");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertEquals(p.getLockoutDuration(), (15*60));
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "lockout-duration:0 seconds");
  }



  /**
   * Tests the <CODE>getLockoutDuration</CODE> method for a password policy
   * using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetLockoutDurationAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertEquals(p.getLockoutDuration(), 0);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "lockout-duration:15 minutes");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertEquals(p.getLockoutDuration(), (15*60));
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "lockout-duration:0 seconds");
  }



  /**
   * Tests the <CODE>getLockoutFailureExpirationInterval</CODE> method for the
   * default password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetLockoutFailureExpirationIntervalDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertEquals(p.getLockoutFailureExpirationInterval(), 0);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "lockout-failure-expiration-interval:10 minutes");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertEquals(p.getLockoutFailureExpirationInterval(), (10*60));
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "lockout-failure-expiration-interval:0 seconds");
  }



  /**
   * Tests the <CODE>getLockoutFailureExpirationInterval</CODE> method for a
   * password policy using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetLockoutFailureExpirationIntervalAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertEquals(p.getLockoutFailureExpirationInterval(), 0);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "lockout-failure-expiration-interval:10 minutes");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertEquals(p.getLockoutFailureExpirationInterval(), (10*60));
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "lockout-failure-expiration-interval:0 seconds");
  }



  /**
   * Tests the <CODE>getRequireChangeByTime</CODE> method for the default
   * password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetRequireChangeByTimeDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertEquals(p.getRequireChangeByTime(), 0);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "require-change-by-time:19700101000001Z");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertEquals(p.getRequireChangeByTime(), 1000);
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--remove", "require-change-by-time:19700101000001Z");
  }



  /**
   * Tests the <CODE>getRequireChangeByTime</CODE> method for a password policy
   * using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetRequireChangeByTimeAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertEquals(p.getRequireChangeByTime(), 0);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "require-change-by-time:19700101000001Z");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertEquals(p.getRequireChangeByTime(), 1000);
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--remove", "require-change-by-time:19700101000001Z");
  }



  /**
   * Tests the <CODE>getLastLoginTimeAttribute</CODE> method for the default
   * password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetLastLoginTimeAttributeDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertNull(p.getLastLoginTimeAttribute());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "last-login-time-attribute:ds-pwp-last-login-time");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertNotNull(p.getLastLoginTimeAttribute());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--remove", "last-login-time-attribute:ds-pwp-last-login-time");
  }



  /**
   * Tests the <CODE>getLastLoginTimeAttribute</CODE> method for a password
   * policy using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetLastLoginTimeAttributeAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertNull(p.getLastLoginTimeAttribute());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "last-login-time-attribute:ds-pwp-last-login-time");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertNotNull(p.getLastLoginTimeAttribute());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--remove", "last-login-time-attribute:ds-pwp-last-login-time");
  }



  /**
   * Tests the <CODE>getLastLoginTimeFormat</CODE> method for the default
   * password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetLastLoginTimeAttributeFormatDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertNull(p.getLastLoginTimeFormat());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "last-login-time-format:yyyyMMdd");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertEquals(p.getLastLoginTimeFormat(), "yyyyMMdd");
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--remove", "last-login-time-format:yyyyMMdd");
  }



  /**
   * Tests the <CODE>getLastLoginTimeFormat</CODE> method for a password policy
   * using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetLastLoginTimeFormatAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertNull(p.getLastLoginTimeFormat());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "last-login-time-format:yyyyMMdd");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertEquals(p.getLastLoginTimeFormat(), "yyyyMMdd");
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--remove", "last-login-time-format:yyyyMMdd");
  }



  /**
   * Tests the <CODE>getPreviousLastLoginTimeFormats</CODE> method for the
   * default password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetPreviousLastLoginTimeFormatsDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertNotNull(p.getPreviousLastLoginTimeFormats());
    assertTrue(p.getPreviousLastLoginTimeFormats().isEmpty());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "previous-last-login-time-format:yyyyMMdd");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertNotNull(p.getPreviousLastLoginTimeFormats());
    assertFalse(p.getPreviousLastLoginTimeFormats().isEmpty());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--remove", "previous-last-login-time-format:yyyyMMdd");
  }



  /**
   * Tests the <CODE>getPreviousLastLoginTimeFormats</CODE> method for a
   * password policy using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetPreviousLastLoginTimeFormatsAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertNotNull(p.getPreviousLastLoginTimeFormats());
    assertTrue(p.getPreviousLastLoginTimeFormats().isEmpty());

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "previous-last-login-time-format:yyyyMMdd");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertNotNull(p.getPreviousLastLoginTimeFormats());
    assertFalse(p.getPreviousLastLoginTimeFormats().isEmpty());
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--remove", "previous-last-login-time-format:yyyyMMdd");
  }



  /**
   * Tests the <CODE>getIdleLockoutInterval</CODE> method for the default
   * password policy.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetIdleLockoutIntervalDefault()
         throws Exception
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertEquals(p.getIdleLockoutInterval(), 0);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "idle-lockout-interval:90 days");

    p = DirectoryServer.getDefaultPasswordPolicy();
    assertEquals(p.getIdleLockoutInterval(), (90*60*60*24));
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "idle-lockout-interval:0 seconds");
  }



  /**
   * Tests the <CODE>getIdleLockoutInterval</CODE> method for a password policy
   * using the authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testGetIdleLockoutIntervalAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertEquals(p.getIdleLockoutInterval(), 0);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "idle-lockout-interval:90 days");

    p = DirectoryServer.getPasswordPolicy(dn);
    assertEquals(p.getIdleLockoutInterval(), (90*60*60*24));
    p.toString();

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "SHA1 AuthPassword Policy",
      "--set", "idle-lockout-interval:0 seconds");
  }



  /**
   * Tests the ability of a user to bind to the server when their account
   * includes the pwdReset operational attribute and last login time tracking is
   * enabled.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testResetWithLastLoginTime()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test.user,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: oldpassword",
      "ds-privilege-name: bypass-acl"
    );

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "force-change-on-reset:true",
      "--set", "last-login-time-attribute:ds-pwp-last-login-time",
      "--set", "last-login-time-format:yyyyMMdd");

    try
    {
      TestCaseUtils.applyModifications(
        "dn: uid=test.user,o=test",
        "changetype: modify",
        "replace: userPassword",
        "userPassword: newpassword");

      String path = TestCaseUtils.createTempFile(
        "dn: uid=test.user,o=test",
        "changetype: modify",
        "replace: userPassword",
        "userPassword: newnewpassword"
      );

      String[] args =
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "uid=test.user,o=test",
        "-w", "newpassword",
        "-f", path
      };

      assertEquals(LDAPModify.mainModify(args, false, null, System.err), 0);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-password-policy-prop",
        "--policy-name", "Default Password Policy",
        "--set", "force-change-on-reset:false",
        "--remove", "last-login-time-attribute:ds-pwp-last-login-time",
        "--remove", "last-login-time-format:yyyyMMdd");
    }
  }



  /**
   * Tests the Directory Server's password history maintenance capabilities
   * using only the password history count configuration option.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPasswordHistoryUsingCount()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test.user,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: originalPassword",
      "ds-privilege-name: bypass-acl");

    // Make sure that before we enable history features we can re-use the
    // current password.
    String origPWPath = TestCaseUtils.createTempFile(
      "dn: uid=test.user,o=test",
      "changetype: modify",
      "replace: userPassword",
      "userPassword: originalPassword");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "originalPassword",
      "-f", origPWPath
    };

    assertEquals(LDAPModify.mainModify(args, false, System.out, System.err), 0);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "password-history-count:3");

    try
    {
      // Make sure that we cannot re-use the original password as a new
      // password.
      assertFalse(LDAPModify.mainModify(args, false, System.out, System.err) ==
                  0);


      // Change the password three times.
      String newPWsPath = TestCaseUtils.createTempFile(
        "dn: uid=test.user,o=test",
        "changetype: modify",
        "replace: userPassword",
        "userPassword: newPassword1",
        "",
        "dn: uid=test.user,o=test",
        "changetype: modify",
        "replace: userPassword",
        "userPassword: newPassword2",
        "",
        "dn: uid=test.user,o=test",
        "changetype: modify",
        "replace: userPassword",
        "userPassword: newPassword3");

      args = new String[]
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "uid=test.user,o=test",
        "-w", "originalPassword",
        "-f", newPWsPath
      };

      assertEquals(LDAPModify.mainModify(args, false, System.out, System.err),
                   0);


      // Sleep until we can be sure that the time thread has been updated.
      // Otherwise, the password changes can all appear to be in the same
      // millisecond and really weird things start to happen because of the way
      // that we handle conflicts in password history timestamps.  In short, if
      // the history is already at the maximum count and all the previous
      // changes occurred in the same millisecond as the new change, then it's
      // possible for a new change to come with a timestamp that is before the
      // timestamps of all the existing values.
      long timeThreadCurrentTime = TimeThread.getTime();
      while (timeThreadCurrentTime == TimeThread.getTime())
      {
        Thread.sleep(10);
      }


      // Make sure that we still can't use the original password.
      args = new String[]
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "uid=test.user,o=test",
        "-w", "newPassword3",
        "-f", origPWPath
      };

      assertFalse(LDAPModify.mainModify(args, false, System.out, System.err) ==
                  0);


      // Change the password one more time and then verify that we can use the
      // original password again.
      String newPWsPath2 = TestCaseUtils.createTempFile(
        "dn: uid=test.user,o=test",
        "changetype: modify",
        "replace: userPassword",
        "userPassword: newPassword4",
        "",
        "dn: uid=test.user,o=test",
        "changetype: modify",
        "replace: userPassword",
        "userPassword: originalPassword");

      args = new String[]
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "uid=test.user,o=test",
        "-w", "newPassword3",
        "-f", newPWsPath2
      };

      assertEquals(LDAPModify.mainModify(args, false, System.out, System.err),
                   0);


      // Sleep again to ensure that the time thread is updated.
      timeThreadCurrentTime = TimeThread.getTime();
      while (timeThreadCurrentTime == TimeThread.getTime())
      {
        Thread.sleep(10);
      }


      // Make sure that we can't use the second new password.
      String firstPWPath = TestCaseUtils.createTempFile(
        "dn: uid=test.user,o=test",
        "changetype: modify",
        "replace: userPassword",
        "userPassword: newPassword2");

      args = new String[]
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "uid=test.user,o=test",
        "-w", "originalPassword",
        "-f", firstPWPath
      };

      assertFalse(LDAPModify.mainModify(args, false, System.out, System.err) ==
                  0);


      // Sleep again to ensure that the time thread is updated.
      timeThreadCurrentTime = TimeThread.getTime();
      while (timeThreadCurrentTime == TimeThread.getTime())
      {
        Thread.sleep(10);
      }


      // Reduce the password history count from 3 to 2 and verify that we can
      // now use the second new password.
      TestCaseUtils.dsconfig(
        "set-password-policy-prop",
        "--policy-name", "Default Password Policy",
        "--set", "password-history-count:2");

      assertEquals(LDAPModify.mainModify(args, false, System.out, System.err),
                   0);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-password-policy-prop",
        "--policy-name", "Default Password Policy",
        "--set", "password-history-count:0");
    }
  }



  /**
   * Tests the Directory Server's password history maintenance capabilities
   * using only the password history duration configuration option.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(groups = "slow")
  public void testPasswordHistoryUsingDuration()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntry(
      "dn: uid=test.user,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "userPassword: originalPassword",
      "ds-privilege-name: bypass-acl");

    // Make sure that before we enable history features we can re-use the
    // current password.
    String origPWPath = TestCaseUtils.createTempFile(
      "dn: uid=test.user,o=test",
      "changetype: modify",
      "replace: userPassword",
      "userPassword: originalPassword");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "originalPassword",
      "-f", origPWPath
    };

    assertEquals(LDAPModify.mainModify(args, false, System.out, System.err), 0);

    TestCaseUtils.dsconfig(
      "set-password-policy-prop",
      "--policy-name", "Default Password Policy",
      "--set", "password-history-duration:5 seconds");

    try
    {
      // Make sure that we can no longer re-use the original password as a new
      // password.
      assertFalse(LDAPModify.mainModify(args, false, System.out, System.err) ==
                  0);


      // Change the password three times.
      String newPWsPath = TestCaseUtils.createTempFile(
        "dn: uid=test.user,o=test",
        "changetype: modify",
        "replace: userPassword",
        "userPassword: newPassword1",
        "",
        "dn: uid=test.user,o=test",
        "changetype: modify",
        "replace: userPassword",
        "userPassword: newPassword2",
        "",
        "dn: uid=test.user,o=test",
        "changetype: modify",
        "replace: userPassword",
        "userPassword: newPassword3");

      args = new String[]
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "uid=test.user,o=test",
        "-w", "originalPassword",
        "-f", newPWsPath
      };

      assertEquals(LDAPModify.mainModify(args, false, System.out, System.err),
                   0);


      // Make sure that we still can't use the original password.
      args = new String[]
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "uid=test.user,o=test",
        "-w", "newPassword3",
        "-f", origPWPath
      };

      assertFalse(LDAPModify.mainModify(args, false, System.out, System.err) ==
                  0);


      // Sleep for six seconds and then verify that we can use the original
      // password again.
      Thread.sleep(6000);
      assertEquals(LDAPModify.mainModify(args, false, System.out, System.err),
                  0);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-password-policy-prop",
        "--policy-name", "Default Password Policy",
        "--set", "password-history-duration:0 seconds");
    }
  }



  /**
   * Tests to ensure that the server will reject an attempt to set the password
   * expiration warning interval to a value larger than the maximum password
   * age.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testWarningIntervalGreaterThanMaxAge()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
      "dn: cn=Default Password Policy,cn=Password Policies,cn=config",
      "changetype: modify",
      "replace: ds-cfg-maximum-password-age",
      "ds-cfg-maximum-password-age: 5 days",
      "-",
      "replace: ds-cfg-password-expiration-warning-interval",
      "ds-cfg-password-expiration-warning-interval: 10 days");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertFalse(LDAPModify.mainModify(args, false, System.out, System.err) ==
                0);
  }



  /**
   * Tests to ensure that the server will reject an attempt to set the sum of
   * the password expiration warning interval and the minimum password age to a
   * value larger than the maximum password age.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testMinAgePlusWarningIntervalGreaterThanMaxAge()
         throws Exception
  {
    String path = TestCaseUtils.createTempFile(
      "dn: cn=Default Password Policy,cn=Password Policies,cn=config",
      "changetype: modify",
      "replace: ds-cfg-maximum-password-age",
      "ds-cfg-maximum-password-age: 5 days",
      "-",
      "replace: ds-cfg-minimum-password-age",
      "ds-cfg-minimum-password-age: 3 days",
      "-",
      "replace: ds-cfg-password-expiration-warning-interval",
      "ds-cfg-password-expiration-warning-interval: 3 days");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertFalse(LDAPModify.mainModify(args, false, System.out, System.err) ==
                0);
  }



  /**
   * Tests the <CODE>toString</CODE> methods with the default password policy.
   */
  @Test()
  public void testToStringDefault()
  {
    PasswordPolicy p = DirectoryServer.getDefaultPasswordPolicy();
    assertNotNull(p.toString());

    StringBuilder buffer = new StringBuilder();
    p.toString(buffer);
    assertFalse(buffer.length() == 0);
  }



  /**
   * Tests the <CODE>toString</CODE> methods with a password policy using the
   * authentication password syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testToStringAuth()
         throws Exception
  {
    DN dn = DN.decode("cn=SHA1 AuthPassword Policy,cn=Password Policies," +
                      "cn=config");
    PasswordPolicy p = DirectoryServer.getPasswordPolicy(dn);
    assertNotNull(p.toString());

    StringBuilder buffer = new StringBuilder();
    p.toString(buffer);
    assertFalse(buffer.length() == 0);
  }
}

