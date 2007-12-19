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



import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

import org.opends.server.TestCaseUtils;
import org.opends.server.admin.AdminTestCase;
import org.opends.server.admin.DefinitionDecodingException;
import org.opends.server.admin.IllegalPropertyValueStringException;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.PropertyException;
import org.opends.server.admin.TestCfg;
import org.opends.server.admin.TestChildCfgClient;
import org.opends.server.admin.TestChildCfgDefn;
import org.opends.server.admin.TestParentCfgClient;
import org.opends.server.admin.client.AuthorizationException;
import org.opends.server.admin.client.CommunicationException;
import org.opends.server.admin.client.ConcurrentModificationException;
import org.opends.server.admin.client.ManagedObject;
import org.opends.server.admin.client.ManagedObjectDecodingException;
import org.opends.server.admin.client.ManagementContext;
import org.opends.server.admin.std.client.RootCfgClient;
import org.opends.server.core.DirectoryServer;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;



/**
 * Test cases for aggregations on the client-side.
 */
@Test(sequential = true)
public class AggregationTest extends AdminTestCase {

  // Test LDIF.
  private static final String[] TEST_LDIF = new String[] {
      // Base entries.
      "dn: cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-branch",
      "cn: config",
      "",
      "dn: cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-branch",
      "cn: test-parents",
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
      "",
      // Child base entry.
      "dn:cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-branch",
      "cn: multiple children",
      "",
      // Child 1 has no references.
      "dn: cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-child-dummy",
      "cn: test child 1",
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "",
      // Child 2 has a single valid reference.
      "dn: cn=test child 2,cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-child-dummy",
      "cn: test child 2",
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "ds-cfg-rotation-policy: cn=LDAP Connection Handler, cn=connection handlers, cn=config",
      "",
      // Child 3 has a multiple valid references.
      "dn: cn=test child 3,cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-child-dummy",
      "cn: test child 3",
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "ds-cfg-rotation-policy: cn=LDAP Connection Handler, cn=connection handlers, cn=config",
      "ds-cfg-rotation-policy: cn=LDAPS Connection Handler, cn=connection handlers, cn=config",
      "",
      // Child 4 has a single bad reference.
      "dn: cn=test child 4,cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-child-dummy",
      "cn: test child 4",
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "ds-cfg-rotation-policy: cn=LDAP Connection Handler, cn=bad rdn, cn=config",
      "",
      "dn: cn=Connection Handlers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-branch",
      "cn: Connection Handlers",
      "",
      "dn: cn=LDAP Connection Handler,cn=Connection Handlers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-connection-handler",
      "objectClass: ds-cfg-ldap-connection-handler",
      "cn: LDAP Connection Handler",
      "ds-cfg-java-class: org.opends.server.protocols.ldap.LDAPConnectionHandler",
      "ds-cfg-enabled: true",
      "ds-cfg-listen-address: 0.0.0.0",
      "ds-cfg-listen-port: 389",
      "",
      "dn: cn=LDAPS Connection Handler,cn=Connection Handlers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-connection-handler",
      "objectClass: ds-cfg-ldap-connection-handler",
      "cn: LDAPS Connection Handler",
      "ds-cfg-java-class: org.opends.server.protocols.ldap.LDAPConnectionHandler",
      "ds-cfg-enabled: false",
      "ds-cfg-listen-address: 0.0.0.0",
      "ds-cfg-listen-port: 636",
      "ds-cfg-use-ssl: true",
      "ds-cfg-ssl-client-auth-policy: optional",
      "ds-cfg-ssl-cert-nickname: server-cert",
      "ds-cfg-key-manager-provider: cn=JKS,cn=Key Manager Providers,cn=config",
      "ds-cfg-trust-manager-provider: cn=JKS,cn=Trust Manager Providers,cn=config",
      "",
      "dn: cn=JMX Connection Handler,cn=Connection Handlers,cn=config",
      "objectClass: top",
      "objectClass: ds-cfg-connection-handler",
      "objectClass: ds-cfg-jmx-connection-handler",
      "cn: JMX Connection Handler",
      "ds-cfg-java-class: org.opends.server.protocols.jmx.JmxConnectionHandler",
      "ds-cfg-enabled: false",
      "ds-cfg-listen-port: 1689",
      ""
  };



  /**
   * Sets up tests
   *
   * @throws Exception
   *           If the server could not be initialized.
   */
  @BeforeClass
  public void setUp() throws Exception {
    // This test suite depends on having the schema available, so
    // we'll start the server. In addition this test is sensitive to
    // having lots of junk left over from previous tests, so we
    // restart the server to clean out the config (issue 2482).
    TestCaseUtils.restartServer();
    TestCfg.setUp();
  }



  /**
   * Tears down test environment.
   */
  @AfterClass
  public void tearDown() {
    TestCfg.cleanup();
  }



