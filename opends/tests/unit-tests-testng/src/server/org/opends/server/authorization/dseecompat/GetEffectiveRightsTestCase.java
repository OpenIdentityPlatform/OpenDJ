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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.AfterClass;
import static org.opends.server.config.ConfigConstants.*;
import org.testng.Assert;
import org.opends.server.TestCaseUtils;
import static org.opends.server.util.ServerConstants.OID_GET_EFFECTIVE_RIGHTS;

import java.util.HashMap;

public class GetEffectiveRightsTestCase extends AciTestCase {
  private static final String base="uid=user.3,ou=People,o=test";
  private static final String user1="uid=user.1,ou=People,o=test";
  private static final String superUser="uid=superuser,ou=admins,o=test";
  private static final String[] attrList={"pager", "fax"};
  private static final String[] memberAttrList={"member"};
  private static final String entryLevel = "aclRights;entryLevel";
  private static final String attributeLevel = "aclRights;attributeLevel;";

  //Various results for entryLevel searches.
  private static final
  String bypassRights = "add:1,delete:1,read:1,write:1,proxy:1";

  private static final
  String rRights = "add:0,delete:0,read:1,write:0,proxy:0";

  private static final
  String arRights = "add:1,delete:0,read:1,write:0,proxy:0";

  private static final
  String adrRights = "add:1,delete:1,read:1,write:0,proxy:0";

  private static final
  String adrwRights = "add:1,delete:1,read:1,write:1,proxy:0";

  private static final
  String allRights = "add:1,delete:1,read:1,write:1,proxy:1";

  //Results for attributeLevel searches
  private static final String srwMailAttrRights =
          "search:1,read:1,compare:0,write:1," +
          "selfwrite_add:0,selfwrite_delete:0,proxy:0";

  private static final String srDescrptionAttrRights =
          "search:1,read:1,compare:0,write:0," +
          "selfwrite_add:0,selfwrite_delete:0,proxy:0";

  private static final String srxFaxAttrRights =
          "search:1,read:1,compare:0,write:?," +
          "selfwrite_add:0,selfwrite_delete:0,proxy:0";

  private static final String srPagerAttrRights =
          "search:1,read:1,compare:0,write:0," +
          "selfwrite_add:0,selfwrite_delete:0,proxy:0";

 private static final String selfWriteAttrRights =
          "search:0,read:0,compare:0,write:0," +
          "selfwrite_add:1,selfwrite_delete:1,proxy:0";

  //ACI needed to search/read aciRights attribute.

  //Need an ACI to allow proxy control
  String controlACI = "(targetcontrol=\"" + OID_GET_EFFECTIVE_RIGHTS + "\")" +
          "(version 3.0; acl \"control\";" +
          "allow(read) userdn=\"ldap:///anyone\";)";

  private static final
  String aclRightsAci = "(targetattr=\"aclRights\")" +
          "(version 3.0;acl \"aclRights access\";" +
          "allow (search, read) " +
          "userdn=\"ldap:///uid=superuser,ou=admins,o=test\";)";

  //General ACI superuser to search/read.

  private static final
  String readSearchAci = "(targetattr=\"*\")" +
          "(version 3.0;acl \"read/search access\";" +
          "allow (search, read) " +
          "userdn=\"ldap:///uid=superuser,ou=admins,o=test\";)";

  //General ACI for anonymous test.
  private static final
  String readSearchAnonAci = "(targetattr=\"*\")" +
          "(version 3.0;acl \"anonymous read/search access\";" +
          "allow (search, read) " +
          "userdn=\"ldap:///anyone\";)";

  //Test ACIs.
  private static final
  String addAci = "(version 3.0;acl \"add access\";" +
          "allow (add) " +
          "userdn=\"ldap:///uid=superuser,ou=admins,o=test\";)";

  private static final
  String delAci = "(version 3.0;acl \"delete access\";" +
          "allow (delete) " +
          "userdn=\"ldap:///uid=superuser,ou=admins,o=test\";)";

  private static final
  String writeAci = "(version 3.0;acl \"write access\";" +
          "allow (write) " +
          "userdn=\"ldap:///uid=superuser,ou=admins,o=test\";)";

  private static final
  String writeMailAci =  "(targetattr=\"mail\")" +
          "(version 3.0;acl \"write mail access\";" +
          "allow (write) " +
          "userdn=\"ldap:///uid=superuser,ou=admins,o=test\";)";

