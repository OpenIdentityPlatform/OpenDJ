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
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.Assert;
import org.opends.server.TestCaseUtils;
import static org.opends.server.config.ConfigConstants.*;
import java.util.HashMap;
import static org.opends.server.util.ServerConstants.*;

/**
 * This class tests ACI behavior using alternate root bind DNs.
 */
public class AlternateRootDN extends AciTestCase {

  private static final String user1="uid=user.1,ou=People,o=test";
  private static final String user3="uid=user.3,ou=People,o=test";
  private static final String pwdFilter = "(" + ATTR_USER_PASSWORD + "=*)";
  private static final String rootDN="cn=root";
  private static final String adminRootDN="cn=admin root";
  private static final String adminDN="cn=admin";

  private static final
  String proxyACI = "(targetattr = \"*\")" +
       "(version 3.0; acl \"proxy" +  user3 + "\";" +
       "allow (proxy) userdn=\"ldap:///" + user3 + "\";)";

  //Need an ACI to allow proxy control
  String controlACI = "(targetcontrol=\"" + OID_PROXIED_AUTH_V2 + "\")" +
          "(version 3.0; acl \"control\";" +
          "allow(read) userdn=\"ldap:///" + user3 + "\";)";

  private static final
  String rootDNACI= "(targetattr=\"" + ATTR_USER_PASSWORD + "\")" +
        "(version 3.0; acl \"pwd search, read " + rootDN + "\";" +
        "allow(read, search) userdn=\"ldap:///" + rootDN + "\";)";


  @BeforeClass
  public void setupClass() throws Exception {
    TestCaseUtils.startServer();
    deleteAttrFromEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
    addEntries();
    addRootEntry();
  }

  @AfterClass
  public void tearDown() throws Exception {
    String aciLdif=makeAddLDIF(ATTR_AUTHZ_GLOBAL_ACI, ACCESS_HANDLER_DN,
            G_READ_ACI, G_SELF_MOD, G_SCHEMA, G_DSE, G_USER_OPS, G_CONTROL,
            E_EXTEND_OP);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
  }


  @BeforeMethod
  public void clearBackend() throws Exception {
    deleteAttrFromEntry(user1, "aci");
    deleteAttrFromEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
  }

  /**
   * This test uses an ACI allowing access to the userPassword attribute, based
   * on one of the alternate bind DNs of a root entry. The root entry does not
   * have bypass-acl privileges (-bypass-acl), so searches will pass through
   * to the ACI system. Searches are performed, binding as each of the
   * alternate DNS. All searches should succeed.
   *
   * @throws Exception  If an unexpected result is received.
   */
  @Test()
  public void testAlternateDNs() throws Exception {
    String aciLdif=makeAddLDIF("aci", user1, rootDNACI);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    String adminDNResults =
            LDAPSearchParams(adminDN, PWD, null, null, null,
                    user1, pwdFilter, ATTR_USER_PASSWORD);
    Assert.assertFalse(adminDNResults.equals(""));
    HashMap<String, String> attrMap=getAttrMap(adminDNResults);
    Assert.assertTrue(attrMap.containsKey(ATTR_USER_PASSWORD));
    String adminRootDNResults =
            LDAPSearchParams(adminRootDN, PWD, null, null, null,
                    user1, pwdFilter, ATTR_USER_PASSWORD);
    Assert.assertFalse(adminRootDNResults.equals(""));
    HashMap<String, String> attrMap1=getAttrMap(adminRootDNResults);
    Assert.assertTrue(attrMap1.containsKey(ATTR_USER_PASSWORD));
    String rootDNResults =
            LDAPSearchParams(rootDN, PWD, null, null, null,
                    user1, pwdFilter, ATTR_USER_PASSWORD);
    Assert.assertFalse(rootDNResults.equals(""));
    HashMap<String, String> attrMap2=getAttrMap(rootDNResults);
    Assert.assertTrue(attrMap2.containsKey(ATTR_USER_PASSWORD));
    deleteAttrFromEntry(user1, "aci");
  }



  /**
   * This test uses two ACIs, one allowing proxy authorization to a user, and
   * the other allowing access to the userPassword attribute based on one of the
   * alternate bind DNs of a root entry. The root entry does not have bypass-acl
   * privileges (-bypass-acl), so searches will pass through to the ACI system.
   * Searches are performed binding as a user, but proxying as each of the
   * alternate bind DNs. All searches should succeed.
   *
   * @throws Exception  If an unexpected result is received.
   */
  @Test()
  public void testAlternateProxyDNs() throws Exception {
    String aciLdif=makeAddLDIF("aci", user1, rootDNACI, proxyACI, controlACI);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    String adminDNResults =
            LDAPSearchParams(user3, PWD, adminDN, null, null,
                    user1, pwdFilter, ATTR_USER_PASSWORD);
    Assert.assertFalse(adminDNResults.equals(""));
    HashMap<String, String> attrMap=getAttrMap(adminDNResults);
    Assert.assertTrue(attrMap.containsKey(ATTR_USER_PASSWORD));
    String adminRootDNResults =
            LDAPSearchParams(user3, PWD, adminRootDN, null, null,
                    user1, pwdFilter, ATTR_USER_PASSWORD);
    Assert.assertFalse(adminRootDNResults.equals(""));
    HashMap<String, String> attrMap1=getAttrMap(adminRootDNResults);
    Assert.assertTrue(attrMap1.containsKey(ATTR_USER_PASSWORD));
    String rootDNResults =
            LDAPSearchParams(user3, PWD, adminDN, null, null,
                    user1, pwdFilter, ATTR_USER_PASSWORD);
    Assert.assertFalse(rootDNResults.equals(""));
    HashMap<String, String> attrMap2=getAttrMap(rootDNResults);
    Assert.assertTrue(attrMap2.containsKey(ATTR_USER_PASSWORD));
    deleteAttrFromEntry(user1, "aci");
  }
}
