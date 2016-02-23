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
 * Portions Copyright 2015 ForgeRock AS.
 */
package org.forgerock.opendj.ldap.schema;

import static org.fest.assertions.Assertions.*;

import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.testng.ForgeRockTestCase;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Tests the AuthPasswordSyntaxImpl. */
@Test
@SuppressWarnings("javadoc")
public class AuthPasswordSyntaxImplTest extends ForgeRockTestCase {

    @DataProvider
    public Object[][] validEncodedPasswords() {
        return new Object[][] {
            { "0$0$0", "0", "0", "0" },
            { " 0$0$0", "0", "0", "0" },
            { "0 $0$0", "0", "0", "0" },
            { "0$ 0$0", "0", "0", "0" },
            { "0$0 $0", "0", "0", "0" },
            { "0$0$ 0", "0", "0", "0" },
            { "0$0$0 ", "0", "0", "0" },
        };
    }

    @Test(dataProvider = "validEncodedPasswords")
    public void decodeValidPassword(String encodedPassword, String expectedScheme, String expectedAuthInfo,
            String expectedAuthValue) throws Exception {
        assertThat(AuthPasswordSyntaxImpl.decodeAuthPassword(encodedPassword))
                .isEqualTo(new String[] {expectedScheme, expectedAuthInfo, expectedAuthValue});
    }

    @DataProvider
    public Object[][] invalidEncodedPasswords() {
        return new Object[][] {
            { "", "zero-length scheme element" },
            { "$", "zero-length scheme element" },
            { "0$$", "zero-length authInfo element" },
            { "0$0$", "zero-length authValue element" },
            { "a", "invalid scheme character" },
            { "0 #", "illegal character between the scheme and authInfo element" },
            { "0$0#", "invalid authInfo character" },
            { "0$0 #", "illegal character between the authInfo and authValue element" },
            { "0$0$\n", "invalid authValue character" },
            { "0$0$0$", "invalid trailing character" },
        };
    }

    @Test(dataProvider = "invalidEncodedPasswords")
    public void decodeInvalidPassword(String encodedPassword, String errorMsg) throws Exception {
        try {
            AuthPasswordSyntaxImpl.decodeAuthPassword(encodedPassword);
            Assert.fail("Expected DirectoryException");
        } catch (DecodeException e) {
            assertThat(e.getMessage()).contains(errorMsg);
        }
    }
}
