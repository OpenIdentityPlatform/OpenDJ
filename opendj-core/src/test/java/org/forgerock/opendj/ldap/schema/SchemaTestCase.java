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
 *      Copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.fest.assertions.Assertions.assertThat;

import org.testng.annotations.Test;

/**
 * Tests the Schema class.
 */
@SuppressWarnings("javadoc")
public class SchemaTestCase extends AbstractSchemaTestCase {
    @Test(description = "Unit test for OPENDJ-1477")
    public void asNonStrictSchemaAlwaysReturnsSameInstance() {
        final Schema schema = Schema.getCoreSchema();
        final Schema nonStrictSchema1 = schema.asNonStrictSchema();
        final Schema nonStrictSchema2 =
                schema.asNonStrictSchema().asStrictSchema().asNonStrictSchema();
        assertThat(nonStrictSchema1).isSameAs(nonStrictSchema2);
    }

    @Test(description = "Unit test for OPENDJ-1477")
    public void asStrictSchemaAlwaysReturnsSameInstance() {
        final Schema schema = Schema.getCoreSchema();
        final Schema strictSchema1 = schema.asStrictSchema();
        final Schema strictSchema2 = schema.asStrictSchema().asNonStrictSchema().asStrictSchema();
        assertThat(strictSchema1).isSameAs(strictSchema2);
    }
}
