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

/**
 * Unit test to test the proxy bind functionality.
 */


package org.opends.server.authorization.dseecompat;

import java.util.Hashtable;
import javax.naming.Context;
import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/*
 * This test tests the proxy bind access control support added to allow
 * authzid's in Sasl Binds.
 */

public class ProxyBindTestCase extends AciTestCase {
    private static final String factory = "com.sun.jndi.ldap.LdapCtxFactory";
    private static final String aciEntry = "o=test";
    private static final String proxyUser="uid=proxyUser,ou=People,o=test";
    private static final String proxyUserID="proxyUser";
    private static final String proxyUserIDu="u:proxyUser";

    private static final String proxyUserURL="\"ldap:///" + proxyUser + "\"";
    private static final String aciUser="uid=aciUser,ou=People,o=test";
    private static final String aciUserID="aciUser";
    private static final String aciUserIDu="u:aciUser";
    private static final String aciUserURL = "\"ldap:///" +
                                                   aciUser + "\"";
    private static final String regUser="uid=regUser,ou=People,o=test";
    private static final String bypassAccessUser="uid=bypassAcl,ou=People,o=test";
    private static final String bypassAccessUserID="bypassAcl";
    private static final String bypassAccessUserIDu="u:bypassAcl";
    private static final String pwdPolicy = "Aci Temp Policy";

    private static final
    String aci = "(targetattr=\"*\")" +
            "(target=" + proxyUserURL + ")" +
            "(version 3.0; acl \"bypass aci\";" +
            "allow(proxy,write) userdn=" + aciUserURL + ";)";

    @BeforeClass
    public void setupClass() throws Exception {
      TestCaseUtils.startServer();
      TestCaseUtils.dsconfig(
              "set-sasl-mechanism-handler-prop",
              "--handler-name", "DIGEST-MD5",
              "--set", "server-fqdn:localhost");
      TestCaseUtils.dsconfig(
              "create-password-policy",
              "--policy-name", pwdPolicy,
              "--set", "password-attribute:userPassword",
              "--set", "default-password-storage-scheme: Clear"
              );
      addEntries("o=test");
      String addLDIF = makeAddLDIF("aci", aciEntry, aci);
      LDIFModify(addLDIF, DIR_MGR_DN, PWD);
      TestCaseUtils.addEntries(
              "dn: uid=proxyUser,ou=People,o=test",
              "objectClass: top",
              "objectClass: person",
              "objectClass: organizationalPerson",
              "objectClass: inetOrgPerson",
              "uid: proxyUser",
              "givenName: proxyUser",
              "sn: proxyUser",
              "cn: proxyUser",
              "userPassword: password",
              "ds-pwp-password-policy-dn:" +
              "cn=Aci Temp Policy,cn=Password Policies,cn=config",
              "",
              "dn: uid=aciUser,ou=People,o=test",
              "objectClass: top",
              "objectClass: person",
              "objectClass: organizationalPerson",
              "objectClass: inetOrgPerson",
              "uid: aciUser",
              "givenName: aciUser",
              "sn: aciUser",
              "cn: aciUser",
              "userPassword: password",
              "ds-privilege-name: proxied-auth",
              "ds-pwp-password-policy-dn:" +
              "cn=Aci Temp Policy,cn=Password Policies,cn=config",
              "",
              "dn: uid=bypassAcl,ou=People,o=test",
              "objectClass: top",
              "objectClass: person",
              "objectClass: organizationalPerson",
              "objectClass: inetOrgPerson",
              "uid: bypassAcl",
              "givenName: bypassAcl",
              "sn: bypassAcl",
              "cn: bypassAcl",
              "userPassword: password",
              "ds-privilege-name: bypass-acl",
              "ds-privilege-name: proxied-auth",
              "ds-pwp-password-policy-dn:" + "" +
              "cn=Aci Temp Policy,cn=Password Policies,cn=config",
              "",
              "dn: uid=regUser,ou=People,o=test",
              "objectClass: top",
              "objectClass: person",
              "objectClass: organizationalPerson",
              "objectClass: inetOrgPerson",
              "uid: regUser",
              "givenName: regUser",
              "sn: regUser",
              "cn: regUser",
              "userPassword: password",
              "ds-pwp-password-policy-dn:" +
              "cn=Aci Temp Policy,cn=Password Policies,cn=config");
    }

