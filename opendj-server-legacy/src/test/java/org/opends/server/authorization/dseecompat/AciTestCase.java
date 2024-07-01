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
 * Portions Copyright 2013-2016 ForgeRock AS.
 */
package org.opends.server.authorization.dseecompat;

import static org.opends.server.protocols.internal.InternalClientConnection.*;
import static org.opends.server.util.CollectionUtils.*;
import static org.opends.server.util.ServerConstants.*;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;
import javax.naming.NoPermissionException;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.ModificationItem;

import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;
import org.opends.server.config.ConfigConstants;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.ModifyOperation;
import org.opends.server.protocols.ldap.LDAPResultCode;
import com.forgerock.opendj.ldap.tools.LDAPDelete;
import com.forgerock.opendj.ldap.tools.LDAPModify;
import com.forgerock.opendj.ldap.tools.LDAPPasswordModify;
import com.forgerock.opendj.ldap.tools.LDAPSearch;
import org.opends.server.types.Attribute;
import org.opends.server.types.Entry;
import org.opends.server.types.Modification;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@Test(groups = {"precommit", "dseecompat"}, sequential = true)
public abstract class  AciTestCase extends DirectoryServerTestCase {

  private Attribute globalACIAttribute;

  @BeforeClass
  public void aciTestCaseSetup() throws Exception
  {
    Reporter.log("Running aciTestCaseSetup");

    TestCaseUtils.startServer();
    TestCaseUtils.clearBackend("userRoot", "dc=example,dc=com");
    TestCaseUtils.initializeTestBackend(true);

    // Save Global ACI.
    Entry e = DirectoryServer.getEntry(DN.valueOf(ACCESS_HANDLER_DN));
    Iterator<Attribute> attrs = e.getAllAttributes(ConfigConstants.ATTR_AUTHZ_GLOBAL_ACI).iterator();
    if (attrs.hasNext())
    {
      Reporter.log("Saved global ACI attribute");

      globalACIAttribute = attrs.next();
    }
  }



  @AfterClass(alwaysRun = true)
  public void aciTestCaseTearDown() throws Exception
  {
    Reporter.log("Running aciTestCaseTearDown");

    TestCaseUtils.clearBackend("userRoot");
    TestCaseUtils.initializeTestBackend(true);

    // Restore Global ACI.
    if (globalACIAttribute != null)
    {
      Reporter.log("Restoring global ACI attribute: " + globalACIAttribute);

      Modification mod = new Modification(ModificationType.REPLACE,globalACIAttribute);
      ModifyOperation op = getRootConnection().processModify(DN.valueOf(ACCESS_HANDLER_DN), newArrayList(mod));
      Assert.assertEquals(op.getResultCode(), ResultCode.SUCCESS, "Unable to restore global ACI");
    }
  }



  public static final String DIR_MGR_DN = "cn=Directory Manager";
  public static final String PWD = "password";
  public  static final String filter = "(objectclass=*)";
  public static final String ACCESS_HANDLER_DN =
                                       "cn=Access Control Handler,cn=config";
  public static final String refURL=
          "ldap://kansashost/OU=People,O=Kansas,C=US";
  //GLOBAL ACIs

  protected static final String G_READ_ACI =
          "(targetattr!=\"userPassword||authPassword\")" +
                  "(version 3.0; acl \"Anonymous read access\";" +
                  "allow (read,search,compare) userdn=\"ldap:///anyone\";)";

  protected static final String G_SELF_MOD =
          "(targetattr=\"*\")(version 3.0; acl \"Self entry modification\";" +
                  "allow (write) userdn=\"ldap:///self\";)";

  protected static final String G_SCHEMA =
          "(target=\"ldap:///cn=schema\")(targetscope=\"base\")" +
          "(targetattr=\"attributeTypes||dITContentRules||dITStructureRules||" +
                  "ldapSyntaxes||matchingRules||matchingRuleUse||nameForms||" +
                  "objectClasses\")" +
          "(version 3.0; acl \"User-Visible Schema Operational Attributes\";" +
                  "allow (read,search,compare) userdn=\"ldap:///anyone\";)";

