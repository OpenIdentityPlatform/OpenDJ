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
 *      Portions copyright 2016 ForgeRock AS.
 */
package org.forgerock.opendj.ldap;

import static org.assertj.core.api.Assertions.*;

import org.forgerock.opendj.ldap.schema.AttributeType;
import org.forgerock.opendj.ldap.schema.Schema;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** This class defines a set of tests for the {@link AVA} class. */
@SuppressWarnings("javadoc")
public class AVATestCase extends SdkTestCase {

    @DataProvider
    private Object[][] valueOfDataProvider() {
        AttributeType cnAttrType = Schema.getCoreSchema().getAttributeType("commonName");
        return new Object[][] {
            { "CN=value", cnAttrType, "CN", "value" },
            { "commonname=value", cnAttrType, "commonname", "value" },
            { "2.5.4.3=#76616C7565", cnAttrType, "2.5.4.3", "value" },
        };
    }

    @Test(dataProvider = "valueOfDataProvider")
    public void valueOf(String avaString, AttributeType expectedAttrType, String expectedAttrName,
            String expectedValue) throws Exception {
        AVA ava = AVA.valueOf(avaString);
        assertThat(ava.getAttributeType()).isEqualTo(expectedAttrType);
        assertThat(ava.getAttributeName()).isEqualTo(expectedAttrName);
        assertThat(ava.getAttributeValue()).isEqualTo(ByteString.valueOfUtf8(expectedValue));
        assertThat(ava.toString()).isEqualTo(avaString);
    }

    @Test
    public void hexEncodingDoesNotLoseInformation() throws Exception {
        final String avaString = "2.5.4.3=#76616C7565";
        final String roundtrippedValue = AVA.valueOf(avaString).toString();
        assertThat(AVA.valueOf(roundtrippedValue).toString()).isEqualTo(avaString);
    }
}
