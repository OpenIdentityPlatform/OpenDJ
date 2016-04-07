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

import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_DIT_CONTENT_RULE_OID;

import org.testng.annotations.DataProvider;

/** DIT content rule syntax tests. */
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

    @Override
    protected Syntax getRule() {
        return Schema.getCoreSchema().getSyntax(SYNTAX_DIT_CONTENT_RULE_OID);
    }
}
