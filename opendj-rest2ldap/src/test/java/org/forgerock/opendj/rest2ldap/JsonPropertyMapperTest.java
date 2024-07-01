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
 * Copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.rest2ldap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.opendj.rest2ldap.FilterType.EQUAL_TO;
import static org.forgerock.opendj.rest2ldap.FilterType.GREATER_THAN;

import org.forgerock.json.JsonPointer;
import org.forgerock.opendj.ldap.Filter;
import org.forgerock.testng.ForgeRockTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@Test
public class JsonPropertyMapperTest extends ForgeRockTestCase {
    private final JsonPropertyMapper mapper = Rest2Ldap.json("json");

    @DataProvider
    static Object[][] ldapFilters() {
        // @formatter:off
        return new Object[][] {
            { "x/y", "z", EQUAL_TO, "string", "(json=/z eq \"string\")" },
            { "x/y", "", GREATER_THAN, 123, "(json=/ gt 123)" },
        };
        // @formatter:on
    }

    @Test(dataProvider = "ldapFilters")
    public void testGetLdapFilter(final String path, final String subPath, final FilterType filterType,
                                  final Object assertion, final String expected) throws Exception {
        final Filter filter = mapper.getLdapFilter(null,
                                                   null,
                                                   new JsonPointer(path),
                                                   new JsonPointer(subPath),
                                                   filterType,
                                                   null,
                                                   assertion).getOrThrowUninterruptibly();
        assertThat(filter.toString()).isEqualTo(expected);
    }

    @Test
    public void testToJsonSchema() throws Exception {
        assertThat(mapper.toJsonSchema()).isNotNull();
        assertThat(mapper.toJsonSchema().isMap()).isTrue();
    }
}