  private static final
  String proxyAci = "(version 3.0;acl \"proxy access\";" +
          "allow  (proxy) " +
          "userdn=\"ldap:///uid=superuser,ou=admins,o=test\";)";

  private static final
  String faxTargAttrAci =
          "(targattrfilters=\"add=fax:(fax=*), del=fax:(fax=*)\")" +
          "(version 3.0;acl \"allow write fax\";" +
          "allow (write)" +
          "userdn=\"ldap:///uid=superuser,ou=admins,o=test\";)";

  private static final
  String pagerTargAttrAci =
          "(targattrfilters=\"add=pager:(pager=*), del=pager:(pager=*)\")" +
          "(version 3.0;acl \"deny write pager\";" +
          "deny (write)" +
          "userdn=\"ldap:///uid=superuser,ou=admins,o=test\";)";

  private static final
  String selfWriteAci = "(targetattr=\"member\")" +
          "(version 3.0; acl \"selfwrite\"; allow(selfwrite)" +  "" +
          "userdn=\"ldap:///uid=user.1,ou=People,o=test\";)";

  @BeforeClass
  public void setupClass() throws Exception {
    TestCaseUtils.startServer();
    deleteAttrFromEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
    addEntries();
  }

  @AfterClass
  public void tearDown() throws Exception {
       String aciLdif=makeAddLDIF(ATTR_AUTHZ_GLOBAL_ACI, ACCESS_HANDLER_DN,
               G_READ_ACI, G_SELF_MOD, G_SCHEMA, G_DSE, G_USER_OPS, G_CONTROL);
       LDIFModify(aciLdif, DIR_MGR_DN, PWD);
   }

   @BeforeMethod
   public void removeAcis() throws Exception {
        deleteAttrFromEntry("ou=People,o=test", "aci");
   }

  /**
   * Test entry level using the -g param and anonymous dn as the authzid.
   * @throws Exception If the search result is empty or a right string
   * doesn't match the expected value.
   */
  @Test()
  public void testAnonEntryLevelParams() throws Exception {
    String aciLdif=makeAddLDIF("aci", "ou=People,o=test", readSearchAnonAci,
                               controlACI);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(DIR_MGR_DN, PWD, null, "dn:", null,
                    base, filter, "aclRights");
    Assert.assertFalse(userResults.equals(""));
    HashMap<String, String> attrMap=getAttrMap(userResults);
    checkEntryLevel(attrMap, rRights);

  }

  /**
   * Test entry level using the -g param and superuser dn as the authzid.
   * @throws Exception If the search result is empty or a right string
   * doesn't match the expected value.
   */
  @Test()
  public void testSuEntryLevelParams() throws Exception {
    String aciLdif=makeAddLDIF("aci", "ou=People,o=test", aclRightsAci,
                               controlACI);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    aciLdif=makeAddLDIF("aci", "ou=People,o=test", readSearchAci);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(superUser, PWD, null, "dn: " + superUser, null,
                    base, filter, "aclRights");
    Assert.assertFalse(userResults.equals(""));
    HashMap<String, String> attrMap=getAttrMap(userResults);
    checkEntryLevel(attrMap, rRights);
    aciLdif=makeAddLDIF("aci", "ou=People,o=test", addAci);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    userResults =
            LDAPSearchParams(superUser, PWD, null, "dn: " + superUser, null,
                    base, filter, "aclRights");
    Assert.assertFalse(userResults.equals(""));
    attrMap=getAttrMap(userResults);
    checkEntryLevel(attrMap, arRights);
    aciLdif=makeAddLDIF("aci", "ou=People,o=test", delAci);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    userResults =
            LDAPSearchParams(superUser, PWD, null, "dn: " + superUser, null,
                    base, filter, "aclRights");
    Assert.assertFalse(userResults.equals(""));
    attrMap=getAttrMap(userResults);
    checkEntryLevel(attrMap, adrRights);
    aciLdif=makeAddLDIF("aci", "ou=People,o=test", writeAci);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    userResults =
            LDAPSearchParams(superUser, PWD, null, "dn: " + superUser, null,
                    base, filter, "aclRights");
    Assert.assertFalse(userResults.equals(""));
    attrMap=getAttrMap(userResults);
    checkEntryLevel(attrMap, adrwRights);
    aciLdif=makeAddLDIF("aci", "ou=People,o=test", proxyAci);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    userResults =
            LDAPSearchParams(superUser, PWD, null, "dn: " + superUser, null,
                    base, filter, "aclRights");
    Assert.assertFalse(userResults.equals(""));
    attrMap=getAttrMap(userResults);
    checkEntryLevel(attrMap, allRights);
  }

