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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 */
package org.opends.server.core.networkgroups;


import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.opends.server.config.ConfigConstants.DN_BACKEND_BASE;

import java.util.ArrayList;
import java.util.Collections;

import static org.opends.messages.CoreMessages.*;

import org.opends.server.TestCaseUtils;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.admin.std.meta.NetworkGroupCfgDefn.AllowedAuthMethod;
import org.opends.server.api.ClientConnection;
import org.opends.server.core.*;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.Attribute;
import org.opends.server.types.Attributes;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Modification;
import org.opends.server.types.ModificationType;
import org.opends.server.types.ResultCode;
import org.opends.server.types.SearchScope;
import org.opends.server.workflowelement.WorkflowElement;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;


/**
 * This set of tests test the network groups.
 */
public class NetworkGroupTest extends DirectoryServerTestCase {
  //===========================================================================
  //
  //                      B E F O R E    C L A S S
  //
  //===========================================================================

  /**
   * Sets up the environment for performing the tests in this suite.
   *
   * @throws Exception if the environment could not be set up.
   */
  @BeforeClass
  public void setUp()
    throws Exception
  {
    // This test suite depends on having the schema available,
    // so we'll start the server.
    TestCaseUtils.startServer();
  }

  //===========================================================================
  //
  //                      D A T A    P R O V I D E R
  //
  //===========================================================================

  /**
   * Provides information to create a network group with one workflow inside.
   *
   * Each set of DNs contains:
   * - one network group identifier
   * - one base DN for the workflow to register with the network group

   */
  @DataProvider (name = "DNSet_0")
  public Object[][] initDNSet_0()
    throws Exception
  {
    // Network group ID
    String networkGroupID1 = "networkGroup1";
    String networkGroupID2 = "networkGroup2";

    // Workflow base DNs
    DN dn1 = null;
    DN dn2 = null;
    try
    {
      dn1 = DN.decode("o=test1");
      dn2 = DN.decode("o=test2");
    }
    catch (DirectoryException de)
    {
      throw de;
    }

    // Network group info
    Object[][] myData =
    {
        // Test1: create a network group with the identifier networkGroupID1
        { networkGroupID1, dn1 },

        // Test2: create the same network group to check that previous
        // network group was properly cleaned.
        { networkGroupID1, dn1 },

        // Test3: create another network group
        { networkGroupID2, dn2 },
    };

    return myData;
  }


  /**
   * Provides a single DN to search a workflow in a network group.
   *
   * Each set of DNs is composed of:
   * - one baseDN
   * - one subordinateDN
   * - a boolean telling whether we expect to find a workflow for the baseDN
   *   in the default network group
   * - a boolean telling whether we expect to find a workflow for the baseDN
   *   in the administration network group
   * - a boolean telling whether we expect to find a workflow for the baseDN
   *   in the internal network group
   *
   * @return set of DNs
   * @throws Exception  when DN.decode fails
   */
  @DataProvider(name = "DNSet_1")
  public Object[][] initDNSet_1()
    throws Exception
  {
    DN dnRootDSE = null;
    DN dnConfig  = null;
    DN dnMonitor = null;
    DN dnSchema  = null;
    DN dnTasks   = null;
    DN dnBackups = null;
    DN dnDummy   = null;

    DN dnSubordinateConfig  = null;
    DN dnSubordinateMonitor = null;
    DN dnSubordinateTasks   = null;

    try
    {
      dnRootDSE = DN.decode("");
      dnConfig  = DN.decode("cn=config");
      dnMonitor = DN.decode("cn=monitor");
      dnSchema  = DN.decode("cn=schema");
      dnTasks   = DN.decode("cn=tasks");
      dnBackups = DN.decode("cn=backups");
      dnDummy   = DN.decode("o=dummy_suffix");

      dnSubordinateConfig  = DN.decode("cn=Work Queue,cn=config");
      dnSubordinateMonitor = DN.decode("cn=schema Backend,cn=monitor");
      dnSubordinateTasks   = DN.decode("cn=Scheduled Tasks,cn=tasks");

      // No DN subordinate for schema because the schema backend is
      // currently empty.
      // No DN subordinate for cn=backups because by default there is no
      // child entry under cn=backups.
    }
    catch (DirectoryException de)
    {
      throw de;
    }

    // Sets of DNs
    Object[][] myData =
    {
        { dnRootDSE,  null,                 true,  true,  true },
        { dnConfig,   dnSubordinateConfig,  true,  true,  true },
        { dnMonitor,  dnSubordinateMonitor, true,  true,  true },
        { dnTasks,    dnSubordinateTasks,   true,  true,  true },
        { dnSchema,   null,                 true,  true,  true },
        { dnBackups,  null,                 true,  true,  true },
        { dnDummy,    null,                 false, false, false },
    };

    return myData;
  }


