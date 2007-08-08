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


import static org.opends.server.messages.CoreMessages.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertNotNull;

import java.util.ArrayList;

import org.opends.server.TestCaseUtils;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;
import org.opends.server.util.UtilTestCase;
import org.opends.server.workflowelement.WorkflowElement;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;


/**
 * This set of tests checks that workflow topology is properly created.
 * Topology is based on DN hierarchical relationship. Once the topolofy
 * is created, we check that the route operation returns the best workflow
 * candidate for a given request base DN.
 */
public class WorkflowTopologyTest extends UtilTestCase
{
  //===========================================================================
  //
  //                      B E F O R E    C L A S S
  //
  //===========================================================================

  /**
   * Set up the environment for performing the tests in this suite.
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
   * Provide a set of DNs to create a single workflow. Each set of DNs contains
   * one baseDN for the new workflow to be created, one subordinateDN and one
   * unrelatedDN which has no hierarchical relationship with the baseDN.
   *
   *           baseDN + subordinateDN + unrelatedDN
   *
   * Sample scenario for a test using this set of DNs:
   * 1) creating a workflow with the baseDN
   * 2) trying to fetch the workflow using the subordinateDN
   * 3) checking that the workflow cannot be candidate to route a request
   *    with the unrelatedDN
   *
   * @return set of DNs
   * @throws Exception  when DN.decode fails
   */
  @DataProvider(name = "DNSet_1")
  public Object[][] initDNSet_1()
    throws Exception
  {
    DN dnNull         = null;
    DN baseDN1        = null;
    DN subordinateDN1 = null;
    DN unrelatedDN    = null;

    try
    {
      dnNull         = DN.decode ("");
      baseDN1        = DN.decode ("o=test");
      subordinateDN1 = DN.decode ("ou=subtest,o=test");
      unrelatedDN    = DN.decode ("o=dummy");
    }
    catch (DirectoryException de)
    {
      throw de;
    }

    // Sets of DNs
    Object[][] myData =
    {
        // SET 1
        // baseDN is null suffix. There is no unrelatedDN because any DN
        // is descendant of the null suffix.
        {
        dnNull,
        subordinateDN1,
        null
        },

        // SET 2
        // One baseDN, one subordinateDN and one unrelatedDN
        {
        baseDN1,
        subordinateDN1,
        unrelatedDN
        },
    };

    return myData;
  }


  /**
   * Provide a set of DNs to create a topology of 3 workflows, and the 3
   * workflows are in the same hierarchy of DNs: baseDN1 is the superior
   * of baseDN2 which is the superior of baseDN3:
   *
   *           baseDN1 + subordinateDN1
   *              |
   *           baseDN2 + subordinateDN2    +   unrelatedDN
   *              |
   *           baseDN3 + subordinateDN3
   *
   * Each baseDN has a subordinateDN: the workflow with the baseDN should be
   * the candidate for a request when request base DN is the subordinateDN.
   *
   * There is an unrelatedDN which has no hierarchical relationship with any
   * of the baseDNs. The unrelatedDN is used to check that none of the
   * workflow can be candidate for the route when a request is using the
   * unrelatedDN.
   *
   * @return set of DNs
   * @throws Exception  when DN.decode fails
   */
  @DataProvider(name = "DNSet_2")
  public Object[][] initDNSet_2()
    throws Exception
  {
    DN   unrelatedDN    = null;
    int  nbElem         = 3;
    DN[] baseDNs        = new DN[nbElem];
    DN[] subordinateDNs = new DN[nbElem];
    DN   rootDSE        = null;

    // Create the topology of DNs:
    //
    //    o=dummy            ou=test1 (==> W1)
    //                          |
    //                          |
    //           +--------------+
    //           |              |
    //           |              |
    //    ou=subordinate1   ou=test2 (==> W2)
    //                          |
    //                          |
    //                          +--------------------+
    //                          |                    |
    //                          |                    |
    //                      ou=test3 (==> W3)   ou=subordinate2
    //                          |
    //                          |
    //           +--------------+
    //           |              |
    //           |              |
    //    ou=subordinate3
    try
    {
      String suffix         = "ou=test1";
      String baseDN1        = suffix;
      String baseDN2        = "ou=test2,"        + baseDN1;
      String baseDN3        = "ou=test3,"        + baseDN2;
      String subordinateDN1 = "ou=subordinate1," + baseDN1;
      String subordinateDN2 = "ou=subordinate2," + baseDN2;
      String subordinateDN3 = "ou=subordinate3," + baseDN3;

      int i = 0;
      baseDNs[i]        = DN.decode (baseDN1);
      subordinateDNs[i] = DN.decode (subordinateDN1);
      i++;
      baseDNs[i]        = DN.decode (baseDN2);
      subordinateDNs[i] = DN.decode (subordinateDN2);
      i++;
      baseDNs[i]        = DN.decode (baseDN3);
      subordinateDNs[i] = DN.decode (subordinateDN3);

      unrelatedDN = DN.decode ("o=dummy");
      rootDSE     = DN.decode ("");
    }
    catch (DirectoryException de)
    {
      throw de;
    }

    // Sets of DNs
    Object[][] myData =
    {
        // SET 1
        {
        baseDNs[0], baseDNs[1], baseDNs[2],
        subordinateDNs[0], subordinateDNs[1], subordinateDNs[2],
        unrelatedDN
        },

        // SET 2
        // Same than SET 1, but the first baseDN is the null suffix DN.
        // Hence there is no unrelatedDN as any DN is a subordinate of
        // the null suffix.
        {
        rootDSE, baseDNs[1], baseDNs[2],
        subordinateDNs[0], subordinateDNs[1], subordinateDNs[2],
        null
        }
    };

    return myData;
  }


