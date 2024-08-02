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
 * Copyright 2024 3A Systems, LLC.
 */
package org.openidentityplatform.opendj;


import org.forgerock.opendj.adapter.server3x.Adapters;
import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.controls.RelaxRulesControl;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.Result;
import org.forgerock.opendj.ldap.responses.SearchResultEntry;
import org.opends.server.DirectoryServerTestCase;
import org.opends.server.TestCaseUtils;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Test(sequential = true)
public class RelaxRulesTestCase extends DirectoryServerTestCase {
    Connection connection;

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
            ""
        );

        final LDAPConnectionFactory factory =new LDAPConnectionFactory("localhost", TestCaseUtils.getServerLdapPort());
        connection = factory.getConnection();
        connection.bind("cn=Directory Manager", "password".toCharArray());
        assertThat(connection.isValid()).isTrue();
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
}
