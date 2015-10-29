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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2014-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.testng.Assert.*;

import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Syntax tests. */
@SuppressWarnings("javadoc")
public abstract class AbstractSyntaxTestCase extends AbstractSchemaTestCase {
    /**
     * Create data for the testAcceptableValues test. This should be a table of
     * tables with 2 elements. The first one should be the value to test, the
     * second the expected result of the test.
     *
     * @return a table containing data for the testAcceptableValues Test.
     */
    @DataProvider(name = "acceptableValues")
    public abstract Object[][] createAcceptableValues();

    /** Test the normalization and the approximate comparison. */
    @Test(dataProvider = "acceptableValues")
    public void testAcceptableValues(final String value, final Boolean result) throws Exception {
        // Make sure that the specified class can be instantiated as a task.
        final Syntax syntax = getRule();

        final LocalizableMessageBuilder reason = new LocalizableMessageBuilder();
        final Boolean liveResult = syntax.valueIsAcceptable(ByteString.valueOfUtf8(value), reason);
        assertEquals(liveResult, result,
            syntax + ".valueIsAcceptable gave bad result for " + value + "reason : " + reason);
    }

    /**
     * Get an instance of the attribute syntax that must be tested.
     *
     * @return An instance of the attribute syntax that must be tested.
     */
    protected abstract Syntax getRule() throws SchemaException, DecodeException;
}
