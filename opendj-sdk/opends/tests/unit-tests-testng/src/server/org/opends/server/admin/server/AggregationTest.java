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



import java.net.ServerSocket;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.naming.ldap.LdapName;

import org.opends.messages.Message;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.AdminTestCase;
import org.opends.server.admin.AdministratorAction;
import org.opends.server.admin.AggregationPropertyDefinition;
import org.opends.server.admin.IllegalPropertyValueStringException;
import org.opends.server.admin.ManagedObjectNotFoundException;
import org.opends.server.admin.PropertyException;
import org.opends.server.admin.PropertyOption;
import org.opends.server.admin.TestCfg;
import org.opends.server.admin.TestChildCfg;
import org.opends.server.admin.TestChildCfgDefn;
import org.opends.server.admin.TestParentCfg;
import org.opends.server.admin.UndefinedDefaultBehaviorProvider;
import org.opends.server.admin.client.OperationRejectedException;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.condition.Conditions;
import org.opends.server.admin.std.client.ConnectionHandlerCfgClient;
import org.opends.server.admin.std.client.LDAPConnectionHandlerCfgClient;
import org.opends.server.admin.std.client.RootCfgClient;
import org.opends.server.admin.std.meta.LDAPConnectionHandlerCfgDefn;
import org.opends.server.admin.std.server.ConnectionHandlerCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ResultCode;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;



/**
 * Test cases for aggregations on the server-side.
 */
@Test(sequential = true)
public final class AggregationTest extends AdminTestCase {

