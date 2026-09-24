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
 * Copyright 2024-2026 3A Systems, LLC.
 */
package org.openidentityplatform.opendj;


import org.forgerock.opendj.adapter.server3x.Adapters;
import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.controls.Control;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.opendj.ldap.controls.ProxiedAuthV2RequestControl;
import org.forgerock.opendj.ldap.controls.RelaxRulesControl;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Test(sequential = true)
public class RelaxRulesTestCase extends DirectoryServerTestCase {
    /** A client which may write anything under the suffix, but has no {@code bypass-acl} privilege. */
    private static final String USER_DN = "uid=relax.user,o=test";
    /** A client with the {@code bypass-acl} privilege which may act as {@link #USER_DN}. */
    private static final String PROXY_DN = "uid=relax.proxy,o=test";
    private static final String ACCESS_HANDLER_DN = "cn=Access Control Handler,cn=config";
    /** Lets any authenticated client send the Relax Rules control, so that it survives the ACI of the controls. */
    private static final String RELAX_CONTROL_ACI = "(targetcontrol=\"" + RelaxRulesControl.OID + "\")"
            + "(version 3.0; acl \"Relax Rules control access\"; allow(read) userdn=\"ldap:///all\";)";
    /** The global ACI of the test configuration which lets anyone use any control. */
    private static final String ANY_CONTROL_ACI = "(targetcontrol=\"*\")"
            + " (version 3.0; acl \"Anonymous control access\"; allow(read) userdn=\"ldap:///anyone\";)";
    private static final String OLD_TIME = "20211203224637.000Z";
    private static final String PRE_ENCODED_PASSWORD = "{SSHA}K9Hv0w7Z0Q2yL3ZJmD1m4n8q3mYk1Xn1R0x6Xw==";

    Connection connection;
    private LDAPConnectionFactory factory;

    @BeforeClass
    public void startServer() throws Exception {
        TestCaseUtils.startServer();
        TestCaseUtils.initializeTestBackend(true);

        TestCaseUtils.addEntries(
           "dn: uid=user.2, o=test",
            "objectClass: top",
            "objectClass: person",
            "objectClass: inetOrgPerson",
            "objectClass: organizationalPerson",
            "cn: Aarika Atpco",
            "sn: user.2",
            "uid:user.2",
            "description: This is the description for Aarika Atpco.",
            "userPassword:: cGFzc3dvcmQ=",
            "postalAddress: Aarika Atpco$00900 Maple Street$New Orleans, KS  10857",
            "postalCode: 10857",
            "",
            "dn: " + USER_DN,
            "objectClass: top",
            "objectClass: person",
            "objectClass: inetOrgPerson",
            "objectClass: organizationalPerson",
            "cn: Relax User",
            "sn: User",
            "uid: relax.user",
            "userPassword: password",
            "",
            "dn: " + PROXY_DN,
            "objectClass: top",
            "objectClass: person",
            "objectClass: inetOrgPerson",
            "objectClass: organizationalPerson",
            "cn: Relax Proxy",
            "sn: Proxy",
            "uid: relax.proxy",
            "userPassword: password",
            "ds-privilege-name: bypass-acl",
            "ds-privilege-name: proxied-auth"
        );

        factory = new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort());
        connection = factory.getConnection();
        connection.bind("cn=Directory Manager", "password".toCharArray());
        assertThat(connection.isValid()).isTrue();

