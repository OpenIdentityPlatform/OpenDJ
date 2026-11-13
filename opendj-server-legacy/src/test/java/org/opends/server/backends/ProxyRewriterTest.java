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

import java.util.Arrays;
import java.util.Collections;

import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.opendj.ldap.LinkedHashMapEntry;
import org.forgerock.opendj.ldap.schema.Schema;
import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

@Test(sequential = true)
public class ProxyRewriterTest extends DirectoryServerTestCase {
    @Test
    public void mapsAttributesWithoutChangingDnWhenRemoteBaseIsAbsent() {
        final ProxyRewriter mapping = new ProxyRewriter(null, null,
                Collections.singletonList("description=displayName"), Schema.getDefaultSchema());
        final DN original = DN.valueOf("uid=user,ou=people,o=test");
        assertSame(mapping.dn(original, true), original);
        assertEquals(mapping.attribute("DESCRIPTION;lang-en", true), "displayName;lang-en");
        assertEquals(mapping.attribute("displayName;lang-en", false), "description;lang-en");
        assertEquals(mapping.value("description", ByteString.valueOfUtf8("ou=proxy,o=test"), true),
                ByteString.valueOfUtf8("ou=proxy,o=test"));
        assertEquals(mapping.value("manager", ByteString.valueOfUtf8(original.toString()), true),
                ByteString.valueOfUtf8(original.toString()));
        assertEquals(mapping.filter(Filter.valueOf("(description=one)")).toString(), "(displayName=one)");
    }

    @Test
    public void rewritesEscapedMultiValuedRdnsAndDnAttributeValues() {
        final DN proxyBase = DN.valueOf("ou=proxy,o=test");
        final DN remoteBase = DN.valueOf("ou=remote,o=test");
        final ProxyRewriter mapping = new ProxyRewriter(proxyBase, remoteBase,
                Collections.singletonList("cn=displayName"), Schema.getDefaultSchema());
        final DN proxyDn = DN.valueOf("uid=user+cn=Smith\\, Jane,ou=proxy,o=test");
        final DN remoteDn = DN.valueOf("uid=user+displayName=Smith\\, Jane,ou=remote,o=test");
        assertEquals(mapping.dn(proxyDn, true), remoteDn);
        assertEquals(mapping.dn(remoteDn, false), proxyDn);
        assertEquals(mapping.value("member", ByteString.valueOfUtf8(proxyDn.toString()), true),
                ByteString.valueOfUtf8(mapping.dn(proxyDn, true).toString()));
        assertEquals(mapping.filter(Filter.valueOf("(:distinguishedNameMatch:=uid=user,ou=proxy,o=test)")).toString(),
                "(:distinguishedNameMatch:=uid=user,ou=remote,o=test)");
        final DN outside = DN.valueOf("uid=external,o=elsewhere");
        assertSame(mapping.dn(outside, true), outside);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void rejectsAmbiguousAttributeMappings() {
        new ProxyRewriter(null, null, Arrays.asList("description=displayName", "cn=displayName"),
                Schema.getDefaultSchema());
    }

    @Test(expectedExceptions = ProxyRewriter.RewriteException.class)
    public void rejectsEntriesWhoseAttributesMapToTheSameName() {
        final ProxyRewriter mapping = new ProxyRewriter(null, null,
                Collections.singletonList("description=displayName"), Schema.getDefaultSchema());
        mapping.entry(new LinkedHashMapEntry("uid=user,o=test")
                .addAttribute("description", "one").addAttribute("displayName", "two"), true);
    }
}
