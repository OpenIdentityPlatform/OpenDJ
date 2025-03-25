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
 * Copyright 2013-2014 Manuel Gaupp
 * Portions Copyright 2014-2016 ForgeRock AS.
 */
package org.opends.server.protocols.asn1;

import static org.testng.Assert.*;

import org.opends.server.DirectoryServerTestCase;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** This class tests the GSERParser. */
public class GSERParserTestCase extends DirectoryServerTestCase
{
  /** Try to create a GSER Parser with <CODE>null</CODE> as parameter. */
  @Test(expectedExceptions = NullPointerException.class)
  public void testGSERParserInitWithNull () throws Exception
  {
    new GSERParser(null);
  }

  /** Test the <CODE>hasNext</CODE> method. */
  @Test
  public void testHasNext() throws Exception
  {
    GSERParser parser = new GSERParser("0");
    assertTrue(parser.hasNext());
    assertEquals(parser.nextInteger(),0);
    assertFalse(parser.hasNext());
  }

  /** Test the <CODE>skipSP</CODE> method. */
  @Test
  public void testSkipSP() throws Exception
  {
    String[] values = {" 42","  42","42"};
    for (String value : values)
    {
      GSERParser parser = new GSERParser(value);
      assertEquals(parser.skipSP().nextInteger(),42);
      assertFalse(parser.hasNext());
    }
  }

  /** Test the <CODE>skipMSP</CODE> method. */
  @Test
  public void testSkipMSP() throws Exception
  {
    String[] values = {" 42","  42","           42"};
    for (String value : values)
    {
      GSERParser parser = new GSERParser(value);
      assertEquals(parser.skipMSP().nextInteger(),42);
      assertFalse(parser.hasNext());
    }
  }

  /** Verify that <CODE>skipMSP</CODE> requires at least one space. */
  @Test(expectedExceptions = GSERException.class)
  public void testSkipMSPwithZeroSpaces() throws Exception
  {
    GSERParser parser = new GSERParser("42");
    parser.skipMSP();
  }

  /** Create data for the <CODE>testSequence</CODE> test case. */
  @DataProvider(name="sequenceValues")
  public Object[][] createSequenceValues()
  {
    return new Object[][] {
      {"{123,122}", true},
      {"{ 123,1}", true },
      {"{ 123   ,   1   }", true },
      {"{0123,}", false},
      {"{0123 42 }", false},
      {"{123  , 11 ", false},
      {" {123  , 11 ", false},
      {" 123  , 11}", false}
    };
  }

  /** Test sequence parsing. */
  @Test(dataProvider="sequenceValues")
  public void testSequence(String value, boolean expectedResult) throws Exception
  {
    GSERParser parser = new GSERParser(value);
    boolean result = true;
    try
    {
      parser.readStartSequence();
      parser.nextInteger();
      parser.skipSP().skipSeparator();
      parser.nextInteger();
      parser.readEndSequence();
      if (parser.hasNext())
      {
        result = false;
      }
    }
    catch (GSERException e)
    {
      result = false;
    }
    assertEquals(expectedResult,result);
  }

  /** Create data for the <CODE>testString</CODE> test case. */
  @DataProvider(name="stringValues")
  public Object[][] createStringValues()
  {
    return new Object[][] {
      {"\"\"", true},
      {"\"escaped\"\"dquotes\"", true },
      {"\"valid Unicode \u00D6\u00C4\"", true },
      {"\"only one \" \"", false},
      {"invalid without dquotes", false},
      {"\"missing end", false},
      {"\"valid string\" with extra trailing characters", false}
    };
  }

  /** Test the parsing of String values. */
  @Test(dataProvider="stringValues")
  public void testString(String value, boolean expectedResult) throws Exception
  {
    GSERParser parser = new GSERParser(value);
    boolean result = true;
    try
    {
      assertNotNull(parser.nextString());
      if (parser.hasNext())
      {
        result = false;
      }
    }
    catch (GSERException e)
    {
      result = false;
    }
    assertEquals(expectedResult,result);
  }