  /**
   * Provides information to create a network group to test the routing
   * process.
   *
   * Each set of DNs contains:
   * - one base DN for the 1st workflow
   * - one base DN for the 2nd workflow
   * - one base DN for the 3rd workflow
   * - one subordinate DN for the 1st workflow
   * - one subordinate DN for the 2nd workflow
   * - one subordinate DN for the 3rd workflow
   * - one unrelated DN which has no hierarchical relationship with
   *   any of the above DNs

   */
  @DataProvider (name = "DNSet_2")
  public Object[][] initDNSet_2()
    throws Exception
  {
    // Network group definition
    DN     dn1          = null;
    DN     dn2          = null;
    DN     dn3          = null;
    DN     subordinate1 = null;
    DN     subordinate2 = null;
    DN     subordinate3 = null;
    DN     unrelatedDN  = null;
    try
    {
      dn1          = DN.decode("o=test1");
      dn2          = DN.decode("o=test2");
      dn3          = DN.decode("o=test3");
      subordinate1 = DN.decode("ou=subtest1,o=test1");
      subordinate2 = DN.decode("ou=subtest2,o=test2");
      subordinate3 = DN.decode("ou=subtest3,o=test3");
      unrelatedDN  = DN.decode("o=dummy");
    }
    catch (DirectoryException de)
    {
      throw de;
    }

    // Network group info
    Object[][] myData =
    {
        // Test1: one DN for one workflow
        {
          dn1,
          null,
          null,
          subordinate1,
          null,
          null,
          unrelatedDN
        },
        // Test2: two DNs for two workflows
        {
          dn1,
          dn2,
          null,
          subordinate1,
          subordinate2,
          null,
          unrelatedDN
        },
        // Test3: three DNs for three workflows
        {
          dn1,
          dn2,
          dn3,
          subordinate1,
          subordinate2,
          subordinate3,
          unrelatedDN
        }
    };

    return myData;
  }


  /**
   * Provides information to create a network group with resource limits.
   */
  @DataProvider (name = "DNSet_3")
  public Object[][] initDNSet_3()
    throws Exception
  {
    // Network group definition
    String networkGroupID = "networkGroup1";
    DN  dn = null;
    int prio = 1;

    // Resource limits
    int maxConnections = 10;
    int maxConnectionsFromSameClient = 5;
    int maxOpsPerConn = 4;
    int maxConcurrentOpsPerConn = 2;
    int searchTimeLimit = 100;
    int searchSizeLimit = 50;
    int minSubstringLength = 4;

    try
    {
      dn = DN.decode("o=test1");
    }
    catch (DirectoryException de)
    {
      throw de;
    }

    // Network group info
    Object[][] myData =
    {
        // Test1: one DN for one workflow
        {
          networkGroupID,
          dn,
          prio,
          maxConnections,
          maxConnectionsFromSameClient,
          maxOpsPerConn,
          maxConcurrentOpsPerConn,
          searchTimeLimit,
          searchSizeLimit,
          minSubstringLength
        }
    };
    return myData;
  }


  /**
   * Provides information to create 2 network groups with different priorities.
   */
  @DataProvider (name = "DNSet_4")
  public Object[][] initDNSet_4()
    throws Exception
  {
    String networkGroupID1 = "group1";
    String networkGroupID2 = "group2";
    DN dn1 = null;
    DN dn2 = null;

    try {
      dn1 = DN.decode("o=test1");
      dn2 = DN.decode("o=test2");
    } catch (DirectoryException de) {
      throw de;
    }
    Object[][] myData = {
      {
        networkGroupID1,
        dn1,
        1,
        networkGroupID2,
        dn2,
        2
      },
      {
        networkGroupID1,
        dn1,
        2,
        networkGroupID2,
        dn2,
        1
      }

    };

    return myData;
  }


  /**
   * Provides the priorities for 3 network groups
   */
  @DataProvider (name = "PrioritySet_0")
  public Object[][] initPrioritySet_0()
  {
    Object[][] myData = {
      { 1, 2, 3 },
      { 1, 3, 2 },
      { 2, 1, 3 },
      { 2, 3, 1 },
      { 3, 1, 2 },
      { 3, 2, 1 }
    };

    return myData;
  }


  /**
   * Provides a bind DN filter to build network group criteria
   * and the expected result (true if the connection with
   * cn=Directory Manager, cn =Root DNs, cn=config should match the
   * network group, false if it should go into the default network group).
   */
  @DataProvider (name = "BindFilterSet_0")
  public Object[][] initBindFilterSet_0()
  {
    Object[][] myData = {
      {
        "*, cn=Root DNs, cn=config",
        true
      },
      {
        "cn=Dir*, cn=Root DNs, cn=config",
        true
      },
      {
        "cn=*",
        false
      },
      {
        "uid=*",
        false
      },
      {
        "**, cn=config",
        true
      },
      {
        "*, cn=config",
        false
      }
    };
    return myData;
  }


