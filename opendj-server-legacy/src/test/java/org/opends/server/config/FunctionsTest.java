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
 * Copyright 2016 ForgeRock AS.
 */
package org.opends.server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.opends.server.TestCaseUtils.getTestResource;

import java.io.File;

import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
@Test(groups = { "precommit" }, singleThreaded = true)
public class FunctionsTest extends DirectoryServerTestCase {
    private static final File CONFIG_PROPERTIES = getTestResource("el-config.properties");
    private static final File PASSWORD_PIN = getTestResource("el-password.pin");

    @DataProvider
    public static Object[][] expressions() {
        return new Object[][] {
            { "${trim('  text   ')}", String.class, "text" },
            { "${read('" + PASSWORD_PIN + "')}", String.class, "changeit" },
            { "${read('file:" + PASSWORD_PIN + "')}", String.class, "changeit" },
            { "${readProperties('" + CONFIG_PROPERTIES + "').hostName}", String.class, "myhost" },
            { "${readProperties('" + CONFIG_PROPERTIES + "').port}", Integer.class, 1389 },
            { "${readProperties('file:" + CONFIG_PROPERTIES + "').port}", Integer.class, 1389 },
            { "${readProperties('" + CONFIG_PROPERTIES.toURI() + "').port}", Integer.class, 1389 },
        };
    }

    @Test(dataProvider = "expressions")
    public void functionsShouldReturnExpectedValues(final String expression, final Class<?> type,
                                                    final Object expected) {
        assertThat(Expression.eval(expression, type)).isEqualTo(expected);
    }
}