  /**
   * Provide a set of DNs to create the following topology:
   *
   *                      [W1]
   *                    baseDN1
   *                       |
   *             +---------+--------+
   *             |                  |
   *             |                  |
   *      subordinateDN1     +------+------+
   *                         |             |
   *                        [W2]          [W3]
   *                      baseDN2        baseDN3
   *                         |             |
   *                         |             |
   *                 subordinateDN2    subordinateDN3
   *
   *
   * @return set of DNs
   * @throws Exception  when DN.decode fails
   */
  @DataProvider(name = "DNSet_3")
  public Object[][] initDNSet_3()
    throws Exception
  {
    DN   unrelatedDN    = null;
    int  nbElem         = 3;
    DN[] baseDNs        = new DN[nbElem];
    DN[] subordinateDNs = new DN[nbElem];
    DN   rootDSE        = null;

    // Create the topology of DNs:
    //
    //    o=dummy       dc=example,dc=com
    //                          |
    //                          |
    //           +--------------+-----------------+
    //           |              |                 |
    //    ou=subordinate1   ou=group          ou=people
    //                          |                 |
    //                          |                 |
    //                   ou=subordinate2   ou=subordinate3
    try
    {
      String suffix         = "dc=example,dc=com";
      String baseDN1        = suffix;
      String baseDN2        = "ou=group,"        + baseDN1;
      String baseDN3        = "ou=people,"       + baseDN1;
      String subordinateDN1 = "ou=subordinate1," + baseDN1;
      String subordinateDN2 = "ou=subordinate2," + baseDN2;
      String subordinateDN3 = "ou=subordinate3," + baseDN3;

      int i = 0;
      baseDNs[i]        = DN.decode (baseDN1);
      subordinateDNs[i] = DN.decode (subordinateDN1);
      i++;
      baseDNs[i]        = DN.decode (baseDN2);
      subordinateDNs[i] = DN.decode (subordinateDN2);
      i++;
      baseDNs[i]        = DN.decode (baseDN3);
      subordinateDNs[i] = DN.decode (subordinateDN3);

      unrelatedDN = DN.decode ("o=dummy");
      rootDSE     = DN.decode ("");
    }
    catch (DirectoryException de)
    {
      throw de;
    }

    // Sets of DNs
    Object[][] myData =
    {
        // SET 1
        //
        //    o=dummy       dc=example,dc=com
        //                          |
        //                          |
        //           +--------------+-----------------+
        //           |              |                 |
        //    ou=subordinate1   ou=group          ou=people
        //                          |                 |
        //                          |                 |
        //                   ou=subordinate2   ou=subordinate3
        {
        baseDNs[0],
        baseDNs[1],
        baseDNs[2],
        subordinateDNs[0],
        subordinateDNs[1],
        subordinateDNs[2],
        unrelatedDN
        },

        // SET 2
        //
        // The top baseDN is the null suffix. Hence there is no unrelatedDN
        // as any DN is a subordinate of the null suffix.
        //
        //                         "" (rootDSE)
        //                          |
        //                          |
        //           +--------------+-----------------+
        //           |              |                 |
        //    ou=subordinate1   ou=group          ou=people
        //                          |                 |
        //                          |                 |
        //                   ou=subordinate2   ou=subordinate3
        {
        rootDSE,
        baseDNs[1],
        baseDNs[2],
        subordinateDNs[0],
        subordinateDNs[1],
        subordinateDNs[2],
        null
        }
    };

    return myData;
  }