   /**
   * Test entry level using the control OID only (no authzid specified).
   * Should use the bound user (superuser) as the authzid.
   * @throws Exception If the search result is empty or a right string
   * doesn't match the expected value.
   */
  @Test()
   public void testSuEntryLevelCtrl() throws Exception {
     String aciLdif=makeAddLDIF("aci", "ou=People,o=test", aclRightsAci,
                                controlACI);
     LDIFModify(aciLdif, DIR_MGR_DN, PWD);
     aciLdif=makeAddLDIF("aci", "ou=People,o=test", readSearchAci);
     LDIFModify(aciLdif, DIR_MGR_DN, PWD);
     String userResults =
            LDAPSearchCtrl(superUser, PWD, null, OID_GET_EFFECTIVE_RIGHTS,
                    base, filter, "aclRights");
     Assert.assertFalse(userResults.equals(""));
     HashMap<String, String> attrMap=getAttrMap(userResults);
     checkEntryLevel(attrMap, rRights);
     aciLdif=makeAddLDIF("aci", "ou=People,o=test", addAci);
     LDIFModify(aciLdif, DIR_MGR_DN, PWD);
     userResults =
            LDAPSearchCtrl(superUser, PWD, null, OID_GET_EFFECTIVE_RIGHTS,
                    base, filter, "aclRights");
     Assert.assertFalse(userResults.equals(""));
     attrMap=getAttrMap(userResults);
     checkEntryLevel(attrMap, arRights);
     aciLdif=makeAddLDIF("aci", "ou=People,o=test", delAci);
     LDIFModify(aciLdif, DIR_MGR_DN, PWD);
     userResults =
            LDAPSearchCtrl(superUser, PWD, null, OID_GET_EFFECTIVE_RIGHTS,
                    base, filter, "aclRights");
     Assert.assertFalse(userResults.equals(""));
     attrMap=getAttrMap(userResults);
     checkEntryLevel(attrMap, adrRights);
     aciLdif=makeAddLDIF("aci", "ou=People,o=test", writeAci);
     LDIFModify(aciLdif, DIR_MGR_DN, PWD);
     userResults =
            LDAPSearchCtrl(superUser, PWD, null, OID_GET_EFFECTIVE_RIGHTS,
                    base, filter, "aclRights");
     Assert.assertFalse(userResults.equals(""));
     attrMap=getAttrMap(userResults);
     checkEntryLevel(attrMap, adrwRights);
     aciLdif=makeAddLDIF("aci", "ou=People,o=test", proxyAci);
     LDIFModify(aciLdif, DIR_MGR_DN, PWD);
     userResults =
             LDAPSearchCtrl(superUser, PWD, null, OID_GET_EFFECTIVE_RIGHTS,
                     base, filter, "aclRights");
     Assert.assertFalse(userResults.equals(""));
     attrMap=getAttrMap(userResults);
     checkEntryLevel(attrMap, allRights);
   }

  /**
  * Test entry level using the control OID only -- bound as a bypass user.
  * Should use the bound user (DIR_MGR) as the authzid.
  * @throws Exception If the search result is empty or a right string
  * doesn't match the expected value.
  */
 @Test()
  public void testBypassEntryLevelCtrl() throws Exception {
    String aciLdif=makeAddLDIF("aci", "ou=People,o=test",  controlACI);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    String userResults =
           LDAPSearchCtrl(DIR_MGR_DN, PWD, null, OID_GET_EFFECTIVE_RIGHTS,
                   base, filter, "aclRights");
    Assert.assertFalse(userResults.equals(""));
    HashMap<String, String> attrMap=getAttrMap(userResults);
    checkEntryLevel(attrMap, bypassRights);
  }

