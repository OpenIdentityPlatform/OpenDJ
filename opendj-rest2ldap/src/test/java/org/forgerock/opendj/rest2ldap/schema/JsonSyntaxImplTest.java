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
package org.forgerock.opendj.rest2ldap.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.opendj.ldap.schema.Schema.getDefaultSchema;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.VALIDATION_POLICY;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.ValidationPolicy.LENIENT;
import static org.forgerock.opendj.rest2ldap.schema.JsonSchema.addJsonSyntaxesAndMatchingRulesToSchema;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.Syntax;
import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@Test
public class JsonSyntaxImplTest extends ForgeRockTestCase {
    private final Syntax syntax = addJsonSyntaxesAndMatchingRulesToSchema(new SchemaBuilder(getDefaultSchema()))
            .setOption(VALIDATION_POLICY, LENIENT)
            .toSchema()
            .getSyntax(JsonSchema.SYNTAX_JSON_OID);

    @Test
    public void testGetName() throws Exception {
        assertThat(syntax.getName()).isEqualTo(JsonSchema.SYNTAX_JSON_DESCRIPTION);
    }

    @Test
    public void testGetApproximateMatchingRule() throws Exception {
        assertThat(syntax.getApproximateMatchingRule()).isNull();
    }

    @Test
    public void testGetEqualityMatchingRule() throws Exception {
        assertThat(syntax.getEqualityMatchingRule().getOID()).isEqualTo(JsonSchema.EMR_CASE_IGNORE_JSON_QUERY_OID);
    }

    @Test
    public void testGetOrderingMatchingRule() throws Exception {
        assertThat(syntax.getOrderingMatchingRule()).isNull();
    }

    @Test
    public void testGetSubstringMatchingRule() throws Exception {
        assertThat(syntax.getSubstringMatchingRule()).isNull();
    }

    @Test
    public void testIsBEREncodingRequired() throws Exception {
        assertThat(syntax.isBEREncodingRequired()).isFalse();
    }

    @Test
    public void testIsHumanReadable() throws Exception {
        assertThat(syntax.isHumanReadable()).isTrue();
    }

    @DataProvider
    public static Object[][] validJson() {
        return JsonQueryEqualityMatchingRuleImplTest.validJson();
    }

    @DataProvider
    public static Object[][] invalidJson() {
        return JsonQueryEqualityMatchingRuleImplTest.invalidJson();
    }

    @Test(dataProvider = "validJson")
    public void testValueIsAcceptableWithValidJson(String value, String normalizedValue) throws Exception {
        final LocalizableMessageBuilder localizableMessageBuilder = new LocalizableMessageBuilder();
        final boolean valueIsAcceptable =
                syntax.valueIsAcceptable(ByteString.valueOfUtf8(value), localizableMessageBuilder);
        assertThat(valueIsAcceptable).isTrue();
        assertThat(localizableMessageBuilder).isEmpty();
    }

    @Test(dataProvider = "invalidJson")
    public void testValueIsAcceptableWithInvalidJson(String value) throws Exception {
        final LocalizableMessageBuilder localizableMessageBuilder = new LocalizableMessageBuilder();
        final boolean valueIsAcceptable =
                syntax.valueIsAcceptable(ByteString.valueOfUtf8(value), localizableMessageBuilder);
        assertThat(valueIsAcceptable).isFalse();
        assertThat(localizableMessageBuilder).isNotEmpty();
    }
}