  /**
   * Provide a set of result codes to test the elaboration of the global
   * result code.
   *
   * @return set of result codes to test
   * @throws Exception
   */
  @DataProvider(name = "ResultCodes_1")
  public Object[][] initResultCodes_1()
  {
    // Short names...
    ResultCode rcSuccess      = ResultCode.SUCCESS;
    ResultCode rcNoSuchObject = ResultCode.NO_SUCH_OBJECT;
    ResultCode rcReferral     = ResultCode.REFERRAL;
    ResultCode rcOther        = ResultCode.ALIAS_PROBLEM;
    ResultCode rcOther2       = ResultCode.AUTHORIZATION_DENIED;

    // Sets of DNs
    Object[][] myData =
    {
        // received        current          expected
        // result code     result code      result code
        { rcSuccess,       rcNoSuchObject,  rcSuccess  },
        { rcReferral,      rcSuccess,       rcSuccess  },
        { rcSuccess,       rcOther,         rcOther    },
        { rcNoSuchObject,  rcSuccess,       rcSuccess  },
        { rcNoSuchObject,  rcReferral,      rcReferral },
        { rcNoSuchObject,  rcOther,         rcOther    },
        { rcReferral,      rcSuccess,       rcSuccess  },
        { rcReferral,      rcReferral,      rcSuccess  },
        { rcReferral,      rcNoSuchObject,  rcReferral },
        { rcReferral,      rcOther,         rcOther    },
        { rcOther,         rcSuccess,       rcOther    },
        { rcOther,         rcReferral,      rcOther    },
        { rcOther,         rcNoSuchObject,  rcOther    },
        { rcOther,         rcOther2,        rcOther2   }
    };

    return myData;
  }


  //===========================================================================
  //
  //                        T E S T   C A S E S
  //
  //===========================================================================

  /**
   * Create a single workflow using a baseDN. There is no workflow element
   * in the workflow nor in the DIT attached to the workflow. Once the
   * workflow has been created, we are trying to fetch it using the baseDN
   * and/or the subordinateDN and/or the unrelatedDN.
   *
   * @param baseDN         baseDN of the workflow to create
   * @param subordinateDN  a subordinate DN of baseDN
   * @param dummyDN        a DN not registered in any workflow
   */
  @Test (dataProvider = "DNSet_1", groups = "virtual")
  public void createWorkflow_basic(
      DN baseDN,
      DN subordinateDN,
      DN dummyDN
      )
  {
    // create a DIT set with the baseDN (no workflow element in the DIT).
    WorkflowElement nullWE = null;
    WorkflowImpl workflow =
      new WorkflowImpl (baseDN.toString(), baseDN, nullWE);

    // Create a worflow with the dit, no pre/post-workflow element.
    WorkflowTopologyNode workflowNode =
      new WorkflowTopologyNode (workflow, null, null);

    // The base DN in the workflow should match baseDN parameter
    DN workflowBaseDN = workflowNode.getBaseDN();
    assertEquals (workflowBaseDN, baseDN);

    // There should be no parent workflow.
    WorkflowTopologyNode parent = workflowNode.getParent();
    assertEquals (parent, null);

    // The workflow should handle the baseDN and subordinateDN.
    DN readBaseDN = null;
    readBaseDN = workflowNode.getParentBaseDN (baseDN);
    assertEquals (readBaseDN, baseDN);
    readBaseDN = workflowNode.getParentBaseDN (subordinateDN);
    assertEquals (readBaseDN, baseDN);

    // The workflow should not handle the dummyDN.
    if (dummyDN != null)
    {
      readBaseDN = workflowNode.getParentBaseDN (dummyDN);
      assertNull (readBaseDN);
    }

  } // createWorkflow_basic


