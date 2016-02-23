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
 * Portions copyright 2014 ForgeRock AS.
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
