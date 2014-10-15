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

import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_IA5_STRING_OID;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Substitution syntax tests.
 */
@SuppressWarnings("javadoc")
public class SubstitutionSyntaxTestCase extends AbstractSyntaxTestCase {
    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "acceptableValues")
    public Object[][] createAcceptableValues() {
        return new Object[][] { { "12345678", true }, { "12345678\u2163", false }, };
    }

    @Test
    public void testSelfSubstitute1() {
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.addSyntax("( 1.3.6.1.4.1.1466.115.121.1.15 "
                + " DESC 'Replacing DirectorySyntax'  "
                + " X-SUBST '1.3.6.1.4.1.1466.115.121.1.15' )", true);
        Assert.assertFalse(builder.toSchema().getWarnings().isEmpty());
    }

    @Test
    public void testSelfSubstitute2() {
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.addSubstitutionSyntax("1.3.6.1.4.1.1466.115.121.1.15", "Replacing DirectorySyntax",
                "1.3.6.1.4.1.1466.115.121.1.15", true);
        Assert.assertFalse(builder.toSchema().getWarnings().isEmpty());
    }

    @Test(expectedExceptions = ConflictingSchemaElementException.class)
    public void testSubstituteCore1() throws ConflictingSchemaElementException {
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.addSyntax("( 1.3.6.1.4.1.1466.115.121.1.26 "
                + " DESC 'Replacing DirectorySyntax'  " + " X-SUBST '9.9.9' )", false);
    }

    @Test
    public void testSubstituteCore1Override() {
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.addSyntax("( 1.3.6.1.4.1.1466.115.121.1.26 "
                + " DESC 'Replacing DirectorySyntax'  " + " X-SUBST '9.9.9' )", true);
    }

    @Test(expectedExceptions = ConflictingSchemaElementException.class)
    public void testSubstituteCore2() throws ConflictingSchemaElementException {
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.addSubstitutionSyntax("1.3.6.1.4.1.1466.115.121.1.26", "Replacing DirectorySyntax",
                "9.9.9", false);
    }

    @Test
    public void testSubstituteCore2Override() {
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.addSubstitutionSyntax("1.3.6.1.4.1.1466.115.121.1.26", "Replacing DirectorySyntax",
                "9.9.9", true);
    }

    @Test
    public void testUndefinedSubstitute1() {
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.addSyntax("( 1.3.6.1.4.1.1466.115.121.1.15 "
                + " DESC 'Replacing DirectorySyntax'  " + " X-SUBST '1.1.1' )", true);
        Assert.assertFalse(builder.toSchema().getWarnings().isEmpty());
    }

    @Test
    public void testUndefinedSubstitute2() {
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.addSubstitutionSyntax("1.3.6.1.4.1.1466.115.121.1.15", "Replacing DirectorySyntax",
                "1.1.1", true);
        Assert.assertFalse(builder.toSchema().getWarnings().isEmpty());
    }

    /** {@inheritDoc} */
    @Override
    protected Syntax getRule() {
        // Use IA5String syntax as our substitute.
        final SchemaBuilder builder = new SchemaBuilder(Schema.getCoreSchema());
        builder.addSubstitutionSyntax("9.9.9", "Unimplemented Syntax", SYNTAX_IA5_STRING_OID, false);
        return builder.toSchema().getSyntax("9.9.9");
    }
}
