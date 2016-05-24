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
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.server.protocols.jmx;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.AttributeDescription;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.RDN;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.requests.Requests;
import org.opends.server.TestCaseUtils;
import org.opends.server.controls.ProxiedAuthV1Control;
import org.opends.server.controls.ProxiedAuthV2Control;
import org.opends.server.core.AddOperationBasis;
import org.opends.server.core.CompareOperationBasis;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DeleteOperationBasis;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyDNOperationBasis;
import org.opends.server.core.ModifyOperation;
import org.opends.server.core.ModifyOperationBasis;
import org.opends.server.core.SchemaConfigManager;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.types.Attributes;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.Control;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.opends.server.types.Operation;
import org.opends.server.types.Privilege;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.forgerock.opendj.ldap.ModificationType.*;
import static org.opends.messages.ProtocolMessages.*;
import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.protocols.internal.Requests.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.testng.Assert.*;

/** This class provides a set of test cases for the Directory Server JMX privilege subsystem. */
public class JmxPrivilegeTestCase extends JmxTestCase
{
  /**
   * An array of boolean values that indicates whether config read operations
   * should be successful for users in the corresponding slots of the connections array.
   */
  private boolean[] successful;

  /** The set of client connections that should be used when performing operations. */
  private JmxClientConnection[] connections;

