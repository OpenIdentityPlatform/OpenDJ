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
 * Portions copyright 2014-2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.forgerock.opendj.ldap.schema.SchemaConstants.SYNTAX_GENERALIZED_TIME_OID;

import org.testng.annotations.DataProvider;

/**
 * Generalized time syntax tests.
 */
public class GeneralizedTimeSyntaxTest extends AbstractSyntaxTestCase {
    @Override
    @DataProvider(name = "acceptableValues")
    public Object[][] createAcceptableValues() {
        return new Object[][] { { "2006090613Z", true }, { "20060906135030+01", true }, { "200609061350Z", true },
            { "20060906135030Z", true }, { "20061116135030Z", true }, { "20061126135030Z", true },
            { "20061231235959Z", true }, { "20060906135030+0101", true }, { "20060906135030+2359", true },
            { "20060906135030+3359", false }, { "20060906135030+2389", false }, { "20060906135030+2361", false },
            { "20060906135030+", false }, { "20060906135030+0", false }, { "20060906135030+010", false },
            { "20061200235959Z", false }, { "2006121a235959Z", false }, { "2006122a235959Z", false },
            { "20060031235959Z", false }, { "20061331235959Z", false }, { "20062231235959Z", false },
            { "20061232235959Z", false }, { "2006123123595aZ", false }, { "200a1231235959Z", false },
            { "2006j231235959Z", false }, { "200612-1235959Z", false }, { "20061231#35959Z", false },
            { "2006", false }, };
    }

    /** {@inheritDoc} */
    @Override
    protected Syntax getRule() {
        return Schema.getCoreSchema().getSyntax(SYNTAX_GENERALIZED_TIME_OID);
    }

}