  /**
   * Dummy change listener for triggering change constraint
   * call-backs.
   */
  private static final class DummyChangeListener implements
      ConfigurationChangeListener<TestChildCfg> {

    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationChange(
        TestChildCfg configuration) {
      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationChangeAcceptable(TestChildCfg configuration,
        List<Message> unacceptableReasons) {
      return true;
    }
  }



  /**
   * Dummy delete listener for triggering delete constraint
   * call-backs.
   */
  private static final class DummyDeleteListener implements
      ConfigurationDeleteListener<TestChildCfg> {

    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationDelete(
        TestChildCfg configuration) {
      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationDeleteAcceptable(TestChildCfg configuration,
        List<Message> unacceptableReasons) {
      return true;
    }
  }

  private static final String TEST_CHILD_7_DN = "cn=test child 7,cn=test children,cn=test parent 1,cn=test parents,cn=config";

  private static final String TEST_CHILD_6_DN = "cn=test child 6,cn=test children,cn=test parent 1,cn=test parents,cn=config";

  // The name of the test connection handler.
  private static final String TEST_CONNECTION_HANDLER_NAME = "Test Connection Handler";

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
      "ds-cfg-rotation-policy: cn=LDAP Connection Handler, cn=connection handlers, cn=config"
  };

  // Test child 3 LDIF (invalid reference).
  private static final String[] TEST_CHILD_3 = new String[] {
      "dn: cn=test child 3,cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-child-dummy",
      "cn: test child 3",
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "ds-cfg-conflict-behavior: virtual-overrides-real",
      "ds-cfg-rotation-policy: cn=LDAP Connection Handler, cn=bad rdn, cn=config"
  };

  // Test child 4 LDIF.
  private static final String[] TEST_CHILD_4 = new String[] {
      "dn: cn=test child 4,cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-child-dummy",
      "cn: test child 4",
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "ds-cfg-conflict-behavior: virtual-overrides-real",
      "ds-cfg-rotation-policy: cn=LDAP Connection Handler, cn=connection handlers, cn=config",
      "ds-cfg-rotation-policy: cn=LDAPS Connection Handler, cn=connection handlers, cn=config"
  };

  // Test child 5 LDIF.
  private static final String[] TEST_CHILD_5 = new String[] {
      "dn: cn=test child 5,cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-child-dummy",
      "cn: test child 5",
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "ds-cfg-conflict-behavior: virtual-overrides-real",
      "ds-cfg-rotation-policy: cn=BAD Connection Handler 1, cn=connection handlers, cn=config",
      "ds-cfg-rotation-policy: cn=BAD Connection Handler 2, cn=connection handlers, cn=config",
      "ds-cfg-rotation-policy: cn=LDAP Connection Handler, cn=connection handlers, cn=config"
  };

  // Test child 6 LDIF.
  private static final String[] TEST_CHILD_6 = new String[] {
      "dn: cn=test child 6,cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-child-dummy",
      "cn: test child 6",
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "ds-cfg-conflict-behavior: virtual-overrides-real",
      "ds-cfg-rotation-policy: cn=" + TEST_CONNECTION_HANDLER_NAME
          + ", cn=connection handlers, cn=config"
  };

  // Test child 7 LDIF.
  private static final String[] TEST_CHILD_7 = new String[] {
      "dn: cn=test child 7,cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-test-child-dummy",
      "cn: test child 7",
      "ds-cfg-enabled: false",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "ds-cfg-conflict-behavior: virtual-overrides-real",
      "ds-cfg-rotation-policy: cn=" + TEST_CONNECTION_HANDLER_NAME
          + ", cn=connection handlers, cn=config"
  };

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
      "ds-cfg-enabled: true",
      "ds-cfg-java-class: org.opends.server.extensions.UserDefinedVirtualAttributeProvider",
      "ds-cfg-attribute-type: description",
      "ds-cfg-conflict-behavior: virtual-overrides-real",
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

  // The saved test child configuration "aggregation-property"
  // property definition.
  private AggregationPropertyDefinition<ConnectionHandlerCfgClient, ConnectionHandlerCfg> aggregationPropertyDefinitionDefault = null;

  // An aggregation where the target must be enabled if the source is
  // enabled.
  private AggregationPropertyDefinition<ConnectionHandlerCfgClient, ConnectionHandlerCfg> aggregationPropertyDefinitionTargetAndSourceMustBeEnabled = null;

  // An aggregation where the target must be enabled.
  private AggregationPropertyDefinition<ConnectionHandlerCfgClient, ConnectionHandlerCfg> aggregationPropertyDefinitionTargetMustBeEnabled = null;



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

    // Save the aggregation property definition so that it can be
    // replaced and restored later.
    aggregationPropertyDefinitionDefault = TestChildCfgDefn.getInstance()
        .getAggregationPropertyPropertyDefinition();

    // Create the two test aggregation properties.
    AggregationPropertyDefinition.Builder<ConnectionHandlerCfgClient, ConnectionHandlerCfg> builder;
    TestChildCfgDefn d = TestChildCfgDefn.getInstance();
    builder = AggregationPropertyDefinition.createBuilder(d,
        "aggregation-property");
    builder.setOption(PropertyOption.MULTI_VALUED);
    builder.setAdministratorAction(new AdministratorAction(
        AdministratorAction.Type.NONE, d, "aggregation-property"));
    builder
        .setDefaultBehaviorProvider(new UndefinedDefaultBehaviorProvider<String>());
    builder.setParentPath("/");
    builder.setRelationDefinition("connection-handler");
    builder.setTargetIsEnabledCondition(Conditions.contains("enabled", "true"));
    aggregationPropertyDefinitionTargetMustBeEnabled = builder.getInstance();
    TestCfg
        .initializePropertyDefinition(aggregationPropertyDefinitionTargetMustBeEnabled);

    builder = AggregationPropertyDefinition.createBuilder(d,
        "aggregation-property");
    builder.setOption(PropertyOption.MULTI_VALUED);
    builder.setAdministratorAction(new AdministratorAction(
        AdministratorAction.Type.NONE, d, "aggregation-property"));
    builder
        .setDefaultBehaviorProvider(new UndefinedDefaultBehaviorProvider<String>());
    builder.setParentPath("/");
    builder.setRelationDefinition("connection-handler");
    builder.setTargetIsEnabledCondition(Conditions.contains("enabled", "true"));
    builder.setTargetNeedsEnablingCondition(Conditions.contains(
        "mandatory-boolean-property", "true"));
    aggregationPropertyDefinitionTargetAndSourceMustBeEnabled = builder
        .getInstance();
    TestCfg
        .initializePropertyDefinition(aggregationPropertyDefinitionTargetAndSourceMustBeEnabled);
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

    // Restore the test child aggregation definition.
    TestCfg.addPropertyDefinition(aggregationPropertyDefinitionDefault);

    // Remove test entries.
    deleteSubtree("cn=test parents,cn=config");
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
   * Tests that aggregation is rejected by a constraint violation when
   * the DN values are dangling.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testAggregationDanglingReference() throws Exception {
    // Add the entry.
    TestCaseUtils.addEntry(TEST_CHILD_5);

    try {
      TestParentCfg parent = getParent("test parent 1");
      parent.getTestChild("test child 5");
      Assert
          .fail("Unexpectedly added test child 5 when it had a dangling reference");
    } catch (ConfigException e) {
      // Check that we have a constraint violation as the cause.
      Throwable cause = e.getCause();
      if (cause instanceof ConstraintViolationException) {
        ConstraintViolationException cve = (ConstraintViolationException) cause;
        Collection<Message> causes = cve.getMessages();
        Assert.assertEquals(causes.size(), 2);
      } else {
        // Got an unexpected cause.
        throw e;
      }
    } finally {
      deleteSubtree("cn=test child 5,cn=test children,cn=test parent 1,cn=test parents,cn=config");
    }
  }



  /**
   * Tests that aggregation is rejected by a constraint violation when
   * an enabled component references a disabled component and the
   * referenced component must always be enabled.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testAggregationDisabledReference1() throws Exception {
    // Add the entry and the connection handler.
    TestCaseUtils.addEntry(TEST_CHILD_6);
    try {
      createConnectionHandler(false);
    } catch (Exception e) {
      deleteSubtree(TEST_CHILD_6_DN);
      throw e;
    }

    // Register the temporary aggregation definition.
    TestCfg.removeConstraint(aggregationPropertyDefinitionDefault
        .getSourceConstraint());
    TestCfg
        .addPropertyDefinition(aggregationPropertyDefinitionTargetMustBeEnabled);
    TestCfg.addConstraint(aggregationPropertyDefinitionTargetMustBeEnabled
        .getSourceConstraint());

    try {
      TestParentCfg parent = getParent("test parent 1");
      parent.getTestChild("test child 6");
      Assert
          .fail("Unexpectedly added test child 6 when it had a disabled reference");
    } catch (ConfigException e) {
      // Check that we have a constraint violation as the cause.
      Throwable cause = e.getCause();
      if (cause instanceof ConstraintViolationException) {
        ConstraintViolationException cve = (ConstraintViolationException) cause;
        Collection<Message> causes = cve.getMessages();
        Assert.assertEquals(causes.size(), 1);
      } else {
        // Got an unexpected cause.
        throw e;
      }
    } finally {
      // Put back the default aggregation definition.
      TestCfg.removeConstraint(aggregationPropertyDefinitionTargetMustBeEnabled
          .getSourceConstraint());
      TestCfg.addPropertyDefinition(aggregationPropertyDefinitionDefault);
      TestCfg.addConstraint(aggregationPropertyDefinitionDefault
          .getSourceConstraint());

      try {
        deleteSubtree(TEST_CHILD_6_DN);
      } finally {
        deleteConnectionHandler();
      }
    }
  }



  /**
   * Tests that aggregation is rejected by a constraint violation when
   * a disabled component references a disabled component and the
   * referenced component must always be enabled.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testAggregationDisabledReference2() throws Exception {
    // Add the entry.
    TestCaseUtils.addEntry(TEST_CHILD_7);
    try {
      createConnectionHandler(false);
    } catch (Exception e) {
      deleteSubtree(TEST_CHILD_7_DN);
      throw e;
    }

    // Register the temporary aggregation definition.
    TestCfg.removeConstraint(aggregationPropertyDefinitionDefault
        .getSourceConstraint());
    TestCfg
        .addPropertyDefinition(aggregationPropertyDefinitionTargetMustBeEnabled);
    TestCfg.addConstraint(aggregationPropertyDefinitionTargetMustBeEnabled
        .getSourceConstraint());

    try {
      TestParentCfg parent = getParent("test parent 1");
      parent.getTestChild("test child 7");
      Assert
          .fail("Unexpectedly added test child 7 when it had a disabled reference");
    } catch (ConfigException e) {
      // Check that we have a constraint violation as the cause.
      Throwable cause = e.getCause();
      if (cause instanceof ConstraintViolationException) {
        ConstraintViolationException cve = (ConstraintViolationException) cause;
        Collection<Message> causes = cve.getMessages();
        Assert.assertEquals(causes.size(), 1);
      } else {
        // Got an unexpected cause.
        throw e;
      }
    } finally {
      // Put back the default aggregation definition.
      TestCfg.removeConstraint(aggregationPropertyDefinitionTargetMustBeEnabled
          .getSourceConstraint());
      TestCfg.addPropertyDefinition(aggregationPropertyDefinitionDefault);
      TestCfg.addConstraint(aggregationPropertyDefinitionDefault
          .getSourceConstraint());

      try {
        deleteSubtree(TEST_CHILD_7_DN);
      } finally {
        deleteConnectionHandler();
      }
    }
  }



  /**
   * Tests that aggregation is rejected by a constraint violation when
   * an enabled component references a disabled component and the
   * referenced component must always be enabled when the referencing
   * component is enabled.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testAggregationDisabledReference3() throws Exception {
    // Add the entry.
    TestCaseUtils.addEntry(TEST_CHILD_6);
    try {
      createConnectionHandler(false);
    } catch (Exception e) {
      deleteSubtree(TEST_CHILD_6_DN);
      throw e;
    }

    // Register the temporary aggregation definition.
    TestCfg.removeConstraint(aggregationPropertyDefinitionDefault
        .getSourceConstraint());
    TestCfg
        .addPropertyDefinition(aggregationPropertyDefinitionTargetAndSourceMustBeEnabled);
    TestCfg
        .addConstraint(aggregationPropertyDefinitionTargetAndSourceMustBeEnabled
            .getSourceConstraint());

    try {
      TestParentCfg parent = getParent("test parent 1");
      parent.getTestChild("test child 6");
      Assert
          .fail("Unexpectedly added test child 6 when it had a disabled reference");
    } catch (ConfigException e) {
      // Check that we have a constraint violation as the cause.
      Throwable cause = e.getCause();
      if (cause instanceof ConstraintViolationException) {
        ConstraintViolationException cve = (ConstraintViolationException) cause;
        Collection<Message> causes = cve.getMessages();
        Assert.assertEquals(causes.size(), 1);
      } else {
        // Got an unexpected cause.
        throw e;
      }
    } finally {
      // Put back the default aggregation definition.
      TestCfg
          .removeConstraint(aggregationPropertyDefinitionTargetAndSourceMustBeEnabled
              .getSourceConstraint());
      TestCfg.addPropertyDefinition(aggregationPropertyDefinitionDefault);
      TestCfg.addConstraint(aggregationPropertyDefinitionDefault
          .getSourceConstraint());

      try {
        deleteSubtree(TEST_CHILD_6_DN);
      } finally {
        deleteConnectionHandler();
      }
    }
  }



  /**
   * Tests that aggregation is allowed when a disabled component
   * references a disabled component and the referenced component must
   * always be enabled when the referencing component is enabled.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testAggregationDisabledReference4() throws Exception {
    // Add the entry.
    TestCaseUtils.addEntry(TEST_CHILD_7);
    try {
      createConnectionHandler(false);
    } catch (Exception e) {
      deleteSubtree(TEST_CHILD_7_DN);
      throw e;
    }

    // Register the temporary aggregation definition.
    TestCfg.removeConstraint(aggregationPropertyDefinitionDefault
        .getSourceConstraint());
    TestCfg
        .addPropertyDefinition(aggregationPropertyDefinitionTargetAndSourceMustBeEnabled);
    TestCfg
        .addConstraint(aggregationPropertyDefinitionTargetAndSourceMustBeEnabled
            .getSourceConstraint());

    try {
      TestParentCfg parent = getParent("test parent 1");
      parent.getTestChild("test child 7");
    } finally {
      // Put back the default aggregation definition.
      TestCfg
          .removeConstraint(aggregationPropertyDefinitionTargetAndSourceMustBeEnabled
              .getSourceConstraint());
      TestCfg.addPropertyDefinition(aggregationPropertyDefinitionDefault);
      TestCfg.addConstraint(aggregationPropertyDefinitionDefault
          .getSourceConstraint());

      try {
        deleteSubtree(TEST_CHILD_7_DN);
      } finally {
        deleteConnectionHandler();
      }
    }
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
   * Tests that it is impossible to delete a referenced component when
   * the referenced component must always exist regardless of whether
   * the referencing component is enabled or not.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testCannotDeleteReferencedComponent() throws Exception {
    // Add the entry.
    TestCaseUtils.addEntry(TEST_CHILD_7);
    try {
      createConnectionHandler(true);
    } catch (Exception e) {
      deleteSubtree(TEST_CHILD_7_DN);
      throw e;
    }

    // Register the temporary aggregation definition.
    TestCfg.removeConstraint(aggregationPropertyDefinitionDefault
        .getSourceConstraint());
    TestCfg
        .addPropertyDefinition(aggregationPropertyDefinitionTargetMustBeEnabled);
    TestCfg.addConstraint(aggregationPropertyDefinitionTargetMustBeEnabled
        .getSourceConstraint());

    ConfigurationDeleteListener<TestChildCfg> dl = new DummyDeleteListener();
    ConfigurationChangeListener<TestChildCfg> cl = new DummyChangeListener();
    try {
      // Retrieve the parent and child managed objects and register
      // delete and change listeners respectively in order to trigger
      // the constraint call-backs.
      TestParentCfg parent = getParent("test parent 1");
      parent.addTestChildDeleteListener(dl);

      TestChildCfg child = parent.getTestChild("test child 7");
      child.addChangeListener(cl);

      // Now attempt to delete the referenced connection handler.
      // This should fail.
      try {
        deleteConnectionHandler();
        Assert.fail("Successfully deleted a referenced component");
      } catch (OperationRejectedException e) {
        // This is the expected exception - do nothing.
      }
    } finally {
      try {
        deleteSubtree(TEST_CHILD_7_DN);
      } finally {
        try {
          deleteConnectionHandler();
        } catch (ManagedObjectNotFoundException e) {
          // Ignore as it may have been deleted already.
        } finally {
          // Remove the temporary delete listener.
          TestParentCfg parent = getParent("test parent 1");
          parent.removeTestChildDeleteListener(dl);

          // Put back the default aggregation definition.
          TestCfg
              .removeConstraint(aggregationPropertyDefinitionTargetMustBeEnabled
                  .getSourceConstraint());
          TestCfg.addPropertyDefinition(aggregationPropertyDefinitionDefault);
          TestCfg.addConstraint(aggregationPropertyDefinitionDefault
              .getSourceConstraint());
        }
      }
    }
  }



  /**
   * Tests that it is impossible to disable a referenced component
   * when the referenced component must always be enabled regardless
   * of whether the referencing component is enabled or not.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testCannotDisableReferencedComponent() throws Exception {
    // Add the entry.
    TestCaseUtils.addEntry(TEST_CHILD_7);
    try {
      createConnectionHandler(true);
    } catch (Exception e) {
      deleteSubtree(TEST_CHILD_7_DN);
      throw e;
    }

    // Register the temporary aggregation definition.
    TestCfg.removeConstraint(aggregationPropertyDefinitionDefault
        .getSourceConstraint());
    TestCfg
        .addPropertyDefinition(aggregationPropertyDefinitionTargetMustBeEnabled);
    TestCfg.addConstraint(aggregationPropertyDefinitionTargetMustBeEnabled
        .getSourceConstraint());

    ConfigurationDeleteListener<TestChildCfg> dl = new DummyDeleteListener();
    ConfigurationChangeListener<TestChildCfg> cl = new DummyChangeListener();
    try {
      // Retrieve the parent and child managed objects and register
      // delete and change listeners respectively in order to trigger
      // the constraint call-backs.
      TestParentCfg parent = getParent("test parent 1");
      parent.addTestChildDeleteListener(dl);

      TestChildCfg child = parent.getTestChild("test child 7");
      child.addChangeListener(cl);

      // Now attempt to disable the referenced connection handler.
      // This should fail.
      try {
        RootCfgClient root = TestCaseUtils.getRootConfiguration();
        ConnectionHandlerCfgClient client = root
            .getConnectionHandler(TEST_CONNECTION_HANDLER_NAME);
        client.setEnabled(false);
        client.commit();
        Assert.fail("Successfully disabled a referenced component");
      } catch (OperationRejectedException e) {
        // This is the expected exception - do nothing.
      }
    } finally {
      try {
        deleteSubtree(TEST_CHILD_7_DN);
      } finally {
        try {
          deleteConnectionHandler();
        } finally {
          // Remove the temporary delete listener.
          TestParentCfg parent = getParent("test parent 1");
          parent.removeTestChildDeleteListener(dl);

          // Put back the default aggregation definition.
          TestCfg
              .removeConstraint(aggregationPropertyDefinitionTargetMustBeEnabled
                  .getSourceConstraint());
          TestCfg.addPropertyDefinition(aggregationPropertyDefinitionDefault);
          TestCfg.addConstraint(aggregationPropertyDefinitionDefault
              .getSourceConstraint());
        }
      }
    }
  }



  // Assert that the values of child 1 are correct.
  private void assertChild1(TestChildCfg child) {
    Assert.assertEquals(child.getMandatoryClassProperty(),
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    Assert.assertEquals(child.getMandatoryReadOnlyAttributeTypeProperty(),
        DirectoryServer.getAttributeType("description"));
    assertSetEquals(child.getAggregationProperty(), new String[0]);
  }



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



  // Assert that the values of child 4 are correct.
  private void assertChild4(TestChildCfg child) {
    Assert.assertEquals(child.getMandatoryClassProperty(),
        "org.opends.server.extensions.UserDefinedVirtualAttributeProvider");
    Assert.assertEquals(child.getMandatoryReadOnlyAttributeTypeProperty(),
        DirectoryServer.getAttributeType("description"));
    assertSetEquals(child.getAggregationProperty(), "LDAPS Connection Handler",
        "LDAP Connection Handler");
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



  // Creates a test connection handler for testing.
  private void createConnectionHandler(boolean enabled) throws Exception {
    ServerSocket freeSocket = TestCaseUtils.bindFreePort();
    int freePort = freeSocket.getLocalPort();
    freeSocket.close();

    RootCfgClient root = TestCaseUtils.getRootConfiguration();
    LDAPConnectionHandlerCfgClient client = root.createConnectionHandler(
        LDAPConnectionHandlerCfgDefn.getInstance(),
        TEST_CONNECTION_HANDLER_NAME, null);
    client.setEnabled(enabled);
    client.setListenPort(freePort);
    client.commit();
  }



  // Deletes the test connection handler after testing.
  private void deleteConnectionHandler() throws Exception {
    RootCfgClient root = TestCaseUtils.getRootConfiguration();
    root.removeConnectionHandler(TEST_CONNECTION_HANDLER_NAME);
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
