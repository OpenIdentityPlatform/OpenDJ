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
 * Portions copyright 2014-2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import java.util.regex.Pattern;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Regex syntax tests. */
@SuppressWarnings("javadoc")
public class RegexSyntaxTestCase extends AbstractSyntaxTestCase {
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

    @Override
    protected Syntax getRule() {
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.addPatternSyntax("1.1.1", "Host and Port in the format of HOST:PORT", Pattern
                .compile("^[a-z-A-Z]+:[0-9.]+\\d$"), false);
        return builder.toSchema().getSyntax("1.1.1");
    }
}
