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
package org.opends.server.admin.client.ldap;



import java.util.Collection;

import javax.naming.NamingException;
import javax.naming.OperationNotSupportedException;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapName;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.AdminTestCase;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.ManagedObjectAlreadyExistsException;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.MockLDAPProfile;
import org.opends.server.admin.TestChildCfgClient;
import org.opends.server.admin.TestChildCfgDefn;
import org.opends.server.admin.TestParentCfgClient;
import org.opends.server.admin.TestParentCfgDefn;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.client.OperationRejectedException;
import org.opends.server.admin.std.client.RootCfgClient;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;



/**
 * Administration framework LDAP client unit tests.
 */
public final class LDAPClientTest extends AdminTestCase {

  // Test LDIF.
  private static final String[] TEST_LDIF = new String[] {
      "dn: cn=test-parents", "objectclass: top", "objectclass: ds-cfg-branch",
      "cn: test-parents", "", "dn: cn=test parent 1, cn=test-parents",
      "objectclass: top", "objectclass: ds-cfg-test-parent",
      "cn: test parent 1", "", "dn: cn=test parent 2,cn=test-parents",
      "objectclass: top", "objectclass: ds-cfg-test-parent",
      "cn: test parent 2", "ds-cfg-minimum-length: 10000",
      "ds-cfg-maximum-length: 20000", "",
      "dn:cn=test-children,cn=test parent 1,cn=test-parents",
      "objectclass: top", "objectclass: ds-cfg-branch", "cn: test-children",
      "", "dn:cn=test-children,cn=test parent 2,cn=test-parents",
      "objectclass: top", "objectclass: ds-cfg-branch", "cn: test-children",
      "",
      "dn: cn=test child 1,cn=test-children,cn=test parent 1,cn=test-parents",
      "objectclass: top", "objectclass: ds-cfg-test-child", "cn: test child 1",
      "",
      "dn: cn=test child 2,cn=test-children,cn=test parent 1,cn=test-parents",
      "objectclass: top", "objectclass: ds-cfg-test-child", "cn: test child 2",
      "ds-cfg-heartbeat-interval: 12345s", "ds-cfg-minimum-length: 11111",
      "ds-cfg-maximum-length: 22222", "" };



  /**
   * Provide valid naming exception to client API exception mappings.
   *
   * @return Returns valid naming exception to client API exception
   *         mappings.
   */
  @DataProvider(name = "createManagedObjectExceptions")
  public Object[][] createManagedObjectExceptions() {
    return new Object[][] {
        { new javax.naming.CommunicationException(),
            CommunicationException.class },
        { new javax.naming.ServiceUnavailableException(),
            CommunicationException.class },
        { new javax.naming.CannotProceedException(),
            CommunicationException.class },
        { new javax.naming.NameAlreadyBoundException(),
            ManagedObjectAlreadyExistsException.class },
        { new javax.naming.NoPermissionException(),
            AuthorizationException.class },
        { new OperationNotSupportedException(),
            OperationRejectedException.class } };
  }



  /**
   * Provide valid naming exception to client API exception mappings.
   *
   * @return Returns valid naming exception to client API exception
   *         mappings.
   */
  @DataProvider(name = "getManagedObjectExceptions")
  public Object[][] getManagedObjectExceptions() {
    return new Object[][] {
        { new javax.naming.CommunicationException(),
            CommunicationException.class },
        { new javax.naming.ServiceUnavailableException(),
            CommunicationException.class },
        { new javax.naming.CannotProceedException(),
            CommunicationException.class },
        { new javax.naming.NameNotFoundException(),
            ManagedObjectNotFoundException.class },
        { new javax.naming.NoPermissionException(),
            AuthorizationException.class },
        { new OperationNotSupportedException(), CommunicationException.class } };
  }



  /**
   * Sets up tests
   *
   * @throws Exception
   *           If the server could not be initialized.
   */
  @BeforeClass
  public void setUp() throws Exception {
    // This test suite depends on having the schema available, so
    // we'll start the server.
    TestCaseUtils.startServer();
  }