  /**
   * Make sure that the server is running and that an appropriate set of
   * structures are in place.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @BeforeClass(alwaysRun = true)
  @Override
  public void setUp() throws Exception
  {
    super.setUp();

    TestCaseUtils.enableBackend("unindexedRoot");
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntries(
      "dn: cn=Unprivileged Root,cn=Root DNs,cn=config",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "objectClass: ds-cfg-root-dn-user",
      "cn: Unprivileged Root",
      "givenName: Unprivileged",
      "sn: Root",
      "uid: unprivileged.root",
      "userPassword: password",
      "ds-privilege-name: config-read",
      "ds-privilege-name: config-write",
      "ds-privilege-name: password-reset",
      "ds-privilege-name: update-schema",
      "ds-privilege-name: ldif-import",
      "ds-privilege-name: ldif-export",
      "ds-privilege-name: backend-backup",
      "ds-privilege-name: backend-restore",
      "ds-privilege-name: unindexed-search",
      "ds-privilege-name: -jmx-read",
      "ds-privilege-name: -jmx-write",
      "",
      "dn: cn=Unprivileged JMX Root,cn=Root DNs,cn=config",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "objectClass: ds-cfg-root-dn-user",
      "cn: Unprivileged Root",
      "givenName: Unprivileged",
      "sn: Root",
      "uid: unprivileged.root",
      "userPassword: password",
      "",
      "dn: cn=Proxy Root,cn=Root DNs,cn=config",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "objectClass: ds-cfg-root-dn-user",
      "cn: Proxy Root",
      "givenName: Proxy",
      "sn: Root",
      "uid: proxy.root",
      "userPassword: password",
      "ds-privilege-name: proxied-auth",
      "ds-privilege-name: jmx-read",
      "ds-privilege-name: jmx-write",
      "",
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
      "ds-privilege-name: jmx-read",
      "ds-privilege-name: jmx-write",
      "ds-privilege-name: subentry-write",
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

    TestCaseUtils.applyModifications(false,
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
    ArrayList<JmxClientConnection> connList = new ArrayList<>();
    ArrayList<Boolean> successList = new ArrayList<>();
    JmxConnectionHandler jmxCtx = getJmxConnectionHandler();

    connList.add(new JmxClientConnection(jmxCtx,new AuthenticationInfo()));
    successList.add(false);

    connList.add(newJmxClientConnection(jmxCtx, "cn=Unprivileged Root,cn=Root DNs,cn=config", true));
    successList.add(false);

    connList.add(newJmxClientConnection(jmxCtx, "cn=Proxy Root,cn=Root DNs,cn=config", true));
    successList.add(true);

    connList.add(newJmxClientConnection(jmxCtx, "cn=Unprivileged User,o=test", false));
    successList.add(false);

    connList.add(newJmxClientConnection(jmxCtx, "cn=Privileged User,o=test", false));
    successList.add(true);


    connections = new JmxClientConnection[connList.size()];
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

  private JmxClientConnection newJmxClientConnection(JmxConnectionHandler jmxCtx, String userDN, boolean isRoot)
      throws DirectoryException
  {
    Entry userEntry = DirectoryServer.getEntry(DN.valueOf(userDN));
    AuthenticationInfo authInfo = new AuthenticationInfo(userEntry, isRoot);
    return new JmxClientConnection(jmxCtx, authInfo);
  }

  /**
   * Cleans up anything that might be left around after running the tests in this class.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @AfterClass
  public void cleanUp() throws Exception
  {
    processDelete(
        "cn=Unprivileged Root,cn=Root DNs,cn=config",
        "cn=Unprivileged JMX Root,cn=Root DNs,cn=config",
        "cn=Proxy Root,cn=Root DNs,cn=config",
        "cn=Privileged User,o=test",
        "cn=UnPrivileged User,o=test",
        "cn=PWReset Target,o=test",
        "cn=test1 user,dc=unindexed,dc=jeb",
        "cn=test2 user,dc=unindexed,dc=jeb",
        "dc=unindexed,dc=jeb");

    for (int i = 0; connections != null && i < connections.length; i++)
    {
      connections[i].finalize();
      connections[i] = null;
    }
    TestCaseUtils.disableBackend("unindexedRoot");
  }

  private void processDelete(String... userDNs) throws DirectoryException
  {
    for (String userDN : userDNs)
    {
      processDelete(DN.valueOf(userDN));
    }
  }

  /**
   * Retrieves a set of data that can be used for performing the tests.  The
   * arguments generated for each method will be:
   * <OL>
   *   <LI>A client connection to use to perform the operation</LI>
   *   <LI>A flag indicating whether the operation should succeed</LI>
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
   * Check that simple connection to the JMX service are
   * accepted only if JMX_READ privilege is set.
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(enabled = true)
  public void simpleConnectJmxPrivilege() throws Exception
  {
    OpendsJmxConnector opendsConnector;
    int jmxPort = TestCaseUtils.getServerJmxPort() ;
    HashMap<String, Object> env = new HashMap<>();
    final String user = "cn=Unprivileged JMX Root,cn=Root DNs,cn=config";
    String password  = "password";
    String[] credentials = new String[] { user, password };
    env.put("jmx.remote.credentials", credentials);
    env.put("jmx.remote.x.client.connection.check.period", 0);

    // Try connection withoutJMX_READ privilege
    // Expected result: failed
    try
    {
      opendsConnector = new OpendsJmxConnector("localhost", jmxPort, env);
      opendsConnector.connect();
      opendsConnector.close() ;
      fail("User \"cn=Unprivileged JMX Root,cn=Root "+
          "DNs,cn=config\" doesn't have JMX_READ privilege but he's able " +
          "to connect, which is not the correct behavior");
    }
    catch (SecurityException e)
    {
      LocalizableMessage message = ERR_JMX_INSUFFICIENT_PRIVILEGES.get();
      assertEquals(message.toString(), e.getMessage());
    }

    final DN userDN = DN.valueOf(user);

    // Add JMX_READ privilege
    processModify(userDN, ADD, "ds-privilege-name", "jmx-read");

    //  Try connection withoutJMX_READ privilege
    // Expected result: success
    try
    {
      opendsConnector = new OpendsJmxConnector("localhost", jmxPort, env);
      opendsConnector.connect();
      opendsConnector.close() ;
    }
    catch (SecurityException e)
    {
      fail("User \"cn=Unprivileged JMX Root,cn=Root " +
          "DNs,cn=config\" has JMX_READ privilege and he's NOT able " +
          "to connect, which is NOT the correct behavior.");
    }

    // remove JMX_READ privilege
    processModify(userDN, DELETE, "ds-privilege-name", "jmx-read");

    // Try connection withoutJMX_READ privilege
    // Expected result: failed
    try
    {
      opendsConnector = new OpendsJmxConnector("localhost", jmxPort, env);
      opendsConnector.connect();
      opendsConnector.close() ;
      fail("User \"cn=Unprivileged JMX Root,cn=Root "+
          "DNs,cn=config\" doesn't have JMX_READ privilege but he's able " +
          "to connect, which is not the correct behavior");
    }
    catch (SecurityException e)
    {
      LocalizableMessage message = ERR_JMX_INSUFFICIENT_PRIVILEGES.get();
      assertEquals(message.toString(), e.getMessage());
    }
  }

  /**
   * Tests to ensure that search operations in the server configuration properly
   * respect the JMX_READ privilege.
   *
   * @param  conn          The client connection to use to perform the search
   *                       operation.
   * @param  hasPrivilege  Indicates whether the authenticated user is expected
   *                       to have the JMX_READ privilege and therefore the
   *                       search should succeed.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test(dataProvider = "testdata")
  public void testConfigReadSearch(JmxClientConnection conn, boolean hasPrivilege) throws Exception
  {
    assertEquals(conn.hasPrivilege(Privilege.JMX_READ, null), hasPrivilege);

    SearchRequest request = newSearchRequest(DN.valueOf("cn=config"), SearchScope.BASE_OBJECT);
    InternalSearchOperation searchOp = conn.processSearch(request);
    if (hasPrivilege)
    {
      assertEquals(searchOp.getResultCode(), ResultCode.SUCCESS);
    }
    else
    {
      assertEquals(searchOp.getResultCode(), ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
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
  public void testUpdateSchemaAddSchemaFile(JmxClientConnection conn, boolean hasPrivilege) throws Exception
  {
    assertEquals(conn.hasPrivilege(Privilege.JMX_WRITE, null), hasPrivilege);

    String schemaDirectory = SchemaConfigManager.getSchemaDirectoryPath();

    String identifier;
    Entry authNEntry = conn.getAuthenticationInfo().getAuthenticationEntry();
    if (authNEntry == null)
    {
      identifier = "null";
    }
    else
    {
      identifier = authNEntry.getName().toString()
          .replace(',', '-')
          .replace(' ', '-')
          .replace('=', '-');
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
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(validFile)))
    {
      for (String line : fileLines)
      {
        writer.write(line);
        writer.newLine();
      }
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
  public void testProxyAuthV1Write(JmxClientConnection conn, boolean hasPrivilege) throws Exception
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

    ArrayList<Control> controls = new ArrayList<>(1);
    controls.add(new ProxiedAuthV1Control(
                          DN.valueOf("cn=PWReset Target,o=test")));

    // Try to add the entry.  If this fails with the proxy control, then add it
    // with a root connection so we can do other things with it.
    AddOperationBasis addOp = new AddOperationBasis(
        conn, conn.nextOperationID(), conn.nextMessageID(), controls,
        e.getName(), e.getObjectClasses(), e.getUserAttributes(), e.getOperationalAttributes());
    assertSuccess(hasProxyPrivilege, addOp);
    if (!hasProxyPrivilege)
    {
      TestCaseUtils.addEntry(e);
    }

    // Try to modify the entry to add a description.
    ArrayList<Modification> mods = newModifications(REPLACE, "description", "foo");

    ModifyOperationBasis modifyOp = new ModifyOperationBasis(conn,
        conn.nextOperationID(), conn.nextMessageID(), controls, e.getName(),
        mods);
    assertSuccess(hasProxyPrivilege, modifyOp);

    // Try to rename the entry.
    ModifyDNOperationBasis modifyDNOp = new ModifyDNOperationBasis(
        conn, conn.nextOperationID(), conn.nextMessageID(), controls,
        e.getName(), RDN.valueOf("cn=Proxy V1 Test"), true, null);
    assertSuccess(hasProxyPrivilege, modifyDNOp);
    DN newEntryDN = hasProxyPrivilege ? modifyDNOp.getNewDN() : e.getName();

    // Try to delete the operation.  If this fails, then delete it with a root
    // connection so it gets cleaned up.
    DeleteOperationBasis deleteOp = new DeleteOperationBasis(conn,
        conn.nextOperationID(), conn.nextMessageID(), controls, newEntryDN);
    assertSuccess(hasProxyPrivilege, deleteOp);
    if (!hasProxyPrivilege)
    {
      processDelete(newEntryDN);
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
  public void testProxyAuthV1Read(JmxClientConnection conn, boolean hasPrivilege) throws Exception
  {
    // We can't trust the value of hasPrivilege because root users don't get
    // proxy privileges by default.  So make the determination based on the
    // privileges the user actually has.
    boolean hasProxyPrivilege = conn.hasPrivilege(Privilege.PROXIED_AUTH, null);

    DN targetDN = DN.valueOf("cn=PWReset Target,o=test");
    ArrayList<Control> controls = new ArrayList<>(1);
    controls.add(new ProxiedAuthV1Control(targetDN));

    // Test a compare operation against the PWReset Target user.
    CompareOperationBasis compareOp = new CompareOperationBasis(
        conn, conn.nextOperationID(), conn.nextMessageID(), controls,
        targetDN, AttributeDescription.valueOf("cn"),
        ByteString.valueOfUtf8("PWReset Target"));
    assertSuccess(hasProxyPrivilege, compareOp);

    // Test a search operation against the PWReset Target user.
    SearchRequest request = newSearchRequest(targetDN, SearchScope.BASE_OBJECT).addControl(controls);
    InternalSearchOperation searchOp = new InternalSearchOperation(
        conn, conn.nextOperationID(), conn.nextMessageID(), request, null);
    assertSuccess(hasProxyPrivilege, searchOp);
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
  public void testProxyAuthV2Write(JmxClientConnection conn, boolean hasPrivilege) throws Exception
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

    ArrayList<Control> controls = new ArrayList<>(1);
    controls.add(new ProxiedAuthV2Control(
                          ByteString.valueOfUtf8("dn:cn=PWReset Target,o=test")));

    // Try to add the entry.  If this fails with the proxy control, then add it
    // with a root connection so we can do other things with it.
    DN authDN = conn.getAuthenticationInfo().getAuthenticationDN();
    AddOperationBasis addOp = new AddOperationBasis(
        conn, conn.nextOperationID(), conn.nextMessageID(), controls,
        e.getName(), e.getObjectClasses(), e.getUserAttributes(), e.getOperationalAttributes());
    assertSuccess(hasProxyPrivilege, authDN, addOp);
    if (!hasProxyPrivilege)
    {
      TestCaseUtils.addEntry(e);
    }

    // Try to modify the entry to add a description.
    ArrayList<Modification> mods = newModifications(REPLACE, "description", "foo");

    ModifyOperationBasis modifyOp = new ModifyOperationBasis(conn,
        conn.nextOperationID(), conn.nextMessageID(), controls, e.getName(),
        mods);
    assertSuccess(hasProxyPrivilege, authDN, modifyOp);

    // Try to rename the entry.
    ModifyDNOperationBasis modifyDNOp = new ModifyDNOperationBasis(
        conn, conn.nextOperationID(), conn.nextMessageID(), controls,
        e.getName(), RDN.valueOf("cn=Proxy V2 Test"), true, null);
    assertSuccess(hasProxyPrivilege, authDN, modifyDNOp);

    DN newEntryDN = hasProxyPrivilege ? modifyDNOp.getNewDN() : e.getName();

    // Try to delete the operation.  If this fails, then delete it with a root
    // connection so it gets cleaned up.
    DeleteOperationBasis deleteOp = new DeleteOperationBasis(conn,
        conn.nextOperationID(), conn.nextMessageID(), controls, newEntryDN);
    assertSuccess(hasProxyPrivilege, authDN, deleteOp);
    if (!hasProxyPrivilege)
    {
      processDelete(newEntryDN);
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
  public void testProxyAuthV2Read(JmxClientConnection conn, boolean hasPrivilege) throws Exception
  {
    // We can't trust the value of hasPrivilege because root users don't get
    // proxy privileges by default.  So make the determination based on the
    // privileges the user actually has.
    boolean hasProxyPrivilege = conn.hasPrivilege(Privilege.PROXIED_AUTH, null);

    DN targetDN = DN.valueOf("cn=PWReset Target,o=test");
    ArrayList<Control> controls = new ArrayList<>(1);
    controls.add(new ProxiedAuthV2Control(ByteString.valueOfUtf8("dn:" + targetDN)));

    // Test a compare operation against the PWReset Target user.
    CompareOperationBasis compareOp = new CompareOperationBasis(
        conn, conn.nextOperationID(), conn.nextMessageID(), controls,
        targetDN, AttributeDescription.valueOf("cn"),
        ByteString.valueOfUtf8("PWReset Target"));
    assertSuccess(hasProxyPrivilege, compareOp);

    // Test a search operation against the PWReset Target user.
    SearchRequest request = newSearchRequest(targetDN, SearchScope.BASE_OBJECT).addControl(controls);
    InternalSearchOperation searchOp = new InternalSearchOperation(
        conn, conn.nextOperationID(), conn.nextMessageID(), request, null);
    assertSuccess(hasProxyPrivilege, searchOp);
  }

  private void assertSuccess(boolean hasProxyPrivilege, CompareOperationBasis op)
  {
    op.run();
    if (hasProxyPrivilege)
    {
      assertEquals(op.getResultCode(), ResultCode.COMPARE_TRUE);
    }
    else
    {
      assertEquals(op.getResultCode(), ResultCode.AUTHORIZATION_DENIED);
    }
  }

  private void assertSuccess(boolean hasProxyPrivilege, Operation op)
  {
    op.run();
    if (hasProxyPrivilege)
    {
      assertEquals(op.getResultCode(), ResultCode.SUCCESS);
    }
    else
    {
      assertEquals(op.getResultCode(), ResultCode.AUTHORIZATION_DENIED);
    }
  }

  private void assertSuccess(boolean hasProxyPrivilege, DN userDN, Operation op)
  {
    op.run();
    if (hasProxyPrivilege)
    {
      assertEquals(op.getResultCode(), ResultCode.SUCCESS,
          "Unexpected failure for user " + userDN + " and operation " + op);
    }
    else
    {
      assertEquals(op.getResultCode(), ResultCode.AUTHORIZATION_DENIED,
          "Unexpected success for user " + userDN + " and operation " + op);
    }
  }

  /**
   * Tests the ability to update the set of privileges for a user on the fly
   * and have them take effect immediately.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testUpdateUserPrivileges() throws Exception
  {
    final String dnStr = "cn=Test User,o=test";
    final DN dn = DN.valueOf(dnStr);
    Entry testEntry = TestCaseUtils.addEntry(
      "dn: " + dnStr,
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "cn: Test User",
      "givenName: Test",
      "sn: User",
      "userPassword: password");

    AuthenticationInfo authInfo = new AuthenticationInfo(testEntry, false);
    JmxConnectionHandler jmxCtx = getJmxConnectionHandler();
    JmxClientConnection testConnection =
         new JmxClientConnection(jmxCtx,authInfo);

    // Make sure the user starts out without any privileges.
    for (Privilege p : Privilege.values())
    {
      assertFalse(testConnection.hasPrivilege(p, null));
    }

    // Modify the user entry to add the JMX_READ privilege and verify that
    // the client connection reflects that.
    processModify(dn, ADD, "ds-privilege-name", "jmx-read");
    assertTrue(testConnection.hasPrivilege(Privilege.JMX_READ, null));

    // Take the privilege away from the user and verify that it is recognized immediately.
    processModify(dn, DELETE, "ds-privilege-name", "jmx-read");
    assertFalse(testConnection.hasPrivilege(Privilege.JMX_READ, null));

    processDelete(dn);
  }

  /**
   * Tests the ability to update the set of root privileges and have them take
   * effect immediately for new root connections.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  @Test
  public void testUpdateRootPrivileges() throws Exception
  {
    // Make sure that a root connection doesn't have the proxied auth privilege.
    JmxConnectionHandler jmxCtx = getJmxConnectionHandler();
    DN unprivRootDN = DN.valueOf("cn=Unprivileged Root,cn=Root DNs,cn=config");
    JmxClientConnection unprivRootConn = newJmxClientConnection(jmxCtx, unprivRootDN);
    assertFalse(unprivRootConn.hasPrivilege(Privilege.PROXIED_AUTH, null));

    DN rootDN = DN.valueOf("cn=Root DNs,cn=config");

    // Update the set of root privileges to include proxied auth.
    processModify(rootDN, ADD, "ds-cfg-default-root-privilege-name", "proxied-auth");

    // Get a new root connection and verify that it now has proxied auth.
    unprivRootConn = newJmxClientConnection(jmxCtx, unprivRootDN);
    assertTrue(unprivRootConn.hasPrivilege(Privilege.PROXIED_AUTH, null));

    // Update the set of root privileges to revoke proxied auth.
    processModify(rootDN, DELETE, "ds-cfg-default-root-privilege-name", "proxied-auth");

    unprivRootConn = newJmxClientConnection(jmxCtx, unprivRootDN);
    assertFalse(unprivRootConn.hasPrivilege(Privilege.PROXIED_AUTH, null));
  }

  private void processModify(DN dn, ModificationType modType, String attrName, String attrValue)
  {
    ModifyOperation modifyOp = getRootConnection().processModify(
        Requests.newModifyRequest(dn).addModification(modType, attrName, attrValue));
    assertEquals(modifyOp.getResultCode(), ResultCode.SUCCESS);
  }

  private ArrayList<Modification> newModifications(ModificationType modType, String attrName, String attrValue)
  {
    return newArrayList(new Modification(modType, Attributes.create(attrName, attrValue)));
  }

  private void processDelete(DN entryDN)
  {
    DeleteOperation delOp = getRootConnection().processDelete(entryDN);
    assertEquals(delOp.getResultCode(), ResultCode.SUCCESS);
  }

  private JmxClientConnection newJmxClientConnection(JmxConnectionHandler jmxCtx, DN entryDN) throws DirectoryException
  {
    Entry entry = DirectoryServer.getEntry(entryDN);
    AuthenticationInfo authInfo = new AuthenticationInfo(entry, true);
    return new JmxClientConnection(jmxCtx, authInfo);
  }
}