  //===========================================================================
  //
  //                        T E S T   C A S E S
  //
  //===========================================================================


  /**
   * Tests the network group registration.
   *
   * @param networkGroupID   the ID of the network group to register
   * @param workflowBaseDN1  the base DN of the first workflow node to register
   *                         in the network group
   */
  @Test (dataProvider = "DNSet_0", groups = "virtual")
  public void testNetworkGroupRegistration(
      String networkGroupID,
      DN     workflowBaseDN
      )
      throws Exception
  {
    // Create and register the network group with the server.
    NetworkGroup networkGroup = new NetworkGroup(networkGroupID);
    networkGroup.register();

    // Register again the network group with the server and catch the
    // expected DirectoryServer exception.
    boolean exceptionRaised = false;
    try
    {
      networkGroup.register();
    }
    catch (InitializationException e)
    {
      exceptionRaised = true;
      assertEquals(e.getMessageObject().getDescriptor(),
                   ERR_REGISTER_NETWORK_GROUP_ALREADY_EXISTS);
    }
    assertEquals(exceptionRaised, true);

    // Create a workflow -- the workflow ID is the string representation
    // of the workflow base DN.
    WorkflowElement<?> nullWE = null;
    WorkflowImpl workflow = new WorkflowImpl(
        workflowBaseDN.toString(), workflowBaseDN, null, nullWE);

    // Register the workflow with the network group.
    networkGroup.registerWorkflow(workflow);

    // Register again the workflow with the network group and catch the
    // expected DirectoryServer exception.
    exceptionRaised = false;
    try
    {
      networkGroup.registerWorkflow(workflow);
    }
    catch (DirectoryException de)
    {
      exceptionRaised = true;
      assertEquals(
          de.getMessageObject().getDescriptor(),
              ERR_REGISTER_WORKFLOW_NODE_ALREADY_EXISTS);
    }
    assertEquals(exceptionRaised, true);

    // Clean the network group
    networkGroup.deregisterWorkflow(workflow.getWorkflowId());
    networkGroup.deregister();
  }


  /**
   * Check the route process in the default network group.
   *
   *  @param dnToSearch     the DN of a workflow to search in the default
   *                        network group
   *  @param dnSubordinate  a subordinate DN of dnToSearch
   *  @param exists         true if we are supposed to find a workflow for
   *                        dnToSearch
   */
  @Test (dataProvider = "DNSet_1", groups = "virtual")
  public void checkDefaultNetworkGroup(
      DN      dnToSearch,
      DN      dnSubordinate,
      boolean existsInDefault,
      boolean existsInAdmin,
      boolean existsInInternal
      )
  {
    // let's get the default network group -- it should always exist
    NetworkGroup defaultNG = NetworkGroup.getDefaultNetworkGroup();
    assertNotNull(defaultNG);

    // let's check the routing through the network group
    doCheckNetworkGroup(defaultNG, dnToSearch, dnSubordinate, null,
            existsInDefault);

    // Dump the default network group
    dump(defaultNG, "defaultNetworkGroup> ");

    // let's get the admin network group -- it should always exist
    NetworkGroup adminNG = NetworkGroup.getAdminNetworkGroup();
    assertNotNull(adminNG);

    // let's check the routing through the network group
    doCheckNetworkGroup(adminNG, dnToSearch, dnSubordinate, null,
            existsInAdmin);

    // Dump the default network group
    dump(adminNG, "adminNetworkGroup> ");

    // let's get the internal network group -- it should always exist
    NetworkGroup internalNG = NetworkGroup.getInternalNetworkGroup();
    assertNotNull(internalNG);

    // let's check the routing through the network group
    doCheckNetworkGroup(internalNG, dnToSearch, dnSubordinate, null,
            existsInInternal);

    // Dump the default network group
    dump(internalNG, "internalNetworkGroup> ");
  }


