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

import static org.testng.Assert.*;
import static org.opends.server.TestCaseUtils.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.ServerConstants.*;

import java.util.Map;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** This class tests ACI behavior using alternate root bind DNs. */
@SuppressWarnings("javadoc")
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

  /** Need an ACI to allow proxy control. */
  private static final
  String controlACI = "(targetcontrol=\"" + OID_PROXIED_AUTH_V2 + "\")" +
          "(version 3.0; acl \"control\";" +
          "allow(read) userdn=\"ldap:///" + user3 + "\";)";

  private static final
  String rootDNACI= "(targetattr=\"" + ATTR_USER_PASSWORD + "\")" +
        "(version 3.0; acl \"pwd search, read " + rootDN + "\";" +
        "allow(read, search) userdn=\"ldap:///" + rootDN + "\";)";

  @BeforeClass
  public void setupClass() throws Exception {
    deleteAttrFromAdminEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
    addEntries("o=test");
    addRootEntry();
  }

  @BeforeMethod
  public void clearBackend() throws Exception {
    deleteAttrFromEntry(user1, "aci");
    deleteAttrFromAdminEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
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
  @Test
  public void testAlternateDNs() throws Exception {
    String aciLdif=makeAddLDIF("aci", user1, rootDNACI);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    String adminDNResults =
            LDAPSearchParams(adminDN, PWD, null, null, null,
                    user1, pwdFilter, ATTR_USER_PASSWORD);
    assertNotEquals(adminDNResults, "");
    Map<String, String> attrMap = getAttrMap(adminDNResults);
    assertTrue(attrMap.containsKey(ATTR_USER_PASSWORD));
    String adminRootDNResults =
            LDAPSearchParams(adminRootDN, PWD, null, null, null,
                    user1, pwdFilter, ATTR_USER_PASSWORD);
    assertNotEquals(adminRootDNResults, "");
    Map<String, String> attrMap1 = getAttrMap(adminRootDNResults);
    assertTrue(attrMap1.containsKey(ATTR_USER_PASSWORD));
    String rootDNResults =
            LDAPSearchParams(rootDN, PWD, null, null, null,
                    user1, pwdFilter, ATTR_USER_PASSWORD);
    assertNotEquals(rootDNResults, "");
    Map<String, String> attrMap2 = getAttrMap(rootDNResults);
    assertTrue(attrMap2.containsKey(ATTR_USER_PASSWORD));
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
  @Test
  public void testAlternateProxyDNs() throws Exception {
    String aciLdif=makeAddLDIF("aci", user1, rootDNACI, proxyACI, controlACI);
    LDIFModify(aciLdif, DIR_MGR_DN, PWD);
    String adminDNResults =
            LDAPSearchParams(user3, PWD, adminDN, null, null,
                    user1, pwdFilter, ATTR_USER_PASSWORD);
    assertNotEquals(adminDNResults, "");
    Map<String, String> attrMap = getAttrMap(adminDNResults);
    assertTrue(attrMap.containsKey(ATTR_USER_PASSWORD));
    String adminRootDNResults =
            LDAPSearchParams(user3, PWD, adminRootDN, null, null,
                    user1, pwdFilter, ATTR_USER_PASSWORD);
    assertNotEquals(adminRootDNResults, "");
    Map<String, String> attrMap1 = getAttrMap(adminRootDNResults);
    assertTrue(attrMap1.containsKey(ATTR_USER_PASSWORD));
    String rootDNResults =
            LDAPSearchParams(user3, PWD, adminDN, null, null,
                    user1, pwdFilter, ATTR_USER_PASSWORD);
    assertNotEquals(rootDNResults, "");
    Map<String, String> attrMap2 = getAttrMap(rootDNResults);
    assertTrue(attrMap2.containsKey(ATTR_USER_PASSWORD));
    deleteAttrFromEntry(user1, "aci");
  }
}
