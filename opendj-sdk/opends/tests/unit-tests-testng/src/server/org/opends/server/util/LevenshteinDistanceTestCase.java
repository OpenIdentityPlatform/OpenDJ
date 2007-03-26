/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.util;



import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;



/**
 * A set of generic test cases for the Levenshtein distance class.
 */
public class LevenshteinDistanceTestCase
       extends UtilTestCase
{
  /**
   * Retrieves a set of data that may be used to test the Levenshtein distance
   * implementation.  Each element of the array returned will itself be an
   * array whose elements are a source string, a target string, and the
   * expected Levenshtein distance.
   *
   * @return  A set of data that may be used to test the Levenshtein distance
   *          implementation.
   */
  @DataProvider(name = "teststrings")
  public Object[][] getTestStrings()
  {
    return new Object[][]
    {
      // When the values are the same, the distance is zero.
      new Object[] { "", "", 0 },
      new Object[] { "1", "1", 0 },
      new Object[] { "12", "12", 0 },
      new Object[] { "123", "123", 0 },
      new Object[] { "1234", "1234", 0 },
      new Object[] { "12345", "12345", 0 },
      new Object[] { "password", "password", 0 },


      // When one of the values is empty, the distance is the length of the
      // other value.
      new Object[] { "", "1", 1 },
      new Object[] { "", "12", 2 },
      new Object[] { "", "123", 3 },
      new Object[] { "", "1234", 4 },
      new Object[] { "", "12345", 5 },
      new Object[] { "", "password", 8 },
      new Object[] { "1", "", 1 },
      new Object[] { "12", "", 2 },
      new Object[] { "123", "", 3 },
      new Object[] { "1234", "", 4 },
      new Object[] { "12345", "", 5 },
      new Object[] { "password", "", 8 },


      // Whenever a single character is inserted or removed, the distance is
      // one.
      new Object[] { "password", "1password", 1 },
      new Object[] { "password", "p1assword", 1 },
      new Object[] { "password", "pa1ssword", 1 },
      new Object[] { "password", "pas1sword", 1 },
      new Object[] { "password", "pass1word", 1 },
      new Object[] { "password", "passw1ord", 1 },
      new Object[] { "password", "passwo1rd", 1 },
      new Object[] { "password", "passwor1d", 1 },
      new Object[] { "password", "password1", 1 },
      new Object[] { "password", "assword", 1 },
      new Object[] { "password", "pssword", 1 },
      new Object[] { "password", "pasword", 1 },
      new Object[] { "password", "pasword", 1 },
      new Object[] { "password", "passord", 1 },
      new Object[] { "password", "passwrd", 1 },
      new Object[] { "password", "passwod", 1 },
      new Object[] { "password", "passwor", 1 },


      // Whenever a single character is replaced, the distance is one.
      new Object[] { "password", "Xassword", 1 },
      new Object[] { "password", "pXssword", 1 },
      new Object[] { "password", "paXsword", 1 },
      new Object[] { "password", "pasXword", 1 },
      new Object[] { "password", "passXord", 1 },
      new Object[] { "password", "passwXrd", 1 },
      new Object[] { "password", "passwoXd", 1 },
      new Object[] { "password", "passworX", 1 },


      // If characters are taken off the front and added to the back and all of
      // the characters are unique, then the distance is two times the number of
      // characters shifted, until you get halfway (and then it becomes easier
      // to shift from the other direction).
      new Object[] { "12345678", "23456781", 2 },
      new Object[] { "12345678", "34567812", 4 },
      new Object[] { "12345678", "45678123", 6 },
      new Object[] { "12345678", "56781234", 8 },
      new Object[] { "12345678", "67812345", 6 },
      new Object[] { "12345678", "78123456", 4 },
      new Object[] { "12345678", "81234567", 2 },


      // If all the characters are unique and the values are reversed, then the
      // distance is the number of characters for an even number of characters,
      // and one less for an odd number of characters (since the middle
      // character will stay the same).
      new Object[] { "12", "21", 2 },
      new Object[] { "123", "321", 2 },
      new Object[] { "1234", "4321", 4 },
      new Object[] { "12345", "54321", 4 },
      new Object[] { "123456", "654321", 6 },
      new Object[] { "1234567", "7654321", 6 },
      new Object[] { "12345678", "87654321", 8 },


      // The rest of these are miscellaneous interesting examples.  They will
      // be illustrated using the following key:
      // = (the characters are equal)
      // + (the character is inserted)
      // - (the character is removed)
      // # (the character is replaced)

      // Mississippi
      //  ippississiM
      // -=##====##=+ --> 6
      new Object[] { "Mississippi", "ippississiM", 6 },

      // eieio
      // oieie
      // #===# --> 2
      new Object[] { "eieio", "oieie", 2 },

      // brad+angelina
      // bra   ngelina
      // ===+++======= --> 3
      new Object[] { "brad+angelina", "brangelina", 3 },

      // test international chars 
      // ?e?uli?ka
      //  e?uli?ka
      // -======== --> 1
      new Object[] { "?e?uli?ka", "e?uli?ka", 1 },
    };
  }



  /**
   * Tests the {@code calculate} method with non-{@code null} String arguments.
   *
   * @param  s  The source string to compare.
   * @param  t  The target string to compare.
   * @param  d  The expected Levenshtein distance for the two strings.
   */
  @Test(dataProvider = "teststrings")
  public void testCalculateStrings(String s, String t, int d)
  {
    assertEquals(LevenshteinDistance.calculate(s, t), d);
  }



  /**
   * Tests the {@code calculate} method with non-{@code null} String arguments
   * in reverse order to verify that they are order-independent.
   *
   * @param  s  The source string to compare.
   * @param  t  The target string to compare.
   * @param  d  The expected Levenshtein distance for the two strings.
   */
  @Test(dataProvider = "teststrings")
  public void testCalculateStringsReversed(String s, String t, int d)
  {
    assertEquals(LevenshteinDistance.calculate(t, s), d);
  }


  /**
   * Retrieves a set of data that may be used to test the Levenshtein distance
   * implementation.  Each element of the array returned will itself be an
   * array whose elements are a source string, a target string, and the
   * expected Levenshtein distance.
   *
   * @return  A set of data that may be used to test the Levenshtein distance
   *          implementation.
   */
  @DataProvider(name = "testnulls")
  public Object[][] getTestNulls()
  {
    return new Object[][]
    {
      new Object[] { "notnull", null },
      new Object[] { null, "notnull" },
      new Object[] { null, null }
    };
  }



  /**
   * Tests the {@code calculate} method with at least one {@code null} string.
   *
   * @param  s  The source string to compare.
   * @param  t  The target string to compare.
   */
  @Test(dataProvider = "testnulls",
        expectedExceptions = { AssertionError.class })
  public void testNullStrings(String s, String t)
  {
    LevenshteinDistance.calculate(s, t);
  }
}

