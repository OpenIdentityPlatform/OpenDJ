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

import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_UUID_OID;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** UUID syntax tests. */
@Test
public class UUIDSyntaxTest extends AbstractSyntaxTestCase {
    @Override
    @DataProvider(name = "acceptableValues")
    public Object[][] createAcceptableValues() {
        return new Object[][] { { "12345678-9ABC-DEF0-1234-1234567890ab", true },
            { "12345678-9abc-def0-1234-1234567890ab", true },
            { "12345678-9abc-def0-1234-1234567890ab", true },
            { "12345678-9abc-def0-1234-1234567890ab", true },
            { "02345678-9abc-def0-1234-1234567890ab", true },
            { "12345678-9abc-def0-1234-1234567890ab", true },
            { "12345678-9abc-def0-1234-1234567890ab", true },
            { "02345678-9abc-def0-1234-1234567890ab", true },
            { "G2345678-9abc-def0-1234-1234567890ab", false },
            { "g2345678-9abc-def0-1234-1234567890ab", false },
            { "12345678/9abc/def0/1234/1234567890ab", false },
            { "12345678-9abc-def0-1234-1234567890a", false }, };
    }

    @Override
    protected Syntax getRule() {
        return Schema.getCoreSchema().getSyntax(SYNTAX_UUID_OID);
    }
}
