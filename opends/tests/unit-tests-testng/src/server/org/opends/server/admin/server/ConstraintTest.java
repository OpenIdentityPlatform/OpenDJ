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

import javax.naming.OperationNotSupportedException;
import javax.naming.ldap.LdapName;

import org.opends.messages.Message;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.AdminTestCase;
import org.opends.server.admin.TestCfg;
import org.opends.server.admin.TestChildCfg;
import org.opends.server.admin.TestChildCfgDefn;
import org.opends.server.admin.TestParentCfg;
import org.opends.server.admin.client.ldap.JNDIDirContextAdaptor;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.config.ConfigException;
import org.opends.server.core.AddOperation;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.Entry;
import org.opends.server.types.ResultCode;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;



/**
 * Test cases for constraints on the server-side.
 */
public final class ConstraintTest extends AdminTestCase {

  // Child DN.
  private static final String TEST_CHILD_1_DN = "cn=test child 1,cn=test children,cn=test parent 1,cn=test parents,cn=config";



  /**
   * A test child add listener.
   */
  private static class AddListener implements
      ConfigurationAddListener<TestChildCfg> {

    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationAdd(TestChildCfg configuration) {
      return new ConfigChangeResult(ResultCode.SUCCESS, false);
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationAddAcceptable(TestChildCfg configuration,
        List<Message> unacceptableReasons) {
      return true;
    }

  }



  /**
   * A test child delete listener.
   */
  private static class DeleteListener implements
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



  /**
   * A test child change listener.
   */
  private static class ChangeListener implements
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
      // Child base entries.
      "dn:cn=test children,cn=test parent 1,cn=test parents,cn=config",
      "objectclass: top",
      "objectclass: ds-cfg-branch",
      "cn: test children",
      "",
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
   * Tests that retrieval can succeed.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testGetManagedObjectSuccess() throws Exception {
    MockConstraint constraint = new MockConstraint(true, false);

    try {
      TestCaseUtils.addEntry(TEST_CHILD_1);
      TestCfg.addConstraint(constraint);

      TestParentCfg parent = getParent("test parent 1");
      parent.getTestChild("test child 1");
    } finally {
      TestCfg.removeConstraint(constraint);

      try {
        deleteSubtree(TEST_CHILD_1_DN);
      } catch (Exception e) {
        // Do nothing.
      }
    }
  }



  /**
   * Tests that retrieval can fail.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testGetManagedObjectFail() throws Exception {
    MockConstraint constraint = new MockConstraint(false, true);

    try {
      TestCaseUtils.addEntry(TEST_CHILD_1);
      TestCfg.addConstraint(constraint);

      TestParentCfg parent = getParent("test parent 1");
      parent.getTestChild("test child 1");
    } catch (ConfigException e) {
      Throwable cause = e.getCause();
      if (cause instanceof ConstraintViolationException) {
        ConstraintViolationException cve = (ConstraintViolationException) cause;
        Assert.assertEquals(cve.getMessages().size(), 1);
        Assert.assertSame(cve.getManagedObject().getManagedObjectDefinition(),
            TestChildCfgDefn.getInstance());
      } else {
        // Wrong cause.
        throw e;
      }
    } finally {
      TestCfg.removeConstraint(constraint);

      try {
        deleteSubtree(TEST_CHILD_1_DN);
      } catch (Exception e) {
        // Do nothing.
      }
    }
  }



  /**
   * Tests that an add constraint can succeed.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testAddConstraintSuccess() throws Exception {
    TestParentCfg parent = getParent("test parent 1");
    AddListener listener = new AddListener();
    parent.addTestChildAddListener(listener);

    MockConstraint constraint = new MockConstraint(true, false);
    TestCfg.addConstraint(constraint);

    try {
      try {
        // Add the entry.
        addEntry(ResultCode.SUCCESS, TEST_CHILD_1);
      } finally {
        try {
          deleteSubtree(TEST_CHILD_1_DN);
        } catch (Exception e) {
          // Do nothing.
        }
      }
    } finally {
      TestCfg.removeConstraint(constraint);
      parent.removeTestChildAddListener(listener);
    }
  }



  /**
   * Tests that an add constraint can fail.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testAddConstraintFail() throws Exception {
    TestParentCfg parent = getParent("test parent 1");
    AddListener listener = new AddListener();
    parent.addTestChildAddListener(listener);

    MockConstraint constraint = new MockConstraint(false, true);
    TestCfg.addConstraint(constraint);

    try {
      try {
        // Add the entry.
        addEntry(ResultCode.UNWILLING_TO_PERFORM, TEST_CHILD_1);
      } finally {
        try {
          deleteSubtree(TEST_CHILD_1_DN);
        } catch (Exception e) {
          // Do nothing.
        }
      }
    } finally {
      TestCfg.removeConstraint(constraint);
      parent.removeTestChildAddListener(listener);
    }
  }



  /**
   * Tests that a delete constraint can succeed.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testDeleteConstraintSuccess() throws Exception {
    TestParentCfg parent = getParent("test parent 1");
    DeleteListener listener = new DeleteListener();
    parent.addTestChildDeleteListener(listener);

    MockConstraint constraint = new MockConstraint(false, true);
    TestCfg.addConstraint(constraint);

    try {
      // Add the entry.
      TestCaseUtils.addEntry(TEST_CHILD_1);

      // Now delete it - this should trigger the constraint.
      deleteSubtree(TEST_CHILD_1_DN);
    } finally {
      TestCfg.removeConstraint(constraint);
      parent.removeTestChildDeleteListener(listener);

      try {
        // Clean up.
        deleteSubtree(TEST_CHILD_1_DN);
      } catch (Exception e) {
        // Ignore.
      }
    }
  }



  /**
   * Tests that a delete constraint can fail.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testDeleteConstraintFail() throws Exception {
    TestParentCfg parent = getParent("test parent 1");
    DeleteListener listener = new DeleteListener();
    parent.addTestChildDeleteListener(listener);

    MockConstraint constraint = new MockConstraint(true, false);
    TestCfg.addConstraint(constraint);

    try {
      // Add the entry.
      TestCaseUtils.addEntry(TEST_CHILD_1);
      try {
        // Now delete it - this should trigger the constraint.
        deleteSubtree(TEST_CHILD_1_DN);

        // Should not have succeeded.
        Assert.fail("Delete constraint failed to prevent deletion");
      } catch (OperationNotSupportedException e) {
        // Ignore - this is the expected exception.
      }
    } finally {
      TestCfg.removeConstraint(constraint);
      parent.removeTestChildDeleteListener(listener);

      try {
        // Clean up.
        deleteSubtree(TEST_CHILD_1_DN);
      } catch (Exception e) {
        // Ignore.
      }
    }
  }



  /**
   * Tests that a modify constraint can succeed.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testChangeConstraintSuccess() throws Exception {
    TestParentCfg parent = getParent("test parent 1");

    MockConstraint constraint = new MockConstraint(true, false);

    try {
      // Add the entry.
      TestCaseUtils.addEntry(TEST_CHILD_1);
      TestChildCfg child = parent.getTestChild("test child 1");

      TestCfg.addConstraint(constraint);
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

      int result = TestCaseUtils.applyModifications(changes);
      Assert.assertEquals(result, ResultCode.SUCCESS.getIntValue());
    } finally {
      TestCfg.removeConstraint(constraint);
      try {
        deleteSubtree(TEST_CHILD_1_DN);
      } catch (Exception e) {
        // Ignore.
      }
    }
  }



  /**
   * Tests that a modify constraint can fail.
   *
   * @throws Exception
   *           If the test unexpectedly fails.
   */
  @Test
  public void testChangeConstraintFail() throws Exception {
    TestParentCfg parent = getParent("test parent 1");
    MockConstraint constraint = new MockConstraint(false, true);

    try {
      // Add the entry.
      TestCaseUtils.addEntry(TEST_CHILD_1);
      TestChildCfg child = parent.getTestChild("test child 1");

      TestCfg.addConstraint(constraint);
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

      int result = TestCaseUtils.applyModifications(changes);
      Assert
          .assertEquals(result, ResultCode.UNWILLING_TO_PERFORM.getIntValue());
    } finally {
      TestCfg.removeConstraint(constraint);
      try {
        deleteSubtree(TEST_CHILD_1_DN);
      } catch (Exception e) {
        // Ignore.
      }
    }
  }



  // Add an entry and check its result.
  private void addEntry(ResultCode expected, String... lines) throws Exception {
    Entry entry = TestCaseUtils.makeEntry(lines);

    InternalClientConnection conn = InternalClientConnection
        .getRootConnection();

    AddOperation add = conn.processAdd(entry.getDN(), entry.getObjectClasses(),
        entry.getUserAttributes(), entry.getOperationalAttributes());

    Assert.assertEquals(add.getResultCode(), expected, add.getErrorMessage()
        .toString());
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
