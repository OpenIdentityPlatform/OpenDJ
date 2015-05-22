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

import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_MATCHING_RULE_OID;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Matching rule syntax tests.
 */
@Test
public class MatchingRuleSyntaxTest extends AbstractSyntaxTestCase {
    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "acceptableValues")
    public Object[][] createAcceptableValues() {
        return new Object[][] {
            {
                "( 1.2.3.4 NAME 'fullMatchingRule' "
                        + " DESC 'description of matching rule' OBSOLETE "
                        + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.17 "
                        + " X-name ( 'this is an extension' ) )", true },
            {
                "( 1.2.3.4 NAME 'missingClosingParenthesis' "
                        + " DESC 'description of matching rule' "
                        + " SYNTAX 1.3.6.1.4.1.1466.115.121.1.17 "
                        + " X-name ( 'this is an extension' ) ", false }, };
    }

    /** {@inheritDoc} */
    @Override
    protected Syntax getRule() {
        return Schema.getCoreSchema().getSyntax(SYNTAX_MATCHING_RULE_OID);
    }

}