  /**
   * Create a topology with 2 workflows. The test case contains creation
   * of clean topologies as well as bad topologies (same baseDN for the parent
   * and subordinate, subordinate above parent...).
   *
   *                 W1 (baseDN)
   *                 |
   *                 |
   *                 W2 (subordinateDN)
   *
   * There is no worklfow element attached to the DITs.
   *
   * @param baseDn         base DN for the parent workflow (W1)
   * @param subordinateDN  base DN for the subordinate workflow (W2)
   * @param unrelatedDN    base DN with no hierarchical relationship with any
   *                       of the two baseDNs; parameter may be null
   */
  @Test (dataProvider = "DNSet_1", groups = "virtual")
  public void createWorkflow_simpleTopology1(
      DN baseDN,
      DN subordinateDN,
      DN unrelatedDN
      )
  {
    // Create one DIT set for baseDN and one DIT set for subordinateDN
    // (no workflow element in any DIT). Create a dummy DIT as well using
    // the unrelatedDN.
    WorkflowImpl workflow          = null;
    WorkflowImpl subWorkflow       = null;
    WorkflowImpl unrelatedWorkflow = null;
    {
      WorkflowElement nullWE = null;
      workflow = new WorkflowImpl (baseDN.toString(), baseDN, nullWE);
      subWorkflow = new WorkflowImpl (subordinateDN.toString(), subordinateDN, nullWE);
      if (unrelatedDN != null)
      {
        unrelatedWorkflow = new WorkflowImpl (unrelatedDN.toString(), unrelatedDN, nullWE);
      }
    }

    // Create a worflow for each dit, no pre/post-workflow element
    WorkflowTopologyNode w1    = new WorkflowTopologyNode (workflow, null, null);
    WorkflowTopologyNode w1bis = new WorkflowTopologyNode (workflow, null, null);
    WorkflowTopologyNode w2    = new WorkflowTopologyNode (subWorkflow, null, null);

    WorkflowTopologyNode w3 = null;
    if (unrelatedWorkflow != null)
    {
       w3 = new WorkflowTopologyNode (unrelatedWorkflow, null, null);
    }

    // insert status
    boolean insert;

    // Try to create a topology with unrelated workflows:
    //
    //         w1 (baseDN)
    //         |
    //         w3 (dnDummy)
    //
    // Insert should be rejected
    if (w3 != null)
    {
      insert = w1.insertSubordinate (w3);
      assertEquals (insert, false);
    }

    // Try to create a topology with the very same workflow:
    //
    //         w1 (baseDN)
    //         |
    //         w1 (baseDN)
    //
    // Insert should be rejected
    insert = w1.insertSubordinate (w1);
    assertEquals (insert, false);

    // Try to create a topology with a workflow whose baseDN is the same than
    // parent baseDN:
    //
    //         w1    (baseDN)
    //         |
    //         w1bis (baseDN)
    //
    // Insert should be rejected
    insert = w1.insertSubordinate (w1bis);
    assertEquals (insert, false);

    // Try to create a topology where subordinate is above the parent:
    //
    //         w2 (subordinateDN)
    //         |
    //         w1 (baseDN)
    //
    // Insert should be rejected
    insert = w2.insertSubordinate (w1);
    assertEquals (insert, false);

    // Try to create a clean topology:
    //
    //         w1 (baseDN)
    //         |
    //         w2 (subordinateDN)
    //
    // Expected results:
    //
    // - insert should be working
    insert = w1.insertSubordinate (w2);
    assertEquals (insert, true);

    // - w1 should be the parent of w2
    WorkflowTopologyNode parent1 = w2.getParent();
    assertEquals (parent1, w1);

    // - w2 should be in the w1 subordinate list
    ArrayList<WorkflowTopologyNode> subordinates1 = w1.getSubordinates();
    assertEquals (subordinates1.size(), 1);
    assertEquals (subordinates1.get(0), w2);

    // - w2 should have no subordinate
    ArrayList<WorkflowTopologyNode> subordinates2 = w2.getSubordinates();
    assertEquals (subordinates2.size(), 0);

  } // createWorkflow_simpleTopology1


