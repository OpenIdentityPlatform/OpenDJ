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
 *      Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.server.plugins;



import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.opends.server.TestCaseUtils;
import org.opends.server.types.ResultCode;
import org.opends.server.core.AddOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.tools.LDAPModify;

import static org.testng.Assert.*;



/**
 * This class defines a set of tests for the
 * org.opends.server.plugins.SevenBitCleanPlugin class.
 */
public class SevenBitCleanPluginTestCase
       extends PluginTestCase
{
  /**
   * The base64-encoded value that will be used as the password for entries that
   * are not 7-bit clean.
   */
  public static final String BASE64_DIRTY_PASSWORD = "cORzc3f2cmQ=";



  /**
   * Ensures that the Directory Server is running.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void startServer()
         throws Exception
  {
    TestCaseUtils.startServer();
  }



  /**
   * Tests to ensure that it is possible to add a clean entry when the plugin
   * is disabled.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddCleanAllowedDisabled()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String path = TestCaseUtils.createTempFile(
      "dn: uid=test.user,o=test",
      "changetype: add",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "mail: test.user@example.com",
      "userPassword: password");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, System.out, System.err), 0);
  }



  /**
   * Tests to ensure that it is possible to add a dirty entry when the plugin
   * is disabled.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddDirtyAllowedDisabled()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    String path = TestCaseUtils.createTempFile(
      "dn: uid=test.user,o=test",
      "changetype: add",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid: test.user",
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "mail: test.user@example.com",
      "userPassword:: " + BASE64_DIRTY_PASSWORD);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, System.out, System.err), 0);
  }



  /**
   * Tests to ensure that it is possible to add a clean entry when the plugin
   * is enabled.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddCleanAllowedEnabled()
         throws Exception
  {
    TestCaseUtils.dsconfig(
      "set-plugin-prop",
      "--plugin-name", "7-Bit Clean",
      "--set", "enabled:true");

    try
    {
      TestCaseUtils.initializeTestBackend(true);

      String path = TestCaseUtils.createTempFile(
        "dn: uid=test.user,o=test",
        "changetype: add",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "mail: test.user@example.com",
        "userPassword: password");

      String[] args =
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "cn=Directory Manager",
        "-w", "password",
        "-f", path
      };

      assertEquals(LDAPModify.mainModify(args, false, System.out, System.err),
                   0);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-plugin-prop",
        "--plugin-name", "7-Bit Clean",
        "--set", "enabled:false");
    }
  }



  /**
   * Tests to ensure that it is not possible to add a dirty entry when the
   * plugin is enabled.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddDirtyRejectedEnabled()
         throws Exception
  {
    TestCaseUtils.dsconfig(
      "set-plugin-prop",
      "--plugin-name", "7-Bit Clean",
      "--set", "enabled:true");

    try
    {
      TestCaseUtils.initializeTestBackend(true);

      String path = TestCaseUtils.createTempFile(
        "dn: uid=test.user,o=test",
        "changetype: add",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "mail: test.user@example.com",
        "userPassword:: " + BASE64_DIRTY_PASSWORD);

      String[] args =
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "cn=Directory Manager",
        "-w", "password",
        "-f", path
      };

      assertEquals(LDAPModify.mainModify(args, false, System.out, System.err),
                  ResultCode.CONSTRAINT_VIOLATION.getIntValue());
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-plugin-prop",
        "--plugin-name", "7-Bit Clean",
        "--set", "enabled:false");
    }
  }



  /**
   * Tests to ensure that it is not possible to add a dirty entry when the
   * plugin is enabled but the entry being added is outside of the scope of the
   * plugin.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testAddDirtyAcceptedEnabledOutsideScope()
         throws Exception
  {
    TestCaseUtils.dsconfig(
      "set-plugin-prop",
      "--plugin-name", "7-Bit Clean",
      "--set", "enabled:true",
      "--set", "base-dn:dc=example,dc=com");

    try
    {
      TestCaseUtils.initializeTestBackend(true);

      String path = TestCaseUtils.createTempFile(
        "dn: uid=test.user,o=test",
        "changetype: add",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "uid: test.user",
        "givenName: Test",
        "sn: User",
        "cn: Test User",
        "mail: test.user@example.com",
        "userPassword:: " + BASE64_DIRTY_PASSWORD);

      String[] args =
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "cn=Directory Manager",
        "-w", "password",
        "-f", path
      };

      assertEquals(LDAPModify.mainModify(args, false, System.out, System.err),
                   0);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-plugin-prop",
        "--plugin-name", "7-Bit Clean",
        "--set", "enabled:false",
        "--remove", "base-dn:dc=example,dc=com");
    }
  }



  /**
   * Tests to ensure that it is possible to modify an entry to have a dirty
   * password value with the plugin disabled.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyDirtyAllowedDisabled()
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
      "mail: test.user@example.com",
      "userPassword: password");

    String path = TestCaseUtils.createTempFile(
      "dn: uid=test.user,o=test",
      "changetype: modify",
      "replace: userPassword",
      "userPassword:: " + BASE64_DIRTY_PASSWORD);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, System.out, System.err), 0);
  }



  /**
   * Tests to ensure that it is not possible to modify an entry to have a dirty
   * password value with the plugin enabled.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyDirtyRejectedEnabled()
         throws Exception
  {
    TestCaseUtils.dsconfig(
      "set-plugin-prop",
      "--plugin-name", "7-Bit Clean",
      "--set", "enabled:true");

    try
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
        "mail: test.user@example.com",
        "userPassword: password");

      String path = TestCaseUtils.createTempFile(
        "dn: uid=test.user,o=test",
        "changetype: modify",
        "replace: userPassword",
        "userPassword:: " + BASE64_DIRTY_PASSWORD);

      String[] args =
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "cn=Directory Manager",
        "-w", "password",
        "-f", path
      };

      assertEquals(LDAPModify.mainModify(args, false, System.out, System.err),
                  ResultCode.CONSTRAINT_VIOLATION.getIntValue());
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-plugin-prop",
        "--plugin-name", "7-Bit Clean",
        "--set", "enabled:false");
    }
  }



  /**
   * Tests to ensure that it is possible to modify an entry containing a dirty
   * password value when changing that value to be clean.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyDirtyToCleanAllowedEnabled()
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
        "mail: test.user@example.com",
        "userPassword:: " + BASE64_DIRTY_PASSWORD);

    TestCaseUtils.dsconfig(
      "set-plugin-prop",
      "--plugin-name", "7-Bit Clean",
      "--set", "enabled:true");

    try
    {
      String path = TestCaseUtils.createTempFile(
        "dn: uid=test.user,o=test",
        "changetype: modify",
        "replace: userPassword",
        "userPassword: clean");

      String[] args =
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "cn=Directory Manager",
        "-w", "password",
        "-f", path
      };

      assertEquals(LDAPModify.mainModify(args, false, System.out, System.err),
                   0);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-plugin-prop",
        "--plugin-name", "7-Bit Clean",
        "--set", "enabled:false");
    }
  }



  /**
   * Tests to ensure that it is possible to modify an entry containing a dirty
   * password in order to remove that value.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyRemoveDirtyValueAllowedEnabled()
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
        "mail: test.user@example.com",
        "userPassword:: " + BASE64_DIRTY_PASSWORD);

    TestCaseUtils.dsconfig(
      "set-plugin-prop",
      "--plugin-name", "7-Bit Clean",
      "--set", "enabled:true");

    try
    {
      String path = TestCaseUtils.createTempFile(
        "dn: uid=test.user,o=test",
        "changetype: modify",
        "delete: userPassword",
        "userPassword:: " + BASE64_DIRTY_PASSWORD);

      String[] args =
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "cn=Directory Manager",
        "-w", "password",
        "-f", path
      };

      assertEquals(LDAPModify.mainModify(args, false, System.out, System.err),
                   0);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-plugin-prop",
        "--plugin-name", "7-Bit Clean",
        "--set", "enabled:false");
    }
  }



  /**
   * Tests to ensure that it is possible to perform a modify DN operation to
   * provide a dirty new RDN with the plugin disabled.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyDNDirtyAllowedDisabled()
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
      "mail: test.user@example.com",
      "userPassword: password");

    String path = TestCaseUtils.createTempFile(
      "dn: uid=test.user,o=test",
      "changetype: modrdn",
      "newrdn: uid=p\\e4ssw\\f6rd",
      "deleteoldrdn: 1");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", "cn=Directory Manager",
      "-w", "password",
      "-f", path
    };

    assertEquals(LDAPModify.mainModify(args, false, System.out, System.err), 0);
  }



  /**
   * Tests to ensure that it is not possible to perform a modify DN operation to
   * provide a dirty new RDN with the plugin enabled.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyDNDirtyRejectedEnabled()
         throws Exception
  {
    TestCaseUtils.dsconfig(
      "set-plugin-prop",
      "--plugin-name", "7-Bit Clean",
      "--set", "enabled:true");

    try
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
        "mail: test.user@example.com",
        "userPassword: password");

      String path = TestCaseUtils.createTempFile(
        "dn: uid=test.user,o=test",
        "changetype: modrdn",
        "newrdn: uid=p\\e4ssw\\f6rd",
        "deleteoldrdn: 1");

      String[] args =
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "cn=Directory Manager",
        "-w", "password",
        "-f", path
      };

      assertEquals(LDAPModify.mainModify(args, false, System.out, System.err),
                  ResultCode.CONSTRAINT_VIOLATION.getIntValue());
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-plugin-prop",
        "--plugin-name", "7-Bit Clean",
        "--set", "enabled:false");
    }
  }



  /**
   * Tests to ensure that it is possible to perform a modify DN operation to
   * provide a clean new RDN for a dirty entry with the plugin enabled.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testModifyDNDirtyToCleanAllowedEnabled()
         throws Exception
  {
    TestCaseUtils.initializeTestBackend(true);

    TestCaseUtils.addEntry(
      "dn: uid=p\\e4ssw\\f6rd,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "uid:: " + BASE64_DIRTY_PASSWORD,
      "givenName: Test",
      "sn: User",
      "cn: Test User",
      "mail: test.user@example.com",
      "userPassword: password");

    TestCaseUtils.dsconfig(
      "set-plugin-prop",
      "--plugin-name", "7-Bit Clean",
      "--set", "enabled:true");

    try
    {
      String path = TestCaseUtils.createTempFile(
        "dn: uid=p\\e4ssw\\f6rd,o=test",
        "changetype: modrdn",
        "newrdn: uid=test.user",
        "deleteoldrdn: 1");

      String[] args =
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", "cn=Directory Manager",
        "-w", "password",
        "-f", path
      };

      assertEquals(LDAPModify.mainModify(args, false, System.out, System.err),
                   0);
    }
    finally
    {
      TestCaseUtils.dsconfig(
        "set-plugin-prop",
        "--plugin-name", "7-Bit Clean",
        "--set", "enabled:false");
    }
  }
}