  /**
   * Creates a network group with several workflows inside and do some check
   * on the route processing.
   *
   * @param dn1           the DN for the 1st workflow
   * @param dn2           the DN for the 2nd workflow
   * @param dn3           the DN for the 3rd workflow
   * @param subordinate1  the subordinate DN for the 1st workflow
   * @param subordinate2  the subordinate DN for the 2nd workflow
   * @param subordinate3  the subordinate DN for the 3rd workflow
   * @param unrelatedDN   a DN with no hierarchical relationship with
   *                      any of the DNs above
   *
   * @throws  DirectoryException  If the network group ID for a provided
   *                              network group conflicts with the network
   *                              group ID of an existing network group.
   */
  @Test (dataProvider = "DNSet_2", groups = "virtual")
  public void createNetworkGroup(
      DN dn1,
      DN dn2,
      DN dn3,
      DN subordinate1,
      DN subordinate2,
      DN subordinate3,
      DN unrelatedDN
      ) throws Exception
  {
    // The network group identifier is always the same for this test.
    String networkGroupID = "Network Group for test2";

    // Create the network group
    NetworkGroup networkGroup = new NetworkGroup(networkGroupID);
    assertNotNull(networkGroup);

    // Register the network group with the server
    networkGroup.register();

    // Create and register workflow 1, 2 and 3
    createAndRegisterWorkflow(networkGroup, dn1);
    createAndRegisterWorkflow(networkGroup, dn2);
    createAndRegisterWorkflow(networkGroup, dn3);

    // Check the route through the network group
    doCheckNetworkGroup(networkGroup, dn1, subordinate1, unrelatedDN, true);
    doCheckNetworkGroup(networkGroup, dn2, subordinate2, unrelatedDN, true);
    doCheckNetworkGroup(networkGroup, dn3, subordinate3, unrelatedDN, true);

    // Deregister the workflow1 and check the route again.
    // Workflow to deregister is identified by its baseDN.
    networkGroup.deregisterWorkflow(dn1);
    doCheckNetworkGroup(networkGroup, dn1, subordinate1, unrelatedDN, false);
    doCheckNetworkGroup(networkGroup, dn2, subordinate2, unrelatedDN, true);
    doCheckNetworkGroup(networkGroup, dn3, subordinate3, unrelatedDN, true);

    // Deregister the workflow2 and check the route again
    networkGroup.deregisterWorkflow(dn2);
    doCheckNetworkGroup(networkGroup, dn1, subordinate1, unrelatedDN, false);
    doCheckNetworkGroup(networkGroup, dn2, subordinate2, unrelatedDN, false);
    doCheckNetworkGroup(networkGroup, dn3, subordinate3, unrelatedDN, true);

    // Deregister the workflow3 and check the route again
    networkGroup.deregisterWorkflow(dn3);
    doCheckNetworkGroup(networkGroup, dn1, subordinate1, unrelatedDN, false);
    doCheckNetworkGroup(networkGroup, dn2, subordinate2, unrelatedDN, false);
    doCheckNetworkGroup(networkGroup, dn3, subordinate3, unrelatedDN, false);

    // Now create again the workflow 1, 2 and 3...
    WorkflowImpl w1;
    WorkflowImpl w2;
    WorkflowImpl w3;
    w1 = createAndRegisterWorkflow(networkGroup, dn1);
    w2 = createAndRegisterWorkflow(networkGroup, dn2);
    w3 = createAndRegisterWorkflow(networkGroup, dn3);

    // ... and deregister the workflows using their workflowID
    // instead of their baseDN
    if (w1 != null)
    {
      networkGroup.deregisterWorkflow(w1.getWorkflowId());
      doCheckNetworkGroup(networkGroup, dn1, subordinate1, unrelatedDN, false);
      doCheckNetworkGroup(networkGroup, dn2, subordinate2, unrelatedDN, true);
      doCheckNetworkGroup(networkGroup, dn3, subordinate3, unrelatedDN, true);
    }

    if (w2 != null)
    {
      networkGroup.deregisterWorkflow(w2.getWorkflowId());
      doCheckNetworkGroup(networkGroup, dn1, subordinate1, unrelatedDN, false);
      doCheckNetworkGroup(networkGroup, dn2, subordinate2, unrelatedDN, false);
      doCheckNetworkGroup(networkGroup, dn3, subordinate3, unrelatedDN, true);
    }

    if (w3 != null)
    {
      networkGroup.deregisterWorkflow(w3.getWorkflowId());
      doCheckNetworkGroup(networkGroup, dn1, subordinate1, unrelatedDN, false);
      doCheckNetworkGroup(networkGroup, dn2, subordinate2, unrelatedDN, false);
      doCheckNetworkGroup(networkGroup, dn3, subordinate3, unrelatedDN, false);
    }

    // Deregister the network group
    networkGroup.deregister();
  }