  /**
   * Create a topology with 3 workflows and check that we are getting the
   * right workflow for a given DN. Then remove a workflow in the chain and
   * check that topology is properly updated in term of parent/subordinate
   * links.
   *
   *                 W1 (baseDN1)
   *                 |
   *                 +----> subordinateDN1
   *                 |
   *                 W2 (baseDN2)
   *                 |
   *                 +----> subordinateDN2
   *                 |
   *                 W3 (baseDN3)
   *                 |
   *                 +----> subordinateDN3
   *                 |
   *
   * There is no worklfow element attached to the DITs.
   *
   * @param baseDn1         base DN for the top workflow (W1)
   * @param baseDN2         base DN for the first subordinate workflow (W2)
   * @param baseDN3         base DN for the second subordinate workflow (W3)
   * @param subordinateDN1  subordinate DN of baseDN1
   * @param subordinateDN2  subordinate DN of baseDN2
   * @param subordinateDN3  subordinate DN of baseDN3
   * @param unrelatedDN     a DN not registered in any workflow
   */
  @Test (dataProvider = "DNSet_2", groups = "virtual")
  public void createWorkflow_simpleTopology2(
      DN baseDN1,
      DN baseDN2,
      DN baseDN3,
      DN subordinateDN1,
      DN subordinateDN2,
      DN subordinateDN3,
      DN unrelatedDN
      )
  {
    // Create a worflow for each baseDN, no pre/post-workflow element
    WorkflowTopologyNode w1;
    WorkflowTopologyNode w2;
    WorkflowTopologyNode w3;
    {
      // create DITs with the given baseDNs with no workflow element.
      WorkflowImpl workflow1;
      WorkflowImpl workflow2;
      WorkflowImpl workflow3;
      {
        WorkflowElement nullWE = null;
        workflow1 = new WorkflowImpl (baseDN1.toString(), baseDN1, nullWE);
        workflow2 = new WorkflowImpl (baseDN2.toString(), baseDN2, nullWE);
        workflow3 = new WorkflowImpl (baseDN3.toString(), baseDN3, nullWE);
      }

      w1 = new WorkflowTopologyNode (workflow1, null, null);
      w2 = new WorkflowTopologyNode (workflow2, null, null);
      w3 = new WorkflowTopologyNode (workflow3, null, null);
    }

    // insert status
    boolean insert;

    // Create a first topology with:
    //
    //         w1 (baseDN1)
    //         |
    //         w3 (baseDN3)
    //
    insert = w1.insertSubordinate (w3);
    assertEquals (insert, true);

    // Now insert w2 between w1 and w3
    //
    //         w1 (baseDN1)
    //         |
    //         w2 (baseDN2)
    //         |
    //         w3 (baseDN3)
    //
    insert = w1.insertSubordinate (w2);
    assertEquals (insert, true);

    // Check the topology:
    // - w1 has no parent and has only w2 as subordinate
    WorkflowTopologyNode parent = w1.getParent();
    assertNull (parent);
    ArrayList<WorkflowTopologyNode>  subordinates = w1.getSubordinates();
    assertEquals (subordinates.size(), 1);
    assertEquals (subordinates.get(0), w2);

    // - w2 has w1 as parent and w3 as subordinate
    parent = w2.getParent();
    assertEquals (parent, w1);
    subordinates = w2.getSubordinates();
    assertEquals (subordinates.size(), 1);
    assertEquals (subordinates.get(0), w3);

    // -w3 has w2 as parent and no subordinate
    parent = w3.getParent();
    assertEquals (parent, w2);
    subordinates = w3.getSubordinates();
    assertEquals (subordinates.size(), 0);

    // ======================================================
    // Topology is clean, now let's check the route algorithm.
    // ======================================================

    DN readDN1 = null;
    DN readDN2 = null;
    DN readDN3 = null;

    // subordinate1 should be handled by w1 only
    readDN1 = w1.getParentBaseDN (subordinateDN1);
    readDN2 = w1.getParentBaseDN (subordinateDN2);
    readDN3 = w1.getParentBaseDN (subordinateDN3);
    assertEquals (readDN1, baseDN1);
    assertEquals (readDN2, baseDN2);
    assertEquals (readDN3, baseDN3);

    // subordinate2 should be handled by w2 only
    readDN1 = w2.getParentBaseDN (subordinateDN1);
    readDN2 = w2.getParentBaseDN (subordinateDN2);
    readDN3 = w2.getParentBaseDN (subordinateDN3);
    assertEquals (readDN1, null);
    assertEquals (readDN2, baseDN2);
    assertEquals (readDN3, baseDN3);

    // subordinate3 should be handled by w3 only
    readDN1 = w3.getParentBaseDN (subordinateDN1);
    readDN2 = w3.getParentBaseDN (subordinateDN2);
    readDN3 = w3.getParentBaseDN (subordinateDN3);
    assertEquals (readDN1, null);
    assertEquals (readDN2, null);
    assertEquals (readDN3, baseDN3);

    // unrelatedDN should be handled by none of the workflows
    readDN1 = w1.getParentBaseDN (unrelatedDN);
    readDN2 = w2.getParentBaseDN (unrelatedDN);
    readDN3 = w3.getParentBaseDN (unrelatedDN);
    assertEquals (readDN1, null);
    assertEquals (readDN2, null);
    assertEquals (readDN3, null);
    
    // ======================================================
    // Remove a workflow in the chain and check that
    // the route algorithm is still working
    // ======================================================

    // Remove w2...
    //
    //         w1 (baseDN1)          w1
    //         |                     |
    //         w2 (baseDN2)   ==>    |
    //         |                     |
    //         w3 (baseDN3)          w3
    //
    w2.remove();

    // subordinate1 and subordinate2 should now be handled by w1 only
    readDN1 = w1.getParentBaseDN (subordinateDN1);
    readDN2 = w1.getParentBaseDN (subordinateDN2);
    readDN3 = w1.getParentBaseDN (subordinateDN3);
    assertEquals (readDN1, baseDN1);
    assertEquals (readDN2, baseDN1); // was baseDN2 before the removal...
    assertEquals (readDN3, baseDN3);
    
    // sanity check1
    // subordinate3 should be handled by w3 only
    readDN1 = w3.getParentBaseDN (subordinateDN1);
    readDN2 = w3.getParentBaseDN (subordinateDN2);
    readDN3 = w3.getParentBaseDN (subordinateDN3);
    assertEquals (readDN1, null);
    assertEquals (readDN2, null);
    assertEquals (readDN3, baseDN3);
    
    // sanity check2
    // unrelatedDN should be handled by none of the workflows
    readDN1 = w1.getParentBaseDN (unrelatedDN);
    readDN2 = w2.getParentBaseDN (unrelatedDN);
    readDN3 = w3.getParentBaseDN (unrelatedDN);
    assertEquals (readDN1, null);
    assertEquals (readDN2, null);
    assertEquals (readDN3, null);
    
  } // createWorkflow_simpleTopology2


