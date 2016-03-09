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
 * Copyright 2015-2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.Schema.*;
import static org.forgerock.opendj.ldap.schema.SchemaConstants.*;
import static org.forgerock.opendj.ldap.schema.SchemaOptions.*;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Telephone number syntax tests. */
@Test
public class TelephoneNumberSyntaxTest extends AbstractSyntaxTestCase {
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

    @Override
    protected Syntax getRule() {
        SchemaBuilder builder = new SchemaBuilder(getCoreSchema()).setOption(ALLOW_NON_STANDARD_TELEPHONE_NUMBERS,
                false);
        return builder.toSchema().getSyntax(SYNTAX_TELEPHONE_OID);
    }
}
