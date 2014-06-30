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
 *      Copyright 2010 Sun Microsystems, Inc.
 *      Portions copyright 2011-2014 ForgeRock AS
 */

package com.forgerock.opendj.util;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests various methods of ASCIICharProp class.
 */
public class ASCIICharPropTestCase extends UtilTestCase {
    /**
     * Invalid Ascii char data provider.
     *
     * @return Returns an array of data.
     */
    @DataProvider(name = "invalidasciidata")
    public Object[][] createInValidASCIIData() {
        return new Object[][] { { '\u200A' } };
    }

    /**
     * Valid Ascii char data provider.
     *
     * @return Returns an array of data.
     */
    @DataProvider(name = "validasciidata")
    public Object[][] createValidASCIIData() {
        // @formatter:off
        return new Object[][] {
            {
                (char) 1,
                false, // uppercase
                -1,    // hex
                -1,    // decimal
                false, // is letter
                false, // is digit
                false, // is key char
                false  // is compat key char
            },
            {
                '-',
                false, // uppercase
                -1,    // hex
                -1,    // decimal
                false, // is letter
                false, // is digit
                true,  // is key char
                true   // is compat key char
            },
            {
                '_',
                false, // uppercase
                -1,    // hex
                -1,    // decimal
                false, // is letter
                false, // is digit
                false, // is key char
                true   // is compat key char
            },
            {
                '.',
                false, // uppercase
                -1,    // hex
                -1,    // decimal
                false, // is letter
                false, // is digit
                false, // is key char
                true   // is compat key char
            },
            {
                '+',
                false, // uppercase
                -1,    // hex
                -1,    // decimal
                false, // is letter
                false, // is digit
                false, // is key char
                false  // is compat key char
            },
            {
                '=',
                false, // uppercase
                -1,    // hex
                -1,    // decimal
                false, // is letter
                false, // is digit
                false, // is key char
                true  // is compat key char
            },
            {
                'a',
                false, // uppercase
                10,    // hex
                -1,    // decimal
                true,  // is letter
                false, // is digit
                true,  // is key char
                true   // is compat key char
            },
            {
                'A',
                true,  // uppercase
                10,    // hex
                -1,    // decimal
                true,  // is letter
                false, // is digit
                true,  // is key char
                true   // is compat key char
            },
            {
                'f',
                false, // uppercase
                15,    // hex
                -1,    // decimal
                true,  // is letter
                false, // is digit
                true,  // is key char
                true   // is compat key char
            },
            {
                'F',
                true,  // uppercase
                15,    // hex
                -1,    // decimal
                true,  // is letter
                false, // is digit
                true,  // is key char
                true   // is compat key char
            },
            {
                'z',
                false, // uppercase
                -1,    // hex
                -1,    // decimal
                true,  // is letter
                false, // is digit
                true,  // is key char
                true   // is compat key char
            },
            {
                'Z',
                true,  // uppercase
                -1,    // hex
                -1,    // decimal
                true,  // is letter
                false, // is digit
                true,  // is key char
                true   // is compat key char
            },
            {
                '0',
                false, // uppercase
                0,     // hex
                0,     // decimal
                false, // is letter
                true,  // is digit
                true,  // is key char
                true   // is compat key char
            },
            {
                '9',
                false, // uppercase
                9,     // hex
                9,     // decimal
                false, // is letter
                true,  // is digit
                true,  // is key char
                true   // is compat key char
            },
        };
        // @formatter:on
    }

    /**
     * Tests whether a character is an invalid ascii character or not.
     *
     * @param myChar
     *            The character that needs to be verified.
     * @throws Exception
     *             In case of any errors.
     */
    @Test(dataProvider = "invalidasciidata")
    public void testValueOf(final char myChar) throws Exception {
        assertEquals(ASCIICharProp.valueOf(myChar), null);
    }

    /**
     * Tests whether a character is a valid ascii character or not.
     *
     * @param myChar
     *            The character that needs to be verified.
     * @param isUpper
     *            Whether it is uppercase
     * @param hexValue
     *            The hexadecimal value
     * @param decimalValue
     *            The decimal value
     * @param isLetter
     *            Whether the character is a letter
     * @param isDigit
     *            Whether the character is a digit
     * @param isKeyChar
     *            Whether the character is a key char.
     * @param isCompatKeyChar
     *            Whether the character is a compat key char.
     * @throws Exception
     *             In case of any errors.
     */
    @Test(dataProvider = "validasciidata")
    public void testValueOf(final char myChar, final boolean isUpper, final int hexValue,
            final int decimalValue, final boolean isLetter, final boolean isDigit,
            final boolean isKeyChar, final boolean isCompatKeyChar) throws Exception {
        final ASCIICharProp myProp = ASCIICharProp.valueOf(myChar);

        // check letter.
        assertEquals(isLetter, myProp.isLetter());

        // Check case.
        assertEquals(isLetter & isUpper, myProp.isUpperCase());
        assertEquals(isLetter & !isUpper, myProp.isLowerCase());

        // check digit.
        assertEquals(isDigit, myProp.isDigit());

        // Check hex.
        assertEquals(myProp.hexValue(), hexValue);

        // Decimal value.
        assertEquals(myProp.decimalValue(), decimalValue);

        // Check if it is equal to itself.
        assertEquals(myProp.charValue(), myChar);
        assertEquals(myProp.compareTo(ASCIICharProp.valueOf(myChar)), 0);

        // keychar.
        assertEquals(isKeyChar, myProp.isKeyChar(false));
        assertEquals(isCompatKeyChar, myProp.isKeyChar(true));
    }
}
