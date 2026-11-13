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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.opends.server.backends;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.Connection;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.DecodeOptions;
import org.forgerock.opendj.ldap.Entry;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.opendj.ldap.controls.AssertionRequestControl;
import org.forgerock.opendj.ldap.controls.GenericControl;
import org.forgerock.opendj.ldap.controls.PostReadRequestControl;
import org.forgerock.opendj.ldap.controls.PostReadResponseControl;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.responses.Result;
import org.testng.Assert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** LDAP-only assertions shared by the OpenDJ and OpenLDAP proxy integration tests. */
public final class ProxyBackendLdapAssertions {
    private ProxyBackendLdapAssertions() { }

    private static Connection admin(ConnectionFactory factory, String dn, String password) throws LdapException {
        final Connection connection = factory.getConnection();
        try {
            connection.bind(dn, password.toCharArray());
            return connection;
        } catch (LdapException e) {
            connection.close();
            throw e;
        }
    }

    public static void assertRewriteOperations(ConnectionFactory proxyFactory, ConnectionFactory remoteFactory,
                                        String proxyBase, String remoteBase, String adminDn, String adminPassword)
            throws Exception {
        final String remoteUser = "uid=rewrite-user," + remoteBase;
        final String proxyUser = "uid=rewrite-user," + proxyBase;
        final String proxyManager = "uid=rewrite-manager," + proxyBase;
        try (Connection client = proxyFactory.getConnection()) {
            client.bind(proxyUser, "password".toCharArray());
            final Entry result = client.searchSingleEntry(proxyUser, SearchScope.BASE_OBJECT,
                    "(&(description=Remote Value)(manager=" + proxyManager + "))", "description", "manager");
            assertEquals(result.getName(), DN.valueOf(proxyUser));
            assertTrue(result.getAttribute("description").contains(ByteString.valueOfUtf8("Remote Value")));
            assertTrue(result.getAttribute("manager").contains(ByteString.valueOfUtf8(proxyManager)));
            assertThat(result.getAttribute("displayName")).isNull();

            assertEquals(client.compare(proxyUser, "description", "Remote Value").getResultCode(), ResultCode.COMPARE_TRUE);
            final ModifyRequest modify = Requests.newModifyRequest(proxyUser)
                    .addModification(ModificationType.REPLACE, "description", "Updated Value");
            modify.addControl(AssertionRequestControl.newControl(true, Filter.valueOf("(description=Remote Value)")));
            modify.addControl(PostReadRequestControl.newControl(true, "description", "manager"));
            final Result modifyResult = client.modify(modify);
            final PostReadResponseControl postRead = modifyResult.getControl(PostReadResponseControl.DECODER,
                    new DecodeOptions());
            Assert.assertNotNull(postRead);
            assertEquals(postRead.getEntry().getName(), DN.valueOf(proxyUser));
            assertTrue(postRead.getEntry().getAttribute("description").contains(ByteString.valueOfUtf8("Updated Value")));
            assertTrue(postRead.getEntry().getAttribute("manager").contains(ByteString.valueOfUtf8(proxyManager)));

            final ModifyRequest guarded = Requests.newModifyRequest(proxyUser)
                    .addModification(ModificationType.REPLACE, "description", "Must Not Appear");
            guarded.addControl(AssertionRequestControl.newControl(true, Filter.valueOf("(description=not-present)")));
            try {
                client.modify(guarded);
                Assert.fail("The false rewritten assertion must prevent the modification");
            } catch (LdapException e) {
                assertEquals(e.getResult().getResultCode(), ResultCode.ASSERTION_FAILED);
            }
            final ModifyRequest unsupported = Requests.newModifyRequest(proxyUser)
                    .addModification(ModificationType.REPLACE, "description", "Must Not Appear");
            unsupported.addControl(GenericControl.newControl("1.3.6.1.4.1.99999.999", true));
            try {
                client.modify(unsupported);
                Assert.fail("An unsupported critical control must not be forwarded without rewriting");
            } catch (LdapException e) {
                assertEquals(e.getResult().getResultCode(), ResultCode.UNAVAILABLE_CRITICAL_EXTENSION);
            }
            assertEquals(client.compare(proxyUser, "description", "Updated Value").getResultCode(), ResultCode.COMPARE_TRUE);
            assertEquals(client.compare(proxyUser, "manager", proxyManager).getResultCode(), ResultCode.COMPARE_TRUE);

            final String proxyCreated = "uid=created," + proxyBase;
            final String remoteCreated = "uid=created," + remoteBase;
            final String proxyRenamed = "uid=renamed,ou=moved," + proxyBase;
            final String remoteRenamed = "uid=renamed,ou=moved," + remoteBase;
            client.add(Requests.newAddRequest(proxyCreated)
                    .addAttribute("objectClass", "top", "person", "organizationalPerson", "inetOrgPerson")
                    .addAttribute("uid", "created").addAttribute("cn", "Created").addAttribute("sn", "Created")
                    .addAttribute("description", "Created Value").addAttribute("manager", proxyUser));
            try (Connection admin = admin(remoteFactory, adminDn, adminPassword)) {
                final Entry stored = admin.searchSingleEntry(remoteCreated, SearchScope.BASE_OBJECT,
                        "(objectClass=*)", "displayName", "manager");
                assertTrue(stored.getAttribute("displayName").contains(ByteString.valueOfUtf8("Created Value")));
                assertTrue(stored.getAttribute("manager").contains(ByteString.valueOfUtf8(remoteUser)));
            }
            client.modifyDN(Requests.newModifyDNRequest(proxyCreated, "uid=renamed")
                    .setNewSuperior(DN.valueOf("ou=moved," + proxyBase)).setDeleteOldRDN(true));
            try (Connection admin = admin(remoteFactory, adminDn, adminPassword)) {
                final Entry renamed = admin.searchSingleEntry(remoteRenamed, SearchScope.BASE_OBJECT,
                        "(objectClass=*)", "uid");
                assertTrue(renamed.getAttribute("uid").contains(ByteString.valueOfUtf8("renamed")));
                assertThat(renamed.getAttribute("uid").contains(ByteString.valueOfUtf8("created"))).isFalse();
            }
            client.delete(proxyRenamed);
        }
    }
}