  protected static final String G_DSE =
          "(target=\"ldap:///\")(targetscope=\"base\")" +
               "(targetattr=\"namingContexts||supportedAuthPasswordSchemes||" +
                  "supportedControl||supportedExtension||supportedFeatures||" +
                 "supportedSASLMechanisms||vendorName||vendorVersion\")" +
        "(version 3.0; acl \"User-Visible Root DSE Operational Attributes\"; " +
                  "allow (read,search,compare) userdn=\"ldap:///anyone\";)";

  protected static final String G_USER_OPS =
          "(targetattr=\"createTimestamp||creatorsName||modifiersName||" +
                  "modifyTimestamp||entryDN||entryUUID||subschemaSubentry\")" +
                 "(version 3.0; acl \"User-Visible Operational Attributes\"; " +
                  "allow (read,search,compare) userdn=\"ldap:///anyone\";)";

  protected static final String G_CONTROL =
          "(targetcontrol = \"*\")" +
          "(version 3.0; acl \"Anonymous control access\"; " +
                  "allow (read) userdn=\"ldap:///anyone\";)";

  protected static final String E_EXTEND_OP =
          "(extop = \"*\")" +
          "(version 3.0; acl \"Anonymous extend op access\"; " +
                  "allow (read) userdn=\"ldap:///anyone\";)";

  private static final ByteArrayOutputStream oStream = new ByteArrayOutputStream();
  private static final ThreadLocal<Map<String, File>> tempLdifFile = new ThreadLocal<>();


  protected String pwdModify(String bindDn, String bindPassword,
                             String newPassword, String noOpControl,
                             String pwdPolicyControl, int expectedRc) {

    ArrayList<String> argList=new ArrayList<>(20);
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
    try (final PrintStream printStream = new PrintStream(oStream)) {
      int ret = LDAPPasswordModify.run(printStream, printStream, argList.toArray(args));
      final String output = oStream.toString();
      Assert.assertEquals(ret, expectedRc, "ldappasswordmodify output: " + output);
      return output;
    }
  }

  /**
   * Perform a modify operation, and request attributes via a preRead control.
   *
   * @param bindDn        The user to authenticate as.
   * @param bindPassword  The user's credentials.
   * @param ldif          The modification to make.
   * @param attributes    A space-separated list of attributes to return.
   *
   * @return  The output of the command.
   *
   * @throws Exception  If an unexpected problem occurred.
   */
  protected String preReadModify(String bindDn, String bindPassword,
                                 String ldif, String attributes) throws Exception
  {
    File tempFile = getTemporaryLdifFile();
    TestCaseUtils.writeFile(tempFile, ldif);

    ArrayList<String> argList=new ArrayList<>(20);
    argList.add("-h");
    argList.add("127.0.0.1");
    argList.add("-p");
    argList.add(String.valueOf(TestCaseUtils.getServerLdapPort()));
    argList.add("-D");
    argList.add(bindDn);
    argList.add("-w");
    argList.add(bindPassword);
    if (attributes != null) {
      argList.add("--preReadAttributes");
      argList.add(attributes);
    }
    argList.add("-f");
    argList.add(tempFile.getAbsolutePath());

    oStream.reset();
    return runLdapModify(argList, 0);
  }

  protected String LDAPSearchCtrl(String bindDn, String bindPassword,
                            String proxyDN, String controlStr,
                            String base, String filter, String attr) {
    ArrayList<String> argList=new ArrayList<>(20);
    argList.add("-h");
    argList.add("127.0.0.1");
    argList.add("-p");
    argList.add(String.valueOf(TestCaseUtils.getServerLdapPort()));
    argList.add("-D");
    argList.add(bindDn);
    argList.add("-w");
    argList.add(bindPassword);
    argList.add("-t");
    argList.add("0");
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
    Collections.addAll(argList, attr.split("\\s+"));
    oStream.reset();
    return runLdapSearch(argList);
  }

  protected String
  LDAPSearchParams(String bindDn,  String bindPassword,
                               String proxyDN, String authzid,
                               String[] attrList,
                               String base, String filter ,String attr,
                               boolean pwdPolicy, boolean reportAuthzID,
                               int expectedRc) {
    return _LDAPSearchParams(bindDn, bindPassword, proxyDN, authzid, attrList,
        base, filter, attr, pwdPolicy, reportAuthzID, expectedRc);
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
                            boolean pwdPolicy, boolean reportAuthzID,
                            int expectedRc) {
    List<String> argList = new ArrayList<>(20);
    argList.add("-h");
    argList.add("127.0.0.1");
    argList.add("-p");
    argList.add(String.valueOf(TestCaseUtils.getServerLdapPort()));
    argList.add("-D");
    argList.add(bindDn);
    argList.add("-w");
    argList.add(bindPassword);
    argList.add("-t");
    argList.add("0");
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
    if(attr != null) {
      Collections.addAll(argList, attr.split("\\s+"));
    }
    oStream.reset();
    return runLdapSearch(argList);
  }

