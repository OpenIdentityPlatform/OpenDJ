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
 * Portions copyright 2014 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_GUIDE_OID;

import org.testng.annotations.DataProvider;

/**
 * Guide syntax tests.
 */
public class GuideSyntaxTest extends AbstractSyntaxTestCase {
    /** {@inheritDoc} */
    @Override
    @DataProvider(name = "acceptableValues")
    public Object[][] createAcceptableValues() {
        return new Object[][] { { "sn$EQ|!(sn$EQ)", true }, { "!(sn$EQ)", true },
            { "person#sn$EQ", true }, { "(sn$EQ)", true }, { "sn$EQ", true },
            { "sn$SUBSTR", true }, { "sn$GE", true }, { "sn$LE", true }, { "sn$ME", false },
            { "?true", true }, { "?false", true }, { "true|sn$GE", false }, { "sn$APPROX", true },
            { "sn$EQ|(sn$EQ)", true }, { "sn$EQ|(sn$EQ", false }, { "sn$EQ|(sn$EQ)|sn$EQ", true },
            { "sn$EQ|(cn$APPROX&?false)", true }, { "sn$EQ|(cn$APPROX&|?false)", false }, };
    }

    /** {@inheritDoc} */
    @Override
    protected Syntax getRule() {
        return Schema.getCoreSchema().getSyntax(SYNTAX_GUIDE_OID);
    }
}
