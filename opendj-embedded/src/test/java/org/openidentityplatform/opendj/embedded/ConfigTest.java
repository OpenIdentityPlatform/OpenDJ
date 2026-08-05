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
 * Copyright 2026 3A Systems LLC.
 */

package org.openidentityplatform.opendj.embedded;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests how the numeric configuration properties are read: they are parsed while the instance
 * is being created, so a value that cannot be parsed has to name the property that carried it
 * instead of surfacing as a bare {@link NumberFormatException} from a constructor.
 */
public class ConfigTest {

    private static final String PREFIX = Config.class.getPackage().getName();

    private final List<String> propertiesSet = new ArrayList<>();

    @AfterMethod
    public void clearProperties() {
        for (String name : propertiesSet) {
            System.clearProperty(name);
        }
        propertiesSet.clear();
    }

    @DataProvider
    public Object[][] numericProperties() {
        return new Object[][] { { ".port" }, { ".admin_port" }, { ".jmx_port" }, { ".delete_timeout" } };
    }

    @Test
    public void readsTheDefaultsWhenNoPropertyIsSet() {
        final Config config = new Config();

        assertEquals(config.getPort(), 1389);
        assertEquals(config.getAdminPort(), 4444);
        assertEquals(config.getJmxPort(), 1689);
        assertEquals(config.getDeleteTimeout(), 10_000L);
    }

    @Test
    public void readsTheNumbersFromTheSystemProperties() {
        setProperty(".port", "11389");
        setProperty(".admin_port", "14444");
        setProperty(".jmx_port", "11689");
        setProperty(".delete_timeout", "0");

        final Config config = new Config();

        assertEquals(config.getPort(), 11389);
        assertEquals(config.getAdminPort(), 14444);
        assertEquals(config.getJmxPort(), 11689);
        assertEquals(config.getDeleteTimeout(), 0L);
    }

    @Test(dataProvider = "numericProperties")
    public void namesThePropertyWhoseValueIsNotANumber(String property) {
        setProperty(property, "not a number");

        try {
            new Config();
            fail("a value that is not a number should have been rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains(PREFIX + property), e.getMessage());
            assertTrue(e.getMessage().contains("not a number"), e.getMessage());
            assertTrue(e.getCause() instanceof NumberFormatException, "unexpected cause: " + e.getCause());
        }
    }

    @Test
    public void rejectsANegativeDeleteTimeoutProperty() {
        setProperty(".delete_timeout", "-1");

        try {
            new Config();
            fail("a negative timeout should have been rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains(PREFIX + ".delete_timeout"), e.getMessage());
            assertTrue(e.getMessage().contains("negative"), e.getMessage());
        }
    }

    @Test
    public void rejectsANegativeDeleteTimeout() {
        final Config config = new Config();

        try {
            config.setDeleteTimeout(-1);
            fail("a negative timeout should have been rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("-1"), e.getMessage());
        }
        assertEquals(config.getDeleteTimeout(), 10_000L, "the timeout has been changed");
    }

    private void setProperty(String property, String value) {
        final String name = PREFIX + property;
        System.setProperty(name, value);
        propertiesSet.add(name);
    }
}
