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
 *      Copyright 2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.Schema.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;
import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Telephone number syntax tests.
 */
@Test
public class TelephoneNumberSyntaxTest extends AbstractSyntaxTestCase {

    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "acceptableValues")
    public Object[][] createAcceptableValues() {
        return new Object[][] {
            { "+61 3 9896 7830", true },
            { "+1 512 315 0280", true },
            { "+1-512-315-0280", true },
            { "3 9896 7830", false },
            { "+1+512 315 0280", false },
            { "+1x512x315x0280", false },
            { "   ", false },
            { "", false } };
    }

    /** {@inheritDoc} */
    @Override
    protected Syntax getRule() {
        SchemaBuilder builder = new SchemaBuilder(getCoreSchema()).setOption(ALLOW_NON_STANDARD_TELEPHONE_NUMBERS,
                false);
        return builder.toSchema().getSyntax(SYNTAX_TELEPHONE_OID);
    }

}