  /**
   * This test checks that network groups are updated as appropriate when
   * backend base DNs are added or removed. When a new backend base DN is
   * added, the new suffix should be accessible for the route process - ie.
   * a workflow should be created and be a potential candidate for the route
   * process. Similarly, when a backend base DN is removed its associated
   * workflow should be removed; subsequently, any request targeting the
   * removed suffix should be rejected and a no such entry status code be
   * returned.
   */
  @Test
  public void testBackendBaseDNModification()
         throws Exception
  {
    String suffix  = "dc=example,dc=com";
    String suffix2 = "o=networkgroup suffix";
    String backendBaseDNName = "ds-cfg-base-dn";

    // Initialize a backend with a base entry.
    TestCaseUtils.clearJEBackend(true, "userRoot", suffix);

    // Create a client connection for the test.
    InternalClientConnection connection =
      InternalClientConnection.getRootConnection();

    // Check that suffix is accessible while suffix2 is not.
    searchEntry(connection, suffix,  true);
    searchEntry(connection, suffix2, false);

    // Add a new suffix in the backend and create a base entry for the
    // new suffix.
    String backendConfigDN = "ds-cfg-backend-id=userRoot," + DN_BACKEND_BASE;
    modifyAttribute(
        connection, backendConfigDN,
        ModificationType.ADD, backendBaseDNName, suffix2);
    addBaseEntry(connection, suffix2, "networkgroup suffix");

    // Both old and new suffix should be accessible.
    searchEntry(connection, suffix,  true);
    searchEntry(connection, suffix2, true);

    // Remove the new suffix...
    modifyAttribute(
        connection, backendConfigDN,
        ModificationType.DELETE, backendBaseDNName, suffix2);

    // ...and check that the removed suffix is no more accessible.
    searchEntry(connection, suffix,  true);
    searchEntry(connection, suffix2, false);

    // Replace the suffix with suffix2 in the backend
    modifyAttribute(
        connection, backendConfigDN,
        ModificationType.REPLACE, backendBaseDNName, suffix2);

    // Now none of the suffixes are accessible: this means the entries
    // under the old suffix are not moved to the new suffix.
    searchEntry(connection, suffix,  false);
    searchEntry(connection, suffix2, false);

    // Add a base entry for the new suffix
    addBaseEntry(connection, suffix2, "networkgroup suffix");

    // The new suffix is accessible while the old one is not.
    searchEntry(connection, suffix,  false);
    searchEntry(connection, suffix2, true);

    // Reset the configuration with previous suffix
    modifyAttribute(
        connection, backendConfigDN,
        ModificationType.REPLACE, backendBaseDNName, suffix);
  }


  /**
   * Tests the network group resource limits
   *
   * @param networkGroupID   the ID of the network group to register
   * @param workflowBaseDN1  the base DN of the first workflow node to register
   *                         in the network group
   */
  @Test (dataProvider = "DNSet_3", groups = "virtual")
  public void testNetworkGroupResourceLimits(
      String networkGroupID,
      DN     workflowBaseDN,
      int    priority,
      final int    maxConnections,
      final int    maxConnectionsFromSameClient,
      final int    maxOpsPerConn,
      final int    maxConcurrentOpsPerConn,
      final int    searchTimeLimit,
      final int    searchSizeLimit,
      final int    minSubstringLength
      )
      throws Exception
  {
    // Create and register the network group with the server.
    TestCaseUtils.dsconfig(
        "set-global-configuration-prop",
        "--set", "workflow-configuration-mode:manual");

    try
    {
      TestCaseUtils.dsconfig(
          "create-network-group",
          "--group-name", networkGroupID,
          "--set", "enabled:true",
          "--set", "priority:" + priority);

      try
      {
        // Ensure that the network group was created ok.
        NetworkGroup networkGroup = NetworkGroup.getNetworkGroup(networkGroupID);
        assertNotNull(networkGroup, "The network group does not seem to be registered.");

        TestCaseUtils.dsconfig(
            "create-network-group-qos-policy",
            "--group-name", networkGroupID,
            "--type", "resource-limits",
            "--set", "max-concurrent-ops-per-connection:" + maxConcurrentOpsPerConn,
            "--set", "max-connections:" + maxConnections,
            "--set", "max-connections-from-same-ip:" + maxConnectionsFromSameClient,
            "--set", "max-ops-per-connection:" + maxOpsPerConn,
            "--set", "min-substring-length:" + minSubstringLength,
            "--set", "size-limit:" + searchSizeLimit,
            "--set", "time-limit:" + searchTimeLimit + "s");

        // Check that the policy was created.
        ResourceLimitsPolicy policy = networkGroup.getNetworkGroupQOSPolicy(ResourceLimitsPolicy.class);
        assertNotNull(policy, "The policy was not registered.");

        // Check the resource limits are set properly.
        assertEquals(policy.getTimeLimit(), searchTimeLimit);
        assertEquals(policy.getSizeLimit(), searchSizeLimit);
        assertEquals(policy.getMinSubstring(), minSubstringLength);

        assertEquals(networkGroup.getTimeLimit(), searchTimeLimit);
        assertEquals(networkGroup.getSizeLimit(), searchSizeLimit);
        assertEquals(networkGroup.getMinSubstring(), minSubstringLength);

        TestCaseUtils.dsconfig(
            "delete-network-group-qos-policy",
            "--group-name", networkGroupID,
            "--policy-type", "resource-limits");

        // Check that the policy was removed.
        policy = networkGroup.getNetworkGroupQOSPolicy(ResourceLimitsPolicy.class);
        assertNull(policy, "The policy was not deregistered.");
      }
      finally
      {
        // The policy will get removed by this as well.
        TestCaseUtils.dsconfig("delete-network-group", "--group-name",
            networkGroupID);
      }
    }
    finally
    {
      TestCaseUtils.dsconfig(
          "set-global-configuration-prop",
          "--set", "workflow-configuration-mode:auto");
    }
  }



