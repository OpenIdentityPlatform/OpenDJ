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
 * Copyright 2008 Sun Microsystems, Inc.
 */
package org.forgerock.opendj.config;

import static org.testng.Assert.assertEquals;

import org.forgerock.opendj.server.config.meta.RootCfgDefn;
import org.testng.annotations.Test;

@SuppressWarnings("javadoc")
public class StringPropertyDefinitionTest extends ConfigTestCase {

    @Test
    public void testValidateValueNoPattern() {
        StringPropertyDefinition d = getDefinition(true, null);
        d.validateValue("abc");
    }

    @Test
    public void testValidateValuePatternMatches() {
        StringPropertyDefinition d = getDefinition(true, "^[a-z]+$");
        d.validateValue("abc");
    }

    // TODO : I18N problem
    @Test(enabled = false, expectedExceptions = PropertyException.class)
    public void testValidateValuePatternDoesNotMatch() {
        StringPropertyDefinition d = getDefinition(true, "^[a-z]+$");
        d.validateValue("abc123");
    }

    @Test
    public void testDecodeValuePatternMatches() {
        StringPropertyDefinition d = getDefinition(true, "^[a-z]+$");
        assertEquals(d.decodeValue("abc"), "abc");
    }

    // TODO : I18N problem
    @Test(enabled = false, expectedExceptions = PropertyException.class)
    public void testDecodeValuePatternDoesNotMatch() {
        StringPropertyDefinition d = getDefinition(true, "^[a-z]+$");
        d.decodeValue("abc123");
    }

    /** Create a string property definition. */
    private StringPropertyDefinition getDefinition(boolean isCaseInsensitive, String pattern) {
        StringPropertyDefinition.Builder builder = StringPropertyDefinition.createBuilder(RootCfgDefn.getInstance(),
                "test-property");
        builder.setCaseInsensitive(isCaseInsensitive);
        builder.setPattern(pattern, "STRING");
        return builder.getInstance();
    }
}
