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

import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_LDAP_SYNTAX_OID;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * LDAP syntax tests.
 */
@Test
public class LDAPSyntaxTest extends AbstractSyntaxTestCase {
    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "acceptableValues")
    public Object[][] createAcceptableValues() {
        return new Object[][] {
            { "( 2.5.4.3 DESC 'full syntax description' " + "X-9EN ('this' 'is' 'a' 'test'))", false },
            { "( 2.5.4.3 DESC 'full syntax description' " + "(X-name 'this", false },
            { "( 2.5.4.3 DESC 'full syntax description' " + "(X-name 'this'", false },
            { "( 2.5.4.3 DESC 'full syntax description' " + "Y-name 'this')", false },
            { "( 2.5.4.3 DESC 'full syntax description' " + "X-name 'this' 'is')", false },
            { "( 2.5.4.3 DESC 'full syntax description' " + "X-name )", false },
            { "( 2.5.4.3 DESC 'full syntax description' " + "X- ('this' 'is' 'a' 'test'))", false },
            {
                "( 2.5.4.3 DESC 'full syntax description' "
                        + "X-name ('this' 'is' 'a' 'test') X-name-a 'this' X-name-b ('this')",
                false },
            {
                "( 2.5.4.3 DESC 'full syntax description' "
                        + "X-name ('this' 'is' 'a' 'test') X-name-a 'this' X-name-b ('this'", false },
            {
                "( 2.5.4.3 DESC 'full syntax description' "
                        + "X-name ('this' 'is' 'a' 'test') X-name-a 'this' X-name-b ('this'))))",
                false },
            {
                "( 2.5.4.3 DESC 'full syntax description' "
                        + "X-name ('this' 'is' 'a' 'test') X-name-a  X-name-b ('this'))))", false },
            {
                "( 2.5.4.3 DESC 'full syntax description' "
                        + "X-name ('this' 'is' 'a' 'test') X-name-a  'X-name-b' ('this'))))", false },
            {
                "( 2.5.4.3 DESC 'full syntax description' "
                        + "X-name ('this' 'is' 'a' 'test') X-name-a 'this' X-name-b ('this'))", true },
            { "( 2.5.4.3 DESC 'full syntax description' " + "X-a-_eN_- ('this' 'is' 'a' 'test'))",  true },
            { "( 2.5.4.3 DESC 'full syntax description' " + "X-name ('this'))", true },
            { "( 2.5.4.3 DESC 'full syntax description' " + "X-name 'this')", true },
            { "( 2.5.4.3 DESC 'full syntax description' " + "X-name 'this' X-name-a 'test')", true },
            { "( 2.5.4.3 DESC 'full syntax description' )", true },
            { "   (    2.5.4.3    DESC  ' syntax description'    )", true },
            { "( 2.5.4.3 DESC syntax description )", false },
            { "($%^*&!@ DESC 'syntax description' )", false },
            { "(temp-oid DESC 'syntax description' )", true },
            { "2.5.4.3 DESC 'syntax description' )", false },
            { "(2.5.4.3 DESC 'syntax description' ", false },
            { "( 1.1.1 DESC 'Host and Port in the format of HOST:PORT' X-PATTERN '^[a-z-A-Z]+:[0-9.]+\\d$' )", true },
        };
    }

    /** {@inheritDoc} */
    @Override
    protected Syntax getRule() {
        return Schema.getCoreSchema().getSyntax(SYNTAX_LDAP_SYNTAX_OID);
    }
}
