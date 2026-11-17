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
package org.forgerock.opendj.config;

import static org.testng.Assert.assertEquals;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.forgerock.opendj.server.config.meta.LDAPPassThroughAuthenticationPolicyCfgDefn;
import org.forgerock.opendj.server.config.meta.ReplicationServiceDiscoveryMechanismCfgDefn;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class PasswordPropertyDefinitionTest extends ConfigTestCase {
    @Test
    public void passwordMetadataRetainsStringAccessorDescriptor() throws Throwable {
        LDAPPassThroughAuthenticationPolicyCfgDefn definition =
                LDAPPassThroughAuthenticationPolicyCfgDefn.getInstance();
        StringPropertyDefinition property = (StringPropertyDefinition) MethodHandles.publicLookup().findVirtual(
                LDAPPassThroughAuthenticationPolicyCfgDefn.class, "getMappedSearchBindPasswordPropertyDefinition",
                MethodType.methodType(StringPropertyDefinition.class)).invokeExact(definition);
        assertEquals(property.getClass().getSimpleName(), "PasswordPropertyDefinition");
    }

    @Test
    public void passwordSyntaxIsDisplayedAsString() {
        PropertyDefinition<?> definition = LDAPPassThroughAuthenticationPolicyCfgDefn.getInstance()
                .getMappedSearchBindPasswordPropertyDefinition();
        assertEquals(new PropertyDefinitionUsageBuilder(false).getUsage(definition).toString(), "STRING");
        assertEquals(new PropertyDefinitionUsageBuilder(true).getUsage(definition).toString(), "STRING");
    }

    @Test
    public void passwordDeclarationsHavePasswordDefinitions() {
        assertEquals(LDAPPassThroughAuthenticationPolicyCfgDefn.getInstance()
                .getMappedSearchBindPasswordPropertyDefinition().getClass().getSimpleName(),
                "PasswordPropertyDefinition");
        assertEquals(ReplicationServiceDiscoveryMechanismCfgDefn.getInstance()
                .getBindPasswordPropertyDefinition().getClass().getSimpleName(),
                "PasswordPropertyDefinition");
    }

    @Test
    public void unclosedBraceIsAnUnprefixedPassword() throws Exception {
        PasswordPropertyDefinition definition = PasswordPropertyDefinition.createPasswordBuilder(
                LDAPPassThroughAuthenticationPolicyCfgDefn.getInstance(), "test-unprefixed-password").getInstance();
        PasswordPropertyDefinition.Codec codec = new PasswordPropertyDefinition.Codec() {
            @Override
            public void requireScheme(String scheme) { }

            @Override
            public String encrypt(String scheme, String plaintext) {
                return "{" + scheme + "}" + plaintext;
            }

            @Override
            public String decrypt(String scheme, String encoded) {
                return encoded;
            }
        };
        assertEquals(definition.encodeForStorage("{unclosed", codec), "{AES}{unclosed");
        assertEquals(definition.decodeFromStorage("{unclosed", codec), "{unclosed");
    }
}
