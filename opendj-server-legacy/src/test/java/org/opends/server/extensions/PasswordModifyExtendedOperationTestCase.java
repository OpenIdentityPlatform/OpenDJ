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
 * Copyright 2006-2010 Sun Microsystems, Inc.
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.extensions;

import java.util.List;

import org.forgerock.opendj.config.server.ConfigException;
import org.forgerock.opendj.io.ASN1;
import org.forgerock.opendj.io.ASN1Writer;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.server.TestCaseUtils;
import org.forgerock.opendj.server.config.meta.PasswordModifyExtendedOperationHandlerCfgDefn;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ExtendedOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.tools.LDAPPasswordModify;
import org.opends.server.types.AuthenticationInfo;
import org.forgerock.opendj.ldap.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.util.ServerConstants.*;
import static org.testng.Assert.*;

/**
 * A set of test cases for the password modify extended operation.
 */
public class PasswordModifyExtendedOperationTestCase
       extends ExtensionsTestCase
{
  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Retrieves a set of invalid configuration entries.
   *
   * @return The set of invalid configuration entries.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @DataProvider(name = "invalidConfigs")
  public Object[][] getInvalidConfigs()
         throws Exception
  {
    List<Entry> entries = TestCaseUtils.makeEntries(
         "dn: cn=Password Modify,cn=Extended Operations,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-extended-operation-handler",
         "objectClass: ds-cfg-password-modify-extended-operation-handler",
         "cn: Password Modify",
         "ds-cfg-java-class: org.opends.server.extensions.PasswordModifyExtendedOperation",
         "ds-cfg-enabled: true",
         "",
         "dn: cn=Password Modify,cn=Extended Operations,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-extended-operation-handler",
         "objectClass: ds-cfg-password-modify-extended-operation-handler",
         "cn: Password Modify",
         "ds-cfg-java-class: org.opends.server.extensions.PasswordModifyExtendedOperation",
         "ds-cfg-enabled: true",
         "ds-cfg-identity-mapper: invaliddn",
         "",
         "dn: cn=Password Modify,cn=Extended Operations,cn=config",
         "objectClass: top",
         "objectClass: ds-cfg-extended-operation-handler",
         "objectClass: ds-cfg-password-modify-extended-operation-handler",
         "cn: Password Modify",
         "ds-cfg-java-class: org.opends.server.extensions.PasswordModifyExtendedOperation",
         "ds-cfg-enabled: true",
         "ds-cfg-identity-mapper: cn=nonexistent,cn=config");

    Object[][] array = new Object[entries.size()][1];
    for (int i=0; i < array.length; i++)
    {
      array[i] = new Object[] { entries.get(i) };
    }

    return array;
  }



  /**
   * Tests the process of initializing the server with invalid configurations.
   *
   * @param  e  The configuration entry to use for the initialization.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "invalidConfigs", expectedExceptions = { ConfigException.class, InitializationException.class})
  public void testInitializeWithInvalidConfigs(Entry e)
         throws Exception
  {
    InitializationUtils.initializeExtendedOperationHandler(
        new PasswordModifyExtendedOperation(), e, PasswordModifyExtendedOperationHandlerCfgDefn.getInstance());
  }



  /**
   * Tests the password modify extended operation over LDAP.  It will use the following configuration:
   * <BR>
   * <UL>
   *   <LI>Authenticated as a root user</LI>
   *   <LI>No authorization ID provided</LI>
   *   <LI>Current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testAsRootImplicitSelfWithOldPassword()
         throws Exception
  {
    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-c", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 0);

    verifyPasswordPerformingInternalBind(DN.valueOf("cn=Directory Manager"), "newPassword");

    // Now change the password back to what it was.
    args = new String[]
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "newPassword",
      "-c", "newPassword",
      "-n", "password"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 0);
  }


  /**
   * Tests the password modify extended operation over LDAP.  It will use the following configuration:
   * <BR>
   * <UL>
   *   <LI>Authenticated as a root user</LI>
   *   <LI>Authorization ID provided</LI>
   *   <LI>Current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testAsRootExplicitSelfWithOldPassword()
         throws Exception
  {
    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-c", "password",
      "-n", "newPassword",
      "-A"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 0);

    verifyPasswordPerformingInternalBind(DN.valueOf("cn=Directory Manager"), "newPassword");

    // Now change the password back to what it was.
    args = new String[]
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "newPassword",
      "-c", "newPassword",
      "-n", "password",
      "-A"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 0);
  }


  /**
   * Tests the password modify extended operation over LDAP.  It will use the following configuration:
   * <BR>
   * <UL>
   *   <LI>Unauthenticated client connection</LI>
   *   <LI>Authorization ID provided</LI>
   *   <LI>Current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testAsAnonymousExplicitRootWithOldPassword()
         throws Exception
  {
    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-a", "dn:cn=Directory Manager",
      "-c", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 0);

    verifyPasswordPerformingInternalBind(DN.valueOf("cn=Directory Manager"), "newPassword");

    // Now change the password back to what it was.
    args = new String[]
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-a", "dn:cn=Directory Manager",
      "-c", "newPassword",
      "-n", "password"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 0);
  }


  /**
   * Tests the password modify extended operation over LDAP.  It will use the following configuration:
   * <BR>
   * <UL>
   *   <LI>Authenticated as a normal user</LI>
   *   <LI>No authorization ID provided</LI>
   *   <LI>Current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testAsUserImplicitSelfWithOldPasswordWithNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = addDefaultTestEntry();


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-c", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 0);

    verifyPasswordPerformingInternalBind(userEntry.getName(), "newPassword");
  }


  /**
   * Tests the password modify extended operation over LDAP.  It will use the following configuration:
   * <BR>
   * <UL>
   *   <LI>Authenticated as a normal user</LI>
   *   <LI>No authorization ID provided</LI>
   *   <LI>No current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testAsUserImplicitSelfNoOldPasswordWithNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = addDefaultTestEntry();


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 0);

    verifyPasswordPerformingInternalBind(userEntry.getName(), "newPassword");
  }



  /**
   * Tests the password modify extended operation over LDAP.  It will use the following configuration:
   * <BR>
   * <UL>
   *   <LI>Authenticated as a normal user</LI>
   *   <LI>No authorization ID provided</LI>
   *   <LI>No current password provided</LI>
   *   <LI>No new password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testAsUserImplicitSelfNoOldPasswordNoNewPassword()
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
         "ds-privilege-name: bypass-acl",
         "userPassword: password");


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 0);
  }


  /**
   * Tests the password modify extended operation over LDAP.  It will use the following configuration:
   * <BR>
   * <UL>
   *   <LI>Authenticated as a normal user</LI>
   *   <LI>Authorization ID provided ("dn:" form)</LI>
   *   <LI>No current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testAsUserExplicitDNColonSelfNoOldPasswordWithNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = addDefaultTestEntry();


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 0);

    verifyPasswordPerformingInternalBind(userEntry.getName(), "newPassword");
  }



  /**
   * Tests the password modify extended operation over LDAP.  It will use the following configuration:
   * <BR>
   * <UL>
   *   <LI>Authenticated as a normal user</LI>
   *   <LI>Authorization ID provided ("u:" form)</LI>
   *   <LI>No current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testAsUserExplicitUColonSelfNoOldPasswordWithNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = addDefaultTestEntry();


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-a", "u:test.user",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 0);

    verifyPasswordPerformingInternalBind(userEntry.getName(), "newPassword");
  }



  /**
   * Tests the password modify extended operation over LDAP.  It will use the following configuration:
   * <BR>
   * <UL>
   *   <LI>Authenticated as a normal user</LI>
   *   <LI>userIdentity provided (LDAPDN form)</LI>
   *   <LI>No current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testAsUserExplicitDNSelfNoOldPasswordWithNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = addDefaultTestEntry();


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-a", "uid=test.user,o=test",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 0);

    verifyPasswordPerformingInternalBind(userEntry.getName(), "newPassword");
  }



  /**
   * Tests the password modify extended operation over LDAP.  It will use the following configuration:
   * <BR>
   * <UL>
   *   <LI>Authenticated as a normal user</LI>
   *   <LI>userIdentity provided (userID form)</LI>
   *   <LI>No current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testAsUserExplicitUSelfNoOldPasswordWithNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = addDefaultTestEntry();


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-a", "test.user",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 0);

    verifyPasswordPerformingInternalBind(userEntry.getName(), "newPassword");
  }



  /**
   * Tests the password modify extended operation over LDAP.  It will use the following configuration:
   * <BR>
   * <UL>
   *   <LI>Unauthenticated client connection</LI>
   *   <LI>Authorization ID provided ("dn:" form)</LI>
   *   <LI>Current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testAsExplicitDNColonAnonymousWithOldPasswordWithNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = addDefaultTestEntry();

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-a", "dn:uid=test.user,o=test",
      "-c", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 0);

    verifyPasswordPerformingInternalBind(userEntry.getName(), "newPassword");
  }



  /**
   * Tests the password modify extended operation over LDAP.  It will use the following configuration:
   * <BR>
   * <UL>
   *   <LI>Unauthenticated client connection</LI>
   *   <LI>Authorization ID provided ("u:" form)</LI>
   *   <LI>Current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testAsExplicitUColonAnonymousWithOldPasswordWithNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = addDefaultTestEntry();

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-a", "u:test.user",
      "-c", "password",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 0);

    verifyPasswordPerformingInternalBind(userEntry.getName(), "newPassword");
  }



  /**
   * Tests the password modify extended operation over LDAP.  It will use the following configuration:
   * <BR>
   * <UL>
   *   <LI>Authenticated as a root user</LI>
   *   <LI>Authorization ID provided ("dn:" form) for a different user</LI>
   *   <LI>No current password provided</LI>
   *   <LI>New password provided</LI>
   * </UL>
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testRootPasswordResetNoCurrentPasswordWithNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = addDefaultTestEntry();

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-n", "newPassword"
    };
    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 0);

    verifyPasswordPerformingInternalBind(userEntry.getName(), "newPassword");
  }



  /**
   * Tests the password modify extended operation over LDAP using an authorization ID
   * with the DN of a user that doesn't exist.
   *
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailureDNColonNoSuchUser()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-n", "newPassword"
    };

    assertFalse(0 == LDAPPasswordModify.mainPasswordModify(args, false, null, null));
  }



  /**
   * Tests the password modify extended operation over LDAP using an authorization ID with a malformed DN.
   *
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailureDNColonMalformedDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:malformed",
      "-n", "newPassword"
    };

    assertFalse(0 == LDAPPasswordModify.mainPasswordModify(args, false, null, null));
  }



  /**
   * Tests the password modify extended operation over LDAP using an authorization ID with the DN
   * of a user where no part of the hierarchy exists.
   *
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailureDNColonNoSuchBaseDN()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=doesnt.exist,o=doesnt.exist",
      "-n", "newPassword"
    };

    assertFalse(0 == LDAPPasswordModify.mainPasswordModify(args, false, null, null));
  }



  /**
   * Tests the password modify extended operation over LDAP using an authorization ID with the uid
   * of a user that doesn't exist.
   *
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailureUColonNoSuchUser()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "u:test.user",
      "-n", "newPassword"
    };

    assertFalse(0 == LDAPPasswordModify.mainPasswordModify(args, false, null, null));
  }



  /**
   * Tests the password modify extended operation over LDAP using an authorization ID with the uid
   * of a user that doesn't exist and providing the current password.
   *
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailureUColonNoSuchUserWithCurrentPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "u:test.user",
      "-c", "password",
      "-n", "newPassword"
    };

    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 32);
  }



  /**
   * Tests the password modify extended operation over LDAP using a malformed authorization ID.
   *
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailureMalformedAuthZID()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "malformed",
      "-n", "newPassword"
    };

    assertFalse(0 == LDAPPasswordModify.mainPasswordModify(args, false, null, null));
  }



  /**
   * Tests the password modify extended operation over LDAP using a bad current password.
   *
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailureWrongCurrentPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    addDefaultTestEntry();

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-c", "wrongPassword",
      "-n", "newPassword"
    };

    assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 49);
  }



  /**
   * Tests the password modify extended operation over LDAP using a pre-encoded new password.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testAllowPreEncoded()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    addDefaultTestEntry();

    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-allow-pre-encoded-passwords";
    applyPwdPolicyMods(dnStr, attr, "true");


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-n", "{SSHA}Fv4b7f4AnRMUiGqBi9QA1xJrTtRTqS3WpRi81g=="
    };

    try {
      assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 0);
    } finally {
      applyPwdPolicyMods(dnStr, attr, "false");
    }
  }



  /**
   * Tests the password modify extended operation over LDAP using a pre-encoded new password.
   *
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailurePreEncodedNewPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    addDefaultTestEntry();

    /* Make sure preEncoded passwords are rejected */
    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-allow-pre-encoded-passwords";
    applyPwdPolicyMods(dnStr, attr, "false");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-n", "{SSHA}Fv4b7f4AnRMUiGqBi9QA1xJrTtRTqS3WpRi81g=="
    };

    assertFalse(0 == LDAPPasswordModify.mainPasswordModify(args, false, null, null));
    // don't restore password policy as this is already the default.
  }



  /**
   * Tests the password modify extended operation over LDAP using a pre-encoded new password.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailurePreEncodedNewPasswordWithCurrentPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    addDefaultTestEntry();

    /* Make sure preEncoded passwords are rejected. This should be the default, so we will not restore config after */
    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-allow-pre-encoded-passwords";
    applyPwdPolicyMods(dnStr, attr, "false");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-a", "dn:uid=test.user,o=test",
      "-c", "password",
      "-n", "{SSHA}Fv4b7f4AnRMUiGqBi9QA1xJrTtRTqS3WpRi81g=="
    };

    assertFalse(0 == LDAPPasswordModify.mainPasswordModify(args, false, null, null));
  }



  /**
   * Tests the password modify extended operation over an internal connection with a request whose value
   * isn't a valid encoded sequence.
   */
  @Test
  public void testFailureInvalidRequestValueFormat()
  {
    ByteString requestValue = ByteString.valueOfUtf8("malformed");

    InternalClientConnection conn = getRootConnection();
    ExtendedOperation extOp = conn.processExtendedOperation(OID_PASSWORD_MODIFY_REQUEST, requestValue);
    assertNotSame(extOp.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation over an internal connection with a request that contain
   * an invalid sequence element type.
   */
  @Test
  public void testFailureInvalidSequenceElementType() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence();
    writer.writeNull((byte)0x50);
    writer.writeEndSequence();
    ByteString requestValue = builder.toByteString();

    InternalClientConnection conn = getRootConnection();
    ExtendedOperation extOp = conn.processExtendedOperation(OID_PASSWORD_MODIFY_REQUEST, requestValue);
    assertNotSame(extOp.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation over an unauthenticated internal connection
   * and without providing an authorization ID.
   */
  @Test
  public void testFailureCompletelyAnonymous() throws Exception
  {
    ByteStringBuilder builder = new ByteStringBuilder();
    ASN1Writer writer = ASN1.getWriter(builder);
    writer.writeStartSequence();
    writer.writeOctetString(TYPE_PASSWORD_MODIFY_NEW_PASSWORD, "newPassword");
    writer.writeEndSequence();
    ByteString requestValue = builder.toByteString();

    InternalClientConnection conn = new InternalClientConnection(new AuthenticationInfo());
    ExtendedOperation extOp = conn.processExtendedOperation(OID_PASSWORD_MODIFY_REQUEST, requestValue);
    assertNotSame(extOp.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the password modify extended operation with a password policy that doesn't allow users
   * to change their own passwords. The current password is not provided in the extended operation.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailureRejectUserPasswordChanges()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    addDefaultTestEntry();


    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-allow-user-password-changes";
    applyPwdPolicyMods(dnStr, attr, "false");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-n", "newPassword"
    };

    try {
      assertFalse(0 == LDAPPasswordModify.mainPasswordModify(args, false, null, null));
    } finally {
      applyPwdPolicyMods(dnStr, attr, "true");
    }
  }



  /**
   * Tests the password modify extended operation with a password policy that doesn't allow users to change
   * their own passwords. The current password is provided.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailureRejectUserPasswordChangesWithCurrentPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    addDefaultTestEntry();


    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-allow-user-password-changes";
    applyPwdPolicyMods(dnStr, attr, "false");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-c", "password",
      "-n", "newPassword"
    };

    try {
      assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 53);
    } finally {
      applyPwdPolicyMods(dnStr, attr, "true");
    }
  }



  /**
   * Tests the password modify extended operation without providing the current password
   * but with a password policy that requires it.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailureRequireCurrentPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    addDefaultTestEntry();

    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-password-change-requires-current-password";
    applyPwdPolicyMods(dnStr, attr, "true");


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-n", "newPassword"
    };

    try {
      assertFalse(0 == LDAPPasswordModify.mainPasswordModify(args, false, null, null));
    } finally {
      // Reset to default configuration
      applyPwdPolicyMods(dnStr, attr, "false");
    }
  }



  /**
   * Tests the password modify extended operation that requires secure authentication
   * but a connection that doesn't provide it.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailureRequireSecureAuthentication()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    addDefaultTestEntry();


    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-require-secure-authentication";
    applyPwdPolicyMods(dnStr, attr, "true");


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-a", "dn:uid=test.user,o=test",
      "-c", "password",
      "-n", "newPassword"
    };

    try {
      assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 13);
    } finally {
      // Reset to default configuration
      applyPwdPolicyMods(dnStr, attr, "false");
    }
  }



  /**
   * Tests the password modify extended operation that requires secure password changes
   * but a connection that doesn't provide it, using self-authentication.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailureRequireSecurePasswordChanges()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    addDefaultTestEntry();


    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-require-secure-password-changes";
    applyPwdPolicyMods(dnStr, attr, "true");


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-n", "newPassword"
    };

    try {
      assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 13);
    } finally {
      applyPwdPolicyMods(dnStr, attr, "false");
    }
  }



  /**
   * Tests the password modify extended operation that requires secure password changes
   * but a connection that doesn't provide it. Authenticating as Directory Manager.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailureRequireSecurePasswordChangesWithCurrentPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    addDefaultTestEntry();


    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-require-secure-password-changes";
    applyPwdPolicyMods(dnStr, attr, "true");


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "uid=test.user,o=test",
      "-c", "password",
      "-n", "newPassword"
    };

    try {
      assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 13);
    } finally {
      // Reset to default configuration
      applyPwdPolicyMods(dnStr, attr, "false");
    }
  }



  /**
   * Tests the password modify extended operation with a password change that is within the minimum password age.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailureWithinMinAge()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    addDefaultTestEntry();


    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-min-password-age";
    applyPwdPolicyMods(dnStr, attr, "24 hours");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-n", "newPassword"
    };

    try {
      assertFalse(0 == LDAPPasswordModify.mainPasswordModify(args, false, null, null));
    } finally {
      applyPwdPolicyMods(dnStr, attr, "0 seconds");
    }
  }



  /**
   * Tests the password modify extended operation with a password change that is within the minimum password age.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailureWithinMinAgeWithCurrentPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    addDefaultTestEntry();


    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-min-password-age";
    applyPwdPolicyMods(dnStr, attr, "24 hours");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-c", "password",
      "-n", "newPassword"
    };

    try {
      assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 53);
    } finally {
      applyPwdPolicyMods(dnStr, attr, "0 seconds");
    }
  }



  /**
   * Tests the password modify extended operation with a password change for a user whose password is expired
   * but expired password changes are not allowed.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailureExpiredChangesNotAllowed()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = addDefaultTestEntry();

    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr1 = "ds-cfg-max-password-age";
    applyPwdPolicyMods(dnStr, attr1, "90 days");

    String attr2 = "ds-cfg-expire-passwords-without-warning";
    applyPwdPolicyMods(dnStr, attr2, "true");

    try {
      setPasswordChangedTime(userEntry);

      String[] args =
      {
        "--noPropertiesFile",
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-a", "dn:uid=test.user,o=test",
        "-c", "password",
        "-n", "newPassword"
      };

      assertFalse(0 == LDAPPasswordModify.mainPasswordModify(args, false, null, null));
    } finally {
      applyPwdPolicyMods(dnStr, attr1, "0 seconds");
      applyPwdPolicyMods(dnStr, attr2, "false");
    }
  }


  /**
   * Tests the password modify extended operation with a password change for a user whose password is expired
   * but expired password changes are allowed.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testExpiredChangesAllowed()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    Entry userEntry = addDefaultTestEntry();

    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr1 = "ds-cfg-max-password-age";
    applyPwdPolicyMods(dnStr, attr1, "90 days");

    String attr2 = "ds-cfg-expire-passwords-without-warning";
    applyPwdPolicyMods(dnStr, attr2, "true");

    String attr3 = "ds-cfg-allow-expired-password-changes";
    applyPwdPolicyMods(dnStr, attr3, "true");

    try {
      setPasswordChangedTime(userEntry);

      String[] args =
      {
        "--noPropertiesFile",
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-a", "dn:uid=test.user,o=test",
        "-c", "password",
        "-n", "newPassword"
      };

      assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 0);
    }
    finally {
      applyPwdPolicyMods(dnStr, attr1, "0 seconds");
      applyPwdPolicyMods(dnStr, attr2, "false");
      applyPwdPolicyMods(dnStr, attr3, "false");
    }
  }



  /**
   * Tests the password modify extended operation with a password change for a user
   * where there is no new password provided and there is no password generator.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailureNoPasswordNoGenerator()
         throws Exception {
    TestCaseUtils.initializeTestBackend(true);
    addDefaultTestEntry();


    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr = "ds-cfg-password-generator";
    applyPwdPolicyMods(dnStr, attr);


    String[] args =
        {
            "--noPropertiesFile",
            "-h", "127.0.0.1",
            "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
            "-D", "uid=test.user,o=test",
            "-w", "password"
        };
    try {
      assertFalse(0 == LDAPPasswordModify.mainPasswordModify(args, false, null, null));
    } finally {
      applyPwdPolicyMods(dnStr, attr, "cn=Random Password Generator,cn=Password Generators,cn=config");
    }
  }


  /**
   * Tests the password modify extended operation with a password change for a user where there is no new password
   * provided and there is no password generator.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailureNoPasswordNoGeneratorWithPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    addDefaultTestEntry();


    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr = "ds-cfg-password-generator";
    applyPwdPolicyMods(dnStr, attr);

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-a", "dn:uid=test.user,o=test",
      "-c", "password"
    };
    try {
      assertFalse(0 == LDAPPasswordModify.mainPasswordModify(args, false, null, null));
    }
    finally {
      applyPwdPolicyMods(dnStr, attr, "cn=Random Password Generator,cn=Password Generators,cn=config");
    }
  }



  /**
   * Tests the password modify extended operation with a password change for a user where a password validator
   * rejects the change.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailureValidatorReject()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    addDefaultTestEntry();


    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr = "ds-cfg-password-validator";
    applyPwdPolicyMods(dnStr, attr, "cn=Length-Based Password Validator,cn=Password Validators,cn=config");


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "uid=test.user,o=test",
      "-w", "password",
      "-n", "short"
    };
    try {
      assertFalse(0 == LDAPPasswordModify.mainPasswordModify(args, false, null, null));
    }
    finally {
      applyPwdPolicyMods(dnStr, attr);
    }
  }



  /**
   * Tests the password modify extended operation with a password change for a user where a password validator
   * rejects the change.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testFailureValidatorRejectWithCurrentPassword()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    addDefaultTestEntry();


    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr = "ds-cfg-password-validator";
    applyPwdPolicyMods(dnStr, attr, "cn=Length-Based Password Validator,cn=Password Validators,cn=config");

    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-a", "dn:uid=test.user,o=test",
      "-c", "password",
      "-n", "short"
    };
    try {
      assertFalse(0 == LDAPPasswordModify.mainPasswordModify(args, false, null, null));
    }
    finally {
      applyPwdPolicyMods(dnStr, attr);
    }
  }


  /**
   * Tests the password modify extended operation over LDAP when the existing
   * account has multiple passwords.
   *
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testAllowMultiplePasswords()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);


    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-allow-multiple-password-values";
    applyPwdPolicyMods(dnStr, attr, "true");

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
        "userPassword: password",
        "userPassword: password2");


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-c", "password",
      "-n", "newPassword"
    };

    try {
      assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, null), 0);
    } finally {
      applyPwdPolicyMods(dnStr, attr, "false");
    }
  }



  /**
   * Tests the password modify extended operation over LDAP when the existing account has multiple passwords.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testAllowMultipleAuthPasswords()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String dnStr = "cn=SHA1 AuthPassword Policy,cn=Password Policies,cn=config";
    String attr  = "ds-cfg-allow-multiple-password-values";
    applyPwdPolicyMods(dnStr, attr, "true");

    TestCaseUtils.addEntry(
        "dn: uid=test.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "objectClass: authPasswordObject",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "authPassword: password",
        "authPassword: password2",
        "ds-pwp-password-policy-dn: cn=SHA1 AuthPassword Policy,cn=Password Policies,cn=config");


    String[] args =
    {
      "--noPropertiesFile",
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-a", "dn:uid=test.user,o=test",
      "-c", "password",
      "-n", "newPassword"
    };

    try {
      assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, System.err), 0);
    } finally {
      applyPwdPolicyMods(dnStr, attr, "false");
    }
  }



  /**
   * Tests to ensure that if the user provides the correct old password, then the last login time will be
   * updated if that feature is enabled.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testUpdateLastLoginTime()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);
    addDefaultTestEntry();

    TestCaseUtils.applyModifications(true,
      "dn: cn=Default Password Policy,cn=Password Policies,cn=config",
      "changetype: modify",
      "replace: ds-cfg-last-login-time-attribute",
      "ds-cfg-last-login-time-attribute: ds-pwp-last-login-time",
      "-",
      "replace: ds-cfg-last-login-time-format",
      "ds-cfg-last-login-time-format: yyyyMMdd");

    try
    {
      AttributeType lastLoginTimeAttr = DirectoryServer.getSchema().getAttributeType("ds-pwp-last-login-time");
      assertNotNull(lastLoginTimeAttr);

      DN userDN = DN.valueOf("uid=test.user,o=test");
      Entry userEntry = DirectoryServer.getEntry(userDN);
      assertNotNull(userEntry);
      assertFalse(userEntry.hasAttribute(lastLoginTimeAttr));

      String[] args =
      {
        "--noPropertiesFile",
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-a", "dn:uid=test.user,o=test",
        "-c", "password",
        "-n", "newpassword"
      };

      assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, System.err), 0);

      userEntry = DirectoryServer.getEntry(userDN);
      assertNotNull(userEntry);
      assertTrue(userEntry.hasAttribute(lastLoginTimeAttr));
    }
    finally
    {
      TestCaseUtils.applyModifications(true,
        "dn: cn=Default Password Policy,cn=Password Policies,cn=config",
        "changetype: modify",
        "replace: ds-cfg-last-login-time-attribute",
        "-",
        "replace: ds-cfg-last-login-time-format");
    }
  }


  /**
   * Tests to ensure that if the user provides an incorrect old password, then
   * the auth failure times will be updated if that feature is enabled.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test
  public void testUpdateAuthFailureTimes()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    addDefaultTestEntry();

    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    applyPwdPolicyMods(dnStr, "ds-cfg-lockout-failure-count",
        "3");

    try
    {
      AttributeType authFailureTimesAttr = DirectoryServer.getSchema().getAttributeType("pwdfailuretime");
      assertNotNull(authFailureTimesAttr);

      DN userDN = DN.valueOf("uid=test.user,o=test");
      Entry userEntry = DirectoryServer.getEntry(userDN);
      assertNotNull(userEntry);
      assertFalse(userEntry.hasAttribute(authFailureTimesAttr));

      String[] args =
      {
        "--noPropertiesFile",
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-a", "dn:uid=test.user,o=test",
        "-c", "wrongoldpassword",
        "-n", "newpassword"
      };

      assertFalse(0 == LDAPPasswordModify.mainPasswordModify(args, false, null, System.err));

      userEntry = DirectoryServer.getEntry(userDN);
      assertNotNull(userEntry);
      assertTrue(userEntry.hasAttribute(authFailureTimesAttr));
    } finally {
      applyPwdPolicyMods(dnStr, "ds-cfg-lockout-failure-count",
          "0");
    }
  }


  /**
   * Tests to ensure that if the password is changed with a generated password, the pwdHistory is getting
   * updated properly.
   *
   * @throws  Exception  If an unexpected error occurs.
   */
  @Test()
  public void testUpdatePasswordHistory()
      throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    addDefaultTestEntry();

    String dnStr = "cn=Default Password Policy,cn=Password Policies,cn=config";
    applyPwdPolicyMods(dnStr, "ds-cfg-password-history-count" , "5");

    try
    {
      AttributeType pwdHistoryAttr = DirectoryServer.getSchema().getAttributeType("pwdhistory");
      assertNotNull(pwdHistoryAttr);

      DN userDN = DN.valueOf("uid=test.user,o=test");
      Entry userEntry = DirectoryServer.getEntry(userDN);
      assertNotNull(userEntry);
      assertFalse(userEntry.hasAttribute(pwdHistoryAttr));

      String[] args =
      {
        "--noPropertiesFile",
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-a", "dn:uid=test.user,o=test",
        "-c", "password"
      };

      assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null, System.err), 0);

      userEntry = DirectoryServer.getEntry(userDN);
      assertNotNull(userEntry);
      assertTrue(userEntry.hasAttribute(pwdHistoryAttr));
    }
    finally
    {
      applyPwdPolicyMods(dnStr, "ds-cfg-password-history-count",
          "0");
    }
  }

  private Entry addDefaultTestEntry() throws Exception {
    return TestCaseUtils.addEntry(
        "dn: uid=test.user,o=test",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "ds-privilege-name: bypass-acl",
        "userPassword: password");
  }

  private void verifyPasswordPerformingInternalBind(DN name, String newPwd) throws DirectoryException {
    // Perform an internal bind to verify the password was actually changed.
    InternalClientConnection conn = new InternalClientConnection(new AuthenticationInfo());
    BindOperation bindOperation =
        conn.processSimpleBind(name, ByteString.valueOfUtf8(newPwd));
    assertEquals(bindOperation.getResultCode(), ResultCode.SUCCESS);
  }

  private void applyPwdPolicyMods(String pwpDN, String attrName, Object... attrValues)
      throws DirectoryException
  {
    ModifyRequest modifyRequest = Requests.newModifyRequest(pwpDN)
.addModification(REPLACE, attrName, attrValues);
    ModifyOperation op = getRootConnection().processModify(modifyRequest);
    assertEquals(op.getResultCode(), ResultCode.SUCCESS);
  }

  private void setPasswordChangedTime(Entry userEntry) {
    ModifyRequest modifyRequest = Requests.newModifyRequest(userEntry.getName())
        .addModification(REPLACE, "pwdchangedtime", "20050101000000.000Z");
    ModifyOperation op = getRootConnection().processModify(modifyRequest);
    assertEquals(op.getResultCode(), ResultCode.SUCCESS);
  }
}
