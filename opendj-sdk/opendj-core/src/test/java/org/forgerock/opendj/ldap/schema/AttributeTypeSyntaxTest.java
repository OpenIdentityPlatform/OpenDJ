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

import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_ATTRIBUTE_TYPE_OID;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Attribute type syntax tests.
 */
@Test
public class AttributeTypeSyntaxTest extends AbstractSyntaxTestCase {
    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "acceptableValues")
    public Object[][] createAcceptableValues() {
        return new Object[][] {
            {
                "(1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE SUP cn "
                        + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                        + " SUBSTR caseIgnoreSubstringsMatch"
                        + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                        + " USAGE userApplications )", true },
            {
                "(1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE "
                        + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                        + " SUBSTR caseIgnoreSubstringsMatch"
                        + " X-APPROX 'equalLengthApproximateMatch'"
                        + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                        + " COLLECTIVE USAGE userApplications )", true },
            {
                "(1.2.8.5 NAME 'testtype')", true },
            {
                "(1.2.8.5 NAME 'testtype' DESC 'full type')", true },
            {
                "(1.2.8.5 NAME 'testType' DESC 'full type' EQUALITY caseIgnoreMatch "
                        + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15)", true},
            {
                "(1.2.8.5 NAME 'testType' DESC 'full type' EQUALITY caseIgnoreMatch "
                        + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 X-ORIGIN 'test' )", true},
            {   "(1.2.8.5 NAME 'testType' DESC 'full type' EQUALITY caseIgnoreMatch "
                        + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 X-ORIGIN 'test' "
                        + " X-SCHEMA-FILE '33-test.ldif' )", true},
            {
                "(1.2.8.5 USAGE directoryOperation )", true },
            {
                "(1.2.8.5 USAGE directoryOperation)", true },
            {
                "(1.2.8.5 USAGE directoryOperation X-SCHEMA-FILE '99-test.ldif')", true },

            // Collective can inherit from non-collective
            {   "(1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE SUP cn "
                        + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                        + " SUBSTR caseIgnoreSubstringsMatch"
                        + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                        + " COLLECTIVE USAGE userApplications )", true},
            // Collective can be operational
            {   "(1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE "
                        + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                        + " SUBSTR caseIgnoreSubstringsMatch"
                        + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                        + " COLLECTIVE USAGE directoryOperation )", true},

            // X-NAME is invalid extension (no value)
            {   "(1.2.8.5 NAME 'testType' DESC 'full type' EQUALITY caseIgnoreMatch "
                        + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 X-ORIGIN 'test' "
                        + " X-SCHEMA-FILE '33-test.ldif' X-NAME )", false},

            {
                "(1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE SUP cn "
                        + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                        + " SUBSTR caseIgnoreSubstringsMatch"
                        + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                        + " COLLECTIVE USAGE badUsage )", false },
            {
                "(1.2.8.a.b NAME 'testtype' DESC 'full type' OBSOLETE "
                        + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                        + " SUBSTR caseIgnoreSubstringsMatch"
                        + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                        + " COLLECTIVE USAGE directoryOperation )", false },
            {
                "(1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE SUP cn "
                        + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                        + " SUBSTR caseIgnoreSubstringsMatch"
                        + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                        + " BADTOKEN USAGE directoryOperation )", false },

            // NO-USER-MODIFICATION can't have non-operational usage
            {
                "1.2.8.5 NAME 'testtype' DESC 'full type' OBSOLETE "
                        + " EQUALITY caseIgnoreMatch ORDERING caseIgnoreOrderingMatch"
                        + " SUBSTR caseIgnoreSubstringsMatch"
                        + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE"
                        + " NO-USER-MODIFICATION USAGE userApplications", false }, };
    }

    /** {@inheritDoc} */
    @Override
    protected Syntax getRule() {
        return Schema.getCoreSchema().getSyntax(SYNTAX_ATTRIBUTE_TYPE_OID);
    }

}