    /**
   * Test attribute level using the -g param and superuser dn as the authzid.
   * The attributes used are mail and description. Mail should show write
   * access allowed, description should show write access not allowed.
   * @throws Exception If the search result is empty or a right string
   * doesn't match the expected value.
   */
  @Test()
  public void testSuAttrLevelParams() throws Exception {
    String aciLdif=makeAddLDIF("aci", "ou=People,o=test", aclRightsAci,
                               controlACI);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    aciLdif=makeAddLDIF("aci", "ou=People,o=test", readSearchAci);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    aciLdif=makeAddLDIF("aci", "ou=People,o=test", writeMailAci);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(superUser, PWD, null, "dn: " + superUser, null,
                    base, filter, "aclRights mail description");
    Assert.assertFalse(userResults.equals(""));
    HashMap<String, String> attrMap=getAttrMap(userResults);
    checkAttributeLevel(attrMap, "mail", srwMailAttrRights);
    checkAttributeLevel(attrMap, "description", srDescrptionAttrRights);
  }

 /**
 * Test attribute level using the -g param and superuser dn as the authzid and
 * the -e option using pager and fax.
 * The attributes used are mail and description. Mail should show write
 * access allowed, description should show write access not allowed.
 *
 * @throws Exception If the search result is empty or a right string
 * doesn't match the expected value.
 */
@Test()
public void testSuAttrLevelParams2() throws Exception {
  String aciLdif=makeAddLDIF("aci", "ou=People,o=test", aclRightsAci,
                             controlACI);
  LDIFModify(aciLdif, DIR_MGR_DN, PWD);
  aciLdif=makeAddLDIF("aci", "ou=People,o=test", readSearchAci);
  LDIFModify(aciLdif, DIR_MGR_DN, PWD);
  aciLdif=makeAddLDIF("aci", "ou=People,o=test", writeMailAci);
  LDIFModify(aciLdif, DIR_MGR_DN, PWD);
  aciLdif=makeAddLDIF("aci", "ou=People,o=test", faxTargAttrAci);
  LDIFModify(aciLdif, DIR_MGR_DN, PWD);
  aciLdif=makeAddLDIF("aci", "ou=People,o=test", pagerTargAttrAci);
  LDIFModify(aciLdif, DIR_MGR_DN, PWD);
  String userResults =
          LDAPSearchParams(superUser, PWD, null, "dn: " + superUser, attrList,
                  base, filter, "aclRights mail description");
  Assert.assertFalse(userResults.equals(""));
  HashMap<String, String> attrMap=getAttrMap(userResults);
  checkAttributeLevel(attrMap, "mail", srwMailAttrRights);
  checkAttributeLevel(attrMap, "description", srDescrptionAttrRights);
  checkAttributeLevel(attrMap, "fax", srxFaxAttrRights);
  checkAttributeLevel(attrMap, "pager", srPagerAttrRights);
}

 /**
 * Test selfwrite attribute level using the -g param and user.1 dn as the
 * authzid and the -e option member.
 * The attributes used are mail and description. Mail should show write
 * access allowed, description should show write access not allowed.
 *
 * @throws Exception If the search result is empty or a right string
 * doesn't match the expected value.
 */
@Test()
public void testSuAttrLevelParams3() throws Exception {
  String aciLdif=makeAddLDIF("aci", "ou=People,o=test", aclRightsAci,
                            controlACI);
  LDIFModify(aciLdif, DIR_MGR_DN, PWD);
  aciLdif=makeAddLDIF("aci", "ou=People,o=test", readSearchAci);
  LDIFModify(aciLdif, DIR_MGR_DN, PWD);
  aciLdif=makeAddLDIF("aci", "ou=People,o=test", selfWriteAci);
  LDIFModify(aciLdif, DIR_MGR_DN, PWD);
  String userResults =
          LDAPSearchParams(superUser, PWD, null, "dn: " + user1, memberAttrList,
                  base, filter, "aclRights");
  Assert.assertFalse(userResults.equals(""));
  HashMap<String, String> attrMap=getAttrMap(userResults);
  checkAttributeLevel(attrMap, "member", selfWriteAttrRights);
}

 private void
 checkAttributeLevel(HashMap<String, String> attrMap, String attr,
                     String reqRightsStr) throws Exception {
   String attrType=attributeLevel.toLowerCase() + attr;
   String retRightsStr=attrMap.get(attrType);
   Assert.assertTrue(retRightsStr.equals(reqRightsStr));
 }

 private void
 checkEntryLevel(HashMap<String, String> attrMap, String reqRightsStr)
 throws Exception {
    String retRightsStr=attrMap.get(entryLevel.toLowerCase());
    Assert.assertTrue(retRightsStr.equals(reqRightsStr));
 }
}
