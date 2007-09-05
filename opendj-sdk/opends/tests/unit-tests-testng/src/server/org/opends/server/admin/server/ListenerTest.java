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



import static org.testng.Assert.*;

import java.util.List;

import org.opends.messages.Message;
import org.opends.server.TestCaseUtils;
import org.opends.server.admin.AdminTestCase;
import org.opends.server.admin.TestCfg;
import org.opends.server.admin.TestParentCfg;
import org.opends.server.admin.std.server.RootCfg;
import org.opends.server.api.ConfigAddListener;
import org.opends.server.api.ConfigDeleteListener;
import org.opends.server.config.ConfigEntry;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DN;
import org.opends.server.types.ResultCode;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;



/**
 * Test cases for listener registration.
 */
public final class ListenerTest extends AdminTestCase {

  // Add listener implementation.
  private static final class TestParentAddListener implements
      ConfigurationAddListener<TestParentCfg> {

    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationAdd(TestParentCfg configuration) {
      // No implementation required.
      return null;
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationAddAcceptable(TestParentCfg configuration,
        List<Message> unacceptableReasons) {
      // No implementation required.
      return false;
    }
  }



  // Delete listener implementation.
  private static final class TestParentDeleteListener implements
      ConfigurationDeleteListener<TestParentCfg> {

    /**
     * {@inheritDoc}
     */
    public ConfigChangeResult applyConfigurationDelete(
        TestParentCfg configuration) {
      // No implementation required.
      return null;
    }



    /**
     * {@inheritDoc}
     */
    public boolean isConfigurationDeleteAcceptable(TestParentCfg configuration,
        List<Message> unacceptableReasons) {
      // No implementation required.
      return false;
    }

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
   * Checks that a ConfigAddListenerAdaptor is delayed when its
   * associated instantiable relation entry does not exist.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testConfigAddListenerAdaptorInstantiableDelayed()
      throws Exception {
    ServerManagementContext ctx = ServerManagementContext.getInstance();
    ServerManagedObject<RootCfg> root = ctx.getRootConfigurationManagedObject();
    ConfigurationAddListener<TestParentCfg> listener = new TestParentAddListener();
    root.registerAddListener(TestCfg.getTestOneToManyParentRelationDefinition(), listener);

    // Make sure that the relation entry does not exist.
    DN relationDN = DN.decode("cn=test parents,cn=config");
    ConfigEntry configEntry = DirectoryServer.getConfigEntry(relationDN);
    assertNull(configEntry, "Relation entry " + relationDN + " already exists");

    // Make sure that the listener was delayed and registered against
    // the parent.
    DN parentDN = DN.decode("cn=config");
    configEntry = DirectoryServer.getConfigEntry(parentDN);
    assertNotNull(configEntry, "Relation parent entry " + parentDN
        + " does not exist");

    boolean isFound = false;
    for (ConfigAddListener l : configEntry.getAddListeners()) {
      if (l instanceof DelayedConfigAddListener) {
        DelayedConfigAddListener dl = (DelayedConfigAddListener) l;
        ConfigAddListener tmp = dl.getDelayedAddListener();
        if (tmp instanceof ConfigAddListenerAdaptor) {
          ConfigAddListenerAdaptor<?> al = (ConfigAddListenerAdaptor<?>) tmp;
          if (al.getConfigurationAddListener() == listener) {
            isFound = true;
          }
        }
      }
    }

    if (!isFound) {
      fail("Unable to locate delayed listener in entry " + parentDN);
    }

    // Now make sure that the delayed listener is removed from the
    // parent and the add listener register against the relation entry
    // when it is created.
    String[] entry = new String[] {
        "dn: cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-branch",
        "cn: test-parents"
    };
    TestCaseUtils.addEntry(entry);

    // Check the delayed listener is removed.
    for (ConfigAddListener l : configEntry.getAddListeners()) {
      if (l instanceof DelayedConfigAddListener) {
        DelayedConfigAddListener dl = (DelayedConfigAddListener) l;
        ConfigAddListener tmp = dl.getDelayedAddListener();
        if (tmp instanceof ConfigAddListenerAdaptor) {
          ConfigAddListenerAdaptor<?> al = (ConfigAddListenerAdaptor<?>) tmp;
          if (al.getConfigurationAddListener() == listener) {
            fail("Delayed listener still exists in entry " + parentDN
                + " when it should have been removed");

            // Clean up.
            configEntry.deregisterAddListener(dl);
          }
        }
      }
    }

    // Check the add listener is registered.
    configEntry = DirectoryServer.getConfigEntry(relationDN);
    assertNotNull(configEntry, "Relation entry " + relationDN
        + " does not exist");

    isFound = false;
    for (ConfigAddListener l : configEntry.getAddListeners()) {
      if (l instanceof ConfigAddListenerAdaptor) {
        ConfigAddListenerAdaptor<?> al = (ConfigAddListenerAdaptor<?>) l;
        if (al.getConfigurationAddListener() == listener) {
          isFound = true;

          // Clean up.
          configEntry.deregisterAddListener(al);
        }
      }
    }

    if (!isFound) {
      fail("Unable to locate listener adaptor in entry " + relationDN);
    }

    // Remove the test entry.
    InternalClientConnection conn = InternalClientConnection
        .getRootConnection();
    DeleteOperation deleteOperation = conn.processDelete(relationDN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Checks that a ConfigAddListenerAdaptor is not delayed when its
   * associated instantiable relation entry already exists.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testConfigAddListenerAdaptorInstantiableImmediate()
      throws Exception {
    ServerManagementContext ctx = ServerManagementContext.getInstance();
    ServerManagedObject<RootCfg> root = ctx.getRootConfigurationManagedObject();
    ConfigurationAddListener<TestParentCfg> listener = new TestParentAddListener();
    root.registerAddListener(TestCfg.getTestOneToManyParentRelationDefinition(), listener);

    // Add the relation entry.
    String[] entry = new String[] {
        "dn: cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-branch",
        "cn: test-parents"
    };
    TestCaseUtils.addEntry(entry);

    // Make sure that the relation entry exist.
    DN relationDN = DN.decode("cn=test parents,cn=config");
    ConfigEntry configEntry = DirectoryServer.getConfigEntry(relationDN);
    assertNotNull(configEntry, "Relation entry " + relationDN
        + " does not exist");

    // Check the add listener is registered.
    boolean isFound = false;
    for (ConfigAddListener l : configEntry.getAddListeners()) {
      if (l instanceof ConfigAddListenerAdaptor) {
        ConfigAddListenerAdaptor<?> al = (ConfigAddListenerAdaptor<?>) l;
        if (al.getConfigurationAddListener() == listener) {
          isFound = true;

          // Clean up.
          configEntry.deregisterAddListener(al);
        }
      }
    }

    if (!isFound) {
      fail("Unable to locate listener adaptor in entry " + relationDN);
    }

    // Remove the test entry.
    InternalClientConnection conn = InternalClientConnection
        .getRootConnection();
    DeleteOperation deleteOperation = conn.processDelete(relationDN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Checks that a ConfigAddListenerAdaptor is registered for optional
   * relations.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testConfigAddListenerAdaptorOptional() throws Exception {
    ServerManagementContext ctx = ServerManagementContext.getInstance();
    ServerManagedObject<RootCfg> root = ctx.getRootConfigurationManagedObject();
    ConfigurationAddListener<TestParentCfg> listener = new TestParentAddListener();
    root.registerAddListener(TestCfg.getTestOneToZeroOrOneParentRelationDefinition(),
        listener);

    // Make sure that the relation entry exists.
    DN relationDN = DN.decode("cn=config");
    ConfigEntry configEntry = DirectoryServer.getConfigEntry(relationDN);
    assertNotNull(configEntry, "Relation entry " + relationDN
        + " does not exist");

    // Check the add listener is registered.
    boolean isFound = false;
    for (ConfigAddListener l : configEntry.getAddListeners()) {
      if (l instanceof ConfigAddListenerAdaptor) {
        ConfigAddListenerAdaptor<?> al = (ConfigAddListenerAdaptor<?>) l;
        if (al.getConfigurationAddListener() == listener) {
          isFound = true;

          // Clean up.
          configEntry.deregisterAddListener(al);
        }
      }
    }

    if (!isFound) {
      fail("Unable to locate listener adaptor in entry " + relationDN);
    }
  }



  /**
   * Checks that a ConfigDeleteListenerAdaptor is delayed when its
   * associated instantiable relation entry does not exist.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testConfigDeleteListenerAdaptorInstantiableDelayed()
      throws Exception {
    ServerManagementContext ctx = ServerManagementContext.getInstance();
    ServerManagedObject<RootCfg> root = ctx.getRootConfigurationManagedObject();
    ConfigurationDeleteListener<TestParentCfg> listener = new TestParentDeleteListener();
    root.registerDeleteListener(TestCfg.getTestOneToManyParentRelationDefinition(), listener);

    // Make sure that the relation entry does not exist.
    DN relationDN = DN.decode("cn=test parents,cn=config");
    ConfigEntry configEntry = DirectoryServer.getConfigEntry(relationDN);
    assertNull(configEntry, "Relation entry " + relationDN + " already exists");

    // Make sure that the listener was delayed and registered against
    // the parent.
    DN parentDN = DN.decode("cn=config");
    configEntry = DirectoryServer.getConfigEntry(parentDN);
    assertNotNull(configEntry, "Relation parent entry " + parentDN
        + " does not exist");

    boolean isFound = false;
    for (ConfigAddListener l : configEntry.getAddListeners()) {
      if (l instanceof DelayedConfigAddListener) {
        DelayedConfigAddListener dl = (DelayedConfigAddListener) l;
        ConfigDeleteListener tmp = dl.getDelayedDeleteListener();
        if (tmp instanceof ConfigDeleteListenerAdaptor) {
          ConfigDeleteListenerAdaptor<?> al = (ConfigDeleteListenerAdaptor<?>) tmp;
          if (al.getConfigurationDeleteListener() == listener) {
            isFound = true;
          }
        }
      }
    }

    if (!isFound) {
      fail("Unable to locate delayed listener in entry " + parentDN);
    }

    // Now make sure that the delayed listener is removed from the
    // parent and the add listener register against the relation entry
    // when it is created.
    String[] entry = new String[] {
        "dn: cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-branch",
        "cn: test-parents"
    };
    TestCaseUtils.addEntry(entry);

    // Check the delayed listener is removed.
    for (ConfigAddListener l : configEntry.getAddListeners()) {
      if (l instanceof DelayedConfigAddListener) {
        DelayedConfigAddListener dl = (DelayedConfigAddListener) l;
        ConfigDeleteListener tmp = dl.getDelayedDeleteListener();
        if (tmp instanceof ConfigDeleteListenerAdaptor) {
          ConfigDeleteListenerAdaptor<?> al = (ConfigDeleteListenerAdaptor<?>) tmp;
          if (al.getConfigurationDeleteListener() == listener) {
            fail("Delayed listener still exists in entry " + parentDN
                + " when it should have been removed");

            // Clean up.
            configEntry.deregisterAddListener(dl);
          }
        }
      }
    }

    // Check the add listener is registered.
    configEntry = DirectoryServer.getConfigEntry(relationDN);
    assertNotNull(configEntry, "Relation entry " + relationDN
        + " does not exist");

    isFound = false;
    for (ConfigDeleteListener l : configEntry.getDeleteListeners()) {
      if (l instanceof ConfigDeleteListenerAdaptor) {
        ConfigDeleteListenerAdaptor<?> al = (ConfigDeleteListenerAdaptor<?>) l;
        if (al.getConfigurationDeleteListener() == listener) {
          isFound = true;

          // Clean up.
          configEntry.deregisterDeleteListener(al);
        }
      }
    }

    if (!isFound) {
      fail("Unable to locate listener adaptor in entry " + relationDN);
    }

    // Remove the test entry.
    InternalClientConnection conn = InternalClientConnection
        .getRootConnection();
    DeleteOperation deleteOperation = conn.processDelete(relationDN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Checks that a ConfigDeleteListenerAdaptor is not delayed when its
   * associated instantiable relation entry already exists.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testConfigDeleteListenerAdaptorInstantiableImmediate()
      throws Exception {
    ServerManagementContext ctx = ServerManagementContext.getInstance();
    ServerManagedObject<RootCfg> root = ctx.getRootConfigurationManagedObject();
    ConfigurationDeleteListener<TestParentCfg> listener = new TestParentDeleteListener();
    root.registerDeleteListener(TestCfg.getTestOneToManyParentRelationDefinition(), listener);

    // Add the relation entry.
    String[] entry = new String[] {
        "dn: cn=test parents,cn=config",
        "objectclass: top",
        "objectclass: ds-cfg-branch",
        "cn: test-parents"
    };
    TestCaseUtils.addEntry(entry);

    // Make sure that the relation entry exist.
    DN relationDN = DN.decode("cn=test parents,cn=config");
    ConfigEntry configEntry = DirectoryServer.getConfigEntry(relationDN);
    assertNotNull(configEntry, "Relation entry " + relationDN
        + " does not exist");

    // Check the add listener is registered.
    boolean isFound = false;
    for (ConfigDeleteListener l : configEntry.getDeleteListeners()) {
      if (l instanceof ConfigDeleteListenerAdaptor) {
        ConfigDeleteListenerAdaptor<?> al = (ConfigDeleteListenerAdaptor<?>) l;
        if (al.getConfigurationDeleteListener() == listener) {
          isFound = true;

          // Clean up.
          configEntry.deregisterDeleteListener(al);
        }
      }
    }

    if (!isFound) {
      fail("Unable to locate listener adaptor in entry " + relationDN);
    }

    // Remove the test entry.
    InternalClientConnection conn = InternalClientConnection
        .getRootConnection();
    DeleteOperation deleteOperation = conn.processDelete(relationDN);
    assertEquals(deleteOperation.getResultCode(), ResultCode.SUCCESS);
  }



  /**
   * Checks that a ConfigDeleteListenerAdaptor is registered for
   * optional relations.
   *
   * @throws Exception
   *           If an unexpected exception occurred.
   */
  @Test
  public void testConfigDeleteListenerAdaptorOptional() throws Exception {
    ServerManagementContext ctx = ServerManagementContext.getInstance();
    ServerManagedObject<RootCfg> root = ctx.getRootConfigurationManagedObject();
    ConfigurationDeleteListener<TestParentCfg> listener = new TestParentDeleteListener();
    root.registerDeleteListener(TestCfg.getTestOneToZeroOrOneParentRelationDefinition(),
        listener);

    // Make sure that the relation entry exists.
    DN relationDN = DN.decode("cn=config");
    ConfigEntry configEntry = DirectoryServer.getConfigEntry(relationDN);
    assertNotNull(configEntry, "Relation entry " + relationDN
        + " does not exist");

    // Check the add listener is registered.
    boolean isFound = false;
    for (ConfigDeleteListener l : configEntry.getDeleteListeners()) {
      if (l instanceof ConfigDeleteListenerAdaptor) {
        ConfigDeleteListenerAdaptor<?> al = (ConfigDeleteListenerAdaptor<?>) l;
        if (al.getConfigurationDeleteListener() == listener) {
          isFound = true;

          // Clean up.
          configEntry.deregisterDeleteListener(al);
        }
      }
    }

    if (!isFound) {
      fail("Unable to locate listener adaptor in entry " + relationDN);
    }
  }
}