  /**
   * Tests the mechanism to attribute a network group to a client connection,
   * comparing the priority.
   */
  @Test (dataProvider = "DNSet_4", groups = "virtual")
  public void testNetworkGroupPriority(
      String ng1,
      DN dn1,
      int prio1,
      String ng2,
      DN dn2,
      int prio2
      )
      throws Exception
  {
    // Create and register the network group with the server.
    NetworkGroup networkGroup1 = new NetworkGroup(ng1);
    networkGroup1.register();
    networkGroup1.setNetworkGroupPriority(prio1);
    NetworkGroup networkGroup2 = new NetworkGroup(ng2);
    networkGroup2.register();
    networkGroup2.setNetworkGroupPriority(prio2);

    // Create a workflow -- the workflow ID is the string representation
    // of the workflow base DN.
    WorkflowElement<?> nullWE = null;
    WorkflowImpl workflow1 = new WorkflowImpl(
        dn1.toString(), dn1, null, nullWE);
    WorkflowImpl workflow2 = new WorkflowImpl(
        dn2.toString(), dn2, null, nullWE);

    // Register the workflow with the network group.
    networkGroup1.registerWorkflow(workflow1);
    networkGroup2.registerWorkflow(workflow2);

    // Create a new ClientConnection
    ClientConnection connection = new InternalClientConnection(DN.NULL_DN);

    // Find a networkGroup for this connection
    // As the network groups define no criteria, the highest priority
    // must be chosen
    NetworkGroup ng = NetworkGroup.findMatchingNetworkGroup(connection);
    if (prio1 < prio2) {
      assertEquals(ng, networkGroup1);
    } else {
      assertEquals(ng, networkGroup2);
    }

    // Clean the network group
    networkGroup1.deregisterWorkflow(workflow1.getWorkflowId());
    networkGroup1.deregister();
    networkGroup2.deregisterWorkflow(workflow2.getWorkflowId());
    networkGroup2.deregister();
  }


  /**
   * Tests the mechanism to attribute a network group to a client connection,
   * based on the authentication method.
   */
  @Test (dataProvider = "PrioritySet_0", groups = "virtual")
  public void testNetworkGroupAuthenticationMethodCriteria(
          int prio1,
          int prio2,
          int prio3)
    throws Exception
  {
    // Create a AuthMethodCriteria for anonymous connections
    AuthMethodConnectionCriteria authCriteria1 =
        new AuthMethodConnectionCriteria(Collections
            .singleton(AllowedAuthMethod.ANONYMOUS));

    // Create a AuthMethodCriteria for simple bind connections
    AuthMethodConnectionCriteria authCriteria2 =
        new AuthMethodConnectionCriteria(Collections
            .singleton(AllowedAuthMethod.SIMPLE));

    // Create a AuthMethodCriteria for sasl connections
    AuthMethodConnectionCriteria authCriteria3 =
        new AuthMethodConnectionCriteria(Collections
            .singleton(AllowedAuthMethod.SASL));


    // Create and register the network group with the server.
    NetworkGroup networkGroup1 = new NetworkGroup("anonymous_group");
    networkGroup1.register();
    networkGroup1.setConnectionCriteria(authCriteria1);
    networkGroup1.setNetworkGroupPriority(prio1);
    NetworkGroup networkGroup2 = new NetworkGroup("simplebind_group");
    networkGroup2.register();
    networkGroup2.setConnectionCriteria(authCriteria2);
    networkGroup2.setNetworkGroupPriority(prio2);
    NetworkGroup networkGroup3 = new NetworkGroup("sasl_group");
    networkGroup3.register();
    networkGroup3.setConnectionCriteria(authCriteria3);
    networkGroup3.setNetworkGroupPriority(prio3);

    // Create a new client connection, with anonymous authentication
    ClientConnection connection1 = new InternalClientConnection(DN.NULL_DN);
    NetworkGroup ng = NetworkGroup.findMatchingNetworkGroup(connection1);
    assertEquals(ng, networkGroup1);

    // Use simple bind on this connection
    Entry userEntry = DirectoryServer.getEntry(
            DN.decode("cn=Directory Manager, cn=Root DNs, cn=config"));
    ByteString password = ByteString.valueOf("password");
    ClientConnection connection2 = new InternalClientConnection(
          new AuthenticationInfo(userEntry, password, true));
    ng = NetworkGroup.findMatchingNetworkGroup(connection2);
    assertEquals(ng, networkGroup2);

    // Use SASL on this connection
    ClientConnection connection3 = new InternalClientConnection(
            new AuthenticationInfo(userEntry, "external", true));
    ng = NetworkGroup.findMatchingNetworkGroup(connection3);
    assertEquals(ng, networkGroup3);

    // Clean the network group
    networkGroup1.deregister();
    networkGroup2.deregister();
    networkGroup3.deregister();
  }