  /**
   * Tests that aggregation contains no values when it contains does
   * not contain any DN attribute values.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testAggregationEmpty() throws Exception {
    MockLDAPConnection c = new MockLDAPConnection();
    c.importLDIF(TEST_LDIF);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    TestChildCfgClient child = parent.getTestChild("test child 1");
    assertSetEquals(child.getAggregationProperty(), new String[0]);
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
    MockLDAPConnection c = new MockLDAPConnection();
    c.importLDIF(TEST_LDIF);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    TestChildCfgClient child = parent.getTestChild("test child 2");

    // Test normalization.
    assertSetEquals(child.getAggregationProperty(), "LDAP Connection Handler");
    assertSetEquals(child.getAggregationProperty(),
        "  LDAP   Connection  Handler ");
    assertSetEquals(child.getAggregationProperty(),
        "  ldap connection HANDLER ");
  }



  /**
   * Tests that aggregation contains multiple valid values when it
   * contains a multiple valid DN attribute values.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testAggregationMultiple() throws Exception {
    MockLDAPConnection c = new MockLDAPConnection();
    c.importLDIF(TEST_LDIF);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    TestChildCfgClient child = parent.getTestChild("test child 3");
    assertSetEquals(child.getAggregationProperty(), "LDAPS Connection Handler",
        "LDAP Connection Handler");
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
    MockLDAPConnection c = new MockLDAPConnection();
    c.importLDIF(TEST_LDIF);
    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");

    try {
      parent.getTestChild("test child 4");
      Assert.fail("Unexpectedly retrieved test child 4"
          + " when it had a bad aggregation value");
    } catch (ManagedObjectDecodingException e) {
      Collection<PropertyException> causes = e.getCauses();
      Assert.assertEquals(causes.size(), 1);

      Throwable cause = causes.iterator().next();
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
    }
  }



  /**
   * Tests creation of a child managed object with a single reference.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testCreateChildManagedObject() throws Exception {
    CreateEntryMockLDAPConnection c = new CreateEntryMockLDAPConnection(
        "cn=test child new,cn=test children,cn=test parent 1,cn=test parents,cn=config");
    c.importLDIF(TEST_LDIF);
    c.addExpectedAttribute("cn", "test child new");
    c.addExpectedAttribute("objectclass", "top", "ds-cfg-test-child-dummy");
    c.addExpectedAttribute("ds-cfg-enabled", "true");
    c.addExpectedAttribute("ds-cfg-java-class",
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    c.addExpectedAttribute("ds-cfg-attribute-type", "description");
    c.addExpectedAttribute("ds-cfg-rotation-policy",
        "cn=LDAP Connection Handler,cn=connection handlers,cn=config");

    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    TestChildCfgClient child = parent.createTestChild(TestChildCfgDefn
        .getInstance(), "test child new", null);
    child.setMandatoryBooleanProperty(true);
    child.setMandatoryReadOnlyAttributeTypeProperty(DirectoryServer
        .getAttributeType("description"));
    child.setAggregationProperty(Collections
        .singleton("LDAP Connection Handler"));
    child.commit();

    c.assertEntryIsCreated();
  }



  /**
   * Tests modification of a child managed object so that it has a
   * different reference.
   *
   * @throws Exception
   *           If an unexpected error occurred.
   */
  @Test
  public void testModifyChildManagedObject() throws Exception {
    ModifyEntryMockLDAPConnection c = new ModifyEntryMockLDAPConnection(
        "cn=test child 2,cn=test children,cn=test parent 1,cn=test parents,cn=config");
    c.importLDIF(TEST_LDIF);
    c.addExpectedModification("ds-cfg-rotation-policy",
        "cn=LDAPS Connection Handler,cn=connection handlers,cn=config",
        "cn=JMX Connection Handler,cn=connection handlers,cn=config");
    ManagementContext ctx = LDAPManagementContext.createFromContext(c);
    TestParentCfgClient parent = getTestParent(ctx, "test parent 1");
    TestChildCfgClient child = parent.getTestChild("test child 2");
    child.setAggregationProperty(Arrays.asList("LDAPS Connection Handler",
        "JMX Connection Handler"));
    child.commit();
    Assert.assertTrue(c.isEntryModified());
  }



  // Retrieve the named test parent managed object.
  private TestParentCfgClient getTestParent(ManagementContext context,
      String name) throws DefinitionDecodingException,
      ManagedObjectDecodingException, AuthorizationException,
      ManagedObjectNotFoundException, ConcurrentModificationException,
      CommunicationException {
    ManagedObject<RootCfgClient> root = context
        .getRootConfigurationManagedObject();
    return root.getChild(TestCfg.getTestOneToManyParentRelationDefinition(),
        name).getConfiguration();
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

}
