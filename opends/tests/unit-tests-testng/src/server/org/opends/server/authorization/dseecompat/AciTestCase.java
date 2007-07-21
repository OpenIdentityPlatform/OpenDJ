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

import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.tools.LDAPModify;
import org.opends.server.tools.LDAPSearch;
import org.opends.server.tools.LDAPDelete;
import org.opends.server.tools.LDAPPasswordModify;
import static org.opends.server.util.ServerConstants.EOL;
import org.testng.annotations.Test;
import org.testng.Assert;

import java.io.*;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@Test(groups = { "precommit", "dseecompat" })
public abstract class  AciTestCase extends DirectoryServerTestCase {
  public static final String DIR_MGR_DN = "cn=Directory Manager";
  public static final String PWD = "password";
  public  static final String filter = "(objectclass=*)";
  public static final String ACCESS_HANDLER_DN =
                                       "cn=Access Control Handler,cn=config";

  //GLOBAL ACIs

  protected final static String G_READ_ACI =
          "(targetattr!=\"userPassword||authPassword\")" +
                  "(version 3.0; acl \"Anonymous read access\";" +
                  "allow (read,search,compare) userdn=\"ldap:///anyone\";)";

  protected final static String G_SELF_MOD =
          "(targetattr=\"*\")(version 3.0; acl \"Self entry modification\";" +
                  "allow (write) userdn=\"ldap:///self\";)";

  protected final static String G_SCHEMA =
          "(target=\"ldap:///cn=schema\")(targetscope=\"base\")" +
          "(targetattr=\"attributeTypes||dITContentRules||dITStructureRules||" +
                  "ldapSyntaxes||matchingRules||matchingRuleUse||nameForms||" +
                  "objectClasses\")" +
          "(version 3.0; acl \"User-Visible Schema Operational Attributes\";" +
                  "allow (read,search,compare) userdn=\"ldap:///anyone\";)";

  protected final static String G_DSE =
          "(target=\"ldap:///\")(targetscope=\"base\")" +
               "(targetattr=\"namingContexts||supportedAuthPasswordSchemes||" +
                  "supportedControl||supportedExtension||supportedFeatures||" +
                 "supportedSASLMechanisms||vendorName||vendorVersion\")" +
        "(version 3.0; acl \"User-Visible Root DSE Operational Attributes\"; " +
                  "allow (read,search,compare) userdn=\"ldap:///anyone\";)";

  protected final static String G_USER_OPS =
          "(targetattr=\"createTimestamp||creatorsName||modifiersName||" +
                  "modifyTimestamp||entryDN||entryUUID||subschemaSubentry\")" +
                 "(version 3.0; acl \"User-Visible Operational Attributes\"; " +
                  "allow (read,search,compare) userdn=\"ldap:///anyone\";)";

  protected final static String G_CONTROL =
          "(targetcontrol = \"*\")" +
          "(version 3.0; acl \"Control\"; " +
                  "allow (read) userdn=\"ldap:///anyone\";)";

  private static final ByteArrayOutputStream oStream = new ByteArrayOutputStream();
  private  static final ThreadLocal<Map<String,File>> tempLdifFile =
           new ThreadLocal<Map<String,File>>();


  protected String pwdModify(String bindDn, String bindPassword,
                             String newPassword, String noOpControl,
                             String pwdPolicyControl, int rc) {

    ArrayList<String> argList=new ArrayList<String>(20);
    argList.add("-h");
    argList.add("127.0.0.1");
    argList.add("-p");
    argList.add(String.valueOf(TestCaseUtils.getServerLdapPort()));
    argList.add("-D");
    argList.add(bindDn);
    argList.add("-w");
    argList.add(bindPassword);
    argList.add("-c");
    argList.add(bindPassword);
    argList.add("-n");
    argList.add(newPassword);
    if(noOpControl != null) {
      argList.add("-J");
      argList.add(noOpControl);
    }
    if(pwdPolicyControl != null) {
      argList.add("-J");
      argList.add(pwdPolicyControl);
    }
    String[] args = new String[argList.size()];
    oStream.reset();
    int ret=
           LDAPPasswordModify.mainPasswordModify(argList.toArray(args),
                   false, oStream, oStream);
    Assert.assertEquals(rc, ret,  "Returned error: " + oStream.toString());
    return oStream.toString();
  }