  /**
   * Tests the mechanism to attribute a network group to a client connection,
   * based on the bind dn filter.
   */
  @Test (dataProvider = "BindFilterSet_0", groups = "virtual")
  public void testNetworkGroupBindDnCriteria(
          String bindDnFilter,
          boolean match)
    throws Exception
  {
    // Create a BindDnFilterCriteria
    BindDNConnectionCriteria bindCriteria =
        BindDNConnectionCriteria.decode(Collections
            .singleton(bindDnFilter));

    // Create and register the network group with the server.
    NetworkGroup networkGroup = new NetworkGroup("bindfilter_group");
    networkGroup.register();
    networkGroup.setConnectionCriteria(bindCriteria);

    NetworkGroup defaultNg = NetworkGroup.getDefaultNetworkGroup();

    // Create a new client connection, with anonymous authentication
    // It should match the default network group
    // as it has no bind information
    ClientConnection connection1 = new InternalClientConnection(DN.NULL_DN);
    NetworkGroup ng = NetworkGroup.findMatchingNetworkGroup(connection1);
    assertEquals(ng, defaultNg);

    // Use simple bind on this connection
    Entry userEntry = DirectoryServer.getEntry(
            DN.decode("cn=Directory Manager, cn=Root DNs, cn=config"));
    ByteString password = ByteString.valueOf("password");
    ClientConnection connection2 = new InternalClientConnection(
          new AuthenticationInfo(userEntry, password, true));
    ng = NetworkGroup.findMatchingNetworkGroup(connection2);
    if (match) {
      assertEquals(ng, networkGroup);
    } else {
      assertEquals(ng, defaultNg);
    }

    // Use SASL on this connection
    ClientConnection connection3 = new InternalClientConnection(
            new AuthenticationInfo(userEntry, "external", true));
    ng = NetworkGroup.findMatchingNetworkGroup(connection3);
    if (match) {
      assertEquals(ng, networkGroup);
    } else {
      assertEquals(ng, defaultNg);
    }

    // Clean the network group
    networkGroup.deregister();
  }


  /**
   * Tests the mechanism to attribute a network group to a client connection,
   * based on the bind dn filter.
   */
  @Test (groups = "virtual")
  public void testNetworkGroupSecurityCriteria()
    throws Exception
  {
    // Create a SecurityCriteria
    SecurityConnectionCriteria secCriteria =
        SecurityConnectionCriteria.SECURITY_REQUIRED;

    // Create and register the network group with the server.
    NetworkGroup networkGroup = new NetworkGroup("secured_group");
    networkGroup.register();
    networkGroup.setConnectionCriteria(secCriteria);

    NetworkGroup defaultNg = NetworkGroup.getDefaultNetworkGroup();

    // Create a new client connection, with anonymous authentication
    // It should match the secured group as internal connections
    // are secured
    ClientConnection connection1 = new InternalClientConnection(DN.NULL_DN);
    NetworkGroup ng = NetworkGroup.findMatchingNetworkGroup(connection1);
    assertEquals(ng, networkGroup);

    // now change the criteria (security not mandatory)
    secCriteria = SecurityConnectionCriteria.SECURITY_NOT_REQUIRED;
    networkGroup.setConnectionCriteria(secCriteria);

    // connection1 should match the networkGroup, even though it is not
    // secured
    ng = NetworkGroup.findMatchingNetworkGroup(connection1);
    assertEquals(ng, networkGroup);

    // Clean the network group
    networkGroup.deregister();
  }


  /**
   * Searches an entry on a given connection.
   *
   * @param connection    the connection to use for the search request
   * @param baseDN        the request base DN string
   * @param shouldExist   if true the searched entry is expected to be found
   */
  private void searchEntry(
      InternalClientConnection connection,
      String  baseDN,
      boolean shouldExist
      ) throws Exception
  {
    SearchOperation search = connection.processSearch(
        DN.decode(baseDN),
        SearchScope.BASE_OBJECT,
        LDAPFilter.decode("(objectClass=*)").toSearchFilter());

    // Compare the result code with the expected one
    ResultCode resultCode = search.getResultCode();
    if (shouldExist)
    {
      assertEquals(resultCode, ResultCode.SUCCESS);
    }
    else
    {
      assertEquals(resultCode, ResultCode.NO_SUCH_OBJECT);
    }
  }


