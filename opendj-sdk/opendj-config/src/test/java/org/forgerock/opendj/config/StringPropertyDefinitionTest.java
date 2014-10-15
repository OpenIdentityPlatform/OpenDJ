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
 *      Copyright 2008 Sun Microsystems, Inc.
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