  protected String LDAPSearchCtrl(String bindDn, String bindPassword,
                            String proxyDN, String controlStr,
                            String base, String filter, String attr) {
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

  protected String
  LDAPSearchParams(String bindDn,  String bindPassword,
                               String proxyDN, String authzid,
                               String[] attrList,
                               String base, String filter ,String attr,
                               boolean pwdPolicy, boolean reportAuthzID,
                               int rc)  {
    return _LDAPSearchParams(bindDn, bindPassword, proxyDN, authzid, attrList,
            base, filter, attr, pwdPolicy, reportAuthzID, rc);
  }

  protected String LDAPSearchParams(String bindDn, String bindPassword,
                                    String proxyDN, String authzid,
                                    String[] attrList,
                                    String base, String filter ,String attr) {
    return _LDAPSearchParams(bindDn, bindPassword, proxyDN, authzid, attrList,
            base, filter, attr, false, false, 0);
  }

  private String _LDAPSearchParams(String bindDn, String bindPassword,
                            String proxyDN, String authzid, String[] attrList,
                            String base, String filter ,String attr,
                            boolean pwdPolicy, boolean reportAuthzID, int rc) {
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
    if(pwdPolicy) {
      argList.add("--usePasswordPolicyControl");
    }
    if(reportAuthzID) {
      argList.add("-E");
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
    Assert.assertEquals(rc, retVal, "Returned error: " + oStream.toString());
    return oStream.toString();
  }

  protected void LDIFAdd(String ldif, String bindDn, String bindPassword,
                            String controlStr, int rc) throws Exception {
    _LDIFModify(ldif, bindDn, bindPassword, controlStr, true, rc);
  }

  protected void LDIFModify(String ldif, String bindDn, String bindPassword,
                            String controlStr, int rc) throws Exception {
    _LDIFModify(ldif, bindDn, bindPassword, controlStr, false, rc);
  }

  protected void LDIFModify(String ldif, String bindDn, String bindPassword)
  throws Exception {
    _LDIFModify(ldif, bindDn, bindPassword, null, false, -1);
  }

  protected void LDIFDelete(String dn, String bindDn, String bindPassword,
                            String controlStr, int rc) {
    _LDIFDelete(dn, bindDn, bindPassword, controlStr, rc);
  }

  private void _LDIFDelete(String dn, String bindDn, String bindPassword,
                           String controlStr, int rc) {
    ArrayList<String> argList=new ArrayList<String>(20);
    argList.add("-h");
    argList.add("127.0.0.1");
    argList.add("-p");
    argList.add(String.valueOf(TestCaseUtils.getServerLdapPort()));
    argList.add("-D");
    argList.add(bindDn);
    argList.add("-w");
    argList.add(bindPassword);
    if(controlStr != null) {
      argList.add("-J");
      argList.add(controlStr);
    }
    argList.add(dn);
    String[] args = new String[argList.size()];
    ldapDelete(argList.toArray(args), rc);
  }

  private void ldapDelete(String[] args, int rc) {
    oStream.reset();
    int retVal = LDAPDelete.mainDelete(args, false, oStream, oStream);
    Assert.assertEquals(rc, retVal, "Returned error: " + oStream.toString());
  }


  private void _LDIFModify(String ldif, String bindDn, String bindPassword,
                           String controlStr, boolean add,  int rc)
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
    if(controlStr != null) {
      argList.add("-J");
      argList.add(controlStr);
    }
    if(add) {
     argList.add("-a");
    }
    argList.add("-f");
    argList.add(tempFile.getAbsolutePath());
    String[] args = new String[argList.size()];
    ldapModify(argList.toArray(args), rc);
  }

  private void ldapModify(String[] args, int rc) {
    oStream.reset();
    int retVal =LDAPModify.mainModify(args, false, oStream, oStream);
    if(rc != -1)
       Assert.assertEquals(rc, retVal, "Returned error: " + oStream.toString());
  }

  protected void deleteAttrFromEntry(String dn, String attr) throws Exception {
    StringBuilder ldif = new StringBuilder();
    ldif.append(TestCaseUtils.makeLdif(
            "dn: "  + dn,
            "changetype: modify",
            "delete: " + attr));
    LDIFModify(ldif.toString(), DIR_MGR_DN, PWD);
  }

  protected static String makeModDNLDIF(String dn, String newRDN,
                                    String deleteOldRDN,
                                    String newSuperior ) {
    StringBuilder ldif = new StringBuilder();
    ldif.append("dn: ").append(dn).append(EOL);
    ldif.append("changetype: modrdn").append(EOL);
    ldif.append("newrdn: ").append(newRDN).append(EOL);
    ldif.append("deleteoldrdn: ").append(deleteOldRDN).append(EOL);
    if(newSuperior != null)
       ldif.append("newsuperior: ").append(newSuperior).append(EOL);
    ldif.append(EOL);
    return ldif.toString();
  }

  protected static String makeDelLDIF(String attr, String dn, String... acis) {
    StringBuilder ldif = new StringBuilder();
    ldif.append("dn: ").append(dn).append(EOL);
    ldif.append("changetype: modify").append(EOL);
    ldif.append("delete: ").append(attr).append(EOL);
    for(String aci : acis)
      ldif.append(attr).append(":").append(aci).append(EOL);
    ldif.append(EOL);
    return ldif.toString();
  }

  protected static String
  makeAddEntryLDIF(String dn, String ... lines) {
    StringBuilder ldif = new StringBuilder();
    ldif.append("dn: ").append(dn).append(EOL);
    ldif.append("changetype: add").append(EOL);
    for(String l : lines)
       ldif.append(l).append(EOL);
    ldif.append(EOL);
    return ldif.toString();
  }

  protected static String makeAddLDIF(String attr, String dn, String... acis) {
    StringBuilder ldif = new StringBuilder();
    ldif.append("dn: ").append(dn).append(EOL);
    ldif.append("changetype: modify").append(EOL);
    ldif.append("add: ").append(attr).append(EOL);
    for(String aci : acis)
      ldif.append(attr).append(":").append(aci).append(EOL);
    ldif.append(EOL);
    return ldif.toString();
  }

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


  protected void addRootEntry() throws Exception {
    TestCaseUtils.addEntries(
      "dn: cn=Admin Root,cn=Root DNs,cn=config",
      "objectClass: top",
      "objectClass: person",
      "objectClass: organizationalPerson",
      "objectClass: inetOrgPerson",
      "objectClass: ds-cfg-root-dn",
      "cn: Admin Root",
      "givenName: Administrator",
      "sn: Admin",
      "uid: admin.root",
      "userPassword: password",
      "ds-privilege-name: -bypass-acl",
      "ds-cfg-alternate-bind-dn: cn=root",
      "ds-cfg-alternate-bind-dn: cn=admin",
      "ds-cfg-alternate-bind-dn: cn=admin root"
    );

  }


  protected void addEntries() throws Exception {
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
            "member: uid=user.3,ou=People,o=test",
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
            "givenName: User 1",
            "sn: 1",
            "cn: User1",
            "l: Austin",
            "manager: cn=group,ou=People,o=test",
            "userPassword: password",
            "",
            "dn: uid=user.2,ou=People,o=test",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: user.2",
            "givenName: User 2",
            "sn: 2",
            "cn: User 2",
             "l: dallas",
            "userPassword: password",
            "",
            "dn: uid=user.3,ou=People,o=test",
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: user.3",
            "givenName: User 3",
            "sn: 3",
            "mail: user.3@test",
            "description: user.3 description",
            "cn: User 3",
            "l: Austin",
            "userPassword: password",
            "ds-privilege-name: proxied-auth");
  }

  protected HashMap<String, String>
  getAttrMap(String resultString) {
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
        attrMap.put(a[0].toLowerCase(),a[1]);
      }
    } catch (IOException e) {
      Assert.assertEquals(0, 1,  e.getMessage());
    }
    return attrMap;
  }
}