  protected void LDIFAdd(String ldif, String bindDn, String bindPassword,
      String controlStr, int expectedRc) throws Exception
  {
    _LDIFModify(ldif, bindDn, bindPassword, controlStr, expectedRc, false);
  }

  protected void LDIFModify(String ldif, String bindDn, String bindPassword,
      String controlStr, int expectedRc) throws Exception
  {
    _LDIFModify(ldif, bindDn, bindPassword, controlStr, expectedRc, false);
  }

  protected void LDIFModify(String ldif, String bindDn, String bindPassword)
  throws Exception {
    _LDIFModify(ldif, bindDn, bindPassword, null, -1, false);
  }

  protected void LDIFAdminModify (String ldif, String bindDn,
                                  String bindPassword)
  throws Exception {
    _LDIFModify(ldif, bindDn, bindPassword, null, -1, true);
  }

  protected void LDIFModify(String ldif, String bindDn, String bindPassword,
                            String ctrlString)
  throws Exception {
    _LDIFModify(ldif, bindDn, bindPassword, ctrlString, -1, false);
  }

  protected void LDIFDelete(String dn, String bindDn, String bindPassword,
      String controlStr, int expectedRc)
  {
    _LDIFDelete(dn, bindDn, bindPassword, controlStr, expectedRc);
  }

  private void _LDIFDelete(String dn, String bindDn, String bindPassword,
      String controlStr, int expectedRc)
  {
    List<String> argList = new ArrayList<>(20);
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
    ldapDelete(argList.toArray(args), expectedRc);
  }

  private void ldapDelete(String[] args, int expectedRc)
  {
    oStream.reset();
    try (final PrintStream printStream = new PrintStream(oStream))
    {
      final int retVal = LDAPDelete.run(printStream, printStream, args);
      Assert.assertEquals(retVal, expectedRc, "ldapdelete output: " + oStream.toString());
    }
  }


  private void _LDIFModify(String ldif, String bindDn, String bindPassword,
      String controlStr, int expectedRc, boolean useAdminPort)
      throws Exception
  {
    File tempFile = getTemporaryLdifFile();
    TestCaseUtils.writeFile(tempFile, ldif);
    ArrayList<String> argList=new ArrayList<>(20);
    argList.add("-h");
    argList.add("127.0.0.1");
    argList.add("-p");
    if (useAdminPort) {
      argList.add(String.valueOf(TestCaseUtils.getServerAdminPort()));
      argList.add("-Z");
      argList.add("-X");
    } else {
      argList.add(String.valueOf(TestCaseUtils.getServerLdapPort()));
    }
    argList.add("-D");
    argList.add(bindDn);
    argList.add("-w");
    argList.add(bindPassword);
    if(controlStr != null) {
      argList.add("-J");
      argList.add(controlStr);
    }
    argList.add("-f");
    argList.add(tempFile.getAbsolutePath());
    runLdapModify(argList, expectedRc);
  }

  protected void JNDIModify(Hashtable<?, ?> env, String name, String attr,
      String val, int expectedRc)
  {
      try {
          DirContext ctx = new InitialDirContext(env);
          ModificationItem[] mods = new ModificationItem[1 ];
          mods[0] = new ModificationItem(DirContext.ADD_ATTRIBUTE,
                  new BasicAttribute(attr, val));
          ctx.modifyAttributes(name, mods);
          ctx.close();
      } catch (NoPermissionException npe) {
          Assert.assertEquals(LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS,
              expectedRc, "Returned error: " + npe.getMessage());
          return;
      } catch (NamingException ex) {
          Assert.assertEquals(-1, expectedRc, "Returned error: "
              + ex.getMessage());
      }
      Assert.assertEquals(LDAPResultCode.SUCCESS, expectedRc, "");
  }

