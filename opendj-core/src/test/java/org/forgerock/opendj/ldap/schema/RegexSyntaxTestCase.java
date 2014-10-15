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
 *      Portions copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import java.util.regex.Pattern;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Regex syntax tests.
 */
@SuppressWarnings("javadoc")
public class RegexSyntaxTestCase extends AbstractSyntaxTestCase {
    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "acceptableValues")
    public Object[][] createAcceptableValues() {
        return new Object[][] { { "invalid regex", false }, { "host:0.0.0", true }, };
    }

    @Test
    public void testDecode() {
        // This should fail due to invalid pattern.
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.addSyntax("( 1.1.1 DESC 'Host and Port in the format of HOST:PORT' "
                + " X-PATTERN '^[a-z-A-Z]+:[0-9.]+\\d$' )", true);
        builder.toSchema();
    }

    @Test
    public void testInvalidPattern() {
        // This should fail due to invalid pattern.
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.addSyntax("( 1.1.1 DESC 'Host and Port in the format of HOST:PORT' "
                + " X-PATTERN '^[a-z-A-Z+:[0-@.]+\\d$' )", true);
        Assert.assertFalse(builder.toSchema().getWarnings().isEmpty());
    }

    /** {@inheritDoc} */
    @Override
    protected Syntax getRule() {
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.addPatternSyntax("1.1.1", "Host and Port in the format of HOST:PORT", Pattern
                .compile("^[a-z-A-Z]+:[0-9.]+\\d$"), false);
        return builder.toSchema().getSyntax("1.1.1");
    }

}
