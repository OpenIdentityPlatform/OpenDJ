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
 * Portions copyright 2016 ForgeRock AS.
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
