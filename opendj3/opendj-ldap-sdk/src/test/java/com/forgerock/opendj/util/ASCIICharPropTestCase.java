/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package com.forgerock.opendj.util;



import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.forgerock.opendj.util.ASCIICharProp;



/**
 * Tests various methods of ASCIICharProp class.
 */
public class ASCIICharPropTestCase extends UtilTestCase
{
  /**
   * Invalid Ascii char data provider.
   *
   * @return Returns an array of data.
   */
  @DataProvider(name = "invalidasciidata")
  public Object[][] createInValidASCIIData()
  {
    return new Object[][] {
    // {null,/* uppercase*/ false,/*hex value*/0,/*decimal value*/0,/* is
    // letter*/false,/*is digit*/false,/*is keychar*/false},
    { '\u200A' } };
  }



  /**
   * Valid Ascii char data provider.
   *
   * @return Returns an array of data.
   */
  @DataProvider(name = "validasciidata")
  public Object[][] createValidASCIIData()
  {
    return new Object[][] {
        { (char) 1,/* uppercase */false,/* hex value */0,/* decimal value */0,/*
                                                                               * is
                                                                               * letter
                                                                               */
        false,/* is digit */false,/* is keychar */false },
        { '-',/* uppercase */false,/* hex value */-1,/* decimal value */-1,/*
                                                                            * is
                                                                            * letter
                                                                            */
        false,/* is digit */false,/* is keychar */true },
        { 'a',/* uppercase */false,/* hex value */10,/* decimal value */-1,/*
                                                                            * is
                                                                            * letter
                                                                            */
        true,/* is digit */false,/* is keychar */true },
        { 'A',/* uppercase */true,/* hex value */10,/* decimal value */-1,/*
                                                                           * is
                                                                           * letter
                                                                           */
        true,/* is digit */false,/* is keychar */true },
        { '1',/* uppercase */false,/* hex value */1,/* decimal value */1,/*
                                                                          * is
                                                                          * letter
                                                                          */
        false,/* is digit */true,/* is keychar */true }, };
  }



  /**
   * Tests whether a character is an invalid ascii character or not.
   *
   * @param myChar
   *          The character that needs to be verified.
   * @throws Exception
   *           In case of any errors.
   */
  @Test(dataProvider = "invalidasciidata")
  public void testEncode(final char myChar) throws Exception
  {
    assertEquals(ASCIICharProp.valueOf(myChar), null);
  }



  /**
   * Tests whether a character is a valid ascii character or not.
   *
   * @param myChar
   *          The character that needs to be verified.
   * @param isUpper
   *          Whether it is uppercase
   * @param hexValue
   *          The hexadecimal value
   * @param decimalValue
   *          The decimal value
   * @param isLetter
   *          Whether the character is a letter
   * @param isDigit
   *          Whether the character is a digit
   * @param isKeyChar
   *          Whether the character is a key char.
   * @throws Exception
   *           In case of any errors.
   */
  @Test(dataProvider = "validasciidata")
  public void testEncode(final char myChar, final Boolean isUpper,
      final Integer hexValue, final Integer decimalValue,
      final Boolean isLetter, final Boolean isDigit, final Boolean isKeyChar)
      throws Exception
  {
    final ASCIICharProp myProp = ASCIICharProp.valueOf(myChar);

    // check letter.
    if (isLetter)
    {
      assertTrue(myProp.isLetter());
      // Check case.
      if (isUpper)
      {
        assertTrue(myProp.isUpperCase());
      }
      else
      {
        assertTrue(myProp.isLowerCase());
      }
    }

    // check digit.
    if (isDigit)
    {
      assertTrue(myProp.isDigit());
    }

    if (isLetter || isDigit)
    {
      // Check hex.
      final int hex = myProp.hexValue();
      final int hex1 = hexValue.intValue();
      assertEquals(myProp.hexValue(), hexValue.intValue());
      // Decimal value.
      assertEquals(myProp.decimalValue(), decimalValue.intValue());
      // Check if it is equal to itself.
      assertEquals(myProp.charValue(), myChar);
      assertEquals(myProp.compareTo(ASCIICharProp.valueOf(myChar)), 0);
    }

    // keychar.
    if (isKeyChar)
    {
      assertTrue(myProp.isKeyChar());
    }
  }
}