  /**
   * Tests creation of a child managed object.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testCreateChildManagedObject() throws Exception {
    CreateEntryMockLDAPConnection c = new CreateEntryMockLDAPConnection(
        "cn=test child 3,cn=test-children,cn=test parent 1,cn=test-parents");
    c.importLDIF(TEST_LDIF);
    c.addExpectedAttribute("cn", "test child 3");
    c.addExpectedAttribute("objectclass", "top", "ds-cfg-test-child");

    // LDAP encoding uses base unit.
    c.addExpectedAttribute("ds-cfg-heartbeat-interval", "10000ms");

    ManagementContext ctx = LDAPManagementContext.createFromContext(c,
        new MockLDAPProfile());
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    TestChildCfgClient child = parent.createTestChild(TestChildCfgDefn
        .getInstance(), "test child 3", null);
    child.setHeartbeatInterval(10000L);
    child.commit();

    c.assertEntryIsCreated();
  }



  /**
   * Tests creation of a child managed object.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testCreateChildManagedObjectDefault() throws Exception {
    CreateEntryMockLDAPConnection c = new CreateEntryMockLDAPConnection(
        "cn=test child 3,cn=test-children,cn=test parent 1,cn=test-parents");
    c.importLDIF(TEST_LDIF);
    c.addExpectedAttribute("cn", "test child 3");
    c.addExpectedAttribute("objectclass", "top", "ds-cfg-test-child");

    ManagementContext ctx = LDAPManagementContext.createFromContext(c,
        new MockLDAPProfile());
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    TestChildCfgClient child = parent.createTestChild(TestChildCfgDefn
        .getInstance(), "test child 3", null);
    child.commit();

    c.assertEntryIsCreated();
  }



  /**
   * Tests creation of a top-level managed object using fails when an
   * underlying NamingException occurs.
   *
   * @param cause
   *          The NamingException cause of the failure.
   * @param expected
   *          The expected client API exception class.
   */
  @Test(dataProvider = "createManagedObjectExceptions")
  public void testCreateManagedObjectException(final NamingException cause,
      Class<? extends Exception> expected) {
    MockLDAPConnection c = new MockLDAPConnection() {

      /**
       * {@inheritDoc}
       */
      @Override
      public void createEntry(LdapName dn, Attributes attributes)
          throws NamingException {
        throw cause;
      }

    };
    c.importLDIF(TEST_LDIF);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c,
        new MockLDAPProfile());
    try {
      TestParentCfgClient parent = createTestParent(ctx, "test parent 3");
      parent.commit();
    } catch (Exception e) {
      Assert.assertEquals(e.getClass(), expected);
    }
  }



  /**
   * Tests creation of a top-level managed object.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testCreateTopLevelManagedObject() throws Exception {
    CreateEntryMockLDAPConnection c = new CreateEntryMockLDAPConnection(
        "cn=test parent 3,cn=test-parents");
    c.importLDIF(TEST_LDIF);
    c.addExpectedAttribute("cn", "test parent 3");
    c.addExpectedAttribute("objectclass", "top", "ds-cfg-test-parent");
    c.addExpectedAttribute("ds-cfg-maximum-length", "54321");
    c.addExpectedAttribute("ds-cfg-minimum-length", "12345");

    ManagementContext ctx = LDAPManagementContext.createFromContext(c,
        new MockLDAPProfile());
    TestParentCfgClient parent = createTestParent(ctx, "test parent 3");
    parent.setMaximumLength(54321);
    parent.setMinimumLength(12345);
    parent.commit();
    c.assertEntryIsCreated();
  }



  /**
   * Tests creation of a top-level managed object.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testCreateTopLevelManagedObjectDefault() throws Exception {
    CreateEntryMockLDAPConnection c = new CreateEntryMockLDAPConnection(
        "cn=test parent 3,cn=test-parents");
    c.importLDIF(TEST_LDIF);
    c.addExpectedAttribute("cn", "test parent 3");
    c.addExpectedAttribute("objectclass", "top", "ds-cfg-test-parent");

    ManagementContext ctx = LDAPManagementContext.createFromContext(c,
        new MockLDAPProfile());
    TestParentCfgClient parent = createTestParent(ctx, "test parent 3");
    parent.commit();
    c.assertEntryIsCreated();
  }



  /**
   * Tests retrieval of a child managed object with non-default
   * values.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testGetChildManagedObject() throws Exception {
    ManagementContext ctx = getManagementContext(TEST_LDIF);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    TestChildCfgClient child = parent.getTestChild("test child 2");
    Assert.assertEquals(child.getHeartbeatInterval(), 12345000);
    Assert.assertEquals(child.getMinimumLength(), 11111);
    Assert.assertEquals(child.getMaximumLength(), 22222);
  }



  /**
   * Tests retrieval of a child managed object with default values.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testGetChildManagedObjectDefault() throws Exception {
    ManagementContext ctx = getManagementContext(TEST_LDIF);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    TestChildCfgClient child = parent.getTestChild("test child 1");
    Assert.assertEquals(child.getHeartbeatInterval(), 1000);
    Assert.assertEquals(child.getMinimumLength(), 10000);
    Assert.assertEquals(child.getMaximumLength(), 456);
  }



  /**
   * Tests retrieval of a top-level managed object using fails when an
   * underlying NamingException occurs.
   *
   * @param cause
   *          The NamingException cause of the failure.
   * @param expected
   *          The expected client API exception class.
   */
  @Test(dataProvider = "getManagedObjectExceptions")
  public void testGetManagedObjectException(final NamingException cause,
      Class<? extends Exception> expected) {
    MockLDAPConnection c = new MockLDAPConnection() {

      /**
       * {@inheritDoc}
       */
      @Override
      public Attributes readEntry(LdapName dn, Collection<String> attrIds)
          throws NamingException {
        throw cause;
      }

    };
    c.importLDIF(TEST_LDIF);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c,
        new MockLDAPProfile());
    try {
      getTestParent(ctx, "test parent 2");
    } catch (Exception e) {
      Assert.assertEquals(e.getClass(), expected);
    }
  }



  /**
   * Tests retrieval of a top-level managed object with non-default
   * values.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testGetTopLevelManagedObject() throws Exception {
    ManagementContext ctx = getManagementContext(TEST_LDIF);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 2");
    Assert.assertEquals(parent.getMinimumLength(), 10000);
    Assert.assertEquals(parent.getMaximumLength(), 20000);
  }



  /**
   * Tests retrieval of a top-level managed object with default
   * values.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testGetTopLevelManagedObjectDefault() throws Exception {
    ManagementContext ctx = getManagementContext(TEST_LDIF);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    Assert.assertEquals(parent.getMinimumLength(), 123);
    Assert.assertEquals(parent.getMaximumLength(), 456);
  }



  /**
   * Tests listing of child managed objects.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testListChildManagedObjects() throws Exception {
    ManagementContext ctx = getManagementContext(TEST_LDIF);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    String[] actual = parent.listTestChildren();
    String[] expected = new String[] { "test child 1", "test child 2" };
    Assert.assertEqualsNoOrder(actual, expected);
  }



  /**
   * Tests listing of child managed objects when their are not any.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testListChildManagedObjectsEmpty() throws Exception {
    ManagementContext ctx = getManagementContext(TEST_LDIF);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 2");
    String[] actual = parent.listTestChildren();
    String[] expected = new String[] {};
    Assert.assertEqualsNoOrder(actual, expected);
  }



  /**
   * Tests listing of top level managed objects.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testListTopLevelManagedObjects() throws Exception {
    ManagementContext ctx = getManagementContext(TEST_LDIF);
    String[] actual = listTestParents(ctx);
    String[] expected = new String[] { "test parent 1", "test parent 2" };
    Assert.assertEqualsNoOrder(actual, expected);
  }



  /**
   * Tests listing of top level managed objects when their are not
   * any.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testListTopLevelManagedObjectsEmpty() throws Exception {
    ManagementContext ctx = getManagementContext();
    String[] actual = listTestParents(ctx);
    String[] expected = new String[] {};
    Assert.assertEqualsNoOrder(actual, expected);
  }



  /**
   * Tests modification of a child managed object.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testModifyChildManagedObjectResetToDefault() throws Exception {
    ModifyEntryMockLDAPConnection c = new ModifyEntryMockLDAPConnection(
        "cn=test child 2,cn=test-children,cn=test parent 1,cn=test-parents");
    c.importLDIF(TEST_LDIF);
    c.addExpectedModification("ds-cfg-heartbeat-interval");
    ManagementContext ctx = LDAPManagementContext.createFromContext(c,
        new MockLDAPProfile());
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    TestChildCfgClient child = parent.getTestChild("test child 2");
    child.setHeartbeatInterval(null);
    child.commit();
    Assert.assertTrue(c.isEntryModified());
  }



  /**
   * Tests modification of a top-level managed object.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testModifyTopLevelManagedObjectNoChanges() throws Exception {
    ModifyEntryMockLDAPConnection c = new ModifyEntryMockLDAPConnection(
        "cn=test parent 1,cn=test-parents");
    c.importLDIF(TEST_LDIF);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c,
        new MockLDAPProfile());
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    parent.commit();
    Assert.assertFalse(c.isEntryModified());
  }



  /**
   * Tests modification of a top-level managed object.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testModifyTopLevelManagedObjectWithChanges() throws Exception {
    ModifyEntryMockLDAPConnection c = new ModifyEntryMockLDAPConnection(
        "cn=test parent 1,cn=test-parents");
    c.importLDIF(TEST_LDIF);
    c.addExpectedModification("ds-cfg-maximum-length", "54321");
    c.addExpectedModification("ds-cfg-minimum-length", "12345");
    ManagementContext ctx = LDAPManagementContext.createFromContext(c,
        new MockLDAPProfile());
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    parent.setMaximumLength(54321);
    parent.setMinimumLength(12345);
    parent.commit();
    Assert.assertTrue(c.isEntryModified());
  }



  /**
   * Tests removal of a child managed object.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testRemoveChildManagedObject() throws Exception {
    DeleteSubtreeMockLDAPConnection c = new DeleteSubtreeMockLDAPConnection(
        "cn=test child 1,cn=test-children,cn=test parent 1,cn=test-parents");
    c.importLDIF(TEST_LDIF);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c,
        new MockLDAPProfile());
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    parent.removeTestChild("test child 1");
    c.assertSubtreeIsDeleted();
  }



  /**
   * Tests removal of a top-level managed object.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testRemoveTopLevelManagedObject() throws Exception {
    DeleteSubtreeMockLDAPConnection c = new DeleteSubtreeMockLDAPConnection(
        "cn=test parent 1,cn=test-parents");
    c.importLDIF(TEST_LDIF);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c,
        new MockLDAPProfile());
    removeTestParent(ctx, "test parent 1");
    c.assertSubtreeIsDeleted();
  }



  /**
   * Tests retrieval of relative inherited default values.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testInheritedDefaultValues1() throws Exception {
    CreateEntryMockLDAPConnection c = new CreateEntryMockLDAPConnection(
        "cn=test child 3,cn=test-children,cn=test parent 1,cn=test-parents");
    c.importLDIF(TEST_LDIF);
    c.addExpectedAttribute("cn", "test child 3");
    c.addExpectedAttribute("objectclass", "top", "ds-cfg-test-child");

    ManagementContext ctx = LDAPManagementContext.createFromContext(c,
        new MockLDAPProfile());
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    TestChildCfgClient child = parent.createTestChild(TestChildCfgDefn
        .getInstance(), "test child 3", null);

    // Inherits from parent (test parent 1).
    Assert.assertEquals(child.getMinimumLength(), 10000);

    // Inherits from test parent 2.
    Assert.assertEquals(child.getMaximumLength(), 456);

    // Check that the default values are not committed.
    child.commit();

    c.assertEntryIsCreated();
  }



  /**
   * Tests retrieval of relative inherited default values.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testInheritedDefaultValues2() throws Exception {
    CreateEntryMockLDAPConnection c = new CreateEntryMockLDAPConnection(
        "cn=test child 3,cn=test-children,cn=test parent 2,cn=test-parents");
    c.importLDIF(TEST_LDIF);
    c.addExpectedAttribute("cn", "test child 3");
    c.addExpectedAttribute("objectclass", "top", "ds-cfg-test-child");

    ManagementContext ctx = LDAPManagementContext.createFromContext(c,
        new MockLDAPProfile());
    TestParentCfgClient parent = getTestParent(ctx, "test parent 2");
    TestChildCfgClient child = parent.createTestChild(TestChildCfgDefn
        .getInstance(), "test child 3", null);

    // Inherits from parent (test parent 2).
    Assert.assertEquals(child.getMinimumLength(), 10000);

    // Inherits from test parent 2.
    Assert.assertEquals(child.getMaximumLength(), 20000);

    // Check that the default values are not committed.
    child.commit();

    c.assertEntryIsCreated();
  }



  // Create the named test parent managed object.
  private TestParentCfgClient createTestParent(ManagementContext context,
      String name) throws ManagedObjectDecodingException,
      AuthorizationException, ManagedObjectAlreadyExistsException,
      ConcurrentModificationException, OperationRejectedException,
      CommunicationException {
    ManagedObject<RootCfgClient> root = context
        .getRootConfigurationManagedObject();
    return root.createChild(TestParentCfgDefn.RD_TEST_PARENT,
        TestParentCfgDefn.getInstance(), name, null).getConfiguration();
  }



  // Creates a management context using the provided LDIF.
  private ManagementContext getManagementContext(String... ldif) {
    MockLDAPConnection c = new MockLDAPConnection();
    c.importLDIF(ldif);
    return LDAPManagementContext.createFromContext(c, new MockLDAPProfile());
  }



  // Retrieve the named test parent managed object.
  private TestParentCfgClient getTestParent(ManagementContext context,
      String name) throws DefinitionDecodingException,
      ManagedObjectDecodingException, AuthorizationException,
      ManagedObjectNotFoundException, ConcurrentModificationException,
      CommunicationException {
    ManagedObject<RootCfgClient> root = context
        .getRootConfigurationManagedObject();
    return root.getChild(TestParentCfgDefn.RD_TEST_PARENT, name)
        .getConfiguration();
  }



  // List test parent managed objects.
  private String[] listTestParents(ManagementContext context)
      throws AuthorizationException, ConcurrentModificationException,
      CommunicationException {
    ManagedObject<RootCfgClient> root = context
        .getRootConfigurationManagedObject();
    return root.listChildren(TestParentCfgDefn.RD_TEST_PARENT);
  }



  // Remove the named test parent managed object.
  private void removeTestParent(ManagementContext context, String name)
      throws AuthorizationException, ManagedObjectNotFoundException,
      OperationRejectedException, ConcurrentModificationException,
      CommunicationException {
    ManagedObject<RootCfgClient> root = context
        .getRootConfigurationManagedObject();
    root.removeChild(TestParentCfgDefn.RD_TEST_PARENT, name);
  }
}