  /**
   * Creates a base entry for the given suffix.
   *
   * @param connection  the connection to use for the add request
   * @param suffix      the suffix for which the base entry is to be created
   */
  private void addBaseEntry(
      InternalClientConnection connection,
      String  suffix,
      String  namingAttribute
      ) throws Exception
  {
    Entry e = TestCaseUtils.makeEntry(
        "dn: " + suffix,
        "objectClass: top",
        "objectClass: organization",
        "o: " + namingAttribute);

   AddOperation addOperation = connection.processAdd(
       e.getDN(),
       e.getObjectClasses(),
       e.getUserAttributes(),
       e.getOperationalAttributes());

   assertEquals(addOperation.getResultCode(), ResultCode.SUCCESS);
  }


  /**
   * Adds/Deletes/Replaces an attribute in a given entry.
   *
   * @param connection      the connection to use for the modify request
   * @param baseDN          the request base DN string
   * @param modType         the modification type (add/delete/replace)
   * @param attributeName   the name  of the attribute to add/delete/replace
   * @param attributeValue  the value of the attribute to add/delete/replace
   */
  private void modifyAttribute(
      InternalClientConnection connection,
      String  baseDN,
      ModificationType modType,
      String  attributeName,
      String  attributeValue
      ) throws Exception
  {
    ArrayList<Modification> mods = new ArrayList<Modification>();
    Attribute attributeToModify =
      Attributes.create(attributeName, attributeValue);
    mods.add(new Modification(modType, attributeToModify));
    ModifyOperation modifyOperation = connection.processModify(
        DN.decode(baseDN), mods);
    assertEquals(modifyOperation.getResultCode(), ResultCode.SUCCESS);
  }


  /**
   * Checks the DN routing through a network group.
   *
   * @param networkGroup    the network group to use for the check
   * @param dnToSearch      the DN of a workflow in the network group; may
   *                        be null
   * @param dnSubordinate   a subordinate of dnToSearch
   * @param unrelatedDN     a DN with no hierarchical relationship with
   *                        any of the DNs above, may be null
   * @param shouldExist     true if we are supposed to find a workflow for
   *                        dnToSearch
   */
  private void doCheckNetworkGroup(
      NetworkGroup networkGroup,
      DN           dnToSearch,
      DN           dnSubordinate,
      DN           unrelatedDN,
      boolean      shouldExist
      )
  {
    if (dnToSearch == null)
    {
      return;
    }

    // Let's retrieve the workflow that maps best the dnToSearch
    Workflow workflow = networkGroup.getWorkflowCandidate(dnToSearch);
    if (shouldExist)
    {
      assertNotNull(workflow);
    }
    else
    {
      assertNull(workflow);
    }

    // let's retrieve the workflow that handles the DN subordinate:
    // it should be the same than the one for dnToSearch
    if (dnSubordinate != null)
    {
       Workflow workflow2 = networkGroup.getWorkflowCandidate(dnSubordinate);
       assertEquals(workflow2, workflow);
    }

    // Check that the unrelatedDN is not handled by any workflow
    if (unrelatedDN != null)
    {
      Workflow unrelatedWorkflow =
        networkGroup.getWorkflowCandidate(unrelatedDN);
      assertNull(unrelatedWorkflow);
    }
  }


  /**
   * Creates a workflow and register it with a network group.
   *
   * @param networkGroup     a network group to register the workflow with
   * @param workflowBaseDN   the base DN of the workflow to register; may be
   *                         null
   * @throws  DirectoryException  If the workflow ID for the provided
   *                              workflow conflicts with the workflow
   *                              ID of an existing workflow.
   */
  private WorkflowImpl createAndRegisterWorkflow(
      NetworkGroup networkGroup,
      DN           workflowBaseDN
      ) throws DirectoryException
  {
    assertNotNull(networkGroup);

    if (workflowBaseDN == null)
    {
      return null;
    }

    // Create a workflow with no task inside. The workflow identifier
    // is the a string representation of the workflow base DN.
    WorkflowElement<?> rootWE = null;
    String workflowId = workflowBaseDN.toString();
    WorkflowImpl workflow = new WorkflowImpl(
        workflowId, workflowBaseDN, null, rootWE);
    assertNotNull(workflow);

    // Register the workflow with the network group.
    networkGroup.registerWorkflow(workflow);

    return workflow;
  }


  /**
   * Prints a text to System.out.
   */
  private void write(String msg)
  {
    System.out.print(msg);
  }


  /**
   * Prints a text to System.out.
   */
  private void writeln(String msg)
  {
    write(msg + "\n");
  }



  /**
   * Dump the network group info to the console.
   */
  private void dump(NetworkGroup networkGroup, String prompt)
  {
    final boolean doDump = false;

    if (doDump)
    {
      StringBuilder sb = networkGroup.toString(prompt);
      writeln(sb.toString());
    }
  }

}