  /** Create data for the <CODE>testInteger</CODE> test case. */
  @DataProvider(name="integerValues")
  public Object[][] createIntegerValues()
  {
    return new Object[][] {
      {"0123456", true},
      {"42", true},
      {"0", true },
      {"", false},
      {"0xFF", false},
      {"NULL", false},
      {"Not a Number", false}
    };
  }

  /** Create data for the <CODE>testBigInteger</CODE> test case. */
  @DataProvider(name="bigIntegerValues")
  public Object[][] createBigIntegerValues()
  {
    return new Object[][] {
      {"0123456", true},
      {"42", true},
      {"0", true },
      {"", false},
      {"0xFF", false},
      {"NULL", false},
      {"Not a Number", false},
      {"2147483648",true}
    };
  }

  /** Test the parsing of Integer values. */
  @Test(dataProvider="integerValues")
  public void testInteger(String value, boolean expectedResult) throws Exception
  {
    GSERParser parser = new GSERParser(value);
    boolean result = true;
    try
    {
      parser.nextInteger();
      if (parser.hasNext())
      {
        result = false;
      }
    }
    catch (GSERException e)
    {
      result = false;
    }
    assertEquals(expectedResult,result);
  }

  /** Test the parsing of BigInteger values. */
  @Test(dataProvider="bigIntegerValues", singleThreaded = true)
  public void testBigInteger(String value, boolean expectedResult) throws Exception
  {
    GSERParser parser = new GSERParser(value);
    boolean result = true;
    try
    {
      parser.nextBigInteger();
      if (parser.hasNext())
      {
        result = false;
      }
    }
    catch (GSERException e)
    {
      result = false;
    }
    assertEquals(expectedResult,result);
  }

  /** Create data for the <CODE>testNamedValueIdentifier</CODE> test case. */
  @DataProvider(name="namedValueIdentifierValues")
  public Object[][] createNamedValueIdentifierValues()
  {
    return new Object[][] {
      {"serialNumber ", true},
      {"issuer ", true},
      {"Serialnumber ", false},
      {"0serialnumber ", false},
      {"serial Number ", false},
      {"missingSpace",false}
    };
  }

  /** Test the parsing of NamedValue identifiers. */
  @Test(dataProvider="namedValueIdentifierValues")
  public void testNamedValueIdentifier(String value, boolean expectedResult) throws Exception
  {
    GSERParser parser = new GSERParser(value);
    boolean result = true;
    try
    {
      assertNotNull(parser.nextNamedValueIdentifier());
      if (parser.hasNext())
      {
        result = false;
      }
    }
    catch (GSERException e)
    {
      result = false;
    }
    assertEquals(expectedResult,result);
  }

  /** Create data for the <CODE>testIdentifiedChoiceIdentifier</CODE> test case. */
  @DataProvider(name="identifiedChoicdeIdentifierValues")
  public Object[][] createIdentifiedChoicdeIdentifierValues()
  {
    return new Object[][] {
      {"serialNumber:", true},
      {"issuer1:", true},
      {"Serialnumber:", false},
      {"0serialnumber:", false},
      {"serial Number:", false},
      {"missingColon",false}
    };
  }

  /** Test the parsing of IdentifiedChoice identifiers. */
  @Test(dataProvider="identifiedChoicdeIdentifierValues")
  public void testIdentifiedChoicdeIdentifier(String value, boolean expectedResult) throws Exception
  {
    GSERParser parser = new GSERParser(value);
    boolean result = true;
    try
    {
      assertNotNull(parser.nextChoiceValueIdentifier());
      if (parser.hasNext())
      {
        result = false;
      }
    }
    catch (GSERException e)
    {
      result = false;
    }
    assertEquals(expectedResult,result);
  }
}
