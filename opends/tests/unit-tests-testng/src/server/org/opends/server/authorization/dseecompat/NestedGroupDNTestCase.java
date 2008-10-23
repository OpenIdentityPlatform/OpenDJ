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
 *      Copyright 2008 Sun Microsystems, Inc.
 */


package org.opends.server.authorization.dseecompat;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.Assert;
import org.opends.server.TestCaseUtils;
import static org.opends.server.config.ConfigConstants.ATTR_AUTHZ_GLOBAL_ACI;

/**
 * Test the groupdn keyword using nested groups.
 */
public class NestedGroupDNTestCase extends AciTestCase {

  private static final String peopleBase="ou=People,o=test";
  private static final String user5="uid=user.5,ou=People,o=test";
  private static final String group1DN = "cn=group 1,ou=Nested Groups, o=test";
  private static final String group2DN = "cn=group 2,ou=Nested Groups, o=test";
  private static final String group3DN = "cn=group 3,ou=Nested Groups, o=test";
  private static final String group4DN = "cn=group 4,ou=Nested Groups, o=test";

  private static final
  String groupAci = "(targetattr=\"*\")" +
        "(version 3.0; acl \"group ACI\"; " +
        "allow (all) " +
        "groupdn=\"ldap:///" + group1DN + "\";)";

  @BeforeClass
  public void setupClass() throws Exception {
    TestCaseUtils.restartServer();
    deleteAttrFromAdminEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
    addEntries("o=test");
  }

  @AfterClass(alwaysRun = true)
  public void tearDown() throws Exception {
       String aciLdif=makeAddLDIF(ATTR_AUTHZ_GLOBAL_ACI, ACCESS_HANDLER_DN,
               G_READ_ACI, G_SELF_MOD, G_SCHEMA, G_DSE, G_USER_OPS, G_CONTROL,
               E_EXTEND_OP);
       LDIFAdminModify(aciLdif, DIR_MGR_DN, PWD);
   }


  @BeforeMethod
  public void clearBackend() throws Exception {
    deleteAttrFromEntry(peopleBase, "aci");
    deleteAttrFromEntry(group1DN, "member");
    deleteAttrFromEntry(group2DN, "member");
    deleteAttrFromEntry(group3DN, "member");
    deleteAttrFromAdminEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
  }

  /**
   * Test access using static nested groups. Add a user to group3, add group3
   * to group2 and group2 to group1.
   *
   * @throws Exception If an unexpected result is received.
   */
  @Test()
  public void testNestedGroup() throws Exception {
    String aciLdif=makeAddLDIF("aci", peopleBase, groupAci);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(user5, PWD, null, null, null,
                    user5, filter, null);
    //Access to user5 should be denied, user5 is not in any groups.
    Assert.assertTrue(userResults.equals(""));
    //Add user5 to group1.
    String member5Ldif=makeAddLDIF("member", group3DN, user5);
    LDIFModify(member5Ldif, DIR_MGR_DN, PWD);
    //Nest group1 in group2.
    String group2Ldif=makeAddLDIF("member", group2DN, group3DN);
    LDIFModify(group2Ldif, DIR_MGR_DN, PWD);
    //Nest group2 in group1.
    String group1Ldif=makeAddLDIF("member", group1DN, group2DN);
    LDIFModify(group1Ldif, DIR_MGR_DN, PWD);
    String userResults1 =
            LDAPSearchParams(user5, PWD, null, null, null,
                    user5, filter, null);
    //Results should be returned since user5 now has access.
    Assert.assertFalse(userResults1.equals(""));
  }


  /**
   * Test access using a dynamic nested group. Group 4 (dynamic) is nested
   * in group1, group1 is nested in group2, group2 is nested in group1.
   *
   * @throws Exception If an unexpected result is returned.
   */
  @Test()
  public void testNestedDynamicGroup() throws Exception {
    String aciLdif=makeAddLDIF("aci", peopleBase, groupAci);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(user5, PWD, null, null, null,
                    user5, filter, null);
    Assert.assertTrue(userResults.equals(""));
    //Add group4 (dynamic) to group3.
    String group3Ldif=makeAddLDIF("member", group3DN, group4DN);
    LDIFModify(group3Ldif, DIR_MGR_DN, PWD);
    String group2Ldif=makeAddLDIF("member", group2DN, group3DN);
    LDIFModify(group2Ldif, DIR_MGR_DN, PWD);
    String group1Ldif=makeAddLDIF("member", group1DN, group2DN);
    LDIFModify(group1Ldif, DIR_MGR_DN, PWD);
    String userResults1 =
            LDAPSearchParams(user5, PWD, null, null, null,
                    user5, filter, null);
    //Results should be returned, since user5 now has access because of
    //nested group4.
    Assert.assertFalse(userResults1.equals(""));
  }


  /**
   * Test group access using a circular group definition. Group3 points back
   * to group1.
   *
   * @throws Exception IKf an unexpected result is returned.
   */
  @Test()
  public void testNestedCircularGroup() throws Exception {
    String aciLdif=makeAddLDIF("aci", peopleBase, groupAci);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(user5, PWD, null, null, null,
                    user5, filter, null);
    Assert.assertTrue(userResults.equals(""));
    //Nest group1 in group3, creating circular nesting.
    String group3Ldif=makeAddLDIF("member", group3DN, group1DN);
    LDIFModify(group3Ldif, DIR_MGR_DN, PWD);
    String group2Ldif=makeAddLDIF("member", group2DN, group3DN);
    LDIFModify(group2Ldif, DIR_MGR_DN, PWD);
    String group1Ldif=makeAddLDIF("member", group1DN, group2DN);
    LDIFModify(group1Ldif, DIR_MGR_DN, PWD);
    String userResults1 =
            LDAPSearchParams(user5, PWD, null, null, null,
                    user5, filter, null);
    //Results should not be returned because of circular condition.
    Assert.assertTrue(userResults1.equals(""));
  }

}