  void proxyModify(String ldif, String bindDn, String bindPassword,
      String proxyUser, int expectedRc)
      throws IOException
  {
      File tempFile = getTemporaryLdifFile();
      TestCaseUtils.writeFile(tempFile, ldif);

      ArrayList<String> argList=new ArrayList<>();
      argList.add("-h");
      argList.add("127.0.0.1");
      argList.add("-p");
      argList.add(String.valueOf(TestCaseUtils.getServerLdapPort()));
      argList.add("-D");
      argList.add(bindDn);
      argList.add("-w");
      argList.add(bindPassword);
      if (proxyUser != null) {
          argList.add("-Y");
          argList.add("dn:" + proxyUser);
      }
      argList.add("-f");
      argList.add(tempFile.getAbsolutePath());
      runLdapModify(argList, expectedRc);
  }

  protected void deleteAttrFromEntry(String dn, String attr) throws Exception {
    StringBuilder ldif = new StringBuilder();
    ldif.append(TestCaseUtils.makeLdif(
            "dn: "  + dn,
            "changetype: modify",
            "delete: " + attr));
    LDIFModify(ldif.toString(), DIR_MGR_DN, PWD);
  }

  protected void deleteAttrFromAdminEntry(String dn, String attr)
  throws Exception {
    StringBuilder ldif = new StringBuilder();
    ldif.append(TestCaseUtils.makeLdif(
            "dn: "  + dn,
            "changetype: modify",
            "delete: " + attr));
    LDIFAdminModify(ldif.toString(), DIR_MGR_DN, PWD);
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
    {
      ldif.append("newsuperior: ").append(newSuperior).append(EOL);
    }
    ldif.append(EOL);
    return ldif.toString();
  }

  protected static String makeDelLDIF(String attr, String dn, String... acis) {
    StringBuilder ldif = new StringBuilder();
    ldif.append("dn: ").append(dn).append(EOL);
    ldif.append("changetype: modify").append(EOL);
    ldif.append("delete: ").append(attr).append(EOL);
    for(String aci : acis)
    {
      ldif.append(attr).append(":").append(aci).append(EOL);
    }
    ldif.append(EOL);
    return ldif.toString();
  }

  protected static String makeAddEntryLDIF(String dn, String ... lines) {
    StringBuilder ldif = new StringBuilder();
    ldif.append("dn: ").append(dn).append(EOL);
    ldif.append("changetype: add").append(EOL);
    for(String l : lines)
    {
      ldif.append(l).append(EOL);
    }
    ldif.append(EOL);
    return ldif.toString();
  }

  protected static String makeAddLDIF(String attr, String dn, String... acis) {
    StringBuilder ldif = new StringBuilder();
    ldif.append("dn: ").append(dn).append(EOL);
    ldif.append("changetype: modify").append(EOL);
    ldif.append("add: ").append(attr).append(EOL);
    for(String aci : acis)
    {
      ldif.append(attr).append(":").append(aci).append(EOL);
    }
    ldif.append(EOL);
    return ldif.toString();
  }

