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
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2026 3A Systems, LLC.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_NAME_AND_OPTIONAL_UID_OID;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Name and optional UID syntax tests. */
@Test
public class NameAndOptionalUIDSyntaxTest extends AbstractSyntaxTestCase {
    @Override
    @DataProvider(name = "acceptableValues")
    public Object[][] createAcceptableValues() {
        return new Object[][] {
            { "dc=example,dc=com", true },
            { "dc=example,dc=com#'0101'B", true },
            { "dc=example,dc=com#'0102'B", false },
            // The escaped '#' is part of the last value "a#'01'B", so there is no uid to check
            { "dc=x,o=a\\#'01'B", true },
            { "dc=x,o=a\\#'02'B", true },
            // An escaped backslash does not escape the '#' that follows it
            { "dc=x,o=a\\\\#'01'B", true },
            { "dc=x,o=a\\\\#'02'B", false },
            // Not a DN
            { "dc=x,o=\"a\"b#'01'B", false },
        };
    }

    @Override
    protected Syntax getRule() {
        return Schema.getCoreSchema().getSyntax(SYNTAX_NAME_AND_OPTIONAL_UID_OID);
    }
}
