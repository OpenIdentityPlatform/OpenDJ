/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008-2009 Sun Microsystems, Inc.
 * Portions Copyright 2012-2016 ForgeRock AS.
 */
package org.opends.server.authorization.dseecompat;

import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.testng.Assert.*;

import java.util.Map;

import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class TargetAttrTestCase extends AciTestCase {

  private static String attrList="sn uid l";
  private static String attrList1="sn uid";
  private static String opAttrList="sn uid aci";
  private static final String user1="uid=user.1,ou=People,o=test";
  private static final String user3="uid=user.3,ou=People,o=test";
  private static final String aciFilter = "(aci=*)";


  private static final
  String grpAttrAci = "(targetattr=\"*\")" +
        "(version 3.0; acl \"user attr URL example\"; " +
        "allow (search,read) " +
        "userattr=\"ldap:///ou=People,o=test?manager#GROUPDN\";)";


  private static final
  String grp1AttrAci = "(targetattr=\"*\")" +
        "(version 3.0; acl \"user attr1 URL example\"; " +
        "allow (search,read) " +
        "userattr=\"ldap:///ou=People1,o=test?manager#GROUPDN\";)";

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

  private static final
  String controlAci = "(targetcontrol=\"1.3.6.1.1.13.1\")" +
          "(version 3.0;acl \"use pre-read control\";" +
          "allow (read) " +
          "userdn=\"ldap:///anyone\";)";

  private static final String user3ForbiddenUserAttr = "sn";
  private static final String user3ForbiddenOperationalAttr = "createTimestamp";
  private static final String user3AllowedUserAttr = "uid";
  private static final String user3AllowedOperationalAttr = "ds-privilege-name";
  private static final String user3WritableAttr = "description";

    private static final
  String selfWriteAci = "(targetattr=\"" + user3WritableAttr + "\")" +
          "(version 3.0;acl \"self write description\";" +
          "allow (write) " +
          "userdn=\"ldap:///self\";)";

  private static final
  String selfDenyAttrReadAci = "(targetattr=\"" + user3ForbiddenUserAttr + "||" +
          user3ForbiddenOperationalAttr + "\")" +
          "(version 3.0;acl \"self deny attribute reads\";" +
          "deny (read,search) " +
          "userdn=\"ldap:///self\";)";

  private static final
  String selfReadAllAttrsAci = "(targetattr=\"*||+\")" +
          "(version 3.0; acl \"self read/search all attributes\";" +
          "allow (read,search) " +
          "userdn=\"ldap:///self\";)";

  @BeforeClass
  public void setupClass() throws Exception {
    deleteAttrFromAdminEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
    String aciLdif3 = makeAddLDIF(ATTR_AUTHZ_GLOBAL_ACI, ACCESS_HANDLER_DN, controlAci, selfWriteAci);
    LDIFModify(aciLdif3, DIR_MGR_DN, PWD);
    addEntries("o=test");
  }

  /**
   * Test targetattr behavior using userattr bind rule.
   *
   * @throws Exception  If a test result is unexpected.
   */
  @Test
  public void testTargetAttrUserAttr() throws Exception {
    String aciLdif=makeAddLDIF("aci", user1, userAttrAci);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, filter, attrList);
    assertNotEquals(userResults, "");
    Map<String, String> attrMap = getAttrMap(userResults);
    checkAttributeVal(attrMap, "l", "Austin");
    checkAttributeVal(attrMap, "sn", "1");
    checkAttributeVal(attrMap, "uid", "user.1");
    String userResults1 =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, filter, attrList1);
    assertNotEquals(userResults1, "");
    Map<String, String> attrMap1 = getAttrMap(userResults1);
    checkAttributeVal(attrMap1, "sn", "1");
    checkAttributeVal(attrMap1, "uid", "user.1");
    deleteAttrFromEntry(user1, "aci");
    String aciLdif2=makeAddLDIF("aci", user1, userAttrAci1);
    LDIFModify(aciLdif2, DIR_MGR_DN, PWD);
    String userResults2 =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, filter, attrList);
    assertNotEquals(userResults2, "");
    Map<String, String> attrMap2 = getAttrMap(userResults2);
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
  @Test
  public void testTargetAttrOpAttr() throws Exception {
    //Add aci that only allows non-operational attributes search/read.
    String aciLdif=makeAddLDIF("aci", user1, nonOpAttrAci);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, filter, opAttrList);
    assertNotEquals(userResults, "");
    Map<String, String> attrMap = getAttrMap(userResults);
    //The aci attribute type is operational, it should not be there.
    //The other two should be there.
    assertFalse(attrMap.containsKey("aci"));
    assertTrue(attrMap.containsKey("sn"));
    assertTrue(attrMap.containsKey("uid"));
    deleteAttrFromEntry(user1, "aci");
    //Add aci that allows both non-operational attributes and the operational
    //attribute "aci" search/read.
    String aciLdif1=makeAddLDIF("aci", user1, nonOpAttrAci, opAttrAci);
    LDIFModify(aciLdif1, DIR_MGR_DN, PWD);
    String userResults1 =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, filter, opAttrList);
    assertNotEquals(userResults1, "");
    Map<String, String> attrMap1 = getAttrMap(userResults1);
    //All three attributes should be there.
    assertTrue(attrMap1.containsKey("aci"));
    assertTrue(attrMap1.containsKey("sn"));
    assertTrue(attrMap1.containsKey("uid"));
    deleteAttrFromEntry(user1, "aci");
    //Add ACI that only allows only aci operational attribute search/read.
    String aciLdif2=makeAddLDIF("aci", user1, opAttrAci);
    LDIFModify(aciLdif2, DIR_MGR_DN, PWD);
    String userResults2 =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, aciFilter, opAttrList);
    assertNotEquals(userResults2, "");
    Map<String, String> attrMap2 = getAttrMap(userResults2);
    // Only operational attribute aci should be there, the other two should not.
    assertTrue(attrMap2.containsKey("aci"));
    assertFalse(attrMap2.containsKey("sn"));
    assertFalse(attrMap2.containsKey("uid"));
    deleteAttrFromEntry(user1, "aci");
  }

  /**
   * Test targetattr behaviour with modify and pre-read controls
   *
   * @throws Exception  If a test result is unexpected.
   */
  @Test
  public void testTargetAttrPreRead() throws Exception {
    String aciLdif=makeAddLDIF("aci", user3,
            controlAci, selfWriteAci, selfDenyAttrReadAci, selfReadAllAttrsAci);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);

    // sanity check that search does not return the forbidden attributes
    String searchResults =
            LDAPSearchParams(user3, PWD, null, null, null, user3, filter, "+ *");
    assertNotEquals(searchResults, "");
    Map<String, String> attrMap = getAttrMap(searchResults);
    assertFalse(attrMap.containsKey(user3ForbiddenUserAttr));
    assertFalse(attrMap.containsKey(user3ForbiddenOperationalAttr));
    assertTrue(attrMap.containsKey(user3AllowedUserAttr));

    // check we can't pre-read the forbidden user attribute
    String modifyLdif1 = makeAddLDIF(user3WritableAttr, user3, "don't care 1");
    String modifyResults1 = preReadModify(user3, PWD, modifyLdif1, user3ForbiddenUserAttr);
    assertNotEquals(modifyResults1, "");
    Map<String, String> modifyMap1 = getAttrMap(modifyResults1, true);
    assertFalse(modifyMap1.containsKey(user3ForbiddenUserAttr));

    // check we can't pre-read the forbidden operational attribute
    String modifyLdif2 = makeAddLDIF(user3WritableAttr, user3, "don't care 2");
    String modifyResults2 = preReadModify(user3, PWD, modifyLdif2, user3ForbiddenOperationalAttr);
    assertNotEquals(modifyResults2, "");
    Map<String, String> modifyMap2 = getAttrMap(modifyResults2, true);
    assertFalse(modifyMap2.containsKey(user3ForbiddenOperationalAttr));

    // check we can pre-read the allowed user attribute
    String modifyLdif3 = makeAddLDIF(user3WritableAttr, user3, "don't care 3");
    String modifyResults3 = preReadModify(user3, PWD, modifyLdif3, user3AllowedUserAttr);
    assertNotEquals(modifyResults3, "");
    Map<String, String> modifyMap3 = getAttrMap(modifyResults3, true);
    assertTrue(modifyMap3.containsKey(user3AllowedUserAttr));

    // check we can pre-read the allowed operational attribute
    String modifyLdif4 = makeAddLDIF(user3WritableAttr, user3, "don't care 4");
    String modifyResults4 = preReadModify(user3, PWD, modifyLdif4, user3AllowedOperationalAttr);
    assertNotEquals(modifyResults4, "");
    Map<String, String> modifyMap4 = getAttrMap(modifyResults4, true);
    assertTrue(modifyMap4.containsKey(user3AllowedOperationalAttr));

    deleteAttrFromEntry(user3, "aci");
  }

  /**
   * Test targetattr shorthand behavior, all attrs both user and operational.
   * See comments.
   *
   * @throws Exception  If a test result is unexpected.
   */
  @Test
  public void testTargetAttrAllAttr() throws Exception {
    //Add aci with: (targetattr = "+ || *")
    String aciLdif=makeAddLDIF("aci", user1, allAttrs);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, filter, opAttrList);
    assertNotEquals(userResults, "");
    Map<String, String> attrMap = getAttrMap(userResults);
    //All should be returned.
    assertTrue(attrMap.containsKey("aci"));
    assertTrue(attrMap.containsKey("sn"));
    assertTrue(attrMap.containsKey("uid"));
    deleteAttrFromEntry(user1, "aci");
  }


  /**
   * Test targetattr shorthand behavior, userattr and plus sign (all op attrs).
   * See comments.
   *
   * @throws Exception If a test result is unexpected.
   */
  @Test
  public void testTargetAttrOpPlusAttr() throws Exception {
    //Add aci with: (targetattr = "objectclass|| +")
    String aciLdif=makeAddLDIF("aci", user1, ocOpAttrs);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, filter, opAttrList);
    assertNotEquals(userResults, "");
    Map<String, String> attrMap = getAttrMap(userResults);
    //Only aci should be returned.
    assertTrue(attrMap.containsKey("aci"));
    assertFalse(attrMap.containsKey("sn"));
    assertFalse(attrMap.containsKey("uid"));
    deleteAttrFromEntry(user1, "aci");
  }


  /**
   * Test targetattr shorthand behavior, star (all user attr) or aci attr.
   * See comments.
   *
   * @throws Exception  If a test result is unexpected.
   */
  @Test
  public void testTargetAttrUserStarAttr() throws Exception {
    //Add aci with: (targetattr = "*|| aci")
    String aciLdif=makeAddLDIF("aci", user1, starAciAttrs);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, filter, opAttrList);
    assertNotEquals(userResults, "");
    Map<String, String> attrMap = getAttrMap(userResults);
    //All should be returned.
    assertTrue(attrMap.containsKey("aci"));
    assertTrue(attrMap.containsKey("sn"));
    assertTrue(attrMap.containsKey("uid"));
    deleteAttrFromEntry(user1, "aci");
  }

  /**
   * Test targetattr shorthand behavior using '+' in expression and an
   * operational attribute in the filter. The second test is two ACIs one
   * with targetattr='+' and the other with targetattr='*'.
   *
   * @throws Exception If test result is unexpected.
   */
  @Test
  public void testTargetAttrSrchShorthand() throws Exception {
    //Aci: (targetattrs="sn || uid || +) and search with an
    //operational attr (aci).
    String aciLdif=makeAddLDIF("aci", user1, OpSrchAttrs);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, aciFilter, opAttrList);
    assertNotEquals(userResults, "");
    Map<String, String> attrMap = getAttrMap(userResults);
    //All should be returned.
    assertTrue(attrMap.containsKey("aci"));
    assertTrue(attrMap.containsKey("sn"));
    assertTrue(attrMap.containsKey("uid"));
    deleteAttrFromEntry(user1, "aci");
    //Add two ACIs, one with '+' and the other with '*'.
    String aciLdif1=makeAddLDIF("aci", user1, allOpAttrAci1, userAttrAci);
    LDIFModify(aciLdif1, DIR_MGR_DN, PWD);
    String userResults1 =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, aciFilter, opAttrList);
    assertNotEquals(userResults1, "");
    Map<String, String> attrMap1 = getAttrMap(userResults1);
    //All should be returned.
    assertTrue(attrMap1.containsKey("aci"));
    assertTrue(attrMap1.containsKey("sn"));
    assertTrue(attrMap1.containsKey("uid"));
    deleteAttrFromEntry(user1, "aci");
        //Add two ACIs, one with '+' and the other with '*'.
    String aciLdif2=makeAddLDIF("aci", user1, notAllOpAttrAci1, userAttrAci);
    LDIFModify(aciLdif2, DIR_MGR_DN, PWD);
    String userResults2 =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, filter, opAttrList);
    assertNotEquals(userResults2, "");
    Map<String, String> attrMap2 = getAttrMap(userResults2);
    //Only non-operation should be returned.
    assertFalse(attrMap2.containsKey("aci"));
    assertTrue(attrMap2.containsKey("sn"));
    assertTrue(attrMap2.containsKey("uid"));
    deleteAttrFromEntry(user1, "aci");
  }

  /**
   * Test two scenarios with userattr LDAP URL and groupdn keyword.
   *
   * @throws Exception Exception If test result is unexpected.
   */
  @Test
  public void testTargetAttrGrpDN() throws Exception {
    String aciLdif=makeAddLDIF("aci", user1, grpAttrAci);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, filter, attrList);
    assertNotEquals(userResults, "");
    Map<String, String> attrMap = getAttrMap(userResults);
    assertTrue(attrMap.containsKey("l"));
    assertTrue(attrMap.containsKey("sn"));
    assertTrue(attrMap.containsKey("uid"));
    deleteAttrFromEntry(user1, "aci");
    String aciLdif1=makeAddLDIF("aci", user1, grp1AttrAci);
    LDIFModify(aciLdif1, DIR_MGR_DN, PWD);
    String userResults1 =
            LDAPSearchParams(user3, PWD, null, null, null,
                    user1, filter, attrList);
    //This search should return nothing since the URL has a bogus DN.
    assertEquals("", userResults1);
  }

  private void
  checkAttributeVal(Map<String, String> attrMap, String attr,
                      String val) throws Exception {
    String mapVal=attrMap.get(attr);
    assertEquals(mapVal, val);
  }

  /** New tests to really unit test the isApplicable method. */
  @DataProvider(name = "targetAttrData")
  public Object[][] createData() throws Exception {
    return new Object[][] {
        /*
         * 4 elements:
         *  Operator ( = or !=),
         *  TartgetAttr Attributes list,
         *  Attribute to eval,
         *  Expected result
         */
        { "=", "cn", "cn", true },
        { "=", "cn || sn", "cn", true },
        { "=", "cn || sn", "sn", true },
        { "=", "cn", "sn", false },
        { "=", "*", "cn", true },
        { "=", "*", "modifytimestamp", false },
        { "=", "+", "modifytimestamp", true },
        { "=", "+", "cn", false },
        { "=", "* || +", "cn", true }, // Always true
        { "=", "* || +", "modifytimestamp", true }, // Always true
        { "=", "+ || *", "foo", true }, // Always true
        { "=", "* || +", "foo", true }, // Always true
        { "!=", "cn", "cn", false },
        { "!=", "cn || sn", "cn", false },
        { "!=", "cn || sn", "sn", false },
        { "!=", "cn", "sn", true }, // Not eq user attr
        { "!=", "cn || sn", "description", true }, // Not eq user attr
        { "!=", "cn || sn", "modifytimestamp", false }, // Not eq op attr
        { "!=", "aci", "cn", false },
        { "!=", "aci", "modifytimestamp", true },
    };
  }

  @Test(dataProvider = "targetAttrData")
  public void testTargetAttrStrings(String eqOperator, String targetAttrString,
    String attribute, boolean expectedResult) throws Exception
  {
    EnumTargetOperator op = EnumTargetOperator.createOperator(eqOperator);
    TargetAttr targetAttr = TargetAttr.decode(op, targetAttrString);
    AttributeType attrType = DirectoryServer.getSchema().getAttributeType(attribute);
    assertEquals(TargetAttr.isApplicable(attrType, targetAttr), expectedResult);
  }
}
