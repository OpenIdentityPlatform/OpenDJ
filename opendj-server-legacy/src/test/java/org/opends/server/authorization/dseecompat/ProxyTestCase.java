/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2015 ForgeRock AS
 */
package org.opends.server.authorization.dseecompat;

import org.opends.server.TestCaseUtils;
import org.opends.server.protocols.ldap.LDAPResultCode;
import org.opends.server.types.Entry;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.opends.server.config.ConfigConstants.ATTR_AUTHZ_GLOBAL_ACI;

/**
 * This class tests ACI behavior with the proxy auth control.
 */
public class ProxyTestCase extends AciTestCase
{
    static final String TEST_BASE = "o=test";
    static final String ALICE_DN = "uid=alice,ou=People," + TEST_BASE;
    static final String BOB_DN = "uid=bob,ou=People," + TEST_BASE;
    static final String CHARLIE_DN = "uid=charlie,ou=People," + TEST_BASE;
    static final String PASSWORD = "password";

    @BeforeClass
    public void setupClass() throws Exception
    {
        deleteAttrFromAdminEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
        TestCaseUtils.initializeTestBackend(true);
        TestCaseUtils.addEntries(
                "dn: ou=People," + TEST_BASE,
                "objectClass: top",
                "objectClass: organizationalUnit",
                "ou: People",
                "aci: (targetcontrol=\"2.16.840.1.113730.3.4.18\")" +
                        "(version 3.0; acl \"Allow proxy auth control\"; " +
                        "allow (read) userdn = \"ldap:///" + ALICE_DN + "\";)",
                "aci: (target=\"ldap:///" + CHARLIE_DN + "\")(targetattr = \"telephoneNumber\")" +
                        "(version 3.0; acl \"Allow Bob to write Charlie\"; " +
                        "allow (write) userdn = \"ldap:///" + BOB_DN + "\";)",
                "aci: (target=\"ldap:///ou=People," + TEST_BASE + "\")" +
                        "(version 3.0; acl \"Allow Alice to proxy People\"; " +
                        "allow (proxy) userdn = \"ldap:///" + ALICE_DN + "\";)",
                "",
                "dn: " + ALICE_DN,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "uid: alice",
                "cn: Alice",
                "sn: User",
                "ds-privilege-name: proxied-auth",
                "userPassword: " + PASSWORD,
                "",
                "dn: uid=bob,ou=People," + TEST_BASE,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "uid: bob",
                "cn: Bob",
                "sn: User",
                "userPassword: " + PASSWORD,
                "",
                "dn: uid=charlie,ou=People," + TEST_BASE,
                "objectClass: top",
                "objectClass: person",
                "objectClass: organizationalPerson",
                "objectClass: inetOrgPerson",
                "uid: charlie",
                "cn: Charlie",
                "sn: User",
                "userPassword: " + PASSWORD,
                "",
                "dn: ou=Groups," + TEST_BASE,
                "objectClass: top",
                "objectClass: organizationalUnit",
                "ou: Groups",
                "",
                "dn: cn=Writable by Bob,ou=Groups," + TEST_BASE,
                "objectClass: top",
                "objectClass: groupOfEntries",
                "cn: Writable by Bob",
                "aci: (targetattr=\"description\")" +
                        "(version 3.0; acl \"Bob writes\"; " +
                        "allow(write) userdn=\"ldap:///" + BOB_DN + "\";)",
                "",
                "dn: cn=Visible to Bob,ou=Groups," + TEST_BASE,
                "objectClass: top",
                "objectClass: groupOfEntries",
                "cn: Visible to Bob",
                "description: Bob can read this group",
                "aci: (targetattr=\"*||+\")" +
                        "(version 3.0; acl \"Bob visible\"; " +
                        "allow(read,search) userdn=\"ldap:///" + BOB_DN + "\";)",
                "",
                "dn: cn=Invisible to Bob,ou=Groups," + TEST_BASE,
                "objectClass: top",
                "objectClass: groupOfEntries",
                "cn: Invisible to Bob",
                "description: Bob cannot see this group",
                "aci: (targetattr=\"*||+\")" +
                        "(version 3.0; acl \"Bob invisible\"; " +
                        "deny(read,search) userdn=\"ldap:///" + BOB_DN + "\";)",
                "");
    }


    @BeforeMethod
    public void clearBackend() throws Exception
    {
        deleteAttrFromAdminEntry(ACCESS_HANDLER_DN, ATTR_AUTHZ_GLOBAL_ACI);
    }

    /**
     * Test Alice cannot proxy as Root.
     *
     * @throws Exception If an unexpected result is returned.
     */
    @Test
    public void testProxyAsRoot() throws Exception
    {
        proxyModify(TestCaseUtils.makeLdif(
            "dn: " + CHARLIE_DN,
            "changetype: modify",
            "replace: telephoneNumber",
            "telephoneNumber: 999"),
            ALICE_DN, PASSWORD,
            DIR_MGR_DN,
            LDAPResultCode.AUTHORIZATION_DENIED);
    }

    /**
     * Test Alice (as Bob) modifies Charlie.
     *
     * @throws Exception If an unexpected result is returned.
     */
    @Test
    public void testSimpleProxy() throws Exception
    {
        proxyModify(TestCaseUtils.makeLdif(
             "dn: " + CHARLIE_DN,
             "changetype: modify",
             "replace: telephoneNumber",
             "telephoneNumber: 999"),
             ALICE_DN, PASSWORD,
             BOB_DN,
             LDAPResultCode.SUCCESS);
    }

    /**
     * Test Alice (as Bob) modifies an entry outside of the proxy target scope.
     *
     * @throws Exception If an unexpected result is returned.
     */
    @Test
    public void testUpdateGroup() throws Exception
    {
        proxyModify(TestCaseUtils.makeLdif(
             "dn: cn=Writable by Bob,ou=Groups," + TEST_BASE,
             "changetype: modify",
             "replace: description",
             "description: written by Alice (Bob)"),
             ALICE_DN, PASSWORD,
             BOB_DN,
             LDAPResultCode.SUCCESS);
    }

    /**
     * Test Alice (as Bob) can see entries that Bob can see.
     * Only "cn=Visible to Bob,ou=Groups,..." should be returned.
     *
     * @throws Exception If an unexpected result is returned.
     */
    @Test
    public void testProxiedSearch() throws Exception
    {
        String results = LDAPSearchParams(ALICE_DN, PASSWORD, BOB_DN, null, null,
                TEST_BASE, "(&)", null,
                false, false, LDAPResultCode.SUCCESS);
        List<Entry> entries = TestCaseUtils.entriesFromLdifString(results);
        Assert.assertEquals(entries.size(), 1, "Wrong number of results");
    }
}
