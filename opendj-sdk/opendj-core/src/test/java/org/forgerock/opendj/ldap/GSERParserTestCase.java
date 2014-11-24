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
 *      Copyright 2013-2014 Manuel Gaupp
 */
package org.forgerock.opendj.ldap;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * This class tests the GSERParser.
 */
@SuppressWarnings("javadoc")
public class GSERParserTestCase extends SdkTestCase {

    /**
     * Try to create a GSER Parser with <CODE>null</CODE> as parameter.
     */
    @Test(expectedExceptions = { NullPointerException.class })
    public void testGSERParserInitWithNull() throws Exception {
        new GSERParser(null);
    }

    /**
     * Test the <CODE>hasNext</CODE> method.
     */
    @Test
    public void testHasNext() throws Exception {
        GSERParser parser = new GSERParser("0");
        assertTrue(parser.hasNext());
        assertEquals(parser.nextInteger(), 0);
        assertFalse(parser.hasNext());
    }

    /**
     * Test the <CODE>skipSP</CODE> method.
     */
    @Test
    public void testSkipSP() throws Exception {
        String[] values = {" 42", "  42", "42"};
        for (String value : values) {
            GSERParser parser = new GSERParser(value);
            assertEquals(parser.skipSP().nextInteger(), 42);
            assertFalse(parser.hasNext());
        }
    }

    /**
     * Test the <CODE>skipMSP</CODE> method.
     */
    @Test
    public void testSkipMSP() throws Exception {
        String[] values = {" 42", "  42", "           42"};
        for (String value : values) {
            GSERParser parser = new GSERParser(value);
            assertEquals(parser.skipMSP().nextInteger(), 42);
            assertFalse(parser.hasNext());
        }
    }

    /**
     * Verify that <CODE>skipMSP</CODE> requires at least one space.
     */
    @Test(expectedExceptions = { DecodeException.class })
    public void testSkipMSPwithZeroSpaces() throws Exception {
        GSERParser parser = new GSERParser("42");
        parser.skipMSP();
    }

    /**
     * Create data for the <CODE>testSequence</CODE> test case.
     */
    @DataProvider(name = "sequenceValues")
    public Object[][] createSequenceValues() {
        return new Object[][]{
            {"{123,122}", true},
            {"{ 123,1}", true},
            {"{ 123   ,   1   }", true},
            {"{0123,}", false},
            {"{0123 42 }", false},
            {"{123  , 11 ", false},
            {" {123  , 11 ", false},
            {" 123  , 11}", false}
        };
    }

    /**
     * Test sequence parsing.
     */
    @Test(dataProvider = "sequenceValues")
    public void testSequence(String value, boolean expectedResult) throws Exception {
        GSERParser parser = new GSERParser(value);
        boolean result = true;
        try {
            parser.readStartSequence();
            parser.nextInteger();
            parser.skipSP().skipSeparator();
            parser.nextInteger();
            parser.readEndSequence();
            if (parser.hasNext()) {
                result = false;
            }
        } catch (DecodeException e) {
            result = false;
        }
        assertEquals(expectedResult, result);
    }

    /**
     * Create data for the <CODE>testString</CODE> test case.
     */
    @DataProvider(name = "stringValues")
    public Object[][] createStringValues() {
        return new Object[][]{
            {"\"\"", true},
            {"\"escaped\"\"dquotes\"", true},
            {"\"valid Unicode \u00D6\u00C4\"", true},
            {"\"only one \" \"", false},
            {"invalid without dquotes", false},
            {"\"missing end", false},
            {"\"valid string\" with extra trailing characters", false}
        };
    }

    /**
     * Test the parsing of String values.
     */
    @Test(dataProvider = "stringValues")
    public void testString(String value, boolean expectedResult) throws Exception {
        GSERParser parser = new GSERParser(value);
        boolean result = true;
        try {
            assertNotNull(parser.nextString());
            if (parser.hasNext()) {
                result = false;
            }
        } catch (DecodeException e) {
            result = false;
        }
        assertEquals(expectedResult, result);
    }

