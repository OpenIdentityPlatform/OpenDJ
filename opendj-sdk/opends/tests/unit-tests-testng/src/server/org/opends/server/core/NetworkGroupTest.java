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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.core;


import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import org.opends.server.TestCaseUtils;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.workflowelement.WorkflowElement;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;


/**
 * This set of tests checks that network groups are properly created.
 */
public class NetworkGroupTest
{
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
   * Provides a single DN to search a workflow in a network group.
   * 
   * Each set of DNs is composed of:
   * - one baseDN
   * - one subordinateDN
   * - a boolean telling whether we expect to find a workflow for the baseDN
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
        { dnRootDSE,  null,                 true  },
        { dnConfig,   dnSubordinateConfig,  true  },
        { dnMonitor,  dnSubordinateMonitor, true  },
        { dnTasks,    dnSubordinateTasks,   true  },
        { dnSchema,   null,                 true  },
        { dnBackups,  null,                 true  },
        { dnDummy,    null,                 false },
    };

    return myData;
  }

  /**
   * Provides information to create a network group.
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


  //===========================================================================
  //
  //                        T E S T   C A S E S
  //
  //===========================================================================

  /**
   * Gets a workflow candidate in the default network group.
   *
   *  @param dnToSearch     the DN of a workflow in the default network group
   *  @param dnSubordinate  a subordinate of dnToSearch
   *  @param exists         true if we are supposed to find a workflow for
   *                        dnToSearch
   */
  @Test (dataProvider = "DNSet_1", groups = "virtual")
  public void checkDefaultNetworkGroup(
      DN      dnToSearch,
      DN      dnSubordinate,
      boolean exists
      )
  {
    // let's get the default network group (it should always exist)
    NetworkGroup defaultNG = NetworkGroup.getDefaultNetworkGroup();
    assertNotNull(defaultNG);

    // let's check the routing through the network group
    doCheckNetworkGroup(defaultNG, dnToSearch, dnSubordinate, exists);

    // Dump info
    StringBuffer sb = defaultNG.toString("defaultNetworkGroup> ");
    writeln(sb.toString());
  }


  /**
   * Checks the DN routing through a network group.
   *
   * @param networkGroup    the network group to use for the check
   * @param dnToSearch      the DN of a workflow in the network group
   * @param dnSubordinate   a subordinate of dnToSearch
   * @param shouldExist     true if we are supposed to find a workflow for
   *                        dnToSearch
   */
  private void doCheckNetworkGroup(
      NetworkGroup networkGroup,
      DN           dnToSearch,
      DN           dnSubordinate,
      boolean      shouldExist
      )
  {
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
  }


  /**
   * Creates a network group with several workflows inside. Routing operation
   * is done through the default root DSE workflow (which is automatically
   * created by the network group class).
   *
   * @param dn1           the DN for the 1st workflow
   * @param dn2           the DN for the 2nd workflow
   * @param dn3           the DN for the 3rd workflow
   * @param subordinate1  the subordinate DN for the 1st workflow
   * @param subordinate2  the subordinate DN for the 2nd workflow
   * @param subordinate3  the subordinate DN for the 3rd workflow
   * @param unrelatedDN   a DN with no hierarchical relationship with
   *                      any of the DNs above
   */
  @Test (dataProvider = "DNSet_2", groups = "virtual")
  public void createNetworkGroup2(
      DN dn1,
      DN dn2,
      DN dn3,
      DN subordinate1,
      DN subordinate2,
      DN subordinate3,
      DN unrelatedDN
      )
  {
    String networkGroupName = "Network Group for test2";

    // Create the network group
    NetworkGroup ng = new NetworkGroup(networkGroupName);
    assertNotNull(ng);

    // Register the network group
    NetworkGroup.registerNetworkGroup(ng);

    // Create and register workflow 1
    if (dn1 != null)
    {
      createAndRegisterWorkflow(ng, dn1, subordinate1, unrelatedDN);
    }

    // Create and register workflow 2
    if (dn2 != null)
    {
      createAndRegisterWorkflow(ng, dn2, subordinate2, unrelatedDN);
    }

    // Create and register workflow 3
    if (dn3 != null)
    {
      createAndRegisterWorkflow(ng, dn3, subordinate3, unrelatedDN);
    }

    // Dump info
    StringBuffer sb = ng.toString("createNetworkGroup2(" + dn1 + ")> ");
    writeln(sb.toString());

    // Dump info of defaultNetworkGroup
    StringBuffer sb2 =
       NetworkGroup.getDefaultNetworkGroup().toString("defaultNetworkGroup> ");
    writeln(sb2.toString());
    
    // Deregister the workflow1...
    deregisterWorkflow(ng, dn1, subordinate1, unrelatedDN);
    
    // ... and dump info again
    sb = ng.toString("DEREGISTER Workflow> ");
    writeln(sb.toString());

    // dump info of defaultNetworkGroup
    sb2 = NetworkGroup.getDefaultNetworkGroup().toString(
        "DEREGISTER defaultNetworkGroup> "
        );
    writeln(sb2.toString());
  }


  /**
   * Creates a workflow and register it with a network group.
   *
   * @param networkGroup     a network group to register the workflow with
   * @param workflowBaseDN   the base DN of the workflow to register
   * @param subordinateDN    subordinate DN of the workflowBaseDN
   * @param unrelatedDN      a DN with no hierarchical relationship with
   *                         any of the base DN above
   */
  private void createAndRegisterWorkflow(
      NetworkGroup networkGroup,
      DN           workflowBaseDN,
      DN           subordinateDN,
      DN           unrelatedDN
      )
  {
    assertNotNull(networkGroup);

    // Create and register a workflow (no task in the workflow)
    WorkflowElement rootWE = null;
    WorkflowImpl realWorkflow = new WorkflowImpl(workflowBaseDN, rootWE);
    assertNotNull(realWorkflow);

    // Register the workflow with the network group.
    networkGroup.registerWorkflow(realWorkflow);

    // Now check that workflow is accessible through the network group
    Workflow electedWorkflow;
    electedWorkflow = networkGroup.getWorkflowCandidate(workflowBaseDN);
    assertEquals(electedWorkflow.getBaseDN(), workflowBaseDN);

    electedWorkflow = networkGroup.getWorkflowCandidate(subordinateDN);
    assertEquals(electedWorkflow.getBaseDN(), workflowBaseDN);

    // Check that the unrelatedDN is not handled by the workflow
    Workflow unrelatedWorkflow =
      networkGroup.getWorkflowCandidate(unrelatedDN);
    assertNull(unrelatedWorkflow);
  }
  
  
  /**
   * Deregisters a workflow with a network group. The workflow to
   * deregister is identified by its baseDN.
   *
   * @param networkGroup     a network group that contains the workflow
   *                         to deregister
   * @param workflowBaseDN   the base DN of the workflow to deregister
   * @param subordinateDN    subordinate DN of the workflowBaseDN
   * @param unrelatedDN      a DN with no hierarchical relationship with
   *                         any of the base DN above
   */
  private void deregisterWorkflow(
      NetworkGroup networkGroup,
      DN           workflowBaseDN,
      DN           subordinateDN,
      DN           unrelatedDN
      )
  {
    assertNotNull(networkGroup);

    // get the workflow in the network group
    Workflow workflow = networkGroup.getWorkflowCandidate(workflowBaseDN);
    if (workflow == null)
    {
      // found no workflow
      return;
    }

    // Deregister the workflow with the network group
    networkGroup.deregisterWorkflow(workflow.getBaseDN());

    // Check that the workflow is no more accessible through the network
    // group
    Workflow electedWorkflow;
    electedWorkflow = networkGroup.getWorkflowCandidate(workflowBaseDN);
    assertNull(electedWorkflow);
    electedWorkflow = networkGroup.getWorkflowCandidate(subordinateDN);
    assertNull(electedWorkflow);
  }
  
  
  /**
   * Prints a text to System.out.
   */
  private void write(String msg)
  {
    boolean dumpInfo = true;

    if (dumpInfo)
    {
      System.out.print(msg);
    }
  }
  
  
  /**
   * Prints a text to System.out.
   */
  private void writeln(String msg)
  {
    write(msg + "\n");
  }
}
