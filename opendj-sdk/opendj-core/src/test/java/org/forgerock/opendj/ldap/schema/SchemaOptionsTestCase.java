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
 *      Copyright 2013-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.fest.assertions.Assertions.*;
import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;

import org.forgerock.util.Option;
import org.forgerock.util.Options;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests the {@link SchemaOptions} class.
 */
public class SchemaOptionsTestCase extends AbstractSchemaTestCase {
    private static final int TEST_OPTION_DEFAULT_VALUE = 42;
    private static final Option<Integer> TEST_OPTION = Option.of(Integer.class, TEST_OPTION_DEFAULT_VALUE);

    @DataProvider
    private Object[][] defaultSchemaOptions() {
        return new Object[][] {
            { ALLOW_MALFORMED_CERTIFICATES },
            { ALLOW_MALFORMED_JPEG_PHOTOS },
            { ALLOW_MALFORMED_NAMES_AND_OPTIONS },
            { ALLOW_NON_STANDARD_TELEPHONE_NUMBERS },
            { ALLOW_ZERO_LENGTH_DIRECTORY_STRINGS },
            { STRICT_FORMAT_FOR_COUNTRY_STRINGS },
            { STRIP_UPPER_BOUND_FOR_ATTRIBUTE_TYPE }};
    }

    @Test(dataProvider = "defaultSchemaOptions")
    public void testDefaultSchemaOptions(Option<?> option) {
        Options defaultOptions = Options.defaultOptions();
        assertThat(new SchemaBuilder().getOptions().get(option)).isEqualTo(defaultOptions.get(option));
    }

    @Test
    public void testAddSchemaOption() {
        assertThat(newSchemaBuilder().getOptions().get(TEST_OPTION)).isEqualTo(TEST_OPTION_DEFAULT_VALUE);
    }

    @Test
    public void testSetSchemaOption() {
        assertThat(newSchemaBuilder().setOption(TEST_OPTION, 0).getOptions().get(TEST_OPTION)).isEqualTo(0);
    }

    @Test
    public void testSchemaOptionsCopy() {
        final Options copiedOptions = Options.copyOf(newSchemaBuilder().getOptions());
        assertThat(copiedOptions.get(TEST_OPTION)).isEqualTo(TEST_OPTION_DEFAULT_VALUE);
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testAsReadOnlyOptions() {
        Options.unmodifiableCopyOf(new SchemaBuilder().getOptions()).set(ALLOW_MALFORMED_CERTIFICATES, false);
    }

    private SchemaBuilder newSchemaBuilder() {
        return new SchemaBuilder().setOption(TEST_OPTION, TEST_OPTION_DEFAULT_VALUE);
    }
}