  /**
   * Create a topology of workflows.
   *
   *                 W1
   *               baseDN1
   *                 /\
   *                /  \
   *               /    \
   *              W2    W3
   *         baseDN2    baseDN3
   *
   * There is no worklfow element attached to the DITs.
   *
   * @param baseDn1         base DN for the top workflow (W1)
   * @param baseDN2         base DN for the first subordinate workflow (W2)
   * @param baseDN3         base DN for the second subordinate workflow (W3)
   * @param subordinateDN1  subordinate DN of baseDN1
   * @param subordinateDN2  subordinate DN of baseDN2
   * @param subordinateDN3  subordinate DN of baseDN3
   * @param unrelatedDN     a DN not registered in any workflow
   */
  @Test (dataProvider = "DNSet_3", groups = "virtual")
  public void createWorkflow_complexTopology1(
      DN baseDN1,
      DN baseDN2,
      DN baseDN3,
      DN subordinateDN1,
      DN subordinateDN2,
      DN subordinateDN3,
      DN unrelatedDN
      )
  {
    // Create a worflow for each baseDN, no pre/post-workflow element
    WorkflowTopologyNode w1;
    WorkflowTopologyNode w2;
    WorkflowTopologyNode w3;
    {
      // create DITs with the given baseDNs with no workflow element.
      WorkflowImpl workflow1;
      WorkflowImpl workflow2;
      WorkflowImpl workflow3;
      {
        WorkflowElement nullWE = null;

        workflow1 = new WorkflowImpl (baseDN1.toString(), baseDN1, nullWE);
        workflow2 = new WorkflowImpl (baseDN2.toString(), baseDN2, nullWE);
        workflow3 = new WorkflowImpl (baseDN3.toString(), baseDN3, nullWE);
      }

      w1 = new WorkflowTopologyNode (workflow1, null, null);
      w2 = new WorkflowTopologyNode (workflow2, null, null);
      w3 = new WorkflowTopologyNode (workflow3, null, null);
    }

    // Put all the workflows in a pool
    WorkflowTopologyNode[] workflowPool = {w1, w2, w3};

    // Create the workflow topology: to do so, try to insert each workflow
    // in the other workflows. This is basically how workflow topology is
    // built by the network group.
    for (WorkflowTopologyNode parent: workflowPool)
    {
      for (WorkflowTopologyNode subordinate: workflowPool)
      {
        if (parent == subordinate)
        {
          // makes no sense to try to insert a workflow in itself!
          // let's do it anyway... but it should fail ;-)
          boolean insertDone = parent.insertSubordinate (parent);
          assertEquals (insertDone, false);
        }
        else
        {
          if (parent.insertSubordinate (subordinate))
          {
            // insert done
          }
        }
      }
    }

    // Check the topology
    // ------------------

    // W1 should have 2 subordinates: W2 and W3
    ArrayList<WorkflowTopologyNode> subordinates1 = w1.getSubordinates();
    assertEquals (subordinates1.size(), 2);

    // W2 and W3 should have no subordinate
    ArrayList<WorkflowTopologyNode> subordinates2 = w2.getSubordinates();
    assertEquals (subordinates2.size(), 0);
    ArrayList<WorkflowTopologyNode> subordinates3 = w3.getSubordinates();
    assertEquals (subordinates3.size(), 0);

    // W1 should be the parent of W2 and W3
    WorkflowTopologyNode parent2 = w2.getParent();
    assertEquals (parent2, w1);
    WorkflowTopologyNode parent3 = w3.getParent();
    assertEquals (parent3, w1);

    // Check the route algorithm
    // -------------------------

    // candidate for baseDN1 and subordinateBaseDN1 should be W1
    WorkflowTopologyNode candidate1 = w1.getWorkflowCandidate (baseDN1);
    assertEquals (candidate1, w1);
    candidate1 = w1.getWorkflowCandidate (subordinateDN1);
    assertEquals (candidate1, w1);

    // candidate for baseDN2/3 and subordinateBaseDN2/3 should be W2/3
    WorkflowTopologyNode candidate2 = w1.getWorkflowCandidate (baseDN2);
    assertEquals (candidate2, w2);
    candidate2 = w1.getWorkflowCandidate (subordinateDN2);
    assertEquals (candidate2, w2);

    WorkflowTopologyNode candidate3 = w1.getWorkflowCandidate (baseDN3);
    assertEquals (candidate3, w3);
    candidate3 = w1.getWorkflowCandidate (subordinateDN3);
    assertEquals (candidate3, w3);

    // there should be no candidate for dummyDN
    if (unrelatedDN != null)
    {
      WorkflowTopologyNode candidateDummy = w1.getWorkflowCandidate (unrelatedDN);
      assertEquals (candidateDummy, null);
    }

    // dump the topology
    StringBuffer sb = w1.toString ("");
    System.out.println (sb);

  } // createWorkflow_complexTopology1


