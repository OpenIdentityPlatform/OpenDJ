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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.opendj.rest2ldap.Rest2Ldap.rest2Ldap;
import static org.forgerock.opendj.rest2ldap.RoutingContext.newRoutingContext;
import static org.forgerock.util.Options.defaultOptions;

import org.forgerock.http.routing.UriRouterContext;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.services.context.Context;
import org.forgerock.services.context.RootContext;
import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test
@SuppressWarnings("javadoc")
public final class DnTemplateTest extends ForgeRockTestCase {
    private final Context context;
    {
        Context ctx = new RootContext();
        ctx = new Rest2LdapContext(ctx, rest2Ldap(defaultOptions()));
        ctx = new UriRouterContext(ctx, "", "", singletonMap("subdomain", "www"));
        ctx = newRoutingContext(ctx, DN.valueOf("dc=example,dc=com"), null);
        ctx = new UriRouterContext(ctx, "", "", singletonMap("tenant", "acme"));
        context = ctx;
    }

    @DataProvider
    Object[][] templateData() {
        // @formatter:off
        return new Object[][] {
            { "dc=www", "dc=www", "dc=www,dc=example,dc=com" },
            { "..", "dc=com", "dc=com" },
            { "dc={subdomain}", "dc=www", "dc=www,dc=example,dc=com" },
            { "dc={subdomain},..", "dc=www,dc=com", "dc=www,dc=com" },
            { "dc={subdomain},dc={tenant},..", "dc=www,dc=acme,dc=com", "dc=www,dc=acme,dc=com" },
        };
        // @formatter:on
    }

    @SuppressWarnings("unused")
    @Test(dataProvider = "templateData")
    public void testCompile(String template, String expectedDn, String expectedRelativeDn) {
        DnTemplate dnTemplate = DnTemplate.compile(template);
        DN dn = dnTemplate.format(context);
        assertThat((Object) dn).isEqualTo(DN.valueOf(expectedDn));
    }

    @SuppressWarnings("unused")
    @Test(dataProvider = "templateData")
    public void testCompileRelative(String template, String expectedDn, String expectedRelativeDn) {
        DnTemplate dnTemplate = DnTemplate.compileRelative(template);
        DN dn = dnTemplate.format(context);
        assertThat((Object) dn).isEqualTo(DN.valueOf(expectedRelativeDn));
    }
}
