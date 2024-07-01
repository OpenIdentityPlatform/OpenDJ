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
 * Copyright 2009 Sun Microsystems, Inc.
 * Portions copyright 2014-2015 ForgeRock AS.
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