  /**
   * Test the elaboration of the global result code by the workflow.
   */
  @Test (dataProvider = "ResultCodes_1", groups = "virtual")
  public void testGlobalResultCode(
      ResultCode receivedResultCode,
      ResultCode initialResultCode,
      ResultCode expectedGlobalResultCode
      )
      throws Exception
  {
    // Check the function that elaborates the global result code
    WorkflowResultCode globalResultCode = new WorkflowResultCode (
        initialResultCode, new StringBuilder("")
        );
    globalResultCode.elaborateGlobalResultCode (
        receivedResultCode, new StringBuilder("")
        );
    assertEquals (globalResultCode.resultCode(), expectedGlobalResultCode);
  }


  /**
   * Tests the workflow registration.
   */
  @Test (dataProvider = "DNSet_1", groups = "virtual")
  public void testWorkflowRegistration(
      DN baseDN,
      DN subordinateDN,
      DN dummyDN
      )
      throws DirectoryException
  {
    WorkflowElement nullWE = null;

    // Create a workflow to handle the baseDN with no workflow element
    WorkflowImpl workflow = new WorkflowImpl(
        baseDN.toString(), baseDN, nullWE);
    
    // Register the workflow with the server. Don't catch the
    // DirectoryException that could be thrown by the register() method.
    workflow.register();
    
    // Register the same workflow twice and catch the expected
    // DirectoryException.
    boolean exceptionRaised = false;
    try
    {
      workflow.register();
    }
    catch (DirectoryException e)
    {
      exceptionRaised = true;
      assertEquals(e.getMessageID(), MSGID_REGISTER_WORKFLOW_ALREADY_EXISTS);
    }
    assertEquals(exceptionRaised, true);
  }
}
