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
package org.opends.server.admin.server;



import java.util.List;
import java.util.SortedSet;

import javax.naming.ldap.LdapName;

import org.opends.messages.Message;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.AdminTestCase;
import org.opends.server.admin.TestCfg;
import org.opends.server.admin.TestChildCfg;
import org.opends.server.admin.TestParentCfg;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ResultCode;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;



/**
 * Test cases for default behavior on the server-side.
 */
public final class DefaultBehaviorTest extends AdminTestCase {

  /**
   * A test child add listener.
   */
  private static class AddListener implements
      ConfigurationAddListener<TestChildCfg> {

    // The child configuration that was added.
    private TestChildCfg child;



    /**
     * Creates a new add listener.
     */
    public AddListener() {
      // No implementation required.
    }



    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationAdd(TestChildCfg configuration) {
      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    }



    /**
     * Gets the child configuration checking that it has the expected
     * name.
     *
     * @param expectedName
     *          The child's expected name.
     * @return Returns the child configuration.
     */
    public TestChildCfg getChild(String expectedName) {
      Assert.assertNotNull(child);
      Assert.assertEquals(child.dn().getRDN().getAttributeValue(0)
          .getStringValue(), expectedName);
      return child;
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationAddAcceptable(TestChildCfg configuration,
        List<Message> unacceptableReasons) {
      child = configuration;
      return true;
    }

  }



  /**
   * A test child change listener.
   */
  private static class ChangeListener implements
      ConfigurationChangeListener<TestChildCfg> {

    // The child configuration that was changed.
    private TestChildCfg child;



    /**
     * Creates a new change listener.
     */
    public ChangeListener() {
      // No implementation required.
    }



    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationChange(
        TestChildCfg configuration) {
      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    }



    /**
     * Gets the child configuration checking that it has the expected
     * name.
     *
     * @param expectedName
     *          The child's expected name.
     * @return Returns the child configuration.
     */
    public TestChildCfg getChild(String expectedName) {
      Assert.assertNotNull(child);
      Assert.assertEquals(child.dn().getRDN().getAttributeValue(0)
          .getStringValue(), expectedName);
      return child;
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationChangeAcceptable(TestChildCfg configuration,
        List<Message> unacceptableReasons) {
      child = configuration;
      return true;
    }

  }

  // Test child 1 LDIF.
  private static final String[] TEST_CHILD_1 = new String[] {
      "dn: cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-child-dummy",
      "cn: test child 1",
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "ds-cfg-conflict-behavior: virtual-overrides-real"
  };

  // Test child 2 LDIF.
  private static final String[] TEST_CHILD_2 = new String[] {
      "dn: cn=test child 2,cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-child-dummy",
      "cn: test child 2",
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "ds-cfg-conflict-behavior: virtual-overrides-real",
      "ds-cfg-base-dn: dc=default value c2v1,dc=com",
      "ds-cfg-base-dn: dc=default value c2v2,dc=com"
  };

  // Test child 3 LDIF.
  private static final String[] TEST_CHILD_3 = new String[] {
      "dn: cn=test child 3,cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-child-dummy",
      "cn: test child 3",
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "ds-cfg-conflict-behavior: virtual-overrides-real",
      "ds-cfg-base-dn: dc=default value c3v1,dc=com",
      "ds-cfg-base-dn: dc=default value c3v2,dc=com",
      "ds-cfg-group-dn: dc=default value c3v3,dc=com",
      "ds-cfg-group-dn: dc=default value c3v4,dc=com"
  };

  // Test child 4 LDIF.
  private static final String[] TEST_CHILD_4 = new String[] {
      "dn: cn=test child 4,cn=test children,cn=test parent 2,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-child-dummy",
      "cn: test child 4",
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "ds-cfg-conflict-behavior: virtual-overrides-real"
  };

  // Test LDIF.
  private static final String[] TEST_LDIF = new String[] {
      // Base entries.
      "dn: cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-branch",
      "cn: test parents",
      "",
      // Parent 1 - uses default values for
      // optional-multi-valued-dn-property.
      "dn: cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-parent-dummy",
      "cn: test parent 1",
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "ds-cfg-conflict-behavior: virtual-overrides-real",
      "",
      // Parent 2 - overrides default values for
      // optional-multi-valued-dn-property.
      "dn: cn=test parent 2,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-parent-dummy",
      "cn: test parent 2",
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "ds-cfg-conflict-behavior: virtual-overrides-real",
      "ds-cfg-base-dn: dc=default value p2v1,dc=com",
      "ds-cfg-base-dn: dc=default value p2v2,dc=com",
      "",
      // Child base entries.
      "dn:cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-branch",
      "cn: test children",
      "",
      "dn:cn=test children,cn=test parent 2,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-branch",
      "cn: test children",
      ""
  };

  // JNDI LDAP context.
  private JNDIDirContextAdaptor adaptor = null;



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
    TestCfg.setUp();

    // Add test managed objects.
    TestCaseUtils.addEntries(TEST_LDIF);
  }



  /**
   * Tears down test environment.
   *
   * @throws Exception
   *           If the test entries could not be removed.
   */
  @AfterClass
  public void tearDown() throws Exception {
    TestCfg.cleanup();

    // Remove test entries.
    deleteSubtree("cn=test parents,cn=config");
  }



  /**
   * Tests that children have correct values when accessed through an
   * add listener.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testAddListenerChildValues1() throws Exception {
    TestParentCfg parent = getParent("test parent 1");
    AddListener listener = new AddListener();
    parent.addTestChildAddListener(listener);

    try {
      // Add the entry.
      TestCaseUtils.addEntry(TEST_CHILD_1);
      try {
        assertChild1(listener.getChild("test child 1"));
      } finally {
        deleteSubtree("cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config");
      }
    } finally {
      parent.removeTestChildAddListener(listener);
    }
  }



  /**
   * Tests that children have correct values when accessed through an
   * add listener.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testAddListenerChildValues2() throws Exception {
    TestParentCfg parent = getParent("test parent 1");
    AddListener listener = new AddListener();
    parent.addTestChildAddListener(listener);

    try {
      // Add the entry.
      TestCaseUtils.addEntry(TEST_CHILD_2);
      try {
        assertChild2(listener.getChild("test child 2"));
      } finally {
        deleteSubtree("cn=test child 2,cn=test children,cn=test parent 1,cn=test parents,cn=config");
      }
    } finally {
      parent.removeTestChildAddListener(listener);
    }
  }



  /**
   * Tests that children have correct values when accessed through an
   * add listener.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testAddListenerChildValues3() throws Exception {
    TestParentCfg parent = getParent("test parent 1");
    AddListener listener = new AddListener();
    parent.addTestChildAddListener(listener);

    try {
      // Add the entry.
      TestCaseUtils.addEntry(TEST_CHILD_3);
      try {
        assertChild3(listener.getChild("test child 3"));
      } finally {
        deleteSubtree("cn=test child 3,cn=test children,cn=test parent 1,cn=test parents,cn=config");
      }
    } finally {
      parent.removeTestChildAddListener(listener);
    }
  }



  /**
   * Tests that children have correct values when accessed through an
   * add listener.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testAddListenerChildValues4() throws Exception {
    TestParentCfg parent = getParent("test parent 2");
    AddListener listener = new AddListener();
    parent.addTestChildAddListener(listener);

    try {
      // Add the entry.
      TestCaseUtils.addEntry(TEST_CHILD_4);
      try {
        assertChild4(listener.getChild("test child 4"));
      } finally {
        deleteSubtree("cn=test child 4,cn=test children,cn=test parent 2,cn=test parents,cn=config");
      }
    } finally {
      parent.removeTestChildAddListener(listener);
    }
  }



  /**
   * Tests that children have correct values when accessed through a
   * change listener. This test replaces the defaulted properties with
   * real values.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testChangeListenerChildValues1() throws Exception {
    TestParentCfg parent = getParent("test parent 1");

    try {
      // Add the entry.
      TestCaseUtils.addEntry(TEST_CHILD_1);
      TestChildCfg child = parent.getTestChild("test child 1");
      ChangeListener listener = new ChangeListener();
      child.addChangeListener(listener);

      // Now modify it.
      String[] changes = new String[] {
          "dn: cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config",
          "changetype: modify",
          "replace: ds-cfg-base-dn",
          "ds-cfg-base-dn: dc=new value 1,dc=com",
          "ds-cfg-base-dn: dc=new value 2,dc=com",
          "-",
          "replace: ds-cfg-group-dn",
          "ds-cfg-group-dn: dc=new value 3,dc=com",
          "ds-cfg-group-dn: dc=new value 4,dc=com"
      };
      TestCaseUtils.applyModifications(changes);

      // Make sure that the change listener was notified and the
      // modified child contains the correct values.
      child = listener.getChild("test child 1");

      Assert.assertEquals(child.getMandatoryClassProperty(),
          "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
      Assert.assertEquals(child.getMandatoryReadOnlyAttributeTypeProperty(),
          DirectoryServer.getAttributeType("description"));
      assertDNSetEquals(child.getOptionalMultiValuedDNProperty1(),
          "dc=new value 1,dc=com", "dc=new value 2,dc=com");
      assertDNSetEquals(child.getOptionalMultiValuedDNProperty2(),
          "dc=new value 3,dc=com", "dc=new value 4,dc=com");
    } finally {
      deleteSubtree("cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config");
    }
  }



  /**
   * Tests that children have correct values when accessed through a
   * change listener. This test makes sure that default values
   * inherited from within the modified component itself behave as
   * expected.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testChangeListenerChildValues2() throws Exception {
    TestParentCfg parent = getParent("test parent 1");

    try {
      // Add the entry.
      TestCaseUtils.addEntry(TEST_CHILD_1);
      TestChildCfg child = parent.getTestChild("test child 1");
      ChangeListener listener = new ChangeListener();
      child.addChangeListener(listener);

      // Now modify it.
      String[] changes = new String[] {
          "dn: cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config",
          "changetype: modify",
          "replace: ds-cfg-base-dn",
          "ds-cfg-base-dn: dc=new value 1,dc=com",
          "ds-cfg-base-dn: dc=new value 2,dc=com"
      };
      TestCaseUtils.applyModifications(changes);

      // Make sure that the change listener was notified and the
      // modified child contains the correct values.
      child = listener.getChild("test child 1");

      Assert.assertEquals(child.getMandatoryClassProperty(),
          "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
      Assert.assertEquals(child.getMandatoryReadOnlyAttributeTypeProperty(),
          DirectoryServer.getAttributeType("description"));
      assertDNSetEquals(child.getOptionalMultiValuedDNProperty1(),
          "dc=new value 1,dc=com", "dc=new value 2,dc=com");
      assertDNSetEquals(child.getOptionalMultiValuedDNProperty2(),
          "dc=new value 1,dc=com", "dc=new value 2,dc=com");
    } finally {
      deleteSubtree("cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config");
    }
  }



  /**
   * Tests that children have correct values when accessed through a
   * change listener. This test makes sure that default values
   * inherited from outside the modified component behave as expected.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testChangeListenerChildValues3() throws Exception {
    TestParentCfg parent = getParent("test parent 1");

    try {
      // Add the entry.
      TestCaseUtils.addEntry(TEST_CHILD_1);
      TestChildCfg child = parent.getTestChild("test child 1");
      ChangeListener listener = new ChangeListener();
      child.addChangeListener(listener);

      // Now modify it.
      String[] changes = new String[] {
          "dn: cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config",
          "changetype: modify",
          "replace: ds-cfg-group-dn",
          "ds-cfg-group-dn: dc=new value 1,dc=com",
          "ds-cfg-group-dn: dc=new value 2,dc=com"
      };
      TestCaseUtils.applyModifications(changes);

      // Make sure that the change listener was notified and the
      // modified child contains the correct values.
      child = listener.getChild("test child 1");

      Assert.assertEquals(child.getMandatoryClassProperty(),
          "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
      Assert.assertEquals(child.getMandatoryReadOnlyAttributeTypeProperty(),
          DirectoryServer.getAttributeType("description"));
      assertDNSetEquals(child.getOptionalMultiValuedDNProperty1(),
          "dc=domain1,dc=com", "dc=domain2,dc=com", "dc=domain3,dc=com");
      assertDNSetEquals(child.getOptionalMultiValuedDNProperty2(),
          "dc=new value 1,dc=com", "dc=new value 2,dc=com");
    } finally {
      deleteSubtree("cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config");
    }
  }



  /**
   * Tests that children have correct values when accessed through a
   * change listener. This test makes sure that a component is
   * notified when the default values it inherits from another
   * component are modified.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testChangeListenerChildValues4() throws Exception {
    TestParentCfg parent = getParent("test parent 1");

    try {
      // Add the entry.
      TestCaseUtils.addEntry(TEST_CHILD_1);
      TestChildCfg child = parent.getTestChild("test child 1");
      ChangeListener listener = new ChangeListener();
      child.addChangeListener(listener);

      // Now modify the parent.
      String[] changes = new String[] {
          "dn: cn=test parent 1,cn=test parents,cn=config",
          "changetype: modify",
          "replace: ds-cfg-base-dn",
          "ds-cfg-base-dn: dc=new value 1,dc=com",
          "ds-cfg-base-dn: dc=new value 2,dc=com"
      };
      TestCaseUtils.applyModifications(changes);

      // Make sure that the change listener was notified and the
      // modified child contains the correct values.
      child = listener.getChild("test child 1");

      Assert.assertEquals(child.getMandatoryClassProperty(),
          "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
      Assert.assertEquals(child.getMandatoryReadOnlyAttributeTypeProperty(),
          DirectoryServer.getAttributeType("description"));
      assertDNSetEquals(child.getOptionalMultiValuedDNProperty1(),
          "dc=new value 1,dc=com", "dc=new value 2,dc=com");
      assertDNSetEquals(child.getOptionalMultiValuedDNProperty2(),
          "dc=new value 1,dc=com", "dc=new value 2,dc=com");
    } finally {
      deleteSubtree("cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config");

      // Undo the modifications.
      String[] changes = new String[] {
          "dn: cn=test parent 1,cn=test parents,cn=config",
          "changetype: modify",
          "delete: ds-cfg-base-dn"
      };
      TestCaseUtils.applyModifications(changes);
    }
  }



  /**
   * Tests that children have correct values.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testChildValues1() throws Exception {
    // Add the entry.
    TestCaseUtils.addEntry(TEST_CHILD_1);

    try {
      TestParentCfg parent = getParent("test parent 1");
      assertChild1(parent.getTestChild("test child 1"));
    } finally {
      deleteSubtree("cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config");
    }
  }



  /**
   * Tests that children have correct values.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testChildValues2() throws Exception {
    // Add the entry.
    TestCaseUtils.addEntry(TEST_CHILD_2);

    try {
      TestParentCfg parent = getParent("test parent 1");
      assertChild2(parent.getTestChild("test child 2"));
    } finally {
      deleteSubtree("cn=test child 2,cn=test children,cn=test parent 1,cn=test parents,cn=config");
    }
  }



  /**
   * Tests that children have correct values.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testChildValues3() throws Exception {
    // Add the entry.
    TestCaseUtils.addEntry(TEST_CHILD_3);

    try {
      TestParentCfg parent = getParent("test parent 1");
      assertChild3(parent.getTestChild("test child 3"));
    } finally {
      deleteSubtree("cn=test child 3,cn=test children,cn=test parent 1,cn=test parents,cn=config");
    }
  }



  /**
   * Tests that children have correct values.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testChildValues4() throws Exception {
    // Add the entry.
    TestCaseUtils.addEntry(TEST_CHILD_4);

    try {
      TestParentCfg parent = getParent("test parent 2");
      assertChild4(parent.getTestChild("test child 4"));
    } finally {
      deleteSubtree("cn=test child 4,cn=test children,cn=test parent 2,cn=test parents,cn=config");
    }
  }



  /**
   * Tests that parent 1 has correct values.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testParentValues1() throws Exception {
    TestParentCfg parent = getParent("test parent 1");

    Assert.assertEquals(parent.getMandatoryClassProperty(),
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    Assert.assertEquals(parent.getMandatoryReadOnlyAttributeTypeProperty(),
        DirectoryServer.getAttributeType("description"));
    assertDNSetEquals(parent.getOptionalMultiValuedDNProperty(),
        "dc=domain1,dc=com", "dc=domain2,dc=com", "dc=domain3,dc=com");
  }



  /**
   * Tests that parent 2 has correct values.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testParentValues2() throws Exception {
    TestParentCfg parent = getParent("test parent 2");

    Assert.assertEquals(parent.getMandatoryClassProperty(),
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    Assert.assertEquals(parent.getMandatoryReadOnlyAttributeTypeProperty(),
        DirectoryServer.getAttributeType("description"));
    assertDNSetEquals(parent.getOptionalMultiValuedDNProperty(),
        "dc=default value p2v1,dc=com", "dc=default value p2v2,dc=com");
  }



  // Assert that the values of child 1 are correct.
  private void assertChild1(TestChildCfg child) {
    Assert.assertEquals(child.getMandatoryClassProperty(),
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    Assert.assertEquals(child.getMandatoryReadOnlyAttributeTypeProperty(),
        DirectoryServer.getAttributeType("description"));
    assertDNSetEquals(child.getOptionalMultiValuedDNProperty1(),
        "dc=domain1,dc=com", "dc=domain2,dc=com", "dc=domain3,dc=com");
    assertDNSetEquals(child.getOptionalMultiValuedDNProperty2(),
        "dc=domain1,dc=com", "dc=domain2,dc=com", "dc=domain3,dc=com");
  }



  // Assert that the values of child 2 are correct.
  private void assertChild2(TestChildCfg child) {
    Assert.assertEquals(child.getMandatoryClassProperty(),
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    Assert.assertEquals(child.getMandatoryReadOnlyAttributeTypeProperty(),
        DirectoryServer.getAttributeType("description"));
    assertDNSetEquals(child.getOptionalMultiValuedDNProperty1(),
        "dc=default value c2v1,dc=com", "dc=default value c2v2,dc=com");
    assertDNSetEquals(child.getOptionalMultiValuedDNProperty2(),
        "dc=default value c2v1,dc=com", "dc=default value c2v2,dc=com");
  }



  // Assert that the values of child 3 are correct.
  private void assertChild3(TestChildCfg child) {
    Assert.assertEquals(child.getMandatoryClassProperty(),
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    Assert.assertEquals(child.getMandatoryReadOnlyAttributeTypeProperty(),
        DirectoryServer.getAttributeType("description"));
    assertDNSetEquals(child.getOptionalMultiValuedDNProperty1(),
        "dc=default value c3v1,dc=com", "dc=default value c3v2,dc=com");
    assertDNSetEquals(child.getOptionalMultiValuedDNProperty2(),
        "dc=default value c3v3,dc=com", "dc=default value c3v4,dc=com");
  }



  // Assert that the values of child 4 are correct.
  private void assertChild4(TestChildCfg child) {
    Assert.assertEquals(child.getMandatoryClassProperty(),
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    Assert.assertEquals(child.getMandatoryReadOnlyAttributeTypeProperty(),
        DirectoryServer.getAttributeType("description"));
    assertDNSetEquals(child.getOptionalMultiValuedDNProperty1(),
        "dc=default value p2v1,dc=com", "dc=default value p2v2,dc=com");
    assertDNSetEquals(child.getOptionalMultiValuedDNProperty2(),
        "dc=default value p2v1,dc=com", "dc=default value p2v2,dc=com");
  }



  // Asserts that the actual set of DNs contains the expected values.
  private void assertDNSetEquals(SortedSet<DN> actual, String... expected) {
    String[] actualStrings = new String[actual.size()];
    int i = 0;
    for (DN dn : actual) {
      actualStrings[i] = dn.toString();
      i++;
    }
    Assert.assertEqualsNoOrder(actualStrings, expected);
  }



  // Deletes the named sub-tree.
  private void deleteSubtree(String dn) throws Exception {
    getAdaptor().deleteSubtree(new LdapName(dn));
  }



  // Gets the JNDI connection for the test server instance.
  private synchronized JNDIDirContextAdaptor getAdaptor() throws Exception {
    if (adaptor == null) {
      adaptor = JNDIDirContextAdaptor.simpleBind("127.0.0.1", TestCaseUtils
          .getServerLdapPort(), "cn=directory manager", "password");
    }
    return adaptor;
  }



  // Gets the named parent configuration.
  private TestParentCfg getParent(String name) throws IllegalArgumentException,
      ConfigException {
    ServerManagementContext ctx = ServerManagementContext.getInstance();
    ServerManagedObject<RootCfg> root = ctx.getRootConfigurationManagedObject();
    TestParentCfg parent = root.getChild(TestCfg.getTestOneToManyParentRelationDefinition(),
        name).getConfiguration();
    return parent;
  }
}