  private File getTemporaryLdifFile() throws IOException {
    Map<String,File> tempFilesForThisThread = tempLdifFile.get();
    if (tempFilesForThisThread == null) {
      tempFilesForThisThread = new HashMap<>();
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
      "objectClass: ds-cfg-root-dn-user",
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


  protected void addEntries(String suffix) throws Exception {
    TestCaseUtils.initializeTestBackend(true);
    TestCaseUtils.addEntries(
            "dn: ou=People," + suffix,
            "objectClass: top",
            "objectClass: organizationalUnit",
            "ou: People",
            "",
            "dn: ou=admins," + suffix ,
            "objectClass: top",
            "objectClass: organizationalUnit",
            "ou: admins",
            "",
            "dn: cn=group,ou=People," + suffix,
            "objectclass: top",
            "objectclass: groupOfNames",
            "cn: group",
            "member: uid=user.3,ou=People," + suffix,
            "",
            "dn: ou=Nested Groups," + suffix,
            "objectClass: top",
            "objectClass: organizationalUnit",
            "ou: Nested Groups",
            "",
            "dn: cn=group 1,ou=Nested Groups," + suffix,
            "objectClass: top",
            "objectClass: groupOfNames",
            "cn: group 1",
            "",
            "dn: cn=group 2,ou=Nested Groups," + suffix,
            "objectClass: top",
            "objectClass: groupOfNames",
            "cn: group 2",
            "",
            "dn: cn=group 3,ou=Nested Groups," + suffix,
            "objectClass: top",
            "objectClass: groupOfNames",
            "cn: group 3",
            "",
            "dn: cn=group 4,ou=Nested Groups," + suffix,
            "objectClass: top",
            "objectClass: groupOfURLs",
            "cn: group 4",
            "memberURL: ldap:///ou=people,o=test??sub?(sn>=5)",
            "",
            "dn: uid=superuser,ou=admins," + suffix,
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
            "dn: uid=proxyuser,ou=admins," + suffix,
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
            "dn: uid=smart referral admin,uid=proxyuser,ou=admins," + suffix,
            "objectClass: top",
            "objectClass: extensibleobject",
            "objectClass: referral",
            "ref:" + refURL,
            "ref: ldap://texashost/OU=People,O=Texas,C=US",
            "",
            "dn: uid=smart referral people,ou=people," + suffix,
            "objectClass: top",
            "objectClass: extensibleobject",
            "objectClass: referral",
            "ref:" + refURL,
            "ref: ldap://texashost/OU=People,O=Texas,C=US",
            "",
            "dn: uid=user.1,ou=People," + suffix,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: user.1",
            "givenName: User 1",
            "sn: 1",
            "cn: User1",
            "l: Austin",
            "manager: cn=group,ou=People," + suffix,
            "userPassword: password",
            "",
            "dn: uid=user.2,ou=People," + suffix,
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
            "dn: uid=user.3,ou=People," + suffix,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: user.3",
            "givenName: User 3",
            "sn: 3",
            "cn: User 3",
            "l: Austin",
            "userPassword: password",
            "ds-privilege-name: proxied-auth",
            "",
            "dn: uid=user.4,ou=People," + suffix,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: user.4",
            "givenName: User 4",
            "sn: 4",
            "cn: User 4",
            "l: ft worth",
            "userPassword: password",
            "",
            "dn: uid=user.5,ou=People," + suffix,
            "objectClass: top",
            "objectClass: person",
            "objectClass: organizationalPerson",
            "objectClass: inetOrgPerson",
            "uid: user.5",
            "givenName: User 5",
            "sn: 5",
            "mail: user.5@test",
            "description: user.5 description",
            "cn: User 5",
            "l: waco",
            "userPassword: password",
            "ds-privilege-name: proxied-auth");
  }

  protected Map<String, String> getAttrMap(String resultString)
  {
    return getAttrMap(resultString, false);
  }

  /**
   * Parse a tool output for an LDIF record, returning the attributes in a Map.
   *
   * @param resultString The entire output from the operation
   * @param stripHeader  Set to {@code true} if data before the LDIF needs to be ignored
   * @return  A map of attribute-values
   */
  protected Map<String, String> getAttrMap(String resultString, boolean stripHeader)
  {
    StringReader r = new StringReader(resultString);
    BufferedReader br = new BufferedReader(r);
    boolean stripping = stripHeader;
    Map<String, String> attrMap = new HashMap<>();
    try {
      while (true) {
        String s = br.readLine();
        if (s == null)
        {
          break;
        }
        if (stripping) {
          if (s.startsWith("dn:"))
          {
            stripping = false;
          }
          continue;
        }
        String[] a=s.split(": ");
        if (a.length != 2)
        {
          break;
        }
        attrMap.put(a[0].toLowerCase(), a[1]);
      }
    } catch (IOException e) {
      Assert.fail(e.getMessage());
    }
    return attrMap;
  }

  private String runLdapModify(final List<String> argList, final int expectedReturnCode)
  {
    try (final PrintStream printStream = new PrintStream(oStream))
    {
      final int retVal = LDAPModify.run(printStream, printStream, argList.toArray(new String[argList.size()]));
      final String output = oStream.toString();
      if (expectedReturnCode != -1)
      {
        Assert.assertEquals(retVal, expectedReturnCode, "ldapmodify output: " + output);
      }
      return output;
    }
  }

  private String runLdapSearch(final List<String> argList)
  {
    try (final PrintStream printStream = new PrintStream(oStream))
    {
      final int retVal = LDAPSearch.run(printStream, printStream, argList.toArray(new String[argList.size()]));
      final String output = oStream.toString();
      Assert.assertEquals(retVal, 0, "ldapsearch output: " + output);
      return output;
    }
  }
}
