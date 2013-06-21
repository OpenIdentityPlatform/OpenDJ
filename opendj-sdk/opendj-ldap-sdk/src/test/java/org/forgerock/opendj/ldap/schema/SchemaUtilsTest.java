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
 *      Portions copyright 2011 ForgeRock AS
 */
package org.forgerock.opendj.ldap.schema;

import org.forgerock.opendj.ldap.DecodeException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.forgerock.opendj.util.SubstringReader;

/**
 * Test schema utilities.
 */
@SuppressWarnings("javadoc")
public class SchemaUtilsTest extends SchemaTestCase {

    @DataProvider(name = "invalidOIDs")
    public Object[][] createInvalidOIDs() {
        return new Object[][] { { "" }, { ".0" }, { "0." }, { "100." }, { ".999" }, { "1one" },
            { "one+two+three" }, { "one.two.three" },
            // AD puts quotes around OIDs - test mismatched quotes.
            { "'0" }, { "'10" }, { "999'" }, { "0.0'" }, };
    }

    @DataProvider(name = "validOIDs")
    public Object[][] createValidOIDs() {
        return new Object[][] {
            // Compliant NOIDs
            { "0.0" }, { "1.0" }, { "2.0" }, { "3.0" }, { "4.0" }, { "5.0" }, { "6.0" }, { "7.0" },
            { "8.0" }, { "9.0" }, { "0.1" }, { "0.2" }, { "0.3" }, { "0.4" }, { "0.5" }, { "0.6" },
            { "0.7" }, { "0.8" }, { "0.9" }, { "10.0" }, { "100.0" }, { "999.0" }, { "0.100" },
            { "0.999" }, { "100.100" }, { "999.999" }, { "111.22.333.44.55555.66.777.88.999" },
            { "a" },
            { "a2" },
            { "a-" },
            { "one" },
            { "one1" },
            { "one-two" },
            { "one1-two2-three3" },
            // AD puts quotes around OIDs - not compliant but we need to
            // handle them.
            { "'0.0'" }, { "'10.0'" }, { "'999.0'" }, { "'111.22.333.44.55555.66.777.88.999'" },
            { "'a'" }, { "'a2'" }, { "'a-'" }, { "'one'" }, { "'one1'" }, { "'one-two'" },
            { "'one1-two2-three3'" },
            // Not strictly legal, but we'll be lenient with what we accept.
            { "0" }, { "1" }, { "2" }, { "3" }, { "4" }, { "5" }, { "6" }, { "7" }, { "8" },
            { "9" }, { "00" }, { "01" }, { "01.0" }, { "0.01" }, };
    }

    @Test(dataProvider = "invalidOIDs", expectedExceptions = DecodeException.class)
    public void testReadOIDInvalid(final String oid) throws DecodeException {
        final SubstringReader reader = new SubstringReader(oid);
        SchemaUtils.readOID(reader, false);
    }

    @Test(dataProvider = "validOIDs")
    public void testReadOIDValid(final String oid) throws DecodeException {
        String expected = oid;
        if (oid.startsWith("'")) {
            expected = oid.substring(1, oid.length() - 1);
        }

        final SubstringReader reader = new SubstringReader(oid);
        Assert.assertEquals(SchemaUtils.readOID(reader, false), expected);
    }
}