    @BeforeMethod(alwaysRun = true)
    public void methodSetup() throws Exception {
        deleteAttrFromAdminEntry(proxyUser, "description");
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() throws Exception {
        deleteAttrFromEntry(aciEntry, "aci");
         TestCaseUtils.dsconfig(
                 "set-sasl-mechanism-handler-prop",
                 "--handler-name", "DIGEST-MD5",
                 "--reset", "server-fqdn",
                 "--reset", "quality-of-protection");
     }

    /**
     * Test DIGEST-MD5 SASL binds using various combinations of authID and
     * authZIDs. The user binding is allowed because of an aci added.
     *
     * @throws Exception If an error occurs.
     */
    @Test()
    public void testAci() throws Exception {
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, factory);
        int port = TestCaseUtils.getServerLdapPort();
        String url = "ldap://localhost:" + Integer.valueOf(port);
        env.put(Context.PROVIDER_URL, url);
        env.put(Context.SECURITY_AUTHENTICATION, "DIGEST-MD5");
        String authID = "dn:" + aciUser;
        String authZID = "dn:" + proxyUser;
        env.put("java.naming.security.sasl.authorizationID", authZID);
        env.put(Context.SECURITY_PRINCIPAL, authID);
        env.put(Context.SECURITY_CREDENTIALS, "password");
        env.put("javax.security.sasl.qop", "auth");
        JNDIModify(env, proxyUser, "description", "a description",
                   LDAPResultCode.SUCCESS);
        deleteAttrFromAdminEntry(proxyUser, "description");
        env.put("java.naming.security.sasl.authorizationID", proxyUserID);
        env.put(Context.SECURITY_PRINCIPAL, aciUserID);
        env.put(Context.SECURITY_CREDENTIALS, "password");
        env.put("javax.security.sasl.qop", "auth");
        JNDIModify(env, proxyUser, "description", "a description",
                   LDAPResultCode.SUCCESS);
        deleteAttrFromAdminEntry(proxyUser, "description");
        env.put("java.naming.security.sasl.authorizationID", proxyUserIDu);
        env.put(Context.SECURITY_PRINCIPAL, aciUserIDu);
        env.put(Context.SECURITY_CREDENTIALS, "password");
        env.put("javax.security.sasl.qop", "auth");
        JNDIModify(env, proxyUser, "description", "a description",
                   LDAPResultCode.SUCCESS);
        deleteAttrFromAdminEntry(proxyUser, "description");
        env.put("java.naming.security.sasl.authorizationID", proxyUserID);
        env.put(Context.SECURITY_PRINCIPAL, "dn:" + regUser);
        env.put(Context.SECURITY_CREDENTIALS, "password");
        env.put("javax.security.sasl.qop", "auth");
        JNDIModify(env, proxyUser, "description", "a description",
                   LDAPResultCode.INSUFFICIENT_ACCESS_RIGHTS);
    }

    /**
     * Test DIGEST-MD5 SASL binds using various combinations of authID and
     * authZIDs. The user binding is allowed because it has bypass-acl
     * privileges.
     *
     * @throws Exception If an error occurs.
     */
    @Test()
    public void testBypass() throws Exception {
        Hashtable<String, String> env = new Hashtable<String, String>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, factory);
        int port = TestCaseUtils.getServerLdapPort();
        String url = "ldap://localhost:" + Integer.valueOf(port);
        env.put(Context.PROVIDER_URL, url);
        env.put(Context.SECURITY_AUTHENTICATION, "DIGEST-MD5");
        String authID = "dn:" + bypassAccessUser;
        String authZID = "dn:" + proxyUser;
        env.put("java.naming.security.sasl.authorizationID", authZID);
        env.put(Context.SECURITY_PRINCIPAL, authID);
        env.put(Context.SECURITY_CREDENTIALS, "password");
        env.put("javax.security.sasl.qop", "auth");
        JNDIModify(env, proxyUser, "description", "a description",
                   LDAPResultCode.SUCCESS);
        deleteAttrFromAdminEntry(proxyUser, "description");
        env.put("java.naming.security.sasl.authorizationID", bypassAccessUserID);
        env.put(Context.SECURITY_PRINCIPAL, authID);
        env.put(Context.SECURITY_CREDENTIALS, "password");
        env.put("javax.security.sasl.qop", "auth");
        JNDIModify(env, proxyUser, "description", "a description",
                   LDAPResultCode.SUCCESS);
        deleteAttrFromAdminEntry(proxyUser, "description");
        env.put("java.naming.security.sasl.authorizationID", bypassAccessUserIDu);
        env.put(Context.SECURITY_PRINCIPAL, authID);
        env.put(Context.SECURITY_CREDENTIALS, "password");
        env.put("javax.security.sasl.qop", "auth");
        JNDIModify(env, proxyUser, "description", "a description",
                   LDAPResultCode.SUCCESS);
    }
}