        // The user may write anything under the suffix, operational attributes included: what stops it
        // below is the Relax Rules control alone.
        connection.modify(Requests.newModifyRequest("o=test").addModification(ModificationType.ADD, "aci",
                "(targetattr=\"*||+\")(version 3.0; acl \"Relax Rules test user\"; allow(all) userdn=\"ldap:///"
                        + USER_DN + "\";)"));
    }

    @AfterClass(alwaysRun = true)
    public void closeConnection() {
        if (connection != null) {
            connection.close();
        }
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    public void test() throws LdapException {
        final ModifyRequest changeRequest =
                Requests.newModifyRequest("uid=user.2, o=test")
                        .addControl(new RelaxRulesControl())
                        .addModification(ModificationType.REPLACE, "pwdChangedTime", "20211203224637.000Z");

        final Result result = connection.modify(changeRequest);
        assertThat(result.getDiagnosticMessage()).isEmpty();
        assertThat(result.getMatchedDN()).isEmpty();

        //Verifies that entry has been correctly modified.
        final SearchResultEntry srEntry =
                connection.searchSingleEntry(Requests.newSearchRequest(
                        "uid=user.2, o=test", SearchScope.BASE_OBJECT, "(uid=user.2)").addAttribute("+"));
        assertThat(srEntry.getAttribute("pwdChangedTime").firstValueAsString()).isEqualTo(
                "20211203224637.000Z");
    }

    @Test
    public void aRelaxedModifyStillRunsThePreOperationPlugins() throws Exception {
        final String dn = addPerson("plugins");

        connection.modify(Requests.newModifyRequest(dn)
                .addControl(new RelaxRulesControl())
                .addModification(ModificationType.REPLACE, "description", "relaxed"));

        assertThat(valueOf(dn, "modifiersName")).as("the last modified plugin did not run")
                .isEqualToIgnoringCase("cn=Directory Manager,cn=Root DNs,cn=config");
    }

    @Test
    public void aRelaxedModifyKeepsTheLastModifiedValuesTheClientSupplies() throws Exception {
        final String dn = addPerson("lastmod modify");

        connection.modify(Requests.newModifyRequest(dn)
                .addControl(new RelaxRulesControl())
                .addModification(ModificationType.REPLACE, "modifyTimestamp", OLD_TIME)
                .addModification(ModificationType.REPLACE, "modifiersName", "cn=migrated"));

        assertThat(valueOf(dn, "modifyTimestamp")).isEqualTo(OLD_TIME);
        assertThat(valueOf(dn, "modifiersName")).isEqualTo("cn=migrated");
    }

    @Test
    public void aRelaxedAddKeepsTheCreationValuesTheClientSupplies() throws Exception {
        final String dn = "cn=lastmod add,o=test";

        connection.add(person(dn)
                .addAttribute("createTimestamp", OLD_TIME)
                .addAttribute("creatorsName", "cn=migrated")
                .addControl(new RelaxRulesControl()));

        assertThat(valueOf(dn, "createTimestamp")).isEqualTo(OLD_TIME);
        assertThat(valueOf(dn, "creatorsName")).isEqualTo("cn=migrated");
    }

    @Test
    public void aRelaxedModifyKeepsAMigratedPasswordAndItsChangeTime() throws Exception {
        final String dn = addPerson("password modify");

        connection.modify(Requests.newModifyRequest(dn)
                .addControl(new RelaxRulesControl())
                .addModification(ModificationType.REPLACE, "userPassword", PRE_ENCODED_PASSWORD)
                .addModification(ModificationType.REPLACE, "pwdChangedTime", OLD_TIME));

        assertThat(valueOf(dn, "userPassword")).isEqualTo(PRE_ENCODED_PASSWORD);
        assertThat(valueOf(dn, "pwdChangedTime")).isEqualTo(OLD_TIME);
    }

    @Test
    public void aRelaxedAddKeepsAMigratedPasswordAndItsChangeTime() throws Exception {
        final String dn = "cn=password add,o=test";

        connection.add(person(dn)
                .addAttribute("userPassword", PRE_ENCODED_PASSWORD)
                .addAttribute("pwdChangedTime", OLD_TIME)
                .addControl(new RelaxRulesControl()));

        assertThat(valueOf(dn, "userPassword")).isEqualTo(PRE_ENCODED_PASSWORD);
        assertThat(valueOf(dn, "pwdChangedTime")).isEqualTo(OLD_TIME);
    }

    @Test
    public void aRelaxedAddSkipsTheSchemaCheck() throws Exception {
        // person does not allow mail.
        assertThat(resultOf(() -> connection.add(person("cn=schema add,o=test").addAttribute("mail", "x@example.com"))))
                .isEqualTo(ResultCode.OBJECTCLASS_VIOLATION);

        connection.add(person("cn=schema add,o=test")
                .addAttribute("mail", "x@example.com")
                .addControl(new RelaxRulesControl()));

        assertThat(valueOf("cn=schema add,o=test", "mail")).isEqualTo("x@example.com");
    }

    @Test
    public void aRelaxedModifySkipsTheSchemaCheck() throws Exception {
        final String dn = addPerson("schema modify");
        final ModifyRequest addMail = Requests.newModifyRequest(dn)
                .addModification(ModificationType.ADD, "mail", "x@example.com");
        assertThat(resultOf(() -> connection.modify(addMail))).isEqualTo(ResultCode.OBJECTCLASS_VIOLATION);

        connection.modify(Requests.copyOfModifyRequest(addMail).addControl(new RelaxRulesControl()));

        assertThat(valueOf(dn, "mail")).isEqualTo("x@example.com");
    }

    @Test
    public void aRelaxedModifyMayWriteAnObsoleteAttribute() throws Exception {
        final String obsoleteType = "( 1.3.6.1.4.1.36733.2.1.999.1053 NAME 'relaxRulesObsoleteTest' OBSOLETE"
                + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 X-ORIGIN 'RelaxRulesTestCase' )";
        connection.modify(Requests.newModifyRequest("cn=schema")
                .addModification(ModificationType.ADD, "attributeTypes", obsoleteType));
        try {
            final String dn = "cn=obsolete,o=test";
            connection.add(Requests.newAddRequest(dn)
                    .addAttribute("objectClass", "top", "person", "extensibleObject")
                    .addAttribute("cn", "obsolete")
                    .addAttribute("sn", "obsolete"));
            final ModifyRequest writeObsolete = Requests.newModifyRequest(dn)
                    .addModification(ModificationType.REPLACE, "relaxRulesObsoleteTest", "value");
            assertThat(resultOf(() -> connection.modify(writeObsolete))).isEqualTo(ResultCode.CONSTRAINT_VIOLATION);

            connection.modify(Requests.copyOfModifyRequest(writeObsolete).addControl(new RelaxRulesControl()));

            assertThat(valueOf(dn, "relaxRulesObsoleteTest")).isEqualTo("value");
            connection.delete(dn);
        } finally {
            connection.modify(Requests.newModifyRequest("cn=schema")
                    .addModification(ModificationType.DELETE, "attributeTypes", obsoleteType));
        }
    }

    /**
     * A non-critical control the client may not use is dropped (RFC 4511 4.1.11): the request is then an
     * ordinary one, and succeeds as such.
     */
    @Test
    public void aNonCriticalControlTheClientMayNotUseIsIgnored() throws Exception {
        final String dn = addPerson("ignored control");
        try (ControlAccess restricted = restrictControlAccess(null);
             Connection user = bindAsUser()) {
            user.modify(Requests.newModifyRequest(dn)
                    .addControl(nonCriticalRelaxRules())
                    .addModification(ModificationType.REPLACE, "description", "ordinary"));
            user.add(person("cn=ignored control add,o=test").addControl(nonCriticalRelaxRules()));
        }

        assertThat(valueOf(dn, "description")).isEqualTo("ordinary");
        assertThat(exists("cn=ignored control add,o=test")).isTrue();
    }

    /** Once the control ACI lets the control through, only the {@code bypass-acl} privilege lets it relax anything. */
    @Test
    public void aClientWithoutBypassAclMayNotRelaxTheRules() throws Exception {
        final String dn = addPerson("unprivileged");
        try (ControlAccess relaxAllowed = restrictControlAccess(RELAX_CONTROL_ACI);
             Connection user = bindAsUser()) {
            assertThat(resultOf(() -> user.add(person("cn=unprivileged add,o=test")
                    .addAttribute("pwdChangedTime", OLD_TIME)
                    .addControl(new RelaxRulesControl()))))
                    .isNotEqualTo(ResultCode.SUCCESS);
            assertThat(resultOf(() -> user.add(person("cn=unprivileged plain add,o=test")
                    .addControl(new RelaxRulesControl()))))
                    .isEqualTo(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
            assertThat(resultOf(() -> user.modify(Requests.newModifyRequest(dn)
                    .addControl(new RelaxRulesControl())
                    .addModification(ModificationType.REPLACE, "pwdChangedTime", OLD_TIME))))
                    .isEqualTo(ResultCode.INSUFFICIENT_ACCESS_RIGHTS);
        }

        assertThat(exists("cn=unprivileged add,o=test")).isFalse();
        assertThat(exists("cn=unprivileged plain add,o=test")).isFalse();
        assertThat(valueOf(dn, "pwdChangedTime")).isNull();
    }

    /**
     * The privilege which lets the rules be relaxed is the one of the identity the request runs as: a
     * client with {@code bypass-acl} which proxies as an ordinary user relaxes nothing, whether the control
     * ACI lets the control through for that user or not.
     */
    @Test
    public void aProxiedIdentityWithoutBypassAclMayNotRelaxTheRules() throws Exception {
        assertAProxiedRelaxedChangeIsRefused("proxied kept");
        try (ControlAccess restricted = restrictControlAccess(null)) {
            assertAProxiedRelaxedChangeIsRefused("proxied dropped");
        }
    }

    private void assertAProxiedRelaxedChangeIsRefused(String cn) throws Exception {
        final String dn = addPerson(cn);
        final String addedDN = "cn=" + cn + " add,o=test";
        final ProxiedAuthV2RequestControl asUser = ProxiedAuthV2RequestControl.newControl("dn:" + USER_DN);

        try (Connection proxy = factory.getConnection()) {
            proxy.bind(PROXY_DN, "password".toCharArray());
            // Without the Relax Rules control, the proxy may act as the user.
            proxy.modify(Requests.newModifyRequest(dn)
                    .addControl(asUser)
                    .addModification(ModificationType.REPLACE, "description", "proxied"));

            assertThat(resultOf(() -> proxy.add(person(addedDN)
                    .addAttribute("pwdChangedTime", OLD_TIME)
                    .addControl(asUser)
                    .addControl(nonCriticalRelaxRules()))))
                    .isNotEqualTo(ResultCode.SUCCESS);
            assertThat(resultOf(() -> proxy.modify(Requests.newModifyRequest(dn)
                    .addControl(asUser)
                    .addControl(nonCriticalRelaxRules())
                    .addModification(ModificationType.REPLACE, "pwdChangedTime", OLD_TIME))))
                    .isNotEqualTo(ResultCode.SUCCESS);
        }

        assertThat(valueOf(dn, "modifiersName")).isEqualToIgnoringCase(USER_DN);
        assertThat(exists(addedDN)).isFalse();
        assertThat(valueOf(dn, "pwdChangedTime")).isNull();
    }

    /**
     * Removes the global ACI of the test configuration which lets anyone use any control, adding the provided
     * one instead if any, until closed.
     */
    private ControlAccess restrictControlAccess(String aci) throws LdapException {
        connection.modify(Requests.newModifyRequest(ACCESS_HANDLER_DN)
                .addModification(ModificationType.DELETE, "ds-cfg-global-aci", ANY_CONTROL_ACI));
        if (aci != null) {
            connection.modify(Requests.newModifyRequest(ACCESS_HANDLER_DN)
                    .addModification(ModificationType.ADD, "ds-cfg-global-aci", aci));
        }
        return () -> {
            if (aci != null) {
                connection.modify(Requests.newModifyRequest(ACCESS_HANDLER_DN)
                        .addModification(ModificationType.DELETE, "ds-cfg-global-aci", aci));
            }
            connection.modify(Requests.newModifyRequest(ACCESS_HANDLER_DN)
                    .addModification(ModificationType.ADD, "ds-cfg-global-aci", ANY_CONTROL_ACI));
        };
    }

    private interface ControlAccess extends AutoCloseable {
        @Override
        void close() throws LdapException;
    }

    /** {@link RelaxRulesControl} is always critical. */
    private static Control nonCriticalRelaxRules() {
        return GenericControl.newControl(RelaxRulesControl.OID, false);
    }

    private String addPerson(String cn) throws LdapException {
        final String dn = "cn=" + cn + ",o=test";
        connection.add(person(dn));
        return dn;
    }

    private static AddRequest person(String dn) {
        final String cn = DN.valueOf(dn).rdn().getFirstAVA().getAttributeValue().toString();
        return Requests.newAddRequest(dn)
                .addAttribute("objectClass", "top", "person")
                .addAttribute("cn", cn)
                .addAttribute("sn", cn);
    }

    private Connection bindAsUser() throws LdapException {
        final Connection user = factory.getConnection();
        user.bind(USER_DN, "password".toCharArray());
        return user;
    }

    private String valueOf(String dn, String attribute) throws LdapException {
        final SearchResultEntry entry = connection.searchSingleEntry(
                Requests.newSearchRequest(dn, SearchScope.BASE_OBJECT, "(objectClass=*)").addAttribute("*", "+"));
        final Attribute attr = entry.getAttribute(attribute);
        return attr != null ? attr.firstValueAsString() : null;
    }

    private boolean exists(String dn) {
        return resultOf(() -> connection.searchSingleEntry(
                Requests.newSearchRequest(dn, SearchScope.BASE_OBJECT, "(objectClass=*)"))) == ResultCode.SUCCESS;
    }

    private interface Request {
        Object send() throws LdapException;
    }

    private static ResultCode resultOf(Request request) {
        try {
            request.send();
            return ResultCode.SUCCESS;
        } catch (LdapException e) {
            return e.getResult().getResultCode();
        }
    }
}
