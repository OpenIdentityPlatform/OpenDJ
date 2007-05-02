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
import static org.opends.server.config.ConfigConstants.*;
import org.testng.Assert;
import static org.testng.Assert.assertEquals;
import org.opends.server.TestCaseUtils;
import org.opends.server.types.AttributeType;
import org.opends.server.types.Attribute;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.tools.LDAPModify;
import static org.opends.server.util.ServerConstants.OID_GET_EFFECTIVE_RIGHTS;
import static org.opends.server.util.ServerConstants.EOL;

import java.io.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

public class GetEffectiveRightsTestCase {
  private static ByteArrayOutputStream oStream = new ByteArrayOutputStream();
  private static final String DIR_MGR_DN = "cn=Directory Manager";
  private static final String PWD = "password";
  private static final String filter = "(objectclass=*)";
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

  private static final
  String ACCESS_HANDLER_DN = "cn=Access Control Handler,cn=config";

  //Results for attributeLevel searches
  private static final String srwMailAttrRights =
          "search:1,read:1,compare:0,write:1," +
          "selfwrite_add:1,selfwrite_delete:1,proxy:0";

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
    String aciLdif=makeAddAciLdif("aci", "ou=People,o=test", readSearchAnonAci);
    modEntries(aciLdif, DIR_MGR_DN, PWD);
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
    String aciLdif=makeAddAciLdif("aci", "ou=People,o=test", aclRightsAci);
    modEntries(aciLdif, DIR_MGR_DN, PWD);
    aciLdif=makeAddAciLdif("aci", "ou=People,o=test", readSearchAci);
    modEntries(aciLdif, DIR_MGR_DN, PWD);
    String userResults =
            LDAPSearchParams(superUser, PWD, null, "dn: " + superUser, null,
                    base, filter, "aclRights");
    Assert.assertFalse(userResults.equals(""));
    HashMap<String, String> attrMap=getAttrMap(userResults);
    checkEntryLevel(attrMap, rRights);
    aciLdif=makeAddAciLdif("aci", "ou=People,o=test", addAci);
    modEntries(aciLdif, DIR_MGR_DN, PWD);
    userResults =
            LDAPSearchParams(superUser, PWD, null, "dn: " + superUser, null,
                    base, filter, "aclRights");
    Assert.assertFalse(userResults.equals(""));
    attrMap=getAttrMap(userResults);
    checkEntryLevel(attrMap, arRights);
    aciLdif=makeAddAciLdif("aci", "ou=People,o=test", delAci);
    modEntries(aciLdif, DIR_MGR_DN, PWD);
    userResults =
            LDAPSearchParams(superUser, PWD, null, "dn: " + superUser, null,
                    base, filter, "aclRights");
    Assert.assertFalse(userResults.equals(""));
    attrMap=getAttrMap(userResults);
    checkEntryLevel(attrMap, adrRights);
    aciLdif=makeAddAciLdif("aci", "ou=People,o=test", writeAci);
    modEntries(aciLdif, DIR_MGR_DN, PWD);
    userResults =
            LDAPSearchParams(superUser, PWD, null, "dn: " + superUser, null,
                    base, filter, "aclRights");
    Assert.assertFalse(userResults.equals(""));
    attrMap=getAttrMap(userResults);
    checkEntryLevel(attrMap, adrwRights);
    aciLdif=makeAddAciLdif("aci", "ou=People,o=test", proxyAci);
    modEntries(aciLdif, DIR_MGR_DN, PWD);
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
     String aciLdif=makeAddAciLdif("aci", "ou=People,o=test", aclRightsAci);
     modEntries(aciLdif, DIR_MGR_DN, PWD);
     aciLdif=makeAddAciLdif("aci", "ou=People,o=test", readSearchAci);
     modEntries(aciLdif, DIR_MGR_DN, PWD);
     String userResults =
            LDAPSearchCtrl(superUser, PWD, null, OID_GET_EFFECTIVE_RIGHTS,
                    base, filter, "aclRights");
     Assert.assertFalse(userResults.equals(""));
     HashMap<String, String> attrMap=getAttrMap(userResults);
     checkEntryLevel(attrMap, rRights);
     aciLdif=makeAddAciLdif("aci", "ou=People,o=test", addAci);
     modEntries(aciLdif, DIR_MGR_DN, PWD);
     userResults =
            LDAPSearchCtrl(superUser, PWD, null, OID_GET_EFFECTIVE_RIGHTS,
                    base, filter, "aclRights");
     Assert.assertFalse(userResults.equals(""));
     attrMap=getAttrMap(userResults);
     checkEntryLevel(attrMap, arRights);
     aciLdif=makeAddAciLdif("aci", "ou=People,o=test", delAci);
     modEntries(aciLdif, DIR_MGR_DN, PWD);
     userResults =
            LDAPSearchCtrl(superUser, PWD, null, OID_GET_EFFECTIVE_RIGHTS,
                    base, filter, "aclRights");
     Assert.assertFalse(userResults.equals(""));
     attrMap=getAttrMap(userResults);
     checkEntryLevel(attrMap, adrRights);
     aciLdif=makeAddAciLdif("aci", "ou=People,o=test", writeAci);
     modEntries(aciLdif, DIR_MGR_DN, PWD);
     userResults =
            LDAPSearchCtrl(superUser, PWD, null, OID_GET_EFFECTIVE_RIGHTS,
                    base, filter, "aclRights");
     Assert.assertFalse(userResults.equals(""));
     attrMap=getAttrMap(userResults);
     checkEntryLevel(attrMap, adrwRights);
     aciLdif=makeAddAciLdif("aci", "ou=People,o=test", proxyAci);
     modEntries(aciLdif, DIR_MGR_DN, PWD);
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
    String aciLdif=makeAddAciLdif("aci", "ou=People,o=test", aclRightsAci);
    modEntries(aciLdif, DIR_MGR_DN, PWD);
    aciLdif=makeAddAciLdif("aci", "ou=People,o=test", readSearchAci);
    modEntries(aciLdif, DIR_MGR_DN, PWD);
    aciLdif=makeAddAciLdif("aci", "ou=People,o=test", writeMailAci);
    modEntries(aciLdif, DIR_MGR_DN, PWD);
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
  String aciLdif=makeAddAciLdif("aci", "ou=People,o=test", aclRightsAci);
  modEntries(aciLdif, DIR_MGR_DN, PWD);
  aciLdif=makeAddAciLdif("aci", "ou=People,o=test", readSearchAci);
  modEntries(aciLdif, DIR_MGR_DN, PWD);
  aciLdif=makeAddAciLdif("aci", "ou=People,o=test", writeMailAci);
  modEntries(aciLdif, DIR_MGR_DN, PWD);
  aciLdif=makeAddAciLdif("aci", "ou=People,o=test", faxTargAttrAci);
  modEntries(aciLdif, DIR_MGR_DN, PWD);
  aciLdif=makeAddAciLdif("aci", "ou=People,o=test", pagerTargAttrAci);
  modEntries(aciLdif, DIR_MGR_DN, PWD);
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
  String aciLdif=makeAddAciLdif("aci", "ou=People,o=test", aclRightsAci);
  modEntries(aciLdif, DIR_MGR_DN, PWD);
  aciLdif=makeAddAciLdif("aci", "ou=People,o=test", readSearchAci);
  modEntries(aciLdif, DIR_MGR_DN, PWD);
  aciLdif=makeAddAciLdif("aci", "ou=People,o=test", selfWriteAci);
  modEntries(aciLdif, DIR_MGR_DN, PWD);
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
   String attrType=attributeLevel + attr;
   String retRightsStr=attrMap.get(attrType);
   Assert.assertTrue(retRightsStr.equals(reqRightsStr));
 }

 private void
 checkEntryLevel(HashMap<String, String> attrMap, String reqRightsStr)
 throws Exception {
    String retRightsStr=attrMap.get(entryLevel);
    Assert.assertTrue(retRightsStr.equals(reqRightsStr));
 }

 private HashMap<String, String>
 getAttrMap(String resultString) throws Exception {
       StringReader r=new StringReader(resultString);
    BufferedReader br=new BufferedReader(r);
    HashMap<String, String> attrMap = new HashMap<String,String>();
    try {
      while(true) {
        String s = br.readLine();
        if(s == null)
          break;
        if(s.startsWith("dn:"))
          continue;
        String[] a=s.split(": ");
        if(a.length != 2)
          break;
        attrMap.put(a[0],a[1]);
      }
    } catch (IOException e) {
      Assert.assertEquals(0, 1,  e.getMessage());
    }
   return attrMap;
 }


  private void addEntries() throws Exception {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntries(
            "dn: ou=People,o=test",
            "objectClass: top",
            "objectClass: organizationalUnit",
            "ou: People",
            "",
            "dn: ou=admins,o=test",
            "objectClass: top",
            "objectClass: organizationalUnit",
            "ou: admins",
            "",
            "dn: cn=group,ou=People,o=test",
            "objectclass: top",
            "objectclass: groupOfNames",
            "cn: group",
            "member: uid=user.1,ou=People,o=test",
            "",
            "dn: uid=superuser,ou=admins,o=test",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: superuser",
            "givenName: superuser",
            "sn: 1",
            "cn: User 1",
            "userPassword: password",
            "ds-privilege-name: proxied-auth",
            "",
            "dn: uid=proxyuser,ou=admins,o=test",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: proxyuser",
            "givenName: proxyuser",
            "sn: 1",
            "cn: User 1",
            "userPassword: password",
            "",
            "dn: uid=user.1,ou=People,o=test",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: user.1",
            "givenName: User",
            "sn: 1",
            "cn: User 1",
            "userPassword: password",
            "",
            "dn: uid=user.2,ou=People,o=test",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: user.2",
            "givenName: User",
            "sn: 2",
            "cn: User 2",
            "userPassword: password",
            "",
            "dn: uid=user.3,ou=People,o=test",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: user.3",
            "givenName: User",
            "sn: 3",
            "mail: user.3@test",
            "description: user.3 description",
            "cn: User 3",
            "userPassword: password");
  }

  private String LDAPSearchCtrl(String bindDn, String bindPassword,
                            String proxyDN, String controlStr,
                            String base, String filter, String attr)
          throws Exception {
    ArrayList<String> argList=new ArrayList<String>(20);
    argList.add("-h");
    argList.add("127.0.0.1");
    argList.add("-p");
    argList.add(String.valueOf(TestCaseUtils.getServerLdapPort()));
    argList.add("-D");
    argList.add(bindDn);
    argList.add("-w");
    argList.add(bindPassword);
    argList.add("-T");
    if(proxyDN != null) {
      argList.add("-Y");
      argList.add("dn:" + proxyDN);
    }
    if(controlStr != null) {
      argList.add("-J");
      argList.add(controlStr);
    }
    argList.add("-b");
    argList.add(base);
    argList.add("-s");
    argList.add("sub");
    argList.add(filter);
    String[] attrs=attr.split("\\s+");
    for(String a : attrs)
     argList.add(a);
    String[] args = new String[argList.size()];
    oStream.reset();
    int retVal =
            LDAPSearch.mainSearch(argList.toArray(args), false, oStream, oStream);
    Assert.assertEquals(0, retVal,  "Returned error: " + oStream.toString());
    return oStream.toString();
  }

  private String LDAPSearchParams(String bindDn, String bindPassword,
                            String proxyDN, String authzid, String[] attrList,
                            String base, String filter ,String attr)
          throws Exception {
    ArrayList<String> argList=new ArrayList<String>(20);
    argList.add("-h");
    argList.add("127.0.0.1");
    argList.add("-p");
    argList.add(String.valueOf(TestCaseUtils.getServerLdapPort()));
    argList.add("-D");
    argList.add(bindDn);
    argList.add("-w");
    argList.add(bindPassword);
    argList.add("-T");
    if(proxyDN != null) {
      argList.add("-Y");
      argList.add("dn:" + proxyDN);
    }
    if(authzid != null) {
      argList.add("-g");
      argList.add(authzid);
    }
    if(attrList != null) {
      for(String a : attrList) {
        argList.add("-e");
        argList.add(a);
      }
    }
    argList.add("-b");
    argList.add(base);
    argList.add("-s");
    argList.add("sub");
    argList.add(filter);
    String[] attrs=attr.split("\\s+");
    for(String a : attrs)
     argList.add(a);
    String[] args = new String[argList.size()];
    oStream.reset();
    int retVal =
         LDAPSearch.mainSearch(argList.toArray(args), false, oStream, oStream);
    Assert.assertEquals(0, retVal, "Returned error: " + oStream.toString());
    return oStream.toString();
  }


  private void modEntries(String ldif, String bindDn, String bindPassword)
          throws Exception {
    File tempFile = getTemporaryLdifFile();
    TestCaseUtils.writeFile(tempFile, ldif);
    ArrayList<String> argList=new ArrayList<String>(20);
    argList.add("-h");
    argList.add("127.0.0.1");
    argList.add("-p");
    argList.add(String.valueOf(TestCaseUtils.getServerLdapPort()));
    argList.add("-D");
    argList.add(bindDn);
    argList.add("-w");
    argList.add(bindPassword);
    argList.add("-f");
    argList.add(tempFile.getAbsolutePath());
    String[] args = new String[argList.size()];
    ldapModify(argList.toArray(args));
  }


  private void ldapModify(String[] args) {
    oStream.reset();
    LDAPModify.mainModify(args, false, oStream, oStream);
  }

  private void deleteAttrFromEntry(String dn, String attr)
  throws Exception {
    StringBuilder ldif = new StringBuilder();
    ldif.append(TestCaseUtils.makeLdif(
            "dn: "  + dn,
            "changetype: modify",
            "delete: " + attr));
    modEntries(ldif.toString(), DIR_MGR_DN, PWD);
  }

  private static ThreadLocal<Map<String,File>> tempLdifFile =
          new ThreadLocal<Map<String,File>>();

  private File getTemporaryLdifFile() throws IOException {
    Map<String,File> tempFilesForThisThread = tempLdifFile.get();
    if (tempFilesForThisThread == null) {
      tempFilesForThisThread = new HashMap<String,File>();
      tempLdifFile.set(tempFilesForThisThread);
    }
    File tempFile = tempFilesForThisThread.get("effectiverights-tests");
    if (tempFile == null) {
      tempFile = File.createTempFile("effectiverights-tests", ".ldif");
      tempFile.deleteOnExit();
      tempFilesForThisThread.put("effectiverights-tests", tempFile);
    }
    return tempFile;
  }

  private static String makeAddAciLdif(String attr, String dn, String... acis) {
    StringBuilder ldif = new StringBuilder();
    ldif.append("dn: ").append(dn).append(EOL);
    ldif.append("changetype: modify").append(EOL);
    ldif.append("add: ").append(attr).append(EOL);
    for(String aci : acis)
      ldif.append(attr).append(":").append(aci).append(EOL);
    ldif.append(EOL);
    return ldif.toString();
  }
}
