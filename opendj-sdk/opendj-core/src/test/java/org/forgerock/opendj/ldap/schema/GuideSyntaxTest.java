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
