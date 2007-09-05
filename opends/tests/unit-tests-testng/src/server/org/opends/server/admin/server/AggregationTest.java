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



import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.naming.ldap.LdapName;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.AdminTestCase;
import org.opends.server.admin.IllegalPropertyValueStringException;
import org.opends.server.admin.PropertyException;
import org.opends.server.admin.TestCfg;
import org.opends.server.admin.TestChildCfg;
import org.opends.server.admin.TestChildCfgDefn;
import org.opends.server.admin.TestParentCfg;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;



/**
 * Test cases for aggregations on the server-side.
 */
@Test(sequential=true)
public final class AggregationTest extends AdminTestCase {

  // Test child 1 LDIF.
  private static final String[] TEST_CHILD_1 = new String[] {
      "dn: cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-child-dummy",
      "cn: test child 1",
      "ds-cfg-virtual-attribute-enabled: true",
      "ds-cfg-virtual-attribute-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-virtual-attribute-type: description",
      "ds-cfg-virtual-attribute-conflict-behavior: virtual-overrides-real"
  };



  // Assert that the values of child 1 are correct.
  private void assertChild1(TestChildCfg child) {
    Assert.assertEquals(child.getMandatoryClassProperty(),
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    Assert.assertEquals(child.getMandatoryReadOnlyAttributeTypeProperty(),
        DirectoryServer.getAttributeType("description"));
    assertSetEquals(child.getAggregationProperty(), new String[0]);
  }

  // Test child 2 LDIF.
  private static final String[] TEST_CHILD_2 = new String[] {
      "dn: cn=test child 2,cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-child-dummy",
      "cn: test child 2",
      "ds-cfg-virtual-attribute-enabled: true",
      "ds-cfg-virtual-attribute-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-virtual-attribute-type: description",
      "ds-cfg-virtual-attribute-conflict-behavior: virtual-overrides-real",
      "ds-cfg-backend-base-dn: cn=LDAP Connection Handler, cn=connection handlers, cn=config"
  };



  // Assert that the values of child 2 are correct.
  private void assertChild2(TestChildCfg child) {
    Assert.assertEquals(child.getMandatoryClassProperty(),
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    Assert.assertEquals(child.getMandatoryReadOnlyAttributeTypeProperty(),
        DirectoryServer.getAttributeType("description"));

    // Test normalization.
    assertSetEquals(child.getAggregationProperty(), "LDAP Connection Handler");
    assertSetEquals(child.getAggregationProperty(),
        "  LDAP   Connection  Handler ");
    assertSetEquals(child.getAggregationProperty(),
        "  ldap connection HANDLER ");
  }

  // Test child 3 LDIF (invalid reference).
  private static final String[] TEST_CHILD_3 = new String[] {
      "dn: cn=test child 3,cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-child-dummy",
      "cn: test child 3",
      "ds-cfg-virtual-attribute-enabled: true",
      "ds-cfg-virtual-attribute-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-virtual-attribute-type: description",
      "ds-cfg-virtual-attribute-conflict-behavior: virtual-overrides-real",
      "ds-cfg-backend-base-dn: cn=LDAP Connection Handler, cn=bad rdn, cn=config"
  };

  // Test child 4 LDIF.
  private static final String[] TEST_CHILD_4 = new String[] {
      "dn: cn=test child 4,cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-child-dummy",
      "cn: test child 4",
      "ds-cfg-virtual-attribute-enabled: true",
      "ds-cfg-virtual-attribute-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-virtual-attribute-type: description",
      "ds-cfg-virtual-attribute-conflict-behavior: virtual-overrides-real",
      "ds-cfg-backend-base-dn: cn=LDAP Connection Handler, cn=connection handlers, cn=config",
      "ds-cfg-backend-base-dn: cn=LDAPS Connection Handler, cn=connection handlers, cn=config"
  };



  // Assert that the values of child 4 are correct.
  private void assertChild4(TestChildCfg child) {
    Assert.assertEquals(child.getMandatoryClassProperty(),
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    Assert.assertEquals(child.getMandatoryReadOnlyAttributeTypeProperty(),
        DirectoryServer.getAttributeType("description"));
    assertSetEquals(child.getAggregationProperty(), "LDAPS Connection Handler",
        "LDAP Connection Handler");
  }

  // Test LDIF.
  private static final String[] TEST_LDIF = new String[] {
      // Base entries.
      "dn: cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-branch",
      "cn: test parents",
      "",
      // Parent 1.
      "dn: cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-parent-dummy",
      "cn: test parent 1",
      "ds-cfg-virtual-attribute-enabled: true",
      "ds-cfg-virtual-attribute-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-virtual-attribute-type: description",
      "ds-cfg-virtual-attribute-conflict-behavior: virtual-overrides-real",
      "",
      // Child base entries.
      "dn:cn=test children,cn=test parent 1,cn=test parents,cn=config",
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
   * Tests that aggregation contains no values when it
   * contains does not contain any DN attribute values.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testAggregationEmpty() throws Exception {
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
   * Tests that aggregation contains single valid value when it
   * contains a single valid DN attribute values.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testAggregationSingle() throws Exception {
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
   * Tests that aggregation is rejected when the LDAP DN contains a
   * valid RDN but an invalid parent DN.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testAggregationBadBaseDN() throws Exception {
    // Add the entry.
    TestCaseUtils.addEntry(TEST_CHILD_3);

    try {
      TestParentCfg parent = getParent("test parent 1");
      parent.getTestChild("test child 3");
      Assert
          .fail("Unexpectedly added test child 3 when it had a bad aggregation value");
    } catch (ConfigException e) {
      // Check that we have a decoding exception as the cause and
      // there was only one cause the illegal property value.
      Throwable cause = e.getCause();
      if (cause instanceof ServerManagedObjectDecodingException) {
        ServerManagedObjectDecodingException de = (ServerManagedObjectDecodingException) cause;

        Collection<PropertyException> causes = de.getCauses();
        Assert.assertEquals(causes.size(), 1);

        cause = causes.iterator().next();
        if (cause instanceof IllegalPropertyValueStringException) {
          IllegalPropertyValueStringException pe = (IllegalPropertyValueStringException) cause;
          Assert.assertEquals(pe.getPropertyDefinition(), TestChildCfgDefn
              .getInstance().getAggregationPropertyPropertyDefinition());
          Assert.assertEquals(pe.getIllegalValueString(),
              "cn=LDAP Connection Handler, cn=bad rdn, cn=config");
        } else {
          // Got an unexpected cause.
          throw e;
        }
      } else {
        // Got an unexpected cause.
        throw e;
      }
    } finally {
      deleteSubtree("cn=test child 3,cn=test children,cn=test parent 1,cn=test parents,cn=config");
    }
  }



  /**
   * Tests that aggregation contains multiple valid values when it
   * contains a multiple valid DN attribute values.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testAggregationMultipleValues() throws Exception {
    // Add the entry.
    TestCaseUtils.addEntry(TEST_CHILD_4);

    try {
      TestParentCfg parent = getParent("test parent 1");
      assertChild4(parent.getTestChild("test child 4"));
    } finally {
      deleteSubtree("cn=test child 4,cn=test children,cn=test parent 1,cn=test parents,cn=config");
    }
  }



  // Asserts that the actual set of DNs contains the expected values.
  private void assertSetEquals(SortedSet<String> actual, String... expected) {
    SortedSet<String> values = new TreeSet<String>(TestChildCfgDefn
        .getInstance().getAggregationPropertyPropertyDefinition());
    if (expected != null) {
      for (String value : expected) {
        values.add(value);
      }
    }
    Assert.assertEquals((Object) actual, (Object) values);
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
    TestParentCfg parent = root.getChild(
        TestCfg.getTestOneToManyParentRelationDefinition(), name)
        .getConfiguration();
    return parent;
  }
}
