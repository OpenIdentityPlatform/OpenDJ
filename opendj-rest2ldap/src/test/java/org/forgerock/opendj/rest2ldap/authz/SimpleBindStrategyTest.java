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
package org.forgerock.opendj.rest2ldap.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.forgerock.opendj.ldap.Connections.newInternalConnectionFactory;
import static org.forgerock.opendj.rest2ldap.authz.AuthenticationStrategies.newSimpleBindStrategy;
import static org.forgerock.services.context.SecurityContext.AUTHZID_DN;
import static org.forgerock.services.context.SecurityContext.AUTHZID_ID;

import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.LdapException;
import org.forgerock.opendj.ldap.MemoryBackend;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.services.context.RootContext;
import org.forgerock.services.context.SecurityContext;
import org.forgerock.testng.ForgeRockTestCase;
import org.forgerock.util.promise.Promise;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
@SuppressWarnings("javadoc")
public final class SimpleBindStrategyTest extends ForgeRockTestCase {
    private static final String USER_DN = "uid=bjensen,ou=People,dc=example,dc=com";

    private ConnectionFactory factory;

    @BeforeMethod
    public void setUp() throws Exception {
        factory = newInternalConnectionFactory(new MemoryBackend(new LDIFEntryReader(
                "dn: dc=example,dc=com",
                "objectClass: domain",
                "dc: example",
                "",
                "dn: ou=People,dc=example,dc=com",
                "objectClass: organizationalUnit",
                "ou: People",
                "",
                "dn: " + USER_DN,
                "objectClass: inetOrgPerson",
                "uid: bjensen",
                "cn: Barbara Jensen",
                "sn: Jensen",
                "userPassword: secret")));
    }

    /** The documented default template, {@code {username}}, takes the user name as the bind DN. */
    @Test
    public void testDefaultTemplateTakesTheUserNameAsTheBindDn() throws Exception {
        final SecurityContext context = newSimpleBindStrategy(factory, "%s", Schema.getDefaultSchema())
                .authenticate(USER_DN, "secret", new RootContext()).getOrThrow();

        assertThat(context.getAuthorization().get(AUTHZID_DN)).isEqualTo(USER_DN);
        assertThat(context.getAuthorization().get(AUTHZID_ID)).isEqualTo(USER_DN);
    }

    @Test
    public void testTemplateTakesTheUserNameAsAnAttributeValue() throws Exception {
        final SecurityContext context =
                newSimpleBindStrategy(factory, "uid=%s,ou=People,dc=example,dc=com", Schema.getDefaultSchema())
                        .authenticate("bjensen", "secret", new RootContext()).getOrThrow();

        assertThat(context.getAuthorization().get(AUTHZID_DN)).isEqualTo(USER_DN);
    }

    /** A user name inside a template stays one attribute value: it cannot add RDNs of its own. */
    @Test
    public void testTemplateEscapesTheUserName() throws Exception {
        assertFailsWith(newSimpleBindStrategy(factory, "uid=%s,dc=example,dc=com", Schema.getDefaultSchema())
                .authenticate("bjensen,ou=People", "secret", new RootContext()), ResultCode.INVALID_CREDENTIALS);
    }

    /** A user name which is not a DN fails the returned promise instead of being thrown by authenticate(). */
    @Test
    public void testUserNameWhichIsNotADnFailsThePromise() throws Exception {
        assertFailsWith(newSimpleBindStrategy(factory, "%s", Schema.getDefaultSchema())
                .authenticate("bjensen", "secret", new RootContext()), ResultCode.INVALID_CREDENTIALS);
    }

    @Test
    public void testWrongPasswordFails() throws Exception {
        assertFailsWith(newSimpleBindStrategy(factory, "%s", Schema.getDefaultSchema())
                .authenticate(USER_DN, "wrong", new RootContext()), ResultCode.INVALID_CREDENTIALS);
    }

    private static void assertFailsWith(final Promise<SecurityContext, LdapException> promise,
            final ResultCode expected) throws Exception {
        try {
            promise.getOrThrow();
            fail("The authentication should have failed");
        } catch (final LdapException e) {
            assertThat(e.getResult().getResultCode()).isEqualTo(expected);
        }
    }
}
