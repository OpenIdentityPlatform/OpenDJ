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

package org.opends.server.authorization.dseecompat;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.Assert;
import org.opends.server.TestCaseUtils;
import static org.opends.server.config.ConfigConstants.*;

import java.util.HashMap;

public class TargetAttrTestCase extends AciTestCase {

  private static String attrList="sn uid l";
  private static String attrList1="sn uid";
  private static String opAttrList="sn uid aci";
  private static final String user1="uid=user.1,ou=People,o=test";
  private static final String user3="uid=user.3,ou=People,o=test";
  public  static final String aciFilter = "(aci=*)";


  private static final
  String starAciAttrs = "(targetattr=\"* || aci\")" +
          "(version 3.0;acl \"read/search all user, aci op\";" +
          "allow (search, read) " +
          "userattr=\"l#Austin\";)";

  private static final
  String ocOpAttrs = "(targetattr=\"objectclass || +\")" +
          "(version 3.0;acl \"read/search all op, oc user\";" +
          "allow (search, read) " +
          "userattr=\"l#Austin\";)";

  private static final
  String OpSrchAttrs = "(targetattr=\"sn || uid || +\")" +
          "(version 3.0;acl \"read/search all op, sn uid user\";" +
          "allow (search, read) " +
          "userattr=\"l#Austin\";)";

  private static final
  String allAttrs = "(targetattr=\"* || +\")" +
          "(version 3.0;acl \"read/search all user and all op lattr\";" +
          "allow (search, read) " +
          "userattr=\"l#Austin\";)";

  private static final
  String allOpAttrAci1 = "(targetattr=\"+\")" +
          "(version 3.0;acl \"read/search all op attr\";" +
          "allow (search, read) " +
          "userattr!=\"l#New York\";)";

  private static final
  String notAllOpAttrAci1 = "(targetattr!=\"+\")" +
          "(version 3.0;acl \"read/search not all op attr\";" +
          "allow (search, read) " +
          "userattr!=\"l#New York\";)";

  private static final
  String userAttrAci = "(targetattr=\"*\")" +
          "(version 3.0;acl \"read/search all userattr\";" +
          "allow (search, read) " +
          "userattr=\"l#Austin\";)";

  private static final
  String userAttrAci1 = "(targetattr=\"*\")" +
          "(version 3.0;acl \"read/search userattr\";" +
          "allow (search, read) " +
          "userattr!=\"l#New York\";)";

  private static final
  String nonOpAttrAci = "(targetattr=\"*\")" +
          "(version 3.0;acl \"read/search non-operational attr\";" +
          "allow (search, read) " +
          "userattr=\"l#Austin\";)";

  private static final
  String opAttrAci = "(targetattr=\"aci\")" +
          "(version 3.0;acl \"read/search operational attr\";" +
          "allow (search, read) " +
          "userattr=\"l#Austin\";)";

  @BeforeClass
  public void setupClass() throws Exception {
    TestCaseUtils.startServer();
    deleteAttrFromEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
    addEntries();
  }

