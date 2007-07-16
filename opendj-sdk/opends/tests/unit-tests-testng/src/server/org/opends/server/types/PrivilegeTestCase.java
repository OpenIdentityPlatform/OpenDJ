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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.types;



import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.UUID;

import org.opends.server.TestCaseUtils;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskBackend;
import org.opends.server.backends.task.TaskState;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.AddOperation;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.CompareOperation;
import org.opends.server.core.CompareOperationBasis;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DeleteOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperation;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.core.SchemaConfigManager;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.tools.LDAPModify;
import org.opends.server.tools.LDAPPasswordModify;
import org.opends.server.tools.LDAPSearch;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * This class provides a set of test cases for the Directory Server privilege
 * subsystem.
 *
 * FIXME -- It will likely be necessary to also have access control rules in
 *          place to allow operations as necessary once that functionality has
 *          integrated into the server.
 */
public class PrivilegeTestCase
       extends TypesTestCase
{
  /**
   * The DN of the user that is associated with the internal root connection.
   */
  private static final String INTERNAL_ROOT_DN =
       "cn=Internal Client,cn=Root DNs,cn=config";



  // An array of boolean values that indicates whether config read operations
  // should be successful for users in the corresponding slots of the
  // connections array.
  private boolean[] successful;

  // The set of client connections that should be used when performing
  // operations.
  private InternalClientConnection[] connections;



  /**
   * Make sure that the server is running and that an appropriate set of
   * structures are in place.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass()
  public void setUp()
         throws Exception
  {
    TestCaseUtils.startServer();

    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntries(
      "dn: cn=Unprivileged Root,cn=Root DNs,cn=config",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "objectClass: ds-cfg-root-dn",
      "cn: Unprivileged Root",
      "givenName: Unprivileged",
      "sn: Root",
      "uid: unprivileged.root",
      "userPassword: password",
      "ds-privilege-name: -config-read",
      "ds-privilege-name: -config-write",
      "ds-privilege-name: -password-reset",
      "ds-privilege-name: -update-schema",
      "ds-privilege-name: -ldif-import",
      "ds-privilege-name: -ldif-export",
      "ds-privilege-name: -backend-backup",
      "ds-privilege-name: -backend-restore",
      "ds-privilege-name: -unindexed-search",
      "",
      "dn: cn=Proxy Root,cn=Root DNs,cn=config",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "objectClass: ds-cfg-root-dn",
      "cn: Proxy Root",
      "givenName: Proxy",
      "sn: Root",
      "uid: proxy.root",
      "userPassword: password",
      "ds-privilege-name: proxied-auth",
      "",
      "dn: cn=Privileged User,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "cn: Privileged User",
      "givenName: Privileged",
      "sn: User",
      "uid: privileged.user",
      "userPassword: password",
      "ds-privilege-name: config-read",
      "ds-privilege-name: config-write",
      "ds-privilege-name: password-reset",
      "ds-privilege-name: update-schema",
      "ds-privilege-name: ldif-import",
      "ds-privilege-name: ldif-export",
      "ds-privilege-name: backend-backup",
      "ds-privilege-name: backend-restore",
      "ds-privilege-name: proxied-auth",
      "ds-privilege-name: bypass-acl",
      "ds-privilege-name: unindexed-search",
      "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
           "cn=Password Policies,cn=config",
      "",
      "dn: cn=Unprivileged User,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "cn: Unprivileged User",
      "givenName: Unprivileged",
      "sn: User",
      "uid: unprivileged.user",
      "ds-privilege-name: bypass-acl",
      "userPassword: password",
      "ds-pwp-password-policy-dn: cn=Clear UserPassword Policy," +
           "cn=Password Policies,cn=config",
      "",
      "dn: cn=PWReset Target,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "cn: PWReset Target",
      "givenName: PWReset",
      "sn: Target",
      "uid: pwreset.target",
      "userPassword: password");

    TestCaseUtils.applyModifications(
      "dn: o=test",
      "changetype: modify",
      "add: aci",
      "aci: (version 3.0; acl \"Proxy Root\"; allow (proxy) " +
           "userdn=\"ldap:///cn=Proxy Root,cn=Root DNs,cn=config\";)",
      "aci: (version 3.0; acl \"Unprivileged Root\"; allow (proxy) " +
           "userdn=\"ldap:///cn=Unprivileged Root,cn=Root DNs,cn=config\";)",
      "aci: (version 3.0; acl \"Privileged User\"; allow (proxy) " +
           "userdn=\"ldap:///cn=Privileged User,o=test\";)",
      "aci: (targetattr=\"*\")(version 3.0; acl \"PWReset Target\"; " +
           "allow (all) userdn=\"ldap:///cn=PWReset Target,o=test\";)");


    // Build the array of connections we will use to perform the tests.
    ArrayList<InternalClientConnection> connList =
         new ArrayList<InternalClientConnection>();
    ArrayList<Boolean> successList = new ArrayList<Boolean>();

    connList.add(new InternalClientConnection(new AuthenticationInfo()));
    successList.add(false);

    connList.add(InternalClientConnection.getRootConnection());
    successList.add(true);

    String userDN = "cn=Directory Manager,cn=Root DNs,cn=config";
    Entry userEntry = DirectoryServer.getEntry(DN.decode(userDN));
    AuthenticationInfo authInfo = new AuthenticationInfo(userEntry, true);
    connList.add(new InternalClientConnection(authInfo));
    successList.add(true);

    userDN    = "cn=Unprivileged Root,cn=Root DNs,cn=config";
    userEntry = DirectoryServer.getEntry(DN.decode(userDN));
    authInfo  = new AuthenticationInfo(userEntry, true);
    connList.add(new InternalClientConnection(authInfo));
    successList.add(false);

    userDN    = "cn=Proxy Root,cn=Root DNs,cn=config";
    userEntry = DirectoryServer.getEntry(DN.decode(userDN));
    authInfo  = new AuthenticationInfo(userEntry, true);
    connList.add(new InternalClientConnection(authInfo));
    successList.add(true);

    userDN    = "cn=Unprivileged User,o=test";
    userEntry = DirectoryServer.getEntry(DN.decode(userDN));
    authInfo  = new AuthenticationInfo(userEntry, false);
    connList.add(new InternalClientConnection(authInfo));
    successList.add(false);

    userDN    = "cn=Privileged User,o=test";
    userEntry = DirectoryServer.getEntry(DN.decode(userDN));
    authInfo  = new AuthenticationInfo(userEntry, false);
    connList.add(new InternalClientConnection(authInfo));
    successList.add(true);


    connections = new InternalClientConnection[connList.size()];
    successful  = new boolean[connections.length];
    for (int i=0; i < connections.length; i++)
    {
      connections[i] = connList.get(i);
      successful[i]  = successList.get(i);
    }

    TestCaseUtils.addEntries(
        "dn: dc=unindexed,dc=jeb",
        "objectClass: top",
        "objectClass: domain",
        "",
        "dn: cn=test1 user,dc=unindexed,dc=jeb",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "cn: test1 user",
        "givenName: user",
        "sn: test1",
        "",
        "dn: cn=test2 user,dc=unindexed,dc=jeb",
        "objectClass: top",
        "objectClass: person",
        "objectClass: organizationalPerson",
        "objectClass: inetOrgPerson",
        "cn: test2 user",
        "givenName: user",
        "sn: test2"
    );
  }



  /**
   * Cleans up anything that might be left around after running the tests in
   * this class.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @AfterClass()
  public void cleanUp()
         throws Exception
  {
    InternalClientConnection conn =
         InternalClientConnection.getRootConnection();

    DeleteOperation deleteOperation =
         conn.processDelete(
              DN.decode("cn=Unprivileged Root,cn=Root DNs,cn=config"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    deleteOperation =
      conn.processDelete(
           DN.decode("cn=Proxy Root,cn=Root DNs,cn=config"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    deleteOperation =
      conn.processDelete(
           DN.decode("cn=Privileged User,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    deleteOperation =
      conn.processDelete(
           DN.decode("cn=UnPrivileged User,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    deleteOperation =
      conn.processDelete(
           DN.decode("cn=PWReset Target,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    deleteOperation = conn.processDelete(DN
        .decode("cn=test1 user,dc=unindexed,dc=jeb"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    deleteOperation = conn.processDelete(DN
        .decode("cn=test2 user,dc=unindexed,dc=jeb"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);

    deleteOperation = conn.processDelete(DN
        .decode("dc=unindexed,dc=jeb"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Retrieves a set of data that can be used for performing the tests.  The
   * arguments generated for each method will be:
   * <OL>
   *   <LI>A client connection to use to perform the operation</LI>
   *   <LI>A flag indicating whether or not the operation should succeed</LI>
   * </OL>
   *
   * @return  A set of data that can be used for performing the tests.
   */
  @DataProvider(name = "testdata")
  public Object[][] getTestData()
  {
    Object[][] returnArray = new Object[connections.length][2];
    for (int i=0; i < connections.length; i++)
    {
      returnArray[i][0] = connections[i];
      returnArray[i][1] = successful[i];
    }

    return returnArray;
  }



  /**
   * Tests to ensure that search operations in the server configuration properly
   * respect the CONFIG_READ privilege.
   *
   * @param  conn          The client connection to use to perform the search
   *                       operation.
   * @param  hasPrivilege  Indicates whether the authenticated user is expected
   *                       to have the CONFIG_READ privilege and therefore the
   *                       search should succeed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testConfigReadSearch(InternalClientConnection conn,
                                   boolean hasPrivilege)
         throws Exception
  {
    assertEquals(conn.hasPrivilege(Privilege.CONFIG_READ, null), hasPrivilege);

    InternalSearchOperation searchOperation =
         conn.processSearch(DN.decode("cn=config"), SearchScope.BASE_OBJECT,
              SearchFilter.createFilterFromString("(objectClass=*)"));
    if (hasPrivilege)
    {
      assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    }
    else
    {
      assertEquals(searchOperation.getResultCode(),
                   ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    }
  }

  /**
   * Tests to ensure that unindexed search operations properly respect the
   * UNINDEXED_SEARCH privilege.
   *
   * @param conn The client connection to use to perform the search operation.
   *
   * @param hasPrivilege Indicates whether the authenticated user is expected
   *                     to have the UNINDEXED_SEARCH privilege and therefore
   *                     the search should succeed.
   * @throws Exception If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testUnindexedSearch(InternalClientConnection conn,
                                  boolean hasPrivilege)
         throws Exception
  {
    assertEquals(conn.hasPrivilege(Privilege.UNINDEXED_SEARCH, null), hasPrivilege);

    for(DN dn : DirectoryServer.getBaseDNs().keySet())
    {
      System.out.println(dn.toString());
    }

    InternalSearchOperation searchOperation =
        conn.processSearch(DN.decode("dc=unindexed,dc=jeb"), SearchScope.WHOLE_SUBTREE,
             SearchFilter.createFilterFromString("(sn=test*)"));
    if (hasPrivilege)
    {
      assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    }
    else
    {
      assertEquals(searchOperation.getResultCode(),
                   ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    }
  }



  /**
   * Tests to ensure that compare operations in the server configuration
   * properly respect the CONFIG_READ privilege.
   *
   * @param  conn          The client connection to use to perform the compare
   *                       operation.
   * @param  hasPrivilege  Indicates whether the authenticated user is expected
   *                       to have the CONFIG_READ privilege and therefore the
   *                       compare should succeed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testConfigReadCompare(InternalClientConnection conn,
                                    boolean hasPrivilege)
         throws Exception
  {
    assertEquals(conn.hasPrivilege(Privilege.CONFIG_READ, null), hasPrivilege);

    CompareOperation compareOperation =
         conn.processCompare(DN.decode("cn=config"),
                             DirectoryServer.getAttributeType("cn"),
                             ByteStringFactory.create("config"));
    if (hasPrivilege)
    {
      assertEquals(compareOperation.getResultCode(), ResultCode.COMPARE_TRUE);
    }
    else
    {
      assertEquals(compareOperation.getResultCode(),
                   ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    }
  }



  /**
   * Tests to ensure that add and delete operations in the server configuration
   * properly respect the CONFIG_WRITE privilege.
   *
   * @param  conn          The client connection to use to perform the
   *                       operations.
   * @param  hasPrivilege  Indicates whether the authenticated user is expected
   *                       to have the CONFIG_WRITE privilege and therefore the
   *                       operations should succeed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testConfigWriteAddAndDelete(InternalClientConnection conn,
                                          boolean hasPrivilege)
         throws Exception
  {
    assertEquals(conn.hasPrivilege(Privilege.CONFIG_WRITE, null), hasPrivilege);

    Entry entry = TestCaseUtils.makeEntry(
      "dn: cn=Test Root,cn=Root DNs,cn=config",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "objectClass: ds-cfg-root-dn",
      "cn: Test Root",
      "givenName: Test",
      "sn: Root",
      "userPassword: password");

    AddOperation addOperation =
         conn.processAdd(entry.getDN(), entry.getObjectClasses(),
                         entry.getUserAttributes(),
                         entry.getOperationalAttributes());
    if (hasPrivilege)
    {
      assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

      DeleteOperation deleteOperation = conn.processDelete(entry.getDN());
      assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    }
    else
    {
      assertEquals(addOperation.getResultCode(),
                   ResultCode.INSUFFICIENT_ACCESS_RIGHTS);

      DeleteOperation deleteOperation =
           conn.processDelete(
                DN.decode("cn=Telex Number,cn=Syntaxes,cn=config"));
      assertEquals(deleteOperation.getResultCode(),
                   ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    }
  }



  /**
   * Tests to ensure that modify operations in the server configuration
   * properly respect the CONFIG_WRITE privilege.
   *
   * @param  conn          The client connection to use to perform the modify
   *                       operation.
   * @param  hasPrivilege  Indicates whether the authenticated user is expected
   *                       to have the CONFIG_WRITE privilege and therefore the
   *                       modify should succeed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testConfigWriteModify(InternalClientConnection conn,
                                    boolean hasPrivilege)
         throws Exception
  {
    assertEquals(conn.hasPrivilege(Privilege.CONFIG_WRITE, null), hasPrivilege);

    ArrayList<Modification> mods = new ArrayList<Modification>();

    mods.add(new Modification(ModificationType.REPLACE,
                              new Attribute("ds-cfg-size-limit", "2000")));

    ModifyOperation modifyOperation =
         conn.processModify(DN.decode("cn=config"), mods);
    if (hasPrivilege)
    {
      assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

      mods.clear();
      mods.add(new Modification(ModificationType.REPLACE,
                              new Attribute("ds-cfg-size-limit", "1000")));

      modifyOperation = conn.processModify(DN.decode("cn=config"), mods);
      assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    }
    else
    {
      assertEquals(modifyOperation.getResultCode(),
                   ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    }
  }



  /**
   * Tests to ensure that modify DN operations in the server configuration
   * properly respect the CONFIG_WRITE privilege.
   *
   * @param  conn          The client connection to use to perform the modify DN
   *                       operation.
   * @param  hasPrivilege  Indicates whether the authenticated user is expected
   *                       to have the CONFIG_WRITE privilege and therefore the
   *                       modify DN should succeed (or at least get past the
   *                       privilege check, only to fail because we don't
   *                       support modify DN in the server configuration).
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testConfigWriteModifyDN(InternalClientConnection conn,
                                      boolean hasPrivilege)
         throws Exception
  {
    assertEquals(conn.hasPrivilege(Privilege.CONFIG_WRITE, null), hasPrivilege);

    ModifyDNOperation modifyDNOperation =
         conn.processModifyDN(DN.decode("cn=Work Queue,cn=config"),
                              RDN.decode("cn=New RDN for Work Queue"), true,
                              null);
    if (hasPrivilege)
    {
      // We don't support modify DN operations in the server configuration, but
      // at least we need to make sure we're getting past the privilege check.
      assertEquals(modifyDNOperation.getResultCode(),
                   ResultCode.UNWILLING_TO_PERFORM);
    }
    else
    {
      assertEquals(modifyDNOperation.getResultCode(),
                   ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    }
  }



  /**
   * Tests to ensure that modify operations which attempt to reset a user's
   * password properly respect the PASSWORD_RESET privilege.
   *
   * @param  conn          The client connection to use to perform the password
   *                       reset.
   * @param  hasPrivilege  Indicates whether the authenticated user is expected
   *                       to have the PASSWORD_RESET privilege and therefore
   *                       the password reset should succeed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testPasswordResetModify(InternalClientConnection conn,
                                      boolean hasPrivilege)
         throws Exception
  {
    // We've got to do this as an external operation rather than internal, so
    // get the bind DN and password to use from the client connection.

    String userDN;
    String userPassword;
    Entry  authNEntry = conn.getAuthenticationInfo().getAuthenticationEntry();
    if (authNEntry == null)
    {
      userDN       = "";
      userPassword = "";
    }
    else if (authNEntry.getDN().toString().equalsIgnoreCase(INTERNAL_ROOT_DN))
    {
      return;
    }
    else
    {
      userDN       = authNEntry.getDN().toString();
      userPassword = "password";
    }

    assertEquals(conn.hasPrivilege(Privilege.PASSWORD_RESET, null),
                 hasPrivilege);

    String path = TestCaseUtils.createTempFile(
      "dn: cn=PWReset Target,o=test",
      "changetype: modify",
      "replace: userPassword",
      "userPassword: newpassword",
      "",
      "dn: cn=PWReset Target,o=test",
      "changetype: modify",
      "replace: userPassword",
      "userPassword: password");

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", userDN,
      "-w", userPassword,
      "-f", path
    };

    int resultCode = LDAPModify.mainModify(args, false, null, null);
    if (hasPrivilege)
    {
      assertEquals(resultCode, 0);
    }
    else
    {
      assertEquals(resultCode, 50);
    }
  }



  /**
   * Tests to ensure that password modify extended operations which attempt to
   * reset a user's password properly respect the PASSWORD_RESET privilege.
   *
   * @param  conn          The client connection to use to perform the password
   *                       reset.
   * @param  hasPrivilege  Indicates whether the authenticated user is expected
   *                       to have the PASSWORD_RESET privilege and therefore
   *                       the password reset should succeed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testPasswordResetExtOp(InternalClientConnection conn,
                                     boolean hasPrivilege)
         throws Exception
  {
    // We've got to do this as an external operation rather than internal, so
    // get the bind DN and password to use from the client connection.

    String userDN;
    String userPassword;
    Entry  authNEntry = conn.getAuthenticationInfo().getAuthenticationEntry();
    if (authNEntry == null)
    {
      userDN       = "";
      userPassword = "";
    }
    else if (authNEntry.getDN().toString().equalsIgnoreCase(INTERNAL_ROOT_DN))
    {
      return;
    }
    else
    {
      userDN       = authNEntry.getDN().toString();
      userPassword = "password";
    }

    assertEquals(conn.hasPrivilege(Privilege.PASSWORD_RESET, null),
                 hasPrivilege);

    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-D", userDN,
      "-w", userPassword,
      "-a", "dn:cn=PWReset Target,o=test",
      "-n", "newpassword"
    };

    int resultCode =
             LDAPPasswordModify.mainPasswordModify(args, false, null, null);
    if (hasPrivilege)
    {
      assertEquals(resultCode, 0);

      args = new String[]
      {
        "-h", "127.0.0.1",
        "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
        "-D", userDN,
        "-w", userPassword,
        "-a", "dn:cn=PWReset Target,o=test",
        "-n", "password"
      };
      assertEquals(LDAPPasswordModify.mainPasswordModify(args, false, null,
                                                         null), 0);
    }
    else
    {
      assertEquals(resultCode, 50);
    }
  }



  /**
   * Tests to ensure that attempts to update the schema with a modify operation
   * will properly respect the UPDATE_SCHEMA privilege.
   *
   * @param  conn          The client connection to use to perform the schema
   *                       update.
   * @param  hasPrivilege  Indicates whether the authenticated user is expected
   *                       to have the UPDATE_SCHEMA privilege and therefore
   *                       the schema update should succeed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testUpdateSchemaModify(InternalClientConnection conn,
                               boolean hasPrivilege)
         throws Exception
  {
    assertEquals(conn.hasPrivilege(Privilege.UPDATE_SCHEMA, null),
                 hasPrivilege);

    String attrDefinition =
         "( testupdateschemaat-oid NAME 'testUpdateSchemaAT' " +
         "SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE " +
         "X-ORIGIN 'PrivilegeTestCase' )";

    ArrayList<Modification> mods = new ArrayList<Modification>();

    mods.add(new Modification(ModificationType.ADD,
                              new Attribute("attributetypes", attrDefinition)));

    ModifyOperation modifyOperation =
         conn.processModify(DN.decode("cn=schema"), mods);
    if (hasPrivilege)
    {
      assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);

      mods.clear();
      mods.add(new Modification(ModificationType.DELETE,
                        new Attribute("attributetypes", attrDefinition)));

      modifyOperation = conn.processModify(DN.decode("cn=schema"), mods);
      assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    }
    else
    {
      assertEquals(modifyOperation.getResultCode(),
                   ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    }
  }



  /**
   * Tests to ensure that attempts to update the schema with an add schema file
   * task will properly respect the UPDATE_SCHEMA privilege.
   *
   * @param  conn          The client connection to use to perform the schema
   *                       update.
   * @param  hasPrivilege  Indicates whether the authenticated user is expected
   *                       to have the UPDATE_SCHEMA privilege and therefore
   *                       the schema update should succeed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testUpdateSchemaAddSchemaFile(InternalClientConnection conn,
                                            boolean hasPrivilege)
         throws Exception
  {
    assertEquals(conn.hasPrivilege(Privilege.UPDATE_SCHEMA, null),
                 hasPrivilege);


    String schemaDirectory = SchemaConfigManager.getSchemaDirectoryPath();

    String identifier;
    Entry authNEntry = conn.getAuthenticationInfo().getAuthenticationEntry();
    if (authNEntry == null)
    {
      identifier = "null";
    }
    else
    {
      identifier = authNEntry.getDN().toString();
      identifier = identifier.replace(',', '-');
      identifier = identifier.replace(' ', '-');
      identifier = identifier.replace('=', '-');
    }

    String[] fileLines =
    {
      "dn: cn=schema",
      "objectClass: top",
      "objectClass: ldapSubentry",
      "objectClass: subschema",
      "attributeTypes: ( " + identifier.toLowerCase() + "-oid " +
           "NAME '" + identifier + "' )"
    };

    File validFile = new File(schemaDirectory, "05-" + identifier + ".ldif");
    BufferedWriter writer = new BufferedWriter(new FileWriter(validFile));
    for (String line : fileLines)
    {
      writer.write(line);
      writer.newLine();
    }
    writer.close();

    Entry taskEntry = TestCaseUtils.makeEntry(
      "dn: ds-task-id=" + UUID.randomUUID() + ",cn=Scheduled Tasks,cn=Tasks",
      "objectClass: top",
      "objectClass: ds-task",
      "objectClass: ds-task-add-schema-file",
      "ds-task-class-name: org.opends.server.tasks.AddSchemaFileTask",
      "ds-task-schema-file-name: 05-" + identifier + ".ldif");

    AddOperation addOperation =
         conn.processAdd(taskEntry.getDN(), taskEntry.getObjectClasses(),
                         taskEntry.getUserAttributes(),
                         taskEntry.getOperationalAttributes());

    if (hasPrivilege)
    {
      assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

      Task task = getCompletedTask(taskEntry.getDN());
      assertNotNull(task);
      assertTrue(TaskState.isSuccessful(task.getTaskState()));
    }
    else
    {
      assertEquals(addOperation.getResultCode(),
                   ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    }
  }



  /**
   * Tests to ensure that attempts to backup the Directory Server backends
   * will properly respect the BACKEND_BACKUP privilege.
   *
   * @param  conn          The client connection to use to perform the backup.
   * @param  hasPrivilege  Indicates whether the authenticated user is expected
   *                       to have the BACKEND_BACKUP privilege and therefore
   *                       the backup should succeed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata", groups = { "slow" })
  public void testBackupBackend(InternalClientConnection conn,
                                boolean hasPrivilege)
         throws Exception
  {
    // We have to sleep here because the backup ID that gets generated will be
    // based on a timestamp and we don't want two in the same second.
    Thread.sleep(1100);

    assertEquals(conn.hasPrivilege(Privilege.BACKEND_BACKUP, null),
                 hasPrivilege);

    Entry taskEntry = TestCaseUtils.makeEntry(
      "dn: ds-task-id=" + UUID.randomUUID() + ",cn=Scheduled Tasks,cn=Tasks",
      "objectclass: top",
      "objectclass: ds-task",
      "objectclass: ds-task-backup",
      "ds-task-class-name: org.opends.server.tasks.BackupTask",
      "ds-backup-directory-path: bak",
      "ds-task-backup-all: TRUE");

    AddOperation addOperation =
         conn.processAdd(taskEntry.getDN(), taskEntry.getObjectClasses(),
                         taskEntry.getUserAttributes(),
                         taskEntry.getOperationalAttributes());

    if (hasPrivilege)
    {
      assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

      Task task = getCompletedTask(taskEntry.getDN());
      assertNotNull(task);
      assertTrue(TaskState.isSuccessful(task.getTaskState()));
    }
    else
    {
      assertEquals(addOperation.getResultCode(),
                   ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    }
  }



  /**
   * Tests to ensure that attempts to restore the Directory Server backends
   * will properly respect the BACKEND_RESTORE privilege.
   *
   * @param  conn          The client connection to use to perform the restore.
   * @param  hasPrivilege  Indicates whether the authenticated user is expected
   *                       to have the BACKEND_RESTORE privilege and therefore
   *                       the restore should succeed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(enabled = false, dataProvider = "testdata", groups = { "slow" },
        dependsOnMethods = { "testBackupBackend" })
  public void testRestoreBackend(InternalClientConnection conn,
                                 boolean hasPrivilege)
         throws Exception
  {
    assertEquals(conn.hasPrivilege(Privilege.BACKEND_RESTORE, null),
                 hasPrivilege);

    Entry taskEntry = TestCaseUtils.makeEntry(
      "dn: ds-task-id=" + UUID.randomUUID() + ",cn=Scheduled Tasks,cn=Tasks",
      "objectclass: top",
      "objectclass: ds-task",
      "objectclass: ds-task-restore",
      "ds-task-class-name: org.opends.server.tasks.RestoreTask",
      "ds-backup-directory-path: bak" + File.separator + "userRoot");

    AddOperation addOperation =
         conn.processAdd(taskEntry.getDN(), taskEntry.getObjectClasses(),
                         taskEntry.getUserAttributes(),
                         taskEntry.getOperationalAttributes());

    if (hasPrivilege)
    {
      assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

      Task task = getCompletedTask(taskEntry.getDN());
      assertNotNull(task);
      assertTrue(TaskState.isSuccessful(task.getTaskState()));
    }
    else
    {
      assertEquals(addOperation.getResultCode(),
                   ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    }
  }



  /**
   * Tests to ensure that attempts to export the contents of a Directory Server
   * backend will properly respect the LDIF_EXPORT privilege.
   *
   * @param  conn          The client connection to use to perform the export.
   * @param  hasPrivilege  Indicates whether the authenticated user is expected
   *                       to have the LDIF_EXPORT privilege and therefore
   *                       the export should succeed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata", groups = { "slow" })
  public void testLDIFExport(InternalClientConnection conn,
                             boolean hasPrivilege)
         throws Exception
  {
    assertEquals(conn.hasPrivilege(Privilege.LDIF_EXPORT, null), hasPrivilege);

    File   tempFile     = File.createTempFile("export-", ".ldif");
    String tempFilePath = tempFile.getAbsolutePath();
    tempFile.delete();

    Entry taskEntry = TestCaseUtils.makeEntry(
      "dn: ds-task-id=" + UUID.randomUUID() + ",cn=Scheduled Tasks,cn=Tasks",
      "objectclass: top",
      "objectclass: ds-task",
      "objectclass: ds-task-export",
      "ds-task-class-name: org.opends.server.tasks.ExportTask",
      "ds-task-export-backend-id: userRoot",
      "ds-task-export-ldif-file: " + tempFilePath);

    AddOperation addOperation =
         conn.processAdd(taskEntry.getDN(), taskEntry.getObjectClasses(),
                         taskEntry.getUserAttributes(),
                         taskEntry.getOperationalAttributes());

    if (hasPrivilege)
    {
      assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

      Task task = getCompletedTask(taskEntry.getDN());
      assertNotNull(task);
      assertTrue(TaskState.isSuccessful(task.getTaskState()));

      tempFile.delete();
    }
    else
    {
      assertEquals(addOperation.getResultCode(),
                   ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    }
  }



  /**
   * Tests to ensure that attempts to import into a Directory Server backend
   * will properly respect the LDIF_IMPORT privilege.
   *
   * @param  conn          The client connection to use to perform the import.
   * @param  hasPrivilege  Indicates whether the authenticated user is expected
   *                       to have the LDIF_IMPORT privilege and therefore
   *                       the import should succeed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata", groups = { "slow" })
  public void testLDIFImport(InternalClientConnection conn,
                             boolean hasPrivilege)
         throws Exception
  {
    assertEquals(conn.hasPrivilege(Privilege.LDIF_IMPORT, null), hasPrivilege);

    String path = TestCaseUtils.createTempFile(
      "dn: dc=example,dc=com",
      "objectClass: top",
      "objectClass: domain",
      "dc: example");

    Entry taskEntry = TestCaseUtils.makeEntry(
      "dn: ds-task-id=" + UUID.randomUUID() + ",cn=Scheduled Tasks,cn=Tasks",
      "objectclass: top",
      "objectclass: ds-task",
      "objectclass: ds-task-import",
      "ds-task-class-name: org.opends.server.tasks.ImportTask",
      "ds-task-import-backend-id: userRoot",
      "ds-task-import-ldif-file: " + path);

    AddOperation addOperation =
         conn.processAdd(taskEntry.getDN(), taskEntry.getObjectClasses(),
                         taskEntry.getUserAttributes(),
                         taskEntry.getOperationalAttributes());

    if (hasPrivilege)
    {
      assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

      Task task = getCompletedTask(taskEntry.getDN());
      assertNotNull(task);
      assertTrue(TaskState.isSuccessful(task.getTaskState()));
    }
    else
    {
      assertEquals(addOperation.getResultCode(),
                   ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    }
  }

  /**
   * Test to ensure that attempts to rebuild indexes will property respect
   * the LDIF_IMPORT privilege.
   *
   * @param conn The client connection to use to perform the rebuild.
   * @param hasPrivilege Indicates weather the authenticated user is
   *                     expected to have the INDEX_REBUILD privilege
   *                     and therefore the rebuild should succeed.
   * @throws Exception if an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata", groups = { "slow" })
  public void testRebuildIndex(InternalClientConnection conn,
                               boolean hasPrivilege)
      throws Exception
  {
    assertEquals(conn.hasPrivilege(Privilege.LDIF_IMPORT, null), hasPrivilege);

    Entry taskEntry = TestCaseUtils.makeEntry(
      "dn: ds-task-id=" + UUID.randomUUID() + ",cn=Scheduled Tasks,cn=Tasks",
      "objectclass: top",
      "objectclass: ds-task",
      "objectclass: ds-task-rebuild",
      "ds-task-class-name: org.opends.server.tasks.RebuildTask",
      "ds-task-rebuild-base-dn: dc=example,dc=com",
      "ds-task-rebuild-index: cn");

    AddOperation addOperation =
         conn.processAdd(taskEntry.getDN(), taskEntry.getObjectClasses(),
                         taskEntry.getUserAttributes(),
                         taskEntry.getOperationalAttributes());

    if (hasPrivilege)
    {
      assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);

      Task task = getCompletedTask(taskEntry.getDN());
      assertNotNull(task);
      assertTrue(TaskState.isSuccessful(task.getTaskState()));
    }
    else
    {
      assertEquals(addOperation.getResultCode(),
                   ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    }
  }



  /**
   * Tests to ensure that the use of the Directory Server will properly respect
   * the PROXIED_AUTH privilege for add, delete, modify and modify DN requests
   * that contain the proxied auth v1 control.
   *
   * @param  conn          The client connection to use to perform the
   *                       operation.
   * @param  hasPrivilege  Indicates whether the authenticated user is expected
   *                       to have the PROXIED_AUTH privilege and therefore
   *                       the operation should succeed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testProxyAuthV1Write(InternalClientConnection conn,
                                   boolean hasPrivilege)
         throws Exception
  {
    // We can't trust the value of hasPrivilege because root users don't get
    // proxy privileges by default.  So make the determination based on the
    // privileges the user actually has.
    boolean hasProxyPrivilege = conn.hasPrivilege(Privilege.PROXIED_AUTH, null);

    Entry e = TestCaseUtils.makeEntry(
      "dn: cn=ProxyV1 Test,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "cn: ProxyV1 Test",
      "givenName: ProxyV1",
      "sn: Test");

    ArrayList<Control> controls = new ArrayList<Control>(1);
    controls.add(new ProxiedAuthV1Control(
                          DN.decode("cn=PWReset Target,o=test")));


    // Try to add the entry.  If this fails with the proxy control, then add it
    // with a root connection so we can do other things with it.
    AddOperationBasis addOperation =
         new AddOperationBasis(conn, conn.nextOperationID(), conn.nextMessageID(),
                          controls, e.getDN(), e.getObjectClasses(),
                          e.getUserAttributes(), e.getOperationalAttributes());
    addOperation.run();

    if (hasProxyPrivilege)
    {
      assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
    }
    else
    {
      assertEquals(addOperation.getResultCode(),
                   ResultCode.AUTHORIZATION_DENIED);
      TestCaseUtils.addEntry(e);
    }


    // Try to modify the entry to add a description.
    ArrayList<Modification> mods = new ArrayList<Modification>(1);
    mods.add(new Modification(ModificationType.REPLACE,
                              new Attribute("description", "foo")));

    ModifyOperationBasis modifyOperation =
         new ModifyOperationBasis(conn, conn.nextOperationID(), conn.nextMessageID(),
                             controls, e.getDN(), mods);
    modifyOperation.run();

    if (hasProxyPrivilege)
    {
      assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    }
    else
    {
      assertEquals(modifyOperation.getResultCode(),
                   ResultCode.AUTHORIZATION_DENIED);
    }


    // Try to rename the entry.
    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(),
                               conn.nextMessageID(), controls, e.getDN(),
                               RDN.decode("cn=Proxy V1 Test"), true, null);
    modifyDNOperation.run();

    DN newEntryDN;
    if (hasProxyPrivilege)
    {
      assertEquals(modifyDNOperation.getResultCode(), ResultCode.SUCCESS);
      newEntryDN = modifyDNOperation.getUpdatedEntry().getDN();
    }
    else
    {
      assertEquals(modifyDNOperation.getResultCode(),
                   ResultCode.AUTHORIZATION_DENIED);
      newEntryDN = e.getDN();
    }


    // Try to delete the operation.  If this fails, then delete it with a root
    // connection so it gets cleaned up.
    DeleteOperationBasis deleteOperation =
         new DeleteOperationBasis(conn, conn.nextOperationID(), conn.nextMessageID(),
                             controls, newEntryDN);
    deleteOperation.run();

    if (hasProxyPrivilege)
    {
      assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
    }
    else
    {
      assertEquals(deleteOperation.getResultCode(),
                   ResultCode.AUTHORIZATION_DENIED);

      InternalClientConnection rootConnection =
           InternalClientConnection.getRootConnection();
      DeleteOperation delOp = rootConnection.processDelete(newEntryDN);
      assertEquals(delOp.getResultCode(), ResultCode.SUCCESS);
    }
  }



  /**
   * Tests to ensure that the use of the Directory Server will properly respect
   * the PROXIED_AUTH privilege for search and compare requests that contain the
   * proxied auth v1 control.
   *
   * @param  conn          The client connection to use to perform the
   *                       operation.
   * @param  hasPrivilege  Indicates whether the authenticated user is expected
   *                       to have the PROXIED_AUTH privilege and therefore
   *                       the operation should succeed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testProxyAuthV1Read(InternalClientConnection conn,
                                  boolean hasPrivilege)
         throws Exception
  {
    // We can't trust the value of hasPrivilege because root users don't get
    // proxy privileges by default.  So make the determination based on the
    // privileges the user actually has.
    boolean hasProxyPrivilege = conn.hasPrivilege(Privilege.PROXIED_AUTH, null);

    DN targetDN = DN.decode("cn=PWReset Target,o=test");
    ArrayList<Control> controls = new ArrayList<Control>(1);
    controls.add(new ProxiedAuthV1Control(targetDN));


    // Test a compare operation against the PWReset Target user.
    CompareOperationBasis compareOperation =
         new CompareOperationBasis(conn, conn.nextOperationID(),
                              conn.nextMessageID(), controls, targetDN,
                              DirectoryServer.getAttributeType("cn", true),
                              ByteStringFactory.create("PWReset Target"));
    compareOperation.run();

    if (hasProxyPrivilege)
    {
      assertEquals(compareOperation.getResultCode(), ResultCode.COMPARE_TRUE);
    }
    else
    {
      assertEquals(compareOperation.getResultCode(),
                   ResultCode.AUTHORIZATION_DENIED);
    }


    // Test a search operation against the PWReset Target user.
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                  conn.nextMessageID(), controls, targetDN,
                  SearchScope.BASE_OBJECT,
                  DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                  SearchFilter.createFilterFromString("(objectClass=*)"), null,
                  null);
    searchOperation.run();

    if (hasProxyPrivilege)
    {
      assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    }
    else
    {
      assertEquals(searchOperation.getResultCode(),
                   ResultCode.AUTHORIZATION_DENIED);
    }
  }



  /**
   * Tests to ensure that the use of the Directory Server will properly respect
   * the PROXIED_AUTH privilege for add, delete, modify and modify DN requests
   * that contain the proxied auth v2 control.
   *
   * @param  conn          The client connection to use to perform the
   *                       operation.
   * @param  hasPrivilege  Indicates whether the authenticated user is expected
   *                       to have the PROXIED_AUTH privilege and therefore
   *                       the operation should succeed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testProxyAuthV2Write(InternalClientConnection conn,
                                   boolean hasPrivilege)
         throws Exception
  {
    // We can't trust the value of hasPrivilege because root users don't get
    // proxy privileges by default.  So make the determination based on the
    // privileges the user actually has.
    boolean hasProxyPrivilege = conn.hasPrivilege(Privilege.PROXIED_AUTH, null);

    Entry e = TestCaseUtils.makeEntry(
      "dn: cn=ProxyV2 Test,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "cn: ProxyV2 Test",
      "givenName: ProxyV2",
      "sn: Test");

    ArrayList<Control> controls = new ArrayList<Control>(1);
    controls.add(new ProxiedAuthV2Control(
                          new ASN1OctetString("dn:cn=PWReset Target,o=test")));


    // Try to add the entry.  If this fails with the proxy control, then add it
    // with a root connection so we can do other things with it.
    DN authDN = conn.getAuthenticationInfo().getAuthenticationDN();
    AddOperationBasis addOperation =
         new AddOperationBasis(conn, conn.nextOperationID(), conn.nextMessageID(),
                          controls, e.getDN(), e.getObjectClasses(),
                          e.getUserAttributes(), e.getOperationalAttributes());
    addOperation.run();

    if (hasProxyPrivilege)
    {
      assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS,
                   "Unexpected add failure for user " + authDN);
    }
    else
    {
      assertEquals(addOperation.getResultCode(),
                   ResultCode.AUTHORIZATION_DENIED,
                   "Unexpected add success for user " + authDN);
      TestCaseUtils.addEntry(e);
    }


    // Try to modify the entry to add a description.
    ArrayList<Modification> mods = new ArrayList<Modification>(1);
    mods.add(new Modification(ModificationType.REPLACE,
                              new Attribute("description", "foo")));

    ModifyOperationBasis modifyOperation =
         new ModifyOperationBasis(conn, conn.nextOperationID(), conn.nextMessageID(),
                             controls, e.getDN(), mods);
    modifyOperation.run();

    if (hasProxyPrivilege)
    {
      assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS,
                   "Unexpected mod failure for user " + authDN);
    }
    else
    {
      assertEquals(modifyOperation.getResultCode(),
                   ResultCode.AUTHORIZATION_DENIED,
                   "Unexpected mod success for user " + authDN);
    }


    // Try to rename the entry.
    ModifyDNOperation modifyDNOperation =
         new ModifyDNOperation(conn, conn.nextOperationID(),
                               conn.nextMessageID(), controls, e.getDN(),
                               RDN.decode("cn=Proxy V2 Test"), true, null);
    modifyDNOperation.run();

    DN newEntryDN;
    if (hasProxyPrivilege)
    {
      assertEquals(modifyDNOperation.getResultCode(), ResultCode.SUCCESS,
                   "Unexpected moddn failure for user " + authDN);
      newEntryDN = modifyDNOperation.getUpdatedEntry().getDN();
    }
    else
    {
      assertEquals(modifyDNOperation.getResultCode(),
                   ResultCode.AUTHORIZATION_DENIED,
                   "Unexpected moddn success for user " + authDN);
      newEntryDN = e.getDN();
    }


    // Try to delete the operation.  If this fails, then delete it with a root
    // connection so it gets cleaned up.
    DeleteOperationBasis deleteOperation =
         new DeleteOperationBasis(conn, conn.nextOperationID(), conn.nextMessageID(),
                             controls, newEntryDN);
    deleteOperation.run();

    if (hasProxyPrivilege)
    {
      assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS,
                   "Unexpected delete failure for user " + authDN);
    }
    else
    {
      assertEquals(deleteOperation.getResultCode(),
                   ResultCode.AUTHORIZATION_DENIED,
                   "Unexpected delete success for user " + authDN);

      InternalClientConnection rootConnection =
           InternalClientConnection.getRootConnection();
      DeleteOperation delOp = rootConnection.processDelete(newEntryDN);
      assertEquals(delOp.getResultCode(), ResultCode.SUCCESS);
    }
  }



  /**
   * Tests to ensure that the use of the Directory Server will properly respect
   * the PROXIED_AUTH privilege for search and compare requests that contain the
   * proxied auth v2 control.
   *
   * @param  conn          The client connection to use to perform the
   *                       operation.
   * @param  hasPrivilege  Indicates whether the authenticated user is expected
   *                       to have the PROXIED_AUTH privilege and therefore
   *                       the operation should succeed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testProxyAuthV2Read(InternalClientConnection conn,
                                  boolean hasPrivilege)
         throws Exception
  {
    // We can't trust the value of hasPrivilege because root users don't get
    // proxy privileges by default.  So make the determination based on the
    // privileges the user actually has.
    boolean hasProxyPrivilege = conn.hasPrivilege(Privilege.PROXIED_AUTH, null);

    DN targetDN = DN.decode("cn=PWReset Target,o=test");
    ArrayList<Control> controls = new ArrayList<Control>(1);
    controls.add(new ProxiedAuthV2Control(
                          new ASN1OctetString("dn:" + targetDN.toString())));


    // Test a compare operation against the PWReset Target user.
    CompareOperationBasis compareOperation =
         new CompareOperationBasis(conn, conn.nextOperationID(),
                              conn.nextMessageID(), controls, targetDN,
                              DirectoryServer.getAttributeType("cn", true),
                              ByteStringFactory.create("PWReset Target"));
    compareOperation.run();

    if (hasProxyPrivilege)
    {
      assertEquals(compareOperation.getResultCode(), ResultCode.COMPARE_TRUE);
    }
    else
    {
      assertEquals(compareOperation.getResultCode(),
                   ResultCode.AUTHORIZATION_DENIED);
    }


    // Test a search operation against the PWReset Target user.
    InternalSearchOperation searchOperation =
         new InternalSearchOperation(conn, conn.nextOperationID(),
                  conn.nextMessageID(), controls, targetDN,
                  SearchScope.BASE_OBJECT,
                  DereferencePolicy.NEVER_DEREF_ALIASES, 0, 0, false,
                  SearchFilter.createFilterFromString("(objectClass=*)"), null,
                  null);
    searchOperation.run();

    if (hasProxyPrivilege)
    {
      assertEquals(searchOperation.getResultCode(), ResultCode.SUCCESS);
    }
    else
    {
      assertEquals(searchOperation.getResultCode(),
                   ResultCode.AUTHORIZATION_DENIED);
    }
  }



  /**
   * Tests to ensure that the server will properly respect the PROXIED_AUTH
   * privilege when attempting to perform DIGEST-MD5 authentication when an
   * anonymous authorization ID is specified with an authentication identity
   * that has sufficient privileges and using the "dn:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDIGESTMD5AnonymousAuthzIDSuccessfulDNColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=dn:cn=Privileged User,o=test",
      "-o", "authzid=dn:",
      "-o", "realm=o=test",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests to ensure that the server will behave properly when attempting to
   * perform DIGEST-MD5 authentication when an authorization ID equaling the
   * authentication ID is specified with an authentication identity that has
   * sufficient privileges and using the "dn:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDIGESTMD5SameAuthzIDSuccessfulDNColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=dn:cn=Privileged User,o=test",
      "-o", "authzid=dn:cn=Privileged User,o=test",
      "-o", "realm=o=test",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests to ensure that the server will properly respect the PROXIED_AUTH
   * privilege when attempting to perform DIGEST-MD5 authentication when an
   * alternate authorization ID is specified with an authentication identity
   * that has sufficient privileges and using the "dn:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDIGESTMD5DifferentAuthzIDSuccessfulDNColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=dn:cn=Privileged User,o=test",
      "-o", "authzid=dn:cn=Unprivileged User,o=test",
      "-o", "realm=o=test",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests to ensure that the server will properly respect the PROXIED_AUTH
   * privilege when attempting to perform DIGEST-MD5 authentication when an
   * anonymous authorization ID is specified with an authentication identity
   * that has sufficient privileges and using the "u:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDIGESTMD5AnonymousAuthzIDSuccessfulUColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=u:privileged.user",
      "-o", "authzid=u:",
      "-o", "realm=o=test",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests to ensure that the server will behave properly when attempting to
   * perform DIGEST-MD5 authentication when an authorization ID equaling the
   * authentication ID is specified with an authentication identity that has
   * sufficient privileges and using the "u:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDIGESTMD5SameAuthzIDSuccessfulUColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=u:privileged.user",
      "-o", "authzid=u:privileged.user",
      "-o", "realm=o=test",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests to ensure that the server will properly respect the PROXIED_AUTH
   * privilege when attempting to perform DIGEST-MD5 authentication when an
   * alternate authorization ID is specified with an authentication identity
   * that has sufficient privileges and using the "u:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDIGESTMD5DifferentAuthzIDSuccessfulUColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=u:privileged.user",
      "-o", "authzid=u:unprivileged.user",
      "-o", "realm=o=test",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests to ensure that the server will properly respect the PROXIED_AUTH
   * privilege when attempting to perform DIGEST-MD5 authentication when an
   * anonymous authorization ID is specified with an authentication identity
   * that does not have sufficient privileges and using the "dn:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDIGESTMD5AnonymousAuthzIDFailedDNColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=dn:cn=Unprivileged User,o=test",
      "-o", "authzid=dn:",
      "-o", "realm=o=test",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Tests to ensure that the server will behave properly when attempting to
   * perform DIGEST-MD5 authentication when an authorization ID equaling the
   * authentication ID is specified with an authentication identity that does
   * not have sufficient privileges and using the "dn:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDIGESTMD5SameUnprivAuthzIDSuccessfulDNColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=dn:cn=Unprivileged User,o=test",
      "-o", "authzid=dn:cn=Unprivileged User,o=test",
      "-o", "realm=o=test",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests to ensure that the server will properly respect the PROXIED_AUTH
   * privilege when attempting to perform DIGEST-MD5 authentication when an
   * alternate authorization ID is specified with an authentication identity
   * that does not have sufficient privileges and using the "dn:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDIGESTMD5DifferentAuthzIDFailedDNColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=dn:cn=Unprivileged User,o=test",
      "-o", "authzid=dn:cn=Privileged User,o=test",
      "-o", "realm=o=test",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Tests to ensure that the server will properly respect the PROXIED_AUTH
   * privilege when attempting to perform DIGEST-MD5 authentication when an
   * anonymous authorization ID is specified with an authentication identity
   * that does not have sufficient privileges and using the "u:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDIGESTMD5AnonymousAuthzIDFailedUColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=u:unprivileged.user",
      "-o", "authzid=u:",
      "-o", "realm=o=test",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Tests to ensure that the server will behave properly when attempting to
   * perform DIGEST-MD5 authentication when an authorization ID equaling the
   * authentication ID is specified with an authentication identity that does
   * not have sufficient privileges and using the "u:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDIGESTMD5SameUnprivAuthzIDSuccessfulUColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=u:unprivileged.user",
      "-o", "authzid=u:unprivileged.user",
      "-o", "realm=o=test",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests to ensure that the server will properly respect the PROXIED_AUTH
   * privilege when attempting to perform DIGEST-MD5 authentication when an
   * alternate authorization ID is specified with an authentication identity
   * that does not have sufficient privileges and using the "u:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testDIGESTMD5DifferentAuthzIDFailedUColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=DIGEST-MD5",
      "-o", "authid=u:unprivileged.user",
      "-o", "authzid=u:privileged.user",
      "-o", "realm=o=test",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Tests to ensure that the server will properly respect the PROXIED_AUTH
   * privilege when attempting to perform PLAIN authentication when an
   * anonymous authorization ID is specified with an authentication identity
   * that has sufficient privileges and using the "dn:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPLAINAnonymousAuthzIDSuccessfulDNColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=PLAIN",
      "-o", "authid=dn:cn=Privileged User,o=test",
      "-o", "authzid=dn:",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests to ensure that the server will behave properly when attempting to
   * perform PLAIN authentication when an authorization ID equaling the
   * authentication ID is specified with an authentication identity that has
   * sufficient privileges and using the "dn:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPLAINSameAuthzIDSuccessfulDNColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=PLAIN",
      "-o", "authid=dn:cn=Privileged User,o=test",
      "-o", "authzid=dn:cn=Privileged User,o=test",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests to ensure that the server will properly respect the PROXIED_AUTH
   * privilege when attempting to perform PLAIN authentication when an
   * alternate authorization ID is specified with an authentication identity
   * that has sufficient privileges and using the "dn:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPLAINDifferentAuthzIDSuccessfulDNColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=PLAIN",
      "-o", "authid=dn:cn=Privileged User,o=test",
      "-o", "authzid=dn:cn=Unprivileged User,o=test",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests to ensure that the server will properly respect the PROXIED_AUTH
   * privilege when attempting to perform PLAIN authentication when an
   * anonymous authorization ID is specified with an authentication identity
   * that has sufficient privileges and using the "u:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPLAINAnonymousAuthzIDSuccessfulUColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=PLAIN",
      "-o", "authid=u:privileged.user",
      "-o", "authzid=u:",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests to ensure that the server will behave properly when attempting to
   * perform PLAIN authentication when an authorization ID equaling the
   * authentication ID is specified with an authentication identity that has
   * sufficient privileges and using the "u:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPLAINSameAuthzIDSuccessfulUColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=PLAIN",
      "-o", "authid=u:privileged.user",
      "-o", "authzid=u:privileged.user",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests to ensure that the server will properly respect the PROXIED_AUTH
   * privilege when attempting to perform PLAIN authentication when an
   * alternate authorization ID is specified with an authentication identity
   * that has sufficient privileges and using the "u:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPLAINDifferentAuthzIDSuccessfulUColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=PLAIN",
      "-o", "authid=u:privileged.user",
      "-o", "authzid=u:unprivileged.user",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests to ensure that the server will properly respect the PROXIED_AUTH
   * privilege when attempting to perform PLAIN authentication when an
   * anonymous authorization ID is specified with an authentication identity
   * that does not have sufficient privileges and using the "dn:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPLAINAnonymousAuthzIDFailedDNColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=PLAIN",
      "-o", "authid=dn:cn=Unprivileged User,o=test",
      "-o", "authzid=dn:",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Tests to ensure that the server will behave properly when attempting to
   * perform PLAIN authentication when an authorization ID equaling the
   * authentication ID is specified with an authentication identity that does
   * not have sufficient privileges and using the "dn:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPLAINSameUnprivAuthzIDSuccessfulDNColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=PLAIN",
      "-o", "authid=dn:cn=Unprivileged User,o=test",
      "-o", "authzid=dn:cn=Unprivileged User,o=test",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests to ensure that the server will properly respect the PROXIED_AUTH
   * privilege when attempting to perform PLAIN authentication when an
   * alternate authorization ID is specified with an authentication identity
   * that does not have sufficient privileges and using the "dn:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPLAINDifferentAuthzIDFailedDNColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=PLAIN",
      "-o", "authid=dn:cn=Unprivileged User,o=test",
      "-o", "authzid=dn:cn=Privileged User,o=test",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Tests to ensure that the server will properly respect the PROXIED_AUTH
   * privilege when attempting to perform PLAIN authentication when an
   * anonymous authorization ID is specified with an authentication identity
   * that does not have sufficient privileges and using the "u:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPLAINAnonymousAuthzIDFailedUColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=PLAIN",
      "-o", "authid=u:unprivileged.user",
      "-o", "authzid=u:",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Tests to ensure that the server will behave properly when attempting to
   * perform PLAIN authentication when an authorization ID equaling the
   * authentication ID is specified with an authentication identity that does
   * not have sufficient privileges and using the "u:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPLAINSameUnprivAuthzIDSuccessfulUColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=PLAIN",
      "-o", "authid=u:unprivileged.user",
      "-o", "authzid=u:unprivileged.user",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertEquals(LDAPSearch.mainSearch(args, false, null, System.err), 0);
  }



  /**
   * Tests to ensure that the server will properly respect the PROXIED_AUTH
   * privilege when attempting to perform PLAIN authentication when an
   * alternate authorization ID is specified with an authentication identity
   * that does not have sufficient privileges and using the "u:" syntax.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testPLAINDifferentAuthzIDFailedUColon()
         throws Exception
  {
    String[] args =
    {
      "-h", "127.0.0.1",
      "-p", String.valueOf(TestCaseUtils.getServerLdapPort()),
      "-o", "mech=PLAIN",
      "-o", "authid=u:unprivileged.user",
      "-o", "authzid=u:privileged.user",
      "-w", "password",
      "-b", "o=test",
      "-s", "base",
      "(objectClass=*)"
    };

    assertFalse(LDAPSearch.mainSearch(args, false, null, null) == 0);
  }



  /**
   * Tests the ability to update the set of privileges for a user on the fly
   * and have them take effect immediately.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testUpdateUserPrivileges()
         throws Exception
  {
    InternalClientConnection rootConnection =
         InternalClientConnection.getRootConnection();

    TestCaseUtils.addEntry(
      "dn: cn=Test User,o=test",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "cn: Test User",
      "givenName: Test",
      "sn: User",
      "userPassword: password");

    Entry testEntry =
               DirectoryServer.getEntry(DN.decode("cn=Test User,o=test"));
    AuthenticationInfo authInfo = new AuthenticationInfo(testEntry, false);
    InternalClientConnection testConnection =
         new InternalClientConnection(authInfo);


    // Make sure the user starts out without any privileges.
    for (Privilege p : Privilege.values())
    {
      assertFalse(testConnection.hasPrivilege(p, null));
    }


    // Modify the user entry to add the CONFIG_READ privilege and verify that
    // the client connection reflects that.
    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.ADD,
                      new Attribute("ds-privilege-name", "config-read")));
    ModifyOperation modifyOperation =
         rootConnection.processModify(DN.decode("cn=Test User,o=test"), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    assertTrue(testConnection.hasPrivilege(Privilege.CONFIG_READ, null));


    // Take the privilege away from the user and verify that it is recognized
    // immediately.
    mods.clear();
    mods.add(new Modification(ModificationType.DELETE,
                      new Attribute("ds-privilege-name", "config-read")));
    modifyOperation =
         rootConnection.processModify(DN.decode("cn=Test User,o=test"), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
    assertFalse(testConnection.hasPrivilege(Privilege.CONFIG_READ, null));


    DeleteOperation deleteOperation =
         rootConnection.processDelete(DN.decode("cn=Test User,o=test"));
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Tests the ability to update the set of root privileges and have them take
   * effect immediately for new root connections.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test()
  public void testUpdateRootPrivileges()
         throws Exception
  {
    // Make sure that a root connection doesn't  have the proxied auth
    // privilege.
    DN unprivRootDN = DN.decode("cn=Unprivileged Root,cn=Root DNs,cn=config");
    Entry unprivRootEntry = DirectoryServer.getEntry(unprivRootDN);
    AuthenticationInfo authInfo = new AuthenticationInfo(unprivRootEntry, true);
    InternalClientConnection unprivRootConn =
         new InternalClientConnection(authInfo);
    assertFalse(unprivRootConn.hasPrivilege(Privilege.PROXIED_AUTH, null));


    // Update the set of root privileges to include proxied auth.
    InternalClientConnection internalRootConn =
         InternalClientConnection.getRootConnection();

    ArrayList<Modification> mods = new ArrayList<Modification>();
    mods.add(new Modification(ModificationType.ADD,
                      new Attribute("ds-cfg-default-root-privilege-name",
                                    "proxied-auth")));
    ModifyOperation modifyOperation =
         internalRootConn.processModify(DN.decode("cn=Root DNs,cn=config"),
                                        mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    // Get a new root connection and verify that it now has proxied auth.
    unprivRootEntry = DirectoryServer.getEntry(unprivRootDN);
    authInfo = new AuthenticationInfo(unprivRootEntry, true);
    unprivRootConn = new InternalClientConnection(authInfo);
    assertTrue(unprivRootConn.hasPrivilege(Privilege.PROXIED_AUTH, null));


    // Update the set of root privileges to revoke proxied auth.
    mods.clear();
    mods.add(new Modification(ModificationType.DELETE,
                      new Attribute("ds-cfg-default-root-privilege-name",
                                    "proxied-auth")));
    modifyOperation =
         internalRootConn.processModify(DN.decode("cn=Root DNs,cn=config"),
                                        mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);


    // Get a new root connection and verify that it no longer has proxied auth.
    unprivRootEntry = DirectoryServer.getEntry(unprivRootDN);
    authInfo = new AuthenticationInfo(unprivRootEntry, true);
    unprivRootConn = new InternalClientConnection(authInfo);
    assertFalse(unprivRootConn.hasPrivilege(Privilege.PROXIED_AUTH, null));
  }



  /**
   * Retrieves the specified task from the server, waiting for it to finish all
   * the running its going to do before returning.
   *
   * @param  taskEntryDN  The DN of the entry for the task to retrieve.
   *
   * @return  The requested task entry.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  private Task getCompletedTask(DN taskEntryDN)
          throws Exception
  {
    TaskBackend taskBackend =
         (TaskBackend) DirectoryServer.getBackend(DN.decode("cn=tasks"));
    Task task = taskBackend.getScheduledTask(taskEntryDN);
    if (task == null)
    {
      long stopWaitingTime = System.currentTimeMillis() + 10000L;
      while ((task == null) && (System.currentTimeMillis() < stopWaitingTime))
      {
        Thread.sleep(10);
        task = taskBackend.getScheduledTask(taskEntryDN);
      }
    }

    if (task == null)
    {
      throw new AssertionError("There is no such task " +
                               taskEntryDN.toString());
    }

    if (! TaskState.isDone(task.getTaskState()))
    {
      long stopWaitingTime = System.currentTimeMillis() + 20000L;
      while ((! TaskState.isDone(task.getTaskState())) &&
             (System.currentTimeMillis() < stopWaitingTime))
      {
        Thread.sleep(10);
      }
    }

    if (! TaskState.isDone(task.getTaskState()))
    {
      throw new AssertionError("Task " + taskEntryDN.toString() +
                               " did not complete in a timely manner.");
    }

    return task;
  }
}