    /**
     * Create data for the <CODE>testInteger</CODE> test case.
     */
    @DataProvider(name = "integerValues")
    public Object[][] createIntegerValues() {
        return new Object[][]{
            {"0123456", true},
            {"42", true},
            {"0", true},
            {"", false},
            {"0xFF", false},
            {"NULL", false},
            {"Not a Number", false}
        };
    }

    /**
     * Create data for the <CODE>testBigInteger</CODE> test case.
     */
    @DataProvider(name = "bigIntegerValues")
    public Object[][] createBigIntegerValues() {
        return new Object[][]{
            {"0123456", true},
            {"42", true},
            {"0", true},
            {"", false},
            {"0xFF", false},
            {"NULL", false},
            {"Not a Number", false},
            {"2147483648", true}
        };
    }

    /**
     * Test the parsing of Integer values.
     */
    @Test(dataProvider = "integerValues")
    public void testInteger(String value, boolean expectedResult) throws Exception {
        GSERParser parser = new GSERParser(value);
        boolean result = true;
        try {
            parser.nextInteger();
            if (parser.hasNext()) {
                result = false;
            }
        } catch (DecodeException e) {
            result = false;
        }
        assertEquals(expectedResult, result);
    }

    /**
     * Test the parsing of BigInteger values.
     */
    @Test(dataProvider = "bigIntegerValues")
    public void testBigInteger(String value, boolean expectedResult) throws Exception {
        GSERParser parser = new GSERParser(value);
        boolean result = true;
        try {
            parser.nextBigInteger();
            if (parser.hasNext()) {
                result = false;
            }
        } catch (DecodeException e) {
            result = false;
        }
        assertEquals(expectedResult, result);
    }

    /**
     * Create data for the <CODE>testNamedValueIdentifier</CODE> test case.
     */
    @DataProvider(name = "namedValueIdentifierValues")
    public Object[][] createNamedValueIdentifierValues() {
        return new Object[][]{
            {"serialNumber ", true},
            {"issuer ", true},
            {"Serialnumber ", false},
            {"0serialnumber ", false},
            {"serial Number ", false},
            {"missingSpace", false}
        };
    }

    /**
     * Test the parsing of NamedValue identifiers.
     */
    @Test(dataProvider = "namedValueIdentifierValues")
    public void testNamedValueIdentifier(String value, boolean expectedResult) throws Exception {
        GSERParser parser = new GSERParser(value);
        boolean result = true;
        try {
            assertNotNull(parser.nextNamedValueIdentifier());
            if (parser.hasNext()) {
                result = false;
            }
        } catch (DecodeException e) {
            result = false;
        }
        assertEquals(expectedResult, result);
    }

    /**
     * Create data for the <CODE>testIdentifiedChoiceIdentifier</CODE> test
     * case.
     */
    @DataProvider(name = "identifiedChoicdeIdentifierValues")
    public Object[][] createIdentifiedChoicdeIdentifierValues() {
        return new Object[][]{
            {"serialNumber:", true},
            {"issuer1:", true},
            {"Serialnumber:", false},
            {"0serialnumber:", false},
            {"serial Number:", false},
            {"missingColon", false}
        };
    }

    /**
     * Test the parsing of IdentifiedChoice identifiers.
     */
    @Test(dataProvider = "identifiedChoicdeIdentifierValues")
    public void testIdentifiedChoicdeIdentifier(String value, boolean expectedResult) throws Exception {
        GSERParser parser = new GSERParser(value);
        boolean result = true;
        try {
            assertNotNull(parser.nextChoiceValueIdentifier());
            if (parser.hasNext()) {
                result = false;
            }
        } catch (DecodeException e) {
            result = false;
        }
        assertEquals(expectedResult, result);
    }
}