  /**
   * Test targetattr behavior using userattr bind rule.
   *
   * @throws Exception  If a test result is unexpected.
   */
  @Test()
  public void testTargetAttrUserAttr() throws Exception {
    String aciLdif=makeAddAciLdif("aci", user1, userAttrAci);
    modEntries(aciLdif, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, filter, attrList);
    Assert.assertFalse(userResults.equals(""));
    HashMap<String, String> attrMap=getAttrMap(userResults);
    checkAttributeVal(attrMap, "l", "Austin");
    checkAttributeVal(attrMap, "sn", "1");
    checkAttributeVal(attrMap, "uid", "user.1");
    String userResults1 =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, filter, attrList1);
    Assert.assertFalse(userResults1.equals(""));
    HashMap<String, String> attrMap1=getAttrMap(userResults1);
    checkAttributeVal(attrMap1, "sn", "1");
    checkAttributeVal(attrMap1, "uid", "user.1");
    deleteAttrFromEntry(user1, "aci");
    String aciLdif2=makeAddAciLdif("aci", user1, userAttrAci1);
    modEntries(aciLdif2, DIR_MGR_DN, PWD);
    String userResults2 =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, filter, attrList);
    Assert.assertFalse(userResults2.equals(""));
    HashMap<String, String> attrMap2=getAttrMap(userResults2);
    checkAttributeVal(attrMap2, "l", "Austin");
    checkAttributeVal(attrMap2, "sn", "1");
    checkAttributeVal(attrMap2, "uid", "user.1");
    deleteAttrFromEntry(user1, "aci");
  }

  /**
   * Test targetattr and operational attribute behavior. See comments.
   *
   * @throws Exception If a test result is unexpected.
   */
  @Test()
  public void testTargetAttrOpAttr() throws Exception {
    //Add aci that only allows non-operational attributes search/read.
    String aciLdif=makeAddAciLdif("aci", user1, nonOpAttrAci);
    modEntries(aciLdif, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, filter, opAttrList);
    Assert.assertFalse(userResults.equals(""));
    HashMap<String, String> attrMap=getAttrMap(userResults);
    //The aci attribute type is operational, it should not be there.
    //The other two should be there.
    Assert.assertFalse(attrMap.containsKey("aci"));
    Assert.assertTrue(attrMap.containsKey("sn"));
    Assert.assertTrue(attrMap.containsKey("uid"));
    deleteAttrFromEntry(user1, "aci");
    //Add aci that allows both non-operational attributes and the operational
    //attribute "aci" search/read.
    String aciLdif1=makeAddAciLdif("aci", user1, nonOpAttrAci, opAttrAci);
    modEntries(aciLdif1, DIR_MGR_DN, PWD);
    String userResults1 =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, filter, opAttrList);
    Assert.assertFalse(userResults1.equals(""));
    HashMap<String, String> attrMap1=getAttrMap(userResults1);
    //All three attributes should be there.
    Assert.assertTrue(attrMap1.containsKey("aci"));
    Assert.assertTrue(attrMap1.containsKey("sn"));
    Assert.assertTrue(attrMap1.containsKey("uid"));
    deleteAttrFromEntry(user1, "aci");
    //Add ACI that only allows only aci operational attribute search/read.
    String aciLdif2=makeAddAciLdif("aci", user1, opAttrAci);
    modEntries(aciLdif2, DIR_MGR_DN, PWD);
    String userResults2 =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, aciFilter, opAttrList);
    Assert.assertFalse(userResults2.equals(""));
    HashMap<String, String> attrMap2=getAttrMap(userResults2);
    //Only operational attribute aci should be there, the other two should
    //not.
    Assert.assertTrue(attrMap2.containsKey("aci"));
    Assert.assertFalse(attrMap2.containsKey("sn"));
    Assert.assertFalse(attrMap2.containsKey("uid"));
    deleteAttrFromEntry(user1, "aci");
  }

  /**
   * Test targetattr shorthand behavior, all attrs both user and operational.
   * See comments.
   *
   * @throws Exception  If a test result is unexpected.
   */
  @Test()
  public void testTargetAttrAllAttr() throws Exception {
    //Add aci with: (targetattr = "+ || *")
    String aciLdif=makeAddAciLdif("aci", user1, allAttrs);
    modEntries(aciLdif, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, filter, opAttrList);
    Assert.assertFalse(userResults.equals(""));
    HashMap<String, String> attrMap=getAttrMap(userResults);
    //All should be returned.
    Assert.assertTrue(attrMap.containsKey("aci"));
    Assert.assertTrue(attrMap.containsKey("sn"));
    Assert.assertTrue(attrMap.containsKey("uid"));
    deleteAttrFromEntry(user1, "aci");
  }


  /**
   * Test targetattr shorthand behavior, userattr and plus sign (all op attrs).
   * See comments.
   *
   * @throws Exception If a test result is unexpected.
   */
  @Test()
  public void testTargetAttrOpPlusAttr() throws Exception {
    //Add aci with: (targetattr = "objectclass|| +")
    String aciLdif=makeAddAciLdif("aci", user1, ocOpAttrs);
    modEntries(aciLdif, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, filter, opAttrList);
    Assert.assertFalse(userResults.equals(""));
    HashMap<String, String> attrMap=getAttrMap(userResults);
    //Only aci should be returned.
    Assert.assertTrue(attrMap.containsKey("aci"));
    Assert.assertFalse(attrMap.containsKey("sn"));
    Assert.assertFalse(attrMap.containsKey("uid"));
    deleteAttrFromEntry(user1, "aci");
  }


  /**
   * Test targetattr shorthand behavior, star (all user attr) or aci attr.
   * See comments.
   *
   * @throws Exception  If a test result is unexpected.
   */
  @Test()
  public void testTargetAttrUserStarAttr() throws Exception {
    //Add aci with: (targetattr = "*|| aci")
    String aciLdif=makeAddAciLdif("aci", user1, starAciAttrs);
    modEntries(aciLdif, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, filter, opAttrList);
    Assert.assertFalse(userResults.equals(""));
    HashMap<String, String> attrMap=getAttrMap(userResults);
    //All should be returned.
    Assert.assertTrue(attrMap.containsKey("aci"));
    Assert.assertTrue(attrMap.containsKey("sn"));
    Assert.assertTrue(attrMap.containsKey("uid"));
    deleteAttrFromEntry(user1, "aci");
  }

  /**
   * Test targetattr shorthand behavior using '+' in expression and an
   * operational attribute in the filter. The second test is two ACIs one
   * with targetattr='+' and the other with targetattr='*'.
   *
   * @throws Exception If test result is unexpected.
   */
  @Test()
  public void testTargetAttrSrchShorthand() throws Exception {
    //Aci: (targetattrs="sn || uid || +) and search with an
    //operational attr (aci).
    String aciLdif=makeAddAciLdif("aci", user1, OpSrchAttrs);
    modEntries(aciLdif, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, aciFilter, opAttrList);
    Assert.assertFalse(userResults.equals(""));
    HashMap<String, String> attrMap=getAttrMap(userResults);
    //All should be returned.
    Assert.assertTrue(attrMap.containsKey("aci"));
    Assert.assertTrue(attrMap.containsKey("sn"));
    Assert.assertTrue(attrMap.containsKey("uid"));
    deleteAttrFromEntry(user1, "aci");
    //Add two ACIs, one with '+' and the other with '*'.
    String aciLdif1=makeAddAciLdif("aci", user1, allOpAttrAci1, userAttrAci);
    modEntries(aciLdif1, DIR_MGR_DN, PWD);
    String userResults1 =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, aciFilter, opAttrList);
    Assert.assertFalse(userResults1.equals(""));
    HashMap<String, String> attrMap1=getAttrMap(userResults1);
    //All should be returned.
    Assert.assertTrue(attrMap1.containsKey("aci"));
    Assert.assertTrue(attrMap1.containsKey("sn"));
    Assert.assertTrue(attrMap1.containsKey("uid"));
    deleteAttrFromEntry(user1, "aci");
        //Add two ACIs, one with '+' and the other with '*'.
    String aciLdif2=makeAddAciLdif("aci", user1, notAllOpAttrAci1, userAttrAci);
    modEntries(aciLdif2, DIR_MGR_DN, PWD);
    String userResults2 =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, filter, opAttrList);
    Assert.assertFalse(userResults2.equals(""));
    HashMap<String, String> attrMap2=getAttrMap(userResults2);
    //Only non-operation should be returned.
    Assert.assertFalse(attrMap2.containsKey("aci"));
    Assert.assertTrue(attrMap2.containsKey("sn"));
    Assert.assertTrue(attrMap2.containsKey("uid"));
    deleteAttrFromEntry(user1, "aci");
  }


  private void
  checkAttributeVal(HashMap<String, String> attrMap, String attr,
                      String val) throws Exception {
    String mapVal=attrMap.get(attr);
    Assert.assertTrue(mapVal.equals(val));
  }

}
