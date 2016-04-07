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

import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_BIT_STRING_OID;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Bit string syntax tests. */
@Test
public class BitStringSyntaxTest extends AbstractSyntaxTestCase {
    @Override
    @DataProvider(name = "acceptableValues")
    public Object[][] createAcceptableValues() {
        return new Object[][] {
            { "'0101'B", true },
            { "'1'B", true },
            { "'0'B", true },
            { "invalid", false },
            { "1", false },
            { "'010100000111111010101000'B", true },
        };
    }

    @Override
    protected Syntax getRule() {
        return Schema.getCoreSchema().getSyntax(SYNTAX_BIT_STRING_OID);
    }
}
