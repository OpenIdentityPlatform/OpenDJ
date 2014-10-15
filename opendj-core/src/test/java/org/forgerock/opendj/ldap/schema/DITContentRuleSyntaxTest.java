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

import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_DIT_CONTENT_RULE_OID;

import org.testng.annotations.DataProvider;

/**
 * DIT content rule syntax tests.
 */
public class DITContentRuleSyntaxTest extends AbstractSyntaxTestCase {
    @Override
    @DataProvider(name = "acceptableValues")
    public Object[][] createAcceptableValues() {
        return new Object[][] {
            {
                "( 2.5.6.4 DESC 'content rule for organization' NOT "
                        + "( x121Address $ telexNumber ) )", true },
            {
                "( 2.5.6.4 NAME 'fullRule' DESC 'rule with all possible fields' " + " OBSOLETE"
                        + " AUX ( posixAccount )" + " MUST ( cn $ sn )" + " MAY ( dc )"
                        + " NOT ( x121Address $ telexNumber ) )", true },
            {
                "( 2.5.6.4 NAME 'fullRule' DESC 'ommit parenthesis' " + " OBSOLETE"
                        + " AUX posixAccount " + " MUST cn " + " MAY dc " + " NOT x121Address )",
                true },
            {
                "( 2.5.6.4 NAME 'fullRule' DESC 'use numeric OIDs' " + " OBSOLETE"
                        + " AUX 1.3.6.1.1.1.2.0" + " MUST cn " + " MAY dc " + " NOT x121Address )",
                true },
            {
                "( 2.5.6.4 NAME 'fullRule' DESC 'illegal OIDs' " + " OBSOLETE" + " AUX 2.5.6.."
                        + " MUST cn " + " MAY dc " + " NOT x121Address )", false },
            {
                "( 2.5.6.4 NAME 'fullRule' DESC 'illegal OIDs' " + " OBSOLETE" + " AUX 2.5.6.x"
                        + " MUST cn " + " MAY dc " + " NOT x121Address )", false },
            {
                "( 2.5.6.4 NAME 'fullRule' DESC 'missing closing parenthesis' " + " OBSOLETE"
                        + " AUX posixAccount" + " MUST cn " + " MAY dc " + " NOT x121Address",
                false },
            {
                "( 2.5.6.4 NAME 'fullRule' DESC 'extra parameterss' " + " MUST cn "
                        + " X-name ( 'this is an extra parameter' ) )", true },

        };
    }

    /** {@inheritDoc} */
    @Override
    protected Syntax getRule() {
        return Schema.getCoreSchema().getSyntax(SYNTAX_DIT_CONTENT_RULE_OID);
    }
}
